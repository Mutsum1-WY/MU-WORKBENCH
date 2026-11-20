package com.mu.workbench.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mu.workbench.data.ThemeStyle
import com.mu.workbench.domain.ScheduleEngine
import com.mu.workbench.domain.TodoEngine
import com.mu.workbench.ui.WorkbenchViewModel
import com.mu.workbench.ui.components.MuCard
import com.mu.workbench.ui.components.MuChip
import com.mu.workbench.ui.components.MuSectionHeader
import com.mu.workbench.ui.components.TodoCardRow
import com.mu.workbench.ui.components.parallelogramShape
import com.mu.workbench.ui.theme.LocalMuColors
import com.mu.workbench.ui.theme.MuMotion
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale

@Composable
fun TodayScreen(vm: WorkbenchViewModel) {
    val mu = LocalMuColors.current
    val state by vm.state.collectAsState()
    val nowTime by vm.nowMinute.collectAsState()
    val today by vm.nowDate.collectAsState()
    val bannerDismissed by vm.bannerDismissed.collectAsState()
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    val todayKey = TodoEngine.formatDate(today)
    val todayHoliday = TodoEngine.holidayOf(todayKey, state, vm.builtinHolidays)
    val todayTodos = remember(state.todos, state.holidayManual, state.holidayCache, todayKey) {
        TodoEngine.todayItems(state, vm.builtinHolidays, today)
    }
    val doneCount = todayTodos.count { it.isDone }
    val totalCount = todayTodos.size
    val pendingCount = totalCount - doneCount
    val pct = if (totalCount == 0) 0f else doneCount.toFloat() / totalCount.toFloat()
    val animPct by animateFloatAsState(targetValue = pct, animationSpec = MuMotion.floatSpring(mu.style), label = "heroPct")

    val tt = vm.activeTimetable()
    val wkStatus = remember(tt, today) { ScheduleEngine.weekStatus(tt, today) }
    val curWeek = wkStatus.week.coerceIn(1, tt.totalWeeks.coerceAtLeast(1))
    val weekdayNum = today.dayOfWeek.value // 1=Mon..7=Sun
    val todayClasses = remember(tt, wkStatus, weekdayNum, nowTime.hour, nowTime.minute) {
        if (wkStatus.inRange) ScheduleEngine.classesForDay(tt, wkStatus.week, weekdayNum, nowTime) else emptyList()
    }
    val nextHint = remember(tt, today, nowTime.hour, nowTime.minute) {
        if (todayClasses.isEmpty()) ScheduleEngine.nextCourseWithin14Days(tt, today, nowTime) else null
    }
    var showWeekKb by remember { mutableStateOf(false) }
    var inspectWeek by remember(curWeek) { mutableStateOf(curWeek) }
    var expandedUcloudId by remember { mutableStateOf("") }

    val pomoCount = if (state.todayPomoDate == todayKey) state.todayPomos else 0
    val weekStr = today.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.CHINA)
    val dayStatusSuffix = when {
        todayHoliday?.off == true -> " · ${todayHoliday.name.ifEmpty { "放假" }}"
        todayHoliday?.off == false -> " · 调休上班"
        else -> ""
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 顶部待办提醒横幅
        if (!bannerDismissed && pendingCount > 0) {
            item(key = "banner") {
                TodayAlertBanner(
                    pendingCount = pendingCount,
                    onGo = { scope.launch { listState.animateScrollToItem(4) } },
                    onDismiss = { vm.dismissBanner() }
                )
            }
        }

        // Hero 主卡（1:1 还原 P5R / P3R / iOS 三种风格）
        item(key = "hero") {
            TodayHeroCard(
                monthDay = "${today.monthValue}月${today.dayOfMonth}日",
                subLine = "$weekStr · ${today.year} 年$dayStatusSuffix",
                doneCount = doneCount,
                totalCount = totalCount,
                animPct = animPct,
                pomoCount = pomoCount,
                onNewTodo = { vm.openNewTodo(todayKey) }
            )
        }

        // 今日课程卡
        item(key = "kb_header") {
            val kbBadge = if (wkStatus.inRange) {
                val wdLabel = ScheduleEngine.WD_NAMES.getOrElse(weekdayNum - 1) { "" }
                "第 ${wkStatus.week} 周 · $wdLabel · ${if (todayClasses.isEmpty()) "无课" else "${todayClasses.size} 门"}"
            } else {
                "学期外"
            }
            MuSectionHeader("今日课程", kbBadge)
        }

        if (!wkStatus.inRange) {
            item(key = "kb_out") {
                MuCard(backgroundColor = mu.surface2, padding = 14.dp) {
                    Text("课表不在本学期", fontWeight = FontWeight.Black, fontSize = 14.sp, color = mu.ink)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "当前快照为「${tt.semesterLabel.ifEmpty { tt.semester }}」（${tt.fetched} 抓取），" +
                            if (wkStatus.daysBeforeStart > 0) "距离第 1 周周一还有 ${wkStatus.daysBeforeStart} 天。"
                            else "可在「设置 → 教务系统 · 在线课表」抓取最新课表。",
                        fontSize = 12.sp,
                        color = mu.ink3,
                        lineHeight = 18.sp
                    )
                }
            }
        } else {
            if (todayHoliday?.off == true) {
                item(key = "kb_holiday_warn") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(mu.cardRadius))
                            .background(mu.warn.copy(alpha = 0.14f))
                            .border(1.dp, mu.warn.copy(alpha = 0.45f), RoundedCornerShape(mu.cardRadius))
                            .padding(horizontal = 12.dp, vertical = 9.dp)
                    ) {
                        Text(
                            "今天是法定节假日（${todayHoliday.name.ifEmpty { "放假" }}）：课表按教学周排，节假日通常停课或调休，请以学校教务通知为准。",
                            color = mu.warn,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            if (todayClasses.isEmpty()) {
                item(key = "kb_empty") {
                    MuCard(backgroundColor = mu.surface1, padding = 14.dp) {
                        Text("今天没课 🎉", color = mu.ink, fontWeight = FontWeight.Black, fontSize = 14.5.sp)
                        Spacer(Modifier.height(4.dp))
                        val nextText = if (nextHint != null) {
                            val wdTxt = ScheduleEngine.WD_NAMES.getOrElse(nextHint.weekday - 1) { "" }
                            val roomTxt = if (nextHint.room.isNotEmpty()) "（${nextHint.room}）" else ""
                            "下一门：$wdTxt ${nextHint.startTime} ${nextHint.courseName}$roomTxt"
                        } else {
                            "接下来两周都没有排课"
                        }
                        Text(nextText, color = mu.ink3, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            } else {
                itemsIndexed(todayClasses, key = { idx, it -> "kb_${idx}_${it.item.name}_${it.item.start}" }) { _, cls ->
                    val isLive = cls.status == ScheduleEngine.ClassStatus.LIVE
                    val isPast = cls.status == ScheduleEngine.ClassStatus.PAST
                    MuCard(
                        borderColor = if (isLive) mu.accent else null,
                        backgroundColor = if (isLive) mu.accentSoft else if (isPast) mu.paper2 else mu.surface1,
                        padding = 12.dp
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(horizontalAlignment = Alignment.End, modifier = Modifier.width(48.dp)) {
                                Text(
                                    cls.startTime,
                                    fontWeight = FontWeight.Black,
                                    fontStyle = if (mu.style != ThemeStyle.IOS) FontStyle.Italic else FontStyle.Normal,
                                    style = androidx.compose.ui.text.TextStyle(fontFeatureSettings = "tnum"),
                                    fontSize = 14.sp,
                                    color = if (isLive) mu.accentInk else if (isPast) mu.ink3 else mu.ink
                                )
                                Text(cls.endTime, fontSize = 10.5.sp, color = mu.ink3)
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(
                                        cls.item.name,
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 14.5.sp,
                                        color = if (isPast) mu.ink3 else mu.ink,
                                        modifier = Modifier.weight(1f, fill = false)
                                    )
                                    if (cls.item.practice) MuChip("实践", if (mu.style == ThemeStyle.P5R) mu.ink else mu.accentSoft, if (mu.style == ThemeStyle.P5R) mu.paper else mu.accentInk)
                                }
                                Spacer(Modifier.height(3.dp))
                                Text(
                                    "第 ${cls.item.start}-${cls.item.end} 节 · ${cls.room.ifEmpty { "地点待定" }}" +
                                        if (cls.teacherShort.isNotEmpty()) " · ${cls.teacherShort}" else "",
                                    fontSize = 12.sp,
                                    color = mu.ink2
                                )
                                Spacer(Modifier.height(4.dp))
                                if (isLive) {
                                    val progAnim by animateFloatAsState(cls.remainingProgress, label = "clsProg")
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(6.dp)
                                                .clip(RoundedCornerShape(if (mu.style == ThemeStyle.IOS) 999.dp else 0.dp))
                                                .background(mu.surface3)
                                                .border(
                                                    if (mu.style == ThemeStyle.P5R) 1.dp else 0.dp,
                                                    if (mu.style == ThemeStyle.P5R) mu.ink else Color.Transparent
                                                )
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth(progAnim)
                                                    .fillMaxHeight()
                                                    .background(mu.accent)
                                            )
                                        }
                                        Text(cls.hintText, fontSize = 11.5.sp, fontWeight = FontWeight.Black, color = mu.accentInk)
                                    }
                                } else {
                                    Text(
                                        cls.hintText,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isPast) mu.ink3 else mu.accentInk
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // 整周课表折叠（带上一周/下一周/回本周切换器 + 学期元信息）
        item(key = "kb_week_toggle") {
            val weekCourseCount = remember(tt, inspectWeek) {
                (1..7).sumOf { d -> ScheduleEngine.classesForDay(tt, inspectWeek, d, nowTime).size }
            }
            MuCard(padding = 12.dp, showCornerAccent = false) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showWeekKb = !showWeekKb },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (showWeekKb) "▾ 整周课表" else "▸ 展开整周课表",
                        fontWeight = FontWeight.ExtraBold,
                        color = mu.ink,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = "第 $inspectWeek 周 · 共 $weekCourseCount 门",
                        fontSize = 11.5.sp,
                        color = mu.accentInk,
                        fontWeight = FontWeight.Bold
                    )
                }
                AnimatedVisibility(visible = showWeekKb) {
                    Column(modifier = Modifier.padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        // 周次翻页器
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            OutlinedButton(
                                onClick = { if (inspectWeek > 1) inspectWeek-- },
                                enabled = inspectWeek > 1,
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                shape = RoundedCornerShape(mu.cardRadius),
                                modifier = Modifier.height(32.dp)
                            ) {
                                Icon(Icons.Rounded.ChevronLeft, null, modifier = Modifier.size(16.dp))
                                Text("上一周", fontSize = 11.5.sp)
                            }
                            TextButton(
                                onClick = { inspectWeek = curWeek },
                                modifier = Modifier.weight(1f).height(32.dp)
                            ) {
                                Text(
                                    if (inspectWeek == curWeek) "第 $inspectWeek / ${tt.totalWeeks} 周（本周）"
                                    else "第 $inspectWeek / ${tt.totalWeeks} 周 · 回本周",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Black,
                                    color = if (inspectWeek == curWeek) mu.ink else mu.accentInk
                                )
                            }
                            OutlinedButton(
                                onClick = { if (inspectWeek < tt.totalWeeks) inspectWeek++ },
                                enabled = inspectWeek < tt.totalWeeks,
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                shape = RoundedCornerShape(mu.cardRadius),
                                modifier = Modifier.height(32.dp)
                            ) {
                                Text("下一周", fontSize = 11.5.sp)
                                Icon(Icons.Rounded.ChevronRight, null, modifier = Modifier.size(16.dp))
                            }
                        }

                        HorizontalDivider(color = mu.line2)

                        for (d in 1..7) {
                            val dayCls = ScheduleEngine.classesForDay(tt, inspectWeek, d, nowTime)
                            val dn = DayOfWeek.of(d).getDisplayName(TextStyle.SHORT, Locale.CHINA)
                            val isTodayDay = inspectWeek == curWeek && d == weekdayNum
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = dn,
                                    fontWeight = FontWeight.Black,
                                    fontStyle = FontStyle.Normal,
                                    fontSize = 12.sp,
                                    color = if (isTodayDay) mu.accentInk else mu.ink2,
                                    modifier = Modifier.width(38.dp)
                                )
                                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                    if (dayCls.isEmpty()) {
                                        Text("无课", fontSize = 12.sp, color = mu.ink3)
                                    } else {
                                        dayCls.forEach { c ->
                                            Text(
                                                "${c.startTime}-${c.endTime}  ${c.item.name}" +
                                                    (if (c.room.isNotEmpty()) " · ${c.room}" else ""),
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Medium,
                                                color = mu.ink
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        HorizontalDivider(color = mu.line2)
                        Text(
                            text = "${tt.semesterLabel.ifEmpty { tt.semester }} · 第 $curWeek / ${tt.totalWeeks} 周 · ${tt.source.ifEmpty { "内置快照" }}（${tt.fetched} 抓取，调课以教务通知为准）",
                            fontSize = 10.5.sp,
                            color = mu.ink3,
                            lineHeight = 16.sp
                        )
                    }
                }
            }
        }

        // 云邮待办卡片（始终根据开关展示，未配账号时给快捷入口，展开查详情）
        if (state.ucloud.on) {
            val ucItems = state.ucloud.items
            val ucPending = ucItems.count { it.id !in state.ucloud.done }
            item(key = "uc_header") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    MuSectionHeader(
                        "云邮待办",
                        if (ucItems.isNotEmpty()) "$ucPending / ${ucItems.size} 未处理" else "未拉取",
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = { vm.syncUcloudOnly() }) {
                        Icon(Icons.Rounded.Refresh, contentDescription = "刷新云邮", tint = mu.accentInk)
                    }
                }
            }
            if (state.ucloud.id.isBlank() || state.ucloud.pwd.isBlank()) {
                item(key = "uc_need_login") {
                    MuCard(backgroundColor = mu.surface2, onClick = { vm.openSettings() }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Settings, null, tint = mu.accentInk, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "去设置填写云邮学工号与密码，即可自动同步作业待办",
                                color = mu.ink2,
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            } else if (ucItems.isEmpty()) {
                item(key = "uc_empty") {
                    MuCard(backgroundColor = mu.surface2, onClick = { vm.syncUcloudOnly() }) {
                        Text(
                            if (state.ucloud.lastError.isNotEmpty()) "同步失败：${state.ucloud.lastError}（点此重试）"
                            else if (state.ucloud.lastSync > 0L) "云邮里没有未完成的作业了 🎉"
                            else "点此同步云邮未完成待办",
                            color = if (state.ucloud.lastError.isNotEmpty()) mu.danger else mu.ink2,
                            fontSize = 13.sp
                        )
                    }
                }
            } else {
                itemsIndexed(ucItems, key = { _, u -> "uc_${u.id}" }) { _, u ->
                    val uDone = u.id in state.ucloud.done
                    val isOpen = expandedUcloudId == u.id
                    val detail = state.ucloud.details[u.id]
                    val typeLabel = when (u.type) { 1 -> "作业"; 2 -> "测验"; else -> "待办" }
                    val dueProgress = remember(u.endTime, detail?.beginTime, today, nowTime.hour, nowTime.minute) {
                        computeUcloudUrgency(u.endTime, detail?.beginTime.orEmpty(), LocalDateTime.of(today, nowTime))
                    }
                    val ucProgAnimState = animateFloatAsState(
                        targetValue = if (uDone) 0f else dueProgress.second,
                        animationSpec = MuMotion.floatSpring(mu.style),
                        label = "ucProg"
                    )
                    val showProg = !uDone && u.endTime.isNotEmpty()
                    val sheenPhaseState = if (showProg) {
                        val sheenTransition = rememberInfiniteTransition(label = "ucSheen")
                        sheenTransition.animateFloat(
                            initialValue = -0.35f,
                            targetValue = 1.35f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(durationMillis = 2600, easing = LinearEasing),
                                repeatMode = RepeatMode.Restart
                            ),
                            label = "ucSheenPhase"
                        )
                    } else {
                        remember { mutableFloatStateOf(-0.35f) }
                    }

                    MuCard(
                        backgroundColor = if (uDone) mu.paper2 else mu.surface1,
                        borderColor = if (isOpen) mu.accent else null,
                        padding = 12.dp,
                        behindContent = if (showProg) {
                            {
                                if (size.width > 1f && size.height > 1f) {
                                    val fillW = (size.width * ucProgAnimState.value).coerceIn(0f, size.width)
                                    if (fillW > 1f) {
                                        drawRect(
                                            brush = Brush.horizontalGradient(
                                                colors = listOf(
                                                    dueProgress.third.copy(alpha = 0.15f),
                                                    dueProgress.third.copy(alpha = 0.05f)
                                                ),
                                                startX = 0f,
                                                endX = fillW
                                            ),
                                            size = androidx.compose.ui.geometry.Size(fillW, size.height)
                                        )
                                        val edgeW = 2.dp.toPx().coerceAtMost(fillW)
                                        drawRect(
                                            color = dueProgress.third.copy(alpha = 0.45f),
                                            topLeft = Offset((fillW - edgeW).coerceAtLeast(0f), 0f),
                                            size = androidx.compose.ui.geometry.Size(edgeW, size.height)
                                        )
                                    }
                                    val bandW = (size.width * 0.36f).coerceAtLeast(1f)
                                    val cx = sheenPhaseState.value * size.width
                                    drawRect(
                                        brush = Brush.horizontalGradient(
                                            colors = listOf(
                                                Color.Transparent,
                                                dueProgress.third.copy(alpha = 0.13f),
                                                Color.White.copy(alpha = 0.10f),
                                                Color.Transparent
                                            ),
                                            startX = cx - bandW,
                                            endX = cx + bandW
                                        ),
                                        size = size
                                    )
                                }
                            }
                        } else null,
                        onClick = {
                            expandedUcloudId = if (isOpen) "" else u.id
                            if (!isOpen) vm.fetchUcloudTodoDetail(u.id)
                        }
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(20.dp)
                                    .clip(if (mu.style == ThemeStyle.IOS) CircleShape else RoundedCornerShape(0.dp))
                                    .background(if (uDone) mu.accent else Color.Transparent)
                                    .border(1.5.dp, if (uDone) mu.accent else mu.ink3, if (mu.style == ThemeStyle.IOS) CircleShape else RoundedCornerShape(0.dp))
                                    .clickable { vm.toggleUcloudDone(u.id) },
                                contentAlignment = Alignment.Center
                            ) {
                                if (uDone) Icon(Icons.Rounded.Check, null, tint = Color.White, modifier = Modifier.size(13.dp))
                            }
                            Spacer(Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    MuChip(typeLabel, mu.accentSoft, mu.accentInk)
                                    Text(
                                        u.title,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = if (uDone) mu.ink3 else mu.ink,
                                        textDecoration = if (uDone) TextDecoration.LineThrough else TextDecoration.None,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                                Spacer(Modifier.height(4.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                    if (u.siteName.isNotEmpty()) MuChip(u.siteName, mu.surface2, mu.ink2)
                                    if (u.endTime.isNotEmpty()) {
                                        Text(
                                            dueProgress.first,
                                            fontSize = 11.5.sp,
                                            color = dueProgress.third,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                        if (u.endTime.isNotEmpty()) {
                            Spacer(Modifier.height(8.dp))
                            val barShape = RoundedCornerShape(if (mu.style == ThemeStyle.P5R) 0.dp else 999.dp)
                            val borderColor = if (mu.style == ThemeStyle.P5R) mu.ink else dueProgress.third.copy(alpha = 0.35f)
                            val borderWidth = if (mu.style == ThemeStyle.P5R) 1.dp else 0.75.dp
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(9.dp)
                                    .clip(barShape)
                                    .background(dueProgress.third.copy(alpha = 0.14f))
                                    .drawBehind {
                                        val fillW = (size.width * ucProgAnimState.value).coerceIn(0f, size.width)
                                        if (fillW > 1f && size.height > 0f) {
                                            drawRect(
                                                brush = Brush.horizontalGradient(
                                                    colors = listOf(dueProgress.third, dueProgress.third.copy(alpha = 0.78f)),
                                                    startX = 0f,
                                                    endX = fillW
                                                ),
                                                size = androidx.compose.ui.geometry.Size(fillW, size.height)
                                            )
                                            val bw = (fillW * 0.45f).coerceAtLeast(1f)
                                            val bx = sheenPhaseState.value * fillW
                                            drawRect(
                                                brush = Brush.horizontalGradient(
                                                    colors = listOf(
                                                        Color.Transparent,
                                                        Color.White.copy(alpha = 0.42f),
                                                        Color.Transparent
                                                    ),
                                                    startX = bx - bw,
                                                    endX = bx + bw
                                                ),
                                                size = androidx.compose.ui.geometry.Size(fillW, size.height)
                                            )
                                        }
                                    }
                                    .border(
                                        width = borderWidth,
                                        color = borderColor,
                                        shape = barShape
                                    )
                            )
                        }

                        AnimatedVisibility(visible = isOpen) {
                            Column(modifier = Modifier.padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                HorizontalDivider(color = mu.line2)
                                if (detail == null) {
                                    Text("正在读取作业正文与发布时间...", fontSize = 12.sp, color = mu.ink3)
                                } else if (detail.error.isNotEmpty()) {
                                    Text("详情加载失败：${detail.error}", fontSize = 12.sp, color = mu.danger)
                                } else {
                                    if (detail.content.isNotEmpty()) {
                                        Text(detail.content, fontSize = 12.5.sp, color = mu.ink2, lineHeight = 19.sp)
                                    } else {
                                        Text("该条暂无文字说明", fontSize = 12.sp, color = mu.ink3)
                                    }
                                    val metaBits = buildList {
                                        if (detail.fullMark > 0) add("满分 ${detail.fullMark.toInt()}")
                                        if (detail.chapter.isNotEmpty()) add(detail.chapter)
                                        if (detail.attachments > 0) add("附件 ${detail.attachments} 个")
                                        if (detail.beginTime.isNotEmpty()) add("发布 ${detail.beginTime.take(16)}")
                                    }
                                    if (metaBits.isNotEmpty()) {
                                        Text(metaBits.joinToString(" · "), fontSize = 11.sp, color = mu.ink3)
                                    }
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedButton(
                                        onClick = { vm.openExternalUrl("https://ucloud.bupt.edu.cn/uclass/index.html#/student/homePage") },
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                        shape = RoundedCornerShape(mu.cardRadius),
                                        modifier = Modifier.height(32.dp)
                                    ) {
                                        Icon(Icons.AutoMirrored.Rounded.OpenInNew, null, modifier = Modifier.size(14.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("在云邮中打开", fontSize = 11.5.sp)
                                    }
                                    Button(
                                        onClick = { vm.toggleUcloudDone(u.id) },
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                        shape = RoundedCornerShape(mu.cardRadius),
                                        modifier = Modifier.height(32.dp)
                                    ) {
                                        Text(if (uDone) "取消已处理" else "标记已处理", fontSize = 11.5.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // 今日待办列表
        item(key = "todo_header") {
            MuSectionHeader("今日待办", "$doneCount / $totalCount 条")
        }
        if (todayTodos.isEmpty()) {
            item(key = "todo_empty") {
                MuCard(backgroundColor = mu.surface2, onClick = { vm.openNewTodo(todayKey) }) {
                    Text(
                        "今天还没有安排 · 点这里或上方「+ 新建待办」加一条",
                        color = mu.ink2,
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        } else {
            itemsIndexed(todayTodos, key = { _, occ -> "td_${occ.todo.id}" }) { idx, occ ->
                TodoCardRow(
                    todo = occ.todo,
                    isDone = occ.isDone,
                    overdueDays = occ.overdueDays,
                    index = idx,
                    onToggleDone = { vm.toggleTodoDone(occ.todo.id, todayKey) },
                    onToggleSubStep = { sid -> vm.toggleSubStep(occ.todo.id, sid) },
                    onEdit = { vm.openEditTodo(occ.todo) },
                    onPostpone = { vm.postponeTodoToTomorrow(occ.todo.id) },
                    onDelete = { vm.deleteTodoWithUndo(occ.todo) }
                )
            }
        }
    }
}

@Composable
private fun TodayAlertBanner(
    pendingCount: Int,
    onGo: () -> Unit,
    onDismiss: () -> Unit
) {
    val mu = LocalMuColors.current
    val bgBrush = when (mu.style) {
        ThemeStyle.IOS -> Brush.linearGradient(listOf(mu.accent, mu.accent2))
        ThemeStyle.P3R -> Brush.linearGradient(listOf(Color(0xFF03164A), Color(0xFF015FCC)))
        ThemeStyle.P5R -> Brush.linearGradient(listOf(mu.chrome, mu.chrome))
    }
    val mod = Modifier
        .fillMaxWidth()
        .then(
            if (mu.style == ThemeStyle.P5R) {
                Modifier
                    .padding(end = 4.dp, bottom = 4.dp)
                    .drawBehind {
                        val s = 4.dp.toPx()
                        drawRect(mu.accent, topLeft = Offset(s, s), size = size)
                    }
            } else Modifier
        )
        .clip(RoundedCornerShape(mu.cardRadius))
        .background(bgBrush)
        .then(
            when (mu.style) {
                ThemeStyle.P3R -> Modifier.drawBehind {
                    drawRect(mu.p3Cyan, topLeft = Offset.Zero, size = androidx.compose.ui.geometry.Size(4.dp.toPx(), size.height))
                }
                ThemeStyle.P5R -> Modifier.border(2.dp, mu.chromeInk)
                ThemeStyle.IOS -> Modifier
            }
        )
        .padding(horizontal = 14.dp, vertical = 11.dp)

    Row(modifier = mod, verticalAlignment = Alignment.CenterVertically) {
        Icon(
            Icons.Rounded.NotificationsActive,
            null,
            tint = when (mu.style) {
                ThemeStyle.IOS -> Color.White
                ThemeStyle.P3R -> mu.p3Cyan
                ThemeStyle.P5R -> mu.accent2
            },
            modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            "今天还有 $pendingCount 条待办未完成",
            color = Color.White,
            fontWeight = FontWeight.Black,
            fontStyle = FontStyle.Normal,
            fontSize = 13.sp,
            modifier = Modifier.weight(1f)
        )
        Box(
            modifier = Modifier
                .clip(if (mu.style == ThemeStyle.IOS) RoundedCornerShape(999.dp) else RoundedCornerShape(3.dp))
                .background(if (mu.style == ThemeStyle.P5R) mu.accent else Color.White)
                .clickable(onClick = onGo)
                .padding(horizontal = 10.dp, vertical = 4.dp)
        ) {
            Text(
                "去处理",
                color = if (mu.style == ThemeStyle.P5R) Color.White else if (mu.style == ThemeStyle.P3R) mu.p3Deep else mu.accent,
                fontSize = 12.sp,
                fontWeight = FontWeight.Black
            )
        }
        Spacer(Modifier.width(8.dp))
        Icon(
            Icons.Rounded.Close,
            contentDescription = "关闭横幅",
            tint = Color.White.copy(alpha = 0.8f),
            modifier = Modifier.size(18.dp).clickable(onClick = onDismiss)
        )
    }
}

@Composable
private fun TodayHeroCard(
    monthDay: String,
    subLine: String,
    doneCount: Int,
    totalCount: Int,
    animPct: Float,
    pomoCount: Int,
    onNewTodo: () -> Unit
) {
    val mu = LocalMuColors.current

    when (mu.style) {
        ThemeStyle.P5R -> {
            // P5R：纯黑底 + 右侧红斜切带 + 倾斜 TODAY 贴条 + 红硬阴影日期 + 底部斑马预警条
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(end = 5.dp, bottom = 5.dp)
                    .drawWithCache {
                        val s = 5.dp.toPx()
                        val shadowCol = mu.accent
                        onDrawBehind {
                            drawRect(shadowCol, topLeft = Offset(s, s), size = size)
                        }
                    }
                    .background(mu.chrome)
                    .border(2.dp, mu.ink)
                    .drawWithCache {
                        val slash = Path().apply {
                            moveTo(size.width * 0.72f, 0f)
                            lineTo(size.width, 0f)
                            lineTo(size.width, size.height)
                            lineTo(size.width * 0.54f, size.height)
                            close()
                        }
                        val slashCol = mu.accent.copy(alpha = 0.90f)
                        val zebraPath = Path().apply {
                            val barY = size.height - 5.dp.toPx()
                            val shiftX = 8.dp.toPx()
                            val step = 16.dp.toPx()
                            var x = -step
                            while (x < size.width + step) {
                                moveTo(x, barY)
                                lineTo(x + shiftX, size.height)
                                x += step
                            }
                        }
                        val zebraStroke = Stroke(width = 4.dp.toPx())
                        val zebraCol = Color.White.copy(alpha = 0.28f)
                        onDrawBehind {
                            drawPath(slash, slashCol)
                            drawPath(zebraPath, zebraCol, style = zebraStroke)
                        }
                    }
                    .padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 18.dp)
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Box(
                                modifier = Modifier
                                    .graphicsLayer { rotationZ = -3f }
                                    .clip(parallelogramShape(0.22f))
                                    .background(mu.chromeInk)
                                    .padding(horizontal = 10.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    "TODAY",
                                    color = mu.chrome,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Black,
                                    letterSpacing = 2.sp
                                )
                            }
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = monthDay,
                                color = Color.White,
                                fontSize = 29.sp,
                                fontWeight = FontWeight.Black,
                                fontStyle = FontStyle.Normal,
                                letterSpacing = (-0.5).sp
                            )
                            Text(
                                text = subLine,
                                color = mu.chromeSoft,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(66.dp)) {
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                drawCircle(color = mu.chrome, radius = size.minDimension / 2f)
                                drawCircle(color = Color.White, radius = size.minDimension / 2f, style = Stroke(2.dp.toPx()))
                                drawArc(
                                    color = Color.White.copy(alpha = 0.2f),
                                    startAngle = 0f, sweepAngle = 360f, useCenter = false,
                                    style = Stroke(6.5.dp.toPx())
                                )
                                drawArc(
                                    color = mu.accent,
                                    startAngle = -90f, sweepAngle = animPct * 360f, useCenter = false,
                                    style = Stroke(6.5.dp.toPx())
                                )
                            }
                            Text("$doneCount/$totalCount", color = Color.White, fontSize = 13.5.sp, fontWeight = FontWeight.Black, fontStyle = FontStyle.Italic)
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "今日专注 $pomoCount 个番茄",
                            color = mu.chromeSoft,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f)
                        )
                        Box(
                            modifier = Modifier
                                .padding(end = 3.dp, bottom = 3.dp)
                                .drawBehind {
                                    val d = 3.dp.toPx()
                                    drawRect(Color.White, topLeft = Offset(d, d), size = size)
                                }
                                .clip(parallelogramShape(0.16f))
                                .background(mu.accent)
                                .border(1.5.dp, Color.White)
                                .clickable(onClick = onNewTodo)
                                .padding(horizontal = 14.dp, vertical = 8.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Rounded.Add, null, tint = Color.White, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("新建待办", color = Color.White, fontWeight = FontWeight.Black, fontSize = 13.sp)
                            }
                        }
                    }
                }
            }
        }

        ThemeStyle.P3R -> {
            // P3R：深蓝渐变大卡 + 右上白色斜楔 + 巨型 01 水印 + 白底旋转日期牌(粉色硬影) + 冰青新建按钮
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .drawWithCache {
                        val bgGrad = Brush.linearGradient(listOf(Color(0xFF03164A), Color(0xFF0148A6), Color(0xFF0B84F3)))
                        val wedge = Path().apply {
                            moveTo(size.width * 0.56f, 0f)
                            lineTo(size.width, 0f)
                            lineTo(size.width, size.height * 0.42f)
                            close()
                        }
                        val wedgeCol = Color.White.copy(alpha = 0.90f)
                        val barGrad = Brush.horizontalGradient(listOf(Color.Transparent, mu.p3Cyan, Color(0xFF77FEFC), Color.Transparent))
                        val barH = 3.dp.toPx()
                        val barTop = size.height - barH
                        val barSize = androidx.compose.ui.geometry.Size(size.width, barH)
                        onDrawBehind {
                            drawRect(bgGrad)
                            drawPath(wedge, wedgeCol)
                            drawRect(barGrad, topLeft = Offset(0f, barTop), size = barSize)
                        }
                    }
                    .padding(16.dp)
            ) {
                Text(
                    "01",
                    color = Color.White.copy(alpha = 0.11f),
                    fontSize = 104.sp,
                    fontWeight = FontWeight.Black,
                    fontStyle = FontStyle.Italic,
                    modifier = Modifier.align(Alignment.BottomEnd).offset(x = 6.dp, y = 14.dp)
                )
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .weight(1f, fill = false)
                                .padding(end = 5.dp, bottom = 5.dp)
                                .graphicsLayer { rotationZ = -1.6f }
                                .drawBehind {
                                    val d = 4.dp.toPx()
                                    drawRect(mu.p3Pink, topLeft = Offset(d, d), size = size)
                                }
                                .clip(RoundedCornerShape(3.dp))
                                .background(Color.White)
                                .padding(horizontal = 13.dp, vertical = 8.dp)
                        ) {
                            Column {
                                Box(
                                    modifier = Modifier
                                        .clip(parallelogramShape(0.22f))
                                        .background(mu.accent)
                                        .padding(horizontal = 7.dp, vertical = 1.dp)
                                ) {
                                    Text("TODAY", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp)
                                }
                                Spacer(Modifier.height(3.dp))
                                Text(
                                    monthDay,
                                    color = mu.accent,
                                    fontSize = 26.sp,
                                    fontWeight = FontWeight.Black,
                                    fontStyle = FontStyle.Normal,
                                    letterSpacing = (-0.5).sp
                                )
                                Text(subLine, color = mu.accent.copy(alpha = 0.85f), fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        Spacer(Modifier.weight(1f))
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(66.dp)) {
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                drawCircle(color = mu.accent, radius = size.minDimension / 2f)
                                drawCircle(color = Color.White, radius = size.minDimension / 2f, style = Stroke(2.dp.toPx()))
                                drawArc(
                                    color = Color.White.copy(alpha = 0.25f),
                                    startAngle = 0f, sweepAngle = 360f, useCenter = false,
                                    style = Stroke(6.dp.toPx())
                                )
                                drawArc(
                                    color = mu.p3Cyan,
                                    startAngle = -90f, sweepAngle = animPct * 360f, useCenter = false,
                                    style = Stroke(6.dp.toPx())
                                )
                            }
                            Text("$doneCount/$totalCount", color = Color.White, fontSize = 13.5.sp, fontWeight = FontWeight.Black)
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    HorizontalDivider(color = Color.White.copy(alpha = 0.22f))
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "今日专注 $pomoCount 个番茄",
                            color = Color(0xFFD6F2FF),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f)
                        )
                        Box(
                            modifier = Modifier
                                .padding(end = 4.dp, bottom = 4.dp)
                                .drawBehind {
                                    val d = 4.dp.toPx()
                                    drawRect(mu.p3Pink, topLeft = Offset(d, d), size = size)
                                }
                                .clip(parallelogramShape(0.2f))
                                .background(mu.p3Cyan)
                                .clickable(onClick = onNewTodo)
                                .padding(horizontal = 15.dp, vertical = 8.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Rounded.Add, null, tint = mu.p3Deep, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("新建待办", color = mu.p3Deep, fontWeight = FontWeight.Black, fontSize = 13.sp)
                            }
                        }
                    }
                }
            }
        }

        ThemeStyle.IOS -> {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(mu.cardRadius))
                    .background(Brush.linearGradient(listOf(mu.accent, mu.accent2)))
                    .padding(18.dp)
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Surface(color = Color.White.copy(alpha = 0.22f), shape = RoundedCornerShape(999.dp)) {
                                Text("TODAY", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Black, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp))
                            }
                            Spacer(Modifier.height(6.dp))
                            Text(monthDay, color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Black)
                            Text(subLine, color = Color.White.copy(alpha = 0.85f), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        }
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(66.dp)) {
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                drawArc(Color.White.copy(alpha = 0.22f), 0f, 360f, false, style = Stroke(7.dp.toPx(), cap = StrokeCap.Round))
                                drawArc(Color.White, -90f, animPct * 360f, false, style = Stroke(7.dp.toPx(), cap = StrokeCap.Round))
                            }
                            Text("$doneCount/$totalCount", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    HorizontalDivider(color = Color.White.copy(alpha = 0.18f))
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("今日专注 $pomoCount 个番茄", color = Color.White.copy(alpha = 0.9f), fontSize = 12.5.sp, modifier = Modifier.weight(1f))
                        Button(
                            onClick = onNewTodo,
                            colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = mu.accent),
                            shape = RoundedCornerShape(999.dp),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            Icon(Icons.Rounded.Add, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("新建待办", fontWeight = FontWeight.ExtraBold, fontSize = 13.sp)
                        }
                    }
                }
            }
        }
    }
}

private fun computeUcloudUrgency(
    endTime: String,
    beginTime: String,
    now: LocalDateTime = LocalDateTime.now()
): Triple<String, Float, Color> {
    val fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
    fun parseLdt(raw: String): LocalDateTime? {
        val clean = raw.replace('T', ' ').replace('/', '-').trim().take(16)
        return try { LocalDateTime.parse(clean, fmt) } catch (_: Throwable) {
            try { LocalDateTime.parse("${clean.take(10)} 23:59", fmt) } catch (_: Throwable) { null }
        }
    }
    val due = parseLdt(endTime) ?: return Triple("截止 $endTime", 1f, Color(0xFF0D7F86))
    val leftMin = ChronoUnit.MINUTES.between(now, due)
    val label = when {
        leftMin < 0 -> "已超时 ${(-leftMin / 1440).coerceAtLeast(1)} 天"
        leftMin < 60 -> "还剩 ${leftMin.coerceAtLeast(1)} 分"
        leftMin < 1440 -> "还剩 ${leftMin / 60} 小时"
        else -> "还剩 ${leftMin / 1440} 天 · ${endTime.take(16).drop(5)}"
    }
    val col = when {
        leftMin <= 0 -> Color(0xFFC1121F)
        leftMin <= 1440 -> Color(0xFFE4002B)
        leftMin <= 4320 -> Color(0xFFD97706)
        else -> Color(0xFF0F7A4F)
    }
    val frac = when {
        leftMin <= 0 -> 0f
        else -> {
            val begin = parseLdt(beginTime)
            if (begin != null && due.isAfter(begin)) {
                val span = ChronoUnit.MINUTES.between(begin, due).coerceAtLeast(1)
                (leftMin.toFloat() / span.toFloat()).coerceIn(0.03f, 1f)
            } else {
                (leftMin.toFloat() / (14 * 1440f)).coerceIn(0.03f, 1f)
            }
        }
    }
    return Triple(label, frac, col)
}
