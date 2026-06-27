# Release Notes

## WorkMate Workbench v0.3

First open-source release of the WorkMate Workbench — a multi-agent collaboration example built
on top of `spring-ai-ascend` (Spring Boot control plane + React UI + a file-based `office/`
configuration layer).

### Highlights

- **Sessions & agent loop** — create a task and stream the agent's reasoning, tool calls, and
  output over SSE.
- **Multi-agent expert teams** — multi-member orchestration (sequential / orchestrator / message-bus
  / shared-state topologies) with a single `run_events` event source projected into the UI, a live
  team blackboard, and per-member artifacts.
- **Human-in-the-loop (HITL)** — high-risk tool calls (e.g. shell `rm`) pause for explicit approval
  before execution.
- **Developer Studio** — author and edit experts, skills, playbooks, and teams through a draft layer
  with hot reload (no restart).
- **MCP gateway** — connect Model Context Protocol servers over stdio or streamable HTTP.
- **Multi-model catalog** — a configurable list of models (DeepSeek, OpenAI, a local OpenAI-compatible
  server, …); users pick a model per session. Each entry can carry its own provider/endpoint/key.
- **Audit ledger** — append-only `run_events` (WORM) with payload redaction for sensitive fields.

### Developer experience

- **One-command startup**: `make setup` then `make dev` brings up Postgres + API + UI together.
  `make` lists all targets (`db`, `api`, `ui`, `test`, `build`, `stop`).
- Out of the box the default model points at a real provider (DeepSeek) — set `WORKMATE_LLM_API_KEY`
  in the gitignored `.env.local` and you're running.

### Security & configuration

- No secrets are committed. All real keys/endpoints live only in the local, gitignored
  `workmate-api/.env.local`; committed defaults are placeholders.
- Hardening: path-traversal guards on authoring writes, safe YAML loading, ZIP-bomb limits on
  imports, atomic JSON writes, constant-time webhook secret checks, a sandboxed artifact-preview
  response, strict CORS defaults, and startup warnings for weak DB / placeholder LLM credentials.
- The Developer Studio authoring surface is gated by `WORKMATE_STUDIO_ENABLED`.
- **No API authentication** — local example only unless you add auth in front of `/api/**`.
  See [open-source-release.md](./open-source-release.md).

### Requirements

- JDK 21, Node.js 20+, Docker (for the bundled PostgreSQL 16), `make`, and an OpenAI-compatible
  LLM key.

See [getting-started.md](./getting-started.md), [architecture.md](./architecture.md),
[configuration.md](./configuration.md), and [open-source-release.md](./open-source-release.md)
for details.
