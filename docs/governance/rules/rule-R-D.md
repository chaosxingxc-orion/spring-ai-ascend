---
rule_id: R-D
title: "SPI + DFX + TCK Co-Design + Catalog Integrity"
level: L0
view: development
principle_ref: P-D
authority_refs: [ADR-0067, ADR-0083, ADR-0085]
enforcer_refs: [E3, E32, E105, E106, E107, E108, E117, E118, E131]
status: active
scope_phase: design
kernel_cap: 8
kernel: |
  **Every `kind: domain` module exposes ≥1 `*.spi.*` package with ≥1 public interface listed under `spi_packages` in `module-metadata.yaml`, plus a `docs/dfx/<module>.yaml` covering five DFX dimensions (releasability, resilience, availability, vulnerability, observability); TCK conformance suites are deferred to W2 per `CLAUDE-deferred.md` 32.b/.c (sub-clause .a). Every `spi_packages` entry MUST resolve to a real directory with ≥1 `.java` beyond `package-info.java` (sub-clause .b), MUST be declared by exactly one Maven module (no split packages, sub-clause .c), and MUST end in `.spi` OR contain `.spi.` (sub-clause .d). Every `kind ∈ {platform, domain}` module's `docs/dfx/<module>.yaml` declares an order-insensitive set-matching `spi_packages` block vs `module-metadata.yaml#spi_packages` (sub-clause .e). Every row in `docs/contracts/contract-catalog.md` §2 Active SPI interfaces table (not `(internal)`-marked) MUST resolve back to `module-metadata.yaml#spi_packages` AND `docs/dfx/<module>.yaml#spi_packages` (sub-clause .f). Every `public interface` declaration under any `*/spi/*` path (excluding `target/`) MUST appear in the catalog as an Active SPI row OR be `(internal)`-marked (sub-clause .g).**
---

# Rule R-D — SPI + DFX + TCK Co-Design + Catalog Integrity

Operationalises across 7 sub-clauses. See `## Sub-clauses` below for the per-sub-clause assertion + enforcer mapping. Authority: [ADR-0067, ADR-0083, ADR-0085].

## Sub-clauses

### .a — (was sub-clause .a)

## Motivation

Rule R-D sub-clause .a is the in-repo enforceable expression of governing principle P-D (SPI-Aligned, DFX-Explicit, Spec-Driven, TCK-Tested). Domain modules without an SPI become customisation-by-source-patch waiting to happen; platform/domain modules without a DFX manifest ship resilience and availability claims as marketing rather than commitments. The TCK companion module is deferred to W2 but the SPI surface and the DFX manifest land in the same PR that declares a module `kind: domain` or `kind: platform`.

## Details

Enforced by E48 (`SpiPurityGeneralizedArchTest`), Gate Rule R-E (`dfx_yaml_present_and_wellformed`), and Gate Rule R-F (`domain_module_has_spi_package`).

## Cross-references

- ADR-0067 — origin decision record.
- P-D — governing principle Rule R-D sub-clause .a operationalises.
- Architecture reference: §4 #63.
- Deferred sub-clauses 32.b (TCK module scaffolding), 32.c (TCK conformance content), 32.d (vulnerability-scanner integration).
- Rule R-A (Business/Platform Decoupling Enforcement) — co-enforced by E48 on the SPI purity side.
- Rule R-C.1 (Independent Module Evolution; was Rule R-C.b pre-rc17 per ADR-0094) — the `module-metadata.yaml` that declares `kind: domain` is the same artefact Rule R-D sub-clause .a reads.

## Deferred sub-clauses

Rule R-D sub-clause .a.b, Rule R-D sub-clause .a.c, Rule R-D sub-clause .a.d (see `docs/CLAUDE-deferred.md` for the deferred-runtime obligation(s) and re-introduction trigger(s)). Rule G-3 sub-clause .d (`kernel_deferred_clause_coherence`, rc9 / ADR-0083) asserts the bidirectional link between this active rule and each deferred sub-clause.

### .b — (was sub-clause .b)

# Rule R-D sub-clause .b — SPI Packages Populated

## Motivation

The 2026-05-18 SPI integrity audit found that `agent-execution-engine/module-metadata.yaml` declared `ascend.springai.engine.spi` as an SPI package, but the physical directory contained only `package-info.java` — every real engine SPI class had landed in the parallel `service.runtime.orchestration.spi` namespace. The declaration was aspirational; reality differed. No gate rule caught this drift.

Rule R-D sub-clause .b operationalises the implicit invariant of Rule R-D sub-clause .a ("Every module MUST expose at least one SPI package containing ≥ 1 public interface"): declared SPI must be backed by code.

## Algorithm

For each `*/module-metadata.yaml`:
1. Parse the top-level `spi_packages:` list.
2. For each entry, skip if its inline comment includes BOTH `placeholder` AND `ADR-NNNN` (deferred SPI work, explicitly waived).
3. Otherwise, resolve `pkg.name.parts` → `<module>/src/main/java/pkg/name/parts/`.
4. Fail if the directory is missing.
5. Fail if the directory contains only `package-info.java` (no real SPI classes).

## Placeholder convention

A module's metadata may legitimately declare future SPI before the implementation lands. Mark each such line with `# placeholder; ... ADR-NNNN ...` so the rule treats it as deferred. Example:

```yaml
spi_packages:
  - ascend.springai.bus.spi      # placeholder; populated in W2 per ADR-0050
```

## Activation

Activated 2026-05-18 by the SPI metadata integrity wave per `D:\.claude\plans\spi-smooth-llama.md`. Surfaced during the wave: bus/client/evolve modules already had ADR-referenced placeholders; only agent-execution-engine had an unmarked empty SPI declaration (fixed by Track A of the same plan).

### .c — (was sub-clause .c)

# Rule R-D sub-clause .c — No Split SPI Packages

## Motivation

The 2026-05-18 SPI integrity audit found that `ascend.springai.service.runtime.orchestration.spi` was declared simultaneously by `agent-runtime-core/module-metadata.yaml` AND `agent-execution-engine/module-metadata.yaml`. Both modules physically contributed `.java` files to the same Java package — a Maven split-package.

Split-packages compile but degrade quietly: Maven test classpaths can collide, IDE refactor-rename traverses only one module, and JPMS (Java Platform Module System) refuses to start because two modules cannot both own a package.

Rule R-D sub-clause .c forces an explicit owner per SPI package.

## Algorithm

1. Collect `(spi_package, module)` pairs from every `*/module-metadata.yaml`.
2. Group by `spi_package`. Fail if any group has more than one module.

## Resolution patterns

When two modules legitimately need to share a Java package, prefer ONE of:
- Promote the shared package to a third (upstream) module that both depend on.
- Split the package into non-overlapping sub-packages (e.g. `foo.spi.kernel` vs `foo.spi.adapter`) so each module owns its sub-package.
- Move the smaller contributor's classes to a sibling package the owning module does not claim.

The 2026-05-18 remediation chose option 3 for `orchestration.spi` / `engine.spi`: engine-adapter classes moved out of `orchestration.spi` into the (previously empty) `engine.spi` package.

## Activation

Activated 2026-05-18 by the SPI metadata integrity wave per `D:\.claude\plans\spi-smooth-llama.md`.

### .d — (was sub-clause .d)

# Rule R-D sub-clause .d — SPI Packages Dot-Spi Convention

## Motivation

The 2026-05-18 SPI integrity audit found that `agent-runtime-core/module-metadata.yaml` declared `ascend.springai.service.runtime.runs` as an SPI package — but the directory contained domain value types (`Run`, `RunMode`, `RunStatus`, `RunStateMachine`) plus one contract (`RunRepository`). Only the latter is truly SPI-grade; the rest are domain types.

Rule R-D sub-clause .a specifies the `*.spi.*` literal convention. Without machine enforcement, that convention drifts: developers conflate "interesting package" with "SPI package", and the SPI surface bloats to include non-contracts.

Rule R-D sub-clause .d forces every declared SPI package to actually contain `.spi` in its name.

## Algorithm

For each `spi_packages:` entry, fail unless the package ends in `.spi` OR contains `.spi.` (sub-packages of a `.spi.*` namespace).

## Examples

- `ascend.springai.engine.spi` — passes (ends in `.spi`).
- `ascend.springai.service.runtime.runs.spi` — passes (ends in `.spi`).
- `ascend.springai.engine.spi.kernel` — passes (contains `.spi.`).
- `ascend.springai.service.runtime.runs` — fails (no `.spi` token).
- `ascend.springai.engine.SPI` (uppercase) — fails (Java packages are lowercase by convention).

## Activation

Activated 2026-05-18 by the SPI metadata integrity wave per `D:\.claude\plans\spi-smooth-llama.md`. Drove the `runs` → `runs/spi/` sub-package move documented in the same wave.

### .e — (was sub-clause .e)

# Rule R-D sub-clause .e — DFX SPI Packages Match Module Metadata

## Motivation

The 2026-05-18 SPI integrity audit found three drift patterns between `module-metadata.yaml` and the matching `docs/dfx/*.yaml`:

1. `docs/dfx/agent-runtime-core.yaml` had no `spi_packages` declaration at any level, even though metadata claimed three SPI packages.
2. `docs/dfx/agent-service.yaml` declared `spi_packages` nested under `observability:` rather than as a top-level peer.
3. `docs/dfx/agent-execution-engine.yaml` declared a single SPI package while metadata claimed two (one of which was empty — see Rule R-D sub-clause .b).

DFX is the design-time contract document; module-metadata is the build-time declaration. They MUST agree on which packages the module publishes.

## Algorithm

For each `*/module-metadata.yaml` whose kind is `platform` or `domain`:
1. Build the "real SPI" set from the metadata's `spi_packages:` MINUS placeholder entries (those with `# placeholder; ... ADR-NNNN ...` comment).
2. If the real-SPI set is empty, skip (the module is placeholder-only).
3. Build the same real-SPI set from `docs/dfx/<module>.yaml`'s top-level `spi_packages:`.
4. Fail if dfx is missing the top-level `spi_packages:` block OR the sets differ.

Order-insensitive comparison via `sort -u` on both sides.

## Why top-level only

DFX yamls have a 5-dimension structure (releasability/resilience/availability/vulnerability/observability). Nested `spi_packages:` under `observability:` (a real pre-rule pattern) is a structural error: it hides the SPI declaration from anyone scanning the file for module-level contracts.

## Activation

Activated 2026-05-18 by the SPI metadata integrity wave per `D:\.claude\plans\spi-smooth-llama.md`. Triggered fixes to all 4 affected DFX files (agent-runtime-core, agent-execution-engine, agent-service, agent-middleware).

### .f — (was sub-clause .f)

# Rule R-D sub-clause .f — Catalog SPI Row Matches Module SPI Metadata

## Motivation

The 2026-05-18 rc5 post-response architecture review (finding P1-2 in `docs/logs/reviews/2026-05-18-l0-rc5-post-response-architecture-review.en.md`) found `ResilienceContract` simultaneously treated as a shipped SPI and excluded from SPI governance:

- `docs/contracts/contract-catalog.md:22-31` listed `ResilienceContract` in "Active SPI interfaces (11 total)" with package `ascend.springai.service.runtime.resilience` — a package with no `.spi` token.
- `docs/contracts/contract-catalog.md:43` counted `agent-service` as having two SPI interfaces (`GraphMemoryRepository`, `ResilienceContract`).
- `docs/governance/architecture-status.yaml#resilience_contract.allowed_claim` called it an "L1: ResilienceContract SPI".
- `agent-service/ARCHITECTURE.md` resilience section called it an "Operation-routing SPI (W0)".
- `agent-service/module-metadata.yaml:13-14` declared only `ascend.springai.service.runtime.memory.spi` under `spi_packages` — `resilience` was missing.
- `docs/dfx/agent-service.yaml:14-15` mirrored only `memory.spi`.

Rule R-D sub-clause .d (every `spi_packages:` entry must end in `.spi` or contain `.spi.`) therefore passed vacuously — `ResilienceContract` was *called* SPI in the catalog but its package was never declared as SPI in the metadata, so the package convention check had no rows to test. The corpus advertised one classification while metadata declared another, and no gate caught the divergence.

The rc4 response's hidden-defects audit had logged this as a known edge case ("the one shipped SPI not under a .spi package — out of scope for this wave, but logged for future audit"). The rc5 reviewer escalated it: a contract catalog row that calls something a shipped SPI IS a published-SPI commitment; the package home must match.

The rc6 wave closes the substantive half via ADR-0080 (move `ResilienceContract` + value types to `...resilience.spi.*`) and adds Rule R-D sub-clause .f to prevent the dual-classification defect from recurring on any future SPI.

## Details

### Algorithm

For each row in the SPI table of `docs/contracts/contract-catalog.md` §2 (between the header `**Active SPI interfaces (N total):**` and the next bold-heading separator):

1. Skip header rows and table separators (`|---|`).
2. Parse the four columns: `Interface`, `Module`, `Package`, `Status`.
3. If `Status` contains the literal substring `(internal)` (case-insensitive), the row is exempt: it MUST NOT be counted in the `(N total)` header, but it MAY exist in the table as historical context.
4. Otherwise, the row is a shipped-SPI commitment. Resolve `<module>/module-metadata.yaml`:
   - Fail if the metadata file does not exist.
   - Fail if `spi_packages:` is absent.
   - Fail if `Package` is not listed in `spi_packages:` as either an exact match OR a parent package (the catalog row's package contains the metadata entry as a sub-package — e.g., row package `ascend.springai.service.runtime.resilience.spi` matches metadata entry `ascend.springai.service.runtime.resilience.spi`).
5. Resolve `docs/dfx/<module>.yaml`:
   - Fail if the DFX file does not exist or has no top-level `spi_packages:` block.
   - Fail if the same package is not listed there. (Rule R-D sub-clause .e set-match already enforces metadata ↔ DFX agreement; Rule R-D sub-clause .f inherits that property by requiring the package in both files.)

### Header-count consistency

The catalog's `**Active SPI interfaces (N total):**` header MUST equal the number of non-`(internal)` rows in the table. If a row is exempted via `(internal)`, the header MUST be decremented. This prevents the `(11 total)` count from silently shadowing a hidden-non-SPI row.

### Excluded cases

- Rows in deprecated-SPI subtables or appendices below a separator marker like `**Deprecated SPI:**`. These are not "active" and Rule R-D sub-clause .f does not apply.
- `package-info.java`-only SPI scaffolds — covered by Rule R-D sub-clause .b's placeholder waiver, not by Rule R-D sub-clause .f.

### Why both files

Rule R-D sub-clause .e already enforces that `module-metadata.yaml#spi_packages` and `docs/dfx/<module>.yaml#spi_packages` set-match. Rule R-D sub-clause .f piles on a third constraint — the contract catalog must also point at the same set — closing the triangle. Without Rule R-D sub-clause .f, the catalog could drift from metadata silently (the rc5 defect); with Rule R-D sub-clause .f, all three artefacts must agree.

## Activation

Activated 2026-05-18 by the v2.0.0-rc5 post-response architecture review response wave. Enforcer E118. Closes P1-2 of `docs/reviews/2026-05-18-l0-rc5-post-response-architecture-review.en.md`.

## Cross-references

- Rule R-D sub-clause .a (SPI + DFX + TCK Co-Design) — origin authority for the `.spi` package convention and DFX requirement. Rule R-D sub-clause .f enforces the catalog as the third corner of the SPI-truth triangle.
- Rule R-D sub-clause .b (SPI Packages Populated) — each metadata SPI package must have real Java content (or an ADR-waived placeholder).
- Rule R-D sub-clause .c (No Split SPI Packages) — no two modules can co-declare the same SPI package.
- Rule R-D sub-clause .d (SPI Packages Dot-Spi Convention) — every metadata SPI package must end in `.spi` or contain `.spi.`.
- Rule R-D sub-clause .e (DFX SPI Packages Match Module Metadata) — metadata ↔ DFX set-match; Rule R-D sub-clause .f extends this to catalog ↔ metadata.
- ADR-0030 (Skill-capacity arbitration) — original authority that published `ResilienceContract` as the architectural boundary.
- ADR-0070 (Tenant-aware `resolve(tenant, skill)` two-arg signature) — Rule R-K.b's signature evolution that confirmed cross-module SPI status.
- ADR-0080 (ResilienceContract `.spi` package alignment) — substantive closure of the rc5 P1-2 defect; Rule R-D sub-clause .f makes the prevention permanent.
- `docs/logs/reviews/2026-05-18-l0-rc5-post-response-architecture-review.en.md` finding P1-2 — origin.
- `docs/logs/reviews/2026-05-18-l0-rc5-post-response-architecture-review.en.md` finding P1-2 — origin.

### .g — (was sub-clause .g)

# Rule R-D sub-clause .g — SPI Catalog Exhaustiveness

## Motivation

The rc8 post-corrective review (P1-2) found that `SkillCapacityRegistry` — a public interface under `ascend.springai.service.runtime.resilience.spi`, called out by ADR-0080 as the "registry SPI" and exposed by `ResilienceAutoConfiguration` as an `@ConditionalOnMissingBean` overrideable bean — was absent from `docs/contracts/contract-catalog.md` §2 "Active SPI interfaces (11 total)" table.

Rule R-D sub-clause .f (rc6) enforced the other direction: every catalog row whose status doesn't say `(internal)` must have its `Module` and `Package` columns resolve to real `module-metadata.yaml#spi_packages` entries. That's "declared rows must be valid" — important, but one-directional. The opposite direction — "all public surfaces must be declared" — was never enforced.

Rule R-D sub-clause .g closes the second edge. The catalog now has to know about every public SPI interface OR explicitly mark it internal.

## Algorithm

The gate scans all Java files under any path containing `/spi/`:
```
find . -type f -name '*.java' -path '*/spi/*' -not -path './target/*' -not -path './*/target/*'
```
For each, extract the first `public [sealed|non-sealed] interface <Name>` declaration. The interface name must appear inside a markdown backtick (`` `Iface` ``) somewhere in `docs/contracts/contract-catalog.md`. If absent → violation.

## Why backtick-match instead of structured table parsing

The catalog's Active SPI table has a stable shape, but the rule needs to also accept `(internal)` annotations that may live in prose adjacent to the table rather than in the table itself. A simple backtick-name match is robust to both forms; the prose decision (active SPI vs internal) is up to the architects.

## Enforcement

Enforced by E131 (Gate Rule R-D sub-clause .g — `spi_catalog_exhaustiveness`). Positive self-test: every SPI interface present in catalog. Negative self-test: a synthetic public interface under `spi/` not in catalog → FAIL.

## Activation

Activated 2026-05-19 by the v2.0.0-rc9 wave (rc8 post-corrective review response). Enforcer E131 + E132 (positive + negative self-test fixtures).

## Cross-references

- ADR-0080 — calls out SkillCapacityRegistry as registry SPI.
- ADR-0081 — describes ResilienceContract.resolve consuming SkillCapacityRegistry.
- ADR-0083
- Rule R-D sub-clause .f — sibling direction: catalog row → metadata.
- Gate Rule 66 (`spi_package_exhaustiveness`) — SPI package exhaustiveness against module-metadata (gate-layer rule per ADR-0086 gate_layer_boundary).
- `docs/logs/reviews/2026-05-18-l0-rc8-post-corrective-architecture-review.en.md` finding P1-2 — origin.
