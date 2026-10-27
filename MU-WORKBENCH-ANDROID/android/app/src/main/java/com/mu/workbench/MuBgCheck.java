package com.mu.workbench;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.webkit.JavascriptInterface;

/**
 * 后台检查新内容的 JS 桥，挂在 window.MuBgCheck 上。
 *
 * 为什么要在原生侧做：WebView 的 JS 定时器在后台会被 Chromium 节流（1 分钟一次起步，
 * 久了更低），进程也可能被回收，所以「每 15 分钟去看一眼学校服务器」只能交给
 * WorkManager + 原生 HTTP。
 *
 * 为什么不重新登录：云邮走学校统一身份认证（CAS），每 15 分钟登录一次几乎必然触发
 * 风控 / 验证码甚至锁号。所以这里只复用 App 里已经登录成功后的 access_token 和
 * refresh_token（refresh_token 换新令牌不走密码、也不计登录失败次数），
 * 换不动了才提示用户「打开 App 一次」。
 *
 * JS 侧用法：
 *   MuBgCheck.push(json)                  保存最新快照（token / cookie / 已见 id）
 *   MuBgCheck.enable(uc, pt, wifiOnly)    开启后台检查并排期末任务
 *   MuBgCheck.disable()                   关闭并取消
 *   MuBgCheck.status()                    诊断信息（字符串）
 *   MuBgCheck.testNow()                   立刻跑一次（结果以通知呈现）
 *   MuBgCheck.version()                   能力探测
 */
public class MuBgCheck {

    private final Activity act;

    MuBgCheck(Activity act) {
        this.act = act;
    }

    @JavascriptInterface
    public String version() {
        return "1";
    }

    /** 保存快照。字段与 BgCheck.Cfg 对应，缺的字段保持原值。 */
    @JavascriptInterface
    public void push(String json) {
        try {
            BgCheck.store(act, json);
        } catch (Throwable ignored) {
        }
    }

    @JavascriptInterface
    public void enable(boolean uc, boolean pt, boolean wifiOnly) {
        try {
            BgCheck.setEnabled(act, true, uc, pt, wifiOnly);
        } catch (Throwable ignored) {
        }
    }

    @JavascriptInterface
    public void disable() {
        try {
            BgCheck.setEnabled(act, false, false, false, false);
        } catch (Throwable ignored) {
        }
    }

    @JavascriptInterface
    public String status() {
        try {
            return BgCheck.statusText(act);
        } catch (Throwable e) {
            return "读取状态失败：" + e.getMessage();
        }
    }

    /** 立刻检查一次。跑在后台线程里，结果用通知呈现（没新内容也会给一条「检查完成」）。 */
    @JavascriptInterface
    public String testNow() {
        try {
            final Context ctx = act.getApplicationContext();
            new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        BgCheck.run(ctx, true);
                    } catch (Throwable ignored) {
                    }
                }
            }, "mu-bg-test").start();
            return "已开始检查，稍后会有一条通知";
        } catch (Throwable e) {
            return "没能开始检查：" + e.getMessage();
        }
    }

    /** 让系统打开本 App 的通知设置（用户把通知关掉了时用） */
    @JavascriptInterface
    public void openNotificationSettings() {
        try {
            BgCheck.openNotificationSettings(act);
        } catch (Throwable ignored) {
        }
    }

    static SharedPreferences prefs(Context ctx) {
        return ctx.getSharedPreferences("mu_bg_check", Context.MODE_PRIVATE);
    }
}
