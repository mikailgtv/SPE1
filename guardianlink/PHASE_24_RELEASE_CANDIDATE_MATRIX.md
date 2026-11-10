# ماتریس وضعیت نامزد انتشار

**تاریخ ارزیابی:** ۶ اکتبر ۲۰۲۶  
**مرزبندی شواهد:** PASS فقط برای فرمان اجراشده؛ مواردی که محیط/دسترسی لازم ندارند BLOCKED و موارد اجرا‌نشده NOT_TESTED هستند. ادعاهای فازهای پیشین سابقه‌اند و به‌تنهایی PASS این فاز نیستند.
| حوزه | وضعیت | شواهد/مانع |
|---|---|---|
| نسخه‌بندی | PASS محدود | نسخه هدف 0.1.0-rc.1؛ Android code=2 در تنظیم پیش‌فرض. فقط فایل‌های پیکربندی بررسی شدند. |
| Web build | PASS | `npm ci --ignore-scripts` و `npm run build` موفق؛ Vite 7.3.6، 90 ماژول. |
| وابستگی Web | PASS محدود | `npm audit --omit=dev --audit-level=high`: صفر آسیب‌پذیری؛ ارزیابی تمام زنجیره/تولید SBOM انجام نشده. |
| Backend build/tests | BLOCKED | wrapper موجود نیست؛ Gradle نصب نیست؛ محیط Java 11. تغییرات Backend پس از آخرین بیلد قابل‌کامپایل‌بودنشان اثبات نشده. |
| Android build/runtime | BLOCKED | wrapper، JDK 17، SDK/device/ADB معتبر در دسترس نیست؛ APK تولید/تأیید نشده. |
| API/DB/runtime/security | NOT_TESTED / BLOCKED | اجرای API و DB این نوبت ممکن نشد؛ احراز هویت، خانواده/دستگاه، migration و integrity مجدداً تأیید نشدند. |
| E2E و قابلیت‌های عملیاتی | NOT_TESTED | browser UI، offline، notification/FCM، SOS، export/delete و backup/restore زنده اجرا نشدند. |
| استقرار و مقیاس | BLOCKED | توپولوژی واقعی نامعلوم؛ rate limiter فرایندمحور است و برای چند replica معتبر فرض نمی‌شود. |
| تصمیم | **NO-GO** | چندین دروازه لازم مسدود/اثبات‌نشده؛ انتشار یا deployment تولیدی مجاز نیست. |
