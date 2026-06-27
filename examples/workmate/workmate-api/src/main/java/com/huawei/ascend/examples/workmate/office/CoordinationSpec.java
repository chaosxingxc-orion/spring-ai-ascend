package com.huawei.ascend.examples.workmate.office;

import java.util.Set;

/**
 * 多 agent 协同拓扑（ADR-013）。pattern 决定 leader 是否存在与可视化形态。
 */
public record CoordinationSpec(String pattern, Termination termination, String acceptanceCriteria) {

    public static final String ORCHESTRATOR = "orchestrator";
    public static final String PIPELINE = "pipeline";
    public static final String AGENT_TEAM = "agent-team";
    public static final String GENERATOR_VERIFIER = "generator-verifier";
    public static final String MESSAGE_BUS = "message-bus";
    public static final String SHARED_STATE = "shared-state";

    private static final Set<String> KNOWN = Set.of(
            ORCHESTRATOR, PIPELINE, AGENT_TEAM, GENERATOR_VERIFIER, MESSAGE_BUS, SHARED_STATE);

    /** 含中心协调者（有 leader/coordinator）的拓扑。 */
    private static final Set<String> HAS_LEAD = Set.of(ORCHESTRATOR, AGENT_TEAM);

    public CoordinationSpec {
        if (pattern == null || pattern.isBlank() || !KNOWN.contains(pattern)) {
            pattern = ORCHESTRATOR;
        }
    }

    public CoordinationSpec(String pattern, Termination termination) {
        this(pattern, termination, null);
    }

    public boolean hasLead() {
        return HAS_LEAD.contains(pattern);
    }

    public static CoordinationSpec orchestrator() {
        return new CoordinationSpec(ORCHESTRATOR, null, null);
    }

    public record Termination(
            Integer maxIterations,
            Long timeBudgetMs,
            String convergence,
            String decider) {

        public boolean isEmpty() {
            return maxIterations == null && timeBudgetMs == null
                    && (convergence == null || convergence.isBlank())
                    && (decider == null || decider.isBlank());
        }
    }
}
