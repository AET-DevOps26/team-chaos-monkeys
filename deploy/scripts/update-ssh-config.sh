#!/usr/bin/env bash
# Append or update a `Host tum-azure` block in ~/.ssh/config so you can
# `ssh tum-azure` instead of remembering the IP. Idempotent.
set -euo pipefail

ALIAS="tum-azure"
SSH_CONFIG="$HOME/.ssh/config"
TERRAFORM_DIR="$(cd "$(dirname "$0")/../terraform" && pwd)"

IP="$(terraform -chdir="$TERRAFORM_DIR" output -raw public_ip 2>/dev/null || true)"
if [[ -z "$IP" ]]; then
  echo "Could not read public_ip from terraform output." >&2
  echo "Did you run 'terraform apply' in $TERRAFORM_DIR yet?" >&2
  exit 1
fi

mkdir -p "$(dirname "$SSH_CONFIG")"
touch "$SSH_CONFIG"
chmod 600 "$SSH_CONFIG"

# Use python for safe in-place block edit (macOS ships python3 by default).
python3 - "$SSH_CONFIG" "$ALIAS" "$IP" <<'PY'
import re, sys, pathlib

cfg_path, alias, ip = pathlib.Path(sys.argv[1]), sys.argv[2], sys.argv[3]
content = cfg_path.read_text() if cfg_path.exists() else ""

block = (
    f"Host {alias}\n"
    f"    HostName {ip}\n"
    f"    User azureuser\n"
    f"    IdentityFile ~/.ssh/tum_devops_azure\n"
    f"    StrictHostKeyChecking accept-new\n"
)

# Match `Host {alias}` + all indented lines that follow, up to the next
# top-level directive or EOF. Replace the whole block so re-runs don't
# accumulate stale fields.
pattern = re.compile(
    rf"^Host\s+{re.escape(alias)}\s*\n(?:[ \t]+.*\n?)*",
    re.MULTILINE,
)

if pattern.search(content):
    content = pattern.sub(block, content)
    action = f"Updated existing `{alias}` entry → {ip}"
else:
    if content and not content.endswith("\n"):
        content += "\n"
    if content and not content.endswith("\n\n"):
        content += "\n"
    content += block
    action = f"Added `{alias}` entry → {ip}"

cfg_path.write_text(content)
print(action)
PY

echo "==> Try it: ssh $ALIAS"
