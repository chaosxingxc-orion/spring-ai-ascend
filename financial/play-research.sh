#!/usr/bin/env bash
# Research-report engine CLI demo — runs the thematic / sector-strategy pipeline
# end-to-end and prints the finished Markdown report. (Fund / bond reports are
# demoed via the web playground: ./financial/play-web.sh)
#   ./financial/play-research.sh "中国 TMT"          # offline: scenario stub + scripted model
#   ./financial/play-research.sh "中国 TMT" --real    # env-driven live model (BANK_LLM_*, RESEARCH_REPORT_LIVE_MODEL)
set -euo pipefail

: "${JAVA_HOME:=/Library/Java/JavaVirtualMachines/openjdk-21.jdk/Contents/Home}"
export JAVA_HOME

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

# The bank's product set has no single-stock equity coverage; the CLI demo runs the
# sector-strategy (thematic) engine. (--thematic kept as a no-op alias for compat.)
MAIN=com.bank.financial.research.ResearchReportPlaygroundThematic
ARGS="${*/--thematic/}"

# Filter the framework's own INFO/timestamped log chatter so only the report shows.
./mvnw -q -o -f financial/pom.xml \
  -Dexec.mainClass="$MAIN" \
  -Dexec.args="$ARGS" \
  compile exec:java \
  | grep --line-buffered -vE '^[0-9]{4}-[0-9]{2}-[0-9]{2} |^[0-9]{2}:[0-9]{2}:[0-9]{2}|^SLF4J|logback'
