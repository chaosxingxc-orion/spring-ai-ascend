package com.huawei.ascend.client;

import com.google.protobuf.util.JsonFormat;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.a2aproject.sdk.grpc.utils.JSONRPCUtils;
import org.a2aproject.sdk.grpc.utils.ProtoUtils;
import org.a2aproject.sdk.spec.AgentCapabilities;
import org.a2aproject.sdk.spec.AgentCard;
import org.a2aproject.sdk.spec.AgentInterface;
import org.a2aproject.sdk.spec.Message;
import org.a2aproject.sdk.spec.TaskState;
import org.a2aproject.sdk.spec.TaskStatus;
import org.a2aproject.sdk.spec.TaskStatusUpdateEvent;
import org.a2aproject.sdk.spec.TextPart;
import org.a2aproject.sdk.spec.TransportProtocol;

/**
 * Stub A2A server for wire-contract tests: serves the agent card and a
 * ping/pong JSON-RPC endpoint (send + SSE stream), records request headers
 * per path, and answers the platform's {@code traceresponse} header. The
 * responses are serialized with the same proto-JSON helpers the real server
 * side uses, so the JSON dialect matches the platform's A2A surface by
 * construction.
 */
final class StubA2aServer implements AutoCloseable {

    static final String TRACERESPONSE =
            "00-0123456789abcdef0123456789abcdef-89abcdef01234567-01";

    private static final Pattern REQUEST_ID_PATTERN =
            Pattern.compile("\"id\"\\s*:\\s*\"([^\"]+)\"");

    private final HttpServer server;
    private final String baseUrl;
    /** Last-seen request headers per path, captured off the wire. */
    private final Map<String, Map<String, String>> recordedHeaders = new ConcurrentHashMap<>();
    private volatile boolean failJsonRpc;
    private volatile boolean streamInputRequired;
    private volatile HttpExchange openSseExchange;

    StubA2aServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        baseUrl = "http://localhost:" + server.getAddress().getPort();
        server.createContext("/.well-known/agent-card.json", this::serveAgentCard);
        server.createContext("/a2a", this::serveJsonRpc);
        server.start();
    }

    String baseUrl() {
        return baseUrl;
    }

    /** Recorded request headers for {@code path}, names lowercased. */
    Map<String, String> recordedHeaders(String path) {
        return recordedHeaders.get(path);
    }

    /** From now on, /a2a answers HTTP 500 — for failure-path tests. */
    void failJsonRpcWithHttp500() {
        failJsonRpc = true;
    }

    /**
     * From now on, SSE streams end the turn with a human-in-the-loop pause:
     * ack, the agent's prompt message, then a non-final
     * {@code input-required} status update — and the stream stays OPEN, like
     * the real runtime, which holds a suspended run's stream until the user
     * answers. A client waiting for a run-terminal event would hang here.
     */
    void streamInputRequiredAndStayOpen() {
        streamInputRequired = true;
    }

    @Override
    public void close() {
        HttpExchange open = openSseExchange;
        if (open != null) {
            open.close();
        }
        server.stop(0);
    }

    private void serveAgentCard(HttpExchange exchange) throws IOException {
        record(exchange);
        AgentCard card = AgentCard.builder()
                .name("stub-agent")
                .description("stub A2A server for wire-contract tests")
                .url(baseUrl + "/a2a")
                .version("1.0")
                .capabilities(AgentCapabilities.builder().streaming(true).build())
                .defaultInputModes(List.of("text"))
                .defaultOutputModes(List.of("text"))
                .skills(List.of())
                .supportedInterfaces(List.of(
                        new AgentInterface(TransportProtocol.JSONRPC.asString(), baseUrl + "/a2a")))
                .preferredTransport(TransportProtocol.JSONRPC.asString())
                .build();
        String json = JsonFormat.printer().print(ProtoUtils.ToProto.agentCard(card));
        respondJson(exchange, json);
    }

    private void serveJsonRpc(HttpExchange exchange) throws IOException {
        record(exchange);
        if (failJsonRpc) {
            exchange.sendResponseHeaders(500, -1);
            exchange.close();
            return;
        }
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        String requestId = requestId(body);
        if (body.contains("\"SendStreamingMessage\"")) {
            serveSse(exchange, requestId);
        } else {
            respondJson(exchange, JSONRPCUtils.toJsonRPCResultResponse(
                    requestId, ProtoUtils.ToProto.taskOrMessage(completedPong())));
        }
    }

    private void serveSse(HttpExchange exchange, String requestId) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", "text/event-stream");
        exchange.getResponseHeaders().set("traceresponse", TRACERESPONSE);
        exchange.sendResponseHeaders(200, 0);
        if (streamInputRequired) {
            // No try-with-resources: the suspended-run stream must stay open.
            openSseExchange = exchange;
            OutputStream out = exchange.getResponseBody();
            writeSseEvent(out, JSONRPCUtils.toJsonRPCResultResponse(
                    requestId, ProtoUtils.ToProto.taskOrMessageStream(acceptedAck())));
            writeSseEvent(out, JSONRPCUtils.toJsonRPCResultResponse(
                    requestId, ProtoUtils.ToProto.taskOrMessageStream(promptMessage())));
            writeSseEvent(out, JSONRPCUtils.toJsonRPCResultResponse(
                    requestId, ProtoUtils.ToProto.taskOrMessageStream(inputRequiredStatus())));
            return;
        }
        try (OutputStream out = exchange.getResponseBody()) {
            writeSseEvent(out, JSONRPCUtils.toJsonRPCResultResponse(
                    requestId, ProtoUtils.ToProto.taskOrMessageStream(acceptedAck())));
            writeSseEvent(out, JSONRPCUtils.toJsonRPCResultResponse(
                    requestId, ProtoUtils.ToProto.taskOrMessageStream(completedPong())));
        }
    }

    /**
     * Multi-line JSON is emitted as one SSE event with one {@code data:} line
     * per JSON line; conforming parsers re-join them with newlines.
     */
    private static void writeSseEvent(OutputStream out, String json) throws IOException {
        StringBuilder event = new StringBuilder();
        for (String line : json.split("\\R")) {
            event.append("data: ").append(line).append('\n');
        }
        event.append('\n');
        out.write(event.toString().getBytes(StandardCharsets.UTF_8));
        out.flush();
    }

    private static Message acceptedAck() {
        return Message.builder()
                .role(Message.Role.ROLE_AGENT)
                .messageId("ack-1")
                .metadata(Map.of("accepted", Boolean.TRUE))
                .parts(List.of(new TextPart("execution enqueued")))
                .build();
    }

    private static Message completedPong() {
        return Message.builder()
                .role(Message.Role.ROLE_AGENT)
                .messageId("reply-1")
                .metadata(Map.of("runStatus", "completed"))
                .parts(List.of(new TextPart("pong")))
                .build();
    }

    /** The agent's question to the user, as the runtime's INTERRUPTED route emits it. */
    private static Message promptMessage() {
        return Message.builder()
                .role(Message.Role.ROLE_AGENT)
                .messageId("prompt-1")
                .parts(List.of(new TextPart("Which account should I use?")))
                .build();
    }

    /** Non-final input-required status, as the runtime's requiresInput() emits it. */
    private static TaskStatusUpdateEvent inputRequiredStatus() {
        return new TaskStatusUpdateEvent(
                "task-1",
                new TaskStatus(TaskState.TASK_STATE_INPUT_REQUIRED, null, null),
                "session-1",
                Map.of());
    }

    private static String requestId(String body) {
        Matcher matcher = REQUEST_ID_PATTERN.matcher(body);
        if (!matcher.find()) {
            throw new IllegalStateException("JSON-RPC request without string id: " + body);
        }
        return matcher.group(1);
    }

    /** Header names recorded lowercase: HTTP headers are case-insensitive on the wire. */
    private void record(HttpExchange exchange) {
        Map<String, String> headers = new ConcurrentHashMap<>();
        exchange.getRequestHeaders().forEach((name, values) -> {
            if (!values.isEmpty()) {
                headers.put(name.toLowerCase(java.util.Locale.ROOT), values.get(0));
            }
        });
        recordedHeaders.put(exchange.getRequestURI().getPath(), headers);
    }

    private static void respondJson(HttpExchange exchange, String json) throws IOException {
        byte[] payload = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.getResponseHeaders().set("traceresponse", TRACERESPONSE);
        exchange.sendResponseHeaders(200, payload.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(payload);
        }
    }
}
