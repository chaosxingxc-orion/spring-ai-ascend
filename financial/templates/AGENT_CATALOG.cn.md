# 银行 Agent 参考模板目录

> 借鉴 IBM IFW(Information FrameWork)"预置行业模型、覆盖大部分需求、其余定制"的思路:
> 这里给会 Java 的银行开发一组**可直接跑、可复制改**的金融 agent 模板。每个模板 =
> 一个继承 `AbstractFinancialAgentHandler` 的 Java 类,演示该业务域的典型接线
> (提示词 + 后端工具 + 合规护栏 + 人审)。源码在
> [`src/main/java/com/bank/financial/templates/`](./src/main/java/com/bank/financial/templates)。

## 怎么用

```bash
# 本地试跑(免 key,带可读 trace)
./financial/play.sh credit-card-servicing --mock
./financial/play.sh loan-intake --mock
./financial/play.sh aml-screening --mock
./financial/play.sh financial-advisor-agent --mock

# 演示「人审暂停 → 批准/拒绝续跑」(--demo 让 mock 主动请求被门控的工具)
./financial/play.sh credit-card-servicing --demo    # 然后输入「批准」或「拒绝」
./financial/play.sh loan-intake --demo
./financial/play.sh aml-screening --demo

# 接真实模型
BANK_LLM_API_KEY=sk-... ./financial/play.sh credit-card-servicing
```

落地:复制模板类 → 改 ID/提示词/工具 URL → 二选一上线:
1. 加一个 `@Bean OpenJiuwenAgentRuntimeHandler` 返回它(运行时自动经 A2A 暴露);
2. 或先在 `PlaygroundCatalog` 注册一行,本地调顺了再上线。

## 模板清单(按 BIAN 服务域归类)

| 模板(ID) | BIAN 服务域 | 面向 | 演示的能力 | 后端工具 | 人审 |
|---|---|---|---|---|---|
| `financial-advisor-agent` | Customer Servicing | 对客 | 只读问答、防幻觉提示 | 无 | 无 |
| `credit-card-servicing` | Card / Customer Servicing | 对客 | 查账单(取数)+ 还款 | `query_bill`、`repay` | 还款 >5万 |
| `loan-intake` | Consumer Loan / Credit Mgmt | 对客 | 多轮资料收集 + 提交 | `check_status`、`submit_application` | 提交必审 |
| `aml-screening` | Financial Crime / AML | **对内** | 交易研判 + 名单筛查 + 起草 SAR | `get_transactions`、`screen_name`、`file_sar` | SAR 上报必审 |
| `retail-wealth-advisor` | Investment Mgmt / Advisory | 对客(手机银行) | **适当性匹配** + 千人千面推荐 + 申购 | `get_customer_profile`、`recommend_products`、`place_order` | 申购必审(双录) |
| `private-banking-rm` | Private Banking | **对内(客户经理)** | 高净值配置建议(含私募/家族信托) | `get_customer_profile`、`recommend_products` | — |

## 零售理财:投资者适当性 + 资产分层(领域资产)

`com.bank.financial.wealth` 把零售理财规则**固化进代码**(模型拿不到不该给的产品):

- **投资者适当性**:客户 `C1–C5` × 产品 `R1–R5`,`recommend_products` 只返回 `产品R ≤ 客户C` 的产品(信贷除外)。例:C2 客户只见 R1/R2,看不到混合/股票基金/黄金(R3)。
- **资产分层**:`<500万 大众 / ≥500万 私行 / ≥1000万 家族办公室`;私募(私行起)、家族信托(家族办公室起)按分层放开。
- **渠道隔离(点3)**:手机银行自助渠道**不直售私募/信托**,符合资格客户给 `rmNote` 引导至专属客户经理;客户经理渠道(`private-banking-rm`)才放开。同一客户两渠道结果不同。
- **千人千面(点4)**:`recommend_products` 依画像 tags 排序(成长偏好→合规范围内优先较高风险)。
- 货架/客户为内存示例(`WealthData`),银行换成真实产品目录与 CRM(`LocalTool` → `HttpTool`)。

演示(`--demo` 同一私行客户,对比两渠道):
```bash
./financial/play.sh retail-wealth-advisor --demo   # 手机银行自助:无私募,给 rmNote
./financial/play.sh private-banking-rm  --demo     # 客户经理:私募 FOF 可见
```

> 这几个覆盖了"对客只读 → 对客取数动账 → 多轮办理 → 对内决策辅助 → 适当性推荐"的难度阶梯,
> 与之前梳理的银行 agent 落地爬坡一致。每个模板都遵守同一套横切原则:
> **数字来自后端工具(不编造)、敏感动作人在环、合规护栏 fail-closed、全程可观测。**

## 共性骨架(每个模板都长这样)

```java
public final class XxxAgent extends AbstractFinancialAgentHandler {
    public static final String ID = "xxx";
    public XxxAgent(ModelConnection model) { super(ID, model); }

    @Override protected String description() { ... }
    @Override protected String systemPrompt() { ... }          // 领域大脑 + 边界/拒答
    @Override protected List<LocalFunction> tools() { ... }      // 声明式后端取数(防幻觉)
    @Override protected List<AgentRail> complianceRails(ctx){...}// 合规护栏(HIGH+ 拦截)
    @Override protected List<AgentRail> approvalRails(ctx){...}  // 敏感动作人审
}
```

工具用 `HttpTool.toLocalFunction(new ToolDef(...))` + `Schemas.object().required(...).build()` 声明,
`{path}` 占位自动用入参填充;护栏/人审用 kit 现成资产。换言之:**领域语义是你的增值,
横切关注点 kit 全包了。**
