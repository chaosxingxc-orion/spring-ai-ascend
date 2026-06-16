package com.bank.financial.research.agent;

import com.bank.financial.research.engine.ReportContext;

/**
 * One specialised member of the research desk. Each sub-agent reads what it needs
 * from the shared blackboard (and the assembled dataset), does its narrow job —
 * compute figures, decide the view, draft prose, or check consistency — and
 * writes its contribution back to the blackboard under keys it owns. The
 * orchestrator sequences them; they never call each other directly.
 *
 * <p>The roster mirrors a sell-side desk: Planner, Data, Quant/Model, Valuation,
 * Sector/Macro, Lead-Manager, Writer, Critic, Compliance.
 */
public interface ReportSubAgent {

    /** Stable role id; also the agentId that owns this agent's blackboard keys. */
    String role();

    /** Capability label used for the cross-run experience signature. */
    String capability();

    /** Do this agent's work for the run, reading/writing the shared blackboard. */
    void contribute(ReportContext ctx);
}
