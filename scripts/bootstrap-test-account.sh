#!/usr/bin/env bash
set -euo pipefail

API_URL="${FINMATE_API_URL:-http://localhost:8080}"
EMAIL="${FINMATE_TEST_EMAIL:-minjun@finmate.local}"
PASSWORD="${FINMATE_TEST_PASSWORD:-password123!}"
DISPLAY_NAME="${FINMATE_TEST_DISPLAY_NAME:-민준}"

response="$(
  curl -fsS -X POST "${API_URL}/api/auth/signup" \
    -H "Content-Type: application/json" \
    -d "{\"email\":\"${EMAIL}\",\"password\":\"${PASSWORD}\",\"displayName\":\"${DISPLAY_NAME}\"}"
)"

access_token="$(
  RESPONSE="${response}" python3 - <<'PY'
import json
import os

print(json.loads(os.environ["RESPONSE"])["accessToken"])
PY
)"

curl -fsS -X POST "${API_URL}/api/users/me/onboarding" \
  -H "Authorization: Bearer ${access_token}" \
  -H "Content-Type: application/json" \
  -d '{
    "ageBand": "20대 후반",
    "incomeBand": "3,000만원 ~ 4,000만원",
    "jobCategory": "IT/개발",
    "householdType": "1인가구",
    "moneyStyle": "안정 추구형",
    "area": "서울 강남권",
    "goalType": "EMERGENCY_FUND",
    "painPoint": "SAVE_CONSISTENTLY",
    "privacyConsent": {
      "anonymousPortfolioOptIn": true,
      "friendShareDefault": "MISSION_ONLY",
      "exposedFields": ["ageBand", "goalType", "financialSummary", "missionStatus"],
      "privacyConsentVersion": "privacy-v1.4"
    },
    "mydataConsent": {
      "mydataConsentVersion": "synthetic-mydata-v1.4",
      "mydataScopes": ["ACCOUNT_SUMMARY", "CARD_SPENDING", "INVESTMENT_SUMMARY"]
    }
  }' >/dev/null

echo "FinMate test account ready: ${EMAIL} / ${PASSWORD}"
