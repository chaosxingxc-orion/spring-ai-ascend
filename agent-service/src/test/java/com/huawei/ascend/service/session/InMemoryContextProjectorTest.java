package com.huawei.ascend.service.session;

import com.huawei.ascend.service.session.spi.ContextProjectionRequest;
import com.huawei.ascend.service.session.spi.ProjectedContext;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InMemoryContextProjectorTest {

    @Test
    void projectCarriesTenantSessionAndDefaultPolicy() {
        ProjectedContext projection = new InMemoryContextProjector()
                .project(ContextProjectionRequest.sessionScoped("tenant-1", "session-1"));

        assertThat(projection.tenantId()).isEqualTo("tenant-1");
        assertThat(projection.sessionId()).isEqualTo("session-1");
        assertThat(projection.projectionPolicy()).isEqualTo("last_n");
        assertThat(projection.projectionWindow()).isEqualTo(10);
        assertThat(projection.messages()).isEmpty();
        assertThat(projection.variables()).isEmpty();
        assertThat(projection.tokenCount()).isZero();
    }

    @Test
    void projectHonoursExplicitPolicyAndReportsItBack() {
        ProjectedContext projection = new InMemoryContextProjector().project(
                new ContextProjectionRequest("tenant-1", "session-1",
                        /* taskId */ null,
                        /* projectionPolicy */ "summary_v1",
                        /* tokenBudget */ 2048,
                        /* memoryRefs */ List.of("mem-ref-1")));

        assertThat(projection.projectionPolicy())
                .as("projector MUST report the policy actually applied (request's policy when non-null)")
                .isEqualTo("summary_v1");
        // summary_v1 is not windowed; window is 0.
        assertThat(projection.projectionWindow()).isZero();
    }

    @Test
    void contextProjectionRequestRejectsNullTenantId() {
        assertThatThrownBy(() -> new ContextProjectionRequest(null, "session-1", null, null, 0, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("tenantId");
    }

    @Test
    void contextProjectionRequestRejectsNullSessionId() {
        assertThatThrownBy(() -> new ContextProjectionRequest("tenant-1", null, null, null, 0, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("sessionId");
    }

    @Test
    void contextProjectionRequestRejectsNegativeTokenBudget() {
        assertThatThrownBy(() -> new ContextProjectionRequest("tenant-1", "session-1", null, null, -1, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("tokenBudget");
    }

    @Test
    void contextProjectionRequestDefensivelyCopiesMemoryRefs() {
        List<String> mutable = new java.util.ArrayList<>(List.of("ref-1"));
        ContextProjectionRequest req = new ContextProjectionRequest(
                "tenant-1", "session-1", null, null, 0, mutable);

        mutable.add("ref-2"); // caller mutates after construction
        assertThat(req.memoryRefs())
                .as("memoryRefs MUST be defensively copied")
                .containsExactly("ref-1");
        assertThatThrownBy(() -> req.memoryRefs().add("ref-3"))
                .as("memoryRefs returned by accessor MUST be immutable")
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
