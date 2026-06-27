# Architecture

WorkMate Workbench is a multi-agent collaboration workbench layered on top of the
`spring-ai-ascend` `agent-runtime`. It is a single Spring Boot control plane plus a React UI.

## Components

| Module | Responsibility |
|--------|----------------|
| `workmate-api` | Spring Boot control plane: session CRUD, agent loop, HITL approvals, SSE streaming, MCP gateway, Developer Studio, audit ledger |
| `workmate-ui` | React + Vite SPA: chat, expert market, Developer Studio, settings |
| `workmate-desktop` | Optional Electron shell that packages the built UI |
| `office/` | File-based configuration (the single source of truth) for experts, skills, playbooks, the welcome page, and curated team examples |
| `member-runtimes/` | Optional per-member agent runtime template communicating over A2A |

## Request flow

```
React UI  ──REST/SSE──>  workmate-api  ──embeds──>  agent-runtime (+ OpenJiuwen ReAct)
                              │
                              ├── office/      (YAML experts/skills/teams, loaded at startup)
                              ├── data/        (local JSON stores: shares, favorites, install state, drafts)
                              ├── workspaces/  (per-session working directory)
                              └── PostgreSQL   (sessions, run_events, usage, audit)
```

The UI never talks to the LLM directly; it calls the control plane, which runs the agent loop
and streams `message.delta` / `tool.start` / `tool.end` / `approval.required` / `run.completed`
events over SSE.

## Event sourcing

All run activity is recorded as ordered `run_events`. The UI timeline, token accounting, and the
audit ledger are all *projections* of this single event stream — there is no separate dual-write
path. The audit ledger is append-only and redacts sensitive payload fields (tokens, keys,
credentials) before persistence.

**Timeline single source of truth:** `run_events` is authoritative on the backend.
`SessionPersistenceService` and audit projectors derive chat items and ledger entries from it.
On the frontend, chat is hydrated from server projections when a session opens; live updates
arrive via SSE (team sessions may also poll `run_events`). Client-side hydrate helpers during
streaming must not diverge from server shape. See [testing.md](./testing.md#timeline-single-source-of-truth).

## Expert teams

A team expert defines members, a lead, and a coordination topology (e.g. orchestrator). The
`TeamRuntimeRouter` selects a runtime; members run in-process by default and hand results back to
the lead via a mailbox protocol. Team activity is emitted as `team.*` run events and projected into
the team visualization.

## Developer Studio

Studio edits never mutate the built-in/market config directly. Edits land in a writable **draft
layer** under `data/office-drafts/`, which overrides the read-only built-in and market layers. A
`POST /studio/reload` applies changes without a restart. Studio file writes are constrained to safe
file names and validated to stay within the draft directory.

## Configuration layering

```
office/experts      (built-in, read-only)
office/experts-market   (curated examples, read-only)
data/office-drafts  (Studio drafts, writable, highest precedence)
```

The same layering applies to skills. See [configuration.md](./configuration.md) for environment
variables and security-relevant defaults.
