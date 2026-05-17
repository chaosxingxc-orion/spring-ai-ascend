package ascend.springai.service.runtime.posture;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AppPostureGateTest {

    @Test
    void dev_posture_passes_without_throw() {
        assertThatCode(() -> AppPostureGate.checkPosture("TestComponent", "dev"))
                .doesNotThrowAnyException();
    }

    @Test
    void null_posture_treated_as_dev_passes_without_throw() {
        // APP_POSTURE not set → null → dev default
        assertThatCode(() -> AppPostureGate.checkPosture("TestComponent", null))
                .doesNotThrowAnyException();
    }

    @Test
    void empty_posture_treated_as_dev_passes_without_throw() {
        assertThatCode(() -> AppPostureGate.checkPosture("TestComponent", ""))
                .doesNotThrowAnyException();
    }

    @Test
    void research_posture_throws_illegal_state() {
        assertThatThrownBy(() -> AppPostureGate.checkPosture("TestComponent", "research"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("TestComponent")
                .hasMessageContaining("research")
                .hasMessageContaining("ADR-0035");
    }

    @Test
    void prod_posture_throws_illegal_state() {
        assertThatThrownBy(() -> AppPostureGate.checkPosture("TestComponent", "prod"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("TestComponent")
                .hasMessageContaining("prod")
                .hasMessageContaining("ADR-0035");
    }

    @Test
    void public_method_succeeds_in_dev_environment() {
        // In test env APP_POSTURE is not set (null → dev), so this must not throw.
        assertThatCode(() -> AppPostureGate.requireDevForInMemoryComponent("TestComponent"))
                .doesNotThrowAnyException();
    }
}
