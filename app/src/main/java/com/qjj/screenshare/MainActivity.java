package com.qjj.screenshare;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.SurfaceView;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.qjj.screenshare.entity.MessageEvent;
import com.qjj.screenshare.services.RecordScreenService;
import com.qjj.screenshare.client.SocketClientThread;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Enumeration;

import static com.qjj.screenshare.MyApplication.*;

/**
 * @author 曲建金
 */
public class MainActivity extends AppCompatActivity implements View.OnClickListener {

    private static final int REQUEST_MEDIA_PROJECTION = 11;
    private static final int REQUEST_POST_NOTIFICATIONS = 12;
    private RecordScreenServiceConnection recordScreenServiceConnection = null;
    private RecordScreenService.MyBinder myBinder;
    private Button recordButton;
    private Button playButton;
    private EditText ipEditText;
    private TextView localIpTextView;
    private View displayView;
    private WindowManager windowManager;
    private WindowManager.LayoutParams layoutParams;
    private boolean floatWindowIsShow = false;
    private SocketClientThread socketClientThread;

    private long lastClickTime = 0;
    private short screenSize = 2;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        WindowManager.LayoutParams lp = getWindow().getAttributes();
        lp.flags |= WindowManager.LayoutParams.FLAG_FULLSCREEN;
        getWindow().setAttributes(lp);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);

        EventBus.getDefault().register(this);
        //初始化布局与监听
        initView();
        //初始化服务
        initService();
        //显示本机ip地址
        String ip = getHostIP();
        localIpTextView.setText(ip);
        ipEditText.setText(ip);

        //get screen width and height
        WindowManager wm = (WindowManager) getApplicationContext().getSystemService(Context.WINDOW_SERVICE);
        DisplayMetrics dm = new DisplayMetrics();
        if (null != wm) {
            wm.getDefaultDisplay().getMetrics(dm);
            width = dm.widthPixels;
            height = dm.heightPixels;
        }

        checkNotificationPermission();
    }

    private void checkNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQUEST_POST_NOTIFICATIONS);
            }
        }
    }

    /**
     * 获取ip地址
     *
     * @return ip地址
     */
    public String getHostIP() {

        String hostIp = "";
        try {
            Enumeration enumeration = NetworkInterface.getNetworkInterfaces();
            InetAddress inetAddress;
            while (enumeration.hasMoreElements()) {
                NetworkInterface networkInterface = (NetworkInterface) enumeration.nextElement();
                Enumeration<InetAddress> inetAddressEnumeration = networkInterface.getInetAddresses();
                while (inetAddressEnumeration.hasMoreElements()) {
                    inetAddress = inetAddressEnumeration.nextElement();
                    if (inetAddress instanceof Inet6Address) {
                        continue;// skip ipv6
                    }
                    String ip = inetAddress.getHostAddress();
                    if (!"127.0.0.1".equals(ip)) {
                        hostIp = inetAddress.getHostAddress();
                        break;
                    }
                }
            }
        } catch (SocketException e) {
            e.printStackTrace();
        }
        return hostIp;
    }

    private void initService() {
        recordScreenServiceConnection = new RecordScreenServiceConnection();
        Intent intent = new Intent(this, RecordScreenService.class);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent);
        } else {
            startService(intent);
        }

        bindService(intent, recordScreenServiceConnection, Context.BIND_AUTO_CREATE);
    }

    /**
     * 初始化布局
     */
    private void initView() {
        recordButton = findViewById(R.id.button);
        recordButton.setOnClickListener(this);

        playButton = findViewById(R.id.button2);
        playButton.setOnClickListener(this);

        ipEditText = findViewById(R.id.editText);
        localIpTextView = findViewById(R.id.textView);

        initLayoutParams();
    }

    /**
     * 初始化悬浮框布局
     */
    @SuppressLint("InflateParams")
    private void initLayoutParams() {
        //申请权限
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!Settings.canDrawOverlays(this)) {
                Toast.makeText(MainActivity.this, "当前无权限，请授权", Toast.LENGTH_SHORT).show();
                startActivityForResult(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName())), 2);
            }
        }

        //获取窗口管理器
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        //初始化窗口布局
        layoutParams = new WindowManager.LayoutParams();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            layoutParams.type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
        } else {
            layoutParams.type = WindowManager.LayoutParams.TYPE_PHONE;
        }
        //layoutParams.format = PixelFormat.RGBA_8888;
        //layoutParams.gravity = Gravity.CENTER;
        layoutParams.flags = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
        layoutParams.width = width / 2;
        layoutParams.height = height / 2;
        layoutParams.x = 0;
        layoutParams.y = 0;
        //初始化悬浮框
        LayoutInflater layoutInflater = LayoutInflater.from(this);
        displayView = layoutInflater.inflate(R.layout.float_display, null);
        displayView.setOnTouchListener(new FloatingOnTouchListener());

        SurfaceView surfaceView = displayView.findViewById(R.id.video_display_surfaceView);
        Surface surface = surfaceView.getHolder().getSurface();
        MyApplication.setSurface(surface);
    }

    /**
     * 关闭悬浮框
     */
    private void dismassFloatWindow() {
        if (floatWindowIsShow) {
            floatWindowIsShow = false;
            windowManager.removeView(displayView);
        }
    }

    /**
     * 显示悬浮框
     */
    private void showFloatingWindow() {
        floatWindowIsShow = true;
        windowManager.addView(displayView, layoutParams);
    }

    /**
     * 悬浮框触摸移动监听
     */
    private class FloatingOnTouchListener implements View.OnTouchListener {
        private int x;
        private int y;

        @Override
        public boolean onTouch(View view, MotionEvent event) {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    if (System.currentTimeMillis() - lastClickTime < 200) {
                        setFloatWindowSize(view);
                    } else {
                        lastClickTime = System.currentTimeMillis();
                    }
                    x = (int) event.getRawX();
                    y = (int) event.getRawY();
                    break;
                case MotionEvent.ACTION_MOVE:
                    int nowx = (int) event.getRawX();
                    int nowy = (int) event.getRawY();
                    int movex = nowx - x;
                    int movey = nowy - y;
                    x = nowx;
                    y = nowy;
                    layoutParams.x = layoutParams.x + movex;
                    layoutParams.y = layoutParams.y + movey;
                    windowManager.updateViewLayout(view, layoutParams);
                    break;
                default:
                    break;
            }
            view.performClick();
            return true;
        }
    }

    private void setFloatWindowSize(View view) {
        switch (screenSize) {
            case 1:
                layoutParams.width = width / 2;
                layoutParams.height = height / 2;
                break;
            case 2:
                layoutParams.width = (int) (width * 0.7);
                layoutParams.height = (int) (height * 0.7);
                break;
            case 3:
                layoutParams.width = (int) (width * 0.8);
                layoutParams.height = (int) (height * 0.8);
                break;
            case 4:
                layoutParams.width = width;
                layoutParams.height = height;
                break;
            default:
                break;
        }
        screenSize++;
        if (screenSize == 5) {
            screenSize = 1;
        }
        windowManager.updateViewLayout(view, layoutParams);
    }

    /**
     * 捕捉屏幕
     */
    private void captureScreen() {
        //5.0 之后才允许使用屏幕截图
        if (mediaProjectionManager == null) {
            mediaProjectionManager = (MediaProjectionManager) getSystemService(
                    Context.MEDIA_PROJECTION_SERVICE);
        }
        if (null == mediaProjectionManager) {
            recordButton.setEnabled(false);
            Toast.makeText(this, "无法录屏，请确认授权录屏", Toast.LENGTH_SHORT).show();
            return;
        }
        startActivityForResult(mediaProjectionManager.createScreenCaptureIntent(),
                REQUEST_MEDIA_PROJECTION);
    }

    /**
     * 按键点击事件
     *
     * @param v 点击视图
     */
    @Override
    public void onClick(View v) {
        int id = v.getId();
        if (id == R.id.button) {
            if ("开启".equals(recordButton.getText().toString())) {
                recordButton.setEnabled(false);
                captureScreen();
            } else {
                recordButton.setEnabled(false);
                recordScreenServiceConnection.stopShare();
            }
        } else if (id == R.id.button2) {
            if ("播放".equals(playButton.getText().toString())) {
                playButton.setEnabled(false);
                String string = ipEditText.getText().toString();
                if (string.length() <= 0) {
                    EventBus.getDefault().post(new MessageEvent(IP_IS_NULL));
                } else {
                    socketClientThread = new SocketClientThread(string);
                    socketClientThread.start();
                }
            } else {
                updateClientState(false);
                socketClientThread.exit();
            }
        }
    }

    /**
     * 处理EventBUS事件
     *
     * @param messageEvent 事件对象
     */
    @Subscribe(threadMode = ThreadMode.MAIN)
    public void event(MessageEvent messageEvent) {
        switch (messageEvent.getWhat()) {
            case OPEN_FLOAT_WINDOW:
                showFloatingWindow();
                break;
            case IP_IS_NULL:
                updateClientState(false);
                Toast.makeText(this, getResources().getString(R.string.input_ip), Toast.LENGTH_SHORT).show();
                break;
            case CLIENT_CONNECT:
                Toast.makeText(this, getResources().getString(R.string.client_connect), Toast.LENGTH_SHORT).show();
                break;
            case CODEC_SERVER_RUN:
                Toast.makeText(this, getResources().getString(R.string.server_run), Toast.LENGTH_SHORT).show();
                break;
            case REQUEST_MEDIA_PROJECTION_FAIL:
                Toast.makeText(this, getResources().getString(R.string.request_media_projection_fail), Toast.LENGTH_SHORT).show();
                updateServerState(false);
                break;
            case MEDIA_PROJECTION_IS_NULL:
                Toast.makeText(this, getResources().getString(R.string.mediaprojection_is_null), Toast.LENGTH_SHORT).show();
                updateServerState(false);
                break;
            case SEND_DATA_ERROR:
                Toast.makeText(this, getResources().getString(R.string.send_data_error) + getResources().getString(R.string.wait_next_connect), Toast.LENGTH_SHORT).show();
                break;
            case CLIENT_SOCKET_CLOSE_ERROR:
                Toast.makeText(this, getResources().getString(R.string.socket_client_close_error) + getResources().getString(R.string.wait_next_connect), Toast.LENGTH_SHORT).show();
                break;
            case SOCKET_CLOSE:
                Toast.makeText(this, getResources().getString(R.string.socket_close), Toast.LENGTH_SHORT).show();
                break;
            case SERVER_SOCKET_CLOSE_ERROR:
                updateServerState(false);
                Toast.makeText(this, getResources().getString(R.string.server_socket_close_error), Toast.LENGTH_SHORT).show();
                break;
            case SERVER_SOCKET_CLOSE:
                updateServerState(false);
                Toast.makeText(this, getResources().getString(R.string.server_socket_close), Toast.LENGTH_SHORT).show();
                break;
            case SERVER_SOCKET_CREATE_ERROR:
                updateServerState(false);
                Toast.makeText(this, getResources().getString(R.string.server_socket_create_error), Toast.LENGTH_SHORT).show();
                break;
            case CODEC_CREATE_ERROE:
                Toast.makeText(this, getResources().getString(R.string.codec_create_error), Toast.LENGTH_SHORT).show();
                break;
            case CODEC_ERROR:
                updateServerState(false);
                Toast.makeText(this, getResources().getString(R.string.codec_error), Toast.LENGTH_SHORT).show();
                break;
            case WAIT_CONNECT:
                updateServerState(true);
                Toast.makeText(this, getResources().getString(R.string.wait_connect), Toast.LENGTH_SHORT).show();
                break;
            case CONNECT_SERVER_ERROR:
                updateClientState(false);
                Toast.makeText(this, getResources().getString(R.string.connect_server_error), Toast.LENGTH_SHORT).show();
                break;
            case CONNECT_SERVER:
                updateClientState(true);
                Toast.makeText(this, getResources().getString(R.string.connect_server), Toast.LENGTH_SHORT).show();
                break;
            case RECEIVE_DATA_ERROR:
                updateClientState(false);
                Toast.makeText(this, getResources().getString(R.string.receive_data_error), Toast.LENGTH_SHORT).show();
                break;
            case CLOSE_CLIENT:
                updateClientState(false);
                Toast.makeText(this, getResources().getString(R.string.close_client), Toast.LENGTH_SHORT).show();
                break;
            case SERVER_IS_NOT_OPEN:
                updateClientState(false);
                Toast.makeText(this, getResources().getString(R.string.server_is_not_open), Toast.LENGTH_SHORT).show();
                break;
            case LOST_PACK:
                Toast.makeText(this, messageEvent.getMessage(), Toast.LENGTH_SHORT).show();
                break;
            case ON_FRAME_FAIL:
                Toast.makeText(this, getResources().getString(R.string.on_frame_fail), Toast.LENGTH_SHORT).show();
                break;
            case CREATE_CODEC_EDCODER:
                updateClientState(false);
                Toast.makeText(this, getResources().getString(R.string.create_codec_decoder), Toast.LENGTH_SHORT).show();
                break;
            case CLIENT_CONNECT_ERROR:
                updateClientState(false);
                Toast.makeText(this, getResources().getString(R.string.client_connect_error) + getResources().getString(R.string.wait_next_connect), Toast.LENGTH_SHORT).show();
                break;
            case CREATE_OUTPUT_STREAM_ERROR:
                updateClientState(false);
                Toast.makeText(this, getResources().getString(R.string.create_output_stream_error) + getResources().getString(R.string.wait_next_connect), Toast.LENGTH_SHORT).show();
                break;
            default:
                Toast.makeText(this, getResources().getString(R.string.un_known), Toast.LENGTH_SHORT).show();
                break;
        }
    }

    private void updateClientState(boolean b) {
        if (b) {
            playButton.setText("停止");
            playButton.setEnabled(true);
        } else {
            dismassFloatWindow();
            playButton.setText("播放");
            playButton.setEnabled(true);
        }
    }

    private void updateServerState(boolean b) {
        if (b) {
            recordButton.setText("关闭");
            recordButton.setEnabled(true);
        } else {
            recordButton.setText("开启");
            recordButton.setEnabled(true);
        }
    }


    /**
     * 接收请求录屏服务反馈
     *
     * @param requestCode 请求码
     * @param resultCode  返回码
     * @param data        数据
     */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_MEDIA_PROJECTION) {
            if (resultCode == RESULT_OK && data != null) {
                recordScreenServiceConnection.startShare(resultCode, data);
            } else {
                EventBus.getDefault().post(new MessageEvent(REQUEST_MEDIA_PROJECTION_FAIL));
            }
        }

    }

    /**
     * 录屏服务连接
     */
    private class RecordScreenServiceConnection implements ServiceConnection {

        ArrayList<Runnable> tasks = new ArrayList<>();
        private boolean isConnect = false;

        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            myBinder = (RecordScreenService.MyBinder) service;
            isConnect = true;
            for (Runnable runnable : tasks) {
                runnable.run();
            }
            tasks.clear();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            isConnect = false;
        }

        private void startShare(int resultCode, Intent data) {
            if (isConnect) {
                myBinder.startShare(resultCode, data);
            } else {
                reConnect();
                tasks.add(() -> startShare(resultCode, data));
            }
        }

        private void stopShare() {
            if (isConnect) {
                myBinder.stopShare();
            } else {
                reConnect();
                tasks.add(this::stopShare);
            }
        }

        private void reConnect() {
            Intent intent = new Intent(MainActivity.this, RecordScreenService.class);
            bindService(intent, this, Context.BIND_AUTO_CREATE);
        }
    }

    @Override
    protected void onDestroy() {
        if (myBinder != null) {
            myBinder.stopShare();
        }
        if (recordScreenServiceConnection != null) {
            unbindService(recordScreenServiceConnection);
        }
        updateClientState(false);
        if (socketClientThread != null) {
            socketClientThread.exit();
        }
        if (EventBus.getDefault().isRegistered(this)) {
            EventBus.getDefault().unregister(this);
        }
        super.onDestroy();
    }
}
