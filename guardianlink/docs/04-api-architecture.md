# 04 — API Architecture

## ۱. قواعد کلی REST

| مورد | قرارداد |
|---|---|
| Base URL | `https://api.guardianlink.app/v1` (در Dev: `http://192.168.x.x:8080/v1`) |
| Versioning | در path (`/v1`). تغییرات breaking ⇒ `/v2` و پشتیبانی همزمان ۶ ماه |
| Content-Type | `application/json; charset=utf-8` |
| Serialization | kotlinx-serialization (snake_case در JSON) |
| Auth والد | `Authorization: Bearer <access_jwt>` |
| Auth دستگاه فرزند | `X-Device-Id` + `X-Timestamp` + `X-Nonce` + `X-Signature` (HMAC — ADR-005) |
| Idempotency | `Idempotency-Key: <uuid>` برای همه POSTهای نوشتنی (سرور ۲۴ ساعت کش می‌کند) |
| Pagination | `?limit=30&cursor=<opaque>` → `{ "items": […], "next_cursor": "…", "has_more": true }` |
| Filtering | `?status=active&since=<iso8601>` |
| زمان | همیشه ISO-8601 UTC (`2026-09-28T12:34:56.789Z`) |
| ETag | روی `GET /devices/{id}/snapshot` و `GET /devices/{id}/settings` ⇒ صرفه‌جویی باتری/دیتا |
| Rate limit headers | `X-RateLimit-Limit`, `X-RateLimit-Remaining`, `X-RateLimit-Reset`, `Retry-After` |
| CORS | فقط برای پنل وب آینده؛ اپ‌های Native نیازی ندارند |
| Body size limit | ۲۵۶ KB (۱ MB برای batch sync) |

---

## ۲. مدل خطا — RFC 7807 (Problem Details)

```http
HTTP/1.1 410 Gone
Content-Type: application/problem+json

{
  "type": "https://errors.guardianlink.app/device-removed",
  "title": "Device removed",
  "status": 410,
  "code": "DEVICE_REMOVED",
  "detail": "This device was removed by a parent on 2026-09-28T10:12:00Z.",
  "instance": "/v1/child/heartbeat",
  "request_id": "01J8ZK6Q9VX3P2M7R4T1WYB0CN",
  "retryable": false,
  "client_action": "stop_sync_and_notify_child",
  "errors": []
}
```

### جدول کدهای خطا

| HTTP | `code` | معنا | `retryable` | `client_action` |
|---|---|---|---|---|
| 400 | `VALIDATION_FAILED` | بدنه/پارامتر نامعتبر (+ `errors[]` با `field`,`message_key`) | ✗ | `show_form_error` |
| 401 | `TOKEN_EXPIRED` | access token منقضی | ✗ | `refresh_then_retry` |
| 401 | `TOKEN_INVALID` | امضا/فرمت بد | ✗ | `logout` |
| 401 | `DEVICE_SIGNATURE_INVALID` | امضای HMAC دستگاه نامعتبر | ✗ | `reauthenticate_device` |
| 401 | `CREDENTIALS_INVALID` | ایمیل/رمز اشتباه | ✗ | `show_auth_error` |
| 403 | `FORBIDDEN_FAMILY` | دسترسی به خانواده دیگر | ✗ | `logout` + `[SECURITY]` audit |
| 403 | `INSUFFICIENT_PERMISSION` | عضو خانواده این مجوز را ندارد | ✗ | `show_explanation` |
| 403 | `CONSENT_REQUIRED` | این capability توسط والد غیرفعال شده (یا رضایت اتصال ثبت نشده) | ✗ | `show_explanation` |
| 404 | `NOT_FOUND` | منبع وجود ندارد یا در خانواده شما نیست | ✗ | `refresh_list` |
| 409 | `ALREADY_PAIRED` | دستگاه قبلاً pair شده | ✗ | `show_unpair_option` |
| 409 | `DEVICE_LIMIT_REACHED` | حداکثر دستگاه خانواده | ✗ | `show_upgrade_or_remove` |
| 409 | `VERSION_CONFLICT` | تنظیمات قدیمی (optimistic lock) | ✓ | `refetch_and_merge` |
| 410 | `DEVICE_REMOVED` | والد دستگاه را حذف کرده | ✗ | **`stop_sync_and_notify_child`** |
| 410 | `SESSION_REVOKED` | نشست باطل شده | ✗ | `logout` |
| 422 | `PAIRING_CODE_EXPIRED` | کد منقضی | ✗ | `request_new_code` |
| 422 | `PAIRING_CODE_INVALID` | کد اشتباه (attempt_count++) | ✗ | `show_retry_with_attempts_left` |
| 422 | `PAIRING_CODE_USED` | یک‌بارمصرف، قبلاً استفاده شده | ✗ | `request_new_code` |
| 429 | `RATE_LIMITED` | بیش از حد مجاز | ✓ (با `Retry-After`) | `backoff` |
| 423 | `ACCOUNT_LOCKED` | قفل موقت بعد از تلاش‌های زیاد | ✓ | `show_lockout_timer` |
| 500 | `INTERNAL_ERROR` | خطای سرور (بدون نشت جزئیات) | ✓ | `retry_with_backoff` |
| 503 | `SERVICE_UNAVAILABLE` | maintenance / db down | ✓ | `offline_mode` |
| — | `NETWORK_UNAVAILABLE` | **کلاینتی** (سرور پاسخ نداد) | ✓ | `offline_mode` + Outbox |

> `request_id` در همه پاسخ‌ها و در `X-Request-Id` header ⇒ تطبیق لاگ کلاینت و سرور (بند ۱۹ پرامپت).

---

## ۳. Endpoints — Phase 1

### ۳.۱ Auth (والد)

| Method | Path | Auth | توضیح |
|---|---|---|---|
| POST | `/v1/auth/register` | — | ساخت حساب + ساخت خودکار Family |
| POST | `/v1/auth/login` | — | ورود (rate limit: 5/min/IP) |
| POST | `/v1/auth/refresh` | refresh token | چرخش token |
| POST | `/v1/auth/logout` | Bearer | revoke همان refresh token |
| POST | `/v1/auth/logout-all` | Bearer | revoke همه نشست‌ها |
| GET | `/v1/auth/me` | Bearer | پروفایل + خانواده + نقش |
| POST | `/v1/auth/password/forgot` | — | (Phase 9: ایمیل) |
| DELETE | `/v1/auth/me` | Bearer | حذف حساب (۳۰ روز grace) |

**`POST /v1/auth/register`**
```jsonc
// Request
{ "email": "parent@example.com", "password": "••••••••", "display_name": "مریم",
  "family_name": "خانواده رضایی", "locale": "fa-IR", "timezone": "Asia/Tehran",
  "accept_terms_version": "1.0" }
// Response 201
{ "user": { "id": "…", "email": "…", "display_name": "مریم", "role": "parent", "status": "active" },
  "family": { "id": "…", "name": "خانواده رضایی", "role": "owner_parent",
              "limits": { "max_children": 6, "max_devices": 10 } },
  "tokens": { "access_token": "eyJ…", "access_expires_in": 900,
              "refresh_token": "gl_rt_…", "refresh_expires_in": 2592000, "token_type": "Bearer" },
  "must_verify_email": false }
```
`409 EMAIL_TAKEN` · `422 VALIDATION_FAILED` (رمز ≥ ۱۰ کاراکتر، شامل عدد و حرف)

**`POST /v1/auth/login`**
```jsonc
{ "email": "…", "password": "…", "device_fingerprint": "sha256:…", "app_version": "0.1.0", "locale": "fa-IR" }
// 200 → همان ساختار tokens
// 401 CREDENTIALS_INVALID  ·  423 ACCOUNT_LOCKED (retry_after_sec: 900)
```

### ۳.۲ Family

| Method | Path | توضیح |
|---|---|---|
| GET | `/v1/family` | خانواده + اعضا + خلاصه دستگاه‌ها |
| PATCH | `/v1/family` | تغییر نام، timezone، limits |
| GET | `/v1/family/consents` | وضعیت رضایت‌ها per device/capability |
| PUT | `/v1/family/consents/{deviceId}` | به‌روزرسانی رضایت (فقط با نقش `admin`) |

### ۳.۳ Pairing

| Method | Path | Auth | توضیح |
|---|---|---|---|
| POST | `/v1/pairing/codes` | Bearer | ساخت کد ۸ کاراکتری + QR payload + `expires_at` |
| GET | `/v1/pairing/codes?status=pending` | Bearer | لیست کدهای فعال (بدون نمایش کد کامل) |
| DELETE | `/v1/pairing/codes/{id}` | Bearer | باطل کردن کد |
| POST | `/v1/pairing/exchange` | **Pairing** | کودک کد + اطلاعات دستگاه را می‌فرستد ⇒ `device_id` + `device_secret` می‌گیرد |
| POST | `/v1/pairing/confirm` | Device | فرزند بعد از دیدن صفحه افشا (فقط‌خواندنی) تأیید می‌کند |
| GET | `/v1/pairing/status/{codeId}` | Bearer | والد وضعیت را poll/SSE می‌کند: `pending → redeemed → confirmed → completed` |

**`POST /v1/pairing/codes`**
```jsonc
// Request
{ "ttl_seconds": 600, "child_profile": { "display_name": "سارا", "avatar_key": "star", "birth_year": 2013 },
  "max_uses": 1 }
// Response 201
{ "pairing_id": "01J8…", "code": "7QK4M2XP",
  "qr_payload": "gl1://pair?c=7QK4M2XP&s=https%3A%2F%2Fapi.guardianlink.app&v=1",
  "expires_at": "2026-09-28T12:44:56Z", "max_attempts": 10,
  "child_profile": { "id": "01J8…", "display_name": "سارا", "avatar_key": "star" } }
```

**`POST /v1/pairing/exchange`** (بدون Bearer — اعتبارسنجی با خود کد + rate limit سخت)
```jsonc
// Request
{ "code": "7QK4M2XP",
  "device": { "install_id": "…", "device_key_id": "sha256:…", "public_key": "base64…",
              "model": "Pixel 8", "manufacturer": "Google", "brand": "google", "device": "shiba",
              "android_sdk_int": 36, "android_release": "16",
              "app_version_name": "0.1.0", "app_version_code": 1,
              "locale": "fa-IR", "timezone": "Asia/Tehran",
              "is_emulator": false, "is_rooted": false, "capabilities": { … } },
  "client_nonce": "…", "client_timestamp": "2026-09-28T12:35:00Z" }
// Response 201
{ "device_id": "01J8…", "family_id": "01J8…", "child_profile_id": "01J8…",
  "device_secret": "base64url(32B)",           // ⚠️ فقط یک بار برگردانده می‌شود
  "secret_algorithm": "HMAC-SHA256", "key_version": 1,
  "server_public_key": "…",                     // برای pin کردن امضای سرور (اختیاری)
  "consent": { "version": "1.0", "url": "https://…/consent/fa-IR", "capabilities": [ … ] },
  "heartbeat_interval_sec": 900, "lease_sec": 1200, "presence_model": "push_verified",
  "session_expires_at": null, "paired_at": "2026-09-28T12:35:01Z" }
// 422 PAIRING_CODE_EXPIRED | PAIRING_CODE_INVALID (attempts_left) | PAIRING_CODE_USED
// 409 ALREADY_PAIRED (install_id شناخته‌شده) → 200 با "reissue": true
// 409 DEVICE_LIMIT_REACHED · 429 RATE_LIMITED
```

**`POST /v1/pairing/confirm`** (با Device auth)
```jsonc
{ "consent_version": "1.0", "consented_capabilities": ["presence","battery","device_info","permissions"],
  "child_display_name": "سارا" }
// 200 → { "status": "completed", "paired_at": "…" }  + SSE `pairing.completed` به والد + FCM
```

### ۳.۴ Devices (والد)

| Method | Path | توضیح |
|---|---|---|
| GET | `/v1/devices?status=active` | لیست دستگاه‌ها + وضعیت (کوئری Dashboard) |
| GET | `/v1/devices/{id}` | جزئیات کامل (Device Info) |
| GET | `/v1/devices/{id}/snapshot` | **یک‌جا**: status + battery + connectivity + permissions + settings_version (با ETag) |
| GET | `/v1/devices/{id}/permissions` | وضعیت مجوزها |
| GET | `/v1/devices/{id}/timeline?since=&limit=` | رویدادها (online/offline/battery/permission) |
| GET | `/v1/devices/{id}/tamper-events?status=new&severity=critical` | رویدادهای `[TAMPER]` با چهار فیلد event_type/timestamp/device/status |
| POST | `/v1/devices/{id}/tamper-events/{eventId}/ack` | تغییر status به `ack/resolved/false_alarm` |
| PATCH | `/v1/devices/{id}` | nickname / avatar / child profile |
| POST | `/v1/devices/{id}/logout` | باطل کردن session دستگاه (بدون حذف) |
| **DELETE** | `/v1/devices/{id}` | **Remove Device** ⇒ revoke + purge + unpair command |
| POST | `/v1/devices/{id}/commands` | ارسال فرمان (`refresh_status`, `revoke`, `wipe_local_data`, `update_settings`) |

**`GET /v1/devices/{id}/snapshot` — Response 200**
```jsonc
{
  "device": {
    "id": "01J8…", "nickname": "گوشی سارا", "child_name": "سارا", "avatar_key": "star",
    "model": "Pixel 8", "manufacturer": "Google", "android_release": "16", "android_sdk_int": 36,
    "app_version_name": "0.1.0", "status": "active", "paired_at": "2026-09-28T12:35:01Z",
    "capabilities": { "battery": "FULL", "presence": "FULL", "device_info": "FULL",
                      "permissions": "FULL", "location": "ASSISTED", "screen_time": "ASSISTED",
                      "app_block": "OWNER_ONLY", "web_filter": "ASSISTED" }
  },
  "status": {
    "presence": "online", "presence_reason": null,
    "online_since": "2026-09-28T12:30:00Z", "offline_since": null,
    "last_heartbeat_at": "2026-09-28T12:41:22Z",
    "last_sync_at": "2026-09-28T12:41:22Z",
    "lease_expires_at": "2026-09-28T12:42:52Z",
    "battery": { "level": 74, "status": "discharging", "temperature_c": 31.2, "power_save_mode": false,
                 "is_charging": false, "estimated_minutes_left": 268 },
    "connectivity": { "type": "wifi", "is_roaming": false, "network_country": "FR" },
    "screen_state": "on", "storage_free_mb": 18432, "device_uptime_sec": 291844,
    "app_restart_count": 0
  },
  "permissions": {
    "granted":  ["notifications", "battery_optimization_exempt"],
    "denied":   ["device_admin"],
    "not_requested": ["usage_access", "accessibility", "location_while_using"],
    "unavailable": [],
    "details": [ { "kind": "notifications", "state": "granted", "required": true,
                   "blocking_features": [], "last_checked_at": "2026-09-28T12:41:22Z" }, … ]
  },
  "consent": { "version": "1.0", "granted": ["presence","battery","device_info","permissions"],
               "revoked": [], "consented_at": "2026-09-28T12:35:04Z" },
  "settings_version": 1,
  "server_time": "2026-09-28T12:41:25Z"
}
```

**`DELETE /v1/devices/{id}`**
```jsonc
// Request
{ "purge_telemetry": true, "notify_child": true, "reason": "upgrading_device" }
// 200
{ "device_id": "01J8…", "status": "removed", "removed_at": "…",
  "revoked": { "credentials": true, "sessions": 3, "push_tokens": 1, "pairing_codes": 0 },
  "purged": { "telemetry_events": 1482, "permissions": 15, "device_status": 1 },
  "child_command_queued": "unpair",
  "audit_id": "01J8…" }
```

### ۳. Notifications (والد)

| Method | Path | توضیح |
|---|---|---|
| GET | `/v1/notifications/preferences` | تنظیمات والد per alert-type |
| PUT | `/v1/notifications/preferences` | bulk update toggles/quiet hours/min severity |
| POST | `/v1/notifications/test` | `{type}` ⇒ push آزمایشی برای بررسی UI |
| GET | `/v1/notifications?status=unread&since=` | لیست هشدارها (Phase 8) |
| POST | `/v1/notifications/{id}/read` · `/read-all` | — |

**`PUT /v1/notifications/preferences`**
```jsonc
{ "prefs": [
    { "alert_type": "device_offline",   "enabled": true,  "channel_importance": "HIGH" },
    { "alert_type": "battery_low",      "enabled": true,  "channel_importance": "DEFAULT" },
    { "alert_type": "location_event",   "enabled": true,  "channel_importance": "DEFAULT" },
    { "alert_type": "geofence_event",   "enabled": true,  "channel_importance": "HIGH" },
    { "alert_type": "screen_time_limit","enabled": true,  "channel_importance": "HIGH" },
    { "alert_type": "permission_changed","enabled": true, "channel_importance": "DEFAULT" },
    { "alert_type": "tamper_attempt",   "enabled": true,  "channel_importance": "HIGH" },
    { "alert_type": "sos",              "enabled": true,  "channel_importance": "MAX" } ],
  "quiet_hours": { "enabled": true, "start": "23:00", "end": "07:00",
                   "exceptions": ["sos", "tamper_attempt"] },
  "min_severity": "warning",
  "digest": { "weekly": false } }
```
> Double-gate: سرور بر اساس prefs push می‌سازد یا `muted` ثبت می‌کند؛ کلاینت Parent هم قبل از `notify()` دوباره prefs لوکال را چک می‌کند (جزئیات سند ۱۲ §2.1).

### ۳.۶ Child Device API (با Device auth)

| Method | Path | توضیح | Phase |
|---|---|---|---|
| POST | `/v1/child/heartbeat` | ping + lease renewal + battery/connectivity/screen + queue_size | **1** |
| POST | `/v1/child/sync/device-info` | اطلاعات دستگاه و capabilities | **1** |
| POST | `/v1/child/sync/permissions` | snapshot کامل مجوزها | **1** |
| POST | `/v1/child/events` | batch از `telemetry_events` (idempotent) | **1** |
| POST | `/v1/child/tamper` | batch رویدادهای `[TAMPER]` (severity-based priority, dedupe) | **1** |
| GET | `/v1/child/settings?version=3` | policy فعلی ⇒ `304` اگر تغییری نیست | 2+ |
| POST | `/v1/child/push-token` | ثبت/حذف FCM token | 7 |
| POST | `/v1/child/report/location` | batch مکان | 3 |
| POST | `/v1/child/report/screen-time` | daily + app usage | 2 |
| POST | `/v1/child/sos` | SOS | 8 |
| POST | `/v1/child/geofence-events` | رویداد ورود/خروج | 4 |
| GET | `/v1/child/consents` | فهرست فقط‌خواندنی capabilityهای فعال (برای نمای حریم خصوصی فرزند) — **v0.2: هیچ endpoint لغو/توقف برای فرزند وجود ندارد** | 1 |

**`POST /v1/child/heartbeat`**
```jsonc
// Request
{ "client_ts": "2026-09-28T12:41:20Z",
  "battery": { "level": 74, "status": "discharging", "temperature_c": 31.2, "power_save_mode": false },
  "connectivity": { "type": "wifi", "is_roaming": false },
  "screen_state": "on", "uptime_sec": 291844, "storage_free_mb": 18432,
  "queue_size": 0, "app_version_code": 1, "idempotency_key": "hb-01J8…" }
// Response 200
{ "server_time": "2026-09-28T12:41:22Z", "lease_sec": 1200, "next_baseline_in_sec": 870,
  "ping_channel": "fcm_data_only", "settings_version": 1, "commands": [], "ack_event_ids": ["hb-01J8…"] }
// 410 DEVICE_REMOVED  ·  401 DEVICE_SIGNATURE_INVALID  ·  403 CONSENT_REQUIRED
```

> 🔋 **چرا `next_heartbeat_in_sec` از سرور می‌آید؟** تا بدون انتشار نسخه جدید اپ، فاصله heartbeat را تنظیم کنیم (مثلاً وقتی باتری کم است ⇒ ۱۸۰ ثانیه، وقتی والد صفحه Dashboard را باز کرده ⇒ ۲۰ ثانیه).

---

## ۴. Realtime — SSE

| کانال | مسیر | Auth |
|---|---|---|
| والد | `GET /v1/events?family_id=…` | Bearer |
| کودک | `GET /v1/child/events/stream` | Device signature (یا long-lived SSE token) |

```http
GET /v1/events?family_id=01J8… HTTP/1.1
Authorization: Bearer eyJ…
Accept: text/event-stream
Last-Event-ID: 01J8ZK6Q9V            ← بعد از reconnect، رویدادهای از دست رفته دوباره ارسال می‌شوند
```
```
retry: 5000

event: device.online
id: 01J8ZK7A1B
data: {"family_id":"01J8…","device_id":"01J8…","at":"2026-09-28T12:30:00Z","battery":{"level":74}}

event: battery.updated
id: 01J8ZK7A2C
data: {"device_id":"01J8…","level":73,"status":"discharging","at":"2026-09-28T12:41:22Z"}

: keep-alive comment هر ۲۵ ثانیه
```

### ۴.۱ کاتالوگ رویدادها

| رویداد | کانال | Phase | payload کلیدی |
|---|---|---|---|
| `pairing.code_created` / `pairing.redeemed` / `pairing.completed` | والد | **1** | pairing_id, device_id |
| `device.added` / `device.removed` / `device.updated` | والد | **1** | device_id |
| `device.online` / `device.offline` | والد | **1** | device_id, presence_reason, at |
| `battery.updated` | والد | **1** | level, status |
| `battery.low` | والد | **1** | level ≤ 20 ⇒ Alert |
| `connectivity.changed` | والد | **1** | type |
| `permission.changed` / `permission.revoked` | والد | **1** | kind, old_state, new_state |
| `sync.completed` | والد | **1** | last_sync_at |
| `device.uninstalled` / `device.tampered` | والد | **1** | reason |
| `tamper.detected` | والد | **1** | type, severity, status, protection_mode, evidence(redacted) |
| `consent.updated` (فقط والد-initiated) | والد | **1** | capability, granted, changed_by=parent |
| `screen_time.updated` / `screen_time.limit_reached` / `bedtime.started` | والد | 2 | minutes, limit |
| `location.updated` / `location.stale` | والد | 3 | accuracy, provider (**بدون مختصات در FCM**، فقط در SSE رمزنگاری‌نشده روی TLS) |
| `geofence.entered` / `geofence.exited` | والد | 4 | zone_id, name |
| `app.installed` / `app.removed` / `app.blocked` | والد | 5 | package, label |
| `web.blocked` | والد | 6 | domain, rule |
| `sos.triggered` / `sos.acknowledged` | والد + کودک | 8 | — |
| `settings.updated` / `device.unpair` / `session.revoked` | کودک | **1** | version, reason |
| `command.issued` | کودک | 2+ | command, params |

### ۴.۲ Fallback وقتی SSE ممکن نیست

`GET /v1/devices/{id}/snapshot?since_version=<etag>` هر ۶۰ ثانیه (فقط وقتی صفحه Dashboard باز است) + FCM data message برای هشدارهای critical. کلاینت به‌صورت خودکار بین `SSE → long-poll → periodic-poll` تنزل می‌کند و در UI نشان می‌دهد «به‌روزرسانی زنده: فعال/غیرفعال».

---

## ۵. Rate Limiting (Redis — sliding window + token bucket)

| مسیر | حد | کلید | جریمه |
|---|---|---|---|
| `POST /auth/login` | ۵ در دقیقه، ۲۰ در ساعت | IP + email hash | قفل حساب ۱۵ دقیقه بعد از ۱۰ خطا |
| `POST /auth/register` | ۳ در ساعت | IP | 429 |
| `POST /auth/refresh` | ۳۰ در دقیقه | user_id | 429 + reuse detection |
| `POST /auth/password/forgot` | ۳ در ساعت | IP + email | 429 (پاسخ همیشه ۲۰۰ برای جلوگیری از enumeration) |
| `POST /pairing/codes` | ۵ در دقیقه، ۲۰ در روز | family_id | 429 |
| `POST /pairing/exchange` | ۵ در دقیقه | IP + install_id | 429 + `pairing.invalidated` |
| `POST /child/heartbeat` | ۲۰ در دقیقه | device_id | نادیده گرفته می‌شود (۲۰۴ با `Retry-After`) |
| `POST /child/events` | ۶۰ در دقیقه، ۵ MB/ساعت | device_id | 429 + client backoff |
| همه `/v1/*` با Bearer | ۲۴۰ در دقیقه | user_id | 429 |
| همه `/v1/*` | ۶۰ در دقیقه | IP | 429 |
| `POST /child/sos` | **بدون محدودیت** (فقط abuse detection) | device_id | — |

**Anti-enumeration:** پاسخ `forgot password` و `register` یکسان است؛ `X-RateLimit-*` برای مسیرهای حساس حذف می‌شود تا oracle نشود.

---

## ۶. `BackendGateway` — نقطه تعویض‌پذیری (Firebase ↔ سرور اختصاصی)

```kotlin
// domain/gateway/BackendGateway.kt  — تنها وابستگی لایه Domain به بیرون
interface BackendGateway {
    suspend fun register(req: RegisterRequest): Result<AuthBundle>
    suspend fun login(req: LoginRequest): Result<AuthBundle>
    suspend fun refresh(token: String): Result<AuthBundle>
    suspend fun logout(): Result<Unit>

    suspend fun createPairingCode(req: PairingRequest): Result<PairingTicket>
    suspend fun observePairingStatus(pairingId: String): Flow<PairingState>
    suspend fun exchangePairingCode(req: RedeemRequest): Result<DeviceCredentials>
    suspend fun confirmPairing(req: ConfirmRequest): Result<PairingCompletion>

    suspend fun fetchDevices(): Result<List<Device>>
    suspend fun fetchDeviceSnapshot(deviceId: DeviceId): Result<DeviceSnapshot>
    suspend fun fetchPermissions(deviceId: DeviceId): Result<List<PermissionState>>
    suspend fun renameDevice(deviceId: DeviceId, nickname: String): Result<Device>
    suspend fun removeDevice(deviceId: DeviceId, opts: RemoveOptions): Result<RemoveResult>
    suspend fun logoutDevice(deviceId: DeviceId): Result<Unit>
    suspend fun deviceTimeline(deviceId: DeviceId, since: Instant?): Result<List<TimelineEvent>>

    suspend fun sendHeartbeat(payload: HeartbeatPayload): Result<HeartbeatAck>
    suspend fun sendDeviceInfo(info: DeviceInfoPayload): Result<Unit>
    suspend fun sendPermissions(list: List<PermissionSnapshot>): Result<Unit>
    suspend fun fetchSettings(version: Int): Result<SettingsEnvelope>

    fun observeEvents(channel: EventChannel): Flow<ServerEvent>     // SSE
    suspend fun registerPushToken(token: String): Result<Unit>
}
```

| پیاده‌سازی | کجا |
|---|---|
| `HttpBackendGateway` | `core/network` — Ktor Client (production) |
| `FirebaseBackendGateway` | `core/network-firebase` — Auth + Firestore (در صورت تصمیم شما در آینده) |
| `InMemoryBackendGateway` | `core/testing` — تست UI/ViewModel بدون سرور + Dev Menu «Mock mode» |

**قاعده:** Domain و Presentation **فقط** `BackendGateway` را می‌شناسند ⇒ تعویض backend هیچ تغییری در UI/ViewModel ایجاد نمی‌کند. همین الگو برای `PushProvider` (FCM / Noop / Local) و `ClockProvider`.

---

## ۷. قرارداد DTO مشترک (`shared-contract`)

ماژول `shared-contract` (Kotlin JVM) شامل `@Serializable` data classها و enumهاست که **هم کلاینت و هم سرور** از آن استفاده می‌کنند ⇒ یک تغییر Schema در یک جا، و خطای «field اشتباه» به صفر می‌رسد.

```
shared-contract/
├── dto/{AuthDto, DeviceDto, StatusDto, PairingDto, TelemetryDto, EventDto, ProblemDto}.kt
├── enums/{Presence, BatteryStatus, Connectivity, PermissionKind, PermissionState, AlertType}.kt
└── ApiConstants.kt        # مسیرها، نسخه‌ها، limits، event names
```

---

## ۸. OpenAPI و مستندسازی زنده

Ktor 3.6 با `ktor-server-openapi` ⇒ spec در زمان اجرا تولید می‌شود:
- `GET /docs/openapi.json`
- `GET /docs` (Swagger UI) — فقط در dev/staging
- تست قرارداد: یک CI job که spec را با `shared-contract` مقایسه می‌کند ⇒ API drift غیرممکن.

---

## ۹. نکات عملی برای تست روی دو گوشی

| موضوع | راه‌حل |
|---|---|
| دسترسی گوشی به سرور روی PC | گوشی و PC روی یک Wi-Fi ⇒ `BASE_URL=http://192.168.1.20:8080/v1` (در Dev Menu قابل تغییر است، بدون rebuild) |
| cleartext در debug | `network_security_config.xml` با `<domain-config cleartextTrafficPermitted="true">` فقط برای IPهای خصوصی + فقط در `debug` source set |
| اگر LAN ممکن نبود | Cloudflare Tunnel / ngrok ⇒ `https://xyz.trycloudflare.com` (بدون خرید دامنه) |
| بدون سرور | Dev Menu → «Mock mode» ⇒ `InMemoryBackendGateway` |
| مشاهده درخواست‌ها | Chucker (فقط debug) + `X-Request-Id` در لاگ Logcat و سرور |
