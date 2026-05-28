---
analysis_id: COMPETITIVE-OPENSPG
governance_infra: true
analysis_date: 2026-05-28
analyst: spring-ai-ascend Phase C Tranche 1
repo_clone_at: D:\ai-research\agent-platforms-survey\openspg\
---

# Competitive Analysis: OpenSPG/openspg

Source-grounded analysis at commit `ceeb3ef` (2025-06-29, tip of
`main`), Maven `groupId=com.antgroup.openspg`, version `0.0.1-
SNAPSHOT` declared in `pom.xml:18-20`. OpenSPG is **Ant Group's
knowledge-graph engine** — NOT a multi-agent platform — but it is the
relevant competitor for spring-ai-ascend's PC-005 evolution claim
because its KAG (Knowledge-Augmented Generation) paper extension is
the canonical "KG + agent" reference architecture in the Chinese
ecosystem.

## 1. Tagline & positioning

The repository's own pitch, verbatim from `README.md:7`:

> "OpenSPG is a knowledge graph engine developed by Ant Group in
> collaboration with OpenKG, based on the SPG (Semantic-enhanced
> Programmable Graph) framework, which is a summary of Ant Group's
> years of experience in constructing and applying diverse domain
> knowledge graphs in the financial scenarios."

The framework's four claimed capabilities (`README.md:22-43`):

> "* SPG-Schema semantic modeling … * SPG-Builder knowledge
> construction … * SPG-Reasoner logical rule reasoning … *
> Programmable Framework — KNext … * Cloud Adaptation Layer —
> Cloudext"

This is fundamentally a **structured knowledge engine** — schema
definitions, ETL pipelines, rule reasoning, an embedded query DSL
(KGDSL), and a pluggable cloud-adapter layer for graph store / search
engine / object store / cache. The "agent" angle is implicit, mediated
through two architectural choices:

1. **LLM-driven extraction nodes** in the builder pipeline
   (`builder/core/.../logical/LLMBasedExtractNode.java`,
   `LLMNlExtractNode.java`) — the LLM is a **node in the ETL DAG**,
   used to extract entities/relations from text into the KG.
2. **KAG** (Knowledge Augmented Generation, cited at
   `README.md:65`) — the agent integration story, but the KAG
   codebase is a **sibling repository** at OpenSPG/KAG, NOT
   included in this repo. The OpenSPG repository proper has zero
   `Agent` classes, zero ReAct loop, zero LLM-orchestration code.

Positioning vs spring-ai-ascend: OpenSPG is **knowledge-engine-first,
agent-adjacent**. Its closest analogue in spring-ai-ascend is the
`spring-ai-ascend-graphmemory-starter` — a memory substrate, not an
agent runtime. The architectural intent does not overlap with our
agent service / engine modules. Where it **does** overlap is the
graph-memory layer: OpenSPG's KGDSL + LPG semantics are the depth
target a serious "graph memory" feature must reach to be credible.

## 2. Architecture skeleton

The Maven reactor (`pom.xml:30-36`) declares five top-level modules:

```
openspg-parent (pom)
├── common/util             # shared utilities, JSON/string helpers
├── server                  # HTTP server + REST controllers + DAO
│   ├── api/                # facade, http-client, http-server
│   ├── biz/                # use-case orchestrators
│   ├── common/             # model + service + util
│   ├── core/               # reasoner + scheduler + schema (core)
│   └── infra/              # dao (MyBatis), release, test
├── reasoner                # KGDSL parser + lube logical/physical
│   ├── catalog/, common/
│   ├── kgdsl-parser/       # ANTLR4 grammar at KGDSL.g4
│   ├── lube-api/, lube-logical/, lube-physical/
│   ├── runner/, udf/, warehouse/
├── builder                 # ETL pipeline (LLM-aware extract nodes)
│   ├── core/ (LogicalPlan, PhysicalPlan, processors)
│   ├── model/ (pipeline config DTOs)
│   ├── runner/local/
│   └── testdata/
└── cloudext                # SPI: cloud-pluggable adapters
    ├── interface/          # SPIs: cache, computing-engine, graph-store,
    │                       #        object-storage, search-engine
    └── impl/               # implementations: redis, neo4j, tugraph,
                            # minio, oss, elasticsearch
```

Code surface: **1384 `.java` files** + **167 `.scala` files**
(verified by `find -name "*.{java,scala}" | wc -l`). The Scala
content is concentrated in `reasoner/` (the KGDSL execution path).
HTTP controllers under `server/api/http-server/.../openapi/`:
**BuilderController, ConceptController, ConceptInstanceController,
DataSourceController, GraphController, ProjectController,
QueryController, ReasonController, RetrievalController,
SamplingController, SchedulerController, SchemaController,
SearchController, SearchEngineController, TenantController** — 15
controllers, none of them "AgentController" or similar.

**Counterpart mapping to spring-ai-ascend**:

| spring-ai-ascend                  | OpenSPG counterpart                                | Notes |
|-----------------------------------|----------------------------------------------------|-------|
| `agent-service`                   | (none — no agent loop)                             | OpenSPG is a KG engine, not an agent platform |
| `agent-bus`                       | (none — synchronous REST + scheduled jobs)         | Scheduler exists but no event-bus discipline |
| `agent-execution-engine`          | `builder/core/` ETL pipeline (LogicalPlan/PhysicalPlan) | Pipeline executor, not agent executor |
| `agent-middleware`                | `server/infra/dao/` (MyBatis) + `cloudext/impl/`   | DAO + cloud adapters |
| `agent-client`                    | (none — HTTP REST only via `server/api/http-server/`) | No client SDK |
| `agent-evolve`                    | (closest analogue) `builder/core/` knowledge construction | KG ingestion ≠ agent evolution; same conceptual layer |
| `spring-ai-ascend-graphmemory-starter` | **The entire repo** is the structural target | OpenSPG is what a serious graph-memory substrate looks like |

The `cloudext` module is the most architecturally interesting surface
for us: it declares clean SPIs at `cloudext/interface/{cache,
computing-engine, graph-store, object-storage, search-engine}/` with
swappable impls under `cloudext/impl/{cache/redis, graph-store/neo4j
+ tugraph, object-storage/minio + oss, search-engine/elasticsearch +
neo4j}`. This is **exactly** the structural pattern we want for our
own memory substrate's pluggable backends.

## 3. Developer experience

OpenSPG is a **Docker-Compose-first product**, not a library. The
quickstart (`README.md:46-52`) points to Yuque-hosted docs:
"[Install OpenSPG](https://openspg.yuque.com/...)" — there is no
README-embedded code snippet. The `dev/release/docker-compose.yml`
(lines 1-86) wires four services: `server` (Spring Boot Sofaboot,
port 8887), MySQL, Neo4j (with APOC plugin), and MinIO. The server
image is `spg-registry.cn-hangzhou.cr.aliyuncs.com/spg/openspg-
server:latest` — a private Alibaba Cloud registry path. Bootstrap
command line carries inline arguments configuring the graph store
URL pointing at the in-compose Neo4j instance
(`docker-compose.yml:22-30`).

A developer's interaction surface is:

1. **REST API** under `server/api/http-server/.../openapi/` — 15
   controllers covering schema (define ontology), builder (run ETL
   jobs), reason (run KGDSL queries), query/retrieval/search,
   scheduler (cron-like job scheduling), project/tenant management.
2. **KGDSL** — a custom domain-specific query language for the KG,
   grammar declared in
   `reasoner/kgdsl-parser/src/main/antlr4/com/antgroup/openspg/
   reasoner/KGDSL.g4` (60+ lines, ANTLR4). Supports `GraphStruture`
   blocks, `Rule` blocks, `Action` blocks, and a `Define` predicated
   construct (KGDSL.g4:14-50). This is OpenSPG's most distinctive
   developer surface.
3. **Schema definition** — `ConceptController` + `SchemaController`
   let users declare entity types and properties via HTTP API or
   loaded SPG schema files.
4. **Python builder runner** — `dev/release/python/Dockerfile`
   ships a Python sidecar that hosts the LLM-extraction nodes;
   model integration is Python-side (the JVM-side `LLMBasedExtract
   Node.java` is a thin wrapper that delegates to Python via
   `builder.model.execute.num=20` worker pool, see
   `docker-compose.yml:27`).

There is no Spring-Boot-style auto-configuration, no posture knob,
no "hello world" agent example. The expected onboarding path is:
deploy → log into the web UI → define schema → run builder jobs →
query via KGDSL. This is the same "deploy-then-click" UX shape as
Coze Studio, but for KG construction instead of agent canvas.

## 4. Multi-tenancy & governance

OpenSPG has **a real `Tenant` model** — distinct from Coze's
`SpaceID` column convention and from AgentScope's `user_id` keying.
The class `server/common/model/src/main/java/com/antgroup/openspg/
server/common/model/tenant/Tenant.java:18-19` declares with verbatim
intent:

> "Tenant usually stands for a department, or a team, data in
> different tenant is isolated."
>
> ```java
> public class Tenant extends BaseModel {
>   private final Long id;
>   private final String name;
>   private final String description;
> }
> ```

And `Project` is **explicitly scoped under a tenant** — see
`Project.java:42-45`:

> ```java
> /** The tenant id that project belong to. */
> private final Long tenantId;
> ```

with the class-level comment "Namespace unit for department manager
self schema, the schema elements such as entityType or property
between Project is isolated" (`Project.java:18-22`). The full
hierarchy is `Tenant → Project → Namespace → Schema/Entity`, with
`namespace: String` carrying the schema-isolation key (`Project.java:
36`). HTTP-edge controllers (`TenantController.java`,
`ProjectController.java`) gate access by tenant/project ID.

What's present **architecturally**:

- `TenantManager` (`biz/common/.../TenantManagerImpl.java`) +
  `TenantRepository` SPI in `common/service/.../tenant/
  TenantRepository.java`.
- `ProjectManagerImpl` + `ProjectServiceImpl` (`common/service/.../
  project/impl/ProjectServiceImpl.java:34-77`) — the
  project query path actively resolves project-scoped config and
  hydrates vectorizer model info per tenant project.
- MyBatis mappers `ProjectDOMapper.xml`, `TenantConvertor.java`,
  `ProjectConvertor.java` carry the persistence layer.

What's **NOT** present:

- **No Row-Level Security migrations** — schema lives in MyBatis
  XML mappers, not Flyway-with-CREATE-POLICY. Tenant isolation is
  WHERE-clause-based, the same shape as Coze.
- **No posture-aware fail-closed startup** — the docker-compose
  bootstrap passes JDBC credentials as CLI args (`docker-compose
  .yml:25-26`).
- **No idempotency spine on jobs** — `SchedulerInstance` /
  `SchedulerJob` / `SchedulerTask` carry `projectId` but no
  idempotency key in their mapper XML.

For spring-ai-ascend, OpenSPG's tenant model is **closer in
architectural intent** than SAA, AgentScope, or Coze — it explicitly
names `Tenant`, declares hierarchical scoping (Tenant → Project),
and ships dedicated REST controllers for tenant administration. The
Java-class evidence is at the same conceptual level as our Rule R-J
contract.

## 5. Engine pluggability

There is **no agent engine** — OpenSPG dispatches **KG operations**,
not agent runs. The substitutable engines are at the **storage and
compute** layer, exposed through the `cloudext` SPI:

- `cloudext/interface/graph-store/` — `GraphStoreClient` SPI.
  Implementations: `cloudext/impl/graph-store/neo4j/`,
  `cloudext/impl/graph-store/tugraph/`.
- `cloudext/interface/search-engine/` — search backend SPI.
  Implementations: `cloudext/impl/search-engine/elasticsearch/`,
  `cloudext/impl/search-engine/neo4j/` (Neo4j-as-search).
- `cloudext/interface/object-storage/` — blob storage SPI.
  Implementations: `cloudext/impl/object-storage/minio/`,
  `cloudext/impl/object-storage/oss/` (Alibaba Cloud OSS).
- `cloudext/interface/cache/` — cache SPI.
  Implementation: `cloudext/impl/cache/redis/`.
- `cloudext/interface/computing-engine/` — compute backend SPI for
  the reasoner.

The ETL pipeline is graph-of-nodes (`LogicalPlan` →
`PhysicalPlan`). Builder node catalogue from `builder/core/src/
main/java/com/antgroup/openspg/builder/core/logical/`:
`BuilderIndexNode`, `CsvSourceNode`, `StringSourceNode`,
`ParagraphSplitNode`, `LLMBasedExtractNode`, `LLMNlExtractNode`,
`UserDefinedExtractNode`, `ExtractPostProcessorNode`,
`SPGTypeMappingNode`, `RelationMappingNode`, `GraphStoreSinkNode`,
`Neo4jSinkNode`, `PythonNode`. The LLM is one of several extraction
options, dispatched through `LLMBasedExtractProcessor.java` in the
physical-plan tier.

Engine selection mechanism: each `BaseLogicalNode<T>` carries a
`NodeTypeEnum` discriminator (`LLMBasedExtractNode.java:22`,
`super(id, name, NodeTypeEnum.LLM_BASED_EXTRACT, nodeConfig)`). The
runner pattern-matches on the enum to dispatch — structurally
similar to spring-ai-ascend's pre-Rule-R-M.a state (pre-EngineRegistry)
where `pattern-matching on ExecutorDefinition subtypes` was the
default. There's no envelope, no `engine_type` discriminator at the
record level, no `EngineMatchingException`.

The KGDSL reasoner is a **second engine**: parsed by ANTLR4 grammar
(`reasoner/kgdsl-parser/src/main/antlr4/.../KGDSL.g4`), planned by
the `lube-logical` / `lube-physical` tiers, executed against the
cloudext graph-store backend.

## 6. Evolution substrate

This is OpenSPG's strongest dimension and the reason it matters to
spring-ai-ascend's PC-005 evolution claim. The repository ships
**knowledge construction as a first-class substrate** — the
"evolution" framing in OpenSPG's vocabulary literally appears in the
SPG framework intent: "supporting the construction of knowledge
graphs and the continuous iterative evolution of incomplete data
states in industrial-level scenarios" (`README.md:13`).

Concrete elements:

1. **Schema evolution** — `server/core/schema/{model,service}/`
   declares schema-level operations including `AddPropertyOperation
   .java`, `AlterEdgeTypeOperation.java` (`cloudext/interface/graph-
   store/.../lpg/schema/operation/`). Schema is a versioned,
   mutable surface — a real evolution affordance.
2. **Continuous knowledge construction** — the builder pipeline
   (`builder/core/`) supports incremental ingestion. LLM extraction
   nodes feed structured assertions back into the KG.
3. **KGDSL rule reasoning** — declarative rules can derive new
   facts from existing ones; the rule semantics (`KGDSL.g4:38-60`)
   are first-class language constructs (`base_rule_define`,
   `base_predicated_define`).
4. **Concept layer** — `ConceptController` + `ConceptInstance
   Controller` distinguish *concepts* (semantic types with logical
   rules) from *entities* (concrete instances).
5. **KAG (sibling repo)** — `README.md:65` cites the KAG paper
   (arxiv 2409.13731), which extends OpenSPG with retrieval-
   augmented generation. The agent ↔ KG bridge lives in the sibling
   KAG repository, not in this one.

The architectural shape is **knowledge as substrate, agent as
consumer** — OpenSPG provides the structured-knowledge backbone over
which an agent (KAG) operates. spring-ai-ascend's intent is the
opposite shape: **agent as substrate, knowledge as consumer**.

For PC-005, the implication is that our `spring-ai-ascend-
graphmemory-starter` should:

- Declare a **schema surface** (entity types, properties, edge
  types) — borrow OpenSPG's LPG / SPG approach.
- Support **incremental ingestion** with LLM-driven extraction —
  borrow the `LLMBasedExtractNode` pattern as a `MemoryWriteHook`.
- Ship **at least one KGDSL-equivalent declarative query surface** —
  KGDSL is overkill at v1, but a Cypher-subset or SPARQL-subset
  declarative path is the credible target.
- Treat **schema versions** as first-class — every schema mutation
  should be a Flyway-style migration with a downgrade path.

OpenSPG does NOT compete with us at the agent runtime; it sets the
**credibility bar** for graph-memory depth. If our memory layer ships
only a flat vector store, we lose against any reviewer who has read
OpenSPG.

## 7. Deployment model + sovereign-hardware support

Deployment is **Docker-Compose** centric, with explicit cloud-vendor
ties. `dev/release/docker-compose.yml:1-86` composes four services:

- `openspg-server`: `spg-registry.cn-hangzhou.cr.aliyuncs.com/spg/
  openspg-server:latest` (Alibaba Cloud Container Registry,
  Hangzhou region) — JVM heap 2-8 GB, Sofaboot fat jar.
- `openspg-mysql`: same registry namespace, MySQL 8 with utf8mb4.
- `openspg-neo4j`: Neo4j with APOC + GDS plugins, 4 GB heap, 1 GB
  pagecache.
- `openspg-minio`: MinIO S3-compatible object store.

The container registry namespace `spg-registry.cn-hangzhou.cr.
aliyuncs.com` is **Alibaba Cloud Hangzhou** — same cloud as SAA,
AgentScope, and OpenSPG's parent Ant Group's primary infrastructure
provider. There is also a `docker-compose-west.yml` variant
(`dev/release/docker-compose-west.yml`) for non-China-region image
mirrors.

**No Helm chart** — the repo ships only docker-compose. No
Kubernetes manifests under `dev/`.

**No Chinese-silicon support.** A repository-wide grep for
`Ascend|Kunpeng|昇腾|鲲鹏` returns zero matches. The JVM target is
Java 1.8 (`pom.xml:46`, `<java.version>1.8</java.version>`) — older
than spring-ai-ascend's Java 21 LTS baseline. Scala 2.11 / 2.12
(`pom.xml:53-54`) is also older. The Neo4j base image is x86_64
generic Linux. No ARM64-specific build profile, no NPU adapter.

For spring-ai-ascend, OpenSPG's deployment shape is **less mature**
than Coze Studio's (no Helm), **more cloud-tied** than AgentScope or
SAA (private Aliyun registry is the default path), and **older Java
baseline** (1.8 vs our 21). The deployment dimension is not where
OpenSPG sets the bar.

## 8. License + corporate sponsor

License: **Apache 2.0** (`LICENSE` + `pom.xml:23-27` declaration:
`<name>Apache 2.0 License</name>` with `<distribution>repo</
distribution>`). Every Java file carries the boilerplate "Copyright
2023 OpenSPG Authors. Licensed under the Apache License, Version
2.0" (e.g., `Tenant.java:1-12`, `Project.java:1-12`).

Corporate sponsor: **Ant Group** (Alibaba's financial-services
affiliate) — declared in `README.md:7` ("knowledge graph engine
developed by Ant Group in collaboration with OpenKG"). The core team
list (`README.md:87`) names 19 Ant Group / academic researchers
including Lei Liang (lead), Mengshu Sun, Wen Zhang (Zhejiang
University), Huajun Chen (Zhejiang University), and Jun Zhou. The
project is co-developed with **OpenKG** (the Chinese open-knowledge-
graph academic consortium) — a research-and-industry hybrid
governance model unusual among the other competitors in this
analysis.

Latest commit on `main`: `ceeb3ef549df79ca4c4878e7ff452c73584991f3`
dated **2025-06-29** ("fix(all): version 0.8 (#572)"). The 11-month
commit lag (vs the others' 2026-04..2026-05 tips) signals lower
active churn — OpenSPG is **stable-but-slow**, not under continuous
heavy development.

For spring-ai-ascend, the Ant Group sponsor matters less than for
the Alibaba-cloud-aligned competitors (SAA, AgentScope, Coze): Ant
Group's gravity well is **financial-services KG**, not agent-cloud,
and OpenSPG's competitive footprint with spring-ai-ascend is narrow
(graph-memory substrate only). It's the only competitor in this
tranche that is **complementary** rather than substitutive.

## 9. What we LEARN

Patterns worth absorbing into spring-ai-ascend, with concrete file
paths in OpenSPG:

1. **Tenant → Project → Namespace hierarchy as the right scoping
   shape** — `server/common/model/.../tenant/Tenant.java:18-19` +
   `project/Project.java:18-22,42-45`. spring-ai-ascend's Run aggregate
   has `tenantId` (Rule R-C.2.a) but no project-level sub-scope.
   Adding `projectId` as a sibling scope (so a tenant can carve up
   schema/agent isolation by project) is a low-cost, high-value
   structural improvement. OpenSPG's `Project.namespace` field
   doubles as the schema-isolation key — that's a useful idea for
   a future `agent-evolve` schema isolation surface.

2. **The `cloudext` SPI pattern: interface module + per-backend impl
   modules** — `cloudext/interface/{cache, computing-engine, graph-
   store, object-storage, search-engine}/` declares the SPIs; each
   has its own Maven submodule. Implementations live under
   `cloudext/impl/<spi>/<vendor>/`, also per-Maven-module. Our
   `agent-middleware` should adopt this shape — split into
   `agent-middleware-spi` + `agent-middleware-postgres` + `agent-
   middleware-redis` rather than one fat middleware module.

3. **KGDSL as a structural reference for declarative agent-memory
   queries** — `reasoner/kgdsl-parser/src/main/antlr4/.../KGDSL.g4`
   declares an ANTLR4 grammar with `GraphStruture`, `Rule`,
   `Action`, `Define` blocks. For our graph-memory layer, even a
   tiny declarative query surface (a Cypher subset or a
   property-graph selector) is more valuable than a Java fluent API.

4. **Schema-evolution operations as first-class** — `cloudext/
   interface/graph-store/.../lpg/schema/operation/{AddProperty,
   AlterEdgeType, ...}Operation.java`. The schema isn't just a
   YAML; it's a stream of typed mutation operations that can be
   replayed for evolution. We should treat memory schema the same
   way.

5. **LLMBasedExtractNode as the right shape for "AI in the
   pipeline"** — `builder/core/.../LLMBasedExtractNode.java` is a
   structural node in an ETL DAG that *uses* an LLM to extract
   structured facts. It carries an `LLMBasedExtractNodeConfig`
   declaring which model + which prompt. Our `agent-evolve` Python
   plane can borrow the same shape — LLM as a node in a typed graph,
   not as a free-floating service.

6. **Separating logical / physical plan tiers** — `builder/core/
   logical/` declares `BaseLogicalNode`, `LogicalPlan` etc.;
   `builder/core/physical/` declares the same in compiled form.
   The two-tier compile-then-execute split mirrors classical
   database engineering. Our `agent-execution-engine` could benefit
   from the same logical/physical-plan distinction at the workflow
   tier.

7. **Co-development with academic consortium (OpenKG)** —
   `README.md:7,16` cites the joint Ant Group + OpenKG white paper.
   Co-publishing with academic groups raises credibility for
   research-leaning reviewers (HKUST, Tsinghua). For spring-ai-
   ascend, the equivalent move would be co-publishing the L0/L1
   architecture with Huawei research partners.

## 10. Where we DIFFER

OpenSPG is the only **complementary** (rather than substitutive)
competitor in this tranche. The "DIFFER" table therefore captures
**non-overlap** as the headline, with selective overlap rows.

| # | Dimension | OpenSPG evidence | spring-ai-ascend evidence |
|---|-----------|------------------|---------------------------|
| 1 | **Primary intent** — OpenSPG: KG construction + reasoning engine. Ascend: enterprise agent runtime. | `README.md:7,20` ("knowledge graph engine ... explicit semantic representations, logical rule definitions, operator frameworks") | `D:\chao_workspace\spring-ai-ascend\README.md:1-20` |
| 2 | **Agent surface presence** — OpenSPG: none in this repo; agent integration lives in sibling KAG repo. Ascend: dedicated `agent-service` + `agent-execution-engine`. | `find . -name "*Agent*"` returns zero business hits in openspg | `D:\chao_workspace\spring-ai-ascend\agent-service\pom.xml` |
| 3 | **Tenant scoping depth** — OpenSPG: real `Tenant → Project → Namespace` Java hierarchy (better than Coze/SAA/AgentScope/LangBot). Ascend: `tenantId` only at v1; we should LEARN from OpenSPG here. | `server/common/model/.../tenant/Tenant.java:18-19` + `Project.java:42-45` | Rule R-J.a (no projectId sub-scope yet) |
| 4 | **Storage isolation enforcement** — OpenSPG: WHERE-clause-based via MyBatis mappers, no RLS. Ascend: RLS-in-migration enforcement. | `server/infra/dao/src/main/resources/mapper/ProjectDOMapper.xml` (WHERE tenant/project filters in XML) | Rule R-J.a (CREATE POLICY in Flyway migration) |
| 5 | **Engine pluggability for compute** — OpenSPG: `cloudext` SPI lets graph-store/search-engine/object-store/cache backends swap. Ascend: `EngineRegistry` lets execution engines swap. | `cloudext/interface/graph-store/` SPI + `cloudext/impl/{neo4j,tugraph}/` | Rule R-M.a/.b |
| 6 | **Sovereign hardware** — OpenSPG: Java 1.8, Aliyun-Hangzhou private registry, x86 Neo4j. Ascend: Java 21, Ascend NPU + Kunpeng ARM64. | `pom.xml:46` (java.version 1.8) + `dev/release/docker-compose.yml:5` (spg-registry.cn-hangzhou.cr.aliyuncs.com) | `D:\chao_workspace\spring-ai-ascend\README.md:3-10` |
| 7 | **Evolution substrate depth** — OpenSPG: industry-grade KG with KGDSL + schema evolution. Ascend: graphmemory-starter, much shallower today. Strict comparison: we are BEHIND OpenSPG. | `reasoner/kgdsl-parser/.../KGDSL.g4` + `builder/core/logical/` | `spring-ai-ascend-graphmemory-starter` (single-starter, no DSL) |
| 8 | **Declarative query surface** — OpenSPG: KGDSL custom DSL. Ascend: programmatic API, no declarative query yet. | `KGDSL.g4:14-60` (ANTLR4 grammar with `GraphStruture`/`Rule`/`Action` blocks) | (none — no DSL grammar in our memory layer) |
| 9 | **License + supplier posture** — OpenSPG: Ant Group + OpenKG; the corporate gravity is financial-services KG, not agent cloud. Ascend: complementary, not substitutive — we could in principle adapt OpenSPG as a memory backend. | `pom.xml:18` (groupId `com.antgroup.openspg`) | (project memory: `feedback_saa_competitor.md` does not cover Ant Group) |
| 10 | **Governance / Code-as-Contract** — OpenSPG: Spotless + Scalastyle (`scalastyle-config.xml`); no architectural enforcers, no ADRs, no governance ledger. Ascend: 144+ gate rules + ArchUnit. | `scalastyle-config.xml` + `pom.xml:56` (`spotless.version`) | `D:\chao_workspace\spring-ai-ascend\CLAUDE.md` + `gate/check_architecture_sync.sh` |

OpenSPG is **the credibility bar for spring-ai-ascend's graph-memory
substrate** — not a competitor for the agent runtime. The biggest
takeaway is structural: the `Tenant → Project → Namespace`
hierarchy in `Tenant.java` + `Project.java` is a low-cost
improvement to our current `tenantId`-only scoping. The KGDSL grammar
is the long-horizon target for any declarative graph-memory query
surface we eventually ship.
