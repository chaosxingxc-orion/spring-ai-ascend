# 中间件解耦 — Memory & State 设计文档

> 目标模块：`agent-runtime/src/main/java/com/huawei/ascend/runtime/engine/spi/MemoryProvider.java`
> 最后更新：2026-06-14

---

## 1. 概述

### 1.1 特性定位

Agent 执行过程中依赖的通用基础设施能力（记忆、状态持久化）从 Agent 框架中解耦，以可注入、可替换的中间件服务形式由 runtime 统一提供。

- **解决的问题**：不同 Agent 框架有各自的记忆和持久化机制。runtime 通过 SPI 抽象统一这些能力，使同一套中间件实现可用于不同框架，切换后端不影响 Agent 代码。
- **适用场景**：需要为 Agent 添加跨会话记忆、需要持久化 Agent 执行状态以支持中断恢复。如果 Agent 无状态或框架自带完整持久化方案，不需要此特性。

### 1.2 核心设计原则

1. **SPI 隔离** — 中间件接口定义在 `engine.spi`，不依赖任何特定 Agent 框架
2. **按需注入** — Agent Adapter 按需使用中间件能力，不强制所有 Adapter 都使用
3. **后端可替换** — 切换存储后端只需替换 SPI 实现，不影响 Agent 代码
4. **作用域分离** — Runtime session（外部连续会话管理）与 Agent session（框架级执行会话）概念分离

### 1.3 子特性全景

| 子特性 | 职责 | 关键抽象 | 状态 |
|--------|------|---------|------|
| Memory 服务 | 跨会话记忆检索与保存 | `MemoryProvider` SPI | ✅ |
| OpenJiuwen 记忆集成 | 自动注入记忆到 prompt + 写回对话记录 | `MemoryRuntimeRail` | ✅ |
| State 持久化 | Agent 执行状态 checkpoint | `CheckpointerFactory` (OpenJiuwen) | ⚠️ 仅 OpenJiuwen |

---

## 2. 功能规格

### 2.1 能力清单

| 能力 | 状态 | 说明 |
|------|------|------|
| MemoryProvider SPI | ✅ | `search(userId, sessionId, query, limit)` + `save(userId, sessionId, records)` |
| OpenJiuwen ReActAgent 记忆注入 | ✅ | `MemoryRuntimeRail`：beforeInvoke 检索 → 注入 system prompt，afterInvoke 保存 |
| OpenJiuwen harness 记忆适配 | ✅ | `OpenJiuwenExternalMemoryProviderAdapter`：适配 runtime MemoryProvider 为 OpenJiuwen 原生接口 |
| MemoryMessageAdapter | ✅ | BaseMessage ↔ MemoryRecord 角色映射转换 |
| OpenJiuwen Checkpoint (InMemory) | ✅ | `OpenJiuwenCheckpointerConfigurer.setInMemoryDefault()` |
| OpenJiuwen Checkpoint (SQLite) | ✅ | 自定义 Checkpointer 注入 |
| 中途检索记忆 | ⬜ | 当前仅在轮次开始前一次性注入，不支持 Agent 推理中途按需检索 |
| 记忆工具 | ⬜ | Agent 无法在对话过程中主动调用记忆读写 |
| AgentScope 记忆适配 | ⬜ | 仅 OpenJiuwen 已接入 |
| Redis 分布式 Checkpoint 预置适配 | ⬜ | 需自行实现 `Checkpointer` |
| AgentScope Checkpoint 适配 | ⬜ | 未适配 |

### 2.2 显式排除

| 排除项 | 原因 | 替代 |
|--------|------|------|
| 向量数据库后端 | MemoryProvider 的实现层，不属于 runtime SPI 职责 | 外部模块实现 MemoryProvider 接口 |
| 跨框架通用 Checkpoint SPI | 各框架的 checkpoint 机制差异大，定义通用 SPI 成本高 | 各框架独立适配 |

### 2.3 接口契约

#### MemoryProvider SPI

```java
/** 框架无关的记忆服务。切换后端只需替换实现。 */
public interface MemoryProvider {
    /** 初始化（默认空实现）。 */
    default void init() {}

    /**
     * 按 userId/sessionId 隔离检索记忆。
     * @param userId 用户标识
     * @param sessionId 会话标识
     * @param query 检索查询
     * @param limit 返回数量上限
     * @return 记忆命中列表
     */
    List<MemoryHit> search(String userId, String sessionId, String query, int limit);

    /**
     * 保存记忆记录（默认空实现）。
     * @param records 待保存的记忆记录
     */
    default void save(String userId, String sessionId, List<MemoryRecord> records) {}
}
```

#### 行为承诺

- **必须**：`search()` 按 `userId`/`sessionId` 隔离检索
- **允许**：`save()` 异步执行，不阻塞 Agent 返回
- **允许**：`init()` 中建立连接、加载模型等重操作

---

## 3. 模块结构

### 3.1 包结构

```
engine/spi/
└── MemoryProvider.java                              # 记忆服务 SPI

engine/openjiuwen/
├── MemoryRuntimeRail.java                           # ReActAgent 记忆注入 Rail
├── OpenJiuwenExternalMemoryProviderAdapter.java     # runtime MemoryProvider → OpenJiuwen 原生接口
├── OpenJiuwenMemoryMessageAdapter.java              # BaseMessage ↔ MemoryRecord 转换
└── OpenJiuwenCheckpointerConfigurer.java            # CheckpointerFactory 全局配置
```

### 3.2 核心类静态关系

```
«interface»
MemoryProvider
      ↑
      └─── implements ──── <外部实现类>

OpenJiuwenExternalMemoryProviderAdapter
      ↑
      └─── wraps ──── MemoryProvider
      └─── implements ──── com.openjiuwen...external.MemoryProvider

MemoryRuntimeRail
      ↑
      └─── uses ──── MemoryProvider + OpenJiuwenMemoryMessageAdapter
      └─── extends ──── AgentRail
```

---

## 4. 核心设计

### 4.1 Memory 服务

#### 4.1.1 记忆注入流程（ReActAgent）

```
Agent 执行前
  │
  ├─ MemoryRuntimeRail.beforeInvoke()
  │     ├─ MemoryProvider.search(userId, sessionId, query, limit)
  │     ├─ MemoryMessageAdapter: MemoryHit → BaseMessage
  │     └─ 注入到 ReActAgent system prompt（SystemMessage 内）
  │
  ▼ Agent 执行（LLM 调用 + 工具调用）
  │
  ├─ MemoryRuntimeRail.afterInvoke()
  │     ├─ MemoryMessageAdapter: 对话轮次 BaseMessage → MemoryRecord
  │     └─ MemoryProvider.save(userId, sessionId, records)
  │
  ▼ 返回结果
```

**角色映射**：

| BaseMessage Role | MemoryRecord Role |
|-----------------|-------------------|
| `assistant` | `agent` |
| `system` | `system` |
| `tool` | `tool` |
| `user` | `user` |

### 4.2 State 持久化

#### 4.2.1 Checkpoint 配置流程

```
应用启动
  │
  ├─ 创建 Checkpointer Bean（InMemory / SQLite / 自定义）
  ├─ OpenJiuwenCheckpointerConfigurer.setDefault(checkpointer)
  │     └─ CheckpointerFactory.setDefaultCheckpointer(checkpointer)
  │
  ▼ Runtime 就绪
  │
  ├─ Agent 执行: Runner.runAgent(agent, input, conversationId, null)
  │     └─ openJiuwen 按 conversationId 自动 save/restore
  │
  ▼ 应用关闭 → Checkpointer 清理（框架负责）
```

`conversation_id` = `AgentExecutionContext.agentStateKey`。

---

## 5. 配置模型

### 5.1 完整配置示例

Memory 和 State 通过 Bean 注入，无固定 YAML 前缀：

```java
// Memory: 实现 MemoryProvider 并注册为 Bean
@Bean MemoryProvider memoryProvider() {
    return new MyCustomMemoryProvider();
}

// Checkpoint (OpenJiuwen): 创建并全局注册
@Bean Checkpointer checkpointer() {
    return new InMemoryCheckpointer();
}
// 在 @PostConstruct 中:
OpenJiuwenCheckpointerConfigurer.setDefault(checkpointer);
```

### 5.2 OpenJiuwen Checkpoint 后端

| 后端 | 持久化 | 多实例共享 | 适用场景 |
|------|--------|----------|---------|
| InMemory | 否 | 否 | 开发/测试 |
| SQLite | 是 | 否 | 单机部署 |
| 自定义 | 取决于实现 | 取决于实现 | 通过 `CheckpointerFactory.setDefault()` 注入 |

---

## 6. 对外呈现 / 用户场景

### 6.1 外部接口

| API | 说明 |
|-----|------|
| `MemoryProvider` SPI | 实现此接口接入任意记忆后端 |
| `MemoryRuntimeRail` | 通过 `openJiuwenRails()` 方法注入 ReActAgent |
| `OpenJiuwenCheckpointerConfigurer` | 全局配置 OpenJiuwen Checkpoint 后端 |

### 6.2 用户示例

#### 6.2.1 为 OpenJiuwen Agent 添加记忆

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

    @Override
    protected BaseAgent createOpenJiuwenAgent(AgentExecutionContext ctx) {
        // ... ReActAgent 配置
    }
}
```

前置条件：有 `MemoryProvider` Bean 注册。预期结果：每次调用前自动检索历史记忆并注入到 system prompt，调用后保存对话轮次。

#### 6.2.2 配置 SQLite Checkpoint

```java
@Configuration
public class CheckpointConfig {
    @Bean
    Checkpointer checkpointer() {
        return new SqliteCheckpointer(Path.of("/data/checkpoints"));
    }

    @PostConstruct
    void configure() {
        OpenJiuwenCheckpointerConfigurer.setDefault(checkpointer);
    }
}
```

前置条件：SQLite 驱动在 classpath。预期结果：Agent 会话状态持久化到 `/data/checkpoints`，重启后可恢复。

### 6.3 E2E 流程

```
应用启动
  │
  ├─ MemoryProvider Bean 初始化 (init)
  ├─ Checkpointer Bean 注册
  │
  ▼ 用户请求到达
  │
  ├─ MemoryProvider.search() → 历史记忆
  ├─ Agent 执行 (自动加载 checkpoint 状态)
  │     ├─ 对话中的模型调用 + 工具调用
  │     └─ 自动 checkpoint save
  ├─ MemoryProvider.save() → 保存新记忆
  │
  ▼ 返回响应
```

---

## 7. 错误处理

| 错误场景 | 触发条件 | 行为 | 对外结果 |
|---------|---------|------|---------|
| Memory 检索失败 | `search()` 抛出异常 | WARN 日志，Agent 以空记忆继续执行 | Agent 正常执行（无记忆增强） |
| Memory 保存失败 | `save()` 抛出异常 | WARN 日志 | 不影响 Agent 响应 |
| Checkpoint 加载失败 | 存储不可用 | openJiuwen 框架以新会话启动 | Agent 正常执行（无历史状态） |
| Checkpoint 保存失败 | 磁盘满 / 连接断开 | openJiuwen 框架处理异常 | 下次调用无法恢复本次状态 |

**降级策略**：记忆和 Checkpoint 都是增强能力，失败时 Agent 核心功能不受影响。Memory 检索失败以空记忆继续；Checkpoint 失败以新会话启动。

---

## 8. 限制与待补

| 限制 | 影响范围 | 临时方案 |
|------|---------|---------|
| 记忆仅轮次开始前一次性注入 | Agent 推理中途无法按需检索记忆 | 在 system prompt 中预先加载更多上下文 |
| 无记忆工具 | Agent 无法主动调用记忆读写 | 通过 Tool 封装 MemoryProvider |
| 仅 OpenJiuwen 已接入记忆 | AgentScope 无记忆能力 | 在 AgentScope 层自行实现 |
| Checkpoint 仅 OpenJiuwen | AgentScope 无状态持久化 | — |
| 无 Redis 预置 Checkpoint | 多实例部署无法共享状态 | 自行实现 `Checkpointer` 接口 |
