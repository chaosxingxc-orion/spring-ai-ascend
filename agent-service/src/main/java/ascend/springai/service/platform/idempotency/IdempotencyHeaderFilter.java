package ascend.springai.service.platform.idempotency;

import ascend.springai.service.platform.tenant.TenantContext;
import ascend.springai.service.platform.tenant.TenantContextHolder;
import ascend.springai.service.platform.web.ErrorEnvelopeWriter;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

/**
 * L1 idempotency filter (ADR-0057). At order 30 — runs after Spring Security,
 * after {@link ascend.springai.service.platform.tenant.JwtTenantClaimCrossCheck} (15),
 * and after {@link ascend.springai.service.platform.tenant.TenantContextFilter} (20),
 * so {@link TenantContextHolder} is already populated.
 *
 * <p>Behaviour:
 * <ol>
 *   <li>Skip non-mutating methods and permit-list paths
 *       ({@link #shouldNotFilter(HttpServletRequest)}).</li>
 *   <li>Validate {@code Idempotency-Key} header as UUID (existing W0 contract).</li>
 *   <li>If no {@link IdempotencyStore} bean was wired (dev posture, no Postgres,
 *       no allow-in-memory) and posture is {@code dev}: pass through with a
 *       warning. Otherwise demand the store.</li>
 *   <li>Read the request body once into a {@link CachedBodyHttpServletRequest}
 *       (capped at {@value #MAX_CACHED_BODY_BYTES} bytes), hash
 *       {@code method:path:body} (SHA-256 → base64url), call
 *       {@link IdempotencyStore#claimOrFind(UUID, UUID, String)}.</li>
 *   <li>Empty result → claim taken; pass the cached-body wrapper downstream so
 *       the {@code @RequestBody} deserialiser can re-read the body.</li>
 *   <li>Existing row with same hash → 409 {@code idempotency_conflict}.</li>
 *   <li>Existing row with different hash → 409 {@code idempotency_body_drift}.</li>
 * </ol>
 *
 * <p>Enforcer rows: docs/governance/enforcers.yaml#E12, #E13, #E14.
 */
public class IdempotencyHeaderFilter extends OncePerRequestFilter {

    private static final Logger LOG = LoggerFactory.getLogger(IdempotencyHeaderFilter.class);

    /**
     * Upper bound on cached request body size in bytes. 2 MiB comfortably covers
     * the W1 max-http-form-post-size (1 MiB) without letting a malicious caller
     * pin large buffers in heap before Spring's own size limits engage.
     */
    static final int MAX_CACHED_BODY_BYTES = 2 * 1024 * 1024;

    private final IdempotencyStore store;
    private final String posture;
    private final Counter missingCounter;
    private final Counter invalidCounter;
    private final Counter conflictCounter;
    private final Counter bodyDriftCounter;

    public IdempotencyHeaderFilter(IdempotencyStore store, MeterRegistry registry, String posture) {
        this.store = store;
        this.posture = posture;
        this.missingCounter = Counter.builder("springai_ascend_idempotency_header_missing_total")
                .tag("posture", posture).register(registry);
        this.invalidCounter = Counter.builder("springai_ascend_idempotency_header_invalid_total")
                .tag("posture", posture).register(registry);
        this.conflictCounter = Counter.builder("springai_ascend_idempotency_conflict_total")
                .tag("posture", posture).register(registry);
        this.bodyDriftCounter = Counter.builder("springai_ascend_idempotency_body_drift_total")
                .tag("posture", posture).register(registry);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        if (path.startsWith("/actuator") || "/v1/health".equals(path)) return true;
        if (path.startsWith("/v3/api-docs")) return true;
        String method = request.getMethod();
        return !"POST".equalsIgnoreCase(method)
                && !"PUT".equalsIgnoreCase(method)
                && !"PATCH".equalsIgnoreCase(method);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String header = request.getHeader(IdempotencyConstants.HEADER_NAME);
        if (header == null || header.isBlank()) {
            missingCounter.increment();
            if ("dev".equalsIgnoreCase(posture)) {
                LOG.warn("Idempotency-Key header missing; continuing in posture=dev");
                chain.doFilter(request, response);
            } else {
                ErrorEnvelopeWriter.write(response, 400, "idempotency_key_missing",
                        "Idempotency-Key header is required.");
            }
            return;
        }

        UUID key;
        try {
            key = IdempotencyKey.parse(header).value();
        } catch (IllegalArgumentException e) {
            invalidCounter.increment();
            ErrorEnvelopeWriter.write(response, 400, "idempotency_key_invalid",
                    "Idempotency-Key must be a valid UUID.");
            return;
        }

        if (store == null) {
            // dev posture without a wired store: behave as W0 header-only validation.
            LOG.warn("No IdempotencyStore bean wired; idempotency check is header-only (posture={}).", posture);
            chain.doFilter(request, response);
            return;
        }

        TenantContext tenant = TenantContextHolder.get();
        if (tenant == null) {
            // TenantContextFilter must have populated this by now; if not, the
            // request is malformed enough that returning 400 is the right answer.
            ErrorEnvelopeWriter.write(response, 400, "tenant_context_missing",
                    "Tenant context not resolved before idempotency check.");
            return;
        }

        // Cap the cached body so a malicious caller cannot pin large buffers
        // before Spring's own size limits engage. We read at most
        // MAX_CACHED_BODY_BYTES + 1 to detect overrun without allocating more.
        byte[] body = readBodyCapped(request, MAX_CACHED_BODY_BYTES);
        if (body == null) {
            ErrorEnvelopeWriter.write(response, 413, "request_body_too_large",
                    "Request body exceeds " + MAX_CACHED_BODY_BYTES + " bytes.");
            return;
        }
        CachedBodyHttpServletRequest wrapped = new CachedBodyHttpServletRequest(request, body);
        String requestHash = sha256Base64Url(request.getMethod(), request.getRequestURI(), body);

        Optional<IdempotencyStore.IdempotencyRecord> existing =
                store.claimOrFind(tenant.tenantId(), key, requestHash);
        if (existing.isPresent()) {
            IdempotencyStore.IdempotencyRecord rec = existing.get();
            if (!rec.requestHash().equals(requestHash)) {
                bodyDriftCounter.increment();
                ErrorEnvelopeWriter.write(response, 409, "idempotency_body_drift",
                        "Idempotency-Key was previously used with a different request body.");
            } else {
                conflictCounter.increment();
                ErrorEnvelopeWriter.write(response, 409, "idempotency_conflict",
                        "A request with this Idempotency-Key is already in flight or completed.");
            }
            return;
        }

        chain.doFilter(wrapped, response);
    }

    /**
     * Reads up to {@code limit} bytes from the request body. Returns the byte
     * array on success, or {@code null} if the body exceeds the limit. Empty
     * bodies (e.g. {@code POST /v1/runs/{id}/cancel}) return a zero-length
     * array, never {@code null}.
     */
    private static byte[] readBodyCapped(HttpServletRequest request, int limit) throws IOException {
        java.io.ByteArrayOutputStream buf = new java.io.ByteArrayOutputStream();
        byte[] chunk = new byte[8192];
        int total = 0;
        try (java.io.InputStream in = request.getInputStream()) {
            int n;
            while ((n = in.read(chunk)) != -1) {
                total += n;
                if (total > limit) {
                    return null;
                }
                buf.write(chunk, 0, n);
            }
        }
        return buf.toByteArray();
    }

    private static String sha256Base64Url(String method, String path, byte[] body) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update(method.getBytes(StandardCharsets.UTF_8));
            md.update((byte) ':');
            md.update(path.getBytes(StandardCharsets.UTF_8));
            md.update((byte) ':');
            md.update(body);
            return Base64.getUrlEncoder().withoutPadding().encodeToString(md.digest());
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is mandatory in every standard JDK; absence indicates a broken JVM.
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
