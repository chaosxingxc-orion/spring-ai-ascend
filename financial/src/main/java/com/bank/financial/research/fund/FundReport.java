package com.bank.financial.research.fund;

import com.bank.financial.research.engine.Bb;
import com.bank.financial.research.engine.ReportSection;
import com.bank.financial.research.engine.ReportMetadata;
import java.util.List;

/** A finished fund / FOF research report. Reuses {@link ReportSection} + {@link ReportMetadata}. */
public record FundReport(
        String code, String name, String type, String overallRating, String thesis,
        Metrics metrics, List<ReportSection> sections, ReportMetadata metadata) {

    public FundReport {
        sections = List.copyOf(sections);
    }

    /** Headline computed metrics (fractions). */
    public record Metrics(double cumReturn, double annReturn, double annVol, double sharpe,
            double maxDrawdown, double calmar, double beta, double alpha) {
    }

    public int charCount() {
        return toMarkdown().length();
    }

    public String toMarkdown() {
        StringBuilder sb = new StringBuilder();
        sb.append("# ").append(name).append(" (").append(code).append(") — 基金研究报告\n\n");
        sb.append("**类型:** ").append(type).append("　**综合评级:** ").append(overallRating).append("\n\n");
        sb.append("> **核心观点:** ").append(thesis).append("\n\n");
        sb.append("**关键指标:**\n\n");
        sb.append("- 累计收益: ").append(Bb.pct(metrics.cumReturn())).append('\n');
        sb.append("- 年化收益: ").append(Bb.pct(metrics.annReturn())).append('\n');
        sb.append("- 年化波动: ").append(Bb.pct(metrics.annVol())).append('\n');
        sb.append("- 夏普比率: ").append(Bb.fmt(metrics.sharpe())).append('\n');
        sb.append("- 最大回撤: ").append(Bb.pct(metrics.maxDrawdown())).append('\n');
        sb.append("- Calmar: ").append(Bb.fmt(metrics.calmar())).append('\n');
        sb.append("- Beta: ").append(Bb.fmt(metrics.beta())).append("　Alpha: ").append(Bb.pct(metrics.alpha())).append('\n');
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
