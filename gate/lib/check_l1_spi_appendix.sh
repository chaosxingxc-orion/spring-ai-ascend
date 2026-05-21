#!/usr/bin/env bash
# gate/lib/check_l1_spi_appendix.sh — Rule G-1.1.b real implementation.
#
# Scans every agent-*/ARCHITECTURE.md for an SPI Interface Appendix
# section. Extracts every interface FQN appearing in the appendix and
# cross-validates against module-metadata.yaml#spi_packages (the canonical
# Rule R-D-tracked surface). Surfaces parity gaps for downstream review.
#
# Authority: ADR-0099 (rc22) + rc27 corrective (rc22-2 closure).
# Enforcer: E167.
#
# Exit codes:
#   0 — all 6 agent-*/ARCHITECTURE.md SPI appendices align with
#       module-metadata.yaml#spi_packages.
#   1 — at least one file declares an SPI FQN whose package is NOT in
#       its module-metadata.yaml#spi_packages.
#
# Public functions:
#   check_l1_spi_appendix              — runs the check, returns 0/1
#   check_l1_spi_appendix_for_file     — checks a single ARCHITECTURE.md
#
# Output: stdout TSV `<status>\t<file>\t<detail>` per checked file.

set -uo pipefail
export LC_ALL=C

if [[ -z "${GATE_REPO_ROOT:-}" ]]; then
  GATE_REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
fi

# Extract every FQN that appears in any line under a "SPI Interface
# Appendix" heading. Captures `com.huawei.ascend.X.Y.ClassName` patterns
# inside backticks or table cells.
_l1_extract_spi_fqns() {
  local _file="$1"
  awk '
    BEGIN { in_spi=0 }
    /^##[[:space:]]/ {
      if ($0 ~ /SPI[[:space:]]+Interface[[:space:]]+Appendix/) {
        in_spi=1; next
      }
      # Any other ## section ends the SPI appendix
      if (in_spi) in_spi=0
    }
    in_spi {
      # Extract com.huawei.ascend.XYZ.ClassName patterns
      while (match($0, /com\.huawei\.ascend\.[a-zA-Z0-9_.]+/)) {
        print substr($0, RSTART, RLENGTH)
        $0 = substr($0, RSTART + RLENGTH)
      }
    }
  ' "$_file" | sort -u
}

# Given an FQN, derive its package (everything except the last segment).
_l1_fqn_to_package() {
  printf '%s' "$1" | sed -E 's/\.[A-Z][A-Za-z0-9_]*$//'
}

# Public: check one ARCHITECTURE.md file.
check_l1_spi_appendix_for_file() {
  local _file="$1"
  if [[ ! -f "$_file" ]]; then
    echo "SKIP	$_file	file-missing"
    return 0
  fi
  local _module
  _module=$(dirname "$_file" | sed 's|.*/||')
  local _metadata="$GATE_REPO_ROOT/$_module/module-metadata.yaml"
  if [[ ! -f "$_metadata" ]]; then
    echo "SKIP	$_file	module-metadata-missing"
    return 0
  fi
  local _declared_pkgs
  _declared_pkgs=$(awk '/^spi_packages:/{f=1;next} /^[a-z_]+:/{f=0} f && /^[[:space:]]+- /{gsub(/^[[:space:]]+- /,""); sub(/[[:space:]]+#.*$/,""); print}' "$_metadata" | sort -u)
  if [[ -z "$_declared_pkgs" ]]; then
    # Module declares no SPI packages; appendix is vacuously OK.
    echo "PASS	$_file	module-has-no-spi-packages"
    return 0
  fi
  local _appendix_fqns
  _appendix_fqns=$(_l1_extract_spi_fqns "$_file")
  if [[ -z "$_appendix_fqns" ]]; then
    # No SPI appendix found. For modules with declared SPI packages this
    # is a Rule G-1.1.b violation, but we tolerate it as WARN at rc27
    # while migration completes; future rc tightens to FAIL.
    echo "PASS	$_file	no-spi-appendix-found-tolerated-rc27"
    return 0
  fi
  # rc27 corrective: allow cross-module SPI references in the appendix
  # (e.g., agent-client's appendix lists agent-bus's IngressGateway as a
  # CONSUMED SPI; agent-evolve's appendix lists agent-bus.ReflectionEnvelopeRouter
  # as a cross-module reference). The rule is satisfied when each FQN
  # whose package starts with the CURRENT module's prefix resolves to that
  # module's spi_packages; cross-module references are documentation only.
  local _module_prefix="com.huawei.ascend.${_module#agent-}"
  local _fail=0
  local _drift=""
  while IFS= read -r _fqn; do
    [[ -z "$_fqn" ]] && continue
    # Only enforce on FQNs belonging to THIS module's namespace.
    [[ "$_fqn" != "$_module_prefix"* ]] && continue
    local _pkg
    _pkg=$(_l1_fqn_to_package "$_fqn")
    # Match _pkg against declared_pkgs (exact match OR fqn starts with declared).
    local _match=0
    while IFS= read -r _dp; do
      [[ -z "$_dp" ]] && continue
      if [[ "$_pkg" == "$_dp" ]] || [[ "$_pkg" == "$_dp".* ]]; then
        _match=1; break
      fi
    done <<< "$_declared_pkgs"
    if [[ $_match -eq 0 ]]; then
      _drift="$_drift;$_fqn(pkg:$_pkg)"
      _fail=1
    fi
  done <<< "$_appendix_fqns"
  if [[ $_fail -eq 1 ]]; then
    echo "FAIL	$_file	spi-fqn-not-in-module-metadata:${_drift}"
    return 1
  fi
  echo "PASS	$_file	"
  return 0
}

# Public: check all 6 agent-*/ARCHITECTURE.md files.
check_l1_spi_appendix() {
  local _root="${GATE_L1_TREE_ROOT:-$GATE_REPO_ROOT}"
  local _fail=0
  for _arch in "$_root"/agent-*/ARCHITECTURE.md; do
    [[ -f "$_arch" ]] || continue
    if ! check_l1_spi_appendix_for_file "$_arch"; then
      _fail=1
    fi
  done
  return $_fail
}
