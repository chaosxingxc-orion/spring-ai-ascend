package com.huawei.ascend.examples.openjiuwen.workflow;

import com.openjiuwen.core.common.constants.Constant;
import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;
import com.openjiuwen.core.foundation.llm.schema.SystemMessage;
import com.openjiuwen.core.foundation.llm.schema.UserMessage;
import com.openjiuwen.core.foundation.tool.Tool;
import com.openjiuwen.core.session.WorkflowSessionApi;
import com.openjiuwen.core.session.interaction.InteractiveInput;
import com.openjiuwen.core.session.interaction.InteractionOutput;
import com.openjiuwen.core.session.stream.OutputSchema;
import com.openjiuwen.core.workflow.Workflow;
import com.openjiuwen.core.workflow.WorkflowCard;
import com.openjiuwen.core.workflow.WorkflowExecutionState;
import com.openjiuwen.core.workflow.WorkflowOutput;
import com.openjiuwen.core.workflow.component.Start;
import com.openjiuwen.core.workflow.component.End;
import com.openjiuwen.core.workflow.component.llm.LLMCompConfig;
import com.openjiuwen.core.workflow.component.llm.LLMComponent;
import com.openjiuwen.core.workflow.component.llm.QuestionerComponent;
import com.openjiuwen.core.workflow.component.llm.QuestionerConfig;
import com.openjiuwen.core.workflow.component.tool.ToolComponent;
import com.openjiuwen.core.workflow.component.tool.ToolComponentConfig;

import com.huawei.ascend.examples.openjiuwen.workflow.tools.MockSearchTool;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;

/**
 * Standalone demo of OpenJiuwen multi-step DAG workflow with human-in-the-loop.
 *
 * <p>Scenario: Article Summarizer — a 5-step linear workflow:
 * <pre>
 *   [Start] → [LLM: analyze] → [Tool: search] → [LLM: summarize] → [Questioner: confirm] → [LLM: finalize] → [End]
 * </pre>
 *
 * <p>Key features demonstrated:
 * <ul>
 *   <li>Multi-step DAG built programmatically (no YAML)</li>
 *   <li>Tool invocation as a workflow node</li>
 *   <li>QuestionerComponent for human-in-the-loop suspend/resume</li>
 *   <li>Step-by-step progress via invoke loop</li>
 * </ul>
 */
public final class ArticleSummarizerWorkflow {

    private static final String SAMPLE_ARTICLE =
            "人工智能在医疗领域的应用正在快速发展。近日，某研究团队开发了一款基于深度学习的"
                    + "医学影像诊断系统，据称在肺部CT影像分析中的准确率达到了96.8%，超过了资深放射科医生的平均水平。"
                    + "该系统使用了超过100万张标注影像进行训练，能够在3秒内完成一次完整的肺部CT扫描分析。"
                    + "研究团队表示，该系统目前已在三家医院进入临床试验阶段。";

    private ArticleSummarizerWorkflow() {}

    public static void main(String[] args) throws Exception {
        Properties props = loadConfig();

        String modelProvider = props.getProperty("model.provider", "openai");
        String apiKey = props.getProperty("model.api-key", "sk-your-api-key-here");
        String apiBase = props.getProperty("model.api-base", "http://localhost:4000/v1");
        String modelName = props.getProperty("model.name", "gpt-4");
        double temperature = Double.parseDouble(props.getProperty("model.temperature", "0.7"));
        int maxTokens = Integer.parseInt(props.getProperty("model.max-tokens", "1024"));
        boolean verifySsl = Boolean.parseBoolean(props.getProperty("model.ssl-verify", "true"));

        System.out.println("=== OpenJiuwen Workflow 独立示例 ===");
        System.out.println("场景: 智能文章摘要 — 多步骤 DAG + 人工确认中断/恢复");
        System.out.println("模型: " + modelProvider + " / " + modelName);
        System.out.println("API:  " + apiBase);
        System.out.println();

        // ── 1. Build the workflow DAG ─────────────────────────────────

        Workflow workflow = buildWorkflow(modelProvider, apiKey, apiBase, modelName,
                temperature, maxTokens, verifySsl);

        // ── 2. Prepare input ──────────────────────────────────────────

        System.out.println("输入文章:");
        System.out.println(SAMPLE_ARTICLE);
        System.out.println();

        Map<String, Object> inputs = Map.of("query", SAMPLE_ARTICLE);
        String sessionId = UUID.randomUUID().toString();

        // ── 3. Execute with interrupt/resume loop ─────────────────────

        Object nextInputs = inputs;
        WorkflowSessionApi session = new WorkflowSessionApi(null, sessionId, Map.of());

        while (true) {
            System.out.println("── 执行工作流... ──");
            WorkflowOutput output = workflow.invoke(nextInputs, session, null);

            if (output.getState() == WorkflowExecutionState.INPUT_REQUIRED) {
                System.out.println("── 工作流挂起，等待人工输入 ──");
                InteractiveInput resumeInputs = handleInterrupt(output);
                nextInputs = resumeInputs;
                continue;
            }

            if (output.getState() == WorkflowExecutionState.COMPLETED) {
                System.out.println("── 工作流完成 ──");
                displayResult(output);
                break;
            }

            if (output.getState() == WorkflowExecutionState.ERROR) {
                System.err.println("工作流执行出错: " + output.getResult());
                System.exit(1);
            }
        }
    }

    // ── Workflow construction ────────────────────────────────────────

    static Workflow buildWorkflow(String provider, String apiKey, String apiBase,
                                   String modelName, double temperature, int maxTokens,
                                   boolean verifySsl) {
        ModelClientConfig clientConfig = ModelClientConfig.builder()
                .clientProvider(provider)
                .apiKey(apiKey)
                .apiBase(apiBase)
                .verifySsl(verifySsl)
                .build();

        ModelRequestConfig requestConfig = ModelRequestConfig.builder()
                .modelName(modelName)
                .temperature(temperature)
                .maxTokens(maxTokens)
                .build();

        Map<String, Object> textResponseFormat = Map.of("type", "text");
        Map<String, Object> textOutputConfig = Map.of("text", Map.of("type", "string"));

        // -- LLM 1: analyze article topic --
        LLMCompConfig analyzeConfig = new LLMCompConfig();
        analyzeConfig.setModelClientConfig(clientConfig);
        analyzeConfig.setModelConfig(requestConfig);
        analyzeConfig.setSystemPromptTemplate(new SystemMessage(
                "你是一个专业的内容分析师。请用简洁的语言总结以下文章的主题和关键信息点。"));
        analyzeConfig.setUserPromptTemplate(new UserMessage("{{article}}"));
        analyzeConfig.setResponseFormat(textResponseFormat);
        analyzeConfig.setOutputConfig(textOutputConfig);

        // -- LLM 2: generate summary (receives analysis + search results) --
        LLMCompConfig summarizeConfig = new LLMCompConfig();
        summarizeConfig.setModelClientConfig(clientConfig);
        summarizeConfig.setModelConfig(requestConfig);
        summarizeConfig.setSystemPromptTemplate(new SystemMessage(
                "你是一个专业的摘要撰写者。请根据文章分析和搜索结果，生成一段200字以内的中文摘要。"));
        summarizeConfig.setUserPromptTemplate(new UserMessage(
                "文章分析: {{analysis}}\n\n搜索结果: {{search_results}}"));
        summarizeConfig.setResponseFormat(textResponseFormat);
        summarizeConfig.setOutputConfig(textOutputConfig);

        // -- LLM 3: finalize based on human confirmation --
        LLMCompConfig finalizeConfig = new LLMCompConfig();
        finalizeConfig.setModelClientConfig(clientConfig);
        finalizeConfig.setModelConfig(requestConfig);
        finalizeConfig.setSystemPromptTemplate(new SystemMessage(
                "你是一个专业的编辑。根据人工审核反馈，生成最终输出。"
                        + "如果审批通过，输出最终摘要。如果被驳回，回复驳回说明。"));
        finalizeConfig.setUserPromptTemplate(new UserMessage(
                "摘要: {{summary}}\n\n人工审核反馈: {{confirmation}}"));
        finalizeConfig.setResponseFormat(textResponseFormat);
        finalizeConfig.setOutputConfig(textOutputConfig);

        // -- Tool: mock search --
        ToolComponentConfig toolCfg = new ToolComponentConfig();
        Tool searchTool = MockSearchTool.create();
        ToolComponent toolComp = new ToolComponent(toolCfg);
        toolComp.bindTool(searchTool);

        // -- Questioner: human confirmation --
        QuestionerConfig qConfig = new QuestionerConfig();
        qConfig.setModelClientConfig(clientConfig);
        qConfig.setModelConfig(requestConfig);
        qConfig.setResponseType("reply_directly");
        qConfig.setExtractFieldsFromResponse(false);
        qConfig.setQuestionContent(
                "请审核以上摘要质量。输入 'yes' 批准，'no' 驳回并附原因。");

        // -- Build DAG --
        WorkflowCard card = WorkflowCard.builder()
                .id("article-summarizer")
                .name("Article Summarizer with Human Review")
                .version("1.0")
                .description("Multi-step workflow: analyze → search → summarize → human confirm → finalize")
                .build();

        Workflow wf = new Workflow(card);

        // Nodes
        wf.setStartComp("start", new Start(),
                Map.of("query", "${query}"), null);

        wf.addWorkflowComp("analyze", new LLMComponent(analyzeConfig),
                Map.of("article", "${start.query}"), null);

        wf.addWorkflowComp("search", toolComp,
                Map.of("query", "${analyze.text}"), null);

        wf.addWorkflowComp("summarize", new LLMComponent(summarizeConfig),
                Map.of("analysis", "${analyze.text}",
                       "search_results", "${search.data}"), null);

        wf.addWorkflowComp("confirm", new QuestionerComponent(qConfig),
                Map.of("summary", "${summarize.text}"), null);

        wf.addWorkflowComp("finalize", new LLMComponent(finalizeConfig),
                Map.of("summary", "${summarize.text}",
                       "confirmation", "${confirm.user_response}"), null);

        wf.setEndComp("end", new End(),
                Map.of("result", "${finalize.text}"), null);

        // Edges (linear chain)
        wf.addConnection("start", "analyze");
        wf.addConnection("analyze", "search");
        wf.addConnection("search", "summarize");
        wf.addConnection("summarize", "confirm");
        wf.addConnection("confirm", "finalize");
        wf.addConnection("finalize", "end");

        return wf;
    }

    // ── Interrupt handling ───────────────────────────────────────────

    static InteractiveInput handleInterrupt(WorkflowOutput output) throws Exception {
        InteractiveInput resumeInputs = new InteractiveInput();
        Object result = output.getResult();

        if (result instanceof List<?> chunks) {
            for (Object chunk : chunks) {
                if (chunk instanceof OutputSchema schema
                        && ("interaction".equals(schema.getType())
                            || Constant.INTERACTION.equals(schema.getType()))) {
                    if (schema.getPayload() instanceof InteractionOutput interaction) {
                        String nodeId = interaction.getId();
                        String prompt = interaction.getValue() != null
                                ? interaction.getValue().toString() : "";

                        System.out.println();
                        System.out.println("╔══════════════════════════════════════════╗");
                        System.out.println("║  人工确认节点: " + nodeId);
                        System.out.println("║  " + prompt);
                        System.out.println("╚══════════════════════════════════════════╝");
                        System.out.print(">>> 请输入回复: ");
                        System.out.flush();

                        String userResponse;
                        java.io.Console console = System.console();
                        if (console != null) {
                            userResponse = console.readLine();
                        } else {
                            // Fallback for exec:java where System.console() may be null
                            BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
                            userResponse = reader.readLine();
                        }

                        if (userResponse == null || userResponse.isBlank()) {
                            userResponse = "yes"; // default approve
                            System.out.println("(默认: yes)");
                        }

                        resumeInputs.update(nodeId, Map.of("answer", userResponse));
                        System.out.println();
                    }
                }
            }
        }
        return resumeInputs;
    }

    // ── Result display ───────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    static void displayResult(WorkflowOutput output) {
        System.out.println();
        System.out.println("═══════════════════════════════════════════");
        System.out.println("  最终输出");
        System.out.println("═══════════════════════════════════════════");

        Object result = output.getResult();
        String text = extractResultText(result);
        if (text != null) {
            System.out.println(text);
        } else {
            System.out.println(result);
        }
        System.out.println("═══════════════════════════════════════════");
        System.out.println();
        System.out.println("工作流执行完毕，程序退出。");

        // OpenJiuwen's TaskExecutorPool uses non-daemon threads; explicit exit required.
        System.exit(0);
    }

    @SuppressWarnings("unchecked")
    static String extractResultText(Object result) {
        // Case 1: End node wraps in Map {output: {result: "..."}}
        if (result instanceof Map<?, ?> m) {
            Object output = m.get("output");
            if (output instanceof Map<?, ?> outputMap) {
                Object text = outputMap.values().stream().findFirst().orElse(null);
                if (text instanceof String s) return s;
            }
        }
        // Case 2: List of OutputSchema chunks
        if (result instanceof List<?> chunks) {
            for (Object chunk : chunks) {
                if (chunk instanceof OutputSchema schema) {
                    Object payload = schema.getPayload();
                    if (payload instanceof Map<?, ?> pm) {
                        // End wraps as {output: {key: "value"}}
                        Object output = pm.get("output");
                        if (output instanceof Map<?, ?> outputMap) {
                            Object text = outputMap.values().stream().findFirst().orElse(null);
                            if (text instanceof String s) return s;
                        }
                    }
                    if (payload instanceof String s) return s;
                }
            }
        }
        return null;
    }

    // ── Config loading ───────────────────────────────────────────────

    static Properties loadConfig() throws Exception {
        Properties props = new Properties();
        // Try classpath resource first
        try (InputStream is = ArticleSummarizerWorkflow.class.getClassLoader()
                .getResourceAsStream("application.properties")) {
            if (is != null) {
                props.load(is);
            }
        }
        // Env vars override file values
        overrideFromEnv(props, "SAA_MODEL_PROVIDER", "model.provider");
        overrideFromEnv(props, "SAA_LLM_API_KEY", "model.api-key");
        overrideFromEnv(props, "SAA_LLM_API_BASE", "model.api-base");
        overrideFromEnv(props, "SAA_LLM_MODEL", "model.name");
        overrideFromEnv(props, "SAA_LLM_TEMPERATURE", "model.temperature");
        overrideFromEnv(props, "SAA_LLM_MAX_TOKENS", "model.max-tokens");
        overrideFromEnv(props, "SAA_SSL_VERIFY", "model.ssl-verify");
        return props;
    }

    static void overrideFromEnv(Properties props, String envKey, String propKey) {
        String val = System.getenv(envKey);
        if (val != null && !val.isBlank()) {
            props.setProperty(propKey, val);
        }
    }
}
