
# L0 Architecture Analysis: Client-Server Separation and Heterogeneous Runtime Compatibility

Date: 2026-05-13
Audience: Architecture design team
Scope reviewed: root `ARCHITECTURE.md`, `agent-platform/ARCHITECTURE.md`, `agent-runtime/ARCHITECTURE.md`, all orchestration SPI sources, `pom.xml` dependency declarations, W0-W4 engineering plan, and competitive analysis documents.

## 1. Context and Design Intent

### 1.1 The Stated Design Philosophy

The project's L0 design intent (as articulated by the architecture owner) is:

1. **Agent = Client + Server**: The agent system is split into two deployment units.
2. **Client deploys inside the business system**: It lives in the same JVM as the business application, has direct access to business data and logic.
3. **Server deploys remotely**: It hosts the cognitive runtime, interacts with LLMs, MCP servers, vector stores, and other external AI infrastructure.
4. **Server must be runtime-agnostic**: It should support heterogeneous Java AI platform runtimes (Spring AI, LangChain4j, AgentScope-Java) without requiring a full rewrite.

### 1.2 Why This Matters for Financial Services

In financial services, this split is not optional — it is driven by:

- **Data sovereignty**: Customer PII, transaction data, and risk models cannot leave the business system's security perimeter without explicit authorization.
- **Regulatory compliance**: The business system controls what data is exposed to AI systems; the AI system cannot have unrestricted database access.
- **Operational independence**: The AI runtime can be upgraded, scaled, or replaced without touching the business system's deployment.
- **Vendor flexibility**: Financial institutions do not want to be locked into a single AI framework; they need the ability to switch between Spring AI, LangChain4j, or future frameworks as the market evolves.

### 1.3 The Four Questions Under Review

| # | Question | Core Concern |
|---|----------|--------------|
| Q1 | Does AgentClient need to be defined? How? | SDK contract and deployment boundary |
| Q2 | Does the Client-Server interface need to be defined? How? | Communication protocol |
| Q3 | How does Server interact with business systems? | Data flow and permission model |
| Q4 | Is heterogeneous runtime support technically feasible? How? | Framework abstraction |

---

## 2. Current Project State Assessment

### 2.1 What Exists Today

Before analyzing the gaps, let us establish what the current W0 implementation provides:

**Orchestration SPI (pure Java, framework-agnostic)**:
- `Orchestrator` — top-level entry point, owns suspend/checkpoint/resume loop
- `GraphExecutor` — deterministic graph traversal
- `AgentLoopExecutor` — ReAct-style iterative reasoning
- `SuspendSignal` — checked exception for interrupt-driven nesting
- `Checkpointer` — suspend-point persistence
- `RunContext` — per-run context (tenantId, checkpointer, suspendForChild)
- `ExecutorDefinition` — sealed type (GraphDefinition | AgentLoopDefinition)

**Key architectural enforcement**:
- `OrchestrationSpiArchTest`: SPI packages import only `java.*` (no Spring)
- `TenantPropagationPurityTest`: runtime code cannot import `TenantContextHolder`
- `ApiCompatibilityTest`: module dependency direction enforced

**Platform layer (Spring Boot)**:
- `TenantContextFilter` — X-Tenant-Id header binding
- `IdempotencyHeaderFilter` — Idempotency-Key UUID validation
- `HealthController` — GET /v1/health with Postgres ping
- `WebSecurityConfig` — permitAll for health/actuator/api-docs, denyAll for rest

**Runtime dependencies (in agent-runtime/pom.xml)**:
- `spring-ai-starter-model-anthropic` (compile scope)
- `spring-ai-starter-model-openai` (compile scope)
- `spring-ai-starter-vector-store-pgvector` (compile scope)
- `io.modelcontextprotocol.sdk:mcp` (compile scope)
- `io.temporal:temporal-sdk` (compile scope)
- `org.apache.tika:tika-core` (compile scope)

### 2.2 What Does NOT Exist Today

- No `AgentClient` interface or module
- No bidirectional communication protocol (only unidirectional HTTP)
- No callback mechanism (Server cannot request data from Client)
- No `CognitiveEngine` SPI abstracting over AI frameworks
- No framework adapter modules
- No deployment topology constraint in §4
- `SuspendSignal` only supports internal child-run nesting, not external callbacks
- `AwaitExternal` suspend reason exists only in ADR-0019 design text, not in code

---

## 3. Question 1: Does AgentClient Need to Be Defined? How?

### 3.1 Reasoning Process

**Starting assumption**: Maybe we don't need a formal Client SDK. Business systems could just call the Server's REST API directly using any HTTP client.

**Counter-arguments that led to rejecting this assumption**:

1. **Connection lifecycle complexity**: A long-running agent task (minutes to hours) requires the caller to maintain an SSE connection for progress events, handle reconnection on network failures, and manage heartbeat timeouts. This is non-trivial boilerplate that every business system would need to reimplement.

2. **Callback registration**: If the Server needs to request business data mid-execution (Q3), the Client must expose an HTTP endpoint that the Server can call back. This requires an embedded HTTP server within the business system — not something you want every team to build from scratch.

3. **Type safety and contract evolution**: A raw HTTP client gives no compile-time guarantees about request/response shapes. When the Server API evolves (new fields, deprecated endpoints), every business system's hand-written HTTP calls break silently. A typed SDK catches these at compile time.

4. **Authentication token management**: The Client needs to manage JWT acquisition, refresh, and injection into every request. This is security-critical code that should be centralized.

5. **Retry and resilience**: The Client should handle transient failures (503, network timeout) with exponential backoff, circuit breaking, and idempotent retries. This is the same pattern for every caller.

**Conclusion**: Yes, AgentClient must be formally defined as a first-class architectural concept.

### 3.2 How to Define AgentClient

**Design constraints derived from the use case**:

| Constraint | Rationale |
|------------|-----------|
| Zero Spring dependency | Must embed in any Java application (Spring Boot, Quarkus, Micronaut, plain Java) |
| Async-first API | Must not block business threads during long-running agent tasks |
| Callback-capable | Must be able to receive and respond to Server data requests |
| Tenant-aware | Must carry tenant identity on every interaction |
| Minimal footprint | Business systems should not need to add 50 transitive dependencies |
| Java 11+ compatible | Business systems may not be on Java 21 yet |

**Proposed interface hierarchy**:

```java
/**
 * Primary entry point for business systems to interact with the Agent Server.
 * Zero Spring dependency. Requires only java.* + one HTTP client (pluggable).
 */
public interface AgentClient extends AutoCloseable {

    /**
     * Submit a new agent run. Returns immediately with a handle for tracking.
     */
    RunHandle submitRun(RunRequest request);

    /**
     * Query the current status of a run.
     */
    RunSnapshot getRunStatus(UUID runId);

    /**
     * Cancel a running or suspended run.
     */
    CancelResult cancelRun(UUID runId);

    /**
     * Resume a suspended run with external data (response to a callback).
     */
    void resumeRun(UUID runId, ResumePayload payload);

    /**
     * Register a handler for Server-initiated callbacks.
     * The handler is invoked when the Server needs business data during execution.
     */
    void registerCallbackHandler(CallbackHandler handler);

    /**
     * Subscribe to run events (progress, state changes, completion).
     */
    EventSubscription subscribeToEvents(UUID runId, EventListener listener);
}
```

**Why this shape**:

- `submitRun` returns `RunHandle` (not `RunResult`) because runs may take minutes. The business system should not block.
- `registerCallbackHandler` is separate from `submitRun` because the same handler serves all runs for a tenant. It is registered once at application startup.
- `subscribeToEvents` is separate from `submitRun` because the business system may want to subscribe to events for a run it did not initiate (e.g., monitoring dashboard).
- `resumeRun` is the Client's response to a callback — the Server suspended the run waiting for data, and the Client provides it.

**Callback handler interface**:

```java
public interface CallbackHandler {

    /**
     * Server requests business data. Client queries its local systems and responds.
     * Returning null or throwing signals "data unavailable" to the Server.
     */
    DataResponse onDataRequest(DataRequestEnvelope envelope);

    /**
     * Server requests human approval for a sensitive action.
     * Client routes to the appropriate approval workflow.
     */
    ApprovalResponse onApprovalRequest(ApprovalRequestEnvelope envelope);

    /**
     * Server requests the Client to execute a business action (with side effects).
     * Client validates permissions and executes if authorized.
     */
    ActionResponse onActionRequest(ActionRequestEnvelope envelope);
}
```

**Why three callback types**:

- **DataRequest**: Read-only, no side effects. Example: "What is customer C001's risk rating?"
- **ApprovalRequest**: Requires human decision. Example: "Approve trade execution for $1M?"
- **ActionRequest**: Has side effects. Example: "Create a new compliance ticket for review."

These three types have fundamentally different security, latency, and audit requirements. Collapsing them into one generic callback would lose important semantic information.

### 3.3 Module Structure Recommendation

```
spring-ai-ascend-client/                    # New module
  pom.xml                                   # Dependencies: java.net.http only (JDK 11+)
  src/main/java/com/huawei/ascend/client/
    AgentClient.java                        # Core interface
    AgentClientBuilder.java                 # Fluent builder
    impl/
      DefaultAgentClient.java              # JDK HttpClient-based implementation
      SseEventStreamReader.java            # SSE parsing for event subscription
      CallbackHttpServer.java             # Embedded Javalin/Jetty-lite for receiving callbacks
    model/
      RunRequest.java
      RunHandle.java
      RunSnapshot.java
      RunEvent.java
      ResumePayload.java
    callback/
      CallbackHandler.java
      DataRequestEnvelope.java
      DataResponse.java
      ApprovalRequestEnvelope.java
      ApprovalResponse.java
      ActionRequestEnvelope.java
      ActionResponse.java
    auth/
      TokenProvider.java                   # Interface for JWT acquisition
      StaticTokenProvider.java             # Simple: fixed token
      RefreshableTokenProvider.java        # OAuth2 client_credentials flow
```

---

## 4. Question 2: Client-Server Interface Definition

### 4.1 Reasoning Process

**Starting point**: The current project has HTTP endpoints planned for W2 (`POST /v1/runs`, `GET /v1/runs/{id}`, `POST /v1/runs/{id}/cancel`). Are these sufficient as the Client-Server interface?

**Analysis of communication patterns needed**:

| Pattern | Example | Current Support |
|---------|---------|-----------------|
| Client submits work | "Analyze this customer's portfolio" | ✅ POST /v1/runs (W2) |
| Client queries status | "Is the analysis done?" | ✅ GET /v1/runs/{id} (W2) |
| Client cancels work | "Stop the analysis" | ✅ POST /v1/runs/{id}/cancel (W2) |
| Server pushes progress | "I'm 60% done, found 3 issues so far" | ⚠️ Designed (§4 #11 Flux<RunEvent>) but not implemented |
| Server requests data | "I need customer C001's transaction history" | ❌ No mechanism |
| Server requests approval | "Should I proceed with this trade?" | ❌ No mechanism |
| Server requests action | "Please create a compliance ticket" | ❌ No mechanism |
| Server sends heartbeat | "I'm still alive, working on it" | ⚠️ Designed (§4 #28 heartbeat track) but not implemented |

**Key insight**: The first three patterns (Client → Server) are standard REST. The last five patterns (Server → Client, or Server → Client → Server) require either:

- **Option A**: Long-polling (Client repeatedly asks "any callbacks for me?")
- **Option B**: WebSocket (persistent bidirectional connection)
- **Option C**: SSE + Reverse HTTP (Server pushes events via SSE; Server calls Client's HTTP endpoint for callbacks)
- **Option D**: gRPC bidirectional streaming

**Evaluation of options**:

| Option | Firewall-friendly | Complexity | Latency | Scalability |
|--------|-------------------|------------|---------|-------------|
| A: Long-polling | ✅ | Low | High (polling interval) | Poor (many idle connections) |
| B: WebSocket | ⚠️ (some proxies block) | Medium | Low | Good |
| C: SSE + Reverse HTTP | ✅ | Medium | Low | Good |
| D: gRPC bidirectional | ❌ (HTTP/2 required, proxy issues) | High | Lowest | Best |

**Decision reasoning for Option C (SSE + Reverse HTTP)**:

1. **Firewall compatibility**: Financial institutions often have strict egress rules. SSE works over standard HTTPS (port 443). Reverse HTTP callbacks work if the Client exposes an endpoint (which it controls).
2. **Separation of concerns**: Events (progress, heartbeat) flow on the SSE channel. Callbacks (data requests) flow on the reverse HTTP channel. These have different reliability requirements — events can be dropped (at-most-once), callbacks must be acknowledged (at-least-once).
3. **Existing design alignment**: The project already designs three tracks (§4 #28): Control, Data, Heartbeat. Adding a fourth "Callback" track fits naturally.
4. **Fallback for no-callback scenarios**: If the Client cannot expose an HTTP endpoint (e.g., behind a strict NAT), the system degrades gracefully — the Server suspends the Run and waits for the Client to poll and resume manually.

### 4.2 Protocol Specification

**Four-channel model**:

```
┌─────────────────────────────────────────────────────────────────┐
│                    Client-Server Protocol                         │
├─────────────┬───────────────┬──────────────┬────────────────────┤
│  Control    │  Event Stream │  Heartbeat   │  Callback          │
│  (C → S)    │  (S → C)      │  (S → C)     │  (S → C → S)      │
├─────────────┼───────────────┼──────────────┼────────────────────┤
│  HTTP/2     │  SSE          │  SSE (same   │  HTTP POST         │
│  POST/GET   │               │  connection) │  (reverse)         │
├─────────────┼───────────────┼──────────────┼────────────────────┤
│  Submit     │  NodeStarted  │  Ping every  │  DataRequest       │
│  Cancel     │  NodeCompleted│  ≤30s        │  ApprovalRequest   │
│  Resume     │  Progress     │              │  ActionRequest     │
│  Query      │  Terminal     │              │                    │
└─────────────┴───────────────┴──────────────┴────────────────────┘
```

**Callback flow (detailed)**:

```
Time →

Server                              Client                          Business System
  │                                    │                                │
  │ [LLM reasoning needs data]         │                                │
  │                                    │                                │
  ├─── POST /callback/data-request ───►│                                │
  │    {                               │                                │
  │      correlationId: "abc-123",     │                                │
  │      runId: "run-456",             │                                │
  │      operationType: "customer_risk",│                               │
  │      parameters: {customerId:"C001"},                               │
  │      deadline: "2026-05-13T10:05:00Z"│                              │
  │    }                               │                                │
  │                                    ├─── query local DB/API ────────►│
  │                                    │                                │
  │                                    │◄── {risk_level: "HIGH"} ───────┤
  │                                    │                                │
  │◄── 200 {data: {risk_level:"HIGH"}}─┤                                │
  │                                    │                                │
  │ [Resume reasoning with data]       │                                │
  │                                    │                                │
```

**What happens when the Client is offline or slow**:

```
Server                              Client (offline)
  │                                    │
  ├─── POST /callback/data-request ───►│ ← connection refused / timeout
  │                                    │
  │ [Callback failed]                  │
  │ [Run.status → SUSPENDED]           │
  │ [SuspendReason: AwaitExternal]     │
  │ [deadline: T+5min]                 │
  │                                    │
  │ ... time passes ...                │
  │                                    │
  │                                    │ [Client comes back online]
  │                                    │
  │◄── POST /v1/runs/{id}/resume ──────┤ {data: {risk_level: "HIGH"}}
  │                                    │
  │ [Run.status → RUNNING]             │
  │ [Continue reasoning]               │
```

This degradation path uses the existing `SUSPENDED → RUNNING` transition and the `AwaitExternal` suspend reason from ADR-0019.

### 4.3 Integration with Current Architecture

The protocol maps to existing concepts:

| Protocol Element | Maps To |
|-----------------|---------|
| Control channel (submit) | `Orchestrator.run()` entry point |
| Control channel (cancel) | `RunStatus.CANCELLED` transition |
| Control channel (resume) | `SuspendSignal` resume path |
| Event stream | §4 #11 `Flux<RunEvent>` (W2 design) |
| Heartbeat | §4 #28 heartbeat track (W2 design) |
| Callback | `AwaitExternal` suspend reason (ADR-0019 design) |
| Callback timeout → EXPIRED | `RunStatus.EXPIRED` (already in DFA) |

**Key realization**: The existing architecture already has all the internal primitives needed. What is missing is the external protocol layer that connects these primitives to a remote Client.

---

## 5. Question 3: Server-to-Business-System Interaction

### 5.1 Reasoning Process

**The fundamental tension**: The Agent Server needs business data to reason effectively, but it should not have direct access to business systems (security, compliance, data sovereignty).

**Exploring the design space**:

**Option A: All data pre-injected at submission time**

```java
RunRequest request = RunRequest.builder()
    .capability("portfolio-analysis")
    .context(Map.of(
        "customer", customerService.getFullProfile(id),
        "positions", portfolioService.getPositions(id),
        "transactions", txService.getLast90Days(id),
        "market_data", marketService.getCurrentPrices(symbols)
    ))
    .build();
client.submitRun(request);
```

*Pros*: Simple, no callback needed, all data available immediately.
*Cons*:
- Cannot predict what data the LLM will need (it depends on reasoning path).
- May send too much data (waste bandwidth, increase context window cost).
- May send too little data (LLM cannot complete the task).
- Stale data problem: if the run takes 10 minutes, market prices from submission time are outdated.

**Verdict**: Necessary as a baseline, but insufficient for complex multi-step reasoning.

**Option B: Server directly accesses business system APIs**

```
Server ──── service account credentials ────► Business System API
```

*Pros*: Low latency, no Client involvement needed.
*Cons*:
- Requires the business system to expose APIs to the Server's network.
- Requires managing service account credentials (rotation, scope limitation).
- Violates the principle that the business system controls data exposure.
- Audit trail is harder (who authorized this access? the Server? which run? which tenant?).
- In financial services, this often violates data classification policies.

**Verdict**: Acceptable only for public/low-sensitivity data (e.g., market prices from a public feed). Not acceptable for customer PII or internal business data.

**Option C: Server requests data through Client callback**

```
Server ──callback──► Client ──local access──► Business Data
```

*Pros*:
- Business system retains full control over what data is exposed.
- Client can apply fine-grained authorization (this run is allowed to see risk ratings but not account balances).
- Audit trail is clear (Client logs every callback it serves).
- No direct network path from Server to business databases.

*Cons*:
- Added latency (network round-trip to Client).
- Client must be online when the callback arrives (or Run suspends).
- More complex protocol.

**Verdict**: This is the primary interaction model for sensitive business data.

**Option D: Hybrid approach**

Combine all three based on data sensitivity:

| Data Type | Interaction Mode | Example |
|-----------|-----------------|---------|
| Task parameters | Pre-injected (Option A) | "Analyze customer C001's portfolio" |
| Public reference data | Server direct access (Option B) | Market prices, exchange rates |
| Customer PII | Client callback (Option C) | Name, address, account balances |
| Internal business logic | Client callback (Option C) | Risk models, compliance rules |
| Computed results | Client callback (Option C) | "What is the current P&L for position X?" |

**Final decision**: Option D (Hybrid) with Option C as the default for any data that is not explicitly classified as public.

### 5.2 Detailed Callback Interaction Design

**Security model for callbacks**:

```java
public record CallbackPolicy(
    // What operations the Server is allowed to request
    List<AllowedOperation> allowedOperations,
    
    // Maximum time the Server will wait for a callback response
    Duration callbackTimeout,
    
    // Whether callback payloads must be encrypted in transit
    boolean requireEncryption,
    
    // Maximum payload size the Client will accept/return
    int maxPayloadBytes,
    
    // Rate limit: max callbacks per minute per run
    int maxCallbacksPerMinute
) {}

public record AllowedOperation(
    String operationType,           // e.g., "customer_profile", "transaction_history"
    List<String> allowedFields,     // e.g., ["name", "risk_level"] (not "ssn", "account_number")
    boolean requiresApproval        // If true, human must approve before data is returned
) {}
```

**Why per-run callback policies**: Different agent capabilities need different data access. A "portfolio analysis" capability needs position data but not compliance records. A "compliance check" capability needs compliance records but not trading history. The policy is declared per capability and enforced by the Client.

**Callback authentication**:

```
1. Client registers with Server: "My callback URL is https://biz.internal:9443/agent-callback"
2. Server generates a per-run callback token (JWT, short-lived, scoped to runId + tenantId)
3. Server includes this token in every callback request header: Authorization: Bearer <token>
4. Client validates the token before processing any callback
5. Client rejects callbacks with expired/invalid/wrong-tenant tokens
```

### 5.3 Does Server Ever Directly Interact with Business Systems?

**Analysis**: In the hybrid model, yes — but only for specific, pre-authorized, low-sensitivity data sources.

**Examples where direct access is acceptable**:

| Data Source | Why Direct Access Is OK |
|-------------|------------------------|
| Public market data feed | No PII, no business secrets, publicly available |
| Shared knowledge base (company wiki) | Already published internally, no access control needed |
| Pre-computed embeddings in vector store | Derived data, already sanitized |

**Examples where direct access is NOT acceptable**:

| Data Source | Why Callback Is Required |
|-------------|--------------------------|
| Customer database | PII, regulated, access must be audited |
| Trading system | Side effects possible, authorization required |
| Internal risk models | Proprietary, access controlled |
| Compliance records | Regulated, need-to-know basis |

**Architectural rule**: "Agent Server MAY directly access data sources that are classified as PUBLIC or SHARED-INTERNAL. Agent Server MUST use the Client callback channel for data classified as RESTRICTED, CONFIDENTIAL, or PII."

---

## 6. Question 4: Heterogeneous Runtime Compatibility

### 6.1 Reasoning Process

**The question**: Can one Agent Server support Spring AI, LangChain4j, and AgentScope-Java simultaneously or interchangeably?

**Step 1: Identify what "runtime" means in this context**

An AI platform runtime provides:
1. **LLM client abstraction**: How you call the model (ChatClient vs ChatLanguageModel vs AgentRuntime)
2. **Tool/function calling**: How tools are defined and invoked
3. **Memory/context management**: How conversation history is maintained
4. **Embedding/vector operations**: How text is embedded and searched
5. **Prompt templating**: How prompts are constructed

**Step 2: Identify what the current project's orchestration layer needs from a runtime**

Looking at the SPI:
- `ExecutorDefinition.Reasoner.reason(RunContext ctx, Object payload, int iteration)` — needs to call an LLM and return a result
- `ExecutorDefinition.NodeFunction.apply(RunContext ctx, Object payload)` — needs to execute logic (may or may not involve LLM)
- `Checkpointer.save/load` — pure persistence, no AI framework involvement
- `RunContext.suspendForChild` — pure orchestration, no AI framework involvement

**Key insight**: The orchestration layer only needs ONE thing from the AI framework: **"given a prompt and tools, call the LLM and return the response"**. Everything else (state management, checkpointing, nesting, lifecycle) is handled by the orchestration SPI.

**Step 3: Define the minimal abstraction needed**

```java
public interface CognitiveEngine {
    CognitiveResponse invoke(CognitiveRequest request);
}
```

That's it. The orchestration layer calls `CognitiveEngine.invoke()` inside a `Reasoner` or `NodeFunction`. The `CognitiveEngine` implementation can use Spring AI, LangChain4j, or any other framework internally.

**Step 4: Assess dependency conflicts**

| Dependency | Spring AI 2.0.0-M5 | LangChain4j 1.14 | Conflict? |
|------------|--------------------|--------------------|-----------|
| Jackson | 2.17.x (via Spring Boot 4) | 2.15.x-2.17.x | ⚠️ Minor version mismatch possible |
| HTTP Client | Reactor Netty (WebClient) | OkHttp 4.x | ✅ No conflict (different libraries) |
| Reactive | Project Reactor 3.6.x | None (sync) | ✅ No conflict |
| SLF4J | 2.0.x | 2.0.x | ✅ Compatible |
| Annotations | Jakarta EE 10 | None | ✅ No conflict |

**Conclusion on same-JVM coexistence**: Spring AI and LangChain4j CAN coexist in the same JVM if:
- Jackson ObjectMapper instances are isolated (each framework uses its own configured instance)
- No global static configuration is shared
- HTTP client instances are separate

This is achievable with careful dependency management. The risk is LOW for these two specific frameworks.

**AgentScope-Java**: This project is too young (as of 2026-05) to assess definitively. Its dependency footprint is unknown. Recommend deferring AgentScope support to post-W4 when the project matures.

**Step 5: Evaluate architecture patterns**

**Pattern A: Adapter in same JVM (recommended for W2)**

```
┌─────────────────────────────────────────────┐
│ Agent Server JVM                             │
│                                             │
│  Orchestrator ──► CognitiveEngine SPI       │
│                        │                    │
│                   ┌────┴────┐               │
│                   │ Adapter │               │
│                   └────┬────┘               │
│                        │                    │
│              ┌─────────┼─────────┐          │
│              ▼         ▼         ▼          │
│         Spring AI  LangChain4j  (future)    │
│                                             │
└─────────────────────────────────────────────┘
```

*Implementation*: One adapter is active per deployment (selected by configuration). Multiple adapters can coexist on the classpath but only one is wired as the active `CognitiveEngine` bean.

**Pattern B: Process isolation (for incompatible frameworks)**

```
┌──────────────────┐     ┌──────────────────┐
│ Agent Server     │     │ Runtime Worker    │
│ (Orchestrator)   │────►│ (Spring AI)       │
│                  │gRPC │                   │
│                  │     └──────────────────┘
│                  │     ┌──────────────────┐
│                  │────►│ Runtime Worker    │
│                  │gRPC │ (LangChain4j)    │
└──────────────────┘     └──────────────────┘
```

*When needed*: Only if two frameworks have irreconcilable dependency conflicts (e.g., incompatible native libraries, conflicting global state).

**Pattern C: Per-tenant runtime selection**

```java
// Configuration
springai.ascend.runtime.default=springai
springai.ascend.runtime.tenants.tenant-a=langchain4j
springai.ascend.runtime.tenants.tenant-b=springai
```

*Use case*: Different tenants have different framework preferences or licensing constraints.

### 6.2 Feasibility Verdict

| Scenario | Feasible? | Approach | Wave |
|----------|-----------|----------|------|
| Single framework per deployment | ✅ Yes | Adapter pattern, config-selected | W2 |
| Switch framework without code change | ✅ Yes | CognitiveEngine SPI + adapter modules | W2 |
| Spring AI + LangChain4j in same JVM | ✅ Yes (with care) | Isolated ObjectMapper instances | W3 |
| Per-tenant framework selection | ✅ Yes | Multi-adapter with tenant routing | W3 |
| AgentScope-Java support | ⚠️ Unknown | Defer until project matures | post-W4 |
| Simultaneous incompatible frameworks | ✅ Yes (complex) | Process isolation via gRPC | post-W4 |

### 6.3 Required Changes to Current Project

**Step 1: Extract CognitiveEngine SPI (W1)**

Create in `agent-runtime-core` (or keep in `agent-runtime` under a new `cognitive/spi/` package):

```java
package com.huawei.ascend.runtime.cognitive.spi;

// Pure Java — no Spring, no framework imports

public interface CognitiveEngine {
    CognitiveResponse chat(CognitiveRequest request);
    CognitiveResponse chatWithTools(CognitiveRequest request, List<ToolDefinition> tools);
    EmbeddingResult embed(EmbeddingRequest request);
    boolean supports(String modelId);
}

public record CognitiveRequest(
    String modelId,
    List<Message> messages,
    Map<String, Object> generationParameters,
    String tenantId,
    String runId
) {}

public record Message(Role role, String content, List<ToolCall> toolCalls) {}
public enum Role { SYSTEM, USER, ASSISTANT, TOOL }

public record CognitiveResponse(
    String content,
    List<ToolCall> toolCalls,
    UsageMetrics usage,
    String finishReason
) {}

public record ToolCall(String id, String name, String arguments) {}
public record ToolDefinition(String name, String description, String parametersSchema) {}
public record UsageMetrics(int inputTokens, int outputTokens, String modelId) {}
public record EmbeddingRequest(List<String> texts, String modelId, String tenantId) {}
public record EmbeddingResult(List<float[]> embeddings, UsageMetrics usage) {}
```

**Step 2: Create Spring AI adapter module (W2)**

```xml
<!-- agent-runtime-springai/pom.xml -->
<artifactId>agent-runtime-springai</artifactId>
<dependencies>
    <dependency>
        <groupId>com.huawei.ascend</groupId>
        <artifactId>agent-runtime</artifactId>  <!-- for CognitiveEngine SPI -->
    </dependency>
    <dependency>
        <groupId>org.springframework.ai</groupId>
        <artifactId>spring-ai-starter-model-anthropic</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.ai</groupId>
        <artifactId>spring-ai-starter-model-openai</artifactId>
    </dependency>
</dependencies>
```

```java
package com.huawei.ascend.runtime.springai;

public class SpringAiCognitiveEngine implements CognitiveEngine {
    private final ChatClient chatClient;
    
    @Override
    public CognitiveResponse chat(CognitiveRequest request) {
        Prompt prompt = toSpringAiPrompt(request);
        ChatResponse response = chatClient.prompt(prompt).call().chatResponse();
        return fromSpringAiResponse(response);
    }
    
    // ... conversion methods ...
}
```

**Step 3: Create LangChain4j adapter module (W3)**

```java
package com.huawei.ascend.runtime.langchain4j;

public class LangChain4jCognitiveEngine implements CognitiveEngine {
    private final ChatLanguageModel model;
    
    @Override
    public CognitiveResponse chat(CognitiveRequest request) {
        List<dev.langchain4j.data.message.ChatMessage> messages = toLangChain4jMessages(request);
        Response<AiMessage> response = model.generate(messages);
        return fromLangChain4jResponse(response);
    }
    
    // ... conversion methods ...
}
```

**Step 4: Remove Spring AI from agent-runtime core (W2)**

Move these dependencies from `agent-runtime/pom.xml` to `agent-runtime-springai/pom.xml`:
- `spring-ai-starter-model-anthropic`
- `spring-ai-starter-model-openai`
- `spring-ai-starter-vector-store-pgvector`

Keep in `agent-runtime/pom.xml` (they are framework-agnostic):
- `io.modelcontextprotocol.sdk:mcp` (MCP is a protocol, not an AI framework)
- `io.temporal:temporal-sdk` (workflow engine, not AI framework)
- `org.apache.tika:tika-core` (document parsing, not AI framework)

**Step 5: Refactor OssApiProbe (W2)**

Current `OssApiProbe` imports Spring AI classes directly. After refactoring:
- `agent-runtime` keeps a probe that verifies MCP + Temporal + Tika
- `agent-runtime-springai` has its own probe that verifies Spring AI classes
- `agent-runtime-langchain4j` has its own probe that verifies LangChain4j classes

---

## 7. Synthesis: How the Four Questions Interconnect

### 7.1 Dependency Graph of Decisions

```
Q4 (Heterogeneous Runtime)
  │
  ├── Requires: CognitiveEngine SPI extraction
  │     │
  │     └── Enables: Framework adapter modules
  │
  └── Constrains: agent-runtime must be framework-agnostic
        │
        └── Validates: Current SPI purity (OrchestrationSpiArchTest) is correct direction

Q1 (AgentClient Definition)
  │
  ├── Requires: Clear deployment boundary (Client JVM ≠ Server JVM)
  │
  └── Enables: Q2 (Protocol) and Q3 (Callback)

Q2 (Client-Server Protocol)
  │
  ├── Requires: Q1 (Client must exist to have a protocol)
  │
  ├── Enables: Q3 (Callback channel is part of the protocol)
  │
  └── Constrains: Must work across network boundaries (firewall-friendly)

Q3 (Server-Business Interaction)
  │
  ├── Requires: Q2 (Callback channel defined in protocol)
  │
  ├── Requires: Q1 (Client mediates all business data access)
  │
  └── Constrains: Server MUST NOT directly access restricted business data
```

### 7.2 Implementation Order (Critical Path)

```
Week 1-2 (W1 scope):
  ├── Define CognitiveEngine SPI (pure Java)
  ├── Define AgentClient interface (pure Java)
  ├── Write ADR-0032 (Client SDK) and ADR-0033 (Protocol)
  └── Add §4 #29 (Client-Server Separation constraint)

Week 3-5 (W2 scope):
  ├── Extract Spring AI deps into agent-runtime-springai
  ├── Implement SpringAiCognitiveEngine adapter
  ├── Implement AwaitExternal suspend reason in SuspendSignal
  ├── Implement callback channel (Server → Client HTTP POST)
  ├── Implement spring-ai-ascend-client module
  └── Implement SSE event stream (Server → Client)

Week 6-8 (W3 scope):
  ├── Implement LangChain4jCognitiveEngine adapter
  ├── Implement CallbackPolicy enforcement
  ├── Implement per-tenant runtime selection
  └── Integration test: full Client-Server round-trip with callbacks
```

### 7.3 Impact on Existing Architecture Constraints

| Existing Constraint | Impact | Action |
|---------------------|--------|--------|
| §4 #1 Dependency direction | Extended: Client module has no deps on Server internals | Add Client to dependency diagram |
| §4 #7 SPI purity | Extended: CognitiveEngine SPI must also be pure Java | Add to OrchestrationSpiArchTest scope |
| §4 #9 Dual-mode runtime | Unchanged: Orchestrator still owns suspend/resume | No change |
| §4 #11 Northbound handoff | Extended: SSE event stream becomes the Client's event channel | Align with Client EventSubscription |
| §4 #14 Resume re-authorization | Extended: Callback responses are a form of resume | Callback token validation = re-auth |
| §4 #19 Suspend-reason taxonomy | Implemented: AwaitExternal becomes real | Move from design-only to shipped |
| §4 #22 Canonical run context | Extended: RunContext gains requestExternalData() | New method on RunContext |
| §4 #28 Three-track channels | Extended: Add fourth "Callback" track | Update constraint text |

---

## 8. Risk Assessment

### 8.1 Risks of Implementing This Design

| Risk | Probability | Impact | Mitigation |
|------|-------------|--------|------------|
| CognitiveEngine SPI is too narrow (misses framework-specific features) | Medium | High | Design SPI with extension points; allow framework-specific options via `Map<String,Object> parameters` |
| Callback latency degrades LLM reasoning quality | Low | Medium | Allow pre-injection for predictable data; callbacks only for dynamic needs |
| Client offline causes Run accumulation in SUSPENDED state | Medium | Medium | Deadline-based expiration (already in DFA); alerting on suspended run count |
| Spring AI and LangChain4j Jackson conflict | Low | Medium | Isolated ObjectMapper instances; integration test with both on classpath |
| Protocol versioning becomes complex | Low | High | Version the protocol from day 1; include version in every message header |
| Callback security (token theft, replay) | Low | High | Short-lived tokens (5min), TLS required, nonce in every callback |

### 8.2 Risks of NOT Implementing This Design

| Risk | Probability | Impact | Consequence |
|------|-------------|--------|-------------|
| Business systems build ad-hoc HTTP clients | High | Medium | Inconsistent error handling, no retry logic, security gaps |
| Server directly accesses business databases | High | High | Compliance violation, audit failure, data breach risk |
| Locked into Spring AI forever | Medium | High | Cannot adopt better frameworks as they emerge |
| No callback = all data must be pre-injected | High | Medium | Complex tasks fail or require massive context windows |
| No deployment separation = monolithic coupling | Medium | High | Cannot scale/upgrade AI runtime independently |

---

## 9. Comparison with Industry Approaches

### 9.1 How Others Solve This

| Platform | Client-Server Split | Callback Model | Multi-Runtime |
|----------|--------------------|--------------------|---------------|
| OpenAI Assistants API | Yes (API = Server, SDK = Client) | No (polling only) | No (OpenAI only) |
| LangGraph Cloud | Yes (SDK + Cloud) | No (state is server-side) | No (LangChain only) |
| AutoGen (Microsoft) | No (single process) | N/A | No (OpenAI/Azure only) |
| CrewAI | No (single process) | N/A | Partial (multiple LLM providers) |
| Semantic Kernel (Microsoft) | Partial (plugins can be remote) | Yes (function calling) | Yes (multiple AI services) |
| Google Vertex AI Agent Builder | Yes (API = Server) | Yes (webhooks) | No (Google only) |

**Key observation**: Most platforms do NOT solve the callback problem well. They either require all data upfront or assume the AI system has direct access. The financial services requirement for mediated data access through a Client is a differentiator for spring-ai-ascend.

### 9.2 What spring-ai-ascend Can Learn

From **Semantic Kernel**: The plugin model (local functions that the AI can call) is analogous to our callback model. SK allows plugins to be local (in-process) or remote (HTTP). Our design should similarly allow callbacks to be either local (for testing/dev) or remote (for production).

From **Google Vertex AI**: Webhooks for agent-to-business-system communication are proven at scale. The pattern of "agent suspends, sends webhook, waits for response" is exactly our `AwaitExternal` design.

From **OpenAI Assistants**: The polling model (Client repeatedly checks status) is a valid fallback when callbacks are not possible. Our design should support both push (callback) and pull (polling) modes.

---

## 10. Conclusions and Recommendations

### 10.1 Summary of Findings

1. **AgentClient MUST be defined** (Q1): The deployment topology requires a formal Client SDK. The current project has no Client concept. The Client must be pure Java, async-first, and callback-capable.

2. **The Client-Server protocol MUST be defined** (Q2): A four-channel model (Control, Event Stream, Heartbeat, Callback) provides the necessary communication patterns. SSE + Reverse HTTP is the recommended transport for firewall compatibility.

3. **Server-to-business interaction flows through Client callbacks** (Q3): The hybrid model (pre-injection for simple data, callbacks for dynamic/sensitive data, direct access only for public data) balances security with capability. The `AwaitExternal` suspend reason is the correct internal mechanism.

4. **Heterogeneous runtime support IS technically feasible** (Q4): The orchestration SPI is already framework-agnostic. Extracting a `CognitiveEngine` SPI and creating per-framework adapter modules is straightforward. Same-JVM coexistence of Spring AI and LangChain4j is feasible with isolated configurations.

### 10.2 Priority Actions

| Priority | Action | Deliverable |
|----------|--------|-------------|
| P0 | Add §4 #29 Client-Server Separation constraint | ARCHITECTURE.md update |
| P0 | Define CognitiveEngine SPI | New interface in agent-runtime |
| P0 | Define AgentClient interface | New module spring-ai-ascend-client |
| P1 | Write ADR-0032 (Client SDK) | docs/adr/0032-agent-client-sdk.md |
| P1 | Write ADR-0033 (Bidirectional Protocol) | docs/adr/0033-client-server-protocol.md |
| P1 | Extract Spring AI into adapter module | agent-runtime-springai module |
| P2 | Implement AwaitExternal in SuspendSignal | Code change in orchestration SPI |
| P2 | Implement callback channel | New code in agent-platform |
| P3 | LangChain4j adapter | agent-runtime-langchain4j module |

### 10.3 What the Current Architecture Gets Right

The existing design provides an excellent foundation:

- **SPI purity** (§4 #7) means the orchestration layer is already framework-agnostic.
- **SuspendSignal** is the correct primitive for "pause and wait for external input."
- **ADR-0019** already designs the `AwaitExternal` suspend reason — conceptual work is done.
- **RunStateMachine DFA** already includes `SUSPENDED → RUNNING` (resume) and `SUSPENDED → EXPIRED` (timeout) — the lifecycle for callbacks is already modeled.
- **Posture model** naturally extends: dev = no Client needed (Server self-sufficient with mocks), prod = Client callback required.
- **Tenant propagation** (Rule 21, RunContext.tenantId()) ensures the Server always knows which tenant's Client to call back.

The gap is not in design quality but in architectural scope. The L0 design was conceived as a server-side runtime. Extending it to a Client-Server distributed system requires adding the Client boundary, the bidirectional protocol, and the framework abstraction layer — all of which build naturally on the existing SPI foundation.

## Closing Note

This analysis recommends treating the Client-Server split as an L0 architectural principle (not a feature to be added later). The reason: if the Server is built assuming co-location with business data (direct database access, shared JVM), retrofitting a Client-Server split later requires fundamental redesign. By establishing the boundary now — even if the W0-W1 implementation uses a "local Client" that calls the Server in-process — the architecture remains honest about its deployment topology from the start.

The existing `SuspendSignal` + `AwaitExternal` + `RunStateMachine` design is remarkably well-suited to this split. The primary work is making the implicit explicit: define the Client, define the protocol, extract the framework dependency, and implement the callback channel.
```

---
