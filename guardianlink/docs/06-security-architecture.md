# 06 — Security Architecture

## ۱. مدل تهدید (STRIDE خلاصه‌شده برای این محصول)

| # | تهدید | سناریوی واقعی | دفاع |
|---|---|---|---|
| T1 | **شنود ترافیک** | Wi-Fi عمومی مدرسه | TLS 1.2+ اجباری، `cleartextTrafficPermitted=false`، **Certificate Pinning** در release/staging |
| T2 | **Replay درخواست‌ها** | ضبط بسته heartbeat و ارسال مجدد | HMAC با `timestamp` (±300s) + `nonce` یک‌بارمصرف در Redis |
| T3 | **سرقت refresh token** | بدافزار روی گوشی والد | Token چرخشی + **Reuse Detection** + binding به device_fingerprint + ذخیره رمزنگاری‌شده (Tink/Keystore) |
| T4 | **دسترسی cross-tenant** | والد A دستگاه خانواده B را ببیند | سه لایه: principal-only family_id + scoped query + **Postgres RLS** |
| T5 | **Pairing مخفیانه (جاسوسی)** | کسی بدون اطلاع فرزند اپ را نصب کند | تأیید دوطرفه در لحظه Pairing + **صفحه افشای اجباری** روی دستگاه فرزند + نوتیف دائمی FGS «این دستگاه به خانواده X متصل است» + مدیریت/حذف فقط توسط والد |
| T6 | **Brute force کد pairing** | حدس زدن کد ۸ کاراکتری | ۴۰ بیت آنتروپی + ۱۰ تلاش + rate limit + TTL ۱۰ دقیقه + invalidate |
| T7 | **نشت دیتابیس** | SQL dump لو برود | رمزنگاری ستون‌های حساس با **family DEK** (envelope)، password با Argon2id، device_secret فقط hash، push token رمزنگاری‌شده |
| T8 | **نشت Master Key** | دسترسی به سرور | Master Key از env/KMS، rotation با `key_version`، DEK per family ⇒ blast radius محدود به یک خانواده |
| T9 | **جعل کلاینت** | اسکریپت داده موقعیت جعلی بفرستد | HMAC device credential + Play Integrity (Phase 9) + اعتبارسنجی فیزیکی داده (speed/accuracy/jump detection) + anomaly detection |
| T10 | **دستکاری دستگاه فرزند** | فرزند مجوز را پس بگیرد یا اپ را حذف کند | تشخیص و گزارش فوری (`permission.revoked`, `heartbeat_missed`, `uninstall_imminent`) + Alert به والد + Device Owner (اختیاری) برای جلوگیری قطعی |
| T11 | **نشت PII در لاگ** | لاگ Crashlytics شامل مکان/رمز | **Redactor** مرکزی + allowlist فیلدها + ممنوعیت `toString` روی مدل‌های حساس + CI check |
| T12 | **DoS / هزینه‌زا** | flood روی `/child/events` | Rate limit چندلایه (IP/user/device) + body size limit + queue با backpressure + `429` با `Retry-After` |
| T13 | **Account takeover از راه ایمیل** | رمز ضعیف | Argon2id + soft lockout + هشدار login جدید + 2FA (Phase 9) + «logout همه دستگاه‌ها» |
| T14 | **خطر نشت/دست‌کاری VPN (قابلیت آینده؛ در Phase 7 پیاده نشده)** | فیلتر VPN در scope جاری وجود ندارد | هیچ ادعای tunnel/DNS leak protection نداریم؛ اگر VPN آینده بررسی شود، باید service واقعی، رضایت Android، امنیت و آزمون مستقل داشته باشد |
| T15 | **فیزیکی: گوشی والد بدون قفل** | دسترسی دیگران به داشبورد | App Lock (بیومتریک/PIN) + قفل خودکار بعد از ۶۰ ثانیه + عدم نمایش داده در Recent Apps (FLAG_SECURE اختیاری) |
| T16 | **فرار با uninstall / force-stop / clear-data** | حذف کنترل‌ها توسط فرزند | پیشگیری رسمی فقط در M2 (Device Owner)؛ در M0/M1: early-warning رسمی (`DeviceAdminReceiver.onDisabled`) + Silence Analyzer + Recovery Pairing — [`11`](./11-anti-tampering.md) |
| T17 | **تغییر بی‌صدای مجوزها** | خاموش‌کردن location/usage بدون رویداد | `AppOpsManager.startWatchingMode` + IntegritySnapshot diff در هر wake ⇒ `[TAMPER] permission.changed` |
| T18 | **دستکاری ساعت/Policy** | دور زدن bedtime/limits | زمان سرور = منبع حقیقت + `ACTION_TIME_CHANGED` + policy re-assert در M2 |
| T19 | **استفاده از ابزار ممنوعه** (accessibility-abuse/exploit) | ban + پیگرد | خط قرمز پروژه: حذف کامل Accessibility از معماری + lint rule در CI برای APIهای ممنوع |

---

## ۲. رمزنگاری — مشخصات دقیق

### ۲.۱ سمت سرور

| مورد | الگوریتم | جزئیات |
|---|---|---|
| رمز عبور | **Argon2id** | `m=65536 KiB, t=3, p=4, salt=16B, hash=32B` (BouncyCastle) |
| Access token | **Ed25519 (EdDSA JWT)** | `kid` برای rotation؛ کلیدها در Phase 1 از env، در Phase 9 از KMS |
| Refresh token | opaque ۲۵۶ بیت | فقط `SHA-256` در DB |
| Device secret | ۲۵۶ بیت `SecureRandom` | فقط `SHA-256` در DB |
| امضای درخواست دستگاه | **HMAC-SHA256** | canonical string شامل method/path/ts/nonce/body-hash |
| رمزنگاری ستون‌های حساس | **AES-256-GCM** | per-family DEK، DEK با Master Key wrap می‌شود (`family_deks.wrapped_dek`) |
| Master Key | AES-256 | env `GL_MASTER_KEY` (Phase 1) → Vault/KMS (Phase 9)؛ rotation با `key_version` |
| Pairing code | `SecureRandom` + Crockford Base32 | فقط hash در DB |
| TLS | 1.3 ترجیحاً، حداقل 1.2 | HSTS، فقط cipher suiteهای مدرن (Caddy به‌صورت پیش‌فرض) |

### ۲.۲ سمت کلاینت (Android)

| مورد | فناوری |
|---|---|
| تولید/نگهداری کلید | **Android Keystore** (`KeyGenParameterSpec`, AES-256-GCM, `setIsStrongBoxBacked(true)` اگر موجود) |
| رمزنگاری فایل‌های محلی | **Google Tink** AEAD از طریق `androidx.datastore:datastore-tink` (`AeadSerializer`) |
| رمزنگاری رکوردهای حساس Room | TypeConverter با Tink AEAD (برای مختصات در Phase 3) |
| امضای درخواست | HMAC-SHA256 با secret خوانده‌شده از SecureStore (در حافظه: `CharArray`/`ByteArray` که بعد از استفاده `fill(0)` می‌شود) |
| تصادفی | `java.security.SecureRandom` |
| Biometric | `androidx.biometric` + `CryptoObject` |
| Certificate pinning | OkHttp `CertificatePinner` (SPKI pins) + `network_security_config` `<pin-set>` با expiration |

### ۲.۳ `network_security_config.xml` (release)

```xml
<network-security-config>
  <base-config cleartextTrafficPermitted="false">
    <trust-anchors><certificates src="system"/></trust-anchors>
  </base-config>
  <domain-config>
    <domain includeSubdomains="true">api.guardianlink.app</domain>
    <pin-set expiration="2027-03-01">
      <pin digest="SHA-256">PRIMARY_SPKI_PIN=</pin>
      <pin digest="SHA-256">BACKUP_SPKI_PIN=</pin>   <!-- ⚠️ همیشه دو pin: یکی یدک -->
    </pin-set>
  </domain-config>
  <!-- فقط در source set debug: اجازه cleartext برای 10.0.0.0/8 و 192.168.0.0/16 -->
</network-security-config>
```

---

## ۳. Revocation — سه سطح (بند ۶ پرامپت)

### ۳.۱ `Logout Device` (سبک — قابل بازگشت)
```
DELETE-like: POST /v1/devices/{id}/logout
 ⇒ device_sessions(child_device).revoked_at = now, reason='parent_logout'
 ⇒ refresh/access مربوطه در deny-list
 ⇒ device_secret معتبر می‌ماند ⇒ کودک می‌تواند با «اتصال مجدد» برگردد (بدون pairing جدید)
 ⇒ SSE/FCM به کودک: session.revoked ⇒ UI: «والد شما این نشست را بست»
```

### ۳.۲ `Revoke Access` (متوسط)
```
POST /v1/devices/{id}/commands {command:"revoke_credentials"}
 ⇒ device_credentials.revoked_at=now, reason='compromised'|'parent_revoke'
 ⇒ همه sessionها و push tokenهای دستگاه revoke
 ⇒ دستگاه دیگر هیچ درخواستی نمی‌تواند بفرستد (401) ولی رکورد device و تاریخچه می‌ماند
 ⇒ والد می‌تواند «re-issue credential» کند (بدون pairing کامل) یا کامل حذف کند
```

### ۳.۳ `Remove Device` (کامل — Test 8)
```
DELETE /v1/devices/{id}?purge_telemetry=true&notify_child=true
BEGIN TX
  1) device_credentials.revoked_at = now(), reason='parent_removed'
  2) device_sessions   → revoke همه
  3) push_tokens       → revoke (و حذف از FCM)
  4) pairing_codes     → revoke همه کدهای pending این دستگاه/خانواده مرتبط
  5) outbox            → درج رویداد device.removed + command unpair (کانال sse+fcm)
  6) purge: telemetry_events, permissions, device_status, data_consents  → DELETE
  7) devices.status='removed', removed_at=now, removed_by_user_id=caller
  8) audit_logs[DEVICE][device.removed] + [PRIVACY][data.purged]
COMMIT
```
**سمت کودک (دریافت `device.unpair` از SSE یا `410 DEVICE_REMOVED` از heartbeat):**
1. توقف فوری `SyncForegroundService` و همه Collectorها
2. پاک‌سازی `device_secret`, `device_id`, session از SecureStore
3. پاک‌سازی همه داده محلی (Room + DataStore) — **wipe local data**
4. غیرفعال کردن Accessibility/VPN اگر فعال بودند (Phase 5/6)
5. UI: صفحه «اتصال شما توسط والد قطع شد» + گزینه « pairing جدید با رضایت والد»
6. **هیچ درخواست دیگری به سرور نمی‌فرستد** (اگر بفرستد ⇒ `410` و audit `[SECURITY]`)

### ۳.۴ ماتریس Revocation

| اقدام | credential | sessions | push | داده‌های تاریخی | برگشت‌پذیر؟ |
|---|---|---|---|---|---|
| Logout Device | ✅ معتبر | ❌ revoke | ✅ | ✅ می‌ماند | بله (reconnect) |
| Revoke Access | ❌ revoke | ❌ revoke | ❌ revoke | ✅ می‌ماند | بله (re-issue) |
| Remove Device | ❌ revoke | ❌ revoke | ❌ revoke + delete | ❌ purge | خیر (pairing جدید لازم است) |
| حذف حساب والد | ❌ همه | ❌ همه | ❌ همه | حذف کامل بعد از ۳۰ روز | ۳۰ روز |

---

## ۴. Logging و Audit (بند ۱۹)

### ۴.۱ Taxonomy تگ‌ها
```
[AUTH] [PAIRING] [DEVICE] [SESSION] [PERMISSION] [CONSENT]
[SYNC] [NETWORK] [BATTERY] [PRESENCE]
[LOCATION] [SCREEN_TIME] [APP_MGMT] [WEB_SAFETY] [GEOFENCE] [SOS]   ← Phaseهای بعد
[NOTIFICATION] [SECURITY] [PRIVACY] [DB] [WORK] [UI] [TEST]
```

### ۴.۲ قالب لاگ (ساختاریافته)

**کلاینت (Logcat + فایل چرخشی رمزنگاری‌شده برای debug):**
```
2026-09-28T12:41:22.104Z  I  [PAIRING] code_redeemed  req=01J8ZK… fam=***3f9a dev=***7b2c attempts=0
2026-09-28T12:41:22.610Z  W  [SECURITY] signature_mismatch dev=***7b2c reason=ts_skew skew_sec=412
```

**سرور (JSON → stdout → Loki):**
```json
{"ts":"2026-09-28T12:41:22.104Z","level":"INFO","tag":"PAIRING","event":"code_redeemed",
 "request_id":"01J8ZK6Q9V","family_id":"***","actor":"parent:***","device_id":"***",
 "duration_ms":84,"status":201}
```

### ۴.۳ قواعد Redaction (اجباری — با CI test)

| هرگز لاگ نشود | جایگزین |
|---|---|
| password, password_hash | `[REDACTED:password]` |
| access_token, refresh_token, device_secret | فقط `token_id` (jti) و ۴ کاراکتر آخر |
| pairing code کامل | `code_prefix` + `pairing_id` |
| Authorization / X-Signature headers | حذف کامل |
| lat/lng دقیق | `geohash6` یا `lat=35.7***` (۲ رقم اعشار) |
| email کامل | `p***@example.com` |
| phone, national id | `[REDACTED:pii]` |
| device_id / family_id کامل | ۴ کاراکتر آخر (`***7b2c`) |
| body کامل درخواست | فقط size + schema version |
| FCM token | hash |

**ابزار:** `LogRedactor` مرکزی در `core/logging` با regex + allowlist؛ همه لاگ‌ها از `AppLogger` عبور می‌کنند (استفاده مستقیم از `Log.d` با Detekt ممنوع است). در `release` سطح پیش‌فرض `INFO` و بدون نوشتن فایل.

### ۴.۴ چه چیزی در `audit_logs` می‌رود (نه Logcat)

`auth.register/login/logout/failed/locked/password_changed` · `pairing.code_created/redeemed/confirm/revoked/expired/brute_force` · `device.added/renamed/removed/logout/revoked/suspended/uninstalled_detected` · `permission.granted/revoked/changed` · `consent.granted/revoked` · `security.signature_failed/replay_detected/token_reuse/rls_violation/rate_limited` · و در Phaseهای بعد `location.accessed` (چه کسی، کی، کدام دستگاه — **برای پاسخگویی والد هم مهم است**)، `screen_time.limit_changed`، `geofence.created`، `sos.triggered`.

---

## ۵. حریم خصوصی و حقوقی (برای محصول واقعی ضروری است)

| موضوع | اقدام در معماری |
|---|---|
| **GDPR / GDPR-K (زیر ۱۶ سال)** | مبنای قانونی = رضایت + منافع حیاتی (SOS). حق دسترسی/حذف/انتقال ⇒ endpointهای `export` و `delete` در Phase 9. `birth_year` برای تشخیص کودک |
| **COPPA (اگر بازار US)** | Parental consent gate؛ داده کودک زیر ۱۳ بدون حساب والد ذخیره نمی‌شود |
| **Data minimization** | فقط داده‌ای که `data_consents.granted=true` است پذیرفته می‌شود — **سرور هم بررسی می‌کند، نه فقط UI** (`403 CONSENT_REQUIRED`) |
| **Purpose limitation** | هر `capability` یک purpose مشخص دارد؛ در صفحه افشای اتصال و نمای فقط‌خواندنی فرزند نمایش داده می‌شود |
| **Retention** | جدول Retention در سند ۰۳ + job پاک‌سازی روزانه |
| **افشا به کودک** | نوتیف دائمی FGS: «GuardianLink در حال اجرا — خانواده رضایی به وضعیت این دستگاه دسترسی دارد» (الزام Android) + افشا در لحظه اتصال + نمای فقط‌خواندنی موارد فعال |
| **مدیریت فقط توسط والد (v0.2)** | دکمه‌های «توقف موقت / قطع اتصال / لغو رضایت» توسط فرزند **به درخواست مالک محصول حذف شدند**؛ `data_consents.revoked_at` فقط توسط والد/admin ست می‌شود. نمای فرزند فقط‌خواندنی است و فرزند از طریق آن می‌داند چه مواردی فعال است |
| **Google Play Data Safety** | در Phase 9 فرم کامل بر اساس همین جداول تولید می‌شود (خروجی خودکار از `data_consents` + capability matrix) |
| **حذف حساب** | `DELETE /v1/auth/me` ⇒ soft delete + purge سخت بعد از ۳۰ روز + revoke همه دستگاه‌ها |

---

## ۶. امنیت Backend (Hardening)

- **Secrets:** فقط از environment/secret manager؛ `.env` در `.gitignore`؛ `deploy/scripts/gen-keys.sh` کلیدها را تولید می‌کند.
- **کمترین دسترسی DB:** نقش `gl_app` بدون `BYPASSRLS` و بدون DDL؛ نقش جدا برای Flyway.
- **Rate limiting:** Redis sliding window + token bucket (جدول در سند ۰۴).
- **Input validation:** هر endpoint یک `@Serializable` schema + validator (طول، range، regex) ⇒ هیچ رشته خامی به SQL نمی‌رسد (Exposed = prepared statements).
- **Headers امنیتی:** `Strict-Transport-Security`, `X-Content-Type-Options: nosniff`, `Referrer-Policy: no-referrer`, `Content-Security-Policy` (برای Swagger).
- **Body limits:** ۲۵۶ KB / ۱ MB برای batch.
- **Error leakage:** در production هیچ stack trace به کلاینت نمی‌رود؛ فقط `request_id`.
- **CORS:** بسته به‌جز دامنه‌های مشخص.
- **Dependency scanning:** Dependabot + `gradle dependencyCheckAnalyze` در CI + `pip-audit`/`trivy` برای imageها.
- **Backup:** `pg_dump` روزانه + WAL archive؛ رمزنگاری backupها؛ تست restore ماهانه.
- **Monitoring:** Prometheus metrics (`http_requests_total`, `pairing_attempts`, `signature_failures`, `rls_denied`) + alert روی نرخ غیرعادی.
- **Anomaly detection (Phase 9):** login از کشور جدید، جهش غیرممکن موقعیت، heartbeat از دو IP همزمان.

---

## ۷. امنیت سمت کلاینت

| مورد | اقدام |
|---|---|
| Tamper detection | بررسی root (بدون مسدودسازی، فقط گزارش) · emulator · hooking frameworks (Frida/Xposed) · debugger |
| Screen security | `FLAG_SECURE` در صفحات حساس (pairing code، settings) — قابل تنظیم |
| Clipboard | کد pairing فقط ۶۰ ثانیه در کلیپ‌بورد و با `ClipDescription.EXTRA_IS_SENSITIVE` |
| Backup | `allowBackup=false` برای فایل‌های رمزنگاری‌شده؛ `data_extraction_rules` سخت‌گیرانه |
| ProGuard/R8 | full mode + obfuscation + حذف logging در release |
| Deep link | اعتبارسنجی کامل `gl1://pair` (فقط کد خوانده می‌شود، هیچ پارامتری اعتماد نمی‌شود؛ `s` فقط اگر در allowlist دامنه باشد) |
| WebView | اگر استفاده شد: `JavaScriptEnabled=false`, بدون `addJavascriptInterface` |
| مجوزهای زمان اجرا | هرگز بیش از نیاز درخواست نمی‌شود؛ `PermissionOrchestrator` وضعیت را قبل از درخواست بررسی می‌کند |
| داده در Room | `data_consents` و داده حساس رمزنگاری‌شده؛ کش فقط برای نمایش |

### ۷.۱ خط قرمزهای پروژه (v2 — غیرقابل مذاکره)
بدون Root Exploit · بدون Spyware/Malware · بدون **Accessibility Abuse** (سرویس کاملاً حذف شده) · بدون Exploit/0day · بدون دور زدن Android Security (از جمله پنهان‌کردن اپ از سیستم یا کاربر) · بدون دور زدن Google Play Policies (مجوزهای حساس فقط با declaration رسمی؛ توزیع APK مستقیم تا Phase 10 یک تصمیم توزیع است نه دور زدن policy). هر جا OS پیشگیری رسمی می‌دهد (Device Owner / Android Enterprise) همان پیاده‌سازی می‌شود؛ وگرنه **تشخیص + گزارش + بازیابی**. جزئیات: [`11-anti-tampering.md`](./11-anti-tampering.md)

---

## ۸. چک‌لیست امنیتی Phase 1 (Definition of Security Done)

- [ ] TLS اجباری در staging/release + pinning با دو pin
- [ ] Argon2id + soft lockout بعد از ۱۰ تلاش ناموفق
- [ ] JWT ۱۵ دقیقه + refresh چرخشی + reuse detection (تست خودکار دارد)
- [ ] HMAC device signing + nonce anti-replay (تست خودکار دارد)
- [ ] Pairing code: hash-only, TTL ۱۰ دقیقه, ۱۰ تلاش, rate limit
- [ ] RLS روی همه جداول tenant-scoped (تست: کاربر خانواده A → ۴۰۳ برای خانواده B)
- [ ] SecureStore با Tink+Keystore (بدون `EncryptedSharedPreferences`)
- [ ] `allowBackup=false` + `data_extraction_rules`
- [ ] LogRedactor فعال + تست CI که stringهای ممنوع را در لاگ پیدا می‌کند
- [ ] `audit_logs` برای همه ۲۰+ اقدام حساس Phase 1
- [ ] Remove Device ⇒ revoke + purge + unpair command (Test 8)
- [ ] Rate limiting روی همه مسیرهای عمومی
- [ ] `request_id` سراسری در لاگ کلاینت و سرور
