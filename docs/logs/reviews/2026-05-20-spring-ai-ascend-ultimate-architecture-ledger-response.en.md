---
level: L0
view: scenarios
affects_level: L0
affects_view: scenarios
proposal_status: response
date: 2026-05-20
authors: ["rc15 corrective wave"]
responds_to:
  - docs/logs/reviews/2026-05-20-spring-ai-ascend-ultimate-architecture-ledger.md
related_adrs:
  - ADR-0049   # C/S Dynamic Hydration Protocol + Task Cursor
  - ADR-0050   # Workflow intermediary mailbox + rhythm track
  - ADR-0051   # Memory & knowledge ownership boundary
  - ADR-0052   # Skill topology scheduler + capability bidding
  - ADR-0070   # ResilienceContract + skill capacity matrix
  - ADR-0078   # Engine-contract consolidation (W2.x)
  - ADR-0088   # agent-runtime-core dissolution
  - ADR-0089   # Edge-plane IngressGateway mandate
  - ADR-0090   # rc14 cross-authority parity + engine semantic-home
  - ADR-0091   # rc15 structural-carrier parity + terminal-state scope
  - ADR-0092   # Ledger acknowledgment + Agent-OS scope boundary
affects_artefact: [docs/adr/0092-ledger-acknowledgment-and-agent-os-scope-boundary.yaml, docs/governance/whitepaper-alignment-matrix.md, docs/logs/releases/2026-05-20-l0-rc15-structural-carrier-parity-and-terminal-state-scope.en.md]
---

# Ultimate Architecture Ledger — Alignment & Scope Response

**Verdict:** Ledger accepted as L0 `view: scenarios` vision artefact (ADR-0092). Phase-1 baseline confirmed against shipped code + L0 ADRs. JVM-reachable Phase-2 sub-items mapped to existing deferrals (`docs/CLAUDE-deferred.md`). **Phase-3 OS/hardware items declared out-of-scope for `spring-ai-ascend` L0 authority** and assigned to a sibling Agent-OS / openEuler / NPU-driver epic.

## 1. Document Type and Strongest Interpretation

The ledger carries `proposal_status: ledger` with `level: L0` and `view: scenarios`. It is **not a review with actionable findings**; it is a vision artefact sketching the long-arc evolution of `spring-ai-ascend` as the JVM control plane of a future "Agent OS" with OS-kernel + hardware co-design.

The strongest valid interpretation: the architecture vision team is naming a multi-phase trajectory and asking the engineering team to (a) confirm Phase-1 parity with shipped code, (b) acknowledge which Phase-2 sub-items are already deferred vs which are new, and (c) declare a scope boundary for Phase-3 since those items reach beyond JVM authority. This response document does all three.

## 2. Per-Dimension × Per-Phase Alignment Table

For each (Dimension N, Phase M), we cite one of:

- **shipped** — implementation lives in code today; ADR + path attached.
- **architected** — L0 ADR + §4 constraint exists; implementation deferred per `docs/CLAUDE-deferred.md`.
- **JVM-reachable Phase-2 / deferred** — within `spring-ai-ascend` authority but past W2 wave boundary.
- **out-of-scope (Agent-OS)** — declared out of `spring-ai-ascend` L0 by ADR-0092; belongs to a sibling Agent-OS / openEuler / Kunpeng / NPU-driver epic.

### Dim 1 — Business-System Separation

| Phase | Ledger claim | Status | Spring-AI-Ascend artefact / authority |
|---|---|---|---|
| **Phase 1** | C/S Task Cursor + Business Rule Subset + Skill Pool Limit | **architected** | ADR-0049 (C/S Dynamic Hydration Protocol); ARCHITECTURE.md §4 #47; whitepaper alignment matrix row "C/S separation" + "Task Cursor" + "Dynamic Hydration". Wire bindings deferred W2. |
| **Phase 2** | Pure control plane; business inference delegated to LLM gateway via JSON Schema | **JVM-reachable / shipped subset** | ResilienceContract.resolve(tenant, skill) shipped at W1 (ADR-0070; `agent-service/.../resilience/spi/ResilienceContract.java`). Multi-tenant `tenantId` propagation shipped per Rule R-C.c+e (post ADR-0088). Full delegation-to-gateway architecture deferred W2 per `docs/CLAUDE-deferred.md`. |
| **Phase 3** | eBPF kernel probe for wait/polling; DMA zero-copy for multimodal; Kunpeng KAE for crypto/serde | **out-of-scope (Agent-OS)** | ADR-0092 declares this Agent-OS / openEuler / Kunpeng-driver authority. `spring-ai-ascend` will not open ADRs for kernel-level eBPF probes, DMA channels, or KAE offload. Re-evaluate at sibling-epic boundary, not in this repo. |

### Dim 2 — Multi-Track Bus + Data Plane

| Phase | Ledger claim | Status | Spring-AI-Ascend artefact / authority |
|---|---|---|---|
| **Phase 1** | A2A async envelope; control / data topic split in broker | **shipped (schema) / architected (physical)** | `docs/governance/bus-channels.yaml` declares three channels (control / data / rhythm) per Rule R-E + ADR-0050; `docs/contracts/engine-envelope.v1.yaml` + `EngineEnvelope.java` shipped per ADR-0090 + ADR-0091. Physical channel isolation (separate brokers) deferred per `docs/CLAUDE-deferred.md` 35.b. |
| **Phase 2** | Data-locality routing; Serverless NPU weight prewarm | **JVM-reachable / deferred (W2/W3)** | `WorkflowIntermediary` + `Mailbox` + `AdmissionDecision` architected per ADR-0050 + ADR-0052. Data-locality routing on top of `EngineEnvelope.targetNode` is a W2/W3 wave item; not yet in `docs/CLAUDE-deferred.md` — add when scheduled. **Serverless NPU weight prewarm: out-of-scope (Agent-OS)** — NPU firmware authority. |
| **Phase 3** | RDMA + NPU-Direct Storage; DAG-aware prefetch | **out-of-scope (Agent-OS)** | ADR-0092. RDMA NIC drivers + NPU-Direct firmware = hardware authority. JVM cannot reach this surface without a kernel-bypass library. Sibling epic only. |

### Dim 3 — Transactional Rollback

| Phase | Ledger claim | Status | Spring-AI-Ascend artefact / authority |
|---|---|---|---|
| **Phase 1** | `SuspendSignal` checked-exception ejection; `RunStatus` atomic flag; `Checkpointer` in-memory | **shipped** | `SuspendSignal` (sealed checked-exception variant per ADR-0070), `RunStatus` enum + `RunStateMachine` (ADR-0020), `InMemoryCheckpointer` (16 KiB inline cap; W2 PayloadStore deferred). Lives at `ascend.springai.engine.orchestration.spi` per ADR-0088. |
| **Phase 2** | Distributed `AgentBus` + `EngineEnvelope`; pre-commit fingerprint; causal subscription; auto-rollback | **JVM-reachable / partial** | `EngineEnvelope` shipped. Pre-commit fingerprint + causal subscription = W2/W3 wave (S2C callback envelope per ADR-0091 partially covers this surface). Add to `docs/CLAUDE-deferred.md` when scheduled. |
| **Phase 3** | Semantic GC over HBM (NPU display memory); RunStateMachine instructs gateway to free Session ID | **out-of-scope (Agent-OS)** | ADR-0092. Semantic GC over NPU HBM = NPU memory-manager authority. JVM `RunStateMachine.cleanup()` can emit a `RunFinalized` event; the *consumer* of that event in NPU HBM is hardware authority. |

### Dim 4 — Swarm Evolution (Heterogeneous Integration)

| Phase | Ledger claim | Status | Spring-AI-Ascend artefact / authority |
|---|---|---|---|
| **Phase 1** | Heterogeneous frameworks lose native memory; stateless execution shell; central read-only context | **shipped** | Rule R-M (engine contract + server-sovereign boundary); ADR-0070 (stateless shell + read-only context); ADR-0078 (engine-contract consolidation). `docs/contracts/engine-envelope.v1.yaml` + `docs/contracts/engine-hooks.v1.yaml`. **Phrasing note**: see §3.1 below — "cognitive disablement" framing is replaced by *SPI boundary immutability* in spring-ai-ascend authority text. |
| **Phase 2** | Standardized middleware interface; runtime Hooks; dynamic parameter injection via `HookOutcome`; Context immutable | **shipped (W2.x)** | `HookPoint` enum + `HookDispatcher` per ADR-0070 / ADR-0078; `docs/contracts/engine-hooks.v1.yaml` declares 9 canonical hooks (`BEFORE_LLM_CALL` included); HookOutcome contract enforces explicit injection — no side effects on read-only Context. Rule R-M.c kernel + card. |
| **Phase 3** | Async shadow evaluation; pre-integrated async RL pipeline on NPU; Envelope auto-refresh | **out-of-scope (Agent-OS)** | ADR-0092. NPU-based async RL pipeline = training-stack authority. The JVM control plane can *consume* a refreshed `EngineEnvelope` config that an external RL system writes back; the RL pipeline itself is sibling-epic work. |

## 3. Pushback / Disagreements

### 3.1 — "Cognitive disablement" framing (Dim 4 Phase 1)

The ledger frames heterogeneous-framework isolation as adversarial:

> 任何异构框架（如内置了演进能力的 Hermes）接入时，**强制关闭其原生记忆系统**。

The shipped mechanism is *SPI boundary immutability*, not "disablement":

- Engines run as **stateless execution shells** injected with read-only `RunContext` via `engine-envelope.v1.yaml` (Rule R-M.a + ADR-0070).
- Engines observe lifecycle through the canonical `HookPoint` enum + `HookDispatcher` per `engine-hooks.v1.yaml` (Rule R-M.c).
- Engines **cannot side-effect into platform middleware** because middleware is consumed via SPI behind the engine-envelope boundary; the engine has no handle to platform-side stores.

The framework is **bounded by contract**, not forcibly disabled. A heterogeneous framework that *carries* a native memory system is welcome to use it — the boundary just guarantees that nothing inside the engine shell can persist to platform memory. The ledger framing reads as adversarial because it implies the platform reaches into the framework to disable subsystems; the reality is that the SPI boundary leaves the framework's internals untouched but exposes no path for them to escape the shell.

**Recommendation:** Future architecture prose in `spring-ai-ascend` MUST use *SPI boundary immutability* / *stateless execution shell* vocabulary. The ledger document itself is not edited (`proposal_status: ledger`); ADR-0092 + this response capture the vocabulary alignment.

### 3.2 — Phase-3 OS/hardware scope (all four dimensions)

Every Phase-3 item in the ledger reaches into one of:

- Linux kernel authority (eBPF probes, syscall scheduling)
- Hardware DMA / RDMA driver authority (network NICs, NPU memory subsystems)
- Accelerator firmware authority (Kunpeng KAE, NPU-Direct Storage)
- NPU memory-manager authority (Semantic GC over HBM)
- Training-stack authority (async RL pipeline on NPU)

None of these are JVM-reachable from `spring-ai-ascend`'s authority surfaces (engine SPI, hook contract, resilience contract, ingress gateway, bus channels). They require kernel modules, NPU firmware, or RDMA NIC drivers — authority surfaces owned by openEuler kernel team, Huawei Kunpeng team, NPU-driver team, training-platform team.

**Recommendation:** ADR-0092 declares Phase-3 out-of-scope for `spring-ai-ascend` L0 authority and assigns it to a sibling Agent-OS / openEuler / Kunpeng / NPU-driver epic. This is not a rejection of the vision — it is a scope alignment so that:

- `spring-ai-ascend` rc15+ release notes are not measured against eBPF / DMA / KAE / RDMA / NPU-Direct / Semantic GC / async RL deliverables.
- Future ADRs in this repo do not formalize kernel-level decisions that this repo cannot enforce.
- The sibling epic gets clear ownership of the hardware co-design work without competing authority claims.

### 3.3 — Phase-2 sub-items: shipped vs deferred boundary

The ledger does not distinguish shipped Phase-2 sub-items from deferred ones; this response document does. The boundary matters for release-note accuracy. After this wave, the Whitepaper Alignment Matrix is the authoritative pointer for ledger ↔ shipped-ADR navigation; the per-dimension table in §2 above is the comprehensive map.

### 3.4 — "Post-software-depreciation era" framing (introduction)

This is motivational language with no architectural surface. `docs/governance/competitive-baselines.yaml` uses neutral four-pillar language (performance / cost / developer onboarding / governance) per Rule R-B. No code conflict; flagged as a stylistic note for the vision team in case they want to align the introduction with the four-pillar vocabulary in a future revision of the ledger document.

## 4. Acknowledgment

The ledger is a valuable L0 `view: scenarios` artefact:

- **Phase 1 baseline ~80%+ shipped or architected.** The Task Cursor protocol (ADR-0049), the three-track bus (ADR-0050), `EngineEnvelope` (ADR-0090 + ADR-0091), `SuspendSignal` checked-exception ejection, `ResilienceContract` decision envelope, Rule R-M engine contract are all in place. Where the ledger uses "Phase 1" language, the platform is ready.

- **Phase 2 trajectory is consistent with deferred items.** Distributed 2PC envelope, dynamic Hook injection (already shipped per ADR-0070), causal subscription, data-locality routing — these match items already in `docs/CLAUDE-deferred.md` or scheduled for W2/W3 waves. No new deferral rows required beyond what is already there.

- **Phase 3 is correctly framed as "Agent OS" ambition.** The ledger's framing of these items as OS-kernel + hardware co-design is accurate. The scope boundary in ADR-0092 acknowledges this directly: the items belong to a sibling epic, not to `spring-ai-ascend`'s repo authority.

## 5. Out-of-scope (Explicit Deferrals)

Items the ledger names that are explicitly NOT in `spring-ai-ascend` L0 authority:

- **eBPF kernel probes for agent wait/polling** — openEuler kernel team.
- **DMA zero-copy multimodal data channels** — Linux DMA / hardware driver team.
- **Kunpeng KAE crypto/serde offload** — Huawei Kunpeng accelerator team.
- **RDMA + NPU-Direct Storage** — RDMA NIC driver + NPU firmware teams.
- **Semantic GC over NPU HBM** — NPU memory-manager team.
- **Async RL pipeline on NPU** — training-platform team.
- **Serverless NPU weight prewarm** — NPU firmware authority.

These should be evaluated and decided in the sibling Agent-OS / openEuler / Kunpeng / NPU-driver epic, not in this repo.

## 6. Verification

This response does not ship code or rules. Its claims are verified by:

```bash
# 1. ADR-0092 exists and is well-formed
ls docs/adr/0092-ledger-acknowledgment-and-agent-os-scope-boundary.yaml

# 2. Whitepaper alignment matrix has new rows
grep -E '^\| \*\*Ledger Dim' docs/governance/whitepaper-alignment-matrix.md

# 3. rc15 release note cites this response
grep -F 'ultimate-architecture-ledger-response' docs/logs/releases/2026-05-20-l0-rc15-structural-carrier-parity-and-terminal-state-scope.en.md

# 4. Live graph reflects ADR-0092 + this doc
wsl python3 gate/build_architecture_graph.py
# Expect node/edge bump from rc15-pre-ADR-0092 baseline
```

## 7. Lessons captured to memory

- **`proposal_status: ledger` is the right frontmatter for vision artefacts.** It tells future reviewers + the gate that the document is not raising actionable findings; the response is alignment + scope boundary, not closure.
- **OS/hardware co-design needs an explicit boundary ADR.** Without ADR-0092, Phase-3 items would drift back into release-note expectations every time the ledger is read. A single boundary-declaration ADR is the proportionate response — neither rejecting the vision nor over-committing the repo.
- **SPI boundary immutability ≠ cognitive disablement.** Vocabulary alignment matters: the same mechanism reads as adversarial under one framing and as principled isolation under another. Authority text uses the SPI vocabulary; vision text remains as-is.
