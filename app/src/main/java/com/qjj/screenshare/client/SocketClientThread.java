package com.qjj.screenshare.client;

import com.qjj.screenshare.entity.MessageEvent;
import com.qjj.screenshare.MyApplication;
import com.qjj.screenshare.server.Decoder;

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
import static com.qjj.screenshare.MyApplication.TYPE1;

/**
 * @author 曲建金
 */
public class SocketClientThread extends Thread {

    private Socket client;
    private boolean exit = false;
    private SocketAddress socketAddress;
    private InputStream inputStream;
    private OutputStream outputStream;
    private Decoder videoDecoder;
    private boolean hasInitVideo = false;
    private String ip = "";

    public SocketClientThread(String ip) {
        this.ip = ip;
    }

    public void connect() {
        client = new Socket();
        socketAddress = new InetSocketAddress(ip, 9900);
        try {
            client.connect(socketAddress);
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
            EventBus.getDefault().post(new MessageEvent(OPEN_FLOAT_WINDOW));

            try {
                inputStream = client.getInputStream();
                outputStream = client.getOutputStream();
                DataInputStream dis = new DataInputStream(inputStream);

                while (!exit) {
                    dis.readInt(); // crc
                    int dataLength = dis.readInt();
                    long presentationTimeUs = dis.readLong();
                    dis.readByte(); // type

                    byte[] videoPack = new byte[dataLength];
                    dis.readFully(videoPack);

                    if (!hasInitVideo) {
                        videoDecoder = new Decoder(MyApplication.width, MyApplication.height, MyApplication.videoFrameRate, MyApplication.getSurface());
                        if (videoDecoder.initDecoder()) {
                            hasInitVideo = true;
                        } else {
                            break;
                        }
                    }

                    if (hasInitVideo) {
                        videoDecoder.onFrame(videoPack, 0, dataLength, presentationTimeUs);
                    }
                    outputStream.write(CRC_OK);
                }
            } catch (Throwable e) {
                e.printStackTrace();
                if (!exit) {
                    EventBus.getDefault().post(new MessageEvent(RECEIVE_DATA_ERROR));
                }
            } finally {
                if (!exit) {
                    exit();
                }
            }
        }
    }

    public void exit() {
        exit = true;
        try {
            if (inputStream != null) {
                inputStream.close();
            }
            client.close();
        } catch (Throwable e) {
            e.printStackTrace();
        }
        if (videoDecoder != null) {
            videoDecoder.release();
        }
        EventBus.getDefault().post(new MessageEvent(CLOSE_CLIENT));
        super.interrupt();
    }
}
