# ممیزی پیش از تغییر — فاز ۲۰

**زمان ممیزی:** 2026-10-05 (تاریخ محیط اجرا)  
**دامنه:** source tree موجود در `/home/user/guardianlink`، migrationهای V1–V9، مسیرهای API، Android/Web storage، deployment/configuration، backup/restore script و مستندات عملیاتی. این یک ممیزی ایستای source است؛ هیچ PostgreSQL، cloud، device یا production account در اختیار نبود.

## نتیجه ممیزی

- 42 جدول در 9 migration SQL، از V1 تا V9، شناسایی شد. نام جدول‌ها و دسته‌بندی آن‌ها در `PRIVACY_DATA_MAP.md` آمده است.
- endpointهای account/auth، family/device، pairing، usage/screen-time، location، geofence، app management، web safety، notifications، SOS و monitoring از routeهای Kotlin شناسایی شدند؛ inventory مسیرها در همین سند آمده است.
- API یک export یکپارچهٔ family یا account/family deletion ندارد. حذف تاریخچهٔ location برای دستگاه و soft-remove دستگاه موجود است؛ soft-remove معادل پاک‌سازی وابستگی‌ها نیست.
- location retention هر دستگاه configurable است (0 یا 1..365 روز) و purge عمدتاً lazy است؛ geofence events تنظیم server-side با مقدار پیش‌فرض 30 روز دارند و purge هنگام مسیرهای عملیاتی صدا زده می‌شود. retention سراسری/قابل تنظیم برای بسیاری از جدول‌ها وجود ندارد.
- Backup/restore script وجود دارد اما هیچ artifact، off-host storage، schedule، alert یا restore drill واقعی در این محیط تأیید نشد. سند قبلی `BACKUP_RESTORE_TEST.md` نیز صریحاً NOT RUN/BLOCKED است.
- Browser Web دارای privacy route ایستا بود؛ Parent Android Privacy Center مستقل موجود نبود. هیچ endpoint پشتیبان Export/Delete وجود ندارد.
- درخواست «Monitoring مخفی» با محدودیت‌های حریم خصوصی/شفافیت پروژه تعارض دارد؛ اجرا نشد. فقط افشای شفاف telemetry عملیاتی مجاز است.

## Inventory فایل/ماژول

| دامنه | مسیرهای منبع اصلی | نگهداری/ذخیرهٔ مشاهده‌شده |
|---|---|---|
| Schema/DB | `server/src/main/resources/db/migration/V1__phase1.sql` تا `V9__sos_emergency.sql`; `Db.kt`; `Repos.kt`, `LocationRepos.kt`, `UsageRepos.kt`, `GeofenceRepos.kt`, `NotificationRepo.kt`, `AppManagementRepos.kt`, `StatusRepos.kt`, `SosService.kt` | PostgreSQL. هیچ DB واقعی برای بررسی دادهٔ زنده/تعداد ردیف در دسترس نیست. |
| API/Models | تمام `*Routes.kt`, `Models.kt`, `*Models.kt`, `*Service.kt` در `server/src/main/kotlin/ir/guardianlink/server/` | inventory endpoint پایین همین سند؛ role enforcement عمدتاً user token/device token و ownership check. |
| Authentication/security | `Auth.kt`, `Tokens.kt`, `PasswordHasher.kt`, `Crypto.kt`, `RateLimiter.kt`, `UserRoutes.kt`, `DeviceRoutes.kt` | password hash؛ refresh/device/pairing hash در DB؛ private Ed25519 JWK در `DATA_DIR/ed25519.jwk`; session revocation در DB. |
| Location/geofence | Android `android/feature/location/**`; backend `Location*`, `Geofence*`; migrations V4/V5 | Android permission-aware collector و صف encrypted؛ PostgreSQL مختصات/history/settings/status/zone/events. |
| App usage/screen time | `android/feature/usage/**`; backend `Usage*`, `AppManagement*`; migrations V3/V6 | آمار روزانه و شناسهٔ package، inventory/rules/events و icon binary در DB؛ UsageStatsManager/Usage Access در Child. |
| Web safety | `android/feature/usage/WebSafetyScreen.kt`, `android/feature/childstatus/WebSafety*`; `WebSafety*` backend; V7 | rules/settings/overrides/capability events؛ schema comment می‌گوید visited site history نگهداری نمی‌شود. |
| SOS | `android/feature/childstatus/EmergencyScreen.kt`, `SosLocationService.kt`, `SosOutbox.kt`; dashboard `EmergencyCenterScreen.kt`; backend `Sos*`; V9 | session، metadata، notes، location points؛ نقاط حساس مکانی. هیچ اعلان واقعی در این ممیزی تولید نشد. |
| Notifications/events | Android `feature/dashboard/*Notification*`, `ParentNotifier.kt`; backend `Notification*`, `Monitoring*`; V2/V8 | اعلان‌ها projection از device events؛ `device_events`, family notification و per-user read state. |
| Audit/observability | `Log.kt`, `Observability.kt`, `MonitoringRoutes.kt`, `Monitoring.kt`, `logback.xml` | logs JSON به stdout؛ metrics process-local و bounded. جدول مستقل `audit_logs` وجود ندارد؛ event stores محدود به domain هستند. |
| Backup/restore | `deploy/scripts/backup-postgres.sh`, `restore-postgres.sh`, `BACKUP_RESTORE_GUIDE.md`, `BACKUP_RESTORE_TEST.md`, deployment compose | طرح `pg_dump → age → S3 SSE-KMS`; provisioning، schedule، S3 bucket، cloud keys و آزمون restore `NOT TESTED`. |
| Web local/cache | `web/src/auth.tsx`, `ui.tsx`, `api.ts`, `public/sw.js`, `public/offline.html` | توکن در حافظهٔ tab؛ language/theme در `localStorage`; service worker cache باید جداگانه بررسی شود. |
| Android local/cache | `android/core/storage/{SecureStore,PlainPrefs}.kt`, `LocationQueue.kt`, `SosOutbox.kt`, `AppPolicyCache.kt`, `WebSafetyCache.kt`, `ParentNotifier.kt` | SecureStore AES-GCM/Android Keystore ciphertext در DataStore؛ بعضی prefs معمولی برای تنظیمات/cooldown؛ صف‌های offline محدود. |
| Configuration | `server/src/main/resources/application.conf`, `server/src/main/resources/logback.xml`, `server/Dockerfile`, `deploy/docker-compose*.yml`, `deploy/Caddyfile`, `deploy/environments/.env.*.example`, `deploy/.env.example`, `web/.env.example`, Android Gradle properties/catalog, `.github/workflows/*.yml` | نمونه‌ها/placeholder؛ deployment config اثبات deployment واقعی نیست. |
| Secrets refs | GitHub Actions `${{ secrets.* }}` برای Android signing و GITHUB_TOKEN؛ env refs DB/AWS/age/KMS در scripts; `DATA_DIR` برای JWK | مقدار واقعی چاپ/استخراج نشد. `.git` metadata موجود نیست؛ Git history audit `BLOCKED BY ENVIRONMENT`. |
| Object storage/buckets | `S3_BACKUP_URI`, `S3_KMS_KEY_ARN` در backup script و راهنما | فقط reference؛ bucket، versioning، lifecycle، public block، IAM و KMS config واقعی `NOT TESTED`. |
| Build/distribution artifacts | `artifacts/phase5/*.apk`, `web/dist/` (از build فاز ۱۹)، GitHub release workflows | بررسی binary secret leakage/signing provenance انجام نشده؛ APK موجود است. هیچ secret واقعی در خروجی جستجوی متنی اعلام نشده، اما این جایگزین تاریخچه/اسکن binary نیست. |

## فهرست endpointها از route source

مسیرها زیر prefix `/v1` هستند مگر `/health/*`.

- Auth/user/family: `POST /auth/register`, `/auth/login`, `/auth/refresh`, `/auth/logout`; `GET /me`; `POST /families`; `GET /families/me`; `GET /families/me/devices`, `GET|DELETE /families/me/devices/{id}`.
- Pairing/device auth: `POST /pairing/sessions`, `GET /pairing/sessions/active`, `POST /pairing/exchange`, `/pairing/confirm`; `POST /device/auth`, `/device/refresh`, `/device/heartbeat`, `/device/status`, `/device/sync`.
- Usage: `POST /device/usage/upload`; تحت `/families/me/devices/{id}`: `GET usage/today|weekly|apps|apps/{pkg}/detail|apps/{pkg}/icon|usage/report/daily|screen-time/report|screen-time/settings|screen-time/schedules`; `POST screen-time/settings|screen-time/schedules`; `PUT screen-time/schedules/{sid}`.
- Location/geofence: `POST /device/location/sync`, `GET /families/me/devices/{id}/location/current|history|settings`, `POST .../location/settings`, `DELETE .../location/history`; device geofence `GET /device/geofences`, `POST /device/geofences/sync|events`; family geofence `GET|POST geofences`, `PUT|DELETE geofences/{gid}`, `GET geofences/{gid}/events|geofence-events`.
- App management: device `GET /device/app-rules`, `POST /device/app-management/sync`; family device `GET apps|apps/{packageName}/usage|app-rules|app-events`, `POST app-rules`, `PUT|DELETE app-rules/{ruleId}`.
- Web safety: `POST /device/web-safety/sync`; family device web safety routes for rules/events, `POST/PUT/DELETE rules`, `POST/DELETE overrides`; exact names defined in `WebSafetyRoutes.kt`.
- Monitoring: `GET /families/me/devices/{id}/health|permissions|events`; `GET/PUT /families/me/settings`; `GET /families/me/events`; health endpoints `/health/live`, `/health/ready`; `/metrics` route in proxy is deliberately 404 public.
- Notification: `GET /families/me/notifications` (with route query controls), `/unread-count`, `/{id}`, `/preferences`; `POST /read-all`, `/{id}/read|acknowledge|resolve`; `PUT /preferences`.
- SOS: device `POST /device/sos`, `GET /device/sos/active|{sosId}`, `POST /device/sos/{sosId}/cancel|tracking|network|locations`; family `GET|PUT /families/me/sos/preferences`, `GET /families/me/sos/active`, `GET /families/me/devices/{id}/sos/history`; under `/families/me/sos/{sosId}`: `GET detail|location|events|notes`, `POST notes|acknowledge|resolve`.

`POST` versus `GET`/`PUT` above follows Ktor source; verify exact route composition in `Main.kt` before client integration. No export endpoint, account-delete endpoint, family-delete endpoint, general retention admin endpoint or backup-monitoring endpoint was found.

## محدودهٔ اجرا و عدم قطعیت

- PostgreSQL/psql/pg_dump/pg_restore/Docker/Podman/age/AWS CLI absent; secret/cloud credentials unavailable; no production/staging DB endpoint.
- No `.git` metadata; secret-history scan impossible. Search of tracked text/config surfaces only references/placeholders, not history or full binary forensic scan.
- Android SDK/Gradle/JDK 17/device absent; Android source modifications below were not compiled or instrumented.
- Web had Node/npm; after privacy page edit `npm ci --ignore-scripts`, Vitest and Vite production build actually ran (evidence in final report).
- No code was changed until this pre-change audit and source inventory were completed.
