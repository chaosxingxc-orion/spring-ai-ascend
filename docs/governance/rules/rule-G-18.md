---
rule_id: G-18
title: "Traceability Chain Completeness"
level: L1
view: logical
principle_ref: P-A
authority_refs: [ADR-0156]
enforcer_refs: [E183]
status: active
scope_phase: review
kernel_cap: 8
product_claim: governance_infra
governance_infra: true
kernel: |
  Every `PC-NNN` declared in `product/claims.yaml` MUST have >=1 SAA Feature referencing it via `saa.productClaim`; every shipped feature MUST have >=1 `code_entrypoint_refs[]` + `test_refs[]` (subsumed from Rule G-15.d); every ADR with `product_claim:` MUST have its `product_claim` value match the SAA Feature(s) it authors via `saa.sourceAdr`. Advisory at Wave 5 landing; promotes to blocking when Rule G-21 placeholder count reaches zero.
---

## Motivation

The traceability chain is `ProductClaim → ProductFeature → ArchitectureFeature → FunctionPoint → Contract → CodeFact/TestFact → Rule/Enforcer`. Each step is a separate field on a separate artefact. Completeness — every claim has consumers, every consumer references its claim — is the load-bearing invariant.

## Composition

- **Rule G-16/G-17** — field presence + referential integrity.
- **Rule G-15.d** — CodeFact/TestFact subsumption.
- **Rule G-14** — feature lifecycle states.
