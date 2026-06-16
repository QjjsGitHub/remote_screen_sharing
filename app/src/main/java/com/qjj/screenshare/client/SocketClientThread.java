package com.qjj.screenshare.client;

import android.util.Log;

import com.qjj.screenshare.entity.MessageEvent;
import com.qjj.screenshare.MyApplication;
import com.qjj.screenshare.entity.VideoPack;

import org.greenrobot.eventbus.EventBus;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.util.LinkedList;

import static com.qjj.screenshare.MyApplication.CLOSE_CLIENT;
import static com.qjj.screenshare.MyApplication.CONNECT_SERVER;
import static com.qjj.screenshare.MyApplication.CONNECT_SERVER_ERROR;
import static com.qjj.screenshare.MyApplication.CRC_FAIL;
import static com.qjj.screenshare.MyApplication.CRC_OK;
import static com.qjj.screenshare.MyApplication.LOST_PACK;
import static com.qjj.screenshare.MyApplication.RECEIVE_DATA_ERROR;
import static com.qjj.screenshare.MyApplication.TYPE1;

/**
 * @author 曲建金
 */
public class SocketClientThread extends Thread {

    private int sumPack = 0;
    private int lostPack = 0;

    private LinkedList<VideoPack> linkedListVideo = new LinkedList<>();

    private Socket client;
    private boolean exit = false;
    private SocketAddress socketAddress;
    private InputStream inputStream;
    private OutputStream outputStream;
    private VideoPlayThread videoPlayThread = null;
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

            videoPlayThread = new VideoPlayThread(MyApplication.getSurface(), linkedListVideo);
            videoPlayThread.start();

            try {
                inputStream = client.getInputStream();
                outputStream = client.getOutputStream();
                DataInputStream dis = new DataInputStream(inputStream);

                while (!exit) {
                    int crc = dis.readInt();
                    int dataLength = dis.readInt();
                    long presentationTimeUs = dis.readLong();
                    byte typeByte = dis.readByte();
                    int type = (typeByte == TYPE1 ? 1 : 2);

                    byte[] videoPack = new byte[dataLength];
                    dis.readFully(videoPack); // 确保读取完整的数据包

                    synchronized (linkedListVideo) {
                        outputStream.write(CRC_OK);
                        sumPack++;
                        if (linkedListVideo.size() >= 24) {
                            lostPack++;
                            EventBus.getDefault().post(new MessageEvent(LOST_PACK, "client:" + lostPack * 100 / sumPack + "%"));
                            linkedListVideo.removeLast();
                        }
                        VideoPack videoPack1 = new VideoPack(videoPack, type, presentationTimeUs);
                        linkedListVideo.push(videoPack1);
                    }
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
        if (videoPlayThread != null) {
            videoPlayThread.exit();
        }
        EventBus.getDefault().post(new MessageEvent(CLOSE_CLIENT));
        super.interrupt();
    }
}
