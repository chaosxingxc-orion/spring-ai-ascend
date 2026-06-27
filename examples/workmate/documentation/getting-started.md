# Getting Started

## Prerequisites

- JDK 21
- Node.js 20+ (npm)
- Docker (for the bundled PostgreSQL 16)
- `make`
- An OpenAI-compatible LLM endpoint + API key (any provider that speaks the OpenAI API)

## Fastest path

```bash
make setup       # creates workmate-api/.env.local from the template
#  → edit workmate-api/.env.local and set WORKMATE_LLM_API_KEY
make dev         # Postgres + API (:8080) + UI (:5174), one command
```

Open `http://localhost:5174`. `Ctrl-C` stops the API and UI; `make stop` stops Postgres.
Run `make` to list all targets.

> **Security note:** WorkMate is a **local example** with no API authentication. Before any
> shared or non-local deployment, read
> [open-source-release.md](./open-source-release.md) and use `SPRING_PROFILES_ACTIVE=production`.

The rest of this page explains the same steps individually, for when you want more control.

## 1. Configure secrets locally

Secrets are never committed. Copy the template and fill in your own values in the
gitignored `workmate-api/.env.local`:

```bash
make setup        # or: cp workmate-api/.env.local.example workmate-api/.env.local
```

Minimum required to run an agent (the app also ships `deepseek-chat`, `gpt-4o-mini`,
and `local-model` catalog entries; `WORKMATE_LLM_API_KEY` enables the default
DeepSeek entry, while `WORKMATE_OPENAI_API_KEY` enables the OpenAI entry):

```bash
WORKMATE_LLM_API_KEY=sk-your-key
# Optional — point the default "local-model" entry at your own OpenAI-compatible endpoint:
# WORKMATE_LLM_API_BASE=http://localhost:11434/v1
# WORKMATE_LLM_MODEL=your-model
```

## 2. Start PostgreSQL

```bash
make db          # or: docker compose -f docker/docker-compose.yml up -d
```

Override `WORKMATE_DB_URL` / `WORKMATE_DB_USERNAME` / `WORKMATE_DB_PASSWORD` if you use a
different database. Flyway owns the schema; Hibernate validates against it.

## 3. Run the backend

```bash
make api         # or: ./scripts/run-local.sh
```

This loads `workmate-api/.env.local` and starts `workmate-api` on port `8080`.

## 4. Run the frontend

```bash
make ui          # or: ./scripts/dev-ui.sh
```

Vite serves the UI on `http://localhost:5174` and proxies `/api` to the backend.

## 5. (Optional) Desktop shell

```bash
cd workmate-desktop && npm install && npm run dev
```

## Running tests

```bash
make test
# equivalently:
# cd workmate-api && ../../../mvnw test      # backend (JUnit + Testcontainers/H2)
# cd workmate-ui  && npm ci && npm test      # frontend (Vitest)
```

## Smoke scripts

`scripts/dogfood/` ships a small smoke set (`dogfood-all.sh`, `dogfood-v03-basics.sh`) and offline
validators (`run-dogfood-validators.sh`). Live smoke scripts require `WORKMATE_LLM_API_KEY`; offline
validators do not need the API or an LLM.

See [open-source-release.md](./open-source-release.md#prompts-and-dogfood-assets) for which
`office/prompts/` files are part of the public smoke set vs internal QA.

## Further reading

- [Open-source release guide](./open-source-release.md) — what ships in git, security model, maintainer checklist
- [Configuration](./configuration.md)
- [Testing](./testing.md)
