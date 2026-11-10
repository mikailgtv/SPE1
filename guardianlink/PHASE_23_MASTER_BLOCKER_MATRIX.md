# ماتریس جامع موانع Phase 23

**تاریخ:** 2026-10-06 (UTC) · **وضعیت:** `PARTIALLY_VALIDATED` · **محیط:** Debian 13، PostgreSQL 17.11، backend/Ktor محلی، Web/Vite. هیچ production deploy انجام نشد.

| ID | مانع/حوزه | شواهد و نتیجه | وضعیت | اقدام بعدی |
|---|---|---|---|---|
| P23-B01 | Jackson/زمان | Register و create family واقعی HTTP 201؛ timestamp ISO-8601 string | بسته | نگهداری dependency/config |
| P23-B02 | Device session mapping | خطای واقعی `user_id` در `device_sessions`; trace به `ReposKt.sessionRow`; mapper مجزای `deviceSessionRow()` با `device_id`; installDist build شد | بسته/flow regression | افزودن تست DB در CI |
| P23-B03 | Device status | پس از B02، status HTTP 200 و parent device read 200 در Vite proxy E2E | بسته در محیط تست | CI integration |
| P23-B04 | Location status upsert | trace به `LocationRepo.upsertStatus`: هفت bind موردنیاز/فقط شش bind و جای‌گذاری نامتناسب `last_report_ts`/`health_state`; SQL اصلاح؛ E2E location/geofence پس از آن HTTP 200 و state assertions پاس | بسته در تست | تست insert/update به CI |
| P23-B05 | API/security/E2E | `/tmp/gl_phase23_api_flow.py`: 54/54 پاسخ مورد انتظار از طریق Vite proxy؛ flows و assertions واقعی در `PHASE_23_E2E_REPORT.md` | PASS با قید | body limit از شمار PASS خارج شود |
| P23-B06 | Migration/runtime | DB آزمایشی تازه: 9 migration از ابتدا، backend start؛ `/health/live` 200، `/health/ready` 200 | PASS | pipeline staging تکرار کند |
| P23-B07 | Backup/restore | `pg_dump -Fc` و restore DB مستقل؛ 43 جدول، Flyway v9، مجموع دقیق 186 ردیف مبدأ و مقصد | PASS | اتوماسیون/retention/ offsite test |
| P23-B08 | Android build | AGP 9.4.1 حداقل Gradle 9.6 را خواست؛ Gradle 8.14.3 ناکافی؛ Gradle 9.7.1 assembleDebug نهایی daemon timeout/disappeared. SDK 36 / cmdline-tools موجود نبود | BLOCKED | Android SDK cmdline-tools + platform 36/build-tools; Gradle 9.7.1؛ runner با منابع بیشتر؛ rebuild دو app |
| P23-B09 | Android runtime | `adb`, `sdkmanager`, `emulator` و SDK پیدا نشد؛ دستگاه/managed enforcement اجرا نشد | BLOCKED | دستگاه واقعی/emulator API 26+ و policy test |
| P23-B10 | Backend automated tests | 141 تست کامل؛ 122 failure ناشی از Testcontainers/DockerClientProvider؛ 19 تست واحد/security pass. `docker`/`podman` غایب | BLOCKED_BY_ENVIRONMENT برای suite کامل | Docker Engine/daemon/socket و اجرای مجدد |
| P23-B11 | سقف بدنه HTTP | payload آزمایشی 1 MiB در login پاسخ 401 داد، نه 413؛ سقف 1 MiB اثبات نشد؛ این probe PASS امنیتی نیست | GAP / NOT VERIFIED | تعیین حد در server/proxy و boundary test با auth معتبر |
| P23-B12 | Export/delete | export probe 404؛ DELETE family 405؛ هیچ داده‌ای حذف نشد | API capability موجود نیست | دامنه و طرح امن/retention را بازبینی کنید؛ فعلاً قابلیت ادعا نشود |
| P23-B13 | Web/browser/push | `npm run build` موفق؛ API واقعی از Vite proxy؛ automation مرورگر و دستگاه Android غایب؛ ارسال push بیرونی تست نشد | PARTIAL/BLOCKED | browser test runner و device; provider staging |
| P23-B14 | Production/compliance | دسترسی production/cloud و approval وجود نداشت؛ deployment اجرا نشد | NOT TESTED | تمام release gateها و jurisdiction-specific review |
| P23-B15 | Error observability | برای تعدادی خطای HTTP واقعی 401/429، log تکمیل درخواست بعضاً status=500 گزارش کرد | FOLLOW-UP | اصلاح logger و مقایسهٔ status واقعی/ثبت‌شده |

## معیار نهایی
تا رفع/پذیرش رسمی B08–B15 و اجرای CI/device/security gate، وضعیت فقط `PARTIALLY_VALIDATED` است؛ Production deployment مجاز نیست.

## پیوند شواهد
`PHASE_23_E2E_REPORT.md`, `PHASE_23_DATABASE_REPORT.md`, `PHASE_23_BACKUP_RESTORE_REPORT.md`, `PHASE_23_SECURITY_REGRESSION_REPORT.md`, `PHASE_23_ANDROID_BUILD_REPORT.md`, `PHASE_23_ANDROID_RUNTIME_REPORT.md`, `PHASE_23_DATA_EXPORT_DELETE_REPORT.md`, `phase23-artifacts/`.