package com.mu.workbench;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.PowerManager;
import android.provider.Settings;
import android.webkit.JavascriptInterface;

/**
 * 后台常驻的 JS 桥，挂在 window.MuKeepAlive 上。
 *
 * JS 侧用法：
 *   MuKeepAlive.start(title, text)   开前台服务（重复调用 = 更新通知文案）
 *   MuKeepAlive.stop()               关服务
 *   MuKeepAlive.isIgnoringBatteryOptimizations()   是否已在电池优化白名单
 *   MuKeepAlive.requestIgnoreBatteryOptimizations() 系统弹窗申请白名单
 *   MuKeepAlive.version()            能力探测
 */
public class MuKeepAlive {

    private final Activity act;

    MuKeepAlive(Activity act) {
        this.act = act;
    }

    @JavascriptInterface
    public String version() {
        return "1";
    }

    @JavascriptInterface
    public void start(final String title, final String text) {
        try {
            Intent i = new Intent(act, KeepAliveService.class);
            i.setAction(KeepAliveService.ACTION_START);
            i.putExtra(KeepAliveService.EXTRA_TITLE, title == null ? "" : title);
            i.putExtra(KeepAliveService.EXTRA_TEXT, text == null ? "" : text);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                act.startForegroundService(i);
            } else {
                act.startService(i);
            }
        } catch (Throwable ignored) {
            /* 系统不让起，就退回纯前台运行；JS 侧计时本来就是按时间戳算的，不影响准确性 */
        }
    }

    @JavascriptInterface
    public void stop() {
        try {
            Intent i = new Intent(act, KeepAliveService.class);
            i.setAction(KeepAliveService.ACTION_STOP);
            act.startService(i);
        } catch (Throwable ignored) {
        }
    }

    @JavascriptInterface
    public boolean isIgnoringBatteryOptimizations() {
        try {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true;
            PowerManager pm = (PowerManager) act.getSystemService(Context.POWER_SERVICE);
            return pm != null && pm.isIgnoringBatteryOptimizations(act.getPackageName());
        } catch (Throwable e) {
            return true;
        }
    }

    @JavascriptInterface
    public void requestIgnoreBatteryOptimizations() {
        try {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return;
            Intent i = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
            i.setData(Uri.parse("package:" + act.getPackageName()));
            act.startActivity(i);
        } catch (Throwable e) {
            /* 有些国产 ROM 没有这个直连 Action，退回列表页让用户自己找 */
            try {
                act.startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
            } catch (Throwable ignored) {
            }
        }
    }
}
