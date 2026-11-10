package ir.guardianlink.feature.childstatus

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource

@Composable
internal fun webStatusLabel(status: String): String = stringResource(when (status) {
    "DISABLED" -> R.string.child_web_status_disabled
    "UNSUPPORTED" -> R.string.child_web_status_unsupported
    "CONFIGURATION_APPLIED_UNVERIFIED" -> R.string.child_web_status_unverified
    "LIMITED" -> R.string.child_web_status_limited
    "ERROR" -> R.string.child_web_status_error
    else -> R.string.child_web_status_unknown
})

@Composable
internal fun webReasonLabel(reason: String): String = stringResource(when (reason) {
    "disabled" -> R.string.child_web_reason_disabled
    "managed_device_required" -> R.string.child_web_reason_managed_required
    "chrome_not_installed" -> R.string.child_web_reason_chrome_missing
    "policy_readback_mismatch" -> R.string.child_web_reason_readback_mismatch
    "policy_stored_effect_unverified" -> R.string.child_web_reason_effect_unverified
    "category_provider_unavailable" -> R.string.child_web_reason_category_unavailable
    "vpn_not_implemented" -> R.string.child_web_reason_vpn_unavailable
    "dns_provider_not_configured" -> R.string.child_web_reason_dns_unavailable
    "android_policy_rejected" -> R.string.child_web_reason_policy_rejected
    else -> R.string.child_web_reason_default
})
