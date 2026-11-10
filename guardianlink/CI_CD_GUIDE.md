# CI/CD، Build Automation و approval gate

## پلتفرم انتخاب‌شده

**GitHub Actions** پیشنهاد شده است. پروژه فعلی Git repository/remote ندارد؛ بنابراین workflow YAMLها هنوز به remote متصل یا اجرا نشده‌اند. قبل از اتکا به آنها، فایل‌ها را در مخزن Git واقعی قرار دهید، branch/tag protection و GitHub Environments را فعال کنید و workflowها را از روی PR واقعی اجرا کنید.

## فایل‌های اضافه‌شده

- `.github/workflows/ci.yml`: PR و push به `main/master`؛ backend tests با PostgreSQL/Testcontainers، Android local unit tests، lint، debug build و artifact کوتاه‌عمر.
- `.github/workflows/dependency-review.yml`: PR dependency review با fail روی High severity.
- `.github/workflows/codeql.yml`: Java/Kotlin static analysis به‌صورت دستی روی source build؛ هنوز run نشده.
- CI image stage از Trivy برای CVEهای HIGH/CRITICAL سیستم‌عامل/library استفاده می‌کند؛ vulnerability DB scan در این workspace اجرا نشده.
- `.github/dependabot.yml`: پیشنهاد update هفتگی Gradle و GitHub Actions.
- `.github/workflows/quality-reusable.yml`: reusable gates قبل از release.
- `.github/workflows/staging-release.yml`: manual dispatch، backend/Android quality+Trivy و reusable CodeQL، staging APKهای signed/fingerprint-verified و image `staging-<sha>` در GHCR؛ این **deploy به ECS نیست**.
- `.github/workflows/production-release.yml`: stable tag `vX.Y.Z`، quality+Trivy+CodeQL gates، انتظار `production` environment، signed APK/fingerprint و versioned backend image در GHCR؛ این **production rollout/distribution نیست**.

## Release secrets/variables

در GitHub Environmentهای مستقل `staging` و `production` تعریف کنید. هر دو نیازمند variable `API_BASE_URL=https://...` هستند. Secretهای زیر را جداگانه در هر environment بسازید؛ نام‌ها می‌توانند یکسان باشند ولی مقدار staging و production نباید یکی باشد:

- `ANDROID_SIGNING_KEYSTORE_B64`
- `ANDROID_SIGNING_CERT_SHA256` (environment variable، fingerprint گواهی SHA-256 مورد اعتماد برای مقایسه با APK)
- `ANDROID_SIGNING_STORE_PASSWORD`
- `ANDROID_SIGNING_KEY_ALIAS`
- `ANDROID_SIGNING_KEY_PASSWORD`

برای `production` نیز secret زیر workflow را fail-closed می‌کند: `PRODUCTION_RELEASE_APPROVED=APPROVED_BY_PROTECTED_ENVIRONMENT`. علاوه بر آن، در GitHub Environment، **required reviewers** و deployment branches/tags را از تنظیمات repository فعال کنید. Secret sentinel به‌تنهایی جایگزین reviewer approval نیست.

Keystore را در GitHub Environment encrypted secrets یا HSM/secure signing service نگه دارید، دسترسی را محدود، recovery را کنترل و کلید را دوره‌ای بازبینی کنید. Secret را در repository-level variable، artifact یا log قرار ندهید.

## رفتار نسخه

- `versionName` از SemVer می‌آید؛ tag تولیدی با `v` شروع می‌شود و داخل APK prefix حذف می‌شود.
- `versionCode` در workflow tag بر مبنای workflow run number + 1 است؛ در staging operator مقدار monotonic می‌دهد. پیش از release آن را با آخرین نسخهٔ منتشرشده تطبیق دهید؛ workflow به‌تنهایی ترتیب معنایی SemVer را اثبات نمی‌کند.
- هر دو app فعلاً `applicationId` ثابت دارند؛ staging/prod را هم‌زمان روی یک دستگاه نصب نمی‌توان کرد و امضای staging متفاوت، update مستقیم به production را نمی‌دهد.

## Gateهای فعلی و شکاف‌ها

- Gradle 9.7.1 از Dockerfile server انتخاب شده؛ Java 17 و Android API 36/build-tools 36 workflow provision می‌شوند.
- Server `gradle -p server test` را اجرا می‌کند؛ integration tests به Docker/Testcontainers روی runner نیاز دارند.
- Android local tests و lint/build debug در CI نوشته شده‌اند. `androidTest` فعلاً صفر test دارد و device farm/instrumentation اجرا نمی‌شود؛ این gate برای انتشار واقعی باز است.
- Dependency review/Dependabot، CodeQL و Trivy image scan تعریف شده‌اند، اما Gradle lockfiles، SBOM، OSV/OWASP source dependency scan و gitleaks/secret scan هنوز تنظیم نشده‌اند؛ هیچ scanی در این workspace اجرا نشده.
- GitHub Actions با major tags به‌جای commit SHA ارجاع شده‌اند؛ قبل از production use، اقدام‌ها را pin و update policy تعیین کنید.
- workflowهای release public rollout، S3 backup, ECS task deploy، production smoke و rollback اجرا نمی‌کنند. این‌ها نیازمند cloud account, roles, infrastructure-as-code, approver و تست جداگانه‌اند.
- `production` environment اگر در GitHub با reviewers ساخته/محافظت نشود، YAML به‌تنهایی approval انسانی را تضمین نمی‌کند.

## توقف در خطا

هر `needs` ناموفق، workflow release را متوقف می‌کند. هیچ step برای نادیده‌گرفتن تست fail یا deploy دستی پس از failure اضافه نشده است. برای merge protection در repository settings، CI status checks را required کنید؛ این تنظیم از فایل workflow به‌تنهایی فعال نمی‌شود.
