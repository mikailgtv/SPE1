# عیب‌یابی GuardianLink

## پیش از شروع

در Phase22 هیچ نسخهٔ Production منتشر نشده و Staging هم در دسترس نبوده است. اگر build محلی را می‌بینید، آن را روی داده/حساب واقعی نصب یا استفاده نکنید. این نکته‌ها عمومی‌اند و مسیرهای end-to-end در دستگاه واقعی تأیید نشده‌اند.

| نشانه | اقدام بی‌خطر | محدودیت |
|---|---|---|
| Login یا API کار نمی‌کند | اتصال و URL تأییدشده از مسئول محیط را بررسی کنید؛ token/password را در گزارش کپی نکنید | production API/domain در دسترس/تأیید نیست |
| Pairing code رد یا منقضی است | از Parent مجاز کد تازه بگیرید و همان خانواده/device را بازبینی کنید | rate-limit/race behavior در PG staging آزمایش نشده؛ کد را bypass نکنید |
| Device آفلاین یا last sync قدیمی است | network/permission/زمان sync را بررسی و اپ را باز کنید؛ پس‌زمینهٔ Android best-effort است | زمان sync قطعی نیست؛ offline به معنی حذف یا خاموشی ثابت نیست |
| App usage ناموجود/ناقص است | Usage Access در Android Settings و وضعیت disclosure را بازبینی کنید | رد permission، API و Store policy completeness را محدود می‌کند |
| Location یا Geofence موجود نیست | Location/provider/background permission، timestamp و دقت را بررسی کنید | OEM/Doze/GPS ممکن است delay دهد؛ نقطهٔ قدیمی را زنده فرض نکنید |
| App restriction اثر نمی‌کند | ببینید device واقعاً managed/device-owner enrolled است یا Normal Mode | Normal Mode enforcement قطعی ندارد؛ مجوز/policy را دور نزنید |
| Web Safety محدود است | وضعیت managed Chrome/DPM را بررسی کنید | VPN/DNS/all-browser/url history filtering ارائه‌شده/اثبات‌شده نیست |
| Notification نرسیده | in-app notification center و permission/channel Parent را بررسی کنید | FCM/push server delivery configured/verified نیست؛ ارسال/تحویل را فرض نکنید |
| SOS Parent را مطلع نکرد | وضعیت queue/network/sync را بررسی کنید؛ تا sync موفق Parent ممکن است مطلع نباشد | GuardianLink تماس اضطراری نمی‌گیرد؛ خطر فوری را به خدمات محلی گزارش کنید |
| Export یا حذف حساب لازم است | داده را از طریق حذف موضعی معادل export/delete کامل ندانید؛ owner/privacy process لازم است | family/account export و delete کامل وجود ندارد/ناقص است |
| Backup/Restore لازم است | هیچ command/backup را روی Production اجرا نکنید؛ operator را مطلع کنید | backup/restore عملیاتی یا RPO/RTO تأیید نشده |

## گزارش مشکل امن

در صورت وجود محیط داخلی مجاز، ثبت کنید: version/commit (اگر موجود)، زمان UTC، نوع دستگاه/OS، مرحله، error code و redacted request ID. هرگز access/refresh token، password، pairing code، مختصات، SOS note، payload یا child data را در ticket/chat/log وارد نکنید.

## Engineering blocker

Phase22 verdict: `DEPLOYMENT_BLOCKED`. Backend/Android/DB/staging/CI/monitoring build/testهای عملیاتی انجام نشده‌اند. موضوعات فنی باز در `KNOWN_LIMITATIONS.md` و `PHASE_22_FINAL_REPORT.md` هستند.
