---
level: L1
view: development
status: shipped
authority: "ADR-0154 (Fact-Layer Authority); Rule G-15 (Fact-Layer Integrity)"
---

# `architecture/facts/` — Generated Structured Facts for AI-Unbiased L1 Understanding

This directory holds the **machine-extracted fact layer** for the spring-ai-ascend
architecture corpus. Files under `generated/` are produced ONLY by deterministic
extractor scripts, never by humans and never by an LLM. The hand-authored DSL,
ADRs, and L1 prose remain canonical for intent, rationale, trade-offs, and
non-goals; this directory carries the factual ground-truth they must agree with.

## Why this directory exists

The expert review filed under
[`docs/reviews/2026-05-27-l1-structured-facts-for-ai-unbiased-understanding.en.md`](../../docs/reviews/2026-05-27-l1-structured-facts-for-ai-unbiased-understanding.en.md)
argued that AI agents using the L1 Feature Registry must not be required to
infer factual claims (code paths, SPI signatures, test FQNs, contract operations,
module dependencies, runtime configuration) from hand-authored DSL or rendered
Markdown. The engineering response under
[`docs/reviews/2026-05-27-l1-structured-facts-response.en.md`](../../docs/reviews/2026-05-27-l1-structured-facts-response.en.md)
adopts that direction: a parallel, deterministically-generated fact layer is
introduced as the AI's primary input for implementation decisions; the human-
authored layer is downgraded to "intent / rationale" and cited *alongside* the
fact id when it speaks about code.

## Zone discipline

| Sub-path | Edited by | When | How drift is prevented |
|---|---|---|---|
| `README.md` (this file) | humans, with review | rarely | linked from `architecture/README.md` reading path |
| `schema/fact.schema.yaml` | humans, with review | rarely | YAML-schema parse check (Rule G-15.a) |
| `generated/` | deterministic extractor scripts ONLY | every gate run / every CI build / on demand | byte-identical regeneration gate (Rule G-15.b) + LLM-no-author rule (Rule G-15.c) |

> **LLM no-author rule:** no LLM, no human, no editor may modify any file under
> `architecture/facts/generated/`. Such files carry the `# DO NOT EDIT — see
> architecture/facts/README.md` banner and are checked for byte-identical
> regeneration on every gate run. To change a generated fact, change the
> extractor binary or the underlying source authority (code, contract YAML,
> ADR, etc.) and re-run the extractor.

## Provenance contract

Every entry in every fact file carries the eight REQUIRED provenance fields
declared in [`schema/fact.schema.yaml`](schema/fact.schema.yaml) plus four
OPTIONAL facets that individual extractors MAY emit. Round-2 Wave B
(2026-05-28 P1-5) reconciles the previous README/schema/checker
disagreement to a single line: schema-required is the authority.

**Required (8 fields):**

```yaml
fact_id:           "<kebab-case unique id; '/' permitted as namespace separator>"
fact_kind:         code_symbol | contract_operation | schema | test | build_module | runtime_config | adr | relationship
source_kind:       code | contract | test | build | config | adr
source_path:       "<repo-relative path>"
extractor:         "<binary id under tools/architecture-workspace/.../facts/ or gate/lib/extractors/>"
extractor_version: "<semver tag of the extractor module>"
repo_commit:       "<40-char workspace HEAD at extraction time>"
observed_value:    { ... }                # the fact payload itself
```

**Optional facets (extractor-dependent; promoted to required in a future
sub-wave once every extractor emits them):**

```yaml
source_symbol:     "<FQN | operation_id | adr_id | yaml-anchor>"
source_span:       "<startLine:startCol-endLine:endCol | NA>"
source_refs:       [<fact_id>, ...]       # cross-fact dependencies
notes:             "<free-form extractor note>"
```

> **Reproducibility note:** `generated_at_utc` is intentionally OMITTED from
> generated payloads — live timestamps would break the byte-identical regen
> contract that Rule G-13 enforces. The `repo_commit` field provides
> provenance without sacrificing reproducibility (SLSA-style: same source +
> same extractor → byte-identical output).
>
> **Schema enforcement (Round-2 Wave B):** the gate validates every fact
> entry against the JSON Schema declared in `schema/fact.schema.yaml`. A
> missing required field, malformed `fact_id`, or non-40-char-hex
> `repo_commit` fails Rule G-15.b. The hand-rolled subset check that
> shipped in Round-1 is replaced by a real JSON Schema validator.

## Reading path (AI agents — read these BEFORE prose)

For implementation decisions that name code, contracts, tests, or runtime
configuration, read in this order:

1. **`architecture/facts/generated/code-symbols.json`** (Wave 4) — public
   interfaces, records, enums, Spring stereotypes per module.
2. **`architecture/facts/generated/contract-surfaces.json`** (Wave 3) — OpenAPI
   operations + per-contract YAML status + ADR refs.
3. **`architecture/facts/generated/tests.json`** (Wave 4) — JUnit / Failsafe /
   ArchUnit test classes and methods.
4. **`architecture/facts/generated/module-build.json`** (Wave 2) — Maven module
   graph, SPI declarations, allowed dependency direction.
5. **`architecture/facts/generated/runtime-config.json`** (Wave 2) — Spring
   configuration properties, posture guards, default values.
6. **`architecture/facts/generated/adrs.json`** (Wave 2) — ADR id → title /
   status / decided_by[] / relates_to[] / extends[].
7. **THEN** `architecture/features/function-points.dsl` and the rendered
   `architecture/docs/L1/<module>/features/README.md` catalog.
8. **THEN** L1 prose under `architecture/docs/L1/<module>/*.md`.
9. **THEN** ADR rationale under `docs/adr/*.yaml`.

If an L1 prose claim disagrees with a generated fact, the FACT wins. File an
issue and update the prose; never adjust the fact file directly.

## Extraction commands

Until Wave 6 ships, the extractor invocation is the Maven exec goal:

```bash
./mvnw -f tools/architecture-workspace/pom.xml exec:java@extract-facts \
    -Dexec.args="--repo . --out architecture/facts/generated"
```

`--check` mode (used by the gate) verifies byte-identical regeneration:

```bash
./mvnw -f tools/architecture-workspace/pom.xml exec:java@extract-facts \
    -Dexec.args="--repo . --out architecture/facts/generated --check"
```

## Lifecycle (current state)

Round-1 (2026-05-27) shipped Waves 1-6 as a contiguous PR train; Round-2
(2026-05-28) lands corrective Waves A-B-C in response to expert review
P0-1..P2-3. Current authoritative state (single source: this section +
`docs/governance/architecture-status.yaml#allowed_claim`):

| Surface | State | Authority |
|---|---|---|
| `architecture/facts/README.md` (this file) | shipped | ADR-0154 + Round-2 Wave C truth-up |
| `architecture/facts/schema/fact.schema.yaml` | shipped (`fact_id` regex permits `/`; Round-1 W2 + Round-2 Wave B README reconciliation) | ADR-0154 |
| `architecture/facts/generated/module-build.json` | shipped | Round-1 Wave 2 (`ModuleBuildFactExtractor`) |
| `architecture/facts/generated/adrs.json` | shipped | Round-1 Wave 2 (`AdrFactExtractor`) |
| `architecture/facts/generated/runtime-config.json` | shipped | Round-1 Wave 2 (`RuntimeConfigFactExtractor`) |
| `architecture/facts/generated/contract-surfaces.json` | shipped — Round-2 Wave B removed the `parse_failed: true` swallow; `run-event.v1.yaml` fixed | Round-1 Wave 3 + Round-2 Wave B (closes P1-3) |
| `architecture/facts/generated/code-symbols.json` | shipped — fail-closed on missing classes | Round-1 Wave 4 + Round-2 Wave B (closes P2-3) |
| `architecture/facts/generated/tests.json` | shipped — fail-closed on unbuilt modules; legitimately empty `target/` skipped | Round-1 Wave 4 + Round-2 Wave B |
| `architecture/profile/saa-property-authority.yaml` | shipped | Round-1 Wave 1 |
| `architecture/features/function-points.dsl` (W5 schema) | shipped — Round-2 Wave A truth-up replaced four hallucinated FP refs against real `RunController.{create,cancel,get}` + `RunHttpContractIT`; `FP-LIST-RUNS` removed (no list endpoint exists) | Round-2 Wave A (closes P0-1) |
| `AGENTS.md` AI consumption contract | shipped | Round-1 Wave 6 |
| Rule G-15 sub-clause `.a` | blocking | Round-1 Wave 1 |
| Rule G-15 sub-clause `.b` (provenance + JSON-schema validation) | blocking | Round-1 Wave 2 + Round-2 Wave B (closes P1-5) |
| Rule G-15 sub-clause `.c` (banner + ExtractFactsCli `--check` byte-diff) | blocking | Round-1 Wave 4 + Round-2 Wave B (closes P1-1) |
| Rule G-15 sub-clause `.d` (FunctionPoint ref resolver) | blocking | Round-2 Wave A (closes P1-2) |

> Wave-status history: Round-1 ship notes lived inline in
> `docs/reviews/2026-05-27-l1-structured-facts-response.en.md`; Round-2
> corrective ship notes live in
> `docs/reviews/2026-05-28-fact-layer-delivery-correction-response.en.md`.
> Do not chase wave status anywhere else in the repository — those two
> response files + this table are the single source.

## Cross-references

- ADR-0154 — Fact-Layer Authority (this directory's anchor ADR)
- Rule G-15 — Fact-Layer Integrity (kernel in CLAUDE.md, card at
  `docs/governance/rules/rule-G-15.md`)
- Rule G-13 — Single-Source Rendering Coherence (the render-idempotency
  pattern that this directory generalises to extracted facts)
- `architecture/profile/saa-property-authority.yaml` — classification of every
  `saa.*` property as `intent` / `factual_generated` /
  `factual_hand_authored_grandfathered` with sunset dates
- `tools/architecture-workspace/` — home of the Java extractor binaries
- `docs/reviews/2026-05-27-l1-structured-facts-for-ai-unbiased-understanding.en.md` —
  expert review that triggered this layer
- `docs/reviews/2026-05-27-l1-structured-facts-response.en.md` — engineering
  response with per-SFR accept/reject decisions
