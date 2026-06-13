# agent-trip — 行程规划智能体

> 状态：**已落地，本地真链路验证通过**。
> 配套：[../agent-hotel/README.cn.md](../agent-hotel/README.cn.md)（本智能体唯一的下游子智能体）。
> 设计稿：`md文档/差旅智能体md/行程规划智能体.md`。

## 1. 目标与定位

差旅多智能体系统中的"行程规划智能体"。

- 当前阶段交付形态：**纯 Java 库 (jar)**，被宿主进程 import 后通过函数调用驱动。
- 内部走 **OpenJiuwen ReAct** 单 agent，唯一工具 `plan_hotel`（编排调用酒店规划子智能体）。
- 上游主规划智能体（他人负责）在**同一进程内**通过 `TripPlanningAgent.chat(String)` 调用本智能体。
- 下游酒店规划子智能体（[agent-hotel](../agent-hotel)）在**同一进程内**被 `plan_hotel` 工具以 Java 方法调用驱动。
- A2A 服务形态延迟到 `agent-runtime` 具备之后再做（届时核心代码不变，仅在外层加一个 A2A wrapper 模块）。

参考实现：[tuniucorp/aigc-agents](https://github.com/tuniucorp/aigc-agents) 的 `impl/trip/`。

## 2. 范围

**In scope**
- Java 库（jar）：暴露 `TripPlanningAgent` 入口。
- OpenJiuwen ReAct agent + 唯一工具 `plan_hotel`：从自然语言抽出城市/日期/差标/偏好，调用酒店子智能体。
- 以住宿为核心的 markdown 行程方案输出。
- `TripSampleMain`：演示「主规划(入参) → 行程规划 ReAct → 酒店子智能体」端到端串调。

**Out of scope**
- 事项收集 / 路线规划子智能体（本项目**不实现也不预留**）。
- A2A endpoint（推迟到 `agent-runtime` 具备）。
- Spring Boot app shell / HTTP server / port。
- 真实数据源（酒店侧用 mock；行程侧无独立数据）。
- 多日多段连续行程的统一编排（如需"北京 2 天 + 上海 3 天"，由上游/本体多次调 `plan_hotel`）。
- 流式输出（仅 sync `String chat(...)`）。

## 3. 模块标识

| 项 | 值 |
|---|---|
| 模块路径 | [examples/travel/agent-trip/](.) |
| groupId : artifactId | `com.huawei.ascend.examples : agent-travel-trip` |
| version | `0.1.0` |
| parent | **无**（独立 pom，未挂 `spring-ai-ascend-parent`，便于单独构建调试） |
| packaging | **jar（库，无 spring-boot-maven-plugin repackage）** |
| 入口类 | `com.huawei.ascend.examples.trip.TripPlanningAgent` |
| Sample 主类 | `com.huawei.ascend.examples.trip.TripSampleMain` |
| agentId | `trip-planning-agent` |
| 启动方式 | **无独立服务**（库形态，被宿主进程调用 / 跑 Sample 主类） |
| 依赖 | `com.openjiuwen:agent-core-java:0.1.12`、`com.huawei.ascend.examples:agent-travel-hotel:0.1.0-SNAPSHOT`（runtime） |

## 4. 对外接口 — Java 方法调用

### 4.1 入口类

```java
package com.huawei.ascend.examples.trip;

public class TripPlanningAgent {

    /**
     * 构造器：宿主进程注入 LLM 配置 + 酒店子智能体客户端（pure Java，无 Spring 注解）。
     * @param llm         LLM 连接配置
     * @param hotelClient 下游酒店规划子智能体的客户端（本期本地直连；A2A 预留）
     */
    public TripPlanningAgent(LlmConfig llm, HotelPlannerClient hotelClient);

    /**
     * 同步入口：传入自然语言差旅诉求，返回 markdown 行程方案。
     * 一次调用内自带 ReAct 循环（自主调 plan_hotel + 整合）。跨调用之间无状态。
     */
    public String chat(String userMessage);
}

/** LLM 连接配置（与 agent-hotel 的 LlmConfig 字段一致，宿主可复用同一套配置）。 */
public record LlmConfig(
    String provider, String apiKey, String apiBase, String modelName, boolean sslVerify) {

    /** 读取 classpath 下 llm.properties；同名环境变量优先级更高，可覆盖文件值。 */
    public static LlmConfig load();
}
```

### 4.2 输入约定 — 纯自然语言

主规划把出差基本要素 + 差标 + 偏好**全部拼成自然语言**下发：

```
员工 zhang3 出差北京 2026-06-16 至 2026-06-18，共 3 天。
差标：每晚不超过 800 元、最低 4 星、协议品牌 全季/亚朵/希尔顿欢朋。
偏好：国贸附近，需要会议室。
```

LLM 从中抽取城市/入离日期/差标/偏好，填入 `plan_hotel` 工具入参。

### 4.3 输出约定

返回 **markdown 字符串**：出差概览（城市/日期/天数）+ 住宿推荐（核心，来自 `plan_hotel`）+ 一句总推荐理由。不渲染 UI 卡片，渲染由上游/前端负责。

### 4.4 宿主集成示例

**纯 Java main 宿主（反射装配酒店子智能体，无 agent-hotel 编译期依赖）**
```java
LlmConfig llm = LlmConfig.load();

// 反射委托：ReflectiveHotelPlannerClient 内部 Class.forName 加载
// com.huawei.ascend.examples.hotel.HotelPlanAgentBuilder 构建酒店 ReActAgent，
// agent-trip 编译期不出现任何 agent-hotel 类型（pom 中 agent-hotel 为 runtime scope）。
HotelPlannerClient hotelClient = new ReflectiveHotelPlannerClient(
        llm.provider(), llm.apiKey(), llm.apiBase(), llm.modelName(), llm.sslVerify());

TripPlanningAgent tripAgent = new TripPlanningAgent(llm, hotelClient);
String itineraryMarkdown = tripAgent.chat("员工 zhang3 出差北京 ...");
System.out.println(itineraryMarkdown);
```

**反射友好入口（供主规划智能体反射装配）**
```java
// 包名/类名对齐 travel-assistant-mainplan-agent 的反射期望，
// 主规划智能体可在无编译期依赖的情况下加载并驱动本智能体。
ReActAgent trip = com.huawei.ascend.examples.travel.trip.TripPlanAgentBuilder.builder()
        .modelClient(provider, apiKey, apiBase, modelName, verifySsl)
        .build();   // 返回已注册 plan_hotel（反射委托酒店）的 ReActAgent
Object out = trip.invoke(Map.of("query", nl, "conversation_id", id), session);
```

> 提示：`ReflectiveHotelPlannerClient` 只引用 openJiuwen 共享类型（`ReActAgent`/`Runner`）+ 反射 API，运行时需 `agent-hotel` 在 classpath（pom 的 runtime 依赖或手动加 jar）。

## 5. 内部架构（ReAct 单体）

### 5.1 总体流程

```
TripPlanningAgent.chat(userMessage)
  ├─ 1. 构造 system prompt（含 today = Asia/Shanghai）
  ├─ 2. 构造 OpenJiuwen ReActAgent + 注册唯一工具 plan_hotel
  ├─ 3. Runner.runAgent(...) — ReAct 循环（LLM 自主决策）
  │      └─ LLM 从 NL 抽出 城市/日期/差标/偏好
  │          → 调用 plan_hotel（拼成酒店 NL → 酒店子智能体）
  │                └─ ReflectiveHotelPlannerClient.plan(nl)  ← 反射装配的酒店 ReActAgent，内部又是一轮 ReAct
  │          → LLM 观察酒店结果，整合成以住宿为核心的行程方案
  └─ 4. 返回 markdown
```

### 5.2 为什么用 ReAct（而非固定流水线）

- 与酒店子智能体「ReAct 单体 + 工具」技术栈一致，团队心智模型统一。
- 理解 NL、决定参数、整合输出都在循环内完成，无需额外"意图拆解/汇总"步骤。
- 可扩展：未来要加新子能力（如信息查询），直接注册新工具即可，编排逻辑不变。
- 不确定性通过 system prompt 规则 + `maxIterations=5` 约束。

### 5.3 与 aigc-agents 行程实现的对照

| 维度 | aigc-agents（tuniu） | 本智能体 |
|---|---|---|
| 编排方式 | `TripChain` 责任链 | **ReAct 单体 + 工具自主编排** |
| 框架 | Spring AI + 自研 Agent 体系 | **agent-core-java（openJiuwen ReActAgent）** |
| 子任务 | 同仓多子任务 | **仅酒店（调子智能体工具）** |
| 通信 | 进程内 + Spring Bean | 同进程函数调用；A2A 预留 |
| 输出 | Freemarker 卡片 | **纯 markdown** |

## 6. 工具设计 — `plan_hotel`

工具用 openJiuwen 的 `LocalFunction` 实现，注册方式：
```java
agent.getAbilityManager().add(tool.getCard());
Runner.resourceMgr().addTool(tool, AGENT_ID);
```

### Request（LLM 填充）
| 字段 | 类型 | 含义 | 必填 |
|---|---|---|---|
| `city` | string | 目的城市中文名 | 是 |
| `checkIn` | `yyyy-MM-dd` | 入住 | 是 |
| `checkOut` | `yyyy-MM-dd` | 离店 | 是 |
| `policyText` | string | 差标与偏好原文：价格上限/最低星级/协议品牌/商圈/设施 | 否 |
| `preferences` | string | 其他偏好 | 否 |

工具内部把入参拼成**纯自然语言**（中文品牌名，如"全季"而非"Ji Hotel"），交给 `HotelPlannerClient.plan(nl)` → 酒店子智能体 `chat(nl)`，返回酒店推荐 markdown。

## 7. 与酒店规划子智能体的衔接

| 对齐项 | 约定 |
|---|---|
| 集成方式 | 同进程调用，但**反射装配**：`plan_hotel` → `HotelPlannerClient.plan` → `ReflectiveHotelPlannerClient`（反射 `HotelPlanAgentBuilder`）→ 酒店 `ReActAgent` |
| 出站抽象 | `HotelPlannerClient` 接口；本期实现 `ReflectiveHotelPlannerClient`（反射委托 agent-hotel，无编译期依赖），`A2aHotelPlannerClient` 为 A2A 预留（throw） |
| 编译期依赖 | **无**：agent-trip 不 import 任何 agent-hotel 类型；agent-hotel 在 pom 中为 `runtime` scope，仅运行时 classpath 需要 |
| 酒店侧入口 | agent-hotel 暴露 `HotelPlanAgentBuilder.builder().modelClient(...).build()`（基本类型参数 + 返回共享类型 `ReActAgent`），专供反射装配 |
| 输入风格 | 纯自然语言（见 agent-hotel §4.2），城市/日期/差标/偏好拼进文本 |
| 协议品牌 | 用中文品牌名 |
| LLM 配置 | 两模块 `LlmConfig` 字段一致，宿主可共用一套环境变量 |
| 工具名隔离 | 行程 `plan_hotel` vs 酒店 `hotel_search`/`hotel_detail`，天然不冲突 |
| 输出 | 酒店返回 markdown，行程规划在 ReAct 整合阶段择优纳入 |

## 8. Prompt 设计（单一 ReAct system prompt）

`prompt/SystemPromptBuilder.build()`（`{today}` 注入 `Asia/Shanghai` 当天）要点：

```
你是华为差旅系统的行程规划助手。根据用户的出差诉求，给出一份以住宿为核心的行程方案。
【今天】{today}（yyyy-MM-dd）
【可用工具】plan_hotel：查询并推荐差旅酒店（需要城市、入住、离店日期；差标/偏好放 policyText/preferences）
【工作方式】
1. 从自然语言理解：城市、入离日期、天数、差标、偏好。
2. 城市缺失时主动询问，不要猜；日期严格 yyyy-MM-dd。
3. 调用 plan_hotel；差标原样放进 policyText（用中文品牌名）。
4. 不编造工具未返回的数据。
【输出格式】markdown：出差概览 + 住宿推荐（≤3 家 + 一句主推理由）
```

> 本项目不做事项收集 / 路线规划，prompt 不含"每日行程/路线"段落。

## 9. 目录结构

```
examples/travel/agent-trip/
├── README.cn.md                       ← 本文件
├── pom.xml                            # packaging=jar；agent-core-java（compile）+ agent-hotel（runtime）
├── query.txt                         # 本地调试输入（不入 jar；上游调用不读它）
├── .gitignore                        # 忽略 llm.properties（含真实 Key）
└── src/
    ├── main/
    │   ├── java/com/huawei/ascend/examples/trip/
    │   │   ├── TripPlanningAgent.java          # ★ 入口类：ReActAgent + 唯一工具 plan_hotel（含 public static buildPlanHotelTool/buildHotelNl）
    │   │   ├── LlmConfig.java                  # LLM 配置 record + load()
    │   │   ├── TripSampleMain.java             # main 串调 Sample（反射装配真实酒店子智能体）
    │   │   ├── prompt/SystemPromptBuilder.java # 拼 today + 编排规则
    │   │   └── hotel/
    │   │       ├── HotelPlannerClient.java          # 出站抽象
    │   │       ├── ReflectiveHotelPlannerClient.java# 反射委托 agent-hotel（无编译期依赖）
    │   │       └── A2aHotelPlannerClient.java       # A2A 预留（本期 throw）
    │   ├── java/com/huawei/ascend/examples/travel/trip/
    │   │   └── TripPlanAgentBuilder.java       # 反射友好入口，包名/类名对齐 mainplan 的反射期望
    │   └── resources/
    │       ├── llm.properties.example          # LLM 配置模板（复制成 llm.properties 填 Key）
    │       └── logback.xml                     # 日志降到 WARN + UTF-8，控制台只剩干净输出
    └── test/
        └── java/com/huawei/ascend/examples/trip/
            ├── support/StubHotelPlannerClient.java  # 酒店桩（隔离测行程规划）
            ├── PlanHotelToolTest.java               # 单测：buildHotelNl + plan_hotel 透传
            └── TripPlanningAgentIT.java             # 集成：真调 LLM + 酒店走桩
```

## 10. 配置

LLM 配置放 `src/main/resources/llm.properties`（被 `LlmConfig.load()` 读取；**已被 .gitignore 忽略，不会提交**）。首次使用从模板复制：

```bash
cp src/main/resources/llm.properties.example src/main/resources/llm.properties
# 然后填入真实 provider / apiKey / apiBase / model
```

```properties
LLM_PROVIDER=openai
LLM_API_KEY=sk-xxx
LLM_API_BASE=https://api.deepseek.com
LLM_MODEL=deepseek-chat
LLM_SSL_VERIFY=false
```

说明：
- 同名**环境变量优先级更高**，可临时覆盖文件值。
- **provider 名约束**：openJiuwen 0.1.12 仅接受 `[OpenAI, OpenRouter, SiliconFlow, DashScope, InferenceAffinity, inference_affinity]`，**不接受 `deepseek`**。对接 DeepSeek / OpenAI 协议兼容网关时，`provider` 填 `openai`（大小写不敏感），由 `apiBase` 决定实际端点。
- 稳定值（`maxIterations=5`、`timezone=Asia/Shanghai`）硬编码到 Java 常量，不通过配置外露。

## 11. 构建与运行

**前置**：先把两个依赖装进本地仓。

```bash
# 1) agent-core-java（openJiuwen 框架）
mvn -f <agent-core-java 仓>/pom.xml -DskipTests install

# 2) agent-hotel（酒店子智能体）
mvn -f examples/travel/agent-hotel/pom.xml -DskipTests install
```

**构建 / 测试 / 运行**（在 `examples/travel/agent-trip/` 目录）：

```bash
# 单元测试（不调 LLM）
mvn test

# 含集成测试 *IT（需配好 LLM Key）；跳过加 -DskipITs
mvn verify

# 跑 Sample：读 query.txt → 端到端串调（行程 ReAct → 酒店子智能体）
mvn compile exec:java
```

> ⚠️ 改了 `llm.properties` 后必须用 `mvn compile exec:java`（或 `mvn process-resources exec:java`）——`mvn exec:java` 不会重新拷贝资源，会读到 `target` 里的旧配置。

输入优先级：命令行参数（`-Dexec.args=...`）> `query.txt`（UTF-8，推荐）> 控制台交互。

## 12. 已知本地坑

- **Windows 控制台中文乱码**：JVM 字符串是 UTF-8，但 Windows 控制台默认 GBK。`TripSampleMain.main()` 顶部已强制 `System.setOut` 用 UTF-8 `PrintStream`；`logback.xml` 也设了 UTF-8。**输入**走 `query.txt`（程序按 UTF-8 读文件），彻底绕开 PowerShell stdin 编码问题——不要依赖控制台交互输入中文。
- **改配置不生效**：见 §11 的 `mvn compile exec:java` 提示。
- **端到端耗时**：一次请求跑两层 LLM（行程 + 酒店），网关较慢时可能数分钟，属正常不是卡死。

## 13. 开放问题

1. **🟢 共进程嵌套 ReAct**：`plan_hotel` 工具内部触发酒店子智能体的 `Runner.runAgent`（ReAct 套 ReAct），与行程自身的 `Runner.runAgent` 同栈。`Runner` 为进程级单例；工具名（`plan_hotel` vs `hotel_search`/`hotel_detail`）与 agentId 均不冲突，**本地真链路验证通过**。多 agent（再叠加机票/高铁）规模下的 `Runner` 共享语义待后续验证。
2. **🟡 plan_hotel 入参由 LLM 填结构化字段 vs 直接给整段 NL**：本期填 city/checkIn/checkOut/policyText 结构化字段、工具内拼 NL（确定性更好）；若发现拆字段易丢信息，可改为让 LLM 直接产出整段酒店 NL。
3. **🟡 模型客户端共享**：多 agent 共进程时 LLM HTTP 客户端是否进程级共享，观测后再优化。
4. **A2A wrapper 合入时机**：等 `agent-runtime` 具备后新增 `examples/travel/agent-trip-a2a`，把出站 `ReflectiveHotelPlannerClient` 换成 `A2aHotelPlannerClient`，本模块代码不动。

## 14. 测试

### 单元测试（不真调 LLM）
- `PlanHotelToolTest`：`buildHotelNl` 拼装含城市/日期/差标/偏好；`plan_hotel` 工具注入桩 `HotelPlannerClient` 时正确透传 NL 并回传 markdown。

### 集成测试（真调 LLM，可 `-DskipITs` 跳过）
- `TripPlanningAgentIT`：给标准差旅 NL → 返回含「出差概览 + 住宿推荐」的 markdown；酒店走桩以隔离行程规划自身。

### 手测
- `TripSampleMain` 读 `query.txt` 端到端串调真实酒店子智能体，人工核对城市/日期解析与住宿推荐。
