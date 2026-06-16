package com.qjj.screenshare;

import android.app.Application;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.view.Surface;

/**
 * 全局 Application 类，用于存储全局共享的变量和状态
 */
public class MyApplication extends Application {
    public static MediaProjection mediaProjection;
    public static MediaProjectionManager mediaProjectionManager;

    static Surface surface;

    /* 消息事件 ID 定义 */
    public final static int OPEN_FLOAT_WINDOW = 1;          // 打开悬浮窗
    public final static int IP_IS_NULL = 2;                 // IP 为空
    public final static int MEDIA_PROJECTION_IS_NULL = 3;   // MediaProjection 为空
    public final static int CODEC_SERVER_RUN = 4;           // 编码服务端启动
    public final static int CLIENT_CONNECT = 5;             // 客户端连接成功
    public final static int REQUEST_MEDIA_PROJECTION_FAIL = 6; // 请求截屏权限失败
    public final static int SEND_DATA_ERROR = 7;            // 发送数据错误
    public final static int CLIENT_SOCKET_CLOSE_ERROR = 8;  // 客户端 Socket 关闭错误
    public final static int SERVER_SOCKET_CLOSE_ERROR = 9;  // 服务端 Socket 关闭错误
    public final static int SOCKET_CLOSE = 10;              // Socket 关闭
    public final static int SERVER_SOCKET_CLOSE = 11;       // 服务端 Socket 关闭
    public final static int SERVER_SOCKET_CREATE_ERROR = 12;// 服务端 Socket 创建失败
    public final static int CODEC_CREATE_ERROE = 13;        // 编码器创建错误
    public final static int CODEC_ERROR = 14;               // 编码错误
    public final static int WAIT_CONNECT = 15;              // 等待连接
    public final static int CONNECT_SERVER_ERROR = 16;      // 连接服务端失败
    public final static int CONNECT_SERVER = 17;            // 已连接到服务端
    public final static int RECEIVE_DATA_ERROR = 18;        // 接收数据错误
    public final static int CLOSE_CLIENT = 19;              // 关闭客户端
    public final static int SERVER_IS_NOT_OPEN = 20;        // 服务端未开启
    public final static int LOST_PACK = 21;                 // 丢包
    public final static int ON_FRAME_FAIL = 22;             // 解码帧失败
    public final static int CREATE_CODEC_EDCODER = 23;      // 创建解码器失败
    public final static int CLIENT_CONNECT_ERROR = 24;      // 客户端连接异常
    public final static int CREATE_OUTPUT_STREAM_ERROR = 25;// 创建输出流失败

    /* 视频参数配置 */
    public static int width = 1080;         // 录制宽度
    public static int height = 1920;        // 录制高度
    public static int screenDpi = 420;      // 屏幕密度
    public static int videoBitrate = width * height * 4; // 比特率
    public static int videoFrameRate = 24;  // 帧率

    /* 数据类型标识 */
    public static final byte TYPE1 = (byte) 0x00; // I 帧（关键帧）
    public static final byte TYPE2 = (byte) 0x01; // P 帧（预测帧）
    public static final byte CRC_OK = (byte) 0x01; // 校验成功
    public static final byte CRC_FAIL = (byte) 0x00;// 校验失败

    public static Surface getSurface() {
        return surface;
    }

    public static void setSurface(Surface surface) {
        MyApplication.surface = surface;
    }

    public static void setMediaProjection(MediaProjection mediaProjection) {
        MyApplication.mediaProjection = mediaProjection;
    }

    public static MediaProjection getMediaProjection() {
        return mediaProjection;
    }

}
