# Agent Runtime Agent State InMemory Example

本样例验证 OpenJiuwen Checkpointer 的 InMemory 形态。它代表短期状态或会话 checkpoint 的最小使用方式：用户启动常驻服务，通过 curl 保存、查询、释放 state。

完整 step by step 教程见：[TUTORIAL.cn.md](TUTORIAL.cn.md)。

## 快速启动

```bash
./mvnw -f examples/agent-runtime-middleware-state-inmemory/pom.xml spring-boot:run
```

默认监听：

```text
http://localhost:18083
```

## 主要接口

| 接口 | 用途 |
|---|---|
| `POST /sample/state/save` | 保存一次 OpenJiuwen session checkpoint |
| `GET /sample/state/exists?stateKey=demo-state` | 查询 checkpoint 是否存在 |
| `GET /sample/state/load?stateKey=demo-state` | 用新的 session 读取并验证 checkpoint 内容 |
| `DELETE /sample/state/{stateKey}` | 释放 checkpoint |

## 设计要点

- `openJiuwenCheckpointer` Bean 在 Spring 容器启动时初始化。
- 样例通过 `OpenJiuwenCheckpointerConfigurer.setInMemoryDefault()` 把 OpenJiuwen 默认 checkpointer 设置为 InMemory。
- InMemory 适合本地开发和功能验证；生产环境通常应使用 Redis 或其他可恢复后端。
