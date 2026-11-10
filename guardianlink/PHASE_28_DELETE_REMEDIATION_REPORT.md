# گزارش رفع Delete — Phase 28

**وضعیت قابلیت:** `VERIFIED_FOR_IMPLEMENTED_SCOPE`  
**وضعیت کلی Phase 28:** `BLOCKED_BY_ENVIRONMENT`  
**تصمیم انتشار:** **NO-GO**.

## علت ریشه‌ای Phase 27

- `DELETE /v1/families/me` و `DELETE /v1/me` در probe قبلی 405 می‌دادند؛ routeهای DELETE وجود نداشتند، درحالی‌که GET وجود داشت.
- حذف device از قبل soft revoke بود، نه پاک‌کردن تاریخچه. این معنای قبلی حفظ شده و با family/account erasure اشتباه گرفته نمی‌شود.

## قرارداد و گاردهای پیاده‌شده

### حذف خانواده

- `DELETE /v1/families/me`، Parent احراز‌شده، body `{ "password": "…" }`؛ password دوباره تأیید می‌شود.
- فقط وقتی درخواست‌دهنده تنها عضو خانواده باشد مجاز است. خانوادهٔ چندعضوی با `409 FAMILY_SHARED` رد می‌شود؛ membership/session/داده پیش از رد دست‌نخورده می‌مانند.
- خانواده و ردیف‌های وابسته در transaction PostgreSQL از طریق FK cascade حذف می‌شوند. Parent account و user session باقی می‌مانند؛ دسترسی `GET /v1/me` می‌ماند، اما family دیگر 404 است.
- retry پس از حذف، با همان session کاربر به علت نبود خانواده 404 می‌دهد. rate limit کد ۳ درخواست/ساعت دارد؛ آزمون محدودیت حذف خانواده با سه تأیید نامعتبر و درخواست چهارم 429 پاس شد.

### حذف حساب

- `DELETE /v1/me`، Parent احراز‌شده و re-auth password؛ account بدون خانواده یا عضوِ تنها قابل حذف است.
- accountِ خانوادهٔ چندعضوی با `409 FAMILY_SHARED` رد می‌شود تا transfer/leave-member workflow تعریف شود؛ هیچ مالکیت ضمنی اختراع نشده است.
- account و در صورت وجود خانوادهٔ تک‌عضوی، کل family aggregate در همان transaction حذف می‌شوند. cascade ردیف‌های `user_sessions`, `device_sessions`, `pairing_sessions`, device associations و family data را حذف می‌کند؛ access token کاربر و device tokenهای قبلی پس از عملیات دیگر پذیرفته نمی‌شوند.
- مسیر rate limit در source سه درخواست/ساعت است؛ آزمون مستقل rate-limit برای endpoint account delete اجرا نشد.

### Device و location

- `DELETE /v1/families/me/devices/{id}` همچنان soft revoke: device=`removed`، `device_secret_hash` پاک و sessionها revoke؛ device row و location/history حفظ می‌شوند. آزمون DB تأیید کرد این **پاک‌سازی کامل** نیست.
- حذف تاریخچهٔ location نیازمند `scope=today|last7|all` صریح است؛ scope نامعتبر/حذف‌شده 400، cross-family و شناسهٔ ناشناخته 404؛ اجرای مجدد موفق با `deleted=0`. ردیف‌های PostgreSQL پس از هر scope با assertion خوانده شدند.
- مدل `child/person` مستقل و endpoint حذف کودک وجود ندارد؛ این Phase عملیات جدید مبهمی برای آن نساخت.

## شواهد transaction و DB

- `PrivacyLifecycleTest` روی PostgreSQL 17.11 disposable، با fixtureهای synthetic، rollback تزریقی را آزمایش کرد: trigger تستی که هنگام حذف device خطا می‌دهد، 500 تولید می‌کند اما family/device/member/sessionهای پیشین حفظ می‌شوند.
- cascade family deletion با دادهٔ synthetic در `app_usage_daily`, `locations`, `geofences`, `geofence_states`, `geofence_events`, `device_events`, `family_notifications`, `notification_user_state`, `sos_sessions`, `sos_location_points`, `sos_events`, `sos_notes` اجرا شد؛ پس از حذف، query همهٔ جدول‌های assertشده صفر بود و حساب Parent باقی ماند.
- account deletion با خانوادهٔ تک‌عضوی، بدون خانواده، و خانوادهٔ مشترک (رد ایمن) جداگانه آزموده شد. user/family/device/session/pairing counts، وضعیت session و پاسخ API بررسی شدند.
- password نادرست با 401 رد شد و داده باقی ماند. حذف family/account در HTTP/Netty واقعی روی DB disposable fresh پاسخ 204 داد؛ پس از account deletion token با 401 رد شد و SQL نشان داد `users=0` و `families=0`.

## فایل‌های مرتبط

- `server/src/main/kotlin/ir/guardianlink/server/PrivacyService.kt`
- `server/src/main/kotlin/ir/guardianlink/server/UserRoutes.kt`
- `server/src/main/kotlin/ir/guardianlink/server/LocationRoutes.kt`
- `server/src/test/kotlin/ir/guardianlink/server/PrivacyLifecycleTest.kt`
- `server/src/test/kotlin/ir/guardianlink/server/Phase4Test.kt`
- `API_DOCUMENTATION.md`

## محدودیت‌ها و blockerها

- حذف خانواده/حساب در خانوادهٔ مشترک پشتیبانی نمی‌شود؛ 409 رفتار حفاظتی است، نه bugِ رفع‌شده. transfer، حذف member مستقل و تعیین owner نیازمند طراحی/تصمیم مجزا هستند.
- پاک‌شدن backupهای تاریخی، cloud object، log retention خارج برنامه، export دانلودشده نزد کاربر، یا cache/offline داده روی Android اثبات/تضمین نشده است. Phase 28 backup/restore جدید انجام نداد؛ نتیجهٔ دستی Phase 27 جایگزین DR drill یا purge از backup نیست.
- schema cascade با migration تازه تغییر نکرد؛ ۹ migration فعلی روی PostgreSQL 17.11 validate و روی DB تازه apply شد. این شاهد رفتار production backup/restore نیست.
- محدودیت حوزهٔ قضایی و مهلت/فرآیند حقوقی حذف: `JURISDICTION-SPECIFIC REQUIREMENT`؛ compliance ادعا نمی‌شود.
