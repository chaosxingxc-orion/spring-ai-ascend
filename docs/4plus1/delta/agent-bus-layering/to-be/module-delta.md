# to-be · module delta — agent-bus layering

Delta against `as-is/module`. Palette: **green `++` added**, **amber `~` modified**; forwarding + registry packages are **unchanged context** (neutral white, not recolored — L7 rule 5: delta semantics override, but unchanged elements stay neutral). No removed/red elements — nothing is deleted; 4 files **move** (added in new homes, gateway.runtime is *modified* slimmer, not minus anything that stays).

## The delta, package by package

- **`++ common`** (NEW package `com.huawei.ascend.bus.common`) — holds `AgentBusBrokerProperties` (moved from `gateway.runtime`). Q2a chosen: it is plane-level shared config consumed by BOTH planes, so it lives in a neutral package; neither plane depends on the other's implementation for config. This is the one deviation from the DoD's literal "4 files → eventbus.runtime" (now 3 → eventbus.runtime + 1 → common).
- **`++ eventbus.runtime`** (NEW package `com.huawei.ascend.bus.eventbus.runtime`) — holds the 3 relay-wiring files (`EventBusRelayConfiguration`, `EventBusRelaySchedulingConfig`, `RelayScheduler`), all moved from `gateway.runtime`. Flat — no sub-packages (3 files, Q1a). `@Profile("eventbus")` + bean names/qualifiers unchanged.
- **`~ gateway.runtime`** (MODIFIED) — now holds ONLY the 3 gateway-specific files (`GatewayRuntimeConfiguration`, `GatewayRuntimeController`, `GatewayRuntimeService`). `GatewayRuntimeConfiguration` gains one `import com.huawei.ascend.bus.common.AgentBusBrokerProperties;` (was same-package, no import). The `@EnableConfigurationProperties(AgentBusBrokerProperties.class)` + 4 method params stay — now resolve via the import.
- **forwarding.spi / forwarding.runtime.{relay,transport.broker,persistence.jdbc} / registry.runtime** — unchanged; the moved files keep depending on these exactly as before (same targets, new home).

## Dependency direction (the load-bearing outcome)

- `eventbus.runtime` → `common` (AgentBusBrokerProperties) + `forwarding.spi` + `forwarding.runtime.*`. ✓ event-bus depends on shared config + SPI/domain.
- `gateway.runtime` → `common` (AgentBusBrokerProperties) + `forwarding.spi` + `forwarding.runtime.transport.broker` + `forwarding.runtime.persistence.jdbc`. ✓ gateway depends on shared config + SPI/domain — **NOT** on `eventbus.runtime`.
- The dashed **`gateway.runtime ↛ eventbus.runtime`** edge is the boundary the **new ArchUnit rule (Q3a)** enforces — gateway may not depend on the event-bus wiring implementation package. Green immediately post-move (gateway imports `AgentBusBrokerProperties` from `common`, never from `eventbus.runtime`).

## Files touched (planned, for the G5 file-change table)

| Δ | Path | What |
|---|---|---|
| `++` move | `agent-bus/.../eventbus/runtime/EventBusRelayConfiguration.java` | moved from `gateway.runtime`; package decl + javadoc home-ref updated |
| `++` move | `agent-bus/.../eventbus/runtime/EventBusRelaySchedulingConfig.java` | moved from `gateway.runtime`; package decl updated |
| `++` move | `agent-bus/.../eventbus/runtime/RelayScheduler.java` | moved from `gateway.runtime`; package decl updated |
| `++` move | `agent-bus/.../common/AgentBusBrokerProperties.java` | moved from `gateway.runtime`; package decl + javadoc "Lives in gateway.runtime — Spring-permitted wiring home" prose updated |
| `~` | `agent-bus/.../gateway/runtime/GatewayRuntimeConfiguration.java` | + `import …common.AgentBusBrokerProperties;` (the one gateway-side ripple) |
| `++` move | `agent-bus/src/test/.../eventbus/runtime/EventBusRelaySchedulerTest.java` | moved to mirror `RelayScheduler`; package decl updated (stays co-located → no import for the moved classes) |
| `++` | `agent-bus/src/test/.../architecture/AgentBusForwardingSpiPurityTest.java` (or sibling) | + ONE rule: `gateway.runtime.. must not depend on eventbus.runtime..` (Q3a) |

Net: 4 source files moved (3 → `eventbus.runtime`, 1 → `common`), 1 test moved, 1 gateway file + 1 arch test edited. Zero behaviour change; zero profile/wiring/bean-name change; zero `@ComponentScan` change.

## What is NOT in the delta (stated, to prevent scope creep)

- No new relay logic, no scheduler change, no bean-graph change, no Spring-profile change, no deployment/compose change. This is a **package re-organization + one boundary rule** — nothing more.
- Doc edits to the closed plan `2026-07-15-relay-scheduler.md` + L2 line 457 are NOT done (Q5a: leave closed/historical records as-is).
- No amend to ADR-0161 (Q4a: it's unrelated; a NEW ADR records this layering at G7).
