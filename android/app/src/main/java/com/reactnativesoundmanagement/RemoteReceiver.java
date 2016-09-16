package com.reactnativesoundmanagement;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.widget.Toast;

import com.facebook.react.bridge.WritableMap;
import com.facebook.react.bridge.WritableNativeMap;

import java.io.IOException;

class RemoteReceiver extends BroadcastReceiver {
  private SoundPlayerModule player;

  public RemoteReceiver(SoundPlayerModule player) {
    super();
    this.player = player;
  }

  @Override
  public void onReceive(Context context, Intent intent) {
    String action = intent.getAction();
    WritableMap data = new WritableNativeMap();
    try {
      switch(action){
        case SoundPlayerModule.BROADCAST_PLAYBACK_PLAY:
          if(this.player.mMediaPlayer.isPlaying())
            this.player.pause();
          else
            this.player.play();
          break;
        case SoundPlayerModule.BROADCAST_PLAYBACK_EXIT:
          this.player.stop();
          this.player.exitNotification();
          break;
        case SoundPlayerModule.BROADCAST_PLAYBACK_NEXT:
          this.player.emitEvent("next", null);
          break;
        case SoundPlayerModule.BROADCAST_PLAYBACK_PREVIOUS:
          this.player.emitEvent("previous", null);
          break;
      }
    } catch (Exception e) {
      this.player.exitNotification();
    }
  }
}
