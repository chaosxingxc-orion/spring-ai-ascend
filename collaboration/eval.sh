#!/usr/bin/env bash
# Multi-agent collaboration eval harness.
#   ./collaboration/eval.sh            # generate the eval set + run it + report
#   ./collaboration/eval.sh generate   # (re)generate the eval set JSON only
#   ./collaboration/eval.sh run        # run the existing eval set JSON only
#
# Env:
#   JAVA_HOME         optional; if unset, the JDK on PATH is used (Java 21 required).
#   SAA_EVAL_OFFLINE  "true" => run Maven offline (-o). Default online so a clean
#                     checkout can fetch the exec plugin on first run (WSL/Linux-safe).
set -euo pipefail

# Java 21 required. Honor an explicit JAVA_HOME; otherwise use the JDK on PATH.
# No hardcoded OS-specific JAVA_HOME (CLAUDE.md Rule G-7 — Linux-first).
if [[ -z "${JAVA_HOME:-}" ]] && ! command -v java >/dev/null 2>&1; then
  echo "error: no Java found — set JAVA_HOME to a JDK 21, or put 'java' (21) on PATH." >&2
  exit 1
fi
[[ -n "${JAVA_HOME:-}" ]] && export JAVA_HOME

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

# Offline is opt-in: a clean WSL/Linux checkout must be able to fetch the
# exec-maven-plugin on first run; forcing -o would fail with an offline-cache error.
OFFLINE=""
if [[ "${SAA_EVAL_OFFLINE:-false}" == "true" ]]; then
  OFFLINE="-o"
fi

./mvnw -q ${OFFLINE} -f collaboration/pom.xml \
  -Dexec.mainClass=com.huawei.ascend.collab.eval.EvalMain \
  -Dexec.args="$*" \
  compile exec:java
