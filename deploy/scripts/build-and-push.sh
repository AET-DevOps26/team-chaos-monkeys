#!/usr/bin/env bash
# Build all FoundFlow service images locally and push them to GHCR.
# Run this on your laptop (more headroom than the 4 GB VM).
#
# Required env:
#   GHCR_USERNAME — your GitHub username
#   GHCR_TOKEN    — a Personal Access Token with `write:packages` scope
# Optional:
#   IMAGE_TAG     — image tag to push (default: latest)
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$REPO_ROOT"

: "${GHCR_USERNAME:?GHCR_USERNAME required (your GitHub username)}"
: "${GHCR_TOKEN:?GHCR_TOKEN required (PAT with write:packages scope)}"
IMAGE_TAG="${IMAGE_TAG:-latest}"
export IMAGE_TAG

echo "==> Logging in to ghcr.io as $GHCR_USERNAME"
echo "$GHCR_TOKEN" | docker login ghcr.io -u "$GHCR_USERNAME" --password-stdin

echo "==> Building images (tag: $IMAGE_TAG)"
docker compose \
  -f docker-compose.yml \
  -f deploy/docker-compose.prod.yml \
  build

echo "==> Pushing images to ghcr.io/aet-devops26/*"
docker compose \
  -f docker-compose.yml \
  -f deploy/docker-compose.prod.yml \
  push

echo "==> Done. Images live at: https://github.com/orgs/AET-DevOps26/packages"
echo "    First push? Make the packages public via the GHCR UI so the VM can pull"
echo "    without a token (Package settings → Change visibility → Public)."
