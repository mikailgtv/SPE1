# گزارش Phase 10 — Security Hardening & Production Readiness

**وضعیت: در جریان؛ فاز ۱۰ کامل نشده است. ارزیابی فعلی پروژه: `NOT READY`.**  
کاربر پیش از شروع این فاز، Phase 9 را صریحاً با وجود build/test/migration/device gateهای اجرا‌نشده تأیید کرد. این اجازه، هیچ‌یک از آن gateها را PASS نکرد.

## دامنهٔ بررسی واقعی

فایل‌های اصلی سرور (`Auth`, `UserRoutes`, `DeviceRoutes`, `Tokens`, `PasswordHasher`, `Db`, `Main`, rate limiting، routeها و migrationهای V1–V9)، Android manifests، token/SecureStore/API client، LocationQueue، SOS queue/service، route navigation و پیکربندی Android/Compose/Docker/Caddy بررسی شدند. گزارش‌های Phase 1–9، docs امنیت و محدودیت Android نیز مرور شدند.

این **ممیزی کامل/خط‌به‌خط تمام منطق ۶۸ خواسته‌شده نیست**؛ کل APIها از نظر route surface فهرست شدند ولی همهٔ DTOها، همهٔ queryها، همهٔ domain rules، dependency vulnerabilities و تمام بخش‌های Android به‌طور عمیق تست یا threat-model نشده‌اند. گزارش امنیت، این شکاف‌ها را صریحاً ثبت می‌کند.

## Hardening انجام‌شده در کد/پیکربندی

1. Pairing `exchange` اکنون با `FOR UPDATE` و شرط status از active به exchanging می‌رود؛ `confirm` زیر transaction/row lock token را یک‌بار مصرف می‌کند. نیازمند تست concurrency PostgreSQL.
2. Refresh rotation برای user/device زیر transaction ردیف را قفل و revoke را شرطی می‌کند تا دو refresh هم‌زمان هر دو موفق نشوند. Reuse-chain detection هنوز وجود ندارد.
3. Password enrollment در Parent/backend به ۱۲–۱۲۸ کاراکتر هم‌راستا شد؛ Argon2id به `t=3, m=64MiB, p=1` رفت؛ ناشناخته‌بودن email با dummy verification پوشش داده شد؛ موقتاً آرایهٔ char صفر می‌شود. Test credentialها و validator Android به‌روز شدند. باید روی hardware و Testcontainers اندازه‌گیری شود.
4. Rate limiter حافظه‌ای keyها را محدود و bucketهای قدیمی را prune می‌کند؛ برای app/web/location/geofence و برخی readها سقف‌های per-device/user افزوده شد؛ هنوز برای چند replica مشترک نیست و همهٔ تغییرات نیازمند تست‌اند.
5. صف location که قبلاً PlainPrefs بود، به SecureStore/Android Keystore منتقل می‌شود و queue قدیمی پس از مهاجرت رمز‌شده پاک می‌شود؛ refresh موقتاً ناموفق دیگر credential محلی را پاک نمی‌کند؛ پاک‌سازی فقط برای statusهای 401/403/410 تعریف شده است.
6. log عمومی exception دیگر `cause.message` را نمی‌نویسد؛ `/health` وضعیت DB را بدون جزئیات secret اعلام می‌کند.
7. logهای شناسایی device/family/user/session به ID کوتاه‌شده تغییر کردند و نام Safe Zone از log حذف شد.
8. Compose: Backend port در config اصلی فقط داخل شبکهٔ Docker expose می‌شود؛ Caddy XFF را از remote peer بازنویسی و headerهای TLS/امنیت اضافه می‌کند؛ image backend با کاربر non-root اجرا می‌شود. DB secret نمونهٔ پیش‌فرض حذف شد.
9. Release Android از `.example` به URL ورودی operator تغییر کرد؛ Release package task به HTTPS واقعی و keystore variables بیرون از مخزن وابسته شد. R8/minification عمداً خاموش مانده تا keep rules با build/test واقعی بررسی شود.
10. Emergency Center نقشهٔ SOS را به ترسیم محلی شماتیک تغییر داد؛ درخواست tile مبتنی بر مختصات SOS به OSM ارسال نمی‌شود.
11. شش سند درخواستی ایجاد شدند: `SECURITY_AUDIT_REPORT.md`, `PRODUCTION_READINESS_REPORT.md`, `API_DOCUMENTATION.md`, `USER_GUIDE.md`, `CHILD_SETUP_GUIDE.md` و همین گزارش.

## نتیجهٔ اجرا

- `docker-compose.yml` و override توسعه با PyYAML parse شدند: موفق.
- manifestهای Android با XML parser بررسی شدند: موفق.
- `libs.versions.toml` با TOML parser بررسی شد: موفق.
- چند فایل Kotlin از نظر delimiter خام بررسی شدند: sanity متنی، نه Kotlin compilation.
- Android/Server Gradle build و test، Flyway/PostgreSQL، Docker image، dependency scan، adb/emulator/physical device، restore و E2E اجرا نشدند.
- شمارش ایستای annotationهای تست: ۱۳۲ server، ۱۸ Android local، ۰ Android `androidTest` در مسیرهای جست‌وجوشده. **این آمار نتیجهٔ آزمون نیست.**

## Blockerهای اصلی

1. RLS دیتابیس وجود ندارد؛ tenant isolation به query scoping وابسته است.
2. DB runtime و Flyway migration از credential واحد استفاده می‌کنند؛ اصل حداقل دسترسی اجرا نشده.
3. کلید خصوصی JWT از فایل JWK روی `DATA_DIR` خوانده/ساخته می‌شود؛ secret manager/KMS، permission audit، key rotation و restore test کامل نیست.
4. Audit log سراسری actor-aware/append-only وجود ندارد؛ telemetry و SOS event جایگزین آن نیستند.
5. encryption-at-rest دادهٔ حساس سمت PostgreSQL/backup پیاده/اثبات نشده و retention/delete/recovery per-data-class ناقص است؛ به‌خصوص SOS history، backup/restore و حذف cache/دادهٔ برگشتی.
6. Rate limiter توزیع‌شده، body-size limit، global request timeout، correlation ID، metrics و alerting کامل نشده‌اند.
7. Parent session list/revoke و refresh-token reuse detection وجود ندارد.
8. Dependency/SBOM/OSV/OWASP scan انجام نشده؛ نسخه‌های transitive lock نشده‌اند.
9. R8، ProGuard/serialization keep rules، release signing واقعی، release artifact، production domain و TLS handshake آزمایش نشده‌اند.
10. E2E زنجیره‌ای دو گوشی، authorization/IDOR کامل، Doze/OEM/battery، performance datasetها و backup/restore test اجرا نشده‌اند.

## Final Quality Gate

| Gate | وضعیت واقعی |
|---|---|
| BUILD | **NOT RUN / BLOCKED** — Gradle wrapper/executable موجود نبود. |
| UNIT TESTS | **NOT RUN** — تست‌ها موجودند، تعداد بالا صرفاً annotation count است. |
| INTEGRATION TESTS | **NOT RUN** — PostgreSQL/Testcontainers اجرا نشد. |
| E2E | **NOT RUN** — دستگاه/سرور واقعی نداشتیم. |
| SECURITY AUDIT | **PARTIAL** — یافته‌ها ثبت شده‌اند؛ sign-off نیست. |
| REAL DEVICE | **NOT RUN** |
| BACKUP RESTORE | **NOT RUN** |
| OFFLINE TEST | **NOT RUN** |
| PERMISSION TEST | **NOT RUN** |
| AUTHORIZATION / IDOR | **PARTIAL CODE REVIEW; TESTS NOT RUN** |
| RELEASE BUILD/SIGN | **NOT RUN** — نیاز به host و keystore operator. |

## تصمیم نهایی

**`NOT READY`** برای استفادهٔ واقعی/انتشار. هیچ blockerی پنهان یا صرفاً برای سبزکردن گزارش حذف نشده است. Phase 10 هنوز پایان نیافته و Release Candidate واقعی وجود ندارد. Feature بزرگ جدید اضافه نشده؛ تغییرات این دور hardening، documentation و اصلاح حریم خصوصی/پیکربندی بوده‌اند.

### گام بعدی برای ادامه

محیط Gradle/JDK/Android SDK/PostgreSQL/Docker/دو دستگاه واقعی و keystore آزمایشی امن لازم است. ابتدا build/test، migration concurrency، IDOR matrix و offline/permission checks؛ سپس رفع خطاها و تکرار کامل gateها. تا ثبت نتایج واقعی، وضعیت باید `NOT READY` باقی بماند.
