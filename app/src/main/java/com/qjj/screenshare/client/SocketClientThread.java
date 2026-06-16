package com.qjj.screenshare.client;

import com.qjj.screenshare.entity.MessageEvent;
import com.qjj.screenshare.MyApplication;

import org.greenrobot.eventbus.EventBus;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;

import static com.qjj.screenshare.MyApplication.CLOSE_CLIENT;
import static com.qjj.screenshare.MyApplication.CONNECT_SERVER;
import static com.qjj.screenshare.MyApplication.CONNECT_SERVER_ERROR;
import static com.qjj.screenshare.MyApplication.CRC_OK;
import static com.qjj.screenshare.MyApplication.OPEN_FLOAT_WINDOW;
import static com.qjj.screenshare.MyApplication.RECEIVE_DATA_ERROR;

/**
 * 客户端连接与接收线程
 * 负责连接远程服务端，接收并解析视频数据流，直接调用解码器渲染
 *
 * @author 曲建金
 */
public class SocketClientThread extends Thread {

    private Socket client;
    private boolean exit = false;
    private InputStream inputStream;
    private OutputStream outputStream;
    private Decoder videoDecoder;
    private boolean hasInitVideo = false;
    private final String ip;

    public SocketClientThread(String ip) {
        this.ip = ip;
    }

    /**
     * 建立 Socket 连接
     */
    public void connect() {
        client = new Socket();
        SocketAddress socketAddress = new InetSocketAddress(ip, 9900);
        try {
            client.connect(socketAddress, 5000); // 设置 5 秒连接超时
        } catch (IOException e) {
            EventBus.getDefault().post(new MessageEvent(CONNECT_SERVER_ERROR));
            exit = true;
        }
    }

    @Override
    public void run() {
        connect();
        if (!exit) {
            EventBus.getDefault().post(new MessageEvent(CONNECT_SERVER));
            // 通知 UI 层打开悬浮显示窗
            EventBus.getDefault().post(new MessageEvent(OPEN_FLOAT_WINDOW));

            try {
                inputStream = client.getInputStream();
                outputStream = client.getOutputStream();
                // 使用 DataInputStream 简化大端字节序数据的解析
                DataInputStream dis = new DataInputStream(inputStream);

                // 首先读取服务端声明的画面宽高
                int remoteWidth = dis.readInt();
                int remoteHeight = dis.readInt();

                while (!exit) {
                    // 1. 解析自定义协议头部（必须与服务端顺序完全一致）
                    dis.readInt(); // 读取并跳过 CRC 校验位 (4字节)
                    int dataLength = dis.readInt(); // 获取当前帧的有效负载长度 (4字节)
                    long presentationTimeUs = dis.readLong(); // 获取呈现时间戳 (8字节)
                    dis.readByte(); // 读取并跳过帧类型标识 (1字节)

                    // 2. 稳定读取完整的帧数据内容
                    byte[] videoPack = new byte[dataLength];
                    dis.readFully(videoPack); // 阻塞读取，直到填满缓冲区

                    // 3. 延迟初始化解码器，使用从服务端接收到的宽高
                    if (!hasInitVideo) {
                        videoDecoder = new Decoder(remoteWidth, remoteHeight, MyApplication.videoFrameRate, MyApplication.getSurface());
                        if (videoDecoder.initDecoder()) {
                            hasInitVideo = true;
                        } else {
                            break; // 初始化失败则退出
                        }
                    }

                    // 4. 将接收到的 H.264 数据送入解码器
                    videoDecoder.onFrame(videoPack, 0, dataLength, presentationTimeUs);

                    // 5. 向服务端回复 ACK（用于服务端检测连接状态）
                    outputStream.write(CRC_OK);
                }
            } catch (Throwable e) {
                if (!exit) {
                    EventBus.getDefault().post(new MessageEvent(RECEIVE_DATA_ERROR));
                }
            } finally {
                exit();
            }
        }
    }

    /**
     * 优雅地关闭客户端连接并释放资源
     */
    public void exit() {
        exit = true;
        try {
            if (inputStream != null) {
                inputStream.close();
            }
            if (outputStream != null) {
                outputStream.close();
            }
            if (client != null) {
                client.close();
            }
        } catch (Throwable ignored) {
        }

        if (videoDecoder != null) {
            videoDecoder.release();
        }

        EventBus.getDefault().post(new MessageEvent(CLOSE_CLIENT));
        super.interrupt();
    }
}
