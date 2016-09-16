'use strict';

import {
  NativeModules,
  DeviceEventEmitter,
  NativeAppEventEmitter,
  Platform
} from 'react-native';

import _ from 'lodash';
import async from 'async';
import EventEmitter from 'eventemitter3';
import MediaStates from './MediaStates';

var RCTSMSoundRecorder = NativeModules.SoundRecorderModule;

class SoundRecorder extends EventEmitter {
  constructor(path, options = {}) {
    super();
    this._path = path;
    this._options = options;
    this._onReset();
    this._onHandleEvent = this._onHandleEvent.bind(this);

    let appEventEmitter = Platform.OS === 'ios' ? NativeAppEventEmitter : DeviceEventEmitter;
    appEventEmitter.addListener('RCTSoundRecorderModuleBridgeEvent', this._onHandleEvent);
  }

  _onHandleEvent(res){
    const { event, data } = res
    return this.emit(event, data);
  }

  _onReset() {
    this._state = MediaStates.IDLE;
    this._duration = -1;
    this._position = -1;
    this._lastSync = -1;
  }

  _updateState(err, state) {
    this._state = err ? MediaStates.ERROR : state;
  }

  prepare(callback = _.noop) {
    this._updateState(null, MediaStates.PREPARING);

    // Prepare recorder
    RCTSMSoundRecorder.prepare(this._path, this._options, (err, data) => {
      this._filepath = data ? data.filepath :  null;
      this._updateState(err, MediaStates.PREPARED);
      callback(err, data);
    });

    return this;
  }

  record(callback = _.noop) {
    let tasks = [];

    // Make sure recorder is prepared
    if (this._state <= MediaStates.IDLE)
      tasks.push((next) => {
        this.prepare(next);
      });

    // Start recording
    tasks.push((next) => {
      RCTSMSoundRecorder.record(next);
    });

    async.series(tasks, (err) => {
      if (err) {
        debugger
      }
      this._updateState(err, MediaStates.RECORDING);
      callback(err);
    });

    return this;
  }

  stop(callback = _.noop) {
    if (this._state >= MediaStates.RECORDING) {
      RCTSMSoundRecorder.stop((err) => {
        this._updateState(err, MediaStates.IDLE);
        callback(err);
      });
    } else {
      setTimeout(callback, 0);
    }
  }

  toggleRecord(callback = _.noop) {
    if (this._state === MediaStates.RECORDING) {
      this.stop((err) => {
        callback(err, true);
      });
    } else {
      this.record((err) => {
        callback(err, false);
      });
    }

    return this;
  }

  destroy(callback = _.noop) {
    this._onReset();
    RCTSMSoundRecorder.destroy(callback);
  }

  get state()       { return this._state;                          }
  get canRecord()   { return this._state >= MediaStates.PREPARED;  }
  get canPrepare()  { return this._state == MediaStates.IDLE;      }
  get isRecording() { return this._state == MediaStates.RECORDING; }
  get isPrepared()  { return this._state == MediaStates.PREPARED;  }
  get filepath()      { return this._filepath; }
}

export default SoundRecorder;
