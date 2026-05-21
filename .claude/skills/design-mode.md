---
name: design-mode
description: |
  Load the architecture-design phase contract. Use this skill when:
  - Starting an ADR (`docs/adr/NNNN-*.yaml`).
  - Declaring a new SPI or amending an existing one.
  - Drafting a module specification, changing `module-metadata.yaml`,
    or proposing a topology change.
  - Writing or revising a design-review document under
    `docs/logs/reviews/`.
  - Modifying root `ARCHITECTURE.md` or any `agent-*/ARCHITECTURE.md`.
  Reads `docs/governance/contracts/architecture-design.md` and emits
  the phase entry checklist. Replaces progressive on-demand rule
  loading per ADR-0098 (rc21 6-phase scenario contracts).
scope: project
---

# /design-mode — Load Architecture Design Phase Contract

## Purpose

This skill is the architecture-design phase entry point. ADR-0098
(rc21) replaced progressive rule loading with scenario-loaded
contracts; this skill loads the contract that governs design work
before you start editing.

The phase contract names the rules that bind your edits (13 Layer-0
principles + 23 architecture rules with **P** or **X** markers), the
forbidden patterns specific to design work, and the exit criteria
that decide whether the design is shippable.

## When to invoke

- About to author an ADR — invoke this skill first.
- About to declare a new SPI — invoke before adding the interface or
  the catalog row.
- About to change `module-metadata.yaml` for an existing module —
  invoke before the edit.
- About to draft a design-review document — invoke before writing.
- About to modify `ARCHITECTURE.md` (root or any module) — invoke
  before opening the file.

If the task spans multiple phases, invoke `/design-mode` for the
design portion, then `/impl-mode` for the implementation portion;
don't conflate.

## What this skill does

1. **Read** `docs/governance/contracts/architecture-design.md` and
   surface its content into the active context.
2. **Highlight** the Active Rules table — the 13 Layer-0 principles
   plus the architecture rules marked **P** are the primary
   constraints for this phase.
3. **Surface** the forbidden-patterns block so you know what NOT to
   do.
4. **State** the exit criteria so you know when the design phase is
   complete.
5. **Suggest** the next phase at exit — typically `/impl-mode` once
   the ADR is landed.

## What this skill does NOT do

- Does NOT write the ADR for you — you author it; this skill loads
  the rules that govern it.
- Does NOT run gate checks (those belong to `/verify-mode`).
- Does NOT replace reading the rule cards on-demand when a specific
  rule needs deeper detail — the phase contract LINKS to cards, it
  does not COPY their bodies.

## Composes with

- `/impl-mode` — the natural next phase once the design is approved.
- `/commit-mode` — when the ADR + design docs need to land as a
  release.
- `/review-mode` — if a reviewer challenges the design before merge.

## See also

- ADR-0098 — rc21 6-phase scenario-loaded contracts.
- `docs/governance/contracts/architecture-design.md` — the contract
  this skill loads.
- `docs/governance/principles/` — full bodies of P-A … P-M.
- `docs/governance/rules/` — full bodies of every rule listed in the
  contract.
