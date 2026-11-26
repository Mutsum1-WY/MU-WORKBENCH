package com.mu.workbench.data

import android.content.Context
import com.mu.workbench.MuBgCheck
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class WorkbenchRepository(private val ctx: Context) {

    private val file = File(ctx.filesDir, "mu_workbench_v1.json")
    private val saveLock = Any()

    fun load(): WorkbenchState {
        if (!file.exists()) return defaultState()
        return try {
            val txt = file.readText(Charsets.UTF_8)
            fromJson(JSONObject(txt))
        } catch (_: Throwable) {
            defaultState()
        }
    }

    fun save(state: WorkbenchState) {
        synchronized(saveLock) {
            try {
                val tmp = File(ctx.filesDir, "mu_workbench_v1.json.tmp")
                tmp.writeText(toJson(state).toString(), Charsets.UTF_8)
                tmp.renameTo(file)
                syncBgCheckPrefs(state)
            } catch (_: Throwable) {
            }
        }
    }

    fun exportJson(state: WorkbenchState): String = toJson(state).toString(2)

    fun importJson(raw: String): WorkbenchState {
        val state = fromJson(JSONObject(raw))
        save(state)
        return state
    }

    private fun syncBgCheckPrefs(s: WorkbenchState) {
        try {
            val ucUid = s.ucloud.userInfoId.ifEmpty { s.ucloud.userId }
            val ucSeenArr = JSONArray().apply {
                (s.ucloud.seen + s.ucloud.items.map { it.id }).filter { it.isNotEmpty() }.distinct().forEach { put(it) }
            }
            val ptNoticeArr = JSONArray().apply {
                (s.portal.seen + s.portal.notices.map { it.id }).filter { it.isNotEmpty() }.distinct().forEach { put(it) }
            }
            val ptLectureArr = JSONArray().apply {
                (s.portal.seen + s.portal.lectures.map { it.id }).filter { it.isNotEmpty() }.distinct().forEach { put(it) }
            }
            val payload = JSONObject().apply {
                put("enabled", s.settings.bgCheckOn)
                put("ucOn", s.settings.bgCheckUc && s.ucloud.on)
                put("ptOn", s.settings.bgCheckPt && s.portal.on)
                put("wifiOnly", s.settings.bgCheckWifiOnly)
                put("uc", JSONObject().apply {
                    put("token", s.ucloud.token)
                    put("refresh", s.ucloud.refreshToken)
                    put("uid", ucUid)
                    put("identity", s.ucloud.identity)
                    put("roleId", s.ucloud.roleId)
                    put("id", s.ucloud.id)
                    put("pwd", s.ucloud.pwd)
                    put("tokenAt", s.ucloud.tokenAt)
                    put("seen", ucSeenArr)
                })
                put("pt", JSONObject().apply {
                    put("cookie", s.portal.cookieJarJson)
                    put("noticeSeen", ptNoticeArr)
                    put("lectureSeen", ptLectureArr)
                })
            }
            MuBgCheck(ctx).push(payload.toString())
        } catch (_: Throwable) {
        }
    }

    fun defaultState(): WorkbenchState {
        return WorkbenchState(
            projects = listOf(
                ProjectCard(
                    id = "proj_demo_1",
                    title = "个人工作台核心",
                    emoji = "🚀",
                    colorKey = "blue",
                    items = listOf(
                        SkillItem("sk1", "Jetpack Compose 引擎", 100),
                        SkillItem("sk2", "今日待办与课程同步", 80),
                        SkillItem("sk3", "物理弹簧流畅交互", 90)
                    )
                )
            )
        )
    }

    private fun fromJson(o: JSONObject): WorkbenchState {
        val todos = mutableListOf<TodoItem>()
        o.optJSONArray("todos")?.let { arr ->
            for (i in 0 until arr.length()) {
                val t = arr.optJSONObject(i) ?: continue
                val wds = mutableListOf<Int>()
                t.optJSONArray("weekdays")?.let { a -> for (j in 0 until a.length()) wds += a.optInt(j) }
                val mds = mutableListOf<Int>()
                t.optJSONArray("monthDays")?.let { a -> for (j in 0 until a.length()) mds += a.optInt(j) }
                val subs = mutableListOf<SubStep>()
                t.optJSONArray("subSteps")?.let { a ->
                    for (j in 0 until a.length()) {
                        val s = a.optJSONObject(j) ?: continue
                        subs += SubStep(s.optString("id", "s$j"), s.optString("text"), s.optBoolean("done", false))
                    }
                }
                val doneDates = mutableSetOf<String>()
                t.optJSONObject("doneDates")?.let { obj ->
                    val keys = obj.keys()
                    while (keys.hasNext()) {
                        val k = keys.next()
                        if (obj.optBoolean(k, false)) doneDates += k
                    }
                } ?: t.optJSONArray("doneDates")?.let { a ->
                    for (j in 0 until a.length()) doneDates += a.optString(j)
                }
                todos += TodoItem(
                    id = t.optString("id", "t_$i"),
                    title = t.optString("title", "未命名待办"),
                    type = TodoType.fromKey(t.optString("type")),
                    date = t.optString("date"),
                    weekdays = wds,
                    monthDays = mds,
                    interval = t.optInt("interval", 2).coerceAtLeast(1),
                    priority = TodoPriority.fromKey(t.optString("priority")),
                    tag = t.optString("tag", "学习"),
                    remindTime = t.optString("remindTime"),
                    note = t.optString("note"),
                    subSteps = subs,
                    doneDates = doneDates,
                    postponedUntil = t.optString("postponedUntil"),
                    createdAt = t.optLong("createdAt", System.currentTimeMillis())
                )
            }
        }

        val projects = mutableListOf<ProjectCard>()
        o.optJSONArray("projects")?.let { arr ->
            for (i in 0 until arr.length()) {
                val p = arr.optJSONObject(i) ?: continue
                val items = mutableListOf<SkillItem>()
                p.optJSONArray("items")?.let { ia ->
                    for (j in 0 until ia.length()) {
                        val s = ia.optJSONObject(j) ?: continue
                        items += SkillItem(s.optString("id", "sk_$j"), s.optString("name"), s.optInt("progress", 0))
                    }
                }
                projects += ProjectCard(
                    id = p.optString("id", "p_$i"),
                    title = p.optString("title", "未命名项目"),
                    emoji = p.optString("emoji", "🎯"),
                    colorKey = p.optString("colorKey", p.optString("color", "blue")),
                    deadline = p.optString("deadline"),
                    items = items
                )
            }
        }

        val so = o.optJSONObject("settings") ?: JSONObject()
        val settings = SettingsState(
            theme = ThemeStyle.fromKey(so.optString("theme", "p5r")),
            accent = AccentStyle.fromKey(so.optString("accent", "blue")),
            focusMinutes = so.optInt("focus", 25).coerceIn(1, 120),
            breakMinutes = so.optInt("brk", 5).coerceIn(1, 60),
            remindOn = so.optBoolean("remindOn", true),
            summaryTime = so.optString("summaryTime", "08:30"),
            summaryOn = so.optBoolean("summaryOn", false),
            soundLevel = so.optInt("soundLevel", 2).coerceIn(0, 2),
            keepAliveOn = so.optBoolean("keepAliveOn", false),
            bgCheckOn = so.optBoolean("bgCheckOn", false),
            bgCheckUc = so.optBoolean("bgCheckUc", true),
            bgCheckPt = so.optBoolean("bgCheckPt", true),
            bgCheckWifiOnly = so.optBoolean("bgCheckWifiOnly", false),
            courseMajor = so.optString("courseMajor", "se"),
            kExam = so.optDouble("kExam", 1.25),
            kDiff = so.optDouble("kDiff", 1.0),
            kOut = so.optDouble("kOut", 0.15)
        )

        val hManual = LinkedHashMap<String, HolidayEntry>()
        o.optJSONObject("holidayManual")?.let { obj ->
            val ks = obj.keys()
            while (ks.hasNext()) {
                val k = ks.next()
                val v = obj.optJSONObject(k) ?: continue
                hManual[k] = HolidayEntry(v.optBoolean("off", true), v.optString("name"))
            }
        }

        val hCache = LinkedHashMap<String, Map<String, HolidayEntry>>()
        o.optJSONObject("holidayCache")?.let { obj ->
            val ys = obj.keys()
            while (ys.hasNext()) {
                val y = ys.next()
                val mObj = obj.optJSONObject(y) ?: continue
                val map = LinkedHashMap<String, HolidayEntry>()
                val ds = mObj.keys()
                while (ds.hasNext()) {
                    val d = ds.next()
                    val v = mObj.optJSONObject(d) ?: continue
                    map[d] = HolidayEntry(v.optBoolean("off", false), v.optString("name"))
                }
                hCache[y] = map
            }
        }

        val jwObj = o.optJSONObject("timetable") ?: JSONObject()
        val jwDataObj = jwObj.optJSONObject("data")
        val jwTrace = mutableListOf<String>()
        jwObj.optJSONArray("trace")?.let { arr ->
            for (i in 0 until arr.length()) jwTrace += arr.optString(i)
        }
        val timetable = TimetableConfig(
            user = jwObj.optString("user"),
            pwd = jwObj.optString("pwd"),
            auto = jwObj.optBoolean("auto", true),
            cookie = jwObj.optString("cookie"),
            startMonday = jwObj.optString("startMonday"),
            totalWeeks = jwObj.optInt("totalWeeks", 0),
            data = jwDataObj?.let { AssetsLoader.parseTimetableJson(it) },
            fetched = jwObj.optString("fetched"),
            lastOk = jwObj.optLong("lastOk", 0L),
            lastError = jwObj.optString("lastError"),
            trace = jwTrace
        )

        val ucObj = o.optJSONObject("ucloud") ?: JSONObject()
        val ucItems = mutableListOf<UcloudTodoItem>()
        ucObj.optJSONArray("items")?.let { arr ->
            for (i in 0 until arr.length()) {
                val it = arr.optJSONObject(i) ?: continue
                ucItems += UcloudTodoItem(
                    id = it.optString("id", it.optString("activityId")),
                    title = it.optString("title", it.optString("activityName")),
                    endTime = it.optString("endTime"),
                    siteName = it.optString("siteName"),
                    type = it.optInt("type", 3),
                    activityType = it.optString("activityType"),
                    content = it.optString("content")
                )
            }
        }
        val ucDone = mutableSetOf<String>()
        ucObj.optJSONObject("done")?.let { obj ->
            val ks = obj.keys()
            while (ks.hasNext()) {
                val k = ks.next()
                if (obj.optBoolean(k, false)) ucDone += k
            }
        }
        val ucSeen = mutableSetOf<String>()
        ucObj.optJSONObject("seen")?.let { obj ->
            val ks = obj.keys()
            while (ks.hasNext()) {
                val k = ks.next()
                if (obj.optBoolean(k, false)) ucSeen += k
            }
        }
        val ucCourseMap = LinkedHashMap<String, String>()
        ucObj.optJSONObject("courseMap")?.let { obj ->
            val ks = obj.keys()
            while (ks.hasNext()) {
                val k = ks.next()
                ucCourseMap[k] = obj.optString(k)
            }
        }
        val ucDetails = LinkedHashMap<String, UcloudDetailCache>()
        ucObj.optJSONObject("details")?.let { obj ->
            val ks = obj.keys()
            while (ks.hasNext()) {
                val k = ks.next()
                val v = obj.optJSONObject(k) ?: continue
                ucDetails[k] = UcloudDetailCache(
                    content = v.optString("content"),
                    fullMark = v.optDouble("fullMark", 0.0),
                    chapter = v.optString("chapter"),
                    attachments = v.optInt("attachments", 0),
                    beginTime = v.optString("begin", v.optString("beginTime")),
                    endTime = v.optString("end", v.optString("endTime")),
                    fetchedAt = v.optLong("at", v.optLong("fetchedAt", 0L)),
                    error = v.optString("error")
                )
            }
        }
        val ucloud = UcloudConfig(
            on = ucObj.optBoolean("on", true),
            id = ucObj.optString("id"),
            pwd = ucObj.optString("pwd"),
            token = ucObj.optString("token"),
            refreshToken = ucObj.optString("refreshToken"),
            userId = ucObj.optString("userId"),
            userInfoId = ucObj.optString("userInfoId"),
            identity = ucObj.optString("identity"),
            roleId = ucObj.optString("roleId"),
            roleName = ucObj.optString("roleName"),
            tokenAt = ucObj.optLong("tokenAt", 0L),
            lastSync = ucObj.optLong("lastSync", 0L),
            lastError = ucObj.optString("lastError"),
            alertOn = ucObj.optBoolean("alertOn", true),
            seen = ucSeen,
            seenInit = ucObj.optBoolean("seenInit", false),
            courseMap = ucCourseMap,
            details = ucDetails,
            items = ucItems,
            done = ucDone
        )

        val ptObj = o.optJSONObject("portal") ?: JSONObject()
        val ptSeen = mutableSetOf<String>()
        ptObj.optJSONObject("seen")?.let { obj ->
            val ks = obj.keys()
            while (ks.hasNext()) {
                val k = ks.next()
                if (obj.optBoolean(k, false)) ptSeen += k
            }
        }
        fun parsePortalList(arr: JSONArray?): List<PortalArticle> {
            val res = mutableListOf<PortalArticle>()
            if (arr == null) return res
            for (i in 0 until arr.length()) {
                val a = arr.optJSONObject(i) ?: continue
                val atts = mutableListOf<PortalAttachment>()
                a.optJSONArray("attachments")?.let { aa ->
                    for (j in 0 until aa.length()) {
                        val att = aa.optJSONObject(j) ?: continue
                        atts += PortalAttachment(att.optString("name"), att.optString("url"))
                    }
                }
                val blks = mutableListOf<PortalBlock>()
                a.optJSONArray("blocks")?.let { ba ->
                    for (j in 0 until ba.length()) {
                        val b = ba.optJSONObject(j) ?: continue
                        blks += PortalBlock(
                            type = b.optString("type", "p"),
                            text = b.optString("text"),
                            src = b.optString("src"),
                            altSrc = b.optString("altSrc"),
                            level = b.optInt("level", 2),
                            width = b.optInt("width", 0)
                        )
                    }
                }
                val lec = a.optJSONObject("lecture")?.let { lo ->
                    PortalLectureMeta(
                        whenText = lo.optString("whenText"),
                        who = lo.optString("who"),
                        host = lo.optString("host"),
                        where = lo.optString("where"),
                        abstractText = lo.optString("abstractText")
                    )
                }
                res += PortalArticle(
                    id = a.optString("id"),
                    kind = a.optString("kind", "notice"),
                    title = a.optString("title"),
                    url = a.optString("url"),
                    dept = a.optString("dept"),
                    date = a.optString("date"),
                    bodyText = a.optString("bodyText"),
                    blocks = blks,
                    imageCount = a.optInt("imageCount", blks.count { it.type == "img" }),
                    lecture = lec,
                    detailFetched = a.optBoolean("detailFetched", false),
                    attachments = atts
                )
            }
            return res
        }
        val itemsObj = ptObj.optJSONObject("items")
        val portal = PortalConfig(
            on = ptObj.optBoolean("on", true),
            kind = ptObj.optString("kind", "notice"),
            cookieJarJson = ptObj.optString("cookie"),
            lastSync = ptObj.optLong("lastSync", 0L),
            lastError = ptObj.optString("lastError"),
            notices = parsePortalList(itemsObj?.optJSONArray("notice")),
            lectures = parsePortalList(itemsObj?.optJSONArray("lecture")),
            seen = ptSeen,
            seenInit = ptObj.optBoolean("seenInit", false)
        )

        val bObj = o.optJSONObject("bonus") ?: JSONObject()
        val bEntries = mutableListOf<BonusEntry>()
        bObj.optJSONArray("entries")?.let { arr ->
            for (i in 0 until arr.length()) {
                val e = arr.optJSONObject(i) ?: continue
                bEntries += BonusEntry(
                    id = e.optString("id", "b_$i"),
                    title = e.optString("title"),
                    pi = e.optDouble("pi", 0.0),
                    tui = e.optDouble("tui", 0.0),
                    ach = e.optBoolean("ach", false),
                    hours = e.optDouble("hours", 0.0),
                    cat = e.optString("cat", "竞赛"),
                    note = e.optString("note")
                )
            }
        }
        val bonus = BonusState(bEntries, bObj.optBoolean("hasCityDc", false))

        val zObj = o.optJSONObject("zz") ?: JSONObject()
        val zBasic = zObj.optJSONObject("basic") ?: JSONObject()
        val zDeduct = LinkedHashMap<String, String>()
        zBasic.optJSONObject("deduct")?.let { obj ->
            val ks = obj.keys()
            while (ks.hasNext()) { val k = ks.next(); zDeduct[k] = obj.optString(k) }
        }
        val zScores = LinkedHashMap<String, String>()
        zObj.optJSONObject("scores")?.let { obj ->
            val ks = obj.keys()
            while (ks.hasNext()) { val k = ks.next(); zScores[k] = obj.optString(k) }
        }
        val zCustom = mutableListOf<ZzCustomCourse>()
        zObj.optJSONArray("custom")?.let { arr ->
            for (i in 0 until arr.length()) {
                val c = arr.optJSONObject(i) ?: continue
                zCustom += ZzCustomCourse(c.optString("id", "zc_$i"), c.optString("name"), c.optString("credit"), c.optString("score"))
            }
        }
        val zDev = zObj.optJSONObject("dev") ?: JSONObject()
        val zDevMap = LinkedHashMap<String, String>()
        val zDevKeys = zDev.keys()
        while (zDevKeys.hasNext()) {
            val k = zDevKeys.next()
            if (k !in setOf("physicalTest", "exerciseScore", "activityEval")) {
                zDevMap[k] = zDev.optString(k)
            }
        }
        val zz = ZzState(
            major = zObj.optString("major", "se"),
            selfScore = zBasic.optString("self"),
            peerScore = zBasic.optString("peer"),
            counselorScore = zBasic.optString("counselor"),
            deduct = zDeduct,
            courseScores = zScores,
            customCourses = zCustom,
            physicalTest = zDev.optString("physicalTest"),
            exerciseScore = zDev.optString("exerciseScore"),
            activityEval = zDev.optString("activityEval"),
            devScores = zDevMap
        )

        val hwObj = o.optJSONObject("hw") ?: JSONObject()
        val gradedSeen = LinkedHashMap<String, Long>()
        hwObj.optJSONObject("gradedSeen")?.let { obj ->
            val ks = obj.keys()
            while (ks.hasNext()) { val k = ks.next(); gradedSeen[k] = obj.optLong(k, 0L) }
        }
        val hwTerms = mutableListOf<HwTerm>()
        hwObj.optJSONArray("terms")?.let { arr ->
            for (i in 0 until arr.length()) {
                val t = arr.optJSONObject(i) ?: continue
                hwTerms += HwTerm(t.optString("id"), t.optString("name"), t.optBoolean("isCurrent", false))
            }
        }
        val hwCourses = mutableListOf<HwCourse>()
        hwObj.optJSONArray("courses")?.let { arr ->
            for (i in 0 until arr.length()) {
                val c = arr.optJSONObject(i) ?: continue
                hwCourses += HwCourse(
                    siteId = c.optString("siteId"),
                    termId = c.optString("termId"),
                    name = c.optString("name"),
                    teacher = c.optString("teacher"),
                    termName = c.optString("termName")
                )
            }
        }
        fun parseAtts(arr: JSONArray?): List<HwAttachment> {
            val out = mutableListOf<HwAttachment>()
            if (arr == null) return out
            for (i in 0 until arr.length()) {
                val a = arr.optJSONObject(i) ?: continue
                out += HwAttachment(
                    resourceId = a.optString("resourceId"),
                    name = a.optString("name"),
                    sizeText = a.optString("sizeText"),
                    ext = a.optString("ext"),
                    recordFlag = a.optInt("recordFlag", 0)
                )
            }
            return out
        }
        val hwByTerm = LinkedHashMap<String, HwTermCache>()
        hwObj.optJSONObject("byTerm")?.let { obj ->
            val ks = obj.keys()
            while (ks.hasNext()) {
                val tid = ks.next()
                val tc = obj.optJSONObject(tid) ?: continue
                val items = mutableListOf<HwSubmissionItem>()
                tc.optJSONArray("items")?.let { arr ->
                    for (i in 0 until arr.length()) {
                        val it = arr.optJSONObject(i) ?: continue
                        val exList = mutableListOf<HwExcellentItem>()
                        it.optJSONArray("excellent")?.let { ea ->
                            for (k in 0 until ea.length()) {
                                val ex = ea.optJSONObject(k) ?: continue
                                exList += HwExcellentItem(
                                    audiencesId = ex.optString("audiencesId"),
                                    teamId = ex.optString("teamId"),
                                    userId = ex.optString("userId"),
                                    account = ex.optString("account"),
                                    realName = ex.optString("realName"),
                                    className = ex.optString("className"),
                                    score = ex.optString("score"),
                                    submitTime = ex.optString("submitTime"),
                                    content = ex.optString("content"),
                                    comment = ex.optString("comment"),
                                    files = parseAtts(ex.optJSONArray("files"))
                                )
                            }
                        }
                        items += HwSubmissionItem(
                            id = it.optString("id"),
                            siteId = it.optString("siteId"),
                            courseName = it.optString("courseName"),
                            title = it.optString("title"),
                            chapterName = it.optString("chapterName"),
                            submitTime = it.optString("submitTime"),
                            gradeTime = it.optString("gradeTime"),
                            gradeLocal = it.optBoolean("gradeLocal", false),
                            endTime = it.optString("endTime"),
                            isOverTime = it.optBoolean("isOverTime", false),
                            status = it.optInt("status", 0),
                            score = it.optString("score"),
                            comment = it.optString("comment"),
                            groupScore = it.optString("groupScore"),
                            evalAvgScore = it.optString("evalAvgScore"),
                            evalInfos = it.optString("evalInfos"),
                            myContent = it.optString("myContent"),
                            myFiles = parseAtts(it.optJSONArray("myFiles")),
                            excellent = exList,
                            mergedCount = it.optInt("mergedCount", 1),
                            gradedSeenAt = it.optLong("gradedSeenAt", 0L)
                        )
                    }
                }
                hwByTerm[tid] = HwTermCache(
                    termId = tid,
                    fetchedAt = tc.optLong("fetchedAt", 0L),
                    items = items,
                    err = tc.optString("err")
                )
            }
        }

        return WorkbenchState(
            todos = todos,
            projects = projects,
            settings = settings,
            holidayManual = hManual,
            holidayCache = hCache,
            todayPomos = o.optInt("todayPomos", 0),
            todayPomoDate = o.optString("todayPomoDate"),
            timetable = timetable,
            ucloud = ucloud,
            portal = portal,
            hw = HwArchiveState(
                terms = hwTerms,
                courses = hwCourses,
                byTerm = hwByTerm,
                fetchedAt = hwObj.optLong("fetchedAt", 0L),
                lastError = hwObj.optString("lastError"),
                gradedSeen = gradedSeen
            ),
            bonus = bonus,
            zz = zz
        )
    }

    private fun toJson(s: WorkbenchState): JSONObject {
        val root = JSONObject()
        root.put("todos", JSONArray().apply {
            s.todos.forEach { t ->
                put(JSONObject().apply {
                    put("id", t.id)
                    put("title", t.title)
                    put("type", t.type.key)
                    put("date", t.date)
                    put("weekdays", JSONArray(t.weekdays))
                    put("monthDays", JSONArray(t.monthDays))
                    put("interval", t.interval)
                    put("priority", t.priority.key)
                    put("tag", t.tag)
                    put("remindTime", t.remindTime)
                    put("note", t.note)
                    put("subSteps", JSONArray().apply {
                        t.subSteps.forEach { st ->
                            put(JSONObject().apply {
                                put("id", st.id); put("text", st.text); put("done", st.done)
                            })
                        }
                    })
                    put("doneDates", JSONObject().apply { t.doneDates.forEach { put(it, true) } })
                    put("postponedUntil", t.postponedUntil)
                    put("createdAt", t.createdAt)
                })
            }
        })
        root.put("projects", JSONArray().apply {
            s.projects.forEach { p ->
                put(JSONObject().apply {
                    put("id", p.id); put("title", p.title); put("emoji", p.emoji)
                    put("colorKey", p.colorKey); put("deadline", p.deadline)
                    put("items", JSONArray().apply {
                        p.items.forEach { sk ->
                            put(JSONObject().apply {
                                put("id", sk.id); put("name", sk.name); put("progress", sk.progress)
                            })
                        }
                    })
                })
            }
        })
        root.put("settings", JSONObject().apply {
            put("theme", s.settings.theme.key)
            put("accent", s.settings.accent.key)
            put("focus", s.settings.focusMinutes)
            put("brk", s.settings.breakMinutes)
            put("remindOn", s.settings.remindOn)
            put("summaryTime", s.settings.summaryTime)
            put("summaryOn", s.settings.summaryOn)
            put("soundLevel", s.settings.soundLevel)
            put("keepAliveOn", s.settings.keepAliveOn)
            put("bgCheckOn", s.settings.bgCheckOn)
            put("bgCheckUc", s.settings.bgCheckUc)
            put("bgCheckPt", s.settings.bgCheckPt)
            put("bgCheckWifiOnly", s.settings.bgCheckWifiOnly)
            put("courseMajor", s.settings.courseMajor)
            put("kExam", s.settings.kExam)
            put("kDiff", s.settings.kDiff)
            put("kOut", s.settings.kOut)
        })
        root.put("holidayManual", JSONObject().apply {
            s.holidayManual.forEach { (k, v) ->
                put(k, JSONObject().apply { put("off", v.off); put("name", v.name) })
            }
        })
        root.put("holidayCache", JSONObject().apply {
            s.holidayCache.forEach { (y, m) ->
                put(y, JSONObject().apply {
                    m.forEach { (d, v) -> put(d, JSONObject().apply { put("off", v.off); put("name", v.name) }) }
                })
            }
        })
        root.put("todayPomos", s.todayPomos)
        root.put("todayPomoDate", s.todayPomoDate)
        root.put("timetable", JSONObject().apply {
            put("user", s.timetable.user)
            put("pwd", s.timetable.pwd)
            put("auto", s.timetable.auto)
            put("cookie", s.timetable.cookie)
            put("startMonday", s.timetable.startMonday)
            put("totalWeeks", s.timetable.totalWeeks)
            put("fetched", s.timetable.fetched)
            put("lastOk", s.timetable.lastOk)
            put("lastError", s.timetable.lastError)
            put("trace", JSONArray(s.timetable.trace))
            s.timetable.data?.let { td ->
                put("data", JSONObject().apply {
                    put("meta", JSONObject().apply {
                        put("semester", td.semester)
                        put("semesterLabel", td.semesterLabel)
                        put("startMonday", td.startMonday)
                        put("totalWeeks", td.totalWeeks)
                        put("fetched", td.fetched)
                        put("source", td.source)
                    })
                    put("periods", JSONArray().apply {
                        td.periods.forEach { p -> put(JSONObject().apply { put("no", p.no); put("time", p.time) }) }
                    })
                    put("courses", JSONArray().apply {
                        td.courses.forEach { c ->
                            put(JSONObject().apply {
                                put("name", c.name); put("teacher", c.teacher)
                                put("weekdays", JSONArray(c.weekdays))
                                put("start", c.start); put("end", c.end)
                                put("weeks", c.weeks); put("room", c.room); put("practice", c.practice)
                                if (c.segments.isNotEmpty()) {
                                    put("segments", JSONArray().apply {
                                        c.segments.forEach { sg ->
                                            put(JSONObject().apply { put("weeks", sg.weeks); put("room", sg.room) })
                                        }
                                    })
                                }
                            })
                        }
                    })
                })
            }
        })
        root.put("ucloud", JSONObject().apply {
            put("on", s.ucloud.on)
            put("id", s.ucloud.id)
            put("pwd", s.ucloud.pwd)
            put("token", s.ucloud.token)
            put("refreshToken", s.ucloud.refreshToken)
            put("userId", s.ucloud.userId)
            put("userInfoId", s.ucloud.userInfoId)
            put("identity", s.ucloud.identity)
            put("roleId", s.ucloud.roleId)
            put("roleName", s.ucloud.roleName)
            put("tokenAt", s.ucloud.tokenAt)
            put("lastSync", s.ucloud.lastSync)
            put("lastError", s.ucloud.lastError)
            put("alertOn", s.ucloud.alertOn)
            put("seenInit", s.ucloud.seenInit)
            put("seen", JSONObject().apply { s.ucloud.seen.forEach { put(it, true) } })
            put("done", JSONObject().apply { s.ucloud.done.forEach { put(it, true) } })
            put("courseMap", JSONObject().apply { s.ucloud.courseMap.forEach { (k, v) -> put(k, v) } })
            put("details", JSONObject().apply {
                s.ucloud.details.forEach { (k, d) ->
                    put(k, JSONObject().apply {
                        put("content", d.content)
                        put("fullMark", d.fullMark)
                        put("chapter", d.chapter)
                        put("attachments", d.attachments)
                        put("begin", d.beginTime)
                        put("end", d.endTime)
                        put("at", d.fetchedAt)
                        if (d.error.isNotEmpty()) put("error", d.error)
                    })
                }
            })
            put("items", JSONArray().apply {
                s.ucloud.items.forEach { it ->
                    put(JSONObject().apply {
                        put("id", it.id); put("title", it.title); put("endTime", it.endTime)
                        put("siteName", it.siteName); put("type", it.type)
                        put("activityType", it.activityType); put("content", it.content)
                    })
                }
            })
        })
        root.put("portal", JSONObject().apply {
            put("on", s.portal.on)
            put("kind", s.portal.kind)
            put("cookie", s.portal.cookieJarJson)
            put("lastSync", s.portal.lastSync)
            put("lastError", s.portal.lastError)
            put("seenInit", s.portal.seenInit)
            put("seen", JSONObject().apply { s.portal.seen.forEach { put(it, true) } })
            fun arrOf(list: List<PortalArticle>) = JSONArray().apply {
                list.forEach { a ->
                    put(JSONObject().apply {
                        put("id", a.id); put("kind", a.kind); put("title", a.title)
                        put("url", a.url); put("dept", a.dept); put("date", a.date)
                        put("bodyText", a.bodyText)
                        put("imageCount", a.imageCount)
                        put("detailFetched", a.detailFetched)
                        if (a.blocks.isNotEmpty()) {
                            put("blocks", JSONArray().apply {
                                a.blocks.forEach { b ->
                                    put(JSONObject().apply {
                                        put("type", b.type)
                                        if (b.text.isNotEmpty()) put("text", b.text)
                                        if (b.src.isNotEmpty()) put("src", b.src)
                                        if (b.altSrc.isNotEmpty()) put("altSrc", b.altSrc)
                                        if (b.level != 2) put("level", b.level)
                                        if (b.width != 0) put("width", b.width)
                                    })
                                }
                            })
                        }
                        a.lecture?.let { lec ->
                            put("lecture", JSONObject().apply {
                                put("whenText", lec.whenText)
                                put("who", lec.who)
                                put("host", lec.host)
                                put("where", lec.where)
                                put("abstractText", lec.abstractText)
                            })
                        }
                        put("attachments", JSONArray().apply {
                            a.attachments.forEach { att ->
                                put(JSONObject().apply { put("name", att.name); put("url", att.url) })
                            }
                        })
                    })
                }
            }
            put("items", JSONObject().apply {
                put("notice", arrOf(s.portal.notices))
                put("lecture", arrOf(s.portal.lectures))
            })
        })
        root.put("hw", JSONObject().apply {
            put("fetchedAt", s.hw.fetchedAt)
            put("lastError", s.hw.lastError)
            put("terms", JSONArray().apply {
                s.hw.terms.forEach { t ->
                    put(JSONObject().apply {
                        put("id", t.id); put("name", t.name); put("isCurrent", t.isCurrent)
                    })
                }
            })
            put("courses", JSONArray().apply {
                s.hw.courses.forEach { c ->
                    put(JSONObject().apply {
                        put("siteId", c.siteId); put("termId", c.termId)
                        put("name", c.name); put("teacher", c.teacher); put("termName", c.termName)
                    })
                }
            })
            fun attsJson(list: List<HwAttachment>) = JSONArray().apply {
                list.forEach { a ->
                    put(JSONObject().apply {
                        put("resourceId", a.resourceId); put("name", a.name)
                        put("sizeText", a.sizeText); put("ext", a.ext); put("recordFlag", a.recordFlag)
                    })
                }
            }
            put("byTerm", JSONObject().apply {
                s.hw.byTerm.entries
                    .sortedByDescending { it.value.fetchedAt }
                    .take(8)
                    .forEach { (tid, tc) ->
                        put(tid, JSONObject().apply {
                            put("fetchedAt", tc.fetchedAt)
                            put("err", tc.err)
                            put("items", JSONArray().apply {
                                tc.items.forEach { it ->
                                    put(JSONObject().apply {
                                        put("id", it.id); put("siteId", it.siteId)
                                        put("courseName", it.courseName); put("title", it.title)
                                        put("chapterName", it.chapterName); put("submitTime", it.submitTime)
                                        put("gradeTime", it.gradeTime); put("gradeLocal", it.gradeLocal)
                                        put("endTime", it.endTime); put("isOverTime", it.isOverTime)
                                        put("status", it.status); put("score", it.score)
                                        put("comment", it.comment); put("groupScore", it.groupScore)
                                        put("evalAvgScore", it.evalAvgScore); put("evalInfos", it.evalInfos)
                                        put("myContent", it.myContent); put("myFiles", attsJson(it.myFiles))
                                        put("mergedCount", it.mergedCount); put("gradedSeenAt", it.gradedSeenAt)
                                        put("excellent", JSONArray().apply {
                                            it.excellent.forEach { ex ->
                                                put(JSONObject().apply {
                                                    put("audiencesId", ex.audiencesId); put("teamId", ex.teamId)
                                                    put("userId", ex.userId); put("account", ex.account)
                                                    put("realName", ex.realName); put("className", ex.className)
                                                    put("score", ex.score); put("submitTime", ex.submitTime)
                                                    put("content", ex.content); put("comment", ex.comment)
                                                    put("files", attsJson(ex.files))
                                                })
                                            }
                                        })
                                    })
                                }
                            })
                        })
                    }
            })
            put("gradedSeen", JSONObject().apply { s.hw.gradedSeen.forEach { (k, v) -> put(k, v) } })
        })
        root.put("bonus", JSONObject().apply {
            put("hasCityDc", s.bonus.hasCityDc)
            put("entries", JSONArray().apply {
                s.bonus.entries.forEach { e ->
                    put(JSONObject().apply {
                        put("id", e.id); put("title", e.title); put("pi", e.pi); put("tui", e.tui)
                        put("ach", e.ach); put("hours", e.hours); put("cat", e.cat); put("note", e.note)
                    })
                }
            })
        })
        root.put("zz", JSONObject().apply {
            put("major", s.zz.major)
            put("basic", JSONObject().apply {
                put("self", s.zz.selfScore)
                put("peer", s.zz.peerScore)
                put("counselor", s.zz.counselorScore)
                put("deduct", JSONObject().apply { s.zz.deduct.forEach { (k, v) -> put(k, v) } })
            })
            put("scores", JSONObject().apply { s.zz.courseScores.forEach { (k, v) -> put(k, v) } })
            put("custom", JSONArray().apply {
                s.zz.customCourses.forEach { c ->
                    put(JSONObject().apply {
                        put("id", c.id); put("name", c.name); put("credit", c.credit); put("score", c.score)
                    })
                }
            })
            put("dev", JSONObject().apply {
                put("physicalTest", s.zz.physicalTest)
                put("exerciseScore", s.zz.exerciseScore)
                put("activityEval", s.zz.activityEval)
                s.zz.devScores.forEach { (k, v) -> put(k, v) }
            })
        })
        return root
    }
}
