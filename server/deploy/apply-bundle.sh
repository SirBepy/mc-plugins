#!/usr/bin/env bash
# Applies an uploaded mc-server-bundle.tar.gz to the server directory. Run as root on the VPS:
#   apply-bundle.sh /path/to/mc-server-bundle.tar.gz
#
# Safe for the very first deploy (no world yet) and for every later update: never deletes anything,
# never touches world region/playerdata or plugin data folders except the specific config files the
# bundle ships, and only ever moves stale plugin jars aside (into plugins/.removed-<date>/).
#
# --skip-systemctl: used by the local Docker smoke test, which has no systemd; does the same file
# operations but never calls systemctl or depends on the minecraft.service unit existing.
set -euo pipefail

SERVER_DIR=/opt/minecraft/server
BACKUP_BIN=/opt/minecraft/bin/backup.sh

SKIP_SYSTEMCTL=0
BUNDLE=""
for arg in "$@"; do
  case "$arg" in
    --skip-systemctl) SKIP_SYSTEMCTL=1 ;;
    *) BUNDLE="$arg" ;;
  esac
done

if [ -z "$BUNDLE" ] || [ ! -f "$BUNDLE" ]; then
  echo "usage: apply-bundle.sh [--skip-systemctl] /path/to/mc-server-bundle.tar.gz" >&2
  exit 1
fi

mkdir -p "$SERVER_DIR"

HAD_WORLD=0
[ -d "$SERVER_DIR/world" ] && HAD_WORLD=1

if [ "$HAD_WORLD" -eq 1 ]; then
  if [ "$SKIP_SYSTEMCTL" -eq 0 ]; then
    echo "==> Stopping minecraft.service before deploying over an existing world"
    systemctl stop minecraft.service
  fi
  echo "==> Pre-deploy backup"
  if [ -x "$BACKUP_BIN" ]; then
    "$BACKUP_BIN" pre-deploy --no-service
  else
    echo "    $BACKUP_BIN not found, skipping (expected only in the Docker smoke test)"
  fi
fi

STAGING="$(mktemp -d)"
trap 'rm -rf "$STAGING"' EXIT
echo "==> Extracting $BUNDLE to $STAGING"
tar xzf "$BUNDLE" -C "$STAGING"

echo "==> Copying bundle contents over $SERVER_DIR (seed/ handled separately below)"
find "$STAGING" -mindepth 1 -maxdepth 1 ! -name seed -exec cp -a -t "$SERVER_DIR" {} +
chmod +x "$SERVER_DIR/start.sh" 2>/dev/null || true

if [ -d "$STAGING/seed" ]; then
  echo "==> Applying seed/ (no-clobber: only files that don't already exist on the server)"
  cp -rn "$STAGING/seed/." "$SERVER_DIR/"
fi

if [ -d "$SERVER_DIR/plugins" ] && [ -d "$STAGING/plugins" ]; then
  REMOVED_DIR="$SERVER_DIR/plugins/.removed-$(date +%Y%m%d-%H%M%S)"
  mapfile -t STALE_JARS < <(comm -23 \
    <(find "$SERVER_DIR/plugins" -maxdepth 1 -name '*.jar' -printf '%f\n' | sort) \
    <(find "$STAGING/plugins" -maxdepth 1 -name '*.jar' -printf '%f\n' | sort))
  if [ "${#STALE_JARS[@]}" -gt 0 ]; then
    mkdir -p "$REMOVED_DIR"
    for jar in "${STALE_JARS[@]}"; do
      mv "$SERVER_DIR/plugins/$jar" "$REMOVED_DIR/"
      echo "    moved stale plugin jar (no longer in bundle): $jar -> ${REMOVED_DIR#$SERVER_DIR/}/"
    done
  fi
fi

echo "==> chown -R minecraft:minecraft $SERVER_DIR"
chown -R minecraft:minecraft "$SERVER_DIR"

if [ "$SKIP_SYSTEMCTL" -eq 0 ]; then
  echo "==> Starting minecraft.service"
  systemctl start minecraft.service
  sleep 2
  systemctl status minecraft.service --no-pager || true
  echo "==> Last 30 log lines"
  journalctl -u minecraft.service -n 30 --no-pager || true
else
  echo "==> --skip-systemctl: files staged, service not started"
fi

echo "==> apply-bundle.sh done"
