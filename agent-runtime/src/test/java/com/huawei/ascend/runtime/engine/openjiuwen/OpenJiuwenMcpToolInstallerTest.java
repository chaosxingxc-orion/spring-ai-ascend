package com.huawei.ascend.runtime.engine.openjiuwen;

import static org.assertj.core.api.Assertions.assertThat;

import com.huawei.ascend.runtime.common.RuntimeIdentity;
import com.huawei.ascend.runtime.common.RuntimeMessage;
import com.huawei.ascend.runtime.engine.AgentExecutionContext;
import com.huawei.ascend.runtime.engine.spi.McpProvider;
import com.huawei.ascend.runtime.engine.spi.McpToolResult;
import com.huawei.ascend.runtime.engine.spi.McpToolSpec;
import com.openjiuwen.core.session.Session;
import com.openjiuwen.core.session.stream.StreamMode;
import com.openjiuwen.core.singleagent.BaseAgent;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class OpenJiuwenMcpToolInstallerTest {

    @Test
    void installsMcpToolCardOnAgentInstance() {
        RecordingAgent agent = new RecordingAgent();
        FakeMcpProvider provider = new FakeMcpProvider();
        OpenJiuwenMcpToolInstaller installer = new OpenJiuwenMcpToolInstaller(provider);

        installer.install(agent, context());

        assertThat(provider.listed).isTrue();
        assertThat(agent.getAbilityManager().get("get_current_time")).isNotNull();
        assertThat(agent.getAbilityManager().listToolInfo())
                .anySatisfy(info -> assertThat(info.getName()).isEqualTo("get_current_time"));
    }

    private static AgentExecutionContext context() {
        return new AgentExecutionContext(
                new RuntimeIdentity("tenant", "user", "ctx-1", "task-1", "agent-a"),
                "USER_MESSAGE",
                List.of(RuntimeMessage.user("time?")),
                Map.of());
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
            return McpToolResult.success(
                    List.of(Map.of("type", "text", "text", "12:00:00")),
                    Map.of("time", "12:00:00"),
                    Map.of(),
                    Map.of("serverId", serverId, "toolName", name));
        }
    }

    private static final class RecordingAgent extends BaseAgent {
        private RecordingAgent() {
            super(AgentCard.builder().id("agent-a").name("agent-a").description("test").build());
        }

        @Override
        public BaseAgent configure(Object config) {
            return this;
        }

        @Override
        public Object getConfig() {
            return null;
        }

        @Override
        public Object invoke(Object input, Session session) {
            return null;
        }

        @Override
        public Iterator<Object> stream(Object input, Session session, List<StreamMode> streamModes) {
            return List.of().iterator();
        }
    }
}
