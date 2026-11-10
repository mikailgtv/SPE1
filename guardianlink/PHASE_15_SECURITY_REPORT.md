# Phase 15 — گزارش امنیت frontend

## کنترل‌هایی که در کد Web پیاده‌سازی شدند

- Token response/body مطابق Jackson `SNAKE_CASE` نرمال می‌شود. Access/refresh tokens در React state/refs حافظه‌ای می‌مانند؛ هیچ token در localStorage/sessionStorage/URL/log/SW cache نیست. reload عمداً sign-in مجدد می‌خواهد.
- `fetch` با `credentials: omit`, `cache: no-store`; API base فقط same-origin relative path می‌پذیرد؛ origin مطلق یا `//host` هنگام startup رد می‌شود. Dev proxy target از `API_PROXY_TARGET` server-only است، نه `VITE_`.
- Refresh روی 401، single-flight rotation و یک retry؛ 403 به refresh تبدیل نمی‌شود. Logout تلاش می‌کند refresh session را revoke کند و همیشه client cache/token را پاک می‌کند.
- QueryClient با خروج/بی‌اعتبارشدن نشست پاک می‌شود تا دادهٔ family قبلی بین کاربران نمایش داده نشود. Server family/device ownership همچنان authorization مرجع است.
- Caddy CSP: `default-src 'self'`, no object/base injection, frame-ancestors none, scripts self-only, connect self; `style-src 'unsafe-inline'` فقط برای styleهای محاسباتی عددی نمودار/نوارها و صفحهٔ offline (بنابراین CSP کاملاً سخت‌گیرانه نیست). `Permissions-Policy` دوربین/microphone/location مرورگر را می‌بندد؛ Web از location خود browser استفاده نمی‌کند.
- `Referrer-Policy: no-referrer`, `X-Frame-Options: DENY`, `X-Content-Type-Options: nosniff`, HSTS و حذف Server header در Caddy.
- JSON در React به‌صورت text نمایش می‌یابد؛ HTML تزریقی استفاده نشده. Metadata اعلان‌ها فقط allowlist محدودی از فیلدهای non-content را نمایش می‌دهد.
- مسیر عمومی `/preview` فقط متن/ساختار رابط را نشان می‌دهد؛ هیچ درخواست API، دادهٔ نمونه یا mutation ندارد و دورزدن auth نیست. Pairing code فقط پس از کلیک، در state حافظه‌ای نمایش داده می‌شود؛ به clipboard/storage نمی‌رود.
- هیچ contact/call log/camera permission/page content/search/message collection یا third-party map/SOS upload وجود ندارد.

## گیت/ریسک باز

- `deploy/Caddyfile` و Compose با Docker/Caddy validate نشده‌اند؛ Docker در محیط حاضر نیست. Same-origin TLS/headers را در host واقعی بررسی کنید.
- Backend هنوز production readiness `NOT READY` دارد (Phase 13); API auth, DB isolation/RLS, ownership با دو خانواده، rate limit چند replica، backup/recovery و runtime tests در این کار اصلاح/اثبات نشده‌اند.
- هیچ API real-world integration test یا penetration test اجرا نشده؛ frontend testها security assurance backend نیستند.
- Refresh token memory-only UX را قربانی persistence می‌کند؛ به‌جای ذخیرهٔ browser token یا اضافه‌کردن BFF جدید، این trade-off مستند شده است.
- `npm audit --audit-level=moderate` در ۲۰۲۶-۱۰-۰۲: ۰ vulnerability گزارش‌شده. Dependency source/provenance/SBOM و action pinning جداگانه هنوز release gate هستند.

**نتیجهٔ security:** frontend hardening پایه دارد؛ نه audit مستقل، نه backend approval، نه production security sign-off.
