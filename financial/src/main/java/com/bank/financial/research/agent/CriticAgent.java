package com.bank.financial.research.agent;

import com.bank.financial.research.consistency.NumericConsistencyChecker;
import com.bank.financial.research.consistency.NumericConsistencyChecker.HeadlineFigure;
import com.bank.financial.research.engine.Bb;
import com.bank.financial.research.engine.ReportContext;
import java.util.ArrayList;
import java.util.List;

/**
 * Critic / editor — the pre-publication review gate. It holds the drafted prose
 * to the blackboard's numbers via the deterministic
 * {@link NumericConsistencyChecker}: every headline figure must appear faithfully
 * in the body, and any near-miss number is flagged as drift. The orchestrator
 * uses the findings to drive bounded writer revisions. Findings are written back
 * to the blackboard so the audit trail shows what was checked.
 */
public final class CriticAgent implements ReportSubAgent {

    @Override
    public String role() {
        return "critic";
    }

    @Override
    public String capability() {
        return "review";
    }

    @Override
    public void contribute(ReportContext ctx) {
        review(ctx);
    }

    /** Review the current draft; returns consistency findings (empty = clean). */
    public List<String> review(ReportContext ctx) {
        String body = assembledBody(ctx);
        List<HeadlineFigure> figures = headlineFigures(ctx);
        List<String> findings = NumericConsistencyChecker.check(body, figures);

        ctx.put(role(), "critique.findingCount", Integer.toString(findings.size()));
        if (!findings.isEmpty()) {
            ctx.put(role(), "critique.findings", String.join(" | ", findings));
        }
        return findings;
    }

    private String assembledBody(ReportContext ctx) {
        StringBuilder sb = new StringBuilder();
        for (String key : ctx.blackboardKeys()) {
            if (key.startsWith(Bb.SECTION_PREFIX)) {
                ctx.latest(key).ifPresent(v -> sb.append(v).append('\n'));
            }
        }
        return sb.toString();
    }

    private List<HeadlineFigure> headlineFigures(ReportContext ctx) {
        List<HeadlineFigure> figures = new ArrayList<>();
        addIfPresent(ctx, figures, "目标价", Bb.PRICE_TARGET);
        addIfPresent(ctx, figures, "现价", Bb.CURRENT_PRICE);
        addIfPresent(ctx, figures, "DCF每股", Bb.DCF_PER_SHARE);
        addIfPresent(ctx, figures, "可比中位每股", Bb.COMPS_MEDIAN);
        addIfPresent(ctx, figures, "FY1每股收益", Bb.EPS_FY1);
        addIfPresent(ctx, figures, "FY1收入", Bb.REVENUE_FY1);
        return figures;
    }

    private void addIfPresent(ReportContext ctx, List<HeadlineFigure> figures, String label, String key) {
        ctx.latestNum(key).ifPresent(v -> figures.add(new HeadlineFigure(label, v)));
    }
}
