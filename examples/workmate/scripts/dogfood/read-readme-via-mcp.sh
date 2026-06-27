#!/usr/bin/env bash
# W4 dogfood: MCP read README via agent, write summary.md into workspace.
# Requires WORKMATE_MCP_ENABLED=true in workmate-api/.env.local and API restart.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck disable=SC1091
source "$SCRIPT_DIR/_common.sh"

dogfood_load_env
require_llm
require_api
require_mcp

export WORKMATE_MCP_FS_ROOT="${WORKMATE_MCP_FS_ROOT:-$(cd "$WORKMATE_ROOT/documentation" && pwd)}"
echo "MCP docs root: $WORKMATE_MCP_FS_ROOT"

echo "Creating session..."
SESSION_JSON=$(create_session "W4 MCP readme summary")
SESSION_ID=$(echo "$SESSION_JSON" | json_field id)
WORKSPACE=$(echo "$SESSION_JSON" | json_field workspaceRoot)

echo "Registered MCP tools:"
curl -sf "$API/api/v1/mcp/tools" | python3 -m json.tool | head -40

PROMPT='Use MCP tools to read README.md from the mounted docs root, then write a short summary.md in the session workspace.'

echo "Sending prompt..."
send_prompt "$SESSION_ID" "$PROMPT" | head -80 || true

SUMMARY="$WORKSPACE/summary.md"
if [[ -f "$SUMMARY" ]]; then
  echo "--- summary.md ---"
  cat "$SUMMARY"
  echo "OK: $SUMMARY"
else
  echo "FAIL: expected $SUMMARY" >&2
  exit 1
fi
