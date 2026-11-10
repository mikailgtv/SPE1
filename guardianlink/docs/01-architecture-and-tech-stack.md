# 01 — معماری کامل و Tech Stack

## ۱. نمای کلی سیستم (System Context)

```
┌──────────────────┐        HTTPS + SSE         ┌───────────────────────────┐
│   PARENT APP     │ ─────────────────────────► │   GuardianLink Backend    │
│  (Android/Kotlin)│ ◄───────────────────────── │   Ktor 3.6 · PostgreSQL 17│
└──────────────────┘   events: device.*, alert  │   Redis 7 · Flyway        │
                                               └──────────────┬────────────┘
┌──────────────────┘        HTTPS + SSE                      │
│   CHILD APP      │ ─────────────────────────►              │ FCM (Data-only)
│  (Android/Kotlin)│ ◄─────────────────────────              ▼
└──────────────────┘   commands: config.update, revoke   ┌─────────┐
                                                          │  FCM    │ → Push به Parent/Child
                                                          └─────────┘
```

🖼 نسخه گرافیکی: [`diagrams/system-architecture.svg`](./diagrams/system-architecture.svg)

### سه جزء اصلی

| جزء | نقش | تکنولوژی |
|---|---|---|
| **Parent App** | داشبورد، مدیریت خانواده، تنظیم محدودیت، دریافت هشدار | Jetpack Compose · MVVM · Hilt |
| **Child App** | جمع‌آوری داده (با رضایت در لحظه اتصال)، اجرای Policy، نمای فقط‌خواندنی حریم خصوصی، SOS · **صفر Notification (بدون FGS)** | Jetpack Compose · WorkManager/AlarmManager · Device Owner |
| **Backend** | منبع حقیقت (Source of Truth)، Multi-tenant، Auth، Pairing، Realtime، Audit | Ktor · PostgreSQL · Redis |

---

## ۲. معماری کلاینت — Clean Architecture + MVVM (Feature-Modular)

### ۲.۱ لایه‌ها و قانون وابستگی

```
presentation  ──►  domain  ◄──  data
     │                ▲             │
     └── core:* ──────┴─────────────┘
```

> **قانون طلایی:** فلش وابستگی فقط به سمت `domain` و `core` است.
> `domain` هیچ وابستگی به Android Framework ندارد (به‌جز `kotlinx.coroutines`) ⇒ ۱۰۰٪ Unit-testable روی JVM.

| لایه | مسئولیت | فناوری | نمونه |
|---|---|---|---|
| **Presentation** | UI، State، رویدادهای کاربر | Compose · ViewModel · `StateFlow<UiState>` | `DashboardScreen`, `DashboardViewModel` |
| **Domain** | قواعد کسب‌وکار، UseCase، Model خالص، Contractهای Repository | Kotlin Pure · Coroutines | `GetFamilyDevicesUseCase`, `DeviceRepository` (interface) |
| **Data** | پیاده‌سازی Repository، Remote/Local DataSource، Mapper، Offline Queue | Ktor Client · Room · DataStore · WorkManager | `DeviceRepositoryImpl`, `DeviceLocalDao` |
| **Core** | قابلیت‌های عرضی: Log، Result، Crypto، Dispatchers، Clock، Feature Flags | — | `AppLogger`, `SecureStore`, `AppClock` |

### ۲.۲ جریان داده (یک‌طرفه — UDF)

```
User Event ─► ViewModel.onEvent() ─► UseCase ─► Repository ─► Remote / Local
                    │                                              │
              StateFlow<UiState>  ◄────── Mapper ◄─────────────────┘
                    │
              Compose Screen (recompose)
```

### ۲.۳ مدل State استاندارد (برای **هر** صفحه) — پاسخ به بند ۲۰ پرامپت

```kotlin
sealed interface UiState<out T> {
    data object Loading                                  : UiState<Nothing>
    data class  Success<T>(val data: T)                  : UiState<T>
    data object Empty                                    : UiState<Nothing>   // داده‌ای نیست ولی خطا هم نیست
    data class  Error(val error: AppError)               : UiState<Nothing>  // سرور/منطق
    data class  Offline(val cached: T?)                  : UiState<T>        // اینترنت قطع + کش موجود/نیست
}
```

`AppError` یک sealed class با دامنه‌های مشخص است: `Network`, `Unauthorized`, `Forbidden`, `NotFound`, `Validation`, `Server`, `Permission`, `Revoked`, `Unknown` — هرکدام `userMessage` (دوزبانه) و `retryable: Boolean` دارد. **هیچ صفحه‌ای در قطع اینترنت crash نمی‌کند**، چون همه‌ی Repositoryها `Either<Result, Cached>` برمی‌گردانند و لایه UI فقط State را رندر می‌کند.

### ۲.۴ استراتژی Offline-First

| نوع داده | استراتژی |
|---|---|
| Device status / battery / permissions | **Cache-then-Network**: اول Room، بعد SSE/REST؛ `isStale` نمایش داده می‌شود |
| Reports (Screen time / App usage) | Room به‌عنوان منبع نمایش + همگام‌سازی دوره‌ای |
| داده ارسالی Child | **Outbox Queue** در Room ⇒ WorkManager با Backoff ⇒ Idempotency-Key |
| Pairing / Login | فقط Online (بدون کش) — با پیام خطای واضح |

---

## ۳. معماری Backend — Modular Monolith (نه Microservice)

چرا Monolith ماژولار؟ تیم کوچک، سرعت توسعه، Deploy ساده، و **بدون** از دست دادن مرزهای ماژول. مرزها طوری طراحی شده‌اند که بعداً بتوان یک ماژول را به سرویس جدا تبدیل کرد.

```
server/
├── app            → bootstrap, DI (Koin), config, plugins (CORS/Auth/RateLimit/SSE/OpenAPI)
├── modules/
│   ├── identity   → users, auth, sessions, refresh tokens, password reset
│   ├── family     → families, members, consents, tenancy guard (RLS)
│   ├── pairing    → pairing codes, device registration
│   ├── device     → devices, status, heartbeat, permissions, device info
│   ├── telemetry  → battery, connectivity, sync (Phase 1)
│   ├── location   → locations, last-known (Phase 3)
│   ├── screentime → daily/app usage, limits, bedtime (Phase 2)
│   ├── appmgmt    → app policies (Phase 5)
│   ├── websafety  → domain lists, filter policy (Phase 6)
│   ├── geofence   → zones, triggers (Phase 4)
│   ├── notify     → notifications, preferences, FCM push (Phase 7)
│   ├── sos        → emergency (Phase 8)
│   └── audit      → audit logs, security events
└── shared         → errors (RFC7807), persistence (Exposed), crypto, ratelimit, sse, outbox
```

### ۳.۱ Multi-tenancy — سه لایه دفاعی

| لایه | مکانیزم |
|---|---|
| 1. Application | هر UseCase/Service اجباراً `FamilyId` را از `AuthenticatedPrincipal` می‌گیرد، نه از Request Body |
| 2. Repository | کوئری‌ها همیشه `WHERE family_id = :callerFamilyId` — یک `FamilyScopedQuery` wrapper |
| 3. **Database (RLS)** | PostgreSQL Row-Level Security روی جداول حساس + `SET app.family_id` از JWT claim ⇒ حتی اگر باگ در لایه ۱/۲ باشد، داده خانواده دیگر نشت نمی‌کند |

### ۳.۲ Outbox Pattern برای Push

رویدادها در همان Transaction در جدول `outbox` نوشته می‌شوند ⇒ یک Worker آن‌ها را به FCM/SSE می‌فرستد ⇒ **Push هرگز گم نمی‌شود** حتی اگر FCM موقتاً down باشد.

---

## ۴. Tech Stack — نسخه‌های واقعی (بررسی‌شده در سپتامبر ۲۰۲۶)

### ۴.۱ Android Client

| دسته | فناوری | نسخه |
|---|---|---|
| زبان | Kotlin | **2.4.20** |
| Build | Gradle (wrapper) | **9.7.1** |
| Build | Android Gradle Plugin | **9.4.1** |
| Build | JDK / JVM target | **21 / 17** |
| SDK | compileSdk · targetSdk · minSdk | **36 · 36 · 26** |
| KSP | Symbol processing | **2.3.12** |
| UI | Compose BOM | **2026.09.00** |
| UI | Material 3 | از BOM |
| Nav | navigation-compose | **2.10.2** |
| DI | Hilt (dagger) | **2.60.1** |
| DI | hilt-lifecycle-viewmodel-compose | 1.4.0 |
| Local DB | Room | **2.8.5** |
| Prefs | Jetpack DataStore (Proto) | 1.1.x |
| Crypto | **Google Tink** + `androidx.datastore:datastore-tink` | 1.17.x |
| Network | **Ktor Client (OkHttp engine)** | **3.6.0** |
| Async | kotlinx-coroutines | 1.10.x |
| JSON | kotlinx-serialization | 1.9.x |
| Background | WorkManager | 2.10.x |
| Image | Coil 3 (Compose) | 3.x |
| QR | ZXing core + CameraX (ML Kit barcode به‌عنوان جایگزین) | 3.5.x / 1.4.x |
| Location (P3) | play-services-location | 21.x |
| Maps (P4) | play-services-maps / Compose Maps | 19.x |
| Push | firebase-messaging (فقط Push — پشت `PushProvider`) | 24.x |
| Test | JUnit5 · MockK · Turbine · kotlinx-coroutines-test · Robolectric | — |
| UI Test | compose-ui-test · Espresso | — |
| Lint/Format | Detekt · ktlint · Android Lint | — |

> 🔒 نکته مهم: `androidx.security:security-crypto` (EncryptedSharedPreferences) **deprecated** است ⇒ استفاده نمی‌کنیم. جایگزین: DataStore + Tink AEAD با کلید در Android Keystore.

### ۴.۲ Backend

| دسته | فناوری | نسخه |
|---|---|---|
| Runtime | Kotlin/JVM | 2.4.20 · JDK **21** |
| Framework | **Ktor Server (Netty)** | **3.6.0** |
| ORM | Exposed (DAO + DSL) | 0.61.x |
| Migrations | **Flyway** | 11.x |
| DB | **PostgreSQL** | **17** (+ `pgcrypto`, `pg_trgm`; در Phase 4 `postgis` اختیاری) |
| Cache/Rate limit | **Redis** | 7.x |
| Auth | JWT (EdDSA/ES256) + Argon2id (BouncyCastle) | — |
| Validation | kotlinx-serialization + ktor-server-content-negotiation | — |
| API Docs | ktor-server-openapi + Swagger UI | — |
| Observability | Micrometer + Prometheus + Loki (اختیاری در Dev) | — |
| Test | ktor-server-test-host · Testcontainers (Postgres/Redis) · JUnit5 · Kotest | — |
| Deploy | Docker Compose · Caddy (TLS خودکار) · GitHub Actions | — |

### ۴.۳ ابزار توسعه

Android Studio (نسخه Otter 3 Feature Drop یا جدیدتر — لازم برای AGP 9) · Docker Desktop · `adb` · دو گوشی واقعی Android 8+ · Postman/Bruno برای تست API · Charles/`mitmproxy` (فقط در Debug با pin غیرفعال).

---

## ۵. ADR — تصمیم‌های معماری و دلیلشان

### ADR-001 · Self-hosted Ktor به‌جای Firebase-only
**زمینه:** شما Multi-tenant، Rate limiting، Audit Log، Revocation و Schema رابطه‌ای دقیق خواستید.
**تصمیم:** سرور اختصاصی Ktor + Postgres. Firebase **فقط** برای FCM Push.
**پیامد:** نیاز به یک ماشین برای اجرای سرور (در Dev: همان PC شما با Docker). در عوض کنترل کامل داده و هزینه.
**راه فرار:** `BackendGateway` ⇒ اگر خواستید، `FirebaseBackendGateway` (Auth+Firestore) را جایگزین کنید بدون تغییر Domain/UI.

### ADR-002 · یک Repository مشترک، دو اپ جدا
**تصمیم:** ماژول‌های `core:*` و `feature:*` بین Parent و Child **مشترک**‌اند؛ هر اپ فقط ماژول‌های feature مربوط به خودش را شامل می‌شود. دو `applicationId` جدا.
**پیامد:** کد تکراری ~۶۰٪ کاهش؛ جداسازی کامل در سطح اپ حفظ می‌شود.

### ADR-003 · Capability-based Feature Model (پاسخ به «هیچ قابلیتی حذف نشود»)
هر دستگاه یک `CapabilityProfile` دارد که در زمان Pairing محاسبه و در `devices.capabilities` ذخیره می‌شود:

```
CapabilityLevel per feature:
  FULL         → قابلیت کاملاً رسمی و دقیق (مثلاً Battery, Location, Screen Time)
  ASSISTED     → نیاز به یک مجوز ویژه کاربر دارد (Accessibility / VPN / Usage Access)
  OWNER_ONLY   → فقط با Device Owner (مثلاً block واقعی اپ، جلوگیری از حذف)
  UNAVAILABLE  → روی این دستگاه/نسخه Android ممکن نیست
```

UI **هرگز** قابلیتی را نشان نمی‌دهد که `UNAVAILABLE` است، و برای `ASSISTED` یک «راهنمای فعال‌سازی» نشان می‌دهد. ⇒ هیچ قابلیتی از معماری حذف نمی‌شود، فقط سطح اجرای آن صادقانه اعلام می‌شود.

### ADR-004 · SSE به‌جای WebSocket
**تصمیم:** کانال `GET /v1/events` با `text/event-stream`.
**دلیل:** reconnect خودکار + `Last-Event-ID` (بدون از دست دادن رویداد)، کارکرد روی پروکسی/CDN، پیاده‌سازی ساده‌تر در Ktor و OkHttp، مصرف باتری کمتر.
**پیامد:** مسیر uplink همان REST است (کافی است؛ فرمان‌های والد کم و غیر‌بلادرنگ‌اند).

### ADR-005 · Device credential با امضای هر درخواست (HMAC) به‌جای Bearer token
**تصمیم:** دستگاه فرزند یک `device_secret` دارد؛ هر درخواست با `X-Signature = HMAC-SHA256(secret, method|path|timestamp|nonce|sha256(body))` امضا می‌شود.
**دلیل:** Anti-replay قوی، بدون انقضای ناگهانی توکن (تجربه کاربری بهتر برای دستگاه فرزند)، Revocation آنی با یک flag، و سرور فقط `sha256(secret)` را نگه می‌دارد (نشت DB = نشت credential نامفید).
**پیامد:** کمی پیچیدگی در Interceptor کلاینت — یک‌بار نوشته می‌شود.

### ADR-006 · Outbox برای Push و SSE
رویداد ⇒ `outbox` (در همان تراکنش) ⇒ Worker ⇒ FCM/SSE. تضمین At-least-once با `dedupe_key` در سمت کلاینت.

### ADR-007 · زمان سرور = منبع حقیقت
همه‌ی محاسبه‌های Screen time / Bedtime / انقضای Pairing code با `server_time` انجام می‌شود (کلاینت فقط `reported_at` می‌فرستد). ⇒ تغییر ساعت گوشی توسط فرزند، سیستم را دور نمی‌زند.

### ADR-008 · دو اپ، نه یک اپ با دو حالت
**تصمیم:** `:app:parent` و `:app:child` جدا (applicationId جدا، آیکون جدا، مجوزهای جدا).
**دلیل:** مجوزهای Child (Accessibility/VPN/Usage) نباید در اپ والد وجود داشته باشند ⇒ Play Policy و حجم/ریسک کمتر؛ والد می‌تواند اپ خودش را بدون مجوزهای خطرناک نصب کند.

### ADR-009 · مدل کنترل فرزند v0.2 (به درخواست مالک محصول)
**زمینه:** در v0.1 سه وعده به فرزند داده می‌شد: نمای زنده «چه چیزی ارسال شد»، وعده «هرگز خوانده نمی‌شود»، و دکمه «توقف موقت / قطع اتصال توسط فرزند». مالک محصول هر سه مورد و قابلیت‌های پشتشان را حذف کرد.
**تصمیم:**
- پس از اتصال، فرزند **هیچ دکمه توقف/قطع/لغو** ندارد؛ مدیریت، غیرفعال‌سازی و حذف **فقط با والد** است.
- افشای موارد فعال **یک‌بار در لحظه اتصال** (صفحه Disclosure قبل از confirm) انجام می‌شود؛ نمای بعدی فرزند **فقط‌خواندنی** است (تنظیمات حریم خصوصی و مجوزها — مطابق مشخصه اصلی Child App).
- وعده‌های متنی «هرگز ارسال/خوانده نمی‌شود» از UI فرزند حذف شد. (دامنه فنی جمع‌آوری داده تغییری نکرده: خواندن پیام/تماس/عکس هرگز در Roadmap نبوده و اضافه نمی‌شود.)
**چه چیزهایی ماندنی‌اند و چرا:** تأیید فرزند در لحظه Pairing (در خود جریان Pairing مشخصه شماست: «Child confirms pairing») · نمای فقط‌خواندنی مجوزها (مشخصه اصلی: «امکان مشاهده تنظیمات حریم خصوصی و مجوزها») · نوتیف دائمی FGS (الزام Android برای سرویس پس‌زمینه و غیرقابل حذف).
**پیامد فنی:** `data_consents.revoked_at` فقط توسط والد/admin ست می‌شود · `POST /v1/child/consent` حذف و با `GET /v1/child/consents` (فقط‌خواندنی) جایگزین شد · رویداد `child.consent_changed` حذف و `consent.updated` (والد-initiated) جایگزین شد · UI «توقف موقت» حذف.

### ADR-010 · Child بدون هیچ Notification ⇒ حذف کامل Foreground Service (v2)
**زمینه:** Android برای FGS نوتیفیکیشن دائمی **اجباری** می‌کند و راه رسمی حذف آن نیست؛ شما «Child بدون اصلاً Notification» خواستید.
**تصمیم:** Child هیچ FGS ندارد. کار پس‌زمینه = WorkManager periodic (۱۵m) + AlarmManager chained windows + Broadcast receivers رویدادی + **FCM data-only wake**. Presence = «Push-Verified + Baseline»: والد Dashboard را باز می‌کند ⇒ سرور ping می‌فرستد ⇒ تأیید online/offline ≤ ~۷۰ ثانیه؛ بدون آن، lease 20 دقیقه‌ای.
**پیامد:** مصرف باتری کمتر از FGS؛ تأخیر offline در بدترین حالت ~۲۰ دقیقه (صادقانه در UI)؛ SOS بدون FGS (job کوتاه foreground-initiated). جزئیات: [`12-notification-architecture.md`](./12-notification-architecture.md)

### ADR-011 · Enforcement فقط با روش رسمی؛ Accessibility حذف شد (v2)
**زمینه:** خط قرمزهای v2: بدون Accessibility Abuse، بدون exploit، بدون دور زدن Play Policy؛ و «اگر روش رسمی (Device Owner/Enterprise/Family Management) وجود دارد، همان را پیاده‌سازی کن».
**تصمیم:** کنترل واقعی اپ‌ها/حفاظت فقط در **M2 (Device Owner)** با `setPackagesSuspended`/`setApplicationHidden`/`addUserRestriction`/Lock Task. در M0/M1 فقط **تشخیص + گزارش (advisory)**. مسیر enforcement مبتنی بر Accessibility از معماری **حذف** شد. Family Link قابل استفاده توسط third-party نیست ⇒ فقط مرجع UX.
**پیامد:** Capability Ledger در UI برچسب واقعی/سیستمی/مشاوره‌ای/نیازمند-mode می‌زند ([`13`](./13-android-api-and-modes.md) §5)؛ ویزارد DO provisioning (QR zero-touch / adb dpm / AMA) در اپ Parent.

---

## ۶. الگوهای کلیدی که از روز اول ساخته می‌شوند (تا قابلیت بعدی «بدون بازنویسی» اضافه شود)

| الگو | محل | فایده برای Phaseهای بعد |
|---|---|---|
| **Feature Flag / Capability Registry** | `core:common` + `devices.capabilities` | Screen Time / Location / Geofence بدون تغییر Dashboard فعال می‌شوند |
| **Report Pipeline انتزاعی** (`TelemetryReporter`) | `core:telemetry-contract` | هر سنسور جدید فقط یک `Reporter` اضافه می‌کند (Battery, Location, Usage, …) |
| **Policy Engine** (`Policy` → `PolicyEvaluator` → `Enforcer`) | `core:policy` | محدودیت زمان، Bedtime، App limit و Web filter همه از یک موتور استفاده می‌کنند |
| **Permission Orchestrator** | `core:permissions` | یک جریان واحد برای درخواست/بررسی/هدایت به Settings برای همه مجوزها |
| **Outbox + Idempotency** | `core:network` | ارسال دسته‌ای داده بدون تکراری‌شدن |
| **Audit Logger مشترک** | `core:logging` + `audit_logs` | همه Phaseها به‌طور خودکار Audit دارند |
| **Alert Bus** (رویداد → notification) | `feature:alerts` | هر Phase فقط `AlertType` جدید ثبت می‌کند |
| **SecureStore** (Tink+Keystore) | `core:security` | همه داده حساس بدون کد تکراری رمزنگاری می‌شود |
| **Tamper Watchdog** (receivers + worker + forensics) | `service:tamper` | هر Phase فقط فیلد snapshot/رویداد تازه اضافه می‌کند |
| **NotifyPrefs double-gate** | `feature:notification-settings` + `modules/notify` | هر نوع هشدار جدید = یک enum + یک channel + یک ردیف Toggle |

---

## ۷. دیاگرام ماژول‌ها و لایه‌ها

🖼 [`diagrams/system-architecture.svg`](./diagrams/system-architecture.svg)

---

## ۸. ریسک‌های معماری و برنامه مقابله

| ریسک | اثر | مقابله |
|---|---|---|
| OEMها (Xiaomi/Huawei/Samsung) سرویس پس‌زمینه را می‌کشند | داده Child از دست می‌رود | FGS با نوتیف پایدار + `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` + راهنمای Autostart + Outbox + تشخیص و هشدار `DEVICE_RESTARTED` |
| Google Play سیاست مجوزهای حساس را سخت‌گیرانه بررسی می‌کند | رد شدن در Play | توزیع مستقیم APK در Phase 1-2؛ در Phase 9 پرونده Sensitive Permission Declaration + Data Safety (سند ۰۶ و ۰۷) |
| FCM در ایران/شبکه‌های محدود | Push نرسد | SSE + Polling به‌عنوان مسیر اصلی؛ FCM فقط «بیدارکننده» |
| رشد Schema | Migration دردناک | Flyway از روز اول + قرارداد «هر Phase = یک migration» |
| تست روی دو گوشی | پیچیدگی محیط | Docker Compose + LAN + سناریوهای آماده در سند ۱۰ |
