package com.huawei.ascend.examples.runtime.middleware.skillhub.remotejson;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huawei.ascend.runtime.common.RuntimeIdentity;
import com.huawei.ascend.runtime.common.RuntimeMessage;
import com.huawei.ascend.runtime.engine.AgentExecutionContext;
import com.huawei.ascend.runtime.engine.openjiuwen.OpenJiuwenAgentRuntimeHandler;
import com.huawei.ascend.runtime.engine.openjiuwen.OpenJiuwenSkillHubInstaller;
import com.huawei.ascend.runtime.engine.spi.SkillDefinition;
import com.huawei.ascend.runtime.engine.spi.SkillHubProvider;
import com.huawei.ascend.runtime.engine.spi.SkillPackage;
import com.huawei.ascend.runtime.engine.spi.SkillSummary;
import com.huawei.ascend.runtime.engine.spi.SkillToolDependency;
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
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
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
public class SkillHubRemoteJsonApplication {
    public static void main(String[] args) {
        SpringApplication.run(SkillHubRemoteJsonApplication.class, args);
    }
}

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "sample.skillhub.role", havingValue = "hub")
class RemoteJsonSkillHubServerConfiguration {
    @Bean
    JsonCatalogSkillHubProvider jsonCatalogSkillHubProvider(
            @Value("${sample.skillhub.catalog:skills/catalog.json}") String catalogPath,
            @Value("${sample.skillhub.root:skills}") String skillRoot) {
        return new JsonCatalogSkillHubProvider(new ObjectMapper(), Path.of(catalogPath), Path.of(skillRoot));
    }
}

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "sample.skillhub.role", havingValue = "runtime")
class RemoteJsonSkillHubRuntimeConfiguration {
    private static final String AGENT_ID = "middleware-skillhub-remote-json-agent";

    @Bean
    HttpSkillHubProvider skillHubProvider(
            @Value("${sample.remote-skillhub.base-url}") String baseUrl) {
        return new HttpSkillHubProvider(HttpClient.newHttpClient(), new ObjectMapper(), baseUrl, Duration.ofSeconds(10));
    }

    @Bean
    RemoteJsonSkillHubOpenJiuwenHandler sampleHandler(
            @Value("${sample.openjiuwen.model-provider:${SAA_SAMPLE_OPENJIUWEN_MODEL_PROVIDER:openai}}")
            String modelProvider,
            @Value("${sample.openjiuwen.api-key:${SAA_SAMPLE_LLM_API_KEY:}}") String apiKey,
            @Value("${sample.openjiuwen.api-base:${SAA_SAMPLE_OPENJIUWEN_API_BASE:https://api.deepseek.com}}")
            String apiBase,
            @Value("${sample.openjiuwen.model-name:${SAA_SAMPLE_LLM_MODEL:deepseek-chat}}") String modelName,
            @Value("${sample.openjiuwen.ssl-verify:${SAA_SAMPLE_OPENJIUWEN_SSL_VERIFY:false}}")
            boolean sslVerify) {
        return new RemoteJsonSkillHubOpenJiuwenHandler(
                AGENT_ID, modelProvider, apiKey, apiBase, modelName, sslVerify);
    }
}

@RestController
@ConditionalOnProperty(name = "sample.skillhub.role", havingValue = "hub")
class RemoteJsonSkillHubController {
    private final JsonCatalogSkillHubProvider provider;

    RemoteJsonSkillHubController(JsonCatalogSkillHubProvider provider) {
        this.provider = provider;
    }

    @GetMapping("/hub/skills")
    List<SkillSummary> skills() {
        return provider.listSkills(context());
    }

    @GetMapping("/hub/skills/{skillId}")
    SkillDefinition skill(@PathVariable String skillId) {
        return provider.loadSkill(context(), skillId);
    }

    @GetMapping("/hub/skills/{skillId}/package")
    ResponseEntity<byte[]> skillPackage(@PathVariable String skillId) {
        SkillPackage skillPackage = provider.loadSkillPackage(context(), skillId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(skillPackage.mediaType()))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(skillPackage.skillId() + ".zip").build().toString())
                .body(skillPackage.content());
    }

    private static AgentExecutionContext context() {
        return new AgentExecutionContext(new RuntimeIdentity("hub", "hub", "hub", "hub", "hub"),
                "SKILLHUB", List.of(), Map.of());
    }
}

@RestController
@ConditionalOnProperty(name = "sample.skillhub.role", havingValue = "runtime")
class RemoteJsonSkillHubRuntimeController {
    private final SkillHubProvider skillHubProvider;
    private final RemoteJsonSkillHubOpenJiuwenHandler handler;

    RemoteJsonSkillHubRuntimeController(SkillHubProvider skillHubProvider,
            RemoteJsonSkillHubOpenJiuwenHandler handler) {
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
                .body(skillPackage.content());
    }

    @PostMapping("/sample/skillhub/ask")
    Map<String, Object> ask(@RequestBody SkillAskRequest request) {
        AgentExecutionContext context = buildExecutionContext(request.stateKey(), request.text());
        return Map.of(
                "stateKey", context.getAgentStateKey(),
                "query", request.text(),
                "agentOutputs", handler.execute(context).toList());
    }

    private static AgentExecutionContext buildExecutionContext(String stateKey, String text) {
        RuntimeIdentity identity = new RuntimeIdentity("sample-tenant", "sample-user", "sample-session",
                "sample-task", "middleware-skillhub-remote-json-agent");
        return new AgentExecutionContext(identity, "USER_MESSAGE",
                List.of(RuntimeMessage.user(text == null ? "" : text)), Map.of(),
                stateKey == null || stateKey.isBlank() ? "demo-user" : stateKey, null);
    }

    record SkillAskRequest(String stateKey, String text) {
    }
}

final class JsonCatalogSkillHubProvider implements SkillHubProvider {
    private static final String SKILL_FILE = "SKILL.md";

    private final ObjectMapper objectMapper;
    private final Path catalogPath;
    private final Path skillRoot;

    JsonCatalogSkillHubProvider(ObjectMapper objectMapper, Path catalogPath, Path skillRoot) {
        this.objectMapper = objectMapper;
        this.catalogPath = catalogPath.toAbsolutePath().normalize();
        this.skillRoot = skillRoot.toAbsolutePath().normalize();
    }

    @Override
    public List<SkillSummary> listSkills(AgentExecutionContext context) {
        return entries().stream()
                .map(entry -> new SkillSummary(entry.skillId(), entry.name(), entry.description(),
                        entry.tags(), entry.metadata()))
                .toList();
    }

    @Override
    public SkillDefinition loadSkill(AgentExecutionContext context, String skillId) {
        CatalogEntry entry = entry(skillId);
        Path skillDir = skillRoot.resolve(entry.path()).normalize();
        if (!skillDir.startsWith(skillRoot)) {
            throw new IllegalArgumentException("skill path escapes skill root: " + skillId);
        }
        Path skillFile = skillDir.resolve(SKILL_FILE);
        try {
            String instructions = SkillMarkdown.stripYamlFrontmatter(Files.readString(skillFile, StandardCharsets.UTF_8));
            return new SkillDefinition(entry.skillId(), entry.name(), entry.description(), instructions,
                    List.of(skillFile.toString()), entry.toolDependencies(),
                    mergeMetadata(entry.metadata(), Map.of(
                            OpenJiuwenSkillHubInstaller.METADATA_OPENJIUWEN_SKILL_PATH, skillDir.toString())));
        } catch (IOException error) {
            throw new IllegalStateException("Failed to load skill " + skillId, error);
        }
    }

    @Override
    public SkillPackage loadSkillPackage(AgentExecutionContext context, String skillId) {
        CatalogEntry entry = entry(skillId);
        Path skillDir = skillRoot.resolve(entry.path()).normalize();
        if (!skillDir.startsWith(skillRoot)) {
            throw new IllegalArgumentException("skill path escapes skill root: " + skillId);
        }
        try {
            byte[] zip = zipDirectory(skillDir);
            return new SkillPackage(skillId, "application/zip", zip,
                    Map.of("sourcePath", skillDir.toString(), "sizeBytes", zip.length));
        } catch (IOException error) {
            throw new IllegalStateException("Failed to package skill " + skillId, error);
        }
    }

    private CatalogEntry entry(String skillId) {
        return entries().stream()
                .filter(entry -> entry.skillId().equals(skillId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown skill: " + skillId));
    }

    private List<CatalogEntry> entries() {
        try {
            Catalog catalog = objectMapper.readValue(catalogPath.toFile(), Catalog.class);
            return catalog.skills() == null ? List.of() : catalog.skills();
        } catch (IOException error) {
            throw new IllegalStateException("Failed to load catalog " + catalogPath, error);
        }
    }

    private static Map<String, Object> mergeMetadata(Map<String, Object> left, Map<String, Object> right) {
        java.util.LinkedHashMap<String, Object> merged = new java.util.LinkedHashMap<>();
        if (left != null) {
            merged.putAll(left);
        }
        merged.putAll(right);
        return Map.copyOf(merged);
    }

    private static byte[] zipDirectory(Path skillDir) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(output, StandardCharsets.UTF_8);
                java.util.stream.Stream<Path> paths = Files.walk(skillDir)) {
            for (Path path : paths.filter(Files::isRegularFile).toList()) {
                zip.putNextEntry(new ZipEntry(skillDir.relativize(path).toString().replace('\\', '/')));
                Files.copy(path, zip);
                zip.closeEntry();
            }
        }
        return output.toByteArray();
    }

    record Catalog(List<CatalogEntry> skills) {
    }

    record CatalogEntry(
            String skillId,
            String name,
            String description,
            String path,
            List<String> tags,
            List<SkillToolDependency> toolDependencies,
            Map<String, Object> metadata) {
        CatalogEntry {
            tags = tags == null ? List.of() : List.copyOf(tags);
            toolDependencies = toolDependencies == null ? List.of() : List.copyOf(toolDependencies);
            metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
        }
    }
}

final class HttpSkillHubProvider implements SkillHubProvider {
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String baseUrl;
    private final Duration timeout;

    HttpSkillHubProvider(HttpClient httpClient, ObjectMapper objectMapper, String baseUrl, Duration timeout) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
        this.baseUrl = trimTrailingSlash(baseUrl);
        this.timeout = timeout;
    }

    @Override
    public List<SkillSummary> listSkills(AgentExecutionContext context) {
        try {
            String body = getText("/skills");
            return objectMapper.readValue(body, new TypeReference<List<SkillSummary>>() {
            });
        } catch (IOException error) {
            throw new IllegalStateException("Failed to decode skill summaries", error);
        }
    }

    @Override
    public SkillDefinition loadSkill(AgentExecutionContext context, String skillId) {
        try {
            return objectMapper.readValue(getText("/skills/" + encode(skillId)), SkillDefinition.class);
        } catch (IOException error) {
            throw new IllegalStateException("Failed to decode skill definition " + skillId, error);
        }
    }

    @Override
    public SkillPackage loadSkillPackage(AgentExecutionContext context, String skillId) {
        byte[] content = getBytes("/skills/" + encode(skillId) + "/package");
        return new SkillPackage(skillId, "application/zip", content, Map.of("source", baseUrl));
    }

    private String getText(String path) {
        return new String(getBytes(path), StandardCharsets.UTF_8);
    }

    private byte[] getBytes(String path) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + path))
                .timeout(timeout)
                .GET()
                .build();
        try {
            HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() / 100 != 2) {
                throw new IllegalStateException("SkillHub HTTP " + response.statusCode() + " for " + path);
            }
            return response.body();
        } catch (IOException error) {
            throw new IllegalStateException("SkillHub request failed for " + path, error);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("SkillHub request interrupted for " + path, interrupted);
        }
    }

    private static String encode(String value) {
        return java.net.URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static String trimTrailingSlash(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("baseUrl must not be blank");
        }
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }
}

final class SkillMarkdown {
    private SkillMarkdown() {
    }

    static String stripYamlFrontmatter(String markdown) {
        if (markdown == null || !markdown.startsWith("---")) {
            return markdown == null ? "" : markdown;
        }
        int frontmatterEnd = markdown.indexOf("\n---", 3);
        if (frontmatterEnd < 0) {
            return markdown;
        }
        int bodyStart = frontmatterEnd + "\n---".length();
        if (bodyStart < markdown.length() && markdown.charAt(bodyStart) == '\r') {
            bodyStart++;
        }
        if (bodyStart < markdown.length() && markdown.charAt(bodyStart) == '\n') {
            bodyStart++;
        }
        return markdown.substring(bodyStart);
    }
}

final class RemoteJsonSkillHubOpenJiuwenHandler extends OpenJiuwenAgentRuntimeHandler {
    private final String agentId;
    private final String modelProvider;
    private final String apiKey;
    private final String apiBase;
    private final String modelName;
    private final boolean sslVerify;

    RemoteJsonSkillHubOpenJiuwenHandler(String agentId, String modelProvider, String apiKey,
            String apiBase, String modelName, boolean sslVerify) {
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
                .description("Remote JSON SkillHub curl example")
                .build());
        SysOperationCard sysOpCard = SysOperationCard.builder()
                .id(agentId)
                .mode(OperationMode.LOCAL)
                .workConfig(LocalWorkConfig.builder().workDir(null).build())
                .build();
        Runner.resourceMgr().addSysOperation(sysOpCard, null);
        ReActAgentConfig config = ReActAgentConfig.builder()
                .promptTemplate(List.of(Map.of("role", "system", "content",
                        "You are a remote SkillHub example assistant. Use registered skills when relevant.")))
                .maxIterations(3)
                .build()
                .configureModelClient(modelProvider, apiKey, apiBase, modelName, sslVerify);
        ModelRequestConfig modelConfig = config.getModelConfigObj();
        modelConfig.setTemperature(0.0);
        modelConfig.setMaxTokens(256);
        config.setSysOperationId(sysOpCard.getId());
        agent.configure(config);
        Object readFile = Runner.resourceMgr().getSysOpToolCards(sysOpCard.getId(), "fs", "readFile");
        if (readFile != null) {
            agent.getAbilityManager().add(readFile);
        }
        return agent;
    }
}
