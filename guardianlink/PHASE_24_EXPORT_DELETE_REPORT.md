# گزارش Export و Delete

**تاریخ ارزیابی:** ۶ اکتبر ۲۰۲۶  
**مرزبندی شواهد:** PASS فقط برای فرمان اجراشده؛ مواردی که محیط/دسترسی لازم ندارند BLOCKED و موارد اجرا‌نشده NOT_TESTED هستند. ادعاهای فازهای پیشین سابقه‌اند و به‌تنهایی PASS این فاز نیستند.
در این نوبت قابلیت export/delete در runtime آزمایش نشد؛ completeness داده، مجوز مالک، مرز tenant، حذف آبشاری/وابستگان، retention، فایل قابل‌خواندن، حذف token/cache/backup و audit evidence تأیید نشده‌اند. هیچ حذف production اجرا نشد. چون تغییر schema/cascade پرریسک است، تغییری اعمال نشد. **NOT_TESTED/BLOCKED**؛ نیازمند دیتابیس disposable و معیارهای حذف تأییدشده.
