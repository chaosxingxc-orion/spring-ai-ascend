package com.huawei.ascend.runtime.engine.alpha.gepa3;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class MinimalAgentEngineTest {

    // ========================================================================
    // E2E scenario tests (E1-E4, original)
    // ========================================================================

    @Test
    void e1_planOrAnswerError_localReplan_succeeds() {
        AtomicInteger seq = new AtomicInteger(0);
        List<String> events = new ArrayList<>();

        MinimalAgentEngine.LlmProvider llm = prompt -> {
            int s = seq.getAndIncrement();
            if (s == 0) return """
                [{"id":"think","type":"LLM_CALL",
                  "description":"Analyze: what is 2+3?","inputs":{}},
                 {"id":"answer","type":"LLM_CALL",
                  "description":"State the final answer","inputs":{}}]""";
            if (s == 1) return "Analysis: 2+3 should be 6";
            if (s == 2) return "The answer is 6";
            if (s == 3) return """
                {"passed":false,"failedNodes":["answer"],
                 "feedback":"2+3 equals 5, not 6"}""";
            if (s == 4) {
                if (prompt.contains("<correction>") && prompt.contains("2+3 equals 5"))
                    return "The answer is 5";
                return "The answer is 6";
            }
            if (s == 5) return """
                {"passed":true,"failedNodes":[],
                 "feedback":"Answer is now correct: 2+3=5"}""";
            return "{}";
        };

        MinimalAgentEngine.ToolExecutor tools = (n, a) -> {
            throw new MinimalAgentEngine.ToolException("no tools");
        };

        MinimalAgentEngine.EventListener listener = (t, d) -> events.add(t + ":" + d);

        MinimalAgentEngine engine = new MinimalAgentEngine(llm, tools, listener, 3);
        String result = engine.execute("What is 2+3?");

        assertThat(result).contains("5");
        assertThat(result).doesNotContain("DEGRADED");
        assertThat(result).doesNotContain("TASK_FAILED");
        assertThat(events).anyMatch(e -> e.startsWith("ROOT_CAUSE_DIAGNOSED")
                && e.contains("PlanOrAnswerError"));
        assertThat(events).anyMatch(e -> e.startsWith("REPLAN_LOCAL"));
        assertThat(events).anyMatch(e -> e.startsWith("VERIFY_PASSED"));
        assertThat(events).anyMatch(e -> e.startsWith("TASK_COMPLETED")
                && e.contains("verified"));
    }

    @Test
    void e2_perceptionUnreliable_acceptPartial() {
        AtomicInteger seq = new AtomicInteger(0);
        List<String> events = new ArrayList<>();

        MinimalAgentEngine.LlmProvider llm = prompt -> {
            int s = seq.getAndIncrement();
            if (s == 0) return """
                [{"id":"analyze","type":"LLM_CALL",
                  "description":"Analyze market data","inputs":{}}]""";
            if (s == 1) return "Market analysis: bullish trend";
            if (s == 2) throw new RuntimeException("Verifier crashed: LLM timeout");
            return "{}";
        };

        MinimalAgentEngine.ToolExecutor tools = (n, a) -> {
            throw new MinimalAgentEngine.ToolException("no tools");
        };

        MinimalAgentEngine.EventListener listener = (t, d) -> events.add(t + ":" + d);
        MinimalAgentEngine engine = new MinimalAgentEngine(llm, tools, listener, 3);
        String result = engine.execute("Analyze market data");

        assertThat(result).contains("DEGRADED");
        assertThat(result).contains("Perception unreliable");
        assertThat(events).anyMatch(e -> e.startsWith("ROOT_CAUSE_DIAGNOSED")
                && e.contains("PerceptionUnreliable"));
        assertThat(events).noneMatch(e -> e.startsWith("REPLAN_"));
        assertThat(events).noneMatch(e -> e.startsWith("VERIFY_PASSED"));
    }

    @Test
    void e3_deviceFailure_acceptPartial() {
        AtomicInteger seq = new AtomicInteger(0);
        List<String> events = new ArrayList<>();
        AtomicInteger toolCalls = new AtomicInteger(0);

        MinimalAgentEngine.LlmProvider llm = prompt -> {
            int s = seq.getAndIncrement();
            if (s == 0) return """
                [{"id":"think","type":"LLM_CALL",
                  "description":"Decide what data to fetch","inputs":{}},
                 {"id":"fetch","type":"TOOL_CALL",
                  "description":"fetchData",
                  "inputs":{"symbol":"${think}"}}]""";
            if (s == 1) return "Fetch stock price for AAPL";
            return "{}";
        };

        MinimalAgentEngine.ToolExecutor tools = (n, a) -> {
            toolCalls.incrementAndGet();
            throw new MinimalAgentEngine.ToolException("fetchData: connection refused");
        };

        MinimalAgentEngine.EventListener listener = (t, d) -> events.add(t + ":" + d);
        MinimalAgentEngine engine = new MinimalAgentEngine(llm, tools, listener, 3);
        String result = engine.execute("Fetch AAPL stock data");

        assertThat(result).contains("DEGRADED");
        assertThat(result).contains("Device failure");
        assertThat(toolCalls.get()).isEqualTo(1);
        assertThat(events).anyMatch(e -> e.startsWith("ROOT_CAUSE_DIAGNOSED")
                && e.contains("DeviceFailure"));
        assertThat(events).noneMatch(e -> e.startsWith("REPLAN_"));
    }

    @Test
    void e4_success_directHappyPath() {
        AtomicInteger seq = new AtomicInteger(0);
        List<String> events = new ArrayList<>();

        MinimalAgentEngine.LlmProvider llm = prompt -> {
            int s = seq.getAndIncrement();
            if (s == 0) return """
                [{"id":"greet","type":"LLM_CALL",
                  "description":"Generate greeting for Alice","inputs":{}}]""";
            if (s == 1) return "Hello, Alice! Welcome.";
            if (s == 2) return """
                {"passed":true,"failedNodes":[],
                 "feedback":"Greeting is appropriate"}""";
            return "{}";
        };

        MinimalAgentEngine.ToolExecutor tools = (n, a) -> {
            throw new MinimalAgentEngine.ToolException("no tools");
        };

        MinimalAgentEngine.EventListener listener = (t, d) -> events.add(t + ":" + d);
        MinimalAgentEngine engine = new MinimalAgentEngine(llm, tools, listener, 3);
        String result = engine.execute("Greet user Alice");

        assertThat(result).contains("Hello, Alice");
        assertThat(result).doesNotContain("DEGRADED");
        assertThat(result).doesNotContain("TASK_FAILED");
        assertThat(events).anyMatch(e -> e.startsWith("VERIFY_PASSED"));
        assertThat(events).noneMatch(e -> e.startsWith("VERIFY_FAILED"));
        assertThat(events).noneMatch(e -> e.startsWith("REPLAN_"));
    }

    // ========================================================================
    // diagnoseRootCause tests (original)
    // ========================================================================

    @Test
    void diagnoseRootCause_verifyThrew_perceptionUnreliable() {
        MinimalAgentEngine.VerifyResult vr =
                new MinimalAgentEngine.VerifyResult(false, Set.of("n1"), "");
        MinimalAgentEngine.RootCause c = MinimalAgentEngine.diagnoseRootCause(
                vr, Set.of(), Set.of(), true);
        assertThat(c).isInstanceOf(MinimalAgentEngine.RootCause.PerceptionUnreliable.class);
    }

    @Test
    void diagnoseRootCause_overlapWithExecFailed_deviceFailure() {
        MinimalAgentEngine.VerifyResult vr =
                new MinimalAgentEngine.VerifyResult(false, Set.of("n1", "n2"), "fail");
        MinimalAgentEngine.RootCause c = MinimalAgentEngine.diagnoseRootCause(
                vr, Set.of("n1"), Set.of("n3"), false);
        assertThat(c).isInstanceOf(MinimalAgentEngine.RootCause.DeviceFailure.class);
    }

    @Test
    void diagnoseRootCause_noOverlap_planOrAnswerError() {
        MinimalAgentEngine.VerifyResult vr =
                new MinimalAgentEngine.VerifyResult(false, Set.of("n1", "n2"), "wrong");
        MinimalAgentEngine.RootCause c = MinimalAgentEngine.diagnoseRootCause(
                vr, Set.of("n3"), Set.of(), false);
        assertThat(c).isInstanceOf(MinimalAgentEngine.RootCause.PlanOrAnswerError.class);
    }

    // ========================================================================
    // dispatch tests (original)
    // ========================================================================

    @Test
    void dispatch_deviceFailure_acceptPartial() {
        MinimalAgentEngine.RootCause c =
                new MinimalAgentEngine.RootCause.DeviceFailure(Set.of("t1"));
        MinimalAgentEngine.ReplanAction a = MinimalAgentEngine.dispatch(c, "fb", Set.of("t1"));
        assertThat(a).isInstanceOf(MinimalAgentEngine.ReplanAction.AcceptPartial.class);
    }

    @Test
    void dispatch_perceptionUnreliable_acceptPartial() {
        MinimalAgentEngine.RootCause c =
                new MinimalAgentEngine.RootCause.PerceptionUnreliable(true);
        MinimalAgentEngine.ReplanAction a = MinimalAgentEngine.dispatch(c, "fb", Set.of());
        assertThat(a).isInstanceOf(MinimalAgentEngine.ReplanAction.AcceptPartial.class);
    }

    @Test
    void dispatch_planOrAnswerError_fewNodes_localReplan() {
        MinimalAgentEngine.RootCause c =
                new MinimalAgentEngine.RootCause.PlanOrAnswerError(Set.of("n1", "n2"));
        MinimalAgentEngine.ReplanAction a = MinimalAgentEngine.dispatch(c, "fix", Set.of("n1", "n2"));
        assertThat(a).isInstanceOf(MinimalAgentEngine.ReplanAction.LocalReplan.class);
    }

    @Test
    void dispatch_planOrAnswerError_manyNodes_globalReplan() {
        MinimalAgentEngine.RootCause c =
                new MinimalAgentEngine.RootCause.PlanOrAnswerError(Set.of("n1", "n2", "n3"));
        MinimalAgentEngine.ReplanAction a = MinimalAgentEngine.dispatch(c, "wrong", Set.of("n1", "n2", "n3"));
        assertThat(a).isInstanceOf(MinimalAgentEngine.ReplanAction.GlobalReplan.class);
    }

    // ========================================================================
    // extractBalanced tests (original)
    // ========================================================================

    @Test
    void extractBalanced_simple() {
        String r = MinimalAgentEngine.extractBalanced("x {\"k\":\"v\"} y", '{', '}');
        assertThat(r).isEqualTo("\"k\":\"v\"");
    }

    @Test
    void extractBalanced_notFound() {
        assertThat(MinimalAgentEngine.extractBalanced("no braces", '{', '}')).isNull();
    }

    // ========================================================================
    // Mutation-RED tests (original)
    // ========================================================================

    @Test
    void mutationRED_withoutExecFailed_misdiagnosesAsPlanError() {
        MinimalAgentEngine.VerifyResult vr =
                new MinimalAgentEngine.VerifyResult(false, Set.of("fetch"),
                        "Rule-based: nodes failed execution: [fetch]");

        MinimalAgentEngine.RootCause ca = MinimalAgentEngine
                .diagnoseRootCause(vr, Set.of("fetch"), Set.of(), false);
        assertThat(ca).isInstanceOf(MinimalAgentEngine.RootCause.DeviceFailure.class);

        MinimalAgentEngine.RootCause cb = MinimalAgentEngine
                .diagnoseRootCause(vr, Set.of(), Set.of(), false);
        assertThat(cb).isInstanceOf(MinimalAgentEngine.RootCause.PlanOrAnswerError.class);

        MinimalAgentEngine.ReplanAction aa = MinimalAgentEngine.dispatch(ca, "fb", Set.of("fetch"));
        MinimalAgentEngine.ReplanAction ab = MinimalAgentEngine.dispatch(cb, "fb", Set.of("fetch"));
        assertThat(aa).isInstanceOf(MinimalAgentEngine.ReplanAction.AcceptPartial.class);
        assertThat(ab).isInstanceOf(MinimalAgentEngine.ReplanAction.LocalReplan.class);
    }

    @Test
    void mutationRED_withoutCorrectionHint_failsAfterMaxRetries() {
        AtomicInteger seq = new AtomicInteger(0);
        List<String> events = new ArrayList<>();

        MinimalAgentEngine.LlmProvider llm = prompt -> {
            int s = seq.getAndIncrement();
            if (s == 0) return """
                [{"id":"calc","type":"LLM_CALL",
                  "description":"What is 3+4?","inputs":{}}]""";
            if (s == 1) return "The answer is 8";
            if (s == 2) return """
                {"passed":false,"failedNodes":["calc"],
                 "feedback":"3+4=7 not 8"}""";
            if (s == 3) return "The answer is 8";
            if (s == 4) return """
                {"passed":false,"failedNodes":["calc"],
                 "feedback":"Still wrong"}""";
            if (s == 5) return "The answer is 8";
            if (s == 6) return """
                {"passed":false,"failedNodes":["calc"],
                 "feedback":"Persistent error"}""";
            return "{}";
        };

        MinimalAgentEngine.ToolExecutor tools = (n, a) -> {
            throw new MinimalAgentEngine.ToolException("no tools");
        };

        MinimalAgentEngine.EventListener listener = (t, d) -> events.add(t + ":" + d);
        MinimalAgentEngine engine = new MinimalAgentEngine(llm, tools, listener, 2);
        String result = engine.execute("What is 3+4?");

        assertThat(result).contains("TASK_FAILED");
        assertThat(events).anyMatch(e -> e.startsWith("TASK_FAILED") && e.contains("Max retries"));
    }

    @Test
    void planParsingFailure_returnsTaskFailed() {
        MinimalAgentEngine.LlmProvider llm = p -> "garbage, no JSON";
        MinimalAgentEngine.ToolExecutor tools = (n, a) -> {
            throw new MinimalAgentEngine.ToolException("no tools");
        };
        List<String> events = new ArrayList<>();
        MinimalAgentEngine.EventListener listener = (t, d) -> events.add(t);

        MinimalAgentEngine engine = new MinimalAgentEngine(llm, tools, listener, 3);
        String result = engine.execute("Do something");
        assertThat(result).contains("TASK_FAILED");
        assertThat(events).contains("TASK_FAILED");
    }

    // ========================================================================
    // NEW: P4 Bug #2 fix tests -- isTimeoutLike
    // ========================================================================

    @Test
    void isTimeoutLike_directTimeout() {
        RuntimeException e = new RuntimeException("Connection timeout");
        assertThat(MinimalAgentEngine.isTimeoutLike(e)).isTrue();
    }

    @Test
    void isTimeoutLike_readTimedOut() {
        RuntimeException e = new RuntimeException("Read timed out");
        assertThat(MinimalAgentEngine.isTimeoutLike(e)).isTrue();
    }

    @Test
    void isTimeoutLike_connectionTimedOut() {
        RuntimeException e = new RuntimeException("Connection timed out");
        assertThat(MinimalAgentEngine.isTimeoutLike(e)).isTrue();
    }

    @Test
    void isTimeoutLike_timedOutHyphenated() {
        RuntimeException e = new RuntimeException("connect timed-out");
        assertThat(MinimalAgentEngine.isTimeoutLike(e)).isTrue();
    }

    @Test
    void isTimeoutLike_caseInsensitive() {
        RuntimeException e = new RuntimeException("TIMEOUT occurred");
        assertThat(MinimalAgentEngine.isTimeoutLike(e)).isTrue();
    }

    @Test
    void isTimeoutLike_nestedCause() {
        RuntimeException cause = new RuntimeException("Read timed out");
        RuntimeException e = new RuntimeException("Execution failed", cause);
        assertThat(MinimalAgentEngine.isTimeoutLike(e)).isTrue();
    }

    @Test
    void isTimeoutLike_deeplyNestedCause() {
        RuntimeException root = new RuntimeException("connection timeout");
        RuntimeException mid = new RuntimeException("wrapper", root);
        RuntimeException e = new RuntimeException("outer", mid);
        assertThat(MinimalAgentEngine.isTimeoutLike(e)).isTrue();
    }

    @Test
    void isTimeoutLike_notTimeout() {
        RuntimeException e = new RuntimeException("NullPointerException");
        assertThat(MinimalAgentEngine.isTimeoutLike(e)).isFalse();
    }

    @Test
    void isTimeoutLike_notTimeoutNested() {
        RuntimeException cause = new RuntimeException("Something else");
        RuntimeException e = new RuntimeException("Failed", cause);
        assertThat(MinimalAgentEngine.isTimeoutLike(e)).isFalse();
    }

    // ========================================================================
    // NEW: P4 Bug #3 fix test -- extractInputsMap with numeric/boolean values
    // ========================================================================

    @Test
    void extractInputsMap_parsesNumericValues() {
        AtomicInteger seq = new AtomicInteger(0);
        MinimalAgentEngine.LlmProvider llm = p -> {
            int s = seq.getAndIncrement();
            if (s == 0) return """
                [{"id":"calc","type":"TOOL_CALL",
                  "description":"compute","inputs":{"count":5,"ratio":3.14}}]""";
            if (s == 1) return """
                {"passed":true,"failedNodes":[],
                 "feedback":"numeric inputs resolved correctly"}""";
            return "{}";
        };
        MinimalAgentEngine.ToolExecutor tools = (n, a) -> {
            assertThat(a).containsEntry("count", "5");
            assertThat(a).containsEntry("ratio", "3.14");
            return "done";
        };
        MinimalAgentEngine engine = new MinimalAgentEngine(llm, tools, null, 3);
        String result = engine.execute("Compute something");
        assertThat(result).contains("done");
    }

    @Test
    void extractInputsMap_parsesBooleanValues() {
        AtomicInteger seq = new AtomicInteger(0);
        MinimalAgentEngine.LlmProvider llm = p -> {
            int s = seq.getAndIncrement();
            if (s == 0) return """
                [{"id":"check","type":"TOOL_CALL",
                  "description":"verify","inputs":{"enabled":true,"cached":false}}]""";
            if (s == 1) return """
                {"passed":true,"failedNodes":[],
                 "feedback":"boolean inputs resolved correctly"}""";
            return "{}";
        };
        MinimalAgentEngine.ToolExecutor tools = (n, a) -> {
            assertThat(a).containsEntry("enabled", "true");
            assertThat(a).containsEntry("cached", "false");
            return "verified";
        };
        MinimalAgentEngine engine = new MinimalAgentEngine(llm, tools, null, 3);
        String result = engine.execute("Check flags");
        assertThat(result).contains("verified");
    }

    // ========================================================================
    // NEW: P4 Bug #6 fix test -- extractBalanced with string context
    // ========================================================================

    @Test
    void extractBalanced_ignoresBracesInsideStrings() {
        String json = """
            {"text": "price: ${AAPL}", "nested": {"key": "value"}}""";
        String r = MinimalAgentEngine.extractBalanced(json, '{', '}');
        assertThat(r).isNotNull();
        assertThat(r).contains("\"text\"");
        assertThat(r).contains("\"nested\"");
    }

    @Test
    void extractBalanced_bracketsInsideStrings() {
        String json = """
            {"regex": "[a-z]+", "name": "test"}""";
        String r = MinimalAgentEngine.extractBalanced(json, '{', '}');
        assertThat(r).isNotNull();
        assertThat(r).contains("\"regex\"");
        assertThat(r).contains("\"name\"");
    }

    @Test
    void extractBalanced_escapedQuotesInsideStrings() {
        String json = """
            {"feedback": "he said \\\"hello\\\"", "ok": true}""";
        String r = MinimalAgentEngine.extractBalanced(json, '{', '}');
        assertThat(r).isNotNull();
        assertThat(r).contains("he said");
        assertThat(r).contains("\\\"hello\\\"");
    }

    // ========================================================================
    // NEW: P4 Bug #7 fix test -- extractStringField with escaped quotes
    // ========================================================================

    @Test
    void extractStringField_handlesEscapedQuotes() {
        AtomicInteger seq = new AtomicInteger(0);
        MinimalAgentEngine.LlmProvider llm = p -> {
            int s = seq.getAndIncrement();
            if (s == 0) return """
                [{"id":"n1","type":"TOOL_CALL",
                  "description":"test","inputs":{"msg":"he said \\\"hello\\\" world"}}]""";
            if (s == 1) return """
                {"passed":true,"failedNodes":[],
                 "feedback":"escaped quotes handled"}""";
            return "{}";
        };
        MinimalAgentEngine.ToolExecutor tools = (n, a) -> {
            assertThat(a).containsEntry("msg", "he said \"hello\" world");
            return "ok";
        };
        MinimalAgentEngine engine = new MinimalAgentEngine(llm, tools, null, 3);
        String result = engine.execute("Test escaped quotes");
        assertThat(result).contains("ok");
    }

    // ========================================================================
    // NEW: P4 Bug #9 fix test -- dispatch empty failedNodes
    // ========================================================================

    @Test
    void dispatch_emptyFailedNodes_passedFalse_globalReplan() {
        MinimalAgentEngine.RootCause c =
                new MinimalAgentEngine.RootCause.PlanOrAnswerError(Set.of());
        MinimalAgentEngine.ReplanAction a = MinimalAgentEngine.dispatch(
                c, "something is wrong but cannot name nodes", Set.of());
        assertThat(a).isInstanceOf(MinimalAgentEngine.ReplanAction.GlobalReplan.class);
    }

    // ========================================================================
    // NEW: P4 Bug #8 fix test -- GlobalReplan empty plan -> AcceptPartial
    // ========================================================================

    @Test
    void globalReplan_emptyPlan_acceptPartial() {
        AtomicInteger seq = new AtomicInteger(0);
        List<String> events = new ArrayList<>();

        MinimalAgentEngine.LlmProvider llm = prompt -> {
            int s = seq.getAndIncrement();
            if (s == 0) return """
                [{"id":"n1","type":"LLM_CALL",
                  "description":"do work","inputs":{}}]""";
            if (s == 1) return "some output";
            if (s == 2) return """
                {"passed":false,"failedNodes":["n1","n2","n3"],
                 "feedback":"plan is fundamentally broken"}""";
            if (s == 3) return "Sorry, I cannot help with that.";
            return "{}";
        };

        MinimalAgentEngine.ToolExecutor tools = (n, a) ->
            { throw new MinimalAgentEngine.ToolException("no tools"); };

        MinimalAgentEngine.EventListener listener = (t, d) -> events.add(t + ":" + d);
        MinimalAgentEngine engine = new MinimalAgentEngine(llm, tools, listener, 3);
        String result = engine.execute("Do complex work");

        assertThat(result).contains("DEGRADED");
        assertThat(result).contains("Replan failed");
        assertThat(events).noneMatch(e -> e.startsWith("TASK_FAILED"));
        assertThat(events).anyMatch(e -> e.startsWith("TASK_COMPLETED") && e.contains("degraded"));
    }

    // ========================================================================
    // NEW: parsePlanNodes handles markdown fences
    // ========================================================================

    @Test
    void parsePlanNodes_stripsMarkdownFence() {
        AtomicInteger seq = new AtomicInteger(0);
        MinimalAgentEngine.LlmProvider llm = p -> {
            int s = seq.getAndIncrement();
            if (s == 0) return """
                ```json
                [{"id":"n1","type":"LLM_CALL","description":"test","inputs":{}}]
                ```""";
            if (s == 1) return """
                {"passed":true,"failedNodes":[],
                 "feedback":"ok"}""";
            return "{}";
        };
        MinimalAgentEngine.ToolExecutor tools = (n, a) -> "ok";
        MinimalAgentEngine engine = new MinimalAgentEngine(llm, tools, null, 3);
        String result = engine.execute("Test markdown plan");
        assertThat(result).contains("ok");
        assertThat(result).doesNotContain("TASK_FAILED");
    }

    @Test
    void parsePlanNodes_stripsMarkdownFenceNoLang() {
        AtomicInteger seq = new AtomicInteger(0);
        MinimalAgentEngine.LlmProvider llm = p -> {
            int s = seq.getAndIncrement();
            if (s == 0) return """
                ```
                [{"id":"n1","type":"LLM_CALL","description":"test","inputs":{}}]
                ```""";
            if (s == 1) return """
                {"passed":true,"failedNodes":[],
                 "feedback":"ok"}""";
            return "{}";
        };
        MinimalAgentEngine.ToolExecutor tools = (n, a) -> "ok";
        MinimalAgentEngine engine = new MinimalAgentEngine(llm, tools, null, 3);
        String result = engine.execute("Test markdown plan no lang");
        assertThat(result).contains("ok");
        assertThat(result).doesNotContain("TASK_FAILED");
    }

    // ========================================================================
    // NEW: non-JSON verify response -> PerceptionUnreliable (P4 Bug fix)
    // ========================================================================

    @Test
    void nonJsonVerifyResponse_perceptionUnreliable() {
        AtomicInteger seq = new AtomicInteger(0);
        List<String> events = new ArrayList<>();

        MinimalAgentEngine.LlmProvider llm = prompt -> {
            int s = seq.getAndIncrement();
            if (s == 0) return """
                [{"id":"n1","type":"LLM_CALL",
                  "description":"generate report","inputs":{}}]""";
            if (s == 1) return "Report: all systems operational";
            if (s == 2) return "I think the result is good, but I'm not sure...";
            return "{}";
        };

        MinimalAgentEngine.ToolExecutor tools = (n, a) ->
            { throw new MinimalAgentEngine.ToolException("no tools"); };

        MinimalAgentEngine.EventListener listener = (t, d) -> events.add(t + ":" + d);
        MinimalAgentEngine engine = new MinimalAgentEngine(llm, tools, listener, 3);
        String result = engine.execute("Generate report");

        assertThat(result).contains("DEGRADED");
        assertThat(result).contains("Perception unreliable");
        assertThat(events).anyMatch(e -> e.startsWith("ROOT_CAUSE_DIAGNOSED")
                && e.contains("PerceptionUnreliable"));
        assertThat(events).noneMatch(e -> e.startsWith("REPLAN_"));
    }

    // ========================================================================
    // NEW: P4 Bug #4 fix -- diagnoseRootCause cross-validation with results
    // ========================================================================

    @Test
    void diagnoseRootCause_resultsCrossValidation_deviceFailure() {
        MinimalAgentEngine.VerifyResult vr =
                new MinimalAgentEngine.VerifyResult(false, Set.of("fetch"),
                        "Rule-based: nodes failed execution: [fetch]");
        Map<String, String> results = Map.of("fetch", "FAILED: connection refused");

        MinimalAgentEngine.RootCause c1 = MinimalAgentEngine.diagnoseRootCause(
                vr, Set.of(), Set.of(), false);
        assertThat(c1).isInstanceOf(MinimalAgentEngine.RootCause.PlanOrAnswerError.class);

        MinimalAgentEngine.RootCause c2 = MinimalAgentEngine.diagnoseRootCause(
                vr, Set.of(), Set.of(), false, results);
        assertThat(c2).isInstanceOf(MinimalAgentEngine.RootCause.DeviceFailure.class);
    }

    @Test
    void diagnoseRootCause_resultsCrossValidation_noFalsePositive() {
        MinimalAgentEngine.VerifyResult vr =
                new MinimalAgentEngine.VerifyResult(false, Set.of("answer"), "wrong answer");
        Map<String, String> results = Map.of("answer", "The answer is 6 (wrong)");

        MinimalAgentEngine.RootCause c = MinimalAgentEngine.diagnoseRootCause(
                vr, Set.of(), Set.of(), false, results);
        assertThat(c).isInstanceOf(MinimalAgentEngine.RootCause.PlanOrAnswerError.class);
    }

    // ========================================================================
    // NEW: VerifyResult parseFailure flag
    // ========================================================================

    @Test
    void verifyResult_parseFailureFlag_defaultFalse() {
        MinimalAgentEngine.VerifyResult vr =
                new MinimalAgentEngine.VerifyResult(false, Set.of("n1"), "feedback");
        assertThat(vr.parseFailure()).isFalse();
    }

    @Test
    void verifyResult_parseFailureFlag_explicitTrue() {
        MinimalAgentEngine.VerifyResult vr =
                new MinimalAgentEngine.VerifyResult(false, Set.of(), "bad json", true);
        assertThat(vr.parseFailure()).isTrue();
    }

    // ========================================================================
    // NEW: transient timeout -> execSkipped not execFailed
    // ========================================================================

    @Test
    void transientTimeout_goesToExecSkipped_notExecFailed() {
        AtomicInteger seq = new AtomicInteger(0);
        List<String> events = new ArrayList<>();

        MinimalAgentEngine.LlmProvider llm = prompt -> {
            int s = seq.getAndIncrement();
            if (s == 0) return """
                [{"id":"n1","type":"LLM_CALL",
                  "description":"fetch data","inputs":{}}]""";
            if (s == 1) throw new RuntimeException("Read timed out");
            return "{}";
        };

        MinimalAgentEngine.ToolExecutor tools = (n, a) ->
            { throw new MinimalAgentEngine.ToolException("no tools"); };

        MinimalAgentEngine.EventListener listener = (t, d) -> events.add(t + ":" + d);
        MinimalAgentEngine engine = new MinimalAgentEngine(llm, tools, listener, 3);
        String result = engine.execute("Fetch data");

        // Timeout -> execSkipped (not execFailed). Rule-based verify catches
        // FAILED: prefix -> DeviceFailure diagnosis -> AcceptPartial.
        assertThat(events).anyMatch(e -> e.startsWith("ROOT_CAUSE_DIAGNOSED")
                && e.contains("DeviceFailure"));
        assertThat(result).contains("DEGRADED");
    }

    // ========================================================================
    // NEW: nested braces
    // ========================================================================

    @Test
    void extractBalanced_nestedBraces() {
        String json = """
            {"outer": {"inner": "value"}, "key": "val"}""";
        String r = MinimalAgentEngine.extractBalanced(json, '{', '}');
        assertThat(r).isNotNull();
        assertThat(r).contains("\"outer\"");
        assertThat(r).contains("\"inner\"");
        assertThat(r).contains("\"key\"");
    }
}
