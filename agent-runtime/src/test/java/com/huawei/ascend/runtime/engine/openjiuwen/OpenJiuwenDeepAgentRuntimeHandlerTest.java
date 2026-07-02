package com.huawei.ascend.runtime.engine.openjiuwen;

import static org.assertj.core.api.Assertions.assertThat;

import com.huawei.ascend.runtime.common.RuntimeIdentity;
import com.huawei.ascend.runtime.common.RuntimeMessage;
import com.huawei.ascend.runtime.engine.AgentExecutionContext;
import com.huawei.ascend.runtime.engine.spi.MemoryProvider;
import com.huawei.ascend.runtime.engine.spi.McpProvider;
import com.huawei.ascend.runtime.engine.spi.McpToolResult;
import com.huawei.ascend.runtime.engine.spi.McpToolSpec;
import com.huawei.ascend.runtime.engine.spi.SkillDefinition;
import com.huawei.ascend.runtime.engine.spi.SkillHubProvider;
import com.huawei.ascend.runtime.engine.spi.SkillSummary;
import com.openjiuwen.core.session.AgentSessionApi;
import com.openjiuwen.core.session.stream.StreamMode;
import com.openjiuwen.core.singleagent.agents.ReActAgentConfig;
import com.openjiuwen.core.singleagent.rail.AgentCallbackContext;
import com.openjiuwen.core.singleagent.rail.AgentCallbackEvent;
import com.openjiuwen.core.singleagent.rail.AgentRail;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import com.openjiuwen.harness.deep_agent.DeepAgent;
import com.openjiuwen.harness.rails.DeepAgentRail;
import com.openjiuwen.harness.rails.ExternalMemoryRail;
import com.openjiuwen.harness.rails.TaskIterationRail;
import com.openjiuwen.harness.schema.config.DeepAgentConfig;
import com.openjiuwen.harness.task_loop.TaskIterationContext;
import com.openjiuwen.harness.workspace.Workspace;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class OpenJiuwenDeepAgentRuntimeHandlerTest {

    @Test
    void executeInstallsOpenJiuwenRailsBeforeStreamingDeepAgent() {
        CountingRail rail = new CountingRail();
        RailDeepAgentHandler handler = new RailDeepAgentHandler(rail);

        handler.execute(context()).toList();

        assertThat(handler.installedBeforeStream).isTrue();
        handler.agent.getAgent().fireCallbackEvent(AgentCallbackEvent.BEFORE_INVOKE, AgentCallbackContext.builder()
                .agent(handler.agent.getAgent())
                .build());
        assertThat(rail.beforeInvokeCount).isOne();
    }

    @Test
    void executeInstallsDeepAgentRailsBeforeStreamingDeepAgent() {
        CountingDeepAgentRail rail = new CountingDeepAgentRail();
        DeepRailHandler handler = new DeepRailHandler(rail);

        handler.execute(context()).toList();

        assertThat(handler.installedBeforeStream).isTrue();
        assertThat(rail.initAgent).isSameAs(handler.agent);
        assertThat(handler.agent.getRegisteredRails()).contains(rail);
    }

    @Test
    void openJiuwenDeepAgentRailsDispatchesTaskIterationRailsFromDeepAgent() {
        CountingTaskIterationRail rail = new CountingTaskIterationRail();
        DeepRailHandler handler = new DeepRailHandler(rail);

        handler.execute(context()).toList();
        handler.agent.fireAfterTaskIteration(TaskIterationContext.builder().round(2).build());

        assertThat(rail.initAgent).isSameAs(handler.agent);
        assertThat(rail.afterTaskIterationCount).isOne();
        assertThat(rail.lastRound).isEqualTo(2);
    }

    @Test
    void openJiuwenRailsDoesNotRegisterPureDeepAgentRailOnInnerCallbackBus() {
        CountingDeepAgentRail rail = new CountingDeepAgentRail();
        InnerDeepRailHandler handler = new InnerDeepRailHandler(rail);

        handler.execute(context()).toList();
        handler.agent.getAgent().fireCallbackEvent(AgentCallbackEvent.BEFORE_INVOKE, AgentCallbackContext.builder()
                .agent(handler.agent.getAgent())
                .build());

        assertThat(rail.initAgent).isSameAs(handler.agent);
        assertThat(handler.agent.getRegisteredRails()).contains(rail);
        assertThat(rail.beforeInvokeCount).isZero();
    }

    @Test
    void openJiuwenExternalMemoryRailCanBeInstalledOnDeepAgent() {
        FakeMemoryProvider memoryProvider = new FakeMemoryProvider();
        ExternalMemoryDeepAgentHandler handler = new ExternalMemoryDeepAgentHandler(memoryProvider);

        handler.execute(context()).toList();

        assertThat(handler.installedBeforeStream).isTrue();
        assertThat(handler.installedRail).isInstanceOf(ExternalMemoryRail.class);
        assertThat(handler.agent.getRegisteredRails()).contains(handler.installedRail);
        handler.agent.getAgent().fireCallbackEvent(AgentCallbackEvent.BEFORE_INVOKE, AgentCallbackContext.builder()
                .agent(handler.agent.getAgent())
                .build());
        assertThat(memoryProvider.initialized).isTrue();
    }

    @Test
    void executeInstallsConfiguredMcpToolInstaller() {
        TestDeepAgentHandler handler = new TestDeepAgentHandler();
        FakeMcpProvider provider = new FakeMcpProvider();
        handler.setMcpToolInstaller(new OpenJiuwenMcpToolInstaller(provider));

        handler.execute(context()).toList();

        assertThat(provider.listed).isTrue();
        assertThat(handler.agent.getRegisteredTools()).hasSize(1);
        assertThat(handler.agent.getAgent().getAbilityManager().get("get_current_time")).isNotNull();
    }

    @Test
    void executeInstallsConfiguredSkillHubInstaller(@TempDir Path tempDir) throws IOException {
        TestDeepAgentHandler handler = new SkillRuntimeDeepAgentHandler();
        Path skillDir = tempDir.resolve("hotel");
        Files.createDirectories(skillDir);
        Files.writeString(skillDir.resolve("SKILL.md"),
                "---\nname: hotel\ndescription: Hotel booking memory\n---\n# Hotel");
        handler.setSkillHubInstaller(new OpenJiuwenSkillHubInstaller(new FakeSkillHubProvider(skillDir)));

        handler.execute(context()).toList();

        assertThat(handler.agent.getAgent().getSkillUtil().hasSkill()).isTrue();
        assertThat(handler.agent.getAgent().getSkillUtil().getSkillPrompt()).contains("Hotel booking memory");
    }

    @Test
    void memoryRuntimeRailCanBeInstalledOnDeepAgentInnerReActAgent() {
        FakeMemoryProvider memoryProvider = new FakeMemoryProvider();
        MemoryDeepAgentHandler handler = new MemoryDeepAgentHandler(memoryProvider);

        handler.execute(context()).toList();
        handler.agent.getAgent().fireCallbackEvent(AgentCallbackEvent.BEFORE_INVOKE, AgentCallbackContext.builder()
                .agent(handler.agent.getAgent())
                .build());

        assertThat(memoryProvider.initialized).isTrue();
        assertThat(memoryProvider.searchedQuery).isEqualTo("ping");
        assertThat(handler.agent.getAgent().getPromptBuilder().build()).contains("remembered ping");
    }

    private static AgentExecutionContext context() {
        return new AgentExecutionContext(
                new RuntimeIdentity("tenant", "user", "session", "task", "deep-agent"),
                "USER_MESSAGE",
                List.of(RuntimeMessage.user("ping")),
                Map.of(AgentExecutionContext.AGENT_STATE_KEY_VARIABLE, "conversation-1"));
    }

    private static class TestDeepAgentHandler extends OpenJiuwenDeepAgentRuntimeHandler {
        protected RecordingDeepAgent agent;

        private TestDeepAgentHandler() {
            super("deep-agent");
        }

        @Override
        protected DeepAgent createOpenJiuwenDeepAgent(AgentExecutionContext context) {
            agent = new RecordingDeepAgent();
            return agent;
        }
    }

    private static final class RailDeepAgentHandler extends TestDeepAgentHandler {
        private final CountingRail rail;
        private boolean installedBeforeStream;

        private RailDeepAgentHandler(CountingRail rail) {
            this.rail = rail;
        }

        @Override
        protected List<AgentRail> openJiuwenRails(AgentExecutionContext context) {
            return List.of(rail);
        }

        @Override
        protected Iterator<Object> runOpenJiuwenDeepAgentStreaming(
                DeepAgent agent,
                Map<String, Object> input,
                String conversationId,
                List<StreamMode> streamModes) {
            installedBeforeStream = agent.getAgent().getAgentCallbackManager().hasHooks(AgentCallbackEvent.BEFORE_INVOKE);
            return super.runOpenJiuwenDeepAgentStreaming(agent, input, conversationId, streamModes);
        }
    }

    private static final class MemoryDeepAgentHandler extends TestDeepAgentHandler {
        private final MemoryProvider memoryProvider;

        private MemoryDeepAgentHandler(MemoryProvider memoryProvider) {
            this.memoryProvider = memoryProvider;
        }

        @Override
        protected List<AgentRail> openJiuwenRails(AgentExecutionContext context) {
            return List.of(memoryRuntimeRail(context, memoryProvider));
        }
    }

    private static final class DeepRailHandler extends TestDeepAgentHandler {
        private final DeepAgentRail rail;
        private boolean installedBeforeStream;

        private DeepRailHandler(DeepAgentRail rail) {
            this.rail = rail;
        }

        @Override
        protected List<DeepAgentRail> openJiuwenDeepAgentRails(AgentExecutionContext context) {
            return List.of(rail);
        }

        @Override
        protected Iterator<Object> runOpenJiuwenDeepAgentStreaming(
                DeepAgent agent,
                Map<String, Object> input,
                String conversationId,
                List<StreamMode> streamModes) {
            installedBeforeStream = agent.getRegisteredRails().contains(rail);
            return super.runOpenJiuwenDeepAgentStreaming(agent, input, conversationId, streamModes);
        }
    }

    private static final class InnerDeepRailHandler extends TestDeepAgentHandler {
        private final CountingDeepAgentRail rail;

        private InnerDeepRailHandler(CountingDeepAgentRail rail) {
            this.rail = rail;
        }

        @Override
        protected List<AgentRail> openJiuwenRails(AgentExecutionContext context) {
            return List.of(rail);
        }
    }

    private static final class ExternalMemoryDeepAgentHandler extends TestDeepAgentHandler {
        private final MemoryProvider memoryProvider;
        private AgentRail installedRail;
        private boolean installedBeforeStream;

        private ExternalMemoryDeepAgentHandler(MemoryProvider memoryProvider) {
            this.memoryProvider = memoryProvider;
        }

        @Override
        protected List<AgentRail> openJiuwenRails(AgentExecutionContext context) {
            installedRail = openJiuwenExternalMemoryRail(context, memoryProvider);
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

    private static final class SkillRuntimeDeepAgentHandler extends TestDeepAgentHandler {
        @Override
        protected DeepAgent createOpenJiuwenDeepAgent(AgentExecutionContext context) {
            DeepAgent created = super.createOpenJiuwenDeepAgent(context);
            configureInnerSkillRuntime(created);
            return created;
        }
    }

    private static final class RecordingDeepAgent extends DeepAgent {
        private RecordingDeepAgent() {
            super(
                    AgentCard.builder().id("deep-agent").name("deep-agent").description("test").build(),
                    DeepAgentConfig.builder().enableTaskLoop(true).build(),
                    Workspace.builder().rootPath("./target/deep-agent-handler-test").build());
        }

        @Override
        public Iterator<Object> stream(
                Map<String, Object> inputs,
                AgentSessionApi session,
                List<StreamMode> streamModes) {
            return List.of((Object) Map.of("output", "done")).iterator();
        }
    }

    private static final class CountingRail extends AgentRail {
        private int beforeInvokeCount;

        @Override
        public void beforeInvoke(AgentCallbackContext ctx) {
            beforeInvokeCount++;
        }
    }

    private static final class CountingDeepAgentRail extends DeepAgentRail {
        private Object initAgent;
        private int beforeInvokeCount;

        @Override
        public void init(Object agent) {
            initAgent = agent;
        }

        @Override
        public void beforeInvoke(AgentCallbackContext ctx) {
            beforeInvokeCount++;
        }
    }

    private static final class CountingTaskIterationRail extends DeepAgentRail implements TaskIterationRail {
        private Object initAgent;
        private int afterTaskIterationCount;
        private int lastRound;

        @Override
        public void init(Object agent) {
            initAgent = agent;
        }

        @Override
        public void afterTaskIteration(TaskIterationContext ctx) {
            afterTaskIterationCount++;
            lastRound = ctx.getRound();
        }
    }

    private static final class FakeMcpProvider implements McpProvider {
        private boolean listed;

        @Override
        public List<McpToolSpec> listTools(AgentExecutionContext context) {
            listed = true;
            return List.of(new McpToolSpec(
                    "local-time",
                    "get_current_time",
                    "Current time",
                    "Return current time",
                    Map.of("type", "object", "properties", Map.of()),
                    Map.of(),
                    Map.of(),
                    Map.of(),
                    Map.of()));
        }

        @Override
        public McpToolResult callTool(AgentExecutionContext context, String serverId, String name,
                Map<String, Object> arguments) {
            return McpToolResult.success(List.of(), Map.of(), Map.of(), Map.of());
        }
    }

    private static final class FakeSkillHubProvider implements SkillHubProvider {
        private final Path skillDir;

        private FakeSkillHubProvider(Path skillDir) {
            this.skillDir = skillDir;
        }

        @Override
        public List<SkillSummary> listSkills(AgentExecutionContext context) {
            return List.of(new SkillSummary("hotel", "Hotel", "Hotel skill", List.of(), Map.of()));
        }

        @Override
        public SkillDefinition loadSkill(AgentExecutionContext context, String skillId) {
            return new SkillDefinition(
                    "hotel",
                    "Hotel",
                    "Hotel skill",
                    "Hotel booking memory",
                    List.of(),
                    List.of(),
                    Map.of(OpenJiuwenSkillHubInstaller.METADATA_OPENJIUWEN_SKILL_PATH, skillDir.toString()));
        }
    }

    private static final class FakeMemoryProvider implements MemoryProvider {
        private boolean initialized;
        private String searchedQuery;

        @Override
        public void init(AgentExecutionContext context) {
            initialized = true;
        }

        @Override
        public List<MemoryHit> search(AgentExecutionContext context, String query, int limit) {
            searchedQuery = query;
            return List.of(new MemoryHit("m1", "remembered " + query, 0.9, Map.of()));
        }
    }

    private static void configureInnerSkillRuntime(DeepAgent agent) {
        agent.getAgent().configure(ReActAgentConfig.builder()
                .sysOperationId(agent.getCard().getId())
                .build());
        assertThat(agent.getAgent().getSkillUtil()).isNotNull();
    }
}
