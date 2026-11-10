# گزارش محیط Phase 23

**نتیجه:** `PARTIALLY_VALIDATED` · **تاریخ:** 2026-10-06 UTC

## مشاهدات محیط
- سیستم: Debian GNU/Linux 13 (trixie).
- JVM پیش‌فرض shell: Java 11؛ برای backend build صریحاً JDK 17.0.20.1+1 انتخاب شد.
- Gradle نصب‌شده: 9.7.1 و 8.14.3. پروژه Android روی AGP 9.4.1 است و طبق خطای build حداقل Gradle 9.6 می‌خواهد.
- PostgreSQL 17.11 محلی، test DB `guardianlink_phase23`; fresh DB جداگانه `guardianlink_phase23_fresh` برای migration.
- Node v20.20.2، npm 10.8.2، Vite 7.3.6.
- `ANDROID_HOME`/`ANDROID_SDK_ROOT` unset؛ `adb`, `sdkmanager`, `emulator` در PATH و مسیرهای بررسی‌شده یافت نشدند.
- `docker` و `podman` در PATH یافت نشدند؛ Docker daemon دسترس‌پذیر نبود.
- Web dev server روی `0.0.0.0:5173` و PostgreSQL روی 5432 در حال اجرا بودند. API proxy به backend محلی متصل شد.
- محدودیت حافظهٔ اجرا کم بود؛ Gradle Android daemon در تلاش نهایی ناپدید/timeout شد. علت دقیق از daemon log نهایی اثبات نشده، پس به کمبود حافظه نسبت داده نمی‌شود.

## Test record
| ID | محیط | دستور/اقدام | آغاز–پایان UTC | انتظار | نتیجه واقعی | وضعیت | Artifact |
|---|---|---|---|---|---|---|---|
| P23-ENV-01 | workspace Debian | `java -version`, `node --version`, `npm --version`, `psql --version`, tool discovery | 2026-10-06 07:51:01Z (snapshot) | toolchain و dependencyها شناسایی شوند | Debian 13, Java shell 11, Node 20.20.2, npm 10.8.2, PostgreSQL 17.11؛ SDK/Docker غایب | PASS (inventory) | این گزارش |
| P23-ENV-02 | Android Gradle | `gradle --offline ... :app-parent:assembleDebug :app-child:assembleDebug` | اجرای آخر 2026-10-06؛ فرمان 180s timeout شد؛ پایان دقیق wrapper ثبت نشد | assemble هر دو app | بدون خروجی نهایی؛ Gradle task timeout شد | BLOCKED/INCONCLUSIVE | `PHASE_23_ANDROID_BUILD_REPORT.md` |
| P23-ENV-03 | PostgreSQL test | اجرای restore clone | timestamp از wrapper جا افتاد؛ مدت ابزار 1.263s | restore سالم و داده برابر | 43 جدول، v9، 186 ردیف در هر دو DB | PASS؛ timestamp نقص دارد | `PHASE_23_BACKUP_RESTORE_REPORT.md` |

## راه رفع blocker محیط
1. نصب Android SDK Command-line Tools رسمی، پذیرش licenses، نصب `platforms;android-36`, `build-tools` همخوان و `platform-tools`; export `ANDROID_HOME`/`ANDROID_SDK_ROOT`.
2. استفاده از Gradle >=9.6 (موجود 9.7.1) و JDK موردنیاز AGP؛ runner با heap/memory بیشتر. پروژه باید Gradle Wrapper قابل‌تکرار داشته باشد.
3. نصب Docker Engine + daemon/socket برای Testcontainers؛ اجرای تست‌ها با Docker доступно.
4. افزودن browser automation + Android emulator/device. هیچ install/deploy خارج از workspace در این اجرا انجام نشد.
