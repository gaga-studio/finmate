#!/usr/bin/env bash
set -euo pipefail

API_URL="${FINMATE_API_URL:-http://localhost:8080}"

curl -fsS -X POST "${API_URL}/api/dev/reset" >/dev/null
echo "FinMate product DB reset complete: ${API_URL}"
