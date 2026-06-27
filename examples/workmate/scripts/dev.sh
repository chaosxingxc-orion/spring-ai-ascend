#!/usr/bin/env bash
# One command to bring up the full local stack: Postgres + API + UI.
# Ctrl-C stops the API and UI (Postgres keeps running; stop it with `make stop`).
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
API_PID=""

cleanup() {
  if [[ -n "$API_PID" ]] && kill -0 "$API_PID" 2>/dev/null; then
    echo ""
    echo "▶ Stopping workmate-api (pid $API_PID)…"
    kill "$API_PID" 2>/dev/null || true
    wait "$API_PID" 2>/dev/null || true
  fi
}
trap cleanup EXIT INT TERM

# 0) First-run convenience: create .env.local from the template if missing.
ENV_FILE="$ROOT/workmate-api/.env.local"
if [[ ! -f "$ENV_FILE" ]]; then
  cp "$ROOT/workmate-api/.env.local.example" "$ENV_FILE"
  echo "▶ Created workmate-api/.env.local from the template."
  echo "  Set WORKMATE_LLM_API_KEY in it before running an agent, then re-run."
fi

# 1) Postgres.
if command -v docker >/dev/null 2>&1; then
  echo "▶ Starting Postgres (docker compose)…"
  docker compose -f "$ROOT/docker/docker-compose.yml" up -d
else
  echo "⚠ docker not found — ensure Postgres is reachable on localhost:5432 (see docker/)." >&2
fi

# 2) Backend in the background.
echo "▶ Starting workmate-api on :8080 …"
"$ROOT/scripts/run-local.sh" &
API_PID=$!

# 3) Frontend in the foreground (Ctrl-C stops the whole stack).
echo "▶ Starting workmate-ui on http://localhost:5174 (proxies /api → :8080) …"
"$ROOT/scripts/dev-ui.sh"
