# گزارش گشت اکتشافی و اشکالات

**تاریخ ارزیابی:** ۷ اکتبر ۲۰۲۶  
**اصل شواهد:** تست واقعی کاربر فقط زمانی PASS است که اجرا، مشاهده و ثبت شود. کد/گزارش قبلی/تست خودکار به‌تنهایی UAT نیست.

## محدودیت pass

Exploratory interaction pass در browser/device ممکن نشد؛ هیچ کلیک/فرم/refresh/back-navigation واقعی مشاهده نشد. بررسی محدود source و مقایسه با evidence تاریخی فقط سرنخ می‌دهد، نه مشاهدهٔ bug در UI فعلی. خطای UX تازه یا crash گزارش نمی‌کنم.

## موارد باز بر پایهٔ evidence تاریخی (بدون بازآزمایی)

| ID | شدت | مورد | Evidence و وضعیت | اقدام |
|---|---|---|---|---|
| EXP-25-01 | P1 (release blocker) | Family export/delete در API قابل استفاده نبود | Phase23 authenticated export `404`; delete `405`; هیچ data حذف نشد؛ فاز ۲۵ بازتست نکرد | دامنه/privacy/legal review؛ سپس integration در test DB و UAT end-to-end؛ تا آن زمان blocker باز |
| EXP-25-02 | P1 (security/release gate) | اندازهٔ درخواست بزرگ در probe تاریخی محدودیت مؤثر نشان نداد | Phase23 1 MiB padded login پاسخ `401`, نه `413`; Phase24 افزودن body-limit را گزارش کرد ولی build/runtime تأیید نشده | build dependency؛ تست declared/streaming oversized و پاسخ 413؛ تا آزمون موفق unresolved |
| EXP-25-03 | P2 (نامعلوم) | UI interaction/empty/error/refresh, duplicate clicks و responsive رفتار مشاهده‌نشده | محیط browser/backend در دسترس نبود؛ این bug قطعی نیست | اجرای exploratory روی staging؛ ثبت repro قبل severity نهایی |

**P0:** نامعلوم، نه «صفر». **P1 باز/حل‌نشده:** حداقل EXP-25-01 و EXP-25-02. هیچ fix در این مرحله اعمال نشد: محیط لازم برای repro و retest وجود نداشت؛ قابلیت جدید هم در دامنه مجاز این فاز نیست.
