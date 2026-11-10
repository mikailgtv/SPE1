# گزارش مرحله‌ای GuardianLink — Phase 5 به‌عنوان تاریخچه

> این فایل تاریخچهٔ کوتاه Phase 5 و وضعیت Phase 6/7 را نگه می‌دارد. جزئیات کامل Phase 6 و 7 در گزارش‌های مستقل پیوندشده آمده است. این بازسازی کامل نسخهٔ قدیمی Phase 5 نیست؛ آزمون‌های ادعاشده در زیر همان ادعاهای تاریخی سند هستند و نباید به Phase 6 یا 7 تعمیم داده شوند.

## Phase 5 — Geofencing: خلاصهٔ تاریخی گزارش‌شده

Phase 5 قابلیت محدوده‌های امن را روی location fixes موجود در Phase 4 بنا می‌کند. دریافت مختصات Child همان جریان مجوز/اشتراک رسمی قبلی است؛ Geofence fix تازه یا GPS listener دائمی ایجاد نمی‌کند. Event باید از transition state machine و fixهای معتبر بیاید؛ Child نمی‌تواند transition را جعل کند. دقت و تأخیر در دستگاه واقعی وابسته به مجوز، شبکه، Doze، provider و cadence همگام‌سازی است.

### 2. API

- `/v1/families/me/devices/{id}/geofences` — فهرست و ساخت محدوده.
- `/v1/families/me/devices/{id}/geofences/{gid}` — ویرایش/حذف.
- `/v1/families/me/devices/{id}/geofences/{gid}/events` — رویدادهای همان محدوده.
- `/v1/families/me/devices/{id}/geofence-events` — تاریخچهٔ محدوده‌های دستگاه.
- Child: `GET /v1/device/geofences` و `POST /v1/device/geofences/sync` برای محدوده‌های فعال؛ `POST /v1/device/geofences/events` برای رویدادهای معتبرسازی‌شده.

Parent فقط از مسیر family ownership به دستگاه خانوادهٔ خودش دسترسی دارد؛ هویت Child از token/session دستگاه تعیین می‌شود.

### 3. دیتابیس و اعتبارسنجی

Migration `V5__phase5.sql` جدول‌های `geofences`، `geofence_states`، `geofence_events` و `geofence_notification_settings` را ایجاد می‌کند. نام محدوده روی هر دستگاه case-insensitive یکتا است؛ حذف zone، state و تاریخچهٔ وابسته را cascade می‌کند.

- شعاع پیش‌فرض در محدودهٔ ۵۰ تا ۵۰۰۰ متر؛ قابل تنظیم با `GEO_MIN_RADIUS_M` و `GEO_MAX_RADIUS_M`.
- سقف پیش‌فرض ۵۰ zone/device؛ `GEO_MAX_ZONES`.
- cooldown پیش‌فرض ۳۰۰ ثانیه؛ `GEO_COOLDOWN_SEC`.
- حد accuracy پیش‌فرض ۵۰۰ متر؛ `GEO_MIN_ACC_M`.
- event retention پیش‌فرض ۳۰ روز، configurable تا ۳۶۵ روز با `GEO_EVENT_RETENTION_DAYS`؛ پاک‌سازی lazy در sync/history.

### 4. منطق گذار و خطای GPS

- State اولیه/پس از تغییر هندسه یا re-enable برابر `UNKNOWN` است؛ اولین fix معتبر فقط baseline را تعیین می‌کند و alert ورود/خروج نمی‌سازد.
- Hysteresis با شعاع و خطای accuracy استفاده می‌شود؛ نوار مرزی مبهم حالت قبلی را حفظ می‌کند.
- accuracy بدتر از آستانه، نبود fix، مجوز/سرویس غیرفعال یا location-sharing خاموش موجب ادعای داخل/خارج جدید نمی‌شود.
- cooldown و dedupe transition تکراری را محدود می‌کند؛ «آخرین مکان» به‌جای رویداد تازه معرفی نمی‌شود.
- خاموش‌کردن location sharing state zoneها را `UNKNOWN` می‌کند؛ روشن‌کردن مجدد هم برای baseline منتظر fix تازه می‌ماند.

### 5. Android و تأخیر

Fused location در اولویت و `LocationManager` fallback است. هیچ foreground service، continuous GPS listener یا Child notification جدیدی ایجاد نشده. Geofence رویدادها را از fixهای Phase 4 می‌سازد؛ زمان دریافت رویداد وابسته به sync cadence، WorkManager/Doze، شبکه، مجوز و accuracy است و real-time نیست. Parent entry/exit اعلان‌ها را با تنظیمات per-zone می‌گیرد؛ ارسال push/FCM پس‌زمینه اضافه نشده و Parent event polling موجود استفاده می‌شود.

### 6. Build / آزمون تا پایان Phase 5 — ادعای تاریخی

گزارش ثبت‌شده برای Phase 5 می‌گوید Server `gradle test -PskipIT` و هر دو debug APK در آن محیط با `BUILD SUCCESSFUL` ساخته شده‌اند. PostgreSQL/Docker integration و آزمون دو گوشی واقعی اجرا نشده‌اند. این نتیجهٔ تاریخی Phase 5 است؛ به Phase 6 یا Phase 7 مربوط نیست.

### 7. چک‌لیست آزمون فیزیکی Phase 5 (نتیجه‌ها Pending)

1. روی Parent یک Home zone با شعاع مجاز بسازید؛ Child sync شود؛ state اولیه `UNKNOWN`.
2. Child را به بیرون محدوده ببرید؛ اولین fix فقط `OUTSIDE` را baseline کند.
3. پس از cooldown به داخل بروید؛ یک `ENTER` و فقط در صورت فعال‌بودن notification تنظیم‌شده Parent alert.
4. به بیرون بروید؛ یک `EXIT` و timestamp/dwell بررسی شود.
5. نزدیک مرز چند بار رفت‌وبرگشت کنید؛ accuracy بد/مرزی باید state را نامطمئن/ثابت نگه دارد و اعلان تکراری نسازد.
6. اینترنت Child را قطع کنید و حرکت دهید؛ پس از اتصال، queue و event بررسی شود؛ event timing می‌تواند از fix دیرتر نمایش داده شود.
7. restart دستگاه؛ zoneها باید از سرور پس از sync برگردند.
8. Disable/Enable یا تغییر مرکز/شعاع؛ state باید به `UNKNOWN` reset شود. Delete: zone و history وابسته حذف شوند.
9. Revoke Device؛ Child GET/POST باید رد شود و event تازه ثبت نشود.
10. در دستگاه مجاز، Play Services را غیرفعال کنید تا fallback `LocationManager` بررسی شود. نبود provider/fix باید نبود transition بدهد، نه وضعیت ساختگی.
11. با Battery Historian و network capture، مصرف، sync latency و payload را اندازه بگیرید و فقط اعداد واقعاً مشاهده‌شده را ثبت کنید.

### 8. Performance / Accuracy

- تعداد zone: پیش‌فرض ۵۰/device و configurable.
- درخواست شبکهٔ اضافهٔ Child برای Geofence: صفر؛ Parent safe-zone polling حداکثر هر دو دقیقه در Dashboard فعال.
- cooldown پیش‌فرض ۳۰۰s و accuracy threshold پیش‌فرض ۵۰۰m محافظه‌کارانه است: false alert را کم می‌کند اما در محیط شهری ممکن است transition دیر یا مبهم شود.
- Battery impact افزوده نسبت به Phase 4: listener/frequency/request اضافه سمت Child ندارد؛ absolute fix frequency همان Phase 4 است.
- False-transition rate و Average event delay روی real device اندازه‌گیری نشده‌اند؛ هیچ میانگین یا درصد ساختگی ارائه نشده.

### 9. Security و Privacy

- مختصات همچنان self-report دستگاه است؛ rooted/compromised device یا mock location می‌تواند مختصات جعلی تولید کند. این سیستم ضدتقلب مطلق نیست.
- Parent ownership، Child token، ورودی lat/lon/radius/name/time validation، rate limit، dedupe و state machine کنترل می‌شوند.
- zone و event مکانی حساس‌اند؛ فقط والد خانواده و دستگاه مربوطه مجازند.
- حذف zone، state و event history مرتبط را cascade حذف می‌کند؛ lifecycle event در device audit feed می‌ماند، بدون مختصات.
- دادهٔ مکانی خام برای Child نمایش داده نمی‌شود و Child notification/hidden monitoring/camera/microphone/screen recording اضافه نشده.
- DB production نیازمند دیسک/volume encryption؛ محیط sandbox تضمین at-rest encryption نمی‌دهد.

### منابع رسمی Phase 5

- [Create and monitor geofences — Android Developers](https://developer.android.com/develop/sensors-and-location/location/geofencing)
- [Access location in the background — Android Developers](https://developer.android.com/develop/sensors-and-location/location/background)
- [Request location permissions — Android Developers](https://developer.android.com/develop/sensors-and-location/location/permissions)
- [Google Play background location declaration and disclosure](https://support.google.com/googleplay/android-developer/answer/9799150)
- [Google Play policy announcement](https://support.google.com/googleplay/android-developer/answer/16926792?hl=en)

---

# وضعیت Phase 6 و Phase 7

> اجازهٔ شروع Phase 7 به‌معنای تأیید Phase 6 نیست.

## Phase 6 — App Management (Pending / Unverified)

پیاده‌سازی و گزارش Phase 6 در `PHASE6_REPORT.md` و راهنمای دستگاه در `PHASE6_DEVICE_TEST_GUIDE.md` ثبت شده‌اند. در تغییر فعلی، validation برای رد Rule هدف‌گیرندهٔ `AGENT_PACKAGE` اضافه و assertion integration نوشته شد؛ اما کد، migration و آزمون‌های Phase 6 در محیط این جلسه build یا اجرا نشدند و تست دستگاه واقعی نیز اجرا نشده است. Phase 6 تأیید نشده است.

## Phase 7 — Web Safety (Initial implementation / Unverified)

گزارش کامل محدوده، مدل داده، API، وضعیت پیاده‌سازی و تست‌های Pending در `PHASE7_REPORT.md` آمده است. مسیر enforcement پیاده‌سازی‌شده فقط DPM managed configurations برای URLBlocklist/URLAllowlist در Chrome روی Device Owner/Profile Owner است؛ «فیلتر کامل اینترنت» یا blocked-visit telemetry نیست. VPN، DNS filtering و category classifier فعال در این نسخه وجود ندارند. Build/test/migration و دستگاه واقعی Pending هستند.
