# گزارش Phase 18 — Security Events و Privacy Monitoring

## تغییر/کنترل‌ها
- Request ID ورودی فقط UUID canonical پذیرفته می‌شود؛ ورودی نامعتبر با UUID داخلی جایگزین می‌شود تا log/header injection ممکن نباشد.
- route template metrics از allowlist ساخته می‌شود؛ هیچ raw URL، query، user/device/package identifier یا request body به metrics نمی‌رسد.
- Backend structured logger فقط safe field allowlist را می‌پذیرد؛ token، refresh token، secret، password، pairing code، email، IP، device/family/session ID، SOS/location payload و note در markerها رد می‌شوند.
- Error classification و 429 metricها bounded category هستند. Existing rate limiter local process باقی مانده؛ در multi-replica همچنان shared limiter لازم است.
- Server request errors به exception class و دسته محدود می‌شوند؛ stack trace و exception message encoder عمداً configure نشده‌اند.
- `Redact.token/email/code` در Backend و Android از fingerprint/partial masking به `<redacted>` تغییر کرد.
- `/metrics` در matcher Caddy route نشده و server تنها در Compose internal network `expose` دارد. انتشار مستقیم پورت 8080 بیرون شبکه private امنیت metrics را نقض می‌کند.

## امنیت‌های هنوز فاقد monitoring
- audit log جدا با immutable retention/operator review وجود ندارد؛ security logs فعلی application stdout هستند. 401/403 و rate limiting request metrics می‌دهند اما IDOR pattern/anomaly detection و alert dedup اجرا نشده.
- requestId به DB event/notification persist نشده؛ ردیابی transaction-to-event cross-service هنوز کامل نیست.
- هیچ SaaS error SDK یا client request/error content ارسال نمی‌شود.

## Secret/dependency checks
- جست‌وجوی الگوهای high-confidence PEM private key، AWS AKIA، literal JWT و cloud key assignment: **۰ candidate** در فایل‌های قابل‌خواندن workspace.
- `gitleaks`, `trufflehog`, `detect-secrets` در PATH موجود نیستند؛ نتیجه اسکن سفارشی جامع نیست و history خارج از workspace بررسی نشده.
- `npm ci --ignore-scripts` audit: ۰ vulnerability گزارش‌شده؛ این گزارش جایگزین بررسی کامل supply-chain و Gradle dependencies نیست.
- چون secret واقعی پیدا نشد، موردی حذف/rotate نشد. هیچ production log/secret-manager workspace برای بررسی وجود نداشت.

## Test/status
- unit source tests برای route redaction/error categories وجود دارد؛ اجرا **BLOCKED**.
- runtime abuse/IDOR/rate-limit alert pipeline و sink access control **BLOCKED**؛ ابزار/infrastructure/credentials موجود نیست.