/**
 * Engine-side middleware services.
 *
 * <p>These services are runtime APIs, not Agent framework SPIs. Agent State
 * lets the engine load a framework-neutral state map before invoking an
 * {@code AgentRuntimeHandler}, and save it after the handler finishes. The key
 * is supplied by business input so framework adapters do not hard-code tenant,
 * session, or task layout into storage.
 */
package com.huawei.ascend.runtime.engine.service;
