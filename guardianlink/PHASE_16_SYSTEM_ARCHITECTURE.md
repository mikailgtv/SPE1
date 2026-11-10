# Phase 16 — معماری واقعی سامانه

**مبنای گزارش:** ممیزی ایستای source جاری، migrations و deploy config؛ نه استنتاج از طرح‌های قدیمی. **runtime زنجیرهٔ کامل در این محیط مشاهده نشده است.**

## نمودار معماری

```text
┌─────────────────────────┐       ┌─────────────────────────┐
│ Android Parent          │       │ Android Child           │
│ حساب Parent / UI        │       │ pairing / مجوز / sync    │
│ HTTPS client + SecureStore│     │ WorkManager + queues    │
└───────────┬─────────────┘       └────────────┬────────────┘
            │                                  │
            │             HTTPS /v1            │
            └────────────────┬─────────────────┘
                             │
┌────────────────────────────▼─────────────────────────────┐
│ Parent Web (React/Vite)                                  │
│ API client: same-origin, Bearer, refresh-on-401           │
│ tokens فقط در حافظهٔ tab؛ UI-only /preview بدون API       │
└────────────────────────────┬─────────────────────────────┘
                             │ /v1/*
                    ┌────────▼─────────┐
                    │ Caddy (اختیاری)  │  Web static + reverse proxy
                    │ HTTPS / headers │
                    └────────┬─────────┘
                             │ private Compose network
                    ┌────────▼─────────┐
                    │ Ktor Backend     │
                    │ Auth / policy /  │
                    │ routes/services  │
                    │ DB repositories │
                    └────────┬─────────┘
                             │ JDBC / transactions
                    ┌────────▼─────────┐
                    │ PostgreSQL 17    │
                    │ migrations V1–V9 │
                    │ منبع دادهٔ اصلی │
                    └──────────────────┘
```

در development، Web از Vite proxy و Android از base URL محیطی استفاده می‌کنند. در edge deployment، Caddy فایل Web را از `web/dist` سرو و `/v1` و `/health` را به همان Ktor proxy می‌کند. Compose یک سرویس PostgreSQL و یک Backend دارد؛ هیچ دیتابیس جداگانهٔ Web یا Android برای دادهٔ اصلی در source دیده نشد. Caddy/Compose در Phase 16 اجرا نشده‌اند.

## اجزای اصلی و مالکیت داده

| جزء | نقش و داده |
|---|---|
| Android Parent | login/registration، خانواده، ایجاد pairing، داشبورد، device health/status، usage، location/history، geofence، app rules، Web Safety، notification center، SOS. دادهٔ اصلی را از API می‌خواند/تغییر می‌دهد؛ credential در storage امن Android. |
| Android Child | pairing/auth نوع device؛ snapshot وضعیت/مجوز/نسخه، UsageStats، location تک‌شات، app inventory/events، Web Safety status/events، SOS. کار دوره‌ای با WorkManager و محدودیت شبکه. |
| Parent Web | UI والد برای dashboard، devices/pairing، usage، location، zones، app restrictions، Web Safety، notifications، SOS، reports/privacy/settings. API client مستقل TypeScript، same-origin `/v1`؛ داده را در DB جدید ذخیره نمی‌کند. |
| Ktor Backend | JWT/session validation، خانواده و ownership، pairing، اعتبارسنجی DTO، محاسبه status، domain services و persistence. Event → notification projection در مسیر همزمان DB. |
| PostgreSQL | منبع حقیقت برای user/family/device/session/pairing، status/telemetry، usage، location/settings، geofence state/events، app inventory/rules/events، Web Safety settings/rules/events، notification state/preferences، SOS session/location/events/notes. ساختار از migrations V1–V9 است. |

Android `SecureStore` از AES-GCM و Android Keystore به‌همراه DataStore استفاده می‌کند؛ `PlainPrefs` برای تنظیمات غیرحساس است. صف‌های آفلاین محدود و محلی‌اند، نه پایگاه دادهٔ اصلی. Web در tab از React Query cache حافظه‌ای استفاده می‌کند؛ tokenها پایدار ذخیره نمی‌شوند. `localStorage` صرفاً برای theme/language استفاده می‌شود. Service Worker فقط shell ثابت offline را cache می‌کند، نه API.

## Auth، حساب و pairing

- Parent token: JWT EdDSA/Ed25519، claimهای `iss`, `sub`, `typ=user`, `sid`, `iat`, `exp`؛ TTL پیش‌فرض از config. Refresh token opaque تصادفی، hash آن در DB و rotation در refresh. `requireUser` در هر درخواست session را در DB بررسی می‌کند.
- Child token: `typ=device`, subject برابر device id و session مستقل؛ `requireDevice` session، status فعال device و revoke را بررسی می‌کند. credentialهای Parent و Child قابل‌جایگزینی نیستند.
- مسیر pairing: Parent `POST /v1/pairing/sessions` → کد ۸ نویسه‌ای کوتاه‌عمر/QR payload → Child `exchange` → exchange token → `confirm` → `device_id` و secret یک‌باره. code/token/secret در سرور hash می‌شوند. `FOR UPDATE`/تراکنش و بررسی install id/expiry/مصرف وجود دارد. تست‌های source برای حالت‌های pairing/family موجودند اما اجرا نشده‌اند.
- هر دو Parent Android و Web می‌توانند session بسازند. Web کد را فقط در state حافظه نگه می‌دارد. مرحلهٔ exchange/confirm را Android Child انجام می‌دهد.
- خانواده از principal احراز هویت‌شده استخراج می‌شود. Parent device routes از `ownedDevice` عبور می‌کنند؛ foreign family/device در کد به `404` محدود می‌شود. Child endpoints identity را از device token می‌گیرند، نه از `device_id` ارسالی.

## قرارداد API و داده

- Prefix فعلی `/v1`; Backend Ktor/Jackson با `SNAKE_CASE`; Android Gson با `LOWER_CASE_WITH_UNDERSCORES`; Web ورودی را recursively camelCase و خروجی را snake_case می‌کند.
- خطای استاندارد Backend `{code,message}` است. خطای validation از HTTP 400 است؛ 401، 403، 404، 409، 410، 429 و 500 نیز در مسیرهای مربوط تعریف شده‌اند.
- DTOها در Backend/Android/Web جداگانه نگهداری می‌شوند. OpenAPI/codegen یا contract test جامع مشترک شناسایی نشد؛ بنابراین مطابقت کامل فیلدها صرفاً از source mapping استنباط می‌شود، نه از یک schema واحد.
- API نسخهٔ کلاینت/minimum-client compatibility negotiation ندارد. نسخهٔ Android/app و OS در device metadata ثبت می‌شود.

## Sync، Presence و اعلان

- Child status `POST /v1/device/sync` شامل battery، connection، نسخه‌ها، model/manufacturer، permission snapshot، usage، وضعیت permission/provider مکان (بدون مختصات)، app management، Web Safety و تعداد نقاط صف‌شده است. اگر پاسخ server `locationSharing=true` باشد، نقاط location با درخواست جدا به `/v1/device/location/sync` ارسال می‌شوند. Backend ترتیب پردازش را در route تعیین می‌کند: status ingest، app-management sync/timezone، Web Safety sync، usage، location، سپس پاسخ health/interval/policyها.
- WorkManager دوره‌ای حداقل ۱۵ دقیقه و دارای network constraint است؛ یک‌بار sync در زمان شروع/وصل شبکه نیز enqueue می‌شود. schedule سرور زیر حداقل ۱۵ دقیقه clamp می‌شود.
- device status و سلامت از heartbeat/last sync، battery، connection و permission snapshot در DB محاسبه می‌شود؛ Presence lease است، نه اتصال realtime اثبات‌شده.
- eventهای منتخب توسط `DeviceEventRepo` ثبت و projectionهای notification با همان اتصال/تراکنش DB تولید می‌شوند. این مسیر outbox/worker عمومی نیست.
- والدها از API polling می‌کنند؛ Android Parent داشبورد را هر ۳۰ ثانیه refresh می‌کند. Web اعلان unread را هر ۶۰ ثانیه و SOS باز را هر ۳۰ ثانیه refresh می‌کند. Notification Center Android عمدتاً هنگام ورود/فیلتر/عملیات بارگذاری می‌کند. push server-side، SSE، Redis broker و FCM server delivery مشاهده نشد؛ health آن را `not_configured` اعلام می‌کند.
- `is_read` برای Parent جاری per-user نگهداری می‌شود؛ acknowledge/resolve وضعیت اعلان را در family feed تغییر می‌دهد. پس از refresh API، Web و Androidِ همان user باید همان read-state بگیرند؛ live cross-client runtime تأیید نشده است.

## Location، geofence و screen time

- موقعیت عادی: Child ابتدا وضعیت permission/provider را همراه sync بدون مختصات می‌فرستد. فقط اگر پاسخ authenticated سرور `locationSharing=true` باشد، یک single-shot `FusedLocationProviderClient` (یا `LocationManager` رسمی fallback) گرفته و batch صف را از endpoint جداگانهٔ `/v1/device/location/sync` می‌فرستد؛ `false` یا `null` باعث پاک‌کردن صف و عدم capture/upload عادی می‌شود. حد انتظار ۱۲ ثانیه است؛ last-known قدیمی‌تر از ۴۸ ساعت ارسال نمی‌شود. محل ذخیرهٔ صف با Keystore امن است. پس‌زمینه‌مجوز/خاموشی سرویس باید صادقانه `granted`/`background_granted`/`denied` یا provider-disabled گزارش شود.
- Parent موقعیت جاری/تاریخچه/تنظیم sharing را از API می‌گیرد؛ location sharing خاموش باعث توقف capture و پاک‌شدن صف محلی در sync بعدی می‌شود. runtime accuracy/freshness/permission effect آزمایش نشده است.
- Geofence transition سمت سرور از location fixهای معتبر ارزیابی می‌شود؛ hysteresis دقت، cooldown، state machine و history در service/repository است. از Android `GeofencingClient` استفاده نمی‌شود و payload enter/exit Child به‌تنهایی transition ایجاد نمی‌کند. Child صرفاً zoneهای خودش را می‌خواند.
- Usage با `UsageStatsManager` و مجوز مستقل Usage Access جمع می‌شود (فقط امروز و دیروز، حداکثر ۶۰ app/day در collector). این داده self-report دستگاه است. Backend صفحه‌ها/summary/schedule/limit را روی دادهٔ ذخیره‌شده محاسبه می‌کند؛ enforcement سراسری Screen Time در Android ثابت نشده است.
- Phase 16 ناسازگاری قبلی timezone را در `UsageService` اصلاح کرده است: اکنون upload validation، safe timestamps، today/weekly/detail، daily report، limit و schedule از timezone ثبت‌شدهٔ device استفاده می‌کنند؛ در نبود state به UTC fallback می‌شود. تغییر هنوز compile/test backend نشده است.

## App restrictions، Web Safety و SOS

- App rules در سرور versioned/optimistic update هستند؛ Child snapshot را در SecureStore cache می‌کند. اجرای `DevicePolicyManager.setPackagesSuspended` فقط زمانی مجاز است که اپ Device Owner باشد. در حالت normal یا بدون Usage Access، statusهای unsupported گزارش می‌شوند؛ ساخت restriction در UI/DB، اعمال واقعی را تضمین نمی‌کند.
- Web Safety: مسیر پشتیبانی‌شده managed Chrome policy از `DevicePolicyManager` است؛ readback نشان می‌دهد تنظیم ثبت شده اما اثر واقعی Chrome قابل تأیید نیست. VPN/DNS unsupported، category provider unavailable و blocked-visit/content telemetry وجود ندارد.
- SOS در Child با تأیید کاربر/شمارش معکوس ایجاد می‌شود؛ optional location/tracking بر مجوز/اشتراک/provider و محدودیت Android تکیه دارد. Tracking دارای foreground service و اعلان قابل‌مشاهده است؛ SOS مستقل می‌تواند بدون location ثبت شود. SOS در Backend family-scoped و دارای state/events/notes/location است. درخواست آفلاین queue امن و expire می‌شود؛ تحویل Parent تا پاسخ سرور موفق اثبات نشده باشد، UI هم آن را «تحویل‌نشده» نشان می‌دهد. سرویس امدادی ثالث فراخوانی نمی‌شود.

## اختلاف با اسناد قدیمی

بخش‌هایی از `docs/01-architecture-and-tech-stack.md`, `docs/02-project-structure.md`, `docs/03-database-schema.md`, API/auth docs و SVGهای معماری طرح‌های قدیمی را مانند قابلیت موجود شرح می‌دهند. ادعاهای `SSE`, `Redis`, `FCM`, `Room`, `Proto DataStore/Tink`, `audit_logs` و RLS با implementation جاریِ بررسی‌شده تطابق ندارند. در این گزارش source جاری مرجع است؛ نمودار قدیمی evidence اجرایی نیست.

## محدودیت‌های اثبات

این یک توصیف از source و deploy config است. اجرای Backend/PostgreSQL/Compose، pairing با دو device، UI با backend واقعی، notification cross-client، offline recovery، مجوز Android، geofence واقعی، app suspension، Chrome policy و SOS end-to-end در این محیط انجام نشده‌اند. وضعیت تک‌تک آن‌ها در `PHASE_16_E2E_REPORT.md` آمده است.
