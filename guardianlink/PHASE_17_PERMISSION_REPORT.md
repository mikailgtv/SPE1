# گزارش Phase 17 — مجوزها و توضیح وضعیت

## تغییرات
- Parent onboarding مرحلهٔ بازبینی را از snapshot واقعی device و پاسخ endpoint مجوزها پر می‌کند؛ values ناموجود «نامشخص/گزارش نشده» نمایش داده می‌شوند، نه فعال.
- وضعیت مکان و sharing از خروجی موجود `locationCurrent` خوانده می‌شود؛ خواندن ناموفق به حالت نامشخص می‌ماند.
- وضعیت‌های `granted`, `background_granted`, `denied`, `limited`, `restricted`, `unavailable` برای نمایش کاربرپسند map شده‌اند. قابلیت‌هایی مانند Location و Usage اختیاری و تابع رضایت کاربر و محدودیت Android معرفی می‌شوند.
- هیچ Android permission جدیدی درخواست نشد؛ مسیرهای رسمی موجود Usage Access و Location حفظ شدند. اعلان‌های عادی Child همچنان درخواست نمی‌شوند. اعلان camera/contacts/call logs یا permission جعلی اضافه نشد.
- متن Child pairing دربارهٔ نسبت نصب/مدیریت اصلاح شد؛ اقدام به اتصال را به تأیید خود کاربر و مجوزهای تفکیک‌شدهٔ Android مقید می‌کند.

## مرزهای پشتیبانی و حریم خصوصی
- این snapshot ممکن است آخرین گزارش Child باشد و real-time نیست؛ Parent آن را با آخرین sync/مشاهده جداگانه می‌سنجد.
- «ثبت device» یا اجازهٔ یک قابلیت به معنی فعال‌بودن همهٔ قابلیت‌ها نیست؛ کنترل از راه دور برای اعطای permission وجود ندارد.
- تماس‌ها، مخاطبان و دوربین از API پشتیبانی‌شده نیستند و در UI جمع‌آوری/نمایش داده نمی‌شوند.

## شواهد و محدودیت
- امضای API و ساختار خروجی permission در سورس Android/Server بررسی شد.
- resource XML تازه parse/compile نشده است؛ Android SDK/Gradle در محیط نیست. **BLOCKED:** نمایش واقعی permission در device، Android Settings، TalkBack و permission revocation.
- Backend زنده/DB و credential در دسترس نیست؛ هیچ وضعیت runtime یا permission server را ادعا نمی‌کنیم.

**نتیجه:** اصلاح کپی و نمایش source-level انجام شد؛ بررسی runtime **BLOCKED_BY_ENVIRONMENT**.