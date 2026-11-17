package com.mu.workbench.domain

import com.mu.workbench.data.CourseScheduleItem
import com.mu.workbench.data.HolidayEntry
import com.mu.workbench.data.HwAttachment
import com.mu.workbench.data.HwCourse
import com.mu.workbench.data.HwExcellentItem
import com.mu.workbench.data.HwSubmissionItem
import com.mu.workbench.data.HwTerm
import com.mu.workbench.data.PeriodSlot
import com.mu.workbench.data.PortalArticle
import com.mu.workbench.data.PortalAttachment
import com.mu.workbench.data.TimetableData
import com.mu.workbench.data.UcloudConfig
import com.mu.workbench.data.UcloudDetailCache
import com.mu.workbench.data.UcloudTodoItem
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLDecoder
import java.net.URLEncoder
import java.security.MessageDigest
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.Callable
import java.util.concurrent.Executors

object NativeClients {

    private const val UA =
        "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
    private const val UC_API = "https://apiucloud.bupt.edu.cn"
    private const val UC_BASIC = "Basic c3dvcmQ6c3dvcmRfc2VjcmV0"
    private const val UC_CAS = "https://auth.bupt.edu.cn/authserver/login"
    private const val UC_SERVICE = "https://ucloud.bupt.edu.cn/uclass"
    private const val HW_OAUTH_SALT = "gZTwLteBkHIxHhXFMcQvUMjosqYWPuzTcQwYKpFidkFcradHFm"

    private const val JW_BASE = "https://jwgl.bupt.edu.cn"
    private const val PT_BASE = "http://my.bupt.edu.cn/"
    private const val PT_HOST = "my.bupt.edu.cn"

    data class HttpResp(
        val code: Int,
        val body: String,
        val headers: Map<String, List<String>>,
        val finalUrl: String = "",
        val location: String = ""
    )

    private class HostCookieJar {
        private val map = LinkedHashMap<String, String>()

        fun absorb(host: String, headers: Map<String, List<String>>) {
            val h = host.lowercase()
            headers.forEach { (k, vs) ->
                if (k != null && k.equals("Set-Cookie", true)) {
                    vs.forEach { line ->
                        val kv = line.substringBefore(';').trim()
                        val idx = kv.indexOf('=')
                        if (idx > 0) {
                            val name = kv.substring(0, idx).trim()
                            val value = kv.substring(idx + 1).trim()
                            if (name.isNotEmpty()) map["$h|$name"] = value
                        }
                    }
                }
            }
        }

        fun headerFor(url: String): String {
            val h = try { URL(url).host.lowercase() } catch (_: Throwable) { "" }
            if (h.isEmpty()) return ""
            val pre = "$h|"
            return map.entries
                .filter { it.key.startsWith(pre) }
                .joinToString("; ") { "${it.key.substring(pre.length)}=${it.value}" }
        }

        fun headerForHost(host: String): String {
            val pre = "${host.lowercase()}|"
            return map.entries
                .filter { it.key.startsWith(pre) }
                .joinToString("; ") { "${it.key.substring(pre.length)}=${it.value}" }
        }

        fun countForHost(host: String): Int {
            val pre = "${host.lowercase()}|"
            return map.keys.count { it.startsWith(pre) }
        }

        fun toJsonString(): String {
            val obj = JSONObject()
            map.forEach { (k, v) -> obj.put(k, v) }
            return obj.toString()
        }

        companion object {
            fun fromString(raw: String): HostCookieJar {
                val jar = HostCookieJar()
                val s = raw.trim()
                if (s.isEmpty()) return jar
                if (s.startsWith("{")) {
                    try {
                        val obj = JSONObject(s)
                        val ks = obj.keys()
                        while (ks.hasNext()) {
                            val k = ks.next()
                            jar.map[k] = obj.optString(k)
                        }
                        return jar
                    } catch (_: Throwable) {
                    }
                }
                // 兼容 header 字符串格式
                s.split(';').forEach { part ->
                    val idx = part.indexOf('=')
                    if (idx > 0) {
                        val n = part.substring(0, idx).trim()
                        val v = part.substring(idx + 1).trim()
                        if (n.isNotEmpty()) jar.map["$PT_HOST|$n"] = v
                    }
                }
                return jar
            }
        }
    }

    private fun JSONObject.optClean(key: String, fallback: String = ""): String {
        if (isNull(key)) return fallback
        val v = optString(key, fallback)
        return if (v == "null") fallback else v
    }

    private fun httpReq(
        url: String,
        method: String = "GET",
        headers: Map<String, String> = emptyMap(),
        body: String? = null,
        followRedirects: Boolean = true,
        stopOnTicket: Boolean = false,
        upgradeHttpOnCas: Boolean = false,
        externalJar: HostCookieJar? = null,
        maxHops: Int = 8
    ): HttpResp {
        var cur = url
        var curMethod = method
        var curBody = body
        val jar = externalJar ?: HostCookieJar()
        var lastCode = 0
        var lastBody = ""
        var lastHeaders: Map<String, List<String>> = emptyMap()
        var lastLoc = ""

        for (hop in 0 until maxHops) {
            val parsedUrl = URL(cur)
            val host = parsedUrl.host ?: ""
            val conn = (parsedUrl.openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = false
                connectTimeout = 15000
                readTimeout = 25000
                requestMethod = curMethod
                setRequestProperty("User-Agent", UA)
                setRequestProperty("Accept-Language", "zh-CN,zh;q=0.9")
                setRequestProperty("Accept-Encoding", "identity")
                headers.forEach { (k, v) ->
                    if (curMethod == "GET" && (k.equals("Content-Type", true) || k.equals("Content-Length", true))) return@forEach
                    setRequestProperty(k, v)
                }
                val jarStr = jar.headerFor(cur)
                if (jarStr.isNotEmpty()) {
                    val existing = headers.entries.firstOrNull { it.key.equals("Cookie", true) }?.value
                    setRequestProperty("Cookie", if (existing.isNullOrEmpty()) jarStr else "$existing; $jarStr")
                }
                if (curBody != null && curMethod != "GET") {
                    doOutput = true
                    outputStream.use { it.write(curBody!!.toByteArray(Charsets.UTF_8)) }
                }
            }
            lastCode = conn.responseCode
            lastHeaders = conn.headerFields ?: emptyMap()
            jar.absorb(host, lastHeaders)

            val rawLoc = header(lastHeaders, "Location").orEmpty()
            lastLoc = if (rawLoc.isNotEmpty()) {
                try { URL(parsedUrl, rawLoc).toString() } catch (_: Throwable) { rawLoc }
            } else ""

            val stream = if (lastCode in 200..399) conn.inputStream else conn.errorStream
            lastBody = if (stream != null) {
                val buf = ByteArrayOutputStream()
                stream.use { it.copyTo(buf) }
                buf.toString("UTF-8")
            } else ""
            conn.disconnect()

            if (lastCode in listOf(301, 302, 303, 307, 308) && lastLoc.isNotEmpty()) {
                if (stopOnTicket && Regex("""ticket=(ST-[^&#\s]+)""", RegexOption.IGNORE_CASE).containsMatchIn(lastLoc)) {
                    break
                }
                if (followRedirects) {
                    var nextUrl = lastLoc
                    if (upgradeHttpOnCas && nextUrl.startsWith("http://", ignoreCase = true) && !nextUrl.contains(PT_HOST, ignoreCase = true)) {
                        nextUrl = "https://" + nextUrl.substring(7)
                    }
                    if (lastCode == 303 || ((lastCode == 301 || lastCode == 302) && curMethod == "POST")) {
                        curMethod = "GET"
                        curBody = null
                    }
                    cur = nextUrl
                    continue
                }
            }
            break
        }
        return HttpResp(lastCode, lastBody, lastHeaders, cur, lastLoc)
    }

    private fun header(headers: Map<String, List<String>>, name: String): String? {
        for ((k, v) in headers) {
            if (k != null && k.equals(name, true)) return v.firstOrNull()
        }
        return null
    }

    private fun formEncode(params: Map<String, String>): String {
        return params.entries.joinToString("&") { (k, v) ->
            "${URLEncoder.encode(k, "UTF-8")}=${URLEncoder.encode(v, "UTF-8")}"
        }
    }

    private fun htmlUnescape(s: String): String {
        return s.replace("&amp;", "&")
            .replace("&#38;", "&")
            .replace("&#x26;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
    }

    /** 兼容 CAS 登录页：逐个解析 <input>（无视属性顺序/单双引号）并支持 JS 兜底 */
    internal fun execOf(html: String): String {
        if (html.isBlank()) return ""
        val inputRe = Regex("""<input\b[^>]*>""", RegexOption.IGNORE_CASE)
        val nameExecRe = Regex("""(?:name|id)\s*=\s*["']?execution\b""", RegexOption.IGNORE_CASE)
        val valDqRe = Regex("""value\s*=\s*"([^"]*)"""", RegexOption.IGNORE_CASE)
        val valSqRe = Regex("""value\s*=\s*'([^']*)'""", RegexOption.IGNORE_CASE)
        val valUnqRe = Regex("""value\s*=\s*([^\s>"']+)""", RegexOption.IGNORE_CASE)

        for (m in inputRe.findAll(html)) {
            val tag = m.value
            if (!nameExecRe.containsMatchIn(tag)) continue
            val v = valDqRe.find(tag)?.groupValues?.get(1)
                ?: valSqRe.find(tag)?.groupValues?.get(1)
                ?: valUnqRe.find(tag)?.groupValues?.get(1)
            if (!v.isNullOrEmpty()) return htmlUnescape(v)
        }
        val jsExec = Regex("""["']?execution["']?\s*[:=]\s*["']([A-Za-z0-9+/=._~-]{8,})["']""", RegexOption.IGNORE_CASE)
            .find(html)?.groupValues?.get(1)
        return if (!jsExec.isNullOrEmpty()) htmlUnescape(jsExec) else ""
    }

    private fun ticketOf(s: String?): String? {
        if (s.isNullOrEmpty()) return null
        return Regex("""ticket=(ST-[^&#\s]+)""", RegexOption.IGNORE_CASE).find(s)?.groupValues?.get(1)
    }

    fun md5Hex(input: String): String {
        val digest = MessageDigest.getInstance("MD5").digest(input.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    fun oauthKeyFor(resourceId: String): String = md5Hex(resourceId + HW_OAUTH_SALT)

    /* ===================== 1. 节假日联网更新 ===================== */

    fun fetchHolidays(years: List<Int>): Map<String, Map<String, HolidayEntry>> {
        val out = LinkedHashMap<String, Map<String, HolidayEntry>>()
        for (y in years) {
            val urls = listOf(
                "https://cdn.jsdelivr.net/gh/NateScarlet/holiday-cn@master/$y.json",
                "https://fastly.jsdelivr.net/gh/NateScarlet/holiday-cn@master/$y.json",
                "https://raw.githubusercontent.com/NateScarlet/holiday-cn/master/$y.json"
            )
            for (u in urls) {
                try {
                    val r = httpReq(u)
                    if (r.code == 200 && r.body.trimStart().startsWith("{")) {
                        val obj = JSONObject(r.body)
                        val days = obj.optJSONArray("days") ?: continue
                        val map = LinkedHashMap<String, HolidayEntry>()
                        for (i in 0 until days.length()) {
                            val d = days.optJSONObject(i) ?: continue
                            val date = d.optClean("date")
                            val off = d.optBoolean("isOffDay", false)
                            val name = d.optClean("name")
                            if (date.isNotEmpty()) map[date] = HolidayEntry(off, name)
                        }
                        if (map.isNotEmpty()) {
                            out[y.toString()] = map
                            break
                        }
                    }
                } catch (_: Throwable) {
                }
            }
        }
        return out
    }

    /* ===================== 2. 云邮教学空间 ===================== */

    private fun ucHeaders(cfg: UcloudConfig, withAuth: Boolean = true, json: Boolean = false): Map<String, String> {
        val h = LinkedHashMap<String, String>()
        h["Authorization"] = UC_BASIC
        h["Tenant-Id"] = "000000"
        h["Accept"] = "application/json"
        if (json) h["Content-Type"] = "application/json;charset=UTF-8"
        if (withAuth && cfg.token.isNotEmpty()) h["Blade-Auth"] = cfg.token
        val idHdr = cfg.identity.ifEmpty { cfg.roleId }
        if (withAuth && idHdr.isNotEmpty()) h["identity"] = idHdr
        return h
    }

    private fun ucFetchUserInfoAndRole(inCfg: UcloudConfig, trace: MutableList<String>): UcloudConfig {
        var cfg = inCfg
        // ② 取用户信息（拿到 userInfo.id / data.id）
        try {
            trace += "④ 读取用户信息 GET /ykt-basics/info"
            val iRes = httpReq("$UC_API/ykt-basics/info?id=${URLEncoder.encode(cfg.userId, "UTF-8")}", headers = ucHeaders(cfg))
            if (iRes.code == 200) {
                val data = JSONObject(iRes.body).optJSONObject("data")
                val infoId = data?.optJSONObject("userInfo")?.optClean("id").orEmpty()
                    .ifEmpty { data?.optClean("id").orEmpty() }
                if (infoId.isNotEmpty()) {
                    cfg = cfg.copy(userInfoId = infoId)
                    trace += "   ↳ userInfoId=$infoId"
                }
            }
        } catch (e: Throwable) {
            trace += "   ! 用户信息跳过（${e.message}）"
        }

        // ③ 取角色列表（JS005 学生），并用 refresh_token + identity=role.id 换取角色作用域令牌
        try {
            trace += "⑤ 读取角色列表 /userroledomaindept/listByUserId"
            val rRes = httpReq(
                url = "$UC_API/ykt-basics/userroledomaindept/listByUserId?userId=${URLEncoder.encode(cfg.userId, "UTF-8")}",
                headers = ucHeaders(cfg)
            )
            if (rRes.code == 200) {
                val arr = JSONObject(rRes.body).optJSONArray("data")
                if (arr != null && arr.length() > 0) {
                    var picked = arr.optJSONObject(0)
                    for (i in 0 until arr.length()) {
                        val o = arr.optJSONObject(i) ?: continue
                        val alias = o.optClean("roleAliase").ifEmpty { o.optClean("roleCode") }
                        val rname = o.optClean("roleName")
                        if (alias == "JS005" || rname.contains("学生")) {
                            picked = o
                            break
                        }
                    }
                    if (picked != null) {
                        val alias = picked.optClean("roleAliase").ifEmpty { picked.optClean("roleCode") }
                        val dept = picked.optClean("dept").ifEmpty { picked.optClean("deptId") }
                        val rId = picked.optClean("id").ifEmpty { picked.optClean("roleId") }
                        val rName = picked.optClean("roleName").ifEmpty { alias }
                        val identity = when {
                            alias.isNotEmpty() && dept.isNotEmpty() -> "$alias:$dept"
                            alias.isNotEmpty() -> alias
                            else -> rId
                        }
                        cfg = cfg.copy(roleId = rId, roleName = rName, identity = identity)
                        trace += "   ↳ 选中角色：$identity ($rName)"

                        if (rId.isNotEmpty() && cfg.refreshToken.isNotEmpty()) {
                            val roleRes = httpReq(
                                url = "$UC_API/ykt-basics/oauth/token",
                                method = "POST",
                                headers = mapOf(
                                    "Authorization" to UC_BASIC,
                                    "Tenant-Id" to "000000",
                                    "Accept" to "application/json",
                                    "Blade-Auth" to cfg.token,
                                    "Content-Type" to "application/x-www-form-urlencoded"
                                ),
                                body = formEncode(
                                    mapOf(
                                        "grant_type" to "refresh_token",
                                        "refresh_token" to cfg.refreshToken,
                                        "identity" to rId
                                    )
                                )
                            )
                            if (roleRes.code == 200) {
                                val rJson = JSONObject(roleRes.body)
                                val rTok = rJson.optClean("access_token")
                                if (rTok.isNotEmpty()) {
                                    cfg = cfg.copy(
                                        token = rTok,
                                        refreshToken = rJson.optClean("refresh_token").ifEmpty { cfg.refreshToken },
                                        userId = rJson.optClean("user_id").ifEmpty { cfg.userId },
                                        tokenAt = System.currentTimeMillis()
                                    )
                                    trace += "   ✓ 已切换为角色令牌"
                                }
                            }
                        }
                    }
                }
            }
        } catch (e: Throwable) {
            trace += "   ! 角色切换失败（继续用登录令牌）：${e.message}"
        }

        if (cfg.userInfoId.isEmpty() && cfg.userId.isNotEmpty()) {
            try {
                val iRes = httpReq("$UC_API/ykt-basics/info?id=${URLEncoder.encode(cfg.userId, "UTF-8")}", headers = ucHeaders(cfg))
                if (iRes.code == 200) {
                    val data = JSONObject(iRes.body).optJSONObject("data")
                    val infoId = data?.optJSONObject("userInfo")?.optClean("id").orEmpty()
                        .ifEmpty { data?.optClean("id").orEmpty() }
                    if (infoId.isNotEmpty()) cfg = cfg.copy(userInfoId = infoId)
                }
            } catch (_: Throwable) {
            }
        }
        return cfg
    }

    fun ucloudLogin(user: String, pwd: String): UcloudConfig {
        require(user.isNotBlank() && pwd.isNotBlank()) { "请填写学号和密码" }
        val casUrl = "$UC_CAS?service=${URLEncoder.encode(UC_SERVICE, "UTF-8")}"
        val jar = HostCookieJar()
        val trace = mutableListOf<String>()

        trace += "① 打开统一身份认证页"
        val g1 = httpReq(
            url = casUrl,
            followRedirects = true,
            stopOnTicket = true,
            upgradeHttpOnCas = true,
            externalJar = jar
        )

        var ticket = ticketOf(g1.location) ?: ticketOf(g1.finalUrl)
        if (ticket == null) {
            if (g1.code != 200) {
                throw IllegalStateException("认证页返回 HTTP ${g1.code}（${g1.location.ifEmpty { "请检查网络" }}）")
            }
            val exec = execOf(g1.body)
            if (exec.isEmpty()) {
                val why = when {
                    g1.body.isBlank() -> "认证页返回空（可能被校园网认证拦截）"
                    Regex("""上网认证|校园网|准入|请先登录""").containsMatchIn(g1.body) -> "连到了校园网准入网关，请先联网后再试"
                    else -> "认证页未找到 execution（长度 ${g1.body.length} 字节）"
                }
                throw IllegalStateException(why)
            }
            trace += "   ↳ 获取到 execution（${exec.length} 字符）"

            val postTarget = g1.finalUrl.ifEmpty { casUrl }
            trace += "② 提交学工号与密码"
            val p1 = httpReq(
                url = postTarget,
                method = "POST",
                headers = mapOf(
                    "Content-Type" to "application/x-www-form-urlencoded",
                    "Referer" to postTarget
                ),
                body = formEncode(
                    mapOf(
                        "username" to user.trim(),
                        "password" to pwd,
                        "type" to "username_password",
                        "execution" to exec,
                        "_eventId" to "submit"
                    )
                ),
                followRedirects = false,
                externalJar = jar
            )
            ticket = ticketOf(p1.location) ?: ticketOf(p1.finalUrl)
            if (ticket == null && p1.code in listOf(301, 302, 303, 307, 308) && p1.location.isNotEmpty()) {
                val pFollow = httpReq(
                    url = p1.location,
                    followRedirects = true,
                    stopOnTicket = true,
                    upgradeHttpOnCas = true,
                    externalJar = jar,
                    maxHops = 5
                )
                ticket = ticketOf(pFollow.location) ?: ticketOf(pFollow.finalUrl)
            }
            if (ticket == null) {
                val plain = stripHtml(p1.body)
                val msg = when {
                    Regex("""用户名或密码错误|账号或密码错误|密码错误|用户名不存在|账号不存在""").containsMatchIn(plain) ->
                        "统一身份认证未通过（学号或密码错误）"
                    Regex("""name\s*=\s*["']?captcha\b""", RegexOption.IGNORE_CASE).containsMatchIn(p1.body) ||
                        Regex("""请输入验证码|验证码不正确|验证码错误""").containsMatchIn(plain) ->
                        "CAS 要求输入验证码，请先用手机浏览器登录一次云邮后再试"
                    p1.code == 423 -> "认证服务返回 423（临时锁定，请稍后再试）"
                    p1.code == 401 || p1.code == 200 -> "统一身份认证未通过（学号或密码不对，HTTP ${p1.code}）"
                    else -> "认证未返回 ticket（HTTP ${p1.code}）"
                }
                throw IllegalStateException(msg)
            }
        }
        trace += "③ 换取云邮 OAuth2 令牌"

        // Step 3: Exchange ticket for OAuth2 token (index.html uses grant_type=third)
        val tRes = httpReq(
            url = "$UC_API/ykt-basics/oauth/token",
            method = "POST",
            headers = mapOf(
                "Authorization" to UC_BASIC,
                "Tenant-Id" to "000000",
                "Accept" to "application/json",
                "Content-Type" to "application/x-www-form-urlencoded"
            ),
            body = formEncode(mapOf("ticket" to ticket, "grant_type" to "third"))
        )
        if (tRes.code != 200) throw IllegalStateException("换取令牌失败：HTTP ${tRes.code} ${tRes.body.take(100)}")
        val tJson = JSONObject(tRes.body)
        val token = tJson.optClean("access_token")
        if (token.isEmpty()) throw IllegalStateException(tJson.optClean("error_description", "未返回 access_token"))
        val refresh = tJson.optClean("refresh_token")
        val uid = tJson.optClean("user_id")

        val baseCfg = UcloudConfig(
            on = true,
            id = user.trim(),
            pwd = pwd,
            token = token,
            refreshToken = refresh,
            userId = uid,
            tokenAt = System.currentTimeMillis(),
            trace = trace
        )
        val fullCfg = ucFetchUserInfoAndRole(baseCfg, trace)
        return fullCfg.copy(trace = trace.takeLast(30))
    }

    fun ensureUcloudToken(inCfg: UcloudConfig): UcloudConfig {
        var cfg = inCfg
        val fresh = cfg.token.isNotEmpty() && (System.currentTimeMillis() - cfg.tokenAt) < 25 * 60 * 1000L
        if (!fresh) {
            var refreshed = false
            if (cfg.refreshToken.isNotEmpty()) {
                try {
                    val params = LinkedHashMap<String, String>()
                    params["grant_type"] = "refresh_token"
                    params["refresh_token"] = cfg.refreshToken
                    if (cfg.roleId.isNotEmpty()) params["identity"] = cfg.roleId
                    val r = httpReq(
                        url = "$UC_API/ykt-basics/oauth/token",
                        method = "POST",
                        headers = mapOf(
                            "Authorization" to UC_BASIC,
                            "Tenant-Id" to "000000",
                            "Accept" to "application/json",
                            "Content-Type" to "application/x-www-form-urlencoded"
                        ),
                        body = formEncode(params)
                    )
                    if (r.code == 200) {
                        val o = JSONObject(r.body)
                        val tok = o.optClean("access_token")
                        if (tok.isNotEmpty()) {
                            cfg = cfg.copy(
                                token = tok,
                                refreshToken = o.optClean("refresh_token").ifEmpty { cfg.refreshToken },
                                userId = o.optClean("user_id").ifEmpty { cfg.userId },
                                tokenAt = System.currentTimeMillis()
                            )
                            refreshed = true
                        }
                    }
                } catch (_: Throwable) {
                }
            }
            if (!refreshed) {
                return ucloudLogin(cfg.id, cfg.pwd)
            }
        }
        // 老配置自愈：若缺少 identity / roleId / userInfoId，补拉一次
        if (cfg.userId.isNotEmpty() && (cfg.identity.isEmpty() || cfg.roleId.isEmpty() || cfg.userInfoId.isEmpty())) {
            val tr = cfg.trace.toMutableList()
            cfg = ucFetchUserInfoAndRole(cfg, tr).copy(trace = tr.takeLast(30))
        }
        return cfg
    }

    fun syncUcloudTodos(inCfg: UcloudConfig): Pair<UcloudConfig, List<UcloudTodoItem>> {
        var cfg = ensureUcloudToken(inCfg)
        var uid = cfg.userInfoId.ifEmpty { cfg.userId }.ifEmpty { cfg.id }
        var r = httpReq("$UC_API/ykt-site/site/student/undone?userId=${URLEncoder.encode(uid, "UTF-8")}", headers = ucHeaders(cfg))
        if (r.code == 401 || r.code == 403) {
            cfg = ucloudLogin(cfg.id, cfg.pwd)
            uid = cfg.userInfoId.ifEmpty { cfg.userId }.ifEmpty { cfg.id }
            r = httpReq("$UC_API/ykt-site/site/student/undone?userId=${URLEncoder.encode(uid, "UTF-8")}", headers = ucHeaders(cfg))
        }
        if (r.code != 200) throw IllegalStateException("拉取云邮待办失败：HTTP ${r.code}")

        val dataObj = JSONObject(r.body).optJSONObject("data")
        val list = dataObj?.optJSONArray("undoneList") ?: JSONArray()
        val courseMap = cfg.courseMap.toMutableMap()

        val itemsRaw = mutableListOf<UcloudTodoItem>()
        for (i in 0 until list.length()) {
            val o = list.optJSONObject(i) ?: continue
            val id = o.optClean("activityId").ifEmpty { o.optClean("undoneId").ifEmpty { o.optClean("id") } }
            val ownSite = o.optClean("siteName").ifEmpty { o.optClean("site") }
            val endT = o.optClean("endTime").ifEmpty { o.optClean("endtime").ifEmpty { o.optClean("deadline") } }
            val title = o.optClean("activityName").ifEmpty { o.optClean("undoneName").ifEmpty { o.optClean("name", "未命名作业") } }
            itemsRaw += UcloudTodoItem(
                id = id,
                title = title,
                endTime = endT,
                siteName = ownSite,
                type = o.optInt("type", 3),
                activityType = o.optClean("assignmentType")
            )
        }

        // 反查缺失课程名（与 index.html ucResolveCourses 一致：siteRoleCode=2 + keyword=""）
        val needIds = itemsRaw.filter { it.id.isNotEmpty() && it.siteName.isEmpty() && !courseMap.containsKey(it.id) }.map { it.id }.toMutableSet()
        if (needIds.isNotEmpty()) {
            try {
                val cRes = httpReq(
                    "$UC_API/ykt-site/site/list/student/current?size=999999&current=1&userId=${URLEncoder.encode(uid, "UTF-8")}&siteRoleCode=2",
                    headers = ucHeaders(cfg)
                )
                if (cRes.code == 200) {
                    val records = JSONObject(cRes.body).optJSONObject("data")?.optJSONArray("records") ?: JSONArray()
                    for (i in 0 until records.length()) {
                        if (needIds.isEmpty()) break
                        val course = records.optJSONObject(i) ?: continue
                        val sid = course.optClean("id")
                        val sname = course.optClean("siteName")
                        if (sid.isEmpty() || sname.isEmpty()) continue
                        val wBody = JSONObject().apply {
                            put("userId", uid)
                            put("siteId", sid)
                            put("keyword", "")
                            put("current", 1)
                            put("size", 200)
                            put("studentAssignmentStatus", JSONObject.NULL)
                            put("status", 0)
                        }.toString()
                        val wRes = httpReq("$UC_API/ykt-site/work/student/list", "POST", ucHeaders(cfg, json = true), wBody)
                        if (wRes.code == 200) {
                            val wRecs = JSONObject(wRes.body).optJSONObject("data")?.optJSONArray("records") ?: JSONArray()
                            for (j in 0 until wRecs.length()) {
                                val w = wRecs.optJSONObject(j) ?: continue
                                val wid = w.optClean("id").ifEmpty { w.optClean("assignmentId") }
                                if (wid.isNotEmpty() && wid in needIds) {
                                    courseMap[wid] = sname
                                    needIds.remove(wid)
                                }
                            }
                        }
                    }
                }
            } catch (_: Throwable) {
            }
        }

        val items = itemsRaw.map {
            val resolvedSite = it.siteName.ifEmpty { courseMap[it.id].orEmpty() }
            if (resolvedSite != it.siteName) it.copy(siteName = resolvedSite) else it
        }
        val seen = cfg.seen.toMutableSet().apply { items.forEach { if (it.id.isNotEmpty()) add(it.id) } }
        val nextCfg = cfg.copy(
            lastSync = System.currentTimeMillis(),
            lastError = "",
            courseMap = courseMap,
            seen = seen,
            seenInit = true,
            items = items
        )
        return nextCfg to items
    }

    data class QrSignAttempt(
        val bodyJson: String,
        val status: Int,
        val code: String,
        val msg: String,
        val rawResp: String
    )

    data class QrSignResult(
        val ok: Boolean,
        val qrText: String,
        val summaryMsg: String,
        val okBody: String = "",
        val okResp: String = "",
        val tried: List<QrSignAttempt> = emptyList(),
        val updatedCfg: UcloudConfig? = null
    )

    /** 1:1 对齐 index.html [6c] ucSignCheckedBody（防翘课：URLDecoder 解码 createTime 与所有字段，userId 使用 oauth user_id） */
    internal fun ucSignCheckedBody(raw: String, userId: String): JSONObject? {
        val s = raw.trim()
        if (!s.contains("checkwork", ignoreCase = true)) return null
        val seg = s.substringAfter('|', "")
        if (seg.isEmpty()) return null
        val q = LinkedHashMap<String, String>()
        seg.split('&').forEach { pair ->
            val idx = pair.indexOf('=')
            if (idx <= 0) return@forEach
            val rawK = pair.substring(0, idx)
            val rawV = pair.substring(idx + 1)
            val k = try { URLDecoder.decode(rawK, "UTF-8") } catch (_: Throwable) { rawK }
            val v = try { URLDecoder.decode(rawV, "UTF-8") } catch (_: Throwable) { rawV }
            q[k] = v
        }
        val aid = q["id"]?.ifEmpty { null }
            ?: q["attendanceId"]?.ifEmpty { null }
            ?: q["attendanceDetailId"]?.ifEmpty { null }
            ?: return null
        val info = JSONObject().apply {
            put("attendanceId", aid)
            put("siteId", q["siteId"] ?: "")
            put("userId", userId)
            val lessonId = q["classLessonId"].orEmpty()
            if (lessonId.isNotEmpty()) put("classLessonId", lessonId)
        }
        return JSONObject().apply {
            put("attendanceDetailInfo", info)
            val ct = q["createTime"].orEmpty()
            if (ct.isNotEmpty()) put("qrCodeCreateTime", ct)
        }
    }

    /** 1:1 对齐 index.html [6c] ucSignCandidates */
    internal fun ucSignCandidates(rawQr: String, userId: String): List<JSONObject> {
        val raw = rawQr.trim()
        ucSignCheckedBody(raw, userId)?.let { return listOf(it) }

        val out = ArrayList<JSONObject>()
        val seen = HashSet<String>()
        fun push(o: JSONObject) {
            val k = o.toString()
            if (k != "{}" && seen.add(k)) out.add(o)
        }

        // ① URL：提取查询参数
        try {
            val u = URL(raw)
            val query = u.query.orEmpty()
            val q = JSONObject()
            if (query.isNotEmpty()) {
                query.split('&').forEach { pair ->
                    val idx = pair.indexOf('=')
                    if (idx > 0) {
                        val k = try { URLDecoder.decode(pair.substring(0, idx), "UTF-8") } catch (_: Throwable) { pair.substring(0, idx) }
                        val v = try { URLDecoder.decode(pair.substring(idx + 1), "UTF-8") } catch (_: Throwable) { pair.substring(idx + 1) }
                        q.put(k, v)
                    }
                }
                if (q.length() > 0) {
                    push(q)
                    val withUrl = JSONObject(q.toString()).put("url", raw)
                    push(withUrl)
                }
            }
            val last = u.path.orEmpty().split('/').lastOrNull { it.isNotEmpty() }.orEmpty()
            if (last.isNotEmpty() && !q.has("code") && !q.has("signCode")) {
                push(JSONObject().put("code", last))
            }
        } catch (_: Throwable) {
        }

        // ② JSON 对象：原样
        if (raw.startsWith("{")) {
            try {
                push(JSONObject(raw))
            } catch (_: Throwable) {
            }
        }

        // ③ 常见字段名各试一次
        listOf("code", "signCode", "qrCode", "signNum", "content", "sign", "no").forEach { k ->
            push(JSONObject().put(k, raw))
        }

        // ④ 最后原样塞 data
        push(JSONObject().put("data", raw))
        return out
    }

    fun submitQrSignDetailed(inCfg: UcloudConfig, rawQr: String): QrSignResult {
        val trimmed = rawQr.trim()
        if (trimmed.isEmpty()) return QrSignResult(false, trimmed, "二维码内容为空")
        var cfg = ensureUcloudToken(inCfg)
        if (cfg.userId.isEmpty()) {
            cfg = ucloudLogin(cfg.id, cfg.pwd)
        }
        if (cfg.token.isEmpty() || cfg.userId.isEmpty()) {
            return QrSignResult(false, trimmed, "云邮用户信息缺失，请先在设置中登录云邮")
        }

        val cands = ucSignCandidates(trimmed, cfg.userId)
        val tried = mutableListOf<QrSignAttempt>()
        var retriedLogin = false

        for (body in cands) {
            var r = httpReq(
                "$UC_API/ykt-site/attendancedetailinfo/sign",
                "POST",
                ucHeaders(cfg, json = true),
                body.toString()
            )
            if ((r.code == 401 || r.code == 403) && !retriedLogin) {
                retriedLogin = true
                cfg = ucloudLogin(cfg.id, cfg.pwd)
                val updatedBody = ucSignCheckedBody(trimmed, cfg.userId) ?: body
                r = httpReq(
                    "$UC_API/ykt-site/attendancedetailinfo/sign",
                    "POST",
                    ucHeaders(cfg, json = true),
                    updatedBody.toString()
                )
            }
            val respJson = try { JSONObject(r.body) } catch (_: Throwable) { null }
            val isOk = r.code == 200 && respJson != null && (
                respJson.optBoolean("success", false) ||
                    respJson.optInt("code", -1) == 200 ||
                    respJson.optInt("code", -1) == 0 ||
                    respJson.optClean("code") == "200"
                )
            val msg = respJson?.optClean("msg").orEmpty()
                .ifEmpty { respJson?.optClean("message").orEmpty() }
                .ifEmpty { "HTTP ${r.code}: ${r.body.take(150)}" }
            val codeStr = respJson?.optClean("code").orEmpty()
            if (isOk) {
                return QrSignResult(
                    ok = true,
                    qrText = trimmed,
                    summaryMsg = "签到成功：$msg",
                    okBody = body.toString(),
                    okResp = r.body.take(400),
                    tried = tried,
                    updatedCfg = cfg
                )
            }
            tried += QrSignAttempt(
                bodyJson = body.toString(),
                status = r.code,
                code = codeStr,
                msg = msg,
                rawResp = r.body.take(300)
            )
        }
        val lastMsg = tried.lastOrNull()?.msg?.ifEmpty { "这次没签上，查看下方接口返回" } ?: "签到未成功"
        return QrSignResult(
            ok = false,
            qrText = trimmed,
            summaryMsg = lastMsg,
            tried = tried,
            updatedCfg = cfg
        )
    }

    fun submitQrSign(inCfg: UcloudConfig, rawQr: String): Pair<UcloudConfig, String> {
        val res = submitQrSignDetailed(inCfg, rawQr)
        val cfg = res.updatedCfg ?: inCfg
        if (!res.ok) throw IllegalStateException(res.summaryMsg)
        return cfg to res.summaryMsg
    }

    fun fetchUcloudTodoDetail(inCfg: UcloudConfig, activityId: String): Pair<UcloudConfig, UcloudDetailCache> {
        val cfg = ensureUcloudToken(inCfg)
        val r = httpReq("$UC_API/ykt-site/work/detail?assignmentId=${URLEncoder.encode(activityId, "UTF-8")}", headers = ucHeaders(cfg))
        if (r.code != 200) throw IllegalStateException("HTTP ${r.code}")
        val d = JSONObject(r.body).optJSONObject("data") ?: JSONObject()
        val cache = UcloudDetailCache(
            content = stripHtml(d.optClean("assignmentContent")).take(4000),
            fullMark = d.optDouble("fullMark", 0.0),
            chapter = d.optClean("chapterName"),
            attachments = d.optJSONArray("assignmentResource")?.length() ?: 0,
            beginTime = d.optClean("assignmentBeginTime"),
            endTime = d.optClean("assignmentEndTime"),
            fetchedAt = System.currentTimeMillis()
        )
        return cfg to cache
    }

    fun fetchHwPreviewUrl(inCfg: UcloudConfig, resourceId: String, fileName: String): Pair<UcloudConfig, String> {
        val cfg = ensureUcloudToken(inCfg)
        val r = httpReq("$UC_API/blade-source/resource/preview-url?resourceId=${URLEncoder.encode(resourceId, "UTF-8")}", headers = ucHeaders(cfg))
        if (r.code != 200) throw IllegalStateException("预览地址请求失败 HTTP ${r.code}")
        val root = JSONObject(r.body)
        val raw = root.opt("data")
        val pv = when (raw) {
            is String -> raw
            is JSONObject -> raw.optClean("previewUrl")
            else -> ""
        }
        val viewer = if (raw is JSONObject) raw.optClean("onlinePreview") else ""
        val ext = fileName.substringAfterLast('.', "").lowercase()
        val url = when {
            pv.isEmpty() -> ""
            ext == "mp4" -> "https://ucloud.bupt.edu.cn/uclass/yunfile.html#/viewVideo?url=${URLEncoder.encode(pv, "UTF-8")}"
            ext == "mp3" || ext == "m4a" -> "https://ucloud.bupt.edu.cn/uclass/yunfile.html#/viewAudio?url=${URLEncoder.encode(pv, "UTF-8")}"
            viewer.isNotEmpty() -> viewer + URLEncoder.encode(pv, "UTF-8")
            else -> pv
        }
        if (url.isEmpty()) throw IllegalStateException("该附件暂无在线预览地址")
        return cfg to url
    }

    fun fetchHwExcellentDetail(
        inCfg: UcloudConfig,
        assignmentId: String,
        ex: HwExcellentItem
    ): Pair<UcloudConfig, HwExcellentItem> {
        val cfg = ensureUcloudToken(inCfg)
        val body = JSONObject().apply {
            if (ex.audiencesId.isNotEmpty()) put("audiencesId", ex.audiencesId)
            else {
                put("assignmentId", assignmentId)
                put("teamId", ex.teamId)
            }
        }.toString()
        val r = httpReq("$UC_API/ykt-site/work/excellenceDetail", "POST", ucHeaders(cfg, json = true), body)
        if (r.code != 200) throw IllegalStateException("获取优秀作业详情失败 HTTP ${r.code}")
        val d = JSONObject(r.body).optJSONObject("data") ?: JSONObject()
        val files = mutableListOf<HwAttachment>()
        val fa = d.optJSONArray("fileList") ?: JSONArray()
        for (i in 0 until fa.length()) {
            val f = fa.optJSONObject(i) ?: continue
            files += HwAttachment(
                resourceId = f.optClean("resourceId"),
                name = f.optClean("resourceName", "附件"),
                ext = f.optClean("resourceType")
            )
        }
        return cfg to ex.copy(
            content = stripHtml(d.optClean("assignmentContent")).take(4000),
            comment = stripHtml(d.optClean("assignmentComment")),
            files = files
        )
    }

    /* ===================== 3. 作业档案 ===================== */

    private fun hwExtractAttachmentIds(d: JSONObject): String {
        d.optJSONArray("attachmentIds")?.let { arr ->
            val ids = mutableListOf<String>()
            for (i in 0 until arr.length()) {
                val s = if (arr.isNull(i)) "" else arr.optString(i).trim()
                if (s.isNotEmpty() && s != "null") ids += s
            }
            if (ids.isNotEmpty()) return ids.joinToString(",")
        }
        val raw = d.optClean("attachmentIds").trim()
        if (raw.isEmpty() || raw == "[]") return ""
        return raw.removePrefix("[").removeSuffix("]")
            .split(',')
            .map { it.trim().trim('"', '\'') }
            .filter { it.isNotEmpty() && it != "null" }
            .joinToString(",")
    }

    private fun hwFmtTimestamp(ms: Long): String {
        if (ms <= 0L) return ""
        return try {
            Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault())
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
        } catch (_: Throwable) {
            ""
        }
    }

    private fun hwPickGradeTime(d: JSONObject): String {
        val keys = listOf(
            "correctTime", "correctAt", "gradeTime", "gradedAt",
            "remarkTime", "remarkAt", "reviewTime", "markTime", "checkTime",
            "assignmentCorrectTime", "assignmentUpdateTime", "correctDate", "updateTime", "modifyTime"
        )
        for (k in keys) {
            val raw = d.optClean(k).trim()
            if (raw.isEmpty()) continue
            val m = Regex("""^(\d{4})-(\d{1,2})-(\d{1,2})[T\s](\d{1,2}:\d{2})""").find(raw)
            if (m != null) {
                return "${m.groupValues[1]}-${m.groupValues[2].padStart(2, '0')}-${m.groupValues[3].padStart(2, '0')} ${m.groupValues[4]}"
            }
            return raw.take(16)
        }
        return ""
    }

    fun fetchHwTermsAndCourses(inCfg: UcloudConfig): Triple<UcloudConfig, List<HwTerm>, List<HwCourse>> {
        var cfg = ensureUcloudToken(inCfg)
        var uid = cfg.userInfoId.ifEmpty { cfg.userId }.ifEmpty { cfg.id }

        // ① 学期列表：正确路径是 /ykt-site/base-term/list?size=9999（同时兼容 /ykt-basics 兜底）
        var tRes = httpReq("$UC_API/ykt-site/base-term/list?size=9999", headers = ucHeaders(cfg))
        if (tRes.code == 401 || tRes.code == 403) {
            cfg = ucloudLogin(cfg.id, cfg.pwd)
            uid = cfg.userInfoId.ifEmpty { cfg.userId }.ifEmpty { cfg.id }
            tRes = httpReq("$UC_API/ykt-site/base-term/list?size=9999", headers = ucHeaders(cfg))
        }
        if (tRes.code != 200) {
            val fallback = httpReq("$UC_API/ykt-basics/base-term/list?size=9999&current=1", headers = ucHeaders(cfg))
            if (fallback.code == 200) tRes = fallback
        }
        if (tRes.code != 200) throw IllegalStateException("拉取学期列表失败：HTTP ${tRes.code}")

        val allTerms = mutableListOf<HwTerm>()
        val tRoot = JSONObject(tRes.body)
        val tDataObj = tRoot.optJSONObject("data")
        val tRecs = tDataObj?.optJSONArray("records") ?: tRoot.optJSONArray("data") ?: JSONArray()
        for (i in 0 until tRecs.length()) {
            val t = tRecs.optJSONObject(i) ?: continue
            val tid = t.optClean("id")
            if (tid.isEmpty()) continue
            val isCur = t.optInt("isCurrent", 0) == 1 || t.optBoolean("isCurrent", false)
            allTerms += HwTerm(
                id = tid,
                name = t.optClean("termName").ifEmpty { t.optClean("name", "学期") },
                isCurrent = isCur
            )
        }

        // ② 课程列表：current + history（带 siteRoleCode=2）
        val courses = mutableListOf<HwCourse>()
        val seenSite = HashSet<String>()
        var cRes = httpReq(
            "$UC_API/ykt-site/site/list/student/current?size=99999&current=1&userId=${URLEncoder.encode(uid, "UTF-8")}&siteRoleCode=2",
            headers = ucHeaders(cfg)
        )
        if (cRes.code == 401 || cRes.code == 403) {
            cfg = ucloudLogin(cfg.id, cfg.pwd)
            uid = cfg.userInfoId.ifEmpty { cfg.userId }.ifEmpty { cfg.id }
            cRes = httpReq(
                "$UC_API/ykt-site/site/list/student/current?size=99999&current=1&userId=${URLEncoder.encode(uid, "UTF-8")}&siteRoleCode=2",
                headers = ucHeaders(cfg)
            )
        }
        if (cRes.code != 200) throw IllegalStateException("拉取课程列表失败：HTTP ${cRes.code}")
        fun absorbCourses(body: String) {
            val recs = JSONObject(body).optJSONObject("data")?.optJSONArray("records") ?: JSONArray()
            for (i in 0 until recs.length()) {
                val c = recs.optJSONObject(i) ?: continue
                val sid = c.optClean("id")
                if (sid.isNotEmpty() && seenSite.add(sid)) {
                    courses += HwCourse(
                        siteId = sid,
                        termId = c.optClean("termId"),
                        name = c.optClean("siteName"),
                        teacher = c.optClean("teacherName"),
                        termName = c.optClean("termName")
                    )
                }
            }
        }
        absorbCourses(cRes.body)

        try {
            val hRes = httpReq(
                "$UC_API/ykt-site/site/list/student/history?size=99999&current=1&userId=${URLEncoder.encode(uid, "UTF-8")}&siteRoleCode=2",
                headers = ucHeaders(cfg)
            )
            if (hRes.code == 200) absorbCourses(hRes.body)
        } catch (_: Throwable) {
        }

        // ③ 对齐 index.html 9927-9942：按课程出现的 termId 筛选有效学期并按学期顺序排列
        val termById = allTerms.associateBy { it.id }
        val orderById = allTerms.mapIndexed { idx, t -> t.id to idx }.toMap()
        val courseTermMap = LinkedHashMap<String, HwTerm>()
        for (c in courses) {
            if (c.termId.isEmpty() || courseTermMap.containsKey(c.termId)) continue
            courseTermMap[c.termId] = termById[c.termId] ?: HwTerm(
                id = c.termId,
                name = c.termName.ifEmpty { "学期 ${c.termId}" },
                isCurrent = false
            )
        }
        val filteredTerms = if (courseTermMap.isNotEmpty()) {
            courseTermMap.values.sortedWith(compareBy { orderById[it.id] ?: 999 })
        } else {
            val fallback = allTerms.firstOrNull { it.isCurrent } ?: allTerms.firstOrNull()
            if (fallback != null) listOf(fallback) else emptyList()
        }
        return Triple(cfg, filteredTerms, courses)
    }

    fun fetchHwTermItems(
        inCfg: UcloudConfig,
        courses: List<HwCourse>,
        gradedSeen: MutableMap<String, Long>
    ): Pair<UcloudConfig, List<HwSubmissionItem>> {
        var cfg = ensureUcloudToken(inCfg)
        var uid = cfg.userInfoId.ifEmpty { cfg.userId }.ifEmpty { cfg.id }

        data class RawHwEntry(
            val aid: String,
            val siteId: String,
            val courseName: String,
            val title: String,
            val chapterName: String,
            val submitTime: String,
            val endTime: String,
            val isOverTime: Boolean,
            val initStatus: Int
        )

        val collected = mutableListOf<RawHwEntry>()
        val seenIds = HashSet<String>()
        var retriedAuth = false

        for (course in courses) {
            val body = JSONObject().apply {
                put("siteId", course.siteId)
                put("userId", uid)
                put("keyword", "")
                put("chapterId", "")
                put("nodeId", "")
                put("current", 1)
                put("size", 500)
                put("studentAssignmentStatus", JSONObject.NULL)
                put("status", 0)
                put("sortType", JSONObject.NULL)
            }.toString()
            var r = httpReq("$UC_API/ykt-site/work/student/list", "POST", ucHeaders(cfg, json = true), body)
            if ((r.code == 401 || r.code == 403) && !retriedAuth) {
                retriedAuth = true
                cfg = ucloudLogin(cfg.id, cfg.pwd)
                uid = cfg.userInfoId.ifEmpty { cfg.userId }.ifEmpty { cfg.id }
                r = httpReq("$UC_API/ykt-site/work/student/list", "POST", ucHeaders(cfg, json = true), body)
            }
            if (r.code != 200) continue
            val recs = JSONObject(r.body).optJSONObject("data")?.optJSONArray("records") ?: JSONArray()
            for (i in 0 until recs.length()) {
                val o = recs.optJSONObject(i) ?: continue
                val aid = o.optClean("id").ifEmpty { o.optClean("assignmentId") }
                val submitTime = o.optClean("submitTime")
                if (aid.isEmpty() || submitTime.isEmpty() || !seenIds.add(aid)) continue
                collected += RawHwEntry(
                    aid = aid,
                    siteId = course.siteId,
                    courseName = course.name,
                    title = o.optClean("assignmentTitle"),
                    chapterName = o.optClean("chapterName"),
                    submitTime = submitTime,
                    endTime = o.optClean("assignmentEndTime"),
                    isOverTime = o.optInt("isOverTime", 0) == 1 || o.optBoolean("isOverTime", false),
                    initStatus = o.optInt("assignmentStatus", 0)
                )
            }
        }

        // 先去重再并发拉详情，对齐 index.html 9379：同「课程 + 标题」只留提交时间最新的一份
        data class MergedHwEntry(val entry: RawHwEntry, val dupCount: Int)
        val dedupMap = LinkedHashMap<String, MergedHwEntry>()
        for (entry in collected.sortedByDescending { it.submitTime }) {
            val key = "${entry.courseName}\u0001${entry.title}"
            val prev = dedupMap[key]
            if (prev == null) dedupMap[key] = MergedHwEntry(entry, 1)
            else dedupMap[key] = prev.copy(dupCount = prev.dupCount + 1)
        }
        val toFetch = dedupMap.values.toList()
        if (toFetch.isEmpty()) return cfg to emptyList()

        // 4 并发拉取每条已提交作业的批改情况、附件列表与优秀作业
        val pool = Executors.newFixedThreadPool(4)
        return try {
            val cfgSnap = cfg
            val tasks = toFetch.map { m ->
                Callable<HwSubmissionItem> {
                    val item = m.entry
                    var status = item.initStatus
                    var score = ""
                    var comment = ""
                    var groupScore = ""
                    var evalAvg = ""
                    var evalInfos = ""
                    var myContent = ""
                    var gradeTime = ""
                    var gradeLocal = false
                    val myFiles = mutableListOf<HwAttachment>()

                    try {
                        val vRes = httpReq(
                            "$UC_API/ykt-site/work/submit-view?assignmentId=${URLEncoder.encode(item.aid, "UTF-8")}",
                            headers = ucHeaders(cfgSnap)
                        )
                        if (vRes.code == 200) {
                            val d = JSONObject(vRes.body).optJSONObject("data")
                            if (d != null) {
                                status = d.optInt("assignmentStatus", status)
                                score = d.optClean("assignmentScore").ifEmpty { d.optClean("score") }
                                comment = stripHtml(d.optClean("assignmentComment"))
                                groupScore = d.optClean("groupScore")
                                evalAvg = d.optClean("evaluationAverageScore")
                                myContent = stripHtml(d.optClean("assignmentContent")).take(4000)

                                d.optJSONArray("evaluationInfos")?.let { ea ->
                                    val parts = mutableListOf<String>()
                                    for (ei in 0 until ea.length()) {
                                        val ev = ea.optJSONObject(ei) ?: continue
                                        val txt = ev.optClean("evaluation")
                                        val sc = ev.optClean("score")
                                        if (txt.isNotEmpty() || sc.isNotEmpty()) {
                                            parts += if (sc.isNotEmpty()) "$txt（$sc）" else txt
                                        }
                                    }
                                    evalInfos = parts.joinToString("；")
                                }

                                val realT = hwPickGradeTime(d)
                                if (status == 1) {
                                    if (realT.isNotEmpty()) {
                                        gradeTime = realT
                                        gradeLocal = false
                                    } else {
                                        val ts = synchronized(gradedSeen) {
                                            gradedSeen.getOrPut(item.aid) { System.currentTimeMillis() }
                                        }
                                        gradeTime = hwFmtTimestamp(ts)
                                        gradeLocal = true
                                    }
                                }

                                val attIds = hwExtractAttachmentIds(d)
                                if (attIds.isNotEmpty()) {
                                    val fRes = httpReq(
                                        "$UC_API/blade-source/resource/list/byId?resourceIds=${URLEncoder.encode(attIds, "UTF-8")}",
                                        headers = ucHeaders(cfgSnap)
                                    )
                                    if (fRes.code == 200) {
                                        val arr = JSONObject(fRes.body).optJSONArray("data") ?: JSONArray()
                                        for (k in 0 until arr.length()) {
                                            val f = arr.optJSONObject(k) ?: continue
                                            myFiles += HwAttachment(
                                                resourceId = f.optClean("id").ifEmpty { f.optClean("resourceId") },
                                                name = f.optClean("name").ifEmpty { f.optClean("resourceName", "附件") },
                                                sizeText = f.optClean("fileSizeUnit"),
                                                ext = f.optClean("ext"),
                                                recordFlag = f.optInt("recordFlag", 0)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    } catch (_: Throwable) {
                    }

                    val seenAt = if (status == 1) {
                        synchronized(gradedSeen) { gradedSeen[item.aid] ?: System.currentTimeMillis() }
                    } else 0L

                    val exList = mutableListOf<HwExcellentItem>()
                    try {
                        val exBody = JSONObject().apply {
                            put("assignmentId", item.aid)
                            put("current", 1)
                            put("size", 999)
                            put("sortColumn", "")
                            put("sortType", JSONObject.NULL)
                            put("excellence", 1)
                        }.toString()
                        val exRes = httpReq("$UC_API/ykt-site/work/all-assignment", "POST", ucHeaders(cfgSnap, json = true), exBody)
                        if (exRes.code == 200) {
                            val exRecs = JSONObject(exRes.body).optJSONObject("data")?.optJSONArray("records") ?: JSONArray()
                            for (k in 0 until exRecs.length()) {
                                val e = exRecs.optJSONObject(k) ?: continue
                                exList += HwExcellentItem(
                                    audiencesId = e.optClean("audiencesId"),
                                    teamId = e.optClean("teamId"),
                                    userId = e.optClean("userId"),
                                    account = e.optClean("account"),
                                    realName = e.optClean("realName"),
                                    className = e.optClean("name"),
                                    score = e.optClean("score"),
                                    submitTime = e.optClean("submitTime")
                                )
                            }
                        }
                    } catch (_: Throwable) {
                    }

                    HwSubmissionItem(
                        id = item.aid,
                        siteId = item.siteId,
                        courseName = item.courseName,
                        title = item.title,
                        chapterName = item.chapterName,
                        submitTime = item.submitTime,
                        gradeTime = gradeTime,
                        gradeLocal = gradeLocal,
                        endTime = item.endTime,
                        isOverTime = item.isOverTime,
                        status = status,
                        score = score,
                        comment = comment,
                        groupScore = groupScore,
                        evalAvgScore = evalAvg,
                        evalInfos = evalInfos,
                        myContent = myContent,
                        myFiles = myFiles,
                        excellent = exList,
                        mergedCount = m.dupCount,
                        gradedSeenAt = seenAt
                    )
                }
            }
            val items = pool.invokeAll(tasks).map { it.get() }
            cfg to items
        } finally {
            pool.shutdownNow()
        }
    }

    /* ===================== 4. 信息门户资讯 ===================== */

    fun portalCookieHeader(cookieJarJson: String): String =
        HostCookieJar.fromString(cookieJarJson).headerForHost(PT_HOST)

    private fun isPortalLoginPage(html: String): Boolean {
        if (html.contains("退出") && html.contains("门户首页")) return false
        return Regex("""authserver|统一身份认证|系统发生错误|错误标识码""").containsMatchIn(html)
    }

    private fun portalLoginJar(user: String, pwd: String): HostCookieJar {
        require(user.isNotBlank() && pwd.isNotBlank()) { "请先在设置中填写信息门户账号与密码" }
        val casUrl = "$UC_CAS?service=${URLEncoder.encode(PT_BASE, "UTF-8")}"
        val jar = HostCookieJar()

        var r = httpReq(
            url = casUrl,
            followRedirects = true,
            stopOnTicket = true,
            upgradeHttpOnCas = true,
            externalJar = jar,
            maxHops = 5
        )
        var ticket = ticketOf(r.location) ?: ticketOf(r.finalUrl)
        if (ticket == null) {
            val exec = execOf(r.body)
            if (exec.isEmpty()) {
                val msg = when {
                    r.body.isEmpty() -> "门户认证页返回空（HTTP ${r.code}，请检查网络或校园网连接）"
                    Regex("""上网认证|校园网|准入|请先登录""").containsMatchIn(r.body) -> "连到了校园网准入网关，请先联网后再试"
                    else -> "门户认证页未找到 execution（HTTP ${r.code}）"
                }
                throw IllegalStateException(msg)
            }
            val postTarget = r.finalUrl.ifEmpty { casUrl }
            val p = httpReq(
                url = postTarget,
                method = "POST",
                headers = mapOf(
                    "Content-Type" to "application/x-www-form-urlencoded",
                    "Referer" to postTarget
                ),
                body = formEncode(
                    mapOf(
                        "username" to user.trim(),
                        "password" to pwd,
                        "type" to "username_password",
                        "execution" to exec,
                        "_eventId" to "submit"
                    )
                ),
                followRedirects = false,
                externalJar = jar
            )
            ticket = ticketOf(p.location) ?: ticketOf(p.finalUrl)
            if (ticket == null) {
                val msg = when {
                    p.code == 423 -> "认证服务返回 423（临时锁定，请稍后再试）"
                    p.code == 401 || p.code == 200 -> "统一身份认证未通过（学工号或密码错误，HTTP ${p.code}）"
                    else -> "门户认证未返回 ticket（HTTP ${p.code}）"
                }
                throw IllegalStateException(msg)
            }
        }

        // 用 ticket 在 my.bupt.edu.cn 建会话（注意：my.bupt.edu.cn 仅走 HTTP，不得升级到 HTTPS）
        val r3 = httpReq(
            url = "$PT_BASE?ticket=${URLEncoder.encode(ticket, "UTF-8")}",
            followRedirects = true,
            stopOnTicket = false,
            upgradeHttpOnCas = false,
            externalJar = jar,
            maxHops = 8
        )
        if (isPortalLoginPage(r3.body) || jar.countForHost(PT_HOST) == 0) {
            throw IllegalStateException("门户会话未能建立（返回登录页或无 cookie）")
        }
        return jar
    }

    private fun ptFetchHtml(
        url: String,
        user: String,
        pwd: String,
        cachedCookieJson: String
    ): Pair<String, String> {
        var jar = HostCookieJar.fromString(cachedCookieJson)
        if (jar.headerForHost(PT_HOST).isEmpty() && user.isNotBlank() && pwd.isNotBlank()) {
            jar = portalLoginJar(user, pwd)
        }
        var r = httpReq(
            url = url,
            headers = mapOf("Referer" to PT_BASE),
            followRedirects = false,
            externalJar = jar
        )
        val bad = r.code in 300..399 || r.code != 200 || isPortalLoginPage(r.body) || r.body.isBlank()
        if (bad) {
            if (user.isBlank() || pwd.isBlank()) {
                throw IllegalStateException("需要信息门户登录凭据（请在设置中填学工号和密码）")
            }
            jar = portalLoginJar(user, pwd)
            r = httpReq(
                url = url,
                headers = mapOf("Referer" to PT_BASE),
                followRedirects = false,
                externalJar = jar
            )
            if (r.code != 200 || isPortalLoginPage(r.body)) {
                throw IllegalStateException("门户返回 HTTP ${r.code}（需要登录态或认证失败）")
            }
        }
        return r.body to jar.toJsonString()
    }

    fun syncPortalNews(
        user: String,
        pwd: String,
        kind: String,
        page: Int = 1,
        cachedCookieJson: String = ""
    ): Pair<List<PortalArticle>, String> {
        val baseUrl = if (kind == "lecture") {
            "${PT_BASE}hy_list.jsp?urltype=tree.TreeTempUrl&wbtreeid=1300"
        } else {
            "${PT_BASE}list.jsp?urltype=tree.TreeTempUrl&wbtreeid=1154"
        }
        val listUrl = if (page > 1) "$baseUrl&PAGENUM=$page" else baseUrl
        val (html, nextCookieJson) = ptFetchHtml(listUrl, user, pwd, cachedCookieJson)

        val out = mutableListOf<PortalArticle>()
        val seenUrl = HashSet<String>()

        if (kind == "lecture") {
            // 1:1 对齐 index.html ptParseLectureList (<tr class="...hyitem...">)
            val trRe = Regex("""<tr[^>]*class=["'][^"']*hyitem[^"']*["'][^>]*>([\s\S]{0,1600}?)</tr>""", RegexOption.IGNORE_CASE)
            val hrefRe = Regex("""href\s*=\s*["']([^"']*hy_content[^"']*)["']""", RegexOption.IGNORE_CASE)
            val titleAttrRe = Regex("""<a[^>]+title=["']([^"']*)["']""", RegexOption.IGNORE_CASE)
            val aTextRe = Regex("""<a[^>]*>([\s\S]{0,300}?)</a>""", RegexOption.IGNORE_CASE)
            val whereRe = Regex("""timedatedown[^>]*>([\s\S]{0,80}?)</""", RegexOption.IGNORE_CASE)
            val dateRe = Regex("""timedateup[^>]*>([\s\S]{0,60}?)</""", RegexOption.IGNORE_CASE)

            for (m in trRe.findAll(html)) {
                val blk = m.groupValues[1]
                val href = hrefRe.find(blk)?.groupValues?.get(1)?.let { htmlUnescape(it) } ?: continue
                val abs = try { URL(URL(PT_BASE), href).toString() } catch (_: Throwable) { href }
                if (!seenUrl.add(abs)) continue
                val title = stripHtml(titleAttrRe.find(blk)?.groupValues?.get(1) ?: aTextRe.find(blk)?.groupValues?.get(1).orEmpty()).trim()
                if (title.isEmpty()) continue
                val where = stripHtml(whereRe.find(blk)?.groupValues?.get(1).orEmpty()).trim()
                val dateRaw = stripHtml(dateRe.find(blk)?.groupValues?.get(1).orEmpty()).trim()
                val date = dateRaw.ifEmpty { Regex("""\d{4}-\d{2}-\d{2}""").find(stripHtml(blk))?.value.orEmpty() }
                val id = Regex("""wbnewsid=(\d+)""").find(abs)?.groupValues?.get(1) ?: md5Hex(abs).take(12)
                out += PortalArticle(
                    id = id,
                    kind = kind,
                    title = title,
                    url = abs,
                    dept = where,
                    date = date
                )
            }
        } else {
            // 1:1 对齐 index.html ptParseNoticeList (class="author" / class="time")
            val liNoticeRe = Regex(
                """<li[^>]*>\s*<a[^>]+href\s*=\s*["']([^"']*content\.jsp[^"']*)["'][^>]*>([\s\S]{0,360}?)</a>([\s\S]{0,500}?)</li>""",
                RegexOption.IGNORE_CASE
            )
            val authorRe = Regex("""class=["']author["'][^>]*>([\s\S]{0,100}?)</""", RegexOption.IGNORE_CASE)
            val timeRe = Regex("""class=["']time["'][^>]*>([\s\S]{0,60}?)</""", RegexOption.IGNORE_CASE)

            for (m in liNoticeRe.findAll(html)) {
                val href = htmlUnescape(m.groupValues[1])
                val abs = try { URL(URL(PT_BASE), href).toString() } catch (_: Throwable) { href }
                if (!seenUrl.add(abs)) continue
                val title = stripHtml(m.groupValues[2]).trim()
                if (title.isEmpty()) continue
                val tail = m.groupValues[3]
                val dept = stripHtml(authorRe.find(tail)?.groupValues?.get(1).orEmpty())
                    .ifEmpty { Regex("""[\[【]([^\]】]{2,20})[\]】]""").find(stripHtml(tail))?.groupValues?.get(1).orEmpty() }
                val date = stripHtml(timeRe.find(tail)?.groupValues?.get(1).orEmpty())
                    .ifEmpty { Regex("""\d{4}-\d{2}-\d{2}""").find(stripHtml(tail))?.value.orEmpty() }
                val id = Regex("""wbnewsid=(\d+)""").find(abs)?.groupValues?.get(1) ?: md5Hex(abs).take(12)
                out += PortalArticle(id = id, kind = kind, title = title, url = abs, dept = dept, date = date)
            }
        }

        // 兜底：若页面改版成其它列表结构，扫全部 content.jsp / hy_content 链接
        if (out.isEmpty()) {
            val aRegex = Regex(
                """<a[^>]+href=["']([^"']*(?:content\.jsp|hy_content)[^"']*)["'][^>]*>(.*?)</a>""",
                setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
            )
            for (m in aRegex.findAll(html)) {
                val href = htmlUnescape(m.groupValues[1])
                val abs = try { URL(URL(PT_BASE), href).toString() } catch (_: Throwable) { href }
                if (!seenUrl.add(abs)) continue
                val title = stripHtml(m.groupValues[2]).trim()
                if (title.length < 3) continue
                val ctxEnd = (m.range.last + 260).coerceAtMost(html.length)
                val tail = stripHtml(html.substring(m.range.last + 1, ctxEnd))
                val date = Regex("""\d{4}-\d{2}-\d{2}""").find(tail)?.value.orEmpty()
                val id = Regex("""wbnewsid=(\d+)""").find(abs)?.groupValues?.get(1) ?: md5Hex(abs).take(12)
                out += PortalArticle(id = id, kind = kind, title = title, url = abs, date = date)
                if (out.size >= 40) break
            }
        }
        return out to nextCookieJson
    }

    private fun sliceBalancedDiv(html: String, openRegex: Regex): String {
        val m = openRegex.find(html) ?: return ""
        val start = m.range.last + 1
        var depth = 1
        val divTagRe = Regex("""<d(?:iv)\b|</div>""", RegexOption.IGNORE_CASE)
        for (t in divTagRe.findAll(html, start)) {
            if (t.value.startsWith("</")) depth-- else depth++
            if (depth == 0) return html.substring(start, t.range.first)
        }
        return html.substring(start, (start + 40000).coerceAtMost(html.length))
    }

    fun fetchPortalDetail(
        user: String,
        pwd: String,
        article: PortalArticle,
        cachedCookieJson: String = ""
    ): Pair<PortalArticle, String> {
        val (src, nextCookieJson) = ptFetchHtml(article.url, user, pwd, cachedCookieJson)

        val pats = listOf(
            Regex("""<div[^>]*id=['"]vsb_content['"][^>]*>""", RegexOption.IGNORE_CASE),
            Regex("""<div[^>]*name=["']articlecontent["'][^>]*>""", RegexOption.IGNORE_CASE),
            Regex("""<div[^>]*id=['"]article_content['"][^>]*>""", RegexOption.IGNORE_CASE),
            Regex("""<div[^>]*class=["'][^"']*v_news_content[^"']*["'][^>]*>""", RegexOption.IGNORE_CASE),
            Regex("""<div[^>]*class=['"]content['"][^>]*>""", RegexOption.IGNORE_CASE),
            Regex("""<div[^>]*id=['"](?:content|mainContent|main_content)['"][^>]*>""", RegexOption.IGNORE_CASE)
        )
        var rawBody = ""
        for (pat in pats) {
            val cand = sliceBalancedDiv(src, pat)
            if (cand.contains("<p", true) || cand.contains("<br", true) || stripHtml(cand).length > 40) {
                rawBody = cand
                break
            }
        }
        if (rawBody.isEmpty()) {
            // 挑含 <p> 且字数最多的 div 兜底
            var bestLen = 0
            val anyDiv = Regex("""<div[^>]*>""", RegexOption.IGNORE_CASE)
            var guard = 0
            for (m in anyDiv.findAll(src)) {
                if (guard++ >= 120) break
                if (Regex("""class=["'][^"']*(?:nav|menu|footer|header|crumb|search|sidebar|copyright|link)""", RegexOption.IGNORE_CASE).containsMatchIn(m.value)) continue
                val sub = sliceBalancedDiv(src.substring(m.range.first), Regex("""<div[^>]*>""", RegexOption.IGNORE_CASE))
                if (!sub.contains("<p", true)) continue
                val len = stripHtml(sub).length
                if (len > bestLen) {
                    bestLen = len
                    rawBody = sub
                }
            }
        }
        if (rawBody.isEmpty()) rawBody = src

        val tailStrongRe = Regex("""^(?:公告附件|附件下载|相关通知|相关新闻|相关阅读|相关链接|相关文章|打印$|关\s*闭$|读取内容中|版权所有|Copyright|getRelateNews|function\s|var\s+\w+\s*=)""", RegexOption.IGNORE_CASE)
        val cleanText = stripHtml(
            rawBody
                .replace(Regex("""<br\s*/?>""", RegexOption.IGNORE_CASE), "\n")
                .replace(Regex("""</(?:p|div|li|tr|h[1-6]|blockquote)>""", RegexOption.IGNORE_CASE), "\n")
        ).lines()
            .map { it.trim() }
            .filter { line ->
                line.isNotEmpty() &&
                    !Regex("""^[a-z_\$][\w\$]*\s*\([^)]*\)\s*;$|^function\s+\w+\s*\(|^var\s+\w+\s*=""").containsMatchIn(line) &&
                    !Regex("""^(?:当前位置|您的位置|首页\s*[>›]|字体[:：]|字号|浏览[:：]|点击[:：])""").containsMatchIn(line)
            }
            .let { lines ->
                var end = lines.size
                for (j in lines.indices.reversed()) {
                    if (lines[j].length <= 80 && tailStrongRe.containsMatchIn(lines[j])) end = j
                }
                lines.subList(0, end)
            }
            .joinToString("\n")
            .trim()

        val atts = mutableListOf<PortalAttachment>()
        val seenAtt = HashSet<String>()
        val allLinkRe = Regex("""<a\b[^>]*href\s*=\s*["']([^"']+)["'][^>]*>([\s\S]{0,200}?)</a>""", RegexOption.IGNORE_CASE)
        for (am in allLinkRe.findAll(src)) {
            if (atts.size >= 24) break
            val href = htmlUnescape(am.groupValues[1]).trim()
            if (!Regex("""download\.jsp|wbfileid=|DownloadAttach|/(?:attach(?:ment)?s?|upload)s?/""", RegexOption.IGNORE_CASE).containsMatchIn(href)) continue
            if (href.startsWith("javascript:", true) || href.startsWith("#")) continue
            val u = try { URL(URL(article.url), href).toString() } catch (_: Throwable) { href }
            if (!seenAtt.add(u)) continue
            val n = stripHtml(am.groupValues[2])
                .replace(Regex("""^(?:附\s*件\s*\d*\s*[.．、,，:：\-—–_]*|公告附件如下[:：]?|下载附件[:：]?)\s*"""), "")
                .replace(Regex("""[（(]\s*(?:点击)?下载\s*[)）]"""), "")
                .trim()
                .ifEmpty { "附件" }
            atts += PortalAttachment(n, u)
        }
        return article.copy(
            bodyText = cleanText.ifEmpty { "（正文为空或仅含图片，可点右上角浏览器打开原文）" },
            attachments = atts
        ) to nextCookieJson
    }

    /* ===================== 5. 教务系统在线课表 ===================== */

    data class TimetableFetchResult(
        val data: TimetableData,
        val startMonday: String,
        val totalWeeks: Int,
        val trace: List<String>
    )

    private val JW_KEY = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/="

    /** 兼容强智编码器（按 UTF-16 charCodeAt 分组编码） */
    fun jwEncodeInp(input: String): String {
        val sb = StringBuilder()
        var i = 0
        while (i < input.length) {
            val c1 = input[i++].code
            val has2 = i < input.length
            val c2 = if (has2) input[i++].code else -1
            val has3 = i < input.length
            val c3 = if (has3) input[i++].code else -1

            val enc1 = (c1 shr 2) and 0x3F
            val enc2 = ((c1 and 3) shl 4) or (if (has2) ((c2 shr 4) and 0xF) else 0)
            val enc3 = if (!has2) 64 else (((c2 and 15) shl 2) or (if (has3) ((c3 shr 6) and 3) else 0))
            val enc4 = if (!has2 || !has3) 64 else (c3 and 63)

            sb.append(JW_KEY[enc1]).append(JW_KEY[enc2]).append(JW_KEY[enc3]).append(JW_KEY[enc4])
        }
        return sb.toString()
    }

    private fun jwHttpFollow(
        url: String,
        method: String = "GET",
        jar: MutableMap<String, String>,
        body: String? = null
    ): HttpResp {
        var curUrl = url
        var curMethod = method
        var curBody = body
        var last = HttpResp(0, "", emptyMap())

        fun absorb(h: Map<String, List<String>>) {
            h.forEach { (k, vs) ->
                if (k != null && k.equals("Set-Cookie", true)) {
                    vs.forEach { line ->
                        val kv = line.substringBefore(';').trim()
                        val idx = kv.indexOf('=')
                        if (idx > 0) jar[kv.substring(0, idx).trim()] = kv.substring(idx + 1).trim()
                    }
                }
            }
        }

        for (hop in 0 until 6) {
            val hdr = LinkedHashMap<String, String>()
            hdr["Referer"] = "$JW_BASE/jsxsd/"
            val ck = jar.entries.joinToString("; ") { "${it.key}=${it.value}" }
            if (ck.isNotEmpty()) hdr["Cookie"] = ck
            if (curBody != null) hdr["Content-Type"] = "application/x-www-form-urlencoded"
            last = httpReq(
                url = curUrl,
                method = curMethod,
                headers = hdr,
                body = curBody,
                followRedirects = false
            )
            absorb(last.headers)
            if (last.code in listOf(301, 302, 303, 307, 308)) {
                val loc = header(last.headers, "Location") ?: break
                curUrl = try { URL(URL(curUrl), loc).toString() } catch (_: Throwable) { break }
                if (last.code == 303 || ((last.code == 301 || last.code == 302) && curMethod == "POST")) {
                    curMethod = "GET"
                    curBody = null
                }
                continue
            }
            break
        }
        return last
    }

    private fun jwIsLoginPage(html: String): Boolean {
        return html.contains("id=\"userAccount\"") || html.contains("name=\"encoded\"") || html.contains("请先登录系统")
    }

    private fun jwErrLine(html: String): String {
        val m = Regex("""id=["']showMsg["'][^>]*>([\s\S]{0,160}?)</""", RegexOption.IGNORE_CASE).find(html)
        return m?.groupValues?.get(1)?.let { stripHtml(it) }.orEmpty()
    }

    private fun jwLoginToSession(user: String, pwd: String, jar: MutableMap<String, String>, trace: MutableList<String>) {
        trace += "① 打开教务系统登录页"
        jwHttpFollow("$JW_BASE/jsxsd/", "GET", jar)
        val enc = "${jwEncodeInp(user)}%%%${jwEncodeInp(pwd)}"
        val form = formEncode(mapOf("userAccount" to user, "userPassword" to "", "encoded" to enc))
        trace += "② 提交账号密码（encoded 编码）"
        val loginRes = jwHttpFollow("$JW_BASE/jsxsd/xk/LoginToXk", "POST", jar, form)
        if (loginRes.code != 200) throw IllegalStateException("登录接口返回 HTTP ${loginRes.code}")
        if (jwIsLoginPage(loginRes.body)) {
            val why = jwErrLine(loginRes.body)
            throw IllegalStateException(if (why.isNotEmpty()) "教务系统提示：$why" else "登录后又回到登录页（账号或密码错误）")
        }
        trace += "   ↳ 登录通过（${loginRes.body.length} 字节）"
        try {
            jwHttpFollow("$JW_BASE/jsxsd/framework/xsMain.jsp", "GET", jar)
        } catch (_: Throwable) {
        }
    }

    fun testJwLogin(user: String, pwd: String): List<String> {
        require(user.isNotBlank() && pwd.isNotBlank()) { "请填写教务系统账号和密码" }
        val jar = LinkedHashMap<String, String>()
        val trace = mutableListOf("开始测登录：${java.time.LocalTime.now().toString().take(8)}")
        jwLoginToSession(user.trim(), pwd, jar, trace)
        trace += "✓ 登录成功（仅验证账号，未覆写课表）"
        return trace
    }

    fun fetchJwTimetable(
        user: String,
        pwd: String,
        fallbackPeriods: List<PeriodSlot>,
        fallbackStartMonday: String,
        fallbackTotalWeeks: Int
    ): TimetableFetchResult {
        require(user.isNotBlank() && pwd.isNotBlank()) { "请填写教务系统账号和密码" }
        val u = user.trim()
        val jar = LinkedHashMap<String, String>()
        val trace = mutableListOf("开始更新课表：${java.time.LocalTime.now().toString().take(8)}")

        jwLoginToSession(u, pwd, jar, trace)

        trace += "③ 请求学期理论课表 /xskb/xskb_list.do"
        val kbRes = jwHttpFollow("$JW_BASE/jsxsd/xskb/xskb_list.do", "GET", jar)
        if (kbRes.code != 200 || jwIsLoginPage(kbRes.body)) {
            throw IllegalStateException("课表页拉取失败（HTTP ${kbRes.code}）")
        }
        trace += "   ↳ 课表页到手（${kbRes.body.length} 字节）"

        var weeksHtml = ""
        try {
            val wkRes = jwHttpFollow("$JW_BASE/jsxsd/jxzl/jxzl_query", "GET", jar)
            if (wkRes.code == 200) {
                weeksHtml = wkRes.body
                trace += "④ 教学周历页到手（${weeksHtml.length} 字节）"
            }
        } catch (e: Throwable) {
            trace += "   ! 周历拉取失败（将使用兜底开学日期）：${e.message}"
        }

        val (parsedStart, parsedWeeks, parsedSemLabel) = parseJwWeeksPage(weeksHtml)
        val maxCourseWeek = parseJwKbHtml(kbRes.body, fallbackPeriods, fallbackTotalWeeks.coerceAtLeast(19)).let { parsed ->
            val mw = parsed.flatMap { Regex("""\d+""").findAll(it.weeks).map { m -> m.value.toInt() } }.maxOrNull() ?: 0
            trace += "⑤ 解析出 ${parsed.size} 条课程"
            mw to parsed
        }
        val finalStart = parsedStart.ifEmpty { fallbackStartMonday.ifEmpty { "2026-08-31" } }
        val finalWeeks = when {
            maxCourseWeek.first > 0 -> maxCourseWeek.first.coerceIn(16, 28)
            parsedWeeks > 0 -> parsedWeeks.coerceIn(16, 28)
            fallbackTotalWeeks > 0 -> fallbackTotalWeeks
            else -> 19
        }
        if (parsedStart.isEmpty()) trace += "   ! 周历未识别出第 1 周，使用兜底「$finalStart」"
        else trace += "   ✓ 开学第 1 周周一：$finalStart · 共 $finalWeeks 周"

        val td = TimetableData(
            semester = "在线同步",
            semesterLabel = parsedSemLabel.ifEmpty { "教务系统在线课表" },
            startMonday = finalStart,
            totalWeeks = finalWeeks,
            fetched = LocalDate.now().toString(),
            source = "教务系统（jwgl.bupt.edu.cn）",
            periods = fallbackPeriods,
            courses = maxCourseWeek.second
        )
        return TimetableFetchResult(td, finalStart, finalWeeks, trace)
    }

    internal fun parseJwWeeksPage(html: String): Triple<String, Int, String> {
        if (html.isBlank()) return Triple("", 0, "")
        val plain = stripHtml(html)
        var semLabel = ""
        Regex("""(20\d{2})\s*[-－]\s*(20\d{2})\s*学年\s*第?\s*([一二12])\s*学期""").find(plain)?.let { m ->
            val t = when (m.groupValues[3]) {
                "一", "1" -> "1"
                else -> "2"
            }
            semLabel = "${m.groupValues[1]}-${m.groupValues[2]} 学年 第 $t 学期"
        }
        var startMonday = ""
        var totalWeeks = 0
        val tableHtml = Regex("""<table[^>]*id=["']kbtable["'][^>]*>([\s\S]*?)</table>""", RegexOption.IGNORE_CASE).find(html)?.groupValues?.get(1)
            ?: Regex("""<table[^>]*>([\s\S]*?\d{4}年\d{1,2}月\d{1,2}[\s\S]*?)</table>""", RegexOption.IGNORE_CASE).find(html)?.groupValues?.get(1)
            ?: ""
        if (tableHtml.isNotEmpty()) {
            val rowRe = Regex("""<tr[^>]*>([\s\S]*?)</tr>""", RegexOption.IGNORE_CASE)
            val tdRe = Regex("""<(?:td|th)([^>]*)>([\s\S]*?)</(?:td|th)>""", RegexOption.IGNORE_CASE)
            for (rm in rowRe.findAll(tableHtml)) {
                val cells = tdRe.findAll(rm.groupValues[1]).toList()
                if (cells.size < 2) continue
                val wn = stripHtml(cells[0].groupValues[2]).replace(Regex("""[^\d]"""), "")
                val weekNo = wn.toIntOrNull() ?: continue
                if (weekNo > totalWeeks) totalWeeks = weekNo
                if (weekNo >= 1 && startMonday.isEmpty()) {
                    for (c in 1 until cells.size) {
                        val attrs = cells[c].groupValues[1]
                        val dm = Regex("""(\d{4})年(\d{1,2})月(\d{1,2})""").find(attrs)
                            ?: Regex("""(\d{4})-(\d{1,2})-(\d{1,2})""").find(attrs)
                        if (dm != null) {
                            val dt = LocalDate.of(dm.groupValues[1].toInt(), dm.groupValues[2].toInt(), dm.groupValues[3].toInt())
                            val off = dt.dayOfWeek.value - DayOfWeek.MONDAY.value
                            val mon = dt.minusDays(off.toLong())
                            startMonday = mon.toString()
                            break
                        }
                    }
                }
            }
        }
        return Triple(startMonday, totalWeeks, semLabel)
    }

    internal fun jwNormWeeks(src: String): String {
        val odd = Regex("""[（(]\s*单\s*[)）]|单周""").containsMatchIn(src)
        val even = Regex("""[（(]\s*双\s*[)）]|双周""").containsMatchIn(src)
        val re1 = Regex("""(\d{1,2}(?:\s*[-－—~]\s*\d{1,2})?(?:\s*[,，、]\s*\d{1,2}(?:\s*[-－—~]\s*\d{1,2})?)*)\s*[（(]?\s*周\s*[)）]?""")
        val m = re1.find(src) ?: return ""
        val body = m.groupValues[1].replace('，', ',').replace('、', ',').replace('－', '-').replace('—', '-').replace('~', '-').replace(Regex("""\s+"""), "")
        return "$body(周)" + if (odd) "(单)" else if (even) "(双)" else ""
    }

    internal fun jwPeriodRange(text: String): Pair<Int, Int>? {
        val m = Regex("""\[\s*(\d{1,2}(?:\s*-\s*\d{1,2})*)\s*节\s*]""").find(text) ?: return null
        val nums = m.groupValues[1].split('-').mapNotNull { it.trim().toIntOrNull() }.filter { it > 0 }
        if (nums.isEmpty()) return null
        return nums.min() to nums.max()
    }

    internal fun parseJwKbHtml(
        html: String,
        periods: List<PeriodSlot>,
        defaultTotalWeeks: Int
    ): List<CourseScheduleItem> {
        val tableRe = Regex("""<table[^>]*>([\s\S]*?)</table>""", RegexOption.IGNORE_CASE)
        val kbTable = Regex("""<table[^>]*id=["']kbtable["'][^>]*>([\s\S]*?)</table>""", RegexOption.IGNORE_CASE).find(html)?.value
            ?: tableRe.findAll(html).firstOrNull { it.value.contains("星期一") }?.value
            ?: throw IllegalStateException("课表页中未找到课程表格（登录可能未生效）")

        val rowRe = Regex("""<tr[^>]*>([\s\S]*?)</tr>""", RegexOption.IGNORE_CASE)
        val cellRe = Regex("""<(?:td|th)([^>]*)>([\s\S]*?)</(?:td|th)>""", RegexOption.IGNORE_CASE)

        data class GridCell(val rawHtml: String, val rs: Int, val r0: Int)

        val rows = rowRe.findAll(kbTable).toList()
        val grid = ArrayList<MutableList<GridCell?>>()
        fun ensureCell(r: Int, c: Int) {
            while (grid.size <= r) grid.add(ArrayList())
            while (grid[r].size <= c) grid[r].add(null)
        }
        rows.forEachIndexed { ri, rm ->
            var c = 0
            for (cm in cellRe.findAll(rm.groupValues[1])) {
                ensureCell(ri, c)
                while (grid[ri][c] != null) { c++; ensureCell(ri, c) }
                val attrs = cm.groupValues[1]
                val rs = Regex("""rowspan\s*=\s*["']?(\d+)""", RegexOption.IGNORE_CASE).find(attrs)?.groupValues?.get(1)?.toIntOrNull() ?: 1
                val cs = Regex("""colspan\s*=\s*["']?(\d+)""", RegexOption.IGNORE_CASE).find(attrs)?.groupValues?.get(1)?.toIntOrNull() ?: 1
                for (dr in 0 until rs) {
                    for (dc in 0 until cs) {
                        ensureCell(ri + dr, c + dc)
                        grid[ri + dr][c + dc] = GridCell(cm.groupValues[2], rs, ri)
                    }
                }
                c += cs
            }
        }

        val wdByCol = LinkedHashMap<Int, Int>()
        val headerRows = HashSet<Int>()
        for (r in 0 until grid.size.coerceAtMost(4)) {
            grid[r].forEachIndexed { ci, cell ->
                if (cell != null) {
                    val txt = stripHtml(cell.rawHtml).replace(Regex("""\s+"""), "")
                    val m = Regex("""(?:星期|周)([一二三四五六日天])""").find(txt)
                    val wd = when (m?.groupValues?.get(1)) {
                        "一" -> 1; "二" -> 2; "三" -> 3; "四" -> 4; "五" -> 5; "六" -> 6; "日", "天" -> 7
                        else -> null
                    }
                    if (wd != null) {
                        wdByCol[ci] = wd
                        headerRows += cell.r0
                    }
                }
            }
            if (wdByCol.size >= 5) break
        }
        if (wdByCol.isEmpty()) throw IllegalStateException("未能识别课表星期列")

        val periodOfRow = LinkedHashMap<Int, Int>()
        var firstDataRow = -1
        for (ri in grid.indices) {
            if (ri in headerRows) continue
            val cell0 = grid[ri].firstOrNull() ?: continue
            val txt0 = stripHtml(cell0.rawHtml)
            var pn = Regex("""第\s*(\d{1,2})\s*节""").find(txt0)?.groupValues?.get(1)?.toIntOrNull()
                ?: Regex("""^\s*(\d{1,2})\s*$""").find(txt0)?.groupValues?.get(1)?.toIntOrNull()
                ?: Regex("""(\d{1,2})\s*[-－~]\s*\d{1,2}\s*节""").find(txt0)?.groupValues?.get(1)?.toIntOrNull()
                ?: 0
            if (pn == 0) {
                val tm = Regex("""(\d{1,2}):(\d{2})""").find(txt0)
                if (tm != null) {
                    val mins = tm.groupValues[1].toInt() * 60 + tm.groupValues[2].toInt()
                    for (p in periods) {
                        val sm = Regex("""(\d{1,2}):(\d{2})""").find(p.time.substringBefore('-'))
                        if (sm != null && sm.groupValues[1].toInt() * 60 + sm.groupValues[2].toInt() == mins) {
                            pn = p.no; break
                        }
                    }
                }
            }
            if (pn == 0) {
                val bn = Regex("""(\d{1,2})\s*大节""").find(txt0)?.groupValues?.get(1)?.toIntOrNull()
                if (bn != null) pn = bn * 2 - 1
            }
            if (pn > 0) {
                periodOfRow[ri] = pn
                if (firstDataRow < 0) firstDataRow = ri
            }
        }
        if (firstDataRow < 0) firstDataRow = 1

        val rawList = mutableListOf<CourseScheduleItem>()
        val divFullRe = Regex("""<div[^>]+class=["'][^"']*\bkbcontent\b[^"']*["'][^>]*>([\s\S]*?)</div>""", RegexOption.IGNORE_CASE)
        val divSimpleRe = Regex("""<div[^>]+class=["'][^"']*\bkbcontent1\b[^"']*["'][^>]*>([\s\S]*?)</div>""", RegexOption.IGNORE_CASE)
        val titleAttrRe = Regex("""title=["']([^"']+)["']""", RegexOption.IGNORE_CASE)
        val titleKeySkip = Regex("""^(教师|老师|任课|任课教师|主讲|周次|周次\(节次\)|教室|地点|上课地点|课程|课程名称|名称)$""")

        for (ri in firstDataRow until grid.size) {
            if (ri in headerRows) continue
            val startRowPeriod = periodOfRow[ri] ?: continue
            grid[ri].forEachIndexed { ci, g ->
                if (g == null || g.r0 != ri) return@forEachIndexed
                val wd = wdByCol[ci] ?: return@forEachIndexed

                val fullBlocks = divFullRe.findAll(g.rawHtml).filterNot { it.value.contains("sykb") }.map { it.groupValues[1] }.filter { stripHtml(it).isNotEmpty() }.toList()
                val blocks = fullBlocks.ifEmpty {
                    divSimpleRe.findAll(g.rawHtml).filterNot { it.value.contains("sykb") }.map { it.groupValues[1] }.filter { stripHtml(it).isNotEmpty() }.toList()
                }
                for (bl in blocks) {
                    for (subHtml in bl.split(Regex("""-{5,}"""))) {
                        val titleAttrs = titleAttrRe.findAll(subHtml).map { it.groupValues[1].trim() }.filter { it.isNotEmpty() && !titleKeySkip.matches(it) }.toList()
                        val textWithBreaks = stripHtml(
                            subHtml.replace(Regex("""<br\s*/?>""", RegexOption.IGNORE_CASE), "\n")
                                .replace(Regex("""</(?:p|div|li|tr)>""", RegexOption.IGNORE_CASE), "\n")
                        )
                        val lines = textWithBreaks.lines().map { it.trim() }.filter { it.isNotEmpty() }
                        if (lines.isEmpty()) continue

                        var name = ""
                        var teacher = ""
                        var weeks = ""
                        var room = ""
                        var range: Pair<Int, Int>? = null
                        val rest = mutableListOf<String>()

                        for (line in lines) {
                            if (range == null) range = jwPeriodRange(line)
                            val w = jwNormWeeks(line)
                            if (w.isNotEmpty() && weeks.isEmpty()) weeks = w
                            val tM = Regex("""^(?:教师|老师|任课教师|主讲|任课)\s*[:：]\s*(.+)$""").find(line)
                            if (tM != null && teacher.isEmpty()) teacher = tM.groupValues[1].trim()
                            val lineNoPeriod = line.replace(Regex("""\[\s*\d{1,2}(?:\s*-\s*\d{1,2})*\s*节\s*]"""), "").trim()
                            val rM = Regex("""^(?:教室|地点|上课地点|场地|教室地点)\s*[:：]\s*(.+)$""").find(lineNoPeriod)
                            val lookRoom = if (rM != null) rM.groupValues[1].trim()
                            else if (!lineNoPeriod.contains("周") && tM == null &&
                                (Regex("""(楼|室|教室|报告厅|体育馆|机房|实验中心|校区|馆|操场)""").containsMatchIn(lineNoPeriod) ||
                                    Regex("""^(?:教[一二三四五六]|科研|沙河)?[A-Za-z]?\d{3}|^教[一二三四五六][-－—]""").containsMatchIn(lineNoPeriod)) &&
                                Regex("""[-－—()（）A-Za-z0-9]""").containsMatchIn(lineNoPeriod)
                            ) lineNoPeriod else ""
                            if (lookRoom.isNotEmpty() && room.isEmpty()) room = lookRoom
                            if (w.isEmpty() && tM == null && lookRoom.isEmpty()) rest += line
                        }
                        if (room.isEmpty()) {
                            Regex("""<[^>]*title=["'](?:教室|地点|上课地点)["'][^>]*>([^<]+)</""", RegexOption.IGNORE_CASE)
                                .find(subHtml)?.groupValues?.get(1)?.trim()?.takeIf { it.isNotEmpty() }?.let { room = it }
                        }
                        for (tv in titleAttrs) {
                            if (range == null) range = jwPeriodRange(tv)
                            if (weeks.isEmpty()) weeks = jwNormWeeks(tv)
                            if (teacher.isEmpty()) {
                                Regex("""^(?:教师|老师|任课|主讲)\s*[:：]\s*(.+)$""").find(tv)?.let { teacher = it.groupValues[1].trim() }
                            }
                            if (room.isEmpty()) {
                                Regex("""^(?:教室|地点|上课地点|场地)\s*[:：]\s*(.+)$""").find(tv)?.let { room = it.groupValues[1].trim() }
                            }
                        }
                        val explicitName = rest.firstOrNull { Regex("""^(?:课程|课程名称|名称)\s*[:：]""").containsMatchIn(it) }
                        if (explicitName != null) {
                            name = explicitName.replace(Regex("""^(?:课程|课程名称|名称)\s*[:：]\s*"""), "").trim()
                            rest.remove(explicitName)
                        } else if (rest.isNotEmpty()) {
                            name = rest.removeAt(0)
                        }
                        if (teacher.isEmpty()) {
                            teacher = rest.lastOrNull {
                                !Regex("""(楼|室|厅|馆|周|学分|考试|考查|必修|选修)""").containsMatchIn(it) &&
                                    Regex("""^[\u4e00-\u9fa5·]{2,5}(?:[,，、/][\u4e00-\u9fa5·]{2,5})*$""").matches(it)
                            }.orEmpty()
                        }
                        name = name.replace(Regex("""[\s\u00a0]+[A-Za-z]$"""), "").replace(Regex("""[\s\-—]+$"""), "").trim()
                        if (name.length < 2 || Regex("""^(未安排|无课|空闲)$""").matches(name)) continue
                        val s2 = range?.first ?: startRowPeriod
                        val e2 = (range?.second ?: (startRowPeriod + (g.rs - 1))).coerceAtLeast(s2)
                        val finalWeeks = weeks.ifEmpty { "1-${defaultTotalWeeks}(周)" }
                        val isPractice = Regex("""实践|实验|上机|实训""").containsMatchIn(name) || subHtml.contains("实践")
                        rawList += CourseScheduleItem(
                            name = name,
                            teacher = teacher,
                            weekdays = listOf(wd),
                            start = s2,
                            end = e2,
                            weeks = finalWeeks,
                            room = room,
                            practice = isPractice
                        )
                    }
                }
            }
        }

        // 合并同课同日同周同室同教师的相连节次
        val merged = LinkedHashMap<String, CourseScheduleItem>()
        for (c in rawList) {
            val key = "${c.name}|${c.weekdays.joinToString(",")}|${c.weeks}|${c.room}|${c.teacher}"
            val old = merged[key]
            if (old == null) {
                merged[key] = c
            } else {
                merged[key] = old.copy(
                    start = minOf(old.start, c.start),
                    end = maxOf(old.end, c.end),
                    teacher = old.teacher.ifEmpty { c.teacher }
                )
            }
        }
        val res = merged.values.sortedWith(
            compareBy<CourseScheduleItem> { it.weekdays.firstOrNull() ?: 9 }
                .thenBy { it.start }
                .thenBy { it.name }
        )
        if (res.isEmpty()) throw IllegalStateException("课表解析出 0 门课（页面结构可能已调整）")
        return res
    }

    fun stripHtml(s: String): String {
        return s
            .replace(Regex("""<script[\s\S]*?</script>""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""<style[\s\S]*?</style>""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""<[^>]+>"""), " ")
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace(Regex("""[ \t\u00A0]+"""), " ")
            .replace(Regex("""\n{3,}"""), "\n\n")
            .trim()
    }
}
