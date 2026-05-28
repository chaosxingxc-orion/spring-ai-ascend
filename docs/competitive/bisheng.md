---
analysis_id: COMPETITIVE-BISHENG
governance_infra: true
analysis_date: 2026-05-28
analyst: spring-ai-ascend Phase C Tranche 1
repo_clone_at: D:\ai-research\agent-platforms-survey\bisheng\
---

# Competitive Analysis: dataelement/bisheng

Source-grounded analysis of the `bisheng` repository at clone HEAD, backend
version `2.4.0` declared in `src/backend/pyproject.toml:1-5`. Conducted per
the C.2 ten-point template. Bisheng is the deepest enterprise-vertical
visual platform in this tranche, marketed explicitly at "industry leading
organizations and Fortune 500 companies" (`README.md:31`) by Beijing-based
DataElem Inc.

## 1. Tagline & positioning

The README opens with a national-pride frame (`README.md:1-3`):

> "Proudly made by Chinese，May we, like the creators of Deepseek and Black
> Myth: Wukong, bring more wonder and greatness to the world."

And the elevator pitch (`README.md:31`):

> "BISHENG is an open LLM application devops platform, focusing on enterprise
> scenarios. It has been used by a large number of industry leading
> organizations and Fortune 500 companies."

Three product surfaces ship together (`README.md:36-55`): **Lingsight**
(general-purpose agent with `AGL` — Agent Guidance Language — embedding
domain-expert preferences), **BISHENG Workflow** (the visual flow editor,
self-described as "independent and comprehensive application orchestration
framework" — `README.md:40-44`), and the **high-precision document parsing
model** ("trained on a vast amount of high-quality data accumulated over
past 5 years" — `README.md:54`) covering printed/handwritten text, tables,
layout, and Chinese seals.

Positioning is **enterprise-vertical product**, with explicit emphasis on
regulated-industry use cases enumerated at `README.md:47`: "Document review,
fixed-layout report generation, multi-agent collaboration, policy update
comparison, support ticket assistance, customer service assistance, meeting
minutes generation, resume screening, call record analysis, unstructured
data governance, knowledge mining, data analysis". Enterprise feature claims
include "security review, RBAC, user group management, traffic control by
group, SSO/LDAP, vulnerability scanning and patching, high availability"
(`README.md:51`). The framing is decisively **Chinese-finance / Chinese-
SOE-friendly**, with database support enumerated at
`src/backend/bisheng/workflow/nodes/agent/agent.py:30-46` explicitly listing
**MySQL, DB2, PostgreSQL, GaussDB, Oracle** — GaussDB being Huawei's
flagship enterprise database. The company URL `dataelem.com` and the README
attribution to LangChain + Langflow + LLaMA-Factory + unstructured.io
(`README.md:89`) confirm a LangChain-lineage stack with a heavy
document-parsing differentiator.

## 2. Architecture skeleton

Top-level layout is two directories: `src/backend/` (Python FastAPI +
LangGraph) and `src/frontend/` (React + nginx, with `client/` + `platform/`
sub-apps). Docker is the deployment unit, with `docker-compose.yml` (root
spec) + four variant composes: `docker-compose-ft.yml` (fine-tuning),
`docker-compose-office.yml` (OnlyOffice integration),
`docker-compose-uns.yml` (unstructured-data), and a `bisheng-ft/` +
`bisheng-uns/` runtime per variant.

The backend's internal layering, from `src/backend/`:

```
bisheng/                              # Python FastAPI app
  api/                                # HTTP routes
  channel/                            # Streaming / WebSocket channels
  chat_session/                       # Per-session state
  core/                               # FastAPI bootstrap, DB engine
  database/
    models/                           # SQLModel ORM
      {assistant,flow,flow_version,group,group_resource,role,
       role_access,user_group,user_link,session,message,
       recall_chunk,report,audit_log,evaluation,template,tag,
       variable_value,invite_code,mark_*}.py
  finetune/                           # Fine-tuning orchestration
  knowledge/                          # Knowledge base / RAG
    api/endpoints/{knowledge,knowledge_space,qa}.py
    domain/{schemas,services,repositories,models}/
    rag/
      base_file_pipeline.py
      knowledge_file_pipeline.py
      preview_file_pipeline.py
      temp_file_pipeline.py
      milvus_factory.py + elasticsearch_factory.py
      pipeline/{loader,transformer,types}.py
  linsight/                           # General-purpose agent (Lingsight)
    api/router.py + domain/task_exec.py + worker.py
  llm/                                # LLM service abstraction
  mcp_manage/                         # MCP server management
  message/, share_link/, telemetry/
  tool/                               # Tool registry
  user/                               # User + group domain
  worker/                             # Async worker
  workflow/                           # Visual workflow engine
    graph/{graph_engine.py (387 lines), graph_state.py, workflow.py}
    nodes/
      agent/, code/, condition/, end/, input/, knowledge_retriever/,
      llm/, output/, qa_retriever/, rag/, report/, start/, tool/
    edges/, common/, callback/
bisheng_langchain/                    # LangChain extension library
  agents/{chatglm_functions_agent, llm_functions_agent}/
  autogen_role/
  chains/{autogen, combine_documents, conversational_retrieval,
          qa_generation, question_answering, retrieval, router}/
  chat_models/, embeddings/
  document_loaders/, gpts/
  linsight/{task,react_task,const,resource}.py
  memory/, rag/
    rag/api/{dify_rag,fastgpt_rag,openai_assistant_rag}.py  # competitor adapters!
    rag/init_retrievers/{baseline_vector,keyword,mix,smaller_chunks}.py
    rag/{bisheng_rag_chain,bisheng_rag_pipeline,bisheng_rag_pipeline_v2,
         bisheng_rag_tool}.py
```

The workflow engine is built on **LangGraph**
(`src/backend/bisheng/workflow/graph/graph_engine.py:4-7`):

```python
from langgraph.checkpoint.memory import MemorySaver
from langgraph.constants import END, START
from langgraph.graph import StateGraph
```

i.e., Bisheng's runtime is LangGraph's `StateGraph` wrapped with
Bisheng-specific `GraphState`, `EdgeManage`, and `NodeFactory` glue.

## 3. Developer experience

"Build my first agent" is a **console-driven, visual-canvas** journey
(`README.md:58-86`):

1. Hardware floor: 4 vCPU + 16 GB RAM minimum, recommended 18 vCPU + 48 GB
   (`README.md:60-65`). The recommended stack pulls Elasticsearch, Milvus,
   and OnlyOffice as dependencies — Bisheng is a *heavy* deployment.
2. `git clone … && cd bisheng/docker && docker compose -f docker-compose.yml
   -p bisheng up -d`.
3. The first registered user becomes system admin (`README.md:84`). RBAC
   is on by default (group `id=2` is the default user group per
   `src/backend/bisheng/database/models/group.py:11`).
4. The console offers **three application kinds** declared in
   `src/backend/bisheng/database/models/flow.py:31-37`:

   ```python
   class FlowType(Enum):
       ASSISTANT = 5            # Conversational assistant
       WORKFLOW = 10            # Visual flow editor
       WORKSTATION = 15         # Workstation (multi-flow workspace)
       LINSIGHT = 20            # Inspiration Mode (general agent)
       CHANNEL_ARTICLE = 25     # Channel Article AI Assistant
       KNOLEDGE_SPACE = 30      # Knowledge Space
   ```

5. For a **workflow**, the user enters the React Flow canvas where the
   palette mirrors the directory structure under
   `src/backend/bisheng/workflow/nodes/`: `start`, `end`, `input`,
   `output`, `llm`, `agent`, `code`, `condition`, `knowledge_retriever`,
   `qa_retriever`, `rag`, `report`, `tool`. The README highlights the
   visual loop / parallel / batch semantics (`README.md:44`): "drawing a
   loop forms a loop, aligning elements creates parallelism, and
   selecting multiple items enables batch processing" — meaning loops
   and parallelism are encoded as canvas topology, not as special
   primitive nodes.
6. The flow JSON is persisted in `flow.data` JSON column
   (`flow.py:54`) and a versioned snapshot in `flow_version`. Execution
   materialises a `langgraph.StateGraph` via `GraphEngine`
   (`graph_engine.py:27-71`).

There is **no first-party SDK / library path** for a developer who just
wants to embed a `BishengAgent` Python object. The visual builder is the
entry point.

## 4. Multi-tenancy & governance

Bisheng's tenancy model is **RBAC + group-scoped, NOT tenant-scoped**.

A repository-wide Grep across `src/backend/bisheng/database/models/` for
`tenant_id|workspace_id|org_id` returns **zero matches**. There is no
`Tenant` aggregate. Instead, the access-control hierarchy is:

- `User` (`user/domain/models/`)
- `Role` (`models/role.py`)
- `Group` (`models/group.py:25` — `class Group(GroupBase, table=True)`,
  `DefaultGroup = 2`)
- `UserGroup` (`models/user_group.py`) — many-to-many user↔group
- `RoleAccess` (`models/role_access.py`) — role → resource ACL
- `GroupResource` (`models/group_resource.py`) — group → resource ACL

The `RoleAccess` and `GroupResource` tables implement a flat ACL
(`AccessType` enum gates resource read/write/admin). All resources
(`Flow`, `Assistant`, `Knowledge`, etc.) carry a `user_id` creator
column (`flow.py:52`), not a `tenant_id`. The default group id is
hardcoded as `2` (`group.py:11`).

This is **multi-user single-tenant**, not multi-tenant. A Bisheng
installation has one logical workspace shared by all users, with RBAC
gating who sees which `Flow` / `Knowledge`. The README enterprise
claims of "RBAC, user group management, traffic control by group,
SSO/LDAP" are coherent with this model — they describe authorisation
*within* a single deployment, not tenant *isolation*.

Compared to spring-ai-ascend (Rule R-J.a: Postgres RLS on
`tenant_id`-bearing tables; Rule R-C.2.a: Run records require
`tenantId NOT NULL`), Bisheng's posture is fundamentally different: the
expected enterprise deployment model is **one Bisheng instance per
customer**, with the customer running their own deployment under their
own RBAC. The license (`LICENSE:1-3`, Apache 2.0 unchanged) does not
prohibit multi-tenant operation, but the data model does not support it.

There is no architectural-enforcer corpus, no governance YAML, no
gate-rule machinery, no ADR ledger. Standard Python tooling
(`pyproject.toml` declares `ruff`, `pytest`) is the only enforcement layer.

## 5. Engine pluggability

The execution engine is **LangGraph `StateGraph`**, wrapped with
Bisheng-specific glue (`workflow/graph/graph_engine.py`). Nodes are
registered via the `NodeFactory` pattern
(`workflow/nodes/node_manage.py`), with each node directory under
`workflow/nodes/` carrying:

- A `class XxxNode(BaseNode)` (e.g., `AgentNode` at
  `nodes/agent/agent.py:50`) that implements per-step execution.
- A `NodeType` enum value mapped in `common/node.py`.
- A factory registration in `node_manage.py`.

The agent abstraction inside the agent node is **LangGraph's
`create_react_agent` prebuilt** (`nodes/agent/agent.py:7`):

```python
from langgraph.prebuilt import create_react_agent
```

with two executor strategies (`agent.py:22-25`):

```python
agent_executor_dict = {
    'ReAct': 'get_react_agent_executor',
    'function call': 'get_openai_functions_agent_executor',
}
```

I.e., engine selection is a **string lookup**, not a typed registry.
The Lingsight general-purpose agent (`bisheng_langchain/linsight/task.py`,
`react_task.py`) is a parallel execution path with its own LangGraph
configuration — coexistence is by separate code paths, not by a unified
envelope.

There is no `EngineEnvelope` schema, no `EngineRegistry.resolve()`
abstraction, no typed `EngineMatchingException`. Cross-cutting policy
(rate-limit, audit, RBAC) is enforced via FastAPI middleware and
per-controller `RoleAccess` checks, not via a hook-point catalogue.

Notably, `bisheng_langchain/rag/api/` ships **adapters to competitor
RAG APIs** — `dify_rag.py`, `fastgpt_rag.py`, `openai_assistant_rag.py` —
making Bisheng a federated RAG client. The interoperability story is
ironic but pragmatic: an existing Dify or FastGPT RAG can be wrapped as a
Bisheng tool.

## 6. Evolution substrate

Bisheng has the **deepest document-parsing and RAG substrate** of the
four platforms in this tranche, plus a first-party fine-tuning module.

**RAG pipeline** (`src/backend/bisheng/knowledge/rag/`):

- `base_file_pipeline.py` — pipeline base class.
- `knowledge_file_pipeline.py` — production ingestion pipeline.
- `preview_file_pipeline.py` — pre-ingest preview for the UI.
- `temp_file_pipeline.py` — ephemeral one-shot retrieval.
- `milvus_factory.py` + `elasticsearch_factory.py` — dual-backend
  vector + lexical (Milvus + ES is the **hybrid retrieval default**).
- `pipeline/{loader,transformer,types}.py` — staged transformation.

**LangChain extension RAG** (`bisheng_langchain/rag/`):

- `init_retrievers/{baseline_vector,keyword,mix,smaller_chunks}_retriever.py`
  — four named retrieval strategies including a "smaller chunks" parent-child
  retrieval and a "mix" hybrid retriever.
- `bisheng_rag_pipeline_v2.py` — v2 RAG pipeline with reranking and key
  extraction (`extract_info.py`).
- `qa_corpus/qa_generator.py` — synthetic QA generation for evaluation.

**Document parsing**: BISHENG's headline differentiator. The README claims
"high-precision printed text, handwritten text, and rare character
recognition models, table recognition models, layout analysis models, and
seal models" trained on "5 years" of data (`README.md:54`). The
`bisheng-uns/` docker-compose variant deploys the unstructured-data parser
as a separate microservice. This is genuinely best-in-class for Chinese
document parsing — competitors typically delegate this to upstream
unstructured.io or pdfminer.

**Fine-tuning substrate** (`src/backend/bisheng/finetune/`): a first-party
fine-tuning workflow exists; the `docker-compose-ft.yml` variant deploys
`bisheng-ft/` which the README implies is a LLaMA-Factory wrapper
(`README.md:89` cites LLaMA-Factory). The `linsight/` general-agent module
plus `react_task.py` provides a trajectory-style execution model — though
trajectories are not persisted as a queryable archive analogous to
spring-ai-ascend's `agent-evolve` plane.

## 7. Deployment model + sovereign-hardware support

Deployment is **docker-compose-first**, with five distinct composes:

- `docker/docker-compose.yml` — full stack (MySQL, Redis, Milvus, ES,
  bisheng backend + frontend + nginx).
- `docker/docker-compose-ft.yml` — fine-tuning variant
  (adds `bisheng-ft/`).
- `docker/docker-compose-uns.yml` — unstructured-data parser
  (`bisheng-uns/`).
- `docker/docker-compose-office.yml` — OnlyOffice document editor
  integration.
- `docker/deploy.sh` — orchestrating script.

The full stack pulls **MySQL 8.0** (`docker-compose.yml:3-21`),
**Redis 7.0.4**, Milvus, Elasticsearch, and optionally OnlyOffice — a
**16 GB-RAM-minimum** deployment by `README.md:62`. Container images are
hosted on DataElem's registry, x86_64-default.

**Chinese-silicon footprint**: a repository-wide Grep for `Ascend|Kunpeng|
NPU|昇腾|鲲鹏` in `src/backend/` returns hits **only in tokenizer/vocab
JSON files** (`bisheng_langchain/linsight/resource/model_tokenizer/
{vocab.json, tokenizer.json}` — these are model vocabulary artefacts
containing the word "Ascending" or similar in dictionary entries) plus
LangChain prompt text mentioning "ascending order" (e.g., the React
agent prompt template at
`bisheng_langchain/gpts/prompts/react_agent_prompt.py`). There is **zero
intentional Ascend NPU or Kunpeng ARM64 support** in code.

**However**, Bisheng's `SqlAgentParams` enumerates **GaussDB** as a
supported database engine (`nodes/agent/agent.py:30-46`), which is the
strongest sovereign-database signal of any platform in this tranche.
GaussDB is Huawei's flagship distributed database product, and explicitly
naming it as a supported engine signals the deployment audience: **Chinese
SOEs and banks already running GaussDB**. There is no ARM64 build profile
or NPU model-serving adapter, but the database-engine support is a real
sovereignty bridge — partial overlap with spring-ai-ascend's
sovereign-hardware positioning, but at the database layer only.

No Helm chart in-tree (`find -name "Chart.yaml"` returns nothing).
No `deployment_plane` discriminator analogous to Rule R-I.

## 8. License + corporate sponsor

License: **Apache 2.0**, unmodified (`LICENSE:1-3`). No field-of-use
restrictions, no multi-tenant carve-out, no branding clause, no
contributor re-licensing clause. The most permissive license in this
tranche — Bisheng can be re-bundled and white-labelled freely.

Corporate sponsor: **DataElem Inc.** (`dataelem.com`, Beijing-based). The
README explicitly leans on national identity ("Proudly made by Chinese",
`README.md:1`) and the LinkedIn / acknowledgement section
(`README.md:89-99`) credits LangChain, Langflow, unstructured.io, and
LLaMA-Factory as upstream dependencies — i.e., Bisheng is the
DataElem-authored composition of mature open-source LLM tooling, not a
ground-up framework.

Backend version `2.4.0` (`src/backend/pyproject.toml:3`). License of the
backend submodule explicitly declared `Apache 2.0`
(`src/backend/pyproject.toml:6`). Latest commit not annotated on local
clone. The license posture makes Bisheng **the most embeddable** of the
four platforms reviewed — practical implication for spring-ai-ascend:
Bisheng's RAG pipeline code is legally re-bundleable if we ever need it,
though the LangChain/LangGraph runtime dependency would pull a Python
sidecar into a primarily-JVM stack.

## 9. What we LEARN

Patterns worth absorbing into spring-ai-ascend:

1. **Hybrid retrieval as the default, not an upgrade** —
   `src/backend/bisheng/knowledge/rag/{milvus_factory,elasticsearch_factory}.py`
   plus `bisheng_langchain/rag/init_retrievers/mix_retriever.py` show
   that Bisheng ships Milvus + Elasticsearch hybrid retrieval as the
   baseline, not as an optional advanced feature. Our graphmemory-starter
   should consider promoting hybrid (dense + lexical) retrieval to the
   default-on configuration rather than a posture flag.

2. **GaussDB as a first-class SQL agent target** —
   `src/backend/bisheng/workflow/nodes/agent/agent.py:30-46`'s explicit
   GaussDB enumeration in `SqlAgentParams` is concrete evidence that
   Chinese-SOE customers expect GaussDB compatibility. Our Spring Data
   R2DBC adapter list should validate GaussDB driver support and call it
   out in `docs/dfx/*.yaml`.

3. **Per-application-kind enum on the persistent record** —
   `src/backend/bisheng/database/models/flow.py:31-37` declares a
   `FlowType` enum (ASSISTANT, WORKFLOW, WORKSTATION, LINSIGHT, …) as
   the discriminator on the `Flow` table. Our `Run` aggregate currently
   carries `engine_type` for execution-time dispatch; a similar
   coarse-grained `app_kind` discriminator on the design-time
   `AgentDefinition` row would simplify console differentiation between
   chatbot vs workflow vs assistant.

4. **AGL (Agent Guidance Language) as a domain-expert authoring surface** —
   The README's reference to AGL (`README.md:37`, with a separate repo at
   `github.com/dataelement/AgentGuidanceLanguage`) suggests a DSL for
   encoding expert preferences as agent guidance. We do not need to ship a
   DSL, but the *concept* — that expert knowledge is encoded as a separate
   first-class artefact rather than ad-hoc prompts — is consistent with our
   skill-as-directory + `SKILL.md` direction.

5. **`bisheng_langchain` as a separate Python package** — the split
   between `bisheng/` (FastAPI app) and `bisheng_langchain/` (LangChain
   extensions) gives a clean boundary: the FastAPI app depends on the
   extension library, but the extension library is independently
   installable (`bisheng-langchain` on PyPI). The same separation between
   "framework SPI library" (Maven-importable) and "platform app" (Spring
   Boot deployment) is what spring-ai-ascend's BoM + starter modules
   embody.

6. **Competitor-RAG adapters in-tree** —
   `bisheng_langchain/rag/api/{dify_rag,fastgpt_rag,openai_assistant_rag}.py`
   ship adapters to Dify, FastGPT, and OpenAI Assistant APIs. Shipping a
   one-file adapter to each major competitor's RAG API is a pragmatic way
   to ease migration into your platform without forking. Worth mirroring:
   a `spring-ai-ascend-graphmemory-adapter-dify` artifact that pulls
   from a customer's existing Dify knowledge base.

7. **Document parsing as a vertically-integrated competitive moat** —
   Bisheng's `bisheng-uns/` (unstructured-data parser) is a separate
   microservice deployed via its own docker-compose variant. The lesson:
   parsing quality is a vertical investment that takes years and is not
   commodity. Our v1 stance — delegating to upstream Spring AI document
   readers — is correct, but we should expect customers to demand at
   least Chinese OCR + table extraction depth.

## 10. Where we DIFFER

Each row cites one evidence file in **each** repo.

| # | Dimension | Bisheng evidence | spring-ai-ascend evidence |
|---|-----------|------------------|---------------------------|
| 1 | **Tenant isolation** — Bisheng: no tenant model, RBAC + group-scoped only. Ascend: tenantId mandatory + RLS at storage. | `src/backend/bisheng/database/models/group.py:11,25` (Group is the scope unit) + repo-wide Grep for `tenant_id` returns zero | Rule R-J.a + `agent-service/src/main/resources/db/migration/` (RLS in every tenant migration) |
| 2 | **JVM vs Python runtime** — Bisheng: Python FastAPI + LangGraph + LangChain. Ascend: Spring-native JVM, no Python in hot path. | `src/backend/pyproject.toml:23-50` (LangChain, LangGraph, langchain-openai, langchain-milvus, ...) | `D:\chao_workspace\spring-ai-ascend\pom.xml` (no Python sidecar in agent-service hot path) |
| 3 | **Engine pluggability** — Bisheng: LangGraph `StateGraph` + string-lookup executor (`'ReAct'`/`'function call'`). Ascend: typed `EngineRegistry.resolve(envelope)`. | `src/backend/bisheng/workflow/nodes/agent/agent.py:22-25` (string-lookup) | `docs/contracts/engine-envelope.v1.yaml` + Rule R-M.a |
| 4 | **Visual canvas as developer entry** — Bisheng: console-first, React Flow canvas. Ascend: SPI library, no console v1. | `src/frontend/platform/` (React canvas) + `flow.data` JSON column at `src/backend/bisheng/database/models/flow.py:54` | `D:\chao_workspace\spring-ai-ascend\pom.xml` (no console module) |
| 5 | **Ascend+Kunpeng sovereignty** — Bisheng: GaussDB enumerated in SQL agent (database-layer sovereignty only). Ascend: NPU+ARM64 as design target across all planes. | `src/backend/bisheng/workflow/nodes/agent/agent.py:30-46` (GaussDB listed) | `D:\chao_workspace\spring-ai-ascend\README.md:3-10` + Rule R-I five-plane manifest |
| 6 | **Document-parsing depth** — Bisheng: 5-year-investment OCR/table/seal models. Ascend: delegates to upstream Spring AI document readers. | `README.md:54` + `bisheng-uns/` docker-compose variant | `spring-ai-ascend-graphmemory-starter` (upstream Spring AI delegation) |
| 7 | **License posture** — Bisheng: pure Apache 2.0, no carve-outs. Ascend: Apache 2.0. Both permissive — Bisheng is the most embeddable of the four. | `LICENSE:1-3` (pure Apache 2.0) | `D:\chao_workspace\spring-ai-ascend\LICENSE` (Apache 2.0) |
| 8 | **Multi-app-kind product** — Bisheng: 6 FlowType kinds (ASSISTANT, WORKFLOW, WORKSTATION, LINSIGHT, CHANNEL_ARTICLE, KNOLEDGE_SPACE). Ascend: single Run aggregate, engine_type discriminates. | `src/backend/bisheng/database/models/flow.py:31-37` | Rule R-C.2.a + `agent-service/.../runtime/runs/Run.java` |
| 9 | **Audit-grade Run spine** — Bisheng: `audit_log.py` table + `mark_*` rows. Ascend: Run + idempotency, durable transition history. | `src/backend/bisheng/database/models/audit_log.py` (append-only audit) | `agent-service/.../runtime/idempotency/` + Rule R-C.2.b |
| 10 | **Governance / Code-as-Contract** — Bisheng: ruff + pytest only. Ascend: 144+ gate rules + ArchUnit + ADR ledger. | `src/backend/pyproject.toml` (no architectural-enforcer config) | `D:\chao_workspace\spring-ai-ascend\CLAUDE.md` (Rule kernel index) + `gate/check_architecture_sync.sh` |

Bisheng is the closest "enterprise vertical" competitor reviewed: it
targets the same Chinese-SOE / Fortune-500 segment, ships RBAC + SSO
claims, and natively supports GaussDB. But its tenancy is single-tenant
+ RBAC, its runtime is Python-LangGraph (incompatible with a Spring-JVM
stack), and its sovereignty story stops at the SQL agent layer.
spring-ai-ascend's differentiation is precisely the orthogonal axes:
multi-tenant by design, JVM-first, NPU+ARM64 sovereignty at the runtime
plane, and governance-as-code. The two platforms occupy adjacent but
non-overlapping niches in the same buyer's RFP matrix.
