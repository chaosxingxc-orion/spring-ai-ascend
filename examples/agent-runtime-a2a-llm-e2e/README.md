# Agent Runtime A2A LLM E2E Example

## Purpose

This example shows how to run an `agent-runtime` application that exposes an A2A endpoint, hosts AgentScope SDK agents behind that endpoint, and exercises them from an A2A client perspective only.

The example lives at `examples/agent-runtime-a2a-llm-e2e` and includes:

- a Spring Boot server application: `com.huawei.ascend.examples.a2a.A2aAgentRuntimeApplication`
- a console client: `com.huawei.ascend.examples.a2a.A2aConsoleClientApplication`
- automated end-to-end tests that validate the A2A flow against a local OpenAI-compatible LLM gateway

## What It Verifies

This example verifies the intended boundary for the sample:

1. `agent-runtime` hosts and exposes the agent through A2A.
2. The client discovers `/.well-known/agent-card.json`.
3. The client sends a streaming JSON-RPC request to `/a2a`.
4. The client reads streamed A2A events until the run completes.
5. A simple prompt of `ping` produces a final visible answer of `pong`.
6. A bank retail wealth advisor sample can produce an asset-allocation suggestion through the same A2A surface.

## Selecting the Active Agent

The runtime hosts exactly **one** agent per process. Set `sample.a2a.agent` to select the active runtime:

- `agentscope` (default): AgentScope Java SDK ReAct agent.
- `retail-wealth-advisor`: AgentScope retail wealth advisor sample with mock bank-side skills.

The current automated E2E tests cover both profiles:

- `agentscope-react-agent`: AgentScope Java SDK ReAct agent.
- `agentscope-retail-wealth-advisor`: bank retail wealth advisor built as an AgentScope ReAct agent with sample skills.

## AgentScope Retail Wealth Advisor Sample

The retail wealth advisor sample models a customer-owned AgentScope agent that a
large bank's business engineering team could build with bank-side skills.
It is intentionally kept inside the example module: `spring-ai-ascend` provides
runtime governance, A2A, task state, and output distribution; the wealth-advisor
logic belongs to the customer's AgentScope application.

The sample registers local mock skills to stand in for customer-side systems:

- customer profile and suitability lookup
- current holdings lookup
- market insight analysis
- bank product-universe matching
- allocation projection and stress-scenario calculation

The sample product universe is bank-oriented: short-tenor wealth-management
products, public funds, qualified-investor private funds, gold products, and ETF
feeder funds. The sample does not recommend individual stocks or exchange-traded
ETF products, and it always asks the model to include suitability and compliance
reminders. These skills are demonstration fixtures only, not financial advice.

## Quick start (config templates + scripts)

Copy a template, fill it, and run; the env file is the only thing that differs
between a local Ollama and a cloud OpenAI-compatible API; the command is identical:

```bash
cp .env.ollama.example .env        # or .env.openai-compatible.example, then edit
bash scripts/test-e2e.sh .env      # installs agent-runtime + runs the E2E suite
```

For manual server verification, prefer the server helper script because it loads
the env file before starting Spring Boot:

```bash
bash scripts/run-server.sh .env
# Windows: ./scripts/run-server.ps1 -EnvFile .env
```

Templates (the `.env` you fill is gitignored; the `*.example` templates are tracked):

- `.env.example`: every variable with inline docs.
- `.env.ollama.example`: local Ollama via its OpenAI-compatible `/v1` surface (`gemma4:latest`).
- `.env.openai-compatible.example`: a cloud OpenAI-compatible API (no real key committed).

> `.env` is not loaded automatically by Maven or Spring Boot. The helper scripts
> load it with shell sourcing before launching Maven. If you run `./mvnw ...
> spring-boot:run` directly, only variables already exported in your shell are
> visible to the Java process.

> The real-LLM E2E tests only run when `SAA_SAMPLE_LLM_API_KEY` is non-blank.
> Without it, JUnit `assumeTrue()` **skips** the real-LLM branch after the
> agent-card assertions (the rest of the suite still runs).

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
  agentscope:
    api-key: ${SAA_SAMPLE_LLM_API_KEY:sk-local-placeholder}
    api-base: ${SAA_SAMPLE_AGENTSCOPE_API_BASE:http://localhost:4000/v1}
    endpoint-path: ${SAA_SAMPLE_AGENTSCOPE_ENDPOINT_PATH:/chat/completions}
    model-name: ${SAA_SAMPLE_LLM_MODEL:gpt-5.4-mini}
    runtime:
      base-url: ${SAA_SAMPLE_AGENTSCOPE_RUNTIME_BASE_URL:self}
      endpoint-path: ${SAA_SAMPLE_AGENTSCOPE_RUNTIME_ENDPOINT_PATH:/sample/agentscope/process}
      embedded: ${SAA_SAMPLE_AGENTSCOPE_RUNTIME_EMBEDDED:true}
    retail-wealth:
      runtime:
        base-url: ${SAA_SAMPLE_RETAIL_WEALTH_RUNTIME_BASE_URL:self}
        endpoint-path: ${SAA_SAMPLE_RETAIL_WEALTH_RUNTIME_ENDPOINT_PATH:/sample/agentscope/retail-wealth/process}
        embedded: ${SAA_SAMPLE_RETAIL_WEALTH_RUNTIME_EMBEDDED:true}
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
      default-agent-id: agentscope-react-agent
      # public-base-url: https://agents.example.com/runtime-one
```

`public-base-url` is optional for local runs. When it is blank, the agent-card
endpoint derives the base URL from the current HTTP request. In production, set
it to the externally reachable runtime base URL so standard A2A clients receive
absolute endpoint URLs that do not depend on local host/port inference.

The example also recognizes these environment variables for the local LLM setup:

- `SAA_SAMPLE_LLM_API_KEY`
- `SAA_SAMPLE_LLM_MODEL`
- `SAA_SAMPLE_AGENTSCOPE_API_BASE`
- `SAA_SAMPLE_AGENTSCOPE_ENDPOINT_PATH`
- `SAA_SAMPLE_AGENTSCOPE_RUNTIME_BASE_URL`
- `SAA_SAMPLE_AGENTSCOPE_RUNTIME_ENDPOINT_PATH`
- `SAA_SAMPLE_AGENTSCOPE_RUNTIME_EMBEDDED`
- `SAA_SAMPLE_RETAIL_WEALTH_RUNTIME_BASE_URL`
- `SAA_SAMPLE_RETAIL_WEALTH_RUNTIME_ENDPOINT_PATH`
- `SAA_SAMPLE_RETAIL_WEALTH_RUNTIME_EMBEDDED`

The console client accepts either positional arguments or environment variables:

- arg 1 or `SAA_SAMPLE_A2A_BASE_URL`: A2A server base URL, default `http://localhost:8080`
- arg 2 or `SAA_SAMPLE_AGENT_ID`: agent id, default `agentscope-react-agent`
- arg 3 or `SAA_SAMPLE_USER_ID`: user id, default `manual-user`

Example override:

```bash
export SAA_SAMPLE_LLM_API_KEY="<your-key>"
export SAA_SAMPLE_AGENTSCOPE_API_BASE="http://localhost:4000/v1"
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

The tests start the example application and call it through the A2A client flow.
The basic AgentScope connectivity test expects the visible response for `ping`
to be `pong`. The retail wealth advisor test sends a bank relationship-manager
prompt and expects a visible asset-allocation suggestion with customer profile,
allocation, projection, risk, and compliance sections.

If you have already exported the required variables and want to run Maven directly
(the module pom defaults `skipTests=true`, so the override is required):

```bash
./mvnw -f examples/agent-runtime-a2a-llm-e2e/pom.xml test -DskipTests=false
```

## LangGraph remote-runtime sample (not shipped)

`src/main/java/com/huawei/ascend/examples/langgraph/` hosts a sample
`AgentRuntimeHandler` implementation that fronts a remote LangGraph runtime
(LangGraph Platform / langgraph-api) over SSE. It demonstrates how to adapt a
third framework behind the neutral runtime SPI, but it is NOT part of the
shipped agent-runtime adapter surface; promoting it requires an authorizing ADR
plus the L1/contract-catalog lockstep. Its unit tests run with the rest of this
module's suite.

## Manual Verification

### Prerequisite: install agent-runtime

The example module is outside the root Maven reactor. Install the runtime once:

```bash
./mvnw -pl agent-runtime -am -DskipTests install
```

(The helper scripts do this automatically.)

---

### Way A — Script-based startup (recommended)

The helper script loads your `.env` file and installs dependencies automatically.
**Use this if you want a single command that "just works".**

#### Start the server

```bash
# Default agent (Agentscope ReAct, ping → pong)
bash scripts/run-server.sh .env

# Retail wealth advisor (asset allocation suggestions)
bash scripts/run-server.sh .env retail-wealth-advisor
```

| Argument | Required | Default | Description |
|---|---|---|---|
| env-file | yes | `.env` | Path to environment file |
| agent-profile | no | `agentscope` | `agentscope` or `retail-wealth-advisor` |

#### Switch agent

Stop the current server (Ctrl+C), then restart with a different profile:

```bash
# From agentscope → retail-wealth-advisor
bash scripts/run-server.sh .env retail-wealth-advisor

# From retail-wealth-advisor → agentscope
bash scripts/run-server.sh .env agentscope
```

The script sources `.env` every time — API keys are always inherited.

---

### Way B — Raw command-line startup

Use this when you need full control over Maven arguments, JVM options, or the port.

**Important:** Maven does NOT read `.env` files. You must export environment
variables before running `mvn`, or the application falls back to
`application.yaml` defaults (which use placeholder keys).

#### 1. Export environment variables

```bash
set -a
source .env
set +a
```

Or export manually:

```bash
export SAA_SAMPLE_LLM_API_KEY="sk-your-key"
export SAA_SAMPLE_AGENTSCOPE_API_BASE="http://localhost:4000/v1"
export SAA_SAMPLE_LLM_MODEL="gpt-5.4-mini"
```

#### 2. Start the server

```bash
# Default agent (agentscope-react-agent)
./mvnw -f examples/agent-runtime-a2a-llm-e2e/pom.xml spring-boot:run

# Retail wealth advisor
./mvnw -f examples/agent-runtime-a2a-llm-e2e/pom.xml spring-boot:run \
  -Dspring-boot.run.arguments="--sample.a2a.agent=retail-wealth-advisor"

# Custom port
./mvnw -f examples/agent-runtime-a2a-llm-e2e/pom.xml spring-boot:run \
  -Dspring-boot.run.arguments="--server.port=18080 --sample.a2a.agent=retail-wealth-advisor"
```

#### Switch agent

Stop (Ctrl+C), then run the `mvn` command for the target profile.
Remember to export env vars again if you opened a new terminal.

---

### Verify with curl (works for both Way A and Way B)

Once the server is running on `http://localhost:8080`:

**Agent card:**

```bash
curl -s http://localhost:8080/.well-known/agent-card.json | python3 -m json.tool | grep name
# agentscope-react-agent        (default)
# agentscope-retail-wealth-advisor  (retail-wealth-advisor profile)
```

**Ping → pong (Agentscope ReAct agent):**

```bash
curl -s -N -X POST http://localhost:8080/a2a \
  -H "Content-Type: application/json" \
  -H "Accept: text/event-stream" \
  -d '{
    "jsonrpc": "2.0",
    "method": "SendStreamingMessage",
    "id": "1",
    "params": {
      "message": {
        "role": "ROLE_USER",
        "messageId": "msg-001",
        "contextId": "session-1",
        "metadata": {
          "userId": "test-user",
          "agentId": "agentscope-react-agent",
          "sessionId": "session-1"
        },
        "parts": [{"text": "ping"}]
      }
    }
  }' --no-buffer
```

Expected: last SSE event contains `"text":"pong"`.

**Asset allocation suggestion (retail wealth advisor):**

```bash
curl -s -N -X POST http://localhost:8080/a2a \
  -H "Content-Type: application/json" \
  -H "Accept: text/event-stream" \
  -d '{
    "jsonrpc": "2.0",
    "method": "SendStreamingMessage",
    "id": "1",
    "params": {
      "message": {
        "role": "ROLE_USER",
        "messageId": "msg-001",
        "contextId": "session-2",
        "metadata": {
          "userId": "test-user",
          "agentId": "agentscope-retail-wealth-advisor",
          "sessionId": "session-2"
        },
        "parts": [{"text": "请为客户 BANK-CUST-001 生成一份稳健型资产配置建议。"}]
      }
    }
  }' --no-buffer
```

Expected: completed response contains 客户画像摘要, 建议资产配置, 收益测算, 风险提示, 合规提示.

---

### Interactive console client

In another terminal (with the server still running):

```bash
./mvnw -f examples/agent-runtime-a2a-llm-e2e/pom.xml \
  exec:java \
  -Dexec.mainClass=com.huawei.ascend.examples.a2a.A2aConsoleClientApplication
```

Type `ping` → expect `pong`. Type `exit` to quit.

To target a different server or agent:

```bash
# Via CLI arguments:  <base-url> <agent-id> <user-id>
./mvnw -f examples/agent-runtime-a2a-llm-e2e/pom.xml \
  exec:java \
  -Dexec.mainClass=com.huawei.ascend.examples.a2a.A2aConsoleClientApplication \
  -Dexec.args="http://localhost:18080 agentscope-retail-wealth-advisor manual-user"

# Via environment variables
export SAA_SAMPLE_A2A_BASE_URL="http://localhost:18080"
export SAA_SAMPLE_AGENT_ID="agentscope-retail-wealth-advisor"
./mvnw -f examples/agent-runtime-a2a-llm-e2e/pom.xml \
  exec:java \
  -Dexec.mainClass=com.huawei.ascend.examples.a2a.A2aConsoleClientApplication
```

### Agent profiles summary

| Profile | Agent ID | Behavior |
|---|---|---|
| `agentscope` (default) | `agentscope-react-agent` | ReAct agent, replies `pong` to `ping` |
| `retail-wealth-advisor` | `agentscope-retail-wealth-advisor` | Bank retail wealth advisor with mock skills |

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
  - Verify `SAA_SAMPLE_LLM_API_KEY`, `SAA_SAMPLE_AGENTSCOPE_API_BASE`, and `SAA_SAMPLE_LLM_MODEL`.
  - Confirm the local gateway responds to `curl http://localhost:4000/v1/models -H 'Authorization: Bearer ...'`.
  - If the gateway succeeds with your real key but the sample fails with a placeholder-key symptom, stop the server and restart it with `bash scripts/run-server.sh .env`.
  - If `/v1/models` succeeds but the sample still fails, test the gateway's `/v1/chat/completions` endpoint with the same key and model.

- The console client cannot connect.
  - Confirm the server is running on `http://localhost:8080` or pass the correct base URL through `SAA_SAMPLE_A2A_BASE_URL` or the first CLI argument.

- The A2A call returns no final answer.
  - Check that the stream reaches a completed event.
  - Re-run the automated test to validate the expected `ping -> pong` path.

- Port conflict (8080 already in use).
  - Override the port: `./mvnw -f examples/agent-runtime-a2a-llm-e2e/pom.xml spring-boot:run -Dspring-boot.run.arguments="--server.port=18080"`
