# ممیزی اولیه Phase 15 — Web Parent Dashboard

**زمان ممیزی:** ۲۰۲۶-۱۰-۰۲. **پیش از هر کد UI جدید ایجاد شد.**  
**مبنای بررسی:** workspace، route/DTO definitions، migrations، `API_DOCUMENTATION.md` و `PHASE_13_FINAL_REPORT.md`. این ممیزی static source review است؛ backend build/runtime و staging قبلاً نیز اجرا نشده‌اند.

## وضعیت واقعی معماری

| لایه | یافتهٔ فعلی | وضعیت |
|---|---|---|
| Web frontend | `package.json`, Vite/React/HTML entry یا Web app موجود پیدا نشد. Wireframeهای قدیمی `docs/wireframes/*.html` صرفاً mock/wireframe هستند و API-connected app نیستند. | ساخت frontend جدید لازم است؛ wireframeها دور انداخته نمی‌شوند اما product code نیستند. |
| Backend مشترک | Ktor/Netty server در `server`; prefix API=`/v1`; PostgreSQL و Flyway V1–V9. | Androidها و Web باید به همین API اشاره کنند؛ هیچ server/DB دوم ساخته نمی‌شود. |
| Parent/Child | Android applications مستقل در `android/app-parent` و `android/app-child`. | Web فقط Parent client؛ Child remains Android. |
| Auth | `POST /v1/auth/login`, `/refresh`, `/logout`, `GET /v1/me`; bearer user/device tokens. Access JWT کوتاه‌عمر و refresh rotation در backend؛ no browser cookie/BFF endpoint. | Web token فقط در memory نگه داشته می‌شود؛ reload/sign-out از session memory خارج می‌شود. Token در localStorage/sessionStorage ثبت نمی‌شود. |
| Family/Device model | `Family` شامل Parent members؛ Child به‌عنوان `DeviceRow` متصل به family است؛ routeهای device scoped `ownedDevice` را بررسی می‌کنند. موجودیت Child user مستقلی ندارد. | UI نام `deviceName`/device record را نمایش می‌دهد؛ child name/person field از خودمان نمی‌سازیم. |
| Database | V1–V9 و PostgreSQL tables برای users/families/devices/status/usage/location/geofence/rules/web safety/notifications/SOS. RLS/production runtime اثبات نشده؛ Phase13 `NOT READY` مانده. | Web به DB مستقیم وصل نمی‌شود؛ تمام درخواست‌ها از API مشترک. |
| Browser/CORS | Ktor CORS configuration مشاهده نشد؛ deploy/static same-origin پشت Caddy به‌صورت پیشنهادی قابل انجام است. | UI از relative `/v1` استفاده می‌کند؛ Dev Vite proxy server-side است. Production نیازمند same-origin reverse proxy/static hosting. CORS bypass اضافه نمی‌شود. |
| Real-time/push | WebSocket/SSE/API push در route inventory پیدا نشد؛ Notification Center API موجود است، FCM/server push نیست. | polling محدود/قابل توقف؛ push/live بودن fake نمی‌شود. |
| Device status | `/families/me/devices` شامل `deviceName`, battery, lastSeen/lastSync, status/presence/health/connection, permissions, app/android versions است. | فقط فیلدهای پاسخ backend نمایش داده می‌شوند؛ unavailable fields پنهان یا Not available. |
| Screen Time/App Usage | `usage/today`, `usage/weekly`, `usage/apps`, `screen-time/report` و app catalog/rules endpoints در کد موجود است. | server data source؛ coverage و semantics نیازمند API run است. |
| Location | `location/current`, `history`, `settings`; fields شامل fix/sharing/permission/provider/health/retention. | مختصات فقط پس از API response و family auth؛ عدم دسترسی یا stale دقیق گزارش می‌شود. |
| Safe Zones | Geofence CRUD, event history; تشخیص server-side از sync location و تأخیر ممکن است ≥۱۵ دقیقه باشد. | UI real-time/instant enforcement ادعا نمی‌کند. |
| App Restrictions | App catalog/rules/events و `managementMode`, `enforcementCapability`, `lastEnforcementStatus` وجود دارند. | وضعیت enforcement از response؛ Normal mode blocker را وانمود نمی‌کند. |
| Web Safety | settings/rules/overrides/events و mode/status/reason fields؛ server عمداً visit URL/content feed ندارد. | فقط policy/status metadata؛ content/visited URLs/queries نمایش/جمع‌آوری نمی‌شود. |
| Notifications | family feed, unread count, filters, read/ack/resolve/preferences. | in-app feed؛ push delivery ادعا نمی‌شود. |
| SOS | active/history/detail/location/events/notes/acknowledge/resolve؛ status state machine موجود. | UI دقیق state، location nullable؛ emergency services اطلاع داده نمی‌شوند. |
| Reports/export/delete | منبع گزارش ترکیبی از چند API هست اما `/reports`/CSV export/account/family export/delete endpoint پیدا نشد؛ location history delete در scope device موجود است. | دکمهٔ Export/Delete جعلی ساخته نمی‌شود؛ explicit `Not available`. |
| Calls/contacts/camera | Backend route/table/permission برای call logs یا phone contacts مشاهده نشد. Camera permission هم در Parent/Child manifest موجود نیست؛ دوربین روش تشخیص مکان نیست. | این درخواست‌ها پیاده نمی‌شوند، permission جدید افزوده نمی‌شود؛ UI `Not available`/عدم پشتیبانی را شفاف می‌گوید. |
| Dependency/build tools | `node`, `npm`, `npx` حاضرند؛ `gradle`, wrapper, Docker, psql, adb, gh حاضر نیستند؛ npm package/Web code موجود نبود. | frontend build قابل تلاش است؛ Backend integration/real auth در این workspace قابل اثبات نیست. |

## Route inventory (منبع حقیقت)

مسیرهای Parent از `server/src/main/kotlin/ir/guardianlink/server/*Routes.kt` و `API_DOCUMENTATION.md` استخراج شدند:

- Auth/Profile/Family/Devices: `/v1/auth/{register,login,refresh,logout}`, `/v1/me`, `/v1/families`, `/v1/families/me`, `/v1/families/me/devices[/{id}]`.
- Monitoring: `/v1/families/me/devices/{id}/{health,permissions,events}`, `/v1/families/me/settings`, `/v1/families/me/events`.
- Usage: per-device `/usage/{today,weekly,apps,apps/{pkg}/detail,apps/{pkg}/icon,report/daily}` و `/screen-time/{report,settings,schedules}`.
- App Management: `/apps`, `/apps/{packageName}/usage`, `/app-rules`, `/app-events` و app-rule CRUD.
- Location: `/location/{current,history,settings}` و location history delete/settings.
- Geofences: `/geofences` CRUD, `/geofences/{gid}/events`, `/geofence-events`.
- Web Safety: `/web-safety`, `/rules` CRUD, `/overrides`, `/events`.
- Notifications: `/families/me/notifications`, `/unread-count`, `/{id}` read/ack/resolve, `/preferences`.
- SOS: `/families/me/sos/{active,preferences}`, `/sos/{id}/{location,events,notes,acknowledge,resolve}`, device history.
- Service health: `/health`, `/health/live`, `/health/ready`.

این فهرست source-derived است؛ همهٔ APIها هنوز build/runtime/contract-test نشده‌اند.

## طراحی امنیتی وب پیش از implementation

- Browser همان origin و `/v1` را صدا می‌زند؛ Dev Vite proxy سرور-به-سرور به API local است، نه fetch مستقیم browser به localhost.
- Access/refresh token فقط در memory؛ پس از reload، ورود دوباره لازم است؛ refresh فقط در memory و هنگام 401. logout تلاش برای backend revoke و سپس پاک‌کردن memory.
- درخواست‌ها bearer user token؛ هیچ family/device id به‌عنوان authorization اعتماد نمی‌شود؛ backend principal/family check همچنان مرجع نهایی است.
- API client واحد، error mapping 401/403/404/409/422/429/5xx/offline، no body logging.
- frontend هیچ static fixture، mock API, fake usage/location/SOS, fake enforcement یا call/contacts/camera collection ندارد.

## گیت‌های runtime

در حال حاضر `PHASE_13_FINAL_REPORT.md` وضعیت کلی پروژه را NOT READY نشان می‌دهد. Node/npm فراهم است، ولی backend runtime/PostgreSQL، credentials, staging URL، browser API account و Android device نداریم. Web UI ممکن است locally build/serve شود؛ این به‌تنهایی Auth/Backend Integration/E2E pass نیست.
