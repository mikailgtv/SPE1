# گزارش حریم خصوصی و آمادگی فنی — فاز ۲۰

**وضعیت:** `PARTIAL`. Data map و disclosure UI تکمیل شد؛ retention/export/delete/backup/restore کامل نیستند. این ارزیابی فنی است، نه مشاوره حقوقی یا ادعای انطباق.

## Data minimization review

- Location stores precise lat/lon, accuracy, timestamps and source; necessary for current location/geofence/SOS path as implemented, but sensitivity high. Existing per-device 0/latest or 1–365 day setting and delete endpoint reduce history; exact collection cadence/precision on hardware not measured.
- App usage stores package names/app names/category/daily duration/launch counts/time and app icons/inventory. Usage purpose is reports/rules; no calls/contacts/private message/content API discovered. Retention for `app_usage_daily` not found: minimization gap.
- Device manufacturer/model/Android/app versions, battery/network/status/permissions support health/sync; keep latest status where possible. Device events persist without global TTL.
- IP addresses: source uses request-local remote host / trusted forwarded chain for rate limiting; standard request log safelist does not include IP. Persistent IP database column not found. Proxy/access log IP retention is external/`NOT TESTED`.
- Notification metadata is copied from event metadata; contents may be sensitive depending event. No notification TTL found.
- SOS stores emergency workflow, battery/network/device/permission data, coordinates, event metadata and notes; all highly sensitive. `expires_at` transitions status but does not delete rows. Retention decision outstanding.
- Logs: `GlLogger` strict key safelist and request observability avoid raw body/SQL; sanitized IDs used in domain logs. Source review is not complete dynamic redaction proof. stdout destination/retention/remote collector unknown.
- Audit: no standalone audit table. Domain events have different scopes/retention. No raw content should be retained solely for analytics.

## Privacy Center changes in this phase

- Parent Web `/privacy` copy was expanded to document categories, purposes, Child capabilities/permissions, current location/geofence retention caveats, device removal semantics, unsupported data, operational monitoring and unavailable export/account deletion.
- Parent Android now has `PrivacyCenterScreen` reachable from Dashboard, with matching transparent disclosures and no data collection/API calls.
- These screens are static source-backed disclosures, not evidence that backend retention/delete/export controls work. Web build and existing tests passed; Android build/device test `NOT TESTED`.
- Child has no dedicated transparency screen added; Child permissions and data flow are disclosed to Parent only; `Child Transparency` remains `PARTIAL`.
- User request to “add hidden monitoring” was not implemented. It contradicts transparency/minimization requirements; only visible, bounded, redacted operational observability described in Phase 18/20 is retained.

## Transparency claims deliberately avoided

No claim of “100% secure,” “no data stored,” guaranteed deletion from all backups, guaranteed third-party delivery, successful restore, complete account export/delete or legal compliance. Existing push readiness source says server-side push is not configured; no delivery claim made.

## Technical readiness (not legal advice)

| Principle | Assessment |
|---|---|
| Data minimization | `PARTIAL`: limited fields/redaction/per-device settings exist; usage/event/SOS retention gaps remain |
| Purpose limitation | `PARTIAL`: source purposes documented in UI/map; no comprehensive consent/purpose ledger |
| Access control | `PARTIAL`: Parent family and device identity enforced in routes; black-box boundary tests not run; admin/support boundary not defined |
| Retention limitation | `PARTIAL`: several opportunistic rules; no global scheduled cleanup or full policy |
| Deletion | `PARTIAL`: location history and rule/device workflows; no account/family data deletion; backups/log external copies unresolved |
| Export | `NOT READY`: no family export API/job/expiry controls |
| Security | `PARTIAL`: hashes, TLS constraints, Android encrypted store and redacted logs in source; infrastructure/encryption/key custody unverified |
| Transparency | `PARTIAL`: Parent Web/Android Center added; Child UI disclosure not added; actual runtime not tested |
| Auditability | `PARTIAL`: selected structured/domain events; no uniform audit trail and no verified storage retention |

**Jurisdiction-specific requirement:** jurisdiction/controller roles were not specified in task/environment. Any GDPR/other legal assessment, lawful basis, guardian/child rights/consent, emergency retention, data transfer and deletion exception requires qualified legal/privacy review; no legal conclusion is made here.
