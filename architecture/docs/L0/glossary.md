---
level: L0
view: logical
status: draft
authority: "Consolidated from docs/architecture/l0/00-overview/glossary.md and current architecture vocabulary"
source_of_truth: true
---

# L0 Glossary

## Purpose

This glossary constrains shared project vocabulary. It prevents architecture
authors, AI agents, and module owners from using similar names for different
layers, states, or responsibilities.

When a term is defined by an accepted ADR, module metadata, generated
architecture fact, or accepted contract, that source wins. This glossary records
the current L0 reading and flags pending vocabulary decisions.

## Terms

| Term | Meaning | Owner / Home | Do Not Confuse With | Status |
|---|---|---|---|---|
| Architecture fact system | L0/L1/L2 + 4+1 architecture truth used to guide and constrain architecture. | `architecture/` | Version scope backlog | accepted |
| Version scope system | Requirements, scenarios, feature use cases, function points, delivery slices, and acceptance scope for a release. | To be defined | Architecture authority | pending_location |
| Run | Current canonical/shipped runtime execution aggregate in existing L0 and code vocabulary, including `Run`, `RunStatus`, `RunRepository`, `RunContext`, and run tree terms. | `agent-service` current authority | Task, Session, business order | accepted_current |
| Task | Draft proposed server-side canonical execution/control state in delivery material. It may become the preferred name for what current authority often calls Run. | `agent-service` candidate | Session, Memory, client invocation | pending_decision |
| Client invocation | Client-side call reference or SDK invocation handle that may map to a server-side Run/Task. | `agent-client` + `agent-service` query surface | Independent server lifecycle state | candidate_promote |
| Session | Context state for conversation, variables, and context projection continuity. | `agent-service` session boundary | Run/Task lifecycle, Memory | accepted_direction |
| Memory | Knowledge or experience state exposed through memory SPI or external memory adapters. | `agent-middleware` and configured memory providers | Session temporary context | accepted_direction |
| Checkpoint | Resume/recovery payload saved before suspend or long-horizon interruption. | Checkpointer SPI / runtime owner | Business state snapshot | accepted_direction |
| Agent | Registered entity binding model, skills, memory, planner, prompt, and advisors for execution. | `agent-service` agent SPI | Orchestrator | accepted_direction |
| Orchestrator | Runtime component that dispatches work, handles suspend/resume, and emits execution/state intent. | Engine/service/bus boundary per accepted ADRs | Lifecycle state owner | accepted_direction |
| EnginePort | Neutral engine boundary assigned to `agent-bus` by ADR-0158. | `agent-bus` | Engine adapter implementation | accepted_current |
| Engine adapter | Concrete execution adapter and registry/envelope behavior. | `agent-execution-engine` | Neutral bus-owned EnginePort | accepted_current |
| RuntimeMiddleware | Cross-cutting middleware hook listener and dispatch surface. | `agent-middleware` | Provider implementation | accepted_current |
| ModelGateway | Platform model invocation boundary. | `agent-middleware` model SPI | Direct Spring AI `ChatModel` use | accepted_direction |
| Skill | Governed tool/skill execution unit. | `agent-middleware` skill SPI | Ungoverned business function call | accepted_direction |
| Tool Gateway | Capability aggregate for skill authorization, capacity, audit, idempotency, and tool-call governance. | `agent-middleware` + `agent-service` integration | Independent reactor module | candidate_promote |
| Context Engine | Capability aggregate for session, context projection, memory, retrieval, vector, and context package assembly. | `agent-service` + `agent-middleware` | Independent reactor module | candidate_promote |
| Gateway | External ingress capability. Current runtime exposes `agent-service` as the HTTP service boundary. | infrastructure + `agent-service` edge | Bus, engine, business orchestrator | accepted_direction |
| C-Side | Business application/client side that owns business goals, rules, facts, local tools, local context, and authorization references. | business application side | Platform runtime state | accepted_direction |
| S-Side | Platform runtime side that owns execution trajectory, governance, observability, audit, capacity, and platform middleware. | platform runtime side | Business facts owner | accepted_direction |
| Capability placement | Decision of where a tool, context, memory, retriever, approval UI, adapter, or A2A action executes and which data boundary it crosses. | architecture + contracts | Module placement only | candidate_promote |
| Local capability | Capability executed on the business/client side, such as local tool, local context, local memory, local retriever, or approval UI. | `agent-client` endpoint | Platform-hosted capability | candidate_promote |
| S2C callback | Server-to-client callback or handoff for local capability, approval, or external input. | `agent-bus` S2C + `agent-client` endpoint | A2A federation | accepted_direction |
| A2A control command | Agent-to-Agent control instruction for child work, federation, completion, failure, timeout, or join. | `agent-bus` for cross-boundary control; `agent-service` for same-service relationship | Large data payload or token stream | accepted_direction |
| Federation | Cross-service, cross-department, or cross-deployment A2A collaboration. | `agent-bus` + `agent-service` relationship owner | Same-service child work | accepted_direction |
| Task/Run tree | Parent-child execution relationship used to trace delegation, join, failure, and cost attribution. | `agent-service` + observability | Single trace span | pending_vocabulary |
| Data reference path | Large or sensitive payload path where control messages carry URI/object reference/metadata and data is fetched by authorized consumers. | external storage owner + bus envelope metadata | Bus payload transport | accepted_direction |
| Service SSE stream | `agent-service` realtime external output surface. | `agent-service` | Bus token event stream | candidate_promote |
| Tenant Vertical | Cross-cutting tenant identity propagation and isolation concern. | platform runtime | Per-module tenant reinvention | accepted |
| Posture Vertical | Cross-cutting dev/research/prod behavior and fail-closed startup concern. | platform runtime | Runtime feature flag | accepted |
| Telemetry Vertical | Cross-cutting trace/span/event/LLM call/cost evidence concern. | platform observability | Provider-local logging | accepted |
| TraceContext | Runtime telemetry carrier companion to runtime context. | bus/service runtime SPI per accepted placement | HTTP-only header | accepted_current |
| Audit record | Append-only platform evidence for important runtime decisions and side effects. | platform audit writer | Business record | accepted_direction |
| LLM cost attribution | Platform aggregation of token usage, model route, and model-call cost by tenant/app/agent/tree dimensions. | observability + governance | Customer internal tool cost | candidate_promote |
| Platform-hosted service | Platform-managed runtime for weak department/PaaS tenants. | platform operations | Business-owned service | candidate_promote |
| Business-centric deployment | Deployment where business side may host client, service, and engine while platform keeps shared bus/middleware/federation governance. | deployment architecture | New module boundary | candidate_promote |
| Hybrid capability placement | One business activity uses both local and platform capabilities. | capability placement | Single deployment mode | candidate_promote |
| Replay-safe fixture | Sanitized evidence fixture for reproducing behavior without leaking tenant/business data. | harness + observability governance | Production data backup | candidate_promote |
| Invariant | Checkable architecture rule. | L0 constraints and verification | Slogan | accepted |
| Harness | Mocks, stubs, fixtures, contract tests, scenario assertions, and failure injection used to drive development and validation. | verification/scope system | Production implementation | candidate_promote |
| `draft` | Work material that is not accepted architecture truth. | draft docs | Accepted or shipped | accepted |
| `design_only` | Shape exists but runtime enforcement is not present. | ADR/contract/status ledgers | Shipped | accepted |
| `accepted` | Architecture decision or design fact accepted by governance, even if not shipped. | ADRs/workspace | Runtime enforced | accepted |
| `shipped` | Runtime behavior or artifact exists and is verified by current evidence. | code/tests/generated facts | Design-only | accepted |

## Forbidden Conflations

- Do not treat Task and Run as synonyms until the Run/Task decision is resolved.
- Do not treat client invocation as a second server-side lifecycle state.
- Do not treat Context Engine or Tool Gateway as independent modules.
- Do not treat Gateway, Bus, and service SSE as one communication channel.
- Do not treat A2A control messages as large-payload or token-stream transport.
- Do not treat business state as platform runtime state.
- Do not treat draft ICD/YAML material as accepted contract authority.
- Do not treat version scope scenarios as architecture truth unless promoted.
