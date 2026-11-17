package com.hugs.mimacrolauncher;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.Toast;

/**
 * 悬浮窗调起服务：游戏里常驻一个可拖动的“宏”小浮窗。
 * 点一下：用保存的游戏包名启动 MiMacro 服务；
 * 长按：停止 MiMacro 服务。
 * 本身以前台服务运行，避免被系统杀掉。
 */
public class FloatingService extends Service {

    private static final String CHANNEL_ID = "floating";
    private static final int NOTIF_ID = 2;
    private static final long LONG_PRESS_MS = 600;
    private static final int DRAG_THRESHOLD_DP = 10;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private WindowManager windowManager;
    private View floatView;
    private WindowManager.LayoutParams params;
    private Runnable longPressRunnable;
    private boolean dragged;
    private boolean longPressed;
    private int downX;
    private int downY;
    private int startX;
    private int startY;
    private int dragThresholdPx;

    @Override
    public void onCreate() {
        super.onCreate();
        dragThresholdPx = Math.round(
                DRAG_THRESHOLD_DP * getResources().getDisplayMetrics().density);

        NotificationManager nm = getSystemService(NotificationManager.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(
                    CHANNEL_ID, "悬浮窗", NotificationManager.IMPORTANCE_LOW);
            nm.createNotificationChannel(ch);
        }
        Notification notif = new Notification.Builder(this, CHANNEL_ID)
                .setContentTitle("MiMacro调起器")
                .setContentText("悬浮窗运行中：点一下启动宏，长按停止")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .build();
        startForeground(NOTIF_ID, notif);

        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "没有悬浮窗权限，悬浮窗无法显示", Toast.LENGTH_LONG).show();
            stopSelf();
            return;
        }
        showFloat();
    }

    @Override
    public void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        if (floatView != null && windowManager != null) {
            try {
                windowManager.removeView(floatView);
            } catch (IllegalArgumentException ignored) {
                // 视图已经不在了
            }
            floatView = null;
        }
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void showFloat() {
        windowManager = getSystemService(WindowManager.class);

        Button btn = new Button(this);
        btn.setText("宏");
        btn.setTextSize(16);
        btn.setMinWidth(0);
        btn.setMinimumWidth(0);
        btn.setPadding(28, 16, 28, 16);
        btn.setAlpha(0.85f);

        params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.START;
        params.x = 0;
        params.y = 300;

        // 手势全自己处理：点按启动、长按停止、拖动移位，三者互不干扰。
        // 注意 onTouch 返回 true 接管全部事件，按钮自身的点击/长按回调不用。
        btn.setOnTouchListener((v, e) -> {
            switch (e.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    dragged = false;
                    longPressed = false;
                    downX = (int) e.getRawX();
                    downY = (int) e.getRawY();
                    startX = params.x;
                    startY = params.y;
                    longPressRunnable = () -> {
                        longPressed = true;
                        v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                        stopMacro();
                    };
                    handler.postDelayed(longPressRunnable, LONG_PRESS_MS);
                    return true;
                case MotionEvent.ACTION_MOVE: {
                    int dx = (int) e.getRawX() - downX;
                    int dy = (int) e.getRawY() - downY;
                    if (Math.abs(dx) > dragThresholdPx || Math.abs(dy) > dragThresholdPx) {
                        if (!dragged) {
                            dragged = true;
                            handler.removeCallbacks(longPressRunnable);
                        }
                        params.x = startX + dx;
                        params.y = startY + dy;
                        windowManager.updateViewLayout(floatView, params);
                    }
                    return true;
                }
                case MotionEvent.ACTION_UP:
                    handler.removeCallbacks(longPressRunnable);
                    if (!dragged && !longPressed) {
                        startMacro();
                    }
                    return true;
                case MotionEvent.ACTION_CANCEL:
                    handler.removeCallbacks(longPressRunnable);
                    return true;
                default:
                    return false;
            }
        });

        floatView = btn;
        windowManager.addView(floatView, params);
    }

    private void startMacro() {
        String gamePkg = getSharedPreferences(MainActivity.PREFS, MODE_PRIVATE)
                .getString(MainActivity.KEY_GAME_PKG, "");
        if (gamePkg == null || gamePkg.isEmpty()) {
            Toast.makeText(this, "请先在启动器里填写游戏包名", Toast.LENGTH_LONG).show();
            return;
        }
        try {
            startForegroundService(MainActivity.macroIntent(gamePkg));
            Toast.makeText(this, "已发送启动请求", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, "启动失败：" + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void stopMacro() {
        try {
            stopService(MainActivity.macroIntent(null));
            Toast.makeText(this, "已发送停止请求", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, "停止失败：" + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }
}
