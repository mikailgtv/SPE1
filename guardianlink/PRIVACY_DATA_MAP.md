# نقشهٔ داده و مسیرهای پردازش GuardianLink — فاز ۲۰

**منبع:** migrationهای V1–V9 و source Android/Web/Backend در workspace، بررسی‌شده در 2026-10-05. این نقشه static inventory است؛ نمونهٔ پایگاه‌دادهٔ زنده یا telemetry واقعی بررسی نشده.

## 42 جدول پایگاه‌داده

| جدول | داده/هدف | Scope دسترسی در source | Retention / حذف در source | حساسیت |
|---|---|---|---|---|
| `users` | UUID، ایمیل، password hash، created | خود user از auth; Parent token | حذف account route ندارد؛ cascade اگر user hard delete شود | بالا |
| `families` | UUID، نام، زمان ساخت | Parent عضو | family delete route ندارد؛ hard-delete FK می‌تواند cascade کند | متوسط |
| `family_members` | link user/family، role | خانواده | cascade از family/user؛ membership delete route مشخص نیست | بالا |
| `devices` | family، device name، manufacturer/model، Android/app version، battery، last_seen، install_id، secret hash/status | Parent همان family؛ Child token خودش | API soft remove، نه row delete | بالا |
| `pairing_sessions` | family/parent، code/exchange hashes، state، expiry، used, device UUID | Parent family; pairing flow | expiry checks؛ global purge پیدا نشد؛ `device_id` بدون FK | بسیار بالا موقت |
| `user_sessions` | refresh hash، create/expiry/revoked | user session | expiry/revoke؛ purger عمومی نامعلوم؛ cascade user | بسیار بالا |
| `device_sessions` | refresh hash، create/expiry/revoked | device credential | expiry/revoke؛ cascade device | بسیار بالا |
| `family_settings` | battery thresholds, sync/offline/heartbeat params | Parent family | current row overwrite; cascade family | کم/متوسط |
| `device_status` | battery, network, health, heartbeat/sync, versions/model/manufacturer, permission JSON | Parent family; device own reports | latest state upsert; cascade device | بالا |
| `device_events` | device, type, timestamp, metadata, dedupe key | Parent family; device own event | no general TTL; selected purge in geofence path; cascade device | متوسط/بالا |
| `app_usage_daily` | package/app/category/date/minutes/launch/time | Parent same device; Child upload | no explicit general delete/TTL found | بالا |
| `app_icons` | package + PNG BYTEA | Parent same device; app catalog | overwrite/cascade device; no TTL | متوسط (binary) |
| `screen_time_limits` | daily limit, near pct, enabled | Parent same device | current config, cascade device | متوسط |
| `screen_time_schedules` | name/time/days/enabled | Parent same device | delete endpoint not identified in tested scope; cascade device | متوسط |
| `screen_time_state` | daily limit state/schedule keys | service internal/device scope | no explicit TTL found | متوسط |
| `locations` | latitude/longitude, accuracy, client/server time, source | Parent same device; Child location sync | 0 latest-only or 1..365d; lazy purge; per-device delete route | بسیار بالا |
| `location_settings` | sharing flag, retention days | Parent same device | current row; cascade device | بالا |
| `location_status` | permission/provider state, last fix/report timestamps, health | Parent same device | current row; cascade device | بالا |
| `geofences` | zone name, exact coordinates, radius, enabled | Parent same device | explicit delete route; cascade device | بسیار بالا |
| `geofence_states` | inside/outside/unknown, entered/checked timestamps | device/parent same device | current state; cascade geofence | بالا |
| `geofence_events` | enter/exit/error, time, accuracy, dwell | Parent same device | `GEO_EVENT_RETENTION_DAYS` default 30; opportunistic purge | بسیار بالا |
| `geofence_notification_settings` | entry/exit notify | Parent same device | current config; cascade geofence | متوسط |
| `app_inventory` | package, app name/version/category, install metadata, installed flag | Parent same device; device sync | uninstalled rows purge after 30d when sync runs | بالا |
| `app_management_state` | policy version, management mode, usage access, timezone, hashes/sync | Parent same device; Child sync | current state; cascade device | بالا |
| `app_rules` | package/rule/limit/enabled/version/creator | Parent same device; Child policy sync | explicit delete; cascade device | بالا |
| `app_rule_schedules` | rule schedule days/times | Parent same device; Child policy sync | cascade rule/device | متوسط |
| `app_rule_usage_state` | usage date and enforcement stage | service internal/device | cleanup of records older than 90 days in certain service path | متوسط |
| `app_restriction_events` | package/rule/type/time/metadata | Parent same device; Child reports | 90d purge in selected active paths | بالا |
| `web_protection_settings` | mode, status, enabled, policy version etc | Parent same device; Child sync | current row; cascade device | متوسط |
| `web_rules` | domain/rule type/scope/action/category/priority | Parent same device; Child sync | explicit delete; cascade device | بالا |
| `web_rule_schedules` | schedule/timezone/action | Parent same device | cascade rule | متوسط |
| `web_categories` | category config/catalog | policy source; global | seeded/current state; global retention not applicable | کم |
| `web_overrides` | domain/action/expiry/revoke/creator | Parent same device | 90d cleanup when service path executes | بالا |
| `web_filter_events` | capability/config error events, event type/time/metadata | Parent same device; Child reports | 90d cleanup when flow executes; no visited-site history per migration comment | متوسط/بالا |
| `family_notifications` | event copy/type/category/severity/title/body keys/metadata/times/status | Parent same family | no TTL found; cascade family/device/event | بالا |
| `notification_user_state` | user read timestamp per notification | user/family | cascade notification/user | متوسط |
| `notification_preferences` | categories/quiet hours/timezone/cooldown | Parent same family | current row; cascade family | کم/متوسط |
| `sos_preferences` | auto expiration hours | Parent family | current row; cascade family | متوسط |
| `sos_sessions` | emergency workflow, device state, permissions, battery/network, times, notes field | Parent family/device; own Child token operations | expires workflow status only; no deletion TTL found | بسیار بالا |
| `sos_location_points` | precise lat/lon/accuracy/provider/timestamps | Parent family via authorized SOS session; device session writes | no independent TTL; cascade session | بسیار بالا |
| `sos_events` | event, actor/user, metadata, dedupe | Parent family/device/system | no TTL found; cascade session/family/device | بسیار بالا |
| `sos_notes` | parent note and author/session | Parent family | no TTL; cascade session/family/user | بسیار بالا |

## Backend route index

Full method/path inventory generated from `*Routes.kt` is recorded in `PHASE_20_PRECHANGE_AUDIT.md`. Key authorization patterns: `/families/me` resolves family from authenticated Parent, per-device routes call ownership check, device routes resolve verified device principal; black-box IDOR tests are `NOT TESTED`. No `export`, account delete, family delete, retention administration, backup monitoring/alert endpoint or separate audit table found.

## Client-side/local data stores

| Platform/store | key/contents | Purpose | Protection/clear behavior |
|---|---|---|---|
| Web memory | auth access/refresh tokens in auth state | API session | cleared when page reloads/signout; no localStorage/sessionStorage token use in source |
| Web localStorage | `gl-language`, `gl-theme` | UI preference | not encrypted; low sensitivity; persists until browser/site data clear |
| Web service worker CacheStorage | cache `guardianlink-shell-v1`: `/offline.html`, manifest, icon; navigate network fallback to offline page; `/v1/` and `/health` bypassed | offline shell | activation deletes prior cache names; no TTL for current shell; API response caching is bypassed in source; browser runtime `NOT TESTED` |
| Android SecureStore | `gl_secure` DataStore encrypted using AES/GCM key in Android Keystore; auth/secret queues/policy caches | secure local credential/offline data | Android OS Keystore protection; device backup/restore behavior `NOT TESTED` |
| Android PlainPrefs | `gl_prefs` DataStore | nonsecret app preferences/legacy queue migration source | no encryption; audit content keys before future sensitive additions |
| Android SharedPreferences | `notification_cooldowns` | local parent notification cooldowns | MODE_PRIVATE, not cryptographic; OS sandbox; content is timing keys |
| Android WorkManager | scheduled task metadata | sync/status/policy scheduling | OS managed; persisted job input/source inspection; DB runtime unavailable |
| Android SOS outbox | `sos_outbox_v1`, `sos_tracking_outbox_v1`, up to 1800 points and network events; 24h expiry | reliable offline SOS/tracking delivery | persisted through `GlServices.secure` (AES-GCM SecureStore); separate status marker in PlainPrefs; runtime/device backup lifecycle `NOT TESTED` |

## Config/secret/storage references

- Server config: `DB_URL`, `DB_USER`, `DB_PASS`, `DATA_DIR`, Hikari pool/timeouts, `APP_ENV`, `LOG_LEVEL`, token/pairing/presence/heartbeat/location/geofence thresholds including `GEO_EVENT_RETENTION_DAYS`.
- Web: `VITE_API_BASE_URL` (same-origin constrained); language/theme localStorage keys.
- Android releases: GitHub Actions variables for API base URL and secrets for signing keystore/alias/password; no actual values recorded.
- Backup: `PGHOST`, `PGDATABASE`, `PGUSER`, `BACKUP_DIR`, `AGE_RECIPIENT`, `S3_BACKUP_URI`, `S3_KMS_KEY_ARN`, `AWS_REGION`, `AGE_IDENTITY_FILE`; references only.
- Secret manager references in production template to AWS Secrets Manager/task role; deployment not provisioned/verified. `ed25519.jwk` private signing key file in DATA_DIR is separate from DB dump.
- Storage: Compose named volumes `dbdata`, `serverdata`, `caddydata`; backup S3 reference only. At-rest disk encryption, volume snapshot policies and S3 policy/lifecycle `NOT TESTED`.
- Config files: `server/src/main/resources/application.conf`, `logback.xml`; `server/Dockerfile`; `deploy/docker-compose.yml`, `docker-compose.dev.yml`, `Caddyfile`, `.env*.example`; `web/.env.example`, `vite.config.ts`; Android Gradle `build.gradle.kts`, `gradle.properties`, `libs.versions.toml`; GitHub workflows/dependabot/codeql.
- Workspace has no `.git`; `.env` real files were not present in shallow file inventory; Git history and hidden external secret stores were not available for audit.
