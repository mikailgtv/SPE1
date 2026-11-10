package ir.guardianlink.core.storage

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.util.UUID

private val Context.plainData by preferencesDataStore(name = "gl_prefs")

/** تنظیمات غیرحساس (install id، زمان آخرین sync، نام نمایشی). */
class PlainPrefs(private val context: Context) {
    companion object {
        val INSTALL_ID = stringPreferencesKey("install_id")
        val LAST_SYNC_MS = longPreferencesKey("last_sync_ms")
        val FAMILY_NAME = stringPreferencesKey("family_name")
        val DEVICE_NAME = stringPreferencesKey("device_name")
        val DISCONNECTED = stringPreferencesKey("disconnected_reason")
    }

    suspend fun installId(): String {
        val cur = context.plainData.data.first()[INSTALL_ID]
        if (cur != null) return cur
        val id = UUID.randomUUID().toString()
        context.plainData.edit { it[INSTALL_ID] = id }
        return id
    }

    fun installIdBlocking(): String = runBlocking { installId() }

    suspend fun putString(key: String, value: String?) {
        val k = stringPreferencesKey(key)
        context.plainData.edit { if (value == null) it.remove(k) else it[k] = value }
    }

    suspend fun getString(key: String): String? =
        context.plainData.data.first()[stringPreferencesKey(key)]

    suspend fun putLong(key: String, value: Long?) {
        val k = longPreferencesKey(key)
        context.plainData.edit { if (value == null) it.remove(k) else it[k] = value }
    }

    suspend fun getLong(key: String): Long? =
        context.plainData.data.first()[longPreferencesKey(key)]
}
