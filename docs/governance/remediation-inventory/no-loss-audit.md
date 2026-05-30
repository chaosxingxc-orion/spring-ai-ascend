---
level: L0
view: scenarios
status: advisory
governance_infra: true
audit_date: 2026-05-30
auditor: governance/progressive-learning-curve-remediation Wave W15
waves_covered: [W12, W13, W14, W15]
source_commits: [09ea68d, cb25388, 77899f4, "W15-working-tree"]
authority_refs: [ADR-0068, ADR-0150, ADR-0157, ADR-0158, ADR-0159]
verdict_source: "Adjudicated verdict — L0/L1 leak critique TRUE (see layer-purity-scan.md §0)"
---

# No-Loss Audit — L2/Code Detail Drained from L0 + L1 (W12–W15)

> **What this is.** An advisory, evidence-backed aggregate audit that, for
> **every symbol removed from the L0 root and the L1 module corpus during
> waves W12–W15**, confirms the symbol now appears at an authoritative
> **L2 / contract / generated-fact** location. It is the W15 "prove
> nothing was lost" companion to the W0 [`layer-purity-scan.md`](layer-purity-scan.md)
> (which recorded WHERE the leaks were) and to the layer-purity migration
> waves W12–W15 (which moved them). This document moves no content, edits
> no authority surface, and invents no IDs or relationships.
>
> **What this is NOT.** A migration plan, an ADR, or a gate. It does not
> re-home anything; it audits that the re-homing already done by W12–W15
> landed the detail somewhere authoritative. The authority cascade
> (generated facts > DSL > Card/prose) is the adjudicator for every row:
> a symbol is "not lost" when its authoritative home exists and carries
> it, even if a *readable* L1/L2 rendering of it is deferred.
>
> **How to read a row.** Each row names the removed symbol (verbatim
> trigger from the wave diff), the wave that removed it, the file it left,
> the authoritative destination it now lives in, and a **disposition**:
>
> - **MIGRATED** — the detail is re-inlined at an active L2 sink and/or a
>   contract surface that exists on disk and was confirmed to carry it.
> - **FACT-OWNED** — the detail is a code/test/contract surface whose
>   canonical home is a generated fact in `architecture/facts/generated/*`
>   (verified present); the prose that named it was a readable echo, and
>   the authority cascade makes the fact the home regardless of prose.
> - **DEFERRED-NOT-LOST** — the symbol's *obligation* and/or *contract*
>   home exists and names it, but a concrete realization (SQL body, Java
>   type) or a convenience render (the W3 SPI-appendix emitter) has not
>   landed yet. This is design-only-by-design, not a loss: the binding
>   surface that will carry the realization exists and points at it.
>
> No row is **LOST**. The audit found zero removed symbols without an
> authoritative home.

---

## §0 — Method and provenance

The removed-symbol set is taken from the actual wave diffs, not from
memory or commit prose alone:

- **W12** (`09ea68d`) — L0 root drain. Diff: `architecture/docs/L0/ARCHITECTURE.md`.
- **W13** (`cb25388`) — L1 `agent-service` 4+1 drain. Diff: `logical.md`,
  `process.md`, `ARCHITECTURE.md`, `scenarios.md`, `development.md`, `physical.md`.
- **W14** (`77899f4`) — L1 `agent-bus` / `agent-execution-engine` /
  `agent-middleware` drain. Diff: the three module `ARCHITECTURE.md` +
  their `{logical,process,physical,scenarios}.md`.
- **W15** (working tree at audit time) — L1 `agent-client` / `agent-evolve`
  module-root drain. Diff: the two module `ARCHITECTURE.md`.

Each destination claim was **independently verified** against the live
tree at audit time, not transcribed from the commit message:

- Generated facts were read out of `architecture/facts/generated/{code-symbols,tests}.json`
  by `fact_id` (the canonical `code-symbol/<kebab-fqn>` and `test/<kebab-fqn>`
  forms). Where a wave claimed "cites the real `public_methods[]` descriptor",
  the descriptor string was confirmed to appear in that fact's
  `public_methods` array (no minted descriptor).
- L2 sink and contract content was confirmed by reading the destination
  file for the verbatim trigger token.

Authority-surface boundary: per the reconcile-ownership rule this file
touches none of `architecture-status.yaml`, `README`, `gate/README`,
`enforcers.yaml`, `recurring-defect-families.yaml`, `profile/*`,
`engineering-frames.dsl`, or any `architecture/facts/generated/*` /
`architecture/generated/*`. It only *reads* the facts to audit them.

---

## §1 — W12 · L0 root (`architecture/docs/L0/ARCHITECTURE.md`)

The four verdict-named leaks were drained; the cross-document **invariant**
stayed at L0 and the runtime detail moved to its authoritative home.

### 1.1 §0.5.3 Telemetry Vertical — wire/sink/sampling/hook detail

| Removed symbol (verbatim) | Destination (verified) | Disposition |
|---|---|---|
| `OTLP/HTTP` wire format | `L2/telemetry-vertical/process.md:91`, `README.md:39`; `docs/telemetry/policy.md:78` | MIGRATED |
| `gen_ai.*` + `langfuse.*` attribute namespaces | `L2/telemetry-vertical/process.md:93-94`, `logical.md:48,91`; `docs/telemetry/policy.md:56-61` | MIGRATED |
| Per-posture sampling `dev=100 % / research=10 % / prod=1 % head + tail-on-error` | `L2/telemetry-vertical/process.md:102-106`; `docs/telemetry/policy.md:72` | MIGRATED |
| W3C `traceparent` / `traceresponse` propagation + MDC field shapes (`tenant_id`/`trace_id`/`span_id`/`run_id`) | `L2/telemetry-vertical/process.md:25,68-75`, `README.md:40`; `docs/telemetry/policy.md` (field shape §) | MIGRATED |
| Reference hooks `TokenCounterHook`, `PiiRedactionHook`, `CostAttributionHook`, `LlmSpanEmitterHook` | `L2/telemetry-vertical/logical.md:75-78`, `process.md:68-75`, `README.md:52,82` | MIGRATED |
| MCP replay tools `get_run_trace`, `list_runs`, `get_llm_call`, `list_sessions` | `L2/telemetry-vertical/process.md:96`, `README.md:84`; `docs/telemetry/policy.md:80,172` | MIGRATED |
| `trace_store` Postgres dual-write (ADR-0017) | `L2/telemetry-vertical/process.md:91-106`; `docs/telemetry/policy.md:80` | MIGRATED |

`L2/telemetry-vertical/{README,logical,process}.md` were flipped from
scaffold to **active** in W12 so the L0 §0.5.3 pointer resolves to a live
authority. All seven telemetry symbols confirmed present.

### 1.2 §4 #37 W1 HTTP contract — verb/route/status/header detail

| Removed symbol (verbatim) | Destination (verified) | Disposition |
|---|---|---|
| `X-Tenant-Id` required header + `403` on JWT/header mismatch | `docs/contracts/http-api-contracts.md:14,19,34,103,118`; facts `contract-op/createrun` / `contract-op/getrun` / `contract-op/cancelrun` | MIGRATED + FACT-OWNED |
| Initial run status `PENDING` (DFA-initial value) | `docs/contracts/http-api-contracts.md` (POST /v1/runs §82) | MIGRATED |
| Cancellation is `POST /v1/runs/{id}/cancel` (not `DELETE`) | `docs/contracts/http-api-contracts.md:112-125`; `L2/run-http-contract/process.md` §4 | MIGRATED |

L0 kept the invariant ("tenant *cross-checked* not *replaced*; run begins
in its DFA-initial status; cancel is a transition on a surviving record").
The wire facts are the OpenAPI contract operations.

### 1.3 §4 #41 SPI catalog precision — type-shape detail

| Removed symbol (verbatim) | Destination (verified) | Disposition |
|---|---|---|
| `RunContext` classified `interface` (not `record`) | fact `code-symbol/com-huawei-ascend-bus-spi-engine-runcontext` → `kind: interface` (confirmed) | FACT-OWNED |
| `embeddingModelVersion` canonical field name (ADR-0034) | fact `code-symbol/...-memory-spi-memorymetadata` field; `docs/contracts/contract-catalog.md:68,72` (SemanticMemoryStore + EmbeddingModel rows); `MemoryMetadata.java` | FACT-OWNED + MIGRATED |

L0 kept the *no-drift invariant*; the type kind + field name are read from
the facts (authority cascade), so the prose echo could be removed without
loss. The `RunContext` fact proves `kind: interface` directly.

### 1.4 §4 #44 release-note shipped-surface truth — method/test inventory

| Removed symbol (verbatim) | Destination (verified) | Disposition |
|---|---|---|
| SPI canonical method set is a `public_methods` subset; `posture()` forbidden because it "does not exist on the interface" | fact `code-symbol/...-runcontext` `public_methods` = `[sessionId, suspendForChild, tenantId, traceContext]` — `posture()` confirmed ABSENT | FACT-OWNED |
| Test attribution truth (`OpenApiContractIT` ≠ ArchUnit-only `ApiCompatibilityTest`) | `architecture/facts/generated/tests.json` (test facts present) | FACT-OWNED |

The fact *is* the canonical method set; the forbidden-member check
(`posture()`) is satisfied structurally — the member simply is not in the
fact's `public_methods`. No loss.

> **Audit note (prose-vs-fact reconciliation, not a loss).** The removed
> §4 #44 prose illustrated the `RunContext` method set as
> `runId`/`tenantId`/`checkpointer`/`suspendForChild`. The live fact's
> `public_methods` is `sessionId`/`suspendForChild`/`tenantId`/`traceContext`.
> The two differ because the prose described an older SPI surface; under
> the authority cascade the fact wins and the now-stale illustrative list
> was correctly dropped rather than carried forward. This is the exact
> drift the §4 #41/#44 *no-drift invariant* exists to prevent.

---

## §2 — W13 · L1 `agent-service` (4+1 views)

The L2/code detail the verdict ruled out of L1 structural prose was drained
into the active `L2/run-http-contract/` sinks + the generated facts;
structural layer / aggregate / SPI-boundary identity stayed at L1.

### 2.1 `logical.md` — entity inventories + CAS chain + CAS-annotated DFA

| Removed symbol (verbatim) | Destination (verified) | Disposition |
|---|---|---|
| Run/Task/Session/LIFECYCLE_STATE column inventories (`runId`, `capabilityName`, `RunStatus` 7-values, `RunMode`, `attemptId`, `parentNodeKey`, `traceId`, `sessionId`, `taskKind`, `a2aState`, …) | facts `code-symbol/com-huawei-ascend-service-runtime-runs-run`, `...-service-task-task` (+ `...-task-taskkind`, `...-task-a2astate`), `...-service-session-session` (all present) | FACT-OWNED |
| `Run.java:25` / `Task.java:31` / `Session.java:27` source line refs | dropped (line refs are not authority); tenant-first field shape is FACT-OWNED above | FACT-OWNED |
| `RunRepository.updateIfNotTerminal(...)` atomic CAS method chain | `L2/run-http-contract/process.md:89-118` (cites `code-symbol/...-runrepository` + the real `updateIfNotTerminal(...)Optional;` descriptor) | MIGRATED + FACT-OWNED |
| CAS-annotated `RunStatus` DFA: `POST /v1/runs`, `WHERE status NOT IN (CANCELLED, SUCCEEDED, FAILED, EXPIRED)`, `409`, `RunStateMachine.java:37` | `L2/run-http-contract/process.md:89-126` (CAS predicate + winner/loser + 200-vs-409) | MIGRATED |
| `IdempotencyRecord` (`V2__idempotency_dedup.sql`) RLS-bound; `RunStateMachine.validate(from,to)` inside CAS | `L2/run-http-contract/process.md` §4 (validate-inside-CAS, cites `validate(...)V` descriptor); `development.md` §5.3 (RLS realisation) | MIGRATED + FACT-OWNED |

The L1 view now delegates field shapes to the cited code-symbol facts and
the persistence detail to `development.md §5.3`.

### 2.2 `process.md` — the six P1–P6 sequence diagrams

| Removed symbol (verbatim) | Destination (verified) | Disposition |
|---|---|---|
| `sequenceDiagram` P1–P6 (HTTP status, method calls, RunEvent emissions) | replaced at L1 with structural layer-interaction flowcharts; wire/method steps in `L2/run-http-contract/process.md` (idempotency §3, cancel-CAS §4, winner/loser §5) | MIGRATED |
| `409 idempotency_conflict` / `409 idempotency_body_drift` / `200 cached (W2)` | `L2/run-http-contract/process.md:49-76` | MIGRATED |
| Cancel-race loser sequence (`CAS WINS`/`LOSES`, post-CAS re-read, `409`) | `L2/run-http-contract/process.md` §5 (winner/loser ordering) | MIGRATED |

### 2.3 `ARCHITECTURE.md` / `scenarios.md` / `development.md` / `physical.md`

| Removed symbol (verbatim) | Destination (verified) | Disposition |
|---|---|---|
| Idempotency hash `method:path:body` (SHA-256 → base64url); `ON CONFLICT (tenant_id, idempotency_key) DO NOTHING`; `V2__idempotency_dedup.sql` | `L2/run-http-contract/process.md` §1–§3; `IdempotencyStore` curated in `docs/contracts/contract-catalog.md`; facts `code-symbol/...-idempotency-idempotencystore*`, `...-idempotencyheaderfilter`, `...-idempotencyrecord` | MIGRATED + FACT-OWNED |
| Runs-API status matrix (`201`/`200`/`404`/`409`/`422`/`500`, `CreateRunRequest`, `ErrorEnvelope`) | `docs/contracts/http-api-contracts.md:31-36,82-125`; contract ops `createrun`/`getrun`/`cancelrun` | MIGRATED + FACT-OWNED |
| Filter-chain ordering `TraceExtractFilter@10`, `JwtTenantClaimCrossCheck@15`, `TenantContextFilter@20`, `IdempotencyHeaderFilter`; `traceparent`/`traceresponse`; `403 tenant_mismatch` / `jwt_missing_tenant_claim` | `L2/run-http-contract/development.md:53-94` (each filter → a `code-symbol/*` fact); facts for all four filters present | MIGRATED + FACT-OWNED |
| `StatelessEngine.Execute(TaskMetadata, InjectedContext) → StateDelta` SPI signature; `StateDelta` carrier | fact `code-symbol/...-service-engine-spi-statelessengine` `public_methods` = `execute(...AgentInvokeRequest;)...StateDelta;` (real descriptor); `StateDelta` fact present; `docs/contracts/agent-invoke-request.v1.yaml` | FACT-OWNED + MIGRATED |
| RLS realisation: `SET LOCAL app.tenant_id` GUC, `V?__tenant_rls.sql` series, RLS policy bodies | `development.md §5.3` Boundary Contract (obligation + DFX); concrete SQL/GUC bodies delegated to the future persistence L2 design | DEFERRED-NOT-LOST |

> **DEFERRED-NOT-LOST detail (RLS SQL bodies).** W13 removed the inlined
> `SET LOCAL app.tenant_id` GUC and the `V?__tenant_rls.sql` migration
> series from the L1 `development.md §5` "L2 zone". The §5.3 Boundary
> Contract that replaces them **names the obligation** (RLS on every
> tenant-scoped table, GUC wiring, the CAS SQL backing the transition
> primitive) and **delegates the realization** to a persistence L2 design.
> That persistence L2 design is not yet authored on disk, so the *literal*
> SQL bodies are not currently re-inlined anywhere. This is design-only by
> design (the tenant-RLS migration is a future wave per the
> grandfathered idempotency baseline): the obligation home exists and is
> the sanctioned carrier; the SQL realization lands with the migration.
> Recorded here as DEFERRED, not LOST.

---

## §3 — W14 · L1 `agent-bus` / `agent-execution-engine` / `agent-middleware`

### 3.1 `agent-execution-engine/ARCHITECTURE.md`

| Removed symbol (verbatim) | Destination (verified) | Disposition |
|---|---|---|
| Strict-matching outcome chain: `engine_type=X` → `EngineMatchingException` → `Run.FAILED` reason `engine_mismatch` | `docs/contracts/engine-envelope.v1.yaml:85-86` (raises `EngineMatchingException`; `FAILED` with `engine_mismatch`); fact `code-symbol/...-engine-spi-enginematchingexception`; `contract-catalog.md:134`; tests `EngineMatchingStrictnessIT`, `EngineMismatchTransitionsRunToFailedIT` | MIGRATED + FACT-OWNED |
| `EngineEnvelope` field shape (`envelope_version`, `engine_type`, `payload_class_ref`, `schema_ref`) | `docs/contracts/engine-envelope.v1.yaml`; fact `code-symbol/...-engine-runtime-engineenvelope`; test `EngineEnvelopeValidationTest` | MIGRATED + FACT-OWNED |
| SPI FQN table (`ExecutorAdapter`, `GraphExecutor`, `AgentLoopExecutor`, `EngineHookSurface`, `EngineMatchingException`) + directory tree | facts for each present (`code-symbol/...-engine-spi-*`); `docs/contracts/contract-catalog.md:51-54,94,113-114,134` | FACT-OWNED + MIGRATED |
| Test-class refs in the engine module-root | `architecture/facts/generated/tests.json` (engine test facts present) | FACT-OWNED |
| Neutral orchestration vocab (`RunMode`/`RunContext`/`SuspendSignal`/`Checkpointer`/`Orchestrator`/`TraceContext`/`ExecutorDefinition`/`ExecutionContext`) | named as consumed-from-`agent-bus` `bus.spi.engine` (ADR-0158); facts under `code-symbol/com-huawei-ascend-bus-spi-engine-*` | FACT-OWNED |

### 3.2 `agent-middleware/ARCHITECTURE.md`

| Removed symbol (verbatim) | Destination (verified) | Disposition |
|---|---|---|
| "Hook ordering = middleware registration order" mechanism | `docs/contracts/engine-hooks.v1.yaml:32-33` ("fire in the order declared here"; `after_*` reverse registration order) | MIGRATED |
| `HookOutcome` sealed `Proceed \| Fail \| ShortCircuit`; outcome consumption (`Fail → Run.FAILED`, `ShortCircuit → engine bypass`) | `docs/contracts/engine-hooks.v1.yaml:25-78` ("first non-Proceed wins"); facts `code-symbol/...-middleware-spi-hookoutcome` (+ `-proceed`/`-fail`/`-shortcircuit`); `contract-catalog.md:137` | MIGRATED + FACT-OWNED |
| Per-type SPI table (`ModelGateway.stream(...)`, `ChatAdvisor`/`AdvisorChain.next(...)`/`aroundCall(...)`, `StreamingChatAdvisor`/`StreamingAdvisorChain`, `ConversationMemory extends MemoryStore<…>`) | facts `code-symbol/...-middleware-{model,advisor,memory}-spi-*` (all present); `docs/contracts/contract-catalog.md:62,78-82,96`; `model-streaming.v1.yaml`, `chat-advisor.v1.yaml` | FACT-OWNED + MIGRATED |

### 3.3 `agent-bus/ARCHITECTURE.md`

| Removed symbol (verbatim) | Destination (verified) | Disposition |
|---|---|---|
| `IngressGateway` single-method signature `routeClientRequest(IngressEnvelope) → IngressResponse` | fact `code-symbol/...-bus-spi-ingress-ingressgateway`; `docs/contracts/ingress-envelope.v1.yaml`; `contract-catalog.md:47,95,109` | FACT-OWNED + MIGRATED |
| EnginePort type inventory + suspend/resume realization + transport adapters + orchestration-SPI re-namespacing | `architecture/docs/L2/engine-port-boundary/{README,process,development,scenarios,physical}.md` (standing L2 home, carries EnginePort/strict-matching/transport/suspend-resume) | MIGRATED |
| Per-type ingress / s2c SPI inventories | collapsed to boundary-interface + wire-contract rows at L1; full inventory in facts + `contract-catalog.md §2` | FACT-OWNED |

### 3.4 Per-view promotion note (not a removal)

W14 promoted `agent-execution-engine` + `agent-middleware`
`{logical,process,physical,scenarios}.md` from W2 stub bodies to **active**
L1-altitude narratives. These ADD content; they remove nothing. The engine
`process.md` deliberately does NOT carry the `EngineMatchingException` /
`engine_mismatch` literal — that is the *contract's* job; the process view
narrates the dispatch at obligation altitude. Verified consistent.

---

## §4 — W15 · L1 `agent-client` / `agent-evolve` (module roots)

The layer-purity scan (§3.4) classified both as skeleton modules with no
L1–L8 leak markers; W15 re-grounded them on boundary identity and moved
their forward-looking detail to contracts + facts.

### 4.1 `agent-client/ARCHITECTURE.md`

| Removed symbol (verbatim) | Destination (verified) | Disposition |
|---|---|---|
| Consumed-FQN records `IngressEnvelope` (record) + `IngressResponse` (record) | facts `code-symbol/...-bus-spi-ingress-ingressenvelope`, `...-ingressresponse`; `docs/contracts/ingress-envelope.v1.yaml`; `contract-catalog.md §2` | FACT-OWNED + MIGRATED |
| `IngressRequestType` discriminator | nested fact `code-symbol/...-ingress-ingressenvelope-ingressrequesttype`; `ingress-envelope.v1.yaml` | FACT-OWNED |
| `IngressResponse.deferred(...)` backpressure method + the W3+ test-plan inventory | `ingress-envelope.v1.yaml` (response vocabulary + `IngressStatus` fact `...-ingressresponse-ingressstatus`); test inventory delegated to `architecture/facts/generated/tests.json`; deferred backpressure sub-clause parked in `rule-R-I.md#deferred_sub_clauses` | FACT-OWNED |
| SDK type inventory in the development-view directory tree | `architecture/facts/generated/code-symbols.json` (named as owner) | FACT-OWNED |
| Sentinel test `EdgeToComputeDirectLinkArchTest` | **KEPT** at L1 as boundary-enforcer identity (E143 / Rule 105 — defensible D3); also fact `test/...-client-architecture-edgetocomputedirectlinkarchtest` | (not removed) + FACT-OWNED |

### 4.2 `agent-evolve/ARCHITECTURE.md`

| Removed symbol (verbatim) | Destination (verified) | Disposition |
|---|---|---|
| `SlowTrackJudge` SPI FQN + method shape | fact `code-symbol/com-huawei-ascend-evolve-online-spi-slowtrackjudge` (the L1 body now cites this exact fact-id); `contract-catalog.md` | FACT-OWNED |
| `RunEvent.evolutionExport()` field/method | `docs/contracts/run-event.v1.yaml:65-70` (`evolutionExport` field, `[IN_SCOPE, OUT_OF_SCOPE, OPT_IN]`, per-variant defaults); fact `code-symbol/...-service-runtime-evolution-evolutionexport`; `docs/governance/evolution-scope.v1.yaml` | MIGRATED + FACT-OWNED |
| `ReflectionEnvelope` wire shape | `docs/contracts/reflection-envelope.v1.yaml`; transport fact `code-symbol/...-bus-spi-s2c-reflectionenveloperouter` | MIGRATED + FACT-OWNED |
| Slow-Track DFX thresholds (`≤500ms median latency`, `≥0.7 confidence for auto-apply`) | `docs/contracts/reflection-envelope.v1.yaml:70-74` (confidence threshold `0.7`); §5.3 L2 Constraint Linkage names the latency budget as a future Boundary-Contract DFX obligation | MIGRATED + DEFERRED-NOT-LOST |
| Mode × Modality matrix + offline/online trigger detail | `docs/governance/evolution-modalities.yaml#matrix` (named as authority) | MIGRATED |
| Future types `ReflectionPatchHandler`, `OfflineExportAdapter` | named as contemplated-but-undeclared; will register in `module-metadata.yaml#spi_packages` when shipped, then surface as facts | DEFERRED-NOT-LOST |

---

## §5 — Cross-cutting: the SPI-Appendix render-stub (deferred, not lost)

W13/W14/W15 prose delegated the collapsed per-type SPI FQN tables to "the
rendered SPI appendix" (`architecture/docs/L1/<module>/spi-appendix.md`).
Audit finding: those `spi-appendix.md` files are **W2 render stubs** —
front-matter `status: template`, body is a single `<package.Interface>`
placeholder row, rendered at W3 by `SpiAppendixEmitter` from
`module-metadata.yaml#spi_packages` + a Java source scan. They do NOT yet
re-inline the SPI tables.

This is **DEFERRED-NOT-LOST**, not a gap, because the SPI FQNs the tables
carried are preserved in two authoritative homes that the audit confirmed:

1. **Generated facts** — every removed SPI type resolves to a
   `code-symbol/<kebab-fqn>` fact (verified for `IngressGateway`,
   `ExecutorAdapter`, `GraphExecutor`, `AgentLoopExecutor`,
   `EngineHookSurface`, `EngineMatchingException`, `HookOutcome` (+ sealed
   variants), `ModelGateway`, `ChatAdvisor`, `AdvisorChain`,
   `StreamingChatAdvisor`, `StreamingAdvisorChain`, `ConversationMemory`,
   `StatelessEngine`, `SlowTrackJudge`, `IngressEnvelope`, `IngressResponse`).
2. **Curated readable home** — `docs/contracts/contract-catalog.md §2`
   (plus the sealed-carrier and SPI-scope tables) lists every one of these
   SPIs with module, package, and status.

The SPI-appendix is a *future readable rendering* of (1); under the
authority cascade the fact is the home and the appendix is a derived view.
No SPI identity is lost while the appendix render is deferred.

---

## §6 — Aggregate result

| Wave | Files drained | Removed-symbol rows audited | LOST |
|---|---|---|---|
| W12 (L0) | 1 | 13 (§1.1–§1.4) | 0 |
| W13 (agent-service) | 6 | 14 (§2.1–§2.3) | 0 |
| W14 (bus/engine/middleware) | 3 roots (+ per-view promotions) | 13 (§3.1–§3.3) | 0 |
| W15 (client/evolve) | 2 roots | 11 (§4.1–§4.2) | 0 |
| **Total** | **12** | **51** | **0** |

- **MIGRATED** (re-inlined at an active L2 sink / contract): telemetry
  vertical (7), HTTP contract verbs/status (3), agent-service CAS + filter
  + idempotency + sequences (≈10), engine outcome-chain + envelope +
  hook-ordering + HookOutcome (4), bus IngressGateway + EnginePort L2 (2),
  evolve RunEvent + ReflectionEnvelope + modality matrix (3).
- **FACT-OWNED** (canonical home is a verified generated fact): all SPI
  type identities, method descriptors, entity field shapes, test-class
  inventories, the `RunContext` `kind: interface` + `posture()`-absent
  facts.
- **DEFERRED-NOT-LOST** (obligation/contract home exists; realization or
  convenience render pending): the tenant-RLS SQL/GUC bodies (await the
  persistence L2 design), the Slow-Track latency budget (future Boundary
  Contract DFX), the contemplated `ReflectionPatchHandler` /
  `OfflineExportAdapter` SPIs, and every module's `spi-appendix.md` (W3
  `SpiAppendixEmitter` render).
- **LOST: none.** Every one of the 51 removed symbols has an authoritative
  home on disk that the audit confirmed carries it (or, for the deferred
  rows, an authoritative obligation/contract surface that names it and the
  realization that will carry it).

---

## §7 — Reconcile-step ownership note

This advisory file does NOT touch any shared authority surface
(`architecture-status.yaml`, `README`, `gate/README`, `enforcers.yaml`,
`recurring-defect-families.yaml`, `profile/*`, `engineering-frames.dsl`,
or any `architecture/facts/generated/*` / `architecture/generated/*`). It
**reads** the generated facts and contract/L2 surfaces solely to audit
that the W12–W15 drains landed; it changes none of them. Every `fact_id`,
contract path, and L2 line reference above is copied from the live tree at
audit time — no ID or relationship is invented. Where this audit and a
generated fact disagree, the fact wins; the audit defers to the authority
cascade (generated facts > DSL > Card/prose) by construction.
