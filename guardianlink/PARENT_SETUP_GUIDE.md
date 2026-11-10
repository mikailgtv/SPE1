# راهنمای راه‌اندازی Parent — GuardianLink

## هشدار وضعیت نسخه

تا پایان Phase22 هیچ نسخهٔ Production منتشر یا تأیید نشده است (`DEPLOYMENT_BLOCKED`). بنابراین لینک دانلود، URL عملیاتی، حساب آزمایشی یا نسخهٔ نصب معتبر وجود ندارد. این راهنما برای استقرار/آزمون آینده است؛ از APK/Web archive محلی Phase22 برای کاربر یا دادهٔ واقعی استفاده نکنید.

## پیش‌نیاز انتشار آزمایشی آینده

- دریافت build از کانال داخلی رسمی پس از تأیید امضا، version، digest و API endpoint.
- استفاده از staging/test account و دادهٔ مصنوعی؛ هرگز از Production DB برای آزمون استفاده نکنید.
- Backend و Web باید هر دو در origin/HTTPS مشخص و توسط تیم release تأیید شده باشند؛ هنوز hostی در این پروژه اعلام نشده.

## جریان راه‌اندازی طراحی‌شده (هنوز end-to-end تأیید نشده)

1. Parent account را در نسخهٔ داخلی تأییدشده بسازید/وارد شوید.
2. خانواده را بسازید؛ هیچ عضو/دستگاهی را بدون اجازه و disclosure مناسب اضافه نکنید.
3. pairing code کوتاه‌عمر را فقط برای Child موردنظر در همان خانواده استفاده کنید؛ کد را عمومی نکنید.
4. Child disclosure و permissionها را مرور می‌کند؛ اگر permission رد شود، قابلیت وابسته باید limited/unavailable باشد و نباید دادهٔ فرضی نمایش داده شود.
5. Parent داشبورد را با timestamp و permission status بازبینی می‌کند؛ `offline` به معنی حذف یا خاموشی قطعی نیست.
6. پیش از اتکا به هر محدودیت Android، managed/device-owner status را از روی enrollment واقعی تأیید کنید؛ Normal Mode enforcement سیستم‌عاملی تضمین نمی‌کند.

## محدودیت‌های عملیاتی

- pairing، login، dashboard/API، notifications، location، SOS، privacy، export/delete و backend در staging تست نشده‌اند.
- server-side FCM/push delivery فعال/تأیید نشده؛ اعلان فوری تضمین نیست.
- Export حساب/خانواده و حذف کامل account/family وجود ندارد یا ناقص است؛ حذف device/location history را معادل purge backupها ندانید.
- در خطر فوری از خدمات اضطراری محلی استفاده کنید؛ GuardianLink تماس اضطراری برقرار نمی‌کند.
- محدودیت‌های کامل: `KNOWN_LIMITATIONS.md`; وضعیت حریم خصوصی: `PRIVACY_GUIDE.md`.
