# گزارش E2E و API Integration — Phase 27

**وضعیت:** `CLOSED` برای HTTP integration محلیِ API-only؛ `BLOCKED` برای Android physical E2E/Testcontainers.

## دامنهٔ runtime واقعی

- Backend/Ktor و PostgreSQL/Flyway اجراشده بودند؛ دو synthetic parent account و دو family ساخته شد.
- دو child-device record از pairing session → exchange → confirm و device-auth واقعی backend ایجاد شد، اما client صرفاً REST test client بود؛ هیچ Android یا device فیزیکی وجود نداشت.
- GETهای family/device/usage/screen-time/location/geofence/app-rules/web-safety/events/notifications/SOS اجرا شدند. دو zone configuration و دو Web rule synthetic ساخته و خوانده شدند.
- App inventory واقعی وجود نداشت و ساخته نشد؛ GET خالی بررسی شد و rule برای app نصب‌نشده رد شد.
- دو SOS backend session بدون location payload فعال، acknowledge و resolve شدند؛ internal `EMERGENCY` notification projection آزموده شد.
- ۲۶ cross-family probe عدم دسترسی را با 404/405 تأیید کرد. مجموع ۱۰۴ assertion از اسکریپت runtime پاس شد.
- Chromium 154 headless/ChromeDriver مرورگر واقعی را به Web محلی وصل کرد؛ login و مشاهدهٔ نام family/device از API موفق شد.

## آنچه E2E محسوب نمی‌شود

این آزمون real HTTP/API+DB integration است، ولی Android client، user واقعی، FCM، GPS، transition geofence، network loss/reconnect از device و production transport را پوشش نمی‌دهد. Full Gradle suite نیز به‌علت Docker/Testcontainers startup کامل نشد (۱۲۲ شکست ناشی از نبود Docker؛ ۲۰ unit test مستقل پاس).
