/**
 * agent-bus SPI roots — Bus & State Hub plane control surfaces.
 *
 * <p>Two cross-plane control surfaces ship under this root post-rc13
 * (2026-05-20):
 * <ul>
 *   <li>{@link ascend.springai.bus.spi.ingress} — client → server ingress
 *       (ADR-0089 / Rule R-I sub-clause .b).</li>
 *   <li>{@link ascend.springai.bus.spi.s2c} — server → client callback
 *       (ADR-0074 / ADR-0088).</li>
 * </ul>
 *
 * <p>Intra-service workflow primitives (WorkflowIntermediary, Mailbox,
 * AdmissionDecision, BackpressureSignal, SleepDeclaration, WakeupPulse,
 * TickEngine) land in W2 per ADR-0050 directly under this root package.
 *
 * <p>This roll-up package-info is itself the SPI declaration for
 * {@code ascend.springai.bus.spi} — Rule 66 (spi_package_exhaustiveness)
 * detects this directory as a "spi" leaf and requires it in
 * {@code module-metadata.yaml#spi_packages}.
 *
 * <p>Authority: ADR-0050, ADR-0074, ADR-0088, ADR-0089; Layer-0 principles
 * P-E + P-I; CLAUDE.md Rule R-E + Rule R-I.
 */
package ascend.springai.bus.spi;
