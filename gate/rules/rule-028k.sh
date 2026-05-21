#!/usr/bin/env bash
# Auto-extracted from gate/check_architecture_sync.sh by gate/lib/extract_rules.sh
# Rule 28k — javadoc_enforcer_citation_semantic_check. DO NOT HAND-EDIT — re-run extract_rules.sh to refresh.
# Authority: PR-E5 (D:/.claude/plans/spicy-mixing-galaxy.md).

# Rule 28k — javadoc_enforcer_citation_semantic_check (post-review fix
# plan F / P1-2, enforcer E33+ semantic widening).
#
# Phase 7 post-release review surfaced two test-class Javadocs citing the
# WRONG enforcer ID (S2cCallbackRoundTripIT cited #E83 but is actually E82;
# EngineRegistryBootValidationIT cited #E81 but is actually E84). Rule 28j
# checks `artifact: path#anchor` resolves; it does NOT cross-check that a
# test file citing `enforcers.yaml#E<n>` in its Javadoc actually corresponds
# to E<n>'s declared `artifact:` field.
#
# This rule scans *Test.java and *IT.java under agent-service/src/test/java
# and agent-service/src/test/java for Javadoc citations of the form
# `enforcers.yaml#E<n>` and asserts each cited E-row's `artifact:` field's
# file path (anchor stripped, path normalised) matches the source file
# path. Mis-citation is a Rule 25 truth violation.
# ---------------------------------------------------------------------------
_r28k_fail=0
# PR-Opt-rc22: load pre-parsed enforcers TSV into an associative array.
# Replaces the per-citation `awk` pass over the full enforcers.yaml (which
# was ~9-20s per gate run). The TSV is built once by gate/lib/scan_cache.sh
# as _SCAN_ENFORCERS_TSV with fields: e_id \t artifact_path \t kind.
declare -A _r28k_art_by_eid
if [[ -n "${_SCAN_ENFORCERS_TSV:-}" ]]; then
  while IFS=$'\t' read -r _r28k_eid_k _r28k_art_v _r28k_kind_v; do
    [[ -n "$_r28k_eid_k" ]] && _r28k_art_by_eid["$_r28k_eid_k"]="$_r28k_art_v"
  done <<< "$_SCAN_ENFORCERS_TSV"
fi

if [[ -f "$_efile" ]]; then
  while IFS= read -r _r28k_src; do
    [[ -z "$_r28k_src" ]] && continue
    # Citation activation: a file is in Rule 28k scope only if it contains
    # at least one strict-form `enforcers.yaml#E<n>` citation. Files using
    # the loose-form "Related enforcers in enforcers.yaml: E5" wording have
    # no `#E\d+` and are exempt.
    if ! grep -qE 'enforcers\.yaml#E[0-9]+' "$_r28k_src" 2>/dev/null; then
      continue
    fi
    # In-scope: harvest ALL `#E<n>` tokens in the file -- the strict-form
    # `enforcers.yaml#E12` and the comma-continuation forms `#E13`, `#E14`
    # commonly used in plural `Enforcer rows: enforcers.yaml#E12, #E13, #E14`
    # citation blocks. Any `#E<n>` on the same Javadoc continuation counts.
    _r28k_eids=$(grep -oE '#E[0-9]+' "$_r28k_src" 2>/dev/null \
                 | sed -E 's|^#||' | sort -u)
    [[ -z "$_r28k_eids" ]] && continue
    # SEMANTICS (loosened from initial strict-each-match per post-review-fix
    # iteration): a test class may legitimately cross-reference multiple
    # related E-rows; the citation passes iff at least ONE cited E-row's
    # artifact: path matches the source file path. Tests that want to point
    # at related-but-not-primary enforcers should phrase the reference
    # without the `#E<n>` token (e.g. "Related: E12, E13" instead of
    # "enforcers.yaml#E12") so Rule 28k does not strict-check them.
    _r28k_src_norm=$(printf '%s' "$_r28k_src" | sed -E 's|^\./||')
    _r28k_any_match=0
    _r28k_collected_arts=""
    while IFS= read -r _r28k_eid; do
      [[ -z "$_r28k_eid" ]] && continue
      # PR-Opt-rc22: array lookup replaces per-iteration awk. Fallback to
      # the old awk pass if cache is empty (cache disabled / not populated).
      if [[ ${#_r28k_art_by_eid[@]} -gt 0 ]]; then
        _r28k_art="${_r28k_art_by_eid[$_r28k_eid]:-}"
      else
        _r28k_art=$(awk -v id="$_r28k_eid" '
          $0 ~ "^- id: " id "$" { found=1; next }
          found && /^[[:space:]]+artifact:/ {
            line=$0
            sub(/^[[:space:]]+artifact:[[:space:]]*/, "", line)
            sub(/#.*$/, "", line)
            gsub(/[[:space:]]+$/, "", line)
            print line
            exit
          }
          found && /^- id:/ { exit }
        ' "$_efile")
      fi
      if [[ -z "$_r28k_art" ]]; then
        # Cited E-id has no row at all -- structural break, always fail.
        fail_rule "javadoc_enforcer_citation_semantic_check" "$_r28k_src cites enforcers.yaml#$_r28k_eid but no such row in $_efile (Rule 28k / post-review plan F)"
        _r28k_fail=1
        continue
      fi
      _r28k_art_norm=$(printf '%s' "$_r28k_art" | sed -E 's|^\./||')
      _r28k_collected_arts="$_r28k_collected_arts $_r28k_eid:$_r28k_art_norm"
      if [[ "$_r28k_src_norm" == "$_r28k_art_norm" ]]; then
        _r28k_any_match=1
      fi
    done <<< "$_r28k_eids"
    if [[ "$_r28k_any_match" -eq 0 ]]; then
      fail_rule "javadoc_enforcer_citation_semantic_check" "$_r28k_src cites enforcers.yaml#E<n> rows but NONE of their artifact: paths match this file. Cited:$_r28k_collected_arts. Per Rule 28k / post-review plan F."
      _r28k_fail=1
    fi
  done < <(find agent-service/src/test/java agent-service/src/test/java -type f \( -name '*Test.java' -o -name '*IT.java' \) 2>/dev/null | sort)
fi
if [[ $_r28k_fail -eq 0 ]]; then pass_rule "javadoc_enforcer_citation_semantic_check"; fi

# ---------------------------------------------------------------------------
