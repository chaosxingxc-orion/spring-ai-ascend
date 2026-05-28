---
analysis_id: COMPETITIVE-METAGPT
governance_infra: true
analysis_date: 2026-05-28
analyst: spring-ai-ascend Phase C Tranche 2
repo_clone_at: D:\ai-research\agent-platforms-survey\MetaGPT\
---

# Competitive Analysis: geekan/MetaGPT

Source-grounded analysis at commit `11cdf46` (2026-01-21, tip of
`main`, "Merge pull request #1897 from Ruyuan37/windows_terminal_adaptation").
The README's identifying tagline at `README.md:4`:

> "**MetaGPT: The Multi-Agent Framework** — Assign different roles to
> GPTs to form a collaborative entity for complex tasks."

MetaGPT pioneered the **software-company-as-multi-agent-system**
pattern — agents with roles (Product Manager, Architect, Project
Manager, Engineer, QA Engineer) collaborating via standard operating
procedures (SOPs). The ICLR 2025 paper "AFlow: Automating Agentic
Workflow Generation" (`README.md:32`) accepted as **oral presentation
(top 1.8%)** and the commercial spinoff `mgx.dev` validate the
research-to-production traction.

## 1. Tagline & positioning

The "Software Company as Multi-Agent System" framing
(`README.md:42`) is the canonical pitch. The README emphasises three
positioning lines:

- "MetaGPT takes a one-line requirement as input and outputs user
  stories / competitive analysis / requirements / data structures /
  APIs / documents, etc.";
- agents follow **SOPs (Standard Operating Procedures)** — defined in
  `team.py:33-34` and `roles/role.py` headers;
- the architecture is **role-driven multi-agent** — each role has a
  goal, a set of `Action`s, an `Environment` to publish/subscribe
  messages, and a `Memory` buffer.

Positioning: **structured-output-first, SOP-bound, software-engineering-
specialised multi-agent framework**. The "one-line requirement → full
project" demo is the canonical use case, with role-set extensions for
domains like werewolf simulation (`environment/werewolf/`) and
Stanford-town social agents (`environment/stanford_town/`). License
is **MIT** (`LICENSE:1-3`, `Copyright (c) 2024 Chenglin Wu`).
Corporate sponsor: **DeepWisdom** — the company behind MGX (the
commercial cousin at `mgx.dev`, referenced at `README.md:28-32`). The
README explicitly states "Today we are officially launching our
natural language programming product" (`README.md:32`) — the OSS
funnels into the commercial mgx.dev product.

## 2. Architecture skeleton

The kernel `metagpt/` declares 30+ modules:

```
actions/       # Action classes (DesignAPI, WriteCode, RunCode, …)
base/          # base abstractions (BaseRole, BaseEnvironment, …)
environment/   # Environment + per-domain envs (mgx, werewolf, stanford_town, software)
ext/, exp_pool/, learn/   # extensions / experiment pool / learning
provider/      # LLM provider adapters (incl. HumanProvider)
rag/           # RAG primitives
roles/         # 14 concrete Role implementations
schema.py      # Message + Task + AIMessage + MessageQueue
strategy/      # Planner + Selector strategies
team.py        # Team aggregate
document.py + document_store/   # document persistence
memory/        # short/long-term memory
config2.py, configs/            # configuration
context.py, context_mixin.py    # execution context
software_company.py             # the canonical "software company" entry point
startup.py                      # CLI entry
subscription.py                 # SubscriptionRunner
strategy/planner.py             # planning logic
```

The **central abstractions are `Team`** (`team.py:33`,
`class Team(BaseModel)`) and **`Role`** (`roles/role.py:38`, the
canonical class with 592 lines).

A `Team` carries:
- `env: Optional[Environment]` — the message bus + role registry;
- `investment: float = 10.0` — the LLM-token budget cap (an explicit
  cost guard, unique in this Tranche);
- `idea: str` — the user's one-line requirement.

A `Role` carries (per the file header and `_observe`/`_act`
discipline):
- `subscribed_tags` — typed message-routing filters;
- `rc.msg_buffer: MessageQueue` — per-role private message buffer
  (post-RFC-116 refactor);
- `actions: list[Action]` — the role's behavior contracts;
- `_observe()` (line ~250) → `_think()` (line ~310) → `_act()` (line
  ~360) — the classic ReAct loop split into three phases.

The `Environment` (`environment/__init__.py`) declares
`Environment`, `WerewolfEnv`, `StanfordTownEnv`, `SoftwareEnv`, and
`MGXEnv` (from `environment/mgx/mgx_env.py`) — multiple domain
environments, each a different message-routing topology.

## 3. Developer experience

The canonical first-run from `README.md` examples:

```python
from metagpt.software_company import generate_repo
repo = await generate_repo("write a 2048 game")
```

i.e., a single async call producing a complete code repository.
Behind the scenes, `software_company.py` instantiates a `Team` with
`ProductManager`, `Architect`, `ProjectManager`, `Engineer`, and
`QaEngineer` roles, runs the SOP, and persists outputs to
`workspace/`. For custom team composition the developer constructs a
`Team` directly:

```python
team = Team()
team.hire([Role1(), Role2(), Role3()])
team.invest(10.0)
team.run_project("idea")
await team.run()
```

Configuration is via `config2.py` + `configs/` directory — a YAML
file (`~/.metagpt/config2.yaml`) with `llm.api_type`, `llm.model`,
`llm.api_key`, `llm.base_url`. This is **YAML-first** like XAgent
(and unlike LangChain/CrewAI's env-var pattern). The `HumanProvider`
under `provider/` is a clever shape: a human-in-the-loop provider
exposes the same `chat_completion`-style API as an LLM, so swapping
to a human reviewer is a config switch, not a code rewrite.

There is no posture (dev/prod) split, no fail-closed boot. The
project documentation is dense and structured — `docs/` carries
multilingual READMEs, RFCs (`docs/.../RFC.md`), and a `docs/NEWS.md`
old-news roll-up.

## 4. Multi-tenancy & governance

**There is no tenant model.** A repo-wide grep for `tenant` matches
only `docs/user_guide/en/modules/tooling/mcp.md` — a docs reference
unrelated to framework tenancy. The `Team`, `Role`, `Environment`,
`Memory`, `Message` aggregates carry no `tenant_id` field. The
`Message` schema (`schema.py:1-50`) uses `route_from` / `route_to` /
`cause_by` for message addressing — agent-to-agent routing, not
tenant scoping.

Governance surfaces are partly present in a research-discipline form
but not enterprise-grade:

- **`investment: float`** on `Team` (`team.py:54`) — a per-team
  token-budget cap that throws `NoMoneyException` when exhausted
  (imported at `team.py:24`). This is a real fail-fast governance
  signal — and the only explicit cost guard in this Tranche.
- **`Memory.archive(self)`** + serialisation (`team.py:1-15` file
  header references "archiving operation after completing the
  project, as specified in Section 2.2.3.3 of RFC 135") — the
  project documents its own internal RFCs for run-completion
  artefact persistence.
- **Per-role subscribed_tags** (`roles/role.py:` header) — typed
  message-filter ergonomics that prevent cross-role message leakage.

There is no posture split, no audit MDC, no idempotency spine, no
RLS, no recurring-defect ledger. The `NoMoneyException` + RFC-driven
discipline is the closest MetaGPT comes to spring-ai-ascend's
governance posture — it shows research-grade discipline but not
enterprise-grade enforcement. By contrast, spring-ai-ascend enforces
tenant isolation at the storage engine + HTTP edge + Run record
level. MetaGPT's tenancy is entirely a calling-application problem.

## 5. Engine pluggability

The framework's engine shape is **role-based multi-agent message
passing through an Environment**. There is no `engine_type`
discriminator; instead, the pluggability surfaces are:

1. **Multiple `Environment` implementations** —
   `Environment` (base), `MGXEnv` (default in `team.py:54-58`),
   `SoftwareEnv`, `WerewolfEnv`, `StanfordTownEnv`. Each is a
   different message-routing + role-orchestration topology. Adding
   a domain means adding a new `Environment` subclass.

2. **Role + Action composition** — every role declares a list of
   `Action`s in its constructor; actions are typed
   `Action(BaseModel)` subclasses with an `async def run(self, ...)`
   method. The actions catalog under `actions/` (24 modules
   including `design_api.py`, `write_code.py`, `run_code.py`,
   `debug_error.py`, `analyze_requirements.py`, `extract_readme.py`,
   `generate_questions.py`, `invoice_ocr.py`) is the framework's
   prebuilt-skill library.

3. **`ActionNode` graph** (`actions/action_node.py`) — typed
   declarative action specs with input/output schemas. The closest
   analogue to spring-ai-ascend's contract-first surface in this
   Tranche.

4. **`Planner` strategies** (`strategy/planner.py`) — pluggable
   planning algorithms attached to roles.

5. **`Provider` adapters** including the human-in-the-loop
   `HumanProvider` (`provider/human_provider.py`) — model-side
   pluggability.

There is no envelope, no engine-mismatch typed exception, no
`EngineRegistry.resolve(...)`. The Environment-per-domain pattern is
strong for *vertical specialisation* (software dev vs werewolf vs
Stanford-town) but does not provide engine routing within a single
domain. spring-ai-ascend's Engine Contract envelope routes a single
Run across engine implementations; MetaGPT routes a goal across
*environments* by static composition.

## 6. Evolution substrate

MetaGPT is unusually rich in evolution-related surfaces — reflecting
its academic lineage:

- **`exp_pool/`** — experience pool for cross-task learning.
  Stores typed experience records (per directory structure with
  `__init__.py`).
- **`learn/`** — learning modules for agent skill acquisition.
- **`memory/`** — short + long-term memory with archival.
- **`ext/`** — extension modules including domain-specific learning
  agents.
- **AFlow** (referenced at `README.md:36` as the ICLR 2025 paper) —
  the published research on **automating agentic workflow generation**
  itself. AFlow is the strongest published research evolution
  substrate in this Tranche. Examples implementation lives under
  `examples/aflow/` (per the README).
- **SPO** and **AOT** (`README.md:34`) — two additional papers shipped
  with example implementations under `examples/`. SPO = Self-Supervised
  Prompt Optimisation; AOT = Atom-of-Thought reasoning.

The framework ships **research-grade evolution machinery** that is
substantively beyond CrewAI's `train()` API or XAgent's
`RunningRecorder`. The downside is that the evolution surfaces are
*research artefacts wired into the framework*, not a *clean
substrate API*. There is no `EvolutionExport` scope discriminator on
emitted events; the exp_pool/learn surfaces are tightly coupled to
specific roles.

The strategic implication for spring-ai-ascend: MetaGPT's
`exp_pool` + `learn` patterns are the best published reference for
multi-agent learning at the framework level; the **separation of
substrate-from-runtime** (Rule R-I five-plane topology with
`deployment_plane: evolution`) is the architectural answer to
MetaGPT's coupling.

## 7. Deployment model

The OSS framework is **library + CLI**. The `startup.py` entry runs
`metagpt "<requirement>"` from the command line. The `Dockerfile`
(982 bytes — minimal) packages the CLI for container execution. The
project ships `examples/` directory with reference apps but no
docker-compose, no Helm chart, no Kubernetes manifests
(`find -name "Chart.yaml" -name "docker-compose*.yml"` returns only
the root Dockerfile).

The **commercial sibling** is **MGX (MetaGPT X)** at `mgx.dev`
(README.md:28-32) — "the world's first AI agent development team"
launched February 2025 as the hosted product. Like LangChain/CrewAI,
the OSS funnels into a hosted product.

**No Chinese-silicon support natively**, but the `config2.py` /
`provider/` architecture supports arbitrary OpenAI-compatible
endpoints by configuration (similar to XAgent). Repo-wide grep for
`Ascend`/`Kunpeng` returns zero hits. The project has Chinese-language
docs (`docs/README_CN.md`) and a clear cross-language posture but no
ARM64/NPU build specifics. Target is generic Python 3.9+ (per
`setup.py` review).

The deployment story is **single-process CLI / library**. For
spring-ai-ascend, MetaGPT is a *pattern source* (role + SOP + AFlow
evolution) not a deployment-target peer.

## 8. License + corporate sponsor

License: **MIT** (`LICENSE:1-3`, "The MIT License — Copyright (c)
2024 Chenglin Wu"). No copyleft, no field-of-use restrictions.

Corporate sponsor: **DeepWisdom** — the company behind the
commercial mgx.dev product. Lead author Chenglin Wu (`LICENSE:3`,
also the AFlow / SPO / AOT paper first author). The repository
authorship is concentrated under Chinese-resident researchers (per
commit-history language patterns and the trilingual READMEs
`README_CN.md` / `README_JA.md` / `README_FR.md`). Latest commit
`11cdf466d042aece04fc6cfd13b28e1a70341b1f` dated **2026-01-21** — the
project is **actively maintained** with ~6 month gap to current date.

The strategic positioning is **research-led OSS + commercial hosted
product**. For spring-ai-ascend, MetaGPT's strongest reusable
assets are the role + SOP discipline (cleaner than CrewAI's
persona-strings; SOPs are typed Action sequences) and the AFlow
agentic-workflow-generation research. The weakest asset for
adoption is the same as CrewAI/LangChain — the hosted product funnel
contradicts the sovereignty mandate.

## 9. What we LEARN

Patterns worth absorbing into spring-ai-ascend:

1. **`Role + SOP` discipline** (`team.py:33-34` documenting SOP as
   first-class concept; `roles/role.py:38-592` 14 concrete role
   implementations with typed Actions). The "role = goal + actions +
   subscribed_tags" tuple is cleaner than CrewAI's persona-strings
   because actions are *typed Action subclasses*, not natural-language
   instructions. Worth importing as a typed `RoleProfile` SPI.

2. **`Action` + `ActionNode` typed declarative skill catalog**
   (`actions/action_node.py`, 24+ Action implementations). Every
   skill is an `Action(BaseModel)` subclass with input/output Pydantic
   schemas. Direct prior art for spring-ai-ascend's contract-first
   skill registry — pairs with Rule R-K's skill capacity matrix.

3. **`investment: float` token-budget cap with `NoMoneyException`**
   (`team.py:54` + import of `NoMoneyException` at `team.py:24`). A
   first-class cost guard that fails the run when the LLM-token
   budget is exhausted. spring-ai-ascend has Rule R-K for skill
   capacity; adding a per-Run cost budget knob with typed
   over-budget signal would close the cost-governance gap.

4. **`HumanProvider` as drop-in LLM substitute** (`provider/human_provider.py`,
   imported at `roles/role.py:54` `from metagpt.provider import HumanProvider`).
   A human reviewer exposes the same chat-completion contract as an
   LLM, so swapping in a human is a config switch. Cleaner than
   AutoGen's `UserProxyAgent` because the substitution point is at the
   *provider* layer, not the agent layer. spring-ai-ascend's S2C
   callback envelope should ship a `HumanProvider`-equivalent
   `HumanModelAdapter`.

5. **`Environment` as message-routing topology with multiple
   domain implementations** (`environment/{base_env, mgx, werewolf,
   stanford_town, software}/`). Domain specialisation as
   environment-substitution, not framework-fork. Worth absorbing as a
   `RuntimeEnvironment` SPI in spring-ai-ascend's
   agent-execution-engine.

6. **AFlow-style automated workflow generation** (per the ICLR 2025
   paper, examples at `examples/aflow/`). The closest published
   research substrate for "agents that improve their own workflows".
   spring-ai-ascend's `agent-evolve` module should publish an
   AFlow-style API as a reference benchmark.

7. **Internal RFC-driven evolution** — multiple file-header comments
   reference "RFC 113 §2.2.3.2", "RFC 135 §2.2.3.3" etc. Project-internal
   RFCs (presumably under `docs/`) drive architectural change. This is
   conceptually similar to spring-ai-ascend's ADR discipline; the
   cross-validation reinforces that ADR-driven evolution scales.

## 10. Where we DIFFER

| # | Dimension | MetaGPT evidence | spring-ai-ascend evidence |
|---|-----------|------------------|---------------------------|
| 1 | **Multi-tenancy depth** — MetaGPT: no tenant model; `route_from`/`route_to`/`cause_by` on Message is agent-to-agent only. Ascend: tenant_id NOT NULL on Run, RLS at Postgres, re-validation at HTTP edge. | `schema.py:1-50` (Message routing fields are agent-scoped) | Rule R-C.2.a + `agent-service/.../Run.java` |
| 2 | **Engine Contract envelope vs Environment substitution** — MetaGPT: domain specialisation by `Environment` subclass (5 environments). Ascend: typed `EngineEnvelope` + `EngineRegistry.resolve()` with `EngineMatchingException` for engine-level routing within a domain. | `environment/__init__.py` (multiple Environment subclasses) | `docs/contracts/engine-envelope.v1.yaml` + Rule R-M.a/.b |
| 3 | **Spring-native ergonomics** — MetaGPT is Python-only. Ascend ships Spring Boot starters. | `setup.py` + Python-only | `D:\chao_workspace\spring-ai-ascend\pom.xml` |
| 4 | **中台+能力复用 dual deployment** — MetaGPT: library + CLI + commercial mgx.dev; no on-prem topology. Ascend: five-plane physical topology with `deployment_plane` per module. | minimal `Dockerfile` + no Helm + mgx.dev hosted | Rule R-I + `module-metadata.yaml#deployment_plane` |
| 5 | **Sandbox subsumption** — MetaGPT: code-execution via `WriteCode` + `RunCode` Actions (in-process by default); no permission contract. Ascend: `docs/governance/sandbox-policies.yaml` with six required keys. | `actions/run_code.py` (in-process exec) | Rule R-L + `docs/governance/sandbox-policies.yaml` |
| 6 | **Evolution substrate** — MetaGPT: `exp_pool` + `learn` + AFlow research (substantial); coupled to roles, not a separate plane. Ascend: dedicated `agent-evolve` module on `evolution` deployment plane + `EvolutionExport` discriminator. *(MetaGPT richer research substrate; Ascend cleaner architectural boundary.)* | `metagpt/{exp_pool,learn}/` + AFlow paper | `D:\chao_workspace\spring-ai-ascend\agent-evolve\module-metadata.yaml` |
| 7 | **Ascend+Kunpeng sovereignty** — MetaGPT: generic Python + configurable api_base; no ARM64/NPU adapter. Ascend: ARM64+NPU as design target. | `config2.py` + `provider/` (api_base configurable) | `D:\chao_workspace\spring-ai-ascend\README.md:3-10` |
| 8 | **Cost guard / budget discipline** — MetaGPT: `Team.investment: float` + `NoMoneyException`. Ascend: Rule R-K skill capacity matrix; no Run-level token budget yet. *(MetaGPT stronger on this single dimension.)* | `team.py:54` + `NoMoneyException` import | Rule R-K + `docs/governance/skill-capacity.yaml` |
| 9 | **License + sponsor posture** — MetaGPT: MIT + DeepWisdom + commercial mgx.dev. Ascend: Apache 2.0 + Huawei (no SaaS funnel). | `LICENSE:1-3` + `README.md:28-32` | `D:\chao_workspace\spring-ai-ascend\LICENSE` |
| 10 | **Governance / Code-as-Contract** — MetaGPT: research-grade discipline (internal RFCs, typed Actions, Pydantic-driven schemas); no architectural enforcers, no recurring-defect ledger. Ascend: 144+ gate rules + ArchUnit + governance YAML kernel. | RFC references in file headers; no `gate/` analogue | `CLAUDE.md` + `gate/check_architecture_sync.sh` |

MetaGPT is the **research-grade reference** for multi-agent
software-engineering specialisation, and the only framework in this
Tranche that ships a published evolution research substrate (AFlow,
ICLR 2025 oral). spring-ai-ascend should treat MetaGPT as the
benchmark for the `agent-evolve` plane's substantive capability
(not just substrate); the role + Action + SOP discipline is worth
importing as typed SPI surfaces. The architectural boundary win for
spring-ai-ascend is the *separation* of evolution from runtime —
which gives sovereign-deployment customers a path to run evolution
in a separate physical plane that MetaGPT's role-coupled design
cannot.
