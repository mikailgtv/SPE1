# راهنمای Backup و Restore

**حالت فعلی:** فقط script/روش پیشنهادی ایجاد شده؛ backup واقعی، S3 bucket، retention lifecycle و restore test اجرا نشده‌اند. هیچ فایل backup واقعی تولید نشده است.

## سیاست پیشنهادی (نیازمند تصویب owner/legal)

- Daily logical PostgreSQL backup؛ روزانه ۱۴ روز، نسخهٔ هفتگی ۸ هفته، ماهانه ۱۲ ماه.
- RPO فعلی: `NOT DEFINED`. در نسخهٔ پیشین راهنما عدد ۲۴ ساعت به‌عنوان مثال/پیشنهاد آمده بود؛ چون schedule واقعی، backup artifact، WAL/PITR و restore drill تأیید نشده‌اند، آن عدد RPO مصوب یا عملیاتی نیست.
- RTO فعلی: `NOT DEFINED`; باید محصول/عملیات بر پایهٔ هزینه/اثر کسب‌وکار تعیین کند و سپس با restore drill واقعی اندازه‌گیری شود.
- کپی محلی به‌تنهایی کافی نیست: encrypted off-host S3، private bucket، public access block، versioning، KMS، TLS-only bucket policy، least-privilege IAM و lifecycle/Object Lock جداگانه لازم است.
- `pg_dump` قبل از ذخیره با `age` رمز می‌شود و در S3 نیز SSE-KMS دارد. `AGE_RECIPIENT` عمومی است؛ `AGE_IDENTITY_FILE` خصوصی باید جدا/offline نگهداری و recovery آن تمرین شود.

## Script موجود

`deploy/scripts/backup-postgres.sh`:
- `pg_dump -Fc` را مستقیم به `age` stream می‌کند؛ dump plaintext روی disk ساخته نمی‌شود.
- فایل محلی `umask 077`، directory mode 0700 و backup mode 0600 دارد.
- checksum sidecar می‌سازد و encrypted copy + checksum را به S3 می‌فرستد.
- daily/weekly/monthly local copies را prune می‌کند. S3 retention با bucket lifecycle/Object Lock provision نشده؛ script remote deletion انجام نمی‌دهد.

Script نیاز دارد `pg_dump`, `age`, AWS CLI، `PGHOST`, `PGDATABASE`, `PGUSER`, امن‌ترین راه PGPASSFILE، `AGE_RECIPIENT`, `BACKUP_DIR`, `S3_BACKUP_URI`, `S3_KMS_KEY_ARN`, `AWS_REGION` و IAM task/user role آماده باشند. Secret/password را در command-line یا log قرار ندهید.

**نکته:** اسکریپت باید در scheduled isolated task/runner با دسترسی محدود و مسیر backup خصوصی اجرا شود؛ scheduling/alerting آن هنوز ساخته نشده است. Secretهای real در workspace قرار ندهید.

## Restore

1. Restore فقط به DB نو و isolated با `RESTORE_TARGET_ENV=restore-test` یا `staging`; script env دیگری را رد می‌کند و `RESTORE_TARGET_CONFIRM=VERIFIED_ISOLATED_NONPRODUCTION_TARGET` می‌خواهد. این confirmation یک اپراتور-assertion است، نه تشخیص خودکار production endpoint؛ endpoint/account را جداگانه verify کنید.
2. از S3 encrypted artifact و sidecar را دانلود کنید؛ checksum را با `sha256sum -c` و age decryption را قبل از restore بررسی کنید. از identity private محافظت‌شده استفاده شود.
3. یک PostgreSQL version سازگار و خالی ایجاد کنید؛ TLS `verify-full` و role جدا تنظیم شود.
4. `deploy/scripts/restore-postgres.sh` اجرا کنید. `pg_restore` با single transaction, exit-on-error, no-owner/no-acl اجرا می‌شود؛ `--clean` ندارد.
5. schema versions، table counts بدون افشای محتوا، FK integrity، synthetic API login، query عملکردی، retention/purge و token key validity را کنترل کنید.
6. زمان شروع/پایان، dataset/version، checksum، RPO مؤثر، RTO و خطاهای test را در `BACKUP_RESTORE_TEST.md` ثبت کنید.
7. هر DB restore شده staging را پس از آزمون طبق policy حذف کنید؛ log/checksum هم داده‌های حساس ندارند.

JWT private JWK فعلی در `DATA_DIR` است و DB dump آن را پوشش نمی‌دهد؛ key volume backup/recovery strategy جداگانه لازم است. Key گم‌شده ممکن است همهٔ access tokenها را باطل کند.

## اجرای زمان‌بندی‌شده (پیشنهاد)

AWS EventBridge Scheduler → isolated Fargate backup task in private subnet → RDS reader/backup role (read only as feasible) → S3 prefix محدود. Task image باید `pg_dump` version سازگار، `age` و AWS CLI pin‌شده داشته باشد. Image/scheduler/IAM/lifecycle/alarms در این فاز provision نشده‌اند.

## Data loss warnings

- هیچ DB snapshot/restore، WAL/PITR یا application consistency guarantee در این workspace آزمایش نشده.
- Backup حذف account/user data را فوراً از immutable/versioned backups پاک نمی‌کند. Retention/legal deletion semantics باید تصویب و documented شوند.
- Snapshot restore data loss تا snapshot time می‌آورد؛ recovery captain باید impact/consent/communication را پیش از production action ارزیابی کند.
