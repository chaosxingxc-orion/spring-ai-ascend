# agent-runtime-spring-boot-starter-a2a

Microservice-mode starter for the neutral agent runtime. Add the dependency and publish one
`AgentDriver` bean; the runtime auto-configures the neutral execution core
(`AgentDriverRegistry` → `RunCoordinator`) behind an A2A access endpoint, so a heterogeneous
agent runs as a standalone A2A microservice.

## Usage

```xml
<dependency>
  <groupId>com.huawei.ascend</groupId>
  <artifactId>agent-runtime-spring-boot-starter-a2a</artifactId>
</dependency>
```

```java
@SpringBootApplication
public class MyAgentService {
    public static void main(String[] args) {
        SpringApplication.run(MyAgentService.class, args);
    }

    @Bean
    AgentDriver myAgentDriver() {
        // any framework adapter implementing the neutral AgentDriver SPI
        return new OpenJiuwenAgentDriver("my-agent", systemPrompt, provider, apiKey, apiBase, model, false);
    }
}
```

The service then exposes `/a2a` (JSON-RPC) and `/.well-known/agent-card.json`. The AgentCard is
derived from the driver's `name()` / `description()`.

## Two deployment modes, one core

- **SDK-host**: embed `agent-runtime` directly in a host application.
- **Microservice** (this starter): a standalone Spring Boot service per agent, frontable by the
  registry/gateway control plane.

Both drive the same `RunCoordinator(AgentDriver)` core — only the packaging differs.

## Verified by

The standalone A2A end-to-end test (`OpenJiuwenReactAgentA2aE2eTest` in
`examples/agent-runtime-a2a-llm-e2e`, which depends on this starter) boots a real service through
this starter and exercises a real openJiuwen ReAct agent against a real LLM over the A2A wire.
