# Internal Event Queue 实现与时延观测

本文说明当前 Internal Event Queue 的本地实现、观测日志和最近一次端到端验证结论。

## 1. 实现范围

1. 当前只有一个真实队列实现：`InMemoryInternalEventQueue<T>`。
2. `QueueManager` 负责按自定义 `queueId + payloadType` 创建、复用、查找和关闭队列。
3. `DefaultEgressQueueRegistry`、`InternalEngineCommandGateway`、`TaskQueueRegistry` 是队列使用方，不是队列子类。
4. 队列不关心内容物类型，也不理解 Task 状态；它只负责多生产者写入和单消费者流式读取。

## 2. 日志与 trace 点

当前队列层保留两类日志：

1. 生命周期日志：队列创建、复用、订阅、关闭和注销使用 `INFO` 或 `WARN`。
2. 单条事件日志：`queue-offer` 使用 `DEBUG`，避免压力测试和端到端运行时刷屏。

Engine 侧会在收到队列事件时打印 `engine-command-received`，其中 `queueWaitMs` 表示事件从入队到被 Engine 命令处理器接收的等待时间。这个字段是判断队列是否形成瓶颈的主要证据。

## 3. 最近一次观测结论

最近一次队列压力 smoke 使用 8 个并发生产者写入 4000 条事件，结果如下：

| 场景 | 事件数 | 总耗时 | 平均队列等待 | 最大队列等待 |
|---|---:|---:|---:|---:|
| Internal Event Queue 压力 smoke | 4000 | 66-80 ms | 66-72 us | 1.4-1.7 ms |

同一轮验证中，fake agent 端到端和本地 Ollama 端到端的 `queueWaitMs` 均为 `0 ms`。这说明当前慢点不在 Internal Event Queue，而在 AgentHandler / LLM 执行阶段。

代表性的本地 Ollama 端到端日志显示：

| 阶段 | 代表耗时 |
|---|---:|
| `engine-dispatch-publish` | 2 ms |
| `engine-command-received.queueWaitMs` | 0 ms |
| `engine-handler-finish` | 18204 ms |
| `a2a-egress-deliver` | 10 ms |

结论：当前 in-memory 队列实现的耗时是微秒到低毫秒级，远低于本地 LLM 推理耗时。若后续改成 Redis、Kafka 或其他分布式实现，需要重新保留同样的 `queueWaitMs` / `queue-offer` 观测口径。
