#!/usr/bin/env bash
# Devcontainer post-create hook. Runs once after the container is created.
# Keep it idempotent — VS Code may rerun on rebuild.

set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$REPO_ROOT"

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
