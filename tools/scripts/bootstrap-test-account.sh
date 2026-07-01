#!/usr/bin/env bash
set -euo pipefail

API_URL="${FINMATE_API_URL:-http://localhost:8080}"
EMAIL="${FINMATE_TEST_EMAIL:-qa-birthday@finmate.local}"
PASSWORD="${FINMATE_TEST_PASSWORD:-password123!}"
DISPLAY_NAME="${FINMATE_TEST_DISPLAY_NAME:-테스트 사용자}"
INCLUDE_BIRTHDAY_EVENT="${FINMATE_INCLUDE_BIRTHDAY_EVENT:-true}"

curl -fsS -X POST "${API_URL}/api/dev/bootstrap-test-account" \
  -H "Content-Type: application/json" \
  -d "{
    \"email\": \"${EMAIL}\",
    \"password\": \"${PASSWORD}\",
    \"displayName\": \"${DISPLAY_NAME}\",
    \"includeBirthdayEvent\": ${INCLUDE_BIRTHDAY_EVENT}
  }" >/dev/null

echo "FinMate seeded test account ready: ${EMAIL} / ${PASSWORD}"
