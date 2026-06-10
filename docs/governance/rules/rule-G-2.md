---
rule_id: G-2
title: "Authority-Text Reality (doc / status / path / numeric truth)"
level: L0
view: scenarios
principle_ref: P-D
authority_refs: ["ADR-0043 (retired)", "ADR-0047 (retired)", "ADR-0078 (retired)", "ADR-0082 (retired)", ADR-0083, ADR-0085, "ADR-0094 (retired)"]
enforcer_refs: [E16, E25, E115, E116, E119, E135, E136]
status: active
governance_infra: true
scope_phase: commit
kernel_cap: 8
kernel: |
  **Authority-text reality across the active corpus: `shipped: true` rows in `architecture-status.yaml` MUST have real `tests:` + `implementation:` paths and enforcer-backed prose (sub-clause .a — Architecture-Text Truth). `architecture-status.yaml#baseline_metrics` is the single source for entrypoint counts; `README.md` + `gate/README.md` numeric claims MUST point to it AND match parsed values (sub-clause .b — Baseline Metrics Single Source). Active `agent-*/ARCHITECTURE.md` path claims MUST resolve or carry historical markers (sub-clause .c). Root `ARCHITECTURE.md` module-count + path claims MUST match `pom.xml` and `architecture-status.yaml#repository_counts`; fenced tree-diagram SPI leaves MUST match `module-metadata.yaml#spi_packages` (sub-clause .d). The latest release note under `docs/logs/releases/*.md` MUST NOT contain absolute graph node/edge counts disagreeing with live values unless marked historical (sub-clause .g). Deleted-module-name leakage prevention (former sub-clauses .e/.f/.h) split to Rule G-2.1 per ADR-0094.**
---

# Rule G-2 — Authority-Text Reality (doc / status / path / numeric truth)

Operationalises across 5 sub-clauses (.a/.b/.c/.d/.g). The deleted-module-name leakage sub-clauses (.e/.f/.h) split to Rule G-2.1 in rc17 per ADR-0094. Authority: [ADR-0043, ADR-0047, ADR-0078, ADR-0082, ADR-0083, ADR-0085, ADR-0094].

## Sub-clauses

### .a — (was sub-clause .a)

## Motivation

Architecture documents that claim "enforced by X" without X actually running the assertion erode the trust signal that distinguishes shipped from designed. Once the corpus contains a single false-shipped claim, no reviewer can rely on any other claim without re-verifying. Rule G-2 sub-clause .a closes the loop: shipped rows cite real tests, implementation paths exist on disk, and prose enforcer claims are backed by enforcers performing the named assertion.

## Details

Path-existence violations caught by Gate Rule 7 (`shipped_impl_paths_exist`). Version-drift violations caught by Gate Rule 8 (`no_hardcoded_versions_in_arch`). Route-exposure violations caught by Gate Rule D-5 (`openapi_path_consistency`). Module-dep-direction violations caught by Gate Rule D-6 (`module_dep_direction`). Prose-enforcer claims without a real enforcer are a ship-blocking finding under Rule D-5.

## Cross-references

- ADR-0025, ADR-0026, ADR-0027 — origin decision records.
- Architecture reference: §4 #24.
- Rule D-5 (Self-Audit is a Ship Gate, Not a Disclosure) — prose-enforcer drift is the canonical ship-blocking finding.
- Rule R-C.a (Code-as-Contract) — Rule G-2 sub-clause .a is the architecture-text projection of the broader code-as-contract discipline.

### .b — (was sub-clause .b)

# Rule G-2 sub-clause .b — Baseline Metrics Single Source

## Motivation

Across the v2.0.0 release waves the repository accumulated four parallel ledgers for the same set of baseline counts (active engineering rules, active gate rules, self-tests, enforcer rows, graph nodes, graph edges, Maven tests):

- `README.md` — release-front-of-house counts.
- `AGENTS.md` — historical paragraph that was supposed to stop carrying counts.
- `gate/README.md` — gate's own description of its size.
- `docs/governance/architecture-status.yaml` — structured ledger.

The 2026-05-18 rc4 cross-constraint architecture review (finding P1-1 in `docs/logs/reviews/2026-05-18-l0-rc4-cross-constraint-architecture-review.en.md`) found six contradictions in the same release:

- `README.md:15` claimed 35 engineering rules / 64 gate rules / 94 self-tests / 94 enforcer rows / 306 Maven tests.
- `README.md:111` still said CLAUDE.md had 34 active engineering rules.
- `AGENTS.md:21` still carried "34 active engineering rules".
- `docs/governance/architecture-status.yaml:89` advertised rc4 counts for rules and tests but still said the architecture graph was `249 graph nodes / 326 edges`.
- `docs/governance/architecture-graph.yaml:21-22` and the build output both said `315 nodes / 433 edges`.
- `gate/README.md` contradicted itself across lines 3 / 18-20 / 51 / 68 with three different self-test totals (98, 92, 121).

Worse, the canonical gate passed, which meant the gate had a blind spot precisely in the entrypoint-vocabulary surface. The fix is structural: name one place as the single source, and require every prose count to point at it instead of restating the number.

## Details

### Required structured block

`docs/governance/architecture-status.yaml` MUST carry a block:

```
architecture_sync_gate:
  baseline_metrics:
    active_engineering_rules: <int>
    active_gate_checks: <int>
    gate_executable_test_cases: <int>
    enforcer_rows: <int>
    architecture_graph_nodes: <int>
    architecture_graph_edges: <int>
```

All six keys are required. Additional sibling keys (e.g. `release_baseline_self_tests`, `maven_tests`) are permitted but not enforced by Rule G-2 sub-clause .b.

### Vocabulary discipline

The keys are deliberately separated so prose cannot conflate them:

- `active_engineering_rules` — `#### Rule NN` headings in `CLAUDE.md` (Layer-1 engineering rules).
- `active_gate_checks` — the numbered gate-script rules in `gate/check_architecture_sync.sh` (the small set referenced by `gate/README.md`).
- `gate_executable_test_cases` — the `TOTAL=...` declared by `gate/test_architecture_sync_gate.sh` (the self-test corpus that proves each gate rule's algorithm against inputs).
- `enforcer_rows` — row count of `docs/governance/enforcers.yaml` (the rule→test bridge).
- `architecture_graph_nodes` / `architecture_graph_edges` — graph build outputs.

The reviewer noted the existing vocabulary collision around "active gate rules" — Rule G-2 sub-clause .b enforces that the structured block names them distinctly and that entrypoint prose uses the same names.

### Pointer requirement

`README.md` and `gate/README.md` MUST contain at least one occurrence of the substring `architecture_sync_gate.baseline_metrics` (or `architecture_sync_gate.baseline_metrics.<key>` for a specific metric). This is the minimum link integrity check — it does not enforce that the numbers actually agree (that is the substantive work of the release process), only that the prose acknowledges the canonical source.

### Drift mode

A future numeric claim in `README.md` or `gate/README.md` that does NOT cite the structured block will fail the gate. Restating the number inline without the pointer is forbidden; pointing at the block and then quoting the number is fine.

## Activation

Activated 2026-05-18 by the v2.0.0-rc4 cross-constraint architecture review response wave. Enforcer E115. Closes P1-1 of `docs/reviews/2026-05-18-l0-rc4-cross-constraint-architecture-review.en.md`.

## Cross-references

- Rule G-2 sub-clause .a (Architecture-Text Truth) — Rule G-2 sub-clause .b is the count-vocabulary specialisation of Rule G-2 sub-clause .a; Rule G-2 sub-clause .a protects every prose claim that names an enforcer, Rule G-2 sub-clause .b protects every prose claim that names a baseline count.
- Gate Rule 27 (`active_entrypoint_baseline_truth`, deferred but referenced — gate-layer rule per ADR-0086 gate_layer_boundary) — gate-rule complement that asserts README baseline counts match `allowed_claim`. Rule G-2 sub-clause .b strengthens that surface by enforcing the structured-source form: Gate Rule 27 says "the number is right", Rule G-2 sub-clause .b says "the number must be pulled from one block".
- ADR-0047 (Active Entrypoint Truth and System Boundary Prose Convention) — origin of the entrypoint-baseline-truth invariant that Rule G-2 sub-clause .b operationalises.
- `docs/logs/reviews/2026-05-18-l0-rc4-cross-constraint-architecture-review.en.md` finding P1-1 — origin.
- `docs/logs/reviews/2026-05-18-l0-rc4-cross-constraint-architecture-review.en.md` finding P1-1 — origin.

### .c — (was sub-clause .c)

# Rule G-2 sub-clause .c — Active Module ARCHITECTURE Path Truth

## Motivation

ADR-0078 (Phase C — `agent-platform` + `agent-runtime` merged into `agent-service`) and ADR-0079 (T2.B2 — engine SPI extracted to `agent-execution-engine`, shared core to `agent-runtime-core`, S2C SPI moved with it) both completed in v2.0.0-rc5 (2026-05-18). rc5's prevention wave shipped Rule M-1 to keep skeleton-status modules truthful when production code arrives. That rule is one-directional in practice: it fires only when `status:` literally contains `skeleton`.

The 2026-05-18 rc5 post-response architecture review (finding P0-1 in `docs/logs/reviews/2026-05-18-l0-rc5-post-response-architecture-review.en.md`) found the symmetric defect Rule M-1 does not catch:

- `agent-service/ARCHITECTURE.md:44` declared every Java path was rooted at `agent-service/src/main/java/...`.
- `agent-service/ARCHITECTURE.md:306` listed `EngineRegistry`, `EngineEnvelope`, and `ExecutorAdapter` under `agent-service/src/main/java/.../runtime/engine/`, even though ADR-0079 had moved `EngineRegistry` + `EngineEnvelope` to `agent-execution-engine` and `ExecutorAdapter` to `com.huawei.ascend.engine.spi.*`.
- `agent-service/ARCHITECTURE.md:321` claimed `S2cCallbackEnvelope` and `S2cCallbackTransport` lived under `agent-service/src/main/java/.../s2c/spi/`, even though ADR-0079 moved them to `agent-runtime-core`.
- `agent-service/ARCHITECTURE.md:496–498` and `:585–588` still spoke of engine extraction as future work scheduled "in the next wave."

The status of `agent-service` is `active`, not `skeleton`, so Rule M-1 was vacuous. A contributor or agent starting from the module ARCHITECTURE would be told to modify the wrong module and would believe ADR-0079 had not happened.

Rule G-2 sub-clause .c makes the prevention surface mechanical: every inline path claim in an active module ARCHITECTURE.md MUST resolve to disk, OR the surrounding paragraph MUST carry a historical / deferred / moved-from marker (the same marker convention Rule R-M sub-clause .f uses for S2C historical-only paragraphs). Stale future-tense prose adjacent to a delivered refactor fails the gate.

## Details

### Algorithm

For each `agent-*/ARCHITECTURE.md` in the reactor:

1. Parse the YAML front-matter and extract the `status:` field.
2. If `status:` contains the token `skeleton`, skip — Rule M-1 already governs this case.
3. If `status:` contains the token `deferred` (whole module is paused), skip.
4. Walk the file line-by-line. For each line:
   - Regex-extract path-claim phrases matching `(agent-[a-z-]+/src/main/java/[a-zA-Z0-9_/.-]+)`.
   - For each captured path `P`:
     - If `test -e "$repo_root/$P"` succeeds, the claim resolves — accept.
     - Otherwise, scan lines `[lineno-3 .. lineno+3]` of the same file for the markers listed in the kernel. If any marker is present, accept.
     - Otherwise, fail the gate with `file:line` and the unresolved path.

### Marker convention

The accepted markers are intentionally the same family Rule R-M sub-clause .f uses for the `S2cCallbackSignal` historical-only exemption — `historical`, `moved`, `formerly`, `extracted per ADR-NNNN`, `superseded`, `deferred`, `pre-ADR-NNNN`. A paragraph that names the ADR responsible for the move (and uses an explicit historical-tense verb) is exempt; a paragraph that merely fails to mention the ADR while quoting a stale path is not.

### Path-claim shape

The regex deliberately matches only `<module>/src/main/java/...` style paths — full reactor-root-relative paths to Java sources. This keeps the rule cheap and avoids false positives on prose like "look in `docs/contracts/`" or "see `agent-service` for...". Test-source paths (`src/test/java/`) are out of scope because moved test files do not deceive contributors the same way moved SPI does.

### Failure modes

The rule fails closed on two patterns:

1. Active module ARCHITECTURE.md cites a `<module>/src/main/java/...` path that does not exist on disk, AND no marker keyword appears within ±3 lines.
2. (Symmetric) An active module ARCHITECTURE.md describes ownership of an SPI surface that has moved to another module, but uses present-tense language without a historical marker. Detected as a special case of (1) — the moved type's old path won't resolve.

### Why both halves matter

Rule M-1 catches one direction of post-refactor drift (skeleton claim but production code present). Rule G-2 sub-clause .c catches the other (active claim that points at code now living elsewhere). Together they cover the full bidirectional invariant Rule G-1 sub-clause .a (Layered 4+1 Discipline) names: module identity + module architecture + actual code tree must agree.

## Activation

Activated 2026-05-18 by the v2.0.0-rc5 post-response architecture review response wave. Enforcer E117. Closes P0-1 of `docs/reviews/2026-05-18-l0-rc5-post-response-architecture-review.en.md`.

## Cross-references

- Rule G-2 sub-clause .a (Architecture-Text Truth) — Rule G-2 sub-clause .c is the module-level path specialisation of Rule G-2 sub-clause .a; Rule G-2 sub-clause .a protects every prose claim that names an enforcer, Rule G-2 sub-clause .c protects every prose claim that names a Java source path inside a module ARCHITECTURE.md.
- Rule G-1 sub-clause .a (Layered 4+1 Discipline) — Rule G-2 sub-clause .c guards the truthfulness of L1 path claims so module identity stays coherent.
- Rule R-M sub-clause .f (S2cCallbackSignal Historical-Only in Authority) — shares the marker convention (`historical`, `moved`, `extracted per ADR-NNNN`, `superseded`, `deferred`).
- Rule M-1 (Skeleton Module Has No Production Java) — companion bidirectional gate; Rule G-2 sub-clause .c handles the symmetric "active module, stale path" case Rule M-1 does not reach.
- ADR-0078 (Phase C consolidation) — origin of one half of the rc5 drift surface (`agent-platform` + `agent-runtime` → `agent-service`).
- ADR-0079 (Engine SPI + S2C SPI extraction) — origin of the other half (engine and S2C surfaces moved out of `agent-service`).
- `docs/logs/reviews/2026-05-18-l0-rc5-post-response-architecture-review.en.md` finding P0-1 — origin.
- `docs/logs/reviews/2026-05-18-l0-rc5-post-response-architecture-review.en.md` finding P0-1 — origin.

### .d — (was sub-clause .d)

# Rule G-2 sub-clause .d — Root ARCHITECTURE Count + Path Truth

## Motivation

Rule G-2 sub-clause .c (active_module_architecture_path_truth, rc6) catches stale Java-source path claims inside per-module `agent-*/ARCHITECTURE.md` files but its file glob explicitly excludes the root `ARCHITECTURE.md`. The 2026-05-18 rc6 post-response architecture review (finding P0-2 in `docs/logs/reviews/2026-05-18-l0-rc6-post-response-architecture-review.en.md`) found the symmetric defect at the L0 entrypoint:

- `ARCHITECTURE.md:77-79` said "Eight-module post-Phase-C state" / "The reactor declares **8 modules** today" while `pom.xml` declares 9 and `architecture-status.yaml#repository_counts.reactor_modules: 9`.
- `ARCHITECTURE.md:140-193` showed deleted `agent-platform/` and `agent-runtime/` modules as current.
- `ARCHITECTURE.md:205-224` dependency diagram + prose still used the deleted module names in present tense.

The status of root `ARCHITECTURE.md` is canonical L0 — every contributor or agent starting here is taught the L0 module topology. The rc5 wave's Rule G-2 sub-clause .c was scoped to L1 per-module ARCHITECTURE.md files because those are also canonical for module identity; the L0 entrypoint slipped through. Rule G-2 sub-clause .d closes the L0 half of the bidirectional invariant Rule G-1 sub-clause .a (Layered 4+1 Discipline) names: root architecture text + module ARCHITECTURE.md text + actual reactor topology must agree.

## Details

### Algorithm

1. Parse the canonical count from BOTH `pom.xml` (count of `<module>` entries inside `<modules>...</modules>`) AND `docs/governance/architecture-status.yaml#repository_counts.reactor_modules`. If they disagree, fail with `pom.xml_disagrees_with_status_yaml` and stop — that is a separate Rule R-B / Rule G-2 sub-clause .b invariant that should fail first.

2. Walk root `ARCHITECTURE.md` line-by-line, tracking fenced code block state (lines bracketed by ``` ``` `` ```).

3. For each non-code line that contains a module-count phrase matching `\b[0-9]+-module\b`, `\b[0-9]+ modules\b`, `\b[0-9]+ reactor modules\b`, or `**N modules**` markdown bold:
   - Extract the numeric claim N.
   - Scan ±3 lines for a historical marker (same family as Rule G-2 sub-clause .c + the rc6 additions `pre-Phase-C`, `consolidated`, `merged`).
   - If no marker present, N MUST equal the canonical count.

4. For each non-code line that contains a Java path claim matching `agent-[a-z-]+/src/main/java/[a-zA-Z0-9_/.-]+`:
   - If the path resolves on disk, accept.
   - Otherwise scan ±3 lines for a historical marker. If present, accept.
   - Otherwise fail with the unresolved path.

### Marker convention

Markers extend Rule G-2 sub-clause .c's set with two additions specific to ADR-0078 Phase C: `pre-Phase-C` (the consolidation cutoff) and `consolidated into` / `merged into` (verbs the consolidation prose uses). Rule G-2 sub-clause .c grammar (historical, moved, extracted per ADR-NNNN, superseded, formerly, deferred, pre-ADR-NNNN) remains accepted.

### Why scope to root ARCHITECTURE.md only

L2 documents under `docs/L2/` are technical deep-dives — they may legitimately freeze a snapshot architecture at the time of writing without continuous re-validation. Their drift risk is bounded by Rule G-1 sub-clause .a's freeze-id discipline. Root `ARCHITECTURE.md` and `agent-*/ARCHITECTURE.md` are canonical living entrypoints — those are what Rule G-2 sub-clause .c + Rule G-2 sub-clause .d protect.

### Fenced-tree-block extension (rc8 amendment, 2026-05-18, ADR-0082)

Beyond the prose pass above, a second sweep scans ONLY lines inside fenced code blocks (the inverse of the first pass). It tracks two pieces of state:

1. The current module context — the most recent indented line matching `<modulename>/   # comment` where `<modulename>` starts with `agent-` or `spring-ai-ascend-`. Its indent level is recorded.
2. For each subsequent indented line matching `<pkg>/spi/   # comment` whose indent is strictly greater than the module's indent, the rule looks up `<module>/module-metadata.yaml#spi_packages`. The list MUST contain at least one entry containing the substring `.<pkg>.spi`. If not, fail (subject to the same ±3-line historical-marker exemption).

This closes the **rc7 post-corrective review P0-1** finding: GraphMemoryRepository ownership drift hid inside the root ARCHITECTURE.md tree (a fenced code block) where the original Rule G-2 sub-clause .d pass intentionally skipped all lines. Rule G-2 sub-clause .d's fenced-block exclusion was a deliberate decision to avoid false positives on prose example code; the rc8 amendment narrowly re-enters fenced blocks ONLY when they have the tree-diagram shape (module-header + SPI leaf) and the leaf is verifiable against module-metadata.yaml (the canonical SSOT per ADR-0082).

## Activation

Activated 2026-05-18 by the v2.0.0-rc6 post-response architecture review response wave. Enforcer E119. Closes P0-2 of `docs/reviews/2026-05-18-l0-rc6-post-response-architecture-review.en.md`.

**rc8 amendment 2026-05-18** extended Rule G-2 sub-clause .d with the fenced-tree-block second pass per ADR-0082. Closes P0-1 of `docs/logs/reviews/2026-05-18-l0

## Cross-references

- Rule G-2 sub-clause .a (Architecture-Text Truth) — Rule G-2 sub-clause .d is the root-document path/count specialisation of Rule G-2 sub-clause .a.
- Rule G-1 sub-clause .a (Layered 4+1 Discipline) — Rule G-2 sub-clause .d guards the truthfulness of L0 path + count claims so root identity stays coherent.
- Rule G-2 sub-clause .b (Baseline Metrics Single Source) — same family of numeric-agreement-across-authority-docs invariant; Rule G-2 sub-clause .b covers entrypoint count phrases in `README.md` / `gate/README.md`, Rule G-2 sub-clause .d covers the analogous phrases inside root `ARCHITECTURE.md` (and adds path-claim coverage).
- Rule G-2 sub-clause .c (Active Module ARCHITECTURE Path Truth) — companion bidirectional gate at the L1 module level; Rule G-2 sub-clause .d closes the L0 root-level half Rule G-2 sub-clause .c does not reach.
- Rule G-2 sub-clause .e (Status YAML Allowed Claim Module Name Truth) — companion gate from the same rc6-post wave; Rule G-2 sub-clause .e covers the YAML-corpus side, Rule G-2 sub-clause .d covers the Markdown-corpus side.
- ADR-0078 (Phase C consolidation, 2026-05-18).
- ADR-0079 (Engine SPI + S2C SPI + shared kernel extraction, 2026-05-18).
- ADR-0081 (ResilienceContract dual-surface reconciliation, 2026-05-18) — sibling rc7 ADR; same wave.
- `docs/logs/reviews/2026-05-18-l0-rc6-post-response-architecture-review.en.md` finding P0-2 — origin.
- `docs/logs/reviews/2026-05-18-l0-rc6-post-response-architecture-review.en.md` finding P0-2 — origin.

### .e — (was sub-clause .e)

# Rule G-2 sub-clause .e — Status YAML Allowed Claim Module Name Truth

## Motivation

`docs/governance/architecture-status.yaml#repository_counts` is the structured single-source for module counts. Rule G-2 sub-clause .b (rc6, strengthened from rc5) enforces that numeric phrases in entrypoint docs match the structured block. But the same yaml file ALSO carries prose `allowed_claim:` strings on every capability row — and those strings can drift unchecked.

The 2026-05-18 rc6 post-response architecture review (finding P1-2 in `docs/logs/reviews/2026-05-18-l0-rc6-post-response-architecture-review.en.md`) found that:

- `architecture-status.yaml:1054` said "Service Layer (agent-platform HTTP edge + agent-runtime cognitive runtime)" — names two deleted modules in present tense.
- `architecture-status.yaml:1391` said "each of the 4 reactor modules" — count contradicts the canonical `reactor_modules: 9`.
- `architecture-status.yaml:1409` said "agent-runtime (kind:domain) declares 2 SPI packages" — names a deleted module.

The rc6 family-wide self-check found a fourth occurrence at line 720 (`platform_agent_runtime_independence` allowed_claim) that the reviewer's manual sweep missed.

The pattern is consistent: `allowed_claim:` text was authored during the pre-Phase-C era (when `agent-platform` and `agent-runtime` were separate modules) and never refreshed when ADR-0078 (Phase C consolidation) shipped. Rule G-2 sub-clause .b caught the numeric drift on entrypoint docs; Rule G-2 sub-clause .d caught the L0 ARCHITECTURE.md drift; Rule G-2 sub-clause .e catches the analogous drift inside this yaml ledger.

## Details

### Algorithm

For each line in `docs/governance/architecture-status.yaml`:

1. Match the line against `^\s+allowed_claim:\s*` (a yaml field assignment).
2. Extract the value (everything after the `:`); strip surrounding quotes if present.
3. Grep the value for `\bagent-platform\b` OR `\bagent-runtime\b` (negative lookahead on `agent-runtime-core` — the new shared-kernel module from ADR-0079).
4. If a stale match is found, scan ±3 lines of the yaml file for any of the historical markers listed in the kernel.
5. If no marker is found in the surrounding paragraph, fail with the offending line + the stale module name.

### Stale-pattern detection

The negative lookahead on `agent-runtime-core` is the important detail: ADR-0079 (2026-05-18) introduced a new module whose name starts with the substring `agent-runtime`. A naive substring search for `agent-runtime` would false-fire on every reference to the new module. The rule uses word-boundary regex (`\bagent-runtime\b`) so `agent-runtime-core` is NOT matched. Tools may use `grep -E 'agent-runtime\b' | grep -v 'agent-runtime-core'` as an equivalent pipeline.

### Marker convention

The marker set is intentionally the same family as Rule G-2 sub-clause .c + Rule G-2 sub-clause .d with two additions specific to yaml-ledger context: `archived` (for fully-archived rows pointing to `docs/archive/`) and `deprecated` (used by older capability rows).

### Why scope to allowed_claim only

`allowed_claim:` is the prose narration of each capability — the field most likely to contain free-form text that ages out. Other yaml fields (`implementation:`, `tests:`, `l0_decision:`) are structured path lists that can be cross-validated by other rules (Rule G-2 sub-clause .a, Rule R-C.a). Rule G-2 sub-clause .e narrowly targets the narrative drift surface.

## Activation

Activated 2026-05-18 by the v2.0.0-rc6 post-response architecture review response wave. Enforcer E120. Closes P1-2 of `docs/reviews/2026-05-18-l0-rc6-post-response-architecture-review.en.md`.

## Cross-references

- Rule G-2 sub-clause .a (Architecture-Text Truth) — Rule G-2 sub-clause .e is the ledger-yaml specialisation.
- Rule R-M sub-clause .f (S2cCallbackSignal Historical-Only in Authority) — same marker-convention family for deleted-type references.
- Rule G-2 sub-clause .b (Baseline Metrics Single Source) — sibling numeric-agreement gate from rc6; Rule G-2 sub-clause .b covers entrypoint numeric phrases, Rule G-2 sub-clause .e covers ledger prose module names.
- Rule G-2 sub-clause .d (Root ARCHITECTURE Count + Path Truth) — companion gate from the same rc6-post wave; Rule G-2 sub-clause .d covers the Markdown-corpus side, Rule G-2 sub-clause .e covers the YAML-corpus side.
- ADR-0078 (Phase C consolidation, 2026-05-18) — origin of the deleted module names.
- ADR-0079 (Engine SPI + shared kernel extraction, 2026-05-18) — introduces `agent-runtime-core` (the negative-lookahead exemption).
- `docs/logs/reviews/2026-05-18-l0-rc6-post-response-architecture-review.en.md` finding P1-2 — origin.
- `docs/logs/reviews/2026-05-18-l0-rc6-post-response-architecture-review.en.md` finding P1-2 — origin.

### .f — (was sub-clause .f)

# Rule G-2 sub-clause .f — Active Corpus Deleted-Module Name Truth

## Motivation

Rule G-2 sub-clause .e (rc7) prevented stale `agent-platform` / `agent-runtime` claims in `architecture-status.yaml#allowed_claim` text. The rc8 post-corrective review (P1-3) found that **equivalent current-tense claims still appeared** in:

- `ARCHITECTURE.md` §4 #59 — ArchUnit enforcement prose listed `agent-platform/web/replay/`, `agent-platform/web/trace/`, `agent-platform/web/session/` paths.
- `agent-service/src/test/java/.../McpReplaySurfaceArchTest.java` Javadoc — said "The rule lives in agent-platform" and "agent-runtime hosts no HTTP endpoints".
- `docs/governance/rules/rule-R-G.md` — said "Scope is intentionally narrow to `agent-runtime`" with existing `agent-platform` references.

The actual tests still check the current package names, so this was not a runtime failure — it was a contract-truth failure: an active L0 constraint teaches the wrong module path, and the gate didn't cover that surface.

Rule G-2 sub-clause .f widens Rule G-2 sub-clause .e's scope from one yaml field to the entire active corpus (`.md`, `.yaml`, `.java`), with the same historical-marker exemption pattern.

## Algorithm

For each candidate file across the corpus-wide find (rc11 widening: every `.md`, `.yaml`, `.yml`, `.java` minus the build-artefact + historical-by-location + frozen-release exemption list — see `gate/check_architecture_sync.sh` for the canonical case branches):

1. Track fenced-code-block state (`^````).
2. Skip yaml comment lines (`^[[:space:]]*#`).
3. For each remaining line, test `\bagent-platform\b` OR (`\bagent-runtime\b` AND NOT `\bagent-runtime-core\b`).
4. On a match, look ±3 lines for any historical marker. If found, the match is exempt.
5. Otherwise, flag as a violation with file:line.

## Exemption list (rc11 widening)

The rc9 impl scanned 3 narrow surfaces; the rc11 widening flips the model to "scan everything **minus** an explicit exemption list":

**Build artefacts / version control** — `target/`, `**/target/*`, `.git/`, `node_modules/` (skipped at `find` time).

**Frozen-by-location** — `docs/archive/`, `docs/adr/`, `docs/logs/reviews/`, `docs/logs/releases/2026-05-1[0-8]-*.md`, `docs/logs/releases/2026-05-19-l0-rc[1-9]-*` (single-digit rc1..rc9 historical), `docs/logs/releases/2026-05-19-l0-rc10-*` (retracted), `docs/v6-rationale/`, `docs/delivery/`, `docs/plans/`, `docs/runbooks/`, `docs/cross-cutting/`, `docs/architecture-views/`, `docs/CLAUDE-deferred.md`, `docs/quickstart.md`.

**Generated artefacts** — `docs/governance/architecture-graph.yaml` (built by `gate/build_architecture_graph.py`), `agent-service/target/classes/*` (generated from `src/main/resources`).

**Surfaces that necessarily name the deleted modules** — `docs/governance/architecture-status.yaml` (allowed_claim narrative tracks wave history), `docs/governance/enforcers.yaml` (enforcer descriptions name what they check), `docs/governance/rule-history.md` (historical rule scope catalog), `docs/governance/principles/*` (principle cards reference deferred sub-clauses naming pre-Phase-C modules), `docs/governance/whitepaper-alignment-matrix.md`, `docs/governance/rules/rule-{87,93,94,98,33,37,21}.md` (rule cards about the leakage rule itself + retargeted-Rule-R-C.e/33/37 cards), `docs/telemetry/policy.md` (backward-compat metric tag), `docs/dfx/*` (DFX yaml descriptions naming subsumed prior artifacts), `agent-runtime-core/ARCHITECTURE.md` (kernel module names the legacy loop it broke), `perf/*` (perf docs name pre-Phase-C tests as W4 targets), `spring-ai-ascend-dependencies/module-metadata.yaml` (BoM description).

**Rule-G-2.h domain (avoid duplicate-fail)** — `docs/contracts/*` is Rule G-2 sub-clause .h's primary scope; Rule G-2 sub-clause .f skips it to prevent both rules failing on the same hit.

**Live contract** — `docs/contracts/openapi-v1.yaml` (carries `x-contract-owner` metadata; separate update plan).

## Why this and not just expanding Rule G-2 sub-clause .e

Rule G-2 sub-clause .e specifically targets the `allowed_claim:` field of `architecture-status.yaml` — a tightly-bounded vocabulary check. Rule G-2 sub-clause .f needs different ergonomics (multi-file, multi-language, fenced-block awareness, ±3-line marker window). Keeping them as separate rules makes each one auditable.

## Enforcement

Enforced by E129 (Gate Rule G-2 sub-clause .f — `active_corpus_deleted_module_name_truth`). Positive self-test: clean fixture passes. Negative self-test: a synthetic .md with `agent-platform/web/foo` on a line without a marker → FAIL; same with `pre-Phase-C` marker within ±3 lines → PASS.

## Activation

Activated 2026-05-19 by the v2.0.0-rc9 wave (rc8 post-corrective review response). Enforcer E129 + E130 (positive + negative self-test fixtures).

## Cross-references

- ADR-0078 — the Phase-C consolidation that deleted agent-platform and agent-runtime.
- ADR-0083
- Rule G-2 sub-clause .e — sibling: same family, narrower scope (`architecture-status.yaml#allowed_claim`).
- Rule G-2 sub-clause .c — same family family at the agent-*/ARCHITECTURE.md scope.
- Rule G-2 sub-clause .d — same family at the root ARCHITECTURE.md scope.
- `docs/logs/reviews/2026-05-18-l0-rc8-post-corrective-architecture-review.en.md` finding P1-3 — origin.

### .g — (was sub-clause .g)

# Rule G-2 sub-clause .g — Release-Note Numeric Truth

## Motivation

The rc8 post-corrective review's P0-1 finding fixed the gate's executable manifest count mismatch (74 declared vs 102 actual), and rc9 added Rule G-5 sub-clause .c to enforce that `architecture-status.yaml#baseline_metrics.active_gate_checks` matches the canonical manifest. But Rule G-5 sub-clause .c's scope was narrow: it only checked **one** baseline-metrics key. The same drift mechanism (declared prose vs live source) was still possible for every other numeric claim — including graph node/edge counts which Rule G-5 sub-clause .c never touched.

The rc10 category sweep (defect family I-α) found that the rc9 release note line 33 declared "360 nodes / 510 edges" while the live `architecture-graph.yaml` header was 369 / 520. The delta narrative "+12 nodes / +24 edges" also failed the arithmetic (rc8 baseline 348 + 12 = 360 ≠ 369). The release note prose was wrong at write time and no rule caught it.

Rule G-2 sub-clause .g closes this gap for the most likely future-recurrence surface: release-note graph counts.

## Algorithm

The gate identifies the LATEST release note via `gate/lib/latest_release.sh::latest_release_path` — an rc-number-numeric resolver that extracts `rc(\d+)` from the basename and numeric-sorts. Older release notes are historical snapshots and exempt by construction — each captured the count at its wave time. (rc12 K-β replaced the prior lex-sort resolver — `find ... | sort | tail -1` — which placed `rc9` after `rc11` because character `9` > `1`; closure per Rule 102 release_recency_resolver_correctness.)

For the latest release note, the gate scans for the pattern `<N> nodes` and `<M> edges` (absolute claims, NOT preceded by `+` — delta-formatted claims like `+21 nodes / +34 edges` are exempt by syntax). For each absolute claim:

1. If `N` equals the live `architecture-graph.yaml#node_count` value → pass.
2. If `M` equals the live `architecture-graph.yaml#edge_count` value → pass.
3. If neither equals AND the line has a historical / `rc[N] correction` / `rc[N] snapshot` / `rc[N] first cut` / superseded marker within ±3 lines → pass (acceptable historical reference).
4. Otherwise → fail.

Lines inside fenced code blocks are excluded (gate runs are not authority surfaces).

## Why latest-only, not all release notes

Every prior release note is a frozen historical snapshot. The rc8 release note's "348 nodes" claim was correct at rc8 wave time. Requiring all release notes to carry per-wave snapshot markers would mean retrofitting hundreds of markers and the gate would still allow none-of-them to assert any truth. Latest-only ensures the CURRENT release-note prose can be trusted, which is what readers act on.

When a new release note ships, it becomes "latest" and Rule G-2 sub-clause .g starts enforcing its claims against then-current live values. The previous "latest" relaxes to historical snapshot status automatically.

## Why absolute vs delta distinction

Release notes commonly state both absolute counts ("369 nodes / 520 edges") and deltas ("+21 nodes / +34 edges since rc8"). Both formats use the word `nodes` and `edges`. The discriminating feature is the `+` prefix on deltas — Rule G-2 sub-clause .g's awk pattern excludes any number with a `+` immediately preceding it. This avoids false positives on the delta narrative.

## Enforcement

Enforced by E135 (Gate Rule G-2 sub-clause .g — `release_note_numeric_truth`). Positive self-test: synthetic latest release note with `369 nodes / 520 edges` matching live → pass. Negative self-test: same prose declaring `360 nodes / 510 edges` with no marker → fail. Also positive: `360 nodes / 510 edges` followed by `rc10 correction: ...` within ±3 lines → pass.

## Activation

Activated 2026-05-19 by the v2.0.0-rc10 wave (rc8 post-corrective review category-sweep follow-up). Enforcer E135 + E136.

## Cross-references

- ADR-0084
- Rule G-5 sub-clause .c — `baseline_metric_matches_executable_manifest` (Rule G-2 sub-clause .g extends the same mechanism to release-note prose).
- Rule G-2 sub-clause .b — baseline_metrics numeric-agreement (Rule G-2 sub-clause .g covers a parallel surface that Rule G-2 sub-clause .b didn't enumerate).
- `docs/logs/reviews/2026-05-18-l0-rc8-post-corrective-architecture-review.en.md` category-sweep follow-up — origin.

### .h — (was sub-clause .h)

# Rule G-2 sub-clause .h — Broad-Corpus Deleted-Module-Name Truth

## Motivation

The rc8 post-corrective review's P1-3 finding fixed deleted-module-name leakage in three specific surfaces (ARCHITECTURE.md §4 #59, `McpReplaySurfaceArchTest` Javadoc, `rule-R-G.md`), and rc9 added Rule G-2 sub-clause .f to prevent recurrence. But Rule G-2 sub-clause .f's **kernel** said "every active `.md`, `.yaml`, and `*.java` file" while its **implementation** scanned only three narrow surfaces: root `ARCHITECTURE.md`, `docs/governance/rules/*.md` (one level), and `agent-*/src/test/java/**/*{Test,IT}.java`. The rule body explicitly exempted:

- `docs/contracts/openapi-v1.yaml` ("separate update plan; carries x-contract-owner metadata")
- `*/src/test/resources/*` (test fixtures, including pinned contract snapshots)
- `docs/adr/*` (frozen ADR artifacts — legitimate)
- `docs/plans/*`, `docs/runbooks/*`, `docs/quickstart.md`, several other docs subtrees

The `ops/` directory was NOT in the exemption list, but the file-discovery `find` block also did not include it — so Rule G-2 sub-clause .f silently never scanned `ops/`. The kernel-vs-implementation drift was itself an undiscovered defect.

The rc10 category sweep (defect family I-ε) found ~7 hidden leaks Rule G-2 sub-clause .f missed in the un-scanned surfaces:

- `spring-ai-ascend-dependencies/module-metadata.yaml:9` description: "Bill of Materials — pins agent-platform, agent-runtime, ..."
- `ops/helm/spring-ai-ascend/values.yaml:7` `repository: springaiascend/agent-platform`
- `ops/helm/spring-ai-ascend/templates/deployment.yaml:18` `- name: agent-platform`
- `ops/helm/spring-ai-ascend/Chart.yaml:9` keyword `agent-platform`
- `docs/contracts/openapi-v1.yaml:287` `x-contract-owner: agent-platform`
- `docs/contracts/openapi-v1.yaml:294` note: "Integration test: agent-platform RunCursorFlowIT..."
- `agent-service/src/test/resources/contracts/openapi-v1-pinned.yaml:265` mirrored the live contract

## Algorithm

Rule G-2 sub-clause .h reuses Rule G-2 sub-clause .f's word-boundary awk regex (`(^|[^a-zA-Z0-9_-])agent-platform([^a-zA-Z0-9_-]|$)` and the parallel `agent-runtime` pattern with negative-lookahead-style exclusion of `agent-runtime-core`), and Rule G-2 sub-clause .f's ±3-line marker exemption set.

The DIFFERENCE is file-discovery scope. Rule G-2 sub-clause .h scans:

- `docs/contracts/*.yaml` — live API contracts (single-level, excludes versioned subdirectories like `docs/contracts/openapi-v1.yaml-pinned/` if they exist)
- `**/module-metadata.yaml` (any depth ≤ 3, excludes `target/`, `.git/`, `docs/archive/`) — per-module metadata that often describes BoM contents, allowed/forbidden deps, etc.

The exemption marker list includes `forbidden_dependencies` so that intentional `forbidden_dependencies: - agent-platform` entries in `agent-bus/module-metadata.yaml`, `agent-client/...`, `agent-evolve/...`, `agent-middleware/...` pass — those lists exist precisely to NAME deleted modules and prevent reintroduction.

## Why not just widen Rule G-2 sub-clause .f's scope

Two reasons:

1. **Audit trail**: Rule G-2 sub-clause .h has its own enforcer pair (E137 + E138) so the gate log + the rc10 release note both clearly attribute the new coverage to a specific wave + ADR. Modifying Rule G-2 sub-clause .f in-place would lose that traceability.
2. **Reviewer scope preservation**: Rule G-2 sub-clause .f's reviewer scope was deliberately narrow ("active root architecture, rule cards, and active test Javadocs") per rc8 post-corrective review P1-3 reviewer language. Widening Rule G-2 sub-clause .f silently would change what the rc8 reviewer originally signed off on. Rule G-2 sub-clause .h adds a sibling rule that explicitly covers the broader surface, with its own kernel and review attribution.

The kernel-vs-implementation drift in Rule G-2 sub-clause .f (kernel said "every", implementation scanned three surfaces) is itself worth a future amendment — likely an ADR-0085 follow-up that either (a) narrows Rule G-2 sub-clause .f's kernel to match its implementation, or (b) widens Rule G-2 sub-clause .f's implementation to match its kernel. rc10 chooses option (b) via sibling Rule G-2 sub-clause .h to minimize scope churn.

## Enforcement

Enforced by E137 (Gate Rule G-2 sub-clause .h — `broad_corpus_deleted_module_name_truth`). Positive self-test: synthetic `ops/helm/test.yaml` with only `agent-service` references → pass. Negative self-test: synthetic file with bare `agent-platform` reference and no historical marker → fail. Positive: same bare reference but with `pre-Phase-C` marker on adjacent line → pass.

## Activation

Activated 2026-05-19 by the v2.0.0-rc10 wave (rc8 post-corrective review category-sweep follow-up). Enforcer E137 + E138.

## Cross-references

- ADR-0078 — agent-service consolidation (the structural deletion that creates the leakage surface).
- ADR-0084
- Rule G-2 sub-clause .f — `active_corpus_deleted_module_name_truth` (Rule G-2 sub-clause .h's narrower predecessor — sibling rule for the surfaces Rule G-2 sub-clause .f doesn't cover).
- Rule G-2 sub-clause .e — `status_yaml_allowed_claim_module_name_truth` (Rule G-2 sub-clause .h's even-narrower predecessor — together they form the layered defense against deleted-module-name leakage: status-yaml → active corpus narrow → broad corpus).
- `docs/logs/reviews/2026-05-18-l0-rc8-post-corrective-architecture-review.en.md` category-sweep follow-up — origin.
