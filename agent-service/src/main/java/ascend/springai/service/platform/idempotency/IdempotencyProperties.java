package ascend.springai.service.platform.idempotency;

import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Configuration for the L1 idempotency dedup store (ADR-0057).
 *
 * <p>Bound from {@code app.idempotency.*}.
 *
 * <ul>
 *   <li>{@code ttl} — sliding window for the {@code expires_at} column.
 *       After this many seconds, a {@code FAILED} or stale {@code CLAIMED}
 *       row may be re-claimed (W2). Default {@code PT24H}.</li>
 *   <li>{@code allowInMemory} — opt-in for the in-memory store implementation.
 *       Honoured only when {@code app.posture=dev}; {@code PostureBootGuard}
 *       (Phase F) aborts startup if this is {@code true} elsewhere. Default
 *       {@code false}.</li>
 * </ul>
 *
 * <p>Enforcer rows: docs/governance/enforcers.yaml#E12 (TTL semantics), #E22
 * (posture × allow-in-memory matrix).
 */
@ConfigurationProperties(prefix = "app.idempotency")
public record IdempotencyProperties(
        @NotNull Duration ttl,
        boolean allowInMemory
) {

    public IdempotencyProperties {
        if (ttl == null) {
            ttl = Duration.ofHours(24);
        }
    }
}
