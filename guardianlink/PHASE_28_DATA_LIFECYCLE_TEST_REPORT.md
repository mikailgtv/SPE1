# گزارش آزمون Data Lifecycle — Phase 28

**وضعیت:** `PASS_LOCAL_SYNTHETIC_POSTGRES`؛ فقط دامنهٔ آزموده‌شده.  
**وضعیت کلی Phase 28:** `BLOCKED_BY_ENVIRONMENT`؛ release تصمیم جداگانه دارد.

## محیط و حفاظت داده

- PostgreSQL محلی 17.11؛ DB آزمون `guardianlink_phase28_test` جدا از runtime smoke DB `guardianlink_phase28_runtime`؛ role/رمز موقت و DATA_DIR فقط خارج repository و با permission محدود.
- همهٔ fixtureها synthetic با ایمیل `.invalid`, خانواده/دستگاه/app/location/SOS ساختگی بودند؛ دادهٔ production یا شخص واقعی استفاده نشد.
- suite از `GL_TEST_DB_*` استفاده کرد و `IntegrationBase` Ktor `testApplication` را به PostgreSQL واقعی متصل کرد؛ Testcontainers/Docker اجرا نشد. DB/role/env و JDK موقت پس از راستی‌آزمایی حذف شدند.
- schema جدیدی اضافه یا cascade/retention migration تغییر داده نشد. اجرای runtime روی DB خالی، Flyway V1–V9 را validate و apply کرد.

## ماتریس آزمون و نتیجه

| حوزه | Fixture/عملیات | DB/API شاهد | نتیجه |
|---|---|---|---|
| Export A/B | دو خانواده و app usage با مقادیر synthetic جدا | پاسخ A شامل A و فاقد B؛ user token مجاز، device/no token رد | پاس |
| رازهای export | refresh token و device-secret hash/install id/password hash بررسی شد | body JSON فاقد مقادیر/کلیدهای credential | پاس |
| Export empty/validation | خانوادهٔ خالی، CSV، scope نامعتبر، `family_id` ناشناخته | JSON خالی 200؛ ورودی نامجاز 400 | پاس |
| Export bound | ۱۰٬۰۰۱ ردیف app usage synthetic | 413 `EXPORT_TOO_LARGE` | پاس |
| Export rate | پنج درخواست و درخواست ششم در پنجرهٔ پنج دقیقه | 200×5، سپس 429 | پاس |
| Family delete: sole member | app usage, location, geofence, notifications, SOS rows | cascade و zero-count assert برای جدول‌های تست‌شده؛ user/session باقی ماند | پاس |
| Family delete: shared | دو Parent عضو یک family | family/account deletion هر دو 409؛ دو عضویت، داده و session unchanged | پاس |
| Account delete | sole-family account و account بدون family | user/family/device/session/pairing cascade؛ token نامعتبر شد | پاس |
| Re-auth | رمز نادرست برای family/account | 401 و rowهای قبلی پابرجا | پاس |
| Transaction rollback | trigger دیتابیس synthetic در DELETE وابسته | پاسخ 500؛ خانواده/device/member سالم و قابل‌خواندن ماند | پاس |
| Device remove | device متعلق/نامتعلق، token و location history | cross-family=404؛ device فقط removed و secret/session revoke؛ location باقی | پاس |
| Location erase | today/last7/all و scope missing/invalid، device ناموجود و family دیگر | شمار رکوردهای حذف‌شده و SQL row counts بررسی شد؛ retry=0 | پاس |
| Destructive rate guard | سه تأیید family-delete نامعتبر و تلاش چهارم | سه 401، سپس 429؛ family unchanged | پاس |

## شواهد اجرای نهایی

- دستور نهایی از `server/` با `GL_TEST_DB_URL/USER/PASS` و Temurin JDK 17.0.20.1: `bash ./gradlew --no-daemon --max-workers=1 -Pkotlin.compiler.execution.strategy=in-process -Dorg.gradle.jvmargs='-Xms64m -Xmx768m -XX:MaxMetaspaceSize=256m -XX:ActiveProcessorCount=1' test`.
- نتیجه نهایی: **۱۵۴ test؛ ۰ failure، ۰ error، ۰ skipped**. XMLهای Gradle `server/build/test-results/test/TEST-*.xml` جمع‌شماری شدند. suite sequential اجرا شد.
- پس از suite، process توزیع‌شدهٔ Netty روی DB disposable تازه اجرا شد: ۹ migration موفق؛ export/family delete/account delete از HTTP واقعی بررسی شد؛ بعد از حذف account SQL=`users=0`, `families=0`. DB/role test و runtime سپس حذف شدند.
- Web tests/build نیز جدا اجرا شدند؛ آن‌ها شاهد Data Lifecycle نیستند.

## محدودیت‌های شاهد

- شاخهٔ overflow دقیق **۵ MiB**، transaction timeout سی‌ثانیه‌ای در حالت expiry، رقابت هم‌زمان/multi-replica و database crash mid-commit به‌طور مستقیم fault-injection نشدند؛ کد/constraintها و normal path آزموده شده‌اند، نه این سناریوها.
- حذف از backup تاریخی/Cloud، DR restore، retention log بیرونی و offline Android data در این فاز اجرا نشده است.
- این گزارش اجرای synthetic روی PostgreSQL محلی است؛ staging/production integrity، legal compliance یا دسترسی Android را ثابت نمی‌کند.
