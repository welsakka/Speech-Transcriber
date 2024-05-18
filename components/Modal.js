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

const Modal = (props) => {
    const [modalTextInput, setModalTextInput] = useState(null);

    return (
        <Modal 
        visible={apiKey == null} 
        transparent={true}>
        <View 
          style={styles.modalViewOuter}>
          <BlurView
            style={styles.blurView}
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
    )
} 

const styles = StyleSheet.create({
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
      blurView: {
        position: 'absolute',
        top: 0,
        left: 0,
        bottom: 0,
        right: 0,
      },
});

export default Modal;