package ascend.springai.service.runtime.runs.spi;

import ascend.springai.service.runtime.runs.Run;
import ascend.springai.service.runtime.runs.RunStatus;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * SPI for run persistence. W0 dev: InMemoryRunRegistry. W2: Spring Data JDBC CrudRepository
 * backed by Postgres (per multi_backend_checkpointer; ADR-0021 layered SPI taxonomy).
 * Pure-Java types only (no Spring imports) per ARCHITECTURE.md §4 constraint 7.
 *
 * <p>SPI-pure per CLAUDE.md Rule 32: imports {@code java.*} + sibling domain
 * value types ({@code Run}, {@code RunStatus}) which form the lifecycle
 * vocabulary this SPI persists.
 */
public interface RunRepository {
    Optional<Run> findById(UUID runId);
    Run save(Run run);
    List<Run> findByTenant(String tenantId);
    List<Run> findByParentRunId(UUID parentRunId);
    List<Run> findByTenantAndStatus(String tenantId, RunStatus status);
    /** Returns top-level runs for a tenant — runs with no parent (parentRunId == null). */
    List<Run> findRootRuns(String tenantId);
}
