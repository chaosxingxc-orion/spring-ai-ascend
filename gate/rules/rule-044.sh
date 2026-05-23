#!/usr/bin/env bash
# Auto-extracted from gate/check_architecture_sync.sh by gate/lib/extract_rules.sh
# Rule 44 — frozen_doc_edit_path_compliance. DO NOT HAND-EDIT — re-run extract_rules.sh to refresh.
# Authority: PR-E5 (D:/.claude/plans/spicy-mixing-galaxy.md).

# Rule 44 — frozen_doc_edit_path_compliance (enforcer E63, Phase M D4)
#
# For every architecture artefact declaring `freeze_id: <non-null>` in its
# front-matter, any modification to that file in the working tree (vs the
# merge base) MUST be accompanied by a NEW docs/logs/reviews/*.md proposal in the
# same commit naming the file under `affects_artefact:`. No-op today (all
# freeze_id values are null); arms automatically when a doc is phase-released.
# ---------------------------------------------------------------------------
_r44_fail=0
_r44_base="${BASE_REF:-origin/main}"
# Collect frozen-doc paths.
_r44_frozen=""
for _f44 in ARCHITECTURE.md $(find . -maxdepth 2 -type f -name 'ARCHITECTURE.md' ! -path './ARCHITECTURE.md' 2>/dev/null || true) \
            $(find docs/L2 -type f -name '*.md' 2>/dev/null || true) \
            $(find docs/adr -maxdepth 1 -type f -name '*.yaml' 2>/dev/null || true); do
  [[ -z "$_f44" || ! -f "$_f44" ]] && continue
  _fid="$(awk 'BEGIN{in_fm=0; n=0} /^---[[:space:]]*$/{n++; if(n==1){in_fm=1; next} if(n==2){exit}} in_fm && /^freeze_id:[[:space:]]/{sub(/^freeze_id:[[:space:]]*/,""); sub(/[[:space:]]*$/,""); print; exit}' "$_f44" 2>/dev/null)"
  # YAML ADR (top-level key, no front-matter delimiters)
  if [[ -z "$_fid" ]]; then
    _fid="$(grep -E '^freeze_id:[[:space:]]' "$_f44" 2>/dev/null | head -1 | sed -E 's/^freeze_id:[[:space:]]*([A-Za-z0-9._-]+).*/\1/')"
  fi
  if [[ -n "$_fid" && "$_fid" != "null" ]]; then
    _r44_frozen="${_r44_frozen}${_f44}\n"
  fi
done
# If git is available and a base ref is reachable, check each frozen doc for
# modifications without an accompanying review proposal.
# rc18 Wave 2 (ADR-0095): shallow-clone fail-closed safeguard — if running on
# a shallow clone (CI without fetch-depth: 0), git diff against base ref may
# silently return empty even when the file changed. Fail loudly instead.
if [[ -n "$_r44_frozen" ]] && command -v git >/dev/null 2>&1 && git rev-parse --verify "$_r44_base" >/dev/null 2>&1; then
  _r44_is_shallow=$(git rev-parse --is-shallow-repository 2>/dev/null)
  if [[ "$_r44_is_shallow" == "true" ]]; then
    fail_rule "frozen_doc_edit_path_compliance" "cannot evaluate frozen-doc edit compliance on shallow clone (run git fetch --unshallow in CI; CI workflows must set actions/checkout fetch-depth: 0) -- Rule 44 / E63 (rc18 Wave 2 fix per ADR-0095)"
    _r44_fail=1
  fi
  _r44_changed_reviews="$(git diff --name-only --diff-filter=A "$_r44_base" -- 'docs/logs/reviews/*.md' 2>/dev/null || true)"
  while IFS= read -r _f44; do
    [[ -z "$_f44" ]] && continue
    if git diff --name-only "$_r44_base" -- "$_f44" 2>/dev/null | grep -q .; then
      # Frozen doc was modified; require a review proposal naming it in affects_artefact:.
      _accompanied=0
      while IFS= read -r _r44_proposal; do
        [[ -z "$_r44_proposal" ]] && continue
        # rc28 + rc29 fix (ADV-11/NEW-4 + ADV3-4): accept BOTH single-line and
        # multi-line YAML list form. rc29 uses `grep -F` (fixed-string match)
        # instead of `-E` so regex metachars (`.`, `-`, `[`) in `_f44` are
        # treated literally — defeats the regex-injection class where `.`
        # would match any char and falsely accept typo'd paths.
        if grep -qF "affects_artefact:" "$_r44_proposal" 2>/dev/null \
           && grep -qF "$_f44" "$_r44_proposal" 2>/dev/null; then
          _accompanied=1
          break
        fi
      done <<< "$_r44_changed_reviews"
      if [[ $_accompanied -eq 0 ]]; then
        fail_rule "frozen_doc_edit_path_compliance" "$_f44 carries freeze_id but was modified without an accompanying docs/logs/reviews/*.md proposal citing it under affects_artefact:"
        _r44_fail=1
      fi
    fi
  done <<< "$(printf "%b" "$_r44_frozen")"
fi
if [[ $_r44_fail -eq 0 ]]; then pass_rule "frozen_doc_edit_path_compliance"; fi

# ===========================================================================
# W1.x Phase 1 — L0 ironclad-rule enforcers (Gate Rules 45-52)
# Authority: ADR-0069. Each rule fails on a detected violation today.
# ===========================================================================

# ---------------------------------------------------------------------------
