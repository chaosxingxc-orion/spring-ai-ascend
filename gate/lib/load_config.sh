#!/usr/bin/env bash
# gate/lib/load_config.sh -- single-source config loader for the gate.
#
# Reads gate/config.yaml, applies env-var overrides, validates the merged
# state against gate/config.schema.yaml, and exports namespaced env vars
# consumed by the orchestrator + workers.
#
# Override hierarchy (highest -> lowest):
#   1. Env var (e.g. GATE_JOBS=4)
#   2. gate/config.yaml
#   3. Hardcoded fallback in this script
#
# Authority: docs/governance/rules/rule-73.md + token-optimization wave Phase 2.
# Sourced by: gate/lib/orchestrator.sh, gate/check_architecture_sync.sh (PR-E5),
# and gate Rule 73 (via gate/rules/rule-73.sh once extracted).
#
# Exit codes when sourced via `gate_load_config`:
#   0 -- config loaded successfully (or fallback to defaults with warning)
#   1 -- malformed YAML or schema validation failure (fail closed)
#
# Exported env vars (all prefixed GATE_):
#   GATE_PARALLELISM_JOBS                       int (resolved: 0 -> nproc)
#   GATE_PARALLELISM_ENABLED                    "true" | "false"
#   GATE_PARALLELISM_RULE_TIMEOUT_SECONDS       int
#   GATE_PARALLELISM_BATCH_STRATEGY             "round_robin" | "longest_first"
#   GATE_LOGGING_NDJSON_ENABLED                 "true" | "false"
#   GATE_LOGGING_SUMMARY_ENABLED                "true" | "false"
#   GATE_LOGGING_STDOUT_FORMAT                  "human" | "quiet" | "json"
#   GATE_LOGGING_RETENTION_MAX_RUNS             int
#   GATE_LOGGING_RETENTION_AUTO_PRUNE           "true" | "false"
#   GATE_LOGGING_PROFILE_MODE                   "true" | "false"
#   GATE_SCAN_CACHE_ENABLED                     "true" | "false"
#   GATE_SCAN_CACHE_PATTERNS                    space-separated list
#   GATE_REGRESSION_DETECTION_ENABLED           "true" | "false"
#   GATE_REGRESSION_DETECTION_MULTIPLIER_THRESHOLD  float (as string)
#   GATE_REGRESSION_DETECTION_ABSOLUTE_MIN_MS   int
#   GATE_REGRESSION_DETECTION_BASELINE_WINDOW   int
#   GATE_RULE_FILTERS_SKIP                      space-separated list of rule numbers
#   GATE_RULE_FILTERS_ONLY                      space-separated list of rule numbers
#   GATE_CONFIG_VALID                           "true" | "false" (for Rule 73)
#   GATE_CONFIG_ERRORS                          newline-separated error messages
#
# Usage:
#   source gate/lib/load_config.sh
#   gate_load_config                       # populates GATE_* vars
#   gate_validate_config_against_schema    # populates GATE_CONFIG_VALID + GATE_CONFIG_ERRORS

set -uo pipefail
export LC_ALL=C

# ---------------------------------------------------------------------------
# Resolve repo root regardless of where this script is sourced from.
# ---------------------------------------------------------------------------
if [[ -z "${GATE_REPO_ROOT:-}" ]]; then
  GATE_REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
  export GATE_REPO_ROOT
fi

# ---------------------------------------------------------------------------
# Hardcoded fallbacks (apply if gate/config.yaml is missing or unreadable).
# These mirror the documented defaults in gate/config.yaml.
# ---------------------------------------------------------------------------
_gate_apply_hardcoded_defaults() {
  # Values are kept in lockstep with gate/config.yaml so a missing or
  # malformed config does not silently regress the per-rule timeout
  # (Rules 26 + 115 need >60s on the current corpus) or starve the
  # gate-wide ceiling. Drift between the two surfaces would make
  # CI fail for orthogonal reasons whenever config.yaml is unreachable.
  export GATE_PARALLELISM_JOBS=8
  export GATE_PARALLELISM_ENABLED=true
  export GATE_PARALLELISM_RULE_TIMEOUT_SECONDS=300
  export GATE_PARALLELISM_TOTAL_TIMEOUT_SECONDS=900
  export GATE_PARALLELISM_BATCH_STRATEGY=round_robin
  export GATE_LOGGING_NDJSON_ENABLED=true
  export GATE_LOGGING_SUMMARY_ENABLED=true
  export GATE_LOGGING_STDOUT_FORMAT=human
  export GATE_LOGGING_RETENTION_MAX_RUNS=100
  export GATE_LOGGING_RETENTION_AUTO_PRUNE=true
  export GATE_LOGGING_PROFILE_MODE=false
  export GATE_SCAN_CACHE_ENABLED=true
  export GATE_SCAN_CACHE_PATTERNS="module_metadata active_docs migration_sql agent_java_main"
  export GATE_REGRESSION_DETECTION_ENABLED=true
  export GATE_REGRESSION_DETECTION_MULTIPLIER_THRESHOLD=2.0
  export GATE_REGRESSION_DETECTION_ABSOLUTE_MIN_MS=200
  export GATE_REGRESSION_DETECTION_BASELINE_WINDOW=5
  export GATE_RULE_FILTERS_SKIP=""
  export GATE_RULE_FILTERS_ONLY=""
}

# ---------------------------------------------------------------------------
# Pure-bash YAML parser for the constrained format we use.
# Reads a YAML file and emits one line per leaf: "<dotted.path>=<value>".
# Limitations:
#   - 2-space indent only
#   - block-style arrays only (single-line "[]" allowed for empty)
#   - scalar values only (int, bool, string -- no nested maps in scalars)
#   - no anchors, no merges, no flow style
# ---------------------------------------------------------------------------
_gate_parse_yaml() {
  local _file="$1"
  if [[ ! -f "$_file" ]]; then
    return 1
  fi
  awk '
    BEGIN { depth = 0; split("", path); array_key = "" }
    {
      raw = $0
      # Detect indent depth (count leading spaces / 2)
      indent_str = raw
      sub(/[^ ].*/, "", indent_str)
      cur_depth = int(length(indent_str) / 2)
      # Strip leading whitespace, inline comments, trailing whitespace
      line = raw
      sub(/^[[:space:]]+/, "", line)
      sub(/[[:space:]]+#.*$/, "", line)
      sub(/[[:space:]]+$/, "", line)
      # Skip blank / comment-only lines
      if (line == "" || substr(line, 1, 1) == "#") next

      # Array item: starts with "- "
      if (substr(line, 1, 2) == "- ") {
        item = substr(line, 3)
        gsub(/^"/, "", item); gsub(/"$/, "", item)
        gsub(/^'\''/, "", item); gsub(/'\''$/, "", item)
        if (array_key != "") {
          printf "%s[]=%s\n", array_key, item
        }
        next
      }

      # Map key: matches "key:" or "key: value" with valid identifier
      if (match(line, /^[a-zA-Z_][a-zA-Z_0-9]*:/)) {
        while (depth > cur_depth) {
          delete path[depth]
          depth--
        }
        colon = index(line, ":")
        key = substr(line, 1, colon - 1)
        value = substr(line, colon + 1)
        sub(/^[[:space:]]+/, "", value)
        sub(/[[:space:]]+$/, "", value)
        path[cur_depth] = key
        depth = cur_depth + 1
        full = ""
        for (i = 0; i < depth; i++) {
          if (i > 0) full = full "."
          full = full path[i]
        }
        if (value == "") {
          array_key = full
        } else if (value == "[]") {
          printf "%s[]=\n", full
          array_key = ""
        } else if (substr(value, 1, 1) == "[") {
          printf "__ERROR__=flow-style array at line %d: %s\n", NR, raw
        } else {
          gsub(/^"/, "", value); gsub(/"$/, "", value)
          gsub(/^'\''/, "", value); gsub(/'\''$/, "", value)
          printf "%s=%s\n", full, value
          array_key = ""
        }
      }
    }
  ' "$_file"
}

# ---------------------------------------------------------------------------
# Apply parsed YAML to GATE_* env vars.
# ---------------------------------------------------------------------------
_gate_apply_yaml() {
  local _kv_lines="$1"
  local _line _path _value _envvar
  while IFS= read -r _line; do
    [[ -z "$_line" ]] && continue
    # Drop array-item markers; collect separately below
    if [[ "$_line" == *"[]="* ]]; then continue; fi
    _path="${_line%%=*}"
    _value="${_line#*=}"
    # Convert dotted path to upper-case env var name
    _envvar="GATE_$(printf '%s' "$_path" | tr '.[:lower:]' '_[:upper:]')"
    # Special: GATE_PARALLELISM_JOBS=0 means "auto"; resolve at consumer time
    export "$_envvar=$_value"
  done <<< "$_kv_lines"

  # Collect array values
  local _array_key _array_val
  declare -A _gate_arrays=()
  while IFS= read -r _line; do
    [[ -z "$_line" ]] && continue
    if [[ "$_line" != *"[]="* ]]; then continue; fi
    _array_key="${_line%%\[\]=*}"
    _array_val="${_line#*[]=}"
    _envvar="GATE_$(printf '%s' "$_array_key" | tr '.[:lower:]' '_[:upper:]')"
    if [[ -z "${_gate_arrays[$_envvar]:-}" ]]; then
      _gate_arrays[$_envvar]="$_array_val"
    else
      _gate_arrays[$_envvar]="${_gate_arrays[$_envvar]} $_array_val"
    fi
  done <<< "$_kv_lines"

  # Export collected arrays as space-separated strings
  for _envvar in "${!_gate_arrays[@]}"; do
    export "$_envvar=${_gate_arrays[$_envvar]}"
  done

  # Ensure array env vars exist (empty) even if not set in YAML
  : "${GATE_SCAN_CACHE_PATTERNS:=}"
  : "${GATE_RULE_FILTERS_SKIP:=}"
  : "${GATE_RULE_FILTERS_ONLY:=}"
  export GATE_SCAN_CACHE_PATTERNS GATE_RULE_FILTERS_SKIP GATE_RULE_FILTERS_ONLY
}

# ---------------------------------------------------------------------------
# Apply env-var overrides (env vars set BEFORE this script was sourced win).
# These map short user-facing env names (e.g. GATE_JOBS) to the canonical
# namespaced env vars (e.g. GATE_PARALLELISM_JOBS).
# ---------------------------------------------------------------------------
_gate_apply_env_overrides() {
  [[ -n "${GATE_JOBS:-}" ]]                   && export GATE_PARALLELISM_JOBS="$GATE_JOBS"
  [[ -n "${GATE_PARALLEL:-}" ]]               && export GATE_PARALLELISM_ENABLED=$([[ "$GATE_PARALLEL" == "0" || "$GATE_PARALLEL" == "false" ]] && echo "false" || echo "true")
  [[ -n "${GATE_RULE_TIMEOUT:-}" ]]           && export GATE_PARALLELISM_RULE_TIMEOUT_SECONDS="$GATE_RULE_TIMEOUT"
  [[ -n "${GATE_BATCH_STRATEGY:-}" ]]         && export GATE_PARALLELISM_BATCH_STRATEGY="$GATE_BATCH_STRATEGY"
  [[ -n "${GATE_LOG_NDJSON:-}" ]]             && export GATE_LOGGING_NDJSON_ENABLED="$GATE_LOG_NDJSON"
  [[ -n "${GATE_LOG_SUMMARY:-}" ]]            && export GATE_LOGGING_SUMMARY_ENABLED="$GATE_LOG_SUMMARY"
  [[ -n "${GATE_LOG_STDOUT:-}" ]]             && export GATE_LOGGING_STDOUT_FORMAT="$GATE_LOG_STDOUT"
  [[ -n "${GATE_LOG_MAX_RUNS:-}" ]]           && export GATE_LOGGING_RETENTION_MAX_RUNS="$GATE_LOG_MAX_RUNS"
  [[ -n "${GATE_LOG_AUTO_PRUNE:-}" ]]         && export GATE_LOGGING_RETENTION_AUTO_PRUNE="$GATE_LOG_AUTO_PRUNE"
  [[ -n "${GATE_PROFILE:-}" ]]                && export GATE_LOGGING_PROFILE_MODE=$([[ "$GATE_PROFILE" == "1" || "$GATE_PROFILE" == "true" ]] && echo "true" || echo "false")
  [[ -n "${GATE_SCAN_CACHE:-}" ]]             && export GATE_SCAN_CACHE_ENABLED="$GATE_SCAN_CACHE"
  [[ -n "${GATE_RULE_72:-}" ]]                && export GATE_REGRESSION_DETECTION_ENABLED="$GATE_RULE_72"
  [[ -n "${GATE_REGRESSION_MULTIPLIER:-}" ]]  && export GATE_REGRESSION_DETECTION_MULTIPLIER_THRESHOLD="$GATE_REGRESSION_MULTIPLIER"
  [[ -n "${GATE_REGRESSION_MIN_MS:-}" ]]      && export GATE_REGRESSION_DETECTION_ABSOLUTE_MIN_MS="$GATE_REGRESSION_MIN_MS"
  [[ -n "${GATE_BASELINE_WINDOW:-}" ]]        && export GATE_REGRESSION_DETECTION_BASELINE_WINDOW="$GATE_BASELINE_WINDOW"
  [[ -n "${GATE_SKIP:-}" ]]                   && export GATE_RULE_FILTERS_SKIP="$(printf '%s' "$GATE_SKIP" | tr ',' ' ')"
  [[ -n "${GATE_ONLY:-}" ]]                   && export GATE_RULE_FILTERS_ONLY="$(printf '%s' "$GATE_ONLY" | tr ',' ' ')"
}

# ---------------------------------------------------------------------------
# Resolve special values (jobs=0 -> nproc).
# ---------------------------------------------------------------------------
_gate_resolve_specials() {
  if [[ "${GATE_PARALLELISM_JOBS:-8}" == "0" ]]; then
    local _ncpu
    if command -v nproc >/dev/null 2>&1; then
      _ncpu=$(nproc 2>/dev/null || echo 8)
    elif [[ -f /proc/cpuinfo ]]; then
      _ncpu=$(grep -c ^processor /proc/cpuinfo 2>/dev/null || echo 8)
    else
      _ncpu=8
    fi
    export GATE_PARALLELISM_JOBS="$_ncpu"
  fi
  # If parallelism disabled, force jobs=1
  if [[ "${GATE_PARALLELISM_ENABLED:-true}" == "false" ]]; then
    export GATE_PARALLELISM_JOBS=1
  fi
}

# ---------------------------------------------------------------------------
# Top-level: load config end-to-end.
# ---------------------------------------------------------------------------
gate_load_config() {
  local _config="${GATE_REPO_ROOT}/gate/config.yaml"
  _gate_apply_hardcoded_defaults
  if [[ -f "$_config" ]]; then
    local _kv
    _kv=$(_gate_parse_yaml "$_config")
    if [[ -n "$_kv" ]] && ! printf '%s\n' "$_kv" | grep -q '^__ERROR__='; then
      _gate_apply_yaml "$_kv"
    else
      echo "WARN: gate/config.yaml malformed; using hardcoded defaults" >&2
      printf '%s\n' "$_kv" | grep '^__ERROR__=' >&2 || true
    fi
  else
    echo "WARN: gate/config.yaml missing; using hardcoded defaults" >&2
  fi
  _gate_apply_env_overrides
  _gate_resolve_specials
}

# ---------------------------------------------------------------------------
# Schema validation. Run AFTER gate_load_config.
# Sets GATE_CONFIG_VALID and GATE_CONFIG_ERRORS.
# Returns 0 if valid, 1 if any error.
# ---------------------------------------------------------------------------
gate_validate_config_against_schema() {
  local _schema="${GATE_REPO_ROOT}/gate/config.schema.yaml"
  local _config="${GATE_REPO_ROOT}/gate/config.yaml"
  local _errors=""

  if [[ ! -f "$_schema" ]]; then
    _errors="schema file missing: $_schema"
  elif [[ ! -f "$_config" ]]; then
    _errors="config file missing: $_config"
  else
    # Minimal validation: check required top-level keys are present in config.
    local _required_keys=(parallelism logging scan_cache regression_detection rule_filters)
    local _k
    for _k in "${_required_keys[@]}"; do
      if ! grep -qE "^${_k}:" "$_config"; then
        _errors="${_errors}missing required top-level key: ${_k}"$'\n'
      fi
    done
    # Type spot-checks: jobs must be int in [0,256]
    local _jobs="${GATE_PARALLELISM_JOBS:-}"
    if ! [[ "$_jobs" =~ ^[0-9]+$ ]] || [[ "$_jobs" -lt 0 ]] || [[ "$_jobs" -gt 256 ]]; then
      _errors="${_errors}parallelism.jobs out of range (expected 0..256, got '$_jobs')"$'\n'
    fi
    # batch_strategy enum
    local _bs="${GATE_PARALLELISM_BATCH_STRATEGY:-}"
    case "$_bs" in
      round_robin|longest_first) ;;
      *) _errors="${_errors}parallelism.batch_strategy not in enum (got '$_bs')"$'\n' ;;
    esac
    # stdout_format enum
    local _sf="${GATE_LOGGING_STDOUT_FORMAT:-}"
    case "$_sf" in
      human|quiet|json) ;;
      *) _errors="${_errors}logging.stdout_format not in enum (got '$_sf')"$'\n' ;;
    esac
    # multiplier_threshold must be >= 1.0
    local _mt="${GATE_REGRESSION_DETECTION_MULTIPLIER_THRESHOLD:-}"
    if ! awk -v v="$_mt" 'BEGIN { exit (v + 0 >= 1.0 ? 0 : 1) }'; then
      _errors="${_errors}regression_detection.multiplier_threshold must be >= 1.0 (got '$_mt')"$'\n'
    fi
  fi

  if [[ -z "$_errors" ]]; then
    export GATE_CONFIG_VALID=true
    export GATE_CONFIG_ERRORS=""
    return 0
  else
    export GATE_CONFIG_VALID=false
    export GATE_CONFIG_ERRORS="$_errors"
    return 1
  fi
}
