# agent-runtime middleware state Redis example

这个样例验证「中间件解耦」下的 Agent State Redis 持久化路径：
业务侧创建 OpenJiuwen 原生 `RedisCheckpointer`，再通过
`OpenJiuwenCheckpointerConfigurer.setDefault(...)` 注入。`agent-runtime`
公共层不绑定 Redis 或 OpenJiuwen Redis 包名。

## 配置方式

适用场景：

- 需要跨进程、跨重启恢复 Agent State。
- 需要用 Redis 保存 OpenJiuwen 原生 checkpoint。
- 希望验证生产态持久化后端，但仍保持 runtime 公共层不依赖 Redis。

核心配置点：

```java
Checkpointer redisCheckpointer = new RedisCheckpointer.Provider()
        .create(Map.of("connection", Map.of("url", redisUrl)));
OpenJiuwenCheckpointerConfigurer.setDefault(redisCheckpointer);
```

`redisUrl` 由业务部署或测试环境提供。`agent-runtime` 不解析 Redis 配置，
也不直接依赖 Redis 包名；它只提供 `setDefault(...)` 入口，把业务侧创建好的
OpenJiuwen checkpointer 注入到 OpenJiuwen 的 `CheckpointerFactory`。

测试团队检查点：

- Redis 服务可达。
- 环境变量 `SAA_SAMPLE_OPENJIUWEN_REDIS_URL` 指向实际 Redis。
- `preAgentExecute(...)` 能恢复 session。
- `postAgentExecute(...)` 后 `sessionExists(sessionId)` 返回 `true`。
- `release(sessionId)` 后 Redis 中对应 checkpoint 被清理。

## Environment

启动一个 Redis，并设置连接地址：

```bash
docker run --rm -p 6379:6379 redis:7-alpine
export SAA_SAMPLE_OPENJIUWEN_REDIS_URL=redis://localhost:6379
```

如果 Docker Hub 访问不稳定，可以换成内部镜像或大陆镜像仓的 Redis 镜像；
只要最终暴露 `localhost:6379` 即可。

如果没有设置该环境变量，测试会通过 JUnit assumption 跳过。

## Run

```bash
./mvnw -f examples/agent-runtime-middleware-state-redis/pom.xml test
```

## 端到端链路

```text
Redis service
  -> RedisCheckpointer.Provider().create(...)
  -> OpenJiuwenCheckpointerConfigurer.setDefault(...)
  -> AgentSessionApi(sessionId)
  -> checkpointer.preAgentExecute(...)
  -> session.updateState(...)
  -> checkpointer.postAgentExecute(...)
  -> checkpointer.sessionExists(sessionId)
  -> checkpointer.release(sessionId)
```

## Scope

- 验证 `preAgentExecute -> postAgentExecute -> release` 的一轮 Redis
  checkpointer 状态持久化链路。
- 不引入脚本；Redis 启动和环境变量配置由 README 说明。
