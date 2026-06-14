---
artifact_type: delivery_projection
version: agent-bus-stage6-review-and-stage7-plan
status: draft
source_commit: d2e48ee74f0dcb821dd29aa87c11d14d78808fd8
source_decision_packet: docs/architecture/l0/10-governance/review-packets/agent-bus-forwarding-runtime-decision.md
source_candidates: docs/architecture/l0/10-governance/review-packets/agent-bus-forwarding-runtime-candidates.md
target_module: agent-bus
---

# agent-bus Stage 6 评审与 Stage 7 大批次计划

## 0. 结论

最新提交 `d2e48ee7` 完成了 Stage 6 的运行态候选裁决记录入口，文档内部逻辑一致，也没有越过此前“不裁决不写生产 runtime 代码”的治理边界。

但这个提交仍然停留在“等待裁决”，没有真正解锁实现。因此它可以作为 Stage 6 的收口输入接受，但不应该继续把后续工作切得过小。下一阶段应从“继续补一个文档”升级为一个较大的交付批次：**完成 C3 裁决落位、补 L2 技术设计、定义 outbox / inbox 状态机与 schema 草案、建立代码骨架和 harness 验收计划，并同步 L1 文档。**

简短判断：

- Stage 6 以 md 为主是正常的，因为它本质是 H2/H3 裁决关卡。
- 继续只有 md 就不正常了。Stage 7 必须让代码开始出现，至少要有可测试的领域模型、状态机、端口接口和 harness。
- 若人类没有反对，Stage 7 默认按 Stage 5 推荐采用 **C3 database outbox / inbox** 作为生产路径；C1 只作为本地非持久化实验或测试替身，不作为生产底座。

## 1. 本次提交审查

### 1.1 完成情况

`d2e48ee7` 的主要产物：

- 新增 `agent-bus-forwarding-runtime-decision.md`，把 Stage 6 定义为 H2/H3 裁决入口。
- 在候选评审中补充推荐进入 H2/H3 的候选：默认 C3、早期实验 C1、暂不推荐 C5。
- 同步 L1 `README.md`、`features/README.md`、`physical.md`、`process.md`，明确 Stage 6 仍是 draft，裁决前不写生产代码。

验收判断：

- 文档与既有边界一致：`agent-bus` 不写 Task execution state、不绕过 routeHandle、不引入具体 broker / MQ 依赖。
- 对“为什么没有代码”的解释成立：Stage 6 被定义为裁决关卡。
- 风险也很明显：它把“推荐”与“裁决”分开，但没有给出默认落位机制，导致工程继续等待。

### 1.2 当前修改意见

| 编号 | 意见 | 严重度 | 处理建议 |
|---|---|---|---|
| MI7-001 | Stage 6 文档仍是 draft，所有实现权限都被“待裁决”挡住。 | 高 | Stage 7 第一项必须完成裁决落位，不能再生成一个新的“等待裁决”文档。 |
| MI7-002 | C3 已是明确推荐，但没有转化成默认工程路径。 | 高 | 若 H2/H3 未提出反对，默认采用 C3；C1 只保留为本地非 durable 替身。 |
| MI7-003 | 后续任务切片过小，容易形成“文档驱动但代码不动”。 | 高 | Stage 7 合并决策、L2 设计、schema、代码骨架、harness、L1 同步为一个大批次。 |
| MI7-004 | C3 会引入持久化语义，可能改变 `agent-bus` 当前偏 SPI 的模块性质。 | 中 | 先明确依赖隔离：SPI 保持纯净；运行态实现放入清晰 runtime 包或后续独立模块，不把数据库细节泄露到 SPI。 |
| MI7-005 | outbox / inbox 的状态机、幂等键、租户隔离、重试语义还没有精确化。 | 高 | Stage 7 必须先定义状态机和验收断言，再写实现骨架。 |

## 2. Stage 7 目标

Stage 7 的目标不是继续评审候选，而是启动最小运行态实现路径：

> 采用 C3 database outbox / inbox 作为 agent-bus 类 MQ 转发的生产候选路径，交付可被后续智能体实现和验证的 L2 设计、代码骨架、harness 计划与测试验收清单。

Stage 7 允许一个智能体在同一批次内完成较大范围工作，但必须遵守边界：

- 允许新增 agent-bus 内的 forwarding runtime 领域模型、端口接口、状态机和测试。
- 允许新增 outbox / inbox schema 草案和 L2 技术设计。
- 允许新增 in-memory test double，但只能作为测试替身或本地非持久化实验。
- 暂不允许引入具体 broker / MQ client。
- 暂不允许 `agent-bus` 写 Task execution state。
- 暂不允许绕过 Stage 3 discovery 的 `routeHandle`。
- 暂不允许消息携带 payload body / token stream；有载荷时继续使用 `payloadRef`。

## 3. Stage 7 开发切片

### 切片 1：裁决落位

修改 `agent-bus-forwarding-runtime-decision.md`：

- 将状态从“等待裁决”推进为“默认采用 C3，待 H2/H3 最终确认”或“已采用 C3”，具体取决于人类负责人是否在执行前确认。
- 明确不采用 C1 / C2 / C4 / C5 的原因：
  - C1 不满足跨进程可靠投递、重启不丢、生产审计。
  - C2 不满足跨实例一致路由与 durable replay。
  - C4 引入 broker 运维与产品绑定，当前阶段过重。
  - C5 复杂度超过最小切片收益。
- 明确 Stage 7 的生产代码许可范围：只允许 C3 最小领域模型、端口接口、状态机、schema 草案、harness；不允许完整调度器接入真实服务调用链。

DoD：

- 文档中不再出现“无裁决导致不得推进”的阻塞状态。
- 允许写代码的范围与禁止范围同时存在，且可被 review。

### 切片 2：L2 技术设计

新增 `architecture/docs/L2/agent-bus/forwarding-outbox-inbox.md`。

内容至少包含：

- 目标：用 outbox / inbox 为 runtime-to-runtime 转发提供 durable、可审计、tenant-scoped 的最小底座。
- 非目标：不定义 Task 生命周期、不拥有 agent 定义、不绑定具体 broker、不实现 payload 存储。
- 组件边界：
  - `ForwardingGateway`：外部到内部入口，负责校验、接收、写入 outbox。
  - `ForwardingOutbox`：待发送消息的 durable queue。
  - `ForwardingDispatcher`：从 outbox 取消息，基于 routeHandle 投递。
  - `ForwardingInbox`：接收端去重、幂等、审计。
  - `ForwardingRegistryPort`：消费 Stage 3 discovery 结果，不拥有 registry。
- 状态机：
  - outbox：`PENDING`、`DISPATCHING`、`ACKED`、`RETRY_SCHEDULED`、`DLQ`、`EXPIRED`。
  - inbox：`RECEIVED`、`DUPLICATE_SUPPRESSED`、`CONSUMED`、`REJECTED`。
- 幂等键：至少包含 `tenantId`、`messageId`、`routeHandle` 或等价稳定路由维度。
- 租户隔离：所有查询、投递、去重、审计都必须带 `tenantId`。
- 失败语义：route missing、tenant mismatch、payloadRef invalid、backpressure rejected、duplicate suppressed。

DoD：

- L2 能直接投影出 schema、接口、测试计划。
- 所有状态迁移都有触发条件、终态和失败码。

### 切片 3：契约与 schema 草案

新增或补齐以下契约文件：

- `docs/architecture/l0/05-contracts/human-readable/ICD-agent-bus-forwarding-runtime.md`
- 可选：`docs/architecture/l0/05-contracts/schemas/agent-bus-forwarding-runtime.v1.yaml`

内容至少包含：

- outbox record 字段：`tenantId`、`messageId`、`sourceServiceId`、`targetServiceId`、`routeHandle`、`payloadRef`、`status`、`attemptCount`、`nextAttemptAt`、`createdAt`、`updatedAt`、`lastFailureCode`。
- inbox record 字段：`tenantId`、`messageId`、`consumerServiceId`、`status`、`receivedAt`、`consumedAt`、`failureCode`。
- 唯一约束建议：
  - outbox：`tenantId + messageId`。
  - inbox：`tenantId + messageId + consumerServiceId`。
- 禁止字段：payload body、token stream、Task execution state、物理 endpoint。

DoD：

- schema 草案可以被 harness 转换为字段级校验。
- 每个字段都有 owner、是否必填、是否可变、脱敏要求。

### 切片 4：代码骨架

在 `agent-bus` 中新增最小代码骨架。建议包名：

- `com.huawei.ascend.bus.forwarding`
- `com.huawei.ascend.bus.forwarding.spi`
- `com.huawei.ascend.bus.forwarding.runtime`

建议类型：

- `ForwardingEnvelope`
- `ForwardingMessageId`
- `ForwardingStatus`
- `ForwardingFailureCode`
- `ForwardingReceipt`
- `ForwardingDispatcher`
- `ForwardingOutboxPort`
- `ForwardingInboxPort`
- `ForwardingStateMachine`
- `ForwardingRouteHandle`

边界要求：

- `spi` 包不能依赖数据库、Spring、HTTP client、broker client。
- runtime 包可以定义端口调用顺序，但不接真实数据库。
- 如果需要 in-memory 实现，只能放在 test fixture 或明确标注 non-production。
- 不新增跨模块运行时依赖，除非同步更新 module metadata 并通过架构 review。

DoD：

- 代码能表达 C3 最小语义。
- 编译期可以阻止非法状态迁移或至少通过单元测试覆盖非法迁移。
- 生产代码中没有 concrete broker / MQ / JDBC driver 依赖。

### 切片 5：harness 与测试计划

新增测试或测试计划，至少覆盖：

- tenant mismatch 被拒绝。
- 缺失 `routeHandle` 被拒绝。
- payload body 出现在 envelope 时被拒绝。
- `payloadRef` 在有载荷消息中缺失时被拒绝。
- duplicate inbox message 被 suppress。
- `PENDING -> DISPATCHING -> ACKED` 正常路径。
- `DISPATCHING -> RETRY_SCHEDULED -> DISPATCHING` 重试路径。
- 超过重试上限进入 `DLQ`。
- `agent-bus` 不写 Task execution state。
- SPI 包不依赖 runtime / DB / broker。

验证命令作为计划交给施工智能体执行：

```powershell
.\mvnw.cmd -pl agent-bus test
rg -n "TaskExecution|TaskStatus|payloadBody|Kafka|RabbitMQ|RocketMQ|NATS" agent-bus
```

DoD：

- 施工智能体必须在提交说明中记录验证结果。
- 如果验证未跑，必须说明原因并标注阻塞项。

### 切片 6：L1 同步

同步以下文档：

- `architecture/docs/L1/agent-bus/README.md`
- `architecture/docs/L1/agent-bus/logical.md`
- `architecture/docs/L1/agent-bus/process.md`
- `architecture/docs/L1/agent-bus/development.md`
- `architecture/docs/L1/agent-bus/features/README.md`

同步重点：

- Stage 7 已从“候选裁决”进入“C3 最小运行态实现路径”。
- Gateway 与真 bus 的关系要保持清晰：Gateway 负责入口校验、接收和写 outbox；真 bus 负责 service-to-service 投递、inbox 去重和路由治理。
- 文档不能宣称已经有完整 runtime，只能宣称有最小骨架、状态机和 harness。

DoD：

- L1 与代码状态一致。
- 没有把设计态能力写成已生产可用能力。

## 4. 验收标准

Stage 7 可以接受的结果：

- C3 决策不再处于阻塞状态。
- L2 文档能解释 outbox / inbox 的结构、状态机、失败语义和边界。
- 代码中出现最小 forwarding runtime 骨架，并有单元测试或 harness 覆盖关键规则。
- 不引入 broker / MQ 产品依赖。
- 不把 `agent-bus` 变成 Task 生命周期 owner。
- 不绕过 discovery `routeHandle`。
- 不把 payload body 放入 forwarding envelope。

Stage 7 不能接受的结果：

- 继续只提交 md，且没有代码骨架或测试。
- 直接接入 Kafka / RabbitMQ / RocketMQ / NATS 等具体产品。
- 直接写真实数据库实现但没有 L2 schema / 状态机 / 迁移策略。
- 让 `agent-bus` 修改 Task 状态。
- 在 envelope 中塞入 payload body 或 token stream。

## 5. 给施工智能体的执行提示

这次任务应作为一个较大批次执行，不要拆成“只改一个文档”的小任务。建议一次提交包含：

1. C3 裁决落位。
2. L2 outbox / inbox 技术设计。
3. runtime forwarding 契约或 schema 草案。
4. agent-bus 代码骨架。
5. 状态机与契约测试。
6. L1 文档同步。

如果施工中发现 C3 需要真实数据库依赖才能表达，应先停在端口接口和状态机，不要直接引入数据库驱动。真实持久化实现可以作为 Stage 8。

## 6. 下一阶段建议

Stage 8 再处理真实持久化实现：

- 选择是否复用现有数据库基础设施。
- 定义 migration / rollback 策略。
- 定义 polling、lease、并发抢占和 backpressure 参数。
- 决定是否需要独立 adapter module。
- 将 outbox / inbox 接入 agent-runtime 的受控调用路径。

Stage 7 的重点是把“可以开始写代码”的路铺出来，并让最小代码能被测试约束住。
