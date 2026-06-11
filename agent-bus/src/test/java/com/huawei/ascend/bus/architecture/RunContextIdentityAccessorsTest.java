package com.huawei.ascend.bus.architecture;

import com.huawei.ascend.bus.spi.engine.RunContext;
import com.huawei.ascend.bus.spi.engine.TraceContext;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Enforces ARCHITECTURE.md §4 #54 (Trace ↔ Run ↔ Session identity accessors).
 *
 * <p>{@code RunContext} MUST expose four identity accessors alongside the
 * existing {@code tenantId()}: {@code traceId()}, {@code spanId()},
 * {@code sessionId()} (all returning {@code String}, plain Java per SPI purity
 * §4 #7), and {@code traceContext()} returning {@link TraceContext}.
 *
 * <p>ADR-0061 §2, ADR-0062, enforcer E39.
 */
class RunContextIdentityAccessorsTest {

    @Test
    void runContext_exposes_traceId_returning_String() throws NoSuchMethodException {
        Method m = RunContext.class.getMethod("traceId");
        assertThat(m.getReturnType())
                .as("RunContext.traceId() MUST return String per §4 #54 + ADR-0061 §2 (SPI purity)")
                .isEqualTo(String.class);
    }

    @Test
    void runContext_exposes_spanId_returning_String() throws NoSuchMethodException {
        Method m = RunContext.class.getMethod("spanId");
        assertThat(m.getReturnType()).isEqualTo(String.class);
    }

    @Test
    void runContext_exposes_sessionId_returning_String() throws NoSuchMethodException {
        Method m = RunContext.class.getMethod("sessionId");
        assertThat(m.getReturnType())
                .as("§4 #54: sessionId may be null at L1.x but the accessor MUST exist returning String")
                .isEqualTo(String.class);
    }

    @Test
    void runContext_exposes_traceContext_returning_TraceContext() throws NoSuchMethodException {
        Method m = RunContext.class.getMethod("traceContext");
        assertThat(m.getReturnType())
                .as("§4 #54: traceContext() MUST return the TraceContext SPI for child-span derivation")
                .isEqualTo(TraceContext.class);
    }

    @Test
    void traceContext_returnType_is_pure_Java() {
        // Walk every method on TraceContext; assert no method references a Spring,
        // OpenTelemetry, or Micrometer type. Pure-java SPI per §4 #7 + ADR-0061 §2.
        for (Method m : TraceContext.class.getDeclaredMethods()) {
            String returnPkg = m.getReturnType().getPackageName();
            assertThat(returnPkg)
                    .as("TraceContext SPI MUST import only java.* and project types (§4 #7)")
                    .satisfiesAnyOf(
                            p -> assertThat(p).startsWith("java"),
                            p -> assertThat(p).startsWith("com.huawei.ascend.runtime"),
                            p -> assertThat(p).startsWith("com.huawei.ascend.bus.spi.engine")
                    );
        }
    }
}
