package com.bank.financial.research.thematic;

/**
 * A specialised member of the thematic research desk. Same contract as the equity
 * {@code ReportSubAgent} but over a {@link ThematicContext}: read the shared
 * blackboard + dataset, do one narrow job, write contributions back under owned
 * keys. The orchestrator sequences them.
 */
public interface ThematicSubAgent {

    String role();

    String capability();

    void contribute(ThematicContext ctx);
}
