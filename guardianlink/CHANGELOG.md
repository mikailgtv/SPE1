# Changelog

این changelog تا زمان انتشار نسخهٔ واقعی کامل محسوب نمی‌شود. تاریخ‌ها مطابق تاریخ گزارش و تغییرات workspace هستند؛ هیچ release عمومی انجام نشده.

## Unreleased — Phase 12 preparation (2026-09-30)

### Added
- GitHub Actions CI/reusable quality/release candidate workflows، CodeQL, Trivy image scan, dependency review و Dependabot configuration؛ هنوز روی GitHub اجرا نشده‌اند.
- Backup/restore scripts با encrypted age stream و طرح S3/KMS؛ نه AWS bucket provision شده و نه backup/restore test اجرا شده.
- Persian deployment, operations, CI/CD, monitoring, environment, release, rollback, backup/restore and security operations documentation.
- `/health/live` و `/health/ready`; `/health` readiness alias حفظ شد؛ unit/integration tests اضافه شد ولی اجرا نشده.

### Changed
- Hikari connection pool sizes/timeouts از env/config مصرف می‌شوند؛ defaultهای اولیه نیازمند load testing هستند.
- `APP_ENV=staging/production`، `LOG_LEVEL=DEBUG` را رد می‌کند و `DB_URL` را به `sslmode=verify-full` ملزم می‌کند.
- CallLogging فقط method/status ثبت می‌کند؛ Logback پیش‌فرض INFO است؛ DB pool در ApplicationStopped بسته می‌شود.
- Android version name/code از Gradle property override می‌شوند؛ Release به SemVer، versionCode بالاتر از 1، HTTPS endpoint و release secrets نیاز دارد.
- `.gitignore` برای environment/secrets/build/runtime backup افزوده شد.

### Security / Limitations
- هیچ production deployment، backup, restore, release build, artifact signing verification، Android device test، vulnerability scan یا E2E انجام نشد.
- Global audit log, RLS, separate DB migration role, server-side field encryption, distributed rate limits, crash metrics and complete retention/delete remains open.
- Release workflows image/A PK artifact upload می‌کنند؛ AWS ECS deploy و public APK distribution انجام نمی‌دهند.

## 0.1.0 (تاریخ انتشار نامعلوم؛ فقط default در source)

- Version label در Gradle source؛ release artifact/date/signature/known limitations تاریخی تأیید نشده‌اند. این heading صرفاً وضعیت source baseline است و انتشار 0.1.0 را اثبات نمی‌کند.
