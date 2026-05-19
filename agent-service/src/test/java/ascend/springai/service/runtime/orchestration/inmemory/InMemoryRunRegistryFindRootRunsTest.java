package ascend.springai.service.runtime.orchestration.inmemory;

import ascend.springai.service.runtime.runs.Run;
import ascend.springai.engine.orchestration.spi.RunMode;
import ascend.springai.service.runtime.runs.RunStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies RunRepository.findRootRuns — returns only runs with parentRunId == null
 * for the specified tenant (§4 #29 scope-based hierarchy support).
 *
 * <p>TCK-promotion-candidate: when agent-runtime-tck is created on Rule R-D.a.b
 * trigger, this class lifts-and-shifts as the RunRepository.findRootRuns
 * conformance test. The tenant-scoping + parent-null semantics MUST be honoured
 * by any alternative RunRepository impl (Postgres, Temporal, Redis).
 * See docs/CLAUDE-deferred.md Rule R-D.a.b "Pre-promotion holding tank".
 */
class InMemoryRunRegistryFindRootRunsTest {

    private InMemoryRunRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new InMemoryRunRegistry();
    }

    @Test
    void returns_empty_when_no_runs_exist() {
        assertThat(registry.findRootRuns("t1")).isEmpty();
    }

    @Test
    void returns_root_runs_for_tenant() {
        UUID rootId = UUID.randomUUID();
        UUID childId = UUID.randomUUID();
        registry.save(rootRun(rootId, "t1"));
        registry.save(childRun(childId, "t1", rootId));

        List<Run> roots = registry.findRootRuns("t1");
        assertThat(roots).hasSize(1);
        assertThat(roots.get(0).runId()).isEqualTo(rootId);
    }

    @Test
    void tenant_scoping_excludes_other_tenants() {
        registry.save(rootRun(UUID.randomUUID(), "tenant-a"));
        registry.save(rootRun(UUID.randomUUID(), "tenant-b"));

        assertThat(registry.findRootRuns("tenant-a")).hasSize(1);
        assertThat(registry.findRootRuns("tenant-b")).hasSize(1);
    }

    @Test
    void child_of_child_is_not_a_root_run() {
        UUID root = UUID.randomUUID();
        UUID mid = UUID.randomUUID();
        UUID leaf = UUID.randomUUID();
        registry.save(rootRun(root, "t1"));
        registry.save(childRun(mid, "t1", root));
        registry.save(childRun(leaf, "t1", mid));  // grandchild

        List<Run> roots = registry.findRootRuns("t1");
        assertThat(roots).hasSize(1);
        assertThat(roots.get(0).runId()).isEqualTo(root);
    }

    @Test
    void multiple_root_runs_for_same_tenant_returned_together() {
        UUID root1 = UUID.randomUUID();
        UUID root2 = UUID.randomUUID();
        registry.save(rootRun(root1, "t1"));
        registry.save(rootRun(root2, "t1"));

        assertThat(registry.findRootRuns("t1"))
                .extracting(Run::runId)
                .containsExactlyInAnyOrder(root1, root2);
    }

    private static Run rootRun(UUID runId, String tenantId) {
        return new Run(runId, tenantId, "cap", RunStatus.PENDING, RunMode.GRAPH,
                Instant.now(), null, null, null, null, null, null);
    }

    private static Run childRun(UUID runId, String tenantId, UUID parentRunId) {
        return new Run(runId, tenantId, "cap", RunStatus.PENDING, RunMode.GRAPH,
                Instant.now(), null, null, parentRunId, null, null, null);
    }
}
