package com.bank.financial.research.bond;

/** A specialised member of the fixed-income research desk. Same contract over a {@link BondContext}. */
public interface BondSubAgent {

    String role();

    String capability();

    void contribute(BondContext ctx);
}
