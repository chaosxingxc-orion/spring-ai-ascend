package com.bank.financial.research.web;

import com.bank.financial.research.data.FundDataSource;
import com.bank.financial.research.data.eastmoney.EastMoneyFundDataSource;
import com.bank.financial.research.data.stub.StubFundDataSource;
import com.bank.financial.research.ResearchReports;
import com.bank.financial.research.bond.BondReport;
import com.bank.financial.research.bond.BondReportEngine;
import com.bank.financial.research.data.stub.StubBondDataSource;
import com.bank.financial.research.data.stub.StubThematicDataSource;
import com.bank.financial.research.fund.FundReport;
import com.bank.financial.research.fund.FundReportEngine;
import com.bank.financial.research.engine.PipelineProgress;
import com.bank.financial.research.engine.ReportBudget;
import com.bank.financial.research.engine.ReportRequest;
import com.bank.financial.research.model.ReportModel;
import com.bank.financial.research.thematic.ThematicReport;
import com.bank.financial.research.thematic.ThematicReportEngine;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huawei.ascend.a2a.memory.experience.CollaborationSignature;
import com.huawei.ascend.a2a.memory.experience.ExperienceMemoryKit;
import com.huawei.ascend.a2a.memory.experience.ExperienceStore;
import com.huawei.ascend.a2a.memory.experience.InMemoryExperienceStore;
import com.huawei.ascend.a2a.memory.experience.Lesson;
import com.huawei.ascend.a2a.memory.obs.MemoryObserver;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;

/**
 * A self-contained, dependency-light web playground for the multi-agent research
 * engine. It runs the JDK's built-in {@link HttpServer} (no Spring): a single
 * config-and-preview page at {@code /}, and a Server-Sent-Events stream at
 * {@code /api/run} that drives one report run and emits per-agent progress as the
 * pipeline advances, then the rendered report.
 *
 * <p>The engine is wired in its offline "scripted model" shape so the demo is
 * deterministic and needs no live model. The data source is chosen per request:
 * Tushare (if {@code TUSHARE_TOKEN} is set), or the offline stub as a transparent
 * fallback for the not-yet-wired providers.
 *
 * <pre>
 *   mvn -pl financial exec:java -Dexec.mainClass=com.bank.financial.research.web.ResearchWebServer
 * </pre>
 */
public final class ResearchWebServer {

    private static final ObjectMapper JSON = new ObjectMapper();

    // Cross-run experience, persisted across web requests and isolated per report type
    // (so a fund run recalls fund lessons, not equity ones). This is the "long-term"
    // memory layer; the blackboard is the per-run "working" layer.
    private static final java.util.Map<String, ExperienceStore> WEB_EXP = new java.util.concurrent.ConcurrentHashMap<>();

    private static ExperienceStore webExp(String type) {
        return WEB_EXP.computeIfAbsent(type, k -> new InMemoryExperienceStore());
    }

    // Per-run pause flags (runId → paused). The /api/run worker waits between agents while
    // its flag is set; /api/control flips it. Cooperative pause: the in-flight agent finishes,
    // then the pipeline holds before the next agent until resumed.
    private static final java.util.Map<String, java.util.concurrent.atomic.AtomicBoolean> PAUSED =
            new java.util.concurrent.ConcurrentHashMap<>();

    // A report run holds its HTTP thread for its whole SSE lifetime (tens of seconds to
    // minutes on the live model). With a fixed server pool, enough concurrent /api/run
    // streams would starve every thread — so /, /api/control (pause/resume!) would hang
    // too. Bound concurrent runs and reject the overflow (503) instead of queueing it, so
    // the page and control plane always keep threads. Size via RESEARCH_WEB_MAX_RUNS.
    private static final int MAX_CONCURRENT_RUNS = parseIntEnv("RESEARCH_WEB_MAX_RUNS", 6);
    private static final RunSlots RUN_SLOTS = new RunSlots(MAX_CONCURRENT_RUNS);

    /** Admission control for concurrent /api/run streams: reject (don't queue) the overflow. */
    static final class RunSlots {
        private final java.util.concurrent.Semaphore permits;

        RunSlots(int max) {
            this.permits = new java.util.concurrent.Semaphore(Math.max(1, max));
        }

        boolean tryBegin() {
            return permits.tryAcquire();
        }

        void end() {
            permits.release();
        }

        int available() {
            return permits.availablePermits();
        }
    }

    private static final List<Map<String, String>> FUND_AGENTS = List.of(
            Map.of("role", "planner", "label", "规划"), Map.of("role", "data", "label", "数据"),
            Map.of("role", "performance", "label", "业绩"), Map.of("role", "risk", "label", "风险"),
            Map.of("role", "lead-manager", "label", "首席"), Map.of("role", "writer", "label", "撰写"),
            Map.of("role", "critic", "label", "评审"), Map.of("role", "compliance", "label", "合规"));

    private static final List<Map<String, String>> BOND_AGENTS = List.of(
            Map.of("role", "planner", "label", "规划"), Map.of("role", "data", "label", "数据"),
            Map.of("role", "rates", "label", "利率"), Map.of("role", "credit", "label", "信用"),
            Map.of("role", "lead-manager", "label", "首席"), Map.of("role", "writer", "label", "撰写"),
            Map.of("role", "critic", "label", "评审"), Map.of("role", "compliance", "label", "合规"));

    private static final List<Map<String, String>> MACRO_AGENTS = List.of(
            Map.of("role", "planner", "label", "规划"), Map.of("role", "data", "label", "指标录入"),
            Map.of("role", "analysis", "label", "量化打分"), Map.of("role", "lead-manager", "label", "策略首席"),
            Map.of("role", "writer", "label", "撰写"), Map.of("role", "critic", "label", "评审"),
            Map.of("role", "compliance", "label", "合规"));

    private static final List<Map<String, String>> THEMATIC_AGENTS = List.of(
            Map.of("role", "planner", "label", "规划"), Map.of("role", "data", "label", "宏观录入"),
            Map.of("role", "sector-impact", "label", "因子打分"), Map.of("role", "lead-manager", "label", "策略首席"),
            Map.of("role", "writer", "label", "撰写"), Map.of("role", "critic", "label", "评审"),
            Map.of("role", "compliance", "label", "合规"));

    private ResearchWebServer() {
    }

    public static void main(String[] args) throws IOException {
        com.bank.financial.research.LogQuieter.quiet();
        int port = parsePort(System.getenv("RESEARCH_WEB_PORT"), 8088);
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        // Pool must exceed MAX_CONCURRENT_RUNS so the page + control plane always have
        // threads even when every run slot is occupied.
        server.setExecutor(Executors.newFixedThreadPool(MAX_CONCURRENT_RUNS + 4));
        server.createContext("/", ResearchWebServer::handlePage);
        server.createContext("/api/run", ResearchWebServer::handleRun);
        server.createContext("/api/control", ResearchWebServer::handleControl);
        server.start();
        System.out.println("research web on http://localhost:" + port);
    }

    private static int parsePort(String raw, int fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static int parseIntEnv(String key, int fallback) {
        String v = System.getenv(key);
        if (v == null || v.isBlank()) {
            return fallback;
        }
        try {
            return Math.max(1, Integer.parseInt(v.trim()));
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    // ── GET / ─────────────────────────────────────────────────────────────────
    private static void handlePage(HttpExchange ex) throws IOException {
        try {
            if (!"GET".equalsIgnoreCase(ex.getRequestMethod())) {
                ex.sendResponseHeaders(405, -1);
                return;
            }
            byte[] body = PAGE.getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().set("Content-Type", "text/html;charset=utf-8");
            ex.sendResponseHeaders(200, body.length);
            try (OutputStream os = ex.getResponseBody()) {
                os.write(body);
            }
        } catch (IOException e) {
            // client gone — nothing to do
        } finally {
            ex.close();
        }
    }

    // ── GET /api/control?run=<id>&action=pause|resume ──────────────────────────
    private static void handleControl(HttpExchange ex) {
        try {
            Map<String, String> q = parseQuery(ex.getRequestURI().getRawQuery());
            String runId = q.get("run");
            String action = q.getOrDefault("action", "");
            java.util.concurrent.atomic.AtomicBoolean flag = runId == null ? null : PAUSED.get(runId);
            if (flag != null) {
                if ("pause".equals(action)) {
                    flag.set(true);
                } else if ("resume".equals(action)) {
                    flag.set(false);
                }
            }
            byte[] body = ("{\"ok\":true,\"paused\":" + (flag != null && flag.get()) + "}")
                    .getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().set("Content-Type", "application/json;charset=utf-8");
            ex.sendResponseHeaders(200, body.length);
            try (OutputStream o = ex.getResponseBody()) {
                o.write(body);
            }
        } catch (IOException e) {
            // client gone
        } finally {
            ex.close();
        }
    }

    // ── GET /api/run  → SSE ─────────────────────────────────────────────────────
    private static void handleRun(HttpExchange ex) {
        if (!RUN_SLOTS.tryBegin()) {
            rejectBusy(ex); // all run slots occupied — shed load, keep the control plane alive
            return;
        }
        OutputStream os = null;
        final java.util.concurrent.atomic.AtomicBoolean alive = new java.util.concurrent.atomic.AtomicBoolean(true);
        final Thread[] hb = new Thread[1];
        final java.util.concurrent.atomic.AtomicBoolean paused = new java.util.concurrent.atomic.AtomicBoolean(false);
        String runId = null;
        try {
            Map<String, String> q = parseQuery(ex.getRequestURI().getRawQuery());
            // New model: one SUBJECT (fund/bond/none) + multiple LENSES (macro/industry/sector/global)
            // composed into one report, run through a chosen MODEL (or the 3-way compare).
            String subject = q.getOrDefault("subject", "fund");      // fund | bond | none
            String code = orDefault(q.get("code"), "");
            java.util.Set<String> lenses = new java.util.LinkedHashSet<>();
            for (String l : q.getOrDefault("lenses", "").split(",")) {
                l = l.trim();
                if (!l.isEmpty()) {
                    lenses.add(l);
                }
            }
            boolean real = !"offline".equals(q.getOrDefault("source", "real")); // real(东财/天天基金) | offline
            String modelId = q.getOrDefault("model", "glm-air");      // glm-air | deepseek | script | compare
            long pace = parseLong(q.get("pace"), 150L);
            runId = q.get("run");
            if (runId != null && !runId.isBlank()) {
                PAUSED.put(runId, paused);
            }

            ex.getResponseHeaders().set("Content-Type", "text/event-stream;charset=utf-8");
            ex.getResponseHeaders().set("Cache-Control", "no-cache");
            ex.getResponseHeaders().set("Connection", "keep-alive");
            ex.sendResponseHeaders(200, 0);
            os = ex.getResponseBody();
            final OutputStream out = os;

            long now = System.currentTimeMillis();
            final long paceF = pace;

            // Heartbeat: a model call can block for tens of seconds with no events, which
            // looks frozen. A daemon thread ticks elapsed-time every 2s so the UI stays alive.
            final long startMs = now;
            Thread heartbeat = new Thread(() -> {
                while (alive.get()) {
                    try {
                        Thread.sleep(2000);
                    } catch (InterruptedException ie) {
                        return;
                    }
                    if (alive.get()) {
                        send(out, "tick", Map.of("elapsedMs", System.currentTimeMillis() - startMs));
                    }
                }
            }, "research-web-heartbeat");
            heartbeat.setDaemon(true);
            hb[0] = heartbeat;
            heartbeat.start();

            final java.util.Set<String> lensesF = lenses;
            List<Map<String, String>> roster =
                    com.bank.financial.research.composite.CompositeReportEngine.modulesFor(subject, lenses);
            if (roster.isEmpty()) {
                sendNote(out, "请至少选择一个标的,或勾选一个分析视角。");
                send(out, "done", Map.of());
                return;
            }

            // Single model — staged presentation: subject base report first (its agents +
            // blackboard + memory), then each lens as a supplement below.
            if (!"script".equals(modelId) && !ResearchReports.isLive(modelId)) {
                sendNote(out, "未检测到该模型的配置(" + modelId + "),本次回退脚本模型;在 play-web.sh 注入对应凭据后可用真实模型。");
            }
            ReportModel m = ResearchReports.modelChoice(modelId);
            com.bank.financial.research.composite.CompositeReportEngine.StageSink sink =
                    new com.bank.financial.research.composite.CompositeReportEngine.StageSink() {
                        @Override
                        public void stage(String key, String title, List<Map<String, String>> agents) {
                            send(out, "stage", Map.of("key", key, "title", title, "agents", agents));
                        }

                        @Override
                        public void agent(String role, String state, int index, int total) {
                            // Cooperative pause: hold before each agent transition while paused.
                            while (paused.get()) {
                                try {
                                    Thread.sleep(200);
                                } catch (InterruptedException ie) {
                                    Thread.currentThread().interrupt();
                                    break;
                                }
                            }
                            send(out, "agent", Map.of("role", role, "state", state, "index", index, "total", total));
                            if ("running".equals(state) && paceF > 0) {
                                try {
                                    Thread.sleep(paceF);
                                } catch (InterruptedException ie) {
                                    Thread.currentThread().interrupt();
                                }
                            }
                        }

                        @Override
                        public void agentDone(String role, Map<String, String> wrote) {
                            send(out, "agent-detail", Map.of("role", role, "wrote", wrote));
                        }

                        @Override
                        public void interactions(List<Map<String, String>> edges) {
                            send(out, "interactions", Map.of("edges", edges));
                        }

                        @Override
                        public void experience(List<Map<String, Object>> lessons) {
                            send(out, "experience", Map.of("lessons", lessons));
                        }

                        @Override
                        public void report(String title, String markdown) {
                            send(out, "stage-report", Map.of("title", title, "html", MdHtml.render(markdown)));
                        }
                    };
            com.bank.financial.research.composite.CompositeReport cr =
                    new com.bank.financial.research.composite.CompositeReportEngine(
                            m, webExp("composite"), MemoryObserver.NOOP, () -> now, now, real)
                            .generate(subject, code, lensesF, sink);
            send(out, "summary", Map.of(
                    "subtitle", cr.subtitle(),
                    "modules", cr.modules().size(),
                    "model", cr.metadata().modelName(),
                    "modelCalls", cr.metadata().modelCalls(),
                    "degradations", cr.metadata().degradations().size(),
                    "real", real));
            send(out, "done", Map.of());
        } catch (Exception e) {
            if (os != null) {
                try {
                    send(os, "error", Map.of("message", String.valueOf(e.getMessage())));
                } catch (RuntimeException ignored) {
                    // client gone
                }
            }
        } finally {
            alive.set(false);
            if (hb[0] != null) {
                hb[0].interrupt();
            }
            if (runId != null) {
                PAUSED.remove(runId);
            }
            if (os != null) {
                try {
                    os.close();
                } catch (IOException ignored) {
                    // ignore
                }
            }
            ex.close();
            RUN_SLOTS.end(); // free the slot for a waiting caller
        }
    }

    /** All run slots are taken: shed this request with 503 + Retry-After rather than queue it. */
    private static void rejectBusy(HttpExchange ex) {
        try {
            byte[] body = "{\"error\":\"server busy: too many concurrent reports, retry shortly\"}"
                    .getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().set("Content-Type", "application/json;charset=utf-8");
            ex.getResponseHeaders().set("Retry-After", "5");
            ex.sendResponseHeaders(503, body.length);
            try (OutputStream o = ex.getResponseBody()) {
                o.write(body);
            }
        } catch (IOException ignored) {
            // client gone
        } finally {
            ex.close();
        }
    }

    /** Recall the cross-run experience accumulated for this report type and stream it to the UI. */
    private static void sendExperience(OutputStream out, String type) {
        try {
            ExperienceMemoryKit kit = ExperienceMemoryKit.forTenant(webExp(type), "web");
            // Match the engines' signature taskType (research-report:FUND/EQUITY/BOND) so
            // recall ranks/returns this type's accumulated lessons.
            String taskType = "research-report:" + type.toUpperCase(java.util.Locale.ROOT);
            java.util.List<Lesson> lessons =
                    kit.recall(new CollaborationSignature(java.util.Set.of(), taskType), 12);
            java.util.List<Map<String, Object>> out2 = new java.util.ArrayList<>();
            for (Lesson l : lessons) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("text", l.text());
                m.put("reinforcement", l.reinforcement());
                m.put("source", l.sourceAgentId());
                out2.add(m);
            }
            send(out, "experience", Map.of("lessons", out2));
        } catch (RuntimeException ignored) {
            // experience exposure must never affect the run
        }
    }

    private static void sendNote(OutputStream out, String message) {
        send(out, "note", Map.of("message", message));
    }

    /** Write one SSE frame; swallow IO (a disconnected client must not crash the run).
     *  Synchronized on the stream so the heartbeat thread and the worker can both write. */
    private static void send(OutputStream out, String event, Map<String, ?> data) {
        try {
            String frame = "event: " + event + "\n"
                    + "data: " + JSON.writeValueAsString(data) + "\n\n";
            byte[] bytes = frame.getBytes(StandardCharsets.UTF_8);
            synchronized (out) {
                out.write(bytes);
                out.flush();
            }
        } catch (IOException e) {
            // client disconnected mid-stream — stop trying to write
        } catch (RuntimeException e) {
            // serialization issue — best effort, don't abort the run
        }
    }

    private static Map<String, String> parseQuery(String raw) {
        Map<String, String> q = new LinkedHashMap<>();
        if (raw == null || raw.isBlank()) {
            return q;
        }
        for (String pair : raw.split("&")) {
            int eq = pair.indexOf('=');
            if (eq < 0) {
                q.put(decode(pair), "");
            } else {
                q.put(decode(pair.substring(0, eq)), decode(pair.substring(eq + 1)));
            }
        }
        return q;
    }

    private static String decode(String s) {
        return java.net.URLDecoder.decode(s, StandardCharsets.UTF_8);
    }

    private static String orDefault(String v, String fallback) {
        return (v == null || v.isBlank()) ? fallback : v;
    }

    private static long parseLong(String v, long fallback) {
        if (v == null || v.isBlank()) {
            return fallback;
        }
        try {
            return Math.max(0, Long.parseLong(v.trim()));
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    // ── the single-page app (inline CSS + JS, no external resources) ─────────────
    private static final String PAGE = """
            <!doctype html>
            <html lang="zh-CN">
            <head>
            <meta charset="utf-8"/>
            <meta name="viewport" content="width=device-width, initial-scale=1"/>
            <title>研报生成 · 多智能体引擎</title>
            <style>
              :root{
                --bg:#0e1117; --panel:#161b22; --panel2:#1c232c; --border:#2a313c;
                --txt:#e6edf3; --muted:#8b949e; --accent:#3b82f6; --accent2:#1d4ed8;
                --green:#2ea043; --green-bg:#11331c; --pulse:#f59e0b;
              }
              *{box-sizing:border-box}
              body{margin:0;font-family:-apple-system,BlinkMacSystemFont,"Segoe UI",
                "PingFang SC","Hiragino Sans GB","Microsoft YaHei",sans-serif;
                background:var(--bg);color:var(--txt);font-size:14px;line-height:1.6}
              header{padding:18px 28px;border-bottom:1px solid var(--border);
                background:linear-gradient(180deg,#161b22,#0e1117)}
              header h1{margin:0;font-size:18px;font-weight:600;letter-spacing:.3px}
              header .sub{color:var(--muted);font-size:12px;margin-top:3px}
              .wrap{display:grid;grid-template-columns:300px 1fr;gap:18px;padding:18px 28px;
                align-items:start}
              .card{background:var(--panel);border:1px solid var(--border);border-radius:10px;
                padding:16px}
              .card h2{margin:0 0 12px;font-size:13px;font-weight:600;color:var(--muted);
                text-transform:uppercase;letter-spacing:.5px}
              .field{margin-bottom:14px}
              .field label{display:block;font-size:12px;color:var(--muted);margin-bottom:6px}
              .opt{display:flex;align-items:center;gap:8px;padding:7px 9px;border:1px solid var(--border);
                border-radius:7px;margin-bottom:6px;cursor:pointer;transition:.15s}
              .opt:hover{border-color:var(--accent)}
              .opt input{accent-color:var(--accent)}
              .opt.dis{opacity:.45;cursor:not-allowed}
              input[type=text],input[type=range]{width:100%}
              input[type=text]{background:var(--panel2);border:1px solid var(--border);
                border-radius:7px;color:var(--txt);padding:8px 10px;font-size:14px}
              input[type=text]:focus{outline:none;border-color:var(--accent)}
              .paceval{float:right;color:var(--txt);font-variant-numeric:tabular-nums}
              button{width:100%;background:var(--accent);color:#fff;border:none;border-radius:8px;
                padding:11px;font-size:14px;font-weight:600;cursor:pointer;transition:.15s}
              button:hover{background:var(--accent2)}
              button:disabled{opacity:.5;cursor:not-allowed}
              .btn-go{background:var(--green)} .btn-go:hover{background:#268839}
              .btn-stop{background:#d1242f} .btn-stop:hover{background:#b21e28}
              .btn-resume{background:#f59e0b;color:#1a1205} .btn-resume:hover{background:#d8890a}
              .right{display:flex;flex-direction:column;gap:18px;min-width:0}
              .pipe-head{display:flex;justify-content:space-between;align-items:baseline;
                margin-bottom:12px}
              .pipe-head .count{font-size:12px;color:var(--muted);font-variant-numeric:tabular-nums}
              .chips{display:flex;flex-wrap:wrap;gap:8px}
              .chip{display:flex;align-items:center;gap:6px;background:var(--panel2);
                border:1px solid var(--border);border-radius:20px;padding:6px 13px;font-size:12.5px;
                color:var(--muted);transition:.2s;cursor:pointer}
              .chip .dot{width:7px;height:7px;border-radius:50%;background:var(--border)}
              .chip .rolename{font-variant-numeric:tabular-nums;opacity:.75;font-size:11px}
              .chip.running{color:var(--txt);border-color:var(--pulse);
                animation:pulse 1s ease-in-out infinite}
              .chip.running .dot{background:var(--pulse)}
              .chip.done{color:var(--green);border-color:var(--green);background:var(--green-bg)}
              .chip.done .dot{background:var(--green)}
              .chip.done .dot::after{content:"";}
              .chip .freed{font-size:10px;color:var(--muted);background:rgba(139,148,158,.15);
                border-radius:8px;padding:1px 6px;margin-left:2px}
              .chip.open{border-color:var(--accent)}
              .chip-detail{margin-top:8px;background:var(--panel2);border:1px solid var(--border);
                border-radius:8px;padding:10px 12px;font-size:12px}
              .chip-detail .kv{display:flex;gap:8px;padding:3px 0;border-bottom:1px dashed var(--border)}
              .chip-detail .kv:last-child{border-bottom:none}
              .chip-detail .k{color:var(--accent);font-weight:600;min-width:130px;
                word-break:break-all}
              .chip-detail .v{color:#c9d1d9;word-break:break-all}
              .chip-detail .none{color:var(--muted)}
              /* shared blackboard table */
              .bbtable{width:100%;border-collapse:collapse;font-size:12px}
              .bbtable th,.bbtable td{text-align:left;padding:6px 9px;
                border-bottom:1px solid var(--border);vertical-align:top}
              .bbtable th{color:var(--muted);font-weight:600;text-transform:uppercase;
                letter-spacing:.4px;font-size:11px}
              .bbtable td.k{color:var(--accent);font-weight:600;word-break:break-all}
              .bbtable td.v{color:#c9d1d9;word-break:break-all}
              .bbtable td.owner{color:var(--muted)}
              .bbtable .owner-tag{background:var(--panel2);border:1px solid var(--border);
                border-radius:12px;padding:2px 9px;font-size:11px;white-space:nowrap}
              .bb-empty{color:var(--muted);text-align:center;padding:22px 0;font-size:12px}
              .lifecycle{margin-top:12px;font-size:12px;color:var(--green);
                border-top:1px solid var(--border);padding-top:10px}
              @keyframes pulse{0%,100%{box-shadow:0 0 0 0 rgba(245,158,11,.4)}
                50%{box-shadow:0 0 0 5px rgba(245,158,11,0)}}
              .badges{display:flex;flex-wrap:wrap;gap:8px;margin-bottom:14px}
              .badge{font-size:12px;padding:5px 11px;border-radius:7px;background:var(--panel2);
                border:1px solid var(--border)}
              .badge b{color:var(--accent)}
              .badge.warn b{color:var(--pulse)}
              .preview{background:#0d1117;border:1px solid var(--border);border-radius:8px;
                padding:22px 26px;max-height:62vh;overflow:auto}
              .preview h1{font-size:22px;border-bottom:1px solid var(--border);padding-bottom:8px}
              .preview h2{font-size:17px;margin-top:24px;color:var(--accent)}
              .preview h3{font-size:14px;color:var(--muted)}
              .preview hr{border:none;border-top:1px solid var(--border);margin:18px 0}
              .preview blockquote{margin:14px 0;padding:10px 16px;border-left:3px solid var(--accent);
                background:var(--panel2);border-radius:0 6px 6px 0;color:var(--txt)}
              .preview ul{padding-left:22px}
              .preview p{color:#c9d1d9}
              .empty{color:var(--muted);text-align:center;padding:50px 0}
              .note{font-size:12px;color:var(--pulse);margin-top:8px}
            </style>
            </head>
            <body>
            <header>
              <h1>研报生成 · 多智能体引擎</h1>
            </header>
            <div class="wrap">
              <!-- LEFT: config -->
              <div class="card">
                <h2>配置</h2>
                <div class="field">
                  <label>标的(单选)</label>
                  <label class="opt"><input type="radio" name="subject" value="fund" checked/> 基金 / FOF</label>
                  <label class="opt"><input type="radio" name="subject" value="bond"/> 债券 / 固收</label>
                  <input type="text" id="code" value="110020" autocomplete="off" style="margin-top:6px"/>
                  <div id="codehint" style="font-size:11px;color:var(--muted);margin-top:5px;"></div>
                </div>
                <div class="field">
                  <label>数据源</label>
                  <label class="opt"><input type="radio" name="source" value="real" checked/> 真实(东财/天天基金,失败回退)</label>
                  <label class="opt"><input type="radio" name="source" value="offline"/> 离线(桩/快照)</label>
                </div>
                <div class="field">
                  <label>生成模型</label>
                  <label class="opt"><input type="radio" name="model" value="deepseek" checked/> DeepSeek-V4-Flash(较快,推荐)</label>
                  <label class="opt"><input type="radio" name="model" value="script"/> 桩(离线秒出)</label>
                  <div style="font-size:11px;color:var(--muted);margin-top:5px;">数字始终由计算引擎给出;模型只写散文。真实模型每章节一次调用,需几十秒~数分钟(下方有"已用时"显示);未配置时回退桩。</div>
                </div>
                <div class="field">
                  <label>演示节奏 <span class="paceval" id="paceval">150 ms</span></label>
                  <input type="range" id="pace" min="0" max="1000" step="50" value="150"/>
                </div>
                <div style="display:flex;gap:8px">
                  <button id="go" class="btn-go">开始生成</button>
                  <button id="toggle" class="btn-stop" style="display:none">停止</button>
                </div>
                <div class="note" id="note"></div>
              </div>

              <!-- RIGHT: pipeline + preview -->
              <div class="right">
                <div class="card">
                  <div class="pipe-head">
                    <h2 style="margin:0">智能体流水线</h2>
                    <span class="count" id="count">运行中 0 · 完成 0 / 9</span>
                  </div>
                  <div class="chips" id="chips"></div>
                  <div id="lifecycle"></div>
                </div>
                <div class="card">
                  <div class="pipe-head">
                    <h2 style="margin:0">共享黑板 / Shared Memory</h2>
                    <span class="count" id="bbcount">0 条</span>
                  </div>
                  <div id="blackboard"><div class="bb-empty">运行后将实时累积各智能体写入的共享记忆 ——</div></div>
                </div>
                <div class="card">
                  <div class="pipe-head">
                    <h2 style="margin:0">智能体协作流 / Interactions</h2>
                    <span class="count" id="ixcount">0 条</span>
                  </div>
                  <div id="interactions"><div class="bb-empty">运行后展示智能体间的协作边(分派/读取/交接/产出)——</div></div>
                </div>
                <div class="card">
                  <div class="pipe-head">
                    <h2 style="margin:0">经验 / Experience(跨运行累积)</h2>
                    <span class="count" id="expcount">0 条</span>
                  </div>
                  <div id="experience"><div class="bb-empty">同类研报重复运行后,此处累积可复用的经验教训(跨运行持久)——</div></div>
                </div>
                <div class="card">
                  <h2>报告预览</h2>
                  <div class="badges" id="badges"></div>
                  <div class="preview" id="preview"><div class="empty">点击「生成研报」开始 ——</div></div>
                </div>
              </div>
            </div>

            <script>
            (function(){
              var DEFCODE={fund:"110020",bond:"DEMOBOND",none:""};
              var CODEHINT={fund:"真实基金用 6 位代码(默认 110020);离线源任意",
                            bond:"债券为合成样例(免费实时债券数据难取)",
                            none:"无需代码;直接出纯宏观 / 策略组合"};
              function subjectVal(){ return document.querySelector('input[name=subject]:checked').value; }
              function renderSubject(){
                var s=subjectVal(), code=document.getElementById('code');
                code.value=DEFCODE[s]; code.style.display=(s==='none')?'none':'';
                document.getElementById('codehint').textContent=CODEHINT[s];
              }
              Array.prototype.forEach.call(document.querySelectorAll('input[name=subject]'),function(r){
                r.addEventListener('change',renderSubject);
              });
              renderSubject();

              var chipEl={}, total=0;
              var agentWrote={};   // role -> {key:value,...} accumulated from agent-detail
              var board={};        // key -> {value, owner} live shared blackboard
              var IXLABEL={DISPATCH:"分派",HANDOVER:"交接",READ:"读取",VALIDATE:"校验",OUTCOME:"产出"};
              function renderIx(edges){
                document.getElementById('ixcount').textContent=edges.length+' 条';
                var box=document.getElementById('interactions');
                if(!edges.length){ box.innerHTML='<div class="bb-empty">无协作边</div>'; return; }
                box.innerHTML='<table class="bbtable"><tbody>'+edges.map(function(e){
                  var t=IXLABEL[e.type]||e.type;
                  return '<tr><td class="owner"><span class="owner-tag">'+esc(e.actor)+'</span></td>'+
                    '<td class="k" style="text-align:center">→['+esc(t)+']→</td>'+
                    '<td class="owner"><span class="owner-tag">'+esc(e.target||'')+'</span></td>'+
                    '<td class="v">'+esc(trunc(e.detail||''))+'</td></tr>';
                }).join('')+'</tbody></table>';
              }
              function addStage(title,agents){
                var c=document.getElementById('chips');
                var h=document.createElement('div'); h.style.flexBasis='100%';
                h.style.cssText+=';width:100%;font-size:12px;color:var(--muted);margin:10px 0 2px';
                h.textContent='▸ '+title;
                c.appendChild(h);
                agents.forEach(function(a){
                  var short=a.role.indexOf('/')>=0?a.role.split('/')[1]:a.role;
                  var d=document.createElement('div'); d.className='chip'; d.dataset.role=a.role;
                  d.innerHTML='<span class="dot"></span><span class="rolename">'+esc(short)+
                    '</span> · '+esc(a.label);
                  d.addEventListener('click',function(){ toggleChip(a.role); });
                  c.appendChild(d); chipEl[a.role]=d; total++;
                });
                recount();
              }
              function appendStageReport(title,html){
                var p=document.getElementById('preview');
                var empty=p.querySelector('.empty'); if(empty) p.innerHTML='';
                var w=document.createElement('div');
                w.innerHTML='<h2 style="border-top:1px solid var(--border);padding-top:12px;margin-top:18px">'+
                  esc(title)+'</h2>'+html;
                p.appendChild(w);
              }
              function toggleChip(role){
                var c=document.getElementById('chips');
                var existing=document.getElementById('detail-'+cssId(role));
                if(existing){ existing.parentNode.removeChild(existing);
                  if(chipEl[role]) chipEl[role].classList.remove('open'); return; }
                // close any other open detail (single-open)
                Array.prototype.forEach.call(c.querySelectorAll('.chip-detail'),function(x){
                  x.parentNode.removeChild(x); });
                Array.prototype.forEach.call(c.querySelectorAll('.chip.open'),function(x){
                  x.classList.remove('open'); });
                var det=document.createElement('div'); det.className='chip-detail';
                det.id='detail-'+cssId(role); det.style.flexBasis='100%';
                var wrote=agentWrote[role]||{}; var keys=Object.keys(wrote);
                if(keys.length===0){
                  det.innerHTML='<div class="none">该智能体本次未向黑板写入新键(或尚未执行)。</div>';
                } else {
                  det.innerHTML=keys.map(function(k){
                    return '<div class="kv"><span class="k">'+esc(k)+'</span>'+
                      '<span class="v">'+esc(trunc(wrote[k]))+'</span></div>'; }).join('');
                }
                if(chipEl[role]){ chipEl[role].classList.add('open');
                  // insert right after the chip so it reads inline
                  if(chipEl[role].nextSibling){ c.insertBefore(det,chipEl[role].nextSibling); }
                  else { c.appendChild(det); }
                }
              }
              function cssId(s){ return String(s).replace(/[^a-zA-Z0-9_-]/g,'_'); }
              function trunc(s){ s=String(s==null?'':s); return s.length>80?s.slice(0,80)+'…':s; }
              function renderBoard(){
                var keys=Object.keys(board);
                document.getElementById('bbcount').textContent=keys.length+' 条';
                var box=document.getElementById('blackboard');
                if(keys.length===0){
                  box.innerHTML='<div class="bb-empty">运行后将实时累积各智能体写入的共享记忆 ——</div>';
                  return;
                }
                var rows=keys.map(function(k){
                  return '<tr><td class="k">'+esc(k)+'</td>'+
                    '<td class="v">'+esc(trunc(board[k].value))+'</td>'+
                    '<td class="owner"><span class="owner-tag">'+esc(board[k].owner)+'</span></td></tr>';
                }).join('');
                box.innerHTML='<table class="bbtable"><thead><tr><th>键 Key</th>'+
                  '<th>值 Value</th><th>写入方 Owner</th></tr></thead><tbody>'+rows+'</tbody></table>';
              }
              function renderExp(lessons){
                document.getElementById('expcount').textContent=lessons.length+' 条';
                var box=document.getElementById('experience');
                if(!lessons.length){
                  box.innerHTML='<div class="bb-empty">同类研报重复运行后,此处累积可复用的经验教训(跨运行持久)——</div>';
                  return;
                }
                box.innerHTML=lessons.map(function(l){
                  var r=(l.reinforcement>0)?'<span class="owner-tag">强化×'+l.reinforcement+'</span>':'';
                  return '<div class="kv"><span class="v">'+esc(trunc(l.text))+'</span>'+
                    '<span class="owner">'+r+'</span></div>';
                }).join('');
              }
              function recount(){
                var running=0,done=0;
                Object.keys(chipEl).forEach(function(k){
                  var cl=chipEl[k].className;
                  if(cl.indexOf('done')>=0) done++; else if(cl.indexOf('running')>=0) running++;
                });
                document.getElementById('count').textContent=
                  '运行中 '+running+' · 完成 '+done+' / '+(total||0);
              }
              var pace=document.getElementById('pace'), paceval=document.getElementById('paceval');
              pace.addEventListener('input',function(){paceval.textContent=pace.value+' ms';});

              var go=document.getElementById('go'), es=null;
              var toggleBtn=document.getElementById('toggle'), runId=null, paused=false;
              toggleBtn.addEventListener('click',function(){
                if(!runId) return;
                paused=!paused;
                fetch('/api/control?run='+encodeURIComponent(runId)+'&action='+(paused?'pause':'resume')).catch(function(){});
                if(paused){ toggleBtn.textContent='继续'; toggleBtn.className='btn-resume'; }
                else { toggleBtn.textContent='停止'; toggleBtn.className='btn-stop'; }
              });
              go.addEventListener('click',function(){
                if(es){es.close();}
                document.getElementById('chips').innerHTML=''; chipEl={}; total=0;
                agentWrote={}; board={};
                document.getElementById('count').textContent='运行中 0 · 完成 0 / 0';
                document.getElementById('lifecycle').innerHTML='';
                renderBoard();
                document.getElementById('ixcount').textContent='0 条';
                document.getElementById('interactions').innerHTML=
                  '<div class="bb-empty">运行后展示智能体间的协作边 ——</div>';
                document.getElementById('badges').innerHTML='';
                // NOTE: do not clear the Experience panel — it is cross-run/persistent.
                document.getElementById('note').textContent='';
                document.getElementById('preview').innerHTML='<div class="empty">流水线运行中 ——</div>';
                go.disabled=true; go.textContent='生成中…';
                runId='r'+Date.now()+Math.floor(Math.random()*1e6); paused=false;
                toggleBtn.style.display=''; toggleBtn.textContent='停止'; toggleBtn.className='btn-stop';
                var subject=subjectVal();
                var source=document.querySelector('input[name=source]:checked').value;
                var model=document.querySelector('input[name=model]:checked').value;
                var code=encodeURIComponent(document.getElementById('code').value||'');
                es=new EventSource('/api/run?subject='+subject+'&code='+code+'&source='+source+
                  '&model='+model+'&lenses=&run='+encodeURIComponent(runId)+'&pace='+pace.value);
                es.addEventListener('tick',function(e){
                  var s=Math.round((JSON.parse(e.data).elapsedMs||0)/1000);
                  go.textContent=(paused?'已暂停… ':'生成中… ')+s+'s'; document.title='('+s+'s) 研报生成';
                });
                es.addEventListener('stage',function(e){
                  var d=JSON.parse(e.data); addStage(d.title||d.key, d.agents||[]);
                });
                es.addEventListener('stage-report',function(e){
                  var d=JSON.parse(e.data); appendStageReport(d.title||'报告', d.html||'');
                });
                es.addEventListener('summary',function(e){
                  var d=JSON.parse(e.data), degClass=(d.degradations>0)?'badge warn':'badge';
                  document.getElementById('badges').innerHTML=
                    '<span class="badge">'+esc(d.subtitle)+'</span>'+
                    '<span class="badge">模块 <b>'+d.modules+'</b></span>'+
                    '<span class="badge">模型 <b>'+esc(d.model)+'</b></span>'+
                    '<span class="badge">调用 <b>'+d.modelCalls+'</b></span>'+
                    '<span class="badge">'+(d.real?'真实/快照数据':'离线数据')+'</span>'+
                    '<span class="'+degClass+'">降级 <b>'+d.degradations+'</b></span>';
                });
                es.addEventListener('agent',function(e){
                  var d=JSON.parse(e.data), el=chipEl[d.role]; if(!el) return;
                  el.classList.remove('running','done');
                  el.classList.add(d.state==='done'?'done':'running'); recount();
                });
                es.addEventListener('agent-detail',function(e){
                  var d=JSON.parse(e.data); if(!d||!d.role) return;
                  var wrote=d.wrote||{};
                  agentWrote[d.role]=Object.assign(agentWrote[d.role]||{},wrote);
                  Object.keys(wrote).forEach(function(k){
                    board[k]={value:wrote[k],owner:d.role};
                  });
                  renderBoard();
                  // if this agent's detail panel is open, refresh it live
                  if(chipEl[d.role]&&chipEl[d.role].classList.contains('open')){
                    toggleChip(d.role); toggleChip(d.role);
                  }
                });
                es.addEventListener('note',function(e){
                  document.getElementById('note').textContent=JSON.parse(e.data).message||'';
                });
                es.addEventListener('interactions',function(e){
                  var d=JSON.parse(e.data); renderIx(d.edges||[]);
                });
                es.addEventListener('experience',function(e){
                  var d=JSON.parse(e.data); renderExp(d.lessons||[]);
                });
                es.addEventListener('report',function(e){
                  var d=JSON.parse(e.data), degClass=(d.degradations>0)?'badge warn':'badge';
                  document.getElementById('badges').innerHTML=
                    '<span class="badge">评级 <b>'+esc(d.rating)+'</b></span>'+
                    '<span class="badge">'+esc(d.metric1)+'</span>'+
                    '<span class="badge">'+esc(d.metric2)+'</span>'+
                    '<span class="badge">'+esc(d.metric3)+'</span>'+
                    '<span class="badge">模型调用 <b>'+d.modelCalls+'</b></span>'+
                    '<span class="badge">改稿轮数 <b>'+d.criticRounds+'</b></span>'+
                    '<span class="'+degClass+'">降级 <b>'+d.degradations+'</b></span>';
                  document.getElementById('preview').innerHTML=d.html;
                });
                es.addEventListener('error',function(e){
                  var msg='连接中断'; try{ if(e.data){ msg=JSON.parse(e.data).message||msg; } }catch(_){}
                  document.getElementById('note').textContent='出错:'+msg; finish();
                });
                es.addEventListener('done',function(){
                  var n=0;
                  Object.keys(chipEl).forEach(function(k){
                    var el=chipEl[k];
                    if(el.className.indexOf('done')>=0){
                      n++;
                      if(!el.querySelector('.freed')){
                        var b=document.createElement('span'); b.className='freed';
                        b.textContent='已释放'; el.appendChild(b);
                      }
                    }
                  });
                  document.getElementById('lifecycle').innerHTML=
                    '<div class="lifecycle">✓ '+n+
                    ' 个智能体已执行并释放(无状态);运行内共享黑板已归档为跨运行经验(Experience)</div>';
                  finish();
                });
              });
              function finish(){ if(es){es.close();es=null;} go.disabled=false; go.textContent='开始生成';
                document.title='研报生成 · 多智能体引擎';
                toggleBtn.style.display='none'; toggleBtn.textContent='停止'; toggleBtn.className='btn-stop';
                runId=null; paused=false; }
              function esc(s){
                return String(s==null?'':s).replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;');
              }
            })();
            </script>
            </body>
            </html>
            """;
}
