# Phase 16 — گزارش امنیت integration

**نوع بررسی:** source/config review، نه penetration test یا dynamic test. هیچ live database، authenticated API یا دستگاهی در این گزارش آزموده نشده است.

## کنترل‌های مثبت مشاهده‌شده در source

- **تفکیک principal:** Parent JWT با `typ=user` و Child JWT با `typ=device`; access tokenهای EdDSA/Ed25519، issuer و expiry بررسی می‌شوند؛ هر دو مسیر session موجود/منقضی/لغوشده را به‌صورت زنده بررسی می‌کنند.
- **Refresh/pairing secret:** refresh token opaque و hash در DB با rotation؛ pairing code/exchange token hash؛ device secret hash. Response مربوط به confirm، secret را یک‌بار برمی‌گرداند.
- **خانواده/دستگاه:** Parent family از user جاری می‌آید؛ `ownedDevice` foreign/inactive device را not found می‌کند؛ Child device از JWT subject گرفته می‌شود. Pairing code one-use، TTL و install-id checks/row locks دیده می‌شود.
- **Android storage:** `SecureStore` با AES-GCM و Android Keystore؛ location/SOS/app/WebSafety queueها در مسیر امن نگهداری می‌شوند. permissions را Android رسمی مدیریت می‌کند.
- **Web credential handling:** tokenها tab-memory only؛ `credentials: omit`, `cache: no-store`; same-origin API base اجباری است و absolute origin رد می‌شود. Service Worker `/v1` را از handler خارج می‌کند.
- **Edge config:** Caddy headers شامل HSTS، `nosniff`, no-referrer، `X-Frame-Options: DENY`، restrictive Permissions-Policy و CSP است. Backend در Compose به‌صورت `expose` روی private network است و Caddy reverse proxy آن است. این config اجرا/تأیید نشده.
- **Logging:** Ktor CallLogging فقط method/status ثبت می‌کند و path/query/body را حذف می‌کند. مسیر SOS صریحاً coords را در log/event metadata نگه نمی‌دارد. IDs در logهای دامنه mask می‌شوند (source-level evidence).
- **Input/rate controls:** DTO validation، boundهای تعداد/طول/مقادیر، per-route process-local rate limits و server-side ownership در مسیرهای مربوط موجودند.
- **Android consent/policy:** location/Usage Access permission واقعی و DPM Device Owner check؛ unsupported/failed را گزارش می‌کند و Web Safety modeهای VPN/DNS را unsupported می‌داند. پس از ممیزی Phase 16، Child ابتدا server-authoritative location sharing را می‌خواند و در false/unknown capture/upload را fail-closed می‌کند؛ Backend نیز هنگام off از ذخیره جلوگیری می‌کند. SOS user confirmation/countdown دارد و UI می‌گوید سرویس امدادی ثالث خبر نمی‌شود.

## شکاف‌ها و ریسک‌های مشاهده‌شده

| سطح | یافته | اثر/گام لازم |
|---|---|---|
| بالا / اثبات‌نشده | migrations V1–V9 در scan، RLS enable/policies یا `audit_logs` عمومی نشان ندادند. | isolation فعلی متکی به route/service authorization است؛ DB account/role، backup access و query scoping باید با DB واقعی و tests دو خانواده بررسی شوند. |
| بالا / BLOCKED | Backend/Android auth، pairing، removal/revocation و cross-family tests اجرا نشده‌اند. | source test وجود دارد اما PASS گزارش نمی‌شود؛ اجرای CI/Testcontainers/Docker لازم است. |
| متوسط | `RateLimiter` در حافظهٔ process است؛ چند replica counter مشترک ندارند. | production scale-out می‌تواند limits را دور بزند/ناهمسان کند؛ distributed limiter یا معماری single-instance روشن لازم است. Redis در source جاری مشاهده نشد. |
| متوسط | workflow Trivy از `aquasecurity/trivy-action@0.36.0` با tag استفاده می‌کند نه commit SHA؛ scan این محیط اجرا نشد. | supply-chain pinning و CI logs/scan نتیجه لازم است؛ عدم اجرای scan به معنی پاک بودن image نیست. |
| متوسط | Ed25519 private JWK زیر `DATA_DIR/ed25519.jwk` ایجاد و در volume نگهداری می‌شود؛ rotation/backup access controls در این ممیزی runtime بررسی نشد. | حفاظت filesystem/volume/backup و key lifecycle باید با deploy واقعی ارزیابی شود. |
| متوسط | app/device/OS version ثبت می‌شود اما API compatibility/min-supported version endpoint یا policy پیدا نشد. | کلاینت قدیمی ممکن است قرارداد جدید را در زمان نامناسب بزند؛ rollout/version gate لازم است. |
| متوسط | API DTOها مستقل‌اند؛ OpenAPI/codegen و contract coverage جامع نیست. | drift JSON می‌تواند در یکی از سه client unnoticed بماند؛ schema/contract tests ضروری است. |
| متوسط | `DevicePolicyManager` و Chrome reports از Child آمده‌اند؛ سرور نمی‌تواند claim دستگاه را independently attestate کند. | `managed`/`policy stored` را enforcement proof نخوانید؛ UI باید reported status/reason برجسته کند. |
| متوسط | device removal soft-removal/revocation انجام می‌دهد؛ از source review، حذف کامل همهٔ telemetry/SOS/history یا backup purge اثبات نشده است. | retention/erasure semantics را مستند و با migration/data tests سنجید؛ به کاربر ادعای purge کامل نشود. |
| محدودیت | DB at-rest encryption، secrets injection، file permissions، TLS certificate deployment، backup restore و alerting در این محیط بررسی نشده‌اند. | فقط deploy واقعی می‌تواند این موارد را ثابت کند؛ Compose config به‌تنهایی production hardening نیست. |

## Data minimization و privacy boundary

در collectorهای بررسی‌شده دادهٔ UsageStats شامل package/app name، usage duration، count، دسته‌بندی سیستم و بعضی first/last times/icons است؛ نه محتوای اپ/پیام/کی‌استروک. Location coordinates در مسیر location/SOS. Web Safety مدل eventهای policy/status است، نه browser visits/page content. Call log/contacts API دیده نشد. Camera/microphone/photos/clipboard/cookies/search/form/private-message collection دیده نشد و نباید ادعا شود.

Web dashboard `preview` یا offline shell دسترسی protected data نمی‌دهند. Parent UI authorization جایگزین server authorization نیست. Third-party SOS sharing/dispatch وجود ندارد.

## Auditability

- Domain `device_events`, SOS event history و family notification projection وجود دارند؛ این‌ها event-domain evidence هستند، نه audit log فراگیر برای تمام read/write/auth/admin actionها.
- RLS/general `audit_logs` در migrations فعلیِ بررسی‌شده پیدا نشد. application logs کاهش اطلاعات دارند، اما جایگزین append-only audit trail و query audit نیستند.
- source integration/security tests شامل cross-family/device/pairing حالات هستند؛ اجرا نشده‌اند.

## آزمون امنیتی

- **PASS:** فقط Web unit tests/build (نه security penetration).
- **BLOCKED:** backend security tests/Testcontainers/Docker، Android permission behavior، dynamic API authorization, rate-limit, secret/session revocation و live Caddy/TLS/CSP.
- **NOT RUN:** npm audit در این Phase، Trivy/image scan، SAST/DAST یا external penetration test.
