# Phase 21 — گزارش API و Database

**وضعیت:** API/database integration `BLOCKED / NOT TESTED`; فقط source/config بررسی شده است. PostgreSQL، psql/pg tools، Docker، backend build و staging در دسترس نیستند.

## مشاهدات source

- Backend مبتنی بر Ktor؛ repository/route/service source زیر `server/src/main/kotlin/ir/guardianlink/server/`; `/v1` پشت Caddy reverse proxy طبق config.
- `Auth.kt`: bearer parsing، token type/sid/session expiry/revocation؛ Parent family principal از repo واکشی می‌شود و Child device status فعال بررسی می‌شود. خطاهای parse/authorization در live backend آزموده نشده‌اند.
- `Tokens.kt`: EdDSA JWT؛ expiry/session identifiers از source مستند هستند؛ key custody, rotation, incident recovery operationally نامشخص.
- `Db.kt`: Hikari pool؛ JDBC `connectTimeout=5`, `socketTimeout=30`; Flyway `migrate()` در همان `Db` abstraction؛ deployment جداگانهٔ migration/runtime role در source/configهای بررسی‌شده اثبات نشده.
- Caddy `/metrics` public path را 404 می‌کند؛ deployment routing/network isolation اجرا نشده.
- Phase20 inventory گزارش 42 جدول در migrations V1–V9؛ این شمارش source-derived است و با live DB مطابقت داده نشده.
- Phase13 finding H-01/H-02 همچنان گیت باز: RLS/tenant constraints و DB roles روی PostgreSQL واقعی ثابت نشده‌اند.

## ماتریس endpoint/integration

| ID | دامنه | وضعیت Phase21 | اثبات لازم |
|---|---|---|---|
| API-01 | account/auth/token/session | `IMPLEMENTED_NOT_TESTED` | backend build + valid/expired/revoked token/session integration |
| API-02 | pairing/child device | `IMPLEMENTED_NOT_TESTED` | concurrent PostgreSQL tests, expiry/revoke/replay |
| API-03 | location/usage/status/notifications | `IMPLEMENTED_NOT_TESTED` | authorized write/read and idempotency, permission and data-minimization review |
| API-04 | family/tenant isolation | `PARTIALLY_IMPLEMENTED` | cross-family DB isolation/RLS/route tests absent |
| API-05 | public health/metrics | `PARTIALLY_IMPLEMENTED` | source config exists; deployed reachability/private metrics verification absent |
| DB-01 | Hikari/JDBC access | `IMPLEMENTED_NOT_TESTED` | pool saturation/timeout, runtime least-privilege tests |
| DB-02 | Flyway migrations | `IMPLEMENTED_NOT_TESTED` | clean install and N-1 upgrade on real PostgreSQL versions |
| DB-03 | RLS and roles | `NOT_IMPLEMENTED` (as verified by Phase13 source findings) | migration and pg role matrix |
| DB-04 | export/delete schema API | `NOT_IMPLEMENTED` | approved requirements and full lifecycle tests |
| DB-05 | application field encryption | `NOT_IMPLEMENTED` | threat/key design and ciphertext/key lifecycle test |

## Test register

| Test ID | Environment/command/time | Expected | Actual/result | Artifact |
|---|---|---|---|---|
| API-SRC-001 | source review `Auth.kt`, `Db.kt`, token/route references; 2026-10-05 | record controls without treating source as execution | completed; static only | this report |
| API-PG-001 | disposable PG + backend, `NOT RUN` | schema, authz, pairing, isolation behavior | PG/Docker/backend build unavailable; `BLOCKED` | none |
| API-E2E-001 | staging/browser/mobile, `NOT RUN` | real client ↔ backend ↔ DB flow | no endpoints/test account/device; `BLOCKED` | none |

No production data/database was accessed or altered. No backup/restore or destructive migration was attempted. API response correctness and contract drift between Android/Web/backend remain `NOT TESTED`.
