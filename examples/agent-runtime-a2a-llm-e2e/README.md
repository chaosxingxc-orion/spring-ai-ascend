# Agent Runtime A2A LLM E2E Example

## Purpose

This example shows how to run an `agent-runtime` application that exposes an A2A endpoint, hosts an openJiuwen ReAct agent behind that endpoint, and exercises it from an A2A client perspective only.

The example lives at `examples/agent-runtime-a2a-llm-e2e` and includes:

- a Spring Boot server application: `com.huawei.ascend.examples.a2a.OpenJiuwenA2aAgentRuntimeApplication`
- a console client: `com.huawei.ascend.examples.a2a.A2aConsoleClientApplication`
- an automated end-to-end test that validates the A2A flow against a local OpenAI-compatible LLM gateway

## What It Verifies

This example verifies the intended boundary for the sample:

1. `agent-runtime` hosts and exposes the agent through A2A.
2. The client discovers `/.well-known/agent-card.json`.
3. The client sends a streaming JSON-RPC request to `/a2a`.
4. The client reads streamed A2A events until the run completes.
5. A simple prompt of `ping` produces a final visible answer of `pong`.

The current automated E2E test passes and asserts the `ping -> pong` behavior.

## Gateway Facade Sample

This example module also contains a minimal Gateway facade sample under
`com.huawei.ascend.examples.a2a.gateway`. It demonstrates how a platform layer
can keep a registry of multiple single-agent runtime instances and expose a
small HTTP facade for:

- runtime self-registration, lease renewal, and deregistration
- tenant-scoped agent listing and AgentCard lookup
- route resolution by `tenantId` and `agentId`
- a minimal A2A JSON-RPC forwarding endpoint for customer reference
- tenant-scoped `RouteGrant` issuing and validation for runtime-to-runtime A2A calls
- asynchronous A2A interaction telemetry record/query APIs

The facade sample is intentionally local and in-memory. It is a customer-facing
example for pluggable gateway integration, not the production `agent-service`
implementation.

### Runtime-to-Runtime Discovery and Telemetry

For east-west runtime calls, the sample keeps the examples layer as the
discovery, route-policy, and observability authority without forcing every
runtime-to-runtime payload through the examples data plane:

1. source runtime asks the facade for a short-lived `RouteGrant`
2. facade resolves a healthy target runtime and signs the grant
3. source runtime can call the target runtime A2A endpoint directly
4. target runtime can validate the grant before accepting the call
5. source and target runtimes can asynchronously report interaction telemetry

The sample exposes these minimum HTTP endpoints:

- `POST /v1/route-grants/resolve`
- `POST /v1/route-grants/validate`
- `POST /v1/a2a-interactions`
- `GET /v1/a2a-interactions?tenantId=...&correlationId=...`

This keeps runtime caches small: runtimes cache scoped grants with TTL and
policy version, not a full `tenantId x sourceAgentId x targetAgentId x replica`
authorization table.

### Gateway DFX Reference Shape

The Gateway facade sample is not a five-nines production gateway, but it now
shows the minimum DFX shape expected from a customer-facing platform facade:

- runtime registration records carry TTL / lease information
- expired leases are marked `UNREACHABLE` and are no longer routable
- cold, draining, unreachable, and at-capacity runtimes fail closed with clear
  error codes
- multiple runtime replicas are resolved through the same route view, and only
  `READY` replicas can receive new traffic
- the A2A forwarding endpoint returns trace headers for route resolution,
  response start, total forwarding time, and selected runtime instance
- route grants are short-lived, tenant-scoped, method-scoped, and signed
- A2A interaction telemetry carries correlation, route latency, first-byte
  latency, total latency, status, and selected runtime identity

Production deployments must still add persistent or reconstructable registry
state, runtime identity authentication, tenant-agent authorization, rate
limiting, circuit breaking, multi-AZ deployment, same-city disaster recovery,
cross-region recovery, SLA/SLO dashboards, and error-budget governance.

## Quick start (config templates + scripts)

From `examples/agent-runtime-a2a-llm-e2e`, copy a template, fill it, and run through the helper script:

```bash
cp .env.openai-compatible.example .env   # or .env.ollama.example, then edit
bash scripts/test-e2e.sh .env            # installs agent-runtime + runs the E2E suite
# Windows: ./scripts/test-e2e.ps1 -EnvFile .env
```

For manual server verification, prefer the server helper script because it loads
the env file before starting Spring Boot:

```bash
bash scripts/run-server.sh .env
# Windows: ./scripts/run-server.ps1 -EnvFile .env
```

Templates (the `.env` you fill is gitignored; the `*.example` templates are tracked):

- `.env.example` — every variable with inline docs.
- `.env.ollama.example` — local Ollama via its OpenAI-compatible `/v1` surface (`gemma4:latest`).
- `.env.openai-compatible.example` — a cloud OpenAI-compatible API (no real key committed).

> `.env` is not loaded automatically by Maven or Spring Boot. The helper scripts
> load it with shell sourcing before launching Maven. If you run `./mvnw ...
> spring-boot:run` directly, only variables already exported in your shell are
> visible to the Java process.

> The real-LLM e2e (`OpenJiuwenReactAgentA2aE2eTest`) only runs when
> `SAA_SAMPLE_LLM_API_KEY` is non-blank. Without it, JUnit `assumeTrue()` **skips**
> that branch after the agent-card assertions (the rest of the suite still runs).

## Which Environment Values Are Effective?

Maven and Spring Boot see the process environment at launch time. The effective
values are:

1. **Helper-script env file values** — `scripts/run-server.sh` and
   `scripts/test-e2e.sh` load the env file argument, defaulting to `.env` in this
   example directory. If the env file defines a variable, that value overrides a
   same-name variable that was already exported in the shell running the script.
2. **Explicit shell environment** — when you run Maven directly, or when a helper
   script loads an env file that does not define a variable, Maven sees variables
   already exported in the launching shell, for example `export SAA_SAMPLE_LLM_API_KEY=...`.
3. **Spring Boot defaults** — if no environment variable is visible to the Java
   process, the values in `src/main/resources/application.yaml` are used.

The checked-in defaults are placeholders for a local OpenAI-compatible gateway:

```yaml
sample:
  openjiuwen:
    model-provider: ${SAA_SAMPLE_OPENJIUWEN_MODEL_PROVIDER:openai}
    api-key: ${SAA_SAMPLE_LLM_API_KEY:sk-local-placeholder}
    api-base: ${SAA_SAMPLE_OPENJIUWEN_API_BASE:http://localhost:4000/v1}
    model-name: ${SAA_SAMPLE_LLM_MODEL:gpt-5.4-mini}
    ssl-verify: ${SAA_SAMPLE_OPENJIUWEN_SSL_VERIFY:false}
```

`sk-local-placeholder` is a **non-functional placeholder**, not a usable key:
local gateways such as Ollama ignore the `Authorization` header, so any string
works there. For a real cloud API or a local gateway that validates keys, set
`SAA_SAMPLE_LLM_API_KEY` and start the server through `scripts/run-server.sh .env`
or export the variable before running Maven.

Manual export alternative from the repository root:

```bash
set -a
. ./examples/agent-runtime-a2a-llm-e2e/.env
set +a
./mvnw -f examples/agent-runtime-a2a-llm-e2e/pom.xml spring-boot:run
```

## Local LLM Defaults and Curl

The example is configured for a local OpenAI-compatible gateway by default. You
can sanity-check the local gateway directly before starting the sample:

```bash
curl http://localhost:4000/v1/models \
  -H 'Authorization: Bearer sk-local-placeholder'
```

If your gateway validates keys, use the same key that you put in `.env`:

```bash
curl http://localhost:4000/v1/models \
  -H "Authorization: Bearer ${SAA_SAMPLE_LLM_API_KEY}"
```

If your gateway uses a different key, host, or model, override the environment variables described below.

## Override Environment Variables

The runtime configuration prefix used by this example is `agent-runtime.access.a2a`:

```yaml
agent-runtime:
  access:
    a2a:
      default-tenant-id: sample-tenant
      default-agent-id: openjiuwen-react-agent
      # public-base-url: https://agents.example.com/runtime-one
```

`public-base-url` is optional for local runs. When it is blank, the agent-card
endpoint derives the base URL from the current HTTP request. In production, set
it to the externally reachable runtime base URL so standard A2A clients receive
absolute endpoint URLs that do not depend on local host/port inference.

The example also recognizes these environment variables for the local LLM setup:

- `SAA_SAMPLE_LLM_API_KEY`
- `SAA_SAMPLE_OPENJIUWEN_API_BASE`
- `SAA_SAMPLE_OPENJIUWEN_MODEL_PROVIDER`
- `SAA_SAMPLE_LLM_MODEL`
- `SAA_SAMPLE_OPENJIUWEN_SSL_VERIFY`

The console client accepts either positional arguments or environment variables:

- arg 1 or `SAA_SAMPLE_A2A_BASE_URL`: A2A server base URL, default `http://localhost:8080`
- arg 2 or `SAA_SAMPLE_AGENT_ID`: agent id, default `openjiuwen-react-agent`
- arg 3 or `SAA_SAMPLE_USER_ID`: user id, default `manual-user`

Example override:

```bash
export SAA_SAMPLE_LLM_API_KEY="<your-key>"
export SAA_SAMPLE_OPENJIUWEN_API_BASE="http://localhost:4000/v1"
export SAA_SAMPLE_LLM_MODEL="gpt-5.4-mini"
export SAA_SAMPLE_A2A_BASE_URL="http://localhost:18080"
```

## Install Runtime Dependencies

This example is outside the root Maven reactor, so install the runtime dependency into your local Maven repository first:

```bash
./mvnw -pl agent-runtime -am -DskipTests install
```

That makes the current `agent-runtime` snapshot available to `examples/agent-runtime-a2a-llm-e2e`.

The server helper script performs this install step automatically before starting the server.

## Automated Test

Run the example test module directly through the helper script:

```bash
bash scripts/test-e2e.sh .env
```

The test starts the example application, calls it through the A2A client flow, and expects the visible response for `ping` to be `pong`.

If you have already exported the required variables and want to run Maven directly:

```bash
./mvnw -f examples/agent-runtime-a2a-llm-e2e/pom.xml test
```

## Manual Verification

1. Make sure your local OpenAI-compatible endpoint is reachable.
2. Start the example server with the env-loading helper script:

```bash
bash scripts/run-server.sh .env
```

The script loads `.env`, installs `agent-runtime`, and starts the Spring Boot server.
If the server is already running, stop it first; changing `.env` does not update an
already-running Java process.

3. In another terminal, start the console client:

```bash
./mvnw -f examples/agent-runtime-a2a-llm-e2e/pom.xml \
  exec:java \
  -Dexec.mainClass=com.huawei.ascend.examples.a2a.A2aConsoleClientApplication
```

4. At the prompt, enter:

```text
ping
```

5. Confirm the printed response is `pong`.

To target a different server, pass the base URL as the first argument:

```bash
./mvnw -f examples/agent-runtime-a2a-llm-e2e/pom.xml \
  exec:java \
  -Dexec.mainClass=com.huawei.ascend.examples.a2a.A2aConsoleClientApplication \
  -Dexec.args="http://localhost:18080"
```

You can also verify the A2A surface directly with curl after the server starts.

Check the agent card:

```bash
curl http://localhost:8080/.well-known/agent-card.json
```

Send a streaming JSON-RPC request to `/a2a`:

```bash
curl http://localhost:8080/a2a \
  -H 'Content-Type: application/json' \
  -H 'Accept: text/event-stream' \
  -d '{
    "jsonrpc": "2.0",
    "id": "manual-1",
    "method": "message/stream",
    "params": {
      "message": {
        "role": "user",
        "messageId": "manual-message-1",
        "contextId": "manual-session-1",
        "metadata": {
          "userId": "manual-user",
          "agentId": "openjiuwen-react-agent",
          "sessionId": "manual-session-1"
        },
        "parts": [
          {
            "kind": "text",
            "text": "ping"
          }
        ]
      }
    }
  }'
```

The SSE stream should include an accepted event and then a completed response whose user-visible text is `pong`.

## Expected Ping/Pong

Expected happy path:

- input: `ping`
- agent card is discovered from `/.well-known/agent-card.json`
- JSON-RPC streaming request is sent to `/a2a`
- final visible answer: `pong`

## Troubleshooting

- `Could not resolve com.huawei.ascend:agent-runtime:<version>`
  - Run `./mvnw -pl agent-runtime -am -DskipTests install` first.

- The server starts but the model call fails.
  - Verify `SAA_SAMPLE_LLM_API_KEY`, `SAA_SAMPLE_OPENJIUWEN_API_BASE`, and `SAA_SAMPLE_LLM_MODEL`.
  - Confirm the local gateway responds to `curl http://localhost:4000/v1/models -H 'Authorization: Bearer ...'`.
  - If the gateway succeeds with your real key but the sample fails with a placeholder-key symptom, stop the server and restart it with `bash scripts/run-server.sh .env`.
  - If `/v1/models` succeeds but the sample still fails, test the gateway's `/v1/chat/completions` endpoint with the same key and model.

- The console client cannot connect.
  - Confirm the server is running on `http://localhost:8080` or pass the correct base URL through `SAA_SAMPLE_A2A_BASE_URL` or the first CLI argument.

- The A2A call returns no final answer.
  - Check that the stream reaches a completed event.
  - Re-run the automated test to validate the expected `ping -> pong` path.

- TLS or certificate problems against a local gateway.
  - Check `SAA_SAMPLE_OPENJIUWEN_SSL_VERIFY`; the local default is `false` for this sample.
