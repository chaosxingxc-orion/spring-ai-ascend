package com.huawei.ascend.collab.eval;

import com.huawei.ascend.collab.core.CollaborationResult;
import com.huawei.ascend.collab.core.WorkResult;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Runs eval scenarios and scores each against its expectations: every task's
 * final status must match, and every required coordination-event type must have
 * occurred. Deterministic — same eval set always yields the same score.
 */
public final class EvalRunner {

    private EvalRunner() {
    }

    public record CaseResult(String scenario, boolean passed, List<String> failures,
            int tasks, long completed) {
    }

    public static CaseResult score(EvalScenario s) {
        CollaborationResult r = s.run();
        List<String> failures = new ArrayList<>();

        s.expectedOutcomes().forEach((taskId, expected) -> {
            WorkResult.Status actual = r.outcomes().get(taskId);
            if (actual == null || !actual.name().equals(expected)) {
                failures.add("task " + taskId + " expected " + expected + " but got " + actual);
            }
        });

        Set<String> seen = r.log().stream().map(e -> e.type().name()).collect(Collectors.toSet());
        for (String required : s.requiredEvents()) {
            if (!seen.contains(required)) {
                failures.add("missing required event " + required);
            }
        }

        return new CaseResult(s.name(), failures.isEmpty(), failures,
                r.outcomes().size(), r.count(WorkResult.Status.COMPLETED));
    }

    public static List<CaseResult> runAll(List<EvalScenario> scenarios) {
        return scenarios.stream().map(EvalRunner::score).toList();
    }
}
