package ascend.springai.service.runtime.runs;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Library-mode SPI conformance test for the RunStatus DFA (Rule R-C.d + ADR-0020).
 * Pure JUnit Jupiter — no Spring context, no fixtures, no I/O. Runs in &lt;1s.
 *
 * <p>This class is part of the Rule D-3.b evidence layer + the Rule R-D.a.b TCK-promotion
 * holding tank (see docs/CLAUDE-deferred.md). When the TCK reactor module is
 * scaffolded on the first alternative-impl trigger, this class lifts unchanged
 * to {@code agent-runtime-tck/src/main/java/.../tck/}.
 */
class RunStateMachineLibraryTest {

    @ParameterizedTest(name = "{0} -> {1} is legal")
    @CsvSource({
            "PENDING,   RUNNING",
            "PENDING,   CANCELLED",
            "RUNNING,   SUSPENDED",
            "RUNNING,   SUCCEEDED",
            "RUNNING,   FAILED",
            "RUNNING,   CANCELLED",
            "SUSPENDED, RUNNING",
            "SUSPENDED, EXPIRED",
            "SUSPENDED, FAILED",
            "SUSPENDED, CANCELLED",
            "FAILED,    RUNNING"
    })
    void legal_transitions_do_not_throw(RunStatus from, RunStatus to) {
        assertThatCode(() -> RunStateMachine.validate(from, to))
                .doesNotThrowAnyException();
    }

    @ParameterizedTest(name = "{0} -> {1} is illegal")
    @CsvSource({
            "PENDING,   SUSPENDED",
            "PENDING,   SUCCEEDED",
            "PENDING,   FAILED",
            "PENDING,   EXPIRED",
            "PENDING,   PENDING",
            "RUNNING,   PENDING",
            "RUNNING,   EXPIRED",
            "RUNNING,   RUNNING",
            "SUSPENDED, PENDING",
            "SUSPENDED, SUSPENDED",
            "SUSPENDED, SUCCEEDED",
            "FAILED,    SUSPENDED",
            "FAILED,    SUCCEEDED",
            "FAILED,    FAILED",
            "FAILED,    CANCELLED",
            "FAILED,    EXPIRED",
            "SUCCEEDED, RUNNING",
            "SUCCEEDED, FAILED",
            "CANCELLED, RUNNING",
            "EXPIRED,   RUNNING"
    })
    void illegal_transitions_throw_with_diagnostic_message(RunStatus from, RunStatus to) {
        assertThatThrownBy(() -> RunStateMachine.validate(from, to))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Illegal RunStatus transition")
                .hasMessageContaining(from.name())
                .hasMessageContaining(to.name());
    }

    @Test
    void terminal_statuses_have_no_outgoing_transitions() {
        assertThat(RunStateMachine.isTerminal(RunStatus.SUCCEEDED)).isTrue();
        assertThat(RunStateMachine.isTerminal(RunStatus.CANCELLED)).isTrue();
        assertThat(RunStateMachine.isTerminal(RunStatus.EXPIRED)).isTrue();
        assertThat(RunStateMachine.allowedTransitions(RunStatus.SUCCEEDED)).isEmpty();
        assertThat(RunStateMachine.allowedTransitions(RunStatus.CANCELLED)).isEmpty();
        assertThat(RunStateMachine.allowedTransitions(RunStatus.EXPIRED)).isEmpty();
    }

    @Test
    void non_terminal_statuses_have_outgoing_transitions() {
        assertThat(RunStateMachine.isTerminal(RunStatus.PENDING)).isFalse();
        assertThat(RunStateMachine.isTerminal(RunStatus.RUNNING)).isFalse();
        assertThat(RunStateMachine.isTerminal(RunStatus.SUSPENDED)).isFalse();
        assertThat(RunStateMachine.isTerminal(RunStatus.FAILED)).isFalse();
    }

    @Test
    void allowed_transitions_set_is_unmodifiable() {
        Set<RunStatus> allowed = RunStateMachine.allowedTransitions(RunStatus.RUNNING);
        assertThatThrownBy(() -> allowed.add(RunStatus.PENDING))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void failed_can_retry_to_running() {
        assertThat(RunStateMachine.allowedTransitions(RunStatus.FAILED))
                .containsExactly(RunStatus.RUNNING);
    }

    @Test
    void suspended_can_expire_to_terminal() {
        assertThat(RunStateMachine.allowedTransitions(RunStatus.SUSPENDED))
                .contains(RunStatus.EXPIRED);
    }
}
