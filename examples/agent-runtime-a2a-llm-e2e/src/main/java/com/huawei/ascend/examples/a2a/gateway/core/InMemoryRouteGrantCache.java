package com.huawei.ascend.examples.a2a.gateway.core;

import com.huawei.ascend.examples.a2a.gateway.model.RouteCacheKey;
import com.huawei.ascend.examples.a2a.gateway.model.RouteGrant;
import java.time.Clock;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class InMemoryRouteGrantCache {

    private final Clock clock;
    private final ConcurrentHashMap<RouteCacheKey, RouteGrant> grants = new ConcurrentHashMap<>();

    public InMemoryRouteGrantCache() {
        this(Clock.systemUTC());
    }

    public InMemoryRouteGrantCache(Clock clock) {
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public Optional<RouteGrant> get(RouteCacheKey key) {
        Objects.requireNonNull(key, "key");
        RouteGrant grant = grants.get(key);
        if (grant == null) {
            return Optional.empty();
        }
        if (!grant.expiresAt().isAfter(clock.instant())) {
            grants.remove(key, grant);
            return Optional.empty();
        }
        return Optional.of(grant);
    }

    public void put(RouteGrant grant) {
        Objects.requireNonNull(grant, "grant");
        grants.put(RouteCacheKey.from(grant), grant);
    }

    public void invalidate(RouteCacheKey key) {
        grants.remove(Objects.requireNonNull(key, "key"));
    }

    public void invalidateByPolicyVersion(String tenantId, long policyVersion) {
        grants.entrySet().removeIf(entry -> entry.getValue().tenantId().equals(tenantId)
                && entry.getValue().policyVersion() <= policyVersion);
    }

    public int size() {
        return grants.size();
    }
}
