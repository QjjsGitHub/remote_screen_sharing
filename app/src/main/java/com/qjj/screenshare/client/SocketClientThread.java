package com.qjj.screenshare.client;

import android.util.Log;

import com.qjj.screenshare.entity.MessageEvent;
import com.qjj.screenshare.MyApplication;
import com.qjj.screenshare.entity.VideoPack;
import com.qjj.screenshare.util.CombineValue;

import org.greenrobot.eventbus.EventBus;

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

                int dataLength;
                int crc;
                long presentationTimeUs;
                int type;
                byte[] videoPack;

                int length;
                int lastLength = 0;

                byte[] temp4 = new byte[4];
                byte[] temp8 = new byte[8];

                int headLength = 1024 * 50;

                while (!exit) {
                    byte[] temp = new byte[headLength];

                    length = inputStream.read(temp);
                    // Log.d("+++", "read: " + length);

                    System.arraycopy(temp, 0, temp4, 0, 4);
                    crc = CombineValue.bytesToInt(temp4);

                    System.arraycopy(temp, 4, temp4, 0, 4);
                    dataLength = CombineValue.bytesToInt(temp4);

                    System.arraycopy(temp, 8, temp8, 0, 8);
                    presentationTimeUs = CombineValue.bytesToLong(temp8);

                    type = ((temp[16] == TYPE1) ? 1 : 2);

                    videoPack = new byte[dataLength];

                    if (length - 17 < dataLength) {
                        System.arraycopy(temp, 17, videoPack, lastLength, length - 17);
                        lastLength += length - 17;
                        do {
                            length = inputStream.read(temp);

                            if (length + lastLength > dataLength) {
                                System.arraycopy(temp, 0, videoPack, lastLength, dataLength - lastLength);
                            } else {
                                System.arraycopy(temp, 0, videoPack, lastLength, length);
                            }
                            //Log.d("+++", "read: " + length);
                            lastLength += length;
                        } while (lastLength < dataLength);
                        lastLength = 0;
                    } else {
                        System.arraycopy(temp, 17, videoPack, 0, dataLength);
                    }

                    /*if (crc == CRC.getIntCRC(videoPack)) {*/
                    if (true) {
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
                    } else {
                        Log.d("+++", "CRC_FAIL");
                        outputStream.write(CRC_FAIL);
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
