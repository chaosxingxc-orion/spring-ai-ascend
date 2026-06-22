package com.bank.financial.research.fund.agent;

import com.bank.financial.research.consistency.NumericConsistencyChecker;
import com.bank.financial.research.consistency.NumericConsistencyChecker.HeadlineFigure;
import com.bank.financial.research.fund.FundBb;
import com.bank.financial.research.fund.FundContext;
import com.bank.financial.research.fund.FundSubAgent;
import java.util.ArrayList;
import java.util.List;

/**
 * Critic / editor. Holds the prose to the computed numbers via the deterministic
 * consistency checker. Checks the ratio metrics that appear verbatim in the body
 * (Sharpe / Calmar / Beta); percentage-rendered metrics are excluded since they
 * are shown as "21%" rather than the canonical "0.21".
 */
public final class FundCriticAgent implements FundSubAgent {

    @Override
    public String role() {
        return "critic";
    }

    @Override
    public String capability() {
        return "review";
    }

    @Override
    public void contribute(FundContext ctx) {
        review(ctx);
    }

    public List<String> review(FundContext ctx) {
        StringBuilder body = new StringBuilder();
        for (String key : ctx.blackboardKeys()) {
            if (key.startsWith(FundBb.SECTION_PREFIX)) {
                ctx.latest(key).ifPresent(v -> body.append(v).append('\n'));
            }
        }
        List<HeadlineFigure> figures = new ArrayList<>();
        ctx.latestNum(FundBb.SHARPE).ifPresent(v -> figures.add(new HeadlineFigure("夏普", v)));
        ctx.latestNum(FundBb.CALMAR).ifPresent(v -> figures.add(new HeadlineFigure("Calmar", v)));
        ctx.latestNum(FundBb.BETA).ifPresent(v -> figures.add(new HeadlineFigure("Beta", v)));
        List<String> findings = NumericConsistencyChecker.check(body.toString(), figures);
        ctx.put(role(), "critique.findingCount", Integer.toString(findings.size()));
        if (!findings.isEmpty()) {
            ctx.put(role(), "critique.findings", String.join(" | ", findings));
        }
        return findings;
    }
}
