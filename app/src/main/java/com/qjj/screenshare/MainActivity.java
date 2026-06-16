package com.qjj.screenshare;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.graphics.Rect;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.SurfaceView;
import android.view.View;
import android.view.WindowManager;
import android.view.WindowMetrics;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

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
 * 主界面类
 * 负责权限申请、服务绑定、IP获取以及UI交互
 *
 * @author 曲建金
 */
public class MainActivity extends AppCompatActivity implements View.OnClickListener {

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

    // 新版 Activity 结果启动器
    private ActivityResultLauncher<Intent> mediaProjectionLauncher;
    private ActivityResultLauncher<Intent> overlayPermissionLauncher;

    private long lastClickTime = 0;
    private short screenSize = 2;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // 使用 modern 方式设置全屏
        WindowInsetsControllerCompat controller = WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        controller.hide(WindowInsetsCompat.Type.statusBars());
        controller.setSystemBarsBehavior(WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);

        EventBus.getDefault().register(this);

        initLaunchers(); // 初始化 Activity 结果监听
        getWidthAndHeight(); // 获取屏幕分辨率和 DPI

        // 初始化布局与监听
        initView();
        // 初始化服务
        initService();

        // 显示本机 IP 地址
        String ip = getHostIP();
        localIpTextView.setText(ip);
        ipEditText.setText(ip);

        checkNotificationPermission(); // 检查通知权限（Android 13+）
    }

    /**
     * 初始化 Activity 结果启动器
     */
    private void initLaunchers() {
        // 屏幕捕捉授权结果监听
        mediaProjectionLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        recordScreenServiceConnection.startShare(result.getResultCode(), result.getData());
                    } else {
                        EventBus.getDefault().post(new MessageEvent(REQUEST_MEDIA_PROJECTION_FAIL));
                    }
                }
        );

        // 悬浮窗权限结果监听
        overlayPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (!Settings.canDrawOverlays(this)) {
                        Toast.makeText(MainActivity.this, "未获得悬浮窗权限", Toast.LENGTH_SHORT).show();
                    }
                }
        );
    }

    /**
     * 获取屏幕的分辨率和 DPI 信息
     */
    private void getWidthAndHeight() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Android 11+ 使用 WindowMetrics
            WindowMetrics windowMetrics = getWindowManager().getCurrentWindowMetrics();
            Rect bounds = windowMetrics.getBounds();
            width = bounds.width();
            height = bounds.height();
            screenDpi = getResources().getConfiguration().densityDpi;
        } else {
            // 旧版本使用 DisplayMetrics
            WindowManager wm = (WindowManager) getApplicationContext().getSystemService(Context.WINDOW_SERVICE);
            DisplayMetrics dm = new DisplayMetrics();
            if (null != wm) {
                wm.getDefaultDisplay().getMetrics(dm);
                width = dm.widthPixels;
                height = dm.heightPixels;
                screenDpi = dm.densityDpi;
            }
        }
    }

    /**
     * 检查并请求通知权限
     */
    private void checkNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQUEST_POST_NOTIFICATIONS);
            }
        }
    }

    /**
     * 获取本机 IPv4 地址
     *
     * @return ip地址
     */
    public String getHostIP() {
        String hostIp = "";
        try {
            Enumeration<NetworkInterface> enumeration = NetworkInterface.getNetworkInterfaces();
            while (enumeration.hasMoreElements()) {
                NetworkInterface networkInterface = enumeration.nextElement();
                Enumeration<InetAddress> inetAddressEnumeration = networkInterface.getInetAddresses();
                while (inetAddressEnumeration.hasMoreElements()) {
                    InetAddress inetAddress = inetAddressEnumeration.nextElement();
                    if (inetAddress instanceof Inet6Address) {
                        continue; // 跳过 IPv6
                    }
                    String ip = inetAddress.getHostAddress();
                    if (!"127.0.0.1".equals(ip)) {
                        hostIp = ip;
                        break;
                    }
                }
            }
        } catch (SocketException e) {
            Log.e("MainActivity", "获取IP失败", e);
        }
        return hostIp;
    }

    /**
     * 初始化后台录屏服务
     */
    private void initService() {
        recordScreenServiceConnection = new RecordScreenServiceConnection();
        Intent intent = new Intent(this, RecordScreenService.class);
        bindService(intent, recordScreenServiceConnection, Context.BIND_AUTO_CREATE);
    }

    /**
     * 初始化视图控件
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
     * 初始化悬浮窗参数
     */
    @SuppressLint("InflateParams")
    private void initLayoutParams() {
        // 检查悬浮窗权限
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(MainActivity.this, "当前无权限，请授权", Toast.LENGTH_SHORT).show();
            overlayPermissionLauncher.launch(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName())));
        }

        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        layoutParams = new WindowManager.LayoutParams();
        layoutParams.type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
        layoutParams.flags = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
        layoutParams.width = width / 2;
        layoutParams.height = height / 2;
        layoutParams.x = 0;
        layoutParams.y = 0;

        LayoutInflater layoutInflater = LayoutInflater.from(this);
        // 悬浮窗没有父布局，此处传 null 是正常的
        displayView = layoutInflater.inflate(R.layout.float_display, null);
        displayView.setOnTouchListener(new FloatingOnTouchListener());

        SurfaceView surfaceView = displayView.findViewById(R.id.video_display_surfaceView);
        Surface surface = surfaceView.getHolder().getSurface();
        MyApplication.setSurface(surface);
    }

    /**
     * 关闭悬浮窗
     */
    private void dismissFloatWindow() {
        if (floatWindowIsShow) {
            floatWindowIsShow = false;
            windowManager.removeView(displayView);
        }
    }

    /**
     * 显示悬浮窗
     */
    private void showFloatingWindow() {
        if (!floatWindowIsShow) {
            floatWindowIsShow = true;
            windowManager.addView(displayView, layoutParams);
        }
    }

    /**
     * 悬浮窗触摸拖动监听
     */
    private class FloatingOnTouchListener implements View.OnTouchListener {
        private int x;
        private int y;

        @Override
        public boolean onTouch(View view, MotionEvent event) {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    if (System.currentTimeMillis() - lastClickTime < 200) {
                        setFloatWindowSize(view); // 双击切换大小
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
                    layoutParams.x += movex;
                    layoutParams.y += movey;
                    windowManager.updateViewLayout(view, layoutParams);
                    break;
                default:
                    break;
            }
            view.performClick();
            return true;
        }
    }

    /**
     * 循环切换悬浮窗显示比例
     */
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
                layoutParams.width = width / 3;
                layoutParams.height = height / 3;
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
     * 启动屏幕捕捉授权
     */
    private void captureScreen() {
        if (mediaProjectionManager == null) {
            mediaProjectionManager = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        }
        if (null == mediaProjectionManager) {
            recordButton.setEnabled(false);
            Toast.makeText(this, "无法录屏，请确认授权录屏", Toast.LENGTH_SHORT).show();
            return;
        }
        mediaProjectionLauncher.launch(mediaProjectionManager.createScreenCaptureIntent());
    }

    @Override
    public void onClick(View v) {
        int id = v.getId();
        if (id == R.id.button) {
            // 服务端：启动/停止 屏幕共享
            if ("开启".equals(recordButton.getText().toString())) {
                recordButton.setEnabled(false);
                captureScreen();
            } else {
                recordButton.setEnabled(false);
                recordScreenServiceConnection.stopShare();
            }
        } else if (id == R.id.button2) {
            // 客户端：连接/断开 远程屏幕
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
                if (socketClientThread != null) {
                    socketClientThread.exit();
                }
            }
        }
    }

    /**
     * 处理 EventBus 事件回调（UI 线程）
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
            dismissFloatWindow();
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
     * 录屏服务连接回调
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
