/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.ascend.examples.deepresearch;

/**
 * Constants for the deep-research root DeepAgent.
 */
public final class DeepResearchConstants {

    /** OpenJiuwen / A2A agent identifier. */
    public static final String AGENT_ID = "deep-research-agent";

    /** Default HTTP port for the root A2A wrapper. */
    public static final int DEFAULT_A2A_PORT = 13003;

    /**
     * Remote sub-agent tool names. Must match downstream AgentCard {@code name}
     * fields registered by {@code RemoteAgentCardCache}.
     */
    public static final String REMOTE_TOOL_PLAN_SEARCH = "search-agent";
    public static final String REMOTE_TOOL_PLAN_READ = "plan_read";
    public static final String REMOTE_TOOL_PLAN_VERIFY = "verify-agent";

    /** Classpath location of the production DeepAgent YAML spec. */
    public static final String PROD_YAML_RESOURCE = "agent/deepagent.prod.yaml";

    /** Classpath location of the system prompt markdown. */
    public static final String SYSTEM_PROMPT_RESOURCE = "prompts/deep-research-system-prompt.md";

    private DeepResearchConstants() {
    }
}
