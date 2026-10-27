package com.mu.workbench;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;
import android.webkit.JavascriptInterface;
import android.webkit.MimeTypeMap;
import android.webkit.WebView;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLDecoder;

/**
 * 附件下载的原生桥，挂在 window.MuNative 上。
 *
 * 为什么必须原生做：门户的附件在 download.jsp 后面，要带着门户会话（JSESSIONID）才给文件，
 * 而且会 302 到 CAS。WebView 里直接跳只会被弹回登录页，系统浏览器更没有我们的会话。
 * 所以这里用 JS 传进来的 cookie 自己取流，存到手机「下载/MU WORKBENCH」，
 * 存完再用系统应用打开（FileProvider / MediaStore content URI）。
 *
 * 只做下载与打开，不碰任何提交类接口。
 */
public class MuNative {

    private static final String TAG = "MuNative";
    private static final String FOLDER = "MU WORKBENCH";
    private static final String UA =
            "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) "
            + "Chrome/120.0.0.0 Mobile Safari/537.36";

    private final Activity act;
    private final WebView web;

    MuNative(Activity act, WebView web) {
        this.act = act;
        this.web = web;
    }

    /* ---------------- 给 JS 的接口 ---------------- */

    /** 下载一个附件；结果通过 window.__muDl(json) 回调。referer 用来过门户的防盗链。 */
    @JavascriptInterface
    public void download(final String id, final String url, final String cookie, final String name,
                         final String referer) {
        if (id == null || url == null || url.isEmpty()) return;
        Thread t = new Thread(() -> {
            try {
                fetch(id, url, cookie, name, referer);
            } catch (Throwable e) {
                fail(id, msgOf(e));
            }
        }, "mu-download");
        t.setDaemon(true);
        t.start();
    }

    /** 打开已经下载好的文件（uri 来自 download 的回调）。 */
    @JavascriptInterface
    public void open(String uri, String mime) {
        try {
            if (uri == null || uri.isEmpty()) return;
            Uri u = Uri.parse(uri);
            String type = (mime == null || mime.isEmpty()) ? guessMime(uri) : mime;
            Intent i = new Intent(Intent.ACTION_VIEW);
            i.setDataAndType(u, type);
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
            act.startActivity(i);
        } catch (ActivityNotFoundException e) {
            toast("手机上没装能打开这种文件的软件，文件已存到 下载/" + FOLDER);
        } catch (Throwable e) {
            toast("打不开这个文件：" + msgOf(e));
        }
    }

    /** 版本标记：JS 侧用来判断这个包有没有下载能力。 */
    @JavascriptInterface
    public String version() {
        return "1";
    }

    /* ---------------- 下载实现 ---------------- */

    private void fetch(String id, String url, String cookie, String name, String referer) throws Exception {
        String fileName = safeName(name);
        HttpURLConnection conn = null;
        String cur = url;

        for (int hop = 0; hop < 6; hop++) {
            URL u = new URL(cur);
            conn = (HttpURLConnection) u.openConnection();
            conn.setInstanceFollowRedirects(false);
            conn.setConnectTimeout(20000);
            conn.setReadTimeout(45000);
            conn.setRequestProperty("User-Agent", UA);
            conn.setRequestProperty("Accept", "*/*");
            conn.setRequestProperty("Accept-Encoding", "identity");
            if (cookie != null && !cookie.isEmpty()) conn.setRequestProperty("Cookie", cookie);
            if (referer != null && !referer.isEmpty()) conn.setRequestProperty("Referer", referer);

            int code = conn.getResponseCode();
            if (code == 301 || code == 302 || code == 303 || code == 307 || code == 308) {
                String loc = conn.getHeaderField("Location");
                conn.disconnect();
                conn = null;
                if (loc == null) throw new Exception("门户没有给出跳转地址");
                cur = new URL(u, loc).toString();
                if (cur.contains("authserver")) throw new Exception("门户会话过期了，回资讯页刷新一下再试");
                continue;
            }
            if (code != 200) throw new Exception("门户返回 HTTP " + code);

            /* 302 到登录页但状态码被抹成 200 的情况：附件不可能是 HTML */
            String ctype = conn.getContentType();
            if (ctype != null && ctype.toLowerCase().startsWith("text/html"))
                throw new Exception("门户给的是网页不是文件，会话可能过期了");

            String cd = conn.getHeaderField("Content-Disposition");
            if (cd != null) {
                String fromHeader = nameFromDisposition(cd);
                if (fromHeader != null && !fromHeader.isEmpty()
                        && (fileName == null || fileName.isEmpty() || !hasExt(fileName))) {
                    fileName = safeName(fromHeader);
                }
            }
            break;
        }
        if (conn == null) throw new Exception("下载失败：没有拿到文件流");
        if (fileName == null || fileName.isEmpty()) fileName = "附件";

        long total = conn.getContentLength();
        InputStream in = conn.getInputStream();
        OutputStream out = null;
        Uri target = null;
        File plain = null;

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ContentResolver cr = act.getContentResolver();
                ContentValues cv = new ContentValues();
                cv.put(MediaStore.Downloads.DISPLAY_NAME, fileName);
                cv.put(MediaStore.Downloads.MIME_TYPE, guessMime(fileName));
                cv.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/" + FOLDER);
                cv.put(MediaStore.Downloads.IS_PENDING, 1);
                target = cr.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv);
                if (target == null) throw new Exception("手机不允许写入下载目录");
                out = cr.openOutputStream(target);
            } else {
                File dir = new File(act.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), FOLDER);
                if (!dir.exists() && !dir.mkdirs()) throw new Exception("建不了下载目录");
                plain = unique(dir, fileName);
                out = new FileOutputStream(plain);
            }
            if (out == null) throw new Exception("打不开写入通道");

            byte[] buf = new byte[32 * 1024];
            long done = 0;
            int n, lastPct = -1;
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
                done += n;
                if (total > 0) {
                    int pct = (int) (done * 100 / total);
                    if (pct / 10 != lastPct / 10) { lastPct = pct; progress(id, pct); }
                }
            }
            out.flush();
            out.close();
            out = null;

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ContentValues cv = new ContentValues();
                cv.put(MediaStore.Downloads.IS_PENDING, 0);
                act.getContentResolver().update(target, cv, null, null);
                done(id, target.toString(), fileName);
            } else {
                Uri u = FileProvider.getUriForFile(act, act.getPackageName() + ".fileprovider", plain);
                done(id, u.toString(), fileName);
            }
        } catch (Throwable e) {
            /* 失败别在下载目录里留半截文件 */
            if (target != null) {
                try { act.getContentResolver().delete(target, null, null); } catch (Throwable ignored) { }
            }
            if (plain != null && plain.exists()) {
                try { plain.delete(); } catch (Throwable ignored) { }
            }
            if (e instanceof Exception) throw (Exception) e;
            throw new Exception(msgOf(e));
        } finally {
            try { in.close(); } catch (Throwable ignored) { }
            if (out != null) try { out.close(); } catch (Throwable ignored) { }
            conn.disconnect();
        }
    }

    /* ---------------- 工具 ---------------- */

    private void progress(String id, int pct) {
        try {
            JSONObject o = new JSONObject();
            o.put("id", id);
            o.put("act", "progress");
            o.put("pct", pct);
            emit(o);
        } catch (Throwable ignored) { }
    }

    private void done(String id, String uri, String name) {
        try {
            JSONObject o = new JSONObject();
            o.put("id", id);
            o.put("ok", true);
            o.put("uri", uri);
            o.put("name", name);
            emit(o);
        } catch (Throwable ignored) { }
    }

    private void fail(String id, String msg) {
        try {
            JSONObject o = new JSONObject();
            o.put("id", id);
            o.put("ok", false);
            o.put("msg", msg == null ? "下载失败" : msg);
            emit(o);
        } catch (Throwable ignored) { }
    }

    private void emit(JSONObject o) {
        final String js = "window.__muDl&&window.__muDl(" + JSONObject.quote(o.toString()) + ");";
        web.post(() -> {
            try { web.evaluateJavascript(js, null); } catch (Throwable ignored) { }
        });
    }

    private void toast(final String msg) {
        act.runOnUiThread(() -> {
            try { Toast.makeText(act, msg, Toast.LENGTH_LONG).show(); } catch (Throwable ignored) { }
        });
    }

    private static String msgOf(Throwable e) {
        String s = e == null ? "" : e.getMessage();
        if (s == null || s.isEmpty()) s = e == null ? "下载失败" : e.getClass().getSimpleName();
        if (s.contains("Unable to resolve host") || s.contains("Failed to connect")
                || s.contains("timed out") || s.contains("timeout")) {
            return "连不上门户，检查一下网络再试";
        }
        return s;
    }

    private static boolean hasExt(String n) {
        int dot = n.lastIndexOf('.');
        return dot > 0 && n.length() - dot <= 6;
    }

    private static String safeName(String raw) {
        String n = raw == null ? "" : raw.trim();
        n = n.replaceAll("[\\\\/:*?\"<>|\\r\\n\\t]", "_");
        n = n.replaceAll("\\s+", " ").trim();
        if (n.isEmpty()) n = "附件";
        if (n.length() > 90) {
            int dot = n.lastIndexOf('.');
            String ext = (dot > 0 && n.length() - dot <= 6) ? n.substring(dot) : "";
            n = n.substring(0, Math.max(1, 90 - ext.length())) + ext;
        }
        return n;
    }

    private static String nameFromDisposition(String cd) {
        try {
            String star = null, plain = null;
            String[] parts = cd.split(";");
            for (String p : parts) {
                String s = p.trim();
                if (s.regionMatches(true, 0, "filename*=", 0, 10)) star = s.substring(10);
                else if (s.regionMatches(true, 0, "filename=", 0, 9)) plain = s.substring(9);
            }
            if (star != null) {
                int q = star.indexOf("''");
                String enc = (q >= 0) ? star.substring(q + 2) : star;
                return URLDecoder.decode(enc.replace("\"", ""), "UTF-8");
            }
            if (plain != null) {
                String v = plain.trim();
                if (v.startsWith("\"") && v.endsWith("\"") && v.length() > 1) v = v.substring(1, v.length() - 1);
                try { return URLDecoder.decode(v, "UTF-8"); } catch (Throwable e) { return v; }
            }
        } catch (Throwable e) {
            Log.w(TAG, "Content-Disposition 解析失败", e);
        }
        return null;
    }

    private static File unique(File dir, String name) {
        File f = new File(dir, name);
        if (!f.exists()) return f;
        int dot = name.lastIndexOf('.');
        String base = dot > 0 ? name.substring(0, dot) : name;
        String ext = dot > 0 ? name.substring(dot) : "";
        for (int i = 1; i < 500; i++) {
            File g = new File(dir, base + " (" + i + ")" + ext);
            if (!g.exists()) return g;
        }
        return new File(dir, base + "-" + System.currentTimeMillis() + ext);
    }

    private static String guessMime(String nameOrUri) {
        String n = nameOrUri == null ? "" : nameOrUri;
        int dot = n.lastIndexOf('.');
        if (dot > 0 && n.length() - dot <= 6) {
            String ext = n.substring(dot + 1).toLowerCase();
            String m = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext);
            if (m != null) return m;
        }
        return "application/octet-stream";
    }
}
