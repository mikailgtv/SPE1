# 02 — ساختار پروژه (Project Structure)

## ۱. نمای کلی Repository (Monorepo)

```
guardianlink/
├── android/                        # کلاینت‌ها
│   ├── settings.gradle.kts         # includeBuild + module registry
│   ├── build.gradle.kts
│   ├── gradle/libs.versions.toml   # Version Catalog (تنها منبع نسخه‌ها)
│   ├── gradle.properties
│   ├── build-logic/                # Convention Plugins (کد build مشترک)
│   │   ├── settings.gradle.kts
│   │   └── convention/
│   │       ├── src/main/kotlin/
│   │       │   ├── AndroidApplicationConventionPlugin.kt
│   │       │   ├── AndroidLibraryConventionPlugin.kt
│   │       │   ├── AndroidComposeConventionPlugin.kt
│   │       │   ├── AndroidHiltConventionPlugin.kt
│   │       │   ├── AndroidRoomConventionPlugin.kt
│   │       │   ├── AndroidFeatureConventionPlugin.kt
│   │       │   └── JvmLibraryConventionPlugin.kt
│   │       └── build.gradle.kts
│   │
│   ├── app/
│   │   ├── parent/                 # 🟦 اپ والد  → applicationId com.guardianlink.parent
│   │   └── child/                  # 🟩 اپ فرزند → applicationId com.guardianlink.child
│   │
│   ├── core/
│   │   ├── common/                 # Result, AppError, Dispatchers, Clock, Extensions
│   │   ├── model/                  # Domain models خالص (Device, Battery, Permission, Family…)
│   │   ├── logging/                # AppLogger + Tag taxonomy + Redaction
│   │   ├── security/               # Tink/Keystore, SecureStore, DeviceId, Hmac signer, Cert pinning
│   │   ├── network/                # Ktor client, Interceptors, SSE, Retry, Outbox, BackendGateway
│   │   ├── database/               # Room setup, DAO base, Converters, Migrations
│   │   ├── datastore/              # Proto DataStore (settings, session, consent)
│   │   ├── designsystem/           # Theme (M3), Tokens, Components, RTL, Typography
│   │   ├── ui/                     # Composables مشترک (StateView, Cards, Charts, Indicators)
│   │   ├── permissions/            # Permission Orchestrator (یک API برای همه مجوزها)
│   │   ├── telemetry/              # TelemetryReporter contract + Collectors (battery, connectivity…)
│   │   ├── policy/                 # Policy Engine (Phase 2+ استفاده می‌کند، از الان آماده)
│   │   ├── analytics/              # رویدادهای محصول (بدون PII)
│   │   └── testing/                # Fakeها، TestDispatcher، Fixtures (testImplementation)
│   │
│   ├── data/                       # لایه Data مشترک (Repository impl + DataSource + Mapper)
│   │   ├── repository/
│   │   ├── remote/
│   │   ├── local/
│   │   └── mapper/
│   │
│   ├── domain/                     # UseCaseها و Contractها (Kotlin pure)
│   │   ├── auth/  family/  device/  pairing/  telemetry/  notification/
│   │   ├── screentime/  location/  geofence/  appmgmt/  websafety/  sos/
│   │   └── audit/
│   │
│   ├── feature/                    # هر قابلیت = یک ماژول (Compose + ViewModel)
│   │   ├── auth/                   # 🟦 والد: login/register  ·  🟩 فرزند: setup
│   │   ├── onboarding/
│   │   ├── pairing/                # تولید QR (والد) + اسکن/ورود کد (فرزند)
│   │   ├── dashboard/              # 🟦 صفحه اصلی والد
│   │   ├── devices/                # 🟦 لیست/جزئیات دستگاه، حذف، دسترسی‌ها
│   │   ├── settings/               # 🟦 تنظیمات اپ والد
│   │   ├── alerts/                 # 🟦 مرکز هشدارها (Recent Events شامل Tamper)
│   │   ├── notificationsettings/   # 🟦 Toggle per-type + quiet hours + digest + تست
│   │   ├── tamperevents/           # 🟦 لیست رویدادهای [TAMPER] + filter + ack
│   │   ├── auditlog/               # 🟦 نمای Audit Log امنیتی
│   │   ├── childstatus/            # 🟩 وضعیت اتصال در اپ فرزند
│   │   ├── privacyview/            # 🟩 نمای فقط‌خواندنی حریم خصوصی و مجوزها (بدون دکمه کنترل)
│   │   ├── permissionwizard/       # 🟩 ویزارد مجوزها
│   │   ├── sos/                    # 🟩 دکمه اضطراری (Phase 8)
│   │   ├── screentime/             # Phase 3
│   │   ├── location/               # Phase 4
│   │   ├── geofence/               # Phase 5
│   │   ├── appmgmt/                # Phase 6
│   │   ├── websafety/              # Phase 7: managed Chrome URL policies only
│   │   └── navigation/             # NavHost هر اپ (parent/child)
│   │
│   ├── service/                    # سرویس‌های سیستمی Child (قابل جداسازی) — بدون FGS / بدون Notification
│   │   ├── background/             # BackgroundOrchestrator: WorkManager + chained alarms + receivers + outbox
│   │   ├── tamper/                 # Watchdog, AdminReceiver, DO receiver, LaunchForensics, AppOpsWatcher
│   │   ├── monitor/                # UsageStats/ScreenOn collectors (Phase 3)
│   │   ├── enforcement/            # فقط Device Owner enforcer (Phase 6) — بدون Accessibility
│   │   └── managedconfig/          # Device/Profile Owner DPM managed configurations؛ Chrome-only
│   │
│   └── shared-contract/            # DTO + enum های مشترک با سرور (kotlin-jvm، قابل publish)
│
├── server/                         # 🖥 Backend (Ktor)
│   ├── settings.gradle.kts
│   ├── build.gradle.kts
│   ├── app/
│   │   ├── src/main/kotlin/com/guardianlink/server/
│   │   │   ├── Application.kt      # module(), plugins
│   │   │   ├── di/                 # Koin modules
│   │   │   └── config/             # AppConfig (env-based)
│   │   ├── src/main/resources/
│   │   │   ├── application.conf
│   │   │   ├── logback.xml
│   │   │   └── db/migration/       # Flyway: V1__init.sql, V2__…
│   │   └── src/test/kotlin/…
│   ├── modules/{identity,family,pairing,device,telemetry,audit}/…   # Phase 1
│   ├── modules/{location,screentime,appmgmt,websafety,geofence,notify,sos}/…  # Phaseهای بعد
│   └── shared/{errors,persistence,crypto,ratelimit,sse,outbox,observability}/…
│
├── deploy/
│   ├── docker-compose.yml          # postgres + redis + server + caddy
│   ├── docker-compose.dev.yml      # فقط db/redis برای توسعه لوکال
│   ├── Caddyfile                   # TLS خودکار (برای تست با دامنه عمومی)
│   ├── .env.example
│   └── scripts/{start-dev.sh, tunnel.sh, seed.sh, gen-keys.sh}
│
├── docs/                           # همین بسته طراحی + ADRها + راهنمای تست هر Phase
├── tools/                          # اسکریپت‌های کمکی (pairing simulator، load test)
├── .github/workflows/{android-ci.yml, server-ci.yml, e2e.yml}
└── README.md
```

---

## ۲. ساختار داخلی یک ماژول Feature (الگوی ثابت)

```
feature/dashboard/
├── build.gradle.kts
└── src/
    ├── main/kotlin/com/guardianlink/feature/dashboard/
    │   ├── navigation/DashboardNavigation.kt      # route + NavGraphBuilder extension
    │   ├── DashboardScreen.kt                     # composable ورودی
    │   ├── DashboardViewModel.kt                  # StateFlow<DashboardUiState>
    │   ├── DashboardUiState.kt
    │   ├── DashboardEvent.kt                      # user intents
    │   └── components/
    │       ├── ChildStatusCard.kt
    │       ├── BatteryIndicator.kt
    │       ├── QuickActionsGrid.kt
    │       ├── ActivityProgressCard.kt            # Phase 2
    │       └── RecentAlertsList.kt                # Phase 7 (از الان placeholder)
    ├── main/res/values{,-fa,-b+fa+IR}/strings.xml # دوزبانه، RTL
    └── test/kotlin/com/guardianlink/feature/dashboard/
        ├── DashboardViewModelTest.kt
        └── DashboardScreenTest.kt                 # Robolectric/Compose test
```

**قاعده:** هر ماژول feature فقط به `:domain:*`، `:core:*` و `:core:designsystem` وابسته است — **هرگز** به `:data:*` یا به feature دیگر. ارتباط بین featureها فقط از طریق `navigation` routeها.

---

## ۳. قواعد وابستگی ماژول‌ها (اجرا با Gradle + ArchUnit-like test)

```
:app:parent ─► :feature:{auth,onboarding,pairing,dashboard,devices,settings,alerts,navigation}
:app:child  ─► :feature:{auth,onboarding,pairing,childstatus,transparency,permissionwizard,sos,navigation}
:feature:*  ─► :domain:*  ─► :core:model , :core:common
:feature:*  ─► :core:designsystem , :core:ui , :core:permissions
:data:*     ─► :domain:* (پیاده‌سازی interfaceها) , :core:network , :core:database , :core:security
:service:*  ─► :domain:* , :core:telemetry , :core:policy , :data:*
```

| قانون | ابزار اجرا |
|---|---|
| `:domain` هیچ import از `android.*` ندارد (جز annotations) | Detekt custom rule + CI |
| `:feature` هیچ import از `:data` ندارد | Gradle dependency constraint + `module-graph-assert` |
| همه نسخه‌ها فقط در `libs.versions.toml` | Version Catalog (بدون نسخه در build.gradle) |
| هر ماژول `namespace` و `consumer-rules.pro` دارد | Convention Plugin |

---

## ۴. Convention Plugins — چرا؟

به‌جای تکرار ۶۰ بار `android { compileSdk = … }`، همه تنظیمات در `build-logic` است:

| Plugin | چه می‌کند |
|---|---|
| `guardianlink.android.library` | compileSdk 36, minSdk 26, JVM 17, Kotlin 2.4.20, strict mode, lint config |
| `guardianlink.android.application` | + signing, versioning, buildTypes (debug/staging/release), baseline profile |
| `guardianlink.android.compose` | Compose compiler plugin, BOM, Material3, lint compose rules |
| `guardianlink.android.hilt` | Hilt + KSP config |
| `guardianlink.android.room` | Room + schema export directory + migration test setup |
| `guardianlink.android.feature` | library + compose + hilt + test deps (یک خط برای ساخت feature جدید) |
| `guardianlink.jvm.library` | برای `:domain` و `:shared-contract` (بدون Android) |

⇒ **اضافه کردن یک قابلیت جدید = ساخت یک ماژول با یک خط plugin.**

---

## ۵. Build Types و Environmentها

| Build Type | BASE_URL | ویژگی‌ها |
|---|---|---|
| `debug` | `http://10.0.2.2:8080` یا `http://192.168.x.x:8080` (LAN) | cleartext مجاز (network_security_config debug-only), logging کامل, Dev Menu (تغییر سرور، Mock mode) |
| `staging` | `https://staging.guardianlink.local` | pinning فعال با CA استیجینگ، FCM staging |
| `release` | `https://api.guardianlink.app` | Certificate pinning, R8 full mode, logging فقط `WARN+`, Play Integrity |

**Dev Menu (فقط debug):** تغییر Base URL، حالت Mock (بدون سرور)، force offline، reset pairing، تزریق رویداد تستی ⇒ تست Phaseها سریع و تکرارپذیر می‌شود.

---

## ۶. ساختار دقیق فایل‌هایی که در **Phase 1** ساخته می‌شوند

> این فهرست فقط برای دیدن دامنه است — **کد در Phase 1 تحویل می‌شود.**

### ۶.۱ سرور (Phase 1)

```
server/app/src/main/resources/db/migration/
  V1__identity_family.sql        # users, families, family_members, refresh_tokens, sessions
  V2__pairing_device.sql         # pairing_codes, devices, device_credentials, device_status, child_profiles
  V3__telemetry_audit.sql        # permissions, telemetry_events, audit_logs, outbox, push_tokens, rls policies

server/modules/identity/   → routes, service, repo, model  (register/login/refresh/logout/me)
server/modules/family/     → family, members, consents, tenancy guard
server/modules/pairing/    → create/list/revoke code, exchange, confirm
server/modules/device/     → list/get/snapshot/rename/remove/logout, heartbeat
server/modules/telemetry/  → battery+connectivity ingest, snapshot query
server/modules/audit/      → audit writer + security events
server/shared/             → errors(RFC7807), auth(JWT/HMAC), ratelimit(Redis), sse, outbox, crypto(Argon2)
```

### ۶.۲ کلاینت مشترک (Phase 1)

```
core/model         → Device, DeviceStatus, BatteryState, ConnectivityType, PermissionState,
                     PermissionKind, Family, ParentAccount, PairingTicket, DeviceInfo, AppError
core/common        → Result/UiState, AppClock, DispatchersProvider, Constants
core/logging       → AppLogger + LogTag([AUTH][PAIRING][DEVICE][SECURITY][NETWORK][SYNC]) + Redactor
core/security      → SecureStore(Tink+Keystore), DeviceIdProvider, HmacRequestSigner, TokenStore
core/network       → KtorClientFactory, BackendGateway (interface), HttpBackendGateway,
                     InMemoryBackendGateway, AuthInterceptor, SignatureInterceptor,
                     RetryInterceptor, SseClient, OutboxWorker, ConnectivityMonitor
core/database      → AppDatabase, DeviceDao, DeviceStatusDao, PermissionDao, TelemetryDao, OutboxDao
core/datastore     → SessionStore, SettingsStore, ConsentStore (Proto)
core/designsystem  → Theme(M3 light/dark/dynamic), Type(Vazirmatn+Inter), Color tokens,
                     Components: AppCard, StateView, StatusDot, BatteryPill, PrimaryButton, TopBar
core/permissions   → PermissionOrchestrator, PermissionStatusProvider, SystemSettingsLauncher
core/telemetry     → TelemetryReporter, BatteryCollector, ConnectivityCollector, DeviceInfoCollector,
                     PermissionSnapshotCollector
domain/auth        → LoginUseCase, RegisterUseCase, ObserveSessionUseCase, LogoutUseCase, RefreshUseCase
domain/pairing     → CreatePairingCodeUseCase, ObservePairingStateUseCase, RedeemPairingCodeUseCase,
                     ConfirmPairingUseCase, RevokePairingCodeUseCase
domain/device      → ObserveDevicesUseCase, GetDeviceSnapshotUseCase, RemoveDeviceUseCase,
                     RenameDeviceUseCase, LogoutDeviceUseCase, ObserveDeviceStatusUseCase
data/*             → RepositoryImplها + Mapper + Local/Remote DataSource
```

### ۶.۳ اپ والد (Phase 1)

```
feature/auth        → LoginScreen, RegisterScreen, ForgotPasswordScreen (+ViewModels)
feature/onboarding  → 3-slide intro + ساخت Family
feature/pairing     → PairingScreen (QR + کد دستی + countdown + وضعیت زنده)
feature/dashboard   → DashboardScreen: ChildStatusCard, BatteryIndicator, LastSync,
                      LocationStatusRow(غیرفعال/آماده), ScreenTimeRow(آماده), QuickActionsGrid,
                      RecentAlerts(placeholder)
feature/devices     → DeviceListScreen, DeviceDetailScreen (Device Info, Permissions, Sessions,
                      Actions: Rename/Logout/Remove با confirm dialog)
feature/alerts      → AlertsScreen (Recent Events: battery/permission/tamper/presence)
feature/notificationsettings → NotificationSettingsScreen (۸+۲ Toggle + quiet hours + min severity
                      + digest + «ارسال نوتیف آزمایشی»)
feature/tamperevents → TamperEventsScreen (event_type/timestamp/device/status + severity filter + ack)
feature/auditlog    → AuditLogScreen (category/action/result + فیلتر تاریخ + export JSON)
feature/settings    → SettingsScreen (Account, Security/App lock, Language, Theme,
                      Server/Dev, About, Sign out)
feature/navigation  → ParentNavHost + BottomBar (Home · Devices · Alerts · Settings)
app/parent          → ParentApplication, MainActivity, Hilt modules, AndroidManifest
```

### ۶.۴ اپ فرزند (Phase 1)

```
feature/auth            → ChildWelcomeScreen (این اپ چیست + افشای موارد فعال در لحظه اتصال)
feature/onboarding      → ConsentScreen (چه چیزی به اشتراک گذاشته می‌شود) + نام فرزند
feature/permissionwizard→ مجوزهای Phase 1: Notifications (API33+), Battery optimization exemption,
                          Device Admin (اختیاری) — با deep-link مستقیم به Settings و تشخیص وضعیت
feature/pairing         → EnterCodeScreen (۸ کاراکتر) + ScanQrScreen (CameraX/ZXing) + ConnectingScreen
feature/childstatus     → HomeScreen: وضعیت اتصال، آخرین sync، باتری، «والد چه چیزی می‌بیند»
feature/privacyview     → PrivacyViewScreen (فهرست فقط‌خواندنی قابلیت‌های فعال + مجوزهای اپ)
service/background      → BackgroundOrchestrator (PeriodicWork 15m + chained alarms + event receivers)
                          + OutboxFlushWorker + BootReceiver + FcmWakeHandler (data-only, بدون نوتیف)
service/tamper          → GuardianDeviceAdminReceiver + DeviceOwnerReceiver + WatchdogWorker +
                          IntegritySnapshotter + AppOpsWatcher + LaunchForensics + TamperEventQueue
app/child               → ChildApplication, MainActivity, Service declarations, Manifest
```

---

## ۷. ساختار Manifest و مجوزهای Phase 1

**Parent** — `INTERNET`, `ACCESS_NETWORK_STATE`, `POST_NOTIFICATIONS`(33+), `USE_BIOMETRIC` (قفل اپ)

**Child** — `INTERNET`, `ACCESS_NETWORK_STATE`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_DATA_SYNC`,
`POST_NOTIFICATIONS`, `RECEIVE_BOOT_COMPLETED`, `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`,
`WAKE_LOCK`, `BIND_DEVICE_ADMIN` (اختیاری)، و در Phaseهای بعد: `PACKAGE_USAGE_STATS`(appop),
`BIND_ACCESSIBILITY_SERVICE`, `BIND_VPN_SERVICE`, `QUERY_ALL_PACKAGES`, location permissions.

> هر مجوز در همان Phaseای که لازم است اضافه می‌شود و در `network_security_config` / Data Safety مستند می‌گردد (سند ۰۶ و ۰۷).

---

## ۸. نام‌گذاری و قراردادهای کد

| مورد | قرارداد | نمونه |
|---|---|---|
| Package | `com.guardianlink.<layer>.<feature>` | `com.guardianlink.feature.dashboard` |
| UseCase | `<Verb><Noun>UseCase` + متد `operator fun invoke()` | `RemoveDeviceUseCase` |
| Repository | interface در `domain`، `…Impl` در `data` | `DeviceRepository` / `DeviceRepositoryImpl` |
| DTO | `…Dto` + `toDomain()` mapper جدا | `DeviceDto.toDomain()` |
| Entity (Room) | `…Entity` | `DeviceEntity` |
| Entity (DB) | snake_case جمع | `devices`, `device_status` |
| ViewModel | `…ViewModel` + `UiState` + `onEvent()` | `DashboardViewModel` |
| Composable | `PascalCase` بدون پسوند Screen برای کامپوننت‌ها | `ChildStatusCard` |
| Log tag | `[DOMAIN]` | `[PAIRING] code_redeemed family=***` |
| Branch | `phase-1/pairing-server` | — |
| Commit | Conventional Commits | `feat(pairing): add one-time code exchange` |

**قواعد سخت:** هیچ `GlobalScope` · هیچ `!!` در کد production · هیچ `runBlocking` خارج test · هیچ داده حساس در log · همه coroutineها با `AppDispatchers` تزریق‌شده (تست‌پذیر).

---

## ۹. نحوه افزودن یک قابلیت جدید (چک‌لیست ۷ گامی)

1. `feature/<name>` بساز با `guardianlink.android.feature`
2. Model + Contract در `core/model` و `domain/<name>`
3. Route در `BackendGateway` + endpoint در سرور (`modules/<name>`) + یک Flyway migration
4. Repository + DAO + Mapper در `data`
5. UI در `feature/<name>` + افزودن route به NavHost
6. ثبت در `CapabilityRegistry` و `AlertType` (در صورت نیاز)
7. تست‌ها: UseCase + ViewModel + Repo (fake gateway) + API (test-host) + یک سناریوی E2E دستی

⇒ هیچ فایل موجودی **بازنویسی** نمی‌شود؛ فقط **اضافه** می‌شود.
