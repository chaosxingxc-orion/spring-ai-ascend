# Agent Runtime Remaining Refactor Master Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan phase-by-phase. Steps use checkbox (`- [ ]`) syntax for tracking. For this project, execute efficiently: do not spawn full subagent review loops for every small task. Use focused reviews only at phase boundaries, for test failures, or for high-risk state-machine/API changes.

**Goal:** Complete the remaining `agent-runtime` refactor into a clear, minimal runtime core organized around `common/access/session/queue/control/engine/app`, while preserving A2A behavior and keeping each phase locally committable.

**Architecture:** Build on commit `56c9f7a4 refactor(runtime): establish minimal five-layer foundation`. Keep Reactor as the accepted runtime queue/output stream API. Preserve control as the single task-state authority: `access -> control -> engine -> control -> access`. Move behavior first, then reduce package structure; avoid broad package renames until coupling is localized.

**Tech Stack:** Java 21, Maven, Spring Boot host wiring, Reactor, A2A Java SDK, openJiuwen agent-core-java, JUnit 5, ArchUnit.

---

## Execution Policy for This Plan

- Work from a clean tree after commit `56c9f7a4`.
- Make one local commit per phase. Do not push unless the user asks.
- Prefer direct implementation for clear mechanical steps.
- Use focused code review at each phase boundary, not per tiny file.
- If a test fails, capture failing test FQN, raw failure, and first stack frame before fixing.
- Do not read unrelated governance/architecture docs unless the user explicitly asks.
- Keep all changes scoped to `agent-runtime/**` plus `examples/agent-runtime-a2a-llm-e2e/**` tests/imports when needed.

---

## Current Baseline

Completed in commit `56c9f7a4`:

- `engine.openjiuwen` moved to `engine.adapters.openjiuwen`.
- `common.AgentResponseEvent` and related enums exist.
- `access.output.OutputChannelRegistry` exists.
- `A2aOutputRegistry` behavior is pinned with compatibility tests.
- `LocalA2aRuntimeHost.port(0)` overrides classpath `server.port`.
- Minimal regression suite and example smoke passed.

Current package spread still includes:

```text
access/config
access/core
access/model
access/protocol/a2a/*
engine/command
engine/event
engine/handler
engine/model
engine/port
queue/config
schema
session/config
session/core
session/model
```

Do not remove these mechanically. Remove only when the behavior has been moved and tests prove compatibility.

---

## Overall Phase Map

| Phase | Purpose | Commit Message | Risk |
|---|---|---|---|
| 1 | Replace A2A output registry internals with `OutputChannelRegistry` while keeping A2A public API | `refactor(runtime): back A2A output registry with output channels` | Low-medium |
| 2 | Introduce `AgentResponseEvent` mapping in access output path | `refactor(runtime): route access output through response events` | Medium |
| 3 | Add strong control output sink boundary, keeping control as sole fan-out authority | `refactor(runtime): make control output fanout explicit` | Medium-high |
| 4 | Stabilize session as `RuntimeSession` without broad persistence rewrite | `refactor(runtime): introduce runtime session model` | Medium |
| 5 | Normalize engine request/callback names without changing execution behavior | `refactor(runtime): normalize engine command API names` | Medium |
| 6 | Add engine provider seams only where used by adapter tests | `feat(runtime): add engine provider seams` | Medium |
| 7 | Improve openJiuwen adapter terminal release and streaming coverage | `fix(runtime): harden openJiuwen adapter lifecycle` | Medium |
| 8 | Reduce package clutter and stale comments after behavior is stable | `refactor(runtime): flatten stable runtime packages` | Medium |
| 9 | Decide schema/common migration with updated import count | `refactor(runtime): migrate schema primitives when localized` | High; do only if count is low |
| 10 | Final verification and cleanup | `test(runtime): verify refactored runtime stack` | Low |

---

# Phase 1: Back A2A Output Registry with OutputChannelRegistry

**Goal:** Replace `A2aOutputRegistry`'s internal storage/subscriber logic with `OutputChannelRegistry`, while preserving its public API and all existing A2A behavior.

**Why first:** Task 9 found `A2aOutputRegistry` references are localized to access and tests. This is the next lowest-risk behavior move.

**Files:**
- Modify: `agent-runtime/src/main/java/com/huawei/ascend/runtime/access/protocol/a2a/egress/A2aOutputRegistry.java`
- Modify: `agent-runtime/src/test/java/com/huawei/ascend/runtime/access/protocol/a2a/egress/A2aOutputRegistryTest.java`
- Possibly create: `agent-runtime/src/main/java/com/huawei/ascend/runtime/access/protocol/a2a/egress/A2aRuntimeOutputAdapter.java`

## Design

Keep this public API unchanged:

```java
public void append(A2aOutputHandle handle, A2aOutput output)
public List<A2aOutput> list(A2aOutputHandle handle)
public Runnable subscribe(A2aOutputHandle handle, Consumer<A2aOutput> subscriber)
```

Internally map:

```text
A2aOutputHandle -> RuntimeOutputHandle
A2aOutput       -> RuntimeOutput wrapping AgentResponseEvent
RuntimeOutput   -> A2aOutput for public list/subscribe compatibility
```

Because `A2aOutput` currently carries A2A-specific event objects, Phase 1 can use `RuntimeOutput.metadata()` or a private adapter record to preserve the original `A2aOutput`. If that becomes awkward, keep a small compatibility buffer but make `OutputChannel` the streaming source. Do not over-design.

## Steps

- [ ] Add/adjust `A2aOutputRegistryTest` to prove public API compatibility:

```java
@Test
void listAndSubscribeSeeSameTerminalReplayWhenBackedByOutputChannel() {
    A2aOutputRegistry registry = new A2aOutputRegistry();
    A2aOutputHandle handle = new A2aOutputHandle("tenant-1", "session-1", "task-1");
    A2aOutput accepted = output("task-1", false);
    A2aOutput terminal = output("task-1", true);

    registry.append(handle, accepted);
    registry.append(handle, terminal);

    List<A2aOutput> listed = registry.list(handle);
    List<A2aOutput> replayed = new ArrayList<>();
    Runnable unsubscribe = registry.subscribe(handle, replayed::add);
    unsubscribe.run();

    assertThat(listed).containsExactly(accepted, terminal);
    assertThat(replayed).containsExactly(accepted, terminal);
}
```

- [ ] Run red/guard test:

```bash
mvn -pl agent-runtime -Dtest=A2aOutputRegistryTest test
```

Expected: existing tests pass; new test may pass before implementation if current behavior already matches. That is acceptable because this phase is a safe internal refactor with characterization coverage.

- [ ] Implement `A2aOutputRegistry` using `OutputChannelRegistry` internally.

Minimal shape:

```java
public final class A2aOutputRegistry {
    private final OutputChannelRegistry channels = new OutputChannelRegistry();
    private final ConcurrentMap<A2aOutputHandle, CopyOnWriteArrayList<A2aOutput>> compatibility = new ConcurrentHashMap<>();

    public void append(A2aOutputHandle handle, A2aOutput output) {
        compatibility.computeIfAbsent(handle, ignored -> new CopyOnWriteArrayList<>()).add(output);
        channels.getOrCreate(toRuntimeHandle(handle)).write(RuntimeOutput.from(toEvent(handle, output)));
    }

    public List<A2aOutput> list(A2aOutputHandle handle) {
        return List.copyOf(compatibility.getOrDefault(handle, new CopyOnWriteArrayList<>()));
    }

    public Runnable subscribe(A2aOutputHandle handle, Consumer<A2aOutput> subscriber) {
        AtomicInteger delivered = new AtomicInteger();
        return channels.getOrCreate(toRuntimeHandle(handle)).stream().subscribe(output -> {
            List<A2aOutput> current = list(handle);
            int index = delivered.getAndIncrement();
            if (index < current.size()) {
                subscriber.accept(current.get(index));
            }
        })::dispose;
    }
}
```

If this looks too indirect in implementation, create `A2aRuntimeOutputAdapter` to hold conversion logic. Keep tests green.

- [ ] Run:

```bash
mvn -pl agent-runtime -Dtest=A2aOutputRegistryTest,A2aJsonRpcHandlerTest,A2aJsonRpcControllerTest test
```

Expected: PASS.

- [ ] Run end-to-end guard:

```bash
mvn -pl agent-runtime -Dtest=AgentServiceEndToEndIT test
```

Expected: PASS. If fails due Failsafe/Surefire naming, run the specific integration test with the configured plugin command used by the repo.

- [ ] Commit:

```bash
git add agent-runtime/src/main/java/com/huawei/ascend/runtime/access/protocol/a2a/egress \
        agent-runtime/src/test/java/com/huawei/ascend/runtime/access/protocol/a2a/egress
git commit -m "refactor(runtime): back A2A output registry with output channels"
```

---

# Phase 2: Route Access Output Through AgentResponseEvent

**Goal:** Make access output use `common.AgentResponseEvent` as the internal user-visible response model before A2A-specific mapping.

**Files:**
- Modify: `agent-runtime/src/main/java/com/huawei/ascend/runtime/access/output/RuntimeOutput.java`
- Modify: `agent-runtime/src/main/java/com/huawei/ascend/runtime/access/protocol/a2a/egress/A2aOutputMapper.java`
- Modify: `agent-runtime/src/main/java/com/huawei/ascend/runtime/access/protocol/a2a/egress/DefaultNotificationPort.java`
- Test: `agent-runtime/src/test/java/com/huawei/ascend/runtime/access/protocol/a2a/egress/*Test.java`

## Design

Current flow is likely:

```text
AgentNotification -> A2aOutputMapper -> A2aOutput -> A2aOutputRegistry
```

Target flow:

```text
AgentNotification -> AgentResponseEvent -> RuntimeOutput -> OutputChannelRegistry -> A2aOutputMapper -> A2A wire event
```

Keep external A2A assertions unchanged.

## Steps

- [ ] Add a test proving notification maps to `AgentResponseEvent` with correct response type/status.

Create or extend `A2aOutputMapperTest`:

```java
@Test
void mapsCompletedNotificationToFinalResponseEvent() {
    AgentNotification notification = completedNotification("tenant-1", "session-1", "task-1", "pong");

    AgentResponseEvent event = A2aOutputMapper.toResponseEvent(notification);

    assertThat(event.responseType()).isEqualTo(ResponseType.FINAL);
    assertThat(event.status()).isEqualTo(ResponseStatus.COMPLETED);
    assertThat(event.output()).isEqualTo("pong");
}
```

Use existing `AgentNotification` factory/constructor from current tests. If none exists, add a private helper in the test.

- [ ] Implement `A2aOutputMapper.toResponseEvent(AgentNotification)`.

Mapping rules:

```text
NotificationType/RunStatus running output -> DELTA/RUNNING
terminal completed -> FINAL/COMPLETED
terminal failed -> ERROR/FAILED with ErrorInfo
cancelled -> FINAL/CANCELLED
waiting/input -> FINAL/INPUT_REQUIRED
```

If current enums differ, map current `NotificationType` and `RunStatus` conservatively and keep existing A2A behavior tests green.

- [ ] Update `DefaultNotificationPort` to write `RuntimeOutput.from(event)` into the registry/channel path, while still delivering A2A outputs via existing mapper.

- [ ] Run:

```bash
mvn -pl agent-runtime -Dtest=A2aOutputRegistryTest,A2aJsonRpcHandlerTest,A2aJsonRpcControllerTest test
```

- [ ] Run:

```bash
mvn -pl agent-runtime -Dtest=AgentServiceEndToEndIT test
```

- [ ] Commit:

```bash
git add agent-runtime/src/main/java/com/huawei/ascend/runtime/access \
        agent-runtime/src/test/java/com/huawei/ascend/runtime/access \
        agent-runtime/src/test/java/com/huawei/ascend/runtime/bootstrap/AgentServiceEndToEndIT.java
git commit -m "refactor(runtime): route access output through response events"
```

---

# Phase 3: Make Control Output Fanout Explicit

**Goal:** Make the control layer's ownership of accepted output fanout explicit and testable, without changing behavior.

**Files:**
- Modify: `agent-runtime/src/main/java/com/huawei/ascend/runtime/control/EngineTaskControlAdapter.java`
- Possibly create: `agent-runtime/src/main/java/com/huawei/ascend/runtime/control/ControlOutputSink.java`
- Tests:
  - `agent-runtime/src/test/java/com/huawei/ascend/runtime/control/test/TaskflowEngineBridgeWhiteboxTest.java`
  - `agent-runtime/src/test/java/com/huawei/ascend/runtime/control/test/TaskControlServiceWhiteboxTest.java`

## Design

Current desired invariant:

```text
EngineDispatcher -> TaskControlClient -> EngineTaskControlAdapter -> TaskControlService accepts transition -> AccessLayerClient/NotificationPort fanout
```

Make this obvious in names and tests. Do not let engine write access output directly.

## Steps

- [ ] Add/strengthen whitebox test:

```java
@Test
void engineOutputIsFannedOutOnlyAfterControlAcceptsTransition() {
    // Use existing bridge test fixtures.
    // Arrange a task at expected revision.
    // Send engine output/completed event.
    // Assert task state/revision updated before access notification is recorded.
}
```

- [ ] If current `EngineTaskControlAdapter` mixes state update and fanout in one large method, extract a private method:

```java
private void publishAcceptedOutput(TaskResult result, EngineExecutionEvent event) { ... }
```

or create `ControlOutputSink` if a private method is not enough.

- [ ] Run:

```bash
mvn -pl agent-runtime -Dtest=TaskflowEngineBridgeWhiteboxTest,TaskControlServiceWhiteboxTest,EngineDispatcherTest test
```

- [ ] Commit:

```bash
git add agent-runtime/src/main/java/com/huawei/ascend/runtime/control \
        agent-runtime/src/test/java/com/huawei/ascend/runtime/control/test \
        agent-runtime/src/test/java/com/huawei/ascend/runtime/engine/EngineDispatcherTest.java
git commit -m "refactor(runtime): make control output fanout explicit"
```

---

# Phase 4: Introduce RuntimeSession Model Without Broad Rewrite

**Goal:** Rename/introduce runtime session semantics while preserving current repository behavior.

**Files:**
- Create: `agent-runtime/src/main/java/com/huawei/ascend/runtime/session/RuntimeSession.java`
- Create: `agent-runtime/src/main/java/com/huawei/ascend/runtime/session/SessionId.java`
- Create: `agent-runtime/src/main/java/com/huawei/ascend/runtime/session/ConversationId.java`
- Modify: `agent-runtime/src/main/java/com/huawei/ascend/runtime/session/api/SessionManager.java`
- Modify: `agent-runtime/src/main/java/com/huawei/ascend/runtime/session/core/SessionManagerImpl.java`
- Tests: add/modify session tests if present; otherwise add `RuntimeSessionTest`.

## Design

Do not delete current `session.model.Session` immediately. Add `RuntimeSession` as the new target model and adapt around it.

Minimal `RuntimeSession` fields:

```java
public record RuntimeSession(
        String tenantId,
        String userId,
        String agentId,
        String sessionId,
        String conversationId,
        List<Message> currentUserInput,
        Instant createdAt,
        Instant updatedAt,
        Instant lastAccessedAt,
        Instant expiresAt,
        String lastRequestId,
        String lastTaskId,
        String checkpointRef,
        String agentStateRef,
        Map<String, Object> metadata) { ... }
```

## Steps

- [ ] Add `RuntimeSessionTest` proving:
  - openJiuwen/native session objects cannot be stored because fields are strings/metadata only.
  - lists/maps are copied.
  - IDs are nonblank.

- [ ] Implement `RuntimeSession`, `SessionId`, `ConversationId`.

- [ ] Add adapter methods in `SessionManagerImpl` to create `RuntimeSession` internally, while still returning current `Session` if needed by public API.

- [ ] Run:

```bash
mvn -pl agent-runtime -Dtest='*Session*Test,AgentServiceEndToEndIT' test
```

- [ ] Commit:

```bash
git add agent-runtime/src/main/java/com/huawei/ascend/runtime/session \
        agent-runtime/src/test/java/com/huawei/ascend/runtime/session \
        agent-runtime/src/test/java/com/huawei/ascend/runtime/bootstrap/AgentServiceEndToEndIT.java
git commit -m "refactor(runtime): introduce runtime session model"
```

---

# Phase 5: Normalize Engine Command API Names

**Goal:** Align engine API naming with the target model without changing execution behavior.

**Files:**
- Modify/create around:
  - `agent-runtime/src/main/java/com/huawei/ascend/runtime/engine/api/*`
  - `agent-runtime/src/main/java/com/huawei/ascend/runtime/engine/command/*`
  - `agent-runtime/src/main/java/com/huawei/ascend/runtime/engine/model/*`
- Tests:
  - `DefaultEngineExecutionApiTest`
  - `EngineCommandEventFactoryTest`
  - `EngineCommandProcessorConcurrencyTest`

## Design

Current names use `EnqueueEngineExecutionRequest`, `EngineCommandEvent`, etc. Target names can be introduced as wrappers first:

```text
EngineExecutionRequest
EngineResumeRequest
EngineCancelRequest
```

Do not delete old request names until tests and callers are migrated.

## Steps

- [ ] Add new request records that wrap or mirror existing request data.
- [ ] Add overloads to `EngineExecutionApi`:

```java
EnqueueEngineStatus submit(EngineExecutionRequest request);
EnqueueEngineStatus resume(EngineResumeRequest request);
EnqueueEngineStatus cancel(EngineCancelRequest request);
```

Keep old methods temporarily delegating to new ones or vice versa.

- [ ] Update `TaskControlService` to call new methods.

- [ ] Run:

```bash
mvn -pl agent-runtime -Dtest=DefaultEngineExecutionApiTest,EngineCommandEventFactoryTest,EngineCommandProcessorConcurrencyTest,TaskControlServiceWhiteboxTest test
```

- [ ] Commit:

```bash
git add agent-runtime/src/main/java/com/huawei/ascend/runtime/engine \
        agent-runtime/src/main/java/com/huawei/ascend/runtime/control \
        agent-runtime/src/test/java/com/huawei/ascend/runtime/engine \
        agent-runtime/src/test/java/com/huawei/ascend/runtime/control/test
git commit -m "refactor(runtime): normalize engine command API names"
```

---

# Phase 6: Add Engine Provider Seams

**Goal:** Add minimal provider extension seams needed for future tool/memory/state/sandbox integration, without introducing a separate `service` concept. If a capability is implemented, express it as a provider implementation.

**Files:**
- Create under `agent-runtime/src/main/java/com/huawei/ascend/runtime/engine/provider/`:
  - `ToolProvider.java`
  - `McpToolProvider.java`
  - `MemoryProvider.java`
  - `StateProvider.java`
  - `SandboxProvider.java`
  - `AgentSessionHistoryProvider.java`
  - `InMemoryToolProviderRegistry.java` if a registry is needed by tests
- Tests under `agent-runtime/src/test/java/com/huawei/ascend/runtime/engine/provider/`.

## Design

Keep provider seams useful but tiny. Do not create `engine/service`. Example:

```java
public interface ToolProvider {
    String name();
    ToolResult invoke(ToolInvocation invocation);
}
```

If registry behavior is needed, name it as provider infrastructure, not service:

```java
public final class InMemoryToolProviderRegistry {
    private final Map<String, ToolProvider> providers = new ConcurrentHashMap<>();
    public void register(ToolProvider provider) { ... }
    public Optional<ToolProvider> find(String name) { ... }
}
```

Do not connect to openJiuwen tools yet unless a test requires it.

## Steps

- [ ] Add tests for provider registry register/find and duplicate replacement/rejection.
- [ ] Implement minimal provider interfaces and provider registry implementation.
- [ ] Add provider registry to `AgentExecutionContext` only if needed by adapter tests. If adding to context causes wide churn, stop and split a new phase.
- [ ] Run:

```bash
mvn -pl agent-runtime -Dtest='*Provider*Test,*ProviderRegistry*Test' test
```

- [ ] Commit:

```bash
git add agent-runtime/src/main/java/com/huawei/ascend/runtime/engine/provider \
        agent-runtime/src/test/java/com/huawei/ascend/runtime/engine/provider
git commit -m "feat(runtime): add engine provider seams"
```

---

# Phase 7: Harden openJiuwen Adapter Lifecycle

**Goal:** Ensure openJiuwen adapter releases runtime resources on all terminal outcomes and has blocking/streaming behavior covered.

**Files:**
- Modify: `agent-runtime/src/main/java/com/huawei/ascend/runtime/engine/adapters/openjiuwen/*`
- Tests: `agent-runtime/src/test/java/com/huawei/ascend/runtime/engine/adapters/openjiuwen/*Test.java`
- Example E2E if needed: `examples/agent-runtime-a2a-llm-e2e/src/test/java/com/huawei/ascend/examples/a2a/OpenJiuwenReactAgentA2aE2eTest.java`

## Steps

- [ ] Add test: handler calls `safeRelease` after completed result.
- [ ] Add test: handler calls `safeRelease` after failed result/exception.
- [ ] Add test: stream adapter maps interrupt to input-required equivalent signal, preserving existing behavior.
- [ ] Implement minimal lifecycle hardening.
- [ ] Run:

```bash
mvn -pl agent-runtime -Dtest=OpenJiuwenAgentRuntimeHandlerTest,OpenJiuwenMessageAdapterTest,OpenJiuwenStreamAdapterTest test
```

- [ ] Run example smoke:

```bash
mvn -pl agent-runtime -DskipTests install
mvn -f examples/agent-runtime-a2a-llm-e2e/pom.xml -Dtest=RuntimeAppBootTest,SampleA2aClientTest,A2aClientPerspectiveTest test
```

- [ ] Commit:

```bash
git add agent-runtime/src/main/java/com/huawei/ascend/runtime/engine/adapters/openjiuwen \
        agent-runtime/src/test/java/com/huawei/ascend/runtime/engine/adapters/openjiuwen \
        examples/agent-runtime-a2a-llm-e2e/src/test/java/com/huawei/ascend/examples/a2a
git commit -m "fix(runtime): harden openJiuwen adapter lifecycle"
```

---

# Phase 8: Flatten Stable Runtime Packages

**Goal:** Reduce package clutter only after behavior has been moved and guarded.

**Candidate moves:**

```text
access/core/AccessSubmissionService -> access/DefaultAccessRequestApi or access/AccessSubmissionService
access/model/* -> access or common if protocol-neutral
engine/handler/AgentExecutionContext -> engine/AgentExecutionContext or engine/spi context if user-facing
engine/port/TaskControlClient -> control.api callback naming if appropriate
queue/config remains if Spring-only
session/core/model/config only after RuntimeSession is stable
```

## Steps

- [ ] Generate package hit list:

```bash
find agent-runtime/src/main/java/com/huawei/ascend/runtime -maxdepth 3 -type d | sort
```

- [ ] For each package, move only if:
  - It contains 1-2 files.
  - Import count is small.
  - Behavior tests already cover it.

- [ ] Do one package group per commit if moves are non-trivial.

- [ ] Run package boundary tests:

```bash
mvn -pl agent-runtime -Dtest=RuntimePackageBoundaryTest test
```

- [ ] Run relevant touched tests.

- [ ] Commit:

```bash
git add agent-runtime/src/main/java agent-runtime/src/test/java examples/agent-runtime-a2a-llm-e2e
git commit -m "refactor(runtime): flatten stable runtime packages"
```

---

# Phase 9: Decide and Execute Schema/Common Migration

**Goal:** Migrate `schema` only when import count is low enough or when adapters isolate churn.

## Decision Gate

Run:

```bash
grep -R "com.huawei.ascend.runtime.schema" -n \
  agent-runtime/src/main/java \
  agent-runtime/src/test/java \
  examples/agent-runtime-a2a-llm-e2e/src/test/java 2>/dev/null | wc -l
```

- If count `< 30`: migrate package or selected records.
- If count `>= 30`: do not migrate; add mappers/adapters and defer.

## If Migrating

- [ ] Move low-level message/content types to `common` first:
  - `Message`
  - `Content`
  - `ContentType`
  - `Role`
- [ ] Keep compatibility type aliases or deprecated wrappers if Java allows cleanly; otherwise update imports mechanically.
- [ ] Run full runtime tests.
- [ ] Commit:

```bash
git add agent-runtime/src/main/java agent-runtime/src/test/java examples/agent-runtime-a2a-llm-e2e
git commit -m "refactor(runtime): migrate schema primitives to common"
```

---

# Phase 10: Final Verification and Cleanup

**Goal:** Prove the refactored runtime stack works and produce a clean branch.

## Steps

- [ ] Run runtime suite:

```bash
mvn -pl agent-runtime test
```

- [ ] Run example smoke:

```bash
mvn -pl agent-runtime -DskipTests install
mvn -f examples/agent-runtime-a2a-llm-e2e/pom.xml -Dtest=RuntimeAppBootTest,SampleA2aClientTest,A2aClientPerspectiveTest test
```

- [ ] Run targeted E2E if environment supports it:

```bash
mvn -f examples/agent-runtime-a2a-llm-e2e/pom.xml -Dtest=OpenJiuwenReactAgentA2aE2eTest test
```

If it requires external LLM credentials/server and fails due environment, record that explicitly.

- [ ] Search old package and stale names:

```bash
grep -R "runtime.engine.openjiuwen\|dispatch/dispatch\|engine/runtime\|access/protocol/async" -n agent-runtime examples/agent-runtime-a2a-llm-e2e 2>/dev/null || true
```

- [ ] Review git status:

```bash
git status --short
```

- [ ] Final commit if verification/docs changed:

```bash
git add .
git commit -m "test(runtime): verify refactored runtime stack"
```

---

## Progress Reporting Template

Use this after each phase:

```text
Overall: agent-runtime remaining refactor
Done: Phase N ...
Current commit: <sha> <message>
Verified: <commands>
Remaining: Phase N+1 ... Phase 10
Next: <exact phase and expected commit>
```

---

## Self-Review

### Spec Coverage

- A2A output replacement is planned first because coupling is localized.
- `AgentResponseEvent` integration is planned before schema migration.
- Control authority remains explicit.
- Session, engine providers, adapter lifecycle, package cleanup, and schema migration are all covered.
- Multiple local commits are built into each phase.

### Placeholder Scan

This plan intentionally has decision gates where current information is insufficient. Those gates produce explicit outcomes and do not hide implementation work behind vague placeholders.

### Type Consistency

- `OutputChannelRegistry`, `RuntimeOutput`, and `AgentResponseEvent` refer to types created in commit `56c9f7a4`.
- `engine.adapters.openjiuwen` is consistently used for adapter paths.
- `RuntimeSession` is introduced as a new model without immediately deleting current `Session`.
