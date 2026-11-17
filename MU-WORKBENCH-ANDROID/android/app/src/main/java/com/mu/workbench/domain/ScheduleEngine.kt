package com.mu.workbench.domain

import com.mu.workbench.data.CourseScheduleItem
import com.mu.workbench.data.TimetableData
import java.time.LocalDate
import java.time.LocalTime
import java.time.temporal.ChronoUnit

object ScheduleEngine {

    enum class ClassStatus { PAST, LIVE, UPCOMING }

    data class WeekStatus(
        val week: Int,
        val totalWeeks: Int,
        val inRange: Boolean,
        val daysBeforeStart: Long = 0L
    )

    data class TodayClassInfo(
        val item: CourseScheduleItem,
        val startTime: String,
        val endTime: String,
        val room: String,
        val teacherShort: String,
        val status: ClassStatus,
        val remainingProgress: Float, // 1f -> 0f
        val hintText: String
    )

    data class NextCourseHint(
        val date: LocalDate,
        val weekday: Int,
        val startTime: String,
        val courseName: String,
        val room: String,
        val inDays: Int
    )

    val WD_NAMES = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")

    fun weekStatus(data: TimetableData, today: LocalDate = LocalDate.now()): WeekStatus {
        val total = data.totalWeeks.coerceAtLeast(1)
        val start = TodoEngine.parseDate(data.startMonday) ?: return WeekStatus(1, total, false, 0L)
        val days = ChronoUnit.DAYS.between(start, today)
        if (days < 0) return WeekStatus(0, total, false, -days)
        val week = ((days / 7L) + 1L).toInt()
        return WeekStatus(week, total, week in 1..total, 0L)
    }

    fun currentWeek(data: TimetableData, today: LocalDate = LocalDate.now()): Int {
        val st = weekStatus(data, today)
        return st.week.coerceIn(1, st.totalWeeks)
    }

    fun weekHit(spec: String, week: Int): Boolean {
        if (spec.isBlank()) return false
        val odd = Regex("""[（(]\s*单\s*[)）]|单周""").containsMatchIn(spec)
        val even = Regex("""[（(]\s*双\s*[)）]|双周""").containsMatchIn(spec)
        val clean = spec.replace(Regex("""[（(].*?[)）]"""), "")
        var hit = false
        for (raw in clean.split(",")) {
            val p = raw.trim()
            if (p.isEmpty()) continue
            val m = Regex("""^(\d+)\s*-\s*(\d+)$""").find(p)
            if (m != null) {
                val a = m.groupValues[1].toInt()
                val b = m.groupValues[2].toInt()
                if (week in a..b) { hit = true; break }
            } else if (p.toIntOrNull() == week) {
                hit = true; break
            }
        }
        if (!hit) return false
        if (odd) return week % 2 == 1
        if (even) return week % 2 == 0
        return true
    }

    /** 检查主周次与分段周次（解决如第 10 周实践课只写在 segments 中被漏掉的问题） */
    fun courseActiveInWeek(item: CourseScheduleItem, week: Int): Boolean {
        if (weekHit(item.weeks, week)) return true
        return item.segments.any { weekHit(it.weeks, week) }
    }

    fun roomForWeek(item: CourseScheduleItem, week: Int): String {
        for (sg in item.segments) {
            if (weekHit(sg.weeks, week)) return sg.room
        }
        return item.segments.firstOrNull()?.room?.ifEmpty { item.room } ?: item.room
    }

    fun formatTeacherShort(raw: String): String {
        val arr = raw.split(',', '，').map { it.trim() }.filter { it.isNotEmpty() }
        if (arr.size <= 2) return arr.joinToString("、")
        return "${arr[0]} 等 ${arr.size} 人"
    }

    fun periodSpan(data: TimetableData, start: Int, end: Int): Pair<String, String> {
        val a = data.periods.firstOrNull { it.no == start }?.time ?: ""
        val b = data.periods.firstOrNull { it.no == end }?.time ?: ""
        val s = a.split("-").firstOrNull().orEmpty()
        val e = b.split("-").getOrNull(1) ?: b.split("-").firstOrNull().orEmpty()
        return s to e
    }

    private fun minOf(hhmm: String): Int? {
        val p = hhmm.split(":")
        if (p.size != 2) return null
        val h = p[0].toIntOrNull() ?: return null
        val m = p[1].toIntOrNull() ?: return null
        return h * 60 + m
    }

    private fun formatDurMin(min: Int): String {
        if (min <= 0) return "不到 1 分"
        val h = min / 60
        val m = min % 60
        return when {
            h > 0 && m > 0 -> "$h 小时 $m 分"
            h > 0 -> "$h 小时"
            else -> "$m 分"
        }
    }

    fun classesForDay(
        data: TimetableData,
        week: Int,
        weekday: Int, // 1=Mon..7=Sun
        now: LocalTime = LocalTime.now()
    ): List<TodayClassInfo> {
        val nowSec = now.hour * 3600 + now.minute * 60 + now.second
        return data.courses
            .filter { weekday in it.weekdays && courseActiveInWeek(it, week) }
            .sortedWith(compareBy<CourseScheduleItem> { it.start }.thenBy { it.name })
            .map { c ->
                val (sStr, eStr) = periodSpan(data, c.start, c.end)
                val sMin = minOf(sStr) ?: 0
                val eMin = minOf(eStr) ?: 0
                val sSec = sMin * 60
                val eSec = eMin * 60
                val room = roomForWeek(c, week)
                val tShort = formatTeacherShort(c.teacher)
                when {
                    nowSec >= eSec -> TodayClassInfo(
                        c, sStr, eStr, room, tShort, ClassStatus.PAST, 0f, "已下课"
                    )
                    nowSec in sSec until eSec -> {
                        val total = (eSec - sSec).coerceAtLeast(1)
                        val left = eSec - nowSec
                        val rem = (left.toFloat() / total.toFloat()).coerceIn(0f, 1f)
                        val lm = (left + 59) / 60
                        TodayClassInfo(
                            c, sStr, eStr, room, tShort, ClassStatus.LIVE, rem,
                            "还剩 ${formatDurMin(lm)}"
                        )
                    }
                    else -> {
                        val waitMin = ((sSec - nowSec) + 59) / 60
                        TodayClassInfo(
                            c, sStr, eStr, room, tShort, ClassStatus.UPCOMING, 1f,
                            "距上课 ${formatDurMin(waitMin)}"
                        )
                    }
                }
            }
    }

    fun nextCourseWithin14Days(
        data: TimetableData,
        today: LocalDate = LocalDate.now(),
        now: LocalTime = LocalTime.now()
    ): NextCourseHint? {
        val nowMin = now.hour * 60 + now.minute
        for (d in 0..14) {
            val day = today.plusDays(d.toLong())
            val ws = weekStatus(data, day)
            if (!ws.inRange) continue
            val wd = day.dayOfWeek.value
            val dayCls = classesForDay(data, ws.week, wd, now)
            for (c in dayCls) {
                val sMin = minOf(c.startTime) ?: continue
                if (d == 0 && sMin <= nowMin) continue
                return NextCourseHint(day, wd, c.startTime, c.item.name, c.room, d)
            }
        }
        return null
    }
}
