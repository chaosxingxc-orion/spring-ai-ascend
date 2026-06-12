# AgentCard Configuration

The A2A agent card (served at `/.well-known/agent-card.json`) describes your
agent to clients: its name, capabilities, endpoint, and provider metadata.

## Configuration priority (highest to lowest)

| Priority | Method | Use case |
|---|---|---|
| 1 | Custom `@Bean AgentCard` | Full programmatic control |
| 2 | `AgentCardProvider` bean | Dynamic card generation |
| 3 | YAML `agent-runtime.access.a2a.agent-card.*` | Declarative static config |
| 4 | Auto-generation from `handler.agentId()` | Zero-config default |

## YAML configuration

```yaml
agent-runtime:
  access:
    a2a:
      agent-card:
        name: my-agent-id                   # defaults to handler.agentId()
        description: My custom agent        # defaults to "agent-runtime"
        version: "1.0.0"                    # defaults to "0.1.0"
        organization: my-org                # defaults to "spring-ai-ascend"
        organization-url: https://my.co     # defaults to "http://localhost:8080"
        endpoint: /a2a                      # defaults to "/a2a"
```

All fields are optional. Unset fields draw sensible defaults. If `name` is
unset, the runtime derives the card name from the first `AgentRuntimeHandler`'s
`agentId()`.

## Programmatic override

### Option A: custom AgentCard bean

```java
@Bean
public AgentCard myAgentCard() {
    return AgentCard.builder()
        .name("my-agent")
        .description("Custom agent")
        .version("1.0")
        .url("/a2a")
        .provider(new AgentProvider("my-org", "https://my.co"))
        .capabilities(AgentCapabilities.builder()
            .streaming(true).pushNotifications(true).build())
        .defaultInputModes(List.of("text"))
        .defaultOutputModes(List.of("text"))
        .supportedInterfaces(List.of(
            new AgentInterface(TransportProtocol.JSONRPC.asString(), "/a2a")))
        .build();
}
```

This completely replaces the auto-generated card. Use `@ConditionalOnMissingBean`
semantics — if ANY `AgentCard` bean exists, the auto-configuration backs off.

### Option B: AgentCardProvider

```java
@Bean
public AgentCardProvider myCardProvider() {
    return () -> buildDynamicCard();
}
```

Use when the card needs runtime computation (e.g. reading from a config service).

## Auto-generation (zero-config)

When no `AgentCard` bean, no `AgentCardProvider`, and no YAML `name` is set, the
runtime generates:

```
name:        <first handler's agentId()>   e.g. "openjiuwen-simple-agent"
description: "agent-runtime"
version:     "0.1.0"
provider:    AgentProvider("spring-ai-ascend", "http://localhost:8080")
endpoint:    "/a2a"
capabilities: streaming=true, pushNotifications=true
```

## public-base-url

When the runtime is behind a reverse proxy or load balancer, the auto-detected
local URL in the agent card may not be reachable by external A2A clients. Set:

```yaml
agent-runtime:
  access:
    a2a:
      public-base-url: https://agents.example.com/runtime
```

The `AgentCardController` uses this as the base for resolving interface URLs.
When blank, it derives the base from the incoming HTTP request.

## Endpoint discovery

Clients discover the agent card at:

```
GET /.well-known/agent-card.json
GET /.well-known/agent.json          # legacy alias
```

The card's `url` and `supportedInterfaces[].url` are resolved relative to the
request's base URL (or `public-base-url` if set). The `AgentCardController`
honors `X-Forwarded-*` headers when a `ForwardedHeaderFilter` is registered.
