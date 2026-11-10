# گزارش Encryption و مدیریت کلید — فاز ۲۰

**وضعیت:** `PARTIAL`; source review انجام شد، اثبات deployment/crypto key operation `BLOCKED BY ENVIRONMENT`.

## In transit

- `AppConfig` در staging/production مقدار `DB_URL` با `sslmode=verify-full` را اجباری می‌کند. Enforcement در محیط واقعی `NOT TESTED` است.
- CI release برای Android `API_BASE_URL` با prefix `https://` می‌خواهد؛ مقدار GitHub variable/deployed host اینجا موجود نیست.
- `web/src/api.ts` base API را same-origin محدود می‌کند. Caddyfile TLS و HSTS را تعریف می‌کند؛ Compose profile مربوط Caddy اختیاری و comments می‌گویند نمونهٔ single-host production-grade نیست. وجود edge TLS در استقرار واقعی `NOT TESTED`.
- certificate pinning ادعاهای بعضی diagramها با source/نسخهٔ release جداگانه بازبینی لازم دارد؛ این Phase evidence run/device نداشت.

## At rest

| محل | source observation | شواهد محیطی |
|---|---|---|
| Android SecureStore | AES-256-GCM با key Android Keystore؛ ciphertext در DataStore | device/Keystore testing `NOT TESTED` |
| Android PlainPrefs | DataStore بدون crypto برای برخی settingهای UI/cooldown؛ محتوا باید غیرحساس بماند | device data-at-rest `NOT TESTED` |
| Android offline location/SOS/policy queues | از SecureStore استفاده در source | encryption round-trip/backup `NOT TESTED` |
| Browser tokens | memory-only؛ نه local/session storage | browser runtime `NOT TESTED` |
| Browser UI prefs | language/theme در localStorage؛ کم‌حساس | local browser inspection source فقط |
| PostgreSQL | volume mount در Compose؛ هیچ explicit pgcrypto/TDE config دیده نشد | underlying disk/managed DB encryption `NOT TESTED` |
| Server signing key | private Ed25519 JWK در `DATA_DIR/ed25519.jwk` در فایل؛ write permissions/volume encryption/rotation نامشخص | file permissions/key custody `NOT TESTED` |
| Backup | script از pg_dump→age pipe و AWS SSE-KMS args استفاده می‌کند | age/KMS/remote storage existence/encryption `NOT TESTED` |
| Logs | JSON stdout؛ sink/retention/storage crypto بیرونی نیست | `NOT TESTED` |

## رمزنگاری application-level

- password hash با `PasswordHasher`؛ refresh token، device secret، pairing code و exchange token به شکل hash در DB؛ ذخیرهٔ plaintext source برای این مقادیر دیده نشد.
- مختصات location و SOS در PostgreSQL به صورت columns عددی plaintext relational دیده می‌شوند؛ encryption at rest در سطح disk/database host نامعلوم است. در field-level crypto فعلی evidence پیدا نشد. Risk: DB reader/backup plaintext پس از decrypt دسترسی به مختصات دارد؛ threat model/key management تصمیم لازم دارد.
- کلید Ed25519 خصوصی فایل JWK است و backup PostgreSQL آن را شامل نمی‌شود؛ اگر lost شود، JWTهای قبلی قابل verify نخواهند بود؛ اگر افشا شود، impersonation خطر دارد. Permission/rotation/KMS integration باید پیش از production تعیین شود.
- پیچیدگی field-level encryption، بدون owner، key rotation، query requirements و restore drill افزوده نشد.

## Secret references scan

- `deploy/.env.example`, `deploy/environments/*.env.example`, `web/.env.example` template/placeholder هستند؛ files واقعی `.env` در scan فهرست نشدند.
- DB variables، `DATA_DIR`, DB pool/TTL/APP_ENV/LOG_LEVEL و geofence config در HOCON/env; Web فقط `VITE_API_BASE_URL`; Android release API URL via GitHub variables.
- CI references: GitHub Actions secrets برای Android keystore/signing fields و `GITHUB_TOKEN`; credential values در source report افشا/چاپ نشده‌اند.
- backup refs: DB host/user/PG password mechanism, `AGE_RECIPIENT`, private `AGE_IDENTITY_FILE`, `S3_BACKUP_URI`, `S3_KMS_KEY_ARN`, `AWS_REGION`; واقعی absent.
- Firebase/FCM credential file/value و cloud IAM role واقعی در workspace/deploy config پیدا نشد؛ architecture diagramهای قدیمی نام FCM دارند اما deployment proof نیست.
- Workspace `.git` directory ندارد: `.git` history scan `BLOCKED BY ENVIRONMENT`; cannot conclude secrets never committed historically. APKs and built JS exist; binary/build artifact secret forensic review `NOT TESTED`. No real credential string intentionally printed.

## نتیجه و اقدامات

TLS conditions in source are positive but operational configuration remains unverified. At-rest encryption, KMS/key custody, JWK backup/permissions/rotation and data encryption on backups cannot be declared PASS. Before production: secret scanner against full Git history in trusted environment, artifact scanning, secret manager injection, least privilege, private key backup/rotation/restore procedures, verified DB disk encryption and end-to-end TLS checks.
