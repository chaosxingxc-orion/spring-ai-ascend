package com.bank.financial.research.fund;

/** A specialised member of the fund-research desk. Same contract over a {@link FundContext}. */
public interface FundSubAgent {

    String role();

    String capability();

    void contribute(FundContext ctx);
}
