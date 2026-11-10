# گزارش Phase 3 — Screen Time و App Usage (فقط Monitoring)

تاریخ: ۲۰۲۶-۰۹-۲۹ · وضعیت: آماده‌ی تست روی دستگاه واقعی · فاز بعدی فقط پس از تأیید شما

---

## ۱) خلاصه

در این فاز شش قابلیت خواسته‌شده پیاده‌سازی شد: **Screen Time، App Usage، Daily Usage، Weekly Reports، Usage Limits، Schedules**. هیچ قابلیت دیگری (مکان/Geofence/فیلتر وب/SOS) اضافه نشد.

اصل کلیدی: **این فاز فقط «پایش و اطلاع‌رسانی» است، نه «مسدودسازی».** سقف مصرف و برنامه‌های زمانی روی سرور محاسبه و به والد **هشدار** می‌دهند؛ گوشی فرزند هرگز قفل نمی‌شود و هیچ اپی مسدود نمی‌شود. دلیل فنی و مسیر رسمی رسیدن به مسدودسازی واقعی در بند ۱۰ آمده است. در UI هم هیچ جمله/المانی وانمود نمی‌کند که مسدودسازی رخ می‌دهد (دو یادداشت صریح در صفحات والد درج شده است).

---

## ۲) فایل‌های جدید / تغییر یافته

### سرور (`server/src/main/kotlin/ir/guardianlink/server/`)
| فایل | نقش |
|---|---|
| `db/migration/V3__phase3.sql` | ۵ جدول جدید (بند ۴) |
| `UsageModels.kt` | همه‌ی DTOهای ورودی/خروجی Phase 3 |
| `UsageRepos.kt` | `UsageRepo`: UPSERT مصرف/آیکون، limit، schedule، state |
| `UsageService.kt` | validation سخت‌گیرانه + ذخیره + ماشین‌حالت limit/schedule و تولید رویداد |
| `UsageRoutes.kt` | endpointهای Child و Parent (بند ۵) |
| `Monitoring.kt` | ۵ EventType جدید + انتشار `USAGE_ACCESS_REVOKED` + پذیرش وضعیت `not_granted` در PayloadGuard (باگ واقعی رفع شد) |
| `Models.kt` | فیلد `usage` به `SyncReq` (بسته‌ی مصرف داخل همان درخواست sync) |
| `Auth.kt` | سیم‌کشی `usageService` در `AppDeps` |
| `DeviceRoutes.kt` | handler سینک: اگر `usage` داشت ⇒ `usageService.upload` |
| `Main.kt` | ثبت `usageRoutes` |
| `src/test/.../Phase3Test.kt` | ۲۳ تست یکپارچه (بند ۱۱) |

### اندروید (`android/`)
| فایل/ماژول | نقش |
|---|---|
| `core:common/TimeFmt.kt` + تست | قالب «2:34» / «2h34m» / درصد امن |
| `core:network/UsageModels.kt` | DTOهای Phase 3 سمت کلاینت |
| `core:network/ApiClient.kt` | ۱۲ endpoint جدید + `iconBytes` (دریافت PNG آیکون) |
| `core:network/ApiModels.kt` | فیلد `usage` در `SyncReq` |
| **`feature:usage` (ماژول جدید)** | `UsageCollector` (کودک)، `UsageIcons`، `Charts` (نمودار میله‌ای هفتگی/نوار درصد)، `UsageViewModels`، صفحات: `ScreenTimeScreen` + `ScreenTimeCard` (داشبورد)، `AppUsageScreen`، `AppDetailScreen`، `ReportsScreen`، `ScreenTimeSettingsScreen`، رشته‌ها fa/en |
| `feature:childstatus/StatusSyncWorker.kt` | تجمیع مصرف امروز+دیروز و ارسال در همان sync؛ علامت‌گذاری آیکون‌ها فقط پس از sync موفق |
| `feature:childstatus/ChildHome.kt` | کارت «دسترسی مصرف»: 🟢 فعال / 🔴 غیرفعال + دکمه‌ی تنظیمات رسمی؛ با بازگشت از تنظیمات خودکار تازه می‌شود |
| `feature:dashboard/ParentNotifier.kt` | کانال `Screen Time` + ۵ نوع هشدار جدید |
| `feature:dashboard/DashboardViewModel.kt` | دریافت `screen-time/report` هر دستگاه |
| `feature:dashboard/DashboardScreen.kt` | کارت Screen Time زیر هر دستگاه + برچسب رویدادهای جدید |
| `app-parent/RootNav.kt` | ۵ مقصد ناوبری جدید |
| `app-child/AndroidManifest.xml` | `PACKAGE_USAGE_STATS` (دسترسی خاص؛ با توضیح) |
| `core:common/BatteryBandTest.kt` | اصلاح یک تست که با سمانتیک سرور ناسازگار بود (level == criticalPct ⇒ low) |

---

## ۳) معماری و جریان داده

```
کودک (هر sync دوره‌ای WorkManager، همان فاصله‌ی Phase 2):
  UsageStatsManager ──► تجمیع محلی امروز+دیروز (حداکثر ۶۰ اپ در روز)
       + PackageManager (نام/دسته/آیکون فقط برای پکیج جدید یا نسخه‌ی تغییرکرده)
  ──► همان درخواست POST /v1/device/sync با فیلد usage  (بدون درخواست شبکه‌ی اضافی)

سرور:
  validation سخت‌گیرانه ──► UPSERT در app_usage_daily (idempotent)
  ──► evaluateLimit: ماشین‌حالت under → near → reached (فقط روی گذار، رویداد + dedupe)
  ──► evaluateSchedules: پنجره‌های فعال (زمان سرور، شبانه پشتیبانی می‌شود) ⇒ diff کلیدها ⇒ STARTED/ENDED

والد (poll پیش‌زمینه‌ی داشبورد):
  GET screen-time/report ──► کارت داشبورد، صفحات مصرف/جزئیات/گزارش/تنظیمات
  رویدادهای جدید ──► ParentNotifier (فقط اپ والد؛ کانال Screen Time)
```

نکته‌ی امنیتی: **هویت دستگاه فقط از توکن session استخراج می‌شود**؛ هیچ endpoint والد `deviceId` را از بدنه‌ی درخواست نمی‌پذیرد و دسترسی خانواده‌ی بیگانه ⇒ 404 (بدون نشت اطلاعات).

---

## ۴) پایگاه داده (V3)

| جدول | محتوا |
|---|---|
| `app_usage_daily` | هر (device, package, date) یک ردیف — `UNIQUE` + UPSERT ⇒ sync تکراری داده‌ی تکراری نمی‌سازد. `first_used_at` = کمینه، `last_used_at` = بیشینه |
| `app_icons` | PNG آیکون (bytea) هر (device, package)؛ فقط اگر `version_code` جدیدتر باشد جایگزین می‌شود |
| `screen_time_limits` | `device_id UNIQUE`، `daily_limit_minutes` (پیش‌فرض ۲۴۰)، `near_pct` (پیش‌فرض ۸۰)، `enabled` |
| `screen_time_schedules` | نام، `start_time`/`end_time` به‌صورت HH:MM، `days_of_week` bitmask (bit0=دوشنبه)، بازه‌ی شبانه مجاز، `start==end` رد می‌شود |
| `screen_time_state` | وضعیت روزانه‌ی `limit_state` و `schedule_keys` برای **dedupe رویدادها** (فقط گذار ⇒ رویداد) |

---

## ۵) APIها

### Child (توکن دستگاه)
| متد | مسیر | توضیح |
|---|---|---|
| POST | `/v1/device/usage/upload` | ارسال مستقیم بسته‌ی مصرف (rate-limited) |
| POST | `/v1/device/sync` | فیلد اختیاری `usage` — مسیر اصلی (batch) |

قوانین پذیرش: حداکثر ۸ روز (امروز تا ۷ روز قبل) · حداکثر ۸۰ اپ در روز · `usage_minutes` بین ۰ تا ۱۴۴۰ · نام پکیج با regex · دسته فقط از فهرست واقعی سیستم · آیکون حداکثر ~۱۲۰KB و base64 معتبر · همه‌چیز all-or-nothing.

### Parent (توکن کاربر + بررسی مالکیت خانواده) — زیر `/v1/families/me/devices/{id}/`
`usage/today` · `usage/weekly` · `usage/apps?date=` · `usage/apps/{pkg}/detail` · `usage/apps/{pkg}/icon` (PNG) · `usage/report/daily?date=` · `screen-time/report` · `screen-time/settings` (GET/POST) · `screen-time/schedules` (GET/POST) · `screen-time/schedules/{sid}` (PUT)

### رویدادهای جدید (device_events)
`SCREEN_TIME_LIMIT_NEAR` · `SCREEN_TIME_LIMIT_REACHED` · `USAGE_ACCESS_REVOKED` · `SCHEDULE_STARTED` · `SCHEDULE_ENDED`
همه **فقط روی گذار** تولید می‌شوند (نه در هر sync) و با `dedupe_key` روزانه محافظت می‌شوند.

---

## ۶) APIهای اندروید استفاده‌شده (همه رسمی)

| API | کاربرد |
|---|---|
| `UsageStatsManager.queryAndAggregateUsageStats` | مجموع زمان مصرف هر پکیج در بازه‌ی روز |
| `UsageStatsManager.queryEvents` (ACTIVITY_RESUMED / MOVE_TO_FOREGROUND) | شمارش تعداد باز شدن اپ |
| `UsageStats.firstTimeStamp` (API 29+) | اولین استفاده (در API 26-28 null می‌ماند — داده‌ی ساختگی نمی‌فرستیم) |
| `PackageManager.getApplicationLabel` | نام اپ |
| `ApplicationInfo.category` (API 26+) | دسته‌بندی **فقط اگر سیستم مقدار واقعی بدهد**؛ در غیر این صورت `undefined` — هرگز دسته‌ی ساختگی نه |
| `PackageManager.getApplicationIcon` | آیکون ۹۶px PNG، فقط برای پکیج جدید/نسخه‌ی تغییرکرده |
| `AppOpsManager.OPSTR_GET_USAGE_STATS` | منبع حقیقت «دسترسی مصرف فعال است؟» |
| `Settings.ACTION_USAGE_ACCESS_SETTINGS` | صفحه‌ی رسمی فعال‌سازی دسترسی (تنها مسیر؛ بدون هیچ ترفند) |
| `PACKAGE_USAGE_STATS` در manifest | اعلان دسترسی خاص |

---

## ۷) جریان مجوز و تشخیص لغو

1. کارت «دسترسی مصرف» در خانه‌ی کودک وضعیت را 🟢/🔴 نشان می‌دهد.
2. دکمه‌ی «باز کردن تنظیمات رسمی» ⇒ صفحه‌ی رسمی Usage Access اندروید.
3. هنگام بازگشت (ON_RESUME) وضعیت دوباره خوانده می‌شود.
4. هر sync وضعیت `usage_access` را به سرور می‌فرستد؛ داشبورد والد آن را در بلوک مجوزها نشان می‌دهد.
5. **لغو دسترسی:** گذار `granted → not_granted` روی سرور دو رویداد می‌سازد: `PERMISSION_CHANGED` و `USAGE_ACCESS_REVOKED` ⇒ اعلان در اپ والد + کارت Screen Time عبارت «🔴 دسترسی مصرف: غیرفعال» را نشان می‌دهد. اعاده‌ی دسترسی رویداد لغوِ تکراری نمی‌سازد.

---

## ۸) بهینه‌سازی باتری و شبکه

- **تجمیع محلی:** فقط امروز+دیروز، حداکثر ۶۰ اپ پرمصرف در روز — نه تاریخچه‌ی کامل.
- **بدون درخواست اضافی:** بسته‌ی مصرف سوار همان `sync` دوره‌ای WorkManager می‌شود (همان interval سرور-محور Phase 2).
- **آیکون‌ها یک‌بار:** فقط پکیج جدید یا نسخه‌ی تغییرکرده؛ با `version_code` سمت سرور هم محافظت دوم دارد.
- **idempotent:** UPSERT ⇒ retryهای WorkManager هیچ داده/رویداد تکراری نمی‌سازند.
- بدون Foreground Service، بدون wakeup جدید، بدون notification سمت کودک (طبق سیاست پروژه).

---

## ۹) خطوط قرمز حریم خصوصی در این فاز

هرگز جمع‌آوری/ارسال نمی‌شود: محتوای داخل اپ‌ها، پیامک/واتس‌اپ/تلگرام، عکس‌ها و فایل‌ها، میکروفون، دوربین، ورودی کیبورد، رمزها، ضبط صفحه، کلیپ‌بورد. داده‌ی هر اپ دقیقاً شش قلم است: **نام پکیج، نام اپ، مجموع زمان، اولین/آخرین استفاده، تعداد باز شدن** (+ دسته و آیکون فقط از منابع رسمی سیستم).

---

## ۱۰) Monitoring در برابر Blocking — گزارش دقیق API

**الان پیاده‌شده (Monitoring):** `UsageStatsManager` با دسترسی خاص `PACKAGE_USAGE_STATS`. این API فقط «آمار» می‌دهد و هیچ متد مسدودسازی ندارد. روی گوشی‌های معمولی (بدون Device Owner) کار می‌کند.

**برای مسدودسازی/اعمال محدودیت واقعی، دقیقاً یکی از این‌ها لازم است:**

| روش | API | وضعیت |
|---|---|---|
| **Device Owner (مسیر پیشنهادی پروژه — Phase 6)** | `DevicePolicyManager.setPackagesSuspended()`، `setPermissionGrantState()`، Lock Task Mode. با `adb shell dpm set-device-owner` (روی دستگاه تازه-factory-reset) یا provisioning رسمی Android Enterprise (QR/zero-touch) فعال می‌شود | ✅ رسمی، بدون root، بدون نقض سیاست Play؛ نیازمند پذیرش صریح والد/فرزند در جریان provisioning |
| Managed Google Play / EMM | پالیس‌های `managedConfigurations` و app allowlist از کنسول مدیریت | رسمی ولی نیازمند زیرساخت EMM — خارج از مقصود خانواده |
| Google Family Link | — | API عمومی برای اپ‌های شخص ثالث ندارد |
| Digital Wellbeing | — | **عمومی نیست**؛ فقط اپ‌های سیستمی/OEM |
| Accessibility Service | — | ❌ خط قرمز پروژه + نقض صریح سیاست Play (Accessibility Abuse) — استفاده نشد و نخواهد شد |
| Always-on VPN | — | فقط فیلتر ترافیک وب (مرتبط با Web Filtering نه app blocking)؛ در این فاز خارج از محدوده |

**صداقت در UI:** یادداشت‌های صریح «این برنامه‌ها فقط اطلاع‌رسانی دارند؛ در این نسخه هیچ اپی مسدود نمی‌شود» در صفحه‌ی Screen Time و تنظیمات درج شده است. هیچ المان «قفل/مسدود شد» وجود ندارد.

---

## ۱۱) تست‌ها

### سرور — `Phase3Test.kt` (۲۳ تست یکپارچه با Testcontainers/Postgres)
- **Usage:** بدون داده · تک‌اپ (درصد ۱۰۰) · چند اپ (مرتب‌سازی + درصد) · مقادیر بزرگ (۱۴۴۰ مجاز/۱۴۴۱ رد · ۸۰ اپ مجاز/۸۱ رد · ۸ روز مجاز/۹ رد) · نام پکیج نامعتبر · تاریخ آینده/قدیم/بدفرمت · **آپلود تکراری ⇒ overwrite نه append** · مصرف سوار بر sync · گزارش هفتگی (۷ ردیف/میانگین/بیش‌ترین/کم‌ترین روز) · جزئیات اپ (امروز/هفته/launch/آخرین استفاده) · گزارش روزانه (کل/پرمصرف‌ترین/تعداد اپ/سشن/**دسته‌های واقعی**) · دسته‌ی نامعتبر رد می‌شود · آیکون ذخیره و PNG سرو می‌شود/۴۰۴
- **Daily Limit:** غیرفعال ⇒ بدون رویداد حتی فراتر از سقف · فعال ⇒ under→near→reached با **دقیقاً یک** رویداد در هر گذار (sync تکراری ⇒ رویداد تکراری ندارد) · remaining درست · validation تنظیمات (۵..۱۴۴۰ و ۵۰..۱۰۰)
- **Schedules:** پنجره‌ی فعال ⇒ `SCHEDULE_STARTED` یک‌بار (تریگر مجدد ⇒ تکرار ندارد) · غیرفعال کردن ⇒ `SCHEDULE_ENDED` · روز دیگر هفته ⇒ غیرفعال · چندروزه با bit امروز ⇒ فعال · **شبانه (۲۲:۳۰→۰۷:۰۰ و wrap تمام‌روز) پذیرفته و فعال** · بازه‌های نامعتبر رد (start==end، ۲۵:۰۰، aa:bb، days=0/128، نام خالی)
- **Permission:** granted→revoked ⇒ `PERMISSION_CHANGED` + دقیقاً یک `USAGE_ACCESS_REVOKED` · denied تکراری ⇒ رویداد جدید ندارد · re-grant ⇒ لغوِ تکراری ندارد
- **Security:** بدون توکن/توکن خراب ⇒ ۴۰۱ · توکن والد روی endpoint کودک و برعکس ⇒ ۴۰۱ · خانواده‌ی بیگانه ⇒ ۴۰۴ روی همه‌ی endpointها · توکن منقضی ⇒ ۴۰۱ · دستگاه حذف‌شده ⇒ Gone/401

اجرا: `cd server && gradle test` (نیازمند Docker) · فقط unit: `gradle test -PskipIT` ✅ سبز در سندباکس.

### اندروید
`:core:common:testDebugUnitTest` ✅ **۹/۹ سبز** (TimeFmt ۳ + BatteryBand ۲ + Validators ۴). تست اجرای کامل اندروید طبق روال قبل در محیط شما (رم محدود سندباکس).

---

## ۱۲) ساخت

```bash
# سرور
cd server && gradle build -x test && gradle run        # Flyway مهاجرت V3 را خودکار اعمال می‌کند

# اندروید (هر دو APK)
cd android && gradle :app-child:assembleDebug :app-parent:assembleDebug \
  -PguardianBaseUrl=http://<LAN_IP>:8080
```

---

## ۱۳) راهنمای تست روی دستگاه واقعی (۱۵ گام)

1. Postgres را بالا بیاورید و سرور را اجرا کنید؛ در لاگ ببینید مهاجرت `V3__phase3` اعمال شده.
2. سرور را روی آدرس LAN در دسترس هر دو گوشی بگذارید (`app.baseUrl` / `http://<LAN_IP>:8080`).
3. دو APK را با `guardianBaseUrl` درست بسازید و نصب کنید (Parent روی گوشی خودتان، Child روی گوشی فرزند/تست).
4. در Parent ثبت‌نام کنید و خانواده بسازید.
5. در Child جریان pairing را طی کنید (کد ۸ رقمی از Parent).
6. صفحه‌ی خانه‌ی کودک را ببینید: کارت «دسترسی مصرف (Usage Access)» با وضعیت 🔴 غیرفعال.
7. روی «باز کردن تنظیمات رسمی» بزنید ⇒ صفحه‌ی رسمی Usage Access اندروید باز می‌شود.
8. GuardianLink Child را پیدا و فعال کنید (هشدار سیستمی اندروید را تأیید کنید) و به اپ برگردید.
9. کارت باید **🟢 فعال** شود (بدون نیاز به restart اپ — با بازگشت تازه می‌شود).
10. «همگام‌سازی اکنون» را بزنید؛ در داشبورد Parent ⇒ بلوک مجوزها: «دسترسی آمار مصرف: مجاز».
11. چند دقیقه با چند اپ مختلف کار کنید و دوباره sync بزنید.
12. کارت **⏳ زمان صفحه** در داشبورد Parent: مصرف امروز، نوار پیشرفت، ۳ اپ پرمصرف. (اگر سقف تعریف نشده: فقط مصرف امروز)
13. «مصرف اپ‌ها» ⇒ فهرست با آیکون/نام/زمان/درصد + سه حالت مرتب‌سازی؛ روی یک اپ بزنید ⇒ جزئیات با نمودار هفتگی، تعداد باز شدن، آخرین استفاده.
14. «گزارش‌ها» ⇒ گزارش روزانه (کل، پرمصرف‌ترین، تعداد اپ، سشن‌ها، دسته‌ها **فقط اگر واقعی باشند**) + گزارش هفتگی با نمودار.
15. آزمون هشدارها: در «تنظیمات زمان صفحه» سقف را مثلاً ۳۰ دقیقه و آستانه‌ی «نزدیک» را ۸۰٪ بگذارید؛ یک برنامه‌ی زمانی (مثلاً Bedtime ۲۲:۳۰→۰۷:۰۰) بسازید. در گوشی کودک آنقدر اپ باز کنید تا از ۸۰٪ و سپس از سقف عبور کند ⇒ در Parent (با باز بودن داشبورد) اعلان‌های «نزدیک سقف» و «سقف پر شد». سپس در گوشی کودک Usage Access را غیرفعال کنید ⇒ اعلان «دسترسی مصرف غیرفعال شد» + عبارت 🔴 در کارت. **توجه:** در تمام این مدت هیچ اپی روی گوشی کودک مسدود/قفل نمی‌شود — این دقیقاً رفتار انتظارِ این فاز است.

---

## ۱۴) چه‌چیزی الان کار می‌کند / چه‌چیزی نه

### ✅ الان کار می‌کند (دستگاه معمولی، بدون Device Owner)
- خواندن آمار مصرف همه‌ی اپ‌ها (زمان، اولین/آخرین استفاده، تعداد باز شدن) با Usage Access
- داشبورد Screen Time والد: امروز/سقف/باقی‌مانده/نوار پیشرفت/۳ اپ برتر
- فهرست مصرف اپ‌ها با آیکون و درصد و سه مرتب‌سازی · جزئیات اپ با نمودار هفتگی
- گزارش روزانه و هفتگی (با دسته‌بندی فقط-واقعی)
- سقف روزانه + آستانه‌ی «نزدیک» ⇒ هشدار به والد (فقط گذار)
- برنامه‌های زمانی شامل شبانه ⇒ هشدار شروع/پایان به والد
- تشخیص و هشدار لغو دسترسی مصرف
- sync بهینه‌ی باتری (بدون درخواست اضافه، بدون سرویس پیش‌زمینه)

### ⚠️ نیازمند Device Owner / Managed Device (مسیر Phase 6)
- **مسدودسازی واقعی اپ‌ها** (`setPackagesSuspended`)
- **اعمال سخت برنامه‌ی زمانی** (قفل شدن دستگاه در بازه‌ی Bedtime/School — Lock Task)
- جلوگیری از uninstall اپ کودک توسط خود فرزند
- تا آن زمان، Limits/Schedules فقط advisory هستند و UI هم صادقانه همین را می‌گوید

### ❌ هرگز (خط قرمز)
- استفاده از Accessibility برای مسدودسازی · هر روش غیررسمی/bypass · نمایش «مسدود شد» جعلی در UI · ساخت دسته‌بندی جعلی

---

## ۱۵) محدودیت‌های شناخته‌شده‌ی این فاز

- `firstTimeStamp` فقط در API 29+ موجود است ⇒ در اندروید ۸/۸.۱ «اولین استفاده» ممکن است خالی بماند (داده‌ی جعلی نمی‌گذاریم).
- رویدادهای schedule/limit هنگام poll والد (باز بودن داشبورد) به اعلان تبدیل می‌شوند؛ push پس‌زمینه (FCM) مطابق نقشه در Phase 8.
- محاسبه‌ی پنجره‌های زمانی با ساعت **سرور** انجام می‌شود؛ اگر منطقه‌ی زمانی سرور با دستگاه کودک متفاوت باشد باید در استقرار تنظیم شود (در production: `TZ=Asia/Tehran` روی سرور).
- آیکون اپ‌ها فقط وقتی ارسال می‌شود که کودک حداقل یک sync موفق با دسترسی فعال داشته باشد؛ قبل از آن fallback «📦» نمایش داده می‌شود.

---

**توقف طبق قرارداد BUILD → TEST → FIX → APPROVE → NEXT FEATURE.**
منتظر نتیجه‌ی تست روی دستگاه واقعی و فرمان شما هستم: `APPROVED` / `FIX` / `NEXT PHASE`.
