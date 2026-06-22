package com.bank.financial.research.calc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bank.financial.research.calc.BondCalc.BondMetrics;
import com.bank.financial.research.calc.BondCalc.Stance;
import org.junit.jupiter.api.Test;

/** Unit layer: fixed-income analytics against hand-verified values. */
class BondCalcTest {

    private static final double EPS = 0.0008; // Calc.rate rounds to 4 dp

    @Test
    void priceAtParEqualsFaceWhenYieldMeetsCoupon() {
        assertEquals(100.0, BondCalc.priceAt(100, 0.05, 5, 0.05), 0.01);
    }

    @Test
    void ytmEqualsCouponAtPar() {
        assertEquals(0.05, BondCalc.ytm(100, 0.05, 5, 100.0), EPS);
    }

    @Test
    void ytmAboveCouponWhenBelowPar() {
        double y = BondCalc.ytm(100, 0.045, 5, 97.5);
        assertTrue(y > 0.045, "discount bond yields above coupon, got " + y);
        assertEquals(0.0508, y, 0.002);
    }

    @Test
    void creditSpreadIsYtmMinusBenchmark() {
        assertEquals(0.022, BondCalc.creditSpread(0.05, 0.028), EPS);
    }

    @Test
    void modifiedIsMacaulayOverOnePlusY() {
        double y = 0.05;
        double mac = BondCalc.macaulayDuration(100, 0.045, 5, y);
        assertEquals(mac / (1 + y), BondCalc.modifiedDuration(100, 0.045, 5, y), EPS);
        assertTrue(mac < 5.0, "coupon bond duration below maturity");
    }

    @Test
    void stubIssueIsExtendStance() {
        BondMetrics m = BondCalc.metrics(100, 0.045, 5, 97.5, 0.028);
        assertTrue(m.creditSpread() >= 0.015, "wide spread");
        assertTrue(m.modified() < 7.0, "moderate duration");
        assertEquals(Stance.EXTEND, BondCalc.stance(m));
    }

    @Test
    void shortenWhenSpreadThin() {
        assertEquals(Stance.SHORTEN, BondCalc.stance(new BondMetrics(0.03, 0.03, 4, 3.8, 18, 0.0030)));
    }

    @Test
    void rejectsBadInputs() {
        assertThrows(IllegalArgumentException.class, () -> BondCalc.ytm(0, 0.05, 5, 100));
        assertThrows(IllegalArgumentException.class, () -> BondCalc.ytm(100, 0.05, 0, 100));
    }
}
