# Phase 22 — گزارش Version Freeze

**وضعیت:** snapshot source ثبت و freeze اطلاعاتی شد؛ این `Release Candidate` تأییدشده نیست و version labelها release نشده‌اند. Freeze از `2026-10-05T21:14:13Z` اعمال شد؛ پس از آن هیچ کد/config محصول تغییر نکرده است. فقط مستندات Phase22 و فایل شواهد محلی ایجاد/به‌روزرسانی شدند و در bundle نرم‌افزار release دخیل نیستند.

## نسخه‌های مشاهده‌شده در source

| فیلد | مقدار ثبت‌شده | تفسیر |
|---|---|---|
| Application Version | `0.1.0` source metadata | مقدار مشترک رسمی/RC تصویب‌شده وجود ندارد |
| Android Version Name | `0.1.0` default (Parent و Child) | از property قابل override؛ build واقعی انجام نشد |
| Android Version Code | `1` default (Parent و Child) | release build guard نیازمند code > 1؛ برای release کافی/قبول نیست |
| Web Version | `0.1.0` از `web/package.json` | نسخهٔ package؛ نه deployed release |
| Backend Version | `0.1.0` از `server/build.gradle.kts` | نسخهٔ Gradle project؛ package/container ساخته نشد |
| Database Migration Version | آخرین فایل source `V9__sos_emergency.sql` | نسخهٔ اعمال‌شده در DB `UNKNOWN` چون هیچ DB query وجود ندارد |
| Git Commit | `UNAVAILABLE — `.git` directory موجود نیست` | no commit hash, branch, tag, remote یا clean-tree status اثبات‌پذیر نیست |
| Web Build Timestamp | 2026-10-05 21:12–21:13 UTC | اجرای محلی `npm ci`, tests, Vite build؛ build window از console است |
| Build Environment | Node v20.20.2; npm 10.8.2; Linux sandbox; Java 11 برای سایر components | نه CI، نه release runner، نه Android/Backend build |
| Freeze Timestamp | `2026-10-05T21:14:13Z` | بعد از Web build و fingerprint؛ به وقت کاربر تاریخ 2026-10-06 است |

## Source fingerprint برای audit (جایگزین Git commit نیست)

Fingerprint deterministic از 255 فایل در `.github`, `server`, `android`, `web`, `deploy`؛ مسیرها و hash فایل‌ها ترکیب شدند؛ cache/build/node_modules/dist directories مستثنی‌اند:

`SHA-256: 679433a0ccd993e716bf429bb134d8007d70da882cf6b94ad80e2ab0ea01980c`

این fingerprint provenance گیت‌مانند یا signature نیست؛ برای تشخیص تغییر snapshot محلی است. Web archive hash در `PHASE_22_BUILD_PROVENANCE_REPORT.md` آمده است.

## Freeze rules

از این لحظه هر تغییر در source/build/config مستلزم version/build timestamp تازه، fingerprint تازه، rebuild، retest و بازبینی gateها است. مستندات release user-facing فقط این را می‌گویند که **هیچ release انجام نشده است**؛ نام‌ها و مقادیر بالا ادعای انتشار نیستند.
