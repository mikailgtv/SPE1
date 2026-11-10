# شواهد خلاصهٔ اجرای Phase 23 (پاک‌سازی‌شده)

همهٔ زمان‌ها UTC. فایل هیچ secret/token/password یا مختصات حقیقی ندارد.

## E2E API
- ID: `P23-E2E-API-01`
- Command: `python3 /tmp/gl_phase23_api_flow.py`
- Start: `2026-10-06T07:31:17.795213Z`
- End: `2026-10-06T07:31:21.677169Z`
- Result: 54 response checks در expected-status set؛ 53 status assertions به PASS شمرده شد؛ 1 MiB body test با 401 به NOT VERIFIED تنزل داده شد (limit/413 ثابت نشد).
- Functional assertions completed: usage total; current synthetic fix; server-derived GEOFENCE_ENTER; notification exists/ack; SOS status/ack/resolve; cross-family and cross-device isolation; refresh rotation/logout; login rate limit.
- Full sanitized stage-by-stage table: `../PHASE_23_E2E_REPORT.md`.

## Fresh DB / health
- Start backend: `2026-10-06T07:50:05.591524983Z`; application listening by `07:50:07.384261312Z`.
- DB: `guardianlink_phase23_fresh`, PostgreSQL 17.11, 9 migrations applied to v9.
- `GET /health/live` -> HTTP 200 `live`; `GET /health/ready` -> HTTP 200 `ready`, around `2026-10-06T07:50:14Z`.

## Backup restore
- Test DB: `guardianlink_phase23` to a separate new `guardianlink_phase23_restore`; `pg_dump -Fc`, `pg_restore --exit-on-error`.
- Expected/actual aggregate: source=43 public tables, Flyway v9, 186 rows; restored=43, v9, 186 rows.
- SHA-256: `c8d08f8043a7addff989a6c04a5b7486d3b6b942dd2a440a516f06787d1ce600`.
- Dump path was under `/tmp/phase23-backups/`, not a permanent project artifact.

## Build outputs
- `P23-BE-BUILD-02`; `gradle --offline --no-daemon --max-workers=1 -Dorg.gradle.jvmargs=<bounded-256MiB> -Pkotlin.compiler.execution.strategy=in-process installDist`; JDK17 / Gradle9.7.1; start `2026-10-06T08:11:54Z`; end `08:12:04Z`; exit 0, `BUILD SUCCESSFUL` (up-to-date).
- `P23-WEB-BUILD-02`; `npm run build`; Node20.20.2/npm10.8.2; start `2026-10-06T08:11:42Z`; end `08:11:50Z`; exit 0; TypeScript + Vite; 90 modules.
- Logs: `backend-build.log`, `backend-build-meta.txt`, `web-build.log`, `web-build-meta.txt`.

## Backend test suite
- `P23-TEST-01`; command `gradle --no-daemon --max-workers=1 ... test`; `2026-10-06T07:51:58Z`–`07:52:10Z`; 141 completed, 122 failed; 19 passed.
- Cause: test container startup could not find a Docker provider; `docker` and `podman` absent. These failures are environment blocks, not treated as product PASS.

## Android
- AGP 9.4.1 produced explicit minimum Gradle 9.6.0 error under Gradle 8.14.3.
- Gradle 9.7.1 `assembleDebug` online attempt: daemon disappeared after ~10m; offline retry timed out at 180s with no final output. `ANDROID_HOME`/`ANDROID_SDK_ROOT`, `adb`, `sdkmanager`, `emulator` unavailable. No APK claims.
