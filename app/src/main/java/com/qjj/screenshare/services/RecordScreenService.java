package com.qjj.screenshare.services;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.media.projection.MediaProjection;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import com.qjj.screenshare.MainActivity;
import com.qjj.screenshare.MyApplication;
import com.qjj.screenshare.R;
import com.qjj.screenshare.entity.MessageEvent;
import com.qjj.screenshare.server.SocketServerThread;

import org.greenrobot.eventbus.EventBus;

import static com.qjj.screenshare.MyApplication.MEDIA_PROJECTION_IS_NULL;
import static com.qjj.screenshare.MyApplication.SERVER_IS_NOT_OPEN;
import static com.qjj.screenshare.MyApplication.mediaProjection;
import static com.qjj.screenshare.MyApplication.mediaProjectionManager;

/**
 * @author 曲建金
 */
public class RecordScreenService extends Service {

    private static final String UNLOCK_NOTIFICATION_CHANNEL_ID = "recordscreen";
    private MyBinder myBinder;
    private SocketServerThread socketServerThread;

    @Override
    public void onCreate() {
        super.onCreate();
        if (myBinder == null) {
            myBinder = new MyBinder();
        }
        initNotificationChannel();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            buildNotification(R.mipmap.ic_launcher, "屏幕分享", "服务正在运行");
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return myBinder;
    }

    public class MyBinder extends Binder {

        public void startShare(int resultCode, Intent data) {

            mediaProjection = mediaProjectionManager.getMediaProjection(resultCode, data);
            MyApplication.setMediaProjection(mediaProjection);
            if (mediaProjection == null) {
                EventBus.getDefault().post(new MessageEvent(MEDIA_PROJECTION_IS_NULL));
                return;
            }


            socketServerThread = new SocketServerThread();
            socketServerThread.start();
        }

        public void stopShare() {
            if (socketServerThread != null) {
                socketServerThread.exit();
            } else {
                EventBus.getDefault().post(new MessageEvent(SERVER_IS_NOT_OPEN));
            }
        }
    }

    private boolean initNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            //创建通知渠道
            CharSequence name = "运行通知";
            String description = "服务运行中";
            //重要性级别
            int importance = NotificationManager.IMPORTANCE_DEFAULT;

            NotificationChannel mChannel = new NotificationChannel(UNLOCK_NOTIFICATION_CHANNEL_ID, name, importance);
            //渠道描述
            mChannel.setDescription(description);
            //是否显示通知指示灯
            mChannel.enableLights(false);
            //是否振动
            mChannel.enableVibration(false);

            NotificationManager notificationManager = (NotificationManager) getApplicationContext().getSystemService(NOTIFICATION_SERVICE);
            //创建通知渠道
            if (notificationManager != null) {
                notificationManager.createNotificationChannel(mChannel);
                return true;
            }
        }
        return false;
    }

    private void buildNotification(int resId, String title, String contenttext) {
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, UNLOCK_NOTIFICATION_CHANNEL_ID);

        // 必需的通知内容
        builder.setContentTitle(title)
                .setContentText(contenttext)
                .setSmallIcon(resId);

        Intent notifyIntent = new Intent(this, MainActivity.class);
        PendingIntent notifyPendingIntent = PendingIntent.getActivity(this, 0, notifyIntent, PendingIntent.FLAG_MUTABLE);
        builder.setContentIntent(notifyPendingIntent);

        Notification notification = builder.build();
        //常驻状态栏的图标
        //notification.icon = resId;
        // 将此通知放到通知栏的"Ongoing"即"正在运行"组中
        notification.flags |= Notification.FLAG_ONGOING_EVENT;
        // 表明在点击了通知栏中的"清除通知"后，此通知不清除，经常与FLAG_ONGOING_EVENT一起使用
        notification.flags |= Notification.FLAG_NO_CLEAR;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION);
        } else {
            startForeground(1, notification);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }
}
