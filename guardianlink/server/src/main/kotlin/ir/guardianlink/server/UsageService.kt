package ir.guardianlink.server

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.OffsetDateTime
import java.util.Base64
import java.util.UUID

/**
 * Phase 3 — Screen Time (فقط Monitoring؛ بدون enforcement).
 * همه داده‌ها self-report دستگاه Child هستند ولی با validation سخت‌گیرانه:
 * بازه‌ها، فرمت‌ها و سقف‌ها؛ هویت دستگاه فقط از توکن session می‌آید.
 */
class UsageService(private val deps: AppDeps) {
    private val usage = UsageRepo(deps.db)
    private val events = DeviceEventRepo(deps.db)

    companion object {
        private val PKG_RX = Regex("^[A-Za-z0-9_.$]{1,100}$")
        private val HHMM_RX = Regex("^([01][0-9]|2[0-3]):[0-5][0-9]$")
        private val CATEGORIES = setOf(
            "undefined", "game", "audio", "video", "image", "social",
            "news", "maps", "productivity", "accessibility",
        )
        const val MAX_DAYS = 8
        const val MAX_APPS_PER_DAY = 80
        const val MAX_ICON_BYTES = 120 * 1024
    }

    /** ذخیره‌سازی idempotent (UPSERT) ⇒ sync تکراری داده تکراری نمی‌سازد. */
    fun upload(deviceId: UUID, req: UsageUploadReq): Int {
        if (req.days.size > MAX_DAYS) throw ApiError.validation("too many days")
        val zone = deviceZone(deviceId)
        val today = LocalDate.now(zone)
        // validate همه‌ی rows قبل از نوشتن (all-or-nothing)
        data class Pending(val row: UsageRow, val icon: ByteArray?, val iconVersion: Int)
        val rows = mutableListOf<Pending>()
        for (day in req.days) {
            val date = runCatching { LocalDate.parse(day.date) }.getOrNull()
                ?: throw ApiError.validation("bad date")
            if (date.isAfter(today) || date.isBefore(today.minusDays(7))) throw ApiError.validation("date out of window")
            if (day.apps.size > MAX_APPS_PER_DAY) throw ApiError.validation("too many apps in a day")
            for (a in day.apps) {
                if (!PKG_RX.matches(a.packageName)) throw ApiError.validation("bad package name")
                if (a.usageMinutes !in 0..1440) throw ApiError.validation("usage minutes out of range")
                val launches = a.launchCount ?: 0
                if (launches !in 0..100_000) throw ApiError.validation("launch count out of range")
                val cat = (a.category ?: "undefined").lowercase()
                if (cat !in CATEGORIES) throw ApiError.validation("bad category")
                val name = (a.appName ?: a.packageName).take(60)
                val first = a.firstUsedAt?.let { ts -> safeTs(ts, date, zone) }
                val last = a.lastUsedAt?.let { ts -> safeTs(ts, date, zone) }
                rows.add(
                    Pending(
                        UsageRow(a.packageName, name, cat, date, a.usageMinutes, launches, first, last),
                        decodeIcon(a.iconBase64),
                        a.iconVersion ?: 0,
                    ),
                )
            }
        }
        deps.db.tx { c ->
            for (p in rows) {
                usage.upsertDay(c, deviceId, p.row)
                if (p.icon != null) usage.upsertIcon(c, deviceId, p.row.packageName, p.iconVersion, p.icon)
            }
        }
        evaluateLimit(deviceId)
        evaluateSchedules(deviceId)
        return rows.size
    }

    private fun deviceZone(deviceId: UUID): ZoneId =
        runCatching { ZoneId.of(deps.appManagementService.zoneId(deviceId)) }.getOrDefault(ZoneId.of("UTC"))

    private fun safeTs(epochMs: Long, date: LocalDate, zone: ZoneId): OffsetDateTime? {
        val ts = runCatching { OffsetDateTime.ofInstant(Instant.ofEpochMilli(epochMs), zone) }.getOrNull()
            ?: return null
        val dayStart = date.atStartOfDay(zone).toOffsetDateTime()
        return if (ts.isBefore(dayStart.minusHours(24)) || ts.isAfter(dayStart.plusHours(48))) null else ts
    }

    private fun decodeIcon(b64: String?): ByteArray? {
        if (b64.isNullOrBlank()) return null
        return try {
            val raw = Base64.getDecoder().decode(b64)
            if (raw.isEmpty() || raw.size > MAX_ICON_BYTES) null else raw
        } catch (_: Exception) {
            null
        }
    }

    // ---------------- Daily Limit events (only on threshold crossing) ----------------
    fun evaluateLimit(deviceId: UUID) {
        val limit = usage.limit(deviceId) ?: return
        if (!limit.enabled) return
        val today = LocalDate.now(deviceZone(deviceId))
        val total = usage.dayTotal(deviceId, today)
        val newState = when {
            total >= limit.dailyLimitMinutes -> "reached"
            total >= limit.dailyLimitMinutes * limit.nearPct / 100 -> "near"
            else -> "under"
        }
        val st = usage.state(deviceId, today)
        val old = st?.limitState ?: "under"
        if (newState == old) return
        val crossed = mutableListOf<String>()
        if (newState != "under" && old == "under") crossed.add(EventType.SCREEN_TIME_LIMIT_NEAR)
        if (newState == "reached" && old != "reached") crossed.add(EventType.SCREEN_TIME_LIMIT_REACHED)
        // اگر ردیف امروز تازه ساخته می‌شود، کلیدهای schedule دیروز (overnight) را نگه دار
        val keys = st?.scheduleKeys ?: usage.state(deviceId, today.minusDays(1))?.scheduleKeys.orEmpty()
        deps.db.tx { c ->
            usage.upsertState(c, deviceId, today, newState, keys)
            for (type in crossed) {
                events.emit(
                    c, deviceId, type,
                    Json.write(
                        mapOf(
                            "total_minutes" to total,
                            "limit_minutes" to limit.dailyLimitMinutes,
                            "state" to newState,
                        ),
                    ),
                    "${deviceId}:$type:${today}",
                )
            }
        }
        if (crossed.isNotEmpty()) dlog.info("screen time event", "device" to maskDeviceId(deviceId), "state" to newState)
    }

    // ---------------- Schedule windows (server-side, lazy) ----------------
    fun evaluateSchedules(deviceId: UUID) {
        val zone = deviceZone(deviceId)
        val now = OffsetDateTime.now(zone)
        val today = LocalDate.now(zone)
        val nowTime = now.toLocalTime()
        val schedules = usage.schedules(deviceId).filter { it.enabled }
        val active = mutableSetOf<String>()
        for (s in schedules) {
            val start = runCatching { LocalTime.parse(s.startTime) }.getOrNull() ?: continue
            val end = runCatching { LocalTime.parse(s.endTime) }.getOrNull() ?: continue
            val todayBit = bitFor(today.dayOfWeek)
            val yesterdayBit = bitFor(today.minusDays(1).dayOfWeek)
            val inWindow = if (start == end) {
                false
            } else if (start < end) {
                (s.daysOfWeek and todayBit) != 0 && !nowTime.isBefore(start) && nowTime.isBefore(end)
            } else {
                // overnight: شروع دیشب/امشب یا پایان بامداد
                ((s.daysOfWeek and todayBit) != 0 && !nowTime.isBefore(start)) ||
                    ((s.daysOfWeek and yesterdayBit) != 0 && nowTime.isBefore(end))
            }
            if (inWindow) active.add(s.id.toString())
        }
        val st = usage.state(deviceId, today)
        // اگر هنوز ردیف امروز ساخته نشده (بامداد)، کلیدهای دیروز مبناست تا پنجره‌ی overnight تکراری emit نشود
        val prevState = st ?: usage.state(deviceId, today.minusDays(1))
        val prev = prevState?.scheduleKeys?.split(",")?.filter { it.isNotBlank() }?.toSet() ?: emptySet()
        if (active == prev) return
        val keyStr = active.sorted().joinToString(",")
        deps.db.tx { c ->
            usage.upsertState(c, deviceId, today, st?.limitState ?: "under", keyStr)
            for (added in active - prev) {
                events.emit(
                    c, deviceId, EventType.SCHEDULE_STARTED,
                    Json.write(mapOf("schedule_id" to added)),
                    "$deviceId:SCHEDULE_STARTED:$added:$today",
                )
            }
            for (removed in prev - active) {
                events.emit(
                    c, deviceId, EventType.SCHEDULE_ENDED,
                    Json.write(mapOf("schedule_id" to removed)),
                    "$deviceId:SCHEDULE_ENDED:$removed:$today",
                )
            }
        }
    }

    private fun bitFor(d: DayOfWeek): Int = 1 shl (d.value - 1)

    // ---------------- reads ----------------
    fun today(deviceId: UUID): UsageTodayOut {
        evaluateLimit(deviceId)
        evaluateSchedules(deviceId)
        val today = LocalDate.now(deviceZone(deviceId))
        val apps = usage.appsForDay(deviceId, today)
        val total = apps.sumOf { it.usageMinutes }
        val limit = usage.limit(deviceId)
        val state = usage.state(deviceId, today)?.limitState ?: "under"
        return UsageTodayOut(
            date = today.toString(),
            totalMinutes = total,
            limitMinutes = limit?.dailyLimitMinutes,
            limitEnabled = limit?.enabled == true,
            remainingMinutes = limit?.takeIf { it.enabled }?.let { (it.dailyLimitMinutes - total).coerceAtLeast(0) },
            state = state,
            topApps = apps.take(5).map { it.out(total) },
        )
    }

    fun weekly(deviceId: UUID): UsageWeeklyOut {
        val today = LocalDate.now(deviceZone(deviceId))
        val days = (6 downTo 0).map { off ->
            val d = today.minusDays(off.toLong())
            UsageDayTotal(d.toString(), usage.dayTotal(deviceId, d))
        }
        val total = days.sumOf { it.totalMinutes }
        val max = days.maxByOrNull { it.totalMinutes }
        val min = days.minByOrNull { it.totalMinutes }
        return UsageWeeklyOut(days, total, total / 7, max?.date, min?.date)
    }

    fun appsFor(deviceId: UUID, date: LocalDate?): List<AppUsageOut> {
        val d = date ?: LocalDate.now(deviceZone(deviceId))
        val rows = usage.appsForDay(deviceId, d)
        val total = rows.sumOf { it.usageMinutes }
        return rows.map { it.out(total) }
    }

    fun appDetail(deviceId: UUID, pkg: String): AppDetailOut {
        val today = LocalDate.now(deviceZone(deviceId))
        val weekRows = usage.appWeek(deviceId, pkg, today.minusDays(6), today)
        val todayRow = weekRows.firstOrNull { it.usageDate == today }
        val byDate = weekRows.groupBy { it.usageDate }
        val week = (6 downTo 0).map { off ->
            val d = today.minusDays(off.toLong())
            UsageDayTotal(d.toString(), byDate[d]?.sumOf { it.usageMinutes } ?: 0)
        }
        val last = weekRows.maxByOrNull { it.lastUsedAt ?: it.firstUsedAt ?: OffsetDateTime.MIN }
        return AppDetailOut(
            packageName = pkg,
            appName = weekRows.lastOrNull()?.appName ?: pkg,
            category = weekRows.lastOrNull()?.category ?: "undefined",
            todayMinutes = todayRow?.usageMinutes ?: 0,
            todayLaunches = todayRow?.launchCount ?: 0,
            lastUsedAt = last?.lastUsedAt?.toString(),
            week = week,
            weekLaunches = weekRows.sumOf { it.launchCount },
        )
    }

    fun dailyReport(deviceId: UUID, date: LocalDate?): DailyReportOut {
        val d = date ?: LocalDate.now(deviceZone(deviceId))
        val apps = usage.appsForDay(deviceId, d)
        val total = apps.sumOf { it.usageMinutes }
        return DailyReportOut(
            date = d.toString(),
            totalMinutes = total,
            mostUsedApp = apps.firstOrNull()?.out(total),
            appsUsed = apps.size,
            sessions = apps.sumOf { it.launchCount },
            categories = usage.categoriesForDay(deviceId, d).map { CategoryTotal(it.first, it.second) },
        )
    }

    fun report(deviceId: UUID): ScreenTimeReportOut {
        val limit = usage.limit(deviceId)
        return ScreenTimeReportOut(
            today = today(deviceId),
            weekly = weekly(deviceId),
            settings = ScreenTimeSettingsOut(
                deviceId,
                limit?.dailyLimitMinutes ?: 240,
                limit?.nearPct ?: 80,
                limit?.enabled ?: false,
            ),
            schedules = usage.schedules(deviceId).map {
                ScheduleOut(it.id, it.name, it.startTime, it.endTime, it.daysOfWeek, it.enabled)
            },
        )
    }

    fun saveSettings(deviceId: UUID, req: ScreenTimeSettingsReq): ScreenTimeSettingsOut {
        val cur = usage.limit(deviceId)
        val minutes = req.dailyLimitMinutes ?: cur?.dailyLimitMinutes ?: 240
        val near = req.nearPct ?: cur?.nearPct ?: 80
        val enabled = req.enabled ?: cur?.enabled ?: true
        if (minutes !in 5..1440) throw ApiError.validation("daily limit must be 5..1440 minutes")
        if (near !in 50..100) throw ApiError.validation("near pct must be 50..100")
        val row = deps.db.tx { c -> usage.upsertLimit(c, deviceId, minutes, near, enabled) }
        evaluateLimit(deviceId)
        return ScreenTimeSettingsOut(row.deviceId, row.dailyLimitMinutes, row.nearPct, row.enabled)
    }

    fun createSchedule(deviceId: UUID, req: ScheduleReq): ScheduleOut {
        val name = (req.name ?: "").trim()
        if (name.length !in 1..40) throw ApiError.validation("schedule name invalid")
        val start = req.startTime ?: throw ApiError.validation("start_time required")
        val end = req.endTime ?: throw ApiError.validation("end_time required")
        validateTimes(start, end)
        val days = req.daysOfWeek ?: 127
        if (days !in 1..127) throw ApiError.validation("days_of_week must be 1..127")
        val id = deps.db.tx { c ->
            usage.insertSchedule(c, deviceId, name, start, end, days, req.enabled ?: true)
        }
        evaluateSchedules(deviceId)
        return ScheduleOut(id, name, start, end, days, req.enabled ?: true)
    }

    fun updateSchedule(deviceId: UUID, scheduleId: UUID, req: ScheduleReq): ScheduleOut {
        val cur = usage.scheduleById(scheduleId) ?: throw ApiError.notFound()
        if (cur.deviceId != deviceId) throw ApiError.notFound()
        val name = req.name?.trim()?.ifBlank { cur.name } ?: cur.name
        val start = req.startTime ?: cur.startTime
        val end = req.endTime ?: cur.endTime
        validateTimes(start, end)
        val days = req.daysOfWeek ?: cur.daysOfWeek
        if (days !in 1..127) throw ApiError.validation("days_of_week must be 1..127")
        val enabled = req.enabled ?: cur.enabled
        deps.db.tx { c -> usage.updateSchedule(c, scheduleId, name, start, end, days, enabled) }
        evaluateSchedules(deviceId)
        return ScheduleOut(scheduleId, name, start, end, days, enabled)
    }

    private fun validateTimes(start: String, end: String) {
        if (!HHMM_RX.matches(start) || !HHMM_RX.matches(end)) throw ApiError.validation("time must be HH:MM")
        if (start == end) throw ApiError.validation("start and end must differ (overnight allowed)")
    }

    fun icon(deviceId: UUID, pkg: String): ByteArray? = usage.icon(deviceId, pkg)
}

fun UsageRow.out(totalMinutes: Int): AppUsageOut = AppUsageOut(
    packageName = packageName,
    appName = appName,
    category = category,
    usageMinutes = usageMinutes,
    launchCount = launchCount,
    firstUsedAt = firstUsedAt?.toString(),
    lastUsedAt = lastUsedAt?.toString(),
    percent = if (totalMinutes > 0) (usageMinutes * 100.0 / totalMinutes) else 0.0,
)
