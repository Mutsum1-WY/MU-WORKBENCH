package com.mu.workbench;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;

/**
 * 后台常驻的前台服务。
 *
 * 为什么需要它：切屏 / 锁屏后，Android 会把 WebView 的定时器降频（Chrome 的后台节流
 * 是 1 秒一次，5 分钟后进一步降到 1 分钟一次），时间再久进程还可能被回收。
 * 起一个前台服务后：
 *   · 进程优先级提高，日常「清理后台」不会随手把它清掉；
 *   · 通知栏有一条常驻通知，用户随时看得见它还在跑。
 *
 * 注意：前台服务**不能**让 WebView 的 JS 保持全速运行（那是浏览器层的节流，管不到）。
 * 所以计时功能在 JS 侧一律改成「按结束时刻算」（见 pomoSync / tmSync），
 * 这样即便 JS 被冻结，切回来时间仍然是准的；结束提醒另外由系统通知兜底。
 */
public class KeepAliveService extends Service {

    public static final String ACTION_START = "com.mu.workbench.KA_START";
    public static final String ACTION_STOP = "com.mu.workbench.KA_STOP";
    public static final String EXTRA_TITLE = "title";
    public static final String EXTRA_TEXT = "text";

    private static final String CHANNEL_ID = "mu-keepalive";
    private static final int NOTIF_ID = 4200;

    private String title = "MU WORKBENCH 正在后台运行";
    private String text = "";

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            if (ACTION_STOP.equals(intent.getAction())) {
                stopForegroundCompat();
                stopSelf();
                return START_NOT_STICKY;
            }
            String t = intent.getStringExtra(EXTRA_TITLE);
            String x = intent.getStringExtra(EXTRA_TEXT);
            if (t != null && !t.isEmpty()) title = t;
            if (x != null) text = x;
        }
        startFg();
        /* 被杀后让系统重建：常驻的意义就在于「不轻易没」 */
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        stopForegroundCompat();
        super.onDestroy();
    }

    private void stopForegroundCompat() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_REMOVE);
            } else {
                stopForeground(true);
            }
        } catch (Throwable ignored) {
        }
    }

    private void ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        try {
            NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            if (nm == null) return;
            if (nm.getNotificationChannel(CHANNEL_ID) != null) return;
            NotificationChannel ch = new NotificationChannel(CHANNEL_ID, "后台常驻", NotificationManager.IMPORTANCE_LOW);
            ch.setDescription("让 MU WORKBENCH 切屏后继续运行（计时、提醒）");
            ch.setShowBadge(false);
            ch.setSound(null, null);
            ch.enableVibration(false);
            nm.createNotificationChannel(ch);
        } catch (Throwable ignored) {
        }
    }

    private void startFg() {
        ensureChannel();
        Notification.Builder b;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            b = new Notification.Builder(this, CHANNEL_ID);
        } else {
            b = new Notification.Builder(this);
        }
        Intent open = new Intent(this, MainActivity.class);
        open.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        int piFlags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) piFlags |= PendingIntent.FLAG_IMMUTABLE;
        PendingIntent pi = PendingIntent.getActivity(this, 0, open, piFlags);

        b.setContentTitle(title)
                .setContentText(text)
                .setSmallIcon(android.R.drawable.stat_notify_sync)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setShowWhen(false)
                .setContentIntent(pi);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            b.setVisibility(Notification.VISIBILITY_PUBLIC);
        }

        Notification n = b.build();
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                /* Android 14+ 必须声明前台服务类型，否则直接抛异常 */
                startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
            } else {
                startForeground(NOTIF_ID, n);
            }
        } catch (Throwable e) {
            try {
                startForeground(NOTIF_ID, n);
            } catch (Throwable ignored) {
            }
        }
    }
}
