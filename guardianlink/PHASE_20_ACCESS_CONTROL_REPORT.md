# گزارش کنترل دسترسی — فاز ۲۰

**وضعیت:** `PARTIAL` از نظر static source; تمام black-box privacy/access tests `NOT TESTED` یا `BLOCKED BY ENVIRONMENT`.

## نقش‌ها و محدودهٔ privilege

| Principal | احراز هویت / منبع مجوز | داده/عملیات مجاز از source | محدودیت/ریسک |
|---|---|---|---|
| Parent | JWT `typ=user`, sid معتبر و session فعال، family از DB زنده | Family خودش؛ endpointهای per-device باید از `ownedDevice`/family match استفاده کنند | Parent roles متعدد، ownership transfer/admin delegation وجود ندارد؛ export/delete family وجود ندارد |
| Child/Device | JWT `typ=device`, device session active؛ device status باید active | device خودش برای heartbeat/status/sync/location/usage/policy/SOS | Black-box cross-device test اجرا نشده |
| Device A | همان device principal/device_id binding | داده/تغییر همان دستگاه، حسب route | cross-device injection test در PostgreSQL staging موجود نیست |
| Backend service | role داخلی AppDeps; DB application role در runtime env | عملیات server-side طبق token principal | least-privilege DB roles/runtime vs migration role در deployment اثبات نشده |
| System Worker | Android WorkManager در Child با credentials device | اجرای sync همان device و scopeهای مربوط | role مستقل Backend worker/Admin در source پیدا نشد؛ user actions نباید به worker مستقل واگذار شوند |
| Admin/support | admin endpoint/role در route source پیدا نشد | هیچ دسترسی تعاملی support/admin اثبات نشده | provisioning/just-in-time audited access process تعریف نشده؛ `/metrics` داخلی به قرارداد deployment وابسته است |
| Monitoring service | metrics endpoint `/metrics` server; Caddy public edge پاسخ 404 می‌دهد | process metrics/DB pool values؛ no user payload/SQL label | `/metrics` خود Ktor route فاقد auth دیده می‌شود؛ server port باید private-only بماند. Direct exposure test `NOT TESTED` |

## شواهد source

- `requireUser`: verify JWT signature/issuer/expiry/type، live session lookup و family lookup.
- `requireDevice`: verify device JWT/session، device row و status active.
- `ownedDevice`: family derived from authenticated user and device family checked; mismatch typically 404.
- Notification principal derives family from Parent principal; optional `device_id` checked against family.
- SOS detail/history/location/event/note/transition calls pass family ID and service performs family/session checks; device routes bind device principal.
- RateLimiter applies to selected auth, pairing, SOS and sensitive writes. Rate-limit behavior under access attempts not tested this phase.
- Caddy blocks public `/metrics` with 404 per config and does not forward path; Backend server itself exposes route without auth per Main.kt. Deployment network isolation not verified.
- No separate Admin/support role, per-export job access control, or signed expiring download link exists.

## Audit log review

- Structured `GlLogger` only emits a strict safelist of metadata keys; `Redact` helpers mask token/email/code; status/request logger includes random request ID/method/route/status/duration/error category and no body/query/SQL parameters.
- Domain events are persisted in `device_events`, `app_restriction_events`, `web_filter_events`, `geofence_events`, `family_notifications`, `sos_events`. These are product/event data and not a uniform immutable audit ledger.
- No `audit_logs` table/model/endpoint discovered. Some security-sensitive operations log (register/login/refresh/logout/pair/remove/config changes), but coverage is not uniform and persistence/retention of stdout is external.
- Missing/uncertain coverage: export/delete (no capability), retention-policy changes, administrative access (admin surface absent), privacy setting changes, and request result per domain. Do not claim complete actor/action/target/timestamp/requestId/result audit trail.

## Required tests

| Test ID | Expected | Actual | Result |
|---|---|---|---|
| ACL-PARENT-FAMILY-001 Parent A→Family B | deny/404, no data | not executed | `BLOCKED BY ENVIRONMENT` (DB integration/staging) |
| ACL-CHILD-DEVICE-001 Child A→Child B | deny | not executed | `BLOCKED BY ENVIRONMENT` |
| ACL-DEVICE-MUTATE-001 Device A mutates Device B | deny | not executed | `BLOCKED BY ENVIRONMENT` |
| ACL-WORKER-USER-001 worker cannot perform user/admin actions | deny/no worker identity | no worker API role observed; dynamic test not run | `NOT TESTED` |
| ACL-ADMIN-AUDIT-001 admin access is audited | actor/action/target/time/request/result | admin route/role absent; no universal audit store | `PARTIAL` source; runtime `NOT TESTED` |
| ACL-EXPORT-001 export family boundary / child deny / expired link deny | all required gates | endpoint/job/link absent | `NOT TESTED` (feature absent) |
| ACL-DELETE-001 correct family only / no cross-boundary effect | deny unauthorized, correct cascade | no account/family delete workflow; DB checks unavailable | `BLOCKED BY ENVIRONMENT` |
| ACL-LOCATION-001 Parent cross-family location denied | deny/404 | source ownership check reviewed; integration test `NOT TESTED` | `NOT TESTED` |
| ACL-SOS-001 Parent cross-family SOS denied | deny/404 | source family-scoped service reviewed; integration test `NOT TESTED` | `NOT TESTED` |

No load-bearing access control was replaced with a UI-only check. No authorization bypass or hidden monitoring added.
