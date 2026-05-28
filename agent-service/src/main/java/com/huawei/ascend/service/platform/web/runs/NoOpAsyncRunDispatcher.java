package com.huawei.ascend.service.platform.web.runs;

import com.huawei.ascend.service.runtime.runs.Run;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Fallback {@link AsyncRunDispatcher} that logs the dispatch intent at DEBUG and
 * returns without executing. Registered by {@link RunControllerAutoConfiguration}
 * only when no other {@link AsyncRunDispatcher} bean exists — i.e. in
 * {@code research}/{@code prod} posture (where the dev-posture
 * {@link OrchestratingAsyncRunDispatcher} is absent and a durable, W2-scope
 * dispatcher (ADR-0070) has not yet been provided).
 *
 * <p>The {@code @ConditionalOnMissingBean} that selects between this and the
 * orchestrating dispatcher lives on a {@code @Bean} method inside
 * {@code @Configuration} (not a {@code @Component}-level conditional) so its
 * evaluation is order-independent — the form the rc4 regression established. A
 * test may still override either by declaring its own {@code @Primary
 * AsyncRunDispatcher} (see {@code RunCursorFlowIT.Config#blockingDispatcher()}).
 */
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
