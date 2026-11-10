# گزارش Runtime پایگاه‌داده — Phase 27

**وضعیت:** `CLOSED` برای migration/runtime و isolation fixtureهای محلی؛ `NOT_TESTED` برای Production DB.

## شواهد

- PostgreSQL 17.11 محلی با DB مجزای `guardianlink_phase27` و role مخصوص تست به Backend متصل شد.
- DB ابتدا خالی بود؛ Flyway همهٔ ۹ migration (`V1` تا `V9`) را validate و اعمال کرد؛ schema version نهایی 9.
- Catalog snapshot شامل ۴۳ جدول عمومی بود. ثبت یا نمایش مقدار credential/محتوای خصوصی انجام نشد.
- دو خانوادهٔ مصنوعی با API ساخته شدند؛ خواندن منابع device/location/history/geofence/app-rules/web-safety/events/notifications/SOS از endpointهای مجاز پاسخ معتبر داد. هر family فقط device و eventهای خودش را دید.
- پس از آزمون‌ها، device مصنوعی Family A از API به‌صورت soft removal حذف شد: HTTP 204؛ در DB وضعیت `removed`، hash راز device تهی و ۱ از ۱ device session revoke شده بود. رکوردهای تاریخی family/account حذف نشدند.
- برای پاک‌سازی، backup/restore محلی جداگانه انجام و سپس DBهای disposable و role موقت حذف شدند.

## نتیجه و مرز ادعا

Migration خالی و DDL همان sandbox تأیید شد؛ transaction/cascade/delete semantics در production، migration privilege separation، upgrade/rollback روی دادهٔ موجود، HA، backup schedule، encryption-at-rest، retention و performance آزموده نشدند. هیچ schema یا cascade پرریسکی تغییر نکرد.
