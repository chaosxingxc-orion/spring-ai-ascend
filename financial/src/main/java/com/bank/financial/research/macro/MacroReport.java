package com.bank.financial.research.macro;

import com.bank.financial.research.engine.Bb;
import com.bank.financial.research.engine.ReportMetadata;
import com.bank.financial.research.engine.ReportSection;
import java.util.List;

/** A finished macro & policy report. Reuses {@link ReportSection} + {@link ReportMetadata}. */
public record MacroReport(
        String region, String assetTilt, double composite, String thesis,
        List<IndicatorView> indicators, List<ReportSection> sections, ReportMetadata metadata) {

    public MacroReport {
        indicators = List.copyOf(indicators);
        sections = List.copyOf(sections);
    }

    /** One indicator headline for the at-a-glance table. */
    public record IndicatorView(String label, double value, String unit, String period) {
    }

    public int charCount() {
        return toMarkdown().length();
    }

    public String toMarkdown() {
        StringBuilder sb = new StringBuilder();
        sb.append("# ").append(region).append("宏观与政策 — 研究报告\n\n");
        sb.append("**资产配置倾向:** ").append(assetTilt)
                .append("　**宏观环境综合分:** ").append(Bb.fmt(composite)).append("\n\n");
        sb.append("> **核心判断:** ").append(thesis).append("\n\n");
        sb.append("**关键指标一览:**\n\n");
        for (IndicatorView v : indicators) {
            sb.append("- ").append(v.label()).append(": ").append(Bb.fmt(v.value())).append(v.unit())
                    .append("(").append(v.period()).append(")\n");
        }
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
