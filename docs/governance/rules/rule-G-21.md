---
rule_id: G-21
title: "ProductClaim Placeholder Decreasing"
level: L1
view: logical
principle_ref: P-A
authority_refs: [ADR-0156]
enforcer_refs: [E186]
status: active
scope_phase: review
kernel_cap: 8
product_claim: governance_infra
governance_infra: true
kernel: |
  Count of `product_claim_placeholder: true` markers in the corpus MUST decrease monotonically across Phase B cluster cycles. Reaching zero is the Phase B convergence signal AND the promotion trigger for Rules G-16, G-17, G-18, G-20 from advisory to blocking. Advisory at Wave 5 landing 2026-05-28; baseline is the Wave 4 backfill output count.
---

## Motivation

Wave 4 backfill of placeholders converts every existing artefact from "no product_claim field" to "`product_claim_placeholder: true`". Phase B cluster cycles then resolve each placeholder to a real PC-NNN or to `governance_infra:true`. The metric of how many placeholders remain is the Phase B progress signal. Reaching zero authorizes promotion of Rules G-16/G-17/G-18/G-20 to blocking.

## How it works

Each gate run records the placeholder count. The rule fails IF the current count is GREATER than the count recorded at the previous PR-merge (i.e. a regression). Decreasing count = pass; constant count = pass (Phase B not yet active); increasing count = fail (regression).

## Composition

- Gates the advisory→blocking transitions of Rules G-16, G-17, G-18, G-20.
- Provides Phase B convergence signal.
