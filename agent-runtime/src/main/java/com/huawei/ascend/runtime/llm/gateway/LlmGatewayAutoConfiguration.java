package com.huawei.ascend.runtime.llm.gateway;

import com.huawei.ascend.runtime.llm.gateway.otel.OtelGenerationSpanSink;
import com.huawei.ascend.runtime.llm.gateway.spi.GenerationSpanSink;
import com.huawei.ascend.runtime.llm.gateway.spi.InMemorySpendLog;
import com.huawei.ascend.runtime.llm.gateway.spi.LlmCallListener;
import com.huawei.ascend.runtime.llm.gateway.spi.NoopGenerationSpanSink;
import com.huawei.ascend.runtime.llm.gateway.spi.SpendLog;
import io.micrometer.core.instrument.MeterRegistry;
import io.opentelemetry.api.OpenTelemetry;
import java.time.Clock;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires the LLM egress gateway. Guarded by an explicit opt-in property so the
 * whole surface — endpoint, forwarder, listeners — stays off every classpath
 * consumer's context unless a deployment turns it on.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "agent-runtime.llm.gateway", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(LlmGatewayProperties.class)
public class LlmGatewayAutoConfiguration {

    @Bean
    public ModelAliasRegistry llmModelAliasRegistry(LlmGatewayProperties properties) {
        return new ModelAliasRegistry(properties);
    }

    @Bean
    public MintedTokenAuthenticator llmMintedTokenAuthenticator(LlmGatewayProperties properties) {
        return new MintedTokenAuthenticator(properties);
    }

    @Bean
    @ConditionalOnMissingBean(UpstreamModelClient.class)
    public RestClientUpstreamModelClient llmUpstreamModelClient(LlmGatewayProperties properties) {
        return new RestClientUpstreamModelClient(
                properties.getConnectTimeout(), properties.getRequestTimeout());
    }

    @Bean
    public LlmGatewayMetrics llmGatewayMetrics(ObjectProvider<MeterRegistry> meterRegistry) {
        return new LlmGatewayMetrics(meterRegistry.getIfAvailable());
    }

    @Bean
    @ConditionalOnMissingBean(GenerationSpanSink.class)
    public NoopGenerationSpanSink llmGenerationSpanSink() {
        return new NoopGenerationSpanSink();
    }

    /**
     * OTel bridge: chosen over the Noop fallback whenever the deployment supplies
     * an {@link OpenTelemetry} bean (nested configuration classes register before
     * the enclosing class's bean methods, so the Noop's missing-bean check sees
     * this sink). Separate class-presence-guarded configuration — never a bare
     * bean-method condition — because opentelemetry-api is an optional dependency
     * and bean signatures referencing absent optional types fail bean creation.
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(OpenTelemetry.class)
    static class OtelGenerationSpanSinkConfiguration {

        @Bean
        @ConditionalOnBean(OpenTelemetry.class)
        @ConditionalOnMissingBean(GenerationSpanSink.class)
        OtelGenerationSpanSink llmOtelGenerationSpanSink(OpenTelemetry openTelemetry) {
            return new OtelGenerationSpanSink(openTelemetry);
        }
    }

    @Bean
    @ConditionalOnMissingBean(SpendLog.class)
    public InMemorySpendLog llmSpendLog() {
        return new InMemorySpendLog();
    }

    @Bean
    public LlmSpanEmitterListener llmSpanEmitterListener(GenerationSpanSink sink,
            ModelAliasRegistry registry, LlmGatewayMetrics metrics) {
        return new LlmSpanEmitterListener(sink, registry, metrics);
    }

    @Bean
    public SpendRecordListener llmSpendRecordListener(SpendLog spendLog,
            ModelAliasRegistry registry) {
        // UTC clock: the spend roll-up key is a UTC calendar day, never server-local.
        return new SpendRecordListener(spendLog, registry, Clock.systemUTC());
    }

    @Bean
    public ChatCompletionsController llmChatCompletionsController(
            MintedTokenAuthenticator authenticator, ModelAliasRegistry registry,
            UpstreamModelClient upstreamModelClient, LlmGatewayMetrics metrics,
            ObjectProvider<LlmCallListener> listeners) {
        return new ChatCompletionsController(authenticator, registry, upstreamModelClient,
                metrics, listeners.orderedStream().toList());
    }
}
