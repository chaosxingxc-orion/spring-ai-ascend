# Redis Checkpointer 样例教程

跟着做完，你会准备一个 Redis，再启动样例服务，通过 curl 保存、查询、释放一次 OpenJiuwen session checkpoint。

---

## 0. 你将验证什么

本样例验证短期状态接入可恢复后端：

```text
Redis 启动
        ↓
Spring Bean 初始化
        ↓
RedisCheckpointer.Provider 创建 Checkpointer
        ↓
OpenJiuwenCheckpointerConfigurer.setDefault(...)
        ↓
curl 保存 / 查询 / 释放 checkpoint
```

---

## 1. 环境准备

在仓库根目录执行命令。需要：

- JDK 21
- curl
- 一个可访问的 Redis
- 本地 18084 端口未被占用

Windows PowerShell 可以把下面的 `./mvnw` 换成 `./mvnw.cmd`。

如果你用 Docker，可以临时启动 Redis：

```bash
docker run --name saa-redis-checkpointer -p 6379:6379 -d redis:7
```

---

## 2. Step 1 — 启动样例守护进程

```bash
./mvnw -f examples/agent-runtime-middleware-state-redis/pom.xml spring-boot:run \
  -Dspring-boot.run.arguments="--sample.openjiuwen.redis-url=redis://localhost:6379"
```

服务地址：

```text
http://localhost:18084
```

---

## 3. Step 2 — 保存一次 state

```bash
curl -s -X POST http://localhost:18084/sample/state/save \
  -H 'Content-Type: application/json' \
  -d '{"stateKey":"demo-state","input":"first turn","turn":1,"answer":"pong"}'
```

期望响应：

```json
{
  "stateKey": "demo-state",
  "exists": true
}
```

---

## 4. Step 3 — 查询 state 是否存在

```bash
curl -s 'http://localhost:18084/sample/state/exists?stateKey=demo-state'
```

期望响应：

```json
{
  "stateKey": "demo-state",
  "exists": true
}
```

---

## 5. Step 4 — 读取并验证 state 内容

```bash
curl -s 'http://localhost:18084/sample/state/load?stateKey=demo-state'
```

期望响应包含 `turn=1` 和 `answer=pong`，表示新的 session 已经从 Redis checkpointer 恢复出保存内容：

```json
{
  "stateKey": "demo-state",
  "exists": true,
  "state": {
    "global_state": {
      "turn": 1,
      "answer": "pong"
    }
  }
}
```

---

## 6. Step 5 — 释放 state

```bash
curl -s -X DELETE http://localhost:18084/sample/state/demo-state
```

期望响应：

```json
{
  "stateKey": "demo-state",
  "exists": false
}
```

释放后再次查询应返回 `exists=false`。

---

## 7. Step 6 — 看代码入口

关键代码在：

- `AgentStateRedisApplication.java`
- `AgentStateRedisConfiguration#openJiuwenCheckpointer(...)`
- `OpenJiuwenCheckpointerConfigurer.setDefault(...)`

用户侧要复用这个模式时，核心动作是：

```java
@Bean
Checkpointer openJiuwenCheckpointer(String redisUrl) {
    Checkpointer redisCheckpointer = new RedisCheckpointer.Provider()
            .create(Map.of("connection", Map.of("url", redisUrl)));
    return OpenJiuwenCheckpointerConfigurer.setDefault(redisCheckpointer);
}
```

---

## 8. 清理

在启动服务的终端按 `Ctrl+C` 停止进程。

如果你使用了上面的 Docker Redis：

```bash
docker rm -f saa-redis-checkpointer
```
