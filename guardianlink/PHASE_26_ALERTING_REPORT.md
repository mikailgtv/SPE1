# گزارش هشدارها

**تاریخ بررسی:** ۷ اکتبر ۲۰۲۶  
**محدوده:** بازبینی مخزن و ابزارهای همین workspace؛ هیچ دسترسی به حساب cloud، production، staging، DNS، credential یا سرویس بیرونی وجود نداشت. پیکربندی/سند به‌تنهایی اثبات آمادگی نیست.
| هشدار | Trigger | Severity | Destination/Owner/Runbook | وضعیت |
|---|---|---|---|---|
| Backend down/readiness 503 | تعریف عددی ندارد | SEV-1/2 باید تعیین شود | pager/owner واقعی مشخص نیست | BLOCKED |
| DB unavailable/connections/storage | تعریف/provision نشده | SEV-1/2 | DBA/SRE unassigned | BLOCKED |
| API 5xx/latency spike | baseline ندارد | SEV-2 | destination unconfigured | BLOCKED |
| Backup failure/stale/restore drill age | script error only; no monitor | SEV-1 for no safe restore | backup owner unassigned | BLOCKED |
| FCM/provider failure | provider/config absent | scope/impact undefined | owner missing | BLOCKED |
| SOS processing systemic failure | no monitoring/provider route | SEV-1 definition in incident policy; no alert | owner missing | BLOCKED |
| auth abuse/cross-family access | no deployed detector/pager | SEV-1 | security owner unassigned | BLOCKED |
| storage failure | readiness check source; no alert destination | SEV-2 | SRE unassigned | BLOCKED |

هیچ alert provision یا fire/ack/resolve drill انجام نشد. صرفاً دستورالعمل پیشنهادی monitoring فایل آماده alert واقعی محسوب نمی‌شود. باید trigger, threshold, owner, destination, escalation, dedupe/silence, test event و runbook link مصوب شوند.
