package com.mu.workbench.domain

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.Locale
import java.util.concurrent.CancellationException

/**
 * GitHub Releases 应用内检查更新与 APK 下载校验客户端。
 */
object AppUpdater {

    const val REPO_OWNER = "Mutsum1-WY"
    const val REPO_NAME = "MU-WORKBENCH"
    const val API_LATEST_URL = "https://api.github.com/repos/$REPO_OWNER/$REPO_NAME/releases/latest"
    const val RELEASES_PAGE_URL = "https://github.com/$REPO_OWNER/$REPO_NAME/releases/latest"

    private const val UA = "MU-WORKBENCH-Android"

    data class ReleaseInfo(
        val tagName: String,
        val versionName: String,
        val title: String,
        val notes: String,
        val publishedDate: String,
        val htmlUrl: String,
        val apkName: String,
        val apkUrl: String,
        val apkSize: Long,
        val sha256: String
    )

    fun normalizeVersion(raw: String): String =
        raw.trim()
            .removePrefix("v")
            .removePrefix("V")
            .split('-', '+')
            .firstOrNull()
            .orEmpty()
            .trim()

    fun compareVersions(remote: String, local: String): Int {
        val rParts = normalizeVersion(remote).split('.').filter { it.isNotEmpty() }
        val lParts = normalizeVersion(local).split('.').filter { it.isNotEmpty() }
        val len = maxOf(rParts.size, lParts.size)
        for (i in 0 until len) {
            val rv = rParts.getOrNull(i)?.toIntOrNull() ?: 0
            val lv = lParts.getOrNull(i)?.toIntOrNull() ?: 0
            if (rv != lv) return rv.compareTo(lv)
        }
        return 0
    }

    fun isNewer(remote: String, local: String): Boolean = compareVersions(remote, local) > 0

    fun extractSha256(assetDigest: String, releaseBody: String): String {
        val digestMatch = Regex("""sha256:([0-9a-fA-F]{64})""").find(assetDigest)
        if (digestMatch != null) return digestMatch.groupValues[1].lowercase(Locale.ROOT)
        val bodyMatch = Regex("""(?i)SHA-?256[:：\s`]*([0-9a-fA-F]{64})""").find(releaseBody)
        return bodyMatch?.groupValues?.getOrNull(1)?.lowercase(Locale.ROOT).orEmpty()
    }

    fun parseTagFromRedirectUrl(location: String): String? {
        val m = Regex("""/releases/tag/([^/?#]+)""").find(location)
        return m?.groupValues?.getOrNull(1)?.trim()?.takeIf { it.isNotEmpty() }
    }

    fun formatBytes(bytes: Long): String {
        if (bytes <= 0L) return ""
        return when {
            bytes >= 1024L * 1024L -> String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0))
            bytes >= 1024L -> String.format(Locale.US, "%.0f KB", bytes / 1024.0)
            else -> "$bytes B"
        }
    }

    fun parseReleaseJson(jsonText: String): ReleaseInfo {
        val obj = JSONObject(jsonText)
        val tagName = obj.optString("tag_name", "").trim()
        val versionName = normalizeVersion(tagName)
        val title = obj.optString("name", "").trim().ifEmpty { tagName }
        val notes = obj.optString("body", "").trim()
        val publishedDate = obj.optString("published_at", "").take(10)
        val htmlUrl = obj.optString("html_url", "").trim().ifEmpty { RELEASES_PAGE_URL }

        val assets = obj.optJSONArray("assets") ?: JSONArray()
        val apkAssets = mutableListOf<JSONObject>()
        for (i in 0 until assets.length()) {
            val a = assets.optJSONObject(i) ?: continue
            val name = a.optString("name", "")
            val url = a.optString("browser_download_url", "")
            if (name.endsWith(".apk", ignoreCase = true) && url.isNotEmpty()) {
                apkAssets += a
            }
        }

        val preferred = apkAssets.firstOrNull {
            val n = it.optString("name", "")
            n.startsWith("MU-WORKBENCH-v", ignoreCase = true) ||
                (versionName.isNotEmpty() && n.contains(versionName, ignoreCase = true))
        } ?: apkAssets.firstOrNull()

        val apkName = preferred?.optString("name", "").orEmpty()
        val apkUrl = preferred?.optString("browser_download_url", "").orEmpty()
        val apkSize = preferred?.optLong("size", 0L) ?: 0L
        val digest = preferred?.optString("digest", "").orEmpty()
        val sha256 = extractSha256(digest, notes)

        return ReleaseInfo(
            tagName = tagName,
            versionName = versionName,
            title = title,
            notes = notes,
            publishedDate = publishedDate,
            htmlUrl = htmlUrl,
            apkName = apkName,
            apkUrl = apkUrl,
            apkSize = apkSize,
            sha256 = sha256
        )
    }

    /** 检查 GitHub 最新 Release；当 API 遭遇限流时退回 /releases/latest 302 跳转解析。 */
    fun fetchLatestRelease(): ReleaseInfo {
        val conn = (URL(API_LATEST_URL).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 12000
            readTimeout = 15000
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", UA)
        }
        try {
            val code = conn.responseCode
            if (code == 200) {
                val text = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                return parseReleaseJson(text)
            }
            if (code == 403 || code == 429) {
                return fetchLatestViaRedirect(code)
            }
            throw IllegalStateException("HTTP $code")
        } finally {
            conn.disconnect()
        }
    }

    private fun fetchLatestViaRedirect(apiCode: Int): ReleaseInfo {
        val conn = (URL(RELEASES_PAGE_URL).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            instanceFollowRedirects = false
            connectTimeout = 12000
            readTimeout = 15000
            setRequestProperty("User-Agent", UA)
        }
        try {
            val code = conn.responseCode
            if (code in listOf(301, 302, 303, 307, 308)) {
                val loc = conn.getHeaderField("Location").orEmpty()
                val resolved = if (loc.isNotEmpty()) URL(URL(RELEASES_PAGE_URL), loc).toString() else ""
                val tag = parseTagFromRedirectUrl(resolved)
                if (!tag.isNullOrEmpty()) {
                    val ver = normalizeVersion(tag)
                    val apkName = "MU-WORKBENCH-$tag-debug.apk"
                    val apkUrl = "https://github.com/$REPO_OWNER/$REPO_NAME/releases/download/$tag/$apkName"
                    return ReleaseInfo(
                        tagName = tag,
                        versionName = ver,
                        title = tag,
                        notes = "检测到新版本 $tag（GitHub API 限流，已通过发布页获取版本信息）。",
                        publishedDate = "",
                        htmlUrl = resolved.ifEmpty { RELEASES_PAGE_URL },
                        apkName = apkName,
                        apkUrl = apkUrl,
                        apkSize = 0L,
                        sha256 = ""
                    )
                }
            }
            throw IllegalStateException("GitHub API 限流（HTTP $apiCode）且无法解析最新版本页")
        } finally {
            conn.disconnect()
        }
    }

    /** 流式下载 APK 并校验魔数与 SHA-256（若提供），支持取消。 */
    fun downloadApk(
        url: String,
        targetFile: File,
        expectedSize: Long = 0L,
        expectedSha256: String = "",
        isCancelled: () -> Boolean = { false },
        onProgress: (downloaded: Long, total: Long, pct: Int) -> Unit
    ): File {
        val dir = targetFile.parentFile ?: throw IllegalArgumentException("无效下载目录")
        if (!dir.exists() && !dir.mkdirs()) {
            throw IllegalStateException("无法创建更新缓存目录")
        }
        dir.listFiles()?.forEach { old ->
            if (old.name != targetFile.name && (old.name.endsWith(".apk", true) || old.name.endsWith(".part", true))) {
                old.delete()
            }
        }

        val part = File(dir, "${targetFile.name}.part")
        if (part.exists()) part.delete()

        var conn: HttpURLConnection? = null
        var curUrl = url
        var completed = false
        try {
            for (hop in 0 until 8) {
                if (isCancelled()) throw CancellationException("已取消下载")
                val u = URL(curUrl)
                val nextConn = (u.openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    instanceFollowRedirects = false
                    connectTimeout = 20000
                    readTimeout = 45000
                    setRequestProperty("User-Agent", UA)
                    setRequestProperty("Accept", "*/*")
                    setRequestProperty("Accept-Encoding", "identity")
                }
                val code = nextConn.responseCode
                if (code == 301 || code == 302 || code == 303 || code == 307 || code == 308) {
                    val loc = nextConn.getHeaderField("Location")
                    nextConn.disconnect()
                    if (loc.isNullOrBlank()) throw IllegalStateException("下载跳转地址为空")
                    curUrl = URL(u, loc).toString()
                    continue
                }
                if (code != 200) {
                    nextConn.disconnect()
                    throw IllegalStateException("下载失败（HTTP $code）")
                }
                val ctype = nextConn.contentType.orEmpty()
                if (ctype.startsWith("text/html", ignoreCase = true)) {
                    nextConn.disconnect()
                    throw IllegalStateException("下载链接返回了网页而非 APK 文件")
                }
                conn = nextConn
                break
            }

            val activeConn = conn ?: throw IllegalStateException("下载跳转次数过多")
            val contentLen = activeConn.contentLengthLong
            val totalBytes = if (contentLen > 0L) contentLen else expectedSize.coerceAtLeast(0L)
            val md = MessageDigest.getInstance("SHA-256")
            val buf = ByteArray(16 * 1024)
            var downloaded = 0L
            val magic = ByteArray(4)
            var magicRead = 0
            var lastNotifyMs = 0L
            var lastPct = -1

            onProgress(0L, totalBytes, 0)

            activeConn.inputStream.use { input ->
                FileOutputStream(part).use { out ->
                    while (true) {
                        if (isCancelled()) throw CancellationException("已取消下载")
                        val n = input.read(buf)
                        if (n <= 0) break
                        if (magicRead < 4) {
                            val copyCount = minOf(n, 4 - magicRead)
                            System.arraycopy(buf, 0, magic, magicRead, copyCount)
                            magicRead += copyCount
                        }
                        out.write(buf, 0, n)
                        md.update(buf, 0, n)
                        downloaded += n

                        val pct = if (totalBytes > 0L) {
                            ((downloaded * 100L) / totalBytes).toInt().coerceIn(0, 100)
                        } else 0
                        val now = System.currentTimeMillis()
                        if (pct != lastPct || now - lastNotifyMs >= 120L) {
                            lastPct = pct
                            lastNotifyMs = now
                            onProgress(downloaded, totalBytes, pct)
                        }
                    }
                    out.flush()
                }
            }

            val isZipMagic = magicRead == 4 &&
                magic[0] == 0x50.toByte() &&
                magic[1] == 0x4B.toByte() &&
                magic[2] == 0x03.toByte() &&
                magic[3] == 0x04.toByte()
            if (downloaded <= 1024L || !isZipMagic) {
                throw IllegalStateException("下载的不是有效的 APK 安装包")
            }

            if (expectedSha256.isNotEmpty()) {
                val actualSha = md.digest().joinToString("") { "%02x".format(it) }
                if (!actualSha.equals(expectedSha256, ignoreCase = true)) {
                    throw IllegalStateException("安装包校验失败（SHA-256 不一致）")
                }
            }

            if (targetFile.exists() && !targetFile.delete()) {
                throw IllegalStateException("无法替换旧的安装包缓存")
            }
            if (!part.renameTo(targetFile)) {
                part.copyTo(targetFile, overwrite = true)
                part.delete()
            }
            completed = true
            onProgress(downloaded, totalBytes.coerceAtLeast(downloaded), 100)
            return targetFile
        } finally {
            conn?.disconnect()
            if (!completed && part.exists()) {
                part.delete()
            }
        }
    }
}
