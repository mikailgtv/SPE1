# Phase 21 — گزارش گیت امنیت

**حکم:** `FAIL` برای security sign-off/RC؛ این به‌معنای exploit مشاهده‌شده در اجرای زنده نیست، بلکه گیت‌های high مانده/اثبات‌نشده‌اند.  
**زمان:** 2026-10-05 UTC · فقط source/config review؛ هیچ penetration test، scanner کامل یا runtime security test اجرا نشد.

## شواهد مثبت محدود

- `Tokens.kt` از EdDSA/Ed25519 و بررسی الگوریتم امضا استفاده می‌کند؛ `Auth.kt` بررسی typ/session expiry/revocation و active device را در source نشان می‌دهد. key custody/rotation/restart/recovery اثبات نشده.
- Caddy source سرور را به HTTPS domain متصل می‌کند و headerهای امنیتی/CSP را تنظیم می‌کند؛ هیچ deployment/TLS scan واقعی انجام نشده.
- Caddy برای `/metrics` پاسخ 404 دارد؛ telemetry intended خصوصی است. اتصال network و scrape isolation runtime اثبات نشده.
- `logback.xml`/Observability source ادعای redaction و aggregate metrics دارند؛ دادهٔ واقعی log sink و capture تست نشده.
- release network security Android base، در بررسی قبلی TLS-only؛ debug overlay مجاز به cleartext است. به‌علت build نشدن، merge نهایی resourceها و رفتار release نصب‌شده بررسی نشد. هیچ certificate pinning ادعا نمی‌شود.
- heuristic scan محدود به مسیرهای `.github`, deploy, server source, Android, web source مقدار candidate برنگرداند؛ secret scan جامع نیست.

## Findings باز

| ID | شدت | یافته/مبنای فعلی | وضعیت | شرط عبور |
|---|---|---|---|---|
| SEC-01 | High | tenant enforcement عمدتاً application queries؛ RLS/FORCE RLS و runtime cross-tenant tests اثبات نشده (Phase13 H-01) | OPEN | role-isolation، Parent A↛Family B و Child A↛Device B روی PG واقعی |
| SEC-02 | High | migration در app startup و credential split از نظر runtime تأیید نشده (H-02) | OPEN | DML-only API role؛ DDL مجزای deploy migration job |
| SEC-03 | High | Ed25519 signing source حاضر؛ secure key custody/rotation/recovery اثبات نشده (H-03) | OPEN | KMS/secret manager یا protected mount با documented lifecycle و tests |
| SEC-04 | High | unified append-only security audit trail در source گزارش نشده (H-04) | OPEN | کمینه‌سازی فیلدها، write-only/append permissions، audit test |
| SEC-05 | High | family/account export/delete و retention lifecycle ناقص/غایب (H-05) | OPEN | approved privacy design و synthetic lifecycle/backup tests |
| SEC-06 | High | field encryption/location/SOS/usage در DB به‌صورت application-level اثبات نشده (H-06) | OPEN | key/version/rotation/restore design و ciphertext verification |
| SEC-07 | High | full request replay/idempotency controls در تمام sensitive write endpoints اثبات نشده (H-07) | OPEN | replay/concurrency matrices |
| SEC-08 | High | session inventory/reuse detection/revoke-all API پوشش کامل ندارد (H-08) | OPEN | refresh lineage/rotation/revocation tests |
| SEC-09 | High | Ktor request caps/deadlines و hostile body/slow request test unresolved (H-09) | OPEN | explicit limits، slow/oversized/malformed tests، TLS/cors review |
| SEC-10 | High | `RateLimiter()` process-local؛ scaling quota/abuse resistance در replicas تضمین نشده (H-10) | OPEN | shared atomic backend یا enforced single instance + real abuse test |
| SEC-11 | High | pairing locking source دیده شده اما race test واقعی وجود ندارد (H-11) | OPEN | concurrent exchange/confirm on PostgreSQL repeated |
| SEC-12 | Medium | Android Child `QUERY_ALL_PACKAGES` و usage/location capabilities؛ distribution-policy acceptance نامعلوم | REVIEW REQUIRED | policy/legal distribution review؛ no policy-compliance assertion |
| SEC-13 | Medium | Git history absent; complete secret history/APK scan not run | BLOCKED | canonical Git clone and secret-safe scanners; values must not print |
| SEC-14 | Medium | GitHub Actions mutable tag refs؛ no actionlint/workflow run | OPEN | immutable full SHA refs, valid version comments, actionlint + real CI |
| SEC-15 | Medium | X-Forwarded-For trust is constrained at Caddy source; proxy topology not deployed/verified | NOT TESTED | edge origin spoof tests and proxy trust config review |

## Test evidence

| Test ID | Environment/command/time | Expected | Actual/result | Artifact |
|---|---|---|---|---|
| SEC-SCAN-01 | workspace, Python heuristic over selected text roots; 2026-10-05 13:06–13:07 UTC | detect known high-confidence private-key/JWT/access-key/long secret patterns without printing values | 0 candidate; `PASS` for stated narrow heuristic only | command log; no persistent output |
| SEC-CONFIG-01 | source review; no runtime | identify source controls and open gates | source evidence summarized above; no executable test result | this report |
| SEC-RUNTIME-01 | backend/PG/staging `NOT RUN` | authz, replay, role separation, rate, logging behavior | no tools/service/fixtures; `BLOCKED` | none |

`PASS` only applies to the narrow executed pattern scan and web/syntax checks listed in other reports; there is no overall security PASS. Legal review is `JURISDICTION-SPECIFIC REQUIREMENT` because jurisdiction/market is unspecified.
