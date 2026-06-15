# State 持久化

Agent 执行状态的 checkpoint 持久化，当前通过 OpenJiuwen 框架的原生 Checkpointer 机制实现。支持 InMemory（开发/测试）和 SQLite（单机持久化）后端。

## 1. 概述

```java
// 最小示例：配置 InMemory Checkpoint
@Bean Checkpointer checkpointer() {
    return new InMemoryCheckpointer();
}
// 在 @PostConstruct 中全局注册
OpenJiuwenCheckpointerConfigurer.setDefault(checkpointer);
```

## 2. 快速开始

### Step 1 — 创建 Checkpointer Bean

```java
@Configuration
public class CheckpointConfig {
    @Bean
    Checkpointer checkpointer() {
        return new InMemoryCheckpointer();
    }

    @PostConstruct
    void configure() {
        OpenJiuwenCheckpointerConfigurer.setDefault(checkpointer);
    }
}
```

### Step 2 — 验证

Agent 执行时，OpenJiuwen 框架按 `conversation_id`（=`AgentExecutionContext.agentStateKey`）自动保存和恢复状态。重启后同一 `conversation_id` 的对话可继续。

## 3. 工作原理

```
应用启动
  │
  ├─ Checkpointer Bean 创建
  ├─ OpenJiuwenCheckpointerConfigurer.setDefault(checkpointer)
  │     └─ CheckpointerFactory.setDefaultCheckpointer(checkpointer)
  │
  ▼ Agent 执行
  │
  ├─ Runner.runAgent(agent, input, conversationId, null)
  │     └─ openJiuwen 按 conversationId 自动 save/restore
  │
  ▼ 应用关闭 → Checkpointer 清理（框架负责）
```

`conversation_id` = `AgentExecutionContext.agentStateKey`。每个 A2A 对话有独立的 Agent 状态。

## 4. 核心接口

Checkpoint 使用 OpenJiuwen 框架的原生接口，非 runtime SPI：

```java
// 全局配置
OpenJiuwenCheckpointerConfigurer.setInMemoryDefault();
// 或
OpenJiuwenCheckpointerConfigurer.setDefault(customCheckpointer);
```

## 5. 能力详述

### 后端选择

| 后端 | 持久化 | 多实例共享 | 适用场景 |
|------|--------|----------|---------|
| InMemory | 否 | 否 | 开发/测试 |
| SQLite | 是 | 否 | 单机部署 |
| 自定义 | 取决于实现 | 取决于实现 | 通过 `CheckpointerFactory.setDefault()` 注入 |

### InMemory 模式

```java
@Bean Checkpointer checkpointer() {
    Checkpointer cp = new InMemoryCheckpointer();
    CheckpointerFactory.setDefaultCheckpointer(cp);
    return cp;
}
```

进程重启后状态丢失，仅适用于开发测试。

### SQLite 模式

```java
@Bean Checkpointer checkpointer() {
    return new SqliteCheckpointer(Path.of("/data/checkpoints"));
}
```

状态持久化到文件，进程重启后可恢复。

## 6. 完整示例

```java
@Configuration
public class AppConfig {
    @Value("${app.checkpoint.dir:/data/checkpoints}")
    private String checkpointDir;

    @Bean
    Checkpointer checkpointer() {
        return new SqliteCheckpointer(Path.of(checkpointDir));
    }

    @PostConstruct
    void configure() {
        OpenJiuwenCheckpointerConfigurer.setDefault(checkpointer);
    }
}
```

前置条件：SQLite 驱动在 classpath。预期结果：Agent 会话状态持久化，重启后可恢复。每个 `conversation_id` 对应独立的 checkpoint。

## 7. 配置参考

| 配置项 | 类型 | 默认值 | 说明 |
|--------|------|--------|------|
| `sample.openjiuwen.checkpointer` | String | `in-memory` | checkpoint 后端选择 |
| `sample.openjiuwen.redis-url` | String | `redis://localhost:6379` | Redis 模式必填 |

## 8. 限制

- 仅 OpenJiuwen 支持 Checkpoint，AgentScope 未适配
- 无 Redis 分布式 Checkpoint 预置适配（需自行实现 `Checkpointer` 接口）
- 无跨框架通用 Checkpoint SPI

## 9. 相关资源

- 设计文档：`architecture/docs/L2/agent-runtime/middleware-services-design.md` §3-§4
- [Memory 服务](memory-services.md)
- [OpenJiuwen Adapter](openjiuwen-adapter.md)
- Example：`examples/agent-runtime-middleware-state-inmemory/`
