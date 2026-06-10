/**
 * agent-bus server-to-client (S2C) callback SPI — owned by the Bus & State
 * Hub plane (agent-runtime-core dissolution; relocated here
 * from {@code com.huawei.ascend.runtime.s2c.spi}).
 *
 * <p><strong>DESIGN-FROZEN.</strong> This SPI surface is a design artefact
 * only: it MUST NOT be implemented or consumed by production code until a
 * re-opening ADR lifts the freeze; see
 * {@code docs/logs/plans/2026-06-11-agent-bus-spi-decision.md} for the
 * decision record. An architecture test in agent-runtime enforces that no
 * production class depends on this package.
 *
 * <p>Authority: ADR-0074 (S2C Capability Callback Protocol); ADR-0088
 * (ownership move to agent-bus). Contract schema lives at
 * {@code docs/contracts/s2c-callback.v1.yaml}.
 *
 * <p>S2C (server → client) callback is designed to flow through this package —
 * the bus plane's outbound cross-plane control surface.
 *
 * <p>SPI-pure per Rule R-D sub-clause .d: imports restricted to
 * {@code java.*} + same-spi-package siblings.
 */
package com.huawei.ascend.bus.spi.s2c;
