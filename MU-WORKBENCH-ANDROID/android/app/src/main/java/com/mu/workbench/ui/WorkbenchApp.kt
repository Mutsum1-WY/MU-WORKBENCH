package com.mu.workbench.ui

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mu.workbench.data.ThemeStyle
import com.mu.workbench.domain.AppUpdater
import com.mu.workbench.domain.TodoEngine
import com.mu.workbench.ui.components.MuPageBackground
import com.mu.workbench.ui.components.MuTabTransition
import com.mu.workbench.ui.components.ProjectEditorSheet
import com.mu.workbench.ui.components.TodoEditorSheet
import com.mu.workbench.ui.components.parallelogramShape
import com.mu.workbench.ui.screens.*
import com.mu.workbench.ui.theme.LocalMuColors
import com.mu.workbench.ui.theme.UiSkin
import com.mu.workbench.ui.theme.MuMotion
import com.mu.workbench.ui.theme.MuWorkbenchTheme
import com.mu.workbench.ui.theme.muPressTransform
import com.mu.workbench.ui.theme.muTactileClickable
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun WorkbenchApp(vm: WorkbenchViewModel = viewModel()) {
    val state by vm.state.collectAsState()

    MuWorkbenchTheme(style = state.settings.theme, accent = state.settings.accent) {
        WorkbenchShell(vm)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WorkbenchShell(vm: WorkbenchViewModel) {
    val mu = LocalMuColors.current
    val state by vm.state.collectAsState()
    val tab by vm.tab.collectAsState()
    val tabDir by vm.tabDir.collectAsState()
    val focusSub by vm.focusSub.collectAsState()
    val today by vm.nowDate.collectAsState()
    val showSettings by vm.showSettings.collectAsState()
    val showScan by vm.showScanOverlay.collectAsState()
    val readerArticle by vm.readerArticle.collectAsState()
    val readerLoading by vm.readerLoading.collectAsState()
    val showTodoEditor by vm.showTodoEditor.collectAsState()
    val editingTodo by vm.editingTodo.collectAsState()
    val showProjectEditor by vm.showProjectEditor.collectAsState()
    val editingProject by vm.editingProject.collectAsState()
    val confirmDialog by vm.confirmDialog.collectAsState()
    val updateState by vm.updateState.collectAsState()
    val busyLabel by vm.busyLabel.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }
    val view = LocalView.current
    val useLightBars = mu.skin == UiSkin.IOS && !mu.isDark
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            val ctrl = WindowCompat.getInsetsController(window, view)
            ctrl.isAppearanceLightStatusBars = useLightBars
            ctrl.isAppearanceLightNavigationBars = useLightBars
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    var wasStopped by remember { mutableStateOf(false) }

    DisposableEffect(lifecycleOwner) {
        val obs = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> {
                    wasStopped = true
                    vm.onAppBackgrounded()
                }
                Lifecycle.Event.ON_START -> {
                    val returning = wasStopped
                    wasStopped = false
                    vm.onAppForegrounded(fromBackground = returning)
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
    }

    LaunchedEffect(Unit) {
        vm.snackbars.collect { ev ->
            val res = snackbarHostState.showSnackbar(
                message = ev.message,
                actionLabel = ev.actionLabel,
                duration = SnackbarDuration.Short
            )
            if (res == SnackbarResult.ActionPerformed) {
                ev.onAction?.invoke()
            }
        }
    }

    val hasOverlay = showSettings || showScan || readerArticle != null || showTodoEditor || showProjectEditor || confirmDialog != null || updateState.showDialog || tab != MainTab.TODAY
    BackHandler(enabled = hasOverlay) {
        vm.handleBack()
    }

    val pendingCount = remember(state.todos, today, state.holidayManual, state.holidayCache) {
        TodoEngine.todayItems(state, vm.builtinHolidays, today).count { !it.isDone }
    }
    val unreadNews = remember(state.portal.notices, state.portal.lectures) {
        state.portal.notices.count { it.isNew } + state.portal.lectures.count { it.isNew }
    }
    val weekStr = today.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.CHINA)

    val chapterPair = when {
        showSettings -> "07" to "07 SYSTEM"
        tab == MainTab.TODAY -> "01" to "01 DAILY"
        tab == MainTab.CALENDAR -> "02" to "02 CALENDAR"
        tab == MainTab.FOCUS && focusSub == FocusSub.CLOCK -> "03" to "03 TIME"
        tab == MainTab.FOCUS -> "03" to "03 PROJECTS"
        tab == MainTab.NEWS -> "04" to "04 INFO"
        else -> "05" to "05 COURSE"
    }

    Box(modifier = Modifier.fillMaxSize().background(mu.paper)) {
        Scaffold(
            containerColor = mu.paper,
            snackbarHost = {
                SnackbarHost(snackbarHostState) { data ->
                    val snackContainer = when (mu.skin) {
                        UiSkin.P3R -> mu.p3Deep
                        UiSkin.IOS -> if (mu.isDark) Color(0xF22C2C2E) else Color(0xEB1C1C1E)
                        UiSkin.P5R -> mu.chrome
                    }
                    Snackbar(
                        snackbarData = data,
                        containerColor = snackContainer,
                        contentColor = Color.White,
                        actionColor = if (mu.skin == UiSkin.P3R) mu.p3Cyan else mu.accent2,
                        shape = RoundedCornerShape(
                            when (mu.skin) {
                                UiSkin.IOS -> 999.dp
                                UiSkin.P3R -> 5.dp
                                UiSkin.P5R -> 0.dp
                            }
                        )
                    )
                }
            },
            topBar = {
                MuTopBar(
                    todayText = "${today.monthValue}月${today.dayOfMonth}日 $weekStr · Jetpack 原生版",
                    chapterBadge = chapterPair.second,
                    pendingCount = pendingCount,
                    hasUpdate = updateState.hasUpdate,
                    busy = busyLabel != null,
                    onSettings = { vm.openSettings() },
                    onScan = { vm.openScanOverlay() }
                )
            },
            bottomBar = {
                MuBottomBar(
                    selected = tab,
                    pendingCount = pendingCount,
                    unreadNews = unreadNews,
                    onSelect = { vm.selectTab(it) }
                )
            }
        ) { innerPad ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPad)
                    .background(mu.paper)
                    .graphicsLayer { clip = true }
            ) {
                MuTabTransition(
                    targetState = tab,
                    direction = tabDir,
                    style = mu.skin,
                    modifier = Modifier.fillMaxSize()
                ) { cur ->
                    val ch = when (cur) {
                        MainTab.TODAY -> "01"
                        MainTab.CALENDAR -> "02"
                        MainTab.FOCUS -> "03"
                        MainTab.NEWS -> "04"
                        MainTab.COURSE -> "05"
                    }
                    // 每页自带不透明主题背景，彻底消除切屏叠影与连点卡屏
                    Box(modifier = Modifier.fillMaxSize().background(mu.paper)) {
                        MuPageBackground(chapterNumber = ch)
                        when (cur) {
                            MainTab.TODAY -> TodayScreen(vm)
                            MainTab.CALENDAR -> CalendarScreen(vm)
                            MainTab.FOCUS -> FocusScreen(vm)
                            MainTab.NEWS -> NewsScreen(vm)
                            MainTab.COURSE -> CourseScreen(vm)
                        }
                    }
                }
            }
        }

        // 设置页（纯不透明推入，无残影）
        AnimatedVisibility(
            visible = showSettings,
            enter = slideInHorizontally(animationSpec = MuMotion.intOffsetSpring(mu.skin)) { it },
            exit = slideOutHorizontally(animationSpec = MuMotion.intOffsetSpring(mu.skin)) { it }
        ) {
            Box(modifier = Modifier.fillMaxSize().background(mu.paper)) {
                MuPageBackground(chapterNumber = "07")
                SettingsScreen(vm = vm, onClose = { vm.closeSettings() })
            }
        }

        // 资讯全屏阅读页（纯不透明推入）
        AnimatedVisibility(
            visible = readerArticle != null,
            enter = slideInHorizontally(animationSpec = MuMotion.intOffsetSpring(mu.skin)) { it },
            exit = slideOutHorizontally(animationSpec = MuMotion.intOffsetSpring(mu.skin)) { it }
        ) {
            readerArticle?.let { art ->
                val dlStates by vm.dlStates.collectAsState()
                val refreshSeq by vm.readerRefreshSeq.collectAsState()
                Box(modifier = Modifier.fillMaxSize().background(mu.paper)) {
                    ReaderOverlay(
                        article = art,
                        loading = readerLoading,
                        refreshSeq = refreshSeq,
                        dlStates = dlStates,
                        onLoadImage = { imgUrl, altUrl, force ->
                            vm.loadPortalImage(imgUrl, altUrl, art.url, force)
                        },
                        onRefresh = { vm.refreshReaderArticle() },
                        onDownloadAttachment = { att -> vm.downloadPortalAttachment(att, art.url) },
                        onClose = { vm.closeReader() },
                        onOpenBrowser = { vm.openExternalUrl(it) }
                    )
                }
            }
        }

        // 顶栏快速扫码覆盖页（自底全屏升起）
        AnimatedVisibility(
            visible = showScan,
            enter = slideInVertically(animationSpec = MuMotion.intOffsetSpring(mu.skin)) { it },
            exit = slideOutVertically(animationSpec = MuMotion.intOffsetSpring(mu.skin)) { it }
        ) {
            Scaffold(
                containerColor = mu.paper,
                topBar = {
                    TopAppBar(
                        title = { Text("扫码签到", fontWeight = FontWeight.ExtraBold) },
                        navigationIcon = {
                            IconButton(onClick = { vm.closeScanOverlay() }) {
                                Icon(Icons.Rounded.Close, contentDescription = "关闭")
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
                Box(modifier = Modifier.fillMaxSize().padding(pad).background(mu.paper)) {
                    ScanPane(vm = vm)
                }
            }
        }

        // 待办编辑 ModalBottomSheet
        if (showTodoEditor && editingTodo != null) {
            TodoEditorSheet(
                initial = editingTodo!!,
                onDismiss = { vm.closeTodoEditor() },
                onSave = { vm.saveTodo(it) },
                onDelete = if (editingTodo!!.id.isNotEmpty()) {
                    {
                        val target = editingTodo!!
                        vm.closeTodoEditor()
                        vm.deleteTodoWithUndo(target)
                    }
                } else null
            )
        }

        // 项目编辑 ModalBottomSheet
        if (showProjectEditor && editingProject != null) {
            ProjectEditorSheet(
                initial = editingProject!!,
                onDismiss = { vm.closeProjectEditor() },
                onSave = { vm.saveProject(it) },
                onDelete = if (editingProject!!.id.isNotEmpty()) {
                    {
                        val target = editingProject!!
                        vm.askConfirm("删除项目卡", "确定删除「${target.title}」吗？") {
                            vm.closeProjectEditor()
                            vm.deleteProject(target.id)
                        }
                    }
                } else null
            )
        }

        // 确认弹窗
        confirmDialog?.let { dlg ->
            AlertDialog(
                onDismissRequest = { vm.dismissConfirm() },
                containerColor = mu.surface1,
                titleContentColor = mu.ink,
                textContentColor = mu.ink2,
                tonalElevation = 0.dp,
                shape = RoundedCornerShape(mu.cardRadius),
                title = { Text(dlg.title, fontWeight = FontWeight.ExtraBold) },
                text = { Text(dlg.message) },
                confirmButton = {
                    Button(
                        onClick = {
                            vm.dismissConfirm()
                            dlg.onConfirm()
                        },
                        shape = RoundedCornerShape(mu.cardRadius),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (dlg.isDanger) mu.danger else mu.accent,
                            contentColor = Color.White
                        )
                    ) { Text(dlg.confirmLabel, fontWeight = FontWeight.Bold) }
                },
                dismissButton = {
                    TextButton(
                        onClick = { vm.dismissConfirm() },
                        colors = ButtonDefaults.textButtonColors(contentColor = mu.ink2)
                    ) { Text("取消") }
                }
            )
        }

        // 应用更新弹窗
        if (updateState.showDialog && updateState.latest != null) {
            val rel = updateState.latest!!
            val sizeText = AppUpdater.formatBytes(rel.apkSize)
            val metaParts = buildList {
                if (rel.publishedDate.isNotEmpty()) add("发布于 ${rel.publishedDate}")
                if (sizeText.isNotEmpty()) add("大小 $sizeText")
            }
            AlertDialog(
                onDismissRequest = { vm.dismissUpdateDialog() },
                containerColor = mu.surface1,
                titleContentColor = mu.ink,
                textContentColor = mu.ink2,
                tonalElevation = 0.dp,
                shape = RoundedCornerShape(mu.cardRadius),
                title = {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("发现新版本 v${rel.versionName}", fontWeight = FontWeight.ExtraBold)
                        Text(
                            "当前 v${updateState.currentVersion} → 新版 v${rel.versionName}",
                            fontSize = 12.sp,
                            color = mu.accentInk,
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (metaParts.isNotEmpty()) {
                            Text(metaParts.joinToString(" · "), fontSize = 11.5.sp, color = mu.ink3)
                        }
                        if (rel.notes.isNotEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 200.dp)
                                    .clip(RoundedCornerShape(if (mu.skin == UiSkin.P5R) 0.dp else 10.dp))
                                    .background(mu.surface2)
                                    .padding(10.dp)
                                    .verticalScroll(rememberScrollState())
                            ) {
                                Text(rel.notes, fontSize = 12.5.sp, lineHeight = 18.sp, color = mu.ink2)
                            }
                        }
                        if (updateState.downloading) {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                if (updateState.totalBytes > 0L) {
                                    LinearProgressIndicator(
                                        progress = { (updateState.downloadPct / 100f).coerceIn(0f, 1f) },
                                        modifier = Modifier.fillMaxWidth(),
                                        color = mu.accent,
                                        trackColor = mu.surface3
                                    )
                                    Text(
                                        "正在下载 ${updateState.downloadPct}%（${AppUpdater.formatBytes(updateState.downloadedBytes)} / ${AppUpdater.formatBytes(updateState.totalBytes)}）",
                                        fontSize = 11.5.sp,
                                        color = mu.accentInk,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                } else {
                                    LinearProgressIndicator(
                                        modifier = Modifier.fillMaxWidth(),
                                        color = mu.accent,
                                        trackColor = mu.surface3
                                    )
                                    val dlStr = AppUpdater.formatBytes(updateState.downloadedBytes)
                                    Text(
                                        if (dlStr.isNotEmpty()) "正在下载 $dlStr..." else "正在连接下载服务器...",
                                        fontSize = 11.5.sp,
                                        color = mu.accentInk,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        } else if (updateState.downloadedFilePath != null) {
                            Text(
                                "安装包已就绪，点击下方「立即安装」开始更新",
                                fontSize = 12.sp,
                                color = mu.ok,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        if (updateState.error != null) {
                            Text(updateState.error!!, fontSize = 12.sp, color = mu.danger)
                        }
                        Text(
                            "在浏览器中打开 Release 页面",
                            fontSize = 12.sp,
                            color = mu.accentInk,
                            textDecoration = TextDecoration.Underline,
                            modifier = Modifier.clickable { vm.openExternalUrl(rel.htmlUrl) }
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            when {
                                updateState.downloadedFilePath != null -> vm.installDownloadedUpdate()
                                updateState.downloading -> vm.cancelUpdateDownload()
                                rel.apkUrl.isNotEmpty() -> vm.startUpdateDownload()
                                else -> vm.openExternalUrl(rel.htmlUrl)
                            }
                        },
                        shape = RoundedCornerShape(mu.cardRadius),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (updateState.downloading) mu.danger else mu.accent,
                            contentColor = Color.White
                        )
                    ) {
                        Text(
                            when {
                                updateState.downloadedFilePath != null -> "立即安装"
                                updateState.downloading -> "取消下载"
                                rel.apkUrl.isNotEmpty() -> "立即更新"
                                else -> "前往下载页"
                            },
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                dismissButton = {
                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        if (!updateState.downloading && updateState.downloadedFilePath == null) {
                            TextButton(
                                onClick = { vm.ignoreCurrentUpdate() },
                                colors = ButtonDefaults.textButtonColors(contentColor = mu.ink3)
                            ) { Text("忽略此版") }
                        }
                        TextButton(
                            onClick = { vm.dismissUpdateDialog() },
                            colors = ButtonDefaults.textButtonColors(contentColor = mu.ink2)
                        ) { Text(if (updateState.downloading) "后台下载" else "稍后再说") }
                    }
                }
            )
        }
    }
}

@Composable
private fun MuTopBar(
    todayText: String,
    chapterBadge: String,
    pendingCount: Int,
    hasUpdate: Boolean,
    busy: Boolean,
    onSettings: () -> Unit,
    onScan: () -> Unit
) {
    val mu = LocalMuColors.current
    val bgBrush = when (mu.skin) {
        UiSkin.P3R -> Brush.horizontalGradient(listOf(Color(0xFF03164A), Color(0xFF0148A6), mu.chrome2))
        UiSkin.P5R -> Brush.horizontalGradient(listOf(mu.chrome, mu.chrome))
        UiSkin.IOS -> Brush.horizontalGradient(listOf(mu.chrome, mu.chrome))
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(bgBrush)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 主题化 Logo
            when (mu.skin) {
                UiSkin.P5R -> {
                    Box(
                        modifier = Modifier
                            .padding(end = 3.dp, bottom = 3.dp)
                            .drawBehind {
                                val d = 3.dp.toPx()
                                drawRect(Color.Black.copy(alpha = 0.65f), topLeft = Offset(d, d), size = size)
                            }
                            .size(32.dp)
                            .graphicsLayer { rotationZ = -6f }
                            .background(mu.accent)
                            .border(2.dp, Color.White),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "MU",
                            color = Color.White,
                            fontWeight = FontWeight.Black,
                            fontStyle = FontStyle.Italic,
                            fontSize = 13.sp
                        )
                    }
                }
                UiSkin.P3R -> {
                    Box(
                        modifier = Modifier
                            .padding(end = 3.dp, bottom = 3.dp)
                            .drawBehind {
                                val d = 3.dp.toPx()
                                drawRect(mu.p3Pink, topLeft = Offset(d, d), size = size)
                            }
                            .size(32.dp)
                            .graphicsLayer { rotationZ = -4f }
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color.White),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "MU",
                            color = mu.accent,
                            fontWeight = FontWeight.Black,
                            fontStyle = FontStyle.Italic,
                            fontSize = 13.sp
                        )
                    }
                }
                UiSkin.IOS -> when (mu.style) {
                    ThemeStyle.MINIMAL -> {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(mu.ink),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("MU", color = mu.paper, fontWeight = FontWeight.Black, fontSize = 13.sp)
                        }
                    }
                    ThemeStyle.BRUTAL -> {
                        Box(
                            modifier = Modifier
                                .padding(end = 3.dp, bottom = 3.dp)
                                .drawBehind {
                                    val d = 3.dp.toPx()
                                    drawRect(mu.hardShadowColor, topLeft = Offset(d, d), size = size)
                                }
                                .size(32.dp)
                                .background(mu.accent)
                                .border(2.5.dp, mu.ink),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("MU", color = mu.ink, fontWeight = FontWeight.Black, fontSize = 13.sp)
                        }
                    }
                    ThemeStyle.CLAY -> {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .shadow(6.dp, CircleShape, spotColor = mu.hardShadowColor)
                                .clip(CircleShape)
                                .background(Brush.verticalGradient(listOf(mu.accent2, mu.accent))),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("MU", color = Color.White, fontWeight = FontWeight.Black, fontSize = 13.sp)
                        }
                    }
                    ThemeStyle.OLED -> {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(RoundedCornerShape(9.dp))
                                .background(mu.surface2)
                                .border(1.dp, mu.accent.copy(alpha = 0.6f), RoundedCornerShape(9.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("MU", color = mu.accent2, fontWeight = FontWeight.Black, fontSize = 13.sp)
                        }
                    }
                    else -> {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(RoundedCornerShape(9.dp))
                                .background(Brush.linearGradient(listOf(mu.accent, mu.accent2))),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("MU", color = Color.White, fontWeight = FontWeight.Black, fontSize = 13.sp)
                        }
                    }
                }
            }

            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "MU WORKBENCH",
                        color = mu.chromeInk,
                        fontWeight = if (mu.style == ThemeStyle.MINIMAL) FontWeight.Medium else FontWeight.Black,
                        fontStyle = if (mu.skin != UiSkin.IOS) FontStyle.Italic else FontStyle.Normal,
                        letterSpacing = if (mu.style == ThemeStyle.MINIMAL) 2.sp else 0.sp,
                        fontSize = 15.sp
                    )
                    if (mu.skin == UiSkin.P3R) {
                        Box(
                            modifier = Modifier
                                .clip(parallelogramShape(0.22f))
                                .background(Color.White.copy(alpha = 0.16f))
                                .padding(horizontal = 7.dp, vertical = 1.dp)
                        ) {
                            Text(
                                text = chapterBadge,
                                color = Color(0xFF77FEFC),
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.Black,
                                fontStyle = FontStyle.Italic
                            )
                        }
                    }
                }
                Text(
                    text = todayText,
                    color = mu.chromeSoft,
                    fontSize = 11.sp
                )
            }

            // 右侧扫码与设置按钮（保留 P5R/P3R 线框质感 + 红点提醒）
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                MuTopActionBtn(
                    icon = { Icon(Icons.Rounded.QrCodeScanner, contentDescription = "扫码签到", tint = mu.chromeInk, modifier = Modifier.size(20.dp)) },
                    showDot = false,
                    onClick = onScan
                )
                MuTopActionBtn(
                    icon = { Icon(Icons.Rounded.Settings, contentDescription = "设置", tint = mu.chromeInk, modifier = Modifier.size(20.dp)) },
                    showDot = hasUpdate || (pendingCount > 0 && mu.skin == UiSkin.P5R),
                    onClick = onSettings
                )
            }
        }

        if (busy) {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth().height(2.5.dp),
                color = if (mu.skin == UiSkin.P3R) mu.p3Cyan else mu.accent,
                trackColor = Color.Transparent
            )
        } else {
            HorizontalDivider(
                thickness = when {
                    mu.skin == UiSkin.P5R -> 2.5.dp
                    mu.skin == UiSkin.P3R -> 2.dp
                    mu.style == ThemeStyle.BRUTAL -> 2.5.dp
                    mu.style == ThemeStyle.MINIMAL -> 1.dp
                    else -> 0.5.dp
                },
                color = when {
                    mu.skin == UiSkin.P5R -> mu.accent
                    mu.skin == UiSkin.P3R -> mu.p3Cyan
                    mu.style == ThemeStyle.BRUTAL -> mu.ink
                    else -> mu.line
                }
            )
        }
    }
}

@Composable
private fun MuTopActionBtn(
    icon: @Composable () -> Unit,
    showDot: Boolean,
    onClick: () -> Unit
) {
    val mu = LocalMuColors.current
    val shape = when {
        mu.skin == UiSkin.P5R -> RoundedCornerShape(0.dp)
        mu.skin == UiSkin.P3R -> RoundedCornerShape(4.dp)
        mu.style == ThemeStyle.BRUTAL -> RoundedCornerShape(8.dp)
        mu.style == ThemeStyle.MINIMAL -> RoundedCornerShape(4.dp)
        mu.style == ThemeStyle.CLAY -> CircleShape
        else -> RoundedCornerShape(10.dp)
    }
    val styleMod = when (mu.skin) {
        UiSkin.P5R -> Modifier.size(34.dp).clip(shape).border(1.5.dp, mu.chromeInk, shape)
        UiSkin.P3R -> Modifier.size(34.dp).clip(shape).border(1.dp, Color.White.copy(alpha = 0.45f), shape)
        UiSkin.IOS -> when (mu.style) {
            ThemeStyle.BRUTAL -> Modifier
                .padding(end = 2.dp, bottom = 2.dp)
                .size(34.dp)
                .drawBehind {
                    val d = 2.dp.toPx()
                    val cr = CornerRadius(8.dp.toPx(), 8.dp.toPx())
                    drawRoundRect(mu.hardShadowColor, topLeft = Offset(d, d), size = size, cornerRadius = cr)
                }
                .clip(shape)
                .background(Color.White)
                .border(2.dp, mu.ink, shape)
            ThemeStyle.MINIMAL -> Modifier
                .size(34.dp)
                .clip(shape)
                .border(1.dp, mu.line, shape)
            ThemeStyle.CLAY -> Modifier
                .size(34.dp)
                .shadow(6.dp, shape, spotColor = mu.hardShadowColor)
                .clip(shape)
                .background(mu.surface1)
            ThemeStyle.GLASS -> Modifier
                .size(34.dp)
                .clip(shape)
                .background(Color.White.copy(alpha = if (mu.isDark) 0.14f else 0.50f))
                .border(1.dp, Color.White.copy(alpha = if (mu.isDark) 0.25f else 0.70f), shape)
            ThemeStyle.OLED -> Modifier
                .size(34.dp)
                .clip(shape)
                .background(mu.surface2)
            else -> Modifier
                .size(34.dp)
                .clip(shape)
                .background(mu.surface2.copy(alpha = 0.6f))
        }
    }
    Box(
        modifier = styleMod.muTactileClickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        icon()
        if (showDot) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
                    .size(7.dp)
                    .background(mu.accent)
                    .border(1.dp, mu.chrome)
            )
        }
    }
}

@Composable
private fun MuBottomBar(
    selected: MainTab,
    pendingCount: Int,
    unreadNews: Int,
    onSelect: (MainTab) -> Unit
) {
    val mu = LocalMuColors.current
    val haptic = LocalHapticFeedback.current
    val topLineColor = when {
        mu.skin == UiSkin.P5R -> mu.accent
        mu.skin == UiSkin.P3R -> mu.p3Cyan.copy(alpha = 0.7f)
        mu.style == ThemeStyle.BRUTAL -> mu.ink
        else -> mu.line
    }
    val topLineThick = when {
        mu.skin == UiSkin.P5R -> 2.5.dp
        mu.skin == UiSkin.P3R -> 2.dp
        mu.style == ThemeStyle.BRUTAL -> 2.5.dp
        else -> 0.5.dp
    }
    val bgMod = when (mu.skin) {
        UiSkin.P3R -> Modifier.background(Brush.verticalGradient(listOf(Color(0xFF03163D), Color(0xFF020B20))))
        else -> Modifier.background(mu.chrome)
    }

    Column(modifier = Modifier.fillMaxWidth().then(bgMod)) {
        HorizontalDivider(thickness = topLineThick, color = topLineColor)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .height(58.dp)
                .padding(horizontal = 4.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            MainTab.entries.forEach { t ->
                val isOn = t == selected
                val src = remember(t) { MutableInteractionSource() }
                val iconScale by animateFloatAsState(
                    targetValue = if (isOn) 1.12f else 1f,
                    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = 680f),
                    label = "tabIconScale"
                )
                val icon = when (t) {
                    MainTab.TODAY -> Icons.Rounded.CheckCircle
                    MainTab.CALENDAR -> Icons.Rounded.CalendarMonth
                    MainTab.FOCUS -> Icons.Rounded.AccessTime
                    MainTab.NEWS -> Icons.Rounded.Newspaper
                    MainTab.COURSE -> Icons.AutoMirrored.Rounded.MenuBook
                }
                val badgeCount = when (t) {
                    MainTab.TODAY -> pendingCount
                    MainTab.NEWS -> unreadNews
                    else -> 0
                }

                val itemContentColor = when {
                    isOn && mu.skin == UiSkin.P3R -> mu.p3Deep
                    isOn && mu.skin == UiSkin.P5R -> Color.White
                    isOn && (mu.style == ThemeStyle.MINIMAL || mu.style == ThemeStyle.BRUTAL) -> mu.ink
                    isOn -> mu.accent
                    mu.skin == UiSkin.P3R -> Color(0xFFD4EEFF).copy(alpha = 0.65f)
                    else -> mu.chromeSoft
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .muPressTransform(src)
                        .drawBehind {
                            if (isOn) {
                                val padX = 3.dp.toPx()
                                val padY = 2.dp.toPx()
                                val l = padX
                                val r = size.width - padX
                                val top = padY
                                val bot = size.height - padY
                                when (mu.skin) {
                                    UiSkin.P5R -> {
                                        val dx = (bot - top) * 0.16f
                                        val p = Path().apply {
                                            moveTo(l + dx, top)
                                            lineTo(r, top)
                                            lineTo(r - dx, bot)
                                            lineTo(l, bot)
                                            close()
                                        }
                                        drawPath(p, mu.accent)
                                    }
                                    UiSkin.P3R -> {
                                        val inset = (r - l) * 0.16f
                                        fun wedge(dx: Float, dy: Float): Path = Path().apply {
                                            moveTo(l + dx, top + dy)
                                            lineTo(r + dx, top + dy)
                                            lineTo(r - inset + dx, bot + dy)
                                            lineTo(l + inset + dx, bot + dy)
                                            close()
                                        }
                                        drawPath(wedge(-3.dp.toPx(), 2.dp.toPx()), mu.p3Pink)
                                        drawPath(wedge(0f, 0f), Color.White)
                                    }
                                    UiSkin.IOS -> when (mu.style) {
                                        ThemeStyle.MINIMAL -> {
                                            val bw = size.width * 0.40f
                                            drawRect(
                                                color = mu.ink,
                                                topLeft = Offset((size.width - bw) / 2f, 0f),
                                                size = Size(bw, 2.dp.toPx())
                                            )
                                        }
                                        ThemeStyle.BRUTAL -> {
                                            val boxL = padX + 2.dp.toPx()
                                            val boxT = padY + 1.dp.toPx()
                                            val boxW = r - l - 4.dp.toPx()
                                            val boxH = bot - top - 3.dp.toPx()
                                            val cr = CornerRadius(8.dp.toPx(), 8.dp.toPx())
                                            val sh = 2.dp.toPx()
                                            drawRoundRect(
                                                color = mu.hardShadowColor,
                                                topLeft = Offset(boxL + sh, boxT + sh),
                                                size = Size(boxW, boxH),
                                                cornerRadius = cr
                                            )
                                            drawRoundRect(
                                                color = mu.accent2,
                                                topLeft = Offset(boxL, boxT),
                                                size = Size(boxW, boxH),
                                                cornerRadius = cr
                                            )
                                            drawRoundRect(
                                                color = mu.ink,
                                                topLeft = Offset(boxL, boxT),
                                                size = Size(boxW, boxH),
                                                cornerRadius = cr,
                                                style = Stroke(2.dp.toPx())
                                            )
                                        }
                                        ThemeStyle.CLAY -> {
                                            val boxL = padX + 2.dp.toPx()
                                            val boxT = padY + 1.dp.toPx()
                                            val boxW = r - l - 4.dp.toPx()
                                            val boxH = bot - top - 3.dp.toPx()
                                            val cr = CornerRadius(boxH / 2f, boxH / 2f)
                                            drawRoundRect(
                                                color = mu.hardShadowColor,
                                                topLeft = Offset(boxL, boxT + 2.dp.toPx()),
                                                size = Size(boxW, boxH),
                                                cornerRadius = cr
                                            )
                                            drawRoundRect(
                                                color = mu.surface1,
                                                topLeft = Offset(boxL, boxT),
                                                size = Size(boxW, boxH),
                                                cornerRadius = cr
                                            )
                                        }
                                        ThemeStyle.GLASS -> {
                                            val boxL = padX + 2.dp.toPx()
                                            val boxT = padY + 1.dp.toPx()
                                            val boxW = r - l - 4.dp.toPx()
                                            val boxH = bot - top - 2.dp.toPx()
                                            val cr = CornerRadius(12.dp.toPx(), 12.dp.toPx())
                                            drawRoundRect(
                                                color = Color.White.copy(alpha = if (mu.isDark) 0.16f else 0.55f),
                                                topLeft = Offset(boxL, boxT),
                                                size = Size(boxW, boxH),
                                                cornerRadius = cr
                                            )
                                            drawRoundRect(
                                                color = Color.White.copy(alpha = if (mu.isDark) 0.35f else 0.80f),
                                                topLeft = Offset(boxL, boxT),
                                                size = Size(boxW, boxH),
                                                cornerRadius = cr,
                                                style = Stroke(1.dp.toPx())
                                            )
                                        }
                                        ThemeStyle.OLED -> {
                                            val boxL = padX + 2.dp.toPx()
                                            val boxT = padY + 1.dp.toPx()
                                            val boxW = r - l - 4.dp.toPx()
                                            val boxH = bot - top - 2.dp.toPx()
                                            val cr = CornerRadius(12.dp.toPx(), 12.dp.toPx())
                                            drawRoundRect(
                                                color = mu.accent.copy(alpha = 0.18f),
                                                topLeft = Offset(boxL, boxT),
                                                size = Size(boxW, boxH),
                                                cornerRadius = cr
                                            )
                                            drawCircle(
                                                color = mu.accent,
                                                radius = 1.5.dp.toPx(),
                                                center = Offset(size.width / 2f, boxT + boxH - 4.dp.toPx())
                                            )
                                        }
                                        else -> {
                                            drawRoundRect(
                                                color = mu.accentSoft,
                                                topLeft = Offset(padX + 2.dp.toPx(), padY + 1.dp.toPx()),
                                                size = Size(r - l - 4.dp.toPx(), bot - top - 2.dp.toPx()),
                                                cornerRadius = CornerRadius(12.dp.toPx(), 12.dp.toPx())
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        .clickable(
                            interactionSource = src,
                            indication = LocalIndication.current,
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onSelect(t)
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                        Box(
                            modifier = Modifier
                                .size(22.dp)
                                .graphicsLayer {
                                    scaleX = iconScale
                                    scaleY = iconScale
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = icon,
                                contentDescription = t.label,
                                tint = itemContentColor,
                                modifier = Modifier.size(21.dp).align(Alignment.Center)
                            )
                            if (badgeCount > 0) {
                                val badgeBg = when {
                                    mu.skin == UiSkin.P3R -> Color(0xFFF0182C)
                                    isOn && mu.skin == UiSkin.P5R -> Color(0xFF0B0B0C)
                                    else -> mu.accent
                                }
                                val badgeText = if (badgeCount > 9) "9+" else "$badgeCount"
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .offset(x = 8.dp, y = (-5).dp)
                                        .clip(if (mu.skin == UiSkin.IOS) RoundedCornerShape(999.dp) else parallelogramShape(0.2f))
                                        .background(badgeBg)
                                        .padding(horizontal = 4.dp, vertical = 1.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = badgeText,
                                        color = Color.White,
                                        fontSize = 8.5.sp,
                                        lineHeight = 10.sp,
                                        maxLines = 1,
                                        softWrap = false,
                                        fontWeight = FontWeight.Black
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = t.label,
                            color = itemContentColor,
                            fontSize = 10.5.sp,
                            lineHeight = 13.sp,
                            maxLines = 1,
                            softWrap = false,
                            fontWeight = if (isOn) FontWeight.Black else FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}
