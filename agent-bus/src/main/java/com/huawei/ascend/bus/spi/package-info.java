/**
 * agent-bus SPI roots — Bus & State Hub plane control surfaces.
 *
 * <p>Two cross-plane control surfaces ship under this root post-rc13
 * (2026-05-20):
 * <ul>
 *   <li>{@link com.huawei.ascend.bus.spi.ingress} — client → server ingress
 *       (ADR-0089 / Rule R-I sub-clause .b).</li>
 *   <li>{@link com.huawei.ascend.bus.spi.s2c} — server → client callback
 *       (ADR-0074 / ADR-0088).</li>
 * </ul>
 *
 * <p>Intra-service workflow primitives (workflow intermediary, mailbox,
 * admission control, backpressure signalling, sleep/wakeup declaration, and
 * the tick engine — enumerated by name in ADR-0050) land in W2.
 *
 * <p>This roll-up package-info is itself the SPI declaration for
 * {@code com.huawei.ascend.bus.spi} — Rule 66 (spi_package_exhaustiveness)
 * detects this directory as a "spi" leaf and requires it in
 * {@code module-metadata.yaml#spi_packages}.
 *
 * <p>Authority: ADR-0050, ADR-0074, ADR-0088, ADR-0089; Layer-0 principles
 * P-E + P-I; CLAUDE.md Rule R-E + Rule R-I.
 */
package com.huawei.ascend.bus.spi;
