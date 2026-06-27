package com.huawei.ascend.examples.workmate.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SessionPermissionModeTransitionTest {

    @Test
    void craftToAsk() {
        WorkmateSession session = newSession(PermissionMode.CRAFT);
        SessionPermissionModeTransition.apply(session, PermissionMode.ASK);
        assertThat(session.getPermissionMode()).isEqualTo(PermissionMode.ASK);
        assertThat(session.getPermissionModeBeforePlan()).isNull();
    }

    @Test
    void craftToPlanSetsBeforePlan() {
        WorkmateSession session = newSession(PermissionMode.CRAFT);
        SessionPermissionModeTransition.apply(session, PermissionMode.PLAN);
        assertThat(session.getPermissionMode()).isEqualTo(PermissionMode.PLAN);
        assertThat(session.getPermissionModeBeforePlan()).isEqualTo(PermissionMode.CRAFT);
    }

    @Test
    void planToAskClearsBeforePlan() {
        WorkmateSession session = newSession(PermissionMode.PLAN);
        session.setPermissionModeBeforePlan(PermissionMode.CRAFT);
        SessionPermissionModeTransition.apply(session, PermissionMode.ASK);
        assertThat(session.getPermissionMode()).isEqualTo(PermissionMode.ASK);
        assertThat(session.getPermissionModeBeforePlan()).isNull();
    }

    @Test
    void planToCraftRejected() {
        WorkmateSession session = newSession(PermissionMode.PLAN);
        assertThatThrownBy(() -> SessionPermissionModeTransition.apply(session, PermissionMode.CRAFT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("plan/confirm");
    }

    @Test
    void archivedSessionRejected() {
        WorkmateSession session = newSession(PermissionMode.CRAFT);
        session.setArchivedAt(Instant.now());
        assertThatThrownBy(() -> SessionPermissionModeTransition.apply(session, PermissionMode.ASK))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Archived");
    }

    @Test
    void sameModeIsNoOp() {
        WorkmateSession session = newSession(PermissionMode.ASK);
        SessionPermissionModeTransition.apply(session, PermissionMode.ASK);
        assertThat(session.getPermissionMode()).isEqualTo(PermissionMode.ASK);
    }

    private static WorkmateSession newSession(PermissionMode mode) {
        return new WorkmateSession(
                UUID.randomUUID(), "test", "/tmp/ws", SessionStatus.CREATED, null, mode);
    }
}
