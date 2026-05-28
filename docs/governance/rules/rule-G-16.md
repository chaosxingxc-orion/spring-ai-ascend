---
rule_id: G-16
title: "ProductClaim Referential Integrity"
level: L1
view: logical
principle_ref: P-A
authority_refs: [ADR-0156]
enforcer_refs: [E181]
status: active
scope_phase: review
kernel_cap: 8
product_claim: governance_infra
governance_infra: true
kernel: |
  Every `product_claim:` value in ADR YAML, rule card frontmatter, enforcer rows, SAA feature `saa.productClaim`, or contract frontmatter MUST resolve to a `PC-NNN` id declared in `product/claims.yaml` -- OR carry one of the explicit sentinel values `governance_infra:true` or `product_claim_placeholder:true`. Advisory at Wave 5 landing 2026-05-28; promotes to blocking when Rule G-21 placeholder count reaches zero.
---

## Motivation

The new authority model (2026-05-28) introduces ProductClaim (PC-NNN) as the binding axis. Every architecture/governance artefact answers "which product claim do I serve?" or marks itself `governance_infra:true`. Without referential integrity enforcement, the chain rots — claims get renamed, references stale.

## How it works

The gate scans the corpus for `product_claim:` field values, collects them, and cross-checks each against the `id:` entries in `product/claims.yaml`. References to non-existent claims fail. References pointing at sentinels (`governance_infra` / `product_claim_placeholder`) pass.

## Composition

- **Rule G-17** (no_orphan_artefacts) — covers the inverse direction (missing field).
- **Rule G-18** (traceability_chain_completeness) — ensures every PC has consumers.
- **Rule G-21** (productclaim_placeholder_decreasing) — gates the advisory→blocking promotion of G-16/G-17.
