# Production Operations Runbook

**وضعیت:** قالب Runbook؛ on-call، cloud resources، pager و production deployment هنوز راه‌اندازی نشده‌اند. این دستورالعمل مجوز استقرار فعلی نیست.

## اطلاعات لازم پیش از Change

ثبت کنید: incident/change ID، owner و approver، region/account، Git SHA، image digest، Android versionName/versionCode، Flyway schema version فعلی/هدف، پنجرهٔ تغییر، آخرین backup موفق، آخرین restore drill، rollback trigger و مسیر تماس. در حال حاضر owner/on-call مشخص نشده است.

## Pre-deploy gate

- CI سبز روی همان commit؛ backend integration و Android builds/tests ثبت‌شده باشند.
- High/Criticalهای `SECURITY_AUDIT_REPORT.md` رفع یا پذیرش ریسک رسمی/محدود/زمان‌دار داشته باشند.
- DB schema migration reviewed باشد؛ backup رمز‌شدهٔ off-site و restore test معتبر باشد.
- `DB_URL` خصوصی و TLS `verify-full`، `APP_ENV=production`, `LOG_LEVEL=INFO`, secrets secret-manager mounted و IAM least-privilege بررسی شود.
- RDS capacity و Hikari pool total محاسبه؛ connection limits و alarms کنترل شود.
- staging smoke/rollback قبلاً برای همان version و migration پاس شده باشد.
- نگهداری داده، privacy notice و Android permission/data disclosure برای distribution مستقیم تأیید حقوقی شود.

تا این موارد برقرار نیستند، **STOP — Production Not Ready**.

## استقرار backend (طرح AWS، اجرا نشده)

1. Reviewer release را در GitHub `production` environment approve می‌کند.
2. workflow tagدار image versioned را در GHCR push می‌کند؛ این پروژه هنوز image digest را به IaC/task definition خودکار انتقال نمی‌دهد.
3. Operator با infra pipeline جداگانه ECS task definition revision را به digest immutable تغییر می‌دهد؛ migration task و role separation باید پیش از این release پیاده شده باشد.
4. ابتدا staging/canary target را به‌روز کنید؛ `/health/live` liveness، `/health/ready` readiness check شود.
5. Target healthy، error rate، latency، RDS connections/CPU/storage و logهای redacted را مشاهده کنید.
6. پس از canary، به‌تدریج desired count را به نسخهٔ جدید منتقل کنید؛ deploy کامل تا موفقیت smoke test متوقف بماند.

**CLI deploy command در این Runbook عمدی اضافه نشده:** account/cluster/service/task definition/roleها و rollback strategy مشخص یا validate نشده‌اند؛ placeholder command ممکن است resource اشتباه را تغییر دهد.

## Smoke test پس از deploy

از شبکهٔ کنترل‌شده و بدون ثبت credential/location واقعی:

1. `GET /health/live` → 200 `live`.
2. `GET /health/ready` → 200 `ready`, `database=healthy`, `storage=healthy`; push=`not_configured` را failure نمی‌شمارید چون push server-side موجود نیست.
3. HTTPS certificate hostname/chain و redirect HTTP→HTTPS را بررسی کنید؛ DB endpoint از اینترنت غیرقابل دسترس باشد.
4. Test account synthetic: login/logout, token refresh/expiry, family/device authorization, pairing/revoke/re-pair, event feed, rules CRUD, location disabled/limited, SOS activate/ack/resolve. هیچ emergency service notification ادعا نشود.
5. Logs را بررسی کنید: password/token/location/SOS payload/query string وجود نداشته باشد.
6. Database migrations و pool health، backup freshness و last successful restore drill بررسی شود.
7. Parent/Child روی دستگاه‌های غیرواقعی/QA؛ permission revoke, offline/reconnect و battery restrictions با status واقعی.

نتایج، timestamp، app/server versions و screenshotهای غیرحساس ثبت شوند. Health HTTP 200 به‌تنهایی smoke test محسوب نمی‌شود.

## Incident / alert response

- **API 5xx یا readiness 503:** rollout را pause؛ isolate health failure، DB connectivity/connection pool و storage mount را بررسی کنید؛ secret یا SQL داده را log نکنید.
- **RDS connection/storage threshold:** rollout را متوقف؛ pool total/slow query بررسی؛ `maxPoolSize` را بدون capacity planning زیاد نکنید.
- **Backup stale/upload failure:** RPO خارج از هدف؛ release و destructive migration را متوقف کنید؛ backup operator بررسی کند.
- **Auth anomaly:** credential/token را از log نخوانید؛ rate-limit/security logs، session revocation و incident response امن؛ کاربر را از طریق کانال تأییدشده مطلع کنید.
- **Leak یا cross-family data:** incident severity بالا؛ ingress/credential scope محدود، snapshot forensic کنترل‌شده، access keys rotate و privacy/legal escalation.
- **Crash/Android severe regression:** direct APK را با همان release signing key و `versionCode` بالاتر اصلاح/منتشر کنید؛ پایین آوردن versionCode یا نصب silent فرض نشود.

## پایان تغییر

Record version/digest، smoke evidence، alarms، rollback decision و approver را اضافه کنید. اگر یک check حیاتی fail شد، rollback guide را اجرا و release را نهایی اعلام نکنید.
