#!/usr/bin/env bash
# Auto-extracted from gate/check_architecture_sync.sh by gate/lib/extract_rules.sh
# Rule 43 — new_adr_must_be_yaml. DO NOT HAND-EDIT — re-run extract_rules.sh to refresh.
# Authority: PR-E5.

# Rule 43 — new_adr_must_be_yaml (enforcer E62, Phase M D2)
#
# The highest-numbered ADR file under docs/adr/NNNN-*.{md,yaml} MUST have the
# .yaml extension. This prevents future ADRs from regressing to the legacy
# .md shape after ADR-0068 mandated YAML.
# ---------------------------------------------------------------------------
_r43_fail=0
_r43_top_md="$(find docs/adr -maxdepth 1 -type f -name '[0-9][0-9][0-9][0-9]-*.md' 2>/dev/null | sort -r | head -1 || true)"
_r43_top_yaml="$(find docs/adr -maxdepth 1 -type f -name '[0-9][0-9][0-9][0-9]-*.yaml' 2>/dev/null | sort -r | head -1 || true)"
_r43_top_md_n="$(basename "${_r43_top_md:-0000-x.md}" 2>/dev/null | cut -c1-4)"
_r43_top_yaml_n="$(basename "${_r43_top_yaml:-0000-x.yaml}" 2>/dev/null | cut -c1-4)"
# Force base-10 (4-digit ADR ids can have leading zeros which bash otherwise reads as octal,
# making "0068" / "0099" invalid in arithmetic comparisons).
if (( 10#${_r43_top_md_n:-0} > 10#${_r43_top_yaml_n:-0} )); then
  fail_rule "new_adr_must_be_yaml" "highest-numbered ADR is $_r43_top_md (.md) — ADR-0068 / Rule 33 mandates all new ADRs be .yaml; rename or migrate"
  _r43_fail=1
fi
if [[ $_r43_fail -eq 0 ]]; then pass_rule "new_adr_must_be_yaml"; fi

# ---------------------------------------------------------------------------
