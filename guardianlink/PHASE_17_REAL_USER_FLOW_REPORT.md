# گزارش Phase 17 — جریان واقعی کاربر

## سناریوی منبع‌محور

1. Parent حساب موجود را احراز هویت می‌کند؛ درخواست بدون توکن/حساب محافظت‌شده به مسیر داده دسترسی نمی‌گیرد.
2. نبود خانواده فقط در صورت `NO_FAMILY` واقعی به FamilySetup می‌رود؛ خطای اتصال حالت Retry دارد.
3. پس از پاسخ موفق ساخت/خواندن Family، Android wizard نشان داده می‌شود. Web Overview وضعیت family/device را از API فعلی می‌خواند و در صورت `devices=[]` checklist و مسیر Devices را نمایش می‌دهد.
4. Parent با اقدام صریح وارد pairing موجود می‌شود. Backend باید کد کوتاه‌عمر واقعی بدهد؛ Parent آن را با Child موردنظر به اشتراک می‌گذارد.
5. Child کد را وارد می‌کند، نام device/خانواده/والد را بازبینی می‌کند و اتصال را صریحاً تأیید می‌کند. disclosure ادعای نصب اجباری/مدیریت‌شدن بی‌پشتوانه را حذف می‌کند.
6. پس از بازگشت به onboarding، Android فهرست device را از API بازخوانی می‌کند و status/sync/permission را فقط از دادهٔ واقعی نشان می‌دهد. نبود device، expiry و خطای fetch به موفقیت بدل نمی‌شوند.
7. dashboard و feature screens اطلاعات فعلی را می‌خوانند؛ امکانات unsupported/privacy/export/account recovery را ساختگی نشان نمی‌دهند.

## شواهد اجرا
- Web: `npm test` با ۴ تست موفق در ۲ فایل؛ `npm run build` با `tsc -b` و Vite production build موفق.
- بررسی امضاهای endpoint/DTOهای pairing، permissions، device و location در source انجام شد؛ هیچ mock/fake داده‌ای به E2E اضافه نشد.
- **BLOCKED:** هیچ حساب/credential، Backend زنده، DB، Android SDK/emulator/device یا browser executable فراهم نبود. هیچ fresh install، ثبت‌نام واقعی، pairing واقعی، permissions prompt، شبکهٔ واقعی، SOS واقعی یا کاربر واقعی اجرا نشده است.

## تصمیم
این سناریو فقط مسیر طراحی‌شدهٔ source است، نه E2E اثبات‌شده. پیش از تحویل به real-user test، Android build، اجرای هر دو اپ روی دو دستگاه واقعی، backend auth/family/pairing، offline recovery و accessibility دستی باید تأیید شوند.

**نتیجه:** جریان end-to-end **BLOCKED_BY_ENVIRONMENT**.