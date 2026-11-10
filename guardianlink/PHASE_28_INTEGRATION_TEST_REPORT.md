# گزارش Integration/API — Phase 28

**وضعیت شواهد محلی:** `PASS` در دامنهٔ PostgreSQL/TestApplication و یک smoke test Netty.  
**Release:** **NO-GO**؛ local integration به‌تنهایی release gate نیست.

## پروفایل و دستور

- Backend: Temurin JDK 17.0.20.1، Gradle 9.7.1، PostgreSQL 17.11. هیچ Docker/Testcontainers در دسترس/استفاده‌شده نبود؛ `IntegrationBase` از متغیرهای `GL_TEST_DB_URL`, `GL_TEST_DB_USER`, `GL_TEST_DB_PASS` به DB disposable واقعی وصل شد.
- فرمان نهایی از `server/`:

```bash
bash ./gradlew --no-daemon --max-workers=1 \
  -Pkotlin.compiler.execution.strategy=in-process \
  -Dorg.gradle.jvmargs='-Xms64m -Xmx768m -XX:MaxMetaspaceSize=256m -XX:ActiveProcessorCount=1' \
  test
```

- نتیجه: **۱۵۴/۱۵۴؛ صفر failure/error/skip**. تست‌ها sequential بودند. این‌ها API tests از طریق Ktor `testApplication` همراه PostgreSQL هستند؛ به‌تنهایی external network transport یا browser E2E نیستند.

## External HTTP/Netty smoke

- `installDist` distribution روی port محلی 18081 اجرا شد؛ DB تازهٔ `guardianlink_phase28_runtime` و `DATA_DIR` جدا و disposable.
- Flyway ۹ migration را validate و از schema خالی apply کرد؛ PostgreSQL log نسخه را 17.11 گزارش کرد.
- HTTP واقعی synthetic: `register=201`, family create=201, unauthenticated export=401, family JSON export=200/`record_count=2`, `format=csv` و query تزریقی `family_id`=400, family delete=204, account delete=204, `GET /v1/me` پس از delete=401.
- SQL پس از اجرا: `users=0`, `families=0`, `flyway_schema_history success=9`. Runtime process متوقف و DB/role موقت بعداً حذف شدند. هیچ credential/token/email/داده‌ای در این گزارش ثبت نشده.

## دامنهٔ Integration suite

- `PrivacyLifecycleTest`: family A/B isolation، export rows/credentials/rate/limit، shared-family guard، rollback، cascade خانواده/حساب، account بدون family، حذف deviceِ revoke-only، location scope/repeat/ownership/rows، destructive rate limit.
- سایر testها شامل auth/pairing، device/session و family authorization، web rule fix، request ingress size guard، location/geofence/usage/SOS lifecycle، migration-backed PostgreSQL behavior و security regressions بودند.
- runtime test فقط مسیرهای lifecycle/export مشخص‌شده را در Netty زد؛ تمام ۱۵۴ test را از طریق Netty اجرا نکرد.

## موارد اجرا‌نشده

- Browser/Chrome E2E و UI download flow، Web backend-to-browser integration، Android app/device، staging/production endpoint، TLS/reverse proxy، load/multi-replica و deployment.
- Testcontainers/Docker profile؛ restore/backup جدید، cloud/KMS/PITR و production schema.
- به‌علت نبود این محیط‌ها، این موارد `BLOCKED_BY_ENVIRONMENT`/`NOT_RUN` هستند، نه PASS.
