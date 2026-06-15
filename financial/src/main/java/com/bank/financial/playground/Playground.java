package com.bank.financial.playground;

import com.bank.financial.kit.AbstractFinancialAgentHandler;
import com.openjiuwen.core.session.Session;
import com.openjiuwen.core.singleagent.agents.ReActAgent;
import com.openjiuwen.core.singleagent.rail.AgentRail;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Local playground — the friendly dev loop for Java-defined (and YAML-defined)
 * financial agents. Resolves an agent via {@link PlaygroundCatalog}, attaches a
 * readable {@link TraceRail}, and runs a CLI chat. With {@code --mock} it uses a
 * keyless {@link MockModel} so you can exercise compliance / tools / approval
 * wiring with no API key.
 *
 * <pre>
 *   ./financial/play.sh financial-advisor-agent --mock   # the Java agent
 *   ./financial/play.sh credit-card-advisor              # a YAML agent
 * </pre>
 */
public final class Playground {

    public static void main(String[] args) throws Exception {
        quietFrameworkLogs();

        boolean mock = false;
        boolean demo = false;
        String ref = null;
        for (String a : args) {
            if (a.equals("--mock")) {
                mock = true;
            } else if (a.equals("--demo")) {
                demo = true;
                mock = true; // a demo needs the scripted mock model
            } else if (!a.startsWith("--")) {
                ref = a;
            }
        }
        if (ref == null) {
            System.out.println("用法: play.sh <agent-id 或 yaml 路径> [--mock]");
            System.out.println("可用 agent: " + PlaygroundCatalog.available());
            return;
        }

        AbstractFinancialAgentHandler handler;
        try {
            handler = PlaygroundCatalog.resolve(ref);
        } catch (Exception e) {
            System.out.println("✗ " + e.getMessage());
            return;
        }

        ReActAgent agent = handler.newLocalAgent();
        agent.registerRail(new TraceRail());
        String[] script = demo ? PlaygroundCatalog.demoScript(handler.id()) : null;
        if (mock) {
            String reply = "（mock 模型回复）我已收到您的请求,这是用于本地联调的模拟回答;接真实模型后将给出真实结果。";
            agent.setLlm(script != null ? new MockModel(reply, script[0], script[1]) : new MockModel(reply));
        }
        if (demo && script == null) {
            System.out.println("（该 agent 无被门控的工具,demo 无暂停可演示)");
        }

        System.out.println("▶ playground  agent=" + handler.id() + (mock ? "  [mock LLM]" : "  [real LLM]"));
        if (handler.playgroundHint() != null) {
            System.out.println(handler.playgroundHint());
        }
        System.out.println("  输入消息回车发送;Ctrl-D 退出。\n");

        Session session = new MapSession(handler.id() + "-play");
        try (BufferedReader br = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8))) {
            String input;
            while ((input = br.readLine()) != null) {
                if (input.isBlank()) {
                    continue;
                }
                List<AgentRail> turnRails = handler.localRails(input, "playground-tenant");
                for (AgentRail r : turnRails) {
                    agent.registerRail(r);
                }
                try {
                    Object result = agent.invoke(Map.of("query", input), session);
                    if (result instanceof Map<?, ?> m && "interrupt".equals(m.get("result_type"))) {
                        System.out.println("  ↩ 输入「批准 / approve」放行,或「拒绝」终止");
                    }
                } catch (Exception e) {
                    System.out.println("✗ 执行异常: " + e.getMessage());
                } finally {
                    for (AgentRail r : turnRails) {
                        agent.unregisterRail(r);
                    }
                    System.out.println();
                }
            }
        }
        System.out.println("再见。");
    }

    /**
     * Silence the framework's log chatter so only the readable trace (printed via
     * System.out by {@link TraceRail}) shows. The framework uses a custom logger
     * that ignores logback level changes, so play.sh also greps timestamped lines.
     */
    private static void quietFrameworkLogs() {
        try {
            for (String name : new String[] {org.slf4j.Logger.ROOT_LOGGER_NAME,
                    "com.openjiuwen", "agent", "common", "trajectory"}) {
                ch.qos.logback.classic.Logger lg = (ch.qos.logback.classic.Logger)
                        org.slf4j.LoggerFactory.getLogger(name);
                lg.detachAndStopAllAppenders();
                lg.setLevel(ch.qos.logback.classic.Level.OFF);
                lg.setAdditive(false);
            }
            // Keep the domain audit trail visible in the playground (no timestamp →
            // survives play.sh's log filter), so you can SEE the 🧾 events locally too.
            ch.qos.logback.classic.LoggerContext lc =
                    (ch.qos.logback.classic.LoggerContext) org.slf4j.LoggerFactory.getILoggerFactory();
            ch.qos.logback.classic.encoder.PatternLayoutEncoder enc =
                    new ch.qos.logback.classic.encoder.PatternLayoutEncoder();
            enc.setContext(lc);
            enc.setPattern("%msg%n");
            enc.start();
            ch.qos.logback.core.ConsoleAppender<ch.qos.logback.classic.spi.ILoggingEvent> ap =
                    new ch.qos.logback.core.ConsoleAppender<>();
            ap.setContext(lc);
            ap.setEncoder(enc);
            ap.start();
            ch.qos.logback.classic.Logger audit = (ch.qos.logback.classic.Logger)
                    org.slf4j.LoggerFactory.getLogger("financial.audit");
            audit.detachAndStopAllAppenders();
            audit.addAppender(ap);
            audit.setLevel(ch.qos.logback.classic.Level.INFO);
            audit.setAdditive(false);
        } catch (Throwable ignored) {
            // not logback (or unavailable) — the play.sh grep filter is the backstop
        }
    }

    /** Minimal Map-backed Session for embedded runs (3 abstract methods). */
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
