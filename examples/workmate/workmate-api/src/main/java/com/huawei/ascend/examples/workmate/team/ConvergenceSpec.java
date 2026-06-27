package com.huawei.ascend.examples.workmate.team;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Parses coordination.termination.convergence (ADR-013 §3). */
public final class ConvergenceSpec {

    private static final Pattern NO_NEW_FINDINGS = Pattern.compile("noNewFindingsForN\\((\\d+)\\)");

    private ConvergenceSpec() {}

    /**
     * @return required consecutive rounds without new findings; 0 if convergence not configured
     */
    public static int noNewFindingsThreshold(String convergence) {
        if (convergence == null || convergence.isBlank()) {
            return 0;
        }
        Matcher matcher = NO_NEW_FINDINGS.matcher(convergence.trim());
        if (!matcher.find()) {
            return 0;
        }
        try {
            int n = Integer.parseInt(matcher.group(1));
            return n > 0 ? n : 0;
        } catch (NumberFormatException ex) {
            return 0;
        }
    }
}
