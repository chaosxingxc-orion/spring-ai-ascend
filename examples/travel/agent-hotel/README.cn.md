# agent-hotel — 酒店规划子智能体（设计稿 v3）

> 状态：**设计稿 v3，待 review**。代码尚未落地。
> 与 v2 差异（重要 pivot）：
> - `com.huawei.ascend:agent-runtime` 暂未具备拉起服务的能力，项目组决定**当前阶段聚焦 agent 内部业务逻辑**，多智能体**共进程**通过 Java 函数调用交互
> - 交付形态从"独立 Spring Boot app + A2A endpoint"改为**纯 Java 库 (jar)**
> - 模块改名 `agent-hotel-a2a` → **`agent-hotel`**（A2A 后缀会在 runtime 具备后另起一个 wrapper 模块）
> - 去掉 `HotelAgentApplication` / `HotelAgentHandler`，新增 `HotelPlanningAgent` 入口类
> - pom 去掉 `agent-runtime` 和 `spring-boot-starter-web` 依赖
> - application.yaml 保留但**只承载 LLM 配置**，server / A2A / max-iterations 等全删
> - 业务侧（工具 / mock 数据 / prompt）**完全不变**

## 1. 目标与定位

差旅多智能体系统中的"酒店规划子智能体"。

- 当前阶段交付形态：**纯 Java 库**，被宿主进程 import 后通过函数调用驱动
- 内部走 **OpenJiuwen ReAct** 单 agent + 2 个 mock 工具
- 上游 trip planner（另一子智能体，他人负责）在**同一进程内**通过 Java 方法调用本子智能体
- A2A 服务形态延迟到 `agent-runtime` 具备之后再合入（届时本模块的核心代码不变，仅在外层加一个 A2A wrapper 模块）

参考实现：[tuniucorp/aigc-agents](https://github.com/tuniucorp/aigc-agents) 的 `impl/hotel/`。

## 2. 范围

**In scope**
- Java 库（jar）：暴露 `HotelPlanningAgent` 入口
- OpenJiuwen ReAct agent + 2 个 mock 工具：`hotel_search`、`hotel_detail`
- 覆盖中国 31 个省会/自治区首府+直辖市，每城市 10 条酒店
- 差标信息通过自然语言由 trip planner 拼装传入

**Out of scope (v3)**
- A2A endpoint（推迟到 `agent-runtime` 具备）
- Spring Boot app shell / HTTP server / port
- `agent-runtime` SPI 适配（`AgentRuntimeHandler` / `OpenJiuwenMessageAdapter` 等）
- 真实酒店数据源
- 与机票/高铁 agent 的同步联动
- 订单创建/支付、取消改签
- 多日多段连续行程
- **流式输出**（v1 只给 sync `String chat(...)`，足够函数调用模式用）
- 地图、POI、距离计算

## 3. 模块标识

| 项 | 值 |
|---|---|
| 模块路径 | [examples/travel/agent-hotel/](.) |
| groupId : artifactId | `com.huawei.ascend : agent-hotel` |
| parent | `spring-ai-ascend-parent` |
| packaging | **jar（库，无 spring-boot-maven-plugin repackage）** |
| 入口类 | `com.huawei.ascend.examples.hotel.HotelPlanningAgent` |
| 启动方式 | **无**（库形态，被宿主进程调用） |

## 4. 对外接口 — Java 方法调用

### 4.1 入口类

```java
package com.huawei.ascend.examples.hotel;

public class HotelPlanningAgent {

    /** 构造器：由宿主进程注入 LLM 配置（pure Java，无 Spring 注解）。 */
    public HotelPlanningAgent(LlmConfig llm);

    /**
     * 同步入口：传入自然语言，返回 markdown 推荐文本。
     * 一次调用内自带 ReAct 循环（工具调用 + 整合）。
     * 跨调用之间无状态；trip planner 需要多轮对话时，自己拼上下文重新调即可。
     */
    public String chat(String userMessage);
}

/** LLM 连接配置。 */
public record LlmConfig(
    String provider,
    String apiKey,
    String apiBase,
    String modelName,
    boolean sslVerify) {

    /** 便利方法：从环境变量构造（key 同 application.yaml）。 */
    public static LlmConfig fromEnv();
}
```

### 4.2 输入约定

trip planner 把出差基本要素 + 差标 + 偏好**全部拼成自然语言**：

```
员工 zhang3 出差北京 2026-06-16 至 2026-06-18。
差标：每晚不超过 800 元、最低 4 星、协议品牌 全季/亚朵/希尔顿欢朋。
偏好：国贸附近，需要会议室。
```

LLM 从自然语言里抽出 `cityName` / `checkIn` / `checkOut` / `maxPricePerNight` / `minStar` / `brandWhitelist` / `keyword`，填入工具入参。

### 4.3 输出约定

返回 **markdown 字符串**：最多 6 条推荐（每条 2 行）+ 末尾汇总段。trip planner 负责把 markdown 再渲染成 UI 卡片。

### 4.4 宿主集成示例

**Spring Boot 宿主**
```java
@Bean
HotelPlanningAgent hotelAgent(
        @Value("${hotel-agent.llm.provider}")   String provider,
        @Value("${hotel-agent.llm.api-key}")    String apiKey,
        @Value("${hotel-agent.llm.api-base}")   String apiBase,
        @Value("${hotel-agent.llm.model-name}") String modelName,
        @Value("${hotel-agent.llm.ssl-verify}") boolean sslVerify) {
    return new HotelPlanningAgent(
        new LlmConfig(provider, apiKey, apiBase, modelName, sslVerify));
}

// trip planner 那侧：
@Autowired private HotelPlanningAgent hotelAgent;

String md = hotelAgent.chat("员工 zhang3 出差北京 ...");
```

**纯 Java main 宿主**
```java
HotelPlanningAgent agent = new HotelPlanningAgent(LlmConfig.fromEnv());
String md = agent.chat("员工 zhang3 出差北京 ...");
System.out.println(md);
```

## 5. 内部架构

### 5.1 总体流程

```
chat(userMessage)
  ├─ 1. 构造 system prompt（含 today = ZonedDateTime.now("Asia/Shanghai")）
  ├─ 2. 构造 OpenJiuwen ReActAgent + 注册 hotel_search / hotel_detail 工具
  ├─ 3. Runner.runAgent(...) — ReAct 循环
  │      └─ LLM 从 NL 抽出 city/checkIn/checkOut/差标 → 调工具
  │          → MockHotelInventory 内存查询 → 结果回 LLM
  │      └─ LLM 整合 → markdown
  └─ 4. 返回 String
```

### 5.2 与 tuniu / 与 v2 (A2A) 的差异

| 维度 | tuniu | v2 (A2A) | **v3 (函数调用)** |
|---|---|---|---|
| 部署形态 | Spring Boot app | 独立 Spring Boot app | **库 (jar)，宿主进程引入** |
| 对外接口 | Spring AI ChatClient + WebController | A2A JSON-RPC + SSE | **`HotelPlanningAgent.chat(String)`** |
| Agent 分层 | SelectionAgent + Recommender 两阶段 | ReAct 单体 | **ReAct 单体**（不变）|
| 工具数量 | 3 | 2 | **2**（不变）|
| 输出 | Freemarker → 卡片 | markdown | **markdown**（不变）|
| 差标传递 | 无 | NL（A2A metadata 当时不通） | **NL**（不变）|

## 6. 工具设计

工具**直接实现 openJiuwen 0.1.12 的 `com.openjiuwen.core.foundation.tool.Tool` 接口**（不依赖 agent-sdk，因为 agent-sdk 锁 0.1.7）。注册方式参考 0.1.7 的等价写法：
```java
agent.getAbilityManager().add(tool.getCard());
Runner.resourceMgr().addTool(tool, agentId);
```
> 0.1.12 的 `Tool` / `AbilityManager` / `Runner.resourceMgr()` API 兼容性在实现期 spike 验证。

### 6.1 `hotel_search`

#### Request（LLM 填充）
| 字段 | 类型 | 含义 | 必填 |
|---|---|---|---|
| `cityName` | string | 城市中文名 | 是 |
| `checkIn` | `yyyy-MM-dd` | 入住 | 是 |
| `checkOut` | `yyyy-MM-dd` | 离店 | 是 |
| `maxPricePerNight` | number | 价格上限元/晚（LLM 从 NL 解析）| 否 |
| `minStar` | int 2-5 | 星级下限（LLM 从 NL 解析）| 否 |
| `brandWhitelist` | string[] | 协议品牌中文名数组（LLM 从 NL 解析）| 否 |
| `keyword` | string | 其他偏好（商圈/设施关键字，逗号分隔）| 否 |
| `pageNum` | int | 翻页，默认 1，每页 6 | 否 |

#### Response
```
successCode: boolean
totalCount:  int
hotels:      List<HotelBrief>
```

`HotelBrief`：
| 字段 | 类型 |
|---|---|
| `hotelId` | string |
| `chineseName` | string |
| `star` | int (**2-5**) |
| `brand` | string |
| `lowestPrice` | number (元/晚) |
| `commentScore` | number (0-10) |
| `district` | string（商圈/区，纯文本，**无坐标无距离**）|
| `address` | string |
| `compliancePassed` | boolean（按请求中的 maxPricePerNight / minStar / brandWhitelist 计算；任一字段空则该项默认通过）|
| `facilityTags` | string[] |

### 6.2 `hotel_detail`

#### Request
| 字段 | 类型 | 必填 |
|---|---|---|
| `hotelId` | string | 是 |
| `checkIn` | `yyyy-MM-dd` | 是 |
| `checkOut` | `yyyy-MM-dd` | 是 |

#### Response
酒店基础字段同 search（`star` 同样 **2-5**）+ `rooms: List<RoomOffer>`：

| RoomOffer 字段 | 类型 |
|---|---|
| `roomId` / `roomName` / `bedTypeName` / `area` / `window` | string |
| `breakfastIncluded` | boolean |
| `cancellable` | boolean |
| `rmbPrice` | number（单晚） |

### 6.3 cityName 规范化
LLM 可能写 "北京" / "北京市" / "Beijing"，[`MockHotelInventory`](src/main/java/com/huawei/ascend/examples/hotel/mock/MockHotelInventory.java) 内部做归一：
- 去尾 "市" / "省"
- 大小写归一
- 英文→中文映射表
- 失败 → 返回 `successCode=false` + 空 hotels

## 7. Mock 数据设计

### 7.1 城市清单（31）
| 类别 | 数量 | 城市 |
|---|---|---|
| 直辖市 | 4 | 北京、上海、天津、重庆 |
| 省会 | 22 | 石家庄、太原、沈阳、长春、哈尔滨、南京、杭州、合肥、福州、南昌、济南、郑州、武汉、长沙、广州、海口、成都、贵阳、昆明、西安、兰州、西宁 |
| 自治区首府 | 5 | 呼和浩特、银川、乌鲁木齐、拉萨、南宁 |

### 7.2 每城市 10 条酒店的分布
| 数量 | 类型 | 示例品牌 | 星级 | 价格基线 |
|---|---|---|---|---|
| 3 | 中端连锁 | 全季、亚朵、桔子水晶 | 3-4 | 350-650 |
| 3 | 高端连锁 | 万豪、希尔顿欢朋、洲际智选假日、万丽 | 4-5 | 700-1500 |
| 2 | 经济连锁 | 汉庭、如家、锦江之星 | **2-3** | 180-320 |
| 2 | 本地特色 / 老牌 | 北京：贵宾楼；广州：白天鹅；西安：人民大厦…… | 4-5 | 500-2000 |

### 7.3 价格分级
| 层级 | 城市 | 倍率 |
|---|---|---|
| T1 一线 | 北京、上海、广州 | ×1.3 |
| T2 新一线 | 成都、杭州、武汉、南京、天津、重庆、西安 | ×1.0 |
| T3 二线 | 其余省会 | ×0.7 |
| T4 西部 | 拉萨、西宁、银川、乌鲁木齐、呼和浩特 | ×0.8 |

### 7.4 数据文件
[`src/main/resources/mock/hotels.json`](src/main/resources/mock/hotels.json)，已含全部 31 城 × 10 = **310** 条。

### 7.5 加载与查询
- `MockHotelInventory` 是**普通 Java 类**（不带 `@Component`）
- 构造时（默认 / 显式传 Path）读 `mock/hotels.json` → `Map<String, List<Hotel>>` keyed by 归一化 cityName
- 查询 = 内存过滤；翻页 = 应用层每页 6 条
- 由 `HotelPlanningAgent` 在构造时**懒加载** + **进程级单例**复用

## 8. Prompt 设计

System prompt（实现期由 handler 拼装，`{today}` 服务端注入 `ZonedDateTime.now("Asia/Shanghai")`）：

```
你是华为差旅系统的酒店规划助手。根据用户出差需求调用工具查询酒店并给出推荐。

【今天】{today}（yyyy-MM-dd）

【输入特征】
- 用户输入会同时包含出差基本要素（城市/日期）和差标信息（价格上限/最低星级/协议品牌/POI 偏好）
- 你需要从自然语言中抽出这些字段，调用 hotel_search 时填入对应入参

【规则】
1. 用户未明确城市时，主动询问。不要猜。
2. checkIn/checkOut 未说时：默认次日入住、住 1 晚；当前时间 18:00 后默认隔日。
3. 日期严格 yyyy-MM-dd。
4. 调用 hotel_search 时把差标条件填入入参（maxPricePerNight / minStar / brandWhitelist），LLM 自己从用户文本里抽。
5. 用户描述的商圈、设施关键字放入 keyword（逗号分隔）。
6. 不要编造数据，所有酒店信息来自工具返回。
7. 用户问"第几家详情"时调用 hotel_detail。

【输出格式】
markdown：最多 6 条推荐，每条两行：
  「N. 酒店名 · ★星级 · 品牌 · ¥单晚最低价起 · 商圈 · [符合/不符合差标]」
  「推荐理由 ≤ 30 字」

末尾一行汇总：
  「推荐：XXX；理由：YYY」

【全部不符合差标时】
不要返回空列表。降级返回候选并清楚标 [不符合差标]，由上游决定下一步。
```

## 9. 目录结构（计划）

```
examples/travel/agent-hotel/
├── README.cn.md            ← 本文件
├── pom.xml                 ← packaging=jar（库）
└── src/
    ├── main/
    │   ├── java/com/huawei/ascend/examples/hotel/
    │   │   ├── HotelPlanningAgent.java            # ★ 入口类
    │   │   ├── LlmConfig.java                     # LLM 配置 record + fromEnv()
    │   │   ├── prompt/
    │   │   │   └── SystemPromptBuilder.java       # 拼 today
    │   │   ├── mock/
    │   │   │   ├── MockHotelInventory.java        # JSON 加载 + 内存索引 + cityName 归一化
    │   │   │   ├── Hotel.java
    │   │   │   └── Room.java
    │   │   └── tool/
    │   │       ├── HotelSearchTool.java           # implements openJiuwen Tool
    │   │       └── HotelDetailTool.java
    │   └── resources/
    │       ├── application.yaml                   # LLM 配置模板（供宿主参考）
    │       └── mock/hotels.json                   # 31 × 10 = 310 条
    └── test/
        ├── java/com/huawei/ascend/examples/hotel/
        │   ├── MockHotelInventoryTest.java        # 加载完整性 + cityName 归一化
        │   ├── HotelSearchToolTest.java           # 过滤逻辑 + compliancePassed
        │   ├── HotelDetailToolTest.java
        │   └── HotelPlanningAgentIT.java          # 集成：真调 LLM 验证 chat()
        └── resources/
            └── sample-prompts.txt                 # 手测用样例 NL
```

> 删掉 v2 的：`HotelAgentApplication` / `HotelAgentHandler` / `HotelAgentConfiguration` / `HotelAgentProperties`。

## 10. 配置

[application.yaml](src/main/resources/application.yaml) 只保留 LLM 配置：

```yaml
hotel-agent:
  llm:
    provider:   ${LLM_PROVIDER:openai}
    api-key:    ${LLM_API_KEY:sk-local-placeholder}
    api-base:   ${LLM_API_BASE:http://localhost:4000/v1}
    model-name: ${LLM_MODEL:gpt-4o-mini}
    ssl-verify: ${LLM_SSL_VERIFY:false}
```

说明：
- **本模块不主动加载** application.yaml；由宿主进程决定从哪里读配置并构造 `LlmConfig`
- application.yaml 作为**配置模板**留存：宿主是 Spring Boot 时可直接 import 这套 key；纯 Java 宿主可参考字段含义自己构造
- 模块内的稳定值（max-recommendations=6 / max-iterations=6 / timezone=Asia/Shanghai / mock-data 路径）**全部硬编码到 Java 常量**，不再通过 yaml 外露；调参需要时再加

## 11. 构建与依赖

```bash
# 安装本模块到本地仓
mvn -f examples/travel/agent-hotel/pom.xml -DskipTests install

# 跑单元测试（不调 LLM）
mvn -f examples/travel/agent-hotel/pom.xml test

# 手测：跑 SampleMain 走真实 LLM 链路（需要 LlmConfig 已配置好可用的 provider/apiBase/apiKey）
mvn -f examples/travel/agent-hotel/pom.xml exec:java \
    -Dexec.mainClass=com.huawei.ascend.examples.hotel.SampleMain \
    -Dexec.classpathScope=test

# 宿主进程的 pom.xml 引用
<dependency>
  <groupId>com.huawei.ascend</groupId>
  <artifactId>agent-hotel</artifactId>
  <version>0.1.0-SNAPSHOT</version>
</dependency>
```

宿主进程提供 LLM 配置 + 构造 `HotelPlanningAgent`，参见 §4.4。

> **provider 名约束**：openJiuwen 0.1.12 仅接受 `[OpenAI, OpenRouter, SiliconFlow, DashScope, InferenceAffinity, inference_affinity]`，**不接受 `deepseek`**。对接 OpenAI 协议兼容的内部网关（如 `api.bigmodel.dev.huawei.com/v1`）时，`LlmConfig.provider` 必须填 `OpenAI`，由 `apiBase` 决定实际指向的端点。
>
> **Windows 控制台中文编码**：JVM 内部字符串是 UTF-8，但 Windows cmd 默认 GBK，会把 prompt 里的中文打印成乱码。`SampleMain` 已在 `main()` 头部强制 `System.setOut/setErr` 用 `UTF-8 PrintStream` 包一层。如果用 IDE 直接 run，确认 IDE 控制台编码也是 UTF-8。

## 12. 开放问题（review 时一起拍）

1. **🟡 多 agent 共进程时的 openJiuwen 全局状态**：参考 agent-sdk 的写法 `Runner.resourceMgr().addTool(tool, agentId)`，`Runner` 看起来是**进程级静态**单例。多 agent（hotel / flight / train）在同一进程注册各自工具时：
   - 是按 `agentId` 隔离的吗？工具名同名（比如都叫 `search`）会冲突吗？
   - 多 agent 是否需要协调共享一个 `Runner`，还是各自隔离？
   - 实现期必须 spike 验证；如有冲突，要么约定 `agentId` + 工具名前缀（`hotel_search`、`flight_search`），要么改 `Runner` 用法
   - **2026-06-09 spike 进展（单 agent 子集）**：单进程下同一 `HotelPlanningAgent` 实例反复 `chat()` 工作正常；`HotelPlanningAgent` 已采用每实例自增 `agentId`（`hotel-planning-agent-<seq>`）+ `close()` 时 `removeTool(toolId, agentId, TagMatchStrategy.ALL, true)` 兜底；多 agent（hotel + flight + train）的工具名冲突 / `Runner` 共享语义仍待 flight、train 子智能体联调时再验
2. **✅ openJiuwen 0.1.12 API 兼容性**：spike 完成（见单元测试 + SampleMain 跑通）
   - `com.openjiuwen.core.foundation.tool.Tool` 接口：通过 `LocalFunction` + 自建 `ToolCard.builder()` 适配
   - `ReActAgent.getAbilityManager().add(tool.getCard())` + `Runner.resourceMgr().addTool(tool, agentId)` 双注册可用
   - `ReActAgentConfig.builder()...configureModelClient(...).build()` → `agent.configure(config)` 完成 LLM 绑定
   - `Runner.runAgent(agent, Map.of("query", "conversation_id"), null, null)` 返回 `Map<String,Object>`，取 `output` 字段为最终 markdown
   - provider 限定 `[OpenAI, OpenRouter, SiliconFlow, DashScope, InferenceAffinity, inference_affinity]`（见 §11）
3. **🟡 模型客户端共享**：多 agent 共进程时，是否需要把 LLM HTTP 客户端（连接池/超时/重试）做成进程级共享而非每 agent 一份？v1 实现先各自一份，观测后再优化
4. **✅ A2A wrapper 合入时机**：`agent-runtime` 已具备拉起单 agent 的能力（2026-06-10 验证），同步落地了 [`examples/travel/agent-hotel-a2a`](../agent-hotel-a2a/)：
   - `HotelAgentApplication`（Spring Boot 启动类，`scanBasePackages` 含 `com.huawei.ascend.runtime.boot`）
   - `HotelAgentHandler extends OpenJiuwenAgentRuntimeHandler`（薄壳，`execute()` 抽 query 后委派 `HotelPlanningAgent.chat(...)`）
   - 本模块的 `HotelPlanningAgent` 代码 0 修改
   - 启动后 `GET /.well-known/agent.json` 返 200，A2A endpoint 在 `/a2a`（端口当前由 runtime 收口到 8080）
5. **协议品牌中英文**：mock 数据全中文，要求 trip planner 在 NL 里也用中文品牌名
6. **多日多段行程**：本期只支持一段连续入住；"北京 2 天 + 上海 3 天"由 trip planner 拆成两次调用
7. **全部不符合差标的行为**：降级返回 + 标记 [不符合差标]，让上游决定（见 §8 prompt）
8. **trip planner 接口对齐**：当前 NL 拼装模板（§4.2）只是建议，trip planner 那侧的具体格式确认后可能调整 prompt 引导词

## 13. 验收标准

### 单元测试（不真调 LLM）
- [x] `MockHotelInventoryTest`：JSON 加载 31 × 10 = 310 条；cityName 归一化覆盖 "北京" / "北京市" / "Beijing"（8 个用例 ✅）
- [x] `HotelSearchToolTest`：过滤逻辑（maxPrice / minStar / brandWhitelist / keyword）；`compliancePassed` 计算正确（任一 policy 字段空 → 该项默认通过）；翻页（10 个用例 ✅）
- [x] `HotelDetailToolTest`：按 hotelId 查 rooms（3 个用例 ✅）
- [x] `SystemPromptBuilderTest`：`{today}` 注入 / 关键规则保留 / `Asia/Shanghai` 时区（3 个用例 ✅）
- [x] `LlmConfigTest`：`fromEnv()` 默认值 / record 访问器（2 个用例 ✅）

> 一键验证：`mvn -f examples/travel/agent-hotel/pom.xml test` — Tests run: **26**, Failures: 0, Errors: 0, Skipped: 0.

### 集成测试（真调 LLM，可通过 `-DskipITs` 跳过）
- [ ] `HotelPlanningAgentIT.testChat_typicalBusinessTrip`：给一段标准差旅 NL，验证返回 markdown 含 ≤6 条推荐 + 末尾汇总段；至少包含 "推荐："
- [ ] `HotelPlanningAgentIT.testChat_noCity_asksForClarification`：缺城市 → 模型应反问，不返回推荐

> IT 尚未落地（手测已通过 SampleMain 覆盖，可作为正式 IT 化的 baseline）。

### 手测
- [x] `examples/travel/agent-hotel/src/test/resources/sample-prompts.txt`：6 段样例 NL（典型差标 / 宽松差标 / 高端 / 经济 / 缺城市反问 / 跨调用 follow-up）
- [x] `examples/travel/agent-hotel/src/test/java/com/huawei/ascend/examples/hotel/SampleMain.java`：读 `sample-prompts.txt` 逐条调 `chat()`，分隔符化打印输出

#### SampleMain 真链路冒烟（2026-06-09）

跑法：

```bash
mvn -f examples/travel/agent-hotel/pom.xml exec:java \
    -Dexec.mainClass=com.huawei.ascend.examples.hotel.SampleMain \
    -Dexec.classpathScope=test
```

`LlmConfig` 当前指向内部 OpenAI 协议网关（`api.bigmodel.dev.huawei.com/v1` + `deepseek-v4-pro`，provider 必须填 `OpenAI`）。6 条 prompt 全部走通：

| # | 输入要点 | 行为 |
|---|---|---|
| 1 | 北京 / 4 星 / ≤800 / 全季·亚朵·希尔顿欢朋 / 国贸+会议室 | 抽出 `maxPricePerNight=800` `minStar=4` `brandWhitelist=[全季,亚朵,希尔顿欢朋]` `keyword=国贸,会议室`，工具命中 2 家，markdown 含汇总段 |
| 2 | 上海陆家嘴 / 未给差标 | 工具返回 2 家陆家嘴酒店；模型补充提示"未提供差标，建议补充以做合规校验" |
| 3 | 广州 / 5 星 / 含行政酒廊 | 命中 3 家高端，标准 markdown 表格输出 |
| 4 | 成都 / ≤300 / 经济连锁 | 推荐锦江之星 ¥238 等，全部标 `[符合差标]` |
| 5 | 缺城市 + 无日期 | 触发 prompt 规则 #1，模型主动反问城市/日期/差标，无空推荐 |
| 6 | "上一家详情？"（跨调用 follow-up） | 模型按设计（chat 间无状态）说明无上文记忆，请求补充酒店 ID — 与跨调用无状态契约一致 |

#### 已知本地坑

- **Windows 控制台中文乱码**：JVM 字符串是 UTF-8，但 cmd 默认 GBK。`SampleMain.main()` 顶部已强制 UTF-8 stdout/stderr；如换其他入口注意复用此手法。
- **VS Code 报 `cannot find symbol: LlmConfig / HotelPlanningAgent`**：`SampleMain` 与这两个类同包 `com.huawei.ascend.examples.hotel`，分别位于 `src/main/java` 和 `src/test/java`。同包类**不需要 import**（Java 不允许从自己包里 import），是 jdt.ls（VS Code Java 扩展）的工作区缓存陈旧。处理：
  1. 命令面板执行 `Java: Clean Java Language Server Workspace`（推荐），或 `Java: Restart Language Server`
  2. 再不行 `mvn -f examples/travel/agent-hotel/pom.xml clean compile test-compile` 后重启 IDE
  3. 不要让 IDE 自动加上 `import com.huawei.ascend.examples.hotel.LlmConfig;` 这种同包 import — 这会真的编译失败
  - 判别真假：`mvn -f examples/travel/agent-hotel/pom.xml test-compile` 能过 = IDE 误报；过不去 = 真的有问题
