# گزارش فاز ۹ — SOS و سامانهٔ اضطراری

**وضعیت در زمان تهیهٔ گزارش: پیاده‌سازی اولیه، تأییدنشده؛ build/test/migration/device در انتظار بودند.** در آغاز درخواست Phase 10، کاربر Phase 9 را صریحاً پذیرفت **با وجود موارد باز و اجرا‌نشده** تا فاز ۱۰ آغاز شود. این پذیرش به معنی قبولی build/test/migration/device نیست؛ هیچ‌یک از آن دروازه‌ها به‌صورت پس‌نگر PASS محسوب نمی‌شوند. Phase 10 اکنون در جریان است.

## خلاصهٔ صادقانه

قابلیت SOS به‌صورت جریان درون‌برنامه‌ای Child → سرور → Parent پیاده‌سازی شده است. SOS مستقل از مجوز موقعیت قابل فعال‌سازی است؛ موقعیت و ردیابی اختیاری‌اند و وضعیت محدود/ناموجود باید صادقانه نمایش داده شود. آفلاین‌بودن در Child صف محلی رمز‌شده می‌سازد، اما به Parent تحویل محسوب نمی‌شود تا زمانی که سرور sync را بپذیرد. هیچ SMS، پیام‌رسان، ایمیل، تماس با خدمات امدادی یا ادعای تماس با اورژانس اضافه نشده است.

**هیچ‌یک از تغییرات Phase 9 هنوز build یا اجرا نشده‌اند.** تست‌های جدید فقط نوشته شده‌اند؛ PostgreSQL migration و دستگاه واقعی نیز بررسی نشده‌اند. بررسی منبع این گزارش شامل اسناد/گزارش‌های Phase 1–8 و مسیرهای یکپارچه‌سازی مرتبط با SOS بوده، اما جایگزین بازبینی کامل خط‌به‌خط کل مخزن یا تأیید رسمی امنیت/حریم خصوصی نیست.

## بازبینی Phase 1–8 و عدم تکرار

گزارش‌های فازها، نقشهٔ راه، معماری امنیت، محدودیت‌های Android و مسیرهای Auth/Pairing، Location، notification projection، Parent navigation و Child manifest بررسی شدند. تمرکز تغییر این فاز، افزودن جریان SOS مستقل با استفادهٔ دوباره از خانواده/دستگاه، احراز هویت، `DeviceEventRepo`، مرکز اعلان Phase 8، مجوزها و `LocationQueue` موجود بود؛ سرویس مکان فقط برای ردیابی SOS زمان‌دار اضافه شد.

- مستندات Phase 1 و 2 نتایج build/test تاریخی را گزارش می‌کنند؛ این گزارش Phase 9 آن‌ها را دوباره اجرا یا مستقلاً تأیید نمی‌کند.
- گزارش Phase 5 نیز موفقیت تاریخی build سرور و APK را ذکر می‌کند، ولی تست‌های دو گوشی و DB را Pending می‌گذارد.
- گزارش‌های Phase 6 و 7 وضعیت ساخت/آزمون/دستگاه را صریحاً تأییدنشده می‌دانند.
- گزارش Phase 8 موجود روی دیسک می‌گوید فاز ۸ آمادهٔ تأیید نیست، اما سابقهٔ این گفتگو شامل تأیید صریح کاربر برای شروع Phase 9 با وجود موارد باز است. آن تأیید اجازهٔ شروع این فاز بود، نه گواه قبولی build یا test فاز ۸.
- گزارش‌های قبلی و نقشهٔ راه بین ادعاها/وضعیت‌ها تفاوت دارند؛ این گزارش، نتیجهٔ جدیدی برای آن دروازه‌ها جعل نمی‌کند.

## قابلیت‌های افزوده‌شده

### Child SOS

- ورودی SOS با نگه‌داشتن سه‌ثانیه‌ای؛ لمس کوتاه فعال‌سازی نمی‌کند. برای TalkBack، semantics دسترس‌پذیر به مرحلهٔ تأیید می‌رود.
- مرحلهٔ تأیید و شمارش معکوس سه‌ثانیه‌ایِ قابل لغو، قبل از ارسال.
- توضیح آشکار در UI که فقط Parentهای مجاز GuardianLink مطلع می‌شوند و خدمات امدادی ثالث/اورژانس تماس نمی‌گیرند.
- خاموش‌بودن Location مانع ثبت SOS نیست. مجوزهای رسمی Android، صفحهٔ تنظیمات و وضعیت مجوز هنگام بازگشت از Settings بررسی می‌شوند.
- ثبت باتری، شبکهٔ معتبر/محدود، وضعیت کلی دستگاه، آخرین نقطهٔ مجاز و دقت آن؛ دادهٔ client time در رویداد با برچسب تأخیر گزارش‌شده نگهداری می‌شود تا همگام‌سازی آفلاین از زمان دریافت سرور قابل تفکیک باشد.
- مدت ردیابی اختیاری ۰/۱۵/۳۰/۶۰ دقیقه و فاصلهٔ ۳۰/۶۰ ثانیه. ردیابی پس‌زمینه تنها در صورت مجوز لازم، اشتراک مکان فعال، provider روشن و راه‌اندازی موفق FGS مجاز است؛ در غیر این صورت `LIMITED` گزارش می‌شود.
- `SosLocationService` یک Foreground Service قابل مشاهده و با پایان زمان‌دار است و از Fused Location API رسمی استفاده می‌کند. سرویس دائمی/پنهان یا polling بی‌پایان موقعیت اضافه نشده است.
- Outbox محلی SOS با Keystore AES-GCM محافظت، محدود به ۲۴ ساعت و حداکثر ۱۸۰۰ نقطه است؛ پس از sync موفق حذف منطقی می‌شود. نتیجهٔ آفلاین تا قبل از sync «تحویل Parent تأیید نشده» نمایش داده می‌شود. لغو صف محلی با تأیید انجام می‌شود و روشن می‌گوید که Parent از درخواست sync‌نشده مطلع نمی‌شود.

### سرور و مجوزها

- migration جدید `V9__sos_emergency.sql`: session، نقاط مکانی، رویدادها، یادداشت‌ها و ترجیح انقضای خانواده.
- state machine، idempotency با `clientRequestId`، قفل/unique index برای جلوگیری از SOS باز تکراری در هر دستگاه، تاریخچه و audit trail.
- sessionها با خانواده و دستگاه مقیدند؛ مسیرهای Parent از principal احرازشده و خانوادهٔ او استفاده می‌کنند و مسیرهای Child به credential دستگاه متصل‌اند.
- acknowledge، cancel، resolve، expiration، location، network، tracking و note رویدادهای audit جدا دارند. مختصات داخل metadata رویداد/لاگ قرار نمی‌گیرد؛ نقاط در جدول مکانی جدا هستند.
- نرخ‌محدودسازی برای activation، cancel، tracking، network، location و note افزوده/استفاده شده است. Rate limiter فعلی process-local است و برای چند replica production کافی فرض نشده.
- تنظیم انقضای خودکار خانواده در بازهٔ ۱ تا ۷۲ ساعت، پیش‌فرض ۲۴ ساعت؛ Parent می‌تواند با slider مقدار را ذخیره کند. تنظیم روی جلسه‌های جدید اثر می‌گذارد. انقضای session در اولین درخواست بعدی مربوط به SOS اعمال و با `SOS_EXPIRED` ثبت می‌شود؛ worker زمان‌بندی‌شدهٔ مستقل وجود ندارد.

### Parent Emergency Center

- ورودی از Dashboard و deep link اعلان SOS؛ نمایش session/history، وضعیت/باتری/شبکه/مجوز، موقعیت و زمان آخرین نقطه، محدودهٔ امن، مسیر/نقطهٔ شروع/دایرهٔ دقت، timeline و یادداشت‌ها.
- acknowledge و resolve از Parent با actor/timestamp/event ثبت می‌شوند؛ Child می‌تواند session را لغو کند.
- رویدادها به notification projection دستهٔ `EMERGENCY` می‌روند و read state سمت سرور دارند. کانال محلی Android برای Emergency با اهمیت HIGH افزوده شده است.
- تحویل محلی notification وابسته به مجوز/تنظیمات اعلان Android و اجرای polling اپ Parent است. **FCM/Push provider پیاده نشده و تحویل حداکثر ۱۵ ثانیه یا تحویل در پس‌زمینه تضمین نمی‌شود.** Full-screen intent نیز پیاده نشده است.

## فایل‌های اصلی

- Server: `server/src/main/resources/db/migration/V9__sos_emergency.sql`؛ `SosModels.kt`، `SosService.kt`، `SosRoutes.kt`؛ `SosStateMachineTest.kt` و `Phase9Test.kt`.
- Android network: `core/network/.../SosModels.kt` و `ApiClient.kt`.
- Child: `feature/childstatus/.../EmergencyScreen.kt`، `SosLocationService.kt`، `SosOutbox.kt`، `DeviceInfo.kt`.
- Parent: `feature/dashboard/.../EmergencyCenterScreen.kt`، مسیر Dashboard/notification و notifier؛ `app-parent/.../RootNav.kt` و `app-child/.../RootNav.kt`.
- نقشهٔ SOS در hardening بعدی به `feature/dashboard/.../SosTrackMap.kt` (ترسیم محلی، بدون tile خارجی) تغییر کرد؛ نقشه‌های دیگر پروژه هنوز از `MapProvider.kt` و `OsmTileMap.kt` استفاده می‌کنند.

## آزمون و کیفیت — نتیجهٔ واقعی این دور

| دروازه | نتیجه |
|---|---|
| Gradle wrapper / executable | در workspace wrapper پیدا نشد؛ `gradle` نیز نصب نیست. بررسی مجدد انجام شد. |
| Kotlin compiler | `kotlinc` پیدا نشد. |
| build سرور / Android | اجرا نشده؛ بنابراین compile موفق ادعا نمی‌شود. |
| Unit/Integration tests | تست‌های جدید نوشته شدند ولی اجرا نشدند. |
| PostgreSQL/Flyway | `psql`/`postgres`/Docker در محیط پیدا نشد؛ V9 اجرا/validate نشده است. |
| Android SDK/ADB/دستگاه فیزیکی | build یا اجرای فیزیکی انجام نشده؛ رفتار مجوز، FGS، اعلان و Doze تأیید نشده است. |
| TLS/HTTPS release و Play review | deployment/configuration عملی بررسی نشده؛ تأیید نشده است. |
| static sanity | توازن خام آکولاد/پرانتز چند فایل تغییرکرده بررسی شد؛ این کار compile یا lint نیست. |

تست‌های اضافه‌شده در `SosStateMachineTest.kt` و `Phase9Test.kt` چرخهٔ وضعیت، idempotency، خانوادهٔ دیگر، tracking، event/dedupe/privacy، expiration و تأخیر sync را هدف می‌گیرند؛ تا اجرای واقعی، هیچ‌کدام PASS محسوب نمی‌شوند.

## موارد باز و محدودیت‌های مهم

1. **Retention:** انقضای SOS به معنی حذف تاریخچه نیست. sessionها، مختصات، یادداشت‌ها و audit تا حذف آبشاری خانواده/دستگاه می‌مانند؛ سیاست retention و purge خودکار برای دادهٔ مکان حساس تعریف/اجرا نشده است.
2. **حذف امن محلی:** حذف کلید/رکورد رمز‌شده از DataStore حذف منطقی است؛ پاک‌شدن فیزیکی بلوک‌های flash تضمین نمی‌شود و رمز مستقل per-record برای crypto-erasure پیاده نشده است.
3. **Push/latency:** مرکز Phase 8 به polling وابسته است؛ اعلان HIGH محلی ممکن است به‌علت permission/channel/background محدود شود. راه‌اندازی provider و رسید تحویل جداگانه لازم است.
4. **Tracking:** Android/OEM ممکن است سرویس را متوقف کند، مجوز تغییر کند یا provider خطا دهد. سرور پس از مشاهدهٔ تأخیر، وضعیت را `LIMITED` می‌کند؛ موقعیت زنده یا پس‌زمینه تضمین‌شده نیست.
5. **Rate limiting:** limiter حافظه‌ای برای چند نمونهٔ سرور توزیع‌شده نیست؛ پیش از rollout چند replica باید limiter مشترک/DB-backed انتخاب شود.
6. **Transport:** TLS termination و URL HTTPS محیط Release باید بازبینی و تست شود. وجود Caddy/Docker در مخزن اثبات پیکربندی deployment واقعی نیست.
7. **UI/API:** Compose signatures، DTO JSON، migration، route overloadها، deep link و state refresh هنوز compiler/integration/device verification ندارند.
8. **گزارش تأخیر sync:** زمان trigger از دستگاه می‌آید و ادعای رمزنگاری‌شده/غیرقابل‌جعل نیست؛ UI آن را «گزارش‌شده» می‌نامد، نه زمان مستقل تأییدشدهٔ سرور.

## راهنمای اجرای دروازه‌های بعدی

در محیط توسعهٔ Android/Gradle و PostgreSQL:

1. Server: `cd guardianlink/server && ./gradlew test` و رفع تمام compile/test شکست‌ها.
2. Android: از ریشهٔ `guardianlink/android`، `./gradlew :app:child:assembleDebug :app:parent:assembleDebug` و سپس تست‌های ماژول‌های network/location/childstatus/dashboard.
3. اجرای Flyway روی PostgreSQL خالی و snapshot نسخهٔ قبلی؛ بررسی constraint/index/FK، defaultهای notification preference، cascade و بازگشت از backup.
4. Integration: حالت‌های duplicate/replay، خانوادهٔ نامجاز، خانواده بدون ترجیح، ۱/۷۲ ساعت، expiration lazy، session مختومه با outbox باقی‌مانده، event notification/read، notes، 409، rate limit و timestamps نامعتبر.
5. دو دستگاه واقعی: مجوز coarse/fine/background پذیرفته/ردشده، Location خاموش، battery saver/Doze، قطع/وصل Wi‑Fi و داده همراه، FGS مجاز/ردشده، Parent notification خاموش/روشن، deep link، لغو/تأیید/حل، و تأخیر sync آفلاین. Actual را فقط پس از اجرای واقعی ثبت کنید.
6. پیش از production: retention/purge، distributed rate limit، HTTPS/TLS، Play declarations و متن disclosure را بازبینی کنید.

**تصمیم مرحله‌ای:** Phase 9 در وضعیت «پیاده‌سازی اولیه، تأییدنشده» باقی می‌ماند. هیچ Phase 10 شروع نشده است؛ منتظر اجرای build/test، رفع خطاها و تأیید صریح شما می‌مانم.
