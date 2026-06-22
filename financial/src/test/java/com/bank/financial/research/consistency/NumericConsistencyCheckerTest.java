package com.bank.financial.research.consistency;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bank.financial.research.consistency.NumericConsistencyChecker.HeadlineFigure;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Unit layer: the deterministic numeric-consistency gate. */
class NumericConsistencyCheckerTest {

    @Test
    void cleanReport_hasNoFindings() {
        String body = "评级中性,目标价 70.15;DCF 每股 77.65,可比中位 61.75。";
        List<String> findings = NumericConsistencyChecker.check(body, List.of(
                new HeadlineFigure("目标价", 70.15),
                new HeadlineFigure("DCF每股", 77.65),
                new HeadlineFigure("可比中位每股", 61.75)));
        assertTrue(findings.isEmpty(), () -> "expected clean, got " + findings);
    }

    @Test
    void driftedNumber_isFlagged() {
        // Body says 70.20 but the canonical target is 70.15 — a near-miss drift.
        String body = "目标价 70.20,基于估值收敛。";
        List<String> findings = NumericConsistencyChecker.check(body, List.of(
                new HeadlineFigure("目标价", 70.15)));
        assertFalse(findings.isEmpty());
        assertTrue(findings.stream().anyMatch(f -> f.contains("漂移")),
                () -> "expected a drift finding, got " + findings);
    }

    @Test
    void missingHeadline_isFlagged() {
        String body = "本报告讨论了行业前景与竞争格局,但未给出量化目标。";
        List<String> findings = NumericConsistencyChecker.check(body, List.of(
                new HeadlineFigure("目标价", 70.15)));
        assertFalse(findings.isEmpty());
        assertTrue(findings.stream().anyMatch(f -> f.contains("缺失")));
    }
}
