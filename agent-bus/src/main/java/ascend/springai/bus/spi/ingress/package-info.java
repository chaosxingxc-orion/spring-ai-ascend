/**
 * agent-bus client-to-server ingress SPI — the single cross-plane control
 * surface for {@code edge} → {@code compute_control} traffic per CLAUDE.md
 * Rule R-I sub-clause .b (Edge↔Compute Ingress Routing).
 *
 * <p>Authority: ADR-0089 (Edge-Plane Ingress Gateway Mandate); contract
 * schema lives at {@code docs/contracts/ingress-envelope.v1.yaml}.
 *
 * <p>SPI-pure per Rule R-D sub-clause .d: imports restricted to
 * {@code java.*} + same-spi-package siblings. No Spring, no Reactor, no
 * Jackson. Enforced by {@code SpiPurityGeneralizedArchTest}.
 *
 * <p>Status (W1, rc13 — 2026-05-20): SPI shipped; runtime binding deferred
 * to W3+ when agent-client SDK lands.
 */
package ascend.springai.bus.spi.ingress;
