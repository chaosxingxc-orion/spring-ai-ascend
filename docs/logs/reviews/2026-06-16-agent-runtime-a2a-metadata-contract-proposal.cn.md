# Agent Runtime A2A Metadata Contract Proposal

> 日期：2026-06-16
> 范围：`agent-runtime` A2A 入口、样例工程、Memory/State/MCP 等可选中间件 metadata 约定
> 状态：Proposal

## 1. 背景

当前 `agent-runtime` 已经具备 A2A 北向入口、OpenJiuwen/AgentScope 执行框架适配、Agent State、Memory、远程 A2A tool 和 MCP proposal 等能力。随着样例增多，不同样例对 metadata 的使用方式开始漂移：

- A2A 请求天然存在两层 metadata：`params.metadata` 和 `params.message.metadata`，但样例没有稳定区分两者。
- 部分样例把 `userId`、`agentId`、`sessionId` 放进 `message.metadata`。
- 部分样例把 `tenantId`、`userId`、`agentId`、`sessionId` 等 runtime 上下文字段混放在不同位置。
- Memory 样例中存在 `agentStateKey`、长期记忆 scope 混用的倾向。
- 后续 MCP / Skills Hub / remote tool 都会需要自己的配置入口，如果不提前分层，metadata 会继续膨胀并相互覆盖。

A2A SDK 当前暴露的两个 metadata 字段如下：

| 层级 | Wire 路径 | SDK 类型 | 推荐用途 |
|---|---|---|---|
| 请求级 | `params.metadata` | `MessageSendParams.metadata()` | runtime identity、状态 key、memory scope、工具配置、可观测字段 |
| 消息级 | `params.message.metadata` | `Message.metadata()` | 业务消息自身扩展；runtime 不解释 |

本 proposal 的四层模型本质上不是新造一层 envelope，而是回归 A2A 协议已经给出的分层：runtime 上下文进入 `params.metadata`，业务消息扩展留在 `message.metadata`。

本 proposal 的核心修正是：

> A2A `message` 应该是内聚的业务消息本体，不应承载 runtime identity、路由信息、中间件 scope 或工具配置。

## 2. 目标与非目标

### 2.1 目标

1. 固化 A2A request 中 runtime metadata 的推荐位置。
2. 明确 `message.metadata` 不再作为 runtime metadata 载体。
3. 明确 `tenantId`、`userId`、`agentId`、`agentStateKey`、`memoryScope` 的语义和解析优先级。
4. 给 Memory / State / MCP 留下可扩展但不互相污染的 metadata namespace。
5. 给后续代码迁移提供兼容策略和测试矩阵。

### 2.2 非目标

1. 本轮迁移 Versatile parent-child A2A 样例的 runtime metadata 入口；Versatile adapter 自身的业务 payload 解析规则不在本轮重构范围内。
2. 本轮不设计多租户鉴权、隔离、RLS 或权限模型；`tenantId` 只是普通 request metadata 值，含义由业务方自行判断。
3. 本轮不改变 A2A 标准 message schema。
4. 本轮不要求所有历史调用方立即完成文档迁移；但代码落地后 runtime 不再从 `message.metadata` 读取 runtime metadata，旧调用方需要把字段迁到 `params.metadata`。
5. 本轮不定义 Skills Hub / Nacos Skill Registry 的 metadata 细节。

## 3. 当前问题

### 3.1 Message 与 Runtime Metadata 混用

部分样例把身份字段放进 `message.metadata`：

```json
{
  "message": {
    "role": "ROLE_USER",
    "parts": [
      {
        "text": "帮我查一下今天日期"
      }
    ],
    "contextId": "session-123",
    "metadata": {
      "userId": "test-user",
      "agentId": "openjiuwen-simple-agent",
      "sessionId": "session-123"
    }
  }
}
```

这个写法存在三个问题：

- `message` 变成“业务输入 + 运行时控制信息”的混合对象，破坏内聚。
- `sessionId` 与 A2A 标准字段 `message.contextId` 重复。
- 跨框架适配时，OpenJiuwen / AgentScope / MCP / Memory 无法判断哪些 metadata 是业务消息，哪些是 runtime 控制。

### 3.2 Runtime Identity 与 Variables 读取不一致

当前代码中，`A2aAgentExecutor` 构造 `RuntimeIdentity` 时读取 request-level metadata；但变量合并路径又会合并 message-level metadata。这会导致：

- adapter 或 handler 从 `context.getVariables()` 能看到 `userId`；
- 但 `context.getScope().userId()` 仍可能是默认值 `system`。

也就是说，`message.metadata` 的存在让“看起来传了”和“runtime identity 真正生效”之间产生了错觉。

### 3.3 Agent State 与 Long-term Memory Scope 混用

`agentStateKey` 与长期记忆分区不是一回事：

- `agentStateKey`：短期状态 / Checkpointer / workflow resume key。
- `memoryScope`：长期 Memory 分区，通常和租户、用户、业务域相关。

如果直接用 `agentStateKey` 作为长期 memory scope，多轮任务切换时容易出现：

- 新任务读不到用户长期偏好；
- 同一会话里的临时上下文被误写入长期记忆；
- 业务无法按租户、用户、业务域做独立治理。

## 4. Metadata 分层模型

建议把一次 A2A 调用拆成四层：

| 层级 | 承载内容 | 例子 | 是否进入 `message` |
|---|---|---|---|
| Transport Envelope | JSON-RPC / HTTP / SSE 传输壳 | `jsonrpc`、`id`、`method`、HTTP path、`Accept` | 否 |
| A2A Message Body | 业务消息本体和 A2A 标准上下文 | `role`、`parts`、`contextId`、`messageId` | 是 |
| Request Runtime Metadata | runtime identity、state key、memory scope、可观测字段 | `params.metadata.userId`、`agentId`、`agentStateKey`、`memoryScope` | 否 |
| Feature Namespace | MCP / Memory / future middleware 私有配置 | `params.metadata.mcp.serverId` | 否 |

### 4.1 Request Runtime Metadata

由 A2A request-level metadata 提供，即 `params.metadata` / `MessageSendParams.metadata()`。

| 字段 | 说明 | v1 约定 |
|---|---|---|
| `tenantId` | 普通透传值 | runtime 只读取、传播和参与默认 key 拼接，不赋予认证、隔离或 RLS 语义 |
| `userId` | 终端用户标识 | 用于用户级记忆、审计和默认 scope 组成 |
| `agentId` | 当前目标 / 逻辑 Agent | 用于 runtime identity、日志和默认 state key 组成 |
| `agentStateKey` | 短期状态 key | 显式传入时直接使用，不再二次拼接 |
| `memoryScope` | 长期记忆分区 | 显式传入时直接使用，不应从 `agentStateKey` 推导 |
| `mcp` | MCP feature namespace | 放置 MCP server / tool 选择信息 |

如果部署层需要从网关 claim、认证 token 或平台租户体系得到 `tenantId`，应在进入 A2A runtime 前把它归一化到 `params.metadata.tenantId`。`X-Tenant-Id`、cookie、Authorization 等传输凭证不作为本 proposal 的 runtime metadata 合约。

`stateKey` 不再作为兼容别名。需要短期状态连续性时，调用方必须使用 `agentStateKey`；缺失时 runtime 直接按默认公式派生。

### 4.2 A2A Message Body

由 A2A 标准字段承载用户输入和对话连续性。

| 字段 | 说明 | v1 约定 |
|---|---|---|
| `message.role` | 消息角色 | 保持 A2A 标准语义 |
| `message.parts` | 用户/Agent 消息内容 | 只放业务消息内容 |
| `message.contextId` | 会话上下文 ID | 作为 runtime `sessionId` 默认来源 |
| `message.messageId` | 消息 ID | 只用于消息去重/追踪 |
| `message.metadata` | A2A message 扩展字段 | 不承载 runtime metadata；runtime 不解释其中字段 |

### 4.3 Runtime Metadata

Runtime metadata 推荐放在 request-level metadata，例如 `params.metadata`：

```json
{
  "userId": "demo-user",
  "agentId": "hotel-agent",
  "agentStateKey": "hotel-session-20260618",
  "memoryScope": "demo:xuefanfan-4",
  "mcp": {
    "serverId": "localtime"
  }
}
```

## 5. 字段级契约

### 5.0 Canonical Schema

建议把 request-level metadata 视为如下结构。v1 为了兼容现有调用方，保留扁平顶层字段；后续如需更强隔离，可以再演进到 `runtime.*` 嵌套结构。

```json
{
  "userId": "xuefanfan-4",
  "agentId": "hotel-agent",
  "agentStateKey": "state:demo:hotel-agent:hotel-session-20260618",
  "memoryScope": "memory:demo:xuefanfan-4",
  "correlationId": "corr-20260618-0001",
  "mcp": {
    "serverId": "localtime",
    "preferredTools": ["get_current_date"]
  }
}
```

字段分类：

| 分类 | 字段 | 谁写入 | 谁读取 |
|---|---|---|---|
| Identity | `tenantId`、`userId`、`agentId` | caller / sample client / gateway normalized metadata | `A2aAgentExecutor`、`RuntimeIdentity`、日志、审计 |
| Continuity | `sessionId`、`taskId`、`agentStateKey` | A2A SDK / runtime / caller | Checkpointer、AgentState、WaitForUser resume |
| Memory | `memoryScope` | caller / MemoryProvider 默认生成 | MemoryProvider、Memory rail |
| Observability | `correlationId`、`traceId` | gateway / caller / runtime | MDC、trajectory、日志 |
| Feature | `mcp` | caller / runtime config | MCP provider / MCP tool installer |

### 5.1 保留字段

| 字段 | 类型 | 必填 | 推荐来源 | 默认/派生规则 | 主要消费方 | 说明 |
|---|---|---|---|---|---|---|
| `tenantId` | string | 否 | request metadata `tenantId` | request metadata > `default` | `RuntimeIdentity`、日志、Memory scope、State key | 普通透传值，不做认证、隔离或 RLS |
| `userId` | string | 否 | request metadata `userId` | request metadata > `system` | `RuntimeIdentity`、Memory scope、审计 | 终端用户身份 |
| `agentId` | string | 否 | request metadata `agentId` | request metadata > `handler.agentId()` | `RuntimeIdentity`、AgentCard、State key、日志 | 当前目标/逻辑 Agent |
| `sessionId` | string | 否 | `message.contextId` | `message.contextId` > `taskId` | `RuntimeIdentity`、State key、Checkpointer | 不应放入 metadata |
| `taskId` | string | 否 | A2A SDK / runtime | A2A task id；无则 runtime 创建 | A2A task lifecycle、日志、fallback | 不应由 caller 伪造 |
| `messageId` | string | 是 | `message.messageId` | caller 生成；只要求单次消息内唯一 | A2A message 去重/追踪 | 不参与 runtime identity |
| `agentStateKey` | string | 否 | request metadata `agentStateKey` | 显式值 > `state:{tenantId}:{agentId}:{sessionId}` | AgentState、Checkpointer、WaitForUser resume | 短期状态 key |
| `memoryScope` | string | 否 | request metadata `memoryScope` | 显式值 > `memory:{tenantId}:{userId}` | MemoryProvider、Memory rail | 长期记忆分区 |
| `correlationId` | string | 否 | request metadata / gateway | 显式值 > `message.messageId` > `taskId` | MDC、trajectory、日志 | 一次请求链路 ID |
| `traceId` | string | 否 | gateway / observability | 显式值 > `correlationId` | trajectory、日志、外部可观测系统 | 可跨多次请求延续 |
| `mcp` | object | 否 | request metadata `mcp` | 空 object | MCP Provider / Tool Installer | MCP feature namespace |

### 5.2 派生 ID 规则

派生 ID 需要满足三点：

1. **稳定**：同一租户、同一用户、同一上下文重复调用时得到相同 key。
2. **隔离**：不同租户、不同 Agent、不同用户默认不碰撞。
3. **可读**：日志和测试中可以直接看出 key 的组成。

推荐规则：

| 派生字段 | 公式 | 示例 |
|---|---|---|
| `sessionId` | `message.contextId` 非空时取 `message.contextId`，否则取 `taskId` | `hotel-session-20260618` |
| `agentStateKey` | request metadata 显式值优先；否则 `state:{tenantId}:{agentId}:{sessionId}` | `state:demo:hotel-agent:hotel-session-20260618` |
| `memoryScope` | request metadata 显式值优先；否则 `memory:{tenantId}:{userId}` | `memory:demo:xuefanfan-4` |
| `correlationId` | request metadata 显式值优先；否则 `message.messageId`；再否则 `taskId` | `m1` |
| `traceId` | request metadata 显式值优先；否则 `correlationId` | `m1` |

拼接规范：

- 分隔符统一使用 `:`。
- 空白字段先 `trim`，trim 后为空视为缺失。
- 字段值不做大小写改写，避免破坏外部系统 ID。
- 如果字段值本身包含 `:`，v1 可以保留原值；需要落盘到强约束存储时，由具体 store 做 escape 或 hash。
- 默认 key 只用于 runtime 内部；如果业务显式传入 `agentStateKey` 或 `memoryScope`，runtime 不再二次拼接。
- `stateKey` 不再参与派生；旧调用方如果只传 `stateKey`，runtime 会忽略它并按 `agentStateKey` 缺失处理。

示例：

```text
tenantId      = demo
userId        = xuefanfan-4
agentId       = hotel-agent
contextId     = hotel-session-20260618
sessionId     = hotel-session-20260618
agentStateKey = state:demo:hotel-agent:hotel-session-20260618
memoryScope   = memory:demo:xuefanfan-4
```

### 5.3 字段使用场景

| 字段 | 使用场景 | 不应用于 |
|---|---|---|
| `tenantId` | 日志、审计、默认 Memory/State key 组成；业务方可自行解释其租户含义 | 业务消息内容、认证凭证、runtime 内置隔离策略 |
| `userId` | 用户级记忆、用户级审计、限流维度 | 代替认证凭证 |
| `agentId` | AgentCard、handler fallback、State key 组成、日志 | 表示调用方 Agent 链路 |
| `sessionId` | 对话连续性、Checkpointer 默认维度 | 长期记忆分区 |
| `taskId` | A2A task 生命周期、Cancel/GetTask/ListTasks | 用户身份或 memory scope |
| `agentStateKey` | AgentState、Checkpointer、WaitForUser resume | 长期 Memory scope |
| `memoryScope` | 长期 Memory save/search | Checkpointer key |
| `correlationId` | 单请求链路追踪、日志串联 | 状态存储 key |
| `traceId` | 跨组件可观测链路 | 业务身份 |
| `mcp.serverId` | MCP server 选择、工具发现 | runtime 全局 server identity |

### 5.4 禁止放入 `message.metadata` 的字段

以下字段不应再出现在 `message.metadata` 中：

- `tenantId`
- `userId`
- `agentId`
- `sessionId`
- `agentStateKey`
- `memoryScope`
- `correlationId`
- `traceId`
- `serverId`
- `mcp`

代码落地后，runtime 不再从 `message.metadata` 读取 `tenantId`、`userId`、`agentId`、`agentStateKey`。这些字段放在 `message.metadata` 中只会作为消息扩展保留，不会进入 `RuntimeIdentity` 或 `AgentExecutionContext.variables`。

### 5.5 `agentStateKey` 与 `memoryScope`

两者必须拆开：

| 字段 | 生命周期 | 典型用途 | 推荐默认值 |
|---|---|---|---|
| `agentStateKey` | 短期 / 会话 / 任务流程 | Checkpointer、WaitForUser resume、AgentState | `state:{tenantId}:{agentId}:{sessionId}` |
| `memoryScope` | 长期 / 用户 / 业务域 | 用户偏好、历史事实、长期经验 | `memory:{tenantId}:{userId}` |

示例：

```json
{
  "userId": "xuefanfan-4",
  "agentStateKey": "state:demo:hotel-agent:hotel-session-20260618",
  "memoryScope": "memory:demo:xuefanfan-4"
}
```

## 6. 解析优先级

### 6.1 `tenantId`

优先级：

1. request-level metadata `tenantId`
2. `default`

说明：

- 第 1 层是唯一入口，对应 wire path `params.metadata.tenantId`。
- `message.metadata.tenantId` 会被 runtime 忽略。
- `tenantId` 在 runtime 中只是普通透传值；如果业务方需要认证、隔离、RLS 或权限判断，应在业务网关、业务 provider 或后续专门 proposal 中实现。
- v1 不推荐 `params.tenant`，也不把 `X-Tenant-Id` 定义为 metadata 合约入口。如果未来 A2A SDK 或项目 envelope 增加显式 tenant 字段，需要重新提交设计说明。

### 6.2 `userId`

优先级：

1. request-level metadata `userId`
2. `system`

### 6.3 `agentId`

优先级：

1. request-level metadata `agentId`
2. `handler.agentId()`

### 6.4 `sessionId`

优先级：

1. `message.contextId`
2. `taskId`

不读取 `metadata.sessionId`，也不读取 `message.metadata.sessionId`。需要稳定会话时应使用 A2A `message.contextId`，需要稳定状态 key 时应显式传 `params.metadata.agentStateKey`。

### 6.5 `agentStateKey`

优先级：

1. request-level metadata `agentStateKey`
2. `state:{tenantId}:{agentId}:{sessionId}`

`stateKey` 不是兼容别名，也不参与 fallback。这样可以避免同一语义同时存在两个入口，减少 State、Checkpointer、Memory 之间的歧义。

### 6.6 `memoryScope`

优先级：

1. request-level metadata `memoryScope`
2. provider 自行按 `memory:{tenantId}:{userId}` 生成

不从 `message.metadata` 读取 `memoryScope`，避免长期记忆 scope 被业务消息污染。

## 7. Request 示例

### 7.1 推荐写法

```json
{
  "jsonrpc": "2.0",
  "id": "1",
  "method": "SendMessage",
  "params": {
    "metadata": {
      "tenantId": "demo",
      "userId": "test-user",
      "agentId": "openjiuwen-simple-agent",
      "agentStateKey": "state:demo:openjiuwen-simple-agent:session-123",
      "memoryScope": "memory:demo:test-user",
      "correlationId": "corr-openjiuwen-simple-001"
    },
    "message": {
      "role": "ROLE_USER",
      "parts": [
        {
          "text": "帮我查一下今天日期"
        }
      ],
      "messageId": "m1",
      "contextId": "session-123"
    }
  }
}
```

该请求进入 runtime 后应解析为：

```text
tenantId      = demo
userId        = test-user
agentId       = openjiuwen-simple-agent
sessionId     = session-123
taskId        = <A2A runtime task id>
agentStateKey = state:demo:openjiuwen-simple-agent:session-123
memoryScope   = memory:demo:test-user
correlationId = corr-openjiuwen-simple-001
traceId       = corr-openjiuwen-simple-001
```

### 7.2 MCP namespace 示例

```json
{
  "jsonrpc": "2.0",
  "id": "1",
  "method": "SendMessage",
  "params": {
    "metadata": {
      "tenantId": "demo",
      "userId": "test-user",
      "agentId": "mcp-localtime-agent",
      "memoryScope": "memory:demo:test-user",
      "mcp": {
        "serverId": "localtime",
        "preferredTools": ["get_current_date"]
      }
    },
    "message": {
      "role": "ROLE_USER",
      "parts": [
        {
          "text": "今天是几号？"
        }
      ],
      "messageId": "m1",
      "contextId": "mcp-session-001"
    }
  }
}
```

### 7.3 不推荐写法

```json
{
  "jsonrpc": "2.0",
  "id": "1",
  "method": "SendMessage",
  "params": {
    "message": {
      "role": "ROLE_USER",
      "parts": [
        {
          "text": "帮我查一下今天日期"
        }
      ],
      "messageId": "m1",
      "contextId": "session-123",
      "metadata": {
        "userId": "test-user",
        "agentId": "openjiuwen-simple-agent",
        "sessionId": "session-123"
      }
    }
  }
}
```

问题：

- `message.metadata` 破坏消息本体内聚性。
- `sessionId` 与 `message.contextId` 重复。
- runtime identity 进入业务消息层，后续跨框架适配容易产生歧义。

## 8. 代码落地建议

### 8.1 新增统一常量

建议新增：

```java
public final class A2aRuntimeMetadataKeys {
    public static final String TENANT_ID = "tenantId";
    public static final String USER_ID = "userId";
    public static final String AGENT_ID = "agentId";
    public static final String AGENT_STATE_KEY = "agentStateKey";
    public static final String MEMORY_SCOPE = "memoryScope";
    public static final String CORRELATION_ID = "correlationId";
    public static final String TRACE_ID = "traceId";
    public static final String MCP = "mcp";

    private A2aRuntimeMetadataKeys() {
    }
}
```

### 8.2 调整 `A2aAgentExecutor`

建议拆出两个方法：

```java
private RuntimeIdentity resolveIdentity(RequestContext ctx);

private Map<String, Object> resolveRuntimeMetadata(RequestContext ctx);
```

行为：

1. `resolveIdentity` 只使用 request metadata 和默认值。
2. `resolveRuntimeMetadata` 不再无条件合并 `message.metadata`。
3. `RuntimeMessage` 仍可保留 message 自身 metadata 字段，但 runtime 不再把它当身份来源。

建议增加两个显式 helper，避免各模块重复拼接：

```java
static String defaultAgentStateKey(String tenantId, String agentId, String sessionId) {
    return "state:" + tenantId + ":" + agentId + ":" + sessionId;
}

static String defaultMemoryScope(String tenantId, String userId) {
    return "memory:" + tenantId + ":" + userId;
}
```

这两个 helper 只在字段缺失时使用；如果调用方显式传入 `agentStateKey` 或 `memoryScope`，runtime 必须尊重显式值。

### 8.3 `AgentExecutionContext`

`AgentExecutionContext` 可以继续保留：

- `scope`
- `variables`
- `agentStateKey`
- `metadata`

但建议在构造时保证：

- `scope.userId()` 来自统一 metadata 解析。
- `agentStateKey` 与 `memoryScope` 分离。
- `variables` 中可以包含业务 adapter 需要的参数，但不应再依赖 `message.metadata` 承载 runtime identity。

### 8.4 Memory Provider

Memory Provider 的推荐读取顺序：

1. 显式 `memoryScope`
2. `memory:{tenantId}:{userId}`

Memory Provider 不应直接把 `agentStateKey` 当作长期记忆分区，除非业务样例明确说明这是 demo 简化。

### 8.5 MCP Provider

MCP Provider 的推荐读取位置：

```json
{
  "mcp": {
    "serverId": "localtime"
  }
}
```

不建议读取顶层 `serverId`，避免与 remote runtime、gateway、adapter server 概念冲突。

## 9. 迁移计划

### Wave 1：文档和样例

- 更新 L2 A2A communication 文档，明确 `message.metadata` 不承载 runtime metadata。
- 更新 OpenJiuwen simple、Memory examples 的 README。
- 样例 curl 改为 request-level metadata + `message.contextId`。
- 更新 Versatile parent-child A2A README，确保 curl 示例把 runtime metadata 放在 `params.metadata`。

### Wave 2：代码收敛

`A2aAgentExecutor` 只从 request-level metadata 解析 runtime metadata：

1. runtime identity 从 `params.metadata` 读取；缺失时使用本提案定义的默认值。
2. `message.metadata` 只作为 message extension 保留，不参与 tenant、user、agent、state、memory、trace 等运行时派生。
3. `message.metadata.sessionId` 不再作为推荐来源；默认使用 `message.contextId`。
4. 移除 `stateKey` alias；短期状态只接受 `agentStateKey` 显式值，否则按默认公式派生。
5. Versatile parent sample client 使用 `MessageSendParams.metadata()` 传递 `userId` 和 `agentId`，`Message.metadata()` 只保留服务端事件扩展读取场景。

### Wave 3：清理旧样例文档

当样例和外部调用方迁移完成后：

- 移除 README 和测试里把 `message.metadata` 当 runtime metadata 入口的旧示例。
- 保留 `message.metadata` 作为纯 A2A message extension，但 runtime 不解释其中字段。

## 10. 测试计划

### 10.1 单元测试

| 场景 | 预期 |
|---|---|
| request metadata 提供 `tenantId` | `RuntimeIdentity.tenantId()` 等于该值 |
| request metadata 提供 `userId` | `RuntimeIdentity.userId()` 等于该值 |
| request metadata 提供 `agentId` | `RuntimeIdentity.agentId()` 等于该值 |
| request metadata 缺失，message metadata 提供 `userId` | runtime 忽略 message metadata，`RuntimeIdentity.userId()` 使用 `system` |
| request metadata 缺失，message metadata 提供 `tenantId` | runtime 忽略 message metadata，`RuntimeIdentity.tenantId()` 使用 `default` |
| `message.contextId` 存在 | `RuntimeIdentity.sessionId()` 使用 `contextId` |
| request metadata 提供 `agentStateKey` | `AgentExecutionContext.getAgentStateKey()` 使用显式值 |
| 未提供 `agentStateKey`，存在 tenant/agent/session | 默认生成 `state:{tenantId}:{agentId}:{sessionId}` |
| 只提供 `stateKey`，未提供 `agentStateKey` | runtime 忽略 `stateKey`，默认生成 `state:{tenantId}:{agentId}:{sessionId}` |
| 未提供 `memoryScope`，存在 tenant/user | 默认生成 `memory:{tenantId}:{userId}` |
| 仅提供 `memoryScope` | 不覆盖 `agentStateKey` |
| request metadata 提供 `correlationId` | MDC / trajectory 使用该值；不参与 state key |
| 未提供 `traceId` | 默认等于 `correlationId` |
| metadata 中存在 `mcp.serverId` | MCP provider 可读取 namespace |
| Versatile parent sample client 发送请求 | `userId` / `agentId` 出现在 `MessageSendParams.metadata()`，不出现在 `Message.metadata()` |

### 10.2 示例测试

需要更新并验证：

- `examples/agent-runtime-openjiuwen-simple`
- Memory InMemory example
- Memory Mem0 example
- MCP localtime example，待 MCP middleware 实现后补齐

### 10.3 回归测试

建议命令：

```bash
./mvnw -pl agent-runtime -am verify
./mvnw -f examples/agent-runtime-openjiuwen-simple/pom.xml verify
./mvnw -f examples/agent-runtime-middleware-memory-inmemory/pom.xml verify
```

## 11. Reviewer Checklist

- 是否仍有新增样例把 `userId` / `agentId` / `sessionId` 放进请求 `message.metadata`？
- 是否仍有新增调用方只传 `stateKey` 而不传 `agentStateKey`？
- 是否存在 `metadata.sessionId` 与 `message.contextId` 重复？
- 是否把 `agentStateKey` 当成长期 Memory scope？
- 默认 `agentStateKey` 是否按 `state:{tenantId}:{agentId}:{sessionId}` 生成？
- 默认 `memoryScope` 是否按 `memory:{tenantId}:{userId}` 生成？
- `correlationId` / `traceId` 是否只用于可观测，不参与状态和记忆分区？
- 是否把 MCP 的 `serverId` 放在顶层 metadata？
- 是否仍错误推荐了 `X-Tenant-Id` 或 `params.tenant` 作为 runtime metadata 入口？
- 是否对 message metadata ignored 打了可观测日志？

## 12. Open Questions

1. A2A Java SDK 对 `MessageSendParams.metadata` 的序列化支持是否在所有当前样例路径中稳定可用？如果 SDK 限制 request-level metadata，需要在 runtime access 层增加稳定 envelope 适配。
2. 是否需要把 v1 扁平 metadata 进一步升级为 `runtime.userId` / `runtime.agentStateKey` 这类嵌套结构？当前建议先不做，避免破坏现有调用方。
3. `agentId` 在多 Agent parent/child 场景中到底表示“目标 Agent”还是“调用方 Agent”？建议 v1 保持为目标/当前 handler 标识；调用方链路放入 trajectory 或 tool invocation metadata。

## 13. 结论

本 proposal 建议把 A2A message、transport context、runtime metadata、feature namespace 明确分层。最重要的边界是：

> `message` 只表达业务消息；runtime identity、路由、中间件 scope 和工具配置必须从 `message.metadata` 迁出。

这样可以减少 OpenJiuwen、AgentScope、Memory、MCP、Versatile 等能力之间的 metadata 冲突，也为后续业务自定义权限、工具审计和长期记忆治理留下清晰边界。
