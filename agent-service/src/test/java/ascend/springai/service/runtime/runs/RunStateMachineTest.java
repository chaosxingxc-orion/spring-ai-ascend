package ascend.springai.service.runtime.runs;

import ascend.springai.engine.orchestration.spi.RunMode;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Exhaustive DFA tests for RunStateMachine — §4 #20, ADR-0020.
 */
class RunStateMachineTest {

    // ── Legal transitions (all edges in the DFA must pass) ──────────────────

    @ParameterizedTest(name = "PENDING → {0}")
    @EnumSource(value = RunStatus.class, names = {"RUNNING", "CANCELLED"})
    void pending_to_allowed_transitions(RunStatus to) {
        RunStateMachine.validate(RunStatus.PENDING, to);
    }

    @ParameterizedTest(name = "RUNNING → {0}")
    @EnumSource(value = RunStatus.class, names = {"SUSPENDED", "SUCCEEDED", "FAILED", "CANCELLED"})
    void running_to_allowed_transitions(RunStatus to) {
        RunStateMachine.validate(RunStatus.RUNNING, to);
    }

    @ParameterizedTest(name = "SUSPENDED → {0}")
    @EnumSource(value = RunStatus.class, names = {"RUNNING", "EXPIRED", "FAILED", "CANCELLED"})
    void suspended_to_allowed_transitions(RunStatus to) {
        RunStateMachine.validate(RunStatus.SUSPENDED, to);
    }

    @Test
    void failed_can_retry_to_running() {
        RunStateMachine.validate(RunStatus.FAILED, RunStatus.RUNNING);
    }

    // ── Illegal transitions (forbidden edges must throw) ────────────────────

    @ParameterizedTest(name = "SUCCEEDED → {0} is terminal")
    @EnumSource(value = RunStatus.class, names = {"RUNNING", "PENDING", "CANCELLED", "FAILED", "SUSPENDED", "EXPIRED"})
    void succeeded_is_terminal(RunStatus to) {
        assertThatThrownBy(() -> RunStateMachine.validate(RunStatus.SUCCEEDED, to))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("SUCCEEDED");
    }

    @ParameterizedTest(name = "CANCELLED → {0} is terminal")
    @EnumSource(value = RunStatus.class, names = {"RUNNING", "PENDING", "SUCCEEDED", "FAILED", "SUSPENDED", "EXPIRED"})
    void cancelled_is_terminal(RunStatus to) {
        assertThatThrownBy(() -> RunStateMachine.validate(RunStatus.CANCELLED, to))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("CANCELLED");
    }

    @ParameterizedTest(name = "EXPIRED → {0} is terminal")
    @EnumSource(value = RunStatus.class, names = {"RUNNING", "PENDING", "SUCCEEDED", "FAILED", "SUSPENDED", "CANCELLED"})
    void expired_is_terminal(RunStatus to) {
        assertThatThrownBy(() -> RunStateMachine.validate(RunStatus.EXPIRED, to))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("EXPIRED");
    }

    @Test
    void failed_cannot_go_to_cancelled_directly() {
        // FAILED is NOT terminal — retry to RUNNING is the only exit
        assertThatThrownBy(() -> RunStateMachine.validate(RunStatus.FAILED, RunStatus.CANCELLED))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void pending_cannot_jump_to_succeeded() {
        assertThatThrownBy(() -> RunStateMachine.validate(RunStatus.PENDING, RunStatus.SUCCEEDED))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void pending_cannot_jump_to_suspended() {
        assertThatThrownBy(() -> RunStateMachine.validate(RunStatus.PENDING, RunStatus.SUSPENDED))
                .isInstanceOf(IllegalStateException.class);
    }

    // ── isTerminal helper ────────────────────────────────────────────────────

    @ParameterizedTest(name = "{0} is terminal")
    @EnumSource(value = RunStatus.class, names = {"SUCCEEDED", "CANCELLED", "EXPIRED"})
    void terminal_statuses(RunStatus s) {
        assertThat(RunStateMachine.isTerminal(s)).isTrue();
    }

    @ParameterizedTest(name = "{0} is not terminal")
    @EnumSource(value = RunStatus.class, names = {"PENDING", "RUNNING", "SUSPENDED", "FAILED"})
    void non_terminal_statuses(RunStatus s) {
        assertThat(RunStateMachine.isTerminal(s)).isFalse();
    }

    // ── allowedTransitions helper ────────────────────────────────────────────

    @Test
    void running_allowed_transitions_correct() {
        assertThat(RunStateMachine.allowedTransitions(RunStatus.RUNNING))
                .containsExactlyInAnyOrder(
                        RunStatus.SUSPENDED, RunStatus.SUCCEEDED,
                        RunStatus.FAILED, RunStatus.CANCELLED);
    }

    @Test
    void suspended_allowed_transitions_correct() {
        assertThat(RunStateMachine.allowedTransitions(RunStatus.SUSPENDED))
                .containsExactlyInAnyOrder(
                        RunStatus.RUNNING, RunStatus.EXPIRED,
                        RunStatus.FAILED, RunStatus.CANCELLED);
    }

    // ── Run.withStatus enforcement (validates DFA is wired to the record) ───

    @Test
    void run_with_status_accepts_legal_transition() {
        Run run = makeRun(RunStatus.PENDING);
        Run running = run.withStatus(RunStatus.RUNNING);
        assertThat(running.status()).isEqualTo(RunStatus.RUNNING);
    }

    @Test
    void run_with_status_rejects_illegal_transition() {
        Run run = makeRun(RunStatus.SUCCEEDED);
        assertThatThrownBy(() -> run.withStatus(RunStatus.RUNNING))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void run_with_suspension_accepts_legal_from_running() {
        Run run = makeRun(RunStatus.RUNNING);
        Run suspended = run.withSuspension("nodeA", java.time.Instant.now());
        assertThat(suspended.status()).isEqualTo(RunStatus.SUSPENDED);
    }

    @Test
    void run_with_suspension_rejects_from_succeeded() {
        Run run = makeRun(RunStatus.SUCCEEDED);
        assertThatThrownBy(() -> run.withSuspension("nodeA", java.time.Instant.now()))
                .isInstanceOf(IllegalStateException.class);
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private static Run makeRun(RunStatus status) {
        return new Run(java.util.UUID.randomUUID(), "tenant-1", "test-cap",
                status, RunMode.GRAPH, java.time.Instant.now(),
                null, null, null, null, null, null);
    }
}
