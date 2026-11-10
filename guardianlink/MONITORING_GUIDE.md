# راهنمای Monitoring و Observability

**وضعیت فعلی:** Health endpoints در source تغییر کرده‌اند؛ monitoring backend/cloud/device فعال یا آزموده نشده است. هیچ metric exporter، crash SDK، dashboard یا alert provision نشده.

## معماری پیشنهادی AWS

- **CloudWatch Metrics/Alarms:** ALB request count, target 5xx/latency/healthy-host count؛ ECS task CPU/memory/restarts; RDS CPU, connections, free storage, latency/failover; S3 backup object freshness/failure.
- **CloudWatch Logs:** structured server logs، retention حداقل مصوب، KMS encryption، محدودیت خوانندگان IAM، access/audit روی log groups.
- **External uptime probe:** از نقطهٔ مستقل `GET /health/live` و `/health/ready` با TLS validation؛ alert بر availability و latency. Probeها response/body/location data ذخیره نکنند.
- **DB monitoring:** RDS Performance Insights/CloudWatch; slow query review با query text redaction؛ no production query payload in general logs.
- **Mobile crash monitoring:** فعلاً هیچ crash monitoring vendor SDK نصب نشده. پیش از اضافه‌کردن SDK، privacy/legal review، minimization، consent/disclosure، region/retention و SDK data collection بررسی شود. Firebase/Crashlytics به‌عنوان پیاده‌سازی موجود ادعا نمی‌شود.

## شاخص‌ها و alertهای لازم پیش از Production

| سطح | شاخص/هشدار | مسیر فعلی |
|---|---|---|
| API | availability, p50/p95 latency, 5xx/error rate, request volume | `/health/live` و `/health/ready` فقط health هستند؛ metrics endpoint وجود ندارد. |
| Database | pool active/idle/pending, connection errors, RDS CPU/connections/storage, query latency | Hikari pool limits/timeouts config شد؛ export metric wiring نشده. |
| Infra | ECS desired/running, restart count, CPU/memory, ALB target health | ECS/CloudWatch ایجاد نشده. |
| Backup | آخرین encrypted dump، S3 object/checksum، restore-drill age، retention failure | script/guide آماده؛ scheduler, bucket, alert موجود نیست. |
| Auth/security | login failures/rate limits/permission anomalies/session revocation | event/audit/metrics جامع وجود ندارد؛ ثبت password/token مطلقاً ممنوع. |
| Notifications | delivery failures, queue depth | server-side Push/FCM پیاده نیست؛ push failure metric «N/A»، نه صفر. |
| Android | crash-free sessions, ANR/startup, background work failures | SDK/Play Console/enterprise telemetry در دسترس نیست؛ instrumentation tests صفر. |

## Health semantics

- `/health/live`: process/router پاسخ می‌دهد؛ dependency probe ندارد تا dependency outage سبب restart loop نشود.
- `/health/ready`: SQL `SELECT 1`, DATA_DIR وجود/خواندن/نوشتن؛ DB یا storage unavailable → HTTP 503.
- `/health`: backward-compatible alias برای readiness.
- `push_notifications=not_configured` شفاف است؛ service پیاده نشده و readiness را به‌تنهایی red نمی‌کند.
- `/health/ready` public response جزئیات exception/path/credentials را نمی‌دهد؛ endpoint runtime هنوز تست نشده.

## Logging

`logback.xml` default level را `INFO` می‌گذارد؛ `APP_ENV=production` با `LOG_LEVEL=DEBUG` startup را رد می‌کند. CallLogging format فقط method/status دارد و URI/query/body را ثبت نمی‌کند. Log capture test، correlation ID، structured JSON, trace ID, request duration و remote log pipeline هنوز اضافه/تأیید نشده‌اند.

هیچ Password/JWT/Refresh Token/secret، مختصات دقیق، SOS payload، email کامل، note یا private user data در log نگذارید. logهای upstream proxy/load-balancer هم باید query/body logging خاموش داشته باشند.

## SLO پیشنهادی (تصویب نشده)

پیش از target نهایی، ۳۰ روز baseline staging/load test ثبت شود. SLO اولیه برای پیشنهاد: API availability ≥99.5% ماهانه، p95 latency ≤500ms برای مسیرهای read عمومی و p95 ≤1s برای writeهای معمول، ولی routeهای گزارش/تاریخچه و ظرفیت دیتابیس باید benchmark شوند. این عددها فعلاً **هدف پیشنهادی‌اند، نه عملکرد اندازه‌گیری‌شده یا تعهد محصول**.

## به‌روزرسانی Phase 18 — source change (2026-10-03)

- Backend source اکنون JSON logging با safe field allowlist، UUID request ID تولیدشده در سرور، route template محدود و completion duration دارد؛ markerها user/device/session/email/token/code/body/location و متن exception را نمی‌گیرند. stack trace provider خاموش است.
- `GET /metrics` متن Prometheus با request/status/429 و quantileهای اخیر HTTP، SQL statement aggregate errors/latency و Hikari pool gauges دارد. این metricها process-local هستند، recent window آن‌ها حداکثر ۵۱۲ sample برای هر سری است و با restart پاک می‌شوند.
- Compose پورت Backend را private `expose` می‌کند، Caddy `/metrics` را به collector proxy نمی‌کند و اکنون public edge برای `/metrics`، 404 می‌دهد. Metrics scrape فقط از شبکهٔ private و با کنترل دسترسی اپراتوری مجاز است.
- query text/parameters، statement-level identities، connection acquisition/commit durations و query-specific slow statements اندازه‌گیری نمی‌شوند. per-device worker attempt/queue metrics، notification delivery, SOS/geofence counters، alerting/dashboard، log retention/collector و cloud integrations همچنان provision نشده‌اند.
- این تغییرها **source-only** هستند. Build Backend/JUnit/Testcontainers/Compose, scrape، log pipeline و alert/recovery هنوز **BLOCKED_BY_ENVIRONMENT** هستند؛ Hikari/threshold config هنوز load-test نشده.
