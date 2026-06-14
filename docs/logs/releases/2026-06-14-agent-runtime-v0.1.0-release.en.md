---
formal_release: true
release_candidate_branch: release/v0.1.0
release_candidate_commit: TBD-after-verification
status: formal-release-candidate
---

# agent-runtime v0.1.0 — Release Notes

> Release date: 2026-06-14
> Release engineer: TBD
> Artifact: `agent-runtime-0.1.0.jar`
> Feature baseline: `architecture/docs/L1/agent-runtime/features/agent-runtime-release-features.cn.md`

## Release Decision

- **Decision**: ship
- **Branch**: `release/v0.1.0`
- **Commit**: TBD (frozen after `mvn verify` passes)
- **Scope**: agent-runtime module only (`agent-sdk` and `agent-service` are in planning)

## Architecture Baseline at Release

| Metric | Value |
|--------|-------|
| Active engineering rules | 7 (D-1–D-5, D-9, G-7) |
| Active ADRs | 163 (ADR-0001–ADR-0163) |
| Active gate rules | 32 (all GREEN on release commit) |
| Maven tests | 90+ (all GREEN) |
| Architecture graph nodes | 265 |
| Architecture graph edges | 431 |
| recurring_defect_families | 41 |

## What Shipped

### 1. Heterogeneous Agent Framework Compatibility

Ten commits across Phases 1–4 of the `agent-runtime` module consolidation (ADR-0159).

**1.1 OpenJiuwen Adapter**
- In-process invocation via `openjiuwen-agent-core-java`
- Rails injection: trajectory tracking (`OpenJiuwenTrajectoryRail`), remote tool interrupt (`OpenJiuwenRemoteAgentInterruptRail`), memory injection (`MemoryRuntimeRail`)
- Checkpoint persistence: InMemory / SQLite via `CheckpointerFactory`
- ReActAgent support; Workflow not yet supported

**1.2 AgentScope Adapter**
- Three modes: local SDK Agent, Harness Agent, remote SSE client
- Error code mapping to standard `RuntimeErrorCode` classification
- Checkpoint and Memory not yet supported

**1.3 Versatile REST Proxy Adapter**
- Protocol-translation proxy: A2A JSON-RPC ↔ remote REST/SSE
- URL templates with placeholder substitution, two-level header passthrough, result extraction rules (match keyword → deep-find key)
- Interrupt detection for two-round remote continuation

**1.4 Adapter Abstraction Layer**
- `AgentRuntimeHandler` SPI: `execute()`, `cancel()`, `resultAdapter()`
- `AgentExecutionResult`: OUTPUT / COMPLETED / FAILED / INTERRUPTED
- `AbstractAgentRuntimeHandler` base class with trajectory lifecycle

### 2. Middleware Services — Memory & State

**2.1 Memory Service**
- `MemoryProvider` SPI: `search(userId, sessionId, query, limit)` + `save(userId, sessionId, records)`
- Pre-built OpenJiuwen memory integration: auto-retrieve before invocation, auto-save after
- Current limitation: injected only at round start, no mid-round retrieval

**2.2 State Persistence**
- OpenJiuwen Checkpoint via `CheckpointerFactory` (InMemory / SQLite)
- AgentScope Checkpoint not yet supported

### 3. S2C Communication Models + A2A Protocol

**3.1 Three Communication Models**
- Blocking: `SendMessage` — A2A layer collects Handler Stream → single JSON response
- Streaming: `SendStreamingMessage` — SSE push with terminal-state closure, `SubscribeToTask` reconnection
- Async: `GetTask` / `CancelTask` / `ListTasks` — full Task lifecycle

**3.2 A2A Methods**
- Six methods: `SendMessage`, `SendStreamingMessage`, `GetTask`, `CancelTask`, `ListTasks`, `SubscribeToTask`
- Push notification config CRUD endpoints (SDK components assembled, push not yet activated)

**3.3 Agent Card Discovery**
- `GET /.well-known/agent-card.json` + `/.well-known/agent.json`
- YAML-driven card auto-generation with skills and capabilities declaration
- `AgentCardProvider` SPI for programmatic card metadata

### 4. Trajectory Observability

- Framework-neutral event model: 8 `Kind` types (RUN, MODEL_CALL, TOOL_CALL, ERROR, PROGRESS)
- `StampingTrajectoryEmitter`: automatic seq, span tree nesting, wall-clock timestamps
- `TrajectoryMasking`: configurable regex-based sensitive field redaction
- Adapter coverage: OpenJiuwen (5 kinds), AgentScope (4 kinds)
- Parent-child trace linking via `parentTaskId`/`parentTraceId`

### 5. Remote Agent Orchestration (A2A Southbound)

- YAML-configured remote A2A endpoints (`agent-runtime.remote-agents`)
- Adaptive Card cache: 10s fast retry → 600s keepalive → exponential backoff
- Remote tool injection: Card skills → `RemoteAgentToolSpec` → OpenJiuwen Tool
- Interrupt-resume pipeline: remote `INPUT_REQUIRED` → parent Task suspension → user input → continuation
- Cancel cascading to remote tasks

### 6. Operations

- Lifecycle: start → serve → stop → drain with graceful shutdown and readiness gate
- Health: Actuator Health Indicator per Handler (UP / OFF_OF_SERVICE / DOWN)
- Diagnostics: MDC logging (contextId, taskId, tenantId, agentId), `RuntimeErrorCode` classification
- Embedded deployment: `RuntimeApp.create(handler).run(host)` API (Spring Boot only)

## Documentation Shipped

| Layer | Location | Count |
|-------|----------|-------|
| L1 Feature checklist | `architecture/docs/L1/agent-runtime/features/` | 2 docs |
| L2 Design docs | `architecture/docs/L2/agent-runtime/` | 5 docs |
| Developer guides | `agent-runtime/docs/guides/` | 12 docs |
| E2E Examples | `examples/agent-runtime-*/` | 11 projects |

## Known Limitations

| Limitation | Impact |
|------------|--------|
| No MCP protocol support | Cannot connect to MCP tool ecosystem |
| No gRPC transport (HTTP+SSE only) | Higher latency for high-throughput scenarios |
| AgentScope: no Checkpoint, no Memory | AgentScope agents are stateless |
| OpenJiuwen: cancel only stops consumption, not LLM call | Long-running LLM calls cannot be truly interrupted |
| Only Spring Boot deployment | No non-Spring-Boot RuntimeHost implementation |
| Remote agent: no dynamic discovery | Remote endpoints must be statically configured in YAML |

Full ⬜ list: `architecture/docs/L1/agent-runtime/features/agent-runtime-release-features.cn.md`
