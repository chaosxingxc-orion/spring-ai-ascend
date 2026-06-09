/**
 * Engine-side middleware services.
 *
 * <p>These services are runtime APIs, not Agent framework SPIs. The first W1
 * service is Agent State: the engine loads a framework-neutral checkpoint before
 * invoking an {@code AgentRuntimeHandler}, and saves the checkpoint after the
 * handler finishes. This keeps interruption recovery close to execution while
 * keeping business state outside the runtime.
 */
package com.huawei.ascend.runtime.engine.service;
