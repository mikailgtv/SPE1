package ir.guardianlink.core.network

import android.content.Context
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import ir.guardianlink.core.common.AppLog
import ir.guardianlink.core.common.Cat
import ir.guardianlink.core.common.Redact
import ir.guardianlink.core.storage.SecureStore
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

/** نگه‌دارنده credential هر اپ (Parent=user، Child=device). */
interface CredentialStore {
    data class Creds(val access: String, val refresh: String, val kind: String, val deviceId: String? = null)

    fun get(): Creds?
    fun put(c: Creds)
    fun clear()
}

class SecureCredentialStore(private val secure: SecureStore) : CredentialStore {
    companion object {
        const val KEY = "creds_json"
    }

    private val gson = Gson()

    @Volatile
    private var cache: CredentialStore.Creds? = null

    override fun get(): CredentialStore.Creds? {
        cache?.let { return it }
        val raw = secure.getBlocking(KEY) ?: return null
        return try {
            gson.fromJson(raw, CredentialStore.Creds::class.java).also { cache = it }
        } catch (_: Exception) {
            null
        }
    }

    override fun put(c: CredentialStore.Creds) {
        cache = c
        secure.putBlocking(KEY, gson.toJson(c))
    }

    override fun clear() {
        cache = null
        secure.removeBlocking(KEY)
    }
}

class ApiClient(
    private val baseUrl: String,
    val creds: CredentialStore,
) {
    private val gson: Gson = GsonBuilder()
        .setFieldNamingPolicy(com.google.gson.FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
        .create()

    private val jsonMedia = "application/json; charset=utf-8".toMediaType()

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .addInterceptor(AuthInterceptor())
        .authenticator(TokenAuthenticator())
        .build()

    private inner class AuthInterceptor : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            val req = chain.request()
            val withAuth = if (req.header("Authorization") == null) {
                creds.get()?.let {
                    req.newBuilder().header("Authorization", "Bearer " + it.access).build()
                } ?: req
            } else req
            return chain.proceed(withAuth)
        }
    }

    /** چرخش refresh روی 401 — یک‌بار برای هر درخواست، همزمان (thread-safe). */
    private inner class TokenAuthenticator : okhttp3.Authenticator {
        override fun authenticate(route: okhttp3.Route?, response: Response): Request? {
            if (response.request.header("X-GL-Retry") != null) return null
            if (response.code == 401 && response.request.url.encodedPath.contains("/auth/")) return null
            synchronized(this) {
                val cur = creds.get() ?: return null
                // شاید thread دیگری قبلاً refresh کرده
                val current = response.request.header("Authorization")
                if (current != null && current != "Bearer " + cur.access) {
                    return response.request.newBuilder()
                        .header("Authorization", "Bearer " + cur.access)
                        .header("X-GL-Retry", "1")
                        .build()
                }
                val refreshed: TokenResp? = try {
                    runBlocking { refreshOnce(cur) }
                } catch (e: Exception) {
                    AppLog.warn(Cat.AUTH, "refresh failed", "err" to e.javaClass.simpleName)
                    val status=(e as? ApiException)?.httpStatus
                    // Keep credentials unless the refresh endpoint explicitly rejects this session.
                    if(status==null || (status!=401 && status!=403 && status!=410)) return null
                    creds.clear()
                    return null
                }
                if (refreshed == null) return null
                creds.put(cur.copy(access = refreshed.accessToken, refresh = refreshed.refreshToken))
                return response.request.newBuilder()
                    .header("Authorization", "Bearer " + refreshed.accessToken)
                    .header("X-GL-Retry", "1")
                    .build()
            }
        }
    }

    private suspend fun refreshOnce(cur: CredentialStore.Creds): TokenResp {
        val (path, body) = if (cur.kind == "device") {
            "/v1/device/refresh" to DeviceRefreshReq(cur.deviceId ?: "", cur.refresh)
        } else {
            "/v1/auth/refresh" to RefreshReq(cur.refresh)
        }
        return rawPost(path, body)
    }

    // ---------- primitives ----------
    private fun Request.Builder.jsonBody(any: Any?): Request.Builder {
        if (any == null) return this
        return post(gson.toJson(any).toRequestBody(jsonMedia))
    }

    private suspend fun <T> execute(req: Request, type: Class<T>): T =
        suspendCancellableCoroutine { cont ->
            client.newCall(req).enqueue(object : okhttp3.Callback {
                override fun onFailure(call: okhttp3.Call, e: java.io.IOException) {
                    AppLog.warn(Cat.NETWORK, "request failed", "url" to req.url.encodedPath, "err" to e.javaClass.simpleName)
                    cont.resumeWithException(ApiException(ErrCode.NETWORK, 0, "network error"))
                }

                override fun onResponse(call: okhttp3.Call, resp: Response) {
                    resp.use {
                        val text = it.body?.string().orEmpty()
                        if (!it.isSuccessful) {
                            val (code, msg) = parseError(text)
                            cont.resumeWithException(ApiException(code, it.code, msg))
                        } else {
                            if (type == Unit::class.java) {
                                @Suppress("UNCHECKED_CAST")
                                cont.resume(Unit as T)
                            } else {
                                cont.resume(gson.fromJson(text, type))
                            }
                        }
                    }
                }
            })
        }

    private fun parseError(text: String): Pair<String, String> =
        try {
            val e = gson.fromJson(text, ErrorBody::class.java)
            (e?.code ?: ErrCode.SERVER_ERROR) to (e?.message ?: "error")
        } catch (_: Exception) {
            ErrCode.SERVER_ERROR to "error"
        }

    data class ErrorBody(val code: String?, val message: String?)

    private suspend fun <T> get(path: String, type: Class<T>): T =
        execute(Request.Builder().url(baseUrl + path).get().build(), type)

    private suspend fun <T> post(path: String, body: Any?, type: Class<T>): T =
        execute(
            Request.Builder().url(baseUrl + path).jsonBody(body).let {
                if (body == null) it.post("".toRequestBody(jsonMedia)) else it
            }.build(),
            type,
        )

    private suspend fun <T> delete(path: String, type: Class<T>): T =
        execute(Request.Builder().url(baseUrl + path).delete().build(), type)

    private suspend fun <T> put(path: String, body: Any?, type: Class<T>): T =
        execute(
            Request.Builder().url(baseUrl + path).put(gson.toJson(body).toRequestBody(jsonMedia)).build(),
            type,
        )

    private suspend inline fun <reified T> get(path: String): T = get(path, T::class.java)
    private suspend inline fun <reified T> post(path: String, body: Any? = null): T = post(path, body, T::class.java)
    private suspend inline fun <reified T> delete(path: String): T = delete(path, T::class.java)
    private suspend inline fun <reified T> put(path: String, body: Any? = null): T = put(path, body, T::class.java)

    /** POST بدون auth (برای refresh دستی). */
    private suspend fun rawPost(path: String, body: Any?): TokenResp {
        val req = Request.Builder()
            .url(baseUrl + path)
            .post(gson.toJson(body).toRequestBody(jsonMedia))
            .build()
        return execute(req, TokenResp::class.java)
    }

    // ---------- endpoints ----------
    suspend fun register(email: String, password: String): TokenResp =
        post("/v1/auth/register", RegisterReq(email, password))

    suspend fun login(email: String, password: String): TokenResp =
        post("/v1/auth/login", LoginReq(email, password))

    suspend fun me(): UserOut = get("/v1/me")

    suspend fun createFamily(name: String): FamilyOut = post("/v1/families", CreateFamilyReq(name))

    suspend fun familyMe(): FamilyDetailOut = get("/v1/families/me")

    suspend fun createPairing(): PairingCreateOut = post("/v1/pairing/sessions")

    suspend fun exchange(req: ExchangeReq): ExchangeOut = post("/v1/pairing/exchange", req)

    suspend fun confirm(req: ConfirmReq): ConfirmOut = post("/v1/pairing/confirm", req)

    suspend fun deviceAuth(deviceId: String, secret: String): TokenResp =
        post("/v1/device/auth", DeviceAuthReq(deviceId, secret))

    suspend fun heartbeat(req: HeartbeatReq): HeartbeatOut = post("/v1/device/heartbeat", req)

    suspend fun status(req: StatusReq): Map<String, Any> = post("/v1/device/status", req)

    suspend fun sync(req: SyncReq): SyncOut = post("/v1/device/sync", req)

    suspend fun devices(): List<DeviceOut> =
        get("/v1/families/me/devices", Array<DeviceOut>::class.java).toList()

    suspend fun device(id: String): DeviceOut = get("/v1/families/me/devices/$id")

    suspend fun removeDevice(id: String) = delete<Unit>("/v1/families/me/devices/$id")

    suspend fun health(id: String): HealthOut = get("/v1/families/me/devices/$id/health")

    suspend fun permissions(id: String): Map<String, Any> = get("/v1/families/me/devices/$id/permissions")

    suspend fun events(id: String, limit: Int = 20): List<EventOut> =
        get("/v1/families/me/devices/$id/events?limit=$limit", Array<EventOut>::class.java).toList()

    suspend fun familyEvents(limit: Int = 20): List<EventOut> =
        get("/v1/families/me/events?limit=$limit", Array<EventOut>::class.java).toList()

    suspend fun notifications(limit: Int = 30, before: String? = null, unreadOnly: Boolean = false, category: String? = null, severity: String? = null, search: String? = null, deviceId: String? = null, from: String? = null, to: String? = null): NotificationPageOut {
        val params = buildList {
            add("limit=$limit")
            before?.let { add("before=" + java.net.URLEncoder.encode(it, "UTF-8")) }
            if (unreadOnly) add("unread=true")
            category?.let { add("category=" + java.net.URLEncoder.encode(it, "UTF-8")) }
            severity?.let { add("severity=" + java.net.URLEncoder.encode(it, "UTF-8")) }
            search?.let { add("q=" + java.net.URLEncoder.encode(it, "UTF-8")) }
            deviceId?.let { add("device_id=" + java.net.URLEncoder.encode(it, "UTF-8")) }
            from?.let { add("from=" + java.net.URLEncoder.encode(it, "UTF-8")) }
            to?.let { add("to=" + java.net.URLEncoder.encode(it, "UTF-8")) }
        }.joinToString("&")
        return get("/v1/families/me/notifications?$params")
    }

    suspend fun notification(id: String): NotificationOut = get("/v1/families/me/notifications/$id")
    suspend fun markNotificationRead(id: String) = post<Unit>("/v1/families/me/notifications/$id/read", null)
    suspend fun acknowledgeNotification(id: String) = post<Unit>("/v1/families/me/notifications/$id/acknowledge", null)
    suspend fun resolveNotification(id: String) = post<Unit>("/v1/families/me/notifications/$id/resolve", null)
    suspend fun markAllNotificationsRead() = post<Unit>("/v1/families/me/notifications/read-all", null)
    suspend fun unreadNotificationCount(): UnreadCountOut = get("/v1/families/me/notifications/unread-count")
    suspend fun activateSos(req: SosActivateReq): SosActivateOut = post("/v1/device/sos",req)
    suspend fun activeDeviceSos(): SosActiveDeviceOut = get("/v1/device/sos/active")
    suspend fun cancelSos(id:String,reason:String): SosSessionOut = post("/v1/device/sos/$id/cancel",SosCancelReq(reason))
    suspend fun updateSosTracking(id:String,req:SosTrackingReq): SosTrackingOut = post("/v1/device/sos/$id/tracking",req)
    suspend fun uploadSosLocations(id:String,points:List<SosLocationIn>): Map<String,Any> = post("/v1/device/sos/$id/locations",SosLocationBatchReq(points))
    suspend fun reportSosNetwork(id:String,req:SosNetworkStateReq): Map<String,Any> = post("/v1/device/sos/$id/network",req)
    suspend fun sosPreferences(): SosPreferencesOut = get("/v1/families/me/sos/preferences")
    suspend fun saveSosPreferences(hours:Int): SosPreferencesOut = put("/v1/families/me/sos/preferences",SosPreferencesReq(hours))
    suspend fun activeSos(): List<SosSessionOut> = get("/v1/families/me/sos/active",Array<SosSessionOut>::class.java).toList()
    suspend fun sosHistory(deviceId:String,limit:Int=50): List<SosSessionOut> = get("/v1/families/me/devices/$deviceId/sos/history?limit=$limit",Array<SosSessionOut>::class.java).toList()
    suspend fun sosDetail(id:String): SosSessionOut = get("/v1/families/me/sos/$id")
    suspend fun sosLocations(id:String,limit:Int=100): List<SosLocationOut> = get("/v1/families/me/sos/$id/location?limit=$limit",Array<SosLocationOut>::class.java).toList()
    suspend fun sosEvents(id:String): List<SosEventOut> = get("/v1/families/me/sos/$id/events",Array<SosEventOut>::class.java).toList()
    suspend fun sosNotes(id:String): List<SosNoteOut> = get("/v1/families/me/sos/$id/notes",Array<SosNoteOut>::class.java).toList()
    suspend fun addSosNote(id:String,note:String): SosNoteOut = post("/v1/families/me/sos/$id/notes",SosNotesReq(note))
    suspend fun acknowledgeSos(id:String): SosSessionOut = post("/v1/families/me/sos/$id/acknowledge",emptyMap<String,String>())
    suspend fun resolveSos(id:String,note:String?): SosSessionOut = post("/v1/families/me/sos/$id/resolve",SosResolveReq(note))

    suspend fun notificationPreferences(): NotificationPreferencesOut = get("/v1/families/me/notifications/preferences")
    suspend fun saveNotificationPreferences(p: NotificationPreferencesOut): NotificationPreferencesOut = put("/v1/families/me/notifications/preferences", p)

    suspend fun getSettings(): SettingsOut = get("/v1/families/me/settings")

    suspend fun putSettings(req: SettingsUpdateReq): SettingsOut = put("/v1/families/me/settings", req)

    // ---------- Phase 3: Screen Time & App Usage ----------
    suspend fun uploadUsage(req: UsageUploadReq): Map<String, Any> = post("/v1/device/usage/upload", req)

    suspend fun usageToday(id: String): UsageTodayOut = get("/v1/families/me/devices/$id/usage/today")

    suspend fun usageWeekly(id: String): UsageWeeklyOut = get("/v1/families/me/devices/$id/usage/weekly")

    suspend fun usageApps(id: String, date: String? = null): List<AppUsageOut> =
        get(
            "/v1/families/me/devices/$id/usage/apps" + (date?.let { "?date=$it" } ?: ""),
            Array<AppUsageOut>::class.java,
        ).toList()

    suspend fun appDetail(id: String, pkg: String): AppDetailOut =
        get("/v1/families/me/devices/$id/usage/apps/$pkg/detail")

    suspend fun dailyReport(id: String, date: String? = null): DailyReportOut =
        get("/v1/families/me/devices/$id/usage/report/daily" + (date?.let { "?date=$it" } ?: ""))

    suspend fun screenTimeReport(id: String): ScreenTimeReportOut =
        get("/v1/families/me/devices/$id/screen-time/report")

    suspend fun screenTimeSettings(id: String): ScreenTimeSettingsOut =
        get("/v1/families/me/devices/$id/screen-time/settings")

    suspend fun saveScreenTimeSettings(id: String, req: ScreenTimeSettingsReq): ScreenTimeSettingsOut =
        post("/v1/families/me/devices/$id/screen-time/settings", req)

    suspend fun schedules(id: String): List<ScheduleOut> =
        get("/v1/families/me/devices/$id/screen-time/schedules", Array<ScheduleOut>::class.java).toList()

    suspend fun createSchedule(id: String, req: ScheduleReq): ScheduleOut =
        post("/v1/families/me/devices/$id/screen-time/schedules", req)

    suspend fun updateSchedule(id: String, scheduleId: String, req: ScheduleReq): ScheduleOut =
        put("/v1/families/me/devices/$id/screen-time/schedules/$scheduleId", req)

    // ---------- Phase 4: Location ----------
    suspend fun locationSync(req: LocationSyncIn): Map<String, Any> = post("/v1/device/location/sync", req)

    suspend fun locationCurrent(id: String): LocationCurrentOut = get("/v1/families/me/devices/$id/location/current")

    suspend fun locationHistory(id: String, from: String? = null, to: String? = null, limit: Int = 500): LocationHistoryOut {
        val q = buildList {
            from?.let { add("from=$it") }
            to?.let { add("to=$it") }
            add("limit=$limit")
        }.joinToString("&")
        return get("/v1/families/me/devices/$id/location/history?$q")
    }

    suspend fun locationSettings(id: String): LocationSettingsOut = get("/v1/families/me/devices/$id/location/settings")

    suspend fun saveLocationSettings(id: String, req: LocationSettingsReq): LocationSettingsOut =
        post("/v1/families/me/devices/$id/location/settings", req)

    suspend fun deleteLocationHistory(id: String, scope: String): LocationDeleteOut =
        delete("/v1/families/me/devices/$id/location/history?scope=$scope")

    // ---------- Phase 5: Safe Zones / Geofence ----------
    suspend fun geofences(id: String): GeofenceListOut = get("/v1/families/me/devices/$id/geofences")
    suspend fun createGeofence(id: String, req: GeofenceReq): GeofenceOut = post("/v1/families/me/devices/$id/geofences", req)
    suspend fun updateGeofence(id: String, zoneId: String, req: GeofenceReq): GeofenceOut = put("/v1/families/me/devices/$id/geofences/$zoneId", req)
    suspend fun deleteGeofence(id: String, zoneId: String): Map<String, Any> = delete("/v1/families/me/devices/$id/geofences/$zoneId")
    suspend fun geofenceEvents(id: String, zoneId: String, limit: Int = 100): GeofenceEventsOut =
        get("/v1/families/me/devices/$id/geofences/$zoneId/events?limit=$limit")
    suspend fun geofenceHistory(id: String, from: String? = null, to: String? = null, limit: Int = 500): GeofenceEventsOut {
        val q = buildList { from?.let { add("from=$it") }; to?.let { add("to=$it") }; add("limit=$limit") }.joinToString("&")
        return get("/v1/families/me/devices/$id/geofence-events?$q")
    }
    suspend fun childGeofences(): ChildGeofencesOut = get("/v1/device/geofences")
    suspend fun syncGeofences(): ChildGeofencesOut = post("/v1/device/geofences/sync", emptyMap<String, String>())
    suspend fun postGeofenceEvent(req: GeofenceEventPost): GeofenceEventAck = post("/v1/device/geofences/events", req)

    // ---------- Phase 6: App inventory / per-app rules ----------
    suspend fun appCatalog(id: String, includeUninstalled: Boolean = false): AppCatalogListOut =
        get("/v1/families/me/devices/$id/apps?includeUninstalled=$includeUninstalled")
    suspend fun appUsageHistory(id: String, pkg: String, days: Int = 7): AppUsageHistoryOut =
        get("/v1/families/me/devices/$id/apps/$pkg/usage?days=$days")
    suspend fun appRules(id: String): AppRulesOut = get("/v1/families/me/devices/$id/app-rules")
    suspend fun createAppRule(id: String, req: AppRuleReq): AppRuleOut = post("/v1/families/me/devices/$id/app-rules", req)
    suspend fun updateAppRule(id: String, ruleId: String, req: AppRuleReq): AppRuleOut = put("/v1/families/me/devices/$id/app-rules/$ruleId", req)
    suspend fun deleteAppRule(id: String, ruleId: String, expectedVersion: Long): Map<String, Any> =
        delete("/v1/families/me/devices/$id/app-rules/$ruleId?expectedVersion=$expectedVersion")
    suspend fun appRestrictionEvents(id: String, limit: Int = 100): AppRestrictionEventsOut =
        get("/v1/families/me/devices/$id/app-events?limit=$limit")

    // ---------- Phase 7: transparent Web Safety ----------
    suspend fun webSafety(id: String): WebSafetySettingsOut = get("/v1/families/me/devices/$id/web-safety")
    suspend fun webSafetyEvents(id: String, limit: Int = 100): WebSafetyEventsOut =
        get("/v1/families/me/devices/$id/web-safety/events?limit=$limit")
    suspend fun updateWebSafety(id: String, req: WebSafetySettingsReq): WebSafetySettingsOut =
        put("/v1/families/me/devices/$id/web-safety", req)
    suspend fun createWebRule(id: String, req: WebSafetyRuleReq): WebSafetyRuleOut =
        post("/v1/families/me/devices/$id/web-safety/rules", req)
    suspend fun updateWebRule(id: String, ruleId: String, req: WebSafetyRuleReq): WebSafetyRuleOut =
        put("/v1/families/me/devices/$id/web-safety/rules/$ruleId", req)
    suspend fun deleteWebRule(id: String, ruleId: String, version: Long): Map<String, Any> =
        delete("/v1/families/me/devices/$id/web-safety/rules/$ruleId?expectedVersion=$version")
    suspend fun createWebOverride(id: String, req: WebSafetyOverrideReq): WebSafetyOverrideOut =
        post("/v1/families/me/devices/$id/web-safety/overrides", req)
    suspend fun revokeWebOverride(id: String, overrideId: String): Map<String, Any> =
        delete("/v1/families/me/devices/$id/web-safety/overrides/$overrideId")

    /** بایت‌های PNG آیکون اپ؛ اگر موجود نبود null (404). */
    suspend fun iconBytes(id: String, pkg: String): ByteArray? {
        val req = Request.Builder()
            .url("$baseUrl/v1/families/me/devices/$id/usage/apps/$pkg/icon")
            .get()
            .build()
        return suspendCancellableCoroutine { cont ->
            client.newCall(req).enqueue(object : okhttp3.Callback {
                override fun onFailure(call: okhttp3.Call, e: java.io.IOException) {
                    cont.resume(null)
                }

                override fun onResponse(call: okhttp3.Call, resp: Response) {
                    resp.use {
                        if (!it.isSuccessful) {
                            cont.resume(null)
                        } else {
                            cont.resume(runCatching { it.body?.bytes() }.getOrNull())
                        }
                    }
                }
            })
        }
    }

    init {
        AppLog.info(Cat.NETWORK, "api client ready", "base" to baseUrl)
    }

    companion object {
        fun logToken(t: String?) = Redact.token(t)
    }
}

/** Service locator ساده (بدون DI framework در Phase 1). */
object GlServices {
    lateinit var secure: SecureStore
    lateinit var prefs: ir.guardianlink.core.storage.PlainPrefs
    lateinit var creds: CredentialStore
    lateinit var api: ApiClient

    fun init(context: Context, baseUrl: String) {
        secure = SecureStore(context.applicationContext)
        prefs = ir.guardianlink.core.storage.PlainPrefs(context.applicationContext)
        creds = SecureCredentialStore(secure)
        api = ApiClient(baseUrl, creds)
    }
}
