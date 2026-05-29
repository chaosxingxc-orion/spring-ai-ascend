#!/usr/bin/env bash
# Auto-extracted from gate/check_architecture_sync.sh by gate/lib/extract_rules.sh
# Rule 39 — review_proposal_front_matter. DO NOT HAND-EDIT — re-run extract_rules.sh to refresh.
# Authority: PR-E5.

# Rule 39 — review_proposal_front_matter (enforcer E57, ADR-0068)
#
# docs/logs/reviews/ are interaction records (docs/governance/logs-folder-policy.md):
# front-matter is OPTIONAL and is NOT required on plain records (review responses,
# findings logs, PR responses). A doc that OPTS IN to 4+1 proposal classification —
# i.e. it declares affects_level: OR affects_view: — MUST declare BOTH, with valid
# values, so a half-classified proposal is still caught. Docs declaring neither key
# are exempt. Pre-W1 historical files and _TEMPLATE.md remain exempt.
# This validate-if-present scope keeps logs friction-free per the user's logs-folder
# directive (rc16 / ADR-0093) while preserving classification quality for real proposals.
# ---------------------------------------------------------------------------
_r39_fail=0
# Allow-list of pre-W1 historical files (relative to docs/logs/reviews/).
_r39_allow_re='^(2026-05-1[23]-|2026-05-14-(architecture-governance-in-vibe-coding-era|L0Architecture-LucioIT-wave-1-request|l1-architecture-expert-review)|spring-ai-ascend-implementation-guidelines|Architectural Perspective Review)'
while IFS= read -r _f39; do
  [[ -z "$_f39" ]] && continue
  _base="$(basename "$_f39")"
  [[ "$_base" == "_TEMPLATE.md" ]] && continue
  if [[ "$_base" =~ $_r39_allow_re ]]; then continue; fi
  # Frontmatter is optional: only validate when the doc opts into proposal
  # classification by declaring at least one affects_* key.
  _r39_has_level=0; _r39_has_view=0
  grep -qE '^affects_level:' "$_f39" 2>/dev/null && _r39_has_level=1
  grep -qE '^affects_view:' "$_f39" 2>/dev/null && _r39_has_view=1
  if [[ $_r39_has_level -eq 0 && $_r39_has_view -eq 0 ]]; then continue; fi
  # Opted-in proposal — both keys MUST be present and valid. Accept single-value
  # form (`affects_level: L0`) and YAML list form (`affects_level: [L0, L1]`).
  if ! grep -qE '^affects_level:[[:space:]]+(\[[[:space:]]*)?(L0|L1|L2)' "$_f39" 2>/dev/null; then
    fail_rule "review_proposal_front_matter" "$_f39 declares proposal front-matter but 'affects_level:' is missing/invalid -- a classified proposal MUST declare both affects_level + affects_view (frontmatter is otherwise optional per logs-folder-policy)"; _r39_fail=1
  fi
  if ! grep -qE '^affects_view:[[:space:]]+(\[[[:space:]]*)?(logical|development|process|physical|scenarios)' "$_f39" 2>/dev/null; then
    fail_rule "review_proposal_front_matter" "$_f39 declares proposal front-matter but 'affects_view:' is missing/invalid -- a classified proposal MUST declare both affects_level + affects_view (frontmatter is otherwise optional per logs-folder-policy)"; _r39_fail=1
  fi
done < <(find docs/logs/reviews -maxdepth 1 -type f -name '*.md' 2>/dev/null | sort || true)
if [[ $_r39_fail -eq 0 ]]; then pass_rule "review_proposal_front_matter"; fi

# ---------------------------------------------------------------------------
