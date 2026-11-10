package ir.guardianlink.server

import io.ktor.http.HttpStatusCode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.util.UUID

/** Phase 6 integration contract: inventory, optimistic rules, authorization, dedupe and thresholds. */
class Phase6Test : IntegrationBase() {
    @BeforeEach fun reset() = truncate()

    private fun inventory() = AppInventoryItemIn(
        packageName = "com.example.game", appName = "Example Game", versionName = "2.4",
        versionCode = 24, installedAtMs = System.currentTimeMillis() - 86_400_000,
        isSystemApp = false, category = "game",
    )

    private suspend fun TestScope.syncInventory(token: String, id: UUID, extra: AppManagementSyncIn? = null): AppRulesSyncOut {
        val payload = extra ?: AppManagementSyncIn(
            inventoryComplete = true, inventory = listOf(inventory()), managementMode = "normal",
            usageAccessGranted = true, timeZoneId = "UTC",
        )
        return post("/v1/device/app-management/sync", payload, token).json()
    }

    @Test fun `inventory is device-scoped and parent can create update delete versioned rules`() = withApp {
        val p = fullPairing()
        val id = p.device.deviceId
        val child = p.deviceTokens.accessToken
        syncInventory(child, id)
        val catalog = get("/v1/families/me/devices/$id/apps", p.parentAccess).json<AppCatalogListOut>()
        assertEquals("com.example.game", catalog.apps.single().packageName)
        val selfRule = post(
            "/v1/families/me/devices/$id/app-rules",
            AppRuleReq("ir.guardianlink.child", "RESTRICTED"), p.parentAccess,
        )
        assertEquals(HttpStatusCode.BadRequest, selfRule.status)
        assertEquals("normal", catalog.managementMode)
        assertEquals("advisory_only", catalog.enforcementCapability)

        val create = post(
            "/v1/families/me/devices/$id/app-rules",
            AppRuleReq("com.example.game", "LIMITED", dailyLimitSeconds = 3600),
            p.parentAccess,
        )
        assertEquals(HttpStatusCode.OK, create.status)
        val rule = create.json<AppRuleOut>()
        assertEquals(1, rule.version)
        assertEquals("LIMITED", rule.ruleType)

        val stale = put(
            "/v1/families/me/devices/$id/app-rules/${rule.id}",
            AppRuleReq("com.example.game", "LIMITED", dailyLimitSeconds = 1800, expectedVersion = 0),
            p.parentAccess,
        )
        assertEquals(HttpStatusCode.Conflict, stale.status)

        val updated = put(
            "/v1/families/me/devices/$id/app-rules/${rule.id}",
            AppRuleReq("com.example.game", "SCHEDULED", schedules = listOf(AppRuleScheduleReq(127, "20:00", "07:00")), expectedVersion = rule.version),
            p.parentAccess,
        ).json<AppRuleOut>()
        assertEquals("SCHEDULED", updated.ruleType)
        assertEquals(127, updated.schedules.single().dayOfWeek)
        assertTrue(updated.version > rule.version)

        val deleted = delete(
            "/v1/families/me/devices/$id/app-rules/${rule.id}?expectedVersion=${updated.version}",
            p.parentAccess,
        )
        assertEquals(HttpStatusCode.OK, deleted.status)
        assertTrue(get("/v1/families/me/devices/$id/app-rules", p.parentAccess).json<AppRulesOut>().rules.isEmpty())
    }

    @Test fun `restriction events are deduplicated by authenticated child event id`() = withApp {
        val p = fullPairing()
        val id = p.device.deviceId
        syncInventory(p.deviceTokens.accessToken, id)
        val rule = post(
            "/v1/families/me/devices/$id/app-rules",
            AppRuleReq("com.example.game", "RESTRICTED"), p.parentAccess,
        ).json<AppRuleOut>()
        val event = AppRestrictionEventIn(
            clientEventId = UUID.randomUUID().toString(), packageName = "com.example.game",
            eventType = EventType.APP_RESTRICTION_UNSUPPORTED, ruleVersion = rule.version,
            clientTs = System.currentTimeMillis(), detail = "device_owner_required",
        )
        repeat(2) {
            syncInventory(
                p.deviceTokens.accessToken, id,
                AppManagementSyncIn(
                    inventoryComplete = false, managementMode = "normal", usageAccessGranted = true,
                    timeZoneId = "UTC", events = listOf(event),
                ),
            )
        }
        val history = get("/v1/families/me/devices/$id/app-events", p.parentAccess).json<AppRestrictionEventsOut>()
        assertEquals(1, history.events.count { it.eventType == EventType.APP_RESTRICTION_UNSUPPORTED })
        assertEquals("unsupported", get("/v1/families/me/devices/$id/app-rules", p.parentAccess).json<AppRulesOut>().rules.single().lastEnforcementStatus)
    }

    @Test fun `usage thresholds emit once as uploaded usage crosses stages`() = withApp {
        val p = fullPairing()
        val id = p.device.deviceId
        syncInventory(p.deviceTokens.accessToken, id)
        post("/v1/families/me/devices/$id/app-rules", AppRuleReq("com.example.game", "LIMITED", dailyLimitSeconds = 3600, nearPct = 80, criticalPct = 90), p.parentAccess)
        val today = LocalDate.now(java.time.ZoneId.of("UTC")).toString()

        suspend fun upload(minutes: Int) {
            post(
                "/v1/device/sync",
                SyncReq(
                    batteryLevel = 55,
                    appManagement = AppManagementSyncIn(
                        managementMode = "normal", usageAccessGranted = true, timeZoneId = "UTC",
                    ),
                    usage = UsageUploadReq(listOf(UsageDayIn(today, listOf(
                        UsageAppIn("com.example.game", "Example Game", "game", minutes, 2, null, null),
                    )))),
                ),
                p.deviceTokens.accessToken,
            )
        }
        upload(50) // near (>=48 min)
        upload(58) // critical stage only (>=54 min)
        upload(60) // reached
        upload(60) // duplicate sync: no new threshold events
        val events = get("/v1/families/me/devices/$id/app-events?limit=20", p.parentAccess).json<AppRestrictionEventsOut>().events
        assertEquals(1, events.count { it.eventType == EventType.APP_USAGE_LIMIT_NEAR })
        assertEquals(1, events.count { it.eventType == EventType.APP_USAGE_LIMIT_CRITICAL })
        assertEquals(1, events.count { it.eventType == EventType.APP_USAGE_LIMIT_REACHED })
    }

    @Test fun `parent cannot read another family app inventory`() = withApp {
        val first = fullPairing()
        val second = fullPairing()
        syncInventory(first.deviceTokens.accessToken, first.device.deviceId)
        val denied = get("/v1/families/me/devices/${first.device.deviceId}/apps", second.parentAccess)
        assertTrue(denied.status == HttpStatusCode.NotFound || denied.status == HttpStatusCode.Forbidden)
        assertFalse(denied.status == HttpStatusCode.OK)
    }
}
