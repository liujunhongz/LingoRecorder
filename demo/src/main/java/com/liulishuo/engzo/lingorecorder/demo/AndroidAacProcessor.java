package com.liulishuo.engzo.lingorecorder.demo;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.os.Build;
import android.support.annotation.RequiresApi;
import android.util.Log;

import com.liulishuo.engzo.lingorecorder.processor.AudioProcessor;
import com.liulishuo.engzo.lingorecorder.utils.LOG;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * Created by wcw on 3/30/17.
 */
@RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN)
public class AndroidAacProcessor implements AudioProcessor {

    private String filePath;
    private MediaCodec codec;
    private MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();

    private FileOutputStream fos;

    public AndroidAacProcessor() {
    }

    public AndroidAacProcessor(String filePath) {
        this.filePath = filePath;
    }

    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }

    @Override
    public void start() throws IOException {
        try {
            fos = new FileOutputStream(filePath);
        } catch (Exception e) {
            e.printStackTrace();
        }

        String mime = "audio/mp4a-latm";
        String mediaType = "OMX.google.aac.encoder";
        MediaFormat format = new MediaFormat();
        int minBufferSize = 2 * AudioRecord.getMinBufferSize(44100, AudioFormat.CHANNEL_IN_STEREO, AudioFormat.ENCODING_PCM_16BIT);
        //数据类型
        format.setString(MediaFormat.KEY_MIME, mime);
        format.setInteger(MediaFormat.KEY_CHANNEL_COUNT, 2);
        format.setInteger(MediaFormat.KEY_SAMPLE_RATE, 44100);
        format.setInteger(MediaFormat.KEY_BIT_RATE, 32 * 1024);
        format.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC);
        format.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, minBufferSize);
        codec = MediaCodec.createEncoderByType(mime);
//        codec = MediaCodec.createByCodecName(mediaType);
        codec.configure(
                format,
                null /* surface */,
                null /* crypto */,
                MediaCodec.CONFIGURE_FLAG_ENCODE);
        codec.start();
    }

    @Override
    public void flow(byte[] bytes, int size) {
        withAdts(bytes);
    }

    private void withoutAdts(byte[] bytes, int size) {
        try {
            int inputBufferIndex = codec.dequeueInputBuffer(12000);
            if (inputBufferIndex >= 0) {
                ByteBuffer[] inputBuffers = codec.getInputBuffers();
                ByteBuffer inputBuffer = inputBuffers[inputBufferIndex];
                inputBuffer.clear();
                inputBuffer.put(bytes);
                codec.queueInputBuffer(inputBufferIndex, 0, size, 0, 0);
            }

            MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
            int outputBufferIndex = codec.dequeueOutputBuffer(bufferInfo, 12000);

            while (outputBufferIndex >= 0) {
                ByteBuffer[] outputBuffers = codec.getOutputBuffers();
                ByteBuffer outputBuffer = outputBuffers[outputBufferIndex];
                byte[] outData = new byte[bufferInfo.size];
                outputBuffer.get(outData);


                fos.write(outData, 0, outData.length);
                LOG.d("FlacEncoder " + outData.length + " bytes written");

                codec.releaseOutputBuffer(outputBufferIndex, false);
                outputBufferIndex = codec.dequeueOutputBuffer(bufferInfo, 0);

            }
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

    // called AudioRecord's read
    public synchronized void withAdts(byte[] input) {
        Log.d("AudioEncoder", input.length + " is coming");
        int inputBufferIndex = codec.dequeueInputBuffer(12000);
        if (inputBufferIndex >= 0) {
            ByteBuffer[] inputBuffers = codec.getInputBuffers();
            ByteBuffer inputBuffer = inputBuffers[inputBufferIndex];
            inputBuffer.clear();
            inputBuffer.put(input);
            codec.queueInputBuffer(inputBufferIndex, 0, input.length, System.nanoTime(), 0);
        }


        int outputBufferIndex = codec.dequeueOutputBuffer(bufferInfo, 12000);

        //trying to add a ADTS
        while (outputBufferIndex >= 0) {
            int outBitsSize = bufferInfo.size;
            int outPacketSize = outBitsSize + 7; // 7 is ADTS size
            ByteBuffer[] outputBuffers = codec.getOutputBuffers();
            ByteBuffer outputBuffer = outputBuffers[outputBufferIndex];


            outputBuffer.position(bufferInfo.offset);
            outputBuffer.limit(bufferInfo.offset + outBitsSize);


            byte[] outData = new byte[outPacketSize];
            addADTStoPacket(outData, outPacketSize);


            outputBuffer.get(outData, 7, outBitsSize);
            outputBuffer.position(bufferInfo.offset);


            // byte[] outData = new byte[bufferInfo.size];
            try {
                fos.write(outData, 0, outData.length);
            } catch (IOException e) {
                e.printStackTrace();
            }
            Log.d("AudioEncoder", outData.length + " bytes written");

            codec.releaseOutputBuffer(outputBufferIndex, false);
            outputBufferIndex = codec.dequeueOutputBuffer(bufferInfo, 0);


        }
        Log.d("AudioEncoder", input.length + " is read");
    }

    /**
     * Add ADTS header at the beginning of each and every AAC packet. This is
     * needed as MediaCodec encoder generates a packet of raw AAC data.
     * <p>
     * Note the packetLen must count in the ADTS header itself.
     **/
    public void addADTStoPacket(byte[] packet, int packetLen) {
        int profile = 2; // AAC LC
        // 39=MediaCodecInfo.CodecProfileLevel.AACObjectELD;
        int freqIdx = 4; // 44.1KHz
        int chanCfg = 2; // CPE

        // fill in ADTS data
        packet[0] = (byte) 0xFF;
        packet[1] = (byte) 0xF9;
        packet[2] = (byte) (((profile - 1) << 6) + (freqIdx << 2) + (chanCfg >> 2));
        packet[3] = (byte) (((chanCfg & 3) << 6) + (packetLen >> 11));
        packet[4] = (byte) ((packetLen & 0x7FF) >> 3);
        packet[5] = (byte) (((packetLen & 7) << 5) + 0x1F);
        packet[6] = (byte) 0xFC;
    }

    @Override
    public boolean needExit() {
        return false;
    }

    @Override
    public void end() {
        try {
            if (codec != null) {
                codec.stop();
                codec.release();
                codec = null;
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    public String getFilePath() {
        return filePath;
    }

    @Override
    public void release() {

        try {
            if (fos != null) {
                fos.flush();
                fos.close();
                fos = null;
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}
