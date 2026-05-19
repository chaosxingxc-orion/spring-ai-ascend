package ascend.springai.service.runtime.resilience.spi;

/**
 * Outcome of {@link ResilienceContract#resolve(String, String)}. Either the caller is
 * admitted ({@code admitted = true} and {@code reasonIfRejected = null}) or the caller
 * is rejected ({@code admitted = false} and {@code reasonIfRejected} non-null carrying
 * the {@link SuspendReason} the scheduler should map to a suspension transition).
 *
 * <p>Per Rule R-K / Layer-0 principle P-K (Skill-Dimensional Resource Arbitration),
 * a rejection returns a decision envelope only — NOT "fail the run". The W2
 * scheduler admission (Rule R-K.c, deferred per CLAUDE-deferred.md) maps the
 * decision envelope to {@code Run.withSuspension(...)} and frees the OS thread.
 * At W1 the caller is responsible for the transition by reading this envelope.
 *
 * <p>Authority: ADR-0070; CLAUDE.md Rule R-K (legacy 41.b).
 */
public record SkillResolution(boolean admitted, SuspendReason reasonIfRejected) {

    public static SkillResolution admit() {
        return new SkillResolution(true, null);
    }

    public static SkillResolution reject(SuspendReason reason) {
        if (reason == null) {
            throw new IllegalArgumentException("reasonIfRejected is required when admitted=false");
        }
        return new SkillResolution(false, reason);
    }
}
