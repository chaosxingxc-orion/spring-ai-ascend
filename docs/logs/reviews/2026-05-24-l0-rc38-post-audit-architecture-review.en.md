---
level: L0
view: scenarios
affects_level: L0, L1
affects_view: scenarios, development
proposal_status: review
date: 2026-05-24
authors: ["Codex architecture review"]
review_scope:
  - docs/logs/releases/2026-05-24-l0-rc38-audit-corrective-latent-correctness-and-deploy-packaging.en.md
  - docs/adr/0118-rc38-audit-corrective-latent-correctness-and-deploy-packaging.yaml
  - docs/governance/architecture-status.yaml
  - docs/governance/recurring-defect-families.yaml
  - docs/governance/recurring-defect-families.md
  - docs/contracts/http-api-contracts.md
  - docs/contracts/contract-catalog.md
  - docs/contracts/openapi-v1.yaml
  - docs/CLAUDE-deferred.md
  - agent-service/src/main/java/com/huawei/ascend/service/runtime/runs/spi/RunRepository.java
  - agent-service/src/main/java/com/huawei/ascend/service/runtime/orchestration/inmemory/SyncOrchestrator.java
  - agent-service/src/main/java/com/huawei/ascend/service/platform/web/runs/RunController.java
  - agent-service/src/main/java/com/huawei/ascend/service/platform/idempotency/IdempotencyStore.java
  - agent-service/src/main/java/com/huawei/ascend/service/platform/idempotency/IdempotencyHeaderFilter.java
  - agent-service/src/main/java/com/huawei/ascend/service/engine/spi/StatelessEngine.java
  - agent-service/src/main/java/com/huawei/ascend/service/engine/spi/AgentInvokeRequest.java
  - agent-service/src/main/java/com/huawei/ascend/service/engine/spi/StateDelta.java
  - agent-service/src/main/java/com/huawei/ascend/service/engine/adapter/InMemoryStatelessEngine.java
  - agent-service/src/main/java/com/huawei/ascend/service/session/ReflectionPatchHandler.java
  - agent-service/src/main/java/com/huawei/ascend/service/session/Session.java
  - agent-service/src/main/java/com/huawei/ascend/service/task/InMemoryTaskStateStore.java
  - agent-bus/src/main/java/com/huawei/ascend/bus/spi/s2c/ReflectionEnvelopeRouter.java
  - agent-bus/src/main/java/com/huawei/ascend/bus/spi/s2c/S2cCallbackEnvelope.java
  - agent-bus/src/main/java/com/huawei/ascend/bus/spi/s2c/S2cCallbackResponse.java
  - gate/test_architecture_sync_gate.sh
related_adrs:
  - ADR-0118
  - ADR-0116
  - ADR-0112
  - ADR-0108
  - ADR-0100
  - ADR-0102
  - ADR-0057
  - ADR-0097
  - ADR-0094
---

# L0 rc38 Post-Audit Architecture Review

## Conclusion

rc38 is directionally strong: the latest wave closes real runtime and deploy
defects, and the agent-driven L0 design remains appropriately layered. Dynamic
planning, skill capacity, memory/knowledge ownership, reflection, federation,
and engine contracts are not over-implemented for L0; most advanced surfaces
are correctly marked as design-only or W2/W3+ deferred.

However, L0 should not be declared fully complete yet. The remaining problems
are not broad architecture gaps; they are contract-truth and authority-chain
defects at shipped or partially shipped boundaries:

1. `RunRepository.updateIfNotTerminal` promises atomic compare-and-set semantics
   while still publishing a non-atomic default implementation.
2. The human HTTP contract and status ledger still describe cancel
   cross-tenant access as 403 `tenant_mismatch`, while the active Rule R-J,
   OpenAPI, ADR-0108/0116, and controller all define W0 shipped behavior as
   404 `not_found`.
3. Idempotency replay semantics split between W1 "409 conflict" and W2
   "return original response" depending on whether the reader starts from Java
   and ADRs or from OpenAPI and human contracts.
4. Some agent-service runtime-role contracts have moved from pure design into
   Java scaffolding/reference implementations, but their catalog/YAML status
   and verification evidence have not been promoted or explicitly split.
5. Some agent boundary carriers remain weakly typed and mutable at the exact
   places where L0 says state ownership is separated.

These are fixable without another top-level governance rule, but they should be
closed before the next release note claims final L0 closure.

## Rule D-1 Interpretation

Assumptions:

- The strongest reading of the rc38 release is that the architecture team wants
  to claim L0 completeness after fixing the latest audit-corrective findings.
- The review should judge the stored corpus, not untracked or unmerged work.
- L0 completeness includes contract truth, authority consistency, and enough
  implementation proof to prevent known recurring defect families from
  reappearing at the same interface boundary.

Root cause in one sentence:

`agent-service/src/main/java/com/huawei/ascend/service/runtime/runs/spi/RunRepository.java:40` requires the run-status update to be atomic, but
`agent-service/src/main/java/com/huawei/ascend/service/runtime/runs/spi/RunRepository.java:41` still describes the default as a "correct non-atomic
fallback", and `agent-service/src/main/java/com/huawei/ascend/service/runtime/runs/spi/RunRepository.java:44-50` implements exactly the read-then-save
shape that `F-nonatomic-run-status-write` says must not recur.

## Findings

### P1-1 - Atomic Run Status SPI Contract Is Still Self-Contradictory

Evidence:

- `RunRepository.updateIfNotTerminal` says "the re-read, terminal check, and
  write MUST be a single atomic step" at `RunRepository.java:39-40`.
- The same Javadoc then says "this default is a correct non-atomic fallback" at
  `RunRepository.java:41-42`.
- The default method performs `findById` followed by `save(mutator.apply(...))`
  at `RunRepository.java:44-50`.
- The rc38 family ledger explicitly leaves
  `F-nonatomic-run-status-write` as `partial`, with the durable ArchUnit guard
  deferred in `docs/governance/recurring-defect-families.md:552`.
- ADR-0118 also calls the durable structural fix deferred:
  `docs/adr/0118-rc38-audit-corrective-latent-correctness-and-deploy-packaging.yaml:95-97`.

Why this matters:

The concrete in-memory implementation now overrides the method with a
`ConcurrentHashMap.computeIfPresent` CAS, and `SyncOrchestrator` routes through
that override. That fixes today's dev-posture path. But the SPI itself still
publishes a non-atomic default for any future `RunRepository` implementation
that does not override it. Because the method name and Javadoc promise atomic
semantics, a future implementer can accidentally inherit the race while
believing the SPI has already solved it. This is a contract-level recurrence,
not just a missing test.

Recommended correction:

1. Make `RunRepository.updateIfNotTerminal(...)` abstract, or make the default
   fail closed with `UnsupportedOperationException` and a message requiring an
   implementation-specific atomic compare-and-set.
2. Add an ArchUnit or focused architecture test requiring every concrete
   production `RunRepository` implementation to override
   `updateIfNotTerminal(...)`.
3. Add a source-level guard that forbids production status-changing
   `RunRepository.save(...)` outside create-only paths and the CAS method. Keep
   test fixtures exempt.
4. Keep `F-nonatomic-run-status-write` as `partial` until that guard lands; only
   promote it to `structurally_addressed` or `monitoring` afterward.

Suggested wording for the SPI:

```java
Optional<Run> updateIfNotTerminal(UUID runId, UnaryOperator<Run> mutator);
```

or, if a default is required for binary compatibility:

```java
default Optional<Run> updateIfNotTerminal(UUID runId, UnaryOperator<Run> mutator) {
    throw new UnsupportedOperationException(
            "RunRepository implementations must provide an atomic compare-and-set updateIfNotTerminal");
}
```

This is the smallest fix. It avoids another governance-only rule and closes the
contract where the defect actually recurred.

### P1-2 - Shipped Cancel Cross-Tenant Semantics Still Drift Across Authority Surfaces

Evidence:

- The active Rule R-J kernel says `POST /v1/runs/{runId}/cancel` collapses
  cross-tenant access to HTTP 404 `not_found` at W0; 403 `tenant_mismatch` plus
  structured audit is the W1-widening direction, not the shipped W0 behavior
  (`CLAUDE.md:236`, `docs/governance/rules/rule-R-J.md:41-44`).
- ADR-0116 records the same cross-authority correction: Rule R-J previously
  overclaimed shipped 403+audit, while ADR-0108 defines 404 as W0 shipped and
  403+audit as W1 widening
  (`docs/adr/0116-rc36-kernel-truth-and-cancel-cas-corrective.yaml:64-68`).
- The live controller matches the corrected rule: `RunController.cancel(...)`
  returns 404 when the run is missing, tenant context is absent, or
  `Run.tenantId` differs from the request tenant
  (`RunController.java:156-162`).
- The machine OpenAPI also matches the corrected rule: cancel 404 is documented
  as `not_found (unknown runId OR cross-tenant access)`
  (`docs/contracts/openapi-v1.yaml:154-155`; the pinned test copy has the same
  text).
- The human HTTP contract is stale: it still says cancel returns 403
  `tenant_mismatch` when request tenant differs from `Run.tenantId`, then 404
  only when the run does not exist (`docs/contracts/http-api-contracts.md:121-125`).
- The contract catalog still summarizes cancel as `(200/403/404/409 per Rule
  R-J.b cancel re-authorization)` (`docs/contracts/contract-catalog.md:10`).
  That is ambiguous because 403 is valid for JWT/header tenant-claim mismatch,
  but not for the shipped run-owner mismatch branch of cancel.
- `architecture-status.yaml#run_lifecycle_spi.allowed_claim` still says the
  shipped HTTP cancel edge is `RunController.java#cancelRun (line 141)` with
  `403/404/409 semantics`, while the actual method is `cancel` at
  `RunController.java:150` and the shipped cross-tenant branch is 404
  (`docs/governance/architecture-status.yaml:669-676`).
- `docs/CLAUDE-deferred.md:296` says future resume/retry should behave with
  403 `tenant_mismatch` "exactly as Rule R-J.b enforces today on cancel", which
  is no longer true after ADR-0108/0116 alignment.
- `RunHttpContractIT.tenantMismatchReturns403` is a JWT claim vs `X-Tenant-Id`
  mismatch test on `POST /v1/runs`, not a cancel run-owner mismatch test
  (`RunHttpContractIT.java:169-186`). The test is valid, but its name and the
  architecture-status matrix make it too easy to treat JWT/header mismatch as
  proof of Rule R-J.b cancel re-authorization.

Why this matters:

The shipped code and machine contract are coherent, but the human-facing
contract layer is not. This can mislead client SDK authors, future reviewers,
and gate authors into reintroducing a 403 expectation for the current cancel
surface or into assuming the W1 audit widening has already shipped.

Recommended correction:

1. Update `docs/contracts/http-api-contracts.md` so cancel explicitly says:
   missing run or run-owner tenant mismatch returns 404 `not_found` at W0; 403
   `tenant_mismatch` is reserved for JWT/header mismatch today and for the
   future W1 widening described by ADR-0108.
2. Update `docs/contracts/contract-catalog.md` and
   `architecture-status.yaml#run_lifecycle_spi.allowed_claim` to remove the
   stale `cancelRun (line 141)` anchor and qualify the 403 branch.
3. Rewrite the deferred resume/retry draft in `docs/CLAUDE-deferred.md` so it
   no longer says cancel enforces 403 today. It can still say resume/retry will
   adopt the W1-widened tenant-mismatch semantics when that future wave lands.
4. Rename or re-scope `RunHttpContractIT.tenantMismatchReturns403` to something
   like `jwtClaimHeaderMismatchReturns403`, then add a distinct cancel
   cross-tenant regression test expecting 404 `not_found`.
5. Extend Gate Rule 16 or Rule 104 with a narrow semantic check for the cancel
   404 cross-tenant branch in `http-api-contracts.md` and the catalog. The
   current gates catch route existence/planned-state drift, but not this
   status-code semantic drift.

### P1-3 - Idempotency Replay Semantics Are Split Between W1 Conflict And W2 Replay

Evidence:

- The human HTTP contract says the same `Idempotency-Key` submitted twice
  returns the first response (`docs/contracts/http-api-contracts.md:93`).
- OpenAPI says the same key + same body returns the original response, while
  the same key + different body returns 409 `idempotency_body_drift`
  (`docs/contracts/openapi-v1.yaml:36`, `docs/contracts/openapi-v1.yaml:185-188`).
- The pinned OpenAPI test fixture repeats the same replay claim
  (`agent-service/src/test/resources/contracts/openapi-v1-pinned.yaml:36`,
  `agent-service/src/test/resources/contracts/openapi-v1-pinned.yaml:185-188`).
- The Java SPI and ADR define a different shipped behavior: L1 stops at
  `CLAIMED`; replay is W2; any existing row is returned to the caller so the
  caller can produce 409 (`IdempotencyStore.java:10-17`,
  `docs/adr/0057-durable-idempotency-claim-replay.md:59-65`,
  `docs/adr/0057-durable-idempotency-claim-replay.md:90-93`).
- `IdempotencyHeaderFilter` implements the SPI/ADR behavior: same hash returns
  409 `idempotency_conflict`; different hash returns 409
  `idempotency_body_drift` (`IdempotencyHeaderFilter.java:150-163`).
- `architecture-status.yaml` also agrees with the Java/ADR side: response
  replay is deferred to W2 (`docs/governance/architecture-status.yaml:360`).
- The HTTP IT only exercises a different-body duplicate, and its assertion
  accepts either `idempotency_body_drift` or `idempotency_conflict`
  (`RunHttpContractIT.java:221-251`). That hides the precise semantic split
  instead of pinning it.

Why this matters:

This is the same defect family as cancel semantics, but on a more central API
contract. Client SDKs reading OpenAPI will implement safe retry as "same key +
same body replays the original response." The shipped L1 runtime instead
returns 409 for any existing key until W2 response replay lands. Both behaviors
are defensible, but only one can be the shipped contract.

Recommended correction:

1. For current L1/W1 truth, update `docs/contracts/openapi-v1.yaml`,
   `openapi-v1-pinned.yaml`, and `http-api-contracts.md` to say:
   same key + same hash returns 409 `idempotency_conflict`; same key +
   different hash returns 409 `idempotency_body_drift`; response replay is W2.
2. If the intended public contract is replay today, implement response storage
   and replay in `IdempotencyStore`/`IdempotencyHeaderFilter` before claiming the
   OpenAPI text is true.
3. Tighten `RunHttpContractIT.duplicateIdempotencyKeyReturns409` so the
   different-body branch asserts exactly `idempotency_body_drift`, and add a
   same-body branch that asserts the current chosen behavior exactly.
4. Add a gate check that fails if OpenAPI or HTTP docs say "return original
   response" while `architecture-status.yaml#idempotency_store.allowed_claim`
   still says replay is deferred.

### P1-4 - TaskStateStore Tenant-Scope Guard Has A Check-Then-Put Race

Evidence:

- The SPI catalog says SPI implementations are thread-safe, and SPIs processing
  tenant-owned runtime data must carry tenant scope
  (`docs/contracts/contract-catalog.md:20`).
- `TaskStateStore` explicitly models decoupled Task/Session concurrency:
  one session may execute multiple tasks, and one task may drift across multiple
  sessions (`TaskStateStore.java:13-15`).
- `InMemoryTaskStateStore.save(...)` tries to enforce tenant scope by reading
  `store.get(taskId)`, checking tenant mismatch, and then calling `store.put(...)`
  (`InMemoryTaskStateStore.java:39-46`).
- Because `get` and `put` are separate operations, two tenants can concurrently
  save the same new `taskId`: both observe `existing == null`, both pass the
  guard, and the later `put` silently overwrites the earlier tenant's entry.
- The prior `RunRepository` family shows this exact shape is risky: a
  `ConcurrentHashMap` is not enough when the invariant spans read-check-write.

Why this matters:

This is currently most dangerous as a promotion trap. If `TaskStateStore` is
treated as runtime-shipped or used in tests as a trusted reference
implementation, its tenant-scope guarantee is weaker than its Javadoc and the
contract catalog claim. It is also the same recurring concurrency pattern as
`F-nonatomic-run-status-write`, just moved into Task control state.

Recommended correction:

1. Replace the check-then-put with `ConcurrentHashMap.compute(...)` or
   `putIfAbsent` plus a compare-and-replace loop that makes tenant ownership a
   single atomic decision.
2. Add a deterministic concurrency test where two tenants race to save the same
   `taskId`; exactly one wins and the loser receives the cross-tenant overwrite
   failure.
3. Register this as a sibling occurrence under the non-atomic read-check-write
   family, or explicitly broaden that family beyond Run status writes if the
   team wants one reusable prevention guard.
4. If `TaskStateStore` remains scaffold-only, mark it as
   `implemented_unverified` and say the thread-safety/tenant-scope invariant is
   not yet proven.

### P2-1 - rc38 Maven Test Baseline Is Not Reproducible From The Canonical Command

Evidence:

- AGENTS.md requires Java verification to use `./mvnw clean verify`, not a
  weaker or ambiguous command (`AGENTS.md:32`).
- The rc38 release note records `mvn -Pquality verify` as the Maven evidence at
  `docs/logs/releases/2026-05-24-l0-rc38-audit-corrective-latent-correctness-and-deploy-packaging.en.md:103`.
- The rc38 baseline declares `maven_tests_green: 383` at
  `docs/governance/architecture-status.yaml:139`, and the release table repeats
  383 at
  `docs/logs/releases/2026-05-24-l0-rc38-audit-corrective-latent-correctness-and-deploy-packaging.en.md:95`.
- Running the canonical local command during this review, `./mvnw.cmd clean
  verify`, returned success, but the generated Surefire/Failsafe XML reports
  summed to 396 test cases, 39 skipped, 0 failures, and 0 errors.

Why this matters:

The build is green, so this is not a functional failure. The problem is that the
numeric baseline is not reproducible from the stated operational convention. A
future reviewer cannot tell whether `maven_tests_green` means total discovered
tests, executed non-skipped tests, CI-only tests with services available, or a
profile-specific count.

Recommended correction:

1. Update the rc38 release evidence to cite the canonical wrapper command, for
   example `./mvnw clean verify` or `./mvnw.cmd clean verify` depending on host.
2. Define the counting formula for `maven_tests_green` in
   `architecture-status.yaml`: total XML test cases, non-skipped passed tests,
   CI environment only, or profile-specific total.
3. If the intended canonical value remains 383, add the exact extractor command
   used to compute it and explain why local `clean verify` reports a different
   total.

### P2-2 - Gate Self-Test Harness Prints Shell Errors After Reporting PASS

Evidence:

- `bash gate/test_architecture_sync_gate.sh` exits 0 and reports
  `Tests passed: 226/226`.
- The same run prints shell errors afterward:
  `_fixtures_root: unbound variable` and a command-substitution syntax error.
- The command-substitution source is visible at
  `gate/test_architecture_sync_gate.sh:6302`, where backticks inside a
  double-quoted success message execute ``rc<N> Wave <M>`` as shell syntax.
- The undefined variable sites are visible at
  `gate/test_architecture_sync_gate.sh:6395`, `gate/test_architecture_sync_gate.sh:6430`,
  and `gate/test_architecture_sync_gate.sh:6463`.

Why this matters:

This does not invalidate the 226/226 fixture result, but it weakens test-honesty
evidence. A self-test harness should not emit shell errors after declaring
success; over time that trains reviewers to ignore red output.

Recommended correction:

1. Replace the backticks in the line 6302 message with plain quotes or escaped
   backticks.
2. Initialize `_fixtures_root` in the relevant fixture helpers, or route those
   helpers through the same scratch directory variable used by the rest of the
   harness.
3. Add a harness smoke assertion that `bash gate/test_architecture_sync_gate.sh
   2>&1` contains no `unbound variable`, `syntax error`, or command-substitution
   diagnostics when exit code is 0.

### P2-3 - Agent-Service Runtime-Role Contracts Need A Clear Promotion Boundary

Evidence:

- ADR-0100 says rc24 should ship the `StatelessEngine` /
  `ContextProjector` / `TaskStateStore` reference implementations plus the
  `AgentInvokeRequest` runtime path
  (`docs/adr/0100-rc22-agent-service-l1-runtime-role-decomposition.yaml:115-126`).
- Those Java surfaces now exist in the stored corpus:
  `AgentInvokeRequest.java:30-54`, `StatelessEngine.java:34-46`,
  `InMemoryStatelessEngine.java:32-53`, `InMemoryContextProjector.java`, and
  `InMemoryTaskStateStore.java`.
- `docs/contracts/agent-invoke-request.v1.yaml` still says
  `status: design_only`, `runtime_enforced: false`, and defines the promotion
  trigger as "First InMemoryStatelessEngine implementation lands in
  agent-service" (`docs/contracts/agent-invoke-request.v1.yaml:13-17`). That
  trigger is already satisfied by `InMemoryStatelessEngine.java`.
- The contract catalog still labels the SPI rows and YAML row as `design_only`
  while also saying the reference implementations ship in rc24
  (`docs/contracts/contract-catalog.md:41-43`, `docs/contracts/contract-catalog.md:121`).
- A targeted search did not find tests named around `AgentInvokeRequest`,
  `InMemoryStatelessEngine`, `ContextProjector`, or `TaskStateStore` under the
  existing test trees. That means the surfaces are implemented or scaffolded,
  but not independently promoted to `test_verified`.
- `architecture-status.yaml#value_based_yield_primitive.allowed_claim` says
  "Design only - StatelessEngine.execute returns Mono<StateDelta>..." even
  though ADR-0112 explicitly says the current shipped state returns synchronous
  `StateDelta` and the `Mono<StateDelta>` shape is W0.5+ forward direction
  (`docs/governance/architecture-status.yaml:774-783`,
  `docs/adr/0112-engine-stateless-executor-value-based-yield.yaml:49-63`,
  `StatelessEngine.java:34-46`).

Why this matters:

This is not evidence of over-design. The decomposition itself is sensible:
the service retains the read-modify-write closure, the engine side stays
pure-function, and Task/Session state has dedicated SPI ownership. The problem
is that the authority layer no longer states exactly which parts are merely
design-only, which parts are Java scaffolds, and which parts are runtime-wired
and test-verified. That ambiguity weakens L0 contract truth for the agent
runtime core.

Recommended correction:

1. Decide the intended promotion state for `agent-invoke-request.v1.yaml`:
   either promote it to `schema_shipped` / `runtime_enforced` with focused
   tests, or keep it design-only but change the promotion trigger to the real
   runtime boundary: first orchestrator path that constructs
   `AgentInvokeRequest` and invokes `StatelessEngine`.
2. In `contract-catalog.md`, split "SPI scaffold exists" from "runtime path is
   enforced". For example: `StatelessEngine` = `implemented_unverified`
   interface + reference impl; `AgentInvokeRequest` YAML = design-only until
   orchestrator wiring and tests land.
3. Add narrow tests for the existing scaffolds if they are intended to be more
   than compile-only surfaces: constructor required-field enforcement for
   `AgentInvokeRequest`, no-state/no-I/O behavior for `InMemoryStatelessEngine`,
   tenant isolation for `InMemoryTaskStateStore`, and projection invariants for
   `InMemoryContextProjector`.
4. Reword the value-based-yield status row so it mirrors ADR-0112: current Java
   returns synchronous `StateDelta`; `Mono<StateDelta>` plus nullable
   `InterruptSignal` is the future W0.5+ design.

### P2-4 - Online-Evolution SPI Java Anchors Still Point At Pre-.spi Packages

Evidence:

- The contract catalog correctly places `ReflectionEnvelopeRouter` under
  `com.huawei.ascend.bus.spi.s2c` and `SlowTrackJudge` under
  `com.huawei.ascend.evolve.online.spi`
  (`docs/contracts/contract-catalog.md:44-45`).
- The actual Java packages match the catalog:
  `ReflectionEnvelopeRouter.java:1` and `SlowTrackJudge.java:1`.
- `ReflectionPatchHandler` still links to
  `com.huawei.ascend.bus.s2c.ReflectionEnvelopeRouter`, which omits the
  `.spi` package segment (`ReflectionPatchHandler.java:8`).
- `ReflectionEnvelopeRouter` still references
  `com.huawei.ascend.evolve.online.SlowTrackJudge`, which omits the `.spi`
  package segment (`ReflectionEnvelopeRouter.java:8-10`).
- The same source-level Javadoc blind spot appears in the S2C envelope classes:
  `S2cCallbackEnvelope` and `S2cCallbackResponse` still say they live in
  `runtime.s2c.spi`, while their actual package is `com.huawei.ascend.bus.spi.s2c`
  (`S2cCallbackEnvelope.java:18-21`, `S2cCallbackResponse.java:17-20`).
- Gate Rule 108 only scans `docs/governance/rules/*.md` and
  `docs/governance/principles/P-*.md` for `Class.method` anchors
  (`gate/check_architecture_sync.sh:6089-6102`), so stale fully qualified
  Javadoc references inside Java source are outside the guard's scope.

Why this matters:

This is a small defect, but it is the same family shape as prior path and
Java-anchor truth waves: the compile path is green because these are Javadoc
anchors/prose, while the human contract points at non-existent or pre-move
types. Given rc27 explicitly moved these SPIs under `.spi`, the stale anchors
should be cleaned before the online-evolution and S2C designs are used as
development guides.

Recommended correction:

1. Update the two Javadoc anchors to the real packages:
   `com.huawei.ascend.bus.spi.s2c.ReflectionEnvelopeRouter` and
   `com.huawei.ascend.evolve.online.spi.SlowTrackJudge`.
2. Update the S2C envelope comments to say `com.huawei.ascend.bus.spi.s2c`, not
   `runtime.s2c.spi`.
3. Extend the Java-anchor truth guard or a lightweight source-grep fixture to
   catch fully qualified Javadoc references to known pre-.spi packages in
   `agent-*` source trees.

### P2-5 - Weakly Typed Mutable Carriers Can Undermine The Pure-Function Engine Boundary

Evidence:

- ADR-0100 defines `StatelessEngine` as a pure-function boundary: the engine
  holds no state and returns only a state increment
  (`docs/adr/0100-rc22-agent-service-l1-runtime-role-decomposition.yaml:93-105`).
- `StatelessEngine` Javadoc repeats that the engine must not hold persistent
  state and that the orchestrator owns the read-modify-write closure
  (`StatelessEngine.java:6-9`, `StatelessEngine.java:36-46`).
- `AgentInvokeRequest` checks that its maps/lists are non-null, but stores the
  caller-provided `Map` and `List` instances directly
  (`AgentInvokeRequest.java:30-52`).
- `StateDelta` has no compact constructor, no non-null checks, no defensive
  copies, and represents `runStatusTransition` as a raw `String`
  (`StateDelta.java:23-29`).
- The YAML contract says `run_status_transition` is an enum with values
  `[no_change, succeeded, failed, suspended, yielded]`
  (`docs/contracts/agent-invoke-request.v1.yaml:132-135`), but the Java type can
  carry any string.
- The local `Session` record shows the safer pattern already exists in the
  codebase: it requires non-null fields and defensively copies lists/maps
  (`Session.java:32-42`).

Why this matters:

The engine can remain "stateless" in class fields while still mutating the
mutable maps handed to it through `AgentInvokeRequest`, or returning mutable
delta maps that the orchestrator later observes after mutation. That creates a
hidden side channel across the intended Service/Engine boundary. The raw string
transition also lets invalid state hints cross the boundary until the future
orchestrator integration has to reject them late.

Recommended correction:

1. Give `AgentInvokeRequest` defensive `Map.copyOf` / `List.copyOf` treatment
   for all top-level collection fields, and decide whether nested maps/lists
   need deep copy or schema-normalized immutable records.
2. Add a compact constructor to `StateDelta` with non-null checks, defensive
   copies, and an enum or sealed value for `runStatusTransition`.
3. Add tests proving external mutation after construction cannot affect
   `AgentInvokeRequest` or `StateDelta`.
4. For memory write intents, consider replacing `List<Map<String,Object>>` with
   a small typed `MemoryWriteIntent` record before the GraphMemoryRepository
   path becomes runtime-enforced.

## Reverse Defect Taxonomy

The findings above cluster into repeatable defect classes. The important point
is that each class predicts sibling bugs; fixing only the cited line is not
enough.

### Class A - Shipped-Vs-Future Semantic Drift

Observed examples:

- Cancel cross-tenant behavior: W0 404 in code/OpenAPI/Rule R-J, stale 403 in
  human contracts and status prose.
- Idempotency replay: W1 409 conflict in Java/ADR/status, stale replay-original
  text in OpenAPI and HTTP docs.
- Value-based yield: current synchronous `StateDelta`, stale status prose saying
  `Mono<StateDelta>` is the design-only claim rather than the future direction.

Likely hidden bug pattern:

Whenever a future ADR defines a widening path, older human contracts may absorb
the future behavior as if it already shipped. Search for "W1 widening",
"runtime impl in rc", "future direction", "response replay", and "returns 403"
across contracts and status rows after each corrective wave.

Structural fix:

Add a "current / forward" template for every allowed_claim and public contract
row:

- Current shipped behavior.
- Forward target.
- Promotion trigger.
- Runtime proof and test proof.

### Class B - Atomicity Claimed In Prose But Not Enforced In Code

Observed examples:

- `RunRepository.updateIfNotTerminal` has an atomic contract but a non-atomic
  default.
- `InMemoryTaskStateStore.save` has a tenant-scope check but performs
  `get -> check -> put`.

Likely hidden bug pattern:

Any use of `ConcurrentHashMap` with a multi-step invariant can still race. The
dangerous shapes are `get` followed by `put/save`, `findById` followed by
`save`, and "validate then write" helper methods without a single atomic
primitive.

Structural fix:

For every tenant-owned or status-owned repository reference implementation,
grep for `get(` / `findById(` followed by `put(` / `save(` in the same method.
Require `compute`, `putIfAbsent`, compare-and-replace, database CAS, or a
transactional boundary for cross-field invariants.

### Class C - Scaffold Promotion Drift

Observed examples:

- `AgentInvokeRequest`, `StatelessEngine`, `ContextProjector`, and
  `TaskStateStore` exist in Java, while catalog/YAML/status still call the
  carrier design-only or lack test evidence.
- Online-evolution and federation SPI shells exist, but the runtime envelope
  routing remains design-only.

Likely hidden bug pattern:

A wave lands "only a scaffold", later waves land reference implementations, but
the status row never splits "interface exists", "reference impl exists",
"runtime path uses it", and "tests enforce it".

Structural fix:

Track SPI surfaces with four independent booleans:

- interface_declared
- reference_impl_present
- runtime_path_wired
- contract_test_verified

Do not use one `design_only` label to cover all four states.

### Class D - Java Anchor Truth Scope Too Narrow

Observed examples:

- `ReflectionPatchHandler` links to a non-existent pre-.spi
  `com.huawei.ascend.bus.s2c.ReflectionEnvelopeRouter`.
- `ReflectionEnvelopeRouter` links to pre-.spi
  `com.huawei.ascend.evolve.online.SlowTrackJudge`.
- S2C envelope Javadocs still say `runtime.s2c.spi` even though the actual
  package is `bus.spi.s2c`.

Likely hidden bug pattern:

Gates catch governance-card anchors but not Java-source Javadocs, package-info
files, or fully qualified type prose that does not use `Class.method` shape.

Structural fix:

Add a source-level stale-package scan for known moved package prefixes and a
small allowlist for explicitly historical text.

### Class E - Weakly Typed Map Contracts At Agent Boundaries

Observed examples:

- `AgentInvokeRequest` and `StateDelta` use `Map<String,Object>` and
  `List<Map<String,Object>>` without defensive copying or schema validation.
- `StateDelta.runStatusTransition` is a raw string while YAML declares a closed
  enum.

Likely hidden bug pattern:

As the agent runtime becomes more dynamic, opaque maps can smuggle mutable
state, invalid enum values, tenant scope omissions, or memory-write intents that
skip policy hooks. This becomes much harder to contain once reflection,
knowledge, and memory write paths become runtime-enforced.

Structural fix:

Use typed carrier records for runtime-enforced paths and keep maps only at
explicitly external serialization boundaries. When maps are unavoidable, copy
defensively and validate against a small schema before crossing component
boundaries.

## Architecture Design Assessment

No broad redesign is recommended.

- Dynamic planning is correctly staged: `plan-projection.v1.yaml` is
  design-only and explicitly bridges W2 scheduler admission to the W4 planner
  toolset without forcing premature Java types.
- Skill capacity is appropriately W1 decision-envelope only: the matrix is
  shipped, `ResilienceContract.resolve(tenant, skill)` consults it, and the
  actual `Run`/step suspension transition remains deferred to Rule R-K.c.
- Memory and knowledge ownership boundaries remain clear: business ontology and
  domain facts stay C-side by default; S-side emits proposals/events and does
  not directly mutate the customer graph.
- Reflection and federation contracts are correctly marked design-only until the
  router/broker choices land.
- The recurring-defect family ledger is useful, not over-designed, but its value
  depends on closing families at the actual contract boundary rather than only
  registering them.
- The agent-service decomposition is not over-designed: `StatelessEngine`,
  `AgentInvokeRequest`, `ContextProjector`, and `TaskStateStore` are reasonable
  L1 boundaries for separating read-modify-write orchestration from
  pure-function engine computation. The needed correction is promotion clarity
  and focused tests, not removal of the boundaries.
- Online evolution and federation remain correctly staged as design-only
  envelopes/SPI scaffolds. The stale Java anchors in P2-4 are factual cleanup,
  not evidence that ReflectionEnvelope or FederationGateway should be removed
  from the L0 roadmap.

The one place to avoid over-design is the remediation itself: do not add another
top-level governance rule for P1-1. The smallest sufficient closure is to make
the Java SPI enforce its own atomic contract and add one architecture test that
prevents future implementations from inheriting a non-atomic fallback.

## Systematic Sweep Notes

The review did not stop at the first failure. I swept these authority groups:

- Contract truth: HTTP human contracts, OpenAPI, pinned OpenAPI, Java
  controller behavior, Java SPI contracts, and contract catalog summaries.
- Authority truth: CLAUDE.md kernels, per-rule cards, ADR-0100/0102/0108/0112,
  ADR-0116/0118 corrective notes, architecture-status allowed claims, deferred
  rules, and recurring-defect family entries.
- Constraint truth: gate Rule 16/104 scope, gate self-test harness, baseline
  counts, enforcer rows, ADR files, and architecture graph generation.
- Agent-driven architecture: dynamic planning, skill capacity, runtime-role
  decomposition, memory/knowledge ownership, reflection envelope, federation
  envelope, S2C, and evolution scope.

No additional broad redesign need surfaced in dynamic planning, skill capacity,
memory/knowledge ownership, reflection, or federation. The remaining issues are
localized authority/contract inconsistencies and verification evidence gaps.

## Verification Performed

- `bash gate/check_parallel.sh` - PASS, 135/135 rules.
- `bash gate/test_architecture_sync_gate.sh` - exit 0, 226/226 fixtures; residual
  shell diagnostics noted in P2-2.
- `python gate/build_architecture_graph.py --check --no-write` - 471 nodes, 844
  edges, validation OK.
- `./mvnw.cmd clean verify` - exit 0.
- Targeted `rg` sweeps over cancel semantics, tenant mismatch semantics,
  idempotency replay semantics, task-state tenant concurrency, mutable agent
  carriers, agent-service runtime-role contracts, online-evolution/S2C SPI
  anchors, status baselines, and recurring-defect families.
- Baseline spot checks: 42 active CLAUDE rule headings, 103 top-level ADR
  files, 168 enforcer rows, and 13 recurring-defect families.
- `git diff --check` - clean after this expanded review document update.
