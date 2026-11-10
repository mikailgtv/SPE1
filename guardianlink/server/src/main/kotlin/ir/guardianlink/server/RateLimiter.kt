package ir.guardianlink.server

/**
 * Process-local sliding-window limiter for one server instance.
 * Keys are bounded and expired buckets are pruned so attacker-controlled identifiers
 * cannot grow the map indefinitely. Multi-replica deployments must replace this with
 * a shared Redis/database limiter before production.
 */
class RateLimiter {
    private val hits = HashMap<String, ArrayDeque<Long>>()
    private var lastPruneMs = 0L
    private val maxKeys = 50_000

    @Synchronized
    fun allow(key: String, limit: Int, windowSec: Long): Boolean {
        val now = System.currentTimeMillis()
        if (now - lastPruneMs >= 60_000 || hits.size >= maxKeys) {
            val cutoff = now - 60 * 60 * 1000L
            val iterator = hits.entries.iterator()
            while (iterator.hasNext()) {
                val queue = iterator.next().value
                while (queue.isNotEmpty() && queue.first() < cutoff) queue.removeFirst()
                if (queue.isEmpty()) iterator.remove()
            }
            lastPruneMs = now
        }
        val queue = hits[key] ?: run {
            if (hits.size >= maxKeys) return false
            ArrayDeque<Long>().also { hits[key] = it }
        }
        val cutoff = now - windowSec.coerceAtLeast(1) * 1000
        while (queue.isNotEmpty() && queue.first() <= cutoff) queue.removeFirst()
        if (queue.size >= limit) return false
        queue.addLast(now)
        return true
    }
}
