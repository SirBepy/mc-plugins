#!/usr/bin/env bash
# Backs up the world + plugin data (never jars) into /opt/minecraft/backups/<label>-<timestamp>.tar.zst,
# keeping the newest 3 backups per label. Stops/starts the minecraft service around the backup unless
# --no-service is given (apply-bundle.sh already stopped it and will start it itself after deploying).
#
# Usage: backup.sh [label] [--no-service]
set -euo pipefail

SERVER_DIR=/opt/minecraft/server
BACKUP_DIR=/opt/minecraft/backups

LABEL=nightly
MANAGE_SERVICE=1
for arg in "$@"; do
  case "$arg" in
    --no-service) MANAGE_SERVICE=0 ;;
    *) LABEL="$arg" ;;
  esac
done

mkdir -p "$BACKUP_DIR"

if [ "$MANAGE_SERVICE" -eq 1 ]; then
  echo "==> Stopping minecraft.service"
  systemctl stop minecraft.service
fi

TIMESTAMP="$(date +%Y%m%d-%H%M%S)"
OUT="$BACKUP_DIR/${LABEL}-${TIMESTAMP}.tar.zst"

if [ -d "$SERVER_DIR/world" ]; then
  cd "$SERVER_DIR"
  # Paper 26.x keeps one world/ folder with dimensions/{overworld,the_nether,the_end} inside
  # (confirmed against a real booted server this session), not separate world_nether/world_the_end
  # top-level folders. Plugin jars live directly under plugins/ as siblings of each plugin's own
  # data folder, so `find -type d` picks up only the data folders, never a jar.
  mapfile -t PLUGIN_DATA_DIRS < <(find plugins -mindepth 1 -maxdepth 1 -type d 2>/dev/null)
  echo "==> Writing $OUT"
  tar --zstd -cf "$OUT" world "${PLUGIN_DATA_DIRS[@]}"
else
  echo "==> No world/ yet at $SERVER_DIR, nothing to back up ($LABEL)"
fi

# Keep only the newest 3 backups for this label; pre-deploy and nightly are retained separately
# since they're different label prefixes.
mapfile -t STALE < <(ls -1t "$BACKUP_DIR/${LABEL}-"*.tar.zst 2>/dev/null | tail -n +4)
if [ "${#STALE[@]}" -gt 0 ]; then
  echo "==> Pruning old $LABEL backups: ${STALE[*]}"
  rm -f "${STALE[@]}"
fi

if [ "$MANAGE_SERVICE" -eq 1 ]; then
  echo "==> Starting minecraft.service"
  systemctl start minecraft.service
fi

echo "==> backup.sh done ($LABEL)"
