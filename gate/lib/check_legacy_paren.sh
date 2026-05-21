#!/usr/bin/env bash
# gate/lib/check_legacy_paren.sh
#
# Helper for Rule 113 — closes Pattern D (fixture inlines production
# logic) per rc20 Wave 1 / ADR-0097.
#
# Two sub-checks, both grep-anchored at scope_surfaces named in the
# Rule 113 body. The production gate AND the test fixtures source this
# helper, so any future change to the regex/heading set is felt in
# both surfaces simultaneously.
#
# Functions:
#   _check_legacy_paren_no_reintroduction <enforcers_yaml_path>
#       Emits one FAIL line per offending line; emits nothing on PASS.
#   _check_migration_doc_complete <migration_md_path> <required_heading1> ...
#       Emits FAIL if path missing OR any required heading absent.

_check_legacy_paren_no_reintroduction() {
  local _enforcers_path="$1"
  if [[ -z "$_enforcers_path" || ! -f "$_enforcers_path" ]]; then
    # Caller decides whether absence is fatal.
    return 0
  fi
  # grep regex pinned here as the single source of truth for the pattern.
  local _hits
  _hits=$(grep -nE '\(legacy Rule [0-9]+' "$_enforcers_path" 2>/dev/null || true)
  if [[ -n "$_hits" ]]; then
    while IFS= read -r _line; do
      [[ -z "$_line" ]] && continue
      printf '%s:%s -- reintroduced (legacy Rule NN ...) parenthetical (rc18 Wave 4 removed these; legacy mapping belongs in gate/rule-number-migration.md). Rule 113.a / E160\n' "$_enforcers_path" "$_line"
    done <<< "$_hits"
  fi
}

_check_migration_doc_complete() {
  local _migration_path="$1"
  shift
  if [[ ! -f "$_migration_path" ]]; then
    printf '%s missing -- rc18 Wave 4 created this as legacy-mapping SSOT. Rule 113.b / E160\n' "$_migration_path"
    return 0
  fi
  local _required
  for _required in "$@"; do
    if ! grep -qF "$_required" "$_migration_path" 2>/dev/null; then
      printf '%s missing required section containing %q. Rule 113.b / E160\n' "$_migration_path" "$_required"
    fi
  done
}
