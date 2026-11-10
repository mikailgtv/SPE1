# راهنمای Rollback و Recovery

**وضعیت:** روش تعریف‌شده/آزموده‌نشده؛ ECS، RDS، deploy pipeline و staging drill وجود ندارند. Rollback فعلی قابل اجرا یا تضمین‌شده نیست.

## مسئولیت و trigger

- **Trigger:** readiness/ALB unhealthy، critical smoke failure، sustained server 5xx/latency، broken auth/family authorization، data corruption یا migration error.
- **Owner:** Release Manager/On-call و DB operator باید پیش از هر deploy با نام مشخص شوند؛ فعلاً این اشخاص تعیین نشده‌اند.
- **Decision:** هر failure امنیتی، داده‌ای یا auth، rollout را فوراً pause می‌کند؛ restore/rollback فقط پس از incident lead و data owner approval.

## Backend

در ECS، task definition revision قبلی را با image digest immutable دوباره مستقر کنید و target health/`/health/ready` را کنترل کنید. در حال حاضر workflow فقط GHCR image push می‌کند؛ ECS update/rollback automated نیست.

## Database/migrations

- Flyway migrationهای فعلی در startup اجرا می‌شوند و backward/forward compatibility آزموده نشده؛ migration عموماً down migration ندارد.
- **Rollback application image به‌تنهایی schema را عقب نمی‌برد.** از expand/contract migration، compatibility window و pre/post checks استفاده کنید.
- اگر migration داده را خراب/از دسترس خارج کرد، destructive SQL را تکرار نکنید؛ DB clone/restore را در staging ابتدا validate کنید.
- Production point-in-time restore می‌تواند writeهای بعد از snapshot/WAL point را حذف کند. DBA, incident lead و privacy owner باید scope/RPO/communication را ارزیابی کنند؛ فقط با backup معتبر و تغییر مستند.
- هرگز `restore-postgres.sh` را برای production اجرا نکنید؛ script صریحاً production را رد می‌کند.

## Android direct APK

- APK قبلی را به‌عنوان downgrade نصب نکنید و `versionCode` را پایین نبرید.
- hotfix را با **همان production signing key**، SemVer patch جدید و `versionCode` بزرگ‌تر بسازید؛ از CI protected environment عبور دهید، امضا/checksum را verify و کانال رسمی دانلود را به‌روز کنید.
- keystore گم‌شده/لو‌رفته یک incident بحرانی است؛ signer rotation به app update strategy و Android signing design نیاز دارد. هیچ راهی برای جعل/دورزدن signature مجاز نیست.
- force-update mechanism در اپ ثبت/تأیید نشده؛ rollback Android ممکن است فوری/خودکار نباشد.

## ارتباط و پایان incident

ثبت timestamp، affected versions, data integrity, actions, approver, recovery evidence، کاربرانی که باید مطلع شوند و post-incident items؛ هرگز secret/token/مکان/SOS payload در report ننویسید.

## تمرین لازم پیش از Production

در staging: release→rollback image، non-destructive/expand-contract migration، backup→isolated restore، smoke test، signed APK hotfix با versionCode افزایشی و ارتباط release channel را حداقل یک‌بار تمرین کنید. نتیجه در `PHASE_12_FINAL_RELEASE_REPORT.md` ثبت شود. در حال حاضر هیچ‌کدام انجام نشده‌اند.
