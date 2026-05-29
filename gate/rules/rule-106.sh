#!/usr/bin/env bash
# Auto-extracted from gate/check_architecture_sync.sh by gate/lib/extract_rules.sh
# Rule 106 — cross_authority_parity. DO NOT HAND-EDIT — re-run extract_rules.sh to refresh.
# Authority: PR-E5.

# Rule 106 — cross_authority_parity (enforcers E146 + E147 + E148 + E149)
#
# Closes rc13 post-ratchet review P1-5 (L-δ family): the single-surface
# scanners (Rule 87/94/98/101) all passed while canonical surfaces still
# disagreed with each other. Rule 106 is the cross-authority parity gate
# implementing CLAUDE.md Rule G-8 sub-clauses .a/.b/.c/.d:
#   (a) graph baseline parity (architecture-status vs architecture-graph)
#   (b) SPI path parity (kernel rule SPI paths vs module-metadata vs disk)
#   (c) module topology parity (pom.xml vs repository_counts vs metadata files)
#   (d) current-claim grammar (post-ADR-NNNN marker does NOT exempt present-
#       tense verbs naming deleted modules — only explicitly historical
#       markers do).
# Per ADR-0090 (rc14 cross-authority parity wave).
# ---------------------------------------------------------------------------
_r106_fail=0

# --- (a) Graph baseline parity ---
_r106_graph="docs/governance/architecture-graph.yaml"
_r106_status="docs/governance/architecture-status.yaml"
if [[ -f "$_r106_graph" && -f "$_r106_status" ]]; then
  _r106_nodes_live=$(awk '/^node_count:/{print $2; exit}' "$_r106_graph")
  _r106_edges_live=$(awk '/^edge_count:/{print $2; exit}' "$_r106_graph")
  _r106_nodes_baseline=$(awk '/^[[:space:]]+architecture_graph_nodes:/{print $2; exit}' "$_r106_status")
  _r106_edges_baseline=$(awk '/^[[:space:]]+architecture_graph_edges:/{print $2; exit}' "$_r106_status")
  if [[ -z "$_r106_nodes_live" || -z "$_r106_nodes_baseline" || "$_r106_nodes_live" != "$_r106_nodes_baseline" ]]; then
    fail_rule "cross_authority_parity" "graph node_count parity: architecture-graph.yaml#node_count=$_r106_nodes_live but architecture-status.yaml#baseline_metrics.architecture_graph_nodes=$_r106_nodes_baseline -- Rule 106 / E146 (Rule G-8.a)"
    _r106_fail=1
  fi
  if [[ -z "$_r106_edges_live" || -z "$_r106_edges_baseline" || "$_r106_edges_live" != "$_r106_edges_baseline" ]]; then
    fail_rule "cross_authority_parity" "graph edge_count parity: architecture-graph.yaml#edge_count=$_r106_edges_live but architecture-status.yaml#baseline_metrics.architecture_graph_edges=$_r106_edges_baseline -- Rule 106 / E146 (Rule G-8.a)"
    _r106_fail=1
  fi
fi

# --- (b) SPI path parity ---
# Extract every SPI package literal mentioned in CLAUDE.md (the canonical
# kernel authority — rule cards under docs/governance/rules/ may quote
# historical-defect literals as documentation, so they are intentionally
# excluded from this scan).
# Pattern: com.huawei.ascend.<seg>(.<seg>)*.spi(.<seg>)* — anchored so a trailing
# dot followed by an UpperCase Java identifier (e.g. .IngressGateway) does not
# leak into the captured token. Verify each appears in some
# module-metadata.yaml spi_packages entry AND a directory exists on disk.
_r106_kernel_spis=$(grep -hoE 'ascend\.springai(\.[a-z][a-z0-9_]*)+\.spi((\.[a-z][a-z0-9_]*)+)?' \
                    CLAUDE.md 2>/dev/null \
                    | sort -u || true)
_r106_metadata_spis=$(grep -hE '^\s*-\s*ascend\.springai\.' */module-metadata.yaml 2>/dev/null \
                      | sed -E 's/^\s*-\s*//' | awk '{print $1}' | sort -u || true)
for _r106_pkg in $_r106_kernel_spis; do
  if ! grep -qFx "$_r106_pkg" <(printf '%s\n' "$_r106_metadata_spis") 2>/dev/null; then
    fail_rule "cross_authority_parity" "kernel-mentioned SPI package $_r106_pkg has no module-metadata.yaml#spi_packages entry -- Rule 106 / E147 (Rule G-8.b)"
    _r106_fail=1
    continue
  fi
  _r106_path=$(echo "$_r106_pkg" | tr '.' '/')
  _r106_disk_found=""
  for _r106_mod in */src/main/java; do
    [[ -d "$_r106_mod/$_r106_path" ]] && _r106_disk_found="$_r106_mod/$_r106_path" && break
  done
  if [[ -z "$_r106_disk_found" ]]; then
    fail_rule "cross_authority_parity" "kernel-mentioned SPI package $_r106_pkg has no directory under any agent-*/src/main/java/ -- Rule 106 / E147 (Rule G-8.b)"
    _r106_fail=1
  fi
done

# --- (c) Module topology parity ---
_r106_pom_modules=$(awk '/<modules>/,/<\/modules>/' pom.xml 2>/dev/null \
                    | grep -oE '<module>[^<]+</module>' \
                    | sed -E 's,</?module>,,g' | sort -u || true)
_r106_pom_count=$(echo -n "$_r106_pom_modules" | grep -c . || true)
_r106_reactor_declared=$(awk '/^[[:space:]]+reactor_modules:/{print $2; exit}' "$_r106_status")
_r106_metadata_files=$(find . -maxdepth 2 -name module-metadata.yaml -type f 2>/dev/null \
                       | grep -v '^./target/' | sort -u | wc -l | tr -d ' ')
if [[ -n "$_r106_reactor_declared" && "$_r106_pom_count" != "$_r106_reactor_declared" ]]; then
  fail_rule "cross_authority_parity" "pom.xml has $_r106_pom_count <module> entries but architecture-status.yaml#repository_counts.reactor_modules=$_r106_reactor_declared -- Rule 106 / E148 (Rule G-8.c)"
  _r106_fail=1
fi
if [[ "$_r106_pom_count" -gt 0 && "$_r106_metadata_files" != "$_r106_pom_count" ]]; then
  fail_rule "cross_authority_parity" "pom.xml has $_r106_pom_count <module> entries but found $_r106_metadata_files module-metadata.yaml files on disk -- Rule 106 / E148 (Rule G-8.c)"
  _r106_fail=1
fi
# "each of the N (reactor )?modules" prose count parity. Scope:
# authority surfaces only (root + module ARCHITECTURE.md + architecture-status.yaml
# + contract catalog). docs/governance/rules/*.md is intentionally excluded
# because rule cards may quote historical-defect literals when documenting
# the patterns they prevent.
_r106_prose_hits=$(grep -rnE 'each of the [0-9]+ (reactor )?modules' \
                   --include='*.md' --include='*.yaml' \
                   ARCHITECTURE.md architecture/docs/L1/agent-*.md architecture/docs/L1/agent-service/ARCHITECTURE.md docs/governance/architecture-status.yaml docs/contracts/contract-catalog.md 2>/dev/null \
                   | grep -v 'docs/archive/' | grep -v 'docs/logs/' || true)
while IFS= read -r _r106_line; do
  [[ -z "$_r106_line" ]] && continue
  _r106_n=$(echo "$_r106_line" | grep -oE 'each of the [0-9]+ ' | grep -oE '[0-9]+' | head -1)
  if [[ -n "$_r106_n" && -n "$_r106_pom_count" && "$_r106_n" != "$_r106_pom_count" ]]; then
    # Allow if line carries a historical marker
    if ! echo "$_r106_line" | grep -qE '(formerly|historical|pre-rc13|pre-rc12|pre-Phase-C|until dissolved|was consolidated|was extracted|was dissolved|narration)'; then
      fail_rule "cross_authority_parity" "$_r106_line -- says 'each of the $_r106_n modules' but pom.xml has $_r106_pom_count -- Rule 106 / E148 (Rule G-8.c)"
      _r106_fail=1
    fi
  fi
done <<< "$_r106_prose_hits"

# --- (d) Current-claim grammar (post-ADR-NNNN marker is NOT historical) ---
# Scope: authority surfaces only (root ARCHITECTURE.md + architecture/docs/L1/agent-*.md architecture/docs/L1/agent-service/ARCHITECTURE.md
# + architecture-status.yaml + contract-catalog.md). docs/governance/rules/*.md
# is intentionally excluded — rule cards document patterns, including the
# patterns they prevent (so they legitimately quote old prose).
# rc15 widening (per ADR-0091): noun-phrase additions (`shared kernel in`,
# `extracted to`, `is deployed`) close the rc14 M-β gap.
_r106_grammar_hits=$(grep -rnE '(agent-platform|agent-runtime-core|agent-runtime[^-])' \
                     --include='*.md' --include='*.yaml' \
                     docs/governance/architecture-status.yaml ARCHITECTURE.md architecture/docs/L1/agent-*.md architecture/docs/L1/agent-service/ARCHITECTURE.md docs/contracts/contract-catalog.md docs/contracts/s2c-callback.v1.yaml 2>/dev/null \
                     | grep -v 'docs/archive/' | grep -v 'docs/logs/' \
                     | grep -E '(now reads|lives in|^[^#]*\bdeclares\b|each of the [0-9]+ (reactor )?modules|shared kernel in|extracted to|is deployed)' \
                     | grep -vE '(formerly|historical|until dissolved|pre-rc13|pre-rc12|pre-Phase-C|narration|dissolved|relocated|was consolidated|was extracted|was dissolved|<!--)' || true)
if [[ -n "$_r106_grammar_hits" ]]; then
  _r106_first=$(echo "$_r106_grammar_hits" | head -3 | tr '\n' '|')
  fail_rule "cross_authority_parity" "present-tense verb/noun-phrase naming deleted module without explicitly-historical marker (post-ADR-NNNN alone is NOT historical per Rule G-8.d): ${_r106_first}-- Rule 106 / E149 (Rule G-8.d)"
  _r106_fail=1
fi

# --- (e) Structural-carrier parity (rc15 — Rule G-8.e / E150 per ADR-0091) ---
# Scope: every NON-SPI structural-carrier row in docs/contracts/contract-catalog.md
# that follows the syntax: `| <ClassName> | <module> (`<...package>`) | <desc> |`
# For each row, the package path + class file MUST resolve on disk under
# <module>/src/main/java/<package-path>/<ClassName>.java.
# Carrier class list is the union of:
#   - Sealed/structural records in the catalog (EngineRegistry, EngineEnvelope,
#     Run, RunContext, SuspendSignal, S2cCallbackEnvelope, S2cCallbackResponse,
#     IngressEnvelope, IngressResponse, IdempotencyRecord, etc.)
# The scan extracts these directly from the catalog table rows by syntax
# rather than a hardcoded list, so new carriers added to the catalog are
# automatically covered.
_r106_catalog="docs/contracts/contract-catalog.md"
if [[ -f "$_r106_catalog" ]]; then
  # Extract structural-carrier rows: pattern `| `<ClassName>` | `<module>` (`<...package>`) |`
  # Capture: class name, module name, package suffix (after the `...`)
  while IFS=$'\t' read -r _r106_class _r106_module _r106_pkg_suffix; do
    [[ -z "$_r106_class" || -z "$_r106_module" || -z "$_r106_pkg_suffix" ]] && continue
    # Reconstruct full package path (com.huawei.ascend.<suffix>) — convert "..." prefix to "com.huawei.ascend."
    _r106_full_pkg="com.huawei.ascend.${_r106_pkg_suffix#...}"
    _r106_path="$(echo "$_r106_full_pkg" | tr '.' '/')"
    _r106_java_file="${_r106_module}/src/main/java/${_r106_path}/${_r106_class}.java"
    if [[ ! -f "$_r106_java_file" ]]; then
      fail_rule "cross_authority_parity" "contract-catalog.md structural-carrier row '${_r106_class}' claims package '${_r106_full_pkg}' under module '${_r106_module}' but file '${_r106_java_file}' does not exist on disk -- Rule 106 / E150 (Rule G-8.e per ADR-0091)"
      _r106_fail=1
    fi
  done < <(awk -F'`' '
    # Match catalog rows like: | `EngineRegistry` | `agent-execution-engine` (`...engine.runtime`) | ...
    /^\| `[A-Z][A-Za-z]+` \| `agent-[a-z-]+` \(`\.\.\.[a-z._]+`\)/ {
      cls = $2
      mod = $4
      # Package suffix is between the parens — capture from field 6 ($6)
      pkg = $6
      print cls "\t" mod "\t" pkg
    }
  ' "$_r106_catalog")
fi

if [[ $_r106_fail -eq 0 ]]; then pass_rule "cross_authority_parity"; fi

# ---------------------------------------------------------------------------
