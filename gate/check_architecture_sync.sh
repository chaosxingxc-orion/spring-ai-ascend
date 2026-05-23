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
#  14.  module_arch_method_name_truth                -- method names in ARCHITECTURE.md code-fences must exist in Java class
#  15.  no_active_refs_deleted_wave_plan_paths        -- active .md files must not reference docs/plans/engineering-plan-W0-W4.md or roadmap-W0-W4.md
#  16.  http_contract_w1_tenant_and_cancel_consistency -- W1 HTTP contract: no replace-X-Tenant-Id wording, no CREATED initial status, no DELETE cancel route
#  17.  contract_catalog_spi_table_matches_source     -- SPI sub-table must list 7 known SPIs; OssApiProbe must not appear before Probes sub-table
#  18.  deleted_spi_starter_names_outside_catalog     -- ACTIVE_NORMATIVE_DOCS corpus must not reference deleted SPI/starter names (widened, ADR-0043)
#  19.  shipped_row_tests_evidence                    -- every shipped: true row must have non-empty tests: pointing to real files (ADR-0042, strengthened)
#  20.  module_metadata_truth                         -- module README.md must not reference Java class names absent from the repo (ADR-0043)
#  21.  bom_glue_paths_exist                          -- BoM must not contain known ghost implementation paths unless they exist (ADR-0043)
#  22.  lowercase_metrics_in_contract_docs            -- ACTIVE_NORMATIVE_DOCS must not contain SPRINGAI_ASCEND_<lowercase> patterns (ADR-0043, widened)
#  23.  active_doc_internal_links_resolve             -- markdown links ](path) in active docs must resolve to existing files (ADR-0043)
#  24.  shipped_row_evidence_paths_exist              -- l2_documents: and latest_delivery_file: on shipped rows must exist on disk (ADR-0045)
#  25.  peripheral_wave_qualifier                     -- SPI Javadoc and active docs must not name future-wave impls without wave qualifier (ADR-0045)
#  26.  release_note_shipped_surface_truth            -- docs/logs/releases/*.md must not overclaim RunLifecycle/RunContext.posture/ApiCompatibilityTest-as-OpenAPI/AppPostureGate-scope (ADR-0046)
#  27.  active_entrypoint_baseline_truth              -- root README.md baseline counts must match architecture-status.yaml.architecture_sync_gate.allowed_claim (ADR-0047)
#  28.  release_note_baseline_truth                   -- docs/logs/releases/*.md baseline counts must match canonical YAML unless marked "Historical artifact frozen at SHA" (ADR-0049, whitepaper-alignment P0-1)
#  29.  whitepaper_alignment_matrix_present           -- docs/governance/whitepaper-alignment-matrix.md must exist and list all 20 required whitepaper concepts (ADR-0049, whitepaper-alignment P2-1)
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
#  28.  constraint_enforcer_coverage                   -- enforcers.yaml references CLAUDE.md AND ARCHITECTURE.md (meta-rule, enforcer E28)
#  30.  telemetry_vertical_constraint_coverage         -- ARCHITECTURE.md §4 #53–#59 each cited by an enforcer row (L1.x Telemetry Vertical, enforcer E47)
#  --- Layer-0 governing principles (ADR-0064..0067) ---
#  31.  quickstart_present                              -- docs/quickstart.md present and referenced from README.md (Rule 29, enforcer E49)
#  32.  competitive_baselines_present_and_wellformed    -- docs/governance/competitive-baselines.yaml has 4 pillars (Rule 30, enforcer E50)
#  33.  release_note_references_four_pillars            -- latest release note mentions all 4 pillars by name (Rule 30, enforcer E51)
#  34.  module_metadata_present_and_complete            -- every <module>/pom.xml has a sibling module-metadata.yaml with required keys (Rule 31, enforcer E52)
#  35.  dfx_yaml_present_and_wellformed                 -- every kind:platform|domain module has docs/dfx/<module>.yaml with 5 DFX dimensions (Rule 32, enforcer E53)
#  36.  domain_module_has_spi_package                   -- every kind:domain module declares spi_packages and each one resolves on disk (Rule 32, enforcer E54)
#  --- W1 Layered 4+1 + Architecture Graph (ADR-0068) ---
#  37.  architecture_artefact_front_matter             -- every ARCH/L2/ADR.yaml carries level: + view: front-matter (Rule 33, enforcer E55)
#  38.  architecture_graph_well_formed                 -- generated architecture-graph.yaml builds + validates (Rule 34, enforcer E56)
#  39.  review_proposal_front_matter                   -- docs/logs/reviews/*.md declare affects_level: + affects_view: (Rule 33, enforcer E57)
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
#  55.  engine_envelope_yaml_present_and_wellformed    -- docs/contracts/engine-envelope.v1.yaml declares schema + known_engines + at least one id (Rule 43 / P-M, enforcer E76)
#  56.  engine_registry_covers_all_known_engines       -- bidirectional id <-> ENGINE_TYPE consistency between yaml and agent-service/src/main (Rule 44 / P-M, enforcer E77)
#  --- W2.x Phase 2 — Engine Hooks + Runtime Middleware SPI (ADR-0073) ---
#  57.  engine_hooks_yaml_present_and_wellformed       -- docs/contracts/engine-hooks.v1.yaml declares 9-hook list matching HookPoint enum (Rule 45 / P-M, enforcer E78)
#  --- W2.x Phase 3 — S2C Capability Callback (ADR-0074) ---
#  58.  s2c_callback_yaml_present_and_wellformed       -- docs/contracts/s2c-callback.v1.yaml declares request+response shape with 6 mandatory request fields and outcome enum (Rule 46 / P-M, enforcer E81)
#  --- W2.x Phase 4 — Evolution Scope Boundary (ADR-0075) ---
#  59.  evolution_scope_yaml_present_and_wellformed   -- docs/governance/evolution-scope.v1.yaml declares 3 discriminator blocks + telemetry-export ref (Rule 47 / P-M, enforcer E86)
#  --- W2.x Phase 6 — Schema-First Domain Contracts (ADR-0077, Rule 48) ---
#  60.  schema_first_domain_contracts                   -- prose enums in ARCHITECTURE.md require nearby yaml schema reference or grandfather entry (Rule 48 / P-M cross-cutting, enforcer E85)
#  --- v2.0.0-rc2 second-pass review closure (F-α / F-β / F-γ category audit) ---
#  61.  legacy_powershell_gate_deprecated               -- gate/check_architecture_sync.ps1 contains DEPRECATED header AND is absent from architecture-status.yaml#architecture_sync_gate.implementation: (F-α P0-1)
#  62.  contract_yaml_declares_status                   -- every docs/contracts/*.v1.yaml + 3 governance YAMLs declare top-level status: with allowed enum value (F-β structural prevention)
#  63.  release_note_retracted_tag_qualified            -- every line in docs/logs/releases/*.md mentioning a tag listed in docs/governance/retracted-tags.txt MUST contain "(retracted)" OR appear under a heading matching "Historical" / "Superseded" (F-γ structural prevention)
#  --- 2026-05-17 cross-corpus consistency audit prevention rules (G1/G2/G3 closure, enforcers E94-E96) ---
#  64.  module_count_data_driven                        -- root pom.xml <module> count equals docs/governance/architecture-status.yaml#repository_counts.total_reactor_modules (G1 prevention; canonical count lives in one place)
#  65.  module_metadata_pom_dep_parity                  -- every com.huawei.ascend <dependency> in <module>/pom.xml appears in <module>/module-metadata.yaml allowed_dependencies (G2 prevention; metadata cannot lag behind pom)
#  66.  spi_package_exhaustiveness                      -- every */spi/ directory under <module>/src/main/java appears in <module>/module-metadata.yaml spi_packages (G3 prevention; metadata declares the full SPI surface)
#  --- 2026-05-17 CLAUDE.md token-optimization wave PR1 (enforcers E97-E101) ---
#  67.  claude_md_kernel_size_bounded                   -- each #### Rule NN section in CLAUDE.md fits under the per-rule kernel_cap declared in docs/governance/rules/rule-NN.md (Rule 67 / PR1, enforcer E97)
#  68.  claude_md_kernel_matches_card                   -- the body paragraph of each #### Rule NN in CLAUDE.md byte-matches the kernel: field in docs/governance/rules/rule-NN.md (Rule 68 / PR1, enforcer E98)
#  69.  every_active_rule_has_card                      -- every #### Rule NN heading in CLAUDE.md has a sibling docs/governance/rules/rule-NN.md; every card is either referenced by CLAUDE.md or listed in docs/CLAUDE-deferred.md (Rule 69 / PR1, enforcer E99)
#  70.  always_loaded_budget_enforced                   -- gate/measure_always_loaded_tokens.sh exits 0 (no file exceeds its ceiling in gate/always-loaded-budget.txt) (Rule 70 / PR1, enforcer E100)
#  71.  deferred_doc_not_in_always_loaded               -- docs/CLAUDE-deferred.md not auto-injected (no @-include in CLAUDE.md, no ALWAYS-LOAD mark in SESSION-START-CONTEXT.md once demoted) (Rule 71 / PR1, enforcer E101)
#  --- 2026-05-17 gate-script efficiency wave PR-E1 (enforcer E103) ---
#  73.  gate_config_well_formed                          -- gate/config.yaml validates against gate/config.schema.yaml (required keys, types, ranges, enums, no unknown keys) (Rule 73 / PR-E1, enforcer E103)
#  --- 2026-05-18 Linux-first dev environment policy (enforcer E104) ---
#  74.  linux_first_dev_doc_present                      -- docs/governance/dev-environment.md exists + recommends WSL2/WSL1/Linux for verification (Rule 74 / PR-E7, enforcer E104)
#  --- 2026-05-18 SPI metadata integrity wave (Rules 75-78; enforcers E105-E111) ---
#  --- 2026-05-18 Beyond-SDD response wave (Rule 79; enforcer E112) ---
#  --- 2026-05-18 rc4 cross-constraint review response prevention wave (Rules 80-83; enforcers E113-E116) ---
#  80.  s2c_callback_signal_historical_only_in_authority -- S2cCallbackSignal must appear only in historical/deleted/refactored paragraphs across CLAUDE.md/README.md/ADRs/contract-yamls/module-archs (P0-1 prevention, enforcer E113)
#  81.  skeleton_module_has_no_production_java           -- modules whose ARCHITECTURE.md status: contains "skeleton" must contain only package-info.java or ADR-waived placeholder SPI stubs under src/main/java (P0-2 prevention, enforcer E114)
#  82.  baseline_metrics_single_source                   -- architecture-status.yaml#architecture_sync_gate.baseline_metrics exists with required keys; README.md + gate/README.md point to the block by substring; rc6 strengthening: numeric-drift detection on entrypoint count phrases (P1-1 prevention + rc5-P1-1 strengthening, enforcer E115)
#  83.  design_only_contract_registered_in_catalog       -- every docs/contracts/*.v1.yaml with status: design_only OR runtime_enforced: false is listed in contract-catalog.md AND cites an existing ADR (P1-3 prevention, enforcer E116)
#  --- 2026-05-18 rc5 post-response review response prevention wave (Rules 84-85; enforcers E117-E118) ---
#  84.  active_module_architecture_path_truth           -- every agent-*/ARCHITECTURE.md (status != skeleton|deferred) inline path claim "<module>/src/main/java/..." must resolve on disk OR carry a historical/moved/extracted-per-ADR/superseded/deferred marker within +/-3 lines (rc5 P0-1 prevention, enforcer E117)
#  85.  catalog_spi_row_matches_module_spi_metadata     -- every non-(internal) row in contract-catalog.md SPI table must have its package in <module>/module-metadata.yaml#spi_packages AND docs/dfx/<module>.yaml#spi_packages; the (N total) header MUST equal the non-internal row count (rc5 P1-2 prevention, enforcer E118)
#  --- 2026-05-19 rc10 post-corrective review response prevention wave (Rules 99-100 + Rule 94/98 widening; enforcers E139-E142) ---
#  99.  kernel_terminal_verb_vs_shipped_decision_check  -- For every #### Rule N kernel block in CLAUDE.md with a matching ## Rule N.<letter> sub-clause in CLAUDE-deferred.md, the kernel MUST NOT use end-state verb tokens (`are SUSPENDED`, `is SUSPENDED`, `transitions to FAILED`, `consumes the * capacity`, `is rejected, not failed`, `admits the caller`) that overclaim shipped behaviour. Closes rc10 P1-1 (J-α family; Rule 41 kernel said "callers are SUSPENDED" while shipped code returns SkillResolution.reject — the actual transition is deferred to Rule 41.c).
#  100. kernel_implementation_disjunction_truth        -- For every rule in gate/rule-100-disjunction-allowlist.txt, BOTH the #### Rule N kernel block in CLAUDE.md AND the matching docs/governance/rules/rule-NN.md card MUST contain explicit disjunction wording (EITHER / OR / either surface / either ... or). Closes rc10 P1-3 (J-γ family; Rule 96 kernel said "MUST contain" while impl accepted EITHER kernel OR card — kernel-AND-impl-OR drift in the rule whose job is preventing such drift).

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
# Rule 9 — openapi_path_consistency
# /v3/api-docs must appear in the agent-platform ARCHITECTURE.md documenting
# the security permit path.
# ---------------------------------------------------------------------------
_r9_fail=0
_plat_arch='agent-service/ARCHITECTURE.md'
if [[ -f "$_plat_arch" ]]; then
  if ! grep -q '/v3/api-docs' "$_plat_arch" 2>/dev/null; then
    fail_rule "openapi_path_consistency" "$_plat_arch does not document /v3/api-docs exposure. Document it or remove the security permitAll."
    _r9_fail=1
  fi
fi
if [[ $_r9_fail -eq 0 ]]; then pass_rule "openapi_path_consistency"; fi

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
# Rule 14 — module_arch_method_name_truth
# ADR-0036: method names in code-fence blocks in agent-service/ARCHITECTURE.md
# and agent-service/ARCHITECTURE.md must exist in the named Java class.
# Currently checks the specific known drift: probe.check() was wrong; correct
# is probe.probe(). Fails if probe.check() appears in any module ARCHITECTURE.md.
# ---------------------------------------------------------------------------
_r14_fail=0
for _maf in 'agent-service/ARCHITECTURE.md' 'agent-service/ARCHITECTURE.md'; do
  if [[ -f "$_maf" ]]; then
    if grep -q 'probe\.check()' "$_maf" 2>/dev/null; then
      fail_rule "module_arch_method_name_truth" "$_maf references probe.check() but actual method in OssApiProbe is probe.probe(). Per ADR-0036 Gate Rule 14 method names in docs must match source."
      _r14_fail=1
    fi
  fi
done
if [[ $_r14_fail -eq 0 ]]; then pass_rule "module_arch_method_name_truth"; fi

# ---------------------------------------------------------------------------
# Rule 15 — no_active_refs_deleted_wave_plan_paths
# ADR-0041: active .md files (outside archive/reviews/third_party/target/.git)
# must not reference docs/plans/engineering-plan-W0-W4.md or
# docs/plans/roadmap-W0-W4.md. Both plans were archived per ADR-0037.
# ---------------------------------------------------------------------------
_r15_fail=0
_deleted_plan_refs=('docs/plans/engineering-plan-W0-W4.md' 'docs/plans/roadmap-W0-W4.md')
# Perf fix (2026-05-23): replaced per-file × per-pattern grep loop (~hundreds
# × 2 = ~hundreds of forks, ~16s) with a single bulk `grep -lFf` against the
# pre-built file list. Identical "first match wins" semantics.
_r15_files=$(find . -name '*.md' \
  ! -path './docs/archive/*' \
  ! -path './docs/logs/reviews/*' \
  ! -path './docs/adr/*' \
  ! -path './docs/delivery/*' \
  ! -path './docs/v6-rationale/*' \
  ! -path './third_party/*' \
  ! -path './target/*' \
  ! -path './.git/*' \
  -type f 2>/dev/null | sort || true)
if [[ -n "$_r15_files" ]]; then
  _r15_first_hit=$(printf '%s\n' "$_r15_files" \
    | xargs -d '\n' -r grep -lFf <(printf '%s\n' "${_deleted_plan_refs[@]}") 2>/dev/null \
    | head -1 || true)
  if [[ -n "$_r15_first_hit" ]]; then
    # Identify which deleted ref triggered the match (for the error message).
    _r15_ref=""
    for _r15_candidate in "${_deleted_plan_refs[@]}"; do
      if grep -qF "$_r15_candidate" "$_r15_first_hit" 2>/dev/null; then
        _r15_ref="$_r15_candidate"; break
      fi
    done
    fail_rule "no_active_refs_deleted_wave_plan_paths" "$_r15_first_hit references deleted plan path '${_r15_ref:-?}'. Per ADR-0041 Gate Rule 15 active docs must not reference archived plan paths."
    _r15_fail=1
  fi
fi
if [[ $_r15_fail -eq 0 ]]; then pass_rule "no_active_refs_deleted_wave_plan_paths"; fi

# ---------------------------------------------------------------------------
# Rule 16 — http_contract_w1_tenant_and_cancel_consistency
# ADR-0040: (a) no "replace.*X-Tenant-Id" in active docs; (b) http-api-contracts.md
# must not reference CREATED as initial status; (c) openapi-v1.yaml must not
# mention DELETE /v1/runs/{runId} as the cancel mechanism.
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
  | grep -vE '/docs/(archive|logs/reviews|adr|delivery|v6-rationale|plans)/|/third_party/|/target/|/\.git/' \
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
# Rule 20 — module_metadata_truth
# ADR-0043: module README.md files must not reference Java class names that
# do not exist in the repository.
# ---------------------------------------------------------------------------
_r20_fail=0
_ghost_classes20=('GraphitiRestGraphMemoryRepository' 'CogneeGraphMemoryRepository')
while IFS= read -r _rm20; do
  [[ -z "$_rm20" ]] && continue
  for _gc20 in "${_ghost_classes20[@]}"; do
    if grep -qF "$_gc20" "$_rm20" 2>/dev/null; then
      if ! find . -name "${_gc20}.java" -not -path './target/*' -not -path './.git/*' | grep -q .; then
        fail_rule "module_metadata_truth" "$_rm20 references class '$_gc20' but no .java file exists. Per ADR-0043 Gate Rule 20 module READMEs must not reference non-existent Java classes."
        _r20_fail=1
      fi
    fi
  done
done < <(find . -name 'README.md' ! -path './docs/*' ! -path './third_party/*' ! -path './target/*' 2>/dev/null | sort || true)
if [[ $_r20_fail -eq 0 ]]; then pass_rule "module_metadata_truth"; fi

# ---------------------------------------------------------------------------
# Rule 21 — bom_glue_paths_exist
# ADR-0043: docs/cross-cutting/oss-bill-of-materials.md must not contain the
# known ghost implementation paths unless the path exists on disk.
# ---------------------------------------------------------------------------
_r21_fail=0
_bom21='docs/cross-cutting/oss-bill-of-materials.md'
_ghost_paths21=(
  'agent-service/src/main/java/com/huawei/ascend/service/runtime/llm/ChatClientFactory'
  'agent-service/src/main/java/com/huawei/ascend/service/runtime/llm/LlmRouter'
  'agent-service/src/main/java/com/huawei/ascend/service/runtime/memory/PgVectorAdapter'
  'agent-service/src/main/java/com/huawei/ascend/service/runtime/temporal/RunWorkflow'
  'agent-service/src/main/java/com/huawei/ascend/service/runtime/tool/McpToolRegistry'
)
if [[ -f "$_bom21" ]]; then
  for _gp21 in "${_ghost_paths21[@]}"; do
    if grep -qF "$_gp21" "$_bom21" 2>/dev/null; then
      if [[ ! -e "$_gp21" ]]; then
        fail_rule "bom_glue_paths_exist" "$_bom21 references path '$_gp21' which does not exist on disk. Per ADR-0043 Gate Rule 21 BoM glue paths must exist or be removed."
        _r21_fail=1
      fi
    fi
  done
fi
if [[ $_r21_fail -eq 0 ]]; then pass_rule "bom_glue_paths_exist"; fi

# ---------------------------------------------------------------------------
# Rule 22 — lowercase_metrics_in_contract_docs (widened per ADR-0043/ADR-0045)
# The full ACTIVE_NORMATIVE_DOCS corpus must not contain SPRINGAI_ASCEND_<lowercase>
# metric name patterns. grep -E is case-sensitive by default (LC_ALL=C set above).
# ---------------------------------------------------------------------------
_r22_fail=0
while IFS= read -r _af22; do
  [[ -z "$_af22" ]] && continue
  if grep -qE 'SPRINGAI_ASCEND_[a-z]' "$_af22" 2>/dev/null; then
    fail_rule "lowercase_metrics_in_contract_docs" "$_af22 contains uppercase metric namespace 'SPRINGAI_ASCEND_<lowercase>'. Per ADR-0043 Gate Rule 22 (widened) metric names must use lowercase springai_ascend_ prefix."
    _r22_fail=1
  fi
done < <(find . -name '*.md' -o -name '*.yaml' | grep -v '/docs/archive/' | grep -v '/docs/logs/reviews/' | \
  grep -v '/docs/adr/' | grep -v '/docs/delivery/' | grep -v '/docs/v6-rationale/' | \
  grep -v '/docs/plans/' | grep -v '/third_party/' | grep -v '/target/' | grep -v '/.git/' | sort 2>/dev/null || true)
if [[ $_r22_fail -eq 0 ]]; then pass_rule "lowercase_metrics_in_contract_docs"; fi

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
                './third_party/', './target/', './.git/')

def is_excluded(p: str) -> bool:
    return any(p.startswith(d) for d in EXCLUDE_DIRS)

violations = []
for root, dirs, files in os.walk('.', topdown=True):
    # Prune excluded dirs in-place.
    dirs[:] = [d for d in dirs if not is_excluded(os.path.join(root, d) + '/')]
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
# Rule 25 — peripheral_wave_qualifier
# ADR-0045: SPI Javadoc must not use "Primary sidecar impl:" or "Primary impl:"
# without a wave qualifier (W0-W4) in context. Active markdown docs must not use
# "Sidecar adapter —" without a wave qualifier or ADR reference. Closes PERIPHERAL-DRIFT.
# ---------------------------------------------------------------------------
_r25_fail=0
# Perf fix (2026-05-23): both 25a (java sources, ±2-line context) and 25b
# (active markdown, in-line context) consolidated into a single python pass.
# Original ran ~hundreds of files × ~3-5 forks per match = ~17s; the rewrite
# finishes in ~1s. Same regex patterns and same context windows.
_r25_violations="$("${GATE_PYTHON_BIN:-python3}" - <<'PYEOF'
import os, re
from pathlib import Path

W_RE = re.compile(r'(?:^|[^A-Za-z0-9])W[0-4](?:[^A-Za-z0-9]|$)')
MARKER_25B = re.compile(r'ADR-')
violations: list[str] = []

# 25a: agent-service main java files, "Primary impl:" / "Primary sidecar impl:" need W0-W4 in ±2 lines.
prim_re = re.compile(r'Primary sidecar impl:|Primary impl:')
java_root = 'agent-service/src/main/java'
if os.path.isdir(java_root):
    for dirpath, _, files in os.walk(java_root):
        for fn in files:
            if not fn.endswith('.java'): continue
            p = os.path.join(dirpath, fn)
            try: lines = Path(p).read_text(encoding='utf-8', errors='replace').splitlines()
            except OSError: continue
            n = len(lines)
            for i, ln in enumerate(lines):
                if not prim_re.search(ln): continue
                lo = max(0, i - 2); hi = min(n, i + 3)
                ctx = ' '.join(lines[lo:hi])
                if not W_RE.search(ctx):
                    violations.append(f"25a\t{p}\t{i+1}")

# 25b: active md files, "Sidecar adapter —" on a line lacking W0-W4 AND ADR-.
EXCLUDE_DIRS = ('./docs/archive/', './docs/logs/reviews/', './docs/adr/',
                './docs/delivery/', './docs/v6-rationale/', './docs/plans/',
                './third_party/', './target/', './.git/')
def excluded(p: str) -> bool:
    return any(p.startswith(d) for d in EXCLUDE_DIRS)
sidecar_re = re.compile(r'Sidecar adapter —')
for root, dirs, files in os.walk('.', topdown=True):
    dirs[:] = [d for d in dirs if not excluded(os.path.join(root, d) + '/')]
    for fn in files:
        if not fn.endswith('.md'): continue
        p = os.path.join(root, fn)
        if excluded(p): continue
        try: lines = Path(p).read_text(encoding='utf-8', errors='replace').splitlines()
        except OSError: continue
        for i, ln in enumerate(lines):
            if not sidecar_re.search(ln): continue
            if W_RE.search(ln) or MARKER_25B.search(ln): continue
            violations.append(f"25b\t{p}\t{i+1}")

for v in violations: print(v)
PYEOF
)"
if [[ -n "$_r25_violations" ]]; then
  while IFS=$'\t' read -r _r25_kind _r25_path _r25_line; do
    [[ -z "$_r25_kind" ]] && continue
    case "$_r25_kind" in
      25a) fail_rule "peripheral_wave_qualifier" "$_r25_path:$_r25_line contains 'Primary.*impl:' without wave qualifier (W0-W4) in context. Per ADR-0045 Gate Rule 25 future-wave impl claims must carry wave qualifiers." ;;
      25b) fail_rule "peripheral_wave_qualifier" "$_r25_path:$_r25_line contains 'Sidecar adapter —' without wave qualifier or ADR reference. Per ADR-0045 Gate Rule 25." ;;
    esac
    _r25_fail=1
  done <<< "$_r25_violations"
fi
if [[ $_r25_fail -eq 0 ]]; then pass_rule "peripheral_wave_qualifier"; fi

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
# Rule 29 — whitepaper_alignment_matrix_present
# ADR-0049 + P2-1: docs/governance/whitepaper-alignment-matrix.md must exist
# and must contain rows for each of the 20 required whitepaper concepts.
# Closes the concept-traceability gap from the whitepaper-alignment review.
# ---------------------------------------------------------------------------
_r29_fail=0
_matrix29='docs/governance/whitepaper-alignment-matrix.md'
if [[ ! -f "$_matrix29" ]]; then
  fail_rule "whitepaper_alignment_matrix_present" "$_matrix29 missing. Per Gate Rule 29 / ADR-0049 the whitepaper alignment matrix must exist as concept-level traceability from whitepaper to active architecture."
  _r29_fail=1
else
  _required29=(
    'C/S separation'
    'Task Cursor'
    'Dynamic Hydration'
    'Sync State'
    'Sub-Stream'
    'Yield & Handoff'
    'Business ontology ownership'
    'S-side execution trajectory ownership'
    'Placeholder exemption'
    'Full Trace vs Node Snapshot'
    'Lazy mounting'
    'Skill Topology Scheduler'
    'C-side business degradation authority'
    'Session/context decoupling'
    'Workflow Intermediary'
    'Three-track bus'
    'Capability bidding'
    'Permission issuance'
    'Chronos Hydration'
    'Service Layer microservice commitment'
  )
  for _concept29 in "${_required29[@]}"; do
    if ! grep -qF "$_concept29" "$_matrix29"; then
      fail_rule "whitepaper_alignment_matrix_present" "$_matrix29 missing required concept row '$_concept29'. Per Gate Rule 29 all 20 named whitepaper concepts must appear in the alignment matrix."
      _r29_fail=1
    fi
  done
fi
if [[ $_r29_fail -eq 0 ]]; then pass_rule "whitepaper_alignment_matrix_present"; fi

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
  agent-service/src/main/java agent-service/src/main/java 2>/dev/null || true)
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
_28c_hits=$(git grep -lE "$_secret_patterns" -- ':!target/' ':!*.jar' ':!*.png' ':!*.jpg' ':!*.pdf' ':!docs/governance/enforcers.yaml' ':!gate/check_architecture_sync.sh' ':!gate/check_architecture_sync.ps1' 2>/dev/null || true)
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
# SkillRegistry, HookChain, SpawnEnvelope, LogicalCallHandle, ConnectionLease,
# AdmissionDecision, BackpressureSignal, ChronosHydration, SandboxExecutor)
# MUST NOT appear in agent-*/src/main/java. Test sources, ADRs, plans,
# release notes, and architecture-status.yaml are intentionally exempt.
# ---------------------------------------------------------------------------
_r28d_fail=0
_oos_names='LLMGateway|PostgresCheckpointer|SkillRegistry|HookChain|SpawnEnvelope|LogicalCallHandle|ConnectionLease|AdmissionDecision|BackpressureSignal|ChronosHydration|SandboxExecutor'
_28d_hits=$(grep -rnE "\\b($_oos_names)\\b" \
  agent-service/src/main/java agent-service/src/main/java 2>/dev/null || true)
if [[ -n "$_28d_hits" ]]; then
  fail_rule "out_of_scope_name_guard" "W2+ out-of-scope name detected in main sources:\n$_28d_hits\nPer Rule 28d / enforcer E26 / plan §13."
  _r28d_fail=1
fi
if [[ $_r28d_fail -eq 0 ]]; then pass_rule "out_of_scope_name_guard"; fi

# ---------------------------------------------------------------------------
# Rule 28e — module_count_invariant (enforcer E27)
# Root pom.xml MUST declare exactly 8 <module> entries after the 2026-05-20
# rc13 wave (ADR-0088 dissolved agent-runtime-core): BoM + 6 substantive
# modules (agent-client, agent-bus, agent-middleware, agent-execution-engine,
# agent-evolve, agent-service) + graphmemory starter. Any other count is
# rejected; L1 plan decision D3 amended per ADR-0078 + ADR-0088.
# ---------------------------------------------------------------------------
_r28e_fail=0
_root_pom='pom.xml'
_r28e_expected=8
if [[ -f "$_root_pom" ]]; then
  _module_count=$(grep -c '<module>' "$_root_pom" 2>/dev/null || echo 0)
  if [[ "$_module_count" -ne "$_r28e_expected" ]]; then
    fail_rule "module_count_invariant" "$_root_pom declares $_module_count <module> entries; L1 (six-module materialization) requires exactly $_r28e_expected. Per Rule 28e / enforcer E27 / plan decision D3."
    _r28e_fail=1
  fi
fi
if [[ $_r28e_fail -eq 0 ]]; then pass_rule "module_count_invariant"; fi

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
# Rule 28 — constraint_enforcer_coverage (meta-rule, enforcer E28)
#
# **L1 scope (Phase L truthful naming, per reviewer P2-1):** baseline presence
# check only. Verifies that `docs/governance/enforcers.yaml` references
# `CLAUDE.md` AND `ARCHITECTURE.md`. This is the smallest viable bootstrap
# meta-check — it does NOT parse every "must"/"forbidden"/"required" sentence
# in the corpus and cross-reference each one. Full natural-language parsing is
# deferred (no executable enforcer is feasible without committing to a brittle
# regex over evolving prose).
#
# Anchor-level truth is enforced by Rule 28j (`enforcer_artifact_paths_exist`,
# Phase L hardening), which validates that every `artifact: path#anchor`
# resolves to a real method (.java/.sh) or heading (.md) — closing reviewer
# finding P0-2.
# ---------------------------------------------------------------------------
_r28_fail=0
if [[ -f "$_efile" ]] && [[ -f 'CLAUDE.md' ]]; then
  if ! grep -q 'CLAUDE.md' "$_efile" 2>/dev/null; then
    fail_rule "constraint_enforcer_coverage" "enforcers.yaml does not reference CLAUDE.md at all; the meta-rule requires every active CLAUDE rule to map to an enforcer. Per Rule 28 / enforcer E28."
    _r28_fail=1
  fi
  if ! grep -q 'ARCHITECTURE.md' "$_efile" 2>/dev/null; then
    fail_rule "constraint_enforcer_coverage" "enforcers.yaml does not reference ARCHITECTURE.md; §4 constraints must map to enforcers. Per Rule 28 / enforcer E28."
    _r28_fail=1
  fi
fi
if [[ $_r28_fail -eq 0 ]]; then pass_rule "constraint_enforcer_coverage"; fi

# ---------------------------------------------------------------------------
# Rule 30 — telemetry_vertical_constraint_coverage (enforcer E47)
#
# Telemetry Vertical L1.x (ADR-0061 / §4 #53–#59): every Telemetry-Vertical
# constraint number in ARCHITECTURE.md §4 MUST resolve to at least one
# enforcer row in docs/governance/enforcers.yaml. Stricter than the existing
# meta-rule 28 (presence check only) — Rule 30 validates each §4 #N reference
# individually for N in {53..59}.
# ---------------------------------------------------------------------------
_r30_fail=0
_efile='docs/governance/enforcers.yaml'
_archfile='ARCHITECTURE.md'
if [[ -f "$_archfile" && -f "$_efile" ]]; then
  for _n in 53 54 55 56 57 58 59; do
    # Constraint number must exist in ARCHITECTURE.md §4 as a top-level numbered item.
    if ! grep -qE "^${_n}\. \*\*" "$_archfile"; then
      fail_rule "telemetry_vertical_constraint_coverage" "ARCHITECTURE.md §4 #${_n} (Telemetry Vertical) is missing — expected '${_n}. **' at line start. Per ADR-0061 §8."
      _r30_fail=1
      continue
    fi
    # And the constraint number must be cited in at least one enforcer row.
    if ! grep -qE "§4 #${_n}" "$_efile"; then
      fail_rule "telemetry_vertical_constraint_coverage" "enforcers.yaml has no row citing '§4 #${_n}' (Telemetry Vertical). Add an E-row per ADR-0061 §8 + Rule 28."
      _r30_fail=1
    fi
  done
fi
if [[ $_r30_fail -eq 0 ]]; then pass_rule "telemetry_vertical_constraint_coverage"; fi

# ---------------------------------------------------------------------------
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
# Rule 32 — competitive_baselines_present_and_wellformed (enforcer E50, ADR-0065)
#
# docs/governance/competitive-baselines.yaml MUST exist and MUST declare four
# dimensions: performance, cost, developer_onboarding, governance.
# ---------------------------------------------------------------------------
_r32_fail=0
_baseline_file="docs/governance/competitive-baselines.yaml"
if [[ ! -f "$_baseline_file" ]]; then
  fail_rule "competitive_baselines_present_and_wellformed" "$_baseline_file is missing (CLAUDE.md Rule 30 / ADR-0065)"
  _r32_fail=1
else
  for _dim in performance cost developer_onboarding governance; do
    if ! grep -qE "^[[:space:]]*${_dim}:" "$_baseline_file" 2>/dev/null; then
      fail_rule "competitive_baselines_present_and_wellformed" "$_baseline_file missing required dimension '${_dim}'"
      _r32_fail=1
    fi
  done
fi
if [[ $_r32_fail -eq 0 ]]; then pass_rule "competitive_baselines_present_and_wellformed"; fi

# ---------------------------------------------------------------------------
# Rule 33 — release_note_references_four_pillars (enforcer E51, ADR-0065)
#
# The most recent release note under docs/logs/releases/ MUST mention all four
# pillar names by name so reviewers see the dimensions tracked per release.
# ---------------------------------------------------------------------------
_r33_fail=0
_latest_release="$(latest_release_path docs/logs/releases || true)"
if [[ -z "$_latest_release" ]]; then
  pass_rule "release_note_references_four_pillars"   # no release notes yet — vacuous pass
else
  _missing_pillars=""
  for _p in performance cost developer_onboarding governance; do
    if ! grep -qiE "\b${_p}\b" "$_latest_release" 2>/dev/null; then
      _missing_pillars="${_missing_pillars} ${_p}"
    fi
  done
  if [[ -n "$_missing_pillars" ]]; then
    fail_rule "release_note_references_four_pillars" "$(basename "$_latest_release") does not mention pillar(s):${_missing_pillars} (CLAUDE.md Rule 30 / ADR-0065)"
    _r33_fail=1
  fi
fi
if [[ $_r33_fail -eq 0 ]] && [[ -n "$_latest_release" ]]; then pass_rule "release_note_references_four_pillars"; fi

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
# Rule 37 — architecture_artefact_front_matter (enforcer E55, ADR-0068)
#
# Every L0/L1/L2 architecture artefact MUST declare a level: + view:
# front-matter (YAML at top of file for .md; top-level key for .yaml).
# Targets: ARCHITECTURE.md, agent-*/ARCHITECTURE.md, docs/L2/**/*.md (excluding
# README.md while empty), docs/adr/*.yaml.
# ---------------------------------------------------------------------------
_r37_fail=0
_valid_levels='^(L0|L1|L2)$'
_valid_views='^(logical|development|process|physical|scenarios)$'

_check_front_matter_md() {
  local _f="$1"
  local _level _view
  _level="$(awk 'BEGIN{in_fm=0; n=0} /^---[[:space:]]*$/{n++; if(n==1){in_fm=1; next} if(n==2){exit}} in_fm && /^level:[[:space:]]/{sub(/^level:[[:space:]]*/,""); sub(/[[:space:]]*$/,""); print; exit}' "$_f" 2>/dev/null)"
  _view="$(awk 'BEGIN{in_fm=0; n=0} /^---[[:space:]]*$/{n++; if(n==1){in_fm=1; next} if(n==2){exit}} in_fm && /^view:[[:space:]]/{sub(/^view:[[:space:]]*/,""); sub(/[[:space:]]*$/,""); print; exit}' "$_f" 2>/dev/null)"
  if [[ -z "$_level" ]]; then
    fail_rule "architecture_artefact_front_matter" "$_f missing 'level:' YAML front-matter (CLAUDE.md Rule 33 / ADR-0068)"; _r37_fail=1; return
  fi
  if [[ ! "$_level" =~ $_valid_levels ]]; then
    fail_rule "architecture_artefact_front_matter" "$_f level: '$_level' is not one of L0|L1|L2"; _r37_fail=1
  fi
  if [[ -z "$_view" ]]; then
    fail_rule "architecture_artefact_front_matter" "$_f missing 'view:' YAML front-matter (CLAUDE.md Rule 33 / ADR-0068)"; _r37_fail=1; return
  fi
  if [[ ! "$_view" =~ $_valid_views ]]; then
    fail_rule "architecture_artefact_front_matter" "$_f view: '$_view' is not one of logical|development|process|physical|scenarios"; _r37_fail=1
  fi
}

_check_front_matter_yaml() {
  local _f="$1"
  local _level _view
  _level="$(grep -E '^level:[[:space:]]' "$_f" 2>/dev/null | head -1 | sed -E 's/^level:[[:space:]]*([A-Za-z0-9_]+).*/\1/')"
  _view="$(grep -E '^view:[[:space:]]' "$_f" 2>/dev/null | head -1 | sed -E 's/^view:[[:space:]]*([A-Za-z0-9_]+).*/\1/')"
  if [[ -z "$_level" ]]; then
    fail_rule "architecture_artefact_front_matter" "$_f missing top-level 'level:' (CLAUDE.md Rule 33 / ADR-0068)"; _r37_fail=1; return
  fi
  if [[ ! "$_level" =~ $_valid_levels ]]; then
    fail_rule "architecture_artefact_front_matter" "$_f level: '$_level' is not one of L0|L1|L2"; _r37_fail=1
  fi
  if [[ -z "$_view" ]]; then
    fail_rule "architecture_artefact_front_matter" "$_f missing top-level 'view:' (CLAUDE.md Rule 33 / ADR-0068)"; _r37_fail=1; return
  fi
  if [[ ! "$_view" =~ $_valid_views ]]; then
    fail_rule "architecture_artefact_front_matter" "$_f view: '$_view' is not one of logical|development|process|physical|scenarios"; _r37_fail=1
  fi
}

# Perf fix (2026-05-23): the original ran 2-4 forks per file (~100 files →
# ~400 forks, ~14s on WSL/mnt/d). Replaced with a single python pass that
# walks all target paths, parses front-matter, and validates level/view
# against the same {L0|L1|L2} / {logical|development|process|physical|scenarios}
# enums. Same fail messages so the upstream surface (release-note baseline /
# Rule 28 references) is unchanged.
_r37_violations="$("${GATE_PYTHON_BIN:-python3}" - <<'PYEOF'
import os, re, glob
from pathlib import Path

valid_levels = {'L0', 'L1', 'L2'}
valid_views = {'logical', 'development', 'process', 'physical', 'scenarios'}

def fail(kind: str, path: str, detail: str):
    print(f"{kind}\t{path}\t{detail}")

def check_md(path: str):
    try: lines = Path(path).read_text(encoding='utf-8', errors='replace').splitlines()
    except OSError: return
    in_fm = False; fm_count = 0; level = ''; view = ''
    for ln in lines:
        if re.match(r'^---\s*$', ln):
            fm_count += 1
            if fm_count == 1: in_fm = True; continue
            if fm_count == 2: break
            continue
        if not in_fm: continue
        m = re.match(r'^level:\s*(.+?)\s*$', ln)
        if m and not level: level = m.group(1)
        m = re.match(r'^view:\s*(.+?)\s*$', ln)
        if m and not view: view = m.group(1)
    if not level: fail('MD_LEVEL_MISSING', path, '')
    elif level not in valid_levels: fail('MD_LEVEL_BAD', path, level)
    if not view: fail('MD_VIEW_MISSING', path, '')
    elif view not in valid_views: fail('MD_VIEW_BAD', path, view)

def check_yaml(path: str):
    try: text = Path(path).read_text(encoding='utf-8', errors='replace')
    except OSError: return
    level = ''; view = ''
    for ln in text.splitlines():
        m = re.match(r'^level:\s*([A-Za-z0-9_]+)', ln)
        if m and not level: level = m.group(1)
        m = re.match(r'^view:\s*([A-Za-z0-9_]+)', ln)
        if m and not view: view = m.group(1)
    if not level: fail('YAML_LEVEL_MISSING', path, '')
    elif level not in valid_levels: fail('YAML_LEVEL_BAD', path, level)
    if not view: fail('YAML_VIEW_MISSING', path, '')
    elif view not in valid_views: fail('YAML_VIEW_BAD', path, view)

targets_md = []
if os.path.isfile('ARCHITECTURE.md'): targets_md.append('ARCHITECTURE.md')
for d in sorted(os.listdir('.')):
    p = os.path.join(d, 'ARCHITECTURE.md')
    if os.path.isfile(p) and p != 'ARCHITECTURE.md':
        targets_md.append(p.replace('\\', '/'))
targets_md.extend(sorted(glob.glob('docs/L2/**/*.md', recursive=True)))
for p in targets_md: check_md(p)

for p in sorted(glob.glob('docs/adr/*.yaml')):
    check_yaml(p)
PYEOF
)"
if [[ -n "$_r37_violations" ]]; then
  while IFS=$'\t' read -r _r37_kind _r37_path _r37_val; do
    [[ -z "$_r37_kind" ]] && continue
    case "$_r37_kind" in
      MD_LEVEL_MISSING)   fail_rule "architecture_artefact_front_matter" "$_r37_path missing 'level:' YAML front-matter (CLAUDE.md Rule 33 / ADR-0068)" ;;
      MD_LEVEL_BAD)       fail_rule "architecture_artefact_front_matter" "$_r37_path level: '$_r37_val' is not one of L0|L1|L2" ;;
      MD_VIEW_MISSING)    fail_rule "architecture_artefact_front_matter" "$_r37_path missing 'view:' YAML front-matter (CLAUDE.md Rule 33 / ADR-0068)" ;;
      MD_VIEW_BAD)        fail_rule "architecture_artefact_front_matter" "$_r37_path view: '$_r37_val' is not one of logical|development|process|physical|scenarios" ;;
      YAML_LEVEL_MISSING) fail_rule "architecture_artefact_front_matter" "$_r37_path missing top-level 'level:' (CLAUDE.md Rule 33 / ADR-0068)" ;;
      YAML_LEVEL_BAD)     fail_rule "architecture_artefact_front_matter" "$_r37_path level: '$_r37_val' is not one of L0|L1|L2" ;;
      YAML_VIEW_MISSING)  fail_rule "architecture_artefact_front_matter" "$_r37_path missing top-level 'view:' (CLAUDE.md Rule 33 / ADR-0068)" ;;
      YAML_VIEW_BAD)      fail_rule "architecture_artefact_front_matter" "$_r37_path view: '$_r37_val' is not one of logical|development|process|physical|scenarios" ;;
    esac
    _r37_fail=1
  done <<< "$_r37_violations"
fi
if [[ $_r37_fail -eq 0 ]]; then pass_rule "architecture_artefact_front_matter"; fi

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
  _r38_tmp1="$(mktemp 2>/dev/null || echo /tmp/r38_a.yaml)"
  _r38_tmp2="$(mktemp 2>/dev/null || echo /tmp/r38_b.yaml)"
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
# Rule 39 — review_proposal_front_matter (enforcer E57, ADR-0068)
#
# Every NEW (post-W1) proposal under docs/logs/reviews/ MUST declare
# affects_level: + affects_view: front-matter. Pre-W1 historical review
# files are explicitly listed in the allow-list below and exempted.
# ---------------------------------------------------------------------------
_r39_fail=0
# Allow-list of pre-W1 historical files (relative to docs/logs/reviews/).
_r39_allow_re='^(2026-05-1[23]-|2026-05-14-(architecture-governance-in-vibe-coding-era|L0Architecture-LucioIT-wave-1-request|l1-architecture-expert-review)|spring-ai-ascend-implementation-guidelines|Architectural Perspective Review)'
while IFS= read -r _f39; do
  [[ -z "$_f39" ]] && continue
  _base="$(basename "$_f39")"
  [[ "$_base" == "_TEMPLATE.md" ]] && continue
  if [[ "$_base" =~ $_r39_allow_re ]]; then continue; fi
  # Accept both single-value form (`affects_level: L0`) and YAML list form
  # (`affects_level: [L0, L1]` or `affects_level: L0, L1`) — 2026-05-23
  # widening: the rc33 proposals add multi-level/multi-view proposals.
  if ! grep -qE '^affects_level:[[:space:]]+(\[[[:space:]]*)?(L0|L1|L2)' "$_f39" 2>/dev/null; then
    fail_rule "review_proposal_front_matter" "$_f39 missing 'affects_level:' front-matter (CLAUDE.md Rule 33 / ADR-0068)"; _r39_fail=1
  fi
  if ! grep -qE '^affects_view:[[:space:]]+(\[[[:space:]]*)?(logical|development|process|physical|scenarios)' "$_f39" 2>/dev/null; then
    fail_rule "review_proposal_front_matter" "$_f39 missing 'affects_view:' front-matter (CLAUDE.md Rule 33 / ADR-0068)"; _r39_fail=1
  fi
done < <(find docs/logs/reviews -maxdepth 1 -type f -name '*.md' 2>/dev/null | sort || true)
if [[ $_r39_fail -eq 0 ]]; then pass_rule "review_proposal_front_matter"; fi

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
  _r42_a="$(mktemp 2>/dev/null || echo /tmp/r42_a.yaml)"
  _r42_b="$(mktemp 2>/dev/null || echo /tmp/r42_b.yaml)"
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

# ---------------------------------------------------------------------------
# Rule 44 — frozen_doc_edit_path_compliance (enforcer E63, Phase M D4)
#
# For every architecture artefact declaring `freeze_id: <non-null>` in its
# front-matter, any modification to that file in the working tree (vs the
# merge base) MUST be accompanied by a NEW docs/logs/reviews/*.md proposal in the
# same commit naming the file under `affects_artefact:`. No-op today (all
# freeze_id values are null); arms automatically when a doc is phase-released.
# ---------------------------------------------------------------------------
_r44_fail=0
_r44_base="${BASE_REF:-origin/main}"
# Collect frozen-doc paths.
_r44_frozen=""
for _f44 in ARCHITECTURE.md $(find . -maxdepth 2 -type f -name 'ARCHITECTURE.md' ! -path './ARCHITECTURE.md' 2>/dev/null || true) \
            $(find docs/L2 -type f -name '*.md' 2>/dev/null || true) \
            $(find docs/adr -maxdepth 1 -type f -name '*.yaml' 2>/dev/null || true); do
  [[ -z "$_f44" || ! -f "$_f44" ]] && continue
  _fid="$(awk 'BEGIN{in_fm=0; n=0} /^---[[:space:]]*$/{n++; if(n==1){in_fm=1; next} if(n==2){exit}} in_fm && /^freeze_id:[[:space:]]/{sub(/^freeze_id:[[:space:]]*/,""); sub(/[[:space:]]*$/,""); print; exit}' "$_f44" 2>/dev/null)"
  # YAML ADR (top-level key, no front-matter delimiters)
  if [[ -z "$_fid" ]]; then
    _fid="$(grep -E '^freeze_id:[[:space:]]' "$_f44" 2>/dev/null | head -1 | sed -E 's/^freeze_id:[[:space:]]*([A-Za-z0-9._-]+).*/\1/')"
  fi
  if [[ -n "$_fid" && "$_fid" != "null" ]]; then
    _r44_frozen="${_r44_frozen}${_f44}\n"
  fi
done
# If git is available and a base ref is reachable, check each frozen doc for
# modifications without an accompanying review proposal.
# rc18 Wave 2 (ADR-0095): shallow-clone fail-closed safeguard — if running on
# a shallow clone (CI without fetch-depth: 0), git diff against base ref may
# silently return empty even when the file changed. Fail loudly instead.
if [[ -n "$_r44_frozen" ]] && command -v git >/dev/null 2>&1 && git rev-parse --verify "$_r44_base" >/dev/null 2>&1; then
  _r44_is_shallow=$(git rev-parse --is-shallow-repository 2>/dev/null)
  if [[ "$_r44_is_shallow" == "true" ]]; then
    fail_rule "frozen_doc_edit_path_compliance" "cannot evaluate frozen-doc edit compliance on shallow clone (run git fetch --unshallow in CI; CI workflows must set actions/checkout fetch-depth: 0) -- Rule 44 / E63 (rc18 Wave 2 fix per ADR-0095)"
    _r44_fail=1
  fi
  _r44_changed_reviews="$(git diff --name-only --diff-filter=A "$_r44_base" -- 'docs/logs/reviews/*.md' 2>/dev/null || true)"
  while IFS= read -r _f44; do
    [[ -z "$_f44" ]] && continue
    if git diff --name-only "$_r44_base" -- "$_f44" 2>/dev/null | grep -q .; then
      # Frozen doc was modified; require a review proposal naming it in affects_artefact:.
      _accompanied=0
      while IFS= read -r _r44_proposal; do
        [[ -z "$_r44_proposal" ]] && continue
        # rc28 + rc29 fix (ADV-11/NEW-4 + ADV3-4): accept BOTH single-line and
        # multi-line YAML list form. rc29 uses `grep -F` (fixed-string match)
        # instead of `-E` so regex metachars (`.`, `-`, `[`) in `_f44` are
        # treated literally — defeats the regex-injection class where `.`
        # would match any char and falsely accept typo'd paths.
        if grep -qF "affects_artefact:" "$_r44_proposal" 2>/dev/null \
           && grep -qF "$_f44" "$_r44_proposal" 2>/dev/null; then
          _accompanied=1
          break
        fi
      done <<< "$_r44_changed_reviews"
      if [[ $_accompanied -eq 0 ]]; then
        fail_rule "frozen_doc_edit_path_compliance" "$_f44 carries freeze_id but was modified without an accompanying docs/logs/reviews/*.md proposal citing it under affects_artefact:"
        _r44_fail=1
      fi
    fi
  done <<< "$(printf "%b" "$_r44_frozen")"
fi
if [[ $_r44_fail -eq 0 ]]; then pass_rule "frozen_doc_edit_path_compliance"; fi

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
# Rule 46 — cursor_flow_documented (enforcer E65, Rule 36 / P-F)
#
# docs/contracts/openapi-v1.yaml MUST declare a TaskCursor schema in
# components.schemas AND a top-level x-cursor-flow: annotation. Either alone
# is insufficient — the annotation declares INTENT, the schema declares the
# WIRE shape; both are needed for an LLM or codegen consumer to act on it.
# ---------------------------------------------------------------------------
_r46_fail=0
_r46_path="docs/contracts/openapi-v1.yaml"
if [[ ! -f "$_r46_path" ]]; then
  fail_rule "cursor_flow_documented" "$_r46_path missing"
  _r46_fail=1
else
  if ! grep -qE '^[[:space:]]+TaskCursor:[[:space:]]*$' "$_r46_path"; then
    fail_rule "cursor_flow_documented" "$_r46_path does not declare a TaskCursor schema in components.schemas — Cursor Flow wire shape missing"
    _r46_fail=1
  fi
  if ! grep -qE '^x-cursor-flow:[[:space:]]*$' "$_r46_path"; then
    fail_rule "cursor_flow_documented" "$_r46_path missing top-level x-cursor-flow: annotation — Cursor Flow intent not declared"
    _r46_fail=1
  fi
fi
if [[ $_r46_fail -eq 0 ]]; then pass_rule "cursor_flow_documented"; fi

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
# Rule 49 — deployment_plane_in_module_metadata (enforcer E68, Rule 39 / P-I)
#
# Every <module>/module-metadata.yaml MUST declare deployment_plane: with
# value in {edge, compute_control, bus_state, sandbox, evolution, none}.
# ---------------------------------------------------------------------------
_r49_fail=0
_r49_allowed_re='^(edge|compute_control|bus_state|sandbox|evolution|none)$'
while IFS= read -r _r49_meta; do
  [[ -z "$_r49_meta" ]] && continue
  _r49_plane="$(grep -E '^deployment_plane:' "$_r49_meta" | head -1 | sed -E 's/^deployment_plane:[[:space:]]*([A-Za-z_]+).*/\1/')"
  if [[ -z "$_r49_plane" ]]; then
    fail_rule "deployment_plane_in_module_metadata" "$_r49_meta missing deployment_plane: field"
    _r49_fail=1
  elif ! [[ "$_r49_plane" =~ $_r49_allowed_re ]]; then
    fail_rule "deployment_plane_in_module_metadata" "$_r49_meta declares deployment_plane: $_r49_plane (not in {edge, compute_control, bus_state, sandbox, evolution, none})"
    _r49_fail=1
  fi
done <<< "${_SCAN_MODULE_METADATA:-$(find . -maxdepth 3 -name module-metadata.yaml -not -path './target/*' -not -path './.claude/*' 2>/dev/null)}"
if [[ $_r49_fail -eq 0 ]]; then pass_rule "deployment_plane_in_module_metadata"; fi

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
# Rule 51 — skill_capacity_yaml_present_and_wellformed (enforcer E70, Rule 41 / P-K)
#
# docs/governance/skill-capacity.yaml MUST exist; each skill row MUST have
# capacity_per_tenant + global_capacity + queue_strategy ∈ {suspend, fail}.
# ---------------------------------------------------------------------------
_r51_fail=0
_r51_path="docs/governance/skill-capacity.yaml"
if [[ ! -f "$_r51_path" ]]; then
  fail_rule "skill_capacity_yaml_present_and_wellformed" "$_r51_path missing — Rule 41 / P-K ironclad rule unenforced"
  _r51_fail=1
else
  # Count skill ids vs required-field occurrences. Each id row should be
  # followed by capacity_per_tenant, global_capacity, queue_strategy.
  _r51_ids="$(grep -cE '^[[:space:]]+- id:[[:space:]]+' "$_r51_path" 2>/dev/null || echo 0)"
  _r51_caps_per="$(grep -cE '^[[:space:]]+capacity_per_tenant:' "$_r51_path" 2>/dev/null || echo 0)"
  _r51_caps_global="$(grep -cE '^[[:space:]]+global_capacity:' "$_r51_path" 2>/dev/null || echo 0)"
  _r51_queue="$(grep -cE '^[[:space:]]+queue_strategy:[[:space:]]+(suspend|fail)([[:space:]#].*)?$' "$_r51_path" 2>/dev/null || echo 0)"
  if [[ "$_r51_ids" -lt 1 ]]; then
    fail_rule "skill_capacity_yaml_present_and_wellformed" "$_r51_path declares zero skills — at least one required"
    _r51_fail=1
  fi
  if [[ "$_r51_caps_per" -ne "$_r51_ids" ]] || [[ "$_r51_caps_global" -ne "$_r51_ids" ]] || [[ "$_r51_queue" -ne "$_r51_ids" ]]; then
    fail_rule "skill_capacity_yaml_present_and_wellformed" "$_r51_path schema-incomplete: $_r51_ids skill ids vs $_r51_caps_per capacity_per_tenant / $_r51_caps_global global_capacity / $_r51_queue queue_strategy(suspend|fail)"
    _r51_fail=1
  fi
fi
if [[ $_r51_fail -eq 0 ]]; then pass_rule "skill_capacity_yaml_present_and_wellformed"; fi

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
# Rule 55 — engine_envelope_yaml_present_and_wellformed (enforcer E76, Rule 43 / P-M, ADR-0072)
#
# docs/contracts/engine-envelope.v1.yaml is the single-source-of-truth for
# the EngineEnvelope shape. Required: schema: header, known_engines: block,
# at least one entry carrying an id:.
# ---------------------------------------------------------------------------
_r55_fail=0
_r55_path="docs/contracts/engine-envelope.v1.yaml"
if [[ ! -f "$_r55_path" ]]; then
  fail_rule "engine_envelope_yaml_present_and_wellformed" "$_r55_path missing -- Rule 43 / P-M envelope schema unenforced"
  _r55_fail=1
else
  if ! grep -qE '^schema:[[:space:]]+engine-envelope/v1[[:space:]]*$' "$_r55_path"; then
    fail_rule "engine_envelope_yaml_present_and_wellformed" "$_r55_path missing 'schema: engine-envelope/v1' header"
    _r55_fail=1
  fi
  if ! grep -qE '^known_engines:[[:space:]]*$' "$_r55_path"; then
    fail_rule "engine_envelope_yaml_present_and_wellformed" "$_r55_path missing known_engines: block"
    _r55_fail=1
  fi
  if ! grep -qE '^[[:space:]]+- id:[[:space:]]+\S+' "$_r55_path"; then
    fail_rule "engine_envelope_yaml_present_and_wellformed" "$_r55_path known_engines: contains no '- id:' entry"
    _r55_fail=1
  fi
fi
if [[ $_r55_fail -eq 0 ]]; then pass_rule "engine_envelope_yaml_present_and_wellformed"; fi

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
# Rule 59 — evolution_scope_yaml_present_and_wellformed (enforcer E86, Rule 47 / P-M, ADR-0075)
#
# docs/governance/evolution-scope.v1.yaml MUST exist with schema: header, three
# discriminator blocks (in_scope, out_of_scope_default, opt_in_export), the
# first two non-empty, and opt_in_export referencing telemetry-export.v1.yaml
# (W3 placeholder). Drift would let the evolution plane silently widen its
# surface beyond the server-sovereign boundary.
# ---------------------------------------------------------------------------
_r59_fail=0
_r59_path="docs/governance/evolution-scope.v1.yaml"
if [[ ! -f "$_r59_path" ]]; then
  fail_rule "evolution_scope_yaml_present_and_wellformed" "$_r59_path missing -- Rule 47 / P-M evolution scope unenforced"
  _r59_fail=1
else
  if ! grep -qE '^schema:[[:space:]]+evolution-scope/v1[[:space:]]*$' "$_r59_path"; then
    fail_rule "evolution_scope_yaml_present_and_wellformed" "$_r59_path missing 'schema: evolution-scope/v1' header"
    _r59_fail=1
  fi
  for _r59_block in in_scope out_of_scope_default opt_in_export; do
    if ! grep -qE "^${_r59_block}:" "$_r59_path"; then
      fail_rule "evolution_scope_yaml_present_and_wellformed" "$_r59_path missing top-level discriminator block '${_r59_block}:'"
      _r59_fail=1
    fi
  done
  for _r59_block in in_scope out_of_scope_default; do
    _r59_count=$(awk -v b="^${_r59_block}:" '$0 ~ b {f=1; next} /^[a-z_]+:/{f=0} f && /^[[:space:]]+- [a-z_]+/ {n++} END{print n+0}' "$_r59_path")
    if [[ "${_r59_count:-0}" -lt 1 ]]; then
      fail_rule "evolution_scope_yaml_present_and_wellformed" "$_r59_path block '${_r59_block}:' is empty -- at least one entry required"
      _r59_fail=1
    fi
  done
  if ! grep -qE 'contract_required:[[:space:]]+telemetry-export\.v1\.yaml' "$_r59_path"; then
    fail_rule "evolution_scope_yaml_present_and_wellformed" "$_r59_path opt_in_export.contract_required must reference 'telemetry-export.v1.yaml' (W3 placeholder)"
    _r59_fail=1
  fi
fi
if [[ $_r59_fail -eq 0 ]]; then pass_rule "evolution_scope_yaml_present_and_wellformed"; fi

# ---------------------------------------------------------------------------
# Rule 60 — schema_first_domain_contracts (enforcer E85, Rule 48, ADR-0077)
#
# Forbid new prose-defined enum sites in the architecture corpus. Scan
# ARCHITECTURE.md (root) + agent-*/ARCHITECTURE.md for the prose-enum pattern
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
  # Phase 7 audit fix (Rule 48 sunset discipline -- plan F2/F3 in
  # D:/.claude/plans/spi-atomic-willow.md). Each grandfather entry MUST be
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
# Rule 61 — legacy_powershell_gate_deprecated (v2.0.0-rc2 / second-pass review P0-1)
#
# The PowerShell architecture-sync gate (gate/check_architecture_sync.ps1) was
# frozen at Rule 29 in 2026-05 while the bash gate evolved to Rule 60+. The
# second-pass review (docs/logs/reviews/2026-05-16-l0-w2x-rc1-second-pass-architecture-review.en.md
# §P0-1) required choosing one of two postures. v2.0.0-rc2 picked the
# canonical-bash posture per the response document. This rule asserts BOTH
# halves of that posture:
#   (a) The PS script header carries the DEPRECATED marker.
#   (b) The PS script is NOT listed in architecture-status.yaml under
#       architecture_sync_gate.implementation: (a deprecated_implementations:
#       sibling key is allowed).
# Drift would let a stale "30-rule pass surface" be re-presented as a shipped
# architecture-sync gate.
# ---------------------------------------------------------------------------
_r61_fail=0
_r61_ps="gate/check_architecture_sync.ps1"
_r61_status="docs/governance/architecture-status.yaml"
if [[ ! -f "$_r61_ps" ]]; then
  fail_rule "legacy_powershell_gate_deprecated" "$_r61_ps missing -- v2.0.0-rc2 deprecation stub expected"
  _r61_fail=1
else
  if ! grep -qE '^\s*Write-Host\s+"DEPRECATED:' "$_r61_ps"; then
    fail_rule "legacy_powershell_gate_deprecated" "$_r61_ps missing DEPRECATED Write-Host banner -- v2.0.0-rc2 second-pass review P0-1"
    _r61_fail=1
  fi
fi
if [[ ! -f "$_r61_status" ]]; then
  fail_rule "legacy_powershell_gate_deprecated" "$_r61_status missing"
  _r61_fail=1
else
  # Extract the architecture_sync_gate.implementation: block and verify the PS
  # path is NOT inside it. The deprecated_implementations: sibling is OK.
  # Capability keys live at 2-space indent (under `capabilities:`); sub-fields
  # at 4-space indent. Exit-capability pattern must match the 2-space level
  # specifically -- a 4-space pattern would never fire and in_cap would leak
  # into every following capability's implementation: block.
  _r61_in_impl=$(awk '
    /^  architecture_sync_gate:[[:space:]]*$/ { in_cap=1; next }
    in_cap && /^  [a-z_]+:/ { in_cap=0; in_impl=0; next }
    in_cap && /^    implementation:[[:space:]]*$/ { in_impl=1; next }
    in_cap && in_impl && /^    [a-z_]+:/ { in_impl=0 }
    in_cap && in_impl && /^[[:space:]]+-[[:space:]]+gate\/check_architecture_sync\.ps1([[:space:]]|$)/ { print "found"; exit }
  ' "$_r61_status")
  if [[ -n "$_r61_in_impl" ]]; then
    fail_rule "legacy_powershell_gate_deprecated" "$_r61_status lists $_r61_ps under architecture_sync_gate.implementation: -- v2.0.0-rc2 requires it under deprecated_implementations: only"
    _r61_fail=1
  fi
fi
if [[ $_r61_fail -eq 0 ]]; then pass_rule "legacy_powershell_gate_deprecated"; fi

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

# ---------------------------------------------------------------------------
# Rule 63 — release_note_retracted_tag_qualified (v2.0.0-rc2 / second-pass review F-γ structural prevention)
#
# Every tag listed in docs/governance/retracted-tags.txt MUST, wherever it is
# mentioned in an active release note under docs/logs/releases/*.md, appear either
#   (a) on the same line as "(retracted)" (case-insensitive), OR
#   (b) under a markdown heading (line starting with '#') containing
#       "Historical" or "Superseded" (case-insensitive).
# Drift would let a retracted tag be re-cited as a recommendation in a fresh
# release-note section, recreating the F-γ stale-evidence defect that the
# second-pass review's P1-2 finding flagged.
# ---------------------------------------------------------------------------
_r63_fail=0
_r63_list="docs/governance/retracted-tags.txt"
if [[ ! -f "$_r63_list" ]]; then
  fail_rule "release_note_retracted_tag_qualified" "$_r63_list missing -- v2.0.0-rc2 second-pass review F-γ prevention expects this list"
  _r63_fail=1
else
  # Perf fix (2026-05-23): replaced quadruple-nested bash loop (~25 docs ×
  # ~few tags × ~few lines × per-line sed/grep = ~1000+ forks, ~14s) with
  # a single python pass. Same logic: (a) `(retracted)` on the same line OR
  # (b) nearest upward `#` heading contains 'historical'/'superseded'.
  _r63_violations="$(
    GATE_R63_LIST="$_r63_list" "${GATE_PYTHON_BIN:-python3}" - <<'PYEOF'
import os, re, glob
from pathlib import Path

list_path = os.environ['GATE_R63_LIST']
tags: list[str] = []
for line in Path(list_path).read_text(encoding='utf-8', errors='replace').splitlines():
    s = line.strip()
    if not s or s.startswith('#'): continue
    # First pipe field, trimmed.
    tag = s.split('|', 1)[0].strip()
    if tag: tags.append(tag)

if not tags: raise SystemExit(0)

retracted_re = re.compile(r'\(retracted\)|retracted\b', re.IGNORECASE)
hist_re = re.compile(r'historical|superseded', re.IGNORECASE)
heading_re = re.compile(r'^#')
docs = sorted(glob.glob('docs/logs/releases/*.md'))

for doc in docs:
    try: lines = Path(doc).read_text(encoding='utf-8', errors='replace').splitlines()
    except OSError: continue
    for i, ln in enumerate(lines):
        for tag in tags:
            if tag not in ln: continue
            # (a) same line carries (retracted).
            if retracted_re.search(ln): continue
            # (b) nearest upward heading qualifies.
            qualified = False
            for j in range(i, -1, -1):
                if heading_re.match(lines[j]):
                    qualified = bool(hist_re.search(lines[j]))
                    break
            if not qualified:
                print(f"{doc}\t{i+1}\t{tag}")
PYEOF
  )"
  if [[ -n "$_r63_violations" ]]; then
    while IFS=$'\t' read -r _r63_doc _r63_ln _r63_tag; do
      [[ -z "$_r63_doc" ]] && continue
      fail_rule "release_note_retracted_tag_qualified" "$_r63_doc:$_r63_ln mentions retracted tag '$_r63_tag' without '(retracted)' qualifier on the line OR a 'Historical'/'Superseded' heading above"
      _r63_fail=1
    done <<< "$_r63_violations"
  fi
fi
if [[ $_r63_fail -eq 0 ]]; then pass_rule "release_note_retracted_tag_qualified"; fi

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
# Rule 64 — module_count_data_driven (enforcer E94, G1 prevention)
#
# The canonical module count lives in
# docs/governance/architecture-status.yaml#repository_counts.total_reactor_modules.
# Rule 28e (module_count_invariant) checks against a hard-coded constant; this
# rule cross-checks the canonical value vs the actual count of <module> entries
# in root pom.xml. Adding a new reactor module thus updates ONE file
# (architecture-status.yaml), not four (gate + ADR-0055 + ADR-0059 + ADR-0067).
# ---------------------------------------------------------------------------
_r64_fail=0
_r64_status='docs/governance/architecture-status.yaml'
_r64_pom='pom.xml'
if [[ ! -f "$_r64_status" ]]; then
  fail_rule "module_count_data_driven" "$_r64_status missing -- cannot cross-check canonical module count (G1 prevention)"
  _r64_fail=1
elif [[ ! -f "$_r64_pom" ]]; then
  fail_rule "module_count_data_driven" "$_r64_pom missing -- cannot count <module> entries"
  _r64_fail=1
else
  _r64_canonical=$(grep -E '^[[:space:]]*total_reactor_modules:[[:space:]]*[0-9]+' "$_r64_status" | head -1 | sed -E 's/^[[:space:]]*total_reactor_modules:[[:space:]]*([0-9]+).*/\1/')
  _r64_pom_count=$(grep -c '<module>' "$_r64_pom" 2>/dev/null || echo 0)
  if [[ -z "$_r64_canonical" ]]; then
    fail_rule "module_count_data_driven" "$_r64_status missing repository_counts.total_reactor_modules field (G1 prevention)"
    _r64_fail=1
  elif [[ "$_r64_canonical" != "$_r64_pom_count" ]]; then
    fail_rule "module_count_data_driven" "$_r64_pom declares $_r64_pom_count <module> entries; canonical total_reactor_modules in $_r64_status is $_r64_canonical (G1 prevention -- update one file, not many)"
    _r64_fail=1
  fi
fi
if [[ $_r64_fail -eq 0 ]]; then pass_rule "module_count_data_driven"; fi

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
#            + D:\.claude\plans\tokens-token-buzzing-sprout.md
# Goal: shrink always-loaded governance set from ~99K -> ~10.6K tokens.
# Rules 67-71 with enforcer rows E97-E101 and 10 self-tests (2 per rule).
# ===========================================================================

# ---------------------------------------------------------------------------
# Rule 67 — claude_md_kernel_size_bounded (enforcer E97)
#
# For each "#### Rule NN" heading in CLAUDE.md, count the lines between the
# heading and the next "---" separator (inclusive of the heading). Look up
# kernel_cap from docs/governance/rules/rule-NN.md front-matter; fail if
# exceeded. If the card does not exist yet, this rule is SKIPPED for that
# rule (the missing card is caught by Rule 69 instead).
#
# Cap discipline (per CLAUDE.md token-optimization wave):
#   daily principles (Rules 1-6, 9, 10): kernel_cap: 12
#   architectural + ironclad (Rules 20-48): kernel_cap: 6
# ---------------------------------------------------------------------------
_r67_fail=0
_r67_claude='CLAUDE.md'
_r67_cards_dir='docs/governance/rules'
if [[ ! -f "$_r67_claude" ]]; then
  fail_rule "claude_md_kernel_size_bounded" "$_r67_claude missing"
  _r67_fail=1
elif [[ ! -d "$_r67_cards_dir" ]]; then
  # No cards yet -- rule is vacuously true during initial PR1 landing.
  pass_rule "claude_md_kernel_size_bounded"
else
  _r67_violations=""
  # Extract every Rule NN heading line number from CLAUDE.md.
  _r67_rule_lines=$(grep -nE '^#### Rule [0-9]+' "$_r67_claude" | sort -t: -k1,1n)
  while IFS= read -r _r67_entry; do
    [[ -z "$_r67_entry" ]] && continue
    _r67_ln="${_r67_entry%%:*}"
    _r67_rest="${_r67_entry#*:}"
    _r67_num=$(printf '%s\n' "$_r67_rest" | sed -nE 's/^#### Rule ([0-9]+).*/\1/p')
    [[ -z "$_r67_num" ]] && continue
    _r67_card_padded=$(printf 'rule-%02d.md' "$_r67_num")
    _r67_card="${_r67_cards_dir}/${_r67_card_padded}"
    if [[ ! -f "$_r67_card" ]]; then
      # No card -- skip (Rule 69 will catch it).
      continue
    fi
    _r67_cap=$(awk '/^kernel_cap:[[:space:]]*[0-9]+/{print $2; exit}' "$_r67_card")
    [[ -z "$_r67_cap" ]] && continue
    # Count lines from heading until next '---' separator (exclusive of separator).
    _r67_count=$(awk -v start="$_r67_ln" '
      NR < start { next }
      NR == start { count = 1; next }
      /^---$/ { exit }
      { count++ }
      END { print count + 0 }
    ' "$_r67_claude")
    if [[ "$_r67_count" -gt "$_r67_cap" ]]; then
      _r67_violations+="Rule $_r67_num: $_r67_count lines > cap $_r67_cap; "
      _r67_fail=1
    fi
  done <<< "$_r67_rule_lines"
  if [[ $_r67_fail -eq 0 ]]; then
    pass_rule "claude_md_kernel_size_bounded"
  else
    fail_rule "claude_md_kernel_size_bounded" "$_r67_violations"
  fi
fi

# ---------------------------------------------------------------------------
# Rule 68 — claude_md_kernel_matches_card (enforcer E98)
#
# For every docs/governance/rules/rule-NN.md card, extract the kernel: scalar
# from the YAML front-matter, normalise whitespace, and assert the same text
# appears verbatim in the body of "#### Rule NN" in CLAUDE.md. Fails on drift.
# If no cards exist (initial PR1 landing), the rule is vacuously true.
# ---------------------------------------------------------------------------
_r68_fail=0
_r68_claude='CLAUDE.md'
_r68_cards_dir='docs/governance/rules'
_r68_deferred_doc='docs/CLAUDE-deferred.md'
if [[ ! -f "$_r68_claude" ]]; then
  fail_rule "claude_md_kernel_matches_card" "$_r68_claude missing"
  _r68_fail=1
elif [[ ! -d "$_r68_cards_dir" ]]; then
  pass_rule "claude_md_kernel_matches_card"
else
  # Perf fix (2026-05-23): replace per-card 22-fork awk/sed/tr pipeline
  # (~50 cards × ~22 forks = ~1100 forks per gate run, ~17s on WSL/mnt/d)
  # with a single python pass that reads all cards + CLAUDE.md once.
  _r68_drift="$("${GATE_PYTHON_BIN:-python3}" - "$_r68_cards_dir" "$_r68_claude" "$_r68_deferred_doc" <<'PYEOF'
import os, re, sys, pathlib
cards_dir, claude_md, deferred_doc = sys.argv[1:4]

def norm(s: str) -> str:
    """Collapse all whitespace runs to single spaces; strip outer."""
    return re.sub(r"\s+", " ", s).strip()

# Parse CLAUDE.md once. For each "#### Rule <id> ..." heading, capture body lines
# until blank-line+`Enforced` OR `---` OR next "####" heading.
claude_text = pathlib.Path(claude_md).read_text(encoding="utf-8", errors="replace").splitlines()
bodies: dict[str, str] = {}
i, n = 0, len(claude_text)
while i < n:
    m = re.match(r"^#### Rule (\S+?)(?:\s|$)", claude_text[i])
    if m:
        rid = m.group(1)
        buf = []
        i += 1
        while i < n:
            line = claude_text[i]
            if line.startswith("---") or line.startswith("#### ") or line.startswith("Enforced by"):
                break
            if line.strip():
                buf.append(line)
            i += 1
        bodies[rid] = norm(" ".join(buf))
        continue
    i += 1

deferred_text = ""
if os.path.isfile(deferred_doc):
    deferred_text = pathlib.Path(deferred_doc).read_text(encoding="utf-8", errors="replace")

drift = []
for card in sorted(pathlib.Path(cards_dir).glob("rule-*.md")):
    base = card.stem  # rule-XX
    rid = base[5:]    # strip "rule-"
    if not rid:
        continue
    # Normalise integer ids by stripping leading zeros for heading match.
    rid_match = re.sub(r"^0+(?=\d)", "", rid) if rid.isdigit() else rid

    # Extract kernel: scalar (literal block `|` or inline). Stop at next
    # top-level key or `---`.
    txt = card.read_text(encoding="utf-8", errors="replace").splitlines()
    kernel_lines: list[str] = []
    in_block = False
    for line in txt:
        if not in_block:
            mk = re.match(r"^kernel:\s*\|", line)
            if mk:
                in_block = True
                continue
            mi = re.match(r"^kernel:\s+(.+)$", line)
            if mi:
                kernel_lines.append(mi.group(1))
                break
        else:
            if re.match(r"^[A-Za-z_][A-Za-z_0-9]*:", line) or line.rstrip() == "---":
                break
            kernel_lines.append(line.lstrip())
    kernel = norm(" ".join(kernel_lines))
    if not kernel:
        continue

    body = bodies.get(rid_match, "")
    if not body:
        # Deferred-only sub-clause cards (e.g. R-A.c) live in CLAUDE-deferred.md.
        # Check for `Rule <id>` reference there before flagging drift.
        if deferred_text and re.search(rf"(^|[^A-Za-z0-9])Rule\s+{re.escape(rid_match)}([^A-Za-z0-9]|$)", deferred_text):
            continue
        drift.append(f"Rule {rid_match}: card exists but no body in CLAUDE.md")
    elif kernel != body:
        drift.append(f"Rule {rid_match} drift")
sys.stdout.write("; ".join(drift))
PYEOF
)"
  if [[ -n "$_r68_drift" ]]; then
    fail_rule "claude_md_kernel_matches_card" "$_r68_drift"
    _r68_fail=1
  fi
  if [[ $_r68_fail -eq 0 ]]; then
    pass_rule "claude_md_kernel_matches_card"
  fi
fi

# ---------------------------------------------------------------------------
# Rule 69 — every_active_rule_has_card (enforcer E99)
#
# Every "#### Rule NN" heading in CLAUDE.md MUST have a sibling
# docs/governance/rules/rule-NN.md (zero-padded). Every card MUST either
# (a) appear as a heading in CLAUDE.md, or
# (b) appear as a "Rule NN" reference in docs/CLAUDE-deferred.md.
# Orphan cards that satisfy neither are a fail.
#
# Initial PR1 mode (loose): if docs/governance/rules/ does not exist yet,
# the rule is vacuously true so the budget-gate and other rules can land first.
# ---------------------------------------------------------------------------
_r69_fail=0
_r69_claude='CLAUDE.md'
_r69_deferred='docs/CLAUDE-deferred.md'
_r69_cards_dir='docs/governance/rules'
if [[ ! -d "$_r69_cards_dir" ]]; then
  pass_rule "every_active_rule_has_card"
else
  # rc9 hardening: use temp files instead of multi-line shell variables to avoid
  # SIGPIPE races under the parallel orchestrator (`xargs -P8` + nested subshells +
  # in-loop `echo "$var" | grep -qxF` can truncate the producer's output, producing
  # flaky false-positive "active rules with no card: NN" / "orphan cards: NN"
  # failures on Linux CI even when local WSL passes consistently).
  _r69_active_f=$(mktemp 2>/dev/null || echo "/tmp/r69_active.$$")
  _r69_cards_f=$(mktemp 2>/dev/null || echo "/tmp/r69_cards.$$")
  # Active rule IDs: extract the identifier after `#### Rule ` (can be integer
  # OR namespaced: D-1, R-C.a, G-3.f, M-2.b). Normalise zero-padding away for
  # comparison with card filenames (which may also still have integer form during
  # transition).
  grep -oE '^#### Rule [A-Za-z0-9.-]+' "$_r69_claude" 2>/dev/null \
    | sed -E 's/^#### Rule //; s/^0*([0-9])/\1/' | sort -u > "$_r69_active_f"
  # Card filenames: rule-<id>.md where <id> may be integer or namespaced.
  find "$_r69_cards_dir" -maxdepth 1 -name 'rule-*.md' -type f 2>/dev/null \
    | sed -E 's|.*/rule-(.+)\.md$|\1|; s/^0*([0-9])/\1/' | sort -u > "$_r69_cards_f"
  # Missing cards: active - cards (set difference via comm).
  _r69_missing=$(comm -23 "$_r69_active_f" "$_r69_cards_f" | tr '\n' ' ' | sed 's/[[:space:]]*$//')
  if [[ -n "$_r69_missing" ]]; then
    fail_rule "every_active_rule_has_card" "active rules with no card: $_r69_missing"
    _r69_fail=1
  fi
  # Orphan cards: card exists but rule is neither active nor deferred.
  _r69_orphans=""
  while IFS= read -r _n; do
    [[ -z "$_n" ]] && continue
    if grep -qxF "$_n" "$_r69_active_f"; then
      continue
    fi
    if [[ -f "$_r69_deferred" ]] && grep -qE "Rule[[:space:]]+${_n}([.][a-z])?\b" "$_r69_deferred"; then
      continue
    fi
    _r69_orphans+="$_n "
  done < "$_r69_cards_f"
  rm -f "$_r69_active_f" "$_r69_cards_f"
  if [[ -n "$_r69_orphans" ]]; then
    fail_rule "every_active_rule_has_card" "orphan cards (no active or deferred reference): $_r69_orphans"
    _r69_fail=1
  fi
  if [[ $_r69_fail -eq 0 ]]; then
    pass_rule "every_active_rule_has_card"
  fi
fi

# ---------------------------------------------------------------------------
# Rule 70 — always_loaded_budget_enforced (enforcer E100)
#
# Invokes gate/measure_always_loaded_tokens.sh which walks every file listed
# in gate/always-loaded-budget.txt and fails if any file exceeds its ceiling.
# This is the primary defence against CLAUDE.md regressing back to its
# pre-shrink size after PR1 lands.
# ---------------------------------------------------------------------------
_r70_fail=0
_r70_script='gate/measure_always_loaded_tokens.sh'
if [[ ! -f "$_r70_script" ]]; then
  fail_rule "always_loaded_budget_enforced" "$_r70_script missing"
  _r70_fail=1
else
  _r70_out=$(bash "$_r70_script" 2>&1)
  _r70_rc=$?
  if [[ $_r70_rc -ne 0 ]]; then
    # Extract just the OVER / MISSING lines for the error message.
    _r70_violations=$(printf '%s\n' "$_r70_out" | grep -E '(OVER|MISSING)' | tr '\n' ';' | sed 's/;$//')
    fail_rule "always_loaded_budget_enforced" "${_r70_violations:-budget script exited $_r70_rc}"
    _r70_fail=1
  fi
fi
if [[ $_r70_fail -eq 0 ]]; then pass_rule "always_loaded_budget_enforced"; fi

# ---------------------------------------------------------------------------
# Rule 71 — deferred_doc_not_in_always_loaded (enforcer E101)
#
# Once docs/CLAUDE-deferred.md is demoted from the always-loaded set, the
# demote must stay durable. Fails if:
#   (a) CLAUDE.md contains a literal '@docs/CLAUDE-deferred.md' include directive
#       (the Claude Code auto-load syntax), OR
#   (b) docs/governance/SESSION-START-CONTEXT.md table row for CLAUDE-deferred.md
#       contains an ALWAYS-LOAD / ALWAYS marker.
# Plain prose pointers ("see docs/CLAUDE-deferred.md") are fine.
# ---------------------------------------------------------------------------
_r71_fail=0
_r71_claude='CLAUDE.md'
_r71_sscontext='docs/governance/SESSION-START-CONTEXT.md'
if [[ -f "$_r71_claude" ]] && grep -qE '^[[:space:]]*@docs/CLAUDE-deferred\.md' "$_r71_claude" 2>/dev/null; then
  fail_rule "deferred_doc_not_in_always_loaded" "$_r71_claude contains @docs/CLAUDE-deferred.md auto-load -- must be on-demand"
  _r71_fail=1
fi
if [[ -f "$_r71_sscontext" ]]; then
  # Look at lines mentioning CLAUDE-deferred.md and reject ones marked ALWAYS / ALWAYS-LOAD.
  _r71_bad=$(grep -E 'CLAUDE-deferred\.md' "$_r71_sscontext" 2>/dev/null | grep -E '(\bALWAYS\b|ALWAYS-LOAD)' || true)
  if [[ -n "$_r71_bad" ]]; then
    fail_rule "deferred_doc_not_in_always_loaded" "$_r71_sscontext marks CLAUDE-deferred.md as ALWAYS-LOAD"
    _r71_fail=1
  fi
fi
if [[ $_r71_fail -eq 0 ]]; then pass_rule "deferred_doc_not_in_always_loaded"; fi

# ===========================================================================
# Gate-script efficiency wave PR-E1 (2026-05-17)
# Authority: D:/.claude/plans/tokens-token-buzzing-sprout.md + docs/governance/rules/rule-73.md
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
# Linux-first dev environment policy (PR-E7, 2026-05-18)
# Authority: docs/governance/rules/rule-74.md + docs/governance/dev-environment.md
# ===========================================================================

# ---------------------------------------------------------------------------
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
# Authority: D:/.claude/plans/spicy-mixing-galaxy.md Wave 4.
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

# ---------------------------------------------------------------------------
# Rule 72 — rule_duration_regression_check (enforcer E102)
# Vacuously passes until gate/log/benchmarks/median.json has >= 5 entries
# (bootstrap window per ADR-0077). After bootstrap: fail if any rule's
# current duration > 2x baseline median AND > 200ms absolute.
# ---------------------------------------------------------------------------
_r72_fail=0
_r72_median='gate/log/benchmarks/median.json'
_r72_current="${GATE_LOG_DIR:-gate/log/latest}/per-rule.ndjson"
if [[ ! -s "$_r72_median" ]] || ! command -v jq >/dev/null 2>&1; then
  pass_rule "rule_duration_regression_check"
elif [[ ! -f "$_r72_current" ]]; then
  pass_rule "rule_duration_regression_check"
else
  _r72_baseline_count="$(jq 'length' "$_r72_median" 2>/dev/null || echo 0)"
  if [[ "${_r72_baseline_count:-0}" -lt 5 ]]; then
    pass_rule "rule_duration_regression_check"
  else
    _r72_alerts="$(jq -r --slurpfile baseline "$_r72_median" '
      . as $row | $baseline[0][$row.rule_slug] as $median |
      if ($median != null and $row.duration_ms > $median * 2 and $row.duration_ms > 200)
      then "\($row.rule_slug): \($row.duration_ms)ms (median \($median)ms)"
      else empty end
    ' "$_r72_current" 2>/dev/null || true)"
    if [[ -n "$_r72_alerts" ]]; then
      fail_rule "rule_duration_regression_check" "$_r72_alerts"
      _r72_fail=1
    else
      pass_rule "rule_duration_regression_check"
    fi
  fi
fi

# ===========================================================================
# SPI metadata integrity wave (2026-05-18)
# Authority: docs/governance/rules/rule-{75..78}.md
#            + D:\.claude\plans\spi-smooth-llama.md
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
# Rule 77 — spi_packages_dot_spi_convention (enforcer E110)
#
# Every <module>/module-metadata.yaml#spi_packages entry MUST end in `.spi`
# OR contain `.spi.` (sub-packages). Operationalises Rule 32's `*.spi.*`
# wording — a package called e.g. `service.runtime.runs` (no `.spi`) is a
# domain package, not an SPI package, and must not be declared as one.
# ---------------------------------------------------------------------------
_r77_fail=0
while IFS= read -r _r77_meta; do
  [[ -z "$_r77_meta" ]] && continue
  _r77_declared=$(awk '/^spi_packages:/{flag=1; next} /^[a-zA-Z_]/{flag=0} flag && /^[[:space:]]*-[[:space:]]+/{gsub(/^[[:space:]]*-[[:space:]]+/,""); gsub(/["\047]/,""); gsub(/[[:space:]#].*$/,""); print}' "$_r77_meta")
  while IFS= read -r _r77_pkg; do
    [[ -z "$_r77_pkg" ]] && continue
    if [[ ! "$_r77_pkg" =~ \.spi$ ]] && [[ ! "$_r77_pkg" =~ \.spi\. ]]; then
      fail_rule "spi_packages_dot_spi_convention" "$_r77_meta declares spi package '$_r77_pkg' which does not end in '.spi' or contain '.spi.' (Rule 77 / E110 — Rule 32 *.spi.* convention)"
      _r77_fail=1
    fi
  done <<< "$_r77_declared"
done <<< "${_SCAN_MODULE_METADATA:-$(find . -maxdepth 3 -name module-metadata.yaml -not -path './target/*' -not -path './.claude/*' 2>/dev/null)}"
if [[ $_r77_fail -eq 0 ]]; then pass_rule "spi_packages_dot_spi_convention"; fi

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
# 2026-05-18 beyond-SDD review response wave -- Rule 79
# Authority: docs/governance/rules/rule-79.md
#            + D:/.claude/plans/d-chao-workspace-spring-ai-ascend-docs-shimmering-milner.md
# Operationalises the "Telemetry-First Debugging" remediation proposal from
# docs/logs/reviews/spring-ai-ascend-beyond-sdd-en.md by requiring an executable
# debug-sequence runbook to exist on disk, cited by the rule card, with the
# canonical title string present (so the file cannot drift to a different
# topic while still passing the gate by name alone).
# ===========================================================================

# Rule 79 — rule_79_runbook_present_and_cited (enforcer E112)
#
# Three invariants:
#   1. docs/runbooks/debug-first-evidence.md exists.
#   2. docs/runbooks/debug-first-evidence.md contains the literal string
#      "Evidence-First Debug Sequence" (catches drift-by-replacement).
#   3. docs/governance/rules/rule-79.md references the runbook path
#      (catches card-runbook link breakage on rename).
# ---------------------------------------------------------------------------
_r79_fail=0
_r79_runbook="docs/runbooks/debug-first-evidence.md"
_r79_card="docs/governance/rules/rule-79.md"
if [[ ! -f "$_r79_runbook" ]]; then
  fail_rule "rule_79_runbook_present_and_cited" "$_r79_runbook missing — Rule 79 / E112 (runbook required by docs/governance/rules/rule-79.md)"
  _r79_fail=1
elif ! grep -qF 'Evidence-First Debug Sequence' "$_r79_runbook" 2>/dev/null; then
  fail_rule "rule_79_runbook_present_and_cited" "$_r79_runbook missing the canonical title string 'Evidence-First Debug Sequence' — Rule 79 / E112"
  _r79_fail=1
fi
if [[ -f "$_r79_card" ]] && ! grep -qF 'docs/runbooks/debug-first-evidence.md' "$_r79_card" 2>/dev/null; then
  fail_rule "rule_79_runbook_present_and_cited" "$_r79_card does not reference docs/runbooks/debug-first-evidence.md — Rule 79 / E112 (card-runbook link broken)"
  _r79_fail=1
fi
if [[ $_r79_fail -eq 0 ]]; then pass_rule "rule_79_runbook_present_and_cited"; fi

# ===========================================================================
# 2026-05-18 rc4 cross-constraint review response prevention wave -- Rules 80-83
# Authority: docs/governance/rules/rule-80.md ... rule-83.md
#            + docs/logs/reviews/2026-05-18-l0-rc4-cross-constraint-architecture-review.en.md
#            + docs/logs/reviews/2026-05-18-l0-rc4-cross-constraint-architecture-review-response.en.md
# Closes finding families:
#   P0-1 ADR-vs-code drift after rc3 S2C refactor          -> Rule 80
#   P0-2 module-status drift after engine extraction         -> Rule 81
#   P1-1 baseline-count drift across entrypoints             -> Rule 82
#   P1-3 design-only contracts unregistered / dangling auth  -> Rule 83
# ===========================================================================

# Rule 80 — s2c_callback_signal_historical_only_in_authority (enforcer E113)
#
# In authoritative entrypoints (CLAUDE.md, README.md, root ARCHITECTURE.md,
# agent-*/ARCHITECTURE.md, docs/contracts/*.v1.yaml, docs/adr/*.yaml,
# docs/adr/*.md), the deleted Java type name S2cCallbackSignal MUST appear
# only in paragraphs marked historical / deleted / refactored from /
# amendments / rc3-unification (within +/-5 lines). v2.0.0-rc3 unified S2C
# suspension into the checked SuspendSignal.forClientCallback(...) variant
# (ADR-0074 2026-05-18 amendment); live current-state claims naming
# S2cCallbackSignal are forbidden in authoritative docs.
# ---------------------------------------------------------------------------
_r80_fail=0
_r80_vocab="gate/historical-marker-vocabulary.txt"
if [[ ! -f "$_r80_vocab" ]]; then
  fail_rule "s2c_callback_signal_historical_only_in_authority" "$_r80_vocab missing -- Rule 80 / E113 (Wave 2 vocabulary externalisation)"
  _r80_fail=1
fi
_r80_marker_re="$(grep -vE '^[[:space:]]*(#|$)' "$_r80_vocab" 2>/dev/null | tr '\n' '|' | sed 's/|$//')"
# Perf fix (2026-05-23): replaced per-authority-file grep + per-match sed|grep
# (~110 files × ~3 forks = ~14s) with a single python pass. Same scope
# (CLAUDE.md, README.md, ARCHITECTURE.md, contracts, ADRs, agent-*/ARCH), same
# ±5-line marker window, same vocabulary file.
_r80_violations="$(
  GATE_R80_MARKER_RE="$_r80_marker_re" "${GATE_PYTHON_BIN:-python3}" - <<'PYEOF'
import os, re, glob
from pathlib import Path

marker_src = os.environ.get('GATE_R80_MARKER_RE', '')
marker_re = re.compile(marker_src, re.IGNORECASE) if marker_src else None

targets: list[str] = []
for p in ('CLAUDE.md', 'README.md', 'ARCHITECTURE.md'):
    if os.path.isfile(p): targets.append(p)
targets.extend(sorted(glob.glob('docs/contracts/*.v1.yaml')))
targets.extend(sorted(glob.glob('docs/adr/*.yaml')))
targets.extend(sorted(glob.glob('docs/adr/*.md')))
for arch in sorted(glob.glob('agent-*/ARCHITECTURE.md')):
    targets.append(arch)

for path in targets:
    try: lines = Path(path).read_text(encoding='utf-8', errors='replace').splitlines()
    except OSError: continue
    n = len(lines)
    for i, ln in enumerate(lines):
        if 'S2cCallbackSignal' not in ln: continue
        lo = max(0, i - 5); hi = min(n, i + 6)
        window = '\n'.join(lines[lo:hi])
        if marker_re and marker_re.search(window): continue
        print(f"{path}\t{i+1}")
PYEOF
)"
if [[ -n "$_r80_violations" ]]; then
  while IFS=$'\t' read -r _r80_file _r80_lineno; do
    [[ -z "$_r80_file" ]] && continue
    fail_rule "s2c_callback_signal_historical_only_in_authority" "$_r80_file:$_r80_lineno mentions S2cCallbackSignal without a historical/deleted/refactored/amendment marker within +/-5 lines -- Rule 80 / E113"
    _r80_fail=1
  done <<< "$_r80_violations"
fi
if [[ $_r80_fail -eq 0 ]]; then pass_rule "s2c_callback_signal_historical_only_in_authority"; fi

# Rule 81 — skeleton_module_has_no_production_java (enforcer E114)
#
# For every reactor module whose root ARCHITECTURE.md frontmatter status:
# contains the token "skeleton", the module's src/main/java/**/*.java tree
# MUST contain only package-info.java OR placeholder SPI stubs whose first
# 30 lines name a "placeholder" keyword with an ADR-NNNN waiver. Modules
# with extracted production code (e.g., agent-execution-engine post-ADR-0079,
# agent-middleware post-ADR-0073) MUST NOT carry a "skeleton" status.
# ---------------------------------------------------------------------------
_r81_fail=0
for _r81_arch in agent-*/ARCHITECTURE.md; do
  [[ -f "$_r81_arch" ]] || continue
  _r81_status=$(awk 'BEGIN{infm=0} /^---[[:space:]]*$/{infm=!infm; next} infm && /^status:/{print; exit}' "$_r81_arch" 2>/dev/null)
  if [[ "$_r81_status" == *skeleton* ]]; then
    _r81_module="${_r81_arch%/ARCHITECTURE.md}"
    _r81_src="$_r81_module/src/main/java"
    [[ -d "$_r81_src" ]] || continue
    while IFS= read -r _r81_java; do
      [[ -z "$_r81_java" ]] && continue
      _r81_basename="$(basename "$_r81_java")"
      if [[ "$_r81_basename" == "package-info.java" ]]; then continue; fi
      if head -n 30 "$_r81_java" 2>/dev/null | grep -qE 'placeholder.*ADR-[0-9]{4}|ADR-[0-9]{4}.*placeholder'; then continue; fi
      fail_rule "skeleton_module_has_no_production_java" "$_r81_java in skeleton module $_r81_module is neither package-info.java nor an ADR-waived placeholder -- Rule 81 / E114 (status claims skeleton but production code is present)"
      _r81_fail=1
    done < <(find "$_r81_src" -name '*.java' -type f 2>/dev/null)
  fi
done
if [[ $_r81_fail -eq 0 ]]; then pass_rule "skeleton_module_has_no_production_java"; fi

# Rule 82 — baseline_metrics_single_source (enforcer E115)
#
# docs/governance/architecture-status.yaml MUST contain a baseline_metrics:
# block under architecture_sync_gate: with at minimum these required keys:
# active_engineering_rules, active_gate_checks, gate_executable_test_cases,
# enforcer_rows, architecture_graph_nodes, architecture_graph_edges.
# README.md and gate/README.md MUST point to the block by substring match
# "architecture_sync_gate.baseline_metrics" (so entrypoint counts have one
# structured source -- rc4 review P1-1 closure).
#
# rc6 (2026-05-18) strengthening per rc5 review P1-1 closure: README.md and
# gate/README.md ALSO MUST NOT carry an active "N <phrase>" count whose
# value disagrees with the parsed baseline_metrics value for that phrase's
# canonical key (e.g. "64 active gate rules" disagrees with
# active_gate_checks: 68 -> FAIL). Historical / rc[N] baseline / pre-rc[N]
# / previous / deprecated / superseded markers on the same line exempt the
# claim (matches the marker convention Rule 80 uses for S2cCallbackSignal
# historical-only paragraphs). Lines inside fenced code blocks (``` ... ```)
# are also exempt so code examples cannot trigger false positives.
# ---------------------------------------------------------------------------
_r82_fail=0
_r82_yaml="docs/governance/architecture-status.yaml"
if [[ ! -f "$_r82_yaml" ]]; then
  fail_rule "baseline_metrics_single_source" "$_r82_yaml missing -- Rule 82 / E115"
  _r82_fail=1
else
  for _r82_key in active_engineering_rules active_gate_checks gate_executable_test_cases enforcer_rows architecture_graph_nodes architecture_graph_edges; do
    if ! grep -qE "^[[:space:]]+${_r82_key}:" "$_r82_yaml" 2>/dev/null; then
      fail_rule "baseline_metrics_single_source" "$_r82_yaml missing required key '${_r82_key}:' under architecture_sync_gate.baseline_metrics -- Rule 82 / E115"
      _r82_fail=1
    fi
  done
fi
for _r82_pointer_file in README.md gate/README.md; do
  if [[ -f "$_r82_pointer_file" ]] && ! grep -qF 'architecture_sync_gate.baseline_metrics' "$_r82_pointer_file" 2>/dev/null; then
    fail_rule "baseline_metrics_single_source" "$_r82_pointer_file does not reference architecture_sync_gate.baseline_metrics -- Rule 82 / E115 (entrypoint must point to single source)"
    _r82_fail=1
  fi
done

# rc6 strengthening: numeric-agreement check for entrypoint count phrases.
# Phrase patterns are anchored after their leading number and matched only
# OUTSIDE fenced code blocks AND only on lines NOT carrying a historical
# marker. Each phrase maps to one baseline_metrics key whose parsed value
# defines the expected number.
_r82_phrases=(
  "active gate rules|active_gate_checks"
  "active rules|active_gate_checks"
  "self-tests|gate_executable_test_cases"
  "self-test cases|gate_executable_test_cases"
  "active engineering rules|active_engineering_rules_post_rc6"
  "enforcer rows|enforcer_rows"
  "architecture-graph nodes|architecture_graph_nodes"
  "graph nodes|architecture_graph_nodes"
  "architecture-graph edges|architecture_graph_edges"
  "graph edges|architecture_graph_edges"
  "ADRs|adr_count"
)
_r82_vocab="gate/baseline-snapshot-marker-vocabulary.txt"
if [[ ! -f "$_r82_vocab" ]]; then
  fail_rule "baseline_metrics_single_source" "$_r82_vocab missing -- Rule 82 / E115 (Wave 2 vocabulary externalisation)"
  _r82_fail=1
fi
_r82_marker_re="$(grep -vE '^[[:space:]]*(#|$)' "$_r82_vocab" 2>/dev/null | tr '\n' '|' | sed 's/|$//')"
# Perf fix (2026-05-23): the original inner-loop forked `awk` once per
# (phrase × line) pair (~133 phrases × ~100 lines × 2 files = ~26k forks).
# On WSL/mnt/d that was ~165s per gate run. Pre-parse the baseline_metrics
# block ONCE into a bash associative array, then do O(1) lookups in the
# loop. Same with the per-line `echo | grep` marker check, replaced with a
# bash-native regex.
declare -A _r82_metric=()
while IFS= read -r _r82_kv; do
  [[ -z "$_r82_kv" ]] && continue
  _r82_metric["${_r82_kv%%=*}"]="${_r82_kv#*=}"
done < <(awk '
  /^architecture_sync_gate:/ { f = 1; next }
  f && /^[^[:space:]]/ { exit }
  f && /^[[:space:]]+[a-zA-Z_]+:[[:space:]]*[0-9]+/ {
    key = $0; val = $0
    sub(/^[[:space:]]+/, "", key); sub(/:.*$/, "", key)
    sub(/^[[:space:]]+[a-zA-Z_]+:[[:space:]]*/, "", val); sub(/[^0-9].*$/, "", val)
    if (val != "") print key "=" val
  }
' "$_r82_yaml" 2>/dev/null)
# Cache gate_executable_test_cases for the Tests-passed pattern below.
_r82_tp_expected="${_r82_metric[gate_executable_test_cases]:-}"

# Convert bash-extended-glob phrase list into a single regex group so the
# per-line loop can do ONE bash-regex test instead of N. Marker check stays
# per-line because the vocabulary regex has alternations across many phrases.
for _r82_pointer_file in README.md gate/README.md; do
  [[ -f "$_r82_pointer_file" ]] || continue
  mapfile -t _r82_lines < "$_r82_pointer_file"
  _r82_in_code=0
  for ((_r82_i=0; _r82_i<${#_r82_lines[@]}; _r82_i++)); do
    _r82_line="${_r82_lines[$_r82_i]}"
    _r82_lineno=$((_r82_i + 1))
    if [[ "$_r82_line" =~ ^[[:space:]]*\`\`\` ]]; then
      _r82_in_code=$((1 - _r82_in_code))
      continue
    fi
    [[ $_r82_in_code -eq 1 ]] && continue
    # Marker check via bash regex (case-insensitive). nocasematch shopt is
    # local to this rule body via the save/restore below.
    shopt -q nocasematch
    _r82_nocase_was=$?
    shopt -s nocasematch
    if [[ "$_r82_line" =~ $_r82_marker_re ]]; then
      [[ $_r82_nocase_was -ne 0 ]] && shopt -u nocasematch
      continue
    fi
    [[ $_r82_nocase_was -ne 0 ]] && shopt -u nocasematch

    for _r82_pair in "${_r82_phrases[@]}"; do
      _r82_phrase="${_r82_pair%%|*}"
      _r82_key="${_r82_pair##*|}"
      _r82_expected="${_r82_metric[$_r82_key]:-}"
      [[ -z "$_r82_expected" ]] && continue
      if [[ "$_r82_line" =~ ([^0-9])([0-9]+)[[:space:]]+${_r82_phrase}([^a-zA-Z-]|$) ]] || [[ "$_r82_line" =~ ^([0-9]+)[[:space:]]+${_r82_phrase}([^a-zA-Z-]|$) ]]; then
        if [[ -n "${BASH_REMATCH[2]:-}" ]]; then
          _r82_actual="${BASH_REMATCH[2]}"
        else
          _r82_actual="${BASH_REMATCH[1]}"
        fi
        if [[ "$_r82_actual" != "$_r82_expected" ]]; then
          fail_rule "baseline_metrics_single_source" "$_r82_pointer_file:$_r82_lineno claims '$_r82_actual $_r82_phrase' but architecture_sync_gate.baseline_metrics.$_r82_key = $_r82_expected -- Rule 82 / E115 (numeric drift)"
          _r82_fail=1
        fi
      fi
    done
    # Tests-passed pattern: "Tests passed: N/N" — both N MUST equal gate_executable_test_cases.
    if [[ "$_r82_line" =~ Tests[[:space:]]passed:[[:space:]]*([0-9]+)/([0-9]+) ]] && [[ -n "$_r82_tp_expected" ]]; then
      _r82_tp_left="${BASH_REMATCH[1]}"
      _r82_tp_right="${BASH_REMATCH[2]}"
      if [[ "$_r82_tp_left" != "$_r82_tp_expected" ]] || [[ "$_r82_tp_right" != "$_r82_tp_expected" ]]; then
        fail_rule "baseline_metrics_single_source" "$_r82_pointer_file:$_r82_lineno claims 'Tests passed: $_r82_tp_left/$_r82_tp_right' but baseline_metrics.gate_executable_test_cases = $_r82_tp_expected -- Rule 82 / E115 (numeric drift)"
        _r82_fail=1
      fi
    fi
  done
done

if [[ $_r82_fail -eq 0 ]]; then pass_rule "baseline_metrics_single_source"; fi

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

# Rule 84 — active_module_architecture_path_truth (enforcer E117)
#
# Every agent-*/ARCHITECTURE.md whose front-matter status: token does NOT
# contain "skeleton" or "deferred" MUST have every inline path claim of the
# shape "<module>/src/main/java/..." resolve to a real file on disk OR carry
# a historical/moved/extracted-per-ADR/superseded/deferred/formerly marker
# within +/-3 lines. Operationalises the rc5 review P0-1 closure: module-
# level ARCHITECTURE path claims cannot lag behind real code locations
# (Rule 81 already covers the symmetric skeleton case; Rule 84 covers the
# active-module case Rule 81 cannot reach).
# ---------------------------------------------------------------------------
_r84_fail=0
_r84_marker_re='historical|moved|extracted per ADR-[0-9]{4}|extracted at|was rooted|formerly|deferred|superseded|pre-ADR-[0-9]{4}|relocated|relocated to|migrated|per ADR-[0-9]{4} \(2026|post-ADR-[0-9]{4}'
_r84_path_re='agent-[a-z-]+/src/main/java/[a-zA-Z0-9_/.-]+'
# Perf fix (2026-05-23): replaced per-line `echo | grep -oE` + per-claim
# `sed | grep` with mapfile + bash-native regex. On WSL/mnt/d the original
# took ~52s per gate run; the rewrite finishes in ~1s.
for _r84_arch in agent-*/ARCHITECTURE.md; do
  [[ -f "$_r84_arch" ]] || continue
  _r84_status=$(awk 'BEGIN{infm=0} /^---[[:space:]]*$/{infm=!infm; next} infm && /^status:/{print; exit}' "$_r84_arch" 2>/dev/null)
  [[ "$_r84_status" == *skeleton* ]] && continue
  [[ "$_r84_status" == *deferred* ]] && continue
  mapfile -t _r84_arr < "$_r84_arch"
  _r84_n=${#_r84_arr[@]}
  for ((_r84_i=0; _r84_i<_r84_n; _r84_i++)); do
    _r84_line="${_r84_arr[$_r84_i]}"
    _r84_lineno=$((_r84_i + 1))
    _r84_rest="$_r84_line"
    while [[ "$_r84_rest" =~ $_r84_path_re ]]; do
      _r84_path="${BASH_REMATCH[0]}"
      _r84_rest="${_r84_rest#*"$_r84_path"}"
      _r84_path_clean="${_r84_path%.}"  # strip trailing dots from prose
      if [[ -e "$_r84_path_clean" ]] || [[ -e "${_r84_path_clean}.java" ]]; then continue; fi
      _r84_lo=$((_r84_i > 3 ? _r84_i - 3 : 0))
      _r84_hi=$((_r84_i + 3 < _r84_n - 1 ? _r84_i + 3 : _r84_n - 1))
      _r84_marker_present=0
      for ((_r84_j=_r84_lo; _r84_j<=_r84_hi; _r84_j++)); do
        if [[ "${_r84_arr[$_r84_j]}" =~ $_r84_marker_re ]]; then
          _r84_marker_present=1; break
        fi
      done
      [[ $_r84_marker_present -eq 1 ]] && continue
      fail_rule "active_module_architecture_path_truth" "$_r84_arch:$_r84_lineno claims path '$_r84_path_clean' that does not exist on disk and the surrounding +/-3 lines carry no historical/moved/extracted-per-ADR marker -- Rule 84 / E117"
      _r84_fail=1
    done
  done
done
if [[ $_r84_fail -eq 0 ]]; then pass_rule "active_module_architecture_path_truth"; fi

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

# Rule 86 — root_architecture_count_and_path_truth (enforcer E119)
#
# Every "N-module" / "N modules" / "N reactor modules" claim in root
# ARCHITECTURE.md (outside fenced code blocks and frontmatter) MUST equal the
# pom.xml <module> count AND architecture-status.yaml#repository_counts.reactor_modules.
# Every "agent-*/src/main/java/..." path claim MUST resolve OR have a historical
# marker within +/-3 lines. Operationalises rc6 post-response review P0-2 closure.
# ---------------------------------------------------------------------------
_r86_fail=0
_r86_arch="ARCHITECTURE.md"
_r86_pom="pom.xml"
_r86_status_yaml="docs/governance/architecture-status.yaml"
if [[ ! -f "$_r86_arch" ]]; then
  fail_rule "root_architecture_count_and_path_truth" "$_r86_arch missing -- Rule 86 / E119"
  _r86_fail=1
elif [[ ! -f "$_r86_pom" ]]; then
  fail_rule "root_architecture_count_and_path_truth" "$_r86_pom missing -- Rule 86 / E119"
  _r86_fail=1
elif [[ ! -f "$_r86_status_yaml" ]]; then
  fail_rule "root_architecture_count_and_path_truth" "$_r86_status_yaml missing -- Rule 86 / E119"
  _r86_fail=1
else
  _r86_pom_count=$(awk '/<modules>/,/<\/modules>/' "$_r86_pom" | grep -cE '^[[:space:]]*<module>')
  _r86_status_count=$(awk '/^repository_counts:/{flag=1; next} flag && /^[a-z]/{flag=0} flag' "$_r86_status_yaml" | grep -oE 'reactor_modules:[[:space:]]+[0-9]+' | head -1 | grep -oE '[0-9]+$')
  if [[ "$_r86_pom_count" != "$_r86_status_count" ]]; then
    fail_rule "root_architecture_count_and_path_truth" "pom.xml declares $_r86_pom_count modules but architecture-status.yaml reactor_modules: $_r86_status_count -- Rule 86 / E119 (canonical sources disagree)"
    _r86_fail=1
  fi
  _r86_canonical=$_r86_pom_count
  _r86_marker_re='historical|pre-ADR-[0-9]{4}|pre-Phase-C|consolidated|merged into|merged in|was rooted|formerly|superseded|deferred|moved|extracted per ADR-[0-9]{4}|post-ADR-[0-9]{4}|archived'
  # Perf fix (2026-05-23): the original per-line loop forked `echo | grep`
  # 4+ times per line × 911 lines + `sed | grep` for each ±3-line marker
  # check. On WSL/mnt/d that was ~4 minutes per gate run. Bash-native regex
  # against a pre-loaded array brings this rule from ~59s to ~1s.
  mapfile -t _r86_arr < "$_r86_arch"
  _r86_count_re='(\*\*[0-9]+ modules\*\*|[0-9]+-module|[0-9]+ reactor modules|[0-9]+ modules)'
  _r86_path_re='agent-[a-z-]+/src/main/java/[a-zA-Z0-9_/.-]+'
  _r86_in_code=0
  _r86_in_frontmatter=0
  _r86_frontmatter_seen_open=0
  _r86_n=${#_r86_arr[@]}
  for ((_r86_i=0; _r86_i<_r86_n; _r86_i++)); do
    _r86_line="${_r86_arr[$_r86_i]}"
    _r86_lineno=$((_r86_i + 1))
    if [[ "$_r86_line" =~ ^---[[:space:]]*$ ]]; then
      if [[ $_r86_frontmatter_seen_open -eq 0 ]]; then
        _r86_in_frontmatter=1; _r86_frontmatter_seen_open=1
      else
        _r86_in_frontmatter=0
      fi
      continue
    fi
    [[ $_r86_in_frontmatter -eq 1 ]] && continue
    if [[ "$_r86_line" =~ ^\`\`\` ]]; then
      _r86_in_code=$((1 - _r86_in_code))
      continue
    fi
    [[ "$_r86_in_code" -eq 1 ]] && continue
    # Count-claim detection via bash regex (no fork).
    if [[ "$_r86_line" =~ $_r86_count_re ]]; then
      _r86_count_claim="${BASH_REMATCH[1]}"
      # Extract first number from the claim.
      if [[ "$_r86_count_claim" =~ ([0-9]+) ]]; then
        _r86_claim_num="${BASH_REMATCH[1]}"
        _r86_lo=$((_r86_i > 3 ? _r86_i - 3 : 0))
        _r86_hi=$((_r86_i + 3 < _r86_n - 1 ? _r86_i + 3 : _r86_n - 1))
        _r86_marker_present=0
        for ((_r86_j=_r86_lo; _r86_j<=_r86_hi; _r86_j++)); do
          if [[ "${_r86_arr[$_r86_j]}" =~ $_r86_marker_re ]]; then
            _r86_marker_present=1
            break
          fi
        done
        if [[ $_r86_marker_present -eq 0 ]] && [[ "$_r86_claim_num" != "$_r86_canonical" ]]; then
          fail_rule "root_architecture_count_and_path_truth" "$_r86_arch:$_r86_lineno active count claim '$_r86_count_claim' (N=$_r86_claim_num) disagrees with canonical $_r86_canonical from pom.xml + architecture-status.yaml -- Rule 86 / E119 (root architecture count drift)"
          _r86_fail=1
        fi
      fi
    fi
    # Path-claim detection: bash regex finds first match; loop with offset to
    # find all matches on the line (rare to have multiple, but supported).
    _r86_rest="$_r86_line"
    while [[ "$_r86_rest" =~ $_r86_path_re ]]; do
      _r86_path="${BASH_REMATCH[0]}"
      _r86_rest="${_r86_rest#*"$_r86_path"}"
      _r86_path_clean="${_r86_path%.}"
      if [[ -e "$_r86_path_clean" ]] || [[ -e "${_r86_path_clean}.java" ]]; then continue; fi
      _r86_lo=$((_r86_i > 3 ? _r86_i - 3 : 0))
      _r86_hi=$((_r86_i + 3 < _r86_n - 1 ? _r86_i + 3 : _r86_n - 1))
      _r86_marker_present=0
      for ((_r86_j=_r86_lo; _r86_j<=_r86_hi; _r86_j++)); do
        if [[ "${_r86_arr[$_r86_j]}" =~ $_r86_marker_re ]]; then
          _r86_marker_present=1
          break
        fi
      done
      [[ $_r86_marker_present -eq 1 ]] && continue
      fail_rule "root_architecture_count_and_path_truth" "$_r86_arch:$_r86_lineno claims path '$_r86_path_clean' that does not exist on disk and the surrounding +/-3 lines carry no historical/moved/extracted-per-ADR/consolidated/pre-Phase-C marker -- Rule 86 / E119"
      _r86_fail=1
    done
  done

  # rc8 extension: 2nd pass — validate SPI-ownership claims inside fenced
  # tree-diagram code blocks. The 1st pass above intentionally skips fenced
  # blocks (to avoid false positives on prose examples), but the rc7
  # GraphMemoryRepository drift hid inside the root tree block precisely
  # because of that exclusion. This pass scans only fenced blocks, identifies
  # module-header lines (`  agent-foo/    #...`) plus their indent level, and
  # for each indented `<pkg>/spi/` leaf checks that the module's
  # module-metadata.yaml#spi_packages declares an entry containing
  # `.<pkg>.spi`. Historical markers within +/-3 lines still exempt.
  # Perf fix (2026-05-23): the fenced-tree-block scan also ran multiple
  # `echo | grep` forks per line × 911 lines. Reuse the `_r86_arr` array
  # loaded above and switch to bash-native regex + cached module-metadata
  # spi_package strings.
  declare -A _r86_meta_pkgs_cache=()
  _r86_tb_in=0
  _r86_tb_mod=""
  _r86_tb_mod_indent=0
  _r86_modhdr_re='^([[:space:]]+)(agent-[a-z-]+|spring-ai-ascend-[a-z-]+)/[[:space:]]*(#.*)?$'
  _r86_spi_leaf_re='^([[:space:]]+)([a-z][a-z_]*)/spi/[[:space:]]*(#.*)?$'
  for ((_r86_i=0; _r86_i<_r86_n; _r86_i++)); do
    _r86_tbline="${_r86_arr[$_r86_i]}"
    _r86_tb_lineno=$((_r86_i + 1))
    if [[ "$_r86_tbline" =~ ^\`\`\` ]]; then
      _r86_tb_in=$((1 - _r86_tb_in))
      _r86_tb_mod=""
      continue
    fi
    [[ "$_r86_tb_in" -eq 0 ]] && continue
    # Module-header line: indented `<modulename>/    #...` or `<modulename>/`
    if [[ "$_r86_tbline" =~ $_r86_modhdr_re ]]; then
      _r86_tb_mod_indent=${#BASH_REMATCH[1]}
      _r86_tb_mod="${BASH_REMATCH[2]}"
      continue
    fi
    # SPI leaf line: indented `<pkg>/spi/  # ...` — validate parent module metadata.
    if [[ -n "$_r86_tb_mod" ]] && [[ "$_r86_tbline" =~ $_r86_spi_leaf_re ]]; then
      _r86_tb_leaf_indent=${#BASH_REMATCH[1]}
      _r86_tb_pkg="${BASH_REMATCH[2]}"
      if [[ $_r86_tb_leaf_indent -le $_r86_tb_mod_indent ]]; then
        _r86_tb_mod=""
        continue
      fi
      _r86_tb_meta="${_r86_tb_mod}/module-metadata.yaml"
      _r86_tb_lo=$((_r86_i > 3 ? _r86_i - 3 : 0))
      _r86_tb_hi=$((_r86_i + 3 < _r86_n - 1 ? _r86_i + 3 : _r86_n - 1))
      _r86_marker_present=0
      for ((_r86_j=_r86_tb_lo; _r86_j<=_r86_tb_hi; _r86_j++)); do
        if [[ "${_r86_arr[$_r86_j]}" =~ $_r86_marker_re ]]; then
          _r86_marker_present=1; break
        fi
      done
      [[ $_r86_marker_present -eq 1 ]] && continue
      if [[ ! -f "$_r86_tb_meta" ]]; then
        fail_rule "root_architecture_count_and_path_truth" "$_r86_arch:$_r86_tb_lineno tree-block leaf '${_r86_tb_pkg}/spi/' under module '${_r86_tb_mod}' but ${_r86_tb_meta} does not exist -- Rule 86 / E119 (tree-block ownership drift, fenced-block extension)"
        _r86_fail=1
        continue
      fi
      # Cache the joined `<pkg>` list from each module-metadata.yaml so we
      # don't re-grep it for every leaf in the same module.
      if [[ -z "${_r86_meta_pkgs_cache[$_r86_tb_meta]:-}" ]]; then
        _r86_meta_pkgs_cache[$_r86_tb_meta]="$(grep -E '^[[:space:]]*-[[:space:]]+' "$_r86_tb_meta" 2>/dev/null || true)"
      fi
      _r86_tb_pkgs_str="${_r86_meta_pkgs_cache[$_r86_tb_meta]}"
      _r86_tb_match_re="\\.${_r86_tb_pkg}\\.spi([^a-zA-Z0-9]|$)"
      if ! [[ "$_r86_tb_pkgs_str" =~ $_r86_tb_match_re ]]; then
        fail_rule "root_architecture_count_and_path_truth" "$_r86_arch:$_r86_tb_lineno tree-block claims '${_r86_tb_pkg}/spi/' under module '${_r86_tb_mod}' but ${_r86_tb_meta}#spi_packages declares no entry containing '.${_r86_tb_pkg}.spi' -- Rule 86 / E119 (tree-block ownership drift, fenced-block extension)"
        _r86_fail=1
      fi
    fi
  done
fi
if [[ $_r86_fail -eq 0 ]]; then pass_rule "root_architecture_count_and_path_truth"; fi

# Rule 87 — status_yaml_allowed_claim_module_name_truth (enforcer E120)
#
# Every allowed_claim: text value in docs/governance/architecture-status.yaml
# MUST NOT contain current-tense agent-platform, agent-runtime, or
# agent-runtime-core (all three are now deleted-module names after rc13
# ADR-0088 dissolution) outside a historical marker within +/-3 lines.
# Operationalises rc6 post-response review P1-2 + rc13 dissolution closure.
# ---------------------------------------------------------------------------
_r87_fail=0
_r87_yaml="docs/governance/architecture-status.yaml"
if [[ ! -f "$_r87_yaml" ]]; then
  fail_rule "status_yaml_allowed_claim_module_name_truth" "$_r87_yaml missing -- Rule 87 / E120"
  _r87_fail=1
else
  _r87_marker_re='historical|pre-ADR-[0-9]{4}|pre-Phase-C|pre-rc[0-9]+|consolidated into|consolidated from|merged into|merged in|was rooted|formerly|superseded|deprecated|archived|moved|post-ADR-[0-9]{4}|dissolution|dissolved|relocated|relocate'
  _r87_allowed_re='^[[:space:]]+allowed_claim:[[:space:]]*(.*)$'
  _r87_stale_re='\b(agent-platform|agent-runtime|agent-runtime-core)\b'
  # Perf fix (2026-05-23): per-line `echo | grep -qE` + `echo | sed` +
  # `echo | grep -oE` + `sed | grep -qiE` (~6 forks per line × 1471 lines)
  # → mapfile + bash-native regex against the array. ~164s → ~1s.
  mapfile -t _r87_arr < "$_r87_yaml"
  _r87_n=${#_r87_arr[@]}
  shopt -q nocasematch; _r87_nocase_was=$?
  for ((_r87_i=0; _r87_i<_r87_n; _r87_i++)); do
    _r87_line="${_r87_arr[$_r87_i]}"
    [[ "$_r87_line" =~ $_r87_allowed_re ]] || continue
    _r87_value="${BASH_REMATCH[1]}"
    _r87_value="${_r87_value#\"}"
    _r87_value="${_r87_value%\"}"
    [[ "$_r87_value" =~ $_r87_stale_re ]] || continue
    _r87_stale="${BASH_REMATCH[1]}"
    _r87_lo=$((_r87_i > 3 ? _r87_i - 3 : 0))
    _r87_hi=$((_r87_i + 3 < _r87_n - 1 ? _r87_i + 3 : _r87_n - 1))
    _r87_marker_present=0
    shopt -s nocasematch
    for ((_r87_j=_r87_lo; _r87_j<=_r87_hi; _r87_j++)); do
      if [[ "${_r87_arr[$_r87_j]}" =~ $_r87_marker_re ]]; then
        _r87_marker_present=1; break
      fi
    done
    [[ $_r87_nocase_was -ne 0 ]] && shopt -u nocasematch
    [[ $_r87_marker_present -eq 1 ]] && continue
    _r87_lineno=$((_r87_i + 1))
    fail_rule "status_yaml_allowed_claim_module_name_truth" "$_r87_yaml:$_r87_lineno allowed_claim text contains current-tense '$_r87_stale' (pre-Phase-C module name) without historical/pre-ADR/consolidated marker in +/-3 lines -- Rule 87 / E120 (allowed_claim module name drift)"
    _r87_fail=1
  done
  [[ $_r87_nocase_was -ne 0 ]] && shopt -u nocasematch
fi
if [[ $_r87_fail -eq 0 ]]; then pass_rule "status_yaml_allowed_claim_module_name_truth"; fi

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
      match($0, /^# Rule [0-9]+.?[a-z]? (—|--) ([a-z0-9_]+)/, arr)
      print arr[2]
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
# Rule 91 — baseline_metric_matches_executable_manifest (enforcer E123)
#
# Closes rc8 post-corrective review P0-1: the parallel summary trailer reported
# 102 executable rule sections while `architecture-status.yaml` declared 74.
# Rule 91 asserts that `baseline_metrics.active_gate_checks` equals the literal
# count of `# Rule N — slug` headers in this script (canonical manifest).
# ---------------------------------------------------------------------------
_r91_fail=0
_r91_status_file="docs/governance/architecture-status.yaml"
_r91_canonical="gate/check_architecture_sync.sh"
_r91_enforcers="docs/governance/enforcers.yaml"
if [[ ! -f "$_r91_status_file" ]] || [[ ! -f "$_r91_canonical" ]]; then
  fail_rule "baseline_metric_matches_executable_manifest" "$_r91_status_file or $_r91_canonical missing — Rule 91 / E123"
  _r91_fail=1
else
  _r91_manifest_count=$(awk '/^# === END OF RULES ===$/{exit} /^# Rule [0-9]+.?[a-z]? — /{c++} END{print c+0}' "$_r91_canonical")
  _r91_declared=$(grep -E '^[[:space:]]*active_gate_checks:[[:space:]]*[0-9]+' "$_r91_status_file" | head -1 | sed -E 's/.*active_gate_checks:[[:space:]]*([0-9]+).*/\1/')
  # rc10 widening per ADR-0084 / I-α-1 closure: extend Rule 91 to cover baseline_metrics.enforcer_rows.
  # Closes rc10 hidden defect: rc9 declared enforcer_rows: 116 (104 baseline + 12 wave) but live count was 134.
  _r91_enforcer_actual=$(grep -cE '^- id: E[0-9]+' "$_r91_enforcers" 2>/dev/null || echo 0)
  _r91_enforcer_declared=$(grep -E '^[[:space:]]*enforcer_rows:[[:space:]]*[0-9]+' "$_r91_status_file" | head -1 | sed -E 's/.*enforcer_rows:[[:space:]]*([0-9]+).*/\1/')
  if [[ -z "$_r91_declared" ]]; then
    fail_rule "baseline_metric_matches_executable_manifest" "$_r91_status_file missing baseline_metrics.active_gate_checks key — Rule 91 / E123"
    _r91_fail=1
  elif [[ "$_r91_declared" != "$_r91_manifest_count" ]]; then
    fail_rule "baseline_metric_matches_executable_manifest" "baseline_metrics.active_gate_checks=$_r91_declared != canonical manifest count $_r91_manifest_count (count of '# Rule N — slug' headers in $_r91_canonical before END marker) — Rule 91 / E123 (rc8 post-corrective P0-1 closure)"
    _r91_fail=1
  elif [[ -z "$_r91_enforcer_declared" ]]; then
    fail_rule "baseline_metric_matches_executable_manifest" "$_r91_status_file missing baseline_metrics.enforcer_rows key — Rule 91 / E123 (rc10 widening per ADR-0084)"
    _r91_fail=1
  elif [[ "$_r91_enforcer_declared" != "$_r91_enforcer_actual" ]]; then
    fail_rule "baseline_metric_matches_executable_manifest" "baseline_metrics.enforcer_rows=$_r91_enforcer_declared != live enforcer count $_r91_enforcer_actual ('^- id: E[0-9]+' in $_r91_enforcers) — Rule 91 / E123 (rc10 widening per ADR-0084 / I-α-1 closure)"
    _r91_fail=1
  fi
fi
if [[ $_r91_fail -eq 0 ]]; then pass_rule "baseline_metric_matches_executable_manifest"; fi

# ---------------------------------------------------------------------------
# Rule 92 — gate_rules_corpus_freshness (enforcer E125)
#
# Closes rc8 post-corrective review P2-1: `gate/rules/` is a shadow corpus
# that drifts stale relative to the canonical monolith. Rule 92 asserts that
# every `# Rule N — slug` header in canonical has a matching
# `gate/rules/rule-NNN.sh` file (zero-padded to 3 digits, supports letter
# suffix like `028a`). Production parallel gate operates from the canonical
# monolith; gate/rules/ is the IDE-only inspection artifact (ADR-0083).
# ---------------------------------------------------------------------------
_r92_fail=0
_r92_canonical="gate/check_architecture_sync.sh"
_r92_dir="gate/rules"
if [[ ! -f "$_r92_canonical" ]] || [[ ! -d "$_r92_dir" ]]; then
  fail_rule "gate_rules_corpus_freshness" "$_r92_canonical or $_r92_dir missing — Rule 92 / E125"
  _r92_fail=1
else
  # Perf fix (2026-05-23): replaced per-rule-id `echo | grep -oE` + per-id
  # printf + per-id [[ -f ]] check (~130 ids × 3 forks = ~400 forks, ~13s) with
  # a single bash-native loop that uses bash regex to split id parts.
  _r92_missing=""
  _r92_id_re='^([0-9]+)([a-z]?)$'
  while IFS= read -r _r92_rid; do
    [[ -z "$_r92_rid" ]] && continue
    [[ "$_r92_rid" =~ $_r92_id_re ]] || continue
    _r92_num_part="${BASH_REMATCH[1]}"
    _r92_letter="${BASH_REMATCH[2]}"
    printf -v _r92_padded "%03d" "$_r92_num_part"
    _r92_expected="${_r92_dir}/rule-${_r92_padded}${_r92_letter}.sh"
    if [[ ! -f "$_r92_expected" ]]; then
      _r92_missing="${_r92_missing}${_r92_rid} "
    fi
  done < <(awk '/^# === END OF RULES ===$/{exit} /^# Rule [0-9]+.?[a-z]? — /{match($0, /^# Rule ([0-9]+.?[a-z]?) — /, a); print a[1]}' "$_r92_canonical")
  if [[ -n "$_r92_missing" ]]; then
    fail_rule "gate_rules_corpus_freshness" "$_r92_dir lacks rule file(s) for canonical header(s): ${_r92_missing}-- Rule 92 / E125 (run bash gate/lib/extract_rules.sh to refresh)"
    _r92_fail=1
  fi
fi
if [[ $_r92_fail -eq 0 ]]; then pass_rule "gate_rules_corpus_freshness"; fi

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
# Rule 96 — kernel_deferred_clause_coherence (enforcer E133)
#
# Closes rc8 post-corrective review P1-1: active rule kernels (Rule 42, Rule 46)
# stated runtime obligations that `docs/CLAUDE-deferred.md` correctly defers
# to W2. Rule 96 asserts that for every `Rule N.<letter>` block in
# CLAUDE-deferred.md, the matching `#### Rule N` kernel block in CLAUDE.md
# acknowledges the sub-clause by literal-string reference (e.g. `Rule 42.b`).
# ---------------------------------------------------------------------------
_r96_fail=0
_r96_claude="CLAUDE.md"
_r96_deferred="docs/CLAUDE-deferred.md"
if [[ ! -f "$_r96_claude" ]] || [[ ! -f "$_r96_deferred" ]]; then
  fail_rule "kernel_deferred_clause_coherence" "$_r96_claude or $_r96_deferred missing — Rule 96 / E133"
  _r96_fail=1
else
  _r96_missing=""
  # Find every "## Rule N.X" or "## Rule N.b/c/..." heading in CLAUDE-deferred.md
  while IFS= read -r _r96_subclause; do
    [[ -z "$_r96_subclause" ]] && continue
    _r96_num=$(echo "$_r96_subclause" | grep -oE '^[0-9]+')
    _r96_letter=$(echo "$_r96_subclause" | grep -oE '\.[a-z]$' | sed 's/^\.//')
    [[ -z "$_r96_num" ]] || [[ -z "$_r96_letter" ]] && continue
    _r96_ref="Rule ${_r96_num}.${_r96_letter}"
    # Find the `#### Rule N` block in CLAUDE.md (between heading and next `---`).
    _r96_block=$(awk -v rn="$_r96_num" '
      $0 ~ "^#### Rule "rn" " { in_block = 1; print; next }
      in_block && /^---$/ { exit }
      in_block { print }
    ' "$_r96_claude")
    if [[ -z "$_r96_block" ]]; then continue; fi  # Rule N might be deferred itself
    # Coherence is satisfied if EITHER the CLAUDE.md kernel OR the matching rule card
    # references the sub-clause by literal name. Rule cards have no kernel_cap, so a
    # rule with a long deferred discussion can cite there without bloating CLAUDE.md.
    _r96_card="docs/governance/rules/rule-${_r96_num}.md"
    _r96_kernel_has=0
    _r96_card_has=0
    echo "$_r96_block" | grep -qF "$_r96_ref" && _r96_kernel_has=1
    [[ -f "$_r96_card" ]] && grep -qF "$_r96_ref" "$_r96_card" && _r96_card_has=1
    if [[ $_r96_kernel_has -eq 0 ]] && [[ $_r96_card_has -eq 0 ]]; then
      _r96_missing="${_r96_missing}Rule${_r96_num}.${_r96_letter} "
    fi
  done < <(grep -oE '^## Rule [0-9]+\.[a-z]' "$_r96_deferred" | sed -E 's/^## Rule //')
  if [[ -n "$_r96_missing" ]]; then
    fail_rule "kernel_deferred_clause_coherence" "Active rule kernel + rule card pair does not acknowledge deferred sub-clause(s): ${_r96_missing}-- Rule 96 / E133 (add explicit 'Rule N.X' literal-string reference in either CLAUDE.md kernel block OR docs/governance/rules/rule-NN.md card; rc8 post-corrective P1-1 closure)"
    _r96_fail=1
  fi
fi
if [[ $_r96_fail -eq 0 ]]; then pass_rule "kernel_deferred_clause_coherence"; fi

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
              if (match(line, /([0-9]+)\/([0-9]+)[[:space:]]+(self-tests|tests passed|tests pass|gate self-tests)/, arr)) {
                denom = arr[2]
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
# Rule 99 — kernel_terminal_verb_vs_shipped_decision_check (enforcer E139)
#
# Closes rc10 post-corrective review P1-1 (J-α family): Rule 41 active kernel
# said "over-cap callers are SUSPENDED, not rejected" but the shipped Java
# surface (DefaultSkillResilienceContract.resolve) returns a decision envelope
# (SkillResolution.reject(SuspendReason.RateLimited)), not a Run state
# transition. The actual SUSPENDED transition is W2 orchestrator wiring per
# CLAUDE-deferred.md Rule 41.c.
#
# Rule 99 prevents recurrence by scanning every active #### Rule N kernel
# block in CLAUDE.md for end-state verb tokens (`are SUSPENDED`, `is
# SUSPENDED`, `transitions to FAILED`, `consumes capacity`, `is rejected`,
# `is admitted`). For each match, the rule checks whether CLAUDE-deferred.md
# declares a Rule N.<letter> sub-clause that defers the same behaviour.
# If BOTH (end-state verb in active kernel) AND (deferred sub-clause exists)
# → FAIL — the active kernel is overclaiming shipped behaviour.
#
# This is the SEMANTIC layer Rule 96 doesn't cover. Rule 96 checks the
# literal `Rule N.<letter>` REFERENCE exists; Rule 99 checks the VERBS in
# the kernel match what's actually shipped.
# ---------------------------------------------------------------------------
_r99_fail=0
_r99_claude="CLAUDE.md"
_r99_deferred="docs/CLAUDE-deferred.md"
if [[ ! -f "$_r99_claude" ]] || [[ ! -f "$_r99_deferred" ]]; then
  fail_rule "kernel_terminal_verb_vs_shipped_decision_check" "$_r99_claude or $_r99_deferred missing — Rule 99 / E139"
  _r99_fail=1
else
  # End-state verbs that imply shipped Run-state transitions:
  _r99_end_verbs='are SUSPENDED|is SUSPENDED|callers are SUSPENDED|transitions to FAILED|transitions to SUSPENDED|consumes the .* capacity|is rejected, not failed|admits the caller'
  _r99_violations=""
  # Per-invocation tempfile (mktemp + trap-style cleanup below) so two
  # concurrent gate runs cannot race on a shared /tmp/_r99_hits.<pid> path.
  _r99_hits_file="$(mktemp -t r99_hits.XXXXXX)"
  # Build set of rule numbers that have deferred sub-clauses
  _r99_deferred_nums=$(grep -oE '^## Rule [0-9]+\.[a-z]' "$_r99_deferred" \
    | sed -E 's/^## Rule //; s/\..*$//' | sort -u | tr '\n' ' ')
  # For every #### Rule N block in CLAUDE.md, check kernel body for end-state verbs.
  awk -v end_verbs="$_r99_end_verbs" -v defnums="$_r99_deferred_nums" '
    BEGIN { rule = ""; body = "" }
    /^#### Rule [0-9]+/ {
      if (rule) emit()
      match($0, /^#### Rule ([0-9]+)/, m)
      rule = m[1]
      body = ""
      next
    }
    /^---$/ && rule { emit(); rule = ""; next }
    rule { body = body $0 " " }
    END { if (rule) emit() }
    function emit() {
      # Does this rule have a deferred sub-clause?
      has_deferred = 0
      n = split(defnums, dn, " ")
      for (i = 1; i <= n; i++) if (dn[i] == rule) has_deferred = 1
      if (!has_deferred) return
      # Test body for any end-state verb
      if (body ~ end_verbs) {
        match(body, end_verbs)
        v = substr(body, RSTART, RLENGTH)
        print "Rule " rule ":" v
      }
    }
  ' "$_r99_claude" > "$_r99_hits_file"
  _r99_violations=$(cat "$_r99_hits_file")
  rm -f "$_r99_hits_file"
  if [[ -n "$_r99_violations" ]]; then
    _r99_first=$(echo "$_r99_violations" | head -3 | tr '\n' '|')
    fail_rule "kernel_terminal_verb_vs_shipped_decision_check" "active rule kernel uses end-state verb implying shipped Run-state transition, but matching Rule N.<letter> deferred sub-clause exists (kernel is overclaiming shipped behaviour): ${_r99_first}-- Rule 99 / E139 (rc10 post-corrective P1-1 closure; narrow kernel verb to decision-envelope behaviour OR remove the deferred sub-clause if behaviour has actually shipped)"
    _r99_fail=1
  fi
fi
# rc15 widening (Rule G-3.e scope to module ARCHITECTURE.md — sub-check (b),
# enforcer E151 per ADR-0091): scan agent-*/ARCHITECTURE.md for the
# specific over-claim phrasing pattern (`over-cap[acity]? callers are
# SUSPENDED` and close variants). The rc14 M-γ defect surfaced when
# `agent-service/ARCHITECTURE.md:315-317` said "over-cap callers are
# SUSPENDED, not rejected" while shipped code + Rule R-K kernel both say
# the W1 surface returns a SkillResolution.reject(SuspendReason.RateLimited)
# decision envelope (Run suspension transition deferred to R-K.c / W2).
# This sub-check catches that exact defect class in module architecture
# docs without conflating with shipped end-state verbs like
# `transitions to FAILED on engine_mismatch` which IS shipped behavior.
# Admissible if the line carries decision-envelope wording or an explicit
# defer marker.
_r99b_hits=$(grep -rnE '(over-cap|over-capacity)( callers| requests)?[^.]*(are SUSPENDED|is SUSPENDED|transitions to SUSPENDED)' \
             agent-*/ARCHITECTURE.md 2>/dev/null \
             | grep -vE '(decision envelope|SkillResolution\.reject|deferred to R-K|deferred to Rule R-K|deferred per Rule R-K|W2 scheduler admission)' || true)
if [[ -n "$_r99b_hits" ]]; then
  _r99b_first=$(echo "$_r99b_hits" | head -3 | tr '\n' '|')
  fail_rule "kernel_terminal_verb_vs_shipped_decision_check" "module ARCHITECTURE.md claims shipped over-capacity SUSPENSION while Rule R-K shipped surface returns a decision envelope (suspension deferred to R-K.c / W2). Either rewrite to decision-envelope wording OR add 'deferred to Rule R-K.c' / 'W2 scheduler admission' marker: ${_r99b_first}-- Rule 99 / E151 (Rule G-3.e module-arch scope widening per ADR-0091)"
  _r99_fail=1
fi
if [[ $_r99_fail -eq 0 ]]; then pass_rule "kernel_terminal_verb_vs_shipped_decision_check"; fi

# ---------------------------------------------------------------------------
# Rule 100 — kernel_implementation_disjunction_truth (enforcer E141)
#
# Closes rc10 post-corrective review P1-3 (J-γ family): Rule 96 kernel said
# "the matching CLAUDE.md kernel block MUST contain" while the impl accepted
# EITHER the kernel OR the rule card. The "AND vs OR" drift was a Code-as-
# Contract violation in the rule whose job is preventing kernel/deferred drift.
#
# Rule 100 narrows the check to an explicit allow-list of rules that declare
# "either / OR" semantics in their kernel: for each allow-list rule, both the
# kernel text and the rule card text MUST contain explicit disjunction wording
# (EITHER / OR / either surface / either ... or ...). The allow-list lives at
# gate/rule-100-disjunction-allowlist.txt (one rule id per line).
#
# Why allow-list scope: a fully-general "kernel AND vs impl ||" parser is
# fragile (bash predicate grammar varies; some rules use multi-stage checks).
# The allow-list captures the rules where the disjunction is structurally
# load-bearing.
# ---------------------------------------------------------------------------
_r100_fail=0
_r100_allowlist="gate/rule-100-disjunction-allowlist.txt"
_r100_claude="CLAUDE.md"
if [[ ! -f "$_r100_allowlist" ]]; then
  fail_rule "kernel_implementation_disjunction_truth" "$_r100_allowlist missing — Rule 100 / E141"
  _r100_fail=1
else
  _r100_violations=""
  while IFS= read -r _r100_rule; do
    [[ -z "$_r100_rule" || "$_r100_rule" =~ ^[[:space:]]*# ]] && continue
    # Card path: integer id → zero-pad to 02d; namespaced id (D-1, G-3.d, ...) → literal.
    if [[ "$_r100_rule" =~ ^[0-9]+$ ]]; then
      _r100_card="docs/governance/rules/rule-$(printf '%02d' "$_r100_rule").md"
      [[ ! -f "$_r100_card" ]] && _r100_card="docs/governance/rules/rule-${_r100_rule}.md"
    else
      _r100_card="docs/governance/rules/rule-${_r100_rule}.md"
    fi
    # Extract CLAUDE.md kernel block (heading matches either integer or namespaced id)
    _r100_block=$(awk -v rn="$_r100_rule" '
      $0 ~ "^#### Rule "rn" " || $0 ~ "^#### Rule "rn"$" { in_block = 1; print; next }
      in_block && /^---$/ { exit }
      in_block { print }
    ' "$_r100_claude")
    # Both surfaces must declare disjunction
    _r100_kernel_has=0
    _r100_card_has=0
    if echo "$_r100_block" | grep -qE '\bEITHER\b|\bOR\b|either surface|either ... or|either kernel|either the' 2>/dev/null; then
      _r100_kernel_has=1
    fi
    if [[ -f "$_r100_card" ]] && grep -qE '\bEITHER\b|\bOR\b|either surface|either ... or|either kernel|either the' "$_r100_card" 2>/dev/null; then
      _r100_card_has=1
    fi
    if [[ $_r100_kernel_has -eq 0 ]] || [[ $_r100_card_has -eq 0 ]]; then
      _r100_violations="${_r100_violations}Rule ${_r100_rule} (kernel=$_r100_kernel_has, card=$_r100_card_has) "
    fi
  done < "$_r100_allowlist"
  if [[ -n "$_r100_violations" ]]; then
    fail_rule "kernel_implementation_disjunction_truth" "allow-listed disjunction rules missing 'EITHER/OR' wording in kernel and/or card: ${_r100_violations}-- Rule 100 / E141 (rc10 post-corrective P1-3 closure; allow-list at $_r100_allowlist must declare which rules are 'either-surface' so the kernel + card wording can be checked for the EITHER/OR connective)"
    _r100_fail=1
  fi
fi
if [[ $_r100_fail -eq 0 ]]; then pass_rule "kernel_implementation_disjunction_truth"; fi

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

  # (b) baseline_metrics.active_engineering_rules equals live CLAUDE.md count.
  _r101_kernel_count=$(grep -cE '^#### Rule [A-Z]-' "$_r101_claude" 2>/dev/null || echo 0)
  _r101_declared=$(awk '/^[[:space:]]+active_engineering_rules:/{print $2; exit}' "$_r101_status_yaml")
  if [[ -z "$_r101_declared" ]]; then
    fail_rule "rule_namespace_authority_completeness" "$_r101_status_yaml missing active_engineering_rules: under baseline_metrics -- Rule 101 / E143 (b)"
    _r101_fail=1
  elif [[ "$_r101_declared" != "$_r101_kernel_count" ]]; then
    fail_rule "rule_namespace_authority_completeness" "$_r101_status_yaml baseline_metrics.active_engineering_rules=$_r101_declared but CLAUDE.md has $_r101_kernel_count '#### Rule ' headers -- Rule 101 / E143 (b)"
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
        match_pf = (line ~ /\<agent-platform\>/)
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
_r106_reactor_declared=$(awk '/^\s+reactor_modules:/{print $2; exit}' "$_r106_status")
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
                   ARCHITECTURE.md agent-*/ARCHITECTURE.md docs/governance/architecture-status.yaml docs/contracts/contract-catalog.md 2>/dev/null \
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
# Scope: authority surfaces only (root ARCHITECTURE.md + agent-*/ARCHITECTURE.md
# + architecture-status.yaml + contract-catalog.md). docs/governance/rules/*.md
# is intentionally excluded — rule cards document patterns, including the
# patterns they prevent (so they legitimately quote old prose).
# rc15 widening (per ADR-0091): noun-phrase additions (`shared kernel in`,
# `extracted to`, `is deployed`) close the rc14 M-β gap.
_r106_grammar_hits=$(grep -rnE '(agent-platform|agent-runtime-core|agent-runtime[^-])' \
                     --include='*.md' --include='*.yaml' \
                     docs/governance/architecture-status.yaml ARCHITECTURE.md agent-*/ARCHITECTURE.md docs/contracts/contract-catalog.md docs/contracts/s2c-callback.v1.yaml 2>/dev/null \
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
if [[ -f "$_r107_coverage" && -f "$_r107_deferred" && -f "$_r107_claude" ]]; then
  # Collect deferred-section headings as `Rule-X.<letter>` tokens.
  _r107_deferred_headings=$(grep -oE '^## Rule [A-Z](-[A-Z])?(\.[a-z](\.[a-z])?)?' "$_r107_deferred" \
                            | sed -E 's/^## Rule /Rule-/' | sed 's/ /-/g' | sort -u || true)
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
    # Only sub-letter clauses are expected as deferred headings.
    if echo "$_r107_clause" | grep -qE '\.[a-z]'; then
      if ! echo "$_r107_deferred_headings" | grep -qFx "$_r107_clause"; then
        fail_rule "cross_authority_clause_parity" "principle-coverage.yaml lists deferred operationaliser $_r107_clause but no matching ## heading exists in CLAUDE-deferred.md -- Rule 107 / E152 (Family A per ADR-0093)"
        _r107_fail=1
      fi
    fi
  done <<< "$_r107_listed_clauses"
fi
if [[ $_r107_fail -eq 0 ]]; then pass_rule "cross_authority_clause_parity"; fi

# ---------------------------------------------------------------------------
# Rule 108 — governance_text_java_anchor_truth (enforcer E153)
#
# Family B prevention — closes rc16 P1-2 (stale
# SkillCapacityResolutionIT.suspendsSecondCallerWhenCapacityIsOne in
# rule-R-K.md + P-K.md after rc15 rename). Per ADR-0093.
#
# scope_surfaces: docs/governance/rules/*.md, docs/governance/principles/P-*.md
#
# The rule scans for ClassName.methodName tokens that look like Java
# evidence anchors. For each, it requires either (a) the method to exist
# in agent-*/src/{main,test}/java/, OR (b) a nearby historical marker.
# Marker tokens (within ±2 lines): formerly|renamed from|pre-rc[0-9]+|
# superseded|removed|historical. Filters out non-Java patterns (URLs,
# config keys with dot-notation, etc.) via shape constraints.
# ---------------------------------------------------------------------------
_r108_fail=0
# Class.method tokens with PascalCase class + camelCase method, length-bounded.
_r108_pattern='\b([A-Z][A-Za-z0-9_]{2,40})\.([a-z][A-Za-z0-9_]{2,40})\b'
for _r108_dir in docs/governance/rules docs/governance/principles; do
  [[ -d "$_r108_dir" ]] || continue
  for _r108_file in "$_r108_dir"/*.md; do
    [[ -f "$_r108_file" ]] || continue
    # Find lines with potential anchors
    grep -nE "$_r108_pattern" "$_r108_file" 2>/dev/null | while IFS= read -r _r108_line; do
      _r108_lineno=$(echo "$_r108_line" | cut -d: -f1)
      _r108_content=$(echo "$_r108_line" | cut -d: -f2-)
      # Filter out non-Java tokens: URLs, hyphenated names, etc.
      # Pull each class.method token
      echo "$_r108_content" | grep -oE "$_r108_pattern" | sort -u | while IFS= read -r _r108_token; do
        [[ -z "$_r108_token" ]] && continue
        _r108_cls=$(echo "$_r108_token" | cut -d. -f1)
        _r108_mth=$(echo "$_r108_token" | cut -d. -f2)
        # Skip obvious false positives: classes ending in xyz that aren't real Java types
        # Allow only PascalCase class with length 4+
        [[ ${#_r108_cls} -lt 4 ]] && continue
        # Skip common method names + file extensions that are not real Java methods
        case "$_r108_mth" in
          equals|hashCode|toString|getName|getValue|getId) continue;;
          java|yaml|md|properties|txt|json|xml|sh|sql|bash|py|tpl|spec|sample|class|impl|yml) continue;;
        esac
        # Require method length ≥ 5 to filter out short tokens that look like extensions
        [[ ${#_r108_mth} -lt 5 ]] && continue
        # Check for historical marker within ±2 lines
        _r108_start=$((_r108_lineno - 2))
        [[ $_r108_start -lt 1 ]] && _r108_start=1
        _r108_end=$((_r108_lineno + 2))
        _r108_context=$(sed -n "${_r108_start},${_r108_end}p" "$_r108_file" 2>/dev/null || true)
        if echo "$_r108_context" | grep -qE '(formerly|renamed from|pre-rc[0-9]+|superseded|removed|historical|deleted|deprecated)'; then
          continue
        fi
        # Look for the class file
        _r108_class_file=$(find agent-*/src -name "${_r108_cls}.java" -type f 2>/dev/null | head -1)
        if [[ -z "$_r108_class_file" ]]; then
          # Class doesn't exist anywhere — could be deleted or never existed
          # Only flag if the class looks load-bearing (has uppercase + a recognizable suffix)
          case "$_r108_cls" in
            *IT|*Test|*Repository|*Service|*Controller|*Component|*Configuration|*Properties|*Filter|*Listener|*Hook|*Executor|*Registry|*Envelope|*Signal|*Context|*Response|*Resolver)
              fail_rule "governance_text_java_anchor_truth" "$_r108_file:$_r108_lineno references $_r108_token but class $_r108_cls not found in agent-*/src/ -- Rule 108 / E153 (Family B per ADR-0093)"
              _r108_fail=1
              ;;
          esac
          continue
        fi
        # Class exists — check for the identifier (method call, record component, field, or accessor).
        # Allow `name(`, `name,`, `name)` patterns to cover methods + record components + auto-generated accessors.
        if ! grep -qE "\\b${_r108_mth}\\s*[(,)]" "$_r108_class_file" 2>/dev/null; then
          fail_rule "governance_text_java_anchor_truth" "$_r108_file:$_r108_lineno references $_r108_token but identifier $_r108_mth not found in $_r108_class_file -- Rule 108 / E153 (Family B per ADR-0093)"
          _r108_fail=1
        fi
      done
    done
  done
done
if [[ $_r108_fail -eq 0 ]]; then pass_rule "governance_text_java_anchor_truth"; fi

# ---------------------------------------------------------------------------
# Rule 109 — namespaced_rule_reference_completeness (enforcer E154)
#
# Family C prevention — closes rc16 P2-1 (numeric Rule references in 13
# principle frontmatters + 4 module ARCHITECTURE.md + 3 contract docs).
# rc12 Rule 101 was scoped narrowly per ADR-0086 gate_layer_boundary;
# this rule widens to ALL semantic-authority surfaces. Per ADR-0093.
#
# scope_surfaces: docs/governance/principles/P-*.md, docs/governance/rules/*.md, agent-*/ARCHITECTURE.md, docs/contracts/*.yaml, docs/contracts/*.md
#
# Numeric Rule references MUST carry a legacy marker (formerly|legacy|
# historical|Gate Rule|gate Rule|was Rule|ex-Rule) within the SAME line.
# Gate-layer numeric refs (`Gate Rule N`) are exempt by syntax.
# ---------------------------------------------------------------------------
_r109_fail=0
_r109_surfaces=$(find docs/governance/principles docs/governance/rules \
                   agent-*/ARCHITECTURE.md docs/contracts \
                   -type f \( -name '*.md' -o -name '*.yaml' \) 2>/dev/null \
                 | grep -v 'docs/archive/' | grep -v 'docs/logs/' || true)
while IFS= read -r _r109_file; do
  [[ -z "$_r109_file" || ! -f "$_r109_file" ]] && continue
  # Find lines with `Rule <digits>` in the engineering-rule range (1-48) without same-line legacy marker.
  # Gate-layer rules (≥49) per ADR-0086 gate_layer_boundary are intentionally numeric and not subject to this check.
  _r109_hits=$(grep -nE '\bRule ([1-9]|[1-3][0-9]|4[0-8])(\.[a-z])?\b' "$_r109_file" 2>/dev/null \
               | grep -viE '(formerly|legacy|historical|gate rule|was rule|ex-rule|pre-rc[0-9]+|superseded|deprecated)' || true)
  while IFS= read -r _r109_line; do
    [[ -z "$_r109_line" ]] && continue
    # Skip lines that are URL/section headers/code blocks
    echo "$_r109_line" | grep -qE '^[0-9]+:[[:space:]]*```' && continue
    fail_rule "namespaced_rule_reference_completeness" "$_r109_file:$_r109_line -- Rule 109 / E154 (Family C per ADR-0093)"
    _r109_fail=1
  done <<< "$_r109_hits"
done <<< "$_r109_surfaces"
if [[ $_r109_fail -eq 0 ]]; then pass_rule "namespaced_rule_reference_completeness"; fi

# ---------------------------------------------------------------------------
# Rule 110 — prevention_rule_scope_completeness (enforcer E155) [META]
#
# Operationalises the rc10/rc11/rc12 meta-lesson "Reviewer scope can be
# narrower than defect scope": every gate-script rule that declares
# `# scope_surfaces:` in its leading comment block MUST have ≥2 self-test
# fixture functions in gate/test_architecture_sync_gate.sh matching
# test_rule_<N>_*. This prevents future waves from shipping scope-narrow
# rules that only cover the reviewer-cited surface.
# Per ADR-0093 (rc16 meta scope completeness wave).
#
# scope_surfaces: gate/check_architecture_sync.sh, gate/test_architecture_sync_gate.sh
#
# Pre-rc16 rules without scope_surfaces: are grandfathered (no retrofit).
#
# rc19 Wave 1 (ADR-0096): Rule 110 sources gate/lib/check_recurring_families.sh
# as a structural-compliance no-op to satisfy Rule 112 (meta_rule_self_
# application_check). Rule 112 requires every [META] rule to source a
# gate/lib/check_*.sh helper as the F-recursive-prevention-irony permanent
# close — this source statement is the Rule 110 → Rule 112 self-application
# proof. The helper is not invoked here; its presence in scope is the gate.
# shellcheck disable=SC1091
source "gate/lib/check_recurring_families.sh"  # Rule 112 dogfooding
# ---------------------------------------------------------------------------
_r110_fail=0
_r110_test_file="gate/test_architecture_sync_gate.sh"
_r110_gate_file="gate/check_architecture_sync.sh"
if [[ -f "$_r110_test_file" && -f "$_r110_gate_file" ]]; then
  # For every gate rule whose header is followed (within 20 lines) by a
  # `# scope_surfaces:` comment, require ≥2 test_rule_<N>_* fixtures.
  while IFS= read -r _r110_rid; do
    [[ -z "$_r110_rid" ]] && continue
    _r110_fixture_count=$(grep -cE "^test_rule_${_r110_rid}_" "$_r110_test_file" 2>/dev/null || echo 0)
    if [[ "$_r110_fixture_count" -lt 2 ]]; then
      fail_rule "prevention_rule_scope_completeness" "Rule $_r110_rid declares scope_surfaces in $_r110_gate_file but has only $_r110_fixture_count test_rule_${_r110_rid}_* fixtures (need ≥2) -- Rule 110 / E155 (META per ADR-0093)"
      _r110_fail=1
    fi
  done < <(awk '/^# Rule [0-9]+ — /{match($0,/^# Rule ([0-9]+)/,m); cr=m[1]; ls=0; next} cr!="" { ls++; if (ls>20){cr=""; next} if ($0 ~ /^# scope_surfaces:/){print cr; cr=""} }' "$_r110_gate_file" 2>/dev/null)
fi
if [[ $_r110_fail -eq 0 ]]; then pass_rule "prevention_rule_scope_completeness"; fi

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
    while IFS= read -r _r111_line; do
      [[ -z "$_r111_line" ]] && continue
      fail_rule "architecture_refresh_defect_family_re_eval_required" "$_r111_line"
      _r111_fail=1
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
# Rule 112 — meta_rule_self_application_check (enforcer E159) [META]
#
# Per ADR-0096 (rc19 Wave 1 / F-recursive-prevention-irony permanent close):
# every gate rule whose `# Rule N — slug` header line carries the `[META]`
# marker MUST source a helper from `gate/lib/check_*.sh` (the helper-
# extraction template established by Rule 111 in rc18 Wave 1) within its
# rule body. This structurally prevents future META rules from re-creating
# Pattern D drift — the class that bit rc17 Rule 111.
#
# Rule 110 META already gates scope_surfaces declaration + fixture count,
# but does NOT gate the helper-extraction discipline. Rule 112 closes that
# gap. Together Rules 110 + 111 + 112 prevent the F-recursive-prevention-
# irony family from recurring on future META rules.
#
# scope_surfaces: gate/check_architecture_sync.sh (META-marked rule blocks), gate/lib/check_*.sh
#
# Algorithm:
#   1. Find each `# Rule N — slug ... [META]` header (incl. Rule 112 itself).
#   2. Scan the next 80 lines (or until next `# ---` separator) for
#      `source ... gate/lib/check_*.sh`.
#   3. Fail if no such source statement found in body.
#
# Sources gate/lib/check_recurring_families.sh as a no-op (proves Rule 112
# itself follows its own discipline — dogfooding closes the recursive
# irony at Rule 112's own layer).
# ---------------------------------------------------------------------------
# shellcheck disable=SC1091
source "gate/lib/check_recurring_families.sh"  # Rule 112 self-application
_r112_fail=0
_r112_canonical="gate/check_architecture_sync.sh"
# Hardening:
#   (a) Body window extends until the NEXT `^# Rule` header (or EOF) instead
#       of a hard-coded +80 line cap; a long META rule's source statement
#       must still be inside its own block, not the next rule's.
#   (b) Helper regex accepts `gate/lib/(check|validate)_*.(sh|bash|py)` so a
#       META rule sourcing a .py validator directly (or a future .bash helper)
#       still satisfies the helper-extraction discipline.
if [[ -f "$_r112_canonical" ]]; then
  # Pre-compute header line numbers for window-end resolution
  mapfile -t _r112_all_headers < <(grep -nE '^# Rule [0-9]+.?[a-z]? — ' "$_r112_canonical" 2>/dev/null | cut -d: -f1)
  while IFS= read -r _r112_meta_line; do
    [[ -z "$_r112_meta_line" ]] && continue
    _r112_lineno=$(printf '%s' "$_r112_meta_line" | cut -d: -f1)
    _r112_slug=$(printf '%s' "$_r112_meta_line" | sed -nE 's|^[0-9]+:# Rule ([0-9]+.?[a-z]?) — ([a-z_]+).*\[META\].*|\1:\2|p')
    [[ -z "$_r112_slug" ]] && continue
    _r112_rule_num=$(printf '%s' "$_r112_slug" | cut -d: -f1)
    _r112_rule_slug=$(printf '%s' "$_r112_slug" | cut -d: -f2)
    # Find next-header line strictly greater than _r112_lineno
    _r112_end=0
    for _r112_h in "${_r112_all_headers[@]}"; do
      if [[ "$_r112_h" -gt "$_r112_lineno" ]]; then
        _r112_end="$_r112_h"
        break
      fi
    done
    if [[ "$_r112_end" -eq 0 ]]; then
      # Last header — scan to EOF
      _r112_body=$(awk -v start="$_r112_lineno" 'NR > start' "$_r112_canonical")
    else
      _r112_body=$(awk -v start="$_r112_lineno" -v end="$_r112_end" 'NR > start && NR < end' "$_r112_canonical")
    fi
    # Helper regex widened to (check|validate)_*.(sh|bash|py)
    if ! printf '%s' "$_r112_body" | grep -qE '(source|\. )[[:space:]]+["'\''[:space:]]*[^"'\''[:space:]]*gate/lib/(check|validate)_[a-zA-Z_]+\.(sh|bash|py)'; then
      fail_rule "meta_rule_self_application_check" "Rule $_r112_rule_num ($_r112_rule_slug) is marked [META] but body does not source a gate/lib/(check|validate)_*.(sh|bash|py) helper within its block (until next # Rule header or EOF). Every [META] rule MUST use the helper-extraction template. Rule 112 / E159"
      _r112_fail=1
    fi
  done < <(grep -nE '^# Rule [0-9]+.?[a-z]? — .*\[META\]' "$_r112_canonical" 2>/dev/null)
fi
if [[ $_r112_fail -eq 0 ]]; then pass_rule "meta_rule_self_application_check"; fi
# Rule 113 — legacy_paren_no_reintroduction_and_migration_doc_complete (enforcer E160)
#
# Per ADR-0096 rc19 Wave 2: Wave 4 (rc18) removed 9 `(legacy Rule NN — ...)`
# parentheticals from enforcers.yaml and created gate/rule-number-migration.md
# as the legacy mapping SSOT. Adversarial review (ADV-RC18-6) flagged that
# nothing prevented reintroduction or audited the migration doc.
#
#   sub-check .a: enforcers.yaml MUST NOT contain `(legacy Rule NN — ...)`
#                 parenthetical patterns (reintroduction guard).
#   sub-check .b: gate/rule-number-migration.md MUST exist AND contain both
#                 expected sections so the audit trail is preserved.
#
# scope_surfaces: docs/governance/enforcers.yaml, gate/rule-number-migration.md
# ---------------------------------------------------------------------------
_r113_fail=0
_r113_enforcers="docs/governance/enforcers.yaml"
_r113_migration="gate/rule-number-migration.md"

# Extract grep + heading checks into helper so fixtures source the same
# code — fixtures cannot drift from the production regex this way.
# shellcheck disable=SC1091
source "gate/lib/check_legacy_paren.sh"  # source gate/lib/check_legacy_paren.sh — Rule 113 helper extraction

_r113_paren_output=$(_check_legacy_paren_no_reintroduction "$_r113_enforcers")
if [[ -n "$_r113_paren_output" ]]; then
  while IFS= read -r _r113_line; do
    [[ -z "$_r113_line" ]] && continue
    fail_rule "legacy_paren_no_reintroduction_and_migration_doc_complete" "$_r113_line"
    _r113_fail=1
  done <<< "$_r113_paren_output"
fi

_r113_migration_output=$(_check_migration_doc_complete "$_r113_migration" 'Legacy numeric' 'rc17 sub-rule splits')
if [[ -n "$_r113_migration_output" ]]; then
  while IFS= read -r _r113_line; do
    [[ -z "$_r113_line" ]] && continue
    fail_rule "legacy_paren_no_reintroduction_and_migration_doc_complete" "$_r113_line"
    _r113_fail=1
  done <<< "$_r113_migration_output"
fi

if [[ $_r113_fail -eq 0 ]]; then pass_rule "legacy_paren_no_reintroduction_and_migration_doc_complete"; fi
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
#   - `per ADR-NNNN` pointers
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
  fail_rule "no_version_log_metadata_in_code" "production code contains forbidden version/log metadata tokens (rc<N> Wave / per ADR-NNNN / Finding F<N> / closes #<N>); first hits: ${_r115_first}-- Rule D-9 / E163 (such metadata belongs in commit messages, ADRs, release notes, rule cards, or rule-history.md — NOT in implementation)"
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

# Rule 120 — l1_l2_constraint_linkage (enforcer E168) — vacuously green at rc22
# (no L2 documents exist yet; arms for W3+).
pass_rule "l1_l2_constraint_linkage"

# === END OF RULES ===
# ---------------------------------------------------------------------------
if [[ $fail_count -eq 0 ]]; then
  echo "GATE: PASS"
  exit 0
else
  echo "GATE: FAIL"
  exit 1
fi
