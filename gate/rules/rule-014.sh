#!/usr/bin/env bash
# Auto-extracted from gate/check_architecture_sync.sh by gate/lib/extract_rules.sh
# Rule 14 — module_arch_method_name_truth. DO NOT HAND-EDIT — re-run extract_rules.sh to refresh.
# Authority: PR-E5.

# Rule 14 — module_arch_method_name_truth
# ADR-0036: method names in code-fence blocks in agent-service/ARCHITECTURE.md
# and agent-service/ARCHITECTURE.md must exist in the named Java class.
# Currently checks the specific known drift: probe.check() was wrong; correct
# is probe.probe(). Fails if probe.check() appears in any module ARCHITECTURE.md.
# ---------------------------------------------------------------------------
_r14_fail=0
for _maf in architecture/docs/L1/agent-*.md architecture/docs/L1/agent-service/ARCHITECTURE.md; do
  if [[ -f "$_maf" ]]; then
    if grep -q 'probe\.check()' "$_maf" 2>/dev/null; then
      fail_rule "module_arch_method_name_truth" "$_maf references probe.check() but actual method in OssApiProbe is probe.probe(). Per ADR-0036 Gate Rule 14 method names in docs must match source."
      _r14_fail=1
    fi
  fi
done
if [[ $_r14_fail -eq 0 ]]; then pass_rule "module_arch_method_name_truth"; fi

# ---------------------------------------------------------------------------
