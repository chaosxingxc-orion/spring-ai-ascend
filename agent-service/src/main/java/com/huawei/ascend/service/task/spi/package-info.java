/**
 * Task Center SPI per ADR-0100 (rc22).
 *
 * <p>Contains the {@link com.huawei.ascend.service.task.spi.TaskStateStore}
 * interface for TaskControlState persistence.
 *
 * <p>The Task Center component (per ADR-0100 5-component decomposition
 * of agent-service) owns the control-state layer of the Run ≤ Task ≤
 * Session ≤ Memory lifecycle hierarchy.
 *
 * <p>Reference impl ({@code InMemoryTaskStateStore}) lands in rc24;
 * JDBC impl + Flyway migration with RLS per Rule R-J.a land in rc25.
 *
 * <p>SPI purity per Rule R-D: imports only {@code java.*} + own
 * siblings.
 */
package com.huawei.ascend.service.task.spi;
