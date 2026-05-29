#!/usr/bin/env bash
# Auto-extracted from gate/check_architecture_sync.sh by gate/lib/extract_rules.sh
# Rule 60 — schema_first_domain_contracts. DO NOT HAND-EDIT — re-run extract_rules.sh to refresh.
# Authority: PR-E5.

# Rule 60 — schema_first_domain_contracts (enforcer E85, Rule 48, ADR-0077)
#
# Forbid new prose-defined enum sites in the architecture corpus. Scan
# ARCHITECTURE.md (root) + architecture/docs/L1/agent-*.md architecture/docs/L1/agent-service/ARCHITECTURE.md for the prose-enum pattern
# `<UPPERCASE_TYPE> | <UPPERCASE_TYPE>` outside fenced code blocks and
# markdown tables. For every match, the rule passes only when one of:
#   (a) the file path appears as a prefix line in gate/schema-first-grandfathered.txt
#       (file-level grandfather -- pre-W2.x existing taxonomies);
#   (b) the file path is at file-level grandfather (i.e. has any '<path>:' entry).
# The grandfather list is CLOSED: no entries added after 2026-05-16.
# This rule codifies the W2.x doctrine "yaml schema -> Java type -> runtime
# self-validate" into a permanent constraint.
# ---------------------------------------------------------------------------
_r60_fail=0
_r60_grandfather="gate/schema-first-grandfathered.txt"
_r60_files=(ARCHITECTURE.md agent-service/ARCHITECTURE.md agent-service/ARCHITECTURE.md)
if [[ ! -f "$_r60_grandfather" ]]; then
  fail_rule "schema_first_domain_contracts" "$_r60_grandfather missing -- Rule 48 grandfather list required"
  _r60_fail=1
else
  # Phase 7 audit fix (Rule 48 sunset discipline -- plan F2/F3).
  # Each grandfather entry MUST be
  # pipe-delimited <path>|<sunset_date>|<desc>. Validate sunset_date format
  # and that today <= sunset_date for every entry.
  _r60_today=$(date +%Y-%m-%d)
  while IFS= read -r _r60_line; do
    [[ -z "$_r60_line" || "$_r60_line" =~ ^[[:space:]]*# ]] && continue
    _r60_entry_path=$(printf '%s' "$_r60_line" | cut -d'|' -f1)
    _r60_entry_sunset=$(printf '%s' "$_r60_line" | cut -d'|' -f2)
    if [[ -z "$_r60_entry_path" || -z "$_r60_entry_sunset" ]]; then
      fail_rule "schema_first_domain_contracts" "grandfather entry malformed (need <path>|<sunset>|<desc>): $_r60_line"
      _r60_fail=1
      continue
    fi
    if ! [[ "$_r60_entry_sunset" =~ ^[0-9]{4}-[0-9]{2}-[0-9]{2}$ ]]; then
      fail_rule "schema_first_domain_contracts" "malformed sunset_date '$_r60_entry_sunset' for $_r60_entry_path in $_r60_grandfather (expected YYYY-MM-DD)"
      _r60_fail=1
      continue
    fi
    if [[ "$_r60_today" > "$_r60_entry_sunset" ]]; then
      fail_rule "schema_first_domain_contracts" "$_r60_entry_path grandfather entry expired on $_r60_entry_sunset; retrofit required per CLAUDE-deferred.md 48.b"
      _r60_fail=1
    fi
  done < "$_r60_grandfather"
  for _r60_file in "${_r60_files[@]}"; do
    if [[ ! -f "$_r60_file" ]]; then continue; fi
    _r60_candidates=$(awk '
      BEGIN { in_fence = 0 }
      /^```/ { in_fence = !in_fence; next }
      { if (in_fence) next }
      /^[[:space:]]*\|/ { next }
      /[A-Z][A-Z_][A-Z_]*[[:space:]]*\|[[:space:]]*[A-Z][A-Z_][A-Z_]*/ { print NR }
    ' "$_r60_file")
    if [[ -z "$_r60_candidates" ]]; then continue; fi
    # File-level grandfather check: if any line in grandfather list starts with this file path + '|', whitelist all matches.
    if grep -qE "^${_r60_file}\|" "$_r60_grandfather"; then continue; fi
    while read -r _r60_ln; do
      [[ -z "$_r60_ln" ]] && continue
      _r60_lo=$(( _r60_ln - 5 )); [[ $_r60_lo -lt 1 ]] && _r60_lo=1
      _r60_hi=$(( _r60_ln + 5 ))
      if ! awk -v lo="$_r60_lo" -v hi="$_r60_hi" 'NR>=lo && NR<=hi' "$_r60_file" \
         | grep -qE 'docs/(contracts|governance)/[^[:space:]]+\.yaml'; then
        fail_rule "schema_first_domain_contracts" "$_r60_file:$_r60_ln prose enum without yaml-schema reference within +/-5 lines and not in $_r60_grandfather"
        _r60_fail=1
      fi
    done <<< "$_r60_candidates"
  done
fi
if [[ $_r60_fail -eq 0 ]]; then pass_rule "schema_first_domain_contracts"; fi

# ---------------------------------------------------------------------------
