# راهنمای تست فیزیکی Phase 6 — وضعیت: Pending

هیچ‌یک از مراحل زیر در این محیط روی گوشی اجرا نشده‌اند. نتیجه را فقط پس از ثبت مدل دستگاه، نسخهٔ Android، build و زمان sync به گزارش Phase 6 اضافه کنید.

## آماده‌سازی

1. دو گوشی واقعی آماده کنید: Parent و Child؛ هر دو با build یکسان و API قابل دسترس.
2. Family بسازید و Child را pair کنید. نام package برای بازیابی در تست: `ir.guardianlink.child`.
3. Parent notification را در Android مجاز کنید.
4. برای اندازه‌گیری تأخیر، ساعت و شبکهٔ هر دو گوشی را ثبت کنید؛ WorkManager و Doze می‌توانند زمان اجرا را عقب بیندازند.
5. اگر Google Play build آزمایش می‌شود، وضعیت بررسی مجوز `QUERY_ALL_PACKAGES` و package visibility آن build را جداگانه ثبت کنید؛ sideload نتیجهٔ بررسی Play را اثبات نمی‌کند.

## A. Normal Android mode (گوشی معمولی، بدون Device Owner)

1. Child را pair و صفحهٔ وضعیت را باز کنید. باید Normal Android mode و عبارت «فقط گزارش/هشدار؛ بدون مسدودسازی» دیده شود.
2. Usage Access را خاموش نگه دارید؛ Parent باید وضعیت خاموش/دادهٔ مصرف گزارش‌نشده را ببیند. هیچ permission silently اعطا نمی‌شود.
3. Usage Access را از مسیر رسمی Settings فعال کنید؛ sync دستی بزنید و دادهٔ روز/هفته را پس از ثبت فعالیت واقعی بررسی کنید.
4. روی Child یک اپ آزمایشی نصب کنید. پس از snapshot inventory (چرخهٔ اسمی ۶ ساعته؛ شبکه و WorkManager می‌توانند آن را عقب بیندازند) package/name/version در Parent ظاهر شود. اگر visibility فیلتر شده، نقص inventory را ثبت کنید؛ کامل بودن را فرض نکنید.
5. در Parent برای آن package سقف روزانه بسازید و ذخیره کنید. Child همچنان اپ را باز می‌کند؛ Parent باید `APP_RESTRICTION_UNSUPPORTED` و وضعیت unsupported ببیند، نه applied.
6. در Parent رویدادهای نزدیک/بحرانی/رسیدن به سقف را بررسی کنید. تکرار sync نباید برای همان rule/روز threshold event تکراری بسازد. اعلان فقط به Parent می‌رود.
7. Update هم‌زمان از دو Parent/session را امتحان کنید: نسخهٔ stale باید 409 بدهد؛ دادهٔ جدید با refresh قابل مشاهده باشد.
8. یک schedule با پنجرهٔ عبوری از نیمه‌شب ذخیره کنید؛ چون دستگاه Normal است، هیچ block واقعی انتظار نداشته باشید.
9. اینترنت Child را قطع کنید. rule cache رمزگذاری‌شده باقی می‌ماند؛ Parent باید last policy sync قدیمی را ببیند. رویدادهای صف‌شده پس از reconnect sync شوند. زمان دقیق WorkManager را به‌عنوان real-time تفسیر نکنید.
10. Usage Access را revoke کنید و sync بعدی را بررسی کنید؛ گزارش باید ناموجود/قدیمی شود و threshold جدید با دادهٔ ناموجود ساخته نشود.
11. uninstall اپ را انجام دهید؛ پس از snapshot کامل بعدی باید `installed=false` شود. اگر package visibility گسترده در دسترس نیست، snapshot نباید به‌عنوان کامل گزارش شود.

## B. Android Enterprise / Device Owner (فقط device آزمایشگاهی)

1. از گوشی آزمایشگاهی جداگانه استفاده کنید؛ provisioning کامل معمولاً دستگاه را به حالت مدیریت‌شده در می‌آورد یا factory reset می‌خواهد. از دستگاه شخصی یا اطلاعات واقعی خانواده استفاده نکنید.
2. اپ را فقط از جریان رسمی Android Enterprise/سازمان خود به‌صورت Fully Managed Device Owner provision کنید. نصب یا روشن‌کردن Device Admin عادی معادل Device Owner نیست.
3. در Child UI و Parent API باید `managed` فقط وقتی دیده شود که `DevicePolicyManager.isDeviceOwnerApp()` واقعاً true باشد. صرفاً وجود receiver کافی نیست.
4. برای package آزمایشی قابل تعلیق، rule `RESTRICTED` بسازید. پس از اجرای local policy worker، Android launcher آن اپ را باید در حالت suspended نشان دهد؛ Parent باید event `APP_RESTRICTION_ENFORCED` و status گزارش‌شده را ببیند.
5. rule را غیرفعال/حذف کنید؛ در چرخهٔ بعدی policy worker باید package را با همان DPM API از حالت suspended خارج کند.
6. `SCHEDULED` را برای زمان نزدیک فعلی تنظیم کنید؛ تغییر زمان‌بندی ممکن است تا اجرای WorkManager به تأخیر بیفتد. start، end، روز منتخب و overnight behavior را بررسی کنید.
7. `LIMITED` را با Usage Access روشن آزمایش کنید و واقعاً زمان استفاده را از سقف عبور دهید؛ system policy worker باید در اجرای بعدی suspend کند. Usage Access خاموش باشد، سیستم نباید usage limit را حدس بزند یا fake suspend کند؛ Parent status باید unsupported/usage access required باشد.
8. package سیستم/ضروری آزمایشی را انتخاب نکنید که برای کار گوشی لازم است. اگر Android packageای را رد کرد، باید event `APP_RESTRICTION_FAILED` ثبت و Parent مطلع شود؛ از Accessibility، root یا workaround استفاده نکنید.
9. Device Owner را با روال رسمی IT بردارید و بررسی کنید UI به Normal برگردد و در Normal، policy دیگر `setPackagesSuspended()` را صدا نزند.

## ثبت نتیجه

برای هر آزمون ثبت کنید: pass/fail, مدل گوشی، Android/API level، app version, Device Owner واقعی (بله/خیر)، Usage Access (بله/خیر)، package آزمایشی، نسخهٔ policy، ساعت rule update، ساعت اجرای observed، نتیجهٔ DPM/رویداد، network state، و محدودیت‌های OEM. آزمون ناموفق یا اجرا‌نشده را با عنوان «pending/failed» ثبت کنید؛ آن را به‌عنوان موفقیت تعمیم ندهید.
