# گزارش کارایی پایگاه‌داده — فاز ۱۹

**وضعیت:** `BLOCKED_BY_ENVIRONMENT`; همه سنجه‌های DB `NOT MEASURED`.

## محیط و شواهد

PostgreSQL، psql، Docker/Podman در دسترس نبودند؛ بنابراین هیچ اتصال DB، query اجراشده، `EXPLAIN (ANALYZE, BUFFERS)`, آمار index، pool، lock، deadlock یا اندازه داده ثبت نشد. Migrationها و SQL فقط ایستا مرور شدند. طرح query از روی متن SQL به‌تنهایی تأییدشده تلقی نمی‌شود.

## یافته‌های ایستای قابل پیگیری

- درج batch موقعیت در `LocationRepo.insertPoints` ظاهراً هر نقطه را در transaction جداگانه اجرا نمی‌کند، اما درج به‌صورت حلقه است؛ هزینه و امکان batch به driver، حجم batch و constraintها وابسته است. هیچ تغییری انجام نشد.
- فهرست SOS برای هر دستگاه جداگانه از Web درخواست می‌شود؛ مسیر سرویس `dbSessions(...).map(::getById)` و `getById`/`refreshStaleTracking` می‌تواند به‌ازای هر نشست چند round-trip DB بسازد. این الگو یک نامزد N+1 در کد است، اما شمار query و اثر آن اندازه‌گیری نشده است.
- `deviceOut` برای هر دستگاه `refreshHealth` را صدا می‌زند؛ تأیید تعداد و نوع queryها در runtime لازم است.
- pagination/limit موجود در برخی endpointها و constraint/indexهای migration به‌صورت منبع مرور شده‌اند؛ وجود index در migration، مناسب بودن آن برای workload را ثابت نمی‌کند.
- هیچ index تازه پیشنهاد/افزوده نشد؛ بدون plan، cardinality، selectivity و هزینهٔ write، index کورکورانه مجاز نیست.

## وضعیت سنجه‌ها

| سنجه | مقدار |
|---|---|
| زمان query به تفکیک route/repository، p50/p95/p99 | `NOT MEASURED` |
| query plan و actual rows/buffers | `BLOCKED`؛ DB موجود نیست |
| N+1 تأییدشده با شمار query runtime | `NOT MEASURED`؛ فقط دو الگوی مشکوک در منبع |
| زمان انتظار pool، اندازه pool و اشباع | `NOT MEASURED` |
| lock wait، deadlock و race واقعی | `NOT MEASURED` |
| index hit/miss، bloat و حجم جدول | `NOT MEASURED` |
| رفتار pagination زیر دادهٔ حجیم | `NOT MEASURED` |
| اثر درج موقعیت و batch size | `NOT MEASURED` |

## بودجه و توصیهٔ آزمون

هدف موقت query متعارف DB: p95 ≤ 100 ms، با گزارش مستقل pool wait و lock wait؛ هنوز روی بار و داده واقعی staging تأیید نشده است.

پس از فراهم شدن DB staging: snapshot/schema آزمون، داده مصنوعی غیرحساس و توزیع حجمی مستند؛ `EXPLAIN (ANALYZE, BUFFERS)` فقط برای queryهای پرمصرف منتخب؛ baseline زمان/rows/buffers؛ بررسی constraintها و race؛ سپس یک تغییر در هر مرحله و مقایسه مجدد. دادهٔ تولید کپی نشود مگر با فرایند پاک‌سازی/ناشناس‌سازی مجاز. هیچ عملیات سنگینی روی production اجرا نشود.
