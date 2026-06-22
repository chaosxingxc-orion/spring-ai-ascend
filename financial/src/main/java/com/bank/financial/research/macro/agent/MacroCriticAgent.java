package com.bank.financial.research.macro.agent;

import com.bank.financial.research.consistency.NumericConsistencyChecker;
import com.bank.financial.research.consistency.NumericConsistencyChecker.HeadlineFigure;
import com.bank.financial.research.data.MacroData;
import com.bank.financial.research.macro.MacroBb;
import com.bank.financial.research.macro.MacroContext;
import com.bank.financial.research.macro.MacroSubAgent;
import java.util.ArrayList;
import java.util.List;

/**
 * Critic / editor for the macro note. Holds the prose to the computed numbers: the
 * composite macro score and every indicator reading must appear faithfully in the
 * body (the same deterministic check the other engines use).
 */
public final class MacroCriticAgent implements MacroSubAgent {

    @Override
    public String role() {
        return "critic";
    }

    @Override
    public String capability() {
        return "review";
    }

    @Override
    public void contribute(MacroContext ctx) {
        review(ctx);
    }

    public List<String> review(MacroContext ctx) {
        StringBuilder body = new StringBuilder();
        for (String key : ctx.blackboardKeys()) {
            if (key.startsWith(MacroBb.SECTION_PREFIX)) {
                ctx.latest(key).ifPresent(v -> body.append(v).append('\n'));
            }
        }
        List<HeadlineFigure> figures = new ArrayList<>();
        ctx.latestNum(MacroBb.COMPOSITE).ifPresent(v -> figures.add(new HeadlineFigure("宏观综合分", v)));
        for (MacroData.Indicator i : ctx.dataset().indicators()) {
            figures.add(new HeadlineFigure(i.label(), i.value()));
        }
        List<String> findings = NumericConsistencyChecker.check(body.toString(), figures);
        ctx.put(role(), "critique.findingCount", Integer.toString(findings.size()));
        if (!findings.isEmpty()) {
            ctx.put(role(), "critique.findings", String.join(" | ", findings));
        }
        return findings;
    }
}
