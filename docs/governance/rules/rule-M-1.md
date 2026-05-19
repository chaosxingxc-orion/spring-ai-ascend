---
rule_id: M-1
title: "Skeleton Module Has No Production Java"
level: L1
view: development
principle_ref: P-C
authority_refs: [ADR-0073, ADR-0079, "v2.0.0-rc4 cross-constraint review P0-2"]
enforcer_refs: [E114]
status: active
kernel_cap: 8
kernel: |
  **For every reactor module whose root `ARCHITECTURE.md` frontmatter `status:` field contains the token `skeleton`, the module's `src/main/java/**/*.java` tree MUST contain only `package-info.java` files OR placeholder SPI stub files whose first 30 lines name a `placeholder` keyword with an `ADR-NNNN` waiver. Modules with extracted production code MUST NOT carry a `skeleton` status.**
---

# Rule M-1 — Skeleton Module Has No Production Java

## Motivation

ADR-0079 (T2.B2 — engine extraction into `agent-runtime-core` plus the live SPI shipping under `agent-execution-engine/src/main/java/`) completed the engine-extraction move, and ADR-0078 consolidated the agent-service module boundary. After both ADRs the `agent-execution-engine` module owned a real production Java SPI surface — `EngineRegistry`, `EngineEnvelope`, `ExecutorAdapter`, `EngineHookSurface`, and several spi packages — and Maven correctly built it as a normal domain module.

The 2026-05-18 rc4 cross-constraint architecture review (finding P0-2 in `docs/logs/reviews/2026-05-18-l0-rc4-cross-constraint-architecture-review.en.md`) discovered that the textual sweep was incomplete:

- `agent-execution-engine/ARCHITECTURE.md:17–23` still said the module was a deliberately empty skeleton.
- Root `ARCHITECTURE.md:89–98` still said `agent-execution-engine` remained a skeleton with T2.B2 deferred.
- Root `ARCHITECTURE.md:131–133` still listed `EngineRegistry`, `EngineEnvelope`, and `ExecutorAdapter` as pending T2.B2.
- `README.md:30–40` repeated the skeleton claim.
- `docs/governance/architecture-status.yaml:12,20` said T2.B2 was deferred.
- `agent-execution-engine/pom.xml:5–7` carried a "code is still to be extracted" comment alongside dependencies on `agent-runtime-core` and `agent-middleware` for the extracted engine implementation.

A contributor following the root L0 architecture would treat `agent-execution-engine` as an empty workspace while the reactor already built it as the owner of the engine surface. This undermined Rule R-C.b (Independent Module Evolution) and Rule G-1 sub-clause .a (Layered 4+1 Discipline) because module identity, module architecture, and physical code stopped agreeing.

Rule M-1 prevents the re-occurrence: a module can carry a `skeleton` status in its L1 ARCHITECTURE.md only when its production Java tree literally contains no production types beyond `package-info.java` (or ADR-waived placeholder stubs).

## Details

### Algorithm

For each `agent-*/ARCHITECTURE.md` in the reactor:

1. Parse the YAML front-matter and extract the `status:` field.
2. If `status:` does not contain the token `skeleton`, skip — the rule is vacuous for non-skeleton modules.
3. Walk `agent-*/src/main/java/**/*.java`. For each `.java` file:
   - If the basename equals `package-info.java`, allow.
   - Otherwise, read the first 30 lines. If they contain BOTH the keyword `placeholder` (case-insensitive) AND the token `ADR-NNNN` (a four-digit ADR reference), allow.
   - Otherwise, fail the gate with the violating file path.
4. Symmetrically: a module whose `src/main/java/**/*.java` tree contains any production type that is neither `package-info.java` nor an ADR-waived placeholder stub MUST NOT carry a `status:` token of `skeleton`.

### Why both directions

The reviewer's original wording was one-sided ("if claimed skeleton, must be empty"). Rule M-1 closes the dual axis ("if not empty, must not be claimed skeleton") because the rc4 P0-2 evidence was precisely that mismatch — the module had extracted production code AND was still claimed as a skeleton. Both halves of the bidirectional invariant fail the gate.

### Placeholder-stub waiver

A placeholder SPI stub is permitted (and counted as not-yet-production) only when both signals appear in the first 30 lines:

- The keyword `placeholder` (in a Javadoc, line comment, or class-level annotation).
- An `ADR-NNNN` reference (e.g. `ADR-0073`, `ADR-0079`) that justifies the deferred work.

The 30-line window matches the convention used by Rule R-D sub-clause .b for module-metadata `spi_packages` placeholder entries (`# placeholder; ADR-NNNN ...`). The two rules share the same waiver shape so a single ADR can license both a metadata placeholder and a code stub.

## Activation

Activated 2026-05-18 by the v2.0.0-rc4 cross-constraint architecture review response wave. Enforcer E114. Closes P0-2 of `docs/reviews/2026-05-18-l0-rc4-cross-constraint-architecture-review.en.md`.

## Cross-references

- Rule R-C.b (Independent Module Evolution) — Rule M-1 protects Rule R-C.b's invariant that every module has a coherent identity (metadata + ARCHITECTURE.md + actual Java tree).
- Rule R-D sub-clause .a (SPI + DFX + TCK Co-Design) — placeholder SPI stubs survive Rule R-D sub-clause .a only via the ADR waiver Rule M-1 enforces.
- Rule G-1 sub-clause .a (Layered 4+1 Discipline) — module-level L1 architecture status is one of the artefacts Rule G-1 sub-clause .a freezes; Rule M-1 guards the truthfulness of the status token.
- Rule R-D sub-clause .b (SPI Packages Populated) — companion rule for the metadata side; placeholder-with-ADR shape is shared.
- ADR-0078 (Phase C consolidation) — the consolidation step that preceded engine extraction.
- ADR-0079 (Engine extraction into `agent-runtime-core` / `agent-execution-engine`) — the ADR whose completion exposed the skeleton-drift defect.
- `docs/logs/reviews/2026-05-18-l0-rc4-cross-constraint-architecture-review.en.md` finding P0-2 — origin.
- `docs/logs/reviews/2026-05-18-l0-rc4-cross-constraint-architecture-review.en.md` finding P0-2 — origin.
