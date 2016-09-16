import {
  Platform,
  NativeModules,
  DeviceEventEmitter,
  NativeAppEventEmitter,
} from 'react-native';

import _ from 'lodash';
import async from 'async';
import EventEmitter from 'eventemitter3';
import MediaStates from './MediaStates';

const RCTSoundPlayerModule = NativeModules.SoundPlayerModule

class SoundPlayer extends EventEmitter {
  constructor(path, options = {}) {
    super();
    this._onReset();
    this._path = path;
    this._options = options;
    this._onHandleEvent = this._onHandleEvent.bind(this);

    let appEventEmitter = Platform.OS === 'ios' ? NativeAppEventEmitter : DeviceEventEmitter;
    appEventEmitter.addListener('RCTSoundPlayerModuleBridgeEvent', this._onHandleEvent);
  }

  _onHandleEvent(res){
    const { event, data } = res
    return this.emit(event, data);
  }

  _onReset() {
    this._state = MediaStates.IDLE;
    this._volume = 1.0;
    this._pan = 0.0;
    this._image_url = null;
    this._title = null;
    this._author = null;
    this._singer = null
    this._wakeLock = false;
    this._duration = -1;
    this._position = -1;
    this._looping = false;
  }

  _fetchInfo(info) {
    if (!info) {
      return;
    }

    this._duration = info.duration;
    this._position = info.position;
    this._title    = info.title;
    this._author   = info.author;
    this._singer   = info.singer;
    this._image_url  = info.image_url;
  }

  _updateState(err, state, results) {
    this._state = err ? MediaStates.ERROR : state;

    if (err || !results) {
      return;
    }

    let info = _.last(_.filter(results, _.identity));
    this._fetchInfo(info);
  }

  options(options = {}, callback = _.noop){
    RCTSoundPlayerModule.set(options, callback);
  }

  prepare(path = null, callback = _.noop) {
    if (path)
      this._path = path;

    this._updateState(null, MediaStates.PREPARING);

    let tasks = [];

    // Prepare player
    tasks.push((next) => {
      RCTSoundPlayerModule.prepare(this._path, next);
    });

    const { _options } =  this
    // Set initial values for player options
    tasks.push((next) => {
      RCTSoundPlayerModule.set({
        ..._options,
        pan: this._pan,
        volume: this._volume,
        looping: this._looping,
        wakeLock: this._wakeLock
      }, next);
    });

    async.series(tasks, (err, results) => {
      this._updateState(err, MediaStates.PREPARED, results);
      callback(err);
    });

    return this;
  }

  play(callback = _.noop) {
    let tasks = [];

    // Make sure player is prepared
    if(this._state === MediaStates.IDLE) {
      tasks.push((next) => {
        this.prepare(next);
      });
    }

    // Start playback
    tasks.push((next) => {
      RCTSoundPlayerModule.play(next);
    });

    async.series(tasks, (err, results) => {
      this._updateState(err, MediaStates.PLAYING, results);
      callback(err);
    });

    return this;
  }

  pause(callback = _.noop) {
    RCTSoundPlayerModule.pause((err, results) => {
      this._updateState(err, MediaStates.PAUSED, [results]);
      callback(err);
    });

    return this;
  }  

  setUrl(path =  null, callback = _.noop) {
    RCTSoundPlayerModule.setUrl(path, callback)
    return this;
  }

  playPause(callback = _.noop) {
    if (this._state === MediaStates.PLAYING) {
      this.pause((err) => {
        callback(err, true);
      });
    } else {
      this.play((err) => {
        callback(err, false);
      });
    }

    return this;
  }

  stop(callback = _.noop) {
    RCTSoundPlayerModule.stop((err, results) => {
      this._updateState(err, MediaStates.PREPARED);
      this._position = -1;
      callback(err);
    });

    return this;
  }

  destroy(callback = _.noop) {
    this._reset();
    RCTSoundPlayerModule.destroy(callback);
  }

  seek(position = 0, callback = _.noop) {
    if (this._state != MediaStates.SEEKING) {
      this._preSeekState = this._state;
    }

    this._updateState(null, MediaStates.SEEKING);
    RCTSoundPlayerModule.seek(position, (err, results) => {
      if (err && err.err === 'seekfail') {
        return;
      }
      this._updateState(err, this._preSeekState, [results]);
      callback(err);
    });
  }

  _setIfInitialized(options, callback = _.noop) {
    RCTSoundPlayerModule.set(options, callback);
  }

  set title(value) {
    this._title = value;
    this._setIfInitialized({'title': value});
  }
  
  set singer(value) {
    this.singer = value;
    this._setIfInitialized({'singer': value});
  }

  set author(value) {
    this._title = value;
    this._setIfInitialized({'author': value});
  }

  set image_url(value) {
    this._image_url = value;
    this._setIfInitialized({'image_url': value});
  }

  set volume(value) {
    this._volume = value;
    this._setIfInitialized({'volume': value});
  }

  set currentTime(value) {
    this.seek(value);
  }

  set wakeLock(value) {
    this._wakeLock = value;
    this._setIfInitialized({'wakeLock': value});
  }

  set looping(value) {
    this._looping = value;
    this._setIfInitialized({'looping': value});
  }

  get currentTime() {
    return this._position;
  }

  get title(){
    return this._title;
  }

  get author(){
    return this._author;
  }

  get singer(){
    return this._singer;
  }

  get image_url(){
    return this._image_url;
  }
}

export default SoundPlayer;
