---
artifact_type: a2d_delivery_projection
version: "agent-bus-stage2-review-and-stage3-plan"
status: draft
source_commit: "d894f494 feat(agent-bus): S2C tenant 契约迁移 (Stage 2)"
source_stage2_plan: "docs/architecture/l0/10-governance/delivery-projections/agent-bus-stage1-review-and-stage2-plan.md"
source_l1: "architecture/docs/L1/agent-bus/README.md"
target_module: agent-bus
---

# agent-bus Stage 2 评审与 Stage 3 计划

## 1. Stage 2 评审结论

结论：`接受，带修改意见`

最新提交 `d894f494` 基本完成了 Stage 2 的核心目标：

- `S2cCallbackEnvelope` 已增加 required `tenantId`。
- compact constructor 已校验 `tenantId` 非 null、非 blank。
- `S2cCallbackEnvelopeLibraryTest` 已补充 null / blank tenantId 负向测试，并更新现有构造点。
- `docs/contracts/s2c-callback.v1.yaml` 已把 `tenant_id` 加入 request required fields。
- `contract-catalog.md` 和 `contract-catalog.md.j2` 已把 deferred preferred fix 升级为 migrated fact。
- `architecture/docs/L1/agent-bus/spi-appendix.md` 已把 S2C tenant 状态改为已迁移。
- 没有改变 Task lifecycle 所有权。
- 没有实现 broker、agent registry、service discovery 或 MQ-like forwarding runtime。

本地验证状态：

- 已尝试执行 `.\mvnw.cmd -pl agent-bus test`。
- 未能运行，原因是当前环境 `JAVA_HOME` 未正确配置。
- 提交信息记录了其他环境中的验证结果：`mvn -pl agent-bus -am test -B`，Tests run: 53, Failures: 0, Errors: 0, Skipped: 0。

## 2. 当前修改意见

### MI-001：`s2c-callback.v1.yaml` 仍保留旧的 “Six mandatory fields” 注释

位置：

- `docs/contracts/s2c-callback.v1.yaml`

问题：

- request required fields 当前已经是 7 个：`callback_id`、`tenant_id`、`server_run_id`、`capability_ref`、`request_payload`、`trace_id`、`idempotency_key`。
- 紧邻 required fields 的注释仍写 “Six mandatory fields per the Phase 3a cross-rule audit”。

影响：

- 这会让后续 agent 或评审人误以为 `tenant_id` 不是 mandatory field 的一部分。
- 也会削弱 Stage 2 迁移的事实一致性。

建议：

- 改为 “Seven mandatory fields: the original six from Phase 3a plus tenant_id added in Stage 2”。

### MI-002：`agent-bus` L1 development view 仍写 `tenant 迁移待做`

位置：

- `architecture/docs/L1/agent-bus/development.md`

问题：

- `bus.spi.s2c` 成熟度仍写 “tenant 迁移待做”。
- 测试现状仍写 `S2cCallbackEnvelopeLibraryTest` 需要随 `tenantId` 迁移更新。
- 第 6 节仍以“迁移前必须完成通知和 owner 确认”的时态描述已完成迁移。

影响：

- L1 development view 与最新代码事实冲突。
- 后续智能体可能重复执行已经完成的 Stage 2 迁移。

建议：

- 把 `bus.spi.s2c` 成熟度改为 “SPI 已存在，S2C tenant 已迁移，runtime 构造点待后续波次补齐”。
- 把测试现状改为 “tenantId required-field harness 已补齐”。
- 第 6 节改为 “迁移结果与剩余影响”，保留 runtime 构造点待补。

### MI-003：Feature Catalog 仍把 S2C tenant 写成待做

位置：

- `architecture/docs/L1/agent-bus/features/README.md`

问题：

- AB-F03 / AB-F04 仍写 `tenant 迁移待做`。
- 成熟度定义仍保留 “tenant 迁移待做 = 代码契约尚未改”。
- “不进入当前实现的能力”仍禁止 “S2C tenant 迁移代码改动”。
- 尾部仍写 “S2C tenant 迁移虽然是已接受方向，但必须进入独立切片，并在通知冲突方后施工”。

影响：

- Feature catalog 与 Stage 2 代码事实冲突。

建议：

- 新增或替换成熟度为 “tenant 已迁移”。
- AB-F03 / AB-F04 改为 “tenant 已迁移，runtime 构造点待补”。
- 从“不进入当前实现的能力”中移除 S2C tenant 迁移代码改动，改成“runtime-side S2C construction binding”。

### MI-004：L1 README 仍把 S2C tenant 写成待通知/待施工

位置：

- `architecture/docs/L1/agent-bus/README.md`

问题：

- 已接受边界仍写 “S2C envelope 需要增加 tenantId；该变更……必须在独立迁移切片中施工”。
- 仍有 “待通知事项”。
- 后续工作仍写 “把 S2C tenant 迁移通知记录转成独立 delivery projection”。

影响：

- README 是 L1 入口，入口状态过期会污染后续协作。

建议：

- 改为 “S2C envelope 已增加 tenantId；runtime-side construction binding 仍待后续波次补齐”。
- 将 “待通知事项” 改为 “Stage 2 后续同步事项”。
- 后续工作改为 “补齐 runtime-side S2C construction binding / schema validation integration”。

### MI-005：schema version / compatibility 需要显式裁决

位置：

- `docs/contracts/s2c-callback.v1.yaml`

问题：

- Stage 2 给 `s2c-callback/v1` 增加 required field，这是 breaking contract change。
- 当前文档说明这是 “first deliberate schema change since v1”，但没有明确为什么仍保留 `schema: s2c-callback/v1`，以及兼容窗口如何处理。

影响：

- 如果 v1 已被外部消费，新增 required field 会破坏兼容性。
- 如果当前仍在内部实验期，可以保留 v1，但需要明确 “pre-GA / no external compatibility promise” 或建立 v1.1/v2 策略。

建议：

- 由 contract owner 裁决：
  - 方案 A：当前仍为 pre-GA contract，保留 `s2c-callback/v1`，并在 migration note 中写明无外部兼容承诺。
  - 方案 B：升级 schema 版本，例如 `s2c-callback/v2` 或增加 migration compatibility block。

### MI-006：Stage 1 follow-up 仍未完成

Stage 1 评审提出的两个小修补还没有在 Stage 2 中处理：

- SPI 纯度 harness 缺少 HTTP framework 覆盖。
- 模块依赖边界缺少 POM / module metadata 漂移检查。

建议：

- 不阻塞 Stage 2 接受。
- 放入 Stage 3 的 “前置整理切片”。

## 3. Stage 3 目标

Stage 3 建议聚焦：`Agent 注册与发现设计 / harness`

原因：

- 真 bus 的类 MQ 转发底座需要先知道“发给谁”。
- agent/service/capability registry 是 service-to-service routing 的前置能力。
- 如果不先定义 registry owner、tenant 隔离、health、version、route key 和一致性策略，后续 MQ-like forwarding 会变成无目标的 broker 设计。

Stage 3 不实现：

- 类 MQ 转发 runtime。
- broker binding。
- mailbox / backpressure / tick / DLQ / ordering runtime。
- Task lifecycle owner 变更。
- agent 业务定义仓库。

## 4. Stage 3 需要先裁决的问题

| 决策编号 | 问题 | 候选方案 | 建议 |
|---|---|---|---|
| HD3-001 | Registry 拥有什么 | 只拥有 runtime route index；拥有 agent 定义；拥有 Task 状态 | 只拥有 runtime route index |
| HD3-002 | 注册主体 | service instance；agent；capability；endpoint；topic | 至少覆盖 service instance + agent/capability + endpoint/route key |
| HD3-003 | 租户隔离 | registry key 包含 tenantId；tenantId 作为 metadata；依赖调用方上下文 | registry key 必须包含 tenantId |
| HD3-004 | 健康状态 | push heartbeat；pull health check；lease/TTL | 初版建议 lease/TTL + 可选 health metadata |
| HD3-005 | 版本兼容 | 不记录版本；记录 contract version；记录 capability version | 记录 contract version + capability version |
| HD3-006 | 路由输出 | endpoint；topic；service id；opaque route handle | 输出 route handle，内部包含 endpoint/topic/service id |
| HD3-007 | 状态持久化 | memory only；durable registry；外部 discovery 系统 | Stage 3 先定义接口和 harness，不选择 durable runtime |

## 5. Stage 3 开发切片

### Slice 0：Stage 2 文档同步修正

先修复本评审列出的状态漂移：

- `docs/contracts/s2c-callback.v1.yaml` mandatory field 注释。
- `architecture/docs/L1/agent-bus/README.md`。
- `architecture/docs/L1/agent-bus/development.md`。
- `architecture/docs/L1/agent-bus/features/README.md`。

### Slice 1：Registry / Discovery ICD 草案

建议新增：

- `docs/contracts/agent-discovery.v1.yaml`
或：
- `docs/architecture/l0/05-contracts/human-readable/ICD-agent-registry-discovery.md`

内容至少包括：

- registry key。
- route query。
- route result。
- tenant scope。
- capability version。
- contract version。
- endpoint / route handle。
- health / readiness。
- lease / expiry。
- failure modes。

### Slice 2：L1 agent-bus 视图更新

更新：

- `architecture/docs/L1/agent-bus/logical.md`
- `architecture/docs/L1/agent-bus/process.md`
- `architecture/docs/L1/agent-bus/physical.md`
- `architecture/docs/L1/agent-bus/features/README.md`
- `architecture/docs/L1/agent-bus/spi-appendix.md`

目标：

- 把 Agent Registry / Discovery 从“目标态一句话”推进到可评审的 4+1 元素与关系。
- 明确它仍是设计/harness 阶段，不是 runtime implementation。

### Slice 3：Registry harness 计划

Stage 3 只生成 design-level / contract-level harness，不实现 runtime registry。

建议 harness：

- registry entry 必须携带 tenantId。
- route query 必须携带 tenantId。
- route result 必须携带 route handle。
- route result 不得携带 Task execution state。
- health / lease 缺失时必须定义 failure mode。
- capability / contract version 不匹配时必须有显式结果。

### Slice 4：Stage 1 follow-up harness

补齐 Stage 1 遗留：

- `AgentBusSpiPurityTest` 增加 HTTP framework 包覆盖。
- 新增 POM / module metadata drift check。

## 6. Stage 3 验证方式

如果只改文档和 contract 草案：

- 不需要 Maven 测试，但必须做 source fact / link check。

如果补 Stage 1 follow-up harness：

```powershell
.\mvnw.cmd -pl agent-bus test
```

如果本地缺少 Java：

- 必须记录 `JAVA_HOME` 状态。
- 必须记录未运行的命令。
- 必须指定需要由哪个环境补跑。

## 7. Stage 3 禁止范围

Stage 3 不得：

- 实现 agent registry runtime。
- 实现 service discovery API runtime。
- 引入 broker 或 MQ 产品依赖。
- 修改 Task / Run 状态所有权。
- 让 `agent-bus` 拥有 agent 的业务定义。
- 让 registry 存储 Task execution state。
- 修改 `S2cCallbackEnvelope` 字段顺序或语义，除非 contract owner 批准兼容性修正。

## 8. Stage 4 预告

Stage 4 才建议进入：`类 MQ 转发底座设计 / harness`

Stage 4 的前置条件：

- Stage 3 已明确 route query / route result。
- Registry owner、tenant isolation、health、version、route key 已裁决。
- MQ-like forwarding 的 envelope 可以引用 registry route handle。

Stage 4 需要裁决：

- queue / topic / route key 模型。
- ack / retry。
- ordering / fairness。
- DLQ / replay。
- backpressure。
- broker 选择是否仍保持抽象。
