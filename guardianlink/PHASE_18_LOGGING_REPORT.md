# گزارش Phase 18 — Logging و Correlation

## تغییرهای پیاده‌شده
- `Logback` به JSON console encoder تغییر کرد؛ فیلدهای پایه شامل timestamp، level، logger، thread، message و service/environment ثابت است.
- `GlLogger` برای eventهای برنامه markers ساختاریافته با `category` و `event` تولید می‌کند. فیلدهای اضافی فقط از allowlist کم‌خطر مثل `requestId`, `method`, `route`, `status`, `durationMs`, `errorClass`, `errorCategory`, `count` و `health` عبور می‌کنند؛ user/device/family/session/email/IP/code/token/secret/body/payload/location یا متن exception به log marker راه ندارد.
- `X-Request-ID`: برای هر درخواست Backend یک UUID تازه می‌سازد و هر مقدار caller-supplied را نادیده می‌گیرد. شناسه در response header و پایان هر HTTP request با route template و latency ثبت می‌شود.
- global exception logging فقط exception class، request ID و دستهٔ کم‌جزئیات را ثبت می‌کند؛ stack trace و exception message وارد encoder نشده‌اند. خطاهای API استاندارد body خود را می‌دهند اما درخواست/credential log نمی‌شود.
- سرور و Android helperهای redaction دیگر fingerprint/hash یا بخشی از token/email/pairing code را برنمی‌گردانند و مقدار `<redacted>` می‌دهند. Android `AppLog` فقط در DEBUG فعال است و اکنون keyهای کم‌خطر را می‌پذیرد؛ URL/device/family/session/email/input در allowlist نیستند.

## شواهد حریم خصوصی
- routeهای لاگ/metrics از allowlist template ساخته می‌شوند؛ path خام، query string، package name و شناسهٔ device/user به label تبدیل نمی‌شوند.
- جست‌وجوی repository برای PEM private key، AWS access key، JWT literal و cloud secret assignment انجام شد: ۰ مورد الگو-قوی پیدا شد. `gitleaks`, `trufflehog` و `detect-secrets` نصب نیستند؛ این نتیجه معادل secret scanner جامع نیست.
- هیچ Backend در این محیط اجرا نشد؛ هیچ log واقعی تولید یا بازبینی نشده است. هیچ credential برای rotate پیدا نشد.

## محدودیت‌های مهم
- request ID به event/notif row در DB persist نمی‌شود؛ SQL را بدون query text aggregate می‌سنجیم. برای ردیابی دقیق event→notification در یک incident باید trace context صریح و schema/retention/access design جداگانه بررسی شود.
- Ktor/library logs زیر همان JSON encoder می‌روند، اما privacy review/production collector validation انجام نشده است.
- retention واقعی به log collector/operator وابسته است و cloud sink/IAM/encryption در این repo provision نشده.

## تست
- برای request-ID معتبر/نامعتبر، metrics export و redaction تست source افزوده شد؛ Gradle/Java 17 و Docker برای اجرای JUnit/Testcontainers نیستند (**BLOCKED**).
- Web frontend telemetry یا crash/error SDK نصب نشده؛ هیچ log از session/request کاربر به سرویس ثالث فرستاده نمی‌شود.
