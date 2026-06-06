/**
 * Engine provider SPI surface.
 *
 * <p>This package is intentionally small: {@code AgentRuntimeHandler} is the only
 * interface that external agent providers implement. Engine inbound calls live
 * in {@link com.huawei.ascend.runtime.engine.api}; engine internal command
 * runtime lives in {@link com.huawei.ascend.runtime.engine.command}; engine outbound
 * clients to access/task-control live in {@code com.huawei.ascend.runtime.engine.port}.
 */
package com.huawei.ascend.runtime.engine.spi;
