# گزارش Phase 17 — فارسی، انگلیسی، locale و زمان

## تغییرات
- Parent Android wizard، FamilyGate و وضعیت‌های مجوز برای فارسی و انگلیسی resource دریافت کردند؛ نام‌ها و countها در دو locale برابرند.
- Child pairing copy به‌روز شد و Child Home، SOS label، Web Safety status/reason/scope از hard-coded UI به string resources فارسی/انگلیسی منتقل شدند.
- نمایش «آخرین sync» Child از الگوریتم مختص فارسی به `DateUtils.getRelativeTimeSpanString` تغییر کرد تا متن نسبی تابع locale سیستم باشد.
- timestampهای wizard Parent Android از ISO به formatter محلی‌شده با timezone دستگاه تبدیل می‌شوند. Web `formatDate` موجود locale فارسی/انگلیسی را انتخاب می‌کند و timezone پیش‌فرض مرورگر کاربر را رعایت می‌کند.

## بررسی
- XML منبع `values` و `values-en` برای Parent onboarding، Child status و pairing از نظر مجموعه نام string با هم مقایسه شدند.
- Web `npm test` و production build گذشت.

## باقی‌مانده
- کپی hard-coded قدیمی در سایر صفحات Android/Web هنوز وجود دارد؛ ترجمهٔ کامل کل محصول تضمین نشده است.
- تغییر locale/timezone دستگاه، ارقام فارسی/لاتین، daylight-saving و مرز روز در تاریخ‌های واقعی روی emulator/browser دستی تست نشده‌اند (**BLOCKED**).
- تأیید RTL، اندازهٔ متن و truncation در هر دو زبان نیازمند device/browser واقعی است.

**نتیجه:** scope فاز در مسیرهای دست‌کاری‌شده پوشش داده شد؛ localization سراسری و runtime **BLOCKED/ناقص** باقی است.