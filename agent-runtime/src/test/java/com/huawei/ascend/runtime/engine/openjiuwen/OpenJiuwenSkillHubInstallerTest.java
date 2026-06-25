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
import com.openjiuwen.core.singleagent.agents.ReActAgent;
import com.openjiuwen.core.singleagent.agents.ReActAgentConfig;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import com.openjiuwen.harness.deep_agent.DeepAgent;
import com.openjiuwen.harness.schema.config.DeepAgentConfig;
import com.openjiuwen.harness.workspace.Workspace;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

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
    void skipsDeepAgentSkillInstallWhenInnerSkillRuntimeIsMissing(@TempDir Path tempDir) throws IOException {
        DeepAgent agent = deepAgent("deep-skillhub-installer-test");
        Path skillDir = tempDir.resolve("hotel");
        Files.createDirectories(skillDir);
        Files.writeString(skillDir.resolve("SKILL.md"), "---\ndescription: Hotel booking memory\n---\n# Hotel");
        SkillHubProvider provider = new FakeSkillHubProvider(List.of(
                new SkillDefinition(
                        "hotel",
                        "Hotel booking",
                        "Book hotels",
                        "Use hotel booking workflow.",
                        List.of(),
                        List.of(),
                        Map.of(OpenJiuwenSkillHubInstaller.METADATA_OPENJIUWEN_SKILL_PATH,
                                skillDir.toString()))));

        new OpenJiuwenSkillHubInstaller(provider).install(agent, context());

        assertThat(agent.getAgent().getSkillUtil()).isNull();
    }

    @Test
    void installsOpenJiuwenSkillPathsOnConfiguredDeepAgentInnerAgent(@TempDir Path tempDir) throws IOException {
        DeepAgent agent = deepAgent("deep-skillhub-installer-configured-test");
        configureInnerSkillRuntime(agent);
        Path skillDir = tempDir.resolve("hotel");
        Files.createDirectories(skillDir);
        Files.writeString(skillDir.resolve("SKILL.md"), "---\ndescription: Hotel booking memory\n---\n# Hotel");
        SkillHubProvider provider = new FakeSkillHubProvider(List.of(
                new SkillDefinition(
                        "hotel",
                        "Hotel booking",
                        "Book hotels",
                        "Use hotel booking workflow.",
                        List.of(),
                        List.of(),
                        Map.of(OpenJiuwenSkillHubInstaller.METADATA_OPENJIUWEN_SKILL_PATH,
                                skillDir.toString()))));

        new OpenJiuwenSkillHubInstaller(provider).install(agent, context());

        assertThat(agent.getAgent().getSkillUtil()).isNotNull();
        assertThat(agent.getAgent().getSkillUtil().hasSkill()).isTrue();
        assertThat(agent.getAgent().getSkillUtil().getSkillPrompt()).contains("Hotel booking memory");
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

    @Test
    void injectsSkillInstructionsIntoReactAgentPrompt() {
        ReActAgent agent = reactAgent();
        SkillHubProvider provider = new FakeSkillHubProvider(List.of(
                new SkillDefinition("date-helper", "Date Helper", "Date skill",
                        "RUNTIME_SKILL_PROMPT_MARKER: answer date questions briefly.",
                        List.of(), List.of(), Map.of())));

        new OpenJiuwenSkillHubInstaller(provider).install(agent, context());

        assertThat(agent.getPromptBuilder().build())
                .contains("Runtime SkillHub has loaded the following skills")
                .contains("Skill ID: date-helper")
                .contains("RUNTIME_SKILL_PROMPT_MARKER");
    }

    @Test
    void registersFrontmatterSkillWithOpenJiuwenSkillManager(@TempDir Path tempDir) throws IOException {
        Path skillDir = tempDir.resolve("date-helper");
        Files.createDirectories(skillDir);
        Files.writeString(skillDir.resolve("SKILL.md"),
                """
                ---
                description: Date helper skill.
                ---

                # Date Helper
                RUNTIME_SKILL_FILE_MARKER
                """,
                StandardCharsets.UTF_8);
        ReActAgent agent = reactAgent();
        SkillHubProvider provider = new FakeSkillHubProvider(List.of(
                new SkillDefinition("date-helper", "Date Helper", "Date skill",
                        "RUNTIME_SKILL_FILE_MARKER", List.of(), List.of(),
                        Map.of(OpenJiuwenSkillHubInstaller.METADATA_OPENJIUWEN_SKILL_PATH, skillDir.toString()))));

        new OpenJiuwenSkillHubInstaller(provider).install(agent, context());

        assertThat(agent.getSkillUtil().getSkillManager().count()).isEqualTo(1);
        assertThat(agent.getPromptBuilder().build()).contains("RUNTIME_SKILL_FILE_MARKER");
    }

    private static AgentExecutionContext context() {
        return new AgentExecutionContext(
                new RuntimeIdentity("tenant", "user", "session", "task", "agent-a"),
                "USER_MESSAGE",
                List.of(RuntimeMessage.user("book hotel")),
                Map.of(AgentExecutionContext.AGENT_STATE_KEY_VARIABLE, "conversation-1"));
    }

    private static DeepAgent deepAgent(String workspacePath) {
        return new DeepAgent(
                AgentCard.builder().id("deep-agent").name("deep-agent").description("test").build(),
                DeepAgentConfig.builder().enableTaskLoop(true).build(),
                Workspace.builder().rootPath("./target/" + workspacePath).build());
    }

    private static void configureInnerSkillRuntime(DeepAgent agent) {
        agent.getAgent().configure(ReActAgentConfig.builder()
                .sysOperationId(agent.getCard().getId())
                .build());
        assertThat(agent.getAgent().getSkillUtil()).isNotNull();
    }

    private static ReActAgent reactAgent() {
        ReActAgent agent = new ReActAgent(
                AgentCard.builder().id("agent-a").name("agent-a").description("test").build());
        ReActAgentConfig config = ReActAgentConfig.builder()
                .promptTemplate(List.of(Map.of("role", "system", "content", "You are a test agent.")))
                .build();
        config.setSysOperationId("agent-a");
        agent.configure(config);
        return agent;
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
