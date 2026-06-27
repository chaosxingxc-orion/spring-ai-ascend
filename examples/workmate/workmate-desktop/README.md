# WorkMate Desktop

> **Status**: desktop packaging/distribution is **on hold**. This directory keeps the development
> shell and the ACP sidecar capability, but is **not part of the current deliverable** — use the
> **Web UI** (`workmate-ui` + `workmate-api`) for day-to-day work.

Electron shell. The renderer reuses `workmate-ui`; business logic still goes through `workmate-api`
over REST + SSE.

## Develop

Terminal 1 — Web UI (Vite proxies `/api` → `:8080`):

```bash
cd ../workmate-ui && npm run dev
```

Terminal 2 — Electron (spawns `workmate-api` automatically by default):

```bash
npm install
npm run dev
```

If the API is already running on `:8080`, Electron detects it and goes `ready` without spawning a
duplicate.

### Environment variables

| Variable | Notes |
|----------|-------|
| `WORKMATE_API_URL` | API base (default `http://127.0.0.1:8080`) |
| `WORKMATE_UI_DEV_URL` | Main window URL (default `http://127.0.0.1:5174`) |
| `WORKMATE_SKIP_API_SPAWN=1` | Don't spawn the API; only connect to an existing one |
| `WORKMATE_ACP_SIDECAR_URL` | streamable-http / SSE sidecar base (for the relay) |

## Packaging (electron-builder)

Bundles the built UI (`VITE_API_BASE=http://127.0.0.1:8080`):

```bash
npm run pack:ui    # build workmate-ui → resources/ui
npm run dist       # unpacked release/mac*/WorkMate.app (--dir)
npm run package    # full installers (dmg / AppImage / nsis)
```

After packaging, the main process serves `resources/ui` from a local static server (no Vite needed).

## preload contract

| API | Status |
|-----|--------|
| `getApiBaseUrl()` | ✅ |
| `onApiStatus(starting\|ready\|error)` | ✅ |
| `onOAuthCallback({ state, code })` | ✅ `workmate://oauth/callback` |
| `pickWorkspaceDirectory()` | ✅ |
| `openPath()` | ✅ |
| `relayAcpNdjson` / `relayStreamableHttp` | ✅ |

## ACP sidecar relay

```bash
npm run sidecar-relay -- <session-id> ../scripts/dogfood/fixtures/acp-sidecar-sample.ndjson
WORKMATE_ACP_SIDECAR_URL=http://127.0.0.1:9000/acp/stream npm run sidecar-relay -- <session-id>
npm test
```

Renderer: session header ⋯ → import ACP sidecar / pull streamable-http.

## OAuth deep link

The desktop app registers the `workmate://` protocol. Mock authorize page:
`GET /oauth/mock-authorize` (connector marketplace → redirect-authorize walkthrough).
