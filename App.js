import { React, useEffect, useState } from "react";
import {
  Button,
  Modal,
  NativeEventEmitter,
  NativeModules,
  PermissionsAndroid,
  Platform,
  SafeAreaView,
  ScrollView,
  StyleSheet,
  Text,
  TextInput,
  View,
} from "react-native";
import RNFS from "react-native-fs";
import { FFmpegKit, FFmpegKitConfig, ReturnCode } from "ffmpeg-kit-react-native";
import { BlurView } from "@react-native-community/blur";
import AsyncStorage from "@react-native-async-storage/async-storage";

if (Platform.OS === 'android') {
  // Request record audio permission
  // @ts-ignore
  PermissionsAndroid.requestMultiple([
    PermissionsAndroid.PERMISSIONS.RECORD_AUDIO,
    PermissionsAndroid.PERMISSIONS.WRITE_EXTERNAL_STORAGE,
    PermissionsAndroid.PERMISSIONS.READ_EXTERNAL_STORAGE,
  ]);
}

const {AudioModule} = NativeModules;
const audioModuleEvents = new NativeEventEmitter(AudioModule);
const ERROR_LEVEL = 3;

const App = () => {
  const [results, setResults] = useState('-----Begin Transcribing-----');
  const [logs, setLogs] = useState(null);
  const [isRecording, setIsRecording] = useState(false);
  const [targetPath, setTargetPath] = useState(' ');
  const [modalTextInput, setModalTextInput] = useState(null);
  const [apiKey, setApiKey] = useState(null);

  //Used to filter output from Whisper AI that it believes is silence
  const silenceThreshold = 0.25;

  // Receive API key for whisper API in cache if available
  useEffect(() => {
    getData('api').then(res => {
      setApiKey(res);
    });
  }, []);

  /**
   * Saving data to AsyncStorage
   * @param key
   * @param value
   * @returns {Promise<void>}
   */
  const saveData = async (key, value) => {
    try {
      await AsyncStorage.setItem(key, value);
    } catch (error) {
      console.error('Error saving data to AsyncStorage:', error);
    }
  };

  /**
   * Retrieving data from AsyncStorage
   * @param key
   * @returns {Promise<*|null>}
   */
  const getData = async key => {
    try {
      console.log('key:' + key);
      const value = await AsyncStorage.getItem(key);
      if (value !== null) {
        // Data found in cache
        console.log('value: ' + value);
        return value;
      } else {
        // Data not found in cache
        console.log('value null');
        return null;
      }
    } catch (error) {
      console.error('Error retrieving data from AsyncStorage:', error);
      return null;
    }
  };

  /**
    Function for converting PCM raw audio into MP3
   */
  const convertPcmToMp3 = async uri => {
    const filepath = RNFS.DocumentDirectoryPath + '/reactaudio.mp3';

    // Handle FFmpegKit initialization if needed
    await FFmpegKitConfig.enableRedirection();
    // Set the log level to a more silent option
    await FFmpegKitConfig.setLogLevel(ERROR_LEVEL);

    await RNFS.writeFile(filepath, '');
    try {
      // Execute FFmpeg command to convert PCM to MP3
      const result = await FFmpegKit.execute(
        `-ar 44100 -ac 1 -f s16le -i ${uri} -codec:a mp3 -y ${filepath}`,
      ).then(async session => {
        const returnCode = await session.getReturnCode();
        if (ReturnCode.isSuccess(returnCode)) {
          console.log(
            'Conversion successful:' + (await session.getReturnCode()),
          );
          await setTargetPath(filepath);
          return filepath;
        } else {
          console.error(
            'Conversion failed: ' + (await session.getReturnCode()),
          );
          setTargetPath(null);
          return null;
        }
      });
    } catch (error) {
      console.error('Error during conversion:', error);
    }
  };

  /**
   * Function for REST call to Whisper API.
   */
  const whisperRestCall = async uri => {
    try {
      const data = new FormData();
      data.append('model', 'whisper-1');
      data.append('file', {
        uri: 'file://' + uri,
        type: 'audio/mp4',
        name: 'reactaudio.mp3',
      });
      data.append('language', 'en');
      data.append('response_format', 'verbose_json');
      const res = await fetch(
        'https://api.openai.com/v1/audio/transcriptions',
        {
          method: 'POST',
          headers: {
            Authorization: 'Bearer ' + apiKey,
            'Content-Type': 'multipart/form-data',
            Accept: 'application/json',
          },
          body: data,
        },
      );
      const json = await res.json();
      return json;
    } catch (err) {
      console.log('Error caught in Whisper Rest API call: ', err);
    }
  };

  /**
   * Function for filtering out Whisper AI results for noise
   * @param result
   * @returns {Promise<*|string>}
   */
  const filterResult = async (result) => {
    console.log('Whisper response is: ' + result.text);
    // if (results.segments[0].isEmptyArray())
    const noSpeechProbability = result.segments[0].no_speech_prob;
    if (noSpeechProbability > silenceThreshold) {
      console.log("But is unlikely speech, discarding...")
      return '';
    }
    return result.text;
  };

  /**
   * Function for listening to Android AudioRecorder Events
   */
  const androidAudioRecorderListener = async () => {
    if (!isRecording) {
      await setResults('-----Begin Transcribing-----' + '\n');
      await setIsRecording(true);
      AudioModule.startRecording();
      // Subscribe to native events
      audioModuleEvents.addListener('AudioModule', async update => {
        const targetPath = RNFS.DocumentDirectoryPath + '/reactaudio.mp3';
        console.log('Audio written to: ' + update);
        await convertPcmToMp3(update);
        console.log('newPath: ' + targetPath);
        const res = await whisperRestCall(targetPath);
        const text = await res.text; //filterResult(res);
        console.log('res :' + text);
        if (!blacklist.includes(text)) {
          setResults(currentText => currentText + ' ' + text);
        }
      });
    } else if (isRecording) {
      await setIsRecording(false);
      await AudioModule.stopRecording();
      audioModuleEvents.removeAllListeners('AudioModule');
      console.log('Ending recording.');
    }
  };

  return (
    <SafeAreaView style={styles.background}>
      <Modal visible={apiKey == null} transparent={true}>
        <View style={styles.modalViewOuter}>
          <BlurView
            style={styles.absolute}
            blurType="light"
            blurAmount={10}
            reducedTransparencyFallbackColor="white"
          />
          <Text>
            Enter your API key retrieved from OpenAPI. Please note that this key
            will only be stored on your local device, and not by us!{' '}
          </Text>
          <TextInput
            style={{borderWidth: 1, padding: 10, width: 350, margin: 12}}
            onChangeText={text => {
              setModalTextInput(text);
            }}
          />
          <Button
            title={'Enter'}
            onPress={() => {
              saveData('api', modalTextInput);
              setApiKey(modalTextInput);
            }}
          />
        </View>
      </Modal>
      <Text style={styles.header}>Speech Transcriber</Text>
      <View style={styles.container}>
        <Text style={styles.logs}>{logs}</Text>
        <View style={styles.button}>
          <Button
            title={isRecording ? '   Stop Recording   ' : '   Begin   '}
            onPress={() => {
              if (Platform.OS === 'android') {androidAudioRecorderListener();}
              else if (Platform.OS === 'ios') { //iosAudioRecorderListener();
                 }
            }}
          />
        </View>
        <ScrollView
          ref={ref => (this.scrollView = ref)}
          onContentSizeChange={(contentWidth, contentHeight) => {
            this.scrollView.scrollToEnd({animated: true});
          }}>
          <Text style={styles.results}>{results}</Text>
        </ScrollView>
      </View>
    </SafeAreaView>
  );
};

const blacklist = [
  ' ',
  '',
  undefined,
  'undefined',
  '\n',
  'Go to Beadaholique.com for all of your beading supply needs!',
  'Thank you for watching!',
  'Thank you for watching.',
  'Thank you for watching my video. Don\'t forget to subscribe to my channel. Bye.',
];

const styles = StyleSheet.create({
  background: {
    backgroundColor: '#3c408c',
    flex: 1,
  },
  header: {
    textAlign: 'center',
    fontSize: 30,
    color: '#bcacd4',
  },
  container: {
    margin: 50,
    flex: 1,
  },
  button: {
    flexDirection: 'row',
    justifyContent: 'center',
  },
  results: {
    alignContent: 'center',
    fontSize: 25,
    color: '#bcacd4',
  },
  logs: {
    fontSize: 15,
    color: '#d76a74',
  },
  modalViewOuter: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
    padding: 22,
    flexDirection: 'column',
  },
  modalViewInner: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
    marginTop: 22,
    padding: 33,
  },
  blur: {
    position: 'absolute',
    top: 0,
    left: 0,
    bottom: 0,
    right: 0,
  },
});

export default App;
