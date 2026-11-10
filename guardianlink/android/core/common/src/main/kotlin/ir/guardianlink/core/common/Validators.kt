package ir.guardianlink.core.common

object Validators {
    private val EMAIL_RX = Regex("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")
    private const val CROCKFORD = "0123456789ABCDEFGHJKMNPQRSTVWXYZ"

    fun isValidEmail(e: String): Boolean = EMAIL_RX.matches(e.trim())

    fun isValidPassword(p: String): Boolean = p.length in 12..128

    fun isValidFamilyName(n: String): Boolean = n.trim().length in 1..60

    fun isValidDeviceName(n: String): Boolean = n.trim().length in 1..60

    /** نرمال‌سازی کد pairing: حذف فاصله/خط تیره، تبدیل به حروف بزرگ Crockford. */
    fun normalizePairingCode(raw: String): String =
        raw.trim().uppercase().filter { it != ' ' && it != '-' }

    fun isValidPairingCode(raw: String): Boolean {
        val c = normalizePairingCode(raw)
        return c.length == 8 && c.all { it in CROCKFORD }
    }
}
