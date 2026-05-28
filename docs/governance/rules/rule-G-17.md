---
rule_id: G-17
title: "No Orphan Artefacts"
level: L1
view: logical
principle_ref: P-A
authority_refs: [ADR-0156]
enforcer_refs: [E182]
status: active
scope_phase: review
kernel_cap: 8
product_claim: governance_infra
governance_infra: true
kernel: |
  Every ADR / rule card / enforcer / SAA Feature / contract MUST declare one of: (a) `product_claim:` with a PC-NNN value, (b) `governance_infra: true`, (c) `product_claim_placeholder: true` (Wave 4 backfill marker). Missing all three = orphan artefact. Advisory at Wave 5 landing 2026-05-28; threshold tightens through Wave 4 backfill; promotes to blocking when Rule G-21 placeholder count reaches zero.
---

## Motivation

Orphan artefacts — those with no answer to "which product claim do I serve?" — accumulate and erode the traceability chain. This rule forces every artefact to declare its allegiance: product value, meta-machinery, or pending classification.

## How it works

The gate scans every ADR YAML, rule card, enforcer row, SAA Feature, and contract YAML for one of the three field/sentinel forms. Missing all three = orphan. Orphan count is emitted as info; threshold-based fail-closed pending Wave 4 backfill.

## Composition

- **Rule G-16** (productclaim_referential_integrity) — ensures the field VALUES are valid.
- **Rule G-21** (productclaim_placeholder_decreasing) — gates the advisory→blocking promotion.
