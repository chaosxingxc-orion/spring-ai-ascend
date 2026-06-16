package com.bank.financial.research.calc;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Multi-method trend forecasting that converges to one view. A historical series
 * is projected forward by several independent methods — CAGR, ordinary-least-
 * squares linear regression, and recent-momentum — and the methods are then
 * reconciled into a single blended forecast. The dispersion across methods is
 * itself the signal: tight dispersion ⇒ a confident, convergent trend; wide
 * dispersion ⇒ the methods disagree and the report should hedge.
 *
 * <p>This realises "基于不同的分析方法预测相应的趋势,最终收敛".
 */
public final class TrendForecast {

    private TrendForecast() {
    }

    /**
     * @param history      historical values, oldest first (need at least 2 points)
     * @param periodsAhead how many periods to project (&gt;= 1)
     */
    public static ForecastResult project(List<Double> history, int periodsAhead) {
        if (history == null || history.size() < 2) {
            throw new IllegalArgumentException("need at least two historical points");
        }
        if (periodsAhead < 1) {
            throw new IllegalArgumentException("periodsAhead must be >= 1");
        }

        Map<String, Double> methodForecasts = new LinkedHashMap<>();
        methodForecasts.put("CAGR", cagrForecast(history, periodsAhead));
        methodForecasts.put("OLS", olsForecast(history, periodsAhead));
        methodForecasts.put("Momentum", momentumForecast(history, periodsAhead));

        double mean = methodForecasts.values().stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        double min = methodForecasts.values().stream().mapToDouble(Double::doubleValue).min().orElse(0.0);
        double max = methodForecasts.values().stream().mapToDouble(Double::doubleValue).max().orElse(0.0);
        double dispersion = mean == 0 ? 0 : (max - min) / Math.abs(mean);
        boolean convergent = dispersion <= 0.15;

        return new ForecastResult(
                methodForecasts, Calc.money(mean), Calc.rate(dispersion), convergent,
                new ValueRange(Calc.money(min), Calc.money(mean), Calc.money(max)));
    }

    /** Compound annual growth rate extrapolation. */
    static double cagrForecast(List<Double> h, int ahead) {
        double first = h.get(0);
        double last = h.get(h.size() - 1);
        int periods = h.size() - 1;
        if (first <= 0 || last <= 0) {
            // CAGR undefined on non-positive endpoints — fall back to linear average step.
            return momentumForecast(h, ahead);
        }
        double cagr = Math.pow(last / first, 1.0 / periods) - 1.0;
        return Calc.compound(last, cagr, ahead);
    }

    /** Ordinary-least-squares linear fit (y = a + b·x), projected forward. */
    static double olsForecast(List<Double> h, int ahead) {
        int n = h.size();
        double sumX = 0, sumY = 0, sumXY = 0, sumXX = 0;
        for (int i = 0; i < n; i++) {
            double x = i;
            double y = h.get(i);
            sumX += x;
            sumY += y;
            sumXY += x * y;
            sumXX += x * x;
        }
        double denom = n * sumXX - sumX * sumX;
        double slope = denom == 0 ? 0 : (n * sumXY - sumX * sumY) / denom;
        double intercept = (sumY - slope * sumX) / n;
        double xForecast = (n - 1) + ahead;
        return intercept + slope * xForecast;
    }

    /** Recent-momentum: extrapolate the average absolute step of the series. */
    static double momentumForecast(List<Double> h, int ahead) {
        int n = h.size();
        double avgStep = (h.get(n - 1) - h.get(0)) / (n - 1);
        return h.get(n - 1) + avgStep * ahead;
    }

    public record ForecastResult(
            Map<String, Double> methodForecasts,
            double blended,
            double dispersion,
            boolean convergent,
            ValueRange range) {

        public ForecastResult {
            methodForecasts = Map.copyOf(methodForecasts);
        }
    }
}
