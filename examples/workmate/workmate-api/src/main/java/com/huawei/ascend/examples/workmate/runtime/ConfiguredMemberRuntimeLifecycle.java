package com.huawei.ascend.examples.workmate.runtime;

import com.huawei.ascend.examples.workmate.config.WorkmateMemberRuntimeProperties;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.a2aproject.sdk.client.http.A2ACardResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class ConfiguredMemberRuntimeLifecycle implements RuntimeLifecycle {

    private static final Logger LOG = LoggerFactory.getLogger(ConfiguredMemberRuntimeLifecycle.class);

    private final WorkmateMemberRuntimeProperties properties;
    private final Map<String, Boolean> lastProbe = new ConcurrentHashMap<>();

    public ConfiguredMemberRuntimeLifecycle(WorkmateMemberRuntimeProperties properties) {
        this.properties = properties;
    }

    @Override
    public Optional<URI> memberBaseUrl(String expertId) {
        if (!properties.isEnabled() || expertId == null || expertId.isBlank()) {
            return Optional.empty();
        }
        String raw = properties.getMembers().get(expertId);
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(URI.create(raw.trim()));
    }

    @Override
    public boolean probeHealthy(URI baseUrl) {
        try {
            A2ACardResolver.builder()
                    .baseUrl(baseUrl.toString())
                    .build()
                    .getAgentCard();
            return true;
        } catch (Exception ex) {
            LOG.warn("Member runtime health probe failed baseUrl={}", baseUrl, ex);
            return false;
        }
    }

    @Override
    public Map<String, Boolean> memberHealthSnapshot() {
        Map<String, Boolean> snapshot = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : properties.getMembers().entrySet()) {
            String expertId = entry.getKey();
            Optional<URI> base = memberBaseUrl(expertId);
            if (base.isEmpty()) {
                snapshot.put(expertId, false);
                continue;
            }
            boolean healthy = lastProbe.computeIfAbsent(expertId, id -> probeHealthy(base.get()));
            snapshot.put(expertId, healthy);
        }
        return snapshot;
    }

    public void refreshHealth() {
        for (Map.Entry<String, String> entry : properties.getMembers().entrySet()) {
            Optional<URI> base = memberBaseUrl(entry.getKey());
            if (base.isPresent()) {
                lastProbe.put(entry.getKey(), probeHealthy(base.get()));
            }
        }
    }
}
