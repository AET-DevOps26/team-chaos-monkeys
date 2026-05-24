#!/usr/bin/env bash
# Bootstrap the Azure VM: install Docker, ship compose + .env, pull images, start the stack.
# Idempotent — safe to re-run. Throwaway: Ansible replaces this in the next iteration.
#
# Usage:
#   ./bootstrap.sh <vm_ip_or_ssh_alias>
# Examples:
#   ./bootstrap.sh 20.61.42.99
#   ./bootstrap.sh tum-azure        # after update-ssh-config.sh has been run
set -euo pipefail

TARGET="${1:?Usage: $0 <vm_ip_or_ssh_alias>}"
SSH_KEY="$HOME/.ssh/tum_devops_azure"
SSH_OPTS=(-i "$SSH_KEY" -o StrictHostKeyChecking=accept-new -o ConnectTimeout=15)

# If the user passed a bare IP, prepend azureuser@. If they passed an alias from
# ~/.ssh/config (e.g. tum-azure), keep it as-is so SSH config can supply the user.
if [[ "$TARGET" =~ ^[0-9]+\.[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
  REMOTE="azureuser@${TARGET}"
else
  REMOTE="$TARGET"
fi

REPO_ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
COMPOSE_FILE="${REPO_ROOT}/docker-compose.yml"
PROD_OVERRIDE="${REPO_ROOT}/deploy/docker-compose.prod.yml"
ENV_FILE="${REPO_ROOT}/.env"

[[ -f "$COMPOSE_FILE" ]]  || { echo "Missing $COMPOSE_FILE" >&2; exit 1; }
[[ -f "$PROD_OVERRIDE" ]] || { echo "Missing $PROD_OVERRIDE" >&2; exit 1; }
[[ -f "$ENV_FILE" ]]      || { echo "Missing $ENV_FILE (copy .env.example and fill in values)" >&2; exit 1; }

echo "==> Target: $REMOTE"

# ---- 1. Install Docker engine + compose plugin (only if not already there) --
echo "==> Installing Docker engine + compose plugin (if needed)"
ssh "${SSH_OPTS[@]}" "$REMOTE" 'bash -s' <<'INSTALL'
set -euo pipefail
if command -v docker >/dev/null 2>&1 && docker compose version >/dev/null 2>&1; then
  echo "Docker + compose plugin already installed: $(docker --version)"
  exit 0
fi

export DEBIAN_FRONTEND=noninteractive
sudo apt-get update -qq
sudo apt-get install -y -qq ca-certificates curl gnupg lsb-release

sudo install -m 0755 -d /etc/apt/keyrings
if [[ ! -f /etc/apt/keyrings/docker.gpg ]]; then
  curl -fsSL https://download.docker.com/linux/ubuntu/gpg \
    | sudo gpg --dearmor -o /etc/apt/keyrings/docker.gpg
  sudo chmod a+r /etc/apt/keyrings/docker.gpg
fi

CODENAME="$(lsb_release -cs)"
ARCH="$(dpkg --print-architecture)"
echo "deb [arch=${ARCH} signed-by=/etc/apt/keyrings/docker.gpg] https://download.docker.com/linux/ubuntu ${CODENAME} stable" \
  | sudo tee /etc/apt/sources.list.d/docker.list >/dev/null

sudo apt-get update -qq
sudo apt-get install -y -qq \
  docker-ce docker-ce-cli containerd.io \
  docker-buildx-plugin docker-compose-plugin

sudo usermod -aG docker "$USER" || true
sudo systemctl enable --now docker
echo "Installed: $(docker --version)"
INSTALL

# ---- 2. Ship compose files + .env -------------------------------------------
echo "==> Copying compose files + .env to ~/foundflow/"
ssh "${SSH_OPTS[@]}" "$REMOTE" 'mkdir -p ~/foundflow/deploy'
scp "${SSH_OPTS[@]}" \
  "$COMPOSE_FILE" \
  "$ENV_FILE" \
  "${REMOTE}:~/foundflow/"
scp "${SSH_OPTS[@]}" \
  "$PROD_OVERRIDE" \
  "${REMOTE}:~/foundflow/deploy/"

# ---- 3. Pull images + start the stack ---------------------------------------
echo "==> Pulling images + starting the stack"
ssh "${SSH_OPTS[@]}" "$REMOTE" 'bash -s' <<'START'
set -euo pipefail
cd ~/foundflow
# sudo because the freshly-added docker group membership doesn't apply
# in this SSH session yet. Future sessions can drop sudo.
sudo docker compose -f docker-compose.yml -f deploy/docker-compose.prod.yml pull
sudo docker compose -f docker-compose.yml -f deploy/docker-compose.prod.yml up -d
sudo docker compose -f docker-compose.yml -f deploy/docker-compose.prod.yml ps
START

echo "==> Done. Verify with: curl http://${TARGET//azureuser@/}/"
