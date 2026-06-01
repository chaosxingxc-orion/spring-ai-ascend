#!/usr/bin/env bash
# spring-ai-ascend architecture-sync gate.
# Active rule sections: counted from `# Rule N — slug` headers below the prologue
# and above `# === END OF RULES ===`. Rule 91 (rc9) enforces that this count
# matches `architecture-status.yaml#baseline_metrics.active_gate_checks` and the
# trailer that `gate/check_parallel.sh` emits as `parallel_summary: executed N rules`.
# Wave history:
#   rc1 era: L1 Rule-28 expansion + Phase K + L1.x Telemetry Vertical (Rules 1-29 + 28a-28k sub-checks + Rules 30-44).
#   W1.x L0 ironclad-rule wave: Rules 45-52.
#   W1.x Phases 8-9: Rules 53-54.
#   W2.x Engine Contract Structural Wave Phases 1-6: Rules 55-60.
#   v2.0.0-rc2 second-pass closure: Rules 61-63.
#   2026-05-17 cross-corpus consistency audit: Rules 64-66 (enforcers E94-E96).
#   2026-05-17 CLAUDE.md token-optimization wave PR1: Rules 67-71 (enforcers E97-E101).
#   2026-05-17 gate-script efficiency wave: Rules 72-73 (enforcers E102-E103).
#   2026-05-18 Beyond-SDD review response: Rule 74 (Linux-first dev environment) + Rule 79 (Evidence-First Debug Sequence).
#   2026-05-18 SPI metadata integrity wave: Rules 75-78.
#   2026-05-18 rc4 cross-constraint review response: Rules 80-83 (enforcers E113-E116).
#   2026-05-18 rc5 post-response review response: Rules 84-85 (enforcers E117-E118).
#   2026-05-18 rc6 post-response review response: Rules 86-87 (enforcers E119-E120).
#   2026-05-18 rc7 post-corrective review response: Rules 88-89 (enforcers E121-E122).
#   2026-05-19 rc8 post-corrective review response (rc9 wave): Rules 91-96 (enforcers E123-E134).
#   Code whitebox quality baseline: Rule 121 (enforcer E169).
#   Agent-execution-engine readiness prevention: Rules 122-124 (enforcers E170-E172).
#   rc49 agentic-contract-surface corrective prevention: Rules 127-129 (enforcers E175-E177).
# Exits 0 if all rules pass, 1 if any fail.
# Each rule prints PASS: <name> or FAIL: <name> -- <reason>.
# Prints GATE: PASS or GATE: FAIL at the end.
#
# Rules:
#   1.  status_enum_invalid                          -- docs/governance/architecture-status.yaml status values
#   2.  delivery_log_parity                          -- gate/log/*.json sha field matches filename basename
#   3.  eol_policy                                   -- *.sh files in gate/ must be LF (not CRLF)
#   4.  ci_no_or_true_mask                           -- no gate/run_* || true in .github/workflows/*.yml
#   5.  required_files_present                       -- contract-catalog.md and openapi-v1.yaml must exist
#   6.  metric_naming_namespace                      -- springai_ascend_ prefix in Java metric names
#   7.  shipped_impl_paths_exist                     -- every shipped: true implementation: path exists on disk
#   8.  no_hardcoded_versions_in_arch                -- module ARCHITECTURE.md files must not pin OSS versions inline
#   9.  openapi_path_consistency                     -- /v3/api-docs must appear in WebSecurityConfig + platform ARCH
#  10.  module_dep_direction                         -- agent-runtime must not depend on agent-platform (ADR-0055: platform->runtime is now ALLOWED)
#  11.  shipped_envelope_fingerprint_present         -- InMemoryCheckpointer enforces §4 #13 16-KiB cap
#  12.  inmemory_orchestrator_posture_guard_present  -- AppPostureGate.requireDev in all 3 in-memory components (ADR-0035)
#  16.  http_contract_w1_tenant_and_cancel_consistency -- W1 HTTP contract: no replace-X-Tenant-Id wording, no CREATED initial status, no DELETE cancel route, no W0 cancel/idempotency future-state drift
#  17.  contract_catalog_spi_table_matches_source     -- SPI sub-table must list 7 known SPIs; OssApiProbe must not appear before Probes sub-table
#  19.  shipped_row_tests_evidence                    -- every shipped: true row must have non-empty tests: pointing to real files (ADR-0042, strengthened)
#  21.  bom_glue_paths_exist                          -- BoM must not contain known ghost implementation paths unless they exist (ADR-0043)
#  23.  active_doc_internal_links_resolve             -- markdown links ](path) in active docs must resolve to existing files (ADR-0043)
#  24.  shipped_row_evidence_paths_exist              -- l2_documents: and latest_delivery_file: on shipped rows must exist on disk (ADR-0045)
#  --- L1 Rule-28 sub-checks (ADR-0059) ---
#  28a. tenant_column_present                          -- every CREATE TABLE in db/migration declares tenant_id (enforcer E15)
#  28b. high_cardinality_tag_guard                     -- no Tag.of("run_id"|"idempotency_key"|"jwt_sub"|"body", …) in agent-*/main (enforcer E19)
#  28c. no_secret_patterns                             -- gitleaks-style sweep of tracked files; allowlist via 'secret-allowlist:' (enforcer E20)
#  28d. out_of_scope_name_guard                        -- W2+ deferred names absent from agent-*/main (enforcer E26)
#  28e. module_count_invariant                         -- root pom.xml declares exactly 9 <module> entries (enforcer E27; bumped from 4 to 9 by 2026-05-17 six-module materialization PR; canonical count lives in docs/governance/architecture-status.yaml#repository_counts.total_reactor_modules and is data-driven cross-checked by Rule 64)
#  28f. enforcers_yaml_wellformed                      -- docs/governance/enforcers.yaml every row has all 5 fields + legal kind (enforcer E29)
#  28j. enforcer_artifact_paths_exist                   -- every artifact: path in enforcers.yaml resolves on disk (enforcer E33, Phase K audit fix F6)
#  28k. javadoc_enforcer_citation_semantic_check        -- *Test.java/*IT.java Javadoc `enforcers.yaml#E<n>` citations match the E-row's artifact: field (post-review fix plan F / P1-2)
#  30.  telemetry_vertical_constraint_coverage         -- ARCHITECTURE.md §4 #53–#59 each cited by an enforcer row (L1.x Telemetry Vertical, enforcer E47)
#  --- Layer-0 governing principles (ADR-0064..0067) ---
#  32.  competitive_baselines_present_and_wellformed    -- docs/governance/competitive-baselines.yaml has 4 pillars (Rule 30, enforcer E50)
#  33.  release_note_references_four_pillars            -- latest release note mentions all 4 pillars by name (Rule 30, enforcer E51)
#  36.  domain_module_has_spi_package                   -- every kind:domain module declares spi_packages and each one resolves on disk (Rule 32, enforcer E54)
#  --- W1 Layered 4+1 + Architecture Graph (ADR-0068) ---
#  37.  architecture_artefact_front_matter             -- every ARCH/L2/ADR.yaml carries level: + view: front-matter (Rule 33, enforcer E55)
#  38.  architecture_graph_well_formed                 -- generated architecture-graph.yaml builds + validates (Rule 34, enforcer E56)
#  39.  review_proposal_front_matter                   -- docs/logs/reviews/*.md front-matter is OPTIONAL (interaction records); validated only when a doc opts into 4+1 proposal classification (Rule 33, enforcer E57)
#  41.  enforcer_anchor_resolves                       -- every artifact: anchor resolves to real method/heading (Phase M, enforcer E60)
#  42.  architecture_graph_idempotent                  -- twice-run graph build is byte-identical (Phase M, enforcer E61)
#  44.  frozen_doc_edit_path_compliance                -- freeze_id-tagged file edits require docs/logs/reviews/*.md proposal (Phase M, enforcer E63)
#  --- W1.x L0 ironclad-rule enforcers (ADR-0069) ---
#  46.  cursor_flow_documented                         -- openapi-v1.yaml declares TaskCursor schema + x-cursor-flow annotation (Rule 36 / P-F, enforcer E65)
#  49.  deployment_plane_in_module_metadata            -- every module-metadata.yaml declares deployment_plane (Rule 39 / P-I, enforcer E68)
#  50.  rls_for_new_tenant_tables                      -- Flyway migrations with tenant_id enable RLS or are grandfathered (Rule 40 / P-J, enforcer E69)
#  51.  skill_capacity_yaml_present_and_wellformed     -- skill-capacity.yaml schema check (Rule 41 / P-K, enforcer E70)
#  52.  sandbox_policies_yaml_present_and_wellformed   -- sandbox-policies.yaml default_policy 6 keys (Rule 42 / P-L, enforcer E71)
#  --- W1.x Phase 8 — Cursor Flow runtime activation (ADR-0070) ---
#  53.  cursor_flow_integration_test_present           -- RunCursorFlowIT asserts POST /v1/runs returns 202 within 200ms even with a 30s-blocking dispatcher (Rule 36.b / P-F, enforcer E72)
#  --- W1.x Phase 9 — ResilienceContract runtime activation (ADR-0070) ---
#  54.  skill_capacity_runtime_resolver_present        -- DefaultSkillResilienceContract implements resolve(tenant, skill) consulting SkillCapacityRegistry; rejection carries SuspendReason.RateLimited (Rule 41.b / P-K, enforcer E73)
#  --- W2.x Phase 1 — Engine Envelope + Strict Matching (ADR-0072) ---
#  --- W2.x Phase 2 — Engine Hooks + Runtime Middleware SPI (ADR-0073) ---
#  --- W2.x Phase 3 — S2C Capability Callback (ADR-0074) ---
#  58.  s2c_callback_yaml_present_and_wellformed       -- docs/contracts/s2c-callback.v1.yaml declares request+response shape with 6 mandatory request fields and outcome enum (Rule 46 / P-M, enforcer E81)
#  --- W2.x Phase 6 — Schema-First Domain Contracts (ADR-0077, Rule 48) ---
#  --- v2.0.0-rc2 second-pass review closure (F-α / F-β / F-γ category audit) ---
#  62.  contract_yaml_declares_status                   -- every docs/contracts/*.v1.yaml + 3 governance YAMLs declare top-level status: with allowed enum value (F-β structural prevention)
#  --- 2026-05-17 cross-corpus consistency audit prevention rules (G1/G2/G3 closure, enforcers E94-E96) ---
#  --- 2026-05-17 CLAUDE.md token-optimization wave PR1 (enforcers E97-E101) ---
#  --- 2026-05-17 gate-script efficiency wave PR-E1 (enforcer E103) ---
#  --- 2026-05-18 Linux-first dev environment policy (enforcer E104) ---
#  74.  linux_first_dev_doc_present                      -- docs/governance/dev-environment.md exists + recommends WSL2/WSL1/Linux for verification (Rule 74 / PR-E7, enforcer E104)
#  --- 2026-05-18 SPI metadata integrity wave (Rules 75-78; enforcers E105-E111) ---
#  --- 2026-05-18 rc4 cross-constraint review response prevention wave (Rules 80-83; enforcers E113-E116) ---
#  --- 2026-05-18 rc5 post-response review response prevention wave (Rules 84-85; enforcers E117-E118) ---
#  84.  active_module_architecture_path_truth           -- every architecture/docs/L1/agent-*.md architecture/docs/L1/agent-service/ARCHITECTURE.md (status != skeleton|deferred) inline path claim "<module>/src/main/java/..." must resolve on disk OR carry a historical/moved/extracted-per-ADR/superseded/deferred marker within +/-3 lines (rc5 P0-1 prevention, enforcer E117)
#  85.  catalog_spi_row_matches_module_spi_metadata     -- every non-(internal) row in contract-catalog.md SPI table must have its package in <module>/module-metadata.yaml#spi_packages AND docs/dfx/<module>.yaml#spi_packages; the (N total) header MUST equal the non-internal row count (rc5 P1-2 prevention, enforcer E118)
#  --- 2026-05-19 rc10 post-corrective review response prevention wave (Rules 99-100 + Rule 94/98 widening; enforcers E139-E142) ---
#  99.  kernel_terminal_verb_vs_shipped_decision_check  -- For every #### Rule N kernel block in CLAUDE.md with a matching ## Rule N.<letter> sub-clause in CLAUDE-deferred.md, the kernel MUST NOT use end-state verb tokens (`are SUSPENDED`, `is SUSPENDED`, `transitions to FAILED`, `consumes the * capacity`, `is rejected, not failed`, `admits the caller`) that overclaim shipped behaviour. Closes rc10 P1-1 (J-α family; Rule 41 kernel said "callers are SUSPENDED" while shipped code returns SkillResolution.reject — the actual transition is deferred to Rule 41.c).
#  100. kernel_implementation_disjunction_truth        -- For every rule in gate/rule-100-disjunction-allowlist.txt, BOTH the #### Rule N kernel block in CLAUDE.md AND the matching docs/governance/rules/rule-NN.md card MUST contain explicit disjunction wording (EITHER / OR / either surface / either ... or). Closes rc10 P1-3 (J-γ family; Rule 96 kernel said "MUST contain" while impl accepted EITHER kernel OR card — kernel-AND-impl-OR drift in the rule whose job is preventing such drift).
#  121. whitebox_quality_reports                     -- Maven SpotBugs/PMD/Checkstyle reports exist; high-confidence SpotBugs + hard-style Checkstyle findings block, PMD is review-trigger summary (Rule G-12, enforcer E169)

set -uo pipefail
export LC_ALL=C

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$repo_root"

# Resolve a usable Python interpreter once for the whole gate run.
# Linux/macOS/CI typically ship `python3`; Windows ships `python`. Bare
# `python3 - <<PYEOF` invocations elsewhere in this script silently fall
# through to vacuous PASS on hosts without python3 — Rule G-7 lists WSL
# as the canonical execution env, but the parallel runner extracts each
# rule body into its own script so a host-level miss still trips here.
GATE_PYTHON_BIN="${GATE_PYTHON_BIN:-}"
if [[ -z "$GATE_PYTHON_BIN" ]]; then
  if command -v python3 >/dev/null 2>&1; then
    GATE_PYTHON_BIN="python3"
  elif command -v python >/dev/null 2>&1; then
    GATE_PYTHON_BIN="python"
  fi
fi
export GATE_PYTHON_BIN

fail_count=0
export fail_count

pass_rule() { echo "PASS: $1"; }
fail_rule() {
  echo "FAIL: $1 -- $2"
  fail_count=$((fail_count + 1))
}
# rc27 fix (ADV-1): export functions so they survive the `bash -c` subshell
# spawned by the per-rule timeout wrapper in gate/check_parallel.sh.
# Without `export -f`, a fresh bash child sees `fail_rule: command not found`
# and every rule silently passes. This is the most critical fix in rc27 —
# without it, the entire gate is a green-washed no-op.
export -f pass_rule fail_rule 2>/dev/null || true

# ---------------------------------------------------------------------------
# Scan-cache adoption (PR-E3 of the gate-efficiency wave, 2026-05-17).
# Source gate/lib/scan_cache.sh ONCE here so all rules can read the
# pre-computed file lists ($_SCAN_MODULE_METADATA, $_SCAN_ACTIVE_DOCS,
# $_SCAN_MIGRATION_SQL, $_SCAN_AGENT_JAVA_MAIN) instead of each running
# their own find. The cache is conditional on $GATE_SCAN_CACHE_ENABLED
# (loaded from gate/config.yaml via gate/lib/load_config.sh); when disabled,
# the env vars are empty and rules fall back to their original inline find.
# Authority: docs/governance/rules/rule-70.md + PR-E3 plan.
# ---------------------------------------------------------------------------
if [[ -f "$repo_root/gate/lib/load_config.sh" ]]; then
  GATE_REPO_ROOT="$repo_root"
  # shellcheck source=gate/lib/load_config.sh
  source "$repo_root/gate/lib/load_config.sh"
  gate_load_config 2>/dev/null || true
fi
if [[ -f "$repo_root/gate/lib/scan_cache.sh" ]]; then
  # shellcheck source=gate/lib/scan_cache.sh
  source "$repo_root/gate/lib/scan_cache.sh"
fi
if [[ -f "$repo_root/gate/lib/latest_release.sh" ]]; then
  # shellcheck source=gate/lib/latest_release.sh
  source "$repo_root/gate/lib/latest_release.sh"
fi
# PR-Opt-rc22: fast-grep helpers (rg/git-grep/grep auto-fallback + parallel).
# Auto-selects ripgrep when available for 3-10x grep speedup. See file header.
if [[ -f "$repo_root/gate/lib/fast_grep.sh" ]]; then
  # shellcheck source=gate/lib/fast_grep.sh
  source "$repo_root/gate/lib/fast_grep.sh"
fi
# rc27 fix (rc22-2): real Rule G-1.1 helpers (replaces placeholder pass_rule).
if [[ -f "$repo_root/gate/lib/check_l1_dev_view_tree.sh" ]]; then
  source "$repo_root/gate/lib/check_l1_dev_view_tree.sh"
fi
if [[ -f "$repo_root/gate/lib/check_l1_spi_appendix.sh" ]]; then
  source "$repo_root/gate/lib/check_l1_spi_appendix.sh"
fi
if [[ -f "$repo_root/gate/lib/check_whitebox_quality.sh" ]]; then
  # shellcheck source=gate/lib/check_whitebox_quality.sh
  source "$repo_root/gate/lib/check_whitebox_quality.sh"
fi
# rc27 fix (ADV-1 export -f): export fail_rule + pass_rule so they survive
# the `bash -c` subshell spawned by the per-rule timeout wrapper.
# Without this, every rule under the timeout path silently passes because
# `fail_rule: command not found` returns rc=127, the function never
# increments fail_count, and the orchestrator counts the rule as PASS.

# ---------------------------------------------------------------------------
# Rule 7 — shipped_impl_paths_exist
# Every capability row with shipped: true in architecture-status.yaml MUST
# have all its implementation: paths exist on disk.
# ---------------------------------------------------------------------------
_r7_fail=0
_status_file='docs/governance/architecture-status.yaml'
if [[ -n "${_SCAN_SHIPPED_ROWS:-}" ]]; then
  # Fast path (PR-E3.b): one awk pass over the pre-extracted TSV.
  # Selects every (capability, impl_path) where the capability is shipped:true.
  while IFS=$'\t' read -r _r7_cap _r7_path; do
    [[ -z "$_r7_path" ]] && continue
    [[ "$_r7_path" == "null" ]] && continue
    if [[ ! -e "$_r7_path" ]]; then
      fail_rule "shipped_impl_paths_exist" "shipped: true row '$_r7_cap' references non-existent path: $_r7_path"
      _r7_fail=1
    fi
  done < <(printf '%s\n' "$_SCAN_SHIPPED_ROWS" | awk -F'\t' '
    $2=="shipped" && $3=="true" { shipped[$1]=1 }
    $2=="impl" { rows[NR]=$1 "\t" $3 }
    END { for (k in rows) { split(rows[k], a, "\t"); if (a[1] in shipped) print a[1] "\t" a[2] } }
  ')
elif [[ -f "$_status_file" ]]; then
  # Fallback (cache disabled): original per-line scan.
  _in_shipped=0
  while IFS= read -r _line; do
    if echo "$_line" | grep -qE '^\s*shipped:\s*true'; then
      _in_shipped=1
    elif echo "$_line" | grep -qE '^\s*shipped:\s*false'; then
      _in_shipped=0
    elif [[ $_in_shipped -eq 1 ]] && echo "$_line" | grep -qE '^\s*-\s+\S'; then
      _impl_path=$(echo "$_line" | sed -E 's/^\s*-\s+//')
      if [[ -n "$_impl_path" ]] && [[ "$_impl_path" != "null" ]]; then
        if [[ ! -e "$_impl_path" ]]; then
          fail_rule "shipped_impl_paths_exist" "shipped: true row references non-existent path: $_impl_path"
          _r7_fail=1
        fi
      fi
    elif echo "$_line" | grep -qE '^\s*(status|tests|allowed_claim|l0_decision|l2_documents|note):'; then
      _in_shipped=0
    fi
  done < "$_status_file"
fi
if [[ $_r7_fail -eq 0 ]]; then pass_rule "shipped_impl_paths_exist"; fi

# ---------------------------------------------------------------------------
# Rule 10 — module_dep_direction (amended at L1 by ADR-0055; further by ADR-0078)
# Phase C consolidation (ADR-0078) merged agent-platform + agent-runtime into a
# single agent-service Maven module. The cross-module pom direction is no longer
# meaningful: the new invariant is INTRA-MODULE sub-package layering —
#   com.huawei.ascend.service.runtime.* MUST NOT depend on com.huawei.ascend.service.platform.*
# enforced at source level by ArchUnit RuntimeMustNotDependOnPlatformTest (E2).
# At the pom level, this rule asserts agent-service does not regress by adding
# a dependency on a deleted artifact (agent-platform, agent-runtime).
# Enforcer row: docs/governance/enforcers.yaml#E1
# ---------------------------------------------------------------------------
_r10_fail=0
if [[ -f 'agent-service/pom.xml' ]]; then
  for _r10_dead in 'agent-platform' 'agent-runtime'; do
    if grep -q "<artifactId>${_r10_dead}</artifactId>" 'agent-service/pom.xml' 2>/dev/null; then
      fail_rule "module_dep_direction" "agent-service/pom.xml declares dependency on ${_r10_dead}. Per ADR-0078 this artifact was deleted in Phase C consolidation."
      _r10_fail=1
    fi
  done
fi
if [[ $_r10_fail -eq 0 ]]; then pass_rule "module_dep_direction"; fi

# ---------------------------------------------------------------------------
# Rule 12 — inmemory_orchestrator_posture_guard_present
# ADR-0035: AppPostureGate.requireDevForInMemoryComponent is the single
# construction path for posture reads. All three in-memory components MUST
# contain AppPostureGate.requireDev in their source.
# ---------------------------------------------------------------------------
_r12_fail=0
_posture_targets=(
  'agent-service/src/main/java/com/huawei/ascend/service/runtime/orchestration/inmemory/SyncOrchestrator.java'
  'agent-service/src/main/java/com/huawei/ascend/service/runtime/orchestration/inmemory/InMemoryRunRegistry.java'
  'agent-service/src/main/java/com/huawei/ascend/service/runtime/orchestration/inmemory/InMemoryCheckpointer.java'
)
for _pt in "${_posture_targets[@]}"; do
  if [[ -f "$_pt" ]]; then
    if ! grep -q 'AppPostureGate\.requireDev' "$_pt" 2>/dev/null; then
      fail_rule "inmemory_orchestrator_posture_guard_present" "$_pt does not call AppPostureGate.requireDev*. Per ADR-0035 all in-memory components must delegate posture reads to AppPostureGate."
      _r12_fail=1
    fi
  else
    fail_rule "inmemory_orchestrator_posture_guard_present" "$_pt not found on disk."
    _r12_fail=1
  fi
done
if [[ $_r12_fail -eq 0 ]]; then pass_rule "inmemory_orchestrator_posture_guard_present"; fi

# ---------------------------------------------------------------------------
# Rule 16 — http_contract_w1_tenant_and_cancel_consistency
# ADR-0040: (a) no "replace.*X-Tenant-Id" in active docs; (b) http-api-contracts.md
# must not reference CREATED as initial status; (c) openapi-v1.yaml must not
# mention DELETE /v1/runs/{runId} as the cancel mechanism; (d) shipped W0 cancel
# run-owner mismatch must remain 404 not_found; (e) W1 idempotency must not
# promise W2 response replay while architecture-status says replay is deferred.
# ---------------------------------------------------------------------------
_r16_fail=0
# 16a: no forward-looking "will replace X-Tenant-Id" claim in active normative docs
# Exclude docs/adr/: ADRs may legitimately document rejected options and past wrong text.
while IFS= read -r _mdf16; do
  [[ -z "$_mdf16" ]] && continue
  if grep -qE 'TenantContextFilter[[:space:]]+(switches[[:space:]]+to|replaces?([[:space:]]+with)?[[:space:]]+JWT|moves[[:space:]]+to)[[:space:]]+JWT|will[[:space:]]+replace.*X-Tenant-Id|replace[[:space:]]+header-based.*with[[:space:]]+JWT|W1[[:space:]]+replaces.*X-Tenant-Id' "$_mdf16" 2>/dev/null; then
    fail_rule "http_contract_w1_tenant_and_cancel_consistency" "$_mdf16 contains a replacement-implying claim about X-Tenant-Id or TenantContextFilter. Per ADR-0040 W1 adds JWT cross-check; X-Tenant-Id is NOT replaced. Forbidden phrasings: 'switches to JWT', 'replaces with JWT', 'moves to JWT', 'will replace X-Tenant-Id'."
    _r16_fail=1
    break
  fi
done < <(find . -name '*.md' \
  ! -path './docs/archive/*' \
  ! -path './docs/logs/reviews/*' \
  ! -path './docs/adr/*' \
  ! -path './third_party/*' \
  ! -path './target/*' \
  ! -path './.git/*' \
  -type f 2>/dev/null | sort || true)
# 16b: http-api-contracts.md must not say CREATED as initial status
if [[ $_r16_fail -eq 0 ]] && [[ -f 'docs/contracts/http-api-contracts.md' ]]; then
  if grep -qE 'starts in CREATED|CREATED stage|status.*CREATED' 'docs/contracts/http-api-contracts.md' 2>/dev/null; then
    fail_rule "http_contract_w1_tenant_and_cancel_consistency" "docs/contracts/http-api-contracts.md references CREATED as initial run status. Per ADR-0040 initial status is PENDING."
    _r16_fail=1
  fi
fi
# 16c: openapi-v1.yaml must not mention DELETE /v1/runs/{runId} as cancel
if [[ $_r16_fail -eq 0 ]] && [[ -f 'docs/contracts/openapi-v1.yaml' ]]; then
  if grep -qE 'DELETE[[:space:]]*/v1/runs/\{runId\}|DELETE.*runId.*cancel' 'docs/contracts/openapi-v1.yaml' 2>/dev/null; then
    fail_rule "http_contract_w1_tenant_and_cancel_consistency" "docs/contracts/openapi-v1.yaml references DELETE /v1/runs/{runId} as cancel. Per ADR-0040 cancel is POST /v1/runs/{id}/cancel."
    _r16_fail=1
  fi
fi
# 16d: W0 shipped cancel run-owner mismatch collapses to 404 not_found.
if [[ $_r16_fail -eq 0 ]] && [[ -f 'docs/contracts/http-api-contracts.md' ]]; then
  if grep -qE 'cancel.*Returns 403 `tenant_mismatch` if the request tenant differs from `Run\.tenantId`|request tenant differs from `Run\.tenantId`.*403 `tenant_mismatch`' 'docs/contracts/http-api-contracts.md' 2>/dev/null; then
    fail_rule "http_contract_w1_tenant_and_cancel_consistency" "docs/contracts/http-api-contracts.md says cancel run-owner tenant mismatch returns 403. Per ADR-0108/0116 W0 shipped behavior is 404 not_found; 403 is only JWT/header mismatch today and W1-widening future state."
    _r16_fail=1
  fi
fi
# 16e: W1 idempotency claim-only behavior is 409, response replay is W2.
if [[ $_r16_fail -eq 0 ]] && grep -q 'Response replay deferred to W2' docs/governance/architecture-status.yaml 2>/dev/null; then
  for _r16_idem_file in docs/contracts/http-api-contracts.md docs/contracts/openapi-v1.yaml agent-service/src/test/resources/contracts/openapi-v1-pinned.yaml; do
    [[ -f "$_r16_idem_file" ]] || continue
    if grep -qiE 'same key[^.]*return(s|ed)? the original response|same key[^.]*return(s|ed)? the first response|replays with the same key \+ body return the original response|Reused keys with same body return the original response' "$_r16_idem_file" 2>/dev/null; then
      fail_rule "http_contract_w1_tenant_and_cancel_consistency" "$_r16_idem_file promises response replay for same-body idempotency, but architecture-status.yaml says response replay is deferred to W2. W1 contract is 409 idempotency_conflict for same hash and 409 idempotency_body_drift for different hash."
      _r16_fail=1
      break
    fi
  done
fi
if [[ $_r16_fail -eq 0 ]]; then pass_rule "http_contract_w1_tenant_and_cancel_consistency"; fi

# ---------------------------------------------------------------------------
# Rule 17 — contract_catalog_spi_table_matches_source
# ADR-0041: contract-catalog.md must list the 7 known active SPI interfaces.
# OssApiProbe must NOT appear before the **Probes sub-table heading.
# ---------------------------------------------------------------------------
_r17_fail=0
_catalog17='docs/contracts/contract-catalog.md'
_known_spis=('RunRepository' 'Checkpointer' 'GraphMemoryRepository' 'ResilienceContract' 'Orchestrator' 'GraphExecutor' 'AgentLoopExecutor')
if [[ -f "$_catalog17" ]]; then
  for _spi in "${_known_spis[@]}"; do
    if ! grep -qF "$_spi" "$_catalog17" 2>/dev/null; then
      fail_rule "contract_catalog_spi_table_matches_source" "$_catalog17 does not list SPI '$_spi'. Per ADR-0041 Gate Rule 17 all 7 active SPI interfaces must appear."
      _r17_fail=1
    fi
  done
  # Perf fix (2026-05-23): combine both per-line passes (probes-sub-table
  # OssApiProbe + data-carriers RunContext interface) into a single mapfile +
  # bash-regex walk. Original ran 2 × `while read` loops each with 2 forks
  # per line × ~600 lines = ~2400 forks. Replace with one mapfile + 4 regex
  # checks per line (no forks).
  if [[ $_r17_fail -eq 0 ]]; then
    mapfile -t _r17_arr < "$_catalog17"
    _past_probes=0
    _in_data_carriers=0
    _run_ctx_has_interface=0
    _run_ctx_found=0
    for _ln17 in "${_r17_arr[@]}"; do
      if [[ "$_ln17" =~ \*\*Probes|^#+[[:space:]]+Probes ]]; then _past_probes=1; fi
      if [[ $_past_probes -eq 0 ]] && [[ "$_ln17" == *"OssApiProbe"* ]]; then
        fail_rule "contract_catalog_spi_table_matches_source" "$_catalog17 contains OssApiProbe before the Probes sub-table. OssApiProbe is a probe, not an SPI. Per ADR-0041 Gate Rule 17."
        _r17_fail=1
        break
      fi
      if [[ "$_ln17" =~ \*\*Data\ carriers ]]; then _in_data_carriers=1; fi
      if [[ $_in_data_carriers -eq 1 && $_run_ctx_found -eq 0 ]] && [[ "$_ln17" == *"RunContext"* ]]; then
        _run_ctx_found=1
        [[ "$_ln17" == *"interface"* ]] && _run_ctx_has_interface=1
      fi
    done
    if [[ $_r17_fail -eq 0 && $_run_ctx_found -eq 1 && $_run_ctx_has_interface -eq 0 ]]; then
      fail_rule "contract_catalog_spi_table_matches_source" "$_catalog17 RunContext row in data-carriers sub-table does not contain 'interface'. Per ADR-0044 Gate Rule 17 extension RunContext must be classified as interface."
      _r17_fail=1
    fi
  fi
else
  fail_rule "contract_catalog_spi_table_matches_source" "$_catalog17 not found."
  _r17_fail=1
fi
if [[ $_r17_fail -eq 0 ]]; then pass_rule "contract_catalog_spi_table_matches_source"; fi

# ---------------------------------------------------------------------------
# Rule 19 — shipped_row_tests_evidence (strengthened per ADR-0042 + ADR-0045)
# Every shipped: true row must have:
#   (a) tests: key present (not absent),
#   (b) tests: non-empty (not [] and not block-empty),
#   (c) every listed test path exists on disk.
# Uses [[:space:]] instead of \s for POSIX portability.
# ---------------------------------------------------------------------------
_r19_fail=0
if [[ -n "${_SCAN_SHIPPED_ROWS:-}" ]]; then
  # Fast path (PR-E3.b): single awk pass over the pre-extracted TSV.
  # For every shipped:true capability, check tests_marker == "present"
  # AND tests_count > 0 AND every listed test path exists on disk.
  # Emit: <capability>\t<status>\t<detail> where status ∈ {missing_key,
  # empty, path_missing:<path>}.
  while IFS=$'\t' read -r _r19_cap _r19_status _r19_detail; do
    [[ -z "$_r19_cap" ]] && continue
    case "$_r19_status" in
      missing_key)
        fail_rule "shipped_row_tests_evidence" "$_status_path capability '$_r19_cap' shipped:true but tests: key absent. Per ADR-0042 Gate Rule 19 all shipped rows must have non-empty test evidence."
        _r19_fail=1
        ;;
      empty)
        fail_rule "shipped_row_tests_evidence" "$_status_path capability '$_r19_cap' shipped:true but tests: is empty. Per ADR-0042 Gate Rule 19 all shipped rows must have non-empty test evidence."
        _r19_fail=1
        ;;
      path_missing)
        fail_rule "shipped_row_tests_evidence" "$_status_path capability '$_r19_cap' lists test path '$_r19_detail' not found on disk. Per ADR-0042 Gate Rule 19 all test paths must resolve."
        _r19_fail=1
        ;;
    esac
  done < <(printf '%s\n' "$_SCAN_SHIPPED_ROWS" | awk -F'\t' '
    $2=="shipped" && $3=="true" { shipped[$1]=1 }
    $2=="tests_marker" { marker[$1]=$3 }
    $2=="tests_count" { tcount[$1]=$3 }
    $2=="test" {
      if (!(($1) in tests)) tests[$1] = ""
      tests[$1] = tests[$1] "\n" $3
    }
    END {
      for (cap in shipped) {
        if (marker[cap] != "present") {
          printf "%s\tmissing_key\t\n", cap
          continue
        }
        if ((tcount[cap]+0) == 0) {
          printf "%s\tempty\t\n", cap
          continue
        }
        # Emit each test path so bash can stat-check it.
        n = split(tests[cap], paths, "\n")
        for (i = 1; i <= n; i++) {
          if (paths[i] != "") print cap "\tcandidate\t" paths[i]
        }
      }
    }
  ' | while IFS=$'\t' read -r _cap _status _path; do
    if [[ "$_status" == "candidate" ]]; then
      if [[ ! -e "$_path" ]]; then
        printf '%s\tpath_missing\t%s\n' "$_cap" "$_path"
      fi
    else
      printf '%s\t%s\t%s\n' "$_cap" "$_status" "$_path"
    fi
  done)
elif [[ -f "$_status_path" ]]; then
  # Fallback (cache disabled): original per-line scan.
  _current_key19=''
  _in_shipped19=0
  _in_tests_list19=0
  _tests_found19=0
  _tests_has_items19=0
  _current_test_paths19=()

  _flush_shipped19() {
    if [[ $_in_shipped19 -eq 1 ]]; then
      if [[ $_tests_found19 -eq 0 ]]; then
        fail_rule "shipped_row_tests_evidence" "$_status_path capability '$_current_key19' shipped:true but tests: key absent. Per ADR-0042 Gate Rule 19 all shipped rows must have non-empty test evidence."
        _r19_fail=1
      elif [[ $_tests_has_items19 -eq 0 ]]; then
        fail_rule "shipped_row_tests_evidence" "$_status_path capability '$_current_key19' shipped:true but tests: is empty. Per ADR-0042 Gate Rule 19 all shipped rows must have non-empty test evidence."
        _r19_fail=1
      else
        for _tp19 in "${_current_test_paths19[@]}"; do
          if [[ ! -e "$_tp19" ]]; then
            fail_rule "shipped_row_tests_evidence" "$_status_path capability '$_current_key19' lists test path '$_tp19' not found on disk. Per ADR-0042 Gate Rule 19 all test paths must resolve."
            _r19_fail=1
          fi
        done
      fi
    fi
  }

  while IFS= read -r _line19 || [[ -n "$_line19" ]]; do
    if printf '%s\n' "$_line19" | grep -qE '^  [a-zA-Z][a-zA-Z_]+:'; then
      _flush_shipped19
      _current_key19=$(printf '%s\n' "$_line19" | sed 's/^  \([a-zA-Z][a-zA-Z_]*\):.*/\1/')
      _in_shipped19=0; _in_tests_list19=0
      _tests_found19=0; _tests_has_items19=0; _current_test_paths19=()
      continue
    fi
    if printf '%s\n' "$_line19" | grep -qE '^[[:space:]]+shipped:[[:space:]]+true'; then _in_shipped19=1; fi
    if [[ $_in_shipped19 -eq 1 ]]; then
      if printf '%s\n' "$_line19" | grep -qE '^[[:space:]]+tests:[[:space:]]*\[\]'; then
        _tests_found19=1; _in_tests_list19=0
      elif printf '%s\n' "$_line19" | grep -qE '^[[:space:]]+tests:[[:space:]]*$'; then
        _tests_found19=1; _in_tests_list19=1
      elif printf '%s\n' "$_line19" | grep -qE '^[[:space:]]+tests:'; then
        _tests_found19=1; _in_tests_list19=0
      elif [[ $_in_tests_list19 -eq 1 ]] && printf '%s\n' "$_line19" | grep -qE '^[[:space:]]+-[[:space:]]+'; then
        _tests_has_items19=1
        _tp19_val=$(printf '%s\n' "$_line19" | sed -E 's/^[[:space:]]+-[[:space:]]+(.*)/\1/')
        _current_test_paths19+=("$_tp19_val")
      elif [[ $_in_tests_list19 -eq 1 ]] && ! printf '%s\n' "$_line19" | grep -qE '^[[:space:]]+-'; then
        _in_tests_list19=0
      fi
    fi
  done < "$_status_path"
  _flush_shipped19
fi
if [[ $_r19_fail -eq 0 ]]; then pass_rule "shipped_row_tests_evidence"; fi

# ---------------------------------------------------------------------------
# Rule 24 — shipped_row_evidence_paths_exist
# ADR-0045: every l2_documents: entry and latest_delivery_file: value on a
# shipped: true row must resolve to an existing file. Closes REF-DRIFT.
# ---------------------------------------------------------------------------
_r24_fail=0
if [[ -n "${_SCAN_SHIPPED_ROWS:-}" ]]; then
  # Fast path (PR-E3.b): one awk pass over the pre-extracted TSV.
  # Emit (capability, field, path) for every l2_doc + latest_delivery
  # entry whose capability is shipped:true. Bash then does stat() on each.
  while IFS=$'\t' read -r _r24_cap _r24_field _r24_path; do
    [[ -z "$_r24_path" ]] && continue
    if [[ ! -e "$_r24_path" ]]; then
      case "$_r24_field" in
        latest_delivery)
          fail_rule "shipped_row_evidence_paths_exist" "$_status_path capability '$_r24_cap' latest_delivery_file '$_r24_path' not found on disk. Per ADR-0045 Gate Rule 24 all shipped-row evidence paths must resolve."
          ;;
        l2_doc)
          fail_rule "shipped_row_evidence_paths_exist" "$_status_path capability '$_r24_cap' l2_documents entry '$_r24_path' not found on disk. Per ADR-0045 Gate Rule 24."
          ;;
      esac
      _r24_fail=1
    fi
  done < <(printf '%s\n' "$_SCAN_SHIPPED_ROWS" | awk -F'\t' '
    $2=="shipped" && $3=="true" { shipped[$1]=1 }
    ($2=="l2_doc" || $2=="latest_delivery") { rows[NR]=$1 "\t" $2 "\t" $3 }
    END {
      for (k in rows) {
        split(rows[k], a, "\t")
        if (a[1] in shipped) print a[1] "\t" a[2] "\t" a[3]
      }
    }
  ')
elif [[ -f "$_status_path" ]]; then
  # Fallback (cache disabled): original per-line scan.
  _current_key24=''
  _in_shipped24=0
  _in_l2_list24=0
  while IFS= read -r _line24 || [[ -n "$_line24" ]]; do
    if printf '%s\n' "$_line24" | grep -qE '^  [a-zA-Z][a-zA-Z_]+:'; then
      _current_key24=$(printf '%s\n' "$_line24" | sed 's/^  \([a-zA-Z][a-zA-Z_]*\):.*/\1/')
      _in_shipped24=0; _in_l2_list24=0
      continue
    fi
    if printf '%s\n' "$_line24" | grep -qE '^[[:space:]]+shipped:[[:space:]]+true'; then _in_shipped24=1; fi
    if [[ $_in_shipped24 -eq 1 ]]; then
      # latest_delivery_file
      if printf '%s\n' "$_line24" | grep -qE '^[[:space:]]+latest_delivery_file:[[:space:]]+'; then
        _ldf24=$(printf '%s\n' "$_line24" | sed -E 's/^[[:space:]]+latest_delivery_file:[[:space:]]+(.*)/\1/')
        if [[ -n "$_ldf24" && ! -e "$_ldf24" ]]; then
          fail_rule "shipped_row_evidence_paths_exist" "$_status_path capability '$_current_key24' latest_delivery_file '$_ldf24' not found on disk. Per ADR-0045 Gate Rule 24 all shipped-row evidence paths must resolve."
          _r24_fail=1
        fi
      fi
      # l2_documents list
      if printf '%s\n' "$_line24" | grep -qE '^[[:space:]]+l2_documents:[[:space:]]*\[\]'; then
        _in_l2_list24=0
      elif printf '%s\n' "$_line24" | grep -qE '^[[:space:]]+l2_documents:[[:space:]]*$'; then
        _in_l2_list24=1
      elif printf '%s\n' "$_line24" | grep -qE '^[[:space:]]+l2_documents:'; then
        _in_l2_list24=0
      elif [[ $_in_l2_list24 -eq 1 ]] && printf '%s\n' "$_line24" | grep -qE '^[[:space:]]+-[[:space:]]+'; then
        _l2p24=$(printf '%s\n' "$_line24" | sed -E 's/^[[:space:]]+-[[:space:]]+(.*)/\1/')
        if [[ -n "$_l2p24" && ! -e "$_l2p24" ]]; then
          fail_rule "shipped_row_evidence_paths_exist" "$_status_path capability '$_current_key24' l2_documents entry '$_l2p24' not found on disk. Per ADR-0045 Gate Rule 24."
          _r24_fail=1
        fi
      elif [[ $_in_l2_list24 -eq 1 ]] && ! printf '%s\n' "$_line24" | grep -qE '^[[:space:]]+-'; then
        _in_l2_list24=0
      fi
    fi
  done < "$_status_path"
fi
if [[ $_r24_fail -eq 0 ]]; then pass_rule "shipped_row_evidence_paths_exist"; fi

# ---------------------------------------------------------------------------
# Rule 28a — tenant_column_present (Rule 28 sub-check, ADR-0059, enforcer E15)
# Every CREATE TABLE under any */src/main/resources/db/migration/*.sql that
# isn't a control/system table must declare a tenant_id column.
# Exemptions: health_check (singleton system row).
# ---------------------------------------------------------------------------
_r28a_fail=0
_python_bin=$(command -v python3 || command -v python || echo "")
while IFS= read -r _mig; do
  [[ -z "$_mig" ]] && continue
  if [[ -z "$_python_bin" ]]; then
    # No Python available — fall back to a crude shell heuristic: every
    # CREATE TABLE block must contain 'tenant_id' somewhere before its
    # terminating ';'. We use awk for the statement-level split.
    if awk '
      BEGIN { RS=";"; FS=""; IGNORECASE=1 }
      /CREATE[[:space:]]+TABLE/ {
        if ($0 ~ /health_check/) next
        if ($0 !~ /tenant_id/) { print "FAIL: " FILENAME; exit 1 }
      }
    ' "$_mig"; then :; else _r28a_fail=1; fi
    continue
  fi
  "$_python_bin" - "$_mig" <<'PY' || _r28a_fail=1
import re, sys
path = sys.argv[1]
text = open(path, encoding='utf-8').read()
# tokenize by semicolons; for each CREATE TABLE, inspect the body
for stmt in text.split(';'):
    m = re.search(r'CREATE\s+TABLE\s+(?:IF\s+NOT\s+EXISTS\s+)?([a-zA-Z_][a-zA-Z0-9_]*)', stmt, re.IGNORECASE)
    if not m: continue
    name = m.group(1)
    if name in ('health_check',):
        continue
    if not re.search(r'\btenant_id\b', stmt, re.IGNORECASE):
        print(f"FAIL: {path}: table '{name}' lacks tenant_id column")
        sys.exit(1)
sys.exit(0)
PY
  if [[ $? -ne 0 ]]; then
    fail_rule "tenant_column_present" "$_mig declares a tenant-scoped table without a tenant_id column. Per Rule 28a / enforcer E15."
    _r28a_fail=1
  fi
done < <(printf '%s\n' "${_SCAN_MIGRATION_SQL:-$(find . -path '*/src/main/resources/db/migration/*.sql' -not -path './target/*' 2>/dev/null | sort || true)}" | grep -E '\.sql$' || true)
if [[ $_r28a_fail -eq 0 ]]; then pass_rule "tenant_column_present"; fi

# ---------------------------------------------------------------------------
# Rule 28b — high_cardinality_tag_guard (enforcer E19)
# No source in agent-*/src/main/java registers Tag.of("run_id"|"idempotency_key"|
# "jwt_sub"|"body", ...) on a metric. The TenantTagMeterFilter scrubs these
# at runtime; the gate rejects them at commit time.
# ---------------------------------------------------------------------------
_r28b_fail=0
_forbidden_tag_pattern='Tag\.of\(\s*"(run_id|idempotency_key|jwt_sub|body)"'
_28b_hits=$(grep -rnE "$_forbidden_tag_pattern" \
  agent-*/src/main/java 2>/dev/null || true)
if [[ -n "$_28b_hits" ]]; then
  fail_rule "high_cardinality_tag_guard" "Forbidden high-cardinality metric tag found:\n$_28b_hits\nPer Rule 28b / enforcer E19."
  _r28b_fail=1
fi
if [[ $_r28b_fail -eq 0 ]]; then pass_rule "high_cardinality_tag_guard"; fi

# ---------------------------------------------------------------------------
# Rule 28c — no_secret_patterns (enforcer E20)
# Crude regex sweep for common secret-leak shapes in tracked files.
# Excludes node_modules / target / .git / binary extensions. Files annotated
# with `secret-allowlist:` are exempt.
# Implemented as a single `git grep` for speed on Windows where per-file
# grep loops are pathologically slow.
# ---------------------------------------------------------------------------
_r28c_fail=0
# AWS access keys + private key blocks + GitHub PATs. The 'sk-' pattern was
# dropped — it false-matched documentation that names the regex shape itself.
_secret_patterns='AKIA[0-9A-Z]{16}|-----BEGIN [A-Z ]*PRIVATE KEY-----|ghp_[A-Za-z0-9]{36}'
# docs/governance/enforcers.yaml is the index — it DOCUMENTS the patterns and
# is intentionally excluded; the index does not contain real secrets.
_28c_hits=$(git grep -lE "$_secret_patterns" -- ':!target/' ':!*.jar' ':!*.png' ':!*.jpg' ':!*.pdf' ':!docs/governance/enforcers.yaml' ':!architecture/generated/enforcers.dsl' ':!gate/check_architecture_sync.sh' ':!gate/check_architecture_sync.ps1' 2>/dev/null || true)
if [[ -n "$_28c_hits" ]]; then
  while IFS= read -r _hit; do
    [[ -z "$_hit" ]] && continue
    if ! grep -q 'secret-allowlist:' "$_hit" 2>/dev/null; then
      fail_rule "no_secret_patterns" "$_hit appears to contain a secret pattern. Per Rule 28c / enforcer E20; add 'secret-allowlist: <reason>' inline if it is an intentional test fixture."
      _r28c_fail=1
    fi
  done <<< "$_28c_hits"
fi
if [[ $_r28c_fail -eq 0 ]]; then pass_rule "no_secret_patterns"; fi

# ---------------------------------------------------------------------------
# Rule 28d — out_of_scope_name_guard (enforcer E26)
# Names of W2+ deferred concepts (LLMGateway, PostgresCheckpointer,
# HookChain, SpawnEnvelope, LogicalCallHandle, ConnectionLease,
# AdmissionDecision, BackpressureSignal, ChronosHydration, SandboxExecutor)
# MUST NOT appear in agent-*/src/main/java. Test sources, ADRs, plans,
# release notes, and architecture-status.yaml are intentionally exempt.
# rc43 (ADR-0127): SkillRegistry removed from the blacklist; it is
# promoted from W2 deferred to L0 contract (see ADR-0127 Skill SPI).
# ---------------------------------------------------------------------------
_r28d_fail=0
_oos_names='LLMGateway|PostgresCheckpointer|HookChain|SpawnEnvelope|LogicalCallHandle|ConnectionLease|AdmissionDecision|BackpressureSignal|ChronosHydration|SandboxExecutor'
_28d_hits=$(grep -rnE "\\b($_oos_names)\\b" \
  agent-*/src/main/java 2>/dev/null || true)
if [[ -n "$_28d_hits" ]]; then
  fail_rule "out_of_scope_name_guard" "W2+ out-of-scope name detected in main sources:\n$_28d_hits\nPer Rule 28d / enforcer E26 / plan §13."
  _r28d_fail=1
fi
if [[ $_r28d_fail -eq 0 ]]; then pass_rule "out_of_scope_name_guard"; fi

# ---------------------------------------------------------------------------
# Rule 28f — enforcers_yaml_wellformed (enforcer E29)
# docs/governance/enforcers.yaml MUST: exist, parse as YAML, contain a list
# where every row has all five fields (id, constraint_ref, kind, artifact,
# asserts) and kind is one of the five legal values.
# ---------------------------------------------------------------------------
_r28f_fail=0
_efile='docs/governance/enforcers.yaml'
if [[ ! -f "$_efile" ]]; then
  fail_rule "enforcers_yaml_wellformed" "$_efile missing. Per Rule 28f / enforcer E29 — Rule 28 cannot function without its index."
  _r28f_fail=1
elif [[ -z "$_python_bin" ]]; then
  # No Python — fall back to a coarse shell check: every '- id:' row must
  # be followed within 5 lines by 'constraint_ref:', 'kind:', 'artifact:',
  # 'asserts:'. Best-effort; the full schema validation requires Python.
  if ! grep -q '^- id:' "$_efile"; then
    fail_rule "enforcers_yaml_wellformed" "$_efile contains no '- id:' rows. Per Rule 28f / enforcer E29."
    _r28f_fail=1
  fi
else
  "$_python_bin" - "$_efile" <<'PY' || _r28f_fail=1
import sys, re
path = sys.argv[1]
with open(path, encoding='utf-8') as f:
    text = f.read()
# Required sub-fields under each '- id:' row (id is the boundary itself).
sub_required = ('constraint_ref', 'kind', 'artifact', 'asserts')
kinds = ('archunit', 'gate-script', 'integration', 'schema', 'compile-time')
# Split on the row boundary; drop the pre-list preamble (rows[0]).
rows = re.split(r'^- id:\s*', text, flags=re.MULTILINE)
errors = []
for raw in rows[1:]:
    block = raw  # first line is the ID, subsequent indented lines are the row
    first_line = block.splitlines()[0].strip()
    if not re.fullmatch(r'E\d+', first_line):
        errors.append(f"row id is not E<n>: '{first_line}'")
    for field in sub_required:
        if not re.search(rf'(^|\n)\s*{field}:', block):
            errors.append(f"row '{first_line}' missing field '{field}'")
    km = re.search(r'(^|\n)\s*kind:\s*([a-zA-Z\-]+)', block)
    if km and km.group(2) not in kinds:
        errors.append(f"row '{first_line}' has illegal kind '{km.group(2)}': expected one of {kinds}")
if errors:
    for e in errors:
        print(f"FAIL: {e}")
    sys.exit(1)
sys.exit(0)
PY
  if [[ $? -ne 0 ]]; then
    fail_rule "enforcers_yaml_wellformed" "$_efile rows are not well-formed. Per Rule 28f / enforcer E29."
    _r28f_fail=1
  fi
fi
if [[ $_r28f_fail -eq 0 ]]; then pass_rule "enforcers_yaml_wellformed"; fi

# ---------------------------------------------------------------------------
# Rule 28j — enforcer_artifact_paths_exist (Phase K F6 + Phase L P0-2, E33+E35)
# Every `artifact:` path in docs/governance/enforcers.yaml MUST resolve to a
# real file on disk. `#anchor` suffixes (e.g. `RunHttpContractIT.java#cancel...`
# or `check_architecture_sync.sh#rule_10`) MUST also resolve to a real method
# (.java/.sh) or heading (.md) inside that file. Phase L strengthens the
# file-only check (which let E5/E6/E24 ship with anchors pointing at methods
# that did not exist — closes reviewer finding P0-2).
# ---------------------------------------------------------------------------
_r28j_fail=0
# Perf fix (2026-05-23): the original loop forked grep 1-5x per artifact
# row (~200 rows × ~3 avg forks = ~600 forks). On WSL/mnt/d that was ~19s
# per gate run. Replaced with a single python pass that parses enforcers.yaml
# once and caches file content per target — multiple artifact rows pointing
# at the same file (common) now share one read. Same anchor-detection rules.
if [[ -f "$_efile" ]]; then
  _r28j_violations="$(
    GATE_R28J_EFILE="$_efile" "${GATE_PYTHON_BIN:-python3}" - <<'PYEOF'
import os, re, sys
from pathlib import Path

efile = os.environ['GATE_R28J_EFILE']
artifact_re = re.compile(r'^\s*artifact:\s*(.+?)\s*$')
artifacts: list[tuple[str, str]] = []  # (path, anchor)
for line in Path(efile).read_text(encoding='utf-8', errors='replace').splitlines():
    m = artifact_re.match(line)
    if not m: continue
    val = m.group(1)
    if '#' in val:
        path, anchor = val.split('#', 1)
    else:
        path, anchor = val, ''
    path = path.strip()
    if not path: continue
    artifacts.append((path, anchor))

file_cache: dict[str, str] = {}
def read(p: str) -> str:
    if p not in file_cache:
        try: file_cache[p] = Path(p).read_text(encoding='utf-8', errors='replace')
        except OSError: file_cache[p] = ''
    return file_cache[p]

viol: list[str] = []
for path, anchor in artifacts:
    if not os.path.exists(path):
        viol.append(f"PATH\t{path}\t")
        continue
    if not anchor: continue
    text = read(path)
    ok = True
    if path.endswith('.java'):
        # Method declaration: `(void|...)<ws>anchor<ws>*(`
        m1 = re.search(rf'(void|\)|>|>\s)\s+{re.escape(anchor)}\s*\(', text)
        m2 = re.search(rf'(?m)^\s*[a-zA-Z_<>][^()]*\s{re.escape(anchor)}\s*\(', text)
        ok = bool(m1 or m2)
    elif path.endswith(('.sh', '.bash')):
        # Bash function definition or `# Rule N — anchor` or `(pass_rule|fail_rule) "anchor"`.
        m1 = re.search(rf'(?:^|\s){re.escape(anchor)}\s*\(\)', text)
        m2 = re.search(rf'(?m)^\s*function\s+{re.escape(anchor)}\b', text)
        m3 = re.search(rf'(?m)^#\s*Rule\s+[0-9a-z]+\s+(?:—|--)\s+{re.escape(anchor)}\b', text)
        m4 = re.search(rf'\b(?:pass_rule|fail_rule)\s+"{re.escape(anchor)}"', text)
        ok = bool(m1 or m2 or m3 or m4)
    elif path.endswith('.md'):
        ok = bool(re.search(rf'(?m)^#+\s.*{re.escape(anchor)}', text))
    elif path.endswith(('.yaml', '.yml')):
        ok = anchor in text
    else:
        ok = anchor in text
    if not ok:
        viol.append(f"ANCHOR\t{path}\t{anchor}")
for v in viol: print(v)
PYEOF
  )"
  if [[ -n "$_r28j_violations" ]]; then
    while IFS=$'\t' read -r _r28j_kind _r28j_path _r28j_anchor; do
      [[ -z "$_r28j_kind" ]] && continue
      case "$_r28j_kind" in
        PATH)
          fail_rule "enforcer_artifact_paths_exist" "enforcers.yaml declares artifact path '$_r28j_path' which does not exist on disk. Per Rule 28j / enforcer E33."
          ;;
        ANCHOR)
          fail_rule "enforcer_artifact_paths_exist" "enforcers.yaml declares artifact anchor '$_r28j_path#$_r28j_anchor' but no method/heading/rule with that name exists in the target file. Per Rule 28j / enforcer E33 (anchor validation added in Phase L, enforcer E35)."
          ;;
      esac
      _r28j_fail=1
    done <<< "$_r28j_violations"
  fi
fi
if [[ $_r28j_fail -eq 0 ]]; then pass_rule "enforcer_artifact_paths_exist"; fi

# ---------------------------------------------------------------------------
# Rule 28k — javadoc_enforcer_citation_semantic_check (post-review fix
# plan F / P1-2, enforcer E33+ semantic widening).
#
# Phase 7 post-release review surfaced two test-class Javadocs citing the
# WRONG enforcer ID (S2cCallbackRoundTripIT cited #E83 but is actually E82;
# EngineRegistryBootValidationIT cited #E81 but is actually E84). Rule 28j
# checks `artifact: path#anchor` resolves; it does NOT cross-check that a
# test file citing `enforcers.yaml#E<n>` in its Javadoc actually corresponds
# to E<n>'s declared `artifact:` field.
#
# This rule scans *Test.java and *IT.java under agent-service/src/test/java
# and agent-service/src/test/java for Javadoc citations of the form
# `enforcers.yaml#E<n>` and asserts each cited E-row's `artifact:` field's
# file path (anchor stripped, path normalised) matches the source file
# path. Mis-citation is a Rule 25 truth violation.
# ---------------------------------------------------------------------------
_r28k_fail=0
# PR-Opt-rc22: load pre-parsed enforcers TSV into an associative array.
# Replaces the per-citation `awk` pass over the full enforcers.yaml (which
# was ~9-20s per gate run). The TSV is built once by gate/lib/scan_cache.sh
# as _SCAN_ENFORCERS_TSV with fields: e_id \t artifact_path \t kind.
declare -A _r28k_art_by_eid
if [[ -n "${_SCAN_ENFORCERS_TSV:-}" ]]; then
  while IFS=$'\t' read -r _r28k_eid_k _r28k_art_v _r28k_kind_v; do
    [[ -n "$_r28k_eid_k" ]] && _r28k_art_by_eid["$_r28k_eid_k"]="$_r28k_art_v"
  done <<< "$_SCAN_ENFORCERS_TSV"
fi

if [[ -f "$_efile" ]]; then
  # Perf fix (2026-05-23): the original per-file loop forked grep twice +
  # sed once per test file (~hundreds × 3 forks = thousands). On WSL/mnt/d
  # that was ~51s per gate. Replaced with a single python pass that reads
  # each in-scope file once and consults the pre-parsed _SCAN_ENFORCERS_TSV
  # (or falls back to parsing enforcers.yaml directly when the cache is
  # disabled). Same semantics: at-least-one-match required.
  _r28k_violations=$(GATE_R28K_EFILE="$_efile" "${GATE_PYTHON_BIN:-python3}" - <<'PYEOF'
import os, re, sys
from pathlib import Path

efile = os.environ['GATE_R28K_EFILE']
tsv = os.environ.get('_SCAN_ENFORCERS_TSV', '')

# Build {eid -> artifact_path} map. Prefer the pre-parsed TSV; fall back
# to a one-shot awk-equivalent over enforcers.yaml.
art_by_eid: dict[str, str] = {}
if tsv:
    for row in tsv.splitlines():
        parts = row.split('\t')
        if len(parts) >= 2 and parts[0]:
            art_by_eid[parts[0]] = parts[1]
else:
    cur_id = None
    for line in Path(efile).read_text(encoding='utf-8', errors='replace').splitlines():
        m = re.match(r'^- id: (E\d+)$', line)
        if m:
            cur_id = m.group(1)
            continue
        if cur_id:
            m = re.match(r'^\s+artifact:\s*(.+)$', line)
            if m:
                p = m.group(1).split('#', 1)[0].strip()
                art_by_eid[cur_id] = p
                cur_id = None  # done with this row's artifact
            elif line.startswith('- id:'):
                cur_id = None

# Walk both test trees (the original double-listed agent-service/src/test/java
# for typo-tolerance; we deduplicate via a set).
roots = {'agent-service/src/test/java'}
test_files: list[str] = []
for root in roots:
    if not os.path.isdir(root):
        continue
    for dirpath, _, files in os.walk(root):
        for fn in files:
            if fn.endswith('Test.java') or fn.endswith('IT.java'):
                test_files.append(os.path.join(dirpath, fn))

strict_re = re.compile(r'enforcers\.yaml#E\d+')
eid_re = re.compile(r'#E(\d+)')
viol = []
for src in sorted(test_files):
    try:
        txt = Path(src).read_text(encoding='utf-8', errors='replace')
    except OSError:
        continue
    if not strict_re.search(txt):
        continue
    eids = sorted({m.group(0)[1:] for m in eid_re.finditer(txt)})
    if not eids:
        continue
    src_norm = src.removeprefix('./')
    any_match = False
    missing_eids = []
    collected = []
    for eid in eids:
        art = art_by_eid.get(eid, '')
        if not art:
            missing_eids.append(eid)
            continue
        art_norm = art.removeprefix('./')
        collected.append(f'{eid}:{art_norm}')
        if src_norm == art_norm:
            any_match = True
    for me in missing_eids:
        viol.append(f"MISSING\t{src}\t{me}\t")
    if not any_match and not missing_eids:
        viol.append(f"NOMATCH\t{src}\t\t{' '.join(collected)}")

for line in viol:
    print(line)
PYEOF
)
  if [[ -n "$_r28k_violations" ]]; then
    while IFS=$'\t' read -r _r28k_kind _r28k_src _r28k_eid _r28k_collected; do
      [[ -z "$_r28k_kind" ]] && continue
      case "$_r28k_kind" in
        MISSING)
          fail_rule "javadoc_enforcer_citation_semantic_check" "$_r28k_src cites enforcers.yaml#$_r28k_eid but no such row in $_efile (Rule 28k / post-review plan F)"
          ;;
        NOMATCH)
          fail_rule "javadoc_enforcer_citation_semantic_check" "$_r28k_src cites enforcers.yaml#E<n> rows but NONE of their artifact: paths match this file. Cited: $_r28k_collected. Per Rule 28k / post-review plan F."
          ;;
      esac
      _r28k_fail=1
    done <<< "$_r28k_violations"
  fi
fi
if [[ $_r28k_fail -eq 0 ]]; then pass_rule "javadoc_enforcer_citation_semantic_check"; fi

# ---------------------------------------------------------------------------
# Rule 36 — domain_module_has_spi_package (enforcer E54, ADR-0067)
#
# Every module with kind=domain in its module-metadata.yaml MUST declare at
# least one entry under `spi_packages:` AND each declared package MUST exist
# as a directory under <module>/src/main/java/. Required by CLAUDE.md Rule 32.
# ---------------------------------------------------------------------------
_r36_fail=0
while IFS= read -r _meta; do
  [[ -z "$_meta" ]] && continue
  _kind="$(grep -E '^[[:space:]]*kind:' "$_meta" 2>/dev/null | head -1 | sed -E 's/^[[:space:]]*kind:[[:space:]]*([A-Za-z_]+).*/\1/')"
  [[ "$_kind" != "domain" ]] && continue
  _mod_dir="$(dirname "$_meta")"
  # Extract spi_packages list entries (lines under spi_packages: that look like "  - <pkg>")
  _has_entry=0
  _pkg_lines="$(awk '/^[[:space:]]*spi_packages:/{flag=1; next} /^[A-Za-z_]/{flag=0} flag && /^[[:space:]]*-[[:space:]]*[A-Za-z0-9._-]+/{print}' "$_meta" 2>/dev/null || true)"
  if [[ -z "$_pkg_lines" ]]; then
    fail_rule "domain_module_has_spi_package" "$_meta declares kind=domain but has no spi_packages entries (CLAUDE.md Rule 32 / ADR-0067)"
    _r36_fail=1
    continue
  fi
  while IFS= read -r _ln; do
    _pkg="$(printf '%s\n' "$_ln" | sed -E 's/^[[:space:]]*-[[:space:]]*([A-Za-z0-9._-]+).*/\1/')"
    [[ -z "$_pkg" ]] && continue
    _has_entry=1
    _pkg_path="$(printf '%s\n' "$_pkg" | tr '.' '/')"
    _dir="${_mod_dir}/src/main/java/${_pkg_path}"
    if [[ ! -d "$_dir" ]]; then
      fail_rule "domain_module_has_spi_package" "$_meta declares spi_package '${_pkg}' but directory ${_dir} does not exist"
      _r36_fail=1
    fi
  done <<< "$_pkg_lines"
  if [[ $_has_entry -eq 0 ]]; then
    fail_rule "domain_module_has_spi_package" "$_meta declares kind=domain but spi_packages list is empty"
    _r36_fail=1
  fi
done < <(find . -mindepth 2 -maxdepth 2 -name 'module-metadata.yaml' -type f 2>/dev/null | sort || true)
if [[ $_r36_fail -eq 0 ]]; then pass_rule "domain_module_has_spi_package"; fi

# ===========================================================================
# W1 Layered-4+1 + Architecture-Graph wave (CLAUDE.md Rules 33-34, ADR-0068)
# Gate Rules 37-40 enforce the front-matter discipline and the machine-readable
# graph index. See enforcers.yaml rows E55-E59.
# ===========================================================================

# ---------------------------------------------------------------------------
# Rule 38 — architecture_graph_well_formed (enforcer E56, ADR-0068)
#
# docs/governance/architecture-graph.yaml MUST regenerate idempotently from
# authoritative inputs. The build script runs --check and exits non-zero on
# any validation error (missing endpoint, missing file, cycle in
# supersedes/extends, anchor not resolvable).
# ---------------------------------------------------------------------------
_r38_fail=0
if ! command -v python3 >/dev/null 2>&1 && ! command -v python >/dev/null 2>&1; then
  fail_rule "architecture_graph_well_formed" "neither python3 nor python on PATH — required for gate/build_architecture_graph.py (CLAUDE.md Rule 34)"; _r38_fail=1
else
  _r38_tmp1="$(mktemp 2>/dev/null || echo /tmp/r38_a.$$.yaml)"
  _r38_tmp2="$(mktemp 2>/dev/null || echo /tmp/r38_b.$$.yaml)"
  # Build twice, diff outputs (idempotency).
  if ! bash gate/build_architecture_graph.sh > /dev/null 2> "$_r38_tmp1"; then
    fail_rule "architecture_graph_well_formed" "gate/build_architecture_graph.sh failed: $(cat "$_r38_tmp1")"; _r38_fail=1
  else
    cp docs/governance/architecture-graph.yaml "$_r38_tmp1" 2>/dev/null || true
    if ! bash gate/build_architecture_graph.sh --no-write --check > /dev/null 2> "$_r38_tmp2"; then
      fail_rule "architecture_graph_well_formed" "graph validation failed: $(cat "$_r38_tmp2")"; _r38_fail=1
    fi
  fi
  rm -f "$_r38_tmp1" "$_r38_tmp2" 2>/dev/null || true
fi
if [[ $_r38_fail -eq 0 ]]; then pass_rule "architecture_graph_well_formed"; fi

# ===========================================================================
# Phase M remediation (CLAUDE.md Rules 33-34, ADR-0068)
# Rules 41-44 close the self-violations the W1 wave inherited from Rule 28:
# anchor validation, idempotency, ADR-shape, frozen-doc edit path.
# Enforcer rows E60-E63 in docs/governance/enforcers.yaml.
# ===========================================================================

# ---------------------------------------------------------------------------
# Rule 41 — enforcer_anchor_resolves (enforcer E60, Phase M B2)
#
# Every artefact node in architecture-graph.yaml that carries an `anchor:`
# MUST also carry `anchor_resolves: true`. Closes the L1-expert P0-2 / P2-1
# gap: previously an enforcer row could point at a non-existent test method
# and pass Rule 28j (file-path existence). The graph builder now resolves
# anchors per file type (.java method declaration, .md heading, .sh function,
# .yaml top-level key) and this gate fails on any false.
# ---------------------------------------------------------------------------
_r41_fail=0
if [[ ! -f docs/governance/architecture-graph.yaml ]]; then
  fail_rule "enforcer_anchor_resolves" "docs/governance/architecture-graph.yaml not present — run bash gate/build_architecture_graph.sh first"
  _r41_fail=1
else
  # Scan the graph for any artefact node with anchor: <non-null> and anchor_resolves: false.
  _r41_offenders="$(awk '
    /^- id:/      { cur=$3; type=""; anchor=""; resolves="" }
    /^  type:/    { type=$2 }
    /^  path:/    { path=substr($0, index($0, ":")+2) }
    /^  anchor:/  {
      val = substr($0, index($0, ":")+2)
      gsub(/[[:space:]]+$/, "", val)
      anchor = val
    }
    /^  anchor_resolves:/ {
      val = substr($0, index($0, ":")+2)
      gsub(/[[:space:]]+$/, "", val)
      resolves = val
      if (type == "artefact" && anchor != "" && anchor != "null" && resolves == "false") {
        print "  - " cur " (path " path ", anchor " anchor ")"
      }
    }
  ' docs/governance/architecture-graph.yaml 2>/dev/null || true)"
  if [[ -n "$_r41_offenders" ]]; then
    fail_rule "enforcer_anchor_resolves" "unresolved anchor(s) — fix enforcer row or rename target method/heading:"
    echo "$_r41_offenders" >&2
    _r41_fail=1
  fi
fi
if [[ $_r41_fail -eq 0 ]]; then pass_rule "enforcer_anchor_resolves"; fi

# ---------------------------------------------------------------------------
# Rule 42 — architecture_graph_idempotent (enforcer E61, Phase M B3)
#
# Building the architecture graph twice on unchanged inputs MUST produce a
# byte-identical output. Closes the Rule 34 normative phrase "build script
# MUST be idempotent" which previously had no enforcer.
# ---------------------------------------------------------------------------
_r42_fail=0
if ! command -v python3 >/dev/null 2>&1 && ! command -v python >/dev/null 2>&1; then
  fail_rule "architecture_graph_idempotent" "neither python3 nor python on PATH — required for gate/build_architecture_graph.py"
  _r42_fail=1
elif [[ ! -f docs/governance/architecture-graph.yaml ]]; then
  fail_rule "architecture_graph_idempotent" "docs/governance/architecture-graph.yaml not present — run bash gate/build_architecture_graph.sh first"
  _r42_fail=1
else
  _r42_a="$(mktemp 2>/dev/null || echo /tmp/r42_a.$$.yaml)"
  _r42_b="$(mktemp 2>/dev/null || echo /tmp/r42_b.$$.yaml)"
  cp docs/governance/architecture-graph.yaml "$_r42_a" 2>/dev/null || true
  if ! bash gate/build_architecture_graph.sh > /dev/null 2>&1; then
    fail_rule "architecture_graph_idempotent" "graph build failed during idempotency probe"
    _r42_fail=1
  else
    cp docs/governance/architecture-graph.yaml "$_r42_b" 2>/dev/null || true
    if ! diff -q "$_r42_a" "$_r42_b" >/dev/null 2>&1; then
      fail_rule "architecture_graph_idempotent" "re-running gate/build_architecture_graph.sh produced a DIFFERENT graph — the build is non-deterministic"
      _r42_fail=1
    fi
  fi
  rm -f "$_r42_a" "$_r42_b" 2>/dev/null || true
fi
if [[ $_r42_fail -eq 0 ]]; then pass_rule "architecture_graph_idempotent"; fi

# ===========================================================================
# W1.x Phase 1 — L0 ironclad-rule enforcers (Gate Rules 45-52)
# Authority: ADR-0069. Each rule fails on a detected violation today.
# ===========================================================================

# ---------------------------------------------------------------------------
# Rule 50 — rls_for_new_tenant_tables (enforcer E69, Rule 40 / P-J)
#
# Every Flyway migration creating a table with a tenant_id column MUST
# enable RLS in the same file (ENABLE ROW LEVEL SECURITY) OR be listed in
# gate/rls-baseline-grandfathered.txt.
# ---------------------------------------------------------------------------
_r50_fail=0
_r50_baseline="gate/rls-baseline-grandfathered.txt"
_r50_baseline_paths=""
if [[ -f "$_r50_baseline" ]]; then
  _r50_baseline_paths="$(grep -vE '^[[:space:]]*(#|$)' "$_r50_baseline" 2>/dev/null || true)"
fi
while IFS= read -r _r50_mig; do
  [[ -z "$_r50_mig" ]] && continue
  # Does this migration create a table with tenant_id?
  if ! grep -qE 'tenant_id[[:space:]]+(UUID|uuid|VARCHAR|varchar|TEXT|text)' "$_r50_mig" 2>/dev/null; then
    continue
  fi
  if ! grep -qiE 'CREATE[[:space:]]+TABLE' "$_r50_mig" 2>/dev/null; then
    continue
  fi
  # Has it enabled RLS in the same file?
  if grep -qiE 'ENABLE[[:space:]]+ROW[[:space:]]+LEVEL[[:space:]]+SECURITY' "$_r50_mig" 2>/dev/null; then
    continue
  fi
  # Is it grandfathered?
  _r50_norm="$(printf '%s' "$_r50_mig" | sed -E 's|^\./||')"
  if printf '%s\n' "$_r50_baseline_paths" | grep -qFx "$_r50_norm"; then
    continue
  fi
  fail_rule "rls_for_new_tenant_tables" "$_r50_mig creates a tenant-scoped table without ENABLE ROW LEVEL SECURITY; not in $_r50_baseline either"
  _r50_fail=1
done <<< "$(find agent-service/src/main/resources/db/migration agent-service/src/main/resources/db/migration -maxdepth 1 -type f -name 'V*.sql' 2>/dev/null || true)"
if [[ $_r50_fail -eq 0 ]]; then pass_rule "rls_for_new_tenant_tables"; fi

# ---------------------------------------------------------------------------
# Rule 58 — s2c_callback_yaml_present_and_wellformed (enforcer E81, Rule 46 / P-M, ADR-0074)
#
# docs/contracts/s2c-callback.v1.yaml MUST exist with schema: header, a request:
# block listing the 6 mandatory fields (callback_id, server_run_id, capability_ref,
# request_payload, trace_id, idempotency_key), a response: block, and an
# outcome_values: block declaring exactly {ok, error, timeout}.
# Drift would let an S2C transport accept envelopes that violate the Phase 3a
# cross-rule audit's propagation contract (response doctrine §5.2).
# ---------------------------------------------------------------------------
_r58_fail=0
_r58_path="docs/contracts/s2c-callback.v1.yaml"
if [[ ! -f "$_r58_path" ]]; then
  fail_rule "s2c_callback_yaml_present_and_wellformed" "$_r58_path missing -- Rule 46 / P-M S2C callback contract unenforced"
  _r58_fail=1
else
  if ! grep -qE '^schema:[[:space:]]+s2c-callback/v1[[:space:]]*$' "$_r58_path"; then
    fail_rule "s2c_callback_yaml_present_and_wellformed" "$_r58_path missing 'schema: s2c-callback/v1' header"
    _r58_fail=1
  fi
  if ! grep -qE '^request:[[:space:]]*$' "$_r58_path"; then
    fail_rule "s2c_callback_yaml_present_and_wellformed" "$_r58_path missing request: block"
    _r58_fail=1
  fi
  if ! grep -qE '^response:[[:space:]]*$' "$_r58_path"; then
    fail_rule "s2c_callback_yaml_present_and_wellformed" "$_r58_path missing response: block"
    _r58_fail=1
  fi
  # 6 mandatory request fields per audit §5.2
  for _r58_field in callback_id server_run_id capability_ref request_payload trace_id idempotency_key; do
    if ! grep -qE "^[[:space:]]+- ${_r58_field}([[:space:]]|#|\$)" "$_r58_path"; then
      fail_rule "s2c_callback_yaml_present_and_wellformed" "$_r58_path missing mandatory request field: ${_r58_field}"
      _r58_fail=1
    fi
  done
  # Outcome enum closed at exactly ok | error | timeout
  for _r58_oc in ok error timeout; do
    if ! grep -qE "^[[:space:]]+- ${_r58_oc}([[:space:]]|#|\$)" "$_r58_path"; then
      fail_rule "s2c_callback_yaml_present_and_wellformed" "$_r58_path outcome_values missing entry: ${_r58_oc}"
      _r58_fail=1
    fi
  done
fi
if [[ $_r58_fail -eq 0 ]]; then pass_rule "s2c_callback_yaml_present_and_wellformed"; fi

# ===========================================================================
# Cross-corpus consistency audit prevention rules (2026-05-17)
# Authority: docs/logs/reviews/2026-05-17-cross-corpus-consistency-audit-response.en.md
# Closes structural design flaws G1, G2, G3 surfaced by the audit:
#   G1 — module count was hardcoded in 4 places
#   G2 — no metadata-vs-pom dependency cross-check
#   G3 — no SPI-package exhaustiveness cross-check
# Rules 64-66 with enforcer rows E94-E96 and 6 self-tests (2 per rule).
# ===========================================================================

# ===========================================================================
# CLAUDE.md token-optimization wave -- PR1 (2026-05-17)
# Authority: docs/governance/rules/rule-{67..71}.md
# Goal: shrink always-loaded governance set from ~99K -> ~10.6K tokens.
# Rules 67-71 with enforcer rows E97-E101 and 10 self-tests (2 per rule).
# ===========================================================================

# ===========================================================================
# Gate-script efficiency wave PR-E1 (2026-05-17)
# Authority: docs/governance/rules/rule-73.md
# ===========================================================================

# ===========================================================================
# Wave 4 — small rule activations (2026-05-18)
# ===========================================================================

# ---------------------------------------------------------------------------
# Rule 11 — contract_spine_tenant_id_required (enforcer E105)
# Every persistent record under
#   agent-service/src/main/java/com/huawei/ascend/service/runtime/runs/Run.java
# OR
#   agent-service/src/main/java/com/huawei/ascend/service/runtime/idempotency/IdempotencyRecord.java
# MUST declare a String tenantId component. Scope path relocated from
# agent-runtime-core to agent-service per ADR-0088 (rc13 dissolution).
# Process-internal opt-out via "// scope: process-internal" same-line comment.
# ---------------------------------------------------------------------------
_r11_fail=0
_r11_roots=(
  'agent-service/src/main/java/com/huawei/ascend/service/runtime/runs'
  'agent-service/src/main/java/com/huawei/ascend/service/runtime/idempotency'
)
for _r11_root in "${_r11_roots[@]}"; do
  [[ -d "$_r11_root" ]] || continue
  _r11_hits="$(grep -rEln 'public[[:space:]]+record[[:space:]]' "$_r11_root" 2>/dev/null || true)"
  while IFS= read -r _r11_f; do
    [[ -z "$_r11_f" ]] && continue
    if grep -qE 'scope:[[:space:]]*process-internal' "$_r11_f" 2>/dev/null; then
      continue
    fi
    if ! grep -qE 'String[[:space:]]+tenantId' "$_r11_f" 2>/dev/null; then
      fail_rule "contract_spine_tenant_id_required" "$_r11_f declares a record without a String tenantId component (Rule R-C.c / E105)"
      _r11_fail=1
    fi
  done <<< "$_r11_hits"
done
if [[ $_r11_fail -eq 0 ]]; then pass_rule "contract_spine_tenant_id_required"; fi

# ---------------------------------------------------------------------------
# Rule 24.c — runlifecycle_cancel_reauthz_shipped (enforcer E106)
# agent-service RunController MUST expose POST /v1/runs/{runId}/cancel
# with tenant re-validation + RunStateMachine validation + audit log.
# ---------------------------------------------------------------------------
_r24_fail=0
_r24_path='agent-service/src/main/java/com/huawei/ascend/service/platform/web/runs/RunController.java'
if [[ ! -f "$_r24_path" ]]; then
  fail_rule "runlifecycle_cancel_reauthz_shipped" "$_r24_path missing — Rule 24.c expects RunController to host the cancel surface"
  _r24_fail=1
elif ! grep -qE '/v1/runs/\{[a-zA-Z]+\}/cancel' "$_r24_path" 2>/dev/null; then
  fail_rule "runlifecycle_cancel_reauthz_shipped" "$_r24_path missing the POST /v1/runs/{runId}/cancel mapping"
  _r24_fail=1
elif ! grep -qE 'tenantId\(\)' "$_r24_path" 2>/dev/null; then
  fail_rule "runlifecycle_cancel_reauthz_shipped" "$_r24_path cancel handler does not re-validate tenantId"
  _r24_fail=1
fi
if [[ $_r24_fail -eq 0 ]]; then pass_rule "runlifecycle_cancel_reauthz_shipped"; fi

# ---------------------------------------------------------------------------
# Rule 29.c — quickstart_smoke_job_present (enforcer E107)
# .github/workflows/ci.yml MUST contain a job named quickstart-smoke that
# polls /v1/health.
# ---------------------------------------------------------------------------
_r29c_fail=0
_r29c_path='.github/workflows/ci.yml'
if [[ ! -f "$_r29c_path" ]]; then
  fail_rule "quickstart_smoke_job_present" "$_r29c_path missing — Rule 29.c requires a CI workflow"
  _r29c_fail=1
elif ! grep -qE '^[[:space:]]*quickstart-smoke:' "$_r29c_path" 2>/dev/null; then
  fail_rule "quickstart_smoke_job_present" "$_r29c_path missing job 'quickstart-smoke' — Rule 29.c"
  _r29c_fail=1
elif ! grep -qF '/v1/health' "$_r29c_path" 2>/dev/null; then
  fail_rule "quickstart_smoke_job_present" "$_r29c_path quickstart-smoke job does not poll /v1/health"
  _r29c_fail=1
fi
if [[ $_r29c_fail -eq 0 ]]; then pass_rule "quickstart_smoke_job_present"; fi

# ===========================================================================
# SPI metadata integrity wave (2026-05-18)
# Authority: docs/governance/rules/rule-{75..78}.md
# Rules 75-78 with enforcer rows E108-E111. Prevents the SPI declaration vs
# physical layout drift surfaced by the 2026-05-18 SPI integrity audit
# (T2.B2 extraction left engine.spi empty + orchestration.spi double-claimed
# across two Maven modules + dfx yaml omitting/mis-nesting spi_packages).
# ===========================================================================

# ===========================================================================
# 2026-05-18 rc4 cross-constraint review response prevention wave -- Rule 83
# Authority: docs/governance/rules/rule-83.md
#            + docs/logs/reviews/2026-05-18-l0-rc4-cross-constraint-architecture-review.en.md
#            + docs/logs/reviews/2026-05-18-l0-rc4-cross-constraint-architecture-review-response.en.md
# Closes finding families:
#   P1-3 design-only contracts unregistered / dangling auth  -> Rule 83
# ===========================================================================

# ===========================================================================
# 2026-05-18 rc5 post-response review response prevention wave -- Rules 84-85
# Authority: docs/governance/rules/rule-84.md + rule-85.md
#            + docs/logs/reviews/2026-05-18-l0-rc5-post-response-architecture-review.en.md
#            + docs/logs/reviews/2026-05-18-l0-rc5-post-response-architecture-review-response.en.md
# Closes finding families:
#   P0-1 module-level ARCHITECTURE.md path claim drift after refactor   -> Rule 84
#   P1-2 catalog SPI row not backed by module spi_packages metadata    -> Rule 85
# ===========================================================================

# Rule 85 — catalog_spi_row_matches_module_spi_metadata (enforcer E118)
#
# Every row in docs/contracts/contract-catalog.md "Active SPI interfaces (N
# total)" table whose Status column does NOT contain "(internal)" MUST have
# its Module column resolve to a module whose
# module-metadata.yaml#spi_packages contains the row's Package column value
# (exact OR as a .spi-prefix sub-package match), AND the same module's
# docs/dfx/<module>.yaml#spi_packages MUST contain the same package.
# Operationalises rc5 review P1-2 closure: catalog SPI commitments must be
# backed by SPI metadata declarations on both sides of the Rule 78 set.
# ---------------------------------------------------------------------------
_r85_fail=0
_r85_catalog="docs/contracts/contract-catalog.md"
if [[ -f "$_r85_catalog" ]]; then
  # Find the SPI section header and total claim. Extract rows between header and the next
  # bold-heading separator. Header pattern: **Active SPI interfaces (N total):**
  _r85_header_lineno=$(grep -nE '^\*\*Active SPI interfaces \([0-9]+ total\):\*\*' "$_r85_catalog" 2>/dev/null | head -1 | cut -d: -f1)
  _r85_header_total=$(grep -oE '^\*\*Active SPI interfaces \([0-9]+ total\):\*\*' "$_r85_catalog" 2>/dev/null | head -1 | grep -oE '[0-9]+')
  if [[ -z "$_r85_header_lineno" ]]; then
    fail_rule "catalog_spi_row_matches_module_spi_metadata" "$_r85_catalog missing header '**Active SPI interfaces (N total):**' -- Rule 85 / E118"
    _r85_fail=1
  else
    # Scan rows starting at header_lineno; stop at first ** heading after a blank line, or at the next ** heading.
    _r85_active_rows=0
    _r85_lineno=0
    _r85_in_table=0
    while IFS= read -r _r85_line || [[ -n "$_r85_line" ]]; do
      _r85_lineno=$((_r85_lineno + 1))
      [[ $_r85_lineno -le $_r85_header_lineno ]] && continue
      # Stop scanning once we hit the next bold section heading.
      if [[ "$_r85_line" =~ ^\*\* ]] && [[ ! "$_r85_line" =~ ^\*\*Active\ SPI ]]; then break; fi
      # Table separator marker: skip rows that look like |---|---|---|---|
      [[ "$_r85_line" =~ ^\|[-:[:space:]\|]+\|$ ]] && continue
      [[ ! "$_r85_line" =~ ^\| ]] && continue
      [[ "$_r85_line" =~ ^\|[[:space:]]*Interface ]] && continue
      # Parse | Interface | Module | Package | Status |
      _r85_iface=$(echo "$_r85_line" | awk -F'|' '{gsub(/^[[:space:]]+|[[:space:]]+$/, "", $2); print $2}' | tr -d '`')
      _r85_mod=$(echo "$_r85_line" | awk -F'|' '{gsub(/^[[:space:]]+|[[:space:]]+$/, "", $3); print $3}' | tr -d '`')
      _r85_pkg=$(echo "$_r85_line" | awk -F'|' '{gsub(/^[[:space:]]+|[[:space:]]+$/, "", $4); print $4}' | tr -d '`')
      _r85_status=$(echo "$_r85_line" | awk -F'|' '{gsub(/^[[:space:]]+|[[:space:]]+$/, "", $5); print $5}')
      [[ -z "$_r85_iface" || -z "$_r85_mod" || -z "$_r85_pkg" ]] && continue
      # Internal-marker exemption: skip the metadata + DFX checks AND exclude from the count.
      if echo "$_r85_status" | grep -qi '(internal)'; then continue; fi
      _r85_active_rows=$((_r85_active_rows + 1))
      _r85_meta="$_r85_mod/module-metadata.yaml"
      _r85_dfx="docs/dfx/$_r85_mod.yaml"
      if [[ ! -f "$_r85_meta" ]]; then
        fail_rule "catalog_spi_row_matches_module_spi_metadata" "$_r85_catalog:$_r85_lineno row for $_r85_iface points at module $_r85_mod but $_r85_meta does not exist -- Rule 85 / E118"
        _r85_fail=1; continue
      fi
      if [[ ! -f "$_r85_dfx" ]]; then
        fail_rule "catalog_spi_row_matches_module_spi_metadata" "$_r85_catalog:$_r85_lineno row for $_r85_iface points at module $_r85_mod but $_r85_dfx does not exist -- Rule 85 / E118"
        _r85_fail=1; continue
      fi
      # Extract metadata spi_packages list (only entries under the top-level spi_packages: block;
      # stop at the next non-indented key).
      _r85_meta_pkgs=$(awk '
        /^spi_packages:/{f=1; next}
        f && /^[^[:space:]]/{exit}
        f && /^[[:space:]]*-[[:space:]]+/{sub(/^[[:space:]]*-[[:space:]]+/, ""); sub(/[[:space:]]+#.*$/, ""); print}
      ' "$_r85_meta" 2>/dev/null)
      _r85_dfx_pkgs=$(awk '
        /^spi_packages:/{f=1; next}
        f && /^[^[:space:]]/{exit}
        f && /^[[:space:]]*-[[:space:]]+/{sub(/^[[:space:]]*-[[:space:]]+/, ""); sub(/[[:space:]]+#.*$/, ""); print}
      ' "$_r85_dfx" 2>/dev/null)
      # Match: exact OR catalog-pkg starts with metadata-pkg as a prefix followed by . (sub-package).
      _r85_meta_match=0
      while IFS= read -r _r85_meta_entry; do
        [[ -z "$_r85_meta_entry" ]] && continue
        if [[ "$_r85_pkg" == "$_r85_meta_entry" ]] || [[ "$_r85_pkg" == "$_r85_meta_entry".* ]] || [[ "$_r85_meta_entry" == "$_r85_pkg".* ]]; then
          _r85_meta_match=1; break
        fi
      done <<< "$_r85_meta_pkgs"
      if [[ $_r85_meta_match -eq 0 ]]; then
        fail_rule "catalog_spi_row_matches_module_spi_metadata" "$_r85_catalog:$_r85_lineno row for $_r85_iface declares package '$_r85_pkg' not present in $_r85_meta#spi_packages: ($_r85_meta_pkgs) -- Rule 85 / E118"
        _r85_fail=1; continue
      fi
      _r85_dfx_match=0
      while IFS= read -r _r85_dfx_entry; do
        [[ -z "$_r85_dfx_entry" ]] && continue
        if [[ "$_r85_pkg" == "$_r85_dfx_entry" ]] || [[ "$_r85_pkg" == "$_r85_dfx_entry".* ]] || [[ "$_r85_dfx_entry" == "$_r85_pkg".* ]]; then
          _r85_dfx_match=1; break
        fi
      done <<< "$_r85_dfx_pkgs"
      if [[ $_r85_dfx_match -eq 0 ]]; then
        fail_rule "catalog_spi_row_matches_module_spi_metadata" "$_r85_catalog:$_r85_lineno row for $_r85_iface declares package '$_r85_pkg' not present in $_r85_dfx#spi_packages: ($_r85_dfx_pkgs) -- Rule 85 / E118"
        _r85_fail=1; continue
      fi
    done < "$_r85_catalog"
    # Header count consistency: (N total) MUST equal the number of non-internal rows.
    if [[ -n "$_r85_header_total" ]] && [[ "$_r85_header_total" != "$_r85_active_rows" ]]; then
      fail_rule "catalog_spi_row_matches_module_spi_metadata" "$_r85_catalog header claims '$_r85_header_total total' but counted $_r85_active_rows non-(internal) SPI rows -- Rule 85 / E118"
      _r85_fail=1
    fi
  fi
fi
if [[ $_r85_fail -eq 0 ]]; then pass_rule "catalog_spi_row_matches_module_spi_metadata"; fi

# ---------------------------------------------------------------------------
# Wave history (rc6 -> rc7 -> rc8 prevention waves)
# ===========================================================================
# 2026-05-18 rc6 post-response wave -- Rules 86-87 (E119, E120)
# 2026-05-18 rc8 post-corrective wave -- Rules 88-89 (E121, E122) + Rule 86 fenced-tree-block extension
# Authority cards: docs/governance/rules/rule-86.md, rule-87.md, rule-88.md, rule-89.md
# Reviews:    docs/logs/reviews/2026-05-18-l0-rc6-post-response-architecture-review.en.md
#             docs/logs/reviews/2026-05-18-l0-rc7-post-corrective-architecture-review.en.md
# Responses:  docs/logs/reviews/2026-05-18-l0-rc6-post-response-architecture-review-response.en.md
#             docs/logs/reviews/2026-05-18-l0-rc7-post-corrective-architecture-review-response.en.md
# Closes finding families:
#   rc6 P0-2 root ARCHITECTURE.md 8-module + stale path claims  -> Rule 86 (rc7)
#   rc6 P1-2 status_yaml allowed_claim stale module names        -> Rule 87 (rc7)
#   rc7 P0-1 GraphMemoryRepository ownership corpus drift        -> Rule 86 fenced-tree-block extension (rc8)
#   rc7 P0-2 check_parallel.sh skips Rules 86/87                 -> Rule 88 (rc8)
#   rc7 P1-1 test harness fail-open + hardcoded TOTAL            -> Rule 89 (rc8)
# ===========================================================================

# Rule 88 — serial_parallel_gate_slug_parity (enforcer E121)
#
# Closes rc7 post-corrective review P0-2: check_parallel.sh silently skipped
# Rules 86-87 because (a) its awk exit pattern was `^# Summary$` (a comment
# header that happened to live between Rule 85 and Rule 86), and (b) its
# header regex required em-dash `—` while Rules 86-87 originally used
# double-dash `--`. Both defects compound: even fixing one would leave the
# other. Rule 88 asserts at gate time that the set of rule headers the
# canonical script defines equals the set the parallel wrapper would extract.
# ---------------------------------------------------------------------------
_r88_fail=0
_r88_canonical="gate/check_architecture_sync.sh"
_r88_parallel="gate/check_parallel.sh"
if [[ ! -f "$_r88_canonical" ]] || [[ ! -f "$_r88_parallel" ]]; then
  fail_rule "serial_parallel_gate_slug_parity" "canonical or parallel script missing -- Rule 88 / E121"
  _r88_fail=1
else
  _r88_canonical_set=$(grep -E '^# Rule [0-9]+.?[a-z]? (—|--) ' "$_r88_canonical" \
    | sed -E 's/^# Rule [0-9]+.?[a-z]? (—|--) //' \
    | awk '{print $1}' \
    | sort -u)
  _r88_parallel_set=$(awk '
    /^# Rule [0-9]+.?[a-z]? (—|--) / {
      str = substr($0, 8)
      space_idx = index(str, " ")
      rest = substr(str, space_idx + 1)
      sub(/^[^a-zA-Z0-9_]*/, "", rest)
      match(rest, /^[a-zA-Z0-9_]+/)
      print substr(rest, RSTART, RLENGTH)
    }
    /^# === END OF RULES ===$/ { exit }
  ' "$_r88_canonical" | sort -u)
  _r88_missing=$(comm -23 <(echo "$_r88_canonical_set") <(echo "$_r88_parallel_set") | grep -v '^$' || true)
  _r88_extra=$(comm -13 <(echo "$_r88_canonical_set") <(echo "$_r88_parallel_set") | grep -v '^$' || true)
  if [[ -n "$_r88_missing" ]]; then
    fail_rule "serial_parallel_gate_slug_parity" "parallel wrapper would skip rule(s): $(echo "$_r88_missing" | tr '\n' ' ')-- Rule 88 / E121 (serial-canonical defines rules the parallel awk extraction misses)"
    _r88_fail=1
  fi
  if [[ -n "$_r88_extra" ]]; then
    fail_rule "serial_parallel_gate_slug_parity" "parallel awk would extract rule(s) not defined as canonical pass_rule blocks: $(echo "$_r88_extra" | tr '\n' ' ')-- Rule 88 / E121"
    _r88_fail=1
  fi
  # Sub-check: canonical separator consistency — every rule header MUST use em-dash `—`.
  _r88_bad_sep=$(grep -nE '^# Rule [0-9]+.?[a-z]? -- ' "$_r88_canonical" | head -3 || true)
  if [[ -n "$_r88_bad_sep" ]]; then
    fail_rule "serial_parallel_gate_slug_parity" "rule header(s) use double-dash separator instead of em-dash: $(echo "$_r88_bad_sep" | tr '\n' '|') -- Rule 88 / E121 (separator consistency)"
    _r88_fail=1
  fi
  # Sub-check: parallel wrapper MUST declare END marker awk-extraction terminator
  if ! grep -qE '^# === END OF RULES ===$' "$_r88_canonical"; then
    fail_rule "serial_parallel_gate_slug_parity" "$_r88_canonical missing '# === END OF RULES ===' terminator marker that check_parallel.sh awk uses to bound rule extraction -- Rule 88 / E121"
    _r88_fail=1
  fi
fi
if [[ $_r88_fail -eq 0 ]]; then pass_rule "serial_parallel_gate_slug_parity"; fi

# Rule 89 — self_test_harness_fail_closed_coverage (enforcer E122)
#
# Closes rc7 post-corrective review P1-1: gate/test_architecture_sync_gate.sh
# hardcoded TOTAL=143 + exited 0 when failed=0 regardless of whether
# passed<TOTAL. Rule 89 asserts that the harness (a) fails closed when
# passed != TOTAL, (b) computes TOTAL from a manifest rather than from a
# bare literal, and (c) every rule defined in check_architecture_sync.sh
# has at least one test_rule_<N>_* fixture in the harness.
# ---------------------------------------------------------------------------
_r89_fail=0
_r89_harness="gate/test_architecture_sync_gate.sh"
_r89_canonical="gate/check_architecture_sync.sh"
if [[ ! -f "$_r89_harness" ]]; then
  fail_rule "self_test_harness_fail_closed_coverage" "$_r89_harness missing -- Rule 89 / E122"
  _r89_fail=1
else
  # Sub-check (a): harness MUST contain a fail-closed clause comparing passed vs TOTAL
  if ! grep -qE 'passed[^=]*!=[^=]*\$\{?TOTAL\}?|\$\{?passed\}?[[:space:]]+-ne[[:space:]]+\$\{?TOTAL\}?|"\$passed"[[:space:]]+-ne[[:space:]]+"\$TOTAL"' "$_r89_harness"; then
    fail_rule "self_test_harness_fail_closed_coverage" "$_r89_harness missing 'passed != TOTAL' fail-closed clause -- Rule 89 / E122 sub-check (a) (harness exits 0 when passed<TOTAL — must fail closed)"
    _r89_fail=1
  fi
  # Sub-check (b): TOTAL MUST NOT be a bare literal — must derive from a manifest.
  # Skip lines inside `<<'SHEOF' ... SHEOF` heredoc blocks (synthetic test fixtures
  # legitimately contain `TOTAL=NNN` to test the rule's own detection — see
  # test_rule89_bare_literal_neg).
  _r89_literal_lines=$(awk '
    /^[[:space:]]*cat[[:space:]]+>[[:space:]]+.*<<.SHEOF.$/ { hd=1; next }
    /^SHEOF$/ { hd=0; next }
    !hd && /^[[:space:]]*TOTAL=[0-9]+[[:space:]]*$/ { printf "%d:%s\n", NR, $0 }
  ' "$_r89_harness" || true)
  if [[ -n "$_r89_literal_lines" ]]; then
    fail_rule "self_test_harness_fail_closed_coverage" "$_r89_harness has bare-literal TOTAL declaration(s) at top level (not inside heredoc fixtures): $(echo "$_r89_literal_lines" | tr '\n' '|') -- Rule 89 / E122 sub-check (b) (TOTAL must derive from a manifest, not a literal)"
    _r89_fail=1
  fi
  # Sub-check (c): every PREVENTION-WAVE canonical rule (Rules >= 80; rc4-rc8 waves)
  # has at least one test fixture. Pre-rc4 rules (1-79) are grandfathered — many
  # were covered by ArchUnit or integration tests at design time rather than
  # by inline self-test fixtures, and retrofitting fixtures for ~40 legacy rules
  # is out of rc8 scope. Rule 89's purpose is to prevent NEWLY-ADDED rules from
  # shipping without coverage; the prevention waves established the convention,
  # so the scope tracks that convention.
  if [[ -f "$_r89_canonical" ]]; then
    _r89_canonical_ids=$(grep -E '^# Rule [0-9]+.?[a-z]? (—|--) ' "$_r89_canonical" \
      | sed -E 's/^# Rule ([0-9]+.?[a-z]?) (—|--) .*/\1/' \
      | sort -u)
    _r89_missing_fixtures=""
    for _r89_rid in $_r89_canonical_ids; do
      # Scope: only enforce coverage for prevention-wave rules (Rules >= 80,
      # main numeric IDs only — sub-rules like 28a are grandfathered).
      _r89_rid_num=$(echo "$_r89_rid" | grep -oE '^[0-9]+')
      [[ -z "$_r89_rid_num" ]] && continue
      [[ "$_r89_rid_num" -lt 80 ]] && continue
      # Look for test_rule_<N>_ or test_rule_<N>(  pattern in any form.
      if ! grep -qE "(^test_rule_${_r89_rid}_|^test_rule_${_r89_rid}\(|^test_rule${_r89_rid}_|^test_rule${_r89_rid}\()" "$_r89_harness"; then
        if ! grep -qE "\"rule_${_r89_rid}_|'rule_${_r89_rid}_" "$_r89_harness"; then
          _r89_missing_fixtures="${_r89_missing_fixtures}${_r89_rid} "
        fi
      fi
    done
    if [[ -n "$_r89_missing_fixtures" ]]; then
      fail_rule "self_test_harness_fail_closed_coverage" "$_r89_harness lacks test fixture(s) for prevention-wave Rule(s) >=80: ${_r89_missing_fixtures}-- Rule 89 / E122 sub-check (c) (every prevention-wave Rule MUST have >=1 test_rule_<N>_* fixture; pre-rc4 rules 1-79 grandfathered)"
      _r89_fail=1
    fi
  fi
fi
if [[ $_r89_fail -eq 0 ]]; then pass_rule "self_test_harness_fail_closed_coverage"; fi

# ---------------------------------------------------------------------------
# Rule 93 — dfx_stem_matches_module (enforcer E127)
#
# Closes rc8 post-corrective review P0-3: `docs/dfx/agent-platform.yaml`
# remained on disk after ADR-0078 deleted the agent-platform module.
# Rule 93 asserts that every `docs/dfx/<stem>.yaml` (not under archive/) has
# a stem matching some `<module>` entry in root `pom.xml`.
# ---------------------------------------------------------------------------
_r93_fail=0
_r93_dfx_dir="docs/dfx"
_r93_pom="pom.xml"
if [[ ! -d "$_r93_dfx_dir" ]] || [[ ! -f "$_r93_pom" ]]; then
  fail_rule "dfx_stem_matches_module" "$_r93_dfx_dir or $_r93_pom missing — Rule 93 / E127"
  _r93_fail=1
else
  _r93_pom_modules=$(grep -oE '<module>[^<]+</module>' "$_r93_pom" | sed -E 's|</?module>||g' | sort -u)
  _r93_orphans=""
  for _r93_dfx in "$_r93_dfx_dir"/*.yaml; do
    [[ -e "$_r93_dfx" ]] || continue
    _r93_stem=$(basename "$_r93_dfx" .yaml)
    if ! echo "$_r93_pom_modules" | grep -qxF "$_r93_stem"; then
      _r93_orphans="${_r93_orphans}${_r93_stem} "
    fi
  done
  if [[ -n "$_r93_orphans" ]]; then
    fail_rule "dfx_stem_matches_module" "$_r93_dfx_dir has DFX files for non-existent modules: ${_r93_orphans}-- Rule 93 / E127 (delete the orphan DFX file or archive it; closes rc8 post-corrective P0-3)"
    _r93_fail=1
  fi
fi
if [[ $_r93_fail -eq 0 ]]; then pass_rule "dfx_stem_matches_module"; fi

# ---------------------------------------------------------------------------
# Rule 94 — active_corpus_deleted_module_name_truth (enforcer E129)
#
# Closes rc8 post-corrective review P1-3: Rule 87 only guards
# architecture-status.yaml allowed_claim text; current-tense pre-Phase-C
# module names still appeared in ARCHITECTURE.md §4 constraints, rule cards,
# and test Javadocs. Rule 94 widens the path-truth check to those surfaces.
#
# Scope: active `.md`, `.yaml`, and `*.java` files NOT under docs/archive/,
# docs/logs/reviews/, docs/logs/releases/2026-05-1[0-7]-*.md (historical), and lines
# inside fenced code blocks OR yaml comments. Pattern: word-boundary
# `agent-platform` OR `agent-runtime` (negative-filtered against
# `agent-runtime-core`). Exemption: a historical marker on the same line OR
# within ±3 lines.
# ---------------------------------------------------------------------------
_r94_fail=0
_r94_marker_vocab="gate/active-corpus-name-exemption-markers.txt"
_r94_path_vocab="gate/active-corpus-name-exemption-paths.txt"
if [[ ! -f "$_r94_marker_vocab" ]]; then
  fail_rule "active_corpus_deleted_module_name_truth" "$_r94_marker_vocab missing -- Rule 94 / E129 (Wave 2 vocabulary externalisation)"
  _r94_fail=1
fi
if [[ ! -f "$_r94_path_vocab" ]]; then
  fail_rule "active_corpus_deleted_module_name_truth" "$_r94_path_vocab missing -- Rule 94 / E129 (Wave 2 vocabulary externalisation)"
  _r94_fail=1
fi
# Perf fix (2026-05-23): the original loop forked `awk` once per file in
# the active corpus (~thousands of files post-exempt). On WSL/mnt/d that
# was ~19s per gate run. Replaced with a single python pass that prunes
# excluded dirs via os.walk, applies the same exempt-prefix + test-resource
# filter, and checks the same three deleted-module patterns with ±3-line
# marker exemption. Same semantics, same `gate/active-corpus-name-*` vocab.
_r94_violations="$(
  GATE_R94_MARKER_VOCAB="$_r94_marker_vocab" \
  GATE_R94_PATH_VOCAB="$_r94_path_vocab" \
  "${GATE_PYTHON_BIN:-python3}" - <<'PYEOF'
import os, re, sys
from pathlib import Path

marker_vocab = os.environ['GATE_R94_MARKER_VOCAB']
path_vocab   = os.environ['GATE_R94_PATH_VOCAB']

def load_vocab(p):
    out = []
    if not os.path.isfile(p): return out
    for line in Path(p).read_text(encoding='utf-8', errors='replace').splitlines():
        s = line.strip()
        if not s or s.startswith('#'): continue
        out.append(s)
    return out

markers = load_vocab(marker_vocab)
marker_re = re.compile('|'.join(markers)) if markers else None
exempt_prefixes = tuple(load_vocab(path_vocab))

# Word-boundary surrogate matching the awk version exactly.
ap_re  = re.compile(r'(?:^|[^a-zA-Z0-9_-])agent-platform(?:[^a-zA-Z0-9_-]|$)')
ar_re  = re.compile(r'(?:^|[^a-zA-Z0-9_-])agent-runtime(?:[^a-zA-Z0-9_-]|$)')
arc_re = re.compile(r'(?:^|[^a-zA-Z0-9_-])agent-runtime-core(?:[^a-zA-Z0-9_-]|$)')
fence_re = re.compile(r'^\s*```')
yaml_comment_re = re.compile(r'^\s*#')

# Build file list via os.walk with topdown pruning (faster than find on /mnt/d).
EXTS = ('.md', '.yaml', '.yml', '.java')
PRUNE = {'target', '.git', 'node_modules'}
files: list[str] = []
for root, dirs, fnames in os.walk('.', topdown=True):
    dirs[:] = [d for d in dirs if d not in PRUNE]
    for fn in fnames:
        if not fn.endswith(EXTS): continue
        rel = os.path.join(root, fn)
        if rel.startswith('./'): rel = rel[2:]
        files.append(rel)
files.sort()

violations: list[str] = []
for f in files:
    if '/src/test/resources/' in f: continue
    if any(f.startswith(p) for p in exempt_prefixes): continue
    try:
        text = Path(f).read_text(encoding='utf-8', errors='replace')
    except OSError:
        continue
    lines = text.splitlines()
    n = len(lines)
    in_code = False
    # Two-pass to track fence state up-front (matches awk semantics: state
    # established by first walk, then validated in second walk).
    fence_state = [False] * n
    s = False
    for i, ln in enumerate(lines):
        if fence_re.match(ln):
            s = not s
            fence_state[i] = s
            continue
        fence_state[i] = s
    for i, ln in enumerate(lines):
        if fence_re.match(ln): continue
        if fence_state[i]: continue
        if yaml_comment_re.match(ln): continue
        hit = ap_re.search(ln) or (ar_re.search(ln) and not arc_re.search(ln)) or arc_re.search(ln)
        if not hit: continue
        lo = max(0, i - 3); hi = min(n, i + 4)
        if marker_re:
            window = ' '.join(lines[lo:hi])
            if marker_re.search(window): continue
        violations.append(f"{f}:{i+1}:{ln}")

for v in violations:
    print(v)
PYEOF
)"
if [[ -n "$_r94_violations" ]]; then
  _r94_first=$(printf '%s\n' "$_r94_violations" | head -5 | tr '\n' '|')
  fail_rule "active_corpus_deleted_module_name_truth" "active corpus contains current-tense pre-Phase-C module name(s) without historical marker (first 5): ${_r94_first}-- Rule 94 / E129 (markers loaded from gate/active-corpus-name-exemption-markers.txt; exempt paths from gate/active-corpus-name-exemption-paths.txt)"
  _r94_fail=1
fi
if [[ $_r94_fail -eq 0 ]]; then pass_rule "active_corpus_deleted_module_name_truth"; fi

# ---------------------------------------------------------------------------
# Rule 95 — spi_catalog_exhaustiveness (enforcer E131)
#
# Closes rc8 post-corrective review P1-2: SkillCapacityRegistry was a public
# interface under a declared *.spi.* package but absent from
# contract-catalog.md §2 "Active SPI interfaces" table. Rule 95 asserts that
# every public `interface Foo` declared in a Java file under any `*.spi.*`
# package path appears in `docs/contracts/contract-catalog.md` as either an
# active SPI row OR is explicitly marked `(internal)`.
# ---------------------------------------------------------------------------
_r95_fail=0
_r95_catalog="docs/contracts/contract-catalog.md"
if [[ ! -f "$_r95_catalog" ]]; then
  fail_rule "spi_catalog_exhaustiveness" "$_r95_catalog missing — Rule 95 / E131"
  _r95_fail=1
else
  _r95_missing=""
  while IFS= read -r _r95_spi_file; do
    [[ -z "$_r95_spi_file" ]] && continue
    # Extract `public interface XXX` declarations — EXCLUDING sealed and non-sealed
    # interfaces (the contract-catalog convention classifies sealed types as
    # "Structural carriers" rather than SPI; matches `public interface` only).
    _r95_iface=$(grep -E '^public[[:space:]]+interface[[:space:]]+[A-Za-z_][A-Za-z0-9_]*' "$_r95_spi_file" 2>/dev/null | head -1 | sed -E 's/^public[[:space:]]+interface[[:space:]]+([A-Za-z_][A-Za-z0-9_]*).*/\1/')
    [[ -z "$_r95_iface" ]] && continue
    # Check catalog for the interface name (either as ` `Iface` ` cell or `(internal)` mark)
    if ! grep -qE "\`${_r95_iface}\`" "$_r95_catalog"; then
      _r95_missing="${_r95_missing}${_r95_iface}(${_r95_spi_file}) "
    fi
  done < <(find . -type f -name '*.java' -path '*/spi/*' -not -path './target/*' -not -path './*/target/*' -not -path './.git/*')
  if [[ -n "$_r95_missing" ]]; then
    fail_rule "spi_catalog_exhaustiveness" "public SPI interface(s) missing from $_r95_catalog: ${_r95_missing}-- Rule 95 / E131 (add as active SPI row OR mark '(internal)'; rc8 post-corrective P1-2 closure)"
    _r95_fail=1
  fi
fi
if [[ $_r95_fail -eq 0 ]]; then pass_rule "spi_catalog_exhaustiveness"; fi

# ---------------------------------------------------------------------------
# Rule 97 — release_note_numeric_truth (enforcer E135)
#
# Closes rc10 I-α-2: rc9 release note declared "360 nodes / 510 edges" while
# the live architecture-graph.yaml header reported 369 / 520. Rule 91 narrowly
# checks baseline_metrics keys; release-note prose drift went uncaught.
# Rule 97 scans the LATEST release note (lex-sort tail -1) for the canonical
# "<N> nodes / <M> edges" claim and asserts equality with live values from
# `architecture-graph.yaml`. Older release notes are historical snapshots and
# auto-exempt (each captured the count at its wave time). Lines containing
# `rc[N] correction`, `rc[N] first cut`, `rc[N] snapshot`, or `historical`
# within ±3 lines are also exempt.
# ---------------------------------------------------------------------------
_r97_fail=0
_r97_graph="docs/governance/architecture-graph.yaml"
_r97_releases_dir="docs/logs/releases"
if [[ ! -f "$_r97_graph" ]]; then
  fail_rule "release_note_numeric_truth" "$_r97_graph missing — Rule 97 / E135 (cannot establish live node/edge baseline)"
  _r97_fail=1
elif [[ ! -d "$_r97_releases_dir" ]]; then
  fail_rule "release_note_numeric_truth" "$_r97_releases_dir missing — Rule 97 / E135"
  _r97_fail=1
else
  _r97_nodes=$(grep -E '^node_count:' "$_r97_graph" | head -1 | awk '{print $2}')
  _r97_edges=$(grep -E '^edge_count:' "$_r97_graph" | head -1 | awk '{print $2}')
  _r97_latest=$(latest_release_path "$_r97_releases_dir")
  if [[ -z "$_r97_latest" ]]; then
    : # no release notes yet — vacuously pass
  else
    _r97_markers='historical|rc[0-9]+ snapshot|rc[0-9]+ correction|rc[0-9]+ first cut|rc[0-9]+ baseline|superseded|previous|pre-rc[0-9]+'
    _r97_violations=$(awk -v live_n="$_r97_nodes" -v live_e="$_r97_edges" -v markers="$_r97_markers" '
      { lines[NR] = $0 }
      END {
        in_code = 0
        for (i = 1; i <= NR; i++) {
          line = lines[i]
          if (line ~ /^[[:space:]]*```/) { in_code = 1 - in_code; continue }
          if (in_code) continue
          # Compute marker window before deciding
          lo = i - 3; if (lo < 1) lo = 1
          hi = i + 3; if (hi > NR) hi = NR
          window = ""
          for (j = lo; j <= hi; j++) window = window " " lines[j]
          # Detect absolute (not delta) "<N> nodes" — i.e., no `+` immediately before the digits.
          if (line ~ /[^+0-9][0-9]+[[:space:]]+nodes/ || line ~ /^[0-9]+[[:space:]]+nodes/) {
            n_str = line
            sub(/^[^0-9]*\+[0-9]+[[:space:]]+nodes/, "", n_str)  # strip a leading delta if present
            if (match(n_str, /[^+0-9]?([0-9]+)[[:space:]]+nodes/)) {
              s = substr(n_str, RSTART, RLENGTH)
              gsub(/[^0-9]/, "", s)
              if (s != "" && s != live_n && window !~ markers) {
                print i ":nodes:claim=" s ":live=" live_n ":" line
              }
            }
          }
          if (line ~ /[^+0-9][0-9]+[[:space:]]+edges/ || line ~ /^[0-9]+[[:space:]]+edges/) {
            e_str = line
            sub(/^[^0-9]*\+[0-9]+[[:space:]]+edges/, "", e_str)
            if (match(e_str, /[^+0-9]?([0-9]+)[[:space:]]+edges/)) {
              s = substr(e_str, RSTART, RLENGTH)
              gsub(/[^0-9]/, "", s)
              if (s != "" && s != live_e && window !~ markers) {
                print i ":edges:claim=" s ":live=" live_e ":" line
              }
            }
          }
        }
      }
    ' "$_r97_latest" 2>/dev/null || true)
    if [[ -n "$_r97_violations" ]]; then
      _r97_first=$(echo "$_r97_violations" | head -5 | tr '\n' '|')
      fail_rule "release_note_numeric_truth" "latest release note $_r97_latest contains absolute node/edge count claim(s) that disagree with live $_r97_graph (nodes=$_r97_nodes, edges=$_r97_edges): ${_r97_first}-- Rule 97 / E135 (rc10 I-α-2 closure; either update the prose to match live counts OR add an 'rc[N] correction'/'rc[N] snapshot' marker within ±3 lines)"
      _r97_fail=1
    fi
    # Extension: scan for `N/M self-tests` and `N/M tests` ratio claims whose
    # DENOMINATOR diverges from baseline_metrics.gate_executable_test_cases.
    # Same marker-window exemption as the node/edge check above.
    _r97_status="docs/governance/architecture-status.yaml"
    if [[ -f "$_r97_status" ]]; then
      _r97_live_tests=$(grep -E '^[[:space:]]*gate_executable_test_cases:[[:space:]]+[0-9]+' "$_r97_status" | head -1 | awk -F'[: ]+' '{print $3}')
      if [[ -n "$_r97_live_tests" ]]; then
        _r97_test_violations=$(awk -v live="$_r97_live_tests" -v markers="$_r97_markers" '
          { lines[NR] = $0 }
          END {
            in_code = 0
            for (i = 1; i <= NR; i++) {
              line = lines[i]
              if (line ~ /^[[:space:]]*```/) { in_code = 1 - in_code; continue }
              if (in_code) continue
              lo = i - 3; if (lo < 1) lo = 1
              hi = i + 3; if (hi > NR) hi = NR
              window = ""
              for (j = lo; j <= hi; j++) window = window " " lines[j]
              # Match `<N>/<M> self-tests` or `<N>/<M> tests passed` or `<N>/<M> gate self-tests`
              if (match(line, /[0-9]+\/[0-9]+[[:space:]]+(self-tests|tests passed|tests pass|gate self-tests)/)) {
                matched_str = substr(line, RSTART, RLENGTH)
                split(matched_str, parts, "/")
                match(parts[2], /^[0-9]+/)
                denom = substr(parts[2], RSTART, RLENGTH)
                if (denom != live && window !~ markers) {
                  print i ":self_tests_denom:claim=" denom ":live=" live ":" line
                }
              }
            }
          }
        ' "$_r97_latest" 2>/dev/null || true)
        if [[ -n "$_r97_test_violations" ]]; then
          _r97_first_t=$(echo "$_r97_test_violations" | head -3 | tr '\n' '|')
          fail_rule "release_note_numeric_truth" "latest release note $_r97_latest claims N/M self-tests whose denominator disagrees with baseline_metrics.gate_executable_test_cases=$_r97_live_tests: ${_r97_first_t}-- Rule 97 / E135 (denominator-drift sub-check; either update the ratio OR add an 'rc[N] correction'/'rc[N] snapshot' marker within ±3 lines)"
          _r97_fail=1
        fi
      fi
    fi
  fi
fi
if [[ $_r97_fail -eq 0 ]]; then pass_rule "release_note_numeric_truth"; fi

# ---------------------------------------------------------------------------
# Rule 98 — broad_corpus_deleted_module_name_truth (enforcer E137)
#
# Closes rc10 I-ε family: Rule 94 explicitly exempts docs/contracts/openapi-v1.yaml
# ("separate update plan"), all test fixtures ("pinned contract snapshots"), and
# narrowly scans only ARCHITECTURE.md + rule cards + test Javadocs. Deleted-module
# name leaks in ops/helm/**/*.yaml, docs/contracts/openapi-v1.yaml,
# **/module-metadata.yaml description fields survived rc9's prevention wave.
# Rule 98 widens the file-discovery scope using the SAME word-boundary regex
# and ±3-line marker exemption as Rule 94 — closing the Rule 94 implementation
# /kernel-claim gap where the kernel said "every active .md, .yaml, *.java
# file" but the implementation scanned a tiny subset.
# ---------------------------------------------------------------------------
_r98_fail=0
# Rule 98 reuses Rule 94's marker vocabulary (Wave 2 externalisation).
_r98_marker_vocab="gate/active-corpus-name-exemption-markers.txt"
if [[ ! -f "$_r98_marker_vocab" ]]; then
  fail_rule "broad_corpus_deleted_module_name_truth" "$_r98_marker_vocab missing -- Rule 98 / E137 (Wave 2 vocabulary externalisation)"
  _r98_fail=1
fi
_r98_markers="$(grep -vE '^[[:space:]]*(#|$)' "$_r98_marker_vocab" 2>/dev/null | tr '\n' '|' | sed 's/|$//')"
_r98_violations=""
while IFS= read -r _r98_file; do
  [[ -z "$_r98_file" ]] && continue
  # Rule 98 only scans ops/, docs/contracts/, **/module-metadata.yaml; the docs/logs/
  # and docs/archive/ partitions are NEVER reached by the find pipeline below, so no
  # per-file exemption needed beyond build-artefact paths (already excluded at find time).
  case "$_r98_file" in
    docs/archive/*|docs/logs/*) continue ;;
  esac
  _r98_hits=$(awk -v markers="$_r98_markers" '
    BEGIN {
      ap_re  = "(^|[^a-zA-Z0-9_-])agent-platform([^a-zA-Z0-9_-]|$)"
      ar_re  = "(^|[^a-zA-Z0-9_-])agent-runtime([^a-zA-Z0-9_-]|$)"
      arc_re = "(^|[^a-zA-Z0-9_-])agent-runtime-core([^a-zA-Z0-9_-]|$)"
    }
    { lines[NR] = $0 }
    END {
      in_code = 0
      for (i = 1; i <= NR; i++) {
        line = lines[i]
        if (line ~ /^[[:space:]]*```/) { in_code = 1 - in_code; continue }
        if (in_code) continue
        # rc11 widening (rc10 P1-2): YAML comment lines are NOT exempted — sidecar-mem0.yml
        # carried "(port 8001 avoids collision with agent-platform on 8080 / ...)" in a
        # comment that rc10 missed. The marker check below still allows historical-marked
        # comments to pass.
        # rc13 widening (ADR-0088): agent-runtime-core joins the deleted-module set.
        if (line ~ ap_re || (line ~ ar_re && line !~ arc_re) || line ~ arc_re) {
          lo = i - 3; if (lo < 1) lo = 1
          hi = i + 3; if (hi > NR) hi = NR
          window = ""
          for (j = lo; j <= hi; j++) window = window " " lines[j]
          if (window !~ markers) print i ":" line
        }
      }
    }
  ' "$_r98_file" 2>/dev/null || true)
  if [[ -n "$_r98_hits" ]]; then
    while IFS= read -r _r98_hit; do
      _r98_violations="${_r98_violations}${_r98_file}:${_r98_hit}\n"
    done <<< "$_r98_hits"
  fi
done < <(
  # rc10 widening: surfaces Rule 94 explicitly omitted but where deleted-module-name leaks were found.
  # rc11 widening (per ADR-0085): adds ops/**/*.md (operational runbooks) per rc10 post-corrective P1-2.
  {
    find ops -type f \( -name '*.yaml' -o -name '*.yml' -o -name '*.tpl' -o -name '*.md' \) 2>/dev/null | sed 's|^\./||'
    find docs/contracts -maxdepth 1 -type f -name '*.yaml' 2>/dev/null | sed 's|^\./||'
    find . -maxdepth 3 -type f -name 'module-metadata.yaml' -not -path './target/*' -not -path './*/target/*' -not -path './.git/*' -not -path './docs/archive/*' 2>/dev/null | sed 's|^\./||'
  } | sort -u
)
if [[ -n "$_r98_violations" ]]; then
  _r98_first=$(printf '%b' "$_r98_violations" | head -5 | tr '\n' '|')
  fail_rule "broad_corpus_deleted_module_name_truth" "broad corpus contains current-tense pre-Phase-C module name(s) without historical marker (first 5): ${_r98_first}-- Rule 98 / E137 (rc10 I-ε family closure; widens Rule 94 from ARCHITECTURE.md + rule cards + test Javadocs to ops/**, docs/contracts/*.yaml, **/module-metadata.yaml)"
  _r98_fail=1
fi
if [[ $_r98_fail -eq 0 ]]; then pass_rule "broad_corpus_deleted_module_name_truth"; fi

# ---------------------------------------------------------------------------
# Rule 104 — openapi_implemented_route_catalog_truth (enforcer E146)
#
# Closes rc11 review P2-1 (K-ζ family): catalog (http-api-contracts.md +
# contract-catalog.md) marked POST /v1/runs, GET /v1/runs/{id},
# POST /v1/runs/{id}/cancel as `planned;W1` while the OpenAPI spec and
# RunController.java actually ship the routes. Rule 104 cross-checks live
# Controller @-Mappings against catalog stability markers.
# ---------------------------------------------------------------------------
_r104_fail=0
_r104_catalog="docs/contracts/http-api-contracts.md"
_r104_brief="docs/contracts/contract-catalog.md"
_r104_controller_dir="agent-service/src/main/java"
# Cross-check: for each known live route, the catalog row MUST NOT carry `planned`.
_r104_routes=(
  "POST /v1/runs"
  "GET /v1/runs/{id}"
  "POST /v1/runs/{id}/cancel"
)
for _r104_route in "${_r104_routes[@]}"; do
  _r104_path="${_r104_route##* }"
  _r104_method="${_r104_route%% *}"
  # Live presence: any controller file referencing this path-method combo
  _r104_live=0
  if find "$_r104_controller_dir" -type f -name '*.java' 2>/dev/null \
      | xargs grep -lE "(@${_r104_method^}Mapping|@RequestMapping)[^)]*\"[^\"]*${_r104_path//\//\\/}" 2>/dev/null \
      | head -1 | grep -q .; then
    _r104_live=1
  fi
  [[ $_r104_live -eq 0 ]] && continue
  # Live route — catalog row MUST NOT say "planned"
  for _r104_f in "$_r104_catalog" "$_r104_brief"; do
    [[ -f "$_r104_f" ]] || continue
    if grep -qE "${_r104_route}.*\\(planned" "$_r104_f" 2>/dev/null; then
      fail_rule "openapi_implemented_route_catalog_truth" "$_r104_f marks live shipped route '${_r104_route}' as '(planned...)' -- Rule 104 / E146 (rc11 review P2-1 K-ζ closure; live Controller @-Mapping exists)"
      _r104_fail=1
    fi
  done
done
if [[ $_r104_fail -eq 0 ]]; then pass_rule "openapi_implemented_route_catalog_truth"; fi

# Rule 105 — edge_no_direct_compute_link (enforcer E144)
#
# Closes ADR-0089 (Edge-Plane Ingress Gateway Mandate) / Rule R-I sub-clause .b
# at the source-grep level. The bytecode complement (E143
# EdgeToComputeDirectLinkArchTest) catches violations at compile/test time;
# this rule catches them at the corpus level so a stray .java file shows up
# in gate output even before the ArchUnit test runs.
#
# Scope:
#   For every <module>/module-metadata.yaml whose `deployment_plane:` is `edge`,
#   scan that module's src/main/java tree for:
#     (a) `^import ascend\.springai\.(service|engine|middleware)\.` lines, OR
#     (b) `new RestTemplate` or `WebClient\.builder` construction targeting a
#         host that isn't the bus ingress endpoint (heuristic: any bare base-URL
#         literal that doesn't contain `bus` is forbidden at W1).
#
# At W1 agent-client is skeleton (no production java) so this rule is
# vacuous-but-armed. When the W3+ SDK lands, the rule starts gating PRs.
# ---------------------------------------------------------------------------
_r105_fail=0
while IFS= read -r _r105_meta; do
  _r105_module_dir="$(dirname "$_r105_meta")"
  _r105_main_java="$_r105_module_dir/src/main/java"
  [[ -d "$_r105_main_java" ]] || continue
  # (a) forbidden compute_control imports
  _r105_violations=$(grep -rnE '^import ascend\.springai\.(service|engine|middleware)\.' "$_r105_main_java" 2>/dev/null || true)
  if [[ -n "$_r105_violations" ]]; then
    while IFS= read -r _r105_line; do
      fail_rule "edge_no_direct_compute_link" "$_r105_line — edge plane module must not import compute_control plane production class; route via com.huawei.ascend.bus.spi.ingress.IngressGateway per Rule R-I sub-clause .b / ADR-0089"
      _r105_fail=1
    done <<< "$_r105_violations"
  fi
  # (b) RestTemplate / WebClient direct construction
  _r105_rest=$(grep -rnE 'new[[:space:]]+RestTemplate\(|WebClient\.builder\(' "$_r105_main_java" 2>/dev/null || true)
  if [[ -n "$_r105_rest" ]]; then
    while IFS= read -r _r105_line; do
      fail_rule "edge_no_direct_compute_link" "$_r105_line — edge plane module must not construct direct HTTP clients; route via com.huawei.ascend.bus.spi.ingress.IngressGateway per Rule R-I sub-clause .b / ADR-0089"
      _r105_fail=1
    done <<< "$_r105_rest"
  fi
done < <(grep -lE '^deployment_plane:[[:space:]]*edge' */module-metadata.yaml 2>/dev/null)
if [[ $_r105_fail -eq 0 ]]; then pass_rule "edge_no_direct_compute_link"; fi

# ---------------------------------------------------------------------------
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
# Rule 111 — architecture_refresh_defect_family_re_eval_required (enforcers E156 E157 E158) [META]
#
# Operationalises Rule G-9 (Recurring-Defect Family Truth). Per ADR-0095
# rc18 Wave 1, the 3 sub-checks delegate to shared helpers in
# gate/lib/check_recurring_families.sh — closes F-kernel-vs-implementation-
# drift on Rule 111 itself (Wave 1 finding: fixtures and gate both invoke
# the same code, no inline re-implementation).
#
# Hardening fixes (per ADR-0095):
#   1a — yaml's own git commit date drives freshness (not hand-edited last_updated)
#   1b — families: [] is rejected (hard non-empty assertion)
#   1c — cleanup_status enum value validated against {closed | structurally_addressed |
#        partial | incomplete | monitoring}
#   1d — per-family block-bucket: each family has every required field exactly once
#        (closes duplicate-field compensation blind spot)
#   1e — last_updated must be ISO YYYY-MM-DD format
#   1f — md parity anchored to ^### F- H3 headings (mirrors yaml ^  - id: anchoring,
#        closes prose false-positives)
#   1g — refresh-signal path filter INCLUDES docs/governance/rules/
#   1h — shallow-clone fail-closed (was silent pass)
#
# Sub-checks:
#   .a (E156) — yaml well-formedness (file + top-level keys + ISO date +
#               non-empty + per-family field count + enum validation)
#   .b (E157) — freshness via yaml file's own git commit date vs latest
#               refresh-signal commit date
#   .c (E158) — yaml/md family-id parity, both sides H3/structural-anchored
#
# Per ADR-0094 (rc17 introduction) + ADR-0095 (rc18 Wave 1 hardening).
#
# scope_surfaces: docs/governance/recurring-defect-families.yaml, docs/governance/recurring-defect-families.md, docs/adr/, docs/logs/releases/, CLAUDE.md, docs/governance/architecture-status.yaml, docs/governance/rules/, gate/lib/check_recurring_families.sh
# ---------------------------------------------------------------------------
_r111_yaml="docs/governance/recurring-defect-families.yaml"
_r111_md="docs/governance/recurring-defect-families.md"
_r111_helper="gate/lib/check_recurring_families.sh"
_r111_fail=0

if [[ ! -f "$_r111_helper" ]]; then
  fail_rule "architecture_refresh_defect_family_re_eval_required" "$_r111_helper missing -- Rule G-9 / ADR-0095 Wave 1 helper file required"
else
  # Source helpers once; capture each sub-check's stdout for fail_rule emission.
  # shellcheck disable=SC1090
  source "$_r111_helper"  # source gate/lib/check_recurring_families.sh — Rule 112 [META] self-application marker

  # Sub-check .a — yaml well-formedness (covers fixes 1b, 1c, 1d, 1e)
  _r111_a_output=$(_check_recurring_families_yaml_wellformed "$_r111_yaml")
  if [[ -n "$_r111_a_output" ]]; then
    while IFS= read -r _r111_line; do
      [[ -z "$_r111_line" ]] && continue
      fail_rule "architecture_refresh_defect_family_re_eval_required" "$_r111_line"
      _r111_fail=1
    done <<< "$_r111_a_output"
  fi

  # Sub-check .b — freshness (covers fixes 1a, 1g, 1h)
  _r111_b_output=$(_check_recurring_families_freshness "$_r111_yaml" ".")
  if [[ -n "$_r111_b_output" ]]; then
    # Knowledge/governance rebalancing G-track: sub-clause .b (content-diff
    # freshness) demoted from blocking to advisory. Forcing recurring-defect-
    # families.yaml to be co-bumped in every commit that touches a signal surface
    # is brittle merge-train coupling, not a delivery invariant. Well-formedness
    # (.a) and md/yaml parity (.c) stay blocking.
    while IFS= read -r _r111_line; do
      [[ -z "$_r111_line" ]] && continue
      echo "ADVISORY: architecture_refresh_defect_family freshness (.b) -- $_r111_line -- Rule G-9.b demoted to advisory (rebalancing G-track)"
    done <<< "$_r111_b_output"
  fi

  # Sub-check .c — md/yaml parity (covers fix 1f)
  _r111_c_output=$(_check_recurring_families_md_yaml_parity "$_r111_yaml" "$_r111_md")
  if [[ -n "$_r111_c_output" ]]; then
    while IFS= read -r _r111_line; do
      [[ -z "$_r111_line" ]] && continue
      fail_rule "architecture_refresh_defect_family_re_eval_required" "$_r111_line"
      _r111_fail=1
    done <<< "$_r111_c_output"
  fi
fi

if [[ $_r111_fail -eq 0 ]]; then pass_rule "architecture_refresh_defect_family_re_eval_required"; fi

# ---------------------------------------------------------------------------
# Rule 115 — no_version_log_metadata_in_code (enforcer E163)
#
# Operationalises Rule D-9. Grep across non-exempt production-code surfaces
# for forbidden version/log metadata tokens:
#   - `rc<N> Wave <M>` style tags
#   - narrative `per ADR-NNNN` change-history pointers
#   - `Finding F<N>` or `(F<N>)` references
#   - `closes #<N>` / `addresses #<N>` ticket references
#
# Scope (production code only — exempt surfaces are governance docs):
#   *.java, *.py, *.sh, *.bash, *.kt, *.ts, *.tsx,
#   application*.yml/yaml, Dockerfile, .github/workflows/*.yml
#
# Exempt-by-path: docs/adr/, docs/logs/, docs/governance/rules/*.md,
#   docs/governance/rule-history.md, docs/governance/recurring-defect-families.*,
#   docs/governance/architecture-status.yaml, CHANGELOG.md, CLAUDE.md, gate/lib/,
#   gate/test_*.sh (test fixtures may construct synthetic version-tagged inputs).
# ---------------------------------------------------------------------------
_r115_fail=0
_r115_pattern='\brc[0-9]+ Wave [0-9]+\b|\bper ADR-[0-9]{4}\b|\(F[0-9]+\)|\bFinding F[0-9]+\b|\b(closes|addresses) #[0-9]+\b'
# Grandfather list: pre-existing files with violations at the time Rule D-9
# landed; tracked with sunset_date in gate/d9-grandfathered-files.txt so
# forward motion is required. Listed files are exempted from the gate.
_r115_grandfather_file="gate/d9-grandfathered-files.txt"
_r115_grandfathered=""
if [[ -f "$_r115_grandfather_file" ]]; then
  _r115_grandfathered=$(grep -vE '^[[:space:]]*#|^[[:space:]]*$' "$_r115_grandfather_file" 2>/dev/null | sort -u)
fi
_r115_files=$(find . \
  -path ./target -prune -o \
  -path ./node_modules -prune -o \
  -path ./.git -prune -o \
  -path ./docs/adr -prune -o \
  -path ./docs/logs -prune -o \
  -path ./docs/governance/rules -prune -o \
  -path ./docs/governance/principles -prune -o \
  -path ./gate/lib -prune -o \
  -type f \( \
       -name '*.java' \
    -o -name '*.py' \
    -o -name '*.sh' \
    -o -name '*.bash' \
    -o -name '*.kt' \
    -o -name '*.ts' \
    -o -name '*.tsx' \
    -o -name 'application*.yml' \
    -o -name 'application*.yaml' \
    -o -name 'Dockerfile' \
  \) -print 2>/dev/null \
  | grep -vE '^\./CLAUDE\.md$|^\./CHANGELOG\.md$|^\./docs/governance/architecture-status\.yaml$|^\./docs/governance/enforcers\.yaml$|^\./docs/governance/principle-coverage\.yaml$|^\./docs/governance/architecture-graph\.yaml$|^\./docs/governance/rule-history\.md$|^\./docs/governance/recurring-defect-families\.|^\./gate/test_architecture_sync_gate\.sh$' \
  || true)
# Also include .github/workflows/*.yml (separate find because of leading dot)
_r115_files="$_r115_files"$'\n'"$(find ./.github/workflows -type f -name '*.yml' 2>/dev/null || true)"
_r115_hits=""
# Perf fix (2026-05-22): the original implementation iterated the ~5391 file
# list and forked grep ONCE per file. On WSL with the repo on Windows /mnt/d/,
# each fork crosses the WSL↔Windows boundary (~44 ms each) — total ~4 minutes,
# hitting the 300s gate safety net. Replaced with a single bulk grep call via
# xargs (~0.7 s, ~320× faster). Grandfather filter is applied to the file list
# BEFORE the bulk grep so the semantics are unchanged.
_r115_hits=""
_r115_filtered_files="$_r115_files"
if [[ -n "$_r115_grandfathered" ]]; then
  # Strip leading "./" so grep -vxFf can match against grandfather paths
  # (which are relative without ./), then restore.
  _r115_filtered_files=$(printf '%s\n' "$_r115_files" \
    | sed 's|^\./||' \
    | grep -vxFf <(printf '%s\n' "$_r115_grandfathered") 2>/dev/null \
    | sed 's|^|./|')
fi
if [[ -n "$_r115_filtered_files" ]]; then
  # -H forces filename prefix even when only one file matches (rare but covered).
  _r115_hits=$(printf '%s\n' "$_r115_filtered_files" \
    | grep -v '^$' \
    | xargs -d '\n' -r grep -HnE "$_r115_pattern" 2>/dev/null || true)
fi
if [[ -n "$_r115_hits" ]]; then
  _r115_first=$(echo "$_r115_hits" | grep -v '^$' | head -5 | tr '\n' '|')
  fail_rule "no_version_log_metadata_in_code" "production code contains forbidden version/log metadata tokens (rc<N> Wave / narrative per ADR-NNNN / Finding F<N> / closes #<N>); first hits: ${_r115_first}-- Rule D-9 / E163 (change-history metadata belongs in commit messages, ADRs, release notes, rule cards, or rule-history.md — not implementation)"
  _r115_fail=1
fi
if [[ $_r115_fail -eq 0 ]]; then pass_rule "no_version_log_metadata_in_code"; fi

# ---------------------------------------------------------------------------
# Rule 125 — codegraph_install_truth (enforcer E173)
#
# Operationalises Rule R-A's developer-self-service clause for the
# project-local codegraph MCP tool under tools/codegraph/. Verifies the
# pinning surfaces a fresh contributor needs to reproduce the install:
#   (a) tools/codegraph/package.json declares @colbymchenry/codegraph at an
#       EXACT pin (X.Y.Z form, no ^/~/>=/<= prefix).
#   (b) tools/codegraph/package-lock.json exists with lockfileVersion >= 3
#       (older formats omit integrity hashes for optionalDependencies, so
#       per-platform bundles can drift silently between contributors).
#   (c) .mcp.json registers an mcpServers.codegraph entry whose args list a
#       relative shim path under
#       tools/codegraph/node_modules/@colbymchenry/codegraph/<file>
#       (cross-platform, no contributor PATH dependency).
# Does NOT require node_modules/ to be materialised; CI without `npm ci`
# still passes. This rule guards the pinning truth, not the install state.
#
# scope_surfaces: tools/codegraph/package.json, tools/codegraph/package-lock.json, .mcp.json
# ---------------------------------------------------------------------------
_r125_fail=0
_r125_pkg="tools/codegraph/package.json"
_r125_lock="tools/codegraph/package-lock.json"
_r125_mcp=".mcp.json"

if [[ ! -f "$_r125_pkg" ]]; then
  fail_rule "codegraph_install_truth" "$_r125_pkg missing -- contributor onboarding broken; restore the pinned manifest under tools/codegraph -- Rule R-A / E173"
  _r125_fail=1
elif ! grep -qE '"@colbymchenry/codegraph":[[:space:]]*"[0-9]+\.[0-9]+\.[0-9]+"' "$_r125_pkg"; then
  fail_rule "codegraph_install_truth" "$_r125_pkg: @colbymchenry/codegraph must be exact-pinned (X.Y.Z form, no ^/~/>=/<= prefix) -- Rule R-A / E173"
  _r125_fail=1
fi

if [[ ! -f "$_r125_lock" ]]; then
  fail_rule "codegraph_install_truth" "$_r125_lock missing -- run \`cd tools/codegraph && npm install\` to regenerate the lockfile -- Rule R-A / E173"
  _r125_fail=1
elif ! grep -qE '"lockfileVersion":[[:space:]]*[3-9][0-9]*' "$_r125_lock"; then
  fail_rule "codegraph_install_truth" "$_r125_lock: lockfileVersion must be >= 3 (older formats omit integrity hashes for optionalDependencies) -- Rule R-A / E173"
  _r125_fail=1
fi

if [[ ! -f "$_r125_mcp" ]]; then
  fail_rule "codegraph_install_truth" "$_r125_mcp missing -- project-scope MCP wiring absent; Claude Code contributors cannot load codegraph without it -- Rule R-A / E173"
  _r125_fail=1
elif ! grep -q '"codegraph"' "$_r125_mcp"; then
  fail_rule "codegraph_install_truth" "$_r125_mcp: no mcpServers.codegraph entry registered -- Rule R-A / E173"
  _r125_fail=1
elif ! grep -qE '"tools/codegraph/node_modules/@colbymchenry/codegraph/[^"]+"' "$_r125_mcp"; then
  fail_rule "codegraph_install_truth" "$_r125_mcp: codegraph server args must reference a relative path under tools/codegraph/node_modules/@colbymchenry/codegraph/ (cross-platform, no PATH dependency) -- Rule R-A / E173"
  _r125_fail=1
fi

[[ $_r125_fail -eq 0 ]] && pass_rule "codegraph_install_truth"

# ---------------------------------------------------------------------------
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
# Rule 128 — model_gateway_authority_truth (enforcer E176)
#
# ADR-0121, Java code, and the contract catalog must agree on ModelGateway's
# package and synchronous SPI signature.
#
# scope_surfaces: docs/adr/0121-model-gateway-spi.yaml,
#                 agent-middleware/src/main/java/com/huawei/ascend/middleware/model/spi/ModelGateway.java,
#                 docs/contracts/contract-catalog.md
# ---------------------------------------------------------------------------
_r128_out=$(python3 gate/lib/check_model_gateway_authority_truth.py --root . 2>&1)
_r128_rc=$?
if [[ $_r128_rc -ne 0 ]]; then
  fail_rule "model_gateway_authority_truth" "${_r128_out:-ModelGateway authority surfaces disagree} -- Rule G-8 / E176"
else
  pass_rule "model_gateway_authority_truth"
fi

# ---------------------------------------------------------------------------
# Rule 129 — contract_spi_count_truth (enforcer E177)
#
# Contract-catalog active SPI totals, module totals, and the latest release
# note's Active SPI total must agree. Promoted SPIs must not remain listed
# as deferred design names. Agent/advisor composition claims must also be
# backed by AgentDefinition fields, typed advisor carriers, and the shared
# advisor/model hook sequence.
#
# scope_surfaces: docs/contracts/contract-catalog.md,
#                 docs/logs/releases/*.md,
#                 docs/contracts/chat-advisor.v1.yaml,
#                 docs/contracts/agent-definition.v1.yaml,
#                 docs/contracts/model-streaming.v1.yaml,
#                 agent-service/src/main/java/.../AgentDefinition.java
# ---------------------------------------------------------------------------
_r129_out=$(python3 gate/lib/check_contract_spi_count_truth.py --root . 2>&1)
_r129_rc=$?
if [[ $_r129_rc -ne 0 ]]; then
  fail_rule "contract_spi_count_truth" "${_r129_out:-contract SPI count truth check failed} -- Rule G-8 / E177"
else
  pass_rule "contract_spi_count_truth"
fi

# Rule 130 — feature_lifecycle_validity (enforcer E178, kernel Rule G-14)
#
# Authority: ADR-0151 (L1 Feature Registry canonical schema, W1) +
#            ADR-0153 (L1 Feature Registry closure, W5).
#
# Sub-clause .a — every SAA Feature element in features.dsl declares
#   saa.status ∈ the 9-state lifecycle set. Implemented at W5
#   (advisory→blocking flip). Sub-clauses .b/.c/.d (git-history
#   transition validity, shipped-requires-verification,
#   deprecated-requires-sunset) remain advisory through W5 and ship
#   blocking in a follow-up sub-wave.
# ---------------------------------------------------------------------------
_r130_fail=0
_r130_dsl="architecture/features/features.dsl"
_r130_valid_states="proposed accepted design_only ready_for_impl implemented_unverified test_verified shipped deprecated removed"
if [[ ! -f "$_r130_dsl" ]]; then
  fail_rule "feature_lifecycle_validity" "$_r130_dsl missing -- Rule G-14.a / E178"
  _r130_fail=1
else
  # Walk every "saa.status" "X" property and check X ∈ allowed set.
  while IFS= read -r _r130_status; do
    _r130_status=$(echo "$_r130_status" | tr -d '\r')
    if [[ -z "$_r130_status" ]]; then continue; fi
    _r130_match=0
    for _s in $_r130_valid_states; do
      if [[ "$_r130_status" == "$_s" ]]; then _r130_match=1; break; fi
    done
    if [[ $_r130_match -eq 0 ]]; then
      fail_rule "feature_lifecycle_validity" "$_r130_dsl declares saa.status \"$_r130_status\" which is not in the 9-state lifecycle (proposed/accepted/design_only/ready_for_impl/implemented_unverified/test_verified/shipped/deprecated/removed) -- Rule G-14.a / E178"
      _r130_fail=1
    fi
  done < <(grep -oE '"saa\.status"[[:space:]]+"[^"]+"' "$_r130_dsl" | sed -E 's/.*"saa\.status"[[:space:]]+"([^"]+)".*/\1/')
fi
if [[ $_r130_fail -eq 0 ]]; then
  pass_rule "feature_lifecycle_validity"
fi

# ---------------------------------------------------------------------------
# Rule 131 — fact_layer_integrity (enforcer E179, kernel Rule G-15)
#
# Authority: ADR-0154 (Fact-Layer Authority, Wave 1).
#
# Sub-clause .a — architecture/facts/{README.md, schema/fact.schema.yaml,
#   generated/} and architecture/profile/saa-property-authority.yaml MUST
#   exist; the YAML surfaces MUST parse. ADVISORY at W1 (no fail-closed,
#   just logged); BLOCKING from W2.
#
# Sub-clauses .b (provenance fields), .c (byte-identical regen + LLM-
# no-author banner), .d (FunctionPoint hard-evidence fields) activate in
# Waves 2, 4, and 5-6 respectively. The single python driver
# gate/lib/check_fact_layer_integrity.py accepts --enforce a,b,c,d to
# select sub-clauses; today only 'a' is enforced.
# ---------------------------------------------------------------------------
_r131_fail=0
_r131_facts_dir="architecture/facts"

if [[ ! -d "$_r131_facts_dir" ]]; then
  fail_rule "fact_layer_integrity" "$_r131_facts_dir missing -- Rule G-15.a requires the fact directory structure; land it from architecture/facts/README.md scaffolding -- Rule G-15 / E179"
  _r131_fail=1
else
  # Round-4 Wave Alpha (2026-05-28 fourth-correction R3 redesign):
  # the bash gate enforces sub-clauses .a (structural existence), .b
  # (provenance/schema validation), .c.structural (banner present —
  # part of .c that doesn't need compiled classes), and .d (FunctionPoint
  # resolver). Sub-clause .c.bytes (byte-identity to extractor
  # re-emission) moved to Maven Surefire test `FactLayerByteIdentityIT`.
  # The Python checker's --enforce 'c' covers the structural banner
  # check that doesn't need target/classes; the byte-diff lives in
  # Maven where target/classes is guaranteed by lifecycle.
  _r131_out=$(python3 gate/lib/check_fact_layer_integrity.py --enforce a,b,c,d 2>&1)
  _r131_rc=$?
  if [[ $_r131_rc -ne 0 ]]; then
    _r131_first=$(printf '%s' "$_r131_out" | head -1)
    fail_rule "fact_layer_integrity" "${_r131_first:-rc=$_r131_rc} -- Rule G-15.a/b/c.structural/d / E179 (sub-clause .c.bytes is enforced by Maven test FactLayerByteIdentityIT)"
    _r131_fail=1
  fi
fi

# Round-4 Wave Alpha (2026-05-28 fourth-correction R3 redesign): the
# byte-identity-to-extractor-re-emission contract (sub-clause .c.bytes)
# moved out of the bash gate and into a Maven Surefire test
# `FactLayerByteIdentityIT` under tools/architecture-workspace, where
# `target/classes` is guaranteed by Maven's compile-phase ordering.
# The bash gate retains structural / provenance / resolver checks
# (sub-clauses .a + .b + .c.structural + .d) which do not require
# compiled classes. This eliminates the precondition-gymnastics that
# bred three rounds of fail-open mechanisms (`|| true`, advisory-skip,
# env-var-opt-in) — there is no longer a "is target/classes present?"
# branch in the bash Rule 131 to be fail-open under.

[[ $_r131_fail -eq 0 ]] && pass_rule "fact_layer_integrity"

# ---------------------------------------------------------------------------
# Rule 132 — feature_catalog_render_idempotency (enforcer E180, kernel Rule G-13 sibling)
#
# Authority: ADR-0154 (Fact-Layer Authority) + Round-4 second-correction
# request R2 (2026-05-28). Wires gate/lib/render_features_catalog.py
# --check into the canonical gate so feature-catalog drift fails closed.
# Round-1 declared "rendered L1 feature catalogs" as an in-scope drift
# surface; Rounds 1-3 verified the detector existed but never invoked
# it from the canonical sync gate (sibling of Rule G-13.b for templated
# Markdown). This rule closes that gate-coverage gap.
# ---------------------------------------------------------------------------
_r132_fail=0
_r132_render_script="gate/lib/render_features_catalog.py"
if [[ ! -f "$_r132_render_script" ]]; then
  fail_rule "feature_catalog_render_idempotency" "$_r132_render_script missing -- Rule G-13 sibling / E180"
  _r132_fail=1
else
  _r132_out=$(python3 "$_r132_render_script" --check 2>&1)
  _r132_rc=$?
  if [[ $_r132_rc -ne 0 ]]; then
    _r132_first=$(printf '%s' "$_r132_out" | grep "^DRIFT:" | head -1)
    fail_rule "feature_catalog_render_idempotency" "feature catalog drift: ${_r132_first:-rc=$_r132_rc} -- Rule G-13 sibling / E180"
    _r132_fail=1
  fi
fi
[[ $_r132_fail -eq 0 ]] && pass_rule "feature_catalog_render_idempotency"

# ---------------------------------------------------------------------------
# Rule 133 — productclaim_referential_integrity (enforcer E181, kernel Rule G-16)
#
# Phase A Wave 5 (advisory at landing 2026-05-28; promotes to blocking when
# placeholder count reaches 0 per Rule G-21). Every product_claim: value in
# ADR YAML, rule card frontmatter, enforcer rows, SAA feature saa.productClaim,
# or contract frontmatter MUST resolve to a PC-NNN id declared in
# product/claims.yaml -- OR carry one of the explicit sentinel values
# governance_infra:true or product_claim_placeholder:true. Bare missing field
# is checked by Rule 134 (no_orphan_artefacts), not this rule.
#
# scope_surfaces: product/claims.yaml, docs/governance/rules/*.md (frontmatter),
# docs/governance/enforcers.yaml, architecture/features/features.dsl,
# architecture/decisions/*.yaml, docs/contracts/*.yaml
# ---------------------------------------------------------------------------
_r133_fail=0
_r133_claims="product/claims.yaml"
if [[ ! -f "$_r133_claims" ]]; then
  : # vacuous pass before product authority lands
else
  _r133_valid_ids=$(grep -oE '^  - id: PC-[0-9]+' "$_r133_claims" 2>/dev/null | awk '{print $3}')
  _r133_bad=$(grep -rhEn '^\s*product_claim:\s*"?(PC-[0-9]+(\|PC-[0-9]+)*)"?\s*$|^\s+"saa\.productClaim"\s+"(PC-[0-9]+(\|PC-[0-9]+)*)"\s*$' \
              docs/governance/rules/ architecture/decisions/ docs/contracts/ architecture/features/ 2>/dev/null \
              | grep -oE 'PC-[0-9]+' | sort -u | while read _r133_ref; do
      if ! echo "$_r133_valid_ids" | grep -qxF "$_r133_ref"; then
        echo "$_r133_ref"
      fi
    done | head -3 | tr '\n' ' ')
  if [[ -n "$_r133_bad" ]]; then
    fail_rule "productclaim_referential_integrity" "product_claim references that don't resolve in product/claims.yaml: $_r133_bad -- Rule G-16 / E181 (advisory at W5 landing; blocking when placeholder count reaches 0)"
    _r133_fail=1
  fi
fi
[[ $_r133_fail -eq 0 ]] && pass_rule "productclaim_referential_integrity"

# ---------------------------------------------------------------------------
# Rule 134 — no_orphan_artefacts (enforcer E182, kernel Rule G-17)
#
# Phase A Wave 5 (advisory at landing 2026-05-28). Every ADR YAML / rule card /
# enforcer / SAA Feature / contract MUST declare one of: (a) product_claim: with
# a PC-NNN value, (b) governance_infra: true, (c) product_claim_placeholder: true
# (Wave 4 backfill marker). Missing all three = orphan. Counts orphans and emits
# info; doesn't fail unless orphan count exceeds the per-corpus advisory
# threshold (currently 100% -- vacuous-PASS until Wave 4 backfill brings the
# threshold down).
# ---------------------------------------------------------------------------
_r134_fail=0
# BLOCKING from Phase B convergence (2026-05-28, placeholder count reached 0).
# Every ADR yaml / contract yaml / rule card / principle card MUST carry one of
# product_claim: / governance_infra: / product_claim_placeholder: markers.
_r134_orphans=""
for _r134_f in docs/adr/*.yaml architecture/decisions/*.yaml docs/contracts/*.yaml docs/governance/rules/rule-*.md docs/governance/principles/P-*.md; do
  [[ -f "$_r134_f" ]] || continue
  if ! grep -qE '^[[:space:]]*(product_claim|governance_infra|product_claim_placeholder):' "$_r134_f"; then
    _r134_orphans="${_r134_orphans}$(basename "$_r134_f") "
  fi
done
if [[ -n "$_r134_orphans" ]]; then
  fail_rule "no_orphan_artefacts" "artefacts without a ProductClaim marker (orphans): ${_r134_orphans}-- Rule G-17 / E182 (blocking from Phase B convergence)"
  _r134_fail=1
fi
[[ $_r134_fail -eq 0 ]] && pass_rule "no_orphan_artefacts"

# ---------------------------------------------------------------------------
# Rule 135 — traceability_chain_completeness (enforcer E183, kernel Rule G-18)
#
# Phase A Wave 5 (advisory at landing). Every PC-NNN in product/claims.yaml MUST
# have >=1 SAA Feature referencing it via saa.productClaim. Vacuously passes
# until Wave 4 backfill threads the chain across the corpus.
# ---------------------------------------------------------------------------
_r135_fail=0
# BLOCKING from Phase B convergence: every PC-NNN declared in product/claims.yaml
# MUST be referenced by >=1 artefact (feature / rule / contract / ADR).
_r135_claims="product/claims.yaml"
if [[ -f "$_r135_claims" ]]; then
  for _r135_pc in $(grep -oE '^[[:space:]]*-?[[:space:]]*id:[[:space:]]*PC-[0-9]+' "$_r135_claims" | grep -oE 'PC-[0-9]+' | sort -u); do
    if ! grep -rqE "${_r135_pc}([^0-9]|$)" docs/contracts docs/governance/rules architecture/features docs/adr docs/governance/principles 2>/dev/null; then
      fail_rule "traceability_chain_completeness" "${_r135_pc} declared in product/claims.yaml but referenced by zero artefacts -- Rule G-18 / E183 (blocking from Phase B convergence)"
      _r135_fail=1
    fi
  done
fi
[[ $_r135_fail -eq 0 ]] && pass_rule "traceability_chain_completeness"

# ---------------------------------------------------------------------------
# Rule 140 — shipped_frame_anchor_integrity (enforcer E188, kernel Rule G-23)
#
# Authority: ADR-0157 (EngineeringFrame Ontology) + ADR-0158. Closes external
# review F8.3: every SAA EngineeringFrame with saa.status "shipped" MUST anchor
# >=1 FunctionPoint (an anchors edge in engineering-frames.dsl), else the
# shipped status is a structural lie. Frame elements live in BOTH
# engineering-frames.dsl and features.dsl; the anchors edges live in
# engineering-frames.dsl. ADR-backed exceptions are listed in
# gate/frame-shipped-zero-anchor-allowlist.txt (ships empty).
#
# scope_surfaces: architecture/features/engineering-frames.dsl, architecture/features/features.dsl, gate/frame-shipped-zero-anchor-allowlist.txt, gate/lib/check_frame_shipped_anchors.py
# ---------------------------------------------------------------------------
_r140_fail=0
_r140_helper="gate/lib/check_frame_shipped_anchors.py"
if [[ ! -f "$_r140_helper" ]]; then
  fail_rule "shipped_frame_anchor_integrity" "$_r140_helper missing -- Rule G-23 / E188"
  _r140_fail=1
elif [[ -z "$GATE_PYTHON_BIN" ]]; then
  : # vacuous pass on hosts without python (Rule G-7 lists WSL as canonical env)
else
  _r140_out=$("$GATE_PYTHON_BIN" "$_r140_helper" 2>&1)
  _r140_rc=$?
  if [[ $_r140_rc -ne 0 ]]; then
    _r140_first=$(printf '%s' "$_r140_out" | grep -E '^(MISSING-ANCHOR|MISSING-FILE):' | head -1)
    fail_rule "shipped_frame_anchor_integrity" "shipped EngineeringFrame anchors no FunctionPoint: ${_r140_first:-rc=$_r140_rc} -- Rule G-23 / E188"
    _r140_fail=1
  fi
fi
[[ $_r140_fail -eq 0 ]] && pass_rule "shipped_frame_anchor_integrity"

# === END OF RULES ===
# ---------------------------------------------------------------------------

# Wave 5 authority transfer (ADR-0147): after the rule list runs, invoke the
# workspace check. In BLOCKING mode it fails closed on profile violations or
# generated-zone drift. Listed AFTER the rule loop so the structural-rule
# verdict above is preserved if the workspace tooling is temporarily
# unavailable (e.g. on a host without Java 21 + Maven wrapper).
WORKSPACE_GATE="$(dirname "${BASH_SOURCE[0]}")/check_architecture_workspace.sh"
if [[ -x "$WORKSPACE_GATE" ]]; then
  echo "---"
  echo "Running architecture workspace gate (ADR-0147 W5+)..."
  if ! bash "$WORKSPACE_GATE"; then
    echo "GATE: FAIL (workspace gate)"
    exit 1
  fi
fi

if [[ $fail_count -eq 0 ]]; then
  echo "GATE: PASS"
  exit 0
else
  echo "GATE: FAIL"
  exit 1
fi
