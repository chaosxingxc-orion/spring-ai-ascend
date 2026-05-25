---
level: L1
view: [scenarios, logical, process, development, physical]
module: agent-service
affects_level: L0, L1
affects_view: [scenarios, logical, process, development, physical]
status: proposed
---

# Architecture Review Proposal: agent-service L1 Domain Expansion (Wave 1.2)

> **Date:** 2026-05-21
> **Authors:** LucioIT (Core Architect) & JiJi (Agent)
> **Target Wave:** W0/W1 (Immediate Execution)
> **Associated Rules:** Rule G-1.c (L1 Depth and Grounding), Rule R-G (Reactive I/O), Rule R-M (Engine Decoupling)

## 1. Background & Principles

### 1.1 Top-Level Architecture Baseline (L0 Architecture Topology)
As a pivotal pillar of the overall Agent Ecosystem, the `agent-service` module is deeply embedded in the **L0 Top-Level Architecture Design**. The L0 architecture is systematically composed of **six core modules** and **two major deployment/integration patterns**:

#### 1.1.1 Six Core Modules
1. **Agent Client (`agent-client`)**: Integrated within SaaS applications and desktop clients. It is responsible for perceiving business context, maintaining state, operating business environments/tools, dispatching configuration baselines, and invoking the Agent Service.
2. **Agent Service (`agent-service`)**: **(The target boundary of this proposal)** Encapulates Graph-mode Workflow Agents and Loop-mode ReAct Agents as standard microservices.
3. **Agent Execution Engine (`agent-execution-engine`)**: Provides executors for both Workflow and ReAct agents, supplying components like nodes for Workflows, and tools/hooks for ReAct execution.
4. **Agent Bus (`agent-bus`)**: Connects North-South traffic (Client-to-Server) and East-West traffic (Agent-to-Agent).
5. **Agent Middleware (`agent-middleware`)**: Delivers infrastructure capabilities required by agents, such as Vector memory storage, Skill registry, Knowledge bases, and execution sandboxes.
6. **Agent Evolve Platform (`agent-evolve`)**: Orchestrates both online and offline autonomous evolution/fine-tuning of Agents.

#### 1.1.2 Two Deployment & Integration Modes
- **Platform-Centric Mode**: The business side integrates only the lightweight `agent-client`. All other execution, orchestrator, and middleware modules are centrally hosted and run on the platform side to minimize business integration friction.
- **Business-Centric Mode**: The business side integrates `agent-client` and locally deploys `agent-service` and `agent-execution-engine` within its physical boundaries for ultra-low latency. The platform side acts purely as a governance plane providing public, cross-boundary services.

### 1.2 Project Phase Strategy & Evolutionary Roadmap
To balance rapid delivery in the incubator stage with long-term architectural health, the project establishes distinct execution milestones:

- **Focus on Ecosystem & UX, Defer Scale Pressure**: Currently in the early stage. Under the premise of maintaining architectural boundaries, development priority centers on **building a healthy agent ecosystem and a frictionless developer experience**, rather than pre-maturely optimizing for hyper-scale throughput.
- **Phased Module Construction**:
  - *In-house Core Proprietary Development (Client/Service/Engine)*: Focus immediate engineering bandwidth on fully delivering `agent-client`, `agent-service`, and `agent-execution-engine`.
  - *Leverage Open-Source Ecosystem (Bus/Middleware)*: For `agent-bus` and `agent-middleware`, the project primarily integrates proven open-source solutions (such as NATS, RabbitMQ, Redis, and standard vector databases) with lightweight adapters to maximize velocity.
- **Deployment Mode Roadmap**:
  - *Current Phase*: Prioritize and fully implement the **Platform-Centric Mode** to establish an end-to-end user loop.
  - *Subsequent Phase*: Roll out complete support for the **Business-Centric Mode**. While not delivered in the immediate wave, the L1 architecture and SPI interfaces must pre-emptively incorporate boundaries and polymorphic invocation semantics to ensure zero-code business migration.

### 1.3 Design Pillars and Core Architectural Constraints
The `agent-service` module must rigorously adhere to the following principles to support agent form factors and architectural evolutions:

#### 1.3.1 Dual Agent Form Factors
1. **Workflow Agent**: Encapsulates Graph-mode execution, handling deterministic, directed acyclic graphs (DAGs) or complex topologies with conditional branching.
2. **ReAct Agent**: Encapsulates Loop-mode execution, managing non-deterministic reasoning-action loops that dynamically choose and execute tools and hooks.

#### 1.3.2 Dual Integration and Invocation Models (Dual-Mode Runtime)
1. **Embedded Co-process**: `agent-service` and `agent-execution-engine` are co-deployed within the same process (e.g., same JVM), interacting via direct, in-memory method/function calls to achieve sub-millisecond overhead as a deferred benchmark target.
2. **Stateless Service-level**: Runs the Agent as a fully stateless microservice. `agent-service` acts as the control plane, dispatching execution directives to independent engine instances via RPC, gRPC, or the Agent Bus.

#### 1.3.3 Heterogeneous Compatibility & Legacy System Decoupling
- Seamlessly wrap existing, running heterogeneous/legacy agents within the client ecosystem. Using the service abstraction and specific adapters, these legacy agents are exposed as uniform standard services, ensuring unified platform governance.

#### 1.3.4 Backpressure-driven Reactive & Stateless Execution
- **Reactive API design**: Service boundaries utilize reactive programming models. Backpressure acts as a coordinator, shielding the execution engines from overload while dynamically adjusting upstream consumer flow rates.
- **Inbound Adaptability (Pull & Push)**: Dynamically adapts to both event-driven pulling (from the Agent Bus) and synchronous push requests (HTTP/gRPC), unified under a single reactive streaming framework.
- **Asynchronous Decoupling via Queues**: An internal high-throughput event/task queue decouples network I/O from heavy CPU/LLM processing threads. **(Updated 2026-05-26 per ADR-0138 §3 red-line c, post-rc53-wave-1)**: This "internal queue" is the in-process binding layer over the canonical **three physically-isolated bus channels** declared in `docs/governance/bus-channels.yaml` per Rule R-E — `control` (high-priority, out-of-band), `data` (in-band, heavy-load, 16 KiB inline cap), `rhythm` (heartbeat / liveness). Producer / Consumer route by event intent, not by storage tier; durability is a per-channel property declared in `bus-channels.yaml`, NOT a queue-level mode. See `docs/logs/reviews/2026-05-26-agent-service-l1-4plus1-rewrite-wave-1.en.md` §15.1 / §17.3 for the canonical mapping.
- **Stateless and Semi-persistence Policies** (**Updated 2026-05-26 per ADR-0139 narrowed Fast-Path semantics, post-rc53-wave-1**: any "in-memory queues for compact edge deployment" Fast-Path interpretation MUST preserve the Rule R-G reactive I/O + Rule R-H no-Thread.sleep + Rule R-J.a RLS-on-tenant_id-tables invariants; Fast-Path narrows the **checkpoint/snapshot** axis only, NOT the metadata-persistence axis. The Run + Task records remain RLS-persisted on every Fast-Path execution; "no mandatory persistence" wording is a defect — see `docs/logs/reviews/2026-05-26-agent-service-l1-4plus1-rewrite-wave-1.en.md` §4.4 + §16.2):
  - *Under Business-Centric Mode*: Internal task queues utilize high-performance, in-memory queues (e.g., JVM Reactor Sinks) for compact edge deployment.
  - *Under Platform-Centric Mode*: To enable extreme statelessness and horizontal scaling, internal queues integrate with distributed caches or **semi-persistent store backends** (e.g., Redis Lists, JPA) to safeguard task state during node failover.

#### 1.3.5 Agent-to-Agent (A2A) Peer-to-Peer Networking
- **Symmetric Symmetric Dual Roles**: The `agent-service` acts as both an **A2A Server** (accepting collaborations) and an **A2A Client** (dispatching sub-tasks).
- **Omni-scenario Collaboration Topology**:
  - *Spatial Dimension*: Supports intra-node in-memory method calls and cross-node distributed network routing.
  - *Lifecycle Dimension*: Manages both short-lived, dynamically spawned sub-agents (Dynamic Sub-agents) and long-lived independent agents (Long-lived Agents).
- **Google `a2a-java` Protocol Integration**: Harnesses Google's official **`a2a-java`** SDK to handle handshakes, connection tunnels, routing matrix, and session correlation, enforcing protocol standardization.

#### 1.3.6 Task-Centric State Machine and Explicit Interrupt Primitives
- **Task-Centric Lifecycle**: Moves away from traditional session-locked, synchronous execution. Heavy state machines are modeled around the **A2A standard task lifecycle** (Submitted -> Working -> Suspended -> Completed).
- **Explicit Interrupt Signals**: Eliminates implicit exceptions for flow control. The engine yields a strong-typed **InterruptSignal** whenever it encounters asynchronous waits, triggering service-level state dehydration and saving execution threads.

#### 1.3.7 Dual-Track Fast/Slow-Path Routing
- **Minimalist Execution Kernel**: The execution engine (`agent-execution-engine`) is treated as a pure, stateless computation chip. It never performs direct physical I/O. When it requires external resources, it outputs an `InterruptSignal` and yields control.
- **Polymorphic Scheduling (Fast/Slow-Path)**: The `agent-service` intercepts all `InterruptSignal` outputs and routes them based on latency/durability profiles:
  - *Fast-Path (Memory Bypass)*: For near-instantaneous, low-overhead operations (e.g., local memory cache reads, simple data format transformations, or in-memory variables), the service resolves the request synchronously or asynchronously in-memory, bypassing state dehydration to maximize throughput.
  - *Slow-Path (Durable Orchestration)*: For high-latency, unstable, or human-in-the-loop operations (e.g., long-running inference, external API calls, sandboxed code execution, or human manual approvals), the service triggers the 3-level persistence SPI (Memory -> Postgres -> Temporal). The engine state is fully dehydrated and serialized, and the physical execution thread is released. Temporal manages the distributed workflow orchestration, resuming on any available node when ready.

#### 1.3.8 Heterogeneous Framework Anti-Corruption & Shadow Tool Interceptor
- **Decoupled Heterogeneous Integration**: When hosting legacy agents built on third-party frameworks (e.g., LangChain, LlamaIndex, Spring AI), the platform prohibits direct invocation of underlying database or microservice middleware by the foreign engine to protect platform security.
- **Shadow Tool Interception**:
  - **Bidirectional Context Translation**: An `EngineAdapter` translates our canonical `InjectedContext` into the third-party native format on entry, and serializes state changes back to standard `StateDelta` on exit.
  - **Shadow Plugin Virtualization**: Instead of registering real business tool logic inside the third-party framework, the adapter registers empty "Shadow Tools" (or Proxy Plugins).
  - When the third-party engine decides to invoke a tool, the invocation is caught by the shadow tool, which immediately packages the parameters into a strong-typed `TOOL_EXECUTION` `InterruptSignal` and terminates the native framework execution stack.
  - **Unified Control Plane**: The physical tool execution is managed purely by `agent-service` in a safe, sandboxed environment. This keeps the execution engine completely agnostic of tool runtimes.

---

## 1.4 Execution Granularity and 4-Layer State Model
To establish clear data boundaries, the architecture adopts a four-tier lifecycle model:

1. **Layer 1: Run (Single-Step Computation)**: The most granular execution unit. Matches a single Node transition in Workflows, a single turn iteration in ReAct loops, or execution between interceptor hooks. Transient and in-memory.
2. **Layer 2: Task (Orchestrable Unit)**: A business execution unit derived from a client request or A2A delegation, mapped to standard status lines: Submitted -> Working -> Suspended -> Completed.
3. **Layer 3: Session (Contextual Interaction)**: Manages multi-turn conversations or collaborative state histories. While "Task is Session" in trivial pipelines, complex scenarios support multiple concurrent Tasks under a single Session.
4. **Layer 4: Memory (Cognitive Knowledge Asset)**: Cross-session cognitive assets that represent the long-term personality, memory, and evolutionary optimization of the Agent.

---

## 1.5 Service-Engine Division of Responsibility (Task-Centric Contract)
The system enforces a clean separation of concerns at the Task level:

- **Core Divison Rule**: **Task state and orchestration belong to Service. Run execution belongs to Engine.**
- **Engine: Pure computation, strictly decoupled from physical I/O**:
  - Drives internal Run transitions within a Task.
  - **Strict Constraint**: Engine contains zero physical I/O operations. All inputs must be injected as `InjectedContext`. Upon encountering barriers, it yields state changes and `InterruptSignal` directly to the Service.
- **Service: Task State Machine, Session Projection, Memory Consolidation**:
  - Maintaing the Task state machines and handling queue backpressure.
  - Dynamically projects history using the `ContextProjector` to construct `InjectedContext` for the Engine.
  - Persists context changes and transforms transaction outputs into long-term memories.

---

## 1.6 Message-Centric Data Plane vs Task-Centric Control Plane Separation
To maintain natural language capabilities while securing deterministic microservice control flows:

- **Message (Data Plane)**: Natural language conversational contents representing user intent and interaction history.
- **Task (Control Plane)**: Strongly-typed structured execution plans with deterministic lifecycles.
- **Translation Boundary (The Natural Language Compiler Metaphor)**:
  - *Inbound*: Service compiles natural language Messages into structured Tasks with projected context before pushing to Engine.
  - *Outbound*: Message content is wrapped in `StateDelta.newMessages`. Control plane interactions (e.g., A2A sub-task spawn) must use explicit structured types (e.g., `SubTaskAwaitSignal`) instead of parsing text.

---

## 2. Scenarios View

### 2.1 High-Performance Embedded Execution (Co-process Mode)
- **Flow**: Client Invocation -> Local Service reads Task -> Invokes co-process `agent-execution-engine` via direct memory call -> State delta returned and persisted to local store.
- **Target**: Ultra-low latency edge deployments and Business-Centric mode.

### 2.2 Legacy/Heterogeneous Agent Integration (Service Mode)
- **Flow**: Task Arrives -> Service recognizes the target agent as a legacy system -> Dispatcher routes to Service Mode -> Dispatches over NATS/A2A Bus to the external legacy engine -> Collects state updates and resumes flow.
- **Target**: Enterprise hybrid setups with existing private agents.

### 2.3 Cross-Node Async A2A Collaboration
- **Flow**: Agent A encounters a roadblock -> A2A Client initiates connection using `a2a-java` -> Sub-task dispatched to Agent B's Server -> Agent A dehydrates and suspends -> Agent B completes computation and triggers callback -> Agent A rehydrates, recovers context, and finishes execution.

### 2.4 Ultra-fast Fast-Path Low Latency Execution
- **Flow**: Engine encounters a high-frequency read (e.g., looking up local memory values), yielding `InterruptSignal(TOOL_EXECUTION, RedisGet)` -> Service intercepts, queries the local cache, and calls `resume` inside the same thread -> Engine completes execution loop with sub-millisecond overhead as a deferred benchmark target.

### 2.5 Heterogeneous Shadow-Plugin Interception and Sandbox Execution
- **Flow**: LangChain agent executes -> Adapter translates context and boots engine -> LangChain requests SQL Tool execution -> Hits "Shadow SQL Tool" -> Interrupt signal raised, halting the LangChain stack -> Service captures signal, executes SQL safely within sandboxed DB connection -> Service boots adapter, rehydrates state, and injects results back to LangChain.

---

## 3. Logical View

### 3.1 Polymorphic Dispatcher
- Standard entrypoint of Agent invocations. Evaluates runtime configuration to route tasks dynamically to `LocalDirectExecutor` (embedded co-process) or `RemoteServiceExecutor` (stateless service RPC).
- Integrates A2A Routing to automatically convert cross-node agent targets into Google `a2a-java` protocol envelopes.

### 3.2 Engine Adapter
- Unifies Graph (Workflow) and Loop (ReAct) execution APIs into a single interface.
- **Heterogeneous Anti-Corruption Subsystem**:
  - *Framework Adapter*: Manages lifecycle mapping and bootstrapping for third-party frameworks like LangChain or LlamaIndex.
  - *Shadow Tool Interceptor*: Dynamically injects proxy tools that intercept calls and yield strongly-typed `InterruptSignal` objects.
  - *Context Translator*: Performs bidirectional mapping between platform-standard `InjectedContext` and framework-native formats.

### 3.3 Internal Event Queue
- Decouples network I/O threads from CPU/LLM processing.
  - *Memory-based (Business-Centric Mode)*: Implemented via Reactor Sinks or Disruptor for compact memory performance.
  - *Semi-persistent (Platform-Centric Mode)*: Leverages Redis or external Task Store to ensure failover capability and stateless scale-out.

### 3.4 A2A Connector
- Hosts Google **`a2a-java`** SDK Peer-to-Peer structures:
  - **A2A Server**: Exposes API listeners to accept incoming collaborations.
  - **A2A Client**: Provides socket-like outbound channels for engine sub-task spawning.

### 3.5 Task-Centric State Control & Routing Components
- **A2A State Controller**: Enforces the five states of the task lifecycle.
- **Interrupt Interceptor**: Catches `InterruptSignal` from the executor, parses subtypes, and handles lifecycle routing.
- **Dual-Track Router**: Directs intercepted signals. Routes lightweight signals to Fast-Path (direct execution and in-memory resumption) and heavy signals to Slow-Path (Memory-Postgres-Temporal dehydration, thread release, and asynchronous recovery).

### 3.6 Logical Boundary Mapping
- **Service Domain**: Memory Layer, Session Layer (SessionManager, ContextProjector), Task Control Plane (TaskCenter, PolymorphicDispatcher).
- **Engine Domain**: Task Execution Plane (无状态计算芯片), Run Layer (Workflow DAG or ReAct Loop).

---

## 4. Process View

### 4.1 Asynchronous Task Loop
1. **Task Intake**: Push request (REST/gRPC) or Pull trigger from Bus -> `ReactiveOrchestrator` translates request to `Task` -> Publishes to event queue -> Immediately returns `TaskID` to caller to keep I/O non-blocking.
2. **Backpressured Dispatch**: Reactor subscriber group pulls pending tasks according to capacity feedback `request(N)` and invokes `Engine Adapter`.
3. **Execution & Dehydration**: Engine returns `StateDelta` with a `Yield` (Suspended) state.
   - *Platform-Centric*: Service dehydrates state to shared store and releases JVM threads.
   - *Business-Centric*: Local in-memory state update.

### 4.2 A2A Collaboration and Interrupt Lifecycle
1. **Spawning**: Agent A requests collaboration. Adapter intercepts, packaging request into `a2a-java` message targeting Agent B.
2. **Dehydrated Wait**: Agent A yields. Orchestrator dehydrates A's execution context to Task Store and releases the executing thread.
3. **Rehydrated Resume**: Agent B finishes, calling A's listener. Orchestrator fetches A's context, reconstructs JVM state (rehydration), pushes back to queue, and resumes execution.

### 4.3 Four-Layer Life Cycle Flow
1. **Intake (Memory -> Session -> Task)**: Task registered, SessionManager aligns interaction.
2. **Projection (Session -> Task Context)**: ContextProjector extracts history, building `InjectedContext`.
3. **Execution (Task Context -> Runs Loop)**: Engine executes runs until completed or interrupted.
4. **Consolidation (Run Delta -> Session -> Memory)**: StateDelta written back to Session, asynchronously compiling long-term Memories.

### 4.4 Dual-Track Fast/Slow-Path Dispatch Loop
1. **Interrupt Yield**: Engine raises `InterruptSignal` during a run, halting calculation.
2. **Routing Assessment**: `Dual-Track Router` intercepts the signal and determines its weight:
   - **Fast-Path (In-Memory Bypass)**:
     - Retains the physical executing thread.
     - Invokes the lightweight resource directly.
     - Invokes engine `execute` inside the same thread to continue calculation.
   - **Slow-Path (Durable Dehydration)**:
     - Calls 3-level persistence SPI, serializing state to Postgres database.
     - Handover workflow control to Temporal engine.
     - Releases the physical JVM executing thread to the pool.
     - External sandbox / human approval completes, triggering Temporal callback.
     - Temporal rehydrates context on an active node, calling `resume`.

### 4.5 Heterogeneous Framework Shadow Interceptor Flow
1. **Bootstrap**: Service schedules legacy agent execution, entering `Engine Adapter`.
2. **Context Translation**: `Context Translator` maps `InjectedContext` to the third-party schema (e.g., LangChain Context).
3. **Shadow Tool Injection**: Adapter dynamically maps shadow proxy tools into the legacy engine.
4. **Intercept and Yield**: Legacy engine triggers a tool -> Shadow tool catches call -> Shadow tool throws a standard `InterruptSignal` -> Legacy stack is immediately forced to suspend.
5. **Sandbox Outbound**: Service intercepts signal, executing the physical action in a safe containerized sandbox.
6. **Reconstitution**: Sandboxed action returns -> Service loads adapter -> Adapter deserializes state -> Result injected back to the call frame, continuing native execution.

---

## 5. Development View

### 5.1 Open-Source and Proprietary Boundaries
Utilizes Google's **`a2a-java`** SDK to handle:
1. **Backpressure transport**: Reactor-based streaming channels.
2. **P2P Bus Foundations**: Dual-role `A2AClient`/`A2AServer` communication logic.
3. **Task-Centric Lifecycle**: Conforms to standard protocol transitions.

**In-house Proprietary Focus**: Local polymorphic routing, high-performance semantic projection algorithms, REST/gRPC microservice boundaries, and Temporal persistence SPI adapters.

### 5.2 Package Structure and Directory Layout
```text
agent-service/src/main/java/com/huawei/ascend/agent/service/
├── api/                        # [Proprietary + a2a-java adapter] Northbound API Boundary
│   ├── rest/                   # Push-mode endpoints (REST/gRPC)
│   └── a2a/                    # A2A-Server, catches incoming collaborative messages
├── dispatcher/                 # [100% Proprietary] Polymorphic Routing Core
│   ├── PolymorphicDispatcher.java # Entrypoint deciding between Direct memory or A2A call
│   └── strategy/               # Static/Dynamic routing rules
├── orchestrator/               # [a2a-java deep integration] Task lifecycle control
│   ├── ReactiveOrchestrator.java  # Binds to a2a-java scheduler to drive reactive event loops
│   ├── DualTrackRouter.java    # [100% Proprietary] Fast/Slow path dynamic router
│   └── handler/                # InterruptSignal strong-typed interceptors
├── task/                       # [Proprietary] Task states and databases
│   ├── TaskCenter.java         # Master task controller
│   └── repository/             # Implements a2a-java TaskStore SPI mapping to JPA/Redis
├── session/                    # [100% Proprietary] Conversation domain & semantic projection
│   ├── SessionManager.java     # Conversation session manager
│   └── projection/             # High-performance Semantic Context Projector
├── engine/                     # [Proprietary Adapters] Stateless computing adapter layer
│   ├── workflow/               # Workflow Graph engine SDK adapter
│   ├── react/                  # ReAct Loop engine SDK adapter
│   ├── heterogeneous/          # [100% Proprietary] Foreign framework sandbox and anti-corruption
│   │   ├── adapter/            # Framework Adapters (LangChainAdapter, LlamaIndexAdapter, etc.)
│   │   ├── shadow/             # Shadow Tool Interceptor core (ShadowToolInterceptor)
│   │   └── translation/        # Bidirectional Context Translator (ContextTranslator)
│   └── spi/                    # Proposed standard engine interface: StatelessEngineExecutor (not current)
└── infrastructure/             # [Proprietary glue] Middleware integrations
    ├── config/                 # Spring Boot AutoConfiguration for a2a-java
    └── persistence/            # NATS/Redis adapters, serializers, and client pool managers
```

---

## 6. Physical View

### 6.1 Embedded Co-process Topology
- `agent-service.jar` and `agent-execution-engine.jar` co-deployed within a single Pod/Process. Event queues are bound to JVM heap memory, yielding zero network overhead and local method-level A2A routing.

### 6.2 Decoupled Service-level Topology
- `agent-service` deployed as a centralized cluster. Execution engines run as separate nodes in container sandboxes.
- Multi-instance statelessness: Service nodes share Redis clusters and Postgres JPA task stores. Internal queues are mapped to NATS or RabbitMQ.
- A2A Networking: Service instances expose specific A2A Listener ports, mapping P2P connectivity over the Agent Bus.

### 6.3 Dual-Track Compute and Storage Boundaries
- **Fast-Path Boundaries**: Executed purely within the local JVM Heap memory of the current Runtime node. Bypasses database writes, maximizing high-throughput execution.
- **Slow-Path Boundaries**: Agent execution context serialized to JSON/Protobuf and saved in Postgres databases. Workflow orchestration is governed by Temporal server clusters using Event Sourcing, protecting long-term states from physical infrastructure failovers.

---

## 7. Appendix: Core SPI Interfaces

### 7.1 A2A Task State & Interrupt Specifications
```java
package com.huawei.ascend.agent.service.api;

import java.util.Map;

/**
 * A2A Standard Task Lifecycle States
 */
public enum TaskState {
    SUBMITTED,   // Task submitted, enters queue
    WORKING,     // Engine loaded context, begins execution
    SUSPENDED,   // Interrupted, state dehydrated
    COMPLETED,   // Execution complete, returns StateDelta
    FAILED       // Exception occurred, execution aborted
}

/**
 * Standard A2A Interrupt Types
 */
public enum InterruptType {
    INPUT_REQUIRED,   // Human-in-the-Loop input/approval wait
    SUB_TASK_AWAIT,   // A2A child agent task wait
    TOOL_EXECUTION,   // Engine requests Service to execute physical tool
    DELAY_AWAIT,      // Delayed timer trigger
    POLICY_APPROVAL   // Budget/Security guardrail wait
}

/**
 * Strongly-typed Interrupt Signal
 */
public interface InterruptSignal {
    String getTaskId();
    InterruptType getType();
    Map<String, Object> getPayload();
}
```

### 7.2 StatelessEngineExecutor Specification (proposed, not current)
```java
package com.huawei.ascend.agent.service.engine.spi;

import com.huawei.ascend.agent.service.api.InterruptSignal;
import java.util.List;
import java.util.Map;
import reactor.core.publisher.Mono;

/**
 * Target Stateless Computing SPI implemented by execution engines
 */
public interface StatelessEngineExecutor { // proposed, not current
    /**
     * Stateless execution entrypoint
     */
    Mono<StateDelta> execute(TaskSpec task, InjectedContext ctx);
}

public class TaskSpec {
    private String taskId;
    private String taskType;                  // WORKFLOW or REACT
    private Map<String, Object> parameters;
    
    // Getters and Setters...
}

public class InjectedContext {
    private String sessionId;
    private List<Message> messageHistory;      // Semantic projection output
    private List<Map<String, Object>> tools;   // Permitted tool definitions
    private Map<String, Object> sessionVars;   // Session variables
    
    // Getters and Setters...
}

public class Message {
    private String messageId;
    private String role;                      // USER, ASSISTANT, SYSTEM
    private String content;                   // Conversational natural language text
    private long timestamp;
    
    // Getters and Setters...
}

public class StateDelta {
    private List<Message> newMessages;         // Natural language return payload
    private Map<String, Object> updatedVars;   // Changed variables
    private InterruptSignal interruptSignal;   // Populated if engine yields, null if completed
    
    // Getters and Setters...
}
```

### 7.3 Dual-Track Router & Durable Orchestrator Specifications
```java
package com.huawei.ascend.agent.service.orchestrator;

import com.huawei.ascend.agent.service.api.InterruptSignal;
import com.huawei.ascend.agent.service.engine.spi.InjectedContext;
import reactor.core.publisher.Mono;

/**
 * Decides whether a yielded task routes to Fast-Path or Slow-Path
 */
public interface DualTrackRouter {
    /**
     * Route signal dynamically
     */
    Mono<Void> route(InterruptSignal signal, InjectedContext currentCtx);
    
    /**
     * Returns true if signal is lightweight (Fast-Path)
     */
    boolean isFastPath(InterruptSignal signal);
}

/**
 * Durable Distributed Slow-Path Executor
 */
public interface SlowPathExecutor {
    /**
     * Serializes execution state, saving to Postgres and handing control to Temporal
     */
    Mono<Void> dehydrateAndStore(String taskId, InjectedContext ctx, InterruptSignal signal);
    
    /**
     * Resumes the engine loop with the incoming resource payload
     */
    Mono<Void> resumeWorkflow(String taskId, Object result);
}
```

### 7.4 Shadow Tool Interceptor & Adapter Specifications
```java
package com.huawei.ascend.agent.service.engine.heterogeneous;

import com.huawei.ascend.agent.service.api.InterruptSignal;
import com.huawei.ascend.agent.service.engine.spi.InjectedContext;
import java.util.Map;

/**
 * Foreign Framework Anti-corruption Lifecycle Adapter
 */
public interface HeterogeneousEngineAdapter {
    /**
     * Translates standard InjectedContext to native third-party format
     */
    Object translateContext(InjectedContext standardCtx);
    
    /**
     * Deserializes legacy state, injecting the tool output into execution frame
     */
    Object resumeWithResult(String taskId, Object originalState, Object toolResult);
}

/**
 * Virtual Proxy registered within legacy engines to intercept execution
 */
public interface ShadowToolInterceptor {
    /**
     * Traps tool execution requests, throwing RuntimeException to abort third-party execution thread
     */
    void interceptAndYield(String toolName, Map<String, Object> arguments) throws RuntimeException;
}
```
