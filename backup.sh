#!/bin/bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_NAME="BibelVersApp"
TIMESTAMP="$(date +%Y%m%d-%H%M%S)"
BACKUP_BASENAME="${PROJECT_NAME}-backup-${TIMESTAMP}"
DESKTOP_DIR="${HOME}/Desktop"
BACKUP_DIR="${DESKTOP_DIR}/${BACKUP_BASENAME}"
ARCHIVE_PATH="${DESKTOP_DIR}/${BACKUP_BASENAME}.tar.gz"

EXCLUDES=(
  "--exclude=.gradle/"
  "--exclude=.gradle-tmp/"
  "--exclude=.kotlin/"
  "--exclude=app/build/"
  "--exclude=build/"
  "--exclude=*/build/"
  "--exclude=.DS_Store"
  "--exclude=*/.DS_Store"
  "--exclude=*.tmp"
  "--exclude=*.temp"
  "--exclude=*.log"
  "--exclude=*.iml"
)

mkdir -p "${BACKUP_DIR}"

rsync -a "${EXCLUDES[@]}" "${SCRIPT_DIR}/" "${BACKUP_DIR}/"

tar -czf "${ARCHIVE_PATH}" -C "${DESKTOP_DIR}" "${BACKUP_BASENAME}"

printf 'Backup erstellt: %s\nArchiv erstellt: %s\n' "${BACKUP_DIR}" "${ARCHIVE_PATH}"
rm -rf "${BACKUP_DIR}/"
printf 'Ordner gelöscht: %s\n' "${BACKUP_DIR}"
