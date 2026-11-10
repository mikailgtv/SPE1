# گزارش Security Regression — Phase 28

**وضعیت regressionهای محلی اجراشده:** `PASS`  
**وضعیت کلی Phase 28:** `BLOCKED_BY_ENVIRONMENT`  
**تصمیم انتشار:** **NO-GO**.

## کنترل‌های حساس به Export/Delete

- احراز Parent user token و session زنده الزامی؛ device token و درخواست anonymous در export رد می‌شوند.
- خانواده از principal سرور استخراج می‌شود؛ A/B isolation با دادهٔ synthetic و query `family_id` ردشده آزمایش شد. Device IDهای ناشناخته/خانوادهٔ دیگر برای location deletion به‌صورت 404 پاسخ می‌گیرند.
- export از SQL allowlist/parameter binding استفاده می‌کند؛ فقط فیلدهای مشخص خوانده می‌شوند و credential/password/session/device secrets انتخاب نمی‌شوند. سقف 10k/5MiB، per-statement 5s و PostgreSQL transaction timeout 30s تنظیم شده؛ row-overflow و export request rate حد پنج/پنج دقیقه آزمایش شده‌اند.
- family/account delete نیازمند re-auth password است؛ password اشتباه، shared-family و sessionهای نامعتبر deny می‌شوند. delete rate سه/ساعت کد دارد؛ rate خانواده با ۳ تلاش نامعتبر و ۴امین 429 آزموده شد، اما rate مجزای account delete مستقیماً آزموده نشد.
- حذف transaction failure با trigger synthetic fault-injection شد و DB rollback بررسی شد. پس از hard delete، user/device sessionها و pairing rows طبق cascade پاک/غیرقابل‌استفاده بودند؛ آزمون API پس از حذف token را 401 نشان داد.
- location delete فقط scope صریح را می‌پذیرد و تعداد واقعی rowهای PostgreSQL بررسی شده است. Device soft removal سابقه را عمداً حفظ می‌کند؛ این تفاوت در تست و مستندات آمده است.

## Logging/observability و اسکن

- structured eventهای export/delete در source به operation/scope/format/count محدودند؛ HTTP log route-template/status/duration/error category دارد. Netty runtime logs بررسی شدند و هیچ payload، token، email، مختصات یا متن SOS در eventهای lifecycle دیده نشد.
- secret scan workspace با الگوهای private-key/provider-key/token و hard-coded production secret اجرا شد؛ خروجی redacted: **۰ فایل با signature، ۰ فایل production-literal، ۰ فایل private credential**. هیچ مقدار secret در خروجی scan چاپ نشد.
- `UnitTest.redact never leaks raw secrets`, `ObservabilityUnitTest` (bounded route labels، عدم نگهداری raw URI در metrics، fixed safe error categories)، `SecurityTest` auth/family/device denials و `RequestGuardRegressionTest` body-size 413 در suite نهایی پاس شدند.
- هیچ APM/remote log، SAST/DAST، dependency audit جدا، penetration test، threat-model مستقل یا production log sink بررسی نشد؛ از zero-match اسکن محدود، نبود تمام آسیب‌پذیری‌ها نتیجه نمی‌شود.

## اصلاحات regression در suite نهایی

- `StatusRepos.kt`: SQL اختیاری `last_sync` اصلاح شد؛ sync testها پاس.
- `UserRoutes.kt`: presence فقط با heartbeat تازه‌تر از presence lease آنلاین گزارش می‌شود؛ lease regression پاس.
- `UsageRepos.kt`: screen-time upsert در همان transaction connection ردیف را می‌خواند؛ settings/remaining-minutes regressionها پاس.
- `DeviceRoutes.kt`: sync app-version change رویداد `APP_UPDATED` را با مقایسهٔ snapshot قبلی تولید می‌کند؛ regression پاس.
- `PrivacyService.kt`/`UserRoutes.kt`: export allowlist، auth/scope/limits و transaction timeout؛ deletion guards و log minimization.
- `Phase2Test.kt`: expiry setup به‌جای TTL یک‌ثانیه‌ای شکننده، TTL ده‌ثانیه‌ای را بعد از pairing کامل wait می‌کند؛ بررسی رد session همچنان انجام می‌شود و test تضعیف نشده است.
- `Phase9Test.kt`: fixture صریح `background_granted` را می‌فرستد؛ policy سرویس تغییر/تضعیف نشده است.

## محدودیت‌های امنیتی باز

- process-local rate limits بین replicaها هماهنگ نیستند؛ shared rate limiting و load/race بررسی نشده‌اند.
- backup/log retention، key management واقعی، Android platform enforcement، TLS/deployment hardening، FCM و حقوق دسترسی/پاک‌سازی در jurisdiction مشخص نشده‌اند.
- ارزیابی قانونی: `JURISDICTION-SPECIFIC REQUIREMENT`; هیچ compliance claim ارائه نمی‌شود.
