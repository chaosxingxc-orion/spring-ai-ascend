#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
MVNW="$(cd "$ROOT/../.." && pwd)/mvnw"
ENV_FILE="$ROOT/workmate-api/.env.local"
cd "$ROOT/workmate-api"

if [[ -f "$ENV_FILE" ]]; then
  set -a
  # shellcheck disable=SC1090
  source "$ENV_FILE"
  set +a
  echo "Loaded $ENV_FILE"
fi

# W17: dev/prod unified Postgres. Ensure the local Postgres is up:
#   docker compose -f docker/docker-compose.yml up -d
# Override with WORKMATE_DB_URL / WORKMATE_DB_USERNAME / WORKMATE_DB_PASSWORD if needed.
if command -v pg_isready >/dev/null 2>&1; then
  if ! pg_isready -h "${WORKMATE_DB_HOST:-localhost}" -p "${WORKMATE_DB_PORT:-5432}" >/dev/null 2>&1; then
    echo "Warning: Postgres not reachable on ${WORKMATE_DB_HOST:-localhost}:${WORKMATE_DB_PORT:-5432}." >&2
    echo "Start it: docker compose -f docker/docker-compose.yml up -d" >&2
  fi
fi

# Flyway owns the schema (V1..). Hibernate validates against it.
"$MVNW" -q spring-boot:run "$@"
