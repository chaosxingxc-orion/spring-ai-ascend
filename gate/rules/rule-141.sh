#!/usr/bin/env bash
# Auto-extracted from gate/check_architecture_sync.sh by gate/lib/extract_rules.sh
# Rule 141 — old_orchestration_spi_package_ban. DO NOT HAND-EDIT — re-run extract_rules.sh to refresh.
# Authority: PR-E5.

# Rule 141 — old_orchestration_spi_package_ban (enforcer E189, kernel Rule G-24)
#
# Authority: ADR-0158. Closes external review F6.3: active authority surfaces
# MUST NOT name the old orchestration/engine SPI package
# `engine.orchestration.spi` / `engine/orchestration/spi` as the CURRENT home
# (ADR-0158 re-homed it to com.huawei.ascend.bus.spi.engine). Past-tense
# re-home / dissolution prose is legitimate and skipped via per-line historical
# markers. Scoped to current-authority surfaces (templates + rendered docs +
# inline authority); excludes docs/logs, docs/adr, rule-history, archive,
# scripts, and generated artefacts.
#
# NOTE: the rendered architecture/docs/**.md are re-rendered from the fixed .j2
# in a later wave (W6). The live-tree PASS of this rule is validated POST-RENDER
# (W8); the .j2 sources + inline surfaces are already clean.
#
# scope_surfaces: docs/governance/templates/*.j2, architecture/docs/*.md, docs/dfx/*, docs/quickstart.md, docs/cross-cutting/oss-bill-of-materials.md, docs/governance/enforcers.yaml, docs/governance/architecture-status.yaml, docs/contracts/contract-catalog.md, architecture/features/*, gate/lib/check_old_orchestration_spi_package.py
# ---------------------------------------------------------------------------
_r141_fail=0
_r141_helper="gate/lib/check_old_orchestration_spi_package.py"
if [[ ! -f "$_r141_helper" ]]; then
  fail_rule "old_orchestration_spi_package_ban" "$_r141_helper missing -- Rule G-24 / E189"
  _r141_fail=1
elif [[ -z "$GATE_PYTHON_BIN" ]]; then
  : # vacuous pass on hosts without python (Rule G-7 lists WSL as canonical env)
else
  _r141_out=$("$GATE_PYTHON_BIN" "$_r141_helper" 2>&1)
  _r141_rc=$?
  if [[ $_r141_rc -ne 0 ]]; then
    _r141_count=$(printf '%s\n' "$_r141_out" | grep -cE '^OLD-PACKAGE:')
    _r141_first=$(printf '%s' "$_r141_out" | grep -E '^OLD-PACKAGE:' | head -1)
    fail_rule "old_orchestration_spi_package_ban" "active authority surface(s) name the old engine.orchestration.spi package as current ($_r141_count line(s), first: ${_r141_first:-rc=$_r141_rc}) -- Rule G-24 / E189"
    _r141_fail=1
  fi
fi
[[ $_r141_fail -eq 0 ]] && pass_rule "old_orchestration_spi_package_ban"

# ---------------------------------------------------------------------------
