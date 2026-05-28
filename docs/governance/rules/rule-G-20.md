---
rule_id: G-20
title: "Governance-Infra Honesty"
level: L1
view: logical
principle_ref: P-A
authority_refs: [ADR-0156]
enforcer_refs: [E185]
status: active
scope_phase: review
kernel_cap: 8
product_claim: governance_infra
governance_infra: true
kernel: |
  An artefact (rule card, ADR, contract, feature) marked `governance_infra: true` MUST NOT use product-value vocabulary in its body prose — `customer`, `beneficiary`, `user-facing claim`, `saves time/cost`, `delivers value`. Words reserved for product-claim-bound artefacts. Advisory at Wave 5 landing 2026-05-28; promotes to blocking when Rule G-21 placeholder count reaches zero.
---

## Motivation

A governance-infra artefact serves the meta-machinery (gates, audits, rule registries), not customers. If it pretends to serve product value by using customer-facing vocabulary, the traceability chain becomes meaningless. Honesty is the structural property that keeps `governance_infra:true` from being a junk-drawer escape hatch.

## Composition

- **Rule G-17** (no_orphan_artefacts) — established the `governance_infra:true` sentinel.
- This rule prevents misuse of that sentinel.
