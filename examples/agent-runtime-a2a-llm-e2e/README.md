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

The facade sample is intentionally local and in-memory. It is a customer-facing
example for pluggable gateway integration, not the production `agent-service`
implementation.

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

Production deployments must still add persistent or reconstructable registry
state, runtime identity authentication, tenant-agent authorization, rate
limiting, circuit breaking, multi-AZ deployment, same-city disaster recovery,
cross-region recovery, SLA/SLO dashboards, and error-budget governance.

## Quick start (config templates + scripts)

Copy a template, fill it, and run — the env file is the only thing that differs
between a local Ollama and a cloud OpenAI-compatible API; the command is identical:

```bash
cp .env.ollama.example .env        # or .env.openai-compatible.example, then edit
bash scripts/test-e2e.sh .env      # installs agent-runtime + runs the E2E suite
# Windows: ./scripts/test-e2e.ps1 -EnvFile .env
```

Templates (the `.env` you fill is gitignored; the `*.example` templates are tracked):

- `.env.example` — every variable with inline docs.
- `.env.ollama.example` — local Ollama via its OpenAI-compatible `/v1` surface (`gemma4:latest`).
- `.env.openai-compatible.example` — a cloud OpenAI-compatible API (no real key committed).

> The real-LLM e2e (`OpenJiuwenReactAgentA2aE2eTest`) only runs when
> `SAA_SAMPLE_LLM_API_KEY` is non-blank. Without it, JUnit `assumeTrue()` **skips**
> that branch after the agent-card assertions (the rest of the suite still runs).

## Local LLM Defaults and Curl

The example is configured for a local OpenAI-compatible gateway by default. The checked-in defaults are env-aware placeholders in `examples/agent-runtime-a2a-llm-e2e/src/main/resources/application.yaml`:

```yaml
sample:
  openjiuwen:
    model-provider: ${SAA_SAMPLE_OPENJIUWEN_MODEL_PROVIDER:openai}
    api-key: ${SAA_SAMPLE_LLM_API_KEY:sk-local-placeholder}
    api-base: ${SAA_SAMPLE_OPENJIUWEN_API_BASE:http://localhost:4000/v1}
    model-name: ${SAA_SAMPLE_LLM_MODEL:gpt-5.4-mini}
    ssl-verify: ${SAA_SAMPLE_OPENJIUWEN_SSL_VERIFY:false}
```

`sk-local-placeholder` is a **non-functional placeholder**, not a usable key: local gateways (Ollama) ignore the `Authorization` header, so any string works. For a real cloud OpenAI-compatible API, set `SAA_SAMPLE_LLM_API_KEY` to your own key.

You can sanity-check the local gateway directly before starting the sample:

```bash
curl http://localhost:4000/v1/models \
  -H 'Authorization: Bearer sk-local-placeholder'
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
```

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

## Automated Test

Run the example test module directly:

```bash
./mvnw -f examples/agent-runtime-a2a-llm-e2e/pom.xml test
```

The test starts the example application, calls it through the A2A client flow, and expects the visible response for `ping` to be `pong`.

## Manual Verification

1. Make sure your local OpenAI-compatible endpoint is reachable.
2. Install the runtime dependency:

```bash
./mvnw -pl agent-runtime -am -DskipTests install
```

3. Start the example server:

```bash
./mvnw -f examples/agent-runtime-a2a-llm-e2e/pom.xml spring-boot:run
```

4. In another terminal, start the console client:

```bash
./mvnw -f examples/agent-runtime-a2a-llm-e2e/pom.xml \
  exec:java \
  -Dexec.mainClass=com.huawei.ascend.examples.a2a.A2aConsoleClientApplication
```

5. At the prompt, enter:

```text
ping
```

6. Confirm the printed response is `pong`.

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

- The console client cannot connect.
  - Confirm the server is running on `http://localhost:8080` or pass the correct base URL through `SAA_SAMPLE_A2A_BASE_URL` or the first CLI argument.

- The A2A call returns no final answer.
  - Check that the stream reaches a completed event.
  - Re-run the automated test to validate the expected `ping -> pong` path.

- TLS or certificate problems against a local gateway.
  - Check `SAA_SAMPLE_OPENJIUWEN_SSL_VERIFY`; the local default is `false` for this sample.
