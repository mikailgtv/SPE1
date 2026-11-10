# Phase 21 — گزارش رفع موانع

**تاریخ:** 2026-10-05 (UTC) · **تغییر source/config در فاز:** هیچ‌کدام.  
این تصمیم عمدی است: مواردی که تغییرشان DB/security contract یا workflow production را متاثر می‌کرد، بدون staging، PostgreSQL، JDK17/Gradle، CI و owner approval به شکل امن قابل اعتبارسنجی نبودند.

## کارهای محلی انجام‌شده

| ID | اقدام/فرمان | نتیجه | محدودهٔ ادعا |
|---|---|---|---|
| BR-01 | `npm ci --ignore-scripts` در `web/` | PASS؛ نصب شد، npm گزارش 0 vulnerability در آن resolve | dependency tree وب در همان لحظه؛ نه کل مخزن |
| BR-02 | `npm test` | PASS؛ 2 فایل، 4 تست | فقط `web/src/api.test.ts`, `web/src/i18n.test.ts`؛ نه browser/API واقعی |
| BR-03 | `npm run build` | PASS؛ TypeScript build و Vite production compile | فقط Web bundle؛ نه release deployment |
| BR-04 | `npm audit --audit-level=high` | PASS؛ 0 vulnerability گزارش شد | npm dependency graph وب؛ نه Android/Maven/Docker/GitHub actions |
| BR-05 | YAML/XML/TOML/shell syntax | PASS: 9 YAML، 35 Android XML، 1 TOML؛ Bash scripts parse شدند | syntax-only؛ schema/semantics/workflow execution تأیید نمی‌شود |
| BR-06 | secret-pattern heuristic scan محدود | 0 candidate در مسیرهای متنی منتخب | اسکن جامع نیست؛ Git history، bundle/binary، تمام repo و entropy scan پوشش ندارد |
| BR-07 | inventory و source review | blockerها مستند شدند؛ تغییر پرریسک انجام نشد | review به‌تنهایی remediation یا runtime proof نیست |

زمان مشاهدهٔ تست وب در Vitest: 13:06:13؛ ساعت سیستم ممیزی 2026-10-05 13:07 UTC. خروجی کامل در log اجرای محلی همین فاز است؛ artifact مستقل/CI report صادر نشده. `web/dist/` فقط محصول build محلی و snapshot-ignored است.

## موانعی که عمداً تغییر داده نشدند

| Finding | چرا local fix امن/قابل اثبات نبود | گیت بعدی |
|---|---|---|
| RLS/tenant isolation | نیازمند design/context propagation و migration و runtime role؛ تغییر schema بدون DB و cross-tenant tests پرخطر | review migrations/routes؛ implement/test در disposable PostgreSQL و staging |
| جداسازی migration/runtime role | `Db.migrate()` در startup و credential مشترک از source قبلی شناسایی شده؛ role split نیازمند deploy topology/env | migration job مجزا، privilege tests و boot با role حداقلی |
| JWK custody/rotation | کلید signing و persistence/backup نیاز به secret manager/KMS و recovery test دارد؛ دسترسی موجود نیست | owner تصمیم key architecture؛ rotation, restart/failover/restore drill |
| audit/retention/export/delete/field encryption | شامل داده‌های حساس و cascade/retention؛ migration و privacy approvals لازم | طراحی و threat review؛ synthetic staging, reversible migration, privacy-owner approval |
| replay/session security | نیازمند API contract/client changes و integration tests؛ patch حدسی ممکن است SOS/location رفتار را بشکند | endpoint-specific design، concurrency/replay tests |
| body limits/timeouts/distributed rate limit | پیکربندی Ktor/proxy/clients و حجم واقعی payload نامعلوم؛ limiter اشتراکی نیاز زیرساخت | threat model/payload sizes، multi-replica load test |
| pairing race | row locks/conditional mutations باید روی PG هم‌زمانی واقعی آزموده شوند | repeated concurrent PG integration test |
| workflow action refs | action refs tagهای متغیر؛ ارتقای major/pinning نیاز compatibility/runner review؛ actionlint و CI غایب | pin به SHA معتبر به‌همراه version comment؛ syntax/CI real run و Dependabot policy |
| Android release signing | key و SDK/Gradle/apksigner نداریم؛ key یا artifact نباید جعل شود | controlled release environment، build signed و verify certificate |

## نتیجه

هیچ blocker محصولی به‌عنوان «حل‌شده» علامت نخورده است. گیت‌های وب محلی موفق هستند، اما گیت‌های امنیتی، DB، build Android/backend، integration، operational release همچنان باز/مسدودند. وضعیت نهایی: `NOT_READY`.
