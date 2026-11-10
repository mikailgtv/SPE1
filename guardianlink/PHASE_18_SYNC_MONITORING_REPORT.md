# گزارش Phase 18 — Sync Monitoring

## Source flow
- Child `StatusSyncWorker` روی اتصال شبکه با WorkManager اجرا می‌شود؛ sync شامل status/snapshot، usage، location permission/provider، app inventory/events، Web Safety state/events است. location coordinates فقط بعد از پاسخ authenticated سرور با sharing روشن و از endpoint جدا ارسال می‌شوند.
- Worker در failure نتیجهٔ retry می‌دهد؛ `DEVICE_REMOVED`/session revoked را جداگانه handle و credential/queue policy موجود را اجرا می‌کند. SOS outbox در مسیر sync موفق flush می‌شود.
- صف‌ها در Android encrypted هستند؛ pending/sending/ack state مشترک و operational dashboard server-side وجود ندارد. Parent device health از زمان heartbeat/sync و thresholds خانواده به‌طور lazy هنگام API read محاسبه می‌شود؛ job جدا برای تغییر health وجود ندارد.

## Metrics موجودشده
- Backend request metrics، route bucket `/v1/device/sync` را بر حسب HTTP status class/count/latency می‌شمارد؛ SQL timing/errorها نیز aggregate است. 429ها جدا شمارش می‌شوند.
- هیچ ID دستگاه، queue content، usage record، app name یا مختصات در metric ذخیره نمی‌شود.

## پوشش ناقص
- Client worker attempt count/retry/backoff reason، pending/sending/ack state، queue size و last-success per-device در metrics ارسالی وجود ندارند. Last successful sync از قبل در DB/device UI ثبت است، اما Phase18 آن را به telemetry aggregate تبدیل نکرد.
- HTTP 200 برای sync تنها پاسخ route است، نه اثبات اینکه هر batch field توسط domain تماماً persisted/committed شده؛ sync در صورت partial service behavior باید جدا instrument شود.
- Delay/offline thresholds موجود default: 1800/3600 ثانیه، از config قابل‌تنظیم‌اند؛ براساس load/client OEM واقعی کالیبره نشده‌اند. WorkManager هر ۱۵ دقیقه یا بیشتر و best-effort است؛ 15 دقیقه را به‌عنوان زمان‌بندی قطعی/online بودن تعبیر نکنید.

## راستی‌آزمایی
- Test source route-template/redaction و metrics اضافه شده، ولی Backend/JUnit/DB tests **BLOCKED** به‌علت نبود Gradle/JDK17/Docker.
- قطع و وصل شبکه، Doze، reboot، queue drain، retry و last-sync delay **BLOCKED** به‌علت نبود Android device/Backend.
- هیچ queue یا sync دادهٔ ساختگی/E2E mock ساخته یا اجرا نشد.
