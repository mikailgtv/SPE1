# گزارش انتشار Backend

**تاریخ ارزیابی:** ۶ اکتبر ۲۰۲۶  
**مرزبندی شواهد:** PASS فقط برای فرمان اجراشده؛ مواردی که محیط/دسترسی لازم ندارند BLOCKED و موارد اجرا‌نشده NOT_TESTED هستند. ادعاهای فازهای پیشین سابقه‌اند و به‌تنهایی PASS این فاز نیستند.
**نتیجه: BLOCKED.** نسخه Gradle به `0.1.0-rc.1` تنظیم شد. سه hardening تغییر در کد/پیکربندی وارد شده اما کامپایل و runtime آن‌ها تأیید نشده‌اند: محدودیت بدنه Ktor مسیرمحور (عمومی 1 MiB، app management 4 MiB، sync و usage 12 MiB) با پاسخ 413؛ timeout خواندن/نوشتن Netty هرکدام 30 ثانیه؛ و timeout پرس‌وجوی JDBC سی ثانیه. این timeoutهای Netty شبکه را پوشش می‌دهند، نه deadline پردازش اپلیکیشن. بازه تاریخچه location با دو سر مشخص به حداکثر 31 روز محدود شده.

Backend test/build اجرا نشد: `./gradlew` وجود ندارد، Gradle system موجود نیست و Java 11 است. وابستگی `ktor-server-body-limit-jvm` و مسیر import/exception/status در این پروژه تأیید کامپایل نشده‌اند. بنابراین این تغییرات هنوز **نامزد تأییدنشده** هستند؛ قبل از merge/release باید build و تست 413 برای بدنه declared و streaming، رفتار مسیرها، timeout JDBC/Netty و boundary تاریخچه انجام شود. داده‌های production را برای این تست‌ها استفاده نکنید.
