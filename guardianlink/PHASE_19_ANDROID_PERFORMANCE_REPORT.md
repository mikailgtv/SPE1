# گزارش کارایی Android — فاز ۱۹

**وضعیت:** `BLOCKED_BY_ENVIRONMENT`; build، startup و runtime performance اندازه‌گیری نشد.

## محیط

JDK موجود 11 است؛ Android نیازمند ابزار/build مناسب است. Gradle executable/wrapper، Android SDK، adb، device و emulator موجود نیستند. در نتیجه هیچ ادعای compile، تست دستگاه فیزیکی، startup، profiler، StrictMode یا memory capture در این گزارش وجود ندارد.

## ممیزی کد منبع

- `MainActivity.onCreate` بلافاصله Compose `RootNav` را نصب می‌کند؛ از این مشاهده نمی‌توان زمان startup یا کار main thread را نتیجه گرفت.
- `GlChildApp`/`GlParentApp` نقاط آغاز ممیزی startup هستند؛ Android runtime قابل اجرا نبود.
- صف موقعیت محلی encrypted و محدود به 240 نقطه است؛ صف‌های event نیز در منبع محدود شده‌اند. زمان رمزنگاری، serialization، حجم حافظه و GC اندازه‌گیری نشده‌اند.
- NetworkWatcher پس از دسترسی شبکه یک sync را زمان‌بندی می‌کند؛ ازدحام/هم‌زمانی، retry و کار روی main thread در دستگاه آزموده نشد.
- هیچ تغییر در permission، foreground/background behavior، location cadence یا Android policy اعمال نشد.

## ماتریس

| سنجه | پیش از بهینه‌سازی | پس از بهینه‌سازی |
|---|---|---|
| cold/warm launch و time-to-first-frame | `NOT MEASURED` | `NOT MEASURED` |
| main-thread stalls / frame jank | `NOT MEASURED` | `NOT MEASURED` |
| Java/Kotlin heap، native، GC | `NOT MEASURED` | `NOT MEASURED` |
| CPU و مصرف حافظه در idle/active | `NOT MEASURED` | `NOT MEASURED` |
| API latency/sync duration در Android | `NOT MEASURED` | `NOT MEASURED` |
| compile/unit/instrumentation روی Android | `BLOCKED` | `BLOCKED` |

## معیار پیشنهادی و اقدام

هدف موقت: cold start p95 ≤ 2.5 s روی دستگاه مرجع و نسخه OS ثبت‌شده؛ هیچ I/O شبکه/DB سنکرون روی main thread؛ حافظه باید با پروفایل مرجع و lifecycleهای ثبت‌شده سنجیده شود. این‌ها بودجه/هدف‌اند، نه نتیجه.

برای ادامه Android SDK، Gradle/JDK 17، emulator یا دستگاه آزمایشگاهی با رضایت، build variant غیرتولیدی، profiler و تست‌های تکرارپذیر لازم است. اندازه‌گیری battery/background باید permission و OS policy واقعی را رعایت کند؛ هیچ polling مخفی یا دورزدن محدودیت انجام نشود.
