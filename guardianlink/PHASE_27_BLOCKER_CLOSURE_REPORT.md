# گزارش بستن Blockerها — Phase 27

| شناسه | وضعیت | evidence/مانده | شرط ادامه یا بستن |
|---|---|---|---|
| `B27-WEB-SOURCE` | `CLOSED` | خطای 500 ناشی از constraint source کشف شد؛ mapping parent→server و unit test اضافه شد؛ build و API create/read روی DB تازه پاس شد | برای تغییرات بعدی همین migration/API regression حفظ شود |
| `B27-API-FAMILY` | `CLOSED` (synthetic local) | دو family؛ ۲۶ cross-family denial؛ feed و notifications family-scoped | تست‌های multi-tenant در CI/Testcontainers تکرار شوند |
| `B27-WEB-UI` | `CLOSED` (local) | Chromium headless login/dashboard؛ داده از API محلی | staging/browser matrix و UAT جدا لازم است |
| `B27-DB-RESTORE` | `CLOSED` (manual local) | custom dump به DB خالی، ۴۳ جدول، row counts مساوی، Flyway v9 | cloud/encryption/PITR/RPO/RTO جداگانه لازم است |
| `B27-EXPORT-DELETE` | `FAILED` | family export 404، family delete 405، account delete 405؛ family/account erasure موجود نیست | طراحی مالک‌تأییدشده، threat/data/cascade/retention/backup review، سپس disposable DB tests |
| `B27-ANDROID-SDK` | `BLOCKED` | SDK 36/build-tools و license acceptance فراهم نیست | SDK رسمی و مجوز بررسی/قبول‌شده توسط اپراتور؛ build parent/child |
| `B27-ANDROID-DEVICE` | `BLOCKED` | ADB فهرست خالی؛ emulator و `/dev/kvm` غایب | device/emulator مجاز، build/install و permission tests |
| `B27-DOCKER-IT` | `BLOCKED` | full Gradle suite: ۱۲۲ تست در Testcontainers bootstrap شکست؛ Docker runtime/socket ندارد | Docker/Testcontainers در محیط مجاز؛ اجرای کامل بدون تغییر ACL این sandbox |
| `B27-FCM` | `NOT_TESTED` | provider/test project و credential پیکربندی نیست | FCM test tenant و device مجاز؛ delivery/permission/retry evidence redact‌شده |
| `B27-LOCATION-GEOFENCE` | `BLOCKED` | GET خالی/config ساختگی فقط؛ real fix و transition نداریم | Android device، permission/consent روشن و fix واقعی؛ synthetic coords را به real نسبت ندهید |
| `B27-SOS-DEVICE` | `BLOCKED` | backend API lifecycle پاس؛ Android trigger و emergency واقعی اجرا نشد | device test plan امن و مجاز؛ هیچ تماس حقیقی با اورژانس در آزمون نباشد |
| `B27-GIT-PROVENANCE` | `BLOCKED` | `.git`/remote/history در snapshot نیست | canonical clone/commit و `git status/diff`/history review |
| `B27-PRODUCTION` | `NOT_TESTED` | deployment/TLS/secret rotation/monitoring production اجرا نشد | staging/production-like environment و تمام release gates پاس |
| `B27-LEGAL` | `OPEN` | jurisdiction مشخص نیست | هر شرط حقوقی به‌صورت `JURISDICTION-SPECIFIC REQUIREMENT` از مشاور/owner بررسی شود |

## جمع‌بندی

Blockerهای محلی Web-source، family isolation و manual local restore با evidence تازه بسته شدند. Export/delete یک شکست قابلیت تأییدشده است؛ Android، Docker/Testcontainers، device location/SOS و provider به‌علت محیط/مجوز `BLOCKED` یا `NOT_TESTED` باقی‌اند. هیچ blockerی صرفاً براساس مستندات بسته نشده است.
