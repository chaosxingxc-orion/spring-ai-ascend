/**
 * Engine provider SPI surface.
 *
 * <p>This package is intentionally small: {@code AgentHandler} is the only
 * interface that external agent providers implement. Engine inbound calls live
 * in {@link com.huawei.ascend.service.engine.api}; engine internal command
 * runtime lives in {@link com.huawei.ascend.service.engine.command}; engine outbound
 * clients to access/task-control live in {@code com.huawei.ascend.service.engine.port}.
 */
package com.huawei.ascend.service.engine.spi;
