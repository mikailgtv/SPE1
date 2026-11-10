# گزارش Phase 2 — Device Monitoring + Battery + Last Sync + Connection Health

> **تأییدیه‌های این محیط:** سرور کامپایل ✅ + تست واحد سبز ✅ + تست‌های Integration فاز ۲ کامپایل و آماده اجرا ✅ · اندروید: کامپایل هر ۱۰ ماژول ✅ + هر دو APK debug ✅.
> تست‌های Integration سرور و تست روی گوشی واقعی روی ماشین/گوشی‌های شما اجرا می‌شود.

---

## 1) Files Created / Modified

**سرور (Created):**
- `db/migration/V2__phase2.sql` — جدول‌های `family_settings`، `device_status`، `device_events`
- `StatusRepos.kt` — DeviceStatusRepo / DeviceEventRepo (emit با dedupe_key UNIQUE) / FamilySettingsRepo
- `Monitoring.kt` — BatteryBand، Health، PayloadGuard (validation کامل)، Json، MonitoringService (تنها نقطه‌ی تولید رویداد + محاسبه health سمت سرور)
- `MonitoringRoutes.kt` — endpointهای Parent: health / permissions / events / settings (GET+PUT) / family events + helper مالکیت `ownedDevice`
- `Phase2Test.kt` — ۱۶ تست Integration جدید

**سرور (Modified):** `Config.kt` و `application.conf` (آستانه‌های قابل تنظیم)، `Models.kt` (DTOهای جدید + توسعه DeviceOut)، `Auth.kt` (AppDeps)، `UserRoutes.kt` (mapper جدید deviceOut با maskedId/health/permissions)، `DeviceRoutes.kt` (heartbeat توسعه‌یافته + `/status` + `/sync` دسته‌ای + ingest مشترک با validation و رویدادها)، `Main.kt` (ثبت monitoringRoutes).

**اندروید (Created):** `core/common/BatteryBand.kt` (+تست)، `feature/dashboard/ParentNotifier.kt`.
**اندروید (Modified):** `core/network` (DTOها + endpointهای sync/status/health/permissions/events/settings)، `feature/childstatus` (DeviceInfo: snapshot مجوزها + نوع اتصال؛ StatusSyncWorker: sync دسته‌ای + اعمال interval سروری؛ SyncScheduler: interval پارامتری؛ ChildHome: کارت وضعیت کامل)، `feature/dashboard` (کارت حرفه‌ای + بلوک مجوزها + رویدادهای اخیر + دیالوگ آستانه‌ها + درخواست POST_NOTIFICATIONS)، `app-parent/AndroidManifest.xml` (POST_NOTIFICATIONS فقط برای Parent)، رشته‌های fa/en جدید.

## 2) Architecture Changes
- **Heartbeat دسته‌ای:**_child_ به‌جای چند call، یک `POST /v1/device/sync` می‌فرستد (باتری + اتصال + snapshot مجوزها/نسخه‌ها). **interval را سرور برمی‌گرداند** (`next_sync_sec`) و Child فقط عندالتغییر WorkManager را reschedule می‌کند.
- **Connection Health سمت سرور:** `healthy` (age ≤ sync_delayed) / `delayed` (≤ offline) / `offline` (> offline)؛ محاسبه در هر خواندن (lazy) تا بدون سرویس پس‌زمینه اضافه، رویدادهای SYNC_DELAYED / DEVICE_DISCONNECTED تولید شوند؛ DEVICE_CONNECTED هنگام heartbeat پس از offline/نخستین sync.
- **Battery bands با هیسترزیسِ band ذخیره‌شده:** رویداد فقط روی **تغییر band** ⇒ بدون اسپم رویداد.
- **Permission snapshot:** Child فقط «وضعیت» مجوزهای رسمی موردنیاز همین اپ را می‌فرستد (location=not_configured تا Phase 4، notifications=not_requested چون Child صفر اعلان است، battery_optimization، usage_access)؛ سرور diff می‌گیرد ⇒ PERMISSION_CHANGED.
- **Parent-only notifications:** کانال‌های battery/connection/sync + diff رویدادها بر اساس ts؛ push پس‌زمینه (FCM) مطابق docs/12 در Phase 8.

## 3) API Changes (همه با auth + authorization + family ownership)
| Endpoint | مصرف‌کننده | کار |
|---|---|---|
| `GET /v1/families/me/devices/{id}` | Parent | کارت کامل (maskedId، band، health، permissions، lastSync، firstConnected) |
| `GET /v1/families/me/devices/{id}/health` | Parent | HealthOut + آستانه‌ها |
| `GET /v1/families/me/devices/{id}/permissions` | Parent | snapshot مجوزها |
| `GET /v1/families/me/devices/{id}/events?limit` | Parent | رویدادهای دستگاه |
| `GET /v1/families/me/events?limit` | Parent | رویدادهای خانواده (Recent Events) |
| `GET/PUT /v1/families/me/settings` | Parent | آستانه‌ها (قابل تنظیم، با validation بازه‌ها) |
| `POST /v1/device/heartbeat` | Child | ping سبک (باتری+اتصال) |
| `POST /v1/device/status` | Child | snapshot مجوزها/نسخه‌ها |
| `POST /v1/device/sync` | Child | دسته‌ای = heartbeat+status+last_sync؛ پاسخ: health + next_sync_sec + آستانه‌ها |

نگاشت به فهرست خواسته‌شده: `GET /devices/{id}` ⇒ مسیر family-scoped بالا (مالکیت صریح)؛ `POST /devices/{id}/heartbeat|status|sync` ⇒ مسیرهای device-scoped با توکن خود دستگاه (دستگاه نمی‌تواند برای id دلخواه heartbeat بفرستد — امن‌تر از path parameter).

## 4) Database Changes
`family_settings` (آستانه‌ها per family با پیش‌فرض env) · `device_status` (snapshot زنده: battery/band/connection/health/last_heartbeat/last_sync/نسخه‌ها/permissions/updated_at) · `device_events` (id, device_id, event_type, ts, metadata, **dedupe_key UNIQUE** + ایندکس device+ts). هیچ داده حساس اضافه‌ای ذخیره نمی‌شود.

## 5) Security Changes
- **PayloadGuard:** باتری 0..100 وگرنه 400 · connection_state و permission state فقط از مجموعه مجاز · کلید مجوز `^[a-z_]{1,40}$` · سقف طول متن‌ها · سقف ۳۲ کلید مجوز · queued_count بازه‌دار.
- **Rate limit** per-device روی heartbeat/status/sync (۶/دقیقه).
- health/permissions/events/settings همگی از `ownedDevice` عبور می‌کنند (خانواده غریبه ⇒ 404 بدون نشتی).
- رویدادها فقط سرور تولید می‌کند؛ client نمی‌تواند event type دلخواه درج کند.
- PUT settings با validation بازه‌ها (critical < low، offline ≥ delayed، interval ≥ 900 چون WorkManager حداقل ۱۵ دقیقه دارد).

## 6) Build Instructions
مانند Phase 1 (بدون تغییر):
```bash
cd guardianlink/deploy && docker compose up -d --build   # با env جدید اختیاری
cd guardianlink/android
gradle :app-parent:assembleDebug :app-child:assembleDebug
```

## 7) Real Device Test Guide (سناریوها + Expected)
ستون Actual را هنگام تست پر کنید.
| # | سناریو | Expected | Actual |
|---|---|---|---|
| 1 | Child آنلاین | کارت والد: 🟢 + Healthy + باتری و Last Sync «هم‌اکنون» | |
| 2 | Child بدون sync اولیه | Health: «در انتظار نخستین sync» | |
| 3 | قطع اینترنت Child | پس از offline threshold: 🔴 Offline + رویداد/نوتیف DEVICE_DISCONNECTED | |
| 4 | بازگشت اینترنت | sync فوری (NetworkCallback) ⇒ 🟢 + DEVICE_CONNECTED | |
| 5 | تغییر باتری (شارژ/مصرف) | عدد باتری در sync بعدی به‌روز | |
| 6 | باتری ≤ آستانه low | band=low + رویداد BATTERY_LOW + نوتیف Parent | |
| 7 | باتری < آستانه critical | band=critical + BATTERY_CRITICAL + نوتیف | |
| 8 | تغییر مجوز (مثلاً battery optimization را در Settings کودک off/on کنید) | رویداد PERMISSION_CHANGED با metadata from/to | |
| 9 | Restart Child | پس از boot، WorkManager دوره‌ای ادامه می‌دهد؛ sync بعدی ≤ interval | |
| 10 | Restart Parent | بدون تغییر؛ داشبورد با poll بازیابی می‌شود | |
| 11 | Logout Parent | ورود مجدد لازم؛ رویدادها دست‌نخورده | |
| 12 | Login مجدد | Dashboard همان داده‌ها | |
| 13 | Remove Device | Child به صفحه «قطع شده»؛ sync ⇒ 410 | |
| 14 | Sync پس از Revoke | 410/401 و credential پاک‌شده روی Child | |
تست سریع آستانه‌ها: در `.env` مقدار `OFFLINE_SEC=180 SYNC_DELAYED_SEC=120` بگذارید یا از دیالوگ ⚙️ والد (دقیقه) مقدار کم ست کنید.

## 8) Automated Tests
سرور (`gradle test`): Battery (100/50/30/15/5 + invalid 101/-1) · events فقط روی تغییر band · threshold پرخانواده · heartbeat موفق/delayed/missing/duplicate/unauthorized · sync پس از offline (+APP_UPDATED) · payload بد ⇒ 400 · revoked/wrong-family/expired/invalid-id · permission granted/changed/status-sync بدون رویداد تکراری.
اندروید: `BatteryBandTest` (آستانه پیش‌فرض و سفارشی) + تست‌های Phase 1 (Validators/refresh/ZeroNotification).
```bash
cd guardianlink/server && gradle test            # نیازمند Docker
cd guardianlink/android && gradle :core:common:testDebugUnitTest :app-child:testDebugUnitTest
```

## 9) Battery Impact (چرا مصرف پایین است)
1. **یک درخواست دسته‌ای به‌جای چند درخواست:** هر ۱۵ دقیقه یک `sync` کوچک (~۱KB) به‌جای heartbeat+status+permissions جداگانه.
2. **بدون سرویس دائمی/FGS:** فقط WorkManager با constraint شبکه ⇒ در آفلاین هیچ تلاش شبکه‌ای نمی‌شود (رادیو خاموش می‌ماند).
3. **sync فوری فقط روی لبه‌ها:** باز شدن اپ یا وصل شدن شبکه (NetworkCallback) — نه polling پیوسته.
4. **رویدادها فقط روی تغییر state:** band/permission/health ذخیره‌شده ⇒ بدون ارسال/ثبت تکراری؛ dedupe_key هم جلوی race را می‌گیرد.
5. **Parent فقط وقتی صفحه باز است poll می‌کند** (۳۰ ثانیه)؛ در پس‌زمینه هیچ call périodic ندارد (تا Phase 8/FCM).
6. **interval از سرور:** بدون آپدیت اپ می‌توان فراوانی را کم/زیاد کرد (مثلاً شب‌ها interval بزرگ‌تر در فازهای بعد).
برآورد: ~۹۶ درخواست/روز برای Child در برابر ده‌ها هزار درخواست در معماری polling معمول — ناچیز در برابر مصرف روزانه گوشی.

## 10) Known Limitations
- نوتیف Parent فقط وقتی اپ Parent باز/فرآیند زنده است (poll)؛ **push پس‌زمینه = Phase 8 (FCM data-only)**.
- health به‌صورت lazy در خواندن‌ها به‌روز می‌شود؛ رویداد DEVICE_DISCONNECTED ممکن است تا اولین خواندن والد پس از عبور آستانه ثبت نشود (سویپر مستقل = Phase 8/10).
- interval دوره‌ای WorkManager حداقل ۱۵ دقیقه (محدودیت سیستم)؛ مقادیر کمتر در PUT رد می‌شوند.
- snapshot مجوزها فقط مجوزهای رسمی مرتبط با قابلیت‌های آینده خود اپ است (نه فهرست دلخواه).
- نمودار تاریخچه باتری (time-series) هنوز نیست؛ فقط وضعیت زنده + رویدادها (پیشنهاد Phase 3 در صورت نیاز).

## 11) Next Phase Recommendation
**Phase 3 — Screen Time:** `PACKAGE_USAGE_STATS` با راهنمای گام‌به‌گامgrant، گزارش مصرف اپ‌ها (روزانه)، Daily Limit به‌صورت advisory در M0 (مطابق Capability Ledger) + رویداد limit_reached برای والد. جایگزین رسمی اجرا (`setPackagesSuspended`) فقط پس از مسیر Device Owner در Phase 6.
منتظر `APPROVED` / `FIX` / `NEXT PHASE`.
