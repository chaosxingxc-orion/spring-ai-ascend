package com.huawei.ascend.runtime.engine.openjiuwen;

import static org.assertj.core.api.Assertions.assertThat;

import com.huawei.ascend.runtime.common.RuntimeIdentity;
import com.huawei.ascend.runtime.common.RuntimeMessage;
import com.huawei.ascend.runtime.engine.AgentExecutionContext;
import com.huawei.ascend.runtime.engine.spi.SkillDefinition;
import com.huawei.ascend.runtime.engine.spi.SkillHubProvider;
import com.huawei.ascend.runtime.engine.spi.SkillSummary;
import com.openjiuwen.core.session.Session;
import com.openjiuwen.core.session.stream.StreamMode;
import com.openjiuwen.core.singleagent.BaseAgent;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class OpenJiuwenSkillHubInstallerTest {

    @Test
    void installsOpenJiuwenSkillPathsFromSkillDefinitions() {
        RecordingAgent agent = new RecordingAgent();
        SkillHubProvider provider = new FakeSkillHubProvider(List.of(
                new SkillDefinition(
                        "hotel",
                        "Hotel booking",
                        "Book hotels",
                        "Use hotel booking workflow.",
                        List.of(),
                        List.of(),
                        Map.of(
                                OpenJiuwenSkillHubInstaller.METADATA_OPENJIUWEN_SKILL_PATH, "skills/hotel",
                                OpenJiuwenSkillHubInstaller.METADATA_OPENJIUWEN_SKILL_PATHS,
                                List.of("skills/common")))));
        OpenJiuwenSkillHubInstaller installer = new OpenJiuwenSkillHubInstaller(provider);

        installer.install(agent, context());

        assertThat(agent.registeredSkills).containsExactly("skills/hotel", "skills/common");
    }

    @Test
    void ignoresDefinitionsWithoutOpenJiuwenPaths() {
        RecordingAgent agent = new RecordingAgent();
        SkillHubProvider provider = new FakeSkillHubProvider(List.of(
                new SkillDefinition("general", "General", "General skill", "instructions",
                        List.of(), List.of(), Map.of())));

        new OpenJiuwenSkillHubInstaller(provider).install(agent, context());

        assertThat(agent.registeredSkills).isEmpty();
    }

    private static AgentExecutionContext context() {
        return new AgentExecutionContext(
                new RuntimeIdentity("tenant", "user", "session", "task", "agent-a"),
                "USER_MESSAGE",
                List.of(RuntimeMessage.user("book hotel")),
                Map.of(AgentExecutionContext.AGENT_STATE_KEY_VARIABLE, "conversation-1"));
    }

    private static final class FakeSkillHubProvider implements SkillHubProvider {
        private final List<SkillDefinition> definitions;

        private FakeSkillHubProvider(List<SkillDefinition> definitions) {
            this.definitions = definitions;
        }

        @Override
        public List<SkillSummary> listSkills(AgentExecutionContext context) {
            return definitions.stream()
                    .map(definition -> new SkillSummary(
                            definition.skillId(),
                            definition.name(),
                            definition.description(),
                            List.of(),
                            Map.of()))
                    .toList();
        }

        @Override
        public SkillDefinition loadSkill(AgentExecutionContext context, String skillId) {
            return definitions.stream()
                    .filter(definition -> definition.skillId().equals(skillId))
                    .findFirst()
                    .orElse(null);
        }
    }

    private static final class RecordingAgent extends BaseAgent {
        private final List<Object> registeredSkills = new ArrayList<>();

        private RecordingAgent() {
            super(AgentCard.builder().id("agent-a").name("agent-a").description("test").build());
        }

        @Override
        public void registerSkill(Object skills) {
            registeredSkills.add(skills);
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
