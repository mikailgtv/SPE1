# گزارش Phase 18 — Notification Monitoring

## معماری واقعی موجود
- event منتخب و family notification projection در مسیر DB transaction تولید می‌شود؛ Parent feed/unread polling دارد و Parent Android می‌تواند local OS notification بسازد.
- Backend push/FCM service یا push worker پیاده نشده؛ readiness مقدار `push_notifications=not_configured` را صریح می‌گوید.
- هیچ delivery receipt در server وجود ندارد؛ «notification row generated/persisted» معادل ارسال به Android، نمایش، دریافت یا بازشدن نیست.
- daily summary worker در Parent Android به‌صورت local aggregation اجرا می‌شود؛ metrics نتیجهٔ نمایش OS را به Backend گزارش نمی‌کند.

## Phase18 result
- API metrics شمارش callهای routeهای notifications و status/latency را پوشش می‌دهد؛ 4xx/5xx/429 نیز قابل تفکیک است.
- هیچ `sent`, `delivered`, `opened` یا `push_failed` counter ساخته نشد؛ دادهٔ موجود چنین delivery را اثبات نمی‌کند.
- event generation، projection dedupe، push path، local OS post failure و delivery latency همچنان در metrics جدا ثبت نمی‌شوند. Instrumentation aggregate بعد از transaction نیاز به design دقیق commit/rollback hook دارد.
- Alert برای push failure، queue depth یا delivery SLA تعریف نشد چون push/queue/alerting service واقعاً وجود ندارد.

## Test
- HTTP metrics/Testcontainers endpoint test به source اضافه شد اما اجرا **BLOCKED** است.
- Notification delivery failure test/OS notification test **BLOCKED** به‌علت نبود DB/Backend/device؛ هیچ notification جعلی ارسال نشد.

**تصمیم:** فقط API monitoring partial؛ هیچ تحویل یا دریافت notification ادعا نمی‌شود.