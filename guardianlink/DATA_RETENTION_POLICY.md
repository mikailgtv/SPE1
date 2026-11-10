# سیاست نگهداری داده — فاز ۲۰

**وضعیت سند:** inventory واقعی source انجام شده؛ این سند ترکیبی از مقادیر فعال در کد و پیشنهادهای نیازمند تصویب است. هر مورد «پیشنهادی / فعال‌نشده» تا زمان implementation، migration و تست، policy enforce‌شده محسوب نمی‌شود. این سند مشاورهٔ حقوقی نیست.

## مقادیر قابل مشاهده در implementation فعلی

| دسته | مقدار موجود | پاک‌سازی و limitation |
|---|---|---|
| Location | default schema 30 روز؛ setting per-device: 0 = فقط آخرین نقطه، یا 1..365 روز | purge opportunistic در sync/read; delete API scope `today`, `last7`, `all`; no global scheduler verified |
| Geofence event | `GEO_EVENT_RETENTION_DAYS`, default 30 | purge در مسیرهای فعال geofence، نه مستقل زمان‌بندی‌شده |
| Uninstalled app inventory | 30 روز پس از uninstalled | purge در sync بعدی |
| App restriction events / rule usage state | 90 روز در چند مسیر AppManagement | opportunistic cleanup، نه scheduled job سراسری |
| Web filter events / expired web overrides | 90 روز | cleanup هنگام اجرای service path |
| Daily app usage, notifications, SOS, sessions, auth/pairing, device events | retention کامل/سراسری در source پیدا نشد | `NOT DEFINED` implementation-wide |
| Application logs | destination/rotation خارج repository | `NOT DEFINED`؛ فعلاً structured stdout |
| Metrics | process-local | reset با process restart؛ external retention ندارد در source |
| Backup | script defaults: daily 14d, weekly 56d, monthly 365d برای local files | schedule و remote object lifecycle provision نشده؛ این‌ها policy عملیاتی فعال نیستند |

## پیشنهادهای نیازمند owner approval

- App usage, notifications, web/app restriction event: هدف 90 روز؛ بعد حذف batched، مگر دلیل محصول/قانونی مستندتر.
- Location: پیش‌فرض 30 روز فعلی را به‌عنوان default کاربرمحور حفظ؛ قابلیت 0/1..365 را توضیح و تست کنیم؛ `sharing disabled` باید queueهای محلی را طبق منبع فعلی پاک کند.
- Geofence events: 30 روز، متناسب با setting فعلی.
- SOS records/notes/locations و Security/Audit events: `NOT DEFINED` تا Product + Privacy/Safety owner دربارهٔ safety, incident response, child data و jurisdiction تصمیم دهند؛ انقضای workflow به‌معنای حذف خودکار نیست.
- Authentication/pairing tombstones: پیشنهاد 30 روز پس از expiry/revocation، پس از بررسی incident need.
- Application logs: هدف 14 روز برای metadata redacted، فقط اگر log destination، access control و lifecycle واقعاً provision شود.
- Backup: daily/weekly/monthly defaults script به‌عنوان پیشنهاد؛ remote lifecycle، versioning و deletion schedule باید مجزا و آزمایش شوند.

## تغییر سیاست و اجرا

فعلاً Parent فقط retention تاریخچهٔ موقعیت همان device را تغییر می‌دهد؛ operator env geofence retention را تغییر می‌دهد؛ policy مرکزی audit/approval ندارد. پیاده‌سازی retention کلی نیازمند migration/config, admin operator role, approval log, worker scheduling, safe batch delete/retry/transaction, failure alert, nonexpired preservation tests, backup expiry model و rollback plan است. هیچ scheduled cleanup جدیدی در این Phase اضافه نشده؛ دیتابیس و integration tests unavailable بودند.

**Owner:** Product/Privacy owner و SRE/DB owner باید تعیین شوند. **Jurisdiction:** در specification حاضر تعیین نشده؛ jurisdiction-specific requirement جداگانه بررسی شود.
