package com.qjj.screenshare.server;

import android.media.MediaCodec;
import android.media.MediaFormat;
import android.util.EventLog;
import android.util.Log;
import android.view.Surface;

import com.qjj.screenshare.entity.MessageEvent;

import org.greenrobot.eventbus.EventBus;

import java.io.IOException;
import java.nio.ByteBuffer;

import static com.qjj.screenshare.MyApplication.CREATE_CODEC_EDCODER;
import static com.qjj.screenshare.MyApplication.ON_FRAME_FAIL;

public class Decoder {
    private MediaCodec mCodec;
    private Surface mSurface;
    /**
     * H.264 Advanced Video
     */
    private final static String MIME_TYPE = "video/avc";

    private int videoWidth;
    private int videoHeight;

    public Decoder(int width, int height, int fps, Surface surface) {
        this.videoWidth = width;
        this.videoHeight = height;
        this.mSurface = surface;
    }

    public boolean initDecoder() {

        try {
            mCodec = MediaCodec.createDecoderByType(MIME_TYPE);
        } catch (IOException e) {
            EventBus.getDefault().post(new MessageEvent(CREATE_CODEC_EDCODER));
            return false;
        }
        MediaFormat mediaFormat = MediaFormat.createVideoFormat(MIME_TYPE,
                videoWidth, videoHeight);
        mCodec.configure(mediaFormat, mSurface,
                null, 0);
        mCodec.start();
        return true;
    }

    public void onFrame(byte[] buf, int offset, int length, long ts) {
        try {
            int inputBufferIndex = mCodec.dequeueInputBuffer(10000);
            if (inputBufferIndex >= 0) {
                ByteBuffer inputBuffer = mCodec.getInputBuffer(inputBufferIndex);
                if (inputBuffer != null) {
                    inputBuffer.clear();
                    inputBuffer.put(buf, offset, length);
                }
                mCodec.queueInputBuffer(inputBufferIndex, 0, length, ts, 0);
            } else {
                return;
            }

            // Get output buffer index
            MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
            int outputBufferIndex = mCodec.dequeueOutputBuffer(bufferInfo, 100);
            while (outputBufferIndex >= 0) {
                mCodec.releaseOutputBuffer(outputBufferIndex, true);
                outputBufferIndex = mCodec.dequeueOutputBuffer(bufferInfo, 0);
            }
        } catch (Throwable e) {
            EventBus.getDefault().post(new MessageEvent(ON_FRAME_FAIL));
        }
    }

    public void release() {
        mSurface = null;
        if (mCodec != null) {
            mCodec.stop();
            mCodec.release();
            mCodec = null;
        }
    }
}
