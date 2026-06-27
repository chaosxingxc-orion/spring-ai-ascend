# scripts

Core local-dev helpers live at the top level; smoke scripts and offline validators live under
`dogfood/`.

## Dev helpers

| Script | Purpose |
|--------|---------|
| `dev.sh` | One command: Postgres + API + UI (used by `make dev`) |
| `run-local.sh` | Load `.env.local` and start the API |
| `dev-api.sh` / `dev-ui.sh` | Start the API / UI individually |

## dogfood/ (shipped subset)

End-to-end smoke scripts (require the API on `:8080` and `WORKMATE_LLM_API_KEY`) plus offline
validators (no LLM needed):

```bash
# smoke run (write-hello + PRD + HITL deny)
./scripts/dogfood/dogfood-all.sh           # add --with-mcp for MCP scenario

# P0 smoke only (health + session + SSE)
./scripts/dogfood/dogfood-v03-basics.sh

# offline validator fixtures (no API/LLM needed)
./scripts/dogfood/run-dogfood-validators.sh

# audit hash chain (live API, no LLM)
./scripts/dogfood/dogfood-audit-chain.sh
```

Internal milestone / team-topology / connector-specific dogfood scripts are kept locally but
excluded from git (see `.gitignore`).

### MCP smoke prerequisites

`WORKMATE_MCP_ENABLED` is read **only at JVM startup**. To run MCP smoke:

1. Set `WORKMATE_MCP_ENABLED=true` in `workmate-api/.env.local`.
2. Restart the API: `./scripts/run-local.sh`.
3. Run `./scripts/dogfood/dogfood-all.sh --with-mcp` or `./scripts/dogfood/read-readme-via-mcp.sh`.

`require_mcp` (in `dogfood/_common.sh`) fails early with this hint when MCP is off.
