package com.huawei.ascend.examples.a2a.gateway;

import com.huawei.ascend.examples.a2a.gateway.core.InMemoryRouteGrantCache;
import com.huawei.ascend.examples.a2a.gateway.model.RouteCacheKey;
import com.huawei.ascend.examples.a2a.gateway.model.RouteGrant;
import com.huawei.ascend.examples.a2a.gateway.model.RuntimeInstanceId;
import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class InMemoryRouteGrantCacheTest {

    private static final Instant NOW = Instant.parse("2026-06-05T10:00:00Z");

    @Test
    void cacheReturnsUnexpiredGrantAndEvictsExpiredGrant() {
        MutableClock clock = new MutableClock(NOW);
        InMemoryRouteGrantCache cache = new InMemoryRouteGrantCache(clock);
        RouteGrant grant = grant(1, NOW.plusSeconds(5));

        cache.put(grant);

        assertThat(cache.get(key())).contains(grant);

        clock.set(NOW.plusSeconds(6));

        assertThat(cache.get(key())).isEmpty();
        assertThat(cache.size()).isZero();
    }

    @Test
    void invalidateByPolicyVersionRemovesStaleTenantGrants() {
        InMemoryRouteGrantCache cache = new InMemoryRouteGrantCache(Clock.fixed(NOW, ZoneOffset.UTC));
        cache.put(grant(1, NOW.plusSeconds(60)));

        cache.invalidateByPolicyVersion("tenant-a", 1);

        assertThat(cache.get(key())).isEmpty();
    }

    private static RouteCacheKey key() {
        return new RouteCacheKey("tenant-a", "agent-a", "agent-b", "message/stream");
    }

    private static RouteGrant grant(long policyVersion, Instant expiresAt) {
        return new RouteGrant(
                "grant-1",
                "tenant-a",
                "agent-a",
                "agent-b",
                RuntimeInstanceId.of("runtime-b"),
                URI.create("http://runtime-b.example/a2a"),
                Set.of("message/stream"),
                policyVersion,
                NOW,
                expiresAt,
                "signature");
    }

    private static final class MutableClock extends Clock {

        private final AtomicReference<Instant> instant;

        private MutableClock(Instant instant) {
            this.instant = new AtomicReference<>(instant);
        }

        private void set(Instant instant) {
            this.instant.set(instant);
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant.get();
        }
    }
}
