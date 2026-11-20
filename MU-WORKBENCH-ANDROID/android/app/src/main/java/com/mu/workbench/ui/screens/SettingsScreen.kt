package com.mu.workbench.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mu.workbench.alarm.NotificationHelper
import com.mu.workbench.data.AccentStyle
import com.mu.workbench.data.ThemeStyle
import com.mu.workbench.ui.WorkbenchViewModel
import com.mu.workbench.ui.components.MuCard
import com.mu.workbench.ui.components.MuSectionHeader
import com.mu.workbench.ui.components.MuSegmentedRow
import com.mu.workbench.ui.theme.LocalMuColors
import org.json.JSONObject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    vm: WorkbenchViewModel,
    onClose: () -> Unit
) {
    val mu = LocalMuColors.current
    val ctx = LocalContext.current
    val state by vm.state.collectAsState()

    var focusStr by remember(state.settings.focusMinutes) { mutableStateOf(state.settings.focusMinutes.toString()) }
    var brkStr by remember(state.settings.breakMinutes) { mutableStateOf(state.settings.breakMinutes.toString()) }
    var ucUser by remember(state.ucloud.id) { mutableStateOf(state.ucloud.id) }
    var ucPwd by remember(state.ucloud.pwd) { mutableStateOf(state.ucloud.pwd) }
    var jwUser by remember(state.timetable.user) { mutableStateOf(state.timetable.user) }
    var jwPwd by remember(state.timetable.pwd) { mutableStateOf(state.timetable.pwd) }
    var jwStart by remember(state.timetable.startMonday) { mutableStateOf(state.timetable.startMonday.ifEmpty { vm.builtinTimetable.startMonday }) }
    var jwWeeks by remember(state.timetable.totalWeeks) { mutableStateOf(if (state.timetable.totalWeeks > 0) state.timetable.totalWeeks.toString() else vm.builtinTimetable.totalWeeks.toString()) }
    var showJwTrace by remember { mutableStateOf(false) }
    var bgStatus by remember { mutableStateOf(vm.muBgCheck.status()) }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            try {
                val txt = ctx.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
                if (!txt.isNullOrBlank()) vm.importBackup(txt)
            } catch (e: Throwable) {
                vm.toast("读取备份失败：${e.message}")
            }
        }
    }

    Scaffold(
        containerColor = mu.paper,
        topBar = {
            TopAppBar(
                title = { Text("设置与引擎", fontWeight = FontWeight.ExtraBold) },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = mu.chrome,
                    titleContentColor = mu.chromeInk,
                    navigationIconContentColor = mu.chromeInk
                )
            )
        }
    ) { pad ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().background(mu.paper).padding(pad),
            contentPadding = PaddingValues(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 主题风格与配色
            item {
                MuSectionHeader("主题风格与色彩")
                Spacer(Modifier.height(6.dp))
                MuCard(padding = 14.dp) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        ThemeStyle.entries.forEach { ts ->
                            val on = state.settings.theme == ts
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        vm.updateState { s -> s.copy(settings = s.settings.copy(theme = ts)) }
                                        vm.toast("已切换为 ${ts.label}")
                                    }
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(selected = on, onClick = {
                                    vm.updateState { s -> s.copy(settings = s.settings.copy(theme = ts)) }
                                })
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(ts.label, fontWeight = FontWeight.Bold, color = mu.ink, fontSize = 14.sp)
                                    Text(ts.desc, fontSize = 11.5.sp, color = mu.ink3)
                                }
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("主题配色", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = mu.ink2)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                state.settings.accent.displayLabel(state.settings.theme),
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = mu.accentInk
                            )
                        }
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            AccentStyle.entries
                                .filterNot {
                                    (state.settings.theme == ThemeStyle.P5R && it == AccentStyle.RED) ||
                                        (state.settings.theme == ThemeStyle.P3R && it == AccentStyle.P3BLUE)
                                }
                                .forEach { ac ->
                                    val on = state.settings.accent == ac ||
                                        (state.settings.theme == ThemeStyle.P5R && state.settings.accent == AccentStyle.RED && ac == AccentStyle.BLUE) ||
                                        (state.settings.theme == ThemeStyle.P3R && state.settings.accent == AccentStyle.P3BLUE && ac == AccentStyle.BLUE)
                                    Box(
                                        modifier = Modifier
                                            .size(30.dp)
                                            .clip(CircleShape)
                                            .background(Color(ac.previewHex(state.settings.theme)))
                                            .border(if (on) 3.dp else 1.dp, if (on) mu.ink else Color.White, CircleShape)
                                            .clickable {
                                                vm.updateState { s -> s.copy(settings = s.settings.copy(accent = ac)) }
                                                vm.toast("配色已切换为 ${ac.displayLabel(state.settings.theme)}")
                                            }
                                    )
                                }
                        }
                    }
                }
            }

            // 提醒与番茄钟
            item {
                MuSectionHeader("提醒与专注")
                Spacer(Modifier.height(6.dp))
                MuCard(padding = 14.dp) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("待办到点提醒", fontWeight = FontWeight.Bold, color = mu.ink)
                                Text(if (NotificationHelper.canScheduleExact(ctx)) "精确闹钟已授权" else "建议授权精确闹钟以准时响铃", fontSize = 11.5.sp, color = mu.ink3)
                            }
                            Switch(
                                checked = state.settings.remindOn,
                                onCheckedChange = { on -> vm.updateState(rescheduleAlarms = true) { s -> s.copy(settings = s.settings.copy(remindOn = on)) } }
                            )
                        }
                        if (!NotificationHelper.canScheduleExact(ctx)) {
                            OutlinedButton(onClick = { NotificationHelper.openExactAlarmSettings(ctx) }) {
                                Text("去开启精确闹钟权限")
                            }
                        }
                        Text("提示音音量", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = mu.ink2)
                        MuSegmentedRow(
                            items = listOf(0 to "静默", 1 to "轻柔", 2 to "标准"),
                            selected = state.settings.soundLevel,
                            onSelect = { lvl ->
                                vm.updateState { s -> s.copy(settings = s.settings.copy(soundLevel = lvl)) }
                                NotificationHelper.playChime(lvl)
                            }
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = focusStr,
                                onValueChange = { focusStr = it.filter { c -> c.isDigit() } },
                                label = { Text("专注时长（分）") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = brkStr,
                                onValueChange = { brkStr = it.filter { c -> c.isDigit() } },
                                label = { Text("休息时长（分）") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Button(onClick = {
                            val f = (focusStr.toIntOrNull() ?: 25).coerceIn(1, 120)
                            val b = (brkStr.toIntOrNull() ?: 5).coerceIn(1, 60)
                            vm.updateState { s -> s.copy(settings = s.settings.copy(focusMinutes = f, breakMinutes = b)) }
                            vm.resetPomo(true)
                            vm.toast("番茄钟时长已保存")
                        }) { Text("应用番茄钟时长") }
                    }
                }
            }

            // 云邮账号
            item {
                MuSectionHeader("云邮教学空间 / 门户凭据")
                Spacer(Modifier.height(6.dp))
                MuCard(padding = 14.dp) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("在今日页显示云邮待办卡片", modifier = Modifier.weight(1f), color = mu.ink, fontWeight = FontWeight.Bold)
                            Switch(
                                checked = state.ucloud.on,
                                onCheckedChange = { on -> vm.updateState { s -> s.copy(ucloud = s.ucloud.copy(on = on)) } }
                            )
                        }
                        OutlinedTextField(ucUser, { ucUser = it }, label = { Text("学工号") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(ucPwd, { ucPwd = it }, label = { Text("密码") }, visualTransformation = PasswordVisualTransformation(), singleLine = true, modifier = Modifier.fillMaxWidth())
                        if (state.ucloud.lastError.isNotEmpty()) {
                            Text(state.ucloud.lastError, color = mu.danger, fontSize = 12.sp)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { vm.loginAndSyncUcloud(ucUser, ucPwd) }, modifier = Modifier.weight(1f)) {
                                Text("登录并同步云邮")
                            }
                            OutlinedButton(onClick = {
                                vm.askConfirm("清除云邮凭据", "确定清除本地保存的学工号和密码吗？") {
                                    ucUser = ""; ucPwd = ""
                                    vm.updateState { s -> s.copy(ucloud = s.ucloud.copy(on = true, id = "", pwd = "", token = "", refreshToken = "", items = emptyList())) }
                                }
                            }) { Text("清除") }
                        }
                    }
                }
            }

            // 教务课表账号
            item {
                val activeTt = vm.activeTimetable()
                MuSectionHeader("教务系统 · 在线课表")
                Spacer(Modifier.height(6.dp))
                MuCard(padding = 14.dp) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("进 App 自动更新课表", color = mu.ink, fontWeight = FontWeight.Bold)
                                Text("已填凭据且超过 6 小时自动拉取一次", fontSize = 11.5.sp, color = mu.ink3)
                            }
                            Switch(
                                checked = state.timetable.auto,
                                onCheckedChange = { on -> vm.updateState { s -> s.copy(timetable = s.timetable.copy(auto = on)) } }
                            )
                        }
                        Text(
                            text = "当前课表：${activeTt.semesterLabel.ifEmpty { activeTt.semester }} · ${activeTt.courses.size} 门课 · ${activeTt.source.ifEmpty { "内置快照" }}",
                            fontSize = 11.5.sp,
                            color = mu.accentInk,
                            fontWeight = FontWeight.Bold
                        )
                        OutlinedTextField(jwUser, { jwUser = it }, label = { Text("教务账号") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(jwPwd, { jwPwd = it }, label = { Text("教务密码") }, visualTransformation = PasswordVisualTransformation(), singleLine = true, modifier = Modifier.fillMaxWidth())
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(jwStart, { jwStart = it }, label = { Text("第1周周一 (兜底)") }, singleLine = true, modifier = Modifier.weight(1.4f))
                            OutlinedTextField(
                                jwWeeks,
                                { jwWeeks = it.filter { ch -> ch.isDigit() } },
                                label = { Text("总周数") },
                                singleLine = true,
                                modifier = Modifier.weight(0.8f)
                            )
                        }
                        if (state.timetable.lastError.isNotEmpty()) {
                            Text(state.timetable.lastError, color = mu.danger, fontSize = 12.sp)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = {
                                    vm.syncTimetable(
                                        user = jwUser,
                                        pwd = jwPwd,
                                        startMondayOverride = jwStart.trim(),
                                        totalWeeksOverride = jwWeeks.toIntOrNull() ?: 19
                                    )
                                },
                                modifier = Modifier.weight(1.4f)
                            ) {
                                Text("保存并拉取课表")
                            }
                            OutlinedButton(
                                onClick = { vm.testJwLogin(jwUser, jwPwd) },
                                modifier = Modifier.weight(0.9f)
                            ) {
                                Text("测登录")
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (state.timetable.data != null) {
                                TextButton(onClick = { vm.restoreBuiltinTimetable() }) {
                                    Text("恢复内置课表", color = mu.ink2, fontSize = 12.sp)
                                }
                            }
                            TextButton(onClick = { showJwTrace = !showJwTrace }) {
                                Text(if (showJwTrace) "▾ 收起同步日志" else "▸ 查看同步日志 (${state.timetable.trace.size})", fontSize = 12.sp)
                            }
                        }
                        AnimatedVisibility(visible = showJwTrace) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(mu.surface2)
                                    .padding(10.dp)
                            ) {
                                Text(
                                    text = if (state.timetable.trace.isEmpty()) "暂无同步日志" else state.timetable.trace.joinToString("\n"),
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp,
                                    lineHeight = 16.sp,
                                    color = mu.ink2
                                )
                            }
                        }
                    }
                }
            }

            // 后台检查与常驻
            item {
                MuSectionHeader("后台检查与常驻（原生服务）")
                Spacer(Modifier.height(6.dp))
                MuCard(padding = 14.dp) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("每 15 分钟检查云邮/门户新内容", modifier = Modifier.weight(1f), color = mu.ink, fontWeight = FontWeight.Bold)
                            Switch(
                                checked = state.settings.bgCheckOn,
                                onCheckedChange = { on ->
                                    vm.updateState { s -> s.copy(settings = s.settings.copy(bgCheckOn = on)) }
                                    if (on) vm.muBgCheck.enable(state.settings.bgCheckUc, state.settings.bgCheckPt, state.settings.bgCheckWifiOnly)
                                    else vm.muBgCheck.disable()
                                    bgStatus = vm.muBgCheck.status()
                                }
                            )
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("仅 Wi-Fi 下检查", modifier = Modifier.weight(1f), color = mu.ink2)
                            Switch(
                                checked = state.settings.bgCheckWifiOnly,
                                onCheckedChange = { w ->
                                    vm.updateState { s -> s.copy(settings = s.settings.copy(bgCheckWifiOnly = w)) }
                                    if (state.settings.bgCheckOn) vm.muBgCheck.enable(state.settings.bgCheckUc, state.settings.bgCheckPt, w)
                                }
                            )
                        }
                        Text(bgStatus, fontSize = 11.5.sp, color = mu.ink3)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { vm.toast(vm.muBgCheck.testNow()) }, modifier = Modifier.weight(1f)) { Text("立即检查一次") }
                            OutlinedButton(onClick = { vm.muKeepAlive.requestIgnoreBatteryOptimizations() }, modifier = Modifier.weight(1f)) { Text("电池白名单") }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("前台常驻保活通知", modifier = Modifier.weight(1f), color = mu.ink2)
                            Switch(
                                checked = state.settings.keepAliveOn,
                                onCheckedChange = { on ->
                                    vm.updateState { s -> s.copy(settings = s.settings.copy(keepAliveOn = on)) }
                                    if (on) vm.muKeepAlive.start("MU WORKBENCH 运行中", "正在守护待办与专注计时")
                                    else vm.muKeepAlive.stop()
                                }
                            )
                        }
                    }
                }
            }

            // 节假日与数据备份
            item {
                MuSectionHeader("节假日与数据备份")
                Spacer(Modifier.height(6.dp))
                MuCard(padding = 14.dp) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { vm.updateHolidaysOnline() }, modifier = Modifier.fillMaxWidth()) {
                            Text("联网更新节假日数据（去年/今年/明年）")
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = {
                                val res = JSONObject(vm.exportBackup())
                                vm.toast(if (res.optBoolean("ok")) "已保存到 ${res.optString("display")}" else "导出失败：${res.optString("msg")}")
                            }, modifier = Modifier.weight(1f)) { Text("导出备份 JSON") }
                            OutlinedButton(onClick = { importLauncher.launch(arrayOf("application/json", "*/*")) }, modifier = Modifier.weight(1f)) {
                                Text("导入备份")
                            }
                        }
                        Text("兼容从 v1.0.0–v2.27 导出的 MU-WORKBENCH-备份-*.json 文件。", fontSize = 11.5.sp, color = mu.ink3)
                        OutlinedButton(
                            onClick = {
                                vm.askConfirm("清空全部数据", "此操作不可恢复，确定清空全部待办、项目和设置吗？", "清空") {
                                    vm.clearAllData()
                                }
                            },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = mu.danger),
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("清空全部本地数据") }
                    }
                }
            }
        }
    }
}
