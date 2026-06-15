#!/usr/bin/env bash
# Local agent playground — friendly dev loop with a readable trace.
#   ./financial/play.sh <agent-id|yaml-path> [--mock]
# Examples:
#   ./financial/play.sh credit-card-advisor --mock     # keyless, mock LLM
#   BANK_LLM_API_KEY=sk-... ./financial/play.sh credit-card-advisor
set -euo pipefail

: "${JAVA_HOME:=/Library/Java/JavaVirtualMachines/openjdk-21.jdk/Contents/Home}"
export JAVA_HOME

# repo root = parent of this script's dir
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

# The openJiuwen framework emits its own INFO logs; filter them so only the
# readable trace shows. Trace/banner lines never start with a timestamp.
./mvnw -q -o -f financial/pom.xml \
  -Dexec.mainClass=com.bank.financial.playground.Playground \
  -Dexec.args="$*" \
  compile exec:java \
  | grep --line-buffered -vE '^[0-9]{4}-[0-9]{2}-[0-9]{2} |^[0-9]{2}:[0-9]{2}:[0-9]{2}|^SLF4J|logback'
