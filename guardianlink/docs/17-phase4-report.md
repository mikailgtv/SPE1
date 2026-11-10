# گزارش Phase 4 — Location Sharing + Location History + Location Health

تاریخ: ۲۰۲۶-۰۹-۲۹ · وضعیت: آماده‌ی تست روی دو دستگاه واقعی · توقف تا فرمان `APPROVED` / `NEXT PHASE`

محدوده‌ی انجام‌شده: Current Location · Last Known Location · Location Sharing Status · Location Permission Status · Location History · Location Sync · Location Health.
محدوده‌ی انجام‌نشده (طبق دستور): ❌ Geofence ❌ Web Filtering ❌ App Blocking ❌ SOS ❌ Message Monitoring ❌ Camera ❌ Microphone ❌ Screen Recording.

---

## ۱) فایل‌های ساخته/تغییر یافته

### سرور
| فایل | تغییر |
|---|---|
| `db/migration/V4__phase4.sql` | **جدید** — ۳ جدول: `locations` (UNIQUE(device_id, client_ts) ⇒ ضد duplicate)، `location_settings`، `location_status` |
| `LocationModels.kt` | **جدید** — همه‌ی DTOها (LocationPointIn/LocationSyncIn/LocationFixOut/LocationCurrentOut/LocationHistoryOut/LocationSettingsOut+Req/ردیف‌های DB) |
| `LocationRepos.kt` | **جدید** — درج ON CONFLICT DO NOTHING با شمارش واقعی، history با from/to، purge/keepOnlyLatest/deleteRange، settings/status UPSERT |
| `LocationService.kt` | **جدید** — validation، محرمانگی (sharing off ⇒ مختصات ذخیره نمی‌شود)، health تنبل، retention تنبل، رویدادها فقط روی گذار |
| `LocationRoutes.kt` | **جدید** — endpointهای بند ۴ |
| `Monitoring.kt` | ۷ EventType جدید مکانی |
| `Models.kt` | `SyncReq.location` + `SyncOut.locationSharing` |
| `DeviceRoutes.kt` | sync ⇒ `locationService.sync(...)` + پرچم sharing در پاسخ |
| `Config.kt` + `application.conf` + `deploy/.env.example` | `LOCATION_STALE_SEC` (پیش‌فرض ۳۶۰۰) |
| `Auth.kt` / `Main.kt` | سیم‌کشی `locationService` و `locationRoutes` |
| `Phase4Test.kt` | **جدید** — ۱۹ تست یکپارچه (بند ۱۱) |
| `IntegrationBase.kt` | کلید config جدید برای تست |

### اندروید
| فایل/ماژول | تغییر |
|---|---|
| **`feature:location` (ماژول جدید)** | کودک: `LocationPermission` (وضعیت مجوز/سرویس، واژگان مشترک با سرور)، `LocationCollector` (Fused fix تک‌شات)، `LocationQueue` (صف آفلاین JSON، سقف ۲۴۰، retry-safe). والد: `MapProvider` (انتزاع) + `OsmTileMapProvider` (کاشی OSM بدون کلید)، `LocationViewModels`، `LocationCard` (کارت داشبورد)، `MapScreen`، `LocationHistoryScreen`، `LocationSettingsScreen`، رشته‌ها fa/en |
| `core:network` | `LocationModels.kt` جدید؛ `ApiClient`: ۷ متد جدید (locationSync/current/history/settings×۲/delete)؛ `SyncReq.location`؛ `SyncOut.locationSharing` |
| `feature:childstatus` | `StatusSyncWorker`: fix تک‌شات + صف + embed در sync + `markSent` فقط پس از موفقیت + ذخیره‌ی `loc_sharing` + **گیت سمت کودک:** sharing خاموش توسط والد ⇒ نه capture، نه ارسال، صف محلی هم پاک می‌شود (بند ۱۶)؛ `ChildHome`: کارت موقعیت با جریان رسمی مجوز (دومرحله‌ای برای background) + ردیف‌های مجوز/سرویس/**آخرین همگام‌سازی** (بند ۱۷)؛ `DeviceInfo`: مقدار واقعی `location` در snapshot مجوزها |
| `feature:dashboard` | `DashboardViewModel`: `locations` هر دستگاه؛ `DashboardScreen`: کارت 📍 + ۳ callback ناوبری + ۷ برچسب رویداد؛ `ParentNotifier`: کانال `Location` + ۵ نوع اعلان؛ رشته‌ها fa/en |
| `app-parent` | RootNav: `locmap/{id}`، `lochistory/{id}?filter=`، `locsettings/{id}`؛ وابستگی `feature:location` |
| `app-child` | manifest: `ACCESS_COARSE_LOCATION` + `ACCESS_FINE_LOCATION` + `ACCESS_BACKGROUND_LOCATION` (با توضیح ضرورت) |
| `gradle/libs.versions.toml` / `settings.gradle.kts` | `play-services-location 21.3.0`؛ include ماژول جدید |

---

## ۲) معماری (تغییرات)

```
کودک (هر چرخه‌ی sync — همان WorkManager دوره‌ای، بدون سرویس جدید):
  والد sharing را خاموش کرده؟ ⇒ هیچ fix گرفته نمی‌شود، صف محلی پاک می‌شود،
     فقط وضعیت مجوز/سرویس گزارش می‌شود (بند ۱۶ — گیت سمت مشتری + گیت سمت سرور)
  Fused single-fix (BALANCED_POWER_ACCURACY، timeout ۱۲ ثانیه)
     └─ شکست؟ ⇒ lastLocation با source=last_known و محافظ کهنگی ۴۸ ساعت
  → LocationQueue محلی (آفلاین‌-ready، سقف ۲۴۰ ≈ ۲۴ ساعت، dedupe با clientTs)
  → embed در همان POST /v1/device/sync (فیلد location) ⇒ صفر درخواست شبکه‌ی اضافه
  → پس از پاسخ موفق: صف پاک می‌شود (markSent) — retry هیچ duplicate نمی‌سازد

سرور:
  validation (بازه‌ی lat/lon، دقت، پنجره‌ی ۴۸ ساعت، skew ساعت ۵ دقیقه، whitelist وضعیت‌ها)
  → sharing_enabled=false ⇒ مختصات دور ریخته می‌شود (stored=0) ولی «وضعیت» ثبت می‌شود
  → INSERT ON CONFLICT DO NOTHING ⇒ duplicate غیرممکن
  → location_status: permission/provider/last_fix/health + رویدادها فقط روی گذار
  → retention purge تنبل در هر sync/خواندن

والد:
  کارت 📍 در داشبورد → نقشه (MapProvider انتزاعی) / تاریخچه (فیلتر امروز/دیروز) / تنظیمات (sharing+retention+حذف)
  رویدادها → ParentNotifier کانال Location (poll پیش‌زمینه؛ FCM در Phase 8)
```

---

## ۳) پایگاه داده (V4)

- `locations(id, device_id FK CASCADE, latitude, longitude, accuracy_m, client_ts, server_ts, source, UNIQUE(device_id, client_ts))` + ایندکس `(device_id, client_ts DESC)`
- `location_settings(device_id PK, sharing_enabled DEFAULT true, retention_days DEFAULT 30)` — `0` = فقط آخرین موقعیت
- `location_status(device_id PK, permission_status, provider_status, last_fix_ts, last_report_ts, health_state, updated_at)`
- **مختصات فقط وقتی `sharing_enabled=true` ذخیره می‌شود** (الزام بند ۱۳) — هم در سرویس و هم با تست.

## ۴) APIها

| متد | مسیر | auth |
|---|---|---|
| POST | `/v1/device/location/sync` | توکن دستگاه (rate-limited) |
| POST | `/v1/device/sync` | فیلد `location` (مسیر اصلی batch) + پاسخ شامل `location_sharing` |
| GET | `/v1/families/me/devices/{id}/location/current` | توکن والد + مالکیت خانواده |
| GET | `/v1/families/me/devices/{id}/location/history?from&to&limit` | والد |
| GET/POST | `/v1/families/me/devices/{id}/location/settings` | والد |
| DELETE | `/v1/families/me/devices/{id}/location/history?scope=today\|last7\|all` | والد |

**هویت دستگاه فقط از توکن**؛ `deviceId` بدنه‌ی درخواست هرگز پذیرفته نمی‌شود؛ خانواده‌ی بیگانه ⇒ 404 بدون نشت.

## ۵) APIهای اندروید (همه رسمی)

- `FusedLocationProviderClient.getCurrentLocation(PRIORITY_BALANCED_POWER_ACCURACY, CancellationToken)` — fix تک‌شات، بدون listener دائمی
- `FusedLocationProviderClient.getLastLocation` — fallback با `source=last_known`
- `LocationManager.isLocationEnabled` — وضعیت سرویس مکان
- `ContextCompat.checkSelfPermission` — وضعیت مجوزها
- `ActivityResultContracts.RequestMultiplePermissions` / `RequestPermission` — جریان رسمی runtime
- `Settings.ACTION_APPLICATION_DETAILS_SETTINGS` / `ACTION_LOCATION_SOURCE_SETTINGS` — هدایت رسمی به تنظیمات
- `com.google.android.gms:play-services-location:21.3.0`

## ۶) مجوزها — حداقلِ لازم

| مجوز | چرا لازم است |
|---|---|
| `ACCESS_FINE_LOCATION` + `ACCESS_COARSE_LOCATION` | fix دقیق با fallback تقریبی — استاندارد runtime اندروید |
| `ACCESS_BACKGROUND_LOCATION` | **نیاز واقعی:** اشتراک موقعیت وقتی اپ کودک بسته است (WorkManager در پس‌زمینه). در API 29+ بدون آن fix پس‌زمینه ممکن نیست |

هیچ مجوز دیگری اضافه نشد (بدون ALWAYS-ON GPS، بدون FGS، بدون مجوز بی‌ربط). جریان دومرحله‌ای رسمی: اول while-in-use (دیالوگ)، بعد برای background در API 30+ هدایت به تنظیمات اپ («Allow all the time») و در API 29 دیالوگ رسمی سیستم — **بدون هیچ دور زدنی**.

## ۷) معماری Map Provider (بند ۱۱)

- `interface MapProvider { id, displayName, attribution, MapSurface(camera, marker, onCameraChange) }`
- `MapProviders.current` + `MapProviders.install(provider)` — جایگزینی Google Maps/Mapbox بدون دست‌کاری Business Logic.
- پیاده‌سازی فعلی: **`OsmTileMapProvider`** — کاشی‌های raster اوپن‌استریت‌مپ، **بدون API key**، با pan/zoom، cache حافظه (۴۸ کاشی)، سقف ۲۴ کاشی در هر بارگذاری، User-Agent شناسا و درج attribution طبق license OSM.
- **هیچ کلیدی hard-code نشده.** برای providerهای کلیددار (Google/Mapbox) کلید باید از `local.properties`→`BuildConfig` تزریق شود (در گزارش مستند شده؛ کدی که کلید بخواهد هنوز فعال نیست).
- نکته‌ی صادقانه: نقشه‌ی OSM برای نمایش marker/بازبینی تاریخچه کاملاً کافی است؛ اگر بعداً کاشی زنده در منطقه‌ی شما کند بود، `install()` همان نقطه‌ی تعویض است.

## ۸) امنیت

- Family Authorization سخت‌گیرانه (ownedDevice ⇒ 404 برای بیگانه) + تست
- Device Authorization: توکن دستگاه فقط endpoint کودک؛ توکن والد فقط endpoint والد + تست
- توکن منقضی/باطل/دستگاه حذفشده ⇒ 401/410 + تست
- Rate limiting روی `location/sync`
- **Encryption in transit:** همان زیرساخت Phase 1 — HTTPS/TLS در استقرار واقعی (`network_security_config` فعلاً فقط به دامنه‌ی سرور شما اجازه می‌دهد؛ برای LAN تست debug مجوز cleartarget محدود است)
- **Encryption at rest:** مختصات در Postgres با دسترسی کنترل‌شده؛ در گوشی کودک فقط صف موقت JSON در DataStore خصوصی اپ (بدون root قابل خواندن نیست)؛ در گوشی والد **هیچ ذخیره‌ی دائمی مختصات وجود ندارد** (فقط cache حافظه‌ی نقشه که با بستن اپ پاک می‌شود)
- Revoke Access: حذف دستگاه ⇒ cascade کامل همه‌ی ردیف‌های locations

## ۹) حریم خصوصی

- مختصات فقط با sharing فعال ذخیره می‌شود — **دو لایه:** کودک هنگام خاموش بودن اصلاً fix نمی‌گیرد/ارسال نمی‌کند (و صف محلی‌اش پاک می‌شود) + سرور هم هر مختصاتی را در حالت خاموش دور می‌ریزد (enforce + تست)
- Retention واقعی و خودکار: ۷/۳۰/۹۰ روز یا «فقط آخرین موقعیت»؛ purge روی سرور، نه فقط UI
- حذف واقعی: امروز / ۷ روز / همه — با **تأیید دومرحله‌ای برای «همه»**؛ DELETE واقعی از DB و نمایش تعداد رکوردهای حذف‌شده
- هیچ reverse-geocoding انجام نمی‌شود ⇒ مختصات هرگز به سرویس سوم (جغدکد) فرستاده نمی‌شود؛ به‌همین دلیل کارت به‌جای برچسب «Home» مختصات را صادقانه نشان می‌دهد
- موقعیت کهنه هرگز «موقعیت فعلی» جا زده نمی‌شود: پرچم `is_current` سمت سرور محاسبه و در UI «آخرین موقعیت معلوم» نمایش داده می‌شود؛ دقت ضعیف هم صادقانه نشان داده می‌شود

## ۱۰) تأثیر باتری (بند ۲۱)

| عامل | اندازه |
|---|---|
| الگوی دریافت مکان | **یک fix تک‌شات به ازای هر چرخه‌ی sync** (پیش‌فرض هر ۱۵ دقیقه = ۹۶ fix در روز) — هیچ listener دائمی، هیچ FGS، هیچ wake قفل‌شده |
| timeout | حداکثر ۱۲ ثانیه تلاش برای fix؛ در شکست، lastLocation (بدون مصرف اضافه) |
| اولویت Fused | BALANCED_POWER_ACCURACY (تعادل دقت/مصرف؛ نه HIGH_ACCURACY دائم) |
| شبکه | صفر درخواست اضافه — موقعیت سوار همان sync موجود (payload هر نقطه ≈ ۱۲۰ بایت؛ صف ۲۴۰ نقطه ≈ ۲۹KB بدترین حالت) |
| نقشه (والد) | فقط کاشی‌های نمای فعلی + cache حافظه؛ دانلود فقط هنگام باز بودن صفحه‌ی نقشه |
| پایگاه داده | هر ردیف ≈ ۱۲۰ بایت؛ ۹۶ نقطه/روز ≈ ۱۱.۵KB/روز؛ retention ۳۰ روز ≈ ۳۵۰KB به ازای هر دستگاه |
| برآورد مصرف | تک‌fix بالانس ≈ چند ثانیه GPS/شبکه در هر چرخه ⇒ در عمل زیر ۲–۳٪ باتری در روز (دستگاه معمول) |

**معیار اصلاح:** اگر در تست ۱۵-مرحله‌ای مصرف باتری کودک به‌طور محسوس بالا بود (مثلاً >۵٪/روز صرف مکان)، قبل از ادامه: فاصله‌ی sync سرور-محور را افزایش دهید (از تنظیمات Phase 2) یا اولویت را به `PRIORITY_LOW_POWER` تغییر دهید — هر دو تک‌خطی و آماده‌اند.

## ۱۱) تست‌های خودکار

### سرور — `Phase4Test.kt` (۱۹ تست یکپارچه؛ اجرا با Docker: `cd server && gradle test`)
- **Permission/State:** granted ⇒ یک `LOCATION_PERMISSION_GRANTED` (sync تکراری ⇒ تکرار ندارد) · denied ⇒ `LOCATION_PERMISSION_REVOKED` + health=unavailable · سرویس خاموش ⇒ `LOCATION_DISABLED` + `LOCATION_UNAVAILABLE` · روشن مجدد ⇒ `LOCATION_ENABLED` + دقیقاً یک `LOCATION_UPDATE_RECEIVED` (بازیابی، نه هر fix)
- **Location:** fix معتبر ⇒ current با isCurrent=true و health=healthy · دقت پایین (۲۵۰۰m) صادقانه برگردانده می‌شود · fix ۲ ساعت پیش ⇒ isCurrent=false + delayed + یک `LOCATION_SYNC_DELAYED` · بدون داده ⇒ fix=null + unavailable · lat=91/lon=181/سن ۵۵ ساعت/source نامعتبر/وضعیت نامعتبر ⇒ ۴۰۰
- **Offline/Batch/Duplicate:** بچ ۵ نقطه‌ی آفلاین ⇒ هر ۵ ذخیره، ترتیب نزولی · بازفرستانی همان نقطه ⇒ stored=0 و تاریخچه ۱ رکورد (UNIQUE device+client_ts) · location سوار بر `/v1/device/sync` ⇒ ذخیره می‌شود
- **Sharing toggle:** خاموش ⇒ stored=0، fix=null، health=unavailable ولی وضعیت مجوز ثبت می‌شود · روشن ⇒ ذخیره از سر
- **Retention:** ۷ روز ⇒ purge رکورد ۱۰ روزه · ۰ ⇒ فقط آخرین fix · -1/366 ⇒ ۴۰۰ · ۹۰ ⇒ OK
- **Delete:** today/last7/all ⇒ شمارش واقعی حذف، تاریخچه خالی، scope نامعتبر ⇒ ۴۰۰
- **Security:** بدون توکن/توکن خراب/توکن والد روی endpoint کودک/توکن کودک روی endpoint والد ⇒ ۴۰۱ · خانواده‌ی بیگانه روی ۴ endpoint + delete ⇒ ۴۰۴ · دستگاه تصادفی ⇒ ۴۰۴ · توکن منقضی ⇒ ۴۰۱ · دستگاه حذف‌شده ⇒ Gone/401

### اندروید
`:core:common:testDebugUnitTest` ⇒ **۹/۹ سبز**. (اجرای کامل تست اندروید مثل قبل در محیط شما؛ سندباکس رم محدود است.)

## ۱۲) تست روی دستگاه واقعی (۱۵ سناریوی بند ۲۰)

| # | سناریو | انتظار |
|---|---|---|
| 1 | مجوز مکان فعال (جریان رسمی از کارت کودک) | 🟢 Active در کودک؛ کارت والد بعد از sync: دقت/زمان/سلامت 🟢 |
| 2 | مجوز غیرفعال | 🔴 «مجوز موقعیت لازم است» در کودک؛ والد: health=🔴 + اعلان REVOKED/UNAVAILABLE |
| 3 | سرویس Location گوشی خاموش | کودک: «🔴 خاموش» + دکمه تنظیمات؛ والد: `LOCATION_DISABLED` + unavailable |
| 4 | GPS ضعیف (داخل ساختمان) | fix با accuracy بزرگ — UI والد همان عدد را صادقانه نشان می‌دهد (بدون پنهان‌کاری) |
| 5 | حرکت کودک | نقاط جدید در تاریخچه با فاصله‌ی چرخه‌ی sync؛ marker نقشه به‌روز |
| 6 | Last Known | وقتی fix کهنه است عنوان «آخرین موقعیت معلوم» + «Updated: X پیش» (نه «موقعیت فعلی») |
| 7 | قطع اینترنت کودک | sync شکست می‌خورد؛ نقاط در صف محلی می‌مانند (بدون از دست رفتن) |
| 8 | حرکت در آفلاین | هر چرخه fix گرفته و در صف اضافه می‌شود (تا سقف ۲۴۰) |
| 9 | بازگشت اینترنت | WorkManager retry ⇒ کل صف یک‌جا sync می‌شود |
| 10 | Batch Sync | در والد تمام نقاط دوره‌ی آفلاین با timestamp خودشان در تاریخچه |
| 11 | Duplicate Prevention | حتی اگر retry تکراری بفرستد، UNIQUE(device,client_ts) ⇒ رکورد تکراری صفر |
| 12 | خاموش کردن sharing از والد | کودک: «🔴 خاموش توسط والد» (از پاسخ sync) و از چرخه‌ی بعد هیچ fix نمی‌گیرد/ارسال نمی‌کند (صف محلی هم پاک)؛ سرور: stored=0؛ کارت والد: fix=null |
| 13 | فعال‌سازی مجدد | بدون درخواست مجوز جدید (اگر مجوزها پابرجا باشند) کودک از چرخه‌ی بعد capture را از سر می‌گیرد |
| 14 | Delete History (today/7/all) | شمارش حذف نمایش داده می‌شود؛ تاریخچه واقعاً خالی؛ «همه» با تأیید دومرحله‌ای |
| 15 | Revoke Device | حذف دستگاه ⇒ ۴۰۱/Gone برای sync کودک + cascade حذف همه‌ی locations در DB |

راهنمای گام‌به‌گام ساخت/اجرا مثل فازهای قبل: سرور + `LOCATION_STALE_SEC` اختیاری در `.env`، دو APK با `-PguardianBaseUrl=http://<LAN_IP>:8080`.

## ۱۳) محدودیت‌های شناخته‌شده‌ی اندروید (بند ۱۸ — پاسخ صریح)

1. **مجوزها به تفکیک نسخه:**
   - API 26–28 (اندروید ۸–۹): فقط `ACCESS_FINE/COARSE` با یک دیالوگ runtime؛ مفهوم «background» جدا وجود ندارد.
   - API 29 (اندروید ۱۰): background با دیالوگ رسمی «Allow all the time» در همان جریان requestPermissions.
   - API 30+ (اندروید ۱۱–۱۶): درخواست مستقیم `ACCESS_BACKGROUND_LOCATION` دیالوگ نمی‌دهد؛ **باید** کاربر را به صفحه‌ی تنظیمات اپ هدایت کرد و «Allow all the time» را انتخاب کند — پیاده‌سازی ما دقیقاً همین جریان رسمی دو مرحله‌ای است (بدون دور زدن).
2. **Foreground Service و notification اجباری — پاسخ صادقانه به «مخفی کردن نوتیف»:** اگر از FGS نوع `location` استفاده می‌شد، اندروید یک notification دائمی **اجباری** می‌ساخت و **مخفی کردن آن با هیچ API رسمی ممکن نیست** (و تلاش برای مخفی‌سازی نقض سیاست Play و خط قرمز پروژه است). راه‌حل ما: **اصلاً FGS استفاده نشده** ⇒ هیچ notification اجباری وجود ندارد و سیاست «اپ کودک صفر notification» حفظ شده. هزینه‌ی این انتخاب: دریافت مکان فقط در چرخه‌های WorkManager (≥۱۵ دقیقه) و هنگام باز بودن اپ — یعنی ردیابی لحظه‌ای ثانیه‌به‌ثانیه ممکن نیست (و برای مصرف خانواده ضرورت هم ندارد).
3. **Battery Optimization (Doze):** در Doze عمیق، WorkManager ممکن است چرخه‌ها را به تعویق بیندازد ⇒ health به «delayed» می‌رود (صادقانه در والد). راهنمای «بهینه‌سازی باتری» از Phase 2 در ChildHome هست؛ اگر کاربر آن را unrestricted کند، چرخه‌ها منظم‌تر می‌شوند.
4. `lastLocation` ممکن است متعلق به اپ دیگری باشد و کهنه باشد ⇒ فقط با `source=last_known` و محافظ ۴۸ ساعت استفاده می‌شود و هرگز «current» جا زده نمی‌شود.
5. WorkManager حداقل دوره‌ی ۱۵ دقیقه دارد ⇒ «فاصله‌ی به‌روزرسانی» نمی‌تواند کمتر از این باشد (بدون FGS).

## ۱۴) محدودیت‌های شناخته‌شده‌ی Google Play

- دسترسی `ACCESS_BACKGROUND_LOCATION` در Play Console نیازمند **فرم اعلامیه‌ی دسترسی** و در برخی موارد ویدیوی نمایش عملکرد است؛ استفاده باید هسته‌ی اصلی عملکرد اپ باشد (در اپ خانواده‌محور با رضایت والد/فرزند مطابق سیاست است — در Phase 10 برای انتشار آماده می‌شود).
- سیاست «Family Apps»: اپ کودک‌محور باید در پلی‌استور اعلام target audience کند؛ موقعیت پس‌زمینه برای اپ خانوادگی با افشای شفاف مجاز است ولی بررسی دستی Google دارد.
- OSM tiles: استفاده‌ی کم‌حجم خانوادگی مجاز است؛ استفاده‌ی سنگین نیاز به سرور کاشی خودی/provider تجاری دارد (تعویض با `MapProviders.install`).
- این APKها فعلاً **sideload** می‌شوند (مثل فازهای قبل)؛ مسیر انتشار رسمی در فازهای پایانی.

---

### خلاصه‌ی وضعیت ساخت
- سرور: compile سبز + unit سبز؛ `Phase4Test` آماده‌ی اجرا با Docker
- اندروید: هر دو APK debug با موفقیت ساخته شدند (child ≈ ۱۳.۲MB با play-services-location، parent ≈ ۱۲.۸MB) — شامل گیت بند ۱۶ و ردیف Last Sync بند ۱۷
- پاک‌سازی: دایرکتوری‌های build حذف شدند؛ مخزن ۱.۵MB

**توقف طبق قرارداد BUILD → TEST → FIX → APPROVE → NEXT FEATURE.**
منتظر `APPROVED` یا `NEXT PHASE` یا گزارش `FIX` شما هستم.
