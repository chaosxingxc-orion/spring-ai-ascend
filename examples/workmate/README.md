# WorkMate Workbench

**English** | [中文](./README.zh-CN.md)

A multi-agent collaboration workbench built as an example on top of `spring-ai-ascend`.
It pairs a Spring Boot control plane with a React UI and a file-based configuration layer
(`office/`) for experts, skills, playbooks, and teams.

## Features

- **Sessions & agent loop** — create a task, stream the agent's reasoning, tool calls, and output over SSE.
- **Human-in-the-loop (HITL)** — high-risk tool calls (e.g. shell `rm`) pause for approval before execution.
- **Expert teams** — multi-member orchestration with several coordination topologies and a single `run_events` event source projected into the UI.
- **Developer Studio** — author/edit experts, skills, playbooks, and teams through a draft layer with hot reload.
- **MCP gateway** — connect Model Context Protocol servers (stdio or streamable HTTP).
- **Audit ledger** — append-only `run_events` with payload redaction for sensitive fields.

## Screenshots

A multi-agent research team producing a report — live team members, the shared blackboard,
and generated artifacts on the right:

![Multi-agent research run](./documentation/images/team-session.png)

The workbench home — pick a category or just describe a task to start:

![WorkMate home](./documentation/images/home.png)

The marketplace — summon ready-made experts/teams and install skills:

| Expert & team marketplace | Skill marketplace |
|---------------------------|-------------------|
| ![Expert marketplace](./documentation/images/market-experts.png) | ![Skill marketplace](./documentation/images/market-skills.png) |

## Architecture

```
workmate-api/      Spring Boot control plane (sessions, agent loop, HITL, SSE, MCP, Studio, audit)
workmate-ui/       React + Vite single-page app (chat, market, studio, settings)
workmate-desktop/  Optional Electron shell that packages the UI
office/            File-based config: experts/skills/playbooks/welcome + curated examples
member-runtimes/   Optional per-member agent-runtime template (A2A)
docker/            docker-compose for Postgres
scripts/           Local dev (dev.sh/run-local.sh/dev-ui.sh) and smoke scripts
documentation/     Published documentation (getting started, architecture, configuration)
Makefile           Common tasks: make dev / db / api / ui / test / build
```

The backend embeds the ascend `agent-runtime`; the UI talks to it over REST + SSE (Vite proxies
`/api` in dev). Configuration is read from `office/` at startup and can be hot-reloaded via Studio.

See [documentation/architecture.md](./documentation/architecture.md) for details.

## Quick start

Prerequisites: JDK 21, Node.js 20+, Docker (for Postgres), and `make`.

```bash
make setup           # create workmate-api/.env.local from the template
#  → edit workmate-api/.env.local and set WORKMATE_LLM_API_KEY
make dev             # start Postgres + API (:8080) + UI (:5174) in one command
```

Then open http://localhost:5174. `Ctrl-C` stops the API and UI; `make stop` stops Postgres.

Run `make` to see all targets. Prefer separate terminals? `make db`, then `make api` and `make ui`.

Full setup notes: [documentation/getting-started.md](./documentation/getting-started.md).

## Configuration

All secrets (LLM keys, MCP keys, webhook secrets) are supplied via the local, gitignored
`workmate-api/.env.local`; nothing real is committed. High-risk surfaces and provider endpoints
are configurable and default to safe/neutral values. See
[documentation/configuration.md](./documentation/configuration.md).

**Deploying beyond localhost?** Read
[documentation/open-source-release.md](./documentation/open-source-release.md) first — this app
has **no API authentication**; use the `production` profile and your own network boundary.

## Tests

```bash
make test     # backend (Maven) + frontend (Vitest)
```

## Documentation

- [Getting started](./documentation/getting-started.md)
- [Architecture](./documentation/architecture.md)
- [Configuration](./documentation/configuration.md)
- [Testing](./documentation/testing.md)
- [Open-source release guide](./documentation/open-source-release.md) — what ships, security boundaries, maintainer checklist
- [Release notes](./documentation/release-notes.md)

## License

See the repository root for license terms.
