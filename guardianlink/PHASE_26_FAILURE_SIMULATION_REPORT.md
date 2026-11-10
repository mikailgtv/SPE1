# گزارش شبیه‌سازی خرابی

**تاریخ بررسی:** ۷ اکتبر ۲۰۲۶  
**محدوده:** بازبینی مخزن و ابزارهای همین workspace؛ هیچ دسترسی به حساب cloud، production، staging، DNS، credential یا سرویس بیرونی وجود نداشت. پیکربندی/سند به‌تنهایی اثبات آمادگی نیست.
هیچ simulation اجرا نشد؛ staging/infrastructure/monitoring/DB/provider وجود ندارد. RTO تمام سناریوها `NOT_MEASURED`; data integrity/recovery status `NOT_TESTED`.

| سناریو | Detection/alert | Recovery | Impact/integrity/RTO | Status |
|---|---|---|---|---|
| A Backend unavailable | provisioned monitor ندارد | ECS/Compose action ندارد | NOT_MEASURED | BLOCKED |
| B Database unavailable | source readiness checks DB; no external alert | failover/recovery not configured | NOT_TESTED | BLOCKED |
| C Notification provider unavailable | no verified provider, monitor absent | scope/fallback unspecified | NOT_TESTED | BLOCKED |
| D Storage unavailable | readiness checks local `DATA_DIR`; no alert | volume recovery not tested | NOT_TESTED | BLOCKED |
| E Bad deployment | health endpoints source only | rollback untested | NOT_MEASURED | BLOCKED |
| F Migration failure | Flyway startup migration; no dedicated migration task | failure/compatibility strategy untested | integrity unknown | BLOCKED |
| G Network failure | offline client behavior not UATed | reconnection/retry not verified | data loss/duplication unknown | BLOCKED |

هر drill باید فقط در nonproduction isolate اجرا شود؛ production data/credential را شبیه‌سازی یا مختل نکنید.
