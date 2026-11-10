# Rollback Rehearsal Report — Phase 13

**Status: `BLOCKED` — هیچ rollback rehearsal انجام نشده است.**

| Drill | Expected | Actual | Evidence / blocker |
|---|---|---|---|
| Backend A→B deployment | B healthy; task digest and health recorded | no ECS/staging deploy | AWS/ECS account, image run and service absent |
| Inject known failure | alarm detects and release stops | not executed | no monitoring/alert pipeline |
| Rollback B→A | previous immutable digest returns healthy | not executed | no released A/B image or ECS target |
| DB forward migration compatibility | app B reads/writes new schema; old version safe | not executed | no PostgreSQL staging or migration runtime |
| DB destructive/restore recovery | isolate clone, approved data recovery, integrity check | not executed | no backup/restore proof; destructive production action deliberately not attempted |
| Android hotfix | same production signing identity + higher code; verify update | not executed | no keystore, Release APK or Android device |

## Safe strategy, pending rehearsal

- Keep deployment immutable by image digest; canary then ECS prior task revision.
- Use expand/contract DB migrations; app-image rollback must not assume schema rollback.
- Production restore can lose writes after recovery point; incident commander/DB/privacy owner approval required.
- Direct APK hotfix must use same signing key and a greater `versionCode`; no downgrade, silent install or signature bypass.
- `restore-postgres.sh` is intended only for isolated nonproduction targets; its environment confirmation is operator assertion and is not automatic endpoint verification.

No owner/rollback authority has been named. Before production, rehearse a known failure in staging, prove alarm, roll back backend, validate DB compatibility and run a safe restore drill; record approver, timestamps, versions/digests and test outputs.
