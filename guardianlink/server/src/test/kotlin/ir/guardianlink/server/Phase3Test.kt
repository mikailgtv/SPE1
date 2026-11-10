package ir.guardianlink.server

import io.ktor.client.call.body
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.delay
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalTime
import java.util.Base64
import java.util.UUID

class Phase3Test : IntegrationBase() {
    @BeforeEach
    fun reset() = truncate()

    private val png1x1: String =
        "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg=="

    private fun app(
        pkg: String,
        minutes: Int,
        name: String? = null,
        category: String? = null,
        launches: Int? = null,
        icon: String? = null,
    ) = UsageAppIn(pkg, name, category, minutes, launches, null, null, icon, if (icon != null) 1 else null)

    private fun day(date: LocalDate, vararg apps: UsageAppIn) = UsageDayIn(date.toString(), apps.toList())
    private fun uploadReq(vararg days: UsageDayIn) = UsageUploadReq(days.toList())

    private suspend fun TestScope.upload(t: String?, req: UsageUploadReq) =
        post("/v1/device/usage/upload", req, t)

    private suspend fun TestScope.today(id: UUID, token: String): UsageTodayOut =
        get("/v1/families/me/devices/$id/usage/today", token).json()

    private suspend fun TestScope.weekly(id: UUID, token: String): UsageWeeklyOut =
        get("/v1/families/me/devices/$id/usage/weekly", token).json()

    private suspend fun TestScope.report(id: UUID, token: String): ScreenTimeReportOut =
        get("/v1/families/me/devices/$id/screen-time/report", token).json()

    private suspend fun TestScope.events(id: UUID, token: String): List<EventOut> =
        get("/v1/families/me/devices/$id/events?limit=100", token).json()

    private suspend fun TestScope.setSettings(id: UUID, token: String, minutes: Int, nearPct: Int? = null, enabled: Boolean? = null) =
        post("/v1/families/me/devices/$id/screen-time/settings", ScreenTimeSettingsReq(minutes, nearPct, enabled), token)

    private suspend fun TestScope.createSchedule(
        id: UUID,
        token: String,
        name: String,
        start: String,
        end: String,
        days: Int = 127,
        enabled: Boolean = true,
    ): HttpStatusCode =
        post("/v1/families/me/devices/$id/screen-time/schedules", ScheduleReq(name, start, end, days, enabled), token).status

    private fun todayBit() = 1 shl (LocalDate.now().dayOfWeek.value - 1)

    /** پنجره‌ی تمام‌روز که «الان» فعال است (حتی نزدیک نیمه‌شب). */
    private fun activeWindow(): Pair<String, String> =
        if (LocalTime.now() < LocalTime.of(23, 59)) "00:00" to "23:59" else "23:59" to "23:58"

    // ================= Usage upload & reads =================
    @Test
    fun `no usage data yields empty today and zero weekly`() = withApp {
        val p = fullPairing()
        val t = today(p.device.deviceId, p.parentAccess)
        assertEquals(0, t.totalMinutes)
        assertTrue(t.topApps.isEmpty())
        val w = weekly(p.device.deviceId, p.parentAccess)
        assertEquals(7, w.days.size)
        assertEquals(0, w.totalMinutes)
        assertEquals(0, w.averageMinutes)
    }

    @Test
    fun `single app upload shows in today with percent 100`() = withApp {
        val p = fullPairing()
        val r = upload(p.deviceTokens.accessToken, uploadReq(day(LocalDate.now(), app("com.example.one", 45, "One"))))
        assertEquals(HttpStatusCode.OK, r.status)
        val t = today(p.device.deviceId, p.parentAccess)
        assertEquals(45, t.totalMinutes)
        assertEquals(1, t.topApps.size)
        assertEquals("com.example.one", t.topApps[0].packageName)
        assertEquals("One", t.topApps[0].appName)
        assertEquals(100.0, t.topApps[0].percent, 0.01)
    }

    @Test
    fun `multiple apps sorted by usage with correct percents`() = withApp {
        val p = fullPairing()
        upload(
            p.deviceTokens.accessToken,
            uploadReq(
                day(
                    LocalDate.now(),
                    app("com.a", 30), app("com.b", 90), app("com.c", 0),
                ),
            ),
        )
        val apps: List<AppUsageOut> =
            get("/v1/families/me/devices/${p.device.deviceId}/usage/apps", p.parentAccess).json()
        assertEquals(3, apps.size)
        assertEquals("com.b", apps[0].packageName)
        assertEquals(75.0, apps[0].percent, 0.01)
        assertEquals(25.0, apps[1].percent, 0.01)
        val t = today(p.device.deviceId, p.parentAccess)
        assertEquals(120, t.totalMinutes)
    }

    @Test
    fun `large values accepted at bounds and rejected above`() = withApp {
        val p = fullPairing()
        val t = p.deviceTokens.accessToken
        assertEquals(HttpStatusCode.OK, upload(t, uploadReq(day(LocalDate.now(), app("com.big", 1440)))).status)
        assertEquals(HttpStatusCode.BadRequest, upload(t, uploadReq(day(LocalDate.now(), app("com.over", 1441)))).status)
        assertEquals(HttpStatusCode.BadRequest, upload(t, uploadReq(day(LocalDate.now(), app("com.neg", -5)))).status)
        // ۸۰ اپ مجاز، ۸۱ رد
        val ok = (1..80).map { app("com.p$it", 1) }
        assertEquals(HttpStatusCode.OK, upload(t, uploadReq(day(LocalDate.now(), *ok.toTypedArray()))).status)
        val tooMany = (1..81).map { app("com.q$it", 1) }
        assertEquals(HttpStatusCode.BadRequest, upload(t, uploadReq(day(LocalDate.now(), *tooMany.toTypedArray()))).status)
        // ۸ روز مجاز، ۹ رد
        val days9 = (0..8).map { day(LocalDate.now().minusDays(it.toLong()), app("com.d$it", 1)) }
        assertEquals(HttpStatusCode.BadRequest, upload(t, uploadReq(*days9.toTypedArray())).status)
    }

    @Test
    fun `invalid package name rejected`() = withApp {
        val p = fullPairing()
        val r = upload(p.deviceTokens.accessToken, uploadReq(day(LocalDate.now(), app("bad pkg!", 10))))
        assertEquals(HttpStatusCode.BadRequest, r.status)
        assertEquals(ErrCode.VALIDATION, errorCode(r))
    }

    @Test
    fun `dates outside window rejected`() = withApp {
        val p = fullPairing()
        val t = p.deviceTokens.accessToken
        assertEquals(HttpStatusCode.BadRequest, upload(t, uploadReq(day(LocalDate.now().plusDays(1), app("com.a", 5)))).status)
        assertEquals(HttpStatusCode.BadRequest, upload(t, uploadReq(day(LocalDate.now().minusDays(8), app("com.a", 5)))).status)
        val badDate = upload(t, UsageUploadReq(listOf(UsageDayIn("not-a-date", listOf(app("com.a", 5))))))
        assertEquals(HttpStatusCode.BadRequest, badDate.status)
    }

    @Test
    fun `duplicate upload is idempotent overwrite not append`() = withApp {
        val p = fullPairing()
        val t = p.deviceTokens.accessToken
        upload(t, uploadReq(day(LocalDate.now(), app("com.dup", 30))))
        upload(t, uploadReq(day(LocalDate.now(), app("com.dup", 30))))
        upload(t, uploadReq(day(LocalDate.now(), app("com.dup", 45))))
        val apps: List<AppUsageOut> =
            get("/v1/families/me/devices/${p.device.deviceId}/usage/apps", p.parentAccess).json()
        assertEquals(1, apps.size)
        assertEquals(45, apps[0].usageMinutes)
        val todayOut = today(p.device.deviceId, p.parentAccess)
        assertEquals(45, todayOut.totalMinutes)
    }

    @Test
    fun `usage embedded in sync request is stored`() = withApp {
        val p = fullPairing()
        val req = SyncReq(
            90, "wifi", null, null, null, null, null, 0,
            uploadReq(day(LocalDate.now(), app("com.synced", 25))),
        )
        assertEquals(HttpStatusCode.OK, post("/v1/device/sync", req, p.deviceTokens.accessToken).status)
        val t = today(p.device.deviceId, p.parentAccess)
        assertEquals(25, t.totalMinutes)
    }

    @Test
    fun `weekly report has 7 day rows with totals average and extremes`() = withApp {
        val p = fullPairing()
        val t = p.deviceTokens.accessToken
        upload(t, uploadReq(day(LocalDate.now(), app("com.a", 60))))
        upload(t, uploadReq(day(LocalDate.now().minusDays(1), app("com.a", 120))))
        upload(t, uploadReq(day(LocalDate.now().minusDays(2), app("com.a", 30))))
        val w = weekly(p.device.deviceId, p.parentAccess)
        assertEquals(7, w.days.size)
        assertEquals(210, w.totalMinutes)
        assertEquals(30, w.averageMinutes)
        assertEquals(LocalDate.now().minusDays(1).toString(), w.mostUsedDay)
        // کم‌ترین روز پرمصرف: یکی از ۴ روز صفر
        assertNotNull(w.leastUsedDay)
        assertEquals(0, w.days.first { it.date == w.leastUsedDay }.totalMinutes)
    }

    @Test
    fun `app detail exposes today weekly launches and last used`() = withApp {
        val p = fullPairing()
        val t = p.deviceTokens.accessToken
        val nowMs = System.currentTimeMillis()
        upload(
            t,
            uploadReq(
                day(LocalDate.now(), UsageAppIn("com.detail", "Detail", "game", 40, 7, nowMs - 3600_000, nowMs, null, null)),
                day(LocalDate.now().minusDays(1), app("com.detail", 20, launches = 3)),
            ),
        )
        val d: AppDetailOut =
            get("/v1/families/me/devices/${p.device.deviceId}/usage/apps/com.detail/detail", p.parentAccess).json()
        assertEquals("com.detail", d.packageName)
        assertEquals("Detail", d.appName)
        assertEquals(40, d.todayMinutes)
        assertEquals(7, d.todayLaunches)
        assertEquals(10, d.weekLaunches)
        assertEquals(7, d.week.size)
        assertEquals(60, d.week.sumOf { it.totalMinutes })
        assertNotNull(d.lastUsedAt)
    }

    @Test
    fun `daily report has total most used apps count sessions and real categories only`() = withApp {
        val p = fullPairing()
        val t = p.deviceTokens.accessToken
        upload(
            t,
            uploadReq(
                day(
                    LocalDate.now(),
                    app("com.g1", 60, "Game1", "game", launches = 4),
                    app("com.g2", 30, "Game2", "game", launches = 2),
                    app("com.s1", 10, "Social", "social", launches = 1),
                ),
            ),
        )
        val r: DailyReportOut =
            get("/v1/families/me/devices/${p.device.deviceId}/usage/report/daily", p.parentAccess).json()
        assertEquals(100, r.totalMinutes)
        assertEquals("com.g1", r.mostUsedApp?.packageName)
        assertEquals(3, r.appsUsed)
        assertEquals(7, r.sessions)
        assertEquals(2, r.categories.size)
        assertEquals("game", r.categories[0].category)
        assertEquals(90, r.categories[0].minutes)
        assertEquals("social", r.categories[1].category)
    }

    @Test
    fun `invalid category rejected - never fabricated`() = withApp {
        val p = fullPairing()
        val r = upload(p.deviceTokens.accessToken, uploadReq(day(LocalDate.now(), app("com.a", 5, category = "made_up_cat"))))
        assertEquals(HttpStatusCode.BadRequest, r.status)
    }

    @Test
    fun `icon upload stored and served as png, unknown package 404`() = withApp {
        val p = fullPairing()
        val t = p.deviceTokens.accessToken
        upload(t, uploadReq(day(LocalDate.now(), app("com.ico", 5, icon = png1x1))))
        val r = get("/v1/families/me/devices/${p.device.deviceId}/usage/apps/com.ico/icon", p.parentAccess)
        assertEquals(HttpStatusCode.OK, r.status)
        val bytes = Base64.getDecoder().decode(png1x1)
        assertEquals(bytes.size, r.body<ByteArray>().size)
        val missing = get("/v1/families/me/devices/${p.device.deviceId}/usage/apps/com.none/icon", p.parentAccess)
        assertEquals(HttpStatusCode.NotFound, missing.status)
    }

    // ================= Daily limit =================
    @Test
    fun `limit disabled emits no events even over limit`() = withApp {
        val p = fullPairing()
        val id = p.device.deviceId
        val t = p.deviceTokens.accessToken
        setSettings(id, p.parentAccess, 100, 80, false)
        upload(t, uploadReq(day(LocalDate.now(), app("com.a", 1400))))
        val todayOut = today(id, p.parentAccess)
        assertFalse(todayOut.limitEnabled)
        assertNull(todayOut.remainingMinutes)
        val evs = events(id, p.parentAccess)
        assertFalse(evs.any { it.eventType == EventType.SCREEN_TIME_LIMIT_NEAR })
        assertFalse(evs.any { it.eventType == EventType.SCREEN_TIME_LIMIT_REACHED })
    }

    @Test
    fun `limit enabled transitions under near reached with single events`() = withApp {
        val p = fullPairing()
        val id = p.device.deviceId
        val t = p.deviceTokens.accessToken
        setSettings(id, p.parentAccess, 100, 80, true)

        upload(t, uploadReq(day(LocalDate.now(), app("com.a", 50))))
        var todayOut = today(id, p.parentAccess)
        assertEquals("under", todayOut.state)
        assertEquals(50, todayOut.remainingMinutes)
        assertTrue(events(id, p.parentAccess).none { it.eventType.startsWith("SCREEN_TIME") })

        upload(t, uploadReq(day(LocalDate.now(), app("com.a", 85))))
        todayOut = today(id, p.parentAccess)
        assertEquals("near", todayOut.state)
        assertEquals(15, todayOut.remainingMinutes)
        var evs = events(id, p.parentAccess)
        assertEquals(1, evs.count { it.eventType == EventType.SCREEN_TIME_LIMIT_NEAR })
        assertEquals("85", evs.first { it.eventType == EventType.SCREEN_TIME_LIMIT_NEAR }.metadata["total_minutes"])

        // sync تکراری ⇒ رویداد تکراری ندارد (dedupe)
        upload(t, uploadReq(day(LocalDate.now(), app("com.a", 85))))
        report(id, p.parentAccess)
        evs = events(id, p.parentAccess)
        assertEquals(1, evs.count { it.eventType == EventType.SCREEN_TIME_LIMIT_NEAR })

        upload(t, uploadReq(day(LocalDate.now(), app("com.a", 120))))
        todayOut = today(id, p.parentAccess)
        assertEquals("reached", todayOut.state)
        assertEquals(0, todayOut.remainingMinutes)
        evs = events(id, p.parentAccess)
        assertEquals(1, evs.count { it.eventType == EventType.SCREEN_TIME_LIMIT_REACHED })

        upload(t, uploadReq(day(LocalDate.now(), app("com.a", 130))))
        report(id, p.parentAccess)
        evs = events(id, p.parentAccess)
        assertEquals(1, evs.count { it.eventType == EventType.SCREEN_TIME_LIMIT_REACHED })
        assertEquals(1, evs.count { it.eventType == EventType.SCREEN_TIME_LIMIT_NEAR })
    }

    @Test
    fun `settings validation rejects out of range values`() = withApp {
        val p = fullPairing()
        val id = p.device.deviceId
        assertEquals(HttpStatusCode.BadRequest, setSettings(id, p.parentAccess, 4).status)
        assertEquals(HttpStatusCode.BadRequest, setSettings(id, p.parentAccess, 1441).status)
        assertEquals(HttpStatusCode.BadRequest, setSettings(id, p.parentAccess, 100, 49).status)
        assertEquals(HttpStatusCode.BadRequest, setSettings(id, p.parentAccess, 100, 101).status)
        assertEquals(HttpStatusCode.OK, setSettings(id, p.parentAccess, 240, 80, true).status)
        val s: ScreenTimeSettingsOut = get("/v1/families/me/devices/$id/screen-time/settings", p.parentAccess).json()
        assertEquals(240, s.dailyLimitMinutes)
        assertEquals(80, s.nearPct)
        assertTrue(s.enabled)
    }

    // ================= Schedules =================
    @Test
    fun `active schedule emits SCHEDULE_STARTED once then ENDED on disable`() = withApp {
        val p = fullPairing()
        val id = p.device.deviceId
        val (start, end) = activeWindow()
        assertEquals(HttpStatusCode.OK, createSchedule(id, p.parentAccess, "School", start, end, 127))
        var evs = events(id, p.parentAccess)
        assertEquals(1, evs.count { it.eventType == EventType.SCHEDULE_STARTED })
        // تریگر دوباره ⇒ تکرار ندارد
        report(id, p.parentAccess)
        report(id, p.parentAccess)
        evs = events(id, p.parentAccess)
        assertEquals(1, evs.count { it.eventType == EventType.SCHEDULE_STARTED })

        val schedules: List<ScheduleOut> = get("/v1/families/me/devices/$id/screen-time/schedules", p.parentAccess).json()
        assertEquals(1, schedules.size)
        val sid = schedules[0].id
        assertEquals(HttpStatusCode.OK, put("/v1/families/me/devices/$id/screen-time/schedules/$sid", ScheduleReq(null, null, null, null, false), p.parentAccess).status)
        report(id, p.parentAccess)
        evs = events(id, p.parentAccess)
        assertEquals(1, evs.count { it.eventType == EventType.SCHEDULE_ENDED })
    }

    @Test
    fun `schedule for other day of week stays inactive`() = withApp {
        val p = fullPairing()
        val id = p.device.deviceId
        val (start, end) = activeWindow()
        val tomorrowBit = 1 shl ((LocalDate.now().dayOfWeek.value) % 7)
        assertEquals(HttpStatusCode.OK, createSchedule(id, p.parentAccess, "Tomorrow", start, end, tomorrowBit))
        report(id, p.parentAccess)
        val evs = events(id, p.parentAccess)
        assertTrue(evs.none { it.eventType == EventType.SCHEDULE_STARTED })
    }

    @Test
    fun `multi day schedule with today bit is active`() = withApp {
        val p = fullPairing()
        val id = p.device.deviceId
        val (start, end) = activeWindow()
        val days = todayBit() or (1 shl ((LocalDate.now().dayOfWeek.value) % 7))
        assertEquals(HttpStatusCode.OK, createSchedule(id, p.parentAccess, "Two days", start, end, days))
        report(id, p.parentAccess)
        assertEquals(1, events(id, p.parentAccess).count { it.eventType == EventType.SCHEDULE_STARTED })
    }

    @Test
    fun `overnight schedule accepted and active window spans midnight`() = withApp {
        val p = fullPairing()
        val id = p.device.deviceId
        // bedtime 22:30 -> 07:00 طراحی و ذخیره می‌شود (بدون enforcement)
        assertEquals(HttpStatusCode.OK, createSchedule(id, p.parentAccess, "Bedtime", "22:30", "07:00", 127))
        val schedules: List<ScheduleOut> = get("/v1/families/me/devices/$id/screen-time/schedules", p.parentAccess).json()
        val bedtime = schedules.first { it.name == "Bedtime" }
        assertEquals("22:30", bedtime.startTime)
        assertEquals("07:00", bedtime.endTime)
        // پنجره‌ی شبانه با days=127 تقریباً همیشه فعال است (به‌جز ۲۳:۵۷-۲۳:۵۸)
        val now = LocalTime.now()
        if (!(now >= LocalTime.of(23, 57) && now < LocalTime.of(23, 58))) {
            assertEquals(HttpStatusCode.OK, createSchedule(id, p.parentAccess, "Wrap", "23:58", "23:57", 127))
            report(id, p.parentAccess)
            assertTrue(events(id, p.parentAccess).any { it.eventType == EventType.SCHEDULE_STARTED })
        }
    }

    @Test
    fun `invalid schedule ranges rejected`() = withApp {
        val p = fullPairing()
        val id = p.device.deviceId
        assertEquals(HttpStatusCode.BadRequest, createSchedule(id, p.parentAccess, "Same", "12:00", "12:00"))
        assertEquals(HttpStatusCode.BadRequest, createSchedule(id, p.parentAccess, "Bad hour", "25:00", "26:00"))
        assertEquals(HttpStatusCode.BadRequest, createSchedule(id, p.parentAccess, "Bad fmt", "aa:bb", "12:00"))
        assertEquals(HttpStatusCode.BadRequest, createSchedule(id, p.parentAccess, "Bad days", "12:00", "13:00", 0))
        assertEquals(HttpStatusCode.BadRequest, createSchedule(id, p.parentAccess, "Bad days", "12:00", "13:00", 128))
        assertEquals(HttpStatusCode.BadRequest, createSchedule(id, p.parentAccess, "", "12:00", "13:00"))
    }

    // ================= Permission (Usage Access) =================
    @Test
    fun `usage access granted then revoked emits PERMISSION_CHANGED and USAGE_ACCESS_REVOKED`() = withApp {
        val p = fullPairing()
        val id = p.device.deviceId
        val t = p.deviceTokens.accessToken
        post("/v1/device/sync", SyncReq(90, "wifi", permissions = mapOf("usage_access" to "granted")), t)
        val perms: Map<String, Any> = get("/v1/families/me/devices/$id/permissions", p.parentAccess).json()
        @Suppress("UNCHECKED_CAST")
        assertEquals("granted", (perms["permissions"] as Map<String, String>)["usage_access"])

        post("/v1/device/sync", SyncReq(90, "wifi", permissions = mapOf("usage_access" to "denied")), t)
        val evs = events(id, p.parentAccess)
        assertEquals(1, evs.count { it.eventType == EventType.USAGE_ACCESS_REVOKED })
        assertTrue(evs.any { it.eventType == EventType.PERMISSION_CHANGED && it.metadata["key"] == "usage_access" })

        // تکرار denied ⇒ رویداد جدید ندارد
        post("/v1/device/sync", SyncReq(90, "wifi", permissions = mapOf("usage_access" to "denied")), t)
        assertEquals(1, events(id, p.parentAccess).count { it.eventType == EventType.USAGE_ACCESS_REVOKED })
    }

    @Test
    fun `re grant after revoke does not emit revoked again`() = withApp {
        val p = fullPairing()
        val id = p.device.deviceId
        val t = p.deviceTokens.accessToken
        post("/v1/device/sync", SyncReq(90, "wifi", permissions = mapOf("usage_access" to "granted")), t)
        post("/v1/device/sync", SyncReq(90, "wifi", permissions = mapOf("usage_access" to "denied")), t)
        post("/v1/device/sync", SyncReq(90, "wifi", permissions = mapOf("usage_access" to "granted")), t)
        val evs = events(id, p.parentAccess)
        assertEquals(1, evs.count { it.eventType == EventType.USAGE_ACCESS_REVOKED })
    }

    // ================= Security =================
    @Test
    fun `usage upload without token rejected`() = withApp {
        val r = upload(null, uploadReq(day(LocalDate.now(), app("com.a", 5))))
        assertEquals(HttpStatusCode.Unauthorized, r.status)
        val bad = upload("not-a-jwt", uploadReq(day(LocalDate.now(), app("com.a", 5))))
        assertEquals(HttpStatusCode.Unauthorized, bad.status)
    }

    @Test
    fun `parent token cannot upload usage and device token cannot read reports`() = withApp {
        val p = fullPairing()
        val id = p.device.deviceId
        val r = upload(p.parentAccess, uploadReq(day(LocalDate.now(), app("com.a", 5))))
        assertEquals(HttpStatusCode.Unauthorized, r.status)
        val r2 = get("/v1/families/me/devices/$id/usage/today", p.deviceTokens.accessToken)
        assertEquals(HttpStatusCode.Unauthorized, r2.status)
    }

    @Test
    fun `wrong family cannot read other family usage - 404 no leak`() = withApp {
        val p = fullPairing()
        val id = p.device.deviceId
        val stranger = register("stranger3@test.ir")
        createFamily(stranger.accessToken, "Other Family")
        for (url in listOf("usage/today", "usage/weekly", "screen-time/report", "screen-time/settings")) {
            val r = get("/v1/families/me/devices/$id/$url", stranger.accessToken)
            assertEquals(HttpStatusCode.NotFound, r.status, url)
        }
        val w = post("/v1/families/me/devices/$id/screen-time/settings", ScreenTimeSettingsReq(60, null, null), stranger.accessToken)
        assertEquals(HttpStatusCode.NotFound, w.status)
    }

    @Test
    fun `expired token rejected on usage endpoints`() = withApp(mapOf("app.accessTokenTtlSec" to "5")) {
        val p = fullPairing()
        val t = p.deviceTokens.accessToken
        assertEquals(HttpStatusCode.OK, upload(t, uploadReq(day(LocalDate.now(), app("com.a", 5)))).status)
        delay(5500)
        val r = upload(t, uploadReq(day(LocalDate.now(), app("com.a", 6))))
        assertEquals(HttpStatusCode.Unauthorized, r.status)
        val r2 = get("/v1/families/me/devices/${p.device.deviceId}/usage/today", p.parentAccess)
        assertEquals(HttpStatusCode.Unauthorized, r2.status)
    }

    @Test
    fun `removed device cannot upload usage anymore`() = withApp {
        val p = fullPairing()
        delete("/v1/families/me/devices/${p.device.deviceId}", p.parentAccess)
        val r = upload(p.deviceTokens.accessToken, uploadReq(day(LocalDate.now(), app("com.a", 5))))
        assertTrue(r.status == HttpStatusCode.Gone || r.status == HttpStatusCode.Unauthorized)
    }
}
