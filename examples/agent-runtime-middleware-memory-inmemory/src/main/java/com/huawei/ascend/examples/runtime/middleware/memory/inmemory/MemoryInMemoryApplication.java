package com.huawei.ascend.examples.runtime.middleware.memory.inmemory;

import com.huawei.ascend.runtime.common.RuntimeIdentity;
import com.huawei.ascend.runtime.common.RuntimeMessage;
import com.huawei.ascend.runtime.engine.AgentExecutionContext;
import com.huawei.ascend.runtime.engine.openjiuwen.OpenJiuwenAgentRuntimeHandler;
import com.huawei.ascend.runtime.engine.spi.MemoryProvider;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;
import com.openjiuwen.core.singleagent.BaseAgent;
import com.openjiuwen.core.singleagent.agents.ReActAgent;
import com.openjiuwen.core.singleagent.agents.ReActAgentConfig;
import com.openjiuwen.core.singleagent.rail.AgentRail;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@SpringBootApplication
public class MemoryInMemoryApplication {
    public static void main(String[] args) {
        SpringApplication.run(MemoryInMemoryApplication.class, args);
    }
}

@Configuration(proxyBeanMethods = false)
class MemoryInMemoryConfiguration {
    private static final String AGENT_ID = "middleware-memory-inmemory-agent";

    @Bean
    InMemoryMemoryProvider memoryProvider() {
        return new InMemoryMemoryProvider();
    }

    @Bean
    SampleMemoryOpenJiuwenHandler sampleHandler(
            @Value("${sample.openjiuwen.model-provider:${SAA_SAMPLE_OPENJIUWEN_MODEL_PROVIDER:openai}}")
            String modelProvider,
            @Value("${sample.openjiuwen.api-key:${SAA_SAMPLE_LLM_API_KEY:}}") String apiKey,
            @Value("${sample.openjiuwen.api-base:${SAA_SAMPLE_OPENJIUWEN_API_BASE:https://api.deepseek.com}}")
            String apiBase,
            @Value("${sample.openjiuwen.model-name:${SAA_SAMPLE_LLM_MODEL:deepseek-chat}}") String modelName,
            @Value("${sample.openjiuwen.ssl-verify:${SAA_SAMPLE_OPENJIUWEN_SSL_VERIFY:false}}")
            boolean sslVerify,
            InMemoryMemoryProvider memoryProvider) {
        return new SampleMemoryOpenJiuwenHandler(
                AGENT_ID, modelProvider, apiKey, apiBase, modelName, sslVerify, memoryProvider);
    }
}

@RestController
class MemoryInMemoryController {
    private final InMemoryMemoryProvider memoryProvider;
    private final SampleMemoryOpenJiuwenHandler handler;

    MemoryInMemoryController(InMemoryMemoryProvider memoryProvider, SampleMemoryOpenJiuwenHandler handler) {
        this.memoryProvider = memoryProvider;
        this.handler = handler;
    }

    @PostMapping("/sample/memory/remember")
    Map<String, Object> remember(@RequestBody MemoryRequest request) {
        AgentExecutionContext executionContext = buildExecutionContext(request.stateKey(), request.text());
        memoryProvider.init(executionContext);
        memoryProvider.save(executionContext, List.of(new MemoryProvider.MemoryRecord(
                null, "assistant", request.text(), Map.of("source", "curl"))));
        return Map.of(
                "stateKey", executionContext.getAgentStateKey(),
                "records", memoryProvider.records(executionContext));
    }

    @PostMapping("/sample/memory/ask")
    Map<String, Object> ask(@RequestBody MemoryRequest request) {
        AgentExecutionContext executionContext = buildExecutionContext(request.stateKey(), request.text());
        List<?> agentOutputs = handler.execute(executionContext).toList();
        return Map.of(
                "stateKey", executionContext.getAgentStateKey(),
                "query", request.text(),
                "agentOutputs", agentOutputs,
                "memoryHits", memoryProvider.search(executionContext, request.text(), 5),
                "records", memoryProvider.records(executionContext));
    }

    @GetMapping("/sample/memory/records")
    Map<String, Object> records(@RequestParam(defaultValue = "demo-user") String stateKey) {
        AgentExecutionContext executionContext = buildExecutionContext(stateKey, "");
        return Map.of("stateKey", stateKey, "records", memoryProvider.records(executionContext));
    }

    private static AgentExecutionContext buildExecutionContext(String stateKey, String text) {
        RuntimeIdentity identity =
                new RuntimeIdentity("sample-tenant", "sample-user", "sample-session", "sample-task",
                        "middleware-memory-inmemory-agent");
        return new AgentExecutionContext(identity, "USER_MESSAGE",
                List.of(RuntimeMessage.user(text == null ? "" : text)), Map.of(), normalizeStateKey(stateKey), null);
    }

    private static String normalizeStateKey(String stateKey) {
        return stateKey == null || stateKey.isBlank() ? "demo-user" : stateKey;
    }

    record MemoryRequest(String stateKey, String text) {
    }
}

final class SampleMemoryOpenJiuwenHandler extends OpenJiuwenAgentRuntimeHandler {
    private final String agentId;
    private final String modelProvider;
    private final String apiKey;
    private final String apiBase;
    private final String modelName;
    private final boolean sslVerify;
    private final MemoryProvider memoryProvider;

    SampleMemoryOpenJiuwenHandler(
            String agentId,
            String modelProvider,
            String apiKey,
            String apiBase,
            String modelName,
            boolean sslVerify,
            MemoryProvider memoryProvider) {
        super(agentId);
        this.agentId = agentId;
        this.modelProvider = modelProvider;
        this.apiKey = apiKey;
        this.apiBase = apiBase;
        this.modelName = modelName;
        this.sslVerify = sslVerify;
        this.memoryProvider = memoryProvider;
    }

    @Override
    protected List<AgentRail> openJiuwenRails(AgentExecutionContext context) {
        // OpenJiuwenAgentRuntimeHandler#doExecute calls installRails(...),
        // which registers each returned rail on the concrete OpenJiuwen agent.
        return List.of(createMemoryRail(context));
    }

    private AgentRail createMemoryRail(AgentExecutionContext context) {
        return memoryRuntimeRail(context, memoryProvider);
    }

    @Override
    protected BaseAgent createOpenJiuwenAgent(AgentExecutionContext context) {
        ReActAgent agent = new ReActAgent(AgentCard.builder()
                .id(agentId)
                .name(agentId)
                .description("InMemory MemoryProvider curl example")
                .build());
        ReActAgentConfig config = ReActAgentConfig.builder()
                .promptTemplate(List.of(Map.of("role", "system", "content",
                        """
                        You are a middleware memory example assistant.
                        Answer the user's question using relevant memory when it is provided.
                        If the user asks for a preference, answer with the remembered preference directly.
                        """)))
                .maxIterations(3)
                .build()
                .configureModelClient(modelProvider, apiKey, apiBase, modelName, sslVerify);
        ModelRequestConfig modelConfig = config.getModelConfigObj();
        modelConfig.setTemperature(0.0);
        modelConfig.setMaxTokens(128);
        agent.configure(config);
        return agent;
    }
}
