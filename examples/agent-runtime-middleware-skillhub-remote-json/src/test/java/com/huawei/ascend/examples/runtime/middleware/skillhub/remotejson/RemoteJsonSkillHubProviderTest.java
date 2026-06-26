package com.huawei.ascend.examples.runtime.middleware.skillhub.remotejson;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.huawei.ascend.runtime.common.RuntimeIdentity;
import com.huawei.ascend.runtime.common.RuntimeMessage;
import com.huawei.ascend.runtime.engine.AgentExecutionContext;
import com.huawei.ascend.runtime.engine.openjiuwen.OpenJiuwenSkillHubInstaller;
import com.huawei.ascend.runtime.engine.spi.SkillDefinition;
import com.huawei.ascend.runtime.engine.spi.SkillSummary;
import com.openjiuwen.core.session.Session;
import com.openjiuwen.core.session.stream.StreamMode;
import com.openjiuwen.core.singleagent.BaseAgent;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RemoteJsonSkillHubProviderTest {
    @TempDir
    Path tempDir;

    @Test
    void jsonCatalogProviderServesSummaryDefinitionAndPackage() throws Exception {
        Path skillsRoot = tempDir.resolve("skills");
        Files.createDirectories(skillsRoot.resolve("date-helper"));
        Files.writeString(skillsRoot.resolve("date-helper").resolve("SKILL.md"), """
                # Date Helper

                Answer date questions.
                """);
        Path catalog = tempDir.resolve("catalog.json");
        Files.writeString(catalog, """
                {"skills":[{"skillId":"date-helper","name":"Date Helper","description":"Date skill","path":"date-helper"}]}
                """);
        JsonCatalogSkillHubProvider provider = new JsonCatalogSkillHubProvider(new ObjectMapper(), catalog, skillsRoot);

        List<SkillSummary> summaries = provider.listSkills(context());
        SkillDefinition definition = provider.loadSkill(context(), "date-helper");

        assertThat(summaries).singleElement().satisfies(summary -> {
            assertThat(summary.skillId()).isEqualTo("date-helper");
            assertThat(summary.description()).isEqualTo("Date skill");
        });
        assertThat(definition.instructions()).contains("Date Helper");
        assertThat(provider.loadSkillPackage(context(), "date-helper").content()).isNotEmpty();
    }

    @Test
    void httpProviderConsumesRemoteSkillHubEndpoints() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/hub/skills", exchange -> {
            if ("/hub/skills".equals(exchange.getRequestURI().getPath())) {
                writeJson(exchange, """
                        [{"skillId":"date-helper","name":"Date Helper","description":"Date skill","tags":[],"metadata":{}}]
                        """);
                return;
            }
            if ("/hub/skills/date-helper".equals(exchange.getRequestURI().getPath())) {
                writeJson(exchange, """
                        {"skillId":"date-helper","name":"Date Helper","description":"Date skill","instructions":"# Date Helper","referenceUris":[],"toolDependencies":[],"metadata":{"openjiuwen.skill.path":"skills/date-helper"}}
                        """);
                return;
            }
            if ("/hub/skills/date-helper/package".equals(exchange.getRequestURI().getPath())) {
                writeBinary(exchange, new byte[] {1, 2, 3});
                return;
            }
            exchange.sendResponseHeaders(404, -1);
        });
        server.start();
        try {
            HttpSkillHubProvider provider = new HttpSkillHubProvider(
                    java.net.http.HttpClient.newHttpClient(),
                    new ObjectMapper(),
                    "http://127.0.0.1:" + server.getAddress().getPort() + "/hub",
                    Duration.ofSeconds(5));

            assertThat(provider.listSkills(context())).extracting(SkillSummary::skillId).containsExactly("date-helper");
            assertThat(provider.loadSkill(context(), "date-helper").instructions()).contains("Date Helper");
            assertThat(provider.loadSkillPackage(context(), "date-helper").content()).containsExactly(1, 2, 3);
        } finally {
            server.stop(0);
        }
    }

    @Test
    void installerCanRegisterRemoteDefinitionPathOnOpenJiuwenAgent() {
        RecordingAgent agent = new RecordingAgent();
        com.huawei.ascend.runtime.engine.spi.SkillHubProvider provider =
                new com.huawei.ascend.runtime.engine.spi.SkillHubProvider() {
                    @Override
                    public List<SkillSummary> listSkills(AgentExecutionContext context) {
                        return List.of(new SkillSummary("date-helper", "Date Helper", "Date skill", List.of(), Map.of()));
                    }

                    @Override
                    public SkillDefinition loadSkill(AgentExecutionContext context, String skillId) {
                        return new SkillDefinition(skillId, "Date Helper", "Date skill", "# Date Helper",
                                List.of(), List.of(), Map.of("openjiuwen.skill.path", "skills/date-helper"));
                    }
                };

        new OpenJiuwenSkillHubInstaller(provider).install(agent, context());

        assertThat(agent.registeredSkills).containsExactly("skills/date-helper");
    }

    private static AgentExecutionContext context() {
        return new AgentExecutionContext(new RuntimeIdentity("tenant", "user", "session", "task", "agent"),
                "USER_MESSAGE", List.of(RuntimeMessage.user("date")), Map.of());
    }

    private static void writeJson(HttpExchange exchange, String body) throws IOException {
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        writeBinary(exchange, body.getBytes(StandardCharsets.UTF_8));
    }

    private static void writeBinary(HttpExchange exchange, byte[] body) throws IOException {
        exchange.sendResponseHeaders(200, body.length);
        try (var output = exchange.getResponseBody()) {
            output.write(body);
        }
    }

    private static final class RecordingAgent extends BaseAgent {
        private final List<Object> registeredSkills = new ArrayList<>();

        private RecordingAgent() {
            super(AgentCard.builder().id("agent").name("agent").description("test").build());
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
