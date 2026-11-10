# 05 — Authentication Flow & Pairing Flow

🖼 دیاگرام‌ها: [`diagrams/auth-token-lifecycle.svg`](./diagrams/auth-token-lifecycle.svg) · [`diagrams/pairing-flow.svg`](./diagrams/pairing-flow.svg)

---

# بخش A — Authentication

## A.1 نقش‌ها و روش احراز هویت

| بازیگر | روش | credential | عمر |
|---|---|---|---|
| **والد** (Parent App) | Email + Password | `access_token` (JWT, EdDSA) + `refresh_token` (opaque) | ۱۵ دقیقه / ۳۰ روز (چرخشی) |
| **دستگاه فرزند** (Child App) | Pairing ⇒ Device Credential | `device_id` + `device_secret` + امضای HMAC هر درخواست | تا Revocation (بدون انقضا، با rotation اختیاری ۹۰ روزه) |
| **سرور → کلاینت** | امضای پاسخ‌های حساس (اختیاری) | `server_public_key` از pairing | — |

## A.2 ثبت‌نام و ورود والد

```
Parent App                                   Backend
   │  POST /v1/auth/register                     │
   │  {email, password, display_name, family}    │
   │ ──────────────────────────────────────────► │  1) validate + rate limit
   │                                             │  2) Argon2id hash
   │                                             │  3) BEGIN TX: insert users, families,
   │                                             │     family_members(owner_parent),
   │                                             │     family_deks(wrap DEK with master key)
   │                                             │  4) issue access+refresh, create session
   │                                             │  5) audit_logs[AUTH][auth.register]
   │  201 {user, family, tokens}                 │  COMMIT
   │ ◄────────────────────────────────────────── │
   │  ذخیره در SecureStore (Tink + Keystore)      │
```

**قواعد رمز عبور:** حداقل ۱۰ کاراکتر، حداقل یک عدد و یک حرف؛ بررسی در برابر لیست ۱۰ هزار رمز لو‌رفته (k-anonymity باprefix هش — اختیاری Phase 9)؛ **هرگز در log یا crash report نمی‌آید**.

**Argon2id params:** `m=64MB, t=3, p=4` ⇒ مقاومت در برابر GPU brute force.

## A.3 Access Token (JWT)

```jsonc
// Header
{ "alg": "EdDSA", "kid": "gl-2026-09", "typ": "at+jwt" }
// Payload
{
  "iss": "https://api.guardianlink.app",
  "sub": "01J8USERID…",                  // user_id
  "aud": "guardianlink-mobile",
  "jti": "01J8TOKENID…",                 // برای revocation list
  "iat": 1790000000, "nbf": 1790000000, "exp": 1790000900,   // ۱۵ دقیقه
  "family_id": "01J8FAMILY…",            // ⭐ tenancy claim
  "role": "owner_parent",
  "perms": ["view","status","manage_devices","manage_limits","admin"],
  "sid": "01J8SESSION…",                 // session id
  "fp": "sha256:9f2c…",                  // device fingerprint binding
  "scope": "family:read family:write device:manage"
}
```

**اعتبارسنجی سمت سرور:** امضا (Ed25519 با `kid` برای rotation بدون downtime) → `exp/nbf/iss/aud` → `fp` برابر fingerprint درخواست → `sid` در `device_sessions` و `revoked_at IS NULL` → `jti` در deny-list (Redis, TTL = عمر باقی‌مانده token) اگر logout/revocation رخ داده.

## A.4 Refresh Token — چرخشی با Reuse Detection

```
access منقضی (401 TOKEN_EXPIRED)
   │
   ▼
POST /v1/auth/refresh {refresh_token, device_fingerprint}
   │
   ├─ token_hash پیدا شد + revoked_at IS NULL + expires_at > now + fp مطابقت
   │     ⇒ used_at = now
   │     ⇒ refresh_token جدید (parent_token_id = قبلی) + access جدید
   │     ⇒ پاسخ 200
   │
   └─ token_hash پیدا شد ولی used_at != NULL  ⚠️  REUSE DETECTED
         ⇒ کل زنجیره (همه tokenهای با همان ریشه) revoke
         ⇒ همه sessionهای آن کاربر revoke (logout همه دستگاه‌ها)
         ⇒ audit_logs[SECURITY][auth.token_reuse] result=failure
         ⇒ ایمیل/نوتیف به والد: «نشست شما به دلایل امنیتی بسته شد»
         ⇒ پاسخ 401 TOKEN_INVALID
```

**چرا؟** اگر refresh token بدزدند، یا دزد یا کاربر واقعی اول استفاده می‌کند؛ استفاده دوم **همیشه** نشانه سرقت است ⇒ واکنش خودکار.

## A.5 Session management

- هر login ⇒ یک ردیف `device_sessions` (با fingerprint و IP).
- صفحه «دستگاه‌های فعال» در Settings والد ⇒ لیست + «خروج از همه».
- تغییر رمز عبور ⇒ revoke همه refresh tokenها و sessionها (جز نشست فعلی).
- `Remove Device` روی فرزند ⇒ revoke `device_credentials` + `device_sessions` دستگاه + push token.

## A.6 احراز هویت دستگاه فرزند (HMAC per-request)

```
X-Device-Id:   01J8DEVICE…
X-Timestamp:   1790000082          (unix seconds)
X-Nonce:       7f3a9c1e5b2d4a68    (۱۶ بایت تصادفی، base64url)
X-Key-Version: 1
X-Signature:   base64url( HMAC-SHA256(device_secret, canonical_string) )

canonical_string =
  METHOD + "\n" + PATH + "\n" + X-Timestamp + "\n" + X-Nonce + "\n" +
  X-Key-Version + "\n" + hex(sha256(raw_body)) + "\n" + content_type
```

**اعتبارسنجی سرور:**
1. `|now - X-Timestamp| ≤ 300s` ⇒ در غیر این صورت `401 DEVICE_SIGNATURE_INVALID` (audit `[SECURITY]`)
2. `nonce` در Redis نباشد (TTL 600s) ⇒ **anti-replay**
3. `device_credentials.revoked_at IS NULL` و `failed_auth_count < 20`
4. `devices.family_id == current_family` و `devices.status = 'active'`
5. محاسبه HMAC و مقایسه **constant-time** (`MessageDigest.isEqual`)
6. خطا ⇒ `failed_auth_count++`؛ ≥ ۲۰ ⇒ `devices.status='suspended'` + Alert به والد (`device.tampered`)

**چرا این روش؟**
- راز در هر درخواست روی سیم نمی‌رود (برخلاف Bearer).
- body و path هم امضا می‌شوند ⇒ **Tamper-proof** (پروکسی/MITM نمی‌تواند payload را عوض کند).
- Replay غیرممکن (timestamp + nonce).
- Revocation آنی (یک UPDATE) و سرور فقط hash راز را دارد.

## A.7 ذخیره‌سازی credential در کلاینت

| داده | محل | رمزنگاری |
|---|---|---|
| `device_secret` | Proto DataStore | **Tink AEAD** با کلید در Android Keystore (`StrongBox` اگر موجود، `setUserAuthenticationRequired(false)` چون سرویس پس‌زمینه لازم دارد) |
| `access_token` | **فقط حافظه** (در `TokenStore` با `MutableStateFlow`) — پایدار نمی‌شود | — |
| `refresh_token` | Proto DataStore | Tink AEAD + Keystore |
| `device_id`, `install_id` | Proto DataStore | Tink AEAD |
| کش داده (Room) | Room DB | فایل DB با `SQLCipher` **اختیاری** (toggle در Phase 9)؛ در Phase 1 داده حساس (مختصات) رمزنگاری‌شده ذخیره می‌شود |
| backup | **غیرفعال** برای فایل‌های رمزنگاری‌شده | `android:allowBackup="false"` + `data_extraction_rules` |

> ⚠️ `EncryptedSharedPreferences` استفاده **نمی‌شود** (deprecated در androidx.security-crypto 1.1.0).

## A.8 قفل اپ والد (App Lock)

BiometricPrompt / PIN محلی (ذخیره hash در Keystore) ⇒ بعد از ۶۰ ثانیه در پس‌زمینه، ورود مجدد لازم است. دسترسی به Dashboard بدون unlock ممکن نیست. (Phase 1: زیرساخت، Phase 9: فعال‌سازی کامل)

## A.9 جریان کامل Token

🖼 [`diagrams/auth-token-lifecycle.svg`](./diagrams/auth-token-lifecycle.svg)

---

# بخش B — Pairing Flow

## B.1 مشخصات کد Pairing

| ویژگی | مقدار |
|---|---|
| الفبا | **Crockford Base32** بدون `I L O U` ⇒ `0123456789ABCDEFGHJKMNPQRSTVWXYZ` |
| طول | **۸ کاراکتر** (۴۰ بیت آنتروپی از `SecureRandom`) |
| قالب نمایش | `7QK4-M2XP` (دو گروه چهارتایی برای خوانایی) |
| عمر | **۱۰ دقیقه** (قابل تنظیم ۵/۱۰/۳۰ در تنظیمات والد) |
| تعداد استفاده | **۱** (`max_uses=1`) |
| حداکثر تلاش | **۱۰** ⇒ سپس `invalidated` |
| ذخیره‌سازی | فقط `SHA-256(code)` در DB |
| Rate limit | ساخت: ۵/دقیقه · exchange: ۵/دقیقه per IP+install_id |
| binding | به `family_id` + `created_by_user_id` |

### QR payload (URI scheme اختصاصی)

```
gl1://pair?c=7QK4M2XP&s=https%3A%2F%2Fapi.guardianlink.app&v=1&f=01J8FAMILY&n=%D8%B3%D8%A7%D8%B1%D8%A7
```
| پارامتر | معنا |
|---|---|
| `c` | کد pairing |
| `s` | Base URL سرور (⇒ اپ فرزند بدون hardcode به سرور درست وصل می‌شود؛ برای self-hosting عالی است) |
| `v` | نسخه پروتکل |
| `f` | family_id (اختیاری، برای اعتبارسنجی زودهنگام) |
| `n` | نام فرزند (URL-encoded، فقط برای نمایش در صفحه تأیید) |

> QR **همچنین** با `intent-filter` روی `gl1://` و `assetlinks` ثبت می‌شود تا اگر اپ نصب بود مستقیم باز شود.

## B.2 جریان کامل (۹ گام)

```
 PARENT APP                        BACKEND                         CHILD APP
     │                                │                                 │
 1.  │ POST /pairing/codes            │                                 │
     │ ──────────────────────────────►│                                 │
     │ 201 {code, qr_payload,         │  insert pairing_codes           │
     │      expires_at, child_profile}│  (code_hash فقط)                │
     │ ◄──────────────────────────────│                                 │
     │  نمایش QR + کد + countdown     │  SSE: pairing.code_created      │
     │                                │                                 │
     │                                │                       2.  کاربر «افزودن به خانواده» را می‌زند
     │                                │                            اسکن QR یا ورود کد ۸ کاراکتری
     │                                │                                 │
     │                                │ 3. POST /pairing/exchange        │
     │                                │ ◄────────────────────────────────│
     │                                │  · rate limit (IP + install_id)  │
     │                                │  · sha256(code) lookup           │
     │                                │  · status='pending'? expires?    │
     │                                │  · attempt_count < max?          │
     │                                │  · device_limit? install_id dup? │
     │                                │  BEGIN TX:                       │
     │                                │   · insert devices(pairing_pending)
     │                                │   · insert device_credentials    │
     │                                │     (secret_hash)               │
     │                                │   · insert device_status         │
     │                                │   · insert child_profiles link   │
     │                                │   · pairing_codes.status=redeemed│
     │                                │   · audit[PAIRING][redeemed]     │
     │                                │  COMMIT + outbox event           │
     │                                │ 201 {device_id, device_secret,   │
     │                                │      consent{…}, heartbeat cfg}  │
     │                                │ ─────────────────────────────────►
     │                                │                            4.  ذخیره در SecureStore
     │  SSE: pairing.redeemed         │                                 │  ⚠️ device_secret فقط در حافظه تا نوشتن
     │ ◄ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ │                                 │
     │  UI: «دستگاه سارا در حال       │                                 │
     │       تأیید است…»              │                                 │
     │                                │                            5.  🧒 صفحه افشا (اجباری، فقط‌خواندنی):
     │                                │                                 │     «والد من چه چیزی می‌بیند؟»
     │                                │                                 │     - وضعیت آنلاین/آفلاین ✔
     │                                │                                 │     - درصد باتری ✔
     │                                │                                 │     - مدل دستگاه ✔
     │                                │                                 │     - مجوزها ✔
     │                                │                                 │     - مکان ⛔ (فعلاً غیرفعال)
     │                                │                                 │     - زمان استفاده ⛔
     │                                │                                 │     [می‌پذیرم]  [می‌خواهم با والد صحبت کنم]
     │                                │                                 │
     │                                │ 6. POST /pairing/confirm         │
     │                                │ ◄────────────────────────────────│
     │                                │  · Device HMAC auth (اولین استفاده از secret)
     │                                │  · insert data_consents (per capability)
     │                                │  · devices.status='active', paired_at=now
     │                                │  · device_sessions(child_device)
     │                                │  · audit[PAIRING][confirmed] + [PRIVACY][consent.granted]
     │                                │  · outbox: pairing.completed     │
     │                                │ 200 {status:'completed'}         │
     │                                │ ─────────────────────────────────►
     │  SSE: pairing.completed        │                            7.  شروع SyncForegroundService
     │  FCM: «دستگاه سارا متصل شد»    │                                 │     (heartbeat هر ۶۰ ثانیه)
     │ ◄──────────────────────────────│                                 │
 8.  │ GET /devices + snapshot        │                                 │
     │ ──────────────────────────────►│                                 │
     │  Dashboard: 🟢 Online · 🔋 74% │                                 │
     │ ◄──────────────────────────────│                                 │
```

### گام ۹ — سناریوهای خطا

| سناریو | پاسخ سرور | رفتار Child App | رفتار Parent App |
|---|---|---|---|
| کد اشتباه | `422 PAIRING_CODE_INVALID {attempts_left: 7}` | پیام «کد نادرست است — ۷ تلاش باقی‌مانده»، لرزش فیلد | — |
| کد منقضی | `422 PAIRING_CODE_EXPIRED` | «کد منقضی شد؛ از والد بخواهید کد جدید بسازد» + دکمه «ارسال درخواست به والد» | countdown قرمز + دکمه «کد جدید» |
| کد استفاده‌شده | `422 PAIRING_CODE_USED` | همان بالا | «این کد قبلاً استفاده شده» |
| بیش از ۱۰ تلاش | `422` + `status=invalidated` + audit `[SECURITY]` | «کد غیرفعال شد» | هشدار: «تلاش ناموفق متعدد برای pairing» |
| سقف دستگاه خانواده | `409 DEVICE_LIMIT_REACHED` | «ظرفیت خانواده پر است» | «برای افزودن، یک دستگاه را حذف کنید» |
| نصب مجدد همان اپ (install_id شناخته‌شده) | `409 ALREADY_PAIRED {reissue:false}` + دستگاه قبلی `status=removed` | پیشنهاد «بازیابی دستگاه قبلی» یا «pairing جدید» | نوتیف «دستگاه سارا دوباره درخواست اتصال داد» |
| اینترنت کودک قطع است | — | صف Outbox + نمایش «در انتظار اتصال» + retry با exponential backoff | countdown متوقف + «دستگاه در حال اتصال نیست» |
| والد قبل از تأیید کودک اپ را ببندد | pairing در حالت `redeemed` می‌ماند (۱۰ دقیقه) | «لطفاً در اپ والد تأیید را کامل کنید» | وقتی برگردد، SSE `pairing.redeemed` را می‌بیند و تأیید می‌کند |
| کودک «می‌خواهم با والد صحبت کنم» را بزند | `POST /pairing/{id}/pause` | حالت انتظار + پیام به والد | نوتیف: «سارا می‌خواهد قبل از اتصال صحبت کند» + دکمه‌های «تأیید می‌کنم»/«لغو» |
| امضای HMAC در `confirm` نامعتبر | `401 DEVICE_SIGNATURE_INVALID` | «خطای امنیتی — لطفاً دوباره pairing کنید» | audit `[SECURITY]` + هشدار |

## B.3 ماشین حالت Pairing

```
                 (parent creates)
   ┌─────────────────────────────┐
   ▼                             │
CREATED ──(redeem ok)──► REDEEMED ──(child confirms consent)──► CONFIRMED ──► COMPLETED
   │                        │                                       │
   │ 10 min                 │ 10 min                                │ device_session created
   ▼                        ▼                                       ▼
EXPIRED                  EXPIRED / PAUSED(child asks to talk)    ACTIVE DEVICE
   │                        │
   └── REVOKED (parent) ────┘        INVALIDATED (10 failed attempts)
```

## B.4 امنیت Pairing — چک‌لیست

- [x] کد خام در DB نیست (فقط SHA-256)
- [x] TTL کوتاه (۱۰ دقیقه) + یک‌بارمصرف
- [x] محدودیت تلاش (۱۰) ⇒ جلوگیری از brute force (۴۰ بیت آنتروپی + rate limit ⇒ عملاً غیرممکن)
- [x] Rate limit روی ساخت و exchange
- [x] TLS اجباری در production (pinning) — QR شامل `https://` است
- [x] `device_secret` فقط یک بار در پاسخ exchange، روی TLS
- [x] Audit کامل با `[PAIRING]`
- [x] تأیید دوطرفه: والد کد می‌سازد، **کودک هم باید تأیید کند** (ضد pairing مخفیانه)
- [x] صفحه افشای اجباری قبل از confirm (رضایت آگاهانه در لحظه اتصال)
- [x] **v0.2:** پس از اتصال هیچ دکمه توقف/قطع/لغو برای فرزند وجود ندارد — مدیریت ۱۰۰٪ با والد
- [x] Play Integrity (Phase 9) برای جلوگیری از pairing با کلاینت جعلی
- [x] کد در لاگ چاپ نمی‌شود (فقط `code_prefix`)

## B.5 UX Pairing — جزئیات

**سمت والد (`feature/pairing`)**
1. انتخاب/ساخت پروفایل فرزند (نام + آواتار + سال تولد اختیاری)
2. نمایش QR بزرگ + کد با دکمه «کپی» + «اشتراک‌گذاری»
3. Countdown دایره‌ای (MM:SS) و نوار پیشرفت؛ در ۶۰ ثانیه آخر زرد، بعد قرمز
4. وضعیت زنده: `در انتظار اسکن → دستگاه پیدا شد → در انتظار تأیید فرزند → متصل شد ✓`
5. دکمه‌های «کد جدید» و «لغو»
6. پس از موفقیت: انیمیشن تیک + دکمه «رفتن به داشبورد»
7. حالت Offline: banner «برای ساخت کد به اینترنت نیاز است» + دکمه retry

**سمت فرزند (`feature/pairing`)**
1. دو تب: «اسکن QR» (CameraX + ZXing، با راهنمای قاب و نور) و «ورود کد»
2. فیلد ۸ کاراکتری با جداکننده خودکار `XXXX-XXXX`، کیبورد عددی/حرفی بزرگ، تصحیح خودکار `I→1`, `O→0`, `L→1`
3. «چسباندن از حافظه» اگر کلیپ‌بورد شامل `gl1://pair` باشد
4. صفحه Connecting با ۴ مرحله و progress (ارسال → تأیید سرور → رضایت → اتصال)
5. خطاها با زبان ساده + اقدام مشخص (نه فقط متن خطا)
