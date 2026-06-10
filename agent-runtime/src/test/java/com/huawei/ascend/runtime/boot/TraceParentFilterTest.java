package com.huawei.ascend.runtime.boot;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class TraceParentFilterTest {

    private static final String INBOUND_TRACE_ID = "0af7651916cd43dd8448eb211c80319c";
    private static final String INBOUND_PARENT_ID = "b7ad6b7169203331";

    private final TraceParentFilter filter = new TraceParentFilter();

    @Test
    void validTraceparentKeepsTraceIdAndGeneratesFreshServerSpan() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/a2a");
        request.addHeader("traceparent",
                "00-" + INBOUND_TRACE_ID + "-" + INBOUND_PARENT_ID + "-01");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MdcCapturingChain chain = new MdcCapturingChain();

        filter.doFilter(request, response, chain);

        assertThat(chain.traceId).isEqualTo(INBOUND_TRACE_ID);
        assertThat(chain.spanId).matches("[0-9a-f]{16}").isNotEqualTo(INBOUND_PARENT_ID);
        assertThat(response.getHeader("traceresponse"))
                .isEqualTo("00-" + INBOUND_TRACE_ID + "-" + chain.spanId + "-01");
    }

    @Test
    void missingTraceparentOriginatesFreshTrace() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/a2a");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MdcCapturingChain chain = new MdcCapturingChain();

        filter.doFilter(request, response, chain);

        assertThat(chain.traceId).matches("[0-9a-f]{32}").isNotEqualTo("0".repeat(32));
        assertThat(response.getHeader("traceresponse"))
                .isEqualTo("00-" + chain.traceId + "-" + chain.spanId + "-01");
    }

    /** An unparseable id must never be propagated — a fresh trace originates instead. */
    @Test
    void invalidTraceparentOriginatesFreshTrace() throws Exception {
        String[] malformed = {
                "01-" + INBOUND_TRACE_ID + "-" + INBOUND_PARENT_ID + "-01",
                "00-" + INBOUND_TRACE_ID.toUpperCase() + "-" + INBOUND_PARENT_ID + "-01",
                "00-" + "0".repeat(32) + "-" + INBOUND_PARENT_ID + "-01",
                "00-" + INBOUND_TRACE_ID + "-" + "0".repeat(16) + "-01",
                "00-" + INBOUND_TRACE_ID + "-" + INBOUND_PARENT_ID,
                "garbage",
        };
        for (String header : malformed) {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/a2a");
            request.addHeader("traceparent", header);
            MockHttpServletResponse response = new MockHttpServletResponse();
            MdcCapturingChain chain = new MdcCapturingChain();

            filter.doFilter(request, response, chain);

            assertThat(chain.traceId).as("header: %s", header)
                    .matches("[0-9a-f]{32}")
                    .isNotEqualTo(INBOUND_TRACE_ID)
                    .isNotEqualTo("0".repeat(32));
        }
    }

    @Test
    void invalidTraceparentIncrementsInvalidCounter() throws Exception {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        TraceParentFilter metered = new TraceParentFilter(registry);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/a2a");
        request.addHeader("traceparent", "garbage");

        metered.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

        assertThat(registry.counter("springai_ascend_traceparent_invalid_total").count())
                .isEqualTo(1.0);
    }

    @Test
    void missingOrValidTraceparentDoesNotIncrementInvalidCounter() throws Exception {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        TraceParentFilter metered = new TraceParentFilter(registry);

        MockHttpServletRequest absent = new MockHttpServletRequest("GET", "/a2a");
        metered.doFilter(absent, new MockHttpServletResponse(), new MockFilterChain());

        MockHttpServletRequest valid = new MockHttpServletRequest("POST", "/a2a");
        valid.addHeader("traceparent",
                "00-" + INBOUND_TRACE_ID + "-" + INBOUND_PARENT_ID + "-01");
        metered.doFilter(valid, new MockHttpServletResponse(), new MockFilterChain());

        assertThat(registry.counter("springai_ascend_traceparent_invalid_total").count())
                .isEqualTo(0.0);
    }

    @Test
    void mdcIsClearedAfterTheFilterReturns() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/a2a");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(MDC.get("trace_id")).isNull();
        assertThat(MDC.get("span_id")).isNull();
    }

    /** Captures the MDC trace/span keys as the downstream chain observes them. */
    private static final class MdcCapturingChain implements FilterChain {
        String traceId;
        String spanId;

        @Override
        public void doFilter(ServletRequest request, ServletResponse response) {
            traceId = MDC.get("trace_id");
            spanId = MDC.get("span_id");
        }
    }
}
