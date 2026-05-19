---
rule_id: D-1
title: "Root-Cause + Strongest-Interpretation Before Plan"
level: L1
view: process
principle_ref: P-A
authority_refs: []
enforcer_refs: []
status: active
kernel_cap: 12
kernel: |
  **Before writing any plan, fix, or feature — surface assumptions, name confusion, and state tradeoffs. Then (a) name the root cause mechanically and (b) choose the strongest valid reading of the requirement.**
---

## Motivation

Plans built on guessed root causes ship guessed fixes. Requirements read at their weakest reading produce features that satisfy the letter but not the intent. Both failure modes get caught by the same discipline: before any plan, force two passes — a mechanical four-line root-cause block (observed failure / execution path / root-cause statement / evidence) and a deliberate strongest-interpretation reading that resolves ambiguity in favour of the stronger, more consequential meaning.

## Details

**(a) Root-cause discipline** — required before any plan:
1. **Observed failure**: exact error message or test output
2. **Execution path**: which function calls which, where it diverges from expectation
3. **Root cause statement**: one sentence — "X happens because Y at line Z, which causes W"
4. **Evidence**: file:line references that confirm the cause, not the symptom

**(b) Strongest-interpretation defaults:**
- "Gate" → **blocking**, not notification
- "Isolation" → **per-tenant/profile scope**, not process scope
- "Persist" → **survives restart**, not in-memory
- "Compatible" → **same signature + same semantics**, not "same name"

**Enforcement**: A PR without the four-line root-cause block is rejected.

## Cross-references

- Rule D-5 (Self-Audit is a Ship Gate, Not a Disclosure) — strongest-interpretation discipline reinforces what counts as a ship-blocking finding.
- Rule D-2 (Simplicity & Surgical Changes) — root-cause discipline narrows scope; surgical-changes discipline keeps that scope intact during implementation.
