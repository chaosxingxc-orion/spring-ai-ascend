package com.huawei.ascend.examples.a2a;

import com.huawei.ascend.runtime.common.Message;
import com.huawei.ascend.runtime.common.Role;
import com.huawei.ascend.runtime.engine.AgentExecutionContext;
import com.huawei.ascend.runtime.engine.agentscope.AgentScopeAgent;
import com.huawei.ascend.runtime.engine.agentscope.AgentScopeAgentRuntimeHandler;
import com.huawei.ascend.runtime.engine.agentscope.AgentScopeEvent;
import com.huawei.ascend.runtime.engine.agentscope.AgentScopeHarnessAgent;
import com.huawei.ascend.runtime.engine.agentscope.AgentScopeHarnessRuntimeHandler;
import com.huawei.ascend.runtime.engine.agentscope.AgentScopeInvocation;
import com.huawei.ascend.runtime.engine.agentscope.AgentScopeMessageAdapter;
import com.huawei.ascend.runtime.engine.agentscope.AgentScopeRuntimeClient;
import com.huawei.ascend.runtime.engine.agentscope.AgentScopeRuntimeClientProperties;
import com.huawei.ascend.runtime.engine.agentscope.AgentScopeStreamAdapter;
import com.huawei.ascend.runtime.engine.spi.AgentRuntimeHandler;
import com.huawei.ascend.runtime.engine.spi.StreamAdapter;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.agent.Event;
import io.agentscope.core.agent.EventType;
import io.agentscope.core.agent.StreamOptions;
import io.agentscope.core.formatter.openai.OpenAIChatFormatter;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.model.GenerateOptions;
import io.agentscope.core.model.OpenAIChatModel;
import io.agentscope.core.tool.DefaultToolResultConverter;
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import io.agentscope.core.tool.Toolkit;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.server.context.WebServerApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class RetailWealthAdvisorAgentScopeConfiguration {

    static final String AGENT_ID = "agentscope-retail-wealth-advisor";
    static final String HARNESS_AGENT_ID = "agentscope-retail-wealth-advisor-harness";
    static final String RUNTIME_AGENT_ID = "agentscope-retail-wealth-advisor-runtime";
    static final String RUNTIME_ENDPOINT = "/sample/agentscope/retail-wealth/process";

    private static final String SYSTEM_PROMPT = """
            You are a retail wealth advisor assistant built by a bank's business engineering team with AgentScope.
            You help relationship managers draft asset allocation suggestions using bank-side systems and skills.
            You must stay within a bank wealth-management context:
            - Available sample product families are short-tenor wealth-management products, public funds,
              private funds for qualified investors only, gold products, and ETF feeder funds.
            - Do not recommend individual stocks or exchange-traded ETF products.
            - Never promise guaranteed returns.
            - Always include suitability and compliance reminders.
            Return a concise Chinese report with exactly these headings:
            客户画像摘要、当前持仓诊断、市场观点摘要、建议资产配置、收益测算、风险提示、客户经理下一步动作、合规提示。
            Keep each heading to one or two short bullets. Do not use markdown tables.
            Finish the answer with the 合规提示 section.
            Use the provided skills before making the recommendation.
            """;

    @Bean
    AgentScopeAgent retailWealthAdvisorAgent(
            @Value("${sample.agentscope.api-key:${SAA_SAMPLE_LLM_API_KEY:sk-local-placeholder}}") String apiKey,
            @Value("${sample.agentscope.api-base:${SAA_SAMPLE_AGENTSCOPE_API_BASE:http://localhost:4000/v1}}")
            String apiBase,
            @Value("${sample.agentscope.model-name:${SAA_SAMPLE_LLM_MODEL:gpt-5.4-mini}}") String modelName,
            @Value("${sample.agentscope.endpoint-path:${SAA_SAMPLE_AGENTSCOPE_ENDPOINT_PATH:/chat/completions}}")
            String endpointPath) {
        return new RetailWealthAdvisorAgent(AGENT_ID, apiKey, apiBase, endpointPath, modelName);
    }

    @Bean
    AgentRuntimeHandler retailWealthAdvisorAgentHandler(
            @Qualifier("retailWealthAdvisorAgent") AgentScopeAgent retailWealthAdvisorAgent) {
        return new AgentScopeAgentRuntimeHandler(
                AGENT_ID,
                "Retail Wealth Advisor",
                "Sample bank retail wealth advisor built with AgentScope skills.",
                retailWealthAdvisorAgent);
    }

    @Bean
    AgentScopeHarnessAgent retailWealthAdvisorHarnessAgent(
            @Qualifier("retailWealthAdvisorAgent") AgentScopeAgent retailWealthAdvisorAgent) {
        return new RetailWealthAdvisorHarnessAgent(retailWealthAdvisorAgent);
    }

    @Bean
    AgentRuntimeHandler retailWealthAdvisorHarnessAgentHandler(
            @Qualifier("retailWealthAdvisorHarnessAgent") AgentScopeHarnessAgent retailWealthAdvisorHarnessAgent) {
        return new AgentScopeHarnessRuntimeHandler(
                HARNESS_AGENT_ID,
                "Retail Wealth Advisor Harness",
                "Sample bank retail wealth advisor through an AgentScope Harness adapter.",
                retailWealthAdvisorHarnessAgent);
    }

    @Bean
    AgentRuntimeHandler retailWealthAdvisorRuntimeClientHandler(
            @Value("${sample.agentscope.retail-wealth.runtime.base-url:${SAA_SAMPLE_RETAIL_WEALTH_RUNTIME_BASE_URL:self}}")
            String baseUrl,
            @Value("${sample.agentscope.retail-wealth.runtime.endpoint-path:${SAA_SAMPLE_RETAIL_WEALTH_RUNTIME_ENDPOINT_PATH:"
                    + RUNTIME_ENDPOINT + "}}")
            String endpointPath,
            WebServerApplicationContext webServerContext) {
        return new RetailWealthAdvisorRuntimeClientHandler(
                RUNTIME_AGENT_ID,
                "Retail Wealth Advisor Runtime",
                "Sample bank retail wealth advisor reached through an AgentScope REST/SSE runtime.",
                baseUrl,
                endpointPath,
                webServerContext);
    }

    static final class RetailWealthAdvisorRuntimeClientHandler implements AgentRuntimeHandler {
        private final AgentScopeMessageAdapter messageAdapter = new AgentScopeMessageAdapter();
        private final AgentScopeStreamAdapter streamAdapter = new AgentScopeStreamAdapter();
        private final String agentId;
        private final String baseUrl;
        private final String endpointPath;
        private final WebServerApplicationContext webServerContext;

        RetailWealthAdvisorRuntimeClientHandler(
                String agentId,
                String name,
                String description,
                String baseUrl,
                String endpointPath,
                WebServerApplicationContext webServerContext) {
            this.agentId = Objects.requireNonNull(agentId, "agentId");
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(description, "description");
            this.baseUrl = Objects.requireNonNull(baseUrl, "baseUrl");
            this.endpointPath = Objects.requireNonNull(endpointPath, "endpointPath");
            this.webServerContext = Objects.requireNonNull(webServerContext, "webServerContext");
        }

        @Override
        public String agentId() {
            return agentId;
        }

        @Override
        public boolean isHealthy() {
            return true;
        }

        @Override
        public Stream<?> execute(AgentExecutionContext context) {
            AgentScopeRuntimeClientProperties properties =
                    new AgentScopeRuntimeClientProperties(resolveBaseUrl(), endpointPath);
            return new AgentScopeRuntimeClient(properties).streamEvents(messageAdapter.toInvocation(context));
        }

        @Override
        public StreamAdapter resultAdapter() {
            return streamAdapter;
        }

        private String resolveBaseUrl() {
            if (!"self".equalsIgnoreCase(baseUrl)) {
                return baseUrl;
            }
            int port = webServerContext.getWebServer().getPort();
            return "http://localhost:" + port;
        }
    }

    static final class RetailWealthAdvisorAgent implements AgentScopeAgent {
        private static final Logger LOGGER = LoggerFactory.getLogger(RetailWealthAdvisorAgent.class);
        private static final Duration MODEL_TIMEOUT = Duration.ofSeconds(60);
        private static final Pattern CUSTOMER_ID_PATTERN =
                Pattern.compile("(?i)(?:customer|客户|cust)[-_:：\\s]*([A-Za-z0-9-]+)");

        private final String name;
        private final String apiKey;
        private final String apiBase;
        private final String endpointPath;
        private final String modelName;
        private final RetailWealthAdvisorSkills skills = new RetailWealthAdvisorSkills();

        RetailWealthAdvisorAgent(String name, String apiKey, String apiBase, String endpointPath, String modelName) {
            this.name = Objects.requireNonNull(name, "name");
            this.apiKey = Objects.requireNonNull(apiKey, "apiKey");
            this.apiBase = Objects.requireNonNull(apiBase, "apiBase");
            this.endpointPath = Objects.requireNonNull(endpointPath, "endpointPath");
            this.modelName = Objects.requireNonNull(modelName, "modelName");
        }

        @Override
        public Stream<AgentScopeEvent> streamEvents(AgentScopeInvocation invocation) {
            String customerId = customerId(invocation);
            try {
                LOGGER.info(
                        "retail wealth advisor execute start tenantId={} sessionId={} taskId={} agentId={} customerId={} apiBase={} model={}",
                        invocation.tenantId(),
                        invocation.sessionId(),
                        invocation.taskId(),
                        invocation.agentId(),
                        customerId,
                        apiBase,
                        modelName);
                List<Event> events = buildAgent(invocation.agentId())
                        .stream(toAgentScopeMessages(invocation, customerId), streamOptions())
                        .collectList()
                        .block(MODEL_TIMEOUT);
                return toRuntimeEvents(events);
            } catch (Exception ex) {
                LOGGER.warn(
                        "retail wealth advisor execute failed tenantId={} sessionId={} taskId={} customerId={} errorClass={} message={}",
                        invocation.tenantId(),
                        invocation.sessionId(),
                        invocation.taskId(),
                        customerId,
                        ex.getClass().getSimpleName(),
                        errorMessage(ex));
                throw new IllegalStateException(errorMessage(ex), ex);
            }
        }

        private ReActAgent buildAgent(String agentId) {
            GenerateOptions options = GenerateOptions.builder()
                    .stream(true)
                    .temperature(0.1)
                    .maxTokens(1200)
                    .build();
            OpenAIChatModel model = OpenAIChatModel.builder()
                    .apiKey(apiKey)
                    .baseUrl(apiBase)
                    .endpointPath(endpointPath)
                    .modelName(modelName)
                    .stream(true)
                    .formatter(new OpenAIChatFormatter())
                    .generateOptions(options)
                    .build();
            Toolkit toolkit = new Toolkit();
            toolkit.registerTool(skills);
            return ReActAgent.builder()
                    .name(agentId)
                    .description("Bank retail wealth advisor sample served by agent-runtime A2A.")
                    .sysPrompt(SYSTEM_PROMPT)
                    .model(model)
                    .toolkit(toolkit)
                    .maxIters(6)
                    .generateOptions(options)
                    .build();
        }

        private List<Msg> toAgentScopeMessages(AgentScopeInvocation invocation, String customerId) {
            List<Msg> messages = new ArrayList<>();
            messages.add(Msg.builder()
                    .name(name)
                    .role(MsgRole.SYSTEM)
                    .textContent(skillSnapshot(customerId))
                    .build());
            for (Message message : invocation.messages()) {
                messages.add(Msg.builder()
                        .name(name)
                        .role(toAgentScopeRole(message.role()))
                        .textContent(message.text())
                        .metadata(Map.of(
                                "tenantId", invocation.tenantId(),
                                "sessionId", invocation.sessionId(),
                                "taskId", invocation.taskId(),
                                "agentId", invocation.agentId(),
                                "customerId", customerId))
                        .build());
            }
            if (messages.size() == 1) {
                messages.add(Msg.builder().name(name).role(MsgRole.USER).textContent("").build());
            }
            return messages;
        }

        private String skillSnapshot(String customerId) {
            return """
                    Customer id for this run: %s
                    Skill snapshot from bank-side systems:
                    - Customer profile skill: %s
                    - Current holdings skill: %s
                    - Market insight skill: %s
                    - Product universe skill: %s
                    - Allocation projection skill: %s
                    Use these values as sample skill results when drafting the answer.
                    """
                    .formatted(
                            customerId,
                            skills.queryCustomerProfile(customerId),
                            skills.queryCurrentHoldings(customerId),
                            skills.analyzeMarketInsight("balanced"),
                            skills.matchBankProductUniverse("balanced"),
                            skills.calculateAllocationProjection(customerId, "balanced"));
        }

        private String customerId(AgentScopeInvocation invocation) {
            String input = invocation.messages().stream()
                    .map(Message::text)
                    .filter(Objects::nonNull)
                    .reduce("", (left, right) -> left + "\n" + right);
            Matcher matcher = CUSTOMER_ID_PATTERN.matcher(input);
            if (matcher.find()) {
                return matcher.group(1).toUpperCase(Locale.ROOT);
            }
            return "BANK-CUST-001";
        }

        private StreamOptions streamOptions() {
            return StreamOptions.builder()
                    .eventTypes(EventType.AGENT_RESULT)
                    .incremental(false)
                    .build();
        }

        private Stream<AgentScopeEvent> toRuntimeEvents(List<Event> events) {
            if (events == null || events.isEmpty()) {
                return Stream.of(AgentScopeEvent.completed(""));
            }
            List<AgentScopeEvent> results = new ArrayList<>();
            StringBuilder emitted = new StringBuilder();
            String lastText = "";
            for (Event event : events) {
                String text = event.getMessage() == null ? "" : event.getMessage().getTextContent();
                lastText = text;
                if (event.isLast()) {
                    results.add(AgentScopeEvent.completed(emitted.isEmpty() ? text : ""));
                } else if (!text.isBlank()) {
                    emitted.append(text);
                    results.add(AgentScopeEvent.output(text));
                }
            }
            if (results.stream().noneMatch(event -> event.type() == AgentScopeEvent.Type.COMPLETED)) {
                results.add(AgentScopeEvent.completed(lastText));
            }
            return results.stream();
        }

        private MsgRole toAgentScopeRole(Role role) {
            return switch (role) {
                case ASSISTANT -> MsgRole.ASSISTANT;
                case SYSTEM -> MsgRole.SYSTEM;
                case TOOL -> MsgRole.TOOL;
                case USER -> MsgRole.USER;
            };
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

    static final class RetailWealthAdvisorHarnessAgent implements AgentScopeHarnessAgent {
        private final AgentScopeAgent delegate;

        RetailWealthAdvisorHarnessAgent(AgentScopeAgent delegate) {
            this.delegate = Objects.requireNonNull(delegate, "delegate");
        }

        @Override
        public Stream<AgentScopeEvent> streamEvents(AgentScopeInvocation invocation) {
            AgentScopeInvocation harnessedInvocation = new AgentScopeInvocation(
                    invocation.tenantId(),
                    invocation.userId(),
                    invocation.sessionId(),
                    invocation.taskId(),
                    invocation.agentId(),
                    invocation.inputType(),
                    invocation.messages(),
                    invocation.variables(),
                    withHarnessMetadata(invocation.metadata()));
            return delegate.streamEvents(harnessedInvocation);
        }

        private Map<String, Object> withHarnessMetadata(Map<String, Object> metadata) {
            Map<String, Object> result = new LinkedHashMap<>(metadata);
            result.put("retailWealthAdvisorHarness", Boolean.TRUE);
            result.put("advisorScenario", "bank-retail-wealth");
            return Map.copyOf(result);
        }
    }

    static final class RetailWealthAdvisorSkills {

        @Tool(
                name = "query_customer_profile",
                description = "Query sample bank customer profile and suitability information.",
                converter = DefaultToolResultConverter.class)
        public String queryCustomerProfile(
                @ToolParam(name = "customer_id", required = true, description = "Bank customer id")
                String customerId) {
            return """
                    customerId=%s; riskLevel=R3-balanced; investableAssets=1800000 CNY; horizon=6-12 months;
                    liquidityPreference=high; qualifiedInvestor=false; serviceChannel=retail relationship manager;
                    objective=preserve capital while improving yield over demand deposits
                    """.formatted(customerId);
        }

        @Tool(
                name = "query_current_holdings",
                description = "Query sample current holdings from bank-side portfolio systems.",
                converter = DefaultToolResultConverter.class)
        public String queryCurrentHoldings(
                @ToolParam(name = "customer_id", required = true, description = "Bank customer id")
                String customerId) {
            return """
                    customerId=%s; demandDeposit=18%%; shortTenorWealth=32%%; publicFund=22%%;
                    gold=8%%; cashManagement=20%%; privateFund=0%%; singleAssetConcentration=max 32%%
                    """.formatted(customerId);
        }

        @Tool(
                name = "analyze_market_insight",
                description = "Return sample market views for bank wealth management scenarios.",
                converter = DefaultToolResultConverter.class)
        public String analyzeMarketInsight(
                @ToolParam(name = "risk_preference", required = true, description = "Customer risk preference")
                String riskPreference) {
            return """
                    riskPreference=%s; moneyMarket=neutral-positive; bond=positive for short/intermediate duration;
                    equity=volatile and use public funds or ETF feeder funds only; gold=hedging value but high volatility;
                    keyRisks=interest-rate repricing, equity drawdown, gold price volatility
                    """.formatted(riskPreference);
        }

        @Tool(
                name = "match_bank_product_universe",
                description = "Match sample bank-available product families for a wealth-management recommendation.",
                converter = DefaultToolResultConverter.class)
        public String matchBankProductUniverse(
                @ToolParam(name = "risk_preference", required = true, description = "Customer risk preference")
                String riskPreference) {
            return """
                    riskPreference=%s; allowedFamilies=7-day wealth product, 30-day wealth product,
                    180-day wealth product, public bond fund, public balanced fund, ETF feeder fund,
                    physical gold, paper gold; excludedFamilies=individual stocks, exchange-traded ETF;
                    privateFundEligibility=not eligible in this sample profile
                    """.formatted(riskPreference);
        }

        @Tool(
                name = "calculate_allocation_projection",
                description = "Calculate sample expected return range and stress scenario for an allocation.",
                converter = DefaultToolResultConverter.class)
        public String calculateAllocationProjection(
                @ToolParam(name = "customer_id", required = true, description = "Bank customer id")
                String customerId,
                @ToolParam(name = "risk_preference", required = true, description = "Customer risk preference")
                String riskPreference) {
            return """
                    customerId=%s; riskPreference=%s; suggestedAllocation=7-day wealth 15%%, 30-day wealth 25%%,
                    180-day wealth 20%%, public bond fund 20%%, ETF feeder fund 10%%, gold 10%%;
                    expectedAnnualizedRange=2.4%%-4.6%%; sixMonthStressScenario=-2.0%% to 0.6%%;
                    assumptions=sample market view, no guaranteed return, fees and product terms not included
                    """.formatted(customerId, riskPreference);
        }
    }
}
