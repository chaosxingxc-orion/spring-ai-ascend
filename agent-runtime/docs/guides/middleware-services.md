# 中间件服务

agent-runtime 提供可注入、可替换的中间件服务，将通用基础设施能力从 Agent 框架中
解耦出来。同一套中间件实现可用于不同 Agent 框架（openJiuwen、AgentScope、自定义适配器）。

## 设计原则

- **接口定义** — 每个中间件服务都是 `engine.spi` 中定义的窄接口。不依赖任何特定 Agent 框架。
- **启动时注入** — 用户或平台以 Spring Bean 形式注入具体实现。Runtime 通过执行上下文传递给 Agent 适配器。
- **适配器按需使用** — 适配器仅使用需要的中间件，不强制所有适配器使用全部中间件。
- **存储无关** — 切换存储后端（InMemory → Redis → 数据库）不影响 Agent 代码，只需替换中间件实现。
- **会话分离** — Runtime session（外部 A2A 对话生命周期）与 Agent session（框架内部 Agent 执行状态）
  概念分离。中间件作用于 Agent session，不污染 Runtime session。

## 架构

```
┌──────────────────────────────────────┐
│ Agent 适配器（OpenJiuwen/AgentScope）│
│   └─ 通过 SPI 接口消费中间件         │
├──────────────────────────────────────┤
│ 中间件 SPI（engine.spi）             │
│   ├─ MemoryProvider                  │
│   └─ Checkpointer（框架原生机制）     │
├──────────────────────────────────────┤
│ 实现（用户注入的 Bean）               │
│   ├─ InMemoryMemoryProvider          │
│   ├─ Redis 实现                      │
│   ├─ 向量数据库实现                   │
│   └─ InMemoryCheckpointer / Redis... │
└──────────────────────────────────────┘
```

## MemoryProvider SPI

Runtime 的记忆抽象，覆盖短期会话记忆和长期记忆检索。

### 接口

```java
package com.huawei.ascend.runtime.engine.spi;

public interface MemoryProvider {
    /** 为此执行上下文初始化记忆作用域 */
    void init(AgentExecutionContext context);

    /** 搜索与查询相关的记忆，最多返回 limit 条 */
    List<MemoryHit> search(AgentExecutionContext context, String query, int limit);

    /** 持久化对话记录 */
    void save(AgentExecutionContext context, List<MemoryRecord> records);

    record MemoryRecord(String id, String role, String content, Map<String, Object> metadata) {}
    record MemoryHit(String id, String content, Double score, Map<String, Object> metadata) {}
}
```

### 契约

| 方法 | 调用时机 | 用途 |
|---|---|---|
| `init()` | 每次 Agent 执行前 | 初始化/校验此会话的记忆作用域 |
| `search()` | 每次 Agent 调用前（可选） | 查找相关历史上下文，注入 prompt |
| `save()` | 每次 Agent 调用后 | 持久化本轮对话 |

### 集成模式

#### openJiuwen ReActAgent（MemoryRuntimeRail）

```java
@Override
protected List<AgentRail> openJiuwenRails(AgentExecutionContext context) {
    return List.of(memoryRuntimeRail(context, memoryProvider));
}
```

`MemoryRuntimeRail` 是 openJiuwen 内部的桥接：
- `beforeInvoke()` → 调用 `memoryProvider.init()` + `memoryProvider.search()` → 将结果注入 system message
- `afterInvoke()` → 从 model context 提取消息 → 调用 `memoryProvider.save()`

#### openJiuwen DeepAgent（ExternalMemoryRail）

```java
@Override
protected List<AgentRail> openJiuwenRails(AgentExecutionContext context) {
    return List.of(openJiuwenExternalMemoryRail(context, memoryProvider));
}
```

使用 openJiuwen 原生的 `ExternalMemoryRail`，通过
`OpenJiuwenExternalMemoryProviderAdapter` 将 runtime 的 `MemoryProvider`
桥接到 openJiuwen 的外部记忆 API。

### 内存实现（示例）

```java
final class InMemoryMemoryProvider implements MemoryProvider {
    private final ConcurrentMap<String, List<MemoryRecord>> store = new ConcurrentHashMap<>();

    @Override
    public void init(AgentExecutionContext context) {
        store.computeIfAbsent(context.getAgentStateKey(), k -> new CopyOnWriteArrayList<>());
    }

    @Override
    public List<MemoryHit> search(AgentExecutionContext context, String query, int limit) {
        // 简单文本重叠搜索——生产环境替换为向量搜索
        return store.getOrDefault(context.getAgentStateKey(), List.of()).stream()
            .filter(r -> r.content().toLowerCase().contains(query.toLowerCase()))
            .limit(limit)
            .map(r -> new MemoryHit(r.id(), r.content(), 1.0, r.metadata()))
            .toList();
    }

    @Override
    public void save(AgentExecutionContext context, List<MemoryRecord> records) {
        store.get(context.getAgentStateKey()).addAll(records);
    }
}
```

## Agent State 持久化（Checkpointer）

Agent 状态持久化由 Agent 框架的原生 Checkpointer 机制处理，而非 runtime SPI。
Runtime 提供执行上下文（conversation_id 用于作用域隔离），但将保存/恢复
委托给框架。

### openJiuwen Checkpointer

```java
@Bean
Checkpointer openJiuwenCheckpointer() {
    // 内存模式（仅限开发/测试）
    Checkpointer cp = new InMemoryCheckpointer();
    CheckpointerFactory.setDefaultCheckpointer(cp);
    return cp;
}

@Bean
Checkpointer redisCheckpointer(@Value("${sample.openjiuwen.redis-url}") String redisUrl) {
    // Redis 模式（生产环境）
    Checkpointer cp = new RedisCheckpointer.Provider()
        .create(Map.of("connection", Map.of("url", redisUrl)));
    CheckpointerFactory.setDefaultCheckpointer(cp);
    return cp;
}
```

### 作用域

Runtime 将 `AgentExecutionContext.getAgentStateKey()` 派生的稳定
`conversation_id` 传递给 Checkpointer，确保每个 A2A 对话有隔离的 Agent 状态。

### 配置

```yaml
sample:
  openjiuwen:
    checkpointer: in-memory              # in-memory | redis
    redis-url: redis://localhost:6379    # checkpointer=redis 时必填
```

## 关注点分离

| 关注点 | 所有者 | 作用域键 |
|---|---|---|
| A2A 对话生命周期 | Runtime session（agent-runtime） | `contextId`（A2A） |
| Agent 执行状态 | Agent session（框架） | `conversation_id`（agent state key） |
| 用户/租户记忆隔离 | MemoryProvider（中间件） | `agentStateKey` |
| 跨会话长期记忆 | MemoryProvider（中间件） | `userId`（来自执行作用域） |

## 相关

- [openJiuwen 适配器](openjiuwen-adapter.md) — 实际操作中的 memory rail 集成
- [Handler SPI](handler-spi.md) — AgentExecutionContext 和作用域模型
- 示例：`examples/agent-runtime-openjiuwen-simple/`（最简，无记忆）
- 示例：`examples/agent-runtime-a2a-openjiuwen-e2e/`（含 InMemoryMemoryProvider）
