# گزارش مدیریت اسرار

**تاریخ بررسی:** ۷ اکتبر ۲۰۲۶  
**محدوده:** بازبینی مخزن و ابزارهای همین workspace؛ هیچ دسترسی به حساب cloud، production، staging، DNS، credential یا سرویس بیرونی وجود نداشت. پیکربندی/سند به‌تنهایی اثبات آمادگی نیست.
## Inventory طبقه‌بندی (بدون افشای مقدار)

| گروه | مکان/سازوکار مشاهده‌شده | وضعیت |
|---|---|---|
| Database | env vars `DB_USER/DB_PASS`; production templates خالی؛ AWS Secrets Manager صرفاً مستند | BLOCKED؛ live secret manager/access review ندارد |
| JWT signing | private Ed25519 JWK در `DATA_DIR/ed25519.jwk` load-or-generate؛ persistent, encryption-at-rest, rotation/version transition اثبات نشده | BLOCKED |
| Field/encryption keys | Android Keystore برای local storage؛ server/backup key provisioning real environment ناموجود | PARTIAL/NOT_TESTED |
| FCM | credential/config/service اتصال production پیدا/تأیید نشد؛ delivery تاریخی NOT_TESTED | BLOCKED/NOT_APPLICABLE تا تصمیم scope |
| Cloud IAM/KMS/S3 | docs/script از AWS role/KMS نام می‌برد؛ هیچ resource/credential live ارائه نشده | BLOCKED |
| Storage | volume path `/data`; cloud storage config/use واقعی دیده نشد | BLOCKED |
| Monitoring/third party | integration/provisioned credentials اثبات نشده | NOT_TESTED |
| Android signing | workflow به GitHub protected env secrets نیاز دارد؛ key/cert موجود یا verify نشده | BLOCKED |

`find` فقط `.env.example`/محیط‌نمونه‌ها را یافت؛ secret file/keystore واقعی در workspace دیده نشد. `.gitignore` env/keystore/secrets را exclude می‌کند، اما Git metadata/history در این workspace موجود نیست؛ tracked status/history scan نتیجه‌پذیر نیست. Heuristic scan محدود counts-only شش pattern در دو فایل یافت؛ محتوای احتمالی شامل variable names/placeholders است و match به‌تنهایی secret leak نیست؛ هیچ secret value چاپ یا گزارش نشد. ابزار gitleaks/trufflehog/detect-secrets در دسترس نبود.

**نتیجه: BLOCKED.** Secret manager واقعی قابل‌دسترسی/تأیید نیست. راهکار پیش‌نیاز: cloud manager با workload identity/IAM least privilege، per-environment secret namespaces، JWT key persistence/rotation design، audit/rotation/revocation evidence؛ Android signer در protected CI، backup/recovery کنترل‌شده و جدا از repo.
