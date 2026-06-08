# Agent Runtime Minimal Five-Layer Refactor Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Refactor `agent-runtime` toward the minimal `common/access/session/queue/control/engine/app` runtime shape while preserving a working A2A -> control -> engine -> output path.

**Architecture:** Keep the existing working runtime flow and reduce it surgically. Reactor remains an accepted queue API dependency. The engine adapter namespace becomes `engine.adapters.openjiuwen` so future framework adapters can sit beside it without changing engine core. Control remains the task lifecycle authority; engine reports execution outcomes to control, and access renders user-visible output.

**Tech Stack:** Java, Maven, Spring Boot host wiring, Reactor queue streams, A2A Java SDK, openJiuwen agent-core-java, JUnit 5.

---

## User Decisions Locked In

1. `queue` may expose Reactor (`Flux`) as the runtime queue API dependency.
2. `engine/openjiuwen` must move to `engine/adapters/openjiuwen` because it is one engine adapter among potentially many.
3. Unknowns are not pre-decided. Implement the minimum working refactor first; ask only when blocked by a concrete design choice.
4. Locked code scope: `agent-runtime/**` plus tests/examples that compile against `agent-runtime`.

---

## Current Code Anchors

Read these before executing tasks:

- `agent-runtime/src/main/java/com/huawei/ascend/runtime/schema/AgentRequest.java` — current request model.
- `agent-runtime/src/main/java/com/huawei/ascend/runtime/schema/AgentResponse.java` — current response model.
- `agent-runtime/src/main/java/com/huawei/ascend/runtime/access/protocol/a2a/jsonrpc/A2aJsonRpcHandler.java` — current A2A JSON-RPC ingress.
- `agent-runtime/src/main/java/com/huawei/ascend/runtime/access/protocol/a2a/egress/A2aOutputRegistry.java` — current output buffer/subscriber registry.
- `agent-runtime/src/main/java/com/huawei/ascend/runtime/control/api/TaskControlApi.java` — current control API.
- `agent-runtime/src/main/java/com/huawei/ascend/runtime/control/TaskControlService.java` — current task lifecycle authority.
- `agent-runtime/src/main/java/com/huawei/ascend/runtime/engine/EngineDispatcher.java` — current engine-to-control routing.
- `agent-runtime/src/main/java/com/huawei/ascend/runtime/engine/command/EngineWorker.java` — current engine queue consumer.
- `agent-runtime/src/main/java/com/huawei/ascend/runtime/engine/openjiuwen/OpenJiuwenAgentRuntimeHandler.java` — current openJiuwen adapter base.
- `agent-runtime/src/main/java/com/huawei/ascend/runtime/app/RuntimeApp.java` — current embeddable entry point.

---

## File Structure Target for This Minimal Pass

### Create

- `agent-runtime/src/main/java/com/huawei/ascend/runtime/common/ResponseMode.java`
  - Request execution mode: `BLOCKING`, `STREAMING`.
- `agent-runtime/src/main/java/com/huawei/ascend/runtime/common/ResponseType.java`
  - User-visible event type: `TASK`, `DELTA`, `FINAL`, `ERROR`.
- `agent-runtime/src/main/java/com/huawei/ascend/runtime/common/ResponseStatus.java`
  - User-visible task status: `ACCEPTED`, `RUNNING`, `INPUT_REQUIRED`, `COMPLETED`, `FAILED`, `CANCELLED`.
- `agent-runtime/src/main/java/com/huawei/ascend/runtime/common/ErrorInfo.java`
  - Common error payload.
- `agent-runtime/src/main/java/com/huawei/ascend/runtime/common/AgentResponseEvent.java`
  - Minimal event-style response used inside access output without immediately deleting existing `schema.AgentResponse`.
- `agent-runtime/src/main/java/com/huawei/ascend/runtime/access/output/OutputChannel.java`
  - Access-owned user-visible output channel.
- `agent-runtime/src/main/java/com/huawei/ascend/runtime/access/output/OutputChannelRegistry.java`
  - Task-scoped output channel lookup.
- `agent-runtime/src/main/java/com/huawei/ascend/runtime/access/output/RuntimeOutputHandle.java`
  - Task-scoped output identity.
- `agent-runtime/src/main/java/com/huawei/ascend/runtime/access/output/RuntimeOutput.java`
  - Output object written by control/access egress.

### Move / Rename

- Move package `com.huawei.ascend.runtime.engine.openjiuwen` to `com.huawei.ascend.runtime.engine.adapters.openjiuwen`.
- Move files:
  - `agent-runtime/src/main/java/com/huawei/ascend/runtime/engine/openjiuwen/OpenJiuwenAgentRuntimeHandler.java`
  - `agent-runtime/src/main/java/com/huawei/ascend/runtime/engine/openjiuwen/OpenJiuwenMessageAdapter.java`
  - `agent-runtime/src/main/java/com/huawei/ascend/runtime/engine/openjiuwen/OpenJiuwenStreamAdapter.java`
- Move tests from:
  - `agent-runtime/src/test/java/com/huawei/ascend/runtime/engine/openjiuwen/*`
- To:
  - `agent-runtime/src/test/java/com/huawei/ascend/runtime/engine/adapters/openjiuwen/*`

### Modify

- `agent-runtime/src/main/java/com/huawei/ascend/runtime/engine/config/EngineAutoConfiguration.java`
  - Imports remain mostly stable; no openJiuwen import expected here.
- `agent-runtime/src/main/java/com/huawei/ascend/runtime/engine/spi/package-info.java`
  - Remove stale references if they mention old openJiuwen package.
- `agent-runtime/src/test/java/com/huawei/ascend/runtime/engine/openjiuwen/*`
  - Update package declarations/imports after move.
- `examples/agent-runtime-a2a-llm-e2e/src/test/java/**`
  - Update imports only if they reference old openJiuwen package.

### Preserve For Now

- `agent-runtime/src/main/java/com/huawei/ascend/runtime/schema/*`
  - Do not delete in this pass. Existing A2A/control/engine tests depend on these types.
- `agent-runtime/src/main/java/com/huawei/ascend/runtime/engine/command/*`
  - Keep current command gateway and Reactor `Flux`.
- `agent-runtime/src/main/java/com/huawei/ascend/runtime/engine/event/*`
  - Keep current event types until control/output consolidation has passing tests.
- `agent-runtime/src/main/java/com/huawei/ascend/runtime/access/protocol/a2a/egress/A2aOutputRegistry.java`
  - Do not delete immediately; introduce access output abstractions first.

---

## Task 1: Move openJiuwen Under `engine.adapters`

**Files:**
- Move: `agent-runtime/src/main/java/com/huawei/ascend/runtime/engine/openjiuwen/OpenJiuwenAgentRuntimeHandler.java`
- Move: `agent-runtime/src/main/java/com/huawei/ascend/runtime/engine/openjiuwen/OpenJiuwenMessageAdapter.java`
- Move: `agent-runtime/src/main/java/com/huawei/ascend/runtime/engine/openjiuwen/OpenJiuwenStreamAdapter.java`
- Move tests: `agent-runtime/src/test/java/com/huawei/ascend/runtime/engine/openjiuwen/*.java`
- Modify any imports found by `grep -R "runtime.engine.openjiuwen" agent-runtime examples/agent-runtime-a2a-llm-e2e`

- [ ] **Step 1: Write/adjust package-location test expectation**

Use the existing openJiuwen tests after the move as the test. The expected package declaration in every moved test is:

```java
package com.huawei.ascend.runtime.engine.adapters.openjiuwen;
```

- [ ] **Step 2: Move source files**

Run:

```bash
mkdir -p agent-runtime/src/main/java/com/huawei/ascend/runtime/engine/adapters/openjiuwen
mv agent-runtime/src/main/java/com/huawei/ascend/runtime/engine/openjiuwen/*.java \
  agent-runtime/src/main/java/com/huawei/ascend/runtime/engine/adapters/openjiuwen/
rmdir agent-runtime/src/main/java/com/huawei/ascend/runtime/engine/openjiuwen
```

Expected: files exist under `engine/adapters/openjiuwen` and old directory is gone.

- [ ] **Step 3: Move test files**

Run:

```bash
mkdir -p agent-runtime/src/test/java/com/huawei/ascend/runtime/engine/adapters/openjiuwen
mv agent-runtime/src/test/java/com/huawei/ascend/runtime/engine/openjiuwen/*.java \
  agent-runtime/src/test/java/com/huawei/ascend/runtime/engine/adapters/openjiuwen/
rmdir agent-runtime/src/test/java/com/huawei/ascend/runtime/engine/openjiuwen
```

Expected: tests exist under `engine/adapters/openjiuwen` and old test directory is gone.

- [ ] **Step 4: Update package declarations**

In each moved source and test file, replace:

```java
package com.huawei.ascend.runtime.engine.openjiuwen;
```

with:

```java
package com.huawei.ascend.runtime.engine.adapters.openjiuwen;
```

- [ ] **Step 5: Update imports**

Run:

```bash
grep -R "com.huawei.ascend.runtime.engine.openjiuwen" -n agent-runtime examples/agent-runtime-a2a-llm-e2e
```

For every hit, replace:

```java
import com.huawei.ascend.runtime.engine.openjiuwen.
```

with:

```java
import com.huawei.ascend.runtime.engine.adapters.openjiuwen.
```

Expected: a second grep returns no hits.

- [ ] **Step 6: Run adapter tests**

Run:

```bash
mvn -pl agent-runtime -Dtest='com.huawei.ascend.runtime.engine.adapters.openjiuwen.*Test' test
```

Expected: PASS. If Surefire cannot match the package wildcard, run the concrete moved test class names:

```bash
mvn -pl agent-runtime \
  -Dtest=OpenJiuwenAgentRuntimeHandlerTest,OpenJiuwenMessageAdapterTest,OpenJiuwenStreamAdapterTest \
  test
```

- [ ] **Step 7: Commit**

```bash
git add agent-runtime/src/main/java/com/huawei/ascend/runtime/engine/adapters/openjiuwen \
        agent-runtime/src/test/java/com/huawei/ascend/runtime/engine/adapters/openjiuwen \
        examples/agent-runtime-a2a-llm-e2e \
        agent-runtime/src/main/java \
        agent-runtime/src/test/java
git commit -m "refactor(runtime): move openJiuwen engine adapter namespace"
```

---

## Task 2: Add Minimal Common Event Types Without Breaking Existing Schema

**Files:**
- Create: `agent-runtime/src/main/java/com/huawei/ascend/runtime/common/ResponseMode.java`
- Create: `agent-runtime/src/main/java/com/huawei/ascend/runtime/common/ResponseType.java`
- Create: `agent-runtime/src/main/java/com/huawei/ascend/runtime/common/ResponseStatus.java`
- Create: `agent-runtime/src/main/java/com/huawei/ascend/runtime/common/ErrorInfo.java`
- Create: `agent-runtime/src/main/java/com/huawei/ascend/runtime/common/AgentResponseEvent.java`
- Test: `agent-runtime/src/test/java/com/huawei/ascend/runtime/common/AgentResponseEventTest.java`

- [ ] **Step 1: Write failing common event test**

Create `agent-runtime/src/test/java/com/huawei/ascend/runtime/common/AgentResponseEventTest.java`:

```java
package com.huawei.ascend.runtime.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import org.junit.jupiter.api.Test;

class AgentResponseEventTest {

    @Test
    void finalTextBuildsTerminalCompletedEvent() {
        AgentResponseEvent event = AgentResponseEvent.finalText(
                "request-1", "tenant-1", "user-1", "agent-1", "session-1", "task-1", "pong", Map.of("k", "v"));

        assertThat(event.requestId()).isEqualTo("request-1");
        assertThat(event.sequence()).isEqualTo(1L);
        assertThat(event.responseType()).isEqualTo(ResponseType.FINAL);
        assertThat(event.status()).isEqualTo(ResponseStatus.COMPLETED);
        assertThat(event.output()).isEqualTo("pong");
        assertThat(event.error()).isNull();
        assertThat(event.metadata()).containsEntry("k", "v");
        assertThat(event.terminal()).isTrue();
    }

    @Test
    void errorBuildsTerminalFailedEvent() {
        AgentResponseEvent event = AgentResponseEvent.error(
                "request-2", "tenant-1", "user-1", "agent-1", "session-1", "task-2",
                new ErrorInfo("RUNTIME_ERROR", "handler failed"));

        assertThat(event.responseType()).isEqualTo(ResponseType.ERROR);
        assertThat(event.status()).isEqualTo(ResponseStatus.FAILED);
        assertThat(event.error().code()).isEqualTo("RUNTIME_ERROR");
        assertThat(event.terminal()).isTrue();
    }

    @Test
    void requiresCoreIdentity() {
        assertThatThrownBy(() -> AgentResponseEvent.finalText(
                "request-1", " ", "user-1", "agent-1", "session-1", "task-1", "pong", Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("tenantId");
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run:

```bash
mvn -pl agent-runtime -Dtest=AgentResponseEventTest test
```

Expected: FAIL because `AgentResponseEvent`, `ErrorInfo`, `ResponseType`, and `ResponseStatus` do not exist.

- [ ] **Step 3: Add enums**

Create `agent-runtime/src/main/java/com/huawei/ascend/runtime/common/ResponseMode.java`:

```java
package com.huawei.ascend.runtime.common;

public enum ResponseMode {
    BLOCKING,
    STREAMING
}
```

Create `agent-runtime/src/main/java/com/huawei/ascend/runtime/common/ResponseType.java`:

```java
package com.huawei.ascend.runtime.common;

public enum ResponseType {
    TASK,
    DELTA,
    FINAL,
    ERROR
}
```

Create `agent-runtime/src/main/java/com/huawei/ascend/runtime/common/ResponseStatus.java`:

```java
package com.huawei.ascend.runtime.common;

public enum ResponseStatus {
    ACCEPTED,
    RUNNING,
    INPUT_REQUIRED,
    COMPLETED,
    FAILED,
    CANCELLED
}
```

- [ ] **Step 4: Add error payload**

Create `agent-runtime/src/main/java/com/huawei/ascend/runtime/common/ErrorInfo.java`:

```java
package com.huawei.ascend.runtime.common;

public record ErrorInfo(String code, String message) {

    public ErrorInfo {
        code = normalize(code, "UNKNOWN");
        message = message == null ? "" : message;
    }

    private static String normalize(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
```

- [ ] **Step 5: Add minimal event response**

Create `agent-runtime/src/main/java/com/huawei/ascend/runtime/common/AgentResponseEvent.java`:

```java
package com.huawei.ascend.runtime.common;

import java.util.Map;
import java.util.Objects;

public record AgentResponseEvent(
        String requestId,
        long sequence,
        ResponseType responseType,
        String tenantId,
        String userId,
        String agentId,
        String sessionId,
        String taskId,
        ResponseStatus status,
        String output,
        ErrorInfo error,
        Map<String, Object> metadata) {

    public AgentResponseEvent {
        requestId = requireNonBlank(requestId, "requestId");
        if (sequence < 1L) {
            throw new IllegalArgumentException("sequence must be positive");
        }
        responseType = Objects.requireNonNull(responseType, "responseType");
        tenantId = requireNonBlank(tenantId, "tenantId");
        agentId = requireNonBlank(agentId, "agentId");
        sessionId = requireNonBlank(sessionId, "sessionId");
        taskId = requireNonBlank(taskId, "taskId");
        status = Objects.requireNonNull(status, "status");
        output = output == null ? "" : output;
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }

    public static AgentResponseEvent taskAccepted(
            String requestId, String tenantId, String userId, String agentId, String sessionId, String taskId) {
        return new AgentResponseEvent(requestId, 1L, ResponseType.TASK, tenantId, userId, agentId,
                sessionId, taskId, ResponseStatus.ACCEPTED, "", null, Map.of());
    }

    public static AgentResponseEvent deltaText(
            String requestId, long sequence, String tenantId, String userId, String agentId,
            String sessionId, String taskId, String output) {
        return new AgentResponseEvent(requestId, sequence, ResponseType.DELTA, tenantId, userId, agentId,
                sessionId, taskId, ResponseStatus.RUNNING, output, null, Map.of());
    }

    public static AgentResponseEvent finalText(
            String requestId, String tenantId, String userId, String agentId, String sessionId,
            String taskId, String output, Map<String, Object> metadata) {
        return new AgentResponseEvent(requestId, 1L, ResponseType.FINAL, tenantId, userId, agentId,
                sessionId, taskId, ResponseStatus.COMPLETED, output, null, metadata);
    }

    public static AgentResponseEvent error(
            String requestId, String tenantId, String userId, String agentId, String sessionId,
            String taskId, ErrorInfo error) {
        return new AgentResponseEvent(requestId, 1L, ResponseType.ERROR, tenantId, userId, agentId,
                sessionId, taskId, ResponseStatus.FAILED, "", Objects.requireNonNull(error, "error"), Map.of());
    }

    public boolean terminal() {
        return responseType == ResponseType.FINAL || responseType == ResponseType.ERROR
                || status == ResponseStatus.CANCELLED || status == ResponseStatus.INPUT_REQUIRED;
    }

    private static String requireNonBlank(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}
```

- [ ] **Step 6: Run test to verify it passes**

Run:

```bash
mvn -pl agent-runtime -Dtest=AgentResponseEventTest test
```

Expected: PASS.

- [ ] **Step 7: Run existing schema tests if present**

Run:

```bash
mvn -pl agent-runtime -Dtest='*AgentResponse*Test,*AgentRequest*Test' test
```

Expected: PASS or no matching tests. If no matching tests are reported, continue.

- [ ] **Step 8: Commit**

```bash
git add agent-runtime/src/main/java/com/huawei/ascend/runtime/common \
        agent-runtime/src/test/java/com/huawei/ascend/runtime/common
git commit -m "feat(runtime): add common response event model"
```

---

## Task 3: Introduce Access Output Channel Abstraction Backed by Reactor Queue Semantics

**Files:**
- Create: `agent-runtime/src/main/java/com/huawei/ascend/runtime/access/output/RuntimeOutputHandle.java`
- Create: `agent-runtime/src/main/java/com/huawei/ascend/runtime/access/output/RuntimeOutput.java`
- Create: `agent-runtime/src/main/java/com/huawei/ascend/runtime/access/output/OutputChannel.java`
- Create: `agent-runtime/src/main/java/com/huawei/ascend/runtime/access/output/OutputChannelRegistry.java`
- Test: `agent-runtime/src/test/java/com/huawei/ascend/runtime/access/output/OutputChannelRegistryTest.java`

- [ ] **Step 1: Write failing output channel test**

Create `agent-runtime/src/test/java/com/huawei/ascend/runtime/access/output/OutputChannelRegistryTest.java`:

```java
package com.huawei.ascend.runtime.access.output;

import static org.assertj.core.api.Assertions.assertThat;

import com.huawei.ascend.runtime.common.AgentResponseEvent;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class OutputChannelRegistryTest {

    @Test
    void replaysBufferedOutputsAndCompletesAfterTerminal() {
        OutputChannelRegistry registry = new OutputChannelRegistry();
        RuntimeOutputHandle handle = new RuntimeOutputHandle("tenant-1", "session-1", "task-1");
        OutputChannel channel = registry.getOrCreate(handle);

        channel.write(RuntimeOutput.from(AgentResponseEvent.taskAccepted(
                "request-1", "tenant-1", "user-1", "agent-1", "session-1", "task-1")));
        channel.write(RuntimeOutput.from(AgentResponseEvent.finalText(
                "request-1", "tenant-1", "user-1", "agent-1", "session-1", "task-1", "pong", Map.of())));

        List<RuntimeOutput> replayed = registry.getOrCreate(handle)
                .stream()
                .collectList()
                .block(Duration.ofSeconds(2));

        assertThat(replayed).hasSize(2);
        assertThat(replayed.get(0).event().status().name()).isEqualTo("ACCEPTED");
        assertThat(replayed.get(1).event().output()).isEqualTo("pong");
        assertThat(replayed.get(1).terminal()).isTrue();
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run:

```bash
mvn -pl agent-runtime -Dtest=OutputChannelRegistryTest test
```

Expected: FAIL because output channel classes do not exist.

- [ ] **Step 3: Create output handle**

Create `agent-runtime/src/main/java/com/huawei/ascend/runtime/access/output/RuntimeOutputHandle.java`:

```java
package com.huawei.ascend.runtime.access.output;

import java.util.Objects;

public record RuntimeOutputHandle(String tenantId, String sessionId, String taskId) {

    public RuntimeOutputHandle {
        tenantId = requireNonBlank(tenantId, "tenantId");
        sessionId = requireNonBlank(sessionId, "sessionId");
        taskId = requireNonBlank(taskId, "taskId");
    }

    private static String requireNonBlank(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}
```

- [ ] **Step 4: Create runtime output wrapper**

Create `agent-runtime/src/main/java/com/huawei/ascend/runtime/access/output/RuntimeOutput.java`:

```java
package com.huawei.ascend.runtime.access.output;

import com.huawei.ascend.runtime.common.AgentResponseEvent;
import java.util.Objects;

public record RuntimeOutput(AgentResponseEvent event) {

    public RuntimeOutput {
        event = Objects.requireNonNull(event, "event");
    }

    public static RuntimeOutput from(AgentResponseEvent event) {
        return new RuntimeOutput(event);
    }

    public boolean terminal() {
        return event.terminal();
    }
}
```

- [ ] **Step 5: Create output channel**

Create `agent-runtime/src/main/java/com/huawei/ascend/runtime/access/output/OutputChannel.java`:

```java
package com.huawei.ascend.runtime.access.output;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

public final class OutputChannel {

    private final List<RuntimeOutput> history = new CopyOnWriteArrayList<>();
    private final Sinks.Many<RuntimeOutput> sink = Sinks.many().replay().all();

    public void write(RuntimeOutput output) {
        history.add(output);
        sink.tryEmitNext(output).orThrow();
        if (output.terminal()) {
            sink.tryEmitComplete().orThrow();
        }
    }

    public Flux<RuntimeOutput> stream() {
        return sink.asFlux();
    }

    public List<RuntimeOutput> history() {
        return List.copyOf(history);
    }
}
```

- [ ] **Step 6: Create registry**

Create `agent-runtime/src/main/java/com/huawei/ascend/runtime/access/output/OutputChannelRegistry.java`:

```java
package com.huawei.ascend.runtime.access.output;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class OutputChannelRegistry {

    private final ConcurrentMap<RuntimeOutputHandle, OutputChannel> channels = new ConcurrentHashMap<>();

    public OutputChannel getOrCreate(RuntimeOutputHandle handle) {
        Objects.requireNonNull(handle, "handle");
        return channels.computeIfAbsent(handle, ignored -> new OutputChannel());
    }

    public List<RuntimeOutput> list(RuntimeOutputHandle handle) {
        Objects.requireNonNull(handle, "handle");
        OutputChannel channel = channels.get(handle);
        return channel == null ? List.of() : channel.history();
    }
}
```

- [ ] **Step 7: Run test to verify it passes**

Run:

```bash
mvn -pl agent-runtime -Dtest=OutputChannelRegistryTest test
```

Expected: PASS.

- [ ] **Step 8: Commit**

```bash
git add agent-runtime/src/main/java/com/huawei/ascend/runtime/access/output \
        agent-runtime/src/test/java/com/huawei/ascend/runtime/access/output
git commit -m "feat(runtime): add access output channel abstraction"
```

---

## Task 4: Add Adapter From Existing A2A Output Registry to OutputChannel Model

**Files:**
- Modify: `agent-runtime/src/main/java/com/huawei/ascend/runtime/access/protocol/a2a/egress/DefaultNotificationPort.java`
- Modify: `agent-runtime/src/main/java/com/huawei/ascend/runtime/access/protocol/a2a/egress/A2aOutputRegistry.java`
- Test: `agent-runtime/src/test/java/com/huawei/ascend/runtime/access/protocol/a2a/egress/A2aOutputRegistryTest.java`

- [ ] **Step 1: Extend existing egress test with compatibility assertion**

In `A2aOutputRegistryTest`, add a test that verifies existing A2A output behavior still replays terminal output. Use existing constructors from the file. If the file already has this assertion, keep it and skip adding a duplicate.

Expected assertion shape:

```java
assertThat(registry.list(handle)).hasSize(2);
assertThat(registry.list(handle).get(1).terminal()).isTrue();
```

- [ ] **Step 2: Run existing egress tests before modification**

Run:

```bash
mvn -pl agent-runtime -Dtest=A2aOutputRegistryTest test
```

Expected: PASS before modifying production code.

- [ ] **Step 3: Keep A2A registry as compatibility boundary**

Do not replace `A2aOutputRegistry` in this task. Keep its public API:

```java
public void append(A2aOutputHandle handle, A2aOutput output)
public List<A2aOutput> list(A2aOutputHandle handle)
public Runnable subscribe(A2aOutputHandle handle, Consumer<A2aOutput> subscriber)
```

Rationale: this preserves A2A tests while output channel model is introduced.

- [ ] **Step 4: Run A2A JSON-RPC handler tests**

Run:

```bash
mvn -pl agent-runtime -Dtest=A2aJsonRpcHandlerTest,A2aJsonRpcControllerTest,A2aOutputRegistryTest test
```

Expected: PASS.

- [ ] **Step 5: Commit if any compatibility test was added**

```bash
git add agent-runtime/src/test/java/com/huawei/ascend/runtime/access/protocol/a2a/egress/A2aOutputRegistryTest.java
git commit -m "test(runtime): pin A2A output replay compatibility"
```

If no file changed, do not commit.

---

## Task 5: Remove Stale Design Metadata From Engine API Comments

**Files:**
- Modify: `agent-runtime/src/main/java/com/huawei/ascend/runtime/engine/api/EngineExecutionApi.java`
- Modify: `agent-runtime/src/main/java/com/huawei/ascend/runtime/engine/api/DefaultEngineExecutionApi.java`

- [ ] **Step 1: Inspect comments for version/log metadata**

Run:

```bash
grep -R "2026-\|design §\|ADR-\|Phase [0-9]\|agent-service" -n \
  agent-runtime/src/main/java/com/huawei/ascend/runtime/engine/api \
  agent-runtime/src/main/java/com/huawei/ascend/runtime/engine/EngineDispatcher.java \
  agent-runtime/src/main/java/com/huawei/ascend/runtime/engine/command
```

Expected: current hits include `EngineExecutionApi` and may include `DefaultEngineExecutionApi` or `EngineDispatcher` comments.

- [ ] **Step 2: Replace `EngineExecutionApi` Javadoc**

Replace the current class-level Javadoc in `EngineExecutionApi.java` with:

```java
/**
 * Engine dispatch API.
 *
 * <p>The control layer calls this API to enqueue execution, resume, and cancel
 * commands. The engine accepts or rejects enqueue only; execution progress is
 * reported later through the control callback port.
 */
```

- [ ] **Step 3: Replace `DefaultEngineExecutionApi` Javadoc**

Replace the current class-level Javadoc in `DefaultEngineExecutionApi.java` with:

```java
/**
 * Default engine dispatch API that publishes execution commands onto the engine
 * command gateway.
 */
```

- [ ] **Step 4: Run engine API tests**

Run:

```bash
mvn -pl agent-runtime -Dtest=DefaultEngineExecutionApiTest test
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add agent-runtime/src/main/java/com/huawei/ascend/runtime/engine/api/EngineExecutionApi.java \
        agent-runtime/src/main/java/com/huawei/ascend/runtime/engine/api/DefaultEngineExecutionApi.java
git commit -m "docs(runtime): clarify engine API comments"
```

---

## Task 6: Pin Control as Sole State Authority Before Deeper Refactor

**Files:**
- Test: `agent-runtime/src/test/java/com/huawei/ascend/runtime/control/test/TaskflowEngineBridgeWhiteboxTest.java`
- Test: `agent-runtime/src/test/java/com/huawei/ascend/runtime/engine/EngineDispatcherTest.java`
- Modify only if tests reveal drift: `agent-runtime/src/main/java/com/huawei/ascend/runtime/engine/EngineDispatcher.java`

- [ ] **Step 1: Verify current tests express engine reports to control only**

Run:

```bash
mvn -pl agent-runtime -Dtest=TaskflowEngineBridgeWhiteboxTest,EngineDispatcherTest test
```

Expected: PASS. These tests are the guardrail for the chosen architecture.

- [ ] **Step 2: If `EngineDispatcher` directly writes access output, remove it**

Search:

```bash
grep -R "AccessLayerClient\|NotificationPort\|EngineOutputSink\|A2aOutputRegistry" -n \
  agent-runtime/src/main/java/com/huawei/ascend/runtime/engine
```

Expected: no production engine path writes A2A/access output directly. If a hit is only an unused import or support class, remove the unused reference. If a hit is an active write, stop and ask the user before changing the routing model.

- [ ] **Step 3: Run control and engine bridge tests again**

Run:

```bash
mvn -pl agent-runtime -Dtest=TaskflowEngineBridgeWhiteboxTest,TaskControlServiceWhiteboxTest,EngineDispatcherTest test
```

Expected: PASS.

- [ ] **Step 4: Commit only if code changed**

```bash
git add agent-runtime/src/main/java/com/huawei/ascend/runtime/engine \
        agent-runtime/src/test/java/com/huawei/ascend/runtime/control/test \
        agent-runtime/src/test/java/com/huawei/ascend/runtime/engine
git commit -m "refactor(runtime): preserve control-owned state routing"
```

If no file changed, do not commit.

---

## Task 7: Add Minimal Architecture Boundary Tests for Adapter Namespace and Queue Reactor Acceptance

**Files:**
- Create: `agent-runtime/src/test/java/com/huawei/ascend/runtime/architecture/RuntimePackageBoundaryTest.java`

- [ ] **Step 1: Write failing boundary test**

Create `agent-runtime/src/test/java/com/huawei/ascend/runtime/architecture/RuntimePackageBoundaryTest.java`:

```java
package com.huawei.ascend.runtime.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.junit.jupiter.api.Test;

class RuntimePackageBoundaryTest {

    private final JavaClasses classes = new ClassFileImporter()
            .importPackages("com.huawei.ascend.runtime");

    @Test
    void openJiuwenAdapterLivesUnderEngineAdapters() {
        assertThat(classes.stream()
                .filter(javaClass -> javaClass.getPackageName().contains("openjiuwen"))
                .allMatch(javaClass -> javaClass.getPackageName()
                        .startsWith("com.huawei.ascend.runtime.engine.adapters.openjiuwen")))
                .isTrue();
    }

    @Test
    void accessDoesNotDependOnOpenJiuwenAdapter() {
        noClasses()
                .that().resideInAPackage("..runtime.access..")
                .should().dependOnClassesThat().resideInAPackage("..runtime.engine.adapters.openjiuwen..")
                .check(classes);
    }

    @Test
    void controlDoesNotDependOnOpenJiuwenAdapter() {
        noClasses()
                .that().resideInAPackage("..runtime.control..")
                .should().dependOnClassesThat().resideInAPackage("..runtime.engine.adapters.openjiuwen..")
                .check(classes);
    }
}
```

- [ ] **Step 2: Run boundary test**

Run:

```bash
mvn -pl agent-runtime -Dtest=RuntimePackageBoundaryTest test
```

Expected: PASS after Task 1. If it fails because old openJiuwen package remains, finish Task 1 cleanup.

- [ ] **Step 3: Commit**

```bash
git add agent-runtime/src/test/java/com/huawei/ascend/runtime/architecture/RuntimePackageBoundaryTest.java
git commit -m "test(runtime): pin engine adapter package boundary"
```

---

## Task 8: Run Minimal Regression Suite

**Files:**
- No production files.

- [ ] **Step 1: Run runtime core tests touched by this pass**

Run:

```bash
mvn -pl agent-runtime \
  -Dtest=AgentResponseEventTest,OutputChannelRegistryTest,RuntimePackageBoundaryTest,DefaultEngineExecutionApiTest,EngineDispatcherTest,TaskControlServiceWhiteboxTest,TaskflowEngineBridgeWhiteboxTest,A2aJsonRpcHandlerTest,A2aJsonRpcControllerTest,A2aOutputRegistryTest \
  test
```

Expected: PASS.

- [ ] **Step 2: Run openJiuwen adapter tests**

Run:

```bash
mvn -pl agent-runtime \
  -Dtest=OpenJiuwenAgentRuntimeHandlerTest,OpenJiuwenMessageAdapterTest,OpenJiuwenStreamAdapterTest \
  test
```

Expected: PASS.

- [ ] **Step 3: Run example smoke tests that use RuntimeApp/A2A**

Run:

```bash
mvn -pl examples/agent-runtime-a2a-llm-e2e \
  -Dtest=RuntimeAppBootTest,SampleA2aClientTest,A2aClientPerspectiveTest \
  test
```

Expected: PASS.

- [ ] **Step 4: Record any failures before fixing**

If a test fails, copy the failing test FQN, raw failure message, and first stack frame into the work log before changing code. Then fix only the failed behavior.

---

## Task 9: Decide Next Minimal Slice After Green Tests

This task does not change code. It gates the next refactor stage.

- [ ] **Step 1: Inspect current remaining package spread**

Run:

```bash
find agent-runtime/src/main/java/com/huawei/ascend/runtime -maxdepth 3 -type d | sort
```

Expected: `engine/adapters/openjiuwen` exists, `engine/openjiuwen` does not exist.

- [ ] **Step 2: Check whether `schema` can be migrated to `common`**

Run:

```bash
grep -R "com.huawei.ascend.runtime.schema" -n agent-runtime/src/main/java agent-runtime/src/test/java examples/agent-runtime-a2a-llm-e2e/src/test/java | wc -l
```

Decision rule:

- If the count is under 30, migrate `schema` to `common` in the next slice.
- If the count is 30 or higher, first add adapters/mappers and avoid a broad package rename.

- [ ] **Step 3: Check whether `A2aOutputRegistry` can be replaced by `OutputChannelRegistry`**

Run:

```bash
grep -R "A2aOutputRegistry" -n agent-runtime/src/main/java agent-runtime/src/test/java examples/agent-runtime-a2a-llm-e2e/src/test/java
```

Decision rule:

- If only A2A ingress/egress and tests use it, replace it in the next slice.
- If engine/control code uses it directly, stop and ask the user because that would violate the control-owned output direction.

- [ ] **Step 4: Report next-slice recommendation**

Report one of these exact recommendations:

```text
Recommendation: migrate schema -> common next, because import count is small.
```

or:

```text
Recommendation: replace A2aOutputRegistry with OutputChannelRegistry next, because output coupling is localized.
```

or:

```text
Recommendation: stop for design decision, because engine/control directly depends on A2aOutputRegistry.
```

---

## Self-Review

### Spec Coverage

- Reactor queue acceptance is preserved by keeping `Flux` in queue/command APIs.
- openJiuwen adapter namespace moves to `engine.adapters.openjiuwen`.
- Minimal-first behavior is preserved by adding common/output abstractions without deleting working schema/A2A code immediately.
- Control remains the state authority; no task introduces engine -> access direct writes.

### Placeholder Scan

This plan intentionally avoids unresolved implementation placeholders. Any future unknown is converted into an explicit stop/decision rule in Task 9.

### Type Consistency

- `AgentResponseEvent` uses `ResponseType`, `ResponseStatus`, `ErrorInfo` consistently.
- `RuntimeOutput` wraps `AgentResponseEvent`.
- `OutputChannelRegistry` uses `RuntimeOutputHandle` and returns `OutputChannel`.
- openJiuwen package target is consistently `com.huawei.ascend.runtime.engine.adapters.openjiuwen`.
