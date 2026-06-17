package com.bank.financial.research.web;

import com.bank.financial.research.data.DataIngestionService;
import com.bank.financial.research.data.FreshnessPolicy;
import com.bank.financial.research.data.ResearchDataSource;
import com.bank.financial.research.data.FundDataSource;
import com.bank.financial.research.data.eastmoney.EastMoneyFundDataSource;
import com.bank.financial.research.data.eastmoney.EastMoneyResearchDataSource;
import com.bank.financial.research.data.stub.StubFundDataSource;
import com.bank.financial.research.data.tushare.TushareResearchDataSource;
import com.bank.financial.research.fund.FundReport;
import com.bank.financial.research.fund.FundReportEngine;
import com.bank.financial.research.data.stub.StubResearchDataSource;
import com.bank.financial.research.engine.PipelineProgress;
import com.bank.financial.research.engine.ReportRequest;
import com.bank.financial.research.engine.ResearchReport;
import com.bank.financial.research.engine.ResearchReportEngine;
import com.bank.financial.research.model.ScriptedReportModel;
import com.fasterxml.jackson.databind.ObjectMapper;
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

    private static final List<Map<String, String>> EQUITY_AGENTS = List.of(
            Map.of("role", "planner", "label", "规划"), Map.of("role", "data", "label", "数据"),
            Map.of("role", "quant-model", "label", "建模"), Map.of("role", "valuation", "label", "估值"),
            Map.of("role", "sector-macro", "label", "行业宏观"), Map.of("role", "lead-manager", "label", "首席"),
            Map.of("role", "writer", "label", "撰写"), Map.of("role", "critic", "label", "评审"),
            Map.of("role", "compliance", "label", "合规"));

    private static final List<Map<String, String>> FUND_AGENTS = List.of(
            Map.of("role", "planner", "label", "规划"), Map.of("role", "data", "label", "数据"),
            Map.of("role", "performance", "label", "业绩"), Map.of("role", "risk", "label", "风险"),
            Map.of("role", "lead-manager", "label", "首席"), Map.of("role", "writer", "label", "撰写"),
            Map.of("role", "critic", "label", "评审"), Map.of("role", "compliance", "label", "合规"));

    private ResearchWebServer() {
    }

    public static void main(String[] args) throws IOException {
        com.bank.financial.research.LogQuieter.quiet();
        int port = parsePort(System.getenv("RESEARCH_WEB_PORT"), 8088);
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.setExecutor(Executors.newFixedThreadPool(4));
        server.createContext("/", ResearchWebServer::handlePage);
        server.createContext("/api/run", ResearchWebServer::handleRun);
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

    // ── GET /api/run  → SSE ─────────────────────────────────────────────────────
    private static void handleRun(HttpExchange ex) {
        OutputStream os = null;
        try {
            Map<String, String> q = parseQuery(ex.getRequestURI().getRawQuery());
            String type = q.getOrDefault("type", "equity");
            String source = q.getOrDefault("source", "eastmoney");
            String ticker = orDefault(q.get("ticker"), "600519.SH");
            long pace = parseLong(q.get("pace"), 250L);

            ex.getResponseHeaders().set("Content-Type", "text/event-stream;charset=utf-8");
            ex.getResponseHeaders().set("Cache-Control", "no-cache");
            ex.getResponseHeaders().set("Connection", "keep-alive");
            ex.sendResponseHeaders(200, 0);
            os = ex.getResponseBody();
            final OutputStream out = os;

            long now = System.currentTimeMillis();

            // progress callback: one SSE line per agent transition (+ optional pacing),
            // plus an "agent-detail" line carrying the keys this agent wrote to the blackboard
            PipelineProgress progress = new PipelineProgress() {
                @Override
                public void onAgent(String role, String state, int index, int total) {
                    Map<String, Object> data = new LinkedHashMap<>();
                    data.put("role", role);
                    data.put("state", state);
                    data.put("index", index);
                    data.put("total", total);
                    send(out, "agent", data);
                    if ("running".equals(state) && pace > 0) {
                        try {
                            Thread.sleep(pace);
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                        }
                    }
                }

                @Override
                public void onAgentDone(String role, Map<String, String> wrote) {
                    Map<String, Object> data = new LinkedHashMap<>();
                    data.put("role", role);
                    data.put("wrote", wrote);
                    send(out, "agent-detail", data);
                }
            };

            Map<String, Object> report = new LinkedHashMap<>();
            if ("fund".equals(type)) {
                send(out, "pipeline", Map.of("agents", FUND_AGENTS));
                String code = orDefault(q.get("ticker"), "110011");
                FundReport fr;
                if ("stub".equals(source)) {
                    fr = new FundReportEngine(new StubFundDataSource(now), new ScriptedReportModel(),
                            null, MemoryObserver.NOOP, () -> now)
                            .generate(ReportRequest.equity(code, "web", now), progress);
                } else {
                    try {
                        fr = new FundReportEngine(new EastMoneyFundDataSource(now), new ScriptedReportModel(),
                                null, MemoryObserver.NOOP, () -> now)
                                .generate(ReportRequest.equity(code, "web", now), progress);
                    } catch (RuntimeException fundErr) {
                        sendNote(out, "实时基金数据获取失败(" + fundErr.getMessage() + "),回退桩数据演示。");
                        fr = new FundReportEngine(new StubFundDataSource(now), new ScriptedReportModel(),
                                null, MemoryObserver.NOOP, () -> now)
                                .generate(ReportRequest.equity("DEMOFUND", "web", now), progress);
                    }
                }
                report.put("html", MdHtml.render(fr.toMarkdown()));
                report.put("rating", fr.overallRating());
                report.put("metric1", "夏普 " + com.bank.financial.research.engine.Bb.fmt(fr.metrics().sharpe()));
                report.put("metric2", "年化 " + com.bank.financial.research.engine.Bb.pct(fr.metrics().annReturn()));
                report.put("metric3", "回撤 " + com.bank.financial.research.engine.Bb.pct(fr.metrics().maxDrawdown()));
                report.put("modelCalls", fr.metadata().modelCalls());
                report.put("criticRounds", fr.metadata().criticRounds());
                report.put("degradations", fr.metadata().degradations().size());
            } else {
                send(out, "pipeline", Map.of("agents", EQUITY_AGENTS));
                ResearchDataSource src;
                String token = System.getenv("TUSHARE_TOKEN");
                switch (source) {
                    case "stub" -> src = new StubResearchDataSource(now);
                    case "tushare" -> {
                        if (token != null && !token.isBlank()) {
                            src = new TushareResearchDataSource(token, now);
                        } else {
                            sendNote(out, "Tushare 需积分(未配置 TUSHARE_TOKEN),演示回退桩数据。");
                            src = new StubResearchDataSource(now);
                        }
                    }
                    case "wind", "choice" -> {
                        sendNote(out, "该源规划中(sidecar 网关),演示回退桩数据。");
                        src = new StubResearchDataSource(now);
                    }
                    default -> src = new EastMoneyResearchDataSource(now);
                }
                ResearchReportEngine engine = new ResearchReportEngine(
                        new DataIngestionService(src, FreshnessPolicy.days(90)), src.name(),
                        new ScriptedReportModel(), null, MemoryObserver.NOOP, () -> now);
                ResearchReport r = engine.generate(ReportRequest.equity(ticker, "web", now), progress);
                report.put("html", MdHtml.render(r.toMarkdown()));
                report.put("rating", r.rating());
                report.put("metric1", "目标价 " + com.bank.financial.research.engine.Bb.fmt(r.priceTarget()));
                report.put("metric2", "现价 " + com.bank.financial.research.engine.Bb.fmt(r.currentPrice()));
                report.put("metric3", "空间 " + com.bank.financial.research.engine.Bb.pct(r.upsidePct()));
                report.put("modelCalls", r.metadata().modelCalls());
                report.put("criticRounds", r.metadata().criticRounds());
                report.put("degradations", r.metadata().degradations().size());
            }
            send(out, "report", report);
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
            if (os != null) {
                try {
                    os.close();
                } catch (IOException ignored) {
                    // ignore
                }
            }
            ex.close();
        }
    }

    private static void sendNote(OutputStream out, String message) {
        send(out, "note", Map.of("message", message));
    }

    /** Write one SSE frame; swallow IO (a disconnected client must not crash the run). */
    private static void send(OutputStream out, String event, Map<String, ?> data) {
        try {
            String frame = "event: " + event + "\n"
                    + "data: " + JSON.writeValueAsString(data) + "\n\n";
            out.write(frame.getBytes(StandardCharsets.UTF_8));
            out.flush();
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
              <div class="sub">9 个专家智能体 · 共享黑板协作 · 离线确定性脚本模型演示</div>
            </header>
            <div class="wrap">
              <!-- LEFT: config -->
              <div class="card">
                <h2>配置</h2>
                <div class="field">
                  <label>报告类型</label>
                  <label class="opt"><input type="radio" name="type" value="equity" checked/> 个股研报</label>
                  <label class="opt"><input type="radio" name="type" value="fund"/> 基金 / FOF</label>
                </div>
                <div class="field">
                  <label>数据源</label>
                  <div id="sources"></div>
                </div>
                <div class="field">
                  <label>标的代码</label>
                  <input type="text" id="ticker" value="600519.SH" autocomplete="off"/>
                  <div id="tickhint" style="font-size:11px;color:var(--muted);margin-top:5px;"></div>
                </div>
                <div class="field">
                  <label>演示节奏 <span class="paceval" id="paceval">250 ms</span></label>
                  <input type="range" id="pace" min="0" max="1000" step="50" value="250"/>
                </div>
                <button id="go">生成研报</button>
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
                  <h2>报告预览</h2>
                  <div class="badges" id="badges"></div>
                  <div class="preview" id="preview"><div class="empty">点击「生成研报」开始 ——</div></div>
                </div>
              </div>
            </div>

            <script>
            (function(){
              var SOURCES={
                equity:[["eastmoney","东方财富(免费真实)"],["stub","桩(离线演示)"],
                        ["tushare","Tushare(需积分)"],["wind","Wind(规划中)"],["choice","Choice(规划中)"]],
                fund:[["eastmoney","天天基金(免费真实)"],["stub","桩(离线演示)"]]
              };
              var DEFTICK={equity:"600519.SH",fund:"110011"};
              var HINT={equity:"真实 A 股用 6 位代码(如 600519.SH);桩演示用 DEMO",
                        fund:"真实基金用 6 位代码(如 110011);桩演示任意"};
              function renderSources(){
                var type=document.querySelector('input[name=type]:checked').value;
                var box=document.getElementById('sources'); box.innerHTML='';
                SOURCES[type].forEach(function(s,i){
                  var lab=document.createElement('label'); lab.className='opt';
                  lab.innerHTML='<input type="radio" name="source" value="'+s[0]+'"'+
                    (i===0?' checked':'')+'/> '+s[1];
                  box.appendChild(lab);
                });
                document.getElementById('ticker').value=DEFTICK[type];
                document.getElementById('tickhint').textContent=HINT[type];
              }
              Array.prototype.forEach.call(document.querySelectorAll('input[name=type]'),function(r){
                r.addEventListener('change',renderSources);
              });
              renderSources();

              var chipEl={}, total=0;
              var agentWrote={};   // role -> {key:value,...} accumulated from agent-detail
              var board={};        // key -> {value, owner} live shared blackboard
              function buildChips(agents){
                var c=document.getElementById('chips'); c.innerHTML=''; chipEl={}; total=agents.length;
                agents.forEach(function(a){
                  var d=document.createElement('div'); d.className='chip'; d.dataset.role=a.role;
                  d.innerHTML='<span class="dot"></span><span class="rolename">'+esc(a.role)+
                    '</span> · '+esc(a.label);
                  d.addEventListener('click',function(){ toggleChip(a.role); });
                  c.appendChild(d); chipEl[a.role]=d;
                });
                recount();
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
              go.addEventListener('click',function(){
                if(es){es.close();}
                document.getElementById('chips').innerHTML=''; chipEl={}; total=0;
                agentWrote={}; board={};
                document.getElementById('count').textContent='运行中 0 · 完成 0 / 0';
                document.getElementById('lifecycle').innerHTML='';
                renderBoard();
                document.getElementById('badges').innerHTML='';
                document.getElementById('note').textContent='';
                document.getElementById('preview').innerHTML='<div class="empty">流水线运行中 ——</div>';
                go.disabled=true; go.textContent='生成中…';
                var type=document.querySelector('input[name=type]:checked').value;
                var source=document.querySelector('input[name=source]:checked').value;
                var ticker=encodeURIComponent(document.getElementById('ticker').value||'');
                es=new EventSource('/api/run?type='+type+'&source='+source+'&ticker='+ticker+'&pace='+pace.value);
                es.addEventListener('pipeline',function(e){ buildChips(JSON.parse(e.data).agents); });
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
              function finish(){ if(es){es.close();es=null;} go.disabled=false; go.textContent='生成研报'; }
              function esc(s){
                return String(s==null?'':s).replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;');
              }
            })();
            </script>
            </body>
            </html>
            """;
}
