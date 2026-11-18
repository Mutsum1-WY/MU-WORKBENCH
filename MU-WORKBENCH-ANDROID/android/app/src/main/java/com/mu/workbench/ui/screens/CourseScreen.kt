package com.mu.workbench.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mu.workbench.data.CourseCatalogItem
import com.mu.workbench.data.ThemeStyle
import com.mu.workbench.ui.CourseSub
import com.mu.workbench.ui.WorkbenchViewModel
import com.mu.workbench.ui.components.CameraQrScanner
import com.mu.workbench.ui.components.MuCard
import com.mu.workbench.ui.components.MuChip
import com.mu.workbench.ui.components.MuTabTransition
import com.mu.workbench.ui.theme.LocalMuColors

@Composable
fun CourseScreen(vm: WorkbenchViewModel) {
    val mu = LocalMuColors.current
    val sub by vm.courseSub.collectAsState()
    val dir by vm.courseSubDir.collectAsState()

    Column(modifier = Modifier.fillMaxSize()) {
        // 吸顶可横滑子页签
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(if (mu.style == ThemeStyle.P5R) mu.chrome else mu.paper2)
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            CourseSub.entries.forEach { tab ->
                val on = tab == sub
                val chipBg = when {
                    on -> mu.accent
                    mu.style == ThemeStyle.P5R -> mu.chrome2
                    else -> mu.surface1
                }
                val chipTxt = when {
                    on -> Color.White
                    mu.style == ThemeStyle.P5R -> mu.chromeSoft
                    else -> mu.ink2
                }
                Surface(
                    color = chipBg,
                    shape = RoundedCornerShape(
                        when (mu.style) {
                            ThemeStyle.IOS -> 999.dp
                            ThemeStyle.P3R -> 5.dp
                            ThemeStyle.P5R -> 0.dp
                        }
                    ),
                    modifier = Modifier.clickable { vm.selectCourseSub(tab) }
                ) {
                    Text(
                        text = tab.label,
                        color = chipTxt,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                    )
                }
            }
        }

        MuTabTransition(
            targetState = sub,
            direction = dir,
            style = mu.style,
            modifier = Modifier.fillMaxSize()
        ) { cur ->
            when (cur) {
                CourseSub.COURSE -> CourseRatioPane(vm)
                CourseSub.BONUS -> BonusPane(vm)
                CourseSub.SCAN -> ScanPane(vm)
                CourseSub.HW -> HwArchivePane(vm)
                CourseSub.ZZ -> ZzPane(vm)
            }
        }
    }
}

/* ==================== 1. 课程性价比 ==================== */
@Composable
private fun CourseRatioPane(vm: WorkbenchViewModel) {
    val mu = LocalMuColors.current
    val state by vm.state.collectAsState()

    var major by remember { mutableStateOf(state.settings.courseMajor.ifEmpty { "se" }) }
    var q by remember { mutableStateOf("") }
    var termFilter by remember { mutableIntStateOf(0) } // 0=all, 1..8
    var modFilter by remember { mutableStateOf("") } // "" / "通识教育课程" / "专业教育课程"
    var sortBy by remember { mutableStateOf("v") } // v=性价比 cr=学分 h=学时 t=学期
    var optOnly by remember { mutableStateOf(false) }
    var examOnly by remember { mutableStateOf(false) }
    var bigOnly by remember { mutableStateOf(false) }
    var inTuiOnly by remember { mutableStateOf(false) }
    var showWeights by remember { mutableStateOf(false) }
    var limit by remember { mutableIntStateOf(24) }

    val catalog = vm.courseCatalog
    val rows = remember(major) { catalog.byMajor[major].orEmpty() }
    val majorInfo = catalog.majors.firstOrNull { it.key == major }
    val modules = remember(rows) { rows.map { it.module }.filter { it.isNotEmpty() }.distinct() }

    fun valueScore(c: CourseCatalogItem): Double {
        val hrs = if (c.hours > 0) c.hours else (c.credits * 16).coerceAtLeast(1.0)
        val examF = if (c.examType == "考查") state.settings.kExam else 1.0
        val termF = if (c.minTerm >= 7) state.settings.kOut else if (c.minTerm == 0) 0.6 else 1.0
        val diff = (1.0 + (c.difficulty - 1.0) * state.settings.kDiff).coerceAtLeast(0.2)
        val r = c.credits * examF * termF
        val cost = (hrs / 16.0) * diff
        return r / cost.coerceAtLeast(0.1)
    }

    fun gradeIdx(v: Double): Int = when {
        v >= 1.30 -> 0 // S
        v >= 1.05 -> 1 // A
        v >= 0.85 -> 2 // B
        v >= 0.60 -> 3 // C
        else -> 4 // D
    }

    val filtered = remember(rows, q, termFilter, modFilter, sortBy, optOnly, examOnly, bigOnly, inTuiOnly, state.settings.kExam, state.settings.kDiff, state.settings.kOut) {
        rows.filter { c ->
            if (termFilter > 0 && c.minTerm != termFilter) return@filter false
            if (modFilter.isNotEmpty() && c.module != modFilter) return@filter false
            if (optOnly && c.requiredType != "选修") return@filter false
            if (examOnly && c.examType != "考查") return@filter false
            if (bigOnly && c.credits < 3.0) return@filter false
            if (inTuiOnly && !(c.minTerm in 1..6)) return@filter false
            if (q.isNotBlank() && !c.name.contains(q, true) && !c.code.contains(q, true)) return@filter false
            true
        }.sortedWith(
            when (sortBy) {
                "cr" -> compareByDescending<CourseCatalogItem> { it.credits }.thenByDescending { valueScore(it) }
                "h" -> compareBy<CourseCatalogItem> { it.hours }.thenByDescending { valueScore(it) }
                "t" -> compareBy<CourseCatalogItem> { if (it.minTerm == 0) 9 else it.minTerm }.thenByDescending { valueScore(it) }
                else -> compareByDescending<CourseCatalogItem> { valueScore(it) }.thenByDescending { it.credits }
            }
        )
    }

    val shown = remember(filtered, limit) { filtered.take(limit) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(14.dp, 10.dp, 14.dp, 96.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // 专业与培养方案结构
        item(key = "cr_majors") {
            Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                catalog.majors.forEach { m ->
                    FilterChip(
                        selected = m.key == major,
                        onClick = {
                            major = m.key
                            limit = 24
                            vm.updateState { s -> s.copy(settings = s.settings.copy(courseMajor = m.key)) }
                        },
                        label = { Text(m.shortName) }
                    )
                }
            }
            majorInfo?.let {
                Text("${it.fullName}：${it.structureDesc}", fontSize = 11.5.sp, color = mu.ink3, modifier = Modifier.padding(top = 4.dp), lineHeight = 16.sp)
            }
        }

        // KPI 与等级分布卡
        item(key = "cr_kpi") {
            val avgV = if (filtered.isEmpty()) 0.0 else filtered.sumOf { valueScore(it) } / filtered.size
            val inTuiCount = rows.count { it.minTerm in 1..6 }
            val counts = IntArray(5)
            filtered.forEach { counts[gradeIdx(valueScore(it))]++ }
            val gradeCols = listOf(mu.ok, mu.accent, mu.accent2, mu.warn, mu.danger)
            val gradeNames = listOf("S", "A", "B", "C", "D")

            MuCard(padding = 12.dp) {
                Row(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("筛后课程", fontSize = 11.sp, color = mu.ink3)
                        Text("${filtered.size} / ${rows.size} 门", fontWeight = FontWeight.Black, fontSize = 16.sp, color = mu.ink)
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text("平均性价比", fontSize = 11.sp, color = mu.ink3)
                        Text("%.2f".format(avgV), fontWeight = FontWeight.Black, fontSize = 16.sp, color = mu.accentInk)
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text("推免计入", fontSize = 11.sp, color = mu.ink3)
                        Text("$inTuiCount / ${rows.size} 门", fontWeight = FontWeight.Black, fontSize = 16.sp, color = mu.ink)
                    }
                }
                if (filtered.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Row(modifier = Modifier.fillMaxWidth().height(7.dp).clip(RoundedCornerShape(3.dp))) {
                        counts.forEachIndexed { idx, cnt ->
                            if (cnt > 0) Box(modifier = Modifier.weight(cnt.toFloat()).fillMaxHeight().background(gradeCols[idx]))
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        gradeNames.forEachIndexed { idx, gn ->
                            Text("$gn:${counts[idx]}", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = gradeCols[idx])
                        }
                    }
                }
            }
        }

        // 搜索、筛选、权重参数面板
        item(key = "cr_filter") {
            OutlinedTextField(
                value = q,
                onValueChange = { q = it; limit = 24 },
                placeholder = { Text("搜索课程名称或代码...") },
                leadingIcon = { Icon(Icons.Rounded.Search, null) },
                trailingIcon = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (q.isNotEmpty()) IconButton(onClick = { q = "" }) { Icon(Icons.Rounded.Close, null) }
                        IconButton(onClick = { showWeights = !showWeights }) { Icon(Icons.Rounded.Tune, contentDescription = "调节权重", tint = mu.accentInk) }
                    }
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            AnimatedVisibility(visible = showWeights) {
                MuCard(padding = 12.dp, backgroundColor = mu.surface2, modifier = Modifier.padding(top = 8.dp)) {
                    Text("性价比模型权重调节", fontWeight = FontWeight.Black, fontSize = 13.sp, color = mu.ink)
                    Spacer(Modifier.height(4.dp))
                    Text("考查课偏爱权重 (kExam): ${"%.2f".format(state.settings.kExam)}", fontSize = 11.5.sp, color = mu.ink2)
                    Slider(
                        value = state.settings.kExam.toFloat(),
                        onValueChange = { v -> vm.updateState { s -> s.copy(settings = s.settings.copy(kExam = v.toDouble())) } },
                        valueRange = 0.8f..1.8f
                    )
                    Text("难度阻力惩罚 (kDiff): ${"%.2f".format(state.settings.kDiff)}", fontSize = 11.5.sp, color = mu.ink2)
                    Slider(
                        value = state.settings.kDiff.toFloat(),
                        onValueChange = { v -> vm.updateState { s -> s.copy(settings = s.settings.copy(kDiff = v.toDouble())) } },
                        valueRange = 0.0f..2.0f
                    )
                    Text("大四推免外权重 (kOut): ${"%.2f".format(state.settings.kOut)}", fontSize = 11.5.sp, color = mu.ink2)
                    Slider(
                        value = state.settings.kOut.toFloat(),
                        onValueChange = { v -> vm.updateState { s -> s.copy(settings = s.settings.copy(kOut = v.toDouble())) } },
                        valueRange = 0.05f..1.0f
                    )
                    TextButton(onClick = {
                        vm.updateState { s -> s.copy(settings = s.settings.copy(kExam = 1.25, kDiff = 1.0, kOut = 0.15)) }
                    }) { Text("恢复默认权重") }
                }
            }
            Spacer(Modifier.height(6.dp))
            Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(selected = sortBy == "v", onClick = { sortBy = "v" }, label = { Text("按性价比") })
                FilterChip(selected = sortBy == "cr", onClick = { sortBy = "cr" }, label = { Text("按学分") })
                FilterChip(selected = sortBy == "t", onClick = { sortBy = "t" }, label = { Text("按学期") })
                FilterChip(selected = sortBy == "h", onClick = { sortBy = "h" }, label = { Text("按学时") })
                FilterChip(selected = optOnly, onClick = { optOnly = !optOnly; limit = 24 }, label = { Text("只看选修") })
                FilterChip(selected = examOnly, onClick = { examOnly = !examOnly; limit = 24 }, label = { Text("只看考查") })
                FilterChip(selected = bigOnly, onClick = { bigOnly = !bigOnly; limit = 24 }, label = { Text("≥3学分") })
                FilterChip(selected = inTuiOnly, onClick = { inTuiOnly = !inTuiOnly; limit = 24 }, label = { Text("计入推免") })
            }
            Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(selected = modFilter == "", onClick = { modFilter = ""; limit = 24 }, label = { Text("全部模块") })
                modules.forEach { md ->
                    FilterChip(selected = modFilter == md, onClick = { modFilter = if (modFilter == md) "" else md; limit = 24 }, label = { Text(md.replace("课程", "")) })
                }
                (1..8).forEach { t ->
                    FilterChip(selected = termFilter == t, onClick = { termFilter = if (termFilter == t) 0 else t; limit = 24 }, label = { Text("S$t") })
                }
            }
        }

        items(shown, key = { "${major}_${it.code}_${it.name}" }) { c ->
            val v = valueScore(c)
            val grade = when (gradeIdx(v)) {
                0 -> "S" to mu.ok
                1 -> "A" to mu.accent
                2 -> "B" to mu.accent2
                3 -> "C" to mu.warn
                else -> "D" to mu.danger
            }
            val pctOfGrad = if (catalog.gradCredits > 0) c.credits / catalog.gradCredits * 100.0 else 0.0
            val smartNote = when {
                c.hours >= 100 && c.credits <= 3 -> "${c.hours.toInt()} 学时只换 ${c.credits} 学分，时间黑洞"
                c.minTerm >= 7 -> "第 ${c.minTerm} 学期，通常不计入推免 GPA"
                c.credits >= 4 && c.examType == "考试" -> "高学分硬考，GPA 杠杆大"
                c.practiceHours > 0 && c.hours > 0 && (c.practiceHours / c.hours) >= 0.28 -> "实践占 ${(c.practiceHours / c.hours * 100).toInt()}%"
                c.zzFlag == 1 -> "可对接创新实践 / 竞赛加分"
                else -> ""
            }
            MuCard(padding = 12.dp) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    MuChip(grade.first, grade.second, Color.White)
                    Spacer(Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(c.name, fontWeight = FontWeight.Bold, color = mu.ink, fontSize = 14.sp)
                        Text(
                            "${c.credits} 学分 · ${c.hours.toInt()} 学时 · 第 ${c.termSpec} 学期 · ${c.requiredType}/${c.examType}" +
                                if (c.practiceHours > 0) " · 实践 ${c.practiceHours.toInt()}h" else "",
                            fontSize = 11.5.sp,
                            color = mu.ink3
                        )
                        if (smartNote.isNotEmpty()) {
                            Text(smartNote, fontSize = 11.sp, color = mu.accentInk, fontWeight = FontWeight.Medium)
                        }
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            "%.2f".format(v),
                            fontWeight = FontWeight.Black,
                            fontStyle = if (mu.style != ThemeStyle.IOS) FontStyle.Italic else FontStyle.Normal,
                            style = TextStyle(fontFeatureSettings = "tnum"),
                            color = mu.accentInk,
                            fontSize = 15.sp
                        )
                        Text("占 %.1f%%".format(pctOfGrad), fontSize = 10.sp, color = mu.ink3)
                    }
                }
            }
        }

        if (filtered.size > shown.size) {
            item(key = "cr_more") {
                OutlinedButton(
                    onClick = { limit += 30 },
                    shape = RoundedCornerShape(mu.cardRadius),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("还有 ${filtered.size - shown.size} 门 · 继续加载更多", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

/* ==================== 3. 扫码签到 ==================== */
@Composable
fun ScanPane(vm: WorkbenchViewModel, modifier: Modifier = Modifier) {
    val mu = LocalMuColors.current
    val state by vm.state.collectAsState()
    val scanStatus by vm.scanStatus.collectAsState()
    val signResult by vm.lastQrSignResult.collectAsState()
    var zoom by remember { mutableFloatStateOf(1f) }
    var paused by remember { mutableStateOf(false) }
    var manual by remember { mutableStateOf("") }
    var showRawDiag by remember { mutableStateOf(false) }

    val uc = state.ucloud
    val hasToken = uc.token.isNotEmpty()
    val tokenFresh = hasToken && (System.currentTimeMillis() - uc.tokenAt) < 100 * 60 * 1000L

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(14.dp, 10.dp, 14.dp, 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 状态 HUD：云邮登录状态 & identity & 防翘课提示
        item(key = "sc_hud") {
            MuCard(padding = 10.dp, showCornerAccent = false) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    when {
                        tokenFresh -> MuChip("✓ 云邮已登录", mu.ok.copy(alpha = 0.16f), mu.ok)
                        hasToken -> MuChip("! 令牌将自动续期", mu.warn.copy(alpha = 0.16f), mu.warn)
                        else -> MuChip("× 云邮未登录", mu.danger.copy(alpha = 0.16f), mu.danger)
                    }
                    if (uc.identity.isNotEmpty()) {
                        MuChip(uc.identity, mu.surface3, mu.ink2)
                    }
                    Spacer(Modifier.weight(1f))
                    if (!hasToken && (uc.id.isBlank() || uc.pwd.isBlank())) {
                        Text(
                            "去设置登录",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = mu.accentInk,
                            modifier = Modifier.clickable { vm.openSettings() }
                        )
                    }
                }
            }
        }

        item(key = "sc_cam") {
            MuCard(padding = 12.dp) {
                CameraQrScanner(
                    paused = paused,
                    zoomRatio = zoom,
                    onZoomChange = { zoom = it },
                    onResult = { code ->
                        if (!paused) {
                            paused = true
                            vm.submitQrSign(code)
                        }
                    }
                )
                if (!scanStatus.isNullOrEmpty() && signResult == null) {
                    Spacer(Modifier.height(10.dp))
                    Text(scanStatus!!, fontWeight = FontWeight.Bold, color = mu.accentInk, fontSize = 13.5.sp)
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            vm.clearQrSignResult()
                            paused = false
                        },
                        shape = RoundedCornerShape(mu.cardRadius),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(if (paused) "再扫一次" else "扫描中...", fontWeight = FontWeight.Bold)
                    }
                    OutlinedButton(
                        onClick = { paused = !paused },
                        shape = RoundedCornerShape(mu.cardRadius),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(if (paused) "恢复取景" else "暂停取景")
                    }
                }
            }
        }

        // 签到结果反馈卡片（对齐 index.html scResult + 原始返回诊断）
        signResult?.let { res ->
            item(key = "sc_res") {
                val resColor = if (res.ok) mu.ok else mu.danger
                MuCard(
                    padding = 12.dp,
                    borderColor = resColor,
                    backgroundColor = resColor.copy(alpha = 0.08f)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = if (res.ok) "✓ 签到成功" else "× 这次没签上",
                            fontWeight = FontWeight.Black,
                            fontSize = 15.sp,
                            color = resColor,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedButton(
                            onClick = {
                                vm.clearQrSignResult()
                                paused = false
                            },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                            shape = RoundedCornerShape(mu.cardRadius),
                            modifier = Modifier.height(30.dp)
                        ) {
                            Text("继续扫码", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(res.summaryMsg, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = mu.ink)
                    if (res.qrText.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Text("扫到的二维码内容：", fontSize = 11.sp, color = mu.ink3)
                        Text(
                            text = res.qrText,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            color = mu.ink2,
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(mu.surface1)
                                .border(1.dp, mu.line2)
                                .padding(6.dp)
                        )
                    }
                    if (res.tried.isNotEmpty() || res.okResp.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        TextButton(
                            onClick = { showRawDiag = !showRawDiag },
                            contentPadding = PaddingValues(0.dp),
                            modifier = Modifier.height(26.dp)
                        ) {
                            val cntText = if (res.tried.isNotEmpty()) "${res.tried.size} 次尝试" else "已成功"
                            Text(
                                if (showRawDiag) "▾ 收起接口返回（$cntText）" else "▸ 查看接口返回（$cntText）",
                                fontSize = 11.5.sp,
                                color = mu.accentInk,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        AnimatedVisibility(visible = showRawDiag) {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 6.dp)) {
                                if (res.okResp.isNotEmpty()) {
                                    Text(
                                        text = "提交：${res.okBody}\n返回：${res.okResp}",
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 10.5.sp,
                                        color = mu.ink2,
                                        modifier = Modifier.fillMaxWidth().background(mu.surface1).padding(6.dp)
                                    )
                                }
                                res.tried.forEachIndexed { idx, att ->
                                    Text(
                                        text = "#${idx + 1} HTTP ${att.status} (${att.code}) ${att.msg}\n体：${att.bodyJson}\n回：${att.rawResp}",
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 10.5.sp,
                                        color = mu.ink2,
                                        modifier = Modifier.fillMaxWidth().background(mu.surface1).padding(6.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        item(key = "sc_manual") {
            MuCard(padding = 12.dp) {
                Text("手动输入签到码（兜底）", fontWeight = FontWeight.Bold, fontSize = 13.5.sp, color = mu.ink)
                Spacer(Modifier.height(4.dp))
                Text(
                    "支持原样扫码串 checkwork|id=...&siteId=...&createTime=...，提交时会自动 URL 解码防篡改时间戳。",
                    fontSize = 11.sp,
                    color = mu.ink3
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = manual,
                    onValueChange = { manual = it },
                    label = { Text("checkwork|id=...") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = {
                        if (manual.isNotBlank()) {
                            paused = true
                            vm.submitQrSign(manual)
                        }
                    },
                    shape = RoundedCornerShape(mu.cardRadius),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("提交签到", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
