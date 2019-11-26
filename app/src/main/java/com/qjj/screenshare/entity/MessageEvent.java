package com.qjj.screenshare.entity;

/**
 * 创建日期：2019/11/18 10:23
 *
 * @author 曲建金
 * 说明：
 */
public class MessageEvent {
    private int what;
    private String message;

    public int getWhat() {
        return what;
    }

    public void setWhat(int what) {
        this.what = what;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public MessageEvent(int what, String message) {
        this.what = what;
        this.message = message;
    }

    public MessageEvent(int what) {
        this.what = what;
    }
}
