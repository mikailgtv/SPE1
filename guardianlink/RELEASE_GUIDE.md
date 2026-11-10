# Android/Backend Release Guide

## Channel انتخاب‌شده

کاربر توزیع مستقیم APK را انتخاب کرده است. Google Play یا enterprise/MDM هنوز انتخاب نشده؛ بنابراین store listing/Play Console release را ادعا نمی‌کنیم. دانلود مستقیم مستلزم HTTPS، منبع رسمی و قابل‌اعتماد، signature verification، update channel امن، پشتیبانی و disclosure روشن است.

## Channelها

| Channel | Backend | Android | وضعیت |
|---|---|---|---|
| Development | local/test configuration؛ secrets ساختگی | `debug`، emulator `http://10.0.2.2:8080` | Compose dev config موجود؛ runtime/build اجرا نشده. |
| Internal testing | staging backend/resources | signed staging APK با staging key | GitHub workflow دستی اضافه شده؛ محیط/keys نداریم. |
| Staging | staging VPC/RDS/hostname مستقل | `guardianReleaseBaseUrl=https://...` + staging signing | فقط artifact build workflow؛ staging backend actual وجود ندارد. |
| Production | production infra و data store مستقل | signed production APK و `versionCode` صعودی | workflow protected artifact gate دارد؛ deploy/distribution actual وجود ندارد. |

Android در حال حاضر product flavors جدا ندارد و `applicationId` در همهٔ کانال‌ها یکسان است؛ staging و production را هم‌زمان روی یک گوشی نصب نکنید. Signing keys جدا بین staging/production به‌روزرسانی مستقیم را ناممکن می‌کند؛ برای انتقال بین channelها uninstall/پاک‌شدن local data ممکن است رخ دهد، پس فقط دستگاه QA استفاده شود.

## Build/release sequence

1. PR: CI tests/lint/debug build و review؛ CI status checks را GitHub branch protection required کنید.
2. Staging: manual `staging-release.yml`, `versionName` SemVer، `versionCode` جدید، environment-scoped API URL/secrets. Quality workflow باید کامل پاس شود.
3. Smoke/E2E/permission/offline tests روی دو دستگاه واقعی؛ backup/restore staging test و rollback rehearsal.
4. Production: tag `vX.Y.Z`; workflow به `production` protected environment می‌رسد. Required reviewers + approval sentinel لازم‌اند. Workflow امضای release APK و image versioned در GHCR را می‌سازد؛ ECS deploy یا public distribution خودشکار نیست.
5. Reviewer/operator checksum/signature را verify، release notes و change log را نهایی، سپس فایل را از کانال HTTPS کنترل‌شده منتشر کند.
6. Version Code باید از آخرین code منتشرشده بزرگ‌تر باشد؛ خروجی workflow از run number + 1 برای production محاسبه می‌کند، ولی پیش از هر اجرا دستی مقایسه کنید.

## Android release configuration فعلی

- Parent/Child: `ir.guardianlink.parent` / `ir.guardianlink.child`; `minSdk 26`, `targetSdk 36`, `compileSdk 36`.
- version defaults `0.1.0`/code 1؛ Release build اکنون override SemVer و code بالاتر از 1 می‌خواهد.
- Release `debuggable=false`; Base URL باید HTTPS واقعی باشد؛ signing keystore/password/alias از environment می‌آیند.
- R8/minification خاموش است؛ keep rules/serialization shrink validation blocker است.
- Debug manifests network config و emulator URL از development است؛ Release endpoint باید واقعاً inspect شود؛ no APK/AAB release package build شده.
- `QUERY_ALL_PACKAGES`, Usage Stats, Location/background location و Device Admin نیازمند user disclosure و توزیع/policy review هستند؛ sideload بودن به‌معنی حذف قانون/سیاست یا رضایت نیست.

## Direct APK integrity

Workflowهای staging/production پس از build با `apksigner` fingerprint گواهی را با `ANDROID_SIGNING_CERT_SHA256` environment variable مقایسه و SHA-256 فایل را ثبت می‌کنند؛ این verification تعریف شده ولی اجرا نشده.

پس از تولید artifact:

```bash
apksigner verify --verbose --print-certs parent.apk
apksigner verify --verbose --print-certs child.apk
sha256sum parent.apk child.apk
```

APK certificate fingerprint باید با release keystore record‌شده تطبیق داده شود. SHA-256 را از کانال ثانویهٔ قابل اعتماد منتشر کنید. APK debug را هرگز به‌عنوان release ارسال نکنید. فایل signed/staging را بدون اطلاع کاربر یا دورزدن نصب رسمی نصب نکنید.

## Version/Release notes

هر release `CHANGELOG.md` و tag را با version/date، features/bug/security fixes، known limitations، migration requirements، server image digest، Android code/name، checksum و approver ثبت کند. `versionCode` هر app به‌صورت monotonically increasing باشد.

## بررسی فعلی توزیع Android (۳۰ سپتامبر ۲۰۲۶)

طبق FAQ رسمی Android Developer Verification که ۱۸ ژوئن ۲۰۲۶ به‌روزرسانی شده، enforcement اولیهٔ ۳۰ سپتامبر برای فروشگاه‌های مشارکت‌کننده در برزیل، اندونزی، سنگاپور و تایلند است؛ FAQ می‌گوید نصب مستقیم sideload و فروشگاه‌های خارج از فهرست در این rollout اولیه مشمول enforcement نیستند. آلمان در فهرست اولیه نیست. Android توصیه می‌کند appها را برای rolloutهای بعدی ثبت کنند؛ زمان‌بندی آینده ممکن است تغییر کند. پیش از توزیع واقعی، دوباره وضعیت منطقه/کانال را در منابع رسمی بررسی و packageهای Parent/Child را ثبت/تأیید کنید.

منبع: [Android Developer Verification FAQ](https://developer.android.com/developer-verification/guides/faq) و [Android Developer Verification: Building a safer ecosystem together](https://developer.android.com/blog/posts/android-developer-verification-building-a-safer-ecosystem-together). این بررسی، Google Play policy review، حقوقی/حریم خصوصی یا انتشار تأییدشده نیست.

## App Distribution blockers

- هیچ Play/Enterprise/verified download host یا support contact انتخاب نشده.
- Screenshot, support web, privacy notice, permission/data disclosure و APK update service موجود/تأییدشده نیست.
- Security scan، R8, signing verification و real device test اجرا نشده‌اند.
- Distribution عمومی فعلاً **ممنوع/NOT READY**.
