# 用 spring-ai-ascend 开发一个金融 Agent —— Step by Step

跟着做完,你会从零得到一个能跑、能对话、能本地验证的「定期存款顾问」Agent。
成品已在仓库里(`templates/DepositAdvisorAgent.java`),你可以照着敲一遍,或直接改它。

---

## 0. 你将做什么

一个约 30 行业务代码的存款顾问:客户问"10万存1年利息多少",它调用一个**利息计算工具**
按真实利率表算(数字不靠模型编),并做风险提示。它会用到平台的四个能力接缝:
**提示词 / 工具 / 合规护栏 / 人审**(本例只用前三个)。

心智模型(贯穿始终):

```
spring-ai-ascend 平台(agent-runtime,不改)         ← A2A 运行时,托管你的 agent
        ▲ 依赖
com.bank.financial.kit(工具箱,已给你)            ← 基类 + 合规/人审/审计/工具资产
        ▲ 继承
你的 Agent(就写这一层)                            ← 填:提示词 + 工具 + 护栏
```

> 原则:**平台一个字都不改**;你所有代码都在 `financial/` 目录里。

---

## 1. 环境准备(一次性)

```bash
# JDK 21(项目用它)
export JAVA_HOME=/Library/Java/JavaVirtualMachines/openjdk-21.jdk/Contents/Home

# 平台依赖装进本地 .m2(只写 .m2 和 target/,不碰平台源码;只需首次)
cd ~/Documents/Github/spring-ai-ascend
./mvnw -pl agent-runtime -am -DskipTests install

# 真实模型连接(本例用 DeepSeek;放进 ~/.zshrc 持久化,或每次内联)
export BANK_LLM_PROVIDER=openai
export BANK_LLM_API_BASE=https://api.deepseek.com
export BANK_LLM_MODEL=deepseek-chat
export BANK_LLM_API_KEY=sk-你的key
```

> 坑:改了 `~/.zshrc` 要**开新终端或 `source ~/.zshrc`** 才生效;不确定就用"内联"方式(见第 6 步)。

---

## 2. Step 1 — (可选)先写工具:让 agent 取真实数据

金融 agent 的铁律:**金额、利率、余额必须来自工具,不能让模型编**。工具有两种:

- `HttpTool.toLocalFunction(...)` — 调你行的后端 HTTP API(生产用这个)。
- `LocalTool.of(...)` — 进程内 Java 逻辑(本例用这个:一个确定性利息计算器)。

本例的工具(写在 Handler 里):
```java
LocalTool.of("quote_deposit", "按期限测算定期存款利率与到期利息",
    Schemas.object()
        .required("principal", "number", "本金(元)")
        .required("termMonths", "number", "存期(月):3/6/12/24/36")
        .build(),
    inputs -> {
        double principal = ((Number) inputs.get("principal")).doubleValue();
        int months = ((Number) inputs.get("termMonths")).intValue();
        double rate = /* 查利率表 */ 0.015;
        double interest = principal * rate * (months / 12.0);
        return Map.of("annualRate", rate, "estimatedInterest", interest,
                      "maturityAmount", principal + interest);
    });
```
关键点:`Schemas.object().required(...)` 声明入参(LLM 据此知道怎么调);返回 `Map`,
里面的数字就是"事实层"。

---

## 3. Step 2 — 写 Handler(核心,继承基类填 4 个方法)

新建 `financial/src/main/java/com/bank/financial/templates/DepositAdvisorAgent.java`:

```java
public final class DepositAdvisorAgent extends AbstractFinancialAgentHandler {
    public static final String ID = "deposit-advisor";
    private static final GuardrailBackend SCREEN = new KeywordScreeningBackend();

    public DepositAdvisorAgent(ModelConnection model) { super(ID, model); }

    @Override protected String description() { return "定期存款顾问"; }

    @Override protected String systemPrompt() {           // ① 领域大脑 + 边界
        return "你是银行定期存款顾问。询问存款时必须调用 quote_deposit 工具按真实利率测算,"
             + "绝不自己编利率或金额。做风险提示:存款受存款保险保障(50万限额)。";
    }

    @Override protected List<LocalFunction> tools() {     // ② 工具(见 Step 1)
        return List.of(/* quote_deposit 的 LocalTool.of(...) */);
    }

    @Override protected List<AgentRail> complianceRails(AgentExecutionContext ctx) {  // ③ 合规护栏
        return List.of(new ComplianceRail(SCREEN, RiskLevel.HIGH,
                ctx.lastUserText(), ctx.getScope().tenantId()));
    }
    // ④ 人审 approvalRails(...) —— 本例只读,不需要;动账类才加(见 CreditCardServicingAgent)
}
```

你只填了"它是谁、用什么工具、合规怎么管"——建 ReActAgent、接模型、接 A2A、流式、生命周期,
基类和平台全包了。完整版见仓库 `DepositAdvisorAgent.java`。

---

## 4. Step 3 — 注册到 Playground(一行,便于本地试跑)

编辑 `financial/.../playground/PlaygroundCatalog.java`,在 `static { ... }` 里加:
```java
JAVA.put(DepositAdvisorAgent.ID, DepositAdvisorAgent::create);
```
(可选)再给它一个 `--demo` 脚本,确定性地调一次工具:
```java
case "deposit-advisor" -> new String[] {
        "quote_deposit", "{\"principal\":100000,\"termMonths\":12}"};
```

---

## 5. Step 4 — 本地跑(从确定性到真实模型,三档)

```bash
# (a) 确定性验证:不接模型,直接看工具算得对不对(每次结果一样)
./financial/play.sh deposit-advisor --demo
#  期望:📦 工具返回 estimatedInterest=1500.0, maturityAmount=101500.0  (10万×1.5%×1年)

# (b) 接线验证:mock 模型,确认 agent 能起、护栏在
./financial/play.sh deposit-advisor --mock

# (c) 真实对话:接 DeepSeek,自由提问
./financial/play.sh deposit-advisor
#  然后输入:我有10万,存1年利息多少?
```

读输出的逐轮 trace:`👤用户 → 🧠模型 → 🔧请求工具 → 📦工具返回 → 💬模型输出 → ✅最终`。
被合规拦截显示 `✅ 最终[blocked]`,需人审显示 `⏸ 暂停`。

---

## 6. 内联运行(env 没生效时的稳妥方式)

```bash
BANK_LLM_PROVIDER=openai BANK_LLM_API_BASE=https://api.deepseek.com \
BANK_LLM_MODEL=deepseek-chat BANK_LLM_API_KEY=sk-你的key \
./financial/play.sh deposit-advisor
```

---

## 7. Step 5 — 迭代

改提示词 / 调利率表 / 加一个工具 → 重新 `./financial/play.sh deposit-advisor`
(play.sh 每次会自动 `compile`,改完直接跑)。

---

## 8. Step 6 —(可选)上线为 A2A 服务,供手机银行/前端调用

playground 是开发回路;要被前端调用,把 agent 暴露成服务:

1. 写一个 `@Bean`(参考 `agent/FinancialAdvisorAgentConfiguration.java`):
   ```java
   @Bean OpenJiuwenAgentRuntimeHandler depositAdvisor() {
       return new DepositAdvisorAgent(ModelConnection.fromEnv());
   }
   ```
2. 启动服务并调用:
   ```bash
   ./mvnw -f financial/pom.xml spring-boot:run
   # agent card → http://localhost:8080/.well-known/agent-card.json
   # 调用      → POST http://localhost:8080/a2a   (A2A JSON-RPC,生产需前置鉴权网关注入 X-Tenant-Id)
   ```

---

## 速查

| 你要做的 | 怎么做 |
|---|---|
| 新建一个 agent | 继承 `AbstractFinancialAgentHandler`,填 `description/systemPrompt/tools/complianceRails[/approvalRails]` |
| 让 agent 取真实数据 | `tools()` 里返回 `HttpTool.toLocalFunction(...)`(后端)或 `LocalTool.of(...)`(进程内) |
| 声明工具入参 | `Schemas.object().required(名,类型,说明).build()` |
| 合规拦截 | `complianceRails` 返回 `ComplianceRail(backend, RiskLevel.HIGH, ctx.lastUserText(), tenant)` |
| 敏感动作人审 | `approvalRails` 返回 `RuleBasedApprovalRail(规则)`,详见 `CreditCardServicingAgent` |
| 本地试跑 | `./financial/play.sh <id> [--demo|--mock]` |
| 上线服务 | 加 `@Bean` + `./mvnw -f financial/pom.xml spring-boot:run` |

更多模板见同目录 `AGENT_CATALOG.cn.md`(信用卡/贷款/AML/零售理财/私行)。
