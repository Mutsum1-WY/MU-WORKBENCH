package com.mu.workbench.domain

import com.mu.workbench.data.HolidayEntry
import com.mu.workbench.data.TodoItem
import com.mu.workbench.data.TodoType
import com.mu.workbench.data.WorkbenchState
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

object TodoEngine {

    private val FMT = DateTimeFormatter.ISO_LOCAL_DATE

    fun todayKey(): String = LocalDate.now().format(FMT)

    fun parseDate(k: String): LocalDate? = try {
        LocalDate.parse(k, FMT)
    } catch (_: Throwable) {
        null
    }

    fun formatDate(d: LocalDate): String = d.format(FMT)

    /** 节假日判定优先级：手动标记 > 联网缓存 > 内置 2023-2026 */
    fun holidayOf(
        dateKey: String,
        state: WorkbenchState,
        builtin: Map<String, HolidayEntry>
    ): HolidayEntry? {
        state.holidayManual[dateKey]?.let { return it }
        val year = dateKey.take(4)
        state.holidayCache[year]?.get(dateKey)?.let { return it }
        return builtin[dateKey]
    }

    fun isWorkday(
        date: LocalDate,
        state: WorkbenchState,
        builtin: Map<String, HolidayEntry>
    ): Boolean {
        val key = formatDate(date)
        val h = holidayOf(key, state, builtin)
        if (h != null) return !h.off
        return date.dayOfWeek != DayOfWeek.SATURDAY && date.dayOfWeek != DayOfWeek.SUNDAY
    }

    /** 将 java.time.DayOfWeek 转换为 JS getDay() 口径：0=周日, 1=周一 ... 6=周六 */
    fun jsDayOfWeek(date: LocalDate): Int = when (date.dayOfWeek) {
        DayOfWeek.SUNDAY -> 0
        DayOfWeek.MONDAY -> 1
        DayOfWeek.TUESDAY -> 2
        DayOfWeek.WEDNESDAY -> 3
        DayOfWeek.THURSDAY -> 4
        DayOfWeek.FRIDAY -> 5
        DayOfWeek.SATURDAY -> 6
    }

    fun hitsOnDate(
        todo: TodoItem,
        dateKey: String,
        state: WorkbenchState,
        builtin: Map<String, HolidayEntry>
    ): Boolean {
        val date = parseDate(dateKey) ?: return false
        return when (todo.type) {
            TodoType.ONCE -> {
                val base = todo.postponedUntil.ifEmpty { todo.date }
                base == dateKey
            }
            TodoType.DAILY -> true
            TodoType.WORKDAY -> isWorkday(date, state, builtin)
            TodoType.HOLIDAY -> !isWorkday(date, state, builtin)
            TodoType.WEEKDAYS -> jsDayOfWeek(date) in todo.weekdays
            TodoType.MONTHLY_DAYS -> date.dayOfMonth in todo.monthDays
            TodoType.INTERVAL_DAYS -> {
                val start = parseDate(todo.date) ?: return false
                val diff = ChronoUnit.DAYS.between(start, date)
                val step = todo.interval.coerceAtLeast(1)
                diff >= 0 && (diff % step == 0L)
            }
        }
    }

    fun isDoneOnDate(todo: TodoItem, dateKey: String): Boolean {
        return if (todo.type == TodoType.ONCE) todo.doneDates.isNotEmpty()
        else dateKey in todo.doneDates
    }

    data class TodayOccurrence(
        val todo: TodoItem,
        val overdueDays: Long = 0L,
        val isDone: Boolean = false
    )

    fun todayItems(
        state: WorkbenchState,
        builtin: Map<String, HolidayEntry>,
        today: LocalDate = LocalDate.now()
    ): List<TodayOccurrence> {
        val tk = formatDate(today)
        val out = mutableListOf<TodayOccurrence>()
        for (t in state.todos) {
            if (t.type == TodoType.ONCE) {
                val target = parseDate(t.postponedUntil.ifEmpty { t.date })
                if (target != null) {
                    val diff = ChronoUnit.DAYS.between(target, today)
                    val done = isDoneOnDate(t, tk)
                    if (diff == 0L) {
                        out += TodayOccurrence(t, 0L, done)
                    } else if (diff > 0L && !done) {
                        // 逾期未完成自动顺延到今日并标出逾期天数
                        out += TodayOccurrence(t, diff, false)
                    }
                }
            } else if (hitsOnDate(t, tk, state, builtin)) {
                out += TodayOccurrence(t, 0L, isDoneOnDate(t, tk))
            }
        }
        return out.sortedWith(
            compareBy<TodayOccurrence> { it.isDone }
                .thenByDescending { it.overdueDays }
                .thenBy { it.todo.priority.ordinal }
                .thenBy { it.todo.remindTime.ifEmpty { "99:99" } }
        )
    }

    fun dayItems(
        dateKey: String,
        state: WorkbenchState,
        builtin: Map<String, HolidayEntry>
    ): List<TodayOccurrence> {
        return state.todos
            .filter { hitsOnDate(it, dateKey, state, builtin) }
            .map { TodayOccurrence(it, 0L, isDoneOnDate(it, dateKey)) }
            .sortedWith(compareBy<TodayOccurrence> { it.isDone }.thenBy { it.todo.priority.ordinal })
    }
}
