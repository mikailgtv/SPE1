package ir.guardianlink.feature.childstatus

import com.google.gson.GsonBuilder
import com.google.gson.FieldNamingPolicy
import ir.guardianlink.core.network.AppRestrictionEventIn
import ir.guardianlink.core.network.AppRuleOut
import ir.guardianlink.core.network.AppRulesSyncOut
import ir.guardianlink.core.network.GlServices
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID

/** Versioned offline policy/event cache. Package names and family rules are encrypted with SecureStore. */
internal object AppPolicyCache {
    private const val KEY = "phase6_app_policy_v1"
    private val gson = GsonBuilder().setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES).create()
    private val mutex = Mutex()

    private data class Stored(
        val version: Long = 0,
        val rules: List<AppRuleOut> = emptyList(),
        val events: List<AppRestrictionEventIn> = emptyList(),
        val enforcementStates: Map<String, String> = emptyMap(),
        val inventoryHash: String? = null,
        val inventoryScannedAtMs: Long? = null,
        val retiredPackages: List<String> = emptyList(),
    )

    suspend fun read(): Snapshot = mutex.withLock {
        val data = readStored()
        Snapshot(data.version, data.rules, data.enforcementStates, data.inventoryHash, data.inventoryScannedAtMs, data.retiredPackages)
    }

    suspend fun accept(server: AppRulesSyncOut) = mutex.withLock {
        val current = readStored()
        if (server.ruleVersion < current.version) return@withLock // stale response cannot roll back cached policy
        val livePackages = server.rules.map { it.packageName }.toSet()
        val removed = current.rules.filter { it.enabled && it.packageName !in livePackages }.map { it.packageName }
        write(current.copy(version = server.ruleVersion, rules = server.rules, retiredPackages = (current.retiredPackages + removed).distinct()))
    }

    suspend fun markInventoryScanned(hash: String?, scannedAtMs: Long, updateHash: Boolean) = mutex.withLock {
        val current = readStored()
        write(current.copy(
            inventoryHash = if (updateHash && hash != null) hash else current.inventoryHash,
            inventoryScannedAtMs = scannedAtMs,
        ))
    }

    suspend fun clear() = mutex.withLock { GlServices.secure.remove(KEY) }

    suspend fun pendingEvents(): List<AppRestrictionEventIn> = mutex.withLock { readStored().events }

    suspend fun addEvent(pkg: String, type: String, version: Long, detail: String) = mutex.withLock {
        val current = readStored()
        val event = AppRestrictionEventIn(
            UUID.randomUUID().toString(), pkg, type, version, System.currentTimeMillis(), detail.take(180),
        )
        // Queue is bounded and stores only package/rule outcome fields defined by AppRestrictionEventIn.
        write(current.copy(events = (current.events + event).takeLast(500)))
    }

    suspend fun acknowledge(ids: Set<String>) = mutex.withLock {
        if (ids.isEmpty()) return@withLock
        val current = readStored()
        write(current.copy(events = current.events.filterNot { it.clientEventId in ids }))
    }

    suspend fun clearRetired(pkg: String) = mutex.withLock {
        val current = readStored()
        write(current.copy(retiredPackages = current.retiredPackages.filterNot { it == pkg }))
    }

    suspend fun enforcementState(key: String): String? = mutex.withLock { readStored().enforcementStates[key] }

    suspend fun setEnforcementState(key: String, state: String) = mutex.withLock {
        val current = readStored()
        write(current.copy(enforcementStates = (current.enforcementStates + (key to state)).toList().takeLast(500).toMap()))
    }

    private suspend fun readStored(): Stored {
        val raw = GlServices.secure.get(KEY) ?: return Stored()
        return runCatching { gson.fromJson(raw, Stored::class.java) ?: Stored() }.getOrDefault(Stored())
    }

    private suspend fun write(value: Stored) {
        GlServices.secure.put(KEY, gson.toJson(value))
    }

    data class Snapshot(
        val version: Long,
        val rules: List<AppRuleOut>,
        val enforcementStates: Map<String, String>,
        val inventoryHash: String?,
        val inventoryScannedAtMs: Long?,
        val retiredPackages: List<String>,
    )
}
