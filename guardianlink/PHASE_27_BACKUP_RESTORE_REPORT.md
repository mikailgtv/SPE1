# گزارش Backup/Restore — Phase 27

**وضعیت:** `CLOSED` فقط برای آزمون دستی local PostgreSQL با دادهٔ synthetic؛ `BLOCKED` برای cloud/production DR.

## آزمون اجراشده

1. پس از توقف Backend، از DB محلیِ فقط synthetic با `pg_dump --format=custom --no-owner --no-acl` archive موقت ساخته شد.
2. Archive به یک DB محلیِ خالی و جدا با `pg_restore --no-owner --no-acl` بازیابی شد.
3. شمار ردیف تمام ۴۳ جدول public بین مبدا و مقصد مقایسه شد؛ هر دو برابر بودند. هر دو `flyway_schema_history` را در version 9 داشتند.
4. Archive: 116,943 بایت؛ SHA-256: `ddd5f2509f509361659536d6a4631a4f0317128626d36643dfccc330f06c1028`.
5. پس از ثبت نتیجه، archive، restore DB و DB/roleهای fixture پاک شدند.

## محدوده

این آزمون restore محلی را با row-count و migration-version verify کرد؛ app-after-restore readiness، content hash همهٔ جدول‌ها، encryption/key recovery، offsite replication، S3/KMS، immutability، retention، PITR و اندازه‌گیری RPO/RTO اجرا نشد. checksum صرفاً هویت همان archive موقت را ثبت می‌کند و تضمین محرمانگی/ماندگاری نیست. هیچ backup یا restore production انجام نشد.
