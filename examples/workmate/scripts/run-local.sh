#!/usr/bin/env bash
# Load local secrets (gitignored) then start workmate-api.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ENV_FILE="$ROOT/workmate-api/.env.local"

if [[ -f "$ENV_FILE" ]]; then
  set -a
  # shellcheck disable=SC1090
  source "$ENV_FILE"
  set +a
  echo "Loaded $ENV_FILE"
else
  echo "Tip: run 'make setup' or copy workmate-api/.env.local.example to workmate-api/.env.local" >&2
fi

exec "$ROOT/scripts/dev-api.sh" "$@"
