#!/usr/bin/env bash
# W2 dogfood: create session, prompt agent to write hello.md, verify file exists.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck disable=SC1091
source "$SCRIPT_DIR/_common.sh"

PROMPT="${1:-Create a file named hello.md with content: Hello from WorkMate}"

dogfood_load_env
require_llm
require_api

echo "Creating session..."
SESSION_JSON=$(create_session "W2 hello.md script")
SESSION_ID=$(echo "$SESSION_JSON" | json_field id)
WORKSPACE=$(echo "$SESSION_JSON" | json_field workspaceRoot)
echo "Session: $SESSION_ID"
echo "Workspace: $WORKSPACE"

echo "Sending prompt (SSE)..."
send_prompt "$SESSION_ID" "$PROMPT" | head -50 || true

HELLO="$WORKSPACE/hello.md"
if [[ -f "$HELLO" ]]; then
  echo "--- hello.md ---"
  cat "$HELLO"
  echo "OK: $HELLO exists"
else
  echo "FAIL: expected $HELLO" >&2
  exit 1
fi
