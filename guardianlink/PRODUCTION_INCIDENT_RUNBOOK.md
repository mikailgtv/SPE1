# Runbook پاسخ به رخداد Production

**وضعیت:** قالب عملیاتی؛ Owner، pager، cloud account و مسیر تماس واقعی تعیین/تست نشده‌اند. پیش از Go-Live نام و کانال‌های تأییدشده را تکمیل و drill کنید. هر دادهٔ حساس را redact کنید.

## چرخه

```text
Detection → Triage → Severity → Containment → Communication → Fix → Validation → Recovery → Postmortem
```

## تشخیص و triage

1. Incident commander ثبت‌کنندهٔ رویداد باید شناسهٔ incident، زمان UTC، سرویس/نسخهٔ متاثر و شواهد redacted را نگه دارد. Token، password، private key، coordinates، SOS payload، email کامل و request body را در log/ticket/capture کپی نکنید.
2. Owner on-call (فعلاً UNASSIGNED) alert را acknowledge کند؛ بررسی کند آیا alert واقعی، duplicate یا false positive است؛ user-data access فقط با مجوز حداقلی.
3. scope: availability, integrity, confidentiality, tenant/family boundary, safety/SOS, impacted versions, backups, logs, third parties. هر cross-family disclosure یا secret exposure را security incident فرض کنید تا خلافش ثابت شود.

## Severity و تصمیم

| سطح | تعریف و پاسخ |
|---|---|
| SEV-1 | breach، cross-family leak، major outage/data loss یا failure سیستمیک SOS: incident commander + security/privacy/DB owners؛ فوراً rollout/write/destructive changes را pause و containment شروع کند. |
| SEV-2 | degradation عمدهٔ سرویس/قابلیت، backup failure بدون restore اثبات‌شده، widespread auth/sync failures: page primary on-call، impact/mitigation جاری را ثبت و release را متوقف کند. |
| SEV-3 | اختلال محدود با workaround امن: ticket/assigned owner و زمان follow-up؛ بررسی privacy/security اگر داده حساس است. |
| SEV-4 | مشکل کم‌اثر/polish: backlog و owner؛ بدون نادیده‌گرفتن مشکل privacy/security. |

آستانهٔ زمانی و escalation contact را سازمان تعیین کند؛ این runbook هیچ SLA یا اعداد فرضی تعریف نمی‌کند.

## مهار

- API/backend: از طریق operator مجاز rollout را pause/traffic isolate کنید؛ از دستورهای cloud placeholder یا endpointهای ناشناخته استفاده نکنید.
- credential/key compromise: revoke/rotate از secret manager و از مسیر تأییدشده؛ JWT key rotation ممکن است همهٔ sessionها را invalid کند؛ impact را کنترل کنید. مقدار key را هرگز چاپ نکنید.
- data isolation/privacy: محدودکردن دسترسی/token/session از مسیر مجاز، ضبط evidence حداقلی و حفظ audit chain؛ از production data برای reproduction استفاده نکنید.
- database/migration: توقف write/release در صورت integrity risk؛ snapshot فقط با approval و private access؛ restore روی production فقط پس از تصمیم DBA/incident commander/data owner.
- FCM/SOS: failure را صریح اعلام و fallback ایمن/اطلاع‌رسانی تأییدشده تعریف کنید؛ تماس اضطراری واقعی را شبیه‌سازی نکنید.

## ارتباطات

Incident commander کانال داخلی تأییدشده را انتخاب و cadence وضعیت را اعلام کند؛ customer/privacy/legal notices فقط با owner مجاز و بر مبنای facts/jurisdiction-specific requirements. زمان، دامنهٔ دانسته/نامعلوم، workaround، و تغییرات بعدی را بدون PII بگویید. هیچ ادعای قطعی قانونی نکنید.

## رفع و recovery

1. fix از review/CI و change approval عبور کند؛ production patch دستیِ بدون ثبت ممنوع.
2. deploy به محیط کنترل‌شده، health/auth/tenant isolation و functional smoke امن را verify کنید.
3. DB integrity، migration compatibility و backup freshness را بررسی کنید. Rollback app image، schema را خودکار برنمی‌گرداند؛ migration را بدون review reverse نکنید.
4. monitor alert/error/latency تا پنجرهٔ مصوب owner؛ در صورت regression rollback plan تست‌شده را اجرا کنید.
5. بازیابی را فقط پس از approval، data reconciliation و اطلاع‌رسانی لازم اعلام کنید.

## Postmortem

در موعدی که سازمان تعیین می‌کند blameless review: timeline, root cause/evidence, user impact, data classes, detection gaps, containment, recovery, RPO/RTO measured or NOT_MEASURED, actions owner/date, alert/rollback changes. هیچ credential یا دقیق location درج نشود.

## Owner/کانال‌های نیازمند تکمیل

| نقش | Owner فعلی | تکمیل پیش از Go-Live |
|---|---|---|
| Incident Commander / Operations | UNASSIGNED | نام، pager، deputy |
| Security lead | UNASSIGNED | escalation channel |
| DBA / backup owner | UNASSIGNED | restore decision/contact |
| Privacy/legal contact | UNASSIGNED | jurisdiction-specific review route |
| Release manager | UNASSIGNED | approver/rollback authority |
