# گزارش Phase 18 — Health، Metrics و Performance

## Health checks
- `GET /health/live` process/router را بدون dependency بررسی می‌کند.
- `GET /health/ready` و alias قدیمی `GET /health` DB با `SELECT 1` و وجود/read/write بودن DATA_DIR را بررسی می‌کنند؛ failure باید 503 بدهد و جزئیات حساس را برنمی‌گرداند. `push_notifications=not_configured` باقی می‌ماند و fake healthy نشان داده نمی‌شود.
- Auth ارائه‌دهندهٔ بیرونی ندارد؛ authentication به config/session/DB همان Backend وابسته است. Background scheduler مستقل سرور وجود ندارد. به این دو وضعیت ساختگی HEALTHY برای dependency جدا افزوده نشد.

## API و DB metrics در source
- `/metrics` خروجی Prometheus text داخلی ارائه می‌کند: شمارش HTTP بر اساس method/route template/status class، خطاهای HTTP، 429، latency sum/count و p50/p95/p99 از پنجرهٔ اخیر ۵۱۲ نمونه برای هر سری.
- SQL helperهای مشترک `Connection.query/exec/execReturningUuid` شمارش، failure و latency aggregate می‌گیرند؛ query text/params را ذخیره یا export نمی‌کنند.
- Hikari MXBean active/idle/waiting، configured max/min idle و دسترس‌پذیری bean گزارش می‌شود.
- Error counter دسته‌ای ثابت (AUTH, DATABASE, NETWORK, VALIDATION, PERMISSION, SYNC, NOTIFICATION, LOCATION, GEOFENCE, SOS, WEB, SECURITY, INTERNAL) از StatusPages ثبت می‌شود.
- سری HTTP سقف دارد و overflow به route label ثابت می‌رود. هیچ user/device/location/SOS label وجود ندارد.
- Caddy matcher عمداً `/metrics` را reverse proxy نمی‌کند؛ Compose فقط `expose` داخلی دارد. هرگونه publish مستقیم پورت Backend به اینترنت ممنوع است تا ACL/Auth برای scraper اضافه شود.

## Pool و performance
- تنظیمات کنونی، نه مقادیر benchmarkشده: max=10, min-idle=2, acquire timeout=5000ms, validation=3000ms, idle timeout=600000ms, max lifetime=1800000ms.
- Endpoint latency به routeهای ثابت، p50/p95/p99 آخرین ۵۱۲ درخواست per-series را می‌سنجد. این quantile کوتاه‌پنجره است و با restart پاک می‌شود؛ multi-instance جمع‌آوری بیرونی لازم دارد.
- Database SQL metrics aggregate هستند؛ SQL slow query text/frequency per statement، connection acquisition latency، transaction commit time، migration version و failed migration metric پیاده نشده‌اند.
- Threshold پیشنهادیِ قبلی در `MONITORING_GUIDE.md`: API p95>500ms هشدار و >1000ms بحرانی؛ این صرفاً candidate پس از load-test است، نه SLO اندازه‌گیری‌شده و در فاز حاضر alert خودکار ندارد. آستانهٔ slow query بدون baseline/تصویب operator تعریف نشد.

## Alert/dashboard/status
Prometheus/Grafana/CloudWatch/Sentry/collector، alert receiver، dedup/cooldown و operator dashboard در زیرساخت حاضر وجود ندارند. `/health/ready` status عمومی محدود است، اما all-systems status page یا operational dashboard ساخته نشد تا route admin و زیرساخت/ACL بی‌پشتوانه معرفی نشود.

## راستی‌آزمایی
- Test source برای request ID، `/metrics`، quantile label و عدم افشای URI اضافه شد؛ اجرا **BLOCKED**.
- Web: `npm test` (۴ تست) و `npm run build` موفق؛ Web تغییری در Phase18 نداشت.
- Backend/DB/Prometheus scrape واقعی و latency load test **BLOCKED_BY_ENVIRONMENT**.