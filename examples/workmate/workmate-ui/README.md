# workmate-ui

React + Vite single-page workbench (chat, market, studio, settings). The optional Electron shell
in `workmate-desktop/` reuses this renderer.

## Develop

From the example root, one command brings up the API + UI together:

```bash
make dev          # Postgres + API (:8080) + UI (:5174)
```

Or run just the UI (against an API already on `:8080`):

```bash
../scripts/dev-ui.sh
# or: npm install && npm run dev -- --port 5174
```

Open http://localhost:5174 (Vite proxies `/api` → `:8080`).

## Try it

1. Click "+ 新建任务".
2. Type: `Create a file named hello.md with content: Hello from WorkMate`.
3. Watch the tool cards and the agent reply; the file is written to the session workspace.

## Build & test

```bash
npm run build
npm test          # vitest：SSE 解析 + chat reducer
```

产物在 `dist/`。生产部署时需反代 `/api` 或设置 `VITE_API_BASE`。
