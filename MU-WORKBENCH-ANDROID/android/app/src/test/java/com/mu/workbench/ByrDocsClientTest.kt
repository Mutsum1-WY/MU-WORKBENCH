package com.mu.workbench

import com.mu.workbench.domain.ByrDocsClient
import org.junit.Assert.assertEquals
import org.junit.Test

/** 找资料：关键词规整与「搜不到时退一步」的规则（不联网） */
class ByrDocsClientTest {

    @Test
    fun normTurnsHalfWidthParensIntoFullWidth() {
        assertEquals("数学分析（上）", ByrDocsClient.norm("数学分析(上)"))
        assertEquals("数据结构 期末", ByrDocsClient.norm("  数据结构   期末 "))
    }

    @Test
    fun relaxDropsSubtitleQualifierAndTrailingNumber() {
        assertEquals("人机交互", ByrDocsClient.relax("人机交互：用户体验设计"))
        assertEquals("国家安全教育", ByrDocsClient.relax("国家安全教育（下）"))
        assertEquals("数学分析", ByrDocsClient.relax("数学分析(上)"))
        assertEquals("大学生心理健康", ByrDocsClient.relax("大学生心理健康1"))
        assertEquals("软件工程", ByrDocsClient.relax("软件工程（双语）"))
        assertEquals("线性代数", ByrDocsClient.relax("线性代数"))
    }

    @Test
    fun sizeTextIsHumanReadable() {
        assertEquals("534 KB", ByrDocsClient.sizeText(547_000))
        assertEquals("6.7 MB", ByrDocsClient.sizeText(7_058_882))
        assertEquals("", ByrDocsClient.sizeText(0))
    }
}
