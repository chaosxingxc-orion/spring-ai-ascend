package com.openjiuwen.runtime.core.engine;

import com.openjiuwen.runtime.core.fixtures.MockChatModel;
import com.openjiuwen.runtime.core.fixtures.MockCheckpointStore;
import com.openjiuwen.runtime.core.fixtures.MockToolProvider;
import com.openjiuwen.runtime.core.fixtures.TestKernelFactory;
import com.openjiuwen.core.kernel.model.*;
import com.openjiuwen.core.meta.AgentDefinition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * AgentKernel 7 个系统调用测试。
 *
 * 测试目标：验证 DefaultAgentKernel 的每个系统调用的基本契约。
 * 不依赖外部 LLM，使用 MockChatModel 替代。
 */
@DisplayName("AgentKernel: 7 个系统调用")
class AgentKernelTest {

    private MockCheckpointStore checkpointStore;

    @BeforeEach
    void setUp() {
        checkpointStore = new MockCheckpointStore();
    }

    // ==================== think ====================

    @Nested
    @DisplayName("think: LLM 推理调用")
    class ThinkTest {

        @Test
        @DisplayName("正常调用返回 LLM 响应")
        void think_returnsLlmResponse() {
            MockChatModel llm = new MockChatModel("这是LLM的回答");
            DefaultAgentKernel kernel = TestKernelFactory.createKernelWithStore(
                llm, new MockToolProvider(), checkpointStore, new DefaultSafetyBoundary());

            BudgetLimits budget = BudgetLimits.start(Budget.Fixed.productionDefault());

            StepVerifier.create(kernel.think("你好", budget))
                .assertNext(response -> assertEquals("这是LLM的回答", response))
                .verifyComplete();
        }

        @Test
        @DisplayName("预算耗尽时抛出 SafetyViolationException")
        void think_budgetExceeded_throwsViolation() {
            MockChatModel llm = new MockChatModel("不应该到达这里");
            DefaultAgentKernel kernel = TestKernelFactory.createKernelWithStore(
                llm, new MockToolProvider(), checkpointStore, new DefaultSafetyBoundary());

            Budget budget = new Budget.Fixed(2, 5, 100L, 0L);
            BudgetLimits exceeded = new BudgetLimits(budget, 2, 0, 0L, 0L);

            StepVerifier.create(kernel.think("你好", exceeded))
                .expectError(DefaultAgentKernel.SafetyViolationException.class)
                .verify(Duration.ofSeconds(5));
        }

        @Test
        @DisplayName("慢滴流被 total-duration cap 截断（idle-timeout 触不到的永不完成流）")
        void think_slowDripStream_totalDurationCap_cutsTimeout() {
            // 慢滴：stream() 以 <idle(30s)间隔永远滴 chunk 且永不 complete →
            // idle-timeout 每 chunk 重置永不触发 + reduce 等 onComplete 永不发射 → think() 挂死。
            // reduce 后 Mono.timeout(totalCap) 总时限截断（不随 chunk 重置）。
            DefaultAgentKernel kernel = slowDripKernel();

            // 小 totalCap(400ms)让测试快；idle-timeout(30s)远大于滴速(50ms)→唯一能截的是 total-cap。
            // 预算未超(used 全 0)，checkBudget 放行 → 真正测 total-cap 机制，非预算门。
            Budget budget = new Budget.Fixed(10, 20, 100_000L, 400L);
            BudgetLimits limits = BudgetLimits.start(budget);

            // cap 在场→400ms 后 java.util.concurrent.TimeoutException；
            // 突变(删 reduced.timeout)→流永不完成→verify(2s)超时 RED（承重 RED 证明该行承重）。
            StepVerifier.create(kernel.think("慢滴", limits))
                .expectError(java.util.concurrent.TimeoutException.class)
                .verify(Duration.ofSeconds(2));
        }

        @Test
        @DisplayName("cap 值随 budget 缩放（堵硬编码魔数突变，budget 驱动）")
        void think_totalCap_scalesWithBudget_notHardcoded() {
            // 同一慢滴流，budget=2000ms。cap 真取自 budget → 800ms 观测窗内源既不 complete(reduce 永不发射)
            // 也不 error(cap 未到 2000ms)→ 无事件。若硬编码成 Duration.ofMillis(400) 魔数(绕过 budget)→
            // 400ms error → expectNoEvent(800ms) 抓 RED。证明 cap 是 budget 驱动变量非魔数。
            DefaultAgentKernel kernel = slowDripKernel();
            Budget budget = new Budget.Fixed(10, 20, 100_000L, 2_000L);
            BudgetLimits limits = BudgetLimits.start(budget);

            StepVerifier.create(kernel.think("慢滴", limits))
                .expectSubscription()
                .expectNoEvent(Duration.ofMillis(800))
                .thenCancel()
                .verify();
        }

        @Test
        @DisplayName("Unlimited(timeoutMillis=0)跳过 cap，保留无总时限契约")
        void think_unlimitedBudget_skipsTotalCap() {
            // timeoutMillis=0 = Unlimited/无总时限契约。guard `if(totalCapMillis>0)` 跳过 cap →
            // 慢滴流 800ms 内不 error(idle 30s 也不触发)→ 无事件 → 取消。
            // 突变(删 guard，直接 .timeout(Duration.ofMillis(0)))→ Mono.timeout(0) 立即 error → expectNoEvent 抓 RED。
            // 钉死 guard 的 false 分支(Unlimited 跳过)，不改既有语义由测试而非仅注释守。
            // (expectSubscription 先消费 onSubscribe，否则 expectNoEvent 会把订阅信号当事件误判失败。)
            DefaultAgentKernel kernel = slowDripKernel();
            Budget budget = new Budget.Fixed(10, 20, 100_000L, 0L);
            BudgetLimits limits = BudgetLimits.start(budget);

            StepVerifier.create(kernel.think("慢滴", limits))
                .expectSubscription()
                .expectNoEvent(Duration.ofMillis(800))
                .thenCancel()
                .verify();
        }

        /** 慢滴桩 kernel：stream() 每 50ms 滴一 chunk、永不 complete（reduce 永不发射 → 唯一 cap 能救）。 */
        private DefaultAgentKernel slowDripKernel() {
            DefaultAgentKernel.LLMProvider slowDrip = new DefaultAgentKernel.LLMProvider() {
                @Override public String call(String prompt) { return "unused"; }
                @Override
                public Flux<String> stream(String prompt) {
                    return Flux.interval(Duration.ofMillis(50)).map(i -> "chunk ");
                }
            };
            return new DefaultAgentKernel(
                slowDrip, new MockToolProvider().build(), checkpointStore, new DefaultSafetyBoundary());
        }
    }

    // ==================== invokeTool ====================

    @Nested
    @DisplayName("invokeTool: 工具调用")
    class InvokeToolTest {

        @Test
        @DisplayName("调用已注册工具返回 ToolResult.ok")
        void invokeTool_registeredTool_returnsOk() {
            MockToolProvider tools = new MockToolProvider()
                .register("checkOrder", Map.of("orderId", "ORD-001", "status", "SHIPPED"));

            DefaultAgentKernel kernel = TestKernelFactory.createKernelWithStore(
                new MockChatModel("unused"), tools, checkpointStore, new DefaultSafetyBoundary());

            BudgetLimits budget = BudgetLimits.start(Budget.Fixed.productionDefault());
            ToolName toolName = new ToolName("checkOrder");

            StepVerifier.create(kernel.invokeTool(toolName, Map.of("orderId", "ORD-001"), budget))
                .assertNext(result -> {
                    assertTrue(result.success());
                    assertEquals(toolName, result.toolName());
                    assertNotNull(result.result());
                })
                .verifyComplete();
        }

        @Test
        @DisplayName("调用未注册工具返回 ToolResult.fail")
        void invokeTool_unregisteredTool_returnsFail() {
            MockToolProvider tools = new MockToolProvider();
            DefaultAgentKernel kernel = TestKernelFactory.createKernelWithStore(
                new MockChatModel("unused"), tools, checkpointStore, new DefaultSafetyBoundary());

            BudgetLimits budget = BudgetLimits.start(Budget.Fixed.productionDefault());
            ToolName toolName = new ToolName("nonExistentTool");

            StepVerifier.create(kernel.invokeTool(toolName, Map.of(), budget))
                .assertNext(result -> {
                    assertFalse(result.success());
                    assertTrue(result.error().contains("工具未注册"));
                })
                .verifyComplete();
        }

        @Test
        @DisplayName("工具执行异常返回 ToolResult.fail")
        void invokeTool_executionException_returnsFail() {
            MockToolProvider tools = new MockToolProvider()
                .registerFailing("badTool", "连接超时");

            DefaultAgentKernel kernel = TestKernelFactory.createKernelWithStore(
                new MockChatModel("unused"), tools, checkpointStore, new DefaultSafetyBoundary());

            BudgetLimits budget = BudgetLimits.start(Budget.Fixed.productionDefault());

            StepVerifier.create(kernel.invokeTool(new ToolName("badTool"), Map.of(), budget))
                .assertNext(result -> {
                    assertFalse(result.success());
                    assertTrue(result.error().contains("工具执行失败"));
                })
                .verifyComplete();
        }
    }

    // ==================== saveCheckpoint / restoreCheckpoint ====================

    @Nested
    @DisplayName("saveCheckpoint / restoreCheckpoint: 检查点")
    class CheckpointTest {

        @Test
        @DisplayName("保存检查点并恢复")
        void saveAndRestore() {
            DefaultAgentKernel kernel = TestKernelFactory.createKernelWithStore(
                new MockChatModel("unused"), new MockToolProvider(),
                checkpointStore, new DefaultSafetyBoundary());

            TaskId taskId = TaskId.generate();
            Checkpoint checkpoint = Checkpoint.of(taskId, "EXECUTING", 2, "{\"progress\":50}");

            // IFF: saveCheckpoint must return the saved checkpoint's id, not any non-null id.
            // Strip the .thenReturn(checkpoint.checkpointId()) and this fails.
            StepVerifier.create(kernel.saveCheckpoint(checkpoint))
                .assertNext(cpId -> assertEquals(checkpoint.checkpointId(), cpId))
                .verifyComplete();

            StepVerifier.create(kernel.restoreCheckpoint(taskId))
                .assertNext(restored -> {
                    assertEquals(taskId, restored.taskId());
                    assertEquals("EXECUTING", restored.phase());
                    assertEquals(2, restored.stepIndex());
                    assertEquals("{\"progress\":50}", restored.stateJson());
                })
                .verifyComplete();
        }

        @Test
        @DisplayName("恢复不存在的检查点返回 null")
        void restoreNonExistent_returnsEmpty() {
            DefaultAgentKernel kernel = TestKernelFactory.createKernelWithStore(
                new MockChatModel("unused"), new MockToolProvider(),
                checkpointStore, new DefaultSafetyBoundary());

            StepVerifier.create(kernel.restoreCheckpoint(TaskId.generate()))
                .expectNextCount(0)
                .verifyComplete();
        }
    }

    // ==================== yield ====================

    @Nested
    @DisplayName("yield: 让出执行权")
    class YieldTest {

        @Test
        @DisplayName("yield 保存检查点并发布 TASK_PAUSED 事件")
        void yield_savesCheckpointAndEmitsEvent() {
            DefaultAgentKernel kernel = TestKernelFactory.createKernelWithStore(
                new MockChatModel("unused"), new MockToolProvider(),
                checkpointStore, new DefaultSafetyBoundary());

            TaskId taskId = TaskId.generate();

            // Subscribe to events first
            Flux<AgentEvent> events = kernel.observeEvents(taskId);

            YieldReason reason = new YieldReason.WaitingForApproval("gate-1", "等待审批");

            StepVerifier.create(kernel.yield(taskId, reason, "{\"status\":\"waiting\"}"))
                .assertNext(cpId -> assertNotNull(cpId))
                .verifyComplete();

            // Verify checkpoint was saved
            assertEquals(1, checkpointStore.countForTask(taskId));

            // Verify event was emitted
            StepVerifier.create(events.take(Duration.ofSeconds(2)))
                .assertNext(event -> {
                    assertEquals(taskId, event.taskId());
                    assertEquals(EventType.TASK_PAUSED, event.type());
                })
                .verifyComplete();
        }
    }

    // ==================== emit / observeEvents ====================

    @Nested
    @DisplayName("emit / observeEvents: 事件发布订阅")
    class EventTest {

        @Test
        @DisplayName("emit 后 observeEvents 能收到事件")
        void emitAndObserve() {
            DefaultAgentKernel kernel = TestKernelFactory.createKernelWithStore(
                new MockChatModel("unused"), new MockToolProvider(),
                checkpointStore, new DefaultSafetyBoundary());

            TaskId taskId = TaskId.generate();

            // Subscribe before emitting
            Flux<AgentEvent> events = kernel.observeEvents(taskId);

            AgentEvent event1 = AgentEvent.of(taskId, EventType.TASK_STARTED, "任务开始");
            AgentEvent event2 = AgentEvent.of(taskId, EventType.THINKING, "思考中");

            // Emit events
            kernel.emit(event1).block();
            kernel.emit(event2).block();

            StepVerifier.create(events.take(2))
                .assertNext(e -> assertEquals(EventType.TASK_STARTED, e.type()))
                .assertNext(e -> assertEquals(EventType.THINKING, e.type()))
                .verifyComplete();
        }

        @Test
        @DisplayName("多个事件按顺序接收")
        void multipleEventsInOrder() {
            DefaultAgentKernel kernel = TestKernelFactory.createKernelWithStore(
                new MockChatModel("unused"), new MockToolProvider(),
                checkpointStore, new DefaultSafetyBoundary());

            TaskId taskId = TaskId.generate();
            Flux<AgentEvent> events = kernel.observeEvents(taskId);

            for (int i = 0; i < 5; i++) {
                kernel.emit(AgentEvent.of(taskId, EventType.TOOL_CALL, "tool-" + i)).block();
            }

            StepVerifier.create(events.take(5))
                .expectNextCount(5)
                .verifyComplete();
        }
    }

    // ==================== observe ====================

    @Nested
    @DisplayName("observe: 观察执行状态")
    class ObserveTest {

        @Test
        @DisplayName("observe 返回已记录的节点结果")
        void observe_returnsRecordedResults() {
            DefaultAgentKernel kernel = TestKernelFactory.createKernelWithStore(
                new MockChatModel("unused"), new MockToolProvider(),
                checkpointStore, new DefaultSafetyBoundary());

            TaskId taskId = TaskId.generate();
            NodeId nodeA = new NodeId("A");
            NodeId nodeB = new NodeId("B");

            kernel.recordNodeResult(taskId, nodeA, "resultA");
            kernel.recordNodeResult(taskId, nodeB, "resultB");

            StepVerifier.create(kernel.observe(taskId, Set.of(nodeA)))
                .assertNext(results -> {
                    assertEquals(1, results.size());
                    assertEquals("resultA", results.get(nodeA));
                })
                .verifyComplete();
        }

        @Test
        @DisplayName("observe 空 nodeId 集合返回全部结果")
        void observe_emptySet_returnsAll() {
            DefaultAgentKernel kernel = TestKernelFactory.createKernelWithStore(
                new MockChatModel("unused"), new MockToolProvider(),
                checkpointStore, new DefaultSafetyBoundary());

            TaskId taskId = TaskId.generate();
            kernel.recordNodeResult(taskId, new NodeId("A"), "resultA");
            kernel.recordNodeResult(taskId, new NodeId("B"), "resultB");

            StepVerifier.create(kernel.observe(taskId, Set.of()))
                .assertNext(results -> assertEquals(2, results.size()))
                .verifyComplete();
        }
    }

    // ==================== getToolDefinitions（工具签名透传） ====================

    @Nested
    @DisplayName("getToolDefinitions: 工具签名自省")
    class ToolDefinitionsTest {

        @Test
        @DisplayName("5 参构造注入签名 → getToolDefinitions 返回 schema（含参数名/类型）")
        void getToolDefinitions_5argConstructor_returnsDefinitions() {
            AgentDefinition.ToolDefinition getCaseStatus = new AgentDefinition.ToolDefinition(
                "getCaseStatus", "查询案件状态",
                List.of(new AgentDefinition.ParameterDefinition("caseNo", "案件号", "String", true)));

            DefaultAgentKernel kernel = new DefaultAgentKernel(
                new MockChatModel("unused"),
                Map.of(),
                Map.of(new ToolName("getCaseStatus"), getCaseStatus),
                checkpointStore,
                new DefaultSafetyBoundary());

            List<AgentDefinition.ToolDefinition> result = kernel.getToolDefinitions();
            assertEquals(1, result.size());
            AgentDefinition.ToolDefinition def = result.get(0);
            assertEquals("getCaseStatus", def.name());
            assertEquals(1, def.parameters().size());
            assertEquals("caseNo", def.parameters().get(0).name());
            assertEquals("String", def.parameters().get(0).type());
        }

        @Test
        @DisplayName("4 参便捷构造（无 schema）→ getToolDefinitions 返回空（回退裸名 = 旧行为）")
        void getToolDefinitions_4argConstructor_returnsEmpty() {
            // 4 参重载内部传空 toolDefinitions；planner 对无签名工具回退裸名。
            // 保留既有测试构造点不变即靠此重载。
            DefaultAgentKernel kernel = TestKernelFactory.createKernelWithStore(
                new MockChatModel("unused"), new MockToolProvider(),
                checkpointStore, new DefaultSafetyBoundary());

            assertTrue(kernel.getToolDefinitions().isEmpty());
        }
    }
}
