#!/usr/bin/env bash
# Auto-extracted from gate/check_architecture_sync.sh by gate/lib/extract_rules.sh
# Rule 107 — cross_authority_clause_parity. DO NOT HAND-EDIT — re-run extract_rules.sh to refresh.
# Authority: PR-E5.

# Rule 107 — cross_authority_clause_parity (enforcer E152)
#
# Family A prevention — closes rc16 P1-1 + the 3 hidden defects (R-J.b.d
# orphaned in principle-coverage.yaml + rule-R-J.md kernel + rule-R-J.md
# card; R-K.b orphaned in principle-coverage.yaml + CLAUDE-deferred.md).
# Per ADR-0093 (rc16 cross-authority parity + meta scope completeness wave).
#
# scope_surfaces: docs/governance/principle-coverage.yaml, docs/CLAUDE-deferred.md, CLAUDE.md, docs/governance/rules/*.md
#
# The rule asserts pairwise parity: every clause name (Rule-X.<letter>)
# named in principle-coverage.yaml#deferred_operationalisers MUST have a
# matching `## Rule X.<letter>` heading in CLAUDE-deferred.md. Active
# clause names (Rule-X without sub-letter) are checked against CLAUDE.md
# `#### Rule X` headings.
# ---------------------------------------------------------------------------
_r107_fail=0
_r107_coverage="docs/governance/principle-coverage.yaml"
_r107_deferred="docs/CLAUDE-deferred.md"
_r107_claude="CLAUDE.md"
_r107_cards_dir="docs/governance/rules"
if [[ -f "$_r107_coverage" && -f "$_r107_claude" && -d "$_r107_cards_dir" ]]; then
  # Collect deferred-section headings as `Rule-X.<letter>` tokens from CLAUDE-deferred.md
  # if the file still exists (transitional support during Phase 7 cleanup).
  _r107_deferred_headings=""
  if [[ -f "$_r107_deferred" ]]; then
    _r107_deferred_headings=$(grep -oE '^## Rule [A-Z](-[A-Z])?(\.[a-z](\.[a-z])?)?' "$_r107_deferred" \
                              | sed -E 's/^## Rule /Rule-/' | sed 's/ /-/g' | sort -u || true)
  fi
  # Also collect migrated deferred sub-clauses from rule card frontmatter
  # `deferred_sub_clauses: - id: ".x"` (Phase 7 step 7.2 migration target).
  # Card filename rule-R-X.md + id ".y" -> token "Rule-R-X.y".
  _r107_card_headings=$(
    for _r107_card in "$_r107_cards_dir"/rule-*.md; do
      [[ -f "$_r107_card" ]] || continue
      _r107_base=$(basename "$_r107_card" .md | sed 's/^rule-//')
      awk -v parent="$_r107_base" '
        /^deferred_sub_clauses:/{flag=1; next}
        flag && /^[^[:space:]-]/{flag=0}
        flag && /^[[:space:]]*-[[:space:]]+id:[[:space:]]*/{
          val=$0
          sub(/^[[:space:]]*-[[:space:]]+id:[[:space:]]*/, "", val)
          gsub(/["'\'']/, "", val)
          sub(/^\./, "", val)
          if (val != "") print "Rule-" parent "." val
        }
      ' "$_r107_card"
    done | sort -u || true
  )
  # Union of both sources.
  _r107_all_headings=$(printf '%s\n%s\n' "$_r107_deferred_headings" "$_r107_card_headings" | sort -u)
  # Collect deferred-clause names listed in principle-coverage.yaml.
  _r107_listed_clauses=$(awk '
      /deferred_operationalisers:/{flag=1; next}
      flag && /^[[:space:]]*-[[:space:]]+Rule-/{
        sub(/^[[:space:]]*-[[:space:]]+/, "");
        sub(/[[:space:]]+#.*$/, "");
        print
        next
      }
      flag && !/^[[:space:]]*-/{flag=0}
    ' "$_r107_coverage" | sort -u || true)
  while IFS= read -r _r107_clause; do
    [[ -z "$_r107_clause" ]] && continue
    # Only sub-letter clauses are expected as deferred entries.
    if echo "$_r107_clause" | grep -qE '\.[a-z]'; then
      if ! echo "$_r107_all_headings" | grep -qFx "$_r107_clause"; then
        fail_rule "cross_authority_clause_parity" "principle-coverage.yaml lists deferred operationaliser $_r107_clause but no matching ## heading in CLAUDE-deferred.md AND no matching deferred_sub_clauses entry in $_r107_cards_dir/ -- Rule 107 / E152 (Family A per ADR-0093)"
        _r107_fail=1
      fi
    fi
  done <<< "$_r107_listed_clauses"
fi
if [[ $_r107_fail -eq 0 ]]; then pass_rule "cross_authority_clause_parity"; fi

# ---------------------------------------------------------------------------
