#!/bin/sh
# Seeds demo lost-and-found data through the public API so real GenAI/pgvector
# matches form on first boot. Idempotent and non-fatal: if a service never comes
# up, it exits 0 without touching the stack.
set -u

GATEWAY="${GATEWAY_BASE_URL:-http://gateway-service:8080}"
ADMIN_EMAIL="${DEV_ADMIN_EMAIL:-admin@foundflow.local}"
ADMIN_PASSWORD="${DEV_ADMIN_PASSWORD:-admin12345}"
VENUE_ID="${DEMO_VENUE_ID:-00000000-0000-0000-0000-000000000001}"
STAFF_EMAIL="${DEMO_STAFF_EMAIL:-staff.demo@foundflow.local}"
STAFF_PASSWORD="${DEMO_STAFF_PASSWORD:-test12345}"
ASSETS="${SEED_ASSETS_DIR:-/seed/assets}"
NOW="$(date -u +%Y-%m-%dT%H:%M:%S)"

if [ "${SEED_DEMO_DATA:-true}" != "true" ]; then
  echo "[seed] SEED_DEMO_DATA != true - skipping."
  exit 0
fi

wait_health() {
  slug="$1"; i=0
  until curl -fsS "$GATEWAY/$slug/actuator/health" >/dev/null 2>&1; do
    i=$((i + 1))
    if [ "$i" -ge 120 ]; then
      echo "[seed] $slug not healthy after ~10min - giving up (non-fatal)."
      exit 0
    fi
    sleep 5
  done
}

login_token() {
  email="$1"; password="$2"; label="$3"; token=""; i=0
  echo "[seed] logging in as $label ($email)" >&2
  while [ -z "$token" ] || [ "$token" = "null" ]; do
    token="$(curl -fsS -X POST "$GATEWAY/api/auth/login" \
      -H 'Content-Type: application/json' \
      -d "$(jq -nc --arg email "$email" --arg password "$password" \
        '{email:$email,password:$password}')" \
      2>/dev/null | jq -r '.accessToken // empty')"
    i=$((i + 1))
    if [ "$i" -ge 30 ]; then
      return 1
    fi
    [ -z "$token" ] && sleep 5
  done
  printf '%s' "$token"
}

urlencode() {
  jq -rn --arg value "$1" '$value | @uri'
}

timestamp_minutes_ago() {
  minutes="$1"
  epoch="$(date -u +%s)"
  date -u -d "@$((epoch - (minutes * 60)))" +%Y-%m-%dT%H:%M:%S 2>/dev/null || printf '%s' "$NOW"
}

echo "[seed] waiting for auth / found-items / lost-items ..."
wait_health auth
wait_health found-items
wait_health lost-items

ADMIN_TOKEN="$(login_token "$ADMIN_EMAIL" "$ADMIN_PASSWORD" "admin" || true)"
if [ -z "$ADMIN_TOKEN" ]; then
  echo "[seed] could not obtain an admin token - giving up (non-fatal)."
  exit 0
fi
ADMIN_AUTH="Authorization: Bearer $ADMIN_TOKEN"

ensure_staff_user() {
  encoded_email="$(urlencode "$STAFF_EMAIL")"
  existing="$(curl -fsS -H "$ADMIN_AUTH" "$GATEWAY/api/users/by-email?email=$encoded_email" 2>/dev/null || true)"
  user_id="$(printf '%s' "$existing" | jq -r '.id // empty' 2>/dev/null || true)"

  if [ -n "$user_id" ]; then
    curl -fsS -X PUT "$GATEWAY/api/users/$user_id" \
      -H "$ADMIN_AUTH" \
      -H 'Content-Type: application/json' \
      -d "$(jq -nc --arg email "$STAFF_EMAIL" '{email:$email,role:"STAFF"}')" \
      >/dev/null 2>&1 || true
    echo "[seed] staff account already present - $STAFF_EMAIL"
    return 0
  fi

  created="$(curl -fsS -X POST "$GATEWAY/api/users" \
    -H "$ADMIN_AUTH" \
    -H 'Content-Type: application/json' \
    -d "$(jq -nc \
      --arg email "$STAFF_EMAIL" \
      --arg password "$STAFF_PASSWORD" \
      --arg venueId "$VENUE_ID" \
      '{email:$email,role:"STAFF",password:$password,venueId:$venueId}')" \
    2>/dev/null || true)"
  user_id="$(printf '%s' "$created" | jq -r '.id // empty' 2>/dev/null || true)"
  if [ -n "$user_id" ]; then
    echo "[seed] created staff account - $STAFF_EMAIL / $STAFF_PASSWORD"
  else
    echo "[seed] could not create staff account - continuing with admin token."
  fi
}

ensure_staff_user

STAFF_TOKEN="$(login_token "$STAFF_EMAIL" "$STAFF_PASSWORD" "demo staff" || true)"
if [ -n "$STAFF_TOKEN" ]; then
  DATA_AUTH="Authorization: Bearer $STAFF_TOKEN"
  echo "[seed] demo data will be created as STAFF."
else
  DATA_AUTH="$ADMIN_AUTH"
  echo "[seed] staff login failed - seeding data with admin token instead."
fi

find_found_id() {
  text="$1"
  curl -fsS -H "$DATA_AUTH" "$GATEWAY/api/found-items" 2>/dev/null \
    | jq -r --arg text "$text" '.[] | select(.intakeText == $text) | .id' \
    | head -n 1
}

find_lost_id() {
  description="$1"
  curl -fsS -H "$DATA_AUTH" "$GATEWAY/api/lost-items" 2>/dev/null \
    | jq -r --arg description "$description" '.[] | select(.description == $description) | .id' \
    | head -n 1
}

upload_photo() {
  kind="$1"; id="$2"; photo="$3"; auth_header="$4"
  [ -f "$ASSETS/$photo" ] || return 0
  if [ -n "$auth_header" ]; then
    curl -fsS -X PUT "$GATEWAY/api/$kind/$id/photo" \
      -H "$auth_header" \
      -F "photo=@$ASSETS/$photo" >/dev/null 2>&1 || true
  else
    curl -fsS -X PUT "$GATEWAY/api/$kind/$id/photo" \
      -F "photo=@$ASSETS/$photo" >/dev/null 2>&1 || true
  fi
}

create_found() {
  text="$1"; photo="$2"; minutes="$3"
  found_at="$(timestamp_minutes_ago "$minutes")"
  id="$(find_found_id "$text")"

  if [ -n "$id" ]; then
    upload_photo "found-items" "$id" "$photo" "$DATA_AUTH"
    echo "[seed] found item already present - $text"
    return 0
  fi

  response="$(curl -fsS -X POST "$GATEWAY/api/found-items" -H "$DATA_AUTH" \
    -H 'Content-Type: application/json' \
    -d "$(jq -nc \
      --arg text "$text" \
      --arg foundAt "$found_at" \
      --arg venueId "$VENUE_ID" \
      '{intakeText:$text,foundAt:$foundAt,venueId:$venueId}')" \
    2>/dev/null || true)"

  id="$(printf '%s' "$response" | jq -r '.id // empty' 2>/dev/null || true)"
  [ -n "$id" ] || return 0
  upload_photo "found-items" "$id" "$photo" "$DATA_AUTH"
  echo "[seed] found item $id - $text"
}

create_lost() {
  description="$1"; location="$2"; contact="$3"; photo="$4"; minutes="$5"
  lost_at="$(timestamp_minutes_ago "$minutes")"
  id="$(find_lost_id "$description")"

  if [ -n "$id" ]; then
    upload_photo "lost-items" "$id" "$photo" ""
    echo "[seed] lost report already present - $description"
    return 0
  fi

  response="$(curl -fsS -X POST "$GATEWAY/api/lost-items" \
    -H 'Content-Type: application/json' \
    -d "$(jq -nc \
      --arg description "$description" \
      --arg lostAt "$lost_at" \
      --arg location "$location" \
      --arg venueId "$VENUE_ID" \
      --arg contactEmail "$contact" \
      '{description:$description,lostAt:$lostAt,location:$location,venueId:$venueId,contactEmail:$contactEmail}')" \
    2>/dev/null || true)"

  id="$(printf '%s' "$response" | jq -r '.id // empty' 2>/dev/null || true)"
  [ -n "$id" ] || return 0

  upload_photo "lost-items" "$id" "$photo" ""
  echo "[seed] lost report $id - $description"
}

create_found "Purple leather wallet found at the lobby bar." purple-wallet.jpg 90

create_lost "I lost my purple leather wallet near the main entrance." "Main entrance" "guest.wallet@example.com" purple-wallet.jpg 120

echo "[seed] done. Staff login: $STAFF_EMAIL / $STAFF_PASSWORD"
echo "[seed] the intake -> matching pipeline will produce demo matches shortly."
