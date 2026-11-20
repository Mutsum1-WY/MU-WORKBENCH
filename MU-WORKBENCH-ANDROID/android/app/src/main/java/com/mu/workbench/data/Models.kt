package com.mu.workbench.data

enum class ThemeStyle(val key: String, val label: String, val desc: String) {
    P5R("p5r", "P5R · 红黑斜切", "Persona 5 Royal 视觉致敬：米纸底、粗黑描边、硬投影与斜切角标。"),
    P3R("p3r", "P3R · 蓝白斜切", "Persona 3 Reload 视觉致敬：蓝白斜切、冰青高光与白昼/月夜双模式。"),
    IOS("ios", "iOS · 灵动流体", "原生 Jetpack Compose 物理弹簧驱动：柔和阻尼过渡、圆润分组卡片与毛玻璃层次。");

    companion object {
        fun fromKey(k: String?): ThemeStyle = entries.firstOrNull { it.key == k } ?: P5R
    }
}

enum class AccentStyle(val key: String, val label: String, val hex: Long, val hex2: Long) {
    BLUE("blue", "主题默认", 0xFF007AFF, 0xFF5AC8FA),
    RED("red", "绯红", 0xFFE4002B, 0xFFFF3355),
    CYAN("cyan", "青碧", 0xFF0D7F86, 0xFF14B8A6),
    VIOLET("violet", "紫电", 0xFF5B32C9, 0xFF8B5CF6),
    MINT("mint", "翠绿", 0xFF0F7A4F, 0xFF22C55E),
    SUNSET("sunset", "落日", 0xFFD94A1E, 0xFFF59E0B),
    ROSE("rose", "樱粉", 0xFFC2185B, 0xFFFB7185),
    P3BLUE("p3blue", "月蓝", 0xFF015FCC, 0xFF16CFFB);

    fun previewHex(theme: ThemeStyle): Long = when (this) {
        BLUE -> when (theme) {
            ThemeStyle.IOS -> 0xFF007AFF
            ThemeStyle.P3R -> 0xFF015FCC
            ThemeStyle.P5R -> 0xFFE4002B
        }
        else -> hex
    }

    fun displayLabel(theme: ThemeStyle): String = when (this) {
        BLUE -> when (theme) {
            ThemeStyle.IOS -> "经典蓝（默认）"
            ThemeStyle.P3R -> "月海蓝（默认）"
            ThemeStyle.P5R -> "怪盗红（默认）"
        }
        else -> label
    }

    companion object {
        fun fromKey(k: String?): AccentStyle = entries.firstOrNull { it.key == k } ?: BLUE
    }
}

enum class TodoType(val key: String, val label: String, val desc: String) {
    ONCE("once", "单次", "仅在指定日期出现；逾期未做会自动顺延到今天"),
    DAILY("daily", "每日", "每天都会出现在今日待办"),
    WORKDAY("workday", "工作日", "周一至周五，自动跳过法定节假日，调休补班日自动包含"),
    HOLIDAY("holiday", "休息日", "周末及法定节假日出现，自动跳过调休工作日"),
    WEEKDAYS("weekdays", "自定义星期", "按选定的星期几重复"),
    MONTHLY_DAYS("monthly_days", "每月几号", "按选定的每月 1–31 日重复；当月无此日期时自动跳过"),
    INTERVAL_DAYS("interval_days", "每 N 天", "从起始日起每隔 N 天出现一次");

    companion object {
        fun fromKey(k: String?): TodoType = entries.firstOrNull { it.key == k } ?: ONCE
    }
}

enum class TodoPriority(val key: String, val label: String, val colorHex: Long) {
    HIGH("high", "高", 0xFFFF5D6C),
    MID("mid", "中", 0xFFFF9F2E),
    LOW("low", "低", 0xFF12B981);

    companion object {
        fun fromKey(k: String?): TodoPriority = entries.firstOrNull { it.key == k } ?: MID
    }
}

data class SubStep(
    val id: String,
    val text: String,
    val done: Boolean = false
)

data class TodoItem(
    val id: String,
    val title: String,
    val type: TodoType = TodoType.ONCE,
    val date: String = "",                  // YYYY-MM-DD (once / interval start)
    val weekdays: List<Int> = emptyList(),  // 0=Sun..6=Sat
    val monthDays: List<Int> = emptyList(), // 1..31
    val interval: Int = 2,
    val priority: TodoPriority = TodoPriority.MID,
    val tag: String = "学习",
    val remindTime: String = "",            // HH:mm
    val note: String = "",
    val subSteps: List<SubStep> = emptyList(),
    val doneDates: Set<String> = emptySet(),
    val postponedUntil: String = "",        // YYYY-MM-DD
    val createdAt: Long = System.currentTimeMillis()
)

data class SkillItem(
    val id: String,
    val name: String,
    val progress: Int = 0
)

data class ProjectCard(
    val id: String,
    val title: String,
    val emoji: String = "🎯",
    val colorKey: String = "blue",
    val deadline: String = "",
    val items: List<SkillItem> = emptyList()
)

data class HolidayEntry(
    val off: Boolean,
    val name: String = ""
)

data class PeriodSlot(
    val no: Int,
    val time: String
)

data class CourseSegment(
    val weeks: String,
    val room: String
)

data class CourseScheduleItem(
    val name: String,
    val teacher: String,
    val weekdays: List<Int>, // 1=Mon..7=Sun
    val start: Int,
    val end: Int,
    val weeks: String,
    val room: String,
    val practice: Boolean = false,
    val segments: List<CourseSegment> = emptyList()
)

data class TimetableData(
    val semester: String = "",
    val semesterLabel: String = "",
    val startMonday: String = "",
    val totalWeeks: Int = 19,
    val fetched: String = "",
    val source: String = "",
    val periods: List<PeriodSlot> = emptyList(),
    val courses: List<CourseScheduleItem> = emptyList()
)

data class TimetableConfig(
    val user: String = "",
    val pwd: String = "",
    val auto: Boolean = true,
    val cookie: String = "",
    val startMonday: String = "",
    val totalWeeks: Int = 0,
    val data: TimetableData? = null,
    val fetched: String = "",
    val lastOk: Long = 0L,
    val lastError: String = "",
    val trace: List<String> = emptyList()
)

data class UcloudTodoItem(
    val id: String,
    val title: String,
    val endTime: String = "",
    val siteName: String = "",
    val type: Int = 3,
    val activityType: String = "",
    val content: String = ""
)

data class UcloudDetailCache(
    val content: String = "",
    val fullMark: Double = 0.0,
    val chapter: String = "",
    val attachments: Int = 0,
    val beginTime: String = "",
    val endTime: String = "",
    val fetchedAt: Long = 0L,
    val error: String = ""
)

data class UcloudConfig(
    val on: Boolean = true,
    val id: String = "",
    val pwd: String = "",
    val token: String = "",
    val refreshToken: String = "",
    val userId: String = "",
    val userInfoId: String = "",
    val identity: String = "",
    val roleId: String = "",
    val roleName: String = "",
    val tokenAt: Long = 0L,
    val lastSync: Long = 0L,
    val lastError: String = "",
    val alertOn: Boolean = true,
    val seen: Set<String> = emptySet(),
    val seenInit: Boolean = false,
    val courseMap: Map<String, String> = emptyMap(),
    val details: Map<String, UcloudDetailCache> = emptyMap(),
    val trace: List<String> = emptyList(),
    val items: List<UcloudTodoItem> = emptyList(),
    val done: Set<String> = emptySet()
)

data class HwTerm(
    val id: String,
    val name: String,
    val isCurrent: Boolean = false
)

data class HwCourse(
    val siteId: String,
    val termId: String,
    val name: String,
    val teacher: String = "",
    val termName: String = ""
)

data class HwAttachment(
    val resourceId: String,
    val name: String,
    val sizeText: String = "",
    val ext: String = "",
    val recordFlag: Int = 0
)

data class HwExcellentItem(
    val audiencesId: String,
    val teamId: String = "",
    val userId: String,
    val account: String,
    val realName: String,
    val className: String,
    val score: String,
    val submitTime: String,
    val content: String = "",
    val comment: String = "",
    val files: List<HwAttachment> = emptyList()
)

data class HwSubmissionItem(
    val id: String,
    val siteId: String,
    val courseName: String,
    val title: String,
    val chapterName: String = "",
    val submitTime: String = "",
    val gradeTime: String = "",
    val gradeLocal: Boolean = false,
    val endTime: String = "",
    val isOverTime: Boolean = false,
    val status: Int = 0,              // 0=待批改 1=已批改 2=被驳回 99=未提交
    val score: String = "",
    val comment: String = "",
    val groupScore: String = "",
    val evalAvgScore: String = "",
    val evalInfos: String = "",
    val myContent: String = "",
    val myFiles: List<HwAttachment> = emptyList(),
    val excellent: List<HwExcellentItem> = emptyList(),
    val mergedCount: Int = 1,
    val gradedSeenAt: Long = 0L
)

data class HwTermCache(
    val termId: String,
    val fetchedAt: Long = 0L,
    val items: List<HwSubmissionItem> = emptyList(),
    val err: String = ""
)

data class HwArchiveState(
    val terms: List<HwTerm> = emptyList(),
    val courses: List<HwCourse> = emptyList(),
    val byTerm: Map<String, HwTermCache> = emptyMap(),
    val fetchedAt: Long = 0L,
    val lastError: String = "",
    val gradedSeen: Map<String, Long> = emptyMap()
)

data class PortalAttachment(
    val name: String,
    val url: String
)

data class PortalBlock(
    val type: String,          // "p", "h", "li", "q", "hr", "img"
    val text: String = "",
    val src: String = "",      // 绝对图片 URL
    val altSrc: String = "",   // 备用原图 URL（如 VSB orisrc）
    val level: Int = 2,
    val width: Int = 0
)

data class PortalLectureMeta(
    val whenText: String = "",
    val who: String = "",
    val host: String = "",
    val where: String = "",
    val abstractText: String = ""
)

data class PortalArticle(
    val id: String,
    val kind: String, // "notice" or "lecture"
    val title: String,
    val url: String,
    val dept: String = "",
    val date: String = "",
    val bodyText: String = "",
    val blocks: List<PortalBlock> = emptyList(),
    val imageCount: Int = 0,
    val lecture: PortalLectureMeta? = null,
    val detailFetched: Boolean = false,
    val attachments: List<PortalAttachment> = emptyList(),
    val isNew: Boolean = false
)

data class PortalConfig(
    val on: Boolean = true,
    val kind: String = "notice",
    val cookieJarJson: String = "",
    val lastSync: Long = 0L,
    val lastError: String = "",
    val notices: List<PortalArticle> = emptyList(),
    val lectures: List<PortalArticle> = emptyList(),
    val seen: Set<String> = emptySet(),
    val seenInit: Boolean = false
)

data class CourseMajorInfo(
    val key: String,
    val shortName: String,
    val fullName: String,
    val structureDesc: String
)

data class CourseCatalogItem(
    val code: String,
    val name: String,
    val module: String,
    val credits: Double,
    val hours: Double,
    val practiceHours: Double,
    val termSpec: String,
    val minTerm: Int,
    val requiredType: String,
    val examType: String,
    val difficulty: Double,
    val zzFlag: Int,
    val note: String
)

data class CourseCatalog(
    val gradCredits: Int,
    val majors: List<CourseMajorInfo>,
    val byMajor: Map<String, List<CourseCatalogItem>>
)

data class BonusEntry(
    val id: String,
    val title: String,
    val pi: Double,
    val tui: Double,
    val ach: Boolean = false,
    val hours: Double = 0.0,
    val cat: String = "竞赛",
    val note: String = ""
)

data class BonusState(
    val entries: List<BonusEntry> = emptyList(),
    val hasCityDc: Boolean = false
)

data class BonusGradeRule(val key: String, val name: String, val rule: String)
data class BonusLevel(val key: String, val name: String, val pi: List<Double>, val tui: List<Double?>, val src: String, val note: String = "")
data class BonusComp(val level: String, val name: String)
data class BonusOther(val key: String, val cat: String, val name: String, val pi: Double, val tui: Double, val h: Double, val p: Double, val ach: Boolean, val note: String = "")
data class BonusStep(val title: String, val desc: String, val src: String)
data class BonusDoc(val title: String, val no: String, val org: String, val url: String, val keyPoint: String, val primary: Boolean = false)

data class BonusData(
    val grades: List<BonusGradeRule>,
    val levels: List<BonusLevel>,
    val comps: List<BonusComp>,
    val others: List<BonusOther>,
    val steps: List<BonusStep>,
    val traps: List<String>,
    val docs: List<BonusDoc>
)

data class ZzCustomCourse(
    val id: String,
    val name: String,
    val credit: String,
    val score: String
)

data class ZzDeductItem(
    val key: String,
    val label: String,
    val desc: String,
    val minPoint: Double = 0.0,
    val maxPoint: Double
)

data class ZzDevItem(
    val key: String,
    val category: String,
    val label: String,
    val maxPoint: Double,
    val desc: String = ""
)

data class ZzRuleSection(
    val no: String,
    val title: String,
    val body: String
)

data class ZzRuleChapter(
    val no: String,
    val title: String,
    val sections: List<ZzRuleSection>
)

data class ZzData(
    val deductItems: List<ZzDeductItem>,
    val devItems: List<ZzDevItem>,
    val notes: List<String>,
    val principles: List<String> = emptyList(),
    val chapters: List<ZzRuleChapter> = emptyList(),
    val metaTitle: String = "北京邮电大学本科生综合素质评价办法",
    val metaOrg: String = "北京邮电大学学生处",
    val metaUrl: String = ""
)

data class ZzState(
    val major: String = "se",
    val selfScore: String = "",
    val peerScore: String = "",
    val counselorScore: String = "",
    val deduct: Map<String, String> = emptyMap(),
    val courseScores: Map<String, String> = emptyMap(),
    val customCourses: List<ZzCustomCourse> = emptyList(),
    val physicalTest: String = "",
    val exerciseScore: String = "",
    val activityEval: String = "",
    val devScores: Map<String, String> = emptyMap()
)

data class SettingsState(
    val theme: ThemeStyle = ThemeStyle.P5R,
    val accent: AccentStyle = AccentStyle.BLUE,
    val focusMinutes: Int = 25,
    val breakMinutes: Int = 5,
    val remindOn: Boolean = true,
    val summaryTime: String = "08:30",
    val summaryOn: Boolean = false,
    val soundLevel: Int = 2, // 0=静默 1=轻 2=标准
    val keepAliveOn: Boolean = false,
    val bgCheckOn: Boolean = false,
    val bgCheckUc: Boolean = true,
    val bgCheckPt: Boolean = true,
    val bgCheckWifiOnly: Boolean = false,
    val courseMajor: String = "se",
    val kExam: Double = 1.25,
    val kDiff: Double = 1.0,
    val kOut: Double = 0.15
)

data class WorkbenchState(
    val todos: List<TodoItem> = emptyList(),
    val projects: List<ProjectCard> = emptyList(),
    val settings: SettingsState = SettingsState(),
    val holidayManual: Map<String, HolidayEntry> = emptyMap(),
    val holidayCache: Map<String, Map<String, HolidayEntry>> = emptyMap(),
    val todayPomos: Int = 0,
    val todayPomoDate: String = "",
    val timetable: TimetableConfig = TimetableConfig(),
    val ucloud: UcloudConfig = UcloudConfig(),
    val portal: PortalConfig = PortalConfig(),
    val hw: HwArchiveState = HwArchiveState(),
    val bonus: BonusState = BonusState(),
    val zz: ZzState = ZzState()
)
