package com.bank.financial.research.calc;

import java.util.ArrayList;
import java.util.List;

/**
 * Two-way DCF sensitivity grid: per-share value across a grid of WACC × terminal
 * growth. A research report shows this table so the reader can see how fragile
 * the target is to the two assumptions that drive a DCF most.
 */
public final class SensitivityAnalysis {

    private SensitivityAnalysis() {
    }

    /**
     * @param explicitFcf explicit-year free cash flows
     * @param waccAxis    WACC values (columns)
     * @param growthAxis  terminal growth values (rows)
     */
    public static SensitivityGrid dcfGrid(List<Double> explicitFcf, double netDebt, double minorityInterest,
            double dilutedShares, double[] waccAxis, double[] growthAxis) {
        double[][] cells = new double[growthAxis.length][waccAxis.length];
        double min = Double.POSITIVE_INFINITY;
        double max = Double.NEGATIVE_INFINITY;
        for (int r = 0; r < growthAxis.length; r++) {
            for (int c = 0; c < waccAxis.length; c++) {
                double g = growthAxis[r];
                double w = waccAxis[c];
                double v;
                if (g >= w) {
                    // Undefined Gordon value — mark NaN so the table shows "n/m".
                    v = Double.NaN;
                } else {
                    v = DcfModel.gordon(explicitFcf, w, g, netDebt, minorityInterest, dilutedShares).perShare();
                    min = Math.min(min, v);
                    max = Math.max(max, v);
                }
                cells[r][c] = v;
            }
        }
        return new SensitivityGrid(waccAxis.clone(), growthAxis.clone(), cells,
                Calc.money(min), Calc.money(max));
    }

    /** @param cells indexed [growthRow][waccCol]; NaN where g &gt;= wacc (not meaningful). */
    public record SensitivityGrid(double[] waccAxis, double[] growthAxis, double[][] cells,
            double minPerShare, double maxPerShare) {

        /** Render as a compact text table for the report body. */
        public List<String> asTextRows() {
            List<String> rows = new ArrayList<>();
            StringBuilder header = new StringBuilder("  g\\WACC");
            for (double w : waccAxis) {
                header.append(String.format("  %6.1f%%", w * 100));
            }
            rows.add(header.toString());
            for (int r = 0; r < growthAxis.length; r++) {
                StringBuilder line = new StringBuilder(String.format("  %5.1f%%", growthAxis[r] * 100));
                for (int c = 0; c < waccAxis.length; c++) {
                    double v = cells[r][c];
                    line.append(Double.isNaN(v) ? "     n/m" : String.format("  %7.2f", v));
                }
                rows.add(line.toString());
            }
            return rows;
        }
    }
}
