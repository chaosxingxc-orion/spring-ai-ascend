package com.bank.financial.research.model;

/**
 * The language-generation seam for the research-report sub-agents. Numbers are
 * never produced here — they are computed in {@code research.calc} and read from
 * the blackboard. The model only turns a structured, fact-bearing brief into
 * prose (outline, narrative, section copy, qualitative critique).
 *
 * <p>Two implementations:
 * <ul>
 *   <li>{@link ScriptedReportModel} — deterministic; used by tests and the
 *       {@code --mock} playground so the full pipeline runs offline with
 *       byte-identical output.</li>
 *   <li>{@link OpenJiuwenReportModel} — the production path, backed by a real LLM
 *       via the kit's {@code ModelConnection} / {@code ReActAgent}.</li>
 * </ul>
 */
public interface ReportModel {

    String name();

    /** Turn a fact-bearing brief into prose for one sub-agent's contribution. */
    String generate(ModelTask task);

    /**
     * @param role        the requesting sub-agent (planner, writer, critic, …)
     * @param instruction what to produce (the role's ask)
     * @param brief       the canonical facts + outline the prose must be grounded in
     * @param maxWords    soft length budget (the engine enforces the hard token budget)
     */
    record ModelTask(String role, String instruction, String brief, int maxWords) {
    }
}
