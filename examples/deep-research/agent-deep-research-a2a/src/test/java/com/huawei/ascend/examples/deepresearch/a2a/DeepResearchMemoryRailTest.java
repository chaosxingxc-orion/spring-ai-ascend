/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.ascend.examples.deepresearch.a2a;

import static org.assertj.core.api.Assertions.assertThat;

import com.huawei.ascend.examples.deepresearch.DeepResearchAgentSpecMaterializer;
import com.huawei.ascend.runtime.common.RuntimeIdentity;
import com.huawei.ascend.runtime.common.RuntimeMessage;
import com.huawei.ascend.runtime.engine.AgentExecutionContext;
import com.huawei.ascend.runtime.engine.spi.MemoryProvider;
import com.openjiuwen.core.session.AgentSessionApi;
import com.openjiuwen.core.session.stream.StreamMode;
import com.openjiuwen.core.singleagent.rail.AgentCallbackContext;
import com.openjiuwen.core.singleagent.rail.AgentCallbackEvent;
import com.openjiuwen.core.singleagent.rail.AgentRail;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import com.openjiuwen.harness.deep_agent.DeepAgent;
import com.openjiuwen.harness.rails.ExternalMemoryRail;
import com.openjiuwen.harness.schema.config.DeepAgentConfig;
import com.openjiuwen.harness.workspace.Workspace;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DeepResearchMemoryRailTest {

    @Test
    void handlerInstallsExternalMemoryRailBeforeStreaming() {
        FakeMemoryProvider memoryProvider = new FakeMemoryProvider();
        RecordingDeepResearchHandler handler = new RecordingDeepResearchHandler(memoryProvider);

        handler.execute(context()).toList();

        assertThat(handler.installedBeforeStream).isTrue();
        assertThat(handler.installedRail).isInstanceOf(ExternalMemoryRail.class);
        assertThat(handler.agent.getRegisteredRails()).contains(handler.installedRail);
        handler.agent.getAgent().fireCallbackEvent(AgentCallbackEvent.BEFORE_INVOKE, AgentCallbackContext.builder()
                .agent(handler.agent.getAgent())
                .build());
        assertThat(memoryProvider.initialized).isTrue();
    }

    private static AgentExecutionContext context() {
        return new AgentExecutionContext(
                new RuntimeIdentity("tenant", "user", "session", "task", "deep-research-agent"),
                "USER_MESSAGE",
                List.of(RuntimeMessage.user("上次对比里上下文窗口最大的是哪家？")),
                Map.of(AgentExecutionContext.AGENT_STATE_KEY_VARIABLE, "conversation-1"));
    }

    private static final class RecordingDeepResearchHandler extends DeepResearchConfiguration.DeepResearchHandler {

        private final MemoryProvider provider;
        private RecordingDeepAgent agent;
        private AgentRail installedRail;
        private boolean installedBeforeStream;

        private RecordingDeepResearchHandler(MemoryProvider memoryProvider) {
            super(DeepResearchAgentSpecMaterializer.materializeProdYaml(), memoryProvider);
            this.provider = memoryProvider;
        }

        @Override
        protected DeepAgent createOpenJiuwenDeepAgent(AgentExecutionContext context) {
            agent = new RecordingDeepAgent();
            return agent;
        }

        @Override
        protected List<AgentRail> openJiuwenRails(AgentExecutionContext context) {
            if (provider == null) {
                return List.of();
            }
            installedRail = openJiuwenExternalMemoryRail(context, provider);
            return List.of(installedRail);
        }

        @Override
        protected Iterator<Object> runOpenJiuwenDeepAgentStreaming(
                DeepAgent agent,
                Map<String, Object> input,
                String conversationId,
                List<StreamMode> streamModes) {
            installedBeforeStream = agent.getRegisteredRails().contains(installedRail);
            return super.runOpenJiuwenDeepAgentStreaming(agent, input, conversationId, streamModes);
        }
    }

    private static final class RecordingDeepAgent extends DeepAgent {

        private RecordingDeepAgent() {
            super(
                    AgentCard.builder()
                            .id("deep-research-agent")
                            .name("deep-research-agent")
                            .description("test")
                            .build(),
                    DeepAgentConfig.builder().enableTaskLoop(true).build(),
                    Workspace.builder().rootPath("./target/deep-research-memory-rail-test").build());
        }

        @Override
        public Iterator<Object> stream(
                Map<String, Object> inputs,
                AgentSessionApi session,
                List<StreamMode> streamModes) {
            return List.of((Object) Map.of("output", "done")).iterator();
        }
    }

    private static final class FakeMemoryProvider implements MemoryProvider {
        private boolean initialized;

        @Override
        public void init(AgentExecutionContext context) {
            initialized = true;
        }

        @Override
        public List<MemoryHit> search(AgentExecutionContext context, String query, int limit) {
            return List.of(new MemoryHit("m1", "DeepSeek 128K", 0.9, Map.of()));
        }
    }
}
