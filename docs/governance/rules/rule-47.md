---
rule_id: 47
title: "Evolution Scope Default Boundary"
level: L1
view: development
principle_ref: P-M
authority_refs: [ADR-0075]
enforcer_refs: [E86, E87]
status: active
kernel_cap: 8
kernel: |
  **Every emitted `RunEvent` (when the variant ships in W2 per ADR-0022) MUST declare its `EvolutionExport` value (Java enum constants `IN_SCOPE | OUT_OF_SCOPE | OPT_IN`, mirrored by the yaml section names `in_scope | out_of_scope_default | opt_in_export` in `docs/governance/evolution-scope.v1.yaml`). Out-of-scope events MUST NOT be persisted by the evolution plane. Opt-in export requires the future `telemetry-export.v1.yaml` contract (W3 placeholder declared in `evolution-scope.v1.yaml#opt_in_export.contract_required`).**
---

## Motivation

Authority: ADR-0075 / P-M. Part of the W2.x Engine Contract Structural Wave. The evolution mechanism manages only server-controlled execution scope by default — production agent runs must not silently feed an evolution / ML training plane without explicit declaration. The discriminator (IN_SCOPE | OUT_OF_SCOPE | OPT_IN) makes that declaration a first-class type-level decision rather than a runtime configuration knob.

## Cross-references

- Enforced by Gate Rule 59 (`evolution_scope_yaml_present_and_wellformed` — 3-discriminator-block + telemetry-export-ref schema check, enforcer E86) and ArchUnit E87 (`EveryRunEventDeclaresEvolutionExportTest`, armed-empty until W2 RunEvent variants ship).
- Schema source: `docs/governance/evolution-scope.v1.yaml`.
- Future contract placeholder: `telemetry-export.v1.yaml` (W3, referenced at `evolution-scope.v1.yaml#opt_in_export.contract_required`).
- Companion rule: Rule 48 ([`rule-48.md`](rule-48.md)) — Schema-First Domain Contracts (EvolutionExport is a domain enum that must obey schema-first shape).
- Related architecture: ADR-0022 (RunEvent variants ship in W2).
