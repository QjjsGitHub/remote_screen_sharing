package com.qjj.screenshare.server;

import android.util.Log;

import com.qjj.screenshare.entity.MessageEvent;

import org.greenrobot.eventbus.EventBus;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;

import static com.qjj.screenshare.MyApplication.CLIENT_CONNECT;
import static com.qjj.screenshare.MyApplication.CLIENT_CONNECT_ERROR;
import static com.qjj.screenshare.MyApplication.CLIENT_SOCKET_CLOSE_ERROR;
import static com.qjj.screenshare.MyApplication.CREATE_OUTPUT_STREAM_ERROR;
import static com.qjj.screenshare.MyApplication.SERVER_SOCKET_CLOSE;
import static com.qjj.screenshare.MyApplication.SERVER_SOCKET_CLOSE_ERROR;
import static com.qjj.screenshare.MyApplication.SERVER_SOCKET_CREATE_ERROR;
import static com.qjj.screenshare.MyApplication.WAIT_CONNECT;

/**
 * @author 曲建金
 */
public class SocketServerThread extends Thread {

    private ServerSocket serverSocket;
    private boolean exit = false;
    private MyEncoder myEncoder = null;

    private OutputStream outputStream = null;
    private InputStream inputStream = null;

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
            
            try {
                socket.setTcpNoDelay(true); // 禁用 Nagle 算法，降低延迟
                socket.setSendBufferSize(1024 * 1024); // 设置 1MB 发送缓冲区
                outputStream = socket.getOutputStream();
                inputStream = socket.getInputStream();
            } catch (IOException e) {
                EventBus.getDefault().post(new MessageEvent(CREATE_OUTPUT_STREAM_ERROR));
                continue;
            }

            //创建编码器，直接传入输出流
            myEncoder = new MyEncoder(outputStream);

            if (myEncoder.init()) {
                myEncoder.start();
            } else {
                myEncoder.close();
                myEncoder = null;
                continue;
            }

            // 等待连接断开
            try {
                while (!exit && socket.isConnected()) {
                    if (inputStream.read() == -1) break; 
                }
            } catch (IOException e) {
                Log.e("SocketServer", "连接已断开");
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
