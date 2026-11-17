package com.mu.workbench.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mu.workbench.data.ThemeStyle
import com.mu.workbench.data.TodoType
import com.mu.workbench.domain.TodoEngine
import com.mu.workbench.ui.WorkbenchViewModel
import com.mu.workbench.ui.components.MuCard
import com.mu.workbench.ui.components.MuChip
import com.mu.workbench.ui.components.MuSectionHeader
import com.mu.workbench.ui.components.MuSegmentedRow
import com.mu.workbench.ui.components.TodoCardRow
import com.mu.workbench.ui.theme.LocalMuColors
import java.time.YearMonth

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CalendarScreen(vm: WorkbenchViewModel) {
    val mu = LocalMuColors.current
    val state by vm.state.collectAsState()
    val today by vm.nowDate.collectAsState()

    var currentYm by remember { mutableStateOf(YearMonth.from(today)) }
    var selectedDate by remember { mutableStateOf(today) }
    var showYmPicker by remember { mutableStateOf(false) }
    var batchStart by remember { mutableStateOf("") }
    var batchEnd by remember { mutableStateOf("") }
    var batchNote by remember { mutableStateOf("") }
    var showBatch by remember { mutableStateOf(false) }
    var showDoneHistory by remember { mutableStateOf(false) }

    val selectedKey = TodoEngine.formatDate(selectedDate)
    val selectedHoliday = TodoEngine.holidayOf(selectedKey, state, vm.builtinHolidays)
    val selectedItems = remember(selectedKey, state.todos, state.holidayManual, state.holidayCache) {
        TodoEngine.dayItems(selectedKey, state, vm.builtinHolidays)
    }

    val inboxTodos = remember(state.todos) {
        state.todos.filter { it.type == TodoType.ONCE && it.date.isBlank() && it.doneDates.isEmpty() }
    }
    val doneRecords = remember(state.todos) {
        state.todos.filter {
            (it.type == TodoType.ONCE && it.doneDates.isNotEmpty()) || (it.type != TodoType.ONCE && it.doneDates.isNotEmpty())
        }
    }

    val monthStats = remember(currentYm, state.todos, state.holidayManual, state.holidayCache) {
        var tot = 0
        var dn = 0
        for (d in 1..currentYm.lengthOfMonth()) {
            val dk = TodoEngine.formatDate(currentYm.atDay(d))
            val items = TodoEngine.dayItems(dk, state, vm.builtinHolidays)
            tot += items.size
            dn += items.count { it.isDone }
        }
        tot to dn
    }

    val currentFlagMode = when {
        state.holidayManual[selectedKey]?.off == true -> "off"
        state.holidayManual[selectedKey]?.off == false -> "work"
        else -> "auto"
    }

    val firstDayOfMonth = currentYm.atDay(1)
    val firstColOffset = firstDayOfMonth.dayOfWeek.value % 7 // Sun=0..Sat=6
    val daysInMonth = currentYm.lengthOfMonth()
    val totalCells = ((firstColOffset + daysInMonth + 6) / 7) * 7

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item(key = "cal_grid") {
            MuCard(padding = 12.dp) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { currentYm = currentYm.minusMonths(1) }) {
                        Icon(Icons.Rounded.ChevronLeft, contentDescription = "上个月", tint = mu.ink)
                    }
                    TextButton(onClick = { showYmPicker = !showYmPicker }, modifier = Modifier.weight(1f)) {
                        Text("${currentYm.year} 年 ${currentYm.monthValue} 月 ▾", fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = mu.ink)
                    }
                    if (currentYm != YearMonth.from(today) || selectedDate != today) {
                        TextButton(onClick = {
                            currentYm = YearMonth.from(today)
                            selectedDate = today
                        }) {
                            Text("回今天", fontWeight = FontWeight.Black, color = mu.accentInk)
                        }
                    }
                    IconButton(onClick = { currentYm = currentYm.plusMonths(1) }) {
                        Icon(Icons.Rounded.ChevronRight, contentDescription = "下个月", tint = mu.ink)
                    }
                }

                AnimatedVisibility(visible = showYmPicker) {
                    Column(modifier = Modifier.padding(bottom = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            (-1..2).forEach { delta ->
                                val y = today.year + delta
                                FilterChip(
                                    selected = currentYm.year == y,
                                    onClick = { currentYm = YearMonth.of(y, currentYm.monthValue) },
                                    label = { Text("$y") }
                                )
                            }
                        }
                        for (r in 0..1) {
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
                                for (c in 1..6) {
                                    val m = r * 6 + c
                                    val on = currentYm.monthValue == m
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(if (mu.style == ThemeStyle.P5R) 0.dp else 6.dp))
                                            .background(if (on) mu.accent else mu.surface2)
                                            .clickable {
                                                currentYm = YearMonth.of(currentYm.year, m)
                                                showYmPicker = false
                                            }
                                            .padding(vertical = 6.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text("${m}月", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (on) Color.White else mu.ink)
                                    }
                                }
                            }
                        }
                    }
                }

                Row(modifier = Modifier.fillMaxWidth()) {
                    listOf("日", "一", "二", "三", "四", "五", "六").forEach { w ->
                        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                            Text(w, fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = mu.ink3)
                        }
                    }
                }
                Spacer(Modifier.height(6.dp))

                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    for (row in 0 until (totalCells / 7)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
                            for (col in 0 until 7) {
                                val cellIdx = row * 7 + col
                                val dayNum = cellIdx - firstColOffset + 1
                                if (dayNum !in 1..daysInMonth) {
                                    Spacer(Modifier.weight(1f).height(48.dp))
                                } else {
                                    val d = currentYm.atDay(dayNum)
                                    val dk = TodoEngine.formatDate(d)
                                    val isSel = d == selectedDate
                                    val isToday = d == today
                                    val h = TodoEngine.holidayOf(dk, state, vm.builtinHolidays)
                                    val dayTodos = TodoEngine.dayItems(dk, state, vm.builtinHolidays)
                                    val hasPending = dayTodos.any { !it.isDone }
                                    val hasDone = dayTodos.any { it.isDone }
                                    val cellRadius = if (mu.style == ThemeStyle.P5R) 0.dp else 7.dp

                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(48.dp)
                                            .clip(RoundedCornerShape(cellRadius))
                                            .background(if (isSel) mu.accent else mu.surface2)
                                            .border(
                                                width = if (isToday && !isSel) 1.8.dp else 0.75.dp,
                                                color = if (isToday && !isSel) mu.accent else mu.line2,
                                                shape = RoundedCornerShape(cellRadius)
                                            )
                                            .combinedClickable(
                                                onClick = { selectedDate = d },
                                                onLongClick = { vm.openNewTodo(dk) }
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (h != null) {
                                            Box(
                                                modifier = Modifier
                                                    .align(Alignment.TopEnd)
                                                    .background(
                                                        if (isSel) Color.White else if (h.off) mu.danger else mu.warn,
                                                        RoundedCornerShape(bottomStart = 4.dp)
                                                    )
                                                    .padding(horizontal = 3.dp, vertical = 1.dp)
                                            ) {
                                                Text(
                                                    if (h.off) "休" else "班",
                                                    fontSize = 8.5.sp,
                                                    fontWeight = FontWeight.Black,
                                                    color = if (isSel) mu.accent else Color.White
                                                )
                                            }
                                        }
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text(
                                                "$dayNum",
                                                fontSize = 13.5.sp,
                                                fontWeight = if (isSel || isToday) FontWeight.Black else FontWeight.SemiBold,
                                                color = if (isSel) Color.White else mu.ink
                                            )
                                            if (hasPending || hasDone) {
                                                Spacer(Modifier.height(2.dp))
                                                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                                    if (hasPending) {
                                                        Box(
                                                            modifier = Modifier
                                                                .size(5.dp)
                                                                .clip(CircleShape)
                                                                .background(if (isSel) Color.White else mu.accent)
                                                        )
                                                    }
                                                    if (hasDone) {
                                                        Box(
                                                            modifier = Modifier
                                                                .size(5.dp)
                                                                .clip(CircleShape)
                                                                .background(if (isSel) Color.White.copy(alpha = 0.7f) else mu.ok)
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
                }
                Spacer(Modifier.height(8.dp))
                val (mTotal, mDone) = monthStats
                Text(
                    text = if (mTotal > 0) "本月安排 $mTotal 个待办 · 已完成 $mDone 个 · 长按日期可快速新建"
                    else "本月暂无待办 · 长按任意日期可快速新建 · 休=放假 班=调休",
                    fontSize = 11.5.sp,
                    color = mu.ink3,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        // 选中日期控制条
        item(key = "cal_day_ctrl") {
            MuCard(padding = 12.dp) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(selectedKey, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, color = mu.ink)
                        val info = selectedHoliday?.let {
                            (if (it.off) "放假" else "调休上班") + if (it.name.isNotEmpty()) " · ${it.name}" else ""
                        } ?: if (TodoEngine.isWorkday(selectedDate, state, vm.builtinHolidays)) "按工作日算" else "周末休息日"
                        Text(info, fontSize = 12.sp, color = mu.ink2)
                    }
                    OutlinedButton(onClick = { vm.openNewTodo(selectedKey) }, contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)) {
                        Icon(Icons.Rounded.Add, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("加待办", fontSize = 12.sp)
                    }
                }
                Spacer(Modifier.height(10.dp))
                MuSegmentedRow(
                    items = listOf("auto" to "自动", "off" to "标为放假", "work" to "调休上班"),
                    selected = currentFlagMode,
                    onSelect = { mode -> vm.setHolidayRange(selectedDate, selectedDate, mode, "") }
                )
                Spacer(Modifier.height(6.dp))
                TextButton(onClick = {
                    if (batchStart.isEmpty()) batchStart = selectedKey
                    if (batchEnd.isEmpty()) batchEnd = selectedKey
                    showBatch = !showBatch
                }) {
                    Text(if (showBatch) "收起批量标注" else "给一段日期标放假 / 调休", fontSize = 12.sp, color = mu.accentInk)
                }
                AnimatedVisibility(visible = showBatch) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(batchStart, { batchStart = it }, label = { Text("起 YYYY-MM-DD") }, singleLine = true, modifier = Modifier.weight(1f))
                            OutlinedTextField(batchEnd, { batchEnd = it }, label = { Text("止 YYYY-MM-DD") }, singleLine = true, modifier = Modifier.weight(1f))
                        }
                        OutlinedTextField(batchNote, { batchNote = it }, label = { Text("名称（可选，如 寒假）") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = {
                                val a = TodoEngine.parseDate(batchStart) ?: selectedDate
                                val b = TodoEngine.parseDate(batchEnd) ?: selectedDate
                                vm.setHolidayRange(a, b, "off", batchNote)
                            }, modifier = Modifier.weight(1f)) { Text("整段放假") }
                            OutlinedButton(onClick = {
                                val a = TodoEngine.parseDate(batchStart) ?: selectedDate
                                val b = TodoEngine.parseDate(batchEnd) ?: selectedDate
                                vm.setHolidayRange(a, b, "work", batchNote)
                            }, modifier = Modifier.weight(1f)) { Text("调休上班") }
                        }
                    }
                }
            }
        }

        item(key = "cal_day_head") {
            MuSectionHeader("当日安排", "${selectedItems.size} 条")
        }
        if (selectedItems.isEmpty()) {
            item(key = "cal_day_empty") {
                MuCard(backgroundColor = mu.surface2) {
                    Text("这一天暂无安排", color = mu.ink2, fontSize = 13.sp)
                }
            }
        } else {
            itemsIndexed(selectedItems, key = { _, it -> "cal_${selectedKey}_${it.todo.id}" }) { idx, occ ->
                TodoCardRow(
                    todo = occ.todo,
                    isDone = occ.isDone,
                    overdueDays = occ.overdueDays,
                    index = idx,
                    onToggleDone = { vm.toggleTodoDone(occ.todo.id, selectedKey) },
                    onToggleSubStep = { sid -> vm.toggleSubStep(occ.todo.id, sid) },
                    onEdit = { vm.openEditTodo(occ.todo) },
                    onPostpone = { vm.postponeTodoToTomorrow(occ.todo.id) },
                    onDelete = { vm.deleteTodoWithUndo(occ.todo) }
                )
            }
        }

        // 收集箱（未安排日期的待办）
        item(key = "cal_inbox_head") {
            MuSectionHeader("收集箱（未安排日期）", "${inboxTodos.size} 条")
        }
        if (inboxTodos.isEmpty()) {
            item(key = "cal_inbox_empty") {
                MuCard(backgroundColor = mu.surface2) {
                    Text("收集箱是空的 · 可以在新建待办时清空日期将待办放入收集箱", color = mu.ink3, fontSize = 12.5.sp)
                }
            }
        } else {
            itemsIndexed(inboxTodos, key = { _, t -> "inbox_${t.id}" }) { _, t ->
                MuCard(padding = 12.dp, onClick = { vm.openEditTodo(t) }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(t.title, fontWeight = FontWeight.Bold, color = mu.ink, fontSize = 14.sp)
                            Spacer(Modifier.height(3.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                MuChip(t.priority.label, Color(t.priority.colorHex).copy(alpha = 0.16f), mu.ink2)
                                MuChip(t.tag, mu.accentSoft, mu.accentInk)
                            }
                        }
                        OutlinedButton(
                            onClick = { vm.moveTodoToDate(t.id, selectedKey) },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Text("放到当天", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // 已完成归档（可折叠）
        item(key = "cal_done_toggle") {
            MuCard(padding = 12.dp, onClick = { showDoneHistory = !showDoneHistory }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (showDoneHistory) "▾ 已完成记录" else "▸ 已完成记录",
                        fontWeight = FontWeight.ExtraBold,
                        color = mu.ink,
                        modifier = Modifier.weight(1f)
                    )
                    Text("${doneRecords.size} 条", fontSize = 12.sp, color = mu.ink3, fontWeight = FontWeight.Bold)
                }
                AnimatedVisibility(visible = showDoneHistory) {
                    Column(modifier = Modifier.padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (doneRecords.isEmpty()) {
                            Text("还没有完成记录", fontSize = 12.sp, color = mu.ink3)
                        } else {
                            doneRecords.take(30).forEach { t ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth().clickable { vm.openEditTodo(t) }
                                ) {
                                    Text(
                                        t.title,
                                        fontSize = 13.sp,
                                        color = mu.ink3,
                                        textDecoration = TextDecoration.LineThrough,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Text(
                                        if (t.type == TodoType.ONCE) (t.doneDates.firstOrNull() ?: "已完成")
                                        else "${t.type.label} · 完成 ${t.doneDates.size} 次",
                                        fontSize = 11.sp,
                                        color = mu.ink3
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
