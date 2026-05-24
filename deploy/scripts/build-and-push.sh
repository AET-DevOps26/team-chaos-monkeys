#!/usr/bin/env bash
# Build all FoundFlow service images locally and push them to a container registry.
# Run this on your laptop (more headroom than the 4 GB VM).
#
# Required env:
#   GHCR_USERNAME  — your GitHub username
#   GHCR_TOKEN     — a Personal Access Token with `write:packages` scope
# Optional:
#   IMAGE_TAG      — image tag to push (default: latest)
#   IMAGE_REGISTRY — registry + namespace (default: ghcr.io/aet-devops26).
#                    Override to ghcr.io/<your-user> if the org has restricted
#                    package publishing to admins and you only have personal-
#                    namespace push.
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$REPO_ROOT"

: "${GHCR_USERNAME:?GHCR_USERNAME required (your GitHub username)}"
: "${GHCR_TOKEN:?GHCR_TOKEN required (PAT with write:packages scope)}"
IMAGE_TAG="${IMAGE_TAG:-latest}"
IMAGE_REGISTRY="${IMAGE_REGISTRY:-ghcr.io/aet-devops26}"
export IMAGE_TAG IMAGE_REGISTRY

echo "==> Logging in to ghcr.io as $GHCR_USERNAME"
echo "$GHCR_TOKEN" | docker login ghcr.io -u "$GHCR_USERNAME" --password-stdin

echo "==> Building images (registry: $IMAGE_REGISTRY, tag: $IMAGE_TAG)"
docker compose \
  -f docker-compose.yml \
  -f deploy/docker-compose.prod.yml \
  build

echo "==> Pushing images to $IMAGE_REGISTRY/*"
docker compose \
  -f docker-compose.yml \
  -f deploy/docker-compose.prod.yml \
  push

case "$IMAGE_REGISTRY" in
  ghcr.io/aet-devops26)
    echo "==> Done. Images live at: https://github.com/orgs/AET-DevOps26/packages"
    ;;
  ghcr.io/*)
    USER="${IMAGE_REGISTRY#ghcr.io/}"
    echo "==> Done. Images live at: https://github.com/${USER}?tab=packages"
    ;;
  *)
    echo "==> Done. Images pushed to: $IMAGE_REGISTRY/*"
    ;;
esac
echo "    First push? Mark the packages public in the package settings so the VM"
echo "    can pull without a token (Package settings → Change visibility → Public)."
