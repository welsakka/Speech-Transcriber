package com.whisper_react_native.modules;

import androidx.annotation.NonNull;

import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.modules.core.DeviceEventManagerModule;

import android.annotation.SuppressLint;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.util.Log;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

public class AudioModule extends ReactContextBaseJavaModule {

    //Variables used for obtaining the minimum Buffer size required for the creation of an AudioRecord Object
    private int frequency = 44100; //8000;
    private int channelConfiguration = AudioFormat.CHANNEL_IN_MONO;
    private int audioEncoding = AudioFormat.ENCODING_PCM_16BIT;
    private short threshold = 65; //TODO : TEST LOW THRESHOLD
    private int bufferSize;
    private int silenceBufferSize;
    private Boolean isRecording = false;
    public AudioRecord audioRecord;
    //Second AudioRecord object with smaller buffer to check if input in device is silent
    //public AudioRecord silenceCheck;
    AudioModule context = this;

    public AudioModule(ReactApplicationContext context) {
        super(context);
    }

    @NonNull
    @Override
    public String getName() {
        return "AudioModule";
    }

    @SuppressLint("MissingPermission")
    @ReactMethod
    public void startRecording() throws IOException {
        bufferSize = AudioRecord.getMinBufferSize(frequency, channelConfiguration, audioEncoding)
                * 40; //TODO BUFFERSIZE = < 2 seconds
        //silenceBufferSize = AudioRecord.getMinBufferSize(frequency, channelConfiguration, audioEncoding);

        //Permission check handled from React Native code
        audioRecord = new AudioRecord(MediaRecorder.AudioSource.MIC, frequency, channelConfiguration, audioEncoding, bufferSize);
        //silenceCheck = new AudioRecord(MediaRecorder.AudioSource.MIC, frequency, channelConfiguration, audioEncoding, silenceBufferSize);
        audioRecord.startRecording();
        //silenceCheck.startRecording();
        isRecording = true;

        new Thread(() -> {
            try {
                detectSilence();
            } catch (IOException e) {
                throw new RuntimeException(e);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }).start();
    }

    // Function to determine if a buffer contains mostly silent sound
    public boolean readIfBufferIsSilent(short[] buffer, int bufferReadResult){
        boolean isBufferSilent;
        // Calculate average amplitude
        int sum = 0;
        for (int i = 0; i < bufferReadResult; i++) {
            sum += Math.abs(buffer[i]);
        }
        double averageAmplitude = sum / (double) bufferReadResult;
        // Check if the average amplitude is below the threshold
        isBufferSilent = averageAmplitude < threshold;

        Log.i("AudioModule", "Threshold level: " + averageAmplitude);

        return isBufferSilent;
    }

    public void detectSilence() throws IOException, InterruptedException {
        Log.d("AudioModule", "Calling detectSilence method...");
        short[] buffer = new short[bufferSize];
        short[] silentBuffer = new short[silenceBufferSize];

        while (isRecording) {
            //Check for silence
            //int silentBufferReadResult = silenceCheck.read(silentBuffer,0,silenceBufferSize);

            // Read audio from microphone
            int bufferReadResult = audioRecord.read(buffer, 0, bufferSize);
            if (AudioRecord.ERROR_INVALID_OPERATION != bufferReadResult) {
                Boolean isSilent = readIfBufferIsSilent(buffer, bufferReadResult);
                //If not silent, write buffer
                if (!isSilent){
//                    new Thread(() -> {
                        try {
                            //Write file and emit filename as an event
                            Log.i("AudioModule", "BufferReadResult is : " + bufferReadResult);
                            String filename = writeToFile(buffer, bufferReadResult);
                            context.getReactApplicationContext()
                                    .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
                                    .emit("AudioModule", filename);
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
//                    }).start();
                } else {
                    Log.i("AudioModule", "Silence detected");
                }
            }
        }
    }

    String writeToFile(short[] buffer, int bufferReadResult) throws IOException {

        File outputFile = new File(context.getReactApplicationContext().getFilesDir(), "audio_output.pcm");
        FileOutputStream os = new FileOutputStream(outputFile);
        byte[] byteBuffer = shortToByte(buffer,bufferReadResult);
        try {
            os.write(byteBuffer);
            os.close();
            Log.d("AudioModule", "Filepath of recording is: " + outputFile.getAbsolutePath());
            return outputFile.getAbsolutePath();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            return null;
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    byte[] shortToByte(short [] input, int elements) {
        int short_index, byte_index;
        int iterations = elements; //input.length;
        byte [] buffer = new byte[iterations * 2];

        short_index = byte_index = 0;

        for(/*NOP*/; short_index != iterations; /*NOP*/) {
            buffer[byte_index]     = (byte) (input[short_index] & 0x00FF);
            buffer[byte_index + 1] = (byte) ((input[short_index] & 0xFF00) >> 8);

            ++short_index;
            byte_index += 2;
        }
        return buffer;
    }

    @ReactMethod
    public void stopRecording() {
        audioRecord.stop();
        //silenceCheck.stop();
        audioRecord.release();
        //silenceCheck.release();
        isRecording = false;
    }
}
