package com.huawei.ascend.engine.runtime;

import com.huawei.ascend.bus.spi.engine.AgentEvent;
import com.huawei.ascend.bus.spi.engine.DefinitionResolver;
import com.huawei.ascend.bus.spi.engine.EngineDescriptor;
import com.huawei.ascend.bus.spi.engine.EnginePort;
import com.huawei.ascend.bus.spi.engine.ExecuteRequest;
import com.huawei.ascend.bus.spi.engine.ExecutionContext;
import com.huawei.ascend.bus.spi.engine.ExecutorDefinition;
import com.huawei.ascend.bus.spi.engine.RunContext;
import com.huawei.ascend.bus.spi.engine.SuspendSignal;
import com.huawei.ascend.engine.spi.EngineMatchingException;

import java.util.Objects;
import java.util.concurrent.Flow;

/**
 * In-process realization of {@link EnginePort}: when Service and engine share a JVM the boundary
 * is a direct call. Resolves the {@link ExecuteRequest}'s {@code DefinitionRef} to a runnable
 * {@code ExecutorDefinition} via the shared {@link DefinitionResolver}, runs it synchronously
 * through {@link EngineRegistry} strict dispatch, and emits exactly one terminal
 * {@link AgentEvent}. Suspension and failures are stashed in the {@link EngineOutcomeChannel}
 * and surfaced as INTERRUPT_REQUEST / FAILED events carrying the retrieval handle.
 */
public final class InProcessEnginePort implements EnginePort {

    private final EngineRegistry registry;
    private final DefinitionResolver resolver;
    private final EngineOutcomeChannel outcomes;

    public InProcessEnginePort(EngineRegistry registry, DefinitionResolver resolver,
                               EngineOutcomeChannel outcomes) {
        this.registry = Objects.requireNonNull(registry, "registry is required");
        this.resolver = Objects.requireNonNull(resolver, "resolver is required");
        this.outcomes = Objects.requireNonNull(outcomes, "outcomes is required");
    }

    @Override
    public Flow.Publisher<AgentEvent> execute(ExecutionContext ctx, ExecuteRequest request) {
        return new SingleEventPublisher(runToTerminalEvent(ctx, request));
    }

    private AgentEvent runToTerminalEvent(ExecutionContext ctx, ExecuteRequest request) {
        String runId = request.runId();
        try {
            ExecutorDefinition def = resolver.resolve(request.definitionRef())
                    .orElseThrow(() -> new EngineMatchingException(
                            request.engineType(), null,
                            "No ExecutorDefinition for ref=" + request.definitionRef().capabilityName()
                                    + " — engine_mismatch"));
            RunContext runCtx = (RunContext) ctx; // in-process: the context IS the Service-side subtype
            Object result = registry.resolveByPayload(def).execute(runCtx, def, request.input());
            return new AgentEvent.Finished(runId, result);
        } catch (SuspendSignal suspend) {
            String handle = outcomes.put(suspend);
            return new AgentEvent.InterruptRequest(runId, handle, "suspend", handle);
        } catch (EngineMatchingException mismatch) {
            String handle = outcomes.put(mismatch);
            return new AgentEvent.Failed(runId, "engine_mismatch", String.valueOf(mismatch.getMessage()), handle);
        } catch (RuntimeException failure) {
            String handle = outcomes.put(failure);
            return new AgentEvent.Failed(runId, failure.getClass().getName(),
                    String.valueOf(failure.getMessage()), handle);
        }
    }

    @Override
    public EngineDescriptor describe() {
        return new EngineDescriptor(registry.registeredEngineTypes(), "UP");
    }
}
