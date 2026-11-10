# گزارش آزمون بار — فاز ۱۹

**نتیجه:** `BLOCKED_BY_ENVIRONMENT`; آزمون بار اجرا نشد.

## دلیل و ایمنی

هیچ staging تأییدشده، PostgreSQL، Docker/Podman، JDK 17 یا ابزار آزمون بار در محیط فراهم نبود. به همین دلیل هیچ درخواست بار به backend/production ارسال نشد و هیچ اعلان، SOS یا رخداد مکانی واقعی/مصنوعی در سامانهٔ کاربر ایجاد نشد. آزمون سنگین علیه production ممنوع است.

## نتایج

| سنجه | نتیجه |
|---|---|
| کاربران/دستگاه‌های هم‌زمان | `NOT MEASURED` |
| درخواست در ثانیه و concurrency | `NOT MEASURED` |
| latency p50/p95/p99 | `NOT MEASURED` |
| نرخ 4xx/5xx/timeouts | `NOT MEASURED` |
| CPU، RAM، DB pool و query time | `NOT MEASURED` |
| اعلان/موقعیت/geofence/SOS در بار | `NOT MEASURED` |
| ظرفیت عملی | `INSUFFICIENT_DATA` |

## طرح آزمون بعدی (فقط staging)

1. تأیید کتبی endpoint/environment غیرتولیدی و پاک‌سازی داده پس از آزمون.
2. ساخت داده‌های ساختگی بی‌هویت؛ آزمون read-only ابتدا و write-path idempotent جداگانه.
3. تعیین mix درخواست، تعداد خانواده/device، اندازه payload و ramp-up با مالک سیستم؛ سپس بار پله‌ای و steady-state محدود.
4. ثبت p50/p95/p99، throughput، error, saturation، pool/lock و resource utilization؛ redaction کامل tokens، مختصات و محتوای شخصی.
5. آزمون رخدادهای SOS/اعلان فقط با sink جعلی/غیرفعال‌شده در محیط کاملاً جدا و بدون ارسال بیرونی؛ تا تأیید isolation این آزمون اجرا نشود.

ظرفیت عددی اعلام نمی‌شود تا این آزمون در staging انجام و چند بار تکرار شود.
