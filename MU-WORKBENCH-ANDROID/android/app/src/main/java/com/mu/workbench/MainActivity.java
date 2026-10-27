package com.mu.workbench;

import android.os.Bundle;
import android.webkit.CookieManager;
import android.webkit.WebView;

import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        WebView web = (this.bridge != null) ? this.bridge.getWebView() : null;

        // 信息门户只有 http，而 app 页面跑在 https://localhost。
        // 阅读页的正文图片交给 WebView 直接加载（内存由系统管，几十张长图也不卡），
        // 这在浏览器眼里是「跨站」请求：必须显式允许第三方 cookie，
        // 否则图片请求不带 JSESSIONID，服务器 302 回登录页，浏览器只能报 error。
        if (web != null) {
            CookieManager cm = CookieManager.getInstance();
            cm.setAcceptCookie(true);
            cm.setAcceptThirdPartyCookies(web, true);

            // 附件下载：门户的 download.jsp 要带门户会话，浏览器拿不到，
            // 只能由原生按同一份 cookie 取流再存到手机上（见 MuNative.java）。
            try {
                web.addJavascriptInterface(new MuNative(this, web), "MuNative");

                // 后台常驻：前台服务开关（见 KeepAliveService / MuKeepAlive）
                try {
                    web.addJavascriptInterface(new MuKeepAlive(this), "MuKeepAlive");

                // 后台检查新待办 / 新通知（见 MuBgCheck / BgCheck / BgCheckWorker）
                try {
                    web.addJavascriptInterface(new MuBgCheck(this), "MuBgCheck");
                } catch (Throwable ignored) {
                }
                } catch (Throwable ignored) {
                }
            } catch (Throwable ignored) {
                // 拿不到也不致命：网页里的下载按钮会退回「交给系统浏览器」
            }
        }
    }
}
