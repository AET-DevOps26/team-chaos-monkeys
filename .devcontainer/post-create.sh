#!/usr/bin/env bash
# Devcontainer post-create hook. Runs once after the container is created.
# Keep it idempotent — VS Code may rerun on rebuild.

set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$REPO_ROOT"

# VS Code's Dev Containers extension writes a one-shot `credsStore` entry to
# ~/.docker/config.json that references a helper binary which does not exist
# on disk (`dev-containers-<uuid>`). With Docker-outside-of-Docker, any
# `docker pull` / `docker build` from inside the container then fails with
# `error getting credentials - err: exit status 255`. Strip the field so the
# CLI falls back to anonymous pulls from public registries.
echo "==> Removing broken credsStore from ~/.docker/config.json (DooD workaround)"
DOCKER_CFG="$HOME/.docker/config.json"
if [ -f "$DOCKER_CFG" ] && grep -q '"credsStore"' "$DOCKER_CFG"; then
  tmp="$(mktemp)"
  jq 'del(.credsStore)' "$DOCKER_CFG" > "$tmp" && mv "$tmp" "$DOCKER_CFG"
  echo "    credsStore removed"
else
  echo "    no credsStore present — nothing to do"
fi

echo "==> Installing frontend dependencies"
if [ -f client/package.json ]; then
  (cd client && npm ci)
else
  echo "    client/package.json not found — skipping"
fi

echo "==> Warming Gradle wrapper"
# Pre-downloads the Gradle distribution declared by the wrapper so first
# build inside the container is fast. Running against the auth-service
# scaffold; new services that mirror this scaffold will reuse the cache.
if [ -x services/auth-service/gradlew ]; then
  (cd services/auth-service && ./gradlew --version > /dev/null)
else
  echo "    services/auth-service/gradlew not found — skipping"
fi

echo "==> Installing global OpenAPI tooling"
# Project-wide CLIs: Redocly for spec linting (see redocly.yaml at repo root)
# and openapi-generator-cli for Spring/Python/TS client codegen.
npm install -g --silent @redocly/cli @openapitools/openapi-generator-cli

echo "==> Verifying toolchain"
node --version
java -version 2>&1 | head -1
python3 --version
docker --version
kubectl version --client --output=yaml 2>/dev/null | head -2 || true
helm version --short || true
gh --version | head -1

cat <<'BANNER'

Devcontainer ready.

Next steps:
  - Lint OpenAPI:        npx @redocly/cli lint
  - Frontend dev:        cd client && npm run dev
  - Spring service:      cd services/auth-service && ./gradlew bootRun
  - Runtime stack:       docker compose up   (once #58 lands)

Ollama (local LLM):      install on the host, not in the container.
                         GenAI service points at host.docker.internal:11434.

BANNER
