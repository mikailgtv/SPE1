# راهنمای حریم خصوصی و شفافیت — GuardianLink

## وضعیت

این سند توصیف source-backed behavior و شکاف‌های شناخته‌شده است، نه legal notice نهایی یا ادعای انطباق. هیچ Product release در Phase22 منتشر نشد. حوزهٔ قضایی، controller/processor role و distribution channel تعیین نشده‌اند؛ موارد قانونی `JURISDICTION-SPECIFIC REQUIREMENT` هستند و به بررسی متخصص نیاز دارند.

## دسته‌های دادهٔ حساس شناخته‌شده در source

- Location: مختصات دقیق، دقت/منبع و timestamp برای location/geofence/SOS؛ به permission و OS/provider وابسته.
- App usage/inventory: package/app name/category، مدت و تعداد اجرا؛ به Usage Access و محدودیت distribution/API Android وابسته.
- Device state: نام/شناسهٔ masked، مدل/نسخه، battery/network/permission/sync health.
- Authentication/Pairing: account identifiers, password hashes, sessions, pairing state/code lifecycle.
- SOS/notification: وضعیت emergency flow، notes/status, timestamps, optional location/permission/battery/network context؛ بسیار حساس.

محتوای پیام خصوصی، keystrokes، صفحهٔ Child، clipboard، camera/microphone، SMS/call content یا browse history را ادعای جمع‌آوری نمی‌کنیم. این گزاره بر مبنای static source review است و جایگزین بازبینی runtime نیست.

## Permission و محدودیت Android

- رد Location/Usage Access می‌تواند مکان یا آمار را unavailable/ناقص کند؛ دادهٔ ساختگی نشان داده نشود.
- Android/OEM/Doze/network ممکن است sync را به تأخیر اندازد؛ WorkManager زمان exact را تضمین نمی‌کند.
- `QUERY_ALL_PACKAGES` و usage access نیازمند بررسی کانال توزیع است.
- Normal Mode با Device Owner/Managed Device enforcement یکی نیست.
- SOS location اختیاری است؛ SOS جایگزین تماس با خدمات اضطراری نیست.

## ذخیره‌سازی، retention و حذف

- Backend از PostgreSQL source و Android از encrypted local storage/queues در برخی مسیرها استفاده می‌کند؛ key management, live DB encryption, backup encryption و runtime retention در Phase22 تأیید نشده‌اند.
- بعضی cleanupهای موضعی و حذف history/device revoke وجود دارد؛ retention سراسری و زمان نگهداری همهٔ eventها، backupها و logs کامل/تصویب‌شده نیست.
- Family/account export API و full deletion workflow وجود ندارد یا ناقص است؛ deletion از backupهای قدیمی تضمین نمی‌شود.
- RPO/RTO و backup/restore status: `NOT DEFINED`/`NOT TESTED`.

## دادهٔ ثالث و telemetry

- نقشه‌های عمومی ممکن است OpenStreetMap tile provider را درخواست کنند؛ viewport تقریبی می‌تواند برای provider قابل مشاهده باشد.
- SOS map محلی/شماتیک توصیف شده و tile خارجی ادعا نمی‌شود.
- Backend aggregate metrics/redaction در source وجود دارد؛ collector, retention, alerting و production logs بررسی نشده‌اند. Crash/ANR client SDK فعال/تأیید نشده.
- هیچ دادهٔ خام location/token/request body یا اطلاعات شخصی نباید برای monitoring/reporting منتشر شود.

## حقوق/تماس

در حال حاضر مسیر self-service export/delete وجود ندارد و release منتشر نشده است. برای استفادهٔ عملی از این سند به‌عنوان privacy notice، مسئول داده، retention، درخواست دسترسی/حذف، محدودیت emergency/backup، انتقال بین‌مرزی، والد/کودک و توزیع باید با مالک محصول/حریم خصوصی و مشاور واجد صلاحیت تعریف شود. تا آن زمان هیچ ادعای GDPR یا قانون دیگری ارائه نمی‌شود.
