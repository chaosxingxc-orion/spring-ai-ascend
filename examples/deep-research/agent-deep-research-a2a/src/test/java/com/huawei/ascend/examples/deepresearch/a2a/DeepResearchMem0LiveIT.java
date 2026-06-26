/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.ascend.examples.deepresearch.a2a;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.huawei.ascend.examples.deepresearch.DeepResearchConstants;
import com.huawei.ascend.runtime.common.RuntimeIdentity;
import com.huawei.ascend.runtime.common.RuntimeMessage;
import com.huawei.ascend.runtime.engine.AgentExecutionContext;
import com.huawei.ascend.runtime.engine.spi.MemoryProvider;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Live mem0 round-trip against the configured OSS endpoint.
 *
 * <p>Enable with {@code DEEP_RESEARCH_MEM0_E2E_ENABLED=true} when the mem0 service is reachable.
 */
class DeepResearchMem0LiveIT {

    @Test
    void saveAndSearchRecallsPriorComparisonConclusion() {
        assumeMem0E2eEnabled();
        assumeTrue(DeepResearchMem0Support.isReachable(mem0BaseUrl()),
                "Mem0 endpoint not reachable: " + mem0BaseUrl());

        DeepResearchMem0MemoryProvider provider = new DeepResearchMem0MemoryProvider(mem0BaseUrl(), mem0ApiKey(), false);
        String userId = "phase3-" + UUID.randomUUID();
        AgentExecutionContext context = context(userId);

        provider.init(context);
        provider.save(context, List.of(new MemoryProvider.MemoryRecord(
                "seed-" + UUID.randomUUID(),
                "assistant",
                "对比结论：上下文窗口最大的是 DeepSeek 128K",
                Map.of())));

        List<MemoryProvider.MemoryHit> hits = provider.search(
                context, "上次对比里上下文窗口最大的是哪家", 5);

        assertThat(hits)
                .isNotEmpty()
                .anySatisfy(hit -> assertThat(hit.content()).containsIgnoringCase("DeepSeek"));
    }

    private static AgentExecutionContext context(String userId) {
        return new AgentExecutionContext(
                new RuntimeIdentity(
                        System.getenv().getOrDefault("DEEP_RESEARCH_TENANT_ID", "deep-research-tenant"),
                        userId,
                        "session-mem0",
                        "task-mem0",
                        DeepResearchConstants.AGENT_ID),
                "USER_MESSAGE",
                List.of(RuntimeMessage.user("上次对比里上下文窗口最大的是哪家？")),
                Map.of(AgentExecutionContext.AGENT_STATE_KEY_VARIABLE, "conversation-mem0"));
    }

    private static void assumeMem0E2eEnabled() {
        assumeTrue(
                "true".equalsIgnoreCase(System.getenv("DEEP_RESEARCH_MEM0_E2E_ENABLED")),
                "Set DEEP_RESEARCH_MEM0_E2E_ENABLED=true to run live mem0 integration");
    }

    private static String mem0BaseUrl() {
        return System.getenv().getOrDefault("DEEP_RESEARCH_MEM0_BASE_URL", "http://7.209.189.82:8000");
    }

    private static String mem0ApiKey() {
        String value = System.getenv("DEEP_RESEARCH_MEM0_API_KEY");
        return value == null ? "" : value;
    }
}
