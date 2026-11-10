# گزارش باتری و پس‌زمینه — فاز ۱۹

**وضعیت:** `BLOCKED_BY_ENVIRONMENT`; مصرف باتری، CPU و wakeup: `NOT MEASURED`.

## آنچه بررسی شد

- Android device/emulator، adb، Android SDK و ابزار اندازه‌گیری انرژی موجود نیستند؛ هیچ ادعای تست فیزیکی یا اعداد مصرف ارائه نمی‌شود.
- منبع `LocationQueue` سقف 240 نقطه دارد؛ source comment فاصلهٔ 15 دقیقه‌ای را برای تقریب ظرفیت ذکر می‌کند. این نه اثبات cadence واقعی است و نه سنجهٔ عمر باتری.
- `NetworkWatcher` با رخداد network available یک sync زمان‌بندی می‌کند؛ بار callback، تکرار sync، wake lock و انرژی بررسی runtime نشده است.
- WorkManager/زمان‌بندی پس‌زمینه باید در کد و runtime با محدودیت‌های نسخه OS بررسی شود؛ این گزارش هیچ مجوز یا اجرای پس‌زمینه را فرض نمی‌کند.
- هیچ مکان‌یابی جدید، نظارت مخفی، polling loop یا دور زدن مجوزها افزوده نشده است.

## ماتریس باتری

| سنجه | مقدار |
|---|---|
| درصد/ظرفیت مصرف نسبت به baseline دستگاه | `NOT MEASURED` |
| mAh در ساعت، screen-off و سناریوی پس‌زمینه | `NOT MEASURED` |
| CPU، wakelock، job/wakeup | `NOT MEASURED` |
| تعداد/حجم شبکه برای sync | `NOT MEASURED` |
| مکان‌یابی و cadence واقعی تحت مجوز | `NOT MEASURED` |
| اثر Doze، App Standby و محدودیت OEM | `NOT MEASURED` |

بودجهٔ عددی باتری تعیین نشده است؛ تا زمانی که دستگاه، سلامت باتری، OS، تنظیمات مکان‌یابی opt-in، مدت و baseline control تعریف نشوند، درصدها قابل تعمیم نیستند. الزام حفاظتی ثابت: مکان‌یابی فقط با قابلیت و مجوز معتبر؛ احترام به background-location limits و لغو/خاموش‌کردن اشتراک؛ عدم ایجاد حلقهٔ polling.

## ادامهٔ ایمن

در دستگاه آزمایشی رضایت‌محور، سناریوهای screen-off/idle/active و کنترل همسان بدون sync ثبت شوند؛ از Android Battery Historian/Perfetto یا ابزار رسمی سازگار استفاده شود. تکرار هر سناریو، نسخه OS، سطح باتری، شبکه و مدت آزمایش ثبت گردد. تست فقط علیه staging و بدون اعلان/رخداد اضطراری واقعی باشد.
