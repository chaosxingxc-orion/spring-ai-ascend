#!/usr/bin/env bash
# W8 dogfood scenario 1: PRD draft from outline → prd-draft.md
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck disable=SC1091
source "$SCRIPT_DIR/_common.sh"

dogfood_load_env
require_llm
require_api

PROMPT_FILE="$WORKMATE_ROOT/office/prompts/prd-draft.md"
if [[ ! -f "$PROMPT_FILE" ]]; then
  echo "Missing $PROMPT_FILE" >&2
  exit 1
fi
PROMPT=$(cat "$PROMPT_FILE")

echo "Creating session..."
SESSION_JSON=$(create_session "W8 PRD draft")
SESSION_ID=$(echo "$SESSION_JSON" | json_field id)
WORKSPACE=$(echo "$SESSION_JSON" | json_field workspaceRoot)
echo "Session: $SESSION_ID"

echo "Sending PRD draft prompt..."
send_prompt "$SESSION_ID" "$PROMPT" | grep -E '^(event:|data:)' | head -60 || true

DRAFT="$WORKSPACE/prd-draft.md"
if [[ -f "$DRAFT" ]] && [[ $(wc -c < "$DRAFT") -gt 100 ]]; then
  echo "--- prd-draft.md (first 20 lines) ---"
  head -20 "$DRAFT"
  echo "OK: $DRAFT ($(wc -l < "$DRAFT") lines)"
else
  echo "FAIL: expected substantial $DRAFT" >&2
  exit 1
fi
