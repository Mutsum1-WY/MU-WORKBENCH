package com.mu.workbench.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mu.workbench.data.AccentStyle
import com.mu.workbench.data.ThemeStyle
import com.mu.workbench.domain.TodoEngine
import com.mu.workbench.ui.FocusSub
import com.mu.workbench.ui.WorkbenchViewModel
import com.mu.workbench.ui.components.MuCard
import com.mu.workbench.ui.components.MuChip
import com.mu.workbench.ui.components.MuSectionHeader
import com.mu.workbench.ui.components.MuSegmentedRow
import com.mu.workbench.ui.components.MuTabTransition
import com.mu.workbench.ui.theme.LocalMuColors
import java.time.LocalTime
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun FocusScreen(vm: WorkbenchViewModel) {
    val mu = LocalMuColors.current
    val focusSub by vm.focusSub.collectAsState()

    Column(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
            MuSegmentedRow(
                items = listOf(FocusSub.CLOCK to "时钟", FocusSub.PROJECTS to "项目"),
                selected = focusSub,
                onSelect = { vm.selectFocusSub(it) }
            )
        }
        MuTabTransition(
            targetState = focusSub,
            direction = if (focusSub == FocusSub.PROJECTS) 1 else -1,
            style = mu.style,
            modifier = Modifier.fillMaxSize()
        ) { sub ->
            if (sub == FocusSub.CLOCK) ClockPane(vm) else ProjectsPane(vm)
        }
    }
}

@Composable
private fun ClockPane(vm: WorkbenchViewModel) {
    val mu = LocalMuColors.current
    val state by vm.state.collectAsState()
    val today by vm.nowDate.collectAsState()
    val pomo by vm.pomo.collectAsState()
    val sw by vm.sw.collectAsState()
    var toolTab by remember { mutableStateOf(0) } // 0=秒表 1=倒计时

    val todayItems = remember(state.todos, today) { TodoEngine.todayItems(state, vm.builtinHolidays, today).filter { !it.isDone } }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 10.dp, end = 10.dp, bottom = 18.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // 紧凑表盘 + 数字时间并排卡片（仅时钟卡随秒级心跳重组，隔离番茄钟与秒表卡片）
        item {
            ClockHeroCard(vm = vm, today = today)
        }

        // 番茄钟（进入首屏）
        item {
            val statusText = when {
                pomo.isFocus && pomo.running -> "专注中"
                pomo.isFocus && pomo.paused -> "已暂停"
                pomo.isFocus -> "准备专注"
                pomo.running -> "休息中"
                pomo.paused -> "休息已暂停"
                else -> "准备休息"
            }
            val pomoProg = if (pomo.totalSec > 0) 1f - pomo.leftSec.toFloat() / pomo.totalSec.toFloat() else 0f
            val mins = pomo.leftSec / 60
            val secs = pomo.leftSec % 60
            val linkedTitle = todayItems.firstOrNull { it.todo.id == pomo.linkedTodoId }?.todo?.title

            MuCard(padding = 12.dp) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("番茄钟", fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, color = mu.ink, modifier = Modifier.weight(1f))
                    MuChip(statusText, mu.accentSoft, mu.accentInk)
                }
                Spacer(Modifier.height(8.dp))
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxWidth()) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(124.dp)) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            drawArc(
                                color = mu.surface3,
                                startAngle = 0f, sweepAngle = 360f, useCenter = false,
                                style = Stroke(width = 7.dp.toPx(), cap = StrokeCap.Round)
                            )
                            drawArc(
                                color = if (pomo.isFocus) mu.accent else mu.ok,
                                startAngle = -90f, sweepAngle = pomoProg * 360f, useCenter = false,
                                style = Stroke(width = 7.dp.toPx(), cap = StrokeCap.Round)
                            )
                        }
                        Text(
                            "%02d:%02d".format(mins, secs),
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Black,
                            fontStyle = if (mu.style != ThemeStyle.IOS) FontStyle.Italic else FontStyle.Normal,
                            style = TextStyle(fontFeatureSettings = "tnum"),
                            color = mu.ink
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Button(
                        onClick = { vm.startOrPausePomo() },
                        colors = ButtonDefaults.buttonColors(containerColor = mu.accent, contentColor = Color.White),
                        modifier = Modifier.weight(1.4f)
                    ) {
                        Text(if (pomo.running) "暂停" else if (pomo.paused) "继续" else "开始", fontWeight = FontWeight.Bold)
                    }
                    OutlinedButton(onClick = { vm.resetPomo(pomo.isFocus) }, modifier = Modifier.weight(1f)) { Text("重置") }
                    OutlinedButton(onClick = { vm.resetPomo(!pomo.isFocus) }, modifier = Modifier.weight(1f)) {
                        Text(if (pomo.isFocus) "切休息" else "切专注")
                    }
                }
                if (todayItems.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    Text("关联待办：${linkedTitle ?: "未关联（点下方选择）"}", fontSize = 12.sp, color = mu.ink3)
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        todayItems.take(3).forEach { occ ->
                            FilterChip(
                                selected = occ.todo.id == pomo.linkedTodoId,
                                onClick = { vm.linkPomoTodo(if (pomo.linkedTodoId == occ.todo.id) "" else occ.todo.id) },
                                label = { Text(occ.todo.title.take(8)) }
                            )
                        }
                    }
                }
            }
        }

        // 秒表 / 倒计时
        item {
            MuCard(padding = 11.dp) {
                MuSegmentedRow(
                    items = listOf(0 to "秒表", 1 to "倒计时"),
                    selected = toolTab,
                    onSelect = { toolTab = it }
                )
                Spacer(Modifier.height(8.dp))
                if (toolTab == 0) {
                    val totalSec = sw.elapsedMs / 1000
                    val cs = (sw.elapsedMs % 1000) / 10
                    Text(
                        "%02d:%02d.%02d".format(totalSec / 60, totalSec % 60, cs),
                        fontSize = 30.sp, fontWeight = FontWeight.Black, color = mu.ink
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { vm.toggleStopwatch() }, modifier = Modifier.weight(1f)) {
                            Text(if (sw.running) "暂停" else "开始")
                        }
                        OutlinedButton(onClick = { vm.resetStopwatch() }, modifier = Modifier.weight(1f)) { Text("归零") }
                    }
                } else {
                    val m = sw.cdLeftSec / 60
                    val s = sw.cdLeftSec % 60
                    Text("%02d:%02d".format(m, s), fontSize = 30.sp, fontWeight = FontWeight.Black, color = mu.ink)
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(5, 10, 20, 45).forEach { mins ->
                            FilterChip(
                                selected = sw.cdMinutes == mins && !sw.cdRunning,
                                onClick = { vm.setCountdownMinutes(mins) },
                                label = { Text("${mins}m") }
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = { vm.toggleCountdown() }, modifier = Modifier.fillMaxWidth()) {
                        Text(if (sw.cdRunning) "暂停倒计时" else "开始倒计时")
                    }
                }
            }
        }
    }
}

@Composable
private fun ClockHeroCard(
    vm: WorkbenchViewModel,
    today: java.time.LocalDate
) {
    val mu = LocalMuColors.current
    val nowTime by vm.nowTime.collectAsState()
    val dayPct = (nowTime.hour * 3600 + nowTime.minute * 60 + nowTime.second) / 86400f

    MuCard(padding = 12.dp) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            AnalogClockCanvas(nowTime = nowTime, dayPct = dayPct, modifier = Modifier.size(92.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "%02d:%02d:%02d".format(nowTime.hour, nowTime.minute, nowTime.second),
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Black,
                    fontStyle = if (mu.style != ThemeStyle.IOS) FontStyle.Italic else FontStyle.Normal,
                    style = TextStyle(fontFeatureSettings = "tnum"),
                    color = mu.ink
                )
                Spacer(Modifier.height(4.dp))
                Text("${today.year}年${today.monthValue}月${today.dayOfMonth}日", color = mu.ink2, fontSize = 13.sp)
                Spacer(Modifier.height(8.dp))
                Text("今日已过 ${(dayPct * 100).toInt()}%", fontSize = 11.5.sp, color = mu.ink3)
                Spacer(Modifier.height(4.dp))
                Box(modifier = Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(999.dp)).background(mu.surface3)) {
                    Box(modifier = Modifier.fillMaxWidth(dayPct).fillMaxHeight().background(mu.accent))
                }
            }
        }
    }
}

@Composable
private fun AnalogClockCanvas(nowTime: LocalTime, dayPct: Float, modifier: Modifier = Modifier) {
    val mu = LocalMuColors.current
    Canvas(modifier = modifier) {
        val r = size.minDimension / 2f
        val center = Offset(r, r)
        drawCircle(color = mu.surface2, radius = r - 2.dp.toPx(), center = center)
        drawCircle(color = mu.line2, radius = r - 2.dp.toPx(), center = center, style = Stroke(1.5.dp.toPx()))
        drawArc(
            color = mu.accent.copy(alpha = 0.28f),
            startAngle = -90f, sweepAngle = dayPct * 360f, useCenter = false,
            style = Stroke(4.dp.toPx(), cap = StrokeCap.Round)
        )
        for (i in 0 until 12) {
            val ang = Math.toRadians((i * 30 - 90).toDouble())
            val inner = if (i % 3 == 0) r * 0.74f else r * 0.82f
            val outer = r * 0.90f
            drawLine(
                color = if (i % 3 == 0) mu.accent else mu.ink3,
                start = Offset(center.x + (cos(ang) * inner).toFloat(), center.y + (sin(ang) * inner).toFloat()),
                end = Offset(center.x + (cos(ang) * outer).toFloat(), center.y + (sin(ang) * outer).toFloat()),
                strokeWidth = if (i % 3 == 0) 2.5.dp.toPx() else 1.2.dp.toPx(),
                cap = StrokeCap.Round
            )
        }
        fun hand(deg: Double, len: Float, w: Float, col: Color) {
            val rad = Math.toRadians(deg - 90.0)
            drawLine(
                color = col,
                start = center,
                end = Offset(center.x + (cos(rad) * len).toFloat(), center.y + (sin(rad) * len).toFloat()),
                strokeWidth = w,
                cap = StrokeCap.Round
            )
        }
        val secDeg = nowTime.second * 6.0
        val minDeg = nowTime.minute * 6.0 + nowTime.second * 0.1
        val hrDeg = (nowTime.hour % 12) * 30.0 + nowTime.minute * 0.5
        hand(hrDeg, r * 0.48f, 3.2.dp.toPx(), mu.ink)
        hand(minDeg, r * 0.68f, 2.4.dp.toPx(), mu.ink2)
        hand(secDeg, r * 0.76f, 1.4.dp.toPx(), mu.accent)
        drawCircle(color = mu.accent, radius = 3.5.dp.toPx(), center = center)
    }
}

@Composable
private fun ProjectsPane(vm: WorkbenchViewModel) {
    val mu = LocalMuColors.current
    val state by vm.state.collectAsState()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 10.dp, end = 10.dp, bottom = 18.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                MuSectionHeader("项目与技能点", "${state.projects.size} 张", modifier = Modifier.weight(1f))
                Button(onClick = { vm.openNewProject() }, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)) {
                    Icon(Icons.Rounded.Add, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("新建项目", fontSize = 12.sp)
                }
            }
        }
        if (state.projects.isEmpty()) {
            item {
                MuCard(backgroundColor = mu.surface2, onClick = { vm.openNewProject() }) {
                    Text("还没有项目卡，点这里建第一张", color = mu.ink2)
                }
            }
        } else {
            items(state.projects, key = { it.id }) { p ->
                val avg = if (p.items.isEmpty()) 0 else p.items.sumOf { it.progress } / p.items.size
                val cardColor = Color(AccentStyle.fromKey(p.colorKey).hex)
                MuCard(padding = 11.dp) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(cardColor.copy(alpha = 0.16f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(p.emoji, fontSize = 18.sp)
                        }
                        Spacer(Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(p.title, fontWeight = FontWeight.ExtraBold, fontSize = 15.5.sp, color = mu.ink)
                            Text("总进度 $avg%" + if (p.deadline.isNotEmpty()) " · 截止 ${p.deadline}" else "", fontSize = 11.5.sp, color = mu.ink3)
                        }
                        IconButton(onClick = { vm.openEditProject(p) }) {
                            Icon(Icons.Rounded.Edit, contentDescription = "编辑", tint = mu.ink2)
                        }
                    }
                    if (p.items.isNotEmpty()) {
                        Spacer(Modifier.height(10.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            p.items.forEach { sk ->
                                Column {
                                    Row {
                                        Text(sk.name, fontSize = 13.sp, color = mu.ink, modifier = Modifier.weight(1f))
                                        Text("${sk.progress}%", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = cardColor)
                                    }
                                    Slider(
                                        value = sk.progress.toFloat(),
                                        onValueChange = { vm.updateSkillProgress(p.id, sk.id, it.toInt()) },
                                        valueRange = 0f..100f,
                                        colors = SliderDefaults.colors(thumbColor = cardColor, activeTrackColor = cardColor),
                                        modifier = Modifier.height(26.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
