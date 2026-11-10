# گزارش محیط اجرا — Phase 27

**تاریخ:** ۸ اکتبر ۲۰۲۶  
**دامنه:** sandbox محلی و fixtureهای ساختگی؛ نه staging و نه production.

## نتیجه

| مؤلفه | نتیجه | وضعیت |
|---|---|---|
| سیستم‌عامل و منابع | Debian 13.7، معماری x86_64، ۲ vCPU، RAM حدود ۱٫۹ GiB؛ فضای آزاد workspace حدود ۱۹ GiB | `CLOSED` برای ثبت وضعیت |
| JVM/Backend | JDK 17 Temurin در `/tmp/phase27-tools/jdk17`؛ Gradle Wrapper 9.7.1 | `CLOSED` برای build محلی |
| Web | Node 20.20.2، npm 10.8.2 | `CLOSED` |
| پایگاه‌داده | PostgreSQL 17.11، `psql` و `pg_dump/pg_restore`؛ DB مجزای `guardianlink_phase27` | `CLOSED` برای runtime محلی؛ پس از آزمون پاک شد |
| Android | ADB 34.0.5 موجود؛ SDK platform/build-tools 36، `sdkmanager`، emulator و مجوزهای SDK در دسترس نبودند؛ `/dev/kvm` غایب | `BLOCKED` |
| Docker/Testcontainers | daemon/socket قابل استفاده نبود؛ image/container/volume محلی فراهم نبود | `BLOCKED` |
| Git provenance | پوشهٔ `.git` در snapshot وجود ندارد؛ branch، commit و diff قابل تأیید نیست | `BLOCKED` |

## شواهد و محدودیت

- Backend با JDK 17 و PostgreSQL محلی اجرا شد؛ `/health/live` و `/health/ready` هر دو HTTP 200 دادند.
- Flyway از schema خالی، ۹ migration را اعمال کرد. Web dev server با proxy سمت سرور به Backend محلی اجرا و با Chromium headless آزموده شد.
- برای Android SDK، پذیرش شرایط مجوز به‌جای کاربر انجام نشد. برای رفع blocker، کاربر/اپراتور باید SDK رسمی Platform 36 و Build Tools 36.0.0 را نصب و مجوزها را شخصاً بررسی/قبول کند؛ سپس build parent/child و آزمون دستگاه اجرا شود.
- Docker ACL، گروه کاربر یا دسترسی socket تغییر داده نشد. برای Testcontainers، محیط مجاز با Docker Engine/socket لازم است.
- secret scan محدود به الگوهای کلید خصوصی، AWS access key، GitHub token و Bearer literal بلند روی فایل‌های متنی workspace (به‌جز cache/build/dependency) بود؛ هر چهار دسته صفر مورد داشت. این نتیجه جایگزین provenance از Git یا اسکن CI اختصاصی نیست.
- هیچ secret واقعی در `.env.example` قرار نگرفت. credentialهای موقت و JWK محلی پس از پایان آزمون پاک‌سازی شدند.

## وضعیت نهایی محیط

محیط برای Backend، PostgreSQL، Web و API-only synthetic integration فراهم بود؛ برای Android واقعی، Testcontainers و device E2E کامل نبود. این محدودیت‌ها مانع release approval هستند.
