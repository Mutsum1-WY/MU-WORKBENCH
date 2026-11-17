package com.mu.workbench.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.OpenInBrowser
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mu.workbench.data.PortalArticle
import com.mu.workbench.data.PortalAttachment
import com.mu.workbench.domain.NativeClients
import com.mu.workbench.domain.TodoEngine
import com.mu.workbench.ui.DlStatus
import com.mu.workbench.ui.WorkbenchViewModel
import com.mu.workbench.ui.components.MuCard
import com.mu.workbench.ui.components.MuChip
import com.mu.workbench.ui.components.MuSegmentedRow
import com.mu.workbench.ui.theme.LocalMuColors
import java.time.LocalDate
import java.time.temporal.ChronoUnit

@Composable
fun NewsScreen(vm: WorkbenchViewModel) {
    val mu = LocalMuColors.current
    val state by vm.state.collectAsState()
    val today by vm.nowDate.collectAsState()

    var kind by remember { mutableStateOf(state.portal.kind.ifEmpty { "notice" }) }
    var q by remember { mutableStateOf("") }
    var range by remember { mutableStateOf("all") } // all / 7d / 30d
    var onlyNew by remember { mutableStateOf(false) }
    var selectedDept by remember { mutableStateOf("") }
    var showDeptMenu by remember { mutableStateOf(false) }

    val rawList = if (kind == "lecture") state.portal.lectures else state.portal.notices
    val deptOptions = remember(rawList) {
        rawList.map { it.dept }.filter { it.isNotBlank() }.distinct().sorted()
    }

    val filtered = remember(rawList, q, range, onlyNew, selectedDept, today) {
        rawList.filter { art ->
            if (onlyNew && !art.isNew) return@filter false
            if (selectedDept.isNotEmpty() && art.dept != selectedDept) return@filter false
            if (range != "all") {
                val d = TodoEngine.parseDate(art.date.take(10))
                if (d == null) return@filter false
                val diff = ChronoUnit.DAYS.between(d, today)
                val limit = if (range == "7d") 7L else 30L
                if (diff < 0 || diff > limit) return@filter false
            }
            if (q.isNotBlank()) {
                val hit = art.title.contains(q, true) || art.bodyText.contains(q, true) || art.dept.contains(q, true)
                if (!hit) return@filter false
            }
            true
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item(key = "pt_top") {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(modifier = Modifier.weight(1f)) {
                    MuSegmentedRow(
                        items = listOf("notice" to "校内通知", "lecture" to "学术讲座"),
                        selected = kind,
                        onSelect = {
                            kind = it
                            selectedDept = ""
                        }
                    )
                }
                IconButton(onClick = { vm.syncPortalNews(kind, append = false) }) {
                    Icon(Icons.Rounded.Refresh, contentDescription = "刷新资讯", tint = mu.accentInk)
                }
            }
        }

        item(key = "pt_search_filter") {
            OutlinedTextField(
                value = q,
                onValueChange = { q = it },
                placeholder = { Text("搜标题、部门或正文...") },
                leadingIcon = { Icon(Icons.Rounded.Search, null) },
                trailingIcon = {
                    if (q.isNotEmpty()) {
                        IconButton(onClick = { q = "" }) {
                            Icon(Icons.Rounded.Close, contentDescription = "清空")
                        }
                    }
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(6.dp))
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilterChip(selected = range == "all", onClick = { range = "all" }, label = { Text("全部时间") })
                FilterChip(selected = range == "7d", onClick = { range = "7d" }, label = { Text("近 7 天") })
                FilterChip(selected = range == "30d", onClick = { range = "30d" }, label = { Text("近 30 天") })
                FilterChip(selected = onlyNew, onClick = { onlyNew = !onlyNew }, label = { Text("只看新") })

                Box {
                    FilterChip(
                        selected = selectedDept.isNotEmpty(),
                        onClick = { showDeptMenu = true },
                        label = { Text(selectedDept.ifEmpty { "全部部门" }) },
                        trailingIcon = { Icon(Icons.Rounded.ArrowDropDown, null, modifier = Modifier.size(18.dp)) }
                    )
                    DropdownMenu(expanded = showDeptMenu, onDismissRequest = { showDeptMenu = false }) {
                        DropdownMenuItem(
                            text = { Text("全部部门") },
                            onClick = { selectedDept = ""; showDeptMenu = false }
                        )
                        deptOptions.forEach { d ->
                            DropdownMenuItem(
                                text = { Text(d) },
                                onClick = { selectedDept = d; showDeptMenu = false }
                            )
                        }
                    }
                }
            }
        }

        if (state.portal.lastError.isNotEmpty()) {
            item(key = "pt_err") {
                MuCard(borderColor = mu.danger, backgroundColor = mu.surface2) {
                    Text(state.portal.lastError, color = mu.danger, fontSize = 12.5.sp)
                }
            }
        }

        if (filtered.isEmpty()) {
            item(key = "pt_empty") {
                MuCard(backgroundColor = mu.surface2, onClick = { vm.syncPortalNews(kind, append = false) }) {
                    Text(
                        if (rawList.isEmpty()) "暂无本地资讯 · 先在设置填好学工号密码后点此拉取"
                        else "当前筛选条件下没有匹配的资讯（共 ${rawList.size} 条）",
                        color = mu.ink2, fontSize = 13.sp
                    )
                }
            }
        } else {
            items(filtered, key = { "${it.kind}_${it.id}" }) { art ->
                MuCard(padding = 12.dp, onClick = { vm.openArticle(art) }) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (art.isNew) MuChip("新", mu.accent, Color.White)
                        Text(art.title, fontWeight = FontWeight.Bold, fontSize = 14.5.sp, color = mu.ink, modifier = Modifier.weight(1f))
                    }
                    if (art.date.isNotEmpty() || art.dept.isNotEmpty()) {
                        Spacer(Modifier.height(5.dp))
                        Text(
                            listOf(art.date, art.dept).filter { it.isNotEmpty() }.joinToString(" · "),
                            fontSize = 11.5.sp,
                            color = mu.ink3
                        )
                    }
                }
            }

            item(key = "pt_more") {
                OutlinedButton(
                    onClick = { vm.syncPortalNews(kind, append = true) },
                    shape = RoundedCornerShape(mu.cardRadius),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("加载更多资讯（当前共 ${rawList.size} 条）", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderOverlay(
    article: PortalArticle,
    loading: Boolean,
    dlStates: Map<String, DlStatus> = emptyMap(),
    onDownloadAttachment: ((PortalAttachment) -> Unit)? = null,
    onClose: () -> Unit,
    onOpenBrowser: (String) -> Unit
) {
    val mu = LocalMuColors.current
    Scaffold(
        containerColor = mu.paper,
        topBar = {
            TopAppBar(
                title = { Text(article.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 15.sp, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { onOpenBrowser(article.url) }) {
                        Icon(Icons.Rounded.OpenInBrowser, contentDescription = "浏览器打开")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = mu.chrome,
                    titleContentColor = mu.chromeInk,
                    navigationIconContentColor = mu.chromeInk,
                    actionIconContentColor = mu.chromeInk
                )
            )
        }
    ) { pad ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(mu.paper)
                .padding(pad)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(article.title, fontSize = 19.sp, fontWeight = FontWeight.ExtraBold, color = mu.ink)
            if (article.date.isNotEmpty() || article.dept.isNotEmpty()) {
                Text(
                    listOf(article.dept, article.date).filter { it.isNotEmpty() }.joinToString(" · "),
                    fontSize = 12.sp,
                    color = mu.ink3
                )
            }
            HorizontalDivider(color = mu.line)
            if (loading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = mu.accent)
                Text("正在读取正文...", color = mu.ink3, fontSize = 13.sp)
            } else {
                Text(article.bodyText.ifEmpty { "点击右上角可在浏览器中打开原文" }, fontSize = 14.5.sp, lineHeight = 23.sp, color = mu.ink)
            }
            if (article.attachments.isNotEmpty()) {
                HorizontalDivider(color = mu.line)
                Text("附件 (${article.attachments.size})", fontWeight = FontWeight.Bold, color = mu.ink)
                article.attachments.forEach { att ->
                    val dlKey = "pt_${NativeClients.md5Hex(att.url).take(12)}"
                    val dl = dlStates[dlKey]
                    MuCard(padding = 10.dp) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(att.name, fontWeight = FontWeight.Bold, color = mu.ink, fontSize = 13.5.sp, modifier = Modifier.weight(1f))
                            if (onDownloadAttachment != null) {
                                OutlinedButton(
                                    onClick = { onDownloadAttachment(att) },
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                    modifier = Modifier.height(30.dp)
                                ) {
                                    val txt = when {
                                        dl?.uri != null -> "打开"
                                        dl?.loading == true -> "${dl.pct}%"
                                        else -> "下载"
                                    }
                                    Text(txt, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                            TextButton(
                                onClick = { onOpenBrowser(att.url) },
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                                modifier = Modifier.height(30.dp)
                            ) {
                                Text("浏览器", fontSize = 11.5.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}
