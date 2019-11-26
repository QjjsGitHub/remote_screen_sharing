package com.qjj.screenshare.server;

import android.hardware.display.DisplayManager;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.projection.MediaProjection;
import android.view.Surface;

import com.qjj.screenshare.entity.MessageEvent;
import com.qjj.screenshare.MyApplication;
import com.qjj.screenshare.entity.VideoPack;

import org.greenrobot.eventbus.EventBus;

import java.nio.ByteBuffer;

import static com.qjj.screenshare.MyApplication.CODEC_CREATE_ERROE;
import static com.qjj.screenshare.MyApplication.CODEC_ERROR;
import static com.qjj.screenshare.MyApplication.CODEC_SERVER_RUN;
import static com.qjj.screenshare.MyApplication.MEDIA_PROJECTION_IS_NULL;

/**
 * 创建日期：2019/11/21 11:46
 *
 * @author 曲建金
 * 说明：
 */
public class MyEncoder extends Thread {

    private MediaCodec codec;
    private int videoW;
    private int videoH;
    private int videoBitrate;
    private int videoFrameRate;
    private Surface mSurface;

    private final int TIMEOUT_USEC = 12000;
    private byte[] configbyte;

    private boolean exit = false;

    private static final String TAG = "Encoder";
    private static final String MIME = "Video/AVC";

    private SocketServerThread socketServerThread;

    public MyEncoder(SocketServerThread socketServerThread) {
        this.videoW = MyApplication.width;
        this.videoH = MyApplication.height;
        this.videoBitrate = MyApplication.videoBitrate;
        this.videoFrameRate = MyApplication.videoFrameRate;
        this.socketServerThread = socketServerThread;
    }

    public boolean init() {
        if (initMediaCodec()) {
            return createVirtualDisplay();
        }
        return false;
    }

    private boolean createVirtualDisplay() {
        MediaProjection mediaProjection = MyApplication.getMediaProjection();
        if (mediaProjection != null) {
            mediaProjection.createVirtualDisplay("display",
                    videoW, videoH, 1,
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC,
                    mSurface, null, null);
        } else {
            EventBus.getDefault().post(new MessageEvent(MEDIA_PROJECTION_IS_NULL));
            return false;
        }
        return true;
    }

    private boolean initMediaCodec() {
        try {
            MediaFormat format = MediaFormat.createVideoFormat(MIME, videoW, videoH);
            //颜色格式
            format.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
            //码流
            format.setInteger(MediaFormat.KEY_BIT_RATE, videoBitrate);
            //帧数
            format.setInteger(MediaFormat.KEY_FRAME_RATE, videoFrameRate);
            // 关键帧 5秒
            format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, -1);

            codec = MediaCodec.createEncoderByType(MIME);
            codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            mSurface = codec.createInputSurface();
            codec.start();
        } catch (Exception e) {
            EventBus.getDefault().post(new MessageEvent(CODEC_CREATE_ERROE));
            return false;
        }
        return true;
    }

    /**
     * 获取h264数据
     **/
    @Override
    public void run() {
        try {
            //发送启动通知
            EventBus.getDefault().post(new MessageEvent(CODEC_SERVER_RUN));

            while (!exit) {
                if (codec == null) {
                    exit = true;
                    break;
                }

                MediaCodec.BufferInfo mBufferInfo = new MediaCodec.BufferInfo();
                int outputBufferIndex = codec.dequeueOutputBuffer(mBufferInfo, TIMEOUT_USEC);

                ByteBuffer outputBuffer;
                byte[] outData;

                while (outputBufferIndex >= 0) {
                    outputBuffer = codec.getOutputBuffer(outputBufferIndex);
                    outData = new byte[mBufferInfo.size];
                    if (outputBuffer == null) {
                        continue;
                    }
                    outputBuffer.get(outData);
                    if (mBufferInfo.flags == 2) {
                        configbyte = new byte[mBufferInfo.size];
                        configbyte = outData;
                    } else if (mBufferInfo.flags == 1) {
                        byte[] keyframe = new byte[mBufferInfo.size + configbyte.length];
                        System.arraycopy(configbyte, 0, keyframe, 0, configbyte.length);
                        System.arraycopy(outData, 0, keyframe, configbyte.length, outData.length);
                        onH264(keyframe, 1, mBufferInfo.presentationTimeUs);
                    } else {
                        //其他帧末
                        onH264(outData, 2, mBufferInfo.presentationTimeUs);
                    }
                    codec.releaseOutputBuffer(outputBufferIndex, false);
                    outputBufferIndex = codec.dequeueOutputBuffer(mBufferInfo, TIMEOUT_USEC);
                }
            }
        } catch (Exception e) {
            if (!exit) {
                exit = true;
                EventBus.getDefault().post(new MessageEvent(CODEC_ERROR));
            }
        }
        close();
    }

    public void close() {
        exit = true;
        try {
            codec.stop();
            codec.release();
        } catch (Exception e) {
            exit = true;
        }
        codec = null;
    }

    private void onH264(byte[] buffer, int type, long ts) {
        byte[] bytes = new byte[buffer.length];
        System.arraycopy(buffer, 0, bytes, 0, buffer.length);

        VideoPack pack = new VideoPack(bytes, type, ts);
        if (socketServerThread != null) {
            socketServerThread.putVideoPack(pack);
        }
    }
}
