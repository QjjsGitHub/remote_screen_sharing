package com.qjj.screenshare.client;

import android.view.Surface;

import com.qjj.screenshare.MyApplication;
import com.qjj.screenshare.entity.MessageEvent;
import com.qjj.screenshare.entity.VideoPack;
import com.qjj.screenshare.server.Decoder;

import org.greenrobot.eventbus.EventBus;

import java.util.LinkedList;

import static com.qjj.screenshare.MyApplication.OPEN_FLOAT_WINDOW;


/**
 * @author 曲建金
 */
public class VideoPlayThread extends Thread {
    private boolean exit;
    private Surface surface;
    private boolean hasInitVideo;
    private LinkedList<VideoPack> linkedListVideo;
    private Decoder videoDecoder;

    VideoPlayThread(Surface surface, LinkedList<VideoPack> dataPackList) {
        this.surface = surface;
        this.linkedListVideo = dataPackList;
    }

    @Override
    public void run() {

        EventBus.getDefault().post(new MessageEvent(OPEN_FLOAT_WINDOW));

        while ((!exit)) {
            VideoPack videoPack;
            synchronized (linkedListVideo) {
                if (linkedListVideo.size() <= 0) {
                    continue;
                }
                videoPack = linkedListVideo.getLast();
                linkedListVideo.removeLast();
            }
            if (videoPack != null) {
                if (!hasInitVideo) {
                    videoDecoder = new Decoder(MyApplication.width, MyApplication.height, MyApplication.videoFrameRate, surface);
                    if (videoDecoder.initDecoder()) {
                        hasInitVideo = true;
                        videoPack.setPresentationTimeUs(0);
                    } else {
                        exit = true;
                        break;
                    }
                }
                videoDecoder.onFrame(videoPack.getFrames(), 0, videoPack.getFrames().length, videoPack.getPresentationTimeUs());
            }

        }
        dirtory();
    }

    private void dirtory() {
        if (videoDecoder != null) {
            videoDecoder.release();
        }

        surface = null;
        linkedListVideo = null;
        videoDecoder = null;
    }

    void exit() {
        this.exit = true;
    }
}
