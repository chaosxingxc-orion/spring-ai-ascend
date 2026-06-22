package com.bank.financial.research.thematic;

import com.bank.financial.research.engine.Bb;
import com.bank.financial.research.engine.ReportSection;
import com.bank.financial.research.engine.ReportMetadata;
import java.util.List;

/**
 * A finished thematic / sector-strategy report: the overall sector stance, the
 * thesis, the per-sub-sector ratings, ordered sections, and run metadata.
 * Reuses {@link ReportSection} and {@link ReportMetadata}.
 */
public record ThematicReport(
        String theme, String overallRating, double overallScore, String thesis,
        List<SubSectorView> subSectors, List<ReportSection> sections, ReportMetadata metadata) {

    public ThematicReport {
        subSectors = List.copyOf(subSectors);
        sections = List.copyOf(sections);
    }

    public record SubSectorView(String name, double score, String rating) {
    }

    public int charCount() {
        return toMarkdown().length();
    }

    public String toMarkdown() {
        StringBuilder sb = new StringBuilder();
        sb.append("# ").append(theme).append(" — 板块策略研究报告\n\n");
        sb.append("**板块总体评级:** ").append(overallRating)
                .append("　**综合影响分:** ").append(Bb.fmt(overallScore)).append("\n\n");
        sb.append("> **核心观点:** ").append(thesis).append("\n\n");
        sb.append("**子板块评级一览:**\n\n");
        for (SubSectorView v : subSectors) {
            sb.append("- ").append(v.name()).append(": ").append(v.rating())
                    .append("(影响分 ").append(Bb.fmt(v.score())).append(")\n");
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
