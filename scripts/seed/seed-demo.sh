#!/bin/sh
# Seeds demo lost-and-found data through the public API so a real GenAI/pgvector match
# forms on first boot. Idempotent and non-fatal: if anything is already seeded, or a
# service never comes up, it exits 0 without touching the stack.
set -u

GATEWAY="${GATEWAY_BASE_URL:-http://gateway-service:8080}"
ADMIN_EMAIL="${DEV_ADMIN_EMAIL:-admin@foundflow.local}"
ADMIN_PASSWORD="${DEV_ADMIN_PASSWORD:-admin12345}"
VENUE_ID="${DEMO_VENUE_ID:-00000000-0000-0000-0000-000000000001}"
ASSETS="${SEED_ASSETS_DIR:-/seed/assets}"
NOW="$(date -u +%Y-%m-%dT%H:%M:%S)"

if [ "${SEED_DEMO_DATA:-true}" != "true" ]; then
  echo "[seed] SEED_DEMO_DATA != true — skipping."
  exit 0
fi

# Wait until a service answers its actuator health through the gateway, or give up.
wait_health() {
  slug="$1"; i=0
  until curl -fsS "$GATEWAY/$slug/actuator/health" >/dev/null 2>&1; do
    i=$((i + 1))
    if [ "$i" -ge 120 ]; then
      echo "[seed] $slug not healthy after ~10min — giving up (non-fatal)."
      exit 0
    fi
    sleep 5
  done
}

echo "[seed] waiting for auth / found-items / lost-items ..."
wait_health auth
wait_health found-items
wait_health lost-items

echo "[seed] logging in as $ADMIN_EMAIL"
TOKEN=""; i=0
while [ -z "$TOKEN" ] || [ "$TOKEN" = "null" ]; do
  TOKEN="$(curl -fsS -X POST "$GATEWAY/api/auth/login" \
    -H 'Content-Type: application/json' \
    -d "{\"email\":\"$ADMIN_EMAIL\",\"password\":\"$ADMIN_PASSWORD\"}" \
    2>/dev/null | jq -r '.accessToken // empty')"
  i=$((i + 1))
  if [ "$i" -ge 30 ]; then
    echo "[seed] could not obtain an admin token — giving up (non-fatal)."
    exit 0
  fi
  [ -z "$TOKEN" ] && sleep 5
done
AUTH="Authorization: Bearer $TOKEN"

# Idempotency: if any found items already exist, assume seeding ran before.
EXISTING="$(curl -fsS -H "$AUTH" "$GATEWAY/api/found-items" 2>/dev/null | jq 'length' 2>/dev/null || echo 0)"
if [ "${EXISTING:-0}" -gt 0 ]; then
  echo "[seed] $EXISTING found item(s) already present — nothing to seed."
  exit 0
fi

# Staff logs found items (with photos) for the demo venue.
create_found() {
  text="$1"; photo="$2"
  id="$(curl -fsS -X POST "$GATEWAY/api/found-items" -H "$AUTH" \
    -H 'Content-Type: application/json' \
    -d "{\"intakeText\":\"$text\",\"foundAt\":\"$NOW\",\"venueId\":\"$VENUE_ID\"}" \
    | jq -r '.id')"
  curl -fsS -X PUT "$GATEWAY/api/found-items/$id/photo" -H "$AUTH" \
    -F "photo=@$ASSETS/$photo" >/dev/null
  echo "[seed] found item $id — $text"
}

create_found "Purple leather wallet found at the lobby bar." purple-wallet.jpg
create_found "Purple puffer jacket left in the cloakroom." purple-puffer.jpg
create_found "Purple cotton shirt found in conference room B." purple-shirt.jpg

# A guest reports a lost wallet — this is the one that should match the found wallet.
curl -fsS -X POST "$GATEWAY/api/lost-items" \
  -H 'Content-Type: application/json' \
  -d "{\"description\":\"I lost my purple leather wallet near the main entrance.\",\"lostAt\":\"$NOW\",\"location\":\"Main entrance\",\"contactEmail\":\"guest.demo@example.com\",\"venueId\":\"$VENUE_ID\"}" \
  >/dev/null
echo "[seed] guest lost report — purple leather wallet"

echo "[seed] done. The intake → matching pipeline will produce the wallet match shortly."
