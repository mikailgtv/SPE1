# راستی‌آزمایی معماری Production

**تاریخ بررسی:** ۷ اکتبر ۲۰۲۶  
**محدوده:** بازبینی مخزن و ابزارهای همین workspace؛ هیچ دسترسی به حساب cloud، production، staging، DNS، credential یا سرویس بیرونی وجود نداشت. پیکربندی/سند به‌تنهایی اثبات آمادگی نیست.
## معماری موردنظر در مستندات (NOT_VERIFIED)

```text
Parent/Child Android ─HTTPS─┐
Web ─HTTPS/CDN یا edge──────┴→ Reverse proxy/TLS edge → Backend API → PostgreSQL
                                                       └→ External services (موجود/پیکربندی‌شده اثبات نشده)
```

`DEPLOYMENT_GUIDE.md` فقط AWS/ECS Fargate + ALB + private RDS + Secrets Manager/KMS + S3 را به‌عنوان پیشنهاد معماری معرفی می‌کند؛ rollout واقعی صریحاً اجرا نشده. Workflows ساخت artifact/GHCR image دارند، اما ECS/IaC deploy ندارند.

## شواهد repository

Compose تعریف می‌کند: `db` و `server` در شبکهٔ داخلی پیش‌فرض؛ DB پورت host ندارد؛ server فقط `expose:8080`; Caddy اختیاری profile `edge`، host ports 80/443 و static web/API proxy. Compose single-host آزمایشی است، نه production. Caddy TLS با `DOMAIN` نمونه می‌گیرد؛ cert, DNS, redirect و chain در live host بررسی نشده. `/metrics` در edge به 404 می‌رود؛ health paths از proxy به server می‌روند. Caddy/service و host مشترک، SPOF است. DB failover, HA, network ACL/security groups، WAF/CDN و edge redundancy اثبات نشده.

`server` Ktor listener در 8080؛ if directly exposed, health and metrics/API port may be reachable. `/health/ready` وضعیت DB/storage را بیان می‌کند؛ `/metrics` در کد server موجود است ولی edge آن را hide می‌کند. این مرزبندی فقط source-level است؛ firewall enforcement اثبات نشده. هیچ routeی با عنوان admin از search مسیرهای محدود دیده نشد؛ inventory exhaustiveness/runtime verification انجام نشده.

## نتیجه

**Production architecture: NOT_VERIFIED.** مرزبندی مطلوب و نقش single service را docs/source شرح می‌دهند؛ هیچ production system واقعی برای ارزیابی exposure، failover، isolation یا نقطه‌خرابی وجود ندارد. Multi-replica با process-local limiter فعلی تا تعیین تکلیف `BLOCKED` است.
