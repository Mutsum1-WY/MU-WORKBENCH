package com.mu.workbench;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * 后台检查原生控制（供 Jetpack Compose 直接调用）。
 */
public class MuBgCheck {

    private final Context ctx;

    public MuBgCheck(Context ctx) {
        this.ctx = ctx.getApplicationContext();
    }

    public void push(String json) {
        try {
            BgCheck.store(ctx, json);
        } catch (Throwable ignored) {
        }
    }

    public void enable(boolean uc, boolean pt, boolean wifiOnly) {
        try {
            BgCheck.setEnabled(ctx, true, uc, pt, wifiOnly);
        } catch (Throwable ignored) {
        }
    }

    public void disable() {
        try {
            BgCheck.setEnabled(ctx, false, false, false, false);
        } catch (Throwable ignored) {
        }
    }

    public String status() {
        try {
            return BgCheck.statusText(ctx);
        } catch (Throwable e) {
            return "读取状态失败：" + e.getMessage();
        }
    }

    public String testNow() {
        try {
            new Thread(() -> {
                try {
                    BgCheck.run(ctx, true);
                } catch (Throwable ignored) {
                }
            }, "mu-bg-test").start();
            return "已开始检查，请留意状态更新或新内容通知";
        } catch (Throwable e) {
            return "没能开始检查：" + e.getMessage();
        }
    }

    public void openNotificationSettings() {
        try {
            BgCheck.openNotificationSettings(ctx);
        } catch (Throwable ignored) {
        }
    }

    static SharedPreferences prefs(Context ctx) {
        return ctx.getSharedPreferences("mu_bg_check", Context.MODE_PRIVATE);
    }
}
