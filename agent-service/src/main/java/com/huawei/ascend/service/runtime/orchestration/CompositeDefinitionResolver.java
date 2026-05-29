package com.huawei.ascend.service.runtime.orchestration;

import com.huawei.ascend.bus.spi.engine.DefinitionRef;
import com.huawei.ascend.bus.spi.engine.DefinitionResolver;
import com.huawei.ascend.bus.spi.engine.ExecutorDefinition;
import com.huawei.ascend.service.runtime.capability.Capability;
import com.huawei.ascend.service.runtime.capability.CapabilityRegistry;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Service-side {@link DefinitionResolver}: resolves a {@link DefinitionRef} either to a
 * registered {@link Capability}'s definition (named, top-level) or to a transient definition
 * (child runs have no capability name). {@link #referenceFor} registers a transient definition
 * under a synthetic reference so the in-JVM engine resolves it back. Dev-posture only (the
 * in-memory orchestrator path is gated to dev); the transient map is bounded by the number of
 * distinct dispatched definitions in a session.
 */
public final class CompositeDefinitionResolver implements DefinitionResolver {

    private final CapabilityRegistry capabilities;
    private final ConcurrentHashMap<String, ExecutorDefinition> transientDefs = new ConcurrentHashMap<>();
    private final AtomicLong seq = new AtomicLong();

    public CompositeDefinitionResolver(CapabilityRegistry capabilities) {
        this.capabilities = Objects.requireNonNull(capabilities, "capabilities is required");
    }

    @Override
    public Optional<ExecutorDefinition> resolve(DefinitionRef ref) {
        Objects.requireNonNull(ref, "ref is required");
        ExecutorDefinition tr = transientDefs.get(ref.capabilityName());
        if (tr != null) {
            return Optional.of(tr);
        }
        return capabilities.resolve(ref.capabilityName()).map(Capability::definition);
    }

    @Override
    public DefinitionRef referenceFor(ExecutorDefinition def) {
        Objects.requireNonNull(def, "def is required");
        String key = "inproc-def-" + seq.incrementAndGet();
        transientDefs.put(key, def);
        return new DefinitionRef(key);
    }
}
