---
file_id: GOVERNANCE-ESCALATIONS
governance_infra: true
purpose: "Legacy rules from pre-2026-05-28 namespace ratchet that need human review before disposition; not enforceable, kept for traceability only"
---

# Governance Escalations

Legacy rules from the pre-2026-05-28 namespace ratchet (ADR-0086) that remain in
limbo after the Phase 7 `docs/CLAUDE-deferred.md` cleanup (audit:
`docs/governance/retired-rules-audit.md`). Each rule here is unclear in scope or
target wave and needs design-team review before disposition (migrate to an
alphanumeric card, retire, or activate as new card).

These rules are NOT enforceable. They carry no gate hook. They are kept for
traceability only — the original re-introduction triggers stay verbatim so a
future wave can recover intent.

Disposition policy:
- If the trigger fires (W2 async orchestrator, W3 contract design, etc.) and the
  design team accepts the rule, the legacy entry is moved into the matching
  alphanumeric rule card's `deferred_sub_clauses` block under a `.legacyN`
  identifier (mirrors the Rule 16/17/26/27/28k.b migration pattern).
- If the rule is judged obsolete, the entry is deleted from this file and a
  one-line note added to `docs/governance/rule-history.md`.

---

## (legacy) Rule 7 — Resilience Must Not Mask Signals

**Re-introduction trigger**: first soft-fallback path committed (target: W2 LLM gateway).

**Rule**: Every silent-degradation path emits a loud, structured signal. Required for each fallback branch:

1. **Countable**: named metric counter (e.g. `*_fallback_total`).
2. **Attributable**: `WARNING+` log with run id and trigger reason at the branch entry.
3. **Inspectable**: run metadata carries a `fallback_events` list. Non-empty fallback_events = not "successful".
4. **Gate-asserted**: operator-shape gate asserts fallback counts are zero.

---

## (legacy) Rule 13 — P1 Cost-of-Use Constraints

**Re-introduction trigger**: first context-cache, cost-accounting, or small/large-model handoff capability committed (target: W3).

**Rule (draft)**: Every capability that invokes an LLM call declares its cost profile and cache eligibility. A gate check verifies that:
- Any capability marked `cache_eligible=true` is tested against a real provider cache-hit scenario (not mocked).
- Token budgets are declared in capability metadata; a gate asserts actual usage ≤ declared budget × 1.2.

This rule converts P1 roadmap intent into a pre-commit enforcement path.

---

## (legacy) Rule 15 — Streamed Handoff Mode Conformance

**Re-introduction trigger**: first `Flux<T>` / SSE return from `Orchestrator` or any northbound controller (target: W2).

**Rule**: Every streaming surface MUST declare and enforce:
- (a) Backpressure strategy (bounded buffer, drop, or error on overflow).
- (b) Cancellation propagation: caller cancel → `RunStatus.CANCELLED` set on the Run.
- (c) Heartbeat cadence ≤ 30 s — positive liveness signal, not absence of error.
- (d) Terminal frame carries `runId` + final `RunStatus` + error payload if applicable.
- (e) Typed progress event shape (`progress | cost | tool_call | partial_output | terminal`) — no raw `Object`.

Composes with: ARCHITECTURE.md §4 #11 (`streamed_handoff_mode`, `orchestrator_cancellation_handshake`).

---

## (legacy) Rule 19 — Runtime Hook Conformance

**Re-introduction trigger**: first W2 LLM gateway capability committed (first `ChatClient` call in production code path).

**Rule**: Every LLM invocation, tool call, and agent lifecycle transition MUST be invoked through `HookChain.invoke(...)`, not via a direct provider client call:

1. **No bypass**: an ArchUnit test (`HookChainConformanceTest`) asserts that no class outside the `hookchain` package calls `ChatClient.call(...)`, tool-execution methods, or `AgentLoopExecutor.reason(...)` directly. A violation is a compile-gate failure.
2. **Hook failure safety**: a hook that throws a checked exception MUST be caught; failure is logged at `WARNING+` with `runId` + hook class name; invocation continues. An unchecked exception propagates and fails the invocation — hooks are responsible for safety.
3. **Hook ordering**: hooks execute in `@Order` registration sequence; lower order = earlier execution. `BEFORE_*` hooks run ascending; `AFTER_*` hooks run ascending in the same order (not reversed).
4. **Gate-asserted**: the operator-shape gate asserts that at least one hook (PII filter or token counter) is registered and fires on every real-provider invocation.

Composes with: ARCHITECTURE.md §4 #16 (`runtime_hook_spi`).

---

## (legacy) Rule 22 — PayloadCodec Discipline [Deferred to W2]

**Re-introduction trigger**: first `Checkpointer` implementation that persists bytes to a durable store (target: W2 Postgres `PostgresCheckpointer`).

**Rule**: Every payload type that crosses a suspend/resume JVM boundary MUST have a registered `PayloadCodec<T>` with a stable `codecId` and `typeRef`. `RawPayload(Object)` MUST be rejected at the persistence boundary; it is valid only within a single in-process JVM execution context. `EncodedPayload(byte[], String codecId, String typeRef)` is the mandatory persistence wire format.

Composes with: ARCHITECTURE.md §4 #21 (`payload_codec_spi`); ADR-0022.

---

## (legacy) Rule 23 — Suspension Write Atomicity Enforcement [Deferred to W2]

**Re-introduction trigger**: first W2+ `Orchestrator` implementation that performs both a `RunRepository.save(suspended)` and a `Checkpointer.save(payload)` for suspension.

**Rule**: Any W2+ Orchestrator that performs the suspension pair MUST:
1. Document its atomicity strategy in Javadoc on the suspend-transition method.
2. Wrap both writes in a single Postgres `@Transactional` block (same `DataSource`), OR use the transactional outbox pattern (ADR-0007) for non-DB Checkpointer backends.
3. Enforce the contract with an integration test that kills the JVM mid-write and asserts post-restart consistency (e.g., via `ProcessBuilder` + DB state check).

An implementation that cannot demonstrate this contract is a ship-blocking defect per Rule D-5 (category: "Run lifecycle — checkpoint/resume atomicity").

Composes with: ARCHITECTURE.md §4 #23 (`suspension_write_atomicity_contract`); ADR-0024; ADR-0007.

---

## Authority

- Phase 7 audit: `docs/governance/retired-rules-audit.md`.
- Namespace ratchet authority: ADR-0086.
- Original deferral home (deleted 2026-05-28): `docs/CLAUDE-deferred.md`.
- Migrated siblings (folded into alphanumeric cards as `.legacyN` sub-clauses):
  - legacy Rule 16 → `docs/governance/rules/rule-R-K.md#deferred_sub_clauses(.legacy16)`
  - legacy Rule 17 (degradation half) → `docs/governance/rules/rule-R-M.md#deferred_sub_clauses(.legacy17a)`
  - legacy Rule 17 (resume re-auth half) → already operationalised by Rule R-J sub-clause .b (enforcers E105/E106)
  - legacy Rule 26 → `docs/governance/rules/rule-R-L.md#deferred_sub_clauses(.legacy26)`
  - legacy Rule 27 → `docs/governance/rules/rule-R-L.md#deferred_sub_clauses(.legacy27)`
  - legacy Rule 28k.b → `docs/governance/rules/rule-M-2.md#deferred_sub_clauses(.legacy28kb)`
- Retired (obsolete, no card needed): legacy Rules 8, 14, 18 — trigger conditions never fired; W2/W3/W4 scope not active.
