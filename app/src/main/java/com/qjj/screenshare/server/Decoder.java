package com.qjj.screenshare.server;

import android.media.MediaCodec;
import android.media.MediaFormat;
import android.view.Surface;

import com.qjj.screenshare.entity.MessageEvent;

import org.greenrobot.eventbus.EventBus;

import java.io.IOException;
import java.nio.ByteBuffer;

import static com.qjj.screenshare.MyApplication.CREATE_CODEC_EDCODER;
import static com.qjj.screenshare.MyApplication.ON_FRAME_FAIL;

/**
 * 视频解码器类
 * 负责将接收到的 H.264 原始数据解码并渲染到指定的 Surface 上
 */
public class Decoder {
    private MediaCodec mCodec;
    private Surface mSurface;
    private final static String MIME_TYPE = "video/avc"; // H.264 格式

    private final int videoWidth;
    private final int videoHeight;

    public Decoder(int width, int height, int fps, Surface surface) {
        this.videoWidth = width;
        this.videoHeight = height;
        this.mSurface = surface;
    }

    /**
     * 初始化硬解码器
     */
    public boolean initDecoder() {
        try {
            mCodec = MediaCodec.createDecoderByType(MIME_TYPE);
            MediaFormat mediaFormat = MediaFormat.createVideoFormat(MIME_TYPE, videoWidth, videoHeight);
            // 配置解码器，设置渲染 Surface
            mCodec.configure(mediaFormat, mSurface, null, 0);
            mCodec.start();
            return true;
        } catch (IOException e) {
            EventBus.getDefault().post(new MessageEvent(CREATE_CODEC_EDCODER));
            return false;
        }
    }

    /**
     * 处理单帧 H.264 数据
     * @param buf 数据缓冲区
     * @param offset 偏移量
     * @param length 数据长度
     * @param ts 呈现时间戳（微妙）
     */
    public void onFrame(byte[] buf, int offset, int length, long ts) {
        try {
            // 1. 获取输入缓冲区索引
            int inputBufferIndex = mCodec.dequeueInputBuffer(10000);
            if (inputBufferIndex >= 0) {
                ByteBuffer inputBuffer = mCodec.getInputBuffer(inputBufferIndex);
                if (inputBuffer != null) {
                    inputBuffer.clear();
                    inputBuffer.put(buf, offset, length);
                }
                // 将数据送入解码器
                mCodec.queueInputBuffer(inputBufferIndex, 0, length, ts, 0);
            }

            // 2. 获取输出缓冲区并渲染
            MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
            int outputBufferIndex = mCodec.dequeueOutputBuffer(bufferInfo, 100);
            while (outputBufferIndex >= 0) {
                // releaseOutputBuffer 的第二个参数为 true，表示将解码后的数据渲染到 Surface
                mCodec.releaseOutputBuffer(outputBufferIndex, true);
                outputBufferIndex = mCodec.dequeueOutputBuffer(bufferInfo, 0);
            }
        } catch (Throwable e) {
            EventBus.getDefault().post(new MessageEvent(ON_FRAME_FAIL));
        }
    }

    /**
     * 释放解码器资源
     */
    public void release() {
        mSurface = null;
        if (mCodec != null) {
            try {
                mCodec.stop();
                mCodec.release();
            } catch (Exception ignored) {}
            mCodec = null;
        }
    }
}
