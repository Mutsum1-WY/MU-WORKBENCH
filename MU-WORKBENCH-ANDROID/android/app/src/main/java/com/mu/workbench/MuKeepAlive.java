package com.mu.workbench;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.PowerManager;
import android.provider.Settings;

/**
 * 后台常驻原生控制（供 Jetpack Compose 直接调用）。
 */
public class MuKeepAlive {

    private final Context ctx;

    public MuKeepAlive(Context ctx) {
        this.ctx = ctx.getApplicationContext();
    }

    public void start(final String title, final String text) {
        try {
            Intent i = new Intent(ctx, KeepAliveService.class);
            i.setAction(KeepAliveService.ACTION_START);
            i.putExtra(KeepAliveService.EXTRA_TITLE, title == null ? "" : title);
            i.putExtra(KeepAliveService.EXTRA_TEXT, text == null ? "" : text);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ctx.startForegroundService(i);
            } else {
                ctx.startService(i);
            }
        } catch (Throwable ignored) {
        }
    }

    public void stop() {
        try {
            Intent i = new Intent(ctx, KeepAliveService.class);
            i.setAction(KeepAliveService.ACTION_STOP);
            ctx.startService(i);
        } catch (Throwable ignored) {
        }
    }

    public boolean isIgnoringBatteryOptimizations() {
        try {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true;
            PowerManager pm = (PowerManager) ctx.getSystemService(Context.POWER_SERVICE);
            return pm != null && pm.isIgnoringBatteryOptimizations(ctx.getPackageName());
        } catch (Throwable e) {
            return true;
        }
    }

    public void requestIgnoreBatteryOptimizations() {
        try {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return;
            Intent i = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
            i.setData(Uri.parse("package:" + ctx.getPackageName()));
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            ctx.startActivity(i);
        } catch (Throwable e) {
            try {
                Intent i = new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS);
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                ctx.startActivity(i);
            } catch (Throwable ignored) {
            }
        }
    }
}
