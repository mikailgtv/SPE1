# راه‌اندازی محیط محلی GuardianLink

**دامنه:** توسعه و آزمون محلی با دادهٔ غیرواقعی/محدود؛ این راهنما محیط staging یا production ایجاد نمی‌کند و مجوز بارگذاری دادهٔ واقعی نیست.

## اجرای Phase 27 در sandbox (۸ اکتبر ۲۰۲۶)

- Debian 13.7، x86_64؛ ۲ vCPU؛ RAM حدود ۱٫۹ GiB؛ فضای آزاد workspace حدود ۱۹ GiB.
- Temurin JDK 17 موقت در `/tmp/phase27-tools/jdk17`؛ Gradle Wrapper 9.7.1؛ Node 20.20.2/npm 10.8.2.
- PostgreSQL 17.11، `psql`, `pg_dump`, `pg_restore`; DB `guardianlink_phase27` disposable. Backend startup از schema خالی، ۹ migration را اجرا کرد.
- ADB 34.0.5 موجود، اما SDK platform/build-tools 36، `sdkmanager`, emulator و `/dev/kvm` فراهم نبودند. پذیرش Android SDK license انجام نشد.
- Docker/Testcontainers قابل استفاده نبود؛ socket/daemon فراهم نبود. دسترسی Docker تغییر داده نشد.
- Backend، Vite و Chromium headless به‌طور موقت اجرا شدند؛ credentialهای API، DB و JWK فقط خارج از repository و با دسترسی محدود بودند. پایان تست DB/role/credential/JWK/dump موقت حذف شد.
- Web `npm run build` و چهار test پاس شدند. Backend `installDist` و ۲۰ unit test هدفمند پاس شدند. API-only runtime خانواده/notification/SOS و Chromium login اجرا شدند؛ هیچ Android واقعی استفاده نشد.

وضعیت Phase 27 و مرز شواهد در فایل‌های `PHASE_27_*_REPORT.md` است. snapshot فاقد `.git` است؛ branch/commit/diff این workspace قابل بررسی نیست.

## اجرای راستی‌آزمایی Phase 28 (۹ اکتبر ۲۰۲۶)

- Backend: Temurin JDK 17.0.20.1، Gradle 9.7.1 و PostgreSQL 17.11 محلی. Full suite با `GL_TEST_DB_URL`, `GL_TEST_DB_USER`, `GL_TEST_DB_PASS` به DB جدا و disposable وصل شد؛ هر سه مقدار فقط در فایل env خارج از repository بودند. `IntegrationBase` از Ktor `testApplication` و PostgreSQL واقعی استفاده می‌کند؛ به‌علت نبودن Docker/Testcontainers در این محیط، این اجرا را Testcontainers یا staging نمی‌نامیم.
- اجرای نهایی پس از اصلاح test expiry و افزودن حد تراکنش export: **154/154 تست، صفر شکست/خطا/skip**. از `server/` با `JAVA_HOME` روی JDK 17 و همان JVM limits پایین اجرا شد:

```bash
bash ./gradlew --no-daemon --max-workers=1 \\
  -Pkotlin.compiler.execution.strategy=in-process \\
  -Dorg.gradle.jvmargs='-Xms64m -Xmx768m -XX:MaxMetaspaceSize=256m -XX:ActiveProcessorCount=1' \\
  test
```

- `installDist` نیز با همان JVM تنظیمات موفق شد. Process بسته‌بندی‌شده جداگانه روی PostgreSQL disposable تازه اجرا شد: Flyway نه migration را validate و apply کرد؛ smoke test از HTTP/Netty واقعی synthetic registration/family، export، invalid format، family delete، account delete، revoke session و DB count را بررسی کرد. بعد از حذف account، count ردیف‌های `users` صفر بود. این فقط smoke test API محلی است.
- Web با Node 20.20.2/npm 10.8.2: `npm ci --no-audit --no-fund`, `npm test`, و `npm run build` اجرا شدند؛ ۴ test پاس و production bundle ساخته شد.
- همهٔ fixtureها synthetic بودند. Browser UI/browser automation، Android SDK/device، staging/production، deployment، backup/restore جدید و legal review در این اجرا پوشش داده نشدند. نتیجهٔ تفصیلی و شمارش دقیق testها در گزارش‌های Phase 28 است.

## پیش‌نیازهای قابل تکرار

- JDK 17 برای Kotlin backend؛ PostgreSQL محلی/اختصاصی برای تست؛ Node/npm برای Web.
- Android: SDK platform 36، Build Tools 36.0.0 و platform-tools. اپراتور باید شرایط Android SDK را شخصاً بررسی و قبول کند؛ خودکار نپذیرید. برای emulator به hypervisor/KVM یا محیط پشتیبانی‌شده نیاز است.
- Docker Engine/socket فقط برای Testcontainers/Compose integration suite در محیطی که استفاده از Docker مجاز است.
- Production/Staging credential، account، location و data هرگز به محیط local منتقل نشوند.

## پیکربندی امن

`.env.example` فقط template است؛ آن را به `.env` محلی ignored کپی کنید و password یکتای local/test را خارج از terminal history وارد کنید. `.env`، token، private JWK، keystore، FCM credential یا backup را commit/گزارش نکنید. `DB_PASS` در template خالی است. نسخهٔ فعلی Backend مقادیر `JWT_CONFIG`, `FCM_CONFIG`, `STORAGE_CONFIG`, `ENCRYPTION_CONFIG` را به‌عنوان config فعال نمی‌خواند؛ وجود کلید نام‌گذاری‌شده در env به معنی پیاده‌سازی نیست.

متغیرهای Backend: `DB_URL`, `DB_USER`, `DB_PASS`, `DATA_DIR`, `APP_ENV`, `LOG_LEVEL`, `PORT`. برای Web، `API_PROXY_TARGET` فقط در سرور dev proxy استفاده می‌شود؛ `VITE_API_BASE_URL` را برای same-origin خالی نگه دارید.

## PostgreSQL disposable

1. PostgreSQL local را اجرا کنید.
2. role و database جداگانه با password تصادفی و local-only بسازید؛ هیچ role production را reuse نکنید.
3. `DB_URL=jdbc:postgresql://127.0.0.1:5432/guardianlink_local` و role/database متناظر را فقط در `.env` بگذارید.
4. Flyway در startup migrationها را اعمال/validate می‌کند. برای تغییر schema از DB disposable، backup و rollback جدا استفاده کنید.
5. migration startup و role واحد برای Production مناسب فرض نشود؛ جداسازی migrator/runtime مجزا نیازمند review است.

## Backend

```bash
cd server
set -a
. ../.env
set +a
export JAVA_HOME=/path/to/jdk-17
./gradlew --no-daemon --max-workers=1 \
  -Pkotlin.compiler.execution.strategy=in-process \
  -Dorg.gradle.jvmargs='-Xms64m -Xmx768m -XX:MaxMetaspaceSize=256m -XX:ActiveProcessorCount=1' \
  -PskipIT test
./gradlew --no-daemon --max-workers=1 \
  -Pkotlin.compiler.execution.strategy=in-process \
  -Dorg.gradle.jvmargs='-Xms64m -Xmx768m -XX:MaxMetaspaceSize=256m -XX:ActiveProcessorCount=1' \
  installDist
```

این محدودسازی حافظه برای sandbox کم‌منبع است. در اجرای Phase 27 همین no-IT command، ۲۰ unit test پاس شد. `-PskipIT` فقط integration-tagged tests را حذف می‌کند؛ آن suite را جایگزین نمی‌کند.

`-PskipIT` integration/Testcontainers را حذف می‌کند و جایگزین آن نیست. برای full integration suite، Docker Engine/socket مجاز لازم است. پس از `installDist`، distribution را فقط با env امن local اجرا کنید. Health:

```bash
curl -i http://127.0.0.1:8080/health/live
curl -i http://127.0.0.1:8080/health/ready
```

Health 200 فقط process/dependency check همان محیط را نشان می‌دهد؛ authorization، isolation، UAT یا production readiness را ثابت نمی‌کند.

## Web

```bash
cd web
npm ci
API_PROXY_TARGET=http://127.0.0.1:8080 npm run dev -- --host 0.0.0.0
npm test
npm run build
```

Vite proxy سرور-به-سرور است؛ browser از same-origin استفاده کند و به `localhost` دیگری فراخوانی نزند. Build موفق به‌تنهایی UI/API E2E، TLS، deployment یا UAT نیست.

## Android

پس از SDK رسمی و licenseهایی که اپراتور شخصاً بررسی/قبول کرده است:

```bash
cd android
./gradlew :app-parent:assembleDebug :app-child:assembleDebug
adb devices -l
```

Debug APK را release ننامید. build/install، permission، policy و behavior دستگاه را جداگانه آزمایش کنید. Normal Mode را Managed Device enforcement جا نزنید.

## Backup/restore و پاک‌سازی

Manual test محلی Phase 27 با `pg_dump -Fc` به DB خالی جدا restore شد؛ ۴۳ جدول و row countها مقایسه شدند. این فقط آزمون disposable است، نه backup خودکار/Cloud/PITR یا RPO/RTO اندازه‌گیری‌شده. `deploy/scripts/backup-postgres.sh` به ابزار و دسترسی‌های Cloud/KMS نیاز دارد.

فقط DB/role صریحاً disposable خود را حذف کنید؛ پیش از `DROP DATABASE` نام را دوباره تأیید کنید. از Production data و موقعیت واقعی در test استفاده نکنید. `.env`, private JWK, signing key, archive و log حساس را خارج از repository نگه دارید.
