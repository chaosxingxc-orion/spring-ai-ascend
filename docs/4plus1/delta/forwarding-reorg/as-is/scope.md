# Scope — forwarding-reorg (as-is)

**Change slug:** `forwarding-reorg`
**G2 mode:** code-driven (no `docs/4plus1/` baseline; ADR-0147/0150/0160; same as the prior `agent-bus-layering` slice — `harness-toolkit baseline find-or-build` would re-find the stale L1). Drawn from real code under `agent-bus/src/main/java/com/huawei/ascend/bus/` + `agent-bus/pom.xml`.

## What this change is

Reorganize `agent-bus` into a **forwarding-centric** package structure that can build **separate packages**: an event-bus process package, a producer/consumer SDK, and a gateway process package. Concretely (user's target):

- `forwarding` becomes the level-1 anchor; the top-level `eventbus/` wiring folds INTO `forwarding`; the top-level `common/` moves to `forwarding/common`.
- `forwarding/runtime/transport/broker/` keeps its structure BUT: the externally-facing SPI interfaces move UP to `forwarding/spi/`; common pub/sub code stays in `broker/`; rocketmq-specific code moves to `broker/rocketmq/` (sibling dirs for future brokers).
- `gateway/` level-1 structure unchanged.

This **supersedes/revises ADR-0162** (the just-closed `agent-bus-layering` slice): ADR-0162's top-level `eventbus.runtime` + `common` packages get folded into `forwarding`; the `gateway↛eventbus` ArchUnit rule evolves (the event-bus impl is now within `forwarding/runtime`).

## In scope (modules this change touches)

- **`forwarding/`** (restructure anchor): `spi/` (gains the broker SPI ports per Q3), `runtime/` (structure unchanged per target — `relay/`, `persistence/jdbc/`, `transport/{a2a, broker/{common, rocketmq/}}`), and a NEW `forwarding/common/` (`AgentBusBrokerProperties` moves here from top-level `common/`).
- **`common/` (top-level)** → `forwarding/common/` (eliminated as top-level).
- **`eventbus/runtime/` (top-level)** → folds into `forwarding/` (destination = open Q2; candidate `forwarding/runtime/relay/` alongside `EventBusRelayWorker`, or a sub-package, or a separate process module).
- **`gateway/runtime/`** — structure unchanged per target, but `GatewayRuntimeConfiguration`'s import of `AgentBusBrokerProperties` follows it to `forwarding/common`.
- **Build (`agent-bus/pom.xml`)** — currently SINGLE Maven module + `spring-boot-maven-plugin repackage` (1 fat-jar) + `SPRING_PROFILES_ACTIVE` for the 2 process forms. The "build different packages" requirement = open Q1 (Maven multi-module split vs keep single-module+@Profile).
- **ArchUnit rules** — `AgentBusForwardingSpiPurityTest` (forwarding purity) + `AgentBusDependencyBoundaryTest` (incl. `gateway↛eventbus` from ADR-0162). The reorg must keep these green OR evolve them.

## Out of scope (explicitly)

- `registry.runtime/` — registry plane wiring; untouched.
- Top-level `com.huawei.ascend.bus.spi/` (engine / federation / ingress / registry / s2c) — the cross-plane bus SPI surface (IngressGateway, S2cCallbackTransport, engine, registry SPI); NOT the forwarding SPI. Stays as-is.
- `AgentBusApplication` — stays the single `@SpringBootApplication` (component-scans `com.huawei.ascend.bus..`); unless Q1's multi-module decision splits it.
- External repos (`agent-runtime-java` etc.) — cross-repo boundary only.
- L0/L1/L2 architecture docs (reference authority).

## Why ONLY the module diagram (not flow / sequence) — stated out loud

Like the prior `agent-bus-layering` slice, this change moves **package boundaries + module ownership + the build topology + the SPI surface** — a module/development-view (+ physical/build-view) delta. It does NOT move control flow, request path, state transitions, or cross-component ordering: the relay still consume→govern→re-publish; the scheduler still fires ticks; the Spring bean graph keeps the same names/qualifiers; `@Profile` semantics unchanged. A flow/sequence diagram as-is vs to-be would be byte-identical except package labels. So per G2's "draw only what the change exercises", flow + sequence are **skipped**. (The build/package aspects are annotated on the module diagram, not a separate physical view — the project is code-driven with no 4+1 physical-view baseline.)

## Current build state (load-bearing for Q1)

`agent-bus/pom.xml`: artifactId `agent-bus`, parent `spring-ai-ascend-parent` 0.2.0-SNAPSHOT. **SINGLE Maven module.** `<build>` = `spring-boot-maven-plugin` `repackage` → ONE runnable fat-jar. The two process forms (gateway / eventbus+registry) are selected at launch by `SPRING_PROFILES_ACTIVE` (@Profile-gated configs, G5-B of the relay slice). Dependencies: spring-boot-starter-{jdbc,web}, micrometer-core, flyway+postgresql, a2a-sdk (client-transport-jsonrpc + http-client), rocketmq-client 5.1.4 (with exclusions), + test deps (spring-boot-starter-test, agent-runtime test, archunit, mockwebserver, embedded-postgres). So the "build different packages" requirement starts from a single-module+@Profile base.
