package com.qjj.screenshare.entity;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.io.Serializable;

/**
 * @author 曲建金
 */
public class VideoPack implements Serializable {

    private byte[] frames;
    private int type;
    private long presentationTimeUs;


    public VideoPack(byte[] frames, int type, long presentationTimeUs) {
        this.frames = frames;
        this.presentationTimeUs = presentationTimeUs;
        this.type = type;
    }

    public byte[] getFrames() {
        return frames;
    }

    public void setFrames(byte[] frames) {
        this.frames = frames;
    }

    public int getType() {
        return type;
    }

    public void setType(int type) {
        this.type = type;
    }

    public long getPresentationTimeUs() {
        return presentationTimeUs;
    }

    public void setPresentationTimeUs(long presentationTimeUs) {
        this.presentationTimeUs = presentationTimeUs;
    }
}
