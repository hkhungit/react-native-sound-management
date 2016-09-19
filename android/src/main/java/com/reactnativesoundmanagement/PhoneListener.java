package com.reactnativesoundmanagement;

import android.content.Context;
import android.content.Intent;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;

import java.io.IOException;

public class PhoneListener extends PhoneStateListener {
  private SoundPlayerModule player;

  public PhoneListener(SoundPlayerModule player) {
    super();
    this.player = player;
  }

  @Override
  public void onCallStateChanged(int state, String incomingNumber) {
    switch (state) {
      case TelephonyManager.CALL_STATE_RINGING:
        if (this.player.mMediaPlayer.isPlaying()) {
          try{
            this.player.pause();
          }catch(Exception e){}
        }
        break;
      default:
        break;
    }
    super.onCallStateChanged(state, incomingNumber);
  }

  public void onReceive(Context context, Intent intent) {
    if (intent.getAction().equals(Intent.ACTION_HEADSET_PLUG)) {
      //Headset broadcast
      //  TODO
    }
  }
}
