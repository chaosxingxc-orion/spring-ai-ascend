/**
 * The runtime's entry point and host — how a developer makes the five layers
 * "run".
 *
 * <p>{@link com.huawei.ascend.runtime.app.RuntimeApp} assembles the layers around
 * a handler and starts them on a {@link com.huawei.ascend.runtime.app.RuntimeHost};
 * {@link com.huawei.ascend.runtime.app.LocalA2aRuntimeHost} is the first host,
 * exposing the runtime over A2A.
 *
 * <p>{@code RuntimeApp} and {@code RuntimeHost} are deliberately plain Java with
 * no Spring Boot dependency; a concrete host (such as the local A2A host) may
 * choose Spring Boot or any HTTP stack, but that dependency stays confined to the
 * host implementation and never leaks into common/session/queue/control/engine
 * core. This entry point only wires and runs the layers — it does not construct
 * agents from configuration; the example or business application builds the
 * handler and hands it to the runtime.
 */
package com.huawei.ascend.runtime.app;
