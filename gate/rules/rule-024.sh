#!/usr/bin/env bash
# Auto-extracted from gate/check_architecture_sync.sh by gate/lib/extract_rules.sh
# Rule 24 — shipped_row_evidence_paths_exist. DO NOT HAND-EDIT — re-run extract_rules.sh to refresh.
# Authority: PR-E5.

# Rule 24 — shipped_row_evidence_paths_exist
# ADR-0045: every l2_documents: entry and latest_delivery_file: value on a
# shipped: true row must resolve to an existing file. Closes REF-DRIFT.
# ---------------------------------------------------------------------------
_r24_fail=0
if [[ -n "${_SCAN_SHIPPED_ROWS:-}" ]]; then
  # Fast path (PR-E3.b): one awk pass over the pre-extracted TSV.
  # Emit (capability, field, path) for every l2_doc + latest_delivery
  # entry whose capability is shipped:true. Bash then does stat() on each.
  while IFS=$'\t' read -r _r24_cap _r24_field _r24_path; do
    [[ -z "$_r24_path" ]] && continue
    if [[ ! -e "$_r24_path" ]]; then
      case "$_r24_field" in
        latest_delivery)
          fail_rule "shipped_row_evidence_paths_exist" "$_status_path capability '$_r24_cap' latest_delivery_file '$_r24_path' not found on disk. Per ADR-0045 Gate Rule 24 all shipped-row evidence paths must resolve."
          ;;
        l2_doc)
          fail_rule "shipped_row_evidence_paths_exist" "$_status_path capability '$_r24_cap' l2_documents entry '$_r24_path' not found on disk. Per ADR-0045 Gate Rule 24."
          ;;
      esac
      _r24_fail=1
    fi
  done < <(printf '%s\n' "$_SCAN_SHIPPED_ROWS" | awk -F'\t' '
    $2=="shipped" && $3=="true" { shipped[$1]=1 }
    ($2=="l2_doc" || $2=="latest_delivery") { rows[NR]=$1 "\t" $2 "\t" $3 }
    END {
      for (k in rows) {
        split(rows[k], a, "\t")
        if (a[1] in shipped) print a[1] "\t" a[2] "\t" a[3]
      }
    }
  ')
elif [[ -f "$_status_path" ]]; then
  # Fallback (cache disabled): original per-line scan.
  _current_key24=''
  _in_shipped24=0
  _in_l2_list24=0
  while IFS= read -r _line24 || [[ -n "$_line24" ]]; do
    if printf '%s\n' "$_line24" | grep -qE '^  [a-zA-Z][a-zA-Z_]+:'; then
      _current_key24=$(printf '%s\n' "$_line24" | sed 's/^  \([a-zA-Z][a-zA-Z_]*\):.*/\1/')
      _in_shipped24=0; _in_l2_list24=0
      continue
    fi
    if printf '%s\n' "$_line24" | grep -qE '^[[:space:]]+shipped:[[:space:]]+true'; then _in_shipped24=1; fi
    if [[ $_in_shipped24 -eq 1 ]]; then
      # latest_delivery_file
      if printf '%s\n' "$_line24" | grep -qE '^[[:space:]]+latest_delivery_file:[[:space:]]+'; then
        _ldf24=$(printf '%s\n' "$_line24" | sed -E 's/^[[:space:]]+latest_delivery_file:[[:space:]]+(.*)/\1/')
        if [[ -n "$_ldf24" && ! -e "$_ldf24" ]]; then
          fail_rule "shipped_row_evidence_paths_exist" "$_status_path capability '$_current_key24' latest_delivery_file '$_ldf24' not found on disk. Per ADR-0045 Gate Rule 24 all shipped-row evidence paths must resolve."
          _r24_fail=1
        fi
      fi
      # l2_documents list
      if printf '%s\n' "$_line24" | grep -qE '^[[:space:]]+l2_documents:[[:space:]]*\[\]'; then
        _in_l2_list24=0
      elif printf '%s\n' "$_line24" | grep -qE '^[[:space:]]+l2_documents:[[:space:]]*$'; then
        _in_l2_list24=1
      elif printf '%s\n' "$_line24" | grep -qE '^[[:space:]]+l2_documents:'; then
        _in_l2_list24=0
      elif [[ $_in_l2_list24 -eq 1 ]] && printf '%s\n' "$_line24" | grep -qE '^[[:space:]]+-[[:space:]]+'; then
        _l2p24=$(printf '%s\n' "$_line24" | sed -E 's/^[[:space:]]+-[[:space:]]+(.*)/\1/')
        if [[ -n "$_l2p24" && ! -e "$_l2p24" ]]; then
          fail_rule "shipped_row_evidence_paths_exist" "$_status_path capability '$_current_key24' l2_documents entry '$_l2p24' not found on disk. Per ADR-0045 Gate Rule 24."
          _r24_fail=1
        fi
      elif [[ $_in_l2_list24 -eq 1 ]] && ! printf '%s\n' "$_line24" | grep -qE '^[[:space:]]+-'; then
        _in_l2_list24=0
      fi
    fi
  done < "$_status_path"
fi
if [[ $_r24_fail -eq 0 ]]; then pass_rule "shipped_row_evidence_paths_exist"; fi

# ---------------------------------------------------------------------------
