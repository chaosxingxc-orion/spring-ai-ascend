---
level: L2-LLD
module: agent-core
feature_type: functional
feature_id: Feat-Func-020
status: active
dependency:
  - ../../L1-High-Level-Design/agent-core/README.md
  - ../../L1-High-Level-Design/agent-core/logical.md
  - ../../L1-High-Level-Design/agent-core/physical.md
---

# 智能体感知与下游任务匹配调用 - 设计文档

> 需求名称：`FEAT-020 智能体感知与下游任务匹配调用`
> 目标模块：`agent-core-ext-java` 的 `intent` 扩展模块
> 参考基线：`agent-core-java 0.1.13`、A2A Protocol v1.0.1、A2A Java SDK `1.0.0.Final`
> 最后更新：2026-07-12

---

## 1. 概述

### 1.1 特性定位

本特性在独立的 `agent-core-ext-java` 仓库中提供通用意图识别能力：调用方在初始化阶段传入一组目标对象，模块从每个目标提取一个或多个候选意图文档；运行时根据用户请求对候选文档评分，返回唯一命中的原始目标或 fallback。

首个目标适配器面向标准 A2A v1.0.1 `AgentCard`。适配器从 `AgentCard.skills` 构造 Skill 级候选文档，按 AgentCard 聚合评分，并返回官方 A2A Java SDK 定义的 `AgentCard`。A2A AgentCard 只是通用意图模块支持的一种目标格式，不构成模块的核心领域模型。

本特性提供两个使用相同识别内核的框架适配入口：

1. `IntentRecognitionTool`：作为 OpenJiuwen `Tool` 注册到 ReAct Agent 或 DeepAgent。
2. `IntentRecognitionComponent`：作为 `ComponentComposable` 节点挂接到 Workflow。

### 1.2 可行性结论

| 评估项 | 结论 | 依据 |
|---|---|---|
| 通用意图内核 | 高 | 目录编译、目标聚合和接受门均可在扩展仓独立实现 |
| ReAct Agent Tool 接入 | 高 | `agent-core-java` 已提供 `Tool`、`ToolCard`、`AbilityManager` 和资源注册机制 |
| DeepAgent Tool 接入 | 高 | `DeepAgentConfig.tools` 已支持注入 `Tool` 实例 |
| Workflow Node 接入 | 高 | 已提供 `ComponentComposable`、`ComponentExecutable` 和 `Workflow.addWorkflowComp` |
| reranker 接入 | 高 | 已提供 `Reranker` 和批量 `/rerank` 的 `StandardReranker` |
| 算法 PoC | 高 | 可直接使用本地 TEI + GTE multilingual reranker 全量评分 |
| 未校准直接生产 | 不可行 | score/margin 没有跨目录通用阈值，OOS 风险不可证明 |
| 完成目录校准后生产 | 中高 | 主要风险转为 Card 描述质量、目录冲突、推理资源和验收数据质量 |

结论是：该方案无需修改 `agent-core-java` 即可在扩展仓落地。工程接口不是主要风险，生产可靠性取决于真实目录数据校准、独立验收和本地 reranker 的资源预算。

### 1.3 当前事实边界

- `agent-core-java` 仅作为能力依赖，不在该仓增加本特性代码。
- `agent-core-ext-java` 是独立 Maven 仓库，依赖 `agent-core-java` 并实现意图扩展。
- A2A Card 的发现、远端拉取、认证、签名验证、刷新和注册表治理由上游负责。
- 意图模块不访问 Agent URL，不维护远端 Card 生命周期，不调用被选中的 Agent。
- 初始化时建立不可变目标快照；运行时只接收 `utterance`。
- 第一版只做全量 cross-encoder 评分，不实现 embedding shortlist、意图拆分或 Agent 调用编排。

### 1.4 设计原则

1. **协议无关核心**：核心只识别“目标及其候选意图”，不依赖 A2A 类型。
2. **标准类型复用**：A2A 适配器直接使用官方 `org.a2aproject.sdk.spec.AgentCard`，不定义镜像 DTO。
3. **单一识别内核**：Tool 与 Workflow Component 共享同一个不可变 `IntentRecognizer`。
4. **初始化编译**：目标校验、候选文档构造和 catalog hash 在初始化阶段完成。
5. **运行时无状态**：单次识别不写 Session，不缓存用户请求或评分结果。
6. **失败关闭**：输入无效、分数不足、跨目标冲突或 scorer 失败均返回 fallback。
7. **阈值由数据决定**：不提供可被误认为生产安全的 score/margin 默认值。

### 1.5 子特性全景

| 子特性 | 职责 | 关键抽象 |
|---|---|---|
| 通用目标适配 | 从任意目标提取候选意图 | `IntentTargetAdapter<T>` |
| 目录编译 | 校验目标、生成候选、固化 hash | `IntentCatalogCompiler<T>` |
| 相关性评分 | 批量计算请求与候选的相关性 | `Reranker` |
| 聚合与接受门 | candidate -> target 聚合，score/margin 判定 | `RerankerIntentRecognizer<T>` |
| A2A 适配 | 从标准 AgentCard/AgentSkill 生成候选 | `A2AAgentCardIntentAdapter` |
| Agent 接入 | 将 recognizer 暴露为 Tool | `IntentRecognitionTool<T>` |
| Workflow 接入 | 将 recognizer 暴露为可挂接节点 | `IntentRecognitionComponent<T>` |
| 诊断观测 | 记录选择证据但不暴露模型分数给 Agent | `IntentRecognitionTrace` |

---

## 2. 特性规格

### 2.1 输入输出

通用 Java API：

```java
public interface IntentRecognizer<T> {
    IntentRecognitionResult<T> recognize(String utterance);
}

public record IntentRecognitionResult<T>(
        boolean matched,
        T target,
        IntentRecognitionReason reason) {
}
```

`recognize` 接受 null 或空白字符串，并以 `EMPTY_INPUT` 返回；输入适配层不得为该场景抛出工具执行异常。

Tool 和 Workflow 的业务输入固定为：

```json
{
  "utterance": "查询订单物流"
}
```

命中输出：

```json
{
  "matched": true,
  "target": {
    "name": "Order Agent",
    "description": "处理订单查询",
    "skills": [
      {
        "id": "query-logistics",
        "name": "查询订单物流",
        "description": "查询已有订单的物流状态",
        "tags": ["订单", "物流"]
      }
    ]
  },
  "reason": "MATCHED"
}
```

fallback 输出：

```json
{
  "matched": false,
  "target": null,
  "reason": "INSUFFICIENT_MARGIN"
}
```

固定 reason：

| reason | 含义 |
|---|---|
| `MATCHED` | 唯一目标通过 score 和 margin 接受门 |
| `EMPTY_INPUT` | `utterance` 缺失、不是字符串或去空白后为空 |
| `NO_ELIGIBLE_TARGET` | 初始化目录没有可评分候选 |
| `BELOW_SCORE_THRESHOLD` | top1 未达到绝对分数门限 |
| `INSUFFICIENT_MARGIN` | top1 与其他目标的分差不足 |
| `SCORER_UNAVAILABLE` | scorer 超时或调用失败 |
| `INVALID_SCORER_RESPONSE` | scorer 返回数量、索引或数值非法 |

### 2.2 Tool 原型兼容

`IntentRecognitionTool` 继承 `agent-core-java` 的 `Tool`：

```java
public Object invoke(
        Map<String, Object> inputs,
        Map<String, Object> kwargs) throws Exception;
```

- `inputs` 是对 LLM 暴露的固定业务参数，只允许 `utterance`。
- `kwargs` 是 `agent-core-java` 的可选框架上下文，不属于 Tool JSON Schema。
- `AbilityManager` 可能在 `kwargs` 中注入 `session`；意图 Tool 接受但不使用该参数。
- 直接调用 `tool.invoke(inputs)` 时，`Tool` 基类自动传入空 `kwargs`。

Tool 输入 Schema：

```json
{
  "type": "object",
  "properties": {
    "utterance": {
      "type": "string",
      "description": "待识别的用户请求"
    }
  },
  "required": ["utterance"],
  "additionalProperties": false
}
```

### 2.3 行为承诺

- **必须**：相同 recognizer 对 Tool 和 Workflow 输入产生相同的匹配结论。
- **必须**：命中 A2A 目标时返回官方 SDK `AgentCard` 结构，不转换为内部 AgentCard。
- **必须**：不同 Card 的分数冲突未通过 margin 时 fallback。
- **必须**：scorer 不可用时不得返回最接近的目标。
- **必须**：Card、Skill 文本只作为相关性模型数据，不解释为指令。
- **禁止**：运行时重新传入、更新或拉取 AgentCard。
- **禁止**：把 URL、provider、认证说明和签名文本加入候选语义文档。
- **允许**：多个 Tool/Component 实例共享同一个线程安全 recognizer。

### 2.4 显式排除

| 排除项 | 原因 | 演进方式 |
|---|---|---|
| Card 发现与刷新 | 属于上游注册表或 A2A Client 职责 | 上游构造新 recognizer 并原子替换 |
| Card 签名验证 | 来源信任由 Card Provider 负责 | 初始化前完成验证 |
| 目标 Agent 调用 | 本特性只选择目标 | 由下游调用编排模块消费返回 Card |
| 通用多意图拆分 | reranker 只能提供相关性和冲突信号 | 后续增加独立 multi-intent detector |
| embedding shortlist | 第一版目录规模未知，避免过早增加召回损失 | 全量评分不达标后单独设计 |
| 在线训练或微调 | 当前约束禁止训练 | 使用预训练模型和目录校准 |
| LLM Prompt 分类 | 与报告推荐的 cross-encoder 方案不同 | 可作为后续 `IntentScorer` 实现对比 |

---

## 3. 核心实现

### 3.1 总体架构

```text
外部目标提供方
  ├─ 获取、认证、刷新目标
  └─ 初始化时传入目标结构
             │
             ▼
IntentCatalogCompiler<T>
  ├─ IntentTargetAdapter<T> 提取候选
  ├─ 规范化、长度/数量校验
  ├─ 建立 targetIndex/candidateId 映射
  └─ 生成 catalogHash
             │
             ▼
不可变 IntentCatalog<T>
             │
             ▼
RerankerIntentRecognizer<T>
  ├─ Reranker 批量全量评分
  ├─ 同 target 取 max candidate score
  ├─ 比较不同 target 的 top1/top2
  └─ score + margin 接受门
        │                  │
        ▼                  ▼
IntentRecognitionTool   IntentRecognitionComponent
ReAct / DeepAgent       Workflow
```

Tool 和 Component 不保存目标集合，只持有 `IntentRecognizer<T>` 引用。目标快照由 recognizer 内部的不可变 catalog 统一持有，不承担发现、刷新和注册表职责。

### 3.2 通用候选模型

```java
public interface IntentTargetAdapter<T> {
    String targetKey(T target);

    List<IntentCandidate> candidates(int targetIndex, T target);
}

public record IntentCandidate(
        int targetIndex,
        String candidateId,
        String document) {
}
```

`IntentCatalog<T>` 保存：

- 初始化时目标的不可变快照；
- 扁平化的候选列表；
- `candidateId -> targetIndex` 映射；
- `catalogHash`；
- `candidateFormatVersion`。

A2A adapter 的 `targetKey` 使用 Card 名称、版本和 `supportedInterfaces` 规范化结果的 SHA-256 摘要。接口 URL 只参与目标身份和 catalog hash，不进入语义文档或明文 trace。

候选 ID 必须唯一。A2A 适配器使用：

```text
{targetIndex}:{skill.id}
```

即使不同 Card 使用相同 Skill ID，也不会发生分数覆盖。向 `Reranker` 传参时使用带唯一 `docId/chunkId` 的 `RetrievalResult`，不得直接使用候选文档字符串作为 Map key。

### 3.3 A2A 标准类型适配

扩展模块直接依赖：

```xml
<dependency>
  <groupId>org.a2aproject.sdk</groupId>
  <artifactId>a2a-java-sdk-spec</artifactId>
  <version>1.0.0.Final</version>
</dependency>
```

使用官方类型：

```java
org.a2aproject.sdk.spec.AgentCard
org.a2aproject.sdk.spec.AgentSkill
```

不定义 `A2AAgentCard`、`A2AAgentSkill` 或协议字段镜像类。初始化快照可通过官方 `AgentCard.builder(card).build()` 建立防御性副本，返回值仍为官方类型。

### 3.4 A2A 资格过滤

`A2AAgentCardIntentAdapter` 在编译目录时执行静态资格检查：

1. Card 的 `name`、`description`、`version`、`skills`、`supportedInterfaces` 满足标准结构和本地长度限制。
2. 至少存在一个由 `A2AEligibilityPolicy` 声明为兼容的接口。
3. Skill 的 `id`、`name`、`description` 非空，Card 内 Skill ID 唯一。
4. Skill `inputModes` 非空时覆盖 Card `defaultInputModes`；null 或空列表时继承 Card 默认值。
5. 只保留支持 `text/plain` 的 Skill。
6. required extension 和静态安全要求按上游传入的 eligibility policy 判断；无法满足时过滤。
7. 不抓取 `documentationUrl`、`iconUrl`、interface URL 或扩展 URL。

来源认证、Card 签名验证和当前凭证上下文由上游完成，不在本适配器重复实现。

### 3.5 候选文档

候选模板固定并版本化：

```text
[Target]
{card.name}

[Target scope]
{card.description}

[Intent]
{skill.name}

[Can handle]
{skill.description}

[Keywords]
{skill.tags}

[Examples]
{skill.examples}
```

语义字段只包括 Card 的 `name/description` 和 Skill 的 `name/description/tags/examples`。字段在拼接前执行 Unicode 规范化、单字段长度限制、列表元素数量限制和总文档长度限制。

### 3.6 评分、聚合与接受门

第一版使用 `agent-core-java` 的 `Reranker`：

```java
Map<String, Double> scores = reranker.rerankScores(
        utterance,
        candidates,
        "判断用户请求是否应由给定的候选能力处理。",
        Map.of());
```

推荐由调用方注入连接本地 TEI 的 `StandardReranker`，模型使用 `Alibaba-NLP/gte-multilingual-reranker-base`。意图模块不创建 HTTP Client，不读取 `apiBase/apiKey`，不实现模型调用重试。

聚合规则：

```text
targetScore(target) = max(candidateScore in target)
```

设不同目标最高两个分数为 `s1`、`s2`：

```text
scoreGate  = s1 >= scoreThreshold
marginGate = s1 - s2 >= marginThreshold
```

只有两个 gate 同时通过才返回 top1 目标。同一目标内多个 Skill 分数接近不构成冲突；margin 只比较不同目标。

目录只有一个 eligible target 时不存在 `s2`，margin gate 视为通过，但仍必须通过 score gate；不能因为目录只有一个目标而无条件命中。

`scoreThreshold` 和 `marginThreshold` 必须显式配置且大于零。阈值来自目标目录 calibration set，不提供生产默认值。

### 3.7 Agent Tool

ToolCard 的 `id/name` 固定为 `intent_recognition`，输入 Schema 使用第 2.2 节定义，避免 ReAct Agent 与 DeepAgent 暴露不同的工具名称。

```java
public final class IntentRecognitionTool<T> extends Tool {
    private final IntentRecognizer<T> recognizer;

    @Override
    public Object invoke(Map<String, Object> inputs,
            Map<String, Object> kwargs) {
        String utterance = extractUtterance(inputs);
        return toJsonNode(recognizer.recognize(utterance));
    }

    @Override
    public Iterator<Object> stream(Map<String, Object> inputs,
            Map<String, Object> kwargs) {
        return List.<Object>of(invoke(inputs, kwargs)).iterator();
    }
}
```

Tool 返回 Jackson `JsonNode`，而不是普通 `Map`。原因是 `AbilityManager` 使用 `String.valueOf(result)` 构造 `ToolMessage`；`JsonNode.toString()` 可以保证进入 LLM 上下文的是合法 JSON。`target` 由官方 A2A `AgentCard` 序列化产生。

ReAct Agent 注册：

```java
Runner.resourceMgr().addTool(tool, reactAgent.getCard().getId());
reactAgent.getAbilityManager().add(tool.getCard());
```

DeepAgent 注册：

```java
DeepAgentConfig config = DeepAgentConfig.builder()
        .tools(List.of(tool))
        .build();
```

### 3.8 Workflow Component

```java
public final class IntentRecognitionComponent<T>
        implements ComponentComposable {
    private final IntentRecognizer<T> recognizer;

    @Override
    public Executable<?, ?> toExecutable() {
        return new IntentRecognitionExecutable<>(recognizer);
    }
}
```

```java
public final class IntentRecognitionExecutable<T>
        extends ComponentExecutable {
    private final IntentRecognizer<T> recognizer;

    @Override
    public Object invoke(Object inputs,
            NodeSessionApi session,
            ModelContext context) {
        String utterance = extractUtterance(inputs);
        return toOutputMap(recognizer.recognize(utterance));
    }
}
```

Workflow 返回 `Map<String, Object>`，使表达式可以读取：

```text
${intent.matched}
${intent.target}
${intent.reason}
```

Tool 的 `JsonNode` 与 Workflow 的 Map 使用完全相同的字段协议，仅承载类型适配各自框架的消费方式。

### 3.9 诊断信息

内部 trace 至少包含：

```java
public record IntentRecognitionTrace(
        String targetKey,
        String candidateId,
        double topScore,
        double secondTargetScore,
        IntentRecognitionReason reason,
        String catalogHash,
        String modelVersion,
        String candidateFormatVersion) {
}
```

trace 通过日志或观察回调输出，不放入 Tool/Workflow 业务结果。模型原始相关性分数不能被 Agent 当作业务概率使用。

---

## 4. 代码结构

### 4.1 仓库与包结构

```text
agent-core-ext-java/
├── pom.xml
└── src/
    ├── main/java/com/openjiuwen/ext/intent/
    │   ├── api/
    │   │   ├── IntentRecognizer.java
    │   │   ├── IntentTargetAdapter.java
    │   │   ├── IntentCandidate.java
    │   │   ├── IntentRecognitionResult.java
    │   │   └── IntentRecognitionReason.java
    │   ├── catalog/
    │   │   ├── IntentCatalog.java
    │   │   ├── IntentCatalogCompiler.java
    │   │   └── IntentCatalogConfig.java
    │   ├── reranker/
    │   │   ├── RerankerIntentRecognizer.java
    │   │   └── IntentRecognizerConfig.java
    │   ├── adapter/a2a/
    │   │   ├── A2AAgentCardIntentAdapter.java
    │   │   └── A2AEligibilityPolicy.java
    │   ├── tool/
    │   │   └── IntentRecognitionTool.java
    │   ├── workflow/
    │   │   ├── IntentRecognitionComponent.java
    │   │   └── IntentRecognitionExecutable.java
    │   └── trace/
    │       ├── IntentRecognitionTrace.java
    │       └── IntentTraceListener.java
    └── test/java/com/openjiuwen/ext/intent/
```

### 4.2 Maven 依赖

```xml
<properties>
  <maven.compiler.release>17</maven.compiler.release>
  <agent-core.version>0.1.13</agent-core.version>
  <a2a-sdk.version>1.0.0.Final</a2a-sdk.version>
</properties>

<dependencies>
  <dependency>
    <groupId>com.openjiuwen</groupId>
    <artifactId>agent-core-java</artifactId>
    <version>${agent-core.version}</version>
  </dependency>
  <dependency>
    <groupId>org.a2aproject.sdk</groupId>
    <artifactId>a2a-java-sdk-spec</artifactId>
    <version>${a2a-sdk.version}</version>
  </dependency>
</dependencies>
```

扩展模块不得依赖 A2A Client 或 Server artifact，因为本特性不负责 Card 拉取或 Agent 调用。

### 4.3 依赖方向

```text
tool / workflow ──> intent api <── catalog <── reranker core
                         ▲              ▲
                         │              │
                  target adapter SPI    │
                         ▲              │
                         │              │
                    A2A adapter ────────┘

agent-core-ext-java ──> agent-core-java
agent-core-ext-java ──> official A2A Java SDK spec
```

- 通用 intent API 不依赖 A2A。
- A2A adapter 依赖 intent API 和官方 A2A spec。
- Tool/Workflow 适配依赖 agent-core-java 原型。
- Tool 和 Workflow 不互相依赖，也不各自实现评分逻辑。

---

## 5. 运行流程

### 5.1 初始化流程

```text
调用方取得受信目标集合
  │
  ▼
IntentRecognizers.builder()
  ├─ targets(agentCards)
  ├─ targetAdapter(a2aAdapter)
  ├─ reranker(existingReranker)
  └─ config(calibratedThresholds)
  │
  ▼
IntentCatalogCompiler
  ├─ 建立官方 AgentCard 防御性快照
  ├─ A2A eligibility 过滤
  ├─ Skill -> IntentCandidate
  ├─ 唯一 ID 与目录上限校验
  └─ catalogHash
  │
  ▼
共享的不可变 IntentRecognizer<AgentCard>
  ├─ IntentRecognitionTool
  └─ IntentRecognitionComponent
```

目录变化时，上游重新构建 recognizer/Tool/Component，并在应用层原子替换。单次 `recognize()` 不观察半更新目录。

### 5.2 调用流程

```text
{"utterance":"查询订单物流"}
  │
  ├─ 校验和规范化输入
  ├─ 全量候选构造 RetrievalResult
  ├─ Reranker 批量评分
  ├─ candidateId 关联分数
  ├─ targetIndex 维度 max 聚合
  ├─ top1 score gate
  ├─ top1-top2 margin gate
  └─ IntentRecognitionResult
       ├─ Tool -> JsonNode
       └─ Workflow -> Map
```

### 5.3 错误与降级

| 场景 | 阶段 | 行为 | 对外结果 |
|---|---|---|---|
| 配置缺失或阈值非法 | 初始化 | 抛初始化异常 | 不创建 recognizer |
| 目标数量或候选数量超限 | 初始化 | 抛初始化异常 | 不创建 recognizer |
| 单张 Card/Skill 不合格 | 初始化 | 过滤并记录诊断 | 其他候选继续可用 |
| 全部候选被过滤 | 初始化/调用 | 允许建立空目录 | `NO_ELIGIBLE_TARGET` |
| utterance 无效 | 调用 | 不调用 scorer | `EMPTY_INPUT` |
| top1 分数不足 | 调用 | fail closed | `BELOW_SCORE_THRESHOLD` |
| 不同目标分差不足 | 调用 | fail closed | `INSUFFICIENT_MARGIN` |
| reranker 超时或异常 | 调用 | 不返回近似目标 | `SCORER_UNAVAILABLE` |
| scorer 返回非法数值 | 调用 | 丢弃本次结果 | `INVALID_SCORER_RESPONSE` |
| Card 在外部发生变化 | 运行期 | 当前快照不变 | 新建实例后才生效 |

现有 `StandardReranker` 会把响应中缺失的候选分数初始化为 `0.0`。当生产阈值强制大于零时，该行为对缺失候选是失败关闭，但无法区分“真实零分”和“服务漏返回”。该限制需要记录监控；若必须严格识别缺失索引，应在 `agent-core-java` 的 reranker 响应校验层增强，而不是在意图模块重写 HTTP 调用。

### 5.4 多意图边界

cross-encoder 不负责通用多意图解析。若一句请求同时强匹配不同目标，通常会因 margin 不足 fallback；但这不是完整的多意图检测保证。第一版不得宣称支持任意多意图拆分。

---

## 6. 配置与使用

### 6.1 recognizer 配置

```java
public record IntentRecognizerConfig(
        double scoreThreshold,
        double marginThreshold,
        int maxTargets,
        int maxCandidates,
        int maxFieldLength,
        int maxCandidateLength,
        String candidateFormatVersion,
        String modelVersion) {
}
```

| 属性 | 是否必填 | 说明 |
|---|---|---|
| `scoreThreshold` | 是 | top1 最低接受分数，必须来自 calibration set |
| `marginThreshold` | 是 | 不同目标 top1/top2 最低分差 |
| `maxTargets` | 是 | 防止不受控目录扩张 |
| `maxCandidates` | 是 | 全目录 Skill 数上限 |
| `maxFieldLength` | 是 | 单个 Card/Skill 文本字段上限 |
| `maxCandidateLength` | 是 | 拼接后候选文档上限 |
| `candidateFormatVersion` | 是 | 候选模板版本，参与 catalog hash |
| `modelVersion` | 是 | 模型与量化版本标识，用于 trace 和验收冻结 |

### 6.2 初始化示例

```java
RerankerConfig rerankerConfig = new RerankerConfig();
rerankerConfig.setApiBase("http://127.0.0.1:8080");
rerankerConfig.setModelName("Alibaba-NLP/gte-multilingual-reranker-base");
rerankerConfig.setTimeout(3.0);

Reranker reranker = new StandardReranker(rerankerConfig);

IntentRecognizer<AgentCard> recognizer =
        IntentRecognizers.<AgentCard>builder()
                .targets(agentCards)
                .targetAdapter(new A2AAgentCardIntentAdapter(eligibilityPolicy))
                .reranker(reranker)
                .config(intentConfig)
                .build();
```

这里的 `StandardReranker` 由调用方创建。意图模块只调用 `Reranker`，不实现或直接配置 HTTP Client。

### 6.3 Workflow 挂接示例

```java
workflow.addWorkflowComp(
        "intent",
        new IntentRecognitionComponent<>(recognizer),
        Map.of("utterance", "${start.query}"),
        null);
```

---

## 7. 测试与验收

### 7.1 单元测试

| 测试组 | 必测内容 |
|---|---|
| 通用目录 | 候选唯一 ID、目录 hash、数量/长度限制、不可变快照 |
| 聚合 | 同 target 取 max、margin 只比较不同 target |
| 接受门 | 阈值等值边界、低分、冲突、单目标目录 |
| scorer 异常 | 超时、异常、缺失 ID、重复 ID、NaN、Infinity |
| A2A 适配 | 官方类型、字段提取、media mode 继承/覆盖、资格过滤 |
| 文档安全 | URL/provider/security/signature 不进入候选，文本规范化和截断 |
| Tool | 固定 inputs、忽略 kwargs、JsonNode 输出为合法 JSON |
| Workflow | 输入映射、字段可寻址、与 Tool 语义一致 |
| 并发 | 共享 recognizer 并发调用不改变目录和结果映射 |

### 7.2 框架集成测试

1. ReAct Agent 注册 Tool 后，模型生成 `intent_recognition` tool call，参数只含 `utterance`。
2. `AbilityManager` 注入 Session kwargs 时 Tool 正常工作且不写 Session。
3. DeepAgent 通过 `DeepAgentConfig.tools` 注册并调用相同 Tool。
4. Workflow 通过 `addWorkflowComp` 挂接节点，并由下游节点读取三个输出字段。
5. Tool 与 Workflow 共享同一个 recognizer，对相同 utterance 返回同一 AgentCard。
6. 使用本地 TEI 的 `StandardReranker` 做最小真实模型冒烟测试。

### 7.3 数据集覆盖

- 每个 Card 的正常请求；
- 跨 Card 近邻和 sibling-skill hard negatives；
- 同领域但未支持的 ID-OOS；
- 完全无关 OOS；
- 歧义、多意图、否定、极短、错别字；
- 中文、英文和中英混合；
- 多 Skill Card 和目录扩容场景。

数据分为互不重叠的 calibration set、acceptance set 和 shadow-production validation set。阈值只能在 calibration set 选择，最终结论必须来自 untouched acceptance set。

### 7.4 验收指标

| 指标 | 说明 |
|---|---|
| accepted-route error | 已返回目标中的错误比例 |
| coverage | 非 fallback 比例，防止 fallback-all 伪装高质量 |
| OOS false-accept rate | 不支持请求被错误接受的比例 |
| in-scope false-fallback rate | 可处理请求被错误拒绝的比例 |
| per-target precision/recall | 防止总体指标掩盖弱目标 |
| collision matrix | 定位经常相互混淆的目标 |
| P50/P95/P99 latency | 本地推理时延 |
| memory/CPU/GPU | 部署资源消耗 |

发布前必须定义 accepted-route error、OOS false-accept、coverage 和高风险目标的验收上限。Card 内容、目录、模型、tokenizer、量化或候选模板发生变化后重新验收。

---

## 8. 实施顺序

1. 创建独立 `agent-core-ext-java` Maven 仓和 `intent` 包结构。
2. 定义通用 `IntentRecognizer<T>`、target adapter 和不可变 catalog。
3. 使用伪造 `Reranker` 以测试驱动实现聚合、score/margin gate 和 fallback。
4. 接入官方 A2A SDK spec，实现 AgentCard eligibility 与候选文档构造。
5. 实现 `IntentRecognitionTool` 并验证 ReAct/DeepAgent 注册链。
6. 实现 `IntentRecognitionComponent/Executable` 并验证 Workflow 输出映射。
7. 接入本地 TEI + GTE，完成端到端模型冒烟。
8. 构建真实目录 calibration/acceptance 数据并选择阈值。
9. shadow 运行，验证冲突、OOS、coverage、延迟和资源。
10. 仅当全量评分不满足目标时，另立设计增加 Top-K 召回层。

---

## 9. 当前限制与风险

| 限制或风险 | 影响 | 控制措施 |
|---|---|---|
| AgentCard 描述宽泛或范围重叠 | 模型无法创造不存在的业务边界 | 改善 Card/Skill 描述，冲突时 fallback |
| 没有校准数据 | 阈值无生产意义 | 仅 shadow/PoC，不宣称生产准确率 |
| Card Skill 数量差异大 | 多 Skill Card 有更多偶然高分机会 | 按 Skill 数分层验收，不拍脑袋惩罚 |
| 全量 cross-encoder 延迟 | 大目录吞吐受限 | 先测量，必要时再增加 shortlist |
| TEI 或模型不可用 | 无法评分 | fail closed，不返回近似目标 |
| 多意图检测不完整 | 复杂请求可能未被显式拆分 | margin fallback；后续独立能力演进 |
| StandardReranker 缺失分数归零 | 无法区分真实零分和漏返回 | 阈值必须大于零并监控；必要时增强上游校验 |
| Tool/Workflow 输出承载不同 | Tool 需要合法 JSON，Workflow 需要字段寻址 | 保持同一字段协议，分别使用 JsonNode/Map |

---

## 10. 参考资料

- A2A Protocol v1.0.1 Specification: <https://a2a-protocol.org/v1.0.1/specification/>
- Official A2A Java SDK: <https://github.com/a2aproject/a2a-java>
- mGTE, EMNLP 2024 Industry Track: <https://aclanthology.org/2024.emnlp-industry.103/>
- ToolRet, ACL 2025: <https://arxiv.org/abs/2503.01763>
- CLINC150 OOS: <https://aclanthology.org/D19-1131/>
- 原始调研报告：`agent-core-java-capability-routing-implementation-plan.md`
