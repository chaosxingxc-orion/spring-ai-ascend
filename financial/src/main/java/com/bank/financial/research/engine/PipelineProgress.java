package com.bank.financial.research.engine;

@FunctionalInterface
public interface PipelineProgress {
    void onAgent(String role, String state, int index, int total); // state = "running" | "done"

    /** Called after an agent finishes, with the keys it newly wrote to the blackboard this turn. */
    default void onAgentDone(String role, java.util.Map<String, String> wrote) { }

    /**
     * Called once after the run assembles, with the collaboration's interaction edges
     * (handover / read / outcome). Each edge is a map with keys
     * {@code type}, {@code actor}, {@code target}, {@code detail}.
     */
    default void onInteractions(java.util.List<java.util.Map<String, String>> edges) { }

    PipelineProgress NOOP = (role, state, index, total) -> { };
}
