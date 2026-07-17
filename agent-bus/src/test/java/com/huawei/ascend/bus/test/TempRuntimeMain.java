package com.huawei.ascend.bus.test;

import com.huawei.ascend.bus.forwarding.runtime.transport.BrokerTopicResolver;
import com.huawei.ascend.bus.forwarding.runtime.transport.broker.rocketmq.RocketMqBrokerForwardingConsumer;
import com.huawei.ascend.bus.forwarding.spi.AgentBusEventType;
import com.huawei.ascend.bus.forwarding.spi.ForwardingRouteHandle;
import com.huawei.ascend.bus.forwarding.spi.broker.BrokerControlDescriptor;
import com.huawei.ascend.bus.forwarding.spi.broker.BrokerInboundMessage;
import com.huawei.ascend.bus.forwarding.spi.broker.DeliveryFilter;

import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.common.message.Message;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Standalone main-class runtime double for E2E verification of FEAT-013 (and FEAT-014)
 * two-hop broker forwarding against a REAL RocketMQ + REAL Postgres agent-bus stack.
 *
 * <p>Stands in for the EXTERNAL {@code agent-runtime-java} (which is not yet wired for
 * FEAT-013's broker ingest path). Subscribes to {@code ascend_bus_invocation_deliver}
 * (hop2 deliver), decodes the {@link BrokerControlDescriptor} payloadRef on each polled
 * request, and produces response events back to {@code ascend_bus_invocation_resp_in}
 * (the response relay governs them and re-publishes to {@code ascend_bus_invocation_resp_out}
 * for the gateway).
 *
 * <p>Mirrors the {@code TempRuntime} inner class in
 * {@code RealBrokerTwoHopRelayIntegrationTest} (G5-E IT), but is a standalone main
 * program (no JUnit harness) so it can run as a peer process alongside the gateway
 * and event-bus fat-jar processes during manual E2E verification. NON-PRODUCTION.
 *
 * <h2>Usage</h2>
 * <pre>
 *   java -cp agent-bus-*.jar \
 *        com.huawei.ascend.bus.test.TempRuntimeMain \
 *        --nameserver      &lt;host:9876&gt;       \
 *        --runtime         &lt;serviceId&gt;       \
 *        --tenant          &lt;tenantId&gt;        \
 *        --mode            BLOCKING|STREAMING|ACCEPTED_ONLY|SILENT|REJECT|DEFER_THEN_RESOLVE \
 *        [--poll-wait-ms   3000]              \
 *        [--consumer-group &lt;group&gt;]          \
 *        [--producer-group &lt;group&gt;]          \
 *        [--route          invocation|a2a]    \
 *        [--verbose]
 * </pre>
 *
 * <h2>Defaults</h2>
 * <ul>
 *   <li>{@code --mode} = {@code BLOCKING} (§6.2.1 阻塞调用返回最终响应)</li>
 *   <li>{@code --route} = {@code invocation} (FEAT-013; use {@code a2a} for FEAT-014)</li>
 *   <li>{@code --poll-wait-ms} = {@code 3000}</li>
 *   <li>{@code --consumer-group} = {@code runtime-<serviceId>} (per-runtime isolation)</li>
 *   <li>{@code --producer-group} = {@code runtime-producer-<serviceId>}</li>
 * </ul>
 *
 * <p>The runtime subscribes with {@link DeliveryFilter#forRuntime} ({@code tenantId +
 * targetServiceId = runtime}) — broker-side SQL92 filtering (requires
 * {@code enablePropertyFilter=true} in broker.conf). It blocks on {@code poll} and
 * processes one request per tick (model B ack-after-consume: {@code commit} on success,
 * skip+commit on non-request descriptors). Shutdown via SIGINT (Ctrl+C) — the shutdown
 * hook closes the consumer + producer cleanly.
 *
 * <p><b>Response routing.</b> Responses are produced directly to
 * {@code ascend_bus_<route>_resp_in} (NOT to the request topic), carrying the symmetric
 * {@link BrokerControlDescriptor} payloadRef so the response relay can recover the
 * control fields (traceId / idempotencyKey / routeHandle / capability / deadline) plus
 * the {@code taskId} / {@code status} / {@code streamRef} tokens the gateway reads via
 * {@link BrokerControlDescriptor#token}. Source = this runtime, target = the original
 * source (gateway / caller).
 *
 * <p>Authority: {@code architecture/L2-Low-Level-Design/agent-bus/
 * feat-013-client-invocation-event-forwarding.md} §4.3 / §6.2;
 * {@code RealBrokerTwoHopRelayIntegrationTest.TempRuntime} (G5-E IT reference impl).
 */
// non-production — E2E verification runtime double (mirrors G5-E IT TempRuntime)
public final class TempRuntimeMain {

    /** Configurable response behaviour for a REQUESTED event (FEAT-013 §6 scenarios). */
    public enum ResponseMode {
        /** §6.2.1 — REQUESTED → ACCEPTED + RESPONSE(snapshot) + TERMINAL(completed). */
        BLOCKING,
        /** §6.2.4 — REQUESTED → ACCEPTED + STREAM_READY(streamRef). */
        STREAMING,
        /** §6.2.2 — REQUESTED → ACCEPTED only (degenerate to Task ref). */
        ACCEPTED_ONLY,
        /** §6.2.3 — REQUESTED → create task, emit nothing (gateway accept window UNKNOWN). */
        SILENT,
        /** §6.2.5 UC-07 — REQUESTED → REJECTED (server refuses; no Task created). */
        REJECT,
        /**
         * §6.2.3 UC-03 — first REQUESTED with a given idempotencyKey creates the Task
         * but emits nothing (gateway accept window → UNKNOWN); a second REQUESTED with
         * the same idempotencyKey hits the server-side idempotency map and replays the
         * BLOCKING response sequence with the same taskId. This avoids SIGSTOP/SIGCONT
         * orchestration for the UNKNOWN-then-retry scenario.
         */
        DEFER_THEN_RESOLVE
    }

    private static final String TOPIC_PREFIX = BrokerTopicResolver.TOPIC_PREFIX; // "ascend_bus_"
    private static final long DEFAULT_POLL_WAIT_MS = 3_000L;

    private final String nameserver;
    private final String runtimeServiceId;
    private final String tenant;
    private final ResponseMode mode;
    private final long pollWaitMillis;
    private final String route; // "invocation" | "a2a"
    private final boolean verbose;

    private final DefaultMQProducer producer;
    private final RocketMqBrokerForwardingConsumer consumer;
    private final String consumerGroup;
    private final String respInTopic;

    private final AtomicLong taskSeq = new AtomicLong();
    // server-side creation idempotency (§4.4 layer 2): (idempotencyKey) → taskId
    private final Map<String, String> taskByIdempotencyKey = new LinkedHashMap<>();
    private volatile boolean running;

    public TempRuntimeMain(String nameserver, String runtimeServiceId, String tenant,
                           ResponseMode mode, long pollWaitMillis, String route,
                           String consumerGroup, String producerGroup, boolean verbose) {
        this.nameserver = Objects.requireNonNull(nameserver, "nameserver is required");
        this.runtimeServiceId = Objects.requireNonNull(runtimeServiceId, "runtimeServiceId is required");
        this.tenant = Objects.requireNonNull(tenant, "tenant is required");
        this.mode = Objects.requireNonNull(mode, "mode is required");
        this.pollWaitMillis = pollWaitMillis > 0 ? pollWaitMillis : DEFAULT_POLL_WAIT_MS;
        this.route = Objects.requireNonNull(route, "route is required");
        this.verbose = verbose;
        this.consumerGroup = consumerGroup != null ? consumerGroup : "runtime-" + runtimeServiceId;
        String producerGroupResolved = producerGroup != null ? producerGroup
                : "runtime-producer-" + runtimeServiceId;

        // consumer: subscribe to hop2 deliver topic (ascend_bus_<route>_deliver)
        this.consumer = new RocketMqBrokerForwardingConsumer(
                new BrokerTopicResolver("deliver"),
                RocketMqBrokerForwardingConsumer.defaultPollerFactory(nameserver),
                this.pollWaitMillis);
        // producer: produce to resp_in topic (ascend_bus_<route>_resp_in)
        this.respInTopic = TOPIC_PREFIX + route + "_resp_in";
        this.producer = new DefaultMQProducer(producerGroupResolved);
        this.producer.setNamesrvAddr(nameserver);
    }

    // ===== lifecycle =====

    public void start() throws Exception {
        log("Starting TempRuntime: nameserver=" + nameserver
                + " runtime=" + runtimeServiceId + " tenant=" + tenant
                + " mode=" + mode + " route=" + route
                + " consumerGroup=" + consumerGroup
                + " respInTopic=" + respInTopic);

        producer.start();
        log("Producer started (group=" + producer.getProducerGroup() + ")");

        // tenant-only filter would also work for a single-tenant relay, but forRuntime
        // (tenantId + targetServiceId) is the strict per-runtime filter (D2/D10) — only
        // messages targeted at THIS runtime are delivered broker-side.
        DeliveryFilter filter = DeliveryFilter.forRuntime(tenant, runtimeServiceId);
        consumer.subscribe(consumerGroup, new ForwardingRouteHandle(route, tenant), filter);
        log("Consumer subscribed: group=" + consumerGroup + " route=" + route
                + " filter=" + filter.requiredProperties());

        Runtime.getRuntime().addShutdownHook(new Thread(this::shutdown, "temp-runtime-shutdown"));
        running = true;
        pollLoop();
    }

    private void pollLoop() {
        log("Poll loop started (pollWaitMs=" + pollWaitMillis + ")");
        while (running) {
            try {
                Optional<BrokerInboundMessage> msg = consumer.poll(System.currentTimeMillis());
                if (msg.isPresent()) {
                    processRequest(msg.get());
                    consumer.commit(msg.get()); // model B ack-after-consume
                }
                if (Thread.currentThread().isInterrupted()) {
                    break;
                }
            } catch (Exception e) {
                if (running) {
                    log("WARN: poll/process error (transient — keep polling): " + e);
                    sleepQuiet(1_000);
                }
            }
        }
        log("Poll loop exited.");
    }

    public synchronized void shutdown() {
        if (!running) {
            return;
        }
        running = false;
        log("Shutting down...");
        try {
            consumer.close();
        } catch (Exception e) {
            log("WARN: consumer close failed: " + e);
        }
        try {
            producer.shutdown();
        } catch (Exception e) {
            log("WARN: producer shutdown failed: " + e);
        }
        log("Shutdown complete.");
    }

    // ===== request processing =====

    private synchronized void processRequest(BrokerInboundMessage req) {
        if (req.payloadRef() == null || req.payloadRef().isBlank()) {
            log("SKIP: no payloadRef (not a request descriptor) messageId=" + req.messageId());
            return;
        }
        BrokerControlDescriptor.Descriptor desc;
        try {
            desc = BrokerControlDescriptor.decode(req.payloadRef());
        } catch (RuntimeException e) {
            log("SKIP: payloadRef not a valid request descriptor messageId=" + req.messageId()
                    + " err=" + e.getMessage());
            return;
        }

        AgentBusEventType eventType = desc.eventType();
        if (!isRequestType(eventType)) {
            log("SKIP: eventType not a request type (" + eventType + ") messageId=" + req.messageId());
            return;
        }

        log("RECV: messageId=" + req.messageId()
                + " eventType=" + eventType
                + " corrId=" + desc.correlationId()
                + " idem=" + desc.idempotencyKey()
                + " route=" + desc.routeHandle()
                + " cap=" + desc.capability()
                + " source=" + req.sourceServiceId());

        if (eventType == AgentBusEventType.CLIENT_INVOCATION_REQUESTED
                || eventType == AgentBusEventType.A2A_CALL_REQUESTED) {
            handleRequested(req, desc);
            return;
        }

        // FEAT-001 stubs (CancelTask / GetTask / SubscribeToTask) — canned responses
        handleStub(eventType, req, desc);
    }

    private void handleRequested(BrokerInboundMessage req, BrokerControlDescriptor.Descriptor desc) {
        boolean a2a = desc.eventType() == AgentBusEventType.A2A_CALL_REQUESTED;
        AgentBusEventType accepted = a2a ? AgentBusEventType.A2A_CALL_ACCEPTED
                : AgentBusEventType.INVOCATION_ACCEPTED;
        AgentBusEventType response = a2a ? AgentBusEventType.A2A_CALL_RESPONSE
                : AgentBusEventType.INVOCATION_RESPONSE;
        AgentBusEventType terminal = a2a ? AgentBusEventType.A2A_CALL_TERMINAL
                : AgentBusEventType.INVOCATION_TERMINAL;
        AgentBusEventType streamReady = a2a ? AgentBusEventType.A2A_STREAM_READY
                : AgentBusEventType.INVOCATION_STREAM_READY;
        AgentBusEventType rejected = a2a ? AgentBusEventType.A2A_CALL_REJECTED
                : AgentBusEventType.INVOCATION_REJECTED;

        // §4.4 layer 2 — server-side creation idempotency: same idempotencyKey → same taskId.
        // REJECT mode does NOT create a task (server refuses the invocation outright).
        // DEFER_THEN_RESOLVE creates the task on first sight but defers the response;
        // the second hit replays the BLOCKING sequence with the same taskId.
        boolean firstSight = mode == ResponseMode.DEFER_THEN_RESOLVE
                && !taskByIdempotencyKey.containsKey(desc.idempotencyKey());
        String taskId = mode == ResponseMode.REJECT
                ? null
                : taskByIdempotencyKey.computeIfAbsent(desc.idempotencyKey(),
                        k -> "task-" + taskSeq.incrementAndGet());

        switch (mode) {
            case BLOCKING -> {
                produceResponse(accepted, taskId, "accepted", req, desc);
                produceResponse(response, taskId, "snapshot", req, desc);
                produceResponse(terminal, taskId, "completed", req, desc);
            }
            case STREAMING -> {
                produceResponse(accepted, taskId, "accepted", req, desc);
                produceResponse(streamReady, taskId, "streamReady", req, desc,
                        "streamRef=stream://" + taskId);
            }
            case ACCEPTED_ONLY -> produceResponse(accepted, taskId, "accepted", req, desc);
            case SILENT -> { /* create task only — gateway accept window will time out (UNKNOWN) */ }
            case REJECT -> produceResponse(rejected, null, "rejected", req, desc,
                    "reason=server-policy-rejected");
            case DEFER_THEN_RESOLVE -> {
                if (firstSight) {
                    // first REQUESTED: create task (already recorded above), emit nothing
                    // → gateway accept window drains → UNKNOWN/DEFERRED
                } else {
                    // second REQUESTED with same idempotencyKey: idempotency hit → replay BLOCKING
                    // with the SAME taskId (proves §4.4 layer 2 server-side idempotency)
                    produceResponse(accepted, taskId, "accepted", req, desc);
                    produceResponse(response, taskId, "snapshot", req, desc);
                    produceResponse(terminal, taskId, "completed", req, desc);
                }
            }
        }
    }

    /** FEAT-001 stubs (CancelTask / GetTask / SubscribeToTask and their A2A twins). */
    private void handleStub(AgentBusEventType eventType, BrokerInboundMessage req,
                            BrokerControlDescriptor.Descriptor desc) {
        switch (eventType) {
            case CLIENT_INVOCATION_CANCEL_REQUESTED -> produceResponse(
                    AgentBusEventType.INVOCATION_TERMINAL, null, "cancelled", req, desc);
            case A2A_CALL_CANCEL_REQUESTED -> produceResponse(
                    AgentBusEventType.A2A_CALL_TERMINAL, null, "cancelled", req, desc);
            case CLIENT_INVOCATION_QUERY_REQUESTED -> produceResponse(
                    AgentBusEventType.INVOCATION_RESPONSE, null, "snapshot", req, desc);
            case A2A_CALL_QUERY_REQUESTED -> produceResponse(
                    AgentBusEventType.A2A_CALL_RESPONSE, null, "snapshot", req, desc);
            case CLIENT_STREAM_SUBSCRIBE_REQUESTED -> produceResponse(
                    AgentBusEventType.INVOCATION_STREAM_READY, null, "streamReady", req, desc,
                    "streamRef=stream://stub");
            case A2A_STREAM_SUBSCRIBE_REQUESTED -> produceResponse(
                    AgentBusEventType.A2A_STREAM_READY, null, "streamReady", req, desc,
                    "streamRef=stream://stub");
            default -> log("WARN: no stub handler for eventType=" + eventType);
        }
    }

    private boolean isRequestType(AgentBusEventType t) {
        return switch (t) {
            case CLIENT_INVOCATION_REQUESTED, CLIENT_INVOCATION_CANCEL_REQUESTED,
                    CLIENT_INVOCATION_QUERY_REQUESTED, CLIENT_STREAM_SUBSCRIBE_REQUESTED,
                    A2A_CALL_REQUESTED, A2A_CALL_CANCEL_REQUESTED,
                    A2A_CALL_QUERY_REQUESTED, A2A_STREAM_SUBSCRIBE_REQUESTED -> true;
            default -> false;
        };
    }

    // ===== response produce =====

    private void produceResponse(AgentBusEventType eventType, String taskId, String status,
                                 BrokerInboundMessage req, BrokerControlDescriptor.Descriptor desc) {
        produceResponse(eventType, taskId, status, req, desc, null);
    }

    /**
     * Build + send a response to {@link #respInTopic} with a symmetric descriptor payloadRef.
     * Body is a routing descriptor only (§6.2 ②). Routing metadata rides as user properties
     * (incl. {@code correlationId} + {@code eventType} so the gateway classifies by NATIVE
     * headers — no descriptor-decoding for classification).
     */
    private void produceResponse(AgentBusEventType eventType, String taskId, String status,
                                 BrokerInboundMessage req, BrokerControlDescriptor.Descriptor desc,
                                 String extraToken) {
        // response routes back to the ORIGINAL gateway caller (carried end-to-end in the
        // descriptor's originalCaller field), NOT to req.sourceServiceId() (which is the
        // event-bus relay, not the original gateway).
        String responseTarget = desc.originalCaller() != null ? desc.originalCaller() : req.sourceServiceId();
        String descriptor = BrokerControlDescriptor.encode(
                eventType, desc.traceId(), desc.correlationId(), desc.idempotencyKey(),
                desc.routeHandle(), desc.capability(), desc.deadlineMillisEpoch(),
                desc.originalCaller());
        if (taskId != null) {
            descriptor = descriptor + ";taskId=" + taskId;
        }
        descriptor = descriptor + ";status=" + status;
        if (extraToken != null && !extraToken.isBlank()) {
            descriptor = descriptor + ";" + extraToken;
        }

        String messageId = "resp-" + java.util.UUID.randomUUID();
        Message msg = new Message(respInTopic, /* tags */ null, messageId,
                ("target=" + responseTarget).getBytes(StandardCharsets.UTF_8));
        msg.putUserProperty("tenantId", tenant);
        msg.putUserProperty("messageId", messageId);
        msg.putUserProperty("sourceServiceId", runtimeServiceId);
        msg.putUserProperty("targetServiceId", responseTarget);
        msg.putUserProperty("correlationId", desc.correlationId());
        msg.putUserProperty("eventType", eventType.name());
        msg.putUserProperty("payloadRef", descriptor);

        try {
            producer.send(msg);
            log("SEND: eventType=" + eventType + " messageId=" + messageId
                    + " taskId=" + taskId + " status=" + status
                    + " target=" + responseTarget
                    + (extraToken != null ? " extra=" + extraToken : ""));
        } catch (Exception e) {
            log("ERROR: producer.send failed for eventType=" + eventType
                    + " messageId=" + messageId + " err=" + e);
        }
    }

    // ===== CLI =====

    public static void main(String[] args) throws Exception {
        Args parsed = Args.parse(args);
        TempRuntimeMain runtime = new TempRuntimeMain(
                parsed.nameserver, parsed.runtime, parsed.tenant, parsed.mode,
                parsed.pollWaitMs, parsed.route, parsed.consumerGroup, parsed.producerGroup,
                parsed.verbose);
        runtime.start();
    }

    // ===== helpers =====

    private void log(String msg) {
        if (verbose || msg.startsWith("ERROR") || msg.startsWith("WARN") || msg.startsWith("Starting")
                || msg.startsWith("Producer") || msg.startsWith("Consumer")
                || msg.startsWith("Shutdown") || msg.startsWith("Poll loop")) {
            System.out.println("[TempRuntime " + runtimeServiceId + "] " + msg);
        }
    }

    private static void sleepQuiet(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }

    /** Minimal CLI arg parser — flags {@code --key value} (or {@code --key=value}). */
    static final class Args {
        String nameserver;
        String runtime;
        String tenant;
        ResponseMode mode = ResponseMode.BLOCKING;
        long pollWaitMs = DEFAULT_POLL_WAIT_MS;
        String route = "invocation";
        String consumerGroup;
        String producerGroup;
        boolean verbose = false;

        static Args parse(String[] args) {
            Args a = new Args();
            for (int i = 0; i < args.length; i++) {
                String arg = args[i];
                String next = (i + 1 < args.length) ? args[i + 1] : null;
                switch (arg) {
                    case "--nameserver" -> { a.nameserver = next; i++; }
                    case "--runtime" -> { a.runtime = next; i++; }
                    case "--tenant" -> { a.tenant = next; i++; }
                    case "--mode" -> { a.mode = ResponseMode.valueOf(next); i++; }
                    case "--poll-wait-ms" -> { a.pollWaitMs = Long.parseLong(next); i++; }
                    case "--route" -> { a.route = next; i++; }
                    case "--consumer-group" -> { a.consumerGroup = next; i++; }
                    case "--producer-group" -> { a.producerGroup = next; i++; }
                    case "--verbose" -> a.verbose = true;
                    case "-h", "--help" -> { printHelp(); System.exit(0); }
                    default -> {
                        if (arg.startsWith("--") && arg.contains("=")) {
                            int eq = arg.indexOf('=');
                            String k = arg.substring(2, eq);
                            String v = arg.substring(eq + 1);
                            applyKV(a, k, v);
                        } else {
                            throw new IllegalArgumentException("unknown arg: " + arg);
                        }
                    }
                }
            }
            if (a.nameserver == null || a.runtime == null || a.tenant == null) {
                printHelp();
                throw new IllegalArgumentException(
                        "--nameserver, --runtime, --tenant are required");
            }
            return a;
        }

        private static void applyKV(Args a, String k, String v) {
            switch (k) {
                case "nameserver" -> a.nameserver = v;
                case "runtime" -> a.runtime = v;
                case "tenant" -> a.tenant = v;
                case "mode" -> a.mode = ResponseMode.valueOf(v);
                case "poll-wait-ms" -> a.pollWaitMs = Long.parseLong(v);
                case "route" -> a.route = v;
                case "consumer-group" -> a.consumerGroup = v;
                case "producer-group" -> a.producerGroup = v;
                case "verbose" -> a.verbose = Boolean.parseBoolean(v);
                default -> throw new IllegalArgumentException("unknown key: --" + k);
            }
        }

        private static void printHelp() {
            System.out.println("""
                    TempRuntimeMain — standalone agent-runtime double for FEAT-013/014 E2E verification.

                    Usage:
                      java -cp agent-bus-*.jar com.huawei.ascend.bus.test.TempRuntimeMain \\
                        --nameserver      <host:9876>       (required)
                        --runtime         <serviceId>       (required)
                        --tenant          <tenantId>        (required)
                        --mode            BLOCKING|STREAMING|ACCEPTED_ONLY|SILENT|REJECT|DEFER_THEN_RESOLVE  (default: BLOCKING)
                        --route           invocation|a2a    (default: invocation)
                        --poll-wait-ms    3000              (default: 3000)
                        --consumer-group  <group>           (default: runtime-<serviceId>)
                        --producer-group  <group>           (default: runtime-producer-<serviceId>)
                        --verbose                           (log every poll/recv/send)
                        --help | -h                         (this message)

                    Topology:
                      consumes:  ascend_bus_<route>_deliver   (hop2 forward-relay output)
                      produces:  ascend_bus_<route>_resp_in   (response relay ingests → resp_out → gateway)

                    Modes (FEAT-013 §6.2):
                      BLOCKING           §6.2.1 — ACCEPTED + RESPONSE(snapshot) + TERMINAL(completed)
                      STREAMING          §6.2.4 — ACCEPTED + STREAM_READY(streamRef)
                      ACCEPTED_ONLY      §6.2.2 — ACCEPTED only (gateway returns ACCEPTED_WITH_TASK)
                      SILENT             §6.2.3 — create task, emit nothing (gateway UNKNOWN)
                      REJECT             §6.2.5 UC-07 — REJECTED (no Task created; gateway returns REJECTED)
                      DEFER_THEN_RESOLVE §6.2.3 UC-03 — first REQUESTED creates task but emits nothing
                                          (gateway UNKNOWN); second REQUESTED with same idempotencyKey
                                          replays BLOCKING with the same taskId (server-side idempotency)

                    Shutdown: Ctrl+C (SIGINT) — clean shutdown hook closes consumer + producer.
                    """);
        }
    }
}
