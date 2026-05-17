package ascend.springai.service.platform.idempotency;

import ascend.springai.service.platform.tenant.TenantContext;
import ascend.springai.service.platform.tenant.TenantContextHolder;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression test for L1-expert review finding P1-1: the previous filter
 * implementation drained {@code request.getInputStream()} into a
 * {@code ContentCachingRequestWrapper}, which left the underlying servlet
 * input stream exhausted but did NOT install a replayable
 * {@code getInputStream()} on the wrapped request. The downstream
 * {@code @RequestBody} consumer therefore received an empty stream and
 * deserialised to a record of all-null fields, breaking authenticated
 * {@code POST /v1/runs}.
 *
 * <p>The fix wraps the request in {@link CachedBodyHttpServletRequest}, whose
 * {@code getInputStream()} always returns a fresh {@code ByteArrayInputStream}
 * over the cached bytes. This test asserts the cached bytes are visible to a
 * downstream filter and equal to the original payload.
 */
class IdempotencyHeaderFilterBodyReplayTest {

    private static final UUID TENANT = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID KEY = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");
    private static final String JSON = "{\"capabilityName\":\"echo\",\"payload\":\"hello\"}";

    @BeforeEach
    void seedTenant() {
        TenantContextHolder.set(new TenantContext(TENANT));
    }

    @AfterEach
    void clearTenant() {
        TenantContextHolder.clear();
    }

    @Test
    void downstreamFilter_seesFullBody_whenStoreClaimsRequest() throws Exception {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        IdempotencyStore freshClaim = (tenantId, key, hash) -> Optional.empty();
        IdempotencyHeaderFilter filter = new IdempotencyHeaderFilter(freshClaim, registry, "research");

        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/v1/runs");
        req.setContentType("application/json");
        req.addHeader(IdempotencyConstants.HEADER_NAME, KEY.toString());
        req.setContent(JSON.getBytes(StandardCharsets.UTF_8));
        MockHttpServletResponse res = new MockHttpServletResponse();

        AtomicReference<String> capturedBody = new AtomicReference<>();
        FilterChain capturingChain = new FilterChain() {
            @Override
            public void doFilter(ServletRequest request, ServletResponse response) throws java.io.IOException {
                HttpServletRequest http = (HttpServletRequest) request;
                // Simulates Jackson's @RequestBody deserialiser by re-reading the
                // body via getInputStream(). Pre-fix this returned an empty stream.
                byte[] read = http.getInputStream().readAllBytes();
                capturedBody.set(new String(read, StandardCharsets.UTF_8));
            }
        };

        filter.doFilter(req, res, capturingChain);

        assertThat(res.getStatus()).isEqualTo(200);
        assertThat(capturedBody.get())
                .as("Downstream filter must see the original request body, not an empty stream")
                .isEqualTo(JSON);
    }

    @Test
    void downstreamFilter_canReReadBody_multipleCalls() throws Exception {
        // Spring sometimes re-creates the @RequestBody parser or fails over
        // through error handlers; either path may call getInputStream() more
        // than once. The cached-body wrapper must serve a fresh stream each
        // time, not exhaust on second read.
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        IdempotencyStore freshClaim = (tenantId, key, hash) -> Optional.empty();
        IdempotencyHeaderFilter filter = new IdempotencyHeaderFilter(freshClaim, registry, "research");

        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/v1/runs");
        req.setContentType("application/json");
        req.addHeader(IdempotencyConstants.HEADER_NAME, KEY.toString());
        req.setContent(JSON.getBytes(StandardCharsets.UTF_8));
        MockHttpServletResponse res = new MockHttpServletResponse();

        AtomicReference<String> firstRead = new AtomicReference<>();
        AtomicReference<String> secondRead = new AtomicReference<>();
        FilterChain doubleReadChain = (request, response) -> {
            HttpServletRequest http = (HttpServletRequest) request;
            firstRead.set(new String(http.getInputStream().readAllBytes(), StandardCharsets.UTF_8));
            secondRead.set(new String(http.getInputStream().readAllBytes(), StandardCharsets.UTF_8));
        };

        filter.doFilter(req, res, doubleReadChain);

        assertThat(firstRead.get()).isEqualTo(JSON);
        assertThat(secondRead.get())
                .as("Second getInputStream() call must replay the full body")
                .isEqualTo(JSON);
    }

    @Test
    void bodyDrift_returns409_andDoesNotInvokeDownstream() throws Exception {
        // Sanity: the body-replay wrapper does not interfere with the existing
        // body-drift dedup branch.
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        IdempotencyStore.IdempotencyRecord existing = new IdempotencyStore.IdempotencyRecord(
                TENANT, KEY, "different-hash", IdempotencyStore.Status.CLAIMED,
                null, null, java.time.Instant.now(), null, null);
        IdempotencyStore conflicting = (tenantId, key, hash) -> Optional.of(existing);
        IdempotencyHeaderFilter filter = new IdempotencyHeaderFilter(conflicting, registry, "research");

        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/v1/runs");
        req.setContentType("application/json");
        req.addHeader(IdempotencyConstants.HEADER_NAME, KEY.toString());
        req.setContent(JSON.getBytes(StandardCharsets.UTF_8));
        MockHttpServletResponse res = new MockHttpServletResponse();

        AtomicReference<Boolean> chainCalled = new AtomicReference<>(false);
        FilterChain chain = (request, response) -> chainCalled.set(true);

        filter.doFilter(req, res, chain);

        assertThat(res.getStatus()).isEqualTo(409);
        assertThat(chainCalled.get())
                .as("body-drift branch must short-circuit before the chain")
                .isFalse();
        assertThat(registry.counter("springai_ascend_idempotency_body_drift_total", "posture", "research")
                .count()).isEqualTo(1.0);
    }
}
