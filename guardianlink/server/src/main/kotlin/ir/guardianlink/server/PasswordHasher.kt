package ir.guardianlink.server

import de.mkammerer.argon2.Argon2Factory

/** Argon2id برای hash رمز عبور — هرگز plaintext ذخیره/log نمی‌شود. */
object PasswordHasher {
    private val argon2 = Argon2Factory.create(Argon2Factory.Argon2Types.ARGON2id)
    private val dummyHash by lazy { hash("guardianlink-invalid-account-timing-check") }

    // Argon2id: t=3, m=64 MiB, p=1. Re-measure on production hardware before rollout.
    fun hash(password: String): String {
        val chars=password.toCharArray()
        return try { argon2.hash(3,64*1024,1,chars) } finally { chars.fill('\u0000') }
    }

    fun verify(hash: String, password: String): Boolean {
        val chars=password.toCharArray()
        return try { argon2.verify(hash,chars) } catch (_: Exception) { false } finally { chars.fill('\u0000') }
    }

    /** Expensive dummy verification reduces account-existence timing differences. */
    fun verifyUnknownAccount(password:String) { verify(dummyHash,password) }
}
