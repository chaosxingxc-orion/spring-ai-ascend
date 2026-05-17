package ascend.springai.service.runtime.runs;

import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Enforces the RunStatus DFA defined in §4 #20 (ADR-0020).
 *
 * <p>Legal transitions:
 * <pre>
 * PENDING   → RUNNING | CANCELLED
 * RUNNING   → SUSPENDED | SUCCEEDED | FAILED | CANCELLED
 * SUSPENDED → RUNNING | EXPIRED | FAILED | CANCELLED
 * FAILED    → RUNNING (retry, new attemptId)
 * SUCCEEDED → (terminal)
 * CANCELLED → (terminal)
 * EXPIRED   → (terminal)
 * </pre>
 *
 * <p>All other transitions are illegal. Rule 20: Run.withStatus(...) MUST call
 * {@link #validate(RunStatus, RunStatus)} before constructing the updated record.
 */
public final class RunStateMachine {

    private static final Map<RunStatus, Set<RunStatus>> TRANSITIONS;

    static {
        Map<RunStatus, Set<RunStatus>> t = new EnumMap<>(RunStatus.class);
        t.put(RunStatus.PENDING,   EnumSet.of(RunStatus.RUNNING, RunStatus.CANCELLED));
        t.put(RunStatus.RUNNING,   EnumSet.of(RunStatus.SUSPENDED, RunStatus.SUCCEEDED,
                                               RunStatus.FAILED, RunStatus.CANCELLED));
        t.put(RunStatus.SUSPENDED, EnumSet.of(RunStatus.RUNNING, RunStatus.EXPIRED,
                                               RunStatus.FAILED, RunStatus.CANCELLED));
        t.put(RunStatus.FAILED,    EnumSet.of(RunStatus.RUNNING));
        t.put(RunStatus.SUCCEEDED, EnumSet.noneOf(RunStatus.class));
        t.put(RunStatus.CANCELLED, EnumSet.noneOf(RunStatus.class));
        t.put(RunStatus.EXPIRED,   EnumSet.noneOf(RunStatus.class));
        TRANSITIONS = Collections.unmodifiableMap(t);
    }

    private RunStateMachine() {}

    /**
     * Validates that the transition from {@code from} to {@code to} is legal.
     *
     * @throws IllegalStateException if the transition is not in the DFA
     */
    public static void validate(RunStatus from, RunStatus to) {
        Set<RunStatus> allowed = allowedTransitions(from);
        if (!allowed.contains(to)) {
            throw new IllegalStateException(
                "Illegal RunStatus transition: " + from + " → " + to
                + ". Allowed from " + from + ": " + allowed);
        }
    }

    /**
     * Returns the set of statuses reachable from {@code from}.
     * Returns an empty set for terminal statuses.
     */
    public static Set<RunStatus> allowedTransitions(RunStatus from) {
        return Collections.unmodifiableSet(
                TRANSITIONS.getOrDefault(from, EnumSet.noneOf(RunStatus.class)));
    }

    /**
     * Returns {@code true} if {@code status} has no outgoing transitions.
     */
    public static boolean isTerminal(RunStatus status) {
        return TRANSITIONS.getOrDefault(status, EnumSet.noneOf(RunStatus.class)).isEmpty();
    }
}
