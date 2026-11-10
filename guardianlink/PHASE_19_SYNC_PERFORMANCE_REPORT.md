# گزارش کارایی همگام‌سازی — فاز ۱۹

**وضعیت:** `BLOCKED_BY_ENVIRONMENT`; زمان sync، حجم شبکه و retry `NOT MEASURED`.

## مشاهدات منبع

- صف موقعیت آفلاین سقف 240 نقطه دارد؛ `addAll` برای dedupe پیمایش می‌کند و کل snapshot را در SecureStore serialize/encrypt می‌کند. هزینهٔ CPU، IO و رمزنگاری اندازه‌گیری نشده است.
- `WebSafetyCache` mutex دارد و event queue را به آخرین 100 محدود می‌کند؛ ارسال pending حداکثر 20 event برمی‌گرداند.
- `AppPolicyCache` eventها را به 500 محدود می‌کند؛ وضعیت cache به SecureStore وابسته است. latency و اندازه payload مشخص نیست.
- `NetworkWatcher` با onAvailable یک کار یک‌بارهٔ sync زمان‌بندی می‌کند. تعداد enqueue در reconnectهای پرتکرار، uniqueness/coalescing WorkManager و retry behavior در دستگاه/runtime آزموده نشده‌اند.
- Sync زمان‌بندی‌شده، queueها و privacy/security تغییر نکردند.

## سنجه‌ها

| سنجه | مقدار |
|---|---|
| مدت sync p50/p95، تفکیک شبکه/سرور/ذخیره‌سازی | `NOT MEASURED` |
| حجم request/response و تعداد batch | `NOT MEASURED` |
| reconnect، retry، backoff و duplicate | `NOT MEASURED` |
| اثر queue بزرگ/پرشدن و حذف داده قدیمی | `NOT MEASURED` |
| battery، CPU و wakeup | `NOT MEASURED` |
| race در چند worker/device | `NOT MEASURED` |

## ارزیابی و ادامه

هیچ bottleneck sync با اندازه‌گیری اثبات نشد. برای تست آتی، شبکهٔ کنترل‌شده و قطع/وصل مصنوعی، صف دادهٔ synthetic، ثبت مدت و بایت بدون مختصات/محتوای حساس، و بررسی idempotency در staging لازم است. رفتار محدودیت/رد مکان‌یابی و پاک‌سازی پس از خاموشی sharing باید همچنان privacy-preserving بماند. sync را به foreground یا زمان‌بندی پرتکرار اجباری تبدیل نکنید.
