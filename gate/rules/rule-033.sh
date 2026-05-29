#!/usr/bin/env bash
# Auto-extracted from gate/check_architecture_sync.sh by gate/lib/extract_rules.sh
# Rule 33 — release_note_references_four_pillars. DO NOT HAND-EDIT — re-run extract_rules.sh to refresh.
# Authority: PR-E5.

# Rule 33 — release_note_references_four_pillars (enforcer E51, ADR-0065)
#
# The most recent release note under docs/logs/releases/ MUST mention all four
# pillar names by name so reviewers see the dimensions tracked per release.
# ---------------------------------------------------------------------------
_r33_fail=0
_latest_release="$(latest_release_path docs/logs/releases || true)"
if [[ -z "$_latest_release" ]]; then
  pass_rule "release_note_references_four_pillars"   # no release notes yet — vacuous pass
else
  _missing_pillars=""
  for _p in performance cost developer_onboarding governance; do
    if ! grep -qiE "\b${_p}\b" "$_latest_release" 2>/dev/null; then
      _missing_pillars="${_missing_pillars} ${_p}"
    fi
  done
  if [[ -n "$_missing_pillars" ]]; then
    fail_rule "release_note_references_four_pillars" "$(basename "$_latest_release") does not mention pillar(s):${_missing_pillars} (CLAUDE.md Rule 30 / ADR-0065)"
    _r33_fail=1
  fi
fi
if [[ $_r33_fail -eq 0 ]] && [[ -n "$_latest_release" ]]; then pass_rule "release_note_references_four_pillars"; fi

# ---------------------------------------------------------------------------
