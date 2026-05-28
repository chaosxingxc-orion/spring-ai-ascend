# AgentService L1 v1.2 Absorption Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Absorb PR 92 (AgentService M1–M6 v1.2 design with 66 feature items, 6 boundary-decision reversals, and the messages-in-flight M6 prompt reversal) into the canonical L1 architecture at `architecture/docs/L1/agent-service/`, anchored by ADR-0155, with full SPI/contract parity restored.

**Architecture:** PR 92 lives as design-source under `docs/logs/reviews/` (already G-1.a compliant). This plan creates ADR-0155 as the decision anchor, then propagates the v1.2 deltas into the canonical L1 per-view files, the 6 `features/*.md` inventories (expanded from 8 to ~11 items each), the SPI appendix (7 new SPIs), the contract catalog (14 new contracts), the features DSL (14 new SAA Feature elements), and SPI Java placeholder files. The merge basepoint is `main` after PR 92 lands.

**Tech Stack:** Markdown + YAML + Structurizr DSL + Java (placeholder interfaces) + Bash gate scripts.

---

## Scope guardrails

The plan honours user-confirmed scope from the 2026-05-28 dialogue:

- **Basepoint**: PR 92 merged to `main` first; absorption PR branches off `main`.
- **Bigger range**: W1 skeleton + ALL 14 `*.v1.yaml` schema placeholders + COMPLETE SPI interface Java placeholders.
- **Process**: writing-plans → executing-plans / subagent-driven (user choice at hand-off).

**Out of scope for this PR** (deferred):

- TCK conformance suites for new SPIs.
- ArchUnit enforcers physically wiring the new SPI boundary rules.
- Java production implementations of the new SPIs (only `public interface` placeholders).
- L2 design documents for any subsystem.
- DSL `verification.dsl` edge additions for the 14 new contracts (verifying edges land when SPI shipped — these are `design_only`).

---

## File inventory

**ADR (new, 2 files):**

- Create: `docs/adr/0155-agent-service-l1-v1-2-internal-module-design.yaml`
- Create: `docs/adr/0155-agent-service-l1-v1-2-internal-module-design.md` (engineering rationale)

**L1 view files (modify, 7 files):**

- Modify: `architecture/docs/L1/agent-service/ARCHITECTURE.md` (authority list expand to include ADR-0155)
- Modify: `architecture/docs/L1/agent-service/logical.md` (§6 glossary M6 reversal, §7 RunEvent add `RESUME_ACCEPTED` variant + cross-link)
- Modify: `architecture/docs/L1/agent-service/process.md` (P3/O3 cancel-race amendment with CANCEL_RACE_RESOLVED_AS_*)
- Modify: `architecture/docs/L1/agent-service/physical.md` (new §1.x deployment-vs-code-ownership matrix)
- Modify: `architecture/docs/L1/agent-service/development.md` (new §x InjectionMode table)
- Modify: `architecture/docs/L1/agent-service/scenarios.md` (S6 Weather Clarification reference + crosslink)
- Modify: `architecture/docs/L1/agent-service/spi-appendix.md` (append §4 v1.2 SPI extensions, 7 new SPIs + 4-way parity table extension)

**features/ inventory files (modify, 6 files):**

- Modify: `architecture/docs/L1/agent-service/features/access-layer.md` (AS-L1-F01..F08 → AS-L1-F01..F12)
- Modify: `architecture/docs/L1/agent-service/features/session-task-manager.md` (AS-L2-F01..F08 → +F09, +F10)
- Modify: `architecture/docs/L1/agent-service/features/internal-event-queue.md` (extend to 11 features)
- Modify: `architecture/docs/L1/agent-service/features/task-centric-control.md` (extend to 11 features, including CANCEL_RACE rule + RESUME_ACCEPTED handler)
- Modify: `architecture/docs/L1/agent-service/features/engine-dispatch-execution.md` (extend to 11 features, including ExecutorAdapter SPI + InjectionMode + EDE-09 14 error classes)
- Modify: `architecture/docs/L1/agent-service/features/translation-tool-intercept.md` (extend to 11 features, including TTI-02 messages-in-flight reversal)

**Contract schemas (new, 14 files):**

- Create: `docs/contracts/access-intent.v1.yaml`
- Create: `docs/contracts/control-event.v1.yaml`
- Create: `docs/contracts/work-item.v1.yaml`
- Create: `docs/contracts/execution-request.v1.yaml`
- Create: `docs/contracts/agent-event.v1.yaml`
- Create: `docs/contracts/governed-messages.v1.yaml`
- Create: `docs/contracts/config-snapshot-ref.v1.yaml`
- Create: `docs/contracts/correlation-record.v1.yaml`
- Create: `docs/contracts/interrupt-registration.v1.yaml`
- Create: `docs/contracts/error-class.v1.yaml`
- Create: `docs/contracts/intercept-request.v1.yaml`
- Create: `docs/contracts/tool-result.v1.yaml`
- Create: `docs/contracts/checkpoint-record.v1.yaml`
- Create: `docs/contracts/session-snapshot.v1.yaml`

**Contract catalog (modify, 1 file):**

- Modify: `docs/contracts/contract-catalog.md` (append 14 rows to §3 YAML domain contracts; append 7 rows to §2 Java SPI interfaces)

**SPI Java placeholder files (new, 7 files):**

- Create: `agent-service/src/main/java/com/huawei/ascend/service/runtime/spi/executor/ExecutorAdapter.java`
- Create: `agent-service/src/main/java/com/huawei/ascend/service/runtime/spi/executor/InjectionMode.java` (enum)
- Create: `agent-service/src/main/java/com/huawei/ascend/service/runtime/spi/intercept/PlatformChatClient.java`
- Create: `agent-service/src/main/java/com/huawei/ascend/service/runtime/spi/intercept/PlatformToolCallback.java`
- Create: `agent-service/src/main/java/com/huawei/ascend/service/runtime/spi/intercept/PlatformMemoryProvider.java`
- Create: `agent-service/src/main/java/com/huawei/ascend/service/runtime/spi/intercept/PlatformRetriever.java`
- Create: `agent-service/src/main/java/com/huawei/ascend/service/runtime/spi/intercept/package-info.java`

(ExecutorAdapter + InjectionMode share one `package-info.java` which will also be created.)

- Create: `agent-service/src/main/java/com/huawei/ascend/service/runtime/spi/executor/package-info.java`

**Module metadata + DFX (modify, 2 files):**

- Modify: `agent-service/module-metadata.yaml` (append `service.runtime.spi.executor`, `service.runtime.spi.intercept` to `spi_packages`)
- Modify: `docs/dfx/agent-service.yaml` (mirror the 2 new spi_packages entries)

**Architecture DSL (modify, 2 files):**

- Modify: `architecture/features/features.dsl` (append 6 FEAT- elements — one per module)
- Modify: `architecture/features/function-points.dsl` (append 4 FP- elements covering A2A `message/send`, `tasks/cancel`, `tasks/resubscribe`, MQ inbound)

**Recurring defect families (modify, 2 files):**

- Modify: `docs/governance/recurring-defect-families.yaml` (new family `F-agent-service-internal-boundary-drift`)
- Modify: `docs/governance/recurring-defect-families.md` (mirror)

**Total**: 7 new ADR/Java + 14 new schemas + 24 modified files = **45 file touches**.

---

## Pre-merge action (one-shot, performed by user before plan executes)

This plan assumes PR 92 has been merged to `main`. Verify with:

```bash
gh.exe pr view 92 --json state
git fetch origin main
git log origin/main --oneline -3 | grep -q "c1ff5a1\|PR #92\|govern architecture/temp" && echo MERGED || echo NOT_MERGED
```

If `NOT_MERGED`, stop and merge PR 92 first (out of scope for this plan).

---

## Branch setup

Create the absorption branch off latest `main`:

```bash
git fetch origin main
git checkout main && git pull --ff-only origin main
git checkout -b feat/absorb-pr92-agent-service-l1-v1-2
```

Verify the two new review files from PR 92 exist on this branch:

```bash
test -f docs/logs/reviews/2026-05-28-agent-service-m1-m6-design-draft.cn.md && \
test -f docs/logs/reviews/2026-05-28-agent-service-design-self-audit.cn.md && \
echo OK || echo MISSING
```

---

### Task 1: ADR-0155 yaml (decision anchor)

**Files:**
- Create: `docs/adr/0155-agent-service-l1-v1-2-internal-module-design.yaml`

- [ ] **Step 1: Create the ADR yaml**

```yaml
id: ADR-0155
title: "AgentService L1 v1.2 — internal module design absorption (M1–M6 + 6 boundary reversals)"
status: accepted
date: 2026-05-28
level: L1
view: logical
affects_level: L1
affects_view: [logical, process, physical, development, scenarios]
authors:
  - x00209170
  - chao
extends:
  - ADR-0138    # rc53 5-layer L1 ratification
  - ADR-0140    # rc55 Engine Adapter Layer split into 5a + 5b
  - ADR-0141    # rc55 Internal Event Queue design_only
  - ADR-0142    # rc55 Run aggregate single owner
  - ADR-0143    # rc55 review-log demotion + L1 canonical move
  - ADR-0145    # rc55 sealed RunEvent hierarchy
  - ADR-0151    # L1 Feature Registry canonical schema
relates_to:
  - ADR-0100    # rc22 5-component decomposition
  - ADR-0128    # Agent SPI design_only
context: |
  PR 92 (merged 2026-05-28, head c1ff5a1) submitted two `.cn.md` design
  documents under `docs/logs/reviews/`:

    - `2026-05-28-agent-service-m1-m6-design-draft.cn.md` — 6 modules
      (M1 Access Layer / M2 STM / M3 IEQ / M4 TCC / M5 EDE / M6 TTI) +
      end-to-end Weather Clarification scenario, total 66 functional items
      and a complete inter-module data-contract matrix.
    - `2026-05-28-agent-service-design-self-audit.cn.md` — internal audit
      that closed 20 issues (HIGH 6 / MEDIUM 9 / LOW 5) and recorded two
      v1.x upgrades (v1.1 Agent-form interception strategy; v1.2 prompt
      construction reversal).

  Six of these adjustments are **boundary-decision reversals** with
  cross-module impact and MUST be anchored in an ADR rather than left
  implicit in a review log:

    1. STM-03 sole-caller: M4 TCC-03 is the only module allowed to
       invoke STM-03 (Run state transition CAS). Previously TTI-09 was
       drafted to call STM-03 directly when registering an interrupt;
       v1 audit H1 reverses this — TTI-09 now emits a control event,
       TCC-06A consumes it and drives the STM-03 transition.
    2. responseSnapshot lifetime owner: terminal-state response-snapshot
       writeback responsibility moves from M1 Access Layer (originally
       drafted as part of reply projection) to M4 TCC-03 as a post-
       transition hook for terminal-state targets {COMPLETED, FAILED,
       CANCELLED}. This eliminates the multi-reply-channel race that
       made M1 the wrong owner.
    3. M6 prompt-construction reversal (v1.2): M6 Translation &
       Tool-Intercept does NOT construct prompts. The v1 draft assumed
       M6 owned prompt assembly + context trimming as a platform
       service; the v1.2 reversal restores prompt assembly to the
       Agent itself (native code, third-party framework Formatter,
       or remote service) and re-scopes M6 to a "messages-in-flight"
       boundary-treatment aspect (policy, redaction, token-budget
       audit, fallback trim only). The data contract `BuiltPrompt` is
       deleted; `GovernedMessages` replaces it as M6's output type.
    4. ExecutorAdapter SPI three forms + in-process deployment
       assumption: M5 EDE-01..04 fixes the three Agent forms (Native /
       Third-party / Remote) and binds Native + Third-party to
       in-process deployment with platform-bean injection and bridge
       replacement respectively; Remote (EDE-04) is out-of-process via
       A2A protocol with internal model/tool calls beyond local M6
       jurisdiction.
    5. IEQ three-channel topology: Internal Event Queue physically
       splits into Control / Data / Egress channels, each with
       independent bounded buffer + worker pool + back-pressure
       policy. Cross-channel ordering is undefined; per-Run causality
       lives in STM-09 cursor.
    6. CANCEL_RACE_RESOLVED_AS_{COMPLETED, CANCELLED}: M4 TCC-03
       arbitrates the cancel-vs-complete race deterministically based
       on child-Run settlement and final-artifact presence. Reason
       codes land in RunEvent.

  PR 92 also introduces a new control event `RESUME_ACCEPTED` (M4
  TCC-06B / M5 EDE-07) closing the H3 finding that RESUMING→RUNNING
  had no termination signal, and extends the EDE-09 error class
  enumeration to 14 values (adding CONTEXT_OVERFLOW, ADAPTER_UNAVAILABLE,
  DEADLINE_EXCEEDED).

  The current L1 canonical (rc55) carries the 5-layer architecture but
  predates these six reversals and the 66 v1.2 functional items. Without
  this ADR, the canonical L1 falls out of sync with the merged design
  source under `docs/logs/reviews/`, breaking the G-1.a phase-released
  → review-log → canonical pipeline.
decision: |
  Adopt PR 92's M1–M6 v1.2 design as the canonical L1 architecture
  for agent-service, with the six boundary-decision reversals enumerated
  above as binding contracts at L1. Operationalise via:

    1. Update the 5 canonical 4+1 view files under
       `architecture/docs/L1/agent-service/` to reference this ADR
       as authority for the v1.2 deltas (M6 reversal, RESUME_ACCEPTED
       handler in P3 sequence, CANCEL_RACE_RESOLVED reasons in P3
       cancel-race-loser path, in-process/out-of-process deployment
       matrix in physical view, InjectionMode table in development
       view).
    2. Expand the 6 `features/*.md` inventories from the rc55 8-feature
       baseline (AS-L*-F01..F08) to ~11 features each, accommodating
       the v1.2 functional items. Backwards compatibility with rc55
       feature IDs is preserved by adding new items as F09, F10, F11
       rather than renumbering.
    3. Register 14 new YAML domain contracts under `docs/contracts/`
       (status `design_only`, cite ADR-0155) covering the inter-module
       data contracts that PR 92's self-audit §7 matrix lists as
       authoritative: AccessIntent, ControlEvent, WorkItem, RunEvent
       (already exists — extend variants), ExecutionRequest, AgentEvent,
       GovernedMessages, ConfigSnapshotRef, CorrelationRecord,
       InterruptRegistration, ErrorClass, InterceptRequest, ToolResult,
       CheckpointRecord, SessionSnapshot.
    4. Add 7 new SPI interfaces to the spi-appendix.md inventory with
       full 4-way parity per Rule G-1.1.b: ExecutorAdapter,
       InjectionMode (enum carrier, not counted in interface count),
       PlatformChatClient, PlatformToolCallback, PlatformMemoryProvider,
       PlatformRetriever. New packages
       `com.huawei.ascend.service.runtime.spi.executor` and
       `com.huawei.ascend.service.runtime.spi.intercept` join
       module-metadata.yaml#spi_packages and the DFX yaml.
    5. Register 6 new SAA Feature elements (one per module —
       FEAT-AS-ACCESS-LAYER through FEAT-AS-TRANSLATION-INTERCEPT) in
       `architecture/features/features.dsl` with `saa.status:
       design_only` and `saa.sourceAdr: ADR-0155`, plus 4 new
       FunctionPoints in `function-points.dsl` covering
       A2A message/send, tasks/cancel, tasks/resubscribe, and MQ
       inbound entry.
    6. Open a new recurring defect family
       `F-agent-service-internal-boundary-drift` documenting H1, H4,
       H5 from the self-audit (M4 sole-caller breach, responseSnapshot
       owner drift, REMOTE_AGENT_INVOKE_REQUEST cross-jurisdiction
       leak) so future reviewers detect and prevent recurrence.

  This ADR is the authoritative anchor: every file changed in the
  absorption PR cites ADR-0155 in its `authority:` front-matter or
  inline rationale section.

consequences:
  positive:
    - Single normative anchor for the v1.2 design — future audits and
      L2 designs cite ADR-0155 rather than discovering deltas from the
      review log alone.
    - 4-way parity (Rule G-1.1.b) restored: 7 new SPI interfaces land
      in module-metadata + contract-catalog + DFX + spi-appendix
      simultaneously in one PR, no temporal drift.
    - 14 new design_only YAML contracts unblock W2 impl-mode wave —
      schema placeholders give downstream module authors something to
      reference even before runtime enforcement.
    - F-agent-service-internal-boundary-drift family captures the H1
      / H4 / H5 patterns so the next module-level audit catches them
      early.
  negative:
    - 14 design_only schemas grow the catalog significantly; gate
      Rule R-D.f catalog-vs-metadata parity must be verified.
    - 6 features/*.md files grow by ~3 items each, expanding the rc55
      feature space — verification.dsl test_ref binding for the new
      features is deferred to W2 (acceptable since saa.status is
      design_only and Rule G-14 sub-clause .c only requires test FQNs
      for shipped features).
    - M6 prompt-reversal contradicts any in-flight Java prototype that
      treated M6 as a prompt constructor — but no such production code
      exists today (M6 is design_only per spi-appendix row 6 placeholder
      future).
  neutral:
    - PR 92's two `.cn.md` files remain in `docs/logs/reviews/` as
      design source; ADR-0155 is the structured decision capture.
verification:
  - "Gate: `bash gate/check_architecture_sync.sh` passes after merge."
  - "Spi-appendix 4-way parity audit: 7 new SPI FQN entries resolve in module-metadata.yaml, contract-catalog.md, DFX yaml, and on-disk .java files."
  - "Rule R-D.f catalog integrity: each new docs/contracts/*.v1.yaml entry appears in contract-catalog.md §3 and cites ADR-0155."
  - "Rule G-14 advisory: features.dsl 6 new FEAT- elements declare saa.status design_only + saa.aiBoundary.* properties."
  - "Rule G-15.d: function-points.dsl 4 new FP entries declare code_entrypoint_refs[] when channel=http (placeholder paths pointing to planned classes are acceptable for design_only)."
```

- [ ] **Step 2: Verify the yaml parses**

Run: `python -c "import yaml; yaml.safe_load(open('docs/adr/0155-agent-service-l1-v1-2-internal-module-design.yaml'))"`
Expected: no output (clean parse).

- [ ] **Step 3: Commit**

```bash
git add docs/adr/0155-agent-service-l1-v1-2-internal-module-design.yaml
git commit -m "docs(adr): ADR-0155 anchor PR 92 v1.2 absorption"
```

---

### Task 2: ADR-0155 markdown companion

**Files:**
- Create: `docs/adr/0155-agent-service-l1-v1-2-internal-module-design.md`

- [ ] **Step 1: Write the markdown companion**

```markdown
---
adr_id: ADR-0155
title: AgentService L1 v1.2 internal module design absorption
status: accepted
date: 2026-05-28
---

# ADR-0155 — AgentService L1 v1.2 internal module design absorption

This is the engineering-prose companion to
[`0155-agent-service-l1-v1-2-internal-module-design.yaml`](./0155-agent-service-l1-v1-2-internal-module-design.yaml).
The yaml is the structured authority; this file gives the reasoning trail
for the six boundary-decision reversals.

## 1. STM-03 sole-caller (TCC-03 owns transitions)

PR 92 self-audit finding H1 caught a cross-module breach: the v1 draft
of `TTI-09 (HITL interrupt gateway)` proposed calling STM-03 directly to
push the Run from RUNNING to SUSPENDED. This breaks the M4-sole-driver
constraint already declared by Rule R-C.2.b and the existing logical
view (rc55 §1, ADR-0142 Run aggregate single owner).

The reversal: TTI-09 emits a `ControlEvent { kind: INTERRUPT_REGISTERED,
payloadRef: InterruptRegistration }` onto IEQ-02 (control channel),
TCC-06A consumes it, validates current state == RUNNING, and drives the
STM-03 CAS itself. M4 remains the unique caller; no defence-in-depth
double-write path exists.

## 2. responseSnapshot owner (TCC-03 terminal-state hook)

Self-audit H4: the v1 draft made M1 Access Layer responsible for writing
the responseSnapshot back to STM-08 after delivering the terminal reply.
This created two structural defects:

- M1 is not a state-decision module; idempotent response-snapshot
  writeback is a "terminal-transition consequence" semantically owned by
  whoever drove the terminal transition.
- Multiple reply channels (SYNC_HTTP, SSE_STREAM, MQ_REPLY,
  PUSH_NOTIFICATION) make the "when M1 writes back" timing ambiguous.

Reversal: TCC-03, immediately after a successful CAS to {COMPLETED,
FAILED, CANCELLED}, synchronously (within the per-Run actor) calls
STM-08 to bind the response snapshot. M1's reply projection becomes a
pure read of the same snapshot.

## 3. M6 prompt-construction reversal (v1.2)

This is the deepest reversal. The v1 draft made M6 the canonical prompt
constructor — owning system-instruction assembly, history retrieval,
RAG chunk injection, tool-spec composition. Mid-design human review
(self-audit §11.1) raised: this is impossible for third-party frameworks
(AgentScope-java + LangGraph4j have their own Formatter chains) and
out-of-scope for remote agents (their prompt assembly is entirely
remote). Forcing M6 to be the constructor:

- Either makes M6 a universal Agent-template library (violates
  single-responsibility);
- Or makes Agent code call M6 with a "draft" that is already a
  fully-formed messages list (M6 just re-emits it).

The v1.2 resolution: M6 is a **messages-in-flight aspect**, analogous
to an HTTP API Gateway. Agents construct their own messages (native code
self-assembly / third-party Formatter / remote autonomy). M6 receives
the constructed messages at the model-call boundary and applies:

- TTI-02 boundary treatment (policy chain, PII redaction, token-budget
  audit, fallback trim with `BUDGET_FALLBACK_TRIM` audit event).
- TTI-03 vendor-adapter invocation + ContentBlock normalisation.

The `BuiltPrompt` contract is deleted. `GovernedMessages` replaces it as
M6's downstream output. Context assembly stays the Agent's job;
`PlatformMemoryProvider` is the read-only SPI Agents call to retrieve
STM-04 facts.

## 4. ExecutorAdapter three forms + in-process deployment

PR 92 EDE-02/03/04 binds three deployment shapes explicitly:

- **Native** (`EDE-02`, in-process): platform beans injected via DI;
  Agent code calls `PlatformChatClient.invoke(...)` synchronously into
  M6.
- **Third-party** (`EDE-03`, in-process): startup-time replacement of
  the third-party framework's `Model / Toolkit / Memory` abstractions
  with platform bridges; compliance scan refuses to register if
  bridges are incomplete.
- **Remote** (`EDE-04`, out-of-process): A2A protocol client; remote
  agent's internal resource calls (`model / tool / memory`) are NOT
  intercepted by local M6 — they happen in the remote process and are
  outside this jurisdiction. M6 only audits A2A outbound messages at
  TTI-08 policy chain.

Code ownership and deployment topology are now declared as **orthogonal
dimensions** (a self-developed agent CAN be deployed remotely; that
combination uses EDE-04). `EDE-08 InjectionMode { NATIVE_DI |
THIRD_PARTY_BRIDGE | EVENT_RELAY | NONE }` captures the wiring choice.

## 5. IEQ three-channel topology

Internal Event Queue is physically partitioned into three independent
channels:

- **Control**: `AccessIntent` from M1, `cancel / resume / callback /
  deadline-fired / interrupt-registered / resume-accepted / spawn-child`
  produced by sibling modules.
- **Data**: `WorkItem` carrying `engine-tick / tool-invoke /
  child-run-start / checkpoint / resume-tick`.
- **Egress**: per-Run topic for outward projection (`StateChanged /
  token / tool-progress / artifact / input-required / terminal`).

Each channel has its own bounded buffer + worker pool + back-pressure
policy. Cross-channel ordering is undefined; per-Run causality lives in
STM-09 monotonic cursor.

## 6. CANCEL_RACE_RESOLVED_AS_{COMPLETED, CANCELLED}

Self-audit M3: the v1 draft was silent on what happens when CANCEL
arrives while the Run is mid-`WORKITEM_DONE`. v1.2 rules:

- If child Runs are still unsettled: stay in `CANCEL_REQUESTED`, wait
  for child Run settlement, then `CANCEL_REQUESTED → CANCELLED`.
- If no children and the in-flight `WORKITEM_DONE` carries a complete
  final artifact: take `COMPLETED` (user already received the result).
  RunEvent reason = `CANCEL_RACE_RESOLVED_AS_COMPLETED`.
- If no children and the artifact is partial: take `CANCELLED`.
  RunEvent reason = `CANCEL_RACE_RESOLVED_AS_CANCELLED`.

## Cross-links

- PR 92 design source: [`docs/logs/reviews/2026-05-28-agent-service-m1-m6-design-draft.cn.md`](../logs/reviews/2026-05-28-agent-service-m1-m6-design-draft.cn.md).
- PR 92 self-audit: [`docs/logs/reviews/2026-05-28-agent-service-design-self-audit.cn.md`](../logs/reviews/2026-05-28-agent-service-design-self-audit.cn.md).
- Canonical L1 logical view (post-absorption): [`architecture/docs/L1/agent-service/logical.md`](../../architecture/docs/L1/agent-service/logical.md).
```

- [ ] **Step 2: Commit**

```bash
git add docs/adr/0155-agent-service-l1-v1-2-internal-module-design.md
git commit -m "docs(adr): ADR-0155 markdown companion (v1.2 reversal rationale)"
```

---

### Task 3: SPI Java placeholder — ExecutorAdapter package

**Files:**
- Create: `agent-service/src/main/java/com/huawei/ascend/service/runtime/spi/executor/package-info.java`
- Create: `agent-service/src/main/java/com/huawei/ascend/service/runtime/spi/executor/ExecutorAdapter.java`
- Create: `agent-service/src/main/java/com/huawei/ascend/service/runtime/spi/executor/InjectionMode.java`

- [ ] **Step 1: Create the package directory**

```bash
mkdir -p agent-service/src/main/java/com/huawei/ascend/service/runtime/spi/executor
```

- [ ] **Step 2: Write package-info.java**

```java
/**
 * Executor Adapter SPI for the agent-service runtime layer.
 *
 * <p>Authority: ADR-0155 (AgentService L1 v1.2 internal module design absorption).
 * Status at this commit: design_only — interface declared, no production
 * implementation. Reference impls land in a follow-up impl-mode wave.</p>
 *
 * <p>The {@link com.huawei.ascend.service.runtime.spi.executor.ExecutorAdapter}
 * SPI unifies three Agent forms — Native (in-process platform-bean DI),
 * Third-party (in-process framework-bridge replacement), and Remote
 * (out-of-process A2A protocol client) — behind a single execute contract.
 * {@link com.huawei.ascend.service.runtime.spi.executor.InjectionMode}
 * captures the wiring choice an adapter declares at registration time.</p>
 */
package com.huawei.ascend.service.runtime.spi.executor;
```

- [ ] **Step 3: Write ExecutorAdapter.java**

```java
package com.huawei.ascend.service.runtime.spi.executor;

import java.util.concurrent.Flow;

/**
 * Executor Adapter SPI. Unifies the execute contract across Native,
 * Third-party, and Remote Agent forms.
 *
 * <p>Authority: ADR-0155. Status: design_only at this commit.</p>
 */
public interface ExecutorAdapter {

    /**
     * The wiring mode this adapter uses for resource-call interception.
     */
    InjectionMode injectionMode();

    /**
     * Execute the given request and produce a stream of AgentEvent values.
     *
     * <p>The publisher is non-blocking; the adapter must not perform
     * blocking I/O in the subscription thread. Resource calls
     * (model / tool / memory / RAG / client-hosted skill) MUST go
     * through the M6 intercept SPIs ({@code PlatformChatClient},
     * {@code PlatformToolCallback}, etc.); the adapter MUST NOT call
     * vendor SDKs directly.</p>
     *
     * @param request immutable execution request descriptor; the schema
     *                lives at {@code docs/contracts/execution-request.v1.yaml}
     *                and the carrier record is currently a Java placeholder
     *                (W2 lands the concrete record).
     * @return a non-null publisher of agent events; the schema lives at
     *                {@code docs/contracts/agent-event.v1.yaml}.
     */
    Flow.Publisher<Object> execute(Object request);
    // NOTE: Object/Object placeholder types — the concrete ExecutionRequest
    // and AgentEvent carriers ship with W2 contract-record materialisation.
    // This SPI is design_only at the ADR-0155 anchor commit.
}
```

- [ ] **Step 4: Write InjectionMode.java**

```java
package com.huawei.ascend.service.runtime.spi.executor;

/**
 * Wiring choice an {@link ExecutorAdapter} declares at registration.
 *
 * <p>Authority: ADR-0155 §4. Status: design_only.</p>
 */
public enum InjectionMode {

    /** Native Agent — DI-injected platform beans (PlatformChatClient etc). */
    NATIVE_DI,

    /** Third-party framework — Memory / Model / Toolkit abstractions replaced by platform bridges. */
    THIRD_PARTY_BRIDGE,

    /** Asynchronous interrupt / approval relayed through the IEQ event channel. */
    EVENT_RELAY,

    /** Remote Agent — out-of-process via A2A; local M6 interception N/A. */
    NONE
}
```

- [ ] **Step 5: Commit**

```bash
git add agent-service/src/main/java/com/huawei/ascend/service/runtime/spi/executor/
git commit -m "feat(agent-service): ExecutorAdapter SPI + InjectionMode placeholder per ADR-0155"
```

---

### Task 4: SPI Java placeholder — Platform intercept package

**Files:**
- Create: `agent-service/src/main/java/com/huawei/ascend/service/runtime/spi/intercept/package-info.java`
- Create: `agent-service/src/main/java/com/huawei/ascend/service/runtime/spi/intercept/PlatformChatClient.java`
- Create: `agent-service/src/main/java/com/huawei/ascend/service/runtime/spi/intercept/PlatformToolCallback.java`
- Create: `agent-service/src/main/java/com/huawei/ascend/service/runtime/spi/intercept/PlatformMemoryProvider.java`
- Create: `agent-service/src/main/java/com/huawei/ascend/service/runtime/spi/intercept/PlatformRetriever.java`

- [ ] **Step 1: Create directory + package-info.java**

```bash
mkdir -p agent-service/src/main/java/com/huawei/ascend/service/runtime/spi/intercept
```

Write `package-info.java`:

```java
/**
 * Platform Resource Interception SPI surface for M6 Translation &amp;
 * Tool-Intercept.
 *
 * <p>Authority: ADR-0155 §3 (M6 prompt-construction reversal). Status:
 * design_only at this commit. Native Agents inject these beans via DI;
 * Third-party adapters wrap their framework's Model / Toolkit / Memory
 * abstractions around these contracts.</p>
 */
package com.huawei.ascend.service.runtime.spi.intercept;
```

- [ ] **Step 2: Write PlatformChatClient.java**

```java
package com.huawei.ascend.service.runtime.spi.intercept;

import java.util.concurrent.Flow;

/**
 * Model-call interception entry point used by in-process Agents.
 *
 * <p>The Agent constructs its own messages list and invokes this SPI
 * with the constructed messages and a model reference. M6 applies
 * TTI-02 boundary treatment (policy, redaction, token-budget audit,
 * fallback trim), routes to the vendor adapter via TTI-10, and
 * returns a normalised ContentBlock stream.</p>
 *
 * <p>Authority: ADR-0155. Status: design_only.</p>
 */
public interface PlatformChatClient {

    /**
     * Invoke a model with Agent-constructed messages.
     *
     * @param messagesRef reference to the Agent's constructed messages
     *                    (schema: {@code docs/contracts/governed-messages.v1.yaml}
     *                    after TTI-02 treatment; pre-treatment shape lives
     *                    inside the contract).
     * @param modelRef    canonical model identifier resolved via TTI-10.
     * @return publisher of ContentBlock chunks for streaming models, or a
     *                    one-element publisher for non-streaming models.
     */
    Flow.Publisher<Object> invoke(Object messagesRef, String modelRef);
}
```

- [ ] **Step 3: Write PlatformToolCallback.java**

```java
package com.huawei.ascend.service.runtime.spi.intercept;

/**
 * Tool-call interception entry point.
 *
 * <p>Receives a TOOL_REQUEST (schema: {@code docs/contracts/intercept-request.v1.yaml}),
 * normalises the tool schema, validates inputs, applies the TTI-08 policy
 * chain, executes via the registered ToolProvider, and returns a
 * normalised ToolResult.</p>
 *
 * <p>Authority: ADR-0155. Status: design_only.</p>
 */
public interface PlatformToolCallback {

    /**
     * Execute a tool call subject to platform policy.
     *
     * @param toolRef canonical tool identifier resolved via TTI-10.
     * @param inputJson JSON-encoded tool input matching the registered schema.
     * @return tool result (schema: {@code docs/contracts/tool-result.v1.yaml}).
     */
    Object invoke(String toolRef, String inputJson);
}
```

- [ ] **Step 4: Write PlatformMemoryProvider.java**

```java
package com.huawei.ascend.service.runtime.spi.intercept;

/**
 * Read-only Session-context provider used by Agents to retrieve STM-04
 * facts before constructing prompts.
 *
 * <p>This SPI replaces the v1-draft assumption that M6 would read STM-04
 * and inject history into prompts. Per ADR-0155 §3, Agents read STM-04
 * via this SPI and assemble their own messages list.</p>
 *
 * <p>Authority: ADR-0155. Status: design_only.</p>
 */
public interface PlatformMemoryProvider {

    /**
     * Read a Session snapshot at-or-after the given version cursor.
     *
     * @param sessionId tenant-scoped session identifier.
     * @param fromVersion lower-bound version cursor (inclusive); 0 reads from the head.
     * @return session snapshot (schema: {@code docs/contracts/session-snapshot.v1.yaml}).
     */
    Object readSnapshot(String sessionId, long fromVersion);
}
```

- [ ] **Step 5: Write PlatformRetriever.java**

```java
package com.huawei.ascend.service.runtime.spi.intercept;

/**
 * RAG retrieval interception entry point.
 *
 * <p>Per ADR-0155 §3, M6 does NOT inject retrieved chunks into prompts.
 * It returns chunk references; the Agent decides whether, where, and
 * how to inject them into its constructed messages.</p>
 *
 * <p>Authority: ADR-0155. Status: design_only.</p>
 */
public interface PlatformRetriever {

    /**
     * Retrieve chunk references from the named index.
     *
     * @param query natural-language query.
     * @param indexRef canonical index identifier.
     * @param topK number of chunks to retrieve.
     * @return retrieval result containing chunk references; the Agent
     *         decides if/how to use them.
     */
    Object retrieve(String query, String indexRef, int topK);
}
```

- [ ] **Step 6: Commit**

```bash
git add agent-service/src/main/java/com/huawei/ascend/service/runtime/spi/intercept/
git commit -m "feat(agent-service): platform intercept SPIs (ChatClient/ToolCallback/MemoryProvider/Retriever) per ADR-0155"
```

---

### Task 5: Contract schemas — first batch (5 carriers)

**Files:**
- Create: `docs/contracts/access-intent.v1.yaml`
- Create: `docs/contracts/control-event.v1.yaml`
- Create: `docs/contracts/work-item.v1.yaml`
- Create: `docs/contracts/execution-request.v1.yaml`
- Create: `docs/contracts/agent-event.v1.yaml`

Each schema follows the existing pattern of `docs/contracts/run-event.v1.yaml` (header comment + `schema`/`authority`/`status`/`runtime_enforced` fields + body).

- [ ] **Step 1: Write access-intent.v1.yaml**

```yaml
# AccessIntent inter-module data contract, version 1.
#
# Authority: ADR-0155 (AgentService L1 v1.2 absorption).
# Wave: design_only at this commit; runtime_enforced when M1 Access
# Layer's normalisation step ships the Java record carrier.
#
# Purpose: canonical normalised request shape produced by M1 AL-03 and
# consumed by AL-04 (governance), TCC-01 (control dispatch), and STM
# (for sessionHint / taskHint resolution). A2A and MQ ingress both
# converge to this type before crossing module boundaries.

schema: access-intent/v1
authority: ADR-0155
status: design_only
runtime_enforced: false

fields:
  operation:
    type: enum
    values: [SUBMIT, RESUME, CANCEL, QUERY, SUBSCRIBE, CALLBACK]
    required: true
  tenantId:
    type: string
    required: true
  principal:
    type: object
    required: true
    description: "Authenticated principal (subject + roles + claims)"
  sessionHint:
    type: string
    required: false
    description: "Optional sessionId hint; null means M4 TCC-04 will resolve / create."
  taskHint:
    type: string
    required: false
  runHint:
    type: string
    required: false
    description: "Required for non-SUBMIT operations; SUBMIT assigns runId in TCC-04."
  payload:
    type: object
    required: true
    description: "Operation-specific payload; multimodal payloads carry references not bytes."
  replyChannel:
    type: enum
    values: [SYNC_HTTP, SSE_STREAM, MQ_REPLY, PUSH_NOTIFICATION]
    required: true
  deadline:
    type: timestamp
    required: false
  traceCtx:
    type: object
    required: true
    description: "W3C TraceContext or B3 carrier."
  idempotencyKey:
    type: string
    required: false
    description: "Required for SUBMIT and CALLBACK to participate in STM-08 replay decision."
  clientProfileRef:
    type: string
    required: false
```

- [ ] **Step 2: Write control-event.v1.yaml**

```yaml
# ControlEvent IEQ-02 control-channel envelope, version 1.
#
# Authority: ADR-0155.
# Wave: design_only; runtime_enforced when M3 IEQ ships the Java
# envelope record + M4 control-channel consumer.

schema: control-event/v1
authority: ADR-0155
status: design_only
runtime_enforced: false

fields:
  kind:
    type: enum
    values:
      - SUBMIT
      - CANCEL
      - RESUME
      - CALLBACK
      - INTERRUPT_REGISTERED    # produced by M6 TTI-09 (post-H1 reversal)
      - DEADLINE_FIRED
      - CHILD_DONE
      - WORKITEM_DONE
      - WORKITEM_FAILED
      - RESUME_ACCEPTED         # new in v1.2; produced by M5 EDE-07 (per H3)
      - SPAWN_CHILD
    required: true
  tenantId:
    type: string
    required: true
  runHint:
    type: string
    required: false
    description: "Run identifier; null only for SUBMIT (TCC-04 assigns)."
  payloadRef:
    type: string
    required: false
    description: "STM reference to the kind-specific payload."
  traceCtx:
    type: object
    required: true
  deadline:
    type: timestamp
    required: false
  enqueuedAt:
    type: timestamp
    required: true
    description: "Monotonic clock at enqueue; consumers MUST NOT rely on wall-clock ordering across channels."
```

- [ ] **Step 3: Write work-item.v1.yaml**

```yaml
# WorkItem IEQ-03 data-channel envelope, version 1.
#
# Authority: ADR-0155.
# Wave: design_only.

schema: work-item/v1
authority: ADR-0155
status: design_only
runtime_enforced: false

fields:
  runId:
    type: string
    required: true
  kind:
    type: enum
    values: [ENGINE_TICK, TOOL_INVOKE, CHILD_RUN_START, CHECKPOINT, RESUME_TICK]
    required: true
  payloadRef:
    type: string
    required: true
    description: "STM reference to the kind-specific payload."
  configSnapshotRef:
    type: string
    required: true
    description: "Reference to the immutable ConfigSnapshot bound to this Run at creation."
  parentCursor:
    type: object
    required: false
    description: "Optional StmContextCursor (sessionId + version) for RESUME_TICK; lets the adapter know which session version to continue from."
  enqueuedAt:
    type: timestamp
    required: true
```

- [ ] **Step 4: Write execution-request.v1.yaml**

```yaml
# ExecutionRequest carrier consumed by ExecutorAdapter.execute, version 1.
#
# Authority: ADR-0155.
# Wave: design_only.

schema: execution-request/v1
authority: ADR-0155
status: design_only
runtime_enforced: false

fields:
  runId:
    type: string
    required: true
  agentSpecRef:
    type: string
    required: true
    description: "Reference to the immutable AgentSpec resolved by EDE-05."
  configSnapshotRef:
    type: string
    required: true
  sessionContextRef:
    type: object
    required: true
    description: "(sessionId, version) tuple; adapter MUST read STM-04 only through PlatformMemoryProvider."
  startCheckpointRef:
    type: string
    required: false
    description: "If non-null, resume from this checkpoint instead of cold-starting."
  inputPayloadRef:
    type: string
    required: true
  capabilityHints:
    type: object
    required: false
```

- [ ] **Step 5: Write agent-event.v1.yaml**

```yaml
# AgentEvent stream emitted by ExecutorAdapter.execute, version 1.
#
# Authority: ADR-0155.
# Wave: design_only. NOT to be confused with the sealed RunEvent
# hierarchy in run-event.v1.yaml — AgentEvent is the adapter-internal
# emission stream; RunEvent is the platform-internal projection stream
# emitted on the IEQ-04 egress channel after STM-09 cursor assignment.

schema: agent-event/v1
authority: ADR-0155
status: design_only
runtime_enforced: false

fields:
  kind:
    type: enum
    values:
      - TOKEN
      - STEP
      - TOOL_REQUEST
      - MEMORY_REQUEST
      - MODEL_REQUEST
      - INTERRUPT_REQUEST
      - CHECKPOINT_HINT
      - FINISHED
      - FAILED
    required: true
  runId:
    type: string
    required: true
  payloadRef:
    type: string
    required: false
  errorClass:
    type: string
    required: false
    description: "When kind=FAILED; one of error-class.v1.yaml enum values."
```

- [ ] **Step 6: Parse all 5 with yaml**

```bash
for f in docs/contracts/access-intent.v1.yaml docs/contracts/control-event.v1.yaml docs/contracts/work-item.v1.yaml docs/contracts/execution-request.v1.yaml docs/contracts/agent-event.v1.yaml; do
  python -c "import yaml; yaml.safe_load(open('$f'))" || echo "FAIL: $f"
done
```

Expected: no FAIL lines.

- [ ] **Step 7: Commit**

```bash
git add docs/contracts/access-intent.v1.yaml docs/contracts/control-event.v1.yaml docs/contracts/work-item.v1.yaml docs/contracts/execution-request.v1.yaml docs/contracts/agent-event.v1.yaml
git commit -m "feat(contracts): 5 v1.2 inter-module schemas (AccessIntent/ControlEvent/WorkItem/ExecutionRequest/AgentEvent) per ADR-0155"
```

---

### Task 6: Contract schemas — second batch (5 governance + state carriers)

**Files:**
- Create: `docs/contracts/governed-messages.v1.yaml`
- Create: `docs/contracts/config-snapshot-ref.v1.yaml`
- Create: `docs/contracts/correlation-record.v1.yaml`
- Create: `docs/contracts/interrupt-registration.v1.yaml`
- Create: `docs/contracts/error-class.v1.yaml`

- [ ] **Step 1: Write governed-messages.v1.yaml**

```yaml
# GovernedMessages — M6 TTI-02 output (replaces v1-draft BuiltPrompt), v1.
#
# Authority: ADR-0155 §3 (M6 prompt-reversal).
# Wave: design_only.

schema: governed-messages/v1
authority: ADR-0155
status: design_only
runtime_enforced: false

fields:
  messages:
    type: array
    required: true
    description: "List of typed Msg entries (role + ContentBlock list). M6 may have applied fallback trim; original message references preserved in masks/denials."
  toolSpecs:
    type: array
    required: false
  budgetSnapshot:
    type: object
    required: true
    description: "Token-budget audit record (model context window, estimated input tokens, output cap, headroom)."
  masks:
    type: array
    required: false
    description: "Field-level PII redaction records (path + reason)."
  denials:
    type: array
    required: false
    description: "Policy-denial markers when partial allow + partial deny is in effect."
```

- [ ] **Step 2: Write config-snapshot-ref.v1.yaml**

```yaml
# ConfigSnapshotRef — immutable Run-time configuration binding reference, v1.
#
# Authority: ADR-0155.
# Wave: design_only.

schema: config-snapshot-ref/v1
authority: ADR-0155
status: design_only
runtime_enforced: false

fields:
  snapshotId:
    type: string
    required: true
  hash:
    type: string
    required: true
    description: "Content hash; used as M5 EDE-06 cache-equality key alongside agentId."
  modelBindings:
    type: object
    required: true
  toolBindings:
    type: object
    required: true
  adapterBindings:
    type: object
    required: true
  policyBindings:
    type: object
    required: true
  createdAt:
    type: timestamp
    required: true
```

- [ ] **Step 3: Write correlation-record.v1.yaml**

```yaml
# CorrelationRecord — cross-Run / remote-Agent handle, v1.
#
# Authority: ADR-0155.
# Wave: design_only.

schema: correlation-record/v1
authority: ADR-0155
status: design_only
runtime_enforced: false

fields:
  runId:
    type: string
    required: true
  handleType:
    type: enum
    values: [LOCAL_CHILD_RUN, REMOTE_AGENT]
    required: true
  handle:
    type: object
    required: true
    description: "Discriminated union: LocalChildHandle{childRunId} | RemoteAgentHandle{remoteAgentId, remoteTaskId, remoteThreadId, callbackId}"
  status:
    type: enum
    values: [PENDING, ACTIVE, SETTLED]
    required: true
    description: "PENDING when M4 creates empty skeleton; ACTIVE after M5 fills remote handle; SETTLED on child terminate."
```

- [ ] **Step 4: Write interrupt-registration.v1.yaml**

```yaml
# InterruptRegistration — HITL interrupt site descriptor, v1.
#
# Authority: ADR-0155 §1 (H1 reversal — TTI-09 emits this, TCC-06A
# consumes via ControlEvent.payloadRef).
# Wave: design_only.

schema: interrupt-registration/v1
authority: ADR-0155
status: design_only
runtime_enforced: false

fields:
  runId:
    type: string
    required: true
  callbackId:
    type: string
    required: true
  interruptKind:
    type: enum
    values: [USER_INPUT, APPROVAL, CLIENT_HOSTED_SKILL]
    required: true
  schemaHint:
    type: object
    required: false
    description: "Optional JSON Schema describing the expected callback payload shape."
  promptToUserRef:
    type: string
    required: false
    description: "STM reference to the human-facing prompt content."
  registeredAt:
    type: timestamp
    required: true
```

- [ ] **Step 5: Write error-class.v1.yaml**

```yaml
# ErrorClass — platform-wide error taxonomy enum, v1.
#
# Authority: ADR-0155. Status: design_only.
# Per self-audit H6, EDE-09 is the single authoritative source — M4
# TCC-07, M6 TTI policy denials, and all adapter failures route to one
# of these 14 values. Modules MUST NOT invent additional classes.

schema: error-class/v1
authority: ADR-0155
status: design_only
runtime_enforced: false

values:
  - MODEL_UNAVAILABLE
  - MODEL_RATE_LIMITED
  - INPUT_VALIDATION_ERROR
  - POLICY_DENIED
  - TOOL_FAILED
  - TOOL_TIMEOUT
  - MEMORY_FAILED
  - RETRIEVAL_FAILED
  - REMOTE_PROTOCOL_ERROR
  - CLIENT_CALLBACK_FAILED
  - INTERNAL_ERROR
  - CONTEXT_OVERFLOW          # new in v1.2 per self-audit H6
  - ADAPTER_UNAVAILABLE       # new in v1.2 per self-audit H6
  - DEADLINE_EXCEEDED         # new in v1.2 per self-audit H6
```

- [ ] **Step 6: Parse + commit**

```bash
for f in docs/contracts/governed-messages.v1.yaml docs/contracts/config-snapshot-ref.v1.yaml docs/contracts/correlation-record.v1.yaml docs/contracts/interrupt-registration.v1.yaml docs/contracts/error-class.v1.yaml; do
  python -c "import yaml; yaml.safe_load(open('$f'))" || echo "FAIL: $f"
done

git add docs/contracts/governed-messages.v1.yaml docs/contracts/config-snapshot-ref.v1.yaml docs/contracts/correlation-record.v1.yaml docs/contracts/interrupt-registration.v1.yaml docs/contracts/error-class.v1.yaml
git commit -m "feat(contracts): 5 more v1.2 schemas (GovernedMessages/ConfigSnapshotRef/CorrelationRecord/InterruptRegistration/ErrorClass) per ADR-0155"
```

---

### Task 7: Contract schemas — third batch (4 remaining)

**Files:**
- Create: `docs/contracts/intercept-request.v1.yaml`
- Create: `docs/contracts/tool-result.v1.yaml`
- Create: `docs/contracts/checkpoint-record.v1.yaml`
- Create: `docs/contracts/session-snapshot.v1.yaml`

- [ ] **Step 1: Write intercept-request.v1.yaml**

```yaml
# InterceptRequest — TTI-01 unified intercept entry envelope, v1.
#
# Authority: ADR-0155.
# Wave: design_only.

schema: intercept-request/v1
authority: ADR-0155
status: design_only
runtime_enforced: false

fields:
  runId:
    type: string
    required: true
  kind:
    type: enum
    values: [MODEL, TOOL, MEMORY, RAG, CLIENT_HOSTED_SKILL, INTERRUPT]
    required: true
  payloadRef:
    type: string
    required: true
  sessionContextRef:
    type: object
    required: true
  configSnapshotRef:
    type: string
    required: true
  traceCtx:
    type: object
    required: true
```

- [ ] **Step 2: Write tool-result.v1.yaml**

```yaml
# ToolResult — normalised tool-invocation result, v1.
#
# Authority: ADR-0155. Wave: design_only.

schema: tool-result/v1
authority: ADR-0155
status: design_only
runtime_enforced: false

fields:
  contentBlocks:
    type: array
    required: true
    description: "Platform-normalised ContentBlock list; never carries vendor SDK objects."
  success:
    type: boolean
    required: true
  errorClass:
    type: string
    required: false
    description: "One of error-class.v1.yaml values when success=false."
  usage:
    type: object
    required: false
    description: "Tool-specific usage metrics (latency, units consumed)."
```

- [ ] **Step 3: Write checkpoint-record.v1.yaml**

```yaml
# CheckpointRecord — STM-05 recoverable boundary marker, v1.
#
# Authority: ADR-0155. Wave: design_only.

schema: checkpoint-record/v1
authority: ADR-0155
status: design_only
runtime_enforced: false

fields:
  runId:
    type: string
    required: true
  checkpointId:
    type: string
    required: true
  createdAt:
    type: timestamp
    required: true
  nodeKey:
    type: string
    required: true
    description: "Engine-side schema key identifying the boundary node."
  engineStateRef:
    type: string
    required: true
  sideEffect:
    type: enum
    values: [NONE, PARTIAL, COMMITTED]
    required: true
    description: "Side-effect commit status; COMMITTED checkpoints are NOT valid retry restart points absent explicit policy."
  recoveryPolicy:
    type: string
    required: false
    description: "Optional policy override identifier."
```

- [ ] **Step 4: Write session-snapshot.v1.yaml**

```yaml
# SessionSnapshot — STM-04 read projection, v1.
#
# Authority: ADR-0155 §3 (PlatformMemoryProvider return shape).
# Wave: design_only.

schema: session-snapshot/v1
authority: ADR-0155
status: design_only
runtime_enforced: false

fields:
  sessionId:
    type: string
    required: true
  version:
    type: integer
    required: true
    description: "Monotonic version cursor; STM-04 is append-only."
  messages:
    type: array
    required: true
    description: "Typed Msg entries with role + ContentBlock list."
  variables:
    type: object
    required: false
  memoryRefs:
    type: array
    required: false
  toolSummaries:
    type: array
    required: false
```

- [ ] **Step 5: Parse + commit**

```bash
for f in docs/contracts/intercept-request.v1.yaml docs/contracts/tool-result.v1.yaml docs/contracts/checkpoint-record.v1.yaml docs/contracts/session-snapshot.v1.yaml; do
  python -c "import yaml; yaml.safe_load(open('$f'))" || echo "FAIL: $f"
done

git add docs/contracts/intercept-request.v1.yaml docs/contracts/tool-result.v1.yaml docs/contracts/checkpoint-record.v1.yaml docs/contracts/session-snapshot.v1.yaml
git commit -m "feat(contracts): 4 remaining v1.2 schemas (InterceptRequest/ToolResult/CheckpointRecord/SessionSnapshot) per ADR-0155"
```

---

### Task 8: Contract catalog — append 14 YAML contract rows + 7 SPI rows

**Files:**
- Modify: `docs/contracts/contract-catalog.md` (§2 SPI table + §3 YAML domain contract table)

- [ ] **Step 1: Append 7 SPI rows to §2 (after the last existing row, before §3 header)**

Use Edit to append 7 new rows to the §2 SPI table. Open the file, locate the last row of §2, append:

```markdown
| `com.huawei.ascend.service.runtime.spi.executor.ExecutorAdapter` | `service.runtime.spi.executor` | design_only | Layer 5a Engine Dispatch | ADR-0155 |
| `com.huawei.ascend.service.runtime.spi.intercept.PlatformChatClient` | `service.runtime.spi.intercept` | design_only | Layer 5b Translation & Tool-Intercept (Native + Third-party adapters consume) | ADR-0155 |
| `com.huawei.ascend.service.runtime.spi.intercept.PlatformToolCallback` | `service.runtime.spi.intercept` | design_only | Layer 5b Translation & Tool-Intercept | ADR-0155 |
| `com.huawei.ascend.service.runtime.spi.intercept.PlatformMemoryProvider` | `service.runtime.spi.intercept` | design_only | Layer 5b Translation & Tool-Intercept (read-only STM-04 view) | ADR-0155 |
| `com.huawei.ascend.service.runtime.spi.intercept.PlatformRetriever` | `service.runtime.spi.intercept` | design_only | Layer 5b Translation & Tool-Intercept | ADR-0155 |
```

(Five rows — `InjectionMode` is an enum carrier per Rule R-D.d carve-out and goes into the `2. SPI-adjacent structural carriers` table instead, not the §2 interfaces table.)

- [ ] **Step 2: Append 14 rows to §3 (YAML domain contracts)**

Open `docs/contracts/contract-catalog.md`, locate §3 table, append rows. Match the existing `run-event.v1.yaml` row format. The new rows are:

```markdown
| `access-intent.v1.yaml` | `docs/contracts/` | `design_only` | ADR-0155 (M1 AL-03 normalised request shape; converges A2A + MQ ingress before crossing module boundaries) |
| `control-event.v1.yaml` | `docs/contracts/` | `design_only` | ADR-0155 (IEQ-02 envelope; includes RESUME_ACCEPTED + INTERRUPT_REGISTERED kinds from v1.2 reversal) |
| `work-item.v1.yaml` | `docs/contracts/` | `design_only` | ADR-0155 (IEQ-03 envelope carrying engine-tick / tool-invoke / resume-tick payload refs) |
| `execution-request.v1.yaml` | `docs/contracts/` | `design_only` | ADR-0155 (ExecutorAdapter.execute input carrier) |
| `agent-event.v1.yaml` | `docs/contracts/` | `design_only` | ADR-0155 (ExecutorAdapter.execute output stream; NOT to be confused with sealed RunEvent in run-event.v1.yaml) |
| `governed-messages.v1.yaml` | `docs/contracts/` | `design_only` | ADR-0155 §3 (replaces v1-draft BuiltPrompt; M6 TTI-02 output of boundary-treated messages) |
| `config-snapshot-ref.v1.yaml` | `docs/contracts/` | `design_only` | ADR-0155 (Run-time immutable config binding; STM-07 carrier) |
| `correlation-record.v1.yaml` | `docs/contracts/` | `design_only` | ADR-0155 (STM-06 cross-Run handle union; LocalChildRun | RemoteAgent) |
| `interrupt-registration.v1.yaml` | `docs/contracts/` | `design_only` | ADR-0155 §1 (H1 reversal — TTI-09 produces, TCC-06A consumes via ControlEvent.payloadRef) |
| `error-class.v1.yaml` | `docs/contracts/` | `design_only` | ADR-0155 (EDE-09 14-value platform-wide error taxonomy; M4 + M6 consume) |
| `intercept-request.v1.yaml` | `docs/contracts/` | `design_only` | ADR-0155 (TTI-01 unified entry envelope across 5 resource kinds) |
| `tool-result.v1.yaml` | `docs/contracts/` | `design_only` | ADR-0155 (TTI-04 normalised tool result; never carries vendor SDK objects) |
| `checkpoint-record.v1.yaml` | `docs/contracts/` | `design_only` | ADR-0155 (STM-05 recoverable boundary; sideEffect ∈ {NONE, PARTIAL, COMMITTED}) |
| `session-snapshot.v1.yaml` | `docs/contracts/` | `design_only` | ADR-0155 §3 (PlatformMemoryProvider read projection of STM-04) |
```

- [ ] **Step 3: Update the header `Last refreshed:` line**

Replace the `Version: 0.1.0-SNAPSHOT | Last refreshed:` line with:

```markdown
> Version: 0.1.0-SNAPSHOT | Last refreshed: 2026-05-28 (PR 92 absorption — added 14 design_only YAML schemas + 5 design_only SPI interfaces per ADR-0155)
```

- [ ] **Step 4: Commit**

```bash
git add docs/contracts/contract-catalog.md
git commit -m "docs(contracts): catalog 14 v1.2 schemas + 5 SPIs per ADR-0155"
```

---

### Task 9: Module metadata + DFX yaml updates (4-way parity surfaces)

**Files:**
- Modify: `agent-service/module-metadata.yaml` (append 2 entries to `spi_packages`)
- Modify: `docs/dfx/agent-service.yaml` (mirror)

- [ ] **Step 1: Locate the current spi_packages list**

```bash
grep -n "spi_packages:" agent-service/module-metadata.yaml docs/dfx/agent-service.yaml
```

- [ ] **Step 2: Add 2 entries to `agent-service/module-metadata.yaml`**

Use Edit to append after the last existing entry under `spi_packages:`:

```yaml
  - com.huawei.ascend.service.runtime.spi.executor
  - com.huawei.ascend.service.runtime.spi.intercept
```

(Maintain alphabetical/declaration order consistent with siblings.)

- [ ] **Step 3: Mirror in `docs/dfx/agent-service.yaml#spi_packages`**

Per Rule R-D.e, the two yamls must be set-equal. Add the same 2 entries.

- [ ] **Step 4: Commit**

```bash
git add agent-service/module-metadata.yaml docs/dfx/agent-service.yaml
git commit -m "feat(agent-service): register executor + intercept spi_packages per ADR-0155"
```

---

### Task 10: SPI appendix — append v1.2 SPI section

**Files:**
- Modify: `architecture/docs/L1/agent-service/spi-appendix.md`

The current file has §1 (Active Java SPIs, 9 entries), §2 (SPI-adjacent carriers), §3 (deferred carrier-bloat gap). We append §4 for the v1.2 additions and extend §1's 4-way parity table.

- [ ] **Step 1: Append §4 — v1.2 SPI extensions**

After §3, append:

````markdown

## 4. v1.2 SPI additions (ADR-0155 — design_only)

| # | Interface FQN | SPI package | Status | Cross-module consumer | Authority |
|---|---|---|---|---|---|
| 10 | `com.huawei.ascend.service.runtime.spi.executor.ExecutorAdapter` | `service.runtime.spi.executor` | design_only — interface placeholder; concrete records (`ExecutionRequest`/`AgentEvent`) ship W2 | Layer 5a Engine Dispatch (EDE-01 contract) | ADR-0155 §4 |
| 11 | `com.huawei.ascend.service.runtime.spi.intercept.PlatformChatClient` | `service.runtime.spi.intercept` | design_only | Layer 5b TTI-03 (Native + Third-party adapter call path) | ADR-0155 §3 |
| 12 | `com.huawei.ascend.service.runtime.spi.intercept.PlatformToolCallback` | `service.runtime.spi.intercept` | design_only | Layer 5b TTI-04 | ADR-0155 |
| 13 | `com.huawei.ascend.service.runtime.spi.intercept.PlatformMemoryProvider` | `service.runtime.spi.intercept` | design_only | Layer 5b TTI-05 (read-only STM-04 projection) | ADR-0155 §3 |
| 14 | `com.huawei.ascend.service.runtime.spi.intercept.PlatformRetriever` | `service.runtime.spi.intercept` | design_only | Layer 5b TTI-06 | ADR-0155 |

**§2 carrier addition:** `InjectionMode` enum lives under
`com.huawei.ascend.service.runtime.spi.executor` per Rule R-D.d
carve-out (single-package home; declares an adapter's wiring choice).

**4-way parity check for v1.2 additions:**

| Surface | Where it lives | Status |
|---|---|---|
| `agent-service/module-metadata.yaml#spi_packages` | 2 new package entries (`service.runtime.spi.executor` + `service.runtime.spi.intercept`) | added in Task 9 |
| `docs/contracts/contract-catalog.md` §2 Active SPI interfaces | 5 new rows | added in Task 8 |
| `docs/dfx/agent-service.yaml#spi_packages` | 2 new package entries (set-equal with module-metadata per Rule R-D.e) | added in Task 9 |
| On-disk `.java` files | 5 `public interface` + 1 enum + 2 `package-info` declarations | added in Tasks 3–4 |
````

- [ ] **Step 2: Update the rc55 audit notes at the top of the file**

Find the line beginning `> Authoring source:` near the top. Append at the end of that block:

```markdown
> - **v1.2 absorption (ADR-0155, 2026-05-28)**: §4 added with 5 new SPI interface entries (rows 10–14) and the `InjectionMode` enum carrier; 4-way parity restored in the same PR via Tasks 8–9.
```

- [ ] **Step 3: Commit**

```bash
git add architecture/docs/L1/agent-service/spi-appendix.md
git commit -m "docs(L1): spi-appendix §4 v1.2 SPI extensions per ADR-0155"
```

---

### Task 11: features/access-layer.md — expand to 12 features

**Files:**
- Modify: `architecture/docs/L1/agent-service/features/access-layer.md`

Append 4 new feature rows (AS-L1-F09..F12) for the v1.2 deltas not covered by F01-F08.

- [ ] **Step 1: Append 4 rows to the feature table**

Locate the table row ending `AS-L1-F08`. After it, before the `## Cross-references` heading, append (NOTE: each cell on one line per the existing pattern):

```markdown
| AS-L1-F09 | AccessIntent normalisation | AS-SC01, AS-SC02 | Translate A2A and MQ ingress payloads into a single canonical `AccessIntent` value with immutable fields and replyChannel binding. The AccessIntent contract is `docs/contracts/access-intent.v1.yaml` (design_only). | Inputs: raw protocol payload, ingress metadata, replyDescriptor. Outputs: AccessIntent + IngressContext (authz decision + idempotency decision + traceCtx + deadlineBudget). | Session & Task Manager, Internal Event Queue. | Operation not recognised, immutable-violation attempt downstream, oversized payload requiring blob-store ref. | A2A `MessageSendParams`, AgentScope `AgentRequest`. |
| AS-L1-F10 | Sync reply projection (read-only path) | AS-SC03, AS-SC09 | Project the response snapshot written by TCC-03 terminal-state hook to the bound replyChannel. After ADR-0155 §1 H4 reversal, M1 does NOT write the snapshot — it only reads STM-08 to produce A2A `Task` or MQ reply envelope. | Inputs: runId + replyChannel + STM-08 snapshot. Outputs: A2A `Task` JSON-RPC response or MQ reply envelope. | Session & Task Manager (STM-08). | reply channel disconnected mid-projection, snapshot not yet written (interim Task with state=working). | A2A `Task` terminal projection, Conductor `WorkflowExecutor.getWorkflow`. |
| AS-L1-F11 | Backpressure-aware admission | AS-SC04, AS-SC23 | Translate IEQ rejected-enqueue signals into protocol-level retry-after responses. Uses IEQ-10 health metrics + AL-12 explicit-rejection policy. | Inputs: enqueue rejection signal, current channel depth. Outputs: A2A/MQ retry-after response. | Internal Event Queue (IEQ-06, IEQ-10). | Sustained backpressure, slow consumer cascade, per-tenant fairness rejection. | Sentinel limit policy, Conductor backpressure semantics. |
| AS-L1-F12 | Push Notification outbound | AS-SC13, AS-SC21 | POST state-change projections to per-client webhooks bound at registration (AL-09) using AL-11 dispatch. Failure modes are logged and counted but do NOT block Run progress. | Inputs: state-change projection + push config + auth credentials. Outputs: HTTP POST to client webhook, success/fail audit event. | Translation & Tool-Intercept (audit hook), Session & Task Manager (state source). | Webhook unreachable, auth failure, retry budget exhausted. | A2A `pushNotificationConfig`, OpenAI Agents push events. |
```

- [ ] **Step 2: Update the heading "(AS-L1-F01..F08)" to "(AS-L1-F01..F12)"**

Find:

```markdown
# Access Layer — Feature Inventory (AS-L1-F01..F08)
```

Replace with:

```markdown
# Access Layer — Feature Inventory (AS-L1-F01..F12)
```

- [ ] **Step 3: Update the `authority:` front-matter to include ADR-0155**

Find the front-matter line:

```
authority: "Absorbed from docs/logs/reviews/2026-05-26-agent-service-module-capability-feature-list.{cn,en}.md §6.1. Anchors back to the canonical Access Layer (Layer 1) in ../logical.md §1 + the ingress sub-section of ../physical.md §1."
```

Replace with:

```
authority: "Absorbed from docs/logs/reviews/2026-05-26-agent-service-module-capability-feature-list.{cn,en}.md §6.1 (rc55 W3 baseline F01-F08) + docs/logs/reviews/2026-05-28-agent-service-m1-m6-design-draft.cn.md §M1 (v1.2 expansion F09-F12 per ADR-0155). Anchors back to the canonical Access Layer (Layer 1) in ../logical.md §1 + the ingress sub-section of ../physical.md §1."
```

- [ ] **Step 4: Commit**

```bash
git add architecture/docs/L1/agent-service/features/access-layer.md
git commit -m "docs(L1-features): expand Access Layer F01..F08 to F01..F12 per ADR-0155"
```

---

### Task 12: features/session-task-manager.md — expand to 10 features

**Files:**
- Modify: `architecture/docs/L1/agent-service/features/session-task-manager.md`

- [ ] **Step 1: Inspect current feature count**

```bash
grep -c "^| AS-L2-F" architecture/docs/L1/agent-service/features/session-task-manager.md
```

The expected baseline is 8 rows (F01..F08). If different, adjust the new IDs accordingly.

- [ ] **Step 2: Append 2 rows for STM v1.2 deltas (CorrelationRecord union + response-snapshot write hook)**

Before the `## Cross-references` heading, append:

```markdown
| AS-L2-F09 | CorrelationRecord discriminated handle | AS-SC18, AS-SC15 | Persist cross-Run / remote-Agent handles as a discriminated union (`LocalChildHandle{childRunId}` or `RemoteAgentHandle{remoteAgentId, remoteTaskId, remoteThreadId, callbackId}`); M4 creates empty `PENDING` skeleton, M5 CAS-fills to `ACTIVE`, child settlement marks `SETTLED`. Contract: `docs/contracts/correlation-record.v1.yaml` (design_only). | Inputs: handle type + creation context. Outputs: STM-06 record + CAS fill on remote handle assignment. | Engine Dispatch & Execution (CAS handle fill), Task-Centric Control Layer (creation + cancel cascade reads). | Handle lost (CorrelationRecord incomplete), remote handle CAS conflict, settle-on-cancelled-parent race. | Temporal child-workflow handle, A2A task linkage. |
| AS-L2-F10 | Response snapshot writeback (terminal hook) | AS-SC09, AS-SC12 | Per ADR-0155 §1 H4 reversal, STM-08 receives response-snapshot writeback from TCC-03 immediately after a successful CAS to {COMPLETED, FAILED, CANCELLED}. STM-08 owns the storage; M1 only reads. | Inputs: runId, terminal state, finalArtifactRef, reason. Outputs: STM-08 IdempotencyRecord update (snapshot bound). | Task-Centric Control Layer (sole writer). | Snapshot already bound (terminal-to-terminal), bound-on-non-terminal attempted (rejected). | Stripe-style idempotency snapshot binding. |
```

- [ ] **Step 3: Update front-matter authority to include ADR-0155**

(Same pattern as Task 11 Step 3 — append the rc55 source + the v1.2 source citing ADR-0155.)

- [ ] **Step 4: Commit**

```bash
git add architecture/docs/L1/agent-service/features/session-task-manager.md
git commit -m "docs(L1-features): expand STM features with F09 CorrelationRecord + F10 response-snapshot writeback per ADR-0155"
```

---

### Task 13: features/internal-event-queue.md — expand to 11 features

**Files:**
- Modify: `architecture/docs/L1/agent-service/features/internal-event-queue.md`

PR 92 §M3 lists 11 IEQ functional items (IEQ-01 through IEQ-11). The
current file's rc55 baseline has 8 features. We add 3 new rows.

- [ ] **Step 1: Append 3 rows (three-channel topology + bounded buffer rejection + SPI persistence hook)**

Before `## Cross-references`, append:

```markdown
| AS-L3-F09 | Three-channel physical topology | AS-SC23, AS-SC24 | Physically partition IEQ into Control, Data, and Egress channels with independent bounded buffer + worker pool + back-pressure policy. Cross-channel ordering is undefined; per-Run causality lives in STM-09 cursor. | Inputs: producer enqueue per channel. Outputs: independent channel handles + subscriber registration per channel. | All sibling modules (M1/M4/M5/M6 each bind specific channels). | One channel OOM does not propagate; partition exhaustion handled per-channel. | Reactor `Sinks.many().multicast()` per topic, Kafka topic isolation. |
| AS-L3-F10 | Bounded buffer + explicit rejection policy | AS-SC23 | Each channel enforces independent `lowWatermark / highWatermark / hardCap`; on `hardCap` reach, enqueue returns `RejectedEnqueue` with reason + optional `retryAfterHint`. Control channel reserves headroom for cancel/deadline signals. Egress channel uses per-subscriber drop-oldest to isolate slow consumers. | Inputs: enqueue call + current depth. Outputs: `EnqueueResult ∈ {ACCEPTED, REJECTED(reason, retryAfterHint)}`. | M1 AL-12 (consumes rejection signals), all producers. | Buffer saturation cascade, slow-subscriber stall, retry-after exhaustion. | Reactor onBackpressure*, Sentinel limit policy. |
| AS-L3-F11 | Persistence SPI + outbox extension point | AS-SC25 | v1 ships in-memory; SPI hooks reserved for Kafka/Pulsar/JDBC outbox persistence without changing the channel contract. | Inputs: channel-id + persistence-policy descriptor. Outputs: persisted enqueue record (when SPI is plugged). | All channels. | SPI implementation absent (defaults to in-memory), durability requirement mismatch with in-memory. | Conductor `MessageQueue` SPI, Spring AI Alibaba outbox patterns. |
```

- [ ] **Step 2: Update front-matter authority to cite ADR-0155**

- [ ] **Step 3: Commit**

```bash
git add architecture/docs/L1/agent-service/features/internal-event-queue.md
git commit -m "docs(L1-features): expand IEQ features with three-channel topology + rejection + SPI rows per ADR-0155"
```

---

### Task 14: features/task-centric-control.md — expand to 11 features

**Files:**
- Modify: `architecture/docs/L1/agent-service/features/task-centric-control.md`

Add the three v1.2-load-bearing rows: per-Run actor, CANCEL_RACE rule, RESUME_ACCEPTED handler.

- [ ] **Step 1: Append 3 rows**

Before `## Cross-references`, append:

```markdown
| AS-L4-F09 | per-Run virtual-actor serialisation | AS-SC11, AS-SC18 | Per-runId actor queue serialises all decisions for a single Run; backed by virtual threads so idle Runs consume no resources. This is the physical carrier of the M4-sole-driver constraint (Rule R-C.2.b). | Inputs: `RunDecisionTask { runId, reason, payload }`. Outputs: serial decision invocation; actor released on idle TTL. | Internal Event Queue (control + data channel consumers), Session & Task Manager (STM-03 sole caller). | Actor mailbox overflow → `RejectedDecision` event; per-run starvation prevention. | Akka actor mailbox, Temporal workflow stickiness. |
| AS-L4-F10 | CANCEL race deterministic arbitration | AS-SC12, AS-SC18 | Per ADR-0155 §6, when CANCEL_REQUESTED meets WORKITEM_DONE: if children unsettled → wait + CANCELLED; if no children and final artifact present → COMPLETED with reason `CANCEL_RACE_RESOLVED_AS_COMPLETED`; if no children and partial → CANCELLED with reason `CANCEL_RACE_RESOLVED_AS_CANCELLED`. RunEvent reason codes emitted. | Inputs: current state + work-item-done payload + child-handle inventory. Outputs: terminal-state CAS + reason-bearing RunEvent. | Session & Task Manager (CAS), Internal Event Queue (Egress for reason event). | Decision delayed beyond deadline, child-settle vs cancel concurrent. | Temporal child cancel propagation, structured concurrency cancellation. |
| AS-L4-F11 | RESUME_ACCEPTED handler (RESUMING → RUNNING) | AS-SC14 | Per ADR-0155 H3 (audit finding), the new ControlEvent kind `RESUME_ACCEPTED` arrives from EDE-07 after the adapter accepts a resume tick; TCC-06B CAS RESUMING→RUNNING and emits StateChanged. Closes the v1 gap where RESUMING had no exit signal. | Inputs: `ControlEvent{kind=RESUME_ACCEPTED, runId}`. Outputs: STM-03 CAS + RunEvent. | Engine Dispatch & Execution (producer), Session & Task Manager (CAS). | RESUME_ACCEPTED on non-RESUMING state (rejected), adapter ack lost. | Temporal resume signal, LangGraph4j resume hook. |
```

- [ ] **Step 2: Update authority + commit**

```bash
git add architecture/docs/L1/agent-service/features/task-centric-control.md
git commit -m "docs(L1-features): expand TCC with actor + CANCEL_RACE + RESUME_ACCEPTED per ADR-0155"
```

---

### Task 15: features/engine-dispatch-execution.md — expand to 11 features

**Files:**
- Modify: `architecture/docs/L1/agent-service/features/engine-dispatch-execution.md`

PR 92 EDE has 11 functional items including the ExecutorAdapter SPI three forms, InjectionMode, and 14-class error taxonomy.

- [ ] **Step 1: Append 3 rows**

Before `## Cross-references`, append:

```markdown
| AS-L5A-F09 | ExecutorAdapter SPI three forms | AS-SC15, AS-SC18 | Single SPI `ExecutorAdapter.execute(ExecutionRequest) : Flow.Publisher<AgentEvent>` carries three Agent shapes — Native (in-process platform-bean DI), Third-party (in-process framework-bridge replacement), Remote (out-of-process A2A protocol client). M4/upstream is form-agnostic. SPI: `com.huawei.ascend.service.runtime.spi.executor.ExecutorAdapter` (design_only). | Inputs: ExecutionRequest. Outputs: AgentEvent publisher. | Translation & Tool-Intercept (resource calls intercepted), Session & Task Manager (cursor source). | Adapter unavailable, vendor SDK leakage (rejected at registration time). | Temporal Activity, AgentScope `AgentHandler`, LangGraph4j `CompiledGraph.stream`. |
| AS-L5A-F10 | InjectionMode wiring declaration | AS-SC18 | Adapter declares `InjectionMode ∈ {NATIVE_DI, THIRD_PARTY_BRIDGE, EVENT_RELAY, NONE}` at registration; EDE-08 routes resource-call interception accordingly (sync direct, sync bridge, async event-relay, or no local interception for remote). | Inputs: adapter spec at startup. Outputs: registered wiring mode + per-Run resolution. | Translation & Tool-Intercept (consumes mode for routing). | Mode mismatch with adapter behaviour rejected at startup compliance scan. | Spring auto-configuration + bean-replacement patterns. |
| AS-L5A-F11 | Structured error class emission | AS-SC18, AS-SC11 | Adapter failures emit one of 14 `ErrorClass` values per `docs/contracts/error-class.v1.yaml`; no raw vendor exceptions cross the M5 boundary. M4 consumes the class to drive retry-vs-fail decisions. | Inputs: adapter exception. Outputs: structured `ErrorClass` value (one of MODEL_UNAVAILABLE / MODEL_RATE_LIMITED / INPUT_VALIDATION_ERROR / POLICY_DENIED / TOOL_FAILED / TOOL_TIMEOUT / MEMORY_FAILED / RETRIEVAL_FAILED / REMOTE_PROTOCOL_ERROR / CLIENT_CALLBACK_FAILED / INTERNAL_ERROR / CONTEXT_OVERFLOW / ADAPTER_UNAVAILABLE / DEADLINE_EXCEEDED). | Task-Centric Control Layer (retry policy consumer). | Class not enumerated (rejected at compile-time when enum lands); cross-class leakage detected by static scan. | Temporal Activity error taxonomy, Spring AI vendor error mapping. |
```

- [ ] **Step 2: Update authority + commit**

```bash
git add architecture/docs/L1/agent-service/features/engine-dispatch-execution.md
git commit -m "docs(L1-features): expand EDE with ExecutorAdapter + InjectionMode + 14-class errors per ADR-0155"
```

---

### Task 16: features/translation-tool-intercept.md — expand with v1.2 reversal rows

**Files:**
- Modify: `architecture/docs/L1/agent-service/features/translation-tool-intercept.md`

This is the **most semantically loaded** file because of the M6
prompt-construction reversal. New rows MUST clearly state that M6 does
NOT construct prompts.

- [ ] **Step 1: Append 3 rows**

Before `## Cross-references`, append:

```markdown
| AS-L5B-F09 | Messages-in-flight boundary treatment (NOT prompt construction) | AS-SC07, AS-SC22 | Per ADR-0155 §3 (v1.2 reversal), M6 does NOT construct prompts. It receives Agent-constructed messages at the model-call boundary and applies policy chain, PII redaction, token-budget audit, and fallback trim only. Output type `GovernedMessages` replaces v1-draft `BuiltPrompt`. Contract: `docs/contracts/governed-messages.v1.yaml`. | Inputs: Agent-constructed `List<Msg>` + modelRef + samplingHints. Outputs: GovernedMessages (messages + masks + budgetSnapshot + denials). | Engine Dispatch & Execution (calls in), upstream Agent (constructs messages). | `CONTEXT_OVERFLOW` after fallback trim; policy-denial path emits `POLICY_DENIED`. | HTTP API Gateway pattern (governs body without rewriting business shape). |
| AS-L5B-F10 | Platform intercept SPI surface (4 SPIs) | AS-SC15, AS-SC18 | Exposes `PlatformChatClient` / `PlatformToolCallback` / `PlatformMemoryProvider` / `PlatformRetriever` as DI beans (Native form) and framework-bridge implementations (Third-party form). Adapter must not new-up vendor SDKs; resource calls flow through these SPIs into TTI-03..06. | Inputs: SPI invocations from in-process adapters. Outputs: routed to TTI-03..06 + audit events. | Engine Dispatch & Execution (Native + Third-party adapter consumers). | Bridge replacement incomplete at startup → adapter registration rejected. | Spring Security FilterChain, AgentScope bridge replacement pattern. |
| AS-L5B-F11 | A2A outbound message audit (Remote agent boundary) | AS-SC18 | Per ADR-0155 §4, Remote Agent internal model/tool/memory/RAG calls are NOT intercepted locally — they happen in the remote process. M6 only audits A2A protocol outbound messages via TTI-08 policy chain (egress audit, PII redaction, content classification). | Inputs: A2A outbound message from EDE-04 Remote Adapter. Outputs: audit-tagged message or policy-denial. | Engine Dispatch & Execution (Remote Adapter EDE-04). | Remote protocol error during audit, classified content blocked at boundary. | A2A protocol audit hooks, OAuth resource server boundary classifiers. |
```

- [ ] **Step 2: Update authority + commit**

```bash
git add architecture/docs/L1/agent-service/features/translation-tool-intercept.md
git commit -m "docs(L1-features): expand TTI with v1.2 messages-in-flight reversal + SPI surface + remote audit per ADR-0155"
```

---

### Task 17: L1 view files — surgical updates only

**Files:**
- Modify: `architecture/docs/L1/agent-service/ARCHITECTURE.md` (authority list)
- Modify: `architecture/docs/L1/agent-service/logical.md` (§6 glossary + §7 RunEvent variant)
- Modify: `architecture/docs/L1/agent-service/process.md` (P3 cancel-race amendment)
- Modify: `architecture/docs/L1/agent-service/physical.md` (deployment matrix)
- Modify: `architecture/docs/L1/agent-service/development.md` (InjectionMode table)
- Modify: `architecture/docs/L1/agent-service/scenarios.md` (S6 weather-clarification reference)

Each view file gets a targeted insertion citing ADR-0155 — surgical changes per Rule D-2.

- [ ] **Step 1: ARCHITECTURE.md — extend authority front-matter**

In the front-matter `authority:` line, append at the end of the existing string:

```
 + ADR-0155 (PR 92 v1.2 absorption — 6 boundary-decision reversals + 14 inter-module contracts + 5 new SPIs)
```

- [ ] **Step 2: logical.md — append §6 glossary note + §7 RunEvent variant**

In §6 glossary (Engine vs Adapter vs Bridge entry), add a new paragraph at the end of the relevant entry:

```markdown

**v1.2 (per ADR-0155 §3)**: M6 Translation & Tool-Intercept does NOT
construct prompts. The Agent (native code, third-party framework
Formatter, or remote service) owns prompt assembly. M6 is a
messages-in-flight boundary aspect — policy, redaction, token-budget
audit, fallback trim. `BuiltPrompt` is deleted; `GovernedMessages`
replaces it as M6's downstream type.
```

In §7 RunEvent hierarchy, locate the sealed-type enumeration. Append a new variant:

```markdown
- `ResumeAccepted { runId }` — emitted when M5 EDE-07 successfully injects the adapter on a RESUME_TICK; consumed by M4 TCC-06B to drive RESUMING → RUNNING. Per ADR-0155 H3 audit reversal.
```

- [ ] **Step 3: process.md — append amendment after P3 sequence**

Locate the P3 cancel-race-loser sequence (search for "P3" header or "cancel"). After the sequence diagram (or at the end of its description), insert:

```markdown

#### P3 v1.2 amendment — CANCEL_RACE_RESOLVED reasons (ADR-0155 §6)

When CANCEL_REQUESTED arrives concurrent with WORKITEM_DONE, M4 TCC-03 arbitrates:

| Condition | Final state | RunEvent reason |
|---|---|---|
| Child Runs unsettled | wait → CANCEL_REQUESTED → CANCELLED | `CANCEL_RACE_RESOLVED_AS_CANCELLED` |
| No children, final artifact present | COMPLETED | `CANCEL_RACE_RESOLVED_AS_COMPLETED` |
| No children, partial artifact only | CANCELLED | `CANCEL_RACE_RESOLVED_AS_CANCELLED` |

This is deterministic — the CAS engine never has to "guess" who arrived first; it inspects the in-flight envelope and applies the rule.
```

- [ ] **Step 4: physical.md — append deployment-vs-code-ownership matrix**

Locate the §1 plane description or §1.x sub-heading. After it, insert:

```markdown

### 1.x Deployment vs Code Ownership (ADR-0155 §4 — orthogonal dimensions)

| Agent form | Code home | Deployment | M6 interception range | Bridge mechanism |
|---|---|---|---|---|
| Native | This repo (`agent-service/...`) | In-process JVM | All 5 resource kinds (model / tool / memory / RAG / client-hosted skill) | DI-injected platform beans |
| Third-party (AgentScope-java, LangGraph4j, ...) | External library | In-process JVM | All 5 resource kinds | Startup bridge replacement of framework Model/Toolkit/Memory abstractions |
| Remote | Anywhere (external service) | Out-of-process / remote network | None locally — A2A protocol boundary audit only (TTI-08) | A2A SDK client |

Code home and deployment are **orthogonal** — a self-developed Agent CAN be deployed remotely (use EDE-04). Combinations beyond these three rows are deferred per ADR-0155 §6 (Managed Remote / Resource Gateway pattern is v2 scope).
```

- [ ] **Step 5: development.md — append InjectionMode + package-layout note**

Locate the package-tree section in `development.md`. After the existing tree (where SPI packages are listed), append:

```markdown

### v1.2 SPI package additions (ADR-0155)

Two new SPI packages live under `agent-service/src/main/java/`:

```
service.runtime.spi.executor/
  ExecutorAdapter.java        (interface)
  InjectionMode.java          (enum — InjectionMode wiring choice per ADR-0155 §4)
service.runtime.spi.intercept/
  PlatformChatClient.java     (interface)
  PlatformToolCallback.java   (interface)
  PlatformMemoryProvider.java (interface)
  PlatformRetriever.java      (interface)
```

`InjectionMode` enum values: `NATIVE_DI | THIRD_PARTY_BRIDGE | EVENT_RELAY | NONE` — see L5a EDE-08 in the features inventory.
```

- [ ] **Step 6: scenarios.md — cross-link to PR 92's weather-clarification scenario**

Locate the existing S1-S5 scenario list. After the last scenario, insert:

```markdown

### S6 (cross-reference) — Weather Clarification (PR 92 v1.2 baseline)

A complete end-to-end HITL-plus-tool scenario walking through M1–M6 and demonstrating: MQ ingress → AccessIntent normalisation → Native ReAct Agent first round → INTERRUPT_REGISTERED control event → SUSPENDED → callback → RESUMING → tool call → second LLM round → COMPLETED. Source: [`../../../docs/logs/reviews/2026-05-28-agent-service-m1-m6-design-draft.cn.md`](../../../docs/logs/reviews/2026-05-28-agent-service-m1-m6-design-draft.cn.md) §Scenario. Authority: ADR-0155.
```

- [ ] **Step 7: Commit each view file separately for surgical history**

```bash
git add architecture/docs/L1/agent-service/ARCHITECTURE.md
git commit -m "docs(L1-architecture): extend authority to include ADR-0155"

git add architecture/docs/L1/agent-service/logical.md
git commit -m "docs(L1-logical): v1.2 glossary M6 reversal + RunEvent.ResumeAccepted per ADR-0155"

git add architecture/docs/L1/agent-service/process.md
git commit -m "docs(L1-process): P3 v1.2 amendment — CANCEL_RACE_RESOLVED reasons per ADR-0155"

git add architecture/docs/L1/agent-service/physical.md
git commit -m "docs(L1-physical): deployment vs code ownership matrix per ADR-0155"

git add architecture/docs/L1/agent-service/development.md
git commit -m "docs(L1-development): SPI package additions (executor + intercept) per ADR-0155"

git add architecture/docs/L1/agent-service/scenarios.md
git commit -m "docs(L1-scenarios): S6 cross-link to PR 92 weather-clarification scenario"
```

---

### Task 18: features.dsl — register 6 new SAA Feature elements

**Files:**
- Modify: `architecture/features/features.dsl`

Append 6 FEAT- elements (one per module) with full required-properties.

- [ ] **Step 1: Locate the file end (after the last existing element)**

```bash
grep -n "^}" architecture/features/features.dsl | tail -3
```

- [ ] **Step 2: Append 6 elements**

Open the file and append at the end (use the file's existing pattern as the template — see the existing `featRunLifecycleControl` definition):

```dsl
featAgentServiceAccessLayer = element "AgentService Access Layer" "Feature" "L1 Access Layer module per PR 92 v1.2 (M1)" "SAA Feature" {
    properties {
        "saa.id" "FEAT-AS-ACCESS-LAYER"
        "saa.kind" "feature"
        "saa.level" "L1"
        "saa.view" "logical"
        "saa.status" "design_only"
        "saa.owner" "agent-service"
        "saa.sourceAdr" "ADR-0155"
        "saa.capabilityDomain" "runtime-access-layer"
        "saa.synopsis" "M1 Access Layer per PR 92 v1.2: A2A Server + MQ ingress + AccessIntent normalisation + ingress governance chain + sync/SSE/MQ-reply/push-notification projections. Does not hold execution state."
        "saa.aiBoundary.canModifyCode" "true"
        "saa.aiBoundary.canModifyContracts" "false"
        "saa.aiBoundary.allowedStatusTransitions" "design_only->ready_for_impl|design_only->deprecated"
        "saa.aiBoundary.requiresHumanReviewAt" "ready_for_impl|test_verified|deprecated"
        "saa.aiBoundary.sandboxPolicyRef" "docs/governance/sandbox-policies.yaml#default_policy"
        "saa.devPaths" "agent-service/src/main/java/com/huawei/ascend/service/platform/web|agent-service/src/main/java/com/huawei/ascend/service/dispatcher"
        "saa.goals" "Protocol convergence (A2A + MQ → AccessIntent)|Ingress governance (authn + tenant + idempotency + deadline + trace)|Out-projection (sync + SSE + MQ-reply + push notification)"
        "saa.nonGoals" "Run state ownership|Execution dispatch|Prompt construction"
    }
}

featAgentServiceSessionTaskManager = element "AgentService Session Task Manager" "Feature" "L1 STM module per PR 92 v1.2 (M2)" "SAA Feature" {
    properties {
        "saa.id" "FEAT-AS-SESSION-TASK-MANAGER"
        "saa.kind" "feature"
        "saa.level" "L1"
        "saa.view" "logical"
        "saa.status" "design_only"
        "saa.owner" "agent-service"
        "saa.sourceAdr" "ADR-0155"
        "saa.capabilityDomain" "runtime-state"
        "saa.synopsis" "M2 Session Task Manager per PR 92 v1.2: Run state machine (sole machine), Session append-only context, Task stable-ID multi-attempt model, Checkpoint with side-effect annotation, ConfigSnapshot, IdempotencyRecord, RunEventLog with monotonic cursor."
        "saa.aiBoundary.canModifyCode" "true"
        "saa.aiBoundary.canModifyContracts" "false"
        "saa.aiBoundary.allowedStatusTransitions" "design_only->ready_for_impl|design_only->deprecated"
        "saa.aiBoundary.requiresHumanReviewAt" "ready_for_impl|test_verified|deprecated"
        "saa.aiBoundary.sandboxPolicyRef" "docs/governance/sandbox-policies.yaml#default_policy"
        "saa.devPaths" "agent-service/src/main/java/com/huawei/ascend/service/runtime/runs|agent-service/src/main/java/com/huawei/ascend/service/session|agent-service/src/main/java/com/huawei/ascend/service/task"
        "saa.goals" "Run state machine sole authority|Session append-only context|Task stable-ID multi-attempt"
        "saa.nonGoals" "Execution|Prompt construction|Resource interception"
    }
}

featAgentServiceInternalEventQueue = element "AgentService Internal Event Queue" "Feature" "L1 IEQ module per PR 92 v1.2 (M3)" "SAA Feature" {
    properties {
        "saa.id" "FEAT-AS-INTERNAL-EVENT-QUEUE"
        "saa.kind" "feature"
        "saa.level" "L1"
        "saa.view" "logical"
        "saa.status" "design_only"
        "saa.owner" "agent-service"
        "saa.sourceAdr" "ADR-0155"
        "saa.capabilityDomain" "runtime-event-queue"
        "saa.synopsis" "M3 Internal Event Queue per PR 92 v1.2: three-channel topology (Control / Data / Egress) with independent bounded buffer + worker pool + back-pressure. In-memory v1; SPI hooks for persistence."
        "saa.aiBoundary.canModifyCode" "true"
        "saa.aiBoundary.canModifyContracts" "false"
        "saa.aiBoundary.allowedStatusTransitions" "design_only->ready_for_impl|design_only->deprecated"
        "saa.aiBoundary.requiresHumanReviewAt" "ready_for_impl|test_verified|deprecated"
        "saa.aiBoundary.sandboxPolicyRef" "docs/governance/sandbox-policies.yaml#default_policy"
        "saa.devPaths" "agent-service/src/main/java/com/huawei/ascend/service/runtime/eventqueue"
        "saa.goals" "Three-channel physical isolation|Bounded buffer with explicit rejection|Per-Run causality via STM-09 cursor"
        "saa.nonGoals" "Cross-channel ordering guarantees|State machine arbitration"
    }
}

featAgentServiceTaskCentricControl = element "AgentService Task-Centric Control" "Feature" "L1 TCC module per PR 92 v1.2 (M4)" "SAA Feature" {
    properties {
        "saa.id" "FEAT-AS-TASK-CENTRIC-CONTROL"
        "saa.kind" "feature"
        "saa.level" "L1"
        "saa.view" "logical"
        "saa.status" "design_only"
        "saa.owner" "agent-service"
        "saa.sourceAdr" "ADR-0155"
        "saa.capabilityDomain" "runtime-control"
        "saa.synopsis" "M4 Task-Centric Control per PR 92 v1.2: per-Run virtual-actor serialisation, sole STM-03 driver, CANCEL_RACE deterministic arbitration, RESUME_ACCEPTED handler, retry/checkpoint policy."
        "saa.aiBoundary.canModifyCode" "true"
        "saa.aiBoundary.canModifyContracts" "false"
        "saa.aiBoundary.allowedStatusTransitions" "design_only->ready_for_impl|design_only->deprecated"
        "saa.aiBoundary.requiresHumanReviewAt" "ready_for_impl|test_verified|deprecated"
        "saa.aiBoundary.sandboxPolicyRef" "docs/governance/sandbox-policies.yaml#default_policy"
        "saa.devPaths" "agent-service/src/main/java/com/huawei/ascend/service/runtime/control"
        "saa.goals" "Sole STM-03 driver|per-Run serial decisions|CANCEL_RACE_RESOLVED determinism|RESUME_ACCEPTED handler"
        "saa.nonGoals" "Prompt construction|Resource invocation|Session-context writes (only via STM)"
    }
}

featAgentServiceEngineDispatchExecution = element "AgentService Engine Dispatch Execution" "Feature" "L1 EDE module per PR 92 v1.2 (M5)" "SAA Feature" {
    properties {
        "saa.id" "FEAT-AS-ENGINE-DISPATCH-EXECUTION"
        "saa.kind" "feature"
        "saa.level" "L1"
        "saa.view" "logical"
        "saa.status" "design_only"
        "saa.owner" "agent-service"
        "saa.sourceAdr" "ADR-0155"
        "saa.capabilityDomain" "runtime-engine-dispatch"
        "saa.synopsis" "M5 Engine Dispatch Execution per PR 92 v1.2: ExecutorAdapter SPI for Native / Third-party / Remote forms, Engine instance cache, InjectionMode declaration, structured ErrorClass emission (14 values)."
        "saa.aiBoundary.canModifyCode" "true"
        "saa.aiBoundary.canModifyContracts" "false"
        "saa.aiBoundary.allowedStatusTransitions" "design_only->ready_for_impl|design_only->deprecated"
        "saa.aiBoundary.requiresHumanReviewAt" "ready_for_impl|test_verified|deprecated"
        "saa.aiBoundary.sandboxPolicyRef" "docs/governance/sandbox-policies.yaml#default_policy"
        "saa.devPaths" "agent-service/src/main/java/com/huawei/ascend/service/runtime/spi/executor|agent-service/src/main/java/com/huawei/ascend/service/engine"
        "saa.goals" "ExecutorAdapter SPI three-form unification|InjectionMode wiring declaration|14-value ErrorClass taxonomy|Engine instance cache"
        "saa.nonGoals" "Run state writes|Prompt construction|Direct vendor SDK calls"
    }
}

featAgentServiceTranslationToolIntercept = element "AgentService Translation Tool Intercept" "Feature" "L1 TTI module per PR 92 v1.2 (M6)" "SAA Feature" {
    properties {
        "saa.id" "FEAT-AS-TRANSLATION-TOOL-INTERCEPT"
        "saa.kind" "feature"
        "saa.level" "L1"
        "saa.view" "logical"
        "saa.status" "design_only"
        "saa.owner" "agent-service"
        "saa.sourceAdr" "ADR-0155"
        "saa.capabilityDomain" "runtime-resource-intercept"
        "saa.synopsis" "M6 Translation Tool Intercept per PR 92 v1.2: messages-in-flight boundary aspect (does NOT construct prompts; v1.2 reversal). Platform intercept SPI surface (4 SPIs). 5 resource-call kinds: model / tool / memory / RAG / client-hosted skill. Remote agent internal calls outside jurisdiction."
        "saa.aiBoundary.canModifyCode" "true"
        "saa.aiBoundary.canModifyContracts" "false"
        "saa.aiBoundary.allowedStatusTransitions" "design_only->ready_for_impl|design_only->deprecated"
        "saa.aiBoundary.requiresHumanReviewAt" "ready_for_impl|test_verified|deprecated"
        "saa.aiBoundary.sandboxPolicyRef" "docs/governance/sandbox-policies.yaml#default_policy"
        "saa.devPaths" "agent-service/src/main/java/com/huawei/ascend/service/runtime/spi/intercept|agent-service/src/main/java/com/huawei/ascend/service/runtime/intercept"
        "saa.goals" "Messages-in-flight boundary treatment|Platform intercept SPI surface|HITL interrupt gateway|A2A outbound audit (remote boundary)"
        "saa.nonGoals" "Prompt construction (Agent owns this per v1.2 reversal)|Session-context ownership"
    }
}
```

- [ ] **Step 3: Commit**

```bash
git add architecture/features/features.dsl
git commit -m "feat(architecture-dsl): 6 SAA Feature elements for AgentService M1-M6 per ADR-0155"
```

---

### Task 19: function-points.dsl — register 4 new function points

**Files:**
- Modify: `architecture/features/function-points.dsl`

- [ ] **Step 1: Append 4 elements**

Append at file end (use the existing `fpCreateRun` element as the template):

```dsl
fpA2aMessageSend = element "A2A message/send" "FunctionPoint" "A2A JSON-RPC message/send entry (M1 AL-01 ingress)" "SAA FunctionPoint" {
    properties {
        "saa.id" "FP-A2A-MESSAGE-SEND"
        "saa.kind" "function_point"
        "saa.level" "L1"
        "saa.view" "scenarios"
        "saa.status" "design_only"
        "saa.owner" "agent-service"
        "saa.sourceAdr" "ADR-0155"
        "saa.channel" "http"
        "saa.actor" "tenant-developer-or-peer-agent"
        "saa.trigger" "A2A JSON-RPC POST message/send"
        "saa.code_entrypoint_refs" "agent-service/src/main/java/com/huawei/ascend/service/platform/web/a2a/A2aMessageController.java#send"
        "saa.test_refs" ""
        "saa.contract_op_refs" "access-intent.v1.yaml#operation=SUBMIT"
    }
}

fpA2aTasksCancel = element "A2A tasks/cancel" "FunctionPoint" "A2A tasks/cancel entry (M1 AL-08 control ingress)" "SAA FunctionPoint" {
    properties {
        "saa.id" "FP-A2A-TASKS-CANCEL"
        "saa.kind" "function_point"
        "saa.level" "L1"
        "saa.view" "scenarios"
        "saa.status" "design_only"
        "saa.owner" "agent-service"
        "saa.sourceAdr" "ADR-0155"
        "saa.channel" "http"
        "saa.actor" "tenant-developer-or-peer-agent"
        "saa.trigger" "A2A JSON-RPC POST tasks/cancel"
        "saa.code_entrypoint_refs" "agent-service/src/main/java/com/huawei/ascend/service/platform/web/a2a/A2aTasksController.java#cancel"
        "saa.test_refs" ""
        "saa.contract_op_refs" "access-intent.v1.yaml#operation=CANCEL"
    }
}

fpA2aTasksResubscribe = element "A2A tasks/resubscribe" "FunctionPoint" "A2A tasks/resubscribe stream entry (M1 AL-06 cursor flow)" "SAA FunctionPoint" {
    properties {
        "saa.id" "FP-A2A-TASKS-RESUBSCRIBE"
        "saa.kind" "function_point"
        "saa.level" "L1"
        "saa.view" "scenarios"
        "saa.status" "design_only"
        "saa.owner" "agent-service"
        "saa.sourceAdr" "ADR-0155"
        "saa.channel" "http"
        "saa.actor" "tenant-developer-or-peer-agent"
        "saa.trigger" "A2A JSON-RPC POST tasks/resubscribe"
        "saa.code_entrypoint_refs" "agent-service/src/main/java/com/huawei/ascend/service/platform/web/a2a/A2aStreamController.java#resubscribe"
        "saa.test_refs" ""
        "saa.contract_op_refs" "access-intent.v1.yaml#operation=SUBSCRIBE"
    }
}

fpMqInbound = element "MQ inbound consume" "FunctionPoint" "Outside broker → AL-02 inbound consumer (M1 v1.2)" "SAA FunctionPoint" {
    properties {
        "saa.id" "FP-MQ-INBOUND"
        "saa.kind" "function_point"
        "saa.level" "L1"
        "saa.view" "scenarios"
        "saa.status" "design_only"
        "saa.owner" "agent-service"
        "saa.sourceAdr" "ADR-0155"
        "saa.channel" "spi"
        "saa.actor" "external-mq-broker"
        "saa.trigger" "Broker delivery (RocketMQ / Kafka SPI)"
        "saa.code_entrypoint_refs" "agent-service/src/main/java/com/huawei/ascend/service/dispatcher/mq/MqInboundConsumer.java#onMessage"
        "saa.test_refs" ""
        "saa.contract_op_refs" "access-intent.v1.yaml#operation=SUBMIT"
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add architecture/features/function-points.dsl
git commit -m "feat(architecture-dsl): 4 FunctionPoints for A2A + MQ ingress per ADR-0155"
```

---

### Task 20: Recurring defect family — F-agent-service-internal-boundary-drift

**Files:**
- Modify: `docs/governance/recurring-defect-families.yaml`
- Modify: `docs/governance/recurring-defect-families.md`

- [ ] **Step 1: Inspect current yaml structure**

```bash
head -30 docs/governance/recurring-defect-families.yaml
grep -c "^- id:" docs/governance/recurring-defect-families.yaml
```

- [ ] **Step 2: Append new family to yaml**

Locate the `families:` list (or the `- id:` entries) and append:

```yaml
- id: F-agent-service-internal-boundary-drift
  title: "AgentService internal-module boundary drift (M4 sole-caller breach, responseSnapshot owner drift, cross-jurisdiction remote interception)"
  first_observed_rc: PR-92 (2026-05-28 self-audit)
  last_observed_rc: PR-92 (2026-05-28)
  occurrences: 3
  root_cause: "AgentService internal modules (M1–M6) lack explicit cross-module data-contract anchoring at L1; v1 drafts spread responsibility into the wrong module when authoring per-module designs in isolation. Self-audit caught three concrete drifts (H1 TTI-09 → STM-03 sole-caller breach; H4 responseSnapshot owner drift from M1 to M4; H5 REMOTE_AGENT_INVOKE_REQUEST in M6 violated remote-jurisdiction boundary). The pattern is structural: without ADR-anchored contracts, the next per-module audit will rediscover similar drifts."
  surfaces:
    - architecture/docs/L1/agent-service/logical.md
    - architecture/docs/L1/agent-service/spi-appendix.md
    - architecture/docs/L1/agent-service/features/*.md
    - docs/contracts/*.v1.yaml
    - docs/adr/0155-agent-service-l1-v1-2-internal-module-design.yaml
  prevention_rules:
    - "ADR-0155 anchors the 6 v1.2 boundary reversals as binding contracts."
    - "14 new design_only YAML contracts under docs/contracts/ (per ADR-0155 §3.3 + §5 deliverables) make the inter-module data-contract matrix machine-readable."
    - "Rule R-D.f catalog integrity ensures new contracts appear in contract-catalog.md."
    - "Rule G-1.1.b 4-way SPI parity ensures new SPIs cannot land in only some authority surfaces."
    - "Rule R-C.2.b STM-03 sole-caller is already enforced; this family adds defence-in-depth via TCC-03 ownership in features.dsl + LOGICAL + SPI surfaces."
  cleanup_status: open_documented_with_prevention
  open_residual: "TCK conformance suites for the 7 new SPIs are deferred to W2; ArchUnit physical enforcement of the 14 new YAML contracts as compile-time assertions is also W2. The family stays open until either (a) all 6 module designs ship and the next L1 audit finds no recurrence of these specific drift patterns or (b) static enforcement closes the structural gap."
```

- [ ] **Step 3: Mirror in `recurring-defect-families.md`**

Open the md, find the family list, append a mirroring entry with the same id + title + cleanup_status. Match the format used by other families.

- [ ] **Step 4: Commit**

```bash
git add docs/governance/recurring-defect-families.yaml docs/governance/recurring-defect-families.md
git commit -m "docs(governance): new family F-agent-service-internal-boundary-drift per ADR-0155 (PR 92 self-audit H1+H4+H5)"
```

---

### Task 21: Verification — gate run + parity audit

**Files:** (no edits — verification only)

- [ ] **Step 1: Run the full gate**

```bash
wsl -d Ubuntu -- bash -lc 'cd /mnt/d/chao_workspace/spring-ai-ascend && bash gate/check_architecture_sync.sh 2>&1 | tee /tmp/gate.log'
echo "Exit: $?"
```

Expected: exit 0. Inspect `/tmp/gate.log` for "FAIL" lines:

```bash
grep -E "FAIL|ERROR|✗" /tmp/gate.log | head -20
```

Expected: zero matches.

- [ ] **Step 2: 4-way SPI parity manual cross-check**

```bash
# 5 new SPI FQNs should be present in all 4 surfaces
for fqn in ExecutorAdapter PlatformChatClient PlatformToolCallback PlatformMemoryProvider PlatformRetriever; do
  echo "=== $fqn ==="
  grep -l "$fqn" agent-service/module-metadata.yaml docs/contracts/contract-catalog.md docs/dfx/agent-service.yaml 2>&1
  find agent-service/src/main/java -name "${fqn}.java" -print
done
```

Expected: each FQN appears in exactly 4 surfaces (3 yaml/md + 1 java file).

- [ ] **Step 3: Catalog row count check (Rule R-D.f)**

```bash
# Count yaml schemas in docs/contracts/ matching v1
ls docs/contracts/*.v1.yaml | wc -l
# Count rows in §3 of contract-catalog
awk '/^## 3\./,/^## 4\./' docs/contracts/contract-catalog.md | grep -c "^| \`"
```

Compare the two numbers — they should be equal modulo headers.

- [ ] **Step 4: features.dsl + function-points.dsl parse check**

```bash
# Structurizr DSL has no python parser locally; do a sanity grep
grep -c "saa.id" architecture/features/features.dsl
grep -c "saa.id" architecture/features/function-points.dsl
```

Expected: counts grow by 6 (features) + 4 (function-points) vs pre-PR.

- [ ] **Step 5: Bail and fix if any check fails; otherwise proceed to Task 22**

---

### Task 22: PR creation

**Files:** (no edits — git/gh operations only)

- [ ] **Step 1: Push the branch**

```bash
git push -u origin feat/absorb-pr92-agent-service-l1-v1-2
```

- [ ] **Step 2: Create the PR**

```bash
gh.exe pr create \
  --base main \
  --head feat/absorb-pr92-agent-service-l1-v1-2 \
  --title "feat(agent-service-l1): absorb PR 92 v1.2 design (ADR-0155 — 6 reversals + 14 contracts + 5 SPIs + 66 features expansion)" \
  --body "$(cat <<'BODY'
## Summary

Absorb PR 92 (AgentService M1–M6 v1.2 design draft + self-audit, merged 2026-05-28) into the canonical L1 architecture under `architecture/docs/L1/agent-service/`. Anchored by **ADR-0155**.

### Six boundary-decision reversals (per ADR-0155)

1. **STM-03 sole-caller (TCC-03 owns transitions)** — self-audit H1 reversal; TTI-09 emits `INTERRUPT_REGISTERED` control event instead of calling STM-03 directly.
2. **responseSnapshot owner moves to TCC-03 terminal-state hook** — self-audit H4; eliminates multi-reply-channel race that made M1 the wrong owner.
3. **M6 prompt-construction reversal (v1.2)** — M6 becomes a messages-in-flight boundary aspect (not a prompt constructor). `BuiltPrompt` → `GovernedMessages`.
4. **ExecutorAdapter SPI three forms + InjectionMode** — Native / Third-party / Remote shapes are explicit; code home ⊥ deployment topology.
5. **IEQ three-channel physical topology** — Control / Data / Egress channels with independent bounded buffer + worker pool.
6. **CANCEL_RACE_RESOLVED_AS_{COMPLETED, CANCELLED}** — deterministic arbitration with reason codes in RunEvent.

### Surfaces touched

- **ADR**: `docs/adr/0155-agent-service-l1-v1-2-internal-module-design.{yaml,md}` (new).
- **L1 view files**: 6 surgical edits (logical, process, physical, development, scenarios, ARCHITECTURE.md authority + SPI appendix §4).
- **Feature inventories** (`features/*.md`): 6 files expanded from 8 to ~11 features each, adding v1.2 deltas.
- **Contract schemas**: 14 new `*.v1.yaml` under `docs/contracts/` (design_only).
- **Contract catalog**: 14 YAML rows + 5 SPI rows appended; refresh date bumped.
- **SPI Java placeholders**: 5 interfaces + 1 enum across 2 new packages (`service.runtime.spi.executor`, `service.runtime.spi.intercept`).
- **Module metadata + DFX**: 2 new spi_packages entries in each.
- **Architecture DSL**: 6 SAA Feature elements + 4 FunctionPoints.
- **Recurring defects**: new family `F-agent-service-internal-boundary-drift`.

### Test plan

- [ ] `bash gate/check_architecture_sync.sh` passes (run under WSL per Rule G-7).
- [ ] Rule R-D.f catalog integrity: 14 new contract yamls each have a row in contract-catalog.md §3.
- [ ] Rule G-1.1.b 4-way SPI parity: each new SPI FQN resolves in module-metadata + contract-catalog + DFX + on-disk .java.
- [ ] Rule G-14 advisory: 6 new FEAT- elements declare full saa.aiBoundary.* + saa.status = design_only.
- [ ] Rule G-9.b recurring-defect-families freshness: the new family entry diffs against the pre-PR yaml.

### Out of scope (deferred)

- Java production implementations of the 5 new SPIs (only `public interface` placeholders).
- TCK conformance suites for new SPIs.
- ArchUnit enforcers for new contract boundaries.
- L2 design documents.
- DSL `verification.dsl` edges (will land when SPIs ship).

🤖 Generated with [Claude Code](https://claude.com/claude-code)
BODY
)"
```

- [ ] **Step 3: Verify the PR opened cleanly**

```bash
gh.exe pr view --json url,state,title --jq '"\(.state) — \(.title)\n\(.url)"'
```

- [ ] **Step 4: Print the PR URL**

The PR URL is the deliverable of this plan.

---

## Self-review

### Spec coverage

- ✓ ADR-0155 (yaml + md) — Tasks 1, 2.
- ✓ 6 features/*.md expansions — Tasks 11–16.
- ✓ 5 SPI interface Java placeholders + 1 enum + 2 package-info — Tasks 3, 4.
- ✓ 14 *.v1.yaml schemas — Tasks 5, 6, 7.
- ✓ Contract catalog 14+5 rows — Task 8.
- ✓ module-metadata + DFX mirror — Task 9.
- ✓ SPI appendix §4 — Task 10.
- ✓ L1 view files surgical updates — Task 17.
- ✓ features.dsl + function-points.dsl — Tasks 18, 19.
- ✓ Recurring-defect-families update — Task 20.
- ✓ Gate verification + PR creation — Tasks 21, 22.

### Placeholder scan

- "TBD" / "implement later": none.
- "Add appropriate error handling": none — every contract has explicit error fields and ErrorClass references.
- "Write tests for the above" (without test code): none — gate verification commands are explicit; Java SPIs are design_only so no test code expected (deferred to W2 with explicit acceptance criterion in ADR-0155).
- "Similar to Task N": none — each task spells out its own complete edits.
- "Add new entry to recurring-defect-families.md (mirror)" was vague — refined in Task 20 Step 3 to "match the format used by other families" (the format is well-established and trivially parsable from any existing entry).

### Type consistency

- `ExecutorAdapter.execute` signature consistent across Task 3 Java SPI, Task 5 execution-request.v1.yaml schema, Task 5 agent-event.v1.yaml schema, Task 15 EDE-F09 feature description.
- `PlatformChatClient.invoke(messagesRef, modelRef)` consistent across Task 4 Java SPI, Task 6 governed-messages.v1.yaml schema, Task 16 TTI-F10 feature description.
- `ControlEvent.kind` enum (11 values) consistent in Task 5 control-event.v1.yaml + Task 14 TCC-F11 RESUME_ACCEPTED handler.
- `ErrorClass` 14 values consistent in Task 6 error-class.v1.yaml + Task 15 EDE-F11 feature description.
- `InjectionMode` 4 values consistent in Task 3 Java enum + Task 15 EDE-F10 + Task 17 development.md SPI package note.

### Files known to exist before plan starts

- `docs/contracts/run-event.v1.yaml` (pre-existing) — used as schema template in Tasks 5–7. Plan does NOT rewrite it; only references it.
- `architecture/docs/L1/agent-service/{ARCHITECTURE.md, logical.md, process.md, physical.md, development.md, scenarios.md, spi-appendix.md}` — all pre-existing per rc55 ADR-0143; plan modifies surgically.
- `agent-service/module-metadata.yaml` + `docs/dfx/agent-service.yaml` — pre-existing.
- `docs/contracts/contract-catalog.md` — pre-existing; plan appends rows.

---

## Execution Handoff

Plan complete and saved to `docs/superpowers/plans/2026-05-28-agent-service-l1-v1-2-absorption.md`. Two execution options:

**1. Subagent-Driven (recommended)** — I dispatch a fresh subagent per task, review between tasks, fast iteration. Best for this plan because Tasks 11–16 are 6 highly parallel features edits.

**2. Inline Execution** — Execute tasks in this session using executing-plans, batch execution with checkpoints. Slower but lets you watch each edit.

**Which approach?**
