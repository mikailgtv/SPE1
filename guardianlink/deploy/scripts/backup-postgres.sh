#!/usr/bin/env bash
set -euo pipefail

: "${PGHOST:?Set a private PostgreSQL host}"
: "${PGDATABASE:?Set PGDATABASE}"
: "${PGUSER:?Set PGUSER}"
: "${BACKUP_DIR:?Set BACKUP_DIR to an access-controlled backup mount}"
: "${AGE_RECIPIENT:?Set an age public recipient; keep the private identity offline}"
: "${S3_BACKUP_URI:?Set s3://<private-bucket>/<prefix>; off-host copy is mandatory}"
: "${S3_KMS_KEY_ARN:?Set the S3 KMS key ARN}"
: "${AWS_REGION:?Set the AWS region}"

command -v pg_dump >/dev/null || { echo "pg_dump is required" >&2; exit 2; }
command -v age >/dev/null || { echo "age is required" >&2; exit 2; }
command -v aws >/dev/null || { echo "AWS CLI is required for off-host copy" >&2; exit 2; }

umask 077
mkdir -p "$BACKUP_DIR"
chmod 700 "$BACKUP_DIR"
now="$(date -u +%Y%m%dT%H%M%SZ)"
base="guardianlink-daily-${now}.dump.age"
final="$BACKUP_DIR/$base"
partial="$final.partial"
trap 'rm -f "$partial"' EXIT

# Stream directly from pg_dump through age. No unencrypted dump is written to disk.
pg_dump --no-password --format=custom --no-owner --no-acl --dbname="$PGDATABASE" \
  | age --encrypt --recipient "$AGE_RECIPIENT" --output "$partial"
test -s "$partial"
chmod 600 "$partial"
mv "$partial" "$final"
trap - EXIT
sha256sum "$final" > "$final.sha256"
chmod 600 "$final.sha256"
archive_files=("$final" "$final.sha256")

# Keep explicit daily/weekly/monthly restore points; lifecycle policy on the S3 bucket
# must separately enforce retention and deletion on the remote copy.
weekday="$(date -u +%u)"
monthday="$(date -u +%d)"
if [[ "$weekday" == "7" ]]; then
  weekly="$BACKUP_DIR/guardianlink-weekly-${now}.dump.age"
  cp "$final" "$weekly"
  sha256sum "$weekly" > "$weekly.sha256"
  archive_files+=("$weekly" "$weekly.sha256")
fi
if [[ "$monthday" == "01" ]]; then
  monthly="$BACKUP_DIR/guardianlink-monthly-${now}.dump.age"
  cp "$final" "$monthly"
  sha256sum "$monthly" > "$monthly.sha256"
  archive_files+=("$monthly" "$monthly.sha256")
fi

for pair in "daily:${BACKUP_DAILY_DAYS:-14}" "weekly:${BACKUP_WEEKLY_DAYS:-56}" "monthly:${BACKUP_MONTHLY_DAYS:-365}"; do
  kind="${pair%%:*}"
  days="${pair##*:}"
  [[ "$days" =~ ^[0-9]+$ ]] || { echo "Invalid retention days for $kind" >&2; exit 2; }
  find "$BACKUP_DIR" -maxdepth 1 -type f -name "guardianlink-${kind}-*.dump.age" -mtime "+${days}" -delete
  find "$BACKUP_DIR" -maxdepth 1 -type f -name "guardianlink-${kind}-*.dump.age.sha256" -mtime "+${days}" -delete
done

# Upload encrypted payload and checksum. S3 bucket policy must deny public access,
# require TLS, enable versioning, and enforce its own lifecycle/Object Lock policy.
for file in "${archive_files[@]}"; do
  aws s3 cp "$file" "$S3_BACKUP_URI/$(basename "$file")" --region "$AWS_REGION" \
    --sse aws:kms --sse-kms-key-id "$S3_KMS_KEY_ARN" --only-show-errors
done

echo "Encrypted daily/weekly/monthly backup artifacts copied off-host where scheduled: $base"
