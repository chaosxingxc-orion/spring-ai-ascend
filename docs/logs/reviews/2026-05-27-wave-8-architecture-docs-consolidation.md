---
level: L0
view: scenarios
affects_level: L0, L1
affects_view: scenarios
affects_artefact: [ARCHITECTURE.md]
status: amendment
authors:
  - chao
authority: "ADR-0150 (Wave 8 architecture docs consolidation)"
---

# Wave 8 — ARCHITECTURE.md §0.6 + §0.7 + §65 amendment

This review record satisfies Rule G-1 sub-clause .a's "phase-released L0/L1 artefacts are read-only — further edits flow through `docs/logs/reviews/`" requirement for Wave 8's edits to the frozen root `ARCHITECTURE.md` (freeze_id: W1-russell-2026-05-14).

## What changed in ARCHITECTURE.md

1. **§0.4 (Layered 4+1 view map)** — added one sentence stating that per-module L1 4+1 lives at `architecture/docs/L1/<module>{.md or /}`.
2. **NEW §0.6 (Rhetorical stance of this document)** — declares that ARCHITECTURE.md is the **declarative L0** system boundary + 65 §4 constraints surface, distinct from CLAUDE.md (enforceable rules), `docs/contracts/` (runtime contracts), and `architecture/docs/L1/` (L1 module design). Prevents readers from conflating these slices.
3. **NEW §0.7 (Constraint ↔ Rule cross-reference)** — short paragraph explaining how each §4 constraint maps to one or more CLAUDE.md rules via `architecture/generated/enforcers.dsl` and the workspace `enforced_by` edges; lists 5-10 most-cited pairs as an entry point.
4. **§65 (Architecture workspace truth)** — already amended at W5 (ADR-0147 amendment receipt: `docs/logs/reviews/2026-05-27-structurizr-workspace-authority-w5-amendment.md`); W8 updates the inline list of post-Wave-8 module subdirectory layout to reflect the moved L1 corpus.

## Why

ADR-0150 (Wave 8) — see body. The user directive 2026-05-27: build the architecture-design system around `architecture/workspace.dsl` as the primary entry; consolidate L1 design under `architecture/docs/L1/`; ensure every newcomer (human or AI) gets an unbiased architecture picture from a defined reading path.

## Scope of the amendment

- L0 corpus surfaces touched: ARCHITECTURE.md §0.4 (one-sentence addition), §0.6 (new section, ~12 lines), §0.7 (new section, ~10 lines), §65 (already W5-amended; W8 refreshes the layout description block).
- L0 corpus surfaces NOT touched: ARCHITECTURE.md §1..§64 + §66+ are unchanged.
- The amendment preserves the freeze_id frontmatter; this review record is the explicit edit-path-compliance receipt for Rule 44.

## Cross-references

- ADR-0147 — Structurizr Workspace Authority (W0 wave; declared workspace.dsl as authority).
- ADR-0149 — W0-W5 shipped record (W7 closure ADR).
- ADR-0150 — Wave 8 docs consolidation (this wave's authority).
- `docs/governance/rules/rule-G-1.md` sub-clause .a — amended L1 path regex.
- `architecture/docs/L1/README.md` — L1 navigation root (NEW in W8).
- Commit: `wave8/structurizr-first-docs-consolidation` branch HEAD at the W8 merge commit.
