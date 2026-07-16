# G4 sign-off — forwarding-reorg

**Status:** ✅ SIGNED OFF (2026-07-16). User confirmed all 5 branches as-decided; G5 implement proceeds.

## To-be restated in one paragraph

Reorganize `agent-bus` into a forwarding-centric structure: fold the 3 event-bus wiring files into `forwarding/runtime/relay/` (eliminating top-level `eventbus.runtime/`); move `AgentBusBrokerProperties` to `forwarding/common/` (eliminating top-level `common/`); extract the broker SPI (2 Port interfaces + 3 value-object records — `BrokerForwardingConsumerPort`/`BrokerForwardingRelayPort`/`BrokerInboundMessage`/`BrokerProduceOutcome`/`DeliveryFilter`) to a NEW `forwarding/spi/broker/` subpackage; move the 2 rocketmq impl to a NEW `forwarding/runtime/transport/broker/rocketmq/`; keep the 4 broker-common classes in `broker/`; keep `gateway/runtime/` structure (import follows common→`forwarding/common`); keep the build single-module + `@Profile` (multi-module is a deferred follow-on, NOT now); replace the `gateway↛eventbus` ArchUnit rule (ADR-0162) with the stricter `gateway.runtime ↛ forwarding.runtime..` (new ADR-0163, superseding 0162's layering). No behaviour/profile/bean-name/build change.

## The branches the user must confirm (agent 拍板, user ratifies)

| Q | Chosen | The trade-off you're signing |
|---|---|---|
| **Q1** build strategy | single-module + @Profile (reorg packages only); multi-module DEFERRED | you get the cleaner directory + the SDK boundary made explicit, but NOT 3 separate Maven artifacts yet (the @Profile mechanism already yields distinct process forms). If you want actual separate artifacts NOW, pick multi-module (bigger build ripple) |
| **Q2** event-bus wiring home | `forwarding/runtime/relay/` (co-located with `EventBusRelayWorker`) | the @Configuration + scheduler join the relay mechanism they wire |
| **Q3** broker split | SPI (2 Ports + 3 records) → `forwarding/spi/broker/`; common (4) stay `broker/`; rocketmq (2) → `broker/rocketmq/` | code-verified: the 5 SPI classes reference only `forwarding.spi` (no inversion); the `spi/broker/` subpackage groups the broker SPI (alternative: flat into `forwarding/spi/` — flag if preferred) |
| **Q5** ADR/rule | new ADR-0163 supersedes 0162's layering + replaces `gateway↛eventbus` with stricter `gateway↛forwarding.runtime..` | 0162's top-level-plane premise no longer holds (folded into forwarding); the rule STRENGTHENS (gateway never depends on ANY forwarding runtime impl) |

(Q4 SDK boundary flows from Q1=b: the SDK = `forwarding/spi` + `forwarding/spi/broker` + `forwarding/common` + `forwarding/runtime/transport/broker` common + `forwarding/runtime/transport/a2a` + `forwarding/runtime/relay` mechanism + `forwarding/runtime/persistence/jdbc` — a documented package boundary, not a separate artifact.)

## Sign-off question (verbatim)

> 这个 to-be 方案你确认吗?(Q1–Q5 分支)确认后我开始写代码(G5 implement)。嗯/差不多/先做不算。

## On confirmation (to be filled)

- **Date:** 2026-07-16
- **User's exact words:** 「确认,5 分支全按方案」(G4 AskUserQuestion answer — option "确认,5 分支全按方案")
- **Amendments (if any):** none — all 5 branches ratified as the agent 拍板'd them (Q1 single-module+@Profile multi-module-deferred / Q2 event-bus→forwarding/runtime/relay/ / Q3 broker SPI→forwarding/spi/broker/ + common stays broker/ + rocketmq→broker/rocketmq/ / Q4 SDK package-set / Q5 new ADR-0163 supersedes 0162 + stricter gateway↛forwarding.runtime rule)
- **Confirmed deltas:** `++ forwarding/common` (AgentBusBrokerProperties), `++ forwarding/spi/broker/` (5 broker SPI classes), `++ forwarding/runtime/relay/` gains 3 event-bus wiring, `++ forwarding/runtime/transport/broker/rocketmq/` (2 impl), `~ transport/broker/` (4 common stay), `~ gateway/runtime` (import→forwarding.common), 2 top-level pkgs eliminated (common/ + eventbus.runtime/), ArchUnit `gateway↛forwarding.runtime` (ADR-0163, stricter than 0162's gateway↛eventbus). Build unchanged (Q1=b). DoD: 11 files moved + arch-test edits + import ripple + suite green.
