---
analysis_id: COMPETITIVE-FASTGPT
governance_infra: true
analysis_date: 2026-05-28
analyst: spring-ai-ascend Phase C Tranche 1
repo_clone_at: D:\ai-research\agent-platforms-survey\FastGPT\
---

# Competitive Analysis: labring/FastGPT

Source-grounded analysis of the `FastGPT` repository at clone HEAD, root
version `4.0` declared in `package.json:3`, app version `v4.14.12` declared
in `deploy/helm/fastgpt/Chart.yaml:25`. Conducted per the C.2 ten-point
template. FastGPT is the most visible RAG-first agent builder in the
Chinese market, sponsored by Sealos (the cloud-native infrastructure
company) and shipping under a modified Apache 2.0 license whose
multi-tenant SaaS restriction directly intersects spring-ai-ascend's
target market (`LICENSE:7-8`).

## 1. Tagline & positioning

The README's elevator pitch, verbatim from `README.md:16`:

> "FastGPT 是一个 AI Agent 构建平台，提供开箱即用的数据处理、模型调用等能力，
> 同时可以通过 Flow 可视化进行工作流编排，从而实现复杂的应用场景！"

Translated: "FastGPT is an AI Agent building platform, providing out-of-
the-box data processing and model invocation capabilities, while
supporting visual workflow orchestration via Flow to implement complex
application scenarios."

The core feature catalogue (`README.md:71-80`) emphasises four themes:
**application orchestration** (planning Agent mode, chat/plugin workflows,
RPA nodes, user interaction, bidirectional MCP), **application debugging**
(knowledge-base search testing, citation feedback, full call-trace logs),
**knowledge base** (dataset training, parent-child chunking, advanced
recall, multi-modal retrieval, image OCR), and **OpenAPI** (drop-in
replacement for OpenAI Chat Completions API). The deployment offerings
are tiered (`README.md:53-61`): a hosted **Cloud Service** at
`fastgpt.io`, a free **Community Self-Host Version** via Docker/Sealos,
and a **Commercial Version** with deeper functionality.

Positioning is **RAG-as-product**: knowledge bases are the centre of
gravity, with `dataset_service.py`-equivalents (in TypeScript:
`packages/service/core/dataset/`) carrying the most code mass. The
sponsor **Sealos** (`LICENSE:21`, copyright 2023) is itself a
Kubernetes-based cloud OS, signalling that FastGPT's natural deployment
target is K8s — and indeed FastGPT ships a first-class Helm chart at
`deploy/helm/fastgpt/Chart.yaml` (declared `version: 0.1.0`,
`appVersion: v4.14.12`) with MongoDB + PostgreSQL Bitnami chart
dependencies (`Chart.yaml:28-35`). The framing is **SMB self-host or
Sealos-hosted SaaS**, with explicit commercial-upgrade language for
enterprises.

## 2. Architecture skeleton

Top-level monorepo (pnpm + turbo, `turbo.json` + `pnpm-workspace.yaml`):

```
projects/
  app/                  # Next.js + Node app server (the FastGPT product)
    src/{components,pageComponents,pages/api,service,web,types,global}
  agent-sandbox/        # Sandbox for agent tool execution
  code-sandbox/         # Sandbox for arbitrary code (custom code node)
  marketplace/          # Plugin / template marketplace front-end
  mcp_server/           # MCP server
  volume-manager/       # Storage volume manager
packages/
  global/               # Shared types + constants (TS, no I/O)
  service/              # Server-side library (MongoDB + Postgres + AI)
    core/{ai,app,chat,dataset,plugin,workflow}/
    support/{user,permission,wallet,mcp,outLink,openapi,activity,
             appRegistration,tmpData}/
    common/, openapi/
  web/                  # Web-side library
  sdk/                  # Client SDKs
sdk/                    # Top-level SDK directory
test/, scripts/
deploy/
  dev/                  # Dev docker-compose
  helm/fastgpt/         # Production Helm chart
  k8s/, templates/      # K8s deployment manifests
  version/{main,v4.14}/ # Versioned docker-compose templates
pro/                    # Pro / commercial scripts (gitignored at clone)
```

The `packages/service/core/workflow/dispatch/` engine
(`index.ts:1699 lines`) is the heart of the workflow runtime. Dispatch is
organised by node category:

```
dispatch/
  ai/                # LLM dispatch (chat.ts, classifyQuestion.ts, extract.ts)
  child/             # Sub-app + tool nodes (runApp.ts, runTool.ts)
  dataset/           # Knowledge retrieval (search.ts, concat.ts)
  interactive/       # User-input prompts (formInput.ts, userSelect.ts)
  loop/              # Loop control (runLoopStart, runLoopEnd)
  loopRun/           # Loop body (runLoopRun*, parallel iteration)
  parallelRun/       # Parallel branch (runParallelRun)
  plugin/            # Plugin nodes (run, runInput, runOutput)
  tools/             # Built-in tools (answer.ts, codeSandbox.ts, …)
  init/              # Initialisation
  utils/             # Variables, abort tracking, tarjan SCC for cycle detection
  abandoned/         # Deprecated runApp + runLoop
```

The graph runtime is **not** LangChain-derived. FastGPT implements its own
DAG executor with **Tarjan SCC-based cycle handling**
(`packages/service/core/workflow/utils/tarjan.ts` referenced at
`dispatch/index.ts:61`) — a notable engineering investment. Nodes are
typed by `FlowNodeTypeEnum` (`packages/global/core/workflow/node/constant.ts`)
and dispatched through a single `callbackMap`
(`dispatch/index.ts:50`, defined in `dispatch/constants.ts`).

Persistence is dual-backend: **MongoDB** (via Mongoose, primary store
for app/dataset/chat metadata) + **PostgreSQL** (via raw `pg`/`drizzle`,
the vector store with `pgvector`). The dataset schema at
`packages/service/core/dataset/schema.ts:71-80` is the canonical example.

## 3. Developer experience

"Build my first agent" is **console-driven through Flow editor**, with
strong Docker-first onboarding (`README.md:39-50`):

1. `bash <(curl -fsSL https://doc.fastgpt.cn/deploy/install.sh)` —
   downloads the docker-compose template and stands up the stack.
2. `docker compose up -d` — brings up MongoDB, PostgreSQL+pgvector,
   FastGPT app, sandbox, mcp_server.
3. Browse to `http://localhost:3000`, login as `root` / `1234`
   (`README.md:48`).
4. In the console, create an **App** (`packages/service/core/app/`).
   FastGPT's app kinds (`packages/global/core/app/constants.ts` —
   `AppTypeEnum`) span `simple` (chatbot), `workflow` (Flow editor),
   `plugin` (callable plugin), `tool` (tool node), `httpPlugin`, and
   `mcpTool`. Each kind is a discriminator on the same `app` document.
5. For a **workflow**, the user enters the React Flow canvas. The node
   palette mirrors the dispatch subdirectories: `AI Chat`, `Knowledge
   Search`, `Question Classification`, `Content Extractor`, `HTTP
   Request`, `Custom Code`, `Variable Update`, `Conditional`,
   `Loop`, `Parallel`, `Plugin`, `Tool`, `User Form`, `User Select`,
   `Answer`. Loops and parallel branches are encoded as canvas topology
   via Tarjan-detected SCCs.
6. Workflow JSON is stored in the app document and re-materialised on
   each invocation. Sub-apps (apps invoked as tools) are supported via
   `dispatch/child/runApp.ts`.

A second path — **OpenAPI compatibility** (`packages/service/openapi/`,
plus the `OpenAPI` headline in `README.md:80`) — exposes every FastGPT app
as an OpenAI-Chat-Completions-compatible endpoint, so existing
OpenAI-SDK code can swap in FastGPT as a drop-in. This is a developer
ergonomics win for migration scenarios.

There is **no first-class TypeScript SDK for embedding the runtime**
in your own app; the runtime is server-resident. The `sdk/` directory
ships client SDKs to the OpenAPI surface, not embedding APIs.

## 4. Multi-tenancy & governance

FastGPT has a **first-class team aggregate** that is the load-bearing
unit of its multi-tenant model.

The team schema is declared at
`packages/service/support/user/team/teamSchema.ts:8-50`:

```typescript
const TeamSchema = new Schema({
  name: { type: String, required: true },
  ownerId: { type: Schema.Types.ObjectId, ref: userCollectionName },
  avatar: { type: String, default: '/icon/logo.svg' },
  balance: Number,
  teamDomain: { type: String },
  limit: { lastExportDatasetTime: ..., lastWebsiteSyncTime: ... },
  lafAccount: { token, appid, pat },  // Sealos LAF integration
  openaiAccount: { key, ... },         // per-team OpenAI key override
  ...
});
```

`TeamMemberSchema`
(`packages/service/support/user/team/teamMemberSchema.ts:13-55`) is the
many-to-many `(teamId, userId, status, role)` bridge with deprecated
`role` and `defaultTeam` fields preserved for back-compatibility. The
`teamId: ObjectId, ref: TeamCollectionName, required: true` field
propagates onto every domain document: `Dataset`
(`packages/service/core/dataset/schema.ts:71-80` — `teamId` and `tmbId`
both `required: true`), Collection, Data, App, Chat, Tag, Training. The
`teamId` is **required-not-null** on the dataset, which is structurally
stronger than Dify's "indexed-but-nullable" tenant column.

The permission system is comparatively rich: 12 sub-domains under
`packages/service/support/permission/`: `app`, `auth`, `dataset`,
`evaluation`, `inheritPermission.ts`, `mcp`, `memberGroup`, `model`,
`org`, `publish`, `skill`, `teamLimit.ts`, `user`. The `org` sub-domain
introduces a third dimension (orgs sit between users and teams),
addressed via `support/permission/org/`. `inheritPermission.ts` is a
TypeScript-typed inheritance walker — distinctive engineering.

**However**, tenant isolation is still **application-layer** (MongoDB
queries filter by `teamId`). MongoDB has no row-level-security equivalent;
isolation is the discipline of the access controllers in
`packages/service/support/permission/`. There are no Postgres RLS
policies in the `pgvector` migration (the dataset vector store is keyed
by `teamId` in the document chunk but enforced at the query layer).

Compared to spring-ai-ascend (Rule R-J.a: RLS at the storage engine;
Rule R-C.2.a: `tenantId` not-null on Run), FastGPT's team model is the
**broadest** but again application-layer-only. The license restriction
(see §8) makes multi-tenant SaaS operation a commercial-license
concern; per-team isolation in the open-source distribution is by
ownership claim, not by engine.

## 5. Engine pluggability

The engine is a **single bespoke DAG runtime** under
`packages/service/core/workflow/dispatch/index.ts` (1,699 lines). The
dispatch surface is dictionary-driven (`callbackMap` at
`dispatch/index.ts:50`, defined in `dispatch/constants.ts`), where each
`FlowNodeTypeEnum` value maps to one dispatch function. Adding a new
node type means:

1. Adding the enum value to
   `packages/global/core/workflow/node/constant.ts`.
2. Implementing the dispatch function under
   `packages/service/core/workflow/dispatch/<category>/<node>.ts`.
3. Adding the React node component to the `app` project.

There is no envelope-typed engine registry, no typed mismatch exception,
no hook-point catalogue. The closest thing to cross-cutting policy is
`packages/service/core/workflow/dispatch/utils/clientAbort.ts` (abort
tracking) and `dispatch/workflowStatus.ts` (the `shouldWorkflowStop` /
`delAgentRuntimeStopSign` pair imported at `dispatch/index.ts:64`) —
both ad-hoc rather than declarative.

**Plugin pluggability** is more interesting. The `plugin` node category
(`dispatch/plugin/{run,runInput,runOutput}.ts`) lets a workflow call a
plugin as a node. Plugins themselves are FastGPT apps marked as
`AppTypeEnum.plugin`, so the runtime is recursive: an app dispatches a
node that dispatches a sub-app. **MCP integration** is first-class —
`packages/service/support/mcp/` plus `projects/mcp_server/` and
`packages/global/core/workflow/template/system/mcp/` show MCP server
client + server roles wired in. The README lists **bidirectional MCP**
as a feature (`README.md:74`).

**Agent mode** (`projects/app/src/pages/api/core/ai/agent/v2/`) is a
separate dispatch path — the v2 agent API is a planning agent with
its own tool selection. Selection of agent-mode vs workflow-mode is by
app `type`, not by envelope.

## 6. Evolution substrate

FastGPT's RAG substrate is its **dominant differentiator** — the most
sophisticated retrieval pipeline in this tranche.

The `defaultRecall` directory at
`packages/service/core/dataset/search/defaultRecall/` enumerates the
retrieval stages (line counts shown to indicate engineering depth):

| File | Lines | Purpose |
|---|---|---|
| `collectionFilter.ts` | 248 | Intersection of multi-dimensional filters |
| `embeddingRecall.ts` | 300 | Dense vector recall (pgvector) |
| `fullTextRecall.ts` | 215 | Lexical full-text recall |
| `imageCaption.ts` | 125 | Image-captioning recall (multi-modal) |
| `multiQueryRecall.ts` | 84 | Multi-query expansion |
| `rerank.ts` | 110 | Cross-encoder rerank |
| `result.ts` | 100 | Result fusion + dedup |
| `index.ts` | 191 | Pipeline orchestration |

Hybrid retrieval is the **default**, not an upgrade. The image-caption
recall path indicates multi-modal RAG support (image embeddings stored
alongside text chunks, captioned via the configured VLM).

The dataset schema at `packages/service/core/dataset/schema.ts:60-140`
declares per-dataset configuration of:

- `vectorModel` (default `text-embedding-3-small`).
- `agentModel` (default `gpt-4o-mini` — used for chunk-extraction LLM).
- `vlmModel` (optional, for image captioning).
- `chunkSettings`: full sub-document covering `trainingType`,
  `chunkTriggerType`, `chunkSettingMode`, `chunkSplitMode`,
  `paragraphChunkAIMode`, `paragraphChunkDeep`, `paragraphChunkMinSize`,
  `chunkSize`, `chunkSplitter`, `indexSize`, `qaPrompt`, plus
  `imageIndex`, `autoIndexes`, `indexPrefixTitle`, `dataEnhanceCollectionName`.
- `apiDatasetServer` — external knowledge-base bridge.
- `websiteConfig` — URL crawl + selector.

The training pipeline (`packages/service/core/dataset/training/`) supports
parent-child chunking, auto-indexes (LLM-augmented chunk synonyms), QA
generation (auto-creating Q&A pairs from raw docs), and image embedding
— all directly declared in the chunkSettings sub-document.

There is **no evolution / trajectory store**. Memory is chat-scoped via
`chat` collection (`packages/service/core/chat/`). The
`packages/service/support/wallet/usage/` collection tracks billing
usage per call but is not a trajectory archive. No equivalent to
spring-ai-ascend's `agent-evolve` plane.

## 7. Deployment model + sovereign-hardware support

Deployment is **first-class Helm + docker-compose**. The Helm chart at
`deploy/helm/fastgpt/Chart.yaml` declares `appVersion: v4.14.12` and
pulls Bitnami's `mongodb@15.0.1` + `postgresql` charts as dependencies
(`Chart.yaml:28-35`). This is the only platform in this tranche
shipping a production-ready Helm chart in-tree.

The docker-compose tree is versioned:
- `deploy/dev/docker-compose.yml` + `docker-compose.cn.yml` (CN mirror
  variant).
- `deploy/templates/docker-compose.dev.yml`.
- `deploy/version/main/docker-compose.template.yml` (latest).
- `deploy/version/v4.14/docker-compose.template.yml` (v4.14 LTS).

Sealos-native deployment (`deploy/templates/`, `Chart.yaml` showing
Sealos as sponsor) is the documented preferred path: one-click deploy
to a Sealos cluster (`README.md:58`).

**Chinese-silicon footprint**: a repository-wide Grep for `Ascend|
Kunpeng|NPU|昇腾` returns hits **only** in `monaco-editor` minified JS
under `projects/app/public/js/monaco-editor.0.45.0/vs/...` (editor
bundles containing the words "ascending" / "descending" in tooltips),
plus one tangential hit in `web/core/workflow/adapt.ts` and a
`RoleSelect.tsx` component (both unrelated dictionary words). There is
**zero intentional Ascend NPU or Kunpeng ARM64 support** in code.

Provider list is OpenAI-centric (`packages/service/core/ai/llm/` —
default `agentModel: gpt-4o-mini`, default `vectorModel:
text-embedding-3-small`), with Chinese-cloud LLM support layered via
the `aiproxy/` route (`projects/app/src/pages/api/aiproxy/api/`) — a
proxy gateway pattern rather than first-party adapters. Container base
images target x86_64.

No `deployment_plane` discriminator, no module-level deployment topology.
The Helm chart is monolithic — one chart deploys the entire app.

## 8. License + corporate sponsor

License: **modified Apache 2.0** (`LICENSE:1-22`) with two additional
clauses substantively identical to Dify's:

1. **Multi-tenant SaaS restriction** (`LICENSE:7`):
   > "Multi-tenant SaaS service: Unless explicitly authorized by FastGPT
   > in writing, you may not use the FastGPT.AI source code to operate
   > a multi-tenant SaaS service that is similar to the FastGPT."

2. **Branding restriction** (`LICENSE:8`):
   > "LOGO and copyright information: In the process of using FastGPT,
   > you may not remove or modify the LOGO or copyright information in
   > the FastGPT console."

Contributor re-licensing clause (`LICENSE:12-15`): the producer can
relax or tighten the license, and contributions may be used for
commercial purposes including FastGPT's cloud business. The
"interactive design ... protected by appearance patent" clause
(`LICENSE:19`) is identical wording to Dify's — both projects use the
same template-style modified-Apache license.

Corporate sponsor: **Sealos** (`LICENSE:21`, copyright 2023,
contact `dennis@sealos.io`). Sealos is a Kubernetes-based cloud OS
project; FastGPT is the flagship AI application on the Sealos platform.
The first-class Helm chart at `deploy/helm/fastgpt/` is consistent with
this sponsor relationship — Sealos deploys workloads via Helm.

The license posture has the same practical implication as Dify's:
**FastGPT cannot be embedded as the runtime for a multi-tenant
enterprise platform without a commercial agreement with Sealos**. The
`pro/` directory in the repo (mentioned in `package.json` scripts —
`clean:unused:pro`, `dev:pro`) is gitignored on the public clone but
implies a separately-licensed Pro/Enterprise distribution.

## 9. What we LEARN

Patterns worth absorbing into spring-ai-ascend:

1. **Hybrid retrieval with multi-modal recall as the default** —
   `packages/service/core/dataset/search/defaultRecall/{embeddingRecall,
   fullTextRecall,imageCaption,multiQueryRecall,rerank,result}.ts`
   shows a production-grade RAG pipeline with hybrid (dense+lexical)
   recall, multi-modal (image) recall, multi-query expansion, and
   cross-encoder rerank as the default. Our graphmemory-starter should
   ship hybrid + rerank in the default profile, not behind a flag.

2. **Chunk-settings as a per-dataset sub-document** —
   `packages/service/core/dataset/schema.ts:21-58` (the `ChunkSettings`
   spec) is a clean separation: every chunking knob is configurable at
   ingestion time but stored as a sub-document on the dataset. Same idea
   in JVM land: a `ChunkingProfile` `@ConfigurationProperties` carried
   on the `KnowledgeBase` aggregate.

3. **OpenAPI compatibility as a migration on-ramp** — FastGPT exposes
   every app as an OpenAI-Chat-Completions-compatible endpoint
   (`packages/service/openapi/` plus `README.md:80`). Customers can
   point their existing OpenAI SDK at FastGPT and get all the workflow
   + RAG benefits for free. spring-ai-ascend should consider an
   OpenAI-compat shim over its Runtime API to ease customer migration
   from OpenAI Assistants API.

4. **Tarjan SCC for cycle detection in user-built workflows** —
   `packages/service/core/workflow/utils/tarjan.ts` (imported at
   `dispatch/index.ts:61`) classifies edges by DFS and finds SCCs,
   letting the runtime detect cycles, validate loop placement, and
   reject malformed graphs at design time. Our engine v1 ships graph
   validation in `agent-execution-engine`; this Tarjan-based approach
   is a clean reference for cycle / loop topology validation.

5. **First-class Helm chart in-tree** — `deploy/helm/fastgpt/Chart.yaml`
   ships with the source tree, not as a separate repo, and depends on
   Bitnami's MongoDB + PostgreSQL charts. The single-repo Helm story is
   a significant DX win — `helm install fastgpt ./deploy/helm/fastgpt`
   from the clone is the canonical install path. spring-ai-ascend should
   bring our Helm chart in-tree at `deploy/helm/spring-ai-ascend/`.

6. **Sub-document inheritance via `inheritPermission`** —
   `packages/service/support/permission/inheritPermission.ts` is a
   TypeScript-typed walker that resolves permission inheritance across
   nested resources (e.g., a chat permission inheriting from its app's
   permission). Our `RoleAccess` story currently lacks an inheritance
   mechanism; this is a clean reference shape.

7. **Versioned docker-compose template per app release** —
   `deploy/version/{main,v4.14}/docker-compose.template.yml` keeps a
   versioned docker-compose template per release line, letting a
   self-hoster pin to a stable release. Our `docs/quickstart.md` should
   point at a versioned compose template, not the always-latest.

8. **Sandbox isolation per execution kind** —
   `projects/{agent-sandbox,code-sandbox}/` are two distinct sandbox
   projects (Node sandbox for agent tools, separate code sandbox for
   user-authored custom code nodes). The two-sandbox split — one for
   semi-trusted skill code, one for fully-untrusted user code — aligns
   with our Rule R-L sandbox subsumption posture.

## 10. Where we DIFFER

Each row cites one evidence file in **each** repo.

| # | Dimension | FastGPT evidence | spring-ai-ascend evidence |
|---|-----------|------------------|---------------------------|
| 1 | **Tenant license posture** — FastGPT: multi-tenant SaaS requires commercial license. Ascend: Apache 2.0, multi-tenant is the design target. | `LICENSE:7` (multi-tenant restriction) | `D:\chao_workspace\spring-ai-ascend\LICENSE` (Apache 2.0 baseline) |
| 2 | **Tenant isolation depth** — FastGPT: teamId required on documents, MongoDB has no RLS equivalent. Ascend: RLS at Postgres engine. | `packages/service/core/dataset/schema.ts:71-80` (teamId required, no RLS) | Rule R-J.a + Flyway migrations with `CREATE POLICY` |
| 3 | **JVM vs Node runtime** — FastGPT: TypeScript on Node 22 + Next.js. Ascend: Spring-native JVM. | `package.json:1-15` (Node-only stack) | `D:\chao_workspace\spring-ai-ascend\pom.xml` (Spring Boot + Maven) |
| 4 | **Engine pluggability** — FastGPT: bespoke DAG dispatch (callbackMap dictionary). Ascend: typed `EngineRegistry.resolve(envelope)`. | `packages/service/core/workflow/dispatch/index.ts:50` (callbackMap) | `docs/contracts/engine-envelope.v1.yaml` + Rule R-M.a |
| 5 | **RAG depth** — FastGPT: 1,700 lines of hybrid + multi-modal + rerank recall pipeline. Ascend: graphmemory-starter delegates to upstream Spring AI. | `packages/service/core/dataset/search/defaultRecall/*` (1,500+ lines) | `spring-ai-ascend-graphmemory-starter` (single retrieval node) |
| 6 | **Ascend+Kunpeng sovereignty** — FastGPT: x86_64 + OpenAI-default model config. Ascend: NPU+ARM64 as design target. | `packages/service/core/dataset/schema.ts:99-108` (`text-embedding-3-small` + `gpt-4o-mini` defaults) | Rule R-I five-plane manifest |
| 7 | **MongoDB vs Postgres-only** — FastGPT: dual-backend (MongoDB metadata + Postgres pgvector). Ascend: Postgres only. | `packages/service/core/dataset/schema.ts:1` (Mongoose) + pgvector for vectors | Flyway-migrated Postgres-only persistence |
| 8 | **Visual canvas product** — FastGPT: React Flow console + Sealos cloud-OS deploy. Ascend: SPI library, no console v1. | `projects/app/src/pageComponents/` (React Flow UI) | `D:\chao_workspace\spring-ai-ascend\pom.xml` (no admin module) |
| 9 | **Audit-grade Run spine** — FastGPT: workflow execution status, no Run aggregate or idempotency table. Ascend: Run + idempotency, durable. | `packages/service/core/workflow/dispatch/workflowStatus.ts` (in-memory abort flags) | Rule R-C.2.a + `agent-service/.../runtime/idempotency/` |
| 10 | **Governance / Code-as-Contract** — FastGPT: ESLint + Vitest + Turbo only. Ascend: 144+ gate rules + ArchUnit + ADR ledger. | `eslint.config.mjs` + `turbo.json` (no architectural enforcers) | `D:\chao_workspace\spring-ai-ascend\CLAUDE.md` (Rule kernel index) + `gate/` |

FastGPT and Dify share the **same license template** (modified Apache 2.0
with multi-tenant SaaS restriction and branding clause) and the **same
business model** (open-source for SMB self-host, commercial license for
enterprise SaaS, hosted offering from the sponsor). Both are excellent at
console UX and RAG depth but both gate enterprise multi-tenancy behind
a commercial agreement. spring-ai-ascend's differentiation is the
orthogonal stack: JVM-native, Apache 2.0 without restrictions, RLS-grade
tenant isolation, NPU+ARM64 sovereignty, governance-as-code. FastGPT's
strongest pattern to absorb is its hybrid+multi-modal RAG default — a
clear pillar where the v1 Ascend posture is too thin.
