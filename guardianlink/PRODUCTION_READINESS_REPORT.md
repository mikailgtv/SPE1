# گزارش آمادگی Production — GuardianLink

**وضعیت فعلی: NOT READY.** این وضعیت بر اساس بررسی کد/پیکربندی و دروازه‌های واقعاً اجراشده است؛ Release Candidate یا آزمون production ادعا نمی‌شود.

## خلاصهٔ وضعیت

| حوزه | وضعیت | شواهد/محدودیت |
|---|---|---|
| Backend build | BLOCKED / اجرا‌نشده | Gradle و wrapper در محیط پیدا نشد؛ آخرین patchهای Kotlin هم کامپایل نشده‌اند. |
| Android Parent/Child build | BLOCKED / اجرا‌نشده | Android Gradle Plugin/SDK اجرا نشد؛ Gradle config امضا/Base URL و تغییرات Compose compile نشده‌اند. |
| Unit / Integration | نوشته‌شده، اجرا‌نشده | شمارش ایستا: ۱۳۲ `@Test` در server، ۱۸ در Android local tests، صفر در مسیر `androidTest` پیدا شد. این شمارش PASS نیست. |
| PostgreSQL migrations | اجرا‌نشده | V1–V9 روی DB اجرا/validate نشده؛ Docker و psql موجود نبود. Hikari pool limits/timeouts اکنون configurable شده ولی compile/runtime نشده. |
| E2E دو دستگاه | اجرا‌نشده | Pairing تا revoke/re-pair، notification، permission و SOS واقعی اجرا نشده. |
| HTTPS | پیکربندی جزئی، تأییدنشده | Compose اکنون backend را فقط در شبکه داخلی expose می‌کند و Caddy header/TLS edge دارد؛ دامنهٔ واقعی/گواهی/DNS/firewall اجرا نشده. طرح منتخب پیشنهادی AWS eu-central-1 با ALB TLS است، اما provision نشده. |
| Staging/Production hosting | BLOCKED / هیچ محیط واقعی | AWS فرانکفورت فقط به‌عنوان architecture recommendation تعیین شد؛ account, VPC, RDS, ECS, Secrets Manager و DNS credentials فراهم نشده‌اند. |
| Release signing | Gate افزوده، artifact موجود نیست | Release task اکنون به URL HTTPS واقعی و keystore variables خارج از repo نیاز دارد؛ keystore/secretها فراهم نشده‌اند. |
| Minification/R8 | BLOCKER برای Release | Minification خاموش مانده تا keep rules و Gson/serialization با تست معتبر شوند. |
| DB least privilege | BLOCKER | مهاجرت Flyway در startup با همان credential runtime انجام می‌شود؛ نقش migration و app جدا نیست. |
| RLS | BLOCKER دفاع‌عمقی | RLS/policy در migration پیدا نشد؛ جداسازی فعلی به query scoping وابسته است. |
| دادهٔ حساس در DB/backup | BLOCKER | field encryption برای Location/Usage/SOS دیده نشد؛ disk/backup encryption در استقرار واقعی اثبات نشده است. |
| Backup/restore | BLOCKER | volume تعریف شده، اما backup رمز‌شده، WAL/PITR، restore drill و delete-after-restore verification وجود/اجرا نشده‌اند. |
| Monitoring | BLOCKER | `/health/live` و `/health/ready` به کد اضافه شده‌اند؛ health failure اجرا نشده. metrics، alerting، request ID، crash/error dashboard و SLO بررسی/راه‌اندازی نشده‌اند. |
| Security/dependency scan | جزئی/اجرا‌نشده | Dependabot, PR dependency review, CodeQL و Trivy image workflow تعریف شدند؛ اجرا نشده‌اند؛ lockfile/SBOM/OSV/OWASP/gitleaks source scan موجود/اجراشده نیست. |
| CI/CD | تعریف در فایل، غیرفعال | GitHub Actions pipeline/release gates نوشته شد؛ Git repository/remote و GitHub environments/secrets موجود نیستند؛ workflow اجرا نشده. |
| Battery/performance | اجرا‌نشده | load profile, 100/500/10k… records و آزمایش Doze/OEM/idle انجام نشده. |
| Privacy/retention | BLOCKER | retention per-data-class و حذف کامل family/cache/backup هنوز تعیین و verify نشده. |
| Push notification | پیاده نشده | Phase 8 polling/local notification است؛ FCM یا رسید تحویل پس‌زمینه نداریم. |

## تغییرات Hardening این دور (هنوز تأییدنشده)

- `RateLimiter` map را به ۵۰هزار key محدود و bucketهای قدیمی را prune می‌کند؛ برای برخی mutation/read endpointها limit per-device/user اضافه شد؛ همچنان فقط تک-process است.
- Pairing exchange/confirm از transaction + row lock + شرط status برای جلوگیری از مصرف هم‌زمان استفاده می‌کند.
- User/device refresh rotation در تراکنش ردیف را قفل و revoke را شرطی می‌کند.
- Argon2id روی ۶۴ MiB، ۳ iteration و password enrollment ۱۲–۱۲۸ کاراکتر تنظیم شد؛ timing dummy verification برای email ناشناخته افزوده شد.
- صف مختصات Location از PlainPrefs به SecureStore/Android Keystore مهاجرت می‌کند؛ legacy queue پس از رمزکردن پاک می‌شود؛ Android password validator به ۱۲–۱۲۸ هم‌راستا شد و refresh موقتاً ناموفق credential را پاک نمی‌کند.
- پیام exception داخلی از log backend حذف شد؛ CallLogging مسیر/query/body را ثبت نمی‌کند و `logback.xml` با `INFO` شروع می‌شود. `/health/live` و `/health/ready` جدا شدند؛ shutdown pool اضافه شد. هیچ‌کدام runtime-tested نیستند.
- Backend در Compose از بیرون شبکهٔ container publish نمی‌شود؛ Caddy XFF ورودی را بازنویسی و security headers می‌گذارد؛ image backend با کاربر non-root اجرا می‌شود؛ DB secret پیش‌فرض ضعیف حذف شد.
- Release base URL دیگر host رزروشدهٔ `.example` ندارد؛ ساخت Release به HTTPS، versionName/versionCode معتبر و signing variables خارج از repository gate شده است. Workflowهای GitHub Actions برای test, lint, debug, staging/production artifact و GHCR container push افزوده شده‌اند، اما در remote repo اجرا نشده‌اند.
- SOS map به ترسیم محلی شماتیک تغییر کرد تا از روی مختصات SOS tile خارجی درخواست نشود.

## نتیجهٔ کیفیت

- YAML compose، Android manifests XML و `libs.versions.toml` با parser محلی ساختاری بررسی شدند: **parse موفق**.
- توازن خام delimiter چند فایل SOS بررسی شد: **صرفاً sanity check متنی**.
- این‌ها به معنی compilation یا correctness نیستند.
- هیچ Release APK/AAB، build successful، migration successful، passed test، E2E، penetration test، physical device check یا backup restore test ساخته/گزارش نمی‌شود.

## BLOCKERها برای Release Candidate

1. فراهم‌کردن محیط pinned Gradle/JDK/Android SDK/PostgreSQL/Docker و اجرای تمام builds/tests؛ رفع خطا و اجرای مجدد.
2. بازبینی مستقل session/authorization، RLS، actor-aware audit، refresh reuse detection و replay model.
3. جداسازی DB migration/runtime roles و secret manager برای DB/JWT signing keys.
4. تصمیم threat model و اجرای encryption-at-rest برای Location/Usage/SOS در DB/backup، به‌علاوهٔ retention/export/delete برای Location, Usage, Events, Notification, SOS, audit و cache/backup.
5. rate limiter مشترک، body limits، server/proxy timeouts، request ID، metrics و alerts.
6. backup encryption + restore/DR test واقعی و consistency با deletion.
7. dependency scan/SBOM و patch policy.
8. Release keystore مستقل/محافظت‌شده، R8 keep rules، حقیقی‌بودن domain، TLS test و build امضاشده.
9. دو گوشی واقعی: auth/pair/revoke/re-pair، همهٔ permissions، offline/reconnect، Doze/OEM، notification، SOS cancel/ack/resolve/location.
10. test performance با datasetهای 100 user/500 devices/10k, 100k, 1M events؛ ثبت latency/DB/CPU/memory.
11. تکمیل API docs و permission/data disclosure با بازبینی حقوقی/Play policy؛ Push در صورت نیاز محصولی جداگانه.

## تصمیم

**NOT READY** برای استفادهٔ واقعی یا انتشار. پیکربندی‌های جدید سخت‌گیرانه‌تر می‌شوند، اما چون build و تست در این محیط اجرا نشده‌اند، حتی اصلاحات این دور هم باید قبل از ادغام/استقرار compile، test و review شوند.
