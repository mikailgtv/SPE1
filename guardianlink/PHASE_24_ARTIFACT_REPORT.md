# گزارش artifact و زنجیره تأمین

**تاریخ ارزیابی:** ۶ اکتبر ۲۰۲۶  
**مرزبندی شواهد:** PASS فقط برای فرمان اجراشده؛ مواردی که محیط/دسترسی لازم ندارند BLOCKED و موارد اجرا‌نشده NOT_TESTED هستند. ادعاهای فازهای پیشین سابقه‌اند و به‌تنهایی PASS این فاز نیستند.
هیچ APK/AAB یا JAR/server release artifact تولید/امضا/تأیید نشد. Web build موفق شد اما artifact تکرارپذیر/امضاشده یا منتشرشده نیست. Git repository/metadata موجود نیست؛ provenance، commit، SBOM کامل، امضا، attestation و reproducibility تأیید نشدند. npm production audit صفر آسیب‌پذیری داشت. Hash پیکربندی‌ها (نه artifact انتشار): `web/package.json` `af7e8c1baa7b819788558e56a656f2b5778022b1e4c8ddbea8102166e3175093`; lockfile `ec07be901565c84ad7ae312de1a0f80b97d7a1aca0d81c1259da734d094bda45`. **BLOCKED**.
