package com.codeart.microspeaker;

import android.Manifest;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import com.codeart.microspeaker.databinding.ActivityMainBinding;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {

    private ActivityMainBinding binding;

    /*
     * FOR SPEAKER
     */
    private MediaPlayer mediaPlayer;

    /*
     * FOR RECORDER
     */
    private AudioRecord recorder = null;
    private boolean isRecording = false;
    private int bufferSize = 0;
    private Thread recordingThread = null;

    private static final int RECORDER_SAMPLE_RATE = 44100;

    // FOR STOP RECORDING
    private static final String AUDIO_RECORDER_FILE_EXT_WAV = ".wav";
    private static final int RECORDER_BPP = 16;

    // FOR START RECORDING
    private static final String AUDIO_RECORDER_FOLDER = "RecordTester";
    private static final String AUDIO_NAME_BEFORE_RECORDER = "record_temp.wav";
    private static final String AUDIO_NAME_AFTER_RECORDER = "/record_fix";
    private static final int RECORDER_CHANNELS = AudioFormat.CHANNEL_IN_STEREO;
    private static final int RECORDER_AUDIO_ENCODING = AudioFormat.ENCODING_PCM_16BIT;

    // FOR POP UP REQUEST PERMISSION USER
    private static final int REQUEST_CODE = 0;
    private final String[] permissions = {
            Manifest.permission.RECORD_AUDIO
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        View view = binding.getRoot();
        setContentView(view);

        // FOR POP UP REQUEST PERMISSION USER
        ActivityCompat.requestPermissions(this, permissions, REQUEST_CODE);

        mediaPlayer = new MediaPlayer();

        bufferSize = AudioRecord.getMinBufferSize(
                RECORDER_SAMPLE_RATE,
                AudioFormat.CHANNEL_OUT_STEREO,
                AudioFormat.ENCODING_PCM_16BIT
        );

        File file = new File(getFileName());

        if (file.exists()) {
            binding.btnReset.setEnabled(true);
        }

        binding.btnPlay.setOnClickListener(this);
        binding.btnReset.setOnClickListener(this);
        binding.btnRecord.setOnClickListener(this);
        binding.btnSave.setOnClickListener(this);
        binding.imgRefresh.setOnClickListener(this);
    }

    @Override
    public void onClick(View view) {
        if (view.getId() == R.id.btn_play) {
            // play audio recorder
            playAudio();
            // auto hide lottie speaker in 3.2 second
            Handler handler = new Handler(getMainLooper());
            handler.postDelayed(() -> binding.lottieSpeaker.setVisibility(View.GONE), 3200);
        } else if (view.getId() == R.id.btn_reset) {
            // visibility
            binding.textInfo.setText(R.string.deleted);
            binding.textInfo.setVisibility(View.VISIBLE);
            // disable btn reset
            binding.btnReset.setEnabled(false);
            // delete audio recorder
            resetAudio();
        } else if (view.getId() == R.id.btn_record) {
            // visibility
            binding.btnSave.setVisibility(View.VISIBLE);
            binding.lottieMicrophone.setVisibility(View.VISIBLE);
            binding.btnRecord.setVisibility(View.GONE);
            // start recording
            startRecording();
        } else if (view.getId() == R.id.btn_save) {
            // visibility
            binding.btnRecord.setVisibility(View.VISIBLE);
            binding.btnSave.setVisibility(View.GONE);
            binding.lottieMicrophone.setVisibility(View.GONE);
            // disable btn reset
            binding.btnReset.setEnabled(true);
            // save record
            stopRecording();
            Toast.makeText(this, R.string.saving, Toast.LENGTH_SHORT).show();
        } else if (view.getId() == R.id.img_refresh) {
            // visibility
            binding.lottieMicrophone.setVisibility(View.GONE);
            binding.lottieSpeaker.setVisibility(View.GONE);
            binding.textInfo.setVisibility(View.INVISIBLE);
            Toast.makeText(this, R.string.refreshing, Toast.LENGTH_SHORT).show();
        }
    }


    /*
     * FOR GET PATH AUDIO RECORDER ------------------------------------------------------------------
     */
    private String getFileName() {
        String filepath = getBaseContext().getExternalCacheDir().getPath();
        File file = new File(filepath, AUDIO_RECORDER_FOLDER);

        if (!file.exists()) {
            //noinspection ResultOfMethodCallIgnored
            file.mkdirs();
        }

        return (file.getAbsolutePath() + AUDIO_NAME_AFTER_RECORDER + AUDIO_RECORDER_FILE_EXT_WAV);
    }

    /*
     * FOR PLAY AUDIO ------------------------------------------------------------------------------
     */
    private void playAudio() {
        File file = new File(getFileName());

        Uri myUri = Uri.parse(file.getAbsolutePath());

        try {
            if (!file.exists()) {
                binding.textInfo.setVisibility(View.VISIBLE);
                binding.textInfo.setText(R.string.not_found);
            } else {
                // visible lottie speaker
                binding.lottieSpeaker.setVisibility(View.VISIBLE);
                binding.textInfo.setVisibility(View.INVISIBLE);

                mediaPlayer.reset();
                mediaPlayer.setDataSource(getApplicationContext(), myUri);
                mediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
                mediaPlayer.prepare();
                mediaPlayer.start();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    /*
     * FOR DELETE AUDIO ----------------------------------------------------------------------------
     */
    private void resetAudio() {
        File file = new File(getFileName());

        if (!file.exists()) {
            binding.textInfo.setText(R.string.not_found);
        } else {
            //noinspection ResultOfMethodCallIgnored
            file.delete();
        }
    }

    /*
     * FOR START RECORDING -------------------------------------------------------------------------
     */
    private void startRecording() {
        recorder = new AudioRecord(
                MediaRecorder.AudioSource.MIC,
                RECORDER_SAMPLE_RATE,
                RECORDER_CHANNELS,
                RECORDER_AUDIO_ENCODING,
                bufferSize
        );

        int i = recorder.getState();
        if (i == 1)
            recorder.startRecording();

        isRecording = true;
        recordingThread = new Thread(this::writeAudioDataToFile, "AudioRecorder Thread");
        recordingThread.start();
    }

    private void writeAudioDataToFile() {
        byte[] data = new byte[bufferSize];
        String filename = getTempFilename();
        FileOutputStream os = null;

        try {
            os = new FileOutputStream(filename);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }

        int read;
        int counting = 0;

        if (null != os) {
            while (isRecording) {
                read = recorder.read(data, 0, bufferSize);
                counting += data.length;
                if (AudioRecord.ERROR_INVALID_OPERATION != read) {
                    try {
                        if (counting <= 500000) {
                            os.write(data);
                        } else {
                            isRecording = false;
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }

            try {
                os.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private String getTempFilename() {
        String filepath = getBaseContext().getExternalCacheDir().getPath();
        File file = new File(filepath, AUDIO_RECORDER_FOLDER);

        if (!file.exists()) {
            //noinspection ResultOfMethodCallIgnored
            file.mkdirs();
        }

        File tempFile = new File(filepath, AUDIO_NAME_BEFORE_RECORDER);

        if (tempFile.exists()) {
            //noinspection ResultOfMethodCallIgnored
            tempFile.delete();
        }

        return (file.getAbsolutePath() + "/" + AUDIO_NAME_BEFORE_RECORDER);
    }


    /*
     * FOR STOP RECORDING --------------------------------------------------------------------------
     */
    private void stopRecording() {
        if (null != recorder) {
            isRecording = false;

            int i = recorder.getState();
            if (i == 1) recorder.stop();

            recorder.release();

            recorder = null;
            recordingThread = null;
        }

        copyWaveFile(getTempFilename(), getFileName());
        deleteTempFile();
    }


    private void copyWaveFile(String inFilename, String outFilename) {
        FileInputStream in;
        FileOutputStream out;
        long totalAudioLen;
        long totalDataLen;
        int channels = 2;
        long byteRate = RECORDER_BPP * RECORDER_SAMPLE_RATE * channels / 8;

        byte[] data = new byte[bufferSize];

        try {
            in = new FileInputStream(inFilename);
            out = new FileOutputStream(outFilename);
            totalAudioLen = in.getChannel().size();
            totalDataLen = totalAudioLen + 36;

            Log.i("File Size audio wav", "File size: " + totalDataLen);

            WriteWaveFileHeader(
                    out,
                    totalAudioLen,
                    totalDataLen,
                    channels,
                    byteRate
            );

            while (in.read(data) != -1) {
                out.write(data);
            }

            in.close();
            out.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void deleteTempFile() {
        File file = new File(getTempFilename());
        //noinspection ResultOfMethodCallIgnored
        file.delete();
    }

    private void WriteWaveFileHeader(
            FileOutputStream out, long totalAudioLen,
            long totalDataLen, int channels,
            long byteRate) throws IOException {

        byte[] header = new byte[44];

        header[0] = 'R'; // RIFF/WAVE header
        header[1] = 'I';
        header[2] = 'F';
        header[3] = 'F';
        header[4] = (byte) (totalDataLen & 0xff);
        header[5] = (byte) ((totalDataLen >> 8) & 0xff);
        header[6] = (byte) ((totalDataLen >> 16) & 0xff);
        header[7] = (byte) ((totalDataLen >> 24) & 0xff);
        header[8] = 'W';
        header[9] = 'A';
        header[10] = 'V';
        header[11] = 'E';
        header[12] = 'f'; // 'fmt ' chunk
        header[13] = 'm';
        header[14] = 't';
        header[15] = ' ';
        header[16] = 16; // 4 bytes: size of 'fmt ' chunk
        header[17] = 0;
        header[18] = 0;
        header[19] = 0;
        header[20] = 1; // format = 1
        header[21] = 0;
        header[22] = (byte) channels;
        header[23] = 0;
        header[24] = (byte) ((long) RECORDER_SAMPLE_RATE & 0xff);
        header[25] = (byte) (((long) RECORDER_SAMPLE_RATE >> 8) & 0xff);
        header[26] = (byte) (((long) RECORDER_SAMPLE_RATE >> 16) & 0xff);
        header[27] = (byte) (((long) RECORDER_SAMPLE_RATE >> 24) & 0xff);
        header[28] = (byte) (byteRate & 0xff);
        header[29] = (byte) ((byteRate >> 8) & 0xff);
        header[30] = (byte) ((byteRate >> 16) & 0xff);
        header[31] = (byte) ((byteRate >> 24) & 0xff);
        header[32] = (byte) (2 * 16 / 8); // block align
        header[33] = 0;
        header[34] = RECORDER_BPP; // bits per sample
        header[35] = 0;
        header[36] = 'd';
        header[37] = 'a';
        header[38] = 't';
        header[39] = 'a';
        header[40] = (byte) (totalAudioLen & 0xff);
        header[41] = (byte) ((totalAudioLen >> 8) & 0xff);
        header[42] = (byte) ((totalAudioLen >> 16) & 0xff);
        header[43] = (byte) ((totalAudioLen >> 24) & 0xff);

        out.write(header, 0, 44);
    }
}