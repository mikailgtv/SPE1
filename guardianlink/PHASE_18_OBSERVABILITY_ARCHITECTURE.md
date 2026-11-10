# Phase 18 — ممیزی و معماری Observability (پیش از تغییر کد)

**زمان مبنا:** 2026-10-03. این ممیزی از source جاری، migrations، Android manifests، Web API client، Gradle/Compose، Docker/CI و گزارش‌های Phase 16/17 تهیه شد. پیش از ثبت این audit هیچ کد Phase 18 تغییری نکرده است. هیچ Backend/DB/container/device/browser واقعی در این مرحله بالا نیامد.

## 1) خلاصهٔ محیط و مرز پذیرش

- در محیط: Java 11، Node/npm. `gradle`, Gradle wrapper، Android SDK/adb، Docker/Podman، `psql` و browser executable پیدا نشدند. Server Gradle درخواست JDK 17 (`jvmToolchain(17)`) دارد؛ JDK فعلی 11 نیز کافی نیست.
- Repo دارای `server` Kotlin/Ktor، PostgreSQL migrations، Android modules Parent/Child، React/Vite Web، Compose deployment config و GitHub Actions است.
- Phase 17 قبلی Web npm tests/build را انجام داده، ولی Backend tests/build و Android build/device E2E همچنان مسدود بوده‌اند. این شواهد برای Phase 18 فقط زمینه‌اند، نه تست Phase 18.
- هیچ cloud monitoring stack، SaaS telemetry SDK، Prometheus/Grafana provisioning، push provider یا runtime credentials شناسایی نشد.

## 2) نمای معماری مشاهده‌شده و مسیر telemetry پیشنهادی

```text
 Parent Android ───────┐                  ┌────── Child Android
 • API calls            │ HTTPS /v1        │ • WorkManager sync
 • local notifications  ├──────────────────┤ • SOS visible FGS
 • no crash SDK         │                  │ • encrypted local queues
                        ▼                  ▼
                  ┌──────────────────────────┐
                  │ Ktor Backend             │
                  │ Auth / Routes / Services  │
                  │ current text logs, no    │
                  │ request correlation/API  │
                  │ metrics exporter         │
                  └────────────┬─────────────┘
                               │ JDBC/Hikari
                               ▼
                  ┌──────────────────────────┐
                  │ PostgreSQL 17             │
                  │ transactional domain data │
                  │ Flyway V1–V9              │
                  └──────────────────────────┘
       Parent Web ── same-origin /v1 ──┘
       React Query + volatile session; no global JS error collector

       Proposed pipeline (to be enabled only with operator infrastructure):
       allowlisted structured logs + bounded-cardinality metrics + classified errors
                  └──► operator-owned collector / retention / alerts / dashboard
```

**اصل حریم خصوصی:** در سامانهٔ داخلی هیچ telemetry مستقلِ کاربرمحور به اپ Parent/Child اضافه نشود. Backend logging/metrics به‌صورت عملیاتی و کمینه؛ کلیدهای برچسب کم‌کاردینالیتی و بدون دادهٔ شخصی. مختصات، SOS payload، pairing code، password، token، raw request/URI/query، email و package/app inventory وارد log/metric نشوند. Crash SDK به‌علت نیاز به ارزیابی SDK، رضایت/اطلاع‌رسانی و data residency بدون تصویب جدا اضافه نمی‌شود.

## 3) ممیزی سطح‌ها

### Backend API و احراز هویت

- Health: `GET /health/live` process-only؛ `GET /health/ready` DB `SELECT 1` و `DATA_DIR` readable/writable، پاسخ 503 در عدم آمادگی؛ `GET /health` readiness alias. خروجی شامل وضعیت عمومی DB/storage و `push_notifications=not_configured` است، بدون credential/path.
- Auth/family/device: `POST /v1/auth/register`, `/login`, `/refresh`, `/logout`; `GET /v1/me`; `POST /v1/families`, `GET /v1/families/me`; خانواده/device list/detail/remove: `GET /v1/families/me/devices`, `GET|DELETE /{id}`.
- Pairing/device: `POST /v1/pairing/sessions`, `GET /sessions/active`, `POST /exchange`, `/confirm`; `POST /v1/device/auth`, `/refresh`, `/heartbeat`, `/status`, `/sync`.
- Device monitoring: `GET /v1/families/me/devices/{id}/health`, `/permissions`, `/events`.
- Usage: `POST /v1/device/usage/upload`; parent reads `GET .../usage/today`, `/weekly`, `/apps`, `/apps/{pkg}/detail`, `/apps/{pkg}/icon`, `/usage/report/daily`, `/screen-time/report`, `/screen-time/settings`, `/screen-time/schedules`; writes settings/schedule via POST/PUT.
- Location: child `POST /v1/device/location/sync`; parent current/history/settings reads, settings POST, history DELETE. Location data حساس است و باید از telemetry/logging خارج بماند.
- Geofence: child reads/sync/events; parent CRUD zones, per-zone and family event reads. Geofence transitions server-side هنگام location processing، نه worker مستقل.
- App management/Web Safety: child sync endpoints؛ parent catalog/rules/events و CRUD, overrides؛ implementation current reports real device-reported capability state.
- Notifications: family list, unread count, detail, read/read-all, acknowledge/resolve, preferences GET/PUT. این‌ها persistent notification feed هستند، نه push delivery.
- SOS: child activate/cancel/tracking/network/location; parent active/history/detail/location/events/notes/acknowledge/resolve/preferences. payload مکانی/یادداشت حساس است.
- WebSocket/SSE: route یا EventSource/WebSocket استفاده‌ای پیدا نشد؛ Parent Web با React Query polling عمل می‌کند.

### Database و Background work

- PostgreSQL 17 در Compose؛ Flyway migrationها V1–V9 در startup؛ HikariCP. default/Compose pool: max 10، min idle 2، connection timeout 5000ms، validation timeout 3000ms، idle timeout 600000ms، max lifetime 1800000ms؛ همه قابل override با env. این‌ها config هستند، نه ظرفیت benchmarkشده.
- Hikari metric/JMX export، active/idle/pending pool metrics، SQL duration/slow query listener یا query failure counter در source پیدا نشد. Query duration/slow query و تعداد هر query **از source قابل اندازه‌گیری نیست**؛ هیچ benchmark حقیقی/EXPLAIN production انجام نشده.
- Server کار Background scheduler ندارد؛ notification projection در همان DB transaction/event مسیر domain انجام می‌شود. Push خارج Backend و not_configured است. Daily notification summary یک WorkManager در Parent Android است.
- Backup shell scripts برای pg_dump/encryption/S3 وجود دارند؛ schedule، bucket، cloud IAM، backup age alerts و restore drill provision/اجرا نشده‌اند.

### Logging/Error/Correlation جاری

- Backend `GlLogger` categoryهایی برای AUTH/PAIRING/DEVICE/NETWORK/SECURITY/APP دارد، اما خروجی رشته‌ای `[CAT] ... key=value` است، نه JSON structured. `CallLogging` فقط method/status ثبت می‌کند؛ requestId، route template، duration، user correlation وجود ندارد. Global exceptions فقط exception class log می‌شوند؛ stack trace/cause intentionally به logs داده نمی‌شود.
- Redact helpers برای token hash prefix/email/code وجود دارند؛ سیاست Phase 18 سخت‌گیرانه‌تر است: خود secret/token/code حتی hash-derived fingerprint هم به هیچ log نرود.
- Backend login success/failure، pair events و برخی events log می‌شوند؛ ثبت eventها کامل نیست. Failureهای validation/DB/query/notification با دستهٔ یکنواخت، request ID و علت کنترل‌شده قابل query نیستند.
- Android `AppLog` فقط `BuildConfig.DEBUG` logcat می‌کند؛ بیشتر سرویس‌ها فقط sync/API-level محدود log دارند. Crash SDK، uncaught exception persistence، ANR tracking، startup event persistence شناسایی نشد.
- Web API client خطا را throw می‌کند و UI error state نشان می‌دهد؛ global `window.onerror`, `unhandledrejection`, React ErrorBoundary یا remote error collector شناسایی نشد. خاموشی session روی 401 موجود است. `fetch` credentials omit و tokenها فقط memory.

### Sync، notification، connectivity و domain events

- Child `StatusSyncWorker` یک درخواست sync چندجزئی با `NetworkType.CONNECTED`; periodic حداقل ۱۵ دقیقه (WorkManager، نه زمان دقیق)، run once هنگام app/network. خطا `Result.retry()`؛ revoke/removed با پاک‌کردن credential و توقف work. صف location و SOS رمز‌شده‌اند؛ app/web event cache encrypted. کارها dependency-aware و policy enforcement در دورهٔ system best-effort است. Worker attempt count/duration/success state metrics یا event history persistent ندارد؛ queue depth فقط 일부 payload/statusها، نه dashboard metrics.
- Device reported health و delayed/offline thresholds در FamilySettings config هستند (default: sync delayed 1800 sec، offline 3600 sec). `MonitoringService.refreshHealth` هنگام read/device routes health را محاسبه می‌کند و events lazy می‌سازد؛ schedule مستقل برای کشف offline نداریم.
- Notification projection در transaction و DB؛ Parent Android local polling/notifications. Server push ندارد؛ delivery receipt ندارد؛ generated/persisted rows ≠ device delivered/opened.
- SOS domain state/events موجود است؛ aggregation metrics برای activation/error/ack/resolution/location failures وجود ندارد. No content/coordinates should enter metrics.
- Geofence state transitions/validation/idempotency هستند؛ performance/duplicate/rejected counters وجود ندارد.

### Idempotency/rate limits/security

- Pairing code hash، state transition/timing checks و DB transaction/FOR UPDATE؛ device install unique active index. SOS open-per-device unique partial index, clientRequestId flows; sync/event dedupe keys در بعضی repos. Idempotency semantics endpoint به endpoint متفاوت است؛ observability نباید به معنی blind retry باشد.
- Rate limiter memory-local، size-bounded; کامنت خود source می‌گوید چند replica به shared store نیاز دارد. 429ها با Error code می‌آیند؛ aggregate count/alert نداریم. `clientIp()` trust proxy configuration نیاز بررسی در deployment.
- Security authorization از session+family/device ownership؛ security events در بعضی مسیرها log می‌شوند، اما audit log جدا، retention و review process وجود ندارد. Metric labels نباید identifier خام یا user/device id بگیرند.

### Web, Android, deployment

- Web `src/api.ts` برای هر request `fetch` same-origin، cache no-store و credentials omit، camel/snake normalize؛ React Query با `retry:0`, staleTime 20s؛ polling health ready 60s, unread 60s, SOS 30s. Browser offline service worker فقط shell؛ نه telemetry.
- Android Child manifest یک SOS foreground location service user-triggered/time-bounded؛ no persistent sync foreground service و no POST_NOTIFICATIONS برای Child. WorkManager network constraints/retries؛ OEM/Doze/standby/reboot بایستی روی device آزمایش شوند، bypass ممنوع.
- deployment Compose فقط db/server (Caddy edge profile optional). DB `pg_isready` health check؛ server Docker healthcheck وجود ندارد در Compose، readiness endpoint موجود. هیچ prometheus/grafana/otel/alert receiver service.

## 4) طراحی Phase 18 (low-risk, no new end-user feature)

1. Backend request correlation: تولید UUID تازه در سرور برای هر request و response header؛ caller-supplied ID پذیرفته/ثبت نمی‌شود تا collision و injection محدود شود؛ body/path query log نشود.
2. Backend structured JSON logs با timestamp UTC, level, service, environment, event, requestId, durationMs, status, low-cardinality route template/category, exceptionClass؛ `userId/deviceId` فقط در صورت نیاز و ترجیحاً حذف/short HMAC keyed by secret (اگر key infrastructure approved)؛ در نسخهٔ کم‌ریسک Phase 18 هیچ ID لازم نیست.
3. Bounded in-process API counters/histogram + `/metrics` private/internal exporter یا Prometheus text format با route template و method/status group؛ P50/P95/P99 از histogram windows. In-memory metrics reset on restart و multi-instance aggregation نیاز collector؛ endpoint باید internal-only محدود شود. اگر internal auth/network ACL از Compose فعلی قابل تضمین نیست، metrics endpoint private شبکه/feature disabled-by-default و configuration را BLOCKED اعلام کنیم، نه عمومی‌سازی.
4. Extend readiness checks only for actual critical dependencies (PostgreSQL + storage). Auth availability is covered by DB/config initialization; push stays NOT_CONFIGURED not critical. Check migration state safely (startup migration version) without revealing secrets.
5. Exception classification/structured event logging in StatusPages; counters AUTH/VALIDATION/DATABASE/NETWORK/INTERNAL etc. Avoid exception message/query, stack traces in production logs. Never include request body, credentials, location/SOS private payload, pairing code.
6. DB pool metrics: expose Hikari MXBean aggregate counts and connection acquisition saturation; no username/JDBC labels. Query-level slow query detection not added without safe SQL tagging and privacy review; source-level statement metrics with normalized SQL could leak if not managed. Document as BLOCKED unless robust proxy/Hikari metric route possible.
7. Android and Web error/crash instrumentation: do not transmit user/device metrics from clients. In this environment add only privacy-safe local development diagnostics if warranted; external SDK/device ANR telemetry requires explicit operator approval, consent/legal policy and infrastructure. Client crash monitoring remains BLOCKED, not simulated.
8. Alerting/dashboard/status page: no operator auth, deployment stack or alert destination exists. Do not expose internal dashboard as Parent/Child feature; provide dashboards/alert rules as config/documented templates only if deploy target is specified. Mark runtime alert verification BLOCKED.
9. Alert thresholds are suggestions until load data: document initial warning/critical thresholds as unapproved candidates, never claim measured SLO. No p99/cost thresholds invented as empirically justified.
10. Sync/notification/SOS/geofence metrics should be aggregate, event counters only; no user/device labels, coordinate, titles, note, metadata, app package or message. Delivery statuses only `generated/persisted/unknown`, never delivered/opened absent receipt.

## 5) تغییرهای پیاده‌سازی پس از ثبت Audit

- Backend middleware در Ktor برای هر request یک UUID تازه تولید می‌کند، مقدار caller-supplied را نادیده می‌گیرد، در response می‌گذارد و درخواست را با route template allowlisted، status و latency ثبت می‌کند. مسیر/Query خام به metric label/log نمی‌رود.
- `/metrics` با Prometheus text format افزوده شد: شمارش HTTP/status/error/429، جمع زمان و quantile اخیر p50/p95/p99، SQL aggregate timings/failures، و Hikari active/idle/waiting. سری‌ها و برچسب‌ها bounded هستند؛ quantile پنجرهٔ اخیر ۵۱۲ sample برای هر سری است، نه histogram دائمی.
- SQL extensionهای مشترک `query/exec/execReturningUuid` زمان و failure را بدون SQL text/parameters اندازه می‌گیرند. Pool gauges از HikariMXBean خوانده می‌شوند. Connection acquisition/commit durations و per-query slow SQL identification هنوز پوشش داده نشده‌اند.
- Backend logback به JSON encoder رفت؛ app eventها با markers شامل category/event و safe fields allowlist هستند. stacktrace/exceptions message در encoder عمداً provider ندارند. redactorهای token/email/code دیگر fingerprint یا بخش‌هایی از مقدار را برنمی‌گردانند. CallLogging string قبلی حذف شد و request completion log حاوی request ID جای آن را گرفت.
- Error handling دستهٔ خطا را از کد/status/مسیر استخراج و شمارندهٔ ثابت دسته‌ای را افزایش می‌دهد؛ exception message و request body ثبت نمی‌شوند.
- Metrics endpoint در Compose فقط روی network داخلی `server` expose می‌شود؛ matcher Caddy آن را proxy نمی‌کند. اگر server port مستقیماً به اینترنت publish شود، این فرض امنیتی از بین می‌رود و استقرار ممنوع است تا ACL/auth collector فراهم شود.
- Android helperهای token/email/code از hash/mask جزئی به مقدار `<redacted>` تغییر کردند. Android client crash/ANR SDK یا ارسال telemetry اضافه نشده است.

## 6) Audit exit criteria

The inventory above completes audit-before-code ordering. From this point implementation may add only operational instrumentation/config/tests and documentation; no user-facing monitoring capability, fake data, new external service, DB/cloud provisioning, or permission changes. Each build/test/runtime/secret scan must be labeled with the actual result and unavailable gates as **BLOCKED**.