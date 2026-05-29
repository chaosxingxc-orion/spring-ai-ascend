package com.huawei.ascend.service.runtime.orchestration;

import com.huawei.ascend.bus.spi.engine.ExecutionContext;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ADR-0158 boundary invariant: the neutral, engine-facing {@link ExecutionContext} carries NO
 * tenant or session semantics. Tenant/session live only on the Service-side {@code RunContext}
 * subtype. A method named {@code tenantId} or {@code sessionId} appearing on the neutral surface
 * would leak Service-owned ownership across the EnginePort boundary.
 */
class EngineFacingContextHasNoTenantSessionTest {

    @Test
    void execution_context_declares_no_tenant_or_session_method() {
        for (Method m : ExecutionContext.class.getMethods()) {
            assertThat(m.getName())
                    .as("ExecutionContext must not expose %s across the neutral boundary", m.getName())
                    .isNotEqualTo("tenantId")
                    .isNotEqualTo("sessionId");
        }
        for (Method m : ExecutionContext.class.getDeclaredMethods()) {
            assertThat(m.getName())
                    .as("ExecutionContext must not declare %s", m.getName())
                    .isNotEqualTo("tenantId")
                    .isNotEqualTo("sessionId");
        }
    }
}
