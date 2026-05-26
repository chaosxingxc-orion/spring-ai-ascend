---
level: L1
view: [logical, process, development, physical, scenarios]
module: agent-service
affects_level: L1
affects_view: [logical, process, development, physical, scenarios]
status: proposed
language: zh-CN
relates_to:
  - docs/logs/reviews/2026-05-22-agent-service-l1-expansion-proposal.cn.md
  - docs/logs/reviews/2026-05-25-xiaoming-agent-service-l1-review-wave-1.md
  - docs/logs/reviews/2026-05-26-agent-service-l1-4plus1-rewrite-wave-1.cn.md
  - docs/L1/agent-service/README.md
  - docs/L1/agent-service/scenarios.md
  - docs/L1/agent-service/logical.md
  - docs/L1/agent-service/process.md
  - docs/L1/agent-service/development.md
  - docs/L1/agent-service/physical.md
  - docs/L1/agent-service/spi-appendix.md
---

# Agent Service L1 4+1 一致性审视（Wave 1）

> 日期：2026-05-26  
> 范围：`docs/L1/agent-service/` 今日新增/迁移的 4+1 视图，以及它们与 2026-05-22 提案、2026-05-25 复审稿、2026-05-26 重写审稿、当前代码/契约之间的一致性。  
> 结论：今天的 4+1 文档整体方向已经对齐 5 月 25/26 的主审结论，尤其是 Task/Run 区分、`SuspendSignal` canonical、三轨总线、Fast/Slow Path 红线、Layer 3 `design_only`、Layer 5a/5b 拆分这些核心点都基本收敛；但仍残留若干 **结构级自相矛盾** 与 **文档-实现漂移**，其中逻辑视图与进程视图仍有会误导后续 L2/实现工作的条目。

## 1. Root Cause

本轮问题的根因不是“设计方向错了”，而是 **rc55 把 rc53 review log 迁移为分视图文件时，只完成了主体搬运，没有把索引/frontmatter、ER 关系、方法签名、测试锚点、W2/W3 状态语句做最后一次跨文件锁步校正**；证据见 `docs/L1/agent-service/README.md:3-7,69-73`、`docs/L1/agent-service/logical.md:155,281-283`、`docs/L1/agent-service/process.md:55-81`。

## 2. Findings

### P1-1 `logical.md` 在 Task/Run 关系上自相矛盾，会直接误导 L2 数据模型

- `docs/L1/agent-service/logical.md:155` 把关系画成 `TASK ||--|| RUN`，即 1:1。
- 但同一文件 `docs/L1/agent-service/logical.md:281-283` 又明确写着 “A single Task in WORKING may have many transient Runs” 与 “Task and Run are TWO DISTINCT entities”。
- 这与 5 月 25/26 审稿主线、ADR-0100/ADR-0136 的语义是一致后者、冲突前者：Task 是 control-state，Run 是 transient compute snapshot，不能在 ER 上画成 1:1 当前执行绑定。

判定：**应改**。  
建议：把 ER 从 `TASK ||--|| RUN` 改成能表达 “一个 Task 可承载多个 Run，某一时刻至多一个 current Run” 的关系；如果想表达“当前激活 Run”，应显式增加 current pointer / projection，而不是用 1:1 关系偷表达。

### P1-2 `process.md` / `physical.md` 把 `RunRepository.updateIfNotTerminal` 画成了一个当前并不存在的签名

- `docs/L1/agent-service/process.md:55,66,72,79,262,269` 多处写成 `updateIfNotTerminal(tid, runId, λ→...)`。
- `docs/L1/agent-service/physical.md:37-39,61` 也把它描述成带租户入参的抽象方法。
- 但当前真实 SPI 是 `agent-service/src/main/java/com/huawei/ascend/service/runtime/runs/spi/RunRepository.java:44`：`Optional<Run> updateIfNotTerminal(UUID runId, UnaryOperator<Run> mutator);`

这不是纯表述问题。当前文档把“调用前已做 tenant guard”与“SPI 方法签名携带 tenantId”混成了一件事，导致后续读者会误以为仓库 SPI 已经升级为 tenant-aware CAS API。

判定：**应改**。  
建议二选一：
- 要么把 4+1 文档改回当前真实签名，只在文字里说明 tenant guard 发生在调用边界；
- 要么如果 rc56 确实准备升级 SPI，再把文档统一标成 `design_only / follow-up impl-mode`，不能用现态口吻描述。

### P1-3 `physical.md` 的 Task/Session 主键类型与当前代码、逻辑视图不一致

- `docs/L1/agent-service/physical.md:49-50` 把 `tasks.task_id`、`sessions.session_id` 写成 `UUID`。
- 但当前 Java 实体是 `agent-service/src/main/java/com/huawei/ascend/service/task/Task.java:39,41` 与 `agent-service/src/main/java/com/huawei/ascend/service/session/Session.java:36`，字段类型都是 `String`。
- `logical.md` 的 ER 也使用了 `String taskId`、`String sessionId`。

这会直接影响后续 Flyway、OpenAPI、L2 persistence 设计，因为当前 4+1 自己内部已经出现 “逻辑视图/实现是 String，物理视图是 UUID” 的双重真相。

判定：**应改**。  
建议：若目标是最终 UUID 化，必须在 `physical.md` 里显式标 `future migration`；如果没有已批准迁移 ADR，则应按当前实现与逻辑视图回写为 `String (UUID-shaped)`。

### P2-1 `scenarios.md` 的 cancel 场景测试锚点写错了，和同页语义相冲突

- `docs/L1/agent-service/scenarios.md:112` 明确写：cross-tenant cancel 在 W0 返回 `404 not_found`。
- 但 `docs/L1/agent-service/scenarios.md:117` 的 test grounding 却写成 `RunHttpContractIT.tenantMismatchReturns403`。
- 当前真实测试是 `agent-service/src/test/java/com/huawei/ascend/service/platform/web/runs/RunHttpContractIT.java:318-336` 的 `getCrossTenantRunReturns404()`；文件里没有 `tenantMismatchReturns403` 这个 cancel/read 测试方法。

判定：**应改**。  
建议：把场景页的测试锚点改成真实存在且语义匹配的 404 用例；不要把 “JWT claim/header mismatch returns 403” 与 “cross-tenant resource access returns 404” 混写。

### P2-2 `process.md` 把 SSE 说成了 “W2-shipped”，与当前契约状态不符

- `docs/L1/agent-service/process.md:81` 写的是 “Client polls `/v1/runs/{runId}` or uses SSE (W2-shipped).”
- 但 `docs/contracts/openapi-v1.yaml:289,295` 明确说 `Flux<RunEvent>` / SSE subscription / webhook callback 都还是 `W2 scope`，当前 v1 版本未纳入已交付面。

这会把 “contract mentions future subscription mode” 误写成 “当前 L1 已交付的进程路径”。

判定：**应改**。  
建议：改成 “poll is current; SSE/webhook are W2 scope” 或显式标 `design_only`。

### P2-3 `README.md` 这个索引页的 frontmatter 和波次状态表都还是骨架态，破坏 4+1 整体自洽

- `docs/L1/agent-service/README.md:3` 把自己标成 `view: scenarios`。
- `docs/L1/agent-service/README.md:7` 又把 `covers_views` 写成 `[scenarios]`。
- 但它在 `docs/L1/agent-service/README.md:12` 明明是 “L1 4+1 Architecture (Index)”。
- 同时 `docs/L1/agent-service/README.md:69-73` 仍写 W3/W4/W5/W6 `pending`，而当前目录里的 `scenarios.md`、`logical.md`、`process.md`、`physical.md`、`development.md`、`spi-appendix.md` 已经有实质内容并处于 `status: active`。

这说明索引页还是从 “rc55 W2 skeleton” 状态遗留过来，没有完成最终锁步。

判定：**应改**。  
建议：
- frontmatter 至少不能伪装成 `scenarios` 单视图；
- `covers_views` 应覆盖完整 4+1；
- wave status 表应改成历史完成态，或直接删掉 skeleton 进度表，避免再制造时间性漂移。

### P2-4 4+1 新目录与模块根文档仍存在跨权威残留冲突，整体上还没有完全闭环

- `agent-service/ARCHITECTURE.md:524` 仍引用 `tenantMismatchReturns403`。
- `agent-service/ARCHITECTURE.md:677` 仍写 `TaskRepository` SPI。
- `docs/L1/agent-service/spi-appendix.md:13,27` 已明确指出 canonical 名称应为 `TaskStateStore`，且把前者定性为旧表述。

虽然这不属于 `docs/L1/agent-service/` 目录内的单文件错误，但它会削弱“ADR-0143 之后这里是 canonical 4+1 source”的可信度，因为模块根文档仍在向读者输出旧词汇。

判定：**应改**。  
建议：把模块根 `ARCHITECTURE.md` 中这两处残留也一并清掉，否则 4+1 迁移还没有真正完成。

## 3. Focused Assessment

### 3.1 Logical View

整体上，Logical View 已经成功吸收了 5 月 25/26 审稿的主要修正：

- 接受了 Layer 5a / 5b 拆分；
- 接受了 Layer 3 `design_only`；
- 接受了 `SuspendSignal` canonical；
- 接受了 `Run aggregate single owner`；
- 接受了 `RunEvent` sealed hierarchy 的 design-only 定位。

但它仍有两个关键问题：

1. **ER 关系没收干净**：`TASK ||--|| RUN` 与同页文字冲突，是本轮最危险的问题。
2. **把调用约束写成了已落地方法签名**：`updateIfNotTerminal(tid, runId, ...)` 不是当前真实 SPI。

结论：**逻辑视图方向正确，但还不能认为已经达到可直接指导 L2/实现的稳定状态。**

### 3.2 Process View

Process View 对 cancel-race loser path 的补充是有效的，这一点比 5 月 25 日稿子明显更完整；但它仍有两类偏差：

1. 多处把 `updateIfNotTerminal` 画成错误签名；
2. 把 SSE 写成了已交付流程，而当前契约仍是 W2 scope。

结论：**进程视图的主干流程合理，但当前“实现态/设计态”边界还不够干净。**

### 3.3 4+1 Overall

整体 4+1 的主要结构已经对齐原设计要求，尤其是以下红线没有再倒退：

- 不再回到 `a2a-java` 运行时依赖；
- 不再把单队列三存储模式当成 Rule R-E 的实现；
- 不再把 Fast-Path 写成可绕过 RLS / reactive / no-sleep；
- 不再把 `InterruptSignal` 当成要替换 `SuspendSignal` 的真实 Java 类型。

但 4+1 整体仍未闭环，主要因为：

- 索引页仍是 skeleton 状态；
- 视图之间的实体关系/ID 类型/方法签名没有完全统一；
- 新 canonical 目录与模块根文档还有残余冲突。

结论：**今天这批 4+1 文档已接近“正确版本”，但还不是“锁步完成版本”。**

## 4. Recommended Fix Order

1. 先修 `logical.md` 的 Task/Run ER 关系与 `updateIfNotTerminal` 签名表述。
2. 再修 `process.md` 的签名表述与 SSE 状态表述。
3. 回写 `physical.md` 的 Task/Session 主键类型，统一到当前实现/ADR。
4. 修 `scenarios.md` 的 test grounding。
5. 最后清理 `README.md` 的 frontmatter 和 wave status，并同步 `agent-service/ARCHITECTURE.md` 的残余旧词汇。

## 5. Supplementary Additions Worth Making

这一轮不是“已有内容错了”，而是“已有内容还不够好用”。以下补充项我建议 **采纳**，但优先级低于前面的结构性纠偏。

### 5.1 建议补一张 `Scenarios -> Logical / Process / Physical` 显式追踪表

这个建议值得采纳。当前 4+1 文件已经互相引用，但还不够机械化；读者理解 S1-S5 仍需要在 `scenarios.md`、`logical.md`、`process.md`、`physical.md` 之间跳转。

建议在 `scenarios.md` 或 `README.md` 增一张收敛表，至少包含：

- 场景 ID：S1-S5
- 经过的逻辑层：L1/L2/L3/L4/L5a/L5b
- 触发的状态机：`RunStatus` / `Task.A2aState`
- 发出的 `RunEvent` 变体
- 物理通道：`control` / `data` / `rhythm`
- 当前交付态：`shipped` / `design_only`

这样做的价值不是“多一张表”，而是把 4+1 的跨视图一致性变成可扫读的矩阵，降低再次漂移的概率。

### 5.2 建议把“双部署模式 / 双调用模型”上升为跨视图显式约束

这个建议也值得采纳，而且我认为比上一个更重要。

当前 `physical.md` 已经写了 Mode A / Mode B，但这条主线在 `logical.md` 与 `process.md` 里还是偏隐含。结果会导致读者误以为：

- 平台中心模式和业务中心模式会改变逻辑责任边界；
- 同进程直调与远程无状态调用会改变 Task/Run 语义。

建议补两条显式约束：

- 在 `logical.md` 补一句：**部署位置变化不改变逻辑责任边界，Layer 1-5 的职责划分恒定。**
- 在 `process.md` 补一句：**同进程直调 / 远程服务调用，只改变物理链路与延迟特征，不改变 Task/Run/Session/SuspendSignal 语义。**

这是原设计主线之一，应该被写成可引用的红线，而不只是从多处文字里“读出来”。

### 5.3 建议在 `logical.md` 增加一组“关键约束清单”

这个建议值得采纳。

当前 `logical.md` 的规则是散落在图、ER、状态机、glossary 里的，专业读者能读出来，但不够利于快速校核。建议加一个很短的小节，集中列 5-8 条红线，例如：

- Runtime 不得直连 Middleware。
- Run 状态变更只能经 `RunRepository.updateIfNotTerminal(...)`。
- Layer 3 是 `design_only` binding，不是已落地代码层。
- Fast-Path 不得绕过 metadata persistence / RLS / reactive / no-sleep 约束。
- A2A 协作不得绕过 `S2cCallbackEnvelope` / `control` 通道约束。
- 每个核心实体必须 tenant-scoped。
- 术语映射不得把 `Task`、`Run`、`SuspendSignal` 混名成实现替代。

这样 `logical.md` 会更像“架构约束面”，而不是仅仅“组件说明面”。

### 5.4 建议在 `process.md` 增加“异常路径总表”

这个建议也值得采纳。

当前 `process.md` 已经有代表性时序图，尤其 cancel-race loser path 已经补得不错；但异常路径仍是分散在各图和各场景里的。建议补一个全局异常收口表，至少覆盖：

- `idempotency_conflict` / `idempotency_body_drift`
- cross-tenant access
- `illegal_state_transition`
- callback timeout
- terminal 之后的 late callback
- child run failure / peer unreachable
- middleware rejection / sandbox denial
- engine mismatch / envelope invalid

每项至少标：

- 谁检测
- 返回码 / Run 状态
- 是否发 `RunEvent`
- 走哪条通道
- 当前是 `shipped` 还是 `design_only`

这样流程视图会从“能看懂几个典型路径”提升到“能支撑实现和测试编排的异常规范”。

### 5.5 这些补充项的优先级判断

我的判断是：

1. **必须先修正结构性不一致**，再补这些增强项。
2. 在增强项里，优先级从高到低大致是：
   1. 双部署 / 双调用模型显式约束
   2. Logical View 关键约束清单
   3. Process View 异常路径总表
   4. Scenarios 跨视图追踪表

原因很简单：前两项是在补“主线约束”，后两项是在补“导航与查表能力”。

## 6. Bottom Line

如果只问“今天的 4+1 是否比 5 月 22/25 的稿子更接近原设计要求”，答案是 **是**。  
如果问“它们现在是否已经完全一致、可以作为后续 L2/实现的无歧义基线”，答案是 **还不行**。

当前最值得立刻修的不是新增设计，而是把 **ER 关系、方法签名、ID 类型、测试锚点、索引元数据** 这五类漂移收干净；否则后续每推进一层 L2，都会把这些小偏差放大成新的 recurring family。

在这些硬问题收敛之后，上述 4 个补充项都值得做；它们不会改变架构结论，但会显著提升 4+1 作为“后续设计与实现基线”的可用性。
