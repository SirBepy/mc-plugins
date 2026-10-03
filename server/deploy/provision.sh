#!/usr/bin/env bash
# Run once as root on a fresh Ubuntu 24.04 VPS. Idempotent: re-running after a config change (or to
# pick up a new systemd unit) is safe. Expects its sibling files (minecraft.service,
# mc-backup.service, mc-backup.timer, backup.sh, apply-bundle.sh) to be uploaded alongside it.
set -euo pipefail

if [ "$(id -u)" -ne 0 ]; then
  echo "provision.sh must run as root" >&2
  exit 1
fi

HERE="$(cd "$(dirname "$0")" && pwd)"
SERVER_USER=minecraft
SERVER_HOME=/opt/minecraft
SERVER_DIR="$SERVER_HOME/server"
BACKUP_DIR="$SERVER_HOME/backups"
BIN_DIR="$SERVER_HOME/bin"

# Docker (and other container runtimes) have no systemd as PID 1 and no usable netfilter/ufw stack;
# the local smoke test runs provision.sh's file/package steps inside one, so skip what doesn't apply
# there instead of failing outright.
IN_CONTAINER=0
if [ -f /.dockerenv ] || [ "$(ps -p 1 -o comm= 2>/dev/null)" != "systemd" ]; then
  IN_CONTAINER=1
  echo "==> Container detected (no systemd as PID 1): skipping systemd units, ufw, SSH hardening, timezone."
fi

echo "==> apt update/upgrade"
export DEBIAN_FRONTEND=noninteractive
apt-get update -y
apt-get upgrade -y

echo "==> Prerequisites"
apt-get install -y wget apt-transport-https gpg ca-certificates

echo "==> Eclipse Adoptium apt repo (Temurin 25 JRE)"
# Signed-key method per adoptium.net/installation/linux/ (checked 2026-10-03).
if [ ! -f /etc/apt/sources.list.d/adoptium.list ]; then
  wget -qO - https://packages.adoptium.net/artifactory/api/gpg/key/public | gpg --dearmor | tee /etc/apt/trusted.gpg.d/adoptium.gpg > /dev/null
  echo "deb https://packages.adoptium.net/artifactory/deb $(awk -F= '/^VERSION_CODENAME/{print $2}' /etc/os-release) main" > /etc/apt/sources.list.d/adoptium.list
  apt-get update -y
else
  echo "    already configured"
fi

echo "==> Installing temurin-25-jre, zstd, unattended-upgrades$([ "$IN_CONTAINER" -eq 0 ] && echo ', ufw')"
if [ "$IN_CONTAINER" -eq 0 ]; then
  apt-get install -y temurin-25-jre zstd unattended-upgrades ufw
else
  apt-get install -y temurin-25-jre zstd unattended-upgrades
fi

echo "==> System user $SERVER_USER"
if ! id "$SERVER_USER" >/dev/null 2>&1; then
  useradd --system --create-home --home-dir "$SERVER_HOME" --shell /usr/sbin/nologin "$SERVER_USER"
else
  echo "    already exists"
fi
mkdir -p "$SERVER_DIR" "$BACKUP_DIR" "$BIN_DIR"
chown -R "$SERVER_USER:$SERVER_USER" "$SERVER_HOME"

if [ "$IN_CONTAINER" -eq 0 ]; then
  echo "==> Timezone (Europe/Zagreb, so the 05:00 backup timer fires at the intended local time)"
  timedatectl set-timezone Europe/Zagreb

  echo "==> ufw"
  ufw default deny incoming
  ufw default allow outgoing
  ufw allow 22/tcp
  ufw allow 25565/tcp
  ufw allow 19132/udp
  ufw allow 24454/udp
  ufw --force enable

  echo "==> SSH hardening"
  if [ -s /root/.ssh/authorized_keys ]; then
    cat > /etc/ssh/sshd_config.d/99-hardening.conf <<'EOF'
PasswordAuthentication no
PermitRootLogin prohibit-password
EOF
    systemctl reload ssh || systemctl reload sshd
  else
    echo "    /root/.ssh/authorized_keys is empty or missing: refusing to disable password auth (would lock you out)."
  fi

  echo "==> systemd units"
  cp "$HERE/minecraft.service" "$HERE/mc-backup.service" "$HERE/mc-backup.timer" /etc/systemd/system/
  cp "$HERE/apply-bundle.sh" "$HERE/backup.sh" "$BIN_DIR/"
  chmod +x "$BIN_DIR/apply-bundle.sh" "$BIN_DIR/backup.sh"
  systemctl daemon-reload
  systemctl enable minecraft.service
  systemctl enable --now mc-backup.timer

  echo "==> Unattended security upgrades"
  cat > /etc/apt/apt.conf.d/20auto-upgrades <<'EOF'
APT::Periodic::Update-Package-Lists "1";
APT::Periodic::Unattended-Upgrade "1";
EOF
  systemctl enable --now unattended-upgrades
else
  cp "$HERE/apply-bundle.sh" "$HERE/backup.sh" "$BIN_DIR/"
  chmod +x "$BIN_DIR/apply-bundle.sh" "$BIN_DIR/backup.sh"
fi

echo ""
echo "==> Summary"
echo "    user: $SERVER_USER, home: $SERVER_HOME"
echo "    server dir: $SERVER_DIR"
echo "    backups: $BACKUP_DIR (nightly 05:00 Europe/Zagreb, newest 3 kept)"
java -version
if [ "$IN_CONTAINER" -eq 0 ]; then
  ufw status verbose
  systemctl is-enabled minecraft.service mc-backup.timer
else
  echo "    (systemd/ufw/ssh steps skipped: container environment)"
fi
echo "==> provision.sh done"
