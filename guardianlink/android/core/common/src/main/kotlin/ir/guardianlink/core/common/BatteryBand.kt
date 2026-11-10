package ir.guardianlink.core.common

/**
 * نگاشت سطح باتری به band با آستانه‌های قابل تنظیم (هرگز hard-code نیست).
 * critical: کمتر از criticalPct · low: تا lowPct · normal: بالاتر.
 */
object BatteryBand {
    const val NORMAL = "normal"
    const val LOW = "low"
    const val CRITICAL = "critical"
    const val UNKNOWN = "unknown"

    fun of(level: Int?, lowPct: Int, criticalPct: Int): String = when {
        level == null -> UNKNOWN
        level < criticalPct -> CRITICAL
        level <= lowPct -> LOW
        else -> NORMAL
    }
}

object HealthState {
    const val HEALTHY = "healthy"
    const val DELAYED = "delayed"
    const val OFFLINE = "offline"
    const val UNKNOWN = "unknown"
}
