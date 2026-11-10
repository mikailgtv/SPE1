package ir.guardianlink.feature.usage

import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import ir.guardianlink.core.network.GlServices

/**
 * حافظه‌ی آیکون اپ‌ها سمت Parent (فقط cache در حافظه؛ هیچ ذخیره‌ی دائمی از
 * داده‌های دستگاه فرزند روی گوشی والد انجام نمی‌شود).
 */
object UsageIcons {
    private val cache = LruCache<String, ImageBitmap?>(200)

    suspend fun load(deviceId: String, pkg: String): ImageBitmap? {
        val key = "$deviceId:$pkg"
        cache.get(key)?.let { return it }
        if (cache.snapshot().containsKey(key)) return null
        val bytes = GlServices.api.iconBytes(deviceId, pkg) ?: return null
        val bmp = runCatching { BitmapFactory.decodeByteArray(bytes, 0, bytes.size) }.getOrNull()
            ?: return null
        val ib = bmp.asImageBitmap()
        cache.put(key, ib)
        return ib
    }

    fun clear() = cache.evictAll()
}
