# گزارش Staging واقعی — Phase 13

**نتیجه: `BLOCKED` — staging ساخته یا deploy نشده است.**

## انتخاب معماری و cost control

Phase 12 پیشنهاد AWS `eu-central-1` (Frankfurt) را به‌عنوان معماری هدف ثبت کرده بود. در Phase 13 هیچ AWS account/role/region permission، domain، VPC یا cost approval ارائه نشد؛ `aws` CLI هم در دسترس نیست. برای جلوگیری از resource ناخواستهٔ هزینه‌زا، هیچ AWS resource ایجاد/درخواست/تغییر داده نشد.

پیشنهاد staging: ECS Fargate API خصوصی، ALB HTTPS عمومی، PostgreSQL RDS خصوصی، Secrets Manager/KMS، S3 encrypted backup، CloudWatch logs/alarms. در staging می‌توان single-AZ/کمینهٔ desired task و budget alert را بررسی کرد؛ sizing/قیمت فقط پس از تعیین retention, test throughput, backup/PITR, data transfer, NAT/endpoints و uptime schedule با AWS Pricing Calculator و approval مسئول مالی ثبت شود. قیمت عددی حدس زده/گزارش نشده.

## Resource inventory

| Resource | نیاز | وضعیت | Evidence |
|---|---|---|---|
| AWS account / payer / IAM OIDC role | account مستقل/تفکیک‌شده و least privilege | موجود نیست | `aws` unavailable؛ هیچ account ID ارائه نشده |
| VPC/subnets/security groups | API private؛ DB private؛ ingress محدود به ALB | ساخته نشده | no IaC/apply log |
| Domain/Route 53/ACM/TLS | hostname واقعی + valid cert | موجود نیست | هیچ domain/certificate/HTTPS probe |
| ECS/ECR/ALB | backend task/service + health probes | فقط workflow GHCR artifact source | no ECS/GHCR run/service/target group |
| RDS/PostgreSQL | compatible PG17, TLS, encryption, roles, backups | provision نشده | no endpoint/credential/psql |
| Secrets Manager/KMS | DB, JWT/key lifecycle, backup KMS | provision نشده | no secret ARN/key policy |
| S3 backup bucket | private/versioned/KMS/lifecycle | provision نشده | no bucket/IAM/age identity |
| CloudWatch/alarms | logs, API/DB/backup metrics | provision نشده | no metric/alert/incident test |
| Owner / approver / budget | named change/security/DB owner and spend approval | نامشخص | Phase12 final report lists owners/budget as open |

## Required before provisioning

1. نام حقوقی مالک AWS account، payer/budget سقف، region confirmation، data processing/residency owner و primary contacts.
2. AWS OIDC role scoped به staging؛ هرگز AWS access key را در پیام/Repository وارد نکنید.
3. DNS zone/domain و تأیید دسترسی؛ CI Git remote واقعی، reviewers و staging secrets.
4. IaC/Terraform/CDK plan به همراه security review, state locking/encryption, budget alarm; در این workspace IaC نداریم و Terraform نصب نشده.
5. App blockers: migration/runtime role مشترک، JWK در `DATA_DIR`, field encryption/RLS, backup job/restore test و logging/monitoring integration را پیش از دادهٔ واقعی حل کنید.

## بعد از provision — دستورالعمل سنجش

- Fresh PostgreSQL 17 و TLS `verify-full`; اجرای migrations V1–V9 و role privileges را در runbook ثبت کنید.
- Deploy image digest به ECS؛ `/health/live` و `/health/ready` از مسیر HTTPS واقعی و از داخل شبکه بررسی شوند.
- CI signing secrets و staging endpoint را فقط در protected GitHub Environment وارد کنید؛ no production data.
- API auth/IDOR/pairing/device sync/location-off/notification-feed/SOS synthetic smoke؛ push را `not_configured` گزارش کنید.
- backup encrypted→private S3→restore به DB نو و isolate؛ RPO/RTO اندازه‌گیری.
- Monitor/error alarms را inject و delivery را verify؛ rollback image/migration rehearsal انجام دهید.

هیچ موردی از این فهرست در این مرحله اجرا نشده؛ هیچ Run ID, host, certificate fingerprint, DB schema output یا staging test result وجود ندارد.
