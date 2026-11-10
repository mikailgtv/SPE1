# Phase 17 — ممیزی UX پیش از تغییر

**زمان:** 2026-10-03، پیش از هر تغییر کد در Phase 17. این baseline از source موجود تهیه شد؛ هیچ flow یا قابلیت اجرا/شبیه‌سازی نشده است.

## وضعیت ابزار/اجرا

در این محیط `node`/`npm` و Java/Javac موجود است؛ Java نسخهٔ 11. Gradle و Gradle wrapper، Android SDK/adb، Docker/Podman، PostgreSQL client/server و مرورگر قابل‌اجرای محلی پیدا نشدند. Backend endpoint، credentials، emulator/physical device و browser E2E در دسترس/اثبات نشده‌اند. فقط Web unit/build را می‌توان واقعاً اجرا کرد. در نتیجه Android TalkBack، font scaling، reboot، background restriction، location permission و end-to-end fresh install فعلاً از پیش **BLOCKED** هستند.

## ممیزی componentها

### Parent Android

- شروع فعلی: token → login یا بررسی خانواده؛ خانوادهٔ غایب به FamilySetup می‌رود؛ سپس Dashboard. Pairing از Dashboard با کد/QR و countdown وارد route جدا می‌شود. Wizard هفت‌مرحله‌ای، verify-after-pairing و setup health واحد وجود ندارد.
- Dashboard چند کارت متوالی برای هر device (status، usage، apps، location، geofence) دارد؛ refresh دوره‌ای ۳۰ ثانیه‌ای هنگام باز بودن screen. خالی‌بودن دستگاه، افزودن device را پیشنهاد می‌کند.
- Remove Device confirmation موجود است؛ `DashboardViewModel` در خطای session و network پیام کد محور می‌دهد. بعضی خطاهای جزئی API در `runCatching` به مقدار خالی/default تبدیل می‌شوند و علت از دست می‌رود.
- ناوبری Parent مبتنی بر routeهای جدا؛ bottom navigation سادهٔ Home/Alerts/Children/Devices/More در source دیده نشد. زبان از locale سیستم و RTL خودکار است؛ منبع string fa/en هست اما hard-coded Persian/English فراوان است. Theme سیستم light/dark را دنبال می‌کند و انتخاب سه‌حالتهٔ app وجود ندارد.

### Child Android

- شروع فعلی: اگر device credential فعال باشد Home، در غیر این صورت صفحهٔ واردکردن کد؛ بعد از exchange، صفحهٔ تأیید Family/Parent و نام دستگاه، سپس Home. کد QR scanner در UI Child دیده نشد.
- disclosure مفصل پیش از ورود کد یا بخش مستقل «این اپ چه می‌کند» وجود ندارد. متن فعلی pairing می‌گوید اپ «توسط والد نصب و مدیریت» شده؛ این ادعا با استقلال نصب/حالت عادی Device Owner سازگار نیست و نیازمند اصلاح است.
- Home دسترسی Usage Access و Location را با وضعیت واقعی و راه‌رفتن به Settings توضیح می‌دهد؛ permissionها هنگام نمایش Home خودکار درخواست نمی‌شوند. statusهای app enforcement و Web Safety تا حدی صادقانه‌اند.
- battery optimization snapshot به Backend ارسال می‌شود ولی مرکز Permission یکپارچه/راهنمای فعال‌سازی آن در Child UI دیده نشد. Permission اعلان Child به‌عمد درخواست نمی‌شود؛ عادی sync notification ندارد، و SOS foreground service مسیر اعلان قابل‌مشاهدهٔ جدا دارد.
- Child رشته‌های inline زیاد دارد؛ زمان آخرین sync به‌صورت نسبی فارسی است. ارتباط و retry در Worker وجود دارد ولی صفحهٔ sync queue/debug قابل‌مشاهده نیست.

### Parent Web

- React/Vite app، login و register؛ access/refresh فقط در حافظهٔ tab؛ `/preview` دادهٔ واقعی/fake ندارد. `/preview` مجوز protected-data نیست.
- routeهای Dashboard, Devices, usage, location, zones, app rules, Web Safety, notifications, SOS, reports, privacy و settings وجود دارد؛ pairing code در صفحهٔ Devices ساخته می‌شود. ساخت Family در Web فعلی مسیر اجرایی ندارد؛ کاربر حساب تازه را به صفحهٔ Dashboard می‌برد و FamilySetup دیده نمی‌شود. UX onboarding/verify/setup health وجود ندارد.
- Web انگلیسی/فارسی، RTL/LTR، localStorage فقط برای زبان/theme؛ mobile drawer و bottom nav، skip-link و reduced-motion CSS موجودند. Theme فقط light/dark است، نه system. Search/filter در notification page بخشی از UI، حذف device/zone/rule و خاموش‌کردن sharing confirmation دارند.
- health indicator به `/health/live` وصل است و صرفاً زنده‌بودن process را نشان می‌دهد، نه آماده‌بودن DB/storage؛ متن «online» می‌تواند بیش‌ازحد قوی باشد. Offline page فقط shell است و API cache نمی‌شود؛ session روی reload از بین می‌رود.
- empty/error states وجود دارند اما برخی متن‌ها generic یا hard-coded‌اند. device presence raw value ممکن است بی‌واسطه به کاربر برسد. query failure بعضی صفحه‌ها را جایگزین می‌کند و last cache timestamp مشترک/indicator ندارد.
- Privacy Center فعلی به عدم پشتیبانی تماس/مخاطب/دوربین و محتوای صفحه اشاره دارد؛ backend export/delete-data API ندارد، پس نباید action جعلی اضافه شود.

### Backend، Auth و Pairing

- Backend routeهای account login/register/refresh/logout، خانواده، pairing، devices، health/status، notifications، SOS و domain API دارد. Password reset، session management list، logout-all و export/delete-data API در source جاری پیدا نشدند.
- Parent/Child credential و principal جدا؛ backend device pairing code یک‌بارمصرف کوتاه‌عمر است؛ Android Pairing ViewModel error code را دارد. Web pairing creation پیام‌ها عمدتاً raw API error message هستند؛ همهٔ دلایل invalid/expired/used/device already linked/network/family missing به پیام راهنما map نشده‌اند.
- API `health/live` liveness و `health/ready` DB/storage readiness را جدا می‌کند؛ push `not_configured`. هنگام error auth Web 401 refresh می‌کند و Android بر اساس error tokenهای revoked/expired را پاک می‌کند. تست runtime unavailable است.
- از routeهای پیشین و Phase 16: Device removal soft remove و session revoke دارد؛ خانواده و device scope در server کنترل می‌شود. حساب reset/recovery feature موجود نیست.

### Notifications، SOS و error handling

- notification severity/category در API و Web/Android UI وجود دارد؛ Web هشدارهای Critical/Warning/Information را رنگ/label می‌کند. Parent Android app notifications از server feed/local OS scheduling دارد؛ Child notification permission `not_requested` است. Push server ندارد.
- SOS Parent و Child screens موجودند؛ Parent acknowledge/resolve و Child activation/cancel/tracking. Child تأیید و شمارش معکوس دارد؛ تماس emergency service ثالث انجام نمی‌شود. Resolve confirmation روی همهٔ UIها یکدست نیست.
- Backend `{code,message}`؛ validation 400؛ 401/403/404/409/410/429/500. Web بیشتر raw error messageها را نشان می‌دهد؛ Android `GlErrorBanner` برای برخی کدهای رایج localizes می‌کند. خطاهای `NO_FAMILY` و pairing نیازمند route درست/راهنمای retry هستند.

### Permissions، Offline و Sync

- Child uses UsageStatsManager و Android location permission رسمی؛ Device Owner/DPM فقط مدیریت واقعی. `DeviceInfo.permissionsSnapshot`: location، usage access، battery optimization، notifications=`not_requested`; `LocationPermission` foreground/background/provider را جدا گزارش می‌کند.
- Android Child WorkManager min 15min، network constraint، location/SOS/app/Web queues محدود و رمزگذاری‌شده؛ usage queue مستقل ندارد. Web service worker فقط offline shell را cache می‌کند و `/v1` را bypass؛ API data عمداً offline ذخیره نمی‌شود.
- Parent Android/Web action queue عمومی offline ندارد. Android dashboard روی network failure banner کد خطا دارد؛ Web query retry خودکار صفر و loading/error states. هیچ UI واحدی برای pending/failed queue موجود نیست؛ نباید queue detail حساس Child را نشان داد.
- Manufacturer restriction، process kill/reboot/Doze، token expiry/revoke و sync recovery از روی source قابل استنتاج قطعی نیستند؛ device test لازم است.

## یادداشت اصلاح factual (پس از آغاز تغییرات)

بازبینی source هنگام تهیهٔ گزارش‌های فاز نشان داد جملهٔ baseline دربارهٔ نبودن مسیر ساخت خانواده در Web دقیق نبود: `FamilySetup` در `OverviewPage` هنگام پاسخ 404 خانواده اجرا می‌شد و API واقعی `POST /v1/families` را فراخوانی می‌کرد. کمبود واقعی، wizard/readiness منسجم پس از ثبت خانواده/ورود و راهنمای verify-after-pairing بود، نه نبودن endpoint ساخت Family. این اصلاح، baseline پیش از تغییر را حذف نمی‌کند.


موجود را دوباره‌کاری نمی‌کنیم: auth، family creation Android، server pairing/QR و error codes، existing screens، notifications/SOS، Android permission pathways، WorkManager/offline queues و Web offline shell حفظ می‌شوند. تمرکز code changes پس از این ممیزی بر تکمیل web family/onboarding، honest readiness/offline UX، Child privacy disclosure/permission explainer و کم‌خطرترین بهبودهای status/theme است. هیچ telemetry یا capability نظارتی تازه‌ای به دامنه اضافه نمی‌شود.
