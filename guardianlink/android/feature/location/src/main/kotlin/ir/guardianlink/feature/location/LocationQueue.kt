package ir.guardianlink.feature.location

import ir.guardianlink.core.network.GlServices
import ir.guardianlink.core.network.LocationPointIn
import org.json.JSONArray
import org.json.JSONObject

/**
 * صف محلی موقعیت‌ها برای حالت آفلاین:
 * Local Location → Keystore-encrypted queue → Batch → Backend → Parent
 * نکات:
 * - سقف ۲۴۰ نقطه (≈۲۴ ساعت با فاصله‌ی ۱۵ دقیقه) تا حافظه پر نشود؛ قدیمی‌ترین‌ها دور می‌ریزند.
 * - dedupe محلی بر اساس clientTs؛ سمت سرور هم UNIQUE(device_id, client_ts) دارد ⇒
 *   بازفرستانی پس از reconnect هرگز رکورد تکراری نمی‌سازد.
 * - نقاط فقط پس از sync موفق از صف پاک می‌شوند (retry-safe).
 * - اگر والد sharing را خاموش کند، سرور مختصات را ذخیره نمی‌کند؛ صف هم طبق پاسخ پاک می‌شود
 *   (نقاط رد شده بی‌فایده‌اند و حریم خصوصی ایجاب می‌کند نگه نداشته شوند).
 */
object LocationQueue {
    private const val KEY = "loc_queue_v1"
    private const val CAP = 240

    suspend fun add(point: LocationPointIn) {
        val all = read().toMutableList()
        if (all.any { it.clientTs == point.clientTs }) return
        all.add(point)
        while (all.size > CAP) all.removeAt(0)
        write(all)
    }

    suspend fun addAll(points: List<LocationPointIn>) {
        if (points.isEmpty()) return
        val all = read().toMutableList()
        for (p in points) if (all.none { it.clientTs == p.clientTs }) all.add(p)
        while (all.size > CAP) all.removeAt(0)
        write(all)
    }

    suspend fun snapshot(): List<LocationPointIn> = read()

    /** نقاط تأییدشده (ارسال موفق) را از صف حذف می‌کند. */
    suspend fun markSent(points: List<LocationPointIn>) {
        if (points.isEmpty()) return
        val sentTs = points.map { it.clientTs }.toSet()
        write(read().filterNot { it.clientTs in sentTs })
    }

    suspend fun clear() = write(emptyList())

    private suspend fun read(): List<LocationPointIn> {
        val encrypted = GlServices.secure.get(KEY)
        if (encrypted != null) {
            if (GlServices.prefs.getString(KEY) != null) GlServices.prefs.putString(KEY, null)
            return decode(encrypted) ?: emptyList()
        }
        // One-time transition from the prior plaintext queue: encrypt before deleting legacy data.
        val legacy = GlServices.prefs.getString(KEY) ?: return emptyList()
        val migrated = decode(legacy)
        if (migrated != null) GlServices.secure.put(KEY, legacy)
        GlServices.prefs.putString(KEY, null)
        return migrated ?: emptyList()
    }

    private fun decode(raw: String): List<LocationPointIn>? = try {
        val arr = JSONArray(raw)
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            LocationPointIn(
                latitude = o.getDouble("lat"),
                longitude = o.getDouble("lon"),
                accuracyM = if (o.isNull("acc")) null else o.getDouble("acc").toFloat(),
                clientTs = o.getLong("ts"),
                source = o.optString("src", "fused"),
            )
        }
    } catch (_: Exception) {
        null
    }

    private suspend fun write(points: List<LocationPointIn>) {
        val arr = JSONArray()
        for (p in points) {
            arr.put(
                JSONObject().apply {
                    put("lat", p.latitude)
                    put("lon", p.longitude)
                    val acc = p.accuracyM
                    if (acc != null) put("acc", acc.toDouble()) else put("acc", JSONObject.NULL)
                    put("ts", p.clientTs)
                    put("src", p.source ?: "fused")
                },
            )
        }
        if (points.isEmpty()) {
            GlServices.secure.remove(KEY)
            GlServices.prefs.putString(KEY, null)
        } else {
            GlServices.secure.put(KEY, arr.toString())
            GlServices.prefs.putString(KEY, null)
        }
    }
}
