package ir.guardianlink.core.common

/**
 * قالب‌بندی خنثی از locale برای زمان مصرف (Phase 3).
 * لایه UI با faNum ارقام را فارسی می‌کند؛ اینجا فقط منطق زمان است.
 */
object TimeFmt {
    /** 154 -> "2:34" ؛ 45 -> "0:45" ؛ 0 -> "0:00" */
    fun hm(minutes: Int): String {
        val m = minutes.coerceAtLeast(0)
        val h = m / 60
        val mm = m % 60
        return h.toString() + ":" + (if (mm < 10) "0" else "") + mm.toString()
    }

    /** 154 -> "2h34m" ؛ 45 -> "45m" ؛ 0 -> "0m" */
    fun compact(minutes: Int): String {
        val m = minutes.coerceAtLeast(0)
        val h = m / 60
        val mm = m % 60
        return if (h > 0) h.toString() + "h" + mm.toString() + "m" else m.toString() + "m"
    }

    /** درصد کل نسبت به جزء (برای نوارها) — از تقسیم بر صفر محافظت می‌کند. */
    fun pct(part: Int, total: Int): Int =
        if (total <= 0) 0 else ((part.toLong() * 100) / total).toInt().coerceIn(0, 100)
}
