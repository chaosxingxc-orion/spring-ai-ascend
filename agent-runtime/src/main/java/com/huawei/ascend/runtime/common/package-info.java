/**
 * Neutral, framework-agnostic data types shared across the runtime's layers.
 *
 * <p>This package carries vocabulary, not flow: the request/response model the
 * caller sees ({@link com.huawei.ascend.runtime.common.AgentRequest},
 * {@link com.huawei.ascend.runtime.common.AgentResponseEvent},
 * {@link com.huawei.ascend.runtime.common.ErrorInfo} and the
 * {@code ResponseMode}/{@code ResponseType}/{@code ResponseStatus} enums), plus
 * the small shared helpers {@link com.huawei.ascend.runtime.common.Guards} and
 * {@link com.huawei.ascend.runtime.common.Timing}. Users only need to understand
 * these types — never an underlying framework's wire objects.
 *
 * <p>Dependency rule: this package may depend only on the JDK. It must not
 * depend on Spring Boot, the A2A SDK, or any agent framework, so it can stay the
 * single neutral vocabulary every other layer is free to import.
 */
package com.huawei.ascend.runtime.common;
