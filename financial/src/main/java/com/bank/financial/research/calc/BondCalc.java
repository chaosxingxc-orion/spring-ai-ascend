package com.bank.financial.research.calc;

/**
 * Fixed-income analytics — the bond report's numeric backbone, the analog of DCF
 * for a stock or {@link FundCalc} for a fund. Everything is computed deterministically
 * from the cash-flow terms (face value, coupon, periods, market price); the language
 * model only narrates the result.
 *
 * <p>Conventions: one coupon per period; {@code couponRate} and {@code benchmarkYield}
 * are annual fractions (0.045 = 4.5%) on a one-coupon-per-year schedule, so the
 * solved {@code ytm} is a per-period == annual yield. The credit spread is the
 * issuer YTM minus the risk-free benchmark of the same horizon. Durations are in
 * years; convexity in year². A real desk would add day-count, accrued interest and
 * multi-frequency compounding — kept single-frequency here so the math stays auditable.
 */
public final class BondCalc {

    private BondCalc() {
    }

    /** Duration / allocation stance for the issue, derived deterministically. */
    public enum Stance {
        EXTEND,  // 增持/拉久期 — spread compensates, rate view benign
        NEUTRAL, // 标配
        SHORTEN  // 回避/缩久期 — thin spread or rate/credit risk dominates
    }

    /**
     * @param ytm           solved yield-to-maturity (annual fraction)
     * @param currentYield  annual coupon / market price
     * @param macaulay      Macaulay duration (years)
     * @param modified      modified duration (years; price sensitivity to a 1.00 yield move)
     * @param convexity     convexity (year²)
     * @param creditSpread  ytm − benchmarkYield (fraction; can be negative if rich)
     */
    public record BondMetrics(
            double ytm, double currentYield, double macaulay, double modified,
            double convexity, double creditSpread) {
    }

    static void require(double faceValue, double periodsRemaining, double marketPrice) {
        if (!(faceValue > 0)) {
            throw new IllegalArgumentException("faceValue must be > 0 (was " + faceValue + ")");
        }
        if (!(periodsRemaining >= 1)) {
            throw new IllegalArgumentException("periodsRemaining must be >= 1 (was " + periodsRemaining + ")");
        }
        if (!(marketPrice > 0)) {
            throw new IllegalArgumentException("marketPrice must be > 0 (was " + marketPrice + ")");
        }
    }

    /** Present value of the bond's cash flows discounted at per-period {@code y}. */
    static double priceAt(double faceValue, double couponRate, int periods, double y) {
        double coupon = faceValue * couponRate;
        double pv = 0;
        double df = 1.0;
        double base = 1.0 + y;
        for (int t = 1; t <= periods; t++) {
            df /= base;
            pv += coupon * df;
        }
        pv += faceValue * df; // principal at maturity, same final df
        return pv;
    }

    /**
     * Solve yield-to-maturity by bisection so the discounted cash flows equal the
     * market price. Deterministic and bracketed in [-0.5, 1.0] per period; converges
     * to ~1e-8 in price. The bond-price→yield function is monotonically decreasing,
     * which makes bisection robust and assumption-free (no Newton derivative needed).
     */
    public static double ytm(double faceValue, double couponRate, int periods, double marketPrice) {
        require(faceValue, periods, marketPrice);
        double lo = -0.5;
        double hi = 1.0;
        // priceAt is decreasing in y: price(lo) is the highest, price(hi) the lowest.
        double pLo = priceAt(faceValue, couponRate, periods, lo);
        double pHi = priceAt(faceValue, couponRate, periods, hi);
        if (marketPrice >= pLo) {
            return Calc.rate(lo);
        }
        if (marketPrice <= pHi) {
            return Calc.rate(hi);
        }
        for (int i = 0; i < 200; i++) {
            double mid = (lo + hi) / 2.0;
            double pMid = priceAt(faceValue, couponRate, periods, mid);
            if (Math.abs(pMid - marketPrice) < 1e-8) {
                return Calc.rate(mid);
            }
            if (pMid > marketPrice) {
                lo = mid; // price too high → raise yield
            } else {
                hi = mid;
            }
        }
        return Calc.rate((lo + hi) / 2.0);
    }

    public static double currentYield(double faceValue, double couponRate, double marketPrice) {
        require(faceValue, 1, marketPrice);
        return Calc.rate(faceValue * couponRate / marketPrice);
    }

    /** Macaulay duration in years: PV-weighted average time to each cash flow, discounted at {@code y}. */
    public static double macaulayDuration(double faceValue, double couponRate, int periods, double y) {
        require(faceValue, periods, 1.0);
        double coupon = faceValue * couponRate;
        double base = 1.0 + y;
        double price = 0;
        double weightedTime = 0;
        double df = 1.0;
        for (int t = 1; t <= periods; t++) {
            df /= base;
            double cf = coupon + (t == periods ? faceValue : 0.0);
            double pv = cf * df;
            price += pv;
            weightedTime += t * pv;
        }
        return price == 0 ? 0 : Calc.rate(weightedTime / price);
    }

    /** Modified duration = Macaulay / (1 + y): the −dP/P per unit yield change. */
    public static double modifiedDuration(double faceValue, double couponRate, int periods, double y) {
        return Calc.rate(macaulayDuration(faceValue, couponRate, periods, y) / (1.0 + y));
    }

    /** Convexity (year²): PV-weighted t(t+1)/(1+y)² — the second-order price/yield curvature. */
    public static double convexity(double faceValue, double couponRate, int periods, double y) {
        require(faceValue, periods, 1.0);
        double coupon = faceValue * couponRate;
        double base = 1.0 + y;
        double price = 0;
        double weighted = 0;
        double df = 1.0;
        for (int t = 1; t <= periods; t++) {
            df /= base;
            double cf = coupon + (t == periods ? faceValue : 0.0);
            double pv = cf * df;
            price += pv;
            weighted += t * (t + 1) * pv;
        }
        if (price == 0) {
            return 0;
        }
        return Calc.rate(weighted / (price * base * base));
    }

    /** Credit spread = issuer YTM − risk-free benchmark yield of the same horizon. */
    public static double creditSpread(double ytm, double benchmarkYield) {
        return Calc.rate(ytm - benchmarkYield);
    }

    public static BondMetrics metrics(double faceValue, double couponRate, int periodsRemaining,
            double marketPrice, double benchmarkYield) {
        double y = ytm(faceValue, couponRate, periodsRemaining, marketPrice);
        return new BondMetrics(
                y,
                currentYield(faceValue, couponRate, marketPrice),
                macaulayDuration(faceValue, couponRate, periodsRemaining, y),
                modifiedDuration(faceValue, couponRate, periodsRemaining, y),
                convexity(faceValue, couponRate, periodsRemaining, y),
                creditSpread(y, benchmarkYield));
    }

    /**
     * A coarse, documented allocation stance: a wide spread (≥ 150 bps) with
     * contained interest-rate exposure (modified duration &lt; 7y) earns EXTEND;
     * a thin/negative spread (&lt; 50 bps) OR long duration (&ge; 10y) bearing rate
     * risk earns SHORTEN; else NEUTRAL. A real desk calibrates per rating bucket and
     * rate view.
     */
    public static Stance stance(BondMetrics m) {
        if (m.creditSpread() < 0.0050 || m.modified() >= 10.0) {
            return Stance.SHORTEN;
        }
        if (m.creditSpread() >= 0.0150 && m.modified() < 7.0) {
            return Stance.EXTEND;
        }
        return Stance.NEUTRAL;
    }
}
