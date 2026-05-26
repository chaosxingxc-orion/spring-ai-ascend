package com.huawei.ascend.middleware.skill.spi;

/**
 * Closed discriminator for {@link Skill} kinds (ADR-0122)
 * (Path b: Tool is a SkillKind enum value).
 *
 * <p>New kinds require ADR amendment; open enums defeat type-safe
 * sandbox routing under Rule R-L.
 */
public enum SkillKind {

    /** LLM-callable function. Spring AI {@code ToolCallback} adapter is one. */
    TOOL,

    /** Platform-owned utility (memory read, vector query, MCP server call). */
    BUILTIN,

    /** Another {@code Agent} invoked as a skill (Agent-to-Agent (ADR-0100)). */
    AGENT_AS_TOOL,

    /** Remote MCP server tool exposure (Spring AI MCP adapter). */
    MCP_SERVER,

    /**
     * TOOL kind whose execution MUST route through the sandbox executor SPI
     * (Rule R-L). The runtime enforces sandbox routing based on this
     * discriminator.
     */
    UNTRUSTED_TOOL,

    /**
     * Untrusted code execution (e.g. code-interpreter). Sandbox is
     * mandatory; the runtime refuses to invoke this kind outside
     * a the sandbox executor SPI.
     */
    UNTRUSTED_CODE
}
