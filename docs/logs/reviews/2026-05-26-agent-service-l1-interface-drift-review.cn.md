---
level: L1
view: [logical, development, process, scenarios]
module: agent-service
affects_level: L1, L2
affects_view: [logical, development, process, scenarios]
status: proposed
language: zh-CN
relates_to:
  - agent-service/ARCHITECTURE.md
  - agent-service/module-metadata.yaml
  - docs/dfx/agent-service.yaml
  - docs/contracts/contract-catalog.md
  - docs/contracts/openapi-v1.yaml
  - docs/logs/reviews/2026-05-26-agent-service-l1-4plus1-rewrite-wave-1.en.md
  - docs/logs/reviews/2026-05-26-agent-service-l1-4plus1-rewrite-wave-1.cn.md
  - docs/logs/reviews/2026-05-26-agent-service-l1-service-local-performance-and-parallel-delivery-plan.en.md
  - docs/logs/reviews/2026-05-26-l0-rc53-post-closure-agentic-composition-review.en.md
  - docs/logs/reviews/2026-05-26-l0-rc54-agentic-composition-corrective-response.en.md
  - docs/adr/0136-vocabulary-reconciliation-pr71-task-vs-run.yaml
  - docs/adr/0137-suspendsignal-canonical-interruptsignal-glossary.yaml
  - docs/adr/0138-agent-service-five-layer-l1-ratification.yaml
  - docs/adr/0139-fast-slow-path-narrowed-semantics.yaml
---

# Agent Service L1 接口漂移审查：SPI 对齐、L2 seam 缺口与整改清单

> 日期：2026-05-26
> 范围：`agent-service` 模块的公开 SPI、服务内部 seam、HTTP cursor contract、L2 interface registry。
> 目标：回答 service 层接口定义是否违背当前 L1/L2 架构提议、是否发生代码设计漂移、是否存在已规划但未实现或未登记的接口。
> 结论预览：公开 SPI 四方对齐基本成立；开放问题集中在 L2 内部 seam 尚未落地、`RunRepository` tenant-aware CAS 契约弱化、旧 L2 proposal 对三轨队列的语气与 canonical L1 冲突，以及 `agent-service/ARCHITECTURE.md` 中一处 HTTP 状态码陈旧。

## 1. 背景

`agent-service` 在 rc53 L1 4+1 重写后，被明确建模为五层：

1. Access Layer。
2. Session & Task Manager。
3. Internal Event Queue。
4. Task-Centric Control Layer。
5. Engine Adapter Layer。

rc54 随后关闭了 agent/advisor composition 缺口，把 `AgentDefinition.advisorBindings` 与同包 `AdvisorBinding` 补齐。现在需要单独审查的问题不是“是否继续扩展 service 层”，而是：

- 现有 Java `public interface` 是否仍与 `module-metadata.yaml`、DFX、contract catalog、L1 SPI Appendix 对齐。
- L2 delivery plan 提出的 interface seam 是否已经落地，若未落地，是否有明确规划边界。
- 旧 review proposal 中是否仍有与 canonical L1 红线冲突的表述。
- 是否存在接口签名本身弱于架构不变式的地方。

## 2. 根因与最强解读

### 2.1 根因

rc53/rc54 在同一天完成了 L1 4+1 ratification、agentic SPI 补面、advisor composition 修复和 L2 delivery plan 拆分；文档与代码跨越了“已发布 SPI”、“设计态内部 seam”、“未来 L2 backlog”三种成熟度，尚未全部重新归一。

证据：

- `agent-service/ARCHITECTURE.md:19-35` 指向 rc53 4+1 review draft，并声明其在冲突时优先。
- `docs/logs/reviews/2026-05-26-agent-service-l1-4plus1-rewrite-wave-1.en.md:1116-1138` 声明 9 个当前 agent-service SPI，并明确本 wave 不新增 Java SPI。
- `docs/logs/reviews/2026-05-26-agent-service-l1-service-local-performance-and-parallel-delivery-plan.en.md:381-436` 另行提出 IF-SVC-001..012 L2 proposed seams。
- `docs/logs/reviews/2026-05-26-agent-service-l1-service-local-performance-and-parallel-delivery-plan.en.md:388` 的 `EventClassifier` row 仍使用 optional channel wording，与同文档的 `physical performance rule` 和 rc53 canonical L1 的 Rule R-E 红线需要统一。

### 2.2 最强解读

最强有效解读是：以 rc53 4+1 文档、rc54 corrective response、`module-metadata.yaml`、DFX、contract catalog、OpenAPI 为当前权威；L2 performance plan 中 IF-SVC-001..012 是“待实现内部 seam registry”，不是已经导出的 public SPI；旧 proposal 若与 canonical L1 冲突，应作为待修订的 design drift，而不是同权来源。

## 3. 权威基线

| Surface | 当前结论 | 证据 |
|---|---|---|
| SPI package registration | `agent-service` 登记 7 个 SPI package。 | `agent-service/module-metadata.yaml:13-20` |
| DFX SPI parity | DFX 列出同一组 7 个 SPI package。 | `docs/dfx/agent-service.yaml:14-21` |
| Contract catalog SPI count | catalog 声明 `agent-service` 有 9 个 SPI interface。 | `docs/contracts/contract-catalog.md:75` |
| L1 SPI Appendix | rc53 4+1 Appendix 声明 9 个 interface，且 4-way parity。 | `docs/logs/reviews/2026-05-26-agent-service-l1-4plus1-rewrite-wave-1.en.md:1116-1134` |
| Java filesystem | `agent-service/src/main/java/.../spi` 下实际存在 9 个 `public interface`。 | 本审查命令：`rg -n "^public interface " agent-service/src/main/java/com/huawei/ascend/service -g "*.java" \| rg "[/\\\\]spi[/\\\\]"` |
| Cursor flow HTTP contract | `POST /v1/runs` 为 `202 + TaskCursor`，不是同步 201。 | `docs/contracts/openapi-v1.yaml:49-54` |

## 4. 公开 SPI 对齐表

| # | Interface | Package | Catalog/DFX/module metadata | Java 状态 | 本次判定 |
|---|---|---|---|---|---|
| 1 | `RunRepository` | `service.runtime.runs.spi` | 已登记 | `public interface` 存在 | 对齐，但 tenant-aware CAS 签名偏弱，见 IF-DRIFT-001 |
| 2 | `GraphMemoryRepository` | `service.runtime.memory.spi` | 已登记 | `public interface` 存在 | 对齐 |
| 3 | `ResilienceContract` | `service.runtime.resilience.spi` | 已登记 | `public interface` 存在 | 对齐 |
| 4 | `SkillCapacityRegistry` | `service.runtime.resilience.spi` | 已登记 | `public interface` 存在 | 对齐 |
| 5 | `StatelessEngine` | `service.engine.spi` | 已登记 | `public interface` 存在 | 对齐，runtime orchestration wiring 仍 deferred |
| 6 | `ContextProjector` | `service.session.spi` | 已登记 | `public interface` 存在 | 对齐，但 L2 desired carrier 更强，见 IF-DRIFT-003 |
| 7 | `TaskStateStore` | `service.task.spi` | 已登记 | `public interface` 存在 | 对齐，但 L2 CAS/revision 契约未落，见 IF-DRIFT-002 |
| 8 | `Agent` | `service.agent.spi` | 已登记 | `public interface` 存在 | 对齐；advisor binding 已由 rc54 修复 |
| 9 | `AgentRegistry` | `service.agent.spi` | 已登记 | `public interface` 存在 | 对齐；production registry implementation 仍 W3 trigger |

## 5. 已关闭问题

### 5.1 Agent / Advisor composition gap 已关闭

rc53 review 指出 `ChatAdvisor` 没有绑定进 `AgentDefinition`，导致 `chat-advisor.v1.yaml`、ADR-0132、quickstart 与 Java surface 不一致。rc54 corrective 明确通过 `AgentDefinition.advisorBindings` 和同包 `AdvisorBinding` 修复。

证据：

- 原始问题：`docs/logs/reviews/2026-05-26-l0-rc53-post-closure-agentic-composition-review.en.md:135-182`。
- 修复记录：`docs/logs/reviews/2026-05-26-l0-rc54-agentic-composition-corrective-response.en.md:38-40`。
- Java surface：`agent-service/src/main/java/com/huawei/ascend/service/agent/spi/Agent.java:63-64`，`AgentDefinition.java:73-79`。

判定：closed。后续只需要保持 `agent-definition.v1.yaml`、quickstart、contract-catalog 与 Java carrier 同步。

### 5.2 当前 9 个 public SPI 无遗漏

本审查未发现 `agent-service` 下有未登记的 `.spi` public interface，也未发现 catalog 中声明但 Java 文件不存在的 service SPI。

判定：closed for current public SPI set。

## 6. 开放接口漂移清单

### IF-DRIFT-001：`RunRepository.updateIfNotTerminal` 缺少 tenant-aware CAS 参数

| 字段 | 内容 |
|---|---|
| 严重度 | P1 |
| 类型 | 接口签名弱于 L1 不变式 |
| 当前 Java | `Optional<Run> updateIfNotTerminal(UUID runId, UnaryOperator<Run> mutator)` |
| 架构期望 | 状态写入同时满足 tenant scope、terminal guard、atomic CAS |
| 证据 | L1 过程图多处写 `updateIfNotTerminal(tid, runId, ...)`：`docs/logs/reviews/2026-05-26-agent-service-l1-4plus1-rewrite-wave-1.en.md:759-776`；Java SPI 为 `RunRepository.java:44` |
| 当前风险 | W1 HTTP cancel 先 `findById` + tenant guard，再 CAS，因此当前 HTTP 行为可接受；但 W2 durable repository 如果只按 `runId` CAS，tenant/RLS contract 依赖外部调用者纪律，不是接口自身保证。 |
| 建议动作 | 在下一 implementation wave 中新增 tenant-aware 原子方法，例如 `updateIfNotTerminal(String tenantId, UUID runId, UnaryOperator<Run> mutator)`；旧方法保留为 default 或 internal dev helper，并由 ArchUnit/contract test 禁止 HTTP/control 写路径调用弱方法。 |
| 状态 | open |

### IF-DRIFT-002：`TaskStateStore` 已发布接口低于 L2 CAS/revision contract

| 字段 | 内容 |
|---|---|
| 严重度 | P1 |
| 类型 | 已发布 SPI 与 L2 proposed seam 差距 |
| 当前 Java | `void save(String taskId, String tenantId, Map<String,Object> state)` + `Optional<Map<String,Object>> load(...)` |
| 架构期望 | IF-SVC-003 要求 CAS command 携带 tenant、taskId、expected revision、next state、cause，并拒绝 stale revision、terminal mutation、cross-tenant access。 |
| 证据 | `agent-service/src/main/java/com/huawei/ascend/service/task/spi/TaskStateStore.java:24-43`；`docs/logs/reviews/2026-05-26-agent-service-l1-service-local-performance-and-parallel-delivery-plan.en.md:427` |
| 当前风险 | Task 层会成为 Run/Session 之上的外部可见控制状态；若没有 revision/CAS，后续 cancel-vs-complete、input-required-vs-terminal 等竞态会在 Task 层复发。 |
| 建议动作 | 将 `TaskStateStore` 从 map-store 升级为 typed command/result contract；至少新增 `compareAndUpdate(tenantId, taskId, expectedRevision, mutation)`，并补 `TaskStateStoreCasTest`。 |
| 状态 | open |

### IF-DRIFT-003：`ContextProjector` 入参不足以承载 L2 projection policy

| 字段 | 内容 |
|---|---|
| 严重度 | P2 |
| 类型 | SPI carrier 过窄 |
| 当前 Java | `Map<String,Object> project(String sessionId, String tenantId, String projectionPolicy)` |
| 架构期望 | IF-SVC-004 要求 projection input 携带 tenant、sessionId、taskId、projection policy、token budget、memory references。 |
| 证据 | `agent-service/src/main/java/com/huawei/ascend/service/session/spi/ContextProjector.java:20-36`；`docs/logs/reviews/2026-05-26-agent-service-l1-service-local-performance-and-parallel-delivery-plan.en.md:428` |
| 当前风险 | Engine Adapter Layer 的 Context Translator 需要把 Session、Task、Memory、PromptTemplate、StructuredOutputConverter 合成上下文；当前字符串 policy 无法表达预算、任务视角、memory refs，容易把 policy 细节塞进 map。 |
| 建议动作 | 新增 typed carrier，例如 `ContextProjectionRequest` / `ProjectedContext`，保留旧方法为 dev shortcut 或迁移 shim；补 oversized projection、missing policy、forbidden field negative tests。 |
| 状态 | open |

### IF-DRIFT-004：L2 queue proposal 把三轨路由写成 optional，与 Rule R-E 红线冲突

| 字段 | 内容 |
|---|---|
| 严重度 | P0 文档漂移 |
| 类型 | 旧 review proposal 与 canonical L1 冲突 |
| 漂移位置 | `docs/logs/reviews/2026-05-26-agent-service-l1-service-local-performance-and-parallel-delivery-plan.en.md:388` |
| canonical 权威 | rc53 4+1 明确 reject 单队列 + 三存储模式，要求按 intent 绑定 `control/data/rhythm` 物理通道。 |
| 证据 | `docs/logs/reviews/2026-05-26-agent-service-l1-4plus1-rewrite-wave-1.en.md:141`、`:182`、`:544` |
| 当前风险 | 后续实现者可能按 L2 proposal 建一个统一 queue + priority class，而不是从接口层建模三轨通道，直接触发 F-design-doc-violates-three-track-bus。 |
| 建议动作 | 新增 L2 proposal corrective draft 或直接修订该 proposal 的后续 sibling：把 “optional/candidate refinement” 改为 “mandatory channel binding; durability remains per-channel deployment axis”。 |
| 状态 | open |

### IF-DRIFT-005：`agent-service/ARCHITECTURE.md` 对 `POST /v1/runs` 状态码仍写 201

| 字段 | 内容 |
|---|---|
| 严重度 | P2 |
| 类型 | L1 grounding prose 陈旧 |
| 当前文档 | `POST /v1/runs` -> `201` with status `PENDING` |
| 当前代码/契约 | OpenAPI、contract-catalog、RunController 均为 `202 + TaskCursor` |
| 证据 | 陈旧处：`agent-service/ARCHITECTURE.md:166-167`；OpenAPI：`docs/contracts/openapi-v1.yaml:49-54`；代码：`RunController.java:115-116` |
| 当前风险 | 不是运行时 bug，但会误导接口评审与 client contract 判断。 |
| 建议动作 | 在下一 doc corrective wave 中把该段改为 `202 + TaskCursor`，并引用 Rule R-F cursor flow。 |
| 状态 | open |

### IF-DRIFT-006：L2 proposed interface registry 尚未进入 public SPI parity 面

| 字段 | 内容 |
|---|---|
| 严重度 | P2 |
| 类型 | 已规划未实现 |
| 涉及接口 | `SessionManager`、`TaskManager`、`InternalEvent`、`EventClassifier`、`QueueStorage`、`BackpressureController`、`DispatchCommand`、`DualTrackRouter`、`ResumeDispatcher`、`ModelGatewayFacade`、`ModelCachePolicy` |
| 证据 | `docs/logs/reviews/2026-05-26-agent-service-l1-service-local-performance-and-parallel-delivery-plan.en.md:381-436` |
| 当前状态 | 这些是 proposed L2 seams；除 `TaskStateStore`、`ContextProjector` 等 existing anchors 外，多数还没有 Java type，也没有 module-metadata/DFX/catalog 登记。 |
| 当前风险 | 如果被 PR/README/ARCHITECTURE 写成 shipped SPI，会造成 authority drift；如果不拆 wave owner，则实现者可能并行造不兼容 carrier。 |
| 建议动作 | 新增 “Interface Landing Wave” 计划：先把 IF-SVC-001..012 分成 public SPI / internal seam / carrier-only 三类，再决定哪些进入 `module-metadata.yaml#spi_packages`。 |
| 状态 | deferred/open |

## 7. 已规划但未实现的接口分层

| Proposed seam | 建议 visibility | 是否应进入 `.spi` | 先决条件 |
|---|---|---|---|
| `SessionManager` | internal seam | 否，先作为 application service | typed session command/result、tenant not-found semantics |
| `TaskManager` | internal seam | 否，先作为 application service | Task transition DFA、idempotency admission、TaskCursor shape |
| `TaskStateStore` CAS upgrade | persistence boundary | 已在 `.spi`，需要升级 | revision/CAS carrier、negative tests |
| `ContextProjector` typed request | public/internal SPI boundary | 已在 `.spi`，需要扩展 | projection policy、token budget、memory refs |
| `InternalEvent` | carrier contract | 否，先 carrier | tenantId、intent、lane、payload cap |
| `EventClassifier` | internal seam | 视 broker abstraction 决定 | Rule R-E lane mapping tests |
| `QueueStorage` | persistence/broker boundary | 可能是 `.spi` | first external broker binding 或 durable queue backend |
| `DispatchCommand` | carrier contract | 否，carrier | lease token、task/run/session refs |
| `DualTrackRouter` | internal seam or future SPI | 暂不登记，除非第三方扩展 fast/slow policy | ADR-0139 predicate table |
| `ResumeDispatcher` | internal seam | 暂不登记 | resume re-auth widening、checkpoint/ref carrier |
| `ModelGatewayFacade` | service-owned facade | 否；`ModelGateway` 已在 middleware SPI | cache policy、budget、tenant-safe key |
| `ModelCachePolicy` | carrier/config contract | 否，先 carrier/config | cache-key isolation tests |

## 8. 推荐整改顺序

### Wave A：文档漂移先止血

1. 修订或新增 corrective draft，关闭 IF-DRIFT-004：三轨队列从 optional 改为 mandatory binding。
2. 修订 `agent-service/ARCHITECTURE.md` 的 `POST /v1/runs` 状态码：201 -> 202 + TaskCursor。
3. 在 L2 proposal 的 “Open Questions” 中标明：`QueueStorage` 是否 public SPI 取决于 first external broker binding，不得提前登记为 public SPI。

### Wave B：接口 carrier 升级

1. 给 `RunRepository` 增加 tenant-aware CAS 方法，并迁移 HTTP/control 写路径。
2. 给 `TaskStateStore` 增加 revision/CAS typed command。
3. 给 `ContextProjector` 增加 typed request/result carrier。

### Wave C：L2 seam landing

1. 为 IF-SVC-001..012 建立 landing matrix。
2. 每个 seam 先有 contract test，再有 reference implementation。
3. 只有需要第三方实现的 seam 才进入 `.spi`、DFX、catalog 四方 parity；纯内部 application service 不登记为 public SPI。

## 9. 验证建议

本次审查是静态架构审查，未执行 Maven/gate。后续 corrective PR 建议至少运行：

```text
wsl bash -lc "rg -n '^public interface ' agent-service/src/main/java/com/huawei/ascend/service -g '*.java' | rg '[/\\\\]spi[/\\\\]'"
wsl bash -lc "rg -n 'POST /v1/runs|TaskCursor|202|201' agent-service/ARCHITECTURE.md docs/contracts/openapi-v1.yaml docs/contracts/contract-catalog.md"
wsl bash -lc "rg -n 'optional control/data/rhythm|candidate refinement|control|data|rhythm' docs/logs/reviews/2026-05-26-agent-service-l1-service-local-performance-and-parallel-delivery-plan.en.md docs/logs/reviews/2026-05-26-agent-service-l1-4plus1-rewrite-wave-1.en.md"
wsl bash -lc "./mvnw -pl agent-service -am -DskipITs -Dsurefire.failIfNoSpecifiedTests=false -Dtest=InMemoryRunRegistryUpdateIfNotTerminalTest,RunCursorFlowIT,AgentSpiCarrierImmutabilityTest test"
```

正式关闭前仍应使用仓库 canonical 命令：

```text
wsl bash -lc "./mvnw clean verify"
wsl bash -lc "bash gate/check_architecture_sync.sh"
```

## 10. 关闭标准

| ID | 关闭条件 |
|---|---|
| IF-DRIFT-001 | tenant-aware CAS 方法存在；HTTP cancel 和 orchestrator/control 写路径不再调用弱 CAS；测试覆盖 cross-tenant + terminal race。 |
| IF-DRIFT-002 | `TaskStateStore` 有 typed CAS/revision contract；至少一个 in-memory reference test 覆盖 stale revision、terminal mutation、cross-tenant not-found。 |
| IF-DRIFT-003 | `ContextProjector` 有 typed request/result carrier；oversized/missing-policy/forbidden-field negative tests 存在。 |
| IF-DRIFT-004 | L2 proposal 不再把三轨路由写成 optional；同段引用 Rule R-E / `bus-channels.yaml`。 |
| IF-DRIFT-005 | `agent-service/ARCHITECTURE.md`、OpenAPI、contract-catalog、RunController 对 `POST /v1/runs` 状态码一致为 `202 + TaskCursor`。 |
| IF-DRIFT-006 | IF-SVC-001..012 完成 visibility 分类；public SPI 才进入 module metadata / DFX / catalog；internal seam 只进入 package/development view。 |

## 11. 本次审查结论

`agent-service` 的当前公开 SPI 没有出现“代码里有而权威表没登记”或“权威表声明但 Java 缺失”的硬性漂移。真正需要尽快处理的是接口强度与 L2 计划之间的落差：`RunRepository` 的 tenant-aware CAS 应提升为接口级保障，`TaskStateStore` 与 `ContextProjector` 应从 map/string shortcut 走向 typed carrier，Internal Event Queue 的三轨通道必须从文档语气上恢复为 mandatory invariant。

换句话说：service 层接口不是乱了，但已经到了必须把 L2 seam “分级、定界、逐个落地”的阶段。下一步不宜直接大规模实现；应先做一个小而硬的 interface landing wave，把 public SPI、internal seam、carrier-only 三类切干净。
