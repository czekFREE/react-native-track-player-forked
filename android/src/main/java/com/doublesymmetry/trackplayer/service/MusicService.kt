package com.doublesymmetry.trackplayer.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Binder
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaBrowserCompat.MediaItem
import android.support.v4.media.MediaDescriptionCompat
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.RatingCompat
import android.support.v4.media.session.MediaSessionCompat
import android.util.Log
import androidx.annotation.MainThread
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationCompat.PRIORITY_LOW
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.doublesymmetry.kotlinaudio.models.*
import com.doublesymmetry.kotlinaudio.models.NotificationButton.*
import com.doublesymmetry.kotlinaudio.players.QueuedAudioPlayer
import com.doublesymmetry.trackplayer.R as TrackPlayerR
import com.doublesymmetry.trackplayer.extensions.NumberExt.Companion.toMilliseconds
import com.doublesymmetry.trackplayer.extensions.NumberExt.Companion.toSeconds
import com.doublesymmetry.trackplayer.extensions.asLibState
import com.doublesymmetry.trackplayer.extensions.find
import com.doublesymmetry.trackplayer.model.Track
import com.doublesymmetry.trackplayer.model.TrackAudioItem
import com.doublesymmetry.trackplayer.module.MusicEvents
import com.doublesymmetry.trackplayer.module.MusicEvents.Companion.EVENT_INTENT
import com.doublesymmetry.trackplayer.utils.AppForegroundTracker
import com.doublesymmetry.trackplayer.utils.BundleUtils
import com.doublesymmetry.trackplayer.utils.BundleUtils.setRating
import com.facebook.react.HeadlessJsTaskService
import com.facebook.react.bridge.Arguments
import com.facebook.react.jstasks.HeadlessJsTaskConfig
import com.google.android.exoplayer2.ui.R as ExoPlayerR
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.flow
import java.util.concurrent.TimeUnit
import kotlin.system.exitProcess
import timber.log.Timber


import android.app.ActivityManager
import android.os.ResultReceiver
import android.support.v4.media.session.PlaybackStateCompat
import com.google.android.exoplayer2.C
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.ext.mediasession.MediaSessionConnector

class MusicService : HeadlessJsMediaBrowserTaskService() {
    private lateinit var player: QueuedAudioPlayer
    private val binder = MusicBinder()
    private val scope = MainScope()
    private var progressUpdateJob: Job? = null

    private var mediaSession: MediaSessionCompat? = null

    /**
     * Use [appKilledPlaybackBehavior] instead.
     */
    @Deprecated("This will be removed soon")
    var stoppingAppPausesPlayback = true
        private set

    enum class AppKilledPlaybackBehavior(val string: String) {
        CONTINUE_PLAYBACK("continue-playback"), PAUSE_PLAYBACK("pause-playback"), STOP_PLAYBACK_AND_REMOVE_NOTIFICATION(
            "stop-playback-and-remove-notification"
        )
    }

    private var appKilledPlaybackBehavior = AppKilledPlaybackBehavior.CONTINUE_PLAYBACK

    val tracks: List<Track>
        get() = player.items.map { (it as TrackAudioItem).track }

    val currentTrack
        get() = (player.currentItem as TrackAudioItem).track

    val state
        get() = player.playerState

    var ratingType: Int
        get() = player.ratingType
        set(value) {
            player.ratingType = value
        }

    val playbackError
        get() = player.playbackError

    val event
        get() = player.event

    var playWhenReady: Boolean
        get() = player.playWhenReady
        set(value) {
            player.playWhenReady = value
        }

    private var latestOptions: Bundle? = null
    private var capabilities: List<Capability> = emptyList()
    private var notificationCapabilities: List<Capability> = emptyList()
    private var compactCapabilities: List<Capability> = emptyList()

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(
            "MusicService",
            "MusicService.onStartCommand() " + intent + " , " + flags + " , " + startId
        );

        startTask(getTaskConfig(intent))
        startAndStopEmptyNotificationToAvoidANR()
        return START_STICKY
    }

    /**
     * Workaround for the "Context.startForegroundService() did not then call Service.startForeground()"
     * within 5s" ANR and crash by creating an empty notification and stopping it right after. For more
     * information see https://github.com/doublesymmetry/react-native-track-player/issues/1666
     */
    private fun startAndStopEmptyNotificationToAvoidANR() {
        Log.d("MusicService", "MusicService.startAndStopEmptyNotificationToAvoidANR()");

        val notificationManager =
            this.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        var name = ""
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            name = "temporary_channel"
            notificationManager.createNotificationChannel(
                NotificationChannel(name, name, NotificationManager.IMPORTANCE_LOW)
            )
        }

        val notificationBuilder = NotificationCompat.Builder(this, name)
            .setPriority(PRIORITY_LOW)
            .setCategory(Notification.CATEGORY_SERVICE)
            .setSmallIcon(ExoPlayerR.drawable.exo_notification_small_icon)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            notificationBuilder.setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
        }
        val notification = notificationBuilder.build()
        startForeground(EMPTY_NOTIFICATION_ID, notification)
        @Suppress("DEPRECATION")
        stopForeground(true)
    }

    @MainThread
    fun setupPlayer(playerOptions: Bundle?) {
        Log.d("MusicService", "MusicService.setupPlayer()");

        if (this::player.isInitialized) {
            print("Player was initialized. Prevent re-initializing again")
            return
        }

        val bufferConfig = BufferConfig(
            playerOptions?.getDouble(MIN_BUFFER_KEY)?.toMilliseconds()?.toInt(),
            playerOptions?.getDouble(MAX_BUFFER_KEY)?.toMilliseconds()?.toInt(),
            playerOptions?.getDouble(PLAY_BUFFER_KEY)?.toMilliseconds()?.toInt(),
            playerOptions?.getDouble(BACK_BUFFER_KEY)?.toMilliseconds()?.toInt(),
        )

        val cacheConfig = CacheConfig(playerOptions?.getDouble(MAX_CACHE_SIZE_KEY)?.toLong())
        val playerConfig = PlayerConfig(
            interceptPlayerActionsTriggeredExternally = true,
            handleAudioBecomingNoisy = true,
            handleAudioFocus = playerOptions?.getBoolean(AUTO_HANDLE_INTERRUPTIONS) ?: false,
            audioContentType = when (playerOptions?.getString(ANDROID_AUDIO_CONTENT_TYPE)) {
                "music" -> AudioContentType.MUSIC
                "speech" -> AudioContentType.SPEECH
                "sonification" -> AudioContentType.SONIFICATION
                "movie" -> AudioContentType.MOVIE
                "unknown" -> AudioContentType.UNKNOWN
                else -> AudioContentType.MUSIC
            }
        )

        val automaticallyUpdateNotificationMetadata =
            playerOptions?.getBoolean(AUTO_UPDATE_METADATA, true) ?: true

        if (mediaSession == null) {
            mediaSession = MediaSessionCompat(this, "KotlinAudioPlayer")
        }


        player = QueuedAudioPlayer(this@MusicService, playerConfig, bufferConfig, cacheConfig)
        player.automaticallyUpdateNotificationMetadata = automaticallyUpdateNotificationMetadata
        observeEvents()
        setupForegrounding()

        Log.d(
            "MusicService",
            "MusicService.seupPlayer() - sessionToken " + mediaSession?.sessionToken
        );

    }

    @MainThread
    fun updateOptions(options: Bundle) {
        latestOptions = options
        val androidOptions = options.getBundle(ANDROID_OPTIONS_KEY)

        Log.d("MusicService", "MusicService.updateOptions() " + options);


        appKilledPlaybackBehavior = AppKilledPlaybackBehavior::string.find(
            androidOptions?.getString(APP_KILLED_PLAYBACK_BEHAVIOR_KEY)
        ) ?: AppKilledPlaybackBehavior.CONTINUE_PLAYBACK

        //TODO: This handles a deprecated flag. Should be removed soon.
        options.getBoolean(STOPPING_APP_PAUSES_PLAYBACK_KEY).let {
            stoppingAppPausesPlayback = options.getBoolean(STOPPING_APP_PAUSES_PLAYBACK_KEY)
            if (stoppingAppPausesPlayback) {
                appKilledPlaybackBehavior = AppKilledPlaybackBehavior.PAUSE_PLAYBACK
            }
        }

        ratingType = BundleUtils.getInt(options, "ratingType", RatingCompat.RATING_NONE)

        player.playerOptions.alwaysPauseOnInterruption =
            androidOptions?.getBoolean(PAUSE_ON_INTERRUPTION_KEY) ?: false

        capabilities = options.getIntegerArrayList("capabilities")?.map { Capability.values()[it] }
            ?: emptyList()
        notificationCapabilities =
            options.getIntegerArrayList("notificationCapabilities")?.map { Capability.values()[it] }
                ?: emptyList()
        compactCapabilities =
            options.getIntegerArrayList("compactCapabilities")?.map { Capability.values()[it] }
                ?: emptyList()

        if (notificationCapabilities.isEmpty()) notificationCapabilities = capabilities

        val buttonsList = notificationCapabilities.mapNotNull {
            when (it) {
                Capability.PLAY, Capability.PAUSE -> {
                    val playIcon = BundleUtils.getIconOrNull(this, options, "playIcon")
                    val pauseIcon = BundleUtils.getIconOrNull(this, options, "pauseIcon")
                    PLAY_PAUSE(playIcon = playIcon, pauseIcon = pauseIcon)
                }

                Capability.STOP -> {
                    val stopIcon = BundleUtils.getIconOrNull(this, options, "stopIcon")
                    STOP(icon = stopIcon)
                }

                Capability.SKIP_TO_NEXT -> {
                    val nextIcon = BundleUtils.getIconOrNull(this, options, "nextIcon")
                    NEXT(icon = nextIcon, isCompact = isCompact(it))
                }

                Capability.SKIP_TO_PREVIOUS -> {
                    val previousIcon = BundleUtils.getIconOrNull(this, options, "previousIcon")
                    PREVIOUS(icon = previousIcon, isCompact = isCompact(it))
                }

                Capability.JUMP_FORWARD -> {
                    val forwardIcon = BundleUtils.getIcon(
                        this,
                        options,
                        "forwardIcon",
                        TrackPlayerR.drawable.forward
                    )
                    FORWARD(icon = forwardIcon, isCompact = isCompact(it))
                }

                Capability.JUMP_BACKWARD -> {
                    val backwardIcon = BundleUtils.getIcon(
                        this,
                        options,
                        "rewindIcon",
                        TrackPlayerR.drawable.rewind
                    )
                    BACKWARD(icon = backwardIcon, isCompact = isCompact(it))
                }

                Capability.SEEK_TO -> {
                    SEEK_TO
                }

                else -> {
                    null
                }
            }
        }

        val openAppIntent = packageManager.getLaunchIntentForPackage(packageName)?.apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            // Add the Uri data so apps can identify that it was a notification click
            data = Uri.parse("trackplayer://notification.click")
            action = Intent.ACTION_VIEW
        }

        val accentColor = BundleUtils.getIntOrNull(options, "color")
        val smallIcon = BundleUtils.getIconOrNull(this, options, "icon")
        val pendingIntent =
            PendingIntent.getActivity(this, 0, openAppIntent, getPendingIntentFlags())
        val notificationConfig =
            NotificationConfig(buttonsList, accentColor, smallIcon, pendingIntent)

        player.notificationManager.createNotification(notificationConfig)

        // setup progress update events if configured
        progressUpdateJob?.cancel()
        val updateInterval = BundleUtils.getIntOrNull(options, PROGRESS_UPDATE_EVENT_INTERVAL_KEY)
        if (updateInterval != null && updateInterval > 0) {
            progressUpdateJob = scope.launch {
                progressUpdateEventFlow(updateInterval.toLong()).collect {
                    emit(
                        MusicEvents.PLAYBACK_PROGRESS_UPDATED,
                        it
                    )
                }
            }
        }
    }

    @MainThread
    private fun progressUpdateEventFlow(interval: Long) = flow {
        while (true) {
            if (player.isPlaying) {
                val bundle = progressUpdateEvent()
                emit(bundle)
            }

            delay(interval * 1000)
        }
    }

    @MainThread
    private suspend fun progressUpdateEvent(): Bundle {
        return withContext(Dispatchers.Main) {
            Bundle().apply {
                putDouble(POSITION_KEY, player.position.toSeconds())
                putDouble(DURATION_KEY, player.duration.toSeconds())
                putDouble(BUFFERED_POSITION_KEY, player.bufferedPosition.toSeconds())
                putInt(TRACK_KEY, player.currentIndex)
            }
        }
    }

    private fun getPendingIntentFlags(): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_CANCEL_CURRENT
        } else {
            PendingIntent.FLAG_CANCEL_CURRENT
        }
    }

    private fun isCompact(capability: Capability): Boolean {
        return compactCapabilities.contains(capability)
    }

    @MainThread
    fun add(track: Track) {
        add(listOf(track))
    }

    @MainThread
    fun add(tracks: List<Track>) {
        val items = tracks.map { it.toAudioItem() }
        player.add(items)
    }

    @MainThread
    fun add(tracks: List<Track>, atIndex: Int) {
        val items = tracks.map { it.toAudioItem() }
        player.add(items, atIndex)
    }

    @MainThread
    fun load(track: Track) {
        player.load(track.toAudioItem())
    }

    @MainThread
    fun move(fromIndex: Int, toIndex: Int) {
        player.move(fromIndex, toIndex);
    }

    @MainThread
    fun remove(index: Int) {
        remove(listOf(index))
    }

    @MainThread
    fun remove(indexes: List<Int>) {
        player.remove(indexes)
    }

    @MainThread
    fun clear() {
        player.clear()
    }

    @MainThread
    fun play() {
        player.play()
    }

    @MainThread
    fun pause() {
        player.pause()
    }

    @MainThread
    fun stop() {
        player.stop()
    }

    @MainThread
    fun removeUpcomingTracks() {
        player.removeUpcomingItems()
    }

    @MainThread
    fun removePreviousTracks() {
        player.removePreviousItems()
    }

    @MainThread
    fun skip(index: Int) {
        player.jumpToItem(index)
    }

    @MainThread
    fun skipToNext() {
        player.next()
    }

    @MainThread
    fun skipToPrevious() {
        player.previous()
    }

    @MainThread
    fun seekTo(seconds: Float) {
        player.seek((seconds * 1000).toLong(), TimeUnit.MILLISECONDS)
    }

    @MainThread
    fun seekBy(offset: Float) {
        player.seekBy((offset.toLong()), TimeUnit.SECONDS)
    }

    @MainThread
    fun retry() {
        player.prepare()
    }

    @MainThread
    fun getCurrentTrackIndex(): Int = player.currentIndex

    @MainThread
    fun getRate(): Float = player.playbackSpeed

    @MainThread
    fun setRate(value: Float) {
        player.playbackSpeed = value
    }

    @MainThread
    fun getRepeatMode(): RepeatMode = player.playerOptions.repeatMode

    @MainThread
    fun setRepeatMode(value: RepeatMode) {
        player.playerOptions.repeatMode = value
    }

    @MainThread
    fun getVolume(): Float = player.volume

    @MainThread
    fun setVolume(value: Float) {
        player.volume = value
    }

    @MainThread
    fun getDurationInSeconds(): Double = player.duration.toSeconds()

    @MainThread
    fun getPositionInSeconds(): Double = player.position.toSeconds()

    @MainThread
    fun getBufferedPositionInSeconds(): Double = player.bufferedPosition.toSeconds()

    @MainThread
    fun getPlayerStateBundle(state: AudioPlayerState): Bundle {
        val bundle = Bundle()
        bundle.putString(STATE_KEY, state.asLibState.state)
        if (state == AudioPlayerState.ERROR) {
            bundle.putBundle(ERROR_KEY, getPlaybackErrorBundle())
        }
        return bundle
    }

    @MainThread
    fun updateMetadataForTrack(index: Int, track: Track) {
        player.replaceItem(index, track.toAudioItem())
    }

    @MainThread
    fun updateNotificationMetadata(title: String?, artist: String?, artwork: String?) {
        player.notificationManager.notificationMetadata =
            NotificationMetadata(title, artist, artwork)
    }

    @MainThread
    fun clearNotificationMetadata() {
        player.notificationManager.hideNotification()
    }

    private fun emitPlaybackTrackChangedEvents(
        index: Int?,
        previousIndex: Int?,
        oldPosition: Double
    ) {
        var a = Bundle()
        a.putDouble(POSITION_KEY, oldPosition)
        if (index != null) {
            a.putInt(NEXT_TRACK_KEY, index)
        }

        if (previousIndex != null) {
            a.putInt(TRACK_KEY, previousIndex)
        }

        emit(MusicEvents.PLAYBACK_TRACK_CHANGED, a)

        var b = Bundle()
        b.putDouble("lastPosition", oldPosition)
        if (tracks.size > 0) {
            b.putInt("index", player.currentIndex)
            b.putBundle("track", tracks[player.currentIndex].originalItem)
            if (previousIndex != null) {
                b.putInt("lastIndex", previousIndex)
                b.putBundle("lastTrack", tracks[previousIndex].originalItem)
            }
        }
        emit(MusicEvents.PLAYBACK_ACTIVE_TRACK_CHANGED, b)
    }

    private fun emitQueueEndedEvent() {
        val bundle = Bundle()
        bundle.putInt(TRACK_KEY, player.currentIndex)
        bundle.putDouble(POSITION_KEY, player.position.toSeconds())
        emit(MusicEvents.PLAYBACK_QUEUE_ENDED, bundle)
    }

    @Suppress("DEPRECATION")
    fun isForegroundService(): Boolean {
        val manager = baseContext.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        for (service in manager.getRunningServices(Int.MAX_VALUE)) {
            if (MusicService::class.java.name == service.service.className) {
                return service.foreground
            }
        }
        Timber.e("isForegroundService found no matching service")
        return false
    }

    @MainThread
    private fun setupForegrounding() {
        // Implementation based on https://github.com/Automattic/pocket-casts-android/blob/ee8da0c095560ef64a82d3a31464491b8d713104/modules/services/repositories/src/main/java/au/com/shiftyjelly/pocketcasts/repositories/playback/PlaybackService.kt#L218
        var notificationId: Int? = null
        var notification: Notification? = null
        var stopForegroundWhenNotOngoing = false
        var removeNotificationWhenNotOngoing = false

        fun startForegroundIfNecessary() {
            if (isForegroundService()) {
                Timber.d("skipping foregrounding as the service is already foregrounded")
                return
            }
            if (notification == null) {
                Timber.d("can't startForeground as the notification is null")
                return
            }
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    startForeground(
                        notificationId!!,
                        notification!!,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
                    )
                } else {
                    startForeground(notificationId!!, notification)
                }
                Timber.d("notification has been foregrounded")
            } catch (error: Exception) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                    error is ForegroundServiceStartNotAllowedException
                ) {
                    Timber.e(
                        "ForegroundServiceStartNotAllowedException: App tried to start a foreground Service when it was not allowed to do so.",
                        error
                    )
                    emit(MusicEvents.PLAYER_ERROR, Bundle().apply {
                        putString("message", error.message)
                        putString("code", "android-foreground-service-start-not-allowed")
                    });
                }
            }
        }

        scope.launch {
            val BACKGROUNDABLE_STATES = listOf(
                AudioPlayerState.IDLE,
                AudioPlayerState.ENDED,
                AudioPlayerState.STOPPED,
                AudioPlayerState.ERROR,
                AudioPlayerState.PAUSED
            )
            val REMOVABLE_STATES = listOf(
                AudioPlayerState.IDLE,
                AudioPlayerState.STOPPED,
                AudioPlayerState.ERROR
            )
            val LOADING_STATES = listOf(
                AudioPlayerState.LOADING,
                AudioPlayerState.READY,
                AudioPlayerState.BUFFERING
            )
            var stateCount = 0
            event.stateChange.collect {
                stateCount++
                if (it in LOADING_STATES) return@collect;
                // Skip initial idle state, since we are only interested when
                // state becomes idle after not being idle
                stopForegroundWhenNotOngoing = stateCount > 1 && it in BACKGROUNDABLE_STATES
                removeNotificationWhenNotOngoing =
                    stopForegroundWhenNotOngoing && it in REMOVABLE_STATES
            }
        }

        scope.launch {
            event.notificationStateChange.collect {
                Log.d("MusicService", "MusicService.observeEvents - notificationStateChange");

                when (it) {
                    is NotificationState.POSTED -> {
                        Timber.d(
                            "notification posted with id=%s, ongoing=%s",
                            it.notificationId,
                            it.ongoing
                        )
                        notificationId = it.notificationId;
                        notification = it.notification;
                        if (it.ongoing) {
                            if (player.playWhenReady) {
                                startForegroundIfNecessary()
                            }
                        } else if (stopForegroundWhenNotOngoing) {
                            if (removeNotificationWhenNotOngoing || isForegroundService()) {
                                @Suppress("DEPRECATION")
                                stopForeground(removeNotificationWhenNotOngoing)
                                Timber.d(
                                    "stopped foregrounding%s",
                                    if (removeNotificationWhenNotOngoing) " and removed notification" else ""
                                )
                            }
                        }
                    }

                    else -> {}
                }
            }
        }
    }

    @MainThread
    private fun observeEvents() {
        scope.launch {
            event.stateChange.collect {
                emit(MusicEvents.PLAYBACK_STATE, getPlayerStateBundle(it))

                if (it == AudioPlayerState.ENDED && player.nextItem == null) {
                    emitQueueEndedEvent()
                    emitPlaybackTrackChangedEvents(
                        null,
                        player.currentIndex,
                        player.position.toSeconds()
                    )
                }
            }
        }

        scope.launch {
            event.audioItemTransition.collect {
                var lastIndex: Int? = null
                if (it is AudioItemTransitionReason.REPEAT) {
                    lastIndex = player.currentIndex
                } else if (player.previousItem != null) {
                    lastIndex = player.previousIndex
                }
                var lastPosition = (it?.oldPosition ?: 0).toSeconds();
                emitPlaybackTrackChangedEvents(player.currentIndex, lastIndex, lastPosition)
            }
        }

        scope.launch {
            event.onAudioFocusChanged.collect {
                Bundle().apply {
                    putBoolean(IS_FOCUS_LOSS_PERMANENT_KEY, it.isFocusLostPermanently)
                    putBoolean(IS_PAUSED_KEY, it.isPaused)
                    emit(MusicEvents.BUTTON_DUCK, this)
                }
            }
        }

        scope.launch {
            event.onPlayerActionTriggeredExternally.collect {
                when (it) {
                    is MediaSessionCallback.RATING -> {
                        Bundle().apply {
                            setRating(this, "rating", it.rating)
                            emit(MusicEvents.BUTTON_SET_RATING, this)
                        }
                    }

                    is MediaSessionCallback.SEEK -> {
                        Bundle().apply {
                            putDouble("position", it.positionMs.toSeconds())
                            emit(MusicEvents.BUTTON_SEEK_TO, this)
                        }
                    }

                    MediaSessionCallback.PLAY -> emit(MusicEvents.BUTTON_PLAY)
                    MediaSessionCallback.PAUSE -> emit(MusicEvents.BUTTON_PAUSE)
                    MediaSessionCallback.NEXT -> emit(MusicEvents.BUTTON_SKIP_NEXT)
                    MediaSessionCallback.PREVIOUS -> emit(MusicEvents.BUTTON_SKIP_PREVIOUS)
                    MediaSessionCallback.STOP -> emit(MusicEvents.BUTTON_STOP)
                    MediaSessionCallback.FORWARD -> {
                        Bundle().apply {
                            val interval = latestOptions?.getDouble(
                                FORWARD_JUMP_INTERVAL_KEY,
                                DEFAULT_JUMP_INTERVAL
                            ) ?: DEFAULT_JUMP_INTERVAL
                            putInt("interval", interval.toInt())
                            emit(MusicEvents.BUTTON_JUMP_FORWARD, this)
                        }
                    }

                    MediaSessionCallback.REWIND -> {
                        Bundle().apply {
                            val interval = latestOptions?.getDouble(
                                BACKWARD_JUMP_INTERVAL_KEY,
                                DEFAULT_JUMP_INTERVAL
                            ) ?: DEFAULT_JUMP_INTERVAL
                            putInt("interval", interval.toInt())
                            emit(MusicEvents.BUTTON_JUMP_BACKWARD, this)
                        }
                    }
                }
            }
        }

        scope.launch {
            event.onPlaybackMetadata.collect {
                Bundle().apply {
                    putString("source", it.source)
                    putString("title", it.title)
                    putString("url", it.url)
                    putString("artist", it.artist)
                    putString("album", it.album)
                    putString("date", it.date)
                    putString("genre", it.genre)
                    emit(MusicEvents.PLAYBACK_METADATA, this)
                }
            }
        }

        scope.launch {
            event.playWhenReadyChange.collect {
                Bundle().apply {
                    putBoolean("playWhenReady", it.playWhenReady)
                    emit(MusicEvents.PLAYBACK_PLAY_WHEN_READY_CHANGED, this)
                }
            }
        }

        scope.launch {
            event.playbackError.collect {
                emit(MusicEvents.PLAYBACK_ERROR, getPlaybackErrorBundle())
            }
        }
    }

    private fun getPlaybackErrorBundle(): Bundle {
        var bundle = Bundle()
        var error = playbackError
        if (error?.message != null) {
            bundle.putString("message", error.message)
        }
        if (error?.code != null) {
            bundle.putString("code", "android-" + error.code)
        }
        return bundle
    }

    @MainThread
    private fun emit(event: String?, data: Bundle? = null) {
        Log.d(
            "MusicService",
            "MusicService.emit() " + event + " , " + LocalBroadcastManager.getInstance(this)
        );

        val intent = Intent(EVENT_INTENT)
        intent.putExtra(EVENT_KEY, event)
        if (data != null) intent.putExtra(DATA_KEY, data)
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
    }

    override fun getTaskConfig(intent: Intent?): HeadlessJsTaskConfig {
        return HeadlessJsTaskConfig(TASK_KEY, Arguments.createMap(), 0, true)
    }

    @MainThread
    override fun onBind(intent: Intent?): IBinder {
        val superOnBindResult = super.onBind(intent);
        Log.d("MusicService", "MusicService.onBind() " + intent);

        if (superOnBindResult != null) {
            return superOnBindResult
        }

        return binder
    }

    @MainThread
    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        Log.d("MusicService", "MusicService.onTaskRemoved() " + rootIntent);

        if (!::player.isInitialized) return

        setErrorState("Aplikace na mobilním telefonu neběží")

        notifyChildrenChanged("/")

        when (appKilledPlaybackBehavior) {
            AppKilledPlaybackBehavior.PAUSE_PLAYBACK -> player.pause()
            AppKilledPlaybackBehavior.STOP_PLAYBACK_AND_REMOVE_NOTIFICATION -> {
                player.clear()
                player.stop()

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                } else {
                    @Suppress("DEPRECATION")
                    stopForeground(true)
                }

                Log.d("MusicService", "MusicService.onTaskRemoved - player stoped and stopingSelf");

                stopSelf()
                exitProcess(0)
            }

            else -> {}
        }
    }

    override fun onRebind(intent: Intent?) {
        super.onRebind(intent)
        Log.d("MusicService", "MusicService.onRebind() " + intent);

    }

    override fun onUnbind(intent: Intent?): Boolean {
        Log.d("MusicService", "MusicService.onUnbind() " + intent);

        // Return false to make the service not rebindable
        return false
    }

    private fun ensureMediaSessionIsInitialized() {
        if (mediaSession == null) {
            mediaSession = MediaSessionCompat(this, "KotlinAudioPlayer")
        }
    }


    override fun onCreate() {
        super.onCreate()
        Log.d("MusicService", "MusicService.onCreate() ");

        if (mediaSession == null) {
            mediaSession = MediaSessionCompat(this, "KotlinAudioPlayer")
        }

        //        Log.d("MusicService", "MusicService.onCreate() " + isMainActivityRunning());


        //        if (!isMainActivityRunning()) {
        //            val openAppIntent = packageManager.getLaunchIntentForPackage(packageName)?.apply {
        //                Log.d("MusicService", "MusicService.onCreate() - openAppIntent " + this)
        //
        //                flags = Intent.FLAG_ACTIVITY_NEW_TASK
        //                action = Intent.ACTION_VIEW
        //            }
        //
        //            startActivity(openAppIntent)
        //        }

        //        setupPlayer(null)

        //        mediaSession = MediaSessionCompat(this, "KotlinAudioPlayer")

        sessionToken = mediaSession?.sessionToken


        //        mediaSession.setCallback(new MySessionCallback());
    }

    //    override fun on

    private fun setErrorState(errorMessage: String) {
        val playbackState = PlaybackStateCompat.Builder()
            .setState(
                PlaybackStateCompat.STATE_ERROR,
                0,
                0f
            )
            .setErrorMessage(
                PlaybackStateCompat.ERROR_CODE_AUTHENTICATION_EXPIRED,
                errorMessage
            )
            .build()
        mediaSession?.setPlaybackState(playbackState)
    }


    private fun isMainActivityRunning(): Boolean {
        val packageName = packageName

        val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            val appTasks = activityManager.appTasks
            for (appTask in appTasks) {
                val taskInfo = appTask.taskInfo
                val componentName = taskInfo.baseActivity
                if (componentName != null && componentName.packageName == packageName) {
                    return true
                }
            }
        } else {
            @Suppress("DEPRECATION")
            val runningTasks = activityManager.getRunningTasks(1)
            if (runningTasks.isNotEmpty()) {
                val taskInfo = runningTasks[0].topActivity
                if (taskInfo != null && taskInfo.packageName == packageName) {
                    return true
                }
            }
        }

        return false
    }


    override fun onGetRoot(
        clientPackageName: String,
        clientUid: Int,
        rootHints: Bundle?
    ): BrowserRoot? {
        Log.d(
            "MusicService",
            "MusicService.onGetRoot() " + clientPackageName + " , " + clientUid + " , " + getApplicationContext().getPackageName() + " , " + getCurrentBrowserInfo()
        );

        //        if (clientPackageName === "android.media.browse.MediaBrowserService") {
        //            setupPlayer(null)
        //            mediaSession = player.mediaSession
        //            sessionToken = mediaSession?.sessionToken
        //        }

        if (clientPackageName == getApplicationContext().getPackageName()) {
            return null
        }

        return BrowserRoot("/", null)
    }

    override fun onLoadChildren(
        parentId: String,
        result: Result<MutableList<MediaItem>>
    ) {
        Log.d(
            "MusicService",
            "MusicService.onLoadChildren() " + parentId + " , " + getBrowserRootHints()
        );

        setErrorState("App on mobile phone was closed")

        emit("LoadChildren", Bundle().apply { putString("parentId", parentId) })

        // https://developer.android.com/reference/android/media/session/MediaSession#setSessionActivity(android.app.PendingIntent)

        //        val openAppIntent = packageManager.getLaunchIntentForPackage(packageName)?.apply {
        //            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        //            // Add the Uri data so apps can identify that it was a notification click
        //            data = Uri.parse("trackplayer://notification.click")
        //            action = Intent.ACTION_VIEW
        //        }
        //        val pendingIntent = PendingIntent.getActivity(this, 0, openAppIntent, getPendingIntentFlags())

        //        if (parentId == "/" && ) {
        //            val openAppIntent = packageManager.getLaunchIntentForPackage(packageName)?.apply {
        //                Log.d("MusicService", "MusicService.onLoadChildren() - openAppIntent " + this)
        //
        //                flags = Intent.FLAG_ACTIVITY_NEW_TASK
        //                action = Intent.ACTION_VIEW
        //            }
        //
        //            startActivity(openAppIntent)
        //
        //        }

        if (!isMainActivityRunning()) {

            if (parentId == "/") {
                val rootList = mutableListOf<MediaItem>()

                val podcastsDescription = MediaDescriptionCompat.Builder().apply {
                    setMediaId("aplikace-na-mobilnim-telefonu-nebezi")
                    setTitle("Aplikace na mobilním telefonu neběží")
                    setDescription("Je třeba spustil aplikaci na mobilním telefonu aby Android auto fungovalo správně")
                    setSubtitle("Je třeba spustil aplikaci na mobilním telefonu aby Android auto fungovalo správně")
                    setExtras(Bundle().apply {
                        putString(
                            MediaMetadataCompat.METADATA_KEY_MEDIA_ID,
                            "aplikace-na-mobilnim-telefonu-nebezi"
                        )
                        putString(
                            MediaMetadataCompat.METADATA_KEY_TITLE,
                            "Aplikace na mobilním telefonu neběží"
                        )
                        putString(
                            MediaMetadataCompat.METADATA_KEY_DISPLAY_DESCRIPTION,
                            "Je třeba spustil aplikaci na mobilním telefonu aby Android auto fungovalo správně"
                        )
                        putString(
                            MediaMetadataCompat.METADATA_KEY_DISPLAY_TITLE,
                            "Aplikace na mobilním telefonu neběží"
                        )
                        putString(MediaMetadataCompat.METADATA_KEY_ARTIST, "Jiri")
                        putString(
                            MediaMetadataCompat.METADATA_KEY_ALBUM_ART_URI,
                            "https://storage.googleapis.com/automotive-media/album_art.jpg"
                        )
                    })
                }.build()

                rootList.add(MediaItem(podcastsDescription, MediaItem.FLAG_PLAYABLE))

                result.sendResult(rootList)
                return
            }
        }

        if (parentId == "/") {
            val rootList = mutableListOf<MediaItem>()

            val podcastsDescription = MediaDescriptionCompat.Builder().apply {
                setMediaId("Podcasts")
                setTitle("Podcasts")
                setDescription("Podcasts description")
                setSubtitle("Podcasts subtitle")
                setExtras(Bundle().apply {
                    putString(MediaMetadataCompat.METADATA_KEY_MEDIA_ID, "Podcasts")
                    putString(MediaMetadataCompat.METADATA_KEY_TITLE, "Podcasts")
                    putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_DESCRIPTION, "Podcasts")
                    putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_TITLE, "Podcasts")
                    putString(MediaMetadataCompat.METADATA_KEY_ARTIST, "Jiri")
                    putString(
                        MediaMetadataCompat.METADATA_KEY_ALBUM_ART_URI,
                        "https://storage.googleapis.com/automotive-media/album_art.jpg"
                    )
                })
            }.build()

            val audiobooksDescription = MediaDescriptionCompat.Builder().apply {
                setMediaId("Audiobooks")
                setTitle("Audiobooks")
                setDescription("Audiobooks description")
                setSubtitle("Audiobooks subtitle")
                setExtras(Bundle().apply {
                    putString(MediaMetadataCompat.METADATA_KEY_MEDIA_ID, "Audiobooks")
                    putString(MediaMetadataCompat.METADATA_KEY_TITLE, "Audiobooks")
                    putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_DESCRIPTION, "Audiobooks")
                    putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_TITLE, "Audiobooks")
                    putString(MediaMetadataCompat.METADATA_KEY_ARTIST, "Jiri")
                    putString(
                        MediaMetadataCompat.METADATA_KEY_ALBUM_ART_URI,
                        "https://storage.googleapis.com/uamp/The_Kyoto_Connection_-_Wake_Up/art.jpg"
                    )
                })
            }.build()

            val downloadsDescription = MediaDescriptionCompat.Builder().apply {
                setMediaId("Stažené")
                setTitle("Stažené")
                setDescription("Stažené description")
                setSubtitle("Stažené subtitle")
                setExtras(Bundle().apply {
                    putString(MediaMetadataCompat.METADATA_KEY_MEDIA_ID, "Stažené")
                    putString(MediaMetadataCompat.METADATA_KEY_TITLE, "Stažené")
                    putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_DESCRIPTION, "Stažené")
                    putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_TITLE, "Stažené")
                    putString(MediaMetadataCompat.METADATA_KEY_ARTIST, "Jiri")
                    putString(
                        MediaMetadataCompat.METADATA_KEY_ALBUM_ART_URI,
                        "https://storage.googleapis.com/uamp/The_Kyoto_Connection_-_Wake_Up/art.jpg"
                    )
                })
            }.build()

            val searchDescription = MediaDescriptionCompat.Builder().apply {
                setMediaId("Hledej")
                setTitle("Hledej")
                setDescription("Hledej description")
                setSubtitle("Hledej subtitle")
                setExtras(Bundle().apply {
                    putString(MediaMetadataCompat.METADATA_KEY_MEDIA_ID, "Hledej")
                    putString(MediaMetadataCompat.METADATA_KEY_TITLE, "Hledej")
                    putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_DESCRIPTION, "Hledej")
                    putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_TITLE, "Hledej")
                    putString(MediaMetadataCompat.METADATA_KEY_ARTIST, "Jiri")
                    putString(
                        MediaMetadataCompat.METADATA_KEY_ALBUM_ART_URI,
                        "https://storage.googleapis.com/uamp/The_Kyoto_Connection_-_Wake_Up/art.jpg"
                    )
                })
            }.build()

            rootList.add(MediaItem(podcastsDescription, MediaItem.FLAG_BROWSABLE))
            rootList.add(MediaItem(audiobooksDescription, MediaItem.FLAG_BROWSABLE))
            rootList.add(MediaItem(downloadsDescription, MediaItem.FLAG_BROWSABLE))
            rootList.add(MediaItem(searchDescription, MediaItem.FLAG_BROWSABLE))

            Log.d("MusicService", "MusicService.onLoadChildren() - sending rootList " + rootList);

            result.sendResult(rootList)
            return
        } else if (parentId == "Podcasts") {
            val podcastsList = mutableListOf<MediaItem>()

            val forYouDescription = MediaDescriptionCompat.Builder().apply {
                setMediaId("Pro Tebe")
                setTitle("Pro Tebe")
                setDescription("Pro Tebe description")
                setSubtitle("Pro Tebe subtitle")
                setExtras(Bundle().apply {
                    putString(MediaMetadataCompat.METADATA_KEY_MEDIA_ID, "Pro Tebe")
                    putString(MediaMetadataCompat.METADATA_KEY_TITLE, "Pro Tebe")
                    putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_DESCRIPTION, "Pro Tebe")
                    putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_TITLE, "Pro Tebe")
                    putString(MediaMetadataCompat.METADATA_KEY_ARTIST, "Jiri")
                    putString(
                        MediaMetadataCompat.METADATA_KEY_ALBUM_ART_URI,
                        "https://storage.googleapis.com/automotive-media/album_art_2.jpg"
                    )
                })
            }.build()
            val historyDescription = MediaDescriptionCompat.Builder().apply {
                setMediaId("History")
                setTitle("History")
                setDescription("History description")
                setSubtitle("History subtitle")
                setExtras(Bundle().apply {
                    putString(MediaMetadataCompat.METADATA_KEY_MEDIA_ID, "History")
                    putString(MediaMetadataCompat.METADATA_KEY_TITLE, "History")
                    putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_DESCRIPTION, "History")
                    putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_TITLE, "History")
                    putString(MediaMetadataCompat.METADATA_KEY_ARTIST, "Jiri")
                    putString(
                        MediaMetadataCompat.METADATA_KEY_ALBUM_ART_URI,
                        "https://storage.googleapis.com/automotive-media/album_art_2.jpg"
                    )
                })
            }.build()

            val song = MediaDescriptionCompat.Builder().apply {
                val mediaId = "wake_up_01"
                val title = "Intro - The Way Of Waking Up (feat. Alan Watts)"
                val description = "Intro - The Way Of Waking Up (feat. Alan Watts) - description"
                val subtitle = "Intro - The Way Of Waking Up (feat. Alan Watts) - subtitle"
                val uri =
                    Uri.parse("https://storage.googleapis.com/uamp/The_Kyoto_Connection_-_Wake_Up/01_-_Intro_-_The_Way_Of_Waking_Up_feat_Alan_Watts.mp3")

                setMediaId(mediaId)
                setTitle(title)
                setMediaUri(uri)
                setDescription(description)
                setSubtitle(subtitle)
                setExtras(Bundle().apply {
                    putString(MediaMetadataCompat.METADATA_KEY_MEDIA_ID, mediaId)
                    putString(MediaMetadataCompat.METADATA_KEY_TITLE, title)
                    putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_TITLE, title)
                    putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_DESCRIPTION, description)
                    putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_SUBTITLE, subtitle)
                    putString(MediaMetadataCompat.METADATA_KEY_ARTIST, "Jiri")
                    putString(MediaMetadataCompat.METADATA_KEY_MEDIA_URI, uri.toString())
                    putString(
                        MediaMetadataCompat.METADATA_KEY_ALBUM_ART_URI,
                        "https://storage.googleapis.com/automotive-media/album_art_2.jpg"
                    )
                    putLong(
                        MediaMetadataCompat.METADATA_KEY_DOWNLOAD_STATUS,
                        MediaDescriptionCompat.STATUS_NOT_DOWNLOADED
                    )
                })
            }.build()

            podcastsList.add(MediaItem(forYouDescription, MediaItem.FLAG_BROWSABLE))
            podcastsList.add(MediaItem(historyDescription, MediaItem.FLAG_BROWSABLE))
            podcastsList.add(MediaItem(song, MediaItem.FLAG_PLAYABLE))

            Log.d(
                "MusicService",
                "MusicService.onLoadChildren() - sending rootList " + podcastsList
            );

            result.sendResult(podcastsList)
        } else {
            result.sendResult(null)
        }
    }

    override fun onLoadItem(itemId: String?, result: Result<MediaItem>) {
        super.onLoadItem(itemId, result)
    }


    @MainThread
    override fun onHeadlessJsTaskFinish(taskId: Int) {
        Log.d("MusicService", "MusicService.onHeadlessJsTaskFinish()");

        // This is empty so ReactNative doesn't kill this service
    }

    @MainThread
    override fun onDestroy() {
        super.onDestroy()
        Log.d("MusicService", "MusicService.onDestroy()");
        if (::player.isInitialized) {
            player.destroy()
        }

        progressUpdateJob?.cancel()
    }

    @MainThread
    inner class MusicBinder : Binder() {
        val service = this@MusicService
    }

    companion object {
        const val EMPTY_NOTIFICATION_ID = 1
        const val STATE_KEY = "state"
        const val ERROR_KEY = "error"
        const val EVENT_KEY = "event"
        const val DATA_KEY = "data"
        const val TRACK_KEY = "track"
        const val NEXT_TRACK_KEY = "nextTrack"
        const val POSITION_KEY = "position"
        const val DURATION_KEY = "duration"
        const val BUFFERED_POSITION_KEY = "buffer"

        const val TASK_KEY = "TrackPlayer"

        const val MIN_BUFFER_KEY = "minBuffer"
        const val MAX_BUFFER_KEY = "maxBuffer"
        const val PLAY_BUFFER_KEY = "playBuffer"
        const val BACK_BUFFER_KEY = "backBuffer"

        const val FORWARD_JUMP_INTERVAL_KEY = "forwardJumpInterval"
        const val BACKWARD_JUMP_INTERVAL_KEY = "backwardJumpInterval"
        const val PROGRESS_UPDATE_EVENT_INTERVAL_KEY = "progressUpdateEventInterval"

        const val MAX_CACHE_SIZE_KEY = "maxCacheSize"

        const val ANDROID_OPTIONS_KEY = "android"

        const val STOPPING_APP_PAUSES_PLAYBACK_KEY = "stoppingAppPausesPlayback"
        const val APP_KILLED_PLAYBACK_BEHAVIOR_KEY = "appKilledPlaybackBehavior"
        const val PAUSE_ON_INTERRUPTION_KEY = "alwaysPauseOnInterruption"
        const val AUTO_UPDATE_METADATA = "autoUpdateMetadata"
        const val AUTO_HANDLE_INTERRUPTIONS = "autoHandleInterruptions"
        const val ANDROID_AUDIO_CONTENT_TYPE = "androidAudioContentType"
        const val IS_FOCUS_LOSS_PERMANENT_KEY = "permanent"
        const val IS_PAUSED_KEY = "paused"

        const val DEFAULT_JUMP_INTERVAL = 15.0
    }
}


//    private inner class UampPlaybackPreparer : MediaSessionConnector.PlaybackPreparer {
//
//        /**
//         * UAMP supports preparing (and playing) from search, as well as media ID, so those
//         * capabilities are declared here.
//         *
//         * TODO: Add support for ACTION_PREPARE and ACTION_PLAY, which mean "prepare/play something".
//         */
//        override fun getSupportedPrepareActions(): Long =
//            PlaybackStateCompat.ACTION_PREPARE_FROM_MEDIA_ID or
//                    PlaybackStateCompat.ACTION_PLAY_FROM_MEDIA_ID or
//                    PlaybackStateCompat.ACTION_PREPARE_FROM_SEARCH or
//                    PlaybackStateCompat.ACTION_PLAY_FROM_SEARCH
//
//        override fun onPrepare(playWhenReady: Boolean) {
//            Log.d("uamp", "MusicService.UampPlaybackPreparer.onPrepare()")
//
//
////            val recentSong = storage.loadRecentSong() ?: return
////            onPrepareFromMediaId(
////                recentSong.mediaId!!,
////                playWhenReady,
////                recentSong.description.extras
////            )
//        }
//
//        override fun onPrepareFromMediaId(
//            mediaId: String,
//            playWhenReady: Boolean,
//            extras: Bundle?
//        ) {
//            Log.d("uamp", "MusicService.UampPlaybackPreparer.onPrepareFromMediaId()")
//
////            mediaSource.whenReady {
////                val itemToPlay: MediaMetadataCompat? = mediaSource.find { item ->
////                    item.id == mediaId
////                }
////                if (itemToPlay == null) {
////                    Log.w(TAG, "Content not found: MediaID=$mediaId")
////                    // TODO: Notify caller of the error.
////                } else {
////
////                    val playbackStartPositionMs =
////                        extras?.getLong(MEDIA_DESCRIPTION_EXTRAS_START_PLAYBACK_POSITION_MS, C.TIME_UNSET)
////                            ?: C.TIME_UNSET
////
////                    preparePlaylist(
////                        buildPlaylist(itemToPlay),
////                        itemToPlay,
////                        playWhenReady,
////                        playbackStartPositionMs
////                    )
////                }
////            }
//        }
//
//        /**
//         * This method is used by the Google Assistant to respond to requests such as:
//         * - Play Geisha from Wake Up on UAMP
//         * - Play electronic music on UAMP
//         * - Play music on UAMP
//         *
//         * For details on how search is handled, see [AbstractMusicSource.search].
//         */
//        override fun onPrepareFromSearch(query: String, playWhenReady: Boolean, extras: Bundle?) {
//            Log.d("uamp", "MusicService.UampPlaybackPreparer.onPrepareFromSearch()")
//
////            mediaSource.whenReady {
////                val metadataList = mediaSource.search(query, extras ?: Bundle.EMPTY)
////                if (metadataList.isNotEmpty()) {
////                    preparePlaylist(
////                        metadataList,
////                        metadataList[0],
////                        playWhenReady,
////                        playbackStartPositionMs = C.TIME_UNSET
////                    )
////                }
////            }
//        }
//
//        override fun onPrepareFromUri(uri: Uri, playWhenReady: Boolean, extras: Bundle?) = Unit
//
//        override fun onCommand(
//            player: Player,
//            command: String,
//            extras: Bundle?,
//            cb: ResultReceiver?
//        ) = false
//
////        /**
////         * Builds a playlist based on a [MediaMetadataCompat].
////         *
////         * TODO: Support building a playlist by artist, genre, etc...
////         *
////         * @param item Item to base the playlist on.
////         * @return a [List] of [MediaMetadataCompat] objects representing a playlist.
////         */
////        private fun buildPlaylist(item: MediaMetadataCompat): List<MediaMetadataCompat> =
////            mediaSource.filter { it.album == item.album }.sortedBy { it.trackNumber }
//    }

