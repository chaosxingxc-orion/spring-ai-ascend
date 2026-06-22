package com.bank.financial.research;

import com.bank.financial.research.engine.ReportBudget;
import com.bank.financial.research.engine.ReportRequest;
import com.bank.financial.research.thematic.ThematicReport;
import com.bank.financial.research.thematic.ThematicReportEngine;

/**
 * Standalone demo of the thematic / sector-strategy engine — the multi-agent
 * pipeline (plan → ingest → impact-scoring → strategy → write → critique → comply)
 * that turns a macro scenario into a sector-strategy report with computed
 * sub-sector ratings.
 *
 * <pre>
 *   ./financial/play-research.sh --thematic "中国 TMT"          # offline (scripted)
 *   ./financial/play-research.sh --thematic "中国 TMT" --real    # env-driven live model
 * </pre>
 */
public final class ResearchReportPlaygroundThematic {

    public static void main(String[] args) {
        LogQuieter.quiet();
        String theme = "中国 TMT";
        boolean real = false;
        for (String a : args) {
            if (a.equals("--real")) {
                real = true;
            } else if (!a.startsWith("--")) {
                theme = a;
            }
        }

        long asOf = System.currentTimeMillis();
        ThematicReportEngine engine = real ? ResearchReports.thematicFromEnv(asOf) : ResearchReports.thematicOffline(asOf);
        ReportRequest request = new ReportRequest(theme, "INDUSTRY", "playground", "zh-CN", asOf, ReportBudget.standard());
        ThematicReport report = engine.generate(request);

        System.out.println(report.toMarkdown());
        System.out.println("\n────────────────────────────────────────");
        System.out.println("引擎元数据:");
        System.out.println("  模型           = " + report.metadata().modelName());
        System.out.println("  数据源         = " + report.metadata().dataSource());
        System.out.println("  模型调用次数   = " + report.metadata().modelCalls());
        System.out.println("  评审轮数       = " + report.metadata().criticRounds());
        System.out.println("  一致性发现     = " + report.metadata().consistencyFindings().size() + " 条");
        System.out.println("  降级事件       = " + report.metadata().degradations().size() + " 条");
        System.out.println("  报告字符数     = " + report.charCount());
    }
}
