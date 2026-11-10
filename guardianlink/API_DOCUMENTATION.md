# مستند API — GuardianLink (فهرست routeهای موجود در کد)

**نسخه:** API prefix فعلی `/v1`. OpenAPI تولید نشده است. Routeهای Export/Delete افزوده‌شده در Phase 28 با PostgreSQL 17.11 و آزمون‌های API محلی بررسی شدند؛ این شاهد جایگزین contract کامل همهٔ DTOها، staging یا production نیست. نمونه‌ها خلاصه‌اند، نه جایگزین schema code.

## قواعد عمومی

- JSON؛ پاسخ‌های زمان‌دار عمدتاً ISO-8601 با offset هستند.
- Parent: `Authorization: Bearer <access_token>` نوع user. Child: access token نوع device؛ بعضی pairing مسیرها پیش از صدور credential با code/exchange token انجام می‌شوند.
- خانواده از principal سرور استخراج می‌شود، نه از `family_id` ارسالی؛ Parent resourceهای device-scoped باید مالکیت device را بررسی کنند. این route scoping هنوز جایگزین RLS نیست.
- Access JWT پیش‌فرض ۹۰۰ ثانیه است؛ Refresh opaque و فقط SHA-256 آن در DB نگهداری می‌شود و rotation دارد.
- خطای استاندارد: `{ "code": "...", "message": "..." }`. خطاهای رایج: `VALIDATION`, `UNAUTHORIZED`, `SESSION_EXPIRED`, `SESSION_REVOKED`, `FORBIDDEN`, `NOT_FOUND`, `RATE_LIMITED`, `PAIRING_INVALID`, `PAIRING_EXPIRED`, `PAIRING_USED`, `DEVICE_REMOVED`, `SERVER_ERROR`.
- Rate limits فعلی process-local هستند؛ auth/pairing و مسیرهای device sync limit اختصاصی دارند. Export خانواده ۵ درخواست/۵ دقیقه و عملیات حذف ۳ درخواست/ساعت محدود شده‌اند. برای تغییرات app/web/location/geofence و برخی خواندن‌های location/usage سقف ۳۰ درخواست/۵ دقیقه برای تغییر یا ۱۲۰/۵ دقیقه برای خواندن تعریف شده است؛ این محدودیت‌ها بین replicaها مشترک نیستند. بیشینهٔ body، timeout، Retry-After و CORS هنوز contract سراسری مستند/تأیید نشده‌اند.
## سلامت سرویس (بدون احراز هویت)

| Method / path | رفتار | پاسخ |
|---|---|---|
| `GET /health/live` | فقط پاسخ‌گویی process را می‌سنجد؛ هیچ dependency probe ندارد. | `200 {"status":"live"}` |
| `GET /health/ready` | DB probe و دسترسی خواندن/نوشتن به `DATA_DIR`؛ آماده فقط اگر هر دو سالم باشند. | `200 ready` یا `503 not_ready` با checkهای `database`, `storage`, `push_notifications`; push=`not_configured`. |
| `GET /health` | alias سازگار با نسخه‌های قبلی برای readiness. | همان `/health/ready`. |

این endpointها compile شده‌اند و health tests در backend suite پاس شدند؛ runtime probes مجزا برای همهٔ health pathها انجام نشده‌اند.

## نمونه‌های قرارداد

### ثبت‌نام و session

```http
POST /v1/auth/register
Content-Type: application/json
```

```json
{"email":"parent@example.invalid","password":"REPLACE_WITH_LOCAL_TEST_PASSWORD"}
```

پاسخ موفق `201`: `access_token`, `refresh_token`, `expires_in`, `token_type`. Refresh token فقط در secure client storage نگهداری شود؛ در log، URL یا ticket قرار نگیرد.

### SOS (نمونهٔ request)

```json
{
  "client_request_id":"REPLACE_WITH_CLIENT_GENERATED_UUID",
  "client_triggered_at":0,
  "source":"child_ui",
  "battery_level":42,
  "network_state":"wifi",
  "device_state":"unknown",
  "permission_status":"background_granted",
  "provider_status":"enabled",
  "tracking_duration_minutes":15,
  "tracking_interval_sec":60,
  "initial_location":null
}
```

`initial_location` می‌تواند `null` باشد و tracking اختیاری است. پاسخ شامل `session`, `already_active`, `queued` است. `client_triggered_at` زمان گزارش‌شده از دستگاه است، نه timestamp مستقل قابل‌اثبات سرور؛ مقدار `0` در مثال برای سازگاری به زمان دریافت سرور fallback می‌کند.

## Auth، حساب و خانواده

| Method / Path | Auth | کارکرد / پاسخ |
|---|---|---|
| `POST /v1/auth/register` | عمومی؛ محدود به IP | `{email,password}`؛ ساخت Parent و session؛ password در کد جدید ۱۲–۱۲۸ کاراکتر. |
| `POST /v1/auth/login` | عمومی؛ محدود به IP+email | `{email,password}`؛ access/refresh. خطای credentials عمومی. |
| `POST /v1/auth/refresh` | refresh token در body | `{refresh_token}`؛ چرخش توکن. |
| `POST /v1/auth/logout` | refresh token در body | revoke همان session؛ 204. endpoint مشاهده/revoke سایر نشست‌ها هنوز وجود ندارد. |
| `GET /v1/me` | Parent | نمایهٔ جاری. |
| `POST /v1/families` | Parent | ساخت خانوادهٔ کاربر جاری؛ `{name}`. |
| `GET /v1/families/me` | Parent | خانواده و اعضای مجاز. |
| `GET /v1/families/me/export?format=json&scope=family` | Parent عضو خانواده | خروجی JSON همگام و محدود به خانوادهٔ مجاز کاربر. تنها `format=json` و `scope=family` پذیرفته می‌شود؛ پارامتر ناشناخته/تکراری، CSV و scope دیگر `400` می‌گیرند. سقف ۱۰٬۰۰۰ رکورد و ۵ MiB؛ هر statement حداکثر ۵ ثانیه و تراکنش حداکثر ۳۰ ثانیه؛ خروجی artifact/لینک دانلود نیست. نرخ: ۵/۵ دقیقه. |
| `DELETE /v1/families/me` | Parent؛ تنها عضو خانواده | `{ "password": "..." }`؛ تأیید مجدد رمز و حذف تراکنشی کل خانواده و داده‌های وابسته طبق FKهای موجود؛ 204. خانوادهٔ چندعضوی با 409 رد می‌شود تا انتقال مالکیت/خروج امن طراحی شود. حساب و نشست Parent باقی می‌مانند. نرخ: ۳/ساعت. |
| `DELETE /v1/me` | Parent؛ بدون خانواده یا تنها عضو خانواده | `{ "password": "..." }`؛ تأیید مجدد رمز، حذف تراکنشی account و خانوادهٔ تک‌عضوی وابسته و revoke نشست‌ها/credentialهای دستگاه با cascade؛ 204. خانوادهٔ چندعضوی با 409 رد می‌شود. نرخ: ۳/ساعت. |

### دامنه و محدودیت‌های چرخهٔ داده (Phase 28)

- Export یک JSON همگام در پاسخ HTTP است و فقط خانوادهٔ منتسب به Parent احراز‌شده را می‌خواند؛ ورودی `family_id` پذیرفته نمی‌شود. خروجی تا ۱۰٬۰۰۰ ردیف/۵ MiB محدود است؛ هر statement حداکثر ۵ ثانیه و تراکنش حداکثر ۳۰ ثانیه مجاز است. CSV، export چندخانواده‌ای، فایل/آرشیو پایدار و دانلود async پیاده‌سازی نشده‌اند.
- export شامل داده‌های خانواده/عضویت، نمایهٔ غیرمحرمانهٔ دستگاه و وضعیت/رویداد، usage و screen-time، location/history و تنظیمات، geofence، قواعد app/web، notification state مجاز و SOS history/notes می‌شود. binary آیکن/cache و دادهٔ global که مالک خانواده ندارد صادر نمی‌شود. password hash، access/refresh/device secrets و hash آن‌ها، session/pairing credentials و secretهای مشابه صادر نمی‌شوند.
- structured log فقط `operation`, `scope`, `format`, `count` را برای export (و event عملیاتی برای delete) ثبت می‌کند؛ payload، نام/شناسهٔ شخص، token و مختصات ثبت نمی‌شوند. این رویدادها log-based و وابسته به retention زیرساخت‌اند؛ دفتر audit پایدار/قابل‌جست‌وجو افزوده نشده است.
- حذف خانواده/حساب فوری و تراکنشی در PostgreSQL است. در خانوادهٔ چندعضوی حذف با `409` متوقف می‌شود (بدون تغییر عضویت/session) تا workflow انتقال مالکیت/حذف عضو طراحی شود. حذف حساب فقط برای account بدون خانواده یا خانوادهٔ تک‌عضوی مجاز است.
- حذف device همچنان soft revoke است: credential/session لغو می‌شود، اما device row و سابقه/telemetry باقی می‌مانند؛ آن را «پاک‌سازی دادهٔ دستگاه» تلقی نکنید. پاک‌سازی backup، logهای بیرونی، export ذخیره‌شده نزد کاربر یا داده‌های offline دستگاه توسط این endpoint تضمین نمی‌شود.

## Pairing و Child device

| Method / Path | Auth | کارکرد |
|---|---|---|
| `POST /v1/pairing/sessions` | Parent | ساخت code/QR یک‌بارمصرف با TTL پیکربندی‌شده؛ code فقط در پاسخ اولیه plaintext است. |
| `GET /v1/pairing/sessions/active` | Parent | session فعال، بدون plaintext code. |
| `POST /v1/pairing/exchange` | عمومی؛ IP rate limit | `{code,install_id,device_name,manufacturer,model,android_version,app_version}`؛ exchange token کوتاه‌عمر. |
| `POST /v1/pairing/confirm` | عمومی؛ IP rate limit | `{exchange_token,install_id,device_name,manufacturer,model,android_version,app_version}`؛ صدور device id/secret یک‌باره؛ flow و replay در آزمون‌های backend بررسی شده‌اند، اما concurrency/race چند replica آزمون نشده است. |
| `POST /v1/device/auth` | device id + raw device secret | صدور user session نوع device؛ secret خام نباید log شود. |
| `POST /v1/device/refresh` | refresh token + device id | rotation دستگاه. |
| `POST /v1/device/heartbeat`, `/status`, `/sync` | Child device | telemetry/status/sync دسته‌ای؛ هر payload باید طبق DTO validation شود. |
| `GET /v1/families/me/devices` | Parent | deviceهای فعال خانواده. |
| `GET /v1/families/me/devices/{id}` | Parent مالک خانواده | جزئیات device. |
| `DELETE /v1/families/me/devices/{id}` | Parent مالک خانواده | soft remove و revoke sessionهای device؛ پاک‌سازی داده/restore از backup باید جداگانه بررسی شود. |

## Monitoring، Usage، App Management

| Method / Path | Scope | گروه |
|---|---|---|
| `GET /v1/families/me/devices/{id}/health`, `/permissions`, `/events` | Parent + owned device | سلامت، مجوزها و event feed. |
| `GET/PUT /v1/families/me/settings` | Parent family | آستانه‌ها و intervalهای خانواده. |
| `GET /v1/families/me/events` | Parent family | رویدادهای خانواده؛ pagination/filtering باید طبق route جاری بررسی شود. |
| `POST /v1/device/usage/upload` | Child device | sync مصرف اپ. |
| `GET /v1/families/me/devices/{id}/usage/today`, `/usage/weekly`, `/usage/apps`, `/usage/apps/{pkg}/detail`, `/usage/apps/{pkg}/icon`, `/usage/report/daily` | Parent + owned device | آمار مصرف؛ تاریخ/پکیج ورودی اعتبارسنجی می‌شود. |
| `GET /v1/families/me/devices/{id}/screen-time/report`, `/screen-time/settings`, `/screen-time/schedules` | Parent + owned device | گزارش و تنظیمات. |
| `POST /v1/families/me/devices/{id}/screen-time/settings`, `POST /v1/families/me/devices/{id}/screen-time/schedules`, `PUT /v1/families/me/devices/{id}/screen-time/schedules/{sid}` | Parent + owned device | ثبت/تغییر policy. |
| `GET /v1/device/app-rules`, `POST /v1/device/app-management/sync` | Child device | دریافت/همگام‌سازی rule/inventory. |
| `GET /v1/families/me/devices/{id}/apps`, `/apps/{packageName}/usage`, `/app-rules`, `/app-events` | Parent + owned device | inventory، usage و event. |
| `POST /v1/families/me/devices/{id}/app-rules`, `PUT /v1/families/me/devices/{id}/app-rules/{ruleId}`, `DELETE /v1/families/me/devices/{id}/app-rules/{ruleId}` | Parent + owned device | CRUD rule، نسخه/validation وابسته به DTO. |

## Location، Geofence، Web Safety

| Method / Path | Scope | کارکرد |
|---|---|---|
| `POST /v1/device/location/sync` | Child device | location batch؛ sharing/validation/retention در service. |
| `GET /v1/families/me/devices/{id}/location/current`, `/location/history`, `/location/settings` | Parent + owned device | آخرین موقعیت، تاریخچه و تنظیمات. |
| `POST /v1/families/me/devices/{id}/location/settings`, `DELETE /v1/families/me/devices/{id}/location/history?scope={today\|last7\|all}` | Parent + owned device | تنظیم اشتراک/retention و حذف مختصات با scope صریح و ownership خانواده؛ scope جاافتاده/نامعتبر `400`؛ درخواست تکراری مجاز است. |
| `GET /v1/device/geofences`, `POST /v1/device/geofences/sync`, `POST /v1/device/geofences/events` | Child device | دریافت zone و ارسال گذارهای معتبر. |
| `GET/POST /v1/families/me/devices/{id}/geofences`, `PUT/DELETE /v1/families/me/devices/{id}/geofences/{gid}` | Parent + owned device | مشاهده/تغییر zone. |
| `GET /v1/families/me/devices/{id}/geofences/{gid}/events`, `GET /v1/families/me/devices/{id}/geofence-events` | Parent + owned device | تاریخچهٔ محدوده. |
| `POST /v1/device/web-safety/sync` | Child device | دریافت/گزارش وضعیت policy. |
| `GET/POST /v1/families/me/devices/{id}/web-safety/rules`, `PUT/DELETE /v1/families/me/devices/{id}/web-safety/rules/{ruleId}` | Parent + owned device | CRUD تنظیمات وب. |
| `POST /v1/families/me/devices/{id}/web-safety/overrides`, `DELETE /v1/families/me/devices/{id}/web-safety/overrides/{overrideId}`, `GET /v1/families/me/devices/{id}/web-safety/events` | Parent + owned device | override و eventهای پیکربندی؛ بازدید/URL یا محتوای وب نباید جمع‌آوری شود. |

## Notification Center

- `GET /v1/families/me/notifications` (فیلترها/cursor طبق `NotificationRoutes`)، `GET /unread-count`, `GET /{id}`.
- `POST /{id}/read`, `/read-all`, `/{id}/acknowledge`, `/{id}/resolve`.
- `GET/PUT /preferences`.
- Feed برای Parent جاری/family او است؛ هر ردیف با family و user read-state محدود می‌شود. Push/FCM server endpoint وجود ندارد.

## SOS

| Method / Path | Scope | کارکرد |
|---|---|---|
| `POST /v1/device/sos` | Child device; rate-limited | ایجاد SOS idempotent با `client_request_id`, context و optional location/tracking. |
| `GET /v1/device/sos/active`, `GET /v1/device/sos/{sosId}` | Child device | وضعیت جلسهٔ همان device. |
| `POST /v1/device/sos/{sosId}/cancel`, `/tracking`, `/network`, `/locations` | Child device | لغو، تغییر ردیابی، تغییر شبکه و batch نقاط؛ نرخ/اعتبارسنجی طبق service. |
| `GET/PUT /v1/families/me/sos/preferences` | Parent family | انقضای خودکار جدید ۱–۷۲ ساعت. |
| `GET /v1/families/me/sos/active` | Parent family | SOSهای باز. |
| `GET /v1/families/me/devices/{id}/sos/history` | Parent + owned device | سابقهٔ device؛ `limit` محدود است. |
| `GET /v1/families/me/sos/{sosId}`, `/location`, `/events`, `/notes` | Parent family | جزئیات محدود به family. |
| `POST /v1/families/me/sos/{sosId}/notes`, `POST /v1/families/me/sos/{sosId}/acknowledge`, `POST /v1/families/me/sos/{sosId}/resolve` | Parent family | note و lifecycle؛ actor/timestamp event در SOS ثبت می‌شود. |

## قراردادهای تکمیل‌نشده

- OpenAPI/Swagger، schema نمونهٔ JSON و contract tests یکپارچه برای تمام routeها تولید/اجرا نشده‌اند.
- Body-size limits، global timeout، correlation ID و Retry-After سروری تأیید نشده‌اند.
- Auth sessions API، password reset/OTP و refresh reuse alert وجود ندارد.
- Rate limiter فقط process-local است؛ درخواست حساس در چند replica محدودیت مشترک ندارد.
- CSRF برای API bearer-only در این پروژه کاربرد مستقیم ندارد؛ اگر cookie/web admin افزوده شد، CSRF/CORS جدا لازم است.
- هر مسیر بالا باید با auth/ownership، validation، error shape و pagination خود route در آزمون دو خانواده پوشش داده شود.
