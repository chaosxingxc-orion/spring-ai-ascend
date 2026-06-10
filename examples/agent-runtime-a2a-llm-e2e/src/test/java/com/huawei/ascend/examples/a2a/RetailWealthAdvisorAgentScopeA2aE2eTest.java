package com.huawei.ascend.examples.a2a;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.a2aproject.sdk.spec.AgentCard;
import org.a2aproject.sdk.spec.StreamingEventKind;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

@Tag("e2e")
@ResourceLock("real-llm")
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        classes = OpenJiuwenA2aAgentRuntimeApplication.class,
        properties = "sample.a2a.agent=retail-wealth-advisor")
class RetailWealthAdvisorAgentScopeA2aE2eTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(90);
    private static final String PROMPT = """
            请为客户 BANK-CUST-001 生成一份稳健型资产配置建议。
            客户希望未来 6 个月保持较好流动性，同时希望收益高于活期存款。
            请使用零售客户经理助手的行内系统、行情分析和收益测算技能。
            """;

    @LocalServerPort
    private int port;

    @Test
    void a2aClientCanStreamRetailWealthAdvisorSdkAgentThroughAgentRuntimeOnly() throws Exception {
        assumeRealLlmConfigured("Retail Wealth Advisor AgentScope SDK agent");

        SampleA2aClient client = new SampleA2aClient(URI.create("http://localhost:" + port), TIMEOUT);
        AgentCard card = client.agentCard();
        assertThat(card.name()).isEqualTo(RetailWealthAdvisorAgentScopeConfiguration.AGENT_ID);
        assertAdvisorPathReturnsAllocationSuggestion(client, card.name());
    }

    private void assertAdvisorPathReturnsAllocationSuggestion(SampleA2aClient client, String agentId) throws Exception {
        String sessionId = "session-" + UUID.randomUUID();
        List<StreamingEventKind> events = client.streamMessage("sample-user", agentId, sessionId, PROMPT);
        String answer = SampleA2aClient.textFrom(events);

        assertThat(events).isNotEmpty();
        assertThat(events).anySatisfy(event -> assertThat(SampleA2aClient.isTerminal(event)).isTrue());
        assertThat(answer)
                .contains("客户画像")
                .contains("资产配置")
                .contains("收益测算")
                .contains("风险提示")
                .contains("合规提示");
    }

    private static void assumeRealLlmConfigured(String sampleName) {
        assumeTrue(hasText(System.getenv("SAA_SAMPLE_LLM_API_KEY")),
                "SAA_SAMPLE_LLM_API_KEY not set; skipping real " + sampleName + " E2E sample");
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
