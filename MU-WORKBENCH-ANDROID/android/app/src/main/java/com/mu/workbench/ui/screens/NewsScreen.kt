package com.mu.workbench.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.mu.workbench.data.PortalArticle
import com.mu.workbench.data.PortalAttachment
import com.mu.workbench.data.PortalBlock
import com.mu.workbench.data.ThemeStyle
import com.mu.workbench.domain.NativeClients
import com.mu.workbench.domain.TodoEngine
import com.mu.workbench.ui.DlStatus
import com.mu.workbench.ui.WorkbenchViewModel
import com.mu.workbench.ui.components.MuCard
import com.mu.workbench.ui.components.MuChip
import com.mu.workbench.ui.components.MuSegmentedRow
import com.mu.workbench.ui.theme.LocalMuColors
import com.mu.workbench.ui.theme.UiSkin
import kotlinx.coroutines.CancellationException
import java.time.temporal.ChronoUnit

private const val MAX_RENDER_IMAGES = 60
private val LECTURE_FIELD_PREFIXES = listOf(
    "报告题目", "报告人", "主讲人", "主持人", "报告时间", "讲座时间", "腾讯会议", "会议号", "地点"
)

private fun isLectureFieldLine(text: String): Boolean {
    val compact = text.replace(Regex("""\s+"""), "")
    return LECTURE_FIELD_PREFIXES.any { compact.startsWith("$it：") || compact.startsWith("$it:") }
}

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
        contentPadding = PaddingValues(start = 10.dp, end = 10.dp, top = 8.dp, bottom = 18.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp)
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
                MuCard(padding = 10.dp, onClick = { vm.openArticle(art) }) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (art.isNew) MuChip("新", mu.accent, Color.White)
                        Text(art.title, fontWeight = FontWeight.Bold, fontSize = 14.5.sp, color = mu.ink, modifier = Modifier.weight(1f))
                        if (art.imageCount > 0) {
                            MuChip("图 ${art.imageCount}", mu.accentSoft, mu.accentInk)
                        }
                    }
                    val subBits = if (art.kind == "lecture" && art.lecture != null) {
                        listOf(
                            art.lecture.whenText.ifEmpty { art.date },
                            art.lecture.who,
                            art.lecture.where,
                            art.dept
                        ).filter { it.isNotBlank() }.distinct()
                    } else {
                        listOf(art.date, art.dept).filter { it.isNotBlank() }
                    }
                    if (subBits.isNotEmpty()) {
                        Spacer(Modifier.height(5.dp))
                        Text(
                            subBits.joinToString(" · "),
                            fontSize = 11.5.sp,
                            color = mu.ink3,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
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
    refreshSeq: Int = 0,
    dlStates: Map<String, DlStatus> = emptyMap(),
    onLoadImage: suspend (url: String, altUrl: String, force: Boolean) -> List<ImageBitmap> = { _, _, _ -> emptyList() },
    onRefresh: () -> Unit = {},
    onDownloadAttachment: ((PortalAttachment) -> Unit)? = null,
    onClose: () -> Unit,
    onOpenBrowser: (String) -> Unit
) {
    val mu = LocalMuColors.current
    var previewSlices by remember { mutableStateOf<List<ImageBitmap>?>(null) }

    val skipLectureFields = article.kind == "lecture" && article.lecture != null
    val (displayBlocks, skippedImages) = remember(article.blocks, skipLectureFields) {
        val out = mutableListOf<PortalBlock>()
        var imgs = 0
        var skipped = 0
        for (b in article.blocks) {
            if (b.type == "img") {
                if (imgs < MAX_RENDER_IMAGES) {
                    out += b
                    imgs++
                } else {
                    skipped++
                }
            } else {
                if (skipLectureFields && b.type != "hr" && isLectureFieldLine(b.text)) continue
                out += b
            }
        }
        out to skipped
    }

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
                    IconButton(onClick = onRefresh, enabled = !loading) {
                        Icon(Icons.Rounded.Refresh, contentDescription = "刷新正文与图片")
                    }
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
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(mu.paper)
                .padding(pad),
            contentPadding = PaddingValues(12.dp, 10.dp, 12.dp, 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item(key = "rd_head") {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    SelectionContainer {
                        Text(article.title, fontSize = 19.sp, fontWeight = FontWeight.ExtraBold, color = mu.ink)
                    }
                    val metaText = buildList {
                        if (article.dept.isNotEmpty()) add(article.dept)
                        if (article.date.isNotEmpty()) add(article.date)
                        if (article.imageCount > 0) add("共 ${article.imageCount} 张图片")
                    }.joinToString(" · ")
                    if (metaText.isNotEmpty()) {
                        Text(metaText, fontSize = 12.sp, color = mu.ink3)
                    }
                    HorizontalDivider(color = mu.line, modifier = Modifier.padding(top = 4.dp))
                }
            }

            if (article.kind == "lecture" && article.lecture != null) {
                val lec = article.lecture
                val rows = listOf(
                    "报告时间" to lec.whenText,
                    "报告人" to lec.who,
                    "主持人" to lec.host,
                    "地点 / 会议" to lec.where
                ).filter { it.second.isNotBlank() }
                if (rows.isNotEmpty()) {
                    item(key = "rd_lec_meta") {
                        MuCard(padding = 12.dp, backgroundColor = mu.surface2) {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                rows.forEach { (k, v) ->
                                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
                                        Text(
                                            k,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = mu.ink3,
                                            modifier = Modifier.width(68.dp)
                                        )
                                        SelectionContainer(modifier = Modifier.weight(1f)) {
                                            Text(v, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = mu.ink)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (loading) {
                item(key = "rd_loading") {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = mu.accent)
                        Text("正在读取正文与图片地址...", color = mu.ink3, fontSize = 13.sp)
                    }
                }
            } else if (displayBlocks.isNotEmpty()) {
                itemsIndexed(
                    items = displayBlocks,
                    key = { idx, b -> "blk_${article.id}_${refreshSeq}_${idx}_${b.type}" }
                ) { _, b ->
                    when (b.type) {
                        "img" -> PortalImageBlock(
                            block = b,
                            refreshSeq = refreshSeq,
                            onLoadImage = onLoadImage,
                            onPreview = { previewSlices = it }
                        )
                        "hr" -> HorizontalDivider(color = mu.line2, modifier = Modifier.padding(vertical = 4.dp))
                        "h" -> SelectionContainer {
                            Text(
                                text = b.text,
                                fontSize = when (b.level) {
                                    1 -> 17.5.sp
                                    2 -> 16.sp
                                    else -> 15.sp
                                },
                                fontWeight = FontWeight.ExtraBold,
                                lineHeight = 24.sp,
                                color = mu.ink
                            )
                        }
                        "li" -> Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.Top,
                            modifier = Modifier.padding(start = 4.dp)
                        ) {
                            Text("•", fontSize = 14.5.sp, color = mu.accentInk, fontWeight = FontWeight.Bold)
                            SelectionContainer(modifier = Modifier.weight(1f)) {
                                Text(b.text, fontSize = 14.5.sp, lineHeight = 23.sp, color = mu.ink)
                            }
                        }
                        "q" -> Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(mu.surface2, RoundedCornerShape(4.dp))
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            SelectionContainer {
                                Text(b.text, fontSize = 14.sp, lineHeight = 22.sp, color = mu.ink2)
                            }
                        }
                        else -> SelectionContainer {
                            Text(b.text, fontSize = 14.5.sp, lineHeight = 23.sp, color = mu.ink)
                        }
                    }
                }
                if (skippedImages > 0) {
                    item(key = "rd_img_limit_note") {
                        Text(
                            "还有 $skippedImages 张图片未显示（单篇最多展示 $MAX_RENDER_IMAGES 张），可点右上角在浏览器查看原文。",
                            fontSize = 12.sp,
                            color = mu.ink3
                        )
                    }
                }
            } else {
                item(key = "rd_fallback_text") {
                    SelectionContainer {
                        Text(
                            article.bodyText.ifEmpty { "点击右上角可在浏览器中打开原文" },
                            fontSize = 14.5.sp,
                            lineHeight = 23.sp,
                            color = mu.ink
                        )
                    }
                }
            }

            if (article.attachments.isNotEmpty()) {
                item(key = "rd_att_header") {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 6.dp)) {
                        HorizontalDivider(color = mu.line)
                        Text("附件 (${article.attachments.size})", fontWeight = FontWeight.Bold, color = mu.ink)
                    }
                }
                items(article.attachments, key = { "att_${it.url}" }) { att ->
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

            item(key = "rd_bottom_pad") {
                Spacer(Modifier.height(20.dp))
            }
        }
    }

    previewSlices?.let { slices ->
        PortalImagePreviewDialog(
            slices = slices,
            onDismiss = { previewSlices = null }
        )
    }
}

@Composable
private fun PortalImageBlock(
    block: PortalBlock,
    refreshSeq: Int,
    onLoadImage: suspend (url: String, altUrl: String, force: Boolean) -> List<ImageBitmap>,
    onPreview: (List<ImageBitmap>) -> Unit
) {
    val mu = LocalMuColors.current
    var slices by remember(block.src, refreshSeq) { mutableStateOf<List<ImageBitmap>?>(null) }
    var errMsg by remember(block.src, refreshSeq) { mutableStateOf<String?>(null) }
    var retryTick by remember { mutableIntStateOf(0) }

    LaunchedEffect(block.src, block.altSrc, refreshSeq, retryTick) {
        slices = null
        errMsg = null
        try {
            slices = onLoadImage(block.src, block.altSrc, retryTick > 0)
        } catch (ce: CancellationException) {
            throw ce
        } catch (t: Throwable) {
            errMsg = t.message ?: "网络异常"
        }
    }

    val shape = RoundedCornerShape(if (mu.skin == UiSkin.IOS) 8.dp else 2.dp)
    when {
        slices != null -> {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(shape)
                    .border(1.dp, mu.line2, shape)
                    .clickable { onPreview(slices!!) },
                verticalArrangement = Arrangement.spacedBy(0.dp)
            ) {
                slices!!.forEach { bmp ->
                    val ratio = (bmp.width.toFloat() / bmp.height.coerceAtLeast(1).toFloat()).coerceAtLeast(0.05f)
                    Image(
                        bitmap = bmp,
                        contentDescription = null,
                        contentScale = ContentScale.FillWidth,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(ratio)
                    )
                }
            }
        }
        errMsg != null -> {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(shape)
                    .background(mu.surface2)
                    .border(1.dp, mu.danger.copy(alpha = 0.55f), shape)
                    .clickable { retryTick++ }
                    .padding(horizontal = 14.dp, vertical = 18.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "图片未加载出来（$errMsg）· 点击重试",
                    color = mu.danger,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
        else -> {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp)
                    .clip(shape)
                    .background(mu.surface2)
                    .border(1.dp, mu.line2, shape),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = mu.accent)
                    Text("正在加载图片...", color = mu.ink3, fontSize = 12.5.sp)
                }
            }
        }
    }
}

@Composable
private fun PortalImagePreviewDialog(
    slices: List<ImageBitmap>,
    onDismiss: () -> Unit
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.95f))
                .pointerInput(Unit) {
                    detectTapGestures(
                        onDoubleTap = {
                            if (scale > 1.05f) {
                                scale = 1f
                                offsetX = 0f
                                offsetY = 0f
                            } else {
                                scale = 2.5f
                            }
                        }
                    )
                }
                .pointerInput(Unit) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        do {
                            val event = awaitPointerEvent()
                            val zoomChange = event.calculateZoom()
                            val panChange = event.calculatePan()
                            val multiTouch = event.changes.count { it.pressed } > 1
                            if (multiTouch || scale > 1.02f) {
                                val nextScale = (scale * zoomChange).coerceIn(1f, 5f)
                                scale = nextScale
                                if (nextScale <= 1.02f) {
                                    offsetX = 0f
                                    offsetY = 0f
                                } else {
                                    offsetX += panChange.x
                                    offsetY += panChange.y
                                }
                                event.changes.forEach {
                                    if (it.positionChanged()) it.consume()
                                }
                            }
                        } while (event.changes.any { it.pressed })
                    }
                }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState(), enabled = scale <= 1.02f)
                    .padding(vertical = 56.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                            translationX = offsetX
                            translationY = offsetY
                        },
                    verticalArrangement = Arrangement.spacedBy(0.dp)
                ) {
                    slices.forEach { bmp ->
                        val ratio = (bmp.width.toFloat() / bmp.height.coerceAtLeast(1).toFloat()).coerceAtLeast(0.05f)
                        Image(
                            bitmap = bmp,
                            contentDescription = null,
                            contentScale = ContentScale.FillWidth,
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(ratio)
                        )
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "双击或双指缩放 · ${"%.1f".format(scale)}×",
                    color = Color.White.copy(alpha = 0.85f),
                    fontSize = 12.5.sp,
                    modifier = Modifier
                        .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(999.dp))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                )
                Spacer(Modifier.weight(1f))
                if (scale > 1.02f) {
                    TextButton(onClick = { scale = 1f; offsetX = 0f; offsetY = 0f }) {
                        Text("还原", color = Color.White)
                    }
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Rounded.Close, contentDescription = "关闭预览", tint = Color.White)
                }
            }
        }
    }
}
