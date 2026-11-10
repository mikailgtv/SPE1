# گزارش Build و Test — Phase 28

**وضعیت محلی:** `PASS` برای build/testهای اجراشده.  
**وضعیت انتشار:** **NO-GO**؛ build موفق به معنی release readiness نیست.

## Backend

- Runtime: Temurin JDK `17.0.20.1`, Gradle Wrapper `9.7.1`, PostgreSQL `17.11`.
- اجرای نهایی از `server/` با DB disposable `guardianlink_phase28_test`، متغیرهای `GL_TEST_DB_*` و اجرای sequential:

```bash
bash ./gradlew --no-daemon --max-workers=1 \
  -Pkotlin.compiler.execution.strategy=in-process \
  -Dorg.gradle.jvmargs='-Xms64m -Xmx768m -XX:MaxMetaspaceSize=256m -XX:ActiveProcessorCount=1' \
  test
```

- نتیجهٔ نهایی: **۱۵۴ test، ۰ failure، ۰ error، ۰ skipped**؛ Gradle `BUILD SUCCESSFUL`. فایل‌های XML در `server/build/test-results/test/` پس از اجرا با parser جمع‌شماری شدند.
- `installDist` با همان JDK/JVM محدودیت‌ها `BUILD SUCCESSFUL`؛ distribution نیز در HTTP/Netty runtime smoke استفاده شد.
- suite شامل Ktor API integration tests متصل به PostgreSQL واقعی، unit tests و regression tests است؛ این suite browser/Android/staging نیست. چون `GL_TEST_DB_*` تنظیم بود، `IntegrationBase` Testcontainers را راه نینداخت.

## Web

- Node `20.20.2`, npm `10.8.2`; در `web/`:

```bash
npm ci --no-audit --no-fund
npm test
npm run build
```

- نتیجه: `npm ci` موفق؛ **۲ test files و ۴/۴ test پاس**؛ `tsc -b && vite build` موفق، production assets در `web/dist` تولید شدند.
- این‌ها Web unit tests/build هستند؛ browser E2E، UI API flow، TLS/deployment یا UAT اجرا نشد.

## Database/Migration/Runtime

- نسخهٔ schema جدید اضافه نشد. Netty distribution روی DB جدا و تازه بالا آمد؛ Flyway ۹ migration را validate/apply کرد؛ HTTP smoke lifecycle را اجرا کرد و SQL پس از account deletion `users=0`, `families=0` تأیید کرد.
- test و runtime database/role و env/credential/JDK موقت پس از پایان آزمون حذف شدند. هیچ production database لمس نشد.

## کارهای اجرا‌نشده

- Android Gradle/SDK/device/emulator، browser automation، deploy، staging/prod, TLS/proxy، backup/restore تازه، cloud/KMS/DR و legal review اجرا نشدند. برای آن‌ها build/test سبز ادعا نمی‌شود.
