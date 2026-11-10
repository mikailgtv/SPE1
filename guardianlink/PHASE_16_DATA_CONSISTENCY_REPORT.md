# Phase 16 — گزارش سازگاری داده

## منبع اصلی و مرزهای local state

| داده | منبع اصلی | local/offline copy | وضعیت اعتماد |
|---|---|---|---|
| accounts, sessions, families, devices, pairing | Backend/PostgreSQL | credential/session secure storage و transient UI state | Parent و device principal جدا؛ خانواده از principal server-side. |
| device status, battery, permission, health/presence | Backend snapshot/status rows | Child نمایش آخرین sync محلی؛ Parent query cache | status ممکن است stale باشد؛ timestamp/status lease لازم است. |
| screen-time usage | PostgreSQL usage rows پس از upload Child | آمار Android system query می‌شود؛ صف مستقل usage وجود ندارد | Child self-report با server bounds؛ مستقل/قابل‌اثبات از server نیست. |
| location | Backend location rows/settings | encrypted `LocationQueue` محدود به ۲۴۰ | sharing و permission gating؛ dedupe به timestamp. |
| geofence rules/state/history | Backend/PostgreSQL | Child فقط zone snapshot read می‌کند | state از server fix evaluator؛ نه client claims. |
| app/Web policy | Backend rule tables | encrypted versioned cache و bounded event queues | ذخیرهٔ policy با enforcement قطعی برابر نیست. |
| notifications | Backend event projection/preferences/user state | Android local alert/cache UI | projection فقط برای eventهای classify شده؛ push سروری ندارد. |
| SOS | Backend session/events/locations/notes | encrypted bounded outbox و tracking queue | queue آفلاین تا تأیید server delivered نیست. |
| Web UI | همان API | React Query in-memory، offline shell ثابت | shell/data-free؛ no persisted API cache. |

## جریان consistency

```text
Child official APIs
   │  StatusSyncWorker: status + usage + location + policies/events
   ▼
POST /v1/device/sync  ── transaction/repositories ──> PostgreSQL
   │ response: health, interval, app rules, Web Safety, location sharing
   └──> secure local caches/ACK بعد از موفقیت

PostgreSQL ── same authenticated /v1 routes ──> Android Parent
                                         └────> Parent Web
```

Parent mutations (rules/settings/notifications/SOS acknowledgements) مستقیماً در Backend هستند؛ sync Child در چرخهٔ بعدی policy/status را برمی‌گرداند. رویدادهای منتخب از `device_events` به `family_notifications` در مسیر synchronous DB projection تبدیل می‌شوند.

## Consistency per domain

- **Family/device ownership:** user family از DB گرفته می‌شود؛ device routes به family چک می‌شوند. Child ID از device token است. این ساختار جلوی اتکا به `family_id` client را در مسیر بررسی‌شده می‌گیرد؛ DB RLS ندارد و isolation runtime تست نشده.
- **Device status:** Parent Android و Web یک model سروری می‌خوانند. Child آخرین نتیجهٔ sync را local نمایش می‌دهد؛ timing متفاوت طبیعی است. Online/presence از heartbeat/lease است، نه realtime proof.
- **Events/notifications:** eventها server persisted هستند. notification user read state جداست؛ status acknowledge/resolve مشترک‌تر است. دو دستگاه Parent با همان user باید read-state یکسان بگیرند پس از fetch؛ هیچ تست بین دو session اجرا نشد. هر رویداد audit الزاماً اعلان نمی‌شود.
- **Usage/screen-time:** collector امروز/دیروز را محلی می‌سازد. Backend server upload idempotent UPSERT و window ≤۸ روز دارد. قبل از اصلاح، report day/timezone می‌توانست با دستگاه mismatch داشته باشد.
- **Location:** timestamp `clientTs` و source/accuracy از device؛ DB uniqueness روی `(device_id, client_ts)` گزارش شده. پس از ممیزی، Worker ابتدا status sync بدون مختصات می‌فرستد و فقط با پاسخ server `locationSharing=true` capture و batch را از `/v1/device/location/sync` ارسال می‌کند؛ false/null صف را پاک می‌کند. Queue پس از upload موفق حذف می‌شود؛ server sharing setting و fix validation همچنان مرجع ذخیره است. جعل زمان/موقعیت از client به‌طور مطلق قابل جلوگیری نیست؛ Parent آن را به‌عنوان telemetry گزارش‌شده ببیند.
- **Geofence:** source server computes transition از fix; accuracy uncertainty, hysteresis/cooldown/state/dedupe. Enter/exit request مستقیم Child پذیرفته نمی‌شود. zone state و events در Backend مشترک‌اند.
- **App policies:** version counter و optimistic expectedVersion conflict; Child local version stale نمی‌تواند version server را عقب ببرد. `lastEnforcementStatus` report از Child؛ `managed` claim دستگاه به‌تنهایی اثبات Device Owner server-side نیست.
- **Web Safety:** policy versioned، reported mode/status/reason، DNS/VPN status. `policy_stored_effect_unverified` و unsupported statuses باید قابل‌دیدن بمانند. content/visit data موجود نیست.
- **SOS:** server idempotency براساس client request id؛ یک session active برای device، transition/state machine و expire. Location candidate از sharing/status/validity و مجوز انتخابی می‌آید. Parent acknowledgement/resolve به family scope محدود است. Outbox آفلاین ممکن است expiry بگیرد.

## اصلاح timezone (انجام‌شده پس از ممیزی اولیه)

در `StatusSyncWorker`، usage روزمحور بر مبنای `ZoneId.systemDefault()` ساخته و همان `timeZoneId` در `appManagement` ارسال می‌شود. `DeviceRoutes` ابتدا `appManagementService.syncFromChild` را صدا می‌زند و سپس `usageService.upload`, پس timezone جدید برای usage همان sync در دسترس است.

پیش از Phase 16، `UsageService` بعضی queryها/thresholdها را با timezone JVM پیش‌فرض اجرا می‌کرد، و `safeTs` epoch/day boundary را هم به JVM zone می‌برد. اصلاح محدود در source:

- `deviceZone(deviceId)` از timezone persisted استفاده و در نبود/خطا UTC می‌دهد.
- date validation و `safeTs` و ابتدای روز در همان zone؛
- `evaluateLimit`, `evaluateSchedules`, `today`, `weekly`, `appsFor` بدون date، `appDetail`, `dailyReport` همگی از zone دستگاه استفاده می‌کنند.

**وضعیت اصلاح: اعمال‌شده در source؛ `BLOCKED` برای compile/test/runtime.** در device state قدیمی/بدون timezone ثبت‌شده، fallback UTC می‌ماند تا sync اول timezone را به‌روز کند. هیچ تستی از midnight/DST/timezone mismatch اجرا نشده؛ بنابراین correctness بعد از تغییر به production تأییدشده نیست.

## Consistency limitations

1. **BLOCKED:** مقایسهٔ recordهای دقیق API بین Android Parent/Web با DB.
2. **BLOCKED:** خانواده‌های جدا/کاربر بیگانه/ID دستکاری‌شده و RLS/DB role.
3. no shared generated DTO contract؛ manual mapping drift risk.
4. بدون API version negotiation؛ appVersion فقط telemetry است.
5. server process-local limiter و نبود multi-replica coordination.
6. usage آفلاین فاقد durable history queue برای روزهای فراتر از دو روز collector.
7. timezone correction هنوز compile/test نشده.
8. local UI caches باعث staleness قابل‌قبول می‌شوند؛ نه source truth مستقل.

## آزمون‌های سازگاری

- **PASS:** Web API serialization/error/normalization test subset 3 tests, i18n 1 test و build.
- **BLOCKED:** Backend integration tests (Testcontainers/Docker)، Android unit tests/build، DB migrations واقعی و cross-client API comparison.
