/**
 * Session Manager SPI.
 *
 * <p>Contains the {@link com.huawei.ascend.service.session.spi.ContextProjector}
 * interface for projecting SessionContext from full Session history.
 *
 * <p>The Session Manager component ( 5-component
 * decomposition of agent-service) is responsible for middle/long-context
 * data management; this SPI is the projection surface that compute
 * nodes consume.
 *
 * <p>Reference impl ({@code InMemoryContextProjector}) lands
 *.
 *
 * <p>SPI purity per Rule R-D: imports only {@code java.*} + own
 * siblings.
 */
package com.huawei.ascend.service.session.spi;
