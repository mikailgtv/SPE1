package ir.guardianlink.server

import java.security.MessageDigest
import java.security.SecureRandom

object Digest {
    fun sha256(s: String): String =
        MessageDigest.getInstance("SHA-256").digest(s.toByteArray())
            .joinToString("") { "%02x".format(it) }

    private const val CROCKFORD = "0123456789ABCDEFGHJKMNPQRSTVWXYZ"

    fun pairingCode(): String {
        val r = SecureRandom()
        return CharArray(8) { CROCKFORD[r.nextInt(CROCKFORD.length)] }.joinToString("")
    }
}
