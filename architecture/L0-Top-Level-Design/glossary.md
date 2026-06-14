---
level: L0-TLD
TAG:
  - glossary
  - vocabulary
  - logical-view
  - forbidden-conflations
  - architecture-fact
status: 架构事实
dependency:
  - README.md
  - overview.md
  - boundaries.md
  - constraints.md
---

# L0 Glossary

## Purpose

This glossary constrains shared project vocabulary. It prevents architecture
authors, AI agents, and module owners from using similar names for different
layers, states, or responsibilities.

When a term is defined by an accepted ADR, module metadata, architecture fact,
code fact, or accepted contract, that source wins. This glossary records the
current L0 reading and flags pending vocabulary decisions.

## Terms

| Term | Meaning | Owner / Home | Do Not Confuse With | Status |
|---|---|---|---|---|
| Architecture fact system | L0/L1/L2 + 4+1 architecture facts used to guide and constrain architecture. | `architecture/` | Version scope backlog | accepted |
| Version scope system | Requirements, business scenarios, feature use cases, function points, delivery slices, and acceptance scope for a release. | `version-scope/` | Architecture fact system | accepted_direction |
| openJiuwen implementation project | Future official community project that implements one or more L0 logical modules. For this architecture, openJiuwen `agent-runtime-java` maps to `agent-runtime`, and openJiuwen `agent-core-java` maps to `agent-core`. | openJiuwen community | L0 logical module name | accepted |
| Task | Unified server-side authoritative execution lifecycle state for V1. It aligns with A2A protocol task semantics and can be created or bound by a client-to-server request or by an `agent-runtime` instance A2A/federation request to another `agent-runtime` instance. | `agent-runtime` instance | Session, Memory, client invocation, engine-internal execution state | accepted |
| Run | Historical or implementation-compatibility term for execution/invocation vocabulary. It is not the V1 L0 canonical server-side lifecycle state and must not introduce a second state owner. | Archived docs or implementation history | Task, Session, business order | compatibility_only |
| Client invocation | Client-side call reference or SDK invocation handle that may map to a server-side Task. | `agent-client` + `agent-runtime` query surface | Independent server lifecycle state | accepted |
| TaskStateStore / Historical RunRepository | Controlled read/write entry for Task lifecycle state. If current code or historical documents still use names such as `RunRepository`, read them as implementation-compatible names for the Task owner path, not as a second Run state owner. | `agent-runtime` Task lifecycle owner | Generic DAO, arbitrary state writer, independent Run state model | compatibility_only |
| Session | Context state for conversation, variables, and context projection continuity. | `agent-runtime` session boundary | Task lifecycle, Memory | accepted_direction |
| Memory | Knowledge or experience state exposed through memory SPI or external memory adapters. | `agent-middleware` and configured memory providers | Session temporary context | accepted_direction |
| Checkpoint | Resume/recovery payload saved before suspend or long-horizon interruption. | Checkpointer SPI / runtime owner | Business state snapshot | accepted_direction |
| Agent | Registered entity binding model, skills, memory, planner, prompt, and advisors for execution. | `agent-runtime` agent SPI | Orchestrator | accepted_direction |
| Orchestrator | Runtime component that dispatches work, handles suspend/resume, and emits execution/state intent. | `agent-core` for Task execution; `agent-runtime` for Task lifecycle coordination | Lifecycle state owner | accepted_direction |
| Engine-internal execution state | Finer-grained execution state below the Task lifecycle boundary, such as workflow node execution state or ReAct loop state. | `agent-core` | Task lifecycle state | accepted |
| Execution Engine SPI | Service-to-engine invocation boundary used by `agent-runtime` to ask `agent-core` to execute a Task and return execution results, suspend requests, tool intents, context requests, child-work intents, or terminal results. | `agent-core` | Bus control, model gateway SPI, lifecycle state writer | accepted |
| Official execution engine | The project-owned openJiuwen execution engine implementation behind the `agent-core` boundary. | `agent-core` | Heterogeneous execution engine adapter | accepted |
| Heterogeneous execution engine | A non-openJiuwen agent framework implementation adapted to the Execution Engine SPI, including workflow-style and agent-loop-style frameworks. | `agent-core` adapter domain | Independent lifecycle owner or service core dependency | accepted |
| Engine adapter | Anti-corruption adapter that translates a heterogeneous execution engine to the Execution Engine SPI. The service provides extension and assembly entry points, while framework-specific translation belongs to the execution-engine adapter domain. | `agent-core` adapter domain + `agent-runtime` extension assembly | Service-owned framework implementation, bus control | accepted |
| Heterogeneous framework compatibility | Official openJiuwen execution and heterogeneous framework execution can both participate through the `agent-core` boundary without rewriting Task lifecycle ownership or forcing changes to already-running agent implementations. | `agent-core` + `agent-runtime` extension assembly | Closed single-framework runtime or lifecycle-owner rewrite | accepted |
| RuntimeMiddleware | Cross-cutting middleware hook listener and dispatch surface. | `agent-middleware` | Provider implementation | accepted_current |
| ModelGateway | Platform model invocation boundary. | `agent-middleware` model SPI | Direct Spring AI `ChatModel` use | accepted_direction |
| Skill | Governed tool/skill execution unit. | `agent-middleware` skill SPI | Ungoverned business function call | accepted_direction |
| Tool Gateway | Capability aggregate for skill authorization, capacity, audit, idempotency, and tool-call governance. | `agent-middleware` + `agent-runtime` integration | Independent reactor module | candidate_promote |
| Context Engine | Capability aggregate for session, context projection, memory, retrieval, vector, and context package assembly. | `agent-runtime` + `agent-middleware` | Independent reactor module | candidate_promote |
| Platform Gateway | Platform-level ingress governance capability for authentication pre-check, tenant routing, cross-service routing, traffic governance, A2A/S2C ingress, and permission mediation. It may be realized as an L1/L2 runtime unit under `agent-bus`. | `agent-bus` L1/L2 candidate | Service Task API, service stream, business orchestration | accepted_direction |
| Service Task API | Service-owned create task, query task, stream task, cancel task, and related Task lifecycle HTTP/API surfaces. | `agent-runtime` | Platform Gateway, bus event channel, engine pull queue | accepted |
| Agent Bus | Broad platform interaction governance domain for S2C, A2A/federation, routing, permission mediation, rhythm, data-reference envelopes, and narrower event/control transport units. It is not synonymous with a single MQ or event bus implementation. | `agent-bus` | Narrow event bus, service SSE stream, gateway ingress | accepted |
| Event/control channel | Narrow transport or signaling channel under the `agent-bus` domain, possibly backed by MQ or another messaging middleware. It carries control commands, references, routing metadata, and rhythm signals, not large object bodies or token-by-token external streams. | `agent-bus` L1/L2 runtime unit | Broad Agent Bus domain, data path, service stream | accepted |
| Integrating developer | Direct platform user who integrates `agent-client`, defines agents, connects business tools, and owns application rollout outcomes inside a business system. | business application team | End business user, platform-internal module owner | accepted_direction |
| C-Side | Business application/client side that owns business goals, rules, facts, local tools, local context, and authorization references. | business application side | Platform runtime state | accepted_direction |
| S-Side | Platform runtime side that owns execution trajectory, governance, observability, audit, capacity, and platform middleware. | platform runtime side | Business facts owner | accepted_direction |
| Digital employee application | Enterprise application pattern where an agent performs long-horizon work under business-owned goals, permissions, and approval rules. | business application + platform runtime | Platform-owned business process | candidate_promote |
| Capability placement | Decision of where a tool, context, memory, retriever, approval UI, adapter, or A2A action executes and which data boundary it crosses. | architecture + contracts | Module placement only | candidate_promote |
| Client-mediated local capability | Capability placement pattern where the platform uses S2C/Yield handoff to ask the client side to execute sensitive tools, local context, local retrieval, local memory, or approval UI, then returns a governed result. | `agent-client` endpoint + `agent-bus` S2C + service integration | Platform directly reading core business data, ungoverned client callback | candidate_promote |
| Local capability | Capability executed on the business/client side, such as local tool, local context, local memory, local retriever, or approval UI. | `agent-client` endpoint | Platform-hosted capability | candidate_promote |
| S2C callback | Server-to-client callback or handoff for local capability, approval, or external input. | `agent-bus` S2C + `agent-client` endpoint | A2A federation | accepted_direction |
| A2A control command | Agent-to-Agent control instruction for child work, federation, completion, failure, timeout, or join. | `agent-bus` for cross-instance or cross-boundary control; `agent-runtime` instance for same-instance relationship | Large data payload or token stream | accepted_direction |
| Federation | Cross-instance, cross-department, cross-deployment, or cross-trust-boundary A2A collaboration. | `agent-bus` + local and remote `agent-runtime` relationship owners | Same-instance child work | accepted_direction |
| Task tree | Parent-child execution relationship used to trace delegation, join, failure, and cost attribution. Same-instance child work is owned by the local `agent-runtime` instance; federated child references are mediated through bus/federation control and owned by the participating service instances. | `agent-runtime` instance + observability | Single trace span, engine-internal state, or bus-owned lifecycle | accepted |
| Rhythm signal | Timing, wakeup, retry, timeout, or schedule signal used for cross-instance or cross-boundary coordination. It may be governed or routed by `agent-bus`, but Task-level suspend/resume state remains owned by the relevant `agent-runtime` instance. | `agent-bus` governance + `agent-runtime` Task owner | Bus-owned Task sleep state, engine pull loop | accepted_direction |
| Data reference path | Large or sensitive payload path where control messages carry URI/object reference/metadata and data is fetched by authorized consumers. `agent-bus` may govern the reference envelope and permission handoff, while the data body stays outside narrow event/control channels. | external storage owner + `agent-bus` envelope governance | Event/control channel payload transport | accepted_direction |
| Service SSE stream | `agent-runtime` realtime external output surface. Concrete stream technologies belong below L0, but the L0 boundary treats external realtime content streams as service surfaces by default. | `agent-runtime` | Event/control channel token stream | accepted_direction |
| Customer auth reference | Authorization reference from a customer-owned identity or permission system that the platform may use to access customer data sources without owning or redefining the customer's fine-grained business permission model. | C-Side authorization owner + service/middleware integration | Platform-owned business permission model, copied customer credentials | candidate_promote |
| Tenant Vertical | Cross-cutting tenant identity propagation and isolation concern. | platform runtime | Per-module tenant reinvention | accepted |
| Posture Vertical | Cross-cutting dev/research/prod behavior and fail-closed startup concern. | platform runtime | Runtime feature flag | accepted |
| Telemetry Vertical | Cross-cutting trace/span/event/LLM call/cost evidence concern. | platform observability | Provider-local logging | accepted |
| TraceContext | Runtime telemetry carrier companion to runtime context. | bus/service runtime SPI per accepted placement | HTTP-only header | accepted_current |
| Audit record | Append-only platform evidence for important runtime decisions and side effects. | platform audit writer | Business record | accepted_direction |
| LLM cost attribution | Platform aggregation of token usage, model route, and model-call cost by tenant/app/agent/tree dimensions. | observability + governance | Customer internal tool cost | candidate_promote |
| Platform-hosted service | Platform-managed runtime for weak department/PaaS tenants. | platform operations | Business-owned service | candidate_promote |
| Business-centric deployment | Deployment where the business side may host `agent-client`, `agent-runtime`, and `agent-core`, while the platform may retain shared bus, middleware, and federation governance. | deployment architecture | New module boundary | candidate_promote |
| Hybrid capability placement | One business activity uses both local and platform capabilities. | capability placement | Single deployment mode | candidate_promote |
| Development-time debug evidence | Evidence used by an integrating developer or module owner to understand one agent execution path, including Task timeline, context evidence, model evidence, tool decision evidence, and failure evidence. | observability + harness + `agent-runtime` query surface | Platform-only logs, runtime operations aggregate | candidate_promote |
| Runtime operations insight | Operational and business-operations evidence for running agent capabilities, including request volume, success rate, latency, cost, errors, capacity, trace, and audit dimensions. | observability capability | Single execution debug log, customer-owned business state | candidate_promote |
| Replay-safe fixture | Sanitized evidence fixture for reproducing behavior without leaking tenant/business data. | harness + observability governance | Production data backup | candidate_promote |
| Scenario spec | Scenario description that distinguishes BA-* business activity scenarios from technical sub-scenarios and records expected assertions before promotion into architecture facts or version scope. | version scope system + architecture stress scenarios | Flow diagram only, accepted architecture truth before promotion | candidate_promote |
| Evolution data flywheel | Governed export and analysis loop for runtime evidence, scoring, learning, and optimization outside the main request path. | `agent-evolve` candidate boundary | Synchronous online execution dependency | candidate_promote |
| Invariant | Checkable architecture rule. | L0 constraints and verification | Slogan | accepted |
| Harness | Mocks, stubs, fixtures, contract tests, scenario assertions, and failure injection used to drive development and validation. | verification/scope system | Production implementation | candidate_promote |
| `draft` | Work material that is not accepted architecture truth. | draft docs | Accepted or shipped | accepted |
| `design_only` | Shape exists but runtime enforcement is not present. | ADR/contract/status ledgers | Shipped | accepted |
| `accepted` | Architecture decision or design fact accepted by governance, even if not shipped. | ADRs/workspace | Runtime enforced | accepted |
| `shipped` | Runtime behavior or artifact exists and is verified by current evidence. | code/tests/generated facts | Design-only | accepted |

## Forbidden Conflations

- Do not treat Run as canonical V1 lifecycle vocabulary; use Task for
  server-side execution lifecycle state.
- Do not treat openJiuwen implementation project names such as
  `agent-runtime-java` or `agent-core-java` as replacements for L0 logical module
  names.
- Do not treat client invocation as a second server-side lifecycle state.
- Do not treat Context Engine or Tool Gateway as independent modules.
- Do not treat Platform Gateway, Service Task API, broad Agent Bus, narrow
  event/control channels, and service SSE as one communication channel.
- Do not let `agent-core` directly pull Tasks from bus, broker, or
  external queues; Task dispatch enters through `agent-runtime`.
- Do not treat A2A control messages or narrow event/control channels as
  large-payload or token-stream transport.
- Do not treat business state as platform runtime state.
- Do not treat draft ICD/YAML material as accepted contract authority.
- Do not treat version scope scenarios as architecture truth unless promoted.
