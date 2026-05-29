#!/usr/bin/env bash
# Auto-extracted from gate/check_architecture_sync.sh by gate/lib/extract_rules.sh
# Rule 127 — release_note_no_pending_evidence. DO NOT HAND-EDIT — re-run extract_rules.sh to refresh.
# Authority: PR-E5.

# Rule 127 — release_note_no_pending_evidence (enforcer E175)
#
# Current release notes that claim a shipped / release / closure decision MUST
# NOT carry live placeholder tokens; current review responses are checked too.
# Formal notes must also carry non-placeholder candidate commits.
#
# scope_surfaces: docs/logs/releases/*.md, gate/lib/check_release_note_current_truth.py
# ---------------------------------------------------------------------------
_r127_out=$(python3 gate/lib/check_release_note_current_truth.py --root . 2>&1)
_r127_rc=$?
if [[ $_r127_rc -ne 0 ]]; then
  fail_rule "release_note_no_pending_evidence" "${_r127_out:-latest release note evidence placeholders detected} -- Rule G-2 / E175"
else
  pass_rule "release_note_no_pending_evidence"
fi

# ---------------------------------------------------------------------------
