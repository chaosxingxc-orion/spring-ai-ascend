package com.bank.financial.research.engine;

import java.util.List;

/**
 * The finished research report: the house view (rating, price target, thesis)
 * plus ordered sections and run metadata. {@link #toMarkdown()} renders the full
 * document a client would read; {@link #charCount()} measures its length.
 */
public record ResearchReport(
        String ticker, String company, String currency,
        String rating, double priceTarget, double currentPrice, double upsidePct,
        String thesis, List<ReportSection> sections, Metadata metadata) {

    public ResearchReport {
        sections = List.copyOf(sections);
    }

    public int charCount() {
        return toMarkdown().length();
    }

    /** Render the report as a single Markdown document. */
    public String toMarkdown() {
        StringBuilder sb = new StringBuilder();
        sb.append("# ").append(company).append(" (").append(ticker).append(") — 研究报告\n\n");
        sb.append("**评级:** ").append(rating)
                .append("　**目标价:** ").append(currency).append(' ').append(Bb.fmt(priceTarget))
                .append("　**现价:** ").append(currency).append(' ').append(Bb.fmt(currentPrice))
                .append("　**潜在空间:** ").append(Bb.pct(upsidePct)).append("\n\n");
        sb.append("> **投资论点:** ").append(thesis).append("\n\n");
        sb.append("---\n\n");
        for (ReportSection s : sections.stream().sorted((a, b) -> Integer.compare(a.order(), b.order())).toList()) {
            sb.append("## ").append(s.title()).append("\n\n").append(s.body()).append("\n\n");
        }
        sb.append("---\n\n### 披露与声明\n\n");
        for (String note : metadata.complianceNotes()) {
            sb.append("- ").append(note).append('\n');
        }
        sb.append("\n*本报告由多智能体研报引擎生成,数据源:").append(metadata.dataSource())
                .append(";生成模型:").append(metadata.modelName())
                .append(";收敛判定:").append(metadata.convergenceVerdict()).append("。*\n");
        return sb.toString();
    }

    /**
     * Run metadata for audit and ops.
     *
     * @param dataGaps            tiers that were unavailable/stale (transparent coverage)
     * @param complianceNotes     disclosures + the mandatory analyst certification
     * @param consistencyFindings what the numeric-consistency checker flagged
     */
    public record Metadata(
            String modelName, String dataSource, int modelCalls, int criticRounds,
            String convergenceVerdict, List<String> dataGaps, List<String> complianceNotes,
            List<String> consistencyFindings, long generatedAtEpochMs) {

        public Metadata {
            dataGaps = List.copyOf(dataGaps);
            complianceNotes = List.copyOf(complianceNotes);
            consistencyFindings = List.copyOf(consistencyFindings);
        }
    }
}
