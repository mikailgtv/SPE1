package ir.guardianlink.server

import java.net.IDN
import java.net.URI
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/** Domain-only canonicalization. URLs with credentials, ports, queries or non-root paths are rejected. */
object WebSafetyPolicy {
    private val DOMAIN_LABEL = Regex("^[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?$")
    private val CATEGORIES = setOf("adult", "gambling", "violence", "social", "games", "streaming", "unknown")

    data class HostRule(val host: String, val scope: String, val action: String, val enabled: Boolean = true)
    data class ChromePolicy(val blocklist: List<String>, val allowlist: List<String>)

    data class Schedule(
        val dayOfWeek: Int, val startTime: LocalTime, val endTime: LocalTime,
        val action: String, val timezoneId: String? = null, val enabled: Boolean = true,
    )

    fun normalizeDomain(raw: String, requestedScope: String): Pair<String, String> {
        if (raw.length > 2048 || raw.isBlank()) throw ApiError.validation("invalid domain")
        if (requestedScope !in setOf("EXACT_HOST", "DOMAIN_AND_SUBDOMAINS")) throw ApiError.validation("invalid domain scope")
        var value = raw.trim()
        var scope = requestedScope
        if (value.startsWith("*.")) {
            value = value.removePrefix("*.")
            scope = "DOMAIN_AND_SUBDOMAINS"
        }
        val uri = runCatching {
            if (value.contains("://")) URI(value) else URI("https://$value")
        }.getOrNull() ?: throw ApiError.validation("invalid domain")
        if (uri.scheme?.lowercase() !in setOf("http", "https") || uri.userInfo != null || uri.port != -1 || uri.rawQuery != null || uri.rawFragment != null) {
            throw ApiError.validation("enter an http(s) domain, not credentials, port, query or fragment")
        }
        val path = uri.rawPath.orEmpty()
        if (path.isNotEmpty() && path != "/") throw ApiError.validation("paths are not supported; enter a domain")
        var host = uri.host ?: run {
            val authority = uri.rawAuthority ?: throw ApiError.validation("invalid domain")
            if ('@' in authority || ':' in authority) throw ApiError.validation("ports and credentials are not supported")
            authority
        }
        if (host.endsWith("..")) throw ApiError.validation("invalid domain")
        host = runCatching { IDN.toASCII(host.trimEnd('.'), IDN.USE_STD3_ASCII_RULES).lowercase() }
            .getOrElse { throw ApiError.validation("invalid internationalized domain") }
        if (host.length !in 1..253 || host.endsWith(".local") || host == "localhost" ||
            host.split('.').any { !DOMAIN_LABEL.matches(it) } || !host.contains('.')) {
            throw ApiError.validation("public domain name required")
        }
        val numericHost = host.split('.').all { it.all(Char::isDigit) }
        if (numericHost || host.contains(':')) throw ApiError.validation("IP addresses are not supported as domain rules")
        return host to scope
    }

    fun validateCategory(category: String?) {
        if (category == null || category.lowercase() !in CATEGORIES) throw ApiError.validation("invalid category")
    }

    /** Host-boundary matcher used by tests and schedule/override resolution; no suffix false positives. */
    fun matchesDomain(ruleHost: String, scope: String, candidateHost: String): Boolean {
        val rule = ruleHost.trimEnd('.').lowercase()
        val candidate = candidateHost.trimEnd('.').lowercase()
        return when (scope) {
            "EXACT_HOST" -> candidate == rule
            "DOMAIN_AND_SUBDOMAINS" -> candidate == rule || candidate.endsWith(".$rule")
            else -> false
        }
    }

    fun resolveDomainAction(candidateHost: String, rules: List<HostRule>): String? = rules
        .filter { it.enabled && it.action in setOf("ALLOW", "BLOCK") && matchesDomain(it.host, it.scope, candidateHost) }
        .sortedWith(compareByDescending<HostRule> { it.host.count { ch -> ch == '.' } }
            .thenByDescending { it.scope == "EXACT_HOST" }
            .thenByDescending { it.action == "ALLOW" })
        .firstOrNull()?.action

    /** Chrome's native URL policy matching, not a GuardianLink system-wide filter. */
    fun chromeFilter(host: String, scope: String): String =
        if (scope == "EXACT_HOST") ".${host}" else host

    fun compileChromePolicy(defaultPolicy: String, rules: List<HostRule>): ChromePolicy {
        val active = rules.filter { it.enabled && it.action in setOf("ALLOW", "BLOCK") }
            .filter { it.scope in setOf("EXACT_HOST", "DOMAIN_AND_SUBDOMAINS") }
        val block = linkedSetOf<String>()
        val allow = linkedSetOf<String>()
        if (defaultPolicy == "ALLOW_LISTED_ONLY") block += "*"
        active.forEach { r ->
            val target = if (r.scope == "EXACT_HOST") ".${r.host}" else r.host
            if (defaultPolicy == "ALLOW_LISTED_ONLY") {
                if (r.action == "ALLOW") allow += target
            } else if (r.action == "BLOCK") block += target else allow += target
        }
        // Native Chrome allowlist exceptions override URLBlocklist at equal specificity.
        if (block.size + allow.size > 1000) throw ApiError.validation("Chrome policy supports at most 1000 URL entries")
        return ChromePolicy(block.toList(), allow.toList())
    }

    fun scheduleAction(schedules: List<Schedule>, at: ZonedDateTime): String? {
        val matches = schedules.filter { it.enabled }.filter { schedule ->
            val zone = schedule.timezoneId?.let { runCatching { ZoneId.of(it) }.getOrNull() } ?: at.zone
            val localNow = at.withZoneSameInstant(zone)
            val day = localNow.dayOfWeek.value
            val time = localNow.toLocalTime()
            if (schedule.startTime < schedule.endTime) {
                day == schedule.dayOfWeek && !time.isBefore(schedule.startTime) && time.isBefore(schedule.endTime)
            } else {
                (day == schedule.dayOfWeek && !time.isBefore(schedule.startTime)) ||
                    (day == nextDay(schedule.dayOfWeek) && time.isBefore(schedule.endTime))
            }
        }
        return matches.lastOrNull()?.action
    }

    private fun nextDay(day: Int) = if (day == 7) 1 else day + 1
}
