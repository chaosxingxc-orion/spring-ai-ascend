---
level: L1
view: feature-test-guide
module: agent-runtime
feature: middleware-decoupling
status: draft
---

# agent-runtime 中间件解耦样例配置与端到端验证指南

本文面向后续具体场景测试团队和客户开发团队，说明「中间件解耦」特性如何配置、如何验证、以及哪些位置应该由业务替换。

## 1. 特性目标

`agent-runtime` 不把 Agent State、Memory、Redis、Mem0 等后端硬编码到公共执行链路中。公共层只暴露稳定的窄接口和 OpenJiuwen 适配入口；具体后端由业务 wiring、examples 或客户工程选择。

当前样例覆盖四条端到端链路：

| 样例目录 | 能力 | 后端 | 外部依赖 |
|---|---|---|---|
| `examples/agent-runtime-middleware-state-inmemory` | Agent State checkpoint | OpenJiuwen `InMemoryCheckpointer` | 无 |
| `examples/agent-runtime-middleware-state-redis` | Agent State checkpoint | OpenJiuwen `RedisCheckpointer` | Redis |
| `examples/agent-runtime-middleware-memory-inmemory` | 长期 Memory | example-only `InMemoryMemoryProvider` | 无 |
| `examples/agent-runtime-middleware-memory-mem0` | 长期 Memory | Mem0-compatible REST provider | Mem0-compatible REST 服务 |

## 2. Agent State 配置

Agent State 当前优先复用 OpenJiuwen 原生 checkpointer。业务只需要在启动期选择 checkpointer，并保证 `agentStateKey` 稳定。

### 2.1 InMemory

```java
Checkpointer checkpointer = OpenJiuwenCheckpointerConfigurer.setInMemoryDefault();
```

适合本地开发和不跨进程恢复的测试。验证命令：

```bash
./mvnw -f examples/agent-runtime-middleware-state-inmemory/pom.xml test
```

### 2.2 Redis

```java
Checkpointer redisCheckpointer = new RedisCheckpointer.Provider()
        .create(Map.of("connection", Map.of("url", redisUrl)));
OpenJiuwenCheckpointerConfigurer.setDefault(redisCheckpointer);
```

测试环境需要提供 Redis：

```bash
docker run --rm -p 6379:6379 redis:7-alpine
export SAA_SAMPLE_OPENJIUWEN_REDIS_URL=redis://localhost:6379
./mvnw -f examples/agent-runtime-middleware-state-redis/pom.xml test
```

如果 Docker Hub 不可访问，可以使用内部镜像仓或大陆镜像仓的 Redis 镜像；只要最终暴露 `localhost:6379` 即可。

测试应确认：

- `preAgentExecute(...)` 能恢复或创建 session。
- `postAgentExecute(...)` 能保存 session 状态。
- `sessionExists(sessionId)` 在保存后返回 `true`。
- `release(sessionId)` 后状态被清理。

## 3. Memory 配置

Memory 当前定位为长期记忆。公共 SPI 是 `MemoryProvider`，只定义 `init`、`search`、`save` 基础语义。具体记忆抽取、压缩、去重、向量索引和租户隔离策略由具体 provider 负责。

OpenJiuwen 接入点是 `OpenJiuwenAgentRuntimeHandler#memoryRuntimeRail(...)`。业务 handler 可以在 `openJiuwenRails(context)` 中安装 memory rail。

### 3.1 InMemoryMemoryProvider

```java
InMemoryMemoryProvider provider = new InMemoryMemoryProvider();

class MemoryEnabledHandler extends OpenJiuwenAgentRuntimeHandler {
    @Override
    protected List<AgentRail> openJiuwenRails(AgentExecutionContext context) {
        return List.of(memoryRuntimeRail(context, provider));
    }
}
```

验证命令：

```bash
./mvnw -f examples/agent-runtime-middleware-memory-inmemory/pom.xml test
```

测试应确认：

- 用户输入进入 handler。
- handler 安装 memory rail。
- rail 执行前调用 `MemoryProvider.search(...)`。
- rail 执行后调用 `MemoryProvider.save(...)`。
- 不同 `agentStateKey` 的记忆隔离。

### 3.2 Mem0-compatible REST

```java
Mem0RestMemoryProvider provider = new Mem0RestMemoryProvider(
        "http://localhost:8000",
        apiKey,
        false,
        "oss");
```

该样例必须连接真实 Mem0-compatible REST 服务；没有配置
`SAA_SAMPLE_MEM0_BASE_URL` 时会跳过。自动化测试不再使用内嵌 HTTP server
伪装 Mem0。测试环境可以使用官方 Mem0 OSS REST server，也可以使用基于
`mem0ai` Python 包封装的本地 REST 服务，但服务内部必须真实调用
`Memory.add(...)` / `Memory.search(...)`。

```bash
./mvnw -f examples/agent-runtime-middleware-memory-mem0/pom.xml test
```

运行前需要提供：

```bash
export SAA_SAMPLE_MEM0_BASE_URL=http://localhost:8000
export SAA_SAMPLE_MEM0_API_KEY=
export SAA_SAMPLE_MEM0_API_MODE=oss
```

测试应确认：

- handler 执行前发生 search 请求。
- handler 执行后发生 add/memories 写入请求。
- 请求包含 user、session、task、agentStateKey 等隔离 metadata。
- 执行后再次 search，确认通过 handler/rail 保存的记忆可被真实 Mem0 服务检索。
- runtime 公共层不依赖 Mem0 SDK 或 Mem0 包名。

## 4. 测试团队推荐验证顺序

1. 先跑无外部依赖样例：

```bash
./mvnw -f examples/agent-runtime-middleware-state-inmemory/pom.xml test
./mvnw -f examples/agent-runtime-middleware-memory-inmemory/pom.xml test
./mvnw -f examples/agent-runtime-middleware-memory-mem0/pom.xml test
```

2. 准备 Redis 后跑 Redis 样例：

```bash
docker run --rm -p 6379:6379 redis:7-alpine
export SAA_SAMPLE_OPENJIUWEN_REDIS_URL=redis://localhost:6379
./mvnw -f examples/agent-runtime-middleware-state-redis/pom.xml test
```

3. 如果需要真实 Mem0 联调，把 `Mem0RestMemoryProvider` 的 `baseUrl` 和 `apiKey` 替换成客户环境，再复用样例测试结构。

## 5. 客户开发团队替换点

| 替换点 | 客户需要实现什么 | 不需要改什么 |
|---|---|---|
| Agent State 后端 | 创建 OpenJiuwen 原生 `Checkpointer` 并传给 `OpenJiuwenCheckpointerConfigurer.setDefault(...)` | 不需要修改 `AgentRuntimeHandler` SPI |
| Memory 后端 | 实现或复用 `MemoryProvider` | 不需要修改 A2A executor |
| Memory 注入策略 | 在 OpenJiuwen handler 中通过 `openJiuwenRails(context)` 安装 rail | 不需要在公共 runtime 层写 provider chain |
| 真实外部服务 | 在 README 或部署配置中提供 URL、key、连接串 | 不需要提交本地脚本或私密配置 |

## 6. 通过标准

一个样例不能只证明 Maven 编译通过。它至少要证明一次端到端特性链路：

- State 样例：session 创建/恢复、状态保存、状态存在性检查、release 清理。
- Memory 样例：用户输入进入 handler、memory rail 被安装、执行前检索、执行后写回、provider 或 REST stub 收到真实调用。

如果只是 provider 单元测试、没有经过 handler 或 OpenJiuwen 扩展点，不算该特性的端到端验证。
