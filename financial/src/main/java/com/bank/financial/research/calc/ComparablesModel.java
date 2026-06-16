package com.bank.financial.research.calc;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Relative valuation by comparable-company multiples. Applies peer-set multiples
 * (EV/EBITDA, EV/Sales, P/E) to the subject's metrics and derives an implied
 * per-share value per method, then aggregates to a median (robust to outliers)
 * and mean.
 *
 * <p>EV-based multiples need the enterprise→equity bridge (subtract net debt and
 * minorities); equity multiples (P/E) map straight to equity. Mixing these up is
 * a classic error, so the bridge is applied per method here.
 */
public final class ComparablesModel {

    private ComparablesModel() {
    }

    /**
     * @param subject   the subject company's metrics
     * @param peerMultiples median (or chosen) peer multiples to apply
     */
    public static ComparablesResult value(SubjectMetrics subject, PeerMultiples peerMultiples) {
        Calc.requirePositive("dilutedShares", subject.dilutedShares());
        Map<String, Double> impliedPerShare = new LinkedHashMap<>();

        if (peerMultiples.evEbitda() > 0 && subject.ebitda() != 0) {
            double ev = peerMultiples.evEbitda() * subject.ebitda();
            double equity = ev - subject.netDebt() - subject.minorityInterest();
            impliedPerShare.put("EV/EBITDA", Calc.money(equity / subject.dilutedShares()));
        }
        if (peerMultiples.evSales() > 0 && subject.sales() != 0) {
            double ev = peerMultiples.evSales() * subject.sales();
            double equity = ev - subject.netDebt() - subject.minorityInterest();
            impliedPerShare.put("EV/Sales", Calc.money(equity / subject.dilutedShares()));
        }
        if (peerMultiples.priceEarnings() > 0 && subject.eps() != 0) {
            // P/E maps directly to per-share equity value.
            impliedPerShare.put("P/E", Calc.money(peerMultiples.priceEarnings() * subject.eps()));
        }

        if (impliedPerShare.isEmpty()) {
            throw new IllegalArgumentException(
                    "no applicable multiple (need a positive peer multiple and a non-zero subject metric)");
        }

        List<Double> values = new ArrayList<>(impliedPerShare.values());
        double median = median(values);
        double mean = values.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        return new ComparablesResult(impliedPerShare, Calc.money(median), Calc.money(mean));
    }

    static double median(List<Double> raw) {
        List<Double> v = new ArrayList<>(raw);
        v.sort(Double::compareTo);
        int n = v.size();
        if (n == 0) {
            return 0.0;
        }
        return n % 2 == 1 ? v.get(n / 2) : (v.get(n / 2 - 1) + v.get(n / 2)) / 2.0;
    }

    /** Subject-company metrics that the peer multiples are applied to. */
    public record SubjectMetrics(
            double ebitda, double sales, double eps,
            double netDebt, double minorityInterest, double dilutedShares) {
    }

    /** Peer-set multiples (typically the peer median). A non-positive multiple is skipped. */
    public record PeerMultiples(double evEbitda, double evSales, double priceEarnings) {
    }

    /** Implied per-share by method + the aggregate median/mean. */
    public record ComparablesResult(
            Map<String, Double> impliedPerShare, double medianPerShare, double meanPerShare) {

        public ComparablesResult {
            impliedPerShare = Map.copyOf(impliedPerShare);
        }

        /** Range spanning the min and max implied per-share, mid = median. */
        public ValueRange range() {
            double lo = impliedPerShare.values().stream().mapToDouble(Double::doubleValue).min().orElse(medianPerShare);
            double hi = impliedPerShare.values().stream().mapToDouble(Double::doubleValue).max().orElse(medianPerShare);
            return new ValueRange(Calc.money(lo), medianPerShare, Calc.money(hi));
        }
    }
}
