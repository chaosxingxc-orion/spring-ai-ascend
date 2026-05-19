#!/usr/bin/env bash
# gate/lib/run_rule.sh -- worker wrapper for a single gate rule.
#
# Invoked by the orchestrator (PR-E5) via:
#   bash gate/lib/run_rule.sh <rule_file>
# OR (legacy compat with check_parallel.sh):
#   bash gate/lib/run_rule.sh --slug <slug> --start <line> --end <line>
#
# Behaviour:
#   1. Source gate/lib/load_config.sh + gate/lib/common.sh + gate/lib/scan_cache.sh.
#   2. Source the rule file (or extract the rule body via line range).
#   3. Reset fail_count=0 in this subshell.
#   4. Time the rule body.
#   5. Pass / fail through stdout (backward-compat) AND NDJSON via log_jsonl.
#   6. Exit with rule's fail_count.
#
# Authority: docs/governance/rules/rule-70.md + token-optimization wave Phase 2.

set -uo pipefail
export LC_ALL=C

if [[ -z "${GATE_REPO_ROOT:-}" ]]; then
  GATE_REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
fi
cd "$GATE_REPO_ROOT"

# Load helpers (load_config is idempotent so safe even if orchestrator already ran).
source "$GATE_REPO_ROOT/gate/lib/common.sh"
if [[ -z "${GATE_PARALLELISM_JOBS:-}" ]]; then
  source "$GATE_REPO_ROOT/gate/lib/load_config.sh"
  gate_load_config 2>/dev/null || true
fi
source "$GATE_REPO_ROOT/gate/lib/scan_cache.sh"
# rc12 K-β: latest_release_path resolver (used by Rules 33/97 + Rule G-2.g).
if [[ -f "$GATE_REPO_ROOT/gate/lib/latest_release.sh" ]]; then
  # shellcheck source=latest_release.sh
  source "$GATE_REPO_ROOT/gate/lib/latest_release.sh"
fi

# Parse argv: either a rule file path, or --slug/--start/--end (legacy mode).
mode="file"
rule_file=""
rule_slug=""
rule_start=""
rule_end=""

while [[ $# -gt 0 ]]; do
  case "$1" in
    --slug)  rule_slug="$2"; mode="range"; shift 2 ;;
    --start) rule_start="$2"; shift 2 ;;
    --end)   rule_end="$2"; shift 2 ;;
    --)      shift; break ;;
    *)       rule_file="$1"; shift ;;
  esac
done

rule_number=""
if [[ "$mode" == "file" && -n "$rule_file" ]]; then
  if [[ ! -f "$rule_file" ]]; then
    echo "FAIL: run_rule -- rule file missing: $rule_file" >&2
    exit 1
  fi
  # Extract rule_number from filename: gate/rules/rule-NN.sh -> NN (with leading zeros stripped)
  base=$(basename "$rule_file" .sh)
  rule_number=$(printf '%s\n' "$base" | sed -nE 's/^rule-0*([0-9]+)([a-z]?)$/\1\2/p')
fi

T0=$(gate_now_ms)
fail_count=0

if [[ "$mode" == "file" ]]; then
  # Source the rule file -- it must define exactly one function called
  # rule_<NN>_<slug>. We discover and invoke that function.
  source "$rule_file"
  # Find the function name
  rule_func=$(declare -F | awk '/^declare -f rule_[0-9]+[a-z]?_/{print $3; exit}')
  if [[ -z "$rule_func" ]]; then
    echo "FAIL: run_rule -- no rule_NN_slug() function found in $rule_file" >&2
    exit 1
  fi
  # Invoke
  "$rule_func"
elif [[ "$mode" == "range" ]]; then
  # Legacy range mode (sourced body from the monolith). Used by old check_parallel.sh.
  monolith="$GATE_REPO_ROOT/gate/check_architecture_sync.sh"
  if [[ -z "$rule_start" || -z "$rule_end" || ! -f "$monolith" ]]; then
    echo "FAIL: run_rule -- range mode requires --start --end and a valid monolith" >&2
    exit 1
  fi
  body=$(sed -n "${rule_start},${rule_end}p" "$monolith")
  eval "$body"
else
  echo "FAIL: run_rule -- no rule specified" >&2
  exit 1
fi

T1=$(gate_now_ms)
export GATE_RULE_DURATION_MS=$((T1 - T0))

exit "$fail_count"
