package com.bank.financial.research;

import com.bank.financial.kit.AbstractFinancialAgentHandler;
import com.bank.financial.kit.ModelConnection;
import com.bank.financial.kit.tool.LocalTool;
import com.bank.financial.kit.tool.Schemas;
import com.bank.financial.research.engine.ReportRequest;
import com.bank.financial.research.engine.ResearchReport;
import com.openjiuwen.core.foundation.tool.function.LocalFunction;
import java.util.List;
import java.util.Map;

/**
 * A2A / playground face for the multi-agent research-report engine. The engine is
 * an orchestration of specialised sub-agents, not a single chat agent, so this
 * handler exposes it as one tool — {@code generate_research_report(ticker)} —
 * that runs the full PLAN→…→ASSEMBLE pipeline and returns the finished Markdown
 * report. The handler's own LLM only routes the request to that tool and returns
 * its output verbatim; all analysis and figures come from the engine.
 *
 * <p>For a no-API-key, end-to-end demonstration use the dedicated runnable
 * {@link ResearchReportPlayground} (deterministic stub data + scripted model).
 */
public final class ResearchReportAgent extends AbstractFinancialAgentHandler {

    public static final String ID = "research-report";

    public ResearchReportAgent(ModelConnection model) {
        super(ID, model);
    }

    @Override
    protected int maxIterations() {
        return 3; // route → call tool → return
    }

    @Override
    protected String description() {
        return "研报生成:多智能体(规划/数据/建模/估值/行业/首席/撰写/评审/合规)协作生成完整研究报告";
    }

    @Override
    protected String systemPrompt() {
        return "你是机构研究报告生成助手。当用户要求为某标的生成研究报告时,调用工具 "
                + "generate_research_report(ticker) 运行多智能体研报引擎,并将工具返回的 markdown 报告"
                + "原样返回给用户,不要改写、删减或自行编造其中的任何数字。若用户未给出标的代码,请先询问。";
    }

    @Override
    protected List<LocalFunction> tools() {
        return List.of(LocalTool.of(
                "generate_research_report",
                "为给定标的运行多智能体研报引擎,返回完整 markdown 报告(含评级、目标价、各章节、披露)",
                Schemas.object().required("ticker", "string", "标的代码,例如 DEMO").build(),
                inputs -> {
                    Object t = inputs.get("ticker");
                    String ticker = t == null ? "" : t.toString().trim();
                    if (ticker.isEmpty()) {
                        return Map.of("error", "缺少标的代码 ticker");
                    }
                    long now = System.currentTimeMillis();
                    ResearchReport report = ResearchReports.fromEnv(now)
                            .generate(ReportRequest.equity(ticker, "research-desk", now));
                    return Map.of(
                            "rating", report.rating(),
                            "priceTarget", report.priceTarget(),
                            "markdown", report.toMarkdown());
                }));
    }

    @Override
    public String playgroundHint() {
        return "  研报引擎(facade)。注意:标准 --mock 不会触发工具调用。\n"
                + "  端到端离线演示请用:./financial/play-research.sh DEMO\n"
                + "  接真实模型/数据后,本 agent 可经 A2A 调用 generate_research_report(ticker)。";
    }
}
