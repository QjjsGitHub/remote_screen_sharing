package com.qjj.screenshare.client;

import android.util.Log;

import com.qjj.screenshare.entity.MessageEvent;
import com.qjj.screenshare.MyApplication;
import com.qjj.screenshare.entity.VideoPack;

import org.greenrobot.eventbus.EventBus;

import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.util.LinkedList;

import static com.qjj.screenshare.MyApplication.CLOSE_CLIENT;
import static com.qjj.screenshare.MyApplication.CONNECT_SERVER;
import static com.qjj.screenshare.MyApplication.CONNECT_SERVER_ERROR;
import static com.qjj.screenshare.MyApplication.LOST_PACK;
import static com.qjj.screenshare.MyApplication.RECEIVE_DATA_ERROR;

/**
 * @author 曲建金
 */
public class SocketClientThread extends Thread {

    private int sumPack = 0;
    private int lostPack = 0;

    private LinkedList<VideoPack> linkedListVideo = new LinkedList<VideoPack>();

    private Socket client;
    private boolean exit = false;
    private SocketAddress socketAddress;
    private InputStream inputStream;
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
            ObjectInputStream objectInputStream;
            EventBus.getDefault().post(new MessageEvent(CONNECT_SERVER));

            videoPlayThread = new VideoPlayThread(MyApplication.getSurface(), linkedListVideo);
            videoPlayThread.start();

            try {
                inputStream = client.getInputStream();
                objectInputStream = new ObjectInputStream(inputStream);
                while (!exit) {
                    VideoPack videoPack = (VideoPack) objectInputStream.readObject();
                    synchronized (linkedListVideo) {
                        sumPack++;
                        //Log.d("read", ":" + linkedListVideo.size());
                        if (linkedListVideo.size() >= 24) {
                            lostPack++;
                            EventBus.getDefault().post(new MessageEvent(LOST_PACK, lostPack * 100 / sumPack + "%"));
                            linkedListVideo.removeFirst();
                        }
                        linkedListVideo.add(videoPack);
                    }
                }
            } catch (Throwable e) {
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
