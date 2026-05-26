#!/usr/bin/env bash
set -Eeuo pipefail

SERVICE_DB_VARS=(
  USER_DB_NAME
  AUTH_DB_NAME
  BOOK_DB_NAME
  EXCHANGE_DB_NAME
  NOTIFICATION_DB_NAME
)

require_env() {
  local name="$1"
  if [[ -z "${!name:-}" ]]; then
    echo "Required environment variable is missing: ${name}" >&2
    exit 1
  fi
}

require_command() {
  local command_name="$1"
  if ! command -v "${command_name}" >/dev/null 2>&1; then
    echo "Required command is not available: ${command_name}" >&2
    exit 1
  fi
}

is_supported_database() {
  local requested_db="$1"
  local db_var

  for db_var in "${SERVICE_DB_VARS[@]}"; do
    if [[ "${requested_db}" == "${!db_var}" ]]; then
      return 0
    fi
  done

  return 1
}

require_command pg_restore

require_env DB_HOST
require_env POSTGRES_PORT
require_env DB_USERNAME
require_env DB_PASSWORD
require_env TARGET_DB
require_env BACKUP_FILE

for db_var in "${SERVICE_DB_VARS[@]}"; do
  require_env "${db_var}"
done

if [[ "${CONFIRM_RESTORE:-}" != "true" ]]; then
  echo "Refusing restore: set CONFIRM_RESTORE=true to acknowledge destructive restore." >&2
  exit 1
fi

if [[ ! -f "${BACKUP_FILE}" ]]; then
  echo "Backup file does not exist: ${BACKUP_FILE}" >&2
  exit 1
fi

if ! is_supported_database "${TARGET_DB}"; then
  echo "Unsupported TARGET_DB: ${TARGET_DB}" >&2
  echo "Allowed target DBs are: ${USER_DB_NAME}, ${AUTH_DB_NAME}, ${BOOK_DB_NAME}, ${EXCHANGE_DB_NAME}, ${NOTIFICATION_DB_NAME}" >&2
  exit 1
fi

export PGPASSWORD="${DB_PASSWORD}"

echo "Restoring ${BACKUP_FILE} into ${TARGET_DB}"
pg_restore \
  --host="${DB_HOST}" \
  --port="${POSTGRES_PORT}" \
  --username="${DB_USERNAME}" \
  --dbname="${TARGET_DB}" \
  --clean \
  --if-exists \
  --no-owner \
  --no-privileges \
  --no-password \
  "${BACKUP_FILE}"

echo "PostgreSQL restore completed for ${TARGET_DB}"
