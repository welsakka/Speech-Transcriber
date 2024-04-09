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
import java.nio.ByteBuffer;

/**
 * This Module manages the realtime streaming data from the device microphone and processes it into a readable file from a byte stream
 * The Module uses two buffers, a main buffer and a silent check buffer. The main buffer captures the realtime sound.
 * The silent check buffer is a small buffer that checks every .04 seconds if the incoming sound is silent.
 * This helps to determine when to make a Whisper API call, when there is a break in speech.
 */
public class AudioModule extends ReactContextBaseJavaModule {

    private static final long MAX_BUFFER_SIZE = 10000 ;
    /*
    Variables used for obtaining the minimum Buffer size required for the creation of an AudioRecord Object
     */
    private int frequency = 44100; //8000;
    private int channelConfiguration = AudioFormat.CHANNEL_IN_MONO;
    private int audioEncoding = AudioFormat.ENCODING_PCM_16BIT;
    private short threshold = 400; //Determines the threshold between silence and incoming sound
    private int bufferSize;
    private int silenceBufferSize;
    private Boolean isRecording = false;
    public AudioRecord audioRecord;
    public AudioRecord silenceCheck; //Second AudioRecord object with smaller buffer to check if input in device is silent
    private Boolean isIncomingAudioSilent = true;
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
        //Minimum buffer size is about .04 seconds
        bufferSize = AudioRecord.getMinBufferSize(frequency, channelConfiguration, audioEncoding)
                * 300; //TODO BUFFERSIZE = 12 seconds
        silenceBufferSize = AudioRecord.getMinBufferSize(frequency, channelConfiguration, audioEncoding);

        //Permission check handled from React Native code
        audioRecord = new AudioRecord(MediaRecorder.AudioSource.MIC, frequency, channelConfiguration, audioEncoding, bufferSize);
        silenceCheck = new AudioRecord(MediaRecorder.AudioSource.MIC, frequency, channelConfiguration, audioEncoding, silenceBufferSize);

        audioRecord.startRecording();
        silenceCheck.startRecording();
        isRecording = true;

        new Thread(() -> {
            try {
                readBufferResult();
            } catch (IOException e) {
                throw new RuntimeException(e);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }).start();
    }

    /*
    Function to determine if a buffer contains mostly silent sound.
    Calculates the average amplitude of the buffer to determine if silent
     */
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

    /*
    Checks data in buffer and determines when to process to file
     */
    public void readBufferResult() throws IOException, InterruptedException {
        Log.d("AudioModule", "Calling detectSilence method...");
        short[] silenceBuffer = new short[silenceBufferSize];

        while (isRecording) {
            //Initialize new main buffer every silence check
            byte[] buffer = new byte[bufferSize];
            ByteBuffer byteBuffer = ByteBuffer.wrap(buffer);

            //Check for when to begin tracking silence, in case main recording starts off silent
            while (isIncomingAudioSilent){
                int silenceBufferReadResult = silenceCheck.read(silenceBuffer,0,silenceBufferSize);
                boolean res = readIfBufferIsSilent(silenceBuffer, silenceBufferReadResult);
                if (res == false) {
                    Log.i("AudioModule", "Silence broken, beginning audio capture");
                    isIncomingAudioSilent = false;
                    break;
                }
            }

            // Realtime silence check on incoming audio
            int silenceBufferReadResult = silenceCheck.read(silenceBuffer,0,silenceBufferSize);
            boolean silent = readIfBufferIsSilent(silenceBuffer, silenceBufferReadResult);
            Log.i("AudioModule", String.valueOf(silent));

            // If silence detected, read main buffer
            if (silent){
                int bufferReadResult = audioRecord.read(byteBuffer, 0);
                    if (AudioRecord.ERROR_INVALID_OPERATION != bufferReadResult) {
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
                    }
            }
        }
    }

    /*
    Converts data in byte array to a file for device to send in API call
     */
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

    /*
    Utility function to convert short array to byte array
     */
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
        silenceCheck.stop();
        audioRecord.release();
        silenceCheck.release();
        isRecording = false;
    }
}
