# workmate-api

Spring Boot control plane: session CRUD, the agent loop, human-in-the-loop approvals, SSE streaming,
the MCP gateway, artifacts, and the audit ledger.

## Quick start

```bash
cp .env.local.example .env.local   # set WORKMATE_LLM_API_KEY
../scripts/run-local.sh            # from the examples/workmate root: make api
```

## API overview

### Sessions

| Method | Path | Notes |
|--------|------|-------|
| POST | `/api/v1/sessions` | Create a task + on-disk workspace |
| GET | `/api/v1/sessions` | Task list (by `updatedAt` desc, with usage and artifact paths) |
| GET | `/api/v1/sessions/summary` | Lightweight sidebar list (sessions-table fields only, no N+1) |
| GET | `/api/v1/sessions/{id}` | Task detail |

### Agent + SSE

| Method | Path | Notes |
|--------|------|-------|
| POST | `/api/v1/sessions/{id}/prompt` | SSE stream: `message.delta`, `tool.start/end`, `approval.required`, `run.completed` |

- Embeds `agent-runtime` + OpenJiuwen ReAct in the same JVM.
- Workspace tools are scoped per session: `workmate_read__{sessionId}`, `workmate_write__{sessionId}`,
  `workmate_bash__{sessionId}` (the UI/logs may show the de-suffixed name, e.g. `workmate_read`).

### Human-in-the-loop (HITL)

| Method | Path | Notes |
|--------|------|-------|
| GET | `/api/v1/sessions/{id}/pending-approvals` | Pending approvals |
| POST | `/api/v1/approvals/{id}` | `{ "decision": "approve"\|"deny", "always": false }` |

- High-risk `workmate_bash` calls (e.g. `rm`) pause before execution and push `approval.required` over SSE.
- A denied command is **not** executed.

### MCP gateway

| Method | Path | Notes |
|--------|------|-------|
| GET | `/api/v1/mcp/servers` | Registered MCP servers |
| GET | `/api/v1/mcp/tools` | Proxied tool list |

- stdio subprocess (docs-fs) or remote **streamable HTTP** servers.
- Tool names: `mcp__{serverId}__{toolName}`.

Configure a remote MCP server in `workmate-api/.env.local` (real values are never committed; the
gateway is read at **startup**, so restart the API after changing it):

```bash
WORKMATE_MCP_ENABLED=true
WORKMATE_MCP_QIEMAN_ENABLED=true
WORKMATE_MCP_QIEMAN_URL=https://your-mcp-endpoint/mcp
WORKMATE_MCP_QIEMAN_API_KEY=your-key
# optional: only the remote MCP, disable the local docs-fs
WORKMATE_MCP_DOCS_FS_ENABLED=false
```

### Artifacts & workspace

| Method | Path | Notes |
|--------|------|-------|
| GET | `/api/v1/sessions/{id}/artifacts` | Workspace output list |
| GET | `/api/v1/sessions/{id}/artifacts/content?path=` | Read a file |
| GET | `/api/v1/sessions/{id}/workspace/entries?path=` | Directory tree (files / subdirs) |

- Single-expert sessions: OpenJiuwen `conversation_id` = `sessionId`, supporting multi-turn resume.
- Team sub-runs / lead synthesis: `conversation_id` = `sessionId:taskId`, isolating each member's ReAct trace.

### Audit ledger

- `run_events` is **append-only / WORM** (a Postgres trigger rejects UPDATE/DELETE).
- Redaction before persistence: secrets/PII scrubbed; large fields truncated to a ≤200-char preview + hash;
  payloads capped at 16 KB.
- **Fail-close**: `approval.decided` is written to the audit ledger before the approval is executed.
- **DLQ**: on write failure → `data/audit-dlq/audit-dlq.jsonl`; `/actuator/health` exposes DLQ metrics.
- Live SSE still streams the original text; only the audit ledger is redacted.

### Office artifact contract

- Office experts (`tags: office` or `officeCapability`) initialize
  `office/{capability}/{sessionId}/inputs|outputs|request.md` on session create.
- The agent may not write `inputs/`; drafts go to `outputs/`.
- `GET /sessions` returns `officeArtifactRoot`; artifact entries carry `officeCapability` / `officeZone`.

## Environment variables

| Variable | Notes |
|----------|-------|
| `WORKMATE_LLM_API_KEY` | LLM API key (required to actually run an agent) |
| `WORKMATE_LLM_API_BASE` | OpenAI-compatible endpoint (set locally in `.env.local`) |
| `WORKMATE_LLM_MODEL` | Model id |
| `WORKMATE_MCP_ENABLED` | Default `false`; set `true` **at startup** to enable MCP |
| `WORKMATE_MCP_FS_ROOT` | Root directory the filesystem MCP may read |

Full template: [.env.local.example](./.env.local.example). More config:
[../documentation/configuration.md](../documentation/configuration.md).

## Tests

```bash
../../../mvnw test                          # or: make test (from examples/workmate)
../scripts/dogfood/dogfood-all.sh           # smoke scenarios (needs API + LLM key)
../scripts/dogfood/run-dogfood-validators.sh  # offline validators (no LLM)
```

## Related docs

- [Architecture](../documentation/architecture.md)
- [Configuration](../documentation/configuration.md)
- [Getting started](../documentation/getting-started.md)
