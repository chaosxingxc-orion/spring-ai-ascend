/**
 * Skill SPI — unified Tool/Skill registration, dispatch, and
 * lifecycle boundary.
 *
 * <p>Authority: ADR-0127 (extends ADR-0030); semantic-resolution
 * decision ADR-0122 (Path b: Tool is a {@link SkillKind} enum
 * value).
 *
 * <p>One {@link Skill} interface with lifecycle
 * ({@code init} / {@code execute} / {@code suspend} / {@code teardown});
 * one registry sibling type indexed by {@code (tenantId, skillKey)};
 * one {@link SkillKind} enum discriminating TOOL / BUILTIN /
 * AGENT_AS_TOOL / MCP_SERVER / UNTRUSTED_TOOL / UNTRUSTED_CODE.
 *
 * <p>Capacity arbitration continues through the existing
 * {@code SkillCapacityRegistry} + {@code ResilienceContract}
 * (ADR-0070 / ADR-0080); no new arbitration surface is introduced.
 *
 * <p>Sandbox routing: UNTRUSTED_* kinds MUST route through
 * the sandbox executor SPI (ADR-0018, runtime enforcement deferred
 * to W3).
 *
 * <p>Reference adapter {@code SpringAiToolCallbackSkill}
 * adapts Spring AI {@code ToolCallback} to {@link Skill} with
 * {@link SkillKind#TOOL}; lifecycle methods {@code init} /
 * {@code teardown} are no-ops, {@code suspend} throws
 * {@link UnsupportedOperationException}.
 *
 * <p>SPI purity per Rule R-D.
 */
package com.huawei.ascend.middleware.skill.spi;
