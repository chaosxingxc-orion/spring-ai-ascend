---
date: 2026-06-11
status: approved
owner_directive: >
  Goal 2026-06-11 (post PR-179): (1) end-to-end tests of the agent
  capabilities with transactional consistency, including a test approach for
  dynamic-planning consistency the owner asked Claude to design; (2) 3–5
  finance scenarios covering single agent / agent team / tools / skills /
  memory, adding platform features where scenarios expose gaps; (3) a sanity
  refactor anchored on those tests.
---

# E2E scenarios + dynamic-planning transactional-consistency test design

## 1. What "transactional consistency" means on this platform

The platform deliberately has no distributed transaction manager; its
consistency promises are the composition of five existing mechanisms, so the
tests assert THOSE, not an imaginary 2PC:

| Mechanism | Promise under test |
|---|---|
| Run DFA (`RunStateMachine`) | every observed transition is legal; exactly one terminal state per attempt; FAILED→RUNNING bumps `attemptId` |
| Idempotency (`IdempotencyStore`) | a retried `message/send` (same tenant+messageId) re-executes NOTHING — replay returns the recorded task |
| Session memory (`SessionMemoryStore`) | the window contains exactly the turns of committed steps — no phantom entries from failed/abandoned attempts |
| Business facts (`BusinessFactPublisher`) | emissions correspond 1:1 with completed plan steps |
| Bus messaging (`AgentMessageBus`) | delivered + counted-dropped == published; no delivery after unsubscribe/cancel |

## 2. Dynamic-planning consistency — the test approach (owner-requested design)

"Dynamic planning" = the agent revises its plan mid-run (insert/skip/retry
steps, suspend for input, get cancelled). The platform does not ship a plan
engine (PlanState/RunPlanSheet are deferred by the architecture); the PLAN
lives in the scenario handler. What the platform owes is that the run-level
side effects stay coherent while the plan mutates. The approach:

**Effect-ledger + scripted-fault harness + invariant assertions.**

1. `EffectLedger` (test fixture): every side-effect seam the scenario touches
   (tool call, memory append, fact emission, bus publish) writes one record
   `(runId, attemptId, stepId, effectType, payloadHash)` to a synchronized
   ledger. The ledger is the ground truth of "what actually happened".
2. `ScriptedPlanHandler` (test fixture): an `AgentRuntimeHandler` executing a
   declared step list where any step can be scripted to `FAIL_ONCE`,
   `SUSPEND` (interrupted → input-required), or `REVISE` (replace the
   remaining plan mid-run — the dynamic part). Deterministic, no LLM.
3. Drive it END TO END through the real wire: boot `RuntimeApp`, call via
   `springai-ascend-client` (same path a customer uses), including the HITL
   `awaitingInput()` turn for SUSPEND and a second `sendText` with the SAME
   messageId for idempotent-retry probes.
4. Assert the invariants after each scripted scenario:
   - **I1 idempotent replay**: second send with the same messageId adds ZERO
     ledger records and returns the same taskId.
   - **I2 failure atomicity**: a FAIL_ONCE step leaves no ledger record for
     the failed step execution beyond its declared compensation; the retry
     attempt re-runs from the failed step, and `attemptId` increments —
     completed-step effects from attempt 1 are not duplicated by attempt 2
     (the handler checkpoints completed steps in agent state, which is the
     platform's documented checkpoint seam).
   - **I3 cancel barrier**: cancel during step N → run reaches CANCELLED and
     the ledger contains no record with stepId > N.
   - **I4 plan revision coherence**: after REVISE, effects of abandoned
     branch steps never appear; memory window equals the committed turn
     sequence exactly (newest-first).
   - **I5 concurrent duplicate**: two threads send the same messageId; the
     ledger shows exactly one execution; one caller gets the task, the other
     gets the duplicate-rejection or the replay.
   The DFA history is asserted via `RunRepository.findByTenantAndTask`
   (status + attemptId), making the Run kernel part of every invariant.

This converts "how do I test transaction consistency of dynamic planning"
into checkable algebra: ledger == f(committed plan), independent of timing.

## 3. The scenarios (finance-flavored; inspired by industry copilots, no code
or prompt copied from AgentScope DianJin or others)

| # | Name | Form exercised | Wire path |
|---|---|---|---|
| S1 | FX rate desk — 单智能体 + 工具 | YAML-defined agent, `http:` tool ref against a stub exchange-rate service | agent-sdk YAML → runtime → client SDK |
| S2 | Wealth advisor follow-up — 单智能体 + 记忆 | `SessionMemoryStore` window across 3 turns + `BusinessFactPublisher` ("customer prefers email") | runtime bus.memory seam |
| S3 | Loan calculator — 单智能体 + 技能 | agent-sdk skill/tool form: amortization calculation skill (`file:` java tool), input schema validation | agent-sdk skills/tools |
| S4 | Credit-review team — 智能体 Team | planner + risk-checker + drafter over `bus.messaging` request/reply with `KnowledgeSource` policy lookup | bus.messaging + bus.knowledge |
| S5 | Trade-order orchestration — 动态规划 + 事务一致 | the §2 harness: scripted plan with FAIL_ONCE / SUSPEND / REVISE / cancel / duplicate-send probes | full stack incl. client HITL |

All five run DB-free and deterministic (stub upstreams; no real LLM); they
live in the e2e example module as the customer-facing reference suite.
Feature gaps discovered while building them are fixed in the same change and
called out per scenario.

## 4. Sanity refactor (anchored on §2–§3 green)

Targets chosen by measurement (longest production methods / classes on the
branch), refactored WITHOUT behavior change — the new e2e suite plus the
existing 300+ tests are the anchor: extract long methods, flatten nested
constructs, remove dead parameters, one concept per class. Hard rules: no
nested classes beyond records/carriers, cyclomatic complexity of any touched
method ≤ 10, no public-surface change without a failing-test reason.
