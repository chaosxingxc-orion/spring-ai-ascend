package com.huawei.ascend.examples.a2a;

import com.huawei.ascend.runtime.bootstrap.AbstractRuntimeAgentHandler;
import com.huawei.ascend.runtime.common.InvocationRequest;
import com.huawei.ascend.runtime.common.RunEvent;
import com.huawei.ascend.runtime.common.RunEventType;
import com.huawei.ascend.runtime.dispatch.adapter.openjiuwen.OpenJiuwenMessageConverter;
import com.huawei.ascend.runtime.dispatch.adapter.openjiuwen.OpenJiuwenResultMapper;
import com.huawei.ascend.runtime.dispatch.handler.AgentExecutionContext;
import com.huawei.ascend.runtime.dispatch.spi.AgentResultAdapter;
import com.huawei.ascend.runtime.engine.RunCoordinator;
import com.huawei.ascend.runtime.engine.adapters.openjiuwen.OpenJiuwenAgentDriver;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class OpenJiuwenReactAgentConfiguration {

    static final String AGENT_ID = "openjiuwen-react-agent";

    @Bean
    AbstractRuntimeAgentHandler openJiuwenReactAgentHandler(
            @Value("${sample.openjiuwen.model-provider:${SAA_SAMPLE_OPENJIUWEN_MODEL_PROVIDER:openai}}")
            String modelProvider,
            @Value("${sample.openjiuwen.api-key:${SAA_SAMPLE_LLM_API_KEY:sk-x00550472}}") String apiKey,
            @Value("${sample.openjiuwen.api-base:${SAA_SAMPLE_OPENJIUWEN_API_BASE:http://localhost:4000/v1}}")
            String apiBase,
            @Value("${sample.openjiuwen.model-name:${SAA_SAMPLE_LLM_MODEL:gpt-5.4-mini}}") String modelName,
            @Value("${sample.openjiuwen.ssl-verify:${SAA_SAMPLE_OPENJIUWEN_SSL_VERIFY:false}}")
            boolean sslVerify) {
        return new SampleOpenJiuwenReactAgentHandler(modelProvider, apiKey, apiBase, modelName, sslVerify);
    }

    /**
     * W4 bridge: the legacy A2A access SPI ({@link AbstractRuntimeAgentHandler}) delegates the
     * run to the NEW neutral engine core ({@link RunCoordinator} + {@link OpenJiuwenAgentDriver}),
     * then surfaces the terminal {@link RunEvent} in the legacy result-map shape the A2A egress
     * expects. This lets the existing real-A2A e2e harness verify the rebuilt core end-to-end
     * (SDK-host A2A ping→pong) without re-implementing the transport.
     */
    static final class SampleOpenJiuwenReactAgentHandler extends AbstractRuntimeAgentHandler {
        private static final Logger LOGGER = LoggerFactory.getLogger(SampleOpenJiuwenReactAgentHandler.class);
        private static final String SYSTEM_PROMPT = """
                You are a concise assistant exposed only through the A2A protocol.
                If the user's message is exactly ping, reply exactly pong and nothing else.
                For all other messages, reply to the user's message directly and briefly.
                """;
        private final OpenJiuwenMessageConverter messageConverter = new OpenJiuwenMessageConverter();
        private final OpenJiuwenResultMapper resultMapper = new OpenJiuwenResultMapper();
        private final OpenJiuwenAgentDriver driver;

        SampleOpenJiuwenReactAgentHandler(
                String modelProvider,
                String apiKey,
                String apiBase,
                String modelName,
                boolean sslVerify) {
            super(AGENT_ID, AGENT_ID, "Sample openJiuwen ReAct agent hosted by agent-runtime.");
            this.driver = new OpenJiuwenAgentDriver(
                    AGENT_ID, SYSTEM_PROMPT, modelProvider, apiKey, apiBase, modelName, sslVerify);
        }

        @Override
        public Stream<?> execute(AgentExecutionContext context) {
            Object converted = messageConverter.toOpenJiuwenInput(context);
            String query = converted instanceof Map<?, ?> map ? String.valueOf(map.get("query")) : "";
            InvocationRequest request = new InvocationRequest(
                    context.getScope().taskId(), AGENT_ID, context.getScope().sessionId(), query);
            LOGGER.info("example openjiuwen (new engine core) start sessionId={} taskId={} provider/base/model via driver",
                    context.getScope().sessionId(), context.getScope().taskId());
            RunCoordinator coordinator = new RunCoordinator(driver);
            coordinator.start();
            try {
                List<RunEvent> events = collect(coordinator.stream(request));
                RunEvent terminal = events.isEmpty()
                        ? RunEvent.failed(0, "empty run-event stream")
                        : events.get(events.size() - 1);
                String resultType = terminal.kind() == RunEventType.FAILED ? "error" : "answer";
                String output = terminal.content() != null
                        ? terminal.content()
                        : (terminal.error() != null ? terminal.error() : "");
                LOGGER.info("example openjiuwen (new engine core) finished sessionId={} taskId={} terminalKind={}",
                        context.getScope().sessionId(), context.getScope().taskId(), terminal.kind());
                return Stream.of(Map.of("result_type", resultType, "output", output));
            } catch (RuntimeException e) {
                LOGGER.warn("example openjiuwen (new engine core) failed sessionId={} taskId={} error={}",
                        context.getScope().sessionId(), context.getScope().taskId(), errorMessage(e));
                throw new IllegalStateException(errorMessage(e), e);
            }
        }

        @Override
        public AgentResultAdapter resultAdapter() {
            return rawResults -> rawResults.map(rawResult -> {
                if (rawResult instanceof Map<?, ?> map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> typed = (Map<String, Object>) map;
                    return resultMapper.map(typed);
                }
                return resultMapper.map(Map.of("result_type", "answer", "output", String.valueOf(rawResult)));
            });
        }

        private static List<RunEvent> collect(Flow.Publisher<RunEvent> publisher) {
            List<RunEvent> out = new ArrayList<>();
            CountDownLatch done = new CountDownLatch(1);
            publisher.subscribe(new Flow.Subscriber<RunEvent>() {
                @Override
                public void onSubscribe(Flow.Subscription subscription) {
                    subscription.request(Long.MAX_VALUE);
                }

                @Override
                public void onNext(RunEvent item) {
                    out.add(item);
                }

                @Override
                public void onError(Throwable throwable) {
                    done.countDown();
                }

                @Override
                public void onComplete() {
                    done.countDown();
                }
            });
            try {
                done.await(90, TimeUnit.SECONDS);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
            }
            return out;
        }

        private static String errorMessage(Throwable error) {
            StringBuilder message = new StringBuilder();
            Throwable cursor = error;
            while (cursor != null) {
                String part = cursor.getMessage();
                if (part != null && !part.isBlank()) {
                    if (!message.isEmpty()) {
                        message.append(": ");
                    }
                    message.append(part);
                }
                cursor = cursor.getCause();
            }
            return message.isEmpty() ? error.getClass().getName() : message.toString();
        }
    }
}
