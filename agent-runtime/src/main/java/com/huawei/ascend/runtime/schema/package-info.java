/**
 * Canonical, framework-neutral schema shared by all agent-service modules.
 *
 * <p>This package is the single source of truth for the data structures that
 * cross module boundaries — {@link com.huawei.ascend.service.schema.Message},
 * {@link com.huawei.ascend.service.schema.Content},
 * {@link com.huawei.ascend.service.schema.AgentRequest},
 * {@link com.huawei.ascend.service.schema.AgentResponse},
 * {@link com.huawei.ascend.service.schema.RunStatus} and
 * {@link com.huawei.ascend.service.schema.Role}.
 *
 * <p>The shapes are aligned with the agentscope-runtime schema vocabulary so
 * integrators familiar with that runtime find the same concepts here, while
 * staying plain immutable Java records with no Spring, Jackson or framework
 * dependency. Protocol adapters (access layer) and framework adapters (engine)
 * convert at the edges; everything in between speaks this vocabulary.
 */
package com.huawei.ascend.service.schema;
