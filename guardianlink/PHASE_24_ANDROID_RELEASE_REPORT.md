# گزارش انتشار Android

**تاریخ ارزیابی:** ۶ اکتبر ۲۰۲۶  
**مرزبندی شواهد:** PASS فقط برای فرمان اجراشده؛ مواردی که محیط/دسترسی لازم ندارند BLOCKED و موارد اجرا‌نشده NOT_TESTED هستند. ادعاهای فازهای پیشین سابقه‌اند و به‌تنهایی PASS این فاز نیستند.
تنظیم نسخه Parent/Child روی `0.1.0-rc.1` و code=2 تغییر کرد؛ APK/AAB تولید یا امضا نشده است. تلاش `./gradlew :app-parent:assembleDebug :app-child:assembleDebug` با نبود wrapper شکست خورد. Java 11 موجود است؛ toolchain موردنیاز Android/SDK/device اثبات نشد. URL واقعی HTTPS و credentials امضای خارجی نیز ارائه/تأیید نشده‌اند.

Debug/release build، manifest merger، permission policy، signing verification، artifact hash، install/upgrade و Play policy checks: **BLOCKED / NOT_TESTED**. این گزارش مدرک APK یا آمادگی انتشار نیست.
