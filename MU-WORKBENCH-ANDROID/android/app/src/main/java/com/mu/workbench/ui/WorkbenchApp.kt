package com.mu.workbench.ui

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
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
import com.mu.workbench.ui.theme.MuMotion
import com.mu.workbench.ui.theme.MuWorkbenchTheme
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
    val useLightBars = mu.style == ThemeStyle.IOS && !mu.isDark
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
                    val snackContainer = when (mu.style) {
                        ThemeStyle.P3R -> mu.p3Deep
                        ThemeStyle.IOS -> if (mu.isDark) Color(0xF22C2C2E) else Color(0xEB1C1C1E)
                        ThemeStyle.P5R -> mu.chrome
                    }
                    Snackbar(
                        snackbarData = data,
                        containerColor = snackContainer,
                        contentColor = Color.White,
                        actionColor = if (mu.style == ThemeStyle.P3R) mu.p3Cyan else mu.accent2,
                        shape = RoundedCornerShape(
                            when (mu.style) {
                                ThemeStyle.IOS -> 999.dp
                                ThemeStyle.P3R -> 5.dp
                                ThemeStyle.P5R -> 0.dp
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
                    style = mu.style,
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
            enter = slideInHorizontally(animationSpec = MuMotion.intOffsetSpring(mu.style)) { it },
            exit = slideOutHorizontally(animationSpec = MuMotion.intOffsetSpring(mu.style)) { it }
        ) {
            Box(modifier = Modifier.fillMaxSize().background(mu.paper)) {
                MuPageBackground(chapterNumber = "07")
                SettingsScreen(vm = vm, onClose = { vm.closeSettings() })
            }
        }

        // 资讯全屏阅读页（纯不透明推入）
        AnimatedVisibility(
            visible = readerArticle != null,
            enter = slideInHorizontally(animationSpec = MuMotion.intOffsetSpring(mu.style)) { it },
            exit = slideOutHorizontally(animationSpec = MuMotion.intOffsetSpring(mu.style)) { it }
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
            enter = slideInVertically(animationSpec = MuMotion.intOffsetSpring(mu.style)) { it },
            exit = slideOutVertically(animationSpec = MuMotion.intOffsetSpring(mu.style)) { it }
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
                                    .clip(RoundedCornerShape(if (mu.style == ThemeStyle.P5R) 0.dp else 10.dp))
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
    val bgBrush = when (mu.style) {
        ThemeStyle.P3R -> Brush.horizontalGradient(listOf(Color(0xFF03164A), Color(0xFF0148A6), mu.chrome2))
        ThemeStyle.P5R -> Brush.horizontalGradient(listOf(mu.chrome, mu.chrome))
        ThemeStyle.IOS -> Brush.horizontalGradient(listOf(mu.chrome, mu.chrome))
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
            when (mu.style) {
                ThemeStyle.P5R -> {
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
                ThemeStyle.P3R -> {
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
                ThemeStyle.IOS -> {
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

            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "MU WORKBENCH",
                        color = mu.chromeInk,
                        fontWeight = FontWeight.Black,
                        fontStyle = if (mu.style != ThemeStyle.IOS) FontStyle.Italic else FontStyle.Normal,
                        fontSize = 15.sp
                    )
                    if (mu.style == ThemeStyle.P3R) {
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
                    showDot = hasUpdate || (pendingCount > 0 && mu.style == ThemeStyle.P5R),
                    onClick = onSettings
                )
            }
        }

        if (busy) {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth().height(2.5.dp),
                color = if (mu.style == ThemeStyle.P3R) mu.p3Cyan else mu.accent,
                trackColor = Color.Transparent
            )
        } else {
            HorizontalDivider(
                thickness = when (mu.style) {
                    ThemeStyle.P5R -> 2.5.dp
                    ThemeStyle.P3R -> 2.dp
                    ThemeStyle.IOS -> 0.5.dp
                },
                color = when (mu.style) {
                    ThemeStyle.P5R -> mu.accent
                    ThemeStyle.P3R -> mu.p3Cyan
                    ThemeStyle.IOS -> mu.line
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
    val shape = when (mu.style) {
        ThemeStyle.P5R -> RoundedCornerShape(0.dp)
        ThemeStyle.P3R -> RoundedCornerShape(4.dp)
        ThemeStyle.IOS -> RoundedCornerShape(10.dp)
    }
    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(shape)
            .then(
                when (mu.style) {
                    ThemeStyle.P5R -> Modifier.border(1.5.dp, mu.chromeInk, shape)
                    ThemeStyle.P3R -> Modifier.border(1.dp, Color.White.copy(alpha = 0.45f), shape)
                    ThemeStyle.IOS -> Modifier.background(mu.surface2.copy(alpha = 0.6f))
                }
            )
            .clickable(onClick = onClick),
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
    val topLineColor = when (mu.style) {
        ThemeStyle.P5R -> mu.accent
        ThemeStyle.P3R -> mu.p3Cyan.copy(alpha = 0.7f)
        ThemeStyle.IOS -> mu.line
    }
    val topLineThick = when (mu.style) {
        ThemeStyle.P5R -> 2.5.dp
        ThemeStyle.P3R -> 2.dp
        ThemeStyle.IOS -> 0.5.dp
    }
    val bgMod = when (mu.style) {
        ThemeStyle.P3R -> Modifier.background(Brush.verticalGradient(listOf(Color(0xFF03163D), Color(0xFF020B20))))
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
                    isOn && mu.style == ThemeStyle.P3R -> mu.p3Deep
                    isOn && mu.style == ThemeStyle.P5R -> Color.White
                    isOn -> mu.accent
                    mu.style == ThemeStyle.P3R -> Color(0xFFD4EEFF).copy(alpha = 0.65f)
                    else -> mu.chromeSoft
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .drawBehind {
                            if (isOn) {
                                val padX = 3.dp.toPx()
                                val padY = 2.dp.toPx()
                                val l = padX
                                val r = size.width - padX
                                val top = padY
                                val bot = size.height - padY
                                when (mu.style) {
                                    ThemeStyle.P5R -> {
                                        // P5R：红底斜切平行四边形 + 暗影
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
                                    ThemeStyle.P3R -> {
                                        // P3R：粉红错位伴影 + 纯白倒梯形白楔
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
                                    ThemeStyle.IOS -> {
                                        drawRoundRect(
                                            color = mu.accentSoft,
                                            topLeft = Offset(padX + 2.dp.toPx(), padY + 1.dp.toPx()),
                                            size = androidx.compose.ui.geometry.Size(r - l - 4.dp.toPx(), bot - top - 2.dp.toPx()),
                                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(12.dp.toPx(), 12.dp.toPx())
                                        )
                                    }
                                }
                            }
                        }
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { onSelect(t) }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                        Box(modifier = Modifier.size(22.dp), contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = icon,
                                contentDescription = t.label,
                                tint = itemContentColor,
                                modifier = Modifier.size(21.dp).align(Alignment.Center)
                            )
                            if (badgeCount > 0) {
                                val badgeBg = when {
                                    mu.style == ThemeStyle.P3R -> Color(0xFFF0182C)
                                    isOn && mu.style == ThemeStyle.P5R -> Color(0xFF0B0B0C)
                                    else -> mu.accent
                                }
                                val badgeText = if (badgeCount > 9) "9+" else "$badgeCount"
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .offset(x = 8.dp, y = (-5).dp)
                                        .clip(if (mu.style == ThemeStyle.IOS) RoundedCornerShape(999.dp) else parallelogramShape(0.2f))
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
