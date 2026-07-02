package com.huawei.ascend.runtime.engine.alpha;

import com.openjiuwen.core.dispatch.AutonomyLevel;
import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.model_clients.DefaultModelClientFactories;
import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;
import com.openjiuwen.core.kernel.model.AgentName;
import com.openjiuwen.core.kernel.model.Budget;
import com.openjiuwen.core.kernel.model.ToolName;
import com.openjiuwen.core.meta.AgentDefinition;
import com.openjiuwen.runtime.core.engine.DefaultAgentKernel;
import com.openjiuwen.runtime.core.engine.DefaultAgentKernel.ToolExecutor;
import com.openjiuwen.runtime.core.engine.DefaultSafetyBoundary;
import com.openjiuwen.runtime.core.engine.SafetyBoundary;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring DI wiring for the PEV Alpha execution path.
 *
 * <p>Creates the full AgentKernel stack (LLMProvider via agent-core-java Model,
 * ToolExecutor registry, CheckpointStore, SafetyBoundary) and wires it into
 * an {@link AlphaRuntimeHandler} in PEV mode — replacing the echo fallback
 * with the real Plan-Execute-Verify pipeline.
 *
 * <h3>Usage</h3>
 * Add {@code @Import(PEVAlphaConfiguration.class)} to your Spring Boot application,
 * or let component scanning discover this {@code @Configuration}. The handler bean
 * name is {@code "pevAlphaHandler"}.
 *
 * <h3>Configuration</h3>
 * All properties are sourced from the {@code pev.alpha} prefix with
 * {@code OPENJIUWEN_*} environment variable fallbacks (consistent with
 * the existing Fusion e2e harness). Required:
 * <ul>
 *   <li>{@code pev.alpha.api-key} or {@code OPENJIUWEN_API_KEY}</li>
 *   <li>{@code pev.alpha.api-base} or {@code OPENJIUWEN_BASE_URL}</li>
 *   <li>{@code pev.alpha.model} or {@code OPENJIUWEN_MODEL}</li>
 * </ul>
 *
 * <p>诚实边界：
 * 本配置创建的 handler 是 PEV 模式（kernel != null → doExecutePEV）。
 * ToolExecutor map 是共享 ConcurrentHashMap，@Tool 由 AgentBeanPostProcessor
 * 动态注册到该 map（与 OpenjiuwenAutoConfiguration 同模式）。
 */
@Configuration(proxyBeanMethods = false)
public class PEVAlphaConfiguration {

    static final String HANDLER_AGENT_ID = "pev-alpha";

    // ── Ensure default model client factories are registered (one-time, static) ──
    static {
        DefaultModelClientFactories.ensureRegistered();
    }

    // ── Safety boundary (permissive default) ──

    @Bean
    SafetyBoundary pevSafetyBoundary() {
        return new DefaultSafetyBoundary();
    }

    // ── Checkpoint store (in-memory) ──

    @Bean
    DefaultAgentKernel.CheckpointStore pevCheckpointStore() {
        return new InMemoryCheckpointStore();
    }

    // ── Tool executor registry (shared ConcurrentHashMap for dynamic @Tool registration) ──

    @Bean
    Map<ToolName, ToolExecutor> pevToolExecutors() {
        return new ConcurrentHashMap<>();
    }

    @Bean
    Map<ToolName, AgentDefinition.ToolDefinition> pevToolDefinitions() {
        return new ConcurrentHashMap<>();
    }

    // ── LLM Provider (agent-core-java Model bridge) ──

    @Bean
    DefaultAgentKernel.LLMProvider pevLlmProvider(
            @Value("${pev.alpha.model-provider:${SAA_PEV_MODEL_PROVIDER:OpenAI}}")
            String modelProvider,
            @Value("${pev.alpha.api-key:${OPENJIUWEN_API_KEY:#{null}}}") String apiKey,
            @Value("${pev.alpha.api-base:${OPENJIUWEN_BASE_URL:#{null}}}") String apiBase,
            @Value("${pev.alpha.model:${OPENJIUWEN_MODEL:#{null}}}") String modelName,
            @Value("${pev.alpha.ssl-verify:${SAA_PEV_SSL_VERIFY:true}}") boolean sslVerify) {

        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException(
                    "PEV Alpha LLM 配置缺失：请设置 pev.alpha.api-key 或 OPENJIUWEN_API_KEY 环境变量");
        }
        if (apiBase == null || apiBase.isBlank()) {
            throw new IllegalStateException(
                    "PEV Alpha LLM 配置缺失：请设置 pev.alpha.api-base 或 OPENJIUWEN_BASE_URL 环境变量");
        }
        if (modelName == null || modelName.isBlank()) {
            throw new IllegalStateException(
                    "PEV Alpha LLM 配置缺失：请设置 pev.alpha.model 或 OPENJIUWEN_MODEL 环境变量");
        }

        ModelRequestConfig requestConfig = ModelRequestConfig.builder()
                .modelName(modelName)
                .temperature(0.7)
                .maxTokens(4096)
                .build();

        ModelClientConfig clientConfig = ModelClientConfig.builder()
                .clientId("pev-alpha-model")
                .clientProvider(modelProvider)
                .apiKey(apiKey)
                .apiBase(apiBase)
                .verifySsl(sslVerify)
                .build();

        Model model = new Model(clientConfig, requestConfig);
        return new AgentCoreJavaLlmProvider(model);
    }

    // ── Agent Kernel ──

    @Bean
    DefaultAgentKernel pevAgentKernel(
            DefaultAgentKernel.LLMProvider llmProvider,
            Map<ToolName, ToolExecutor> toolExecutors,
            Map<ToolName, AgentDefinition.ToolDefinition> toolDefinitions,
            DefaultAgentKernel.CheckpointStore checkpointStore,
            SafetyBoundary safetyBoundary) {
        return new DefaultAgentKernel(llmProvider, toolExecutors, toolDefinitions,
                checkpointStore, safetyBoundary);
    }

    // ── Agent Definition ──

    @Bean
    AgentDefinition pevAgentDefinition() {
        return new AgentDefinition(
                new AgentName(HANDLER_AGENT_ID),
                "PEV Alpha Agent — Plan-Execute-Verify explicit control engine",
                "你是一个任务规划与执行专家。分析用户目标，将其分解为子任务图并逐步执行验证。",
                List.of(),    // tools — populated by AgentBeanPostProcessor or empty
                AutonomyLevel.GUIDED,
                Budget.Fixed.productionDefault(),
                com.openjiuwen.core.alpha.model.ExecutionPolicy.productionDefault(),
                null,         // version
                null,         // metadata
                Map.of());
    }

    // ── Handler (PEV mode — the bean that the runtime discovers) ──

    @Bean
    AlphaRuntimeHandler pevAlphaHandler(
            DefaultAgentKernel kernel,
            AgentDefinition agentDef) {
        return new AlphaRuntimeHandler(HANDLER_AGENT_ID, kernel, agentDef);
    }
}
