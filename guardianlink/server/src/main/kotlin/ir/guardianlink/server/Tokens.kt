package ir.guardianlink.server

import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.crypto.Ed25519Signer
import com.nimbusds.jose.crypto.Ed25519Verifier
import com.nimbusds.jose.jwk.Curve
import com.nimbusds.jose.jwk.OctetKeyPair
import com.nimbusds.jose.jwk.gen.OctetKeyPairGenerator
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import java.io.File
import java.security.SecureRandom
import java.util.Base64
import java.util.Date
import java.util.UUID

/**
 * توکن‌ها:
 *  - Access: JWT امضاشده با EdDSA (Ed25519)، TTL کوتاه (پیش‌فرض 15 دقیقه)، شامل sid برای revocation فوری.
 *  - Refresh: opaque تصادفی 256بیتی؛ فقط SHA-256 آن در DB ذخیره می‌شود؛ چرخش (rotation) در هر استفاده.
 *  - Pairing code: Crockford Base32 هشت کاراکتری؛ فقط hash در DB.
 */
class TokenService(private val cfg: AppConfig) {
    private val okp: OctetKeyPair = loadOrGenerate()
    private val signer = Ed25519Signer(okp)
    private val verifier = Ed25519Verifier(okp.toPublicJWK())

    fun access(subject: String, type: String, sid: UUID): String {
        val now = Date()
        val claims = JWTClaimsSet.Builder()
            .issuer(ISSUER)
            .subject(subject)
            .claim("typ", type)
            .claim("sid", sid.toString())
            .issueTime(now)
            .expirationTime(Date(now.time + cfg.accessTokenTtlSec * 1000))
            .build()
        val jwt = SignedJWT(JWSHeader(JWSAlgorithm.EdDSA), claims)
        jwt.sign(signer)
        return jwt.serialize()
    }

    /** در صورت انقضا SESSION_EXPIRED و در صورت نامعتبری UNAUTHORIZED پرتاب می‌کند. */
    fun verifyAccess(token: String): JWTClaimsSet {
        val jwt = try {
            SignedJWT.parse(token)
        } catch (e: Exception) {
            throw ApiError.unauthorized("bad token")
        }
        if (jwt.header.algorithm != JWSAlgorithm.EdDSA) throw ApiError.unauthorized("bad alg")
        if (!jwt.verify(verifier)) throw ApiError.unauthorized("bad signature")
        val claims = jwt.jwtClaimsSet
        if (claims.issuer != ISSUER) throw ApiError.unauthorized("bad issuer")
        val exp = claims.expirationTime ?: throw ApiError.unauthorized("no exp")
        if (exp.before(Date())) throw ApiError.sessionExpired()
        return claims
    }

    fun newOpaque(): String {
        val b = ByteArray(32)
        SecureRandom().nextBytes(b)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(b)
    }

    private fun loadOrGenerate(): OctetKeyPair {
        val dir = File(cfg.dataDir).apply { mkdirs() }
        val f = File(dir, "ed25519.jwk")
        if (f.exists()) return OctetKeyPair.parse(f.readText())
        val kp = OctetKeyPairGenerator(Curve.Ed25519).generate()
        f.writeText(kp.toJSONString())
        return kp
    }

    companion object {
        const val ISSUER = "guardianlink"
        fun sha256(s: String): String = Digest.sha256(s)
        fun pairingCode(): String = Digest.pairingCode()
    }
}
