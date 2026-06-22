package com.bank.financial.research.consistency;

import com.bank.financial.research.engine.Bb;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Deterministic numeric-consistency gate. A long report drafted by a language
 * model can silently drift from the computed figures; this checker holds the
 * prose to the blackboard's single source of truth. For each headline figure it
 * verifies the canonical value actually appears in the body (coverage) and flags
 * any near-miss number that looks like a corrupted copy of it (drift). It is the
 * machine half of the pre-publication review — cheap, exact, and unfooled by
 * fluent wording.
 */
public final class NumericConsistencyChecker {

    private static final Pattern NUMBER = Pattern.compile("-?\\d{1,3}(?:,\\d{3})*(?:\\.\\d+)?|-?\\d+(?:\\.\\d+)?");

    private NumericConsistencyChecker() {
    }

    /** A figure the report MUST carry faithfully. */
    public record HeadlineFigure(String label, double value) {
    }

    /**
     * @return human-readable findings; empty means the prose is consistent with
     *         the canonical figures.
     */
    public static List<String> check(String reportText, List<HeadlineFigure> figures) {
        List<String> findings = new ArrayList<>();
        if (reportText == null) {
            reportText = "";
        }
        List<Double> bodyNumbers = numbersIn(reportText);

        for (HeadlineFigure f : figures) {
            String canonical = Bb.fmt(f.value());
            boolean present = reportText.contains(canonical);
            if (!present) {
                findings.add("headline 缺失: 「" + f.label() + "」的规范值 " + canonical + " 未出现在正文中");
                // Drift: a body number close to (but not equal to) the canonical value.
                for (double w : bodyNumbers) {
                    double rel = f.value() == 0 ? Math.abs(w) : Math.abs(w - f.value()) / Math.abs(f.value());
                    if (rel > 0 && rel <= 0.03) {
                        findings.add("疑似数字漂移: 正文出现 " + Bb.fmt(w)
                                + ",接近但不等于「" + f.label() + "」规范值 " + canonical);
                        break;
                    }
                }
            }
        }
        return findings;
    }

    static List<Double> numbersIn(String text) {
        List<Double> out = new ArrayList<>();
        Matcher m = NUMBER.matcher(text);
        while (m.find()) {
            try {
                out.add(Double.parseDouble(m.group().replace(",", "")));
            } catch (NumberFormatException ignored) {
                // skip non-parseable token
            }
        }
        return out;
    }
}
