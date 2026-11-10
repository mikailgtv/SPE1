# گزارش Phase 18 — Android crash و background reliability

## Audit
- Parent و Child Android از `AppLog` به Logcat استفاده می‌کنند، اما فقط وقتی `BuildConfig.DEBUG` فعال است؛ Release معمولاً این diagnostics را تولید نمی‌کند.
- Crash SDK، ANR detection، persistent fatal-exception handler، startup failure telemetry یا remote Android error reporting در dependency/source فعلی پیدا نشد.
- Child sync و app-policy با WorkManager هستند؛ sync نیاز به network دارد، retry را به WorkManager می‌دهد و حداقل periodic interval برابر ۱۵ دقیقه است. location و SOS queueهای رمز‌شدهٔ محلی دارند؛ retry queue state به اپراتور ارسال نمی‌شود.
- SOS location از foreground service قابل‌مشاهده، user-started و time-bounded استفاده می‌کند. Sync دائمی FGS یا راه‌حل دورزدن Doze/OEM محدودیت وجود ندارد.
- Parent daily notification summary یک WorkManager محلی است؛ خطای worker به retry می‌رود ولی attempt/latency aggregate server-side گزارش نمی‌شود.

## تغییر Phase18
- token/email/pairing code redaction در Android logger helper به `<redacted>` تغییر کرد. هیچ payload/stack trace/keystroke/location به Backend برای crash reporting ارسال نشد.
- عمداً crash SDK یا hidden exception uploader اضافه نشد: نیازمند privacy review، افشای کاربر/قانونی، consent، data region، retention و SDK security review است.
- هیچ battery optimization bypass، root exploit، مخفی‌سازی سرویس یا Android policy bypass اضافه نشد.

## گیت‌های مسدود
- **BLOCKED:** Parent/Child assemble، unit/instrumentation tests، crash injection، ANR، start/background/foreground، Doze، battery saver، standby bucket، reboot، network handover و OEM-specific test؛ Android SDK/Gradle/adb و device موجود نیست.
- برای ادامه نیاز به JDK 17، Gradle 9.7.1 (یا wrapper سازگار commit‌شده)، Android SDK/platform/build-tools، دو device/emulator واقعی و consent/operator runbook است.
- تا تصویب privacy برای client crash data، تصمیم پیشنهادی: crash-free session و ANR از Google Play Console/enterprise platform در سطح app aggregate، با حداقل data و retention مصوب، نه با SDK خودساخته. در این فاز provision/فعال نشده.

**نتیجه:** Android background/crash status **BLOCKED**, نه PASS.