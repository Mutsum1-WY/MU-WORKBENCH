package com.mu.workbench.ui

import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.content.FileProvider
import androidx.core.content.pm.PackageInfoCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mu.workbench.MuBgCheck
import com.mu.workbench.MuKeepAlive
import com.mu.workbench.MuNative
import com.mu.workbench.alarm.NotificationHelper
import com.mu.workbench.data.*
import com.mu.workbench.domain.AppUpdater
import com.mu.workbench.domain.ByrDocsClient
import com.mu.workbench.domain.NativeClients
import com.mu.workbench.domain.ScheduleEngine
import com.mu.workbench.domain.TodoEngine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID

enum class MainTab(val index: Int, val label: String) {
    TODAY(0, "今日"),
    CALENDAR(1, "日历"),
    FOCUS(2, "专注"),
    NEWS(3, "资讯"),
    COURSE(4, "课程")
}

enum class FocusSub(val label: String) { CLOCK("时钟"), PROJECTS("项目") }

enum class CourseSub(val index: Int, val label: String) {
    COURSE(0, "课程性价比"),
    BD(1, "找资料"),
    BONUS(2, "课外加分"),
    SCAN(3, "扫码签到"),
    HW(4, "作业档案"),
    ZZ(5, "综测")
}

data class SnackbarEvent(
    val message: String,
    val actionLabel: String? = null,
    val onAction: (() -> Unit)? = null
)

data class ConfirmDialogState(
    val title: String,
    val message: String,
    val confirmLabel: String = "确定",
    val isDanger: Boolean = true,
    val onConfirm: () -> Unit
)

data class PomoRuntime(
    val isFocus: Boolean = true,
    val running: Boolean = false,
    val paused: Boolean = false,
    val leftSec: Int = 25 * 60,
    val totalSec: Int = 25 * 60,
    val linkedTodoId: String = "",
    val endsAtMs: Long = 0L
)

data class SwRuntime(
    val running: Boolean = false,
    val elapsedMs: Long = 0L,
    val lastTickMs: Long = 0L,
    val cdMinutes: Int = 10,
    val cdRunning: Boolean = false,
    val cdLeftSec: Int = 10 * 60,
    val cdEndsAtMs: Long = 0L
)

data class DlStatus(
    val pct: Int = 0,
    val loading: Boolean = false,
    val uri: String? = null,
    val err: String? = null
)

data class AppUpdateState(
    val currentVersion: String = "",
    val currentVersionCode: Long = 0L,
    val checking: Boolean = false,
    val checkedOnce: Boolean = false,
    val latest: AppUpdater.ReleaseInfo? = null,
    val hasUpdate: Boolean = false,
    val downloading: Boolean = false,
    val downloadPct: Int = 0,
    val downloadedBytes: Long = 0L,
    val totalBytes: Long = 0L,
    val downloadedFilePath: String? = null,
    val error: String? = null,
    val showDialog: Boolean = false
)

class WorkbenchViewModel(app: Application) : AndroidViewModel(app) {

    private val ctx = app.applicationContext
    private val repo = WorkbenchRepository(ctx)
    val muNative = MuNative(ctx)
    val muKeepAlive = MuKeepAlive(ctx)
    val muBgCheck = MuBgCheck(ctx)

    val builtinHolidays: Map<String, HolidayEntry> = AssetsLoader.loadBuiltinHolidays(ctx)
    val builtinTimetable: TimetableData = AssetsLoader.loadBuiltinTimetable(ctx)
    val courseCatalog: CourseCatalog = AssetsLoader.loadCourseCatalog(ctx)
    val bonusData: BonusData = AssetsLoader.loadBonusData(ctx)
    val zzData: ZzData = AssetsLoader.loadZzData(ctx)

    private val _state = MutableStateFlow(repo.load())
    val state: StateFlow<WorkbenchState> = _state.asStateFlow()

    private val _tab = MutableStateFlow(MainTab.TODAY)
    val tab: StateFlow<MainTab> = _tab.asStateFlow()

    private val _tabDir = MutableStateFlow(0)
    val tabDir: StateFlow<Int> = _tabDir.asStateFlow()

    private val _focusSub = MutableStateFlow(FocusSub.CLOCK)
    val focusSub: StateFlow<FocusSub> = _focusSub.asStateFlow()

    private val _courseSub = MutableStateFlow(CourseSub.COURSE)
    val courseSub: StateFlow<CourseSub> = _courseSub.asStateFlow()

    private val _courseSubDir = MutableStateFlow(0)
    val courseSubDir: StateFlow<Int> = _courseSubDir.asStateFlow()

    private val _showSettings = MutableStateFlow(false)
    val showSettings: StateFlow<Boolean> = _showSettings.asStateFlow()

    private val _showScanOverlay = MutableStateFlow(false)
    val showScanOverlay: StateFlow<Boolean> = _showScanOverlay.asStateFlow()

    private val _readerArticle = MutableStateFlow<PortalArticle?>(null)
    val readerArticle: StateFlow<PortalArticle?> = _readerArticle.asStateFlow()

    private val _readerLoading = MutableStateFlow(false)
    val readerLoading: StateFlow<Boolean> = _readerLoading.asStateFlow()

    private val _readerRefreshSeq = MutableStateFlow(0)
    val readerRefreshSeq: StateFlow<Int> = _readerRefreshSeq.asStateFlow()

    private val portalImgCache = LruCache<String, List<ImageBitmap>>(48)
    private val portalImgSemaphore = Semaphore(3)
    private val ucloudDetailInFlight = mutableSetOf<String>()
    private val ucloudDetailSemaphore = Semaphore(2)

    private val _editingTodo = MutableStateFlow<TodoItem?>(null)
    val editingTodo: StateFlow<TodoItem?> = _editingTodo.asStateFlow()

    private val _showTodoEditor = MutableStateFlow(false)
    val showTodoEditor: StateFlow<Boolean> = _showTodoEditor.asStateFlow()

    private val _editingProject = MutableStateFlow<ProjectCard?>(null)
    val editingProject: StateFlow<ProjectCard?> = _editingProject.asStateFlow()

    private val _showProjectEditor = MutableStateFlow(false)
    val showProjectEditor: StateFlow<Boolean> = _showProjectEditor.asStateFlow()

    private val _confirmDialog = MutableStateFlow<ConfirmDialogState?>(null)
    val confirmDialog: StateFlow<ConfirmDialogState?> = _confirmDialog.asStateFlow()

    private val _nowTime = MutableStateFlow(LocalTime.now())
    val nowTime: StateFlow<LocalTime> = _nowTime.asStateFlow()

    private val _nowMinute = MutableStateFlow(LocalTime.now().withSecond(0).withNano(0))
    val nowMinute: StateFlow<LocalTime> = _nowMinute.asStateFlow()

    private val _nowDate = MutableStateFlow(LocalDate.now())
    val nowDate: StateFlow<LocalDate> = _nowDate.asStateFlow()

    private val _pomo = MutableStateFlow(
        PomoRuntime(
            leftSec = _state.value.settings.focusMinutes * 60,
            totalSec = _state.value.settings.focusMinutes * 60
        )
    )
    val pomo: StateFlow<PomoRuntime> = _pomo.asStateFlow()

    private val _sw = MutableStateFlow(SwRuntime())
    val sw: StateFlow<SwRuntime> = _sw.asStateFlow()

    private val _bannerDismissed = MutableStateFlow(false)
    val bannerDismissed: StateFlow<Boolean> = _bannerDismissed.asStateFlow()

    private val _busyLabel = MutableStateFlow<String?>(null)
    val busyLabel: StateFlow<String?> = _busyLabel.asStateFlow()

    private val _scanStatus = MutableStateFlow<String?>(null)
    val scanStatus: StateFlow<String?> = _scanStatus.asStateFlow()

    private val _lastQrSignResult = MutableStateFlow<NativeClients.QrSignResult?>(null)
    val lastQrSignResult: StateFlow<NativeClients.QrSignResult?> = _lastQrSignResult.asStateFlow()

    private val _hwTerms = MutableStateFlow(_state.value.hw.terms)
    val hwTerms: StateFlow<List<HwTerm>> = _hwTerms.asStateFlow()

    private val _hwCourses = MutableStateFlow(_state.value.hw.courses)
    val hwCourses: StateFlow<List<HwCourse>> = _hwCourses.asStateFlow()

    private val _hwCache = MutableStateFlow(_state.value.hw.byTerm)
    val hwCache: StateFlow<Map<String, HwTermCache>> = _hwCache.asStateFlow()

    private val _hwIndexError = MutableStateFlow(_state.value.hw.lastError)
    val hwIndexError: StateFlow<String> = _hwIndexError.asStateFlow()

    private val _hwLoadingTermId = MutableStateFlow<String?>(null)
    val hwLoadingTermId: StateFlow<String?> = _hwLoadingTermId.asStateFlow()

    private val _dlStates = MutableStateFlow<Map<String, DlStatus>>(emptyMap())
    val dlStates: StateFlow<Map<String, DlStatus>> = _dlStates.asStateFlow()

    private val _snackbars = MutableSharedFlow<SnackbarEvent>(extraBufferCapacity = 8)
    val snackbars = _snackbars.asSharedFlow()

    private val _portalPageNotice = MutableStateFlow(1)
    private val _portalPageLecture = MutableStateFlow(1)

    private val updatePrefs = ctx.getSharedPreferences("mu_updater", Context.MODE_PRIVATE)
    private val _updateState = MutableStateFlow(
        readInstalledVersion().let { (ver, code) ->
            AppUpdateState(currentVersion = ver, currentVersionCode = code)
        }
    )
    val updateState: StateFlow<AppUpdateState> = _updateState.asStateFlow()
    private var updateDownloadJob: Job? = null
    private var lastAutoCheckAttemptMs = 0L
    private val saveChannel = Channel<WorkbenchState>(Channel.CONFLATED)
    private var alarmJob: Job? = null
    private var isForeground = true

    init {
        NotificationHelper.ensureChannels(ctx)
        viewModelScope.launch(Dispatchers.Default) {
            NotificationHelper.rescheduleAll(ctx, _state.value, builtinHolidays)
        }

        // 单队列合并落盘协程：避免高频点击/勾选在主线程同步序列化写文件
        viewModelScope.launch(Dispatchers.IO) {
            for (pending in saveChannel) {
                delay(120L)
                var latest = pending
                while (true) {
                    latest = saveChannel.tryReceive().getOrNull() ?: break
                }
                repo.save(latest)
            }
        }

        // 1 秒实时心跳：时钟、今日课程剩余时间进度条、番茄钟、秒表、倒计时（后台无计时器时暂停空转）
        viewModelScope.launch {
            while (true) {
                delay(1000L)
                val p = _pomo.value
                val swv = _sw.value
                val hasRunningTimer = p.running || swv.running || swv.cdRunning
                if (!isForeground && !hasRunningTimer) continue
                val now = LocalTime.now()
                _nowTime.value = now
                val curMin = now.withSecond(0).withNano(0)
                if (curMin != _nowMinute.value) _nowMinute.value = curMin
                val today = LocalDate.now()
                if (today != _nowDate.value) _nowDate.value = today
                tickTimers()
            }
        }

        // 后台静默自动更新（已填凭据且超过阈值）
        viewModelScope.launch {
            val s = _state.value
            val nowMs = System.currentTimeMillis()
            if (s.timetable.auto && s.timetable.user.isNotBlank() && s.timetable.pwd.isNotBlank() &&
                (nowMs - s.timetable.lastOk) > 6 * 3600 * 1000L
            ) {
                syncTimetable(s.timetable.user, s.timetable.pwd, silent = true)
            }
            if (s.ucloud.on && s.ucloud.id.isNotBlank() && s.ucloud.pwd.isNotBlank()) {
                if ((nowMs - s.ucloud.lastSync) > 5 * 60 * 1000L) {
                    syncUcloudOnly(silent = true)
                } else {
                    prefetchUcloudTodoDetails()
                }
            }
        }

        // 每次冷启动自动静默检查应用新版本
        viewModelScope.launch {
            delay(1000L)
            checkForAppUpdate(manual = false)
        }
    }

    private fun tickTimers() {
        val nowMs = System.currentTimeMillis()
        val p = _pomo.value
        if (p.running && p.endsAtMs > 0L) {
            val left = ((p.endsAtMs - nowMs + 999L) / 1000L).toInt().coerceAtLeast(0)
            if (left == 0) {
                val nextFocus = !p.isFocus
                val mins = if (nextFocus) _state.value.settings.focusMinutes else _state.value.settings.breakMinutes
                if (p.isFocus) {
                    val tk = TodoEngine.todayKey()
                    updateState { s ->
                        val cnt = if (s.todayPomoDate == tk) s.todayPomos + 1 else 1
                        s.copy(todayPomos = cnt, todayPomoDate = tk)
                    }
                }
                NotificationHelper.playChime(_state.value.settings.soundLevel)
                _pomo.value = PomoRuntime(
                    isFocus = nextFocus,
                    running = false,
                    paused = false,
                    leftSec = mins * 60,
                    totalSec = mins * 60,
                    linkedTodoId = p.linkedTodoId
                )
                toast(if (p.isFocus) "专注完成！已记录 1 个番茄，休息一下吧" else "休息结束，准备下一轮专注")
            } else {
                _pomo.value = p.copy(leftSec = left)
            }
        }

        val swv = _sw.value
        var nextSw = swv
        if (swv.running) {
            nextSw = nextSw.copy(
                elapsedMs = swv.elapsedMs + (nowMs - swv.lastTickMs).coerceAtLeast(0L),
                lastTickMs = nowMs
            )
        }
        if (swv.cdRunning && swv.cdEndsAtMs > 0L) {
            val left = ((swv.cdEndsAtMs - nowMs + 999L) / 1000L).toInt().coerceAtLeast(0)
            if (left == 0) {
                NotificationHelper.playChime(_state.value.settings.soundLevel)
                nextSw = nextSw.copy(cdRunning = false, cdLeftSec = 0)
                toast("倒计时结束")
            } else {
                nextSw = nextSw.copy(cdLeftSec = left)
            }
        }
        if (nextSw != swv) _sw.value = nextSw
    }

    fun toast(msg: String, actionLabel: String? = null, onAction: (() -> Unit)? = null) {
        _snackbars.tryEmit(SnackbarEvent(msg, actionLabel, onAction))
    }

    fun updateState(rescheduleAlarms: Boolean = false, block: (WorkbenchState) -> WorkbenchState) {
        val next = block(_state.value)
        _state.value = next
        saveChannel.trySend(next)
        if (rescheduleAlarms) {
            alarmJob?.cancel()
            alarmJob = viewModelScope.launch(Dispatchers.Default) {
                NotificationHelper.rescheduleAll(ctx, next, builtinHolidays)
            }
        }
    }

    fun flushPendingSave() {
        val snapshot = _state.value
        viewModelScope.launch(Dispatchers.IO) {
            repo.save(snapshot)
        }
    }

    fun onAppBackgrounded() {
        isForeground = false
        flushPendingSave()
    }

    /* ---------------- 导航 ---------------- */

    fun selectTab(target: MainTab) {
        if (_showSettings.value) _showSettings.value = false
        val old = _tab.value
        _tabDir.value = when {
            target.index > old.index -> 1
            target.index < old.index -> -1
            else -> 0
        }
        _tab.value = target
    }

    fun selectFocusSub(sub: FocusSub) {
        _focusSub.value = sub
    }

    fun selectCourseSub(sub: CourseSub) {
        val old = _courseSub.value
        _courseSubDir.value = when {
            sub.index > old.index -> 1
            sub.index < old.index -> -1
            else -> 0
        }
        _courseSub.value = sub
    }

    fun openSettings() { _showSettings.value = true }
    fun closeSettings() { _showSettings.value = false }
    fun openScanOverlay() { _scanStatus.value = null; _showScanOverlay.value = true }
    fun closeScanOverlay() { _showScanOverlay.value = false }

    /** 返回键层级：确认框 -> 更新框 -> 编辑器 -> 扫码页 -> 阅读页 -> 设置页 -> 非今日页回今日 -> 交给系统退出 */
    fun handleBack(): Boolean {
        if (_confirmDialog.value != null) { _confirmDialog.value = null; return true }
        if (_updateState.value.showDialog) { dismissUpdateDialog(); return true }
        if (_showTodoEditor.value) { _showTodoEditor.value = false; return true }
        if (_showProjectEditor.value) { _showProjectEditor.value = false; return true }
        if (_showScanOverlay.value) { _showScanOverlay.value = false; return true }
        if (_readerArticle.value != null) { _readerArticle.value = null; return true }
        if (_showSettings.value) { _showSettings.value = false; return true }
        if (_tab.value != MainTab.TODAY) { selectTab(MainTab.TODAY); return true }
        return false
    }

    fun askConfirm(title: String, msg: String, confirmLabel: String = "确定", isDanger: Boolean = true, onConfirm: () -> Unit) {
        _confirmDialog.value = ConfirmDialogState(title, msg, confirmLabel, isDanger, onConfirm)
    }

    fun dismissConfirm() { _confirmDialog.value = null }
    fun dismissBanner() { _bannerDismissed.value = true }

    /* ---------------- 待办 CRUD 与滑动操作 ---------------- */

    fun openNewTodo(presetDate: String = TodoEngine.todayKey()) {
        _editingTodo.value = TodoItem(
            id = "",
            title = "",
            type = TodoType.ONCE,
            date = presetDate
        )
        _showTodoEditor.value = true
    }

    fun openEditTodo(todo: TodoItem) {
        _editingTodo.value = todo
        _showTodoEditor.value = true
    }

    fun closeTodoEditor() { _showTodoEditor.value = false }

    fun saveTodo(item: TodoItem) {
        val id = item.id.ifEmpty { "t_${UUID.randomUUID().toString().take(8)}" }
        val finalItem = item.copy(id = id)
        updateState(rescheduleAlarms = true) { s ->
            val exists = s.todos.any { it.id == id }
            val next = if (exists) s.todos.map { if (it.id == id) finalItem else it } else listOf(finalItem) + s.todos
            s.copy(todos = next)
        }
        _showTodoEditor.value = false
        toast("待办已保存")
    }

    fun toggleTodoDone(todoId: String, dateKey: String = TodoEngine.todayKey()) {
        updateState(rescheduleAlarms = true) { s ->
            s.copy(todos = s.todos.map { t ->
                if (t.id != todoId) t
                else {
                    val nextDone = t.doneDates.toMutableSet()
                    if (dateKey in nextDone) nextDone.remove(dateKey) else nextDone.add(dateKey)
                    t.copy(doneDates = nextDone)
                }
            })
        }
    }

    fun toggleSubStep(todoId: String, stepId: String) {
        updateState { s ->
            s.copy(todos = s.todos.map { t ->
                if (t.id != todoId) t else t.copy(subSteps = t.subSteps.map { st ->
                    if (st.id == stepId) st.copy(done = !st.done) else st
                })
            })
        }
    }

    fun postponeTodoToTomorrow(todoId: String) {
        val tomorrow = TodoEngine.formatDate(LocalDate.now().plusDays(1))
        updateState(rescheduleAlarms = true) { s ->
            s.copy(todos = s.todos.map { t ->
                if (t.id != todoId) t else t.copy(postponedUntil = tomorrow, date = if (t.type == TodoType.ONCE) tomorrow else t.date)
            })
        }
        toast("已顺延到明天（$tomorrow）")
    }

    fun deleteTodoWithUndo(todo: TodoItem) {
        val snapshot = _state.value.todos
        updateState(rescheduleAlarms = true) { s ->
            s.copy(todos = s.todos.filterNot { it.id == todo.id })
        }
        toast("已删除「${todo.title}」", actionLabel = "撤销") {
            updateState(rescheduleAlarms = true) { s -> s.copy(todos = snapshot) }
        }
    }

    /* ---------------- 日历节假日/调休 ---------------- */

    fun setHolidayRange(startDate: LocalDate, endDate: LocalDate, mode: String, label: String) {
        val a = minOf(startDate, endDate)
        val b = maxOf(startDate, endDate)
        updateState(rescheduleAlarms = true) { s ->
            val next = s.holidayManual.toMutableMap()
            var cur = a
            while (!cur.isAfter(b)) {
                val k = TodoEngine.formatDate(cur)
                when (mode) {
                    "auto" -> next.remove(k)
                    "off" -> next[k] = HolidayEntry(off = true, name = label.ifEmpty { "手动放假" })
                    "work" -> next[k] = HolidayEntry(off = false, name = label.ifEmpty { "调休上班" })
                }
                cur = cur.plusDays(1)
            }
            s.copy(holidayManual = next)
        }
        toast("已更新节假日/调休标记")
    }

    fun updateHolidaysOnline() {
        viewModelScope.launch {
            _busyLabel.value = "正在更新法定节假日..."
            try {
                val y = LocalDate.now().year
                val fetched = withContext(Dispatchers.IO) {
                    NativeClients.fetchHolidays(listOf(y - 1, y, y + 1))
                }
                if (fetched.isEmpty()) {
                    toast("更新失败：未能连上节假日数据源")
                } else {
                    updateState(rescheduleAlarms = true) { s ->
                        val merged = s.holidayCache.toMutableMap()
                        merged.putAll(fetched)
                        s.copy(holidayCache = merged)
                    }
                    toast("已更新 ${fetched.keys.sorted().joinToString(" / ")} 年节假日")
                }
            } finally {
                _busyLabel.value = null
            }
        }
    }

    /* ---------------- 番茄钟 & 秒表 & 倒计时 ---------------- */

    fun startOrPausePomo() {
        val p = _pomo.value
        val now = System.currentTimeMillis()
        if (p.running) {
            val left = ((p.endsAtMs - now + 999L) / 1000L).toInt().coerceAtLeast(1)
            NotificationHelper.cancelAlarm(ctx, 2002)
            _pomo.value = p.copy(running = false, paused = true, leftSec = left, endsAtMs = 0L)
        } else {
            val endsAt = now + p.leftSec * 1000L
            NotificationHelper.schedulePomoOrTimer(
                ctx, 2002, endsAt,
                if (p.isFocus) "番茄钟专注结束" else "休息时间结束",
                if (p.isFocus) "可以休息 ${state.value.settings.breakMinutes} 分钟" else "准备开始下一轮专注"
            )
            _pomo.value = p.copy(running = true, paused = false, endsAtMs = endsAt)
        }
    }

    fun resetPomo(toFocus: Boolean = _pomo.value.isFocus) {
        NotificationHelper.cancelAlarm(ctx, 2002)
        val mins = if (toFocus) _state.value.settings.focusMinutes else _state.value.settings.breakMinutes
        _pomo.value = PomoRuntime(
            isFocus = toFocus,
            running = false,
            paused = false,
            leftSec = mins * 60,
            totalSec = mins * 60,
            linkedTodoId = _pomo.value.linkedTodoId
        )
    }

    fun linkPomoTodo(todoId: String) {
        _pomo.value = _pomo.value.copy(linkedTodoId = todoId)
    }

    fun toggleStopwatch() {
        val s = _sw.value
        val now = System.currentTimeMillis()
        _sw.value = if (s.running) {
            s.copy(running = false, elapsedMs = s.elapsedMs + (now - s.lastTickMs).coerceAtLeast(0L))
        } else {
            s.copy(running = true, lastTickMs = now)
        }
    }

    fun resetStopwatch() { _sw.value = _sw.value.copy(running = false, elapsedMs = 0L) }

    fun setCountdownMinutes(mins: Int) {
        val m = mins.coerceIn(1, 240)
        NotificationHelper.cancelAlarm(ctx, 2003)
        _sw.value = _sw.value.copy(cdMinutes = m, cdRunning = false, cdLeftSec = m * 60, cdEndsAtMs = 0L)
    }

    fun toggleCountdown() {
        val s = _sw.value
        val now = System.currentTimeMillis()
        if (s.cdRunning) {
            val left = ((s.cdEndsAtMs - now + 999L) / 1000L).toInt().coerceAtLeast(1)
            NotificationHelper.cancelAlarm(ctx, 2003)
            _sw.value = s.copy(cdRunning = false, cdLeftSec = left, cdEndsAtMs = 0L)
        } else {
            val sec = if (s.cdLeftSec > 0) s.cdLeftSec else s.cdMinutes * 60
            val ends = now + sec * 1000L
            NotificationHelper.schedulePomoOrTimer(ctx, 2003, ends, "倒计时结束", "设定的 ${s.cdMinutes} 分钟已到")
            _sw.value = s.copy(cdRunning = true, cdLeftSec = sec, cdEndsAtMs = ends)
        }
    }

    /* ---------------- 项目进度 ---------------- */

    fun openNewProject() {
        _editingProject.value = ProjectCard(id = "", title = "")
        _showProjectEditor.value = true
    }

    fun openEditProject(p: ProjectCard) {
        _editingProject.value = p
        _showProjectEditor.value = true
    }

    fun closeProjectEditor() { _showProjectEditor.value = false }

    fun saveProject(card: ProjectCard) {
        val id = card.id.ifEmpty { "proj_${UUID.randomUUID().toString().take(8)}" }
        val finalCard = card.copy(id = id)
        updateState { s ->
            val exists = s.projects.any { it.id == id }
            val next = if (exists) s.projects.map { if (it.id == id) finalCard else it } else s.projects + finalCard
            s.copy(projects = next)
        }
        _showProjectEditor.value = false
        toast("项目卡已保存")
    }

    fun updateSkillProgress(projectId: String, skillId: String, progress: Int) {
        updateState { s ->
            s.copy(projects = s.projects.map { p ->
                if (p.id != projectId) p else p.copy(items = p.items.map { sk ->
                    if (sk.id == skillId) sk.copy(progress = progress.coerceIn(0, 100)) else sk
                })
            })
        }
    }

    fun deleteProject(projectId: String) {
        updateState { s -> s.copy(projects = s.projects.filterNot { it.id == projectId }) }
        toast("项目卡已删除")
    }

    /* ---------------- 课表 / 云邮 / 资讯 / 作业档案 / 扫码 ---------------- */

    fun activeTimetable(): TimetableData = _state.value.timetable.data ?: builtinTimetable

    fun moveTodoToDate(todoId: String, dateKey: String) {
        updateState(rescheduleAlarms = true) { s ->
            s.copy(todos = s.todos.map { t ->
                if (t.id == todoId) t.copy(date = dateKey, postponedUntil = "") else t
            })
        }
        toast("已移至 $dateKey")
    }

    fun testJwLogin(user: String, pwd: String) {
        viewModelScope.launch {
            _busyLabel.value = "正在验证教务系统登录..."
            try {
                val trace = withContext(Dispatchers.IO) { NativeClients.testJwLogin(user, pwd) }
                updateState { s ->
                    s.copy(timetable = s.timetable.copy(user = user.trim(), pwd = pwd, trace = trace, lastError = ""))
                }
                toast("教务系统登录验证通过！")
            } catch (e: Throwable) {
                val msg = e.message ?: "验证失败"
                updateState { s ->
                    val tr = (s.timetable.trace + "✗ 验证失败：$msg").takeLast(40)
                    s.copy(timetable = s.timetable.copy(user = user.trim(), pwd = pwd, lastError = msg, trace = tr))
                }
                toast("验证失败：$msg")
            } finally {
                _busyLabel.value = null
            }
        }
    }

    fun restoreBuiltinTimetable() {
        updateState { s ->
            s.copy(timetable = s.timetable.copy(data = null, lastError = "", trace = listOf("已恢复使用内置 2026-2027-1 课表快照")))
        }
        toast("已恢复内置课表快照")
    }

    fun syncTimetable(
        user: String,
        pwd: String,
        startMondayOverride: String = _state.value.timetable.startMonday,
        totalWeeksOverride: Int = _state.value.timetable.totalWeeks,
        silent: Boolean = false
    ) {
        viewModelScope.launch {
            if (!silent) _busyLabel.value = "正在从教务系统拉取课表..."
            try {
                val res = withContext(Dispatchers.IO) {
                    NativeClients.fetchJwTimetable(
                        user = user,
                        pwd = pwd,
                        fallbackPeriods = builtinTimetable.periods,
                        fallbackStartMonday = startMondayOverride.ifEmpty { builtinTimetable.startMonday },
                        fallbackTotalWeeks = if (totalWeeksOverride > 0) totalWeeksOverride else builtinTimetable.totalWeeks
                    )
                }
                updateState { s ->
                    s.copy(
                        timetable = s.timetable.copy(
                            user = user.trim(),
                            pwd = pwd,
                            startMonday = res.startMonday,
                            totalWeeks = res.totalWeeks,
                            data = res.data,
                            fetched = res.data.fetched,
                            lastOk = System.currentTimeMillis(),
                            lastError = "",
                            trace = res.trace
                        )
                    )
                }
                if (!silent) toast("课表已更新（共 ${res.data.courses.size} 条课程）")
            } catch (e: Throwable) {
                val msg = e.message ?: "更新失败"
                updateState { s ->
                    val tr = (s.timetable.trace + "✗ 失败：$msg").takeLast(40)
                    s.copy(timetable = s.timetable.copy(user = user.trim(), pwd = pwd, lastError = msg, trace = tr))
                }
                if (!silent) toast("课表更新失败：$msg")
            } finally {
                if (!silent) _busyLabel.value = null
            }
        }
    }

    fun loginAndSyncUcloud(user: String, pwd: String) {
        val prev = _state.value.ucloud
        viewModelScope.launch {
            _busyLabel.value = "正在登录云邮并同步待办..."
            try {
                val (cfg, items) = withContext(Dispatchers.IO) {
                    val logged = NativeClients.ucloudLogin(user, pwd).copy(
                        seen = prev.seen,
                        seenInit = prev.seenInit,
                        done = prev.done,
                        courseMap = prev.courseMap,
                        details = prev.details
                    )
                    NativeClients.syncUcloudTodos(logged)
                }
                updateState { s ->
                    val nextPortal = if (prev.id != cfg.id) s.portal.copy(cookieJarJson = "") else s.portal
                    s.copy(ucloud = cfg, portal = nextPortal)
                }
                prefetchUcloudTodoDetails()
                toast("云邮已同步：${items.size} 条未完成")
            } catch (e: Throwable) {
                val msg = e.message ?: "登录失败"
                updateState { s -> s.copy(ucloud = s.ucloud.copy(id = user.trim(), pwd = pwd, lastError = msg)) }
                toast("云邮同步失败：$msg")
            } finally {
                _busyLabel.value = null
            }
        }
    }

    fun syncUcloudOnly(silent: Boolean = false) {
        val cur = _state.value.ucloud
        if (cur.id.isBlank() || cur.pwd.isBlank()) {
            if (!silent) toast("请先在设置里填写云邮学号与密码")
            return
        }
        viewModelScope.launch {
            if (!silent) _busyLabel.value = "正在刷新云邮待办..."
            try {
                val (cfg, items) = withContext(Dispatchers.IO) { NativeClients.syncUcloudTodos(cur) }
                updateState { s -> s.copy(ucloud = cfg) }
                prefetchUcloudTodoDetails()
                if (!silent) toast("云邮已更新（${items.size} 条）")
            } catch (e: Throwable) {
                val msg = e.message ?: "同步失败"
                updateState { s -> s.copy(ucloud = s.ucloud.copy(lastError = msg)) }
                if (!silent) toast("同步失败：$msg")
            } finally {
                if (!silent) _busyLabel.value = null
            }
        }
    }

    private fun mergeUcloudDetail(activityId: String, nextCfg: UcloudConfig, det: UcloudDetailCache) {
        updateState { s ->
            s.copy(
                ucloud = nextCfg.copy(
                    items = s.ucloud.items,
                    done = s.ucloud.done,
                    seen = s.ucloud.seen,
                    seenInit = s.ucloud.seenInit,
                    courseMap = s.ucloud.courseMap,
                    details = s.ucloud.details + (activityId to det)
                )
            )
        }
    }

    fun fetchUcloudTodoDetail(activityId: String) {
        val cur = _state.value.ucloud
        if (cur.id.isBlank() || cur.pwd.isBlank()) return
        val cached = cur.details[activityId]
        if (cached != null && cached.error.isEmpty() && cached.fetchedAt > 0L) return
        if (!ucloudDetailInFlight.add(activityId)) return
        viewModelScope.launch {
            try {
                val (nextCfg, det) = withContext(Dispatchers.IO) {
                    NativeClients.fetchUcloudTodoDetail(cur, activityId)
                }
                mergeUcloudDetail(activityId, nextCfg, det)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                updateState { s ->
                    s.copy(ucloud = s.ucloud.copy(details = s.ucloud.details + (activityId to UcloudDetailCache(error = e.message ?: "读取失败"))))
                }
            } finally {
                ucloudDetailInFlight.remove(activityId)
            }
        }
    }

    /** 静默预拉取未缓存详情的云邮待办，补全真实发布时间（beginTime）使进度条自动准确。 */
    fun prefetchUcloudTodoDetails() {
        val cur = _state.value.ucloud
        if (!cur.on || cur.id.isBlank() || cur.pwd.isBlank()) return
        val missing = cur.items.filter { u ->
            u.id !in cur.done &&
                u.endTime.isNotEmpty() &&
                u.id !in ucloudDetailInFlight &&
                cur.details[u.id]?.let { it.error.isEmpty() && it.fetchedAt > 0L } != true
        }
        if (missing.isEmpty()) return
        missing.forEach { ucloudDetailInFlight.add(it.id) }
        viewModelScope.launch {
            missing.forEach { item ->
                launch {
                    try {
                        ucloudDetailSemaphore.withPermit {
                            val latestCfg = _state.value.ucloud
                            val (nextCfg, det) = withContext(Dispatchers.IO) {
                                NativeClients.fetchUcloudTodoDetail(latestCfg, item.id)
                            }
                            mergeUcloudDetail(item.id, nextCfg, det)
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Throwable) {
                        // 静默预拉取失败时不写入 error 缓存，保留手动点击重试能力
                    } finally {
                        ucloudDetailInFlight.remove(item.id)
                    }
                }
            }
        }
    }

    fun toggleUcloudDone(id: String) {
        updateState { s ->
            val set = s.ucloud.done.toMutableSet()
            if (id in set) set.remove(id) else set.add(id)
            s.copy(ucloud = s.ucloud.copy(done = set))
        }
    }

    fun clearQrSignResult() {
        _lastQrSignResult.value = null
        _scanStatus.value = null
    }

    fun submitQrSign(rawQr: String) {
        val trimmed = rawQr.trim()
        if (trimmed.isEmpty()) return
        val cur = _state.value.ucloud
        if (cur.token.isBlank() && (cur.id.isBlank() || cur.pwd.isBlank())) {
            val res = NativeClients.QrSignResult(
                ok = false,
                qrText = trimmed,
                summaryMsg = "请先在设置里登录云邮账号"
            )
            _lastQrSignResult.value = res
            _scanStatus.value = res.summaryMsg
            toast(res.summaryMsg)
            return
        }
        viewModelScope.launch {
            _scanStatus.value = "正在提交签到..."
            _lastQrSignResult.value = null
            try {
                val res = withContext(Dispatchers.IO) {
                    NativeClients.submitQrSignDetailed(cur, trimmed)
                }
                res.updatedCfg?.let { upd -> updateState { s -> s.copy(ucloud = upd) } }
                _lastQrSignResult.value = res
                _scanStatus.value = res.summaryMsg
                toast(if (res.ok) "签到成功" else res.summaryMsg)
            } catch (e: Throwable) {
                val msg = "签到失败：${e.message ?: "未知错误"}"
                _lastQrSignResult.value = NativeClients.QrSignResult(false, trimmed, msg)
                _scanStatus.value = msg
                toast(msg)
            }
        }
    }

    fun syncPortalNews(kind: String, append: Boolean = false) {
        val uc = _state.value.ucloud
        val cachedCk = _state.value.portal.cookieJarJson
        val pageFlow = if (kind == "lecture") _portalPageLecture else _portalPageNotice
        val targetPage = if (append) pageFlow.value + 1 else 1
        viewModelScope.launch {
            _busyLabel.value = if (kind == "lecture") "正在拉取学术讲座..." else "正在拉取校内通知..."
            try {
                val (list, nextCookie) = withContext(Dispatchers.IO) {
                    NativeClients.syncPortalNews(uc.id, uc.pwd, kind, targetPage, cachedCk)
                }
                pageFlow.value = targetPage
                updateState { s ->
                    val seen = s.portal.seen.toMutableSet()
                    val oldById = (if (kind == "lecture") s.portal.lectures else s.portal.notices).associateBy { it.id }
                    val existing = if (append) (if (kind == "lecture") s.portal.lectures else s.portal.notices) else emptyList()
                    val existingIds = existing.map { it.id }.toSet()
                    val incoming = list.filter { it.id !in existingIds }.map { art ->
                        val prev = oldById[art.id]
                        val mergedArt = if (prev != null && prev.detailFetched) {
                            art.copy(
                                dept = art.dept.ifEmpty { prev.dept },
                                date = art.date.ifEmpty { prev.date },
                                bodyText = prev.bodyText,
                                blocks = prev.blocks,
                                imageCount = prev.imageCount,
                                lecture = prev.lecture,
                                detailFetched = true,
                                attachments = prev.attachments
                            )
                        } else art
                        mergedArt.copy(isNew = s.portal.seenInit && art.id !in seen)
                    }
                    val merged = existing + incoming
                    merged.forEach { seen.add(it.id) }
                    val nextPortal = if (kind == "lecture") {
                        s.portal.copy(
                            kind = kind,
                            cookieJarJson = nextCookie,
                            lectures = merged,
                            seen = seen,
                            seenInit = true,
                            lastSync = System.currentTimeMillis(),
                            lastError = ""
                        )
                    } else {
                        s.portal.copy(
                            kind = kind,
                            cookieJarJson = nextCookie,
                            notices = merged,
                            seen = seen,
                            seenInit = true,
                            lastSync = System.currentTimeMillis(),
                            lastError = ""
                        )
                    }
                    s.copy(portal = nextPortal)
                }
                toast(if (append) "新增加载 ${list.size} 条" else "已拉取 ${list.size} 条资讯")
            } catch (e: Throwable) {
                val msg = e.message ?: "拉取失败"
                updateState { s -> s.copy(portal = s.portal.copy(lastError = msg)) }
                toast(msg)
            } finally {
                _busyLabel.value = null
            }
        }
    }

    fun openArticle(article: PortalArticle) {
        _readerArticle.value = article
        val hasValidDetail = article.detailFetched && (
            article.blocks.isNotEmpty() ||
                (article.bodyText.isNotEmpty() && !article.bodyText.startsWith("（正文为空"))
            )
        if (hasValidDetail) return
        fetchReaderDetail(article)
    }

    fun refreshReaderArticle() {
        val art = _readerArticle.value ?: return
        art.blocks.filter { it.type == "img" }.forEach { portalImgCache.remove(it.src) }
        _readerRefreshSeq.value += 1
        fetchReaderDetail(art)
    }

    private fun fetchReaderDetail(article: PortalArticle) {
        val uc = _state.value.ucloud
        val cachedCk = _state.value.portal.cookieJarJson
        viewModelScope.launch {
            _readerLoading.value = true
            try {
                val (full, nextCookie) = withContext(Dispatchers.IO) {
                    NativeClients.fetchPortalDetail(uc.id, uc.pwd, article, cachedCk)
                }
                _readerArticle.value = full
                updateState { s ->
                    val updN = s.portal.notices.map { if (it.id == full.id) full else it }
                    val updL = s.portal.lectures.map { if (it.id == full.id) full else it }
                    s.copy(portal = s.portal.copy(cookieJarJson = nextCookie, notices = updN, lectures = updL))
                }
            } catch (e: Throwable) {
                toast("正文读取失败：${e.message}")
            } finally {
                _readerLoading.value = false
            }
        }
    }

    suspend fun loadPortalImage(
        url: String,
        altUrl: String = "",
        referer: String = "http://my.bupt.edu.cn/",
        force: Boolean = false
    ): List<ImageBitmap> {
        if (!force) {
            portalImgCache.get(url)?.let { return it }
        } else {
            portalImgCache.remove(url)
        }
        val (bytes, nextCookie) = portalImgSemaphore.withPermit {
            if (!force) {
                portalImgCache.get(url)?.let { return@loadPortalImage it }
            }
            val uc = _state.value.ucloud
            val cachedCk = _state.value.portal.cookieJarJson
            withContext(Dispatchers.IO) {
                NativeClients.fetchPortalImageBytes(
                    url = url,
                    altUrl = altUrl,
                    referer = referer,
                    user = uc.id,
                    pwd = uc.pwd,
                    cachedCookieJson = cachedCk
                )
            }
        }
        if (nextCookie.isNotEmpty() && nextCookie != _state.value.portal.cookieJarJson) {
            updateState { s ->
                if (s.portal.cookieJarJson == nextCookie) s
                else s.copy(portal = s.portal.copy(cookieJarJson = nextCookie))
            }
        }
        val slices = withContext(Dispatchers.Default) { decodePortalImageSlices(bytes) }
        portalImgCache.put(url, slices)
        return slices
    }

    private fun decodePortalImageSlices(bytes: ByteArray): List<ImageBitmap> {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            throw IllegalStateException("无法解码图片格式")
        }
        var sample = 1
        while (bounds.outWidth / sample > 2048) {
            sample *= 2
        }
        val opts = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
            ?: throw IllegalStateException("图片内存解码失败")
        val maxSliceH = 2048
        if (bmp.height <= maxSliceH) {
            return listOf(bmp.asImageBitmap())
        }
        val slices = mutableListOf<ImageBitmap>()
        var y = 0
        while (y < bmp.height) {
            val h = (bmp.height - y).coerceAtMost(maxSliceH)
            slices += Bitmap.createBitmap(bmp, 0, y, bmp.width, h).asImageBitmap()
            y += h
        }
        bmp.recycle()
        return slices
    }

    fun closeReader() { _readerArticle.value = null }

    fun loadHwIndex(force: Boolean = false) {
        val uc = _state.value.ucloud
        if (uc.token.isBlank() && (uc.id.isBlank() || uc.pwd.isBlank())) {
            _hwIndexError.value = "请先在设置里登录云邮账号"
            toast("请先在设置里登录云邮账号")
            return
        }
        val hwState = _state.value.hw
        val ttl = 6L * 3600 * 1000L
        if (!force && hwState.terms.isNotEmpty() && hwState.courses.isNotEmpty() &&
            (System.currentTimeMillis() - hwState.fetchedAt) < ttl
        ) {
            _hwTerms.value = hwState.terms
            _hwCourses.value = hwState.courses
            _hwCache.value = hwState.byTerm
            _hwIndexError.value = ""
            val cur = hwState.terms.firstOrNull { it.isCurrent } ?: hwState.terms.firstOrNull()
            if (cur != null && hwState.byTerm[cur.id] == null) loadHwTerm(cur.id, false)
            return
        }
        viewModelScope.launch {
            _busyLabel.value = "正在加载作业档案学期与课程..."
            _hwIndexError.value = ""
            try {
                val (nextCfg, terms, courses) = withContext(Dispatchers.IO) {
                    NativeClients.fetchHwTermsAndCourses(uc)
                }
                val emptyByTerm = if (force) emptyMap() else _state.value.hw.byTerm
                updateState { s ->
                    s.copy(
                        ucloud = nextCfg,
                        hw = s.hw.copy(
                            terms = terms,
                            courses = courses,
                            byTerm = emptyByTerm,
                            fetchedAt = System.currentTimeMillis(),
                            lastError = ""
                        )
                    )
                }
                _hwTerms.value = terms
                _hwCourses.value = courses
                if (force) _hwCache.value = emptyMap()
                val cur = terms.firstOrNull { it.isCurrent } ?: terms.firstOrNull()
                if (cur != null) loadHwTerm(cur.id, force)
            } catch (e: Throwable) {
                val msg = e.message ?: "加载失败"
                _hwIndexError.value = msg
                updateState { s -> s.copy(hw = s.hw.copy(lastError = msg)) }
                toast("加载作业档案失败：$msg")
            } finally {
                _busyLabel.value = null
            }
        }
    }

    fun loadHwTerm(termId: String, force: Boolean = false) {
        val uc = _state.value.ucloud
        val cached = _hwCache.value[termId]
        val ttl = 6L * 3600 * 1000L
        if (!force && cached != null && cached.err.isEmpty() && (System.currentTimeMillis() - cached.fetchedAt) < ttl) {
            return
        }
        val courses = _hwCourses.value.filter { it.termId == termId }
        if (courses.isEmpty()) {
            val empty = HwTermCache(termId, System.currentTimeMillis(), emptyList())
            _hwCache.value = _hwCache.value + (termId to empty)
            updateState { s -> s.copy(hw = s.hw.copy(byTerm = s.hw.byTerm + (termId to empty))) }
            return
        }
        viewModelScope.launch {
            _hwLoadingTermId.value = termId
            _busyLabel.value = "正在同步该学期作业详情..."
            try {
                val gradedSeen = _state.value.hw.gradedSeen.toMutableMap()
                val (nextCfg, items) = withContext(Dispatchers.IO) {
                    NativeClients.fetchHwTermItems(uc, courses, gradedSeen)
                }
                val nextTermCache = HwTermCache(termId, System.currentTimeMillis(), items)
                _hwCache.value = _hwCache.value + (termId to nextTermCache)
                updateState { s ->
                    s.copy(
                        ucloud = nextCfg,
                        hw = s.hw.copy(
                            byTerm = s.hw.byTerm + (termId to nextTermCache),
                            gradedSeen = gradedSeen
                        )
                    )
                }
            } catch (e: Throwable) {
                val errCache = HwTermCache(termId, 0L, emptyList(), e.message ?: "加载失败")
                _hwCache.value = _hwCache.value + (termId to errCache)
            } finally {
                if (_hwLoadingTermId.value == termId) _hwLoadingTermId.value = null
                _busyLabel.value = null
            }
        }
    }

    fun downloadHwAttachment(att: HwAttachment) {
        val key = att.resourceId
        val cur = _dlStates.value[key]
        if (cur?.uri != null) {
            muNative.open(cur.uri, "")
            return
        }
        val ep = if (att.recordFlag != 0) "download-large" else "download"
        val url = "https://apiucloud.bupt.edu.cn/blade-source/resource/$ep?resourceId=${att.resourceId}&oauthKey=${NativeClients.oauthKeyFor(att.resourceId)}"
        _dlStates.value = _dlStates.value + (key to DlStatus(0, true))
        muNative.download(key, url, "", att.name, "https://ucloud.bupt.edu.cn/uclass/", object : MuNative.DownloadCallback {
            override fun onProgress(id: String, pct: Int) {
                _dlStates.value = _dlStates.value + (id to DlStatus(pct, true))
            }
            override fun onDone(id: String, uri: String, name: String) {
                _dlStates.value = _dlStates.value + (id to DlStatus(100, false, uri = uri))
                toast("已保存 $name，再点一次可直接打开")
            }
            override fun onFail(id: String, msg: String) {
                _dlStates.value = _dlStates.value + (id to DlStatus(0, false, err = msg))
                toast("下载失败：$msg")
            }
        })
    }

    fun downloadPortalAttachment(att: PortalAttachment, refererUrl: String) {
        val key = "pt_${NativeClients.md5Hex(att.url).take(12)}"
        val cur = _dlStates.value[key]
        if (cur?.uri != null) {
            muNative.open(cur.uri, "")
            return
        }
        val cookie = NativeClients.portalCookieHeader(_state.value.portal.cookieJarJson)
        _dlStates.value = _dlStates.value + (key to DlStatus(0, true))
        muNative.download(key, att.url, cookie, att.name, refererUrl, object : MuNative.DownloadCallback {
            override fun onProgress(id: String, pct: Int) {
                _dlStates.value = _dlStates.value + (id to DlStatus(pct, true))
            }
            override fun onDone(id: String, uri: String, name: String) {
                _dlStates.value = _dlStates.value + (id to DlStatus(100, false, uri = uri))
                toast("已保存到 下载/MU WORKBENCH/$name")
            }
            override fun onFail(id: String, msg: String) {
                _dlStates.value = _dlStates.value + (id to DlStatus(0, false, err = msg))
                toast("下载失败：$msg")
            }
        })
    }

    fun previewHwAttachment(att: HwAttachment) {
        val uc = _state.value.ucloud
        viewModelScope.launch {
            _busyLabel.value = "正在获取附件预览地址..."
            try {
                val (nextCfg, url) = withContext(Dispatchers.IO) {
                    NativeClients.fetchHwPreviewUrl(uc, att.resourceId, att.name)
                }
                updateState { s -> s.copy(ucloud = nextCfg) }
                openExternalUrl(url)
            } catch (e: Throwable) {
                toast("预览失败：${e.message}")
            } finally {
                _busyLabel.value = null
            }
        }
    }

    fun loadHwExcellentDetail(termId: String, assignmentId: String, ex: HwExcellentItem) {
        if (ex.content.isNotEmpty() || ex.files.isNotEmpty()) return
        val uc = _state.value.ucloud
        viewModelScope.launch {
            try {
                val (nextCfg, updatedEx) = withContext(Dispatchers.IO) {
                    NativeClients.fetchHwExcellentDetail(uc, assignmentId, ex)
                }
                updateState { s -> s.copy(ucloud = nextCfg) }
                val curCache = _hwCache.value[termId] ?: return@launch
                val updItems = curCache.items.map { item ->
                    if (item.id != assignmentId) item
                    else item.copy(excellent = item.excellent.map {
                        if (it.audiencesId == ex.audiencesId && it.teamId == ex.teamId) updatedEx else it
                    })
                }
                _hwCache.value = _hwCache.value + (termId to curCache.copy(items = updItems))
            } catch (e: Throwable) {
                toast("读取优秀作业失败：${e.message}")
            }
        }
    }

    fun openExternalUrl(url: String) {
        try {
            ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: Throwable) {
            toast("无法打开链接")
        }
    }

    /* ---------------- 找资料：BYR Docs 检索 ----------------
       只有用户主动搜索时才联网。最近搜索词和「每门课有几条资料」存在单独的
       SharedPreferences 里，不进 WorkbenchState，备份格式不变。 */

    data class ByrDocsUi(
        val query: String = "",
        val type: ByrDocsClient.Type = ByrDocsClient.Type.ALL,
        val limit: Int = 30,
        val loading: Boolean = false,
        val error: String = "",
        val result: ByrDocsClient.Result? = null
    )

    private val bdPrefs by lazy { ctx.getSharedPreferences("mu_byrdocs", android.content.Context.MODE_PRIVATE) }
    private val _bd = MutableStateFlow(ByrDocsUi())
    val byrDocs: StateFlow<ByrDocsUi> = _bd.asStateFlow()
    private val _bdRecent = MutableStateFlow(loadBdRecent())
    val byrDocsRecent: StateFlow<List<String>> = _bdRecent.asStateFlow()
    private val _bdCounts = MutableStateFlow(loadBdCounts())
    val byrDocsCounts: StateFlow<Map<String, Int>> = _bdCounts.asStateFlow()
    private val _bdCounting = MutableStateFlow(false)
    val byrDocsCounting: StateFlow<Boolean> = _bdCounting.asStateFlow()
    private var bdJob: kotlinx.coroutines.Job? = null

    private fun loadBdRecent(): List<String> = try {
        val a = org.json.JSONArray(bdPrefs.getString("recent", "[]"))
        (0 until a.length()).map { a.getString(it) }.filter { it.isNotBlank() }.take(8)
    } catch (_: Throwable) { emptyList() }

    /** 缓存 3 天：{ 课名: { n: 条数, t: 时间戳 } }，过期的当作没数过 */
    private fun loadBdCounts(): Map<String, Int> = try {
        val o = org.json.JSONObject(bdPrefs.getString("cnt", "{}"))
        val now = System.currentTimeMillis()
        o.keys().asSequence().mapNotNull { k ->
            val e = o.optJSONObject(k) ?: return@mapNotNull null
            if (now - e.optLong("t") > 3L * 86_400_000L) null else k to e.optInt("n")
        }.toMap()
    } catch (_: Throwable) { emptyMap() }

    private fun saveBdCount(name: String, n: Int) {
        try {
            val o = org.json.JSONObject(bdPrefs.getString("cnt", "{}"))
            o.put(name, org.json.JSONObject().put("n", n).put("t", System.currentTimeMillis()))
            bdPrefs.edit().putString("cnt", o.toString()).apply()
        } catch (_: Throwable) {}
    }

    private fun rememberBdQuery(q: String) {
        val list = (listOf(q) + _bdRecent.value.filter { it != q }).take(8)
        _bdRecent.value = list
        bdPrefs.edit().putString("recent", org.json.JSONArray(list).toString()).apply()
    }

    fun clearByrDocsRecent() {
        _bdRecent.value = emptyList()
        bdPrefs.edit().remove("recent").apply()
    }

    /** 本学期课表里的课名（去重），当成「一点就搜」的快捷词 */
    fun termCourseNames(): List<String> =
        activeTimetable().courses.map { it.name.trim() }.filter { it.isNotEmpty() }.distinct().take(16)

    /** 改关键词：immediate=false 时等 400ms 没再输入才搜（打字时不要每个字都发请求） */
    fun byrDocsQuery(q: String, immediate: Boolean = false) {
        _bd.value = _bd.value.copy(query = q, limit = 30)
        runByrDocs(if (immediate || q.isBlank()) 0L else 400L)
    }

    fun byrDocsType(t: ByrDocsClient.Type) {
        if (_bd.value.type == t) return
        _bd.value = _bd.value.copy(type = t, limit = 30)
        runByrDocs(0L)
    }

    fun byrDocsMore() {
        _bd.value = _bd.value.copy(limit = 100)
        runByrDocs(0L)
    }

    fun byrDocsRetry() = runByrDocs(0L)

    private fun runByrDocs(delayMs: Long) {
        bdJob?.cancel()
        val cur = _bd.value
        val q = cur.query.trim()
        if (q.isEmpty()) {
            _bd.value = cur.copy(loading = false, error = "", result = null)
            return
        }
        bdJob = viewModelScope.launch {
            if (delayMs > 0) delay(delayMs)
            _bd.value = _bd.value.copy(loading = true, error = "")
            try {
                val r = withContext(Dispatchers.IO) { ByrDocsClient.search(q, cur.type, cur.limit) }
                // 期间又改了关键词 / 类型：这次结果作废（job 被 cancel 时也走不到这里）
                if (_bd.value.query.trim() != q || _bd.value.type != cur.type) return@launch
                _bd.value = _bd.value.copy(loading = false, result = r)
                rememberBdQuery(q)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Throwable) {
                val msg = when (e) {
                    is java.net.SocketTimeoutException -> "搜索超时，网络可能不太好"
                    is java.net.UnknownHostException -> "连不上 BYR Docs，检查一下网络"
                    else -> "搜索失败（${e.message ?: "未知错误"}）"
                }
                _bd.value = _bd.value.copy(loading = false, error = msg)
            }
        }
    }

    /** 介绍卡上的「已收录 N 份」：缓存 3 天 */
    private val _bdStats = MutableStateFlow(loadBdStats())
    val byrDocsStats: StateFlow<ByrDocsClient.Stats?> = _bdStats.asStateFlow()

    private fun loadBdStats(): ByrDocsClient.Stats? = try {
        val o = org.json.JSONObject(bdPrefs.getString("stats", "{}"))
        if (System.currentTimeMillis() - o.optLong("t") > 3L * 86_400_000L) null
        else ByrDocsClient.Stats(o.optInt("b"), o.optInt("t2"), o.optInt("d"))
    } catch (_: Throwable) { null }

    fun loadByrDocsStats() {
        if (_bdStats.value != null) return
        viewModelScope.launch {
            try {
                val s = withContext(Dispatchers.IO) { ByrDocsClient.stats() }
                _bdStats.value = s
                bdPrefs.edit().putString(
                    "stats",
                    org.json.JSONObject().put("b", s.book).put("t2", s.test).put("d", s.doc).put("t", System.currentTimeMillis()).toString()
                ).apply()
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (_: Throwable) {
                // 拿不到就不显示数字，介绍卡照常显示
            }
        }
    }

    /** 数一下本学期每门课在 BYR Docs 里有几条资料（只数没缓存的，两路并发） */
    fun countByrDocsCourses() {
        if (_bdCounting.value) return
        val todo = termCourseNames().filter { it !in _bdCounts.value }
        if (todo.isEmpty()) return
        _bdCounting.value = true
        viewModelScope.launch {
            val sem = Semaphore(2)
            todo.map { name ->
                launch {
                    sem.withPermit {
                        try {
                            val r = withContext(Dispatchers.IO) { ByrDocsClient.search(name, ByrDocsClient.Type.ALL, 1) }
                            saveBdCount(name, r.total)
                            _bdCounts.value = _bdCounts.value + (name to r.total)
                        } catch (e: kotlinx.coroutines.CancellationException) {
                            throw e
                        } catch (_: Throwable) {
                            // 某门课没网失败了就跳过，下次进来再数
                        }
                    }
                }
            }.forEach { it.join() }
            _bdCounting.value = false
        }
    }

    /* ---------------- 备份与设置 ---------------- */

    fun exportBackup(): String {
        val json = repo.exportJson(_state.value)
        val name = "MU-WORKBENCH-备份-${TodoEngine.todayKey()}.json"
        return muNative.saveTextFile(name, json)
    }

    fun importBackup(raw: String) {
        try {
            val next = repo.importJson(raw)
            _state.value = next
            NotificationHelper.rescheduleAll(ctx, next, builtinHolidays)
            toast("备份已导入（${next.todos.size} 条待办）")
        } catch (e: Throwable) {
            toast("导入失败：JSON 格式无效（${e.message}）")
        }
    }

    fun clearAllData() {
        val fresh = repo.defaultState()
        _state.value = fresh
        repo.save(fresh)
        NotificationHelper.rescheduleAll(ctx, fresh, builtinHolidays)
        toast("全部本地数据已清空")
    }

    /* ---------------- 应用内检查更新 ---------------- */

    private fun readInstalledVersion(): Pair<String, Long> {
        return try {
            val pm = ctx.packageManager
            val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getPackageInfo(ctx.packageName, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(ctx.packageName, 0)
            }
            val verName = info.versionName?.takeIf { it.isNotBlank() } ?: "2.33"
            val verCode = PackageInfoCompat.getLongVersionCode(info)
            verName to verCode
        } catch (_: Throwable) {
            "2.33" to 0L
        }
    }

    private fun targetApkFile(rel: AppUpdater.ReleaseInfo): File {
        val dir = File(ctx.cacheDir, "updates")
        val rawName = rel.apkName.ifEmpty { "MU-WORKBENCH-${rel.tagName}.apk" }
        val safeName = rawName.replace(Regex("[^a-zA-Z0-9._-]"), "_")
        return File(dir, safeName)
    }

    fun onAppForegrounded(fromBackground: Boolean = true) {
        isForeground = true
        val now = LocalTime.now()
        _nowTime.value = now
        val curMin = now.withSecond(0).withNano(0)
        if (curMin != _nowMinute.value) _nowMinute.value = curMin
        val today = LocalDate.now()
        if (today != _nowDate.value) _nowDate.value = today
        tickTimers()

        if (fromBackground) {
            val snap = _updateState.value
            if (snap.checking || snap.downloading || snap.downloadedFilePath != null || snap.showDialog) return
            if (System.currentTimeMillis() - lastAutoCheckAttemptMs < 30_000L) return
            checkForAppUpdate(manual = false)
        }
    }

    fun checkForAppUpdate(manual: Boolean = true) {
        val snap = _updateState.value
        if (snap.checking || snap.downloading) return
        lastAutoCheckAttemptMs = System.currentTimeMillis()
        _updateState.update { it.copy(checking = true, error = null) }
        viewModelScope.launch {
            try {
                val rel = withContext(Dispatchers.IO) { AppUpdater.fetchLatestRelease() }
                updatePrefs.edit().putLong("last_check_ms", System.currentTimeMillis()).apply()
                val curVer = _updateState.value.currentVersion
                val newer = AppUpdater.isNewer(rel.versionName, curVer)
                val cachedApk = targetApkFile(rel)
                val readyPath = if (
                    newer &&
                    cachedApk.exists() &&
                    cachedApk.length() > 1024L &&
                    (rel.apkSize <= 0L || cachedApk.length() == rel.apkSize)
                ) {
                    cachedApk.absolutePath
                } else null
                val ignored = updatePrefs.getString("ignored_tag", "").orEmpty()
                if (manual && newer) {
                    updatePrefs.edit().remove("ignored_tag").apply()
                }
                val popup = newer && (manual || rel.tagName != ignored)
                _updateState.update {
                    it.copy(
                        checking = false,
                        checkedOnce = true,
                        latest = rel,
                        hasUpdate = newer,
                        downloadedFilePath = readyPath,
                        error = null,
                        showDialog = if (popup) true else it.showDialog
                    )
                }
                if (!newer && manual) {
                    toast("当前已是最新版本（v$curVer）")
                }
            } catch (e: CancellationException) {
                _updateState.update { it.copy(checking = false) }
                throw e
            } catch (e: Throwable) {
                val msg = e.message?.takeIf { it.isNotBlank() } ?: "检查更新失败"
                _updateState.update {
                    it.copy(
                        checking = false,
                        error = if (manual) msg else it.error
                    )
                }
                if (manual) {
                    toast("检查更新失败：$msg")
                }
            }
        }
    }

    fun openUpdateDialog() {
        if (_updateState.value.latest != null) {
            _updateState.update { it.copy(showDialog = true) }
        }
    }

    fun dismissUpdateDialog() {
        _updateState.update { it.copy(showDialog = false) }
    }

    fun ignoreCurrentUpdate() {
        val tag = _updateState.value.latest?.tagName.orEmpty()
        if (tag.isNotEmpty()) {
            updatePrefs.edit().putString("ignored_tag", tag).apply()
        }
        _updateState.update { it.copy(showDialog = false) }
    }

    fun startUpdateDownload() {
        val cur = _updateState.value
        val rel = cur.latest ?: return
        if (cur.downloading) return
        if (rel.apkUrl.isBlank()) {
            openExternalUrl(rel.htmlUrl)
            return
        }
        val ready = cur.downloadedFilePath?.let { File(it) }
        if (ready != null && ready.exists() && ready.length() > 1024L) {
            installDownloadedUpdate()
            return
        }
        val target = targetApkFile(rel)
        if (target.exists() && target.length() > 1024L && (rel.apkSize <= 0L || target.length() == rel.apkSize)) {
            _updateState.update { it.copy(downloadedFilePath = target.absolutePath) }
            installDownloadedUpdate()
            return
        }

        _updateState.update {
            it.copy(
                downloading = true,
                downloadPct = 0,
                downloadedBytes = 0L,
                totalBytes = rel.apkSize.coerceAtLeast(0L),
                downloadedFilePath = null,
                error = null
            )
        }
        updateDownloadJob = viewModelScope.launch {
            try {
                val saved = withContext(Dispatchers.IO) {
                    AppUpdater.downloadApk(
                        url = rel.apkUrl,
                        targetFile = target,
                        expectedSize = rel.apkSize,
                        expectedSha256 = rel.sha256,
                        isCancelled = { !isActive },
                        onProgress = { downloaded, total, pct ->
                            _updateState.update {
                                it.copy(
                                    downloading = true,
                                    downloadedBytes = downloaded,
                                    totalBytes = if (total > 0L) total else it.totalBytes,
                                    downloadPct = pct
                                )
                            }
                        }
                    )
                }
                _updateState.update {
                    it.copy(
                        downloading = false,
                        downloadPct = 100,
                        downloadedBytes = saved.length(),
                        totalBytes = maxOf(it.totalBytes, saved.length()),
                        downloadedFilePath = saved.absolutePath,
                        error = null
                    )
                }
                installDownloadedUpdate()
            } catch (e: CancellationException) {
                _updateState.update {
                    it.copy(downloading = false, downloadPct = 0, downloadedBytes = 0L)
                }
            } catch (e: Throwable) {
                val msg = e.message?.takeIf { it.isNotBlank() } ?: "下载失败"
                _updateState.update { it.copy(downloading = false, error = msg) }
                toast("下载更新失败：$msg")
            }
        }
    }

    fun cancelUpdateDownload() {
        updateDownloadJob?.cancel()
        updateDownloadJob = null
        _updateState.update { it.copy(downloading = false, downloadPct = 0, downloadedBytes = 0L) }
    }

    fun installDownloadedUpdate() {
        val path = _updateState.value.downloadedFilePath
        if (path.isNullOrBlank()) {
            toast("安装包尚未下载完成")
            return
        }
        val file = File(path)
        if (!file.exists() || file.length() <= 1024L) {
            _updateState.update { it.copy(downloadedFilePath = null) }
            toast("安装包不存在，请重新下载")
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !ctx.packageManager.canRequestPackageInstalls()) {
            toast("请先开启「允许安装未知应用」，返回后再点立即安装")
            try {
                val permIntent = Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:${ctx.packageName}")
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                ctx.startActivity(permIntent)
            } catch (_: Throwable) {
                toast("请前往系统设置授予安装未知应用权限")
            }
            return
        }
        try {
            val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", file)
            val installIntent = Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            ctx.startActivity(installIntent)
        } catch (e: Throwable) {
            toast("无法调起系统安装器：${e.message}")
        }
    }
}
