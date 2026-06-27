# docker

Compose stacks and Dockerfiles for the WorkMate example.

| Command | Brings up |
|---------|-----------|
| `docker compose -f docker/docker-compose.yml up -d` | Postgres only (for local `make dev`) |
| `make compose-up` | Full stack: Postgres + API (`:8080`) + UI (`:5174`) |
| `docker compose -f docker/docker-compose.yml --profile members up -d` | Optional A2A member runtimes |

## Full stack (`app` profile)

`make compose-up` creates `workmate-api/.env.local` if needed, builds the API jar,
then builds and starts the `api` and `ui` images:

- `api` uses `workmate-api/Dockerfile` (a pre-built Spring Boot jar), loads
  `workmate-api/.env.local`, mounts `office/` read-only, and persists
  `workmate-api/data/` plus `workmate-api/workspaces/`.
- `ui` uses `workmate-ui/Dockerfile` (multi-stage build → nginx) and proxies `/api` to `api:8080`.

Set your LLM key in the local env file first:

```bash
make setup
# edit workmate-api/.env.local and set WORKMATE_LLM_API_KEY=sk-your-deepseek-key
make compose-up        # then open http://localhost:5174
make compose-down
```
