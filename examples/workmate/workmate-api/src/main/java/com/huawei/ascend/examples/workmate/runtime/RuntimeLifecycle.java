package com.huawei.ascend.examples.workmate.runtime;

import java.net.URI;
import java.util.Map;
import java.util.Optional;

/**
 * Member agent-runtime lifecycle seam (ADR-012 / W21).
 *
 * <p>v0.3: static compose URLs + Agent Card health probe. v1.0+: spawn/sleep/destroy (REQ-P3-01).
 */
public interface RuntimeLifecycle {

    Optional<URI> memberBaseUrl(String expertId);

    boolean probeHealthy(URI baseUrl);

    /** expertId → reachable (last probe). */
    Map<String, Boolean> memberHealthSnapshot();
}
