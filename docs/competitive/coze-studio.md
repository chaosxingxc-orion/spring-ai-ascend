---
analysis_id: COMPETITIVE-COZE-STUDIO
governance_infra: true
analysis_date: 2026-05-28
analyst: spring-ai-ascend Phase C Tranche 1
repo_clone_at: D:\ai-research\agent-platforms-survey\coze-studio\
---

# Competitive Analysis: coze-dev/coze-studio

Source-grounded analysis at commit `22275b1` (2026-04-20, tip of
`main`), Go module `github.com/coze-dev/coze-studio/backend` declared
in `backend/go.mod:1`. Coze Studio is the **open-source surface** of
ByteDance's commercial Coze platform; the README is explicit that the
open-source build derives from "the Coze Development Platform which
has served tens of thousands of enterprises and millions of
developers" with its "core engine completely open" (`README.md:25`).
What's NOT in the open-source build matters as much as what is.

## 1. Tagline & positioning

The repository's own pitch, verbatim from `README.md:20`:

> "Coze Studio is an all-in-one AI agent development tool. Providing
> the latest large models and tools, various development modes and
> frameworks, Coze Studio offers the most convenient AI agent
> development environment, from development to deployment."

And from `README.md:27`:

> "The backend of Coze Studio is developed using Golang, the frontend
> uses React + TypeScript, and the overall architecture is based on
> microservices and built following domain-driven design (DDD)
> principles."

The feature matrix (`README.md:29-36`) emphasises **visual no-code /
low-code** development: visual canvas workflow builder, plugin
marketplace, knowledge base, prompts library, model service
integrations, Chat SDK for embedding agents into third-party apps,
and an OpenAPI surface for programmatic access. The deployment shape
is **one-command Docker Compose** to `localhost:8888` (`README.md:53-
68`), with a security warning at line 70 explicitly flagging public-
network risks ("account registration, Python execution in workflow
code nodes, server listening address, SSRF, horizontal privilege
escalations"). The framing is **product-first**, **visual-canvas-
centric**, and aimed at **enterprises that want to self-host a Coze-
like SaaS**. Positioning vs spring-ai-ascend: Coze Studio is a
**complete product** with a visual front-end (`frontend/` is a full
Rush monorepo with `apps/`, `packages/`, `infra/` — `frontend/`
listing); spring-ai-ascend is a **library/BoM** with no shipped UI.
The closest analogue is "Dify open-source" — both target the
"workspace-style SaaS app you can self-host" market.

## 2. Architecture skeleton

The repository is a polyrepo-style monorepo with three top-level
languages: Go backend, React/TS frontend, Thrift IDL. The backend
layout (`backend/`) follows DDD:

```
backend/
  main.go                      # hertz HTTP entrypoint (port :8888)
  api/                         # generated handler + router + middleware
    handler/, router/, model/  # hertz-generator output
    middleware/                # session, host, log, openapi_auth, i18n
  application/                 # use-case layer (one dir per domain)
    {app,agent,workflow,conversation,knowledge,memory,...}
    application.go             # global Init()
  domain/                      # DDD core (entity, repository, service)
    agent/singleagent/         # ReAct single-agent
    workflow/                  # visual workflow engine
      internal/{canvas,compose,execute,nodes,repo,schema}
      internal/nodes/          # 17 node packages: llm, code, qa, ...
    knowledge/, memory/, plugin/, conversation/, ...
  crossdomain/                 # explicit cross-domain contracts
  infra/                       # cache, checkpoint, coderunner, eventbus,
                               # idgen, imagex, oceanbase, orm, rdb,
                               # sqlparser, sse, storage, es, embedding
  bizpkg/                      # shared biz utilities (llm/modelbuilder)
  helm/charts/opencoze/        # Kubernetes deployment chart
  docker/                      # docker-compose.yml, nginx, volumes
  idl/                         # Thrift service definitions
```

Total code surface: **1013 `.go` files** under `backend/` (verified by
`find -name "*.go" | wc -l`). Frontend is a Rush + pnpm monorepo with
`apps/`, `packages/`, `infra/`, `scripts/`. The agent runtime is built
on **CloudWeGo's `eino`** library (`backend/go.mod:24` requires
`github.com/cloudwego/eino v0.4.8`), specifically `eino/flow/agent/
react` — Coze Studio does NOT write its own ReAct loop, it composes
one via `eino` (`backend/domain/agent/singleagent/internal/agentflow/
agent_flow_builder.go:25-34`).

**Counterpart mapping to spring-ai-ascend**:

| spring-ai-ascend                  | Coze Studio counterpart                                | Notes |
|-----------------------------------|--------------------------------------------------------|-------|
| `agent-service`                   | `backend/domain/{agent,workflow,conversation}` + `application/*` | Run = `WorkflowExecution`, multi-execution-mode |
| `agent-bus`                       | `backend/infra/eventbus` (Sarama Kafka)                | Real bus, but no channel-isolation discipline |
| `agent-execution-engine`          | `backend/domain/workflow/internal/{compose,execute,nodes}` + `eino` | 17 node types, eino-driven |
| `agent-middleware`                | `backend/infra/{cache,checkpoint,orm,rdb,storage,...}` | 16 infra sub-modules |
| `agent-client`                    | `frontend/` (Rush monorepo, Next.js apps)              | Coze ships the UI; we don't |
| `agent-evolve`                    | (none — no evolution plane, no trajectory store)       | Out of scope |
| `spring-ai-ascend-graphmemory-starter` | `backend/domain/{knowledge,memory}` (RAG + chat memory) | Knowledge = vector RAG only |

The architectural feature absent from the closest competitors (SAA,
AgentScope) is **eventbus**: `backend/infra/eventbus/` declares an
abstraction over Kafka (Sarama) and NSQ, giving Coze a real
producer/consumer surface. But this is a single-channel bus, not a
three-track control/data/rhythm split.

## 3. Developer experience

The README's canonical quickstart (`README.md:46-66`) is **Docker
Compose** — not a code-first SDK:

```
git clone https://github.com/coze-dev/coze-studio.git
cd coze-studio
make web                                  # macOS/Linux
# or: cp .env.example .env && docker compose up
# → register account at localhost:8888/sign
# → configure model at localhost:8888/admin/#model-management
# → visit localhost:8888/
```

There is **no "write a `main.go` to build your first agent" path**;
the supported flow is "click through the visual canvas in the
browser". For programmatic access, the README points to **Chat SDK**
(JavaScript embed) and an **OpenAPI surface** with Personal Access
Token authentication (`README.md:79`). The OpenAPI surface is
generated from `idl/app/bot_open_api.thrift` and similar files under
`idl/`. The IDL covers `passport/`, `permission/`, `playground/`,
`plugin/`, `resource/`, `upload/`, `workflow/`, plus the canonical
`api.thrift` umbrella (`idl/` listing).

Onboarding requirements: Docker, Docker Compose, 2 cores / 4 GB RAM
(`README.md:43`). The runtime surface for a developer is:

1. **Web UI workflow canvas** (`frontend/apps/`) — drag-and-drop
   nodes from the 17-node catalogue (`backend/domain/workflow/
   internal/nodes/`: `batch`, `code`, `conversation`, `database`,
   `emitter`, `entry`, `exit`, `httprequester`, `intentdetector`,
   `json`, `knowledge`, `llm`, `loop`, `plugin`, `qa`, `receiver`,
   `selector`, `subworkflow`, `textprocessor`).
2. **Plugin authoring** for custom tools.
3. **Knowledge base** ingestion through Web UI.
4. **OpenAPI** for inference invocation.

Compared to spring-ai-ascend's library/BoM model, Coze Studio
optimises for the **product user** (build a chatbot via canvas),
not the **library consumer** (embed agent runtime in your Spring
service). The two projects don't compete at the developer-experience
layer because they target different developer personas.

## 4. Multi-tenancy & governance

Coze Studio has a **`SpaceID`-based tenant model** that maps cleanly
to "workspaces" in the commercial Coze product. The generated GORM
model `backend/domain/workflow/internal/repo/dal/model/workflow_
execution.gen.go:14` declares:

> `SpaceID int64 \`gorm:"column:space_id;not null;comment:the space id
> the workflow belongs to"\``

`grep -r "SpaceID" backend/domain/` returns hits across 15+ files
(workflow service, conversation, agent duplicate, etc.). The `User`
entity (`backend/domain/agent/singleagent/entity/single_agent.go:34-
39`) declares `DuplicateInfo { UserID, SpaceID, NewAgentID,
DraftAgent }` — operations are scoped by `(UserID, SpaceID)` tuple.
The IDL `idl/permission/` declares an explicit `openapiauth` service
with `openapiauth.thrift` + `openapiauth_service.thrift` — Personal
Access Token authorization is a real architectural surface, not just
a middleware afterthought.

The session middleware (`backend/api/middleware/session.go:42-60`)
gates every WebAPI request by a session cookie (`entity.SessionKey`)
with `noNeedSessionCheckPath` carving out only `/api/passport/web/
email/{login,register}` paths. Returning 401 on missing session is
fail-closed. Authentication is local-account (registration form), not
SSO/OIDC out of the box.

What's **NOT** there:

- **No PostgreSQL Row-Level Security** — the storage layer uses
  MySQL via GORM (`backend/infra/orm/`) with `gorm:"column:space_id;
  not null"` as the tenant scoping mechanism. Tenant isolation is at
  the **query-builder layer**, not the **storage engine** — every
  service must remember to add the `WHERE space_id = ?` clause. Any
  forgotten predicate is a horizontal-privilege-escalation hole, which
  is exactly what the README warns about at `README.md:70`.
- **No posture-aware fail-closed startup** — the `.env.example` is the
  single configuration surface, no `dev`/`research`/`prod` posture
  split.
- **No idempotency spine** — `WorkflowExecution` has `commit_id`
  (draft snapshot) but no idempotency key.

Compared to spring-ai-ascend's three-layer isolation (storage RLS at
the migration level, HTTP edge re-validation, Run record contract),
Coze's `SpaceID`-as-column is the same shape as **early SAA work**
plus discipline — it works in practice but offers no enforcement
mechanism.

## 5. Engine pluggability

The engine layer is **bicephalous**: a *single-agent* path and a
*workflow* path, both built on CloudWeGo `eino`.

- **Single agent** (`backend/domain/agent/singleagent/internal/
  agentflow/agent_flow_builder.go:60-100`) composes an `eino`
  ReAct agent via `compose.NewGraph` with nodes
  `keyOfPersonRender → keyOfKnowledgeRetriever →
  keyOfPromptVariables → keyOfPromptTemplate → keyOfReActAgent`
  (lines 47-58). The `keyOfReActAgent` uses
  `eino/flow/agent/react` directly.
- **Workflow** (`backend/domain/workflow/internal/`) is the
  visual-canvas executor. The canvas DSL is a graph of typed nodes
  (17 types under `internal/nodes/`); compilation goes through
  `internal/compose/` (uses `eino`'s `compose` package) and
  execution is in `internal/execute/`.

Engine selection is **structural**, not declarative — to add a
non-eino executor you'd write parallel `internal/{compose,execute}/`
trees. There is no envelope, no `engine_type` discriminator on a
persistent record, and no registry SPI. The execution mode field on
`WorkflowExecution.Mode int32` (`backend/domain/workflow/internal/
repo/dal/model/workflow_execution.gen.go:16`) carries values "1.
debug run 2. release run 3. node debug" — i.e., **execution intent**,
not engine selection.

Where Coze does shine is **execution observability**: the same
record carries `Input`, `Output`, `ErrorCode`, `FailReason`,
`InputTokens`, `OutputTokens`, `Duration`, `Status` (1=running
2=success 3=fail 4=interrupted), `RootExecutionID`, `ParentNodeID`,
`AgentID`, `SyncPattern` (1=sync 2=async 3=stream), and
`ResumeEventID` (`workflow_execution.gen.go:11-37`). That schema is
richer than spring-ai-ascend's current `Run` entity — particularly
the `RootExecutionID` parent-child link for nested sub-workflow
executions and the `ResumeEventID` for interrupt/resume.

The model layer pluggability is broad: `backend/go.mod:24-43`
declares `eino-ext` adapters for Ark (Volcengine), Claude, Deepseek,
Gemini, Ollama, OpenAI — the same multi-provider story as
spring-ai-ascend, but Volcengine-defaulted (Bytedance's cloud) rather
than Ascend-defaulted.

## 6. Evolution substrate

Coze Studio has **no evolution plane**. The product surface is
**RAG-based knowledge** + **chat memory**, not trajectory-driven
finetuning:

- **Knowledge** (`backend/domain/knowledge/`): document ingestion,
  chunking, embedding, retrieval via Elasticsearch / Milvus
  (`helm/charts/opencoze/templates/{elasticsearch,milvus}-*.yaml`).
- **Memory** (`backend/domain/memory/`): chat history + variable
  store.
- **Database** (`backend/domain/workflow/internal/nodes/database/`):
  workflow-accessible structured store.

Embedding adapters are provided for **Ark, Gemini, Ollama, OpenAI**
(`backend/go.mod:26-29`). Vector storage uses **Milvus** in
production (`helm/charts/opencoze/templates/milvus-statefulset.yaml`)
or local equivalents.

A repository-wide grep for `trajectory|finetune|grpo|dpo` returns
no business hits — the platform consumes models, it does not train
them. The closest analogue to spring-ai-ascend's `EvolutionExport`
event-scope discriminator (Rule R-M.e) is the workflow execution log,
but it is not designed as a trajectory-export interface — its purpose
is debug-trace + billing-attribution.

The **plugin / tool ecosystem** is the closest Coze comes to an
evolution surface: plugins author tools that the agent can call, and
the `plugin/` IDL declares a marketplace contract. But this is
**capability expansion**, not **agent self-improvement**.

For PC-005 (graph-memory evolution claim), Coze Studio is **not** a
direct competitor — its knowledge layer is vector-RAG-only, no
property-graph backbone, no triple store. The next analysis (OpenSPG)
is the relevant comparator for that dimension.

## 7. Deployment model + sovereign-hardware support

Coze Studio ships **two deployment paths**, both first-class:

1. **Docker Compose** for single-host (`docker/docker-compose.yml`
   + `docker/docker-compose-debug.yml` + `docker/docker-compose-
   oceanbase.yml` for OceanBase variant). Listed services include
   `coze-server`, MySQL, Elasticsearch, Milvus, MinIO, etcd, Nginx.
2. **Helm chart** for Kubernetes (`helm/charts/opencoze/Chart.yaml`
   + 13+ template files including `deployment.yaml`,
   `elasticsearch-statefulset.yaml`, `milvus-statefulset.yaml`,
   `minio-statefulset.yaml`, `etcd-statefulset.yaml`,
   `coze-web-deployment.yaml`, `coze-web-service.yaml`,
   `coze-web-configmap.yaml`). This is a **complete, opinionated**
   K8s deployment — the cluster runs MySQL, ES, Milvus, MinIO, etcd
   in-cluster as StatefulSets.

The Helm chart includes a `push_images_to_volcengine.sh` script
(`helm/charts/opencoze/push_images_to_volcengine.sh`) — Volcengine
(Bytedance's cloud) is the documented private-image-registry target,
echoing the model-provider default of Ark/Volcengine. The default
model template is `model_template_ark.yaml` (`helm/charts/opencoze/
files/conf/model_template_ark.yaml`).

**No Chinese-silicon support.** A repository-wide grep for
`Ascend|Kunpeng|昇腾|鲲鹏` returns hits only in the frontend's `sort
ascend` icon (`frontend/packages/components/bot-icons/src/index.tsx:
243`) and an unrelated i18n entry — never as runtime hardware
support. The base Docker images are generic Linux (Go binary +
JavaScript). The platform deployment topology is **cloud-cluster-
native**, not **NPU/ARM-aware**.

For spring-ai-ascend, Coze Studio's deployment shape is instructive
in two ways: (a) shipping a Helm chart with the database/index
services as in-cluster StatefulSets is a real product expectation we
should answer with our own chart; (b) the Volcengine private-image-
registry script is a clear signal that the Bytedance cloud is the
intended deployment target, mirroring SAA's DashScope default and
AgentScope's DashScope default.

## 8. License + corporate sponsor

License: **Apache 2.0** (`LICENSE-APACHE`). The repo's
`README.md:96-97` reconfirms: "This project uses the Apache 2.0
license." Every Go file carries the boilerplate copyright header
"Copyright 2025 coze-dev Authors" (e.g., `backend/main.go:1-15`).

Corporate sponsor: **ByteDance** (the `coze-dev` GitHub org).
Security reporting points to `security.bytedance.com/src` and
`sec@bytedance.com` (`README.md:101`). The Volcengine private-image-
registry script and the Ark (Volcengine) model template defaults
confirm Bytedance's cloud is the gravity well. The commercial Coze
product (`coze.cn`, `coze.com`) is referenced throughout the README
as the source for documentation (`README.md:85-95`); the README is
explicit that "certain features, such as tone customization, are
limited to the commercial version" (line 86).

Latest commit on `main`: `22275b1c2661d35344a7493cffe401e8cc61cf8e`
dated **2026-04-20** ("fix(plugin): oauth phishing (#2668)") — the
commit message itself indicates active security maintenance.

For spring-ai-ascend, the same competitive posture applies as to SAA
and AgentScope: Coze Studio is a commercial product backed by a
hyperscale Chinese cloud, with the open-source build positioned as
on-ramp marketing for the SaaS. Our positioning (Ascend/Kunpeng
sovereignty, library/BoM model, no SaaS analogue) does not directly
compete — Coze Studio is "open-source Dify-style canvas SaaS",
spring-ai-ascend is "audit-grade SPI runtime on sovereign silicon".

## 9. What we LEARN

Patterns worth absorbing into spring-ai-ascend:

1. **Workflow execution schema with rich observability fields** —
   `backend/domain/workflow/internal/repo/dal/model/workflow_
   execution.gen.go:11-37` declares 25 fields including
   `RootExecutionID` (parent-child link for nested sub-workflow),
   `ResumeEventID` (interrupt/resume support), `SyncPattern` (sync/
   async/stream tri-state), `Mode` (debug/release/node-debug),
   `CommitID` (draft snapshot pin), and the tuple
   `(InputTokens, OutputTokens, Duration, FailReason)`. Our `Run`
   entity should grow at least `RootRunId` (nested sub-Run link) and
   `ResumeEventId` (for SuspendSignal-based resume) — these are
   structural, not feature, gaps.

2. **17-node visual workflow node catalogue as a starter library** —
   `backend/domain/workflow/internal/nodes/{batch, code,
   conversation, database, emitter, entry, exit, httprequester,
   intentdetector, json, knowledge, llm, loop, plugin, qa, receiver,
   selector, subworkflow, textprocessor}`. Spring AI Alibaba ships
   two such nodes (HttpNode, KnowledgeRetrievalNode); spring-ai-
   ascend ships zero. Even if our engine contract is envelope-based,
   shipping a canonical node library inside `agent-execution-engine`
   accelerates workflow adoption.

3. **CrossDomain explicit contract layer** — `backend/crossdomain/`
   declares ports between domains (e.g., `crossdomain/permission/
   contract.go`, `crossdomain/agent/model/`). This is a clean
   Hexagonal-style boundary that makes domain dependencies
   declarative. Our `spi/` packages are the conceptual analogue, but
   a top-level `crossdomain/` directory naming convention would make
   inter-module contract surfaces more discoverable.

4. **Plugin OAuth & marketplace contract** — `idl/plugin/` IDL +
   the security-fix `2026-04-20 fix(plugin): oauth phishing (#2668)`
   show the plugin authorization surface is mature. spring-ai-
   ascend's MCP integration is the closest analogue; the plugin-store
   schema in `idl/plugin/` is worth reading when we formalise our
   own tool-store contract.

5. **Helm chart with in-cluster StatefulSets for storage tier** —
   `helm/charts/opencoze/templates/{elasticsearch,milvus,minio,etcd}-
   statefulset.yaml` deploys the entire storage tier in one chart.
   Our `agent-middleware` should ship a sibling Helm chart for
   Postgres + Redis + (optional) Kafka, giving customers a one-line
   `helm install` path equivalent to Coze's `make web`.

6. **OpenAPI + Chat SDK as parallel access surfaces** — Coze exposes
   both an OpenAPI (REST + Personal Access Token) and a JavaScript
   embed SDK. spring-ai-ascend should make this distinction explicit
   in our agent-client module: an HTTP edge surface plus a JS embed
   SDK distinct from the Java in-process client.

7. **CloudWeGo `eino` as a battle-tested ReAct + Graph composer** —
   `backend/domain/agent/singleagent/internal/agentflow/agent_flow_
   builder.go:25-34` uses `eino/flow/agent/react` directly. `eino`
   is to Go what LangGraph is to Python; while we will not adopt the
   Go library, the **compose-graph + react-agent** pattern (named
   nodes wired with edges, with a ReAct loop as one composable node)
   is the right structural template for our dual-mode runtime.

## 10. Where we DIFFER

Each row cites one evidence file in **each** repo.

| # | Dimension | Coze Studio evidence | spring-ai-ascend evidence |
|---|-----------|----------------------|---------------------------|
| 1 | **Tenant isolation mechanism** — Coze: `space_id` GORM column; relies on every query carrying `WHERE space_id=?`. Ascend: RLS in Postgres migration + HTTP edge re-auth + Run record tenant_id NOT NULL. | `backend/domain/workflow/internal/repo/dal/model/workflow_execution.gen.go:14` (`space_id;not null` column only) | Rule R-J.a + `agent-service/.../service/runtime/runs/Run.java` |
| 2 | **Engine selection** — Coze: structural (eino single-agent vs eino workflow), no envelope. Ascend: declarative envelope + EngineRegistry. | `backend/domain/agent/singleagent/internal/agentflow/agent_flow_builder.go:60-100` (eino compose.NewGraph in code) | `D:\chao_workspace\spring-ai-ascend\docs\contracts\engine-envelope.v1.yaml` |
| 3 | **Evolution substrate** — Coze: knowledge/RAG + plugin marketplace, no trajectory or finetuning. Ascend: `agent-evolve` module on `evolution` deployment plane. | `grep -r "trajectory\|finetune" backend/` returns no business hits | `D:\chao_workspace\spring-ai-ascend\agent-evolve\module-metadata.yaml` |
| 4 | **Sovereign hardware** — Coze: Volcengine private registry + Ark model defaults + generic Linux containers. Ascend: Ascend NPU + Kunpeng ARM64. | `helm/charts/opencoze/push_images_to_volcengine.sh` + `helm/charts/opencoze/files/conf/model_template_ark.yaml` | `D:\chao_workspace\spring-ai-ascend\README.md:3-10` + Rule R-I five-plane manifest |
| 5 | **Posture-aware defaults** — Coze: `.env.example` only, no dev/research/prod split. Ascend: PostureBootGuard + per-knob posture defaults. | `docker/.env.example` referenced from `README.md:62` | `D:\chao_workspace\spring-ai-ascend\docs\governance\rules\rule-D-6.md` |
| 6 | **Run/Execution spine richness** — Coze: 25-field `WorkflowExecution` (RootExecutionID, ResumeEventID, SyncPattern) but no state machine validation. Ascend: `RunStateMachine.validate(from, to)` + idempotency. | `backend/domain/workflow/internal/repo/dal/model/workflow_execution.gen.go:11-37` (rich fields, no state machine call site) | Rule R-C.2.b + `RunStateMachine.java` |
| 7 | **Bus channel discipline** — Coze: Kafka/NSQ eventbus, single channel. Ascend: three physically-isolated channels (control/data/rhythm). | `backend/infra/eventbus/` (one channel) | Rule R-E + `docs/governance/bus-channels.yaml` |
| 8 | **Product vs library** — Coze: complete SaaS-style product with `frontend/` Rush monorepo + browser canvas. Ascend: BoM + SPI library, no shipped UI. | `frontend/apps/`, `frontend/packages/` + `helm/charts/opencoze/templates/coze-web-deployment.yaml` | `D:\chao_workspace\spring-ai-ascend\pom.xml` (no admin/frontend module) |
| 9 | **License + supplier posture** — Coze: ByteDance/Volcengine gravity well; the open-source build is on-ramp marketing for commercial Coze SaaS. Ascend: zero ByteDance dependency. | `README.md:25` ("derived from the Coze Development Platform") + Volcengine registry script | `feedback_saa_competitor.md` (project memory: hyperscale-Chinese-cloud-aligned artifacts are competitor surfaces) |
| 10 | **Governance / Code-as-Contract** — Coze: cspell + golangci-lint; no architectural enforcers, no ADRs, no governance ledger. Ascend: 144+ gate rules + ArchUnit. | `cspell.json` + `CONTRIBUTING.md` (no enforcer YAML, no ADR dir) | `D:\chao_workspace\spring-ai-ascend\CLAUDE.md` + `gate/check_architecture_sync.sh` |

These ten dimensions cleanly separate the two projects: Coze Studio
is a **complete self-hostable visual-canvas SaaS-shaped product**
backed by ByteDance/Volcengine; spring-ai-ascend is an **audit-grade
SPI library + BoM** on Ascend/Kunpeng. The architectural intent
overlap is narrow at the developer-persona layer (canvas-user vs
library-consumer), but the Run/Execution schema, the 17-node workflow
catalogue, and the Helm chart shape are real DX wins worth absorbing
into our middleware and engine modules.
