# راهنمای استقرار GuardianLink

**وضعیت:** طرح عملیاتی؛ هیچ زیرساختی provision یا deploy نشده است. محیط workspace فاقد account، domain، credentials و Docker است.

## انتخاب پیشنهادی

با توجه به حساسیت داده‌های Location/SOS و نبود زیرساخت موجود، پیشنهاد این گزارش **AWS eu-central-1 (Frankfurt)** است: ECS Fargate برای API، RDS PostgreSQL در subnet خصوصی، ALB با HTTPS/ACM، ECR برای image، Secrets Manager/KMS برای secrets و S3 با versioning/Object Lock برای نسخه‌های backup. این صرفاً پیشنهاد معماری است، نه تضمین residency/compliance؛ بازار هدف، قرارداد پردازش داده، سن کاربران و الزامات حقوقی باید جداگانه تأیید شوند.

GitHub Actions برای CI انتخاب شده؛ در workspace مخزن Git/remote وجود ندارد، بنابراین workflowها اجرا نشده‌اند.

## معماری مقصد

```text
Android Parent/Child
        │ HTTPS 443
Route 53 → ALB + ACM (+ WAF پس از threat review)
        │ private target
ECS Fargate API (private subnet, no public IP)
        ├── RDS PostgreSQL Multi-AZ (private, encrypted, TLS verify-full)
        ├── Secrets Manager / KMS (least-privilege task role)
        ├── CloudWatch Logs / Metrics / Alarms
        └── ECR (versioned immutable image)

Scheduled backup task → pg_dump → age encryption → private S3/KMS
```

- DB هیچ public ingress نداشته باشد؛ Security Group فقط از ECS task SG و operational bastion محدودشده اجازه بگیرد.
- ALB فقط 443 عمومی؛ HTTP فقط redirect به HTTPS. backend port از اینترنت منتشر نشود.
- هر environment در account یا حداقل VPC/resource scope مستقل باشد؛ RDS، S3، KMS key، Secrets Manager path، hostname و IAM role مشترک نباشند.
- Staging از fixture بی‌نام‌شده استفاده کند؛ production location/account/SOS داده به staging کپی نشود.
- DB connection pool را با فرمول `replica_count × DB_POOL_MAX + migration/admin headroom < RDS max_connections` اندازه بگیرید؛ مقدار پیش‌فرض ۱۰ صرفاً اولیه است، ظرفیت تضمین‌شده نیست.

## Blockerهای معماری فعلی

1. Backend در startup با همان DB credential، `db.migrate()` اجرا می‌کند؛ نقش migration و runtime جدا نشده‌اند. در این وضعیت DB role با حداقل دسترسی ممکن نیست.
2. JWT signing JWK در `DATA_DIR/ed25519.jwk` خوانده/ساخته می‌شود. ECS ephemeral storage با restart key را از بین می‌برد؛ EFS یا secret/KMS integration و rotation در کد آماده/تأیید نشده.
3. حساسیت داده‌ها در PostgreSQL به‌صورت column encryption پوشش داده نشده؛ RDS disk encryption/S3 KMS architecture است و actual provisioning/verification ندارد.
4. migration V1–V9 روی staging DB اجرا نشده؛ backup قبل از migration و expand/contract policy لازم است.
5. CI workflowهای جدید صرفاً image/APK artifact را می‌سازند؛ ECS deploy/rollback workflow هنوز وجود ندارد.
6. Caddy/Compose فقط برای dev/single-host آزمایشی است؛ Compose profile `edge` به معنی production-grade cloud deployment نیست.

تا رفع این موارد، اجرای واقعی production ممنوع است.

## فرایند Staging پیشنهادی

1. یک AWS staging account/VPC مستقل و staging domain ایجاد کنید؛ TLS certificate و DNS ownership را validate کنید.
2. RDS staging جدا با TLS، encryption at rest، automated backup و retention مصوب بسازید.
3. Secrets را فقط در Secrets Manager نگه دارید؛ هیچ مقدار واقعی در `.env`, GitHub variable معمولی، image یا ticket قرار نگیرد.
4. ابتدا backup/snapshot بگیرید؛ migration را به‌صورت controlled task اجرا کنید. در وضعیت فعلی app startup خودش migration می‌کند و separation هنوز حل نشده.
5. container tag بر اساس SHA/version را به ECS service staging بدهید؛ در workflow فعلی فقط GHCR push وجود دارد و **ECS deploy انجام نمی‌شود**.
6. Android `assembleRelease` را با `guardianReleaseBaseUrl=https://<staging-api>` و staging signing key بسازید؛ فایل خروجی در workflow به‌عنوان artifact خصوصی باقی می‌ماند.
7. smoke-test کامل از `PRODUCTION_RUNBOOK.md` اجرا و نتیجه/نسخه ثبت شود.

## فرایند Production پیشنهادی

1. Staging gates، backup/restore drill، security review، Android direct-distribution verification و change approval باید پاس شوند.
2. backup پیش از migration/release را خارج از RDS بسازید و checksum/رمزگشایی را verify کنید.
3. approval gate در GitHub `production` environment را اعمال کنید؛ workflow تولید فقط image GHCR و APK امضاشده می‌سازد، **خودکار ECS deployment یا public APK distribution نمی‌کند**.
4. operator نسخهٔ image را در ECS task definition جدید deploy کند؛ target group health/readiness را پایش کند؛ rollout درصدی/canary و rollback plan قبل از شروع مشخص شود.
5. smoke test و monitoring را اجرا کنید؛ اگر critical check شکست خورد rollout را متوقف/rollback کنید.

## متغیرهای مهم

Server واقعی: `APP_ENV`, `LOG_LEVEL`, `DB_URL`, `DB_USER`, `DB_PASS`, `DATA_DIR`, `DB_POOL_MAX`, `DB_POOL_MIN`, timeoutهای DB، و آستانه‌های موجود در `application.conf`. `APP_ENV=staging/production` مستلزم `DB_URL` با `sslmode=verify-full` و `LOG_LEVEL` غیر-DEBUG است. برای RDS باید trust chain/CA واقعاً اعتبارسنجی شود.

Android release از Gradle property `guardianReleaseBaseUrl`, `guardianVersionName`, `guardianVersionCode` و secretهای signing environment-scoped استفاده می‌کند.

## بعد از استقرار

- Smoke test و dashboardها را مطابق `PRODUCTION_RUNBOOK.md` بازبینی کنید.
- backup monitor باید آخرین backup موفق، S3 copy، checksum، retention و آخرین restore drill را جداگانه بررسی کند.
- هر deploy با Git SHA، image digest، Android versionCode/VersionName، migration version و approver ثبت شود.
