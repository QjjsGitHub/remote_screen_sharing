package com.qjj.screenshare.server;

import android.util.Log;

import com.qjj.screenshare.entity.MessageEvent;

import org.greenrobot.eventbus.EventBus;

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static com.qjj.screenshare.MyApplication.CLIENT_CONNECT;
import static com.qjj.screenshare.MyApplication.SERVER_SOCKET_CLOSE;
import static com.qjj.screenshare.MyApplication.SERVER_SOCKET_CLOSE_ERROR;
import static com.qjj.screenshare.MyApplication.SERVER_SOCKET_CREATE_ERROR;
import static com.qjj.screenshare.MyApplication.WAIT_CONNECT;

/**
 * Socket 服务端监听线程
 * 优化后支持：多客户端并发连接、按需动态启动/关闭编码器
 */
public class SocketServerThread extends Thread {

    private ServerSocket serverSocket;
    private boolean exit = false;
    private MyEncoder myEncoder = null;
    
    // 线程安全的客户端流列表，用于广播
    private final List<DataOutputStream> clientStreams = new CopyOnWriteArrayList<>();

    @Override
    public void run() {
        try {
            serverSocket = new ServerSocket(9900);
            serverSocket.setSoTimeout(5000);
        } catch (IOException e) {
            exit = true;
            EventBus.getDefault().post(new MessageEvent(SERVER_SOCKET_CREATE_ERROR));
        }

        while (!exit) {
            EventBus.getDefault().post(new MessageEvent(WAIT_CONNECT));
            try {
                Socket socket = serverSocket.accept();
                handleNewClient(socket);
            } catch (IOException ignored) {}
        }
        exit();
    }

    /**
     * 处理新加入的客户端连接
     */
    private void handleNewClient(Socket socket) {
        new Thread(() -> {
            DataOutputStream dos = null;
            try {
                socket.setTcpNoDelay(true);
                socket.setSendBufferSize(1024 * 1024);
                
                OutputStream os = socket.getOutputStream();
                InputStream is = socket.getInputStream();
                dos = new DataOutputStream(os);

                // 将新客户端加入广播列表
                clientStreams.add(dos);
                EventBus.getDefault().post(new MessageEvent(CLIENT_CONNECT));

                // 检查并启动编码器（按需启动）
                checkEncoderStatus();

                // 阻塞读取，直到客户端断开（心跳检测）
                while (!exit && socket.isConnected()) {
                    if (is.read() == -1) break;
                }
            } catch (IOException e) {
                Log.e("SocketServer", "客户端连接断开: " + e.getMessage());
            } finally {
                if (dos != null) clientStreams.remove(dos);
                try { socket.close(); } catch (IOException ignored) {}
                
                // 检查并尝试停止编码器（如果没有客户端了）
                checkEncoderStatus();
            }
        }).start();
    }

    /**
     * 动态管理编码器生命周期
     * 策略：有客户端连接则运行，全断开则停止以省电
     */
    private synchronized void checkEncoderStatus() {
        if (!clientStreams.isEmpty()) {
            if (myEncoder == null || !myEncoder.isAlive()) {
                myEncoder = new MyEncoder(clientStreams);
                if (myEncoder.init()) {
                    myEncoder.start();
                } else {
                    myEncoder = null;
                }
            }
        } else {
            if (myEncoder != null) {
                myEncoder.close();
                myEncoder = null;
            }
        }
    }

    /**
     * 关闭服务端
     */
    public void exit() {
        exit = true;
        try {
            if (serverSocket != null) {
                serverSocket.close();
            }
        } catch (IOException e) {
            EventBus.getDefault().post(new MessageEvent(SERVER_SOCKET_CLOSE_ERROR));
        }
        
        for (DataOutputStream dos : clientStreams) {
            try { dos.close(); } catch (IOException ignored) {}
        }
        clientStreams.clear();
        
        if (myEncoder != null) {
            myEncoder.close();
        }
        EventBus.getDefault().post(new MessageEvent(SERVER_SOCKET_CLOSE));
        super.interrupt();
    }
}
