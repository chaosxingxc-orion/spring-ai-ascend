#!/usr/bin/env bash
# Multi-agent collaboration eval harness.
#   ./collaboration/eval.sh            # generate the eval set + run it + report
#   ./collaboration/eval.sh generate   # (re)generate the eval set JSON only
#   ./collaboration/eval.sh run        # run the existing eval set JSON only
set -euo pipefail

: "${JAVA_HOME:=/Library/Java/JavaVirtualMachines/openjdk-21.jdk/Contents/Home}"
export JAVA_HOME

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

./mvnw -q -o -f collaboration/pom.xml \
  -Dexec.mainClass=com.huawei.ascend.collab.eval.EvalMain \
  -Dexec.args="$*" \
  compile exec:java
