package com.bank.financial.research.model;

import com.bank.financial.kit.AbstractFinancialAgentHandler;
import com.bank.financial.kit.ModelConnection;
import com.openjiuwen.core.session.Session;
import com.openjiuwen.core.singleagent.agents.ReActAgent;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Production {@link ReportModel}: backs each sub-agent with a real LLM through the
 * kit's {@link AbstractFinancialAgentHandler} / {@code ReActAgent} machinery and
 * the standard {@code ModelConnection} (BANK_LLM_* env). It reuses the proven
 * invoke path ({@code agent.invoke({"query": …})} → {@code {"output": …}}), so
 * model wiring, rails, and tracing behave exactly like the rest of the workspace.
 *
 * <p>One lightweight handler is built per role (cached), each carrying that role's
 * system prompt. This path is not exercised by the offline test suite (which uses
 * {@link ScriptedReportModel}); it is the seam a bank flips on by setting a real
 * model endpoint.
 */
public final class OpenJiuwenReportModel implements ReportModel {

    private final ModelConnection model;
    private final int maxTokens;
    private final double temperature;
    private final Map<String, ReActAgent> agents = new ConcurrentHashMap<>();

    public OpenJiuwenReportModel(ModelConnection model) {
        this(model, 4096, 0.4);
    }

    public OpenJiuwenReportModel(ModelConnection model, int maxTokens, double temperature) {
        this.model = model;
        this.maxTokens = maxTokens;
        this.temperature = temperature;
    }

    @Override
    public String name() {
        return "openjiuwen:" + model.modelName();
    }

    @Override
    public String generate(ModelTask task) {
        ReActAgent agent = agents.computeIfAbsent(task.role(),
                r -> new RoleHandler(r, roleSystemPrompt(r), model, maxTokens, temperature).newLocalAgent());
        Object result = agent.invoke(
                Map.of("query", task.instruction() + "\n\n" + task.brief()),
                new MapSession("research-" + task.role()));
        if (result instanceof Map<?, ?> m) {
            Object out = m.get("output");
            if (out != null) {
                return out.toString();
            }
        }
        return result == null ? "" : result.toString();
    }

    private static String roleSystemPrompt(String role) {
        String base = "你是一名机构级卖方研究团队中的子智能体,严格遵循以下纪律:"
                + "(1) 数字只能引用简报中已给出的、经计算或经核验的事实,绝不自行编造或推算新数字;"
                + "(2) 论述以投资论点为脊柱,保持机构统一口径与中文专业风格;"
                + "(3) 给出平衡的正反两面,并标注关键风险。";
        return base + " 你的角色是:" + role + "。";
    }

    /** A minimal kit handler that just carries a role's prompt + model params. */
    private static final class RoleHandler extends AbstractFinancialAgentHandler {
        private final String role;
        private final String prompt;
        private final int maxTokens;
        private final double temperature;

        RoleHandler(String role, String prompt, ModelConnection model, int maxTokens, double temperature) {
            super("research-" + role, model);
            this.role = role;
            this.prompt = prompt;
            this.maxTokens = maxTokens;
            this.temperature = temperature;
        }

        @Override
        protected String description() {
            return "research sub-agent: " + role;
        }

        @Override
        protected String systemPrompt() {
            return prompt;
        }

        @Override
        protected int maxIterations() {
            return 2; // single-shot prose, no tool loop
        }

        @Override
        protected int maxTokens() {
            return maxTokens;
        }

        @Override
        protected double temperature() {
            return temperature;
        }
    }

    /** Map-backed session for embedded single-shot generation. */
    private static final class MapSession implements Session {
        private final String id;
        private final Map<String, Object> state = new LinkedHashMap<>();

        private MapSession(String id) {
            this.id = id;
        }

        @Override
        public String getSessionId() {
            return id;
        }

        @Override
        public Object getState(String key) {
            return state.get(key);
        }

        @Override
        public void updateState(Map<String, Object> s) {
            this.state.putAll(s);
        }
    }
}
