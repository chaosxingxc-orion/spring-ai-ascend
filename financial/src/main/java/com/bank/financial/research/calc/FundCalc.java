package com.bank.financial.research.calc;

import java.util.List;

/**
 * Fund / FOF performance and risk analytics — the fund report's numeric backbone,
 * the analog of DCF for a stock. All metrics are computed deterministically from a
 * cumulative-NAV series (oldest first); the language model only narrates them.
 *
 * <p>Conventions: {@code navs} is the cumulative net-asset-value series (so total
 * return includes distributions), oldest first; {@code periodsPerYear} annualises
 * (≈244 for A-share daily NAV). Returns are fractions (0.21 = 21%); max drawdown
 * is reported as a negative fraction.
 */
public final class FundCalc {

    private FundCalc() {
    }

    public enum Rating {
        PREFERRED, NEUTRAL, AVOID
    }

    public record FundMetrics(
            double cumulativeReturn, double annualizedReturn, double annualizedVol,
            double sharpe, double maxDrawdown, double calmar, double beta, double alpha) {
    }

    static void requireSeries(List<Double> navs) {
        if (navs == null || navs.size() < 2) {
            throw new IllegalArgumentException("need at least two NAV points");
        }
        if (navs.get(0) <= 0) {
            throw new IllegalArgumentException("first NAV must be > 0");
        }
    }

    /** Period-over-period simple returns (length n-1). */
    static double[] periodReturns(List<Double> navs) {
        double[] r = new double[navs.size() - 1];
        for (int i = 1; i < navs.size(); i++) {
            double prev = navs.get(i - 1);
            r[i - 1] = prev == 0 ? 0 : navs.get(i) / prev - 1.0;
        }
        return r;
    }

    public static double cumulativeReturn(List<Double> navs) {
        requireSeries(navs);
        return Calc.rate(navs.get(navs.size() - 1) / navs.get(0) - 1.0);
    }

    /** CAGR using the number of periods and periodsPerYear. */
    public static double annualizedReturn(List<Double> navs, double periodsPerYear) {
        requireSeries(navs);
        double years = (navs.size() - 1) / periodsPerYear;
        if (years <= 0) {
            return 0;
        }
        double total = navs.get(navs.size() - 1) / navs.get(0);
        return Calc.rate(Math.pow(total, 1.0 / years) - 1.0);
    }

    public static double annualizedVol(List<Double> navs, double periodsPerYear) {
        requireSeries(navs);
        double[] r = periodReturns(navs);
        double mean = 0;
        for (double x : r) {
            mean += x;
        }
        mean /= r.length;
        double var = 0;
        for (double x : r) {
            var += (x - mean) * (x - mean);
        }
        var = r.length > 1 ? var / (r.length - 1) : 0; // sample variance
        return Calc.rate(Math.sqrt(var) * Math.sqrt(periodsPerYear));
    }

    public static double sharpe(List<Double> navs, double periodsPerYear, double riskFreeRate) {
        double vol = annualizedVol(navs, periodsPerYear);
        if (vol <= 0) {
            return 0;
        }
        return Calc.rate((annualizedReturn(navs, periodsPerYear) - riskFreeRate) / vol);
    }

    /** Maximum peak-to-trough decline as a negative fraction (0 if monotonic up). */
    public static double maxDrawdown(List<Double> navs) {
        requireSeries(navs);
        double peak = navs.get(0);
        double worst = 0;
        for (double v : navs) {
            if (v > peak) {
                peak = v;
            }
            double dd = peak == 0 ? 0 : v / peak - 1.0;
            if (dd < worst) {
                worst = dd;
            }
        }
        return Calc.rate(worst);
    }

    public static double calmar(List<Double> navs, double periodsPerYear) {
        double mdd = maxDrawdown(navs);
        if (mdd == 0) {
            return 0;
        }
        return Calc.rate(annualizedReturn(navs, periodsPerYear) / Math.abs(mdd));
    }

    /** Beta vs a benchmark NAV series (returns 0 if benchmark missing/degenerate). */
    public static double beta(List<Double> navs, List<Double> benchmark) {
        if (benchmark == null || benchmark.size() != navs.size() || navs.size() < 2) {
            return 0;
        }
        double[] rf = periodReturns(navs);
        double[] rb = periodReturns(benchmark);
        double mf = mean(rf);
        double mb = mean(rb);
        double cov = 0;
        double varb = 0;
        for (int i = 0; i < rf.length; i++) {
            cov += (rf[i] - mf) * (rb[i] - mb);
            varb += (rb[i] - mb) * (rb[i] - mb);
        }
        return varb == 0 ? 0 : Calc.rate(cov / varb);
    }

    /** Annualised Jensen's alpha vs a benchmark; 0 if benchmark missing. */
    public static double alpha(List<Double> navs, List<Double> benchmark, double periodsPerYear,
            double riskFreeRate) {
        if (benchmark == null || benchmark.size() != navs.size()) {
            return 0;
        }
        double b = beta(navs, benchmark);
        double rFund = annualizedReturn(navs, periodsPerYear);
        double rBench = annualizedReturn(benchmark, periodsPerYear);
        return Calc.rate(rFund - (riskFreeRate + b * (rBench - riskFreeRate)));
    }

    public static FundMetrics metrics(List<Double> navs, List<Double> benchmark, double periodsPerYear,
            double riskFreeRate) {
        return new FundMetrics(
                cumulativeReturn(navs), annualizedReturn(navs, periodsPerYear),
                annualizedVol(navs, periodsPerYear), sharpe(navs, periodsPerYear, riskFreeRate),
                maxDrawdown(navs), calmar(navs, periodsPerYear),
                beta(navs, benchmark), alpha(navs, benchmark, periodsPerYear, riskFreeRate));
    }

    /**
     * A coarse, documented rating: Sharpe ≥ 1.0 with drawdown shallower than −35%
     * is PREFERRED; non-positive Sharpe or a drawdown beyond −60% is AVOID; else
     * NEUTRAL. A real desk would calibrate per fund category.
     */
    public static Rating rate(FundMetrics m) {
        if (m.sharpe() <= 0 || m.maxDrawdown() <= -0.60) {
            return Rating.AVOID;
        }
        if (m.sharpe() >= 1.0 && m.maxDrawdown() > -0.35) {
            return Rating.PREFERRED;
        }
        return Rating.NEUTRAL;
    }

    private static double mean(double[] a) {
        double s = 0;
        for (double x : a) {
            s += x;
        }
        return a.length == 0 ? 0 : s / a.length;
    }
}
