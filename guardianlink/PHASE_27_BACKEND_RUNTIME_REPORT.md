# گزارش Runtime Backend — Phase 27

**وضعیت:** `CLOSED` برای runtime و API محلیِ اجراشده؛ `BLOCKED` برای full Testcontainers suite.

## اجرای واقعی

- Build با Temurin JDK 17 و Gradle Wrapper 9.7.1: `installDist` موفق.
- Backend روی `0.0.0.0:18080` با DB آزمایشی محلی اجرا شد؛ `/health/live` = HTTP 200 و `/health/ready` = HTTP 200.
- Startup از DB خالی ۹ migration را اعمال کرد و سرویس آمادهٔ درخواست شد.
- API-only fixtureها از registration/login، ساخت family، pairing session/exchange/confirm، device authentication و status sync عبور کردند.
- پس از اصلاح regression پایین، ۱۰۴ assertion از مسیرهای واقعی HTTP به Backend و PostgreSQL پاس شد؛ نتیجه شامل ۲ حساب/family و ۲ paired device مصنوعی است و Android واقعی در کار نبود.

## Regression کشف‌شده و اصلاح‌شده

نخستین POST ساخت Web rule با HTTP 500 برگشت. لاگ PostgreSQL علت را ثبت کرد: `web_filter_events_source_check` مقدار `parent` را برای ستون `source` نمی‌پذیرفت، در حالی که schema فقط `server` و `device` را مجاز می‌داند. در `WebSafetyService.insertEvent()` مبدأ parent به `server` ذخیره می‌شود؛ actor والد در مسیرهای audit/device-event باقی می‌ماند. تست واحد نگاشت منبع افزوده شد. پس از build مجدد و ساخت DB خالی، ساخت و خواندن Web rule برای هر دو family با موفقیت انجام شد.

## تست‌ها

- اجرای کامل Gradle: ۱۴۲ test کشف شد؛ ۲۰ تست بدون Docker پاس و ۱۲۲ تست به‌علت مقداردهی‌نشدن Testcontainers (`DockerClientProviderStrategy`/نبود Docker runtime) شکست خوردند. این ۱۲۲ مورد را موفق یا code-level regression اعلام نمی‌کنیم؛ full suite در این محیط `BLOCKED` است.
- اجرای هدفمند unit suite پس از اصلاح: ۲۰/۲۰ پاس (`AppManagementUnitTest`, `ObservabilityUnitTest`, `UnitTest`, `SosStateMachineTest`, `WebSafetyPolicyTest`).
- HTTP family-isolation و API-only flows در `PHASE_27_E2E_REPORT.md` مستند شده‌اند.

## خارج از دامنه

Production TLS/secret management، load/stress، cloud deployment، audit مستقل و اجرای Android child app در این تست انجام نشد. Health 200 به‌تنهایی مدرک production readiness نیست.
