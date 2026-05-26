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

backup_database() {
  local database_name="$1"
  local backup_file="${BACKUP_DIR}/${database_name}_${timestamp}.dump"

  echo "Backing up ${database_name} to ${backup_file}"
  pg_dump \
    --host="${DB_HOST}" \
    --port="${POSTGRES_PORT}" \
    --username="${DB_USERNAME}" \
    --dbname="${database_name}" \
    --format=custom \
    --file="${backup_file}" \
    --no-password
}

require_command pg_dump

require_env DB_HOST
require_env POSTGRES_PORT
require_env DB_USERNAME
require_env DB_PASSWORD

for db_var in "${SERVICE_DB_VARS[@]}"; do
  require_env "${db_var}"
done

BACKUP_DIR="${BACKUP_DIR:-backups/postgres}"
timestamp="$(date -u +%Y%m%dT%H%M%SZ)"

mkdir -p "${BACKUP_DIR}"

export PGPASSWORD="${DB_PASSWORD}"

for db_var in "${SERVICE_DB_VARS[@]}"; do
  backup_database "${!db_var}"
done

echo "PostgreSQL backups completed: ${BACKUP_DIR}"
