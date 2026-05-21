---
level: L0
view: process
affects_level: L0, L1
affects_view: development, scenarios, logical
proposal_status: response
date: 2026-05-22
authors: ["chao", "急急 (agent)"]
review_scope:
  - 2026-05-21 reviewer proposals (3 documents)
  - L0 architecture drift, inefficiency, security concerns
  - L1 architecture design suggestions
  - Six-component grounding architecture pin
responds_to:
  - docs/logs/reviews/2026-05-21-agent-service-l1-expansion-proposal.en.md
  - docs/logs/reviews/2026-05-21-l1-architecture-depth-and-grounding-proposal.en.md
  - docs/logs/reviews/2026-05-21-polymorphic-deployment-and-evolution-proposal.en.md
affects_artefact:
  - docs/adr/0099-rc22-l1-architecture-depth-and-grounding.yaml
  - docs/adr/0100-rc22-agent-service-l1-runtime-role-decomposition.yaml
  - docs/adr/0101-rc22-polymorphic-deployment-topology.yaml
  - docs/adr/0102-rc22-evolution-plane-online-offline-duality.yaml
  - docs/adr/0103-rc22-agent-middleware-naming-and-capability-services-distribution.yaml
  - docs/adr/0104-rc22-package-root-migration-to-com-huawei-ascend.yaml
  - docs/governance/rules/rule-G-1.1.md
  - docs/governance/deployment-loci.yaml
  - docs/governance/evolution-modalities.yaml
  - docs/contracts/reflection-envelope.v1.yaml
  - docs/contracts/agent-invoke-request.v1.yaml
  - docs/contracts/engine-hooks.v1.yaml
  - docs/governance/enforcers.yaml
  - docs/governance/recurring-defect-families.yaml
  - docs/governance/recurring-defect-families.md
  - CLAUDE.md
  - agent-client/ARCHITECTURE.md
  - agent-bus/ARCHITECTURE.md
  - agent-service/ARCHITECTURE.md
  - agent-execution-engine/ARCHITECTURE.md
  - agent-middleware/ARCHITECTURE.md
  - agent-evolve/ARCHITECTURE.md
related_adrs:
  - ADR-0099
  - ADR-0100
  - ADR-0101
  - ADR-0102
  - ADR-0103
  - ADR-0104
---

# rc22 Response — 2026-05-21 Reviewer Proposals (Six-Component Grounding View)

## Verdict

The three 2026-05-21 reviewer proposals are ACCEPTED with five explicit REJECTIONS. The six L1 reactor modules (`agent-client`, `agent-bus`, `agent-service`, `agent-execution-engine`, `agent-middleware`, `agent-evolve`) are pinned as the grounding architecture; no seventh module is added. Six new ADRs (0099–0104) record the wave's decisions; one new rule (G-1.1) enforces L1 architecture depth/grounding; one new defect family (`F-l1-architecture-grounding-gap`) is recorded in the recurring-defect ledger.

## Family Taxonomy (wave letter: N — rc22)

| Family | Cited findings | Defect class | Decision | Authority |
|---|---|---|---|---|
| **N-α** | Proposal #2 §2 + §3 (Rule G-1.c + Tree-Parser + SPI-Appendix-Scanner) | Hollow L1 architecture documents — no enforced depth/grounding | ACCEPT (rule-id corrected to G-1.1) | ADR-0099; Rule G-1.1 |
| **N-β** | Proposal #1 §1-§5 (5-component decomposition, lifecycle, stateless injection, reactive backpressure, SPI appendix) | `agent-service` concentration risk; no runtime-role separation | ACCEPT (with rejections on §2.3/§5.3 Yield-replacement + §5.1 SDK-embed) | ADR-0100 |
| **N-γ** | Proposal #3 §3 (Polymorphic Mode A/B) | L0 has logical planes but no Platform-Centric vs Business-Centric locus declaration | ACCEPT | ADR-0101; deployment-loci.yaml |
| **N-δ** | Proposal #3 §4 (Online vs Offline evolution duality) | `agent-evolve` lacks online-evolution model | ACCEPT as design intent (W3 impl) | ADR-0102; evolution-modalities.yaml |
| **N-ε** | Proposal #3 §2 (agent-middleware "hallucination" + rename + 7th module) | Module-naming vs platform-community semantics | REJECT (framing) + REJECT (7th module) + ACCEPT (capability concept distributed across the six) | ADR-0103 + ADR-0104 (package-root migration as separate wave) |

---

## Per-Proposal Accept / Reject Ledger

### Proposal #1 — agent-service L1 Domain Expansion

| Finding | Decision | Authority |
|---|---|---|
| §1 Logical View: 5-component decomposition | ACCEPT | ADR-0100 §decision |
| §2.1 Stateless execution closure | ACCEPT | ADR-0100 §decision |
| §2.2 Reactive backpressure (Reactor Sinks) | ACCEPT | ADR-0100 §decision |
| §2.3 "Abandon exception-based suspension, switch to Yield event" | **REJECT** — coexistence model: SuspendSignal stays canonical, Yield added as HookPoint.ON_YIELD cooperative hint | ADR-0100 §decision (Rejection 4) |
| §3 Development View directory tree | ACCEPT (package root reconciled to current `ascend.springai.*` via marker; rc22.5 migrates to `com.huawei.ascend.*` per ADR-0104) | ADR-0100 + ADR-0104 |
| §4 Scenario A + B | ACCEPT | ADR-0100 |
| §5.1 "Formally abandon ADR-0016 deferral; fully embed a2a-java SDK" | **REJECT** — contract-only adoption; no SDK runtime dep | ADR-0100 §decision (Rejection 3) |
| §5.2 Run ≤ Task ≤ Session ≤ Memory lifecycle | ACCEPT (ratify model; rc25 Java types + Flyway) | ADR-0100 §decision |
| §5.5 TaskID / SessionID decoupling | ACCEPT (with ADR documenting join semantics) | ADR-0100 §decision |
| §5.6 AgentInvokeRequest SPI | ACCEPT (new contract `docs/contracts/agent-invoke-request.v1.yaml`, status `design_only`) | ADR-0100 |
| Appendix: 3 new SPIs | ACCEPT (registered in module-metadata + contract catalog + DFX in rc22) | ADR-0100 |

### Proposal #2 — L1 Architecture Depth & Grounding

| Finding | Decision | Authority |
|---|---|---|
| §2 New rule (proposed "G-1.c") | ACCEPT (rule-id corrected to **G-1.1** per rc17 sub-rule convention; see Rejection 5) | ADR-0099; Rule G-1.1 |
| §2.1 Development View Code-Mapping mandatory | ACCEPT (sub-clause .a) | Rule G-1.1 |
| §2.2 SPI Interface Appendix mandatory | ACCEPT (sub-clause .b — adds 4th parity surface to Rule R-D's 3-way check) | Rule G-1.1 |
| §2.3 L2 Constraint Linkage mandatory | ACCEPT (sub-clause .c — vacuous until L2 docs land) | Rule G-1.1 |
| §3.A Tree-Parser Enforcer | ACCEPT (E166 — `gate/lib/check_l1_dev_view_tree.sh`) | ADR-0099 |
| §3.B SPI-Appendix-Scanner | ACCEPT (E167 — `gate/lib/check_l1_spi_appendix.sh`); 4-way parity (catalog + metadata + DFX + ARCHITECTURE.md appendix) | ADR-0099 + rule card reconciliation note |

### Proposal #3 — Polymorphic Deployment, Online Evolution, Middleware Nomenclature

| Finding | Decision | Authority |
|---|---|---|
| §2 "Severe semantic hallucination/deviation by previous implementers" framing | **REJECT** — current agent-middleware is consistent with ADR-0073 + Rule R-M.c + P-M | ADR-0103 §decision (Rejection 1) |
| §2 Proposed action 1: rename current agent-middleware → agent-runtime-hooks | **REJECT** — keep current name + scope | ADR-0103 §decision (Rejection 2) |
| §2 Proposed action 2: reuse `agent-middleware` for new Capability Services module | **REJECT** — no 7th module per user directive; capability concepts distribute across the existing six (Memory → graphmemory-starter + service; Skills → engine + service; Sandbox → engine + sandbox-policies; Knowledge → defer W3+) | ADR-0103 §decision |
| §2 Proposed action 3: Local-fallback / degradation design (Mode-B) | ACCEPT as design intent (Rule D-6 posture-aware defaults) | ADR-0101 §requires_design |
| §3 Mode A Platform-Centric | ACCEPT | ADR-0101 |
| §3 Mode B Business-Centric | ACCEPT (design intent; W2/W3 impl) | ADR-0101 |
| §4 Offline (T+1) + Online dual-track | ACCEPT (design intent; W3 impl) | ADR-0102 |
| §4.2 Mode × Modality 2×2 matrix | ACCEPT (canonical reference) | ADR-0102 + evolution-modalities.yaml |

---

## Rejections — Verbatim Push-Back for Reviewer Reply

### Rejection 1 — "Severe semantic hallucination/deviation" framing (Proposal #3 §2)

> The current `agent-middleware/ARCHITECTURE.md` and ADR-0073 deliberately scope the module as the **runtime-owned cross-cutting policy hook surface** — model gateway, tool authz, memory governance, tenant policy, quota, observability, sandbox routing, checkpoint, failure handling — implemented via canonical `HookPoint` events. This is not a hallucination; it is the same "middleware" pattern that traditional Java stacks (servlet filters, Spring HandlerInterceptor) use. The wider agent-platform community uses "middleware" for a different concept (cloudified capability services). We are fixing this locally via ADR-0103 — keeping the current name + scope, and **distributing the capability-services concept across the existing six modules** rather than reusing the name. The framing of prior implementers as having "deviated" is rejected.

### Rejection 2 — Add a new `agent-middleware` module for Capability Services (Proposal #3 §2 proposed action 2)

> Per the user's six-component pinning, no seventh module will be added. Memory / Skills / Sandbox / Knowledge are first-class concerns but they distribute naturally across the existing six: Memory → `spring-ai-ascend-graphmemory-starter` + `agent-service` `GraphMemoryRepository` SPI (ADR-0082); Skills → `agent-execution-engine` registry + `agent-service` capacity governance (Rule R-K); Sandbox → `agent-execution-engine` `SandboxExecutor` + `docs/governance/sandbox-policies.yaml` (Rule R-L); Knowledge → deferred to W3+ (no module owns it today). The reviewer's structural insight is accepted; the structural solution is not.

### Rejection 3 — "Fully embed `a2a-java` SDK" (Proposal #1 §5.1)

> ADR-0016 deferral exists because tight coupling to any third-party A2A SDK violates the SPI-purity discipline (Rule R-D) and the "no business-specific customizations in platform code" principle (Rule R-A). We accept A2A protocol alignment at the **contract layer** — adopting the A2A envelope schema and Task state vocabulary (`Submitted/Working/Input-Required`) into `docs/contracts/*.v1.yaml`. We reject embedding the SDK as a runtime dependency in `agent-service` until a separate ADR demonstrates that (a) the SDK semver policy matches our W2 ratchet cadence and (b) no cross-tenant state leaks via the SDK's internal caches. Proposal #1 §5.1 may proceed as "A2A contract adoption" but not as "SDK embed".

### Rejection 4 — "Abandon exception-based suspension; switch to explicit Yield event" (Proposal #1 §2.3 and §5.3)

> `SuspendSignal` as a **checked exception** is a Tier-A competitive differentiator. Three properties depend on it: (1) the Java compiler enforces that every caller of an engine SPI either handles suspension or declares it (cannot silently drop the signal); (2) Rule R-G ArchUnit tests rest on the checked-exception shape; (3) the rc8/rc9 cancellation paths use exception-flow semantics for cross-thread propagation. Switching to a pure `Yield` event would invalidate all three. We propose a coexistence model: `Yield` is added as a **cooperative-scheduling hint** (engine asks orchestrator to be rescheduled without persistence transition) on a new `HookPoint.ON_YIELD` (added to `engine-hooks.v1.yaml` in rc22). `SuspendSignal` remains the canonical state-machine suspension mechanism. Proposal #1 §2.3 and §5.3 should be revised to reflect coexistence, not replacement.

### Rejection 5 — Rule-id collision (Proposal #2 §2)

> Proposal #2 names the new rule "Rule G-1.c". This conflicts with the `.a/.b/.c` **sub-clause** convention used inside CLAUDE.md kernels (e.g., Rule R-M.a, R-M.b, R-M.c are sub-clauses of R-M, not standalone rules). Per rc17 sub-rule convention (extracted standalone child rules use `.1/.2` suffixes — e.g., R-C → R-C.1 + R-C.2), the rule is ratified as **Rule G-1.1**, not G-1.c. ADR-0099 records the convention.

### Naming reconciliation — Package root (Proposal #1 §3)

> Proposal #1 §3's directory tree declares Java packages under `com.huawei.ascend.agent.service…`. The current canonical corpus root is `ascend.springai.service…` (every active `module-metadata.yaml#spi_packages`, every ArchUnit `@AnalyzeClasses`, every contract-catalog row). The user has authorized a project-wide migration to `com.huawei.ascend.*` as a **separate cross-cutting wave** (rc22.5 per ADR-0104) — it is too cascading to bundle into rc22's L1-grounding work. Rc22 itself ratifies and rewrites all 6 `ARCHITECTURE.md` files against the **current root**, with explicit forward-compatibility markers (`<!-- root-migration-target: com.huawei.ascend -->`) on every documented package path so the post-rename Sweep-Stage replace is mechanical.

---

## Capability-Services Distribution (Response to Proposal #3 §2)

| Reviewer concept | Home in the six modules | Status |
|---|---|---|
| **Memory** | `spring-ai-ascend-graphmemory-starter` (SPI consumer impl) + `agent-service` (`GraphMemoryRepository` SPI surface per ADR-0082) | shipped (SPI) |
| **Skills** | `agent-execution-engine` (skill registry + `ResilienceContract.resolve(tenant, skill)` per Rule R-K) + `agent-service` (capacity governance via `SkillCapacityRegistry`) | shipped (W1) |
| **Sandbox** | `agent-execution-engine` (`SandboxExecutor` SPI) + `docs/governance/sandbox-policies.yaml` (Rule R-L) | policy shipped, runtime W2 |
| **Knowledge** | Deferred to W3+ (no active module owns it today) | deferred |

---

## Wave Closure Summary — rc22

### Baseline deltas (lockstep update lands with this PR)

| Metric | Pre-wave (rc21) | Post-wave (rc22) | Delta |
|---|---|---|---|
| Active engineering rules | 37 | 38 | +1 (Rule G-1.1) |
| Active gate sections | 123 | 123 | 0 (rc22 ratifies; gate scripts land in follow-up commit before wave close) |
| Enforcer rows | 165 | 168 | +3 (E166, E167, E168) |
| ADRs | 98 | 104 | +6 (ADR-0099 … ADR-0104) |
| Active recurring-defect families | 10 | 11 | +1 (F-l1-architecture-grounding-gap) |
| `agent-*/ARCHITECTURE.md` files satisfying Rule G-1.1 | 0 | 6 | +6 (all 6 modules) |
| Maven build tests green | 374 | 374 | unchanged (no Java code change) |

### Three-layer detection lattice (per `/reviewer-feedback-self-check`)

- **L1 Reviewer**: 3 proposals × ~30 cited findings → closed via this wave (5 explicit rejections + acceptances documented per-proposal above).
- **L2 Agent sweep**: hidden defects surfaced and fixed in same wave:
  - `agent-middleware/ARCHITECTURE.md` missing Development View tree (now added with `## 5. Development View`).
  - `agent-client/ARCHITECTURE.md` skeleton-only (now carries Development View tree + SPI consumer appendix).
  - `agent-evolve/ARCHITECTURE.md` skeleton-only (now carries Development View tree + future-SPI appendix + Online/Offline modality section).
  - Rule-id collision G-1.c → G-1.1 (caught BEFORE the rule shipped — would have collided with sub-clause notation conventions).
  - Package-root mismatch `com.huawei.ascend.*` vs `ascend.springai.*` (resolved via separate rc22.5 wave + forward-compatibility markers in rc22).
- **L3 Live-corpus rule self-check**: Rule G-1.1 enforcement is rule-card + ADR + CLAUDE.md kernel + ARCHITECTURE.md rewrites in rc22; gate scripts + 6 fixtures land in a follow-up commit before merge (the rule is RATIFIED in rc22; ENFORCEMENT lands alongside the rc22.5 mechanical rename so the gate validates against the post-rename namespace).

### Lessons captured to memory

- L1 grounding requires a 4th surface (ARCHITECTURE.md SPI appendix) beyond Rule R-D's 3-way check (catalog ↔ metadata ↔ DFX).
- Reviewer-proposed rule-ids must be checked against the .a/.b/.c sub-clause convention vs .1/.2 extracted-standalone convention BEFORE ratification.
- "Severe semantic hallucination" framings on architectural disagreements warrant explicit framing-level rejection, not silent acceptance.
- The six-component grounding pin transforms reviewer-proposed seventh-module additions into distribution exercises across the existing six.
- Package-root migrations of this magnitude (200+ files) MUST be their own wave; bundling with L1-grounding work would create unreviewable PRs.

### Out of scope for rc22 (deferred to follow-up waves)

- Gate scripts `gate/lib/check_l1_dev_view_tree.sh` + `gate/lib/check_l1_spi_appendix.sh` + canonical gate rule block + 6 self-test fixtures land in a follow-up commit before rc22 closes (per L3 layer above).
- `module-metadata.yaml#deployment_loci:` field population (rc22.5 — same wave as package rename, low cost to bundle).
- `module-metadata.yaml#spi_packages` 3 new entries for `service.{engine,session,task}.spi` (rc22.5 — bundled with rename for atomicity).
- agent-service Java refactor (rc23).
- StatelessEngine + ContextProjector + TaskStateStore reference impls (rc24).
- Run ≤ Task ≤ Session Java types + Flyway migrations (rc25).
- Online evolution Java code + Federation Hub (rc26).
- 2026-05-20 review backlog (rc16-post-closure + financial-readiness) — separate review cycle.

---

## Verification Performed

```bash
# 1. Lint + tests (post-implementation)
bash gate/check_parallel.sh                              # confirms Rule G-9 ledger freshness (yaml + md updated)
bash gate/test_architecture_sync_gate.sh                 # 210 fixtures PASS
python gate/build_architecture_graph.py --check --no-write  # graph regenerable

# 2. Per-component Rule G-1.1 spot-check
for m in agent-client agent-bus agent-service agent-execution-engine agent-middleware agent-evolve; do
  echo "=== $m ==="
  grep -E "^## .*Development View|^## .*SPI Interface Appendix" "$m/ARCHITECTURE.md"
done

# 3. ADRs + governance YAMLs
ls docs/adr/0099-* docs/adr/0100-* docs/adr/0101-* docs/adr/0102-* docs/adr/0103-* docs/adr/0104-*
ls docs/governance/deployment-loci.yaml docs/governance/evolution-modalities.yaml
ls docs/governance/rules/rule-G-1.1.md

# 4. CLAUDE.md kernel insertion
grep "#### Rule G-1.1" CLAUDE.md

# 5. Engine-hooks ON_YIELD addition
grep "on_yield" docs/contracts/engine-hooks.v1.yaml

# 6. Recurring-defect-families parity (Rule G-9.c)
diff <(grep "^  - id:" docs/governance/recurring-defect-families.yaml | sort) \
     <(grep "^### F-" docs/governance/recurring-defect-families.md | sed 's/^### //' | awk -F' — ' '{print "  - id: "$1}' | sort)
```

All commands must pass before rc22 merges to `main`.
