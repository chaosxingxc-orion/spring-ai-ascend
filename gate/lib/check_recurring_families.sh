#!/usr/bin/env bash
# gate/lib/check_recurring_families.sh
#
# Shared helper functions for Rule G-9 / Gate Rule 111 + the 3 self-test
# fixtures. Per ADR-0095 (rc18 Wave 1) the helper extraction closes the
# F-kernel-vs-implementation-drift family on Rule 111 itself by ensuring
# fixtures and gate both invoke the same logic — no inline re-implementation.
#
# Interface (all return 0 on pass, 1 on fail; failure messages printed to
# stdout for fixture capture):
#
#   _check_recurring_families_yaml_wellformed <yaml_path>
#       Sub-check .a (E156). Validates:
#         - file exists
#         - top-level schema_version, last_updated, families present
#         - last_updated is ISO YYYY-MM-DD format (fix 1e)
#         - families array is non-empty (fix 1b — hard assertion)
#         - per-family block-bucket: each family has all 9 required
#           fields exactly once (fix 1d — closes duplicate-field
#           compensation blind spot)
#         - cleanup_status value ∈ {closed, structurally_addressed,
#           partial, incomplete, monitoring} (fix 1c — closes enum
#           presence-only validation)
#
#   _check_recurring_families_freshness <yaml_path> <repo_root>
#       Sub-check .b (E157). Validates:
#         - shallow clone detection (fix 1h — fail-closed not silent)
#         - signal commit date (git log of refresh-signal paths
#           INCLUDING docs/governance/rules/ — fix 1g)
#         - yaml file's own commit date (fix 1a — git mtime not
#           hand-edited last_updated field)
#         - signal commit date <= yaml file commit date
#
#   _check_recurring_families_md_yaml_parity <yaml_path> <md_path>
#       Sub-check .c (E158). Validates:
#         - yaml `^  - id:` slug set EQUALS
#         - md `^### F-` H3 heading slug set (fix 1f — H3 anchoring,
#           closes prose false-positives)
#
# Authority: ADR-0095 rc18 Wave 1.

set -uo pipefail

# Constants
_CRF_REQUIRED_TOPKEYS="schema_version last_updated families"
_CRF_REQUIRED_FAMILY_FIELDS="title first_observed_rc last_observed_rc occurrences root_cause surfaces prevention_rules cleanup_status open_residual"
_CRF_CLEANUP_STATUS_ENUM="closed structurally_addressed partial incomplete monitoring"
_CRF_REFRESH_SIGNAL_PATHS=(
  "docs/adr/"
  "docs/governance/architecture-status.yaml"
  "docs/logs/releases/"
  "docs/governance/rules/"
  "CLAUDE.md"
)

# -----------------------------------------------------------------------------
# Sub-check .a — yaml well-formedness
# -----------------------------------------------------------------------------
_check_recurring_families_yaml_wellformed() {
  local yaml="$1"
  local fail=0

  if [[ ! -f "$yaml" ]]; then
    printf 'FAIL [yaml-wellformed]: %s missing -- Rule G-9.a / E156\n' "$yaml"
    return 1
  fi

  # Top-level keys present
  local topkey
  for topkey in $_CRF_REQUIRED_TOPKEYS; do
    if ! grep -qE "^${topkey}:" "$yaml" 2>/dev/null; then
      printf 'FAIL [yaml-wellformed]: %s missing top-level key %s -- Rule G-9.a / E156\n' "$yaml" "$topkey"
      fail=1
    fi
  done

  # Fix 1e: last_updated must be ISO YYYY-MM-DD
  local last_updated
  last_updated=$(awk '/^last_updated:/ { gsub(/["'\'']/,""); print $2; exit }' "$yaml" 2>/dev/null)
  if [[ -n "$last_updated" && ! "$last_updated" =~ ^[0-9]{4}-[0-9]{2}-[0-9]{2}$ ]]; then
    printf 'FAIL [yaml-wellformed]: last_updated value %q is not ISO YYYY-MM-DD format -- Rule G-9.a / E156 (fix 1e)\n' "$last_updated"
    fail=1
  fi

  # Fix 1b: families array must be non-empty (hard assertion)
  local fam_count
  fam_count=$(grep -cE '^  - id:' "$yaml" 2>/dev/null)
  fam_count=${fam_count:-0}
  if [[ "$fam_count" -eq 0 ]]; then
    printf 'FAIL [yaml-wellformed]: families array is empty (families: [] is forbidden) -- Rule G-9.a / E156 (fix 1b)\n'
    fail=1
    return $fail
  fi

  # Fix 1d: per-family block-bucket — walk awk between ^  - id: boundaries,
  # check each family has every required field exactly once + cleanup_status
  # value is in the enum set. Closes the duplicate-field compensation blind
  # spot AND the enum-presence-only validation gap (fix 1c).
  local awk_output
  awk_output=$(awk -v required="$_CRF_REQUIRED_FAMILY_FIELDS" \
                   -v enum="$_CRF_CLEANUP_STATUS_ENUM" '
    BEGIN {
      n = split(required, req_arr, " ")
      m = split(enum, enum_arr, " ")
      cur_id = ""
    }
    function flush_family(id,    i, k, cnt, status_val, status_ok) {
      if (id == "") return
      for (i = 1; i <= n; i++) {
        cnt = field_count[id, req_arr[i]] + 0
        if (cnt == 0) {
          print "MISSING|" id "|" req_arr[i]
        } else if (cnt > 1) {
          print "DUPLICATE|" id "|" req_arr[i] "|" cnt
        }
      }
      status_val = field_value[id, "cleanup_status"]
      if (status_val != "") {
        status_ok = 0
        for (k = 1; k <= m; k++) if (status_val == enum_arr[k]) status_ok = 1
        if (!status_ok) print "ENUM|" id "|cleanup_status|" status_val
      }
    }
    /^  - id:[[:space:]]+/ {
      flush_family(cur_id)
      cur_id = $3
      gsub(/["'\'']/, "", cur_id)
      seen_ids[cur_id]++
      if (seen_ids[cur_id] > 1) print "DUPLICATE_ID|" cur_id
      next
    }
    cur_id != "" && /^    [a-z_]+:/ {
      # Field at correct indent (4 spaces under a family entry)
      line = $0
      sub(/^    /, "", line)
      key = line
      sub(/:.*/, "", key)
      value = line
      sub(/^[^:]*:[[:space:]]*/, "", value)
      # strip surrounding quotes if present
      sub(/^["'\'']/, "", value)
      sub(/["'\'']$/, "", value)
      field_count[cur_id, key]++
      if (field_count[cur_id, key] == 1) field_value[cur_id, key] = value
    }
    END { flush_family(cur_id) }
  ' "$yaml")

  if [[ -n "$awk_output" ]]; then
    local line kind id field detail
    while IFS= read -r line; do
      kind="${line%%|*}"
      case "$kind" in
        MISSING)
          IFS='|' read -r _ id field <<< "$line"
          printf 'FAIL [yaml-wellformed]: family %s missing required field %s -- Rule G-9.a / E156 (fix 1d)\n' "$id" "$field"
          fail=1
          ;;
        DUPLICATE)
          IFS='|' read -r _ id field detail <<< "$line"
          printf 'FAIL [yaml-wellformed]: family %s declares field %s %s times (must be exactly 1) -- Rule G-9.a / E156 (fix 1d)\n' "$id" "$field" "$detail"
          fail=1
          ;;
        DUPLICATE_ID)
          IFS='|' read -r _ id <<< "$line"
          printf 'FAIL [yaml-wellformed]: family id %s declared more than once -- Rule G-9.a / E156 (fix 1d)\n' "$id"
          fail=1
          ;;
        ENUM)
          IFS='|' read -r _ id field detail <<< "$line"
          printf 'FAIL [yaml-wellformed]: family %s field %s value %q not in enum {%s} -- Rule G-9.a / E156 (fix 1c)\n' "$id" "$field" "$detail" "$_CRF_CLEANUP_STATUS_ENUM"
          fail=1
          ;;
      esac
    done <<< "$awk_output"
  fi

  return $fail
}

# -----------------------------------------------------------------------------
# Sub-check .b — freshness (yaml file's git mtime vs refresh-signal mtime)
# -----------------------------------------------------------------------------
_check_recurring_families_freshness() {
  local yaml="$1"
  local repo_root="${2:-$(pwd)}"
  local fail=0

  if [[ ! -f "$yaml" ]]; then
    # Subsumed by sub-check .a; don't double-fail
    return 0
  fi

  if ! command -v git >/dev/null 2>&1; then
    # No git available — can't check; skip silently in non-git contexts
    return 0
  fi
  if ! ( cd "$repo_root" && git rev-parse --git-dir >/dev/null 2>&1 ); then
    return 0
  fi

  # Fix 1h: shallow-clone fail-closed (not silent pass)
  local is_shallow
  is_shallow=$( cd "$repo_root" && git rev-parse --is-shallow-repository 2>/dev/null )
  if [[ "$is_shallow" == "true" ]]; then
    printf 'FAIL [freshness]: cannot evaluate freshness on shallow clone (run git fetch --unshallow in CI) -- Rule G-9.b / E157 (fix 1h)\n'
    return 1
  fi

  # Fix 1g: refresh-signal paths INCLUDE docs/governance/rules/
  # Fix 1a: compare yaml FILE's own commit date (not hand-edited last_updated)
  local signal_date yaml_commit_date
  signal_date=$( cd "$repo_root" && git log -1 --format=%cI -- "${_CRF_REFRESH_SIGNAL_PATHS[@]}" 2>/dev/null | cut -dT -f1 )
  yaml_commit_date=$( cd "$repo_root" && git log -1 --format=%cI -- "$yaml" 2>/dev/null | cut -dT -f1 )

  if [[ -z "$yaml_commit_date" ]]; then
    # yaml has never been committed — that's a wave 1 bootstrap state.
    # Don't fail; let .a catch the missing-file case.
    return 0
  fi

  if [[ -z "$signal_date" ]]; then
    # No refresh signals committed yet (impossible in a real repo, but
    # defensive). Treat as pass.
    return 0
  fi

  # signal_date > yaml_commit_date means a refresh-signal commit landed
  # AFTER the family yaml was last touched — author failed to sync.
  # String comparison works for ISO YYYY-MM-DD.
  if [[ "$signal_date" > "$yaml_commit_date" ]]; then
    printf 'FAIL [freshness]: yaml commit date %s is older than refresh-signal commit date %s -- Rule G-9.b / E157 (run /refresh-defect-archive then commit; fix 1a)\n' "$yaml_commit_date" "$signal_date"
    fail=1
  fi

  return $fail
}

# -----------------------------------------------------------------------------
# Sub-check .c — yaml/md family-id parity
# -----------------------------------------------------------------------------
_check_recurring_families_md_yaml_parity() {
  local yaml="$1"
  local md="$2"
  local fail=0

  if [[ ! -f "$yaml" || ! -f "$md" ]]; then
    return 0
  fi

  local yaml_ids_file md_ids_file
  yaml_ids_file=$(mktemp)
  md_ids_file=$(mktemp)

  # yaml side: structural ^  - id: rows only
  awk '/^  - id:[[:space:]]+/ {gsub(/["'\'']/, "", $3); print $3}' "$yaml" 2>/dev/null | sort -u > "$yaml_ids_file"

  # Fix 1f: md side: ONLY ^### F- H3 headings (mirror yaml anchoring;
  # closes prose F-... false-positive matches like "see F-deprecated-thing
  # in the table above")
  grep -oE '^### F-[a-z][a-z0-9-]*' "$md" 2>/dev/null | sed 's/^### //' | sort -u > "$md_ids_file"

  local only_yaml only_md
  only_yaml=$(comm -23 "$yaml_ids_file" "$md_ids_file")
  only_md=$(comm -13 "$yaml_ids_file" "$md_ids_file")

  if [[ -n "$only_yaml" ]]; then
    printf 'FAIL [md-yaml-parity]: family ids in yaml but missing from md ^### F- headings: %s -- Rule G-9.c / E158\n' "$(echo $only_yaml)"
    fail=1
  fi
  if [[ -n "$only_md" ]]; then
    printf 'FAIL [md-yaml-parity]: family ids in md ^### F- headings but missing from yaml: %s -- Rule G-9.c / E158\n' "$(echo $only_md)"
    fail=1
  fi

  rm -f "$yaml_ids_file" "$md_ids_file"
  return $fail
}

# -----------------------------------------------------------------------------
# Wrapper: run all three sub-checks. Used by both Gate Rule 111 and by
# fixture #4 (test_rule_111_all_clean_pos).
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
