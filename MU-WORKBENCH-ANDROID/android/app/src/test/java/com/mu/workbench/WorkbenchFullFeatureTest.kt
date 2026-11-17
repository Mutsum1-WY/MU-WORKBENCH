package com.mu.workbench

import com.mu.workbench.data.AssetsLoader
import com.mu.workbench.data.CourseScheduleItem
import com.mu.workbench.data.CourseSegment
import com.mu.workbench.data.HolidayEntry
import com.mu.workbench.data.PeriodSlot
import com.mu.workbench.data.SettingsState
import com.mu.workbench.data.SubStep
import com.mu.workbench.data.TimetableData
import com.mu.workbench.data.TodoItem
import com.mu.workbench.data.TodoPriority
import com.mu.workbench.data.TodoType
import com.mu.workbench.data.WorkbenchState
import com.mu.workbench.domain.NativeClients
import com.mu.workbench.domain.ScheduleEngine
import com.mu.workbench.domain.TodoEngine
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.time.LocalDate
import java.time.LocalTime

/**
 * 全功能自动化单元测试套件：
 * 校验待办引擎、节假日/调休规则、强智课表抓取与排课、CAS execution 提取、
 * 防翘课 checkwork 二维码解码、附件 oauthKey MD5，以及 5 份内置 JSON 数据集。
 */
class WorkbenchFullFeatureTest {

    private fun assetFile(name: String): File {
        val candidates = listOf(
            File("src/main/assets/data/$name"),
            File("app/src/main/assets/data/$name"),
            File("android/app/src/main/assets/data/$name")
        )
        return candidates.firstOrNull { it.exists() }
            ?: error("Asset file not found: $name (cwd=${File(".").absolutePath})")
    }

    /* ===================== 1. TodoEngine 全类型与顺延校验 ===================== */

    @Test
    fun todoEngine_coversAllTypes_overdue_andHolidayOverrides() {
        val builtin = mapOf(
            "2026-10-01" to HolidayEntry(off = true, name = "国庆节"),
            "2026-10-10" to HolidayEntry(off = false, name = "国庆调休") // 周六补班
        )
        val todos = listOf(
            TodoItem(id = "once_due", title = "今天单次", type = TodoType.ONCE, date = "2026-10-09", priority = TodoPriority.HIGH),
            TodoItem(id = "once_old", title = "逾期单次", type = TodoType.ONCE, date = "2026-10-06", priority = TodoPriority.MID),
            TodoItem(id = "once_post", title = "推迟到明天", type = TodoType.ONCE, date = "2026-10-08", postponedUntil = "2026-10-10"),
            TodoItem(id = "once_done", title = "已完成单次", type = TodoType.ONCE, date = "2026-10-05", doneDates = setOf("2026-10-05")),
            TodoItem(id = "daily", title = "每日阅读", type = TodoType.DAILY),
            TodoItem(id = "work", title = "工作日打卡", type = TodoType.WORKDAY),
            TodoItem(id = "hol", title = "休息日放松", type = TodoType.HOLIDAY),
            TodoItem(id = "wds", title = "周三五例会", type = TodoType.WEEKDAYS, weekdays = listOf(3, 5)),
            TodoItem(id = "mon", title = "每月10号交房租", type = TodoType.MONTHLY_DAYS, monthDays = listOf(10, 31)),
            TodoItem(id = "intv", title = "每3天背单词", type = TodoType.INTERVAL_DAYS, date = "2026-10-01", interval = 3)
        )
        val state = WorkbenchState(
            todos = todos,
            holidayManual = mapOf("2026-10-12" to HolidayEntry(off = true, name = "校庆放假"))
        )

        // 2026-10-09 (周五，正常工作日)
        val oct09 = LocalDate.of(2026, 10, 9)
        val occ09 = TodoEngine.todayItems(state, builtin, oct09)
        val ids09 = occ09.map { it.todo.id }.toSet()
        assertTrue(ids09.containsAll(listOf("once_due", "once_old", "daily", "work", "wds")))
        assertFalse("已推迟的不应在10-09出现", "once_post" in ids09)
        assertFalse("已完成单次不应再出现", "once_done" in ids09)
        assertEquals(3L, occ09.first { it.todo.id == "once_old" }.overdueDays)

        // 2026-10-10 (周六但为国庆调休补班：isWorkday=true，Monthly 10号命中，Interval (9 % 3 == 0) 命中)
        val oct10 = LocalDate.of(2026, 10, 10)
        assertTrue(TodoEngine.isWorkday(oct10, state, builtin))
        val occ10 = TodoEngine.todayItems(state, builtin, oct10).map { it.todo.id }.toSet()
        assertTrue(occ10.containsAll(listOf("once_post", "work", "mon", "intv", "daily")))
        assertFalse("补班日不算休息日", "hol" in occ10)

        // 2026-10-01 (周四但为国庆放假) & 2026-10-12 (周一但手动设为放假)
        assertFalse(TodoEngine.isWorkday(LocalDate.of(2026, 10, 1), state, builtin))
        assertFalse(TodoEngine.isWorkday(LocalDate.of(2026, 10, 12), state, builtin))
        assertTrue(TodoEngine.hitsOnDate(todos.first { it.id == "hol" }, "2026-10-12", state, builtin))
    }

    /* ===================== 2. 强智教务课表解析 & 排课引擎校验 ===================== */

    @Test
    fun scheduleEngine_andJwParsers_workEndToEnd() {
        assertEquals("MjAyNDIxMDAwMQ==", NativeClients.jwEncodeInp("2024210001"))
        assertEquals("1-16(周)", NativeClients.jwNormWeeks("1-16(周)"))
        assertEquals("1-15(周)(单)", NativeClients.jwNormWeeks("1-15周(单)"))
        assertEquals(1 to 2, NativeClients.jwPeriodRange("[01-02节]"))
        assertEquals(3 to 4, NativeClients.jwPeriodRange("[03-04 节]"))

        val weekCalendarHtml = """
            <div>2026-2027学年 第1学期 教学周历</div>
            <table id="kbtable">
              <tr><th>第1周</th><td title="2026年09月02日">9.2</td></tr>
              <tr><th>第19周</th><td title="2027年01月10日">1.10</td></tr>
            </table>
        """.trimIndent()
        val (startMon, totalWk, semLabel) = NativeClients.parseJwWeeksPage(weekCalendarHtml)
        assertEquals("2026-08-31", startMon) // 2026-09-02 (周三) 所在周周一为 2026-08-31
        assertEquals(19, totalWk)
        assertEquals("2026-2027 学年 第 1 学期", semLabel)

        val kbHtml = """
            <table id="kbtable">
              <tr><th>节次</th><th>星期一</th><th>星期二</th><th>星期三</th><th>星期四</th><th>星期五</th><th>星期六</th><th>星期日</th></tr>
              <tr>
                <td>第1节</td>
                <td rowspan="2"><div class="kbcontent">数据结构与算法<br/>教师:张教授<br/>1-16(周)<br/>教三-301[01-02节]</div></td>
                <td></td><td></td><td></td><td></td><td></td><td></td>
              </tr>
              <tr><td>第2节</td><td></td><td></td><td></td><td></td><td></td><td></td></tr>
            </table>
        """.trimIndent()
        val periods = listOf(PeriodSlot(1, "08:00-08:45"), PeriodSlot(2, "08:50-09:35"))
        val parsedCourses = NativeClients.parseJwKbHtml(kbHtml, periods, 19)
        assertEquals(1, parsedCourses.size)
        val course = parsedCourses[0]
        assertEquals("数据结构与算法", course.name)
        assertEquals("张教授", course.teacher)
        assertEquals(listOf(1), course.weekdays)
        assertEquals(1, course.start)
        assertEquals(2, course.end)
        assertEquals("1-16(周)", course.weeks)
        assertEquals("教三-301", course.room)

        // 验证单双周/分段周/实时上课进度
        val tt = TimetableData(
            semester = "2026-2027-1",
            startMonday = "2026-08-31",
            totalWeeks = 19,
            periods = periods,
            courses = listOf(
                course,
                CourseScheduleItem(
                    name = "软件工程实践",
                    teacher = "李老师,王老师,赵老师",
                    weekdays = listOf(1),
                    start = 1,
                    end = 2,
                    weeks = "",
                    room = "科研楼",
                    practice = true,
                    segments = listOf(CourseSegment("10-10(周)", "实验室A201"))
                )
            )
        )
        assertTrue(ScheduleEngine.weekHit("1-15(周)(单)", 3))
        assertFalse(ScheduleEngine.weekHit("1-15(周)(单)", 4))
        assertEquals("实验室A201", ScheduleEngine.roomForWeek(tt.courses[1], 10))
        assertEquals("李老师 等 3 人", ScheduleEngine.formatTeacherShort(tt.courses[1].teacher))

        val dayCls = ScheduleEngine.classesForDay(tt, week = 10, weekday = 1, now = LocalTime.of(8, 20))
        assertEquals(2, dayCls.size)
        assertEquals(ScheduleEngine.ClassStatus.LIVE, dayCls[0].status)
        assertEquals("实验室A201", dayCls[1].room)
    }

    /* ===================== 3. CAS execution 提取 & 防翘课扫码签到校验 ===================== */

    @Test
    fun casExecExtraction_andAntiTruancyQrParsing_matchWebImpl() {
        // 1. 属性顺序打乱 + 无 type + &amp; 转义的 6KB+ execution
        val longToken = "e1s1_" + "A".repeat(6500) + "&amp;end"
        val expectedExec = "e1s1_" + "A".repeat(6500) + "&end"
        val htmlReordered = """<form><input value="$longToken" name="execution" id="execution"/></form>"""
        assertEquals(expectedExec, NativeClients.execOf(htmlReordered))

        // 2. 防翘课 checkwork|...：必须对 createTime URLDecode 且使用 OAuth userId
        val rawCheckwork = "checkwork|id=99887766&siteId=112233&createTime=2026-11-18%2008%3A05%3A12&classLessonId=5566"
        val body = NativeClients.ucSignCheckedBody(rawCheckwork, "oauth_uid_42")
        assertNotNull(body)
        val info = body!!.getJSONObject("attendanceDetailInfo")
        assertEquals("99887766", info.getString("attendanceId"))
        assertEquals("112233", info.getString("siteId"))
        assertEquals("oauth_uid_42", info.getString("userId"))
        assertEquals("5566", info.getString("classLessonId"))
        assertEquals("2026-11-18 08:05:12", body.getString("qrCodeCreateTime"))

        // checkwork 模式仅生成唯一官方请求体，不乱猜字段
        assertEquals(1, NativeClients.ucSignCandidates(rawCheckwork, "oauth_uid_42").size)

        // 3. 普通 URL 二维码候选排序
        val urlCands = NativeClients.ucSignCandidates("https://ucloud.bupt.edu.cn/sign/abc?siteId=10&code=999", "u1")
        assertTrue(urlCands.size >= 3)
        assertEquals("999", urlCands[0].getString("code"))
        assertEquals("https://ucloud.bupt.edu.cn/sign/abc?siteId=10&code=999", urlCands[1].getString("url"))

        // 4. 附件免鉴权 oauthKey MD5 长度与一致性校验
        val k = NativeClients.oauthKeyFor("res_123456")
        assertEquals(32, k.length)
        assertTrue(k.matches(Regex("[0-9a-f]{32}")))
    }

    /* ===================== 4. 全量内置静态数据集 & 计算公式回归 ===================== */

    @Test
    fun builtinAssets_allFiveJsonFiles_parseAndCalculateCorrectly() {
        // 1. holidays.json
        val hRoot = JSONObject(assetFile("holidays.json").readText(Charsets.UTF_8))
        assertTrue(hRoot.length() >= 3)

        // 2. kb_builtin.json
        val kbObj = JSONObject(assetFile("kb_builtin.json").readText(Charsets.UTF_8))
        val kbData = AssetsLoader.parseTimetableJson(kbObj)
        assertTrue("内置节次应>=12节", kbData.periods.size >= 12)
        assertTrue("内置课表应含课程", kbData.courses.isNotEmpty())
        val week1 = ScheduleEngine.classesForDay(kbData, week = 1, weekday = 1, now = LocalTime.of(9, 0))
        assertNotNull(week1)

        // 3. cr_data.json
        val crRoot = JSONObject(assetFile("cr_data.json").readText(Charsets.UTF_8))
        val majors = crRoot.getJSONArray("majors")
        val pool = crRoot.getJSONArray("pool")
        assertTrue(majors.length() >= 1)
        assertTrue(pool.length() >= 20)

        // 4. bonus_data.json
        val bRoot = JSONObject(assetFile("bonus_data.json").readText(Charsets.UTF_8))
        assertTrue(bRoot.getJSONArray("comps").length() >= 30)
        assertTrue(bRoot.getJSONArray("levels").length() >= 4)

        // 5. zz_data.json
        val zRoot = JSONObject(assetFile("zz_data.json").readText(Charsets.UTF_8))
        assertTrue(zRoot.getJSONObject("basic").getJSONArray("deduct").length() >= 1)
        assertTrue(zRoot.getJSONObject("dev").getJSONObject("other").getJSONArray("items").length() >= 1)
        assertTrue(zRoot.getJSONArray("chapters").length() >= 3)
    }
}
