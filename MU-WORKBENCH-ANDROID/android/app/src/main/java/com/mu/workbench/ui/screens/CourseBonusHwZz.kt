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
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mu.workbench.data.BonusEntry
import com.mu.workbench.data.BonusOther
import com.mu.workbench.data.HwAttachment
import com.mu.workbench.data.HwSubmissionItem
import com.mu.workbench.data.ThemeStyle
import com.mu.workbench.data.ZzCustomCourse
import com.mu.workbench.ui.WorkbenchViewModel
import com.mu.workbench.ui.components.MuCard
import com.mu.workbench.ui.components.MuChip
import com.mu.workbench.ui.components.MuSectionHeader
import com.mu.workbench.ui.components.MuSegmentedRow
import com.mu.workbench.ui.theme.LocalMuColors
import java.util.UUID
import kotlin.math.roundToInt

/* ==================== 2. 课外加分 ==================== */
@Composable
fun BonusPane(vm: WorkbenchViewModel) {
    val mu = LocalMuColors.current
    val state by vm.state.collectAsState()
    val bd = vm.bonusData

    var q by remember { mutableStateOf("") }
    var catFilter by remember { mutableStateOf("all") } // all / act / comp
    var lvFilter by remember { mutableStateOf("ALL") }
    var showCalc by remember { mutableStateOf(true) }
    var calcLvlKey by remember { mutableStateOf("S") }
    var calcPrizeIdx by remember { mutableIntStateOf(0) } // 0=一等 1=二等 2=三等
    var calcTeamSize by remember { mutableIntStateOf(1) }
    var calcRank by remember { mutableIntStateOf(1) }
    var openCompName by remember { mutableStateOf("") }
    var openOtherKey by remember { mutableStateOf("") }
    var showRules by remember { mutableStateOf(false) }
    var showDocs by remember { mutableStateOf(false) }

    val totalPi = state.bonus.entries.sumOf { it.pi }
    val achPi = state.bonus.entries.filter { it.ach }.sumOf { it.pi }
    val bestTui = (state.bonus.entries.maxOfOrNull { it.tui } ?: 0.0).coerceAtMost(4.0)
    val grade = when {
        totalPi > 10.0 && achPi >= 6.0 && state.bonus.hasCityDc -> "优秀" to mu.ok
        totalPi >= 8.0 -> "良好" to mu.ok
        totalPi >= 6.0 -> "中评" to mu.accent
        totalPi >= 4.0 -> "合格" to mu.warn
        else -> "待达标" to mu.ink3
    }

    val filteredOthers = remember(q, catFilter, bd.others) {
        if (catFilter == "comp") emptyList()
        else bd.others.filter {
            q.isBlank() || it.name.contains(q, true) || it.cat.contains(q, true) || it.note.contains(q, true)
        }
    }
    val filteredComps = remember(q, catFilter, lvFilter, bd.comps) {
        if (catFilter == "act") emptyList()
        else bd.comps.filter {
            (lvFilter == "ALL" || it.level == lvFilter) && (q.isBlank() || it.name.contains(q, true) || it.level.contains(q, true))
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(mu.paper),
        contentPadding = PaddingValues(14.dp, 10.dp, 14.dp, 96.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // 双线积分看板
        item(key = "bonus_board") {
            MuCard(padding = 14.dp) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "创新实践积分：%.1f 分".format(totalPi),
                            fontWeight = FontWeight.Black,
                            fontStyle = FontStyle.Normal,
                            letterSpacing = (-0.2).sp,
                            fontSize = 17.sp,
                            color = mu.ok
                        )
                        Text(
                            "成果类 %.1f/6.0 分 · 推免加分最高项 %.2f / 4.0 分".format(achPi, bestTui),
                            fontSize = 12.sp,
                            color = mu.ink2
                        )
                    }
                    MuChip(grade.first, grade.second, Color.White)
                }
                Spacer(Modifier.height(8.dp))
                Text("创新实践线（4 合格 / 8 良好 / >10 需市级大创+成果>=6）", fontSize = 11.sp, color = mu.ink3)
                Spacer(Modifier.height(4.dp))
                Box(modifier = Modifier.fillMaxWidth().height(7.dp).clip(RoundedCornerShape(if (mu.style == ThemeStyle.IOS) 999.dp else 0.dp)).background(mu.surface3)) {
                    Box(modifier = Modifier.fillMaxWidth((totalPi / 12.0).toFloat().coerceIn(0f, 1f)).fillMaxHeight().background(mu.ok))
                }
                Spacer(Modifier.height(8.dp))
                Text("推免加分线（51号：最高单条计入，上限 4.0 分）", fontSize = 11.sp, color = mu.ink3)
                Spacer(Modifier.height(4.dp))
                Box(modifier = Modifier.fillMaxWidth().height(7.dp).clip(RoundedCornerShape(if (mu.style == ThemeStyle.IOS) 999.dp else 0.dp)).background(mu.surface3)) {
                    Box(modifier = Modifier.fillMaxWidth((bestTui / 4.0).toFloat().coerceIn(0f, 1f)).fillMaxHeight().background(mu.accent))
                }
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("已完成市级及以上大创结题", fontSize = 12.5.sp, color = mu.ink2, modifier = Modifier.weight(1f))
                    Switch(
                        checked = state.bonus.hasCityDc,
                        onCheckedChange = { v -> vm.updateState { s -> s.copy(bonus = s.bonus.copy(hasCityDc = v)) } }
                    )
                }
            }
        }

        // 交互式竞赛与团队算分器
        item(key = "bonus_calc") {
            val lv = bd.levels.firstOrNull { it.key == calcLvlKey } ?: bd.levels.firstOrNull()
            val piScore = lv?.pi?.getOrNull(calcPrizeIdx) ?: 0.0
            val tuiBase = lv?.tui?.getOrNull(calcPrizeIdx)
            val tuiTeam = if (tuiBase == null) null else if (calcTeamSize <= 1) tuiBase else tuiBase * (1.0 + (calcTeamSize - 1) * 0.6) / calcTeamSize

            MuCard(padding = 12.dp) {
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { showCalc = !showCalc },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(if (showCalc) "▾ 竞赛 / 团队加分算分器" else "▸ 竞赛 / 团队加分算分器", fontWeight = FontWeight.ExtraBold, color = mu.ink, modifier = Modifier.weight(1f))
                    MuChip("创新 +$piScore", mu.ok.copy(alpha = 0.16f), mu.ok)
                }
                AnimatedVisibility(visible = showCalc) {
                    Column(modifier = Modifier.padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            bd.levels.forEach { l ->
                                FilterChip(
                                    selected = calcLvlKey == l.key,
                                    onClick = { calcLvlKey = l.key },
                                    label = { Text(l.name.replace("国家级 · ", "").replace("省部级 · ", "省").replace("校级 · ", "")) }
                                )
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf("一等奖", "二等奖", "三等奖").forEachIndexed { i, pName ->
                                FilterChip(
                                    selected = calcPrizeIdx == i,
                                    onClick = { calcPrizeIdx = i },
                                    label = { Text(pName) }
                                )
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("团队人数:", fontSize = 12.sp, color = mu.ink2)
                            (1..6).forEach { n ->
                                FilterChip(
                                    selected = calcTeamSize == n,
                                    onClick = {
                                        calcTeamSize = n
                                        if (calcRank > n) calcRank = n
                                    },
                                    label = { Text(if (n == 1) "个人" else "${n}人") }
                                )
                            }
                        }
                        val calcSummary = buildString {
                            append("创新实践积分 +$piScore（团队不稀释）。")
                            if (tuiTeam == null) {
                                append("推免不计分。")
                            } else if (calcTeamSize == 1) {
                                append("推免个人 +%.2f 分。".format(tuiTeam))
                            } else {
                                val coef = 1.0 + (calcTeamSize - 1) * 0.6
                                append("${calcTeamSize}人队系数 %.1f，主力平均每人推免 +%.2f 分（限前6名）。".format(coef, tuiTeam))
                            }
                        }
                        Text(calcSummary, fontSize = 12.sp, color = mu.ink2, lineHeight = 18.sp)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = if (tuiTeam != null) "推免预估 +%.2f 分".format(tuiTeam) else "仅计创新实践分",
                                fontWeight = FontWeight.Black,
                                color = mu.accentInk,
                                modifier = Modifier.weight(1f)
                            )
                            Button(
                                onClick = {
                                    val prizeName = listOf("一等", "二等", "三等")[calcPrizeIdx]
                                    val entry = BonusEntry(
                                        id = "b_${UUID.randomUUID().toString().take(6)}",
                                        title = "${lv?.name ?: calcLvlKey} $prizeName" + if (calcTeamSize > 1) " (${calcTeamSize}人队)" else "",
                                        pi = piScore,
                                        tui = tuiTeam ?: 0.0,
                                        ach = true,
                                        cat = "竞赛"
                                    )
                                    vm.updateState { s -> s.copy(bonus = s.bonus.copy(entries = s.bonus.entries + entry)) }
                                    vm.toast("已将计算结果记入账本")
                                },
                                shape = RoundedCornerShape(mu.cardRadius),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                            ) {
                                Text("记入我的账本", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }

        // 我的账本
        item(key = "bonus_ledger_head") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                MuSectionHeader("我的加分账本", "${state.bonus.entries.size} 项", modifier = Modifier.weight(1f))
                if (state.bonus.entries.isNotEmpty()) {
                    TextButton(onClick = {
                        vm.askConfirm("清空账本", "确定清空所有已记录的加分条目吗？") {
                            vm.updateState { s -> s.copy(bonus = s.bonus.copy(entries = emptyList())) }
                        }
                    }) { Text("清空", color = mu.danger, fontSize = 12.sp) }
                }
            }
            if (state.bonus.entries.isEmpty()) {
                Text("可从上方算分器，或下方 ${bd.others.size} 项活动与 ${bd.comps.size} 项竞赛点击加入", fontSize = 12.sp, color = mu.ink3)
            }
        }
        items(state.bonus.entries, key = { it.id }) { e ->
            MuCard(padding = 10.dp) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(e.title, fontWeight = FontWeight.Bold, fontSize = 13.5.sp, color = mu.ink)
                        Text("${e.cat} · 创新 +${e.pi} · 推免 +%.2f".format(e.tui), fontSize = 11.5.sp, color = mu.ink3)
                    }
                    IconButton(onClick = {
                        vm.updateState { s -> s.copy(bonus = s.bonus.copy(entries = s.bonus.entries.filterNot { it.id == e.id })) }
                    }) { Icon(Icons.Rounded.Close, null) }
                }
            }
        }

        // 搜索与清单过滤
        item(key = "bonus_search") {
            MuSectionHeader("活动与竞赛清单", "${filteredOthers.size + filteredComps.size} 条")
            Spacer(Modifier.height(6.dp))
            OutlinedTextField(
                value = q,
                onValueChange = { q = it },
                placeholder = { Text("搜竞赛名称、大创、论文、专利、讲座...") },
                leadingIcon = { Icon(Icons.Rounded.Search, null) },
                trailingIcon = {
                    if (q.isNotEmpty()) IconButton(onClick = { q = "" }) { Icon(Icons.Rounded.Close, null) }
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(6.dp))
            Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(selected = catFilter == "all", onClick = { catFilter = "all" }, label = { Text("全部") })
                FilterChip(selected = catFilter == "act", onClick = { catFilter = "act"; lvFilter = "ALL" }, label = { Text("活动/成果 (${bd.others.size})") })
                FilterChip(selected = catFilter == "comp", onClick = { catFilter = "comp" }, label = { Text("51号竞赛 (${bd.comps.size})") })
                if (catFilter != "act") {
                    listOf("S", "A+", "A", "B+", "B").forEach { lv ->
                        FilterChip(
                            selected = lvFilter == lv && catFilter == "comp",
                            onClick = { catFilter = "comp"; lvFilter = if (lvFilter == lv) "ALL" else lv },
                            label = { Text(lv) }
                        )
                    }
                }
            }
        }

        if (filteredOthers.isNotEmpty()) {
            items(filteredOthers, key = { "act_${it.key}" }) { o: BonusOther ->
                val isOpen = openOtherKey == o.key
                MuCard(padding = 11.dp, onClick = { openOtherKey = if (isOpen) "" else o.key }) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        MuChip(o.cat, mu.accentSoft, mu.accentInk)
                        Column(modifier = Modifier.weight(1f)) {
                            Text(o.name, fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = mu.ink)
                            Text(
                                "创新 ${if (o.pi > 0) "+${o.pi}" else "—"} · 推免 ${if (o.tui > 0) "+${o.tui}" else "—"} · 预估 ${o.h.roundToInt()}h",
                                fontSize = 11.sp,
                                color = mu.ink3
                            )
                        }
                        IconButton(onClick = {
                            val entry = BonusEntry(
                                id = "b_${UUID.randomUUID().toString().take(6)}",
                                title = o.name, pi = o.pi, tui = o.tui, ach = o.ach, cat = o.cat
                            )
                            val markDc = o.cat == "大创" && (o.key == "dcUp" || o.key == "dcCity")
                            vm.updateState { s ->
                                s.copy(bonus = s.bonus.copy(entries = s.bonus.entries + entry, hasCityDc = s.bonus.hasCityDc || markDc))
                            }
                            vm.toast("已记入「${o.name}」")
                        }) {
                            Icon(Icons.Rounded.Add, contentDescription = "加入账本", tint = mu.accentInk)
                        }
                    }
                    AnimatedVisibility(visible = isOpen) {
                        Column(modifier = Modifier.padding(top = 6.dp)) {
                            if (o.note.isNotEmpty()) Text(o.note, fontSize = 12.sp, color = mu.ink2, lineHeight = 18.sp)
                            Text("预估投入（含准备）：约 ${(o.h + o.p).roundToInt()} 小时", fontSize = 11.sp, color = mu.ink3)
                        }
                    }
                }
            }
        }

        if (filteredComps.isNotEmpty()) {
            items(filteredComps, key = { "${it.level}_${it.name}" }) { c ->
                val lvObj = bd.levels.firstOrNull { it.key == c.level.replace("+", "p") }
                val pi = lvObj?.pi?.firstOrNull() ?: 5.0
                val tui = lvObj?.tui?.firstOrNull() ?: 0.0
                val isOpen = openCompName == c.name
                MuCard(padding = 10.dp, onClick = { openCompName = if (isOpen) "" else c.name }) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        MuChip(c.level, mu.accent, Color.White)
                        Column(modifier = Modifier.weight(1f)) {
                            Text(c.name, fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = mu.ink)
                            Text("一等：创新 $pi / 推免 $tui · 点击查看/填入", fontSize = 11.sp, color = mu.ink3)
                        }
                    }
                    AnimatedVisibility(visible = isOpen) {
                        Row(modifier = Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = {
                                    calcLvlKey = c.level.replace("+", "p")
                                    showCalc = true
                                    vm.toast("已将 ${c.level} 级填入上方算分器")
                                },
                                shape = RoundedCornerShape(mu.cardRadius),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                modifier = Modifier.height(32.dp)
                            ) { Text("用它算分", fontSize = 11.5.sp) }
                            Button(
                                onClick = {
                                    val entry = BonusEntry(
                                        id = "b_${UUID.randomUUID().toString().take(6)}",
                                        title = c.name, pi = pi, tui = tui, ach = true, cat = "${c.level}级竞赛"
                                    )
                                    vm.updateState { s -> s.copy(bonus = s.bonus.copy(entries = s.bonus.entries + entry)) }
                                    vm.toast("已记入加分账本")
                                },
                                shape = RoundedCornerShape(mu.cardRadius),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                modifier = Modifier.height(32.dp)
                            ) { Text("一等直接记账", fontSize = 11.5.sp) }
                        }
                    }
                }
            }
        }

        // 加分细则、流程与避坑
        item(key = "bonus_rules") {
            MuCard(padding = 12.dp, onClick = { showRules = !showRules }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(if (showRules) "▾ 认定流程与避坑指南" else "▸ 认定流程与避坑指南", fontWeight = FontWeight.ExtraBold, color = mu.ink, modifier = Modifier.weight(1f))
                    Text("${bd.steps.size} 步 · ${bd.traps.size} 坑", fontSize = 11.5.sp, color = mu.ink3)
                }
                AnimatedVisibility(visible = showRules) {
                    Column(modifier = Modifier.padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("五档评分标准", fontWeight = FontWeight.Black, fontSize = 13.sp, color = mu.accentInk)
                        bd.grades.forEach { g ->
                            Text("• ${g.name}：${g.rule}", fontSize = 12.sp, color = mu.ink2)
                        }
                        HorizontalDivider(color = mu.line2)
                        Text("认定步骤与关键口径", fontWeight = FontWeight.Black, fontSize = 13.sp, color = mu.accentInk)
                        bd.steps.forEachIndexed { i, s ->
                            Text("${i + 1}. ${s.title}：${s.desc}（${s.src}）", fontSize = 12.sp, color = mu.ink2, lineHeight = 18.sp)
                        }
                        HorizontalDivider(color = mu.line2)
                        Text("这些情况会白干", fontWeight = FontWeight.Black, fontSize = 13.sp, color = mu.danger)
                        bd.traps.forEach { tr ->
                            Text("× $tr", fontSize = 12.sp, color = mu.ink2)
                        }
                    }
                }
            }
        }

        // 官方政策文件
        item(key = "bonus_docs") {
            MuCard(padding = 12.dp, onClick = { showDocs = !showDocs }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(if (showDocs) "▾ 官方政策文件 (${bd.docs.size} 份)" else "▸ 官方政策文件 (${bd.docs.size} 份)", fontWeight = FontWeight.ExtraBold, color = mu.ink, modifier = Modifier.weight(1f))
                }
                AnimatedVisibility(visible = showDocs) {
                    Column(modifier = Modifier.padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        bd.docs.forEach { d ->
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(mu.surface2, RoundedCornerShape(6.dp))
                                    .padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(d.title, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = mu.ink)
                                Text(listOf(d.no, d.org).filter { it.isNotEmpty() }.joinToString(" · "), fontSize = 11.sp, color = mu.accentInk)
                                if (d.keyPoint.isNotEmpty()) Text(d.keyPoint, fontSize = 11.5.sp, color = mu.ink2, lineHeight = 17.sp)
                                if (d.url.isNotEmpty()) {
                                    TextButton(
                                        onClick = { vm.openExternalUrl(d.url) },
                                        contentPadding = PaddingValues(0.dp),
                                        modifier = Modifier.height(28.dp)
                                    ) { Text("在浏览器查看原文 ↗", fontSize = 11.5.sp) }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/* ==================== 4. 作业档案 ==================== */
@Composable
fun HwArchivePane(vm: WorkbenchViewModel) {
    val mu = LocalMuColors.current
    val state by vm.state.collectAsState()
    val terms by vm.hwTerms.collectAsState()
    val cache by vm.hwCache.collectAsState()
    val indexErr by vm.hwIndexError.collectAsState()
    val loadingTermId by vm.hwLoadingTermId.collectAsState()
    val busyLabel by vm.busyLabel.collectAsState()
    val dlStates by vm.dlStates.collectAsState()
    var openTerm by remember { mutableStateOf("") }
    var openItem by remember { mutableStateOf("") }
    var openExKey by remember { mutableStateOf("") }

    val uc = state.ucloud
    val hasUcCred = uc.token.isNotBlank() || (uc.id.isNotBlank() && uc.pwd.isNotBlank())

    LaunchedEffect(hasUcCred) {
        if (hasUcCred && terms.isEmpty()) vm.loadHwIndex(false)
    }

    val totalSubmitted = terms.sumOf { t -> cache[t.id]?.items?.size ?: 0 }
    val totalGraded = terms.sumOf { t -> cache[t.id]?.items?.count { it.status == 1 } ?: 0 }
    val defaultOpenId = remember(terms) {
        (terms.firstOrNull { it.isCurrent } ?: terms.firstOrNull())?.id.orEmpty()
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(mu.paper),
        contentPadding = PaddingValues(14.dp, 10.dp, 14.dp, 96.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item(key = "hw_head") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val badgeText = if (totalSubmitted > 0) {
                    "${terms.size} 学期 · 已交 $totalSubmitted" + if (totalGraded > 0) " · 已批 $totalGraded" else ""
                } else {
                    "${terms.size} 学期"
                }
                MuSectionHeader("云邮作业档案", badgeText, modifier = Modifier.weight(1f))
                IconButton(onClick = { vm.loadHwIndex(force = true) }) {
                    Icon(Icons.Rounded.Refresh, contentDescription = "强制刷新作业档案", tint = mu.accentInk)
                }
            }
        }
        if (!hasUcCred) {
            item(key = "hw_need_login") {
                MuCard(backgroundColor = mu.surface2, onClick = { vm.openSettings() }) {
                    Text("请先在「设置 → 云邮教学空间」里登录云邮账号，再回来查看各学期已提交作业与成绩", color = mu.ink2, fontSize = 13.sp)
                }
            }
        } else if (terms.isEmpty()) {
            item(key = "hw_empty") {
                MuCard(
                    backgroundColor = mu.surface2,
                    borderColor = if (indexErr.isNotEmpty()) mu.danger else null,
                    onClick = { vm.loadHwIndex(force = true) }
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = when {
                                indexErr.isNotEmpty() -> "拉取作业档案失败：$indexErr"
                                busyLabel != null -> busyLabel!!
                                else -> "点此加载各学期作业档案..."
                            },
                            color = if (indexErr.isNotEmpty()) mu.danger else mu.ink2,
                            fontSize = 13.sp
                        )
                        if (indexErr.isNotEmpty()) {
                            OutlinedButton(onClick = { vm.loadHwIndex(force = true) }) {
                                Text("重新加载")
                            }
                        }
                    }
                }
            }
        } else {
            items(terms, key = { it.id }) { term ->
                val isOpen = openTerm == term.id || (openTerm.isEmpty() && term.id == defaultOpenId)
                val termCache = cache[term.id]
                val isLoadingThis = loadingTermId == term.id
                val gradedCnt = termCache?.items?.count { it.status == 1 } ?: 0
                MuCard(padding = 12.dp, onClick = {
                    val nextOpen = !isOpen
                    openTerm = if (nextOpen) term.id else "_none"
                    if (nextOpen && termCache == null && !isLoadingThis) vm.loadHwTerm(term.id, false)
                }) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(term.name, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, color = mu.ink, modifier = Modifier.weight(1f))
                        if (term.isCurrent) MuChip("当前", mu.accentSoft, mu.accentInk)
                        val rightTxt = when {
                            isLoadingThis -> "拉取中..."
                            termCache != null && termCache.err.isNotEmpty() -> "加载失败"
                            termCache != null -> "已交 ${termCache.items.size}" + if (gradedCnt > 0) " · 已批 $gradedCnt" else ""
                            else -> "点击拉取"
                        }
                        Text(rightTxt, fontSize = 12.sp, color = if (termCache?.err?.isNotEmpty() == true) mu.danger else mu.ink3)
                    }
                    AnimatedVisibility(visible = isOpen) {
                        Column(modifier = Modifier.padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (termCache == null || isLoadingThis) {
                                Text("正在并发拉取该学期课程作业与批改详情...", fontSize = 12.sp, color = mu.ink3)
                            } else if (termCache.err.isNotEmpty()) {
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(termCache.err, color = mu.danger, fontSize = 12.sp)
                                    OutlinedButton(onClick = { vm.loadHwTerm(term.id, force = true) }) {
                                        Text("重试该学期")
                                    }
                                }
                            } else if (termCache.items.isEmpty()) {
                                Text("这个学期暂无已提交的作业", color = mu.ink3, fontSize = 12.sp)
                            } else {
                                val grouped = termCache.items.groupBy { it.courseName.ifEmpty { "未分课程" } }
                                grouped.forEach { (courseName, courseItems) ->
                                    Text(
                                        "$courseName（${courseItems.size}）",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Black,
                                        color = mu.accentInk,
                                        modifier = Modifier.padding(top = 4.dp)
                                    )
                                    courseItems.forEach { hw: HwSubmissionItem ->
                                        val stLabel = when (hw.status) {
                                            1 -> ("已批改" + if (hw.score.isNotEmpty()) " ${hw.score}分" else "") to mu.ok
                                            2 -> "被驳回" to mu.danger
                                            99 -> "未提交" to mu.ink3
                                            else -> "待批改" to mu.warn
                                        }
                                        val expanded = openItem == hw.id
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(mu.surface2)
                                                .clickable { openItem = if (expanded) "" else hw.id }
                                                .padding(10.dp)
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(hw.title, fontWeight = FontWeight.Bold, fontSize = 13.5.sp, color = mu.ink)
                                                    val subMeta = buildString {
                                                        append("提交 ${hw.submitTime.take(16)}")
                                                        if (hw.gradeTime.isNotEmpty()) {
                                                            append(" · 批改 ${hw.gradeTime.take(16)}")
                                                            if (hw.gradeLocal) append("(本机)")
                                                        }
                                                        if (hw.isOverTime) append(" · 逾期")
                                                        if (hw.mergedCount > 1) append(" · 已合并${hw.mergedCount}份")
                                                        if (hw.chapterName.isNotEmpty()) append(" · ${hw.chapterName}")
                                                    }
                                                    Text(subMeta, fontSize = 11.sp, color = mu.ink3)
                                                }
                                                Spacer(Modifier.width(6.dp))
                                                MuChip(stLabel.first, stLabel.second, Color.White)
                                            }
                                            AnimatedVisibility(visible = expanded) {
                                                Column(modifier = Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                                    if (hw.groupScore.isNotEmpty() && (hw.groupScore.toDoubleOrNull() ?: 0.0) > 0) {
                                                        Text("小组得分：${hw.groupScore} 分", fontSize = 12.sp, color = mu.ink2)
                                                    }
                                                    if (hw.evalAvgScore.isNotEmpty() && (hw.evalAvgScore.toDoubleOrNull() ?: 0.0) > 0) {
                                                        Text("互评均分：${hw.evalAvgScore}", fontSize = 12.sp, color = mu.ink2)
                                                    }
                                                    if (hw.evalInfos.isNotEmpty()) {
                                                        Text("互评意见：${hw.evalInfos}", fontSize = 12.sp, color = mu.ink2)
                                                    }
                                                    if (hw.comment.isNotEmpty()) Text("教师评语：${hw.comment}", fontSize = 12.sp, color = mu.accentInk)
                                                    if (hw.myContent.isNotEmpty()) Text("我的提交：${hw.myContent}", fontSize = 12.sp, color = mu.ink2)
                                                    hw.myFiles.forEach { f: HwAttachment ->
                                                        val dl = dlStates[f.resourceId]
                                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                            Text(
                                                                f.name + (if (f.sizeText.isNotEmpty()) " (${f.sizeText})" else ""),
                                                                fontSize = 12.sp,
                                                                color = mu.ink,
                                                                maxLines = 1,
                                                                overflow = TextOverflow.Ellipsis,
                                                                modifier = Modifier.weight(1f)
                                                            )
                                                            OutlinedButton(
                                                                onClick = { vm.previewHwAttachment(f) },
                                                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                                                modifier = Modifier.height(28.dp)
                                                            ) { Text("预览", fontSize = 11.sp) }
                                                            OutlinedButton(
                                                                onClick = { vm.downloadHwAttachment(f) },
                                                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                                                modifier = Modifier.height(28.dp)
                                                            ) {
                                                                val label = when {
                                                                    dl?.uri != null -> "打开"
                                                                    dl?.loading == true -> "${dl.pct}%"
                                                                    else -> "下载"
                                                                }
                                                                Text(label, fontSize = 11.sp)
                                                            }
                                                        }
                                                    }
                                                    Text(
                                                        "在云邮中打开这次作业 ↗",
                                                        fontSize = 11.5.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = mu.accentInk,
                                                        modifier = Modifier.clickable {
                                                            vm.openExternalUrl(
                                                                "https://ucloud.bupt.edu.cn/uclass/course.html#/student/assignmentDetails_fullpage?activeTabName=first&assignmentId=${hw.id}"
                                                            )
                                                        }
                                                    )
                                                    if (hw.excellent.isNotEmpty()) {
                                                        Text("优秀作业 (${hw.excellent.size} 人 · 点姓名看正文)：", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = mu.accentInk)
                                                        hw.excellent.forEach { ex ->
                                                            val exK = "${hw.id}_${ex.audiencesId}_${ex.teamId}"
                                                            val exOpen = openExKey == exK
                                                            Column(
                                                                modifier = Modifier
                                                                    .fillMaxWidth()
                                                                    .background(mu.surface1, RoundedCornerShape(6.dp))
                                                                    .clickable {
                                                                        openExKey = if (exOpen) "" else exK
                                                                        if (!exOpen) vm.loadHwExcellentDetail(term.id, hw.id, ex)
                                                                    }
                                                                    .padding(8.dp)
                                                            ) {
                                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                                    Text("${ex.realName} (${ex.className})", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = mu.ink, modifier = Modifier.weight(1f))
                                                                    Text("${ex.score} 分", fontWeight = FontWeight.Black, fontSize = 12.sp, color = mu.ok)
                                                                }
                                                                AnimatedVisibility(visible = exOpen) {
                                                                    Column(modifier = Modifier.padding(top = 6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                                                        if (ex.content.isNotEmpty()) Text(ex.content, fontSize = 11.5.sp, color = mu.ink2)
                                                                        else Text("正在加载或无文字内容...", fontSize = 11.sp, color = mu.ink3)
                                                                        if (ex.comment.isNotEmpty()) Text("评语：${ex.comment}", fontSize = 11.sp, color = mu.accentInk)
                                                                        ex.files.forEach { ef ->
                                                                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                                                                Text(ef.name, fontSize = 11.sp, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                                                                TextButton(onClick = { vm.previewHwAttachment(ef) }, contentPadding = PaddingValues(4.dp), modifier = Modifier.height(26.dp)) { Text("预览", fontSize = 11.sp) }
                                                                                TextButton(onClick = { vm.downloadHwAttachment(ef) }, contentPadding = PaddingValues(4.dp), modifier = Modifier.height(26.dp)) { Text("下载", fontSize = 11.sp) }
                                                                            }
                                                                        }
                                                                    }
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/* ==================== 5. 综测自评 ==================== */
@Composable
fun ZzPane(vm: WorkbenchViewModel) {
    val mu = LocalMuColors.current
    val state by vm.state.collectAsState()
    val zz = state.zz
    val zd = vm.zzData
    val catalog = vm.courseCatalog

    var courseQ by remember { mutableStateOf("") }
    var termFilter by remember { mutableIntStateOf(0) } // 0=all, 1..8
    var modeFilter by remember { mutableStateOf("") } // "" / "empty" / "filled"
    var showDeductFold by remember { mutableStateOf(false) }
    var showRulesFold by remember { mutableStateOf(false) }
    var ruleQ by remember { mutableStateOf("") }

    val courses = remember(zz.major) {
        catalog.byMajor[zz.major].orEmpty().filter { it.requiredType == "必修" || (it.module == "专业教育课程" && it.requiredType == "选修") }
    }
    val visibleCourses = remember(courses, courseQ, termFilter, modeFilter, zz.courseScores) {
        courses.filter { c ->
            if (termFilter > 0 && c.minTerm != termFilter) return@filter false
            val sc = zz.courseScores[c.code].orEmpty()
            if (modeFilter == "empty" && sc.isNotBlank()) return@filter false
            if (modeFilter == "filled" && sc.isBlank()) return@filter false
            if (courseQ.isNotBlank() && !c.name.contains(courseQ, true) && !c.code.contains(courseQ, true)) return@filter false
            true
        }
    }

    val self = zz.selfScore.toDoubleOrNull() ?: 0.0
    val peer = zz.peerScore.toDoubleOrNull() ?: 0.0
    val counsel = zz.counselorScore.toDoubleOrNull() ?: 0.0
    val deductSum = zz.deduct.values.sumOf { it.toDoubleOrNull() ?: 0.0 }
    val basicScore = ((self * 0.1 + peer * 0.7 + counsel * 0.2) - deductSum).coerceIn(0.0, 100.0)

    var wSum = 0.0
    var cSum = 0.0
    courses.forEach { c ->
        val sc = zz.courseScores[c.code]?.toDoubleOrNull()
        if (sc != null && c.credits > 0) { wSum += sc.coerceIn(0.0, 100.0) * c.credits; cSum += c.credits }
    }
    zz.customCourses.forEach { cc ->
        val sc = cc.score.toDoubleOrNull()
        val cr = cc.credit.toDoubleOrNull()
        if (sc != null && cr != null && cr > 0) { wSum += sc.coerceIn(0.0, 100.0) * cr; cSum += cr }
    }
    val majorScore = if (cSum > 0) wSum / cSum else 0.0

    val healthScore = ((zz.physicalTest.toDoubleOrNull() ?: 0.0).coerceIn(0.0, 100.0) * 0.2 +
        (zz.exerciseScore.toDoubleOrNull() ?: 0.0).coerceIn(0.0, 100.0) * 0.1 +
        (zz.activityEval.toDoubleOrNull() ?: 0.0).coerceIn(0.0, 10.0)).coerceAtMost(40.0)
    val otherDev = zd.devItems.sumOf { item ->
        (zz.devScores[item.key]?.toDoubleOrNull() ?: 0.0).coerceIn(0.0, item.maxPoint)
    }.coerceAtMost(60.0)
    val devScore = (healthScore + otherDev).coerceAtMost(100.0)
    val total = basicScore * 0.1 + majorScore * 0.7 + devScore * 0.2

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(mu.paper),
        contentPadding = PaddingValues(14.dp, 10.dp, 14.dp, 96.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // 总分看板
        item(key = "zz_hero") {
            MuCard(padding = 16.dp) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("学年综合素质评价总分", fontSize = 12.sp, color = mu.ink3, fontWeight = FontWeight.Bold)
                        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                "%.2f".format(total),
                                fontSize = 32.sp,
                                fontWeight = FontWeight.Black,
                                fontStyle = if (mu.style != ThemeStyle.IOS) FontStyle.Italic else FontStyle.Normal,
                                color = mu.accentInk
                            )
                            Text(
                                "分",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Black,
                                fontStyle = FontStyle.Normal,
                                color = mu.accentInk,
                                modifier = Modifier.padding(bottom = 4.dp)
                            )
                        }
                    }
                    MuChip(if (total >= 60.0) "及格" else "未及格 (<60)", if (total >= 60.0) mu.ok else mu.danger, Color.White)
                }
                Spacer(Modifier.height(8.dp))
                Text("基本 %.1f (×10%%) · 专业 %.2f (×70%%) · 发展 %.1f (×20%%)".format(basicScore, majorScore, devScore), fontSize = 12.sp, color = mu.ink2)
            }
        }

        // 基本素质 + 违纪扣分折叠
        item(key = "zz_basic") {
            MuCard(padding = 12.dp) {
                Text("基本素质（自评 10% + 互评 70% + 辅导员 20%）", fontWeight = FontWeight.Bold, color = mu.ink)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedTextField(zz.selfScore, { v -> vm.updateState { s -> s.copy(zz = s.zz.copy(selfScore = v)) } }, label = { Text("自评") }, singleLine = true, modifier = Modifier.weight(1f))
                    OutlinedTextField(zz.peerScore, { v -> vm.updateState { s -> s.copy(zz = s.zz.copy(peerScore = v)) } }, label = { Text("互评") }, singleLine = true, modifier = Modifier.weight(1f))
                    OutlinedTextField(zz.counselorScore, { v -> vm.updateState { s -> s.copy(zz = s.zz.copy(counselorScore = v)) } }, label = { Text("辅导员") }, singleLine = true, modifier = Modifier.weight(1f))
                }
                Spacer(Modifier.height(6.dp))
                TextButton(onClick = { showDeductFold = !showDeductFold }, contentPadding = PaddingValues(0.dp)) {
                    Text(if (showDeductFold) "▾ 扣分项（已扣 ${deductSum.toInt()} 分）" else "▸ 展开扣分项（处分/通报批评 · 当前 -${deductSum.toInt()}）", fontSize = 12.sp, color = mu.danger)
                }
                AnimatedVisibility(visible = showDeductFold) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        zd.deductItems.forEach { d ->
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(d.label, fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = mu.ink)
                                    Text("参考 ${d.minPoint.toInt()}-${d.maxPoint.toInt()} 分/次", fontSize = 10.5.sp, color = mu.ink3)
                                }
                                OutlinedTextField(
                                    value = zz.deduct[d.key].orEmpty(),
                                    onValueChange = { v -> vm.updateState { s -> s.copy(zz = s.zz.copy(deduct = s.zz.deduct + (d.key to v))) } },
                                    placeholder = { Text("0") },
                                    singleLine = true,
                                    modifier = Modifier.width(86.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // 专业素质（含专业选择、筛选、手补删除、清空）
        item(key = "zz_major_ctl") {
            MuSectionHeader("专业素质（学分加权）", "${visibleCourses.size} 门")
            Spacer(Modifier.height(6.dp))
            Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                catalog.majors.forEach { m ->
                    FilterChip(
                        selected = zz.major == m.key,
                        onClick = { vm.updateState { s -> s.copy(zz = s.zz.copy(major = m.key)) } },
                        label = { Text(m.shortName) }
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            OutlinedTextField(
                value = courseQ,
                onValueChange = { courseQ = it },
                placeholder = { Text("搜索课程名或编码...") },
                leadingIcon = { Icon(Icons.Rounded.Search, null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(6.dp))
            Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(selected = modeFilter == "", onClick = { modeFilter = "" }, label = { Text("全部") })
                FilterChip(selected = modeFilter == "empty", onClick = { modeFilter = "empty" }, label = { Text("未填") })
                FilterChip(selected = modeFilter == "filled", onClick = { modeFilter = "filled" }, label = { Text("已填") })
                (1..8).forEach { t ->
                    FilterChip(selected = termFilter == t, onClick = { termFilter = if (termFilter == t) 0 else t }, label = { Text("S$t") })
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = {
                    val item = ZzCustomCourse(UUID.randomUUID().toString().take(6), "体育 / 补录课", "1", "90")
                    vm.updateState { s -> s.copy(zz = s.zz.copy(customCourses = s.zz.customCourses + item)) }
                }) { Text("+ 手动补课") }
                TextButton(onClick = {
                    vm.askConfirm("清空专业成绩", "只清空已填的课程成绩与手补成绩，保留基本/发展素质，确定吗？") {
                        vm.updateState { s -> s.copy(zz = s.zz.copy(courseScores = emptyMap(), customCourses = s.zz.customCourses.map { it.copy(score = "") })) }
                    }
                }) { Text("清空成绩", color = mu.danger) }
            }
        }

        if (zz.customCourses.isNotEmpty()) {
            items(zz.customCourses, key = { "zz_cust_${it.id}" }) { cc ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedTextField(
                        value = cc.name,
                        onValueChange = { nv -> vm.updateState { s -> s.copy(zz = s.zz.copy(customCourses = s.zz.customCourses.map { if (it.id == cc.id) it.copy(name = nv) else it })) } },
                        label = { Text("课名") },
                        singleLine = true,
                        modifier = Modifier.weight(1.4f)
                    )
                    OutlinedTextField(
                        value = cc.credit,
                        onValueChange = { cv -> vm.updateState { s -> s.copy(zz = s.zz.copy(customCourses = s.zz.customCourses.map { if (it.id == cc.id) it.copy(credit = cv) else it })) } },
                        label = { Text("学分") },
                        singleLine = true,
                        modifier = Modifier.weight(0.7f)
                    )
                    OutlinedTextField(
                        value = cc.score,
                        onValueChange = { sv -> vm.updateState { s -> s.copy(zz = s.zz.copy(customCourses = s.zz.customCourses.map { if (it.id == cc.id) it.copy(score = sv) else it })) } },
                        label = { Text("成绩") },
                        singleLine = true,
                        modifier = Modifier.weight(0.8f)
                    )
                    IconButton(onClick = {
                        vm.updateState { s -> s.copy(zz = s.zz.copy(customCourses = s.zz.customCourses.filterNot { it.id == cc.id })) }
                    }) { Icon(Icons.Rounded.DeleteOutline, null, tint = mu.danger) }
                }
            }
        }

        items(visibleCourses, key = { "zzc_${it.code}_${it.name}" }) { c ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("${c.name} (${c.credits}分)", fontSize = 13.sp, color = mu.ink, fontWeight = FontWeight.SemiBold)
                    Text("第 ${c.termSpec} 学期 · ${c.requiredType}", fontSize = 11.sp, color = mu.ink3)
                }
                OutlinedTextField(
                    value = zz.courseScores[c.code].orEmpty(),
                    onValueChange = { v -> vm.updateState { s -> s.copy(zz = s.zz.copy(courseScores = s.zz.courseScores + (c.code to v))) } },
                    placeholder = { Text("—") },
                    singleLine = true,
                    modifier = Modifier.width(86.dp)
                )
            }
        }

        // 发展素质
        item(key = "zz_dev") {
            MuCard(padding = 12.dp) {
                Text("发展素质（体测×20% + 锻炼×10% + 活动10 + 其他60）", fontWeight = FontWeight.Bold, color = mu.ink)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedTextField(zz.physicalTest, { v -> vm.updateState { s -> s.copy(zz = s.zz.copy(physicalTest = v)) } }, label = { Text("体测") }, singleLine = true, modifier = Modifier.weight(1f))
                    OutlinedTextField(zz.exerciseScore, { v -> vm.updateState { s -> s.copy(zz = s.zz.copy(exerciseScore = v)) } }, label = { Text("锻炼") }, singleLine = true, modifier = Modifier.weight(1f))
                    OutlinedTextField(zz.activityEval, { v -> vm.updateState { s -> s.copy(zz = s.zz.copy(activityEval = v)) } }, label = { Text("活动") }, singleLine = true, modifier = Modifier.weight(1f))
                }
                Spacer(Modifier.height(8.dp))
                zd.devItems.forEach { d ->
                    OutlinedTextField(
                        value = zz.devScores[d.key].orEmpty(),
                        onValueChange = { v ->
                            vm.updateState { s -> s.copy(zz = s.zz.copy(devScores = s.zz.devScores + (d.key to v))) }
                        },
                        label = { Text("${d.label}（上限 ${d.maxPoint.roundToInt()}）") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)
                    )
                }
            }
        }

        // 官方办法原文查阅与搜索
        item(key = "zz_doc") {
            MuCard(padding = 12.dp) {
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { showRulesFold = !showRulesFold },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(if (showRulesFold) "▾ 《${zd.metaTitle}》原文" else "▸ 查阅《${zd.metaTitle}》原文", fontWeight = FontWeight.ExtraBold, color = mu.ink, modifier = Modifier.weight(1f))
                    Text("${zd.chapters.size} 章", fontSize = 11.5.sp, color = mu.ink3)
                }
                AnimatedVisibility(visible = showRulesFold) {
                    Column(modifier = Modifier.padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = ruleQ,
                            onValueChange = { ruleQ = it },
                            placeholder = { Text("搜原文：扣分 / 加权 / 公示 / 第二课堂...") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        if (zd.principles.isNotEmpty() && ruleQ.isBlank()) {
                            Text("测评原则", fontWeight = FontWeight.Black, fontSize = 13.sp, color = mu.accentInk)
                            zd.principles.forEachIndexed { idx, p ->
                                Text("${idx + 1}. $p", fontSize = 12.sp, color = mu.ink2)
                            }
                            HorizontalDivider(color = mu.line2)
                        }
                        zd.chapters.forEach { ch ->
                            val matchingSecs = ch.sections.filter {
                                ruleQ.isBlank() || it.body.contains(ruleQ, true) || it.no.contains(ruleQ, true)
                            }
                            if (matchingSecs.isNotEmpty()) {
                                Text("${ch.no} ${ch.title}", fontWeight = FontWeight.Black, fontSize = 13.5.sp, color = mu.accentInk)
                                matchingSecs.forEach { sec ->
                                    Text("${sec.no} ${sec.body}", fontSize = 12.sp, color = mu.ink2, lineHeight = 18.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
