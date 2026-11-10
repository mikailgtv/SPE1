# گزارش Rollback

**تاریخ بررسی:** ۷ اکتبر ۲۰۲۶  
**محدوده:** بازبینی مخزن و ابزارهای همین workspace؛ هیچ دسترسی به حساب cloud، production، staging، DNS، credential یا سرویس بیرونی وجود نداشت. پیکربندی/سند به‌تنهایی اثبات آمادگی نیست.
Backend image rollback، web prior-version rollback، database migration strategy و Android hotfix/recovery فقط در راهنما هستند؛ اجرا/تمرین نشده‌اند. Production release workflow image/artifact می‌سازد، اما deploy/rollback automation ندارد. Compose single host has SPOF.

- **Backend:** prior immutable image/digest provenance missing; no ECS/revision deploy.
- **Web:** static artifact deploy/version pointer/CDN invalidation/previous build restore process not provisioned.
- **DB:** Flyway auto-run on app startup; no demonstrated down migration. Rollback binary can be schema-incompatible; plan must be expand/contract or tested forward-fix/restore with explicit data-loss RPO approval.
- **Android:** prior installed app cannot safely downgrade; direct hotfix requires same signing key and higher versionCode; signer recovery/update channel unverified.
- **Test:** no staged rollback drill, bad deploy, health check, or recovery evidence.

**Status: BLOCKED**; no change/deploy to production authorized.
