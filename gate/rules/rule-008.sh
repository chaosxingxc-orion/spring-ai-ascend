#!/usr/bin/env bash
# Auto-extracted from gate/check_architecture_sync.sh by gate/lib/extract_rules.sh
# Rule 8 — no_hardcoded_versions_in_arch. DO NOT HAND-EDIT — re-run extract_rules.sh to refresh.
# Authority: PR-E5.

# Rule 8 — no_hardcoded_versions_in_arch
# module ARCHITECTURE.md files (agent-platform/, agent-runtime/) must not
# pin OSS versions inline (e.g., "Spring Boot 3.2.1" or "Java 21.0.2").
# ---------------------------------------------------------------------------
_r8_fail=0
for _arch in 'agent-service/ARCHITECTURE.md' 'agent-service/ARCHITECTURE.md'; do
  if [[ -f "$_arch" ]]; then
    if grep -qE '[0-9]+\.[0-9]+\.[0-9]+' "$_arch" 2>/dev/null; then
      fail_rule "no_hardcoded_versions_in_arch" "$_arch contains inline version pin (x.y.z pattern). Move version pins to pom.xml or oss-bill-of-materials.md."
      _r8_fail=1
    fi
  fi
done
if [[ $_r8_fail -eq 0 ]]; then pass_rule "no_hardcoded_versions_in_arch"; fi

# ---------------------------------------------------------------------------
