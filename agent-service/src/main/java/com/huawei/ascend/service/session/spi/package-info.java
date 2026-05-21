/**
 * Session Manager SPI per ADR-0100 (rc22).
 *
 * <p>Contains the {@link com.huawei.ascend.service.session.spi.ContextProjector}
 * interface for projecting SessionContext from full Session history.
 *
 * <p>The Session Manager component (per ADR-0100 5-component
 * decomposition of agent-service) is responsible for middle/long-context
 * data management; this SPI is the projection surface that compute
 * nodes consume.
 *
 * <p>Reference impl ({@code InMemoryContextProjector}) lands in rc24
 * per ADR-0100 implementation timeline.
 *
 * <p>SPI purity per Rule R-D: imports only {@code java.*} + own
 * siblings.
 */
package com.huawei.ascend.service.session.spi;
