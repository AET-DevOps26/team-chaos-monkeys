#!/usr/bin/env bash
# Build all FoundFlow service images locally and push them to a container registry.
# Run this on your laptop (more headroom than the 4 GB VM).
#
# Required env:
#   GHCR_USERNAME  — your GitHub username
#   GHCR_TOKEN     — a Personal Access Token with `write:packages` scope
# Optional:
#   IMAGE_TAG      — image tag to push (default: latest)
#   IMAGE_REGISTRY — registry + namespace
#                    (default: ghcr.io/aet-devops26/team-chaos-monkeys).
#                    Packages are scoped to this repo, so any teammate with
#                    write/maintain on team-chaos-monkeys can push without
#                    org-admin approval. Override for a different registry.
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$REPO_ROOT"

: "${GHCR_USERNAME:?GHCR_USERNAME required (your GitHub username)}"
: "${GHCR_TOKEN:?GHCR_TOKEN required (PAT with write:packages scope)}"
IMAGE_TAG="${IMAGE_TAG:-latest}"
IMAGE_REGISTRY="${IMAGE_REGISTRY:-ghcr.io/aet-devops26/team-chaos-monkeys}"

# Pin builds to linux/amd64 regardless of the laptop's architecture.
# Without this, an M-series Mac produces arm64 images and they fail on an
# x86_64 VM with "exec format error". The Azure VM in this iteration is x86,
# so single-arch amd64 is correct here. Switch to multi-arch (`linux/amd64,
# linux/arm64`) once we also target ARM VMs or want laptop pulls to work.
TARGET_PLATFORM="${TARGET_PLATFORM:-linux/amd64}"
export DOCKER_DEFAULT_PLATFORM="$TARGET_PLATFORM"
export IMAGE_TAG IMAGE_REGISTRY

echo "==> Logging in to ghcr.io as $GHCR_USERNAME"
echo "$GHCR_TOKEN" | docker login ghcr.io -u "$GHCR_USERNAME" --password-stdin

echo "==> Building images (registry: $IMAGE_REGISTRY, tag: $IMAGE_TAG, platform: $TARGET_PLATFORM)"
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
  ghcr.io/aet-devops26/team-chaos-monkeys)
    echo "==> Done. Images live at: https://github.com/AET-DevOps26/team-chaos-monkeys/pkgs/container"
    ;;
  ghcr.io/*)
    echo "==> Done. Images pushed under: $IMAGE_REGISTRY"
    ;;
  *)
    echo "==> Done. Images pushed to: $IMAGE_REGISTRY/*"
    ;;
esac
echo "    First push? Mark the packages public so the VM can pull without a token"
echo "    (Package settings → Change visibility → Public)."
