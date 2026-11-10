# گزارش فاز ۸ — مرکز اعلان‌ها و مدیریت هشدارها

**وضعیت: پیاده‌سازی اولیه انجام شده؛ بررسی ساخت، آزمون و اجرای واقعی انجام نشده است؛ فاز ۸ هنوز برای تأیید آماده نیست.**

## ۱. دامنه و نتیجهٔ بازبینی معماری

پیش از تغییر کد، مسیرهای تولید رویداد، احراز هویت، مجوز خانواده/دستگاه، API Parent و جریان اعلان Android بررسی شد.

- مسیر مشترک نوشتن بیشتر رویدادهای سرور `DeviceEventRepo.emit()` و جدول `device_events` است. رویدادهای انتقال وضعیت باتری، اتصال، مکان، محدودهٔ امن، مصرف اپ و ایمنی وب از سرویس‌های ویژگی به این جریان می‌رسند.
- رویدادهای مدیریتی/پیکربندی نیز در همین جریان ثبت می‌شوند؛ بنابراین هر رویداد خام نباید خودکار به اعلان کاربر تبدیل شود.
- Parent قبلاً در صفحهٔ Dashboard رویدادهای اخیر را poll می‌کرد و `ParentNotifier` اعلان‌های محلی می‌ساخت. این polling فقط هنگامی فعال است که برنامه/صفحه در حال اجرا باشد؛ کانال محلی Android معادل Push نیست.
- پیش از این تغییرات مرکز اعلان، وضعیت read/acknowledge ذخیره‌شده، ترجیحات اعلان، رسیدگی به لمس اعلان و Push provider/token واقعی مشاهده نشد.
- مسیرهای جدید از `requireUser` و `familyId` هویت Parent را می‌گیرند؛ شناسهٔ خانواده از درخواست پذیرفته نمی‌شود. فهرست و تغییر وضعیت با پیوند دستگاه فعال و خانواده محدود می‌شوند. دسترسی به اعلان خانوادهٔ دیگر باید 404 بدهد.

## ۲. معماری پیاده‌شده

`device_events` همچنان جریان اصلی و تاریخی رویدادهای سیستم/فعالیت باقی می‌ماند. بعد از درج موفق رویداد، `NotificationRepo` فقط زیرمجموعهٔ طبقه‌بندی‌شدهٔ رویدادهای قابل اقدام را به `family_notifications` projection می‌کند. رویداد مدیریتی که در فهرست طبقه‌بندی نیست همچنان در `device_events` باقی می‌ماند و در مرکز اعلان دیده نمی‌شود. کلید یکتای `event_id` مانع ساخت اعلان تکراری برای همان رویداد است. درج رویداد و projection در همان تراکنش انجام می‌شود.

| حوزه | تصمیم/رفتار |
|---|---|
| دسته‌ها | باتری، اتصال، مکان، محدودهٔ امن، زمان استفاده، ایمنی وب، مجوز، امنیت |
| شدت | `INFO`، `WARNING`، `CRITICAL`؛ برای مثال خاموش‌شدن حفاظت وب و آفلاین‌شدن دستگاه بحرانی‌اند |
| چرخهٔ عمر | باز، تأییدشده، حل‌شده؛ read بودن جداگانه و برای هر Parent نگهداری می‌شود |
| رفع خودکار | بازیابی باتری، بازگشت اتصال/همگام‌سازی، برقراری مجوز مکان و اعمال موفق محدودیت اپ هشدار متناظر را حل می‌کند؛ بازیابی محدودیت اپ بر اساس `package_name` محدود شده است |
| محدودهٔ امن | ورود/خروج تنها وقتی projection می‌شود که تنظیم اعلان آن گذار در metadata فعال باشد؛ رویدادهای صرفاً تاریخی/غیراعلامی باقی می‌مانند |
| زمان | زمان رویداد در سرور به‌صورت `TIMESTAMPTZ` نگهداری و در API با offset برگردانده می‌شود؛ UI زمان را در منطقهٔ زمانی دستگاه نمایش می‌دهد. بازهٔ تاریخ سمت API از منطقهٔ زمانی ذخیره‌شده در ترجیحات خانواده استفاده می‌کند (پیش‌فرض UTC). |
| جست‌وجو | روی نوع رویداد، نام دستگاه و فقط فیلدهای whitelisted از metadata (نام اپ/محدوده/کد دلیل/مجوز)؛ نه کل JSON metadata |
| صفحه‌بندی | ترتیب نزولی زمان و UUID و cursor ترکیبی برای جلوگیری از حذف رکوردهای دارای timestamp برابر؛ اندازهٔ صفحه ۱ تا ۱۰۰ |

## ۳. مدل و مهاجرت پایگاه‌داده

مهاجرت `server/src/main/resources/db/migration/V8__notification_center.sql` اضافه شده است:

- `family_notifications`: projection اعلان شامل خانواده، دستگاه، شناسهٔ رویداد، دسته، شدت، کلیدهای متن، metadata، زمان، وضعیت و زمان‌های تأیید/حل.
- `notification_user_state`: read state مستقل برای هر Parent؛ نبود ردیف یعنی خوانده‌نشده.
- `notification_preferences`: تنظیمات خانواده شامل دسته‌های فعال، ساعات سکوت، timezone، استثنای هشدار بحرانی، خلاصهٔ روزانه و cooldown.
- ایندکس feed خانواده/زمان و ایندکس جزئی اعلان‌های خوانده‌نشده.
- دادهٔ تاریخیِ نوع‌های شناخته‌شده در migration به projection تبدیل می‌شود؛ رویدادهای مدیریتیِ غیرقابل‌اقدام projection نمی‌شوند. برای رویدادهای geofence فقط metadata دارای `notify=true` وارد projection می‌شود.

مهاجرت روی PostgreSQL اجرا یا بررسی نشده است. Backfill در خانواده‌هایی با جدول event بزرگ ممکن است زمان/قفل قابل توجه ایجاد کند؛ قبل از rollout باید روی snapshot واقعی اندازه‌گیری شود.

## ۴. API اضافه‌شده

مسیرها در `NotificationRoutes.kt` زیر گروه `families/me/notifications` ثبت شده‌اند:

- `GET /v1/families/me/notifications` — فیلتر دسته، شدت، دستگاه، unread، بازهٔ تاریخ، جست‌وجو، cursor و limit.
- `GET /v1/families/me/notifications/unread-count`
- `GET /v1/families/me/notifications/{id}`
- `POST /v1/families/me/notifications/{id}/read`
- `POST /v1/families/me/notifications/read-all`
- `POST /v1/families/me/notifications/{id}/acknowledge`
- `POST /v1/families/me/notifications/{id}/resolve`
- `GET/PUT /v1/families/me/notifications/preferences`

جست‌وجو، تاریخ، UUID، دسته و شدت اعتبارسنجی می‌شوند. مرز خانواده برای دریافت/تغییر رکورد با query اعمال می‌شود؛ توکن نامعتبر/منقضی و session لغوشده همچنان طبق مسیر احراز هویت موجود رد می‌شود.

## ۵. Android Parent

- API models و متدهای `ApiClient` برای feed، جزئیات، read/acknowledge/resolve، شمارنده و ترجیحات اضافه شد.
- `NotificationCenterScreen` به Dashboard و `RootNav` متصل است و فیلتر دسته/شدت، دستگاه، تاریخ، جست‌وجو، unread، صفحه‌بندی، جزئیات و عملیات lifecycle را فراهم می‌کند.
- `ParentNotifier` اکنون projection اعلان‌های سرور را مصرف می‌کند، نه stream خام audit. کانال‌های محلی بر اساس دسته انتخاب می‌شوند. تنظیم مجوز Android، دسته‌های خاموش، ساعات سکوت با timezone ترجیح خانواده، استثنای بحرانی و cooldown ذخیره‌شده اعمال می‌شود.
- کانال‌ها فقط در اپ Parent ایجاد/استفاده می‌شوند. مجوز `POST_NOTIFICATIONS` دور زده نمی‌شود؛ اگر Android اجازه ندهد، مرکز داخل برنامه کار می‌کند و لینک تنظیمات سیستم نمایش داده می‌شود. چون کاربر ممکن است یک channel خاص را جداگانه خاموش کند، موفقیت نمایش در چنین حالتی تضمین‌شده نیست.
- انتخاب خلاصهٔ روزانه یک `WorkManager` شبکه‌محور روی دستگاه Parent زمان‌بندی می‌کند؛ خلاصه فقط در صورت داشتن اعلان خوانده‌نشده و مجوز نمایش ارسال می‌شود. اجرای زمان‌بندی‌شده به محدودیت‌های سیستم، شبکه و مجوز Android وابسته است.
- لمس اعلان محلی Intent به مرکز اعلان می‌فرستد؛ این deep-link محلی برای مسیر/جزئیات است و Push یا App Link عمومی نیست. رفتار لمس اعلان در اپِ باز/پس‌زمینه هنوز روی دستگاه بررسی نشده است.
- cursor محلی برای جلوگیری از تکرار، timestamp و شناسه دارد. اولین اجرا یک baseline می‌گذارد تا backfill تاریخی باعث موج اعلان نشود.

## ۶. Push، token و تحویل پس‌زمینه

در کد و وابستگی‌های بررسی‌شده Firebase/FCM، provider ارسال Push یا push-token دیده نشد. بنابراین در این فاز هیچ ارسال Push، ثبت/چرخش/حذف token یا ادعای تحویل پس‌زمینه پیاده‌سازی نشده است. جریان متداول اعلان محلی همچنان polling روی Dashboard باز است؛ WorkManager فقط خلاصهٔ روزانهٔ opt-in را روی دستگاه Parent بررسی می‌کند و جایگزین Push فوری/قابل‌اتکا نیست. افزودن provider واقعی، credentials، token lifecycle، ارسال امن، retry و رسید تحویل نیازمند تصمیم و زیرساخت مستقل است.

## ۷. حریم خصوصی و امنیت

- هیچ قابلیت SMS، پیام‌رسان، دوربین، میکروفون، keylogging، clipboard، ضبط صفحه، گرفتن رمز عبور، نظارت مخفی، root، exploit یا دور زدن مجوز اضافه نشده است.
- خانواده از Parent احراز هویت‌شده استخراج می‌شود؛ اعلان‌های دستگاه غیرفعال/حذف‌شده در feed پنهان می‌شوند. شناسهٔ device برای فیلتر فقط پس از تطبیق family پذیرفته می‌شود.
- جست‌وجو به فیلدهای محدود metadata مقید است تا متن خام metadata/مختصات تصادفی قابل جست‌وجو نشود. UI محتوای خام metadata را نمایش نمی‌دهد.
- `device_events` مرجع تاریخی/ممیزی باقی می‌ماند و projection اعلان جدول جداگانه دارد. این فاز actor identity را به تمام رویدادهای قدیمی اضافه نمی‌کند؛ سابقهٔ Parent موجود در metadata به‌صورت قابل اتکا برای همهٔ تولیدکننده‌ها تضمین نشده است.
- اعلان‌ها و تاریخچه در این فاز سیاست پاکسازی/retention جدید ندارند؛ باید پیش از rollout production تعریف و زمان‌بندی شود.

## ۸. آزمون‌ها و وضعیت کیفیت

فایل آزمون integration جدید `server/src/test/kotlin/ir/guardianlink/server/Phase8Test.kt` قراردادهای زیر را اضافه می‌کند:

1. projection یک هشدار باتری، scope خانواده و read/acknowledge/resolve؛
2. عدم تبدیل رویداد پیکربندی geofence به اعلان؛
3. رد دسترسی خانوادهٔ دیگر.

**هیچ‌کدام اجرا نشده‌اند.** در محیط فعلی executable `gradle`، Gradle wrapper یا `kotlinc` کشف نشد؛ در نتیجه build سرور/Android، تست‌ها، بررسی migration با PostgreSQL، lint و اجرای اپ انجام نشده است. هیچ ادعای قبولی build، تست یا اجرای دستگاهی مطرح نمی‌شود.

### راهنمای تأیید پیش از پذیرش

1. در محیط دارای Gradle، اجرای تست‌های server و Android، رفع خطاها و اجرای دوبارهٔ مجموعهٔ کامل.
2. روی PostgreSQL نسخهٔ production، اجرای Flyway از نسخهٔ قبلی، بررسی backfill، unique/FK/index و rollback/backup plan.
3. تست دو خانواده و چند Parent، session لغوشده، دستگاه حذف‌شده، ID نامعتبر، read state مستقل، cursor در timestamp مساوی، timezone، DST، جست‌وجو و limit.
4. تست Android با اجازهٔ اعلان پذیرفته/ردشده، channel خاموش، محدودیت باتری/background، شبکهٔ قطع/وصل، ساعات سکوت نیمه‌شب و تنظیمات DST، cooldown، خلاصهٔ روزانه، tapping notification در حالت cold/warm.
5. اندازه‌گیری latency feed/unread-count/backfill و تعریف retention و محدودیت نرخ.
6. بازبینی privacy/security و تأیید رسمی کاربر پیش از شروع فاز ۹.

## ۹. محدودیت‌ها و موارد باز

- اعلان‌های محلی فوری تنها تا زمان/محدودهٔ polling و اجراشدن برنامه قابل اتکا هستند؛ Push نداریم.
- feed polling روی Dashboard باز محدود به آخرین ۱۰۰ ردیف است؛ اگر برنامه مدت طولانی آفلاین باشد و بیش از ۱۰۰ اعلان ایجاد شود، بعضی اعلان‌ها ممکن است برای اعلان محلی missed شوند (در مرکز با صفحه‌بندی همچنان از سرور قابل مرورند).
- cache آفلاین محلی برای فهرست/جزئیات پیاده‌سازی نشده؛ در قطع شبکه صفحهٔ مرکز پیام به‌روزرسانی‌نشدن نشان می‌دهد و دادهٔ قبلی ممکن است فقط تا وقتی همان composable زنده است در حافظه باشد.
- daily summary، WorkManager، deep-link و lifecycle در دستگاه واقعی تأیید نشده‌اند.
- ترجیحات `daily_summary_enabled` تنها از UI Parent و worker محلی استفاده می‌شوند؛ گزارش تحویل server-side وجود ندارد.
- policy retention و actor attribution برای audit قدیمی تعریف نشده است.
- طبقه‌بندی نوع‌های رویداد باید در بازبینی انسانی با همهٔ event producerها تطبیق نهایی شود؛ رویدادهای جدید تا افزودن classifier به‌طور پیش‌فرض اعلان نمی‌شوند.

## ۱۰. فایل‌های اصلی تغییرکرده

- `server/src/main/resources/db/migration/V8__notification_center.sql`
- `server/src/main/kotlin/ir/guardianlink/server/NotificationRepo.kt`
- `server/src/main/kotlin/ir/guardianlink/server/NotificationRoutes.kt`
- `server/src/main/kotlin/ir/guardianlink/server/StatusRepos.kt`, `Auth.kt`, `Main.kt`
- `server/src/test/kotlin/ir/guardianlink/server/Phase8Test.kt`
- `android/core/network/.../ApiClient.kt`, `NotificationModels.kt`
- `android/feature/dashboard/.../NotificationCenterScreen.kt`, `NotificationPreferencesPanel.kt`, `ParentNotifier.kt`, `DashboardViewModel.kt`, `NotificationSummaryScheduler.kt`, `NotificationSummaryWorker.kt`
- `android/app-parent/.../RootNav.kt`

## تصمیم مرحله‌ای

فاز ۸ **پیاده‌سازی اولیه** دارد اما تا اجرای build/test/migration، بررسی روی دستگاه و بازبینی موارد باز، «تأییدشده» محسوب نمی‌شود. فاز ۹ شروع نشده و تا تأیید صریح فاز ۸ شروع نخواهد شد.
