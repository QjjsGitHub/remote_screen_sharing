package com.qjj.screenshare.server;

import android.hardware.display.DisplayManager;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.projection.MediaProjection;
import android.view.Surface;

import com.qjj.screenshare.entity.MessageEvent;
import com.qjj.screenshare.MyApplication;

import org.greenrobot.eventbus.EventBus;

import java.io.DataOutputStream;
import java.io.IOException;
import java.util.List;
import java.nio.ByteBuffer;

import static com.qjj.screenshare.MyApplication.CODEC_CREATE_ERROE;
import static com.qjj.screenshare.MyApplication.CODEC_ERROR;
import static com.qjj.screenshare.MyApplication.CODEC_SERVER_RUN;
import static com.qjj.screenshare.MyApplication.MEDIA_PROJECTION_IS_NULL;
import static com.qjj.screenshare.MyApplication.TYPE1;
import static com.qjj.screenshare.MyApplication.TYPE2;

/**
 * 屏幕编码线程类
 * 负责获取 MediaProjection 内容，通过 MediaCodec 编码为 H.264，并广播给所有已连接的客户端
 */
public class MyEncoder extends Thread {

    private MediaCodec codec;
    private final int videoW;
    private final int videoH;
    private final int videoFrameRate;
    private Surface mSurface;

    private final int TIMEOUT_USEC = 12000;
    private byte[] configbyte; // 存储 SPS/PPS 数据

    private boolean exit = false;

    private static final String MIME = "Video/AVC"; // H.264 编码格式
    private final List<DataOutputStream> clientStreams; // 客户端流列表（用于广播）

    public MyEncoder(List<DataOutputStream> clientStreams) {
        this.videoW = MyApplication.width;
        this.videoH = MyApplication.height;
        this.videoFrameRate = MyApplication.videoFrameRate;
        this.clientStreams = clientStreams;
    }

    /**
     * 初始化编码器和虚拟显示
     */
    public boolean init() {
        if (initMediaCodec()) {
            return createVirtualDisplay();
        }
        return false;
    }

    /**
     * 创建虚拟显示，将屏幕内容投射到 MediaCodec 的 Surface 上
     */
    private boolean createVirtualDisplay() {
        MediaProjection mediaProjection = MyApplication.getMediaProjection();
        if (mediaProjection != null) {
            // Android 14+ 要求在创建虚拟显示前注册回调
            mediaProjection.registerCallback(new MediaProjection.Callback() {
                @Override
                public void onStop() {
                    super.onStop();
                }
            }, null);
            
            mediaProjection.createVirtualDisplay("-display",
                    videoW, videoH, MyApplication.screenDpi,
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                    mSurface, null, null);
        } else {
            EventBus.getDefault().post(new MessageEvent(MEDIA_PROJECTION_IS_NULL));
            return false;
        }
        return true;
    }

    /**
     * 初始化硬编码器 MediaCodec
     */
    private boolean initMediaCodec() {
        try {
            MediaFormat format = MediaFormat.createVideoFormat(MIME, videoW, videoH);
            format.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
            format.setInteger(MediaFormat.KEY_BIT_RATE, videoW * videoH * 2);
            format.setInteger(MediaFormat.KEY_FRAME_RATE, videoFrameRate);
            format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1);
            format.setInteger(MediaFormat.KEY_LATENCY, 1);
            format.setInteger(MediaFormat.KEY_BITRATE_MODE, MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_VBR);

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

    @Override
    public void run() {
        try {
            EventBus.getDefault().post(new MessageEvent(CODEC_SERVER_RUN));

            while (!exit) {
                if (codec == null) {
                    exit = true;
                    break;
                }

                MediaCodec.BufferInfo mBufferInfo = new MediaCodec.BufferInfo();
                int outputBufferIndex = codec.dequeueOutputBuffer(mBufferInfo, TIMEOUT_USEC);

                while (outputBufferIndex >= 0) {
                    ByteBuffer outputBuffer = codec.getOutputBuffer(outputBufferIndex);
                    if (outputBuffer != null) {
                        byte[] outData = new byte[mBufferInfo.size];
                        outputBuffer.get(outData);

                        if (mBufferInfo.flags == MediaCodec.BUFFER_FLAG_CODEC_CONFIG) {
                            configbyte = outData;
                        } else if (mBufferInfo.flags == MediaCodec.BUFFER_FLAG_KEY_FRAME) {
                            byte[] keyframe = new byte[mBufferInfo.size + (configbyte != null ? configbyte.length : 0)];
                            if (configbyte != null) {
                                System.arraycopy(configbyte, 0, keyframe, 0, configbyte.length);
                                System.arraycopy(outData, 0, keyframe, configbyte.length, outData.length);
                            } else {
                                System.arraycopy(outData, 0, keyframe, 0, outData.length);
                            }
                            broadcastH264(keyframe, 1, mBufferInfo.presentationTimeUs);
                        } else {
                            broadcastH264(outData, 2, mBufferInfo.presentationTimeUs);
                        }
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

    /**
     * 资源释放
     */
    public void close() {
        exit = true;
        try {
            if (codec != null) {
                codec.stop();
                codec.release();
            }
        } catch (Exception ignored) {}
        codec = null;
    }

    /**
     * 广播 H264 数据给所有客户端
     */
    private void broadcastH264(byte[] buffer, int type, long ts) {
        for (DataOutputStream dos : clientStreams) {
            try {
                dos.writeInt(0); // CRC 占位
                dos.writeInt(buffer.length);
                dos.writeLong(ts);
                dos.writeByte((type == 1) ? TYPE1 : TYPE2);
                dos.write(buffer);
                dos.flush();
            } catch (IOException e) {
                // 发送失败的客户端由 SocketServerThread 负责清理
            }
        }
    }
}
