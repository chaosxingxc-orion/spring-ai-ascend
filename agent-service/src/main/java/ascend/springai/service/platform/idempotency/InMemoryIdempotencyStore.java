package ascend.springai.service.platform.idempotency;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory {@link IdempotencyStore} implementation (ADR-0057 §3).
 *
 * <p>Registered only when {@code app.posture=dev} AND
 * {@code app.idempotency.allow-in-memory=true}. Concurrent claim semantics
 * preserved via {@link ConcurrentHashMap#putIfAbsent(Object, Object)}.
 *
 * <p>Not durable: rows are lost on JVM restart. {@code research}/{@code prod}
 * MUST use {@link JdbcIdempotencyStore}; {@code PostureBootGuard} (Phase F)
 * enforces this.
 *
 * <p>Enforcer rows: docs/governance/enforcers.yaml#E22.
 */
public class InMemoryIdempotencyStore implements IdempotencyStore {

    private static final Logger LOG = LoggerFactory.getLogger(InMemoryIdempotencyStore.class);

    private final ConcurrentHashMap<Key, IdempotencyRecord> rows = new ConcurrentHashMap<>();
    private final Clock clock;
    private final Duration ttl;

    public InMemoryIdempotencyStore(Clock clock, Duration ttl) {
        this.clock = clock;
        this.ttl = ttl;
        LOG.warn("InMemoryIdempotencyStore active. NEVER use outside posture=dev; "
                + "rows are lost on JVM restart. Enforced by PostureBootGuard (ADR-0058).");
    }

    @Override
    public Optional<IdempotencyRecord> claimOrFind(UUID tenantId, UUID key, String requestHash) {
        Key composite = new Key(tenantId, key);
        Instant now = clock.instant();
        IdempotencyRecord claim = new IdempotencyRecord(
                tenantId, key, requestHash,
                Status.CLAIMED, null, null,
                now, null, now.plus(ttl));
        IdempotencyRecord prior = rows.putIfAbsent(composite, claim);
        return Optional.ofNullable(prior);
    }

    private record Key(UUID tenantId, UUID idempotencyKey) {
    }

    // package-private hook for IdempotencyDurabilityIT-style tests that need to peek state.
    Map<Key, IdempotencyRecord> snapshot() {
        return Map.copyOf(rows);
    }
}
