package ascend.springai.bus.spi.ingress;

/**
 * Single cross-plane control surface for client-to-server ingress traffic.
 *
 * <p>Authority: ADR-0089 (Edge-Plane Ingress Gateway Mandate); CLAUDE.md
 * Rule R-I sub-clause .b (Edge↔Compute Ingress Routing). Schema:
 * {@code docs/contracts/ingress-envelope.v1.yaml}.
 *
 * <p>Topology invariant — modules whose {@code deployment_plane} is
 * {@code edge} (today: {@code agent-client}) MUST NOT directly import
 * production classes from {@code compute_control}-plane modules
 * ({@code agent-service}, {@code agent-execution-engine},
 * {@code agent-middleware}) and MUST NOT invoke {@code compute_control}
 * HTTP routes directly. Every C2S call flows through this SPI; the bus
 * forwards onto the {@code data} channel of {@code bus-channels.yaml}
 * (Rule R-E) and the runtime returns a {@link IngressResponse} carrying
 * either a Task Cursor (Rule R-F) or a rejection.
 *
 * <p>Status (W1, rc13 — 2026-05-20): SPI interface shipped; no runtime
 * binding. The {@code ingress-envelope.v1.yaml} contract carries
 * {@code status: design_only}; runtime promotion to
 * {@code runtime_enforced} is triggered when the agent-client SDK lands
 * (ADR-0049 / W3+). Until then, the negative invariant is enforced by
 * ArchUnit ({@code EdgeToComputeDirectLinkArchTest}, E143) and gate Rule
 * 105 ({@code edge_no_direct_compute_link}).
 *
 * <p>Pure Java — no Spring, no Reactor, no Jackson imports. SPI purity
 * enforced by {@code SpiPurityGeneralizedArchTest}.
 */
public interface IngressGateway {

    /**
     * Forward a client-originated request to the compute_control plane and
     * return the synchronous acknowledgement envelope. Long-running work
     * MUST return a Task Cursor in {@link IngressResponse#cursor()} (Rule
     * R-F); the client polls / subscribes via SSE / receives webhooks for
     * outcome, never blocking on this call.
     *
     * @param envelope client request; non-null, validated by record's
     *                 compact constructor
     * @return the bus-side acknowledgement; non-null
     */
    IngressResponse routeClientRequest(IngressEnvelope envelope);
}
