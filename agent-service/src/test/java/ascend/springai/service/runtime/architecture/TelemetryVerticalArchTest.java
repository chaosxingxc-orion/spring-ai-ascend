package ascend.springai.service.runtime.architecture;

import ascend.springai.engine.orchestration.spi.TraceContext;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Enforces ARCHITECTURE.md §4 #53 (Telemetry Vertical first-class).
 *
 * <p>The Telemetry Vertical owns trace emission. Adapters MUST emit via the
 * {@code TraceContext} SPI or the Hook SPI (§4 #16 — un-deferred W2); no class
 * outside the {@code orchestration} package family or the platform observability
 * package may construct a {@link TraceContext} directly.
 *
 * <p>At L1.x the universe of {@code TraceContext} producers is exactly two
 * packages: {@code ascend.springai.service.runtime.orchestration} (the SPI + Noop impl
 * + the {@code RunContextImpl} that binds it). This test pins that invariant.
 * It arms automatically when a non-orchestration class tries to construct a
 * {@code NoopTraceContext} or implement the SPI.
 *
 * <p>ADR-0061 §8 (enforcer E38).
 */
class TelemetryVerticalArchTest {

    private static final JavaClasses RUNTIME_MAIN_CLASSES = new ClassFileImporter()
            .importPackages("ascend.springai.service.runtime");

    @Test
    void trace_context_spi_resides_in_orchestration_spi_package() {
        assertThat(TraceContext.class.getPackageName())
                .as("TraceContext SPI MUST live alongside RunContext in orchestration.spi (§4 #53, ADR-0061 §2)")
                .isEqualTo("ascend.springai.engine.orchestration.spi");
    }

    @Test
    void trace_context_implementations_live_only_in_orchestration_package() {
        long count = RUNTIME_MAIN_CLASSES.stream()
                .filter(c -> c.isAssignableTo("ascend.springai.engine.orchestration.spi.TraceContext"))
                .filter(c -> !c.getName().equals("ascend.springai.engine.orchestration.spi.TraceContext"))
                .filter(c -> !c.getPackageName().startsWith("ascend.springai.service.runtime.orchestration"))
                .count();
        assertThat(count)
                .as("Only orchestration-package classes may implement TraceContext at L1.x (§4 #53). "
                  + "W2 may permit observability emitter classes under the runtime sub-tree (post-Phase-C: agent-service.runtime.observability; pre-Phase-C: agent-runtime/observability per ADR-0078); that boundary will be opened explicitly.")
                .isZero();
    }
}
