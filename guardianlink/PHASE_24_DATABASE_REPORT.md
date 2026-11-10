# گزارش پایگاه‌داده

**تاریخ ارزیابی:** ۶ اکتبر ۲۰۲۶  
**مرزبندی شواهد:** PASS فقط برای فرمان اجراشده؛ مواردی که محیط/دسترسی لازم ندارند BLOCKED و موارد اجرا‌نشده NOT_TESTED هستند. ادعاهای فازهای پیشین سابقه‌اند و به‌تنهایی PASS این فاز نیستند.
Migrationهای منبع تا V9 و دیتابیس تست تاریخی Phase 23 روی V9 گزارش شده‌اند؛ این ارزیابی migration زنده اجرا نکرد. اتصال PostgreSQL، migration، rollback، constraint/index، تراکنش، isolation بین خانواده/دستگاه، query plans و integrity در این نوبت **NOT_TESTED** هستند. هیچ migration یا تغییر schema ایجاد نشد.

تغییر helperهای JDBC مقدار `queryTimeout=30` را به سه مسیر query/update/returning اضافه می‌کند؛ به‌دلیل نبود اجرای Backend/DB، کامپایل و اثر روی driver تأیید نشده. timeout دیتابیس جایگزین سقف کار/کنترل cancellation نیست. **دروازه DB: BLOCKED.**
