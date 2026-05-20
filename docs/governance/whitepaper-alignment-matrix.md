# Whitepaper Alignment Matrix

> Last updated: 2026-05-20 (rc15 — added Ledger Dim 1-4 rows per ADR-0092)
>
> **Purpose**: concept-level traceability from `docs/spring-ai-ascend-architecture-whitepaper-en.md` to active architecture artifacts. Per ADR-0049 + Gate Rule 29, every major whitepaper concept MUST appear here with an explicit status, wave, owner side, and gate-coverage marker. Any release note claiming "whitepaper alignment" MUST reference this matrix.
>
> **Status vocabulary**:
> - `shipped` — fully implemented in code at the named wave; tests/gates verify it.
> - `active-design` — named at architecture-contract level (ADR + §4 entry); implementation deferred to a future wave.
> - `deferred` — explicitly deferred with documented re-introduction trigger.
> - `archived` — captured in `docs/archive/` as future direction; not on near-term path.
> - `rejected` — explicitly considered and not adopted.
>
> **Owner side**: `C-side` (business application), `S-side` (platform runtime), `shared` (explicit delegation), `external` (third-party / middleware), `N/A`.

| Concept | Whitepaper line / section | Current artifact | Status | Wave | Owner side | Gate coverage | Notes |
|---|---|---|---|---|---|---|---|
| **C/S separation** | §2.1 (`docs/spring-ai-ascend-architecture-whitepaper-en.md:44`) | ADR-0049; ARCHITECTURE.md §4 #47 | active-design | L0 contract / W2 impl | shared | Yes — Gate Rule 29 verifies matrix row; ADR-0049 prose review | Wire bindings deferred W2; `agent-client-sdk` Maven module deferred W3+ |
| **Task Cursor** | §2.1 (`:48`), §2.3 (`:64`-`:68`) | ADR-0049 `TaskCursor` contract | active-design | L0 contract / W2 impl | C-side | Yes — Gate Rule 29 | Opaque to S-side except routing/lease/resume fields |
| **Dynamic Hydration** | §2.3 (`:64`-`:68`) | ADR-0049 `HydrationRequest` + `HydratedRunContext` | active-design | L0 contract / W2 impl | shared (request from C-side; hydration on S-side) | Yes — Gate Rule 29 | `RunContext` is the internal S-side state, NOT the C/S protocol |
| **Sync State** | §2.3 (`:73`) | ADR-0049 `SyncStateResponse` | active-design | L0 contract / W2 impl | S-side → C-side | Yes — Gate Rule 29 | Cursor advancement mode |
| **Sub-Stream** | §2.3 (`:74`) | ADR-0049 `SubStreamFrame` | active-design | L0 contract / W2 impl | S-side → C-side | Yes — Gate Rule 29 | Pass-through interaction mode; C-side renders, does not persist |
| **Yield & Handoff** | §2.3 (`:75`) | ADR-0049 `YieldResponse` + `ResumeEnvelope`; `SuspendSignal` is one internal cause | active-design | L0 contract / W0 partial (`SuspendSignal` shipped; full taxonomy deferred per §4 #19) | S-side → C-side | Yes — Gate Rule 29 | Permission suspension mode; composes with sealed `SuspendReason` |
| **Business ontology ownership** | §2.4 (`:81`) | ADR-0051 ownership table (C-side default); `BusinessFactEvent`, `OntologyUpdateCandidate` | active-design | L0 contract / W2 impl | C-side | Yes — Gate Rule 29; ADR-0034 forward note | M3/M4/M5 split into platform-derived vs business-owned per ADR-0051 |
| **S-side execution trajectory ownership** | §2.4 (`:82`) | ADR-0051; `RunRepository`, `Checkpointer`, `Run` entity (W0 shipped) | shipped (S-side trajectory persistence at SPI level) | L0 contract / W0 in-memory; W2 Postgres | S-side | Yes — ArchUnit `OrchestrationSpiArchTest`, `RunStateMachineTest`; Gate Rule 29 | Token usage, model version telemetry, retry/failure diagnostics |
| **Placeholder exemption** | §2.4 (`:83`) | ADR-0051 `PlaceholderPreservationPolicy` + `SymbolicReturnEnvelope` | active-design | L0 contract / W3 enforcement | shared (rule applies to S-side; placeholders defined by C-side) | Yes — Gate Rule 29; ship-blocking per ADR-0051 | Ship-blocking violation for any tool/skill/LLM that resolves placeholders without delegation |
| **Full Trace vs Node Snapshot** | §3.1 | ARCHITECTURE.md §4 #9 (dual-mode runtime — `GraphExecutor` Node Snapshot vs `AgentLoopExecutor` Full Trace) | shipped (reference impl) | W0 ref / W2 full | S-side | Yes — `OrchestrationSpiArchTest`; `NestedDualModeIT`; Gate Rule 29 | Heterogeneous nesting via `SuspendSignal` proven |
| **Lazy mounting / bypass context store** | §3.3 | ARCHITECTURE.md §4 #13 `PayloadStore` SPI; `InMemoryCheckpointer.MAX_INLINE_PAYLOAD_BYTES` | active-design (cap shipped) | W0 in-memory cap / W2 `PayloadStore` impl | S-side | Yes — `InMemoryCheckpointerSizeCapTest`; Gate Rule 29 | 16 KiB inline cap enforced; W2 adds content-addressed bypass store |
| **Skill Topology Scheduler** | §4.1 | ADR-0052; ARCHITECTURE.md §4 #50 | active-design | L0 contract / W2 impl | S-side | Yes — Gate Rule 29 | Two-axis arbitration (tenant quota × global skill capacity); `SkillResourceMatrix` extended from ADR-0030/0038 |
| **C-side business degradation authority** | §4.2 | ADR-0049 degradation-authority section (`ComputeCompensation`, `BusinessDegradationRequest`, `GoalMutationProhibition`) | active-design | L0 contract / W2 enforcement | C-side authority; S-side classifies | Yes — Gate Rule 29 | S-side may substitute compute methods; cannot mutate task goals; business degradation requires C-side decision |
| **Session/context decoupling** | §4.3 | ARCHITECTURE.md §4 #9 (Run lifecycle decoupled from HTTP request); §4 #10 (`AgentSubject` deferred W2) | active-design (partial) | W0 Run decoupling / W2 `AgentSubject` | S-side | Yes — `RunStatusTransitionIT`; Gate Rule 29 | Long-horizon `AgentSubject` identity deferred per §4 #10 |
| **Workflow Intermediary** | §5.1 (`:174`-`:179`) | ADR-0050 `WorkflowIntermediary` + `Mailbox` + `AdmissionDecision`; ARCHITECTURE.md §4 #48 | active-design | L0 contract / W2 impl | S-side per-instance | Yes — Gate Rule 29 | Bus MUST NOT force-start computation; local intermediary owns admission |
| **Three-track bus** | §5.2 (`:188`-`:195`) | ADR-0050 (cross-service three tracks); ADR-0031 (in-process); ADR-0048 (amended for Rhythm) | active-design | L0 contract / W2 impl | S-side | Yes — Gate Rule 29 | Track 1 Control (event bus); Track 2 Data (P2P); Track 3 Rhythm (independent) — restored per P0-4 |
| **Capability bidding** | §5.3 (`:198`-`:204`) | ADR-0052 `BidRequest`, `BidResponse`, `CapabilityRegistry` with pre-authorization | active-design | L0 contract / W2 impl | S-side | Yes — Gate Rule 29 | Only pre-authorized delegates may bid; bid-scoring rule defined |
| **Permission issuance** | §5.3 (`:206`) | ADR-0052 `PermissionEnvelope` (subsumption boundary, short expiry, signature) | active-design | L0 contract / W2-W3 impl | S-side issuer; delegate consumer | Yes — Gate Rule 29 | Cascading issuance bounded by Skill Subsumption Principle |
| **Chronos Hydration** | §5.4 (`:217`-`:218`) | ADR-0050 `SleepDeclaration` + `WakeupPulse` + `TickEngine` + `ChronosHydration` flow | active-design | L0 contract / W4 impl | S-side | Yes — Gate Rule 29 | End-to-end flow: sleep → self-destruct → wakeup → rehydrate; long-horizon dimensionally reduced to instantaneous pull-ups |
| **Service Layer microservice commitment** | (whitepaper §1.3 rejects per-agent microservice; ADR-0048 commits Service-Layer microservice) | ADR-0048 + ARCHITECTURE.md §4 #46 | shipped (commitment at L0; deployment topology) | L0 / ongoing | S-side | Yes — Gate Rule 26 (release-note shipped-surface truth); Gate Rule 27 (README baseline); Gate Rule 28 (release-note baseline); Gate Rule 29 (matrix presence) | Long-running JVM microservices; bus traffic split data-P2P / control-event-bus; amended by ADR-0050 to add Rhythm track |

## Ledger dimensions (per `docs/logs/reviews/2026-05-20-spring-ai-ascend-ultimate-architecture-ledger.md` + ADR-0092)

The Ultimate Architecture Ledger introduces a 4-dimensional × 3-phase trajectory. Each dimension is anchored below. **Phase-3 OS/hardware items are declared out-of-scope for `spring-ai-ascend` L0 authority per ADR-0092 and belong to a sibling Agent-OS / openEuler / Kunpeng / NPU-driver epic.**

| Concept | Ledger section | Current artifact | Status | Wave | Owner side | Gate coverage | Notes |
|---|---|---|---|---|---|---|---|
| **Ledger Dim 1 — Business-System Separation** | Dim 1 (Phase 1-3) | ADR-0049 (C/S Cursor); ADR-0070 (ResilienceContract); ADR-0088 (post-runtime-core tenant propagation); ADR-0092 (scope boundary) | architected (Phase 1) / partial (Phase 2) / **archived: out-of-scope (Phase 3)** | L0 / W2 / **out-of-scope** | shared (C/S); platform (resilience); **Agent-OS (eBPF/DMA/KAE)** | Yes — Gate Rule 29 anchors existing rows (`C/S separation`, `Task Cursor`, `Dynamic Hydration`); ADR-0092 scope boundary | Phase 3 (eBPF probe, DMA zero-copy, Kunpeng KAE) is sibling-epic authority, not spring-ai-ascend L0. |
| **Ledger Dim 2 — Multi-Track Bus + Data Plane** | Dim 2 (Phase 1-3) | ADR-0050 (three-track bus schema); ADR-0090 + ADR-0091 (EngineEnvelope structural carrier); ADR-0092 (scope boundary) | shipped schema (Phase 1) / deferred (Phase 2) / **archived: out-of-scope (Phase 3)** | L0 / W2-W3 / **out-of-scope** | platform (envelope + routing); **Agent-OS (RDMA/NPU-Direct/DAG-aware prefetch)** | Yes — Gate Rule 29 anchors `Three-track bus`; structural-carrier parity per Rule G-8.e | Data-locality routing is JVM-reachable W2/W3; serverless NPU weight prewarm + RDMA + NPU-Direct Storage are hardware-driver authority. |
| **Ledger Dim 3 — Transactional Rollback (SuspendSignal + 2PC + Semantic GC)** | Dim 3 (Phase 1-3) | ADR-0020 (RunStateMachine); ADR-0070 (sealed SuspendSignal); ADR-0091 (S2C callback envelope); ADR-0092 (scope boundary) | shipped (Phase 1) / partial (Phase 2) / **archived: out-of-scope (Phase 3)** | W0 / W2-W3 / **out-of-scope** | platform (SuspendSignal + Checkpointer); **Agent-OS (Semantic GC over HBM)** | Yes — Rule R-M.d sealed-checked-variant; Rule G-3.e terminal-state scope; ADR-0092 boundary | Pre-commit fingerprint + causal subscription = W2/W3 JVM-reachable; Semantic GC over NPU HBM = NPU memory-manager authority. |
| **Ledger Dim 4 — Swarm Evolution (Heterogeneous Integration)** | Dim 4 (Phase 1-3) | ADR-0070 (stateless shell + read-only Context); ADR-0078 (engine-contract consolidation); engine-envelope.v1.yaml + engine-hooks.v1.yaml; ADR-0092 (scope boundary) | shipped (Phase 1) / shipped W2.x (Phase 2 dynamic Hook injection) / **archived: out-of-scope (Phase 3)** | W1 / W2.x / **out-of-scope** | platform (SPI boundary); **Agent-OS (async RL pipeline on NPU)** | Yes — Rule R-M.c (HookPoint contract); ADR-0070 read-only Context; ADR-0092 framing alignment | Heterogeneous-framework isolation mechanism is *SPI boundary immutability* (not "cognitive disablement") per ADR-0092 §3.1; async RL pipeline on NPU = training-platform authority. |

## Maintenance

- **New whitepaper concept added** → add a new row here in the same PR; update Gate Rule 29's required-concept list if the concept is "central"; bump self-tests if needed.
- **Concept moves status** (e.g. `active-design` → `shipped`) → update row in the PR that ships the change.
- **Release note** claiming whitepaper alignment MUST reference this matrix and the entries' current status.
- **Gate Rule 29** (`whitepaper_alignment_matrix_present`) mechanically verifies the 20 named concepts above all appear in this file by exact-substring match.

## Companion artifacts

- Reviewer source: `docs/logs/reviews/2026-05-13-whitepaper-alignment-remediation-proposal.en.md`
- Self-audit: `docs/logs/reviews/2026-05-13-whitepaper-alignment-self-audit.en.md`
- ADR-0049 / ADR-0050 / ADR-0051 / ADR-0052: the four whitepaper-alignment ADRs landing in this remediation cycle.
- ARCHITECTURE.md §4 #47-#50: the four new architecture constraints anchored by the above ADRs.
- `architecture-status.yaml`: the per-capability ledger; new rows for each L0 contract introduced in this cycle.
