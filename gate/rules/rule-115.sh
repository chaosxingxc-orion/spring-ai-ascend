#!/usr/bin/env bash
# Auto-extracted from gate/check_architecture_sync.sh by gate/lib/extract_rules.sh
# Rule 115 — no_version_log_metadata_in_code. DO NOT HAND-EDIT — re-run extract_rules.sh to refresh.
# Authority: PR-E5 (D:/.claude/plans/spicy-mixing-galaxy.md).

# Rule 115 — no_version_log_metadata_in_code (enforcer E163)
#
# Operationalises Rule D-9. Grep across non-exempt production-code surfaces
# for forbidden version/log metadata tokens:
#   - `rc<N> Wave <M>` style tags
#   - `per ADR-NNNN` pointers
#   - `Finding F<N>` or `(F<N>)` references
#   - `closes #<N>` / `addresses #<N>` ticket references
#
# Scope (production code only — exempt surfaces are governance docs):
#   *.java, *.py, *.sh, *.bash, *.kt, *.ts, *.tsx,
#   application*.yml/yaml, Dockerfile, .github/workflows/*.yml
#
# Exempt-by-path: docs/adr/, docs/logs/, docs/governance/rules/*.md,
#   docs/governance/rule-history.md, docs/governance/recurring-defect-families.*,
#   docs/governance/architecture-status.yaml, CHANGELOG.md, CLAUDE.md, gate/lib/,
#   gate/test_*.sh (test fixtures may construct synthetic version-tagged inputs).
# ---------------------------------------------------------------------------
_r115_fail=0
_r115_pattern='\brc[0-9]+ Wave [0-9]+\b|\bper ADR-[0-9]{4}\b|\(F[0-9]+\)|\bFinding F[0-9]+\b|\b(closes|addresses) #[0-9]+\b'
# Grandfather list: pre-existing files with violations at the time Rule D-9
# landed; tracked with sunset_date in gate/d9-grandfathered-files.txt so
# forward motion is required. Listed files are exempted from the gate.
_r115_grandfather_file="gate/d9-grandfathered-files.txt"
_r115_grandfathered=""
if [[ -f "$_r115_grandfather_file" ]]; then
  _r115_grandfathered=$(grep -vE '^[[:space:]]*#|^[[:space:]]*$' "$_r115_grandfather_file" 2>/dev/null | sort -u)
fi
_r115_files=$(find . \
  -path ./target -prune -o \
  -path ./node_modules -prune -o \
  -path ./.git -prune -o \
  -path ./docs/adr -prune -o \
  -path ./docs/logs -prune -o \
  -path ./docs/governance/rules -prune -o \
  -path ./docs/governance/principles -prune -o \
  -path ./gate/lib -prune -o \
  -type f \( \
       -name '*.java' \
    -o -name '*.py' \
    -o -name '*.sh' \
    -o -name '*.bash' \
    -o -name '*.kt' \
    -o -name '*.ts' \
    -o -name '*.tsx' \
    -o -name 'application*.yml' \
    -o -name 'application*.yaml' \
    -o -name 'Dockerfile' \
  \) -print 2>/dev/null \
  | grep -vE '^\./CLAUDE\.md$|^\./CHANGELOG\.md$|^\./docs/governance/architecture-status\.yaml$|^\./docs/governance/enforcers\.yaml$|^\./docs/governance/principle-coverage\.yaml$|^\./docs/governance/architecture-graph\.yaml$|^\./docs/governance/rule-history\.md$|^\./docs/governance/recurring-defect-families\.|^\./gate/test_architecture_sync_gate\.sh$' \
  || true)
# Also include .github/workflows/*.yml (separate find because of leading dot)
_r115_files="$_r115_files"$'\n'"$(find ./.github/workflows -type f -name '*.yml' 2>/dev/null || true)"
_r115_hits=""
while IFS= read -r _r115_f; do
  [[ -z "$_r115_f" ]] && continue
  [[ ! -f "$_r115_f" ]] && continue
  _r115_rel="${_r115_f#./}"
  if [[ -n "$_r115_grandfathered" ]] && printf '%s\n' "$_r115_grandfathered" | grep -Fxq "$_r115_rel"; then
    continue
  fi
  _hit=$(grep -nE "$_r115_pattern" "$_r115_f" 2>/dev/null || true)
  if [[ -n "$_hit" ]]; then
    while IFS= read -r _h; do
      [[ -z "$_h" ]] && continue
      _r115_hits="$_r115_hits"$'\n'"$_r115_f:$_h"
    done <<< "$_hit"
  fi
done <<< "$_r115_files"
if [[ -n "$_r115_hits" ]]; then
  _r115_first=$(echo "$_r115_hits" | grep -v '^$' | head -5 | tr '\n' '|')
  fail_rule "no_version_log_metadata_in_code" "production code contains forbidden version/log metadata tokens (rc<N> Wave / per ADR-NNNN / Finding F<N> / closes #<N>); first hits: ${_r115_first}-- Rule D-9 / E163 (such metadata belongs in commit messages, ADRs, release notes, rule cards, or rule-history.md — NOT in implementation)"
  _r115_fail=1
fi
if [[ $_r115_fail -eq 0 ]]; then pass_rule "no_version_log_metadata_in_code"; fi

# ---------------------------------------------------------------------------
