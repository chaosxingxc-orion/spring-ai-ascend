#!/usr/bin/env bash
# Build and start a local Mem0 REST stack for the example Mem0RestMemoryProvider.
#
# The public mem0-api-server image may not be available for linux/amd64 in some
# environments, and Docker Hub access can be slow from mainland networks. This
# script builds the server from source, uses mirrored base images where possible,
# and starts a local fake OpenAI-compatible embedding endpoint for smoke tests.
set -euo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
WORKDIR="${SAA_MEM0_WORKDIR:-/tmp/saa-mem0-rest}"
MEM0_REPO="${SAA_MEM0_REPO_URL:-https://github.com/mem0ai/mem0.git}"
MEM0_REF="${SAA_MEM0_REF:-main}"
PYTHON_IMAGE_MIRROR="${SAA_MEM0_PYTHON_IMAGE:-docker.m.daocloud.io/library/python:3.12-slim}"
POSTGRES_BASE_IMAGE="${SAA_MEM0_POSTGRES_BASE_IMAGE:-docker.1ms.run/library/postgres:17}"
PGVECTOR_IMAGE="${SAA_MEM0_PGVECTOR_IMAGE:-saa-local/pgvector:pg17-cn}"
MEM0_IMAGE="${SAA_MEM0_SERVER_IMAGE:-saa-local/mem0-server:libpq}"
FORCE_BUILD="${SAA_MEM0_FORCE_BUILD:-false}"
POSTGRES_CONTAINER="${SAA_MEM0_POSTGRES_CONTAINER:-saa-mem0-postgres}"
MEM0_CONTAINER="${SAA_MEM0_SERVER_CONTAINER:-saa-mem0-server}"
POSTGRES_PORT="${SAA_MEM0_POSTGRES_PORT:-8432}"
MEM0_PORT="${SAA_MEM0_PORT:-8000}"
FAKE_OPENAI_PORT="${SAA_MEM0_FAKE_OPENAI_PORT:-18080}"
FAKE_OPENAI_PID_FILE="${WORKDIR}/fake-openai.pid"
FAKE_OPENAI_LOG="${WORKDIR}/fake-openai.log"

mkdir -p "$WORKDIR"

if [[ ! -d "${WORKDIR}/mem0/.git" ]]; then
  git clone --depth 1 "$MEM0_REPO" "${WORKDIR}/mem0"
fi
git -C "${WORKDIR}/mem0" fetch --depth 1 origin "$MEM0_REF" >/dev/null 2>&1 || true
git -C "${WORKDIR}/mem0" checkout "$MEM0_REF" >/dev/null 2>&1 || true

if ! docker image inspect python:3.12-slim >/dev/null 2>&1; then
  docker pull --platform linux/amd64 "$PYTHON_IMAGE_MIRROR"
  docker tag "$PYTHON_IMAGE_MIRROR" python:3.12-slim
fi

if [[ "$FORCE_BUILD" == "true" || "$FORCE_BUILD" == "1" || "$FORCE_BUILD" == "yes" ]] \
  || ! docker image inspect "$PGVECTOR_IMAGE" >/dev/null 2>&1; then
  cat >"${WORKDIR}/Dockerfile.pgvector17-cn" <<EOF
FROM ${POSTGRES_BASE_IMAGE}
USER root
RUN apt-get update \\
    && apt-get install -y --no-install-recommends postgresql-17-pgvector \\
    && rm -rf /var/lib/apt/lists/*
USER postgres
EOF
  docker build --platform linux/amd64 \
    -t "$PGVECTOR_IMAGE" \
    -f "${WORKDIR}/Dockerfile.pgvector17-cn" \
    "$WORKDIR"
else
  echo "Reusing existing pgvector image: $PGVECTOR_IMAGE"
fi

if [[ "$FORCE_BUILD" == "true" || "$FORCE_BUILD" == "1" || "$FORCE_BUILD" == "yes" ]] \
  || ! docker image inspect "$MEM0_IMAGE" >/dev/null 2>&1; then
  cat >"${WORKDIR}/Dockerfile.mem0-cn-libpq" <<'EOF'
FROM python:3.12-slim
ENV PIP_INDEX_URL=https://pypi.tuna.tsinghua.edu.cn/simple \
    PIP_TRUSTED_HOST=pypi.tuna.tsinghua.edu.cn \
    PIP_DEFAULT_TIMEOUT=90
RUN apt-get update \
    && apt-get install -y --no-install-recommends libpq5 \
    && rm -rf /var/lib/apt/lists/*
WORKDIR /app
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt
COPY . .
EXPOSE 8000
ENV PYTHONUNBUFFERED=1
CMD ["uvicorn", "main:app", "--host", "0.0.0.0", "--port", "8000", "--reload"]
EOF
  docker build --platform linux/amd64 \
    -t "$MEM0_IMAGE" \
    -f "${WORKDIR}/Dockerfile.mem0-cn-libpq" \
    "${WORKDIR}/mem0/server"
else
  echo "Reusing existing Mem0 server image: $MEM0_IMAGE"
fi

if [[ -z "${SAA_MEM0_OPENAI_BASE_URL:-}" ]]; then
  if [[ -f "$FAKE_OPENAI_PID_FILE" ]]; then
    kill "$(cat "$FAKE_OPENAI_PID_FILE")" >/dev/null 2>&1 || true
    rm -f "$FAKE_OPENAI_PID_FILE"
  fi
  nohup python3 "${HERE}/scripts/fake-openai-compatible.py" \
    --port "$FAKE_OPENAI_PORT" >"$FAKE_OPENAI_LOG" 2>&1 &
  echo "$!" >"$FAKE_OPENAI_PID_FILE"
  export SAA_MEM0_OPENAI_BASE_URL="http://host.docker.internal:${FAKE_OPENAI_PORT}/v1"
fi

docker rm -f "$MEM0_CONTAINER" "$POSTGRES_CONTAINER" >/dev/null 2>&1 || true
docker run -d \
  --name "$POSTGRES_CONTAINER" \
  --platform linux/amd64 \
  -e POSTGRES_USER=postgres \
  -e POSTGRES_PASSWORD=postgres \
  -e POSTGRES_DB=postgres \
  -p "${POSTGRES_PORT}:5432" \
  "$PGVECTOR_IMAGE" >/dev/null

for _ in $(seq 1 30); do
  if docker exec "$POSTGRES_CONTAINER" pg_isready -U postgres >/dev/null 2>&1; then
    break
  fi
  sleep 1
done
docker exec "$POSTGRES_CONTAINER" createdb -U postgres mem0_app >/dev/null 2>&1 || true

docker run -d \
  --name "$MEM0_CONTAINER" \
  --platform linux/amd64 \
  --add-host host.docker.internal:host-gateway \
  --link "${POSTGRES_CONTAINER}:postgres" \
  -e AUTH_DISABLED=true \
  -e JWT_SECRET=local-dev-secret \
  -e POSTGRES_HOST=postgres \
  -e POSTGRES_PORT=5432 \
  -e POSTGRES_DB=postgres \
  -e POSTGRES_USER=postgres \
  -e POSTGRES_PASSWORD=postgres \
  -e APP_DB_NAME=mem0_app \
  -e OPENAI_API_KEY="${SAA_MEM0_OPENAI_API_KEY:-dummy}" \
  -e OPENAI_BASE_URL="$SAA_MEM0_OPENAI_BASE_URL" \
  -e MEM0_TELEMETRY=false \
  -e HISTORY_DB_PATH=/tmp/history.db \
  -p "${MEM0_PORT}:8000" \
  "$MEM0_IMAGE" >/dev/null

for _ in $(seq 1 60); do
  if curl -fsS "http://localhost:${MEM0_PORT}/openapi.json" >/dev/null 2>&1; then
    break
  fi
  sleep 2
done

docker exec "$MEM0_CONTAINER" alembic upgrade head

curl -fsS "http://localhost:${MEM0_PORT}/memories" \
  -H 'Content-Type: application/json' \
  -d '{"messages":[{"role":"user","content":"remember ping means pong"}],"user_id":"saa-test-user","infer":false}' >/dev/null

curl -fsS "http://localhost:${MEM0_PORT}/search" \
  -H 'Content-Type: application/json' \
  -d '{"query":"ping","filters":{"user_id":"saa-test-user"},"top_k":3}' >/dev/null

cat <<EOF
Mem0 REST dependency is ready:
  base URL: http://localhost:${MEM0_PORT}
  api mode: oss
  provider env:
    export SAA_SAMPLE_MEMORY_PROVIDER=mem0
    export SAA_SAMPLE_MEM0_BASE_URL=http://localhost:${MEM0_PORT}
    export SAA_SAMPLE_MEM0_API_MODE=oss
    export SAA_SAMPLE_MEM0_INFER_ON_SAVE=false
EOF
