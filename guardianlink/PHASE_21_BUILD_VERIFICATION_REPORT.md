# Phase 21 — گزارش راستی‌آزمایی Build

**نتیجه کلی:** Web build محلی `PASS`; Backend و Android `BLOCKED` به‌علت نبود Gradle/wrapper/Android SDK و toolchain لازم. هیچ signed APK/AAB/server package یا deployment artifact ساخته نشد.

## محیط آزمون

- زمان: 2026-10-05 13:07 UTC؛ Java 11، Node v20.20.2، npm 10.8.2.
- نبود ابزار: `gradle`, `./gradlew`, Android SDK/`adb`, `apksigner`; Docker نیز موجود نیست.
- Backend/Android reports قبلی نیاز JDK17 را برای ساخت/تست دارند؛ Java موجود 11 است.

## شواهد اجرا

| Test ID | Environment | Command | Timestamp (UTC) | Expected | Actual | Result | Artifact |
|---|---|---|---|---|---|---|---|
| BLD-WEB-001 | `/home/user/guardianlink/web`, Node v20.20.2/npm10.8.2 | `npm ci --ignore-scripts` | 2026-10-05 ~13:06 | deps از lockfile نصب شود | 161 packages added، npm گزارش 0 vulnerability | `PASS` | console؛ `web/dist` بعداً تولید شد |
| BLD-WEB-002 | همان | `npm test` | 2026-10-05 13:06:13 آغاز | Vitest suites موفق شوند | 2 فایل و 4 تست passed | `PASS` | console output |
| BLD-WEB-003 | همان | `npm run build` (`tsc -b && vite build`) | 2026-10-05 ~13:06 | TypeScript و production Vite build | موفق، 90 modules؛ JS 385.93 kB/118.12 kB gzip؛ CSS 35.46/7.61 kB؛ HTML 0.71/0.46 kB | `PASS` | `web/dist/` (snapshot-excluded) |
| BLD-WEB-004 | همان | `npm audit --audit-level=high` | 2026-10-05 ~13:06 | بدون high/critical در npm graph | 0 vulnerabilities | `PASS` (فقط npm graph) | console output |
| BLD-SYNTAX-001 | repository | Python PyYAML BaseLoader + ElementTree + tomllib؛ extracted workflow run scripts `bash -n`; backup/restore `bash -n` | 2026-10-05 13:06–13:07 | syntax parse شود | 9 YAML, 35 Android XML, 1 TOML و shell checks موفق | `PASS` (syntax-only) | console output |
| BLD-SERVER-001 | server | `./gradlew ...` | `NOT RUN` | compile/test backend | wrapper/Gradle/JDK17 environment unavailable | `BLOCKED` | none |
| BLD-ANDROID-001 | Android modules | `./gradlew assemble...` | `NOT RUN` | build Parent/Child variants | no Gradle, SDK, apksigner/device; JDK 11 | `BLOCKED` | none |
| BLD-SIGN-001 | release env | signing + apksigner verification | `NOT RUN` | signed release with reviewed cert | no key/tool/build artifact | `BLOCKED` | none |

## محدودیت تفسیر

- Web PASS صرفاً compile/build است؛ browser rendering, accessibility, service API, CORS, cookies/storage, CSP deployment و parent workflows end-to-end آزمایش نشده‌اند.
- Android Manifest XML parse به معنای manifest merge یا Android policy acceptance نیست.
- YAML syntax parse به معنای GitHub Actions schema/permissions/runner compatibility یا workflow execution نیست؛ actionlint نصب نیست.
- npm audit scope به Android Gradle/Maven, Docker base images, actions, full repo/Git history تعمیم نمی‌یابد.
- هیچ artifact امضاشده، APK/AAB، container image، server package یا release candidate موجود نیست.
