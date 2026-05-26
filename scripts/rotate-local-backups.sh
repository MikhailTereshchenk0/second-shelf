#!/usr/bin/env bash
set -Eeuo pipefail

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

require_command find
require_env BACKUP_DIR

BACKUP_RETENTION_DAYS="${BACKUP_RETENTION_DAYS:-30}"

if [[ ! "${BACKUP_RETENTION_DAYS}" =~ ^[0-9]+$ ]]; then
  echo "BACKUP_RETENTION_DAYS must be a non-negative integer." >&2
  exit 1
fi

if [[ ! -d "${BACKUP_DIR}" ]]; then
  echo "Backup directory does not exist: ${BACKUP_DIR}" >&2
  exit 1
fi

echo "Deleting local PostgreSQL backup dumps older than ${BACKUP_RETENTION_DAYS} days from ${BACKUP_DIR}"
find "${BACKUP_DIR}" \
  -type f \
  -name '*.dump' \
  -mtime +"${BACKUP_RETENTION_DAYS}" \
  -print \
  -delete

echo "Local PostgreSQL backup rotation completed."
