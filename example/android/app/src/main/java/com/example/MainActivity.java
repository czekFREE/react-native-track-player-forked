package com.example;

import android.content.ComponentName;
import android.os.Bundle;
import android.util.Log;

import com.doublesymmetry.trackplayer.service.MusicService;
import com.doublesymmetry.trackplayer.service.MusicServiceConnection;
import com.facebook.react.ReactActivity;
import com.facebook.react.ReactActivityDelegate;
import com.facebook.react.ReactApplication;
import com.facebook.react.ReactInstanceManager;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContext;
import com.facebook.react.defaults.DefaultNewArchitectureEntryPoint;
import com.facebook.react.defaults.DefaultReactActivityDelegate;

import javax.annotation.Nullable;

public class MainActivity extends ReactActivity {
  @Nullable
  MusicServiceConnection connection = null;

  /**
   * Returns the name of the main component registered from JavaScript. This is used to schedule
   * rendering of the component.
   */
  @Override
  protected String getMainComponentName() {
    return "example";
  }

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
//    Log.d("MusicService", "MainActivity.onCreate()");

//    String context = requireContex();

//    connection = MusicServiceConnection.getInstance(this, new ComponentName(this, MusicService.class));

//    ReactInstanceManager reactInstanceManager = getReactInstanceManager();
//    ReactContext reactApplicationContext = reactInstanceManager.getCurrentReactContext();
//
//
//    reactInstanceManager.addReactInstanceEventListener(new ReactInstanceManager.ReactInstanceEventListener() {
//      @Override
//      public void onReactContextInitialized(ReactContext reactContext) {
//        Log.d("MusicService", "MainActivity.onCreate() - set connection");
//
//        connection = MusicServiceConnection.getInstance(reactContext, new ComponentName(reactContext, MusicService.class));
//      }
//    });
  }

  @Override
  protected void onStart() {
    super.onStart();
//    Log.d("MusicService", "MainActivity.onStart()" + connection + " " + connection.isConnected().getValue());


//    if (connection != null) {
//      connection.connect();
//    }
    // TODO - else

  }

  @Override
  public void onResume() {
    super.onResume();
//    Log.d("MusicService", "MainActivity.onResume() " + connection.isConnected().getValue());

    // TODO - finish
//    setVolumeControlStream(AudioManager.STREAM_MUSIC);
  }


  @Override
  protected void onStop() {
    super.onStop();
//    Log.d("MusicService", "MainActivity.onStop()" + connection + " " + connection.isConnected().getValue());

//    if (connection != null) {
//      connection.disconnect();
//    }
  }

  /**
   * Returns the instance of the {@link ReactActivityDelegate}. Here we use a util class {@link
   * DefaultReactActivityDelegate} which allows you to easily enable Fabric and Concurrent React
   * (aka React 18) with two boolean flags.
   */
  @Override
  protected ReactActivityDelegate createReactActivityDelegate() {
    return new DefaultReactActivityDelegate(
      this,
      getMainComponentName(),
      // If you opted-in for the New Architecture, we enable the Fabric Renderer.
      DefaultNewArchitectureEntryPoint.getFabricEnabled(), // fabricEnabled
      // If you opted-in for the New Architecture, we enable Concurrent React (i.e. React 18).
      DefaultNewArchitectureEntryPoint.getConcurrentReactEnabled() // concurrentRootEnabled
    );
  }
}
