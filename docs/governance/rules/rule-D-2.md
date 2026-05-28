---
rule_id: D-2
title: "Simplicity & Surgical Changes"
level: L1
view: process
principle_ref: P-A
authority_refs: []
enforcer_refs: []
status: active
governance_infra: true
scope_phase: always_on
kernel_cap: 12
kernel: |
  **Minimum code that solves the stated problem. Touch only what the task requires.**
---

## Motivation

Speculative features, one-use abstractions, and adjacent reformats inflate review surface, obscure the actual change, and seed defects unrelated to the task. The strongest signal of a healthy patch is that every modified line is causally required by the task it claims to perform. Commits that mix defect IDs or sprawl across modules cannot be reviewed, reverted, or bisected as a unit.

## Details

- No speculative features, one-use abstractions, unrequested configurability, or impossible-scenario error handling.
- Reach for a library before inventing a framework; reach for a function before inventing a class hierarchy.
- Do not improve, reformat, or rename adjacent code in the same commit. Match surrounding style exactly.
- Remove only imports/variables/functions that **your** change made unused — leave pre-existing dead code for a separate cleanup commit.
- Commits spanning >1 defect ID or >2 distinct modules must be split.

## Cross-references

- Rule D-1 (Root-Cause + Strongest-Interpretation Before Plan) — surgical changes follow from a sharply scoped root-cause statement.
- Rule D-3.a (Pre-Commit Checklist) — checklist enforces the per-file audit that catches speculative inclusions.
