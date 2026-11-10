# گزارش سیاست نگهداری — فاز ۲۰

**وضعیت:** `PARTIAL`. جدول retention source audit شده؛ فقط برخی purge pathها در source هستند. حذف خودکار سراسری/زمان‌بندی‌شده و سیاست مصوب برای همهٔ داده‌ها تأیید نشد. پایگاه‌داده برای اجرای query یا اثبات حذف موجود نیست.

## وضعیت جاری در کد (اجرای واقعی `NOT TESTED`)

| داده | وضعیت/مدت موجود در source | روش فعلی | archive/backup | تغییر‌دهندهٔ مجازِ موجود |
|---|---|---|---|---|
| Location history | مقدار default در migration=30 روز؛ API مقدار 0 (فقط آخرین fix) یا 1..365 روز per-device | purge در مسیر sync/خواندن؛ DELETE امروز/7 روز/همه؛ transaction-atomicity delete اجرا نشده | در PostgreSQL dump می‌آید؛ backups در صورت وجود حذف فوری را منعکس نمی‌کنند | Parent مالک همان device از location settings |
| Geofence events | `GEO_EVENT_RETENTION_DAYS`, default 30؛ دامنهٔ validate/config runtime در source | `purgeOldEvents` در مسیرهای فعال geofence؛ schedule سراسری ندارد | DB dump intended؛ archive مستقل نیست | operator از env؛ Parent capability تغییر مقدار ندارد |
| Uninstalled app inventory | حذف پس از 30 روز uninstalled، هنگام sync | `purgeOldUninstalled` | DB dump intended | هیچ retention UI؛ پاک‌سازی در مسیر sync |
| App restriction events | 90 روز در مسیرهای مشخص AppManagement | purge on request/sync, نه job مستقل | DB dump intended | operator/source constant؛ parent تنظیم ندارد |
| Web filter events/expired overrides | 90 روز cleanup در flow فعال web-safety | opportunistic cleanup | DB dump intended | constant/backend; Parent تنظیم ندارد |
| Daily app usage, app icons, screen-time schedule/state | دورهٔ عمومی انقضا برای app_usage_daily/app_icons/schedules/state پیدا نشد | upsert/overwrite یا cascade با device؛ حذف دستی کلی ندارد | DB dump intended | Parent تغییر usage data retention ندارد |
| Device events, family notifications/read state | retention عمومی پیدا نشد؛ 일부 device/geofence-specific cleanup | domain writes/read state; no global cleanup job | DB dump intended | Parent read/status preference؛ retention ندارد |
| SOS sessions/events/notes/location points | مدت حذف/نگهداری پس از close/expire مشخص نیست؛ `expires_at` برای workflow end state است، نه purge | expire state در بعضی SOS requests؛ rows باقی می‌مانند تا cascade/manual DB action | DB dump intended | Parent می‌تواند acknowledge/resolve/note؛ retention policy تغییر نمی‌دهد |
| Pairing/user/device sessions | expiry/revocation در records؛ cleanup عمومی expired rows نامشخص | expiry checks/revocation؛ no global scheduled purger confirmed | DB dump intended | user/device logout; operator DB retention نامشخص |
| Security/audit/logs | جدول مستقل audit_logs وجود ندارد؛ stdout external rotation نامشخص | logger/metrics; metrics in-memory until process restart | DB dump شامل log stream نیست؛ مقصد خارجی نامشخص | operator خارج برنامه؛ مالک مشخص نیست |
| Metrics | bounded in-memory counters و sample ring؛ process lifetime | reset با restart | backup نمی‌شوند | system process |
| Backup files | script defaults local daily 14d, weekly 56d, monthly 365d | cron/scheduler provision نشده؛ local `find` pruning در زمان script؛ S3 lifecycle/delete وجود ندارد | encrypted intent (age+KMS)؛ actual state unknown | operator ENV/IAM; approval owner not specified |

## سیاست پیشنهادی برای تصویب ــ هنوز فعال نشده

این جدول تصمیم مهندسی پیشنهادی برای minimization است؛ **هیچ row آن به‌جز مقدارهایی که صریحاً «فعلی» هستند enforce نمی‌شود**. مدت‌های emergency/security و نگهداری legal را Product/Privacy Owner تعیین کند. حذف از backup/versioned storage باید با retention، legal hold و تکلیف restore هماهنگ شود.

| دسته | target پیشنهاد | حذف/archive | backup retention target | تغییر سیاست |
|---|---|---|---|---|
| Location history | پیش‌فرض هدف 30 روز؛ اختیار 0/1..365 per-device موجود | safe batched delete؛ audit بدون مختصات؛ latest-only طبق setting | همان چرخهٔ backup DB با توضیح عدم حذف فوری از نسخه‌های پشتیبان | Parent per-device؛ حدها فقط operator migration/config |
| Geofence events | 30 روز هدف مطابق default موجود، مشروط به تایید مالک | batched cleanup job؛ retry و شمارش حذف | backup DB؛ remote expiry مطابق سیاست backup | operator؛ تغییر config versioned/audited |
| App usage daily | پیشنهاد 90 روز جهت حفظ بازه گزارش محدود؛ تصویب لازم | cleanup روزانه batched، savepoint/transaction batch | backup <= 14 روز daily proposal, subject owner | operator config; family control desirable but unsupported |
| App/restriction/web events | پیشنهاد 90 روز حداکثر؛ فقط fields لازم و redacted | scheduled batch، failures alert | همان backup window | operator config |
| Notifications | پیشنهاد 90 روز پس از event؛ emergency alert exception policy جدا | purge بعد از verified cascade-safe, retain only needed per user state | backup <= 14 days daily proposal | system policy; no current UI |
| SOS lifecycle/locations/notes | `NOT DEFINED` تا product/privacy + safety owner تصویب کنند؛ sensitive emergency record, do not silently purge | documented closure/delete/hold workflow; preserve only justified subset | `NOT DEFINED` until emergency/legal policy | dual-owner approval; not user configurable today |
| Auth sessions/pairing | retain active until expiry/revoke; expired/revoked tombstones target 30d recommended | scheduled cleanup; keep minimal security event separately | DB backup schedule | operator policy; no UI |
| Security events/audit | `NOT DEFINED` until threat/incident and jurisdiction needs confirmed; separate PII minimization | append-only bounded metadata; avoid raw secrets/coordinates | protected backup, separate access | Security owner, audited changes |
| Application logs | 14d recommendation, metadata-only; actual sink currently unknown | external log platform lifecycle + access control | not in DB backups; separate encrypted archive only if needed | SRE/security operator |
| Metrics | process memory; external TSDB target 30d recommendation if provisioned | aggregations without IDs/payload; external TTL | normally not in DB backup | SRE operator |
| Backups | local defaults exist; target: daily 14d, weekly 56d, monthly 365d is script default proposal, not approved deployment policy | automate remote lifecycle + integrity checks | encrypted, off-host, retention tags | designated backup operator; dual approval for destructive retention changes |

## الزامات پیاده‌سازی لازم

پیش از enforcement: owner/privacy approval، migration/config model with safe bounds, scheduled worker independent from user traffic, bounded batches, locking/transaction design, retry/idempotency, counter-only audit and failure alerting, integration tests with expired/non-expired rows, FK/orphan tests and backup-consistency review. این‌ها انجام نشده‌اند. Purge در مسیرهای user traffic را به‌عنوان scheduler تضمینی معرفی نمی‌کنیم.

فایل `DATA_RETENTION_POLICY.md` سیاست هدف را با تفکیک «فعلی» و «پیشنهادی/تصویب‌نشده» خلاصه می‌کند.
