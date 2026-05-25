# Spring-AI-Ascend Architecture Review and Comparison Report

> **Slug placeholder note (added 2026-05-26 per rc53-wave-5 / family `F-placeholder-leaks-into-active-corpus`)**: the curly-brace token `{wanshoulu}` in this file's slug is an anonymous-name placeholder (万寿路 — a Beijing street name used as a code-name for the original reviewer). The slug is retained for stable-URL preservation per `docs/governance/logs-folder-policy.md`; the placeholder leak is now documented rather than scrubbed to avoid breaking inbound links. Future review-document slugs use thematic naming (e.g., `agent-service-l1-4plus1-rewrite-wave-1` style).
>
> **Purpose**: A comprehensive architecture review comparing Spring-AI-Ascend with OpenJiuwen and Claude Code, identifying design gaps and improvement recommendations
>
> **Version**: 1.0 | **Date**: 2026-05-13

---

## Executive Summary

This report provides a thorough architectural analysis of Spring-AI-Ascend, comparing it with two representative architectures in the AI Agent space:

- **OpenJiuwen**: An AgentOS with multi-agent native support, having been validated in ToB enterprise scenarios
- **Claude Code**: A model-driven agent with a minimalist design philosophy ("Less scaffolding, more model")

### Key Findings

| Category | Spring-AI-Ascend | OpenJiuwen | Claude Code |
|----------|------------------|------------|-------------|
| **Positioning** | Financial-grade runtime kernel | AgentOS | Model-driven Agent |
| **Maturity** | W0 scaffold stage | Production-ready | Production-ready |
| **Multi-Agent** | W2 planned | Native support | Experimental |
| **SDK Support** | None | Python/Java SDK | Claude Agent SDK |
| **Multi-tenancy** | Financial-grade (RLS+GUC) | Enterprise-grade | Single-user |

---

## 1. Dual-Track Execution Model Analysis

### 1.1 Design Intent

Spring-AI-Ascend implements a dual-track execution model with `Run.mode` distinguishing between `GRAPH` and `AGENT_LOOP` modes:

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                     Dual-Track Execution Model                               │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│   GRAPH Mode (Deterministic State Machine):                                 │
│   • Use cases: Fixed workflows, approval chains, compliance checks          │
│   • Financial scenarios: KYC flows, transaction approvals, risk checks      │
│   • Characteristics: Deterministic nodes, deterministic edges, predictable  │
│                                                                             │
│   AGENT_LOOP Mode (ReAct-style Reasoning):                                  │
│   • Use cases: Open-ended reasoning, tool calling, dynamic decisions        │
│   • Financial scenarios: Intelligent customer service, investment advisory  │
│   • Characteristics: Reasoner-driven, iterative, dynamic termination        │
│                                                                             │
│   Unified Interruption Primitive: SuspendSignal                             │
│   • Both modes share the same interruption mechanism                        │
│   • Supports three-level bidirectional nesting                              │
│   • Compile-time visibility (throws SuspendSignal)                          │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 1.2 Dual-Track Coordination Mechanism Analysis

**Current Design:**

The dual-track coordination relies on `SuspendSignal` as the unified interruption primitive:

```java
// SuspendSignal carries coordination context
public final class SuspendSignal extends Exception {
    private final String parentNodeKey;      // Suspension point identifier
    private final Object resumePayload;      // Resume payload
    private final RunMode childMode;         // Child Run execution mode
    private final ExecutorDefinition childDef; // Child execution definition
}
```

**Coordination Flow:**

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                     Dual-Track Coordination Flow                             │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│   1. GRAPH → AGENT_LOOP coordination:                                       │
│      • NodeFunction throws SuspendSignal with childMode = AGENT_LOOP        │
│      • Orchestrator catches, creates child Run                              │
│      • IterativeAgentLoopExecutor executes child                            │
│      • Result returned to GRAPH node via resume                             │
│                                                                             │
│   2. AGENT_LOOP → GRAPH coordination:                                       │
│      • Reasoner throws SuspendSignal with childMode = GRAPH                 │
│      • Orchestrator catches, creates child Run                              │
│      • SequentialGraphExecutor executes child                               │
│      • Result returned to AGENT_LOOP iteration via resume                   │
│                                                                             │
│   3. Three-level nesting validation (NestedDualModeIT):                     │
│      • L1: GRAPH → L2: AGENT_LOOP → L3: GRAPH                              │
│      • Verified bidirectional coordination                                  │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

**Identified Issues:**

| Issue | Severity | Description |
|-------|----------|-------------|
| **Recovery Protocol Inconsistency** | Medium | GRAPH uses `_graph_next_node`, AGENT_LOOP uses `_loop_resume_iter` + `_loop_resume_state` |
| **No Explicit Coordination State** | Medium | SuspendSignal carries coordination context but no dedicated coordination state machine |
| **Limited Nesting Depth** | Low | Three-level nesting is verified but not formally bounded |

### 1.3 Comparison with Claude Code

| Dimension | Spring-AI-Ascend | Claude Code |
|-----------|------------------|-------------|
| **Execution Model** | Dual-track (GRAPH + AGENT_LOOP) | Single loop (while(tool_call)) |
| **Coordination Mechanism** | SuspendSignal (Checked Exception) | Natural termination (no tool_call) |
| **Nesting Support** | Three-level bidirectional | depth=1 sub-agents |
| **Recovery** | Checkpointer SPI | Session persistence (CLAUDE.md) |

**Key Insight**: Claude Code's minimalist design ("Less scaffolding, more model") achieves the same goal with significantly less complexity. The model decides everything - when to call tools, which tools to call, and when to terminate.

---

## 2. Component Registration, Discovery, and Coordination Analysis

### 2.1 Current Design (W0)

**Skill SPI (ADR-0030, W2 planned):**

```java
public interface Skill {
    String name();
    SkillTrustTier trustTier();
    SkillResourceMatrix resourceMatrix();
    
    void init(SkillContext ctx) throws Exception;
    SkillCostReceipt execute(SkillContext ctx, Object input) throws Exception;
    SkillResumeToken suspend(SkillContext ctx);
    void resume(SkillContext ctx, SkillResumeToken token) throws Exception;
    void teardown(SkillContext ctx);
}
```

**SkillKind Taxonomy:**

```java
public enum SkillKind {
    JAVA_NATIVE,              // Java class on classpath; direct invocation
    MCP_TOOL,                 // Remote tool via MCP Java SDK
    SANDBOXED_CODE_INTERPRETER // Code block via SandboxExecutor SPI
}
```

**CapabilityRegistry (W2 planned):**

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                     CapabilityRegistry Design (W2)                           │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│   Registration:                                                              │
│   ┌─────────────────────────────────────────────────────────────────────┐  │
│   │ CapabilityRegistry.register(name, SkillKind, SkillTrustTier, Skill) │  │
│   └─────────────────────────────────────────────────────────────────────┘  │
│                                                                             │
│   Discovery:                                                                 │
│   ┌─────────────────────────────────────────────────────────────────────┐  │
│   │ Skill skill = CapabilityRegistry.resolve(name)                      │  │
│   │ // Returns Skill implementation by name                              │  │
│   └─────────────────────────────────────────────────────────────────────┘  │
│                                                                             │
│   Coordination:                                                              │
│   ┌─────────────────────────────────────────────────────────────────────┐  │
│   │ Orchestrator → Skill.init() → Skill.execute() → Skill.teardown()    │  │
│   │                    ↓                                                │  │
│   │              Skill.suspend() (on SUSPENDED)                          │  │
│   │                    ↓                                                │  │
│   │              Skill.resume() (on resume)                              │  │
│   └─────────────────────────────────────────────────────────────────────┘  │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 2.2 Component Coordination Issues

| Component | Issue | Severity | Description |
|-----------|-------|----------|-------------|
| **Skill** | Deferred to W2 | High | No Skill SPI implementation at W0 |
| **MCP** | Deferred to W3 | High | No MCP native support; cannot leverage MCP ecosystem |
| **CLI** | Not designed | Medium | No CLI interface for local development |
| **Plugin** | Not designed | Medium | No plugin system for extensibility |
| **Workflow** | GRAPH mode only | Medium | No visual workflow orchestration |
| **Sub-agent** | SuspendSignal-based | Medium | No explicit sub-agent lifecycle management |

### 2.3 Comparison with OpenJiuwen

| Dimension | Spring-AI-Ascend | OpenJiuwen |
|-----------|------------------|------------|
| **Skill Registry** | W2 planned | Native Skill ecosystem |
| **MCP Support** | W3 planned | Native MCP support |
| **Plugin System** | Not designed | Native plugin system |
| **Workflow Orchestration** | GRAPH mode | Agent-Studio (visual) |
| **Sub-agent Coordination** | SuspendSignal | Leader-Teammate architecture |

### 2.4 Comparison with Claude Code

| Dimension | Spring-AI-Ascend | Claude Code |
|-----------|------------------|------------|
| **Tool Protocol** | W3 planned | Native MCP support |
| **Plugin System** | Not designed | Hooks + Plugins |
| **Sub-agent** | SuspendSignal nesting | Task tool (depth=1) |
| **SDK** | None | Claude Agent SDK |

---

## 3. Client/Server Architecture Analysis

### 3.1 Current Design

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                     Spring-AI-Ascend Client/Server Architecture              │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│   Client Layer:                                                              │
│   • HTTP Client (curl/Postman) - No official SDK                            │
│   • SDK Client - Not provided                                               │
│   • MCP Client - W3 planned                                                 │
│                                                                             │
│   Server Layer (agent-platform):                                            │
│   • Northbound API: GET /v1/health, POST /v1/runs (W1)                      │
│   • Three-Track Channel (W2): Control, Data, Heartbeat                      │
│   • Filter Chain: TenantContextFilter, IdempotencyHeaderFilter              │
│                                                                             │
│   Runtime Layer (agent-runtime):                                            │
│   • Orchestrator SPI: SyncOrchestrator (W0), PostgresOrchestrator (W2)      │
│   • Skill SPI (W2): JAVA_NATIVE, MCP_TOOL, SANDBOXED_CODE_INTERPRETER       │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 3.2 Client/Server Issues

| Issue | Severity | Description |
|-------|----------|-------------|
| **No Official SDK** | High | Users must manually wrap HTTP calls; increases integration cost |
| **Streaming Response Deferred to W2** | Medium | W0 only supports synchronous return; long-running agents cannot provide real-time feedback |
| **MCP Protocol Deferred to W3** | Medium | Tool calls require Java implementation; cannot leverage MCP ecosystem |

### 3.3 Comparison

| Dimension | Spring-AI-Ascend | OpenJiuwen | Claude Code |
|-----------|------------------|------------|-------------|
| **SDK Support** | None | Python/Java/TypeScript SDK | Claude Agent SDK |
| **Streaming** | W2 planned | Native | Native |
| **Multi-tenancy** | Financial-grade (RLS+GUC) | Enterprise-grade | Single-user |
| **Channel Integration** | HTTP only | WeChat/Feishu/HarmonyOS/DingTalk | CLI/IDE |

---

## 4. Rule Enforcement Mechanism Analysis

### 4.1 Current Design (Most Comprehensive)

Spring-AI-Ascend implements a four-layer rule enforcement mechanism:

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                     Rule Enforcement Mechanism                                │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│   1. Compile-time Enforcement (Java Language Features):                     │
│   ┌─────────────────────────────────────────────────────────────────────┐  │
│   │ • SuspendSignal as Checked Exception                                │  │
│   │   → Compiler forces all callers to declare throws SuspendSignal     │  │
│   │   → Prevents missing interruption handling logic                     │  │
│   │                                                                     │  │
│   │ • SPI interfaces only import java.*                                 │  │
│   │   → Compiler checks import statements                               │  │
│   │   → Rule violation causes compilation failure                       │  │
│   └─────────────────────────────────────────────────────────────────────┘  │
│                                                                             │
│   2. Runtime Validation (State Machine):                                    │
│   ┌─────────────────────────────────────────────────────────────────────┐  │
│   │ • RunStateMachine.validate(from, to)                                │  │
│   │   → Forced validation before every state transition                 │  │
│   │   → Illegal transition throws IllegalStateException                 │  │
│   └─────────────────────────────────────────────────────────────────────┘  │
│                                                                             │
│   3. Architecture Testing (ArchUnit):                                       │
│   ┌─────────────────────────────────────────────────────────────────────┐  │
│   │ • gate/check_architecture_sync.ps1                                  │  │
│   │   → Validates module dependency direction                           │  │
│   │   → Validates SPI purity                                            │  │
│   │   → CI/CD pipeline enforcement                                      │  │
│   └─────────────────────────────────────────────────────────────────────┘  │
│                                                                             │
│   4. Posture Gate (AppPostureGate):                                         │
│   ┌─────────────────────────────────────────────────────────────────────┐  │
│   │ • Runtime check of APP_POSTURE environment variable                 │  │
│   │ • dev/research/prod three modes                                     │  │
│   │ • Restricts in-memory components in non-dev environments            │  │
│   └─────────────────────────────────────────────────────────────────────┘  │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 4.2 Comparison

| Dimension | Spring-AI-Ascend | OpenJiuwen | Claude Code |
|-----------|------------------|------------|-------------|
| **Compile-time Enforcement** | ✅ Checked Exception + SPI purity | ❌ None | ❌ None |
| **Runtime Validation** | ✅ RunStateMachine | ✅ Agent security protocol | ✅ Permission system |
| **Architecture Testing** | ✅ ArchUnit + CI/CD | ❌ None | ❌ None |
| **Posture Gate** | ✅ dev/research/prod | ❌ None | ❌ None |
| **ML-assisted** | ❌ None | ❌ None | ✅ ML-based classifier |
| **Rule Count** | 44 (most) | Undeclared | Fewest |

---

## 5. State Machine Implementation Analysis

### 5.1 Current Design

```java
// State Definition
public enum RunStatus {
    PENDING, RUNNING, SUSPENDED, SUCCEEDED, FAILED, CANCELLED, EXPIRED
}

// State Transition Rules (DFA)
private static final Map<RunStatus, Set<RunStatus>> ALLOWED = Map.of(
    PENDING,    Set.of(RUNNING, CANCELLED),
    RUNNING,    Set.of(SUSPENDED, SUCCEEDED, FAILED, CANCELLED),
    SUSPENDED,  Set.of(RUNNING, EXPIRED, FAILED, CANCELLED),
    FAILED,     Set.of(RUNNING),  // Retry
    SUCCEEDED,  Set.of(),         // Terminal
    CANCELLED,  Set.of(),         // Terminal
    EXPIRED,    Set.of()          // Terminal
);

// Forced Validation Entry Point
public Run withStatus(RunStatus newStatus) {
    RunStateMachine.validate(this.status, newStatus);  // Forced validation
    return new Run(..., newStatus, ...);
}
```

### 5.2 Comparison

| Dimension | Spring-AI-Ascend | OpenJiuwen | Claude Code |
|-----------|------------------|------------|-------------|
| **State Machine Type** | DFA (Deterministic Finite Automaton) | Event-driven | No state machine |
| **State Count** | 7 (fixed) | Infinite (event-driven) | No explicit state |
| **State Transition Validation** | ✅ Forced validation | ❌ None | ❌ None |
| **Intermediate State Details** | ✅ SuspendReason (W2) | ✅ Full event trace | ❌ None |
| **Audit Trail** | ✅ State transition audit (W2) | ✅ Full event audit | ⚠️ Session log |
| **Recovery Mechanism** | Checkpointer SPI | Event replay | Session recovery |
| **Use Case** | Financial compliance | Enterprise applications | Personal development |

---

## 6. Multi-Agent Support Analysis

### 6.1 Current Design (W2 Planned)

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                     Multi-Agent Support Evolution Path                       │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│   W0 (Current): Single-process parent-child nesting                         │
│   • SuspendSignal + parentRunId                                             │
│   • Three-level bidirectional nesting validation                            │
│                                                                             │
│   W2 (Planned): Multi-child Run parallel + JoinPolicy + tenant isolation    │
│   • SuspendReason.AwaitChildren (N child Runs, JoinPolicy)                  │
│   • SuspendReason.SwarmDelegation (SWARM scope delegation)                  │
│   • CapabilityRegistry tenant-scoped                                        │
│                                                                             │
│   W4 (Planned): Temporal persistent workflow + distributed coordination     │
│                                                                             │
│   Post-W4 (Planned): A2A federation + cross-process Agent discovery         │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 6.2 Multi-Agent Issues

| Issue | Severity | Description |
|-------|----------|-------------|
| **No Agent Identity Concept** | High | Run is an execution record, not an Agent identity; cannot support long-term memory and personalization |
| **No Shared Workspace** | High | Multi-agent collaboration requires shared state and files; current design relies on parent-child Run payload passing |
| **No Task Claiming Mechanism** | High | Cannot support proactive claiming, independent execution, automatic reporting |
| **No Message-driven Coordination** | Medium | No task-external message channel for negotiation and feedback |

### 6.3 Comparison with OpenJiuwen AgentTeam

| Dimension | Spring-AI-Ascend | OpenJiuwen |
|-----------|------------------|------------|
| **Agent Identity** | ❌ None (Run is execution record) | ✅ AgentSubject |
| **Team Architecture** | W2 planned | ✅ Native Leader-Teammate |
| **Shared Workspace** | ❌ Not designed | ✅ Native Team Workspace |
| **Task Claiming** | ❌ Not designed | ✅ Native support |
| **Message Coordination** | ❌ Not designed | ✅ Native support |
| **File Locking** | ❌ Not designed | ✅ Consistency guarantee |
| **Fault Recovery** | ⚠️ State machine recovery | ✅ Auto-recovery + Leader approval |
| **Observability** | ⚠️ Basic metrics | ✅ TeamMonitor full monitoring |

---

## 7. Architecture Problem Summary

| Problem Category | Spring-AI-Ascend | OpenJiuwen | Claude Code |
|------------------|------------------|------------|-------------|
| **Agent Identity** | 🔴 Missing (deferred) | ✅ Native support | ⚠️ Session-level |
| **Multi-Agent Coordination** | 🔴 W2 planned | ✅ Native support | ⚠️ Experimental |
| **Shared Workspace** | 🔴 Not designed | ✅ Native support | ❌ None |
| **Official SDK** | 🔴 None | ✅ Python/Java SDK | ✅ Claude Agent SDK |
| **Streaming Response** | 🟡 Deferred to W2 | ✅ Native support | ✅ Native support |
| **MCP Protocol** | 🟡 Deferred to W3 | ✅ Native support | ✅ Native support |
| **Multi-tenancy Isolation** | ✅ Financial-grade (RLS+GUC) | ✅ Enterprise-grade | ❌ Single-user |
| **Audit Compliance** | ✅ Financial-grade | ⚠️ Partial support | ⚠️ Session log |
| **State Machine Management** | ✅ DFA forced validation | ⚠️ Task lifecycle | ❌ None |
| **Rule Enforcement** | ✅ Compile-time + Runtime + Test | ⚠️ Runtime | ⚠️ Runtime + ML |

---

## 8. Improvement Recommendations

### 8.1 Learn from Claude Code

| Learning Point | Recommendation |
|----------------|----------------|
| **Minimalist Design** | Evaluate simplifying dual-track mechanism; consider unified event-driven model |
| **Model-driven** | Reduce hardcoded rules; let the model make more decisions |
| **MCP Native Support** | Advance to W2 instead of W3 |
| **Official SDK** | Provide Java/Python SDK to reduce integration barrier |

### 8.2 Learn from OpenJiuwen

| Learning Point | Recommendation |
|----------------|----------------|
| **Agent Identity** | Introduce AgentSubject concept |
| **Shared Workspace** | Design Team Workspace |
| **Task Claiming** | Support proactive claiming mechanism |
| **Low-code Development** | Consider visual orchestration |

### 8.3 Maintain Differentiated Advantages

| Advantage | Recommendation |
|-----------|----------------|
| **Financial Compliance** | Maintain state machine DFA and audit trail |
| **Multi-tenancy Isolation** | Maintain RLS + GUC design |
| **Rule Enforcement** | Maintain compile-time + runtime + test three-layer guarantee |
| **Posture Model** | Maintain dev/research/prod three modes |

---

## 9. Conclusion

Spring-AI-Ascend demonstrates a well-designed architecture for financial compliance scenarios with:

**Strengths:**
- Comprehensive rule enforcement mechanism (compile-time + runtime + architecture testing + posture gate)
- Financial-grade multi-tenancy isolation (RLS + GUC)
- State machine DFA with forced validation
- SPI purity for portability

**Gaps:**
- No Agent identity concept (critical for multi-agent scenarios)
- No shared workspace (critical for AgentTeam scenarios)
- No official SDK (increases integration cost)
- MCP protocol deferred to W3 (cannot leverage MCP ecosystem)

**Recommendation:**
The architecture is well-suited for financial compliance scenarios but needs to address the multi-agent coordination gaps to compete effectively in enterprise applications. The recommendations in Section 8 provide a prioritized roadmap for improvement.

---

## References

- Spring-AI-Ascend Source Code Analysis
- ARCHITECTURE.md and ADR Documents
- OpenJiuwen Official Documentation (https://www.openjiuwen.com)
- Claude Code Architecture Guide (Anthropic Engineering Blog)
- MCP Protocol Specification (https://modelcontextprotocol.io)

---

**Document Version**: 1.0  
**Author**: Architecture Review Team  
**Date**: 2026-05-13
