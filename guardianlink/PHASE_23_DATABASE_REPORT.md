# گزارش Database Phase 23

**وضعیت:** `PARTIALLY_VALIDATED` (migration و restore تست‌شده؛ performance/production نه).

## محیط و نتایج
PostgreSQL 17.11 روی Debian 13. Database مبدأ `guardianlink_phase23` یک دیتابیس آزمایشی محلی است؛ clone restore برابر `guardianlink_phase23_restore`; DB تازه `guardianlink_phase23_fresh`. هیچ production database لمس نشد.

| ID | تست | محیط/دستور | زمان UTC | انتظار | نتیجه واقعی | وضعیت | Artifact |
|---|---|---|---|---|---|---|---|
| P23-DB-01 | migration موجود | backend startup با DB آزمایشی؛ Flyway validate | 2026-10-06 07:31 و 07:50Z logs | schema معتبر، migration جاری | 9 migrations validated، schema v9، no migration required | PASS | backend logs |
| P23-DB-02 | migrate خالی | startup `guardianlink_phase23_fresh` | 2026-10-06 07:50:05.59–07:50:07.38Z | اجرای همهٔ migrationها | 9 از 9 اعمال، v9؛ backend شروع شد | PASS | `PHASE_23_BACKEND_RUNTIME_REPORT.md` |
| P23-DB-03 | سلامت وابستگی DB | `GET /health/ready` | 2026-10-06 07:50:14Z | readiness 200 | HTTP 200 `ready` | PASS | Backend runtime report |
| P23-DB-04 | row-count restore compare | `pg_dump -Fc`; `pg_restore` به دیتابیس مستقل؛ شمارش دقیق جدول‌های public | ابزار 1.263s؛ زمان absolute ثبت نشده | schema/data برابر | هر دو: 43 جدول، Flyway v9، مجموع 186 ردیف | PASS؛ timestamp metadata ناقص | `PHASE_23_BACKUP_RESTORE_REPORT.md` |
| P23-DB-05 | runtime role + status | API flow واقعی | 07:31:17.795–07:31:21.677Z | status/session دسترسی سالم | status 200، و داده‌های usage/location persisted/readable در flow | PASS | E2E report |

## محدودیت/خطر
- هیچ benchmark، concurrency/load، failover، WAL/PITR، encryption-at-rest verification یا production permission audit در این اجرا انجام نشد (`NOT TESTED`).
- Restore clone را به‌عنوان evidence نگه داشتیم؛ dump داخل `/tmp/phase23-backups/guardianlink_phase23_20261006.dump` بوده و بیرون workspace است؛ hash و خلاصه در backup report. Dump شامل فقط دیتابیس آزمایشی است، اما خود فایل را در deliverable عمومی نگهداری نمی‌کنیم.
- Cloneهای آزمایشی ایجادشده: `guardianlink_phase23_restore`, `guardianlink_phase23_fresh`; هیچ حذف/تغییر production صورت نگرفت.
