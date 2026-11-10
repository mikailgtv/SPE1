# گزارش Phase 6 — فهرست اپ‌ها و محدودیت مصرف

**وضعیت: پیاده‌سازی اولیه انجام شده؛ آزمون build، آزمون PostgreSQL و آزمون دستگاه واقعی هنوز اجرا/تأیید نشده‌اند.** با اجازهٔ صریح کاربر، Phase 7 پیش از approval این فاز آغاز شده؛ Phase 6 همچنان unapproved است.

## خلاصهٔ قابلیت‌ها

- Workflow مرحلهٔ 3 حفظ و گسترش داده شد؛ UsageStats جدید جایگزین یا تکرار APIهای موجود نشده است.
- Child در sync موجود، تصویر metadata اپ‌های نصب‌شده را می‌فرستد (package، label، version، install time، system/category) و همان پاسخ sync نسخه و snapshot ruleها را دریافت می‌کند. inventory حدود هر ۶ ساعت، در اولین sync شبکه‌ای در دسترس، دوباره scan/sync می‌شود تا scan مکرر در هر دورهٔ ۱۵ دقیقه‌ای اضافه نشود؛ Android می‌تواند اجرای WorkManager را عقب بیندازد.
- Parent می‌تواند موجودی، مجموع مصرفِ گزارش‌شدهٔ امروز و ۷ روز، تاریخچهٔ روزانهٔ تا ۳۰ روز، ruleهای per-app، status گزارش‌شدهٔ اعمال و رویدادها را ببیند.
- Ruleها: `ALLOW`، سقف روزانه (`LIMITED`)، محدودیت پیوسته (`RESTRICTED`) و برنامهٔ هفتگی (`SCHEDULED`). schedule با ماسک ۷ روز هفته، زمان محلی دستگاه و پنجره‌های عبوری از نیمه‌شب ذخیره می‌شود.
- نسخهٔ سیاست در سطح device monotonically افزایش می‌یابد؛ update/delete دارای optimistic `expectedVersion` است و تعارض stale به HTTP 409 برمی‌گردد.
- cache نسخه‌دار ruleها و صف رویداد Child در `SecureStore` (AES-GCM با کلید Android Keystore) نگهداری می‌شود؛ cache از نسخهٔ قدیمی‌تر overwrite نمی‌شود و رویدادها پس از پاسخ موفق sync از صف حذف می‌شوند.
- usage-threshold stage در DB برای هر rule و روز ثبت می‌شود؛ عبورهای near/critical/reached برای همان روز dedupe می‌شوند. Child eventها نیز با UUID سمت کلاینت dedupe می‌شوند.
- Parent event feed و اعلان‌های Parent برای عبور از سقف مصرف و شکست واقعی DPM اضافه شده‌اند. Child notification جدیدی ساخته نمی‌شود.

## حالت اجرا و محدودیت‌های واقعی Android

### Normal Android mode

`DevicePolicyManager.isDeviceOwnerApp()` مرجع محلی برای قابلیت blocking است. اگر برنامه Device Owner نباشد:

- `setPackagesSuspended()` فراخوانی نمی‌شود؛ **هیچ اپی مسدود نمی‌شود**.
- ruleهای محدودکننده صرفاً برای Parent قابل مشاهده‌اند و Child رویداد `APP_RESTRICTION_UNSUPPORTED` می‌فرستد.
- usage alerts فقط وقتی ساخته می‌شوند که کاربر Usage Access را از Settings رسمی فعال کرده و UsageStats به sync رسیده باشد.

### Managed / Device Owner mode

برای Device Owner واقعی، Child از API رسمی `DevicePolicyManager.setPackagesSuspended()` استفاده می‌کند. فقط در صورتی که Android Device Owner بودن همین package را تأیید کند، فراخوانی انجام می‌شود. نتیجهٔ موفق/ناموفق API برای Parent گزارش می‌شود؛ Android ممکن است برخی packageهای ضروری/سیستمی را نپذیرد. این نسخه **هیچ provisioning پنهان یا ارتقای دسترسی خودکار** ندارد؛ receiver فقط برای enrollment رسمی/آزمایش سازمانی اعلام شده است. Profile Owner عمداً به‌عنوان Device Owner تلقی نمی‌شود.

## زمان‌بندی و تأخیر

- Policy worker محلی WorkManager بدون شرط شبکه زمان‌بندی می‌شود؛ بازهٔ دوره‌ای حداقل ۱۵ دقیقه است و زمان اجرای واقعی به Android، Doze، سازندهٔ دستگاه و سهمیه‌بندی WorkManager وابسته است.
- Status sync قبلی همچنان شرط اتصال شبکه دارد. thresholdهای سمت سرور پس از upload دادهٔ UsageStats محاسبه می‌شوند؛ هنگام قطع شبکه، Parent alert تا sync بعدی به تعویق می‌افتد.
- Ruleهای آخرین نسخهٔ دریافت‌شده آفلاین قابل ارزیابی‌اند؛ تغییر Parent تا زمان اتصال مجدد به دستگاه نمی‌رسد. Parent UI آخرین تماس policy و نسخه را نشان می‌دهد.
- این workflow real-time نیست و چنین ادعایی نمی‌کند. schedule هم ممکن است با تأخیر اجرا/رفع شود.

## Inventory و Usage Access: disclosure و محدودیت

- Child UI توضیح می‌دهد که Usage Access باید خود کاربر در Settings فعال کند و گزارش شامل آمار مصرف و metadata پایهٔ packageها است؛ محتوای اپ‌ها جزو مدل گزارش نیست.
- `QUERY_ALL_PACKAGES` برای inventory گسترده در manifest Child اعلام شده است؛ این مجوز حساس و مشمول بررسی/تأیید Google Play است. پذیرش Play تضمین‌شده نیست. بدون visibility گسترده، Android 11+ می‌تواند inventory را فیلتر کند؛ API وضعیت کامل بودن snapshot را جداگانه نگه می‌دارد.
- آمار UsageStats ممکن است مجوز نداشته باشد یا در گزارش Phase 3 (batch/top-app cap) برای یک روز ردیفی نداشته باشد. Parent UI به‌جای نمایش مصرف قطعی صفر، «داده‌ای گزارش نشده» نشان می‌دهد. مجموع هفتگی بر مبنای داده‌های موجود است و ممکن است ناقص باشد.
- inventory فقط metadata package است، نه icon یا محتوای اپ. packageهای حذف‌شده تا ۳۰ روز با `installed=false` می‌مانند و سپس هنگام snapshot بعدی پاک می‌شوند. تاریخچهٔ app restriction/threshold حداکثر ۹۰ روز نگهداری و هنگام sync پاک‌سازی می‌شود.
- inventory نصب‌شده دادهٔ حساس است؛ production باید DB volume و backup را رمزگذاری و دسترسی عملیاتی را محدود کند. TLS و رمزگذاری storage را این sandbox اثبات نمی‌کند.

## APIهای Phase 6

### Child

- `POST /v1/device/sync` — inventory/capability/queued events در کنار Usage و Location؛ rules/version در `SyncOut.appManagement`.
- `GET /v1/device/app-rules` — snapshot rule و نسخه.
- `POST /v1/device/app-management/sync` — sync مستقل/اختیاری همان metadata/events؛ مسیر معمول نیاز ندارد درخواست دیگری بسازد.

### Parent (مالکیت خانواده/device الزامی)

- `GET /v1/families/me/devices/{id}/apps?includeUninstalled=false`
- `GET /v1/families/me/devices/{id}/apps/{packageName}/usage?days=7`
- `GET /v1/families/me/devices/{id}/app-rules`
- `POST /v1/families/me/devices/{id}/app-rules`
- `PUT /v1/families/me/devices/{id}/app-rules/{ruleId}` (با `expectedVersion`)
- `DELETE /v1/families/me/devices/{id}/app-rules/{ruleId}?expectedVersion=...`
- `GET /v1/families/me/devices/{id}/app-events?limit=100`

شناسهٔ Child فقط از session token خوانده می‌شود؛ Parent از همان `parentDevice()` و family ownership استفاده می‌کند. rule نمی‌تواند به packageای که در inventory آن device نصب نیست متصل شود. ورودی package، threshold، timezone، schedule و event با validation محدود شده‌اند.

## Database

Migration جدید `server/src/main/resources/db/migration/V6__phase6_app_management.sql` جدول‌های `app_inventory`، `app_management_state`، `app_rules`، `app_rule_schedules`، `app_rule_usage_state` و `app_restriction_events` را اضافه می‌کند. Ruleها یکتای device/package هستند؛ حذف rule schedule و threshold state را cascade می‌کند و event history با `rule_id=NULL` می‌تواند باقی بماند.

## Verification انجام‌شده در این turn

- تست‌های واحد و integration جدید نوشته شده‌اند: threshold boundary، schedule روزانه/عبوری از نیمه‌شب، authorization، inventory، CRUD/version conflict، dedupe رویداد Child و عبور thresholdها.
- XML منابع Android با `xml.etree.ElementTree` parse شد: **موفق**.
- اجرای Gradle ممکن نشد: workspace فاقد `gradlew` است و `gradle` نیز نصب نیست؛ بنابراین نه Kotlin compile/test و نه Android assemble اجرا نشده‌اند.
- Docker، `psql` و ابزار دستگاه Android در محیط موجود نبودند/در دسترس تأیید نشدند؛ PostgreSQL integration و تست فیزیکی اجرا نشده است. هیچ نتیجهٔ build/device test ادعا نمی‌شود.

## راهنمای آزمون فیزیکی (هنوز Pending)

فهرست کامل و check-list در `docs/PHASE6_DEVICE_TEST_GUIDE.md` است. حداقل آزمون پذیرش قبل از approve: دو device واقعی Parent/Child برای Normal mode، Usage Access on/off، inventory install/uninstall، rule CRUD و conflict، threshold dedupe، schedule عبوری از نیمه‌شب، قطع شبکه و بازگشت؛ سپس یک دستگاه آزمایشگاهی پاک‌شده و واقعاً provisionشده به Device Owner برای DPM success و packageهای غیرقابل تعلیق. دستگاه شخصی را برای آزمایش Device Owner استفاده نکنید.

## تصمیم مرحله‌ای

**Phase 6 هنوز BUILD → TEST → FIX → APPROVE کامل نشده است.** تا اجرای Gradle، PostgreSQL integration، آزمون واقعی روی Android و بازبینی گزارش، این مرحله approved نیست. کار Phase 7 با اجازهٔ صریح کاربر پیش از approval فاز ۶ شروع شده است؛ این اجازه وضعیت آزمون/approval فاز ۶ را تغییر نمی‌دهد.

### اصلاح تکمیلی در ادامهٔ کار

Rule هدف‌گیرندهٔ `ir.guardianlink.child` (`AGENT_PACKAGE`) اکنون در validation رد می‌شود و برای آن assertion integration افزوده شده است. این اصلاح هنوز اجرا/کامپایل نشده؛ نتیجهٔ Phase 6 همچنان Pending است.
