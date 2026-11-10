# Environment & Secret Management

## اصل separation

Development, staging, production باید resource/secret/database/key/account/network/channel جدا داشته باشند. فقط اسم environment متفاوت گذاشتن کافی نیست. پیشنهاد AWS eu-central-1; فعلاً هیچ cloud account یا environment provision نشده است.

| Env | Backend/DB | App endpoint/signing | Data |
|---|---|---|---|
| Development | Compose/local PostgreSQL؛ fake/test users | Emulator `10.0.2.2:8080`; debug key | فقط synthetic/local |
| Staging | AWS staging VPC, RDS, S3/KMS, ECS, secrets scope جدا | HTTPS staging URL; staging signing key | synthetic/anonymized |
| Production | AWS prod account/VPC/resources و keys جدا | HTTPS production URL; production signing key | only legitimate production |

Staging و Production باید package signature/API endpoint جدا داشته باشند. Android `applicationId` ثابت است؛ build properties endpoint را جدا می‌کنند ولی دو variant هم‌زمان نصب/به‌روزرسانی نمی‌شوند.

Templateهای non-secret هر محیط در `deploy/environments/.env.development.example`, `.env.staging.example`, `.env.production.example` هستند؛ این‌ها خودکار load نمی‌شوند و credential ندارند. Local `.env` یا AWS task environment باید جداگانه provision شود.

## Server envهای واقعاً مصرف‌شده

| Variable | منبع/مصرف | نکته |
|---|---|---|
| `APP_ENV` | application.conf → `AppConfig` | فقط `development`, `staging`, `production`; staging/production `sslmode=verify-full` و non-DEBUG را enforce می‌کند. |
| `LOG_LEVEL` | logback root + config validation | پیش‌فرض INFO؛ staging/production DEBUG رد می‌شود. |
| `DB_URL`, `DB_USER`, `DB_PASS` | `AppConfig`/Hikari/Flyway | credential از environment/secret mount؛ production URL باید TLS verify-full داشته باشد. Role هنوز migration/runtime مشترک است (blocker). |
| `DB_POOL_MAX`, `DB_POOL_MIN`, `DB_CONNECTION_TIMEOUT_MS`, `DB_VALIDATION_TIMEOUT_MS`, `DB_IDLE_TIMEOUT_MS`, `DB_MAX_LIFETIME_MS` | Hikari config | defaults در source؛ مقادیر باید بر اساس replica/RDS capacity تنظیم و load-test شوند. |
| `DATA_DIR` | token JWK/data path | production persistent/secure key storage هنوز طراحی/اتصال نشده؛ task ephemeral data می‌تواند signing key را از بین ببرد. |
| `PORT`, `ACCESS_TTL_SEC`, `PAIRING_TTL_SEC`, presence/battery/geofence variables | application.conf | برخی envها در Compose نمونه منتقل نشده‌اند؛ فعلاً defaults بر اساس config هستند. |
| `GEO_EVENT_RETENTION_DAYS` | Geofence retention config | این یک retention موردی است، نه policy جامع.

## مواردی که فعلاً configuration نشده‌اند/نباید ساخته‌شده فرض شوند

- `JWT_SECRET`: backend secret variable ندارد؛ Ed25519 JWK در `DATA_DIR` فایل می‌شود.
- `ENCRYPTION_KEY`: Server DB field encryption وجود ندارد؛ Android SecureStore از Android Keystore بهره می‌برد.
- `FCM_CONFIG`: FCM/server push اجرا نشده؛ notification feed/local Android notification با push یکسان نیست.
- `LOCATION_RETENTION_DAYS` و `SOS_RETENTION_DAYS`: متغیر environment سراسری متصل نشده؛ location settings/db logic و SOS retention محدودیت‌های جدا دارند.
- `WEB_FILTER_MODE`: policy در DB/Parent settings ذخیره می‌شود؛ variable محیطی عمومی وجود ندارد.
- `API_BASE_URL`: Android با Gradle property `guardianReleaseBaseUrl` ساخته می‌شود، نه runtime env داخل APK.
- OAuth/admin credentials: OAuth/admin panel پیاده‌سازی/پیکربندی نشده.

## Secrets

- Local `.env` به Git ignore اضافه شده، اما `.env` یا فایل keystore را در workspace/Git commit نکنید.
- Development از دادهٔ ساختگی و secretهای دورریختنی استفاده کند.
- Staging/Production: AWS Secrets Manager/KMS و task IAM role؛ CI Android signing keys در environment-scoped encrypted secrets یا secure signing/HSM. دسترسی read محدود و audit شود.
- نه `.env.example`, CI YAML, Docker build argument, artifact, ADB logcat, issue/ticket یا analytics log محل secret نیست.
- Rotation, expiration, owner, access review و incident revocation ثبت شود؛ کلیدهای فعلی rotation workflow ندارند.

## Config loading/review

PR باید پیکربندی محیط را بدون secret diff کند؛ deployment smoke باید hostname/TLS/environment را بررسی کند. Secret value هیچ‌گاه در health response یا logs چاپ نشود. مقدار placeholder مثل `api.replace-with-your-owned-domain.tld` production endpoint نیست.
