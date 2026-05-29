# Review Response & Rebuttal — EnginePort / EngineeringFrame / Authority Convergence

Date: 2026-05-29
Reviewed commit: `d66749b`
Current HEAD at response time: `98a58c1`
Response branch: `review/2026-05-29-engineport-frame-authority-convergence`

Responds to:
- `docs/logs/reviews/2026-05-29-engine-port-engineering-frame-implementation-review.en.md` (Codex; findings F1–F8)
- `docs/reviews/2026-05-29-progressive-ai-learning-curve-delivery-correction-request.en.md` (P0-1..P0-3, P1-1..P1-4)

Every finding was independently re-validated against current HEAD (file:line) before disposition. The two commits since the reviewed commit (`a7b2d38` gate self-test race fix, `98a58c1` family-ledger refresh) touch only `gate/test_architecture_sync_gate.sh` and `recurring-defect-families.yaml`; they resolve the Rule G-9.b family-freshness half of F7 and nothing else. All other findings stood.

## Disposition

| Finding | Disposition | Closing approach |
|---|---|---|
| F1 — `EF-ENGINE-PORT` absent; `EF-ORCHESTRATION-SPI` not re-homed | **Accepted** | W1: add `EF-ENGINE-PORT` (owner `agent-bus`); re-home `EF-ORCHESTRATION-SPI` owner + contains edge to `agent-bus`. |
| F2 / P0-3 — Java `EnginePort` in-process-only | **Accepted** | W0: neutral `EnginePort(ExecutionContext, ExecuteRequest) → Flow.Publisher<AgentEvent>` + `describe()`; in-process real adapter; **mock-functional** `internal_rpc` + `a2a`. |
| F3 / P1-1 — transport hardwired | **Accepted** | W0: inject `EnginePort`; `app.engine.transport` selection (default `in_process`). |
| F4 — frames carry ProductClaim; no `traverses` edges | **Accepted** | W1: strip `saa.productClaim` from 6 frames; add real `traverses` Feature→Frame edges. |
| F5 / P0-1 — generated facts stale | **Accepted** | W7: regenerate all 6 fact files from the extractor after the Java package move + clean build; `--check` byte-identity. |
| F6 / P1-2 — old SPI path in active prose/templates | **Accepted (expanded)** | W2: fix at `.j2` template sources + inline surfaces; **and relocate 3 stranded Java test files** (see hidden defects). |
| F7 / P0-2 — gate red | **Accepted (partly already closed)** | Family-freshness half closed by `3756b1f`+`98a58c1`; workspace baseline parity (608/469 vs 609/478) closed in W6. |
| F8 — shipped frames with zero anchors | **Accepted (expanded: 4 frames, not 2)** | W1: flip `EF-ORCHESTRATION-SPI`, `EF-ENGINE-DISPATCH`, `EF-INTERNAL-EVENT-QUEUE`, `EF-TRANSLATION-INTERCEPT` → `design_only`; W5 prevention gate. |
| P1-3 — product "mojibake/corruption" | **Partially rejected — see §Rebuttal** | Corruption claim is false (clean UTF-8); the underlying non-English-in-Tier-1 issue is accepted → W3 translate-to-English + archive. |
| P1-4 — `D:\.claude\plans` local paths | **Accepted (expanded: 200 sites, both separators)** | W3+W4 strip/demote active-authority sites; fix the `gate/lib/extract_rules.sh` emitter (150 generated mirrors); W5 ban gate matching both `\` and `/`. |

## Rebuttal — P1-3 "mojibake / corruption" is factually incorrect

The correction request states `product/PRODUCT.md` and the sibling `product/*.yaml`/`journey.md` files contain "corrupted mojibake text" (request lines 216–219). This is **not correct**, and the framing matters because the prescribed fix ("replace corrupted text") implies data loss that is not warranted.

Evidence:
- The four files are well-formed UTF-8. They contain **zero** U+FFFD replacement characters and no GBK/UTF-8 double-encoding artifacts (`Ã`, `â€`, `ï¿½`). The bytes the reviewer read as "mojibake" are correctly-encoded Simplified Chinese (e.g. the middle-office-mode and capability-reuse-mode deployment-topology terms, and the MLPS / PIPL / JR/T 0223-2021 regulatory names).
- The corruption almost certainly originated in the **reviewer's own rendering pipeline** (a viewer decoding UTF-8 bytes as GBK/Latin-1), not in the repository bytes.

**However, the substantive concern underneath the mischaracterization is valid and is accepted.** Chinese text in Tier-1 *auto-loaded* authority (`product/PRODUCT.md`, `claims.yaml`, `personas.yaml`, `journey.md`, and two glosses in `docs/governance/SESSION-START-CONTEXT.md`) is loaded into every AI session and therefore violates the `CLAUDE.md` kernel ("Translate all instructions into English before any model call") and `AGENTS.md:5`. Resolution (W3): translate the AI-facing auto-loaded authority to English (normalized terms: middle-office mode, capability-reuse mode, MLPS 2.0/3.0, PIPL, Data Security Law, JR/T 0223-2021, Critical Information Infrastructure), and preserve the verbatim Chinese "do-not-paraphrase" product-owner input under `product/source-inputs/` (a non-auto-loaded archive). Two non-ASCII YAML *keys* in `claims.yaml` are made ASCII-safe. This satisfies both the English-only kernel and verbatim preservation — without any "de-corruption," because there is nothing corrupted to repair.

## Hidden defects found beyond the reviews

A full corpus sweep (7 parallel family scanners) surfaced issues the reviews did not enumerate:

1. **F8 is 4 frames, not 2.** `EF-ORCHESTRATION-SPI` and `EF-TRANSLATION-INTERCEPT` are also `shipped` with zero anchored FunctionPoints — the same defect class the reviewer flagged for `EF-INTERNAL-EVENT-QUEUE`/`EF-ENGINE-DISPATCH`.
2. **The package move is incomplete at the code layer.** While the main SPI types already live in `com.huawei.ascend.bus.spi.engine`, three **test files** are still in the old `com.huawei.ascend.engine.orchestration.spi` package path: `SuspendSignalTest.java`, `OrchestrationSpiArchTest.java` (agent-service), `SuspendSignalLibraryTest.java` (agent-execution-engine). These must be relocated, or the regenerated fact layer (F5/P0-1) will still report the old package.
3. **P1-4 is much wider than cited** (200 sites vs ~5): 150 are auto-generated `gate/rules/rule-*.sh` mirrors emitted from a single line in `gate/lib/extract_rules.sh:66` (fix the emitter + regenerate, do not hand-edit mirrors); `architecture/decisions/0147.md`/`0148.md` are ADR mirrors (fix the source YAML); the active-authority set spans `architecture/workspace.dsl`, `CLAUDE.md`, and ADRs 0055–0059/0071/0073/0086/0120/0156. References use **both** `\` and `/` separators (ADRs 0071/0073/0086 forward-slash; ADR-0156 backslash), so any ban gate must match both.
4. **Baseline lockstep is wider than the two flagged keys.** Besides `workspace_elements`/`workspace_relationships` (608/469 → live 609/478), the README baseline phrase, the `allowed_claim` "575 elements + 420 relationships" prose, the enforcer-row count (179 → 185), and the family count (35 → 38) are stale and must advance in the same wave.

## Verification (to be green at close)

```
./mvnw clean verify
./mvnw -f tools/architecture-workspace/pom.xml exec:java@extract-facts -Dexec.args='--repo . --out architecture/facts/generated --check'
bash gate/check_architecture_sync.sh
rg 'engine\.orchestration\.spi|engine/orchestration/spi' -g '!docs/logs/**' -g '!docs/adr/**' -g '!docs/governance/rule-history.md' -g '!docs/archive/**'
rg 'D:[\\/]\.claude[\\/]plans' product docs/adr docs/governance architecture
rg -Pn '[\x{4e00}-\x{9fff}]' product/PRODUCT.md product/claims.yaml product/personas.yaml product/journey.md
```

Plus EnginePort acceptance tests: engine-facing context has no `tenantId()`/`sessionId()`; boundary dispatch uses `DefinitionRef`, not an inline lambda; no signature regression to the old `Object execute(RunContext, ExecutorDefinition, Object)`; and the three transports (`in_process`, `internal_rpc` mock, `a2a` mock) pass one identical conformance TCK with terminal events for success / failure / interrupt.
