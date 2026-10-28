package com.mu.workbench;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.util.Log;

import androidx.work.Constraints;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 后台检查新待办 / 新通知。
 *
 * 设计前提（很重要）：
 *   1. 云邮走学校统一认证，**绝不能**在后台反复登录 —— 会触发风控/验证码甚至锁号。
 *      所以这里只用 App 登录成功后交给原生的 access_token，过期了用 refresh_token 换新的。
 *   2. 门户（校内通知/讲座）没有 refresh 机制，只能复用会话 cookie；cookie 过期就降级成
 *      「打开 App 同步一次」的提示，不做任何自动重登。
 *   3. 整个流程不接触学工号密码，凭据只留在 WebView 的 localStorage 与这里的 token 里。
 */
public final class BgCheck {

    /* ---------- 常量（与 index.html 里的 UC_* / PT_* 保持一致） ---------- */
    private static final String UC_API = "https://apiucloud.bupt.edu.cn";
    private static final String UC_BASIC = "Basic c3dvcmQ6c3dvcmRfc2VjcmV0";   /* sword:sword_secret */
    private static final String PT_NOTICE = "http://my.bupt.edu.cn/list.jsp?urltype=tree.TreeTempUrl&wbtreeid=1154";
    private static final String PT_LECTURE = "http://my.bupt.edu.cn/hy_list.jsp?urltype=tree.TreeTempUrl&wbtreeid=1300";
    private static final String PT_BASE = "http://my.bupt.edu.cn/";

    private static final String WORK_NAME = "mu-bg-check";
    private static final String CHANNEL_ID = "mu-new";
    private static final String TAG = "MuBgCheck";
    private static final String PT_HOST = "my.bupt.edu.cn";
    private static final long TOKEN_TTL = 100L * 60 * 1000;      /* 令牌 2 小时，留 20 分钟余量 */
    private static final int SEEN_CAP = 400;

    private static final int N_UC = 4101;
    private static final int N_PT = 4102;
    private static final int N_TEST = 4103;

    private BgCheck() {
    }

    /* ==================== 配置读写 ==================== */

    /** 把 JS 传来的快照合并进本地配置（只覆盖传了的字段；已见 id 做并集，避免重复通知） */
    static void store(Context ctx, String json) throws Exception {
        JSONObject in = new JSONObject(json == null ? "{}" : json);
        SharedPreferences sp = MuBgCheck.prefs(ctx);
        JSONObject cfg = readCfg(sp);

        if (in.has("ucOn")) cfg.put("ucOn", in.optBoolean("ucOn", true));
        if (in.has("ptOn")) cfg.put("ptOn", in.optBoolean("ptOn", true));
        if (in.has("wifiOnly")) cfg.put("wifiOnly", in.optBoolean("wifiOnly", false));

        JSONObject ucIn = in.optJSONObject("uc");
        if (ucIn != null) {
            JSONObject uc = cfg.optJSONObject("uc");
            if (uc == null) uc = new JSONObject();
            mergeStr(uc, ucIn, "token");
            mergeStr(uc, ucIn, "refresh");
            mergeStr(uc, ucIn, "uid");
            mergeStr(uc, ucIn, "identity");
            mergeStr(uc, ucIn, "roleId");
            if (ucIn.has("tokenAt")) uc.put("tokenAt", ucIn.optLong("tokenAt", 0L));
            mergeIds(uc, ucIn, "seen");
            cfg.put("uc", uc);
        }

        JSONObject ptIn = in.optJSONObject("pt");
        if (ptIn != null) {
            JSONObject pt = cfg.optJSONObject("pt");
            if (pt == null) pt = new JSONObject();
            mergeStr(pt, ptIn, "cookie");
            mergeIds(pt, ptIn, "noticeSeen");
            mergeIds(pt, ptIn, "lectureSeen");
            cfg.put("pt", pt);
        }

        sp.edit().putString("cfg", cfg.toString()).apply();
    }

    private static void mergeStr(JSONObject dst, JSONObject src, String key) {
        String v = src.optString(key, null);
        if (v != null) {
            try {
                dst.put(key, v);
            } catch (Exception ignored) {
            }
        }
    }

    /** 数组字段做并集并截断，避免无限增长 */
    private static void mergeIds(JSONObject dst, JSONObject src, String key) {
        JSONArray add = src.optJSONArray(key);
        if (add == null) return;
        Set<String> set = new LinkedHashSet<>();
        JSONArray old = dst.optJSONArray(key);
        if (old != null) {
            for (int i = 0; i < old.length(); i++) {
                String s = old.optString(i, "");
                if (!s.isEmpty()) set.add(s);
            }
        }
        for (int i = 0; i < add.length(); i++) {
            String s = add.optString(i, "");
            if (!s.isEmpty()) set.add(s);
        }
        List<String> keep = new ArrayList<>(set);
        if (keep.size() > SEEN_CAP) keep = keep.subList(keep.size() - SEEN_CAP, keep.size());
        JSONArray out = new JSONArray();
        for (String s : keep) out.put(s);
        try {
            dst.put(key, out);
        } catch (Exception ignored) {
        }
    }

    private static JSONObject readCfg(SharedPreferences sp) {
        try {
            return new JSONObject(sp.getString("cfg", "{}"));
        } catch (Exception e) {
            return new JSONObject();
        }
    }

    /** 开关：同时负责排期 / 取消 WorkManager */
    static void setEnabled(Context ctx, boolean on, boolean uc, boolean pt, boolean wifiOnly) throws Exception {
        SharedPreferences sp = MuBgCheck.prefs(ctx);
        JSONObject cfg = readCfg(sp);
        cfg.put("enabled", on);
        cfg.put("ucOn", uc);
        cfg.put("ptOn", pt);
        cfg.put("wifiOnly", wifiOnly);
        sp.edit().putString("cfg", cfg.toString()).apply();

        WorkManager wm = WorkManager.getInstance(ctx);
        if (!on) {
            wm.cancelUniqueWork(WORK_NAME);
            wm.cancelUniqueWork(WORK_NAME + "-legacy");
            return;
        }
        /* 旧版本用的是 PeriodicWorkRequest，清掉，避免两套并存 */
        wm.cancelUniqueWork(WORK_NAME + "-legacy");
        scheduleNext(ctx, 15);
    }

    /**
     * 排下一次检查。
     *
     * 为什么不用 PeriodicWorkRequest：实测（Redmi + Android 13）发现这个 App 的进程会被
     * 前台服务反复拉起，而 WorkManager 每次重新调度周期任务都会把「下次执行时间」重置，
     * 于是 `TIMING_DELAY` 永远满足不了 —— dumpsys 里能看到 `Enqueue time` 一直在往后滚，
     * 15 分钟窗口永远走不完，任务一次都没真正跑过。
     * 改成「一次性任务 + 跑完自己排下一次」，每次调度只带固定的 initialDelay，
     * 不依赖周期任务的周期状态，实测能正常触发。
     */
    static void scheduleNext(Context ctx, long delayMinutes) {
        try {
            JSONObject cfg = readCfg(MuBgCheck.prefs(ctx));
            boolean wifiOnly = cfg.optBoolean("wifiOnly", false);
            Constraints c = new Constraints.Builder()
                    .setRequiredNetworkType(wifiOnly ? NetworkType.UNMETERED : NetworkType.CONNECTED)
                    .setRequiresBatteryNotLow(true)
                    .build();
            OneTimeWorkRequest req = new OneTimeWorkRequest.Builder(BgCheckWorker.class)
                    .setInitialDelay(delayMinutes, TimeUnit.MINUTES)
                    .setConstraints(c)
                    .addTag("mu-bg")
                    .build();
            WorkManager.getInstance(ctx)
                    .enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.REPLACE, req);
            Log.i(TAG, "scheduleNext in " + delayMinutes + " min");
        } catch (Throwable e) {
            Log.w(TAG, "scheduleNext failed: " + e);
        }
    }

    /** 实际间隔（分钟）：默认 15，可被 cfg.intervalMin 覆盖（调试/省电都方便），夹在 1~240 */
    static long intervalMinutes(Context ctx) {
        int m = readCfg(MuBgCheck.prefs(ctx)).optInt("intervalMin", 15);
        if (m < 1) m = 1;
        if (m > 240) m = 240;
        return m;
    }

    static boolean enabled(Context ctx) {
        return readCfg(MuBgCheck.prefs(ctx)).optBoolean("enabled", false);
    }

    static String statusText(Context ctx) {
        JSONObject cfg = readCfg(MuBgCheck.prefs(ctx));
        long last = cfg.optLong("lastRun", 0L);
        String res = cfg.optString("lastResult", "还没跑过");
        String diag = cfg.optString("lastDiag", "");
        int fails = cfg.optInt("fails", 0);
        StringBuilder sb = new StringBuilder();
        sb.append(cfg.optBoolean("enabled", false) ? "后台检查：开" : "后台检查：关");
        sb.append("\n上次检查：").append(last > 0 ? fmt(last) : "还没跑过");
        sb.append("\n上次结果：").append(res);
        if (fails > 0) sb.append("\n连续失败：").append(fails).append(" 次");
        JSONObject uc = cfg.optJSONObject("uc");
        sb.append("\n云邮令牌：").append(uc != null && uc.optLong("tokenAt", 0) > 0
                ? (fresh(uc) ? "有效" : "已过期（会用 refresh 续）") : "没有（先在今日页登录）");
        JSONObject pt = cfg.optJSONObject("pt");
        int ptN = pt == null ? 0 : countJarHost(pt, PT_HOST);
        sb.append("\n门户会话：").append(ptN > 0
                ? ("有（" + ptN + " 项 cookie）") : "没有（先去资讯页同步一次）");
        if (!diag.isEmpty()) sb.append("\n上次细节：").append(diag);
        return sb.toString();
    }

    /* ==================== 主流程 ==================== */

    static void run(Context ctx, boolean isTest) throws Exception {
        SharedPreferences sp = MuBgCheck.prefs(ctx);
        JSONObject cfg = readCfg(sp);
        if (!isTest && !cfg.optBoolean("enabled", false)) return;

        long now = System.currentTimeMillis();
        List<String> lines = new ArrayList<>();
        boolean anyNew = false;
        boolean needApp = false;
        String err = "";     /* 硬失败：网络异常 / 服务端报错 —— 计入 fails */
        String soft = "";    /* 软状态：会话过期 / 没登录 —— 不计失败，只提示 */
        StringBuilder diag = new StringBuilder();

        JSONObject uc = cfg.optJSONObject("uc");
        JSONObject pt = cfg.optJSONObject("pt");

        /* ---- 云邮：只续 token，不登录 ---- */
        if (uc == null) uc = new JSONObject();
        if (cfg.optBoolean("ucOn", true) && uc.optString("token", "").isEmpty()
                && pt != null && !pt.optString("cookie", "").isEmpty()) {
            /* 有门户 cookie 但没云邮 token：只做门户 */
        }
        if (cfg.optBoolean("ucOn", true)) {
            try {
                if (!uc.optString("token", "").isEmpty() || !uc.optString("refresh", "").isEmpty()) {
                    String token = ensureToken(ctx, uc, now);
                    if (token == null) {
                        needApp = true;
                        err = "云邮令牌失效";
                    } else {
                        List<UcItem> items = fetchUndone(token, uc.optString("identity", ""), uc.optString("uid", ""));
                        if (items == null) {
                            needApp = true;
                            err = "云邮待办拉取失败";
                        } else {
                            Set<String> seen = ids(uc.optJSONArray("seen"));
                            boolean ucFirst = seen.isEmpty();     /* 第一次：只记下来，不报警 */
                            List<UcItem> fresh = new ArrayList<>();
                            for (UcItem it : items) if (!seen.contains(it.id)) fresh.add(it);
                            diag.append("云邮=").append(items.size()).append("项/新").append(fresh.size()).append(" ");
                            if (ucFirst) {
                                fresh.clear();
                                diag.append("(首次只记录) ");
                            }
                            for (UcItem it : items) {
                                if (!seen.contains(it.id)) fresh.add(it);
                            }
                            for (UcItem it : items) seen.add(it.id);
                            uc.put("seen", toArr(seen));
                            if (!fresh.isEmpty()) {
                                anyNew = true;
                                StringBuilder t = new StringBuilder();
                                for (int i = 0; i < Math.min(2, fresh.size()); i++) {
                                    if (i > 0) t.append("、");
                                    t.append(fresh.get(i).title);
                                }
                                String extra = fresh.get(0).due.isEmpty() ? "" : ("（截止 " + fresh.get(0).due + "）");
                                lines.add("云邮新待办 " + fresh.size() + " 项：" + t + extra);
                            }
                        }
                    }
                }
            } catch (Throwable e) {
                err = "云邮：" + brief(e);
            }
        }

        /* ---- 门户：从 cookie 罐里按 host 取 Cookie 头（罐子格式是 {"主机|名字": 值}） ---- */
        boolean ptWanted = cfg.optBoolean("ptOn", true) && pt != null;
        if (ptWanted) {
            String header = cookieHeaderFor(pt, "my.bupt.edu.cn");
            String jarHeader = header;
            if (header.isEmpty()) {
                /* 兜底：App 的 JS 那个 c.cookie 罐子经常是空的（CapacitorHttp 自己持有会话），
                   同一进程里 CapacitorHttp 用的就是 JVM 默认的 CookieHandler，直接问它要。
                   拿不到就安静跳过 —— 会话过期不该被当成"失败"反复打扰。 */
                header = cookieFromDefaultHandler(PT_BASE);
                diag.append("门户cookie=jar:").append(countJarHost(pt, "my.bupt.edu.cn"))
                    .append("/jvm:").append(header.isEmpty() ? 0 : 1).append("项 ");
            } else {
                diag.append("门户cookie=jar:").append(countJarHost(pt, "my.bupt.edu.cn")).append("项 ");
            }
            if (header.isEmpty()) {
                ptWanted = false;
                needApp = true;
                soft = "门户没有可用会话";
            } else {
                try {
                    List<PtItem> notice = fetchPortal(PT_NOTICE, header, false);
                    List<PtItem> lecture = fetchPortal(PT_LECTURE, header, true);
                    diag.append("通知=").append(notice == null ? "失效" : notice.size())
                        .append(" 讲座=").append(lecture == null ? "失效" : lecture.size()).append(" ");
                    if (notice == null || lecture == null) {
                        needApp = true;
                        soft = "门户会话已过期";
                    } else {
                        String nLine = diffNotice(notice, pt, "noticeSeen", "校内通知");
                        if (nLine != null) { lines.add(nLine); anyNew = true; }
                        String lLine = diffNotice(lecture, pt, "lectureSeen", "学术讲座");
                        if (lLine != null) { lines.add(lLine); anyNew = true; }
                    }
                } catch (Throwable e) {
                    if (err.isEmpty()) err = "门户：" + brief(e);
                }
            }
        }

        cfg.put("uc", uc);
        if (pt != null) cfg.put("pt", pt);
        cfg.put("lastRun", now);
        cfg.put("lastDiag", diag.toString().trim());
        Log.i(TAG, "run: ucOn=" + cfg.optBoolean("ucOn", true) + " ptOn=" + cfg.optBoolean("ptOn", true)
                + " | " + diag + "| anyNew=" + anyNew + " err=" + err + " soft=" + soft);

        if (anyNew) {
            cfg.put("lastResult", "发现新内容：" + lines.size() + " 类");
            cfg.put("fails", 0);
            notifyNew(ctx, lines);
        } else if (!err.isEmpty()) {
            /* 只有真正的失败（网络异常 / 服务端报错）才累加，连续 3 次才打扰 */
            int fails = cfg.optInt("fails", 0) + 1;
            cfg.put("fails", fails);
            cfg.put("lastResult", err + "（第 " + fails + " 次）");
            if (isTest || fails == 3) {
                notifySimple(ctx, N_TEST, isTest ? "检查没成功" : "后台检查连续失败",
                        err + (needApp ? " · 打开 App 同步一次即可恢复" : ""));
            }
        } else if (!soft.isEmpty()) {
            /* 会话过期属于正常状态：不累加失败、不反复打扰，一天最多提示一次 */
            cfg.put("fails", 0);
            cfg.put("lastResult", soft);
            long lastSoft = cfg.optLong("softNotifyAt", 0L);
            boolean canNotify = now - lastSoft > 24L * 3600 * 1000;
            if (isTest) {
                notifySimple(ctx, N_TEST, "检查完成", soft + " · 打开 App 同步一次即可恢复");
            } else if (needApp && canNotify) {
                cfg.put("softNotifyAt", now);
                notifySimple(ctx, N_TEST, "后台检查需要你打开一次 App",
                        soft + "，打开 App 同步一次就能继续后台提醒");
            }
        } else {
            cfg.put("lastResult", "没有新内容");
            cfg.put("fails", 0);
            if (isTest) notifySimple(ctx, N_TEST, "检查完成", "没有发现新内容");
        }

        sp.edit().putString("cfg", cfg.toString()).apply();
    }

    /* ---------- 云邮 ---------- */

    /**
     * 从 App 传过来的 cookie 罐里取出某个 host 的 Cookie 头。
     *
     * 罐子的格式是 {"主机|名字": 值}（JS 侧的 ptJarTo / ptJarHeader 就是这么存的），
     * 直接把整个 JSON 当 Cookie 头发出去是错的 —— CAS 和门户都用 JSESSIONID，
     * 必须按 host 过滤，否则门户收到的是 CAS 的票，永远判"未登录"。
     */
    private static String cookieHeaderFor(JSONObject pt, String host) {
        String direct = pt.optString("cookieHeader", "");
        if (!direct.isEmpty()) return direct;
        String raw = pt.optString("cookie", "");
        if (raw.isEmpty()) return "";
        try {
            JSONObject jar = new JSONObject(raw);
            String pre = host + "|";
            StringBuilder sb = new StringBuilder();
            java.util.Iterator<String> it = jar.keys();
            while (it.hasNext()) {
                String k = it.next();
                if (!k.startsWith(pre)) continue;
                String name = k.substring(pre.length());
                if (name.isEmpty()) continue;
                if (sb.length() > 0) sb.append("; ");
                sb.append(name).append('=').append(jar.optString(k, ""));
            }
            return sb.toString();
        } catch (Throwable e) {
            /* 不是 JSON：兼容旧格式（已经是 header 字符串） */
            return raw.indexOf('=') > 0 ? raw : "";
        }
    }

    private static int countJarHost(JSONObject pt, String host) {
        try {
            JSONObject jar = new JSONObject(pt.optString("cookie", ""));
            String pre = host + "|";
            int n = 0;
            java.util.Iterator<String> it = jar.keys();
            while (it.hasNext()) if (it.next().startsWith(pre)) n++;
            return n;
        } catch (Throwable e) {
            return pt.optString("cookieHeader", "").isEmpty() ? 0 : 1;
        }
    }

    /**
     * 从 JVM 默认的 CookieHandler 里取某个站点的 Cookie 头。
     *
     * 为什么需要它：App 里的门户请求走 CapacitorHttp，它自己持有一套 cookie ——
     * 既没写进 state.portal.cookie，也没进 WebView 的 cookie 库，所以 JS 传过来的罐子常常是空的。
     * 同一进程里 CapacitorHttp 用的就是 JVM 默认 CookieHandler，直接问它最省事。
     * 进程被杀过之后这里会拿不到（内存态），此时安静跳过，等用户打开 App 再续。
     */
    private static String cookieFromDefaultHandler(String base) {
        try {
            java.net.CookieHandler ch = java.net.CookieHandler.getDefault();
            if (ch == null) return "";
            java.util.Map<String, java.util.List<String>> got =
                    ch.get(new java.net.URI(base), new java.util.HashMap<String, java.util.List<String>>());
            if (got == null) return "";
            StringBuilder sb = new StringBuilder();
            for (java.util.Map.Entry<String, java.util.List<String>> e : got.entrySet()) {
                if (e.getKey() == null || !"Cookie".equalsIgnoreCase(e.getKey())) continue;
                if (e.getValue() == null) continue;
                for (String v : e.getValue()) {
                    if (v == null || v.isEmpty()) continue;
                    if (sb.length() > 0) sb.append("; ");
                    sb.append(v);
                }
            }
            return sb.toString();
        } catch (Throwable e) {
            return "";
        }
    }

    private static final class UcItem {
        String id, title, due;
    }

    /** 令牌还有效就直接用；过期了用 refresh_token 换（不碰密码、不计登录失败次数） */
    private static String ensureToken(Context ctx, JSONObject uc, long now) throws Exception {
        if (fresh(uc)) return uc.optString("token", "");
        String rt = uc.optString("refresh", "");
        if (rt.isEmpty()) return null;
        /* 冷却：refresh 也失败时别每次都打，至少隔 10 分钟 */
        long lastTry = uc.optLong("refreshAt", 0L);
        if (now - lastTry < 10 * 60 * 1000 && !uc.optString("token", "").isEmpty()) return null;
        uc.put("refreshAt", now);

        String body = "grant_type=refresh_token&refresh_token=" + enc(rt)
                + (uc.optString("roleId", "").isEmpty() ? "" : ("&identity=" + enc(uc.optString("roleId", ""))));
        Http r = post(UC_API + "/ykt-basics/oauth/token", body);
        if (r.code != 200) return null;
        JSONObject d = new JSONObject(r.body);
        String at = d.optString("access_token", "");
        if (at.isEmpty()) return null;
        uc.put("token", at);
        if (!d.optString("refresh_token", "").isEmpty()) uc.put("refresh", d.optString("refresh_token", ""));
        uc.put("tokenAt", now);
        return at;
    }

    private static List<UcItem> fetchUndone(String token, String identity, String uid) throws Exception {
        String url = UC_API + "/ykt-site/site/student/undone?userId=" + enc(uid);
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(15000);
        c.setReadTimeout(20000);
        c.setRequestProperty("Authorization", UC_BASIC);
        c.setRequestProperty("Tenant-Id", "000000");
        c.setRequestProperty("Accept", "application/json");
        c.setRequestProperty("Blade-Auth", token);
        if (identity != null && !identity.isEmpty()) c.setRequestProperty("identity", identity);
        Http r = null;
        try {
            r = read(c);
        } finally {
            c.disconnect();
        }
        if (r.code != 200) return null;
        JSONObject d = new JSONObject(r.body);
        JSONObject data = d.optJSONObject("data");
        JSONArray list = data == null ? null : data.optJSONArray("undoneList");
        if (list == null) return null;
        List<UcItem> out = new ArrayList<>();
        for (int i = 0; i < list.length(); i++) {
            JSONObject o = list.optJSONObject(i);
            if (o == null) continue;
            UcItem it = new UcItem();
            it.id = o.optString("activityId", "");
            it.title = o.optString("activityName", "未命名作业");
            String et = o.optString("endTime", "");
            it.due = et.length() >= 16 ? et.substring(5, 16) : et;
            if (!it.id.isEmpty()) out.add(it);
        }
        return out;
    }

    private static boolean fresh(JSONObject uc) {
        long at = uc.optLong("tokenAt", 0L);
        return !uc.optString("token", "").isEmpty() && at > 0 && (System.currentTimeMillis() - at) < TOKEN_TTL;
    }

    /* ---------- 门户 ---------- */

    private static final class PtItem {
        String id, title;
    }

    /** 拉一页列表。返回 null 表示会话失效（被 302 到 CAS / 返回登录页）。 */
    private static List<PtItem> fetchPortal(String url, String cookie, boolean lecture) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setInstanceFollowRedirects(false);
        c.setConnectTimeout(15000);
        c.setReadTimeout(20000);
        c.setRequestProperty("Cookie", cookie);
        c.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 13) MUWORKBENCH");
        Http r = null;
        try {
            r = read(c);
        } finally {
            c.disconnect();
        }
        if (r.code == 301 || r.code == 302 || r.code == 303) return null;
        if (r.code != 200) return null;
        String html = r.body;
        /* 先解析：页面正常时哪怕页头有登录链接也不影响判定；
           只有一条都解析不出、又确实像登录页时，才当作会话失效。 */
        List<PtItem> parsed = lecture ? parseLecture(html) : parseNotice(html);
        if (parsed.isEmpty()
                && (html.contains("authserver/login") || html.contains("统一身份认证"))) {
            return null;
        }
        return parsed;
    }

    private static final Pattern P_NOTICE_LI = Pattern.compile(
            "<li[^>]*>\\s*<a[^>]+href\\s*=\\s*[\"']([^\"']*content\\.jsp[^\"']*)[\"'][^>]*>([\\s\\S]{0,300}?)</a>",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern P_ID = Pattern.compile("wbnewsid=(\\d+)", Pattern.CASE_INSENSITIVE);

    private static List<PtItem> parseNotice(String html) {
        List<PtItem> out = new ArrayList<>();
        Matcher m = P_NOTICE_LI.matcher(html);
        int guard = 0;
        while (m.find() && guard++ < 60) {
            String title = text(m.group(2));
            if (title.isEmpty()) continue;
            Matcher im = P_ID.matcher(m.group(1));
            if (!im.find()) continue;
            PtItem it = new PtItem();
            it.id = im.group(1);
            it.title = title;
            out.add(it);
        }
        return out;
    }

    private static final Pattern P_LE_ROW = Pattern.compile(
            "<tr[^>]*class=\"[^\"]*hyitem[^\"]*\"[^>]*>([\\s\\S]{0,1200}?)</tr>", Pattern.CASE_INSENSITIVE);
    private static final Pattern P_HREF = Pattern.compile("href\\s*=\\s*[\"']([^\"']*hy_content[^\"']*)[\"']",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern P_TITLE = Pattern.compile("<a[^>]+title=\"([^\"]*)\"", Pattern.CASE_INSENSITIVE);

    private static List<PtItem> parseLecture(String html) {
        List<PtItem> out = new ArrayList<>();
        Matcher m = P_LE_ROW.matcher(html);
        int guard = 0;
        while (m.find() && guard++ < 60) {
            String blk = m.group(1);
            Matcher hm = P_HREF.matcher(blk);
            if (!hm.find()) continue;
            Matcher im = P_ID.matcher(hm.group(1));
            if (!im.find()) continue;
            Matcher tm = P_TITLE.matcher(blk);
            String title = tm.find() ? text(tm.group(1)) : "";
            if (title.isEmpty()) continue;
            PtItem it = new PtItem();
            it.id = im.group(1);
            it.title = title;
            out.add(it);
        }
        return out;
    }

    private static String diffNotice(List<PtItem> list, JSONObject pt, String key, String label) throws Exception {
        Set<String> seen = ids(pt.optJSONArray(key));
        boolean first = seen.isEmpty();     /* 第一次见：只记录，不弹通知 */
        List<PtItem> fresh = new ArrayList<>();
        for (PtItem it : list) if (!seen.contains(it.id)) fresh.add(it);
        for (PtItem it : list) seen.add(it.id);
        pt.put(key, toArr(seen));
        if (first || fresh.isEmpty()) return null;
        StringBuilder t = new StringBuilder();
        for (int i = 0; i < Math.min(2, fresh.size()); i++) {
            if (i > 0) t.append("、");
            t.append(fresh.get(i).title);
        }
        if (fresh.size() > 2) t.append(" 等");
        return label + " " + fresh.size() + " 条更新：" + t;
    }

    /* ---------- 工具 ---------- */

    private static final class Http {
        int code;
        String body;
    }

    private static Http read(HttpURLConnection c) throws Exception {
        Http h = new Http();
        h.code = c.getResponseCode();
        InputStream in;
        try {
            in = h.code >= 400 ? c.getErrorStream() : c.getInputStream();
        } catch (Throwable e) {
            in = c.getErrorStream();
        }
        h.body = in == null ? "" : drain(in);
        return h;
    }

    private static Http post(String url, String form) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(15000);
        c.setReadTimeout(20000);
        c.setRequestMethod("POST");
        c.setDoOutput(true);
        c.setRequestProperty("Authorization", UC_BASIC);
        c.setRequestProperty("Tenant-Id", "000000");
        c.setRequestProperty("Accept", "application/json");
        /* 明确声明 Content-Type，否则服务端会当成空 body（「missing grant type」的成因） */
        c.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
        Http h;
        try {
            OutputStream os = c.getOutputStream();
            os.write(form.getBytes("UTF-8"));
            os.close();
            h = read(c);
        } finally {
            c.disconnect();
        }
        return h;
    }

    private static String drain(InputStream in) throws Exception {
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
        in.close();
        return new String(bo.toByteArray(), "UTF-8");
    }

    private static String enc(String s) {
        try {
            return URLEncoder.encode(s == null ? "" : s, "UTF-8");
        } catch (Exception e) {
            return "";
        }
    }

    private static String text(String html) {
        if (html == null) return "";
        String s = html.replaceAll("<[^>]*>", " ");
        s = s.replace("&nbsp;", " ").replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
                .replace("&quot;", "\"").replace("&#39;", "'").replace("&ldquo;", "“").replace("&rdquo;", "”");
        return s.replaceAll("\\s+", " ").trim();
    }

    private static long lastRefreshGuard = 0;

    private static Set<String> ids(JSONArray a) {
        Set<String> set = new LinkedHashSet<>();
        if (a != null) for (int i = 0; i < a.length(); i++) {
            String s = a.optString(i, "");
            if (!s.isEmpty()) set.add(s);
        }
        return set;
    }

    private static JSONArray toArr(Set<String> set) {
        List<String> keep = new ArrayList<>(set);
        if (keep.size() > SEEN_CAP) keep = keep.subList(keep.size() - SEEN_CAP, keep.size());
        JSONArray out = new JSONArray();
        for (String s : keep) out.put(s);
        return out;
    }

    private static String brief(Throwable e) {
        String m = e == null ? "" : String.valueOf(e.getMessage());
        if (m.length() > 60) m = m.substring(0, 60);
        return m.isEmpty() ? e.getClass().getSimpleName() : m;
    }

    private static String fmt(long ms) {
        java.text.SimpleDateFormat f = new java.text.SimpleDateFormat("MM-dd HH:mm", java.util.Locale.CHINA);
        return f.format(new java.util.Date(ms));
    }

    /* ---------- 通知 ---------- */

    private static void ensureChannel(Context ctx) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null || nm.getNotificationChannel(CHANNEL_ID) != null) return;
        NotificationChannel ch = new NotificationChannel(CHANNEL_ID, "新内容提醒", NotificationManager.IMPORTANCE_HIGH);
        ch.setDescription("后台发现新的云邮待办 / 校内通知时提醒");
        ch.enableVibration(true);
        nm.createNotificationChannel(ch);
    }

    private static void notifyNew(Context ctx, List<String> lines) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < lines.size(); i++) {
            if (i > 0) sb.append("\n");
            sb.append(lines.get(i));
        }
        notifySimple(ctx, N_UC, lines.size() == 1 ? "有新内容" : "有新内容（" + lines.size() + " 项）", sb.toString());
    }

    private static void notifySimple(Context ctx, int id, String title, String text) {
        try {
            ensureChannel(ctx);
            NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm == null) return;

            Intent open = new Intent(ctx, MainActivity.class);
            open.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            int piFlags = PendingIntent.FLAG_UPDATE_CURRENT;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) piFlags |= PendingIntent.FLAG_IMMUTABLE;
            PendingIntent pi = PendingIntent.getActivity(ctx, id, open, piFlags);

            Notification.Builder b;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                b = new Notification.Builder(ctx, CHANNEL_ID);
            } else {
                b = new Notification.Builder(ctx);
            }
            b.setContentTitle(title)
                    .setContentText(text)
                    .setStyle(new Notification.BigTextStyle().bigText(text))
                    .setSmallIcon(android.R.drawable.stat_notify_chat)
                    .setAutoCancel(true)
                    .setShowWhen(true)
                    .setContentIntent(pi);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                b.setVisibility(Notification.VISIBILITY_PUBLIC);
            }
            nm.notify(id, b.build());
        } catch (Throwable ignored) {
        }
    }

    static void openNotificationSettings(Context ctx) {
        try {
            Intent i;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                i = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS);
                i.putExtra(Settings.EXTRA_APP_PACKAGE, ctx.getPackageName());
            } else {
                i = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                i.setData(Uri.parse("package:" + ctx.getPackageName()));
            }
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            ctx.startActivity(i);
        } catch (Throwable ignored) {
        }
    }
}
