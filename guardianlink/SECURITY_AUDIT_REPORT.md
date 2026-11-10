# گزارش ممیزی امنیت — GuardianLink

**وضعیت ممیزی: ناقص/غیرنهایی؛ این گزارش فقط یافته‌های قابل مشاهده در کد workspace و اصلاحات همین نوبت را ثبت می‌کند.** اجرای build، تست، آزمون نفوذ، بررسی dependency database، migration یا دستگاه واقعی انجام نشده است. نبود یافتهٔ بیشتر به معنی نبود آسیب‌پذیری نیست.

## خلاصهٔ ریسک

**ریسک کلی: HIGH — برای production تأیید نمی‌شود.** یافتهٔ قطعی CRITICAL در این بررسی محدود تأیید نشد، اما چند blocker سطح HIGH و چند بخش بررسی‌نشده وجود دارد. اصلاحات همین نوبت هنوز compile/test نشده‌اند.

## یافته‌ها

### HIGH — H-01: جداسازی خانواده فقط در منطق برنامه است؛ RLS وجود ندارد
- **شواهد:** در migrationهای بررسی‌شده `ENABLE ROW LEVEL SECURITY` یا `CREATE POLICY` پیدا نشد. بسیاری از queryها family/device را در SQL محدود می‌کنند که مفید است، اما دفاع مستقل دیتابیس ندارند.
- **اثر:** خطای آتی در یک route/repository می‌تواند دادهٔ خانوادهٔ دیگر را در معرض IDOR قرار دهد؛ backup/DB role نیز محدودهٔ خانواده را نمی‌شناسد.
- **رفع لازم:** طراحی connection context امن برای family scope و RLS واقعی با نقش runtime فاقد `BYPASSRLS`؛ تست دو خانواده برای تمام routeها و queryها. تا آن زمان RLS را ادعا نکنید.

### HIGH — H-02: نقش DB runtime با نقش migration یکی است
- **شواهد:** `docker-compose.yml` همان `DB_USER`/`DB_PASS` را به PostgreSQL و backend می‌دهد و `Main.module()`، `db.migrate()` را در startup اجرا می‌کند.
- **اثر:** compromise اپ می‌تواند به DDL و داده‌های کل DB دسترسی داشته باشد؛ اصل حداقل دسترسی رعایت نشده.
- **رفع لازم:** migration job/role جداگانه؛ runtime role فقط حداقل DML، بدون مالکیت schema/DDL، پس از آزمون upgrade/rollback.

### HIGH — H-03: کلید خصوصی JWT به‌صورت JWK روی volume نگهداری می‌شود
- **شواهد:** `Tokens.kt` کلید Ed25519 را از `DATA_DIR/ed25519.jwk` می‌خواند یا می‌سازد؛ رمزگذاری KMS، rotation/key id عملیاتی یا کنترل permission فایل در کد دیده نشد.
- **اثر:** دسترسی خواندن volume می‌تواند امکان جعل access token را بدهد؛ حذف volume نیز همه tokenها را نامعتبر می‌کند.
- **رفع لازم:** کلید از secret manager/KMS یا mount فقط‌خواندنی با permission مالک محدود، rotation و backup/recovery کنترل‌شده؛ هرگز در image یا Git نباشد. Compose volume به‌تنهایی KMS محسوب نمی‌شود.

### HIGH — H-04: Audit trail جامع و actor-aware وجود ندارد
- **شواهد:** در migration و کد سرور `audit_logs`/سرویس audit مرکزی پیدا نشد. `device_events` telemetry/notification stream است؛ `sos_events` فقط برای SOS است. برخی ruleها `created_by` دارند اما برای همهٔ تغییرها log append-only یکپارچه دیده نشد.
- **اثر:** انتساب امن LOGIN/LOGOUT، rule changes، location-setting changes، pairing/removal و دسترسی Parent به داده‌ها کامل و یکپارچه نیست.
- **رفع لازم:** جدول/سرویس append-only با actor، family/device، action، result، timestamp، request/correlation ID؛ تولید سمت سرور؛ دسترسی client-write ممنوع؛ retention و tamper-evidence تعریف شود.

### HIGH — H-05: retention/delete کامل و قابل تنظیم نیست
- **شواهد مشاهده‌شده:** Location retention پیش‌فرض ۳۰ روز و قابل تنظیم (۰ = آخرین نقطه؛ ۱–۳۶۵ روز) دارد و purge تنبل در sync/read؛ Geofence event retention از config با پیش‌فرض ۳۰ روز استفاده می‌کند؛ Web Safety event/override cleanup در service مقدار ۹۰ روز را hard-code کرده؛ SOS session/location/notes purge دوره‌ای ندارد؛ Usage, `device_events`, Notifications و backup retention در این audit end-to-end تأیید نشد؛ جدول سراسری Audit Log پیدا نشد.
- **اثر:** موقعیت/SOS و telemetry می‌توانند بیش از هدف محصول نگهداری شوند؛ حذف حساب/دستگاه، cache و backup ممکن است سازگار نباشد.
- **رفع لازم:** policy قانونی/محصولی per-data-class، تنظیمات خارج از کد، worker قابل مشاهده، delete/export audit و تست حذف در DB، cache و backup lifecycle. بازهٔ نهایی باید پس از تعیین بازار/سن کاربر و بازبینی حقوقی تصویب شود.

### HIGH — H-06: داده‌های مکانی/مصرف/SOS در DB به‌صورت field-encrypted دیده نشدند
- **شواهد:** در Server Kotlin و migrationهای بررسی‌شده AES/column encryption پیاده نشده است؛ مختصات در `locations` و `sos_location_points`، usage، event metadata و یادداشت‌ها ستون‌های معمول DB هستند. مستندات معماری قدیمی از family DEK/AES حرف می‌زنند، اما با کد واقعی مشاهده‌شده تطبیق ندارند.
- **اثر:** dump/backup/دسترسی read به PostgreSQL می‌تواند مختصات دقیق، مصرف و SOS را آشکار کند. disk/cloud encryption محیط واقعی نیز تأیید نشده.
- **رفع لازم:** تصمیم threat model: field encryption با KMS/per-family DEK و rotation یا کنترل‌های مستند disk/backup encryption + حداقل‌دسترسی/RLS؛ تست key loss/rotation/restore و عملکرد query.

### HIGH — H-07: replay protection برای درخواست‌های authenticated عمومی وجود ندارد
- **شواهد:** در کد بررسی‌شده X-Signature/HMAC/nonce request دیده نشد. JWT bearer کوتاه‌عمر و session lookup/revocation دارد؛ refresh یک‌بارمصرف است، ولی access request در طول TTL قابل replay است. Pairing/SOS idempotency بهتر محافظت شده‌اند.
- **اثر:** سرقت access token تا انقضا امکان replay می‌دهد؛ تغییرات حساس همه nonce-based نیستند.
- **رفع لازم:** threat model دقیق، HTTPS، idempotency برای mutationها، token binding یا nonce/timestamp برای عملیات حساس؛ آزمون replay. ادعای HMAC در اسناد قدیمی با کد مشاهده‌شده منطبق نیست.

### HIGH — H-08: session listing/revoke و refresh reuse detection ناقص است
- **شواهد:** `POST /auth/logout` با refresh token فعلی آن session را revoke می‌کند؛ endpoint مشاهده/لغو نشست‌های دیگر Parent پیدا نشد. schema sessionها `last_seen` ندارد و `user_sessions` به device_id متصل نیست. Refresh token rotation انجام می‌شود، ولی استفادهٔ مجدد از token revoke‌شده باعث revoke زنجیره/هشدار امنیتی نمی‌شود.
- **اثر:** کاربر نمی‌تواند نشست‌های فعال را ببیند یا همهٔ نشست‌ها را مدیریت کند؛ نشت refresh ممکن است به هشدار/مهار سریع منجر نشود.
- **رفع لازم:** session inventory با `last_seen`, device metadata، revoke یک/همه، lineage/reuse detection و audit.

### HIGH — H-09: Reverse proxy/بدنهٔ درخواست/timeout production ناقص است
- **شواهد/تغییرات:** Ktor برای max request body و timeout سراسری HTTP هنوز setting قابل اثبات ندارد. Caddy headers و عدم انتشار مستقیم backend از Phase 10 باقی‌اند؛ این نوبت `/health/live`, `/health/ready`, graceful shutdown config و bounded Hikari timeouts/pool را اضافه کردم. هیچ‌کدام runtime-validated نیستند.
- **اثر:** payload بزرگ/slow request می‌تواند منابع را مصرف کند؛ deployment اشتباه ممکن است listener بدون TLS را expose کند.
- **رفع لازم:** body limits per endpoint، server/proxy request timeouts، rate limit توزیع‌شده، آزمون edge TLS و اطمینان از عدم انتشار backend. CORS در حال حاضر web UI ندارد؛ در صورت افزودن browser client باید allowlist صریح باشد.

### HIGH — H-10: محدودکنندهٔ نرخ فقط process-local است
- **شواهد:** `RateLimiter` در حافظهٔ همین process است. در این نوبت map را bounded و bucketهای منقضی را prune کردم و برای mutationهای app/web/location/geofence و برخی readها سقف‌های per-device/user افزودم؛ این جایگزین limiter مشترک نیست و edits آزمون نشده‌اند.
- **اثر:** چند replica محدودیت را تقسیم می‌کنند؛ restart همهٔ شمارنده‌ها را reset می‌کند. IP نیز به تنظیم trusted proxy وابسته است.
- **رفع لازم:** Redis/shared limiter با key و TTL مناسب؛ Caddy اکنون X-Forwarded-For را بر اساس remote peer بازنویسی می‌کند، ولی deployment واقعی باید validate شود.

### HIGH — H-11: workflow Pairing قبلاً race داشت؛ اصلاح جدید هنوز آزمون نشده
- **شواهد:** کد قبلی خواندن status و mark used را جدا از تراکنش انجام می‌داد. در این نوبت exchange با row lock/شرط status=`active` و confirm با `FOR UPDATE` و تغییر شرطی `status='exchanging'` بازنویسی شد.
- **اثر:** نسخهٔ قبلی امکان concurrent confirmation را داشت؛ اصلاح پیشنهادی باید از دو confirm هم‌زمان فقط یکی را بپذیرد.
- **وضعیت:** **اصلاح در کد، تأییدنشده**؛ تست concurrency و PostgreSQL واقعی اجرا نشده است. deployment قبل از آزمون نباید آن را fix قطعی تلقی کند.

### MEDIUM — M-01: Pairing hash در برابر dump آفلاین کد entropy محدود دارد
- **شواهد:** code کاربرپسند هشت‌کاراکتری Crockford (حدود ۴۰ بیت طبق طراحی) با SHA-256 در DB ذخیره می‌شود؛ salt/pepper سروری برای code hash دیده نشد. TTL کوتاه و rate-limit آنلاین کمک می‌کنند، ولی dump DB امکان حدس آفلاین را می‌دهد.
- **رفع لازم:** HMAC با pepper جدا از DB یا entropy/طول بیشتر با تجربهٔ UX امن؛ rotation و تست one-time/expired هم حفظ شود.

### MEDIUM — M-02: بهبود password policy و timing تازه است و باید آزموده شود
- **شواهد:** hash از Argon2id به تنظیم ۳ iteration/۶۴ MiB/parallelism ۱ تغییر کرد؛ ثبت‌نام بازهٔ ۱۲–۱۲۸ کاراکتر، login برای کاربر ناموجود dummy verification، و پاک‌سازی موقت `CharArray` افزوده شد. Test credentialهای server و validator/پیام‌های محلی Android نیز به حد جدید تغییر کردند؛ build/test آن‌ها اجرا نشده است.
- **ریسک باز:** ۶۴ MiB هم‌زمانی/CPU را روی production اندازه‌گیری نکرده‌ایم؛ lockout حسابی، reset/OTP و email verification وجود ندارند/تأیید نشده‌اند. `AuthTest` حتماً باید اجرا شود.

### MEDIUM — M-03: اصلاح ذخیرهٔ صف موقعیت محلی تازه است
- **شواهد:** `LocationQueue` قبلاً مختصات را در PlainPrefs/SharedPreferences می‌نوشت. این نوبت آن را به `SecureStore` مبتنی بر Android Keystore/AES-GCM منتقل کردم و مهاجرت یک‌بارهٔ دادهٔ legacy را اضافه کردم.
- **ریسک باز:** compile/device/corruption migration test اجرا نشده؛ حذف DataStore تضمین پاک‌شدن فیزیکی flash نیست.

### MEDIUM — M-04: Release build قبلاً endpoint نمونه و signing آماده نداشت
- **شواهد:** `.example` در Base URL release، و `isMinifyEnabled=false` وجود داشت. این نوبت host نمونه را حذف کردم و taskهای assemble/bundle/package Release را مشروط به HTTPS واقعی و متغیرهای keystore خارج از مخزن کردم.
- **ریسک باز:** Gradle script parse/build نشده، R8/minification خاموش است، ProGuard keep rules و artifact امضاشده/تأییدشده نداریم.

### MEDIUM — M-05: OSM tile provider در سایر نقشه‌ها
- **شواهد:** `OsmTileMap.kt` درخواست tile به `tile.openstreetmap.org` می‌زند. viewport tileها می‌تواند محدودهٔ تقریبی نقشهٔ Parent را به provider/شبکه آشکار کند.
- **اصلاح همین فاز:** نقشهٔ SOS به نمای شماتیک محلی (`SosTrackMap.kt`) تغییر کرد تا مختصات SOS برای tile map خارجی درخواست نشود.
- **ریسک باز:** Location/Safe Zones mapهای دیگر هنوز tile خارجی دارند؛ disclosure/provider alternative/self-host باید در تصمیم حریم خصوصی production ثبت شود.

### MEDIUM — M-06: health، log levels و exception logging
- **شواهد/اصلاح:** `Main.kt` اکنون `/health/live` مستقل از dependency، `/health/ready` با DB/storage probe و alias قدیمی `/health` دارد؛ pool دیتابیس با timeoutهای محدود و هنگام stop بسته می‌شود. `logback.xml` پیش‌فرض `INFO` دارد، `LOG_LEVEL` قابل تنظیم است و CallLogging مسیر/query/body را ثبت نمی‌کند؛ Android `AppLog` به `BuildConfig.DEBUG` محدود شد تا release log ندهد (compile/device تأیید نشده).
- **وضعیت:** فقط کد/config؛ compile، health failure test، graceful-shutdown و log-capture اجرا نشده‌اند؛ metrics/alerting/request ID و log pipeline production هنوز موجود/تأیید نشده‌اند.

### LOW/INFO — I-01: Android permission footprint مشاهده‌شده
- `INTERNET`, `ACCESS_NETWORK_STATE`: شبکه/sync؛ نبودشان sync را متوقف می‌کند.
- `ACCESS_COARSE_LOCATION`, `ACCESS_FINE_LOCATION`: current/history و SOS last fix؛ ردشدنشان SOS را متوقف نمی‌کند، فقط مکان ناموجود/محدود می‌شود.
- `ACCESS_BACKGROUND_LOCATION`: اشتراک زمینه‌ای/ردیابی SOS طبق مجوز سیستم؛ ردشدن، background tracking را محدود می‌کند.
- `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_LOCATION`: FGS قابل مشاهده و فقط در ردیابی SOS زمان‌دار؛ نبود/محدودیت موجب عدم ردیابی می‌شود، نه عدم فعال‌سازی SOS.
- `PACKAGE_USAGE_STATS`: آمار مصرف با special access؛ ردشدن آن usage unavailable می‌کند.
- `QUERY_ALL_PACKAGES`: inventory اپ‌ها؛ حساس و نیازمند بررسی policy/declaration در Google Play؛ بدون آن inventory ممکن است ناقص باشد.
- Parent فقط `POST_NOTIFICATIONS`: ردشدن مجوز اعلان محلی را متوقف می‌کند؛ feed در اپ باقی می‌ماند.
- `BIND_DEVICE_ADMIN` فقط به‌عنوان permission محافظ Receiver سیستمی. در manifestهای بررسی‌شده CAMERA, RECORD_AUDIO, READ_SMS, READ_CALL_LOG, READ_CONTACTS, Accessibility Service, Notification Listener, SYSTEM_ALERT_WINDOW و VPN permission مشاهده نشد.

## کنترل‌های مثبت مشاهده‌شده

- Password plaintext در schema نیست؛ Argon2id hash استفاده می‌شود.
- Refresh token opaque است و hash آن در DB قرار می‌گیرد؛ access JWT EdDSA پیش‌فرض ۱۵ دقیقه دارد و `sid` در هر درخواست با session زنده بررسی می‌شود.
- Device secret به‌شکل hash ذخیره می‌شود؛ device sessions و user sessions FK دارند.
- Query helper با PreparedStatement پارامترها را bind می‌کند؛ در مسیرهای بررسی‌شده query tenant معمولاً family/device scoped است.
- Pairing code هشت‌کاراکتری Crockford/TTL کوتاه و hash در DB دارد؛ patch همین فاز مصرف را serialize می‌کند.
- Android credentials و SOS queue از SecureStore مبتنی بر Keystore استفاده می‌کنند؛ LocationQueue نیز در همین نوبت به آن منتقل شد.
- manifestهای اصلی Android XML هستند؛ برای Child اعلان‌ها عمداً مجوز درخواست نشده؛ Parent مجوز را صریح بررسی می‌کند.
- شناسه‌های full در logهای بررسی‌شدهٔ device/family/user/session کوتاه شدند و نام محدودهٔ امن از log حذف شد؛ **log capture واقعی انجام نشده است**.
- خط قرمزهای hidden monitoring رعایت شده: feature مخفی SMS، پیام‌رسان، camera/microphone recording، keylogging، Accessibility abuse یا exploit اضافه نشده است.

## تست‌های امنیتی لازم قبل از پذیرش

- PostgreSQL concurrency: دو `pairing/confirm` هم‌زمان با یک exchange token؛ دو refresh موازی؛ refresh reuse.
- IDOR matrix برای تمام family/device APIs با Family A/B، parent/session revoked، device removed و user بدون family.
- Rate limit از چند replica، spoofed XFF، key-space flood، password reset/OTP (در صورت افزودن).
- Replay access token و idempotency برای event/rule/location/SOS.
- SQL injection/fuzz در همهٔ route params, search, package/domain, time/date.
- Android Keystore locked/corrupt/backup restore، مهاجرت صف plaintext، offline/process death، permission revoke، FGS restrictions.
- Log capture با token/password/location/SOS strings؛ اثبات عدم ثبت دادهٔ حساس.
- Backup/restore و delete-after-restore test.
- Dependency vulnerability scan روی lockfile/SBOM واقعی؛ این دور اجرا نشده است.

## نتیجه

**Security sign-off صادر نمی‌شود.** موارد H-01 تا H-11 یا باید برطرف/آزموده شوند یا با پذیرش ریسک مستند، مالک و موعد داشته باشند. یافتهٔ «بدون vulnerability» یا PASS گزارش نمی‌شود.
