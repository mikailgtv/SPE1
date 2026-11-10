# گزارش Phase 1 — Authentication + Family + Pairing + Dashboard

> وضعیت: **پیاده‌سازی کامل شد.**
> ورژن‌های وریدای‌شده در محیط build: Kotlin 2.4.20 · Gradle 9.7.1 · AGP 9.4.1 · compileSdk 36 · Ktor 3.6.0 · JDK 17.
>
> **تأییدیه‌های گرفته‌شده قبل از تحویل:**
> - سرور: `compileKotlin` ✅ و `gradle test -PskipIT` ✅ (۵ تست واحد سبز: Redact، rate limiter، Argon2id، sha256، فرمت کد).
> - اندروید: کامپایل کامل هر ۱۰ ماژول ✅ + لینک منابع/manifest ✅ + خروجی هر دو APK debug ✅ (`app-parent-debug.apk` و `app-child-debug.apk`).
> - تست‌های Integration سرور (Testcontainers) و تست‌های واحد اندروید روی ماشین شما اجرا می‌شوند (نیاز به Docker/RAM معمولی؛ دستور پایین).

---

## ۱) Files Created

```
guardianlink/
├── server/                          # بک‌اند Ktor 3.6 (Kotlin/JVM 17)
│   ├── build.gradle.kts / settings.gradle.kts / gradle.properties / Dockerfile
│   └── src/main/kotlin/ir/guardianlink/server/
│       ├── Main.kt                  # ماژول Ktor، ContentNegotiation، StatusPages، routing
│       ├── Config.kt                # تنظیمات از env (TTLها، lease، DB)
│       ├── Db.kt                    # HikariCP + Flyway + helperهای JDBC
│       ├── Models.kt                # DTOها
│       ├── Errors.kt                # کدهای خطا + ApiError
│       ├── Log.kt                   # لاگ ساختاریافته AUTH/PAIRING/DEVICE/NETWORK/SECURITY + Redact
│       ├── Crypto.kt                # SHA-256 + کد Crockford
│       ├── Tokens.kt                # JWT EdDSA (Ed25519) + refresh opaque + revocation با sid
│       ├── PasswordHasher.kt        # Argon2id
│       ├── RateLimiter.kt           # sliding-window در حافظه
│       ├── Auth.kt                  # requireUser / requireDevice (بررسی زنده session)
│       ├── Repos.kt                 # لایه داده (users/families/devices/pairing/sessions)
│       ├── UserRoutes.kt            # register/login/refresh/logout/me/families/devices(parent)
│       └── DeviceRoutes.kt          # pairing exchange/confirm + device auth/refresh/heartbeat
│   ├── src/main/resources/db/migration/V1__phase1.sql
│   └── src/test/kotlin/...          # AuthTest, PairingTest, DeviceTest, SecurityTest, UnitTest
├── android/                         # ۱۰ ماژول Gradle
│   ├── settings.gradle.kts / build.gradle.kts / gradle.properties / gradle/libs.versions.toml
│   ├── core/common/                 # AppLog+Redact، Validators (+تست)
│   ├── core/storage/                # SecureStore (Keystore AES-GCM + DataStore)، PlainPrefs
│   ├── core/network/                # ApiClient (OkHttp+Gson)، چرخش refresh، GlServices (+تست MockWebServer)
│   ├── core/designsystem/           # GlTheme (M3، RTL، dark)، کامپوننت‌ها، رشته‌های خطا fa/en
│   ├── feature/auth/                # Login/Register/CreateFamily
│   ├── feature/pairing/             # ParentPairing (کد+QR+شمارش معکوس)، ChildSetup+Confirm
│   ├── feature/dashboard/           # داشبورد والد + Remove Device + poll 30s
│   ├── feature/childstatus/         # StatusSyncWorker (WorkManager 15m)، NetworkWatcher، ChildHome
│   ├── app-parent/                  # Manifest، Splash-nav، تم، network security config
│   └── app-child/                   # Manifest بدون مجوز اعلان، RootNav، ZeroNotificationTest
└── deploy/
    ├── docker-compose.yml           # postgres:17 + server (+caddy با پروفایل prod)
    ├── Caddyfile                    # HTTPS برای تولید
    └── .env.example
```

## ۲) Architecture

- **Parent App ↔ Backend ↔ Child App**؛ هیچ ارتباط مستقیم دستگاه‌به‌دستگاه نیست.
- **Auth والد**: Argon2id → access JWT (EdDSA، ۱۵ دقیقه، شامل `sid`) + refresh opaque با **چرخش** (هر استفاده، قبلی را باطل می‌کند). revocation فوری: هر درخواست `sid` را از جدول session چک می‌کند.
- **Auth فرزند**: پس از pairing، `device_id + device_secret` (یک‌بار صادر می‌شود، فقط hash در DB) → `/v1/device/auth` → توکن نوع `device` با همان مکانیزم چرخش/revocation.
- **Pairing امن**: کد Crockford ۸ کاراکتری (فقط hash در DB) + TTL پیش‌فرض **۵ دقیقه** (قابل تنظیم با `PAIRING_TTL_SEC`) + وضعیت used/active/exchanging + مرحله exchange (توکن موقت ۱۲۰ ثانیه‌ای) + confirm ⇒ **replay غیرممکن**: کد مصرف‌شده `PAIRING_USED`، توکن exchange مصرف‌شده rejected.
- **Presence**: سرور `online` می‌گوید اگر `last_seen` جدیدتر از `PRESENCE_LEASE_SEC` (پیش‌فرض ۱۲۰۰، configurable) باشد. کودک: WorkManager دوره‌ای ۱۵ دقیقه + sync فوری هنگام باز شدن اپ و هنگام وصل شدن شبکه (NetworkCallback). **بدون Foreground Service و بدون هیچ notification.**
- **حذف دستگاه**: status=removed + secret hash پاک + همه device_sessionها revoke ⇒ heartbeat بعدی فرزند `410 DEVICE_REMOVED` ⇒ اپ فرزند به صفحه «قطع شده» می‌رود و credential را پاک می‌کند.
- **Family-level access control**: همه endpointهای والد با membership جدول `family_members` محدودند؛ دستگاه خانواده دیگر ⇒ 404.
- **Rate limit**: login/register/refresh/exchange/confirm/device-auth (sliding-window؛ تک‌نمونه؛ Redis در Phase 10).

## ۳) Setup Instructions (پیش‌نیازها)

- JDK 17، Android Studio (یا SDK 36 + build-tools 36)، Gradle 9.7.1 (یا wrapper بسازید: `gradle wrapper --gradle-version 9.7.1`)
- Docker + Docker Compose (برای بک‌اند و تست‌های integration)
- دو گوشی اندروید ۸+ (API 26+) روی یک Wi-Fi با ماشین شما

## ۴) Backend Setup + Database Setup

```bash
cd guardianlink/deploy
cp .env.example .env        # رمز DB را عوض کنید
docker compose up -d --build
# DB خودش با Flyway migrate می‌شود (V1__phase1.sql): users, families, family_members,
# devices, pairing_sessions, user_sessions, device_sessions — فقط hash، بدون plaintext.
curl http://localhost:8080/health     # {"status":"ok"}
```
برای تست سریع offline/online روی گوشی: `PRESENCE_LEASE_SEC=180 docker compose up -d` (یا در `.env`).

## ۵) Build Instructions (اندروید)

```bash
cd guardianlink/android
# آدرس سرور روی LAN خودتان (IP ماشین از ipconfig/ifconfig):
echo "guardianBaseUrl=http://192.168.1.X:8080" >> local.properties   # یا -PguardianBaseUrl=...
gradle :app-parent:assembleDebug :app-child:assembleDebug
# APKها:
#   app-parent/build/outputs/apk/debug/app-parent-debug.apk
#   app-child/build/outputs/apk/debug/app-child-debug.apk
```
- build **debug** cleartext فقط برای LAN مجاز است (network security config جدا در `src/debug/`)؛ build **release** فقط HTTPS.
- امضای release: در Phase 10 (keystore + Play signing). فعلاً APK debug برای تست کافی است.

## ۶) Install on both devices

```bash
adb -s <serial-parent> install -r app-parent/build/outputs/apk/debug/app-parent-debug.apk
adb -s <serial-child>  install -r app-child/build/outputs/apk/debug/app-child-debug.apk
```
(یا با Android Studio Run روی هر دستگاه.)

## ۷) Real Device Test Guide (۱۷ گام)

1. نصب Parent روی Phone A.
2. ساخت Account (ایمیل + رمز ≥۸) → ورود خودکار.
3. ساخت Family (نام دلخواه).
4. نصب Child روی Phone B؛ صفحه «Connect to Parent».
5. در Parent: Dashboard → «افزودن دستگاه فرزند» ⇒ کد ۸ کاراکتری + QR + شمارش معکوس ۵ دقیقه.
6. در Child: کد را وارد کنید → Connect.
7. صفحه تأیید: نام خانواده + نام والد + نام دستگاه → «تأیید و اتصال».
8. Parent: Dashboard دستگاه را نشان می‌دهد (نام، مدل، نسخه اندروید).
9. وضعیت 🟢 Online (heartbeat اولیه ≤ چند ثانیه؛ Child اپ باز است).
10. باتری و «آخرین همگام‌سازی» مقدار دارند.
11. اینترنت Child را قطع کنید (حالت هواپیما).
12. با `PRESENCE_LEASE_SEC=180`: پس از ~۳ دقیقه refresh والد ⇒ 🔴 Offline. (با lease پیش‌فرض ۲۰ دقیقه.)
13. اینترنت Child را وصل کنید ⇒ NetworkCallback یک sync فوری صف می‌کند.
14. Parent refresh ⇒ 🟢 Online (معمولاً < ۳۰ ثانیه).
15. Parent: Remove Device → تأیید دیالوگ.
16. Child: چند ثانیه بعد (sync بعدی) صفحه «اتصال قطع شده»؛ heartbeat/refresh قبلی ⇒ 401/410؛ داشبورد والد دستگاه را نشان نمی‌دهد.
17. همان کد pairing قبلی را در Child (نصب تازه/دستگاه دیگر) وارد کنید ⇒ خطای «این کد قبلاً استفاده شده است».

## ۸) Automated Tests

سرور (نیازمند Docker برای integration):
```bash
cd guardianlink/server
gradle test                # همه (واحد + integration با Testcontainers postgres:17)
gradle test -PskipIT       # فقط واحد (بدون Docker)
```
- Auth: ورود معتبر/نامعتبر، انقضای access (SESSION_EXPIRED)، چرخش refresh و replay آن، logout، validation.
- Pairing: جریان کامل، کد نامعتبر، منقضی، استفاده‌شده، replay توکن exchange، دستگاه تکراری، نیاز به auth/family.
- Device: ثبت اطلاعات پایه، heartbeat/باتری/last_seen، offline بعد از lease، validation باتری، حذف دستگاه و revoke.
- Security: دستگاه بدون توکن، توکن user روی endpoint دستگاه و برعکس، دسترسی خانواده غریبه (404)، rate limit login، secret غلط.
- واحد: Redact (نشتی توکن/ایمیل/کد)، فرمت کد Crockford، sha256، rate limiter، Argon2id.

اندروید:
```bash
cd guardianlink/android
gradle :core:common:testDebugUnitTest :core:network:testDebugUnitTest :app-child:testDebugUnitTest
```
- Validators/Redact، چرخش refresh روی 401 با MockWebServer (+ پاک‌شدن creds پس از شکست)، **ZeroNotificationTest** (اسکن سورس child: هیچ NotificationManager/startForeground/POST_NOTIFICATIONS).

## ۹) Known Limitations (صادقانه)

- **تشخیص Offline بدون FCM**: تا سپری‌شدن lease طول می‌کشد ( configurable ). «push-verified ping» (FCM data-only) طبق docs/12 در Phase 8 می‌آید؛ فعلاً بازیابی Online سریع است (NetworkCallback) ولی تشخیص Offline = lease.
- **Rate limiter در حافظه** است (تک‌نمونه)؛ Redis در Phase 10.
- **QR فقط نمایشی در Parent** است؛ اسکن QR در Child (دوربین) در Phase 1 اضافه می‌شود اگر بخواهید؛ فعلاً ورود دستی کد.
- **Heartbeat دوره‌ای ۱۵ دقیقه** (حداقل WorkManager)؛ در Doze ممکن است دیرتر شود ⇒ lease حباب امنیتی است. chained alarms طبق docs/12 در Phase 2/8 تکمیل می‌شود.
- **زبان**: فارسی پیش‌فرض + انگلیسی؛ سوییچ دستی زبان درون‌اپ در Phase 10 (فعلاً از locale دستگاه پیروی می‌کند).
- **HTTPS**: در LAN تست با cleartext-only-debug؛ تولید با Caddy/Let's Encrypt + pinning در Phase 10.
- فقط **یک خانواده per user** و نمایش چند دستگاه پشتیبانی می‌شود ولی UI فعلی لیست ساده است.

## ۱۰) Next Phase Proposal (Phase 2)

**Device Status عمیق + Battery History + Last Sync دقیق + Presence push-verified**:
- تاریخچه باتری/آنلاین (جدول `device_status_samples`) + نمودار ساده در Dashboard
- chained alarms برای heartbeat قابل اتکاتر در Doze
- FCM data-only ping برای تأیید online/offline ≤ ~۷۰ ثانیه وقتی والد Dashboard را باز می‌کند (طبق docs/12)
- اسکن QR در Child (اختیاری شما)
منتظر `APPROVED` / `FIX` / `NEXT PHASE` شما هستم.
