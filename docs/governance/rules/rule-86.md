---
rule_id: 86
title: "Root ARCHITECTURE Count + Path Truth"
level: L0
view: development
principle_ref: P-C
authority_refs: [ADR-0078, ADR-0079, ADR-0081, ADR-0082, "v2.0.0-rc6 post-response review P0-2", "v2.0.0-rc7 post-corrective review P0-1"]
enforcer_refs: [E119]
status: active
kernel_cap: 12
kernel: |
  **Every numeric module-count claim in root `ARCHITECTURE.md` matching `\b[0-9]+-module\b`, `\b[0-9]+ modules\b`, or `\b[0-9]+ reactor modules\b` (outside fenced code blocks and YAML frontmatter) MUST equal the count of `<module>` entries in root `pom.xml` AND `docs/governance/architecture-status.yaml#repository_counts.reactor_modules`. Every `<module>/src/main/java/...` path claim in root `ARCHITECTURE.md` (outside fenced code blocks) MUST resolve to a real path on disk OR carry a historical marker (`historical`, `pre-ADR-NNNN`, `pre-Phase-C`, `consolidated into`, `merged into`, `was rooted`, `formerly`, `superseded`, `deferred`, `moved`, `extracted per ADR-NNNN`) within ±3 lines. **rc8 amendment (2026-05-18, ADR-0082)**: a second pass also validates SPI-ownership claims INSIDE fenced tree-diagram code blocks — every indented `<pkg>/spi/` leaf under a module-header `<module>/` line MUST have an `<module>/module-metadata.yaml#spi_packages` entry containing `.<pkg>.spi`. Closes rc7 post-corrective review P0-1 (GraphMemoryRepository ownership drift hid inside a fenced tree block that the original pass excluded). Operationalises rc6 P0-2 + rc7 P0-1 closure (Rule 84 covers `agent-*/ARCHITECTURE.md`; Rule 86 covers root; rc8 extension covers fenced trees).**
---

# Rule 86 — Root ARCHITECTURE Count + Path Truth

## Motivation

Rule 84 (active_module_architecture_path_truth, rc6) catches stale Java-source path claims inside per-module `agent-*/ARCHITECTURE.md` files but its file glob explicitly excludes the root `ARCHITECTURE.md`. The 2026-05-18 rc6 post-response architecture review (finding P0-2 in `docs/reviews/2026-05-18-l0-rc6-post-response-architecture-review.en.md`) found the symmetric defect at the L0 entrypoint:

- `ARCHITECTURE.md:77-79` said "Eight-module post-Phase-C state" / "The reactor declares **8 modules** today" while `pom.xml` declares 9 and `architecture-status.yaml#repository_counts.reactor_modules: 9`.
- `ARCHITECTURE.md:140-193` showed deleted `agent-platform/` and `agent-runtime/` modules as current.
- `ARCHITECTURE.md:205-224` dependency diagram + prose still used the deleted module names in present tense.

The status of root `ARCHITECTURE.md` is canonical L0 — every contributor or agent starting here is taught the L0 module topology. The rc5 wave's Rule 84 was scoped to L1 per-module ARCHITECTURE.md files because those are also canonical for module identity; the L0 entrypoint slipped through. Rule 86 closes the L0 half of the bidirectional invariant Rule 33 (Layered 4+1 Discipline) names: root architecture text + module ARCHITECTURE.md text + actual reactor topology must agree.

## Details

### Algorithm

1. Parse the canonical count from BOTH `pom.xml` (count of `<module>` entries inside `<modules>...</modules>`) AND `docs/governance/architecture-status.yaml#repository_counts.reactor_modules`. If they disagree, fail with `pom.xml_disagrees_with_status_yaml` and stop — that is a separate Rule 30 / Rule 82 invariant that should fail first.

2. Walk root `ARCHITECTURE.md` line-by-line, tracking fenced code block state (lines bracketed by ``` ``` `` ```).

3. For each non-code line that contains a module-count phrase matching `\b[0-9]+-module\b`, `\b[0-9]+ modules\b`, `\b[0-9]+ reactor modules\b`, or `**N modules**` markdown bold:
   - Extract the numeric claim N.
   - Scan ±3 lines for a historical marker (same family as Rule 84 + the rc6 additions `pre-Phase-C`, `consolidated`, `merged`).
   - If no marker present, N MUST equal the canonical count.

4. For each non-code line that contains a Java path claim matching `agent-[a-z-]+/src/main/java/[a-zA-Z0-9_/.-]+`:
   - If the path resolves on disk, accept.
   - Otherwise scan ±3 lines for a historical marker. If present, accept.
   - Otherwise fail with the unresolved path.

### Marker convention

Markers extend Rule 84's set with two additions specific to ADR-0078 Phase C: `pre-Phase-C` (the consolidation cutoff) and `consolidated into` / `merged into` (verbs the consolidation prose uses). Rule 84 grammar (historical, moved, extracted per ADR-NNNN, superseded, formerly, deferred, pre-ADR-NNNN) remains accepted.

### Why scope to root ARCHITECTURE.md only

L2 documents under `docs/L2/` are technical deep-dives — they may legitimately freeze a snapshot architecture at the time of writing without continuous re-validation. Their drift risk is bounded by Rule 33's freeze-id discipline. Root `ARCHITECTURE.md` and `agent-*/ARCHITECTURE.md` are canonical living entrypoints — those are what Rule 84 + Rule 86 protect.

### Fenced-tree-block extension (rc8 amendment, 2026-05-18, ADR-0082)

Beyond the prose pass above, a second sweep scans ONLY lines inside fenced code blocks (the inverse of the first pass). It tracks two pieces of state:

1. The current module context — the most recent indented line matching `<modulename>/   # comment` where `<modulename>` starts with `agent-` or `spring-ai-ascend-`. Its indent level is recorded.
2. For each subsequent indented line matching `<pkg>/spi/   # comment` whose indent is strictly greater than the module's indent, the rule looks up `<module>/module-metadata.yaml#spi_packages`. The list MUST contain at least one entry containing the substring `.<pkg>.spi`. If not, fail (subject to the same ±3-line historical-marker exemption).

This closes the **rc7 post-corrective review P0-1** finding: GraphMemoryRepository ownership drift hid inside the root ARCHITECTURE.md tree (a fenced code block) where the original Rule 86 pass intentionally skipped all lines. Rule 86's fenced-block exclusion was a deliberate decision to avoid false positives on prose example code; the rc8 amendment narrowly re-enters fenced blocks ONLY when they have the tree-diagram shape (module-header + SPI leaf) and the leaf is verifiable against module-metadata.yaml (the canonical SSOT per ADR-0082).

## Activation

Activated 2026-05-18 by the v2.0.0-rc6 post-response architecture review response wave (v2.0.0-rc7). Enforcer E119. Closes P0-2 of `docs/reviews/2026-05-18-l0-rc6-post-response-architecture-review.en.md`.

**rc8 amendment 2026-05-18** (v2.0.0-rc8 corrective wave) extended Rule 86 with the fenced-tree-block second pass per ADR-0082. Closes P0-1 of `docs/reviews/2026-05-18-l0-rc7-post-corrective-architecture-review.en.md`.

## Cross-references

- Rule 25 (Architecture-Text Truth) — Rule 86 is the root-document path/count specialisation of Rule 25.
- Rule 33 (Layered 4+1 Discipline) — Rule 86 guards the truthfulness of L0 path + count claims so root identity stays coherent.
- Rule 82 (Baseline Metrics Single Source) — same family of numeric-agreement-across-authority-docs invariant; Rule 82 covers entrypoint count phrases in `README.md` / `gate/README.md`, Rule 86 covers the analogous phrases inside root `ARCHITECTURE.md` (and adds path-claim coverage).
- Rule 84 (Active Module ARCHITECTURE Path Truth) — companion bidirectional gate at the L1 module level; Rule 86 closes the L0 root-level half Rule 84 does not reach.
- Rule 87 (Status YAML Allowed Claim Module Name Truth) — companion gate from the same rc6-post wave; Rule 87 covers the YAML-corpus side, Rule 86 covers the Markdown-corpus side.
- ADR-0078 (Phase C consolidation, 2026-05-18).
- ADR-0079 (Engine SPI + S2C SPI + shared kernel extraction, 2026-05-18).
- ADR-0081 (ResilienceContract dual-surface reconciliation, 2026-05-18) — sibling rc7 ADR; same wave.
- `docs/reviews/2026-05-18-l0-rc6-post-response-architecture-review.en.md` finding P0-2 — origin.
- `docs/reviews/2026-05-18-l0-rc6-post-response-architecture-review-response.en.md` — response document.
