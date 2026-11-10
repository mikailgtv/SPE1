# گزارش Phase 17 — دسترس‌پذیری

## تغییرات source-level
- نشانگر مراحل wizard در Parent Android برای screen reader دارای نام مرحله، شماره از کل مراحل و state جاری/تکمیل‌شده/بعدی است؛ glyph تنها تکیه‌گاه معنا نیست.
- کارت آمادگی Web از فهرست مرتب معنایی، عنوان‌های واضح، CTA و دکمهٔ تازه‌سازی استفاده می‌کند؛ ساختار با همان کامپوننت‌های native قابل صفحه‌کلید موجود است.
- Child SOS و وضعیت Web Safety از string resource می‌آیند و labelهای status/reason در فارسی/انگلیسی به‌صورت resource به UI وصل شدند.
- حالات خطا و retry از `role="alert"` موجود Web و رنگ‌بندی Material در Android پیروی می‌کنند؛ هیچ مجوز دسترس‌پذیری Android برای کنترل/مانیتورینگ درخواست نشده است.

## آنچه بررسی شد
- Source دارای skip link، semantic nav landmarks، labelهای فرم و media query کاهش حرکت Web بود؛ wizard نشانگر پیشرفت و CTA واضح دارد.
- Web production build و type-check موفق است.

## موارد تأییدنشده
- **BLOCKED:** مرورگر قابل اجرا و ابزار axe/صفحه‌خوان در دسترس نیست؛ keyboard-only، focus order، contrast، zoom 200/400٪، RTL، TalkBack، بزرگ‌نمایی فونت، switch access و reduced motion روی UI نهایی اجرا نشدند.
- Android build انجام نشده؛ API سطح semantics و resourceها صرفاً از source بررسی شده‌اند.

**نتیجه:** بهبودهای منبعی موجودند؛ انطباق WCAG یا دسترس‌پذیری device را ادعا نمی‌کنیم.