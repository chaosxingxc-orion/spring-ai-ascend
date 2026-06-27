package com.huawei.ascend.examples.workmate.team;

import com.huawei.ascend.examples.workmate.office.CoordinationSpec.Termination;
import java.time.Instant;
import java.util.Optional;

/** Enforces coordination termination.timeBudgetMs for team runs (ADR-013 §3). */
public final class TeamTerminationGuard {

    private final Instant deadline;

    public TeamTerminationGuard(Termination termination) {
        if (termination != null
                && termination.timeBudgetMs() != null
                && termination.timeBudgetMs() > 0) {
            deadline = Instant.now().plusMillis(termination.timeBudgetMs());
        } else {
            deadline = null;
        }
    }

    public boolean expired() {
        return deadline != null && Instant.now().isAfter(deadline);
    }

    Optional<String> expiredMessage() {
        if (expired()) {
            return Optional.of("team time budget exceeded");
        }
        return Optional.empty();
    }

    Long remainingMs() {
        if (deadline == null) {
            return null;
        }
        long ms = deadline.toEpochMilli() - Instant.now().toEpochMilli();
        return Math.max(0, ms);
    }
}
