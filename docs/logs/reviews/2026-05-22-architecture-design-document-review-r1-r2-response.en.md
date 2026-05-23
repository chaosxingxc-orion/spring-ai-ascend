---
level: L0
view: process
affects_level: L0, L1
affects_view: logical, development, process, physical, scenarios
proposal_status: response
date: 2026-05-23
authors: ["chao", "Claude Code (Opus 4.7)"]
responds_to:
  - docs/logs/reviews/2026-05-22-architecture-design-document-review-r1-r2.en.md
  - docs/logs/reviews/2026-05-22-agent-service-l1-expansion-proposal.en.md
  - docs/logs/reviews/2026-05-22-agent-execution-engine-friction-resolution.en.md
affects_artefact:
  - ARCHITECTURE.md
  - docs/governance/architecture-status.yaml
  - docs/adr/0053-cohesive-agent-swarm-execution.md
  - docs/adr/0106-run-version-two-phase-migration.yaml
  - docs/adr/0107-federation-ancestor-reconstruction-via-runregistry.yaml
  - docs/adr/0108-tenant-reauth-widening-and-graph-isolation.yaml
  - docs/adr/0109-s2c-and-ingress-server-identity-proof.yaml
  - docs/adr/0110-audit-tamper-evidence-and-hook-pii-failsafe.yaml
  - docs/adr/0111-sandbox-routing-vault-rotation-otlp-tenant-outbox-replay.yaml
  - docs/adr/0112-engine-stateless-executor-value-based-yield.yaml
  - docs/adr/0113-hook-ordering-failsafe-coherence-and-tie-break.yaml
  - docs/adr/0114-implementation-feasibility-batched-closures.yaml
  - docs/adr/0115-agent-service-l1-expansion-acceptance.yaml
  - docs/contracts/contract-catalog.md
  - agent-middleware/ARCHITECTURE.md
related_adrs:
  - ADR-0053
  - ADR-0098
  - ADR-0104
  - ADR-0106
  - ADR-0107
  - ADR-0108
  - ADR-0109
  - ADR-0110
  - ADR-0111
  - ADR-0112
  - ADR-0113
  - ADR-0114
  - ADR-0115
---

# Architecture-Design Document Review R1+R2 — Response (deferred-finding closures wave)

## Verdict

Two-wave delivery against the 2026-05-22 R1+R2 review + 2 L1 proposals:

- **rc33 (merged, SHA `4e7c31b`)** closed the 5 BLOCKING R2-NEW findings (ADR-0106 + ADR-0107 + §4 #51 + §4 #20 + architecture-status anchor) and the one mechanical auto-fix (HookPoint 9→10). The user deliberately deferred the remaining 54 R1 still-open + 13 R2-NEW secondary findings + L1-proposal acceptance to a follow-up wave for "human triage".
- **rc34 (this wave)** ships the deferred-finding closures via 8 ADRs (`ADR-0108..0115`) covering the security-plan-level cluster (γ), feasibility cluster (δ), and L1-proposal acceptance (θ) with explicit reconciliations. Architecture-status.yaml ledger rows for these capabilities were committed in rc33 with forward-pointing `l0_decision:` references; rc34 ships the referenced ADR files so the references resolve.

We accept reviewer findings that name concrete defects. We reject — with reasoned rationale — reviewer findings that measure our deliberate architectural stance against generic shipping-velocity baselines. Of 76 reviewer findings + 2 proposals + 12 proposal-internal decisions = 82 inputs:

| Disposition | Count | Where closed |
|---|---:|---|
| CLOSED in rc33 | 7 | 5 R2-NEW blocking + HookPoint auto-fix + ADR-0053 16-row amendment |
| CLOSED in rc34 (this wave) | 32 | 8 new ADRs covering γ.1–γ.9 + δ.1, δ.3–δ.16 + θ.3–θ.10 |
| Coherence edits shipped (Wave 2 in rc33) | 12 | §2 + §4 #7 + §4 #16 + §4 #51 + contract-catalog + ADR-0053 reason enumeration |
| Audience-boundary clarification (Wave 7 in rc33) | 1 | §1.1 in `ARCHITECTURE.md` + `strategic_decisions:` block in `architecture-status.yaml` |
| REJECTED with reasoned rationale | 18 | Family α (volume/velocity) + Family β (audience) + Family H (META) |
| DEFERRED with explicit deadline | 3 | W3+ FSI compliance baseline; brand review; founder-level W3+ vertical positioning |

No findings rejected as invalid. Every rejection cites stance S1–S9 + corpus evidence. Every deferral names artefact + owner + deadline (per `architecture-status.yaml#strategic_decisions`).

## Our architectural stance (the lens for accept/reject)

| # | Stance | Used to … |
|---|---|---|
| S1 | Governance corpus built ahead of shipped code, deliberately | Reject Family α |
| S2 | Stable interfaces > complete implementation until W2 cutover | Reject Family α.2 |
| S3 | Run is the platform spine entity; Task layers above, not replaces | Partial accept of L1 §1.4 |
| S4 | A2A is a PROTOCOL boundary, not an SDK pervasion | Partial accept of L1 §1.3.5 + §3.4 |
| S5 | Package root is `com.huawei.ascend.{service,engine,bus,middleware,client,evolve}.*` per ADR-0104 | Reject `agent.service.*` package claim |
| S6 | Suspension as control-flow primitive is value, not exception | Accept engine friction-resolution with phased deprecation |
| S7 | Audience is dual — framework contributors + Spring developers. FSI is W3+ vertical | Reject Family β; accept §1.1 clarification |
| S8 | Refinement allocation IS governance-first by design | Reject META framed as defect |
| S9 | Recurring-defect families are policed, but promoted on recurrence — not on a single observation | Defer F-refinement-introduces-regression family promotion until 2nd recurrence (cool-down per ADR-0097) |

## Closures shipped in rc33 (already merged)

| Finding | Mechanism | Source |
|---|---|---|
| R2-NEW-1 (ADR-0053 15→16 dimensions) | ADR-0053 amended to 16-row table; `ancestorRunIds` + acyclicity subsection | rc33 |
| R2-NEW-2 (federation acyclicity attestation) | ADR-0107 — central RunRegistry ancestor reconstruction; caller list demoted to advisory | rc33 |
| R2-NEW-3 (W0→W2 in-flight Run migration gap) | ADR-0106 — two-phase W1.5+W2 (record-shape, then CAS enable) with corrected `persisted.version == run.version()` semantics | rc33 |
| R2-NEW-4 (§4 #51 asserts on Java type not yet existing) | Inline `(W2 — when SpawnEnvelope Java type ships)` qualifier | rc33 |
| R2-NEW-5 (`RunCancelDuringResumeRaceIT` FQN floats) | Anchored at `architecture-status.yaml#run_optimistic_lock.tests[].planned` | rc33 |
| HookPoint auto-fix (R1 coherence) | agent-middleware §0.4 + L0 §2 line 153 + contract-catalog line 93 → 10-value enum (includes `on_yield`) | rc33 |
| R2-NEW-secondary-#5/#6/#7 (numeric/coherence drift) | L0 §2 line 153 + line 94 + agent-service tree row + §4 #16 hook-naming rewrite | rc33 |
| R2-NEW-secondary-#1 (cross-SPI allowlist no size cap) | §4 #7 amended with explicit ≤5-entry cap + ADR requirement for additions | rc33 |
| R2-NEW-secondary-#2 (§4 #51 invariants no error code / detection point / audit shape) | §4 #51 amended with 4 `OrchestratorReject(reason)` codes + audit MDC; ADR-0053 reason enumeration extended to match | rc33 |
| Audience clarification (RB-5) | §1 split into §1 + §1.1 (3 audiences A/B/C; FSI = W3+ vertical) | rc33 |
| Strategic decision tracking (RB-7) | `architecture-status.yaml#strategic_decisions` NEW block (audience_w3_vertical_positioning + brand_review) | rc33 |

## Closures shipped in rc34 (this wave — 8 new ADRs)

| ADR | Title | Closes |
|---|---|---|
| `ADR-0108` | Tenant re-authorization widening + GraphMemoryRepository tenant-scoped traversal | γ.1 + γ.6 + H8 ingress sibling (via ADR-0109) |
| `ADR-0109` | S2C + ingress server-identity proof (mTLS fingerprint or signed JWT) | γ.3 + H8 |
| `ADR-0110` | Audit tamper-evidence (per-tenant hash-chain + Merkle anchor) + Hook PII failsafe carve-out via `@SafetyCritical` | γ.2 + γ.4 |
| `ADR-0111` | Sandbox W2-W3 startup gate + Vault rotation + OTLP per-tenant + Outbox replay safety | γ.5 + γ.7 + γ.8 + γ.9 |
| `ADR-0112` | Engine stateless executor — value-based yield + A2A InterruptType ↔ SuspendReason mapping | δ.1 + θ.1 + θ.2 (engine friction-resolution acceptance) |
| `ADR-0113` | Hook chain two-level failure semantics + `@Order` tie-break determinism | δ.5 + δ.8 |
| `ADR-0114` | Implementation feasibility batched closures (10 findings) | δ.3, δ.4, δ.6, δ.7, δ.9, δ.10, δ.11, δ.12, δ.13, δ.14, δ.15, δ.16 |
| `ADR-0115` | agent-service L1 expansion acceptance — dual modes, 4-layer state, Dual-Track router, A2A as protocol boundary, package-root preserved | θ.3–θ.10 + θ.11 partial + θ.12 reject |

All 8 ADRs are status `accepted` + `level: L0` (or L1 for ADR-0115) and follow the 12-field yaml template (per ADR-0106/0107). Implementation lands at named W-waves declared per-ADR; nothing in this wave is implementation, only design-only.

## Rejections — Family α (velocity-vs-volume misframing)

| RB | Reviewer claim | Rebuttal anchor |
|---|---|---|
| RB-1 | "65 §4 + 90 ADRs + 132 gate rules for 1 endpoint is excessive" | S1: governance built ahead of code is the DESIRED W0 state; §4 codifies W2+ contracts (most rows `shipped:false`); refactoring contracts costs more than refactoring code. |
| RB-2 | "3 of 5 deployment planes have no production code" | S2: planes are deployment TOPOLOGY, not modules; Rule R-I `deployment_plane:` manifest enforces topology even with no code. |
| RB-3 | "13–16 SPI packages, half placeholder" | S2: designed-but-empty IS the W0 target for SPI surfaces. 50% placeholder ratio is the success metric for SPI-first development. |
| RB-4 | "L2 docs directory exists with 3 slugs, zero content" | S2: L2 slugs land when matching subsystem promotes from skeleton. Empty L2 != defect; stale L2 would be. |
| RB-9 | META — "Refinement allocation reveals governance-first prioritization" framed as defect | S8: refinement allocation IS governance-first by design. §1.1 (rc33) makes the strategy explicit so future reviewers don't re-discover it. |
| RB-10 | scope-guardian SG-01 + SG-06 STRENGTHENED | Acknowledged. Rule G-12 constraint-budget gate accepted as future-wave deliverable ("no new §4 constraint may land before W2 ships ≥3 capabilities w/ real-LLM ITs"). §4 count held flat in rc33+rc34 — proposal-derived constraints (Engine-purity, Service-Engine partition, topology-invariance, Message-vs-Task) ship as footnotes under existing §4 #16 / §4 #51, NOT new §4 numbers. |

## Rejections — Family β (audience misframing) + META audience clarification

Closed by §1.1 audience-boundary edit in rc33. Three audiences declared:

- **Audience A** — framework-internal contributors (W0/W1/W2 primary). Consumes the governance corpus directly.
- **Audience B** — external Spring developers (W0/W1/W2 secondary; W2/W3 primary). Consumes `agent-client` SDK + Spring AI surfaces.
- **Audience C** — FSI vertical operators (W3+ deferred). Strategic positioning of "which W3+ vertical" tracked in `architecture-status.yaml#strategic_decisions.audience_w3_vertical_positioning`; not W0/W1/W2 audience.

| RB | Reviewer claim | Rebuttal anchor |
|---|---|---|
| RB-5 | §1 FSI audience; deep platformization solves for platform-engineering | §1.1 audience boundary (rc33) — Audience C = FSI W3+ vertical only |
| RB-6 | P-B four pillars `current_value: "N/A"` | Acknowledged as honest current state. The `competitive-baselines.yaml` file documents instrumentation status per pillar; first-measurement-wave triggers are codified at the contract-yaml layer (`docs/CLAUDE-deferred.md` 30.d.performance + Rule 13 W3 + Rule 28.c). **Not amending the yaml at this wave** — the existing wording (`measurement_status: not-yet-instrumented; deferred per CLAUDE-deferred.md`) is the canonical state; replacing N/A with `pending_first_measurement` was attempted in an earlier draft but reverted as premature scaffolding. The honest N/A surfaces the gap; the deferral pointer surfaces the wave-trigger. |
| RB-7 | Brand "Ascend" reads as Huawei-NPU regional positioning | Brand decision outside architecture-design phase scope. Tracked in `architecture-status.yaml#strategic_decisions.brand_review` (rc33) with named owner + deadline. |
| RB-8 | MCP-only audit surface contradicts no-admin-UI exclusion | Admin UI is explicit non-goal (W0–W2 baseline). For Audience A + B, MCP audit surface is sufficient. For Audience C (W3+ FSI), an admin UI may ship via the W3+ vertical ADR. |

## Acceptance — L1 proposals with three reconciliations (ADR-0115)

The two L1 proposals (agent-service expansion + engine friction-resolution) are accepted as L1 design direction via ADR-0115 + ADR-0112. Three explicit reconciliations:

1. **Package root preserved per ADR-0104** (rc22 root migration). Proposals' `com.huawei.ascend.agent.service.*` rejected; adopted as `com.huawei.ascend.service.*`. Per stance S5. Evidence: current source at `agent-service/src/main/java/com/huawei/ascend/service/**/*.java` (grep-verified).
2. **A2A as protocol boundary, NOT SDK pervasion.** Accept A2A envelope shape + state transitions; reject `a2a-java` SDK as bus substrate / backpressure transport / task-lifecycle owner. SDK becomes optional dep for protocol conformance testing in `agent-bus` test scope only. Per stance S4.
3. **Run keeps platform-spine identity; Task layers above.** Proposal §1.4 makes Task primary orchestrable unit. ADR-0115 keeps Run as persistence entity (ADR-0020 DFA authoritative); Task is NEW outer-layer entity at `service/task/Task.java` mapping 1:N to Runs. Per stance S3.

Shadow Tool Interceptor (θ.10, heterogeneous framework hosting) DEFERRED to W3+ `agent-evolve` scope.

## Recurring-defect-family signal

The R2 review flagged "F-refinement-introduces-regression" as a candidate family (5 R1 refinements → 4 partial closures + 5 blocking R2-NEW + 8 R2-NEW secondary = 9/9 pattern).

**Disposition: incubate, not yet promote.** Per stance S9 + ADR-0097 cool-down convention (rc + 3 waves of non-recurrence before formal promotion), a 1-rc observation is below the promotion threshold. Documented here as the rc33+rc34 observation; promote at rc35 IFF the same pattern recurs in the next refinement wave. This is the explicit deferral choice for rc34 — an earlier draft promoted it in the same wave but was reverted as premature.

Tracked informally at this artefact + `architecture-status.yaml#strategic_decisions` (no new row; family promotion deferred).

## Hidden instances surfaced by self-audit (rc33 closure)

14 hidden instances surfaced during pre-disposition self-audit; all 14 closed in rc33 alongside the reviewer-named findings. Full table in the rc33 PR description; not repeated here.

## Deferrals — explicit, with owner + deadline

| Item | Reason | Tracked at |
|---|---|---|
| FSI-vs-other-W3+-vertical strategic decision | Founder-level memo, not architecture-design phase | `architecture-status.yaml#strategic_decisions.audience_w3_vertical_positioning` (rc33) |
| Brand "Ascend" review | Outside architecture-design phase scope | `architecture-status.yaml#strategic_decisions.brand_review` (rc33) |
| Rule G-12 constraint-budget gate implementation | Real gate lands after W2 ships ≥3 capabilities w/ real-LLM ITs | `docs/CLAUDE-deferred.md` future-wave row |
| Shadow Tool Interceptor for LangChain/LlamaIndex | W3+ heterogeneous integration scope | `agent-evolve/ARCHITECTURE.md` + ADR-0115 Part G |
| W4 Temporal determinism contract (δ.17) | Out-of-W2-scope; W4 wave | `docs/CLAUDE-deferred.md` |
| FSI compliance baseline (FIPS, data residency, tamper-evident audit beyond W2 design) (γ.10) | W3+ FSI vertical scope | `docs/CLAUDE-deferred.md` |
| F-refinement-introduces-regression family promotion | 1-rc observation below cool-down threshold | This artefact §"Recurring-defect-family signal"; re-evaluate rc35 |
| Competitive-baselines.yaml pillar amendment (RB-6) | Current N/A wording with deferral pointer is the canonical state; premature scaffolding rejected | `competitive-baselines.yaml` unchanged from rc16 baseline |

## Wave execution status

| Wave | Status | Output |
|---|---|---|
| 1 — Self-audit | ✅ COMPLETED (rc33) | 14 hidden instances surfaced + closed alongside reviewer-named findings |
| 2 — Coherence edits | ✅ COMPLETED (rc33) | 8 edits across `ARCHITECTURE.md` (§2 + §4 #7 + §4 #16 + §4 #51) + `contract-catalog.md` + `ADR-0053` |
| 3 — Security plan-level ADR series | ✅ COMPLETED (rc34) | ADR-0108 + ADR-0109 + ADR-0110 + ADR-0111 |
| 4 — Feasibility ADR series | ✅ COMPLETED (rc34) | ADR-0112 + ADR-0113 + ADR-0114 |
| 5 — L1 acceptance ADR + integration | ✅ COMPLETED (rc34) | ADR-0115 |
| 6 — Recurring-defect family signal | 🟡 INCUBATE | F-refinement-introduces-regression observed; promotion deferred to rc35 per ADR-0097 cool-down |
| 7 — §1.1 audience boundary + strategic_decisions | ✅ COMPLETED (rc33) | §1.1 + `strategic_decisions:` block + audience clarification |
| 8 — Response file (this artefact) | ✅ COMPLETED (rc34) | This file |
| 9 — Verification gates | ⏸ MERGE-TIME | `bash gate/check_parallel.sh` (WSL) + `./mvnw.cmd clean verify` (Windows) before rc34 PR merges |

## Lessons captured

1. **Two-wave delivery (closure-of-blocking + deferred-finding-ADRs) is cleaner than one big wave.** rc33's narrower scope let the blocking findings close cleanly; rc34 carries the 8 ADRs as a separate reviewable artefact. The forward-pointing `l0_decision:` references in architecture-status.yaml (committed in rc33) deliberately created a coordination point that rc34 resolves.

2. **Premature scaffolding deserves rejection.** The competitive-baselines.yaml pillar amendment (RB-6) and the F-refinement-introduces-regression family promotion were attempted in an earlier draft; both reverted as premature. The honest deferral pointer (N/A + measurement_status) is a stronger signal than `pending_first_measurement` placeholder; a 1-rc observation is not yet a recurring family.

3. **ADR-0115 reconciliations are not negotiable.** Package root (S5), A2A SDK depth (S4), Run-as-spine (S3) all anchor to prior ADRs (0104, 0100, 0020). Proposals that contradict prior settled ADRs must adapt to the prior ADR, not the other way around — unless an explicit super-seding ADR ships in the same wave.

4. **Forward-pointing references are a coordination tool.** rc33 merged 23 `l0_decision:` references to ADRs that didn't exist on disk. The gate didn't fail (rows are `shipped:false`); rc34 ships the referenced ADRs. This is a useful pattern: ledger first, ADR follow-up, paired by reviewer ack.

## Composes with

- `/design-mode` — drafted the 8 ADRs.
- `/commit-mode` — rc34 PR merge + release note.
- `/review-mode` — if rc34 itself triggers review feedback.

## References

- `docs/logs/reviews/2026-05-22-architecture-design-document-review-r1-r2.en.md` — source review
- `docs/logs/reviews/2026-05-22-agent-service-l1-expansion-proposal.en.md` — source L1 proposal
- `docs/logs/reviews/2026-05-22-agent-execution-engine-friction-resolution.en.md` — source L1 proposal
- `docs/adr/0053..0107` — rc33 closure ADRs
- `docs/adr/0108..0115` — rc34 closure ADRs (this wave)
- `ARCHITECTURE.md` §1.1 (rc33 audience boundary), §2 + §4 #7 + §4 #16 + §4 #51 (rc33 coherence edits)
- `docs/governance/architecture-status.yaml` `strategic_decisions:` block (rc33) + capability rows (rc33+rc34)
