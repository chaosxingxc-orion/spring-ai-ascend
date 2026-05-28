---
analysis_id: COMPETITIVE-DIFY
governance_infra: true
analysis_date: 2026-05-28
analyst: spring-ai-ascend Phase C Tranche 1
repo_clone_at: D:\ai-research\agent-platforms-survey\dify\
---

# Competitive Analysis: langgenius/dify

Source-grounded analysis of the `dify` repository at clone HEAD, project
version `1.14.2` declared in `api/pyproject.toml:3`. Conducted per the C.2
ten-point template. Dify is one of the most visible visual-LLM-app builders
in the Chinese enterprise market and ships under a modified Apache 2.0
license whose multi-tenant restriction directly intersects spring-ai-ascend's
positioning (`LICENSE:5-11`).

## 1. Tagline & positioning

The README's own framing, verbatim from `README.md:63`:

> "Dify is an open-source LLM app development platform. Its intuitive
> interface combines AI workflow, RAG pipeline, agent capabilities, model
> management, observability features (including Opik, Langfuse, and Arize
> Phoenix) and more, letting you quickly go from prototype to production."

Three product surfaces ship together: a Next.js console under `web/` for
visual app construction, a Python Flask API under `api/` powering chat /
workflow execution, and a deeply integrated marketplace of plugin runtimes
(`api/core/plugin/impl/{model,model_runtime,tool,trigger,agent,oauth,…}.py`).
The README headlines "AI workflow / RAG pipeline / agent capabilities / model
management / observability" as five first-class verticals.

Positioning is **end-user product**, not framework: the elevator pitch in
`docs/zh-CN/README.md` and the `LICENSE:5-8` clause both reference operating
Dify as a "backend service for other applications or as an application
development platform for enterprises". The producer is LangGenius, Inc.
(`LICENSE:22`), with Dify Cloud (`https://cloud.dify.ai`) as the SaaS
offering and self-hosting (`docker compose up -d` per `README.md:74-80`) as
the open-source path. The console-first framing is decisively different
from spring-ai-ascend's SPI-library posture — Dify is a **product you log
into**, spring-ai-ascend is a **BoM you depend on**. The repo carries
GitHub-stars badges, LFX Linux Foundation health-score badges
(`README.md:35-40`), and ships a 17-language i18n catalogue
(`web/i18n/{en-US,zh-Hans,zh-Hant,ja-JP,…}/`), confirming a
consumer-grade-product gravity well rather than an
enterprise-architecture-kernel one.

## 2. Architecture skeleton

Top-level monorepo layout (root `package.json:1-15` declares pnpm v11.2 +
Node 22.22, indicating the JS workspace is the spine that orchestrates the
Python API as a sibling):

```
api/                  # Python 3.12 Flask backend (api/pyproject.toml:3-7)
web/                  # Next.js console
packages/             # JS shared packages (dify-ui, contracts, dev-proxy)
dify-agent/           # Standalone Python agent runtime (`pyproject.toml`)
sdks/                 # First-party clients (nodejs-client, php-client)
docker/               # 1,200-line docker-compose.yaml (1201 lines counted)
docker/{pgvector,tidb,elasticsearch,iris,couchbase-server}/
e2e/                  # End-to-end test suite
cli/, dev/, scripts/  # Dev tooling
enterprise/, docs/
```

The Python backend's internal layering, from `api/`:

```
api/
  app.py + app_factory.py + dify_app.py     # Flask app construction
  controllers/                              # HTTP routes
  services/                                 # Business logic (~70 *.py files)
  core/
    workflow/        # Workflow node engine (delegates to graphon==0.4.0)
      nodes/{agent,agent_v2,datasource,knowledge_index,
             knowledge_retrieval,trigger_plugin,trigger_schedule,
             trigger_webhook}/
    agent/           # CoT + Function-Call agent runners
      {base_agent_runner,cot_agent_runner,cot_chat_agent_runner,
       fc_agent_runner}.py
    rag/             # RAG pipeline (chunking → embed → retrieve → rerank)
      {cleaner,data_post_processor,docstore,embedding,extractor,
       index_processor,pipeline,rerank,retrieval,splitter,summary_index}/
    plugin/impl/     # Plugin SPI bridges
      {model,model_runtime,tool,trigger,agent,oauth,debugging,
       endpoint,asset,datasource,dynamic_select}.py
    mcp/             # Model Context Protocol
  models/            # SQLAlchemy ORM
    {workflow.py (2,125 lines),dataset.py (1,786 lines),
     account.py (429 lines),agent.py,model.py,provider.py,trigger.py,
     human_input.py,task.py,tools.py}
  providers/vdb/     # 32 vector-store adapters as separate pyproject.toml
                     # (vdb-pgvector, vdb-milvus, vdb-qdrant,
                     #  vdb-huawei-cloud, vdb-tencent, vdb-baidu, …)
  providers/trace/   # 8 observability adapters (aliyun, arize-phoenix,
                     #  langfuse, langsmith, mlflow, opik, tencent, weave)
  enterprise/        # telemetry only — gated commercial features
                     # ride on top of this layer
```

The dependency `graphon==0.4.0` declared at `api/pyproject.toml:51` and
referenced throughout `api/models/workflow.py:34-46` is a separate
LangGenius-owned Python package providing the workflow execution graph —
i.e., the *runtime* is published as a private PyPI dependency, and the
workflow node implementations in `api/core/workflow/nodes/` are
**adapters** that emit `graphon` `NodeConfigDict` objects. This is a
notable architectural inversion: the heavy graph runtime is *out of* the
public repository.

## 3. Developer experience

"Build my first agent" is a **console-driven, zero-Python** journey. The
documented flow from `README.md:67-87`:

1. `cd dify/docker && cp .env.example .env && docker compose up -d` —
   stands up nine services (Postgres, Redis, vector DB, Weaviate, sandbox,
   ssrf_proxy, api, worker, web) per `docker/docker-compose.yaml`
   (1,201 lines).
2. Visit `http://localhost/install`, register the first admin user (who
   becomes tenant owner per `api/models/account.py:240-276`).
3. In the console, click "Create App" → choose `Chatbot`, `Agent`,
   `Workflow`, `Chatflow`, or `Text Generator`.
4. The **visual builder** (a React Flow canvas under
   `web/app/components/workflow/`) lets the user drag nodes —
   `LLM`, `Knowledge Retrieval`, `Agent`, `Code`, `HTTP Request`,
   `Question Classifier`, `IF/ELSE`, `Iteration`, `Variable Aggregator`,
   `Tool`, `End`, plus the eight `api/core/workflow/nodes/` directories —
   onto a canvas and wire them edge-by-edge.
5. The console serialises the canvas to JSON (`api/models/workflow.py`
   declares `WorkflowDraft`/`Workflow` rows holding `graph` blobs) and
   the API materialises a `graphon.entities.graph_config.NodeConfigDict`
   at execution time (`api/models/workflow.py:34`).

A developer never writes a `.py` file unless extending via the
**plugin marketplace** (`api/core/plugin/impl/*.py`), which is itself a
remote registry — plugins are loaded over HTTP from `plugin_daemon` rather
than imported as Python packages. There is no "first-Python-file" path
analogous to spring-ai-ascend's `docs/quickstart.md` — Dify expects
configuration-as-canvas. The closest CLI analogue is `cli/`, which is a
Node.js DSL helper, not an agent runner.

## 4. Multi-tenancy & governance

Dify has the **most deeply baked tenant model of any platform in this
analysis**, and it is the load-bearing wall of its licensing strategy.

The tenant aggregate is declared at `api/models/account.py:240-276`:

```python
class Tenant(TypeBase):
    __tablename__ = "tenants"
    __table_args__ = (sa.PrimaryKeyConstraint("id", name="tenant_pkey"),)
    id: Mapped[str] = mapped_column(StringUUID, ...)
    name: Mapped[str]
    encrypt_public_key: Mapped[str | None]
    plan: Mapped[str] = mapped_column(default="basic")
    status: Mapped[TenantStatus] = ...  # NORMAL, ARCHIVE
    custom_config: Mapped[str | None] = mapped_column(LongText, ...)
```

`TenantAccountJoin` (`api/models/account.py:279-305`) is the
many-to-many bridge with a unique constraint
`UniqueConstraint("tenant_id", "account_id")` and indexes on both
`tenant_id` and `account_id`. The `tenant_id` column propagates through
every major table: `api/models/dataset.py:170,179` indexes
`Dataset.tenant_id`; `api/models/dataset.py:493,499` indexes
`Document.tenant_id`; `api/models/dataset.py:839-842` composites
`(document_id, tenant_id)` and `(dataset_id, tenant_id)` on
`DocumentSegment`. Workflow tables in `api/models/workflow.py` carry 48
`tenant_id` references (counted via Grep). Helper
`_resolve_workflow_app_tenant_id` (`api/models/workflow.py:82-88`)
re-validates tenant_id every time a workflow is loaded for an app.

But — **tenant isolation is application-layer only**. Grep over
`api/migrations/` (Flyway-equivalent Alembic migrations) shows no Postgres
`CREATE POLICY ... ROW LEVEL SECURITY` statements; isolation is enforced by
WHERE-clause discipline in the SQLAlchemy services, not at the storage
engine. A bug that omits a `where(Dataset.tenant_id == current_tenant)`
clause leaks across tenants undetectably.

Compared to spring-ai-ascend (Rule R-J.a: RLS in every Flyway migration
creating a `tenant_id` column; Rule R-C.2.a: `tenantId` required-non-null on
Run; Rule R-J.b: HTTP-edge re-validation on cancel), Dify's tenant model is
**broad but shallow** — present everywhere in code, enforced nowhere in
the engine.

## 5. Engine pluggability

There are **three orthogonal "engine" pluggability surfaces** in Dify:

1. **Workflow node engine** (`api/core/workflow/nodes/`) — node types
   `agent`, `agent_v2`, `datasource`, `knowledge_index`,
   `knowledge_retrieval`, `trigger_plugin`, `trigger_schedule`,
   `trigger_webhook`. Each is a Python class registered with the
   `graphon` library, then composed by the visual builder. Adding a node
   means writing a Python class that emits `NodeConfigDict` and a
   matching React component in `web/`.
2. **Agent runner engine** (`api/core/agent/`) — three concrete agent
   runners share `base_agent_runner.py:1-x` as parent:
   `cot_agent_runner.py` (Chain-of-Thought, prompt-only),
   `cot_chat_agent_runner.py` (CoT over chat), and `fc_agent_runner.py`
   (function-calling). Selection is by app configuration
   (`agent_mode: cot | function_call`), not envelope-typed.
3. **Plugin runtime engine** (`api/core/plugin/impl/`) — `plugin.py`,
   `model.py`, `model_runtime.py`, `tool.py`, `trigger.py`, `agent.py`,
   `oauth.py`, `datasource.py` declare HTTP-shaped SPIs that call a
   separate plugin_daemon process (mocked in
   `api/tests/integration_tests/model_runtime/__mock/plugin_daemon.py`).
   This is genuine pluggability: plugins are out-of-process and discovered
   at runtime through a marketplace.

There is no envelope schema (`EngineEnvelope` analogue), no typed engine
mismatch (`EngineMatchingException` analogue), and no hook-point catalogue
for cross-cutting middleware. Pluggability is *expressed in three different
ways at three layers*, each with its own registration semantic — node
class, agent-mode string, plugin manifest. spring-ai-ascend's Rule R-M
unifies these under one `EngineRegistry.resolve(envelope)` + `HookPoint`
contract; Dify's approach is more pragmatic but architecturally diffuse.

## 6. Evolution substrate

Dify has the **richest knowledge / RAG substrate** of the four platforms
analysed in this tranche. The RAG pipeline lives under `api/core/rag/`:

- `extractor/` — file-type extractors (PDF, DOCX, HTML, Markdown,
  notion-md, …) loaded via per-format `Extractor` subclasses.
- `splitter/` — chunkers (recursive character, token, sentence-split).
- `index_processor/` — orchestrates `extract → clean → split → embed →
  index` with `index_processor_factory.py` choosing the strategy
  (`paragraph`, `parent-child`, `qa`) per `models/dataset.py:21-24`.
- `embedding/` — embedding wrapping over upstream provider models.
- `retrieval/` — `dataset_retrieval.py` exposes semantic, full-text, and
  hybrid retrieval modes (`retrieval_methods.py`); a router enables
  multi-dataset retrieval with reranking.
- `rerank/` — pluggable rerankers (Cohere, Jina, custom).
- `data_post_processor/` — score filtering, citation extraction.
- `pipeline/` — multi-stage pipelines configurable from the canvas.

The vector store adapter count is exceptional: **32 VDB providers** under
`api/providers/vdb/` — each a sibling `pyproject.toml` package
(`vdb-pgvector`, `vdb-milvus`, `vdb-qdrant`, `vdb-tencent`,
`vdb-alibabacloud-mysql`, `vdb-baidu`, `vdb-huawei-cloud`, `vdb-iris`,
`vdb-clickzetta`, `vdb-tidb-vector`, `vdb-tidb-on-qdrant`, `vdb-oceanbase`,
`vdb-opensearch`, `vdb-opengauss`, `vdb-vastbase`, `vdb-vikingdb`,
`vdb-tablestore`, `vdb-lindorm`, `vdb-relyt`, `vdb-pgvecto-rs`,
`vdb-matrixone`, `vdb-myscale`, `vdb-elasticsearch`, `vdb-weaviate`,
`vdb-chroma`, `vdb-couchbase`, `vdb-upstash`, `vdb-analyticdb`,
`vdb-hologres`, `vdb-oracle`, `vdb-huawei-cloud`, `vdb-tidb-vector`). The
Chinese-cloud coverage is comprehensive (Aliyun AnalyticDB, Aliyun
Hologres, Baidu, Tencent, Huawei Cloud, OceanBase, TiDB).

There is **no evolution / trajectory / fine-tune substrate**. Grep for
`Trajectory|FineTune` in `api/` returns no production hits. Memory is
session-scoped (chat memory in `api/core/memory/`), not a durable
post-task knowledge store. There is no analogue to spring-ai-ascend's
`agent-evolve` Python evolution plane (Rule R-I `deployment_plane:
evolution`) or `EvolutionExport` event-scope discriminator (Rule R-M.e).

## 7. Deployment model + sovereign-hardware support

Deployment is **docker-compose first**, with three composes:
`docker/docker-compose.yaml` (1,201 lines, the canonical full stack),
`docker/docker-compose-template.yaml` (1,195 lines, parameterised), and
`docker/docker-compose.middleware.yaml` (Postgres + Redis + Weaviate only,
for developer mode). The full stack stands up nine services. There is
no Helm chart in-tree (`find -name "Chart.yaml"` returns no hits inside
the repo); a separate `langgenius/dify-helm` repository hosts Kubernetes
deployment.

**Chinese-silicon footprint**: a repository-wide Grep for
`Ascend|Kunpeng|NPU|昇腾|鲲鹏` in source code returns zero hits — all
matches are in Monaco editor minified JS bundles (`web/public/vs/...`) or
`tsWorker.js`, none in Python or TypeScript code we author. The Dockerfile
base images (`api/Dockerfile`, `web/Dockerfile`) target `python:3.12-slim`
and `node:22-alpine`, both x86_64-first.

**However**, Dify ships a `vdb-huawei-cloud` provider under
`api/providers/vdb/vdb-huawei-cloud/pyproject.toml` — i.e., Huawei Cloud
GaussDB / Cloud Search Service vector-store integration exists. This is
cloud-service integration, not on-prem Ascend NPU model serving. There is
no NPU-aware model runtime, no ARM64-specific build profile, no
`deployment_plane` discriminator. Compared to spring-ai-ascend's Rule R-I
five-plane manifest with `compute_control` / `bus_state` / `sandbox` /
`evolution` deployment planes declared per-module in
`module-metadata.yaml`, Dify treats deployment as a single docker-compose
unit with no plane discipline.

## 8. License + corporate sponsor

License: **modified Apache 2.0** with two field-of-use restrictions
(`LICENSE:1-21`):

1. **Multi-tenant restriction** (`LICENSE:5-8`):
   > "Multi-tenant service: Unless explicitly authorized by Dify in
   > writing, you may not use the Dify source code to operate a
   > multi-tenant environment. Tenant Definition: Within the context of
   > Dify, one tenant corresponds to one workspace."
2. **Branding restriction** (`LICENSE:10-11`):
   > "LOGO and copyright information: In the process of using Dify's
   > frontend, you may not remove or modify the LOGO or copyright
   > information in the Dify console or applications."

The producer is **LangGenius, Inc.** (`LICENSE:22`, copyright 2025). The
restrictions explicitly prevent rebranding the frontend AND prevent
running Dify as a SaaS multi-tenant service without a commercial license.
This is *exactly* the segment spring-ai-ascend's enterprise customers
would want — multi-tenant deployment is the central enterprise use case.

For spring-ai-ascend, the practical implication is: **Dify cannot be
embedded as the runtime for a multi-tenant enterprise platform without a
commercial agreement with LangGenius**. The license also has a contributor
re-licensing clause (`LICENSE:13-17`) — contributions can be relicensed by
LangGenius. The "interactive design ... protected by appearance patent"
clause (`LICENSE:20`) layers design-patent protection on top of the
copyright license — uncommon and worth flagging.

## 9. What we LEARN

Patterns worth absorbing into spring-ai-ascend:

1. **VDB provider catalogue as separate sibling packages** — 32 `pyproject.toml`
   adapters under `api/providers/vdb/*/pyproject.toml` give Dify the broadest
   vector-store coverage of any platform reviewed. Our
   `spring-ai-ascend-graphmemory-starter` should mirror this pattern: one
   Maven module per major vector store with a thin BoM importing only the
   chosen backend, instead of one fat memory starter.

2. **Visual workflow JSON as the persistence artefact** —
   `api/models/workflow.py:2125` lines reveal that workflow definitions
   are serialised JSON blobs (the `graph` column) versioned via
   `WorkflowDraft` → `Workflow` → `WorkflowAppLog`. The publish/draft
   split is clean: editors mutate `WorkflowDraft`, publish freezes a
   `Workflow` row, and execution always materialises from the frozen
   version. spring-ai-ascend could borrow this draft/publish lifecycle for
   our `EngineEnvelope` definitions.

3. **Out-of-process plugin daemon for engine pluggability** —
   `api/core/plugin/impl/{tool,model_runtime,trigger,agent}.py` all talk
   to a `plugin_daemon` process over HTTP rather than importing Python
   modules. This decouples plugin release cadence from platform release
   cadence and provides natural process-level sandbox isolation.
   spring-ai-ascend's plugin/extension story currently mandates JVM
   classloading; an out-of-process plugin daemon model is worth
   considering for untrusted skills.

4. **Tenant model as a first-class aggregate with custom_config blob** —
   `api/models/account.py:240-276` shows `Tenant.custom_config` as a
   per-tenant JSON blob holding feature flags and limits. Our tenant
   model lacks this per-tenant configuration carrier; adding one would
   unblock posture-aware per-tenant overrides.

5. **Eight trace-provider adapters in-tree** — `api/providers/trace/`
   ships `aliyun, arize-phoenix, langfuse, langsmith, mlflow, opik,
   tencent, weave`. We currently propose OpenTelemetry-only; offering an
   adapter shape with one or two paid SaaS targets shipped in-tree would
   improve Day-1 onboarding for trace-curious enterprises.

6. **`enterprise/` directory as the natural gating point** —
   `api/enterprise/{telemetry,…}` plus
   `api/services/enterprise/{enterprise_service,plugin_manager_service,…}.py`
   is where Dify's commercial gating lives. The same shape — one
   directory per commercial-gated capability, all open-source-licensed
   but feature-gated by license check — is a clean pattern to follow
   *if* we ever offer commercial extras.

7. **17-language i18n with locale catalogues per route** —
   `web/i18n/{lang}/app-debug.json` per locale. If we expose a console,
   the per-route locale-catalogue layout (vs. one monolithic locale file)
   keeps translation diffs small.

## 10. Where we DIFFER

Each row cites one evidence file in **each** repo.

| # | Dimension | Dify evidence | spring-ai-ascend evidence |
|---|-----------|---------------|---------------------------|
| 1 | **Tenant isolation depth** — Dify: tenant_id columns everywhere, no RLS. Ascend: RLS at the storage engine + HTTP-edge re-validation. | `api/models/dataset.py:170,179` (tenant_id indexed but no RLS); `api/models/workflow.py` (48 tenant_id occurrences, no policy) | Rule R-J.a + `docs/governance/rules/rule-R-J.md` + Flyway migrations under `agent-service/src/main/resources/db/migration/` |
| 2 | **Tenant license posture** — Dify: multi-tenant operation requires commercial license. Ascend: Apache 2.0, multi-tenant is the design target. | `LICENSE:5-8` (multi-tenant restriction) | `D:\chao_workspace\spring-ai-ascend\LICENSE` (Apache 2.0 baseline) |
| 3 | **Visual builder vs SPI library** — Dify: console-first product. Ascend: BoM + SPI library, no console. | `web/app/components/workflow/` (React Flow canvas) + `web/i18n/` (17-language UI) | `D:\chao_workspace\spring-ai-ascend\pom.xml` (no admin/console module) |
| 4 | **Engine envelope** — Dify: three orthogonal pluggability mechanisms (node class, agent_mode string, plugin manifest). Ascend: one `EngineEnvelope` + `EngineRegistry.resolve()`. | `api/core/workflow/nodes/agent/agent_node.py` + `api/core/agent/cot_agent_runner.py` + `api/core/plugin/impl/agent.py` | `docs/contracts/engine-envelope.v1.yaml` + Rule R-M.a |
| 5 | **Ascend+Kunpeng sovereignty** — Dify: x86_64 Docker, generic Python 3.12, only `vdb-huawei-cloud` cloud-service adapter. Ascend: NPU+ARM64 as design target. | `api/Dockerfile` (python:3.12-slim) + `api/providers/vdb/vdb-huawei-cloud/pyproject.toml` (cloud-service only) | `D:\chao_workspace\spring-ai-ascend\README.md:3-10` + Rule R-I five-plane manifest |
| 6 | **Run state machine** — Dify: workflow status enums in `WorkflowExecutionStatus` (graphon enum), no typed transition validation. Ascend: `RunStateMachine.validate(from, to)` mandatory. | `api/models/workflow.py:38-42` (status enums) | Rule R-C.2.b + `agent-service/.../runtime/runs/RunStateMachine.java` |
| 7 | **JVM enterprise tooling** — Dify: Python/TypeScript-only, no JVM SPI surface. Ascend: Spring-native, JVM-resident, ArchUnit-gated. | `api/pyproject.toml` + `web/package.json` (no JVM artefacts) | `D:\chao_workspace\spring-ai-ascend\CLAUDE.md` (Rule R-C: ArchUnit + ArchUnit-gated SPI) |
| 8 | **Evolution substrate scope** — Dify: no trajectory/evolution. Ascend: dedicated `agent-evolve` plane. | `api/core/` lacks `evolve/` or `trajectory/` (repository-wide Grep zero hits) | `D:\chao_workspace\spring-ai-ascend\agent-evolve\module-metadata.yaml` |
| 9 | **Audit-grade Run spine** — Dify: workflow_run rows with status, no idempotency table. Ascend: Run + idempotency aggregate, durable. | `api/models/workflow.py:2125` (no idempotency_keys table) | Rule R-C.2.a + `agent-service/.../runtime/idempotency/` |
| 10 | **Governance / Code-as-Contract** — Dify: ruff + pytest + ESLint, no architectural enforcers. Ascend: 144+ gate rules + ArchUnit. | `api/pyproject.toml` (ruff, pytest only) | `D:\chao_workspace\spring-ai-ascend\gate/check_architecture_sync.sh` |

These ten dimensions illustrate the fundamental positioning gap: Dify is a
**finished product targeting developers and SMB self-host**, with a
multi-tenant-restricted license that explicitly carves out enterprise
SaaS as commercial territory. spring-ai-ascend is a **kernel-first SPI
library** for enterprises building their own multi-tenant platforms on
sovereign hardware. The two projects are not substitutes — they sit at
opposite ends of the build-vs-buy spectrum. Where Dify excels (RAG
adapter catalogue, console UX, plugin marketplace) is where
spring-ai-ascend deliberately ships a thinner surface in exchange for
audit-grade governance and JVM-native enterprise integration.
