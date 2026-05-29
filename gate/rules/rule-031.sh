#!/usr/bin/env bash
# Auto-extracted from gate/check_architecture_sync.sh by gate/lib/extract_rules.sh
# Rule 31 — quickstart_present. DO NOT HAND-EDIT — re-run extract_rules.sh to refresh.
# Authority: PR-E5.

# Rule 31 — quickstart_present (enforcer E49, CLAUDE.md Rule 29 / ADR-0064)
#
# docs/quickstart.md MUST exist and MUST be referenced from README.md so a
# developer can reach first-agent execution without platform-team intervention.
# ---------------------------------------------------------------------------
_r31_fail=0
if [[ ! -f "docs/quickstart.md" ]]; then
  fail_rule "quickstart_present" "docs/quickstart.md is missing (CLAUDE.md Rule 29 / ADR-0064)"
  _r31_fail=1
fi
if [[ -f "README.md" ]] && ! grep -q "docs/quickstart.md" "README.md" 2>/dev/null; then
  fail_rule "quickstart_present" "README.md does not reference docs/quickstart.md (CLAUDE.md Rule 29)"
  _r31_fail=1
fi
if [[ $_r31_fail -eq 0 ]]; then pass_rule "quickstart_present"; fi

# ---------------------------------------------------------------------------
