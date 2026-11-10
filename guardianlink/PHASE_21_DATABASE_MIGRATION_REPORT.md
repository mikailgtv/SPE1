# Phase 21 — گزارش مهاجرت پایگاه‌داده

**نتیجه:** migration source موجود ولی apply/upgrade/rollback واقعاً اجرا نشد (`BLOCKED`). هیچ database یا migration schema/data دستکاری نشده است.

## شواهد ایستا

- مسیر migration source طبق `Db.kt`: `classpath:db/migration`; Flyway در `Db.migrate()` اجرا می‌شود.
- Phase20 pre-change report inventory: 9 SQL migration، V1 تا V9، 42 جدول؛ Phase21 این شمارش را از روی گزارش قبلی نقل می‌کند و ادعای live schema audit ندارد.
- منبع Phase13 ثبت کرده `db.migrate()` در startup انجام می‌شود و migration/runtime credential separation حل/تأیید نشده؛ به‌عنوان گیت باز حفظ شد.
- PostgreSQL/psql/pg_dump/pg_restore، Docker و Gradle غایب؛ backend compilation هم ممکن نیست.

## Migration matrix

| ID | Check | Status | توضیح/گیت |
|---|---|---|---|
| MIG-01 | migration files presence and naming | `IMPLEMENTED_NOT_TESTED` | source موجود؛ inventory پیشین V1–V9 |
| MIG-02 | clean database migration | `BLOCKED` | no PostgreSQL instance |
| MIG-03 | N-1 → latest upgrade | `BLOCKED` | no pre-upgrade DB snapshot/test fixture |
| MIG-04 | rollback/forward recovery | `NOT TESTED` | rollback plan/script not executed; no production rollback assertion |
| MIG-05 | DDL/runtime credential boundary | `PARTIALLY_IMPLEMENTED` | source report نشان‌دهندهٔ startup migration؛ privileged split اثبات نشده |
| MIG-06 | RLS/policies/tenant isolation | `NOT_IMPLEMENTED` per open H-01 evidence | migration review + PostgreSQL role tests required |
| MIG-07 | indexes/FK/cascade/data integrity | `NOT TESTED` | no PG catalog/data assertions |
| MIG-08 | backup before migration and restore verification | `BLOCKED` | no backup, target DB, tools or storage |

## Test register

| Test ID | Environment/command/timestamp | Expected | Actual/result | Artifact |
|---|---|---|---|---|
| MIG-STATIC-01 | `server/src/main/kotlin/.../Db.kt` + prior Phase20 inventory; source review, 2026-10-05 | identify migration mechanism, avoid overstating | Flyway source path; no apply test; `PASS` for static review only | this report |
| MIG-SQL-02 | disposable PostgreSQL, `NOT RUN` | apply all migrations cleanly | PG/Docker unavailable; `BLOCKED` | none |
| MIG-UPGRADE-03 | supported prior schema in disposable DB, `NOT RUN` | clean upgrade and constraints verified | no DB fixture; `BLOCKED` | none |
| MIG-ROLLBACK-04 | isolated environment, `NOT RUN` | documented safe recovery | no rollback drill; `NOT TESTED` | none |

## Required gate

Disposable supported PostgreSQL versions, baseline and N-1 data fixtures; record Flyway history, DDL privileges, row counts/constraints/indexes, concurrent transactions, migration duration; verify backup/restore before destructive changes. Production migrations remain prohibited until reviewed, reversible/forward-recoverable and tested. No schema change was made during Phase21.
