package com.qjj.screenshare.server;

import android.util.Log;

import com.qjj.screenshare.entity.MessageEvent;
import com.qjj.screenshare.entity.VideoPack;

import org.greenrobot.eventbus.EventBus;

import java.io.IOException;
import java.io.ObjectOutputStream;
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

    private LinkedList<VideoPack> linkedListVideo = new LinkedList<>();

    private Socket socket;

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
            OutputStream outputStream;
            ObjectOutputStream objectOutputStream = null;
            VideoPack videoPack;
            //try {
            //客户端连接成功
            EventBus.getDefault().post(new MessageEvent(WAIT_CONNECT));

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
                    objectOutputStream = new ObjectOutputStream(outputStream);
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

            int sendDataError = 0;
            while (!exit) {
                synchronized (linkedListVideo) {
                    if (linkedListVideo.size() <= 0) {
                        continue;
                    }
                    videoPack = linkedListVideo.getFirst();
                    linkedListVideo.removeFirst();
                }

                try {
                    objectOutputStream.writeObject(videoPack);
                } catch (IOException e) {
                    sendDataError++;
                    EventBus.getDefault().post(new MessageEvent(SEND_DATA_ERROR));
                    if (sendDataError > 3) {
                        myEncoder.close();
                        myEncoder = null;
                        break;
                    }
                }
            }

            try {
                if (objectOutputStream != null) {
                    objectOutputStream.close();
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

    public void putVideoPack(VideoPack videoPack) {
        synchronized (linkedListVideo) {
            sumPack++;
            if (linkedListVideo.size() >= VIDEO_PACK_LIST_MAX_LENGTH) {
                lostPack++;
                EventBus.getDefault().post(new MessageEvent(LOST_PACK, ((lostPack * 100) / sumPack) + "%"));
                linkedListVideo.removeFirst();
            }
            //Log.d("send", ":" + linkedListVideo.size());
            linkedListVideo.push(videoPack);
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
