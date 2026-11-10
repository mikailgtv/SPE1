package ir.guardianlink.server

import io.ktor.server.config.ApplicationConfig

data class AppConfig(
    val dbUrl: String,
    val dbUser: String,
    val dbPass: String,
    val dataDir: String,
    val dbPoolMaxSize: Int = 10,
    val dbPoolMinIdle: Int = 2,
    val dbConnectionTimeoutMs: Long = 5000,
    val dbValidationTimeoutMs: Long = 3000,
    val dbIdleTimeoutMs: Long = 600000,
    val dbMaxLifetimeMs: Long = 1800000,
    val appEnvironment: String = "development",
    val logLevel: String = "INFO",
    val accessTokenTtlSec: Long,
    val refreshTokenTtlSec: Long,
    val pairingTtlSec: Long,
    val exchangeTtlSec: Long,
    val presenceLeaseSec: Long,
    val defaultBatteryLowPct: Int,
    val defaultBatteryCriticalPct: Int,
    val defaultSyncDelayedSec: Int,
    val defaultOfflineSec: Int,
    val defaultHeartbeatIntervalSec: Int,
    val defaultLocationStaleSec: Int,
    // Phase 5 — Geofence (موتور سمت سرور؛ همه configurable)
    val geofenceCooldownSec: Int,
    val geofenceMinAccuracyM: Int,
    val geofenceMaxPerDevice: Int,
    val geofenceMinRadiusM: Int,
    val geofenceMaxRadiusM: Int,
    val geofenceEventRetentionDays: Int,
) {
    init {
        require(appEnvironment in setOf("development", "staging", "production")) { "APP_ENV must be development, staging, or production" }
        require(logLevel in setOf("TRACE", "DEBUG", "INFO", "WARN", "ERROR")) { "LOG_LEVEL is invalid" }
        require(appEnvironment == "development" || logLevel != "DEBUG") { "DEBUG logging is disabled outside development" }
        require(appEnvironment == "development" || dbUrl.contains("sslmode=verify-full")) { "Staging/production DB_URL must use sslmode=verify-full" }
        require(dbPoolMaxSize in 1..100) { "DB_POOL_MAX must be between 1 and 100" }
        require(dbPoolMinIdle in 0..dbPoolMaxSize) { "DB_POOL_MIN must be between 0 and DB_POOL_MAX" }
        require(dbConnectionTimeoutMs >= 250) { "DB_CONNECTION_TIMEOUT_MS must be at least 250ms" }
        require(dbValidationTimeoutMs >= 250 && dbValidationTimeoutMs <= dbConnectionTimeoutMs) { "DB_VALIDATION_TIMEOUT_MS must be between 250ms and DB_CONNECTION_TIMEOUT_MS" }
        require(dbIdleTimeoutMs >= 10000) { "DB_IDLE_TIMEOUT_MS must be at least 10000ms" }
        require(dbMaxLifetimeMs >= 30000) { "DB_MAX_LIFETIME_MS must be at least 30000ms" }
    }

    companion object {
        fun from(c: ApplicationConfig): AppConfig = AppConfig(
            dbUrl = c.property("app.dbUrl").getString(),
            dbUser = c.property("app.dbUser").getString(),
            dbPass = c.property("app.dbPass").getString(),
            dataDir = c.property("app.dataDir").getString(),
            dbPoolMaxSize = c.propertyOrNull("app.dbPoolMaxSize")?.getString()?.toInt() ?: 10,
            dbPoolMinIdle = c.propertyOrNull("app.dbPoolMinIdle")?.getString()?.toInt() ?: 2,
            dbConnectionTimeoutMs = c.propertyOrNull("app.dbConnectionTimeoutMs")?.getString()?.toLongOrNull() ?: 5000,
            dbValidationTimeoutMs = c.propertyOrNull("app.dbValidationTimeoutMs")?.getString()?.toLongOrNull() ?: 3000,
            dbIdleTimeoutMs = c.propertyOrNull("app.dbIdleTimeoutMs")?.getString()?.toLongOrNull() ?: 600000,
            dbMaxLifetimeMs = c.propertyOrNull("app.dbMaxLifetimeMs")?.getString()?.toLongOrNull() ?: 1800000,
            appEnvironment = c.propertyOrNull("app.environment")?.getString() ?: "development",
            logLevel = c.propertyOrNull("app.logLevel")?.getString()?.uppercase() ?: "INFO",
            accessTokenTtlSec = c.property("app.accessTokenTtlSec").getString().toLong(),
            refreshTokenTtlSec = c.property("app.refreshTokenTtlSec").getString().toLong(),
            pairingTtlSec = c.property("app.pairingTtlSec").getString().toLong(),
            exchangeTtlSec = c.property("app.exchangeTtlSec").getString().toLong(),
            presenceLeaseSec = c.property("app.presenceLeaseSec").getString().toLong(),
            defaultBatteryLowPct = c.property("app.defaultBatteryLowPct").getString().toInt(),
            defaultBatteryCriticalPct = c.property("app.defaultBatteryCriticalPct").getString().toInt(),
            defaultSyncDelayedSec = c.property("app.defaultSyncDelayedSec").getString().toInt(),
            defaultOfflineSec = c.property("app.defaultOfflineSec").getString().toInt(),
            defaultHeartbeatIntervalSec = c.property("app.defaultHeartbeatIntervalSec").getString().toInt(),
            defaultLocationStaleSec = c.property("app.defaultLocationStaleSec").getString().toInt(),
            geofenceCooldownSec = c.property("app.geofenceCooldownSec").getString().toInt(),
            geofenceMinAccuracyM = c.property("app.geofenceMinAccuracyM").getString().toInt(),
            geofenceMaxPerDevice = c.property("app.geofenceMaxPerDevice").getString().toInt(),
            geofenceMinRadiusM = c.property("app.geofenceMinRadiusM").getString().toInt(),
            geofenceMaxRadiusM = c.property("app.geofenceMaxRadiusM").getString().toInt(),
            geofenceEventRetentionDays = c.property("app.geofenceEventRetentionDays").getString().toInt(),
        )
    }
}
