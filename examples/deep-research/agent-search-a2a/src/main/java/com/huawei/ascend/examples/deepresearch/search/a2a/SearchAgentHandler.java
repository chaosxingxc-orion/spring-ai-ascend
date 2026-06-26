package com.huawei.ascend.examples.deepresearch.search.a2a;

import com.huawei.ascend.agentsdk.factory.AgentFactory;
import com.huawei.ascend.runtime.common.RuntimeIdentity;
import com.huawei.ascend.runtime.engine.AgentExecutionContext;
import com.huawei.ascend.runtime.engine.openjiuwen.OpenJiuwenAgentRuntimeHandler;
import com.openjiuwen.core.singleagent.BaseAgent;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Runtime handler for the search sub-agent.
 *
 * <p>Per TOPOLOGY §2.1 (mandatory agent-sdk YAML assembly): this class does
 * NOT build the ReActAgent by hand. The full assembly — model, prompt,
 * tools, framework options — comes from the YAML file at {@link #yamlPath},
 * which the wrapper selects by Spring profile (stub / prod).
 *
 * <p>An INFO-level invocation counter is logged on every
 * {@link #createOpenJiuwenAgent(AgentExecutionContext)} call, including the
 * wall-clock cost of {@link AgentFactory#toReactAgent(Path)}. This makes the
 * per-request rebuild contract of {@link OpenJiuwenAgentRuntimeHandler}
 * observable from outside agent-sdk (which currently emits zero log lines)
 * and lets operators see when YAML re-load latency starts to dominate.
 */
final class SearchAgentHandler extends OpenJiuwenAgentRuntimeHandler {

    static final String AGENT_ID = "search-agent";

    private static final Logger LOG = LoggerFactory.getLogger(SearchAgentHandler.class);

    private final Path yamlPath;
    private final AtomicLong rebuildCounter = new AtomicLong();

    SearchAgentHandler(Path yamlPath) {
        super(AGENT_ID);
        this.yamlPath = yamlPath;
    }

    @Override
    protected BaseAgent createOpenJiuwenAgent(AgentExecutionContext context) {
        long started = System.nanoTime();
        BaseAgent agent = AgentFactory.toReactAgent(yamlPath);
        long elapsedMs = (System.nanoTime() - started) / 1_000_000L;
        long invocation = rebuildCounter.incrementAndGet();
        RuntimeIdentity id = context.getScope();
        LOG.info("createOpenJiuwenAgent invocation#{} taskId={} sessionId={} buildElapsedMs={}",
                invocation, id.taskId(), id.sessionId(), elapsedMs);
        return agent;
    }
}