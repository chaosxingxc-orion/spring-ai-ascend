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

`recognize` 接受 null 或空白字符串，并以 `EMPTY_INPUT` 返回；输入适配层不得为该场景抛出工具执行异常。输入先执行 Unicode NFC 规范化和首尾空白清理，规范化后超过 `maxUtteranceLength` 时返回 `INPUT_TOO_LONG`，禁止静默截断后继续识别。

Tool 和 Workflow 的业务输入固定为：

```json
{
  "utterance": "查询订单物流"
}
```

命中输出如下。为突出 envelope，示例中的 `target` 只展示部分字段；实际输出必须完整保留官方 A2A Java SDK `AgentCard` 的全部字段，包括 `version`、`capabilities`、`defaultInputModes`、`defaultOutputModes` 和 `supportedInterfaces`。

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
| `INPUT_TOO_LONG` | 规范化后的 `utterance` 超过配置上限 |
| `NO_ELIGIBLE_TARGET` | 初始化目录没有可评分候选 |
| `BELOW_SCORE_THRESHOLD` | top1 未达到绝对分数门限 |
| `INSUFFICIENT_MARGIN` | top1 与其他目标的分差不足 |
| `SCORER_UNAVAILABLE` | scorer 超时或调用失败 |
| `INVALID_SCORER_RESPONSE` | scorer 最终结果缺少 candidate ID、包含未知 ID 或非有限数值 |
| `RESULT_ENCODING_FAILED` | 已完成识别，但目标无法编码为约定输出结构 |

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
      "description": "待识别的用户请求",
      "maxLength": 4096
    }
  },
  "required": ["utterance"],
  "additionalProperties": false
}
```

Schema 中的 `maxLength` 不是硬编码常量，由同一个 `IntentRecognizerConfig.maxUtteranceLength` 生成。长度统一按 Unicode code point 计数；Java 实现不得直接用 UTF-16 code unit 数量代替，否则补充平面字符会造成 Schema 校验与 recognizer 校验不一致。

### 2.3 行为承诺

- **必须**：相同 recognizer 对 Tool 和 Workflow 输入产生相同的匹配结论。
- **必须**：命中 A2A 目标时返回官方 SDK `AgentCard` 结构，不转换为内部 AgentCard。
- **必须**：不同 Card 的分数冲突未通过 margin 时 fallback。
- **必须**：scorer 不可用时不得返回最接近的目标。
- **必须**：Card、Skill 文本只作为相关性模型数据，不解释为指令。
- **必须**：Tool 会把完整目标结构放入 LLM 上下文，因此上游除验证来源和签名外，还必须确认 Card 文本内容可被 Agent 信任；签名不等于内容安全。
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
    T snapshot(T target);

    String targetKey(T target);

    List<IntentCandidate> candidates(int targetIndex, T target);
}

public record IntentCandidate(
        int targetIndex,
        String candidateId,
        String document) {
}

public interface IntentResultEncoder<T> {
    JsonNode encode(IntentRecognitionResult<T> result);
}
```

`IntentCatalogCompiler` 必须先调用 `snapshot`，后续 `targetKey`、候选文档、hash 和命中返回值全部基于同一个快照。adapter 无法安全复制可变目标时必须在初始化阶段拒绝该目标，不能一边冻结候选文档、一边保留仍可变化的目标引用。

`IntentResultEncoder` 是 Tool 与 Workflow 共用的输出编码契约。Tool 直接返回其 `JsonNode`；Workflow 使用同一 `JsonNode` 转换为 `Map<String, Object>`，保证两个入口字段和值一致。编码器异常由适配层隔离并转换为固定的 `RESULT_ENCODING_FAILED` envelope，不得泄露半编码目标。

`IntentCatalog<T>` 保存：

- 初始化时目标的不可变快照；
- 扁平化的候选列表；
- `candidateId -> targetIndex` 映射；
- `catalogHash`；
- `candidateFormatVersion`。

A2A adapter 的 `targetKey` 使用 Card 名称、版本和 `supportedInterfaces` 规范化结果的 SHA-256 摘要。各字符串执行 NFC 和首尾空白清理，接口保持 A2A 声明顺序，并完整纳入 `protocolBinding/url/tenant/protocolVersion`；URL 不做可能改变语义的自行改写。接口 URL 只参与目标身份和 catalog hash，不进入语义文档或明文 trace。

初始化时对 `targetKey` 建立唯一索引：重复 key 直接抛初始化异常，不自动保留第一项，也不把重复 Card 当作两个目标参与 margin。`candidateId` 同样必须全目录唯一。

`catalogHash` 的输入是一个按 RFC 8785（JCS）序列化为 UTF-8 的 canonical JSON 对象，包含 `candidateFormatVersion`，以及按 `targetKey`、`candidateId` 升序排列的目标 key、候选 ID 和规范化候选文档；摘要算法固定为 SHA-256，小写十六进制输出。模型版本不进入 catalogHash，而是作为独立 trace 维度冻结。

候选 ID 必须唯一。A2A 适配器使用：

```text
{targetKey}:{skill.id}
```

候选 ID 不依赖目标输入顺序，因此同一逻辑目录换序后仍得到相同 ID 和 catalog hash；不同 Card 使用相同 Skill ID 也不会发生分数覆盖。`targetIndex` 仅作为 catalog 内部的紧凑索引用于聚合，不进入稳定身份。向 `Reranker` 传参时使用 `chunkId=candidateId` 的 `RetrievalResult`，不得直接使用候选文档字符串作为 Map key；这是因为 `StandardReranker` 优先使用 `chunkId` 作为结果 Map key。

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

不定义 `A2AAgentCard`、`A2AAgentSkill` 或协议字段镜像类。`A2AAgentCardIntentAdapter.snapshot()` 使用官方 builder 重建 Card 及其嵌套官方类型，并对 Card、Skill、capabilities extensions、extension params、安全要求、签名 header 中的所有 List/Map 做递归不可变复制。extension params 和签名 header 只接受 JSON 可表达的 null、字符串、布尔值、有限数字、List 和字符串键 Map；遇到其他可变对象或循环引用时初始化失败。只调用 `AgentCard.builder(card).build()` 不足以满足该契约，因为它只复制部分外层集合，仍会复用 Skill、capabilities 及嵌套集合引用。返回值始终是官方 SDK 类型。

`A2AAgentCardResultEncoder` 实现 `IntentResultEncoder<AgentCard>`，使用模块统一配置的 Jackson `ObjectMapper` 把完整官方 Card 编码到 `target`，但不能直接依赖 Jackson 对 record component 的默认命名。SDK `1.0.0.Final` 的 `AgentCardSignature.protectedHeader` 只带 Gson `@SerializedName("protected")`；编码器必须通过 Jackson MixIn 或等价显式映射输出协议字段 `protected`，不得输出 `protectedHeader`。不为此定义 AgentCard 镜像 DTO。

契约测试必须遍历当前 SDK `AgentCard` 的全部 record components，验证所有非 null 标准必填字段、可选字段和嵌套字段均被保留，并用 A2A v1.0.1 标准 JSON fixture 验证字段名，尤其是 `signatures[].protected`。SDK 升级新增字段时该测试必须先失败，防止静默丢字段。

### 3.4 A2A 资格过滤

`A2AEligibilityPolicy` 明确定义：

```java
public record A2AEligibilityPolicy(
        Set<String> supportedProtocolBindings,
        Set<String> supportedProtocolVersions,
        Set<String> supportedRequiredExtensionUris,
        Set<String> acceptedInputModes,
        A2ASecurityRequirementEvaluator securityEvaluator,
        A2AContentTrustEvaluator contentTrustEvaluator) {
}
```

- `securityEvaluator` 根据上游已经持有的凭证上下文判断 Card/Skill 的安全要求是否可满足；意图模块不读取凭证。
- `contentTrustEvaluator` 判断 Card 文本是否允许进入 Agent LLM 上下文。签名验证通过不能替代该判断。
- Set 中的协议 binding、version、extension URI 和 media type 均在初始化时规范化，比较规则固定为精确匹配。

`A2AAgentCardIntentAdapter` 在编译目录时执行静态资格检查：

1. Card 的 `name`、`description`、`version`、`skills`、`supportedInterfaces` 满足标准结构和本地长度限制。
2. 至少存在一个 protocol binding 和 protocol version 均被 `A2AEligibilityPolicy` 接受的接口。
3. Skill 的 `id`、`name`、`description` 非空，Card 内 Skill ID 唯一。
4. Skill `inputModes` 非空时覆盖 Card `defaultInputModes`；null 或空列表时继承 Card 默认值。
5. 只保留 input media type 命中 `acceptedInputModes` 的 Skill；第一版 policy 固定要求包含 `text/plain`。
6. Card 声明的每个 required extension URI 都必须位于 `supportedRequiredExtensionUris`。
7. Skill 级安全要求非空时覆盖 Card 级要求；最终要求必须通过 `securityEvaluator`。
8. Card 必须通过 `contentTrustEvaluator`，否则不得进入该 recognizer 的候选目录。Tool 与 Workflow 共享目录，不允许入口之间出现资格差异。
9. 不抓取 `documentationUrl`、`iconUrl`、interface URL 或扩展 URL。

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

语义字段只包括 Card 的 `name/description` 和 Skill 的 `name/description/tags/examples`。所有字符串先执行 Unicode NFC 规范化和首尾空白清理；tags 去重后按 Unicode 码点升序并以 `, ` 连接，examples 去重后保留 Card 声明顺序并逐行添加 `- ` 前缀；空列表对应的 section 保留并写入 `<none>`。该格式属于 `candidateFormatVersion` 契约，禁止实现自行使用 List.toString()。

长度统一按 NFC 规范化后的 Unicode code point 计数。任一字段、tags/examples 数量或最终文档超过配置上限时过滤对应 Skill 并记录初始化诊断，禁止静默截断后参与评分。Card 的 name/description 超限时过滤整张 Card。

### 3.6 评分、聚合与接受门

第一版使用 `agent-core-java` 的 `Reranker`：

```java
Map<String, Double> scores = reranker.rerankScores(
        utterance,
        candidateBatch,
        "判断用户请求是否应由给定的候选能力处理。",
        Map.of());
```

推荐由调用方注入连接本地 TEI 的 `StandardReranker`，模型使用 `Alibaba-NLP/gte-multilingual-reranker-base`。意图模块不创建 HTTP Client，不读取 `apiBase/apiKey`，不实现模型调用重试。

全量评分表示一次识别覆盖目录中的全部 candidate，不要求把全部 candidate 放进一个 HTTP payload。recognizer 按稳定的 candidateId 顺序切分为不超过 `maxBatchSize` 的批次，逐批调用同一个 Reranker 并合并结果；任一批失败则整次识别返回 fallback，禁止使用部分批次结果。

每批最终 Map 必须满足：key 集合与该批提交的 candidateId 完全相等，且每个 score 都是有限 double；缺少 ID、未知 ID、null、NaN 或 Infinity 返回 `INVALID_SCORER_RESPONSE`。`StandardReranker` 内部会把原始响应中遗漏的 index 转为 `0.0`，扩展模块无法再区分真实零分和原始漏项，因此不承诺检测 TEI 原始响应中的重复/遗漏 index；可见的 `0.0` 按正常低分进入接受门。

recognizer 使用信号量把同时进入 Reranker 的识别数限制为 `maxConcurrentRecognitions`。`StandardReranker` 的配置在 recognizer 构建后不得再修改；注入其他 Reranker 时，调用方必须保证其在该并发度下安全，无法保证时把并发度设为 1。

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

ToolCard 的 LLM 可见 `name` 默认固定为 `intent_recognition`，资源 `id` 必须全局唯一，二者不能使用同一个固定值：

```java
public record IntentRecognitionToolConfig(
        String toolId,
        String toolName) {
}
```

`toolId` 由调用方提供且不能为空，必须在 JVM 级 `Runner.resourceMgr()` 生命周期内全局唯一；重建 recognizer/Tool 时必须分配新 ID，例如 `intent_recognition_{agentInstanceId}_{UUID}`。不能只用 catalog hash 派生 ID，因为非语义 Card 字段变化时 catalog hash 可以不变，而 DeepAgent 遇到已存在 ID 会跳过新 Tool 注册。`toolName` 为空时使用 `intent_recognition`，使 LLM 看到稳定名称。同一个 Agent 内只允许注册一个相同 toolName。

Tool 构造器必须用 `ToolCard.builder()` 设置 `id=toolId`、`name=toolName`、用途描述和第 2.2 节生成的 `inputParams`，再传给 `Tool` 基类。配置对象、生成后的 ToolCard 和 Schema Map 均建立防御性副本，构造完成后不允许调用方修改；否则 LLM 可见 Schema 与 recognizer 输入限制可能漂移。

```java
public final class IntentRecognitionTool<T> extends Tool {
    private final IntentRecognizer<T> recognizer;
    private final IntentResultEncoder<T> encoder;

    @Override
    public Object invoke(Map<String, Object> inputs,
            Map<String, Object> kwargs) {
        String utterance = extractUtterance(inputs);
        return encodeOrFallback(encoder, recognizer.recognize(utterance));
    }

    @Override
    public Iterator<Object> stream(Map<String, Object> inputs,
            Map<String, Object> kwargs) {
        return List.<Object>of(invoke(inputs, kwargs)).iterator();
    }
}
```

Tool 返回 encoder 生成的 Jackson `JsonNode`，而不是普通 `Map`。原因是 `AbilityManager` 使用 `String.valueOf(result)` 构造 `ToolMessage`；`JsonNode.toString()` 可以保证进入 LLM 上下文的是合法 JSON。编码失败时 `encodeOrFallback` 使用不依赖目标 encoder 的固定 envelope 返回 `RESULT_ENCODING_FAILED`。

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
    private final IntentResultEncoder<T> encoder;

    @Override
    public Executable<?, ?> toExecutable() {
        return new IntentRecognitionExecutable<>(recognizer, encoder);
    }
}
```

```java
public final class IntentRecognitionExecutable<T>
        extends ComponentExecutable {
    private final IntentRecognizer<T> recognizer;
    private final IntentResultEncoder<T> encoder;

    @Override
    public Object invoke(Object inputs,
            NodeSessionApi session,
            ModelContext context) {
        String utterance = extractUtterance(inputs);
        JsonNode output = encodeOrFallback(encoder, recognizer.recognize(utterance));
        return OBJECT_MAPPER.convertValue(output, MAP_TYPE);
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

`IntentTraceListener` 在当前识别线程中同步接收不可变 trace。listener 异常必须捕获并记录 WARN，不改变已经得到的匹配结果；listener 实现若访问共享状态，必须自行保证线程安全。默认使用 no-op listener。

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
    │   │   ├── IntentRecognizers.java
    │   │   ├── IntentTargetAdapter.java
    │   │   ├── IntentResultEncoder.java
    │   │   ├── IntentCandidate.java
    │   │   ├── IntentRecognitionResult.java
    │   │   └── IntentRecognitionReason.java
    │   ├── catalog/
    │   │   ├── IntentCatalog.java
    │   │   └── IntentCatalogCompiler.java
    │   ├── reranker/
    │   │   ├── RerankerIntentRecognizer.java
    │   │   └── IntentRecognizerConfig.java
    │   ├── adapter/a2a/
    │   │   ├── A2AAgentCardIntentAdapter.java
    │   │   ├── A2AAgentCardResultEncoder.java
    │   │   ├── A2AAgentCardSnapshots.java
    │   │   ├── A2AEligibilityPolicy.java
    │   │   ├── A2ASecurityRequirementEvaluator.java
    │   │   └── A2AContentTrustEvaluator.java
    │   ├── tool/
    │   │   ├── IntentRecognitionTool.java
    │   │   └── IntentRecognitionToolConfig.java
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
| targetKey 或 candidateId 重复 | 初始化 | 抛初始化异常并报告冲突 key | 不创建 recognizer |
| 单张 Card/Skill 不合格 | 初始化 | 过滤并记录诊断 | 其他候选继续可用 |
| 全部候选被过滤 | 初始化/调用 | 允许建立空目录 | `NO_ELIGIBLE_TARGET` |
| utterance 无效 | 调用 | 不调用 scorer | `EMPTY_INPUT` |
| utterance 超长 | 调用 | 不截断、不调用 scorer | `INPUT_TOO_LONG` |
| top1 分数不足 | 调用 | fail closed | `BELOW_SCORE_THRESHOLD` |
| 不同目标分差不足 | 调用 | fail closed | `INSUFFICIENT_MARGIN` |
| 任一 reranker 批次超时或异常 | 调用 | 丢弃全部批次，不返回部分结果 | `SCORER_UNAVAILABLE` |
| scorer 最终 Map 的 ID 集合或数值非法 | 调用 | 丢弃本次结果 | `INVALID_SCORER_RESPONSE` |
| target 输出编码失败 | 输出适配 | 返回固定失败 envelope | `RESULT_ENCODING_FAILED` |
| Card 在外部发生变化 | 运行期 | 当前快照不变 | 新建实例后才生效 |

现有 `StandardReranker` 会把原始响应中缺失的候选分数初始化为 `0.0`。当生产阈值强制大于零时，该行为对该候选是失败关闭，但扩展模块无法区分“真实零分”和“服务漏返回”。该限制需要记录监控；若必须严格识别 TEI 原始响应的缺失或重复索引，应增强 `agent-core-java` 的 reranker 响应校验层，而不是在意图模块重写 HTTP 调用。

### 5.4 多意图边界

cross-encoder 不负责通用多意图解析。若一句请求同时强匹配不同目标，通常会因 margin 不足 fallback；但这不是完整的多意图检测保证。第一版不得宣称支持任意多意图拆分。

---

## 6. 配置与使用

### 6.1 recognizer 配置

```java
public record IntentRecognizerConfig(
        double scoreThreshold,
        double marginThreshold,
        int maxUtteranceLength,
        int maxTargets,
        int maxCandidates,
        int maxFieldLength,
        int maxCandidateLength,
        int maxTagsPerCandidate,
        int maxExamplesPerCandidate,
        int maxBatchSize,
        int maxConcurrentRecognitions,
        String candidateFormatVersion,
        String modelVersion) {
}
```

| 属性 | 是否必填 | 说明 |
|---|---|---|
| `scoreThreshold` | 是 | top1 最低接受分数，必须来自 calibration set |
| `marginThreshold` | 是 | 不同目标 top1/top2 最低分差 |
| `maxUtteranceLength` | 否，默认 4096 | 规范化后用户请求最大字符数，同时写入 Tool Schema `maxLength` |
| `maxTargets` | 否，默认 100 | 防止不受控目录扩张 |
| `maxCandidates` | 否，默认 1000 | 全目录 Skill 数上限 |
| `maxFieldLength` | 否，默认 4096 | 单个 Card/Skill 文本字段上限 |
| `maxCandidateLength` | 否，默认 16384 | 拼接后候选文档上限 |
| `maxTagsPerCandidate` | 否，默认 32 | 单个 Skill 允许的最大 tags 数 |
| `maxExamplesPerCandidate` | 否，默认 16 | 单个 Skill 允许的最大 examples 数 |
| `maxBatchSize` | 否，默认 128 | 单次提交给 Reranker 的最大候选数，全量目录可拆成多批 |
| `maxConcurrentRecognitions` | 否，默认 8 | 同时进入 Reranker 的识别数；非线程安全实现必须设为 1 |
| `candidateFormatVersion` | 是 | 候选模板版本，参与 catalog hash |
| `modelVersion` | 是 | 模型与量化版本标识，用于 trace 和验收冻结 |

除两个阈值外的默认值是第一版安全上限，不代表性能承诺；发布前通过目标环境负载测试调整。所有数值配置必须在 builder 构建时校验为正数，threshold 和 scorer 返回值还必须是有限 double。

### 6.2 初始化示例

```java
RerankerConfig rerankerConfig = new RerankerConfig();
rerankerConfig.setApiBase("http://127.0.0.1:8080");
rerankerConfig.setModelName("Alibaba-NLP/gte-multilingual-reranker-base");
rerankerConfig.setTimeout(3.0);

Reranker reranker = new StandardReranker(rerankerConfig);
A2AAgentCardResultEncoder resultEncoder = new A2AAgentCardResultEncoder();
String toolId = "intent_recognition_order-router_" + UUID.randomUUID();

IntentRecognizer<AgentCard> recognizer =
        IntentRecognizers.<AgentCard>builder()
                .targets(agentCards)
                .targetAdapter(new A2AAgentCardIntentAdapter(eligibilityPolicy))
                .reranker(reranker)
                .config(intentConfig)
                .build();

IntentRecognitionTool<AgentCard> tool = new IntentRecognitionTool<>(
        recognizer,
        resultEncoder,
        new IntentRecognitionToolConfig(toolId, "intent_recognition"));
```

这里的 `StandardReranker` 由调用方创建。意图模块只调用 `Reranker`，不实现或直接配置 HTTP Client。

### 6.3 Workflow 挂接示例

```java
workflow.addWorkflowComp(
        "intent",
        new IntentRecognitionComponent<>(recognizer, resultEncoder),
        Map.of("utterance", "${start.query}"));
```

---

## 7. 测试与验收

### 7.1 单元测试

| 测试组 | 必测内容 |
|---|---|
| 通用目录 | candidateId/targetKey 唯一、canonical hash、数量/长度限制、目标变更隔离 |
| 聚合 | 同 target 取 max、margin 只比较不同 target |
| 接受门 | 阈值等值边界、低分、冲突、单目标目录 |
| scorer 异常 | 分批合并、任一批失败、最终 Map 缺失/未知 ID、NaN、Infinity；不虚构原始响应可见性 |
| A2A 适配 | 官方类型、深快照、字段提取、media mode 继承/覆盖、资格和内容信任过滤 |
| 文档安全 | URL/provider/security/signature 不进入候选，文本规范化、确定性列表格式和超限过滤 |
| Tool | 固定 inputs、超长输入、忽略 kwargs、全局唯一资源 ID、重建不复用旧 Tool、合法 JsonNode、编码失败 fallback |
| Workflow | 输入映射、完整目标字段可寻址、与 Tool 语义一致 |
| 并发 | 并发上限、共享 recognizer 不改变目录、listener 异常隔离 |

### 7.2 框架集成测试

1. ReAct Agent 注册 Tool 后，模型生成 `intent_recognition` tool call，参数只含 `utterance`。
2. `AbilityManager` 注入 Session kwargs 时 Tool 正常工作且不写 Session。
3. DeepAgent 通过 `DeepAgentConfig.tools` 注册并调用相同 Tool。
4. 两个 Agent 使用不同 Card 目录注册同名但不同 ID 的 Tool，分别命中各自目录且不发生全局资源替换。
5. Workflow 通过 `addWorkflowComp` 挂接节点，并由下游节点读取三个输出字段。
6. Tool 与 Workflow 共享同一个 recognizer 和 encoder，对相同 utterance 返回字段完全一致的 AgentCard。
7. 反射遍历完整 AgentCard 的 record components（含 provider、capabilities、interfaces、security、extensions、signatures 和兼容字段），执行官方类型到 JsonNode/Map 的契约测试。
8. 使用本地 TEI 的 `StandardReranker` 做最小真实模型冒烟测试。

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
2. 定义通用 `IntentRecognizer<T>`、target snapshot adapter、result encoder 和不可变 catalog。
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
| Tool/Workflow 输出承载不同 | Tool 需要合法 JSON，Workflow 需要字段寻址 | 保持同一 encoder 和字段协议，分别使用 JsonNode/Map |
| 外部 Card 文本进入 LLM | 签名有效的 Card 仍可能包含恶意指令 | 共享目录只接受通过 contentTrustEvaluator 的 Card；需要不同信任边界时创建独立 recognizer，不在 Tool/Workflow 入口临时分叉 |

---

## 10. 参考资料

- A2A Protocol v1.0.1 Specification: <https://a2a-protocol.org/v1.0.1/specification/>
- Official A2A Java SDK: <https://github.com/a2aproject/a2a-java>
- mGTE, EMNLP 2024 Industry Track: <https://aclanthology.org/2024.emnlp-industry.103/>
- ToolRet, ACL 2025: <https://arxiv.org/abs/2503.01763>
- CLINC150 OOS: <https://aclanthology.org/D19-1131/>
