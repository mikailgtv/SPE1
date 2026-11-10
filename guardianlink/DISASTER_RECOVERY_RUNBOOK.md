# Runbook بازیابی از بحران — پیشنهادی، تمرین‌نشده

**وضعیت:** هیچ DR drill واقعی انجام نشده؛ این runbook برای tabletop/non-production preparation است، نه authorization برای production action.

## پیش از incident

- تعیین incident commander، Backend, DB, Infra, Security, Privacy/Safety owner و on-call.
- `RPO` و `RTO` را پس از impact/cost analysis تصویب کنید؛ تا آن زمان هر دو `NOT DEFINED`.
- تأیید وجود encrypted off-host backup، backup age alert، restore test cadence، DB snapshots/WAL plan و JWK key recovery. در وضعیت فعلی این موارد اثبات نشده‌اند.
- هرگز از child/family real data برای drill استفاده نکنید؛ SOS/notification provider باید sandbox sink باشد.

## تشخیص اولیه و containment

1. ثبت زمان UTC، alert source، scope و version؛ log/metrics را export کنید بدون token/email/coordinates/body.
2. تفکیک API failure، DB failure، storage, KMS/secret, migration و notification-provider.
3. اگر compromise محتمل است، incident commander با Security owner access/credentials را محدود کند؛ secrets را در ticket/chat نگذارید.
4. Freeze rollout/writes فقط با تصمیم owner و safety impact review؛ SOS/location outage را با وضعیت `LIMITED/UNAVAILABLE` شفاف نگه دارید، وعدهٔ تحویل ندهید.
5. Preserve evidence؛ backup/artifact را overwrite/delete نکنید.

## Backend/deployment rollback

- مقایسهٔ deploy version و readiness; rollback image/config به آخرین version شناخته‌شده فقط اگر migration سازگار است.
- DB migration را کورکورانه downgrade نکنید؛ migrationهای SQL ممکن است transactional rollback تضمین نکنند.
- Post rollback، health endpoints و synthetic auth/family isolation/device access/sync را بررسی کنید.
- RTO و دادهٔ بعد deployment تا آخرین backup: `NOT DEFINED` تا measurement.

## Database restore

از `BACKUP_AND_RESTORE_RUNBOOK.md` پیروی کنید. Restore ابتدا به target تازه و isolated؛ schema/FK/row integrity/auth/family isolation و data classes verify شود. Cutover تنها پس از approver و impact/consent review. Restore proof ندارد تا API/application test هم موفق شود. Restore source/current DB را overwrite نکند.

## Secret/key failure

- Identify scope: DB password, signing JWK, age identity, KMS/IAM, Android release signing.
- Compromised secret را revoke/rotate؛ exposed signing/private key را به حالت قبلی برنگردانید؛ طراحی overlap/rotation policy باید پیشاپیش آماده باشد.
- Verify new secret via synthetic auth, signed token, decrypt/restore where applicable.
- Lost JWK recovery: JWT verification may fail; sessions/login impact را ارزیابی کنید. JWK is outside DB backup.

## Notification provider outage

هیچ اطلاع‌رسانی emergency service را ادعا نکنید. بررسی کنید که app notification provider در deployment واقعاً پیکربندی/فعال است؛ فقط test sink/synthetic event در staging. برای Child/Parent status `NOTIFIED/DELIVERED` فقط با receipt واقعی.

## پایان incident

ثبت actual impact، measured start/end، data loss boundary، rollback/restore artifact and tests؛ scrub sensitive identifiers؛ issue برای remediation/retention/privacy review. RPO/RTO فقط پس از محاسبه بر اساس آخرین backup معتبر و زمان کل recovery قابل گزارش است.
