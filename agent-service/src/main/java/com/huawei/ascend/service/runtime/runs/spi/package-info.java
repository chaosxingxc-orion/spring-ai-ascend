/**
 * agent-service run-lifecycle SPI — the {@link
 * com.huawei.ascend.service.runtime.runs.spi.RunRepository} persistence contract.
 *
 * <p>SPI-pure per CLAUDE.md Rule R-D sub-clause .d: imports restricted to
 * {@code java.*} + sibling domain value types in
 * {@code com.huawei.ascend.service.runtime.runs}
 * ({@link com.huawei.ascend.service.runtime.runs.Run},
 * {@link com.huawei.ascend.service.runtime.runs.RunStatus}) which form the
 * lifecycle vocabulary this SPI persists. Spring / platform / impl / metrics
 * imports are forbidden (enforced by SpiPurityGeneralizedArchTest).
 *
 * <p>Authority: ADR-0088 (agent-runtime-core dissolution — runs lifecycle
 * relocated to agent-service); CLAUDE.md Rule R-C sub-clause .c.
 */
package com.huawei.ascend.service.runtime.runs.spi;
