package ir.guardianlink.server

import com.fasterxml.jackson.databind.ObjectMapper
import io.ktor.http.HttpStatusCode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import io.ktor.client.statement.bodyAsText
import java.util.UUID

class PrivacyLifecycleTest : IntegrationBase() {
    @BeforeEach fun reset() = truncate()

    @Test
    fun `family JSON export authenticates isolates family and excludes credentials`() = withApp {
        val emailA = "export-a-${UUID.randomUUID().toString().take(8)}@test.ir"
        val emailB = "export-b-${UUID.randomUUID().toString().take(8)}@test.ir"
        val a = fullPairing(emailA)
        val b = fullPairing(emailB)
        sql("""INSERT INTO app_usage_daily(device_id,package_name,app_name,category,usage_date,usage_minutes)
            VALUES('${a.device.deviceId}','synthetic.family.a','Synthetic A','test',CURRENT_DATE,17)""")
        sql("""INSERT INTO app_usage_daily(device_id,package_name,app_name,category,usage_date,usage_minutes)
            VALUES('${b.device.deviceId}','synthetic.family.b','Synthetic B','test',CURRENT_DATE,23)""")

        assertEquals(HttpStatusCode.Unauthorized, get("/v1/families/me/export").status)
        assertEquals(HttpStatusCode.Unauthorized, get("/v1/families/me/export", a.deviceTokens.accessToken).status)
        val response = get("/v1/families/me/export", a.parentAccess)
        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.headers["Content-Disposition"].orEmpty().contains("guardianlink-family-export.json"))
        val text = response.bodyAsText()
        val json = ObjectMapper().readTree(text)
        assertEquals("family", json.path("scope").asText())
        assertTrue(text.contains("synthetic.family.a"))
        assertFalse(text.contains("synthetic.family.b"))
        assertFalse(text.contains("device_secret_hash"))
        assertFalse(text.contains("install_id"))
        assertFalse(text.contains("password_hash"))
        assertFalse(text.contains("guardian-test-password"))
        assertFalse(text.contains(a.deviceTokens.refreshToken))
        assertEquals(HttpStatusCode.BadRequest, get("/v1/families/me/export?format=csv", a.parentAccess).status)
        assertEquals(HttpStatusCode.BadRequest, get("/v1/families/me/export?scope=account", a.parentAccess).status)
        assertEquals(HttpStatusCode.BadRequest, get("/v1/families/me/export?scope=family&family_id=${b.device.deviceId}", a.parentAccess).status)
    }

    @Test
    fun `family JSON export handles empty datasets and enforces row bound`() = withApp {
        val emptyParent = register("empty-${UUID.randomUUID().toString().take(8)}@test.ir")
        createFamily(emptyParent.accessToken, "Empty synthetic family")
        val emptyResponse = get("/v1/families/me/export", emptyParent.accessToken)
        assertEquals(HttpStatusCode.OK, emptyResponse.status)
        assertEquals(0, ObjectMapper().readTree(emptyResponse.bodyAsText()).path("data").path("locations").size())

        val p = fullPairing()
        sql("""INSERT INTO app_usage_daily(device_id,package_name,app_name,category,usage_date,usage_minutes)
            SELECT '${p.device.deviceId}','synthetic.limit.'||n,'Synthetic','test',CURRENT_DATE,1 FROM generate_series(1,10001) AS n""")
        val limited = get("/v1/families/me/export", p.parentAccess)
        assertEquals(HttpStatusCode.PayloadTooLarge, limited.status)
        assertEquals("EXPORT_TOO_LARGE", errorCode(limited))
    }

    @Test
    fun `family export rate limit caps repeated synchronous generation`() = withApp {
        val parent = register("rate-${UUID.randomUUID().toString().take(8)}@test.ir")
        createFamily(parent.accessToken, "Synthetic rate family")
        repeat(5) { assertEquals(HttpStatusCode.OK, get("/v1/families/me/export", parent.accessToken).status) }
        assertEquals(HttpStatusCode.TooManyRequests, get("/v1/families/me/export", parent.accessToken).status)
    }

    @Test
    fun `family delete rate limit bounds failed confirmation attempts`() = withApp {
        val parent = register("delete-rate-${UUID.randomUUID().toString().take(8)}@test.ir")
        createFamily(parent.accessToken, "Synthetic delete rate family")
        repeat(3) {
            assertEquals(HttpStatusCode.Unauthorized, delete("/v1/families/me", parent.accessToken, DestructiveActionConfirmationReq("wrong-password-value")).status)
        }
        assertEquals(HttpStatusCode.TooManyRequests, delete("/v1/families/me", parent.accessToken, DestructiveActionConfirmationReq("wrong-password-value")).status)
        assertEquals(1, scalarInt("SELECT count(*) FROM families"))
        assertEquals(HttpStatusCode.OK, get("/v1/me", parent.accessToken).status)
    }

    @Test
    fun `location delete uses explicit scope family ownership and is repeatable`() = withApp {
        val a = fullPairing()
        val b = fullPairing()
        sql("""INSERT INTO locations(device_id,latitude,longitude,accuracy_m,client_ts,source)
            VALUES('${a.device.deviceId}',48.1,2.1,15,now()-interval '2 hours','gps'),
                  ('${a.device.deviceId}',48.2,2.2,20,now()-interval '1 hour','gps'),
                  ('${b.device.deviceId}',47.1,1.1,25,now()-interval '1 hour','gps')""")
        assertEquals(HttpStatusCode.Unauthorized, delete("/v1/families/me/devices/${a.device.deviceId}/location/history?scope=all").status)
        assertEquals(HttpStatusCode.BadRequest, delete("/v1/families/me/devices/${a.device.deviceId}/location/history", a.parentAccess).status)
        assertEquals(HttpStatusCode.BadRequest, delete("/v1/families/me/devices/${a.device.deviceId}/location/history?scope=unsupported", a.parentAccess).status)
        assertEquals(HttpStatusCode.NotFound, delete("/v1/families/me/devices/${UUID.randomUUID()}/location/history?scope=all", a.parentAccess).status)
        assertEquals(HttpStatusCode.NotFound, delete("/v1/families/me/devices/${b.device.deviceId}/location/history?scope=all", a.parentAccess).status)
        val first = delete("/v1/families/me/devices/${a.device.deviceId}/location/history?scope=all", a.parentAccess)
        assertEquals(HttpStatusCode.OK, first.status)
        assertTrue(first.bodyAsText().contains("\"deleted\":2"))
        val repeated = delete("/v1/families/me/devices/${a.device.deviceId}/location/history?scope=all", a.parentAccess)
        assertEquals(HttpStatusCode.OK, repeated.status)
        assertTrue(repeated.bodyAsText().contains("\"deleted\":0"))
        assertEquals(0, scalarInt("SELECT count(*) FROM locations WHERE device_id='${a.device.deviceId}'"))
        assertEquals(1, scalarInt("SELECT count(*) FROM locations WHERE device_id='${b.device.deviceId}'"))
    }

    @Test
    fun `device removal remains revoke not erase and denies cross family`() = withApp {
        val a = fullPairing()
        val b = fullPairing()
        sql("""INSERT INTO locations(device_id,latitude,longitude,accuracy_m,client_ts,source)
            VALUES('${a.device.deviceId}',48.1,2.1,15,now(),'gps')""")
        assertEquals(HttpStatusCode.NotFound, delete("/v1/families/me/devices/${b.device.deviceId}", a.parentAccess).status)
        val removed = delete("/v1/families/me/devices/${a.device.deviceId}", a.parentAccess)
        assertEquals(HttpStatusCode.NoContent, removed.status)
        assertEquals(1, scalarInt("SELECT count(*) FROM devices WHERE id='${a.device.deviceId}' AND status='removed' AND device_secret_hash IS NULL"))
        assertEquals(1, scalarInt("SELECT count(*) FROM device_sessions WHERE device_id='${a.device.deviceId}' AND revoked_at IS NOT NULL"))
        assertEquals(1, scalarInt("SELECT count(*) FROM locations WHERE device_id='${a.device.deviceId}'"))
        val heartbeat = post("/v1/device/heartbeat", HeartbeatReq(10, null, null, null), a.deviceTokens.accessToken)
        assertTrue(heartbeat.status == HttpStatusCode.Gone || heartbeat.status == HttpStatusCode.Unauthorized)
        assertEquals(HttpStatusCode.Conflict, delete("/v1/families/me/devices/${a.device.deviceId}", a.parentAccess).status)
        assertEquals(1, scalarInt("SELECT count(*) FROM devices WHERE id='${b.device.deviceId}' AND status='active'"))
    }

    @Test
    fun `shared family deletion is blocked without changing membership or sessions`() = withApp {
        val email = "shared-a-${UUID.randomUUID().toString().take(8)}@test.ir"
        val a = fullPairing(email)
        val otherEmail = "shared-b-${UUID.randomUUID().toString().take(8)}@test.ir"
        register(otherEmail)
        sql("""INSERT INTO family_members(family_id,user_id,role)
            SELECT d.family_id,u.id,'parent' FROM devices d CROSS JOIN users u
            WHERE d.id='${a.device.deviceId}' AND u.email='$otherEmail'""")
        val deleteFamily = delete("/v1/families/me", a.parentAccess, DestructiveActionConfirmationReq("guardian-test-password"))
        assertEquals(HttpStatusCode.Conflict, deleteFamily.status)
        val deleteAccount = delete("/v1/me", a.parentAccess, DestructiveActionConfirmationReq("guardian-test-password"))
        assertEquals(HttpStatusCode.Conflict, deleteAccount.status)
        assertEquals(1, scalarInt("SELECT count(*) FROM families f JOIN devices d ON d.family_id=f.id WHERE d.id='${a.device.deviceId}'"))
        assertEquals(2, scalarInt("SELECT count(*) FROM family_members WHERE family_id=(SELECT family_id FROM devices WHERE id='${a.device.deviceId}')"))
        assertEquals(HttpStatusCode.OK, get("/v1/me", a.parentAccess).status)
    }

    @Test
    fun `sole family deletion cascades dependents and account stays active`() = withApp {
        val p = fullPairing()
        val familyId = scalarUuid("SELECT family_id FROM devices WHERE id='${p.device.deviceId}'")
        val userId = scalarUuid("SELECT user_id FROM family_members WHERE family_id='$familyId'")
        val geofenceId = UUID.randomUUID()
        val sosId = UUID.randomUUID()
        val eventId = UUID.randomUUID()
        sql("""INSERT INTO app_usage_daily(device_id,package_name,app_name,category,usage_date,usage_minutes)
            VALUES('${p.device.deviceId}','synthetic.delete.app','Synthetic','test',CURRENT_DATE,11)""")
        sql("""INSERT INTO locations(device_id,latitude,longitude,accuracy_m,client_ts,source)
            VALUES('${p.device.deviceId}',48.3,2.3,10,now(),'gps')""")
        sql("INSERT INTO geofences(id,device_id,name,latitude,longitude,radius_m) VALUES('$geofenceId','${p.device.deviceId}','Synthetic zone',48.3,2.3,100)")
        sql("INSERT INTO geofence_states(geofence_id,state) VALUES('$geofenceId','unknown')")
        sql("INSERT INTO geofence_events(geofence_id,device_id,event_type) VALUES('$geofenceId','${p.device.deviceId}','GEOFENCE_CREATED')")
        sql("""INSERT INTO device_events(id,device_id,event_type) VALUES('$eventId','${p.device.deviceId}','DEVICE_ONLINE')""")
        sql("""INSERT INTO family_notifications(family_id,device_id,event_id,event_type,category,severity,title_key,body_key,occurred_at)
            VALUES('$familyId','${p.device.deviceId}','$eventId','DEVICE_ONLINE','CONNECTIVITY','INFO','online.title','online.body',now())""")
        sql("INSERT INTO notification_user_state(notification_id,user_id) SELECT id,'$userId' FROM family_notifications WHERE event_id='$eventId'")
        sql("""INSERT INTO sos_sessions(id,family_id,device_id,client_request_id,status,activated_at)
            VALUES('$sosId','$familyId','${p.device.deviceId}','${UUID.randomUUID()}','RESOLVED',now())""")
        sql("""INSERT INTO sos_location_points(session_id,latitude,longitude,accuracy_m,client_ts,provider)
            VALUES('$sosId',48.3,2.3,10,now(),'gps')""")
        sql("""INSERT INTO sos_events(session_id,family_id,device_id,event_type,actor_type,actor_user_id)
            VALUES('$sosId','$familyId','${p.device.deviceId}','SOS_RESOLVED','parent','$userId')""")
        sql("""INSERT INTO sos_notes(session_id,family_id,author_user_id,note)
            VALUES('$sosId','$familyId','$userId','Synthetic test note')""")

        val noPassword = delete("/v1/families/me", p.parentAccess, DestructiveActionConfirmationReq("wrong-password-value"))
        assertEquals(HttpStatusCode.Unauthorized, noPassword.status)
        assertEquals(1, scalarInt("SELECT count(*) FROM families WHERE id='$familyId'"))
        val response = delete("/v1/families/me", p.parentAccess, DestructiveActionConfirmationReq("guardian-test-password"))
        assertEquals(HttpStatusCode.NoContent, response.status)
        for (table in listOf("families", "family_members", "devices", "device_sessions", "pairing_sessions", "locations", "app_usage_daily", "geofences", "geofence_states", "geofence_events", "device_events", "family_notifications", "notification_user_state", "sos_sessions", "sos_location_points", "sos_events", "sos_notes")) {
            assertEquals(0, scalarInt("SELECT count(*) FROM $table"), "rows remain in $table")
        }
        assertEquals(1, scalarInt("SELECT count(*) FROM users WHERE id='$userId'"))
        assertEquals(HttpStatusCode.NotFound, get("/v1/families/me", p.parentAccess).status)
        assertEquals(HttpStatusCode.NotFound, delete("/v1/families/me", p.parentAccess, DestructiveActionConfirmationReq("guardian-test-password")).status)
        val oldDeviceAccess = post("/v1/device/heartbeat", HeartbeatReq(10, null, null, null), p.deviceTokens.accessToken).status
        assertTrue(oldDeviceAccess == HttpStatusCode.Unauthorized || oldDeviceAccess == HttpStatusCode.Gone)
        assertEquals(HttpStatusCode.OK, get("/v1/me", p.parentAccess).status)
    }

    @Test
    fun `family deletion failure rolls back all effects`() = withApp {
        val p = fullPairing()
        val familyId = scalarUuid("SELECT family_id FROM devices WHERE id='${p.device.deviceId}'")
        sql("""CREATE OR REPLACE FUNCTION phase28_test_fail_device_delete() RETURNS trigger AS $$
            BEGIN RAISE EXCEPTION 'synthetic transaction rollback test'; END; $$ LANGUAGE plpgsql""")
        sql("CREATE TRIGGER phase28_test_fail_device_delete BEFORE DELETE ON devices FOR EACH ROW EXECUTE FUNCTION phase28_test_fail_device_delete()")
        try {
            val response = delete("/v1/families/me", p.parentAccess, DestructiveActionConfirmationReq("guardian-test-password"))
            assertEquals(HttpStatusCode.InternalServerError, response.status)
            assertEquals(1, scalarInt("SELECT count(*) FROM families WHERE id='$familyId'"))
            assertEquals(1, scalarInt("SELECT count(*) FROM devices WHERE id='${p.device.deviceId}' AND status='active'"))
            assertEquals(1, scalarInt("SELECT count(*) FROM family_members WHERE family_id='$familyId'"))
            assertEquals(HttpStatusCode.OK, get("/v1/families/me", p.parentAccess).status)
        } finally {
            sql("DROP TRIGGER IF EXISTS phase28_test_fail_device_delete ON devices")
            sql("DROP FUNCTION IF EXISTS phase28_test_fail_device_delete()")
        }
    }

    @Test
    fun `account without family is deleted with its sessions`() = withApp {
        val email = "account-only-${UUID.randomUUID().toString().take(8)}@test.ir"
        val parent = register(email)
        val userId = scalarUuid("SELECT id FROM users WHERE email='$email'")
        assertEquals(HttpStatusCode.NoContent, delete("/v1/me", parent.accessToken, DestructiveActionConfirmationReq("guardian-test-password")).status)
        assertEquals(0, scalarInt("SELECT count(*) FROM users WHERE id='$userId'"))
        assertEquals(0, scalarInt("SELECT count(*) FROM user_sessions WHERE user_id='$userId'"))
        assertEquals(0, scalarInt("SELECT count(*) FROM families"))
        assertEquals(HttpStatusCode.Unauthorized, get("/v1/me", parent.accessToken).status)
    }

    @Test
    fun `account deletion cascades account family and sessions and invalidates token`() = withApp {
        val p = fullPairing()
        val familyId = scalarUuid("SELECT family_id FROM devices WHERE id='${p.device.deviceId}'")
        val userId = scalarUuid("SELECT user_id FROM family_members WHERE family_id='$familyId'")
        val invalid = delete("/v1/me", p.parentAccess, DestructiveActionConfirmationReq("wrong-password-value"))
        assertEquals(HttpStatusCode.Unauthorized, invalid.status)
        assertEquals(1, scalarInt("SELECT count(*) FROM users WHERE id='$userId'"))
        val response = delete("/v1/me", p.parentAccess, DestructiveActionConfirmationReq("guardian-test-password"))
        assertEquals(HttpStatusCode.NoContent, response.status)
        assertEquals(0, scalarInt("SELECT count(*) FROM users WHERE id='$userId'"))
        assertEquals(0, scalarInt("SELECT count(*) FROM families WHERE id='$familyId'"))
        assertEquals(0, scalarInt("SELECT count(*) FROM devices WHERE id='${p.device.deviceId}'"))
        assertEquals(0, scalarInt("SELECT count(*) FROM user_sessions WHERE user_id='$userId'"))
        assertEquals(0, scalarInt("SELECT count(*) FROM device_sessions WHERE device_id='${p.device.deviceId}'"))
        assertEquals(0, scalarInt("SELECT count(*) FROM pairing_sessions WHERE family_id='$familyId'"))
        assertEquals(HttpStatusCode.Unauthorized, get("/v1/me", p.parentAccess).status)
        assertEquals(HttpStatusCode.Unauthorized, delete("/v1/me", p.parentAccess, DestructiveActionConfirmationReq("guardian-test-password")).status)
        val deviceAccess = post("/v1/device/heartbeat", HeartbeatReq(10, null, null, null), p.deviceTokens.accessToken).status
        assertTrue(deviceAccess == HttpStatusCode.Unauthorized || deviceAccess == HttpStatusCode.Gone)
    }
}
