# Agent Runtime Agent State Redis Example

本样例验证 OpenJiuwen Checkpointer 的 Redis 形态。它代表短期状态或会话 checkpoint 接入可恢复后端的方式：用户先准备 Redis，再启动样例服务，通过 curl 保存、查询、释放 state。

完整 step by step 教程见：[TUTORIAL.cn.md](TUTORIAL.cn.md)。

## 快速启动

先准备可访问的 Redis，然后启动样例：

```bash
./mvnw -f examples/agent-runtime-middleware-state-redis/pom.xml spring-boot:run \
  -Dspring-boot.run.arguments="--sample.openjiuwen.redis-url=redis://localhost:6379"
```

默认监听：

```text
http://localhost:18084
```

## 主要接口

| 接口 | 用途 |
|---|---|
| `POST /sample/state/save` | 保存一次 OpenJiuwen session checkpoint |
| `GET /sample/state/exists?stateKey=demo-state` | 查询 checkpoint 是否存在 |
| `GET /sample/state/load?stateKey=demo-state` | 用新的 session 读取并验证 checkpoint 内容 |
| `DELETE /sample/state/{stateKey}` | 释放 checkpoint |

## 设计要点

- `OpenJiuwenCheckpointerConfigurer.setDefault(...)` 把 Redis checkpointer 设置为 OpenJiuwen 默认实现。
- Redis 适合验证“短期状态可恢复后端”的 wiring 方式。
- 本样例不负责启动 Redis；测试团队或客户开发团队按自己的环境准备即可。
