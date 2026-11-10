#!/usr/bin/env bash
set -euo pipefail

: "${BACKUP_FILE:?Set BACKUP_FILE to a downloaded .dump.age file}"
: "${AGE_IDENTITY_FILE:?Set AGE_IDENTITY_FILE to a protected private identity file}"
: "${RESTORE_TARGET_ENV:?Set RESTORE_TARGET_ENV=restore-test or staging}"
: "${RESTORE_TARGET_CONFIRM:?Explicitly confirm the verified isolated nonproduction target}"
: "${PGHOST:?Set the isolated restore target host}"
: "${PGDATABASE:?Set the pre-created empty restore target database}"
: "${PGUSER:?Set the least-privileged restore role}"

case "$RESTORE_TARGET_ENV" in
  restore-test|staging) ;;
  *) echo "Refusing restore: target must be restore-test or staging" >&2; exit 2 ;;
esac
[[ "$RESTORE_TARGET_CONFIRM" == "VERIFIED_ISOLATED_NONPRODUCTION_TARGET" ]] || {
  echo "Refusing restore: confirm the actual DB endpoint is isolated and nonproduction" >&2
  exit 2
}
[[ -r "$BACKUP_FILE" ]] || { echo "Backup file is not readable" >&2; exit 2; }
[[ -r "$AGE_IDENTITY_FILE" ]] || { echo "Age identity is not readable" >&2; exit 2; }
chmod 600 "$AGE_IDENTITY_FILE"
command -v age >/dev/null || { echo "age is required" >&2; exit 2; }
command -v pg_restore >/dev/null || { echo "pg_restore is required" >&2; exit 2; }

# Restore into a fresh, isolated database. No --clean/--create flags are used.
# The operator must verify the SHA-256 sidecar before invoking this script.
set -o pipefail
age --decrypt --identity "$AGE_IDENTITY_FILE" "$BACKUP_FILE" \
  | pg_restore --no-password --dbname="$PGDATABASE" --single-transaction \
      --exit-on-error --no-owner --no-acl

echo "Restore command completed for isolated target environment: $RESTORE_TARGET_ENV"
echo "Run the documented row-count, migration, API smoke, and data-integrity checks before declaring recovery successful."
