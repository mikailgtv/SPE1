# Phase 15 — گزارش نهایی Web Parent Dashboard

**تصمیم Phase 15: `READY_WITH_LIMITATIONS` — frontend پیاده‌سازی و build شده؛ اتصال واقعی shared API/DB، production deploy و E2E هنوز اثبات نشده‌اند.**  
**وضعیت release کلی پروژه از Phase 13 مستقل و همچنان `NOT READY` است.** این Phase 15 تصمیم Phase 13 را عوض نمی‌کند.

## ممیزی و تصمیم معماری

ممیزی انجام و در `PHASE_15_INITIAL_AUDIT.md` پیش از شروع Web code ثبت شد. Web frontend قبلی وجود نداشت؛ stack جدید React/TypeScript/Vite انتخاب شد. هیچ backend/database جداگانه اضافه نشده است. browser فقط مسیرهای same-origin `/v1` را هدف می‌گیرد؛ Caddy پیکربندی شد تا static Web را سرو و همان Ktor `server:8080` را برای API proxy کند. اجرای Caddy/Compose در این محیط ممکن نبود.

## تحویل‌شده

- Responsive Parent dashboard فارسی RTL / انگلیسی LTR، روشن/تیره، خانواده/دستگاه، Screen Time، موقعیت/تاریخچه، Safe Zones، app rules، Web Safety، notifications، SOS، daily report واقعی، privacy/settings، loading/empty/error، route guard.
- create family, create pairing code, remove device، و mutationهای موجود API برای screen-time settings، location settings، zone create/delete، app rule create/toggle/delete، Web Safety policy/domain rule و notification/SOS actions.
- Auth JSON field conversion مطابق Jackson snake_case؛ memory-only tokens؛ single-flight refresh؛ bearer-only request؛ app data cache با خروج یا invalid شدن auth پاک می‌شود.
- مسیر عمومی `/preview` برای دیدن چیدمان بدون ورود، بدون API، بدون دادهٔ نمونه و بدون عملیات تغییردهنده؛ احراز هویت برای داده/قابلیت واقعی همچنان برقرار است.
- فایل HTML تک‌فایلی `web/GuardianLink-Parent-Dashboard.html`، `.env.example`, راهنمای `WEB_SETUP_GUIDE.md`, و offline PWA shell بدون cache کردن داده/توکن API.
- هیچ تماس، مخاطب، camera permission/location، page content/search/message، fake data/enforcement، map tile، third-party SOS location sharing یا fake emergency-service notification اضافه نشد.

## Status matrix

| معیار | نتیجه |
|---|---|
| ممیزی قبل از پیاده‌سازی | PASS — report ابتدا نوشته شد. |
| Web production build و TypeScript check | PASS — `npm run build`. |
| unit/API/locale tests | PASS — `npm test`: 4 test passed. |
| npm dependency audit | PASS — 0 vulnerabilities در audit اجراشده. |
| Dev-server HTTP smoke test | PASS — `/`, `/preview`, `/login` همگی SPA shell را با HTTP 200 دادند؛ browser workflow نیست. |
| Frontend به API واقعی / همان DB | BLOCKED — backend/staging/database فعال یا credential در دسترس نبود. |
| Caddy/Compose production config | BLOCKED — Docker/Caddy نصب نیست؛ syntax/deploy اجرا نشد. |
| Full browser E2E + auth + family/device authorization | BLOCKED — credentials/test accounts/backend موجود نبود. |
| Android permission/enforcement/physical device checks | NOT RUN — ابزار/دستگاه در دسترس نیست و در scope Web UI تغییر نکرد. |
| Accessibility automated/manual audit | PENDING — semantics/focus/skip link پیاده شد، axe/screen reader/viewport audit اجرا نشد. |
| Security/production approval backend | BLOCKED — Phase 13 gates مستقل باقی‌اند. |

## محدودیت‌های مهم

1. این گزارش ادعای اجرای login با server واقعی یا اثبات دادهٔ مشترک ندارد؛ فقط client route base با source موجود همسو شده است.
2. API contract کامل/generated OpenAPI و exhaustive tests ندارد؛ route/DTO work نیازمند backend contract test است.
3. Web Safety/app enforcement همان status/capability backend را نشان می‌دهد و enforcement موفقیت قطعی تضمین نمی‌شود.
4. API report خروجی CSV/یکپارچه، account/privacy delete/export، password reset، call log، contacts و camera path ندارد؛ unavailable باقی می‌مانند.
5. SOS صفحهٔ Parent است؛ تماس emergency services/ارسال third-party انجام نمی‌شود. Geofence real-time نیست.
6. Web build readiness مساوی production readiness نیست؛ deploy، DB isolation/RLS، security, E2E، backup/restore و Android release gateها بسته نشده‌اند.

## آزمون‌های واقعی

- `web/npm test` — PASS, 4 tests.
- `web/npm run build` — PASS, Vite 7.3.6 / TypeScript.
- `web/npm audit --audit-level=moderate` — PASS, 0 vulnerabilities گزارش‌شده.
- Vite server `/` smoke test با curl — PASS, `HTTP 200`, title درست.
- Docker/Caddy/backend/DB/E2E/Android tests — اجرا نشدند؛ هرگز به‌عنوان PASS شمرده نشده‌اند.

## فایل‌های تکمیلی

- Audit: `PHASE_15_INITIAL_AUDIT.md`
- Architecture/API: `PHASE_15_ARCHITECTURE_REPORT.md`
- UI/UX: `PHASE_15_UI_REPORT.md`
- Security: `PHASE_15_SECURITY_REPORT.md`
- Test: `PHASE_15_TEST_REPORT.md`
- E2E: `PHASE_15_E2E_REPORT.md`
- Setup: `WEB_SETUP_GUIDE.md`
