# گزارش Phase 18 — Recovery Test و Incident Timeline

## نتیجهٔ واقعی: **BLOCKED_BY_ENVIRONMENT**

سناریوی `Failure → Detect → Alert → Recover → Verify` اجرا نشد. Backend/DB در دسترس نبود، Docker/Podman و JDK17/Gradle نبود، هیچ alert receiver/operator stack provision نشده و credential staging موجود نیست. Failure عمداً trigger نشد تا از دادهٔ واقعی یا سرویس مشترک آسیب نبیند.

## Timeline

| مرحله | زمان واقعی | وضعیت |
|---|---:|---|
| T0 خرابی واقعی آغاز شد | — | اجرا نشد؛ incident مصنوعی ساخته نشد |
| T1 Detection | — | endpoint/metrics فقط در source تغییر کرده؛ process بالا نیامد |
| T2 Alert generated | — | alert system وجود/provision ندارد |
| T3 Operator investigation | — | operator channel/console موجود نیست |
| T4 Fix applied | — | هیچ runtime incident fix ثبت نشده |
| T5 Recovery | — | اجرا نشد |
| T6 Verification/retest | — | اجرا نشد |

هیچ زمان ساختگی یا تست pass ادعا نمی‌شود.

## موانع اجراشده
- `gradle test` در `server/`: **BLOCKED** — `gradle: command not found`; پروژه JDK 17 می‌خواهد، محیط Java 11 دارد.
- `./gradlew ...` در `android/`: **BLOCKED** — Gradle wrapper موجود نیست.
- `docker compose ... config`: **BLOCKED** — Docker CLI موجود نیست.
- Android SDK/adb، PostgreSQL client و browser executable در PATH نبودند.
- Web checkها جداگانه گذشتند، اما جایگزین recovery chain نیستند.

## دستورهای لازم برای اپراتور پس از فراهم‌کردن محیط

1. JDK 17 و Gradle 9.7.1 مطابق `server/Dockerfile` فراهم کنید و wrapper سازگار را در repo commit کنید؛ سپس `cd server && ./gradlew test` (یا gradle wrapper pin شده) اجرا شود. Integration tests به Docker/Testcontainers نیاز دارند.
2. با credential staging غیرواقعی و `.env` خارج از repo: `docker compose -f deploy/docker-compose.yml up --build -d db server`؛ از private network `/health/live`, `/health/ready`, و `/metrics` را scrape کنید. `/metrics` را از public edge proxy نکنید.
3. Collector operator-owned با retention/access policy نصب کنید؛ alert rule/API SLO و dedup/cooldown را بعد از baseline load test configure کنید. ابتدا failure در staging را با توقف DB آزمایش کنید، نه production.
4. Recovery: DB را در staging برگردانید، readiness/status و API smoke را بررسی کنید، در صورت استفاده backup isolated restore runbook را طبق `BACKUP_RESTORE_GUIDE.md` اجرا کنید؛ backup success را restore success تلقی نکنید.
5. Android: build نصب روی emulator/device؛ network/Doze/reboot/worker failure را تنها روی staging test family/user اجرا و WorkManager output/last-sync را verify کنید. Crash SDK فعلاً تصویب نشده؛ هیچ crash event ثالثی نباید فعال شود.
6. Web: browser با staging account و API بررسی شود؛ React/runtime unhandled errors، sign-in expiry، offline, API 4xx/5xx را verify کنید؛ request body/token را در capture نگذارید.

**Conclusion:** Recovery و alert verification **BLOCKED**؛ هیچ runtime recovery یا incident واقعی گزارش نشده است.