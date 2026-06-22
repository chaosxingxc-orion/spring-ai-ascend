---
scope: version
module: agent-runtime
feature_type: functional
feature_id: Feat-Func-004
status: active
dependency:
  - agent-runtime-release-features.cn.md
  - ../architecture/L2-Low-Level-Design/agent-runtime/Feat-Func-004-middleware-memory-and-state.md
---

# 中间件解耦 — Memory & State — 黑盒行为说明

## 1. 特性定位

Agent 执行过程中依赖的通用基础设施能力（记忆、状态持久化）从 Agent 框架中解耦，以可注入、可替换的中间件服务形式由 runtime 统一提供。

- **解决的问题**：不同 Agent 框架有各自的记忆和持久化机制。runtime 通过 SPI 抽象统一这些能力，使同一套中间件实现可用于不同框架，切换后端不影响 Agent 代码。
- **适用场景**：需要为 Agent 添加跨会话记忆、需要持久化 Agent 执行状态以支持中断恢复。如果 Agent 无状态或框架自带完整持久化方案，不需要此特性。

## 2. 对外能力边界

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

## 3. 外部行为与用户场景

### 3.1 外部接口

| API | 说明 |
|-----|------|
| `MemoryProvider` SPI | 实现此接口接入任意记忆后端 |
| `MemoryRuntimeRail` | 通过 `openJiuwenRails()` 方法注入 ReActAgent |
| `OpenJiuwenCheckpointerConfigurer` | 全局配置 OpenJiuwen Checkpoint 后端 |

### 3.2 用户示例

#### 3.2.1 为 OpenJiuwen Agent 添加记忆

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

#### 3.2.2 配置 SQLite Checkpoint

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

### 3.3 E2E 流程

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
