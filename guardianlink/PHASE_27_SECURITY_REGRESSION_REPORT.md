# گزارش Security Regression — Phase 27

**وضعیت:** `CLOSED` برای آزمون‌های API محلی ذکرشده؛ `BLOCKED` برای آزمون‌های device/penetration/provenance.

## کنترل‌های اجراشده

- دو حساب، دو family و دو دستگاه paired از مسیرهای واقعی API و با دادهٔ صریحاً synthetic ساخته شدند؛ هیچ حساب یا دستگاه واقعی استفاده نشد.
- ۲۶ درخواست cross-family روی خواندن device/status/permissions/events/usage/location/geofence/app rules/web rules/SOS/notification و عملیات mutation probe شدند. پاسخ‌های عدم افشا 404/405 بود؛ تغییر Family B از Parent A مجاز نشد.
- feed رویداد Family A شامل شناسهٔ device خانوادهٔ B نبود و برعکس. GET و mark-read اعلان Family B با دسترسی Family A رد شد.
- App rule GET خالی مجاز بود؛ ساخت rule برای package نصب‌نشده 404 داد. inventory ساختگی به دستگاه نسبت داده نشد.
- تست‌های موجود secret redaction و bounded route labels در unit suite هدفمند پاس شدند؛ الگوی scan محدود workspace در گزارش محیط صفر مورد یافت.
- بررسی نمونه‌ای لاگ ساختاریافتهٔ runtime: route template، status، مدت و request id ثبت می‌شد؛ password/token/body/مختصات در خروجی گزارش نشد. خطای web با class/frame محدود ثبت شد و جزئیات DB فقط در بررسی محلیِ redact‌شده خوانده شد.

## Regression رفع‌شده

POST ساخت Web rule ابتدا به‌دلیل ناسازگاری مقدار `parent` با constraint ستون `web_filter_events.source` به 500 می‌رسید. نگاشت parent→server به کد اضافه، تست واحد مربوطه اجرا و سپس مسیر create/read واقعی پس از migration تمیز موفق شد. این مورد با شاهد اجرای مجدد بسته شد.

## محدودیت‌ها

این آزمون penetration test، بررسی JWT/rotation کامل، abuse/load، Android permission/policy، امنیت transport Production، دسترسی Git history یا compliance حقوقی نیست. حوزهٔ قضایی تعیین نشده است؛ هر ادعای حقوقی نیازمند `JURISDICTION-SPECIFIC REQUIREMENT` است.
