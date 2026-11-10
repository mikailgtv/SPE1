# Phase 15 — گزارش معماری و اتصال API

**وضعیت:** پیاده‌سازی frontend انجام شد؛ اتصال end-to-end به backend قابل اثبات نبود. ممیزی اولیه پیش از Web code در `PHASE_15_INITIAL_AUDIT.md` ثبت شده است.

## انتخاب فنی

- React 19 + TypeScript، Vite 7، React Router 7 برای SPA؛ ساختار project قبلی Web نداشت.
- TanStack Query برای cache کوتاه‌مدت، query invalidation پس از تغییر، error/loading states و refresh محدود SOS/notification. intervalها به‌ترتیب ۳۰ و ۶۰ ثانیه؛ WebSocket/SSE/push ادعا نشده.
- Translation dictionary داخلی کوچک، بدون CDN فونت/اسکریپت؛ CSS محلی و inline SVG iconها برای کار در sandbox/preview و RTL/LTR.
- PWA shell اختیاری؛ service worker فقط offline HTML/manifest/icon را cache می‌کند و `/v1/*` را network-only/خارج از SW می‌گذارد.
- هیچ backend، schema، DB یا API endpoint جدیدی ساخته نشده است.

## قرارداد API و اتصال

- Client به مسیرهای relative `/v1/...` درخواست می‌دهد.
- Jackson در `server/Main.kt` روی `PropertyNamingStrategies.SNAKE_CASE` تنظیم شده؛ client پاسخ‌های JSON را از snake_case به camelCase داخلی تبدیل می‌کند و کلیدهای body را camelCase→snake_case می‌فرستد. آزمون refresh همین contract را پوشش می‌دهد.
- Dev: Vite server-side proxy با `API_PROXY_TARGET`؛ browser هرگز `localhost` یا origin خارجی را برای API صدا نمی‌زند.
- Prod: Caddy از `web/dist` static را سرو می‌کند و `/v1/*`, `/health*` را به همان `server:8080` proxy می‌کند. UI و API هم-origin؛ DB همان PostgreSQL server فعلی است.
- API ownership/family/device authorization فقط سروری است؛ UI فهرست device را برای UX اعتبارسنجی می‌کند، نه مجوز.

## صفحه‌ها و endpointهای واقعی

| صفحه | درخواست‌های اصلی |
|---|---|
| ورود/ساخت حساب | `POST /v1/auth/login`, `/register`; `POST /refresh`, `/logout`; `GET /v1/me` |
| Overview/family | `GET /v1/families/me`, `/families/me/devices`, notification unread count, active SOS؛ ساخت family از `POST /v1/families` |
| Devices | list, device removal `DELETE /v1/families/me/devices/{id}`, pairing `POST /v1/pairing/sessions` |
| Screen time | `GET /screen-time/report`, `POST /screen-time/settings`؛ weekly/today/top apps از report واقعی |
| Location | `GET location/current/history/settings`, `POST location/settings` |
| Safe Zones | `GET/POST/DELETE geofences`؛ update route موجود است اما UI فعلی ویرایش کامل zone ندارد |
| App restrictions | `GET apps`, `GET/POST/PUT/DELETE app-rules` و enforcement capability/status |
| Web Safety | `GET/PUT web-safety`, rules CRUD، `GET events`; policy/config metadata، نه browser content |
| Notifications | list/unread, read/acknowledge/resolve/read-all, preferences GET |
| SOS | active/history, acknowledge/resolve/location state; polling هر ۳۰ ثانیه |
| Reports | per-device/per-day `GET usage/report/daily`; export تجمیعی وجود ندارد |
| Settings/Privacy | family monitoring settings و notification preferences GET/PUT؛ نبود مسیرهای account-management در UI روشن است |

Route contract هنوز OpenAPI-generated یا جامع backend contract-test نشده؛ فهرست definitive source در `API_DOCUMENTATION.md` و route files است.

## مدل داده و محدودیت‌های محصول

- backend child user مستقل ندارد؛ family-owned device رکورد Child app است و label/نام ساختگی ساخته نمی‌شود.
- گزارش تماس، مخاطبان و camera/location در backend/permission نیستند؛ UI صریحاً not available می‌کند.
- API یک endpoint جامع reports/export یا backend push ندارد؛ daily usage report واقعی است، summary/export/push جعلی نیست.
- Geofence backend تشخیص می‌دهد و sync delay دارد؛ صفحه non-real-time را اعلام می‌کند.
- Safe-zone map provider برای Web وجود ندارد؛ هیچ map tile یا coordinate به third party ارسال نمی‌شود. مختصات خام از API نشان داده می‌شود؛ grid صرفاً تزئینی/غیرجغرافیایی است.
- Phase 13 final release decision `NOT READY` بدون تغییر باقی است.
