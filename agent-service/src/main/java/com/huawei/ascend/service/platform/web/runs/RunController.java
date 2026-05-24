package com.huawei.ascend.service.platform.web.runs;

import com.huawei.ascend.service.platform.tenant.TenantContext;
import com.huawei.ascend.service.platform.tenant.TenantContextHolder;
import com.huawei.ascend.service.platform.web.ErrorEnvelope;
import com.huawei.ascend.service.runtime.runs.Run;
import com.huawei.ascend.engine.orchestration.spi.RunMode;
import com.huawei.ascend.service.runtime.runs.spi.RunRepository;
import com.huawei.ascend.service.runtime.runs.RunStatus;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.CompletableFuture;

/**
 * W1 HTTP run API (plan §6).
 *
 * <ul>
 *   <li>{@code POST /v1/runs} — create a run; initial status {@code PENDING}.</li>
 *   <li>{@code GET /v1/runs/{runId}} — fetch run state within tenant scope; cross-tenant
 *       reads return 404 (architect guidance §9.4).</li>
 *   <li>{@code POST /v1/runs/{runId}/cancel} — idempotent cancellation. Cancelling an
 *       already-{@code CANCELLED} run returns 200 with current state; cancelling a
 *       {@code SUCCEEDED}/{@code FAILED}/{@code EXPIRED} run returns 409
 *       {@code illegal_state_transition}.</li>
 * </ul>
 *
 * <p>Auth, tenant cross-check, and idempotency dedup happen in upstream filters
 * (Spring Security + {@code JwtTenantClaimCrossCheck} + {@code TenantContextFilter}
 * + {@code IdempotencyHeaderFilter}). The controller reads
 * {@link TenantContextHolder} to scope persistence.
 *
 * <p>Enforcer rows: docs/governance/enforcers.yaml#E5, #E6, #E7, #E24.
 */
@RestController
@RequestMapping("/v1/runs")
public class RunController {

    private static final Logger LOG = LoggerFactory.getLogger(RunController.class);
    static final String RUN_DISPATCH_EXECUTOR_BEAN = "runDispatchExecutor";

    private final RunRepository repository;
    private final AsyncRunDispatcher dispatcher;
    private final Executor runDispatchExecutor;

    public RunController(RunRepository repository,
                         AsyncRunDispatcher dispatcher,
                         @Qualifier(RUN_DISPATCH_EXECUTOR_BEAN) Executor runDispatchExecutor) {
        this.repository = repository;
        this.dispatcher = dispatcher;
        this.runDispatchExecutor = runDispatchExecutor;
    }

    @PostMapping(produces = "application/json", consumes = "application/json")
    public ResponseEntity<?> create(@Valid @RequestBody CreateRunRequest request,
                                    HttpServletRequest httpRequest) {
        TenantContext tenant = TenantContextHolder.get();
        if (tenant == null) {
            return error(HttpStatus.BAD_REQUEST, "tenant_context_missing",
                    "Tenant context not resolved.");
        }
        Instant now = Instant.now();
        // Telemetry Vertical (ADR-0061 / §4 #54): persist the inbound trace_id with the
        // Run so cross-suspend trace federation can read it from the Run row. MDC was
        // populated by TraceExtractFilter (order 10) before this controller ran.
        String traceId = MDC.get("trace_id");
        Run run = new Run(
                UUID.randomUUID(),
                tenant.tenantId().toString(),
                request.capabilityName(),
                RunStatus.PENDING,
                RunMode.GRAPH,
                now,
                now,
                null,
                null,
                null,
                null,
                null,
                traceId,
                null);
        Run saved = repository.save(run);
        MDC.put("run_id", saved.runId().toString());
        try {
            LOG.info("Run created: runId={} tenant={} capability={} traceId={}",
                    saved.runId(), saved.tenantId(), saved.capabilityName(), traceId);
        } finally {
            MDC.remove("run_id");
        }
        // Rule 36 / Phase 8 — Cursor Flow Mandate (ADR-0070).
        // Fire-and-forget the dispatcher so POST returns 202 + cursor immediately,
        // regardless of how long the underlying work takes.
        AsyncRunDispatcher fixedDispatcher = dispatcher;
        Run dispatched = saved;
        CompletableFuture.runAsync(() -> fixedDispatcher.dispatch(dispatched), runDispatchExecutor);
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(RunCursorResponse.from(saved, baseUrl(httpRequest)));
    }

    private static String baseUrl(HttpServletRequest request) {
        StringBuilder builder = new StringBuilder()
                .append(request.getScheme()).append("://")
                .append(request.getServerName());
        int port = request.getServerPort();
        if (!(port == 80 || port == 443)) {
            builder.append(':').append(port);
        }
        return builder.toString();
    }

    @GetMapping(value = "/{runId}", produces = "application/json")
    @ApiResponse(responseCode = "200", content = @Content(schema = @Schema(implementation = RunResponse.class)))
    public ResponseEntity<?> get(@PathVariable String runId) {
        UUID id = parseUuidOr400(runId);
        if (id == null) {
            return error(HttpStatus.BAD_REQUEST, "invalid_request",
                    "Path parameter runId must be a UUID.");
        }
        TenantContext tenant = TenantContextHolder.get();
        Optional<Run> found = repository.findById(id);
        if (found.isEmpty() || tenant == null
                || !found.get().tenantId().equals(tenant.tenantId().toString())) {
            return error(HttpStatus.NOT_FOUND, "not_found",
                    "Run not found within tenant scope.");
        }
        return ResponseEntity.ok(RunResponse.from(found.get()));
    }

    @PostMapping(value = "/{runId}/cancel", produces = "application/json")
    @ApiResponse(responseCode = "200", content = @Content(schema = @Schema(implementation = RunResponse.class)))
    public ResponseEntity<?> cancel(@PathVariable String runId) {
        UUID id = parseUuidOr400(runId);
        if (id == null) {
            return error(HttpStatus.BAD_REQUEST, "invalid_request",
                    "Path parameter runId must be a UUID.");
        }
        TenantContext tenant = TenantContextHolder.get();
        Optional<Run> found = repository.findById(id);
        if (found.isEmpty() || tenant == null
                || !found.get().tenantId().equals(tenant.tenantId().toString())) {
            return error(HttpStatus.NOT_FOUND, "not_found",
                    "Run not found within tenant scope.");
        }

        // Atomic re-read + conditional cancel. The prior implementation validated
        // withStatus(CANCELLED) against the stale snapshot above and then blind-saved,
        // so a parallel terminal write (orchestrator SUCCEEDED/FAILED) landing between
        // the read and the save was silently overwritten with CANCELLED. Routing the
        // write through updateIfNotTerminal makes the re-read + terminal check + write
        // one atomic step so a terminal state always wins.
        Optional<Run> result = repository.updateIfNotTerminal(id, r -> r.withStatus(RunStatus.CANCELLED));
        if (result.isEmpty()) {
            return error(HttpStatus.NOT_FOUND, "not_found",
                    "Run not found within tenant scope.");
        }
        Run resolved = result.get();
        if (resolved.status() == RunStatus.CANCELLED) {
            // Either we just cancelled it, or it was already CANCELLED (idempotent) — both 200.
            return ResponseEntity.ok(RunResponse.from(resolved));
        }
        // Re-read status cannot transition to CANCELLED: either terminal
        // (SUCCEEDED/EXPIRED/already-handled) or FAILED (non-terminal but only
        // permits ->RUNNING retry). updateIfNotTerminal left it unchanged.
        return error(HttpStatus.CONFLICT, "illegal_state_transition",
                "Run cannot be cancelled from its current state: " + resolved.status());
    }

    private static UUID parseUuidOr400(String raw) {
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static ResponseEntity<?> error(HttpStatus status, String code, String message) {
        return ResponseEntity.status(status).body(ErrorEnvelope.of(code, message));
    }
}
