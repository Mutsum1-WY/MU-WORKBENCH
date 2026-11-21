package com.mu.workbench

import com.mu.workbench.domain.AppUpdater
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 应用内更新解析与版本号比较单测（不联网）。 */
class AppUpdaterTest {

    @Test
    fun versionComparison_handlesPrefixesMultiDigitAndSegments() {
        assertTrue(AppUpdater.isNewer("v2.31", "2.30"))
        assertTrue(AppUpdater.isNewer("2.30.1", "2.30"))
        assertTrue(AppUpdater.isNewer("v2.30", "v2.9"))
        assertFalse(AppUpdater.isNewer("v2.30", "2.30"))
        assertFalse(AppUpdater.isNewer("v2.29", "2.30"))
        assertEquals(0, AppUpdater.compareVersions("v2.30-debug", "2.30.0"))
    }

    @Test
    fun parseReleaseJson_selectsPreferredApkAndExtractsSha256() {
        val sha = "ad73994ade940da4b8f8dd716ee991b132b0d856582940395fa0c5f11327148f"
        val json = """
            {
              "tag_name": "v2.31",
              "name": "v2.31 紧凑 UI 与应用内更新",
              "body": "### 更新内容\n- 收紧留白\n- 新增应用内更新\nSHA256: $sha",
              "published_at": "2026-11-21T12:00:00Z",
              "html_url": "https://github.com/Mutsum1-WY/MU-WORKBENCH/releases/tag/v2.31",
              "assets": [
                {
                  "name": "checksums.txt",
                  "size": 128,
                  "browser_download_url": "https://example.com/checksums.txt"
                },
                {
                  "name": "MU-WORKBENCH-debug.apk",
                  "size": 22000000,
                  "digest": "",
                  "browser_download_url": "https://example.com/MU-WORKBENCH-debug.apk"
                },
                {
                  "name": "MU-WORKBENCH-v2.31-debug.apk",
                  "size": 22618410,
                  "digest": "sha256:${sha.uppercase()}",
                  "browser_download_url": "https://example.com/MU-WORKBENCH-v2.31-debug.apk"
                }
              ]
            }
        """.trimIndent()

        val rel = AppUpdater.parseReleaseJson(json)
        assertEquals("v2.31", rel.tagName)
        assertEquals("2.31", rel.versionName)
        assertEquals("2026-11-21", rel.publishedDate)
        assertEquals("MU-WORKBENCH-v2.31-debug.apk", rel.apkName)
        assertEquals("https://example.com/MU-WORKBENCH-v2.31-debug.apk", rel.apkUrl)
        assertEquals(22618410L, rel.apkSize)
        assertEquals(sha, rel.sha256)
    }

    @Test
    fun extractSha256_andRedirectTagParsing_workCorrectly() {
        val hex = "61b6136569ae90ce25ee0ac96ce1053ac3e04a83fc4753e66a08c268e53c4092"
        assertEquals(hex, AppUpdater.extractSha256("", "校验 SHA-256: `$hex`"))
        assertEquals("", AppUpdater.extractSha256("", "无校验值"))

        assertEquals(
            "v2.31",
            AppUpdater.parseTagFromRedirectUrl("https://github.com/Mutsum1-WY/MU-WORKBENCH/releases/tag/v2.31?utm=1")
        )
        assertNull(AppUpdater.parseTagFromRedirectUrl("https://github.com/Mutsum1-WY/MU-WORKBENCH/releases/latest"))
    }
}
