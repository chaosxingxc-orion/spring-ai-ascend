---
rule_id: R-L
title: "Sandbox Permission Subsumption"
level: L1
view: physical
principle_ref: P-L
authority_refs: [ADR-0069]
enforcer_refs: [E71]
status: active
product_claim: "PC-003"
scope_phase: design
kernel_cap: 8
kernel: |
  **`docs/governance/sandbox-policies.yaml` MUST exist with a `default_policy:` block declaring at least six required keys: `outbound_network`, `filesystem_read`, `filesystem_write`, `cpu_cap_millicores`, `memory_cap_megabytes`, `wall_clock_cap_seconds`. Enforcement-mode keys (e.g. `syscalls`) MAY be added beyond the required six. Per-skill rows MUST NOT widen the default policy beyond what the physical sandbox can enforce. Runtime refusal of over-wide logical grants by `SandboxExecutor` is deferred to Rule R-L.b (W2) per `docs/CLAUDE-deferred.md`.**
deferred_sub_clauses:
  - id: ".b"
    title: "SandboxExecutor Subsumption Runtime Check [Deferred to W2]"
    re_introduction_trigger: "first sandboxed skill ships (`code-interpreter` or `untrusted-tool`) in research or prod posture (target: W2)."
    deferred_body: |
      **Rule (draft)**: `SandboxExecutor.execute(skill, logical_grant)` MUST cross-reference `logical_grant` against the per-skill row in `docs/governance/sandbox-policies.yaml`. If `logical_grant` declares any capability (outbound destination, filesystem path, syscall) wider than what the per-skill physical limit allows, the executor MUST reject the call with `SandboxSubsumptionViolation` BEFORE invoking the sandboxed code. Test: a synthetic request granting `outbound_network: allow_all` to a skill whose YAML declares an allowlist of `["api.openai.com:443"]` MUST be rejected.

      Composes with: ARCHITECTURE.md §7.4; ADR-0069; Rule R-L; LucioIT W1 §7.4.
    relates_to: ["ADR-0069", "Rule R-L", "ARCHITECTURE.md §7.4", "LucioIT W1 §7.4"]
  - id: ".legacy26"
    title: "(legacy Rule 26) Skill Lifecycle Conformance [Deferred to W2]"
    re_introduction_trigger: "first `Skill` SPI implementation committed (target: W2)."
    deferred_body: |
      **Rule**: Every `Skill` implementation MUST honour the complete lifecycle contract defined in ADR-0030:

      1. **Mandatory init**: `Skill.init(SkillContext)` MUST be called before `execute`. An ArchUnit test (`SkillLifecycleConformanceTest`) asserts no class outside `skill.spi.*` calls `execute()` without a preceding `init()` in the same execution context.
      2. **Suspend/resume pair**: when a Run is suspended, `Skill.suspend(SkillContext) → SkillResumeToken` MUST be called on any Skill holding external resources (DB connections, file handles, HTTP sessions). Resources must be released at `suspend` and reacquired at `resume`.
      3. **Mandatory teardown**: `Skill.teardown(SkillContext)` MUST be called on all code paths — normal completion, exception, and cancellation. Implement using try-finally in the execution harness.
      4. **Cost receipt**: every `Skill.execute` MUST return a `SkillCostReceipt` capturing `inputTokens`, `outputTokens`, `wallClockMs`, `cpuMillis`, and optionally `currencyCode`/`cost`. The harness aggregates receipts and attaches them to the Run.

      Composes with: ARCHITECTURE.md §4 #27 (`skill_spi_lifecycle_resource_matrix`); ADR-0030; legacy Rule 13 (P1 cost-of-use, escalated 2026-05-28).
    relates_to: ["ADR-0030", "ADR-0086", "legacy Rule 26", "Rule R-L"]
  - id: ".legacy27"
    title: "(legacy Rule 27) Untrusted Skill Sandbox Mandate [Deferred to W3]"
    re_introduction_trigger: "first `UNTRUSTED`-tier `Skill` implementation committed in research or prod posture (target: W3)."
    deferred_body: |
      **Rule**: In `research` or `prod` posture, any `Skill` with `SkillTrustTier.UNTRUSTED` MUST be routed through a non-`NoOpSandboxExecutor` implementation:

      1. **Startup gate**: on application startup in `research`/`prod` posture, if any registered Skill carries `UNTRUSTED` trust tier, the container MUST assert that a non-NoOp `SandboxExecutor` bean is present. Missing sandbox → startup failure with clear error message referencing ADR-0030.
      2. **Posture model**: `dev` posture emits a `[WARN]` log when `UNTRUSTED` skills execute without a real sandbox (allows iteration without Docker/GraalVM setup). `research`/`prod` posture fails-closed per Rule D-6.
      3. **VETTED bypass**: `SkillTrustTier.VETTED` skills may route through `NoOpSandboxExecutor` in all postures. Trust-tier assignment is declared at skill registration (via the Skill SPI's metadata accessor — the exact method name was design-only when the legacy rule was authored; the current `Skill` interface uses the `kind()` discriminator with UNTRUSTED_* kinds per ADR-0122/0127) and is immutable at runtime.

      Composes with: ARCHITECTURE.md §4 #27 (`skill_spi_lifecycle_resource_matrix`); ADR-0030; ADR-0018 (`SandboxExecutor` SPI); Rule D-6 (posture-aware defaults).
    relates_to: ["ADR-0030", "ADR-0018", "ADR-0086", "legacy Rule 27", "Rule R-L", "Rule D-6"]
---

## Motivation

The L0 motivation (LucioIT W1 §7.4): a logical authorization issued by the bus to a downstream node MUST NOT exceed what the physical sandbox enforces. Otherwise the bus's authorization is a paper grant — the sandbox refuses at runtime, but the failure mode is unpredictable. Subsumption makes the logical-vs-physical mapping 1:1.

## Cross-references

- Enforced by Gate Rule 52 (`sandbox_policies_yaml_present_and_wellformed`) — schema check.
- Architecture reference: ADR-0069 / LucioIT W1 §7.4.
- Runtime enforcement (SandboxExecutor refusing over-wide grants) deferred to W2 per `CLAUDE-deferred.md` 42.b.
- Cross-cited by Rule R-M sub-clause .d ([`rule-R-M.md`](rule-R-M.md)) envelope-propagation matrix — the S2C callback boundary shares the same logical-vs-physical authority discipline.
- Companion rule: Rule R-I ([`rule-R-I.md`](rule-R-I.md)) — Five-Plane Manifest (the `sandbox` plane that this rule protects).
