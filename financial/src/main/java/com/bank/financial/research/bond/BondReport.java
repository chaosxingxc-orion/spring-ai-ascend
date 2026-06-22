package com.bank.financial.research.bond;

import com.bank.financial.research.engine.Bb;
import com.bank.financial.research.engine.ReportSection;
import com.bank.financial.research.engine.ReportMetadata;
import java.util.List;

/** A finished bond / fixed-income research report. Reuses {@link ReportSection} + {@link ReportMetadata}. */
public record BondReport(
        String code, String name, String issuer, String rating, String stance, String thesis,
        Metrics metrics, List<ReportSection> sections, ReportMetadata metadata) {

    public BondReport {
        sections = List.copyOf(sections);
    }

    /** Headline computed metrics (yields/spread as fractions; durations in years). */
    public record Metrics(double ytm, double currentYield, double macaulay, double modified,
            double convexity, double creditSpread) {
    }

    public int charCount() {
        return toMarkdown().length();
    }

    public String toMarkdown() {
        StringBuilder sb = new StringBuilder();
        sb.append("# ").append(name).append(" (").append(code).append(") — 债券研究报告\n\n");
        sb.append("**发行人:** ").append(issuer).append("　**评级:** ").append(rating)
                .append("　**配置评级:** ").append(stance).append("\n\n");
        sb.append("> **核心观点:** ").append(thesis).append("\n\n");
        sb.append("**关键指标:**\n\n");
        sb.append("- 到期收益率 (YTM): ").append(Bb.pct(metrics.ytm())).append('\n');
        sb.append("- 当期收益率: ").append(Bb.pct(metrics.currentYield())).append('\n');
        sb.append("- Macaulay 久期: ").append(Bb.fmt(metrics.macaulay())).append(" 年\n");
        sb.append("- 修正久期: ").append(Bb.fmt(metrics.modified())).append(" 年\n");
        sb.append("- 凸性: ").append(Bb.fmt(metrics.convexity())).append('\n');
        sb.append("- 信用利差: ").append(Bb.pct(metrics.creditSpread())).append('\n');
        sb.append("\n---\n\n");
        for (ReportSection s : sections.stream().sorted((a, b) -> Integer.compare(a.order(), b.order())).toList()) {
            sb.append("## ").append(s.title()).append("\n\n").append(s.body()).append("\n\n");
        }
        sb.append("---\n\n### 披露与声明\n\n");
        for (String note : metadata.complianceNotes()) {
            sb.append("- ").append(note).append('\n');
        }
        sb.append("\n*本报告由多智能体研报引擎生成,数据源:").append(metadata.dataSource())
                .append(";生成模型:").append(metadata.modelName()).append("。*\n");
        return sb.toString();
    }
}
