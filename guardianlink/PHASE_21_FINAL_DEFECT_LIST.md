# Phase 21 — فهرست نهایی نقص‌ها و گیت‌های باز

ترتیب بر اساس ریسک انتشار؛ severityها طبقه‌بندی این ممیزی‌اند، نه نتیجهٔ آزمون نفوذ. `OPEN` یعنی رفع/قبول ریسک با evidence ثبت نشده است.

| ID | اولویت | نقص/ریسک | شاهد | وضعیت | شرط بستن |
|---|---|---|---|---|---|
| D-01 | P0 | tenant isolation وابسته به app-level scoping؛ RLS/FORCE RLS و cross-tenant PG tests اثبات نشده | Phase13 H-01, current migrations/DB unavailable | OPEN | RLS/role design + Parent A/Family B و Child A/Device B tests |
| D-02 | P0 | migration/runtime DB privilege separation اثبات نشده؛ `Db.migrate()` در startup source | Phase13 H-02, `Db.kt` | OPEN | مجزاسازی deploy migration job و DML-only API role |
| D-03 | P0 | secure key custody/rotation/recovery برای signing JWK اثبات نشده | Phase13 H-03, `Tokens.kt` | OPEN | key management architecture + rotate/restart/restore test |
| D-04 | P0 | no complete append-only security audit trail | Phase13 H-04 | OPEN | reviewed schema/permissions + sensitive action coverage |
| D-05 | P0 | family/account export و حذف کامل موجود نیست/ناقص؛ retention سراسری تأیید نشده | Phase20 source/reports | OPEN | privacy approved feature + data/backup lifecycle test |
| D-06 | P0 | field encryption of sensitive server records not established | Phase13 H-06, Phase20 encryption | OPEN | threat/key design, migration, rotation/recovery tests |
| D-07 | P0 | authenticated replay/session inventory/reuse controls incomplete or unproven | Phase13 H-07/H-08 | OPEN | write idempotency/replay and refresh lineage tests |
| D-08 | P0 | pairing race, transactions only code-reviewed, not tested under concurrent PG | Phase13 H-11 | OPEN | repeatable concurrent exchange/confirm test |
| D-09 | P0 | signed Android release and certificate verification absent | no Gradle/SDK/key/apksigner | BLOCKED | controlled reproducible signed build + cert/digest validation |
| D-10 | P0 | no backend/Android build, staged API/DB integration or real E2E | environment tool inventory | BLOCKED | establish isolated test environment and actual runs |
| D-11 | P1 | process-local limiter cannot guarantee quotas across replicas | `AppDeps.build` constructs `RateLimiter()`; Caddy comments | OPEN | shared limiter or validated deployment single instance + abuse testing |
| D-12 | P1 | Ktor request body cap/deadlines and hostile request controls not proven | Phase13 H-09 | OPEN | bounded body/deadline tests on API and edge |
| D-13 | P1 | backup/restore/DR not performed; RPO/RTO undefined | Phase20 reports; tools/cloud/storage absent | BLOCKED | isolated restore drill, integrity/API tests, measured RPO/RTO |
| D-14 | P1 | action refs are mutable tags; no actionlint/CI run | `.github/workflows/*` `uses:` inventory | OPEN | full SHA pins, workflow validation and CI evidence |
| D-15 | P1 | API/Web/Android contract drift and real-client backend path not verified | Phase16 reports; P21 E2E block | NOT TESTED | generated/contract diff or integration matrix on live test backend |
| D-16 | P1 | Android release manifest and network configuration not merged/runtime validated | 35 XML parse only | NOT TESTED | inspect built release merged manifest + capture network behavior |
| D-17 | P1 | Child `QUERY_ALL_PACKAGES` policy acceptance unknown | Child manifest source | REVIEW REQUIRED | distribution channel-specific policy review |
| D-18 | P1 | npm web audit doesn't cover Maven/Gradle, actions, container images or Git history | P21 web audit scope | PARTIAL | ecosystem scans and complete repo/history source |
| D-19 | P2 | localization/accessibility/browser performance lack actual browser verification | web build/unit only; Phase17 | NOT TESTED | manual/browser automation and assistive technology checks |
| D-20 | P2 | observability/alerts/privacy-safe redaction verified only from source | Phase18; no runtime stack | NOT TESTED | synthetic event capture and alert/recovery test, no raw personal data |
| D-21 | P2 | legal/distribution obligations undetermined | jurisdiction/market unspecified | JURISDICTION-SPECIFIC REQUIREMENT | qualified market-specific assessment; no compliance claim |
| D-22 | P2 | report naming/content mismatch for `PHASE_12_FINAL_RELEASE_REPORT.md` | file content begins Phase15 | OPEN | rename/correct after owner confirmation, preserve original provenance |

## موارد pass محدود برای تفکیک

Web unit/build/npm audit و source syntax checks طبق reports اختصاصی واقعاً اجرا شدند. این موارد هیچ نقص P0 را خودکار نمی‌بندند. هیچ test failure واقعی مرتبط با backend/device ثبت نشد؛ این با PASS اشتباه نشود.
