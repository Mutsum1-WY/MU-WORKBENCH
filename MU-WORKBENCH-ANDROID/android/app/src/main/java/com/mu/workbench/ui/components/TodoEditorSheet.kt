package com.mu.workbench.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mu.workbench.data.SubStep
import com.mu.workbench.data.TodoItem
import com.mu.workbench.data.TodoPriority
import com.mu.workbench.data.TodoType
import com.mu.workbench.domain.TodoEngine
import com.mu.workbench.ui.theme.LocalMuColors
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodoEditorSheet(
    initial: TodoItem,
    onDismiss: () -> Unit,
    onSave: (TodoItem) -> Unit,
    onDelete: (() -> Unit)? = null
) {
    val mu = LocalMuColors.current
    var title by remember(initial) { mutableStateOf(initial.title) }
    var type by remember(initial) { mutableStateOf(initial.type) }
    var date by remember(initial) { mutableStateOf(initial.date.ifEmpty { TodoEngine.todayKey() }) }
    var weekdays by remember(initial) { mutableStateOf(initial.weekdays.ifEmpty { listOf(1, 3, 5) }) }
    var monthDays by remember(initial) { mutableStateOf(initial.monthDays.ifEmpty { listOf(1) }) }
    var intervalText by remember(initial) { mutableStateOf(initial.interval.toString()) }
    var priority by remember(initial) { mutableStateOf(initial.priority) }
    var tag by remember(initial) { mutableStateOf(initial.tag.ifEmpty { "学习" }) }
    var remindTime by remember(initial) { mutableStateOf(initial.remindTime) }
    var note by remember(initial) { mutableStateOf(initial.note) }
    var subSteps by remember(initial) { mutableStateOf(initial.subSteps) }
    var newStepText by remember { mutableStateOf("") }
    var showMore by remember { mutableStateOf(initial.note.isNotEmpty() || initial.subSteps.isNotEmpty()) }

    val tags = listOf("学习", "工作", "生活", "运动", "阅读", "其他")
    val weekLabels = listOf(1 to "一", 2 to "二", 3 to "三", 4 to "四", 5 to "五", 6 to "六", 0 to "日")

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = mu.surface1,
        contentColor = mu.ink,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    if (initial.id.isEmpty()) "新建待办" else "编辑待办",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = mu.ink,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Rounded.Close, contentDescription = "关闭", tint = mu.ink2)
                }
            }

            Column(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("要做什么") },
                    placeholder = { Text("例如：复习高数 / 交实验报告") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Text("重复类型", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = mu.ink2)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TodoType.entries.forEach { tp ->
                        val on = tp == type
                        FilterChip(
                            selected = on,
                            onClick = { type = tp },
                            label = { Text(tp.label, fontWeight = FontWeight.Bold) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = mu.accent,
                                selectedLabelColor = Color.White
                            )
                        )
                    }
                }
                Text(type.desc, fontSize = 11.5.sp, color = mu.ink3)

                if (type == TodoType.ONCE || type == TodoType.INTERVAL_DAYS) {
                    OutlinedTextField(
                        value = date,
                        onValueChange = { date = it },
                        label = { Text(if (type == TodoType.ONCE) "日期 (YYYY-MM-DD)" else "起始日期 (YYYY-MM-DD)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                if (type == TodoType.INTERVAL_DAYS) {
                    OutlinedTextField(
                        value = intervalText,
                        onValueChange = { intervalText = it.filter { c -> c.isDigit() } },
                        label = { Text("间隔天数（N）") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                if (type == TodoType.WEEKDAYS) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                        weekLabels.forEach { (idx, lbl) ->
                            val on = idx in weekdays
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (on) mu.accent else mu.surface2)
                                    .border(1.dp, if (on) mu.accent else mu.line2, RoundedCornerShape(8.dp))
                                    .clickable {
                                        weekdays = if (on && weekdays.size > 1) weekdays - idx else if (on) weekdays else weekdays + idx
                                    }
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(lbl, color = if (on) Color.White else mu.ink, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                        }
                    }
                }
                if (type == TodoType.MONTHLY_DAYS) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        for (row in 0..4) {
                            Row(horizontalArrangement = Arrangement.spacedBy(5.dp), modifier = Modifier.fillMaxWidth()) {
                                for (col in 1..7) {
                                    val day = row * 7 + col
                                    if (day <= 31) {
                                        val on = day in monthDays
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(if (on) mu.accent else mu.surface2)
                                                .border(1.dp, if (on) mu.accent else mu.line, RoundedCornerShape(6.dp))
                                                .clickable {
                                                    monthDays = if (on && monthDays.size > 1) monthDays - day else if (on) monthDays else monthDays + day
                                                }
                                                .padding(vertical = 6.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text("$day", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (on) Color.White else mu.ink)
                                        }
                                    } else {
                                        Spacer(Modifier.weight(1f))
                                    }
                                }
                            }
                        }
                    }
                }

                // 优先级与提醒时间
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("优先级", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = mu.ink2)
                        Spacer(Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            TodoPriority.entries.forEach { p ->
                                val on = p == priority
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (on) Color(p.colorHex) else mu.surface2)
                                        .clickable { priority = p }
                                        .padding(vertical = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(p.label, color = if (on) Color.White else mu.ink, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                }
                            }
                        }
                    }
                    OutlinedTextField(
                        value = remindTime,
                        onValueChange = { remindTime = it },
                        label = { Text("提醒 (HH:mm)") },
                        placeholder = { Text("09:00") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                Text("分类", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = mu.ink2)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    tags.forEach { tg ->
                        FilterChip(
                            selected = tag == tg,
                            onClick = { tag = tg },
                            label = { Text(tg) }
                        )
                    }
                }

                TextButton(onClick = { showMore = !showMore }) {
                    Text(if (showMore) "收起更多选项（子步骤 / 备注）" else "展开更多选项（子步骤 / 备注）", color = mu.accentInk)
                }

                AnimatedVisibility(visible = showMore) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = newStepText,
                                onValueChange = { newStepText = it },
                                label = { Text("添加子步骤") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = {
                                if (newStepText.isNotBlank()) {
                                    subSteps = subSteps + SubStep(UUID.randomUUID().toString().take(6), newStepText.trim())
                                    newStepText = ""
                                }
                            }) {
                                Icon(Icons.Rounded.Add, contentDescription = "添加")
                            }
                        }
                        subSteps.forEach { st ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("• ${st.text}", modifier = Modifier.weight(1f), color = mu.ink2, fontSize = 13.sp)
                                IconButton(onClick = { subSteps = subSteps.filterNot { it.id == st.id } }) {
                                    Icon(Icons.Rounded.Close, contentDescription = "删步骤", modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                        OutlinedTextField(
                            value = note,
                            onValueChange = { note = it },
                            label = { Text("备注") },
                            minLines = 2,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
            }

            // Sticky 底部操作栏
            HorizontalDivider(color = mu.line)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(mu.surface1)
                    .padding(horizontal = 18.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (onDelete != null && initial.id.isNotEmpty()) {
                    OutlinedButton(
                        onClick = onDelete,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = mu.danger)
                    ) { Text("删除") }
                }
                OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) {
                    Text("取消")
                }
                Button(
                    onClick = {
                        if (title.isBlank()) return@Button
                        onSave(
                            initial.copy(
                                title = title.trim(),
                                type = type,
                                date = date.trim(),
                                weekdays = weekdays,
                                monthDays = monthDays.sorted(),
                                interval = (intervalText.toIntOrNull() ?: 2).coerceAtLeast(1),
                                priority = priority,
                                tag = tag,
                                remindTime = remindTime.trim(),
                                note = note.trim(),
                                subSteps = subSteps
                            )
                        )
                    },
                    enabled = title.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = mu.accent, contentColor = Color.White),
                    modifier = Modifier.weight(1.4f)
                ) {
                    Text("保存", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
