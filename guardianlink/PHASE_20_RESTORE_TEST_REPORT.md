# گزارش آزمون Restore — فاز ۲۰

## حکم

**Full restore drill: `BLOCKED BY ENVIRONMENT`.** هیچ backup واقعی تولید/دانلود/decrypt/restore نشده و هیچ test database یا API smoke پس از restore اجرا نشده است. فایل موجود `BACKUP_RESTORE_TEST.md` نیز سابقهٔ قبلی `NOT RUN / BLOCKED` دارد.

## محیط و ابزار

در workspace نبودند: PostgreSQL server، `psql`, `pg_dump`, `pg_restore`, Docker/Podman، `age`, AWS CLI/credentials، S3/KMS، source/target DB و backup file. No staging/restore-test account. Private signing key یا DB credentials در اختیار نیستند. لذا هیچ دادهٔ واقعی/مصنوعی DB موجود نبود.

## شواهد دقیق

| Test ID | Environment | Command / timestamp | Expected | Actual | Result | Artifact |
|---|---|---|---|---|---|---|
| RESTORE-PARSE-001 | local sandbox `/home/user/guardianlink` | `bash -n deploy/scripts/backup-postgres.sh deploy/scripts/restore-postgres.sh`; 2026-10-05 (UTC) | هر دو bash script بدون syntax error parse شوند | `bash -n PASS` | `PASS` (syntax-only) | console output؛ هیچ DB action انجام نشد |
| RESTORE-DB-001 | PostgreSQL restore-test | `NOT RUN` | Backup به DB خالی و isolated restore شود | target/backup/tool موجود نیست | `BLOCKED` | هیچ |
| RESTORE-SCHEMA-001 | restore-test DB | `NOT RUN` | tables/indexes/constraints/migration state صحیح | query اجرا نشد | `BLOCKED` | هیچ |
| RESTORE-DATA-001 | restore-test DB | `NOT RUN` | synthetic known rows در identity/location/SOS/notification intact | query اجرا نشد | `BLOCKED` | هیچ |
| RESTORE-APP-001 | restored DB + backend | `NOT RUN` | backend starts؛ login/family-isolation API checks | JDK17/Gradle/backend/DB unavailable | `BLOCKED` | هیچ |
| RESTORE-CRYPTO-001 | isolated storage | `NOT RUN` | checksum verify/decrypt/KMS key accessible؛ JWK recovery | backup, keys, cloud absent | `BLOCKED` | هیچ |

`bash -n` فقط shell syntax را validate می‌کند. آن را به عنوان backup test، encryption verification یا restore موفق تلقی نکنید.

## Restore drill لازم (هنوز اجرا نشده)

1. ایجاد DB آزمایشی isolated با PostgreSQL target version و migration history معلوم.
2. بارگذاری fixtureهای synthetic معلوم: parent/family/device، usage، location/geofence، notification، SOS، sessions hashes.
3. اجرای backup script با age public recipient و S3/KMS sandbox credentials؛ ثبت artifact ID/bytes/time/checksum بدون data.
4. `sha256sum -c`, `age --decrypt` به pipe parse/restore check و `pg_restore --list`; secret identity خارج artifact.
5. snapshot isolated test را drop کردن؛ ایجاد DB خالی جدید؛ اجرای restore script در `RESTORE_TARGET_ENV=restore-test` و confirmation واقعی endpoint.
6. بررسی Flyway schema/migration, tables, indexes, FK/check constraints, row counts و orphan checks.
7. Backend startup، synthetic login, Parent A→own family, Parent A→Family B deny, device-bound reads, location/SOS/notification integrity.
8. ثبت زمان backup/restore/API verification و recovery issues، سپس fixture/DB deletion با تأیید.

هیچ گامی جز shell syntax اجرا نشد. RPO/RTO اندازه‌گیری نشده و `NOT DEFINED` باقی می‌ماند.
