package com.huawei.ascend.service.platform.web.runs;

import com.huawei.ascend.engine.orchestration.spi.ExecutorDefinition;
import com.huawei.ascend.engine.orchestration.spi.Orchestrator;
import com.huawei.ascend.service.runtime.capability.Capability;
import com.huawei.ascend.service.runtime.capability.CapabilityRegistry;
import com.huawei.ascend.service.runtime.runs.Run;
import com.huawei.ascend.service.runtime.runs.RunStatus;
import com.huawei.ascend.service.runtime.runs.spi.RunRepository;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Dev-posture dispatcher that actually executes a Run, replacing
 * {@link NoOpAsyncRunDispatcher}. Resolves the Run's {@code capabilityName}
 * to a {@link Capability} and drives it through the in-memory
 * {@link Orchestrator}; the orchestrator owns all Run-state transitions
 * (PENDING -> RUNNING -> SUCCEEDED/FAILED).
 *
 * <p>This is the dev-posture realization of the dispatcher whose production,
 * durable form is scoped to W2 in ADR-0070. It is registered (and posture-gated)
 * by {@link RunControllerAutoConfiguration}. When the capability is unknown the
 * Run is driven to FAILED so a caller polling the cursor observes a terminal
 * state instead of a permanent PENDING.
 */
public class OrchestratingAsyncRunDispatcher implements AsyncRunDispatcher {

    private static final Logger LOG = LoggerFactory.getLogger(OrchestratingAsyncRunDispatcher.class);

    private final RunRepository runs;
    private final Orchestrator orchestrator;
    private final CapabilityRegistry capabilities;

    public OrchestratingAsyncRunDispatcher(RunRepository runs,
                                           Orchestrator orchestrator,
                                           CapabilityRegistry capabilities) {
        this.runs = Objects.requireNonNull(runs, "runs");
        this.orchestrator = Objects.requireNonNull(orchestrator, "orchestrator");
        this.capabilities = Objects.requireNonNull(capabilities, "capabilities");
    }

    @Override
    public void dispatch(Run run) {
        Optional<Capability> resolved = capabilities.resolve(run.capabilityName());
        if (resolved.isEmpty()) {
            failUnknownCapability(run);
            return;
        }
        Capability capability = resolved.get();
        ExecutorDefinition definition = capability.definition();
        try {
            orchestrator.run(run.runId(), run.tenantId(), definition, capability.initialPayload());
        } catch (RuntimeException e) {
            // The orchestrator already transitioned the Run to FAILED and fired
            // the ON_ERROR hook before rethrowing; nothing to repair here. The
            // dispatcher runs fire-and-forget on the dispatch executor, so a
            // rethrow would only be swallowed by CompletableFuture.runAsync.
            LOG.warn("Run {} (capability={}) failed during execution: {}",
                    run.runId(), run.capabilityName(), e.toString());
        }
    }

    private void failUnknownCapability(Run run) {
        LOG.warn("Run {} references unknown capability '{}'; driving to FAILED",
                run.runId(), run.capabilityName());
        runs.updateIfNotTerminal(run.tenantId(), run.runId(),
                r -> r.withStatus(RunStatus.RUNNING));
        runs.updateIfNotTerminal(run.tenantId(), run.runId(),
                r -> r.withStatus(RunStatus.FAILED).withFinishedAt(Instant.now()));
    }
}
