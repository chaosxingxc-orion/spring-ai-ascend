package ascend.springai.service.platform.web.runs;

import ascend.springai.service.runtime.runs.Run;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

/**
 * Default {@link AsyncRunDispatcher} for W1.x. Logs the dispatch intent at DEBUG and
 * returns. A real orchestrator-backed dispatcher (ADR-0070, W2 scope) overrides this
 * bean by declaring its own {@code @Component} or {@code @Bean}.
 *
 * <p>Marked {@code @ConditionalOnMissingBean} so consumers can register a custom
 * dispatcher (e.g. test {@code BlockingAsyncRunDispatcher}, future W2 orchestrator)
 * without removing this default first.
 */
@Component
@ConditionalOnMissingBean(AsyncRunDispatcher.class)
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
