/**
 * Runtime session management — the continuity of an external caller's
 * conversation, addressed by {@code sessionId}.
 *
 * <p>A runtime session is NOT an agent session: it carries the external request
 * context (tenant/user/agent ids, conversation id, history window, and opaque
 * refs such as checkpoint/agent-state references) used by access and control to
 * find prior context across calls. It deliberately does not advance task state,
 * execute agents, or store an agent framework's native session/checkpoint
 * objects — only references to them. Keeping the framework's heavyweight state
 * out of here is what lets the persistence backend stay simple and swappable.
 *
 * <p>Persistence is expressed as the {@link
 * com.huawei.ascend.runtime.session.RuntimeSessionRepository} interface; the
 * first implementation is in-memory. A future Redis/JDBC implementation is named
 * directly (e.g. {@code RedisRuntimeSessionRepository}) — there is no
 * {@code store} sub-package until multiple backends actually need isolating.
 *
 * <p>Dependency rule: may depend on {@code common} and (internally) {@code queue};
 * must not depend on Spring Boot, the engine, or any agent framework.
 */
package com.huawei.ascend.runtime.session;
