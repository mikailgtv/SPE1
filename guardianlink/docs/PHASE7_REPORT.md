# گزارش فاز ۷ — ایمنی وب شفاف و محدود به Chrome مدیریت‌شده

**وضعیت: پیاده‌سازی اولیه انجام شده؛ آزمون ساخت، یکپارچه‌سازی و دستگاه واقعی هنوز اجرا/تأیید نشده است.** این گزارش تأیید فاز ۶ نیست؛ وضعیت فاز ۶ همچنان «ساخت و آزمون‌نشده» باقی می‌ماند.

## خلاصهٔ صادقانهٔ قابلیت

- مسیر محدود و پشتیبانی‌شده: تنظیم `URLBlocklist` و `URLAllowlist` برای `com.android.chrome` از طریق `DevicePolicyManager.setApplicationRestrictions()`، فقط هنگامی که اپ Child در نقش رسمی Device Owner یا Profile Owner باشد و Chrome نصب باشد.
- این **فیلتر فقط Chrome** است؛ سایر مرورگرها، برنامه‌های مبتنی بر WebView و ترافیک کل Android را فیلتر نمی‌کند. Google نیز تصریح می‌کند که برنامه‌های Android System WebView ممکن است URL blocklist را رعایت نکنند.
- در حالت عادی Android، API مسدودسازی فراگیر پیاده نشده و وضعیت دستگاه «پشتیبانی نمی‌شود» گزارش می‌شود. VPN و DNS filtering در این نسخه پیاده‌سازی نشده‌اند؛ هیچ مجوز VPN، مسیر VPN، resolver، سرویس‌دهندهٔ رده‌بندی یا دورزدن رضایت کاربر وجود ندارد.
- پس از تنظیم، Child فقط مقادیر DPM را بازمی‌خواند. وضعیت `CONFIGURATION_APPLIED_UNVERIFIED` یعنی **تنظیم در DPM ثبت و بازخوانی شده**؛ اثربخشی داخلی Chrome به‌طور برنامه‌ای تأیید نشده و هرگز «فیلتر فعال/محافظت کامل» نمایش داده نمی‌شود. در آزمون دستگاه، `chrome://policy` باید جداگانه بازبینی شود.
- دامنه، زیردامنه، allow/block، سیاست پیش‌فرض allow-listed-only، برنامهٔ هفتگی و اجازهٔ موقت تا ۲۴ ساعت در API/ذخیره‌سازی پشتیبانی شده‌اند. `ALLOW_ALL` در schema/API یک نام سازگاری برای `BLOCK_LISTED` است: پیش‌فرض allow، اما قواعد صریح BLOCK همچنان فعال‌اند. اجرای زمان‌بندی و انقضای مجوز در حالت آفلاین به WorkManager وابسته و با تأخیر احتمالی است.
- کنترل دسته‌بندی‌ها و جایگاه ارائه‌دهنده در مدل API/داده تعریف شده است؛ هیچ منبع دسته‌بندی یا classifier پیکربندی نشده است. قواعد دسته‌بندی به‌صراحت **غیرقابل اعمال** برگردانده می‌شوند و در سیاست Chrome قرار نمی‌گیرند.
- رویدادهای ثبت‌شده فقط تغییر قاعده/سیاست، وضعیت قابلیت، خطا، و اجازهٔ موقت هستند. رویداد تلاش برای بازدید، دامنهٔ بازدیدشده، تاریخچه، URL، query، عنوان/محتوا، کوکی، فرم، گذرواژه، keystroke یا صفحه ثبت نمی‌شود. `WEB_RULE_TRIGGERED` و `WEB_CATEGORY_BLOCKED` عمداً تولید نمی‌شوند، چون Chrome این API به GuardianLink جریان رویداد مسدودسازی نمی‌دهد.
- هشدار خطا/عدم پشتیبانی به Parent ارسال می‌شود فقط اگر `alertsEnabled` روشن باشد؛ رویدادها نیز در صفحهٔ Parent قابل بازبینی‌اند. هیچ اعلان جدیدی برای Child اضافه نشده است.

## تغییرات پیاده‌سازی

### Android
- مدل‌های sync و APIهای Parent برای وضعیت، تنظیمات، قواعد دامنه، زمان‌بندی، override موقت و رویدادها.
- cache رمزگذاری‌شدهٔ offline برای snapshot سیاست و وضعیت‌های مجاز.
- `WebSafetyEngine` در Child: فقط DevicePolicyManager و Chrome managed configurations؛ بدون `VpnService`, Usage Access، Accessibility، WebView injection یا خواندن history.
- بررسی محدود package visibility فقط برای `com.android.chrome`.
- صفحهٔ Parent با کلید فعال‌سازی، سیاست پیش‌فرض، allow/block، میزبان دقیق یا دامنه و زیردامنه، زمان‌بندی هفتگی، اجازهٔ ۱۵ دقیقه‌ای، رویدادهای پیکربندی و وضعیت محدود.
- Child disclosure در صفحهٔ خانه دربارهٔ scope و عدم دریافت تاریخچه/بازدید.

### Server و داده
- مهاجرت `V7__phase7_web_safety.sql`: تنظیمات/نسخه، قواعد، زمان‌بندی، category catalog placeholder، overrideهای تاریخ‌دار و رویدادهای حداقلی با dedupe و حذف رویدادهای قدیمی‌تر از ۹۰ روز در sync.
- APIهای زیر با احراز هویت Child و بررسی مالکیت خانوادگی Parent:
  - `GET/PUT /v1/families/me/devices/{id}/web-safety`
  - `GET/POST /v1/families/me/devices/{id}/web-safety/rules`
  - `PUT/DELETE /v1/families/me/devices/{id}/web-safety/rules/{ruleId}`
  - `POST /v1/families/me/devices/{id}/web-safety/overrides` و `DELETE /v1/families/me/devices/{id}/web-safety/overrides/{overrideId}`
  - `GET /v1/families/me/devices/{id}/web-safety/events`
  - Child sync هم از `/v1/device/sync` و هم `/v1/device/web-safety/sync` قابل دریافت است.
- نرمال‌سازی دامنه با IDN/Punycode؛ پذیرش فقط میزبان عمومی HTTP(S)، بدون credential، port، path غیرریشه، query/fragment یا IP literal.
- نسخه‌بندی خوش‌بینانه، سقف ۵۰۰ قاعده و ۱۰۰ override فعال برای دستگاه، محدودیت حداکثر ۱۰۰۰ entry در policy ارسالی Chrome، dedupe event و whitelisting کدهای علت/وضعیت.

راهنمای آزمون مجاز روی دستگاه واقعی: `PHASE7_DEVICE_TEST_GUIDE.md`.

## آزمون و وضعیت تأیید

- تست‌های جدید نوشته شده‌اند:
  - `server/src/test/.../WebSafetyPolicyTest.kt`: نرمال‌سازی/IDN، رد ورودی ناامن، مرزبندی suffix دامنه، اولویت قاعده، تبدیل Chrome policy و زمان‌بندی شبانه.
  - `server/src/test/.../Phase7Test.kt`: CRUD/نسخه، schedule و temporary override، ایزوله‌سازی خانواده، گزارش وضعیت Child و dedupe؛ عدم تولید event بازدید.
- **این تست‌ها هنوز اجرا نشده‌اند.** در محیط موجود Gradle/Gradle wrapper، Kotlin compiler، Docker/DB، Android SDK، adb یا دستگاه واقعی در دسترس نبود. بنابراین migration، Kotlin compilation، تست integration و Android build همه «در انتظار» هستند.
- آزمون روی Device Owner/Profile Owner، Chrome Android، DPM readback، اثر سیاست در `chrome://policy` و پاک‌سازی سیاست نیز «در انتظار» است.
- اصلاح Phase 6 در همین تغییر: Rule هدف‌گیرندهٔ `AGENT_PACKAGE` اکنون پیش از CRUD رد می‌شود، assertion integration برای آن افزوده شده و Child guard دفاعی مانع suspend/unsuspend شدن app agent از cache/tombstone قدیمی است. این اصلاح و regressionهای Phase 6 هنوز build/اجرا نشده‌اند؛ بنابراین Phase 6 همچنان تأیید نشده است.

## محدودیت‌های شناخته‌شده و کارهای بعدی

1. ابتدا build سرور و Android، migration و دو مجموعهٔ تست Phase 7 را اجرا و هر خطا را رفع کنید.
2. روی دستگاه آزمایشیِ مجاز و پس از provisioning رسمی، سیاست‌ها را با Chrome بررسی کنید. DPM readback به‌تنهایی معیار اثر واقعی نیست.
3. اگر روزی provider دسته‌بندی یا DNS اضافه شود، باید سرویس resolver و روش دقیق enforcement، امنیت، پشتیبانی Chrome/WebView و disclosure جداگانه طراحی و آزموده شوند؛ جدول/کدهای فعلی هیچ classifier فرضی‌ای را پیاده نمی‌کنند.
4. در حال حاضر، هر ادعای «حفاظت کامل اینترنت»، VPN filtering، DNS filtering، منع دورزدن در مرورگرهای دیگر یا feed بازدید وب نادرست است.

## منابع رسمی بررسی‌شده

- Android `DevicePolicyManager`: https://developer.android.com/reference/android/app/admin/DevicePolicyManager
- راهنمای DPC و managed configurations: https://developer.android.com/work/dpc/build-dpc
- Chrome policy URLBlocklist: https://chromeenterprise.google/policies/url-blocklist/
- Chrome policy URLAllowlist: https://chromeenterprise.google/policies/url-allowlist/
- راهنمای Chrome برای allow/block و محدودیت System WebView: https://support.google.com/chrome/a/answer/7532419?hl=en
- قالب و تقدم فیلتر URLهای Chrome: https://support.google.com/chrome/a/answer/9942583?hl=en
- `VpnService` (برای روشن‌بودن اینکه VPN engine/API مجزا است): https://developer.android.com/reference/android/net/VpnService
