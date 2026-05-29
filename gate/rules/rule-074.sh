#!/usr/bin/env bash
# Auto-extracted from gate/check_architecture_sync.sh by gate/lib/extract_rules.sh
# Rule 74 — linux_first_dev_doc_present. DO NOT HAND-EDIT — re-run extract_rules.sh to refresh.
# Authority: PR-E5.

# Rule 74 — linux_first_dev_doc_present (enforcer E104)
#
# docs/governance/dev-environment.md MUST exist and MUST mention all three
# of: WSL2 (preferred), WSL1 (fallback), and Linux (native). The doc is the
# canonical guide an engineer reads when first joining the project; its
# absence (or absence of the Linux-first recommendation) signals the policy
# has been silently weakened.
# ---------------------------------------------------------------------------
_r74_fail=0
_r74_doc='docs/governance/dev-environment.md'
if [[ ! -f "$_r74_doc" ]]; then
  fail_rule "linux_first_dev_doc_present" "$_r74_doc missing -- Rule 74 requires the canonical Linux-first setup guide on disk"
  _r74_fail=1
else
  _r74_missing=""
  for _r74_kw in "WSL2" "WSL1" "Linux"; do
    if ! grep -qF "$_r74_kw" "$_r74_doc" 2>/dev/null; then
      _r74_missing+="${_r74_kw} "
    fi
  done
  if [[ -n "$_r74_missing" ]]; then
    fail_rule "linux_first_dev_doc_present" "$_r74_doc missing required Linux-first keywords: ${_r74_missing}-- Rule 74 requires the doc to recommend WSL2, WSL1, and native Linux"
    _r74_fail=1
  fi
fi
if [[ $_r74_fail -eq 0 ]]; then pass_rule "linux_first_dev_doc_present"; fi

# ===========================================================================
# Wave 4 — small rule activations (2026-05-18)
# ===========================================================================

# ---------------------------------------------------------------------------
