/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.ascend.examples.deepresearch.a2a;

import static org.assertj.core.api.Assertions.assertThat;

import com.huawei.ascend.runtime.common.RuntimeIdentity;
import com.huawei.ascend.runtime.common.RuntimeMessage;
import com.huawei.ascend.runtime.engine.AgentExecutionContext;
import com.huawei.ascend.runtime.engine.spi.MemoryProvider;
import com.huawei.ascend.runtime.engine.spi.MemoryProvider.MemoryRecord;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DeepResearchInMemoryMemoryProviderTest {

    @Test
    void recallsPriorComparisonConclusionAcrossSessions() {
        DeepResearchInMemoryMemoryProvider memoryProvider = new DeepResearchInMemoryMemoryProvider();
        AgentExecutionContext firstTurn = context("session-1", "截至 2026 Q2，对比五家 LLM API");
        memoryProvider.init(firstTurn);
        memoryProvider.save(firstTurn, List.of(new MemoryRecord(
                "memory-1",
                "assistant",
                "对比结论：上下文窗口最大的是 DeepSeek 128K，火山方舟 128K 次之",
                Map.of())));

        AgentExecutionContext recallTurn = context("session-2", "上次对比里上下文窗口最大的是哪家？");

        assertThat(memoryProvider.search(recallTurn, "上次对比里上下文窗口最大的是哪家？", 5))
                .extracting(MemoryProvider.MemoryHit::content)
                .anyMatch(content -> content.contains("DeepSeek 128K"));
    }

    private static AgentExecutionContext context(String sessionId, String userText) {
        return new AgentExecutionContext(
                new RuntimeIdentity("deep-research-tenant", "manual-user", sessionId, "task", "deep-research-agent"),
                "USER_MESSAGE",
                List.of(RuntimeMessage.user(userText)),
                Map.of(AgentExecutionContext.AGENT_STATE_KEY_VARIABLE, sessionId));
    }
}
