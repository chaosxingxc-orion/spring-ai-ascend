/**
 * agent-bus server-to-client (S2C) callback SPI — owned by the Bus & State
 * Hub plane (agent-runtime-core dissolution; relocated here
 * from {@code com.huawei.ascend.service.runtime.s2c.spi}).
 *
 * <p>Authority: ADR-0074 (S2C Capability Callback Protocol); ADR-0088
 * (ownership move to agent-bus). Contract schema lives at
 * {@code docs/contracts/s2c-callback.v1.yaml}.
 *
 * <p>Symmetry note: the bus plane owns BOTH directions of cross-plane
 * traffic. C2S (client → server) flows through {@code bus.spi.ingress};
 * S2C (server → client) flows through this package. This makes
 * {@code agent-bus} the single cross-plane control surface in both
 * directions per Rule R-I sub-clause .b.
 *
 * <p>SPI-pure per Rule R-D sub-clause .d: imports restricted to
 * {@code java.*} + same-spi-package siblings.
 */
package com.huawei.ascend.bus.spi.s2c;
