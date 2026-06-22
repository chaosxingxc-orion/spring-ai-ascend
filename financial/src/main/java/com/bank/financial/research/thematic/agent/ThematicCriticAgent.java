package com.bank.financial.research.thematic.agent;

import com.bank.financial.research.consistency.NumericConsistencyChecker;
import com.bank.financial.research.consistency.NumericConsistencyChecker.HeadlineFigure;
import com.bank.financial.research.data.ThematicData;
import com.bank.financial.research.thematic.ThematicBb;
import com.bank.financial.research.thematic.ThematicContext;
import com.bank.financial.research.thematic.ThematicSubAgent;
import java.util.ArrayList;
import java.util.List;

/**
 * Critic / editor for the sector-strategy note. Holds the prose to the computed
 * numbers: the overall score and every sub-sector impact score must appear
 * faithfully in the body (the same deterministic check the equity engine uses).
 */
public final class ThematicCriticAgent implements ThematicSubAgent {

    @Override
    public String role() {
        return "critic";
    }

    @Override
    public String capability() {
        return "review";
    }

    @Override
    public void contribute(ThematicContext ctx) {
        review(ctx);
    }

    public List<String> review(ThematicContext ctx) {
        StringBuilder body = new StringBuilder();
        for (String key : ctx.blackboardKeys()) {
            if (key.startsWith(ThematicBb.SECTION_PREFIX)) {
                ctx.latest(key).ifPresent(v -> body.append(v).append('\n'));
            }
        }
        List<HeadlineFigure> figures = new ArrayList<>();
        ctx.latestNum(ThematicBb.OVERALL_SCORE).ifPresent(v -> figures.add(new HeadlineFigure("综合影响分", v)));
        for (ThematicData.SubSector s : ctx.dataset().subSectors()) {
            ctx.latestNum(ThematicBb.sectorScoreKey(s.name()))
                    .ifPresent(v -> figures.add(new HeadlineFigure(s.name() + "影响分", v)));
        }
        List<String> findings = NumericConsistencyChecker.check(body.toString(), figures);
        ctx.put(role(), "critique.findingCount", Integer.toString(findings.size()));
        if (!findings.isEmpty()) {
            ctx.put(role(), "critique.findings", String.join(" | ", findings));
        }
        return findings;
    }
}
