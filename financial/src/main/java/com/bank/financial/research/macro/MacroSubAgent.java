package com.bank.financial.research.macro;

/**
 * A specialised member of the macro & policy desk. Reads the shared blackboard +
 * dataset, does one narrow job, writes contributions back under owned keys; the
 * orchestrator sequences them.
 */
public interface MacroSubAgent {

    String role();

    String capability();

    void contribute(MacroContext ctx);
}
