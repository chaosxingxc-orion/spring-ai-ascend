---
rule_id: G-1.1
title: "L1 Architecture Depth & Grounding"
level: L0
view: development
principle_ref: P-C
authority_refs: [ADR-0099]
enforcer_refs: [E166, E167, E168]
status: active
governance_infra: true
scope_phase: design
kernel_cap: 8
scope_surfaces:
  - agent-*/ARCHITECTURE.md
kernel: |
  **Every L1 architecture artefact (`architecture/docs/L1/<module>/` canonical directory per ADR-0152) MUST satisfy four depth/grounding constraints: (sub-clause .a — Development View Code-Mapping) `<module>/development.md` includes a Markdown fenced text block under `## *Development View*` (or numbered `## 3.`) declaring the target directory tree at least to the package level; every major logical component named in `<module>/logical.md` MUST map to a specific code path in the tree; tree paths cross-checked against actual filesystem. (sub-clause .b — SPI Interface Appendix) `<module>/spi-appendix.md` contains a section or appendix enumerating the module's full SPI/API design; every interface FQN listed MUST correspond to an entry in the module's `module-metadata.yaml#spi_packages` AND appear in `docs/contracts/contract-catalog.md` AND exist as a `public interface` `.java` file (adds the L1 architecture-doc appendix as the FOURTH parity point to Rule R-D sub-clauses .e/.f/.g — catalog ↔ metadata ↔ DFX; G-1.1 explicitly does NOT duplicate Rule R-D). (sub-clause .c — L2 Constraint Linkage) for any complex subsystem delegated to an L2 design, the L1 document MUST define the "Boundary Contracts" (inputs, outputs, DFX expectations) that the L2 design MUST adhere to. (sub-clause .d — Canonical Directory Parity) every `<module>/` directory MUST contain the canonical file basenames declared in `architecture/docs/L1/_template/` (README.md, ARCHITECTURE.md, logical.md, process.md, physical.md, development.md, scenarios.md, spi-appendix.md, features/README.md); advisory at W2 of the L1 Feature Registry plan, blocking at W5 after soak.**
---

# Rule G-1.1 — L1 Architecture Depth & Grounding

Added in the rc22 wave (per ADR-0099) as the structural backstop for
the F-l1-architecture-grounding-gap defect family. Operationalises the
2026-05-21 reviewer proposal `docs/logs/reviews/2026-05-21-l1-architecture-depth-and-grounding-proposal.en.md`.

## Motivation

Rule G-1.a (Layered 4+1 Discipline) mandates that every architecture
artefact declares `level:` + `view:` frontmatter, but it does not
enforce *depth* or *grounding* of the content. An L1 ARCHITECTURE.md
can be 4+1-shaped yet still fail to:

- Map logical components to specific package paths.
- Enumerate the actual SPI surface the module ships.
- Define boundary contracts for subsystems delegated to L2 designs.

At rc21 HEAD, the 6 agent-* ARCHITECTURE.md files showed uneven
grounding: agent-middleware lacked a Development View tree;
agent-client and agent-evolve were explicitly skeleton-status with no
SPI appendix. Rule G-1.1 closes this gap.

## Sub-clauses

### .a — Development View Code-Mapping

**Enforcer**: E166 (Gate Rule G-1.1 sub-check a — `gate/lib/check_l1_dev_view_tree.sh`).

Every `agent-*/ARCHITECTURE.md` MUST include a Markdown fenced text
block (```` ```text ````) under a `## *Development View*` section (or
the convention `## 3. Development View`). Every line of the tree that
ends with `/` is parsed as a package-path declaration relative to the
module's `src/main/java/`.

Cross-check (the rule fails when):
- Any documented package directory does NOT exist on the filesystem.
- Any major production package (under `src/main/java/`, excluding
  `*.impl.*` internals) is NOT documented.

Forward-compatibility marker: a fenced-block path may carry an HTML
comment `<!-- root-migration-target: com.huawei.ascend -->` to declare
intended post-rename path. The marker is stripped by the rc22.5
package-root migration wave (per ADR-0104).

### .b — SPI Interface Appendix

**Enforcer**: E167 (Gate Rule G-1.1 sub-check b — `gate/lib/check_l1_spi_appendix.sh`).

Every `agent-*/ARCHITECTURE.md` MUST contain a section titled "SPI
Interface Appendix" (or `## *Appendix*` containing "SPI" in body
prose) listing every `public interface` FQN the module ships.

Four-way parity (rule fails when any cross-check is violated):
1. The FQN appears in module's `module-metadata.yaml#spi_packages`.
2. The FQN appears as a row in `docs/contracts/contract-catalog.md`
   (or its package appears in the §2 SPI table).
3. The FQN appears in `docs/dfx/<module>.yaml#spi_packages`.
4. The FQN exists as a `public interface` `.java` file on disk.

This adds the L1 ARCHITECTURE.md appendix as the **fourth parity
point** to Rule R-D's three-way check (catalog ↔ metadata ↔ DFX). The
rule card MUST cite this reconciliation note (already cited above) so
future readers do not ask "why two enforcers for SPI?".

### .c — L2 Constraint Linkage

**Enforcer**: E168 (Gate Rule G-1.1 sub-check c — prose pattern check in `gate/check_architecture_sync.sh`).

For any complex subsystem the L1 document delegates to an L2 design
(any line of the form "see L2 doc at docs/L2/..." or "delegated
to L2"), the L1 document MUST contain a "Boundary Contracts" sub-
section enumerating: input types, output types, DFX expectations
(throughput, latency, error rate, deletion policy).

The rule is intentionally lightweight (prose pattern) because L2
documents are not yet a standard surface; the rule arms for W3+ when
L2 docs land. Until then it is vacuously green when no L2 delegation
prose exists.

## Reconciliation with Rule R-D

Rule R-D sub-clauses .e/.f/.g enforce parity across three machine-
readable surfaces (catalog, metadata, DFX). Rule G-1.1 adds the
human-readable L1 architecture document as the fourth surface. The
two rules are complementary, not duplicate:

| Surface | Rule R-D | Rule G-1.1 |
|---|---|---|
| `docs/contracts/contract-catalog.md` | catalog ↔ metadata ↔ DFX | also asserted as 4th-leg parity |
| `<module>/module-metadata.yaml#spi_packages` | catalog ↔ metadata ↔ DFX | also asserted as 4th-leg parity |
| `docs/dfx/<module>.yaml#spi_packages` | catalog ↔ metadata ↔ DFX | also asserted as 4th-leg parity |
| `<module>/ARCHITECTURE.md` SPI Interface Appendix | NOT enforced | **enforced by G-1.1.b** |

## Activation

Activated 2026-05-22 by the v2.0.0-rc22 wave per ADR-0099. Enforcers
E166, E167, E168 cover the three sub-clauses with six self-test
fixtures (positive + negative per sub-clause). Real helper implementations
landed in rc27 corrective wave (rc22-2 closure); the prior placeholder
`pass_rule` stubs were replaced with `gate/lib/check_l1_dev_view_tree.sh`
and `gate/lib/check_l1_spi_appendix.sh`.

## Self-tests (satisfying Rule 110 META requirements)

Per Rule 110 META, every prevention rule MUST declare `scope_surfaces:`
frontmatter AND carry ≥2 self-test fixtures across distinct surfaces.
Rule G-1.1 declares 1 scope surface (the 6 `agent-*/ARCHITECTURE.md`
files) and ships 6 fixtures:

- Fixture .a-pos — synthetic `agent-fake/ARCHITECTURE.md` with a
  well-formed Development View tree → Rule G-1.1 PASSES.
- Fixture .a-neg — synthetic ARCHITECTURE.md whose tree references
  `nonexistent/package/` → Rule G-1.1 FAILS.
- Fixture .b-pos — synthetic ARCHITECTURE.md whose SPI appendix lists
  all 4 parity surfaces → Rule G-1.1 PASSES.
- Fixture .b-neg — synthetic ARCHITECTURE.md whose SPI appendix lists
  an FQN absent from `module-metadata.yaml` → Rule G-1.1 FAILS.
- Fixture .c-pos — synthetic ARCHITECTURE.md with L2-delegation prose
  AND a Boundary Contracts sub-section → Rule G-1.1 PASSES.
- Fixture .c-neg — synthetic ARCHITECTURE.md with L2-delegation prose
  but NO Boundary Contracts sub-section → Rule G-1.1 FAILS.

All six fixtures live under `gate/test_architecture_sync_gate.sh`
following the existing `test_rule_NNN_*` naming pattern (here:
`test_rule_G_1_1_a_pos`, `test_rule_G_1_1_a_neg`, etc.).

## Cross-references

- ADR-0099 — wave authority + rule-id naming convention rationale.
- Rule G-1.a (Layered 4+1 Discipline) — parent rule; G-1.1 extends it.
- Rule G-3.b (kernel-card byte-match) — the kernel field above MUST
  byte-match the CLAUDE.md kernel paragraph for Rule G-1.1.
- Rule R-D sub-clauses .e/.f/.g — three-way SPI parity (this rule
  adds the fourth surface).
- Rule 110 META (`prevention_rule_scope_completeness`) — Rule G-1.1
  is a test case for Rule 110: declares `scope_surfaces:` AND ships
  6 fixtures.
- [`agent-*/ARCHITECTURE.md`](../../../) — the surfaces this rule
  governs.
