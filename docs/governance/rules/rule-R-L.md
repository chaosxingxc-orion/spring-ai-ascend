---
rule_id: R-L
title: "Sandbox Permission Subsumption"
level: L1
view: physical
principle_ref: P-L
authority_refs: [ADR-0069]
enforcer_refs: [E71]
status: active
scope_phase: design
kernel_cap: 8
kernel: |
  **`docs/governance/sandbox-policies.yaml` MUST exist with a `default_policy:` block declaring at least six required keys: `outbound_network`, `filesystem_read`, `filesystem_write`, `cpu_cap_millicores`, `memory_cap_megabytes`, `wall_clock_cap_seconds`. Enforcement-mode keys (e.g. `syscalls`) MAY be added beyond the required six. Per-skill rows MUST NOT widen the default policy beyond what the physical sandbox can enforce. Runtime refusal of over-wide logical grants by `SandboxExecutor` is deferred to Rule R-L.b (W2) per `docs/CLAUDE-deferred.md`.**
---

## Motivation

The L0 motivation (LucioIT W1 §7.4): a logical authorization issued by the bus to a downstream node MUST NOT exceed what the physical sandbox enforces. Otherwise the bus's authorization is a paper grant — the sandbox refuses at runtime, but the failure mode is unpredictable. Subsumption makes the logical-vs-physical mapping 1:1.

## Cross-references

- Enforced by Gate Rule 52 (`sandbox_policies_yaml_present_and_wellformed`) — schema check.
- Architecture reference: ADR-0069 / LucioIT W1 §7.4.
- Runtime enforcement (SandboxExecutor refusing over-wide grants) deferred to W2 per `CLAUDE-deferred.md` 42.b.
- Cross-cited by Rule R-M sub-clause .d ([`rule-R-M.md`](rule-R-M.md)) envelope-propagation matrix — the S2C callback boundary shares the same logical-vs-physical authority discipline.
- Companion rule: Rule R-I ([`rule-R-I.md`](rule-R-I.md)) — Five-Plane Manifest (the `sandbox` plane that this rule protects).
