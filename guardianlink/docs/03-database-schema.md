# 03 — Database Schema

## ۱. اصول طراحی

| اصل | توضیح |
|---|---|
| **Multi-tenant** | هر جدول داده‌محور `family_id` دارد + **PostgreSQL Row-Level Security** به‌عنوان لایه سوم دفاع |
| **UUIDv7** برای PK | زمان‌مرتب ⇒ index friendly، بدون نشت اطلاعات ترتیب، تولید سمت کلاینت/سرور ممکن |
| **Soft delete** | `deleted_at` برای `devices`, `family_members` (Audit و Revocation) |
| **جداسازی داده پرحجم** | `telemetry_events`, `locations`, `app_usage` جداول جدا با **Retention policy** |
| **رمزنگاری ستون‌های حساس** | مختصات مکان، `device_secret_hash` (فقط hash)، داده‌های احراز هویت |
| **Optimistic concurrency** | `version` روی `device_settings` |
| **Migration** | Flyway؛ هر Phase دقیقاً یک یا دو migration جدید (بدون تغییر migrationهای قبلی) |
| **زمان** | همه `timestamptz` و **UTC**؛ منبع حقیقت = سرور (ADR-007) |

### نمودار موجودیت‌ها (ERD خلاصه)

```
users ──< family_members >── families
                              │ 1
                              ├────< devices >──── device_credentials
                              │         │ 1        device_status (1:1)
                              │         ├────< device_sessions
                              │         ├────< permissions
                              │         ├────< telemetry_events
                              │         ├────< locations            (Phase 3)
                              │         ├────< screen_time_daily    (Phase 2)
                              │         ├────< app_usage            (Phase 2)
                              │         ├────< geofence_events      (Phase 4)
                              │         └────< sos_events           (Phase 8)
                              ├────< geofences                      (Phase 4)
                              ├────< device_settings (per device)
                              ├────< data_consents
                              ├────< notifications                  (Phase 7)
                              ├────< notification_preferences
                              ├────< pairing_codes
                              └────< audit_logs
refresh_tokens ──> users          push_tokens ──> users | devices
family_deks ──> families          outbox (global)
```

---

## ۲. جداول Phase 1 (هسته) — ۱۴ جدول

### 2.1 `users` — حساب والد (و در آینده حساب فرزند)

| field | type | null | توضیح |
|---|---|---|---|
| id | uuid | ✗ | **PK** (UUIDv7) |
| email | citext | ✗ | **UNIQUE** (partial where deleted_at is null) |
| password_hash | text | ✓ | Argon2id (`$argon2id$v=19$m=…`) — NULL برای حساب‌های بدون رمز |
| display_name | text(80) | ✓ | |
| locale | text(10) | ✗ | default `fa-IR` |
| role | user_role enum | ✗ | `parent`, `child`, `support` |
| status | user_status enum | ✗ | `pending_verification`, `active`, `locked`, `deactivated`, `deleted` |
| email_verified_at | timestamptz | ✓ | |
| failed_login_count | smallint | ✗ | default 0 |
| locked_until | timestamptz | ✓ | قفل نرم بعد از ۱۰ خطا (۱۵ دقیقه) |
| last_login_at | timestamptz | ✓ | |
| totp_secret_enc | bytea | ✓ | 2FA (Phase 9) |
| created_at / updated_at | timestamptz | ✗ | default now() |
| deleted_at | timestamptz | ✓ | soft delete (GDPR: hard purge بعد از ۳۰ روز) |

**Indexes:** `uniq_users_email` UNIQUE (email) WHERE deleted_at IS NULL · `ix_users_status` (status) · `ix_users_created_at`

---

### 2.2 `families` — واحد tenancy

| field | type | null | توضیح |
|---|---|---|---|
| id | uuid | ✗ | **PK** |
| name | text(60) | ✗ | مثال: «خانواده رضایی» |
| owner_user_id | uuid | ✗ | **FK → users(id) ON DELETE RESTRICT** |
| plan | text(20) | ✗ | default `free` (آماده برای آینده) |
| max_children | smallint | ✗ | default 6 |
| max_devices | smallint | ✗ | default 10 |
| timezone | text(40) | ✗ | default `Asia/Tehran` |
| status | family_status enum | ✗ | `active`, `suspended`, `deleted` |
| created_at / updated_at | timestamptz | ✗ | |

**Indexes:** `ix_families_owner` (owner_user_id) · `ix_families_status`

---

### 2.3 `family_members` — رابطه والد/فرزند با خانواده

| field | type | null | توضیح |
|---|---|---|---|
| id | uuid | ✗ | **PK** |
| family_id | uuid | ✗ | **FK → families(id) ON DELETE CASCADE** |
| user_id | uuid | ✓ | **FK → users(id)** — NULL برای `child_profile` بدون حساب |
| child_profile_id | uuid | ✓ | **FK → child_profiles(id)** |
| role | member_role enum | ✗ | `owner_parent`, `parent`, `guardian`, `child` |
| permissions | text[] | ✗ | default `{view,status}`؛ مقادیر: `view`, `manage_devices`, `manage_limits`, `manage_location`, `manage_alerts`, `admin` |
| invited_by | uuid | ✓ | FK → users(id) (چند والد در آینده) |
| status | member_status enum | ✗ | `active`, `invited`, `removed` |
| joined_at / removed_at | timestamptz | ✓ | |
| created_at / updated_at | timestamptz | ✗ | |

**Constraints:** `uniq_member_user` UNIQUE (family_id, user_id) · `chk_member_identity` CHECK (user_id IS NOT NULL OR child_profile_id IS NOT NULL)
**Indexes:** `ix_fm_family` (family_id, status) · `ix_fm_user` (user_id) · `ix_fm_child` (child_profile_id)

---

### 2.4 `child_profiles` — هویت فرزند (جدا از دستگاه)

| field | type | null | توضیح |
|---|---|---|---|
| id | uuid | ✗ | **PK** |
| family_id | uuid | ✗ | FK → families ON DELETE CASCADE |
| user_id | uuid | ✓ | FK → users — وقتی فرزند بزرگ شد و حساب خودش را خواست |
| display_name | text(60) | ✗ | «سارا» |
| avatar_key | text(40) | ✓ | یکی از آواتارهای از پیش تعریف‌شده (بدون آپلود عکس در MVP) |
| birth_year | smallint | ✓ | برای سیاست‌های سنی و COPPA/GDPR-K |
| default_device_id | uuid | ✓ | FK → devices (ست می‌شود بعد از pairing) |
| created_at / updated_at / deleted_at | timestamptz | | |

**Indexes:** `ix_cp_family` (family_id) WHERE deleted_at IS NULL · `uniq_cp_default_device` UNIQUE (default_device_id)

> 🔎 **چرا جدا از `devices`؟** چون یک فرزند ممکن است بعداً تبلت + گوشی داشته باشد، یا گوشی‌اش عوض شود ولی پروفایل و تاریخچه‌اش بماند. این از همین Phase 1 ساخته می‌شود تا بعداً migration سنگین لازم نشود.

---

### 2.5 `pairing_codes` — کد یک‌بارمصرف Pairing

| field | type | null | توضیح |
|---|---|---|---|
| id | uuid | ✗ | **PK** |
| family_id | uuid | ✗ | FK → families ON DELETE CASCADE |
| created_by_user_id | uuid | ✗ | FK → users |
| code_hash | bytea | ✗ | **SHA-256(code)** — کد خام هرگز ذخیره نمی‌شود |
| code_prefix | text(2) | ✗ | برای نمایش «کد 7Q…» در لیست (بدون نشت) |
| attempt_count | smallint | ✗ | default 0؛ بیش از ۱۰ ⇒ `invalidated` |
| max_attempts | smallint | ✗ | default 10 |
| expires_at | timestamptz | ✗ | **default now() + interval '10 minutes'** |
| redeemed_at | timestamptz | ✓ | |
| redeemed_by_device_id | uuid | ✓ | FK → devices |
| status | pairing_status enum | ✗ | `pending`, `redeemed`, `expired`, `revoked`, `invalidated` |
| client_ip | inet | ✓ | برای Audit (بدون PII حساس) |
| created_at | timestamptz | ✗ | |

**Indexes:** `uniq_pairing_code_hash` UNIQUE (code_hash) WHERE status='pending' · `ix_pairing_family` (family_id, status, expires_at DESC) · `ix_pairing_expires` (expires_at) WHERE status='pending' (برای پاک‌سازی دوره‌ای)

---

### 2.6 `devices` — دستگاه فرزند

| field | type | null | توضیح |
|---|---|---|---|
| id | uuid | ✗ | **PK** — `device_id` یکتا (سمت کلاینت در SecureStore) |
| family_id | uuid | ✗ | FK → families ON DELETE CASCADE |
| child_profile_id | uuid | ✓ | FK → child_profiles ON DELETE SET NULL |
| pairing_code_id | uuid | ✓ | FK → pairing_codes (برای Audit زنجیره pairing) |
| install_id | uuid | ✗ | **UNIQUE** — تصادفی در اولین اجرا (تشخیص reinstall) |
| device_key_id | text(64) | ✗ | **UNIQUE** — اثر انگشت کلید عمومی Keystore (`sha256(pubkey)`) |
| public_key | bytea | ✓ | کلید عمومی Ed25519/EC (اختیاری؛ در Phase 1 از HMAC استفاده می‌شود) |
| nickname | text(60) | ✓ | «گوشی سارا» |
| avatar_key | text(40) | ✓ | |
| manufacturer / model / device / brand | text | ✗ | از `Build.*` |
| android_sdk_int | smallint | ✗ | |
| android_release | text(10) | ✗ | |
| app_version_name | text(20) | ✗ | |
| app_version_code | integer | ✗ | |
| locale / timezone | text | ✗ | |
| is_emulator | boolean | ✗ | default false |
| is_rooted | boolean | ✗ | default false |
| play_integrity_verdict | text(30) | ✓ | `MEETS_DEVICE_INTEGRITY` … (Phase 9) |
| capabilities | jsonb | ✗ | `{"battery":"FULL","location":"ASSISTED","app_block":"OWNER_ONLY",…}` (ADR-003) |
| consent_version | text(10) | ✓ | نسخه سند رضایتی که فرزند تأیید کرده |
| consented_at | timestamptz | ✓ | |
| status | device_status enum | ✗ | `pairing_pending`, `active`, `suspended`, `removed`, `uninstalled` |
| last_seen_at | timestamptz | ✓ | آخرین heartbeat موفق |
| paired_at | timestamptz | ✓ | |
| removed_at | timestamptz | ✓ | |
| removed_by_user_id | uuid | ✓ | FK → users |
| created_at / updated_at | timestamptz | ✗ | |

**Indexes:** `uniq_devices_install_id` UNIQUE (install_id) · `uniq_devices_key_id` UNIQUE (device_key_id) · `ix_devices_family_status` (family_id, status) — کوئری اصلی Dashboard · `ix_devices_child` (child_profile_id) · `ix_devices_last_seen` (last_seen_at)

---

### 2.7 `device_credentials` — راز دستگاه (۱:1 با devices)

| field | type | null | توضیح |
|---|---|---|---|
| device_id | uuid | ✗ | **PK + FK → devices ON DELETE CASCADE** |
| family_id | uuid | ✗ | denormalized برای RLS |
| secret_hash | bytea | ✗ | SHA-256(device_secret) — راز خام هرگز ذخیره نمی‌شود |
| algorithm | text(20) | ✗ | `HMAC-SHA256` / `Ed25519` |
| key_version | smallint | ✗ | default 1؛ برای rotation |
| issued_at | timestamptz | ✗ | |
| rotated_at | timestamptz | ✓ | |
| revoked_at | timestamptz | ✓ | **Revocation mechanism** |
| revoked_reason | text(40) | ✓ | `parent_removed`, `compromised`, `reinstall`, `expired` |
| last_used_at | timestamptz | ✓ | |
| failed_auth_count | smallint | ✗ | default 0 → قفل بعد از ۲۰ (هشدار `SECURITY`) |

**Indexes:** `ix_dc_family` (family_id) · `ix_dc_revoked` (revoked_at) WHERE revoked_at IS NULL

---

### 2.8 `device_status` — وضعیت لحظه‌ای (۱:۱، جدول باریک برای خواندن سریع)

| field | type | null | توضیح |
|---|---|---|---|
| device_id | uuid | ✗ | **PK + FK → devices ON DELETE CASCADE** |
| family_id | uuid | ✗ | denormalized برای RLS |
| presence | presence enum | ✗ | `online`, `offline`, `stale`, `uninstalled`, `unknown` |
| presence_reason | text(30) | ✓ | `grace_expired`, `connectivity_lost`, `device_off`, `removed` |
| online_since | timestamptz | ✓ | |
| offline_since | timestamptz | ✓ | |
| lease_expires_at | timestamptz | ✗ | `last_heartbeat + 90s` ⇒ موتور Online/Offline |
| last_heartbeat_at | timestamptz | ✓ | |
| last_sync_at | timestamptz | ✓ | آخرین همگام‌سازی موفق داده (نه فقط ping) |
| battery_level | smallint | ✓ | 0..100 — **CHECK (battery_level BETWEEN 0 AND 100)** |
| battery_status | battery_status enum | ✓ | `unknown`, `charging`, `discharging`, `full`, `not_charging` |
| battery_temperature_c | numeric(4,1) | ✓ | |
| is_power_save_mode | boolean | ✓ | |
| connectivity_type | connectivity enum | ✓ | `wifi`, `cellular`, `ethernet`, `none`, `unknown` |
| is_roaming | boolean | ✓ | |
| network_country | text(4) | ✓ | |
| screen_state | screen_state enum | ✓ | `on`, `off`, `locked`, `unknown` |
| app_in_foreground | boolean | ✓ | (Phase 2 استفاده می‌شود) |
| storage_free_mb | integer | ✓ | |
| device_uptime_sec | bigint | ✓ | تشخیص ریستارت دستگاه |
| app_restart_count | integer | ✗ | default 0 |
| updated_at | timestamptz | ✗ | |

**Indexes:** `ix_ds_family_presence` (family_id, presence) · `ix_ds_lease` (lease_expires_at) — برای sweeper job که `online → stale` می‌کند

---

### 2.9 `device_sessions` — Session management

| field | type | null | توضیح |
|---|---|---|---|
| id | uuid | ✗ | **PK** |
| device_id | uuid | ✓ | FK → devices ON DELETE CASCADE (جلسه دستگاه فرزند) |
| user_id | uuid | ✓ | FK → users ON DELETE CASCADE (جلسه والد) |
| family_id | uuid | ✗ | |
| session_kind | session_kind enum | ✗ | `parent_app`, `child_device`, `backend_service` |
| device_fingerprint | text(120) | ✓ | hash(model+os+app_version+install_id) — نه IMEI |
| ip_address | inet | ✓ | |
| user_agent | text(255) | ✓ | |
| issued_at | timestamptz | ✗ | |
| last_seen_at | timestamptz | ✗ | |
| expires_at | timestamptz | ✗ | |
| revoked_at | timestamptz | ✓ | |
| revoked_reason | text(40) | ✓ | `user_logout`, `parent_revoke`, `password_changed`, `device_removed`, `token_reuse_detected` |

**Constraints:** `chk_session_owner` CHECK ((device_id IS NULL) <> (user_id IS NULL))
**Indexes:** `ix_dsess_device` (device_id, revoked_at) · `ix_dsess_user` (user_id, revoked_at) · `ix_dsess_expires` (expires_at)

---

### 2.10 `refresh_tokens` — والد (چرخشی با Reuse Detection)

| field | type | null | توضیح |
|---|---|---|---|
| id | uuid | ✗ | **PK** |
| user_id | uuid | ✗ | FK → users ON DELETE CASCADE |
| family_id | uuid | ✓ | |
| token_hash | bytea | ✗ | SHA-256(token) |
| parent_token_id | uuid | ✓ | self-FK → زنجیره rotation |
| device_fingerprint | text(120) | ✗ | binding به دستگاه والد |
| ip_address | inet | ✓ | |
| issued_at | timestamptz | ✗ | |
| expires_at | timestamptz | ✗ | issued + 30d |
| used_at | timestamptz | ✓ | |
| revoked_at | timestamptz | ✓ | |
| reuse_detected_at | timestamptz | ✓ | ⇒ کل زنجیره revoke + هشدار `[SECURITY]` + ایمیل به والد |

**Indexes:** `uniq_rt_hash` UNIQUE (token_hash) · `ix_rt_user` (user_id) WHERE revoked_at IS NULL · `ix_rt_parent` (parent_token_id)

---

### 2.11 `permissions` — وضعیت مجوزهای دستگاه فرزند (بخش ۹ MVP)

| field | type | null | توضیح |
|---|---|---|---|
| id | uuid | ✗ | **PK** |
| device_id | uuid | ✗ | FK → devices ON DELETE CASCADE |
| family_id | uuid | ✗ | |
| kind | permission_kind enum | ✗ | `notifications`, `battery_optimization_exempt`, `device_admin`, `device_owner`, `usage_access`, `accessibility`, `location_while_using`, `location_background`, `vpn`, `private_dns`, `overlay`, `exact_alarm`, `all_files`, `camera`, `microphone` |
| state | permission_state enum | ✗ | `granted`, `denied`, `not_requested`, `unavailable`, `revoked_by_user`, `restricted` |
| required | boolean | ✗ | آیا برای قابلیت‌های فعلی لازم است |
| blocking_features | text[] | ✗ | `{"screen_time","app_block"}` — برای پیام راهنما |
| requested_at / granted_at / revoked_at | timestamptz | ✓ | |
| last_checked_at | timestamptz | ✗ | |
| note | text(160) | ✓ | |

**Constraints:** `uniq_perm_device_kind` UNIQUE (device_id, kind)
**Indexes:** `ix_perm_family_state` (family_id, state) · `ix_perm_device` (device_id, last_checked_at DESC)

> در Phase 1 این جدول **snapshot** است (یک ردیف per kind). تاریخچه تغییرات در `audit_logs` و `telemetry_events` ثبت می‌شود تا «Permission Revoked» در Phase 7 قابل تشخیص باشد.

---

### 2.12 `telemetry_events` — رویدادهای وضعیت (Battery/Connectivity/Permission/Restart)

| field | type | null | توضیح |
|---|---|---|---|
| id | bigserial | ✗ | **PK** (حجم بالا ⇒ bigserial نه uuid) |
| family_id | uuid | ✗ | |
| device_id | uuid | ✗ | FK → devices ON DELETE CASCADE |
| type | telemetry_type enum | ✗ | `battery`, `connectivity`, `permission_changed`, `device_restart`, `app_restart`, `sync_completed`, `uninstall_imminent`, `heartbeat_missed` |
| payload | jsonb | ✗ | `{"level":74,"status":"DISCHARGING","temp_c":31.2}` |
| client_ts | timestamptz | ✓ | زمان دستگاه (فقط تشخیصی) |
| server_ts | timestamptz | ✗ | default now() — **منبع حقیقت** |
| idempotency_key | text(64) | ✓ | UNIQUE per device ⇒ ارسال مجدد تکراری نمی‌شود |
| ingest_batch_id | uuid | ✓ | |

**Indexes:** `ix_te_device_ts` (device_id, server_ts DESC) · `ix_te_family_type_ts` (family_id, type, server_ts DESC) · `uniq_te_idem` UNIQUE (device_id, idempotency_key) · `ix_te_ts` (server_ts) برای retention
**Retention:** ۳۰ روز (سپس aggregate در `telemetry_daily`)

---

### 2.13 `audit_logs` — لاگ حسابرسی (بند ۶ و ۱۹ پرامپت)

| field | type | null | توضیح |
|---|---|---|---|
| id | bigserial | ✗ | **PK** |
| family_id | uuid | ✓ | NULL برای رویدادهای سطح پلتفرم |
| actor_type | actor_type enum | ✗ | `parent`, `child_device`, `system`, `admin` |
| actor_id | uuid | ✓ | user_id یا device_id |
| action | text(60) | ✗ | `auth.login`, `pairing.code_created`, `pairing.redeemed`, `device.removed`, `device.revoked`, `permission.changed`, `security.signature_failed` |
| target_type | text(40) | ✓ | `device`, `user`, `family`, `pairing_code` |
| target_id | uuid | ✓ | |
| category | audit_category enum | ✗ | `AUTH`, `PAIRING`, `DEVICE`, `LOCATION`, `SCREEN_TIME`, `APP_MGMT`, `WEB_SAFETY`, `NOTIFICATION`, `SECURITY`, `PRIVACY`, `SOS` |
| result | audit_result enum | ✗ | `success`, `failure`, `denied`, `partial` |
| reason_code | text(40) | ✓ | `code_expired`, `rate_limited`, `token_reuse` |
| ip_address | inet | ✓ | |
| user_agent | text(255) | ✓ | |
| app_version | text(20) | ✓ | |
| metadata | jsonb | ✓ | **فقط** داده غیرحساس و redacted |
| created_at | timestamptz | ✗ | default now() — **immutable (بدون UPDATE/DELETE در سطح اپ)** |

**Indexes:** `ix_al_family_ts` (family_id, created_at DESC) · `ix_al_category_ts` (category, created_at DESC) · `ix_al_actor` (actor_type, actor_id, created_at DESC) · `ix_al_action` (action)
**Retention:** ۳۶۵ روز (و hard-delete خودکار). **هیچ password/token/مختصات دقیقی در metadata نمی‌آید** (Redactor در `core/logging` + validator سمت سرور).

---

### 2.14 `outbox` + `push_tokens`

**`outbox`**

| field | type | null |
|---|---|---|
| id | bigserial | **PK** |
| aggregate_type | text(30) | ✗ (`device`, `pairing`, `alert`) |
| aggregate_id | uuid | ✗ |
| family_id | uuid | ✗ |
| event_type | text(60) | ✗ (`device.online`, `battery.low`, `pairing.completed`) |
| payload | jsonb | ✗ |
| channels | text[] | ✗ (`{sse,fcm}`) |
| status | outbox_status enum | ✗ (`pending`, `sent`, `failed`, `dropped`) |
| attempts | smallint | ✗ default 0 |
| available_at | timestamptz | ✗ |
| created_at / sent_at | timestamptz | |

**Indexes:** `ix_outbox_pending` (status, available_at) WHERE status='pending' · `ix_outbox_family` (family_id, created_at DESC)

**`push_tokens`**

| field | type | null |
|---|---|---|
| id | uuid | **PK** |
| family_id | uuid | ✗ |
| user_id | uuid | ✓ FK |
| device_id | uuid | ✓ FK |
| provider | text(10) | ✗ `fcm` |
| token_hash | bytea | ✗ UNIQUE (token خام ذخیره نمی‌شود؛ برای ارسال از vault/cache استفاده می‌شود) |
| token_enc | bytea | ✗ رمزنگاری‌شده با family DEK |
| platform | text(10) | ✗ `android` |
| locale | text(10) | ✓ |
| created_at / last_seen_at | timestamptz | |
| revoked_at | timestamptz | ✓ |

**Indexes:** `uniq_push_token` UNIQUE (provider, token_hash) · `ix_push_user` (user_id) WHERE revoked_at IS NULL · `ix_push_device` (device_id)

---

### 2.15 `data_consents` — رضایت و افشا (ثبت در لحظه اتصال؛ مدیریت با والد)

| field | type | null | توضیح |
|---|---|---|---|
| id | uuid | **PK** | |
| family_id | uuid | ✗ | |
| device_id | uuid | ✗ | FK → devices |
| subject_type | text(20) | ✗ | `child` (صاحب داده) |
| capability | text(30) | ✗ | `presence`, `battery`, `device_info`, `permissions`, `location`, `screen_time`, `app_usage`, `web_filter`, `geofence`, `sos` |
| consent_version | text(10) | ✗ | نسخه متن رضایت‌نامه |
| granted | boolean | ✗ | |
| granted_by | text(20) | ✗ | `child`, `parent`, `both` |
| granted_at | timestamptz | ✗ | |
| revoked_at | timestamptz | ✓ | **فقط توسط والد/admin ست می‌شود (v0.2)** ⇒ سرور دریافت داده آن capability را رد می‌کند (نه فقط UI) |
| note | text(160) | ✓ | |

**Constraints:** `uniq_consent` UNIQUE (device_id, capability, consent_version)
**Indexes:** `ix_consent_family` (family_id) · `ix_consent_device_granted` (device_id, granted)

---

### 2.16 `family_deks` — کلید رمزنگاری هر خانواده (Envelope encryption)

| field | type | null |
|---|---|---|
| family_id | uuid | **PK** FK → families ON DELETE CASCADE |
| wrapped_dek | bytea | ✗ — DEK رمزنگاری‌شده با **Master Key** (AES-256-GCM) |
| algorithm | text(30) | ✗ `AES-256-GCM` |
| key_version | smallint | ✗ |
| created_at / rotated_at | timestamptz | |

**کاربرد:** رمزنگاری ستون‌های حساس (`locations.lat/lng`, `geofences.center`, `push_tokens.token_enc`) ⇒ نشت دیتابیس بدون Master Key بی‌فایده است. Master Key در Phase 1 از environment variable و در Phase 9 از KMS/Vault خوانده می‌شود.

---

### 2.17 `tamper_events` — رویدادهای Anti-Tamper (هسته Phase 1 v2)

| field | type | null | توضیح |
|---|---|---|---|
| id | bigserial | ✗ | **PK** |
| family_id | uuid | ✗ | FK-less (RLS) برای سرعت؛ integrity با device join |
| device_id | uuid | ✗ | **FK → devices(id) ON DELETE CASCADE** |
| type | tamper_type enum | ✗ | ۱۷ نوع (§5) |
| severity | tamper_severity enum | ✗ | `info`, `warning`, `critical` |
| status | tamper_status enum | ✗ | `new`, `ack`, `resolved`, `false_alarm` — چرخه رسیدگی والد |
| evidence | jsonb | ✗ | **redacted**: `{old_state, new_state, source:'watchdog|admin_receiver|server', boot_id, …}` |
| protection_mode | text(4) | ✗ | `M0/M1/M2/M3` در لحظه رویداد (برای تحلیل اینکه پیشگیری ممکن بود یا نه) |
| client_ts | timestamptz | ✓ | زمان دستگاه (فقط تشخیصی) |
| server_ts | timestamptz | ✗ | default now() — **timestamp رسمی رویداد** |
| dedupe_key | text(64) | ✓ | UNIQUE per device ⇒ ارسال مجدد تکراری نمی‌شود |
| ack_by_user_id | uuid | ✓ | FK → users |
| ack_at | timestamptz | ✓ | |
| notification_id | uuid | ✓ | FK → notifications (در Phase 8 لینک می‌شود) |
| created_at | timestamptz | ✗ | |

**Indexes:** `ix_tamper_family_status_ts` (family_id, status, server_ts DESC) — کوئری اصلی UI · `ix_tamper_device_ts` (device_id, server_ts DESC) · `uniq_tamper_dedupe` UNIQUE (device_id, dedupe_key) · `ix_tamper_type_ts` (type, server_ts DESC)
**Retention:** ۳۶۵ روز (هم‌رده audit).
**قاعده:** هر رویداد TAMPER هم‌زمان یک ردیف `audit_logs(category='TAMPER')` می‌سازد (single writer server-side) ⇒ یک منبع حقیقت برای ممیزی.

> **`notification_preferences` و `notifications`:** جدول `notification_preferences` در **همان V3 (Phase 1)** ساخته می‌شود (چون هشدارهای Tamper/Offline از P1 وجود دارند و Toggleها باید از روز اول کار کنند)؛ جدول `notifications` (لیست read/unread والد) در Phase 8 اضافه می‌شود.

---

## ۳. جداول Phaseهای بعد (اکنون طراحی، بعداً migration)

### 3.1 `screen_time_daily` (Phase 2)

| field | type |
|---|---|
| id | uuid **PK** |
| device_id / family_id | uuid **FK** |
| day_date | date (در timezone خانواده) |
| active_minutes | integer |
| foreground_minutes | integer |
| unlock_count | integer |
| app_count | integer |
| limit_minutes | integer NULL (از device_settings snapshot) |
| limit_exceeded_at | timestamptz NULL |
| source | enum `usage_stats`, `screen_events`, `mixed` |
| created_at / updated_at | timestamptz |

**Indexes:** `uniq_st_daily` UNIQUE (device_id, day_date) · `ix_st_family_date` (family_id, day_date DESC)

### 3.2 `app_usage` (Phase 2)

| field | type |
|---|---|
| id | bigserial **PK** |
| device_id / family_id | uuid |
| package_name | text(150) |
| app_label | text(120) |
| category | text(30) (`social`, `game`, `education`, `browser`, …) |
| day_date | date |
| bucket_start / bucket_end | timestamptz (بازه ۱۵ دقیقه‌ای) |
| foreground_ms | bigint |
| last_used_at | timestamptz |
| launch_count | integer |
| created_at | timestamptz |

**Indexes:** `ix_au_device_day` (device_id, day_date DESC) · `ix_au_pkg` (device_id, package_name, day_date) · `ix_au_family_day` (family_id, day_date)
**Retention:** ۶۰ روز جزئیات؛ تاریخچه بلندمدت در `screen_time_daily`.
**Privacy:** بسته به `data_consents(app_usage)`؛ قابلیت «فقط دسته‌بندی، بدون نام اپ» (aggregated mode).

### 3.3 `locations` (Phase 3) — رمزنگاری‌شده

| field | type |
|---|---|
| id | bigserial **PK** |
| device_id / family_id | uuid |
| lat_enc / lng_enc | bytea (AES-256-GCM با family DEK) |
| accuracy_m | numeric(6,1) |
| altitude_m / speed_mps / bearing | numeric NULL |
| provider | text(20) (`fused`, `gps`, `network`) |
| geohash6 | char(6) — برای جست‌وجوی محدوده‌ای بدون رمزگشایی |
| is_significant | boolean (Semantic Location) |
| client_ts / server_ts | timestamptz |
| idempotency_key | text(64) |

**Indexes:** `ix_loc_device_ts` (device_id, server_ts DESC) · `ix_loc_family_geohash` (family_id, geohash6) · `ix_loc_ts` (server_ts) برای retention (۹۰ روز)
**دسترسی:** خواندن فقط با `manage_location` permission در `family_members`.

### 3.4 `geofences` + `geofence_events` (Phase 4)

`geofences`: id, family_id, device_id NULL (یعنی برای همه دستگاه‌های خانواده), name, kind(`home`,`school`,`gym`,`other`), center_lat_enc, center_lng_enc, radius_m (50..2000), trigger_on(`enter`,`exit`,`both`), dwell_seconds default 60, is_active, color_key, created_by, created_at, updated_at, deleted_at.
**Indexes:** `ix_gf_family_active` (family_id, is_active) · `ix_gf_device` (device_id)
**محدودیت Android:** حداکثر ۱۰۰ geofence per app ⇒ در لایه domain اعتبارسنجی می‌شود.

`geofence_events`: id bigserial, geofence_id FK, device_id, family_id, transition(`enter`,`exit`,`dwell`), confidence, lat_enc/lng_enc, accuracy_m, triggered_at, notified_at, notification_id, created_at. **Indexes:** `ix_ge_device_ts`, `ix_ge_geofence`.

### 3.5 `device_settings` (Policy — Phase 2+)

| field | type |
|---|---|
| device_id | uuid **PK** FK |
| family_id | uuid |
| version | integer (optimistic lock) |
| screen_time | jsonb `{"daily_limit_min":180,"weekly":[…],"bedtime":{"start":"22:00","end":"07:00","days":[1..7]}}` |
| app_policies | jsonb `[{"package":"…","mode":"limit","minutes":30,"schedule":[…]}]` |
| web_policy | jsonb `{"mode":"allowlist|blocklist|safe","domains":[…],"safe_search":true,"vpn_enabled":false}` |
| location_policy | jsonb `{"mode":"on_demand|scheduled|continuous","interval_sec":900,"significant_only":true}` |
| alerts_policy | jsonb `{…}` |
| enforcement_level | enum `advisory`, `assisted`, `owner` |
| updated_by | uuid |
| updated_at | timestamptz |

`applied_settings_version` در `device_status` اضافه می‌شود تا بدانیم دستگاه کدام نسخه را اجرا کرده.

### 3.6 `notifications` + `notification_preferences` (Phase 7)

`notifications`: id, family_id, device_id, user_id (گیرنده), alert_type(`device_offline`,`battery_low`,`geofence_exit`,`geofence_enter`,`screen_time_limit`,`bedtime_started`,`permission_revoked`,`device_unpaired`,`sos`,`device_uninstalled`), severity(`info`,`warning`,`critical`), title_key, body_params jsonb, data jsonb, dedupe_key, status(`created`,`pushed`,`delivered`,`read`,`muted`,`failed`), channel(`push`,`in_app`,`sms`), created_at, read_at.
**Indexes:** `ix_notif_family_unread` (family_id, status, created_at DESC) · `uniq_notif_dedupe` UNIQUE (dedupe_key) · `ix_notif_device_ts`.

`notification_preferences`: id, family_id, user_id, alert_type, enabled, quiet_hours_start/end, min_severity, channel_prefs jsonb. **UNIQUE** (user_id, alert_type).

### 3.7 `sos_events` (Phase 8)

id, family_id, device_id, status(`triggered`,`acknowledged`,`resolved`,`false_alarm`), lat_enc, lng_enc, accuracy_m, battery_level, connectivity, note, triggered_at, acknowledged_at, acknowledged_by, resolved_at, created_at.
**Indexes:** `ix_sos_family_status_ts` (family_id, status, triggered_at DESC) · `ix_sos_device_ts`.

### 3.8 `app_inventory` (Phase 5)

id, device_id, family_id, package_name, app_label, version_name, installed_at, first_seen_at, last_seen_at, is_system, is_launcher, manageability(`full`,`assisted`,`none`), policy_state jsonb, created_at/updated_at. **UNIQUE** (device_id, package_name).

### 3.9 `web_filter_rules` (Phase 6)

id, family_id, scope(`family`,`device`), device_id NULL, rule_type(`block`,`allow`,`category_block`,`safe_search`), pattern (domain/regex), category, enabled, created_by, created_at. **Indexes:** `ix_wfr_family_enabled`, `ix_wfr_pattern`.

---

## ۴. Row-Level Security (لایه سوم Multi-tenancy)

```sql
ALTER TABLE devices            ENABLE ROW LEVEL SECURITY;
ALTER TABLE device_status      ENABLE ROW LEVEL SECURITY;
ALTER TABLE permissions        ENABLE ROW LEVEL SECURITY;
ALTER TABLE telemetry_events   ENABLE ROW LEVEL SECURITY;
ALTER TABLE locations          ENABLE ROW LEVEL SECURITY;
ALTER TABLE screen_time_daily  ENABLE ROW LEVEL SECURITY;
ALTER TABLE app_usage          ENABLE ROW LEVEL SECURITY;
ALTER TABLE geofences          ENABLE ROW LEVEL SECURITY;
ALTER TABLE geofence_events    ENABLE ROW LEVEL SECURITY;
ALTER TABLE notifications      ENABLE ROW LEVEL SECURITY;
ALTER TABLE data_consents      ENABLE ROW LEVEL SECURITY;
ALTER TABLE child_profiles     ENABLE ROW LEVEL SECURITY;
ALTER TABLE audit_logs         ENABLE ROW LEVEL SECURITY;

-- در ابتدای هر connection (از JWT claim):
--   SET LOCAL app.family_id = '<uuid>';  SET LOCAL app.actor_id = '<uuid>';  SET LOCAL app.bypass = 'false';
CREATE POLICY family_isolation ON devices
  USING (family_id::text = current_setting('app.family_id', true)
         OR current_setting('app.bypass', true) = 'true');
-- همین الگو برای بقیه جداول
```

`app.bypass` فقط برای **مigrations، sweeper jobها و admin** با نقش DB مخصوص (`gl_service`) فعال می‌شود؛ نقش اپ (`gl_app`) هرگز bypass ندارد.

---

## ۵. Enumها (PostgreSQL)

```
user_role          : parent | child | support
user_status        : pending_verification | active | locked | deactivated | deleted
family_status      : active | suspended | deleted
member_role        : owner_parent | parent | guardian | child
member_status      : active | invited | removed
pairing_status     : pending | redeemed | expired | revoked | invalidated
device_status      : pairing_pending | active | suspended | removed | uninstalled
presence           : online | offline | stale | uninstalled | unknown
battery_status     : unknown | charging | discharging | full | not_charging
connectivity       : wifi | cellular | ethernet | none | unknown
screen_state       : on | off | locked | unknown
session_kind       : parent_app | child_device | backend_service
permission_state   : granted | denied | not_requested | unavailable | revoked_by_user | restricted
telemetry_type     : battery | connectivity | permission_changed | device_restart | app_restart
                     | sync_completed | uninstall_imminent | heartbeat_missed
audit_category     : AUTH | PAIRING | DEVICE | LOCATION | SCREEN_TIME | APP_MGMT | WEB_SAFETY
                     | NOTIFICATION | SECURITY | PRIVACY | SOS | TAMPER
tamper_type        : protection.disable_requested | protection.removed | app.uninstall_attempt
                     | service.force_stopped | data.cleared_suspected | permission.changed
                     | background.killed | connectivity.cut | time.tampered | policy.mismatch
                     | boot.evasion_suspected | integrity.root_suspected | integrity.failed
                     | app.downgrade | webfilter.disabled | device.silent | device.factory_reset_suspected
tamper_severity    : info | warning | critical
tamper_status      : new | ack | resolved | false_alarm
audit_result       : success | failure | denied | partial
actor_type         : parent | child_device | system | admin
outbox_status      : pending | sent | failed | dropped
alert_severity     : info | warning | critical
```

---

## ۶. DDL — `V1__init_phase1.sql` (خلاصه اجرایی)

```sql
CREATE EXTENSION IF NOT EXISTS citext;
CREATE EXTENSION IF NOT EXISTS pgcrypto;   -- gen_random_uuid(), digest()
CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE TYPE user_role        AS ENUM ('parent','child','support');
CREATE TYPE user_status      AS ENUM ('pending_verification','active','locked','deactivated','deleted');
CREATE TYPE family_status    AS ENUM ('active','suspended','deleted');
CREATE TYPE member_role      AS ENUM ('owner_parent','parent','guardian','child');
CREATE TYPE member_status    AS ENUM ('active','invited','removed');
CREATE TYPE pairing_status   AS ENUM ('pending','redeemed','expired','revoked','invalidated');
CREATE TYPE device_lifecycle AS ENUM ('pairing_pending','active','suspended','removed','uninstalled');
CREATE TYPE presence         AS ENUM ('online','offline','stale','uninstalled','unknown');
CREATE TYPE battery_status   AS ENUM ('unknown','charging','discharging','full','not_charging');
CREATE TYPE connectivity     AS ENUM ('wifi','cellular','ethernet','none','unknown');
CREATE TYPE screen_state     AS ENUM ('on','off','locked','unknown');
CREATE TYPE session_kind     AS ENUM ('parent_app','child_device','backend_service');
CREATE TYPE permission_state AS ENUM ('granted','denied','not_requested','unavailable','revoked_by_user','restricted');
CREATE TYPE permission_kind  AS ENUM ('notifications','battery_optimization_exempt','device_admin','device_owner',
                                      'usage_access','accessibility','location_while_using','location_background',
                                      'vpn','private_dns','overlay','exact_alarm','all_files','camera','microphone');
CREATE TYPE telemetry_type   AS ENUM ('battery','connectivity','permission_changed','device_restart','app_restart',
                                      'sync_completed','uninstall_imminent','heartbeat_missed');
CREATE TYPE audit_category   AS ENUM ('AUTH','PAIRING','DEVICE','LOCATION','SCREEN_TIME','APP_MGMT','WEB_SAFETY',
                                      'NOTIFICATION','SECURITY','PRIVACY','SOS','TAMPER');
CREATE TYPE tamper_type      AS ENUM ('protection.disable_requested','protection.removed','app.uninstall_attempt',
                                      'service.force_stopped','data.cleared_suspected','permission.changed',
                                      'background.killed','connectivity.cut','time.tampered','policy.mismatch',
                                      'boot.evasion_suspected','integrity.root_suspected','integrity.failed',
                                      'app.downgrade','webfilter.disabled','device.silent','device.factory_reset_suspected');
CREATE TYPE tamper_severity  AS ENUM ('info','warning','critical');
CREATE TYPE tamper_status    AS ENUM ('new','ack','resolved','false_alarm');
CREATE TYPE audit_result     AS ENUM ('success','failure','denied','partial');
CREATE TYPE actor_type       AS ENUM ('parent','child_device','system','admin');
CREATE TYPE outbox_status    AS ENUM ('pending','sent','failed','dropped');

CREATE TABLE users (
  id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  email               citext NOT NULL,
  password_hash       text,
  display_name        text CHECK (char_length(display_name) <= 80),
  locale              text NOT NULL DEFAULT 'fa-IR',
  role                user_role NOT NULL DEFAULT 'parent',
  status              user_status NOT NULL DEFAULT 'pending_verification',
  email_verified_at   timestamptz,
  failed_login_count  smallint NOT NULL DEFAULT 0,
  locked_until        timestamptz,
  last_login_at       timestamptz,
  totp_secret_enc     bytea,
  created_at          timestamptz NOT NULL DEFAULT now(),
  updated_at          timestamptz NOT NULL DEFAULT now(),
  deleted_at          timestamptz
);
CREATE UNIQUE INDEX uniq_users_email ON users (email) WHERE deleted_at IS NULL;
CREATE INDEX ix_users_status ON users (status);

CREATE TABLE families (
  id            uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  name          text NOT NULL CHECK (char_length(name) BETWEEN 1 AND 60),
  owner_user_id uuid NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
  plan          text NOT NULL DEFAULT 'free',
  max_children  smallint NOT NULL DEFAULT 6,
  max_devices   smallint NOT NULL DEFAULT 10,
  timezone      text NOT NULL DEFAULT 'Asia/Tehran',
  status        family_status NOT NULL DEFAULT 'active',
  created_at    timestamptz NOT NULL DEFAULT now(),
  updated_at    timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX ix_families_owner ON families (owner_user_id);

CREATE TABLE family_deks (
  family_id    uuid PRIMARY KEY REFERENCES families(id) ON DELETE CASCADE,
  wrapped_dek  bytea NOT NULL,
  algorithm    text  NOT NULL DEFAULT 'AES-256-GCM',
  key_version  smallint NOT NULL DEFAULT 1,
  created_at   timestamptz NOT NULL DEFAULT now(),
  rotated_at   timestamptz
);

CREATE TABLE child_profiles (
  id                uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  family_id         uuid NOT NULL REFERENCES families(id) ON DELETE CASCADE,
  user_id           uuid REFERENCES users(id) ON DELETE SET NULL,
  display_name      text NOT NULL CHECK (char_length(display_name) BETWEEN 1 AND 60),
  avatar_key        text CHECK (char_length(avatar_key) <= 40),
  birth_year        smallint CHECK (birth_year BETWEEN 1950 AND 2100),
  default_device_id uuid,
  created_at        timestamptz NOT NULL DEFAULT now(),
  updated_at        timestamptz NOT NULL DEFAULT now(),
  deleted_at        timestamptz
);
CREATE INDEX ix_cp_family ON child_profiles (family_id) WHERE deleted_at IS NULL;

CREATE TABLE family_members (
  id               uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  family_id        uuid NOT NULL REFERENCES families(id) ON DELETE CASCADE,
  user_id          uuid REFERENCES users(id) ON DELETE CASCADE,
  child_profile_id uuid REFERENCES child_profiles(id) ON DELETE CASCADE,
  role             member_role NOT NULL,
  permissions      text[] NOT NULL DEFAULT '{view,status}',
  invited_by       uuid REFERENCES users(id),
  status           member_status NOT NULL DEFAULT 'active',
  joined_at        timestamptz,
  removed_at       timestamptz,
  created_at       timestamptz NOT NULL DEFAULT now(),
  updated_at       timestamptz NOT NULL DEFAULT now(),
  CONSTRAINT chk_member_identity CHECK (user_id IS NOT NULL OR child_profile_id IS NOT NULL)
);
CREATE UNIQUE INDEX uniq_member_user ON family_members (family_id, user_id) WHERE user_id IS NOT NULL;
CREATE INDEX ix_fm_family ON family_members (family_id, status);
CREATE INDEX ix_fm_user   ON family_members (user_id);

CREATE TABLE devices (
  id                       uuid PRIMARY KEY,
  family_id                uuid NOT NULL REFERENCES families(id) ON DELETE CASCADE,
  child_profile_id         uuid REFERENCES child_profiles(id) ON DELETE SET NULL,
  pairing_code_id          uuid,
  install_id               uuid NOT NULL,
  device_key_id            text NOT NULL,
  public_key               bytea,
  nickname                 text CHECK (char_length(nickname) <= 60),
  avatar_key               text,
  manufacturer             text NOT NULL,
  model                    text NOT NULL,
  device                   text NOT NULL,
  brand                    text NOT NULL,
  android_sdk_int          smallint NOT NULL,
  android_release          text NOT NULL,
  app_version_name         text NOT NULL,
  app_version_code         integer NOT NULL,
  locale                   text NOT NULL,
  timezone                 text NOT NULL,
  is_emulator              boolean NOT NULL DEFAULT false,
  is_rooted                boolean NOT NULL DEFAULT false,
  play_integrity_verdict   text,
  capabilities             jsonb NOT NULL DEFAULT '{}'::jsonb,
  consent_version          text,
  consented_at             timestamptz,
  status                   device_lifecycle NOT NULL DEFAULT 'pairing_pending',
  last_seen_at             timestamptz,
  paired_at                timestamptz,
  removed_at               timestamptz,
  removed_by_user_id       uuid REFERENCES users(id),
  created_at               timestamptz NOT NULL DEFAULT now(),
  updated_at               timestamptz NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX uniq_devices_install_id ON devices (install_id);
CREATE UNIQUE INDEX uniq_devices_key_id     ON devices (device_key_id);
CREATE INDEX ix_devices_family_status       ON devices (family_id, status);
CREATE INDEX ix_devices_child               ON devices (child_profile_id);
CREATE INDEX ix_devices_last_seen           ON devices (last_seen_at);
ALTER TABLE child_profiles
  ADD CONSTRAINT fk_cp_default_device FOREIGN KEY (default_device_id) REFERENCES devices(id) ON DELETE SET NULL;

CREATE TABLE device_credentials (
  device_id         uuid PRIMARY KEY REFERENCES devices(id) ON DELETE CASCADE,
  family_id         uuid NOT NULL REFERENCES families(id) ON DELETE CASCADE,
  secret_hash       bytea NOT NULL,
  algorithm         text NOT NULL DEFAULT 'HMAC-SHA256',
  key_version       smallint NOT NULL DEFAULT 1,
  issued_at         timestamptz NOT NULL DEFAULT now(),
  rotated_at        timestamptz,
  revoked_at        timestamptz,
  revoked_reason    text,
  last_used_at      timestamptz,
  failed_auth_count smallint NOT NULL DEFAULT 0
);
CREATE INDEX ix_dc_family  ON device_credentials (family_id);
CREATE INDEX ix_dc_active  ON device_credentials (device_id) WHERE revoked_at IS NULL;

CREATE TABLE device_status (
  device_id             uuid PRIMARY KEY REFERENCES devices(id) ON DELETE CASCADE,
  family_id             uuid NOT NULL REFERENCES families(id) ON DELETE CASCADE,
  presence              presence NOT NULL DEFAULT 'unknown',
  presence_reason       text,
  online_since          timestamptz,
  offline_since         timestamptz,
  lease_expires_at      timestamptz NOT NULL DEFAULT now(),
  last_heartbeat_at     timestamptz,
  last_sync_at          timestamptz,
  battery_level         smallint CHECK (battery_level BETWEEN 0 AND 100),
  battery_status        battery_status,
  battery_temperature_c numeric(4,1),
  is_power_save_mode    boolean,
  connectivity_type     connectivity,
  is_roaming            boolean,
  network_country       text,
  screen_state          screen_state,
  app_in_foreground     boolean,
  storage_free_mb       integer,
  device_uptime_sec     bigint,
  app_restart_count     integer NOT NULL DEFAULT 0,
  updated_at            timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX ix_ds_family_presence ON device_status (family_id, presence);
CREATE INDEX ix_ds_lease           ON device_status (lease_expires_at);

CREATE TABLE device_sessions (
  id                 uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  device_id          uuid REFERENCES devices(id) ON DELETE CASCADE,
  user_id            uuid REFERENCES users(id) ON DELETE CASCADE,
  family_id          uuid NOT NULL,
  session_kind       session_kind NOT NULL,
  device_fingerprint text,
  ip_address         inet,
  user_agent         text,
  issued_at          timestamptz NOT NULL DEFAULT now(),
  last_seen_at       timestamptz NOT NULL DEFAULT now(),
  expires_at         timestamptz NOT NULL,
  revoked_at         timestamptz,
  revoked_reason     text,
  CONSTRAINT chk_session_owner CHECK ((device_id IS NULL) <> (user_id IS NULL))
);
CREATE INDEX ix_dsess_device  ON device_sessions (device_id, revoked_at);
CREATE INDEX ix_dsess_user    ON device_sessions (user_id, revoked_at);
CREATE INDEX ix_dsess_expires ON device_sessions (expires_at);

CREATE TABLE refresh_tokens (
  id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id             uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  family_id           uuid,
  token_hash          bytea NOT NULL,
  parent_token_id     uuid REFERENCES refresh_tokens(id),
  device_fingerprint  text NOT NULL,
  ip_address          inet,
  issued_at           timestamptz NOT NULL DEFAULT now(),
  expires_at          timestamptz NOT NULL,
  used_at             timestamptz,
  revoked_at          timestamptz,
  reuse_detected_at   timestamptz
);
CREATE UNIQUE INDEX uniq_rt_hash ON refresh_tokens (token_hash);
CREATE INDEX ix_rt_user   ON refresh_tokens (user_id) WHERE revoked_at IS NULL;
CREATE INDEX ix_rt_parent ON refresh_tokens (parent_token_id);

CREATE TABLE pairing_codes (
  id                    uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  family_id             uuid NOT NULL REFERENCES families(id) ON DELETE CASCADE,
  created_by_user_id    uuid NOT NULL REFERENCES users(id),
  code_hash             bytea NOT NULL,
  code_prefix           text NOT NULL,
  attempt_count         smallint NOT NULL DEFAULT 0,
  max_attempts          smallint NOT NULL DEFAULT 10,
  expires_at            timestamptz NOT NULL DEFAULT now() + interval '10 minutes',
  redeemed_at           timestamptz,
  redeemed_by_device_id uuid REFERENCES devices(id),
  status                pairing_status NOT NULL DEFAULT 'pending',
  client_ip             inet,
  created_at            timestamptz NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX uniq_pairing_code_hash ON pairing_codes (code_hash) WHERE status = 'pending';
CREATE INDEX ix_pairing_family  ON pairing_codes (family_id, status, expires_at DESC);
CREATE INDEX ix_pairing_expires ON pairing_codes (expires_at) WHERE status = 'pending';
ALTER TABLE devices ADD CONSTRAINT fk_devices_pairing_code
  FOREIGN KEY (pairing_code_id) REFERENCES pairing_codes(id);

CREATE TABLE permissions (
  id                 uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  device_id          uuid NOT NULL REFERENCES devices(id) ON DELETE CASCADE,
  family_id          uuid NOT NULL REFERENCES families(id) ON DELETE CASCADE,
  kind               permission_kind NOT NULL,
  state              permission_state NOT NULL,
  required           boolean NOT NULL DEFAULT false,
  blocking_features  text[] NOT NULL DEFAULT '{}',
  requested_at       timestamptz,
  granted_at         timestamptz,
  revoked_at         timestamptz,
  last_checked_at    timestamptz NOT NULL DEFAULT now(),
  note               text
);
CREATE UNIQUE INDEX uniq_perm_device_kind ON permissions (device_id, kind);
CREATE INDEX ix_perm_family_state ON permissions (family_id, state);

CREATE TABLE telemetry_events (
  id              bigserial PRIMARY KEY,
  family_id       uuid NOT NULL,
  device_id       uuid NOT NULL REFERENCES devices(id) ON DELETE CASCADE,
  type            telemetry_type NOT NULL,
  payload         jsonb NOT NULL DEFAULT '{}'::jsonb,
  client_ts       timestamptz,
  server_ts       timestamptz NOT NULL DEFAULT now(),
  idempotency_key text,
  ingest_batch_id uuid
);
CREATE UNIQUE INDEX uniq_te_idem    ON telemetry_events (device_id, idempotency_key) WHERE idempotency_key IS NOT NULL;
CREATE INDEX ix_te_device_ts        ON telemetry_events (device_id, server_ts DESC);
CREATE INDEX ix_te_family_type_ts   ON telemetry_events (family_id, type, server_ts DESC);
CREATE INDEX ix_te_ts               ON telemetry_events (server_ts);

CREATE TABLE data_consents (
  id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  family_id       uuid NOT NULL REFERENCES families(id) ON DELETE CASCADE,
  device_id       uuid NOT NULL REFERENCES devices(id) ON DELETE CASCADE,
  subject_type    text NOT NULL DEFAULT 'child',
  capability      text NOT NULL,
  consent_version text NOT NULL,
  granted         boolean NOT NULL,
  granted_by      text NOT NULL DEFAULT 'child',
  granted_at      timestamptz NOT NULL DEFAULT now(),
  revoked_at      timestamptz,
  note            text
);
CREATE UNIQUE INDEX uniq_consent ON data_consents (device_id, capability, consent_version);
CREATE INDEX ix_consent_device_granted ON data_consents (device_id, granted);

CREATE TABLE audit_logs (
  id          bigserial PRIMARY KEY,
  family_id   uuid,
  actor_type  actor_type NOT NULL,
  actor_id    uuid,
  action      text NOT NULL,
  target_type text,
  target_id   uuid,
  category    audit_category NOT NULL,
  result      audit_result NOT NULL,
  reason_code text,
  ip_address  inet,
  user_agent  text,
  app_version text,
  metadata    jsonb NOT NULL DEFAULT '{}'::jsonb,
  created_at  timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX ix_al_family_ts   ON audit_logs (family_id, created_at DESC);
CREATE INDEX ix_al_category_ts ON audit_logs (category, created_at DESC);
CREATE INDEX ix_al_actor       ON audit_logs (actor_type, actor_id, created_at DESC);
CREATE INDEX ix_al_action      ON audit_logs (action);

CREATE TABLE outbox (
  id             bigserial PRIMARY KEY,
  aggregate_type text NOT NULL,
  aggregate_id   uuid NOT NULL,
  family_id      uuid NOT NULL,
  event_type     text NOT NULL,
  payload        jsonb NOT NULL DEFAULT '{}'::jsonb,
  channels       text[] NOT NULL DEFAULT '{sse}',
  status         outbox_status NOT NULL DEFAULT 'pending',
  attempts       smallint NOT NULL DEFAULT 0,
  available_at   timestamptz NOT NULL DEFAULT now(),
  created_at     timestamptz NOT NULL DEFAULT now(),
  sent_at        timestamptz
);
CREATE INDEX ix_outbox_pending ON outbox (status, available_at) WHERE status = 'pending';
CREATE INDEX ix_outbox_family  ON outbox (family_id, created_at DESC);

CREATE TABLE push_tokens (
  id             uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  family_id      uuid NOT NULL,
  user_id        uuid REFERENCES users(id) ON DELETE CASCADE,
  device_id      uuid REFERENCES devices(id) ON DELETE CASCADE,
  provider       text NOT NULL DEFAULT 'fcm',
  token_hash     bytea NOT NULL,
  token_enc      bytea NOT NULL,
  platform       text NOT NULL DEFAULT 'android',
  locale         text,
  created_at     timestamptz NOT NULL DEFAULT now(),
  last_seen_at   timestamptz NOT NULL DEFAULT now(),
  revoked_at     timestamptz
);
CREATE UNIQUE INDEX uniq_push_token ON push_tokens (provider, token_hash);
CREATE INDEX ix_push_user   ON push_tokens (user_id) WHERE revoked_at IS NULL;
CREATE INDEX ix_push_device ON push_tokens (device_id) WHERE revoked_at IS NULL;

-- updated_at trigger
CREATE OR REPLACE FUNCTION set_updated_at() RETURNS trigger AS $$
BEGIN NEW.updated_at = now(); RETURN NEW; END $$ LANGUAGE plpgsql;

DO $$ DECLARE t text;
BEGIN
  FOREACH t IN ARRAY ARRAY['users','families','child_profiles','family_members','devices','device_status']
  LOOP EXECUTE format('CREATE TRIGGER trg_%s_updated_at BEFORE UPDATE ON %I
                      FOR EACH ROW EXECUTE FUNCTION set_updated_at()', t, t);
  END LOOP;
END $$;

-- RLS
ALTER TABLE devices ENABLE ROW LEVEL SECURITY;
ALTER TABLE device_status ENABLE ROW LEVEL SECURITY;
ALTER TABLE device_credentials ENABLE ROW LEVEL SECURITY;
ALTER TABLE permissions ENABLE ROW LEVEL SECURITY;
ALTER TABLE telemetry_events ENABLE ROW LEVEL SECURITY;
ALTER TABLE data_consents ENABLE ROW LEVEL SECURITY;
ALTER TABLE child_profiles ENABLE ROW LEVEL SECURITY;
ALTER TABLE audit_logs ENABLE ROW LEVEL SECURITY;
ALTER TABLE device_sessions ENABLE ROW LEVEL SECURITY;

CREATE OR REPLACE FUNCTION current_family_id() RETURNS uuid AS $$
  SELECT NULLIF(current_setting('app.family_id', true), '')::uuid $$ LANGUAGE sql STABLE;
CREATE OR REPLACE FUNCTION rls_bypass() RETURNS boolean AS $$
  SELECT coalesce(current_setting('app.bypass', true), 'false') = 'true' $$ LANGUAGE sql STABLE;

CREATE POLICY family_isolation_devices ON devices
  USING (family_id = current_family_id() OR rls_bypass());
CREATE POLICY family_isolation_status ON device_status
  USING (family_id = current_family_id() OR rls_bypass());
CREATE POLICY family_isolation_creds ON device_credentials
  USING (family_id = current_family_id() OR rls_bypass());
CREATE POLICY family_isolation_perms ON permissions
  USING (family_id = current_family_id() OR rls_bypass());
CREATE POLICY family_isolation_telemetry ON telemetry_events
  USING (family_id = current_family_id() OR rls_bypass());
CREATE POLICY family_isolation_consents ON data_consents
  USING (family_id = current_family_id() OR rls_bypass());
CREATE POLICY family_isolation_children ON child_profiles
  USING (family_id = current_family_id() OR rls_bypass());
CREATE POLICY family_isolation_sessions ON device_sessions
  USING (family_id = current_family_id() OR rls_bypass());
CREATE POLICY family_isolation_audit ON audit_logs
  USING (family_id = current_family_id() OR family_id IS NULL OR rls_bypass());

-- نقش‌ها
CREATE ROLE gl_app  LOGIN PASSWORD 'change_me';   -- بدون BYPASSRLS
CREATE ROLE gl_migr LOGIN PASSWORD 'change_me';   -- برای Flyway
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO gl_app;
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO gl_app;
GRANT ALL ON SCHEMA public TO gl_migr;
```

---

## ۷. الگوهای کوئری اصلی Phase 1

```sql
-- Dashboard: وضعیت همه دستگاه‌های خانواده (یک کوئری، بدون N+1)
SELECT d.id, d.nickname, d.model, d.avatar_key, cp.display_name AS child_name,
       s.presence, s.battery_level, s.battery_status, s.connectivity_type,
       s.last_heartbeat_at, s.last_sync_at, s.lease_expires_at
FROM devices d
JOIN device_status s ON s.device_id = d.id
LEFT JOIN child_profiles cp ON cp.id = d.child_profile_id
WHERE d.family_id = $1 AND d.status = 'active'
ORDER BY cp.display_name NULLS LAST, d.paired_at DESC;

-- مجوزهای یک دستگاه
SELECT kind, state, required, blocking_features, last_checked_at
FROM permissions WHERE device_id = $1 ORDER BY required DESC, kind;

-- Sweeper job (هر ۱۵ ثانیه): online → stale وقتی lease تمام شد
UPDATE device_status SET presence = 'stale', presence_reason = 'grace_expired',
       offline_since = lease_expires_at, updated_at = now()
WHERE presence = 'online' AND lease_expires_at < now();
-- سپس رویداد device.offline در outbox درج می‌شود (با dedupe_key)
```

---

## ۸. Retention و پاک‌سازی داده (Privacy by design)

| داده | نگهداری | پاک‌سازی |
|---|---|---|
| `telemetry_events` | ۳۰ روز | aggregate → `telemetry_daily` |
| `locations` (Phase 3) | ۹۰ روز (قابل تنظیم توسط والد: ۷/۳۰/۹۰) | حذف سخت |
| `app_usage` (Phase 2) | ۶۰ روز | aggregate → `screen_time_daily` |
| `audit_logs` | ۳۶۵ روز | حذف سخت |
| `notifications` | ۱۸۰ روز | حذف سخت |
| `outbox` sent | ۷ روز | حذف |
| دستگاه حذف‌شده | `devices.status='removed'` + revoke credential | داده‌های telemetry/location/app_usage فوراً purge (در Phase 1: battery/status/permissions)؛ رکورد device برای Audit ۹۰ روز می‌ماند سپس anonymize |

**«Remove Device» دقیقاً چه می‌کند؟** ⇒ بخش Revocation در [`06-security-architecture.md`](./06-security-architecture.md)
