---
level: L1
view: development
status: active
authority: "arch-driven G6/G7 (agent-bus-layering); G4 sign-off 2026-07-16"
---

# ADR-0162: agent-bus package layering — gateway vs event-bus plane boundary

- **Status:** Accepted
- **Date:** 2026-07-16
- **Change slug:** `agent-bus-layering`
- **Supersedes:** the implicit `gateway.runtime`-as-Spring-wiring-home convention (prose in `AgentBusBrokerProperties` javadoc line 20 + `docs/superpowers/plans/2026-07-15-relay-scheduler.md:17`) — **NOT ADR-0161.**
- **Related:** `docs/4plus1/delta/agent-bus-layering/`; ADR-0160 (web confined to `gateway.runtime`); ADR-0161 (event-bus-relay substrate deviations — **unrelated** to the wiring-home convention).

## Context

The relay-scheduler TDD slice (commits `6c4f2054`/`ac44b302`/`ae9e7ef3`/`a9cf103c`) landed 4 **event-bus wiring** files in `com.huawei.ascend.bus.gateway.runtime`: `EventBusRelayConfiguration`, `EventBusRelaySchedulingConfig`, `RelayScheduler`, and the shared `AgentBusBrokerProperties`. They are semantically event-bus code (`@Profile("eventbus")` / relay scheduling), not gateway code — they co-located in `gateway.runtime` only because of an implicit convention that "`gateway.runtime` is the Spring-permitted wiring home" (javadoc prose on `AgentBusBrokerProperties` line 20 + plan doc line 17 — NOT any enforced rule, NOT ADR-0161). That co-location meant `gateway.runtime` held a MIX of gateway-specific + event-bus wiring + shared config: the gateway and event-bus planes were not separated, and nothing prevented gateway wiring from depending on event-bus wiring (or vice versa).

This ADR records the arch-driven `agent-bus-layering` refactor (G1 code-driven → G4 signed off 2026-07-16 → G5 implemented → G6 as-built all-Matched + 2 minor Drifted accepted → G7 CLOSED) that establishes the plane boundary explicitly. G4 sign-off ratified 5 branches (decision-tree Q1a–Q5a); the two corrected-handoff facts (the "must grant Spring permission to `eventbus.runtime`" hard-constraint was code-verified FALSE; "reverse ADR-0161" was mis-attributed) were acknowledged at sign-off.

## Decision

1. **`eventbus.runtime` plane** — the 3 event-bus relay-wiring files (`EventBusRelayConfiguration`, `EventBusRelaySchedulingConfig`, `RelayScheduler`) live in a NEW package `com.huawei.ascend.bus.eventbus.runtime` (flat — no sub-packages; 3 files is too few for premature abstraction). It is a sibling plane to `gateway.runtime` / `registry.runtime` under `com.huawei.ascend.bus`. `@Profile("eventbus")` + bean names/qualifiers unchanged.
2. **`common` plane-config** — the shared `AgentBusBrokerProperties` (`@ConfigurationProperties("agent-bus")`, consumed by BOTH planes) lives in a NEW neutral package `com.huawei.ascend.bus.common`. It is neither gateway- nor event-bus-specific; putting it in `eventbus.runtime` would make the gateway depend on the event-bus **implementation** package — the very inversion this refactor fixes. (This deviates from a literal "4 files → `eventbus.runtime`" DoD wording → 3 + 1; ratified at G4 as Q2a.)
3. **`gateway.runtime` slims to gateway-only** — now holds only the 3 gateway-specific files (`GatewayRuntimeConfiguration` + `GatewayRuntimeController` + `GatewayRuntimeService`). `GatewayRuntimeConfiguration` gains one `import com.huawei.ascend.bus.common.AgentBusBrokerProperties;` (it referenced it same-package before).
4. **Enforced boundary** — ONE ArchUnit rule in `AgentBusDependencyBoundaryTest` (reusing `BUS_PRODUCTION` = `importPackages("com.huawei.ascend.bus")`, production-only): `gateway.runtime..` must not depend on `eventbus.runtime..`. Green immediately post-move (gateway imports `AgentBusBrokerProperties` from `common`, never from `eventbus.runtime`). Plus a liveness guard asserting both plane packages are non-empty (prevents the rule passing vacuously if a plane is ever emptied). The rule lives in the boundary test (whose `BUS_PRODUCTION` covers the whole bus tree), NOT in `AgentBusForwardingSpiPurityTest` (whose `FORWARDING` set imports only `forwarding..` and excludes gateway/eventbus — a rule there would pass vacuously, the exact failure its own `forwarding_import_is_non_empty` guard warns about).
5. **The implicit convention is superseded** — the "`gateway.runtime` is the Spring-permitted wiring home" prose (javadoc + plan) is reversed: gateway wiring stays in `gateway.runtime`; event-bus wiring lives in `eventbus.runtime`; shared config in `common`. ADR-0161 is NOT amended (it records 3 G5-E substrate deviations — governance-mode / `markRejected` upsert / V3 outbox columns — unrelated to the wiring-home convention).

## Consequences

- **New invariant (plane separation):** `gateway.runtime` holds only gateway-specific wiring; `eventbus.runtime` holds only event-bus wiring; `common` holds plane-shared config. Future wiring files land in their plane package, not in `gateway.runtime` by default.
- **New invariant (dependency direction):** gateway depends on `common` + `forwarding.spi` + the broker/jdbc adapters; event-bus depends on `common` + `forwarding.spi` + `forwarding.runtime.*`. Gateway must NOT depend on `eventbus.runtime` (ArchUnit-enforced). Both planes depend on shared `common` config + the forwarding SPI/domain, never on each other's implementation.
- **Enforced, not conventional:** the `gateway↛eventbus` boundary is an ArchUnit rule, not prose. An unenforced boundary rots back (as the implicit "gateway.runtime-as-wiring-home" convention rotted into the co-location this refactor fixed). The liveness guard keeps the rule non-vacuous.
- **Component scan unchanged:** `AgentBusApplication` (plain `@SpringBootApplication` in `com.huawei.ascend.bus`) default-scans `com.huawei.ascend.bus..`; the new `common` + `eventbus.runtime` are auto-covered. No `@ComponentScan` change (javadoc line 19 confirms).
- **No behaviour/profile/bean-name change:** the refactor is a package re-organisation + one boundary rule. Bean names/qualifiers (`forwardRelayTick`/`responseRelayTick`/`forwardRelayWorker`/`responseRelayWorker`/`relayTaskScheduler`/`relayScheduler`), `@Profile("eventbus")`/`@Profile("gateway")`, and Spring 7 idioms (`scheduleWithFixedDelay`, `jakarta.annotation.PostConstruct`) are unchanged.
- **Import ripple (mechanical, Q2 consequence):** 4 files import `common.AgentBusBrokerProperties` post-move — `GatewayRuntimeConfiguration` (stays) + `EventBusRelayConfiguration` + `EventBusRelaySchedulingConfig` + `EventBusRelaySchedulerTest` (moved, no longer co-located with it). `RelayScheduler` needs no import (constructor takes primitives). See `deviations.md` #1/#2.
- **NOT a REQ-2026-001 release blocker** (consistent with ADR-0161's release-bar framing; pure package re-organisation + a green-on-arrival ArchUnit rule).

## Updated baseline

This project is code-driven (no `docs/4plus1/` baseline; uses `architecture/L0|L1|L2` + structurizr per ADR-0147/0150/0160), so the `docs/4plus1/<system>-architecture/element-model.yaml` baseline does not apply. The arch-driven delta artifacts live at `docs/4plus1/delta/agent-bus-layering/` (G2 as-is · G3 decision tree · G4 to-be + sign-off · G5 deviations · G6 as-built + diff · G7 CLOSED). Suite: `./mvnw -f agent-bus/pom.xml test` → 443 green / 14 skip / 0 fail (was 441 +2 = the Q3 rule + liveness guard). No L2 amendment needed (the L2 line naming `gateway.runtime` for the gateway 生产实现 remains accurate for the 3 staying gateway files).
