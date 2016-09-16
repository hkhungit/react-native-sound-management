import React from 'react';
import {
  Text,
  View,
  Image,
  Switch,
  Slider,
  Platform,
  StyleSheet,
  NativeModules,
  TouchableOpacity,
} from 'react-native';

const filename  = "https://api.soundcloud.com/tracks/243823577/stream?client_id=f3b190ea31e81a860f53326e2f8a92da";

const options = {
  title: "We don't talk any more",
  singer: 'Charlie Puth',
  showNotification: true,
  image_url: "http://image.mp3.zdn.vn/covers/1/9/191486d4ba699a1ce061251e53767546_1471003295.jpg",
}

const options2 = {
  title: "Beautiful in white",
  singer: 'Shane Filan',
  showNotification: true,
  image_url: "http://hocsao.net/wp-content/uploads/2014/12/loi-bai-hat-beautiful-in-white-shane-filan.jpg",
}

const filerecorder = "recorder.mp4";

const filename2  = "https://api.soundcloud.com/tracks/17312504/stream?client_id=f3b190ea31e81a860f53326e2f8a92da";

import SoundPlayer    from './SoundPlayer'
import SoundRecorder  from './SoundRecorder'

class Media extends React.Component {
  constructor() {
    super();
    this.initPlayer();
    this.initRecorder();
    this.state = {
      position: 0,
      progress: 0,
      duration: 0,
      error: null,
    }
  }

  onSeek(percentage){
    const { duration } = this.state
    let position = duration ? (percentage * duration) : 0;  
    this.player.seek(position)
  }

  initPlayer(){
    this.player  = new SoundPlayer(filename2, options);
    this.player.prepare(filename2, (eee,eeee)=>{
      if (eee) {
        debugger
      }
    })
    this.player.on("ended", (data)=>{
      
    })

    this.player.play((eee)=>{
    })
    this.player.on("next", (data)=>{
      this.btn3();
    })
    this.player.on("previous", (data)=>{
      this.btn1();
    })

    this.player.on("progress", (data)=>{
      let position = data.position;
      let duration = data.duration;
      let progress = duration != 0 ? (position / duration) : 0;
      this.setState({position, duration, progress});
    })   
  }  

  initRecorder(){
    this.recorder = new SoundRecorder(filerecorder, {
      bitrate: 256000,
      channels: 2,
      sampleRate: 44100,
      quality: 'max'
    });

    this.recorder.on("progress", (data)=>{
      let position = data.position;
      this.setState({position});
    })   
  }

  prepare(){
    this.recorder.prepare((ee,eee)=>{
      debugger
    })
  }

  toggleRecord(){
    this.recorder.toggleRecord((error, stopped) => {
      if (error)
        this.setState(error)
    });
  }

  seek(){
    const { position } = this.state
    this.player.seek(position * 1.5, (ee)=>{
      debugger
    })
  }

  btn1(){
    this.btn2();
    this.player.play();
  }

  btn2(){
    this.player.options(options)
    this.player.setUrl(filename)
  }  

  btn4(){
    this.player.options(options2)
    this.player.setUrl(filename2)
  }  

  btn3(){
    this.btn4();
    this.player.play();
  }  

  btn5(){
    this.player.pause();
  }

  btn6(){
    this.player.pause();
  }  

  btn7(){
    this.player.setUrl(filerecorder)
    this.player.play();
  }

  render() {
    return (
      <View>
        <View style={styles.container}>
          <TouchableOpacity onPress={this.btn1.bind(this)} style={{backgroundColor: 'red', margin: 1, padding: 10}}>
            <Text>Play 1</Text>
          </TouchableOpacity>
          <TouchableOpacity onPress={this.btn3.bind(this)} style={{backgroundColor: 'blue', margin: 1, padding: 10}}>
            <Text>Play 2</Text>
          </TouchableOpacity>
          <TouchableOpacity onPress={this.btn5.bind(this)} style={{backgroundColor: 'blue', margin: 1, padding: 10}}>
            <Text>Pause</Text>
          </TouchableOpacity>
          <TouchableOpacity onPress={this.seek.bind(this)} style={{backgroundColor: 'blue', margin: 1, padding: 10}}>
            <Text>Seek</Text>
          </TouchableOpacity>
          <TouchableOpacity onPress={this.prepare.bind(this)} style={{backgroundColor: 'gray', margin: 1, padding: 10}}>
            <Text>Prepare</Text>
          </TouchableOpacity>
          <TouchableOpacity onPress={this.toggleRecord.bind(this)} style={{backgroundColor: 'gray', margin: 1, padding: 10}}>
            <Text>Record</Text>
          </TouchableOpacity>
          <TouchableOpacity onPress={this.btn7.bind(this)} style={{backgroundColor: 'gray', margin: 1, padding: 10}}>
            <Text>Play record</Text>
          </TouchableOpacity>
          <Text>{this.state.position}</Text>
          <Text>{this.state.error}</Text>
        </View>
        <View style={styles.slider}>
          <Slider step={0.0001} onValueChange={this.onSeek.bind(this)} value={this.state.progress}/>
        </View>
      </View>
    );
  }
}

var styles = StyleSheet.create({
  container: {
    flex: 1,
    paddingTop: 20,
    position: 'relative',
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: '#F5FCFF',
  },
  slider: {
    marginLeft: -5,
    marginRight: -10,
    ...Platform.select({
      ios: {
        marginLeft: 5,
        marginRight: 5,
      }
    })
  },

});

export default Media;