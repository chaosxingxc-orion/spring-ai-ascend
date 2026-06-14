# 运维指南

agent-runtime 在生产环境中的运维能力：生命周期管理、健康检查、日志诊断、嵌入式部署。

## 1. 概述

```yaml
# 最小示例：启用优雅停机和健康检查
server:
  shutdown: graceful
management:
  endpoints:
    web:
      exposure:
        include: health,info
```

## 2. 快速开始

```bash
# 验证健康检查
curl -s http://localhost:8080/actuator/health | jq .

# 预期输出：{"status":"UP","components":{"agentRuntime":{"status":"UP"}}}
```

## 3. 工作原理

```
应用启动
  ├─ Handler Bean 创建 → AgentRuntimeLifecycle.start()
  ├─ RuntimeReadiness → 开放（接受请求）
  ├─ 服务中：A2A 请求正常处理
  │
  ▼ 应用关闭
  ├─ RuntimeReadiness → 关闭（拒绝新请求）
  ├─ A2aServerExecutor: drain 等待进行中请求（10s grace）
  ├─ AgentRuntimeLifecycle.stop() → Handler.stop()
  └─ Spring 容器关闭
```

## 4. 能力详述

### 生命周期管理

| 阶段 | 行为 |
|------|------|
| start | Handler 依次初始化，全部就绪后开放端口 |
| serve | 接受 A2A 请求处理 |
| stop | 关闭端口，drain 进行中请求，调用 Handler.stop() |
| 就绪门控 | RuntimeReadiness 关闭时拒绝入站请求 |

### 健康检查

```bash
curl -s http://localhost:8080/actuator/health | jq .
```

| 状态 | 含义 |
|------|------|
| UP | Handler 正常运行 |
| OFF_OF_SERVICE | Handler 标记离线 |
| DOWN | Handler 异常 |

远程 Agent 目录健康状态：`available` / `pending` / `unreachable`。

### MDC 日志关联

每次请求自动注入以下 MDC 键，所有日志自动携带：

| MDC 键 | 来源 | 示例值 |
|--------|------|--------|
| contextId | 请求 message.contextId | `session-abc` |
| taskId | A2A SDK | `task-123` |
| tenantId | X-Tenant-Id 头或默认值 | `my-tenant` |
| agentId | Handler | `my-agent` |

### 错误码分类

异常自动归类为稳定错误码，在 A2A 错误响应和日志中可见：

| 错误码 | 可重试 | 典型原因 |
|--------|--------|---------|
| INVALID_INPUT | 否 | 请求参数格式错误 |
| TIMEOUT | 是 | 远程调用或 Agent 执行超时 |
| UPSTREAM_UNAVAILABLE | 是 | 远程 Agent 不可达 |
| CANCELLED | 否 | 任务被取消 |
| INTERNAL | 否 | 未分类的内部错误 |

### 嵌入式部署

```java
try (RunningRuntime runtime = RuntimeApp.create(handler).run(LocalA2aRuntimeHost.port(8080))) {
    // 服务运行中
}
```

> `RuntimeApp` API 仅封装 Handler 调用。当前仅 Spring Boot 路径可用。

## 5. 完整示例

```yaml
# application.yaml
server:
  port: 8080
  shutdown: graceful

management:
  endpoints:
    web:
      exposure:
        include: health,info

logging:
  level:
    com.huawei.ascend: DEBUG
```

预期结果：启动后 `/actuator/health` 返回 UP，日志携带 MDC 上下文，`Ctrl+C` 触发优雅停机。

## 6. 配置参考

| 属性 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `server.port` | int | `8080` | HTTP 端口 |
| `server.shutdown` | String | `immediate` | 设为 `graceful` 启用优雅停机 |
| `management.endpoints.web.exposure.include` | String | — | 暴露的端点，建议 `health,info` |
| `logging.level.root` | String | `INFO` | 根日志级别 |
| `logging.level.com.huawei.ascend` | String | `INFO` | Runtime 日志级别 |

## 7. 限制

| 限制 | 影响 | 替代 |
|------|------|------|
| 仅 Spring Boot 部署可用 | 非 Spring Boot 环境不支持 | 自行实现 RuntimeHost |
| 优雅停机 drain 超时硬编码 10s | 长请求可能被截断 | — |
| 启动配置无 fail-fast | 配置错误时可能静默使用默认值 | 启动后检查日志和 health |
| 无 Metrics/Prometheus | 缺少量化监控 | 使用 OTel Traces 替代 |

## 8. 相关资源

- 设计文档：`architecture/docs/L2/agent-runtime/a2a-protocol-and-communication-design.md` §4.3
- [配置属性参考](configuration-properties.md)
- [A2A 协议调用](a2a-endpoints.md)
