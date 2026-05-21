#!/usr/bin/env bash
# gate/lib/scan_cache.sh -- pre-scan shared file lists ONCE per gate run.
#
# Currently each gate rule independently invokes find(1) over the source tree.
# Several patterns repeat (e.g. find . -name module-metadata.yaml happens 5x).
# This file is sourced by the orchestrator before fanning out workers; it
# populates env vars listing every match for the common patterns. Workers then
# iterate the env var instead of re-running find.
#
# Authority: docs/governance/rules/rule-70.md (always-loaded budget) +
#            token-optimization wave Phase 2 / PR-E3.
#
# Patterns provided (subject to GATE_SCAN_CACHE_PATTERNS):
#   _SCAN_MODULE_METADATA   newline-separated paths to every module-metadata.yaml
#   _SCAN_ACTIVE_DOCS       newline-separated paths to every active *.md / *.yaml
#   _SCAN_MIGRATION_SQL     newline-separated paths to every Flyway V*.sql migration
#   _SCAN_AGENT_JAVA_MAIN   newline-separated paths to every agent-*/src/main/*.java
#   _SCAN_SHIPPED_ROWS      TSV from extract_shipped_rows.sh — one row per
#                           (capability, field, value); fields: shipped, impl,
#                           test, l2_doc, latest_delivery, tests_marker,
#                           tests_count. Consumed by Rules 7, 19, 24 (replaces
#                           their per-line printf|grep loops over the 1388-line
#                           architecture-status.yaml; saves ~25 min of CPU per
#                           gate run on Git Bash for Windows).
#   _SCAN_ENFORCERS_TSV     TSV from docs/governance/enforcers.yaml — one row
#                           per E-id; fields: e_id, artifact_path (with #anchor
#                           stripped), kind. Consumed by Rule 28k (~180-row
#                           awk-per-citation replaced with bash-array lookup,
#                           saves ~9-20s on gate run). PR-Opt-rc22.
#   _SCAN_RULE_CARDS        newline-separated paths to every docs/governance/rules/rule-*.md.
#                           Consumed by Rules 67, 68, 69, 70, 100. PR-Opt-rc22.
#   _SCAN_ADR_YAMLS         newline-separated paths to every docs/adr/*.yaml
#                           (excluding archive/locked). Consumed by Rules 28e,
#                           28f, 28i, 28j, 34, 62, 65, 83, 85. PR-Opt-rc22.
#   _SCAN_GIT_SHA           short HEAD sha. Consumed by Rules 64, 111.
#   _SCAN_GIT_LATEST_DATE   ISO date of HEAD commit. Consumed by Rules 64, 111.
#
# Each var is empty if GATE_SCAN_CACHE_ENABLED=false OR the pattern is not in
# GATE_SCAN_CACHE_PATTERNS. Consumers MUST handle the empty case.

set -uo pipefail
export LC_ALL=C

if [[ -z "${GATE_REPO_ROOT:-}" ]]; then
  GATE_REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
fi
cd "$GATE_REPO_ROOT"

gate_scan_cache_populate() {
  local _enabled="${GATE_SCAN_CACHE_ENABLED:-true}"
  local _patterns="${GATE_SCAN_CACHE_PATTERNS:-module_metadata active_docs migration_sql agent_java_main enforcers_tsv rule_cards adr_yamls git_metadata}"

  export _SCAN_MODULE_METADATA=""
  export _SCAN_ACTIVE_DOCS=""
  export _SCAN_MIGRATION_SQL=""
  export _SCAN_AGENT_JAVA_MAIN=""
  export _SCAN_SHIPPED_ROWS=""
  export _SCAN_ENFORCERS_TSV=""
  export _SCAN_RULE_CARDS=""
  export _SCAN_ADR_YAMLS=""
  export _SCAN_GIT_SHA=""
  export _SCAN_GIT_LATEST_DATE=""

  [[ "$_enabled" != "true" ]] && return 0

  if [[ " $_patterns " == *" module_metadata "* ]]; then
    _SCAN_MODULE_METADATA=$(find . -maxdepth 3 -name module-metadata.yaml \
      -not -path './target/*' \
      -not -path './.claude/*' \
      -not -path './.git/*' \
      2>/dev/null | sort)
  fi

  if [[ " $_patterns " == *" active_docs "* ]]; then
    _SCAN_ACTIVE_DOCS=$(find . \( -name '*.md' -o -name '*.yaml' -o -name '*.yml' \) \
      -not -path './target/*' \
      -not -path './.claude/*' \
      -not -path './.git/*' \
      -not -path './docs/archive/*' \
      -not -path './docs/v6-rationale/*' \
      -not -path './gate/log/*' \
      2>/dev/null | sort)
  fi

  if [[ " $_patterns " == *" migration_sql "* ]]; then
    _SCAN_MIGRATION_SQL=$(find . -path '*/src/main/resources/db/migration/V*.sql' \
      -not -path './target/*' \
      2>/dev/null | sort)
  fi

  if [[ " $_patterns " == *" agent_java_main "* ]]; then
    _SCAN_AGENT_JAVA_MAIN=$(find . -path '*/agent-*/src/main/java/*' -name '*.java' \
      -not -path './target/*' \
      2>/dev/null | sort)
  fi

  if [[ " $_patterns " == *" shipped_rows "* ]]; then
    if [[ -x "$GATE_REPO_ROOT/gate/lib/extract_shipped_rows.sh" ]]; then
      _SCAN_SHIPPED_ROWS=$(bash "$GATE_REPO_ROOT/gate/lib/extract_shipped_rows.sh" \
        "$GATE_REPO_ROOT/docs/governance/architecture-status.yaml" 2>/dev/null)
    fi
  fi

  # PR-Opt-rc22: pre-parse enforcers.yaml -> TSV (e_id \t artifact_path \t kind).
  # Eliminates Rule 28k's per-citation awk pass over the 3000-line file.
  if [[ " $_patterns " == *" enforcers_tsv "* ]]; then
    local _efile="$GATE_REPO_ROOT/docs/governance/enforcers.yaml"
    if [[ -f "$_efile" ]]; then
      _SCAN_ENFORCERS_TSV=$(awk '
        /^- id:[[:space:]]+E[0-9]+/ {
          if (eid != "") print eid "\t" art "\t" kind
          eid=$3; art=""; kind=""
          next
        }
        /^[[:space:]]+artifact:/ {
          line=$0
          sub(/^[[:space:]]+artifact:[[:space:]]*/, "", line)
          sub(/#.*$/, "", line)
          gsub(/[[:space:]]+$/, "", line)
          art=line
        }
        /^[[:space:]]+kind:/ {
          line=$0
          sub(/^[[:space:]]+kind:[[:space:]]*/, "", line)
          gsub(/[[:space:]]+$/, "", line)
          kind=line
        }
        END { if (eid != "") print eid "\t" art "\t" kind }
      ' "$_efile" 2>/dev/null)
    fi
  fi

  # PR-Opt-rc22: rule cards + ADR yaml file lists (5 rules each consume these).
  if [[ " $_patterns " == *" rule_cards "* ]]; then
    _SCAN_RULE_CARDS=$(find docs/governance/rules -maxdepth 1 -name 'rule-*.md' 2>/dev/null | sort)
  fi

  if [[ " $_patterns " == *" adr_yamls "* ]]; then
    _SCAN_ADR_YAMLS=$(find docs/adr -maxdepth 1 -name '*.yaml' \
      -not -path '*/archive/*' \
      -not -path '*/locked/*' \
      2>/dev/null | sort)
  fi

  # PR-Opt-rc22: git metadata cached once (avoids spawning git per-rule).
  if [[ " $_patterns " == *" git_metadata "* ]]; then
    _SCAN_GIT_SHA=$(git rev-parse --short HEAD 2>/dev/null || echo "unknown")
    _SCAN_GIT_LATEST_DATE=$(git log -1 --format=%cd --date=short 2>/dev/null || echo "unknown")
  fi

  export _SCAN_MODULE_METADATA _SCAN_ACTIVE_DOCS _SCAN_MIGRATION_SQL _SCAN_AGENT_JAVA_MAIN
  export _SCAN_SHIPPED_ROWS _SCAN_ENFORCERS_TSV _SCAN_RULE_CARDS _SCAN_ADR_YAMLS
  export _SCAN_GIT_SHA _SCAN_GIT_LATEST_DATE
}

# Auto-populate when sourced (consumers can also call manually for re-scan).
gate_scan_cache_populate
