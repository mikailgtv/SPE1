# Phase 16 — گزارش یکپارچگی API

## نتیجهٔ کوتاه

از روی source، Android Parent، Child و Parent Web از یک API versioned با prefix `/v1` استفاده می‌کنند. Backend اتصال PostgreSQL و Repositoryها را برای دادهٔ اصلی دارد. قراردادها از یک schema/codegen مشترک تولید نمی‌شوند؛ بنابراین «وجود integration در کد» مستند است، اما تبادل واقعی JSON، خطا، auth و داده بین سه client و Backend **BLOCKED** مانده است. Web build و unit tests اجرا شده‌اند؛ سایر client/API tests در این محیط قابل اجرا نبودند.

## نقشهٔ endpointها

| دامنه | سمت client | endpointهای مشاهده‌شده | مسئولیت |
|---|---|---|---|
| سلامت | عمومی | `GET /health/live`, `/health/ready`, alias `/health` | liveness و readiness DB/storage؛ پاسخ push=`not_configured`. |
| Auth Parent | Web و Android Parent | `POST /v1/auth/register`, `/login`, `/refresh`, `/logout`; `GET /v1/me` | user session؛ refresh rotation. |
| Family | Web و Android Parent | `POST /v1/families`, `GET /v1/families/me`, `GET /v1/families/me/devices` | ساخت/خواندن family و فهرست deviceها. |
| Pairing | هر دو Parent؛ Child exchange/confirm | `POST /v1/pairing/sessions`, `GET /sessions/active`, `POST /exchange`, `/confirm`; `POST /v1/device/auth`, `/refresh` | code یک‌بارمصرف → exchange token → device credentials. |
| Device status | Child؛ Parent read | `POST /v1/device/heartbeat`, `/status`, `/sync`; Parent `GET /families/me/devices/{id}`, `/health`, `/permissions`, `/events` | status، sync، health/permissions/event feed. |
| Usage/Screen Time | Child upload؛ هر دو Parent read/write | `POST /v1/device/usage/upload`; `GET .../usage/today`, `/weekly`, `/apps`, `/apps/{pkg}/detail`, `/icon`, `/report/daily`, `/screen-time/report`, `/screen-time/settings`, `/screen-time/schedules`; `POST` settings/schedule، `PUT` schedule | telemetry، report و settings؛ تاریخ‌ها و schedule روزمحور. |
| Location | Child sync؛ هر دو Parent read/write | `POST /v1/device/location/sync`; Parent `GET .../location/current`, `/history`, `/settings`; `POST` settings؛ `DELETE` history | batch fix، sharing، retention/history. |
| Geofence | Child read/sync; Parent CRUD/read | Child `GET /v1/device/geofences`, `POST /sync`, `/events`; Parent `GET/POST .../geofences`, `PUT/DELETE .../{gid}`, event/history routes | policy server-side و transitions مشتق‌شده از fixes. |
| App management | Child sync; Parent manage | Child `POST /v1/device/app-management/sync`, `GET /device/app-rules`; Parent `GET/POST .../app-rules`, `PUT/DELETE .../{ruleId}`, inventory/events/history | inventory، rule version، status و enforcement events. |
| Web Safety | Child sync; Parent manage/read | Child `POST /v1/device/web-safety/sync`; Parent `GET/PUT .../web-safety`, rules CRUD، overrides، events | sync rule/capability/status؛ فقط قابلیت‌های موجود، نه visit content. |
| Notifications | Parent | `GET .../notifications`, `/unread-count`, `/{id}`; `POST /read`, `/read-all`, `/acknowledge`, `/resolve`; `GET/PUT /preferences` | feed/paging/filter، per-user read state و family status. |
| SOS | Child create/tracking; Parent family view | Child `POST /v1/device/sos`, GET active/detail, POST cancel/tracking/network/locations; Parent `GET/PUT /families/me/sos/preferences`, `/active`, per-session detail/location/events/notes/history, POST notes/acknowledge/resolve | stateful family-scoped SOS؛ location optional. |

فهرست فوق بر اساس route definitions و `ApiClient.kt`/`web/src/api.ts`/`App.tsx` است؛ برای query/bodyهای جزئی باید DTOها و route جاری دیده شوند. این جدول ادعای اجرای endpointها نیست.

**Location sync در Phase 16:** Child ابتدا `POST /v1/device/sync` را با permission/provider status ولی بدون مختصات می‌فرستد. فقط اگر response از همان server-authenticated request شامل `locationSharing=true` باشد، fix گرفته و از endpoint موجود `POST /v1/device/location/sync` ارسال می‌کند؛ `false`/`null` صف را پاک می‌کند. `LocationService` سمت Backend نیز مستقل از gate کلاینت، sharing را قبل از ذخیره بررسی می‌کند. این تغییر privacy gate در source اعمال شده، اما Android build/device validation **BLOCKED** است.

## تفاوت implementation clientها

- **Android:** `core/network/ApiClient.kt` توابع typed برای auth, family/pairing, sync, usage, location, geofence, app/Web Safety, notifications و SOS دارد. Base URL از build config است. `ApiException` شامل HTTP/code است و خطای استاندارد Backend `{code,message}` را می‌خواند.
- **Web:** `web/src/api.ts` درخواست JSON با `credentials: omit`, `cache: no-store`, Bearer و same-origin base path می‌سازد؛ 401 را با refresh token refresh می‌کند و درخواست را یک‌بار retry می‌کند. Payload camelCase به snake_case تبدیل و response به camelCase نرمال می‌شود. access/refresh فقط در حافظهٔ tab است؛ reload نیازمند login دوباره است. CORS cross-origin برای استقرار single-origin لازم نیست.
- **Backend:** Jackson `SNAKE_CASE`، `FAIL_ON_UNKNOWN_PROPERTIES=false`; response/error از DTOهای Kotlin تولید می‌شود. Validation معمولاً HTTP 400 و `VALIDATION` است، نه 422. conflictها 409، unauthenticated 401، forbidden 403، not-found 404، removed 410 و rate-limit 429 هستند.
- **قرارداد مشترک:** OpenAPI/Swagger تولیدشده، DTOهای codegen‌شده یا coverage سراسری contract tests پیدا نشد. سه model/client مستقل‌اند؛ build موفق Web سازگاری runtime با Kotlin DTOها را اثبات نمی‌کند.

## Auth و مجوز route

- `requireUser` انتظار JWT `typ=user`، valid signature/issuer/expiry، session موجود/غیرلغوشده و owner user دارد؛ family id از DB استخراج می‌شود.
- `requireDevice` انتظار `typ=device` و session فعال دارد و device باید موجود و `active` باشد.
- Parent device routeها از `ownedDevice` یا بررسی family-scoped service استفاده می‌کنند؛ device خارج از family در source به 404 می‌رسد. Child device id از bearer subject می‌آید.
- Pairing code و exchange token یک‌بارمصرف‌اند؛ server hash ذخیره می‌کند و row lock/transaction و install-id duplicate check در کد موجود است. این موارد source-level‌اند؛ `PairingTest`، `AuthTest` و cross-family tests اجرا نشده‌اند.
- JWT claims دیده‌شده شامل issuer, subject, type, session id, issued-at, expiry است؛ audience/نسخهٔ API/min-client claim دیده نشد.

## Event و notification contract

- `device_events` رویدادهای telemetry/domain را ذخیره می‌کند؛ دسته‌ای از event types با `NotificationRepo` به `family_notifications` projection می‌شوند. projection در همان تراکنش/connection event رخ می‌دهد، نه با SSE/Redis/FCM worker.
- `notification_user_state` read state را برای user نگه می‌دارد؛ statusهایی مثل acknowledged/resolved روی notification family record است. Parent Android و Web از همین resourceها استفاده می‌کنند.
- Server push در health به‌صراحت `not_configured` است. در کد backend بررسی‌شده endpoint/FCM delivery یا SSE stream دیده نشد؛ clientها polling/refresh دارند.

## تغییر Phase 16 در data contract/timezone

ممیزی نشان داد `UsageCollector` تاریخ محلی Android را می‌فرستد؛ `POST /v1/device/sync` ابتدا `appManagement.timeZoneId` را sync می‌کند و بعد usage را می‌پردازد. `UsageService` قبلاً خواندن/روزمره‌سازی را با timezone JVM پیش‌فرض می‌کرد. اصلاح محدود بعد از ممیزی: `UsageService.deviceZone(deviceId)` اکنون timezone ذخیره‌شده را (fallback UTC) برای date validation، timestamp boundaries، today/weekly/detail, daily report, limit و schedule استفاده می‌کند. این کاهش‌دهندهٔ drift تقویمی است؛ Backend compile و contract/integration test آن **BLOCKED** است، پس fix تأیید runtime نشده.

## نقاط ناتمام/ریسک API

1. **BLOCKED — contract compatibility:** هیچ API واقعی پاسخ نداده؛ mapping سه client به JSON مشترک در source بازبینی شده، نه runtime.
2. **ریسک متوسط — قرارداد دستی:** فیلدها مستقل تعریف شده‌اند و test جامع schema وجود ندارد؛ تغییر نام/optional semantics می‌تواند drift بسازد.
3. **ریسک نسخه:** appVersion/AndroidVersion ثبت می‌شود، ولی endpoint negotiation/minimum supported version پیدا نشد.
4. **محدودیت limiter:** rate limiter process-local است؛ در چند replica، counter مشترک میان processها تضمین نمی‌شود. Redis در implementation جاری پیدا نشد.
5. **status code semantics:** validation فعلی 400 است؛ clientها باید بر اساس `code` و HTTP موجود عمل کنند، نه انتظار فرضی 422.
6. **error completeness:** Backend در `StatusPages` خطای عمومی را به `{code,message}` و 500 تبدیل می‌کند؛ Web برای پاسخ غیر JSON به HTTP status fallback دارد. این رفتار با server runtime بررسی نشده.
7. **shared API primary data:** هر سه client به همان `/v1` اشاره می‌کنند؛ هیچ data-free preview/shell یا standalone visual page اثبات دسترسی به protected API نیست.

## آزمون‌های این گزارش

- **PASS:** Web tests: `2` test files، `4/4` tests.
- **PASS:** `npm run build` شامل `tsc -b` و Vite production bundle.
- **BLOCKED:** Backend compile/test؛ محیط Java 11 است (CI به Java 17 نیاز دارد)، Gradle/wrapper نیست و Testcontainers به Docker نیاز دارد.
- **BLOCKED:** Android compile/tests؛ Gradle/wrapper و Android SDK/adb موجود نیستند.
- **BLOCKED:** API contract/integration/E2E؛ Backend، DB، credentials و deviceها در دسترس/اثبات نشده‌اند.
