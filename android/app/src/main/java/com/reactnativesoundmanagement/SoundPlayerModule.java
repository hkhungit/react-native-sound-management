package com.reactnativesoundmanagement;

import java.net.*;
import android.net.Uri;
import android.util.Log;
import android.content.Intent;
import android.os.Environment;
import android.graphics.Bitmap;
import android.content.Context;
import android.os.PowerManager;
import android.app.Notification;
import android.media.MediaPlayer;
import android.app.PendingIntent;
import android.widget.RemoteViews;
import android.content.IntentFilter;
import android.media.PlaybackParams;
import android.media.AudioAttributes;
import android.graphics.BitmapFactory;
import android.content.ContextWrapper;
import android.app.NotificationManager;
import android.support.annotation.Nullable;
import android.media.AudioAttributes.Builder;
import android.support.v4.app.NotificationCompat;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;

import com.facebook.react.bridge.Callback;
import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.bridge.ReactContext;
import com.facebook.react.bridge.WritableNativeMap;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.modules.core.DeviceEventManagerModule;

import java.io.File;
import java.util.Map;
import java.util.Timer;
import java.lang.Thread;
import java.util.HashMap;
import java.util.Objects;
import java.util.Map.Entry;
import java.io.IOException;
import java.util.TimerTask;

public class SoundPlayerModule extends ReactContextBaseJavaModule implements MediaPlayer.OnInfoListener, MediaPlayer.OnCompletionListener, MediaPlayer.OnSeekCompleteListener,
      MediaPlayer.OnBufferingUpdateListener {

  private static final int NOTIFICATION_ID = 8746221;
  private static final String LOG_TAG = "SoundPlayerModule";    
  public static final String BROADCAST_PLAYBACK_STOP = "stop",
            BROADCAST_PLAYBACK_NEXT = "next",
            BROADCAST_PLAYBACK_EXIT = "exit",
            BROADCAST_PLAYBACK_PLAY = "playback",
            BROADCAST_PLAYBACK_PREVIOUS = "previous";
  public  static MediaPlayer mMediaPlayer; //Set only play in app
  public static RemoteViews remoteViews;
  private NotificationCompat.Builder notifyBuilder;
  private NotificationManager mNotificationManager = null;
  private static TimerTask mTimerTask;
  private static Timer timer; 
  private Integer doPlayerId;
  private static boolean looping = false;
  private static String filepath;
  private ReactApplicationContext context;
  private final RemoteReceiver receiver = new RemoteReceiver(this);
  private PhoneListener phoneStateListener;
  private TelephonyManager phoneManager;
  private static WritableMap mMediaMeta = Arguments.createMap();

  public SoundPlayerModule(ReactApplicationContext reactContext) {
    super(reactContext);
    this.context = reactContext;
    IntentFilter intentFilter = new IntentFilter();
    intentFilter.addAction(BROADCAST_PLAYBACK_PREVIOUS);
    intentFilter.addAction(BROADCAST_PLAYBACK_NEXT);
    intentFilter.addAction(BROADCAST_PLAYBACK_STOP);
    intentFilter.addAction(BROADCAST_PLAYBACK_PLAY);
    intentFilter.addAction(BROADCAST_PLAYBACK_EXIT);
    registerReceiverRemote(intentFilter);
  }

  @Override
  public String getName() {
    return "RCTSoundPlayerModule";
  }

  public void emitEvent(String event, WritableMap data) {
    WritableMap payload = new WritableNativeMap();
    payload.putString("event", event);
    payload.putMap("data", data);

    this.context
            .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
            .emit("RCTSoundPlayerModuleBridgeEvent", payload);
  }

  private WritableMap errObj(final String code, final String message, final boolean enableLog) {
    WritableMap err = Arguments.createMap();

    StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
    String stackTraceString = "";

    for (StackTraceElement e : stackTrace) {
      stackTraceString += e.toString() + "\n";
    }

    err.putString("err", code);
    err.putString("message", message);

    if (enableLog) {
      err.putString("stackTrace", stackTraceString);
      Log.e(LOG_TAG, message);
      Log.d(LOG_TAG, stackTraceString);
    }

    return err;
  }

  private WritableMap errObj(final String code, final String message) {
    return errObj(code, message, true);
  }

  private Uri uriFromPath(String path) {
    File file = null;
    String fileNameWithoutExt;
    String extPath;

    // Try finding file in Android "raw" resources
    if (path.lastIndexOf('.') != -1) {
        fileNameWithoutExt = path.substring(0, path.lastIndexOf('.'));
    } else {
        fileNameWithoutExt = path;
    }

    int resId = this.context.getResources().getIdentifier(fileNameWithoutExt,
        "raw", this.context.getPackageName());
    if (resId != 0) {
        return Uri.parse("android.resource://" + this.context.getPackageName() + "/" + resId);
    }

    // Try finding file in app data directory
    extPath = new ContextWrapper(this.context).getFilesDir() + "/" + path;
    file = new File(extPath);
    if (file.exists()) {
        return Uri.fromFile(file);
    }

    // Try finding file on sdcard
    extPath = Environment.getExternalStorageDirectory() + "/" + path;
    file = new File(extPath);
    if (file.exists()) {
        return Uri.fromFile(file);
    }

    // Try finding file by full path
    file = new File(path);
    if (file.exists()) {
        return Uri.fromFile(file);
    }

    // Otherwise pass whole path string as URI and hope for the best
    return Uri.parse(path);
  }

  @ReactMethod
  public void destroy(Callback callback) {
    if (mMediaPlayer != null) {
      mMediaPlayer.release();
      WritableMap data = new WritableNativeMap();
      data.putString("message", "Destroyed player");
      emitEvent("destroy", data);
    }

    if (callback != null) {
      callback.invoke();
    }
    stopTask();
  }

  @ReactMethod
  public void seek(Integer position, Callback callback) {
    if (mMediaPlayer == null) {
      callback.invoke(errObj("notfound", "mMediaPlayer not found."));
      return;
    }

    if (position >= 0) {
      mMediaPlayer.seekTo(position);
    }
  }

  private WritableMap getInfo(MediaPlayer player) {
    WritableMap info = Arguments.createMap();

    info.putDouble("duration", player.getDuration());
    info.putDouble("position", player.getCurrentPosition());
    info.putDouble("audioSessionId", player.getAudioSessionId());

    return info;
  }

  @ReactMethod
  public void setUrl(String path, Callback callback) {
    this.prepare(path, callback);
  }

  @ReactMethod
  public void prepare(String path, Callback callback) {
    if (path == null || path.isEmpty()) {
      callback.invoke(errObj("nopath", "Provided path was empty"));
      return;
    }

    this.filepath = path;

    destroy(null);

    Uri uri = uriFromPath(path);

    this.mMediaPlayer =  new MediaPlayer();

    try {
      this.mMediaPlayer.reset();
      this.mMediaPlayer.setDataSource(this.context, uri);
    } catch (IOException e) {
      callback.invoke(errObj("invalidpath", e.toString()));
      return;
    }

    this.mMediaPlayer.setOnInfoListener(this);
    this.mMediaPlayer.setOnCompletionListener(this);
    this.mMediaPlayer.setOnSeekCompleteListener(this);

    try {
      this.mMediaPlayer.prepare();
      callback.invoke(null, getInfo(mMediaPlayer));
    } catch (Exception e) {
      callback.invoke(errObj("prepare", e.toString()));
    }
  }

  @ReactMethod
  public void set(ReadableMap options, Callback callback) {
      if (mMediaPlayer == null) {
        callback.invoke(errObj("notfound", "mMediaPlayer not found."));
        return;
      }

      if (options.hasKey("wakeLock") && options.getBoolean("wakeLock"))
        mMediaPlayer.setWakeMode(this.context, PowerManager.PARTIAL_WAKE_LOCK);

      if (options.hasKey("volume") && !options.isNull("volume")) {
        double vol = options.getDouble("volume");
        mMediaPlayer.setVolume((float) vol, (float) vol);
      }

      if (options.hasKey("looping") && !options.isNull("looping"))
        this.looping = options.getBoolean("looping");

      if (options.hasKey("title"))
        mMediaMeta.putString("title", options.getString("title"));

      if (options.hasKey("singer"))
        mMediaMeta.putString("singer", options.getString("singer"));

      if (options.hasKey("author"))
        mMediaMeta.putString("author", options.getString("author"));

      if (options.hasKey("image_url"))
        mMediaMeta.putString("image_url", options.getString("image_url"));

      if (options.hasKey("looping") && !options.isNull("looping"))
        this.looping = options.getBoolean("looping");

      if (options.hasKey("looping") && !options.isNull("looping"))
        this.looping = options.getBoolean("looping");

      if (options.hasKey("speed") || options.hasKey("pitch")) {
          PlaybackParams params = new PlaybackParams();

          if (options.hasKey("speed") && !options.isNull("speed")) {
              params.setSpeed((float) options.getDouble("speed"));
          }

          if (options.hasKey("pitch") && !options.isNull("pitch")) {
              params.setPitch((float) options.getDouble("pitch"));
          }

          mMediaPlayer.setPlaybackParams(params);
      }

      if (options.hasKey("showNotification") && options.getBoolean("showNotification"))
        this.showNotification();

      callback.invoke();
  }

  @ReactMethod
  public void play(Callback callback) {
    try {
      this.play();
      callback.invoke(null, getInfo(mMediaPlayer));
    } catch (Exception e) {
      callback.invoke(errObj("playback", e.toString()));
    }
  }

  public void play() throws Exception
  {
    if (this.mMediaPlayer == null)
      throw new Exception("MediaPlayer not found");

    try {
      this.mMediaPlayer.start();
      doTimerTask();
      this.showNotification();
    } catch (Exception e) {
      throw e;
    }
  }

  @ReactMethod
  public void pause(Callback callback) {
    try {
      this.pause();
      callback.invoke(null, getInfo(mMediaPlayer));
    } catch (Exception e) {
      callback.invoke(errObj("pause", e.toString()));
    }
  }

  public void pause() throws Exception
  {
    if (this.mMediaPlayer == null)
      throw new Exception("MediaPlayer not found");

    stopTask();
    try {
      mMediaPlayer.pause();
      this.showNotification();
    } catch (Exception e) {
      throw e;
    }
  }

  @ReactMethod
  public void stop(Callback callback) {
    try {
      this.stop();
    } catch (Exception e) {
      callback.invoke(errObj("stop", e.toString()));
    }
  }

  public void stop() throws Exception
  {
    if (mMediaPlayer == null)
      throw new Exception("MediaPlayer not found");

    stopTask();
    try {
      mMediaPlayer.seekTo(0);
      mMediaPlayer.pause();
    } catch (Exception e) {
      throw e;
    }
  }

  public boolean isPlaying(){
    return mMediaPlayer != null ? mMediaPlayer.isPlaying() : false;
  }

  @Override
  public void onBufferingUpdate(MediaPlayer player, int percent) {
    WritableMap data = new WritableNativeMap();
    data.putString("message", "Status update for media stream buffering");
    data.putInt("percent", percent);
    emitEvent("progress2", data);
  }

  @Override
  public void onSeekComplete(MediaPlayer player) {
    WritableMap data = new WritableNativeMap();
    data.putString("message", "Seek operation completed");
    emitEvent("seeked", data);
  }

  @Override
  public void onCompletion(MediaPlayer player) {
    WritableMap data = new WritableNativeMap();
    mMediaPlayer.seekTo(0);
    if (this.looping) {
      mMediaPlayer.start();
      data.putString("message", "Media playback looped");
      emitEvent("looped", data);
    } else {
      data.putString("message", "Playback completed");
      emitEvent("ended", data);
      stopTask();
    }
  }

  @Override
  public boolean onInfo(MediaPlayer player, int what, int extra) {
    WritableMap info = new WritableNativeMap();
    info.putInt("what", what);
    info.putInt("extra", extra);

    WritableMap data = new WritableNativeMap();
    data.putMap("info", info);
    data.putString("message", "Android MediaPlayer info");
    emitEvent("info", data);
    return false;
  }

  private void doTimerTask(){
    mTimerTask = new TimerTask() {
      public void run() {
        if (mMediaPlayer != null) {
            emitEvent("progress", currentPosition(mMediaPlayer));
        }
    }};
    timer = new Timer();
    timer.schedule(mTimerTask, 0, 100);  // 
  }
    
  private void stopTask(){
    if(mTimerTask!=null)
      mTimerTask.cancel();

    if(timer!=null){
      timer.cancel();
      timer.purge();
    }
  } 

  private WritableMap currentPosition(MediaPlayer mediaPlayer){
    WritableMap data   = new WritableNativeMap();
    data.putDouble("position", mediaPlayer.getCurrentPosition() );
    data.putDouble("duration", mediaPlayer.getDuration() );
    return data;
  }

  private void showNotification(){
    remoteViews = new RemoteViews(context.getPackageName(), R.layout.layout_notification_player);
    remoteViews.setImageViewResource(R.id.remoteview_notification_icon, R.mipmap.ic_launcher);

    if (mMediaMeta.hasKey("title"))
      remoteViews.setTextViewText(R.id.remoteview_notification_headline, mMediaMeta.getString("title"));

    if (mMediaMeta.hasKey("singer") && !mMediaMeta.isNull("singer"))
      remoteViews.setTextViewText(R.id.remoteview_notification_short_message, mMediaMeta.getString("singer"));

    if (mMediaMeta.hasKey("image_url") && !mMediaMeta.isNull("image_url")){
      try{
        URL url=new URL(mMediaMeta.getString("image_url"));
        Bitmap bmp = BitmapFactory.decodeStream(url.openConnection().getInputStream());
        remoteViews.setImageViewBitmap(R.id.remoteview_notification_icon, bmp); 
      }catch(Exception e){}
    }

    if (mMediaPlayer.isPlaying()) {
      remoteViews.setImageViewResource(R.id.btn_notification_play, android.R.drawable.ic_media_pause); 
    }else{
      remoteViews.setImageViewResource(R.id.btn_notification_play, android.R.drawable.ic_media_play);
    }

    // build notification
    notifyBuilder =  
      new NotificationCompat.Builder(context)
          .setSmallIcon(android.R.drawable.ic_lock_silent_mode_off)
          .setContentTitle("Content Title")
          .setContentText("Content Text")
          .setContent(remoteViews)
          .setPriority( NotificationCompat.PRIORITY_MIN);

    Notification mNotification = notifyBuilder.build();

    // set big content view for newer androids
    if (android.os.Build.VERSION.SDK_INT >= 16) {  
      mNotification.bigContentView = remoteViews;
    }
    
    remoteViews.setOnClickPendingIntent(R.id.btn_notification_previous, makePendingIntent(BROADCAST_PLAYBACK_PREVIOUS));
    remoteViews.setOnClickPendingIntent(R.id.btn_notification_play, makePendingIntent(BROADCAST_PLAYBACK_PLAY));
    remoteViews.setOnClickPendingIntent(R.id.btn_notification_next, makePendingIntent(BROADCAST_PLAYBACK_NEXT));
    remoteViews.setOnClickPendingIntent(R.id.btn_notification_stop, makePendingIntent(BROADCAST_PLAYBACK_STOP));
    remoteViews.setOnClickPendingIntent(R.id.btn_notification_stop, makePendingIntent(BROADCAST_PLAYBACK_EXIT));
    mNotificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);  
    mNotificationManager.notify(NOTIFICATION_ID, mNotification);  
  }

  private PendingIntent makePendingIntent(String broadcast) {
    Intent intent = new Intent(broadcast);
    return PendingIntent.getBroadcast(this.context, 0, intent, 0);
  }

  public void clearNotification() {
    if (mNotificationManager != null)
      mNotificationManager.cancel(NOTIFICATION_ID);
  }

  public void exitNotification() {
    mNotificationManager.cancelAll();
    clearNotification();
    notifyBuilder = null;
    mNotificationManager = null;
  }

  private void registerReceiverRemote(IntentFilter filter){
    getReactApplicationContext().registerReceiver(this.receiver, filter);
  }

  private void sendBroadcast(Intent intent){
    getReactApplicationContext().sendBroadcast(intent);
  }
}
