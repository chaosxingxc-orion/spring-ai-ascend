#!/usr/bin/env bash
set -euo pipefail

CONTAINER_NAME="${SAA_SAMPLE_REDIS_CONTAINER_NAME:-saa-redis-checkpointer}"
docker rm -f "$CONTAINER_NAME" >/dev/null 2>&1 || true
echo "Stopped Redis checkpointer dependency: ${CONTAINER_NAME}"
