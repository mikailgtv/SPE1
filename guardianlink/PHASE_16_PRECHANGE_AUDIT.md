# Phase 16 — ممیزی پیش از هر تغییر کد

**زمان/دامنه:** baseline ممیزی ایستای source فعلی Android Parent، Android Child، Web، Backend، migrations و deploy config در 2026-10-03، پیش از patchهای Phase 16. عبارت «هیچ کدی تغییر نکرده» به وضعیت همان زمان ثبت اشاره دارد؛ پس از ثبت ممیزی دو اصلاح محدود در `UsageService.kt` و `StatusSyncWorker.kt` اعمال شد و هنوز build/test نشده‌اند.

## مسیر دادهٔ پایه

```text
Android Parent ─┐
Android Child ──┼── HTTPS /v1 ──> Ktor Backend ──> PostgreSQL (مهاجرت‌های V1–V9)
Parent Web ─────┘                  │
                                   └── رویدادهای دامنه و اعلان‌ها در همان DB
```

- Android Parent، Android Child و Web به endpointهای `/v1` یک Backend مشترک متصل‌اند. Backend با Repositoryهای سرور PostgreSQL را منبع دادهٔ پایه می‌گیرد.
- Caddy در پیکربندی edge فایل‌های buildشدهٔ Web را میزبانی و `/v1` و `/health` را به همان Backend proxy می‌کند. مسیر Web در Vite نیز برای development proxy دارد. runtime این زنجیره در این محیط اجرا نشده است.
- Web token را فقط در حافظهٔ tab نگه می‌دارد و `/preview` مسیر UI-only است؛ هیچ‌کدام راه دسترسی به دادهٔ محافظت‌شده نیستند. Service Worker فقط shell آفلاین را cache می‌کند و `/v1` را صریحاً از interception خارج می‌کند.

## هویت، خانواده و pairing

- Parent با access JWT نوع `user` و Child با access JWT نوع `device` احراز هویت می‌شوند. درخواست‌های authenticated، session فعال/لغونشده را در DB بررسی می‌کنند.
- هر دو Parent Android و Parent Web مسیر ساخت pairing session را دارند؛ Child با code یک‌بارمصرف exchange و سپس confirm می‌کند. code/token/secret در سرور به‌صورت hash نگهداری می‌شوند؛ exchange/confirm از تراکنش و row lock استفاده می‌کنند. pairing واقعی در runtime آزمایش نشده است.
- کنترل مالکیت در routeهای Parent از خانوادهٔ همان کاربر و device متعلق به آن خانواده انجام می‌شود؛ درخواست device متعلق به خانوادهٔ دیگر در کد به `404` می‌رسد. RLS دیتابیس در migrationهای بررسی‌شده پیدا نشد.

## قراردادی که بررسی شد

- Backend Jackson، Android Gson و Web API client از قرارداد snake_case استفاده می‌کنند؛ Web ورودی را recursively به camelCase و خروجی را به snake_case تبدیل می‌کند. خطای Backend `{code,message}` است و Web/Android آن را به خطای client نگاشت می‌کنند.
- OpenAPI تولیدشده یا قرارداد مشترک codegen‌شده و contract test سراسری دیده نشد.
- Event projection به اعلان در مسیر همزمانِ همان تراکنش/اتصال DB انجام می‌شود؛ processor جداگانه، outbox عمومی، SSE و server-side FCM در پیاده‌سازی بررسی‌شده وجود ندارد. health endpoint، push را `not_configured` گزارش می‌کند.

## قابلیت‌های Child و حدود واقعی

- sync وضعیت با WorkManager و حداقل فاصلهٔ دوره‌ای ۱۵ دقیقه، یک‌بار هنگام شروع/بازگشت شبکه، و محدودیت اتصال دارد. موقعیت با تک‌شات رسمی Android/Google Fused API گرفته می‌شود؛ مجوز و provider بررسی می‌شود و loop دائمی مکان برای sync عادی وجود ندارد.
- geofence در Backend از fixهای syncشده ارزیابی می‌شود؛ Android `GeofencingClient` استفاده نمی‌کند. payload ورود/خروج ارسالی Child به‌تنهایی برای ایجاد transition معتبر نیست.
- App suspension فقط در صورت Device Owner و از `DevicePolicyManager` قابل اعمال است؛ حالت عادی `unsupported_normal_mode` گزارش می‌کند. Web Safety فقط مسیر managed Chrome policy را دارد؛ اثر واقعی policy قابل تأیید خودکار نیست و VPN/DNS filter و visit telemetry پیاده‌سازی نشده‌اند.
- Usage با `UsageStatsManager` و مجوز جداگانهٔ Usage Access خوانده می‌شود؛ این مجوز به‌خودی‌خود enforcement نیست. payload دادهٔ خوداظهاری دستگاه است و سرور حدود/مقادیر آن را اعتبارسنجی می‌کند.

## یافتهٔ ناسازگاری تقویمیِ پیش از تغییر

`UsageCollector` تاریخ‌ها و بازهٔ روز را با timezone محلی Android می‌سازد و `StatusSyncWorker` همان timezone را در `appManagement.timeZoneId` می‌فرستد. `POST /v1/device/sync` ابتدا وضعیت App Management/timezone را ذخیره و سپس usage را پردازش می‌کند؛ اما `UsageService` برای بسیاری از خواندن‌ها و ارزیابی‌های روزانه (`today`, `weekly`, `appsFor` بدون date, `appDetail`, `dailyReport`, `evaluateLimit`, `evaluateSchedules`) از `LocalDate.now()`/timezone پیش‌فرض JVM استفاده می‌کرد. `safeTs` نیز epoch و ابتدای روز را با timezone سرور تفسیر می‌کرد. بنابراین در مرز روز و وقتی timezone دستگاه با سرور متفاوت باشد، ردیف‌های uploadشده و گزارش/limit/schedule سرور ممکن است به روزهای متفاوت نسبت داده شوند. این مورد نیازمند اصلاح محدود در `UsageService` و پس از آن build/test backend است؛ اجرای آن در محیط فعلی ممکن نیست مگر Java/Gradle/Testcontainers فراهم باشد.

## یافتهٔ تکمیلی location consent قبل از اصلاحات Android

بازبینی تکمیلی `StatusSyncWorker` و `LocationService` نشان داد Worker پیش از دریافت پاسخ sync از سرور، با `prefs.getString("loc_sharing") != "off"` مکان می‌گرفت و fixهای صف‌شده را در همان `/device/sync` می‌فرستاد. این مقدار ممکن است `null`/قدیمی باشد؛ در نتیجه در اولین sync یا یک چرخه پس از خاموش‌کردن sharing، مختصات پیش از دریافت وضعیت تازه از Parent capture و در request ارسال می‌شدند. Backend فعلی در `LocationService.sync` هنگام `sharing_enabled=false` نقاط را ذخیره نمی‌کند، اما client capture/send را تضمیناً متوقف نمی‌کرد. این با comment Worker که می‌گوید هنگام خاموشی «نه fix می‌گیرد و نه ارسال می‌کند» هم‌خوان نیست. پیش از اصلاح Android ثبت شد. اصلاح هدفمند در source انجام شد: ابتدا sync بدون مختصات برای دریافت server-authoritative `locationSharing`؛ تنها در صورت `true` capture و ارسال batch از endpoint موجود `POST /v1/device/location/sync`; در `false` یا `null` صف پاک و هیچ مختصاتی ارسال نمی‌شود. Build/device validation همچنان لازم است.

## شواهد آزمون‌پذیری موجود در workspace

- Backend integration-test harness از `PostgreSQLContainer("postgres:17-alpine")` استفاده می‌کند؛ اجرای آن به Docker نیاز دارد.
- workflow CI، Java 17، Gradle 9.7.1 و Android SDK را برای build/test تعریف می‌کند؛ وجود workflow، اجرای موفق محلی/CI را اثبات نمی‌کند.
- در محیط ممیزی Java 11 موجود است؛ Gradle، wrapper، Android SDK/adb، Docker/Podman و PostgreSQL client/server پیدا نشدند.
- در این مرحله Web build/test و هیچ backend/device/runtime/E2E checkی هنوز اجرا نشده بود. فایل‌های APK زیر `artifacts/phase5` قدیمی‌اند و evidence ساخت Phase 16 نیستند.

## اسناد پیشین نیازمند اصلاح/تفسیر مجدد

اسناد قدیمی `docs/01-architecture-and-tech-stack.md`، `docs/02-project-structure.md`، `docs/03-database-schema.md` و بخش‌هایی از API/auth docs، معماری مطلوب/طرح قدیمی را به‌صورت واقعیت فعلی توصیف می‌کنند. ادعاهای SSE/Redis/FCM، Room، Proto DataStore/Tink، RLS و `audit_logs` با source فعلیِ بررسی‌شده هم‌خوان نیستند. برای Phase 16 source جاری مبناست؛ این اسناد به‌عنوان evidence اجرای قابلیت تلقی نمی‌شوند.
