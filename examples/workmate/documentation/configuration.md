# Configuration

All configuration is environment-driven. Real secrets live only in the local, gitignored
`workmate-api/.env.local` (copy from `.env.local.example`); committed defaults are neutral/safe.

For **what ships in the open-source tree** and the **full security model** (no API auth, optional
modules, market curation), see [open-source-release.md](./open-source-release.md).

## LLM

The app ships a **model catalog** (`workmate.llm.catalog` in `application.yaml`) with several selectable
models; users pick one per session in the UI. Each catalog entry can override the global
provider/endpoint/key, so you can mix a local model with hosted ones (DeepSeek, OpenAI, …) and only the
providers you supply a key for become usable. Blank entry fields fall back to the global `workmate.llm`
defaults below.

Default catalog entries: `deepseek-chat` (default), `gpt-4o-mini`, `local-model`. The default points at
the real DeepSeek endpoint, so dropping your key into `WORKMATE_LLM_API_KEY` is enough to run.

| Variable | Default | Notes |
|----------|---------|-------|
| `WORKMATE_LLM_DEFAULT_MODEL` | `deepseek-chat` | Catalog `id` selected when a session doesn't specify one. |
| `WORKMATE_LLM_API_KEY` | `sk-local-placeholder` | Drives the default `deepseek-chat` entry. Set to your real key locally. |
| `WORKMATE_LLM_API_BASE` | `https://api.deepseek.com/v1` | Default endpoint; any OpenAI-compatible server. |
| `WORKMATE_LLM_MODEL` | `deepseek-chat` | Default model name. |
| `WORKMATE_OPENAI_API_KEY` | _(empty)_ | Set to enable the `gpt-4o-mini` catalog entry. |
| `WORKMATE_OPENAI_API_BASE` | `https://api.openai.com/v1` | |
| `WORKMATE_LOCAL_API_BASE` | `http://localhost:11434/v1` | Endpoint for the `local-model` entry (Ollama/vLLM/LM Studio). |
| `WORKMATE_LOCAL_MODEL` | `local-model` | Model name for the `local-model` entry. |
| `WORKMATE_LLM_SSL_VERIFY` | `true` | |

The application warns at startup if the global LLM key is unset/placeholder. To add more models, append
entries to `workmate.llm.catalog` (`id`, `display-name`, `provider`, `api-base`, `api-key`, `model-name`,
`capabilities`).

## Database

| Variable | Default | Notes |
|----------|---------|-------|
| `WORKMATE_DB_URL` | `jdbc:postgresql://localhost:5432/workmate` | |
| `WORKMATE_DB_USERNAME` | `workmate` | |
| `WORKMATE_DB_PASSWORD` | `workmate` | Dev default; the app logs a startup warning if left unchanged. Override for any non-local deployment. |

## Feature toggles

| Variable | Default | Notes |
|----------|---------|-------|
| `WORKMATE_STUDIO_ENABLED` | `true` | Developer Studio authoring API. Set `false` to lock it down. |
| `WORKMATE_CLOUD_ENABLED` | `true` | Cloud session API (local stub). Set `false` or use `spring.profiles.active=production`. |
| `WORKMATE_OAUTH_MOCK_ENABLED` | `true` | Mock OAuth redirect page at `/oauth/mock-authorize` (dogfood). Set `false` in production. |
| `WORKMATE_MCP_ENABLED` | `false` | Master switch for the MCP gateway. |
| `WORKMATE_MCP_DOCS_FS_ENABLED` | `false` | Filesystem MCP server (reads `WORKMATE_MCP_FS_ROOT`). |
| `WORKMATE_WEBHOOK_GENERIC_ENABLED` | `false` | Inbound automation webhook. When enabled, a non-empty secret is mandatory. |

## Inbound webhooks

A webhook channel that is enabled **must** have a configured secret; an enabled channel with an
empty secret refuses requests. Secrets are compared in constant time and must be presented in the
`X-WorkMate-Webhook-Secret` header (request-body tokens are only accepted for provider
URL-verification handshakes).

```bash
WORKMATE_WEBHOOK_GENERIC_ENABLED=true
WORKMATE_WEBHOOK_GENERIC_SECRET=set-a-strong-random-secret
```

## Example vs production profile

Local demo defaults keep **Developer Studio**, **cloud sessions (stub)**, and **OAuth mock redirect**
enabled so you can explore the full example without extra flags.

For any shared or non-local deployment, activate the hardened profile:

```bash
SPRING_PROFILES_ACTIVE=production
```

`application-production.yaml` turns off `workmate.cloud.enabled`, `workmate.oauth.mock-enabled`, and
`workmate.studio.enabled`. You can still override individual flags with `WORKMATE_*` environment
variables. Startup logs warn when demo features remain enabled under the production profile.

This app ships **without API authentication** — treat it as a local example only unless you add your
own auth layer in front of `/api/**`.

## Network / CORS

CORS for `/api/**` is restricted to the dev UI origin(s). Override with
`workmate.web.allowed-origins` (comma-separated). For any shared/non-local deployment, place the
service behind a trusted network boundary or reverse proxy.

## Workspace file previews

The `/preview/**` endpoint serves agent-authored workspace files with a strict
`Content-Security-Policy: sandbox` and `X-Content-Type-Options: nosniff`, so untrusted HTML/JS/SVG
cannot execute in the API origin.

## MCP servers

MCP servers are declared under `workmate.mcp.servers`. Endpoint URLs and API keys are injected from
the environment (`.env.local`); no real endpoint or key is committed. The filesystem server is
restricted to `WORKMATE_MCP_FS_ROOT`.
