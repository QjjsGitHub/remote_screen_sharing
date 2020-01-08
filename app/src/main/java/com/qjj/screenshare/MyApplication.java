package com.qjj.screenshare;

import android.app.Application;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.view.Surface;

public class MyApplication extends Application {
    public static final int VIDEO_PACK_LIST_MAX_LENGTH = 24;
    public static MediaProjection mediaProjection;
    public static MediaProjectionManager mediaProjectionManager;

    static Surface surface;

    /*format type list*/
    public static final byte VIDEO = (byte) 0x00;
    public static final byte AUDIO = (byte) 0x01;

    /*data type list*/
    public static final byte TYPE1 = (byte) 0x00;
    public static final byte TYPE2 = (byte) 0x01;

    /*data type list*/
    public static final byte CRC_OK = (byte) 0x01;
    public static final byte CRC_FAIL = (byte) 0x00;

    public final static int OPEN_FLOAT_WINDOW = 1;
    public final static int IP_IS_NULL = 2;
    public final static int MEDIA_PROJECTION_IS_NULL = 3;
    public final static int CODEC_SERVER_RUN = 4;
    public final static int CLIENT_CONNECT = 5;
    public final static int REQUEST_MEDIA_PROJECTION_FAIL = 6;
    public final static int SEND_DATA_ERROR = 7;
    public final static int CLIENT_SOCKET_CLOSE_ERROR = 8;
    public final static int SERVER_SOCKET_CLOSE_ERROR = 9;
    public final static int SOCKET_CLOSE = 10;
    public final static int SERVER_SOCKET_CLOSE = 11;
    public final static int SERVER_SOCKET_CREATE_ERROR = 12;
    public final static int CODEC_CREATE_ERROE = 13;
    public final static int CODEC_ERROR = 14;
    public final static int WAIT_CONNECT = 15;
    public final static int CONNECT_SERVER_ERROR = 16;
    public final static int CONNECT_SERVER = 17;
    public final static int RECEIVE_DATA_ERROR = 18;
    public final static int CLOSE_CLIENT = 19;
    public final static int SERVER_IS_NOT_OPEN = 20;
    public final static int LOST_PACK = 21;
    public final static int ON_FRAME_FAIL = 22;
    public final static int CREATE_CODEC_EDCODER = 23;
    public final static int CLIENT_CONNECT_ERROR = 24;
    public final static int CREATE_OUTPUT_STREAM_ERROR = 25;


    public static int width = 1080;
    public static int height = 1920;
    public static int videoBitrate = width * height * 4;
    public static int videoFrameRate = 24;

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
