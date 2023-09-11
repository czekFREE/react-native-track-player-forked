import TrackPlayer, {Event} from '@czekfree/react-native-track-player-forked';

export async function PlaybackService() {
    TrackPlayer.addEventListener(Event.RemotePause, () => {
        console.log('Event.RemotePause');
        TrackPlayer.pause();
    });

    TrackPlayer.addEventListener(Event.RemotePlay, () => {
        console.log('Event.RemotePlay');
        TrackPlayer.play();
    });

    TrackPlayer.addEventListener(Event.RemoteNext, () => {
        console.log('Event.RemoteNext');
        TrackPlayer.skipToNext();
    });

    TrackPlayer.addEventListener(Event.RemotePrevious, () => {
        console.log('Event.RemotePrevious');
        TrackPlayer.skipToPrevious();
    });

    TrackPlayer.addEventListener(Event.RemoteJumpForward, async (event) => {
        console.log('Event.RemoteJumpForward', event);
        TrackPlayer.seekBy(event.interval);
    });

    TrackPlayer.addEventListener(Event.RemoteJumpBackward, async (event) => {
        console.log('Event.RemoteJumpBackward', event);
        TrackPlayer.seekBy(-event.interval);
    });

    TrackPlayer.addEventListener(Event.RemoteSeek, (event) => {
        console.log('Event.RemoteSeek', event);
        TrackPlayer.seekTo(event.position);
    });

    TrackPlayer.addEventListener(Event.RemoteDuck, async (event) => {
        console.log('Event.RemoteDuck', event);
    });

    TrackPlayer.addEventListener(Event.RemotePlayId, async (event) => {
        console.log('Event.RemotePlayId', event);
    });

    TrackPlayer.addEventListener(Event.PlaybackQueueEnded, (event) => {
        console.log('Event.PlaybackQueueEnded', event);
    });

    TrackPlayer.addEventListener(Event.PlaybackActiveTrackChanged, (event) => {
        console.log('Event.PlaybackActiveTrackChanged', event);
    });

    TrackPlayer.addEventListener(Event.PlaybackPlayWhenReadyChanged, (event) => {
        console.log('Event.PlaybackPlayWhenReadyChanged', event);
    });

    TrackPlayer.addEventListener("LoadChildren" as any, ({parentId}: any) => {
        console.log('Event.LoadChildren', parentId);

        const mockDataByParentId = {
            "/": [
                {
                    mediaId: "Podcasts",
                    title: "Podcasts",
                    subtitle: "Podcasts subtitle",
                    description: "Podcasts description",
                    mediaUri: null,
                    iconUri: null,
                    playable: false
                },
                {
                    mediaId: "Audiobooks",
                    title: "Audiobooks",
                    subtitle: "Audiobooks subtitle",
                    description: "Audiobooks description",
                    mediaUri: null,
                    iconUri: null,
                    playable: false
                },
                {
                    mediaId: "Stažené",
                    title: "Stažené",
                    subtitle: "Stažené subtitle",
                    description: "Stažené description",
                    mediaUri: null,
                    iconUri: null,
                    playable: false
                },
                {
                    mediaId: "Hledej",
                    title: "Hledej",
                    subtitle: "Hledej subtitle",
                    description: "Hledej description",
                    mediaUri: null,
                    iconUri: null,
                    playable: false
                },
            ],
            "Podcasts": [
                {
                    mediaId: "Pro Tebe",
                    title: "Pro Tebe",
                    subtitle: "Pro Tebe subtitle",
                    description: "Pro Tebe description",
                    mediaUri: null,
                    iconUri: null,
                    playable: false
                },
                {
                    mediaId: "History",
                    title: "History",
                    subtitle: "History subtitle",
                    description: "History description",
                    mediaUri: null,
                    iconUri: null,
                    playable: false
                },
                {
                    mediaId: "wake_up_01",
                    title: "Intro - The Way Of Waking Up (feat. Alan Watts)",
                    subtitle: "Intro - The Way Of Waking Up (feat. Alan Watts) - subtitle",
                    description: "Intro - The Way Of Waking Up (feat. Alan Watts) - description",
                    mediaUri: "https://storage.googleapis.com/uamp/The_Kyoto_Connection_-_Wake_Up/01_-_Intro_-_The_Way_Of_Waking_Up_feat_Alan_Watts.mp3",
                    iconUri: "https://storage.googleapis.com/uamp/The_Kyoto_Connection_-_Wake_Up/art.jpg",
                    playable: true
                },
            ],
            "Audiobooks": [
                {
                    mediaId: "Novinky",
                    title: "Novinky",
                    subtitle: "Novinky subtitle",
                    description: "Novinky description",
                    mediaUri: null,
                    iconUri: null,
                    playable: false
                },
                {
                    mediaId: "Dle abecedy",
                    title: "Dle abecedy",
                    subtitle: "Dle abecedy subtitle",
                    description: "Dle abecedy description",
                    mediaUri: null,
                    iconUri: null,
                    playable: false
                },
                {
                    mediaId: "wake_up_02",
                    title: "Geisha",
                    subtitle: "Geisha - subtitle",
                    description: "Geisha - description",
                    mediaUri: "https://storage.googleapis.com/uamp/Spatial Audio/Shore.wav",
                    iconUri: "https://storage.googleapis.com/uamp/The_Kyoto_Connection_-_Wake_Up/art.jpg",
                    playable: true
                },
            ]
        }

        setTimeout(() => {
            TrackPlayer.handleLoadChildren(parentId, mockDataByParentId[parentId] ?? [])
        }, 1000)
    });

    TrackPlayer.addEventListener(Event.PlaybackState, (event) => {
        console.log('Event.PlaybackState', event);
    });

    TrackPlayer.addEventListener(
        Event.PlaybackMetadataReceived,
        async ({title, artist}) => {
            const activeTrack = await TrackPlayer.getActiveTrack();
            TrackPlayer.updateNowPlayingMetadata({
                artist: [title, artist].filter(Boolean).join(' - '),
                title: activeTrack?.title,
                artwork: activeTrack?.artwork,
            });
        }
    );
}
