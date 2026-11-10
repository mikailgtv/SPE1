# ماتریس Build/Runtime — Phase 27

**تاریخ:** ۸ اکتبر ۲۰۲۶؛ تمام اجراها local/synthetic.

| مؤلفه | Build/آزمون اجراشده | Evidence | وضعیت |
|---|---|---|---|
| Backend | JDK 17, Gradle 9.7.1؛ `test` هدفمند و `installDist` | build موفق؛ ۲۰/۲۰ unit هدفمند پاس؛ full suite: ۲۰ pass، ۱۲۲ Testcontainers startup failure به‌علت نبود Docker | `CLOSED` برای build/unit/runtime؛ `BLOCKED` full suite |
| PostgreSQL | PostgreSQL 17.11، DB disposable | ۹ migration روی DB خالی؛ ۴۳ جدول؛ health ready 200؛ backup/restore محلی با row count برابر | `CLOSED` local |
| Parent Android | `:app-parent:assembleDebug` | SDK 36/build-tools و مجوز SDK فراهم/تأیید نشده | `BLOCKED` |
| Child Android | `:app-child:assembleDebug` | SDK 36/build-tools و مجوز SDK فراهم/تأیید نشده | `BLOCKED` |
| Parent Web | `npm run build`, `npm test`, Chromium WebDriver login | build موفق؛ ۴/۴ Web test؛ dashboard دادهٔ API واقعی local را نشان داد | `CLOSED` local |
| Notifications | SOS→notification projection، detail/read و family authorization | هر دو family اعلان داخلی `EMERGENCY`؛ cross-family 404 | `CLOSED` backend projection؛ push `NOT_TESTED` |
| Location | current/history GET و zone config | endpointها پاسخ 200؛ history خالی؛ zone config synthetic | `BLOCKED` real measurement/transition |
| SOS | `POST /device/sos`, parent active/ack/resolve | دو session synthetic، بدون location؛ internal notification دیده شد | `CLOSED` API lifecycle؛ Android/real emergency `NOT_TESTED` |
| Export/Delete | export/delete route probe و device removal | family export 404، family/account delete 405؛ soft device removal 204 | `FAILED` family/account capability |
| Android device/emulator | ADB/emulator | دستگاه متصل نبود؛ emulator/KVM موجود نبود | `BLOCKED` |
| Production/Cloud | deploy, TLS, FCM, S3/KMS, operational DR | در این محیط اجرا نشد | `NOT_TESTED` |

## تفسیر

`CLOSED` فقط به همان دامنهٔ local و شواهد جدول اشاره دارد. جدول ماتریس مجوز deployment نیست. Final release status در `PHASE_27_FINAL_REPORT.md` برابر `FAILED` است؛ blockerها در گزارش closure تفکیک شده‌اند.
