# Phase 21 — گزارش E2E

**حکم:** `BLOCKED`; هیچ end-to-end واقعی در Phase 21 اجرا نشد. Mock/UI test یا source annotation به‌عنوان integration موفق شمرده نشده است.

## محیط و prerequisites

Backend/DB و test URL در دسترس نیست؛ Docker/PostgreSQL/psql و Gradle/wrapper و Android SDK/adb غایب‌اند؛ test accounts/devices/cloud credentials نداریم. هیچ browser automation runner یا CI run evidence ثبت نشد. از production DB استفاده نشده.

## Scenario matrix

| Test ID | جریان | Expected | Actual / status | Artifact |
|---|---|---|---|---|
| E2E-AUTH-01 | Parent signup/login → JWT/session → dashboard API | authorized data برای family خود | `NOT RUN`؛ no backend/PG/staging; `BLOCKED` | none |
| E2E-PAIR-02 | Child pairing start/exchange/confirm با race/replay | یکبار مصرف، scope درست | `NOT RUN`; no PG/device; `BLOCKED` | none |
| E2E-LOCATION-03 | permission → collection/sync → parent view | consent/permission، freshness و access constraints | `NOT RUN`; no device/backend; `BLOCKED` | none |
| E2E-USAGE-04 | OS usage permission → sync → parent view | OS-limited completeness disclosure | `NOT RUN`; no device/Play policy test; `BLOCKED` | none |
| E2E-SOS-05 | SOS create/update/acknowledge/notification | authorized and idempotent delivery state | `NOT RUN`; no backend/device/push provider; `BLOCKED` | none |
| E2E-OFFLINE-06 | disconnect, queued sync, process death/restart | no silent loss/duplicate; truthful status | `NOT RUN`; no devices/network harness; `BLOCKED` | none |
| E2E-PRIVACY-07 | export/delete/retention/backup restore | complete family data lifecycle | export/account-family delete incomplete; no DB/backup; `NOT IMPLEMENTED` + `BLOCKED` for runtime proof | none |
| E2E-WEB-08 | browser dashboard → real `/v1` | deployed CSP/auth/API works | `NOT RUN`; npm build only; `BLOCKED` | web build evidence is not E2E |
| E2E-RECOVERY-09 | fault/restore/recovery | restore integrity and RTO/RPO evidence | `NOT RUN`; no backup/staging; `BLOCKED` | none |

## Nearby but non-E2E test evidence

P21-WEB-UNIT-001 passed four Vitest tests; P21-WEB-BUILD-001 compiled a web bundle. These validate a narrow unit/build scope only. They do not exercise Ktor, Postgres, actual Android, network, notification delivery, cloud, or browser workflows.

## Exit criteria

Run disposable PostgreSQL plus separately controlled staging; use synthetic family/device records; execute all rows with timestamp, command/build id, expected/actual/result, redacted logs, and artifacts. At least cross-family negative tests, concurrency/replay, process restart/offline, and backup→restore→API verification are blocking. Never use destructive tests on production data.
