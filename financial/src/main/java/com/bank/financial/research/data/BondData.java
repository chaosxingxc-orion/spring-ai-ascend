package com.bank.financial.research.data;

import java.util.List;

/** Normalised inputs for a bond / fixed-income report. */
public final class BondData {

    private BondData() {
    }

    /**
     * @param faceValue        par / redemption value per unit
     * @param couponRate       annual coupon rate as a fraction (0.045 = 4.5%)
     * @param periodsRemaining whole coupon periods until maturity (one coupon per period)
     * @param marketPrice      dirty/clean market price per unit (used to solve YTM)
     * @param benchmarkYield   risk-free benchmark yield of the same horizon, for the credit spread
     * @param rating           issuer/issue credit rating label (e.g. "AA+")
     */
    public record Dataset(
            String code, String name, String issuer,
            double faceValue, double couponRate, int periodsRemaining,
            double marketPrice, double benchmarkYield, String rating,
            Provenance provenance, List<String> freshnessWarnings) {

        public Dataset {
            rating = rating == null || rating.isBlank() ? "NR" : rating;
            freshnessWarnings = freshnessWarnings == null ? List.of() : List.copyOf(freshnessWarnings);
        }
    }
}
