package ir.guardianlink.feature.childstatus

import ir.guardianlink.core.network.WebSafetyScheduleOut
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/** Pure schedule resolver shared by the local policy engine and Android unit tests. */
internal object WebSafetyScheduleMatcher {
    /** Null means no schedule is active and the base rule action should be used. */
    fun activeAction(schedules: List<WebSafetyScheduleOut>, now: ZonedDateTime): String? {
        val matches = schedules.filter { it.enabled }.filter { schedule ->
            val zone = schedule.timezoneId?.let { runCatching { ZoneId.of(it) }.getOrNull() } ?: now.zone
            val local = now.withZoneSameInstant(zone)
            val start = runCatching { LocalTime.parse(schedule.startTime) }.getOrNull() ?: return@filter false
            val end = runCatching { LocalTime.parse(schedule.endTime) }.getOrNull() ?: return@filter false
            if (start < end) {
                local.dayOfWeek.value == schedule.dayOfWeek && !local.toLocalTime().isBefore(start) && local.toLocalTime().isBefore(end)
            } else {
                (local.dayOfWeek.value == schedule.dayOfWeek && !local.toLocalTime().isBefore(start)) ||
                    (local.dayOfWeek.value == (if (schedule.dayOfWeek == 7) 1 else schedule.dayOfWeek + 1) && local.toLocalTime().isBefore(end))
            }
        }
        return matches.lastOrNull()?.action
    }
}
