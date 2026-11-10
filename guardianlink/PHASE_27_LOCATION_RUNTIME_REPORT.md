# گزارش Runtime موقعیت و Geofence — Phase 27

**وضعیت:** `BLOCKED` برای measurement/transition واقعی؛ `CLOSED` فقط برای read/config endpointهای backend.

- از هر دو device fixture، GET current location و history روی API واقعی HTTP 200 داد؛ دادهٔ مکانی برای fixtureها خالی بود و هیچ location fix ثبت نشد.
- ساخت و خواندن دو geofence configuration synthetic از API موفق شد. مرکز zone صرفاً مقدار قراردادی test برای تعریف policy بود؛ آن را موقعیت device/user یا مختصات واقعی تلقی/گزارش نمی‌کنیم.
- هیچ مختصاتی از device واقعی، location provider، GPS، background permission یا Android Location API خوانده نشد.
- ورود/خروج geofence، dwell/accuracy، stale fix، offline replay و ارسال location از app روی سخت‌افزار واقعی آزموده نشد؛ emulator/KVM و Android SDK/device در دسترس نبود.

**نتیجه:** وجود zone در API اثبات transition یا measurement نیست. برای بستن blocker، device آزمون مجاز، build نصب‌شده و رضایت/permission روشن لازم است؛ سپس eventهای ورود/خروج باید از fix واقعی و با logهای redact‌شده آزموده شوند. مختصات synthetic نباید به‌عنوان موقعیت واقعی گزارش شود.
