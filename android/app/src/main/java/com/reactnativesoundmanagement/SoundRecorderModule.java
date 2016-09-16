package com.reactnativesoundmanagement;

import android.os.*;
import android.net.Uri;
import android.util.Log;
import android.os.Environment;
import android.webkit.URLUtil;
import android.media.MediaRecorder;
import android.content.ContextWrapper;
import android.support.annotation.Nullable;

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
import java.io.IOException;
import java.util.Map.Entry;
import java.util.TimerTask;
import java.net.URISyntaxException;

public class SoundRecorderModule extends ReactContextBaseJavaModule implements
      MediaRecorder.OnInfoListener, MediaRecorder.OnErrorListener {
  private static final String LOG_TAG = "SoundRecorderModule";

  private static MediaRecorder mMediaRecorder;
  private ReactApplicationContext context;
  private Integer currentRecorderId;
  private long startHTime = 0L;
  private TimerTask mTimerTask;
  private Timer timer;

  public SoundRecorderModule(ReactApplicationContext reactContext) {
    super(reactContext);
    this.context = reactContext;
  }

  @Override
  public String getName() {
    return "RCTSoundRecorderModule";
  }

  private void emitEvent(String event, WritableMap data) {
    WritableMap payload = new WritableNativeMap();
    payload.putString("event", event);
    payload.putMap("data", data);

    this.context
            .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
            .emit("RCTSoundRecorderModuleBridgeEvent", payload);
  }

  private WritableMap errObj(final String code, final String message) {
    WritableMap err = Arguments.createMap();

    StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
    String stackTraceString = "";

    for (StackTraceElement e : stackTrace) {
      stackTraceString += e.toString() + "\n";
    }

    err.putString("err", code);
    err.putString("message", message);
    err.putString("stackTrace", stackTraceString);
    return err;
  }

  private int formatFromName(String name) {
    switch (name) {
      case "aac":
        return MediaRecorder.OutputFormat.AAC_ADTS;
      case "mp4":
        return MediaRecorder.OutputFormat.MPEG_4;
      case "webm":
      case "ogg":
        return MediaRecorder.OutputFormat.WEBM;
      case "amr":
        return MediaRecorder.OutputFormat.AMR_WB;
      default:
        return MediaRecorder.OutputFormat.DEFAULT;
    }
  }
  private int formatFromPath(String path) {
    String ext = path.substring(path.lastIndexOf('.') + 1);
    return formatFromName(ext);
  }

  private int encoderFromName(String name) {
    switch (name) {
      case "aac":
      case "mp4":
        return MediaRecorder.AudioEncoder.HE_AAC;
      case "webm":
      case "ogg":
        return MediaRecorder.AudioEncoder.VORBIS;
      case "amr":
        return MediaRecorder.AudioEncoder.AMR_WB;
      default:
        return MediaRecorder.AudioEncoder.DEFAULT;
    }
  }
  private int encoderFromPath(String path) {
    String ext = path.substring(path.lastIndexOf('.') + 1);
    return encoderFromName(ext);
  }

  private Uri uriFromPath(String path) {
    Uri uri = null;

    if (URLUtil.isValidUrl(path)) {
        uri = Uri.parse(path);
    } else {
        String extPath = new ContextWrapper(this.context).getFilesDir() + "/" + path;
        //String extPath = Environment.getExternalStorageDirectory() + "/" + path;

        File file = new File(extPath);
        uri = Uri.fromFile(file);
    }

    return uri;
  }

  @ReactMethod
  public void destroy(Callback callback) {
    if (mMediaRecorder != null) {
      mMediaRecorder.reset();
      mMediaRecorder.release();
      mMediaRecorder =  null;
      WritableMap data = new WritableNativeMap();
      data.putString("message", "Destroyed recorder");
      emitEvent("destroy", data);
    }

    if (callback != null) {
      callback.invoke();
    }

    stopTask();
  }

  private void destroy() {
    this.destroy(null);
  }

  @ReactMethod
  public void prepare(String path, ReadableMap options, Callback callback) {
    if (path == null || path.isEmpty()) {
        callback.invoke(errObj("invalidpath", "Provided path was empty"));
        return;
    }

    Uri uri = uriFromPath(path);
    destroy(null);
    mMediaRecorder =  new MediaRecorder();

    int format = formatFromPath(path);
    int encoder = encoderFromPath(path);
    int bitrate = 128000;
    int channels = 2;
    int sampleRate = 44100;

    if (options.hasKey("format")) {
      format = formatFromName(options.getString("format"));
    }
    if (options.hasKey("encoder")) {
      encoder = encoderFromName(options.getString("encoder"));
    }
    if (options.hasKey("bitrate")) {
      bitrate = options.getInt("bitrate");
    }
    if (options.hasKey("channels")) {
      channels = options.getInt("channels");
    }
    if (options.hasKey("sampleRate")) {
      sampleRate = options.getInt("sampleRate");
    }


    mMediaRecorder.reset();
    mMediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
    mMediaRecorder.setOutputFormat(format);
    mMediaRecorder.setAudioEncoder(encoder);
    mMediaRecorder.setAudioEncodingBitRate(bitrate);
    mMediaRecorder.setAudioChannels(channels);
    mMediaRecorder.setAudioSamplingRate(sampleRate);
    mMediaRecorder.setOutputFile(uri.getPath());
    mMediaRecorder.setOnErrorListener(this);
    mMediaRecorder.setOnInfoListener(this);

    try {
      mMediaRecorder.prepare();
      WritableMap data   = new WritableNativeMap();
      data.putString("filepath",  uri.getPath());
      callback.invoke(null, data);
    } catch (IOException e) {
      WritableMap data = new WritableNativeMap();
      data.putString("message", e.toString());
      emitEvent("preparefail", data);
      callback.invoke(errObj("preparefail", e.toString()));
    }
  }

  @ReactMethod
  public void record(Callback callback) {
    if (mMediaRecorder == null) {
      callback.invoke(errObj("notfound", "MediaRecorder not found."));
      return;
    }

    try {
      mMediaRecorder.start();
      doTimerTask();
      callback.invoke();
    } catch (Exception e) {
      callback.invoke(errObj("startfail", e.toString()));
    }
  }

  @ReactMethod
  public void stop(Callback callback) {
    if (mMediaRecorder == null) {
      callback.invoke(errObj("notfound", "MediaRecorder not found."));
      return;
    }

    try {
      mMediaRecorder.stop();
      callback.invoke();
      WritableMap data = new WritableNativeMap();
      emitEvent("stop", data);
    } catch (Exception e) {
      callback.invoke(errObj("stopfail", e.toString()));
    }
    stopTask();
  }

  @Override
  public void onError(MediaRecorder recorder, int what, int extra) {
    // TODO: translate these codes into english
    WritableMap err = new WritableNativeMap();
    err.putInt("what", what);
    err.putInt("extra", extra);

    WritableMap data = new WritableNativeMap();
    data.putMap("err", err);
    data.putString("message", "Android MediaRecorder error");
    emitEvent("error", data);

    destroy();
  }

  @Override
  public void onInfo(MediaRecorder recorder, int what, int extra) {
    // TODO: translate these codes into english
    WritableMap info = new WritableNativeMap();
    info.putInt("what", what);
    info.putInt("extra", extra);

    WritableMap data = new WritableNativeMap();
    data.putMap("info", info);
    data.putString("message", "Android MediaRecorder info");
    emitEvent( "info", data);

  }

  public void doTimerTask(){
    startHTime = SystemClock.uptimeMillis();
    mTimerTask = new TimerTask() {
    public void run() {
      if (mMediaRecorder != null) {
        double currentTime  = (float)(SystemClock.uptimeMillis() - startHTime);
        WritableMap data   = new WritableNativeMap();
        data.putDouble("position", currentTime);
        emitEvent("progress", data);
      }
    }};

    timer = new Timer();
    timer.schedule(mTimerTask, 0, 100);  // 
  }

  public void stopTask(){
    if(mTimerTask != null)
      mTimerTask.cancel();

    if (timer != null) {
      timer.cancel();
      timer.purge();
    }
  }
}
