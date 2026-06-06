/**
 * agent-runtime engine SPI — the single framework-neutral seam each framework adapter
 * implements. {@link com.huawei.ascend.runtime.engine.spi.AgentDriver} (with the convenience base
 * {@link com.huawei.ascend.runtime.engine.spi.AbstractAgentDriver}) runs one agent and surfaces
 * its native stream; the matching {@link com.huawei.ascend.runtime.engine.spi.OutputConverter}
 * turns that into the neutral {@link com.huawei.ascend.runtime.common.RunEvent} stream.
 *
 * <p>Framework-native tool / memory / skill / MCP / middleware stay inside the adapter; the
 * runtime is never customised for any one framework.
 *
 * <p>SPI-pure per CLAUDE.md Rule 32: imports restricted to {@code java.*}, own spi siblings, and
 * the neutral {@code common} model. Spring / platform / framework imports are forbidden.
 *
 * <p>Authority: ADR-0160 (neutral execution core).
 */
package com.huawei.ascend.runtime.engine.spi;
