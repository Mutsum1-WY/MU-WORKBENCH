package com.mu.workbench;

import android.util.Log;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 门户（my.bupt.edu.cn）的后台登录与会话管理。
 *
 * 背景：门户只有 HTTP，且列表页是登录态才给。App 里的门户请求走 CapacitorHttp，
 * 它自己持有一套 cookie（进程内存），进程被杀就没了 —— 所以后台任务不能只依赖它。
 *
 * 门户的风控很松（和云邮不一样），所以这里**允许真的登录**，流程与 JS 侧 ptLogin 一致：
 *   ① GET  CAS 登录页（service=http://my.bupt.edu.cn/），拿 execution；若已带 ticket 直接用
 *   ② POST 学工号 + 密码 + execution + _eventId=submit  → 302 里读 ticket=ST-…
 *   ③ GET  门户 ?ticket=ST-… 跟随跳转，攒下门户自己的 JSESSIONID
 * 会话存进 BgCheck 的 prefs（pt.jar），能复用就复用，失效了才重登；
 * 登录失败后 30 分钟内不再重试，避免密码错时反复打 CAS。
 *
 * 注意：密码因此会和云邮凭据一样存在本机 App 私有目录里（明文），设置里可随时清除。
 */
final class PortalLogin {

    private static final String TAG = "MuBgCheck";
    private static final String UA = "Mozilla/5.0 (Linux; Android 13) MUWORKBENCH";
    static final String PT_HOST = "my.bupt.edu.cn";
    private static final String PT_BASE = "http://my.bupt.edu.cn/";
    private static final String CAS = "https://auth.bupt.edu.cn/authserver/login?service="
            + urlEnc(PT_BASE);

    private static final long SESSION_TTL = 6L * 3600 * 1000;   /* 会话最多复用 6 小时 */
    private static final long LOGIN_COOLDOWN = 30L * 60 * 1000;  /* 登录失败后冷却 30 分钟 */

    private PortalLogin() {
    }

    /* ==================== 对外 ==================== */

    /**
     * 保证门户会话可用，返回可用于 my.bupt.edu.cn 的 Cookie 头。
     * 复用顺序：已有 jar → 直接返回；否则真登录一次。
     */
    static String ensure(ContextHolder h, JSONObject pt, String id, String pwd) {
        String cached = jarHeader(pt, PT_HOST);
        long at = pt.optLong("jarAt", 0L);
        if (!cached.isEmpty() && System.currentTimeMillis() - at < SESSION_TTL) return cached;
        return login(h, pt, id, pwd, false);
    }

    /** 强制重新登录（会话被判定失效时用） */
    static String relogin(ContextHolder h, JSONObject pt, String id, String pwd) {
        try {
            pt.remove("jar");
            pt.put("jarAt", 0L);
        } catch (Throwable ignored) {
        }
        return login(h, pt, id, pwd, true);
    }

    static String jarHeader(JSONObject pt, String host) {
        try {
            JSONObject jar = pt.optJSONObject("jar");
            if (jar == null) return "";
            String pre = host + "|";
            StringBuilder sb = new StringBuilder();
            Iterator<String> it = jar.keys();
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
            return "";
        }
    }

    /* ==================== 登录 ==================== */

    private static String login(ContextHolder holder, JSONObject pt, String id, String pwd, boolean force) {
        if (id == null || id.isEmpty() || pwd == null || pwd.isEmpty()) {
            Log.i(TAG, "portal login skipped: 没有学工号/密码");
            return "";
        }
        long failAt = pt.optLong("loginFailAt", 0L);
        if (!force && System.currentTimeMillis() - failAt < LOGIN_COOLDOWN) {
            Log.i(TAG, "portal login skipped: 冷却中");
            return "";
        }
        Map<String, String> jar = new LinkedHashMap<>();
        try {
            /* ① 打开认证页 */
            Res r = req(holder, CAS, "GET", jar, null);
            String ticket = ticketOf(r.location);
            if (ticket == null && r.code >= 300 && r.code < 400) {
                /* 首跳就 3xx：跟一跳再看（云邮那边踩过同样的坑） */
                Res r2 = follow(holder, CAS, jar, 4);
                ticket = ticketOf(r2.location);
                if (ticket == null) r = r2;
            }
            if (ticket == null) {
                String exec = execOf(r.body);
                if (exec == null) {
                    throw new IOException("认证页没有 execution（HTTP " + r.code + "，"
                            + (r.body == null ? 0 : r.body.length()) + " 字节）");
                }
                /* ② 提交账密 */
                String form = "username=" + urlEnc(id)
                        + "&password=" + urlEnc(pwd)
                        + "&type=username_password"
                        + "&execution=" + urlEnc(exec)
                        + "&_eventId=submit";
                r = req(holder, CAS, "POST", jar, form);
                ticket = ticketOf(r.location);
                if (ticket == null) {
                    if (r.code == 423) throw new IOException("认证服务返回 423（临时锁定）");
                    if (r.code == 401 || r.code == 200) throw new IOException("统一身份认证没通过（HTTP " + r.code + "）");
                    throw new IOException("认证没返回 ticket（HTTP " + r.code + "）");
                }
            }
            /* ③ 用 ticket 在门户建会话（跟随跳转，逐跳攒 cookie） */
            Res r3 = follow(holder, PT_BASE + "?ticket=" + urlEnc(ticket), jar, 8);
            if (isLoginPage(r3.body)) throw new IOException("门户又跳回认证页（会话没建起来）");

            JSONObject out = new JSONObject();
            for (Map.Entry<String, String> e : jar.entrySet()) out.put(e.getKey(), e.getValue());
            pt.put("jar", out);
            pt.put("jarAt", System.currentTimeMillis());
            pt.remove("loginFailAt");
            Log.i(TAG, "portal login ok: cookie " + jar.size() + " 项");
            return jarHeader(pt, PT_HOST);
        } catch (Throwable e) {
            try {
                pt.put("loginFailAt", System.currentTimeMillis());
            } catch (Throwable ignored) {
            }
            Log.w(TAG, "portal login failed: " + e);
            return "";
        }
    }

    /* ==================== HTTP ==================== */

    /** 让调用方能把 Application Context 传进来（避免这里再依赖 Activity） */
    interface ContextHolder {
        android.content.Context ctx();
    }

    private static final class Res {
        int code;
        String body = "";
        String location = "";
    }

    private static Res req(ContextHolder holder, String url, String method,
                           Map<String, String> jar, String formBody) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setInstanceFollowRedirects(false);
        c.setConnectTimeout(15000);
        c.setReadTimeout(25000);
        c.setRequestProperty("User-Agent", UA);
        c.setRequestProperty("Accept-Language", "zh-CN,zh;q=0.9");
        c.setRequestProperty("Accept", "text/html,application/xhtml+xml,*/*;q=0.8");
        /* 明确要明文：CAS 会按 accept-encoding 回 gzip，拿到压缩字节流就整页「没有 execution」 */
        c.setRequestProperty("Accept-Encoding", "identity");
        String host = new URL(url).getHost();
        String ck = cookieHeader(jar, host);
        if (!ck.isEmpty()) c.setRequestProperty("Cookie", ck);

        Res r = new Res();
        try {
            if (formBody != null) {
                c.setRequestMethod("POST");
                c.setDoOutput(true);
                c.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
                OutputStream os = c.getOutputStream();
                os.write(formBody.getBytes("UTF-8"));
                os.close();
            }
            r.code = c.getResponseCode();
            r.location = optHeader(c, "Location");
            addSetCookie(jar, host, c.getHeaderFields());
            r.body = readBody(c);
        } finally {
            c.disconnect();
        }
        return r;
    }

    private static Res follow(ContextHolder holder, String url, Map<String, String> jar, int max) throws Exception {
        String cur = url;
        Res r = null;
        for (int i = 0; i <= max; i++) {
            r = req(holder, cur, "GET", jar, null);
            String loc = r.location;
            if (loc == null || loc.isEmpty() || r.code < 300 || r.code >= 400) return r;
            try {
                cur = new URL(new URL(cur), loc).toString();
            } catch (Throwable e) {
                return r;
            }
        }
        return r;
    }

    private static String readBody(HttpURLConnection c) {
        try {
            InputStream in;
            int code = c.getResponseCode();
            in = code >= 400 ? c.getErrorStream() : c.getInputStream();
            if (in == null) return "";
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
            in.close();
            return new String(bo.toByteArray(), "UTF-8");
        } catch (Throwable e) {
            return "";
        }
    }

    private static String optHeader(HttpURLConnection c, String name) {
        try {
            return c.getHeaderField(name) == null ? "" : c.getHeaderField(name);
        } catch (Throwable e) {
            return "";
        }
    }

    /** 把 Set-Cookie 攒进 jar（键 = 主机|名字）；注意 CAS 和门户都用 JSESSIONID，必须按主机分开 */
    private static void addSetCookie(Map<String, String> jar, String host, Map<String, List<String>> headers) {
        if (headers == null) return;
        for (Map.Entry<String, List<String>> e : headers.entrySet()) {
            if (e.getKey() == null || !"Set-Cookie".equalsIgnoreCase(e.getKey())) continue;
            if (e.getValue() == null) continue;
            for (String raw : e.getValue()) {
                if (raw == null) continue;
                String kv = raw.split(";", 2)[0];
                int i = kv.indexOf('=');
                if (i <= 0) continue;
                String name = kv.substring(0, i).trim();
                String val = kv.substring(i + 1).trim();
                if (name.isEmpty()) continue;
                jar.put(host + "|" + name, val);
            }
        }
    }

    private static String cookieHeader(Map<String, String> jar, String host) {
        String pre = host + "|";
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> e : jar.entrySet()) {
            if (!e.getKey().startsWith(pre)) continue;
            if (sb.length() > 0) sb.append("; ");
            sb.append(e.getKey().substring(pre.length())).append('=').append(e.getValue());
        }
        return sb.toString();
    }

    /* ==================== 解析 ==================== */

    private static final Pattern P_TICKET = Pattern.compile("ticket=(ST-[^&#\\s]+)", Pattern.CASE_INSENSITIVE);

    private static String ticketOf(String url) {
        if (url == null) return null;
        Matcher m = P_TICKET.matcher(url);
        return m.find() ? m.group(1) : null;
    }

    /**
     * 从登录页里抠 execution。
     * 别用一条死正则：实测这颗 input 没有 type、属性顺序不保证、值还是 6.8KB 的 base64，
     * 所以逐条 <input> 解析，再分别找 name 与 value（忽略属性顺序）。
     */
    private static String execOf(String html) {
        if (html == null || html.isEmpty()) return null;
        Matcher m = Pattern.compile("<input\\b[^>]*>", Pattern.CASE_INSENSITIVE).matcher(html);
        while (m.find()) {
            String tag = m.group(0);
            if (!Pattern.compile("name\\s*=\\s*[\"']?execution", Pattern.CASE_INSENSITIVE).matcher(tag).find()) continue;
            Matcher vm = Pattern.compile("value\\s*=\\s*\"([^\"]*)\"", Pattern.CASE_INSENSITIVE).matcher(tag);
            if (vm.find()) return htmlDecode(vm.group(1));
            Matcher vm2 = Pattern.compile("value\\s*=\\s*'([^']*)'", Pattern.CASE_INSENSITIVE).matcher(tag);
            if (vm2.find()) return htmlDecode(vm2.group(1));
        }
        return null;
    }

    private static boolean isLoginPage(String html) {
        if (html == null) return false;
        if (html.contains("退出") && html.contains("门户首页")) return false;
        return html.contains("authserver") || html.contains("统一身份认证")
                || html.contains("系统发生错误") || html.contains("错误标识码");
    }

    private static String htmlDecode(String s) {
        return s.replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
                .replace("&quot;", "\"").replace("&#39;", "'");
    }

    static String urlEnc(String s) {
        try {
            return URLEncoder.encode(s == null ? "" : s, "UTF-8");
        } catch (Throwable e) {
            return "";
        }
    }
}
