# 10 — برنامه تست (Test Plan)

## ۱. هرم تست

```
                 ┌──────────────┐
                 │  E2E دستی    │  ۸ سناریوی Phase 1 (دو گوشی واقعی)
                 │  (۲ گوشی)     │
               ┌─┴──────────────┴─┐
               │ Instrumentation   │  Compose UI test · Room migration test · Keystore/Tink
               │  (~۳۰ در P1)      │
             ┌─┴──────────────────┴─┐
             │  Integration (سرور)   │  Ktor test-host + Testcontainers (Postgres/Redis)
             │  (~۴۰ در P1)          │
           ┌─┴──────────────────────┴─┐
           │   Unit (JVM — سریع)       │  UseCase · ViewModel · Mapper · Repository(fake) · Crypto · Redactor
           │   (~۱۲۰ در P1)            │
           └──────────────────────────┘
```

| لایه | ابزار | کجا | هدف |
|---|---|---|---|
| Unit (Domain) | JUnit5 · MockK · kotlinx-coroutines-test · Turbine | `domain/*`, `core/*` | منطق بدون Android، < ۱۰ms هر تست |
| ViewModel | Turbine + `MainDispatcherRule` + Fake UseCase | `feature/*/test` | State transitions و رویدادها |
| Repository | `InMemoryBackendGateway` + Room in-memory | `data/*/test` | mapping، cache، offline، retry |
| API (سرور) | `ktor-server-test-host` + **Testcontainers** Postgres/Redis | `server/*/test` | contract، auth، RLS، rate limit |
| Database | Room migration test + Flyway validate | `core/database/androidTest` | schema و migration |
| UI | `compose-ui-test` (Robolectric برای JVM، Instrumentation برای واقعی) | `feature/*/androidTest` | رندر، RTL، حالت‌ها |
| Crypto/Security | تست اختصاصی | `core/security/test` + `server/shared/crypto/test` | HMAC، nonce، Argon2، Tink، pinning config |
| E2E | دستی + سناریونامه (و در P9: Maestro) | `docs/testing/` | رفتار واقعی روی دو گوشی |

**هدف پوشش Phase 1:** Domain ≥ ۹۰٪ · Data ≥ ۸۰٪ · ViewModel ≥ ۸۰٪ · Server modules ≥ ۸۰٪ (با Jacoco/Kover در CI، gate روی MR).

---

## ۲. تست‌های خودکار ضروری Phase 1 (فهرست دقیق)

### ۲.۱ Domain / UseCase
```
RegisterUseCaseTest         → ساخت حساب+family موفق / رمز ضعیف / ایمیل تکراری / network error
LoginUseCaseTest            → موفق / رمز اشتباه / locked / offline → UiState.Offline
ObserveSessionUseCaseTest   → emit های Flow بعد از login/logout/refresh
CreatePairingCodeUseCaseTest→ TTL درست / rate limit / offline queue
RedeemPairingCodeUseCaseTest→ موفق / expired / invalid(attempts_left) / used / device_limit / already_paired
ConfirmPairingUseCaseTest   → consent ذخیره می‌شود / status=completed / خطای امضا
ObserveDevicesUseCaseTest   → cache-first سپس network / خطا ⇒ cached با isStale
GetDeviceSnapshotUseCaseTest→ ترکیب status+battery+permissions / ETag 304 / offline
RemoveDeviceUseCaseTest     → revoke+purge / خطای ۴۰۴ / به‌روزرسانی optimistic لیست
ObserveDeviceStatusUseCaseTest → presence محاسبه‌شده از lease (online→stale→offline)
```

### ۲.۲ ViewModel (با Turbine)
```
LoginViewModelTest          → Loading→Success / Loading→Error(CREDENTIALS_INVALID) / Offline / validation
DashboardViewModelTest      → Loading→Success / Empty (بدون دستگاه) / Offline(cached) / Error+retry
                              / SSE device.online ⇒ presence عوض شود بدون refetch
                              / battery.updated ⇒ درصد و رنگ عوض شود
PairingViewModelTest        → countdown / pending→redeemed→confirmed→completed / expired / cancel
DeviceDetailViewModelTest   → snapshot / permission states / rename optimistic+rollback
RemoveDeviceViewModelTest   → hold-to-confirm / success ⇒ از لیست حذف / failure ⇒ پیام
PermissionWizardViewModelTest(child) → وضعیت هر مجوز، هدایت به Settings، بازگشت و re-check
```

### ۲.۳ Repository / Data
```
HttpDeviceRepositoryTest    → با InMemoryBackendGateway: نگاشت DTO→Domain درست، خطا→AppError درست
OfflineQueueRepositoryTest  → قطع شبکه ⇒ در Room ذخیره ⇒ اتصال ⇒ flush با Idempotency-Key ⇒ دوباره ارسال نشود
DeviceDaoTest (Room)        → insert/query/observe Flow/delete
TokenStoreTest              → Tink roundtrip / رمزگشایی با کلید اشتباه fail-closed / هیچ plaintext روی دیسک
SecureStoreCorruptionTest   → فایل خراب ⇒ پاک‌سازی + مسیر re-auth (نه crash)
HmacRequestSignerTest       → golden vectors ثابت + canonical string + body hash + timestamp skew
RedactorTest                → password/token/email/lat-lng هرگز در خروجی نباشد (property-based)
```

### ۲.۴ Server (Ktor test-host + Testcontainers)
```
AuthRoutesTest              → register 201 / duplicate 409 / login 200 / wrong 401 / lockout 423
RefreshTokenRotationTest    → چرخش / reuse ⇒ کل زنجیره revoke + audit[SECURITY]
JwtValidationTest           → exp/iss/aud/kid/fp mismatch ⇒ 401
PairingRoutesTest           → create/exchange/confirm / expired 422 / used 422 / attempts>10 ⇒ invalidated
DeviceSignatureAuthTest     → امضای درست 200 / timestamp skew>300s ⇒ 401 / nonce تکراری ⇒ 401 replay / revoked ⇒ 401
HeartbeatTest               → lease تمدید / battery ذخیره / idempotency_key تکراری ⇒ ۲۰۴ بدون duplicate
RlsTenantIsolationTest      → ⭐ کاربر خانواده A به دستگاه خانواده B ⇒ 403/404 و audit[SECURITY][rls_denied]
RateLimitTest               → ۶ تلاش login در دقیقه ⇒ 429 با Retry-After
RemoveDeviceTest            → revoke + purge + outbox event + 410 برای heartbeat بعدی
SseEventsTest               → اتصال، دریافت device.online، reconnect با Last-Event-ID ⇒ رویداد از دست نرود
AuditLogRedactionTest       → metadata هیچ فیلد ممنوعه‌ای ندارد
```

### ۲.۵ Anti-Tamper (v2)
```
AdminReceiverTest            → غیرفعال‌کردن Device Admin ⇒ tamper_events(protection.removed, critical)
                                + audit[TAMPER] + SSE tamper.detected به والد
WatchdogSnapshotDiffTest     → تغییر state مجوز بین دو اجرا ⇒ permission.changed با severity درست
LaunchForensicsTest          → شبیه‌سازی force-stop (kill process) ⇒ در launch بعدی service.force_stopped
DataClearedRecoveryTest      → پاک‌کردن data ⇒ حالت Recovery + کد بازیابی bound به device_id قدیمی ⇒ تاریخچه حفظ
SilenceAnalyzerTest (سرور)   → gap > lease بدون رویداد connectivity ⇒ device.silent +Tamper/warning
ReEnrollHeuristicTest        → enroll جدید با fingerprint مشابه در 24h ⇒ data.cleared_suspected
TimeTamperTest               → تغییر ساعت دستگاه ⇒ time.tampered (info) و محاسبات limit با server_time
DedupeTest                   → ارسال مجدد یک dedupe_key ⇒ فقط یک ردیف tamper_events
AckFlowTest                  → ack/resolved/false_alarm والد ⇒ status درست + فقط صاحب خانواده
```

### ۲.۶ Child Zero-Notification (v2 — اجباری)
```
ZeroNotificationInstrumentedTest → پس از ۲۴h شبیه‌سازی چرخه‌ها (wakeها، alarmها، FCM wake):
                                   NotificationManager.activeNotifications.size == 0
NoForegroundServiceTest          → هیچ FGS فعال برای پکیج child در هر لحظه
LintRule CI                      → ممنوعیت NotificationManager.notify / Notification.Builder در :app:child و :service:*
PresencePushVerifiedE2E          → باز کردن Dashboard ⇒ تأیید online/offline ≤ 70s
```

### ۲.۷ Instrumentation (روی گوشی/امولاتور)
```
BatteryCollectorInstrumentedTest   → مقدار واقعی بین ۰..۱۰۰ و status معتبر
PermissionSnapshotInstrumentedTest → ۱۵ نوع مجوز بدون crash گزارش می‌شود
ConnectivityMonitorInstrumentedTest→ تغییر Wi-Fi→Airplane ⇒ `none` منتشر شود
SyncForegroundServiceInstrumentedTest → start/stop، نوتیف، restart بعد از kill، BootReceiver
ComposeUiTest (fa + en)            → Dashboard در هر دو locale و RTL درست رندر شود
StateViewInstrumentedTest          → هر ۵ حالت
KeystoreAvailabilityTest           → StrongBox fallback بدون crash
```

---

## ۳. آماده‌سازی محیط تست

### سناریو A — LAN (پیشنهادی، بدون اینترنت عمومی)
```bash
# روی PC (Docker نصب باشد)
cd deploy && cp .env.example .env && ./scripts/gen-keys.sh
docker compose -f docker-compose.dev.yml up -d      # postgres + redis
cd ../server && ./gradlew run                        # http://0.0.0.0:8080
ip addr | grep 192.168                               # مثلاً 192.168.1.20
```
- در هر دو اپ (debug): **Dev Menu → Base URL** = `http://192.168.1.20:8080/v1`
- گوشی‌ها و PC روی یک Wi-Fi. فایروال PC پورت 8080 را باز بگذارد.

### سناریو B — دامنه عمومی (وقتی LAN ممکن نیست)
```bash
cloudflared tunnel --url http://localhost:8080    # ⇒ https://xyz.trycloudflare.com
```
Base URL را در Dev Menu بگذارید `https://xyz.trycloudflare.com/v1`.

### سناریو C — بدون سرور (فقط UI)
Dev Menu → **Mock mode** ⇒ `InMemoryBackendGateway`. (برای ضبط ویدیو؛ در release غیرفعال)

### نصب روی دو گوشی
```bash
# والد
./gradlew :app:parent:assembleDebug
adb -s <PARENT_SERIAL> install -r android/app/parent/build/outputs/apk/debug/parent-debug.apk
# فرزند
./gradlew :app:child:assembleDebug
adb -s <CHILD_SERIAL>  install -r android/app/child/build/outputs/apk/child-debug.apk
```
بدون `adb`: انتقال APK با Drive/کابل، فعال‌کردن «نصب از منابع ناشناس».

### مشاهده لاگ‌ها
```bash
adb logcat -v time | grep -E "\[(AUTH|PAIRING|DEVICE|SECURITY|SYNC|PRESENCE|BATTERY)\]"
docker compose logs -f server | jq .        # لاگ JSON سرور
docker compose exec postgres psql -U gl_migr -d guardianlink \
  -c "select category,action,result,created_at from audit_logs order by id desc limit 20;"
```

---

## ۴. هشت تست دستی Phase 1 (دقیقاً مطابق درخواست شما)

> هر تست: **پیش‌نیاز → گام‌ها → نتیجه مورد انتظار → معیار PASS → در صورت FAIL چه لاگی را ببینیم**

### ✅ Test 1 — ساخت Parent Account
**پیش‌نیاز:** سرور بالا (`curl http://<host>:8080/health` ⇒ `{"status":"ok"}`)؛ اپ والد نصب.
**گام‌ها:**
1. اپ والد را باز کنید ⇒ Onboarding (۳ اسلاید) ⇒ «شروع»
2. «ساخت حساب» ⇒ ایمیل `parent1@test.local`، رمز `Tehran2026!x`، نام `مریم`، نام خانواده `خانواده رضایی`
3. «ادامه»
**نتیجه مورد انتظار:**
- ورود مستقیم به Dashboard (بدون خطا)
- Dashboard در حالت `Empty`: «هنوز دستگاهی متصل نکرده‌اید» + دکمه «افزودن دستگاه»
- نام خانواده در Top bar: «خانواده رضایی»
**PASS اگر:** در DB یک `users` با `status='active'`، یک `families`، یک `family_members(role='owner_parent')`، یک `family_deks` و یک `audit_logs(category='AUTH', action='auth.register', result='success')` وجود داشته باشد:
```sql
SELECT u.email, f.name, m.role, (dek.family_id IS NOT NULL) AS has_dek
FROM users u JOIN family_members m ON m.user_id=u.id
JOIN families f ON f.id=m.family_id LEFT JOIN family_deks dek ON dek.family_id=f.id;
```
**تست منفی:** رمز `123` ⇒ `422 VALIDATION_FAILED` و پیام فارسی زیر فیلد. ایمیل تکراری ⇒ `409 EMAIL_TAKEN`.
**در صورت FAIL:** `docker compose logs server | grep AUTH` + `request_id` از دیالوگ خطا.

---

### ✅ Test 2 — ثبت Child Device (نصب و راه‌اندازی اپ فرزند)
**پیش‌نیاز:** Test 1 پاس شده. اپ فرزند نصب. Base URL ست شده.
**گام‌ها:**
1. اپ فرزند را باز کنید ⇒ صفحه «خوش آمدی» با توضیح صادقانه
2. «ادامه» ⇒ صفحه رضایت (لیست ✅ها و ⛔ها)
3. نام فرزند: `سارا` ⇒ «بعدی»
4. ویزارد مجوزها: **اعلان‌ها** (در Android 13+ «اجازه») · **بهینه‌سازی باتری** («مجاز»)
5. «اتصال به خانواده» ⇒ صفحه Pairing (دو تب: اسکن QR / ورود کد)
**نتیجه مورد انتظار:**
- هر مجوز بعد از بازگشت از Settings **بلافاصله** سبز شود (بدون ریستارت اپ)
- وضعیت مجوزها در `permissions` (هنوز با `install_id` موقت، پیش از pairing در حافظه محلی)
- هیچ crash در هیچ مرحله؛ با زدن Back از Settings، اپ به همان مرحله برگردد
**PASS اگر:** اپ تا صفحه Pairing برسد و در صفحه «حریم خصوصی» (قبل از pairing) لیست داده‌ها نشان داده شود.
**در صورت FAIL:** `adb logcat | grep -E "\[(PERMISSION|UI)\]"`

---

### ✅ Test 3 — Pair کردن دو دستگاه
**پیش‌نیاز:** Test 1 و 2. هر دو گوشی + سرور در دسترس. والد login.
**گام‌ها (والد):**
1. Dashboard ⇒ «افزودن دستگاه» (یا `+` در Quick Actions)
2. پروفایل فرزند: `سارا` + آواتار ⇒ «ساخت کد»
3. QR + کد `XXXX-XXXX` + countdown ۱۰:۰۰ نمایش داده شود
**گام‌ها (فرزند):**
4. تب «اسکن QR» ⇒ دوربین ⇒ QR والد را اسکن کنید **یا** تب «ورود کد» ⇒ کد را تایپ کنید
5. «اتصال» ⇒ صفحه Connecting با ۴ مرحله
6. صفحه «والد من چه چیزی می‌بیند» ⇒ «می‌پذیرم و وصل می‌شوم»
**نتیجه مورد انتظار:**
- والد (بدون refresh دستی، از SSE): وضعیت به «دستگاه پیدا شد» سپس «در حال تأیید فرزند» سپس ✅ «متصل شد»
- نوتیف به والد: «دستگاه سارا متصل شد»
- فرزند: تب Status ⇒ 🟢 متصل به «خانواده رضایی» + battery + last sync
- والد: Dashboard ⇒ کارت سارا با 🟢 Online
**PASS اگر:**
```sql
SELECT d.id, d.nickname, d.status, d.paired_at, c.algorithm, s.presence, s.battery_level,
       (SELECT count(*) FROM permissions p WHERE p.device_id=d.id) AS perm_rows,
       (SELECT count(*) FROM data_consents dc WHERE dc.device_id=d.id AND dc.granted) AS consents
FROM devices d JOIN device_credentials c ON c.device_id=d.id
JOIN device_status s ON s.device_id=d.id WHERE d.family_id='<FAMILY_ID>';
-- انتظار: status='active', paired_at NOT NULL, perm_rows>=2, consents>=4
```
و `audit_logs` شامل `pairing.code_created` و `pairing.redeemed` و `pairing.confirmed` باشد.
**تست‌های منفی (حتماً اجرا شوند):**
| سناریو | انتظار |
|---|---|
| کد اشتباه (`AAAA-AAAA`) | «کد نادرست است — ۹ تلاش باقی‌مانده» |
| ۱۱ بار کد اشتباه | کد `invalidated` + هشدار `[SECURITY]` در لاگ سرور |
| صبر تا پایان countdown | «کد منقضی شد» + در والد countdown قرمز و «کد جدید» |
| استفاده مجدد از همان کد موفق | `422 PAIRING_CODE_USED` |
| حالت هواپیما روی گوشی فرزند در لحظه exchange | «در انتظار اتصال» + صف Outbox + به محض اتصال، کامل شود |

---

### ✅ Test 4 — نمایش Online
**پیش‌نیاز:** Test 3 پاس.
**گام‌ها:**
1. والد: Dashboard را باز کنید
2. فرزند: اپ را باز نگه دارید، سپس به Home بروید و ۳ دقیقه صبر کنید
3. در والد، Pull-to-refresh نکنید — فقط تماشا کنید
**نتیجه مورد انتظار:**
- 🟢 «آنلاین» با زمان «آنلاین از HH:MM»
- با باز کردن Dashboard: تأیید push-verified ≤ ~۱۰ ثانیه (FCM data-ping ⇒ پاسخ Child)
- «آخرین همگام‌سازی» در هر baseline (۱۵ دقیقه) و هر wake رویدادی به‌روز شود + badge «آخرین تأیید: X پیش»
- در Device Detail: `lease_expires_at` در آینده باشد (lease = 20 دقیقه)
- **هیچ Notification روی گوشی فرزند ساخته نشود** (بررسی دستی نوار اعلان)
**PASS اگر:** `device_status.presence='online'` و `now() - last_heartbeat_at < 90s` و **بدون** refresh دستی UI به‌روز شده باشد:
```sql
SELECT presence, last_heartbeat_at, lease_expires_at,
       extract(epoch from (now()-last_heartbeat_at))::int AS secs_ago
FROM device_status WHERE device_id='<DEVICE_ID>';
```
**نکته:** اگر گوشی فرزند را ۲ دقیقه در Doze بگذارید و همچنان online بماند ⇒ PASS واقعی (FGS کار می‌کند).

---

### ✅ Test 5 — نمایش Battery
**پیش‌نیاز:** Test 4.
**گام‌ها:**
1. درصد باتری واقعی گوشی فرزند را از نوار وضعیت بخوانید
2. در والد، Dashboard و Device Detail را ببینید
3. گوشی فرزند را به شارژر بزنید
4. ۹۰ ثانیه صبر کنید
**نتیجه مورد انتظار:**
- درصد در والد = درصد واقعی (اختلاف ≤ ۱٪ در همان لحظه sync)
- آیکون/برچسب «در حال شارژ» ظاهر شود و رنگ به `success` تغییر کند
- دما (`battery_temperature_c`) منطقی باشد (۲۰–۴۵°C)
- اگر باتری زیر ۲۰٪ برود ⇒ رویداد `battery.low` در timeline (Alert کامل در Phase 7)
**PASS اگر:**
```sql
SELECT battery_level, battery_status, battery_temperature_c, is_power_save_mode, updated_at
FROM device_status WHERE device_id='<DEVICE_ID>';
SELECT type, payload, server_ts FROM telemetry_events
WHERE device_id='<DEVICE_ID>' AND type='battery' ORDER BY id DESC LIMIT 5;
```
و مقدار با واقعیت گوشی یکی باشد (نه عدد ثابت/تصادفی).
**تست دقت:** حالت «ذخیره نیرو» را روی گوشی فرزند روشن کنید ⇒ `is_power_save_mode=true` در والد.

---

### ✅ Test 6 — قطع اینترنت Child ⇒ نمایش Offline در Parent
**پیش‌نیاز:** Test 5. ساعت را یادداشت کنید.
**گام‌ها:**
1. گوشی فرزند: **حالت هواپیما** را روشن کنید (یا Wi-Fi + داده را خاموش کنید)
2. تایم بگیرید
3. والد: Dashboard را تماشا کنید (بدون refresh)
**نتیجه مورد انتظار:**
- اگر Dashboard والد باز باشد: ≤ ~۷۰ ثانیه بعد 🔴 (ping بی‌پاسخ ⇒ offline فوری)
- اگر Dashboard باز نباشد: پس از انقضای lease (≤ ~۲۲ دقیقه) 🔴 + رویداد `device.silent`/`device.offline`
- زیر آن: «آخرین اتصال: ۲ دقیقه پیش» و `presence_reason='grace_expired'`
- آخرین درصد باتری **نگه داشته می‌شود** ولی با badge «قدیمی» (نه حذف، نه صفر)
- نوتیف به والد (اگر Phase 7 فعال نباشد، حداقل در timeline): «دستگاه سارا آفلاین شد»
- والد **crash نمی‌کند** و UI قفل نمی‌شود
**PASS اگر:** `presence='offline'` یا `'stale'` و `offline_since` تقریباً برابر زمان قطع باشد:
```sql
SELECT presence, presence_reason, offline_since, last_heartbeat_at, battery_level
FROM device_status WHERE device_id='<DEVICE_ID>';
SELECT event_type, payload, created_at FROM outbox
WHERE family_id='<FAMILY_ID>' ORDER BY id DESC LIMIT 5;   -- باید device.offline باشد
```
**تست تکمیلی (مهم):** اینترنت **والد** را قطع کنید (نه فرزند) ⇒ Dashboard باید داده کش‌شده را با banner «آفلاین هستید» نشان دهد و crash نکند. سپس با `adb shell cmd connectivity airplane-mode enable` روی گوشی فرزند، خاموش‌شدن **دستگاه فرزند** را هم شبیه‌سازی کنید.

---

### ✅ Test 7 — Reconnect ⇒ بازگشت به Online
**پیش‌نیاز:** Test 6 (دستگاه آفلاین است).
**گام‌ها:**
1. گوشی فرزند: حالت هواپیما را خاموش کنید
2. صبر کنید تا Wi-Fi/داده وصل شود
3. والد: Dashboard را تماشا کنید
**نتیجه مورد انتظار:**
- با Dashboard باز: ≤ ~۱۵ ثانیه بعد 🟢 (پاسخ به ping)
- بدون Dashboard: در اولین baseline/wake بعدی (≤ ۱۵ دقیقه) 🟢
- «آخرین همگام‌سازی: همین حالا»
- درصد باتری به‌روز شود (اگر در این مدت تغییر کرده، عدد جدید)
- داده‌های صف‌شده در Outbox گوشی فرزند ارسال شوند ⇒ در timeline رویدادهای فاصله آفلاینی دیده شود
- `online_since` = زمان بازگشت
**PASS اگر:**
```sql
SELECT presence, online_since, last_heartbeat_at, last_sync_at, battery_level FROM device_status WHERE device_id='<DEVICE_ID>';
SELECT count(*) FROM telemetry_events WHERE device_id='<DEVICE_ID>' AND server_ts > now() - interval '10 minutes';
```
و **هیچ داده تکراری** نباشد (Idempotency):
```sql
SELECT idempotency_key, count(*) FROM telemetry_events
WHERE device_id='<DEVICE_ID>' GROUP BY 1 HAVING count(*) > 1;   -- باید ۰ ردیف باشد
```
**تست سخت‌گیرانه‌تر:** در حالت آفلاین، اپ فرزند را **Force Stop** کنید، سپس اینترنت را وصل کنید و اپ را باز کنید ⇒ باید `BootReceiver`/شروع اپ سرویس را برگرداند و در ≤ ۹۰ ثانیه online شود.

---

### ✅ Test 8 — Remove Device (و قطع دسترسی کامل)
**پیش‌نیاز:** Test 7 (دستگاه آنلاین).
**گام‌ها (والد):**
1. Device Detail ⇒ ⋮ ⇒ «حذف دستگاه»
2. دیالوگ تأیید با **hold-to-confirm ۱.۵ ثانیه** ⇒ «حذف و پاک‌سازی داده‌ها»
**گام‌ها (فرزند):**
3. حداکثر ۹۰ ثانیه صبر کنید (یا اپ فرزند را باز کنید)
**نتیجه مورد انتظار (والد):**
- دستگاه از Dashboard و DeviceList **فوری** ناپدید شود
- پیام «دستگاه سارا حذف شد» + Undo به مدت ۵ ثانیه (اختیاری)
**نتیجه مورد انتظار (فرزند):**
- صفحه «اتصال شما توسط والد قطع شد» (با دلیل و زمان)
- نوتیف FGS حذف شود و **سرویس متوقف شود**
- اپ دیگر **هیچ درخواستی** به سرور نفرستد (جز یک `heartbeat` نهایی که `410 DEVICE_REMOVED` می‌گیرد)
- اگر دوباره وارد شود ⇒ صفحه Pairing (نه Status)
**معیار PASS (مهم‌ترین بخش Test 8):**
```sql
-- ۱) credential باطل
SELECT revoked_at, revoked_reason FROM device_credentials WHERE device_id='<DEVICE_ID>';      -- NOT NULL
-- ۲) sessionها باطل
SELECT count(*) FROM device_sessions WHERE device_id='<DEVICE_ID>' AND revoked_at IS NULL;   -- 0
-- ۳) داده‌های تله‌متری purge شده
SELECT count(*) FROM telemetry_events WHERE device_id='<DEVICE_ID>';                         -- 0
SELECT count(*) FROM permissions      WHERE device_id='<DEVICE_ID>';                         -- 0
-- ۴) وضعیت دستگاه
SELECT status, removed_at, removed_by_user_id FROM devices WHERE id='<DEVICE_ID>';           -- removed
-- ۵) audit
SELECT category, action, result FROM audit_logs
WHERE target_id='<DEVICE_ID>' ORDER BY id DESC LIMIT 5;   -- DEVICE/device.removed + PRIVACY/data.purged
```
**تست نفوذ بعد از حذف (الزامی):**
```bash
# با device_secret قبلی یک heartbeat جعلی بفرستید ⇒ باید 401/410 بگیرد و audit بنویسد
curl -i -X POST http://<host>:8080/v1/child/heartbeat \
  -H "X-Device-Id: <DEVICE_ID>" -H "X-Timestamp: $(date +%s)" -H "X-Nonce: $(openssl rand -hex 8)" \
  -H "X-Key-Version: 1" -H "X-Signature: <HMAC>" -H "Content-Type: application/json" -d '{}'
# انتظار: HTTP 410 {"code":"DEVICE_REMOVED"}   و   audit_logs[SECURITY][device.request_after_removal]
```
**PASS نهایی:** هیچ داده‌ای از دستگاه حذف‌شده در Dashboard والد دیده نشود، و هیچ درخواست موفق (`2xx`) از آن دستگاه ثبت نشود.

---

## ۵. تست‌های غیرکارکردی Phase 1

| تست | روش | آستانه قبولی |
|---|---|---|
| مصرف باتری سرویس sync | ۲۴ ساعت استفاده عادی + `dumpsys batterystats` | < ۳٪ در روز |
| مصرف داده | `dumpsys netstats` برای ۲۴ ساعت | < ۵ MB در روز |
| Cold start | Android Studio Profiler / `adb shell am start -W` | < ۱.۵s |
| اندازه APK | `./gradlew :app:parent:assembleRelease` | parent < ۱۲ MB، child < ۱۶ MB |
| پاسخ API | k6/Gatling: ۱۰۰۰ دستگاه heartbeat هر ۶۰s | p95 < ۱۵۰ms، بدون خطا |
| حافظه نشتی | LeakCanary (debug) ۳۰ دقیقه | ۰ leak |
| Crash در قطع شبکه | Monkey test + airplane mode تصادفی | ۰ crash |
| RTL | اسکرین‌شات خودکار هر صفحه در `fa` و `en` | بدون برش/هم‌پوشانی |
| دسترسی‌پذیری | Accessibility Scanner | ۰ خطای critical |

---

## ۶. CI Pipeline (GitHub Actions)

```
on: push | pull_request
├── android-ci
│   ├── ktlint + detekt
│   ├── :app:parent:assembleDebug  :app:child:assembleDebug
│   ├── unit tests (JVM) + Kover coverage gate
│   ├── Robolectric UI tests
│   ├── lint (fatal warnings)
│   └── module-graph-assert (قواعد وابستگی ماژول‌ها)
├── server-ci
│   ├── ktlint + detekt
│   ├── test-host + Testcontainers (Postgres 17, Redis 7)
│   ├── flyway validate
│   ├── coverage gate
│   └── trivy image scan
├── security-ci
│   ├── dependency-check / Dependabot
│   ├── gitleaks (هیچ secret در repo)
│   └── log-redaction test (رشته‌های ممنوع در تست‌ها)
└── artifacts: آپلود APKهای debug برای تست سریع روی گوشی
```

---

## ۷. گزارش تست (قالبی که بعد از هر Phase پر می‌کنم)

```
Phase N Test Report
| # | Test | Status | Evidence (SQL/logcat/screenshot) | Note |
|---|------|--------|----------------------------------|------|
| 1 | …    | PASS/FAIL | … | … |
Known issues: …
Blocked by: …
Next action: FIX / APPROVED
```
