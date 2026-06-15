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

## 3. 健康与就绪

- `GET /actuator/health`、`/actuator/info`(已开启)。
- A2A 探活:`GET /.well-known/agent-card.json` 应 200。
- ⚠️ 当前就绪探针**未覆盖模型端点可达性**——见下"待补强"。

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

## 6. 待补强(下一档,本手册只覆盖"核心可观测")

- **指标(Micrometer/Prometheus)**:按租户/agent 的 请求量、延迟、拦截率、待审数、**token 成本**、工具错误率 + Grafana 看板 + 告警规则。
- **就绪探针**含模型端点探测;**限流/熔断**;审计事件落 SIEM 的标准化 schema。

> 选 `/goal` 里的"核心+指标告警"档我就把指标层补上。
