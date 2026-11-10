package ir.guardianlink.server

import io.ktor.http.HttpStatusCode

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
    // Phase 5 — Geofence
    const val GEO_ZONE_LIMIT = "GEO_ZONE_LIMIT"
    const val GEO_DUPLICATE_ZONE = "GEO_DUPLICATE_ZONE"
    // Phase 6 — app rule validation/version conflicts
    const val APP_RULE_CONFLICT = "APP_RULE_CONFLICT"
    const val APP_RULE_NOT_FOUND = "APP_RULE_NOT_FOUND"
    const val RATE_LIMITED = "RATE_LIMITED"
    const val SERVER_ERROR = "SERVER_ERROR"
}

class ApiError(
    val status: HttpStatusCode,
    val code: String,
    message: String,
) : Exception(message) {
    companion object {
        fun validation(msg: String) = ApiError(HttpStatusCode.BadRequest, ErrCode.VALIDATION, msg)
        fun unauthorized(msg: String = "unauthorized") = ApiError(HttpStatusCode.Unauthorized, ErrCode.UNAUTHORIZED, msg)
        fun sessionExpired() = ApiError(HttpStatusCode.Unauthorized, ErrCode.SESSION_EXPIRED, "session expired")
        fun sessionRevoked() = ApiError(HttpStatusCode.Unauthorized, ErrCode.SESSION_REVOKED, "session revoked")
        fun forbidden(msg: String = "forbidden") = ApiError(HttpStatusCode.Forbidden, ErrCode.FORBIDDEN, msg)
        fun notFound(msg: String = "not found") = ApiError(HttpStatusCode.NotFound, ErrCode.NOT_FOUND, msg)
        fun conflict(code: String, msg: String) = ApiError(HttpStatusCode.Conflict, code, msg)
        fun rateLimited() = ApiError(HttpStatusCode.TooManyRequests, ErrCode.RATE_LIMITED, "rate limited")
        fun removed() = ApiError(HttpStatusCode.Gone, ErrCode.DEVICE_REMOVED, "device removed")
    }
}

data class ErrorBody(val code: String, val message: String)
