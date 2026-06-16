#!/usr/bin/env bash
# Research-report engine demo — runs the full multi-agent pipeline end-to-end
# and prints the finished Markdown report.
#   ./financial/play-research.sh <ticker> [--real]
# Examples:
#   ./financial/play-research.sh DEMO            # offline: stub data + scripted model (no API key)
#   ./financial/play-research.sh DEMO --real     # env-driven (BANK_LLM_*, RESEARCH_DATA_BASE_URL, RESEARCH_REPORT_LIVE_MODEL)
set -euo pipefail

: "${JAVA_HOME:=/Library/Java/JavaVirtualMachines/openjdk-21.jdk/Contents/Home}"
export JAVA_HOME

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

# Filter the framework's own INFO/timestamped log chatter so only the report shows.
./mvnw -q -o -f financial/pom.xml \
  -Dexec.mainClass=com.bank.financial.research.ResearchReportPlayground \
  -Dexec.args="$*" \
  compile exec:java \
  | grep --line-buffered -vE '^[0-9]{4}-[0-9]{2}-[0-9]{2} |^[0-9]{2}:[0-9]{2}:[0-9]{2}|^SLF4J|logback'
