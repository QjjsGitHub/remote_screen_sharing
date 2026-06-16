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
import java.io.OutputStream;
import java.nio.ByteBuffer;

import static com.qjj.screenshare.MyApplication.CODEC_CREATE_ERROE;
import static com.qjj.screenshare.MyApplication.CODEC_ERROR;
import static com.qjj.screenshare.MyApplication.CODEC_SERVER_RUN;
import static com.qjj.screenshare.MyApplication.MEDIA_PROJECTION_IS_NULL;
import static com.qjj.screenshare.MyApplication.TYPE1;
import static com.qjj.screenshare.MyApplication.TYPE2;

/**
 * 屏幕编码线程类
 * 负责获取 MediaProjection 内容，通过 MediaCodec 编码为 H.264，并直接写入网络输出流
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
    private final DataOutputStream dos; // 封装后的网络输出流

    public MyEncoder(OutputStream os) {
        this.videoW = MyApplication.width;
        this.videoH = MyApplication.height;
        this.videoFrameRate = MyApplication.videoFrameRate;
        this.dos = new DataOutputStream(os);
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
            // 颜色格式：使用 Surface 作为输入
            format.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
            // 码率设置
            format.setInteger(MediaFormat.KEY_BIT_RATE, videoW * videoH * 2);
            // 帧率
            format.setInteger(MediaFormat.KEY_FRAME_RATE, videoFrameRate);
            // 关键帧间隔：1秒
            format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1);
            // 低延迟配置
            format.setInteger(MediaFormat.KEY_LATENCY, 1);
            format.setInteger(MediaFormat.KEY_BITRATE_MODE, MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_VBR);

            codec = MediaCodec.createEncoderByType(MIME);
            codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            mSurface = codec.createInputSurface(); // 创建输入 Surface
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
                // 取出编码后的数据索引
                int outputBufferIndex = codec.dequeueOutputBuffer(mBufferInfo, TIMEOUT_USEC);

                while (outputBufferIndex >= 0) {
                    ByteBuffer outputBuffer = codec.getOutputBuffer(outputBufferIndex);
                    if (outputBuffer != null) {
                        byte[] outData = new byte[mBufferInfo.size];
                        outputBuffer.get(outData);

                        if (mBufferInfo.flags == MediaCodec.BUFFER_FLAG_CODEC_CONFIG) {
                            // 保存 SPS/PPS 参数，后续拼接到关键帧前
                            configbyte = outData;
                        } else if (mBufferInfo.flags == MediaCodec.BUFFER_FLAG_KEY_FRAME) {
                            // 关键帧：拼接参数后再发送，确保接收端可随时解码
                            byte[] keyframe = new byte[mBufferInfo.size + (configbyte != null ? configbyte.length : 0)];
                            if (configbyte != null) {
                                System.arraycopy(configbyte, 0, keyframe, 0, configbyte.length);
                                System.arraycopy(outData, 0, keyframe, configbyte.length, outData.length);
                            } else {
                                System.arraycopy(outData, 0, keyframe, 0, outData.length);
                            }
                            onH264(keyframe, 1, mBufferInfo.presentationTimeUs);
                        } else {
                            // 普通帧
                            onH264(outData, 2, mBufferInfo.presentationTimeUs);
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
     * 数据协议封装并发送
     * 格式：[4字节CRC][4字节长度][8字节时间戳][1字节类型][有效负载数据]
     */
    private void onH264(byte[] buffer, int type, long ts) {
        try {
            dos.writeInt(0); // CRC 占位，暂未实现
            dos.writeInt(buffer.length); // 有效数据长度
            dos.writeLong(ts); // 呈现时间戳
            dos.writeByte((type == 1) ? TYPE1 : TYPE2); // 帧类型标识
            dos.write(buffer); // 写入原始 H.264 字节
            dos.flush();
        } catch (IOException e) {
            exit = true; // 发送失败通常意味着连接已断开
        }
    }
}
