# گزارش رفع Export — Phase 28

**وضعیت قابلیت:** `VERIFIED_LOCALLY_WITH_LIMITS`  
**وضعیت کلی Phase 28:** `BLOCKED_BY_ENVIRONMENT`  
**تصمیم انتشار:** **NO-GO**؛ این گزارش تأیید انتشار یا استقرار نیست.

## علت ریشه‌ای Phase 27

- probe قبلی `GET /v1/families/me/export` پاسخ `404` داشت.
- بازبینی source پیش از تغییر نشان داد مسیر، handler، service و repository برای export وجود نداشتند؛ مشکل route/capability غایب بود، نه خرابی DB یا شکست یک check احراز هویت.
- شناسهٔ خانواده در export از `UserPrincipal.familyId` احراز‌شده می‌آید؛ ورودی مشتری نمی‌تواند خانوادهٔ هدف را تعیین کند.

## پیاده‌سازی و قرارداد نهایی

- `GET /v1/families/me/export` با Parent session معتبر؛ فقط JSON و scope=`family`.
- پارامترهای مجاز فقط `format` و `scope`، هرکدام حداکثر یک‌بار. پارامتر ناشناخته مانند `family_id`، مقدار تکراری، CSV یا scope ناشناخته با `400 VALIDATION` رد می‌شود. بدون family، پاسخ 404؛ بدون احراز، 401.
- پاسخ synchronous JSON با schema نسخهٔ ۱، `generated_at`, خانواده، اعضا، بخش‌های داده و `record_count`. header دانلود نام‌گذاری‌شده است، اما فایل یا artifact مستقل ذخیره نمی‌شود.
- خانواده و اعضا و queryها در یک transaction فقط‌خواندنی با `REPEATABLE_READ` خوانده می‌شوند. query هر statement حداکثر ۵ ثانیه، تراکنش حداکثر ۳۰ ثانیه، خروجی حداکثر ۱۰٬۰۰۰ رکورد و ۵ MiB است. overflow با 413 `EXPORT_TOO_LARGE` پاسخ می‌گیرد. اجرای دقیق شاخهٔ سقف ۵ MiB به‌صورت مستقل آزموده نشد؛ شاخهٔ سقف رکورد با ۱۰٬۰۰۱ ردیف آزموده شد.
- dataset allowlist در `server/src/main/kotlin/ir/guardianlink/server/PrivacyService.kt`: خانواده/عضویت؛ نمایه و وضعیت/رویداد دستگاه؛ تنظیمات خانواده؛ مصرف اپ و screen-time؛ locations/status/settings؛ geofence/state/events/notification settings؛ inventory و app rules/schedules/state/restriction events؛ web policy/rules/schedules/overrides/events؛ notification preferences و read-state مجاز؛ SOS sessions/locations/events/notes/preferences.
- عمداً صادر نمی‌شوند: password hash؛ access/refresh/device secrets و hash آن‌ها؛ session/pairing/install credentials؛ binary icon/cache و دادهٔ global فاقد مالک خانواده. همهٔ این موارد فراتر از «رکوردهای exportشده» بررسی نشده‌اند و باید با schema evolution بازبینی شوند.
- audit عملیاتی ساختاریافته با `operation=export`, `scope`, `format`, `count` ثبت می‌شود. body/payload، ایمیل، token، شناسهٔ شخص، مختصات یا محتوای کاربر log نمی‌شود. این event صرفاً log-based است؛ پایگاه audit پایدار و retention مرکزی تعریف نشده.

## راستی‌آزمایی واقعی

- Integration: full suite نهایی `test` شامل ۱۵۴ تست، صفر failure/error/skip؛ اجرای آن روی PostgreSQL 17.11 اختصاصی `guardianlink_phase28_test` با `GL_TEST_DB_URL/USER/PASS` انجام شد. `IntegrationBase` از Ktor `testApplication` و PostgreSQL واقعی استفاده می‌کند؛ این اجرا Testcontainers نبود.
- `PrivacyLifecycleTest.family JSON export authenticates isolates family and excludes credentials`: دو خانوادهٔ synthetic با دادهٔ app usage مجزا؛ export خانوادهٔ A دادهٔ A را دارد و B را افشا نمی‌کند. احراز user الزامی است؛ device token رد می‌شود؛ password hash، device-secret hash، install id و refresh token در پاسخ نیستند.
- آزمون دیگر export: dataset خالی موفق؛ format/scope/پارامتر family تزریقی رد؛ ۱۰٬۰۰۱ ردیف synthetic پاسخ 413؛ rate limit درخواست ششم پس از پنج پاسخ موفق را 429 می‌کند.
- HTTP/Netty runtime روی distribution ساخته‌شده و DB disposable تازه: Flyway نه migration را validate/apply کرد؛ registration/family با 201؛ export JSON با 200 و دو رکورد synthetic؛ بدون token 401؛ CSV و `family_id` با 400. smoke test بدون دادهٔ واقعی اجرا شد.
- structured runtime logهای مشاهده‌شده برای export فقط operation/scope/format/count و route/status/duration bounded داشتند؛ هیچ payload یا مختصات در آن‌ها دیده نشد.

## فایل‌های مرتبط

- `server/src/main/kotlin/ir/guardianlink/server/PrivacyService.kt`
- `server/src/main/kotlin/ir/guardianlink/server/UserRoutes.kt`
- `server/src/test/kotlin/ir/guardianlink/server/PrivacyLifecycleTest.kt`
- `API_DOCUMENTATION.md`
- `LOCAL_ENVIRONMENT_SETUP.md`

## باقیمانده‌ها

- CSV، export حسابِ چندخانواده‌ای، streaming، object storage، دانلود قابل‌انقضا و durable audit ledger پیاده‌سازی نشده‌اند؛ ادعای پشتیبانی ندارند.
- آزمون bytes دقیق ۵ MiB، رقابت هم‌زمان در چند replica، large production-like benchmark، browser download/UI، staging/production، backup/restore و legal review انجام نشدند.
- export محلی تأیید می‌کند endpoint و DB behavior در محیط disposable کار می‌کنند؛ completeness برای schemaهای آینده، حذف نسخة export نزد کاربر یا پشتیبان بیرونی، و آمادگی انتشار را ثابت نمی‌کند.
