#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"

start_stub() {
  local module="$1"
  local port="$2"
  local jar
  jar=$(ls "${module}"/target/*-SNAPSHOT.jar 2>/dev/null | head -n 1 || true)
  if [[ -z "${jar}" ]]; then
    echo "skip ${module}: jar not found (build stub module first)" >&2
    return 1
  fi
  java -jar "${jar}" --spring.profiles.active=stub --server.port="${port}" \
    >"/tmp/deep-research-${module}.log" 2>&1 &
  echo "started ${module} on ${port} (log: /tmp/deep-research-${module}.log)"
}

failed=0
start_stub agent-search-a2a 13004 || failed=1
start_stub agent-read-a2a 13005 || failed=1
start_stub agent-verify-a2a 13006 || failed=1

if [[ "${failed}" -ne 0 ]]; then
  echo "Some stubs were not started. Build B/C/D modules first, then re-run." >&2
  exit 1
fi

echo "stubs started: search=13004 read=13005 verify=13006"
