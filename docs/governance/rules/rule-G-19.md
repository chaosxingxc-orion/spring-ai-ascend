---
rule_id: G-19
title: "Auto-Load Tier Integrity"
level: L1
view: logical
principle_ref: P-A
authority_refs: [ADR-0156]
enforcer_refs: [E184]
status: active
scope_phase: review
kernel_cap: 8
product_claim: governance_infra
governance_infra: true
kernel: |
  `gate/always-loaded-budget.txt` MUST contain `product/PRODUCT.md` at a non-zero ceiling AND `CLAUDE.md` MUST have a ceiling `<= 12000` bytes. Enforces the Tier-1 product-authority + collab-only kernel discipline established by the 2026-05-28 surgery. Blocking from Wave 5 landing.
---

## Motivation

The AI progressive learning curve (HC-1..HC-6 of the restructure plan) requires product/PRODUCT.md to be the FIRST authoritative surface a fresh AI session sees. If CLAUDE.md regresses back to a rule encyclopaedia OR product/PRODUCT.md drops from auto-load, the whole design collapses — AI session learns rules before product intent again.

## How it works

Scans `gate/always-loaded-budget.txt` for the two structural constraints. Failure is blocking from landing.

## Composition

- **Rule G-4** (always_loaded_budget_enforced) — the underlying budget-ceiling gate.
- This rule is the structural-policy layer on top.
