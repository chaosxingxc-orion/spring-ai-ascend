# G4 sign-off — agent-bus layering refactor

**Status:** ✅ SIGNED OFF (2026-07-16). User confirmed all 5 branches as-decided; G5 implement proceeds.

## To-be restated in one paragraph

Move the 3 event-bus relay-wiring files (`EventBusRelayConfiguration`, `EventBusRelaySchedulingConfig`, `RelayScheduler`) from `com.huawei.ascend.bus.gateway.runtime` into a NEW `com.huawei.ascend.bus.eventbus.runtime` package; move the shared `AgentBusBrokerProperties` (`@ConfigurationProperties("agent-bus")`) into a NEW `com.huawei.ascend.bus.common` package (it is consumed by BOTH planes, so it is neither gateway- nor event-bus-specific); keep the 3 gateway-specific files (`GatewayRuntimeConfiguration` gains one `import`, `GatewayRuntimeController`/`GatewayRuntimeService` untouched) in `gateway.runtime`; move `EventBusRelaySchedulerTest` to mirror `RelayScheduler`; add ONE ArchUnit rule (`gateway.runtime ↛ eventbus.runtime`) to make the new boundary load-bearing. No behaviour, profile, bean-name, or component-scan change. No amend to ADR-0161 (it is unrelated); a NEW ADR records this layering at G7.

## The 5 branches the user must confirm (agent 拍板, user ratifies)

| Q | Chosen | The trade-off you're signing |
|---|---|---|
| **Q1** package name | `eventbus.runtime`, flat | matches DoD; sibling to gateway/registry.runtime |
| **Q2** AgentBusBrokerProperties → `common` (not eventbus.runtime) | **deviates from the DoD's literal "4 files → eventbus.runtime"** → becomes 3 + 1. Putting it in eventbus.runtime would make gateway depend on event-bus impl (the inversion being fixed) |
| **Q3** add ONE ArchUnit rule | the one piece of NEW behaviour vs a purely mechanical move. If you want strictly mechanical, say so and the rule stays absent (boundary = convention only) |
| **Q4** new ADR, not amend ADR-0161 | ADR-0161 is about 3 G5-E substrate deviations, NOT this |
| **Q5** leave closed plan doc + L2 line untouched | they're historical/still-accurate records |

## The corrected handoff fact (must be acknowledged)

The handoff's **hard-constraint #1** — *"AgentBusForwardingSpiPurityTest 只许可 Spring 在 gateway.runtime;搬到 eventbus.runtime 后必须给新包发 Spring 许可,否则 purity 测试变红"* — is **code-verified FALSE**. That test scopes `forwarding..` only; no ArchUnit rule scopes `gateway..`/`eventbus..`. Moving the 4 files breaks **zero** rules and needs **no** license grant. The "ArchUnit 规则同步改且 green" DoD item is therefore satisfied vacuously (nothing to sync) EXCEPT for the optional Q3 new rule. This is recorded so the sign-off isn't built on a false premise.

## Sign-off question (verbatim)

> 这个 to-be 方案你确认吗?(5 个 Q1–Q5 分支 + 已纠正的 handoff 事实)确认后我开始写代码(G5 implement)。

## On confirmation (to be filled)

- **Date:** 2026-07-16
- **User's exact words:** 「确认,5 分支全按方案」(G4 AskUserQuestion answer — option "确认,5 分支全按方案")
- **Amendments (if any):** none — all 5 branches ratified as the agent 拍板'd them (Q1 eventbus.runtime flat / Q2 → common / Q3 +1 ArchUnit rule / Q4 new ADR not amend 0161 / Q5 leave closed docs)
- **Corrected-handoff-fact acknowledgement:** user reviewed both code-verified corrections (hard-constraint #1 = false; "reverse ADR-0161" = mis-attributed) as part of the sign-off presentation; sign-off is not built on the false premises.
- **Confirmed deltas:** `++ eventbus.runtime` (3 files), `++ common` (AgentBusBrokerProperties), `~ gateway.runtime` (GatewayRuntimeConfiguration +1 import), `++ EventBusRelaySchedulerTest` → eventbus.runtime, `++ 1 ArchUnit rule` (gateway↛eventbus). DoD: suite 441 green/14 skip/0 fail, ArchUnit green, working tree controlled, commit on experimental separate from prior 6 unpushed, push deferred.
