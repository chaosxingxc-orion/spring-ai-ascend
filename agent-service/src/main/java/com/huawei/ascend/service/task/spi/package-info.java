/**
 * Task Center SPI.
 *
 * <p>Contains the {@link com.huawei.ascend.service.task.spi.TaskStateStore}
 * interface for TaskControlState persistence.
 *
 * <p>The Task Center component ( 5-component decomposition
 * of agent-service) owns the control-state layer of the Run ≤ Task ≤
 * Session ≤ Memory lifecycle hierarchy.
 *
 * <p>Reference impl ({@code InMemoryTaskStateStore}) lands;
 * JDBC impl + Flyway migration with RLS per Rule R-J.a land.
 *
 * <p>SPI purity per Rule R-D: imports only {@code java.*} + own
 * siblings.
 */
package com.huawei.ascend.service.task.spi;
