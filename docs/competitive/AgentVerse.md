---
analysis_id: COMPETITIVE-AGENTVERSE
governance_infra: true
analysis_date: 2026-05-28
analyst: spring-ai-ascend Phase C Tranche 1
repo_clone_at: D:\ai-research\agent-platforms-survey\AgentVerse\
---

# Competitive Analysis: OpenBMB/AgentVerse

Source-grounded analysis at commit `f90c4bd` (2024-09-09, tip of
`main`), Python package version `0.1.8.1` declared in `setup.py:16`.
AgentVerse is **Tsinghua / OpenBMB**'s academic multi-agent research
framework, published as **ICLR 2024** (`README.md:68-69`). The HEAD
commit dates to **September 2024** — over 20 months behind the other
competitors in this tranche — confirming the project's status as a
**published-and-stable academic artefact**, not active commercial
infrastructure.

## 1. Tagline & positioning

The repository's own pitch, verbatim from `README.md:47`:

> "AgentVerse is designed to facilitate the deployment of multiple
> LLM-based agents in various applications. AgentVerse primarily
> provides two frameworks: **task-solving** and **simulation**."

The two framings are operationalised differently (`README.md:49,56`):

> "Task-solving: This framework assembles multiple agents as an
> automatic multi-agent system to collaboratively accomplish the
> corresponding tasks. Applications: software development system,
> consulting system, etc."
>
> "Simulation: This framework allows users to set up custom
> environments to observe behaviors among, or interact with, multiple
> agents. Applications: game, social behavior research of LLM-based
> agents, etc."

The project ships **example tasks** rather than a product:

- **Simulation tasks**: NLP Classroom (3 / 9 players),
  Prisoner Dilemma, Software Design (SDE Team), Database
  Administrator (DBA), Pokemon multi-character game, math 2-player
  with tools, Alice Home — directory listing under
  `agentverse/tasks/simulation/`.
- **Task-solving tasks**: HumanEval (coding), MGSM (math), Logic
  Grid, Brainstorming, CommonGen, ResponseGen, Tool Using, Python
  Calculator — directory listing under
  `agentverse/tasks/tasksolving/`.

Positioning vs spring-ai-ascend: AgentVerse is a **research
notebook**, not a production framework. It is closer to
**CAMEL-AI** or **AutoGen v0.2** in shape than to LangGraph,
AgentScope, or Spring AI Alibaba. The README's "Coming Soon"
section (`README.md:84-88`) lists "Add documentation" and "Support
more sophisticated memory" as unchecked — strong signals that the
repo froze at paper-acceptance and has not graduated to production
maturity. The architectural intent overlap with spring-ai-ascend is
**negligible at the runtime layer** but **interesting at the
multi-agent-orchestration semantics layer** — the
`environment + rule + agent set` model is a clean academic
formulation worth understanding.

## 2. Architecture skeleton

The Python package layout is small and flat — **141 `.py` files**
under `agentverse/` (verified by `find -name "*.py" | wc -l`). The
top-level subpackages are:

```
agentverse/
  __init__.py
  agentverse.py            # main AgentVerse class (simulation entry)
  simulation.py            # simulation runner
  tasksolving.py           # task-solving runner
  demo.py                  # interactive demo
  gui.py                   # Gradio UI launcher
  initialization.py        # task config → agents/environment loader
  message.py               # Message type
  registry.py              # ClassRegistry helper
  logging.py, utils.py
  agents/                  # BaseAgent + 2 subtype trees
    base.py                # BaseAgent (Pydantic, ~120 lines)
    simulation_agent/      # conversation, prisoner_dilemma,
                           # reflection, tool
    tasksolving_agent/     # role, critic, solver, evaluator, executor
  environments/            # BaseEnvironment + 2 environment trees
    base.py                # BaseEnvironment (~60 lines)
    simulation_env/        # 8 environment types + rule sub-system
      rules/{describer,order,selector,updater,visibility}/
    tasksolving_env/       # basic + rule sub-system
  llms/                    # BaseLLM + OpenAI adapter only
  memory/                  # 5 memory types (chat_history, summary,
                           # vectorstore, sde_team, base)
  memory_manipulator/      # memory-update strategies
  output_parser/           # task-specific output parsers
  tasks/                   # bundled task YAML configs
    simulation/            # 10 example tasks (configs only)
    tasksolving/           # 8 example tasks (configs only)

agentverse_command/        # CLI entry points
  main_simulation_cli.py
  main_simulation_gui.py
  main_tasksolving_cli.py
  benchmark.py

pokemon_server.py          # standalone Pokemon-demo HTTP server (FastAPI)
ui/                        # React-based simulation visualizer
data/, dataloader/         # benchmark dataset loaders
```

The architectural model is **`Environment + Rule + Agent[]`** —
agents step through turns coordinated by an environment whose
behaviour is parameterised by a `BaseRule` instance
(`agentverse/environments/base.py:16-58`). The `BaseEnvironment`
fields (`base.py:33-38`) carry `agents: List[BaseAgent]`,
`rule: BaseRule`, `max_turns: int = 10`, `cnt_turn: int = 0`,
`last_messages: List[Message]`, `rule_params: Dict`.

**Counterpart mapping to spring-ai-ascend**:

| spring-ai-ascend                  | AgentVerse counterpart                            | Notes |
|-----------------------------------|---------------------------------------------------|-------|
| `agent-service`                   | (none — no persistence, no Run record)            | Each invocation is in-process Python |
| `agent-bus`                       | (none — direct method call between agents)        | No event bus |
| `agent-execution-engine`          | `agentverse/environments/` (env.step orchestrates one turn) | Turn-based, not event-driven |
| `agent-middleware`                | (none — no persistence layer)                     | Memory is in-memory dict |
| `agent-client`                    | `ui/` Gradio + Pokemon HTTP server                | Demo-grade, not production |
| `agent-evolve`                    | (none)                                            | Out of scope |
| `spring-ai-ascend-graphmemory-starter` | `agentverse/memory/vectorstore.py` (Python dict + OpenAI embeddings) | Toy implementation |

The structural distinctive of AgentVerse vs the others is the
**explicit `Rule` sub-system**: `environments/simulation_env/rules/
{describer, order, selector, updater, visibility}/` carries five
rule-component axes. `order/` defines turn order (sequential,
concurrent, random, classroom, prisoner); `visibility/` defines who
sees what messages (all, oneself, classroom, prisoner, pokemon); etc.
This is a small, clean, ablation-friendly research surface.

## 3. Developer experience

There is **no SDK-style "build your first agent" path**. The
documented workflow (`README.md:101-114`) is:

```
python agentverse_command/main_simulation_gui.py \
       --task simulation/nlp_classroom_9players
```

A "task" is a directory under `agentverse/tasks/{simulation,
tasksolving}/<task_name>/` containing a single `config.yaml` plus
optional `README.md`. The config declares:

1. **Agents**: name, role description, prompts, LLM config, output
   parser, memory configuration.
2. **Environment**: type (e.g., `basic`, `prisoner_dilemma`,
   `pokemon`, `sde_team`) + rule sub-fields (order, visibility,
   selector, updater, describer).
3. **Task metadata**: task_description, max_rounds, cnt_agents.

Concrete example: `agentverse/tasks/simulation/db_diag/config.yaml`
(60 lines visible) declares a Database Administrator (DBA) team
simulation with `chief_dba_format_prompt`, `cpu_agent_format_prompt`,
etc. Prompts use `${chat_history}`, `${tool_observation}`,
`${agent_name}` template variables resolved at runtime.

The `BaseAgent` (`agentverse/agents/base.py:17-32`) is a Pydantic
model with `name, llm, output_parser, prepend_prompt_template,
append_prompt_template, prompt_template, role_description, memory,
memory_manipulator, max_retry, receiver: Set[str], async_mode: bool`.
Subclassing means writing a Python class declaring `step()` and
`astep()` methods that return a `Message`. There is no posture
concept, no fail-closed startup, no configuration validation beyond
Pydantic.

Compared to spring-ai-ascend's Spring-Boot starter onboarding,
AgentVerse onboarding is **researcher-grade**: edit a YAML, run a
script, observe behaviour in the Gradio UI. The repository ships a
Dockerfile (`Dockerfile:1-21`) — based on `python:3.10` with
**Tsinghua Tuna pip mirror** (`Dockerfile:3-5`) and
**BMTools** (`Dockerfile:11-14`) cloned from `OpenBMB/BMTools` —
which makes the dev environment China-academic-network-friendly but
not production-deployable.

## 4. Multi-tenancy & governance

**There is no tenant model, no user model, no persistence layer.**
Every run is an in-process Python session. A repository-wide grep for
`tenant|TenantId` returns no business hits. The codebase has zero
concept of access control, authentication, authorisation, audit
logging, or rate limiting.

The closest "governance" surface is the **Rule** sub-system already
described, but the rules govern **agent interaction protocol**
(turn order, visibility, message routing), NOT user / tenant / data
isolation. Examples (`agentverse/environments/simulation_env/rules/
visibility/`): `all.py` makes every message visible to every agent;
`oneself.py` restricts to the sender; `classroom.py` implements a
teacher/student visibility pattern; `prisoner.py` implements the
prisoner-dilemma asymmetric visibility.

There is no:
- Database (no SQLite/Postgres/MySQL in the dependencies — `setup.py:
  install_requires` reads `requirements.txt` which carries openai,
  pydantic, langchain, gradio, fastapi but no SQL driver).
- Authentication or authorization layer.
- Posture-aware defaults (everything is "research" mode by default).
- Audit MDC, structured logging beyond basic Python `logging`.

For spring-ai-ascend, AgentVerse offers **zero** to the multi-tenancy
dimension. The repo is single-process, single-user research code —
its absence of governance is by design, not by oversight. Comparing
"AgentVerse vs Ascend on multi-tenancy" is a category error.

## 5. Engine pluggability

The model layer is **OpenAI-only**. `agentverse/llms/` declares
`base.py` (`BaseLLM`) and `openai.py` — that's it. Other providers
(Anthropic, Gemini, DashScope) are absent. The LLM adapter is
hardcoded in `agentverse/memory/vectorstore.py:39` to
`OpenAIChat(model="gpt-4")`.

The agent layer is **inheritance-based**: `BaseAgent` →
`SimulationAgent` (conversation, prisoner_dilemma, reflection, tool)
or `TasksolvingAgent` (role, critic, solver, evaluator, executor).
Customising means subclassing, not registering an engine.

The environment layer is **factory-based via `registry.py`**.
Environments are registered with a string key
(`agentverse.environments.simulation_env.rules.visibility.all`
registers under `"all"`); a task config's `environment.type` selects
the registered class. This is the closest AgentVerse comes to an
SPI surface, and it's structurally similar to spring-ai-ascend's
`EngineRegistry.resolve(envelope)` — but the discriminator is a
**string field**, not a strongly-typed envelope, and there is no
mismatch exception (KeyError on a missing registry key is the only
failure mode).

There is no:
- Engine envelope or `engine_type` discriminator.
- Hook system or middleware (the closest is `memory_manipulator/`
  which lets memory-update behaviour be customised, but it's
  per-agent, not cross-cutting).
- S2C callback transport (no HTTP edge exists).

For spring-ai-ascend, the engine layer is not where AgentVerse sets
the bar. The **`Rule` decomposition** (order/visibility/selector/
updater/describer) is the architectural idea worth absorbing: it
splits the multi-agent coordination contract into five orthogonal
axes that can vary independently per task. Our `EngineEnvelope` could
borrow the same decomposition for multi-agent envelopes.

## 6. Evolution substrate

**There is no evolution substrate.** AgentVerse runs the same agents
on the same tasks; there is no trajectory store, no replay buffer,
no fine-tuning hook, no graph memory. The memory layer is shallow:

- `agentverse/memory/chat_history.py` — list of `Message` objects in
  Python.
- `agentverse/memory/summary.py` — summary memory (LLM-condensed
  history).
- `agentverse/memory/vectorstore.py:15-46` — `VectorStoreMemory`,
  but the implementation is **Python dicts** (`embedding2memory:
  dict`, `memory2embedding: dict`) with OpenAI embeddings — no real
  vector store, no persistence, no scale. Comments at line 21:
  "treat memory as a dict, treat message.content as memory" make the
  toy nature explicit.
- `agentverse/memory/sde_team.py` — SDE-team-task-specific memory.

`memory_manipulator/` carries memory-update strategies but only for
the in-process dictionaries.

**No trajectory persistence.** A repo-wide grep for
`Trajectory|finetune|GRPO|DPO|SFT` returns zero business hits.

For spring-ai-ascend, AgentVerse's interesting evolution-adjacent
contribution is **reflection as an agent role** (`agentverse/agents/
simulation_agent/reflection.py`) and **critic agents** in
task-solving (`agentverse/agents/tasksolving_agent/` has critic,
solver, evaluator, executor roles in a Critic-Evaluator-Solver
pattern from the ICLR paper). This is the architectural form that
"agent self-improvement" takes in their paper — orchestrate
specialised roles in a debate / critique loop. Our `agent-evolve`
plane should explicitly catalogue these role archetypes (critic /
solver / evaluator / reflector) as a starter library.

## 7. Deployment model + sovereign-hardware support

The shipped deployment surface is **CLI + Gradio UI + Pokemon HTTP
demo server**:

- `agentverse-simulation-cli` / `agentverse-simulation-gui` /
  `agentverse-tasksolving` console scripts declared in `setup.py:
  48-54`. These are entry points into `agentverse_command/main_*.py`
  scripts that spin up an in-process Python run.
- `pokemon_server.py` is a standalone FastAPI server hosting the
  Pokemon demo, intended as an HTTP harness for the H5 game UI.
- `Dockerfile` (21 lines) builds a single-image dev container with
  Tsinghua-mirror pip + apt repos, BMTools installed, AgentVerse
  pip-installed in editable mode. This is a **dev convenience**, not
  a production deployment.

There is no `docker-compose.yml`, no Kubernetes manifests, no Helm
chart, no Aliyun/Volcengine cloud-registry default. The deployment
unit is a single Python process with optional Gradio UI.

**No Chinese-silicon support.** A repository-wide grep for
`Ascend|Kunpeng|昇腾|鲲鹏` returns zero matches. CPython 3.10
generic Linux (`Dockerfile:1`). The model serving target is OpenAI
API, optionally local LLMs (LLaMA, Vicuna — listed as completed in
"Coming Soon" — `README.md:86`) via the `requirements_local.txt`
extra dependency set.

For spring-ai-ascend, AgentVerse is **not a deployment competitor**.
The repository is research code with academic-grade packaging. Its
relevance to our deployment discipline is zero.

## 8. License + corporate sponsor

License: **Apache 2.0** (`LICENSE`, declared in `setup.py:24-27`
classifier `License :: OSI Approved :: Apache Software License`).
Fully permissive.

Sponsor: **OpenBMB / Tsinghua University**. The `setup.py:17-19`
declares `author="AgentVerse Team", author_email="agentverse2@
gmail.com"` (a community-style Gmail rather than a corporate
domain). The GitHub org is `OpenBMB` (Tsinghua's open-source group
that also publishes BMTools, BMTrain, ChatDev, MiniCPM, XAgent).
ICLR 2024 publication (`README.md:69`) names paper authors via the
arXiv pre-print: 2308.10848 (the main AgentVerse paper) +
2309.02427 ("Multi-agent as system"). NVIDIA's developer blog
featured AgentVerse in March 2024 (`README.md:67`), conferring
mainstream technical legitimacy.

Latest commit on `main`: `f90c4bd9680fdd3bcff8c52c9170911a59b23478`
dated **2024-09-09** ("fix: bug in openai async call. update
requirements. #139 #134 #128"). **20 months of inactivity** as of
this analysis date (2026-05-28). The README "Coming Soon" section
(`README.md:84-88`) still shows unchecked items including
"Add documentation" and "Support more sophisticated memory" — both
of which would have shipped if the project remained active.

For spring-ai-ascend, the implication is that **AgentVerse is not a
moving target** — its architectural ideas are stable, its code is
not. We can cite the paper and the rule decomposition without
worrying about being out-paced.

## 9. What we LEARN

Patterns worth absorbing into spring-ai-ascend, with concrete file
paths in AgentVerse:

1. **Five-axis rule decomposition for multi-agent coordination** —
   `agentverse/environments/simulation_env/rules/{describer, order,
   selector, updater, visibility}/` decomposes multi-agent
   coordination into five orthogonal axes. For our `EngineEnvelope`
   multi-agent extension, this is the right ontology. Specifically:
   - **order**: turn-taking policy (sequential, concurrent, random,
     classroom, prisoner) → mapped to our scheduler.
   - **visibility**: who sees what messages → mapped to our event
     bus channel filters.
   - **selector**: which message(s) are passed to the next turn →
     mapped to context construction.
   - **updater**: how environment state evolves → mapped to memory
     write strategy.
   - **describer**: how environment state is described to agents →
     mapped to prompt assembly.

2. **Task-as-directory-of-YAML convention** —
   `agentverse/tasks/{simulation,tasksolving}/<task>/config.yaml`
   is a clean ergonomics pattern: a "task" is a directory, the
   `config.yaml` is the entry point, optional `README.md` documents
   it. Our `examples/` directory should mirror this — one directory
   per example with a `config.yaml`-equivalent (or `application.yml`
   in Spring terms) as the entry.

3. **Critic / Solver / Evaluator / Executor agent roles** —
   `agentverse/agents/tasksolving_agent/` ships role archetypes from
   the ICLR paper. For our `agent-evolve` plane and multi-agent
   workflows, these archetypes should be **named roles** in our
   skill catalogue so developers don't reinvent them.

4. **Reflection agent pattern** —
   `agentverse/agents/simulation_agent/reflection.py` ships a
   reflection-loop agent. Our middleware layer should ship a
   `ReflectionMiddleware` HookPoint listener as a canonical example
   of cross-cutting policy.

5. **Pydantic-based BaseAgent contract** — `agents/base.py:17-32`
   declares agent fields with Pydantic validation. Spring-ai-ascend
   uses `@ConfigurationProperties + @Valid` for the same
   declarative-validation effect; the pattern parity is good
   structural confirmation.

6. **Output-parser-as-class hierarchy** — `agentverse/output_parser/
   ` ships per-task output parsers. Our engine layer should expose
   structured output parsing as a per-task SPI extension point
   (similar to spring AI's `StructuredOutputConverter` but with our
   own HookPoint integration).

7. **Memory manipulator as a separate sub-system** —
   `agentverse/memory_manipulator/` separates **what is remembered**
   (`memory/`) from **how memory is updated** (`memory_manipulator/`).
   This is a clean separation our graphmemory-starter should adopt
   — store + write-strategy as separate SPIs.

## 10. Where we DIFFER

AgentVerse is research code, not production infrastructure — the
"DIFFER" table is largely vacuous at the deployment/governance
dimensions. The few rows that matter focus on architectural ideas
versus production discipline.

| # | Dimension | AgentVerse evidence | spring-ai-ascend evidence |
|---|-----------|---------------------|---------------------------|
| 1 | **Production vs research framing** — AgentVerse: 20-month-stale academic artefact, single-process. Ascend: enterprise production discipline. | HEAD commit `f90c4bd` dated 2024-09-09; README "Coming Soon" still lists "Add documentation" unchecked (`README.md:84-88`) | `D:\chao_workspace\spring-ai-ascend\CLAUDE.md` (active rule kernel, governance enforcement) |
| 2 | **Tenancy depth** — AgentVerse: zero (no user, no tenant). Ascend: tenantId mandatory + RLS + edge re-auth. | `grep -r "tenant" agentverse/` returns no business hits | Rule R-J.a |
| 3 | **Persistence layer** — AgentVerse: in-process Python dicts. Ascend: Postgres + Flyway migrations + checkpoint stores. | `agentverse/memory/vectorstore.py:36-38` (`embedding2memory: dict`) | `agent-middleware/` + `agent-service/src/main/resources/db/migration/` |
| 4 | **Engine pluggability** — AgentVerse: factory-via-string-registry, KeyError on miss. Ascend: typed envelope + EngineRegistry + EngineMatchingException. | `agentverse/registry.py` (string-key registry) | Rule R-M.a/.b |
| 5 | **Evolution substrate** — AgentVerse: critic/solver roles + reflection, but no persistence. Ascend: `agent-evolve` deployment plane. | `agentverse/agents/tasksolving_agent/` (in-process role archetypes) | `D:\chao_workspace\spring-ai-ascend\agent-evolve\module-metadata.yaml` |
| 6 | **Sovereign hardware** — AgentVerse: CPython 3.10 + OpenAI API, Tsinghua-mirror pip. Ascend: Ascend NPU + Kunpeng ARM64. | `Dockerfile:3-5` (`mirrors.tuna.tsinghua.edu.cn`) | `D:\chao_workspace\spring-ai-ascend\README.md:3-10` |
| 7 | **Multi-agent coordination semantics** — AgentVerse: explicit `Rule = {order, visibility, selector, updater, describer}` decomposition (we want to LEARN). Ascend: single execution envelope, multi-agent semantics not yet first-class. | `agentverse/environments/simulation_env/rules/` (five rule axes) | (no equivalent decomposition in our `agent-execution-engine`) |
| 8 | **Model provider breadth** — AgentVerse: OpenAI only. Ascend: pluggable via Spring AI BoM + our envelope. | `agentverse/llms/{base.py, openai.py}` only | `spring-ai-ascend-dependencies/pom.xml` |
| 9 | **License + sponsor posture** — AgentVerse: Tsinghua/OpenBMB academic; non-competing with our commercial positioning. Ascend: complementary citation target (paper-bibliographable). | `setup.py:17-19` (`author_email="agentverse2@gmail.com"`) | (project memory: no academic-competitor entry) |
| 10 | **Governance / Code-as-Contract** — AgentVerse: black + pytest only. Ascend: 144+ gate rules + ArchUnit + governance YAML. | `pyproject.toml`-free repository; lint config is `black` shield in `README.md:21` | `D:\chao_workspace\spring-ai-ascend\CLAUDE.md` + `gate/check_architecture_sync.sh` |

AgentVerse is the **most architecturally interesting** of the five
competitors despite being the smallest in code and oldest in
commit-time. Its five-axis Rule decomposition for multi-agent
coordination is a contribution that none of SAA, AgentScope, Coze
Studio, OpenSPG, or LangBot offer. For spring-ai-ascend, the right
posture is to **cite AgentVerse as paper bibliography** when we
formalise our multi-agent envelope (PC-something), absorb the
five-axis decomposition, and otherwise treat the codebase as
non-competing research artefact.
