/**
 * agent-bus SPI roots — Bus & State Hub plane control surfaces.
 *
 * <p>Control surfaces under this root:
 * <ul>
 *   <li>{@link com.huawei.ascend.bus.spi.engine} — the engine dispatch boundary.</li>
 *   <li>{@link com.huawei.ascend.bus.spi.s2c} — server → client callback
 *       (ADR-0074 / ADR-0088).</li>
 * </ul>
 *
 * <p>This roll-up package-info is itself the SPI declaration for
 * {@code com.huawei.ascend.bus.spi}.
 *
 * <p>Authority: ADR-0074, ADR-0088; Layer-0 principle P-E; CLAUDE.md Rule R-E.
 */
package com.huawei.ascend.bus.spi;
