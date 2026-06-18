# Memory 服务

agent-runtime 提供可注入、可替换的记忆服务，将跨会话记忆能力从 Agent 框架中解耦。实现 `MemoryProvider` SPI 即可为 Agent 添加记忆，切换后端不影响 Agent 代码。

## 1. 概述

```java
// 最小示例：为 OpenJiuwen Agent 注入记忆
@Override
protected List<AgentRail> openJiuwenRails(AgentExecutionContext ctx) {
    return List.of(memoryRuntimeRail(ctx, memoryProvider));
}
```

## 2. 快速开始

### Step 1 — 实现 MemoryProvider

```java
@Component
public class MyMemoryProvider implements MemoryProvider {
    @Override
    public List<MemoryHit> search(String userId, String sessionId, String query, int limit) {
        // 从记忆后端检索
        return results;
    }

    @Override
    public void save(String userId, String sessionId, List<MemoryRecord> records) {
        // 持久化对话记录
    }
}
```

### Step 2 — 注入到 OpenJiuwen Agent

```java
public class MyHandler extends OpenJiuwenAgentRuntimeHandler {
    private final MemoryProvider memoryProvider;
    public MyHandler(MemoryProvider memoryProvider) {
        super("my-agent");
        this.memoryProvider = memoryProvider;
    }

    @Override
    protected List<AgentRail> openJiuwenRails(AgentExecutionContext ctx) {
        return List.of(memoryRuntimeRail(ctx, memoryProvider));
    }
}
```

前置条件：`MemoryProvider` Bean 已注册。预期结果：每次调用前检索历史记忆并注入 system prompt，调用后保存对话。

## 3. 工作原理

```
Agent 执行前
  │
  ├─ MemoryRuntimeRail.beforeInvoke()
  │     ├─ memoryProvider.search(userId, sessionId, query, limit)
  │     └─ 检索结果注入到 system prompt
  │
  ▼ Agent 执行（LLM 调用 + 工具调用）
  │
  ├─ MemoryRuntimeRail.afterInvoke()
  │     ├─ 提取本轮对话的 BaseMessage
  │     └─ memoryProvider.save(userId, sessionId, records)
  │
  ▼ 返回结果
```

记忆隔离：按 `userId` / `sessionId` 隔离。不同用户和会话的记忆互不干扰。

## 4. 核心接口

```java
public interface MemoryProvider {
    /** 初始化（默认空实现）。 */
    default void init() {}

    /** 按 userId/sessionId 隔离检索记忆。 */
    List<MemoryHit> search(String userId, String sessionId, String query, int limit);

    /** 保存记忆记录（默认空实现）。 */
    default void save(String userId, String sessionId, List<MemoryRecord> records) {}
}
```

| 方法 | 调用时机 | 用途 |
|------|---------|------|
| `init()` | Agent 执行前 | 初始化/校验记忆作用域 |
| `search()` | Agent 调用前 | 查找相关历史上下文，注入 prompt |
| `save()` | Agent 调用后 | 持久化本轮对话 |

## 5. 能力详述

### ReActAgent 记忆注入（MemoryRuntimeRail）

```java
@Override protected List<AgentRail> openJiuwenRails(AgentExecutionContext ctx) {
    return List.of(memoryRuntimeRail(ctx, memoryProvider));
}
```

`MemoryRuntimeRail` 在 `beforeInvoke` 时检索记忆并注入 system message，`afterInvoke` 时从 model context 提取消息并保存。

### Harness 兼容记忆（ExternalMemoryRail）

```java
@Override protected List<AgentRail> openJiuwenRails(AgentExecutionContext ctx) {
    return List.of(openJiuwenExternalMemoryRail(ctx, memoryProvider));
}
```

通过 `OpenJiuwenExternalMemoryProviderAdapter` 将 runtime `MemoryProvider` 桥接到 OpenJiuwen 原生的 `ExternalMemoryRail`。

## 6. 完整示例

```java
// 内嵌 InMemory 实现
@Component
class InMemoryMemoryProvider implements MemoryProvider {
    private final ConcurrentMap<String, List<MemoryRecord>> store = new ConcurrentHashMap<>();

    @Override
    public List<MemoryHit> search(String userId, String sessionId, String query, int limit) {
        var key = userId + ":" + sessionId;
        return store.getOrDefault(key, List.of()).stream()
            .filter(r -> r.content().toLowerCase().contains(query.toLowerCase()))
            .limit(limit)
            .map(r -> new MemoryHit(r.id(), r.content(), 1.0, r.metadata()))
            .toList();
    }

    @Override
    public void save(String userId, String sessionId, List<MemoryRecord> records) {
        store.computeIfAbsent(userId + ":" + sessionId, k -> new CopyOnWriteArrayList<>())
            .addAll(records);
    }
}
```

## 7. 限制

- 记忆仅在 ReAct 轮次开始前一次性注入，不支持中途检索
- Agent 无法在对话过程中主动调用记忆读写（无记忆工具）
- 仅 OpenJiuwen 已接入，AgentScope 尚未支持

## 8. 相关资源

- 设计文档：`architecture/docs/L2/agent-runtime/middleware-services-design.md` §2-§4
- [OpenJiuwen Adapter](openjiuwen-adapter.md)
- [State 持久化](state-persistence.md)
- Example：`examples/agent-runtime-middleware-memory-inmemory/`
