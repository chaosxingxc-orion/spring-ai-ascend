package com.bank.financial.research.bond.agent;

import com.bank.financial.research.bond.BondBb;
import com.bank.financial.research.bond.BondContext;
import com.bank.financial.research.bond.BondSubAgent;
import com.bank.financial.research.consistency.NumericConsistencyChecker;
import com.bank.financial.research.consistency.NumericConsistencyChecker.HeadlineFigure;
import java.util.ArrayList;
import java.util.List;

/**
 * Critic / editor. Holds the prose to the computed numbers via the deterministic
 * consistency checker. Checks the duration / convexity metrics that appear verbatim
 * in the body (Macaulay / Modified / Convexity); yield-and-spread metrics are
 * excluded since they are rendered as "5.1%" rather than the canonical "0.0508".
 */
public final class BondCriticAgent implements BondSubAgent {

    @Override
    public String role() {
        return "critic";
    }

    @Override
    public String capability() {
        return "review";
    }

    @Override
    public void contribute(BondContext ctx) {
        review(ctx);
    }

    public List<String> review(BondContext ctx) {
        StringBuilder body = new StringBuilder();
        for (String key : ctx.blackboardKeys()) {
            if (key.startsWith(BondBb.SECTION_PREFIX)) {
                ctx.latest(key).ifPresent(v -> body.append(v).append('\n'));
            }
        }
        List<HeadlineFigure> figures = new ArrayList<>();
        ctx.latestNum(BondBb.MACAULAY).ifPresent(v -> figures.add(new HeadlineFigure("Macaulay久期", v)));
        ctx.latestNum(BondBb.MODIFIED).ifPresent(v -> figures.add(new HeadlineFigure("修正久期", v)));
        ctx.latestNum(BondBb.CONVEXITY).ifPresent(v -> figures.add(new HeadlineFigure("凸性", v)));
        List<String> findings = NumericConsistencyChecker.check(body.toString(), figures);
        ctx.put(role(), "critique.findingCount", Integer.toString(findings.size()));
        if (!findings.isEmpty()) {
            ctx.put(role(), "critique.findings", String.join(" | ", findings));
        }
        return findings;
    }
}
