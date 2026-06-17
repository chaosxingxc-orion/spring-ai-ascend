package com.bank.financial.research.engine;

@FunctionalInterface
public interface PipelineProgress {
    void onAgent(String role, String state, int index, int total); // state = "running" | "done"

    /** Called after an agent finishes, with the keys it newly wrote to the blackboard this turn. */
    default void onAgentDone(String role, java.util.Map<String, String> wrote) { }

    PipelineProgress NOOP = (role, state, index, total) -> { };
}
