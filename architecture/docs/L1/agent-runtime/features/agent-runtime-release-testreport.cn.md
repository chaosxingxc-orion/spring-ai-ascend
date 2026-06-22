---
level: L1
view: test-report
module: agent-runtime
status: sit
updated: 2026-06-21
source: agent-runtime-release-features.cn.md
test-basis: agent-runtime-SIT-plan.xlsx
---

# agent-runtime v0.1.0 — SIT 测试报告

> **锚点**：[agent-runtime-release-features.cn.md](agent-runtime-release-features.cn.md)（特性清单）
> **测试依据**：[agent-runtime-SIT-plan.xlsx](agent-runtime-SIT-plan.xlsx)（正常功能用例 + 异常场景用例）
> **Excel 版本**：[agent-runtime-SIT-report.xlsx](agent-runtime-SIT-report.xlsx)（6 个工作表，含状态色标）
> **报告日期**：2026-06-21
> **判定口径**：已覆盖+备注空=PASS；已覆盖+备注有描述=可能阻塞/不可发布；待测+不支持=不可发布；待测+无需关注/节后覆盖=不影响发布；不交付=不可发布

---

## 一、发布结论（总览）

| 类别 | 结论 |
|---|---|
| ✅ **已覆盖且通过** | 流式通讯、异步任务（Task CRUD）、A2A 协议端点（Card/Stream/Get/Cancel/降级）、远程 Agent 编排、记忆/状态中间件、OpenJiuwen 适配器（除轨迹 Rail）、开发者示例与文档 |
| 🔴 **阻塞发布，需修复后才能发** | **1.3 Versatile REST 代理适配器** — 用例 B-11 存在结果提取/事件类型逻辑 BUG，备注明确"建议待完全修复后再发布" |
| 🔴 **本迭代不交付（从范围剔除）** | **3.1 同步模型**（改为不支持）、**5 轨迹可观测性**整体（含 1.1 轨迹追踪 Rail） |
| ⚠️ **已知能力缺口**（核心可用、子项缺失，需评估对外承诺） | 1.2 AgentScope 记忆未支持；3.3 异步 Push 未支持；2.2 文档称 "SQLite" 实为 Redis（实现与文档不符）、Redis 分布式 Checkpoint 未预置 |
| ⏸️ **待测但不影响发布评估** | 7 运维就绪（节后覆盖）、4.1 ListTasks/SubscribeToTask（节后测试）、C-08 连续快速发送（协议层不支持）、C-10 流式中断恢复（节后覆盖）、C-04/C-05（与机制能力无关） |

**总体结论**：**存在功能性问题，暂不具备正式发布条件；相关问题需与开发对齐，在 6/30 前完成修复。** 核心链路（流式 + 异步 + 远程编排 + 中间件）已覆盖且通过；阻塞项为 **Versatile 适配器（1.3）存在 BUG**，另 **同步模型（3.1）/ 轨迹可观测性（5）** 两个特性本迭代明确不交付，需在发布说明中剔除。

---

## 二、✅ 已覆盖且通过的特性

| 特性（清单锚点） | 测试证据 | 说明 |
|---|---|---|
| **1.1 OpenJiuwen 适配器**（进程内调用 / 远程工具中断 Rail / 记忆注入 Rail / Checkpoint） | B-01、B-02、B-03、B-04 | InMemory 多轮连续性、跨请求状态恢复、Redis 多轮、InMemory↔Redis 仅改 YAML 切换均通过 |
| **1.4 Adapter 抽象层**（AgentRuntimeHandler SPI） | 开发者 example agent 覆盖 | SPI 对外语义一致 |
| **2.1 记忆服务**（MemoryProvider SPI） | B-05、B-06 | 不同 sessionId 记忆隔离、同 userId 不同 session 并发不串数据，均通过 |
| **3.2 流式（Streaming）** | A-04、A-09、A-10、A-11、C-01 | SSE 时序正确（SUBMITTED→WORKING→COMPLETED）、无增量直接完成、多会话并发独立，全通过 |
| **4.1 A2A Methods — Agent Card 端点** | A-01、A-02 | Card 发现、字段完整性（name/description/url/skills/supportedInterfaces）通过 |
| **4.1 A2A Methods — SendStreamingMessage / GetTask / CancelTask** | A-04、A-05、A-06 | 流式消息、已完成任务查询、流式中取消（状态→CANCELED）通过 |
| **4.1 远程 Agent 优雅降级** | B-09 | 停掉 trip agent 后 mainplan 收到错误并优雅回复，通过 |
| **4.2 Agent Card YAML 配置** | A-12 | **历史有 issue，但备注确认"issue 已修复完成、手动验证 OK"** → 视为通过 |
| **6 远程 Agent 编排**（南向） | B-07、B-08、B-10、B-09 | mainplan→trip 远程调用、工具名参数化（中划线→下划线）、三级链路（mainplan→trip→hotel）、故障降级，全通过 |
| **8 开发者体验**（Spring Boot 内嵌 / 声明式配置 / 11 个 E2E 示例 + 中文指南） | 开发提供 | 开箱即用 |

---

## 三、🔴 当前存在问题 / 不可发布的特性

### 3.1 — Versatile REST 代理适配器（**阻塞，最高优先级修复**）
- **证据**：用例 **B-11**（一句话复杂转账），状态"已覆盖"但备注含明确缺陷描述。
- **备注原文要点**：
  > "当前测试一句话转账能走完，但是逻辑上有些 BUG…… **建议这个待完全修复后再发布**。"
- **具体缺陷**：
  1. 结果提取规则不完善 —— 仅按 event 类型白名单 / `node_type=QA` 提取；一个 SSE 流中存在多个 QA，当前实现无法按 `node_type=QA + node_name=XX` 精确提取，临时用 QA 判断会导致工作流执行不到最终步骤；
  2. 应答 artifact 缺少 event 类型；
  3. 看不到 `event=end` 事件；
  4. （待讨论）metadata 由被调用的 versatile_call 决定、而非调用方决定，通用性不足。
- **清单标注**：1.3 标注"【已覆盖，有issue，不可发布】"，与测试结论一致。

### 3.2 — 同步（Blocking）通讯模型（**本迭代不交付，改为不支持**）
- **证据**：A-03、A-07、C-02、A-08 **四例均"待测 / 不支持"** —— 同步简单问答、同步 E2E、同步异常返回均无法测试。
- **清单标注**：3.1 标注"【有issue，要改为不支持，不可发布】"。
- **影响**：产品对外不提供同步模式，仅保留**流式 + 异步**。需在发布说明中明示。

### 3.3 — 轨迹可观测性（Trajectory）（**本迭代整体不交付**）
- **证据**：第 5 章无对应通过的 SIT 用例。
- **清单标注**：第 5 章标注"【有issue跟踪，本迭代不交付，不可发布】"，其中 **1.1 轨迹追踪 Rail** 单独标注"【有issue，未闭环，不可发布】"（与第 5 章一致）。
- **影响**：对外不提供北向轨迹能力（含 OTLP 导出、TTFT、REASONING、采样、大载荷外置等子项均 ⬜）。

---

## 四、⚠️ 已知能力缺口（核心可用，但子项缺失 — 建议评估对外承诺）

| 特性 | 已通过部分 | 缺口 | 证据 |
|---|---|---|---|
| **1.2 AgentScope 适配器** | A2A 调用 AgentScope Agent、三种运行模式、错误码映射 | **记忆未支持**（清单 2.1 亦注明"仅 OpenJiuwen 已接入，AgentScope 尚未支持"） | 清单标注"已覆盖，有issue" |
| **3.3 异步（Async）** | GetTask / CancelTask / ListTasks / 完整生命周期 | **Push Notification / Webhook 核心功能不支持** | 清单标注"已覆盖 Task 获取和记录相关 API，但 Push 核心功能不支持" |
| **2.2 状态持久化** | InMemory、Redis Checkpoint 多轮连续与切换（B-03/B-04） | ① 文档称 "SQLite" 实际为 Redis（**实现与文档不符**）；② Redis 分布式 Checkpoint 预置适配 ⬜；③ AgentScope Checkpoint ⬜ | 清单 1.1、2.2 均注"SQLite 实际为 redis" |

---

## 五、⏸️ 待测但不影响发布评估的特性

| 项 | 备注 | 判定 |
|---|---|---|
| **7 运维就绪**（生命周期 / 健康检查 / MDC 日志 / 嵌入式部署） | "端午节后覆盖" | 不影响本次发布评估 |
| **4.1 ListTasks / SubscribeToTask** | "端午节后测试" | 不影响发布评估 |
| C-10 流式中断恢复（TCP RESET） | "依赖实现 TCP RESET，实现复杂度高，端午节后覆盖" | 不影响发布评估 |
| C-08 同 session 连续快速发送 | "协议层面支持不了，JSON-RPC+SSE 只支持一发多收……无需关注" | 协议限制，不影响发布 |
| C-04 非出差意图处理 / C-05 需求描述客观性 | "跟机制能力无关" | 与 runtime 机制无关，不影响发布 |

---

## 六、特性清单状态总表（按清单 8 章锚点）

| # | 特性 | 状态 | 关键证据 |
|---|---|---|---|
| 1.1 | OpenJiuwen 适配器 | ⚠️ 部分 | 调用/中断Rail/记忆Rail/Checkpoint 通过（B-01~04）；**轨迹追踪 Rail 未闭环、不交付** |
| 1.2 | AgentScope 适配器 | ⚠️ 部分 | A2A 调用通过；**记忆未支持** |
| 1.3 | Versatile REST 代理 | 🔴 阻塞 | **B-11 缺陷，修复后再发布** |
| 1.4 | Adapter 抽象层（SPI） | ✅ 通过 | example agent 覆盖 |
| 2.1 | 记忆服务 | ✅ 通过 | B-05、B-06 |
| 2.2 | 状态持久化 | ⚠️ 部分 | InMemory/Redis 通过（B-03/04）；**"SQLite=Redis"文档不符、分布式 ⬜** |
| 3.1 | 同步（Blocking） | 🔴 不交付 | A-03/A-07/C-02/A-08 全"不支持" |
| 3.2 | 流式（Streaming） | ✅ 通过 | A-04/A-09/A-10/A-11/C-01 |
| 3.3 | 异步（Async） | ⚠️ 部分 | Task CRUD 通过（A-05/A-06）；**Push 不支持** |
| 3.4 | gRPC 传输 | ⬜ 未实现 | 计划项，不在本迭代范围 |
| 4.1 | A2A Methods | ⚠️ 部分 | Card/Stream/Get/Cancel/降级 通过；**SendMessage 有 issue；List/Subscribe 节后测** |
| 4.2 | Agent Card YAML 配置 | ✅ 通过 | A-12（issue 已修复验证） |
| 5 | 轨迹可观测性 | 🔴 不交付 | 本迭代整体不交付 |
| 6 | 远程 Agent 编排 | ✅ 通过 | B-07/B-08/B-10/B-09 |
| 7 | 运维就绪 | ⏸️ 待测 | 节后覆盖，不影响发布 |
| 8 | 开发者体验 | ✅ 通过 | example + 文档已提供 |

---

## 七、建议的下一步（按优先级）

1. **修复 1.3 Versatile 适配器**（B-11 三项缺陷）。
2. **发布说明需明示剔除范围**：3.1 同步、5 轨迹可观测性（含 1.1 轨迹 Rail）本迭代不交付。
3. **对缺口项做对外承诺评估**：1.2 AgentScope 记忆、3.3 异步 Push、2.2 文档"SQLite=Redis"订正与 Redis 分布式 Checkpoint。
4. **节后补测**：第 7 章运维就绪、4.1 ListTasks/SubscribeToTask、C-10 流式中断恢复。
