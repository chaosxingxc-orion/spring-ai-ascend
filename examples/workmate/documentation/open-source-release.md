# Open-source release guide

This page explains **what ships in the public repo**, **what stays local**, and **how to run
WorkMate safely** as a localhost example — not as an internet-facing service without extra
hardening.

For day-to-day env vars see [configuration.md](./configuration.md). For architecture see
[architecture.md](./architecture.md).

---

## Intended use

WorkMate Workbench is an **example application** on top of `spring-ai-ascend`. It demonstrates
multi-agent sessions, expert teams, Developer Studio, MCP, and audit projections.

It is designed for:

- Local development and learning (`make dev`)
- Smoke scripts and offline validators under `scripts/dogfood/`
- Forking and adapting the control-plane patterns

It is **not** designed to be exposed to untrusted users on the public internet without adding
your own authentication layer and network boundary.

---

## What ships in git

The root `.gitignore` curates a small, brand-safe subset. A typical first commit is on the order
of **~1,300 files** (source, docs, curated `office/` examples — not local caches or dependencies).

| Area | Shipped | Not shipped (local / gitignored) |
|------|---------|----------------------------------|
| **Secrets** | `.env.local.example`, placeholder defaults in `application.yaml` | `**/.env.local`, real API keys |
| **Runtime data** | — | `workmate-api/data/`, `workmate-api/workspaces/`, `workmate-api/logs/`, `target/` |
| **Dependencies / build** | — | `node_modules/`, `dist/`, `coverage/` |
| **Market pull caches** | — | `office/_sources/`, `office/market/`, `office/skills-market/` |
| **Experts market** | 12 curated example teams under `office/experts-market/` (GPT-researcher, NCRE, …) | All other pulled expert packages on disk |
| **Connectors market** | `office/connectors-market/github`, `office/connectors-market/notion` | ~37 other connector dirs that may exist locally after marketplace pulls |
| **Internal design notes** | Published docs in `documentation/` | `docs/`, `ASCEND-DEPENDENCIES.md` |
| **Dogfood scripts** | Slim smoke set: `dogfood-all.sh`, `dogfood-v03-basics.sh`, offline validators, `mock-oa-mcp/` | 24 milestone / internal QA shell scripts (listed in `.gitignore`) |

Hand-written experts under `office/experts/` (e.g. `fund-analyst`, `prd-writer`) ship in full.

---

## Optional modules

These directories are part of the example tree but **not required** for the default path
(`make dev`):

| Module | Role |
|--------|------|
| `workmate-desktop/` | Optional Electron shell that packages the built UI. Run separately: `cd workmate-desktop && npm install && npm run dev`. |
| `member-runtimes/workmate-member-a2a/` | Optional per-member agent runtime communicating over A2A. Used with Docker Compose `--profile members`. |

The primary deliverable is **`workmate-api` + `workmate-ui` + `office/`**.

---

## Security model

### No API authentication

There is **no Spring Security filter** and no global bearer-token check. Any client that can
reach the API port can call `/api/v1/**`.

For non-local deployments:

- Bind to localhost or a private network, **or**
- Place a reverse proxy with authentication in front of `/api/**`, **or**
- Fork and add your own auth layer.

This is documented explicitly in [configuration.md](./configuration.md#example-vs-production-profile).

### Production profile

Local demo defaults keep **Developer Studio**, **cloud sessions (stub)**, and **OAuth mock
redirect** enabled so you can explore the full example without extra flags.

For shared or non-local use:

```bash
SPRING_PROFILES_ACTIVE=production
```

`application-production.yaml` sets:

- `workmate.cloud.enabled=false`
- `workmate.oauth.mock-enabled=false`
- `workmate.studio.enabled=false`

Override individual flags with `WORKMATE_*` env vars if needed. Startup logs warn when demo
features remain enabled under the production profile.

### High-impact surfaces (gated by flags, not user auth)

| Surface | Default (local) | Hardened |
|---------|-------------------|----------|
| Developer Studio (`/api/v1/studio/**`) | enabled | `WORKMATE_STUDIO_ENABLED=false` or `production` profile |
| Cloud sessions (`/api/v1/cloud/**`) | enabled (stub) | `WORKMATE_CLOUD_ENABLED=false` or `production` profile |
| OAuth mock page (`/oauth/mock-authorize`) | enabled | `WORKMATE_OAUTH_MOCK_ENABLED=false` or `production` profile |
| Inbound webhooks | disabled | When enabled, **non-empty secret is mandatory** |

### Agent shell execution is not a sandbox

The bash tool runs `/bin/bash -lc` with agent-supplied commands as the **API OS user**.
Mitigations are HITL approval, `ToolRiskPolicy` pattern blocks, and process timeouts/output
caps (`BoundedProcessRunner`) — not kernel-level isolation.

### Connector credentials

OAuth and API tokens for connectors are stored in plaintext JSON under the data directory
(`data/connector-credentials.json`). Treat the data dir like local secrets storage.

### Authoring path safety

Studio writes use layered validation (`OfficeImportValidator`, `StudioDraftStore.resolveWithin`,
symlink checks on skill file reads). Integration tests cover path-traversal rejection.

---

## Example MCP integration (qieman)

The shipped `fund-analyst` expert and default `application.yaml` MCP catalog include a **qieman**
(streamable HTTP) entry as an illustration of remote MCP wiring. It points at a public example
endpoint; it is **not** a committed secret.

To use it locally, set in `.env.local`:

```bash
WORKMATE_MCP_ENABLED=true
WORKMATE_MCP_QIEMAN_ENABLED=true
WORKMATE_MCP_QIEMAN_API_KEY=your-key
```

If you fork for a neutral demo, you can remove or replace this entry; tests reference `qieman`
as a stable connector id for OAuth/MCP behaviour checks.

---

## Prompts and dogfood assets

### Shipped smoke prompts (`office/prompts/`)

| File | Used by |
|------|---------|
| `prd-draft.md` | `scripts/dogfood/dogfood-prd-draft.sh` |
| `hitl-probe.txt` | `scripts/dogfood/hitl-probe.sh` |

### Internal QA prompts (not part of the public smoke set)

Files matching `office/prompts/*-dogfood.md` are **local team-topology QA prompts** used during
milestone sign-off. They are documented in [office/prompts/README.md](../office/prompts/README.md)
and **excluded from git** via `.gitignore` — they are not part of the public release.

---

## Maintainer pre-commit checklist

Before the first public commit or a release tag:

1. **Never commit secrets** — confirm `.env.local` is ignored:
   ```bash
   git check-ignore -v examples/workmate/workmate-api/.env.local
   ```
2. **Review the staged file list** — avoid accidental cache/runtime adds:
   ```bash
   git ls-files --others --exclude-standard examples/workmate | wc -l
   git ls-files --others --exclude-standard examples/workmate | rg -i '\.env|secret|/data/|node_modules|target/'
   ```
   (Expect no matches.)
3. **Confirm market curation** — only whitelisted `experts-market/` and `connectors-market/`
   paths should appear:
   ```bash
   git ls-files --others --exclude-standard examples/workmate/office/connectors-market/
   git ls-files --others --exclude-standard examples/workmate/office/experts-market/
   ```
4. **Run tests** — `make test` (428+ backend tests + Vitest).
5. **License** — this example inherits the license at the repository root; confirm it applies to
   your distribution context.

---

## Related documentation

- [Getting started](./getting-started.md) — prerequisites and `make dev`
- [Configuration](./configuration.md) — env vars, production profile, webhooks, CORS
- [Testing](./testing.md) — unit vs integration tests, timeline SSOT
- [Architecture](./architecture.md) — components and event sourcing
- [office/README.md](../office/README.md) — config layout and market curation
- [Release notes](./release-notes.md) — v0.3 highlights
