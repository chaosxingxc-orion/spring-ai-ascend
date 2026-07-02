package com.openjiuwen.reference.gepa3;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * P8 Minimal Kernel -- <800 lines of pure Java, zero frameworks.
 *
 * Replaces AlphaStrategy (720 lines) + DefaultPregelExecutor (581 lines) +
 * DefaultVerifier + DefaultPlanner with a single class.
 *
 * Design invariants:
 * - No Spring, no Reactor, no openjiuwen-core -- java.base only.
 * - Sealed types (RootCause 3 variants, ReplanAction 3 variants) enforce
 *   exhaustiveness at compile time.
 * - diagnoseRootCause() is a pure function (zero-LLM).
 * - dispatch() uses switch expression over sealed types.
 * - Single blocking LlmProvider.think() replaces ChatModel/Model/stream/Reactor.
 */
public class MinimalAgentEngine {

    // ========================================================================
    // Sealed types -- 6 variants, compiler-enforced exhaustiveness
    // ========================================================================

    public sealed interface RootCause
            permits RootCause.DeviceFailure, RootCause.PlanOrAnswerError,
                   RootCause.PerceptionUnreliable {

        record DeviceFailure(Set<String> nodes) implements RootCause {
            public DeviceFailure { nodes = Set.copyOf(nodes); }
        }

        record PlanOrAnswerError(Set<String> nodes) implements RootCause {
            public PlanOrAnswerError { nodes = Set.copyOf(nodes); }
        }

        record PerceptionUnreliable(boolean verifierThrew) implements RootCause {}
    }

    public sealed interface ReplanAction
            permits ReplanAction.LocalReplan, ReplanAction.GlobalReplan,
                   ReplanAction.AcceptPartial {

        record LocalReplan(Set<String> failedNodes, String feedback)
                implements ReplanAction {}

        record GlobalReplan(String feedback) implements ReplanAction {}

        record AcceptPartial(String reason) implements ReplanAction {}
    }

    // ========================================================================
    // Data records
    // ========================================================================

    public record PlanNode(String id, String type, String description,
                           Map<String, String> inputs) {
        public PlanNode {
            inputs = Map.copyOf(inputs);
        }
    }

    /**
     * @param parseFailure true when the verifier LLM returned non-JSON text
     *                     (cannot be parsed) -- signals PerceptionUnreliable
     */
    public record VerifyResult(boolean passed, Set<String> failedNodes,
                                String feedback, boolean parseFailure) {
        public VerifyResult {
            failedNodes = Set.copyOf(failedNodes);
        }
        /** Backward-compatible constructor: parseFailure defaults to false. */
        public VerifyResult(boolean passed, Set<String> failedNodes,
                            String feedback) {
            this(passed, failedNodes, feedback, false);
        }
    }

    // ========================================================================
    // SPI interfaces
    // ========================================================================

    @FunctionalInterface
    public interface LlmProvider {
        String think(String prompt);
    }

    @FunctionalInterface
    public interface ToolExecutor {
        String execute(String toolName, Map<String, String> args);
    }

    @FunctionalInterface
    public interface EventListener {
        void onEvent(String type, Map<String, Object> data);
    }

    // ========================================================================
    // Engine state
    // ========================================================================

    private final LlmProvider llm;
    private final ToolExecutor tools;
    private final EventListener events;
    private final int maxRetries;

    public MinimalAgentEngine(LlmProvider llm, ToolExecutor tools,
                              EventListener events, int maxRetries) {
        this.llm = Objects.requireNonNull(llm, "llm");
        this.tools = Objects.requireNonNull(tools, "tools");
        this.events = events;
        this.maxRetries = maxRetries > 0 ? maxRetries : 3;
    }

    // ========================================================================
    // execute() -- single entry point
    // ========================================================================

    public String execute(String goal) {
        emit("TASK_STARTED", Map.of("goal", goal));

        List<PlanNode> plan = plan(goal);
        if (plan.isEmpty()) {
            emit("TASK_FAILED", Map.of("reason", "Planning produced empty plan"));
            return "TASK_FAILED: plan empty";
        }
        emit("PLAN_GENERATED", Map.of("nodeCount", plan.size(),
                "nodes", plan.stream().map(PlanNode::id).toList()));

        Map<String, String> results = new LinkedHashMap<>();
        Set<String> execFailed = new HashSet<>();
        Set<String> execSkipped = new HashSet<>();
        return executeVerifyLoop(goal, plan, results, execFailed, execSkipped, 0);
    }

    // ========================================================================
    // Phase 1: Plan
    // ========================================================================

    private List<PlanNode> plan(String goal) {
        String prompt = buildPlanPrompt(goal);
        String response = llm.think(prompt);
        return parsePlanNodes(response);
    }

    // ========================================================================
    // Phase 2-3: Execute + Verify recursive loop
    // ========================================================================

    /** First call: execute all nodes, then verify. Retries only verify. */
    private String executeVerifyLoop(String goal, List<PlanNode> plan,
                                      Map<String, String> results,
                                      Set<String> execFailed,
                                      Set<String> execSkipped,
                                      int retryCount) {
        return executeVerifyLoop(goal, plan, results, execFailed, execSkipped,
                retryCount, false);
    }

    private String executeVerifyLoop(String goal, List<PlanNode> plan,
                                      Map<String, String> results,
                                      Set<String> execFailed,
                                      Set<String> execSkipped,
                                      int retryCount,
                                      boolean skipExecution) {
        if (!skipExecution) {
            emit("EXECUTION_STARTED", Map.of("superstep", retryCount));

            for (PlanNode node : topologicalSort(plan)) {
                try {
                    String result = executeNode(node, results);
                    results.put(node.id(), result);
                    emit("NODE_COMPLETED", Map.of("nodeId", node.id(),
                            "type", node.type()));
                } catch (RuntimeException e) {
                    String msg = e.getMessage() != null ? e.getMessage() : "null";
                    if (isTimeoutLike(e)) {
                        results.put(node.id(), "FAILED: " + msg);
                        execSkipped.add(node.id());
                    } else {
                        results.put(node.id(), "FAILED: " + msg);
                        execFailed.add(node.id());
                    }
                    emit("NODE_FAILED", Map.of("nodeId", node.id(), "error", msg));
                }
            }
            emit("EXECUTION_COMPLETED", Map.of("results", results.size(),
                    "failed", execFailed.size(), "skipped", execSkipped.size()));
        }

        // Verify
        emit("VERIFY_STARTED", Map.of());
        boolean verifyThrew = false;
        VerifyResult vr;
        try {
            vr = verify(goal, plan, results);
        } catch (Exception e) {
            verifyThrew = true;
            vr = new VerifyResult(false, Set.of(),
                    "Verifier threw: " + e.getMessage());
        }

        if (vr == null) {
            verifyThrew = true;
            vr = new VerifyResult(false, Set.of(), "Verifier returned null");
        }

        // P4 Bug fix: non-JSON verify response -> PerceptionUnreliable
        if (!verifyThrew && vr.parseFailure()) {
            verifyThrew = true;
        }

        if (!verifyThrew && vr.passed()) {
            emit("VERIFY_PASSED", Map.of("feedback", vr.feedback()));
            emit("TASK_COMPLETED", Map.of("verified", true, "degraded", false));
            return assembleOutput(results);
        }

        emit("VERIFY_FAILED", Map.of("failedNodes", vr.failedNodes(),
                "feedback", vr.feedback()));

        // P4 Bug fix: pass results for cross-validation
        RootCause cause = diagnoseRootCause(vr, execFailed, execSkipped,
                verifyThrew, results);
        emit("ROOT_CAUSE_DIAGNOSED", Map.of("cause",
                cause.getClass().getSimpleName(), "details", cause.toString()));

        ReplanAction action = dispatch(cause, vr.feedback(), vr.failedNodes());

        if (retryCount >= maxRetries
                && !(action instanceof ReplanAction.AcceptPartial)) {
            emit("TASK_FAILED", Map.of("reason",
                    "Max retries (" + maxRetries + ") exceeded"));
            return "TASK_FAILED: max retries exceeded";
        }

        return executeReplanAction(goal, plan, results, execFailed,
                execSkipped, retryCount, action);
    }

    private String executeReplanAction(String goal, List<PlanNode> plan,
                                        Map<String, String> results,
                                        Set<String> execFailed,
                                        Set<String> execSkipped,
                                        int retryCount,
                                        ReplanAction action) {
        return switch (action) {
            case ReplanAction.LocalReplan lr -> {
                emit("REPLAN_LOCAL", Map.of("failedNodes", lr.failedNodes(),
                        "feedback", lr.feedback()));
                for (String nodeId : lr.failedNodes()) {
                    PlanNode node = findNode(plan, nodeId);
                    if (node == null) continue;
                    try {
                        String desc = "LLM_CALL".equals(node.type())
                                ? node.description() + "\n<correction>"
                                  + lr.feedback() + "</correction>"
                                : node.description();
                        PlanNode corrected = new PlanNode(node.id(),
                                node.type(), desc, node.inputs());
                        String result = executeNode(corrected, results);
                        results.put(node.id(), result);
                        execFailed.remove(node.id());
                        emit("NODE_COMPLETED", Map.of("nodeId", node.id(),
                                "retry", true));
                    } catch (Exception e) {
                        results.put(node.id(), "FAILED: "
                                + (e.getMessage() != null ? e.getMessage()
                                   : e.getClass().getSimpleName()));
                    }
                }
                yield executeVerifyLoop(goal, plan, results, execFailed,
                        execSkipped, retryCount + 1, true);
            }
            case ReplanAction.GlobalReplan gr -> {
                emit("REPLAN_GLOBAL", Map.of("feedback", gr.feedback()));
                String aug = goal + "\n<previous_feedback>"
                        + gr.feedback() + "</previous_feedback>";
                List<PlanNode> newPlan = plan(aug);
                // P4 Bug fix: empty replan -> AcceptPartial, don't waste retries
                if (newPlan.isEmpty()) {
                    emit("TASK_COMPLETED", Map.of("verified", false,
                            "degraded", true,
                            "reason", "Global replan returned empty plan"));
                    yield assembleOutput(results)
                            + "\n[DEGRADED: Replan failed -- "
                            + gr.feedback() + "]";
                }
                Map<String, String> nr = new LinkedHashMap<>();
                Set<String> nf = new HashSet<>();
                Set<String> ns = new HashSet<>();
                yield executeVerifyLoop(goal, newPlan, nr, nf, ns,
                        retryCount + 1, false);
            }
            case ReplanAction.AcceptPartial ap -> {
                emit("TASK_COMPLETED", Map.of("verified", false,
                        "degraded", true, "reason", ap.reason()));
                yield assembleOutput(results)
                        + "\n[DEGRADED: " + ap.reason() + "]";
            }
        };
    }

    // ========================================================================
    // executeNode()
    // ========================================================================

    private String executeNode(PlanNode node, Map<String, String> results) {
        Map<String, String> resolved = resolveInputs(node.inputs(), results);
        return switch (node.type()) {
            case "LLM_CALL" -> {
                String prompt = buildNodePrompt(node.description(), resolved);
                yield llm.think(prompt);
            }
            case "TOOL_CALL" -> {
                String toolName = node.description();
                yield tools.execute(toolName, resolved);
            }
            default -> throw new IllegalArgumentException(
                    "Unknown node type: " + node.type());
        };
    }

    // ========================================================================
    // verify() -- rule-based pre-check + LLM-as-judge
    // ========================================================================

    private VerifyResult verify(String goal, List<PlanNode> plan,
                                 Map<String, String> results) {
        Set<String> ruleFailed = new LinkedHashSet<>();
        for (var entry : results.entrySet()) {
            if (entry.getValue().startsWith("FAILED:")) {
                ruleFailed.add(entry.getKey());
            }
        }
        if (!ruleFailed.isEmpty()) {
            return new VerifyResult(false, ruleFailed,
                    "Rule-based: nodes failed execution: " + ruleFailed);
        }
        String prompt = buildVerifyPrompt(goal, plan, results);
        String response = llm.think(prompt);
        return parseVerification(response);
    }

    // ========================================================================
    // diagnoseRootCause() -- pure function, zero-LLM; cross-validates side-channel
    // ========================================================================

    /** Backward-compatible overload (no results cross-validation). */
    public static RootCause diagnoseRootCause(VerifyResult vr,
            Set<String> execFailed, Set<String> execSkipped,
            boolean verifyThrew) {
        return diagnoseRootCause(vr, execFailed, execSkipped, verifyThrew, null);
    }

    /**
     * Priority: verifier-threw > device-failure > plan/answer-error.
     *
     * P4 Bug #4 fix: cross-validates execFailed side-channel against
     * structural "FAILED:" evidence in results map. If execFailed is lost
     * but results still show "FAILED:", diagnosis correctly identifies
     * DeviceFailure.
     */
    public static RootCause diagnoseRootCause(VerifyResult vr,
            Set<String> execFailed, Set<String> execSkipped,
            boolean verifyThrew, Map<String, String> results) {
        if (verifyThrew) return new RootCause.PerceptionUnreliable(true);
        Set<String> device = new HashSet<>();
        if (execFailed != null) device.addAll(execFailed);
        if (execSkipped != null) device.addAll(execSkipped);
        Set<String> hit = new HashSet<>(vr.failedNodes());
        hit.retainAll(device);
        if (!hit.isEmpty()) return new RootCause.DeviceFailure(hit);

        // Cross-validation (P4 Bug #4 fix): check results for structural
        // "FAILED:" evidence even if execFailed side-channel was lost.
        if (results != null) {
            Set<String> structuralDevice = new LinkedHashSet<>();
            for (String nodeId : vr.failedNodes()) {
                String val = results.get(nodeId);
                if (val != null && val.startsWith("FAILED:")) {
                    structuralDevice.add(nodeId);
                }
            }
            if (!structuralDevice.isEmpty()) {
                return new RootCause.DeviceFailure(structuralDevice);
            }
        }
        return new RootCause.PlanOrAnswerError(vr.failedNodes());
    }

    // ========================================================================
    // dispatch() -- sealed switch, compiler-red on miss
    // ========================================================================

    public static ReplanAction dispatch(RootCause cause, String feedback,
                                         Set<String> failedNodes) {
        return switch (cause) {
            case RootCause.DeviceFailure d ->
                    new ReplanAction.AcceptPartial(
                        "Device failure: tool/infra error -- "
                        + "replan cannot fix broken tools");
            case RootCause.PerceptionUnreliable p ->
                    new ReplanAction.AcceptPartial(
                        "Perception unreliable: verifier threw/returned null"
                        + " -- cannot trust its FAILED judgement");
            case RootCause.PlanOrAnswerError pe -> {
                // P4 Bug #9 fix: empty failedNodes with passed=false
                // -> GlobalReplan rather than no-op LocalReplan
                if (failedNodes.isEmpty()) {
                    yield new ReplanAction.GlobalReplan(feedback);
                }
                if (failedNodes.size() <= 2) {
                    yield new ReplanAction.LocalReplan(failedNodes, feedback);
                } else {
                    yield new ReplanAction.GlobalReplan(feedback);
                }
            }
        };
    }

    // ========================================================================
    // Prompt builders
    // ========================================================================

    private String buildPlanPrompt(String goal) {
        return """
            You are a precise task planner. Output a JSON array of nodes.
            Each node: {"id":"...","type":"LLM_CALL"|"TOOL_CALL",
            "description":"...","inputs":{"key":"value or ${upstreamId}"}}

            Goal: %s

            Output ONLY the JSON array. No markdown, no explanation.
            """.formatted(goal);
    }

    private String buildNodePrompt(String description,
                                    Map<String, String> inputs) {
        StringBuilder sb = new StringBuilder(description);
        if (!inputs.isEmpty()) {
            sb.append("\n\nInput data:\n");
            for (var e : inputs.entrySet()) {
                sb.append("  ").append(e.getKey()).append(": ")
                        .append(e.getValue()).append("\n");
            }
        }
        return sb.toString();
    }

    private String buildVerifyPrompt(String goal, List<PlanNode> plan,
                                      Map<String, String> results) {
        StringBuilder sb = new StringBuilder();
        sb.append("Verify if results satisfy the goal.\n\n");
        sb.append("Goal: ").append(goal).append("\n\nResults:\n");
        for (var e : results.entrySet()) {
            sb.append("  ").append(e.getKey()).append(": ")
                    .append(truncate(e.getValue(), 200)).append("\n");
        }
        sb.append("\nRespond with JSON only: {\"passed\":true/false,");
        sb.append("\"failedNodes\":[\"id\"],\"feedback\":\"reason\"}");
        return sb.toString();
    }

    // ========================================================================
    // JSON parsing -- pure JDK (java.util.regex)
    // ========================================================================

    /** P4 Bug fix: strips markdown fences before parsing. */
    private List<PlanNode> parsePlanNodes(String response) {
        if (response == null || response.isBlank()) return List.of();
        // Strip markdown fences (```json ... ``` or ``` ... ```)
        String cleaned = response.strip();
        if (cleaned.startsWith("```")) {
            int nl = cleaned.indexOf('\n');
            if (nl > 0) cleaned = cleaned.substring(nl + 1);
        }
        if (cleaned.endsWith("```")) {
            cleaned = cleaned.substring(0, cleaned.lastIndexOf("```")).strip();
        }
        String inner = extractBalanced(cleaned, '[', ']');
        if (inner == null) return List.of();
        String array = "[" + inner + "]";

        List<PlanNode> nodes = new ArrayList<>();
        int pos = 0;
        while (pos < array.length()) {
            int start = array.indexOf('{', pos);
            if (start < 0) break;
            String obj = extractBalanced(array.substring(start), '{', '}');
            if (obj == null) break;
            PlanNode n = parseNodeObject("{" + obj + "}");
            if (n != null) nodes.add(n);
            pos = start + obj.length() + 2;
        }
        return nodes;
    }

    private PlanNode parseNodeObject(String obj) {
        String id = extractStringField(obj, "id");
        String type = extractStringField(obj, "type");
        String desc = extractStringField(obj, "description");
        if (id == null || type == null) return null;
        Map<String, String> inputs = extractInputsMap(obj);
        return new PlanNode(id, type, desc != null ? desc : "", inputs);
    }

    /** P4 Bug fix: returns parseFailure=true when LLM response is non-JSON. */
    private VerifyResult parseVerification(String response) {
        if (response == null || response.isBlank()) {
            return new VerifyResult(false, Set.of(), "Empty verify response",
                    true);
        }
        String obj = extractBalanced(response, '{', '}');
        if (obj == null) {
            return new VerifyResult(false, Set.of(),
                    "Bad verify JSON: " + truncate(response, 100), true);
        }
        String json = "{" + obj + "}";

        String strPassed = extractStringField(json, "passed");
        boolean passed;
        if (strPassed != null) {
            passed = "true".equals(strPassed);
        } else {
            String boolVal = extractBoolField(json, "passed");
            passed = "true".equals(boolVal);
        }
        String feedback = extractStringField(json, "feedback");
        Set<String> failed = extractStringArrayField(json, "failedNodes");
        return new VerifyResult(passed, failed != null ? failed : Set.of(),
                feedback != null ? feedback : "no feedback", false);
    }

    /** P4 Bug #7 fix: handles escaped quotes in JSON string values. */
    private static String extractStringField(String json, String fieldName) {
        Matcher m = Pattern.compile(
                "\"" + Pattern.quote(fieldName) + "\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"")
                .matcher(json);
        if (!m.find()) return null;
        return m.group(1).replace("\\\"", "\"").replace("\\\\", "\\");
    }

    private static String extractBoolField(String json, String fieldName) {
        Matcher m = Pattern.compile(
                "\"" + Pattern.quote(fieldName) + "\"\\s*:\\s*(true|false)")
                .matcher(json);
        return m.find() ? m.group(1) : null;
    }

    /** P4 Bug #7 fix: handles escaped quotes in array element values. */
    private static Set<String> extractStringArrayField(String json,
                                                        String fieldName) {
        Matcher m = Pattern.compile(
                "\"" + Pattern.quote(fieldName) + "\"\\s*:\\s*\\[([^\\]]*)\\]")
                .matcher(json);
        if (!m.find()) return Set.of();
        String content = m.group(1).trim();
        if (content.isEmpty()) return Set.of();
        Set<String> result = new LinkedHashSet<>();
        Matcher im = Pattern.compile("\"((?:[^\"\\\\]|\\\\.)*)\"").matcher(content);
        while (im.find()) result.add(im.group(1)
                .replace("\\\"", "\"").replace("\\\\", "\\"));
        return result;
    }

    /** P4 Bug #3 fix: also captures numeric and boolean JSON values. */
    private static Map<String, String> extractInputsMap(String nodeObj) {
        int idx = nodeObj.indexOf("\"inputs\"");
        if (idx < 0) return Map.of();
        int colon = nodeObj.indexOf(':', idx);
        if (colon < 0) return Map.of();
        int brace = nodeObj.indexOf('{', colon);
        if (brace < 0) return Map.of();
        String inner = extractBalanced(nodeObj.substring(brace), '{', '}');
        if (inner == null) return Map.of();
        String inputsJson = "{" + inner + "}";

        Map<String, String> result = new LinkedHashMap<>();
        // String values (with escaped-quote support)
        Matcher ms = Pattern.compile(
                "\"([^\"]+)\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"")
                .matcher(inputsJson);
        while (ms.find()) {
            result.put(ms.group(1),
                    ms.group(2).replace("\\\"", "\"").replace("\\\\", "\\"));
        }
        // Numeric values (integer or decimal)
        Matcher mn = Pattern.compile(
                "\"([^\"]+)\"\\s*:\\s*(-?\\d+(?:\\.\\d+)?)")
                .matcher(inputsJson);
        while (mn.find()) {
            result.putIfAbsent(mn.group(1), mn.group(2));
        }
        // Boolean values
        Matcher mb = Pattern.compile(
                "\"([^\"]+)\"\\s*:\\s*(true|false)")
                .matcher(inputsJson);
        while (mb.find()) {
            result.putIfAbsent(mb.group(1), mb.group(2));
        }
        return result;
    }

    // ========================================================================
    // extractBalanced() -- non-recursive brace/bracket matching
    // ========================================================================

    /**
     * P4 Bug #6 fix: tracks string context to avoid misparse on braces/brackets
     * inside JSON string literals (e.g., "price: ${AAPL}").
     */
    public static String extractBalanced(String s, char open, char close) {
        int start = s.indexOf(open);
        if (start < 0) return null;
        int depth = 0;
        boolean inString = false;
        for (int i = start; i < s.length(); i++) {
            char c = s.charAt(i);
            if (inString) {
                if (c == '\\' && i + 1 < s.length()) {
                    i++; // skip escaped char
                } else if (c == '"') {
                    inString = false;
                }
                continue;
            }
            if (c == '"') { inString = true; continue; }
            if (c == open) depth++;
            else if (c == close) {
                depth--;
                if (depth == 0) return s.substring(start + 1, i);
            }
        }
        return null;
    }

    // ========================================================================
    // Topological sort (Kahn's algorithm)
    // ========================================================================

    /** Detects duplicate node IDs (throws) and handles cycles gracefully. */
    private List<PlanNode> topologicalSort(List<PlanNode> nodes) {
        Map<String, PlanNode> nodeMap = new LinkedHashMap<>();
        Map<String, List<String>> dependents = new LinkedHashMap<>();
        Map<String, Integer> inDegree = new LinkedHashMap<>();

        for (PlanNode n : nodes) {
            if (nodeMap.containsKey(n.id())) {
                throw new IllegalArgumentException(
                        "Duplicate node ID in plan: " + n.id());
            }
            nodeMap.put(n.id(), n);
            dependents.put(n.id(), new ArrayList<>());
            inDegree.put(n.id(), 0);
        }

        for (PlanNode n : nodes) {
            for (String val : n.inputs().values()) {
                String ref = extractNodeRef(val);
                if (ref != null && nodeMap.containsKey(ref)) {
                    dependents.get(ref).add(n.id());
                    inDegree.merge(n.id(), 1, Integer::sum);
                }
            }
        }

        Queue<String> queue = new ArrayDeque<>();
        for (var e : inDegree.entrySet()) {
            if (e.getValue() == 0) queue.add(e.getKey());
        }

        List<PlanNode> sorted = new ArrayList<>();
        while (!queue.isEmpty()) {
            String id = queue.poll();
            sorted.add(nodeMap.get(id));
            for (String dep : dependents.get(id)) {
                int d = inDegree.merge(dep, -1, Integer::sum);
                if (d == 0) queue.add(dep);
            }
        }

        // Cyclic dependency: append unsorted nodes at end (best-effort)
        for (PlanNode n : nodes) {
            if (!sorted.contains(n)) sorted.add(n);
        }
        return sorted;
    }

    // ========================================================================
    // Utilities
    // ========================================================================

    private Map<String, String> resolveInputs(Map<String, String> inputs,
                                               Map<String, String> results) {
        Map<String, String> out = new LinkedHashMap<>();
        for (var e : inputs.entrySet()) {
            String val = e.getValue();
            if (val.startsWith("${") && val.endsWith("}")) {
                String ref = val.substring(2, val.length() - 1);
                // Known limitation: field access (${node.field}) resolves to
                // full node output, not the specific field.
                int dot = ref.indexOf('.');
                if (dot > 0) ref = ref.substring(0, dot);
                out.put(e.getKey(), results.getOrDefault(ref, val));
            } else {
                out.put(e.getKey(), val);
            }
        }
        return out;
    }

    private static String extractNodeRef(String input) {
        if (input.startsWith("${") && input.endsWith("}")) {
            String ref = input.substring(2, input.length() - 1);
            int dot = ref.indexOf('.');
            return dot > 0 ? ref.substring(0, dot) : ref;
        }
        return null;
    }

    private static PlanNode findNode(List<PlanNode> plan, String nodeId) {
        return plan.stream().filter(n -> n.id().equals(nodeId))
                .findFirst().orElse(null);
    }

    private String assembleOutput(Map<String, String> results) {
        StringBuilder sb = new StringBuilder();
        for (var e : results.entrySet()) {
            if (!e.getValue().startsWith("FAILED:")) {
                if (!sb.isEmpty()) sb.append("\n");
                sb.append(e.getKey()).append(": ").append(e.getValue());
            }
        }
        return sb.toString();
    }

    private void emit(String type, Map<String, Object> data) {
        if (events != null) {
            try { events.onEvent(type, data); }
            catch (Exception ignored) { /* events are not control-flow */ }
        }
    }

    private static String truncate(String s, int max) {
        if (s == null) return "null";
        return s.length() <= max ? s : s.substring(0, max) + "...";
    }

    /**
     * P4 Bug #2 fix: detects "timed out" patterns in addition to "timeout".
     * Checks full recursive cause chain, not just immediate cause.
     */
    static boolean isTimeoutLike(RuntimeException e) {
        String msg = e.getMessage();
        if (msg != null && isTimeoutMessage(msg)) return true;
        Throwable c = e.getCause();
        while (c != null) {
            String cm = c.getMessage();
            if (cm != null && isTimeoutMessage(cm)) return true;
            c = c.getCause();
        }
        return false;
    }

    /** Matches: "timeout", "timed out", "timed-out", case-insensitive. */
    private static boolean isTimeoutMessage(String msg) {
        String lower = msg.toLowerCase(Locale.ROOT);
        return lower.contains("timeout") || lower.contains("timed out")
                || lower.contains("timed-out");
    }

    // ========================================================================
    // ToolException
    // ========================================================================

    public static class ToolException extends RuntimeException {
        public ToolException(String message) { super(message); }
    }

    // ========================================================================
    // HONEST LIMITATIONS -- what this approach can NEVER handle
    // ========================================================================
    //
    // 1. FIELD-LEVEL DATAFLOW: ${node.field} resolves to full node output.
    //    The engine has no JSON-path parser and cannot extract sub-fields.
    //    A schema-aware or runtime-type-inspecting system is needed.
    //
    // 2. TRULY ADVERSARIAL LLMs: If the LLM actively subverts (e.g., produces
    //    "FAILED:" as legitimate data, or crafts responses to trick regex
    //    parsers), the engine has no cryptographic or structural defense.
    //    The parser is regex-based, not a verified grammar.
    //
    // 3. CONCURRENT NODE EXECUTION: Topological sort produces a partial order
    //    that could be parallelized, but the engine is strictly sequential.
    //    Adding parallelism requires thread-safety for the shared results map.
    //
    // 4. VERIFIER AS SINGLE POINT OF TRUST FAILURE: If the verifier
    //    hallucinates (passed=true when answer is wrong), there is no
    //    cross-validation. A second independent verifier or consistency check
    //    would be needed for higher assurance.
    //
    // 5. DEEPLY NESTED JSON: The regex parser handles flat objects with
    //    string/number/boolean values. Nested objects or arrays as values
    //    are silently dropped or misparsed. A streaming parser (e.g.,
    //    javax.json-style) would be needed for arbitrary JSON depth.
    //
    // 6. STATE PERSISTENCE/RESUME: No checkpointing. A JVM crash loses all
    //    progress. Resumability requires serializing the plan+results+retry
    //    state to durable storage.
    //
    // 7. MULTI-AGENT COORDINATION: Single engine, single goal at a time.
    //    No fan-out/fan-in of sub-goals, no inter-agent communication.
    //
    // 8. STREAMING/PARTIAL RESULTS: The engine blocks on each LLM call.
    //    No incremental progress reporting or early termination mid-plan.
    //
    // 9. CORRUPT STATE RECOVERY: If the results map accumulates contradictory
    //    or poisoned data across retries, there is no rollback or isolation
    //    mechanism. Each retry mutates the same results map in place.
    //
    // 10. PROGRESSIVE RETRY STRATEGIES: Retries use the same strategy each
    //     time. There is no escalation (e.g., LocalReplan -> GlobalReplan ->
    //     AcceptPartial on repeated failures of the same type).
    // ========================================================================
}
