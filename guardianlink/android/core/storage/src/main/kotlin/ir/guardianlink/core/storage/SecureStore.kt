package ir.guardianlink.core.storage

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import ir.guardianlink.core.common.AppLog
import ir.guardianlink.core.common.Cat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

private val Context.secureData by preferencesDataStore(name = "gl_secure")

/**
 * ذخیره‌سازی امن: متن با AES-256-GCM و کلید داخل Android Keystore رمز می‌شود،
 * سپس ciphertext در DataStore نگهداری می‌شود. هیچ مقدار خامی در SharedPreferences/Log نیست.
 */
class SecureStore(private val context: Context) {
    private val ks by lazy {
        KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
    }

    private fun key(): SecretKey {
        val alias = "gl_master_key"
        (ks.getEntry(alias, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec.Builder(
                alias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return gen.generateKey()
    }

    private fun encrypt(plain: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val out = cipher.iv + cipher.doFinal(plain.toByteArray())
        return Base64.encodeToString(out, Base64.NO_WRAP)
    }

    private fun decrypt(enc: String): String? =
        try {
            val raw = Base64.decode(enc, Base64.NO_WRAP)
            val iv = raw.copyOfRange(0, 12)
            val data = raw.copyOfRange(12, raw.size)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv))
            String(cipher.doFinal(data))
        } catch (e: Exception) {
            AppLog.warn(Cat.SECURITY, "secure store decrypt failed", "err" to e.javaClass.simpleName)
            null
        }

    suspend fun put(key: String, value: String) {
        val k = stringPreferencesKey(key)
        val enc = encrypt(value)
        context.secureData.edit { it[k] = enc }
    }

    suspend fun get(key: String): String? {
        val k = stringPreferencesKey(key)
        val enc = context.secureData.data.first()[k] ?: return null
        return decrypt(enc)
    }

    suspend fun remove(key: String) {
        val k = stringPreferencesKey(key)
        context.secureData.edit { it.remove(k) }
    }

    // نسخه‌های blocking برای threadهای OkHttp (Authenticator)
    fun putBlocking(key: String, value: String) = runBlocking { put(key, value) }
    fun getBlocking(key: String): String? = runBlocking { get(key) }
    fun removeBlocking(key: String) = runBlocking { remove(key) }
}
