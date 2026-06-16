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
 * Socket 服务端监听线程
 * 负责监听客户端连接，管理网络流，并启动编码器
 * @author 曲建金
 */
public class SocketServerThread extends Thread {

    private ServerSocket serverSocket;
    private boolean exit = false;
    private MyEncoder myEncoder = null;

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
            // 发送等待连接通知
            EventBus.getDefault().post(new MessageEvent(WAIT_CONNECT));

            Socket socket;
            try {
                // 阻塞等待客户端接入
                socket = serverSocket.accept();
            } catch (IOException e) {
                if (!exit) EventBus.getDefault().post(new MessageEvent(CLIENT_CONNECT_ERROR));
                continue;
            }

            // 客户端连接成功
            EventBus.getDefault().post(new MessageEvent(CLIENT_CONNECT));
            
            OutputStream outputStream;
            InputStream inputStream;
            try {
                // Socket 参数调优
                socket.setTcpNoDelay(true); // 禁用 Nagle 算法，降低实时流延迟
                socket.setSendBufferSize(1024 * 1024); // 增大发送缓冲区到 1MB
                outputStream = socket.getOutputStream();
                inputStream = socket.getInputStream();
            } catch (IOException e) {
                EventBus.getDefault().post(new MessageEvent(CREATE_OUTPUT_STREAM_ERROR));
                try { socket.close(); } catch (IOException ignored) {}
                continue;
            }

            // 创建编码器，并直接传入网络输出流实现“直接对接”
            myEncoder = new MyEncoder(outputStream);

            if (myEncoder.init()) {
                myEncoder.start();
            } else {
                myEncoder.close();
                myEncoder = null;
                continue;
            }

            // 保持线程运行，循环读取客户端心跳或断开信号
            try {
                while (!exit && socket.isConnected()) {
                    // 如果读取到 -1，说明客户端已主动关闭连接
                    if (inputStream.read() == -1) break; 
                }
            } catch (IOException e) {
                Log.e("SocketServer", "客户端连接异常断开");
            } finally {
                // 清理资源，准备接受下一个连接
                if (myEncoder != null) {
                    myEncoder.close();
                    myEncoder = null;
                }
                try {
                    socket.close();
                } catch (IOException ignored) {}
            }
        }
        exit();
    }

    /**
     * 关闭服务端
     */
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
