package com.huawei.ascend.service.platform.web.runs;

import com.huawei.ascend.service.runtime.runs.Run;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Default {@link AsyncRunDispatcher} for W1.x. Logs the dispatch intent at DEBUG and
 * returns. A real orchestrator-backed dispatcher (ADR-0070, W2 scope) overrides this
 * bean by declaring its own {@code @Bean @Primary} in a test {@code @TestConfiguration}
 * or by registering a production dispatcher with {@code @Primary}.
 *
 * <p>Override pattern: see {@code RunCursorFlowIT.Config#blockingDispatcher()} — a
 * test {@code @Bean @Primary AsyncRunDispatcher} unambiguously wins autowiring without
 * touching the default registration. Earlier revisions used
 * {@code @ConditionalOnMissingBean} on this {@code @Component} to gate registration;
 * that annotation is reliable only on {@code @Bean} methods inside
 * {@code @Configuration} classes — on {@code @Component} the evaluation is
 * order-dependent and excluded this bean on Linux CI (root cause of the rc4
 * regression).
 */
@Component
public class NoOpAsyncRunDispatcher implements AsyncRunDispatcher {

    private static final Logger LOG = LoggerFactory.getLogger(NoOpAsyncRunDispatcher.class);

    @Override
    public void dispatch(Run run) {
        if (LOG.isDebugEnabled()) {
            LOG.debug("NoOp dispatch — runId={} tenant={} capability={} (W1.x default)",
                    run.runId(), run.tenantId(), run.capabilityName());
        }
    }
}
