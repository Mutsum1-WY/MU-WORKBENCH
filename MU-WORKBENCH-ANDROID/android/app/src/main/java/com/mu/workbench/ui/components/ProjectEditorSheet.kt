package com.mu.workbench.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import com.mu.workbench.data.AccentStyle
import com.mu.workbench.data.ProjectCard
import com.mu.workbench.data.SkillItem
import com.mu.workbench.ui.theme.LocalMuColors
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectEditorSheet(
    initial: ProjectCard,
    onDismiss: () -> Unit,
    onSave: (ProjectCard) -> Unit,
    onDelete: (() -> Unit)? = null
) {
    val mu = LocalMuColors.current
    var title by remember(initial) { mutableStateOf(initial.title) }
    var emoji by remember(initial) { mutableStateOf(initial.emoji.ifEmpty { "🎯" }) }
    var colorKey by remember(initial) { mutableStateOf(initial.colorKey.ifEmpty { "blue" }) }
    var deadline by remember(initial) { mutableStateOf(initial.deadline) }
    var items by remember(initial) { mutableStateOf(initial.items) }
    var newSkill by remember { mutableStateOf("") }

    val emojis = listOf("🧩", "🚀", "📚", "🎯", "💡", "🛠️", "🎨", "📈", "🧠", "🌱", "🏃", "✍️")

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = mu.surface1,
        contentColor = mu.ink,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(modifier = Modifier.fillMaxWidth().navigationBarsPadding()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    if (initial.id.isEmpty()) "新建项目卡" else "编辑项目卡",
                    fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = mu.ink,
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
                    label = { Text("项目名称") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Text("图标", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = mu.ink2)
                Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    emojis.forEach { e ->
                        val on = e == emoji
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (on) mu.accentSoft else mu.surface2)
                                .border(1.5.dp, if (on) mu.accent else mu.line, RoundedCornerShape(10.dp))
                                .clickable { emoji = e },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(e, fontSize = 18.sp)
                        }
                    }
                }

                Text("卡片色调", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = mu.ink2)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    AccentStyle.entries.take(6).forEach { ac ->
                        val on = colorKey == ac.key
                        Box(
                            modifier = Modifier
                                .size(30.dp)
                                .clip(CircleShape)
                                .background(Color(ac.hex))
                                .border(if (on) 3.dp else 1.dp, if (on) mu.ink else Color.White, CircleShape)
                                .clickable { colorKey = ac.key }
                        )
                    }
                }

                OutlinedTextField(
                    value = deadline,
                    onValueChange = { deadline = it },
                    label = { Text("截止日期（可选 YYYY-MM-DD）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Text("技能点", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = mu.ink2)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = newSkill,
                        onValueChange = { newSkill = it },
                        label = { Text("新增技能点") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = {
                        if (newSkill.isNotBlank()) {
                            items = items + SkillItem(UUID.randomUUID().toString().take(6), newSkill.trim(), 0)
                            newSkill = ""
                        }
                    }) {
                        Icon(Icons.Rounded.Add, contentDescription = "添加")
                    }
                }
                items.forEach { sk ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("• ${sk.name} (${sk.progress}%)", modifier = Modifier.weight(1f), color = mu.ink2)
                        IconButton(onClick = { items = items.filterNot { it.id == sk.id } }) {
                            Icon(Icons.Rounded.Close, contentDescription = "移除", modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }

            HorizontalDivider(color = mu.line)
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (onDelete != null && initial.id.isNotEmpty()) {
                    OutlinedButton(onClick = onDelete, colors = ButtonDefaults.outlinedButtonColors(contentColor = mu.danger)) {
                        Text("删除")
                    }
                }
                OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("取消") }
                Button(
                    onClick = {
                        if (title.isBlank()) return@Button
                        onSave(initial.copy(title = title.trim(), emoji = emoji, colorKey = colorKey, deadline = deadline.trim(), items = items))
                    },
                    enabled = title.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = mu.accent, contentColor = Color.White),
                    modifier = Modifier.weight(1.4f)
                ) { Text("保存", fontWeight = FontWeight.Bold) }
            }
        }
    }
}
