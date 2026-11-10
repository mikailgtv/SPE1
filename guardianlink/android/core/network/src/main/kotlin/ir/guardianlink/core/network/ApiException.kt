package ir.guardianlink.core.network

import java.io.IOException

object ErrCode {
    const val VALIDATION = "VALIDATION"
    const val EMAIL_EXISTS = "EMAIL_EXISTS"
    const val UNAUTHORIZED = "UNAUTHORIZED"
    const val SESSION_EXPIRED = "SESSION_EXPIRED"
    const val SESSION_REVOKED = "SESSION_REVOKED"
    const val FORBIDDEN = "FORBIDDEN"
    const val NOT_FOUND = "NOT_FOUND"
    const val FAMILY_EXISTS = "FAMILY_EXISTS"
    const val NO_FAMILY = "NO_FAMILY"
    const val PAIRING_INVALID = "PAIRING_INVALID"
    const val PAIRING_EXPIRED = "PAIRING_EXPIRED"
    const val PAIRING_USED = "PAIRING_USED"
    const val DEVICE_ALREADY_CONNECTED = "DEVICE_ALREADY_CONNECTED"
    const val DEVICE_REMOVED = "DEVICE_REMOVED"
    const val RATE_LIMITED = "RATE_LIMITED"
    const val SERVER_ERROR = "SERVER_ERROR"
    const val NETWORK = "NETWORK"
}

class ApiException(
    val code: String,
    val httpStatus: Int,
    message: String,
) : IOException(message)
