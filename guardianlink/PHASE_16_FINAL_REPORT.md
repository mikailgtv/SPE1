# Phase 16 — گزارش نهایی یکپارچگی Android + Web + Backend

**وضعیت Phase 16: `BLOCKED_BY_ENVIRONMENT`**

این وضعیت یعنی اتصال clientها به API مشترک در source وجود دارد، ولی Backend/DB/device/browser E2E و buildهای Android/Backend در این محیط قابل اجرا نبودند؛ پس یکپارچگی عملیاتی و اثر runtime تأیید نشده است. افزون بر مانع محیطی، محدودیت‌های قطعی feature و دو اصلاح محدود در timezone و fail-closed location consent وجود دارد که هنوز compile/test/device-verify نشده‌اند.

**وضعیت کلی Release از Phase 13: `NOT READY` و بدون تغییر باقی است.** وضعیت Phase 15 یعنی `READY_WITH_LIMITATIONS` فقط دربارهٔ frontend بوده و گیت‌های Phase 13 را نمی‌بندد.

## نتیجهٔ ممیزی و تغییر کد

ممیزی source قبل از تغییر کد انجام شد و خلاصهٔ baseline در `PHASE_16_PRECHANGE_AUDIT.md` ثبت است. پس از آن دو اصلاح محدود و مرتبط انجام شد:

1. در `server/src/main/kotlin/ir/guardianlink/server/UsageService.kt`، date/time usage قبلاً در upload با timezone دستگاه ولی در چند مسیر گزارش، limit، schedule و timestamp با timezone JVM تفسیر می‌شد. اکنون این مسیرها از timezone ثبت‌شدهٔ device استفاده می‌کنند؛ اگر هنوز ثبت نشده باشد، UTC fallback می‌شود.
2. در `android/feature/childstatus/.../StatusSyncWorker.kt`، Worker قبلاً fix می‌گرفت و در status sync می‌فرستاد پیش از آنکه پاسخ سرور sharing را تأیید کند؛ preference محلی می‌توانست null/قدیمی باشد. اکنون status sync اول بدون مختصات است؛ فقط پاسخ `locationSharing=true` اجازهٔ capture و ارسال batch از endpoint موجود location sync را می‌دهد؛ false/null صف را پاک می‌کند.

هر دو تغییر در source اعمال شده‌اند؛ Android/Backend compile، unit/integration tests، permission/device و runtime آن‌ها **BLOCKED** هستند. بنابراین هیچ‌کدام verified محسوب نمی‌شوند. تغییر غیرمرتبط دیگری در code/migration انجام نشد.

## معماری و مرز integration

- Android Parent، Android Child و Parent Web از `/v1` یک Backend Ktor استفاده می‌کنند؛ Backend از Repositoryها به PostgreSQL متصل است. Web در deploy single-origin از Caddy static hosting و reverse proxy به همان Backend استفاده می‌کند.
- Android/Web پایگاه دادهٔ primary جدا ندارند. Android صرفاً credential، settings غیرحساس و صف/cache محدود دارد؛ Web React Query cache حافظه‌ای و shell آفلاین دارد. Service Worker `/v1` را cache نمی‌کند.
- Parent و Child principal/session جدا دارند؛ Parent routes family ownership check دارند و Child identity از device JWT می‌آید. pairing server-backed one-time code/exchange/confirm است.
- سناریوهای اصلی usage/location/geofence/app/Web Safety/SOS/event/notification در source/API وجود دارند؛ capability واقعی محدود به شرح پایین است.
- **هیچ live flow بین سه client و DB اجرا نشده؛** این نتیجه source-level است، نه E2E pass.

## ماتریس نهایی component/build/runtime/integration/E2E/status

| جزء | Build/Tests | Runtime | Integration | E2E | وضعیت Phase 16 |
|---|---|---|---|---|---|
| Android Parent | **BLOCKED** — Gradle/wrapper و Android SDK غایب؛ Java 11 است | **BLOCKED** — app/device ندارد | **BLOCKED** — Backend/credentials ندارد | **BLOCKED** — browser/device flow اجرا نشد | API mapping از source؛ نه build/pass فعلی |
| Android Child | **BLOCKED** — همان محدودیت Gradle/SDK | **BLOCKED** — emulator/device ندارد | **BLOCKED** — pairing/API اجرا نشد | **BLOCKED** — مجوز/سینک واقعی نشد | source capability review؛ fail-closed location gate و enforcement هنوز build/device-verify نشده‌اند |
| Parent Web | **PASS** — `npm test` (۴/۴)، `npm run build` | **BLOCKED** — browser/runtime E2E انجام نشد | **BLOCKED** — API/backend/credential ندارد | **BLOCKED** — لاگین و دادهٔ واقعی تست نشد | Web source build/test pass؛ integration نه |
| Backend Ktor | **BLOCKED** — Java 17/Gradle لازم، حاضر نیست | **BLOCKED** — server بالا نیامد | **BLOCKED** — PostgreSQL/Docker/Testcontainers نیست | **BLOCKED** — end-to-end نیست | source فقط؛ timezone fix unverified |
| PostgreSQL/migrations V1–V9 | **BLOCKED** — runtime DB ندارد | **BLOCKED** | **BLOCKED** — migrations/seeds/isolation اجرا نشد | **BLOCKED** | structure review فقط |
| Caddy/Compose/deploy | **BLOCKED** — Docker/Compose نیست | **BLOCKED** — domain/TLS/health probes اجرا نشد | **BLOCKED** | **BLOCKED** | config review فقط |
| Shared account/family/pairing | test source موجود، اجرا **BLOCKED** | **BLOCKED** | **BLOCKED** | **BLOCKED** | route logic دیده شد؛ واقعی اثبات نشد |
| Events/notifications | Web tests به flow کامل مربوط نیستند | **BLOCKED** | **BLOCKED** — polling/per-user read state runtime نشد | **BLOCKED** | Backend push/SSE/FCM ندارد؛ polling model |
| Usage/timezone | Backend test **BLOCKED** | **BLOCKED** | **BLOCKED** — cross-client day comparisons نشد | **BLOCKED** | اصلاح timezone اعمال ولی verify نشده |
| Location/geofence | Android/backend build/test **BLOCKED** | **BLOCKED** — permission/GPS/device | **BLOCKED** | **BLOCKED** | server-derived geofence؛ no Android GeofencingClient |
| App restrictions/Web Safety | build **BLOCKED** | **BLOCKED** — Device Owner/Chrome effect | **BLOCKED** | **BLOCKED** | DPM-gated/limited؛ unsupported/unverified statusها واقعی‌اند |
| SOS/offline sync | build/test **BLOCKED** | **BLOCKED** | **BLOCKED** | **BLOCKED** | outbox/source semantics؛ تحویل واقعی اثبات نشده |

## قابلیت‌ها و محدودیت‌های قطعی که باید در UI/ادعا بمانند

1. **Push/realtime:** Backend health می‌گوید `push_notifications=not_configured`; SSE/FCM server delivery مشاهده نشد. والدها polling می‌کنند؛ تحویل فوری را ادعا نکنید.
2. **Geofence:** موتور server-side از fixها؛ Android `GeofencingClient` نصب/استفاده نشده؛ event جعلی enter/exit از Child پذیرفته نمی‌شود.
3. **App restrictions:** suspend واقعی فقط با Device Owner و API رسمی `DevicePolicyManager`; Normal mode advisory/unsupported. ثبت rule به معنای enforce نیست.
4. **Web Safety:** فقط managed Chrome policy مسیر محدود دارد و اعمال واقعی داخل Chrome خودکار verify نمی‌شود؛ VPN/DNS filtering و visit/content telemetry وجود ندارد.
5. **Usage:** Android Usage Access لازم است؛ گزارش device self-report است؛ collector امروز/دیروز را می‌فرستد و offline usage queue مستقل ندارد.
6. **Location:** مجوز/provider/background location و sharing state لازم‌اند؛ capture تک‌شات عادی و WorkManager با حداقل interval دارد؛ no polling loop. runtime accuracy/freshness نامعلوم است.
7. **SOS:** تحویل فقط به Parentهای family از طریق Backend app؛ emergency service/third party فراخوانی نمی‌شود. صف آفلاین به معنی اطلاع Parent نیست.
8. **دادهٔ پشتیبانی‌نشده:** API تماس/مخاطبان و call logs/contacts یافت نشد؛ camera/photos/private messages/keystrokes/clipboard/cookies/page/search content این integration را تشکیل نمی‌دهند.

## موانع محیطی و نتیجهٔ آزمون‌ها

- محیط: Java 11؛ Gradle و wrapper، Android SDK/adb، Docker/Podman، PostgreSQL/psql/pg_isready و browser automation موجود/اثبات نشدند.
- Backend tests از `PostgreSQLContainer("postgres:17-alpine")` استفاده می‌کنند؛ اجرا بدون Docker ممکن نیست.
- Web-only checks واقعی و موفق: `npm ci --no-audit --no-fund`, `npm test` (۲ فایل/۴ تست)، `npm run build`.
- Phase 5 APKهای موجود، build Phase 16 محسوب نمی‌شوند.
- CI workflow و Trivy config موجود است اما در این Phase اجرا نشده؛ هیچ security scan result ادعا نمی‌شود.
- همهٔ runtime/device/browser/API/DB/CI checks غیرقابل اجرا در `PHASE_16_E2E_REPORT.md` به‌صراحت **BLOCKED** ثبت شده‌اند.

## ریسک‌های integration باقی‌مانده

- Backend/Android compile و tests لازم است، به‌ویژه regression test برای timezone device، DST و midnight boundaries.
- OpenAPI/generated contract و contract tests مشترک برای جلوگیری از drift بین Kotlin/Gson/TypeScript وجود ندارد.
- API version negotiation پیدا نشد؛ version telemetry به معنی compatibility gate نیست.
- RLS و `audit_logs` عمومی در migrations جاریِ scanشده دیده نشد؛ isolation عمدتاً route-level است و DB-level runtime isolation اثبات نشده.
- process-local rate limiting در multi-replica مشترک نیست.
- Web/Android cross-client freshness، notification read-state و pairing با data واقعی باید آزمایش شود.
- docs/diagrams قدیمی، ادعاهای SSE/Redis/FCM/Room/Tink/RLS/audit را مانند واقعیت جاری نشان می‌دهند و باید جداگانه به‌روز شوند؛ این ادعاها در گزارش Phase 16 پذیرفته نشده‌اند.

## گام‌های لازم برای عبور از BLOCKED

1. فراهم‌کردن JDK 17، Gradle 9.7.1 (یا wrapper معتبر) و Android SDK؛ اجرای Backend tests, Android module tests/lint/build.
2. اجرای PostgreSQL/Testcontainers یا DB مجاز ایزوله؛ اجرای migrations V1–V9، cross-family/device isolation، pairing expiry/replay، event/notification و timezone regression.
3. بالا آوردن یک Backend+DB آزمایشی با secrets و credentials بی‌خطر؛ اجرای Web و Android Parent با همان user/family و Child واقعی/امولاتور برای verify pairing، refresh/revoke، settings, status, usage, location, geofence, rules, notifications و SOS.
4. تست‌های رسمی permission و managed-device جداگانه؛ هر capability unsupported/denied/unverified در UI باید همان‌طور بماند.
5. پس از موفقیت، ثبت نسخهٔ app/commit، test commands/logs، device/API level، backend config و evidence برای هر matrix row؛ سپس گزارش را از BLOCKED به status مبتنی بر شواهد ارتقا دهید.

## تصمیم نهایی

# `BLOCKED_BY_ENVIRONMENT`

این Phase از روی source نشان می‌دهد مسیرهای API مشترک طراحی/سیم‌کشی شده‌اند، اما یکپارچگی live، builds/testهای Backend/Android، device behavior، DB isolation و E2E هنوز ثابت نشده‌اند. بنابراین این status نه `FULLY_INTEGRATED` است، نه مجوز release؛ release کلی همچنان **`NOT READY`** طبق Phase 13 باقی می‌ماند.
