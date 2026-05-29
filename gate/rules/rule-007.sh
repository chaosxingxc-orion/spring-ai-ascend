#!/usr/bin/env bash
# Auto-extracted from gate/check_architecture_sync.sh by gate/lib/extract_rules.sh
# Rule 7 — shipped_impl_paths_exist. DO NOT HAND-EDIT — re-run extract_rules.sh to refresh.
# Authority: PR-E5.

# Rule 7 — shipped_impl_paths_exist
# Every capability row with shipped: true in architecture-status.yaml MUST
# have all its implementation: paths exist on disk.
# ---------------------------------------------------------------------------
_r7_fail=0
_status_file='docs/governance/architecture-status.yaml'
if [[ -n "${_SCAN_SHIPPED_ROWS:-}" ]]; then
  # Fast path (PR-E3.b): one awk pass over the pre-extracted TSV.
  # Selects every (capability, impl_path) where the capability is shipped:true.
  while IFS=$'\t' read -r _r7_cap _r7_path; do
    [[ -z "$_r7_path" ]] && continue
    [[ "$_r7_path" == "null" ]] && continue
    if [[ ! -e "$_r7_path" ]]; then
      fail_rule "shipped_impl_paths_exist" "shipped: true row '$_r7_cap' references non-existent path: $_r7_path"
      _r7_fail=1
    fi
  done < <(printf '%s\n' "$_SCAN_SHIPPED_ROWS" | awk -F'\t' '
    $2=="shipped" && $3=="true" { shipped[$1]=1 }
    $2=="impl" { rows[NR]=$1 "\t" $3 }
    END { for (k in rows) { split(rows[k], a, "\t"); if (a[1] in shipped) print a[1] "\t" a[2] } }
  ')
elif [[ -f "$_status_file" ]]; then
  # Fallback (cache disabled): original per-line scan.
  _in_shipped=0
  while IFS= read -r _line; do
    if echo "$_line" | grep -qE '^\s*shipped:\s*true'; then
      _in_shipped=1
    elif echo "$_line" | grep -qE '^\s*shipped:\s*false'; then
      _in_shipped=0
    elif [[ $_in_shipped -eq 1 ]] && echo "$_line" | grep -qE '^\s*-\s+\S'; then
      _impl_path=$(echo "$_line" | sed -E 's/^\s*-\s+//')
      if [[ -n "$_impl_path" ]] && [[ "$_impl_path" != "null" ]]; then
        if [[ ! -e "$_impl_path" ]]; then
          fail_rule "shipped_impl_paths_exist" "shipped: true row references non-existent path: $_impl_path"
          _r7_fail=1
        fi
      fi
    elif echo "$_line" | grep -qE '^\s*(status|tests|allowed_claim|l0_decision|l2_documents|note):'; then
      _in_shipped=0
    fi
  done < "$_status_file"
fi
if [[ $_r7_fail -eq 0 ]]; then pass_rule "shipped_impl_paths_exist"; fi

# ---------------------------------------------------------------------------
