package com.bank.financial.research.composite;

import com.bank.financial.research.engine.ReportMetadata;
import com.bank.financial.research.engine.ReportSection;
import java.util.List;

/**
 * A composed report: one subject (fund/bond, or none) analysed as the spine, plus
 * one section-module per selected analysis lens (macro / industry-sector / global).
 * Each module's sections are produced by the corresponding engine over the same
 * chosen model, then merged into one document with a single disclosure block.
 */
public record CompositeReport(
        String title, String subtitle, List<Module> modules,
        List<String> complianceNotes, ReportMetadata metadata) {

    public CompositeReport {
        modules = List.copyOf(modules);
        complianceNotes = List.copyOf(complianceNotes);
    }

    /** One analysis block (subject or a lens), with its ordered sections. */
    public record Module(String key, String title, List<ReportSection> sections) {
        public Module {
            sections = List.copyOf(sections);
        }
    }

    public int charCount() {
        return toMarkdown().length();
    }

    public String toMarkdown() {
        StringBuilder sb = new StringBuilder();
        sb.append("# ").append(title).append("\n\n");
        if (subtitle != null && !subtitle.isBlank()) {
            sb.append("> ").append(subtitle).append("\n\n");
        }
        int n = 0;
        for (Module m : modules) {
            n++;
            sb.append("## ").append(cn(n)).append("、").append(m.title()).append("\n\n");
            for (ReportSection s : m.sections().stream()
                    .sorted((a, b) -> Integer.compare(a.order(), b.order())).toList()) {
                sb.append("### ").append(s.title()).append("\n\n").append(s.body()).append("\n\n");
            }
        }
        sb.append("---\n\n### 披露与声明\n\n");
        for (String note : complianceNotes) {
            sb.append("- ").append(note).append('\n');
        }
        sb.append("\n*本报告由多智能体研报引擎组合生成,数据源:").append(metadata.dataSource())
                .append(";生成模型:").append(metadata.modelName()).append("。*\n");
        return sb.toString();
    }

    private static String cn(int i) {
        String[] d = {"一", "二", "三", "四", "五", "六", "七", "八"};
        return i >= 1 && i <= d.length ? d[i - 1] : String.valueOf(i);
    }
}
