---
analysis_id: COMPETITIVE-OPENHANDS
governance_infra: true
analysis_date: 2026-05-28
analyst: spring-ai-ascend Phase C Tranche 3
repo_clone_at: D:\ai-research\agent-platforms-survey\OpenHands\
---

# Competitive Analysis: All-Hands-AI/OpenHands

Source-grounded analysis at `main` cloned 2026-05-28. OpenHands is **not**
a direct competitor — it is a **contrast project** with one nuance:
unlike OpenManus / cline / aider / opencode (pure single-developer
tools), OpenHands ships an *Enterprise tier* (PolyForm-licensed
`enterprise/` directory with multi-org auth, Keycloak SSO, RBAC, RLS
discipline) layered atop an MIT-licensed core. So while the *public* tool
overlaps with OpenManus, the *Enterprise tier* is the closest
spring-ai-ascend-shaped thing in this tranche. It is still
fundamentally **Python**, not JVM.

## 1. Tagline & positioning (contrast)

The repo's elevator pitch from `README.md:5`:

> "OpenHands: AI-Driven Development"

Followed by a `SWEBench` score badge of `77.6` (`README.md:11`). The
README then lists five distribution shapes (`README.md:32-73`):

> "### OpenHands Software Agent SDK — The SDK is a composable Python
> library that contains all of our agentic tech. … Define agents in
> code, then run them locally, or scale to 1000s of agents in the cloud.
>
> ### OpenHands CLI — The CLI is the easiest way to start using
> OpenHands. … You can power it with Claude, GPT, or any other LLM.
>
> ### OpenHands Local GUI — Use the Local GUI for running agents on
> your laptop. It comes with a REST API and a single-page React
> application. The experience will be familiar to anyone who has used
> Devin or Jules.
>
> ### OpenHands Cloud — This is a deployment of OpenHands GUI, running
> on hosted infrastructure.
>
> ### OpenHands Enterprise — Large enterprises can work with us to
> self-host OpenHands Cloud in their own VPC, via Kubernetes. … you'll
> need to purchase a license if you want to run it for more than one
> month."

**Customer profile**: a wide spectrum — from a single developer running
the CLI on their laptop, to enterprises self-hosting OpenHands Enterprise
in their own VPC via Kubernetes. Trust signals at `README.md:99-118`
name TikTok, VMware, Roche, Amazon, C3.AI as enterprise adopters.

**How this differs from spring-ai-ascend**: spring-ai-ascend is
JVM-native, ships as a Maven Central library + BoM, and is positioned
for Ascend NPU + Kunpeng ARM64 sovereign hardware. OpenHands is
Python-native, ships as `openhands-ai` on PyPI plus Docker images, and
is positioned for SWE-bench-style autonomous coding on AWS / GCP / Azure
cloud. The audiences overlap *only* where an enterprise wants an
autonomous coding agent on their own cluster — but the substrate
(Python vs JVM) and the optimisation target (SWE-bench score vs
multi-tenant audit-grade evidence) differ.

## 2. Architecture skeleton

OpenHands is structured as a Python Poetry project plus a TypeScript
React frontend, plus a PolyForm-licensed enterprise sub-tree. The
top-level layout (verified by `ls`):

```
openhands/             # MIT-licensed core (Python package)
  __init__.py, version.py, analytics/, app_server/, server/, py.typed
frontend/              # React + TypeScript SPA
openhands-ui/          # additional UI components
enterprise/            # PolyForm-licensed enterprise tier
  server/{auth,routes,models,services,...}
  storage/             # 40+ SQLAlchemy table models
  migrations/versions/ # Alembic migrations (10+)
  alembic.ini, saas_server.py
build.sh, Makefile, docker-compose.yml
config.template.toml, dev_config/, scripts/, skills/
poetry.lock, pyproject.toml, pytest.ini
```

The `openhands.app_server` package is the FastAPI server. Sub-packages
include `event/` (event service with AWS / GCP / filesystem backends),
`sandbox/` (Docker + process + remote sandbox services),
`integrations/{azure_devops,bitbucket,bitbucket_data_center,forgejo,
github,gitlab,jira_dc}/`, `mcp/`, `git/`, `secrets/`, `services/`
(`jwt_service.py`, `db_session.py`, `httpx_client_injector.py`),
`settings/`, `user_auth/`, `event_callback/`, `file_store/`,
`status/`, etc. (24 sub-packages enumerated by `ls`).

The `enterprise/` tree adds: `server/auth/` (Keycloak integration —
`keycloak_manager.py`, `org_context.py`, `saas_user_auth.py`, GitHub /
GitLab SSO sync), `server/routes/`, `storage/` (40+ table models for
billing, integrations, webhooks, conversations, etc.),
`migrations/versions/` (Alembic), `analytics/`, `integrations/`,
`sync/`, plus a `saas_server.py` SaaS-specific entrypoint.

**Counterpart mapping to spring-ai-ascend**:

| spring-ai-ascend                  | OpenHands counterpart                          | Notes |
|-----------------------------------|------------------------------------------------|-------|
| `agent-service`                   | `openhands/app_server/`                        | FastAPI vs Spring WebFlux |
| `agent-bus`                       | `openhands/app_server/event/`                  | Event services backed by AWS SQS / GCP Pub/Sub / filesystem |
| `agent-execution-engine`          | `openhands/agent/` (in the SDK repo, not here) | Code split — the SDK is in a separate repo |
| `agent-middleware`                | `openhands/app_server/middleware.py` + services | Auth + DB middleware |
| `agent-client`                    | `frontend/` (React SPA)                        | TS SPA vs Java SDK |
| `agent-evolve`                    | (no direct counterpart in OpenHands repo)      | OpenHands does not train models in-tree |
| spring-ai-ascend-graphmemory-starter | `skills/` directory                          | Memory is skill-shaped |

## 3. Developer experience

Three onboarding paths per `README.md`:

1. **CLI** — `pip install openhands-ai && openhands` (familiar to
   Claude Code / Codex users).
2. **Local GUI** — Docker compose: `docker-compose up -d` per
   `docker-compose.yml` at the repo root.
3. **Cloud / Enterprise** — managed at `app.all-hands.dev` (free
   sign-in with GitHub/GitLab) or self-hosted via Kubernetes.

For development, `AGENTS.md` documents the contributor flow:
`make build && make run FRONTEND_PORT=12000 BACKEND_HOST=0.0.0.0`
(`AGENTS.md:6-8`). Pre-commit hooks are mandatory: `make
install-pre-commit-hooks` (`AGENTS.md:23-25`); lint must pass before
push (`AGENTS.md:27-33`).

Compared to spring-ai-ascend's quickstart (add a Maven dependency, write
`@ConfigurationProperties`, `mvn spring-boot:run`), OpenHands's
quickstart is closer to "install a CLI tool + spin a Docker compose
stack" — much heavier than ascend's library-embed model but lighter
than self-hosting a Kubernetes cluster.

## 4. Multi-tenancy & governance

**This is the most interesting OpenHands dimension.** The core MIT
package has *no* tenant model — `openhands.app_server` assumes a
single deployment per developer. But the `enterprise/` tier introduces
**organization scoping** via `enterprise/server/auth/org_context.py`,
which deserves verbatim citation (`org_context.py:1-22`):

> "Resolve the *effective* organization ID for the current request.
> Precedence (highest first):
> 1. ``api_key_org_id`` — org bound to the API key used for
>    authentication. The key is pinned to that org and cannot be
>    overridden. If an ``X-Org-Id`` header is also present and differs,
>    the request is rejected with 403.
> 2. ``X-Org-Id`` header — explicit, per-request override sent by the
>    client. Validated against the authenticated user's org memberships;
>    rejected with 403 if the user is not a member of that org.
> 3. ``user.current_org_id`` — the user's currently selected org …
> The resolution is cached on ``SaasUserAuth`` for the duration of a
> single request …"

This is conceptually identical to spring-ai-ascend's Rule R-J.b — same
re-validation at the HTTP edge, same 403 on mismatch, same per-request
caching. OpenHands implements it in FastAPI dependencies
(`@Depends(resolve_effective_org_id)`); ascend implements it in Spring
WebFlux interceptors. Both arrive at the same architectural shape.

What OpenHands does **not** ship (verified by grep):

- No Row-Level Security `CREATE POLICY` statements in Alembic
  migrations (`enterprise/migrations/versions/*.py`). Org scoping is
  application-layer, not storage-engine.
- No "fail-closed posture" concept; the enterprise tier targets
  always-production deployment.
- No idempotency table (the equivalent is conversation-id keyed
  state).

By contrast, spring-ai-ascend pushes tenant isolation **into the storage
engine** via Postgres RLS (Rule R-J.a). OpenHands keeps it at the
application layer with org-membership validation. Both are defensible
choices — RLS is stronger but couples Postgres-version compatibility;
app-layer validation is more portable but trusts every code path to
check.

## 5. Engine pluggability

OpenHands separates *the engine* (SDK at `github.com/OpenHands/
software-agent-sdk`, `README.md:39`) from *the server*
(`openhands/app_server`) — the SDK is in a separate repo, so the
engine-pluggability surface lives outside this clone. What this repo
shows:

- **Sandbox service contract** (`openhands/app_server/sandbox/
  sandbox_service.py`) — abstract sandbox interface with concrete
  backends: `docker_sandbox_service.py`, `process_sandbox_service.py`,
  `remote_sandbox_service.py`. Multi-backend pluggability is real
  here.
- **Event service contract** (`openhands/app_server/event/
  event_service_base.py`) — concrete backends `aws_event_service.py`
  (SQS), `google_cloud_event_service.py` (Pub/Sub),
  `filesystem_event_service.py`. Same plug-in shape.
- **Sandbox spec service** (`openhands/app_server/sandbox/
  sandbox_spec_service.py`) — preset / docker / process / remote
  sandbox spec providers, each with its own service implementation.
- **Integration providers** (`openhands/app_server/integrations/`) —
  per-platform `provider.py` for Azure DevOps, Bitbucket (cloud +
  data center), Forgejo, GitHub, GitLab, Jira DC.

There is no `EngineRegistry.resolve(envelope)` analogue with a typed
envelope, but the FastAPI dependency-injection pattern serves a similar
role — `Depends(get_sandbox_service)` returns a concrete service
implementation per the runtime config. The shape is "Python ABC +
DI" rather than "typed envelope + registry".

## 6. Evolution substrate

OpenHands does not ship an evolution plane *in this repo* — the
project's evolution focus is in sister repos:
`github.com/OpenHands/benchmarks` (evaluation infrastructure, named at
`README.md:80`), `github.com/OpenHands/ToM-SWE` (theory-of-mind
research), `github.com/OpenHands/openhands-chrome-extension/` (browser
extension). The skills surface lives in `skills/` at the repo root.

Memory is *conversation-scoped*, not user-scoped trajectory training.
The enterprise tier records billing sessions, conversation metadata,
feedback (`enterprise/storage/feedback.py`,
`enterprise/storage/conversation_work.py`,
`enterprise/storage/billing_session.py`) but does not export these for
model fine-tuning in this repo.

No `EvolutionExport` discriminator on emitted events. No five-plane
topology — OpenHands deploys as a single FastAPI process plus event
queue plus sandbox runtime. Spring-ai-ascend's explicit
`deployment_plane: evolution` (Rule R-I) is a wider architectural bet
than OpenHands's "ship the agent, evaluate it in a separate repo"
shape.

## 7. Deployment model

OpenHands ships **three deployment shapes**:

- **Local Docker**: `docker-compose.yml` at the repo root spins up
  the runtime. The `containers/` directory holds Dockerfile
  definitions for `app`, `runtime`, `dev`, plus build scripts.
- **Cloud SaaS**: `app.all-hands.dev` (managed by All Hands AI).
- **Enterprise Kubernetes**: `kind/` directory holds Kind cluster
  configs; `enterprise/` directory plus `enterprise/Dockerfile`
  build the SaaS-as-VPC stack.

There is no Helm chart in the cloned tree (`find -name "Chart.yaml"`
returns zero hits), but `kind/` and `dev_config/` suggest Kubernetes is
the canonical deployment target for enterprise.

**No Chinese-silicon support.** Repository-wide grep for
`Ascend|Kunpeng|昇腾|鲲鹏` returns zero source-code hits. Docker base
images are generic Python + Node — `containers/` Dockerfiles inherit
from `python:3.12` and `node:20`. The hardware target is generic AWS /
GCP / Azure on x86_64.

This is the second axis of differentiation from spring-ai-ascend — both
support Kubernetes deployment, but ascend's deployment story is
ARM64-native + NPU-aware, whereas OpenHands is x86_64-native +
cloud-GPU-aware.

## 8. License + corporate sponsor

License: **dual** — MIT for the core (`LICENSE:1-9` and `LICENSE:11-13`)
and **PolyForm Free Trial 1.0.0** for the `enterprise/` directory
(`enterprise/LICENSE:1-9`):

> "# PolyForm Free Trial License 1.0.0 — Copyright (c) 2026 All Hands
> AI — In order to get any license under these terms, you must agree to
> them as both strict obligations and conditions to all your licenses."

The PolyForm Free Trial allows up to one month of evaluation; running
the enterprise tier in production requires a commercial license per
`README.md:68`:

> "OpenHands Enterprise is source-available--you can see all the source
> code here in the enterprise/ directory, but you'll need to purchase a
> license if you want to run it for more than one month."

Corporate sponsor: **All Hands AI** (the entity holding copyright on
the enterprise tier). The MIT core is collectively authored. Adopters
named at `README.md:99-118` include TikTok, VMware, Roche, Amazon,
C3.AI.

The dual-license is the closest thing in this tranche to a *commercial-
ready open core*. Spring-ai-ascend ships uniformly Apache 2.0; ascend's
business model relies on the platform being a sovereign-hardware play
rather than a freemium SaaS. Different go-to-market shapes.

## 9. What we LEARN

Patterns worth absorbing into spring-ai-ascend, with concrete file paths
in OpenHands:

1. **Org-context resolution precedence** — `enterprise/server/auth/
   org_context.py:1-22` declares a clean three-level precedence: API
   key org-binding > X-Org-Id header > user's current_org_id. Each
   level has a specific 4xx for failure (400 invalid UUID, 403
   mismatch, 404 no effective org). Ascend's Rule R-J.b cancel
   re-auth only covers the cancel endpoint; widening to a generic
   `X-Tenant-Id` header policy with the same precedence ladder
   would harden every endpoint uniformly.

2. **Multi-backend service contracts** — `openhands/app_server/event/`
   ships `EventServiceBase` ABC with `aws_event_service.py`,
   `google_cloud_event_service.py`, `filesystem_event_service.py`.
   Same pattern in `sandbox/`. Spring-ai-ascend's `agent-middleware`
   layer should publish similar paired SPI + 2-3 concrete backends
   per dimension so consumers can switch backend without rewriting
   code.

3. **Integration-provider directory pattern** — `openhands/app_server/
   integrations/{azure_devops,bitbucket,bitbucket_data_center,
   forgejo,github,gitlab,jira_dc}/` is a per-platform sub-package
   with its own `provider.py`. Clean discovery pattern; ascend's
   `agent-bus` MCP integration could mirror this layout.

4. **PolyForm Free Trial for enterprise tier** — `enterprise/LICENSE`
   shows a viable open-core monetisation model. Ascend's
   all-Apache-2.0 stance is correct for the JVM enterprise audience
   (Apache is what large vendors expect), but the *pattern* of an
   MIT-licensed core + restricted-license enterprise enhancements is
   worth understanding even if we don't adopt it.

5. **Conversation-as-aggregate** — `enterprise/storage/jira_conversation
   .py`, `linear_conversation.py`, `bitbucket_dc_webhook.py` show that
   OpenHands models a "conversation" as the durable unit-of-work. This
   is conceptually similar to ascend's Run aggregate, but conversation
   semantics are looser (no idempotency, no state machine validation).
   Ascend's Run is stronger; OpenHands's conversation is faster to
   evolve.

6. **Alembic migration discipline** — `enterprise/migrations/versions/
   001_create_feedback_table.py` … `010_create_offline_tokens_table.py`
   shows a clean numbered migration set. Ascend uses Flyway; the
   numbering convention + per-table-grouping in OpenHands is a useful
   reference.

7. **`AGENTS.md` as the contributor-facing rulebook** —
   `AGENTS.md:1-35` documents `make build`, pre-commit hook
   installation, lint requirements, and Git best practices in a
   single concise file. Ascend's equivalent is split across
   `CLAUDE.md`, `CONTRIBUTING.md`, and several governance docs;
   considering whether a single `AGENTS.md` should be the on-ramp
   would close the new-contributor onboarding gap.

8. **Multi-protocol git-integration spread** — Azure DevOps, Bitbucket
   (cloud + data center), Forgejo, GitHub, GitLab, Jira DC integrations
   each in their own sub-package. This breadth is what enterprise
   self-hosted customers actually need; ascend's planned git
   integration story should cover at least the same five.

## 10. Where we DIFFER

| # | Dimension | OpenHands evidence | spring-ai-ascend evidence |
|---|-----------|-------------------|---------------------------|
| 1 | **Runtime substrate** — OpenHands: CPython 3.12-3.13 + FastAPI + SQLAlchemy + Alembic. Ascend: JVM 21 + Spring WebFlux + R2DBC + Flyway. | `pyproject.toml:18` (`requires-python = ">=3.12,<3.14"`) | `D:\chao_workspace\spring-ai-ascend\pom.xml` (Java 21) |
| 2 | **Tenant scoping mechanism** — OpenHands: org-id at HTTP edge via X-Org-Id header. Ascend: tenant-id at HTTP edge + Postgres RLS at storage engine. | `enterprise/server/auth/org_context.py:1-22` | Rule R-J.a (RLS) + Rule R-J.b (HTTP re-auth) |
| 3 | **License** — OpenHands: MIT core + PolyForm Free Trial enterprise tier (one-month free, commercial license required for production). Ascend: uniformly Apache 2.0. | `LICENSE:1-13` + `enterprise/LICENSE:1-9` | `D:\chao_workspace\spring-ai-ascend\LICENSE` (Apache 2.0) |
| 4 | **Hardware sovereignty** — OpenHands: generic x86_64 cloud (AWS/GCP/Azure). Ascend: Ascend NPU + Kunpeng ARM64. | `containers/` Dockerfiles (`FROM python:3.12`), grep `Ascend\|Kunpeng` zero hits | Rule R-I five-plane + ARM64 + NPU |
| 5 | **Engine envelope vs DI service contract** — OpenHands: FastAPI `Depends(get_sandbox_service)` for backend selection. Ascend: typed `EngineEnvelope` + `EngineRegistry.resolve()`. | `openhands/app_server/sandbox/sandbox_service.py` (ABC) | `docs/contracts/engine-envelope.v1.yaml` + Rule R-M.a |
| 6 | **Persistent durability** — OpenHands: conversation aggregate (looser semantics). Ascend: Run aggregate with state-machine validation + idempotency table. | `enterprise/storage/jira_conversation.py`, `linear_conversation.py` | Rule R-C.2.b + `RunStateMachine.java` |
| 7 | **Posture-aware fail-closed** — OpenHands: no posture concept; "always production". Ascend: every config knob declares `dev/research/prod` defaults; PostureBootGuard fails closed. | `config.template.toml` single-mode | Rule D-6 + PostureBootGuard |
| 8 | **Evolution scope** — OpenHands: evaluation in sister repo (`OpenHands/benchmarks`); no in-tree trajectory store. Ascend: dedicated `agent-evolve` module on plane `evolution` + EvolutionExport scope per event. | `skills/`, sister repos referenced at `README.md:80` | `agent-evolve/module-metadata.yaml` + Rule R-M.e |
| 9 | **Integration breadth** — OpenHands: 7 git/issue-tracker integrations (Azure DevOps, Bitbucket cloud+DC, Forgejo, GitHub, GitLab, Jira DC). Ascend: HTTP-only ingress in v1. | `openhands/app_server/integrations/{azure_devops,bitbucket,...}/` | `docs/contracts/ingress-envelope.v1.yaml` |
| 10 | **Governance enforcement** — OpenHands: pre-commit hooks (ruff + mypy + frontend lint) + GitHub Actions SHA-pinning. Ascend: 144+ gate rules, ArchUnit, ADR-driven, recurring-defect ledger. | `AGENTS.md:23-33`, `dev_config/python/.pre-commit-config.yaml` | `D:\chao_workspace\spring-ai-ascend\CLAUDE.md` |

**Closing positioning note**: OpenHands is the closest thing to
spring-ai-ascend in this tranche — both ship server-side runtimes,
both have multi-org / multi-tenant ambitions, both have enterprise
adopters. But the *substrate is Python*, the *license is dual*, and
the *hardware target is generic cloud*. The fact that the
spring-ai-ascend-equivalent of OpenHands does not exist in the JVM
world (an open-source FastAPI+Alembic+Keycloak Devin-equivalent) is
*precisely* the gap that spring-ai-ascend addresses. The consumer /
single-developer agent space has overwhelmingly chosen Python or
Node.js/TypeScript; the *enterprise* tier of OpenHands (PolyForm-
licensed, Keycloak-integrated, Kubernetes-deployed) is the only thing
in this tranche pointing at a similar customer to ours, and it
confirms the un-served JVM-enterprise gap.
