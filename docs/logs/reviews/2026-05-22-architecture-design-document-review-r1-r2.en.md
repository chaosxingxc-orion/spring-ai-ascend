---
level: L0
view: process
affects_level: L0
affects_view: scenarios
affects_artefact:
  - ARCHITECTURE.md
  - docs/governance/architecture-status.yaml
  - docs/adr/0053-cohesive-agent-swarm-execution.md
  - docs/adr/0106-run-version-two-phase-migration.yaml
  - docs/adr/0107-federation-ancestor-reconstruction-via-runregistry.yaml
  - docs/logs/releases/2026-05-22-l0-rc32-residual-corrective-and-family-truth.en.md
  - gate/always-loaded-budget.txt
  - agent-middleware/ARCHITECTURE.md
proposal_status: review
date: 2026-05-22
authors: ["compound-engineering:document-review (orchestrated via Claude Code on D--chao-workspace-spring-ai-ascend)"]
review_scope:
  - L0 root ARCHITECTURE.md (911 lines)
  - L1 agent-bus / agent-client / agent-evolve / agent-execution-engine / agent-middleware / agent-service ARCHITECTURE.md (6 modules, ~1553 lines combined)
  - architecture-design phase contract enforcement
responds_to:
  - docs/governance/contracts/architecture-design.md
  - docs/logs/releases/2026-05-22-l0-rc32-residual-corrective-and-family-truth.en.md
review_methodology:
  - tool: compound-engineering:document-review skill (multi-persona parallel review)
  - personas_dispatched: [coherence, feasibility, product-lens, security-lens, scope-guardian, adversarial]
  - rounds: 2
  - r1_findings_raw: 65
  - r1_findings_post_dedup: 47
  - r1_auto_fixes_applied: 1
  - r1_refinement_edits_applied: 4
  - r2_r1_closed: 4
  - r2_r1_partially_closed: 5
  - r2_r1_still_open: 54
  - r2_r1_strengthened: 2
  - r2_new_findings: 17
related_adrs:
  - ADR-0053
  - ADR-0074
  - ADR-0078
  - ADR-0079
  - ADR-0088
  - ADR-0098
  - ADR-0100
recurring_family_signal:
  - F-cross-authority-agreement (one new occurrence: §4 #51 vs ADR-0053 dimension count)
  - F-numeric-drift (one residual occurrence: L0 §2 tree "9-value enum" not caught by R1 auto-fix)
---

# L0 Architecture-Design Document Review — R1 + R2

## Verdict

The L0 + 6 L1 architecture-design corpus is **structurally honest about what ships** (W0 subset, posture gates, SPI scaffolds) but carries **deferred-design prose proportional to a W3–W4 system**, not the W0 product surface. The 2-round multi-persona review surfaced 76 findings whose distribution across personas — and resistance to mechanical refinement — point to a stable, well-named class of decisions waiting on author judgment, not on additional review iteration.

Three specific R2-NEW findings are **blocking** for the next ADR or implementation wave because they were introduced or revealed by the R1 refinement itself (compounding-irony class). Five R1 findings closed cleanly. Most R1 findings (54 of 63) require strategic decisions the document-review skill cannot resolve.

Recommend completion; route follow-up through `/design-mode` for the blocking R2-NEW set and `/commit-mode` for shipping this review note. The 11 product-lens / scope-guardian convergence findings are surfaced for human triage — they are the architecture-team decision space, not defects.

## Methodology

The review used the `compound-engineering:document-review` skill twice. R1 dispatched 6 reviewer personas against the full 7-doc corpus in parallel; 3 of 6 personas hit a transient API 529 overload and were retried successfully. R1 produced 65 raw findings deduplicated to 47. One auto-eligible fix landed (HookPoint enum count, agent-middleware §0.4: 9 → 10).

Between R1 and R2, four surgical refinement edits landed on the L0 ARCHITECTURE.md:

1. **§3 OSS deps row** — Spring AI 2.0.0-M5 footnoted with milestone-not-GA status, 2026-08-01 gate re-evaluation deadline, cross-reference to `docs/cross-cutting/oss-bill-of-materials.md` §3.1.
2. **§4 #7 SPI purity** — wording generalized from narrow `service.runtime.*.spi.*` to every `*.spi.*` package across modules, plus a documented narrow cross-spi allowlist (`engine.orchestration.spi` ↔ kernel `runs.*`/`runs.spi.*`; `engine.spi` ↔ `middleware.spi.HookPoint`).
3. **§4 #20 RunStateMachine** — added explicit "Known W0 limitation" block naming the in-memory cancel-vs-resume race, plus a W2 plan (version field + CAS, `RunCancelDuringResumeRaceIT`).
4. **§4 #51 SpawnEnvelope** — added a child-tenant equality invariant (`child.tenantId == parent.tenantId`) and a parent-chain acyclicity invariant (`ancestor_run_ids` max-depth 8, parent-propagated; orchestrator rejects spawns whose requested child run-id appears in the list).

R2 re-dispatched the same 6 personas to assess closure of R1 findings and detect new defects introduced by the refinement.

## R2 closure tally

| Outcome | Count | Note |
|---|---|---|
| **Closed** | 4 | All coherence: SPI purity contradiction L0 vs L1; agent-bus SPI narrative; agent-service maturity phrasing; HookPoint count (auto-fix). |
| **Partially closed** | 5 | Each closure introduced at least one new gap (see §"R2-NEW" below). |
| **Still-open** | 54 | Most R1 P0/P1 product, security, and scope findings unaffected by surgical edits. |
| **STRENGTHENED** | 2 | scope-guardian SG-01 + SG-06: refinement added prose to §4 without adding shipped functionality. |
| **R2-NEW** | 17 + 1 meta | Issues revealed or introduced by the refinement edits themselves. |

## Blocking R2-NEW findings (5)

The following findings were introduced or revealed by the R1 refinement edits and need closure before the next ADR / implementation wave:

### R2-NEW-1: ADR-0053 still enumerates 15 SpawnEnvelope dimensions; §4 #51 now demands 16

The refinement edit added `ancestor_run_ids` as a required SpawnEnvelope field. ADR-0053's enumerated 15-row dimension table did not gain a 16th row. `grep ancestor_run_ids` across the repo matches only `ARCHITECTURE.md`. `docs/governance/architecture-status.yaml` has no row tracking the new dimension. This is a fresh instance of the **F-cross-authority-agreement** recurring-defect family already named in `docs/governance/recurring-defect-families.yaml`.

Mitigation paths:
- Amend ADR-0053 to the 16-row state and add an `architecture-status.yaml` row, OR
- Move the acyclicity invariant out of §4 #51 prose into a new ADR explicitly scoped W2 with `wave_qualifier`.

### R2-NEW-2: Federation acyclicity is unenforceable on caller-propagated ancestor_run_ids

Same-instance cycle prevention works (the orchestrator owns its own ancestor list authoritatively). Cross-instance federation does not: a malicious or buggy peer Agent Service instance can truncate the list (max-depth 8) or omit entries to defeat cycle detection or forge ancestor attribution. The refinement edit gave **false confidence of compile-time prevention** for a property that requires cryptographic attestation to actually hold across instances.

Mitigation paths (each requires an ADR):
- Sign `ancestor_run_ids` per-hop with the spawning instance's key, verify on receive.
- Replace caller-propagated list with a central `RunRegistry` that reconstructs the ancestor chain from server-side state.
- Two-phase admission: receiving instance asks each ancestor instance to confirm cycle-free before admitting.

### R2-NEW-3: §4 #20 W0 → W2 migration ignores in-flight Run rows

The refinement edit's W2 plan (version field + CAS, `persisted.version != run.version() - 1`) assumes clean version semantics from W2 onward. Pre-W2 SUSPENDED Runs migrated to the new schema will have `version = NULL` or `version = 0` default. First W2 save evaluates `0 != -1` (FAIL) or NPE depending on default. There is no documented "first save is unconditional, subsequent are CAS" carve-out, and any such carve-out re-opens the race window once per Run-lifetime across the migration boundary.

Mitigation paths:
- Introduce the `version` field at W1 with no behavior (just shape) so W2 migration is purely enabling the check.
- Document a status-reset migration that loses suspended-resume context (operationally expensive but clean).
- Two-phase migration (W1.5 field, W2 check) with an explicit ADR.

### R2-NEW-4: §4 #51 invariants assert a guarantee on a Java type that does not yet exist

The child-tenant equality and acyclicity invariants are asserted "every SpawnEnvelope MUST set …" but `SpawnEnvelope` is explicitly deferred to W2. Until then there is no enforcement surface. The phantom contract may itself violate Gate Rule G-2 sub-clause .a (architecture-text reality) because the W2 wave qualifier is not in the same text block as the MUST clause.

Mitigation: amend §4 #51 prose to carry an explicit `(W2 — when SpawnEnvelope Java type ships)` qualifier in the same block.

### R2-NEW-5: `RunCancelDuringResumeRaceIT` floats with no anchor

The refinement edit names this integration test, but the FQN appears only in `ARCHITECTURE.md`. It is absent from `docs/governance/architecture-status.yaml#run_optimistic_lock`, `docs/CLAUDE-deferred.md`, and any ADR. Rule G-2.a (architecture-text reality) requires `tests:` paths to anchor test claims.

Mitigation: add the FQN to `architecture-status.yaml#run_optimistic_lock.tests:` slot (currently absent) when the W2 row gains a tests block.

## R2-NEW secondary findings

| # | Finding | Reviewer | Severity |
|---|---|---|---|
| 1 | §4 #7 cross-spi allowlist softened SPI purity from absolute to list-bound with no list-size cap | adversarial | P2 |
| 2 | §4 #51 child-tenant-equality + acyclicity invariants have no defined error code / detection point / audit shape | feasibility | P1 |
| 3 | `ancestor_run_ids` max-depth 8 has no nil / empty / overflow semantics | feasibility | P2 |
| 4 | HookPoint count fixed in agent-middleware §0.4 prose; no count-truth gate added — F-numeric-drift will recur on next enum addition | adversarial | P2 |
| 5 | L0 §2 tree (line 153) STILL says "9-value enum" — R1 auto-fix only touched agent-middleware §0.4 | coherence | P1 |
| 6 | L0 §2 says "6 orchestration SPI types" but enumerates 6 + RunMode = 7 | coherence | P1 |
| 7 | StatelessEngine SPI declared rc22 but L0 §2 module-layout omits it | coherence | P1 |
| 8 | Refinement-edit allocation reveals governance-first prioritization (5 internal-governance + 0 external-developer surfaces) | product-lens META | P1 |

## R1 finding categories that remain open (high-level)

The 54 still-open R1 findings cluster into four categories that document-review cannot resolve mechanically:

**Premise / audience (8 findings).** §1 names "financial-services operators (self-host)" as the target persona; the deep platformization (8 modules, 5 planes, 3 channels, 16+ SPI packages, 90 ADRs) solves for a platform-engineering audience, not an FSI compliance buyer. P-A's self-service promise is rhetorical because the quickstart requires Spring Boot 4 + Java 21 + Postgres + (W1+) Vault. P-B's four competitive pillars are unmeasured (`current_value: N/A` across the board). Brand "Ascend" reads as Huawei-NPU regional positioning. Spring Boot 4.0.5 + Spring AI 2.0.0-M5 milestone pairing contradicts FSI procurement risk posture. The MCP-only audit surface contradicts the "no admin UI" exclusion in a buyer category that interviews operators for visual evidence.

**Scope / governance overhead (12 findings).** 65 §4 constraints + 90 ADRs + 132 gate rules + 167 enforcers for a system shipping 1 HTTP endpoint. Three of five deployment planes have no production code. 13–16 declared SPI packages, half explicitly placeholder. Three-track channel isolation is gate-enforced today but the W2 bus substrate is absent. L2 docs directory exists with three slugs and zero content. agent-evolve ships 1 SPI interface but carries a 136-line L1 ARCHITECTURE.md with a 2×2 mode-modality matrix. CausalPayloadEnvelope + PayloadCodec + SemanticOntology constraint stack (§4 #13/#21/#25/#36) governs cross-JVM-boundary scenarios for a W0 system that never crosses a JVM boundary.

**Security plan-level gaps (10 findings).** W0 ships unauthenticated tenant identity (`X-Tenant-Id` header validated for UUID shape only, no JWT until W1). Tenant re-authorization shipped only for cancel; read and resume endpoints rely on tenant-scope-as-not-found which is an existence oracle. Audit log not tamper-evident at W2 plan level. S2C callback has no documented server-identity-proof mechanism (no mTLS / HMAC / signed JWT in `s2c-callback.v1.yaml`). Hook chain `failsafe` design treats PII redaction as best-effort — PII redactor failure proceeds to downstream LLM call with raw content. UNTRUSTED skills route through `NoOpSandboxExecutor` between W2 (Skill SPI ships) and W3 (Rule 27 startup gate). GraphMemoryRepository has no tenant-isolation mechanism for graph traversals (Cypher injection, cross-tenant nodes). Outbox at-least-once + sensitive payload replay unaddressed. OTLP/Langfuse cross-tenant data hub risk unaddressed. Vault key rotation + LLM provider credential lifecycle absent. Run.parentRunId cross-tenant equality partially closed by §4 #51 refinement; new ancestor-list integrity threat opened. FSI compliance baseline (tamper-evident audit, FIPS, data residency, key rotation) entirely absent.

**Implementation feasibility / failure modes (24 findings).** SuspendSignal as checked exception fails three concrete migration paths: Reactor operators, `CompletableFuture.supplyAsync` lambdas, W4 Temporal workflows. RunStateMachine.validate has no lock and no version field at W0; partially closed by refinement. Spring Boot 4.0.5 + Spring AI 2.0.0-M5 pairing not verified end-to-end (partially closed by gate footnote). Postgres RLS + HikariCP + virtual-thread pinning interaction unaddressed. W4 Temporal determinism contract missing for `NodeFunction` lambdas + PayloadCodec ↔ DataConverter binding. AppPostureGate fail-open on missing env var (unset APP_POSTURE silently boots dev in prod). Three-track Control vs Data ordering: client observably receives `NodeStarted` after `Terminal(CANCELLED)`. Hook chain ordered+failsafe wording self-contradicts between L0 §4 #16 and agent-middleware §3. Idempotency-Key 409 response body shape undefined (timing-oracle + replay-divergence). CausalPayloadEnvelope FACT-tag is producer-set with no attestation — LLM hallucination promoted to FACT. Hook @Order tie-breaking undefined. Run.parentRunId cycle deadlock partially closed (same-instance only). MCP SDK 1.0.0 pin vs Spring AI 2.0-M5 transitive binding unaddressed. GraalVM Polyglot JVM distribution / GPL license / Context-vs-virtual-thread pinning hazards unaddressed. agent-runtime-core dissolution migration guide absent. Performance pillar destroyed by RLS + Hook + Outbox stack (~19ms overhead per LLM call) without published budget. W2 IdempotencyStore Postgres dedup table has no GC / GDPR erasure plan. agent-client SDK W3+ vs current curl callers — no wire-contract compatibility plan. Reference adapters posture-gated via constructor throw — opaque `BeanCreationException` boot diagnostic.

## Recurring-defect-family signal

This review surfaces fresh instances of two families already in `docs/governance/recurring-defect-families.yaml`:

- **F-cross-authority-agreement** — §4 #51 vs ADR-0053 dimension count drift (R2-NEW-1). Per Rule G-9 sub-clause .b this freshness signal SHOULD trigger an update to the family ledger when a follow-up ADR lands closing R2-NEW-1.
- **F-numeric-drift** — L0 §2 tree line 153 "9-value enum" survived R1 auto-fix because the fix scoped to agent-middleware §0.4 only; the second occurrence was outside the fix's reach. R2-NEW-5 reads this as evidence that count claims need a structural gate (enum cardinality vs prose), not per-incident corrections.

A new family candidate, **F-refinement-introduces-regression**, is implicit in the 17 R2-NEW findings: every R1 refinement edit either failed to close its target finding completely (4 partial closures) or introduced a fresh defect adjacent to the closure (5 blocking R2-NEW findings). If observed across a future wave, this pattern would justify promotion to a named family.

## Coverage table

| Persona | R1 raw | R1 post-dedup | R1 closed at R2 | R1 partial at R2 | R1 still-open at R2 | R1 strengthened at R2 | R2 new |
|---|---|---|---|---|---|---|---|
| coherence | 5 | 5 | 3 | 0 | 2 | 0 | 5 |
| feasibility | 10 | 10 | 1 | 0 | 9 | 0 | 4 |
| product-lens | 10 | 10 | 0 | 0 | 10 | 0 | 2 + 1 meta |
| security-lens | 12 | 12 | 0 | 1 | 11 | 0 | 1 sub-finding |
| scope-guardian | 12 | 12 | 0 | 0 | 10 | 2 | 1 |
| adversarial | 16 | 13 | 0 | 4 | 12 | 0 | 5 |
| **Total** | **65** | **62** | **4** | **5** | **54** | **2** | **17 + 1 meta** |

## Recommendations

1. **Triage the 5 blocking R2-NEW findings first** via `/design-mode`. Each needs an ADR amendment or new ADR before the next ratchet wave (rc33 if numbering continues). Order of priority by closure-cost: R2-NEW-5 (anchor a test FQN — minutes), R2-NEW-1 (amend ADR-0053 to 16 rows or move invariant — hours), R2-NEW-4 (add wave qualifier to §4 #51 — minutes), R2-NEW-3 (write a migration ADR — hours), R2-NEW-2 (federation attestation requires significant ADR — days).

2. **Decide the governance-first META question** surfaced by the convergence signal (product-lens P-R2-META-1). The 5 R1 refinement edits all tightened internal governance and zero touched external-facing surfaces. The architecture team's revealed prioritization function is consistent across both rounds. If this matches strategic intent (internal-governance infrastructure with open-source byproduct), the 10 product-lens findings are deprioritisable wholesale and §1 should say so. If strategic intent is to compete with Spring AI Alibaba for external Spring Java developers, the convergence pattern is a misalignment signal worth a founder-level memo.

3. **Defer R3 and further refinement rounds.** The skill's iteration guidance is "after 2 refinement passes, recommend completion — diminishing returns are likely." R2 confirmed this: 4 R1 closures + 17 R2-NEW findings is net-negative on closed-defect count. The remaining R1 still-open findings are decision-class, not defect-class.

4. **Address SG-01 + SG-06 STRENGTHENED honestly.** The refinement edits made the "governance corpus volume exceeds product surface" finding worse, not better. The next ADR cycle should institute a constraint-budget gate (e.g. "no new §4 constraint may land before W2 ships ≥3 end-to-end shipped capabilities backed by real-LLM integration tests").

## Composes with

- `/design-mode` for R2-NEW-1 through R2-NEW-5 ADR drafting.
- `/commit-mode` for landing this review note + recording R2-NEW closures in `recurring-defect-families.yaml` once the corresponding ADRs land.
- `/review-mode` if R2-NEW closure work itself triggers another review wave.
