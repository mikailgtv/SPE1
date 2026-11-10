# گزارش Phase 17 — Offline و بازیابی Sync

## آنچه تغییر کرد
- Parent Android FamilyGate خطای شبکه/API را با حالت خطا و Retry از «نبود خانواده» جدا می‌کند؛ کاربر آفلاین به مسیر ساخت خانواده هدایت نمی‌شود.
- wizard در شکست fetch پیام می‌دهد که snapshot تازه دریافت نشده و دادهٔ نمونه نمایش نمی‌دهد؛ Retry، پاسخ‌های واقعی را دوباره می‌خواند.
- Web health indicator از `/health/live` به `/health/ready` تغییر کرد تا آمادگی Backend (از جمله وابستگی‌های readiness موجود) با زنده‌بودن process اشتباه نشود.
- Web راه‌اندازی خالی از پاسخ واقعی `/devices` تشخیص داده می‌شود؛ checklist ادعای اتصال ندارد و اقدام pairing به صفحهٔ واقعی Devices هدایت می‌کند.

## رفتار حفظ‌شده/محدودیت‌ها
- Web، cache دادهٔ API برای استفادهٔ آفلاین ندارد؛ فقط offline shell موجود حفظ شده است. query retry خودکار صفر است و error/retry محلی صفحه باقی است.
- Android WorkManager و صف‌های موجود تغییر نکردند. retry خودکار/API queue برای Parent ایجاد نشد و دادهٔ pending ساخته یا مخفیانه queue نشد.
- device snapshot قدیمی ممکن است در صفحات قبلی باقی بماند؛ یک stale age indicator سراسری اضافه نشده است.
- startup RootNav در برخی خطاهای عمومی هنوز می‌تواند به Dashboard برسد؛ اصلاح/تست recovery کامل در device لازم است.

## راستی‌آزمایی
- `npm test`: ۲ فایل، ۴ تست موفق.
- `npm run build`: TypeScript و Vite production build موفق.
- **BLOCKED:** قطع/وصل واقعی شبکه، WorkManager/Doze، فرآیند kill/reboot، DB readiness واقعی، sync recovery و crash recovery به‌دلیل نبود Backend/DB، emulator/device و Docker.

**نتیجه:** Web build ثابت می‌کند کد کامپایل می‌شود؛ offline recovery واقعی اثبات نشده و **BLOCKED** است.