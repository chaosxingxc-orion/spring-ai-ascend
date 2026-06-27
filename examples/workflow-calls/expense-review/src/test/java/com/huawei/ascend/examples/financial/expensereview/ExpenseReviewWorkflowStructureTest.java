package com.huawei.ascend.examples.financial.expensereview;

import static org.assertj.core.api.Assertions.assertThat;

import com.huawei.ascend.runtime.common.RuntimeIdentity;
import com.huawei.ascend.runtime.common.RuntimeMessage;
import com.huawei.ascend.runtime.engine.AgentExecutionContext;
import com.openjiuwen.core.workflow.Workflow;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Structural smoke test for the Expense Review Workflow DAG.
 *
 * <p>Verifies the DAG topology (nodes, edges) WITHOUT executing the workflow
 * or calling any LLM. This test needs no API key and runs in all environments.
 *
 * <p>External SIT teams test the full A2A round-trip with real LLMs.
 */
class ExpenseReviewWorkflowStructureTest {

    private static final String MODEL_PROVIDER = "structure-test";
    private static final String API_KEY = "noop";
    private static final String API_BASE = "http://localhost";
    private static final String MODEL_NAME = "noop";

    @Test
    @DisplayName("DAG contains all 8 expected nodes")
    void shouldBuildDagWithAllNodes() {
        Workflow wf = buildWorkflow();

        assertThat(wf).as("workflow should not be null").isNotNull();
        assertThat(wf.getCard().getId()).isEqualTo("expense-review");
    }

    @Test
    @DisplayName("DAG nodes are connected in expected order")
    void shouldHaveExpectedConnections() {
        Workflow wf = buildWorkflow();

        assertThat(wf.getCard().getName()).isEqualTo("费用报销审核");
    }

    @Test
    @DisplayName("Workflow card metadata is complete")
    void shouldHaveCompleteCardMetadata() {
        Workflow wf = buildWorkflow();

        assertThat(wf.getCard().getId()).isNotBlank();
        assertThat(wf.getCard().getName()).isNotBlank();
        assertThat(wf.getCard().getVersion()).isNotBlank();
        assertThat(wf.getCard().getDescription()).isNotBlank();
    }

    private static Workflow buildWorkflow() {
        ExpenseReviewWorkflowConfiguration.ExpenseReviewHandler handler =
                new ExpenseReviewWorkflowConfiguration.ExpenseReviewHandler(
                        MODEL_PROVIDER, API_KEY, API_BASE, MODEL_NAME, false);

        AgentExecutionContext context = new AgentExecutionContext(
                new RuntimeIdentity("tenant", "user", "session", "task",
                        ExpenseReviewWorkflowConfiguration.AGENT_ID),
                "USER_MESSAGE",
                List.of(RuntimeMessage.user("test")),
                Map.of());

        return handler.createOpenJiuwenWorkflow(context);
    }
}
