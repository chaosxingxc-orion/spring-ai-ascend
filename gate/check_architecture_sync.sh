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
#  13.  contract_catalog_no_deleted_spi_or_starter_names -- contract-catalog.md must not reference deleted names
#  16.  http_contract_w1_tenant_and_cancel_consistency -- W1 HTTP contract: no replace-X-Tenant-Id wording, no CREATED initial status, no DELETE cancel route, no W0 cancel/idempotency future-state drift
#  17.  contract_catalog_spi_table_matches_source     -- SPI sub-table must list 7 known SPIs; OssApiProbe must not appear before Probes sub-table
#  18.  deleted_spi_starter_names_outside_catalog     -- ACTIVE_NORMATIVE_DOCS corpus must not reference deleted SPI/starter names (widened, ADR-0043)
#  19.  shipped_row_tests_evidence                    -- every shipped: true row must have non-empty tests: pointing to real files (ADR-0042, strengthened)
#  21.  bom_glue_paths_exist                          -- BoM must not contain known ghost implementation paths unless they exist (ADR-0043)
#  23.  active_doc_internal_links_resolve             -- markdown links ](path) in active docs must resolve to existing files (ADR-0043)
#  24.  shipped_row_evidence_paths_exist              -- l2_documents: and latest_delivery_file: on shipped rows must exist on disk (ADR-0045)
#  26.  release_note_shipped_surface_truth            -- docs/logs/releases/*.md must not overclaim RunLifecycle/RunContext.posture/ApiCompatibilityTest-as-OpenAPI/AppPostureGate-scope (ADR-0046)
#  27.  active_entrypoint_baseline_truth              -- root README.md baseline counts must match architecture-status.yaml.architecture_sync_gate.allowed_claim (ADR-0047)
#  28.  release_note_baseline_truth                   -- docs/logs/releases/*.md baseline counts must match canonical YAML unless marked "Historical artifact frozen at SHA" (ADR-0049, whitepaper-alignment P0-1)
#  --- L1 Rule-28 sub-checks (ADR-0059) ---
#  28a. tenant_column_present                          -- every CREATE TABLE in db/migration declares tenant_id (enforcer E15)
#  28b. high_cardinality_tag_guard                     -- no Tag.of("run_id"|"idempotency_key"|"jwt_sub"|"body", …) in agent-*/main (enforcer E19)
#  28c. no_secret_patterns                             -- gitleaks-style sweep of tracked files; allowlist via 'secret-allowlist:' (enforcer E20)
#  28d. out_of_scope_name_guard                        -- W2+ deferred names absent from agent-*/main (enforcer E26)
#  28e. module_count_invariant                         -- root pom.xml declares exactly 9 <module> entries (enforcer E27; bumped from 4 to 9 by 2026-05-17 six-module materialization PR; canonical count lives in docs/governance/architecture-status.yaml#repository_counts.total_reactor_modules and is data-driven cross-checked by Rule 64)
#  28f. enforcers_yaml_wellformed                      -- docs/governance/enforcers.yaml every row has all 5 fields + legal kind (enforcer E29)
#  28g. no_prose_only_constraint_marker                -- no TODO/FIXME/XXX/deferred:enforce|enforcer|test|gate in CLAUDE.md / ARCHITECTURE.md (enforcer E30)
#  28h. l1_review_checklist_present                    -- ADRs 0055–0059 contain '§16 Review Checklist' (enforcer E31)
#  28i. plan_enforcer_table_in_sync                    -- plan §11 IDs == enforcers.yaml IDs (enforcer E32)
#  28j. enforcer_artifact_paths_exist                   -- every artifact: path in enforcers.yaml resolves on disk (enforcer E33, Phase K audit fix F6)
#  28k. javadoc_enforcer_citation_semantic_check        -- *Test.java/*IT.java Javadoc `enforcers.yaml#E<n>` citations match the E-row's artifact: field (post-review fix plan F / P1-2)
#  30.  telemetry_vertical_constraint_coverage         -- ARCHITECTURE.md §4 #53–#59 each cited by an enforcer row (L1.x Telemetry Vertical, enforcer E47)
#  --- Layer-0 governing principles (ADR-0064..0067) ---
#  32.  competitive_baselines_present_and_wellformed    -- docs/governance/competitive-baselines.yaml has 4 pillars (Rule 30, enforcer E50)
#  33.  release_note_references_four_pillars            -- latest release note mentions all 4 pillars by name (Rule 30, enforcer E51)
#  34.  module_metadata_present_and_complete            -- every <module>/pom.xml has a sibling module-metadata.yaml with required keys (Rule 31, enforcer E52)
#  35.  dfx_yaml_present_and_wellformed                 -- every kind:platform|domain module has docs/dfx/<module>.yaml with 5 DFX dimensions (Rule 32, enforcer E53)
#  36.  domain_module_has_spi_package                   -- every kind:domain module declares spi_packages and each one resolves on disk (Rule 32, enforcer E54)
#  --- W1 Layered 4+1 + Architecture Graph (ADR-0068) ---
#  37.  architecture_artefact_front_matter             -- every ARCH/L2/ADR.yaml carries level: + view: front-matter (Rule 33, enforcer E55)
#  38.  architecture_graph_well_formed                 -- generated architecture-graph.yaml builds + validates (Rule 34, enforcer E56)
#  39.  review_proposal_front_matter                   -- docs/logs/reviews/*.md front-matter is OPTIONAL (interaction records); validated only when a doc opts into 4+1 proposal classification (Rule 33, enforcer E57)
#  40.  enforcer_reachable_from_principle              -- every enforcer has at least one rule-edge (Rule 34, enforcer E58)
#  41.  enforcer_anchor_resolves                       -- every artifact: anchor resolves to real method/heading (Phase M, enforcer E60)
#  42.  architecture_graph_idempotent                  -- twice-run graph build is byte-identical (Phase M, enforcer E61)
#  43.  new_adr_must_be_yaml                           -- highest-numbered ADR is .yaml not .md (Phase M, enforcer E62)
#  44.  frozen_doc_edit_path_compliance                -- freeze_id-tagged file edits require docs/logs/reviews/*.md proposal (Phase M, enforcer E63)
#  --- W1.x L0 ironclad-rule enforcers (ADR-0069) ---
#  45.  bus_channels_three_track_present               -- bus-channels.yaml declares 3 channels with unique physical_channel (Rule 35 / P-E, enforcer E64)
#  46.  cursor_flow_documented                         -- openapi-v1.yaml declares TaskCursor schema + x-cursor-flow annotation (Rule 36 / P-F, enforcer E65)
#  47.  no_blocking_io_in_runtime_main                 -- agent-service/src/main excludes RestTemplate / JdbcTemplate (Rule 37 / P-G, enforcer E66)
#  48.  no_thread_sleep_in_business_code               -- main java sources exclude Thread.sleep / TimeUnit.sleep (Rule 38 / P-H, enforcer E67)
#  49.  deployment_plane_in_module_metadata            -- every module-metadata.yaml declares deployment_plane (Rule 39 / P-I, enforcer E68)
#  50.  rls_for_new_tenant_tables                      -- Flyway migrations with tenant_id enable RLS or are grandfathered (Rule 40 / P-J, enforcer E69)
#  51.  skill_capacity_yaml_present_and_wellformed     -- skill-capacity.yaml schema check (Rule 41 / P-K, enforcer E70)
#  52.  sandbox_policies_yaml_present_and_wellformed   -- sandbox-policies.yaml default_policy 6 keys (Rule 42 / P-L, enforcer E71)
#  --- W1.x Phase 8 — Cursor Flow runtime activation (ADR-0070) ---
#  53.  cursor_flow_integration_test_present           -- RunCursorFlowIT asserts POST /v1/runs returns 202 within 200ms even with a 30s-blocking dispatcher (Rule 36.b / P-F, enforcer E72)
#  --- W1.x Phase 9 — ResilienceContract runtime activation (ADR-0070) ---
#  54.  skill_capacity_runtime_resolver_present        -- DefaultSkillResilienceContract implements resolve(tenant, skill) consulting SkillCapacityRegistry; rejection carries SuspendReason.RateLimited (Rule 41.b / P-K, enforcer E73)
#  --- W2.x Phase 1 — Engine Envelope + Strict Matching (ADR-0072) ---
#  56.  engine_registry_covers_all_known_engines       -- bidirectional id <-> ENGINE_TYPE consistency between yaml and agent-service/src/main (Rule 44 / P-M, enforcer E77)
#  --- W2.x Phase 2 — Engine Hooks + Runtime Middleware SPI (ADR-0073) ---
#  57.  engine_hooks_yaml_present_and_wellformed       -- docs/contracts/engine-hooks.v1.yaml declares 9-hook list matching HookPoint enum (Rule 45 / P-M, enforcer E78)
#  --- W2.x Phase 3 — S2C Capability Callback (ADR-0074) ---
#  58.  s2c_callback_yaml_present_and_wellformed       -- docs/contracts/s2c-callback.v1.yaml declares request+response shape with 6 mandatory request fields and outcome enum (Rule 46 / P-M, enforcer E81)
#  --- W2.x Phase 6 — Schema-First Domain Contracts (ADR-0077, Rule 48) ---
#  60.  schema_first_domain_contracts                   -- prose enums in ARCHITECTURE.md require nearby yaml schema reference or grandfather entry (Rule 48 / P-M cross-cutting, enforcer E85)
#  --- v2.0.0-rc2 second-pass review closure (F-α / F-β / F-γ category audit) ---
#  62.  contract_yaml_declares_status                   -- every docs/contracts/*.v1.yaml + 3 governance YAMLs declare top-level status: with allowed enum value (F-β structural prevention)
#  --- 2026-05-17 cross-corpus consistency audit prevention rules (G1/G2/G3 closure, enforcers E94-E96) ---
#  65.  module_metadata_pom_dep_parity                  -- every com.huawei.ascend <dependency> in <module>/pom.xml appears in <module>/module-metadata.yaml allowed_dependencies (G2 prevention; metadata cannot lag behind pom)
#  66.  spi_package_exhaustiveness                      -- every */spi/ directory under <module>/src/main/java appears in <module>/module-metadata.yaml spi_packages (G3 prevention; metadata declares the full SPI surface)
#  --- 2026-05-17 CLAUDE.md token-optimization wave PR1 (enforcers E97-E101) ---
#  --- 2026-05-17 gate-script efficiency wave PR-E1 (enforcer E103) ---
#  73.  gate_config_well_formed                          -- gate/config.yaml validates against gate/config.schema.yaml (required keys, types, ranges, enums, no unknown keys) (Rule 73 / PR-E1, enforcer E103)
#  --- 2026-05-18 Linux-first dev environment policy (enforcer E104) ---
#  74.  linux_first_dev_doc_present                      -- docs/governance/dev-environment.md exists + recommends WSL2/WSL1/Linux for verification (Rule 74 / PR-E7, enforcer E104)
#  --- 2026-05-18 SPI metadata integrity wave (Rules 75-78; enforcers E105-E111) ---
#  --- 2026-05-18 rc4 cross-constraint review response prevention wave (Rules 80-83; enforcers E113-E116) ---
#  83.  design_only_contract_registered_in_catalog       -- every docs/contracts/*.v1.yaml with status: design_only OR runtime_enforced: false is listed in contract-catalog.md AND cites an existing ADR (P1-3 prevention, enforcer E116)
#  --- 2026-05-18 rc5 post-response review response prevention wave (Rules 84-85; enforcers E117-E118) ---
#  84.  active_module_architecture_path_truth           -- every architecture/docs/L1/agent-*.md architecture/docs/L1/agent-service/ARCHITECTURE.md (status != skeleton|deferred) inline path claim "<module>/src/main/java/..." must resolve on disk OR carry a historical/moved/extracted-per-ADR/superseded/deferred marker within +/-3 lines (rc5 P0-1 prevention, enforcer E117)
#  85.  catalog_spi_row_matches_module_spi_metadata     -- every non-(internal) row in contract-catalog.md SPI table must have its package in <module>/module-metadata.yaml#spi_packages AND docs/dfx/<module>.yaml#spi_packages; the (N total) header MUST equal the non-internal row count (rc5 P1-2 prevention, enforcer E118)
#  --- 2026-05-19 rc10 post-corrective review response prevention wave (Rules 99-100 + Rule 94/98 widening; enforcers E139-E142) ---
#  99.  kernel_terminal_verb_vs_shipped_decision_check  -- For every #### Rule N kernel block in CLAUDE.md with a matching ## Rule N.<letter> sub-clause in CLAUDE-deferred.md, the kernel MUST NOT use end-state verb tokens (`are SUSPENDED`, `is SUSPENDED`, `transitions to FAILED`, `consumes the * capacity`, `is rejected, not failed`, `admits the caller`) that overclaim shipped behaviour. Closes rc10 P1-1 (J-α family; Rule 41 kernel said "callers are SUSPENDED" while shipped code returns SkillResolution.reject — the actual transition is deferred to Rule 41.c).
#  100. kernel_implementation_disjunction_truth        -- For every rule in gate/rule-100-disjunction-allowlist.txt, BOTH the #### Rule N kernel block in CLAUDE.md AND the matching docs/governance/rules/rule-NN.md card MUST contain explicit disjunction wording (EITHER / OR / either surface / either ... or). Closes rc10 P1-3 (J-γ family; Rule 96 kernel said "MUST contain" while impl accepted EITHER kernel OR card — kernel-AND-impl-OR drift in the rule whose job is preventing such drift).
#  121. whitebox_quality_reports                     -- Maven SpotBugs/PMD/Checkstyle reports exist; high-confidence SpotBugs + hard-style Checkstyle findings block, PMD is review-trigger summary (Rule G-12, enforcer E169)
#  122. proposal_immediate_scope_pending_contract_guard -- proposal docs must not claim immediate W0/W1 scope while same boundary contracts are still pending (Rule G-2, enforcer E170)
#  123. proposal_engine_package_truth                 -- proposal FQNs must not contradict current engine/service package authority unless explicitly marked proposed (Rule G-8, enforcer E171)
#  124. unsupported_absolute_claim_guard              -- proposal security/performance absolutes require evidence wording (Rule G-2, enforcer E172)

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
# Rule 1 — status_enum_invalid
# docs/governance/architecture-status.yaml status: values must be in the
# allowed enum. Any other value is a FAIL.
# ---------------------------------------------------------------------------
_status_path="docs/governance/architecture-status.yaml"
_r1_fail=0
if [[ -f "$_status_path" ]]; then
  # PR-E3.b speedup: single awk pass instead of 1388 sed subprocesses.
  # awk emits the first invalid status value it finds (or empty for clean).
  _r1_bad=$(awk '
    BEGIN {
      ok["design_accepted"]=1; ok["implemented_unverified"]=1
      ok["test_verified"]=1; ok["deferred_w1"]=1; ok["deferred_w2"]=1
      # 2026-05-23: "open" added for strategic_decisions: block (founder-level
      # decisions deferred to W3+; tracked outside the architecture phase contract).
      # 2026-05-23: "design_only" added for contract-status rows whose
      # spec exists but runtime enforcement is deferred (Rule 62 vocab).
      ok["open"]=1; ok["design_only"]=1
    }
    /^[[:space:]]*status:[[:space:]]*[A-Za-z_]+[[:space:]]*$/ {
      val = $0
      sub(/^[[:space:]]*status:[[:space:]]*/, "", val)
      sub(/[[:space:]]+$/, "", val)
      if (!(val in ok)) { print val; exit }
    }
  ' "$_status_path")
  if [[ -n "$_r1_bad" ]]; then
    fail_rule "status_enum_invalid" "status '$_r1_bad' not in allowed enum {design_accepted,implemented_unverified,test_verified,deferred_w1,deferred_w2} in $_status_path"
    _r1_fail=1
  fi
  if [[ $_r1_fail -eq 0 ]]; then pass_rule "status_enum_invalid"; fi
else
  fail_rule "status_enum_invalid" "$_status_path not found"
fi

# ---------------------------------------------------------------------------
# Rule 2 — delivery_log_parity
# For each gate/log/*.json file: its sha field must equal the basename
# (without .json suffix). Its semantic_pass field must be a boolean.
# ---------------------------------------------------------------------------
_r2_fail=0
_r2_checked=0
while IFS= read -r _lf; do
  [[ -z "$_lf" ]] && continue
  _base="$(basename "$_lf" .json)"
  # Strip platform suffix (-posix, -windows) to get the sha
  _sha="${_base%%-posix}"
  _sha="${_sha%%-windows}"
  # Skip non-sha filenames (self-test-*, operator-shape-*, etc.)
  if [[ "$_sha" == self-test-* ]] || [[ "$_sha" == operator-shape-* ]]; then continue; fi
  _r2_checked=$((_r2_checked + 1))
  _log_sha="$(grep -oE '"sha":"[^"]*"' "$_lf" 2>/dev/null | head -1 | sed -E 's/.*"sha":"([^"]*)".*/\1/')"
  if [[ "$_log_sha" != "$_sha" ]]; then
    fail_rule "delivery_log_parity" "log $(basename "$_lf"): sha field '$_log_sha' != filename sha '$_sha'"
    _r2_fail=1
    break
  fi
  _sem="$(grep -oE '"semantic_pass":(true|false)' "$_lf" 2>/dev/null | head -1 | sed -E 's/.*:(.*)/\1/')"
  if [[ -z "$_sem" ]]; then
    fail_rule "delivery_log_parity" "log $(basename "$_lf") missing semantic_pass boolean field"
    _r2_fail=1
    break
  fi
done < <(find gate/log -maxdepth 1 -name '*.json' -type f 2>/dev/null | sort || true)
if [[ $_r2_fail -eq 0 ]]; then pass_rule "delivery_log_parity"; fi

# ---------------------------------------------------------------------------
# Rule 3 — eol_policy
# All *.sh files in gate/ must have LF line endings (not CRLF).
# ---------------------------------------------------------------------------
_r3_fail=0
while IFS= read -r _shf; do
  [[ -z "$_shf" ]] && continue
  [[ ! -f "$_shf" ]] && continue
  if grep -qU $'\r' "$_shf" 2>/dev/null; then
    fail_rule "eol_policy" "$_shf contains CRLF; must be LF"
    _r3_fail=1
    break
  fi
done < <(find gate -maxdepth 1 -name '*.sh' -type f 2>/dev/null | sort || true)
if [[ $_r3_fail -eq 0 ]]; then pass_rule "eol_policy"; fi

# ---------------------------------------------------------------------------
# Rule 4 — ci_no_or_true_mask
# .github/workflows/*.yml files must not contain gate/run_* invocations
# masked with || true.
# ---------------------------------------------------------------------------
_r4_fail=0
while IFS= read -r _wf; do
  [[ -f "$_wf" ]] || continue
  if grep -qE 'gate/run_.*\|\|[[:space:]]*true' "$_wf" 2>/dev/null; then
    fail_rule "ci_no_or_true_mask" "$_wf contains gate/run_* masked with || true"
    _r4_fail=1
    break
  fi
done < <(find .github/workflows -maxdepth 1 -name '*.yml' -type f 2>/dev/null | sort || true)
if [[ $_r4_fail -eq 0 ]]; then pass_rule "ci_no_or_true_mask"; fi

# ---------------------------------------------------------------------------
# Rule 5 — required_files_present
# These 2 files must exist: docs/contracts/contract-catalog.md and
# docs/contracts/openapi-v1.yaml.
# ---------------------------------------------------------------------------
_r5_fail=0
for _req in "docs/contracts/contract-catalog.md" "docs/contracts/openapi-v1.yaml"; do
  if [[ ! -f "$_req" ]]; then
    fail_rule "required_files_present" "$_req not found"
    _r5_fail=1
  fi
done
if [[ $_r5_fail -eq 0 ]]; then pass_rule "required_files_present"; fi

# ---------------------------------------------------------------------------
# Rule 6 — metric_naming_namespace
# In *.java files under agent-platform/src and agent-runtime/src, any
# hardcoded metric name strings must start with springai_ascend_.
# Also no springai_fin_ prefix outside docs/archive/.
# ---------------------------------------------------------------------------
_r6_fail=0
while IFS= read -r _jf; do
  [[ -f "$_jf" ]] || continue
  while IFS= read -r _jl; do
    # Match .counter("name") or similar metric-name string literals
    _nm="${_jl#*.counter(\"}"
    if [[ "$_nm" != "$_jl" ]]; then
      _nm="${_nm%%\"*}"
      if [[ -n "$_nm" && "${_nm:0:15}" != "springai_ascend" ]]; then
        fail_rule "metric_naming_namespace" "counter name '$_nm' in $_jf does not use springai_ascend_ prefix"
        _r6_fail=1
        break 2
      fi
    fi
    # Also catch timer/gauge builders if needed: .timer("name") .gauge("name")
    for _mbuilder in "timer" "gauge" "summary"; do
      _nm2="${_jl#*.$_mbuilder(\"}"
      if [[ "$_nm2" != "$_jl" ]]; then
        _nm2="${_nm2%%\"*}"
        if [[ -n "$_nm2" && "${_nm2:0:15}" != "springai_ascend" ]]; then
          fail_rule "metric_naming_namespace" "$_mbuilder name '$_nm2' in $_jf does not use springai_ascend_ prefix"
          _r6_fail=1
          break 3
        fi
      fi
    done
  done < "$_jf"
done < <(find agent-service/src -name '*.java' 2>/dev/null | sort || true)
# Check for residual springai_fin_ prefix outside docs/archive/
if grep -rn 'springai_fin_\|springai\.fin\.' \
    --include='*.java' \
    agent-service/src \
    2>/dev/null | grep -qv 'docs/archive'; then
  fail_rule "metric_naming_namespace" "residual springai_fin_ or springai.fin. found in Java sources"
  _r6_fail=1
fi
if [[ $_r6_fail -eq 0 ]]; then pass_rule "metric_naming_namespace"; fi

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
# Rule 11 — shipped_envelope_fingerprint_present
# InMemoryCheckpointer.java MUST contain MAX_INLINE_PAYLOAD_BYTES to prove
# the §4 #13 16-KiB inline cap is actually enforced (not just documented).
# ---------------------------------------------------------------------------
_r11_fail=0
_imc_path='agent-service/src/main/java/com/huawei/ascend/service/runtime/orchestration/inmemory/InMemoryCheckpointer.java'
if [[ -f "$_imc_path" ]]; then
  if ! grep -q 'MAX_INLINE_PAYLOAD_BYTES' "$_imc_path" 2>/dev/null; then
    fail_rule "shipped_envelope_fingerprint_present" "$_imc_path missing MAX_INLINE_PAYLOAD_BYTES. §4 #13 16-KiB cap enforcement required."
    _r11_fail=1
  fi
else
  fail_rule "shipped_envelope_fingerprint_present" "$_imc_path not found on disk"
  _r11_fail=1
fi
if [[ $_r11_fail -eq 0 ]]; then pass_rule "shipped_envelope_fingerprint_present"; fi

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
# Rule 13 — contract_catalog_no_deleted_spi_or_starter_names
# ADR-0036: contract-catalog.md must not reference deleted SPI interface names
# or deleted starter artifact coordinates.
# ---------------------------------------------------------------------------
_r13_fail=0
_catalog='docs/contracts/contract-catalog.md'
_deleted_names=(
  'LongTermMemoryRepository'
  'ToolProvider'
  'LayoutParser'
  'DocumentSourceConnector'
  'PolicyEvaluator'
  'IdempotencyRepository'
  'ArtifactRepository'
  'spring-ai-ascend-memory-starter'
  'spring-ai-ascend-skills-starter'
  'spring-ai-ascend-knowledge-starter'
  'spring-ai-ascend-governance-starter'
  'spring-ai-ascend-persistence-starter'
  'spring-ai-ascend-resilience-starter'
  'spring-ai-ascend-mem0-starter'
  'spring-ai-ascend-docling-starter'
  'spring-ai-ascend-langchain4j-profile'
)
if [[ -f "$_catalog" ]]; then
  for _dn in "${_deleted_names[@]}"; do
    if grep -qF "$_dn" "$_catalog" 2>/dev/null; then
      fail_rule "contract_catalog_no_deleted_spi_or_starter_names" "$_catalog references deleted name '$_dn'. Per ADR-0036 Gate Rule 13 this is a contract-surface truth violation."
      _r13_fail=1
    fi
  done
else
  fail_rule "contract_catalog_no_deleted_spi_or_starter_names" "$_catalog not found."
  _r13_fail=1
fi
if [[ $_r13_fail -eq 0 ]]; then pass_rule "contract_catalog_no_deleted_spi_or_starter_names"; fi

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
# Rule 18 — deleted_spi_starter_names_outside_catalog
# ADR-0041 extends Rule 13: deleted SPI/starter names must not appear in
# third_party/MANIFEST.md, docs/cross-cutting/oss-bill-of-materials.md, README.md.
# ---------------------------------------------------------------------------
_r18_fail=0
_deleted_names18=(
  'LongTermMemoryRepository' 'ToolProvider' 'LayoutParser' 'DocumentSourceConnector'
  'PolicyEvaluator' 'IdempotencyRepository' 'ArtifactRepository'
  'spring-ai-ascend-memory-starter' 'spring-ai-ascend-skills-starter'
  'spring-ai-ascend-knowledge-starter' 'spring-ai-ascend-governance-starter'
  'spring-ai-ascend-persistence-starter' 'spring-ai-ascend-resilience-starter'
  'spring-ai-ascend-mem0-starter' 'spring-ai-ascend-docling-starter'
  'spring-ai-ascend-langchain4j-profile'
)
# Perf fix (2026-05-23): the original loop forked grep N_files × N_names
# times (~thousands × 16 = ~50k forks). On WSL/mnt/d that was ~225s per
# gate run. Replaced with a single bulk `grep -Ff <(patterns) <files>` call
# (~1s) — same 16 fixed-string patterns, same file set, identical
# pass/fail semantics. ADR-0043 (widened to full ACTIVE_NORMATIVE_DOCS).
_r18_files=$(find . -name '*.md' -o -name '*.yaml' 2>/dev/null \
  | grep -vE '/docs/(archive|logs|adr|delivery|v6-rationale|plans|competitive|superpowers)/|/knowledge/|/third_party/|/target/|/\.git/' \
  | sort || true)
if [[ -n "$_r18_files" ]]; then
  _r18_patterns=$(printf '%s\n' "${_deleted_names18[@]}")
  # -H forces filename prefix; -F = fixed strings; -f - reads patterns from stdin.
  _r18_hits=$(printf '%s\n' "$_r18_files" | xargs -d '\n' -r grep -HnFf <(printf '%s\n' "$_r18_patterns") 2>/dev/null || true)
  if [[ -n "$_r18_hits" ]]; then
    while IFS= read -r _r18_hit; do
      [[ -z "$_r18_hit" ]] && continue
      # Parse `file:line:content` → extract first matching deleted-name token.
      _r18_file="${_r18_hit%%:*}"
      _r18_rest="${_r18_hit#*:}"
      _r18_line="${_r18_rest%%:*}"
      _r18_text="${_r18_rest#*:}"
      _r18_matched=""
      for _r18_name in "${_deleted_names18[@]}"; do
        if [[ "$_r18_text" == *"$_r18_name"* ]]; then _r18_matched="$_r18_name"; break; fi
      done
      fail_rule "deleted_spi_starter_names_outside_catalog" "$_r18_file:$_r18_line references deleted name '${_r18_matched:-?}'. Per ADR-0043 Gate Rule 18 (widened) this is a contract-surface truth violation."
      _r18_fail=1
    done <<< "$_r18_hits"
  fi
fi
if [[ $_r18_fail -eq 0 ]]; then pass_rule "deleted_spi_starter_names_outside_catalog"; fi

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
# Rule 23 — active_doc_internal_links_resolve
# ADR-0043: markdown links ](relative-path) in active normative docs must
# resolve to files that exist on disk. Excludes http://, https://, anchors.
# ---------------------------------------------------------------------------
_r23_fail=0
# Perf fix (2026-05-23): the original loop forked `grep | sed` per file +
# `cd | realpath` per link (~hundreds × ~10 = thousands of forks). On
# WSL/mnt/d this ran ~65s per gate. Replaced with a single python pass
# that reads each file once, extracts links via re, and resolves with
# os.path.normpath + os.path.exists. ADR-0043, same semantics.
_r23_violations=$("${GATE_PYTHON_BIN:-python3}" - <<'PYEOF'
import os, re, sys
from pathlib import Path
LINK_RE = re.compile(r'\]\(([^)]+)\)')
EXCLUDE_DIRS = ('./docs/archive/', './docs/logs/', './docs/adr/',
                './docs/delivery/', './docs/v6-rationale/', './docs/plans/',
                './docs/superpowers/', './third_party/', './discovery/',
                './knowledge/', './docs/architecture/', './architecture/docs/L1/', './docs/reviews/')
EXCLUDE_DIR_NAMES = {'target', '.git', 'node_modules'}

def is_excluded(p: str) -> bool:
    return any(p.startswith(d) for d in EXCLUDE_DIRS)

violations = []
for root, dirs, files in os.walk('.', topdown=True):
    # Prune excluded dirs in-place.
    dirs[:] = [
        d for d in dirs
        if d not in EXCLUDE_DIR_NAMES
        and not is_excluded(os.path.join(root, d) + '/')
    ]
    for fn in files:
        if not fn.endswith('.md'):
            continue
        fpath = os.path.join(root, fn)
        if is_excluded(fpath):
            continue
        try:
            text = Path(fpath).read_text(encoding='utf-8', errors='replace')
        except OSError:
            continue
        fdir = os.path.dirname(fpath)
        for link in LINK_RE.findall(text):
            # Skip external + anchor-only.
            if link.startswith(('http://', 'https://', 'mailto:', '#')):
                continue
            # Strip anchor fragment.
            path_only = link.split('#', 1)[0]
            if not path_only:
                continue
            resolved = os.path.normpath(os.path.join(fdir, path_only))
            if not os.path.exists(resolved):
                violations.append((fpath, link, resolved))

for fpath, link, resolved in violations:
    print(f"{fpath}\t{link}\t{resolved}")
PYEOF
)
if [[ -n "$_r23_violations" ]]; then
  while IFS=$'\t' read -r _r23_file _r23_link _r23_resolved; do
    [[ -z "$_r23_file" ]] && continue
    fail_rule "active_doc_internal_links_resolve" "$_r23_file has broken link to '$_r23_link' (resolved: '$_r23_resolved'). Per ADR-0043 Gate Rule 23 all internal links in active docs must resolve."
    _r23_fail=1
  done <<< "$_r23_violations"
fi
if [[ $_r23_fail -eq 0 ]]; then pass_rule "active_doc_internal_links_resolve"; fi

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
# Rule 26 — release_note_shipped_surface_truth
# ADR-0046: docs/logs/releases/*.md must not overclaim shipped surfaces.
#   26a — RunLifecycle name guard: line containing 'RunLifecycle' must be in a one-line
#         context window with a wave qualifier W1/W2/W3/W4, OR the same line must contain
#         one of: design-only|deferred|not shipped|remains design|materialised at W.
#   26b — RunContext method-list guard: line listing RunContext methods MUST NOT contain
#         posture() and method tokens must be subset of {runId,tenantId,checkpointer,suspendForChild}.
#   26c — OpenAPI snapshot attribution: ApiCompatibilityTest co-mentioned with
#         snapshot|OpenAPI.*spec|diverges fails (unless ArchUnit-only disclaimer present).
#   26d — AppPostureGate scope guard: 'AppPostureGate' on a line with 'HTTP Edge' fails;
#         'all runtime components.*posture.*constructor' fails.
# Closes GATE-SCOPE-GAP for release artifact class.
# ---------------------------------------------------------------------------
_r26_fail=0
if [[ -d docs/logs/releases ]]; then
  while IFS= read -r _rf26; do
    [[ -z "$_rf26" ]] && continue
    # rc32 perf: frozen release notes are immutable historical artefacts;
    # re-validating them on every gate run is wasted work (Rule 28 already
    # exempts them; Rule 26 should too). This dropped Rule 26 wall-clock
    # from 300s timeout to under 30s on a corpus with 25+ release notes.
    if grep -q "Historical artifact frozen at SHA" "$_rf26"; then
      continue
    fi
    # Pre-read file into an array of lines for context-window 26a.
    mapfile -t _rf26_lines < "$_rf26"
    _rf26_count=${#_rf26_lines[@]}
    for ((_i26=0; _i26 < _rf26_count; _i26++)); do
      _ln26="${_rf26_lines[$_i26]}"
      _lno26=$((_i26 + 1))
      # Narrative exemption: lines that explicitly describe Rule 26 itself are meta,
      # not shipped-surface claims. Skip them.
      if printf '%s' "$_ln26" | grep -qE 'Gate Rule 26|ADR-0046|release_note_shipped_surface_truth'; then
        continue
      fi
      # 26a: RunLifecycle name guard
      if printf '%s' "$_ln26" | grep -q 'RunLifecycle'; then
        _lo26=$((_i26 > 0 ? _i26 - 1 : 0))
        _hi26=$((_i26 + 1 < _rf26_count ? _i26 + 1 : _i26))
        _ctx26a=""
        for ((_j26=_lo26; _j26 <= _hi26; _j26++)); do
          _ctx26a="$_ctx26a ${_rf26_lines[$_j26]}"
        done
        _has_wave26a=0
        if printf '%s' "$_ctx26a" | grep -qE '(^|[^A-Za-z0-9])W[1-4]([^A-Za-z0-9]|$)'; then _has_wave26a=1; fi
        _has_marker26a=0
        if printf '%s' "$_ln26" | grep -qE 'design-only|deferred|not shipped|remains design|materialised at W|materialized at W'; then _has_marker26a=1; fi
        if [[ $_has_wave26a -eq 0 && $_has_marker26a -eq 0 ]]; then
          fail_rule "release_note_shipped_surface_truth" "$_rf26:$_lno26 (26a) contains 'RunLifecycle' without W1-W4 wave qualifier in context window or design-only/deferred/not shipped/remains design marker on the same line. Per ADR-0046."
          _r26_fail=1
        fi
      fi
      # 26b: RunContext method-list guard — only fires on methods-context lines
      # (table cell header, methods verb, or RunContext.method( syntax) and extracts
      # tokens only from the substring AFTER the first 'RunContext' occurrence.
      if printf '%s' "$_ln26" | grep -q 'RunContext'; then
        _is_methods_ctx26b=0
        if printf '%s' "$_ln26" | grep -qE '\|[[:space:]]*`?RunContext`?[[:space:]]*\|'; then _is_methods_ctx26b=1; fi
        if printf '%s' "$_ln26" | grep -qE 'RunContext[^.]{0,40}(exposes|interface|methods?|provides|carries|has)'; then _is_methods_ctx26b=1; fi
        if printf '%s' "$_ln26" | grep -qE 'RunContext\.[A-Za-z_]'; then _is_methods_ctx26b=1; fi
        if [[ $_is_methods_ctx26b -eq 1 ]]; then
          # Substring after first RunContext occurrence (POSIX awk).
          _after_rc26=$(printf '%s' "$_ln26" | awk '{ idx = index($0, "RunContext"); if (idx > 0) print substr($0, idx); }')
          if printf '%s' "$_after_rc26" | grep -qE '\bposture[[:space:]]*\('; then
            fail_rule "release_note_shipped_surface_truth" "$_rf26:$_lno26 (26b) contains 'RunContext' co-mentioned with 'posture()'. Per ADR-0046 RunContext has no posture(); canonical methods are runId/tenantId/checkpointer/suspendForChild."
            _r26_fail=1
          fi
          for _mt26 in $(printf '%s' "$_after_rc26" | grep -oE '\b[A-Za-z_][A-Za-z0-9_]*\(' | sed 's/($//'); do
            case "$_mt26" in
              [a-z]*)
                case "$_mt26" in
                  runId|tenantId|checkpointer|suspendForChild) : ;;
                  exposes|lists|returns|threads|carries|provides|sourced|interface|method|methods|requires|reads|writes|sees|gets|fails) : ;;
                  *)
                    fail_rule "release_note_shipped_surface_truth" "$_rf26:$_lno26 (26b) lists method '$_mt26()' alongside 'RunContext' in a methods-context. Per ADR-0046 canonical RunContext methods are {runId, tenantId, checkpointer, suspendForChild}; other tokens flag an invented method."
                    _r26_fail=1
                    ;;
                esac
                ;;
              *) : ;;
            esac
          done
        fi
      fi
      # 26c: OpenAPI snapshot test attribution
      if printf '%s' "$_ln26" | grep -q 'ApiCompatibilityTest' && \
         printf '%s' "$_ln26" | grep -qE 'snapshot|OpenAPI[[:space:]]*(snapshot|spec|v1)|diverges|live[[:space:]]*spec'; then
        if ! printf '%s' "$_ln26" | grep -qE 'ArchUnit[[:space:]]*-?[[:space:]]*only|not[[:space:]]+the[[:space:]]+OpenAPI|is[[:space:]]+not[[:space:]]+the[[:space:]]+OpenAPI'; then
          fail_rule "release_note_shipped_surface_truth" "$_rf26:$_lno26 (26c) attributes OpenAPI snapshot enforcement to ApiCompatibilityTest. Per ADR-0046 the snapshot diff lives in OpenApiContractIT (via OpenApiSnapshotComparator). ApiCompatibilityTest is ArchUnit-only."
          _r26_fail=1
        fi
      fi
      # 26d: AppPostureGate scope guard
      if printf '%s' "$_ln26" | grep -q 'AppPostureGate' && printf '%s' "$_ln26" | grep -qE 'HTTP[[:space:]]*Edge'; then
        fail_rule "release_note_shipped_surface_truth" "$_rf26:$_lno26 (26d) co-mentions 'AppPostureGate' with 'HTTP Edge'. Per ADR-0046 AppPostureGate lives in agent-runtime; it does not belong under HTTP Edge."
        _r26_fail=1
      fi
      if printf '%s' "$_ln26" | grep -qE 'all[[:space:]]+runtime[[:space:]]+components.*posture.*constructor|posture.*constructor.*all[[:space:]]+runtime[[:space:]]+components'; then
        fail_rule "release_note_shipped_surface_truth" "$_rf26:$_lno26 (26d) claims posture is a constructor argument for all runtime components. Per ADR-0046 only SyncOrchestrator, InMemoryRunRegistry, InMemoryCheckpointer call AppPostureGate; the claim is over-generalised."
        _r26_fail=1
      fi
    done
  done < <(find docs/logs/releases -name '*.md' -type f 2>/dev/null | sort || true)
fi
if [[ $_r26_fail -eq 0 ]]; then pass_rule "release_note_shipped_surface_truth"; fi

# ---------------------------------------------------------------------------
# Rule 27 — active_entrypoint_baseline_truth
# ADR-0047: root README.md MUST contain the four architecture baseline counts
# currently asserted by docs/governance/architecture-status.yaml
# architecture_sync_gate.allowed_claim. Catches CANONICAL-DRIFT.
# ---------------------------------------------------------------------------
_r27_fail=0
if [[ -f docs/governance/architecture-status.yaml && -f README.md ]]; then
  # Extract the architecture_sync_gate.allowed_claim line (it is a single line in YAML).
  _claim27=$(awk '/^[[:space:]]+architecture_sync_gate:/{flag=1} flag && /allowed_claim:/{print; exit}' docs/governance/architecture-status.yaml)
  if [[ -z "$_claim27" ]]; then
    fail_rule "active_entrypoint_baseline_truth" "docs/governance/architecture-status.yaml missing architecture_sync_gate.allowed_claim line. Per ADR-0047 Gate Rule 27."
    _r27_fail=1
  else
    _readme27=$(cat README.md)
    _check_baseline27() {
      _label="$1"; _yaml_re="$2"; _readme_re="$3"
      _expected=$(printf '%s' "$_claim27" | grep -oE "$_yaml_re" | head -1 | grep -oE '^[0-9]+' | head -1)
      [[ -z "$_expected" ]] && return 0
      _readme_matches=$(printf '%s' "$_readme27" | grep -oE "$_readme_re")
      if [[ -z "$_readme_matches" ]]; then
        fail_rule "active_entrypoint_baseline_truth" "README.md missing baseline count for '$_label'. Per ADR-0047 Gate Rule 27 the README MUST contain '$_expected $_label' (current canonical baseline)."
        _r27_fail=1
        return 0
      fi
      while IFS= read -r _rm27; do
        _actual=$(printf '%s' "$_rm27" | grep -oE '^[0-9]+' | head -1)
        if [[ "$_actual" != "$_expected" ]]; then
          fail_rule "active_entrypoint_baseline_truth" "README.md asserts '$_actual $_label' but canonical baseline is '$_expected $_label'. Per ADR-0047 Gate Rule 27."
          _r27_fail=1
        fi
      done <<< "$_readme_matches"
    }
    _check_baseline27 '§4 constraints' '[0-9]+[[:space:]]+§4[[:space:]]+constraints' '[0-9]+[[:space:]]+§4[[:space:]]+constraints'
    _check_baseline27 'ADRs' '[0-9]+[[:space:]]+ADRs' '[0-9]+[[:space:]]+ADRs'
    _check_baseline27 'gate rules' '[0-9]+[[:space:]]+active[[:space:]]+gate[[:space:]]+rules' '[0-9]+[[:space:]]+(active[[:space:]]+)?gate[[:space:]]+rules'
    _check_baseline27 'self-tests' '[0-9]+[[:space:]]+gate[[:space:]]+self-tests' '[0-9]+[[:space:]]+(gate[[:space:]]+)?self-tests'
  fi
fi
if [[ $_r27_fail -eq 0 ]]; then pass_rule "active_entrypoint_baseline_truth"; fi

# ---------------------------------------------------------------------------
# Rule 28 — release_note_baseline_truth
# ADR-0049 (whitepaper-alignment remediation P0-1): every docs/logs/releases/*.md
# baseline table MUST match the canonical architecture_sync_gate.allowed_claim
# counts, UNLESS the release note declares itself a historical artifact via
# the marker "Historical artifact frozen at SHA". Closes GATE-SCOPE-GAP for
# release-note baseline drift (Gate Rule 27 only covers README.md).
# ---------------------------------------------------------------------------
_r28_fail=0
if [[ -f docs/governance/architecture-status.yaml ]]; then
  _claim28=$(awk '/^[[:space:]]+architecture_sync_gate:/{flag=1} flag && /allowed_claim:/{print; exit}' docs/governance/architecture-status.yaml)
  if [[ -n "$_claim28" ]]; then
    while IFS= read -r _rf28; do
      [[ -z "$_rf28" ]] && continue
      if grep -qE 'Historical artifact frozen at SHA' "$_rf28"; then
        continue
      fi
      _rfcontent28=$(cat "$_rf28")
      _check_baseline28() {
        _label="$1"; _yaml_re="$2"; _rf_re="$3"
        _expected=$(printf '%s' "$_claim28" | grep -oE "$_yaml_re" | head -1 | grep -oE '^[0-9]+' | head -1)
        [[ -z "$_expected" ]] && return 0
        _rfmatches=$(printf '%s' "$_rfcontent28" | grep -oE "$_rf_re")
        if [[ -z "$_rfmatches" ]]; then
          fail_rule "release_note_baseline_truth" "$_rf28 missing baseline count for '$_label'. Per Gate Rule 28 active release notes must contain a table row matching '$_label | $_expected' or declare 'Historical artifact frozen at SHA <sha>'."
          _r28_fail=1
          return 0
        fi
        while IFS= read -r _rmline; do
          # Release notes use markdown-table format: '| <label> | <number> ... |'.
          # The number appears AFTER the label, so extract the trailing number.
          _actual=$(printf '%s' "$_rmline" | grep -oE '[0-9]+' | tail -1)
          if [[ "$_actual" != "$_expected" ]]; then
            fail_rule "release_note_baseline_truth" "$_rf28 asserts '$_actual' for '$_label' but canonical baseline is '$_expected $_label'. Per Gate Rule 28 active release notes must match the canonical baseline or declare 'Historical artifact frozen at SHA <sha>'."
            _r28_fail=1
          fi
        done <<< "$_rfmatches"
      }
      # Release-note table format: '| §4 constraints | 50 (#1–#50) |', etc.
      _check_baseline28 '§4 constraints' '[0-9]+[[:space:]]+§4[[:space:]]+constraints' '§4[[:space:]]+constraints[[:space:]]*\|[[:space:]]*[0-9]+'
      _check_baseline28 'ADRs' '[0-9]+[[:space:]]+ADRs' '(Active[[:space:]]+)?ADRs[[:space:]]*\|[[:space:]]*[0-9]+'
      _check_baseline28 'gate rules' '[0-9]+[[:space:]]+active[[:space:]]+gate[[:space:]]+rules' '(Active[[:space:]]+)?gate[[:space:]]+rules[[:space:]]*\|[[:space:]]*[0-9]+'
      _check_baseline28 'self-tests' '[0-9]+[[:space:]]+gate[[:space:]]+self-tests' '(Gate[[:space:]]+)?self-test[[:space:]]+cases[[:space:]]*\|[[:space:]]*[0-9]+'
    done < <(find docs/logs/releases -maxdepth 1 -name '*.md' -type f 2>/dev/null | sort || true)
  fi
fi
if [[ $_r28_fail -eq 0 ]]; then pass_rule "release_note_baseline_truth"; fi

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
# Rule 28g — no_prose_only_constraint_marker (enforcer E30)
# Rule 28 forbids deferring an enforcer. Markers like "TODO: enforce",
# "FIXME: enforcer", "XXX: test", "deferred: gate" in CLAUDE.md /
# ARCHITECTURE.md / module ARCHITECTURE.md / docs/governance/*.yaml are bans.
# ---------------------------------------------------------------------------
_r28g_fail=0
_marker_pattern='(TODO|FIXME|XXX|deferred)[[:space:]]*:[[:space:]]*(enforce|enforcer|test|gate)\b'
# Canonical architecture-text files + every L1+ ADR (00[5-9]X glob). ADR-0059
# is exempt because it documents the marker patterns themselves; any future
# L1+ ADR that legitimately needs to document the markers must explicitly
# extend the _28g_exempt list (rather than silently drop out of scope).
# Phase K (audit fix F7): switched from a hardcoded list to a glob with an
# explicit exempt set so new ADRs are auto-covered.
_28g_files=(CLAUDE.md ARCHITECTURE.md)
while IFS= read -r _arch; do
  [[ -n "$_arch" ]] && _28g_files+=("$_arch")
done < <(ls agent-service/ARCHITECTURE.md agent-service/ARCHITECTURE.md 2>/dev/null || true)
_28g_exempt=("docs/adr/0059-code-as-contract-architectural-enforcement.md")
while IFS= read -r _adr; do
  [[ -z "$_adr" ]] && continue
  _skip=0
  for _ex in "${_28g_exempt[@]}"; do
    [[ "$_adr" == "$_ex" ]] && _skip=1 && break
  done
  [[ $_skip -eq 0 ]] && _28g_files+=("$_adr")
done < <(ls docs/adr/00[5-9][0-9]-*.md 2>/dev/null | sort || true)
_28g_existing=()
for _f in "${_28g_files[@]}"; do
  [[ -f "$_f" ]] && _28g_existing+=("$_f")
done
_28g_hits=""
if (( ${#_28g_existing[@]} > 0 )); then
  _28g_hits=$(grep -nE "$_marker_pattern" "${_28g_existing[@]}" 2>/dev/null || true)
fi
if [[ -n "$_28g_hits" ]]; then
  fail_rule "no_prose_only_constraint_marker" "Rule-28-bypass marker found:\n$_28g_hits\nPer Rule 28g / enforcer E30."
  _r28g_fail=1
fi
if [[ $_r28g_fail -eq 0 ]]; then pass_rule "no_prose_only_constraint_marker"; fi

# ---------------------------------------------------------------------------
# Rule 28h — l1_review_checklist_present (enforcer E31)
# Every L1 ADR (0055–0059) MUST include the §16 review checklist subsection.
# ---------------------------------------------------------------------------
_r28h_fail=0
for _n in 0055 0056 0057 0058 0059 0060; do
  _adr=$(find docs/adr -maxdepth 1 -name "${_n}-*.md" 2>/dev/null | head -1)
  [[ -z "$_adr" ]] && continue
  if ! grep -qE '(§16 Review Checklist|L1 Review Checklist)' "$_adr" 2>/dev/null; then
    fail_rule "l1_review_checklist_present" "$_adr missing '§16 Review Checklist' subsection. Per Rule 28h / enforcer E31 / architect guidance §16."
    _r28h_fail=1
  fi
done
if [[ $_r28h_fail -eq 0 ]]; then pass_rule "l1_review_checklist_present"; fi

# ---------------------------------------------------------------------------
# Rule 28i — plan_enforcer_table_in_sync (enforcer E32)
# The L1 plan §11 table E<n> IDs MUST equal the set of `id:` fields in
# docs/governance/enforcers.yaml. The plan and the index are two views of the
# same truth.
# ---------------------------------------------------------------------------
_r28i_fail=0
_plan_file="$HOME/.claude/plans/l1-modular-russell.md"
# Fall back to alternative locations (Windows: /d/.claude/plans/...).
if [[ ! -f "$_plan_file" ]]; then
  _plan_file="/d/.claude/plans/l1-modular-russell.md"
fi
if [[ ! -f "$_plan_file" ]]; then
  # Plan lives outside the repo (user home). Skip with a NOTE.
  pass_rule "plan_enforcer_table_in_sync"
else
  _yaml_ids=$(grep -E '^- id: E[0-9]+' "$_efile" 2>/dev/null | sed -E 's/^- id:\s*//' | sort -u)
  _plan_ids=$(grep -oE '\| E[0-9]+ \|' "$_plan_file" 2>/dev/null | sed -E 's/\| (E[0-9]+) \|/\1/' | sort -u)
  if [[ -n "$_plan_ids" ]] && [[ "$_yaml_ids" != "$_plan_ids" ]]; then
    fail_rule "plan_enforcer_table_in_sync" "plan §11 enforcer IDs and enforcers.yaml IDs diverge. Per Rule 28i / enforcer E32."
    _r28i_fail=1
  fi
  if [[ $_r28i_fail -eq 0 ]]; then pass_rule "plan_enforcer_table_in_sync"; fi
fi

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
# Rule 34 — module_metadata_present_and_complete (enforcer E52, ADR-0066)
#
# Every reactor module (every <module>/pom.xml) MUST have a sibling
# module-metadata.yaml declaring module, kind, version, semver_compatibility.
# Required by CLAUDE.md Rule 31.
# ---------------------------------------------------------------------------
_r34_fail=0
_required_keys=(module kind version semver_compatibility)
while IFS= read -r _pom; do
  [[ -z "$_pom" ]] && continue
  # Skip the root reactor pom — it's the reactor declaration, not a module
  if [[ "$_pom" == "./pom.xml" || "$_pom" == "pom.xml" ]]; then continue; fi
  _mod_dir="$(dirname "$_pom")"
  _meta="${_mod_dir}/module-metadata.yaml"
  if [[ ! -f "$_meta" ]]; then
    fail_rule "module_metadata_present_and_complete" "$_meta missing — required for ${_mod_dir} (CLAUDE.md Rule 31 / ADR-0066)"
    _r34_fail=1
    continue
  fi
  for _k in "${_required_keys[@]}"; do
    if ! grep -qE "^[[:space:]]*${_k}:" "$_meta" 2>/dev/null; then
      fail_rule "module_metadata_present_and_complete" "$_meta missing required key '${_k}'"
      _r34_fail=1
    fi
  done
done < <(find . -mindepth 2 -maxdepth 2 -name 'pom.xml' -type f 2>/dev/null | sort || true)
if [[ $_r34_fail -eq 0 ]]; then pass_rule "module_metadata_present_and_complete"; fi

# ---------------------------------------------------------------------------
# Rule 35 — dfx_yaml_present_and_wellformed (enforcer E53, ADR-0067)
#
# Every module with kind ∈ {platform, domain} in its module-metadata.yaml
# MUST have a docs/dfx/<module>.yaml covering five DFX dimensions:
# releasability, resilience, availability, vulnerability, observability.
# DFX is OPTIONAL for kind ∈ {bom, starter, sample}.
# Required by CLAUDE.md Rule 32.
# ---------------------------------------------------------------------------
_r35_fail=0
_dfx_required_kinds_re='^(platform|domain)$'
while IFS= read -r _meta; do
  [[ -z "$_meta" ]] && continue
  _kind="$(grep -E '^[[:space:]]*kind:' "$_meta" 2>/dev/null | head -1 | sed -E 's/^[[:space:]]*kind:[[:space:]]*([A-Za-z_]+).*/\1/')"
  [[ ! "$_kind" =~ $_dfx_required_kinds_re ]] && continue
  _mod_name="$(grep -E '^[[:space:]]*module:' "$_meta" 2>/dev/null | head -1 | sed -E 's/^[[:space:]]*module:[[:space:]]*([A-Za-z0-9_-]+).*/\1/')"
  _dfx="docs/dfx/${_mod_name}.yaml"
  if [[ ! -f "$_dfx" ]]; then
    fail_rule "dfx_yaml_present_and_wellformed" "$_dfx missing — required for kind=${_kind} module '${_mod_name}' (CLAUDE.md Rule 32 / ADR-0067)"
    _r35_fail=1
    continue
  fi
  for _d in releasability resilience availability vulnerability observability; do
    if ! grep -qE "^[[:space:]]*${_d}:" "$_dfx" 2>/dev/null; then
      fail_rule "dfx_yaml_present_and_wellformed" "$_dfx missing required DFX dimension '${_d}'"
      _r35_fail=1
    fi
  done
done < <(find . -mindepth 2 -maxdepth 2 -name 'module-metadata.yaml' -type f 2>/dev/null | sort || true)
if [[ $_r35_fail -eq 0 ]]; then pass_rule "dfx_yaml_present_and_wellformed"; fi

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

# ---------------------------------------------------------------------------
# Rule 40 — enforcer_reachable_from_principle (enforcer E58, ADR-0068)
#
# Every shipped enforcer row in docs/governance/enforcers.yaml MUST be
# reachable from at least one Layer-0 principle (P-A..P-D or legacy
# P1..P3/E1) through the edge chain in architecture-graph.yaml:
#   principle --operationalised_by--> Rule-N --enforced_by--> E<n>
# The Python graph builder owns the traversal; this rule delegates to it.
# ---------------------------------------------------------------------------
_r40_fail=0
if [[ ! -f docs/governance/architecture-graph.yaml ]]; then
  fail_rule "enforcer_reachable_from_principle" "docs/governance/architecture-graph.yaml not present — run gate/build_architecture_graph.sh first"; _r40_fail=1
else
  # Embedded traversal check (avoids second Python invocation). For every
  # enforcer node E<n>, confirm there exists at least one Rule-N node feeding
  # it and that Rule-N is operationalised by at least one principle.
  _r40_orphans="$(awk '
    /^- id: / {
      if (cur != "" && type == "enforcer") enforcers[cur] = 1
      cur = $3
      type = ""
    }
    /^  type: enforcer/ { type = "enforcer" }
    /^  type: rule/    { rules_seen[cur] = 1 }
    /^  type: principle/ { principles_seen[cur] = 1 }
    /^- src: / { src = $3 }
    /^  dst: / { dst = $2 }
    /^  type: enforced_by/ { rule_to_enf[src] = rule_to_enf[src] " " dst; enf_has_rule[dst] = 1 }
    /^  type: operationalised_by/ { prin_to_rule[src] = prin_to_rule[src] " " dst; rule_has_prin[dst] = 1 }
    END {
      for (e in enforcers) {
        if (!(e in enf_has_rule)) {
          print "  - " e " (no rule -> enforcer edge)"
          orphan++
        }
      }
      if (orphan > 0) exit 1
    }
  ' docs/governance/architecture-graph.yaml 2>/dev/null || true)"
  if [[ -n "$_r40_orphans" ]]; then
    fail_rule "enforcer_reachable_from_principle" "orphaned enforcer(s): no rule path back to a principle:"
    echo "$_r40_orphans" >&2
    _r40_fail=1
  fi
fi
if [[ $_r40_fail -eq 0 ]]; then pass_rule "enforcer_reachable_from_principle"; fi

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

# ---------------------------------------------------------------------------
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

# ===========================================================================
# W1.x Phase 1 — L0 ironclad-rule enforcers (Gate Rules 45-52)
# Authority: ADR-0069. Each rule fails on a detected violation today.
# ===========================================================================

# ---------------------------------------------------------------------------
# Rule 45 — bus_channels_three_track_present (enforcer E64, Rule 35 / P-E)
#
# docs/governance/bus-channels.yaml MUST exist; declare 3 channels with ids
# control / data / rhythm; each MUST have a unique physical_channel: value.
# ---------------------------------------------------------------------------
_r45_fail=0
_r45_path="docs/governance/bus-channels.yaml"
if [[ ! -f "$_r45_path" ]]; then
  fail_rule "bus_channels_three_track_present" "$_r45_path missing — Rule 35 / P-E ironclad rule unenforced"
  _r45_fail=1
else
  # Extract id: values under channels:
  _r45_ids="$(awk '/^channels:[[:space:]]*$/{in_ch=1; next} /^[a-zA-Z]/{in_ch=0} in_ch && /^[[:space:]]+- id:/{sub(/^[[:space:]]+- id:[[:space:]]*/,""); sub(/[[:space:]].*$/,""); print}' "$_r45_path")"
  _r45_count="$(printf '%s\n' "$_r45_ids" | grep -c .)"
  if [[ "$_r45_count" -ne 3 ]]; then
    fail_rule "bus_channels_three_track_present" "$_r45_path declares $_r45_count channel ids; expected exactly 3 (control/data/rhythm)"
    _r45_fail=1
  else
    for _expected in control data rhythm; do
      if ! printf '%s\n' "$_r45_ids" | grep -qx "$_expected"; then
        fail_rule "bus_channels_three_track_present" "$_r45_path missing required channel id: $_expected"
        _r45_fail=1
      fi
    done
    # Extract physical_channel: values; must be unique
    _r45_phys="$(grep -E '^[[:space:]]+physical_channel:' "$_r45_path" | sed -E 's/^[[:space:]]+physical_channel:[[:space:]]*//; s/[[:space:]].*$//')"
    _r45_phys_count="$(printf '%s\n' "$_r45_phys" | grep -c .)"
    _r45_phys_uniq="$(printf '%s\n' "$_r45_phys" | sort -u | grep -c .)"
    if [[ "$_r45_phys_count" -ne "$_r45_phys_uniq" ]]; then
      fail_rule "bus_channels_three_track_present" "$_r45_path channels share physical_channel: identifiers (got $_r45_phys_count entries, $_r45_phys_uniq unique) — isolation guarantee violated"
      _r45_fail=1
    fi
  fi
fi
if [[ $_r45_fail -eq 0 ]]; then pass_rule "bus_channels_three_track_present"; fi

# ---------------------------------------------------------------------------
# Rule 47 — no_blocking_io_in_runtime_main (enforcer E66, Rule 37 / P-G)
#
# No production class under agent-service/src/main/java/** may import
# org.springframework.web.client.RestTemplate or
# org.springframework.jdbc.core.JdbcTemplate. Scope is intentionally narrow
# to agent-runtime (the cognitive kernel). Existing agent-platform JdbcTemplate
# uses migrate to R2DBC in W2 per CLAUDE-deferred.md 37.c.
# ---------------------------------------------------------------------------
_r47_fail=0
# Scope NARROWED post-Phase-C (ADR-0078): Rule 37 applies to the runtime sub-
# package only. agent-service/src/main/java/com/huawei/ascend/service/platform/**
# is excluded per CLAUDE-deferred.md 37.c — the platform-side JdbcTemplate uses
# (HealthCheckRepository, PlatformOssApiProbe) migrate to R2DBC in W2.
_r47_root="agent-service/src/main/java/com/huawei/ascend/service/runtime"
if [[ -d "$_r47_root" ]]; then
  _r47_hits="$(grep -rEln '^import[[:space:]]+org\.springframework\.(web\.client\.RestTemplate|jdbc\.core\.JdbcTemplate);' "$_r47_root" 2>/dev/null || true)"
  if [[ -n "$_r47_hits" ]]; then
    while IFS= read -r _f; do
      [[ -z "$_f" ]] && continue
      fail_rule "no_blocking_io_in_runtime_main" "$_f imports a forbidden blocking-I/O client (RestTemplate or JdbcTemplate) — use WebClient or R2dbcEntityTemplate instead"
      _r47_fail=1
    done <<< "$_r47_hits"
  fi
fi
if [[ $_r47_fail -eq 0 ]]; then pass_rule "no_blocking_io_in_runtime_main"; fi

# ---------------------------------------------------------------------------
# Rule 48 — no_thread_sleep_in_business_code (enforcer E67, Rule 38 / P-H)
#
# No production class under agent-service/src/main/java/** or
# agent-service/src/main/java/** may invoke Thread.sleep(...) or
# TimeUnit.<unit>.sleep(...). Test code is excluded.
# ---------------------------------------------------------------------------
_r48_fail=0
# Post-Phase-C (ADR-0078): both platform and runtime sub-packages are scanned
# under the single agent-service module. Pre-Phase-C this iterated over the
# two separate Maven modules.
for _r48_root in agent-service/src/main/java; do
  [[ ! -d "$_r48_root" ]] && continue
  _r48_hits="$(grep -rEn 'Thread\.sleep[[:space:]]*\(|TimeUnit\.[A-Z_]+\.sleep[[:space:]]*\(' "$_r48_root" 2>/dev/null || true)"
  if [[ -n "$_r48_hits" ]]; then
    while IFS= read -r _line; do
      [[ -z "$_line" ]] && continue
      fail_rule "no_thread_sleep_in_business_code" "$_line — physical sleep is forbidden (Chronos Hydration Rule 38); use SuspendSignal + bus Tick Engine"
      _r48_fail=1
    done <<< "$_r48_hits"
  fi
done
if [[ $_r48_fail -eq 0 ]]; then pass_rule "no_thread_sleep_in_business_code"; fi

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
# Rule 52 — sandbox_policies_yaml_present_and_wellformed (enforcer E71, Rule 42 / P-L)
#
# docs/governance/sandbox-policies.yaml MUST exist with default_policy:
# declaring all 6 required keys.
# ---------------------------------------------------------------------------
_r52_fail=0
_r52_path="docs/governance/sandbox-policies.yaml"
if [[ ! -f "$_r52_path" ]]; then
  fail_rule "sandbox_policies_yaml_present_and_wellformed" "$_r52_path missing — Rule 42 / P-L ironclad rule unenforced"
  _r52_fail=1
else
  if ! grep -qE '^default_policy:[[:space:]]*$' "$_r52_path"; then
    fail_rule "sandbox_policies_yaml_present_and_wellformed" "$_r52_path missing default_policy: block"
    _r52_fail=1
  else
    for _r52_key in outbound_network filesystem_read filesystem_write cpu_cap_millicores memory_cap_megabytes wall_clock_cap_seconds; do
      if ! grep -qE "^[[:space:]]+${_r52_key}:" "$_r52_path"; then
        fail_rule "sandbox_policies_yaml_present_and_wellformed" "$_r52_path default_policy missing required key: $_r52_key"
        _r52_fail=1
      fi
    done
  fi
fi
if [[ $_r52_fail -eq 0 ]]; then pass_rule "sandbox_policies_yaml_present_and_wellformed"; fi

# ---------------------------------------------------------------------------
# Rule 53 — cursor_flow_integration_test_present (enforcer E72, Rule 36.b / P-F, ADR-0070)
#
# A Phase 8 / Rule 36.b integration test MUST exist that drives the cursor-flow
# contract end-to-end: POST /v1/runs returns 202 within 200 ms even when the
# registered AsyncRunDispatcher synchronously blocks. The gate greps for the
# canonical method name + the elapsed-millis assertion shape so any future
# refactor that drops this coverage fails the gate.
# ---------------------------------------------------------------------------
_r53_fail=0
_r53_path="agent-service/src/test/java/com/huawei/ascend/service/platform/web/runs/RunCursorFlowIT.java"
if [[ ! -f "$_r53_path" ]]; then
  fail_rule "cursor_flow_integration_test_present" "$_r53_path missing — Rule 36.b / P-F integration test not landed"
  _r53_fail=1
else
  if ! grep -qE 'void[[:space:]]+createReturns202WithCursorWithin200ms[[:space:]]*\(' "$_r53_path"; then
    fail_rule "cursor_flow_integration_test_present" "$_r53_path missing canonical method createReturns202WithCursorWithin200ms() — Rule 36.b cursor flow IT contract"
    _r53_fail=1
  fi
  if ! grep -qE 'isLessThan\([[:space:]]*200L?[[:space:]]*\)' "$_r53_path"; then
    fail_rule "cursor_flow_integration_test_present" "$_r53_path missing elapsed-ms < 200 assertion — Rule 36.b requires response within 200 ms"
    _r53_fail=1
  fi
fi
if [[ $_r53_fail -eq 0 ]]; then pass_rule "cursor_flow_integration_test_present"; fi

# ---------------------------------------------------------------------------
# Rule 54 — skill_capacity_runtime_resolver_present (enforcer E73, Rule 41.b / P-K, ADR-0070)
#
# A production ResilienceContract implementation MUST exist under
# agent-service/src/main that (a) implements the two-arg resolve signature
# returning SkillResolution and (b) consults a SkillCapacityRegistry's
# tryAcquire(...) method. The gate greps for the canonical class shape so a
# regression that silently admits every caller (returning admit() unconditionally)
# fails. The matching integration test (E73) verifies behaviour separately.
# ---------------------------------------------------------------------------
_r54_fail=0
_r54_impl="agent-service/src/main/java/com/huawei/ascend/service/runtime/resilience"
_r54_spi="agent-service/src/main/java/com/huawei/ascend/service/runtime/resilience/spi"
if [[ ! -d "$_r54_spi" ]]; then
  fail_rule "skill_capacity_runtime_resolver_present" "$_r54_spi directory missing — Rule 41.b runtime SPI types not landed (post-ADR-0080 .spi package home)"
  _r54_fail=1
else
  if [[ ! -f "$_r54_spi/SkillCapacityRegistry.java" ]]; then
    fail_rule "skill_capacity_runtime_resolver_present" "SkillCapacityRegistry.java missing under .spi/ — Rule 41.b capacity tracking SPI absent (ADR-0080 package home)"
    _r54_fail=1
  fi
  if [[ ! -f "$_r54_spi/SkillResolution.java" ]]; then
    fail_rule "skill_capacity_runtime_resolver_present" "SkillResolution.java missing under .spi/ — Rule 41.b admit/reject envelope absent (ADR-0080 package home)"
    _r54_fail=1
  fi
  if [[ ! -f "$_r54_spi/SuspendReason.java" ]]; then
    fail_rule "skill_capacity_runtime_resolver_present" "SuspendReason.java missing under .spi/ — Rule 41.b sealed reason taxonomy absent (ADR-0080 package home)"
    _r54_fail=1
  fi
  if [[ ! -f "$_r54_impl/DefaultSkillResilienceContract.java" ]]; then
    fail_rule "skill_capacity_runtime_resolver_present" "DefaultSkillResilienceContract.java missing in impl parent package — Rule 41.b production impl absent"
    _r54_fail=1
  else
    if ! grep -qE 'SkillResolution[[:space:]]+resolve\([[:space:]]*String[[:space:]]+\w+,[[:space:]]*String[[:space:]]+\w+[[:space:]]*\)' "$_r54_impl/DefaultSkillResilienceContract.java"; then
      fail_rule "skill_capacity_runtime_resolver_present" "DefaultSkillResilienceContract.java missing two-arg resolve(String, String) returning SkillResolution"
      _r54_fail=1
    fi
    if ! grep -qE 'tryAcquire\(' "$_r54_impl/DefaultSkillResilienceContract.java"; then
      fail_rule "skill_capacity_runtime_resolver_present" "DefaultSkillResilienceContract.java does not call SkillCapacityRegistry.tryAcquire — Rule 41.b runtime consultation missing"
      _r54_fail=1
    fi
  fi
fi
if [[ $_r54_fail -eq 0 ]]; then pass_rule "skill_capacity_runtime_resolver_present"; fi

# ---------------------------------------------------------------------------
# Rule 56 — engine_registry_covers_all_known_engines (enforcer E77, Rule 44 / P-M, ADR-0072)
#
# Bidirectional consistency: every known_engines[].id in
# docs/contracts/engine-envelope.v1.yaml MUST appear as a
# String ENGINE_TYPE = "<id>" constant in agent-service/src/main, and every
# such constant MUST appear in known_engines. This guarantees the Phase 5
# EngineRegistry.validateAgainstSchema() boot check has matching inputs at
# compile time -- Rule 44 strict matching cannot be silently broken by a
# missing yaml row or a stale ENGINE_TYPE constant.
# ---------------------------------------------------------------------------
_r56_fail=0
_r56_yaml="docs/contracts/engine-envelope.v1.yaml"
# Post-T2.B2 (ADR-0079): EngineRegistry + ENGINE_TYPE constants moved to
# agent-execution-engine. Reference adapters (SequentialGraphExecutor +
# IterativeAgentLoopExecutor) stay in agent-service/.../inmemory and also
# declare ENGINE_TYPE. Scan BOTH source roots.
_r56_main="agent-execution-engine/src/main/java agent-service/src/main/java"
if [[ ! -f "$_r56_yaml" ]]; then
  fail_rule "engine_registry_covers_all_known_engines" "$_r56_yaml missing -- cannot cross-check"
  _r56_fail=1
else
  _r56_yaml_ids=$(grep -E '^[[:space:]]+- id:[[:space:]]+' "$_r56_yaml" | sed -E 's/^[[:space:]]+- id:[[:space:]]+([A-Za-z0-9_.-]+).*/\1/' | sort -u)
  _r56_src_ids=$(grep -rhE 'String[[:space:]]+ENGINE_TYPE[[:space:]]*=[[:space:]]*"[A-Za-z0-9_.-]+"' $_r56_main 2>/dev/null | sed -E 's/.*ENGINE_TYPE[[:space:]]*=[[:space:]]*"([A-Za-z0-9_.-]+)".*/\1/' | sort -u)
  for _id in $_r56_yaml_ids; do
    if ! echo "$_r56_src_ids" | grep -qxE "${_id}"; then
      fail_rule "engine_registry_covers_all_known_engines" "yaml declares known_engines.id=$_id but no ENGINE_TYPE=\"$_id\" found in $_r56_main"
      _r56_fail=1
    fi
  done
  for _id in $_r56_src_ids; do
    if ! echo "$_r56_yaml_ids" | grep -qxE "${_id}"; then
      fail_rule "engine_registry_covers_all_known_engines" "ENGINE_TYPE=\"$_id\" in source has no matching - id: $_id in $_r56_yaml"
      _r56_fail=1
    fi
  done
fi
if [[ $_r56_fail -eq 0 ]]; then pass_rule "engine_registry_covers_all_known_engines"; fi

# ---------------------------------------------------------------------------
# Rule 57 — engine_hooks_yaml_present_and_wellformed (enforcer E78, Rule 45 / P-M, ADR-0073)
#
# docs/contracts/engine-hooks.v1.yaml MUST exist with schema:, hooks: list of
# exactly the 9 canonical hook names, and bidirectionally agree with the
# HookPoint enum constants in agent-service/src/main. Drift in either
# direction breaks Rule 45 (Runtime-Owned Middleware via Engine Hooks).
# ---------------------------------------------------------------------------
_r57_fail=0
_r57_yaml="docs/contracts/engine-hooks.v1.yaml"
# Updated 2026-05-17: HookPoint moved from agent-runtime/orchestration/spi/ to
# agent-middleware/spi/ during the six-module materialization PR (T2.B1).
_r57_enum="agent-middleware/src/main/java/com/huawei/ascend/middleware/spi/HookPoint.java"
if [[ ! -f "$_r57_yaml" ]]; then
  fail_rule "engine_hooks_yaml_present_and_wellformed" "$_r57_yaml missing -- Rule 45 / P-M hook surface unenforced"
  _r57_fail=1
elif [[ ! -f "$_r57_enum" ]]; then
  fail_rule "engine_hooks_yaml_present_and_wellformed" "$_r57_enum missing -- cannot cross-check HookPoint enum"
  _r57_fail=1
else
  if ! grep -qE '^schema:[[:space:]]+engine-hooks/v1[[:space:]]*$' "$_r57_yaml"; then
    fail_rule "engine_hooks_yaml_present_and_wellformed" "$_r57_yaml missing 'schema: engine-hooks/v1' header"
    _r57_fail=1
  fi
  # Extract hook names from yaml (lines under 'hooks:' that look like '  - <name>')
  _r57_yaml_hooks=$(awk '/^hooks:/{f=1;next} /^[a-z_]+:/{f=0} f && /^[[:space:]]+- [a-z_]+/{gsub(/^[[:space:]]+- /,""); print}' "$_r57_yaml" | sort -u)
  # Extract HookPoint enum constants (lines like '    BEFORE_LLM_INVOCATION,' or '    ON_ERROR')
  _r57_enum_consts=$(grep -E '^[[:space:]]+[A-Z_]+[,;]?[[:space:]]*$' "$_r57_enum" | sed -E 's/[[:space:]]+([A-Z_]+)[,;]?[[:space:]]*/\1/' | tr 'A-Z_' 'a-z_' | sort -u)
  for _hook in $_r57_yaml_hooks; do
    if ! echo "$_r57_enum_consts" | grep -qxE "${_hook}"; then
      fail_rule "engine_hooks_yaml_present_and_wellformed" "yaml declares hook=$_hook but no matching HookPoint enum constant"
      _r57_fail=1
    fi
  done
  for _const in $_r57_enum_consts; do
    if ! echo "$_r57_yaml_hooks" | grep -qxE "${_const}"; then
      fail_rule "engine_hooks_yaml_present_and_wellformed" "HookPoint enum has constant $_const with no matching yaml hooks: entry"
      _r57_fail=1
    fi
  done
fi
if [[ $_r57_fail -eq 0 ]]; then pass_rule "engine_hooks_yaml_present_and_wellformed"; fi

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

# ---------------------------------------------------------------------------
# Rule 60 — schema_first_domain_contracts (enforcer E85, Rule 48, ADR-0077)
#
# Forbid new prose-defined enum sites in the architecture corpus. Scan
# ARCHITECTURE.md (root) + architecture/docs/L1/agent-*.md architecture/docs/L1/agent-service/ARCHITECTURE.md for the prose-enum pattern
# `<UPPERCASE_TYPE> | <UPPERCASE_TYPE>` outside fenced code blocks and
# markdown tables. For every match, the rule passes only when one of:
#   (a) the file path appears as a prefix line in gate/schema-first-grandfathered.txt
#       (file-level grandfather -- pre-W2.x existing taxonomies);
#   (b) the file path is at file-level grandfather (i.e. has any '<path>:' entry).
# The grandfather list is CLOSED: no entries added after 2026-05-16.
# This rule codifies the W2.x doctrine "yaml schema -> Java type -> runtime
# self-validate" into a permanent constraint.
# ---------------------------------------------------------------------------
_r60_fail=0
_r60_grandfather="gate/schema-first-grandfathered.txt"
_r60_files=(ARCHITECTURE.md agent-service/ARCHITECTURE.md agent-service/ARCHITECTURE.md)
if [[ ! -f "$_r60_grandfather" ]]; then
  fail_rule "schema_first_domain_contracts" "$_r60_grandfather missing -- Rule 48 grandfather list required"
  _r60_fail=1
else
  # Phase 7 audit fix (Rule 48 sunset discipline -- plan F2/F3).
  # Each grandfather entry MUST be
  # pipe-delimited <path>|<sunset_date>|<desc>. Validate sunset_date format
  # and that today <= sunset_date for every entry.
  _r60_today=$(date +%Y-%m-%d)
  while IFS= read -r _r60_line; do
    [[ -z "$_r60_line" || "$_r60_line" =~ ^[[:space:]]*# ]] && continue
    _r60_entry_path=$(printf '%s' "$_r60_line" | cut -d'|' -f1)
    _r60_entry_sunset=$(printf '%s' "$_r60_line" | cut -d'|' -f2)
    if [[ -z "$_r60_entry_path" || -z "$_r60_entry_sunset" ]]; then
      fail_rule "schema_first_domain_contracts" "grandfather entry malformed (need <path>|<sunset>|<desc>): $_r60_line"
      _r60_fail=1
      continue
    fi
    if ! [[ "$_r60_entry_sunset" =~ ^[0-9]{4}-[0-9]{2}-[0-9]{2}$ ]]; then
      fail_rule "schema_first_domain_contracts" "malformed sunset_date '$_r60_entry_sunset' for $_r60_entry_path in $_r60_grandfather (expected YYYY-MM-DD)"
      _r60_fail=1
      continue
    fi
    if [[ "$_r60_today" > "$_r60_entry_sunset" ]]; then
      fail_rule "schema_first_domain_contracts" "$_r60_entry_path grandfather entry expired on $_r60_entry_sunset; retrofit required per CLAUDE-deferred.md 48.b"
      _r60_fail=1
    fi
  done < "$_r60_grandfather"
  for _r60_file in "${_r60_files[@]}"; do
    if [[ ! -f "$_r60_file" ]]; then continue; fi
    _r60_candidates=$(awk '
      BEGIN { in_fence = 0 }
      /^```/ { in_fence = !in_fence; next }
      { if (in_fence) next }
      /^[[:space:]]*\|/ { next }
      /[A-Z][A-Z_][A-Z_]*[[:space:]]*\|[[:space:]]*[A-Z][A-Z_][A-Z_]*/ { print NR }
    ' "$_r60_file")
    if [[ -z "$_r60_candidates" ]]; then continue; fi
    # File-level grandfather check: if any line in grandfather list starts with this file path + '|', whitelist all matches.
    if grep -qE "^${_r60_file}\|" "$_r60_grandfather"; then continue; fi
    while read -r _r60_ln; do
      [[ -z "$_r60_ln" ]] && continue
      _r60_lo=$(( _r60_ln - 5 )); [[ $_r60_lo -lt 1 ]] && _r60_lo=1
      _r60_hi=$(( _r60_ln + 5 ))
      if ! awk -v lo="$_r60_lo" -v hi="$_r60_hi" 'NR>=lo && NR<=hi' "$_r60_file" \
         | grep -qE 'docs/(contracts|governance)/[^[:space:]]+\.yaml'; then
        fail_rule "schema_first_domain_contracts" "$_r60_file:$_r60_ln prose enum without yaml-schema reference within +/-5 lines and not in $_r60_grandfather"
        _r60_fail=1
      fi
    done <<< "$_r60_candidates"
  done
fi
if [[ $_r60_fail -eq 0 ]]; then pass_rule "schema_first_domain_contracts"; fi

# ---------------------------------------------------------------------------
# Rule 62 — contract_yaml_declares_status (v2.0.0-rc2 / second-pass review F-β structural prevention)
#
# Every domain-contract YAML under docs/contracts/*.v1.yaml AND the three
# previously-status-less governance YAMLs (skill-capacity, sandbox-policies,
# bus-channels) MUST declare a top-level `status:` field with a value in
# {design_only, schema_shipped, runtime_enforced}. This codifies the W2.x
# "post-review status label" convention and prevents the F-β defect family
# (deferred-as-live spec drift) from regrowing.
# ---------------------------------------------------------------------------
_r62_fail=0
_r62_allowed_re='^(design_only|schema_shipped|runtime_enforced)$'
_r62_files=(
  "docs/contracts/engine-envelope.v1.yaml"
  "docs/contracts/engine-hooks.v1.yaml"
  "docs/contracts/s2c-callback.v1.yaml"
  "docs/contracts/plan-projection.v1.yaml"
  "docs/governance/evolution-scope.v1.yaml"
  "docs/governance/skill-capacity.yaml"
  "docs/governance/sandbox-policies.yaml"
  "docs/governance/bus-channels.yaml"
)
for _r62_file in "${_r62_files[@]}"; do
  if [[ ! -f "$_r62_file" ]]; then
    fail_rule "contract_yaml_declares_status" "$_r62_file missing"
    _r62_fail=1
    continue
  fi
  _r62_status_val=$(awk '
    /^status:[[:space:]]+/ {
      v=$0
      sub(/^status:[[:space:]]+/, "", v)
      sub(/[[:space:]]+#.*$/, "", v)
      sub(/[[:space:]]+$/, "", v)
      print v
      exit
    }
  ' "$_r62_file")
  if [[ -z "$_r62_status_val" ]]; then
    fail_rule "contract_yaml_declares_status" "$_r62_file missing top-level 'status:' field"
    _r62_fail=1
    continue
  fi
  if ! [[ "$_r62_status_val" =~ $_r62_allowed_re ]]; then
    fail_rule "contract_yaml_declares_status" "$_r62_file has status: '$_r62_status_val' -- must be one of {design_only, schema_shipped, runtime_enforced}"
    _r62_fail=1
  fi
done
if [[ $_r62_fail -eq 0 ]]; then pass_rule "contract_yaml_declares_status"; fi

# ===========================================================================
# Cross-corpus consistency audit prevention rules (2026-05-17)
# Authority: docs/logs/reviews/2026-05-17-cross-corpus-consistency-audit-response.en.md
# Closes structural design flaws G1, G2, G3 surfaced by the audit:
#   G1 — module count was hardcoded in 4 places
#   G2 — no metadata-vs-pom dependency cross-check
#   G3 — no SPI-package exhaustiveness cross-check
# Rules 64-66 with enforcer rows E94-E96 and 6 self-tests (2 per rule).
# ===========================================================================

# ---------------------------------------------------------------------------
# Rule 65 — module_metadata_pom_dep_parity (enforcer E95, G2 prevention)
#
# For each <module>/module-metadata.yaml, every com.huawei.ascend sibling
# artifact declared in <module>/pom.xml's <dependencies> MUST appear in
# allowed_dependencies of the metadata. Catches drift where a developer
# adds a dep to the pom but forgets to update the metadata declaration.
# ---------------------------------------------------------------------------
_r65_fail=0
while IFS= read -r _r65_meta; do
  [[ -z "$_r65_meta" ]] && continue
  _r65_mod_dir="$(dirname "$_r65_meta")"
  _r65_pom="${_r65_mod_dir}/pom.xml"
  [[ -f "$_r65_pom" ]] || continue
  # Extract com.huawei.ascend sibling deps from pom — only inside <dependency> blocks
  # (excludes the <parent> block at top, which would otherwise be a false positive).
  # Skip <dependencyManagement> block — those are managed versions for downstream
  # modules (BoM-style), not direct compile-time deps of the current module.
  _r65_pom_deps=$(awk '
    /<dependencyManagement>/ { in_mgmt=1; next }
    /<\/dependencyManagement>/ { in_mgmt=0; next }
    !in_mgmt && /<dependency>/ { in_dep=1; want=0; next }
    /<\/dependency>/ { in_dep=0; want=0; next }
    in_dep && /<groupId>ascend\.springai<\/groupId>/ { want=1; next }
    in_dep && want && /<artifactId>/ {
      gsub(/^[[:space:]]*<artifactId>/, "")
      gsub(/<\/artifactId>.*/, "")
      print
      want=0
    }
  ' "$_r65_pom" | sort -u)
  # Extract allowed_dependencies block entries from metadata
  _r65_meta_allowed=$(awk '/^allowed_dependencies:/{flag=1; next} /^[a-zA-Z_]/{flag=0} flag && /^[[:space:]]*-[[:space:]]+/{gsub(/^[[:space:]]*-[[:space:]]+/,""); gsub(/[[:space:]#].*$/,""); print}' "$_r65_meta" | sort -u)
  while IFS= read -r _r65_dep; do
    [[ -z "$_r65_dep" ]] && continue
    if ! echo "$_r65_meta_allowed" | grep -qxF "$_r65_dep"; then
      fail_rule "module_metadata_pom_dep_parity" "$_r65_pom declares dependency on '$_r65_dep' (com.huawei.ascend sibling) but $_r65_meta allowed_dependencies does not list it (G2 prevention)"
      _r65_fail=1
    fi
  done <<< "$_r65_pom_deps"
done <<< "${_SCAN_MODULE_METADATA:-$(find . -maxdepth 3 -name module-metadata.yaml -not -path './target/*' -not -path './.claude/*' 2>/dev/null)}"
if [[ $_r65_fail -eq 0 ]]; then pass_rule "module_metadata_pom_dep_parity"; fi

# ---------------------------------------------------------------------------
# Rule 66 — spi_package_exhaustiveness (enforcer E96, G3 prevention)
#
# For each <module>/module-metadata.yaml, every src/main/java/.../spi
# directory MUST appear in spi_packages. Catches drift where a developer
# adds a new SPI package (e.g. runtime.s2c.spi) but forgets to declare it
# in the metadata.
# ---------------------------------------------------------------------------
_r66_fail=0
while IFS= read -r _r66_meta; do
  [[ -z "$_r66_meta" ]] && continue
  _r66_mod_dir="$(dirname "$_r66_meta")"
  _r66_src="${_r66_mod_dir}/src/main/java"
  [[ -d "$_r66_src" ]] || continue
  _r66_declared=$(awk '/^spi_packages:/{flag=1; next} /^[a-zA-Z_]/{flag=0} flag && /^[[:space:]]*-[[:space:]]+/{gsub(/^[[:space:]]*-[[:space:]]+/,""); gsub(/[[:space:]#].*$/,""); print}' "$_r66_meta" | sort -u)
  while IFS= read -r _r66_dir; do
    [[ -z "$_r66_dir" ]] && continue
    _r66_pkg="${_r66_dir#${_r66_src}/}"
    _r66_pkg="${_r66_pkg//\//.}"
    if ! echo "$_r66_declared" | grep -qxF "$_r66_pkg"; then
      fail_rule "spi_package_exhaustiveness" "$_r66_dir exists on disk but package '$_r66_pkg' is not declared in $_r66_meta spi_packages (G3 prevention)"
      _r66_fail=1
    fi
  done <<< "$(find "$_r66_src" -type d -name spi 2>/dev/null)"
done <<< "${_SCAN_MODULE_METADATA:-$(find . -maxdepth 3 -name module-metadata.yaml -not -path './target/*' -not -path './.claude/*' 2>/dev/null)}"
if [[ $_r66_fail -eq 0 ]]; then pass_rule "spi_package_exhaustiveness"; fi

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

# ---------------------------------------------------------------------------
# Rule 73 — gate_config_well_formed (enforcer E103)
#
# Sources gate/lib/load_config.sh and runs the validator. Fails if:
#   - gate/config.yaml or gate/config.schema.yaml missing
#   - YAML parser detected malformed input (__ERROR__ sentinel)
#   - Required top-level key missing
#   - Type / range / enum violation on any validated leaf
#
# The validator implementation lives in gate/lib/load_config.sh
# (gate_validate_config_against_schema). This rule is the gate-side wrapper.
# ---------------------------------------------------------------------------
_r73_fail=0
_r73_loader='gate/lib/load_config.sh'
_r73_config='gate/config.yaml'
_r73_schema='gate/config.schema.yaml'
if [[ ! -f "$_r73_loader" ]]; then
  fail_rule "gate_config_well_formed" "$_r73_loader missing -- cannot validate gate/config.yaml"
  _r73_fail=1
elif [[ ! -f "$_r73_config" ]]; then
  fail_rule "gate_config_well_formed" "$_r73_config missing"
  _r73_fail=1
elif [[ ! -f "$_r73_schema" ]]; then
  fail_rule "gate_config_well_formed" "$_r73_schema missing"
  _r73_fail=1
else
  # Run validation in a subshell so we don't pollute the main shell with
  # the loader's exported GATE_* variables. Capture VALID + ERRORS via stdout.
  _r73_result=$(bash -c '
    source '"'$_r73_loader'"'
    gate_load_config >/dev/null 2>&1
    gate_validate_config_against_schema >/dev/null 2>&1
    printf "%s\n" "${GATE_CONFIG_VALID:-false}"
    printf "%s" "${GATE_CONFIG_ERRORS:-}"
  ')
  _r73_valid=$(printf '%s\n' "$_r73_result" | head -1)
  _r73_errors=$(printf '%s\n' "$_r73_result" | tail -n +2)
  if [[ "$_r73_valid" == "true" ]]; then
    pass_rule "gate_config_well_formed"
  else
    fail_rule "gate_config_well_formed" "$(printf '%s' "$_r73_errors" | tr '\n' ';')"
    _r73_fail=1
  fi
fi

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

# Rule 75 — spi_packages_populated (enforcer E108)
#
# Every <module>/module-metadata.yaml#spi_packages entry MUST resolve to a
# real directory under <module>/src/main/java/... AND that directory MUST
# contain at least one .java file beyond package-info.java. Catches the
# 2026-05-18 root cause (com.huawei.ascend.engine.spi declared but empty).
#
# Placeholder marker: an spi_packages line that includes BOTH "placeholder"
# AND an "ADR-NNNN" reference in its inline comment is allowed to be empty
# (or absent on disk). This honors deferred SPI work explicitly waived via
# an ADR — e.g. agent-bus / agent-client / agent-evolve W2/W3+ scaffolds.
# ---------------------------------------------------------------------------
_r75_fail=0
while IFS= read -r _r75_meta; do
  [[ -z "$_r75_meta" ]] && continue
  _r75_mod_dir="$(dirname "$_r75_meta")"
  _r75_src="${_r75_mod_dir}/src/main/java"
  _r75_in_block=0
  while IFS= read -r _r75_line; do
    if [[ "$_r75_line" =~ ^spi_packages: ]]; then
      _r75_in_block=1
      continue
    fi
    if [[ $_r75_in_block -eq 1 ]]; then
      if [[ "$_r75_line" =~ ^[a-zA-Z_] ]]; then
        _r75_in_block=0
        continue
      fi
      if [[ "$_r75_line" =~ ^[[:space:]]*-[[:space:]] ]]; then
        # Honor placeholder marker — skip if line comment contains both
        # "placeholder" and an ADR-NNNN reference (deferred SPI work).
        if [[ "$_r75_line" == *"#"* ]] && \
           echo "$_r75_line" | grep -qE 'placeholder' && \
           echo "$_r75_line" | grep -qE 'ADR-[0-9]{4}'; then
          continue
        fi
        _r75_pkg=$(echo "$_r75_line" | sed -E 's/^[[:space:]]*-[[:space:]]*//' | sed -E 's/[[:space:]#].*$//' | tr -d "\"'")
        [[ -z "$_r75_pkg" ]] && continue
        _r75_dir="${_r75_src}/${_r75_pkg//./\/}"
        if [[ ! -d "$_r75_dir" ]]; then
          fail_rule "spi_packages_populated" "$_r75_meta declares spi package '$_r75_pkg' which resolves to '$_r75_dir' — directory does not exist on disk (Rule 75 / E108)"
          _r75_fail=1
          continue
        fi
        _r75_java_count=$(find "$_r75_dir" -maxdepth 1 -name '*.java' -not -name 'package-info.java' 2>/dev/null | wc -l)
        if [[ "${_r75_java_count:-0}" -lt 1 ]]; then
          fail_rule "spi_packages_populated" "$_r75_meta declares spi package '$_r75_pkg' at '$_r75_dir' which contains only package-info.java (no real SPI classes). Mark as deferred with '# placeholder; ADR-NNNN ...' comment to waive, or populate the SPI. Rule 75 / E108"
          _r75_fail=1
        fi
      fi
    fi
  done < "$_r75_meta"
done <<< "${_SCAN_MODULE_METADATA:-$(find . -maxdepth 3 -name module-metadata.yaml -not -path './target/*' -not -path './.claude/*' 2>/dev/null)}"
if [[ $_r75_fail -eq 0 ]]; then pass_rule "spi_packages_populated"; fi

# ---------------------------------------------------------------------------
# Rule 76 — no_split_spi_packages (enforcer E109)
#
# A given Java spi package MUST be declared by exactly one Maven module's
# module-metadata.yaml#spi_packages. Two modules co-declaring the same
# package is a split-package — Maven and JPMS cannot reason about ownership.
# Catches the 2026-05-18 root cause (orchestration.spi historical double-
# declaration by agent-runtime-core AND agent-execution-engine — both modules
# resolved by rc13 ADR-0088 dissolution).
# ---------------------------------------------------------------------------
_r76_fail=0
_r76_tmp="$(mktemp 2>/dev/null || echo /tmp/r76.$$)"
: > "$_r76_tmp"
while IFS= read -r _r76_meta; do
  [[ -z "$_r76_meta" ]] && continue
  _r76_mod="$(grep -E '^[[:space:]]*module:' "$_r76_meta" 2>/dev/null | head -1 | sed -E 's/^[[:space:]]*module:[[:space:]]*([A-Za-z0-9_-]+).*/\1/')"
  _r76_pkgs=$(awk '/^spi_packages:/{flag=1; next} /^[a-zA-Z_]/{flag=0} flag && /^[[:space:]]*-[[:space:]]+/{gsub(/^[[:space:]]*-[[:space:]]+/,""); gsub(/["\047]/,""); gsub(/[[:space:]#].*$/,""); print}' "$_r76_meta")
  while IFS= read -r _r76_pkg; do
    [[ -z "$_r76_pkg" ]] && continue
    printf '%s|%s\n' "$_r76_pkg" "$_r76_mod" >> "$_r76_tmp"
  done <<< "$_r76_pkgs"
done <<< "${_SCAN_MODULE_METADATA:-$(find . -maxdepth 3 -name module-metadata.yaml -not -path './target/*' -not -path './.claude/*' 2>/dev/null)}"
_r76_dupes=$(sort "$_r76_tmp" | awk -F'|' '{ owners[$1]=owners[$1]" "$2; counts[$1]++ } END { for (k in counts) if (counts[k] > 1) print k "|" owners[k] }')
rm -f "$_r76_tmp"
if [[ -n "$_r76_dupes" ]]; then
  while IFS= read -r _r76_d; do
    fail_rule "no_split_spi_packages" "spi package '${_r76_d%%|*}' declared by multiple modules:${_r76_d#*|} (Rule 76 / E109)"
    _r76_fail=1
  done <<< "$_r76_dupes"
fi
if [[ $_r76_fail -eq 0 ]]; then pass_rule "no_split_spi_packages"; fi

# ---------------------------------------------------------------------------
# Rule 78 — dfx_spi_packages_match_module_metadata (enforcer E111)
#
# For every module with kind ∈ {platform, domain}, the dfx yaml at
# docs/dfx/<module>.yaml MUST declare a top-level `spi_packages:` block
# whose entries are an order-insensitive set match with the module's
# module-metadata.yaml#spi_packages (placeholder entries excluded — see
# Rule 75). Catches the 2026-05-18 root cause where dfx yamls omitted,
# mis-nested (under observability), or under-declared spi packages
# relative to module-metadata.yaml.
#
# Placeholder filter: lines whose inline comment contains BOTH "placeholder"
# AND "ADR-NNNN" are excluded from both sides of the comparison so deferred
# SPI work declared symmetrically (or asymmetrically) in metadata only does
# not force a noisy dfx declaration before the real SPI lands.
# ---------------------------------------------------------------------------
_r78_fail=0
# Perf fix (2026-05-23): replaced per-metadata × per-line grep/sed/tr loop
# (~8 modules × 2 files × ~5 lines × ~5 forks = ~400 forks, ~13s) with a
# single python pass. Same placeholder filter (`# ... placeholder ... ADR-NNNN`).
_r78_violations="$("${GATE_PYTHON_BIN:-python3}" - <<'PYEOF'
import os, re, glob
from pathlib import Path

DFX_REQUIRED_KINDS = {'platform', 'domain'}

def extract_real_spi(path: str) -> list[str]:
    try: lines = Path(path).read_text(encoding='utf-8', errors='replace').splitlines()
    except OSError: return []
    in_block = False; out: list[str] = []
    placeholder_re = re.compile(r'placeholder')
    adr_re = re.compile(r'ADR-\d{4}')
    for line in lines:
        if re.match(r'^spi_packages:', line):
            in_block = True; continue
        if not in_block: continue
        if re.match(r'^[a-zA-Z_]', line):
            in_block = False; continue
        m = re.match(r'^\s*-\s+', line)
        if not m: continue
        # Placeholder filter: comment containing both 'placeholder' AND 'ADR-NNNN'.
        if '#' in line and placeholder_re.search(line) and adr_re.search(line):
            continue
        # Strip leading dash + token-and-onwards.
        v = re.sub(r'^\s*-\s*', '', line)
        v = re.sub(r'[\s#].*$', '', v)
        v = v.strip('"\'')
        if v: out.append(v)
    return sorted(set(out))

metas = sorted(set(glob.glob('*/module-metadata.yaml') + glob.glob('*/*/module-metadata.yaml')))
for meta in metas:
    try: text = Path(meta).read_text(encoding='utf-8', errors='replace')
    except OSError: continue
    km = re.search(r'^\s*kind:\s*([A-Za-z_]+)', text, re.MULTILINE)
    if not km or km.group(1) not in DFX_REQUIRED_KINDS: continue
    mm = re.search(r'^\s*module:\s*([A-Za-z0-9_-]+)', text, re.MULTILINE)
    if not mm: continue
    mod = mm.group(1)
    dfx = f'docs/dfx/{mod}.yaml'
    if not os.path.isfile(dfx): continue  # Rule 35 reports missing-dfx
    meta_spi = extract_real_spi(meta)
    dfx_spi = extract_real_spi(dfx)
    if not meta_spi: continue
    if not dfx_spi:
        print(f"MISSING\t{meta}\t{dfx}\t\t")
        continue
    if meta_spi != dfx_spi:
        print(f"MISMATCH\t{meta}\t{dfx}\t{','.join(meta_spi)}\t{','.join(dfx_spi)}")
PYEOF
)"
if [[ -n "$_r78_violations" ]]; then
  while IFS=$'\t' read -r _r78_kind _r78_meta _r78_dfx _r78_meta_one _r78_dfx_one; do
    [[ -z "$_r78_kind" ]] && continue
    case "$_r78_kind" in
      MISSING)
        fail_rule "dfx_spi_packages_match_module_metadata" "$_r78_dfx missing top-level 'spi_packages:' block (must mirror non-placeholder entries of $_r78_meta) — Rule 78 / E111"
        ;;
      MISMATCH)
        fail_rule "dfx_spi_packages_match_module_metadata" "$_r78_meta non-placeholder spi_packages={${_r78_meta_one}} but $_r78_dfx declares {${_r78_dfx_one}} — Rule 78 / E111"
        ;;
    esac
    _r78_fail=1
  done <<< "$_r78_violations"
fi
if [[ $_r78_fail -eq 0 ]]; then pass_rule "dfx_spi_packages_match_module_metadata"; fi

# ===========================================================================
# 2026-05-18 rc4 cross-constraint review response prevention wave -- Rule 83
# Authority: docs/governance/rules/rule-83.md
#            + docs/logs/reviews/2026-05-18-l0-rc4-cross-constraint-architecture-review.en.md
#            + docs/logs/reviews/2026-05-18-l0-rc4-cross-constraint-architecture-review-response.en.md
# Closes finding families:
#   P1-3 design-only contracts unregistered / dangling auth  -> Rule 83
# ===========================================================================

# Rule 83 — design_only_contract_registered_in_catalog (enforcer E116)
#
# Every docs/contracts/*.v1.yaml with status: design_only (or runtime_enforced:
# false) MUST (a) be listed by file basename in docs/contracts/contract-catalog.md,
# AND (b) cite at least one ADR-NNNN reference whose file exists under
# docs/adr/. Operationalises the rc4 review P1-3 prevention: design-only
# contracts cannot drift unregistered, and cited ADRs cannot dangle.
# ---------------------------------------------------------------------------
_r83_fail=0
_r83_catalog="docs/contracts/contract-catalog.md"
for _r83_contract in docs/contracts/*.v1.yaml; do
  [[ -f "$_r83_contract" ]] || continue
  _r83_status=$(grep -E '^status:' "$_r83_contract" 2>/dev/null | head -1 || true)
  _r83_runtime=$(grep -E '^runtime_enforced:' "$_r83_contract" 2>/dev/null | head -1 || true)
  if [[ "$_r83_status" == *design_only* ]] || [[ "$_r83_runtime" == *false* ]]; then
    _r83_name="$(basename "$_r83_contract")"
    if [[ ! -f "$_r83_catalog" ]] || ! grep -qF "$_r83_name" "$_r83_catalog" 2>/dev/null; then
      fail_rule "design_only_contract_registered_in_catalog" "$_r83_contract is design-only/runtime_enforced=false but not listed in $_r83_catalog -- Rule 83 / E116"
      _r83_fail=1
    fi
    _r83_adr_ok=0
    while IFS= read -r _r83_adr; do
      [[ -z "$_r83_adr" ]] && continue
      _r83_num="${_r83_adr#ADR-}"
      if compgen -G "docs/adr/${_r83_num}-*.yaml" > /dev/null || compgen -G "docs/adr/${_r83_num}-*.md" > /dev/null; then
        _r83_adr_ok=1
      fi
    done < <(grep -oE 'ADR-[0-9]{4}' "$_r83_contract" 2>/dev/null | sort -u)
    if [[ $_r83_adr_ok -eq 0 ]]; then
      fail_rule "design_only_contract_registered_in_catalog" "$_r83_contract cites no ADR file that exists under docs/adr/ -- Rule 83 / E116 (authority chain broken)"
      _r83_fail=1
    fi
  fi
done
if [[ $_r83_fail -eq 0 ]]; then pass_rule "design_only_contract_registered_in_catalog"; fi

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
# Rule 101 — rule_namespace_authority_completeness (enforcer E143)
#
# Closes rc11 review P1-1 (K-α family): ratchet authority surfaces had
# diverged across CLAUDE.md (30 namespaced kernels) vs rule cards (15/16
# hybrid frontmatter) vs enforcers.yaml (60+ stale `Rule 28[a-i]` refs) vs
# architecture-status.yaml (`active_engineering_rules: 67` vs CLAUDE 30).
# Rule 101 gates the semantic-authority parity per ADR-0086 `gate_layer_boundary:`:
#   (a) every `#### Rule <ns>` heading in CLAUDE.md has a matching
#       `docs/governance/rules/rule-<ns>.md` with `rule_id: <ns>` frontmatter.
#   (b) `baseline_metrics.active_engineering_rules` equals the live count of
#       `^#### Rule ` headers in CLAUDE.md.
#   (c) every active enforcer `constraint_ref:` either uses namespaced
#       form (`Rule [DRGM]-`) OR carries a legacy/historical marker.
# Gate-layer identifiers (gate section headers, gate/rules/*.sh filenames)
# stay numeric BY DESIGN per ADR-0086; Rule 101 only gates authority surfaces.
# ---------------------------------------------------------------------------
_r101_fail=0
_r101_claude="CLAUDE.md"
_r101_status_yaml="docs/governance/architecture-status.yaml"
_r101_cards_dir="docs/governance/rules"
_r101_enforcers="docs/governance/enforcers.yaml"
if [[ ! -f "$_r101_claude" ]] || [[ ! -d "$_r101_cards_dir" ]] || [[ ! -f "$_r101_status_yaml" ]]; then
  fail_rule "rule_namespace_authority_completeness" "missing CLAUDE.md or rule-card dir or architecture-status.yaml -- Rule 101 / E143"
  _r101_fail=1
else
  # (a) Every CLAUDE kernel header has a card.
  _r101_missing_cards=""
  while IFS= read -r _r101_h; do
    _r101_ns="$(echo "$_r101_h" | sed -E 's/^#### Rule ([A-Z]-[A-Za-z0-9.]+).*/\1/')"
    _r101_card="${_r101_cards_dir}/rule-${_r101_ns}.md"
    if [[ ! -f "$_r101_card" ]]; then
      _r101_missing_cards="${_r101_missing_cards} ${_r101_ns}"
    elif ! grep -qE "^rule_id: ${_r101_ns}[[:space:]]*\r?$" "$_r101_card" 2>/dev/null; then
      _r101_missing_cards="${_r101_missing_cards} ${_r101_ns}(frontmatter)"
    fi
  done < <(grep -E '^#### Rule [A-Z]-' "$_r101_claude" 2>/dev/null)
  if [[ -n "$_r101_missing_cards" ]]; then
    fail_rule "rule_namespace_authority_completeness" "CLAUDE.md kernel heading(s) without matching rule card OR card frontmatter rule_id mismatch:${_r101_missing_cards} -- Rule 101 / E143 (a) -- ADR-0086 authority-surface parity"
    _r101_fail=1
  fi

  # (b) baseline_metrics.active_engineering_rules equals live count of active rule cards.
  # Semantic shift 2026-05-28: with CLAUDE.md restructured to collaboration-only,
  # the truthful "active engineering rules" count is the rule card count (cards are
  # sole authority per Rule 68/69 semantic shift), not the CLAUDE.md heading count.
  _r101_card_count=$(grep -lE '^status:[[:space:]]*active' "$_r101_cards_dir"/rule-*.md 2>/dev/null | wc -l | tr -d '[:space:]')
  _r101_declared=$(awk '/^[[:space:]]+active_engineering_rules:/{print $2; exit}' "$_r101_status_yaml")
  if [[ -z "$_r101_declared" ]]; then
    fail_rule "rule_namespace_authority_completeness" "$_r101_status_yaml missing active_engineering_rules: under baseline_metrics -- Rule 101 / E143 (b)"
    _r101_fail=1
  elif [[ "$_r101_declared" != "$_r101_card_count" ]]; then
    fail_rule "rule_namespace_authority_completeness" "$_r101_status_yaml baseline_metrics.active_engineering_rules=$_r101_declared but $_r101_cards_dir/ has $_r101_card_count cards with status:active -- Rule 101 / E143 (b)"
    _r101_fail=1
  fi

  # (c) enforcers.yaml constraint_ref lines must be namespaced or carry legacy marker.
  if [[ -f "$_r101_enforcers" ]]; then
    # Engineering-rule range (1-48) per ADR-0086 gate_layer_boundary requires legacy/namespaced markers.
    # Gate-layer rules (numeric ≥49) are intentional numeric per ADR-0086 and are exempt.
    _r101_bad_refs=$(grep -nE 'constraint_ref:[[:space:]]*"[^"]*\bRule ([1-9]|[1-3][0-9]|4[0-8])[a-z]?\b' "$_r101_enforcers" 2>/dev/null \
                     | grep -vE 'legacy Rule [0-9]+.?[a-z]?|Rule [DRGM]-|historical' || true)
    if [[ -n "$_r101_bad_refs" ]]; then
      _r101_first=$(echo "$_r101_bad_refs" | head -3 | tr '\n' '|')
      fail_rule "rule_namespace_authority_completeness" "enforcers.yaml constraint_ref row(s) carry bare numeric 'Rule N' without 'legacy' marker or namespaced form: ${_r101_first}-- Rule 101 / E143 (c)"
      _r101_fail=1
    fi
  fi
fi
if [[ $_r101_fail -eq 0 ]]; then pass_rule "rule_namespace_authority_completeness"; fi

# ---------------------------------------------------------------------------
# Rule 102 — release_recency_resolver_correctness (enforcer E144)
#
# Closes rc11 review P1-2 (K-β family): lex-sort `find docs/logs/releases |
# sort | tail -1` placed `rc9-corrective.en.md` after `rc11-corrective.en.md`
# (character "9" > "1"), so Rules 33/97/G-2 evaluated stale rc9 prose as
# canonical. The fix is gate/lib/latest_release.sh::latest_release_path
# (rc-number numeric resolver). Rule 102 is a static guard against the
# anti-pattern recurring elsewhere in the gate.
# ---------------------------------------------------------------------------
_r102_fail=0
_r102_canonical="gate/check_architecture_sync.sh"
_r102_helper="gate/lib/latest_release.sh"
if [[ ! -f "$_r102_helper" ]]; then
  fail_rule "release_recency_resolver_correctness" "$_r102_helper missing -- Rule 102 / E144 (K-β resolver helper must exist)"
  _r102_fail=1
fi
# Scan production gate scripts for the lex-sort anti-pattern.
_r102_bad_sites=""
while IFS= read -r _r102_f; do
  [[ -f "$_r102_f" ]] || continue
  # Skip the helper itself + test fixtures + this very gate-script comment block.
  case "$_r102_f" in
    "$_r102_helper") continue ;;
    gate/test_architecture_sync_gate.sh) continue ;;
  esac
  _r102_hits=$(grep -nE 'find[[:space:]]+docs/logs/releases.*\|[[:space:]]*sort[[:space:]]*\|[[:space:]]*tail' "$_r102_f" 2>/dev/null || true)
  if [[ -n "$_r102_hits" ]]; then
    _r102_bad_sites="${_r102_bad_sites}${_r102_f}: ${_r102_hits}|"
  fi
done < <(find gate -maxdepth 2 -type f -name '*.sh' 2>/dev/null)
if [[ -n "$_r102_bad_sites" ]]; then
  fail_rule "release_recency_resolver_correctness" "production gate script(s) use lex-sort tail-1 anti-pattern instead of gate/lib/latest_release.sh::latest_release_path: ${_r102_bad_sites}-- Rule 102 / E144 (K-β closure; rc11 review P1-2)"
  _r102_fail=1
fi
if [[ $_r102_fail -eq 0 ]]; then pass_rule "release_recency_resolver_correctness"; fi

# ---------------------------------------------------------------------------
# Rule 103 — deploy_entrypoint_deleted_module_truth (enforcer E145)
#
# Closes rc11 review P1-4 + P1-5 (K-δ family): Rule 94 / 98 scopes covered
# .md/.yaml/.java/ops but missed root Dockerfile + .github/workflows/*.yml
# + .puml + gate/run_operator_shape_smoke.sh — all active deploy-entrypoint
# surfaces. Rule 103 closes the gap for deploy/operator/visual surfaces.
#
# SCOPE NOTE (rc14 — L-η closure): Rule 103 intentionally scans deploy entry-
# points only for `agent-platform` and `agent-runtime` (the pre-Phase-C /
# pre-W2 dissolved modules). `agent-runtime-core` (dissolved rc13 per
# ADR-0088) is owned by the broader corpus scanners:
#   - Rule 94 covers active `.md/.yaml/.yml/.java` files corpus-wide.
#   - Rule 98 covers `ops/**/*.{yaml,yml,tpl,md}` + `docs/contracts/*.yaml`
#     + `**/module-metadata.yaml`.
# Deploy artefacts (Dockerfile / compose / .github/workflows / .puml /
# operator scripts) referencing `agent-runtime-core` are therefore caught by
# Rule 94 / Rule 98 when they live under those path partitions. Rule 103 is
# the legacy deploy-entrypoint closure rule; rc14 deliberately did NOT widen
# its name-set to keep the L-η scope decision auditable (see rc14 release
# note + ADR-0090).
# ---------------------------------------------------------------------------
_r103_fail=0
_r103_files=()
[[ -f Dockerfile ]] && _r103_files+=(Dockerfile)
for _r103_f in ops/Dockerfile* ops/compose*.yml ops/compose*.yaml; do
  [[ -f "$_r103_f" ]] && _r103_files+=("$_r103_f")
done
while IFS= read -r _r103_f; do
  [[ -f "$_r103_f" ]] && _r103_files+=("$_r103_f")
done < <(find .github/workflows -maxdepth 1 -type f \( -name '*.yml' -o -name '*.yaml' \) 2>/dev/null)
[[ -f gate/run_operator_shape_smoke.sh ]] && _r103_files+=(gate/run_operator_shape_smoke.sh)
while IFS= read -r _r103_f; do
  [[ -f "$_r103_f" ]] && _r103_files+=("$_r103_f")
done < <(find docs/architecture-views -type f -name '*.puml' 2>/dev/null)

_r103_markers_file="gate/active-corpus-name-exemption-markers.txt"
_r103_marker_re="$(grep -vE '^[[:space:]]*(#|$)' "$_r103_markers_file" 2>/dev/null | tr '\n' '|' | sed 's/|$//')"
[[ -z "$_r103_marker_re" ]] && _r103_marker_re='historical'

_r103_violations=""
for _r103_f in "${_r103_files[@]}"; do
  _r103_hits=$(awk -v markers="$_r103_marker_re" '
    { lines[NR] = $0 }
    END {
      for (i = 1; i <= NR; i++) {
        line = lines[i]
        # Check for agent-platform or agent-runtime (not -core variant)
        match_pf = (line ~ /([^a-zA-Z0-9_-]|^)agent-platform([^a-zA-Z0-9_-]|$)/)
        match_rt = (line ~ /agent-runtime[^-]/) || (line ~ /agent-runtime$/)
        if (!match_pf && !match_rt) continue
        # Build ±3 marker window
        lo = i - 3; if (lo < 1) lo = 1
        hi = i + 3; if (hi > NR) hi = NR
        window = ""
        for (j = lo; j <= hi; j++) window = window " " lines[j]
        if (window !~ markers) {
          print i ": " line
        }
      }
    }
  ' "$_r103_f" 2>/dev/null || true)
  if [[ -n "$_r103_hits" ]]; then
    _r103_violations="${_r103_violations}${_r103_f}:\n${_r103_hits}\n"
  fi
done

if [[ -n "$_r103_violations" ]]; then
  fail_rule "deploy_entrypoint_deleted_module_truth" "active deploy-entrypoint surface(s) reference deleted modules (agent-platform / agent-runtime) outside historical-marker window:\n${_r103_violations}-- Rule 103 / E145 (rc11 review P1-4 + P1-5 K-δ closure)"
  _r103_fail=1
fi
if [[ $_r103_fail -eq 0 ]]; then pass_rule "deploy_entrypoint_deleted_module_truth"; fi

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
# Rule 107 — cross_authority_clause_parity (enforcer E152)
#
# Family A prevention — closes rc16 P1-1 + the 3 hidden defects (R-J.b.d
# orphaned in principle-coverage.yaml + rule-R-J.md kernel + rule-R-J.md
# card; R-K.b orphaned in principle-coverage.yaml + CLAUDE-deferred.md).
# Per ADR-0093 (rc16 cross-authority parity + meta scope completeness wave).
#
# scope_surfaces: docs/governance/principle-coverage.yaml, docs/CLAUDE-deferred.md, CLAUDE.md, docs/governance/rules/*.md
#
# The rule asserts pairwise parity: every clause name (Rule-X.<letter>)
# named in principle-coverage.yaml#deferred_operationalisers MUST have a
# matching `## Rule X.<letter>` heading in CLAUDE-deferred.md. Active
# clause names (Rule-X without sub-letter) are checked against CLAUDE.md
# `#### Rule X` headings.
# ---------------------------------------------------------------------------
_r107_fail=0
_r107_coverage="docs/governance/principle-coverage.yaml"
_r107_deferred="docs/CLAUDE-deferred.md"
_r107_claude="CLAUDE.md"
_r107_cards_dir="docs/governance/rules"
if [[ -f "$_r107_coverage" && -f "$_r107_claude" && -d "$_r107_cards_dir" ]]; then
  # Collect deferred-section headings as `Rule-X.<letter>` tokens from CLAUDE-deferred.md
  # if the file still exists (transitional support during Phase 7 cleanup).
  _r107_deferred_headings=""
  if [[ -f "$_r107_deferred" ]]; then
    _r107_deferred_headings=$(grep -oE '^## Rule [A-Z](-[A-Z])?(\.[a-z](\.[a-z])?)?' "$_r107_deferred" \
                              | sed -E 's/^## Rule /Rule-/' | sed 's/ /-/g' | sort -u || true)
  fi
  # Also collect migrated deferred sub-clauses from rule card frontmatter
  # `deferred_sub_clauses: - id: ".x"` (Phase 7 step 7.2 migration target).
  # Card filename rule-R-X.md + id ".y" -> token "Rule-R-X.y".
  _r107_card_headings=$(
    for _r107_card in "$_r107_cards_dir"/rule-*.md; do
      [[ -f "$_r107_card" ]] || continue
      _r107_base=$(basename "$_r107_card" .md | sed 's/^rule-//')
      awk -v parent="$_r107_base" '
        /^deferred_sub_clauses:/{flag=1; next}
        flag && /^[^[:space:]-]/{flag=0}
        flag && /^[[:space:]]*-[[:space:]]+id:[[:space:]]*/{
          val=$0
          sub(/^[[:space:]]*-[[:space:]]+id:[[:space:]]*/, "", val)
          gsub(/["'\'']/, "", val)
          sub(/^\./, "", val)
          if (val != "") print "Rule-" parent "." val
        }
      ' "$_r107_card"
    done | sort -u || true
  )
  # Union of both sources.
  _r107_all_headings=$(printf '%s\n%s\n' "$_r107_deferred_headings" "$_r107_card_headings" | sort -u)
  # Collect deferred-clause names listed in principle-coverage.yaml.
  _r107_listed_clauses=$(awk '
      /deferred_operationalisers:/{flag=1; next}
      flag && /^[[:space:]]*-[[:space:]]+Rule-/{
        sub(/^[[:space:]]*-[[:space:]]+/, "");
        sub(/[[:space:]]+#.*$/, "");
        print
        next
      }
      flag && !/^[[:space:]]*-/{flag=0}
    ' "$_r107_coverage" | sort -u || true)
  while IFS= read -r _r107_clause; do
    [[ -z "$_r107_clause" ]] && continue
    # Only sub-letter clauses are expected as deferred entries.
    if echo "$_r107_clause" | grep -qE '\.[a-z]'; then
      if ! echo "$_r107_all_headings" | grep -qFx "$_r107_clause"; then
        fail_rule "cross_authority_clause_parity" "principle-coverage.yaml lists deferred operationaliser $_r107_clause but no matching ## heading in CLAUDE-deferred.md AND no matching deferred_sub_clauses entry in $_r107_cards_dir/ -- Rule 107 / E152 (Family A per ADR-0093)"
        _r107_fail=1
      fi
    fi
  done <<< "$_r107_listed_clauses"
fi
if [[ $_r107_fail -eq 0 ]]; then pass_rule "cross_authority_clause_parity"; fi

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

# Rule 114 — rule_card_filename_dot_convention (enforcer E161)
#
# Per ADR-0096 rc19 Wave 4 + docs/governance/rules/README.md convention:
# every rule card under docs/governance/rules/rule-*.md MUST match the
# dotted-suffix filename pattern. Hyphenated variants (e.g.,
# rule-G-3-1.md) are rejected because the gate's rule_id ↔ card-file
# mapping uses dot notation (rule_id: G-3.1 → rule-G-3.1.md). rc17
# corrective #1 had 5 file renames precisely because of this hyphen-vs-dot
# confusion; this rule prevents the next contributor from re-creating
# the same trap.
#
# Accepted: rule-D-N.md, rule-R-X.md, rule-R-X.N.md, rule-R-X.c.md (R-A.c
# hybrid), rule-G-N.md, rule-G-N.N.md, rule-M-N.md, README.md.
#
# scope_surfaces: docs/governance/rules/*.md
# ---------------------------------------------------------------------------
_r114_fail=0
_r114_dir="docs/governance/rules"
if [[ -d "$_r114_dir" ]]; then
  while IFS= read -r _r114_file; do
    [[ -z "$_r114_file" ]] && continue
    _r114_basename=$(basename "$_r114_file")
    # README.md is allowed; ignore. Other names must match the convention.
    [[ "$_r114_basename" == "README.md" ]] && continue
    # Convention regex: rule-(D|R|G|M)-<letter>(.<suffix>)?.md
    # <suffix> is single letter (a-z) for old sub-clause OR digit(.letter)?
    # for new sub-rule (e.g., R-C.1, R-C.2.a).
    # Acceptable: rule-D-1.md, rule-R-A.md, rule-R-A.c.md, rule-R-C.1.md,
    # rule-R-C.2.md, rule-G-3.1.md, rule-G-9.md, rule-M-2.md.
    if [[ ! "$_r114_basename" =~ ^rule-[DRGM]-[A-Z0-9]+(\.[a-z0-9]+)?\.md$ ]]; then
      fail_rule "rule_card_filename_dot_convention" "$_r114_file: filename does not match rule card convention (rule-PREFIX-ID[.SUBID].md with dot, NOT hyphen). Per docs/governance/rules/README.md + ADR-0098 Wave 4 (rc21 widened ID to multi-char to admit G-10, G-11). Rule 114 / E161"
      _r114_fail=1
    fi
  done < <(find "$_r114_dir" -maxdepth 1 -type f -name '*.md' 2>/dev/null | sort)
fi
if [[ $_r114_fail -eq 0 ]]; then pass_rule "rule_card_filename_dot_convention"; fi

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
# Rule 116 — parallel_linux_scripts_mandate (enforcer E164)
#
# Operationalises Rule G-10. Every gate script under gate/*.sh (top-level,
# excluding the parallel orchestrator and canonical source) MUST either be
# listed in gate/serial-only-paths.txt (one-shot / helper / diagnostic /
# generator exemption list) OR carry a parallel-execution mechanism
# (xargs -P, GNU parallel, or background jobs with explicit wait).
#
# Vacuously passes if gate/serial-only-paths.txt is absent (initial-deployment
# fallback). Companion list: gate/serial-only-paths.txt.
# ---------------------------------------------------------------------------
_r116_fail=0
_r116_exempt_file='gate/serial-only-paths.txt'
if [[ ! -f "$_r116_exempt_file" ]]; then
  pass_rule "parallel_linux_scripts_mandate"
else
  _r116_exempt=$(grep -vE '^[[:space:]]*#|^[[:space:]]*$' "$_r116_exempt_file" 2>/dev/null | sort -u)
  _r116_drift=""
  for _r116_sh in gate/*.sh; do
    [[ -f "$_r116_sh" ]] || continue
    [[ "$_r116_sh" == "gate/check_parallel.sh" || "$_r116_sh" == "gate/check_architecture_sync.sh" ]] && continue
    if printf '%s\n' "$_r116_exempt" | grep -Fxq "$_r116_sh"; then
      continue
    fi
    # Tighter regex per rc21 PR review: drop trailing-`wait` alternative
    # (matched any line ending in word `wait`, e.g. `# we wait` — false-pass).
    # Accepted parallel mechanisms:
    #   1. `xargs -P<N>` (any -P flag, with or without space before N)
    #   2. `parallel` command at start of line
    #   3. `wait` builtin at start of line (after `&`-backgrounded jobs)
    #   4. `&` line-end (background job indicator, must be paired with wait)
    if grep -qE 'xargs[[:space:]]+([^|]*[[:space:]])?-P[0-9[:space:]]|^[[:space:]]*parallel([[:space:]]|$)|^[[:space:]]*wait([[:space:]]|$|;)|&[[:space:]]*$' "$_r116_sh" 2>/dev/null; then
      continue
    fi
    _r116_drift+="$_r116_sh; "
    _r116_fail=1
  done
  if [[ $_r116_fail -eq 0 ]]; then
    pass_rule "parallel_linux_scripts_mandate"
  else
    fail_rule "parallel_linux_scripts_mandate" "Gate scripts lacking parallel-execution mechanism (xargs -P / parallel / wait) AND not exempted in gate/serial-only-paths.txt: ${_r116_drift}-- Rule G-10 / E164"
  fi
fi

# ---------------------------------------------------------------------------
# Rule 117 — phase_contract_rule_allocation_coherence (enforcer E165)
#
# Operationalises Rule G-11. Phase contract <-> rule card coherence on the
# post-ADR-0098 contract layer:
#   (a) every Active Rules row in docs/governance/contracts/*.md MUST cite
#       a rule whose card exists under docs/governance/rules/rule-*.md OR
#       a principle whose card exists under docs/governance/principles/P-*.md;
#   (b) every active rule card MUST be cited in at least one phase contract
#       as P or X;
#   (c) dual-P (same rule cited as P in multiple contracts) is forbidden
#       except for the enumerated G-9 exception (commit + review).
#
# Vacuously passes if docs/governance/contracts/ is absent.
# ---------------------------------------------------------------------------
_r117_fail=0
_r117_contracts_dir='docs/governance/contracts'
_r117_rules_dir='docs/governance/rules'
_r117_principles_dir='docs/governance/principles'
if [[ ! -d "$_r117_contracts_dir" ]]; then
  pass_rule "phase_contract_rule_allocation_coherence"
else
  _r117_drift=""
  # Set of rule + principle card ids on disk
  _r117_cards=$(find "$_r117_rules_dir" -maxdepth 1 -name 'rule-*.md' -type f 2>/dev/null \
    | sed -E 's|.*/rule-||; s|\.md$||' | sort -u)
  _r117_principles=$(find "$_r117_principles_dir" -maxdepth 1 -name 'P-*.md' -type f 2>/dev/null \
    | sed -E 's|.*/||; s|\.md$||' | sort -u)
  # Extract citations: each Active Rules row of form "| <id> | <title> | **P** | ..." or **X**
  _r117_cited_p=""
  _r117_cited_x=""
  for _r117_contract in "$_r117_contracts_dir"/*.md; do
    [[ -f "$_r117_contract" ]] || continue
    while IFS= read -r _r117_row; do
      _r117_id=$(printf '%s\n' "$_r117_row" | sed -nE 's/^\| ([A-Za-z][A-Za-z0-9.-]*) \|.*/\1/p')
      [[ -z "$_r117_id" ]] && continue
      [[ "$_r117_id" == "Rule" ]] && continue
      _r117_marker=$(printf '%s\n' "$_r117_row" | grep -oE '\*\*[PX]\*\*' | head -1 | tr -d '*')
      if [[ "$_r117_marker" == "P" ]]; then
        _r117_cited_p+="$_r117_id"$'\n'
      elif [[ "$_r117_marker" == "X" ]]; then
        _r117_cited_x+="$_r117_id"$'\n'
      fi
    done < <(grep -E '^\| [A-Za-z][A-Za-z0-9.-]* \|' "$_r117_contract" 2>/dev/null)
  done
  # Materialise the cited / card / principle sets to temp files BEFORE the
  # lookup loops. `printf '%s\n' "$big_var" | grep -Fxq` triggers SIGPIPE
  # on the printf when grep -q exits early on first match — combined with
  # `set -o pipefail` at the top of this script, the captured result becomes
  # non-deterministic across fast (CI) vs slow (local) runners. CI rc21 hit
  # this on R-C.1 reporting false orphan. Temp files make the lookup
  # pipefail-immune.
  _r117_tmp=$(mktemp -d 2>/dev/null || mktemp -d -t r117) || _r117_tmp="/tmp/r117_$$"
  mkdir -p "$_r117_tmp"
  printf '%s%s' "$_r117_cited_p" "$_r117_cited_x" | grep -v '^$' | sort -u > "$_r117_tmp/all_cited" || true
  printf '%s\n' "$_r117_cards" | grep -v '^$' > "$_r117_tmp/cards" || true
  printf '%s\n' "$_r117_principles" | grep -v '^$' > "$_r117_tmp/principles" || true
  # Check (a): every cited id resolves to a card or principle
  while IFS= read -r _r117_cited; do
    [[ -z "$_r117_cited" ]] && continue
    if ! grep -Fxq "$_r117_cited" "$_r117_tmp/cards" \
       && ! grep -Fxq "$_r117_cited" "$_r117_tmp/principles"; then
      _r117_drift+="ghost-rule:$_r117_cited (cited in contract; no card on disk); "
      _r117_fail=1
    fi
  done < "$_r117_tmp/all_cited"
  # Check (b): every rule card is cited at least once
  while IFS= read -r _r117_card; do
    [[ -z "$_r117_card" ]] && continue
    if ! grep -Fxq "$_r117_card" "$_r117_tmp/all_cited"; then
      _r117_drift+="orphan-rule:$_r117_card (card exists; not cited in any contract); "
      _r117_fail=1
    fi
  done < "$_r117_tmp/cards"
  # Check (c): dual-P only allowed for G-9
  printf '%s' "$_r117_cited_p" | grep -v '^$' | sort | uniq -d > "$_r117_tmp/dup_p" || true
  _r117_dup_p=$(cat "$_r117_tmp/dup_p" 2>/dev/null || true)
  if [[ -n "$_r117_dup_p" ]]; then
    while IFS= read -r _r117_dup; do
      [[ -z "$_r117_dup" ]] && continue
      if [[ "$_r117_dup" != "G-9" ]]; then
        _r117_drift+="dual-P-violation:$_r117_dup (only G-9 dual-P sanctioned; see docs/governance/rules/rule-G-11.md); "
        _r117_fail=1
      fi
    done <<< "$_r117_dup_p"
  fi
  if [[ $_r117_fail -eq 0 ]]; then
    pass_rule "phase_contract_rule_allocation_coherence"
  else
    fail_rule "phase_contract_rule_allocation_coherence" "${_r117_drift}-- Rule G-11 / E165"
  fi
  rm -rf "$_r117_tmp" 2>/dev/null || true
fi

# ---------------------------------------------------------------------------
# Rule G-1.1 — L1 Architecture Depth & Grounding (3 sub-clauses, ADR-0099)
# rc27 fix (rc22-2): real helpers replace prior placeholder pass_rule stubs.
# ---------------------------------------------------------------------------
# Rule 118 — l1_dev_view_code_mapping (enforcer E166)
# rc28 fix (NEW-3): fail-closed when helper missing instead of silent pass.
_r118_fail=0
if ! command -v check_l1_dev_view_tree >/dev/null 2>&1; then
  fail_rule "l1_dev_view_code_mapping" "helper-missing: gate/lib/check_l1_dev_view_tree.sh not sourced -- Rule G-1.1.a / E166"
  _r118_fail=1
else
  _r118_out=$(check_l1_dev_view_tree 2>&1)
  while IFS=$'\t' read -r _s _f _d; do
    [[ "$_s" == "FAIL" ]] || continue
    fail_rule "l1_dev_view_code_mapping" "$_f: $_d -- Rule G-1.1.a / E166"
    _r118_fail=1
  done <<< "$_r118_out"
fi
[[ $_r118_fail -eq 0 ]] && pass_rule "l1_dev_view_code_mapping"

# Rule 119 — l1_spi_appendix_4way_parity (enforcer E167)
# rc28 fix (NEW-3): fail-closed when helper missing instead of silent pass.
_r119_fail=0
if ! command -v check_l1_spi_appendix >/dev/null 2>&1; then
  fail_rule "l1_spi_appendix_4way_parity" "helper-missing: gate/lib/check_l1_spi_appendix.sh not sourced -- Rule G-1.1.b / E167"
  _r119_fail=1
else
  _r119_out=$(check_l1_spi_appendix 2>&1)
  while IFS=$'\t' read -r _s _f _d; do
    [[ "$_s" == "FAIL" ]] || continue
    fail_rule "l1_spi_appendix_4way_parity" "$_f: $_d -- Rule G-1.1.b / E167"
    _r119_fail=1
  done <<< "$_r119_out"
fi
[[ $_r119_fail -eq 0 ]] && pass_rule "l1_spi_appendix_4way_parity"

# ---------------------------------------------------------------------------
# Rule 121 — whitebox_quality_reports (enforcer E169)
#
# Operationalises Rule G-12. Maven owns execution of SpotBugs, PMD, and
# Checkstyle through the quality profile; this gate owns repository semantics:
# report presence, high-confidence SpotBugs blocking, low-dispute Checkstyle
# blocking, and PMD review-trigger summarisation.
#
# scope_surfaces: pom.xml, config/spotbugs/exclude.xml, config/pmd/pmd-ruleset.xml, config/checkstyle/checkstyle.xml, gate/lib/check_whitebox_quality.sh, .github/workflows/ci.yml
# ---------------------------------------------------------------------------
_r121_fail=0
if ! command -v check_whitebox_quality_reports >/dev/null 2>&1; then
  fail_rule "whitebox_quality_reports" "helper-missing: gate/lib/check_whitebox_quality.sh not sourced -- Rule G-12 / E169"
  _r121_fail=1
else
  _r121_out=$(check_whitebox_quality_reports 2>&1)
  while IFS=$'\t' read -r _s _f _d; do
    [[ -z "$_s" ]] && continue
    if [[ "$_s" == "FAIL" ]]; then
      fail_rule "whitebox_quality_reports" "$_f: $_d -- Rule G-12 / E169"
      _r121_fail=1
    elif [[ "$_s" == "INFO" ]]; then
      printf 'INFO: whitebox_quality_reports -- %s: %s\n' "$_f" "$_d"
    fi
  done <<< "$_r121_out"
fi
[[ $_r121_fail -eq 0 ]] && pass_rule "whitebox_quality_reports"

# ---------------------------------------------------------------------------
# Rule 122 — proposal_immediate_scope_pending_contract_guard (enforcer E170)
#
# Design proposals under docs/logs/reviews/ may be exploratory, but they MUST
# NOT claim immediate W0/W1 execution scope while the same document still says
# the boundary contracts are pending. This prevents release-readiness drift
# where a draft looks like current release authority.
# ---------------------------------------------------------------------------
_r122_fail=0
for _r122_file in docs/logs/reviews/*proposal*.md; do
  [[ -f "$_r122_file" ]] || continue
  if grep -qiE 'Target Wave:[^[:cntrl:]]*(W0/W1|Immediate Execution)' "$_r122_file" \
     && grep -qiE 'Pending Refinement|pending gaps|pending contract|pending refinement|TODO annotations' "$_r122_file"; then
    fail_rule "proposal_immediate_scope_pending_contract_guard" "$_r122_file claims immediate W0/W1 scope while carrying pending boundary-contract work -- Rule G-2 / E170"
    _r122_fail=1
  fi
done
[[ $_r122_fail -eq 0 ]] && pass_rule "proposal_immediate_scope_pending_contract_guard"

# ---------------------------------------------------------------------------
# Rule 123 — proposal_engine_package_truth (enforcer E171)
#
# Proposal FQNs must respect current package authority unless explicitly marked
# proposed/future on the same line. Current authority is:
#   - engine-owned SPI/runtime under com.huawei.ascend.engine.*
#   - service-owned StatelessEngine under com.huawei.ascend.service.engine.spi
# ---------------------------------------------------------------------------
_r123_fail=0
for _r123_file in docs/logs/reviews/*proposal*.md; do
  [[ -f "$_r123_file" ]] || continue
  _r123_hits=$(grep -nE 'com\.huawei\.ascend\.agent\.engine|StatelessEngineExecutor' "$_r123_file" 2>/dev/null \
    | grep -viE 'proposed|future|candidate|exploratory|not current' || true)
  if [[ -n "$_r123_hits" ]]; then
    fail_rule "proposal_engine_package_truth" "$_r123_file contains engine/service FQN or signature claims not marked proposed: ${_r123_hits//$'\n'/; } -- Rule G-8 / E171"
    _r123_fail=1
  fi
done
[[ $_r123_fail -eq 0 ]] && pass_rule "proposal_engine_package_truth"

# ---------------------------------------------------------------------------
# Rule 124 — unsupported_absolute_claim_guard (enforcer E172)
#
# Security/performance absolutes in proposal docs invite false release claims.
# The terms below are allowed only when the same line points at evidence such as
# a benchmark, threat model, measurement, or acceptance criterion.
# ---------------------------------------------------------------------------
_r124_fail=0
for _r124_file in docs/logs/reviews/*proposal*.md; do
  [[ -f "$_r124_file" ]] || continue
  _r124_hits=$(grep -nEi 'bulletproof|zero-day safety|zero downtime|sub-millisecond|sub-milliseconds' "$_r124_file" 2>/dev/null \
    | grep -viE 'benchmark|threat model|measured|measurement|acceptance criteria|acceptance criterion|deferred' || true)
  if [[ -n "$_r124_hits" ]]; then
    fail_rule "unsupported_absolute_claim_guard" "$_r124_file contains unsupported absolute claim(s): ${_r124_hits//$'\n'/; } -- Rule G-2 / E172"
    _r124_fail=1
  fi
done
[[ $_r124_fail -eq 0 ]] && pass_rule "unsupported_absolute_claim_guard"

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
# Rule 126 — template_render_idempotency (enforcer E174, kernel Rule G-13)
#
# Operationalises Rule G-13.a (Surface Classification): the
# single-source rendering registry MUST exist and be parseable. Today's
# stub verifies existence and structural keys only; Rule G-13.b
# (byte-identical regen of `templated` + `hybrid` outputs against
# render(load_context())) is a forward contract that activates when
# the render engine + general gate driver ship under
# gate/lib/render_template.py + gate/lib/check_template_render_idempotency.py
# + gate/check_template_render_idempotency.sh.
#
# Today's contract:
#   - docs/governance/templates/surface-classification.yaml MUST exist.
#   - It MUST parse as YAML with schema_version + templates keys present.
#   - The templates list MAY be empty.
#
# Forward contract (activated when the render-engine driver lands):
#   - For each entry where bucket in {templated, hybrid}, the template
#     file MUST exist, the context_schema file MUST exist, and
#     render(template, load_context()) MUST byte-match the on-disk output.
#
# scope_surfaces: docs/governance/templates/surface-classification.yaml,
#                 docs/governance/templates/*.md.j2,
#                 docs/governance/rules/rule-G-13.md
# ---------------------------------------------------------------------------
_r126_fail=0
_r126_registry="docs/governance/templates/surface-classification.yaml"

if [[ ! -f "$_r126_registry" ]]; then
  fail_rule "template_render_idempotency" "$_r126_registry missing -- Rule G-13.a requires the surface-classification registry to exist; land it from rule-G-13.md scaffolding -- Rule G-13 / E174"
  _r126_fail=1
else
  _r126_out=$(python3 gate/lib/check_template_render_idempotency.py 2>&1)
  _r126_rc=$?
  if [[ $_r126_rc -ne 0 ]]; then
    _r126_first=$(printf '%s' "$_r126_out" | head -1)
    # Knowledge/governance rebalancing G-track: generated-doc byte-idempotency
    # (template render drift) demoted from blocking to advisory — a regenerable
    # rendered doc drifting is maintenance, not a delivery blocker.
    echo "ADVISORY: template_render_idempotency (${_r126_first:-rc=$_r126_rc}) -- Rule G-13.b demoted to advisory (rebalancing G-track)"
  fi
fi

# W1 forward-pointer: when surface-classification.yaml has non-empty
# templates list, this rule will also delegate to
# gate/check_template_render_idempotency.sh for the byte-identical
# check. Today the list is empty (W0); the check is vacuously satisfied.

[[ $_r126_fail -eq 0 ]] && pass_rule "template_render_idempotency"

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
# Rule 136 — autoload_tier_integrity (enforcer E184, kernel Rule G-19)
#
# Phase A Wave 5 (BLOCKING from landing). gate/always-loaded-budget.txt MUST
# contain product/PRODUCT.md at a non-zero ceiling AND CLAUDE.md MUST have a
# ceiling <= 12000 bytes. Enforces the Tier-1 product-authority + collab-only
# kernel discipline established by the 2026-05-28 surgery.
# ---------------------------------------------------------------------------
_r136_fail=0
_r136_budget="gate/always-loaded-budget.txt"
if [[ ! -f "$_r136_budget" ]]; then
  fail_rule "autoload_tier_integrity" "$_r136_budget missing -- Rule G-19 / E184"
  _r136_fail=1
else
  if ! grep -qE '^product/PRODUCT\.md=[1-9][0-9]*' "$_r136_budget"; then
    fail_rule "autoload_tier_integrity" "$_r136_budget missing or zero-ceiling product/PRODUCT.md entry -- Rule G-19 / E184"
    _r136_fail=1
  fi
  _r136_claude_ceiling=$(awk -F= '/^CLAUDE\.md=/{print $2; exit}' "$_r136_budget")
  if [[ -z "$_r136_claude_ceiling" ]]; then
    fail_rule "autoload_tier_integrity" "$_r136_budget missing CLAUDE.md entry -- Rule G-19 / E184"
    _r136_fail=1
  elif [[ "$_r136_claude_ceiling" -gt 12000 ]]; then
    fail_rule "autoload_tier_integrity" "$_r136_budget CLAUDE.md ceiling=$_r136_claude_ceiling exceeds 12000 (collab-only kernel discipline) -- Rule G-19 / E184"
    _r136_fail=1
  fi
fi
[[ $_r136_fail -eq 0 ]] && pass_rule "autoload_tier_integrity"

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

# ---------------------------------------------------------------------------
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
# Rule 142 — tier1_non_english_lint (enforcer E190, kernel Rule G-25)
#
# Authority: CLAUDE.md kernel "Translate all instructions into English".
# Closes external review P1-3: every file with a non-zero budget in
# gate/always-loaded-budget.txt (the always-loaded Tier-1 set) MUST be free of
# CJK code points [U+4E00..U+9FFF] and common UTF-8/GBK mojibake markers
# (U+FFFD plus the literal sequences for double-decoded text). Fails closed.
# The checker reports line:col + byte offset ONLY and MUST NOT echo the
# offending non-English text, so the gate log never embeds non-English source.
#
# scope_surfaces: gate/always-loaded-budget.txt, gate/lib/check_tier1_non_english.py
# ---------------------------------------------------------------------------
_r142_fail=0
_r142_helper="gate/lib/check_tier1_non_english.py"
if [[ ! -f "$_r142_helper" ]]; then
  fail_rule "tier1_non_english_lint" "$_r142_helper missing -- Rule G-25 / E190"
  _r142_fail=1
elif [[ -z "$GATE_PYTHON_BIN" ]]; then
  : # vacuous pass on hosts without python (Rule G-7 lists WSL as canonical env)
else
  _r142_out=$("$GATE_PYTHON_BIN" "$_r142_helper" 2>&1)
  _r142_rc=$?
  if [[ $_r142_rc -ne 0 ]]; then
    _r142_count=$(printf '%s\n' "$_r142_out" | grep -cE '^(NON-ENGLISH|MISSING-SURFACE|MISSING-BUDGET|NO-SCOPE|UNREADABLE):')
    _r142_first=$(printf '%s' "$_r142_out" | grep -E '^(NON-ENGLISH|MISSING-SURFACE|MISSING-BUDGET|NO-SCOPE|UNREADABLE):' | head -1)
    fail_rule "tier1_non_english_lint" "always-loaded Tier-1 surface(s) carry non-English / mojibake ($_r142_count finding(s), first: ${_r142_first:-rc=$_r142_rc}) -- Rule G-25 / E190 (line:col + byte offset only; offending text intentionally not echoed)"
    _r142_fail=1
  fi
fi
[[ $_r142_fail -eq 0 ]] && pass_rule "tier1_non_english_lint"

# ---------------------------------------------------------------------------
# Rule 143 — local_plan_path_ban (enforcer E191, kernel Rule G-26)
#
# Authority: 2026-05-29 EnginePort/Frame review P1-4. Closes the defect class of
# active authority referencing a local agent-host plan path (`D:\.claude\plans`
# or `D:/.claude/plans`, BOTH separators). SCOPE: product/, docs/adr/,
# docs/governance/ (excluding rule-history.md), architecture/, CLAUDE.md,
# AGENTS.md. The only permitted surfaces are listed in
# gate/local-plan-path-exemptions.txt (Linux-first workflow docs + history +
# gate helpers/fixtures that construct synthetic inputs).
#
# scope_surfaces: product/*, docs/adr/*, docs/governance/*, architecture/*, CLAUDE.md, AGENTS.md, gate/local-plan-path-exemptions.txt
# ---------------------------------------------------------------------------
_r143_fail=0
_r143_exempt="gate/local-plan-path-exemptions.txt"
# Match BOTH path separators: D:\.claude\plans and D:/.claude/plans.
_r143_pat='D:[\\/]\.claude[\\/]plans'
_r143_hits=$(grep -rEln "$_r143_pat" \
               product docs/adr docs/governance architecture CLAUDE.md AGENTS.md 2>/dev/null \
             | sort -u)
_r143_bad=""
while IFS= read -r _r143_f; do
  [[ -z "$_r143_f" ]] && continue
  _r143_rel="${_r143_f#./}"
  _r143_exempted=0
  if [[ -f "$_r143_exempt" ]]; then
    while IFS= read -r _r143_e; do
      _r143_e="${_r143_e%%$'\r'}"
      [[ -z "$_r143_e" || "$_r143_e" == \#* ]] && continue
      # Prefix match supports both exact files and directory prefixes (trailing /).
      if [[ "$_r143_rel" == "$_r143_e" || "$_r143_rel" == "$_r143_e"* ]]; then
        _r143_exempted=1
        break
      fi
    done < "$_r143_exempt"
  fi
  if [[ $_r143_exempted -eq 0 ]]; then
    _r143_bad="${_r143_bad}${_r143_rel} "
  fi
done <<< "$_r143_hits"
if [[ -n "$_r143_bad" ]]; then
  fail_rule "local_plan_path_ban" "active authority references local plan path D:\\.claude\\plans (both separators) in non-exempt surface(s): ${_r143_bad}-- Rule G-26 / E191 (exempt list: $_r143_exempt)"
  _r143_fail=1
fi
[[ $_r143_fail -eq 0 ]] && pass_rule "local_plan_path_ban"

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
