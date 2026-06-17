#!/usr/bin/env bash
# One command to enable MemOpt as the persistent experience backend for A2A shared
# memory: brings up the MemOpt engine image + its NATS bus, then prints how to wire
# the Java kit to it. Reads ./.env (copy from .env.example first).
set -euo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"
cd "$HERE"

if [[ ! -f .env ]]; then
  echo "error: no .env here. Run:  cp .env.example .env  then set MEMOPT_IMAGE + GATEWAY_*." >&2
  exit 1
fi
set -a; . ./.env; set +a

PORT="${MEMOPT_PORT:-8077}"
echo "▶ bringing up MemOpt (${MEMOPT_IMAGE:-memopt:0.0.1}) + NATS ..."
docker compose up -d

echo "⏳ waiting for MemOpt /healthz on :${PORT} ..."
for _ in $(seq 1 30); do
  if curl -fsS "http://localhost:${PORT}/healthz" >/dev/null 2>&1; then
    echo "✅ MemOpt is up: http://localhost:${PORT}/healthz"
    echo
    echo "Wire the A2A shared-memory EXPERIENCE layer to it (Java):"
    echo "    ExperienceStore experience = new MemOptExperienceStore("
    echo "            \"http://<memopt-host>:${PORT}\","
    echo "            new MemOptExperienceStore.Options("
    echo "                    Duration.ofSeconds(2), /*failOpen*/ true, 5, 30_000L,"
    echo "                    System.getenv(\"MEMOPT_FACADE_AUTH_TOKEN\")));  // null => no auth header"
    echo
    echo "Then pass that store to the kit (see ../README.md). Tear down: docker compose down"
    exit 0
  fi
  sleep 2
done

echo "⚠️ MemOpt did not become healthy in time. Check logs:  docker compose logs memopt" >&2
exit 1
