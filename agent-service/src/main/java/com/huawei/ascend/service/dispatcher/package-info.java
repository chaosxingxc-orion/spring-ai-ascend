/**
 * Polymorphic Dispatcher component.
 *
 * <p>Unified entry point for BOTH local function-call and remote
 * bus-call invocations. Component #1 of the agent-service 5-component
 * runtime-role decomposition.
 *
 * <p>Implementation lands (agent-service Java refactor moves
 * the in-process dispatcher logic here from
 * {@code com.huawei.ascend.service.runtime.orchestration.inmemory}).
 *
 * <p>Cross-package boundary (rc23 ArchUnit
 * {@code AgentServiceComponentBoundaryArchTest}):
 * dispatcher → may call orchestrator, task, session.
 * Reverse direction forbidden.
 */
package com.huawei.ascend.service.dispatcher;
