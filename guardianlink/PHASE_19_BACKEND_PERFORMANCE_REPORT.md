# گزارش کارایی Backend — فاز ۱۹

**وضعیت:** `BLOCKED_BY_ENVIRONMENT` | latency، throughput و نرخ خطا: `NOT MEASURED`.

## دامنه

گروه‌های مورد نظر: احراز هویت/نشست، خانواده و دستگاه‌ها، اعلان‌ها، موقعیت و geofence، رخدادهای sync، محدودیت برنامه، web safety و SOS. هیچ API runtime، پروفایل، بار مصنوعی یا request trace در این محیط ثبت نشده است. Backend نیز با JDK 17 ساخته/اجرا نشده؛ فقط JDK 11 حاضر بود و Gradle موجود نبود.

## یافته‌های منبع (بدون ادعای زمان)

- فهرست دستگاه‌ها در `UserRoutes` از `deviceOut` برای هر ردیف استفاده می‌کند؛ `deviceOut` فراخوانی `refreshHealth` دارد. این مسیر مستحق trace query در staging است تا هزینهٔ هر دستگاه و امکان تجمیع امن روشن شود.
- صفحه SOS در Web برای هر device یک درخواست تاریخچه می‌فرستد (`Promise.all`)، یعنی تعداد درخواست‌های تاریخچه متناسب با تعداد دستگاه‌هاست. اندازه خانواده و زمان پاسخ ثبت نشده؛ bottleneck فقط نامزد است.
- `LocationRepo.insertPoints` insertها را در یک transaction اما در حلقه انجام می‌دهد؛ هیچ benchmark و مقایسه با JDBC batch وجود ندارد. تغییر ندادیم.
- endpointهای چندبخشی باید با instrumentation موجود API/DB در Phase 18 و logهای redacted بررسی شوند؛ هیچ دادهٔ اجرای آن instrumentation در دسترس این ممیزی نبود.
- روی احراز هویت، مجوز خانواده/دستگاه، rate limit یا payloadهای SOS/location تغییری اعمال نشد.

## جدول سنجه‌ها

| گروه | p50 / p95 / p99 | throughput | 5xx / timeout | payload | نتیجه |
|---|---|---|---|---|---|
| Auth/session | `NOT MEASURED` | `NOT MEASURED` | `NOT MEASURED` | `NOT MEASURED` | DB/runtime/staging مسدود |
| Family/device | `NOT MEASURED` | `NOT MEASURED` | `NOT MEASURED` | `NOT MEASURED` | fan-out و refreshHealth نیازمند trace |
| Notifications | `NOT MEASURED` | `NOT MEASURED` | `NOT MEASURED` | `NOT MEASURED` | pagination در منبع، نه آزموده |
| Location/geofence | `NOT MEASURED` | `NOT MEASURED` | `NOT MEASURED` | `NOT MEASURED` | batch insertion نامزد profiling |
| Sync/policy/events | `NOT MEASURED` | `NOT MEASURED` | `NOT MEASURED` | `NOT MEASURED` | سنجش runtime موجود نیست |
| SOS | `NOT MEASURED` | `NOT MEASURED` | `NOT MEASURED` | `NOT MEASURED` | هیچ event واقعی/مصنوعی اجرا نشد |

## بودجه/مقایسه

اهداف موقت: API خواندنی p95 ≤ 500 ms، p99 ≤ 1,500 ms؛ خطای 5xx < 0.5% در بار نامی توافق‌شده. این اهداف measured نیستند. مقدار پس از بهینه‌سازی وجود ندارد؛ تغییری انجام نشد؛ مقایسه ممکن نیست.

## گام لازم

روی staging، با داده ساختگی و هیچ اعلان/رخداد اضطراری واقعی، برای هر گروه request mix و اندازه payload تعریف شود؛ trace زمان API/DB/pool و 5xx با داده حساس حذف‌شده گرفته شود. هر بهینه‌سازی تنها پس از شناسایی hot path و آزمون regression مجاز است.
