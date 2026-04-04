#!/bin/bash
# Setup Grafana Service Account and generate a token via docker exec.
# Usage: ./scripts/setup-grafana-sa.sh [CONTAINER_NAME] [ADMIN_USER] [ADMIN_PASSWORD]

set -e

CONTAINER="${1:-prices-grafana}"
ADMIN_USER="${2:-admin}"
ADMIN_PASSWORD="${3:-admin123}"
GRAFANA_URL="http://localhost:3000"
SA_NAME="prices-api"
TOKEN_NAME="prices-api-token"

run_curl() {
  docker exec "$CONTAINER" curl -s "$@"
}

echo "==> Container: $CONTAINER"
echo "==> Creating service account: $SA_NAME"

SA_RESPONSE=$(run_curl -X POST "$GRAFANA_URL/api/serviceaccounts" \
  -H "Content-Type: application/json" \
  -u "$ADMIN_USER:$ADMIN_PASSWORD" \
  -d "{\"name\":\"$SA_NAME\",\"role\":\"Viewer\"}")

SA_ID=$(echo "$SA_RESPONSE" | grep -o '"id":[0-9]*' | head -1 | cut -d: -f2)

if [ -z "$SA_ID" ]; then
  echo "Failed to create service account. Response:"
  echo "$SA_RESPONSE"
  echo ""
  echo "==> Checking if service account already exists..."
  SA_LIST=$(run_curl -X GET "$GRAFANA_URL/api/serviceaccounts/search?query=$SA_NAME" \
    -u "$ADMIN_USER:$ADMIN_PASSWORD")
  SA_ID=$(echo "$SA_LIST" | grep -o "\"id\":[0-9]*" | head -1 | cut -d: -f2)
  if [ -z "$SA_ID" ]; then
    echo "Could not find existing service account either. Exiting."
    exit 1
  fi
  echo "==> Found existing service account ID: $SA_ID"
fi

echo "==> Service account ID: $SA_ID"
echo "==> Generating token: $TOKEN_NAME"

TOKEN_RESPONSE=$(run_curl -X POST "$GRAFANA_URL/api/serviceaccounts/$SA_ID/tokens" \
  -H "Content-Type: application/json" \
  -u "$ADMIN_USER:$ADMIN_PASSWORD" \
  -d "{\"name\":\"$TOKEN_NAME\"}")

TOKEN=$(echo "$TOKEN_RESPONSE" | grep -o '"key":"[^"]*"' | cut -d'"' -f4)

if [ -z "$TOKEN" ]; then
  echo "Failed to generate token. Response:"
  echo "$TOKEN_RESPONSE"
  exit 1
fi

echo ""
echo "============================================"
echo "Service Account Token (save this!):"
echo "$TOKEN"
echo "============================================"
echo ""
echo "Add to your .env file:"
echo "GRAFANA_SERVICE_ACCOUNT_TOKEN=$TOKEN"
