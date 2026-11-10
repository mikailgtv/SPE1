# گزارش Export و حذف داده — فاز ۲۰

**وضعیت:** `PARTIAL` برای قابلیت‌های موجود؛ Export family/account و حذف account/family `NOT IMPLEMENTED`. آزمون‌های واقعی DB/API `BLOCKED BY ENVIRONMENT`.

## قابلیت‌های موجود از source

| عملیات | مسیر/Scope | اثر مشاهده‌شده در کد | آزمون فاز ۲۰ |
|---|---|---|---|
| location history delete | `DELETE /v1/families/me/devices/{id}/location/history?scope=today|last7|all` | Parent auth + device ownership؛ حذف locationهای همان device بر اساس scope | `NOT TESTED`؛ DB/Gradle/staging موجود نیست |
| remove device | `DELETE /v1/families/me/devices/{id}` | Parent family ownership؛ status به removed، secret null و sessions revoke؛ دادهٔ تاریخی حذف نمی‌شود | `NOT TESTED` در این phase |
| delete app rule/web rule/geofence | per-device routeها | مالکیت device family-check در route/service؛ cascade محدود به child tables | `NOT TESTED` |
| notification read/ack/resolve, SOS ack/resolve/cancel | family/device-scoped routeها | تغییر workflow/read state است، نه حذف رکورد | `NOT TESTED` |
| account delete / family delete | endpoint یافت نشد | پیاده‌سازی نشده؛ DB hard cascade صرفاً schema behavior بالقوه است | `BLOCKED BY ENVIRONMENT` برای DB proof; route absent در source audit |
| family-wide JSON/CSV export | endpoint یافت نشد | پیاده‌سازی نشده؛ هیچ job، signed link یا expiration store ندارد | route absent؛ black-box auth tests نیز `NOT TESTED` |

## Delete dependency graph از FKهای migration

```text
families
 ├─ family_members ── users
 ├─ devices ── device_sessions, status, events, usage, icons, location,
 │              geofence, app-management, web-safety, notifications, SOS
 ├─ pairing_sessions (family/parent cascade; device_id itself has NO FK)
 ├─ family_settings / notification_preferences / sos_preferences
 ├─ family_notifications ── notification_user_state
 ├─ sos_sessions ── sos_location_points, sos_events, sos_notes
 └─ sos_events/notes also carry family/device/user references

users
 ├─ user_sessions
 ├─ family_members
 ├─ notification_user_state
 └─ SOS actor/author references: SET NULL for actor fields; sos_notes author FK CASCADE
```

### نکتهٔ orphan/حذف

- بسیاری از device-scoped tables دارای `ON DELETE CASCADE` به devices هستند؛ `families` نیز به بسیاری از ردیف‌ها cascade دارد. اما هیچ query روی DB واقعی برای orphanها انجام نشد.
- `pairing_sessions.device_id` در V1 صرفاً UUID nullable و فاقد FK است؛ hard delete device می‌تواند pairing row دارای reference منطقی stale باقی بگذارد، مگر family/user cascade یا cleanup جدا آن را حذف کند.
- `users` از `family_members` cascade می‌گیرد؛ حذف hard user به‌تنهایی الزاماً `families` را حذف نمی‌کند (family parent column/reference مستقلی در schema پایه دیده نشد) و می‌تواند خانوادهٔ بدون عضو باقی بگذارد.
- Device API به‌جای hard delete از soft removal استفاده می‌کند؛ تاریخچهٔ location، usage، notifications، SOS و سایر data باقى می‌ماند. بنابراین «remove device» برابر «delete data» نیست.
- `family_notifications`، `sos_events` و `sos_notes` چند FK هم‌زمان به family/device/session/user دارند. ترتیب cascadeها را فقط با PostgreSQL integration test و دادهٔ fixture تأیید می‌توان کرد.
- audit log مستقل وجود ندارد. اگر audit immutable/lawful retention لازم شود، قبل از hard deletion باید آن را pseudonymize/retain جداگانه و با policy مصوب طراحی کرد؛ فعلاً فرض نکنید hard delete تمام logهای بیرونی را پاک می‌کند.

## طراحی لازم برای Export (هنوز پیاده‌سازی نشده)

Export آینده باید به Parent احراز‌شده، Family ID از principal سمت server (نه query parameter قابل‌دستکاری)، authorization روی هر dataset، JSON manifest/version/timestamps و CSV برای جدول‌های مناسب محدود باشد. Export کوچک synchronous با سقف حجم یا job asynchronous؛ artifact encrypted/private، one-time random token hash، TTL کوتاه، download audit بدون URL/token/data، expiry enforcement و delete-on-expiry لازم‌اند. Child/device token همیشه رد شود. هیچ signed link یا job store در source فعلی نیست.

## طراحی لازم برای حذف

- Account delete: re-authentication/confirmation، transactionally revoke user sessions, remove memberships, determine family ownership transfer versus family deletion, clean orphan pairing rows, explicit decision on residual event/audit/backup copies.
- Family delete: owner-only flow; staged confirmation; delete all family/device-derived records transactionally or safe asynchronous state machine; family-boundary invariant and FK checks; audit event sanitized, not raw deleted payload.
- Device remove: retain current semantics but clearly call it disconnect/deauthorize, not erase; optionally separate consentful per-device erasure only after dependency test.
- Location history erase: existing per-device route; define backup deletion/expiry semantics and log just scope/count, no coordinates.
- SOS history: retention/deletion requires product/safety/privacy owner decision; do not delete on “resolved” silently.

## آزمون‌های درخواستی/واقعی

| Test ID | محیط/فرمان | مورد انتظار | نتیجه واقعی |
|---|---|---|---|
| EXPORT-A-001 | Parent A→Family A export; API absent | allowed only after implemented | `NOT TESTED` (capability absent) |
| EXPORT-IDOR-001 | Parent A→Family B export | 403/404; no data | `NOT TESTED` (API absent; integration DB unavailable) |
| EXPORT-CHILD-001 | Child token→export | deny | `NOT TESTED` (API absent) |
| EXPORT-EXP-001 | expired download token | deny | `NOT TESTED` (no export links) |
| DELETE-DEVICE-001 | Parent removes own device | sessions revoked; no unauthorized cross-family effect | source path reviewed; runtime `NOT TESTED` |
| DELETE-IDOR-001 | Parent A deletes Family B device | deny/404/no change | `NOT TESTED` (DB/runtime unavailable) |
| DELETE-LOCATION-001 | Parent deletes own device history | requested scope rows removed | source path reviewed; actual DB `NOT TESTED` |
| DELETE-CASCADE-001 | delete family/device fixture then check all dependent tables/no orphans | no orphan data | `BLOCKED BY ENVIRONMENT` (PostgreSQL/psql absent) |
| DELETE-ROLLBACK-001 | induce mid-delete failure | transaction rollback | `BLOCKED BY ENVIRONMENT`; no deletion workflow |

هر تست مورد نیاز فاز ۲۰ را به‌عنوان pass گزارش نمی‌کنیم؛ هیچ endpoint privacy جدیدی جهت دورزدن نبود DB ایجاد نشده است.
