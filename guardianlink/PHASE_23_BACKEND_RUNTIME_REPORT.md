# گزارش اجرای Backend Phase 23

**وضعیت:** `PARTIALLY_VALIDATED` · اجرای local/test فقط؛ production deploy انجام نشد.

## اصلاح‌های انجام‌شده
1. Jackson به `JavaTimeModule` مجهز است و `WRITE_DATES_AS_TIMESTAMPS` خاموش؛ ثبت/ساخت خانواده واقعاً HTTP 201 داد و timestamp رشتهٔ ISO-8601 شد.
2. خطای status با PSQLException به mapping نشست دستگاه رسید. `DeviceSessionRepo` از `sessionRow()` نشست کاربر استفاده می‌کرد، در نتیجه برای `device_sessions` ستون ناموجود `user_id` خوانده می‌شد. mapper جداگانهٔ `deviceSessionRow()` اکنون `device_id` می‌خواند.
3. Trace خطای location به `LocationRepo.upsertStatus` رسید: bind list ناقص بود؛ `last_report_ts` و `health_state` نیز bindهای هم‌تراز نداشتند. `VALUES` اکنون `CASE WHEN ? THEN now() ELSE NULL END` و bind متناظر دارد؛ تعداد/ترتیب پارامترها با ON CONFLICT هم‌تراز شد.
4. traceهای موقت `errorSite`/`errorCaller` بعد از diagnosis حذف شدند؛ فقط diagnostic `errorFrame` امنِ قبلی باقی است.

## Test records
| ID | محیط | دستور | زمان UTC | انتظار | نتیجه واقعی | وضعیت | Artifact |
|---|---|---|---|---|---|---|---|
| P23-BE-01 | backend/JDK17, Gradle 9.7.1, PG test | `gradle --offline --no-daemon --max-workers=1 ... installDist` | اجرای نهایی 2026-10-06 08:11:54–08:12:04Z | build نصب‌شونده موفق | `BUILD SUCCESSFUL`; final source up-to-date | PASS | `phase23-artifacts/backend-build.log`, `backend-build-meta.txt` |
| P23-BE-02 | DB تازه v0 | startup backend با `DB_URL=...guardianlink_phase23_fresh` | 2026-10-06 07:50:05.591–07:50:07.384Z | migration و startup موفق | Flyway 9 migration، listening 0.0.0.0:8080 | PASS | log startup در ابزار؛ خلاصه در `PHASE_23_DATABASE_REPORT.md` |
| P23-BE-03 | fresh DB/local HTTP | `GET /health/live`; `GET /health/ready` | 2026-10-06 07:50:14Z | 200 live/ready | هر دو 200 | PASS | این گزارش |
| P23-BE-04 | API proxy+backend+PG | `python3 /tmp/gl_phase23_api_flow.py` | 2026-10-06 07:31:17.795–07:31:21.677Z | flow واقعی و assertions | 54/54 پاسخ مورد انتظار؛ details در E2E report | PASS با قید body-size | `PHASE_23_E2E_REPORT.md` |
| P23-BE-05 | backend tests | `gradle ... test` | 2026-10-06 07:51:58–07:52:10Z | suite کامل PASS | 141 complete; 122 fail در Testcontainers چون Docker provider unavailable؛ 19 unit tests PASS | BLOCKED_BY_ENVIRONMENT | `server/build/reports/tests/test/index.html` (generated, may not persist); test report file |

## باقیمانده
- API errors در بعضی موارد در client به 401/429 رسیدند، درحالی‌که بعضی logهای completion/exception 500 نشان می‌دادند؛ logging lifecycle باید اصلاح و با status واقعی correlation شود.
- تست‌های repository/route متکی به Testcontainers بدون Docker کامل نشدند.
- build موفق به‌تنهایی release evidence نیست؛ Android و production gates همچنان بازند.
