package com.mu.workbench.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

object AssetsLoader {

    private fun readAsset(ctx: Context, name: String): String {
        return ctx.assets.open("data/$name").bufferedReader(Charsets.UTF_8).use { it.readText() }
    }

    fun loadBuiltinHolidays(ctx: Context): Map<String, HolidayEntry> {
        val out = LinkedHashMap<String, HolidayEntry>()
        val root = JSONObject(readAsset(ctx, "holidays.json"))
        val years = root.keys()
        while (years.hasNext()) {
            val y = years.next()
            val arr = root.optJSONArray(y) ?: continue
            for (i in 0 until arr.length()) {
                val row = arr.optJSONArray(i) ?: continue
                val date = row.optString(0)
                val off = row.optInt(1, 0) != 0
                val name = row.optString(2, "")
                if (date.isNotEmpty()) out[date] = HolidayEntry(off, name)
            }
        }
        return out
    }

    fun loadBuiltinTimetable(ctx: Context): TimetableData {
        return parseTimetableJson(JSONObject(readAsset(ctx, "kb_builtin.json")))
    }

    fun parseTimetableJson(o: JSONObject): TimetableData {
        val meta = o.optJSONObject("meta") ?: JSONObject()
        val periods = mutableListOf<PeriodSlot>()
        val pArr = o.optJSONArray("periods") ?: JSONArray()
        for (i in 0 until pArr.length()) {
            val p = pArr.optJSONObject(i) ?: continue
            periods += PeriodSlot(p.optInt("no"), p.optString("time"))
        }
        val courses = mutableListOf<CourseScheduleItem>()
        val cArr = o.optJSONArray("courses") ?: JSONArray()
        for (i in 0 until cArr.length()) {
            val c = cArr.optJSONObject(i) ?: continue
            val wArr = c.optJSONArray("weekdays") ?: JSONArray()
            val wds = (0 until wArr.length()).map { wArr.optInt(it) }
            val segs = mutableListOf<CourseSegment>()
            val sArr = c.optJSONArray("segments")
            if (sArr != null) {
                for (j in 0 until sArr.length()) {
                    val sg = sArr.optJSONObject(j) ?: continue
                    segs += CourseSegment(sg.optString("weeks"), sg.optString("room"))
                }
            }
            courses += CourseScheduleItem(
                name = c.optString("name"),
                teacher = c.optString("teacher"),
                weekdays = wds,
                start = c.optInt("start"),
                end = c.optInt("end"),
                weeks = c.optString("weeks"),
                room = c.optString("room"),
                practice = c.optBoolean("practice", false),
                segments = segs
            )
        }
        return TimetableData(
            semester = meta.optString("semester"),
            semesterLabel = meta.optString("semesterLabel"),
            startMonday = meta.optString("startMonday"),
            totalWeeks = meta.optInt("totalWeeks", 19),
            fetched = meta.optString("fetched"),
            source = meta.optString("source"),
            periods = periods,
            courses = courses
        )
    }

    fun loadCourseCatalog(ctx: Context): CourseCatalog {
        val root = JSONObject(readAsset(ctx, "cr_data.json"))
        val grad = root.optInt("grad", 145)
        val majors = mutableListOf<CourseMajorInfo>()
        val mArr = root.optJSONArray("majors") ?: JSONArray()
        for (i in 0 until mArr.length()) {
            val row = mArr.optJSONArray(i) ?: continue
            majors += CourseMajorInfo(
                key = row.optString(0),
                shortName = row.optString(1),
                fullName = row.optString(2),
                structureDesc = row.optString(3)
            )
        }
        val pool = mutableListOf<CourseCatalogItem>()
        val pArr = root.optJSONArray("pool") ?: JSONArray()
        for (i in 0 until pArr.length()) {
            val p = pArr.optJSONArray(i) ?: continue
            val ts = p.opt(6)?.toString() ?: ""
            val minT = Regex("(\\d+)").find(ts)?.groupValues?.get(1)?.toIntOrNull() ?: 0
            pool += CourseCatalogItem(
                code = p.optString(0),
                name = p.optString(1),
                module = p.optString(2),
                credits = p.optDouble(3, 0.0),
                hours = p.optDouble(4, 0.0),
                practiceHours = p.optDouble(5, 0.0),
                termSpec = ts,
                minTerm = minT,
                requiredType = p.optString(7),
                examType = p.optString(8),
                difficulty = p.optDouble(9, 1.0),
                zzFlag = p.optInt(10, 0),
                note = p.optString(11, "")
            )
        }
        val idx = root.optJSONObject("idx") ?: JSONObject()
        val byMajor = LinkedHashMap<String, List<CourseCatalogItem>>()
        val keys = idx.keys()
        while (keys.hasNext()) {
            val mk = keys.next()
            val arr = idx.optJSONArray(mk) ?: continue
            val list = mutableListOf<CourseCatalogItem>()
            for (i in 0 until arr.length()) {
                val k = arr.optInt(i, -1)
                if (k in pool.indices) list += pool[k]
            }
            byMajor[mk] = list
        }
        return CourseCatalog(grad, majors, byMajor)
    }

    fun loadBonusData(ctx: Context): BonusData {
        val root = JSONObject(readAsset(ctx, "bonus_data.json"))
        val grades = mutableListOf<BonusGradeRule>()
        root.optJSONArray("grades")?.let { arr ->
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                grades += BonusGradeRule(o.optString("k"), o.optString("n"), o.optString("rule"))
            }
        }
        val levels = mutableListOf<BonusLevel>()
        root.optJSONArray("levels")?.let { arr ->
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val piArr = o.optJSONArray("pi") ?: JSONArray()
                val tuiArr = o.optJSONArray("tui") ?: JSONArray()
                val pi = (0 until piArr.length()).map { piArr.optDouble(it, 0.0) }
                val tui = (0 until tuiArr.length()).map { if (tuiArr.isNull(it)) null else tuiArr.optDouble(it, 0.0) }
                levels += BonusLevel(o.optString("k"), o.optString("n"), pi, tui, o.optString("src"), o.optString("note"))
            }
        }
        val comps = mutableListOf<BonusComp>()
        root.optJSONArray("comps")?.let { arr ->
            for (i in 0 until arr.length()) {
                val row = arr.optJSONArray(i) ?: continue
                comps += BonusComp(row.optString(0), row.optString(1))
            }
        }
        val others = mutableListOf<BonusOther>()
        root.optJSONArray("others")?.let { arr ->
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                others += BonusOther(
                    key = o.optString("k"),
                    cat = o.optString("cat"),
                    name = o.optString("n"),
                    pi = o.optDouble("pi", 0.0),
                    tui = o.optDouble("tui", 0.0),
                    h = o.optDouble("h", 0.0),
                    p = o.optDouble("p", 0.0),
                    ach = o.optInt("ach", 0) != 0,
                    note = o.optString("note")
                )
            }
        }
        val steps = mutableListOf<BonusStep>()
        root.optJSONArray("steps")?.let { arr ->
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                steps += BonusStep(o.optString("t"), o.optString("d"), o.optString("src"))
            }
        }
        val traps = mutableListOf<String>()
        root.optJSONArray("traps")?.let { arr ->
            for (i in 0 until arr.length()) traps += arr.optString(i)
        }
        val docs = mutableListOf<BonusDoc>()
        root.optJSONArray("docs")?.let { arr ->
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                docs += BonusDoc(
                    title = o.optString("t"),
                    no = o.optString("no"),
                    org = o.optString("org"),
                    url = o.optString("url"),
                    keyPoint = o.optString("key"),
                    primary = o.optInt("primary", 0) != 0
                )
            }
        }
        return BonusData(grades, levels, comps, others, steps, traps, docs)
    }

    fun loadZzData(ctx: Context): ZzData {
        val root = JSONObject(readAsset(ctx, "zz_data.json"))
        val deduct = mutableListOf<ZzDeductItem>()
        val dev = mutableListOf<ZzDevItem>()
        val notes = mutableListOf<String>()
        val principles = mutableListOf<String>()
        val chapters = mutableListOf<ZzRuleChapter>()

        root.optJSONObject("basic")?.optJSONArray("deduct")?.let { arr ->
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                deduct += ZzDeductItem(
                    key = o.optString("k", "d$i"),
                    label = o.optString("n", o.optString("t", "扣分项")),
                    desc = o.optString("desc", ""),
                    minPoint = o.optDouble("min", 0.0),
                    maxPoint = o.optDouble("max", 10.0)
                )
            }
        }

        root.optJSONObject("dev")?.optJSONObject("other")?.optJSONArray("items")?.let { arr ->
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val exArr = o.optJSONArray("examples")
                val desc = if (exArr != null && exArr.length() > 0) exArr.optString(0) else ""
                dev += ZzDevItem(
                    key = o.optString("k", "dev$i"),
                    category = "其他发展素质（上限 60 分）",
                    label = o.optString("n", o.optString("t", "")),
                    maxPoint = o.optDouble("max", 10.0),
                    desc = desc
                )
            }
        }

        root.optJSONArray("notes")?.let { arr ->
            for (i in 0 until arr.length()) notes += arr.optString(i)
        }
        root.optJSONArray("principles")?.let { arr ->
            for (i in 0 until arr.length()) principles += arr.optString(i)
        }
        root.optJSONArray("chapters")?.let { arr ->
            for (i in 0 until arr.length()) {
                val ch = arr.optJSONObject(i) ?: continue
                val secs = mutableListOf<ZzRuleSection>()
                ch.optJSONArray("sections")?.let { sa ->
                    for (j in 0 until sa.length()) {
                        val so = sa.optJSONObject(j) ?: continue
                        secs += ZzRuleSection(so.optString("no"), so.optString("title"), so.optString("body"))
                    }
                }
                chapters += ZzRuleChapter(ch.optString("no"), ch.optString("title"), secs)
            }
        }
        val meta = root.optJSONObject("meta")
        return ZzData(
            deductItems = deduct,
            devItems = dev,
            notes = notes,
            principles = principles,
            chapters = chapters,
            metaTitle = meta?.optString("title").orEmpty().ifEmpty { "北京邮电大学本科生综合素质评价办法" },
            metaOrg = meta?.optString("org").orEmpty().ifEmpty { "北京邮电大学学生处" },
            metaUrl = meta?.optString("url").orEmpty()
        )
    }
}
