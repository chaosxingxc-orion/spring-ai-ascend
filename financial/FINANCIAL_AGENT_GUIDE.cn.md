# 金融智能体开发指南(代码级)

> 面向银行客户开发者:在 `financial/` 工作区里,基于 spring-ai-ascend 平台开发金融行业 agent 的**原则 + 可复用资产 + 装配配方**。
> 配套基础原则见 [README.md](./README.md)(不可破坏平台结构)。

---

## 0. 一句话

**把横切关注点(合规、人审、审计、防幻觉、租户)固化成 `com.bank.financial.kit` 里的可复用 Java 资产;业务开发(会 Java)继承一个基类、填几个方法,就有一个能跑、能服务、能本地调试的金融 agent。**

---

## 最快上手(Java + 本地可视化调试)

**1) 写一个 handler**(继承 `AbstractFinancialAgentHandler`,只填"领域大脑 + 装哪些资产"):
```java
public final class CreditCardAgentHandler extends AbstractFinancialAgentHandler {
    private static final GuardrailBackend SCREEN = new KeywordScreeningBackend();

    public CreditCardAgentHandler(ModelConnection model) { super("credit-card-agent", model); }

    @Override protected String description() { return "信用卡智能客服"; }
    @Override protected String systemPrompt() {
        return "你是信用卡客服…涉及金额必须查后端工具,绝不编造数字。";
    }
    // 后端工具:声明式 HTTP,自动取数(防幻觉)
    @Override protected List<LocalFunction> tools() {
        return List.of(HttpTool.toLocalFunction(new ToolDef(
            "query_bill", "查询账单", "GET",
            System.getenv("BANK_API_BASE") + "/cards/{cardId}/bill",
            Map.of(), Map.of("type","object","properties",Map.of("cardId",Map.of("type","string"))))));
    }
    // 合规:HIGH+ 拦截、fail-closed
    @Override protected List<AgentRail> complianceRails(AgentExecutionContext ctx) {
        return List.of(new ComplianceRail(SCREEN, RiskLevel.HIGH, ctx.lastUserText(), ctx.getScope().tenantId()));
    }
    // 人审:大额还款需复核
    @Override protected List<AgentRail> approvalRails(AgentExecutionContext ctx) {
        return List.of(new RuleBasedApprovalRail(List.of(
            new ApprovalRule(List.of("repay"), "大额还款需复核", 50000.0, "amount"))));
    }
}
```
上线:一个 `@Bean` 返回它即可,运行时自动经 A2A 暴露(见 [`FinancialAdvisorAgentConfiguration`](./src/main/java/com/bank/financial/agent/FinancialAdvisorAgentConfiguration.java))。

**2) 本地一条命令调试**(`--mock` 免 key,带可读 trace)。先把你的 handler 在 [`PlaygroundCatalog`](./src/main/java/com/bank/financial/playground/PlaygroundCatalog.java) 注册一行:
```java
JAVA.put("credit-card-agent", () -> new CreditCardAgentHandler(ModelConnection.fromEnv()));
```
然后:
```bash
./financial/play.sh credit-card-agent --mock          # 不接真模型也能把合规/工具/审批跑通
BANK_LLM_API_KEY=sk-... ./financial/play.sh credit-card-agent   # 接真模型
```
输出是一条干净的逐轮 trace:
```
▶ playground  agent=credit-card-agent  [mock LLM]
👤 用户: 帮我查账单
  🧠 调用模型…
  ⚙️  执行工具 query_bill: {"cardId":"6225"}
  📦 工具返回: {...}
  💬 模型输出: 您本期账单...
✅ 最终[answer]: ...
```
被合规拦截显示 `✅ 最终[blocked]: …`;需人审显示 `⏸ 暂停:等待人工审批`。

> **可选:声明式 YAML(少量样板的简单 agent)。** 不想写 handler 类时,可在 `agents/<id>.yaml` 里声明 prompt/model/compliance/tools/approvals(见 [`agents/credit-card-advisor.yaml`](./src/main/resources/agents/credit-card-advisor.yaml)),放进去即被运行时托管、也能 `play.sh <id>` 调试。这是 Java 路径的便捷糖,不是主路径——逻辑稍复杂就回到上面的 Java handler。

---

## 1. 五条工程原则(已固化在 kit 里,不要绕过)

| 原则 | 为什么 | 代码落点 |
|---|---|---|
| **数字不由模型生成** | 余额/利率/金额错一位是金融事故 | system prompt 明令 + 后端工具取数(见 §4 工具) |
| **合规前置、可设阈值、fail-closed** | 核心护栏对任意风险都拦、且要主动触发 | `kit.compliance.ComplianceRail`(阈值 + 出错即拦) |
| **不可逆动作必须人审** | 动钱/改额/减免不能 agent 单独决定 | `kit.approval.SensitiveActionApprovalRail` |
| **全程审计留痕** | 监管可追溯;运行时已出 trajectory+OTel,领域事件另记 | `kit.audit.FinancialAudit` + 平台 trajectory |
| **租户隔离** | 多机构/多条线数据不串 | `context.getScope().tenantId()` 贯穿;平台前置鉴权网关 |

---

## 2. kit 资产清单(`com.bank.financial.kit`)

| 资产 | 类型 | 作用 | 你要做什么 |
|---|---|---|---|
| `ModelConnection` | record | LLM 连接参数一次声明 | 从 `@Value` 注入构造 |
| `AbstractFinancialAgentHandler` | 抽象基类 | 去样板:建卡/建模型/装 rails;暴露两个 seam | 继承它,实现 `description()`/`systemPrompt()`,按需 override `complianceRails`/`approvalRails`/`maxIterations`/`temperature` |
| `compliance.ComplianceRail` | AgentRail | 首次模型调用前筛查用户输入,达阈值即 `requestForceFinish` 拒绝 | 直接用,传入 backend + 阈值 |
| `compliance.KeywordScreeningBackend` | GuardrailBackend | **起步占位**的关键词/制裁词筛查 | 生产替换成真实 AML/适当性筛查服务 |
| `approval.SensitiveActionApprovalRail` | BaseInterruptRail | 敏感工具前人审暂停/续跑 | 继承它,实现 `requiresApproval` + `buildApprovalRequest` |
| `audit.FinancialAudit` | 工具类 | 领域审计事件(脱敏) | 在关键节点调 `record(...)` |
| `spec.AgentDefinition` + `AgentDefinitionLoader` | 声明式 | YAML → agent 定义(`${ENV}`/`${ENV:default}` 插值) | 写 YAML,不碰 |
| `spec.DeclarativeAgentFactory` | 工厂 | 由定义建 ReActAgent + 注册工具 + 装合规/审批 rail | 被复用,不碰 |
| `tool.HttpTool` | 工具 | 声明式 HTTP 后端调用 → 自动工具(防幻觉取数) | YAML `http:` 块 |
| `approval.RuleBasedApprovalRail` | rail | YAML `approvals:` 规则驱动的人审(金额阈值等) | 写规则 |
| `DeclarativeFinancialAgentHandler` + `agent.DeclarativeAgentsConfiguration` | 运行时 | 每个 YAML 自动注册成被托管的 A2A handler | 放文件即可 |
| `playground.Playground` / `TraceRail` / `MockModel` + `play.sh` | 调试 | 本地 CLI + 可读 trace + 免 key mock | `./financial/play.sh <id> --mock` |

> 这些都通过平台**官方扩展点**接入(`OpenJiuwenAgentRuntimeHandler.openJiuwenRails` / `createOpenJiuwenAgent`),`financial/` 不改平台一行。

---

## 3. 业务全景 → 资产装配表

把银行 agent 场景按"能做什么"分档,每档需要哪些 kit 资产 + openJiuwen 原语:

| 档位 | 代表场景 | 必备资产 | 用到的 openJiuwen 原语 |
|---|---|---|---|
| **L1 知识问答** | 员工/客服 copilot、制度问答 | Abstract基类 + (可选)ComplianceRail | ReActAgent + 知识检索(RAG) |
| **L2 只读查询** | 账户助手、账单/流水查询 | + 后端取数工具 + FinancialAudit | `core.foundation.tool.service_api` 工具调用 + 租户 |
| **L3 多轮办理** | 贷款受理、开户尽调 | + 工作流/检查点 | 工作流/pregel + `CheckpointerFactory` 续跑 |
| **L4 动账/敏感** | 转账、分期、限额调整 | + **SensitiveActionApprovalRail** + Redis 检查点 | `BaseInterruptRail` 人审 + 审计 |
| **L5 风控合规** | AML 研判、SAR 起草、适当性 | + **ComplianceRail(真实 backend)** + 法规 RAG | `GuardrailBackend` + RAG |

横切档(每档都要):**租户隔离 + 审计留痕 + 防幻觉**。

---

## 4. 配方:新建一个金融 agent

### 4.1 最小(L1/L2)—— 看现成例子
[`agent/FinancialAdvisorAgentConfiguration.java`](./src/main/java/com/bank/financial/agent/FinancialAdvisorAgentConfiguration.java):继承 `AbstractFinancialAgentHandler`,给 `systemPrompt()` + `description()`,发布成 `@Bean`。就这些。

### 4.2 加合规筛查(L5 / 任何对客)
override `complianceRails`,传入你的 backend 与阈值:
```java
@Override
protected List<AgentRail> complianceRails(AgentExecutionContext ctx) {
    return List.of(new ComplianceRail(
            myAmlBackend,            // 实现 GuardrailBackend:接真实筛查服务
            RiskLevel.HIGH,          // 达到 HIGH 及以上才拦(低风险放行/观测)
            ctx.lastUserText(),      // 平台已解析的用户输入
            ctx.getScope().tenantId()));
}
```

### 4.3 加敏感动作人审(L4)
1) 写一个审批 rail:
```java
public final class TransferApprovalRail extends SensitiveActionApprovalRail {
    public TransferApprovalRail() { super(List.of("transfer_funds")); } // 哪些工具敏感

    @Override protected boolean requiresApproval(AgentCallbackContext ctx, ToolCall toolCall) {
        // 解析 toolCall 参数,按业务阈值/规则判断是否需要人审
        return true;
    }
    @Override protected InterruptRequest buildApprovalRequest(AgentCallbackContext ctx, ToolCall toolCall) {
        return InterruptRequest.builder()
                .interruptId(toolCall.getId())
                .message("需要审批人确认该笔操作")
                .context(Map.of(/* 脱敏后的关键信息 */))
                .build();
    }
}
```
2) 在 handler 里 `approvalRails(...)` 返回它。
3) 配持久检查点(暂停跨进程/跨节点可恢复):
```java
CheckpointerFactory.setDefaultCheckpointer(
        CheckpointerFactory.create("redis", Map.of("connection", Map.of("url", "redis://..."))));
```
4) 续跑:同一 `conversation_id`,把审批决定作为输入再发一次(`InteractiveInput().update(toolCallId, "approve")`)。

### 4.4 接后端取数工具(防幻觉,L2+)
数字必须来自后端。把后端 API 包成 openJiuwen 工具(`core.foundation.tool` / `service_api`),在 handler 的 `installRuntimeTools(...)` 或建 agent 时 `agent.getAbilityManager().add(card)` 注册;system prompt 里禁止编造数字。

---

## 5. 约定

- **包结构**:`com.bank.financial.kit.*` 放可复用资产;`com.bank.financial.agent.*` 放每个业务 agent;`com.bank.financial.tool.*` 放后端工具。
- **命名**:agent id 用 kebab-case(`loan-intake-agent`);敏感工具名集中常量化。
- **租户**:任何取数/审计都带 `ctx.getScope().tenantId()`;绝不信任客户端自报租户(平台靠前置鉴权网关)。
- **审计**:动账/审批/拒绝节点必调 `FinancialAudit.record(...)`,detail 脱敏。
- **fail-closed**:筛查/鉴权出错一律按"拒绝"处理。
- **不改平台**:需要平台能力缺失时,先在 kit 里适配;真要动平台,走平台自己的 PR + 治理,不在本目录改。

---

## 6. 构建

```bash
# 平台装一次到 .m2(只写 .m2/target,不碰平台源码)
JAVA_HOME=/Library/Java/JavaVirtualMachines/openjdk-21.jdk/Contents/Home ./mvnw -pl agent-runtime -am -DskipTests install
# 独立构建本工作区
JAVA_HOME=/Library/Java/JavaVirtualMachines/openjdk-21.jdk/Contents/Home ./mvnw -f financial/pom.xml -DskipTests package
```

底层原语的精确位置与机制见持久记忆 `openjiuwen-agent-core-source`,以及框架源码 `~/Documents/Github/agent-core-java`(分支 0.1.12)。

---

## Token 经济(开发时就该想的成本)

ReAct 每一轮都会把"系统提示 + 全部历史 + 工具结果"重发给模型,所以**调用次数 × 上下文大小**直接等于钱。开发 agent 时遵守几条:

1. **工具一次返全,别让模型多轮调**:工具设计成一次返回所需全部(如 `recommend_products` 不传 category 即返回全部品类),并在提示词里明确"一次性调用,不要按品类多次调用"。一个反例就是私行 agent 曾按品类调 5 次 = 5 个模型往返。
2. **工具输出瘦身**:只返模型真正需要的字段;**重复的免责声明/固定话术放进提示词说一次**,不要塞进每个工具结果(它会被反复重发)。
3. **right-size `maxIterations`**:按 agent 真实需要设(存款顾问 3、推荐类 5、AML 6),别一律留 8 给"跑飞"留空间。
4. **`maxTokens` 封顶输出**(基类默认 1024)。
5. **模型分层(已接线)**:简单 agent 用便宜模型,复杂的用强模型。注册表已给每个 agent 标了档位——`fast`(存款顾问/理财问答/信用卡)、`smart`(零售理财/私行/贷款/AML)。设 `BANK_LLM_MODEL_FAST`、`BANK_LLM_MODEL_SMART` 即生效(如 DeepSeek:fast=`deepseek-chat`、smart=`deepseek-reasoner`);**不设则都回落到 `BANK_LLM_MODEL`,行为不变**。
6. **上下文窗口**:长多轮对话用 openJiuwen `ReActAgentConfig.configureContextEngine(maxMsgs, windowRounds, ...)` 限历史(避免上下文无限增长)。
7. **利用 provider 前缀缓存**:DeepSeek/OpenAI 对稳定前缀(系统提示)有缓存折扣——**保持系统提示稳定、把易变内容放后面**,自动省输入 token。
8. **先量后优**:从 trajectory `Usage` 取 token 数做 metric(见 `OPERATIONS.cn.md` §7),按 agent 看成本再针对性优化。
