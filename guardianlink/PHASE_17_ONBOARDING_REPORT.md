# گزارش Phase 17 — راه‌اندازی و Onboarding

## تغییرات منبع
- در Android Parent، صفحهٔ هفت‌مرحله‌ای `ParentOnboardingScreen.kt` اضافه و در `RootNav.kt` متصل شد: وضعیت حساب/خانواده، آماده‌سازی device، ورود به pairing موجود، بازگشت از pairing، بررسی deviceهای واقعی، مرور snapshot مجوزها و پایان.
- FamilyGate اکنون `NO_FAMILY` را از خطای شبکه تفکیک می‌کند؛ فقط 404/کد واقعی نبود خانواده به ساخت خانواده می‌رود. شکست API در این gate با پیام و Retry نمایش داده می‌شود؛ خانواده یا device ساختگی تولید نمی‌شود.
- پس از تکمیل خانواده، ورود به wizard انجام می‌شود؛ وقتی کاربر از wizard به pairing می‌رود، بازگشت به مرحلهٔ بررسی حفظ می‌شود. polling فقط در مرحلهٔ verify است و هر ۸ ثانیه فهرست واقعی API را می‌خواند.
- صفحه فقط `me`, `familyMe`, `devices`, `permissions` و `locationCurrent` موجود را می‌خواند. وضعیت مجوز از snapshot دریافتی می‌آید؛ صفحه مجوز جدید درخواست یا تنظیم Android را از راه دور فعال نمی‌کند.
- Parent Web در Overview، وقتی API فهرست خالی واقعی برمی‌گرداند، checklist راه‌اندازی و لینک صفحهٔ Devices/Pairing دارد. refresh فقط queryهای واقعی را دوباره می‌خواند.
- متن نادرست Child pairing دربارهٔ «نصب و مدیریت‌شدن توسط والد» اصلاح شد؛ متن جدید اتصال را به اقدامِ تأییدشدهٔ خود کاربر و داده‌های صریحاً گزارش‌شده محدود می‌کند.

## راستی‌آزمایی
- جست‌وجوی امضاها تأیید کرد `permissions(id): Map<String, Any>`، `devices(): List<DeviceOut>` و DTOهای مکان مورد استفاده در سورس وجود دارند؛ پاسخ permission در سرور به شکل `{deviceId, permissions}` است.
- اسکریپت ایستأ نهایی: تمام ۵۷ ارجاع `R.string` در Parent Android منبع متن دارند؛ XML منابع parse شد و ۵۸ نام در هرکدام از `values` و `values-en` هم‌تراز و بدون تکرار بود.
- `npm test` و `npm run build` برای Web موفق شدند؛ این‌ها رفتار runtime/API و pairing حقیقی را اثبات نمی‌کنند.
- **BLOCKED:** Gradle/wrapper، Android SDK و adb در محیط موجود نیست؛ wizard هنوز با Android compiler یا دستگاه اجرا نشده است. Backend و credentials هم در دسترس نیستند؛ مسیر end-to-end واقعی انجام نشد.

## باقی‌مانده/تصمیم
- Wizard در Parent Android از نظر source به navigation وصل است، اما تا build و اجرای واقعی نباید «تأییدشده/کارا» تلقی شود.
- راه‌اندازی مجدد device حذف‌شده، account recovery و ثبت خانواده از Web در مرورگر واقعی تست نشده‌اند. backend برای password reset/delete/export قابلیت ارائه نمی‌کند؛ UI جعلی اضافه نشده.
- **وضعیت:** پیاده‌سازی منبع انجام‌شده؛ راستی‌آزمایی Android و journey واقعی **BLOCKED**.