#!/usr/bin/env bash
# Start the Redis dependency used by the openJiuwen RedisCheckpointer path.
set -euo pipefail

CONTAINER_NAME="${SAA_SAMPLE_REDIS_CONTAINER_NAME:-saa-redis-checkpointer}"
IMAGE="${SAA_SAMPLE_REDIS_IMAGE:-docker.1panel.live/library/redis:7-alpine}"
PORT="${SAA_SAMPLE_REDIS_PORT:-6379}"

docker rm -f "$CONTAINER_NAME" >/dev/null 2>&1 || true
docker pull "$IMAGE"
docker run -d \
  --name "$CONTAINER_NAME" \
  -p "${PORT}:6379" \
  "$IMAGE" >/dev/null

for _ in $(seq 1 30); do
  if docker exec "$CONTAINER_NAME" redis-cli ping | grep -q PONG; then
    echo "Redis checkpointer dependency is ready: redis://localhost:${PORT}"
    exit 0
  fi
  sleep 1
done

docker logs "$CONTAINER_NAME" || true
echo "Redis did not become ready" >&2
exit 1
