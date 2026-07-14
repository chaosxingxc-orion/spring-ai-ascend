---
artifact_type: a2d_review_packet
version: "agent-bus-broker-filtering-spi-completion-decision"
status: adopted
adopted_at: 2026-07-14
adopted_by_req: REQ-2026-001
target_module: agent-bus
source_decision: "docs/architecture/l0/10-governance/review-packets/agent-bus-forwarding-runtime-transport-decision.md"
source_l2: "architecture/L2-Low-Level-Design/agent-bus/feat-014-a2a-call-event-forwarding.md"
---

# agent-bus broker 侧过滤机制 + 消费者 SPI 补全裁决（Stage 25 transport-decision delta）

## 0. 裁决边界与性质

本 packet 是 [`transport-decision`](agent-bus-forwarding-runtime-transport-decision.md)（Stage 25, `adopted-t4`）的 **delta / 细化**，不重新评审 T4 投递模型、broker 选型、ack 语义、租户纵深——上述全部沿用 `transport-decision` §1 裁决项表 D1-D8。本 packet 只补 `transport-decision` 留白的两块：

1. **消费者如何只收到发给自己的消息**（`transport-decision` §4 给了 broker 概念封装映射，但未指定 `targetServiceId` 判别机制——runtime-A 与 runtime-B 共享 topic 时靠什么隔离）。
2. **消费者 SPI 的 `subscribe` 生命周期方法**（`transport-decision` §4 组件表列了 `BrokerForwardingConsumerPort` = "broker poll → inbox"，但未定 subscribe 为 SPI 一等方法——结果是测试替身直接 `new DefaultMQPushConsumer()`，rocketmq lib 漏给调用方）。

**触发条件**：联调 UC-1..UC-10 真实 RocketMQ（`7.213.203.4:9876` + `apache/rocketmq:5.3.1` docker-compose）green 后浮现上述两块 gap。`transport-decision` §5 当时写"本开发机无自部署 RocketMQ 实例可连"——该前提已被联调推翻，故 `enablePropertyFilter` 等 broker 侧配置项现在可执行（Stage 25 时不可执行）。

**性质**：裁决阶段，不写生产代码。落地切片见 §7，目标进 step-8 baseline freeze 钉死。

## 1. 裁决项表

| # | 裁决项 | 裁决 | 性质 | 依据 |
|---|---|---|---|---|
| D1 | `targetServiceId` 判别机制 | **SQL92 property filter**（按命名属性过滤），非 tag / 非 per-runtime-topic / 非纯客户端 | 新裁决 | §2.1 |
| D2 | 过滤载体 | **命名属性 userProperty**（生产者已设 `targetServiceId`），不用 tag | 新裁决 | §2.1；`RocketMqBrokerForwardingRelay.java:102` |
| D3 | 过滤语法封装 | `MessageSelector.bySql` 关在 RocketMQ adapter；SPI 层只见 `DeliveryFilter`（结构化命名属性=值） | 新裁决 | §2.2；§3 |
| D4 | 消费者 `subscribe` 为 SPI 一等方法 | `subscribe(consumerServiceId, RouteHandle, DeliveryFilter)` + `close()`；`poll` 变无参 | 新裁决（细化 `transport-decision` §4） | §2.2；§3 |
| D5 | `routeHandle` 进消费者 SPI | 消费者订阅带 `routeHandle`，topic 由 adapter 内 `ForwardingEndpointResolver` 解析，不漏 topic 给调用方 | 新裁决 | §2.2；§3 |
| D6 | ArchUnit consumer 侧补齐 | `org.apache.rocketmq` 圈死在 `transport.broker` 子包（relay 已守，consumer 待补） | 细化既有 | [`transport-decision` §2.3](agent-bus-forwarding-runtime-transport-decision.md) |
| D7 | 可移植性契约措辞 | **尽力 broker 侧过滤，能力不具备时降级客户端**；不写死"broker 保证只投给目标" | 新裁决 | §2.3；§5 |
| D8 | 能力位 | adapter 暴露 `supportsBrokerSidePropertyFilter()`；correctness 无感、perf 有感 | 新裁决 | §2.3 |
| D9 | broker 配置 | SQL92 broker 侧生效前提 = `enablePropertyFilter=true`（5.3.1 镜像默认 false，当前 compose 未挂 broker.conf） | 新裁决 | §2.1；§6 |
| D10 | per-serviceId 消费组 | `runtime-${serviceId}`（同组负载均衡、跨组广播） | 确认既有 | [`feat-014 §5.2`](../../../../../architecture/L2-Low-Level-Design/agent-bus/feat-014-a2a-call-event-forwarding.md) |
| D11 | 不预建多 broker 框架 | 现在只实现 RocketMQ adapter；换 broker 时再写新 adapter | 新裁决（反 YAGNI 边界） | §2.4 |
| D12 | 生产者不动 | targeting 经 `ForwardingOutboxRecord.targetServiceId`（已盖 userProperty），**不给 producer 加 DeliveryFilter 参** | 新裁决（澄清） | §2.5 |
| D13 | 响应侧不动 | gateway 按 `correlationId` 客户端过滤（动态集合，不适合静态 SQL），留客户端 | 新裁决 | §2.5 |
| D14 | 消费模型保持 pull | `poll/commit/reject`（model B ack-after-consume，至少一次）；adapter 用 `DefaultLitePullConsumer`，不用 push consumer | 确认既有 | [`transport-decision` D5](agent-bus-forwarding-runtime-transport-decision.md)；§2.6 |

## 2. 裁决论证

### 2.1 为何 SQL92 而非 tag / per-runtime-topic / 纯客户端

四候选对照（基于 [`transport-decision` §4](agent-bus-forwarding-runtime-transport-decision.md) 已定的"共享 topic + consumerServiceId 消费组"基座）：

| 候选 | 生产者改动 | broker 配置 | broker 侧生效条件 | 多维度 | serviceId 字符限制 | 结论 |
|---|---|---|---|---|---|---|
| **SQL92 property filter** | **0**（`targetServiceId` 已是 userProperty，`RocketMqBrokerForwardingRelay.java:102`） | `enablePropertyFilter=true` | 开了开关才 broker 侧，否则静默退化客户端 | ✅（`targetServiceId='A' AND tenantId='T'`） | 无 | **采用** |
| Tag | 要设 tag + sanitize | 无 | 始终 broker 侧 | ❌（单 tag 单维度） | 受 tag charset 限制（忌点号，同 topic 已踩 `13fcc31e`） | 不采用 |
| per-runtime-topic | 要按 runtime 拆 topic | 无 | 始终 | ✅ | 无 | 不采用（topic 随 runtime 线性增长 + 动态建 topic + 失同 runtime 多实例组内负载均衡） |
| 纯客户端过滤 | 0 | 无 | n/a（broker 不参与） | ✅ | 无 | 不采用（= 现状，每个 runtime 拉全量本地丢，broker 侧收益为 0） |

**SQL92 胜出的核心**：生产者**已经**把 `targetServiceId` 当 userProperty 设了（D2），SQL92 零生产者改动即可对接；tag 还要改生产者 + sanitize + 受单维度限制。代价是 D9（`enablePropertyFilter` 一次性 broker 配置）。

### 2.2 为何 `subscribe` 必须进 SPI（消费者不引 rocketmq lib）

`BrokerForwardingConsumerPort`（`agent-bus/.../broker/BrokerForwardingConsumerPort.java`）当前只有 `poll(consumerServiceId, tenantId, now) / commit / reject`，**没有 `subscribe`**。后果：订阅只能由调用方用 rocketmq 原生 API 做——测试替身 `TempRuntime` 直接 `new DefaultMQPushConsumer().subscribe(TOPIC, "*")`（`RealBrokerResponseSideIntegrationTest.java:476`），gateway 响应消费者同理（`:118-119`）。**调用方直接 `import org.apache.rocketmq...`**，正是 `transport-decision` §2.3 要杜绝的漏。

修法（D4/D5）：`subscribe` 提为 SPI 一等方法，broker consumer 对象（`DefaultLitePullConsumer`）整个活在 adapter 内部；`poll` 变无参（订阅时 group/route/filter 已定）；新增 `close()` 关停 underlying consumer。消费者签名见 §3。

同根漏（D5）：当前 `poll` 签名**无 `routeHandle`**，topic 只能硬编码（测试 `TOPIC_INVOCATION_REQ` 常量）。`routeHandle` 进 `subscribe`，topic 由 adapter 内 `ForwardingEndpointResolver` 从 opaque routeHandle 解析（与生产者 `RocketMqBrokerForwardingRelay.java:67` 同一套 resolver，HD4 不破）。

### 2.3 可移植性契约措辞（D7/D8）

各 broker 能力（详见 §5 矩阵）：RocketMQ（SQL92）、Pulsar（BrokerEntryFilter）、RabbitMQ（headers exchange）、SNS（filter policy）、NATS（subject 模式）均有 broker 侧等价物但**机制形式各不同**；**Kafka / Redis Streams 无 broker 侧内容过滤**（只能 partition-by-key + 静态 `assign()` 或退回客户端）。

故契约**不能写死"broker 保证只投给目标"**（Kafka/Redis 做不到即违约）。措辞定为：

> **尽力 broker 侧过滤；adapter 能力不具备时降级客户端过滤。correctness 始终保证（调用方只收到 `DeliveryFilter` 命中的消息）；perf 特性（是否 broker 侧预过滤）通过 `supportsBrokerSidePropertyFilter()` 暴露。**

correctness 对调用方无感；perf 有感（Kafka/Redis 上调用方实际 pull 了别人的消息再本地丢，带宽成本）。capability flag 让运维知晓当前 broker 是否 broker 侧过滤，不破坏契约。

### 2.4 为何不预建多 broker 框架（D11）

抽象合理高度 = port + `DeliveryFilter` + **一个** RocketMQ adapter，不是 N-adapter 注册表。现在建 Pulsar/RabbitMQ/Kafka adapter 是 YAGNI（无第二个 broker 用户）。换 broker 时再写新 adapter 翻译同一 `DeliveryFilter`，port 签名不动——这是可移植性的兑现方式，不是现在要付的成本。

### 2.5 为何生产者 / 响应侧不动（D12/D13）

**生产者**（D12）：`targetServiceId` 已是 `ForwardingOutboxRecord` 属性 → `buildMessage` 盖成 userProperty（`:102`）。这就是消费者 `DeliveryFilter` 匹配的同一命名属性——**统一契约 = 共享属性名，不是生产者也带 filter 参**。生产者表达 targeting 经 envelope 属性，不经 filter 参（produce 是 fire-and-get-outcome，无"过滤"语义）。给 producer 加 `DeliveryFilter` 是错误抽象。

**响应侧**（D13）：gateway 按 `correlationId` 客户端过滤（`GatewayRuntimeService.acceptWindow`）。`correlationId` 是动态集合（每个在途请求一个，生命周期短），SQL/tag 表达式是启动时静态订阅——动态集合塞不进静态表达式。响应侧留客户端合理，**只动请求侧（runtime 收的方向）**。

### 2.6 为何保持 pull（D14）

`poll/commit/reject`（model B ack-after-consume）是 [`transport-decision` D5](agent-bus-forwarding-runtime-transport-decision.md) 已定，且是**最可移植**消费形态：Kafka/Redis 天生 pull（零阻抗），push broker（RocketMQ/Pulsar/RabbitMQ）pull 也支持。若当初选 push/listener，Kafka/Redis 需 adapter 内 pull→push 转换层。pull 不动；adapter 用 `DefaultLitePullConsumer`（原生 pull，与 SPI 形状对齐），不用测试替身图省事的 `DefaultMQPushConsumer`（push→poll 转换）。

## 3. 消费者 SPI 完成形态（before / after）

**before**（`BrokerForwardingConsumerPort.java:42`，过滤维度硬编码、无 subscribe、无 routeHandle）：

```java
interface BrokerForwardingConsumerPort {
    Optional<BrokerInboundMessage> poll(String consumerServiceId, String tenantId, long nowMillisEpoch);
    void commit(BrokerInboundMessage message);
    void reject(BrokerInboundMessage message, ForwardingFailureCode code);
}
```

**after**（D3/D4/D5）：

```java
// 新增，broker 无关：命名属性 = 值（adapter 翻译成 bySql / header 绑定 / filter policy / 客户端 fallback）
record DeliveryFilter(Map<String,String> requiredProperties) {
    static DeliveryFilter forRuntime(String tenantId, String myServiceId) {
        return new DeliveryFilter(Map.of("tenantId", tenantId, "targetServiceId", myServiceId));
    }
}

interface BrokerForwardingConsumerPort {
    void subscribe(String consumerServiceId, RouteHandle route, DeliveryFilter filter); // 启动时一次
    Optional<BrokerInboundMessage> poll(long nowMillisEpoch);                           // 反复，无参
    void commit(BrokerInboundMessage message);
    void reject(BrokerInboundMessage message, ForwardingFailureCode code);
    void close();                                                                       // 关停 underlying consumer
    boolean supportsBrokerSidePropertyFilter();                                         // D8 能力位
}
```

RocketMQ adapter 内部：`subscribe` 懒注册 `DefaultLitePullConsumer`（group=`consumerServiceId`、topic=resolver 解析 route、filter=`MessageSelector.bySql(拼 DeliveryFilter)`）；`poll` 取下一条；`commit`/`reject` 走手动 offset（`enable.auto.commit=false`，沿用 [`transport-decision` §3 ④](agent-bus-forwarding-runtime-transport-decision.md) model B）。`bySql` SQL 字符串**只在此 adapter 出现**，业务侧只见 `DeliveryFilter.forRuntime(...)`。

## 4. broker 概念封装映射（扩展 `transport-decision` §4）

`transport-decision` §4 映射表新增两行（broker 侧过滤维度）：

| broker 产品概念 | 封装为（broker-agnostic 治理语义） | 本 packet 裁决 |
|---|---|---|
| filter 表达式（tag / SQL92 / header 绑定 / filter policy） | `DeliveryFilter`（命名属性=值 criteria） | D3 新增 |
| subscribe 生命周期（`consumer.subscribe` / `assign`） | `BrokerForwardingConsumerPort.subscribe`（SPI 一等方法） | D4 新增 |

既有行（topic→routeHandle / partition key→messageId hash / consumer-group→consumerServiceId / offset→adapter commit / broker retry→ForwardingDeliveryResult）全部不变。

## 5. 各 broker 过滤能力矩阵（D7 可移植性契约依据）

| broker | broker 侧过滤能力 | `DeliveryFilter` 翻译为 | 能力位 |
|---|---|---|---|
| **RocketMQ**（锁定） | SQL92（`enablePropertyFilter=true`）+ tag | `MessageSelector.bySql(...)` | true（开 D9 后） |
| Pulsar | Broker Entry Filter（2.6.1+）+ key_shared | `BrokerEntryFilter` 实现 | true |
| RabbitMQ | headers exchange / direct / topic | queue 绑定 header criteria | true |
| SNS+SQS | Subscription Filter Policy | filter policy JSON | true |
| NATS / JetStream | subject 模式匹配 | serviceId 编进 subject | true |
| **Kafka** | ❌ 无 broker 侧内容过滤 | partition-by-serviceId + 静态 `assign()` 或客户端 fallback | **false** |
| Redis Streams | ❌ 无 | 客户端 fallback | **false** |

Kafka 是从 RocketMQ 换过去概率最高的选型，且做不到 broker 侧属性过滤——这是 D7 措辞"尽力+降级"、D8 能力位、不写死"broker 保证"的直接原因。

## 6. 护栏清单（step-8 baseline freeze 钉死）

- **`org.apache.rocketmq` 只能被 `transport.broker` 子包引用**（D6）：ArchUnit 规则 `forwarding_core_does_not_import_broker_client_outside_broker_adapter`（[`transport-decision` §2.3](agent-bus-forwarding-runtime-transport-decision.md) 已规划）对 consumer 侧生效。relay 已守（`RocketMqBrokerForwardingRelay` javadoc "ArchUnit-confined"）；consumer adapter 落地后同守。
- **测试替身走 SPI，不直接 `new DefaultMQPushConsumer`**（D4 验证机制）：`TempRuntime`/`ResponseSideConsumer` 改成调 `BrokerForwardingConsumerPort.subscribe/poll`。**测试能不碰 rocketmq lib 消费，即证明生产也能**——这是封装成立的硬证据。
- **`bySql` SQL 字符串只在 RocketMQ adapter 出现**（D3）：业务侧只见 `DeliveryFilter`，不构造 SQL。
- **`enablePropertyFilter=true` 必须验证真在 broker 侧生效**（D9）：5.3.1 默认 false，不开则 bySql 静默退化客户端（不报错）。验证法：临时让一个 runtime `bySql("targetServiceId='nobody'")`，发一条 target=别人的消息，确认该 runtime pull 0 条、broker 无投递；否则开关没生效。
- **生产者不改**（D12）：`ForwardingOutboxRecord.targetServiceId` 已是 userProperty 载体，不新增 producer filter 参。
- **响应侧不改**（D13）：gateway 按 `correlationId` 留客户端。
- **不预建 Pulsar/RabbitMQ/Kafka adapter**（D11）：换 broker 时再写。
- **broker retry off、outbox 保留、payloadRef 走 header、跨租户 R-C.c 显式 reject**：沿用 [`transport-decision` §10](agent-bus-forwarding-runtime-transport-decision.md) 护栏，不变。

## 7. 落地切片 / 时点

| 切片 | 内容 | 时点 |
|---|---|---|
| broker 配置 | 挂 broker.conf（`enablePropertyFilter=true`），改 compose broker 段 `command` 加 `-c` | step-8 前 |
| consumer SPI 改签名 | `BrokerForwardingConsumerPort` 加 `subscribe`/`close`/`supportsBrokerSidePropertyFilter`、`poll` 变无参、加 `DeliveryFilter` + `routeHandle` 参 | **现在**（零生产 impl，改最便宜） |
| RocketMQ consumer adapter | 实现 `subscribe`（懒注册 `DefaultLitePullConsumer` + bySql）、`poll/commit/reject/close` | consumer adapter 落地切片 |
| 测试替身改走 SPI | `TempRuntime`/`ResponseSideConsumer` 去掉 `new DefaultMQPushConsumer`，改调 SPI | 同上 |
| ArchUnit consumer 侧 | 既有规则对 consumer 包生效 | 同上 |
| step-8 baseline freeze | 本 packet `status: proposed → adopted`，进 baseline | step-8 |

**时点硬约束**：`BrokerForwardingConsumerPort` 当前**零生产实现**（只有 test double，真正消费者推迟到外部 `agent-runtime-java` 仓）。现在改签名 = 改 1 接口 + 几个测试替身；等外部仓按旧签名建好再改 = 动外部仓调用方，贵一个数量级。**现在是把"消费者不引 rocketmq lib"钉死的唯一便宜窗口**。

## 8. deferred

- **`enablePropertyFilter` 在华为云 DMS for RocketMQ 托管形态下的对应开关**：本机 `apache/rocketmq:5.3.1` 自部署用 broker.conf；DMS 托管形态的等价配置项 deferred 部署环境确认。
- **双层 ordering 与 SQL92 过滤的交互**：同 `targetServiceId` 的消息是否需同分区保序，沿用 [`transport-decision` §11 双层 ordering](agent-bus-forwarding-runtime-transport-decision.md) deferred，Stage 27/29 建立全局序号时同次裁决。
- **外部 `agent-runtime-java` 消费者落地**：本 packet 定 SPI 契约，实现 deferred 外部仓（仓不在本机，见 memory `agent-runtime-real-code-external`）。
- **capability flag 的运维可观测**：`supportsBrokerSidePropertyFilter()` 如何 surface 到监控/告警，deferred observability 切片。

---

相关文档：

- [`transport-decision`](agent-bus-forwarding-runtime-transport-decision.md)（Stage 25 `adopted-t4`，本 packet 的基座，本 packet 不重判其 D1-D8）。
- [`feat-014-a2a-call-event-forwarding`](../../../../../architecture/L2-Low-Level-Design/agent-bus/feat-014-a2a-call-event-forwarding.md) §5.2（`runtime-${serviceId}` 消费组，D10 确认）。
- [`feat-013-client-invocation-event-forwarding`](../../../../../architecture/L2-Low-Level-Design/agent-bus/feat-013-client-invocation-event-forwarding.md) §4.2（correlationId 响应匹配，D13 依据）。
