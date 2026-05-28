---
level: L1
view: development
status: review_required
authority: "Post-delivery review on 2026-05-28; follows ADR-0154 / Rule G-15"
responds_to:
  - docs/reviews/2026-05-27-l1-structured-facts-for-ai-unbiased-understanding.en.md
  - docs/reviews/2026-05-27-l1-structured-facts-response.en.md
---

# Correction Request: Fact-Layer Delivery Must Close the Evidence Loop

Date: 2026-05-28

Audience: Engineering team maintaining the Spring AI Ascend architecture
workspace, L1 Feature Registry, fact-layer extractors, gates, and generated
architecture projections.

## Executive summary

The delivery moves in the right direction. It introduces
`architecture/facts/`, extractor code under `tools/architecture-workspace`,
Rule G-15, ADR-0154, an AI reading-path update, and generated JSON fact files.
Those are the correct building blocks.

However, the implementation is not ready to accept. The current delivery still
allows AI-facing structured facts to be wrong, hand-authored, stale, or
unchecked by the gate. The most serious issue is that new FunctionPoint
evidence fields in `architecture/features/function-points.dsl` are not
script-derived and several of them point to code classes, methods, tests, and
contract operations that do not exist.

The hard requirement remains:

> Structured facts used by AI for implementation guidance must be produced or
> validated by deterministic scripts from authoritative source surfaces. Human
> or LLM-authored factual fields must not become the AI's source of truth unless
> a gate proves that they resolve to generated facts.

Until the corrections below are implemented, the fact layer is a promising
scaffold, not an evidence-backed authority.

## Review root cause

Root cause: the delivery introduced generated fact files, but the AI-facing
FunctionPoint and gate integration still rely on hand-authored factual claims
and incomplete enforcement.

The strongest valid reading of the engineering intent is that the team tried to
ship a multi-wave fact-layer train quickly. That is reasonable. The defect is
that later-wave artifacts were partially landed without the resolver gates that
make them trustworthy.

## Blocking findings

### Finding P0-1: FunctionPoint evidence fields contain false facts

`architecture/features/function-points.dsl` now carries `saa.code_entrypoint_refs`,
`saa.test_refs`, and `saa.contract_op_refs` for shipped HTTP FunctionPoints, but
several values do not resolve to the generated fact layer or real code.

Examples:

- `FP-CREATE-RUN` references
  `agent-service/src/main/java/com/huawei/ascend/service/runtime/api/RunsController.java#createRun`.
  The real controller is
  `agent-service/src/main/java/com/huawei/ascend/service/platform/web/runs/RunController.java`,
  and its method is `create(...)`, not `createRun(...)`.
- `FP-CANCEL-RUN` references `RunsController.java#cancelRun`; the real method is
  `RunController.cancel(...)`.
- `FP-GET-RUN-STATUS` references `RunsController.java#getRun`; the real method
  is `RunController.get(...)`.
- `FP-LIST-RUNS` references `RunsController.java#listRuns`, but the current
  controller has no list method and `docs/contracts/openapi-v1.yaml` has no
  `listRuns` operation.
- `saa.test_refs` names `RunControllerCreateIT`,
  `RunControllerCancelIT`, `RunControllerGetIT`, and `RunControllerListIT`,
  while the generated test facts show the current HTTP contract test class as
  `com.huawei.ascend.service.platform.web.runs.RunHttpContractIT`.

Local evidence:

- `architecture/features/function-points.dsl`
- `agent-service/src/main/java/com/huawei/ascend/service/platform/web/runs/RunController.java`
- `architecture/facts/generated/code-symbols.json`
- `architecture/facts/generated/tests.json`
- `docs/contracts/openapi-v1.yaml`
- Review cross-check command:
  `python` script comparing FunctionPoint refs against generated `code-symbols`,
  `tests`, and `contract-surfaces` facts reported unresolved refs for all four
  HTTP FunctionPoints and an unresolved `contract-op/listruns`.

External support:

- The OpenAPI Specification defines `operationId` as a unique operation
  identifier and states that tools may use it to uniquely identify operations:
  <https://spec.openapis.org/oas/v3.1.0#operation-object>.
- Structurizr's model-as-code guidance emphasizes structured architecture
  sources that can be kept in sync with reality:
  <https://docs.structurizr.com/as-code>.

Required correction:

1. Remove or regenerate the false FunctionPoint refs.
2. Add a deterministic resolver that maps:
   - `code_entrypoint_refs` to `code-symbols.json`;
   - `test_refs` to `tests.json`;
   - `contract_op_refs` or replacement `input_contract_refs` /
     `output_contract_refs` to `contract-surfaces.json`.
3. Fail the gate when any shipped `http` or `spi` FunctionPoint contains an
   unresolved factual reference.

Acceptance criteria:

- `FP-CREATE-RUN`, `FP-CANCEL-RUN`, `FP-GET-RUN-STATUS`, and `FP-LIST-RUNS`
  either resolve to real generated facts or are downgraded out of `shipped`
  until source authority exists.
- No FunctionPoint can cite a Java path, method name, test FQN, or contract
  operation that is absent from the generated fact files.
- A deliberate typo in any of these refs fails `bash gate/check_architecture_sync.sh`.

### Finding P0-2: The canonical architecture gate currently fails

The canonical gate fails after this delivery because generated architecture DSL
fragments drifted.

Review command:

```bash
bash gate/check_architecture_sync.sh
```

Observed failure:

```text
ARCHITECTURE WORKSPACE: generated-zone drift
FAIL: drift detected in 3 fragment(s):
  - enforcers.dsl
  - rules.dsl
  - adr-graph.dsl
GATE: FAIL (workspace gate)
```

Local evidence:

- `gate/check_architecture_sync.sh`
- `architecture/generated/enforcers.dsl`
- `architecture/generated/rules.dsl`
- `architecture/generated/adr-graph.dsl`
- `docs/adr/0154-fact-layer-authority.yaml`
- `docs/governance/enforcers.yaml`
- `docs/governance/rules/rule-G-15.md`

Required correction:

1. Regenerate and commit the changed generated DSL fragments after adding
   ADR-0154, Rule G-15, and E179.
2. Re-run the canonical gate from a clean working tree.

Acceptance criteria:

- `bash gate/check_architecture_sync.sh` passes.
- `git status --short` does not show regenerated `architecture/generated/*`
  drift after the gate completes.

### Finding P1-1: Rule G-15.c claims byte-identical regeneration, but the gate does not run the extractor

`ExtractFactsCli` implements `--check` mode and can compare emitted facts with
committed files. The canonical architecture gate does not call this mode. It
only runs `python3 gate/lib/check_fact_layer_integrity.py --enforce a,b,c`.
Inside that Python checker, sub-clause `.c` checks for a `DO NOT EDIT` banner
only. It does not re-run extraction and does not compare bytes.

Local evidence:

- `tools/architecture-workspace/src/main/java/com/huawei/ascend/tools/architecture/facts/ExtractFactsCli.java`
- `gate/check_architecture_sync.sh`
- `gate/lib/check_fact_layer_integrity.py`
- `docs/governance/rules/rule-G-15.md`

External support:

- Reproducible Builds defines reproducibility as recreating bit-by-bit
  identical artifacts from the same source, environment, and instructions:
  <https://reproducible-builds.org/docs/definition/>.
- SLSA provenance treats build outputs as products of an identified builder and
  build definition:
  <https://slsa.dev/provenance/>.

Required correction:

1. Wire `ExtractFactsCli --check` into `gate/check_architecture_sync.sh`.
2. Ensure the command runs after the relevant Java modules have compiled, or
   make the extractor fail closed when required `target/classes` surfaces are
   missing.
3. Add a self-test that mutates a generated fact payload while preserving the
   banner and proves the gate fails.

Acceptance criteria:

- A manual edit to `architecture/facts/generated/code-symbols.json` fails the
  gate even if the `DO NOT EDIT` banner remains intact.
- Re-running the extractor from the same source commit produces byte-identical
  files.
- The gate output clearly reports which fact file drifted.

### Finding P1-2: Rule G-15.d is a no-op

Rule G-15.d says shipped `http` or `spi` FunctionPoints must carry hard evidence
refs that resolve to generated facts. The implementation of
`check_subclause_d` currently returns an empty finding list unconditionally.
This allowed P0-1 to pass even though the refs are wrong.

Local evidence:

- `docs/governance/rules/rule-G-15.md`
- `gate/lib/check_fact_layer_integrity.py`
- `architecture/features/function-points.dsl`

Required correction:

1. Implement `check_subclause_d`.
2. Parse `architecture/features/function-points.dsl`.
3. For each FunctionPoint with `saa.status "shipped"` and channel `http` or
   `spi`, require non-empty hard evidence fields.
4. Resolve every ref against generated facts, not against plain text.
5. Fail closed on unresolved refs.

Acceptance criteria:

- Running `python gate/lib/check_fact_layer_integrity.py --enforce a,b,c,d`
  fails on the current false FunctionPoint refs before they are corrected.
- The same command passes only after refs resolve against generated facts.
- Gate fixtures cover both positive and negative cases.

### Finding P1-3: Contract extraction hides parser failures as facts

`ContractFactExtractor` catches YAML parser exceptions and emits a fact with
`parse_failed: true`. The generated `contract-surfaces.json` currently contains
such a failed fact for `run-event.v1.yaml`. That is not a reliable contract
fact; it is evidence that the extractor failed to understand the contract.

Local evidence:

- `tools/architecture-workspace/src/main/java/com/huawei/ascend/tools/architecture/facts/ContractFactExtractor.java`
- `architecture/facts/generated/contract-surfaces.json`
- `docs/contracts/run-event.v1.yaml`

External support:

- The OpenAPI Specification's purpose is to let humans and computers discover
  and understand API capabilities from a defined interface description:
  <https://spec.openapis.org/oas/v3.1.0>.

Required correction:

1. Treat contract parse failure as a gate failure for active contract surfaces.
2. Fix `run-event.v1.yaml` or narrow the extractor to a declared format with a
   documented non-parseable exclusion list.
3. Do not emit `parse_failed` entries as authoritative facts unless the fact
   kind explicitly represents extraction failure and is blocked from AI
   implementation guidance.

Acceptance criteria:

- No `parse_failed: true` entry exists in committed `contract-surfaces.json`.
- Introducing malformed YAML in an active contract file fails the gate.
- AI-facing contract facts contain parsed structural payload, not failure stubs.

### Finding P1-4: Feature catalog drift remains open

The prior review already identified drift in the rendered agent-service feature
catalog. The delivery response said the drift check would be re-run and
confirmed before later waves. The drift still exists.

Review command:

```bash
python gate/lib/render_features_catalog.py --check
```

Observed failure:

```text
DRIFT: architecture/docs/L1/agent-service/features/README.md
```

Local evidence:

- `gate/lib/render_features_catalog.py`
- `architecture/docs/L1/agent-service/features/README.md`
- `docs/reviews/2026-05-27-l1-structured-facts-response.en.md`

Required correction:

1. Re-render or correct `architecture/docs/L1/agent-service/features/README.md`.
2. Add the catalog drift check to the same verification path used for this
   fact-layer delivery, if it is not already covered.

Acceptance criteria:

- `python gate/lib/render_features_catalog.py --check` passes.
- The canonical architecture gate remains green after rendering.

### Finding P1-5: Fact schema and README disagree on required provenance

`architecture/facts/README.md` says every fact entry carries `source_symbol`,
`source_span`, and `source_refs` at minimum. The schema does not require
`source_span`, and the generated fact entries do not emit it. The checker only
requires eight fields. This mismatch creates ambiguity about what an AI can rely
on.

Local evidence:

- `architecture/facts/README.md`
- `architecture/facts/schema/fact.schema.yaml`
- `gate/lib/check_fact_layer_integrity.py`
- `architecture/facts/generated/*.json`

External support:

- JSON Schema uses `required` to define which properties must be present and
  `additionalProperties` to control undeclared fields:
  <https://json-schema.org/draft/2020-12/draft-bhutton-json-schema-01>.

Required correction:

1. Decide whether `source_span`, `source_symbol`, and `source_refs` are truly
   required.
2. Make README, schema, extractor output, and checker agree.
3. Add schema validation against generated facts, not just YAML parse checks.

Acceptance criteria:

- The README's "minimum provenance" list exactly matches enforced schema.
- Every generated fact file validates against the fact schema.
- A missing required provenance field fails the gate.

### Finding P2-1: Documentation wave status is inconsistent with the delivered files

Several docs still describe this as Wave 1 with generated facts empty or
extractors pending, while the repository already contains generated facts and
extractor classes for build, ADR, runtime config, contract, code symbols, and
tests.

Local evidence:

- `architecture/README.md`
- `architecture/facts/README.md`
- `docs/governance/architecture-status.yaml`
- `docs/reviews/2026-05-27-l1-structured-facts-response.en.md`
- `tools/architecture-workspace/src/main/java/com/huawei/ascend/tools/architecture/facts/`
- `architecture/facts/generated/*.json`

Required correction:

1. Replace wave labels with an accurate current implementation state.
2. Make one file the authority for fact-layer wave status and have other docs
   point to it instead of repeating stale claims.

Acceptance criteria:

- No doc says generated facts are empty if generated facts are committed.
- No doc says extractors are pending if extractor classes and fact outputs are
  already present.
- `architecture-status.yaml#allowed_claim` agrees with the repository state.

### Finding P2-2: Extractors lack direct tests

The `tools/architecture-workspace` module compiles and its existing tests pass,
but there are no direct tests for `ExtractFactsCli`, `FactWriter`,
`ContractFactExtractor`, `CodeSymbolFactExtractor`, or
`TestInventoryFactExtractor`.

Local evidence:

- `tools/architecture-workspace/src/main/java/com/huawei/ascend/tools/architecture/facts/`
- `tools/architecture-workspace/src/test/java/`
- Review search command:
  `rg -n "Fact|ExtractFacts|CodeSymbol|ContractFact|TestInventory|ModuleBuild" tools/architecture-workspace/src/test -g "*.java"`
  returned no fact-layer tests.

Required correction:

1. Add focused fixture tests for each extractor.
2. Add a determinism test that runs extraction twice and compares bytes.
3. Add negative tests for malformed contracts and missing class directories.

Acceptance criteria:

- `./mvnw -f tools/architecture-workspace/pom.xml clean verify` runs
  fact-layer tests, not only profile/model tests.
- Contract parse failure and missing compiled classes are covered by tests.
- Deterministic ordering is covered by tests.

### Finding P2-3: Code and test extractors silently skip missing compiled classes

`CodeSymbolFactExtractor` and `TestInventoryFactExtractor` read
`target/classes` and `target/test-classes`. If those directories are missing,
they silently skip the module. This makes non-check extraction dangerous in a
clean workspace: it can emit partial or empty facts instead of failing closed.

Local evidence:

- `tools/architecture-workspace/src/main/java/com/huawei/ascend/tools/architecture/facts/CodeSymbolFactExtractor.java`
- `tools/architecture-workspace/src/main/java/com/huawei/ascend/tools/architecture/facts/TestInventoryFactExtractor.java`

Required correction:

1. Fail when an active module lacks expected compiled classes, unless an
   explicit `--allow-missing-classes` mode is used for fixture generation.
2. Document that fact extraction must run after `./mvnw clean verify`, or wire
   compilation into the extraction workflow.

Acceptance criteria:

- Running extraction from a clean tree without compiled class directories fails
  with a clear message.
- CI runs extraction after compilation.
- No committed fact file can be regenerated from a partial module set.

## Verification performed during review

Commands run:

```bash
python gate/lib/check_fact_layer_integrity.py --enforce a,b,c
bash -lc './mvnw -f tools/architecture-workspace/pom.xml exec:java@extract-facts -Dexec.args="--repo . --out architecture/facts/generated --check"'
bash gate/check_architecture_sync.sh
python gate/lib/render_features_catalog.py --check
bash -lc './mvnw -f tools/architecture-workspace/pom.xml clean verify'
bash -lc './mvnw clean verify'
python gate/lib/check_fact_layer_integrity.py --enforce a,b,c,d
```

Observed results:

- `./mvnw clean verify` passed.
- `./mvnw -f tools/architecture-workspace/pom.xml clean verify` passed.
- `ExtractFactsCli --check` passed after the Java modules were built.
- `python gate/lib/check_fact_layer_integrity.py --enforce a,b,c,d` passed,
  but this is misleading because `.d` is a no-op.
- `python gate/lib/render_features_catalog.py --check` failed with
  `DRIFT: architecture/docs/L1/agent-service/features/README.md`.
- `bash gate/check_architecture_sync.sh` failed with generated-zone drift in
  `enforcers.dsl`, `rules.dsl`, and `adr-graph.dsl`.

## Decision requested from engineering

Please do not merge or mark the fact-layer delivery as complete until the P0
and P1 findings are corrected.

The expected end state is not simply "fact files exist." The expected end state
is:

1. generated facts are reproducible;
2. AI-facing FunctionPoint facts resolve to generated facts;
3. gates fail when generated facts drift;
4. gates fail when FunctionPoint evidence points to nonexistent code, tests, or
   contracts;
5. contract extraction fails closed on active malformed contracts;
6. the architecture generated zone and feature catalogs are clean.

## Source index

Local sources:

- `architecture/features/function-points.dsl`
- `architecture/facts/README.md`
- `architecture/facts/schema/fact.schema.yaml`
- `architecture/facts/generated/code-symbols.json`
- `architecture/facts/generated/tests.json`
- `architecture/facts/generated/contract-surfaces.json`
- `architecture/README.md`
- `architecture/generated/enforcers.dsl`
- `architecture/generated/rules.dsl`
- `architecture/generated/adr-graph.dsl`
- `agent-service/src/main/java/com/huawei/ascend/service/platform/web/runs/RunController.java`
- `docs/contracts/openapi-v1.yaml`
- `docs/contracts/run-event.v1.yaml`
- `docs/adr/0154-fact-layer-authority.yaml`
- `docs/governance/architecture-status.yaml`
- `docs/governance/enforcers.yaml`
- `docs/governance/rules/rule-G-15.md`
- `docs/reviews/2026-05-27-l1-structured-facts-response.en.md`
- `gate/check_architecture_sync.sh`
- `gate/lib/check_fact_layer_integrity.py`
- `gate/lib/render_features_catalog.py`
- `tools/architecture-workspace/src/main/java/com/huawei/ascend/tools/architecture/facts/`

External sources:

- OpenAPI Specification v3.1.0: <https://spec.openapis.org/oas/v3.1.0>
- Reproducible Builds definition:
  <https://reproducible-builds.org/docs/definition/>
- SLSA provenance: <https://slsa.dev/provenance/>
- JSON Schema Draft 2020-12:
  <https://json-schema.org/draft/2020-12/draft-bhutton-json-schema-01>
- Structurizr as code: <https://docs.structurizr.com/as-code>
- Structurizr DSL: <https://docs.structurizr.com/dsl>
