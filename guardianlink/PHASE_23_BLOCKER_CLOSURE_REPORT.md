# گزارش بستن موانع Phase 23

**جمع‌بندی:** دو code blocker backend بسته شد و API E2E پس از اصلاحات PASS شد؛ suite/test/platform blockers بازند. وضعیت فاز `PARTIALLY_VALIDATED` است.

## بسته‌شده با شواهد
| ID | Root cause | تغییر محدود | شاهد بستن |
|---|---|---|---|
| CL-01 | Device session mapper ستون `user_id` را از جدول `device_sessions` می‌خواست | mapper مخصوص device که `device_id` می‌خواند | installDist موفق؛ status sync و کل API flow HTTP 200؛ دنبالهٔ readها PASS |
| CL-02 | `LocationRepo.upsertStatus` پارامتر bind جاافتاده/ترتیب ناسازگار داشت | SQL `last_report_ts` با condition و health column به bindها متصل شد | build موفق؛ location sync=200، current fix/geofence transition assertions PASS |
| CL-03 | OffsetDateTime response serialization | JavaTimeModule و timestamps خاموش | register/family=201 و created_at رشته |
| CL-04 | Flyway/runtime schema | تغییری لازم نبود | DB تازه: 9 migration applied؛ health live/ready=200 |
| CL-05 | Backup/restore | runbook/feature تغییر نکرد | custom dump/restore test clone؛ 43 tables، v9، 186 rows برابر |

## باز و blockerها
| ID | وضعیت | دلیل دقیق | راه بستن |
|---|---|---|---|
| OPEN-01 | BLOCKED | Android SDK/cmdline-tools و adb/emulator غایب؛ AGP9.4.1 requires Gradle>=9.6؛ Gradle9.7.1 task build time out/disappeared | نصب ابزار SDK 36، runner پایدار، wrapper pinned، هر دو app assemble/lint/test |
| OPEN-02 | BLOCKED_BY_ENVIRONMENT | Testcontainers suite به Docker نیاز دارد؛ 141 tests, 122 fail DockerClientProvider | نصب Docker Engine/daemon و rerun test |
| OPEN-03 | NOT VERIFIED | body 1MiB probe به 401 رسید، حد 1MiB/413 اثبات نشد | limit صریح در proxy/Ktor و آزمون معتبر مرزی |
| OPEN-04 | FOLLOW-UP | بعضی 401/429 پاسخ‌ها در completion log به‌صورت 500 | logger بعد از StatusPages final status بگیرد؛ test correlation |
| OPEN-05 | API ABSENT | family export 404 و delete 405 | پیش از پیاده‌سازی product/legal/privacy review؛ هیچ حذف نشود تا طرح مصوب |
| OPEN-06 | BLOCKED | browser automation/Android runtime/push dispatch موجود نبود | Playwright و device/provider staging |
| OPEN-07 | NOT TESTED | production deployment/rollout/rollback/monitoring/compliance | فقط بعد از همه release gateها و approvalهای واقعی |

## نتیجهٔ closure
`REAL_E2E_VALIDATED` را نمی‌توان انتخاب کرد چون API synthetic flow فقط بخشی از سیستم را پوشش می‌دهد و Android/production/runtime gates اجرا نشده‌اند. هیچ blocker محیطی را به‌عنوان PASS معرفی نمی‌کنیم. Production deploy عمداً انجام نشد.
