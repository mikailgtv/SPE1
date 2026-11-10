# 07 — محدودیت‌های Android و سطح واقعی کنترل

> ⚠️ **مهم‌ترین سند این بسته.** انتظار شما: «مثل یک اپ اندروید نصب شود و هیچ قابلیتی حذف نشود و گوگل محدودش نکند».
> پاسخ صادقانه مهندسی: **نصب مثل اپ معمولی ممکن است (APK/Play)، ولی Android به اپ ثالث اجازه «کنترل مطلق» نمی‌دهد.** راه‌حل ما: **معماری Capability-based با سه سطح اجرا** که *هیچ قابلیتی را از محصول حذف نمی‌کند*، بلکه برای هرکدام **قوی‌ترین مسیر ممکن** را انتخاب می‌کند و در UI صادقانه نشان می‌دهد.

---

## ۱. سه سطح کنترل (Capability Levels)

| سطح | نام | چگونه به دست می‌آید | چه چیزی ممکن می‌شود |
|---|---|---|---|
| **M0** | Normal | نصب معمولی + مجوزهای runtime | مانیتورینگ، گزارش، advisory، تشخیص tamper |
| **M1** | Device Admin | M0 + فعال‌سازی Device Admin توسط کاربر | early-warning رسمی قبل از uninstall (callbackهای سیستم) |
| **M2** | Device Owner | provisioning رسمی: QR zero-touch / `adb dpm` / Android Management API | block/suspend واقعی، جلوگیری از uninstall/force-stop/clear-data، restrictions، kiosk |

> **v2:** نام‌های قدیمی L0/L1/L2 جای خود را به M0/M1/M2 دادند و مسیر enforcement مبتنی بر **Accessibility حذف شد** (Accessibility Abuse در خط قرمزها). جدول کامل مقایسه Normal vs Managed: [`13-android-api-and-modes.md`](./13-android-api-and-modes.md) §3.

هر دستگاه در زمان Pairing یک `capabilities` map می‌گیرد و در `devices.capabilities` ذخیره می‌شود. **UI هرگز قابلیتی را که در سطح فعلی ممکن نیست، به‌صورت «فعال» نشان نمی‌دهد** — بلکه دکمه «ارتقای کنترل» با راهنمای دقیق نمایش می‌دهد.

---

## ۲. ماتریس قابلیت‌ها (۱۲ قابلیت × واقعیت فنی)

| قابلیت | ممکن؟ | API رسمی | مجوز | سطح | محدودیت و راه‌حل ما |
|---|---|---|---|---|---|
| **حضور Online/Offline** | ✅ کامل | WorkManager periodic + chained alarms + event receivers + **FCM data-only ping** + lease سرور | `INTERNET`, `ACCESS_NETWORK_STATE` | M0 | **بدون FGS و بدون نوتیف (v2)**: baseline 15m/lease 20m + push-verified ≤ ~70s وقتی والد dashboard را باز می‌کند — سند ۱۲ |
| **درصد باتری** | ✅ کامل | `BatteryManager` / sticky broadcast `ACTION_BATTERY_CHANGED` | هیچ (normal) | M0 | در Doze ممکن است به‌روز نشود ⇒ از `is_charging`/`battery_status` و آخرین مقدار معتبر + `stale` flag |
| **Last Sync** | ✅ کامل | سرور (`server_ts`) | — | M0 | منبع حقیقت سرور است (دستکاری ساعت بی‌اثر) |
| **اطلاعات دستگاه** | ✅ کامل | `Build.*`, `PackageManager`, `StorageStatsManager`, `SystemClock` | هیچ | M0 | `IMEI/Android ID` **استفاده نمی‌شود** (حریم خصوصی + Play policy) ⇒ `install_id` تصادفی |
| **وضعیت مجوزها** | ✅ کامل | `Context.checkSelfPermission`, `AppOpsManager`, `isDeviceOwnerApp`, `NotificationManagerCompat.areNotificationsEnabled` | برخی نیاز به `PACKAGE_USAGE_STATS` برای خواندن appop | M0 | برای appopها از reflection رایج `AppOpsManager.checkOpNoThrow` استفاده می‌کنیم (در API 29+ `unsafeCheckOpNoThrow`) |
| **حذف دستگاه / Revocation** | ✅ کامل | سمت سرور + پاک‌سازی محلی | — | M0 | — |
| **Screen Time (روزانه/هفتگی)** | ✅ دقیق ~۹۰٪ | `UsageStatsManager.queryEvents` / `queryUsageStats` | **`PACKAGE_USAGE_STATS`** (Special app access — کاربر باید دستی در Settings فعال کند) | **M0 + مجوز ویژه** | بدون این مجوز، فقط از `ACTION_SCREEN_ON/OFF` تخمین می‌زنیم ⇒ `source=mixed` و badge «دقت کمتر» |
| **App Usage (per-app)** | ✅ | `UsageStatsManager.queryEvents(MOVE_TO_FOREGROUND)` | `PACKAGE_USAGE_STATS` + `QUERY_ALL_PACKAGES` | **M0 + مجوز ویژه** | `QUERY_ALL_PACKAGES` در Play نیاز به declaration دارد؛ برای APK مستقیم مشکلی نیست |
| **Daily Limit / Bedtime (اجرا)** | ⚠️ | **M2:** `setPackagesSuspended` + restrictions + exact-alarm grant · **M0:** advisory (رویداد limit_reached به والد) | `SCHEDULE_EXACT_ALARM` (grant کاربر) · DO | **M2 / advisory** | **v2:** مسیر Accessibility+overlay **حذف شد** (خط قرمز). بدون M2 «block سخت» وجود ندارد ⇒ برچسب `ADVISORY` و گزارش به والد؛ با M2 اجرای واقعی سیستمی |
| **Block/Restrict یک اپ مشخص** | ⚠️ | **فقط M2:** `DevicePolicyManager.setPackagesSuspended()` / `setApplicationHidden()` / `addUserRestriction(DISALLOW_*)` · M0: advisory (گزارش به والد) | M2: Device Owner | **M2** | مسیر Accessibility در v2 **حذف شد** (خط قرمز). در M0 block واقعی غیرممکن ⇒ برچسب صادقانه `ADVISORY` |
| **جلوگیری از حذف Child App** | ❌ در L0/L1 · ✅ در L2 | `DevicePolicyManager.setUninstallBlocked()` (API 21+, نیاز به Device Owner) / `setPackagesSuspended` / persistent app | Device Owner | **M2** | در L0/L1: **تشخیص** (`ACTION_PACKAGE_REMOVED` در یک receiver، و heartbeat timeout ⇒ `device.uninstalled` alert). راه‌حل پیشنهادی: مسیر اختیاری `adb shell dpm set-device-owner` (یک‌بار، با راهنمای تصویری) یا خرید/تنظیم گوشی نو با QR provisioning |
| **Current Location** | ✅ | `FusedLocationProviderClient.getCurrentLocation()` | `ACCESS_COARSE_LOCATION` / `ACCESS_FINE_LOCATION` (runtime) | M0 | در Android 12+ کاربر می‌تواند «تقریبی» بدهد ⇒ `accuracy_m` را نمایش می‌دهیم |
| **Background/History Location** | ✅ با محدودیت | `FusedLocationProviderClient.requestLocationUpdates` + `ACCESS_BACKGROUND_LOCATION` | background location (API 29+: باید جدا و با «Allow all the time» توسط کاربر) | **M0 + مجوز ویژه** | از Android 11، درخواست background باید از صفحه Settings باشد (نه dialog) ⇒ ویزارد ما دقیقاً همان‌جا هدایت می‌کند. Play: نیاز به **Location declaration form** و video demo |
| **Geofencing** | ✅ | `GeofencingClient.addGeofences` | `ACCESS_BACKGROUND_LOCATION` + FGS `location` type (برای قابلیت اطمینان) | **M0 + مجوز ویژه** | حداکثر **۱۰۰ geofence** per app · در Doze/App Standby triggerها با تأخیر (چند دقیقه) · `setResponsiveness` حداقل ~۳۰ ثانیه · دقت روی شبکه بدون GPS کمتر |
| **Location با دقت بالا و پیوسته** | ⚠️ | `ForegroundService(type="location")` + نوتیف دائمی | `FOREGROUND_SERVICE_LOCATION` (API 34+) + background location | M0 + مجوز ویژه | Android 15: محدودیت زمانی برای برخی FGS types؛ `location` معاف از timeout شش‌ساعته `dataSync` است، ولی باید `setUserInitiated`/نوع درست را انتخاب کنیم |
| **Safe Browsing / Domain filter** | ⚠️ محدود، هنوز دستگاهی تأیید نشده | Device/Profile Owner از DPM managed configurations برای Chrome `URLBlocklist`/`URLAllowlist` | Device/Profile Owner؛ بدون `VpnService` | فقط Chrome مدیریت‌شده | سایر مرورگرها، WebView و ترافیک کل سیستم خارج از scope؛ DPM readback اثربخشی Chrome را تأیید نمی‌کند. گزارش: `CONFIGURATION_APPLIED_UNVERIFIED`; حالت عادی = unsupported. رویداد بازدید/blocked-URL وجود ندارد |
| **فیلتر DNS / Private DNS** | ❌ در Phase 7 پیاده نشده | DPM می‌تواند در شرایط managed مقدار Private DNS host را تنظیم کند، اما GuardianLink resolver/category provider ندارد | Device Owner برای API مربوطه | خارج از scope نسخهٔ فعلی | هیچ DNS تغییر داده نمی‌شود و filtering ادعا نمی‌شود؛ تنظیم resolver به‌تنهایی طبقه‌بندی/مسدودسازی نیست |
| **Content filtering سطح صفحه / دسته‌بندی وب** | ❌ | در نسخهٔ جاری provider یا content inspector وجود ندارد؛ MITM/TLS inspection، CA injection و محتوای صفحه scope نیستند | هیچ | پیاده نشده | دسته‌بندی‌ها در API فقط ذخیرهٔ غیرقابل‌اجرا هستند (`categoryEnforceable=false`); SafeSearch/DNS classifier هم پیاده نشده |
| **اجبار SafeSearch در Google/YouTube** | ❌ در این نسخه پیاده نشده | هیچ DNS binding، Chrome SafeSearch policy یا enforcement در این پروژه اضافه نشده است | هیچ | خارج از scope فعلی | نباید اثر SafeSearch ادعا شود |
| **SOS با مکان فوری** | ✅ | job کوتاه foreground-initiated + `getCurrentLocation` one-shot + outbox retry (بدون FGS) + FCM high priority به والد | location + notification والد | M0 | FCM high-priority در Doze ممکن است تا چند دقیقه تأخیر کند ⇒ همچنین SSE + نوتیف محلی + (Phase 8) صدای هشدار |
| **نوتیف به والد** | ✅ | FCM + SSE + `NotificationManager` | `POST_NOTIFICATIONS` (API 33+) | M0 | اگر کاربر نوتیف را ببندد ⇒ detection و Alert «permission.revoked» |
| **استفاده از اپ در ساعات خاص (Schedule)** | ⚠️ | همان Daily Limit (M2/advisory) + `AlarmManager.setExactAndAllowWhileIdle` | `SCHEDULE_EXACT_ALARM` (31+) | **M2 / advisory** | exact alarm پیش‌فرض رد می‌شود ⇒ `setWindow`/WorkManager؛ exact فقط برای SOS/bedtime با مجوز |
| **خواندن نوتیف‌های فرزند** | ✅ ولی **توصیه نمی‌شود** | `NotificationListenerService` | `BIND_NOTIFICATION_LISTENER_SERVICE` | M0 + مجوز ویژه | **ما این را پیاده نمی‌کنیم** (حریم خصوصی شدید + Play policy). اگر خواستید، فقط «شمارش نوتیف per app» بدون محتوا |
| **خواندن پیامک/تماس** | ❌ | `READ_SMS`, `READ_CALL_LOG` | خطرناک | — | **هرگز پیاده نمی‌شود** (Play policy + قانون + اخلاق). جایگزین: فقط «تعداد تماس/پیامک» بدون محتوا اگر کاربر صریح بخواهد و Play اجازه دهد |
| **Keylogger / اسکرین‌شات مخفی** | ❌ | — | — | — | **هرگز.** هم غیرقانونی است، هم Play ban دائم، هم خلاف فلسفه «با رضایت و اطلاع فرزند» |

---

## ۳. محدودیت‌های سیستمی که روی همه Phaseها اثر دارند

### ۳.۱ کشتن سرویس پس‌زمینه توسط OEM (بزرگ‌ترین ریسک عملی)

| سازنده | رفتار | راه‌حل ما |
|---|---|---|
| **Xiaomi/MIUI-HyperOS** | کشتن تهاجمی، نیاز به «Autostart» | ویزارد OEM-specific: هدایت به `Settings → Apps → Autostart` + `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` + FGS با نوتیف دائمی |
| **Samsung** | «Sleeping apps» / «Deep sleeping apps» | هدایت به `Device care → Battery → Background usage limits` و حذف اپ از لیست |
| **Huawei/Honor** | مشابه MIUI | `Settings → Apps → App launch → Manage manually` |
| **OPPO/Realme/Vivo** | کشتن پس از چند دقیقه | همان الگو + `AlarmManager` wake + `BootReceiver` |
| **Android Stock/Pixel** | Doze + App Standby | FGS معاف از Doze؛ `setExactAndAllowWhileIdle` برای sync بعدی |

**معماری مقاوم:**
1. `SyncForegroundService` با نوتیف دائمی (شفاف به فرزند) — اولویت اصلی
2. `WorkManager` periodic (۱۵ دقیقه) به‌عنوان fallback اگر FGS کشته شد
3. `BootReceiver` + `ACTION_MY_PACKAGE_REPLACED` ⇒ شروع مجدد سرویس
4. `AlarmManager` زنجیره‌ای (self-rescheduling) برای heartbeat
5. **Outbox** در Room ⇒ هیچ داده‌ای با کشته‌شدن سرویس از دست نمی‌رود
6. تشخیص «سرویس کشته شد» ⇒ `telemetry_events(app_restart)` + Alert به والد `device.unstable`
7. صفحه «سلامت اتصال» در اپ فرزند با چراغ و راهنمای OEM

### ۳.۲ Android 14/15/16 — تغییرات موثر

| تغییر | اثر | مقابله |
|---|---|---|
| `FOREGROUND_SERVICE_TYPE_*` اجباری (API 34) | باید نوع درست اعلام شود | `dataSync` برای sync، `location` برای tracking، `specialUse` برای enforcement + `PROPERTY_SPECIAL_USE_FGS_SUBTYPE` |
| **محدودیت ۶ ساعته برای `dataSync`/`mediaProcessing` (Android 15)** | سرویس sync بعد از ۶ ساعت متوقف می‌شود | طراحی stateless: سرویس می‌تواند هر لحظه بمیرد و از Outbox ادامه دهد + `WorkManager` + (برای enforcement) نوع `specialUse` که معاف است |
| `SCHEDULE_EXACT_ALARM` به‌صورت پیش‌فرض رد (API 31+) | alarm دقیق برای bedtime | `USE_EXACT_ALARM` فقط اگر Play اجازه دهد؛ وگرنه `setWindow` ±۵ دقیقه |
| `POST_NOTIFICATIONS` runtime (API 33+) | نوتیف‌ها نمایش داده نمی‌شوند | ویزارد مجوز در اولین اجرا + detection |
| Predictive back (API 36) | ناوبری | استفاده از `BackHandler`/NavHost جدید، بدون `onBackPressed` |
| Edge-to-edge اجباری (API 35+) | layout | `enableEdgeToEdge()` + insets در همه صفحه‌ها |
| Health Connect / Privacy indicators | — | ما میکروفون/دوربین استفاده نمی‌کنیم (جز QR scan) ⇒ نشانگر سبز دوربین طبیعی است |
| Private Space (Android 15) | فرزند می‌تواند اپ را در Private Space پنهان کند | تشخیص محدود؛ در L2 با `DISALLOW_*` قابل کنترل |

### ۳.۳ Google Play Policy (برای انتشار)

| سیاست | وضعیت ما |
|---|---|
| **Accessibility API** فقط برای کمک به کاربران دارای معلولیت | ⚠️ استفاده ما «محدودسازی والد» است ⇒ **نیاز به پر کردن Sensitive Permission Declaration** و توضیح کاربرد. اپ‌های parental control متعدد (Qustodio, Bark, FamilyTime) همین مسیر را رفته‌اند و تأیید شده‌اند، ولی **ریسک رد شدن وجود دارد** |
| **VPN Service** | باید دلیل و Data Safety دقیق باشد؛ ما **هیچ ترافیکی را به سرور نمی‌فرستیم** ⇒ قوی‌ترین استدلال |
| **QUERY_ALL_PACKAGES** | declaration لازم؛ جایگزین: `<queries>` با intent خاص — ولی برای parental control به فهرست کامل نیاز داریم |
| **Location (background)** | Location declaration form + video + Privacy Policy + فقط وقتی قابلیت فعال باشد |
| **Data Safety** | فرم کامل بر اساس `data_consents` تولید می‌شود (سند ۰۶) |
| **Family Policy** | اگر اپ «برای کودکان» هدف‌گذاری شود، قواعد سخت‌گیرانه‌تر (تبلیغات، SDKها) ⇒ ما **Target audience = والد** اعلام می‌کنیم |
| **راه‌حل فازبندی** | Phase 1–8: **توزیع مستقیم APK** (بدون محدودیت Play) + تست روی گوشی واقعی. Phase 9: پرونده Play با declaration forms |

---

## ۴. «جعل» کنترل — موضع صریح ما

شما نوشتید: *«اگر Android اجازه Block واقعی یک اپ را با API معمولی نمی‌دهد، آن را جعل کن.»*

**ما این کار را به شکل فریبنده انجام نمی‌دهیم** (چون محصول باید روی گوشی واقعی کار کند و اعتماد والد/فرزند سرمایه اصلی است). در عوض:

| به‌جای جعل | چه می‌سازیم |
|---|---|
| «اپ مسدود شد» وقتی واقعاً مسدود نشده | **Blocking واقعی فقط با M2** (`setPackagesSuspended` ⇒ صفحه سیستمی «app disabled by admin») + در M0 برچسب صادقانه `ADVISORY` (فقط گزارش به والد). هیچ برچسب «مسدود شد» روی چیزی که مسدود نیست نمایش داده نمی‌شود |
| Screen time ساختگی | داده واقعی از `UsageStatsManager` + `source` field (`usage_stats`/`screen_events`/`mixed`) و badge دقت |
| موقعیت جعلی | مکان واقعی + `accuracy_m` + `is_stale` + «آخرین به‌روزرسانی: ۳ دقیقه پیش» |
| وضعیت Online جعلی | lease-based با `presence_reason` دقیق |

**«Demo Mode»** تنها جایی است که داده ساختگی تولید می‌شود: در Dev Menu با برچسب بزرگ `DEMO` روی صفحه ⇒ برای ضبط ویدیو/اسکرین‌شات بدون گوشی دوم. در build release غیرفعال است.

---

## ۵. مسیر Device Owner (M2) — توصیه‌شده برای حفاظت واقعی

### چرا؟
تنها راه **واقعی** برای: block کردن اپ، جلوگیری از حذف، محدودیت‌های سیستمی (`DISALLOW_INSTALL_APPS`, `DISALLOW_USB`), زمان‌بندی سطح OS.

### سه روش provisioning

| روش | شرایط | تجربه کاربری |
|---|---|---|
| **A. QR / NFC در setup wizard** | گوشی **نو یا factory reset** شده | بهترین: در اولین راه‌اندازی، QR والد را اسکن می‌کند ⇒ Zero-touch enrollment (نیاز به سرور provisioning دارد) |
| **B. ADB (یک‌بار)** | گوشی روشن + USB debugging + **بدون حساب فعلی** | `adb shell dpm set-device-owner com.guardianlink.child/.admin.GuardianDeviceAdminReceiver` — ما یک ابزار کوچک + راهنمای تصویری در Dev Menu ارائه می‌کنیم |
| **C. EMM/Android Management API** | ناوگان بزرگ | برای مقیاس سازمانی؛ در Phase 9 به‌عنوان integration جداگانه |

### معماری Enforcement (از Phase 1؛ فقط روش‌های رسمی)
```
service/enforcement/
├── GuardianDeviceAdminReceiver.kt      # DeviceAdminReceiver (از Phase 1: فقط تشخیص حذف)
├── CapabilityDetector.kt               # isDeviceOwnerApp(), isProfileOwnerApp(), appops, accessibility
├── EnforcementStrategy.kt              # interface
│   ├── AdvisoryEnforcer.kt             # M0/M1: فقط گزارش/هشدار به والد (بدون اجرای فیزیکی)
│   └── OwnerEnforcer.kt                # M2: setPackagesSuspended, setHidden, addUserRestriction, LockTask
│   # ❌ AssistedEnforcer (Accessibility) در v2 حذف شد — Accessibility Abuse در خط قرمزها
└── EnforcementBinder.kt                # انتخاب strategy بر اساس CapabilityProfile (Strategy Pattern)
```
⇒ وقتی M2 فعال شود، **هیچ کد UI/Domain تغییری نمی‌کند**؛ فقط `EnforcementStrategy` عوض می‌شود.

---

## ۶. جدول خلاصه «چه چیزی در چه Phaseای واقعاً کار می‌کند»

| Phase | قابلیت | سطح لازم | در تست واقعی چه می‌بینید |
|---|---|---|---|
| 1 | Auth, Pairing, Dashboard, Anti-Tamper core, Remove/Revoke | M0 (+M1 توصیه‌شده) | ✅ pairing واقعی · dashboard واقعی · تشخیص tamper کار می‌کند |
| 2 | Status + Battery + Last Sync + permission watcher | M0 | ✅ اعداد واقعی با برچسب زمان |
| 3 | Screen Time (daily/weekly/per-app) | M0 + `Usage Access` | ✅ دقیق؛ بدون مجوز ⇒ تخمین با badge |
| 3 | اجرای Daily Limit / Bedtime | **M2** / M0 advisory | M2: suspend واقعی سیستمی · M0: گزارش به والد |
| 4 | Location current/history | M0 + background location | ✅ دقت و freshness واقعی، بدون FGS |
| 5 | Geofencing | M0 + background location | ✅ trigger واقعی (با تأخیر رسمی Doze) |
| 6 | App Management (inventory/usage/block) | **M2** برای block | M2: block واقعی · M0: inventory + usage + advisory |
| 7 | Web Safety | M0 (Private DNS) · VPN با افشا | فیلتر دامنه بدون سرور؛ VPN = نوتیف سیستمی (تصمیم شما) |
| 8 | Notifications والد (toggles) | M0 | ✅ هر ۸ نوع با Toggle جدا |
| 9 | SOS | M0 | ✅ ≤ ۱۵ ثانیه تا نوتیف والد |
| 10 | Hardening + QR DO wizard + AMA + Play | M2/M3 | ✅ حفاظت کامل رسمی |
