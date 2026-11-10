# گزارش مقیاس‌پذیری — فاز ۱۹

**نتیجه:** `INSUFFICIENT_DATA` برای ظرفیت؛ بار و منابع runtime اندازه‌گیری نشدند.

## برداشت معماری از منبع

سامانه شامل Web SPA، Android Parent/Child، Backend API و PostgreSQL است. Web/Android از API مشترک استفاده می‌کنند. فهرست‌ها و برخی queueها محدود هستند. مسیر Web برای تاریخچه SOS درخواست جداگانه به ازای هر device می‌زند؛ مسیر Backend فهرست SOS را به نشست‌ها نگاشت می‌کند و هر نشست را جداگانه بازخوانی می‌کند. فهرست device نیز به `refreshHealth` می‌رسد. این الگوها نامزد بررسی fan-out/N+1 هستند، نه نتیجهٔ load test.

## نمودار معماری مشاهده‌شده

```text
┌────────────────────┐             ┌──────────────────────┐
│ Web SPA (Vite/React)│             │ Android Parent/Child │
└─────────┬──────────┘             └──────────┬───────────┘
          └──────────── HTTPS/API ────────────┘
                              │
                    ┌─────────▼──────────┐
                    │ Backend (Ktor/API) │
                    │ auth / rate limits │
                    └─────────┬──────────┘
                              │ JDBC
                    ┌─────────▼──────────┐
                    │ PostgreSQL         │
                    │ migrations/indexes │
                    └────────────────────┘

Android local encrypted queues ──(scheduled sync)──> Backend API
```

این نمودار از ساختار واقعی source/repository و فایل‌های فاز 16/18 خلاصه شده است؛ ظرفیت، topology deployment یا تعداد replica را ادعا نمی‌کند.

## وضعیت ظرفیت

| محور | نتیجه |
|---|---|
| خانواده/device هم‌زمان | `INSUFFICIENT_DATA` |
| RPS یا سقف safe concurrency | `INSUFFICIENT_DATA` |
| سقف connection/pool/DB | `NOT MEASURED` |
| صف اعلان/location throughput | `NOT MEASURED` |
| قفل/race و رفتار چند replica | `NOT MEASURED` |
| scale-up/scale-out/recovery | `NOT MEASURED` |

## پیشنهاد

پیش از ادعای ظرفیت، deployment topology، resource limits، pool size، SLO و workload مورد انتظار مستند شود؛ آزمون بار/stress/recovery فقط در staging ایزوله اجرا گردد. مسیرهای fan-out با trace واقعی تأیید، سپس در صورت اثبات، API batch-safe یا query تجمیعی با تست‌های مجوز/مالکیت و regression بررسی شود. برای DB فقط بر پایهٔ plan واقعی بهینه‌سازی شود.
