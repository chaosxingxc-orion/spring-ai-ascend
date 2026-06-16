package com.bank.financial.research.calc;

import java.util.List;

/**
 * Discounted-cash-flow intrinsic valuation. Projects free cash flows, discounts
 * them at the WACC, adds the present value of a terminal value (Gordon growth or
 * exit multiple), then bridges enterprise value to equity value and a per-share
 * figure.
 *
 * <p>The enterprise→equity bridge is where sell-side models most often slip
 * (net debt, minorities, diluted shares), so it is explicit and tested here
 * rather than folded into the LLM's prose.
 */
public final class DcfModel {

    private DcfModel() {
    }

    /**
     * @param explicitFcf    free cash flows for each explicit forecast year (year 1..N), in currency units
     * @param wacc           discount rate (e.g. 0.09 for 9%)
     * @param terminalGrowth perpetual growth rate g for the Gordon terminal value (must be &lt; wacc)
     * @param netDebt        net debt (debt − cash); subtracted in the bridge
     * @param minorityInterest minority interest + preferred; subtracted in the bridge
     * @param dilutedShares  fully-diluted share count (&gt; 0)
     */
    public static DcfResult gordon(List<Double> explicitFcf, double wacc, double terminalGrowth,
            double netDebt, double minorityInterest, double dilutedShares) {
        Calc.requirePositive("wacc", wacc);
        Calc.requirePositive("dilutedShares", dilutedShares);
        if (terminalGrowth >= wacc) {
            throw new IllegalArgumentException(
                    "terminalGrowth (" + terminalGrowth + ") must be < wacc (" + wacc + ") for a finite Gordon value");
        }
        if (explicitFcf == null || explicitFcf.isEmpty()) {
            throw new IllegalArgumentException("explicitFcf must have at least one year");
        }

        double pvExplicit = 0.0;
        for (int i = 0; i < explicitFcf.size(); i++) {
            int year = i + 1;
            double fcf = Calc.requireFinite("fcf[" + year + "]", explicitFcf.get(i));
            pvExplicit += fcf / Math.pow(1.0 + wacc, year);
        }

        int n = explicitFcf.size();
        double lastFcf = explicitFcf.get(n - 1);
        // Gordon terminal value at year N: FCF_{N+1} / (wacc - g)
        double terminalValue = (lastFcf * (1.0 + terminalGrowth)) / (wacc - terminalGrowth);
        double pvTerminal = terminalValue / Math.pow(1.0 + wacc, n);

        double enterpriseValue = pvExplicit + pvTerminal;
        double equityValue = enterpriseValue - netDebt - minorityInterest;
        double perShare = equityValue / dilutedShares;

        return new DcfResult(
                Calc.money(enterpriseValue), Calc.money(equityValue), Calc.money(perShare),
                Calc.money(pvExplicit), Calc.money(pvTerminal), Calc.money(terminalValue),
                Calc.rate(pvTerminal / enterpriseValue));
    }

    /**
     * Terminal value via an exit EV/EBITDA multiple applied to terminal-year EBITDA.
     * An alternative to Gordon growth, often used as a cross-check.
     */
    public static DcfResult exitMultiple(List<Double> explicitFcf, double wacc, double terminalEbitda,
            double exitEvEbitda, double netDebt, double minorityInterest, double dilutedShares) {
        Calc.requirePositive("wacc", wacc);
        Calc.requirePositive("dilutedShares", dilutedShares);
        Calc.requirePositive("exitEvEbitda", exitEvEbitda);
        if (explicitFcf == null || explicitFcf.isEmpty()) {
            throw new IllegalArgumentException("explicitFcf must have at least one year");
        }

        double pvExplicit = 0.0;
        for (int i = 0; i < explicitFcf.size(); i++) {
            pvExplicit += explicitFcf.get(i) / Math.pow(1.0 + wacc, i + 1);
        }
        int n = explicitFcf.size();
        double terminalValue = terminalEbitda * exitEvEbitda;
        double pvTerminal = terminalValue / Math.pow(1.0 + wacc, n);

        double enterpriseValue = pvExplicit + pvTerminal;
        double equityValue = enterpriseValue - netDebt - minorityInterest;
        double perShare = equityValue / dilutedShares;

        return new DcfResult(
                Calc.money(enterpriseValue), Calc.money(equityValue), Calc.money(perShare),
                Calc.money(pvExplicit), Calc.money(pvTerminal), Calc.money(terminalValue),
                Calc.rate(pvTerminal / enterpriseValue));
    }

    /**
     * Result of a DCF run. {@code terminalWeight} is the fraction of enterprise
     * value coming from the terminal value — a high value (&gt; ~0.75) is a known
     * fragility signal the critic agent flags.
     */
    public record DcfResult(
            double enterpriseValue,
            double equityValue,
            double perShare,
            double pvExplicit,
            double pvTerminal,
            double terminalValue,
            double terminalWeight) {

        public ValueRange perShareRange(double spreadPct) {
            return ValueRange.around(perShare, spreadPct);
        }
    }
}
