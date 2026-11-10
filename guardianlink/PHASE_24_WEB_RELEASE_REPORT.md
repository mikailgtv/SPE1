# گزارش انتشار Web

**تاریخ ارزیابی:** ۶ اکتبر ۲۰۲۶  
**مرزبندی شواهد:** PASS فقط برای فرمان اجراشده؛ مواردی که محیط/دسترسی لازم ندارند BLOCKED و موارد اجرا‌نشده NOT_TESTED هستند. ادعاهای فازهای پیشین سابقه‌اند و به‌تنهایی PASS این فاز نیستند.
`npm ci --ignore-scripts` موفق شد؛ 161 package نصب شد. `npm run build` موفق: TypeScript و Vite، 90 ماژول، خروجی build گزارش‌شده 35.46 KiB CSS و 385.93 KiB JS (gzip 7.61/118.12 KiB). `npm audit --omit=dev --audit-level=high` صفر آسیب‌پذیری production گزارش کرد. نسخه در `package.json` و lockfile برابر 0.1.0-rc.1 است.

این شواهد build وب‌اند، نه runtime/UI E2E، مرورگرهای هدف، accessibility، API integration یا امنیت deployment. خروجی `web/dist` تولید محلی است و artifact انتشارِ امضاشده/قابل‌اعتماد محسوب نمی‌شود. موارد مذکور NOT_TESTED.
