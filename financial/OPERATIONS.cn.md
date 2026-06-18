# 金融 Agent 运维与可观测手册(OPERATIONS)

面向**运维(ops)**和**行业 agent 开发者**:部署后怎么观测、怎么按一次客户会话调试、怎么审计、怎么应对故障。

> 心智:playground 的可读 trace 是**开发态**;生产里靠下面三条 ——
> **结构化审计事件(按 traceId 串)** + **分布式追踪(OTel/Jaeger)** + **关联日志**。

---

## 1. 三种"看见 agent 在干什么"的方式

### a) 结构化领域审计(每个 agent 自动产生,零配置)
`ObservabilityRail` 自动挂在每个金融 agent 上,在关键节点经 `FinancialAudit` 落结构化事件到 `financial.audit` 日志器:

```
🧾 fin-audit trace=<runId> tenant=<租户> agent=<agentId> action=tool.call tool=recommend_products ok=true
🧾 fin-audit trace=<runId> tenant=<租户> agent=retail-wealth-advisor action=turn.completed outcome=blocked durationMs=12
```
- `action`:`tool.call` / `tool.error` / `model.error` / `turn.completed`
- `outcome`:`answer` / `blocked`(合规拦截)/ `interrupt`(待人审)/ `error`
- **生产**:把 `financial.audit` 日志器单独路由到审计 sink(文件/Kafka/SIEM),满足留痕与监管。

### b) 分布式追踪(OTel → Jaeger,跨 A2A hop)
平台内置 OTel span 导出,默认关。开启:
```bash
docker compose -f financial/ops/docker-compose.yml up -d        # 起本地 Jaeger
OTEL_ENABLED=true OTEL_ENDPOINT=http://localhost:4317 \
  BANK_LLM_PROVIDER=openai BANK_LLM_API_BASE=https://api.deepseek.com \
  BANK_LLM_MODEL=deepseek-chat BANK_LLM_API_KEY=sk-... \
  ./mvnw -f financial/pom.xml spring-boot:run
# 访问 http://localhost:16686 → 选 service → 看每个请求的 span(模型调用/工具/耗时/错误)
```
(financial 已自带 `opentelemetry-exporter-otlp`;平台的 span sink 由 `app.trajectory.otel.enabled` 激活。)

### c) 关联日志
`application.yaml` 的日志 pattern 已带平台的每请求 id:
```
%d{HH:mm:ss.SSS} %-5level [trace=%X{runId:-} tenant=%X{tenantId:-}] %logger - %msg
```
每条日志都带 `trace=` 和 `tenant=`,可直接按它们过滤。

---

## 2. 怎么调试"某个客户的某次会话"(最常用)

1. 从前端/网关拿到该请求的 **traceId/runId**(也在 A2A 响应的 `trajectory.*` 元数据里);
2. 一条命令复盘整段:
   ```bash
   grep "trace=<那个id>" app.log            # 关联日志 + 🧾 审计事件按时间排开
   ```
   能看到:命中了哪些工具、合规判定、是否进人审、最终 outcome、各步耗时。
3. 要可视化时序/跨服务,去 Jaeger 按 traceId 搜。

---

## 3. 部署模型 & 健康就绪

**一个 runtime 实例托管一个 agent**(平台约束)。用 `financial.agent`(env `FINANCIAL_AGENT`)选择本实例托管哪个:
```bash
FINANCIAL_AGENT=retail-wealth-advisor ./mvnw -f financial/pom.xml spring-boot:run   # 一个实例一个 agent
```
多个 agent = 部署多个实例(各自不同 `FINANCIAL_AGENT`),便于独立扩缩容/隔离故障。

健康就绪:
- `GET /actuator/health`(liveness)、`/actuator/health/readiness`、`/actuator/info`。
- **就绪探针含模型端点可达性**(`modelEndpoint` HealthIndicator,TCP 探测 `financial.llm.api-base`):模型不可达 → readiness `DOWN`,编排器不再向该实例打流量;但 liveness 不受影响(不会因上游故障重启 Pod)。
- A2A 探活:`GET /.well-known/agent-card.json` 应 200。

---

## 4. 故障预案

| 场景 | 现状 | 建议 |
|---|---|---|
| LLM 端点不可达 | 平台返回错误结果(`outcome=error`),审计有记录 | 在网关/前端对 `result_type=error` 给用户安全话术("稍后再试");按 `model.error` 事件告警 |
| 后端工具失败 | 工具返回 `{error:...}`,模型据此说明;审计 `tool.error` | 按 `tool.error` 速率告警;给工具加超时/重试 |
| 需人工审批堆积 | `outcome=interrupt` | 按 interrupt 速率监控审批队列积压 |
| 进程重启丢失待审状态 | 默认内存检查点 | 生产换 Redis 检查点(持久化 HITL,任意节点可续),见平台 `CheckpointerFactory` |

---

## 5. 密钥与配置

- 模型密钥用环境变量(`BANK_LLM_API_KEY`),**不入库、不入镜像**;生产用密钥管理(Vault/K8s Secret)注入。
- 多租户:运行时**不认证** `X-Tenant-Id`,生产必须前置鉴权网关剥离客户头、鉴权后重注入可信租户。

---

## 6. 指标与告警(Prometheus)

`/actuator/prometheus` 暴露指标;`ObservabilityRail` 自动产出以下业务指标(低基数标签 `agent`/`outcome`/`tool`/`type`,**不打 tenant 以免基数爆炸**——租户维度在审计日志/trace 里):

| 指标 | 类型 | 标签 | 含义 |
|---|---|---|---|
| `financial_agent_turns_total` | counter | agent, outcome | 每轮完成数(outcome=answer/blocked/interrupt/error) |
| `financial_agent_turn_latency_seconds` | timer | agent | 每轮时延 |
| `financial_agent_tool_calls_total` | counter | agent, tool, status | 工具调用(status=ok/error) |
| `financial_agent_errors_total` | counter | agent, type | 错误(type=model/tool) |

PromQL 告警示例:
```promql
# 合规拦截率突增(>10%)
sum(rate(financial_agent_turns_total{outcome="blocked"}[5m])) by (agent)
  / sum(rate(financial_agent_turns_total[5m])) by (agent) > 0.1

# 模型错误率(LLM 不稳)
sum(rate(financial_agent_errors_total{type="model"}[5m])) by (agent) > 0.05

# 待人审积压(interrupt 速率)
sum(rate(financial_agent_turns_total{outcome="interrupt"}[5m])) by (agent)

# 时延 p99 > 8s
histogram_quantile(0.99, sum(rate(financial_agent_turn_latency_seconds_bucket[5m])) by (le, agent)) > 8
```
Grafana:加 Prometheus 数据源,按上面指标建"每 agent 的吞吐/时延/拦截率/错误率"看板。

> 注:业务指标在**首次有流量后**才注册出现;JVM/HTTP 标准指标启动即有。

## 7. 待补强(再下一档)

- **优雅降级**:`outcome=error` 时在网关/前端给用户安全话术(已记 `model.error`/`errors_total` 可告警);可在 agent 侧加兜底回复。
- **token 成本**指标(需从 trajectory Usage 取 token 数,接入后按 agent 累计)。
- **限流/熔断**;审计事件落 SIEM 的标准化 schema;OTel 指标(非仅 trace)导出。
