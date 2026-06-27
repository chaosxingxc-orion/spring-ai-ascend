#!/usr/bin/env bash
# W31 dogfood: audit hash chain verify (live API). Offline tamper fixture: test_dogfood_audit_chain_validate.py
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck disable=SC1091
source "$SCRIPT_DIR/_common.sh"

dogfood_load_env
require_api

echo "=== W31 audit chain verify (live) ==="

VERIFY_JSON=$(curl -sf "$API/api/v1/admin/audit/verify")
echo "$VERIFY_JSON" | python3 -m json.tool

OK=$(echo "$VERIFY_JSON" | python3 -c "import sys,json; print('true' if json.load(sys.stdin).get('ok') else 'false')")
if [[ "$OK" != "true" ]]; then
  echo "FAIL: audit chain verify returned ok=false" >&2
  exit 1
fi

VERIFIED=$(echo "$VERIFY_JSON" | python3 -c "import sys,json; print(json.load(sys.stdin).get('verifiedThroughSeq', 0))")
echo "OK: chain verified through seq=$VERIFIED"

ENTRIES=$(curl -sf "$API/api/v1/admin/audit/entries?limit=1")
COUNT=$(echo "$ENTRIES" | python3 -c "import sys,json; print(len(json.load(sys.stdin).get('entries', [])))")

if [[ "$COUNT" -eq 0 ]] && [[ "${DOGFOOD_AUDIT_SEED:-}" == "1" ]]; then
  echo "Chain empty — seeding via write-hello (requires LLM)..."
  require_llm
  "$SCRIPT_DIR/write-hello.sh" "Audit chain seed"
  VERIFY_JSON=$(curl -sf "$API/api/v1/admin/audit/verify")
  OK=$(echo "$VERIFY_JSON" | python3 -c "import sys,json; print('true' if json.load(sys.stdin).get('ok') else 'false')")
  if [[ "$OK" != "true" ]]; then
    echo "FAIL: verify after seed" >&2
    exit 1
  fi
  echo "OK: seeded chain still verifies"
fi

echo "=== offline tamper fixture ==="
python3 "$SCRIPT_DIR/test_dogfood_audit_chain_validate.py"

echo "=== W31 audit chain dogfood passed ==="
