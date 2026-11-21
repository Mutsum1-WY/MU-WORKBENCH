package com.mu.workbench.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mu.workbench.data.ThemeStyle
import com.mu.workbench.domain.ByrDocsClient
import com.mu.workbench.ui.WorkbenchViewModel
import com.mu.workbench.ui.components.MuCard
import com.mu.workbench.ui.components.MuChip
import com.mu.workbench.ui.theme.LocalMuColors
import com.mu.workbench.ui.theme.MuColors

/* ==================== 2. 找资料（BYR Docs 检索） ==================== */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ByrDocsPane(vm: WorkbenchViewModel) {
    val mu = LocalMuColors.current
    val ui by vm.byrDocs.collectAsState()
    val recent by vm.byrDocsRecent.collectAsState()
    val counts by vm.byrDocsCounts.collectAsState()
    val counting by vm.byrDocsCounting.collectAsState()
    val focus = LocalFocusManager.current
    val courses = remember { vm.termCourseNames() }

    val stats by vm.byrDocsStats.collectAsState()

    // 进页面顺手数一下本学期每门课有几条资料、BYR Docs 一共收录多少（都有 3 天缓存，大多数时候不发请求）
    LaunchedEffect(Unit) {
        vm.countByrDocsCourses()
        vm.loadByrDocsStats()
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(14.dp, 10.dp, 14.dp, 96.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item(key = "bd_intro") {
            BdIntroCard(stats) { vm.openExternalUrl(it) }
        }

        item(key = "bd_search") {
            OutlinedTextField(
                value = ui.query,
                onValueChange = { vm.byrDocsQuery(it) },
                placeholder = { Text("搜课名、书名、作者或 ISBN") },
                leadingIcon = { Icon(Icons.Rounded.Search, null) },
                trailingIcon = {
                    if (ui.query.isNotEmpty()) IconButton(onClick = { vm.byrDocsQuery("", true) }) {
                        Icon(Icons.Rounded.Close, contentDescription = "清空")
                    }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = {
                    vm.byrDocsQuery(ui.query, immediate = true)
                    focus.clearFocus()
                }),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(6.dp))
            Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ByrDocsClient.Type.entries.forEach { t ->
                    FilterChip(selected = ui.type == t, onClick = { vm.byrDocsType(t) }, label = { Text(t.label) })
                }
            }
        }

        val q = ui.query.trim()
        val res = ui.result

        when {
            /* ---- 没输入：本学期的课 + 最近搜过 ---- */
            q.isEmpty() -> {
                if (courses.isNotEmpty()) item(key = "bd_courses") {
                    // 有资料的排前面（按条数从多到少），没数过的居中，确定没有的放最后并淡化
                    val sorted = remember(courses, counts) {
                        courses.sortedByDescending { n -> counts[n]?.let { if (it > 0) 2_000_000 + it else 0 } ?: 1_000_000 }
                    }
                    BdSectionTitle(if (counting) "本学期的课 · 正在统计资料数…" else "本学期的课")
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        sorted.forEach { n ->
                            val c = counts[n]
                            BdQuickChip(
                                text = n,
                                count = c,
                                dim = c == 0,
                                onClick = { vm.byrDocsQuery(n, immediate = true); focus.clearFocus() }
                            )
                        }
                    }
                }
                if (recent.isNotEmpty()) item(key = "bd_recent") {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        BdSectionTitle("最近搜过", Modifier.weight(1f))
                        TextButton(onClick = { vm.clearByrDocsRecent() }) { Text("清空", fontSize = 12.sp, color = mu.ink3) }
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        recent.forEach { n ->
                            BdQuickChip(text = n, count = null, dim = false, onClick = { vm.byrDocsQuery(n, immediate = true); focus.clearFocus() })
                        }
                    }
                }
                if (courses.isEmpty() && recent.isEmpty()) item(key = "bd_hint") {
                    BdEmpty("输入课名、书名、作者或 ISBN 开始搜索")
                }
            }

            /* ---- 第一次搜，还没有结果：骨架占位 ---- */
            ui.loading && res == null -> {
                item(key = "bd_status") { BdStatus("正在搜索…") }
                items(3, key = { "bd_skel_$it" }) {
                    MuCard(padding = 12.dp, showCornerAccent = false, backgroundColor = mu.surface2) {
                        Spacer(Modifier.fillMaxWidth().height(58.dp))
                    }
                }
            }

            /* ---- 出错 ---- */
            ui.error.isNotEmpty() -> item(key = "bd_err") {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text(ui.error, color = mu.danger, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    OutlinedButton(onClick = { vm.byrDocsRetry() }, shape = RoundedCornerShape(mu.cardRadius)) { Text("重试") }
                }
            }

            res != null && res.items.isEmpty() -> item(key = "bd_none") {
                BdEmpty("没有找到「$q」" + (if (ui.type != ByrDocsClient.Type.ALL) "的${ui.type.label}" else "") + "，换个说法试试（比如只写课名的关键部分）")
            }

            res != null -> {
                item(key = "bd_status") {
                    val relaxed = res.used != ByrDocsClient.norm(q)
                    BdStatus(buildAnnotatedString {
                        if (ui.loading) append("正在更新… ")
                        if (relaxed) {
                            append("没有「$q」的资料，下面是「")
                            withStyle(SpanStyle(fontWeight = FontWeight.Black, color = mu.ink2)) { append(res.used) }
                            append("」的 ${res.total} 条")
                        } else append("找到 ${res.total} 条")
                        if (res.total > res.items.size) append("，显示前 ${res.items.size} 条")
                    })
                }
                items(res.items, key = { "bd_${it.type}_${it.id}" }) { item ->
                    BdItemCard(item, res.used) { url -> vm.openExternalUrl(url) }
                }
                if (res.total > res.items.size) item(key = "bd_more") {
                    if (ui.limit < 100) {
                        OutlinedButton(
                            onClick = { vm.byrDocsMore() },
                            shape = RoundedCornerShape(mu.cardRadius),
                            modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp)
                        ) { Text("显示更多", fontWeight = FontWeight.Bold) }
                    } else {
                        BdEmpty("最多显示 100 条，加个关键词缩小范围（比如「期末」「答案」）")
                    }
                }
            }
        }

        item(key = "bd_foot") {
            Text(
                "资料来自 BYR Docs（byrdocs.org），搜索不需要登录。下载需要连校园网，或在浏览器里用北邮统一认证登录 BYR Docs。",
                fontSize = 11.sp, lineHeight = 16.sp, color = mu.ink3,
                modifier = Modifier.padding(top = 6.dp)
            )
        }
    }
}

/** 页顶介绍卡：BYR Docs 是什么 + 收录数 + Star / 投稿 / 关于 三个入口（给 BYR Docs 引流） */
@Composable
private fun BdIntroCard(stats: ByrDocsClient.Stats?, open: (String) -> Unit) {
    val mu = LocalMuColors.current
    MuCard(padding = 14.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("BYR Docs", fontSize = 17.sp, fontWeight = FontWeight.Black, color = mu.ink)
            Spacer(Modifier.width(8.dp))
            MuChip("北邮资料站", mu.accent, Color.White)
        }
        Spacer(Modifier.height(6.dp))
        Text(
            "北邮同学自己维护的开源资料库：电子教材、往年试题、复习笔记，免费、不登录就能搜。",
            fontSize = 12.5.sp, lineHeight = 18.sp, color = mu.ink2
        )
        if (stats != null && stats.total > 0) {
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                BdStat("已收录", stats.total, mu.accentInk)
                BdStat("试题", stats.test, mu.ink)
                BdStat("教材", stats.book, mu.ink)
                BdStat("资料", stats.doc, mu.ink)
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "觉得有用就去 GitHub 点个 Star；手里有试题和笔记，也欢迎投稿，让下一届少走弯路。",
            fontSize = 11.5.sp, lineHeight = 16.sp, color = mu.ink3
        )
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            Button(
                onClick = { open(ByrDocsClient.URL_STAR) },
                shape = RoundedCornerShape(mu.cardRadius),
                contentPadding = PaddingValues(horizontal = 8.dp),
                modifier = Modifier.weight(1.25f).heightIn(min = 44.dp)
            ) {
                Icon(Icons.Rounded.Star, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("点个 Star", fontSize = 12.5.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            }
            OutlinedButton(
                onClick = { open(ByrDocsClient.URL_PUBLISH) },
                shape = RoundedCornerShape(mu.cardRadius),
                contentPadding = PaddingValues(horizontal = 8.dp),
                modifier = Modifier.weight(1f).heightIn(min = 44.dp)
            ) { Text("投稿资料", fontSize = 12.5.sp, fontWeight = FontWeight.Bold, maxLines = 1) }
            OutlinedButton(
                onClick = { open(ByrDocsClient.URL_ABOUT) },
                shape = RoundedCornerShape(mu.cardRadius),
                contentPadding = PaddingValues(horizontal = 8.dp),
                modifier = Modifier.weight(1f).heightIn(min = 44.dp)
            ) { Text("了解更多", fontSize = 12.5.sp, fontWeight = FontWeight.Bold, maxLines = 1) }
        }
    }
}

@Composable
private fun BdStat(label: String, n: Int, color: Color) {
    val mu = LocalMuColors.current
    Column {
        Text("$n", fontSize = 16.sp, fontWeight = FontWeight.Black, color = color)
        Text(label, fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = mu.ink3)
    }
}

@Composable
private fun BdSectionTitle(text: String, modifier: Modifier = Modifier) {
    val mu = LocalMuColors.current
    Text(text, fontSize = 12.sp, fontWeight = FontWeight.Black, color = mu.ink2, modifier = modifier.padding(vertical = 6.dp))
}

@Composable
private fun BdStatus(text: String) = BdStatus(AnnotatedString(text))

@Composable
private fun BdStatus(text: AnnotatedString) {
    val mu = LocalMuColors.current
    Text(text, fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = mu.ink3, modifier = Modifier.padding(horizontal = 2.dp))
}

@Composable
private fun BdEmpty(text: String) {
    val mu = LocalMuColors.current
    MuCard(padding = 16.dp, showCornerAccent = false) {
        Text(text, fontSize = 12.5.sp, color = mu.ink2, fontWeight = FontWeight.Bold, lineHeight = 18.sp, modifier = Modifier.fillMaxWidth())
    }
}

private fun chipShape(mu: MuColors) = RoundedCornerShape(
    when (mu.style) {
        ThemeStyle.IOS -> 999.dp
        ThemeStyle.P3R -> 5.dp
        ThemeStyle.P5R -> 0.dp
    }
)

/** 快捷课名：带资料条数；确定没有资料的淡化 */
@Composable
private fun BdQuickChip(text: String, count: Int?, dim: Boolean, onClick: () -> Unit) {
    val mu = LocalMuColors.current
    val shape = chipShape(mu)
    Surface(
        color = mu.surface1,
        shape = shape,
        border = androidx.compose.foundation.BorderStroke(if (mu.style == ThemeStyle.P5R) 2.dp else 1.dp, if (mu.style == ThemeStyle.P5R) mu.ink else mu.line2),
        modifier = Modifier
            .alpha(if (dim) 0.45f else 1f)
            .clip(shape)
            .clickable(onClick = onClick)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.heightIn(min = 36.dp).padding(horizontal = 11.dp, vertical = 6.dp)
        ) {
            Text(text, fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = mu.ink)
            if (count != null && count > 0) {
                Spacer(Modifier.width(6.dp))
                Text("$count", fontSize = 11.sp, fontWeight = FontWeight.Black, color = mu.accentInk)
            }
        }
    }
}

/** 标题里把命中的关键词加粗上色 */
private fun highlight(text: String, q: String, color: Color): AnnotatedString = buildAnnotatedString {
    append(text)
    q.split(' ').filter { it.isNotBlank() }.forEach { w ->
        var from = 0
        while (true) {
            val i = text.indexOf(w, from, ignoreCase = true)
            if (i < 0) break
            addStyle(SpanStyle(color = color, fontWeight = FontWeight.Black), i, i + w.length)
            from = i + w.length
        }
    }
}

@Composable
private fun BdItemCard(item: ByrDocsClient.Item, used: String, open: (String) -> Unit) {
    val mu = LocalMuColors.current
    val (tag, tagColor) = when (item.type) {
        "test" -> "试题" to mu.accent
        "book" -> "教材" to mu.ok
        else -> "资料" to mu.accent3
    }
    MuCard(padding = 12.dp, showCornerAccent = false, onClick = { open(item.url) }) {
        Row(verticalAlignment = Alignment.Top) {
            MuChip(tag, tagColor, Color.White)
            Spacer(Modifier.width(8.dp))
            Text(
                highlight(item.title, used, mu.accentInk),
                fontSize = 14.sp, fontWeight = FontWeight.Bold, color = mu.ink, lineHeight = 19.sp,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(8.dp))
            Text(item.sizeText, fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = mu.ink3, modifier = Modifier.padding(top = 2.dp))
        }
        // 信息行左、按钮右：一屏能多看几条
        Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) {
            Text(
                item.meta.joinToString(" · "),
                fontSize = 11.5.sp, color = mu.ink3, lineHeight = 16.sp,
                modifier = Modifier.weight(1f).padding(bottom = 4.dp)
            )
            Spacer(Modifier.width(8.dp))
            if (item.wikiUrl.isNotEmpty()) {
                OutlinedButton(
                    onClick = { open(item.wikiUrl) },
                    shape = RoundedCornerShape(mu.cardRadius),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    modifier = Modifier.height(36.dp)
                ) { Text("在线看题", fontSize = 12.sp) }
                Spacer(Modifier.width(6.dp))
            }
            Button(
                onClick = { open(item.url) },
                shape = RoundedCornerShape(mu.cardRadius),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                modifier = Modifier.height(36.dp)
            ) {
                Text(if (item.isWiki) "在线看题" else "下载" + if (item.fileType.isNotEmpty()) " ${item.fileType}" else "", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}
