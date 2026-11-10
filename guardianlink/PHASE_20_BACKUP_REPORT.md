# گزارش معماری Backup — فاز ۲۰

**وضعیت:** `PARTIAL` از نظر script/source؛ backup artifact واقعی، cloud, integrity verify و monitoring: `BLOCKED BY ENVIRONMENT`.

## معماری موجود/مقترح در source

```text
PostgreSQL
   │ pg_dump -Fc (stream; no plaintext dump file)
   ▼
age encryption (AGE_RECIPIENT)
   │ encrypted local file + SHA-256 sidecar
   ├──────── local restricted BACKUP_DIR (same-host risk)
   └──────── aws s3 cp + SSE-KMS request
                 └─ private/off-host retention/lifecycle/verification NOT provisioned

(DATA_DIR/ed25519.jwk is separate from DB and not included by pg_dump)
```

## شواهد script

`deploy/scripts/backup-postgres.sh` نیازمند pg_dump، age و AWS CLI است؛ local directory را 0700 و files را 0600 می‌گذارد؛ dump custom-format را مستقیم به age pipe می‌کند تا فایل plaintext روی disk نسازد؛ size nonzero، SHA-256 sidecar، daily/weekly/monthly file copies و local pruning defaults (14/56/365 روز) را پیاده می‌کند؛ upload encrypted artifact/sidecar را با `--sse aws:kms --sse-kms-key-id` درخواست می‌کند.

اما `BACKUP_DIR` می‌تواند same-host باشد؛ S3 bucket/object-lock/versioning/public access block/TLS policy/KMS key/IAM/lifecycle وجود یا صحت آن‌ها اینجا ثابت نیست. Script فقط checksum می‌سازد و read-back verify نمی‌کند؛ backup-age monitoring، alert، schedule، restore date/result tracking و backup duration/size metrics ندارد. اگر upload شکست بخورد script fail می‌شود اما alert خارجی تعریف نشده. Remote S3 lifecycle/delete policy به script واگذار نشده.

`deploy/scripts/restore-postgres.sh` encrypted backup را age-decrypt کرده و `pg_restore --single-transaction --exit-on-error --no-owner --no-acl` روی fresh database می‌زند؛ target env/confirmation check دارد و `--clean` ندارد. اما checksum باید operator از sidecar قبل از script verify کند. Confirmation string خوداظهاری است، نه endpoint production detector.

Private signing JWK در `DATA_DIR` خارج dump DB است؛ باید با سیاست کلید جداگانه backup/rotation شود. DB dump با restore بدون همان key می‌تواند auth impact داشته باشد.

## Integrity / monitoring requirements

طراحی هدف: backup موفق روزانه، age/version/checksum metadata، encrypted off-host copy، periodic decrypt-to-disposable-test + `pg_restore --list`/full isolated restore, schema/migration/FK/count and synthetic API tests; alert on failed/old backup, storage capacity, encryption/KMS failure and overdue restore drill. هیچ موردی از این زنجیره در cloud/runtime provision نشده است.

| کنترل | وضعیت واقعی |
|---|---|
| Scheduled daily execution | `NOT TESTED`; schedule یافت نشد |
| Off-host/region-separated storage | `BLOCKED BY ENVIRONMENT`; S3 URI/key/bucket absent |
| Encryption with age | source script only؛ artifact/key absent؛ `NOT TESTED` |
| S3 SSE-KMS | source flag only؛ KMS absent؛ `NOT TESTED` |
| Retention local | script defaults/pruning source; execution `NOT TESTED` |
| Remote lifecycle retention | `NOT CONFIGURED/NOT TESTED` |
| SHA-256 generated/verified | generation code; actual file absent; no verification run |
| Backup age/size/duration/failure metrics & alerts | `NOT IMPLEMENTED` in repository |
| Restore drill monitoring | `NOT IMPLEMENTED` |
| JWK key backup and recovery | guide flags gap; `NOT TESTED` |

## آزمون واقعی این فاز

در local sandbox فقط `bash -n deploy/scripts/backup-postgres.sh deploy/scripts/restore-postgres.sh` با خروجی `bash -n PASS` اجرا شد (2026-10-05). این صرفاً parse syntax است؛ به‌هیچ‌وجه اجرای backup، encryption، checksum یا restore نیست. اجرای backup script انجام نشد.

## وضعیت RPO/RTO

`RPO: NOT DEFINED`; روزانه‌بودن در script بدون schedule اجراشده و بدون WAL/PITR یک RPO عملیاتی اثبات نمی‌کند.  
`RTO: NOT DEFINED`; restore زمان‌گیری/تمرین نشده و target/runtime/resources/owner مشخص نیستند.

تا فراهم‌شدن PostgreSQL، pg_dump/pg_restore، age identities، cloud credentials، S3/KMS، off-host policy، runner/alerting و isolated restore DB، backup readiness قابل اعلام نیست.
