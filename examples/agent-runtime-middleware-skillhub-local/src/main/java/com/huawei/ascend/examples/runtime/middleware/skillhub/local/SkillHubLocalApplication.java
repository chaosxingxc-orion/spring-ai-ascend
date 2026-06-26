package com.huawei.ascend.examples.runtime.middleware.skillhub.local;

import com.huawei.ascend.runtime.common.RuntimeIdentity;
import com.huawei.ascend.runtime.common.RuntimeMessage;
import com.huawei.ascend.runtime.engine.AgentExecutionContext;
import com.huawei.ascend.runtime.engine.openjiuwen.OpenJiuwenAgentRuntimeHandler;
import com.huawei.ascend.runtime.engine.openjiuwen.OpenJiuwenSkillHubInstaller;
import com.huawei.ascend.runtime.engine.spi.SkillDefinition;
import com.huawei.ascend.runtime.engine.spi.SkillHubProvider;
import com.huawei.ascend.runtime.engine.spi.SkillPackage;
import com.huawei.ascend.runtime.engine.spi.SkillSummary;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;
import com.openjiuwen.core.runner.Runner;
import com.openjiuwen.core.singleagent.BaseAgent;
import com.openjiuwen.core.singleagent.agents.ReActAgent;
import com.openjiuwen.core.singleagent.agents.ReActAgentConfig;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import com.openjiuwen.core.sysop.OperationMode;
import com.openjiuwen.core.sysop.SysOperationCard;
import com.openjiuwen.core.sysop.config.LocalWorkConfig;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@SpringBootApplication
public class SkillHubLocalApplication {
    public static void main(String[] args) {
        SpringApplication.run(SkillHubLocalApplication.class, args);
    }
}

@Configuration(proxyBeanMethods = false)
class SkillHubLocalConfiguration {
    private static final String AGENT_ID = "middleware-skillhub-local-agent";

    @Bean
    LocalDirectorySkillHubProvider skillHubProvider(
            @Value("${sample.skillhub.root:skills}") String skillRoot) {
        return new LocalDirectorySkillHubProvider(Path.of(skillRoot));
    }

    @Bean
    SampleSkillHubOpenJiuwenHandler sampleHandler(
            @Value("${sample.openjiuwen.model-provider:${SAA_SAMPLE_OPENJIUWEN_MODEL_PROVIDER:openai}}")
            String modelProvider,
            @Value("${sample.openjiuwen.api-key:${SAA_SAMPLE_LLM_API_KEY:}}") String apiKey,
            @Value("${sample.openjiuwen.api-base:${SAA_SAMPLE_OPENJIUWEN_API_BASE:https://api.deepseek.com}}")
            String apiBase,
            @Value("${sample.openjiuwen.model-name:${SAA_SAMPLE_LLM_MODEL:deepseek-chat}}") String modelName,
            @Value("${sample.openjiuwen.ssl-verify:${SAA_SAMPLE_OPENJIUWEN_SSL_VERIFY:false}}")
            boolean sslVerify) {
        return new SampleSkillHubOpenJiuwenHandler(
                AGENT_ID, modelProvider, apiKey, apiBase, modelName, sslVerify);
    }
}

@RestController
class SkillHubLocalController {
    private final SkillHubProvider skillHubProvider;
    private final SampleSkillHubOpenJiuwenHandler handler;

    SkillHubLocalController(SkillHubProvider skillHubProvider, SampleSkillHubOpenJiuwenHandler handler) {
        this.skillHubProvider = skillHubProvider;
        this.handler = handler;
    }

    @GetMapping("/sample/skillhub/skills")
    List<SkillSummary> skills() {
        return skillHubProvider.listSkills(buildExecutionContext("demo-user", ""));
    }

    @GetMapping("/sample/skillhub/skills/{skillId}")
    SkillDefinition skill(@PathVariable String skillId) {
        return skillHubProvider.loadSkill(buildExecutionContext("demo-user", ""), skillId);
    }

    @GetMapping("/sample/skillhub/skills/{skillId}/package")
    ResponseEntity<byte[]> skillPackage(@PathVariable String skillId) {
        SkillPackage skillPackage = skillHubProvider.loadSkillPackage(buildExecutionContext("demo-user", ""), skillId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(skillPackage.mediaType()))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment()
                                .filename(skillPackage.skillId() + ".zip")
                                .build()
                                .toString())
                .body(skillPackage.content());
    }

    @PostMapping("/sample/skillhub/ask")
    Map<String, Object> ask(@RequestBody SkillAskRequest request) {
        AgentExecutionContext context = buildExecutionContext(request.stateKey(), request.text());
        List<?> rawOutputs = handler.execute(context).toList();
        return Map.of(
                "stateKey", context.getAgentStateKey(),
                "query", request.text(),
                "agentOutputs", rawOutputs);
    }

    private static AgentExecutionContext buildExecutionContext(String stateKey, String text) {
        RuntimeIdentity identity =
                new RuntimeIdentity("sample-tenant", "sample-user", "sample-session", "sample-task",
                        "middleware-skillhub-local-agent");
        return new AgentExecutionContext(identity, "USER_MESSAGE",
                List.of(RuntimeMessage.user(text == null ? "" : text)), Map.of(), normalizeStateKey(stateKey), null);
    }

    private static String normalizeStateKey(String stateKey) {
        return stateKey == null || stateKey.isBlank() ? "demo-user" : stateKey;
    }

    record SkillAskRequest(String stateKey, String text) {
    }
}

final class LocalDirectorySkillHubProvider implements SkillHubProvider {
    private static final String SKILL_FILE = "SKILL.md";

    private final Path root;

    LocalDirectorySkillHubProvider(Path root) {
        this.root = root.toAbsolutePath().normalize();
    }

    @Override
    public List<SkillSummary> listSkills(AgentExecutionContext context) {
        if (!Files.isDirectory(root)) {
            return List.of();
        }
        try (java.util.stream.Stream<Path> paths = Files.list(root)) {
            return paths.filter(Files::isDirectory)
                    .filter(path -> Files.isRegularFile(path.resolve(SKILL_FILE)))
                    .map(this::toSummary)
                    .toList();
        } catch (IOException error) {
            throw new IllegalStateException("Failed to list local skills from " + root, error);
        }
    }

    @Override
    public SkillDefinition loadSkill(AgentExecutionContext context, String skillId) {
        Path skillDir = skillDir(skillId);
        Path skillFile = skillDir.resolve(SKILL_FILE);
        if (!Files.isRegularFile(skillFile)) {
            throw new IllegalArgumentException("Unknown skill: " + skillId);
        }
        try {
            String instructions = Files.readString(skillFile, StandardCharsets.UTF_8);
            return new SkillDefinition(
                    skillId,
                    title(instructions, skillId),
                    description(instructions),
                    instructions,
                    List.of(skillFile.toString()),
                    List.of(),
                    Map.of(OpenJiuwenSkillHubInstaller.METADATA_OPENJIUWEN_SKILL_PATH, skillDir.toString()));
        } catch (IOException error) {
            throw new IllegalStateException("Failed to load skill: " + skillId, error);
        }
    }

    @Override
    public SkillPackage loadSkillPackage(AgentExecutionContext context, String skillId) {
        Path skillDir = skillDir(skillId);
        if (!Files.isRegularFile(skillDir.resolve(SKILL_FILE))) {
            throw new IllegalArgumentException("Unknown skill: " + skillId);
        }
        try {
            byte[] zip = zipDirectory(skillDir);
            return new SkillPackage(
                    skillId,
                    "application/zip",
                    zip,
                    Map.of(
                            "sourcePath", skillDir.toString(),
                            "sizeBytes", zip.length));
        } catch (IOException error) {
            throw new IllegalStateException("Failed to package skill: " + skillId, error);
        }
    }

    private SkillSummary toSummary(Path skillDir) {
        String skillId = skillDir.getFileName().toString();
        try {
            String instructions = Files.readString(skillDir.resolve(SKILL_FILE), StandardCharsets.UTF_8);
            return new SkillSummary(
                    skillId,
                    title(instructions, skillId),
                    description(instructions),
                    List.of("local"),
                    Map.of(OpenJiuwenSkillHubInstaller.METADATA_OPENJIUWEN_SKILL_PATH, skillDir.toString()));
        } catch (IOException error) {
            throw new IllegalStateException("Failed to read skill: " + skillId, error);
        }
    }

    private Path skillDir(String skillId) {
        if (skillId == null || skillId.isBlank()) {
            throw new IllegalArgumentException("skillId must not be blank");
        }
        Path skillDir = root.resolve(skillId).normalize();
        if (!skillDir.startsWith(root)) {
            throw new IllegalArgumentException("skillId escapes skill root: " + skillId);
        }
        return skillDir;
    }

    private static byte[] zipDirectory(Path skillDir) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(output, StandardCharsets.UTF_8);
                java.util.stream.Stream<Path> paths = Files.walk(skillDir)) {
            for (Path path : paths.filter(Files::isRegularFile).toList()) {
                String entryName = skillDir.relativize(path).toString().replace('\\', '/');
                zip.putNextEntry(new ZipEntry(entryName));
                Files.copy(path, zip);
                zip.closeEntry();
            }
        }
        return output.toByteArray();
    }

    private static String title(String markdown, String fallback) {
        return markdown.lines()
                .map(String::trim)
                .filter(line -> line.startsWith("# "))
                .map(line -> line.substring(2).trim())
                .filter(line -> !line.isBlank())
                .findFirst()
                .orElse(fallback);
    }

    private static String description(String markdown) {
        return markdown.lines()
                .map(String::trim)
                .filter(line -> !line.isBlank())
                .filter(line -> !line.startsWith("#"))
                .findFirst()
                .orElse("");
    }
}

final class SampleSkillHubOpenJiuwenHandler extends OpenJiuwenAgentRuntimeHandler {
    private final String agentId;
    private final String modelProvider;
    private final String apiKey;
    private final String apiBase;
    private final String modelName;
    private final boolean sslVerify;

    SampleSkillHubOpenJiuwenHandler(
            String agentId,
            String modelProvider,
            String apiKey,
            String apiBase,
            String modelName,
            boolean sslVerify) {
        super(agentId);
        this.agentId = agentId;
        this.modelProvider = modelProvider;
        this.apiKey = apiKey;
        this.apiBase = apiBase;
        this.modelName = modelName;
        this.sslVerify = sslVerify;
    }

    @Override
    protected BaseAgent createOpenJiuwenAgent(AgentExecutionContext context) {
        ReActAgent agent = new ReActAgent(AgentCard.builder()
                .id(agentId)
                .name(agentId)
                .description("Local SkillHub curl example")
                .build());

        SysOperationCard sysOpCard = SysOperationCard.builder()
                .id(agentId)
                .mode(OperationMode.LOCAL)
                .workConfig(LocalWorkConfig.builder().workDir(null).build())
                .build();
        Runner.resourceMgr().addSysOperation(sysOpCard, null);

        ReActAgentConfig config = ReActAgentConfig.builder()
                .promptTemplate(List.of(Map.of("role", "system", "content",
                        """
                        You are a SkillHub example assistant.
                        Use registered skills when the user's request matches a skill.
                        Keep the final answer short.
                        """)))
                .maxIterations(3)
                .build()
                .configureModelClient(modelProvider, apiKey, apiBase, modelName, sslVerify);
        ModelRequestConfig modelConfig = config.getModelConfigObj();
        modelConfig.setTemperature(0.0);
        modelConfig.setMaxTokens(256);
        config.setSysOperationId(sysOpCard.getId());
        agent.configure(config);
        addSysOpTool(agent, sysOpCard.getId(), "fs", "readFile");
        return agent;
    }

    private static void addSysOpTool(ReActAgent agent, String sysOpId, String operationName, String toolName) {
        Object toolCard = Runner.resourceMgr().getSysOpToolCards(sysOpId, operationName, toolName);
        if (toolCard != null) {
            agent.getAbilityManager().add(toolCard);
        }
    }
}
