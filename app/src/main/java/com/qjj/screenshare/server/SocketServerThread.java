package com.qjj.screenshare.server;

import android.util.Log;

import com.qjj.screenshare.entity.MessageEvent;
import com.qjj.screenshare.util.ByteArrayPool;

import org.greenrobot.eventbus.EventBus;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.LinkedList;

import static com.qjj.screenshare.MyApplication.*;

/**
 * @author 曲建金
 */
public class SocketServerThread extends Thread {

    private ServerSocket serverSocket;
    private boolean exit = false;
    private MyEncoder myEncoder = null;

    private long sumPack = 0;
    private long lostPack = 0;

    private OutputStream outputStream = null;
    private InputStream inputStream = null;

    private final LinkedList<VideoData> linkedListVideo = new LinkedList<>();

    private static class VideoData {
        byte[] data;
        int size;

        VideoData(byte[] data, int size) {
            this.data = data;
            this.size = size;
        }
    }

    private int sendDataError = 0;

    @Override
    public void run() {
        try {
            int port = 9900;
            serverSocket = new ServerSocket(port);
        } catch (IOException e) {
            exit = true;
            EventBus.getDefault().post(new MessageEvent(SERVER_SOCKET_CREATE_ERROR));
        }
        while (!exit) {

            //try {
            //客户端连接成功
            EventBus.getDefault().post(new MessageEvent(WAIT_CONNECT));

            Socket socket;
            try {
                socket = serverSocket.accept();
            } catch (IOException e) {
                EventBus.getDefault().post(new MessageEvent(CLIENT_CONNECT_ERROR));
                continue;
            }

            //客户端连接成功
            EventBus.getDefault().post(new MessageEvent(CLIENT_CONNECT));
            //创建编码器
            myEncoder = new MyEncoder(this);

            if (myEncoder.init()) {
                myEncoder.start();
                //初始化编码器之后初始化服务输出流

                try {
                    outputStream = socket.getOutputStream();
                    inputStream = socket.getInputStream();
                } catch (IOException e) {
                    if (!exit) {
                        exit = true;
                        EventBus.getDefault().post(new MessageEvent(CREATE_OUTPUT_STREAM_ERROR));
                    }
                }
            } else {
                myEncoder.close();
                myEncoder = null;
                continue;
            }

            while (!exit) {

                byte[] result = new byte[1];
                VideoData videoData;

                synchronized (linkedListVideo) {
                    if (linkedListVideo.size() <= 0) {
                        continue;
                    }
                    videoData = linkedListVideo.getLast();
                    linkedListVideo.removeLast();
                }
                if (!write(videoData.data, videoData.size)) {
                    ByteArrayPool.release(videoData.data);
                    break;
                }
                ByteArrayPool.release(videoData.data);

                try {
                    inputStream.read(result);
                } catch (IOException e) {
                    e.printStackTrace();
                }

                if (result[0] == CRC_FAIL) {
                    Log.d("---", "CRC_FAIL");
                }
            }

            try {
                if (inputStream != null) {
                    inputStream.close();
                }
                if (outputStream != null) {
                    outputStream.close();
                }
                if (socket != null) {
                    socket.close();
                }
            } catch (IOException e) {
                EventBus.getDefault().post(new MessageEvent(CLIENT_SOCKET_CLOSE_ERROR));
            }
        }
        exit();
    }

    private boolean write(byte[] videoPack, int size) {
        try {
            //Log.d("+++", "write: " + videoPack.length);
            outputStream.write(videoPack, 0, size);
            outputStream.flush();
        } catch (IOException e) {
            sendDataError++;
            if (sendDataError > 3) {
                EventBus.getDefault().post(new MessageEvent(SEND_DATA_ERROR));
                myEncoder.close();
                myEncoder = null;
                return false;
            }
        }
        return true;
    }

    public void putVideoPack(byte[] videoPack, int size) {
        synchronized (linkedListVideo) {
            sumPack++;
            if (linkedListVideo.size() >= VIDEO_PACK_LIST_MAX_LENGTH) {
                lostPack++;
                EventBus.getDefault().post(new MessageEvent(LOST_PACK, "server:" + ((lostPack * 100) / sumPack) + "%"));
                VideoData removed = linkedListVideo.removeLast();
                ByteArrayPool.release(removed.data);
            }
            linkedListVideo.push(new VideoData(videoPack, size));
        }
    }

    public void exit() {
        exit = true;
        if (serverSocket != null) {
            try {
                serverSocket.close();
            } catch (IOException e) {
                EventBus.getDefault().post(new MessageEvent(SERVER_SOCKET_CLOSE_ERROR));
            }
        }
        if (myEncoder != null) {
            myEncoder.close();
        }
        EventBus.getDefault().post(new MessageEvent(SERVER_SOCKET_CLOSE));
        super.interrupt();
    }
}
