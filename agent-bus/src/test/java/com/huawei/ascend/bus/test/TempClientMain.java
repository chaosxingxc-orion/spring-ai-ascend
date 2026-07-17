package com.huawei.ascend.bus.test;

import com.huawei.ascend.bus.forwarding.common.AgentBusBrokerProperties;
import com.huawei.ascend.bus.spi.ingress.IngressEnvelope;
import com.huawei.ascend.bus.spi.ingress.IngressGateway;
import com.huawei.ascend.bus.spi.ingress.IngressResponse;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.ComponentScan;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Standalone E2E client driver for FEAT-013/014 verification. Bootstraps a Spring
 * context in the {@code gateway} profile (so the {@link IngressGateway} bean +
 * its broker wiring is live), then issues ONE {@link IngressEnvelope} request via
 * {@link IngressGateway#routeClientRequest} and prints the resulting
 * {@link IngressResponse}.
 *
 * <p>This stands in for the not-yet-landed gateway HTTP controller (S4 slice). The
 * gateway process must already be running — this driver launches a SECOND Spring
 * context in the same JVM as the caller, sharing the same broker + DB.
 *
 * <p><b>Important</b>: because this driver starts its own Spring context in the
 * {@code gateway} profile, it competes with the standalone gateway process for the
 * same RocketMQ producer-group / consumer-group names. Either:
 * <ul>
 *   <li>(A) Run this driver INSTEAD of the standalone gateway process (the driver's
 *       context IS the gateway — it has the producer, outbox, response consumer, and
 *       accept-window). Recommended for manual E2E.</li>
 *   <li>(B) Run this driver alongside the gateway process, but give it distinct
 *       group names via {@code --agent-bus.producer-group=client-producer} etc. Not
 *       recommended — two gateways reading the same outbox produces races.</li>
 * </ul>
 *
 * <p>Recommended usage = (A): this driver IS the gateway. Do NOT start the standalone
 * gateway jar separately. Start only: event-bus jar + TempRuntimeMain + this driver.
 *
 * <h2>Usage</h2>
 * <pre>
 *   java -cp agent-bus-*.jar com.huawei.ascend.bus.test.TempClientMain \
 *        --spring.profiles.active=gateway \
 *        --agent-bus.nameserver=localhost:9876 \
 *        --agent-bus.tenant=tenant-a \
 *        --agent-bus.gateway-service-id=gateway-01 \
 *        --agent-bus.event-bus-service-id=eventbus-01 \
 *        --agent-bus.accept-timeout-ms=10000 \
 *        --agent-bus.response-timeout-ms=30000 \
 *        --request-type    RUN_CREATE|RUN_GET|RUN_CANCEL|RUN_RESUME \
 *        --target-service  runtime-01 \
 *        --route-handle    invocation|a2a \
 *        --capability      a2a \
 *        --payload         "hello" \
 *        [--idempotency-key <UUID>]  \
 *        [--trace-id       <32hex>]  \
 *        [--deadline-ms    <epoch>]  \
 *        [--print-only]              # build envelope, print descriptor, do NOT call
 * </pre>
 *
 * <p>The driver uses {@code --agent-bus.*} flags for the same {@link AgentBusBrokerProperties}
 * the gateway jar uses, so broker / DB / namespace / timing params are identical.
 *
 * <h2>Exit codes</h2>
 * <ul>
 *   <li>0 — request dispatched, response printed</li>
 *   <li>1 — argument error</li>
 *   <li>2 — Spring context startup failed</li>
 *   <li>3 — routeClientRequest threw</li>
 * </ul>
 *
 * <p>Authority: {@code architecture/L2-Low-Level-Design/agent-bus/
 * feat-013-client-invocation-event-forwarding.md} §4.2 / §6.2;
 * {@code RealBrokerTwoHopRelayIntegrationTest} (G5-E IT reference for inline-gateway
 * form).
 */
// non-production — E2E verification client driver (in-process gateway + 1-shot dispatch)
public final class TempClientMain {

    public static void main(String[] args) {
        ClientArgs parsed;
        try {
            parsed = ClientArgs.parse(args);
        } catch (IllegalArgumentException e) {
            System.err.println("ERROR: " + e.getMessage());
            System.err.println();
            ClientArgs.printHelp(System.err);
            System.exit(1);
            return; // unreachable
        }
        if (parsed.printHelp) {
            ClientArgs.printHelp(System.out);
            return;
        }

        // Build the IngressEnvelope up-front (validates fields before Spring boot,
        // so a bad trace-id / missing target fails fast with a clear message).
        IngressEnvelope envelope;
        try {
            envelope = parsed.toEnvelope();
        } catch (IllegalArgumentException e) {
            System.err.println("ERROR (envelope): " + e.getMessage());
            System.exit(1);
            return;
        }

        // Print the request descriptor for traceability (mirrors what the gateway
        // will encode into the broker payloadRef).
        System.out.println("==== TempClientMain ====");
        System.out.println("requestId        = " + envelope.requestId());
        System.out.println("tenantId         = " + envelope.tenantId());
        System.out.println("idempotencyKey   = " + envelope.idempotencyKey());
        System.out.println("requestType      = " + envelope.requestType());
        System.out.println("traceId          = " + envelope.traceId());
        System.out.println("deadline         = " + envelope.deadlineMillisEpoch());
        System.out.println("targetServiceId  = " + parsed.targetService);
        System.out.println("routeHandle      = " + parsed.routeHandle);
        System.out.println("capability       = " + parsed.capability);
        System.out.println("payload          = " + parsed.payload);

        if (parsed.printOnly) {
            System.out.println("--print-only: skipping dispatch + Spring boot.");
            return;
        }

        // Boot Spring in the gateway profile (this driver IS the gateway — do not
        // also run the standalone gateway jar).
        ConfigurableApplicationContext ctx;
        try {
            SpringApplication app = new SpringApplicationBuilder(ClientApp.class)
                    .profiles(parsed.springProfile)
                    .properties(parsed.springProps())
                    .build();
            ctx = app.run(parsed.springArgs());
        } catch (Exception e) {
            System.err.println("ERROR (spring): " + e.getMessage());
            e.printStackTrace();
            System.exit(2);
            return;
        }

        try {
            IngressGateway gateway = ctx.getBean(IngressGateway.class);
            System.out.println();
            System.out.println("---- dispatching routeClientRequest ----");
            long t0 = System.currentTimeMillis();
            IngressResponse response = gateway.routeClientRequest(envelope);
            long elapsed = System.currentTimeMillis() - t0;

            System.out.println();
            System.out.println("==== IngressResponse ====");
            System.out.println("requestId        = " + response.requestId());
            System.out.println("status           = " + response.status());
            System.out.println("cursor           = " + response.cursor());
            System.out.println("rejectionReason  = " + response.rejectionReason());
            System.out.println("elapsedMs        = " + elapsed);
            System.out.println();
        } catch (Exception e) {
            System.err.println("ERROR (routeClientRequest): " + e.getMessage());
            e.printStackTrace();
            System.exit(3);
        } finally {
            ctx.close();
        }
    }

    /**
     * Empty {@code @SpringBootApplication} that component-scans the gateway +
     * forwarding infra packages. Reuses the production
     * {@link com.huawei.ascend.bus.AgentBusApplication} autoconfig but is named
     * distinctly so the driver's main class is clear in logs.
     */
    @SpringBootApplication
    @ComponentScan(basePackages = "com.huawei.ascend.bus")
    public static class ClientApp {
        // intentionally empty — component scan picks up AgentBusApplication's config
    }

    // ===== CLI arg parsing =====

    static final class ClientArgs {
        // spring-side
        String springProfile = "gateway";
        String nameserver = "localhost:9876";
        String tenant = "tenant-a";
        String gatewayServiceId = "gateway-01";
        String eventBusServiceId = "eventbus-01";
        String datasourceUrl = "jdbc:postgresql://localhost:5432/agentbus";
        String datasourceUser = "agentbus";
        String datasourcePassword = "agentbus";
        long acceptTimeoutMs = 10_000L;
        long responseTimeoutMs = 30_000L;
        long pollWaitMillis = 3_000L;
        long leaseDurationMs = 60_000L;
        String namespace = "ascend-prod";

        // request-side
        IngressEnvelope.IngressRequestType requestType = IngressEnvelope.IngressRequestType.RUN_CREATE;
        String targetService = "runtime-01";
        String routeHandle = "invocation";
        String capability = "a2a";
        String payload = "hello";
        UUID idempotencyKey = UUID.randomUUID();
        String traceId = randomTraceId();
        Long deadlineMillisEpoch = null;

        boolean printOnly = false;
        boolean printHelp = false;

        // raw spring args (passed through to SpringApplication.run for any --foo=bar
        // the user wants to forward that we don't explicitly model).
        java.util.List<String> springArgs = new java.util.ArrayList<>();

        static ClientArgs parse(String[] args) {
            ClientArgs a = new ClientArgs();
            for (int i = 0; i < args.length; i++) {
                String arg = args[i];
                String next = (i + 1 < args.length) ? args[i + 1] : null;
                // spring-style --key=value
                if (arg.startsWith("--") && arg.contains("=")) {
                    a.springArgs.add(arg);
                    applyKV(a, arg.substring(2, arg.indexOf('=')), arg.substring(arg.indexOf('=') + 1));
                    continue;
                }
                switch (arg) {
                    case "--request-type" -> { a.requestType = IngressEnvelope.IngressRequestType.valueOf(next); i++; }
                    case "--target-service" -> { a.targetService = next; i++; }
                    case "--route-handle" -> { a.routeHandle = next; i++; }
                    case "--capability" -> { a.capability = next; i++; }
                    case "--payload" -> { a.payload = next; i++; }
                    case "--idempotency-key" -> { a.idempotencyKey = UUID.fromString(next); i++; }
                    case "--trace-id" -> { a.traceId = next; i++; }
                    case "--deadline-ms" -> { a.deadlineMillisEpoch = Long.parseLong(next); i++; }
                    case "--print-only" -> a.printOnly = true;
                    case "-h", "--help" -> a.printHelp = true;
                    default -> {
                        if (arg.startsWith("--")) {
                            // forward any unknown --key value (or --key=value) to spring
                            if (next != null && !next.startsWith("--")) {
                                a.springArgs.add(arg + "=" + next);
                                applyKV(a, arg.substring(2), next);
                                i++;
                            } else {
                                a.springArgs.add(arg);
                            }
                        } else {
                            throw new IllegalArgumentException("unknown arg: " + arg);
                        }
                    }
                }
            }
            return a;
        }

        private static void applyKV(ClientArgs a, String k, String v) {
            switch (k) {
                case "spring.profiles.active" -> a.springProfile = v;
                case "agent-bus.nameserver" -> a.nameserver = v;
                case "agent-bus.tenant" -> a.tenant = v;
                case "agent-bus.gateway-service-id" -> a.gatewayServiceId = v;
                case "agent-bus.event-bus-service-id" -> a.eventBusServiceId = v;
                case "agent-bus.accept-timeout-ms" -> a.acceptTimeoutMs = Long.parseLong(v);
                case "agent-bus.response-timeout-ms" -> a.responseTimeoutMs = Long.parseLong(v);
                case "agent-bus.poll-wait-millis" -> a.pollWaitMillis = Long.parseLong(v);
                case "agent-bus.lease-duration-ms" -> a.leaseDurationMs = Long.parseLong(v);
                case "agent-bus.namespace" -> a.namespace = v;
                case "spring.datasource.url" -> a.datasourceUrl = v;
                case "spring.datasource.username" -> a.datasourceUser = v;
                case "spring.datasource.password" -> a.datasourcePassword = v;
                // unknown keys are still forwarded to spring (already added to springArgs)
                default -> { /* no-op: already in springArgs via the --key=value path */ }
            }
        }

        /** Build a spring-args array: --spring.profiles.active + all --key=value passthroughs. */
        String[] springArgs() {
            java.util.List<String> out = new java.util.ArrayList<>();
            out.add("--spring.profiles.active=" + springProfile);
            out.addAll(springArgs);
            return out.toArray(new String[0]);
        }

        /** Spring properties that must be set programmatically (not via CLI args). */
        java.util.Map<String, Object> springProps() {
            java.util.Map<String, Object> p = new LinkedHashMap<>();
            p.put("agent-bus.nameserver", nameserver);
            p.put("agent-bus.tenant", tenant);
            p.put("agent-bus.gateway-service-id", gatewayServiceId);
            p.put("agent-bus.event-bus-service-id", eventBusServiceId);
            p.put("agent-bus.accept-timeout-ms", acceptTimeoutMs);
            p.put("agent-bus.response-timeout-ms", responseTimeoutMs);
            p.put("agent-bus.poll-wait-millis", pollWaitMillis);
            p.put("agent-bus.lease-duration-ms", leaseDurationMs);
            p.put("agent-bus.namespace", namespace);
            p.put("spring.datasource.url", datasourceUrl);
            p.put("spring.datasource.username", datasourceUser);
            p.put("spring.datasource.password", datasourcePassword);
            p.put("spring.datasource.driver-class-name", "org.postgresql.Driver");
            // silence the web server — this driver is a 1-shot CLI, not a long-running service
            p.put("spring.main.web-application-type", "NONE");
            p.put("spring.main.banner-mode", "off");
            p.put("spring.flyway.enabled", "true");
            return p;
        }

        IngressEnvelope toEnvelope() {
            Map<String, Object> attrs = new LinkedHashMap<>();
            attrs.put("targetServiceId", Objects.requireNonNull(targetService, "targetService is required"));
            attrs.put("routeHandle", Objects.requireNonNull(routeHandle, "routeHandle is required"));
            attrs.put("capability", Objects.requireNonNull(capability, "capability is required"));
            return new IngressEnvelope(
                    UUID.randomUUID(),
                    tenant,
                    idempotencyKey,
                    requestType,
                    payload,
                    traceId,
                    deadlineMillisEpoch,
                    attrs);
        }

        static void printHelp(java.io.PrintStream out) {
            out.println("""
                    TempClientMain — standalone E2E client driver for FEAT-013/014 verification.

                    This driver boots a Spring context in the 'gateway' profile and dispatches ONE
                    IngressEnvelope via IngressGateway.routeClientRequest. It IS the gateway — do
                    NOT also run the standalone gateway jar (they would compete for the same
                    RocketMQ groups + outbox).

                    Usage:
                      java -cp <classpath> com.huawei.ascend.bus.test.TempClientMain \\
                        --spring.profiles.active=gateway \\
                        --agent-bus.nameserver=localhost:9876 \\
                        --agent-bus.tenant=tenant-a \\
                        --agent-bus.gateway-service-id=gateway-01 \\
                        --agent-bus.event-bus-service-id=eventbus-01 \\
                        --agent-bus.accept-timeout-ms=10000 \\
                        --agent-bus.response-timeout-ms=30000 \\
                        --spring.datasource.url=jdbc:postgresql://localhost:5432/agentbus \\
                        --spring.datasource.username=agentbus \\
                        --spring.datasource.password=agentbus \\
                        --request-type    RUN_CREATE|RUN_GET|RUN_CANCEL|RUN_RESUME  (default: RUN_CREATE) \\
                        --target-service  runtime-01                               (default: runtime-01) \\
                        --route-handle    invocation|a2a                            (default: invocation) \\
                        --capability      a2a                                       (default: a2a) \\
                        --payload         "hello"                                   (default: hello) \\
                        --idempotency-key <UUID>                                    (default: random) \\
                        --trace-id        <32hex>                                   (default: random) \\
                        --deadline-ms     <epoch-millis>                            (default: none) \\
                        --print-only                                                 (build envelope, skip dispatch) \\
                        --help | -h                                                  (this message)

                    Modes (FEAT-013 §6.2):
                      RUN_CREATE  → CLIENT_INVOCATION_REQUESTED (UC-01/02/03/04/07)
                      RUN_CANCEL  → CLIENT_INVOCATION_CANCEL_REQUESTED (UC-05)
                      RUN_GET     → CLIENT_INVOCATION_QUERY_REQUESTED (UC-06)
                      RUN_RESUME  → CLIENT_STREAM_SUBSCRIBE_REQUESTED (UC-04 stream subscribe)

                    Exit codes: 0=ok, 1=arg error, 2=spring boot failed, 3=dispatch failed
                    """);
        }
    }

    /** Generate a W3C-compliant 32-char lowercase hex trace-id. */
    static String randomTraceId() {
        StringBuilder sb = new StringBuilder(32);
        java.util.concurrent.ThreadLocalRandom rng = java.util.concurrent.ThreadLocalRandom.current();
        for (int i = 0; i < 32; i++) {
            int v = rng.nextInt(16);
            sb.append(v < 10 ? (char) ('0' + v) : (char) ('a' + v - 10));
        }
        return sb.toString();
    }
}
