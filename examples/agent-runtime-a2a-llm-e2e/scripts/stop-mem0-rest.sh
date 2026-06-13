#!/usr/bin/env bash
set -euo pipefail

WORKDIR="${SAA_MEM0_WORKDIR:-/tmp/saa-mem0-rest}"
POSTGRES_CONTAINER="${SAA_MEM0_POSTGRES_CONTAINER:-saa-mem0-postgres}"
MEM0_CONTAINER="${SAA_MEM0_SERVER_CONTAINER:-saa-mem0-server}"
FAKE_OPENAI_PID_FILE="${WORKDIR}/fake-openai.pid"

docker rm -f "$MEM0_CONTAINER" "$POSTGRES_CONTAINER" >/dev/null 2>&1 || true
if [[ -f "$FAKE_OPENAI_PID_FILE" ]]; then
  kill "$(cat "$FAKE_OPENAI_PID_FILE")" >/dev/null 2>&1 || true
  rm -f "$FAKE_OPENAI_PID_FILE"
fi
echo "Stopped Mem0 REST dependency containers."
