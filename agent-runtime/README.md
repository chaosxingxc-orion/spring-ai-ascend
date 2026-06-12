# agent-runtime

`agent-runtime` is the run-owning runtime SDK for hosting agents behind a standard A2A interface. It packages the execution engine, run lifecycle, dispatch, session and task-control flow, the internal event queue, and the bootable Spring Boot runtime application into one module.

## What runtime is

At a high level, `agent-runtime` provides:

- a bootable Spring Boot runtime application
- the runtime-side engine adapter and execution flow
- the northbound A2A access layer
- shared runtime wiring for session, task, and output handling

This module is intended to host an agent implementation and expose it over A2A so a caller can interact with the agent through standard discovery and JSON-RPC calls instead of runtime-internal Java APIs.

## agent-service boundary

`agent-runtime` owns the running agent process and its A2A access surface.

Use `agent-runtime` when you need to:

- boot an agent-hosting process
- expose the agent through A2A
- wire runtime execution, task flow, and output streaming together

`agent-service` is downstream of `agent-runtime`, not the other way around. `agent-service` is a separate serviceization facade that can sit on top of the runtime rather than the runtime owning serviceization concerns. Keep runtime-hosting concerns, A2A ingress/egress, and boot wiring in `agent-runtime`.

## Install

Build and install the module to your local Maven repository when another module outside the root reactor needs to consume it:

```bash
./mvnw -pl agent-runtime -am -DskipTests install
```

That is the required first step for the local A2A example at:

- `examples/agent-runtime-a2a-llm-e2e`

## Boot entry point

`agent-runtime` ships as a **library**; embed the framework-neutral entry:

```java
try (RunningRuntime runtime = RuntimeApp.create(handler).run(LocalA2aRuntimeHost.port(8080))) {
    // serving A2A on runtime.port()
}
```

`RuntimeApp` / `RuntimeHost` (`com.huawei.ascend.runtime.app`) carry no Spring Boot dependency; Spring Boot is confined to `LocalA2aRuntimeHost`. There is no executable boot-classifier jar.

A minimal local launch path is to start an application that depends on `agent-runtime`, such as the example app:

```bash
export SAA_SAMPLE_LLM_API_KEY=sk-x00550472
./mvnw -f examples/agent-runtime-a2a-llm-e2e/pom.xml spring-boot:run
```

Useful local overrides:

- `SAA_SAMPLE_LLM_API_KEY` overrides the example LLM key. Local default: `sk-x00550472`
- `SAA_SAMPLE_OPENJIUWEN_API_BASE` overrides the OpenAI-compatible base URL. Default: `http://localhost:4000/v1`
- `SAA_SAMPLE_LLM_MODEL` overrides the model name. Default: `gpt-5.4-mini`
- `SAA_SAMPLE_OPENJIUWEN_MODEL_PROVIDER` overrides the provider. Default: `openai`
- `SAA_SAMPLE_OPENJIUWEN_SSL_VERIFY` overrides SSL verification. Default: `false`

Runtime A2A access properties bind under:

- `agent-runtime.access.a2a`

Current built-in properties are:

- `agent-runtime.access.a2a.default-tenant-id`
- `agent-runtime.access.a2a.default-agent-id`
- `agent-runtime.access.a2a.public-base-url`

`public-base-url` is the externally reachable runtime base URL used when
publishing absolute A2A endpoint URLs in `/.well-known/agent-card.json`. If it is
blank, the discovery controller derives a local base URL from the current HTTP
request.

The local example sets:

```yaml
agent-runtime:
  access:
    a2a:
      default-tenant-id: sample-tenant
      default-agent-id: openjiuwen-react-agent
      # public-base-url: https://agents.example.com/runtime-one
```

## Exposed A2A endpoints

The runtime exposes a standard agent card plus a JSON-RPC A2A endpoint.

### Agent discovery

- `GET /.well-known/agent-card.json`

Clients use the agent card to discover the supported transport and endpoint path.

### JSON-RPC endpoint

- `POST /a2a`
- `POST /a2a/`

The controller produces either normal JSON responses or SSE for streaming requests.

### Supported JSON-RPC methods

Current handler support includes:

- `message/send`
- `tasks/get`
- `tasks/cancel`
- `message/stream`

Canonical method names from the A2A SDK are also recognized where applicable:

- `SendMessage`
- `GetTask`
- `CancelTask`
- `SendStreamingMessage`

Notes on behavior:

- `message/send` returns a normal JSON-RPC response.
- `tasks/get` returns task state derived from runtime output state.
- `tasks/cancel` sends a cancel command into the runtime.
- `message/stream` uses HTTP/SSE and streams JSON-RPC events back to the caller.

## Java extension points

The main Java-level integration pattern is:

1. build or wire an agent in your application
2. depend on `agent-runtime`
3. boot a Spring application that includes runtime configuration
4. expose the agent through the runtime A2A surface

Useful starting points in this module include:

- `com.huawei.ascend.runtime.app.RuntimeApp` / `com.huawei.ascend.runtime.app.LocalA2aRuntimeHost`
- `com.huawei.ascend.runtime.access.a2a.A2aAccessProperties`
- `com.huawei.ascend.runtime.access.AccessLayerConfiguration`

Important Java extension points and related types include:

- `com.huawei.ascend.runtime.engine.spi.AgentRuntimeHandler` - framework-neutral runtime SPI for running an agent inside `agent-runtime` (with `StreamAdapter`; keep framework-specific decoration inside each adapter)
- `com.huawei.ascend.runtime.engine.spi.AgentCardProvider` - optional A2A Agent Card metadata provider; keep this separate when the execution handler should stay focused on framework execution and state bridge logic
- `com.huawei.ascend.runtime.engine.spi.MemoryProvider` - reserved narrow SPI for frameworks that need runtime-provided memory init/search/save integration; memory hit scores are optional and hit order is the relevance contract
- `com.huawei.ascend.runtime.engine.openjiuwen.OpenJiuwenAgentRuntimeHandler` - built-in `AgentRuntimeHandler` implementation used by the local OpenJiuwen example path
- `com.huawei.ascend.runtime.engine.agentscope.AgentScopeAgentRuntimeHandler` - built-in `AgentRuntimeHandler` implementation for an in-process AgentScope SDK agent
- `com.huawei.ascend.runtime.engine.agentscope.AgentScopeHarnessRuntimeHandler` - built-in `AgentRuntimeHandler` implementation for an AgentScope Harness agent
- `com.huawei.ascend.runtime.engine.agentscope.AgentScopeRuntimeClientHandler` - built-in `AgentRuntimeHandler` implementation for a remote AgentScope REST/SSE runtime
- `com.huawei.ascend.runtime.engine.agentscope.AgentScopeRuntimeClientProperties` - endpoint configuration for the AgentScope REST/SSE runtime client
- `org.a2aproject.sdk.spec.AgentCard` - A2A agent-card model exposed by the runtime discovery endpoint

The example application shows the intended consumer shape from outside the module:

- app boot class: `com.huawei.ascend.examples.a2a.OpenJiuwenA2aAgentRuntimeApplication`
- console client: `com.huawei.ascend.examples.a2a.A2aConsoleClientApplication`

For OpenJiuwen memory integration, prefer OpenJiuwen's native memory hooks when the concrete agent supports them. The runtime keeps only the narrow `MemoryProvider` SPI; the adapter that maps it to OpenJiuwen external memory semantics lives under `runtime.engine.openjiuwen`, so future OpenJiuwen memory module splits do not leak into the public runtime SPI.

## Developer guides

Per-feature reference documentation lives under [`docs/guides/`](docs/guides/).
Each guide is self-contained and AI-readable — read the one relevant to your task.

| Guide | When to read |
|---|---|
| [handler-spi.md](docs/guides/handler-spi.md) | Implementing a custom AgentRuntimeHandler |
| [openjiuwen-adapter.md](docs/guides/openjiuwen-adapter.md) | Hosting an openJiuwen agent (ReActAgent / DeepAgent) |
| [agent-card-configuration.md](docs/guides/agent-card-configuration.md) | Configuring the A2A agent discovery card |
| [a2a-endpoints.md](docs/guides/a2a-endpoints.md) | Understanding the A2A JSON-RPC protocol surface |
| [configuration-properties.md](docs/guides/configuration-properties.md) | Complete application.yaml property reference |

## Quick start — minimal integration

The simplest integration example is:

- `examples/agent-runtime-openjiuwen-simple` — minimal openJiuwen ReActAgent, three-step integration, no extra dependencies

That example is the recommended starting point for developers learning to host an agent in agent-runtime. It contains detailed inline comments and a step-by-step README.

## Local example: test, start, client

The full-featured end-to-end example lives at:

- `examples/agent-runtime-a2a-llm-e2e`

### Run the example test

Run the runtime module tests first:

```bash
./mvnw -pl agent-runtime test
```

Then run the example-module test:

```bash
export SAA_SAMPLE_LLM_API_KEY=sk-x00550472
./mvnw -f examples/agent-runtime-a2a-llm-e2e/pom.xml test
```

If needed, also override the local gateway settings:

```bash
export SAA_SAMPLE_OPENJIUWEN_API_BASE=http://localhost:4000/v1
export SAA_SAMPLE_LLM_MODEL=gpt-5.4-mini
export SAA_SAMPLE_OPENJIUWEN_MODEL_PROVIDER=openai
export SAA_SAMPLE_OPENJIUWEN_SSL_VERIFY=false
```

### Start the example service

```bash
export SAA_SAMPLE_LLM_API_KEY=sk-x00550472
./mvnw -f examples/agent-runtime-a2a-llm-e2e/pom.xml spring-boot:run
```

After startup, the service exposes:

- `/.well-known/agent-card.json`
- `/a2a`

### Start the console client

```bash
./mvnw -f examples/agent-runtime-a2a-llm-e2e/pom.xml \
  exec:java \
  -Dexec.mainClass=com.huawei.ascend.examples.a2a.A2aConsoleClientApplication
```

By default the client connects to `http://localhost:8080` and uses:

- agent id: `openjiuwen-react-agent`
- user id: `manual-user`

For the local manual smoke flow, type `ping` at the console prompt and expect a visible answer of `pong` in the returned agent response.

Useful client overrides:

- first CLI arg or `SAA_SAMPLE_A2A_BASE_URL` for the base URL
- second CLI arg or `SAA_SAMPLE_AGENT_ID` for the agent id
- third CLI arg or `SAA_SAMPLE_USER_ID` for the user id

Example against a different port:

```bash
./mvnw -f examples/agent-runtime-a2a-llm-e2e/pom.xml \
  exec:java \
  -Dexec.mainClass=com.huawei.ascend.examples.a2a.A2aConsoleClientApplication \
  -Dexec.args="http://localhost:18080"
```

## Notes

- The example is outside the root Maven reactor, so install `agent-runtime` locally first.
- The example is intentionally client-perspective driven: discover the agent card, call `/a2a`, and read the streamed result.
- The console client is the quickest manual smoke path for local A2A behavior.
- The local default key `sk-x00550472` is intentionally allowed for local development in this repository state.
- Do not treat the example defaults as production credentials or production deployment guidance.
