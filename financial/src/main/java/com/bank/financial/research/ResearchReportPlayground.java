package com.bank.financial.research;

import com.bank.financial.research.engine.ReportRequest;
import com.bank.financial.research.engine.ResearchReport;
import com.bank.financial.research.engine.ResearchReportEngine;

/**
 * Standalone, no-API-key demonstration of the research-report engine — the
 * end-to-end "一条龙": ingest → analyse → converge → write → critique → comply →
 * assemble, printed as a finished Markdown report.
 *
 * <pre>
 *   ./financial/play-research.sh DEMO          # offline: stub data + scripted model
 *   ./financial/play-research.sh DEMO --real    # env-driven data/model (BANK_LLM_*, RESEARCH_*)
 * </pre>
 */
public final class ResearchReportPlayground {

    public static void main(String[] args) {
        String ticker = "DEMO";
        boolean real = false;
        for (String a : args) {
            if (a.equals("--real")) {
                real = true;
            } else if (!a.startsWith("--")) {
                ticker = a;
            }
        }

        long asOf = System.currentTimeMillis();
        ResearchReportEngine engine = real ? ResearchReports.fromEnv(asOf) : ResearchReports.offline(asOf);
        ResearchReport report = engine.generate(ReportRequest.equity(ticker, "playground", asOf));

        System.out.println(report.toMarkdown());
        System.out.println("\n────────────────────────────────────────");
        System.out.println("引擎元数据:");
        System.out.println("  模型           = " + report.metadata().modelName());
        System.out.println("  数据源         = " + report.metadata().dataSource());
        System.out.println("  模型调用次数   = " + report.metadata().modelCalls());
        System.out.println("  评审轮数       = " + report.metadata().criticRounds());
        System.out.println("  收敛判定       = " + report.metadata().convergenceVerdict());
        System.out.println("  一致性发现     = " + report.metadata().consistencyFindings().size() + " 条");
        System.out.println("  数据缺口       = " + report.metadata().dataGaps().size() + " 条");
        System.out.println("  报告字符数     = " + report.charCount());
    }
}
