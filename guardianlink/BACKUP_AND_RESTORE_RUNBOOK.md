# Runbook عملیاتی Backup/Restore — پیشنهادی، اجرا/تأییدنشده

**وضعیت:** Runbook بر اساس scriptهای موجود تهیه شده؛ schedule، cloud account، keys، restore target و alerting در workspace نیستند. تا آماده‌سازی و آزمایش، این runbook مجوز اجرای production محسوب نمی‌شود.

## پیش‌نیازهای backup

- PostgreSQL endpoint خصوصی؛ role کم‌اختیار و credential از secret manager یا PGPASSFILE با mode 0600؛ `pg_dump` compatible.
- نصب/نسخه pin شدهٔ `age` و AWS CLI؛ `AGE_RECIPIENT` عمومی؛ AWS task role حداقل دسترسی فقط به prefix هدف؛ `S3_BACKUP_URI`, `S3_KMS_KEY_ARN`, `AWS_REGION`.
- `BACKUP_DIR` روی storage رمز‌شده/محدود و ترجیحاً موقت؛ off-host copy mandatory. اطمینان از public access block، TLS-only policy، versioning/lifecycle/Object Lock طبق owner policy.
- health/alert برای failed run، last-success age، free capacity، bytes/duration و KMS errors؛ این بخش در حال حاضر پیاده‌سازی نشده.
- کلید private Ed25519 `DATA_DIR/ed25519.jwk` را جداگانه در key-management system backup/restore کنید؛ DB dump آن را شامل نمی‌شود. هرگز private identity را کنار artifact قرار ندهید.

## Backup (تست‌نشده)

1. staging/production scope، DB و window را تأیید و ثبت کنید؛ DB restore/backup credentials را هرگز inline نکنید.
2. از environment secret manager vars لازم را inject کنید؛ ابتدا در staging آزمایش شود.
3. فرمان source موجود: `bash deploy/scripts/backup-postgres.sh`.
4. exit status nonzero، zero-size file یا missing sidecar را failure تلقی کنید؛ alert/incident ثبت کنید. فعلاً script خودکار alert نمی‌فرستد.
5. SHA-256 verify: `sha256sum -c <backup-file>.sha256`؛ verification باید روی artifact دانلودشده هم انجام شود.
6. decrypt/restore test را طبق بخش بعد به DB جدید isolated انجام دهید؛ `pg_restore --list` به تنهایی full recovery proof نیست.
7. artifact ID, start/end UTC, size, checksum, destination, key version, DB version و نتیجه را ثبت کنید؛ هیچ row/token/location/log حساس در evidence نگذارید.
8. مطمئن شوید off-host upload موفق است و remote lifecycle واقعاً فعال است؛ local retention defaults در script به S3 خودکار اعمال نمی‌شود.

## Restore (تست‌نشده)

1. incident commander تأیید کند target account/VPC/DB تازه، isolated و nonproduction است؛ URL/account را مستقل verify کنید. env flag script صرفاً confirmation operator است.
2. encrypted backup + `.sha256` را از private off-host storage دریافت و `sha256sum -c` اجرا کنید.
3. age private identity را از secret manager باز کنید؛ permission 0600؛ هرگز چاپ یا commit نکنید. KMS/age key IDs را ثبت کنید نه secret.
4. DB خالی با PostgreSQL سازگار بسازید و محدودیت شبکه را اعمال کنید.
5. `RESTORE_TARGET_ENV=restore-test`, `RESTORE_TARGET_CONFIRM=VERIFIED_ISOLATED_NONPRODUCTION_TARGET` و PG* credential امن را set کنید؛ سپس `bash deploy/scripts/restore-postgres.sh`.
6. migration state، schema/tables/indexes/constraints/FKs، orphan checks و synthetic row counts را verify کنید.
7. backend non-production را با config/jwk مناسب بالا آورید؛ synthetic login/refresh, Parent A own-family access, cross-family denied, Child/device isolation, location/SOS/notification sample integrity را بررسی کنید.
8. record زمان‌ها، effective data point آخر backup (RPO)، elapsed restore (RTO)، tests/artifacts، blockers و approver را در restore test report ثبت کنید.
9. قبل از حذف restore DB، محیط را دوباره verify؛ fixtureها و storage test artifacts را به روش امن حذف کنید.

## Failure handling

Backup failure: backup اخیر را حذف/overwrite نکنید؛ alert، disk space/network/KMS status و source DB health را بررسی کنید؛ retry محدود با incident owner. Restore failure: source artifact/DB را تغییر ندهید؛ target را از isolated network خارج نگه دارید، logs را scrub کنید، backup دیگر را انتخاب و defect را ثبت کنید. به production cutover بدون full integrity+application checks اقدام نکنید.

## وضعیت امروز

هیچ backup/restore واقعی اجرا نشده؛ `pg_dump`, `pg_restore`, `age`, AWS CLI/credentials, DB, S3/KMS و alerting در محیط Phase 20 موجود نیستند. RPO/RTO: `NOT DEFINED`.
