# Phase 15 — گزارش آزمون frontend

**تاریخ:** ۲۰۲۶-۱۰-۰۲. فقط testهای اجراشده با `PASS` علامت‌گذاری شده‌اند.

| بررسی | نتیجه | شواهد/توضیح |
|---|---|---|
| `npm test` | PASS | Vitest 4.1.11: 2 test files، 4 assertions/tests passed (API URL relative، refresh snake_case→camelCase، retry bearer، عدم refresh روی 403، کلیدهای پایهٔ locale). |
| `npm run build` | PASS | `tsc -b` و Vite build؛ Vite 7.3.6؛ output حدود 34.8 KB CSS و 378.4 KB JS قبل از gzip (build نهایی). |
| `npm audit --audit-level=moderate` | PASS | ۰ vulnerability در lockfile طبق registry audit اجراشده در ۲۰۲۶-۱۰-۰۲. |
| Vite dev-server HTTP smoke test | PASS | `curl` به `/`, `/preview`, `/login` پاسخ `HTTP 200` داد؛ این فقط SPA shell/route fallback است، نه browser E2E/API integration. |
| Code source review | REVIEWED، نه test | route names, JSON snake_case, parent-owned paths و unavailable features با Kotlin source بررسی شدند. |
| Caddy/Compose validation | BLOCKED | Docker/Caddy binary در environment نیست؛ config syntax/deploy اجرا نشده. |
| Backend compile/start + DB migrations | BLOCKED | Gradle/wrapper runtime, Docker, PostgreSQL unavailable؛ هیچ service/staging credentials نداریم. |
| Auth/API integration واقعی | BLOCKED | login/refresh/family/device routes به backend فعال متصل و آزموده نشدند. |
| E2E/browser screen reader/axe | PENDING | browser automation/credentialed backend و accessibility audit اجرا نشده‌اند. |
| Android physical-device/permission/effectiveness | NOT IN SCOPE / NOT RUN | هیچ تست دستگاهی انجام نشده؛ UI وضعیت اعلام‌شدهٔ backend را فقط نشان می‌دهد. |

Build/test نتیجهٔ frontend است؛ این نتیجه API/DB/Android/production approval نیست.
