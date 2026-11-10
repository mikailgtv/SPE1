# Security Operations, Privacy & Permission Review

**وضعیت:** عملیاتی/فنی باز؛ این راهنما gapها را صریح می‌کند و نمی‌گوید کنترل پیاده نشده، موجود است.

## دادهٔ حساس

| Data class | Purpose | Storage مشاهده‌شده | Retention مشاهده‌شده | Who can access | Delete method / gap |
|---|---|---|---|---|---|
| Account/email/password | Login/session | PostgreSQL؛ password Argon2id hash | user sessions TTL/revocation محدود؛ cleanup/verification کامل نامعلوم | backend; family user by access token | account delete/export endpoint پیدا نشد. |
| Location current/history | Parent-approved child location sharing | `locations` + Android queue؛ queue local از Phase 10 SecureStore migration دارد؛ DB ستون field-encrypted نیست | location policy/settings و purge محدود؛ global env policy نداریم | Parent resource family-owned; backend runtime DB role | history delete route دارد؛ physical flash/backups and data-class delete not proven. |
| SOS points/events/notes | emergency state tracking/Parent handling | `sos_sessions`, `sos_location_points`, `sos_events`, `sos_notes`; PostgreSQL regular columns | auto-expiry/session behavior دارد اما full points/notes retention job در audit تأیید نشده | owning family users/authorized child workflows | dedicated delete/export/audit lifecycle incomplete. Emergency services notify نمی‌شوند. |
| Device events/monitoring | health/battery/permission and notification projection | `device_events` and status tables; DB | complete retention/cleanup end-to-end unknown | Parent family/device API | no unified purge/export policy. |
| App usage/inventory/rules | parent view and policy | `app_usage_daily`, inventory/rules/icons, DB; package inventory sensitive | usage retention job/export not verified | family-scoped Parent routes, child upload as device principal | no full account/device deletion audit.
| Web safety rules/events | parent configured domain/category policy | DB tables; events may include domain/category metadata | service has hard-coded 90-day cleanup for some event/override data; not universal | family/device owners and Child policy cache | purge only some data; domain metadata is sensitive.
| Notifications | in-app operational feed/read state | `family_notifications`, `notification_user_state` | comprehensive retention not established | family members/read state | no full export/delete lifecycle; no server push.
| Audit logs | attribution/security | global append-only `audit_logs` table/service not found | N/A | N/A | **Not implemented**; Phase 12 does not claim audit log complete.
| Backups | disaster recovery | none generated in this environment; proposed encrypted age/S3 pipeline | draft daily14/weekly56/monthly365; S3 policy not provisioned | proposed limited AWS IAM + offline age identity | restore test not run; deletion from versioned backups unresolved.

## Audit events gap

Code review previously found no unified actor-aware/tamper-resistant audit log. Required events (LOGIN/LOGOUT/PAIR/REMOVE/REVOKE/CHANGE_RULE/CHANGE_LOCATION_SETTING/CREATE/DELETE_GEOFENCE/SOS_ACTION/ACCOUNT_CHANGE/SECURITY_EVENT) are **OPEN** as centralized audited events. Existing `sos_events`/`device_events` serve feature/event feeds and are not a substitute for immutable security audit. Before implementing, define minimal fields, access restriction, retention, append-only DB role, integrity mechanism, privacy minimization and failure semantics; use separate migration and tests. No untested placeholder table is presented as a control.

## Android manifest permission inventory

| Permission/component | App | Purpose / where used | Required/optional / fallback |
|---|---|---|---|
| `INTERNET` | Parent + Child | `core/network/ApiClient.kt`; authenticated API/sync | Required for online; offline UI/cache limited. |
| `ACCESS_NETWORK_STATE` | Parent + Child | `feature/childstatus/NetworkWatcher.kt`, `StatusSyncWorker.kt` | Optional connectivity hint; network restore triggers sync. |
| `POST_NOTIFICATIONS` | Parent | `feature/dashboard/ParentNotifier.kt`, `NotificationSummaryScheduler.kt` | Optional, Android 13+ consent; feed remains in app if denied. |
| `ACCESS_COARSE_LOCATION`, `ACCESS_FINE_LOCATION` | Child | `feature/location/LocationPermission.kt`, `LocationCollector.kt`; `feature/childstatus/EmergencyScreen.kt` | Optional/consent; permission denial reports unavailable/limited; no forced loop. |
| `ACCESS_BACKGROUND_LOCATION` | Child | `feature/location/LocationPermission.kt` and location sharing service path | Highly sensitive; separate system permission/disclosure; denied → background location unavailable. |
| `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_LOCATION` | Child | `feature/childstatus/SosLocationService.kt` | Only active SOS; system FGS notification/status required; restrictions honored. |
| `PACKAGE_USAGE_STATS` special access | Child | `feature/usage/UsageCollector.kt` | User opens official settings; denied → unavailable, no content access. |
| `QUERY_ALL_PACKAGES` | Child | `feature/usage/AppInventoryCollector.kt` | Policy-sensitive; only retain if distribution path/legal justification approved. No Play policy review in this workspace. |
| `BIND_DEVICE_ADMIN` receiver | Child | `feature/childstatus/GuardianDeviceAdminReceiver.kt`, `AppPolicyEngine.kt` | Declaration is not Device Owner. Normal mode must disclose unsupported admin-only actions. |
| Chrome package visibility query | Child | `feature/childstatus/WebSafetyEngine.kt` / capability checks | Query is limited to Chrome; absence/provider restrictions report unavailable, no broad hidden query added. |
| Chrome package query | Child | managed Chrome capability check | Narrow package visibility query only. |
| SOS location FGS | Child | user-visible limited tracking | No hidden/background covert monitoring. |

No Accessibility service, SMS/call log, camera, microphone, screen capture, VPN permission, root/ADB bypass permission was found in the primary manifests inspected. This static check is not a merged-manifest/device policy audit.

## Device Owner / mode truth

- `NORMAL`: default consumer Android mode; app-management blocking is not Device Owner enforcement. Feature status must remain limited/unsupported where OS API denies it.
- `MANAGED_DEVICE`: report only after official `DevicePolicyManager.isDeviceOwnerApp`/Profile Owner check; provisioning via Android Enterprise is not included in this phase.
- Existing code checks owner status and reports mode; Factory Reset, enrollment, recovery/unprovisioning and real Device Owner E2E were not tested. Do not claim a normal APK behaves like Device Owner.

## Data export/delete status

- Location history delete endpoint exists for a device/scoped range; do not infer account/family-wide erasure.
- Device removal/revoke path exists, but complete related data, backups, cache, signing/device credentials and retention cleanup must be verified.
- Account export/delete and Delete Family operations were not found in API route inventory.
- SOS, App Usage, Notifications, Device Events and Audit data need an explicit delete/export map.
- Product/legal owner must determine deletion deadlines and backup lifecycle. Do not promise erasure before verification.

## Incident/secret routine

1. Record incident ID and scope without copying PII/credentials.
2. Isolate compromised CI/cloud identity using provider control plane; rotate only approved keys and preserve forensic access trail.
3. Revoke affected user/device sessions through supported API or controlled DBA only after review; do not invent UI/remote action.
4. Assess exposure by data class/tenant, backups, log access and processor region; notify privacy/legal owner.
5. Save redacted evidence, containment timeline, follow-up, and key rotation proof; run post-incident review.
