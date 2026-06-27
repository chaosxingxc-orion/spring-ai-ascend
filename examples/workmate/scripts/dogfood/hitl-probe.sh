#!/usr/bin/env bash
# W8 dogfood scenario 3: HITL probe — rm requires approval; deny keeps file.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck disable=SC1091
source "$SCRIPT_DIR/_common.sh"

MODE="${1:-deny}"

dogfood_load_env
require_llm
require_api

PROMPT_FILE="$WORKMATE_ROOT/office/prompts/hitl-probe.txt"
PROMPT=$(cat "$PROMPT_FILE")

echo "Creating session..."
SESSION_JSON=$(create_session "W8 HITL probe ($MODE)")
SESSION_ID=$(echo "$SESSION_JSON" | json_field id)
WORKSPACE=$(echo "$SESSION_JSON" | json_field workspaceRoot)
TARGET="$WORKSPACE/hitl-test.txt"
echo "Session: $SESSION_ID"
echo "Workspace: $WORKSPACE"

echo "HITL probe content" > "$TARGET"
echo "Seeded $TARGET"

echo "Sending rm prompt (background SSE)..."
SSE_LOG=$(mktemp)
(
  send_prompt "$SESSION_ID" "$PROMPT" > "$SSE_LOG" 2>&1 || true
) &
PROMPT_PID=$!

echo "Waiting for pending approval..."
if ! APPROVAL_ID=$(wait_for_pending_approval "$SESSION_ID" 120); then
  kill "$PROMPT_PID" 2>/dev/null || true
  wait "$PROMPT_PID" 2>/dev/null || true
  echo "FAIL: no pending approval within timeout" >&2
  echo "--- SSE log ---" >&2
  tail -30 "$SSE_LOG" >&2 || true
  rm -f "$SSE_LOG"
  exit 1
fi
echo "Approval: $APPROVAL_ID"

echo "Decision: $MODE"
decide_approval "$APPROVAL_ID" "$MODE" >/dev/null

wait "$PROMPT_PID" 2>/dev/null || true

if grep -q 'approval.required' "$SSE_LOG" 2>/dev/null; then
  echo "OK: SSE emitted approval.required"
else
  echo "WARN: approval.required not found in SSE log"
fi
rm -f "$SSE_LOG"

case "$MODE" in
  deny)
    if [[ -f "$TARGET" ]]; then
      echo "OK: file preserved after deny — $TARGET"
    else
      echo "FAIL: file deleted despite deny" >&2
      exit 1
    fi
    ;;
  approve)
    if [[ ! -f "$TARGET" ]]; then
      echo "OK: file removed after approve"
    else
      echo "FAIL: file still exists after approve" >&2
      exit 1
    fi
    ;;
  *)
    echo "Usage: $0 [deny|approve]" >&2
    exit 1
    ;;
esac
