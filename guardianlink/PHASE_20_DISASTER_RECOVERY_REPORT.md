# گزارش Disaster Recovery — فاز ۲۰

**وضعیت کلی:** `BLOCKED BY ENVIRONMENT`; هیچ DR drill واقعی انجام نشد. RPO و RTO هر دو `NOT DEFINED`.

## تحلیل سناریوها (طرح، نه نتایج عملیاتی)

| سناریو | تشخیص | اثر | بازیابی/rollback | Data loss | RTO/RPO | owner | Verification |
|---|---|---|---|---|---|---|---|
| 1. Backend server failure | health/readiness probe و edge error؛ alert واقعی provision نشده | API unavailable؛ clients ممکن است sync را عقب بیندازند | restart/replace deployment، rollback image/config؛ ensure no migration mismatch | writes بعد از آخرین DB commit? DB persists; queued client events depend on device queues | `NOT DEFINED` | SRE/backend on-call (تعیین‌نشده) | `/health/ready`, synthetic auth/family scoped APIs, error/queue review |
| 2. Database failure | readiness/JDBC pool errors; external alert absent | API read/write fail؛ all DB-backed capabilities down | restore DB service/failover if provisioned; not configured; replay only verified sources | unknown; no PITR/WAL proof | `NOT DEFINED` | DB/SRE owner تعیین‌نشده | schema, FK, synthetic auth/access/data integrity |
| 3. Database corruption | DB integrity checks/restore checks; not configured | corrupt records/index/schema | stop writes; preserve evidence; restore to isolated new DB and validate, then controlled cutover/rollback | since verified backup; unknown | `NOT DEFINED` | DBA/Security owner تعیین‌نشده | constraints/counts/domain invariants/API tests |
| 4. Backup corruption | checksum/decrypt/restore check; automated verify absent | backup unusable, recovery window lengthens | use prior independent verified backup; re-backup after source health restored | until previous good backup; unknown | `NOT DEFINED` | Backup operator تعیین‌نشده | checksum + decrypt + full restore drill |
| 5. Storage failure | volume/S3 capacity/health alerts absent | DB volume, JWK or backup unavailable | recover separate storage/replica; DB volume restore and private key recovery coordinated | depends on replicated snapshot; unknown | `NOT DEFINED` | Infra/SRE تعیین‌نشده | volume integrity, DB starts, signing key verify, backup retrieval |
| 6. Secret/key rotation failure | auth/signature failures/secret-manager alert absent | users/devices may be logged out; backup decrypt may fail | staged dual-key/overlap plan, rollback only if old key uncompromised; compromised key must not be reinstated | sessions/key-bound artifacts possibly unavailable | `NOT DEFINED` | Security owner تعیین‌نشده | token verify/refresh, backup decrypt, revoke-compromise tests |
| 7. Migration failure | Flyway startup failure/log; migration alert absent | new backend may fail or schema partially apply; Flyway DDL not globally rollbackable by assumption | stop rollout; forward-fix or restore pre-change snapshot; do not manually rewrite history | writes during rollout unknown | `NOT DEFINED` | Backend/DBA | migrate fresh copy + previous-version compatibility and API tests |
| 8. Bad deployment | smoke tests/health/error alerts; release workflows staged | API/Web/Android regression | rollback to prior known artifact; DB migrations may not be backward compatible | post-deploy writes may conflict with rollback | `NOT DEFINED` | Release/SRE | version health, auth, family boundary and sync synthetic tests |
| 9. Notification service failure | notification path/API errors; delivery telemetry not established | Parent may not receive app notifications; DB center may remain but external delivery unknown | restore sender/provider config; no third-party notification delivery implementation evidence in runtime | missed/pending notifications unknown | `NOT DEFINED` | Notifications owner تعیین‌نشده | test sink, provider status, one synthetic event; never real SOS |
| 10. Regional/cloud service failure | provider status/SLO/region alarms not provisioned | API/DB/object-store may be unavailable together | cross-region failover/restore plan not provisioned; isolate data residency and keys | unknown, especially last replica/backup | `NOT DEFINED` | Cloud/SRE owner تعیین‌نشده | cross-region recovery rehearsal and data integrity checks |

## DR drill

`NOT TESTED` / `BLOCKED BY ENVIRONMENT`: no non-production account, PostgreSQL, backup artifact, S3/KMS, credentials or isolated region target. No destructive action was taken. `PHASE_20_RESTORE_TEST_REPORT.md` includes the exact blocker and future drill sequence.

## Priorities before claiming DR readiness

1. Name primary/backup/incident owners and escalation channel.
2. Decide service criticality and approved RPO/RTO from business impact/cost; currently `NOT DEFINED`, not guessed.
3. Provision separate DB and off-host encrypted backup storage, lifecycle, monitor and alert; recover `DATA_DIR` private signing key.
4. Use a sandbox with synthetic-only data; repeat full restore + app/auth/family-boundary tests and publish measured RTO/effective RPO.
5. Test migration rollback/forward-fix, key rotation and notification provider outage independently.
