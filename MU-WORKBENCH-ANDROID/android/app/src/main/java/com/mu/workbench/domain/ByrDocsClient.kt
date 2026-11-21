package com.mu.workbench.domain

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * BYR Docs（byrdocs.org）资料检索。
 *
 * 用的是 BYR Docs 官方公开的搜索接口 https://search.byrdocs.org/api/search
 * （POST JSON，不需要登录，跨域开放），不是去爬它的网页。
 * 搜索结果只在内存里；下载链接 byrdocs.org/files/… 需要校园网或北邮统一认证登录，
 * 交给系统浏览器处理。
 */
object ByrDocsClient {

    private const val API = "https://search.byrdocs.org/api/search"

    enum class Type(val key: String, val label: String) {
        ALL("all", "全部"),
        TEST("test", "试题"),
        BOOK("book", "教材"),
        DOC("doc", "复习资料")
    }

    data class Item(
        val id: String,
        val type: String,          // test / book / doc
        val title: String,
        val meta: List<String>,
        val fileType: String,      // PDF / ZIP；在线题目为空
        val sizeText: String,
        val url: String,           // 下载链接；在线题目时是 wiki 页面
        val wikiUrl: String,       // 同时有 PDF 和 wiki 转录时的 wiki 链接
        val isWiki: Boolean
    )

    data class Result(val total: Int, val items: List<Item>, val used: String)

    /** 课表里是半角括号「数学分析(上)」，BYR Docs 用全角「数学分析（上）」，半角会搜不到 */
    fun norm(q: String): String =
        q.trim().replace('(', '（').replace(')', '）').replace(Regex("\\s+"), " ")

    /**
     * 搜不到时退一步：去掉冒号后的副标题、括号里的限定、末尾编号。
     * 「人机交互：用户体验设计」→「人机交互」，「大学生心理健康1」→「大学生心理健康」
     */
    fun relax(q: String): String {
        var s = norm(q).split('：', ':').first()
        s = s.replace(Regex("（[^）]*）"), " ")
            .replace(Regex("[0-9０-９ⅠⅡⅢⅣIVX]+$"), "")
            .replace(Regex("\\s+"), " ")
            .trim()
        return s
    }

    /** 先按原词搜，0 条再用放宽后的词搜一次。阻塞调用，要在 IO 线程里跑。 */
    fun search(q: String, type: Type, limit: Int): Result {
        val exact = norm(q)
        val r = request(exact, type, limit)
        val loose = relax(q)
        if (r.total > 0 || loose.isEmpty() || loose == exact) return r.copy(used = exact)
        val r2 = request(loose, type, limit)
        return if (r2.total > 0) r2.copy(used = loose) else r.copy(used = exact)
    }

    private fun request(keyword: String, type: Type, limit: Int): Result {
        val body = JSONObject()
            .put("keyword", keyword)
            .put("type", type.key)
            .put("limit", limit.coerceIn(1, 100))
            .toString()
            .toByteArray(Charsets.UTF_8)
        val conn = (URL(API).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 12000
            readTimeout = 15000
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("Accept", "application/json")
        }
        try {
            conn.outputStream.use { it.write(body) }
            val code = conn.responseCode
            if (code !in 200..299) throw IllegalStateException("HTTP $code")
            val text = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            val o = JSONObject(text)
            val arr = o.optJSONArray("results") ?: JSONArray()
            val items = (0 until arr.length()).mapNotNull { i -> arr.optJSONObject(i)?.let(::parseItem) }
            return Result(o.optInt("total", items.size), items, keyword)
        } finally {
            conn.disconnect()
        }
    }

    private fun strList(a: JSONArray?): List<String> =
        if (a == null) emptyList() else (0 until a.length()).mapNotNull { a.optString(it).takeIf { s -> s.isNotBlank() } }

    /** test 的 course 是对象，doc 的 course 是数组，统一取课名 */
    private fun courseNames(d: JSONObject): List<String> {
        d.optJSONArray("course")?.let { a ->
            return (0 until a.length()).mapNotNull { a.optJSONObject(it)?.optString("name")?.takeIf { s -> s.isNotBlank() } }
        }
        return listOfNotNull(d.optJSONObject("course")?.optString("name")?.takeIf { it.isNotBlank() })
    }

    fun sizeText(n: Long): String = when {
        n >= 104_857_600L -> "${n / 1_048_576} MB"
        n >= 1_048_576L -> "%.1f MB".format(n / 1_048_576.0)
        n >= 1024L -> "${n / 1024} KB"
        n > 0 -> "$n B"
        else -> ""
    }

    private fun parseItem(o: JSONObject): Item {
        val type = o.optString("type")
        val d = o.optJSONObject("data") ?: JSONObject()
        val ft = d.optString("filetype")
        val isWiki = ft.equals("wiki", true)
        val courses = courseNames(d)

        // 标题：书名里已经写了「第 N 版」的不再重复补
        val rawTitle = d.optString("title")
        val edition = d.optString("edition")
        val title = when {
            rawTitle.isNotBlank() ->
                rawTitle + if (type == "book" && edition.isNotBlank() && edition != "1" && !rawTitle.contains("版")) "（第 $edition 版）" else ""
            else -> {
                val t = d.optJSONObject("time")
                val years = t?.let {
                    val s = it.optString("start"); val e = it.optString("end")
                    if (s.isNotBlank() && e.isNotBlank() && s != e) "$s-$e" else s
                }.orEmpty()
                listOf(years, courses.joinToString(" / "), t?.optString("stage").orEmpty())
                    .filter { it.isNotBlank() && it != "null" }
                    .joinToString(" ")
                    .ifBlank { "未命名文件" }
            }
        }

        val meta = mutableListOf<String>()
        if (type == "book") {
            val authors = strList(d.optJSONArray("authors"))
            if (authors.isNotEmpty()) meta += authors.take(3).joinToString("、") + if (authors.size > 3) " 等" else ""
            d.optString("publisher").takeIf { it.isNotBlank() }?.let { meta += it }
            d.optString("publish_year").takeIf { it.isNotBlank() }?.let { meta += "$it 年" }
        } else {
            // 试题标题里已经有课名，不再重复
            if (courses.isNotEmpty() && !(type == "test" && rawTitle.contains(courses.first()))) meta += courses.joinToString(" / ")
            strList(d.optJSONArray("content")).takeIf { it.isNotEmpty() }?.let { meta += it.joinToString(" + ") }
            strList(d.optJSONArray("college")).takeIf { it.isNotEmpty() }?.let { meta += it.joinToString("、") }
        }

        return Item(
            id = o.optString("id"),
            type = type,
            title = title,
            meta = meta,
            fileType = if (isWiki) "" else ft.uppercase(),
            sizeText = if (isWiki) "在线题目" else listOf(ft.uppercase(), sizeText(d.optLong("filesize"))).filter { it.isNotBlank() }.joinToString(" · "),
            url = o.optString("url"),
            wikiUrl = if (isWiki) "" else d.optJSONObject("wiki")?.optString("url").orEmpty(),
            isWiki = isWiki
        )
    }
}
