# Phase 15 — گزارش E2E و اتصال

## نتیجهٔ اجرا

**BLOCKED — end-to-end با backend واقعی اجرا نشد.** در workspace، PostgreSQL/server process/staging URL و test Parent account نداریم؛ Docker, Gradle, psql و adb نصب نیستند. هیچ دادهٔ مصنوعی یا mock-server برای شبیه‌سازی integration ساخته نشد. Dev server بالا آمد و فقط HTTP smoke test مسیرهای `/`, `/preview`, `/login` با 200 انجام شد؛ این browser E2E نیست و backend requestها در این محیط موفق نخواهند شد.

## سناریوهایی که با backend واقعی باید اجرا شوند

| سناریو | نتیجه فعلی | ورودی/اثبات موردنیاز |
|---|---|---|
| Static Vite dev server root (`/`) | PASS — محدود | `curl`: HTTP 200 و HTML title درست؛ هیچ API/auth/browser workflow در این smoke check نبود. |
| ورود Parent، token fields snake_case، GET `/v1/me` | BLOCKED | حساب staging واقعی؛ browser network trace بدون token logging |
| refresh token rotation پس از 401 و logout revoke | BLOCKED | session واقعی و بررسی revoke سمت DB/backend |
| account بی‌family → POST family و refresh list | BLOCKED | کاربر جدید، پاسخ family و GET devices |
| pairing یک‌بارمصرف و اتصال Child test device | BLOCKED | Android device/test build و pair code با expiry |
| family/device scoping بین دو خانواده؛ IDOR 404/403 | BLOCKED | دو family مجزا + API/security test؛ UI check جایگزین نیست |
| usage report و policy mutation | BLOCKED | Child usage sync قبلی، Android mode/permission و response/event verify |
| location current/history/settings | BLOCKED | location consent/permission داده‌شده، stale/denied/disabled cases |
| geofence enter/exit timing | BLOCKED | fixهای server-approved و انتظار sync/debounce واقعی؛ هیچ real-time claim |
| app rules و Web Safety enforcement state | BLOCKED | Android capability modes: supported, unsupported, failure; observed events |
| notification read/ack/resolve/preferences | BLOCKED | backend state before/after و family user-state |
| SOS lifecycle/location/expiry | BLOCKED | Child UI trigger, Parent notification, acknowledge/resolve/expiry; emergency services هرگز claim نشود |
| offline shell/API no-cache | BLOCKED | production SW host + DevTools network/storage check |
| Caddy same-origin/CSP/SPA refresh | BLOCKED | Docker/Caddy deployment smoke test |
| keyboard/RTL/LTR/mobile/tablet/desktop viewport | BLOCKED | browser automation/manual visual + screen reader review |

## معیار تکمیل E2E

هر mutation باید قبل/بعد در shared API/DB یا state معتبر دیده شود؛ برای authorization حداقل دو خانواده؛ برای permissions چند حالت granted/denied/stale؛ برای enforcement همهٔ reported modes. تا آن زمان E2E و «shared DB verified» در final gate برابر PASS نمی‌شود.
