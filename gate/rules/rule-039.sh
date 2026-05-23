#!/usr/bin/env bash
# Auto-extracted from gate/check_architecture_sync.sh by gate/lib/extract_rules.sh
# Rule 39 — review_proposal_front_matter. DO NOT HAND-EDIT — re-run extract_rules.sh to refresh.
# Authority: PR-E5 (D:/.claude/plans/spicy-mixing-galaxy.md).

# Rule 39 — review_proposal_front_matter (enforcer E57, ADR-0068)
#
# Every NEW (post-W1) proposal under docs/logs/reviews/ MUST declare
# affects_level: + affects_view: front-matter. Pre-W1 historical review
# files are explicitly listed in the allow-list below and exempted.
# ---------------------------------------------------------------------------
_r39_fail=0
# Allow-list of pre-W1 historical files (relative to docs/logs/reviews/).
_r39_allow_re='^(2026-05-1[23]-|2026-05-14-(architecture-governance-in-vibe-coding-era|L0Architecture-LucioIT-wave-1-request|l1-architecture-expert-review)|spring-ai-ascend-implementation-guidelines|Architectural Perspective Review)'
while IFS= read -r _f39; do
  [[ -z "$_f39" ]] && continue
  _base="$(basename "$_f39")"
  [[ "$_base" == "_TEMPLATE.md" ]] && continue
  if [[ "$_base" =~ $_r39_allow_re ]]; then continue; fi
  # Accept both single-value form (`affects_level: L0`) and YAML list form
  # (`affects_level: [L0, L1]` or `affects_level: L0, L1`) — 2026-05-23
  # widening: the rc33 proposals add multi-level/multi-view proposals.
  if ! grep -qE '^affects_level:[[:space:]]+(\[[[:space:]]*)?(L0|L1|L2)' "$_f39" 2>/dev/null; then
    fail_rule "review_proposal_front_matter" "$_f39 missing 'affects_level:' front-matter (CLAUDE.md Rule 33 / ADR-0068)"; _r39_fail=1
  fi
  if ! grep -qE '^affects_view:[[:space:]]+(\[[[:space:]]*)?(logical|development|process|physical|scenarios)' "$_f39" 2>/dev/null; then
    fail_rule "review_proposal_front_matter" "$_f39 missing 'affects_view:' front-matter (CLAUDE.md Rule 33 / ADR-0068)"; _r39_fail=1
  fi
done < <(find docs/logs/reviews -maxdepth 1 -type f -name '*.md' 2>/dev/null | sort || true)
if [[ $_r39_fail -eq 0 ]]; then pass_rule "review_proposal_front_matter"; fi

# ---------------------------------------------------------------------------
