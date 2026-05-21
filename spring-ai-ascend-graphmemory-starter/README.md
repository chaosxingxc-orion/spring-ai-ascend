# spring-ai-ascend-graphmemory-starter

> Spring Boot auto-configuration scaffold for the `GraphMemoryRepository` SPI. No bean shipped at W0.

## What is this?

A Spring Boot starter that wires a `GraphMemoryRepository` bean into the agent runtime when one is provided. At W0 it registers **no beans by default** — it is a placeholder so consumers can plug in their own implementation (or wait for the W1 Graphiti REST reference adapter, ADR-0034) without restructuring their application.

## Status

**W0 scaffold.** No `GraphMemoryRepository` bean is registered by default. The Graphiti REST reference adapter is the W1 integration target (ADR-0034); no adapter class ships at W0.

## Quick start

Provide your own `GraphMemoryRepository` implementation in your application configuration:

```java
@Configuration
class GraphMemoryConfig {

    @Bean
    GraphMemoryRepository graphMemoryRepository() {
        return new MyGraphMemoryRepository();
    }
}
```

Enable the starter:

```yaml
springai:
  ascend:
    graphmemory:
      enabled: true
      base-url: ${SPRINGAI_ASCEND_GRAPHITI_BASE_URL:http://localhost:8001}
```

The SPI contract lives at [`agent-service/src/main/java/com/huawei/ascend/service/runtime/memory/spi/GraphMemoryRepository.java`](../agent-service/src/main/java/com/huawei/ascend/service/runtime/memory/spi/GraphMemoryRepository.java).

## Configuration

| Property | Default | Purpose |
|----------|---------|---------|
| `springai.ascend.graphmemory.enabled` | `false` | Master toggle. Auto-config contributes nothing when `false` or absent. At W0, even when `true`, no bean is contributed — the W1 adapter does the wiring. |
| `springai.ascend.graphmemory.base-url` | `${SPRINGAI_ASCEND_GRAPHITI_BASE_URL:http://localhost:8001}` | **RESERVED for W1.** Not consumed at W0 (the Graphiti REST adapter lands at W1 per ADR-0034). Marked `@Deprecated(forRemoval=false)` to flag the orphan-config Rule 3 exemption explicitly; see v2.0.0-rc3 cross-constraint audit α-8 / P1-7. |
| `springai.ascend.graphmemory.api-key` | `""` | **RESERVED for W1.** Same status as `base-url` above. |

Auto-discovery uses the Spring Boot 2.7+ contract: `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`.

## See also

- [ARCHITECTURE.md](../ARCHITECTURE.md) — system boundary and SPI contracts.
- [docs/adr/0034-memory-and-knowledge-taxonomy-at-l0.md](../docs/adr/0034-memory-and-knowledge-taxonomy-at-l0.md) — memory taxonomy + Graphiti selection rationale.
- [agent-service/src/main/java/com/huawei/ascend/service/runtime/memory/spi/GraphMemoryRepository.java](../agent-service/src/main/java/com/huawei/ascend/service/runtime/memory/spi/GraphMemoryRepository.java) — the SPI interface.
