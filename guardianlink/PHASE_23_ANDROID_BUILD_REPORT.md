# گزارش Android Build Phase 23

**وضعیت:** `BLOCKED`؛ هیچ Android APK/AAB موفقی در این فاز ساخته نشده است.

| ID | محیط/فرمان | زمان | انتظار | نتیجهٔ واقعی | وضعیت | Artifact |
|---|---|---|---|---|---|---|
| P23-AND-B-01 | JDK17 + Gradle 8.14.3؛ `:app-parent:assembleDebug :app-child:assembleDebug` | زمان مطلق در wrapper ثبت نشده؛ اجرا حدود 46s | assemble هر دو app | AGP 9.4.1 صریحاً حداقل Gradle 9.6.0 خواست؛ 8.14.3 رد شد | BLOCKED | CLI failure در session |
| P23-AND-B-02 | JDK17 + Gradle 9.7.1، آنلاین، همان دو assemble task | حدود 10m; daemon disappeared؛ timestamps wrapper در آن اجرا ذخیره نشد | assemble | daemon ناپدید شد؛ build result/AAR/APK تولید موفق اثبات نشد | BLOCKED/INCONCLUSIVE | log daemon در `~/.gradle/daemon/9.7.1`؛ خارج workspace |
| P23-AND-B-03 | Gradle 9.7.1 `--offline`, assemble دو app | 2026-10-06؛ timeout ابزار 180s؛ start/end wrapper ثبت نشده | assemble | timeout؛ stdout نهایی دریافت نشد | BLOCKED/INCONCLUSIVE | `PHASE_23_ENVIRONMENT_REPORT.md` |
| P23-AND-B-04 | SDK/tool discovery | 2026-10-06 07:51:01Z snapshot | SDK 36/build-tools و adb | `ANDROID_HOME`/`ANDROID_SDK_ROOT` unset؛ `adb`,`sdkmanager`,`emulator` غایب | BLOCKED | Environment report |

## پیکربندی پروژه
- `compileSdk = 36`, `targetSdk = 36`, `minSdk = 26` هر دو app.
- AGP 9.4.1; Kotlin plugin 2.4.20؛ Gradle 9.7.1 نصب است، اما wrapper project-wide موجود/قابل‌استفاده در مسیر بررسی‌شده نیست.
- Java 11 به‌صورت shell default؛ build commands با JDK 17 تنظیم شدند.

## Required remediation
Android SDK Command-line Tools، platform 36، build-tools، platform-tools نصب و licenses پذیرفته شوند؛ `ANDROID_HOME` تنظیم و Gradle Wrapper pin شود به نسخهٔ حداقل 9.6 (ترجیحاً نسخهٔ تاییدشدهٔ CI). سپس clean assemble برای `app-parent` و `app-child`, unit tests, lint, APK signature/provenance اجرا شود. تا آن زمان Android build **NOT PASS** و release gate باز است.
