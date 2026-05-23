#!/usr/bin/env bash
# gate/lib/check_recurring_families.sh
#
# Shared helper functions for Rule G-9 / Gate Rule 111 + the Rule 111
# self-test fixtures. Per ADR-0095 (rc18 Wave 1) the helper extraction
# closed F-kernel-vs-implementation-drift on Rule 111 itself.
#
# rc19 Wave 1 (ADR-0096): the awk-based yaml parsing is replaced by a
# python helper at gate/lib/validate_recurring_families.py to close the
# adversarial findings ADV-RC18-1 (no-op commit mtime bypass via content-
# diff freshness), ADV-RC18-2 (semantic-date validation), ADV-RC18-3
# (refresh-signal path filter derived from families[].surfaces[]), and
# ADV-RC18-4 (yaml literal-block injection in awk parser). The bash
# helper now shells out to python so the fixture interface is preserved
# (fixtures still source this file and call the same function names).
#
# Interface (unchanged from rc18 Wave 1):
#
#   _check_recurring_families_yaml_wellformed <yaml_path>
#       Sub-check .a (E156). Validates: file exists, top-level keys,
#       semantic ISO date (no future, no 9999-12-31), non-empty families,
#       9 required fields per family, cleanup_status enum membership.
#
#   _check_recurring_families_freshness <yaml_path> <repo_root>
#       Sub-check .b (E157). Compares yaml CONTENT (not just mtime)
#       between current state and state at refresh-signal commit. Defeats
#       no-op-commit bypass. Signal paths auto-derived from families[].
#       surfaces[] + base paths.
#
#   _check_recurring_families_md_yaml_parity <yaml_path> <md_path>
#       Sub-check .c (E158). Compares family-id set in yaml (real parse)
#       vs md `^### F-...` H3 headings (regex widened to accept uppercase/
#       underscore to mirror yaml-side acceptance).
#
#   _check_recurring_families_all <yaml> <md> <repo_root>
#       Wrapper running all three sub-checks. Returns aggregated fail
#       status.
#
# All functions return 0 on pass, 1 on fail; failure messages on stdout
# (one per line) for fixture capture via while-read iteration.
#
# Authority: ADR-0095 (rc18 Wave 1) + ADR-0096 (rc19 Wave 1).

set -uo pipefail

# Resolve script directory so we can find the sibling python validator
_CRF_SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
_CRF_VALIDATOR="${_CRF_SCRIPT_DIR}/validate_recurring_families.py"

_crf_run_python() {
  # $1 = sub-check name (wellformed|freshness|parity), $@... = args.
  # Calls the python validator and forwards stdout (FAIL messages) +
  # exit code. Prefer `python3` over `python` to match the rest of the
  # gate corpus (`check_parallel.sh`, `build_architecture_graph.sh`,
  # `aggregate_summary.sh`); on systems where `python` resolves to
  # Python 2 (older RHEL / minimal images) the validator's Python 3
  # syntax would crash with SyntaxError. Honour GATE_PYTHON_BIN when
  # set so a one-time export at the gate entrypoint pins the choice.
  local pyexe="${GATE_PYTHON_BIN:-}"
  if [[ -z "$pyexe" ]]; then
    if command -v python3 >/dev/null 2>&1; then
      pyexe="python3"
    elif command -v python >/dev/null 2>&1; then
      pyexe="python"
    else
      printf 'FAIL [crf-helper]: neither python3 nor python found on PATH (required for the recurring-families validator) -- Rule G-9 / ADR-0096\n'
      return 1
    fi
  fi
  "$pyexe" "$_CRF_VALIDATOR" "$@"
}

# -----------------------------------------------------------------------------
# Sub-check .a — yaml well-formedness
# -----------------------------------------------------------------------------
_check_recurring_families_yaml_wellformed() {
  _crf_run_python wellformed "$1"
}

# -----------------------------------------------------------------------------
# Sub-check .b — freshness via content diff
# -----------------------------------------------------------------------------
_check_recurring_families_freshness() {
  local yaml="$1"
  local repo_root="${2:-$(pwd)}"
  _crf_run_python freshness "$yaml" "$repo_root"
}

# -----------------------------------------------------------------------------
# Sub-check .c — yaml/md family-id parity
# -----------------------------------------------------------------------------
_check_recurring_families_md_yaml_parity() {
  _crf_run_python parity "$1" "$2"
}

# -----------------------------------------------------------------------------
# Wrapper: run all three sub-checks.
# -----------------------------------------------------------------------------
_check_recurring_families_all() {
  local yaml="$1"
  local md="$2"
  local repo_root="${3:-$(pwd)}"
  local fail=0
  _check_recurring_families_yaml_wellformed "$yaml" || fail=1
  _check_recurring_families_freshness "$yaml" "$repo_root" || fail=1
  _check_recurring_families_md_yaml_parity "$yaml" "$md" || fail=1
  return $fail
}
