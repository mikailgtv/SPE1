package ir.guardianlink.feature.childstatus

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import ir.guardianlink.core.common.AppLog
import ir.guardianlink.core.common.Cat
import ir.guardianlink.core.network.ApiException
import ir.guardianlink.core.network.ErrCode
import ir.guardianlink.core.network.GlServices
import ir.guardianlink.core.network.LocationSyncIn
import ir.guardianlink.core.network.SyncReq

/**
 * sync دسته‌ای وضعیت پایه (بدون Foreground Service و بدون هیچ notification):
 * battery + connection + snapshot مجوزها/نسخه‌ها در یک درخواست ⇒ کمترین مصرف باتری/شبکه.
 * interval بعدی را سرور تعیین می‌کند (configurable) و فقط در صورت تغییر reschedule می‌کنیم.
 */
class StatusSyncWorker(
    private val context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val creds = GlServices.creds.get() ?: return Result.success()
        if (creds.kind != "device") return Result.success()
        return try {
            val battery = DeviceInfo.batteryLevel(context)
            // Phase 3: تجمیع محلی مصرف (امروز+دیروز) در همان درخواست sync — بدون درخواست اضافی
            val usage = runCatching { ir.guardianlink.feature.usage.UsageCollector.collect(context) }.getOrNull()
            // Phase 4: report permission/provider with status first. Do not capture or transmit
            // coordinates until this authenticated response confirms server-side sharing is ON.
            // This is fail-closed when the cached preference is missing/stale or the server omits it.
            val prefs = GlServices.prefs
            val permStatus = ir.guardianlink.feature.location.LocationPermission.permissionStatus(context)
            val providerStatus = ir.guardianlink.feature.location.LocationPermission.providerStatus(context)
            val queuedLocationCount = ir.guardianlink.feature.location.LocationQueue.snapshot().size

            // Phase 6: local rules run from the encrypted versioned cache even before network sync.
            // The network sync reuses this same batch for package metadata, capability and queued events.
            WebSafetyEngine.run(context)
            AppPolicyEngine.run(context)
            val webSnapshot = WebSafetyCache.read()
            val pendingWebEvents = WebSafetyCache.pendingEvents()
            val sentWebEventIds = pendingWebEvents.map { it.clientEventId }.toSet()
            val webSafety = ir.guardianlink.core.network.WebSafetySyncIn(
                reportedMode = webSnapshot.reportedMode,
                reportedStatus = webSnapshot.reportedStatus,
                reportedReason = webSnapshot.reportedReason,
                dnsStatus = webSnapshot.dnsStatus,
                vpnStatus = webSnapshot.vpnStatus,
                timezoneId = java.time.ZoneId.systemDefault().id,
                events = pendingWebEvents,
            )
            val policy = AppPolicyCache.read()
            val nowMs = System.currentTimeMillis()
            val inventoryScanDue = policy.inventoryScannedAtMs == null || nowMs - policy.inventoryScannedAtMs >= 6L * 60 * 60 * 1000
            val inventory = if (inventoryScanDue) runCatching { ir.guardianlink.feature.usage.AppInventoryCollector.collect(context) }.getOrNull() else null
            // Periodic full snapshot (6h) refreshes inventory timestamps; changed hashes also send immediately.
            val inventoryPayload = inventory?.takeIf { inventoryScanDue || it.hash != policy.inventoryHash }
            val pendingAppEvents = AppPolicyCache.pendingEvents().take(100)
            val sentEventIds = pendingAppEvents.map { it.clientEventId }.toSet()
            val devicePolicy = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as? android.app.admin.DevicePolicyManager
            val managementMode = if (runCatching { devicePolicy?.isDeviceOwnerApp(context.packageName) == true }.getOrDefault(false)) "managed" else "normal"
            val appManagement = ir.guardianlink.core.network.AppManagementSyncIn(
                inventoryComplete = inventoryPayload?.let { it.broadQueryDeclared && it.apps.size <= 5000 } ?: false,
                inventory = inventoryPayload?.apps?.take(5000),
                managementMode = managementMode,
                usageAccessGranted = ir.guardianlink.feature.usage.UsageCollector.hasUsageAccess(context),
                timeZoneId = java.time.ZoneId.systemDefault().id,
                events = pendingAppEvents,
            )
            val out = GlServices.api.sync(
                SyncReq(
                    batteryLevel = battery,
                    connectionState = DeviceInfo.connectionState(context),
                    appVersion = DeviceInfo.appVersion(context),
                    androidVersion = DeviceInfo.androidVersion(),
                    model = DeviceInfo.modelName(),
                    manufacturer = DeviceInfo.manufacturer(),
                    permissions = DeviceInfo.permissionsSnapshot(context),
                    queuedCount = queuedLocationCount,
                    usage = usage,
                    location = LocationSyncIn(permStatus, providerStatus, null),
                    appManagement = appManagement,
                    webSafety = webSafety,
                ),
            )
            // Only the server-authoritative response may enable ordinary location capture/upload.
            // Send points separately so coordinates cannot ride the status request before consent state arrives.
            if (out.locationSharing == true) {
                if (permStatus != "denied" && providerStatus == "enabled") {
                    runCatching { ir.guardianlink.feature.location.LocationCollector.captureFix(context) }
                        .getOrNull()
                        ?.let { ir.guardianlink.feature.location.LocationQueue.add(it) }
                }
                val locPoints = ir.guardianlink.feature.location.LocationQueue.snapshot()
                if (locPoints.isNotEmpty()) {
                    GlServices.api.locationSync(LocationSyncIn(permStatus, providerStatus, locPoints))
                    ir.guardianlink.feature.location.LocationQueue.markSent(locPoints)
                }
            } else {
                // false or unknown: fail closed and discard stale points rather than transmit them.
                ir.guardianlink.feature.location.LocationQueue.clear()
            }
            // Phase 9: secure SOS outbox is flushed only after the authenticated device sync succeeds.
            runCatching { SosOutbox.syncPending() }.onFailure { e ->
                AppLog.warn(Cat.NETWORK,"SOS outbox sync deferred","err" to e.javaClass.simpleName)
            }
            ir.guardianlink.feature.usage.UsageCollector.markIconsSent(usage)
            out.appManagement?.let {
                AppPolicyCache.accept(it)
                GlServices.prefs.putLong("app_rule_sync_ms", System.currentTimeMillis())
            }
            AppPolicyCache.acknowledge(sentEventIds)
            out.webSafety?.let { WebSafetyCache.accept(it) }
            WebSafetyCache.acknowledge(sentWebEventIds)
            // Apply just-received Chrome settings promptly; a later sync reports their status.
            WebSafetyEngine.run(context)
            if (inventoryPayload != null) {
                AppPolicyCache.markInventoryScanned(
                    hash = inventoryPayload.hash,
                    scannedAtMs = nowMs,
                    updateHash = inventoryPayload.apps.size <= 5000,
                )
            }
            AppPolicyScheduler.schedule(context)
            AppPolicyScheduler.once(context)
            prefs.putLong("last_sync_ms", System.currentTimeMillis())
            prefs.putString("disconnected_reason", null)
            prefs.putString("last_health", out.health)
            prefs.putString("last_battery", battery?.toString())
            // وضعیت کلید اشتراک موقعیت نزد والد — برای نمایش صادقانه در خانه‌ی کودک
            prefs.putString("loc_sharing", out.locationSharing?.let { if (it) "on" else "off" })
            // interval سروری را اعمال کن (فقط وقتی تغییر کرده)
            val wantMin = (out.nextSyncSec / 60).coerceAtLeast(SyncScheduler.MIN_INTERVAL_MIN)
            val curMin = prefs.getLong("sync_interval_min") ?: SyncScheduler.MIN_INTERVAL_MIN
            if (curMin != wantMin) {
                prefs.putLong("sync_interval_min", wantMin)
                SyncScheduler.schedulePeriodic(context, wantMin)
            }
            AppLog.info(Cat.DEVICE, "sync ok", "health" to out.health, "next_min" to wantMin.toString())
            Result.success()
        } catch (e: ApiException) {
            when (e.code) {
                ErrCode.DEVICE_REMOVED, ErrCode.SESSION_REVOKED -> {
                    AppLog.warn(Cat.SECURITY, "device revoked by server", "code" to e.code)
                    GlServices.creds.clear()
                    AppPolicyCache.clear()
                    WebSafetyCache.clear()
                    AppPolicyScheduler.cancel(context)
                    GlServices.prefs.putString("disconnected_reason", e.code)
                    SyncScheduler.cancel(context)
                    Result.success()
                }
                else -> Result.retry()
            }
        } catch (e: Exception) {
            AppLog.warn(Cat.NETWORK, "sync failed (offline?)", "err" to e.javaClass.simpleName)
            Result.retry()
        }
    }
}
